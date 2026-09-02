# JURIKA — Script unique pour préparer la démo cabinets amis.
#
# Enchaîne :
#   1. Démarre les infra Docker (postgres + redis + rabbit + minio + mailhog)
#   2. Spawn les 9 services Java en background (logs dans .tmp/logs/)
#   3. Attend que tous les ports soient UP
#   4. Active jurika.test.seed.enabled et seed 2 cabinets demo
#   5. Affiche les credentials prêts à partager
#   6. Optionnel : lance le tunnel Cloudflare
#
# Usage simple : .\scripts\start-demo.ps1
# Avec tunnel  : .\scripts\start-demo.ps1 -Tunnel

param(
    [switch]$Tunnel,
    [switch]$SkipDocker
)

$ErrorActionPreference = "Stop"
$Root = Resolve-Path "$PSScriptRoot\.."
$Backend = Join-Path $Root "backend-java"
$Infra = Join-Path $Root "infrastructure"
$LogDir = Join-Path $Root ".tmp\logs"
New-Item -ItemType Directory -Path $LogDir -Force | Out-Null

# 1. Charge env (.env + .env.local)
$env:TEMP = "$Root\.tmp"; $env:TMP = $env:TEMP
foreach ($f in @("$Root\.env", "$Root\.env.local")) {
    if (Test-Path $f) {
        Get-Content $f | ForEach-Object {
            $l = $_.Trim()
            if ($l -and -not $l.StartsWith("#") -and $l.Contains("=")) {
                $i=$l.IndexOf("="); $k=$l.Substring(0,$i).Trim(); $v=$l.Substring($i+1).Trim()
                if ($v.StartsWith('"') -and $v.EndsWith('"')) { $v=$v.Substring(1,$v.Length-2) }
                [Environment]::SetEnvironmentVariable($k,$v,"Process")
            }
        }
    }
}
Write-Host "[1/5] Env charge" -ForegroundColor Green

# 2. Docker infra
if (-not $SkipDocker) {
    Write-Host "[2/5] Demarrage Docker infra (postgres, redis, rabbit, minio, mailhog)..." -ForegroundColor Cyan
    Push-Location $Infra
    docker-compose up -d postgres redis rabbitmq minio mailhog 2>&1 | Out-Null
    Pop-Location
    Start-Sleep -Seconds 5
}

# 3. Spawn les services (kill les existants d'abord)
$svc = @(
    @{ Module="discovery-service"; Label="discovery"; Port=8761 }
    @{ Module="gateway-service";  Label="gateway";  Port=8080 }
    @{ Module="auth-service";     Label="auth";     Port=8081 }
    @{ Module="ticket-service";   Label="ticket";   Port=8082 }
    @{ Module="workflow-service"; Label="workflow"; Port=8083 }
    @{ Module="dataroom-service"; Label="dataroom"; Port=8084 }
    @{ Module="ai-service";       Label="ai";       Port=8085 }
    @{ Module="supervision-service"; Label="supervision"; Port=8086 }
    @{ Module="dashboard-service";  Label="dashboard";    Port=8087 }
)
Write-Host "[3/5] Demarrage des $($svc.Count) services Java en background..." -ForegroundColor Cyan
foreach ($s in $svc) {
    $existing = (Get-NetTCPConnection -State Listen -LocalPort $s.Port -ErrorAction SilentlyContinue).OwningProcess
    if ($existing) {
        Write-Host "  [stop] $($s.Label) PID=$existing (etait deja UP)" -ForegroundColor DarkYellow
        Stop-Process -Id $existing -Force -ErrorAction SilentlyContinue
        Start-Sleep -Milliseconds 500
    }
    $logFile = "$LogDir\$($s.Label).log"
    if (Test-Path $logFile) { Remove-Item $logFile -Force }
    $p = Start-Process -FilePath "mvn.cmd" `
        -ArgumentList "-pl",$s.Module,"spring-boot:run","-DskipTests" `
        -WorkingDirectory $Backend `
        -RedirectStandardOutput $logFile `
        -RedirectStandardError "$LogDir\$($s.Label).err.log" `
        -WindowStyle Hidden -PassThru
    Write-Host "  [run] $($s.Label) PID=$($p.Id) port=$($s.Port)" -ForegroundColor Green
}

# 4. Attend que tous les ports soient UP (max 3 min)
Write-Host "[4/5] Attente que tous les services soient UP (timeout 3 min)..." -ForegroundColor Cyan
$deadline = (Get-Date).AddMinutes(3)
$allUp = $false
while ((Get-Date) -lt $deadline) {
    $upCount = (@($svc.Port | ForEach-Object { Get-NetTCPConnection -State Listen -LocalPort $_ -ErrorAction SilentlyContinue }) | Measure-Object).Count
    if ($upCount -eq $svc.Count) { $allUp = $true; break }
    Write-Host "    $upCount / $($svc.Count) services UP..." -ForegroundColor DarkGray
    Start-Sleep -Seconds 10
}
if (-not $allUp) {
    Write-Host "Pas tous UP apres 3 min. Verifie les logs dans $LogDir" -ForegroundColor Red
    Get-ChildItem $LogDir | Select-Object Name, Length, LastWriteTime
    exit 1
}
Write-Host "Tous les services UP" -ForegroundColor Green

# 5. Seed cabinets demo
Write-Host "[5/5] Seed 2 cabinets demo..." -ForegroundColor Cyan
Start-Sleep -Seconds 15  # laisse le temps a Eureka de propager
& "$PSScriptRoot\seed-demo-cabinets.ps1"

# Optionnel : tunnel Cloudflare
if ($Tunnel) {
    Write-Host ""
    Write-Host "Demarrage tunnel Cloudflare..." -ForegroundColor Cyan
    & "$PSScriptRoot\share-via-cloudflare-tunnel.ps1"
}

Write-Host ""
Write-Host "================================" -ForegroundColor Green
Write-Host "Demo prete !" -ForegroundColor Green
Write-Host "  Frontend  : http://localhost:5173" -ForegroundColor White
Write-Host "  API       : http://localhost:8080" -ForegroundColor White
Write-Host "  MailHog   : http://localhost:8025" -ForegroundColor White
Write-Host "  Eureka    : http://localhost:8761" -ForegroundColor White
Write-Host "  RabbitMQ  : http://localhost:15672" -ForegroundColor White
Write-Host ""
Write-Host "Credentials cabinets demo : scripts\demo-credentials.txt" -ForegroundColor White
Write-Host "Guide a envoyer aux amis  : docs\v2\Guide_Demo_Cabinets_Amis.md" -ForegroundColor White
Write-Host "================================" -ForegroundColor Green
