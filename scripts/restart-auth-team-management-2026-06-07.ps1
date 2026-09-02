# Redemarre auth-service pour appliquer BUG 6 (feat/team-management-2026-06-07) :
#  - migration V27 (users.status PENDING/ACTIVE/INACTIVE + trigger is_active)
#  - GET /api/v1/auth/users + PATCH /api/v1/auth/users/{id}/status
#  - LoginUseCase transition PENDING -> ACTIVE
#  - PlanLimitsService.countUsers exclut INACTIVE
#
# Usage :  pwsh .\scripts\restart-auth-team-management-2026-06-07.ps1
# Pre-requis : stack postgres/redis/rabbit deja up (compose hote).

$ErrorActionPreference = "Stop"
$Root = Resolve-Path "$PSScriptRoot\.."
$Backend = Join-Path $Root "backend-java"

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

Write-Host ""
Write-Host "=== STOP auth-service ===" -ForegroundColor Magenta
Stop-PortIfBusy 8081 "auth-service"

Write-Host ""
Write-Host "=== START auth-service (nouvelle fenetre, logs visibles) ===" -ForegroundColor Magenta
$envSetup = ""
Get-ChildItem env: | Where-Object { $_.Name -match '^(SMTP|SPRING|JWT|AES|JURIKA|RABBIT|REDIS|POSTGRES|CORS|FRONTEND|VITE|EUREKA|MINIO|STRIPE|BILLING|TWILIO|SMS|OCR|OLLAMA|GROQ|RAG|LLM|TEMP|TMP)' } | ForEach-Object {
    $val = $_.Value -replace "'", "''"
    $envSetup += "`$env:$($_.Name)='$val'; "
}
$cmd = "$envSetup cd '$Backend'; mvn -pl auth-service -am spring-boot:run -DskipTests"
Start-Process powershell -ArgumentList "-NoExit", "-Command", $cmd -WindowStyle Normal

Write-Host ""
Write-Host "Patiente ~45s puis lance : node scripts/e2e-team-management-2026-06-07.mjs" -ForegroundColor Green
