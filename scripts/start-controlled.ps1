# Lance la stack JURIKA en arriere-plan, un fichier de log par service.
# Pratique pour diagnostic Claude Code (pas de fenetres separees).
$ErrorActionPreference = "Stop"
$Root = Resolve-Path "$PSScriptRoot\.."
$Backend = Join-Path $Root "backend-java"
$LogDir = Join-Path $Root ".tmp\logs"
New-Item -ItemType Directory -Path $LogDir -Force | Out-Null

# TEMP redirect (Controlled Folder Access workaround)
$ProjectTmp = Join-Path $Root ".tmp"
New-Item -ItemType Directory -Path $ProjectTmp -Force | Out-Null
$env:TEMP = $ProjectTmp
$env:TMP = $ProjectTmp

# Charger .env puis .env.local
function Import-EnvFile([string]$Path) {
    if (-not (Test-Path $Path)) { return }
    Get-Content $Path | ForEach-Object {
        $line = $_.Trim()
        if ($line -and -not $line.StartsWith("#") -and $line.Contains("=")) {
            $idx = $line.IndexOf("=")
            $key = $line.Substring(0, $idx).Trim()
            $val = $line.Substring($idx + 1).Trim()
            if ($val.StartsWith('"') -and $val.EndsWith('"')) { $val = $val.Substring(1, $val.Length - 2) }
            [System.Environment]::SetEnvironmentVariable($key, $val, "Process")
        }
    }
}
Import-EnvFile (Join-Path $Root ".env")
Import-EnvFile (Join-Path $Root ".env.local")

function Wait-Health([string]$Url, [int]$TimeoutSec = 90) {
    $sw = [Diagnostics.Stopwatch]::StartNew()
    while ($sw.Elapsed.TotalSeconds -lt $TimeoutSec) {
        try {
            $r = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 3
            if ($r.StatusCode -eq 200) { return $true }
        } catch { Start-Sleep -Seconds 2 }
    }
    return $false
}

function Start-Svc([string]$Name, [string]$Module, [int]$Port) {
    $log = Join-Path $LogDir "$Name.log"
    if (Test-Path $log) { Remove-Item $log -Force }
    Write-Host "  -> $Name (port $Port)..." -ForegroundColor Gray
    $p = Start-Process -FilePath "cmd.exe" `
        -ArgumentList "/c", "mvn -pl $Module spring-boot:run -DskipTests > `"$log`" 2>&1" `
        -WorkingDirectory $Backend `
        -WindowStyle Hidden `
        -PassThru
    return $p.Id
}

$Args0 = $args
if ($Args0.Count -eq 0) {
    # Liste par defaut
    $services = @(
        @{ Name = "discovery";   Module = "discovery-service";   Port = 8761 }
        @{ Name = "auth";        Module = "auth-service";        Port = 8081 }
        @{ Name = "gateway";     Module = "gateway-service";     Port = 8080 }
        @{ Name = "billing";     Module = "billing-service";     Port = 8090 }
        @{ Name = "ticket";      Module = "ticket-service";      Port = 8082 }
        @{ Name = "dashboard";   Module = "dashboard-service";   Port = 8087 }
        @{ Name = "workflow";    Module = "workflow-service";    Port = 8083 }
        @{ Name = "dataroom";    Module = "dataroom-service";    Port = 8084 }
        @{ Name = "ai";          Module = "ai-service";          Port = 8085 }
        @{ Name = "supervision"; Module = "supervision-service"; Port = 8086 }
    )
} else {
    $allSvc = @{
        discovery   = @{ Name = "discovery";   Module = "discovery-service";   Port = 8761 }
        auth        = @{ Name = "auth";        Module = "auth-service";        Port = 8081 }
        gateway     = @{ Name = "gateway";     Module = "gateway-service";     Port = 8080 }
        billing     = @{ Name = "billing";     Module = "billing-service";     Port = 8090 }
        ticket      = @{ Name = "ticket";      Module = "ticket-service";      Port = 8082 }
        dashboard   = @{ Name = "dashboard";   Module = "dashboard-service";   Port = 8087 }
        workflow    = @{ Name = "workflow";    Module = "workflow-service";    Port = 8083 }
        dataroom    = @{ Name = "dataroom";    Module = "dataroom-service";    Port = 8084 }
        ai          = @{ Name = "ai";          Module = "ai-service";          Port = 8085 }
        supervision = @{ Name = "supervision"; Module = "supervision-service"; Port = 8086 }
    }
    $services = $Args0 | ForEach-Object { $allSvc[$_] }
}

foreach ($s in $services) {
    $pid_ = Start-Svc -Name $s.Name -Module $s.Module -Port $s.Port
    Write-Host "     pid=$pid_ log=$LogDir\$($s.Name).log"
    if ($s.Name -eq "discovery") {
        Write-Host "  Attente discovery (max 90s)..." -ForegroundColor Yellow
        if (Wait-Health "http://localhost:8761/actuator/health" 90) {
            Write-Host "  discovery UP" -ForegroundColor Green
        } else {
            Write-Host "  discovery TIMEOUT — voir $LogDir\discovery.log" -ForegroundColor Red
        }
    } else {
        Start-Sleep -Seconds 3
    }
}

Write-Host "`nTous les services lances. Logs : $LogDir" -ForegroundColor Cyan
