# JURIKA — Restart de tous les microservices Java backend.
#
# A executer apres TOUT changement de `jurika-common` :
#   * sans ca, les services downstream (ticket, workflow, dataroom,
#     supervision, dashboard, ai) gardent en memoire le bytecode de
#     jurika-common charge au demarrage initial, ce qui peut rendre la
#     verification JWT inconsistante / generer des 403 silencieux sur
#     /tickets, /workflows/start, /dataroom/dossiers.
#
# Fix branch : fix/workflow-start-403-2026-06-08 (cause racine confirmee
# par bissection v0.86.3-preprod → main 529366c).
#
# Usage :
#   pwsh .\scripts\restart-all-backend-2026-06-08.ps1
#
# Pre-requis : .env + .env.local charges, infra docker compose up, mvn
# install -DskipTests sur la racine backend-java effectue.

$ErrorActionPreference = "Stop"
$Root = Resolve-Path "$PSScriptRoot\.."
$Backend = Join-Path $Root "backend-java"
$LogDir = Join-Path $Root ".tmp"
New-Item -ItemType Directory -Path $LogDir -Force | Out-Null

# Redirection TEMP -> .tmp/ pour bypasser Controlled Folder Access NIO
# (cf memoire never-stop-winnat 2026-06-04).
$env:TEMP = $LogDir; $env:TMP = $LogDir

# Charge .env puis .env.local (defauts puis overrides locaux).
foreach ($f in @("$Root\.env", "$Root\.env.local")) {
    if (Test-Path $f) {
        Get-Content $f | ForEach-Object {
            $line = $_.Trim()
            if ($line -and -not $line.StartsWith("#") -and $line.Contains("=")) {
                $i = $line.IndexOf("=")
                $k = $line.Substring(0, $i).Trim()
                $v = $line.Substring($i + 1).Trim()
                if ($v.StartsWith('"') -and $v.EndsWith('"')) { $v = $v.Substring(1, $v.Length - 2) }
                [Environment]::SetEnvironmentVariable($k, $v, "Process")
            }
        }
        Write-Host "Charge $(Split-Path $f -Leaf)" -ForegroundColor DarkGray
    }
}

# (port, module, label)
$Services = @(
    @{ Port=8081; Module="auth-service";       Label="auth" },
    @{ Port=8082; Module="ticket-service";     Label="ticket" },
    @{ Port=8083; Module="workflow-service";   Label="workflow" },
    @{ Port=8084; Module="dataroom-service";   Label="dataroom" },
    @{ Port=8085; Module="ai-service";         Label="ai" },
    @{ Port=8086; Module="supervision-service";Label="supervision" },
    @{ Port=8087; Module="dashboard-service";  Label="dashboard" }
)

# 1) STOP all
Write-Host "`n=== STOP backend services ===" -ForegroundColor Magenta
foreach ($svc in $Services) {
    $procId = (Get-NetTCPConnection -State Listen -LocalPort $svc.Port -ErrorAction SilentlyContinue | Select-Object -First 1).OwningProcess
    if ($procId) {
        Stop-Process -Id $procId -Force -ErrorAction SilentlyContinue
        Write-Host "  killed $($svc.Label) (PID $procId on $($svc.Port))" -ForegroundColor Yellow
    } else {
        Write-Host "  $($svc.Label) already down ($($svc.Port))" -ForegroundColor DarkGray
    }
}

Start-Sleep -Seconds 3

# 2) START all in parallel (mvn spring-boot:run en arriere-plan).
Write-Host "`n=== START backend services (parallel, logs -> .tmp/) ===" -ForegroundColor Magenta
$mvn = "C:\Program Files\apache-maven-3.9.9\bin\mvn.cmd"
foreach ($svc in $Services) {
    $logFile = Join-Path $LogDir "$($svc.Label)-service-restart.log"
    "=== restart $(Get-Date -Format o) ===" | Out-File -FilePath $logFile
    $moduleDir = Join-Path $Backend $svc.Module
    $cmd = "cd /d `"$moduleDir`" && `"$mvn`" spring-boot:run -DskipTests >> `"$logFile`" 2>&1"
    Start-Process -FilePath "cmd.exe" -ArgumentList "/c", $cmd -WindowStyle Hidden | Out-Null
    Write-Host "  started $($svc.Label) -> $logFile" -ForegroundColor Cyan
}

# 3) WAIT until all healthy
Write-Host "`n=== WAIT actuator/health ===" -ForegroundColor Magenta
$deadline = (Get-Date).AddMinutes(5)
do {
    $allUp = $true
    foreach ($svc in $Services) {
        try {
            $resp = Invoke-WebRequest -Uri "http://localhost:$($svc.Port)/actuator/health" -TimeoutSec 2 -UseBasicParsing -ErrorAction Stop
            if ($resp.StatusCode -ne 200) { $allUp = $false }
        } catch { $allUp = $false }
    }
    if ($allUp) {
        Write-Host "  all 7 services UP" -ForegroundColor Green
        break
    }
    Start-Sleep -Seconds 6
} while ((Get-Date) -lt $deadline)

# 4) FINAL status
foreach ($svc in $Services) {
    try {
        $resp = Invoke-WebRequest -Uri "http://localhost:$($svc.Port)/actuator/health" -TimeoutSec 2 -UseBasicParsing -ErrorAction Stop
        Write-Host "  [OK] $($svc.Label) ($($svc.Port))" -ForegroundColor Green
    } catch {
        Write-Host "  [KO] $($svc.Label) ($($svc.Port)) -- check $LogDir\$($svc.Label)-service-restart.log" -ForegroundColor Red
    }
}

Write-Host "`nGateway (8080) doit etre demarre separement (start-all.ps1)." -ForegroundColor DarkGray
