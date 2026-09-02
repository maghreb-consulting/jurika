# JURIKA — Redemarre auth-service + gateway pour appliquer :
#   1. Fix Setup2faRequiredEnforcer whitelist (/setup-2fa)
#   2. Endpoint GET /api/v1/auth/2fa/setup-options
#   3. Gateway route order (supervision-public-events AVANT auth-public)
#   4. .env.local rebascule sur MailHog (SMTP_HOST=localhost:1025) + URLs localhost
#
# Usage : depuis PowerShell admin (ou normal si pas d'AV bloquant) :
#   .\scripts\restart-after-fixes.ps1

$ErrorActionPreference = "Stop"
$Root = Resolve-Path "$PSScriptRoot\.."
$Backend = Join-Path $Root "backend-java"

# Charge .env puis .env.local (mêmes regles que start-all.ps1)
$ProjectTmp = Join-Path $Root ".tmp"
New-Item -ItemType Directory -Path $ProjectTmp -Force | Out-Null
$env:TEMP = $ProjectTmp; $env:TMP = $ProjectTmp

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

function Stop-PortIfBusy([int]$Port, [string]$Label) {
    $procId = (Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue).OwningProcess
    if ($procId) {
        Write-Host "Stopping $Label (PID $procId, port $Port)..." -ForegroundColor Yellow
        Stop-Process -Id $procId -Force -ErrorAction SilentlyContinue
        Start-Sleep -Seconds 2
    }
}

function Start-Service-Background([string]$Module, [int]$Port, [string]$Label) {
    Write-Host "Starting $Label ($Module port $Port) in background..." -ForegroundColor Cyan
    # Lance dans une NOUVELLE fenetre PowerShell pour que les logs soient visibles
    $envSetup = ""
    Get-ChildItem env: | Where-Object { $_.Name -match '^(SMTP|SPRING|JWT|AES|JURIKA|RABBIT|REDIS|POSTGRES|CORS|FRONTEND|VITE|EUREKA|MINIO|STRIPE|BILLING|TWILIO|SMS|OCR|OLLAMA|GROQ|RAG|LLM|TEMP|TMP)' } | ForEach-Object {
        $val = $_.Value -replace "'", "''"
        $envSetup += "`$env:$($_.Name)='$val'; "
    }
    $cmd = "$envSetup cd '$Backend'; mvn -pl $Module -am spring-boot:run -DskipTests"
    Start-Process powershell -ArgumentList "-NoExit", "-Command", $cmd -WindowStyle Normal
}

Write-Host ""
Write-Host "=== STOP des services à patcher ===" -ForegroundColor Magenta
Stop-PortIfBusy 8080 "gateway-service"
Stop-PortIfBusy 8081 "auth-service"

Write-Host ""
Write-Host "=== START en background (nouvelles fenetres) ===" -ForegroundColor Magenta
Start-Service-Background "gateway-service" 8080 "gateway-service"
Start-Service-Background "auth-service"    8081 "auth-service"

Write-Host ""
Write-Host "Patiente ~30s puis lance .\scripts\smoke-fixes-verify.ps1 pour valider." -ForegroundColor Green
