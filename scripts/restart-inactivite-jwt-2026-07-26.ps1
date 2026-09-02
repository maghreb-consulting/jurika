# Redemarre auth-service (8081) apres le lot "Workflow Inactivite / Refresh / JWT"
# (2026-07-26) : access-ttl 8h -> 15m, garde-fou SessionTimingGuard (fail-fast si
# inactivity-window <= access-ttl), plafond absolu preserve a la rotation, refresh
# proactif front. Pre-requis : stack postgres/redis/rabbit up.
# Usage : pwsh .\scripts\restart-inactivite-jwt-2026-07-26.ps1

$ErrorActionPreference = "Stop"
$Root = Resolve-Path "$PSScriptRoot\.."
$Backend = Join-Path $Root "backend-java"

# Controlled Folder Access bloque AF_UNIX dans %TEMP% -> rediriger vers .tmp projet.
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

$envSetup = ""
Get-ChildItem env: | Where-Object { $_.Name -match '^(SMTP|SPRING|JWT|AES|JURIKA|RABBIT|REDIS|POSTGRES|CORS|FRONTEND|VITE|EUREKA|MINIO|STRIPE|BILLING|TWILIO|SMS|OCR|OLLAMA|GROQ|RAG|LLM|TEMP|TMP)' } | ForEach-Object {
    $val = $_.Value -replace "'", "''"
    $envSetup += "`$env:$($_.Name)='$val'; "
}

Write-Host ""
Write-Host "=== RESTART auth-service (port 8081) ===" -ForegroundColor Magenta
Stop-PortIfBusy 8081 "auth-service"
$cmd = "$envSetup cd '$Backend'; mvn -pl auth-service spring-boot:run -DskipTests"
Start-Process powershell -ArgumentList "-NoExit", "-Command", $cmd -WindowStyle Normal

Write-Host ""
Write-Host "auth (8081) relance en fenetre separee. Patiente ~45s puis /actuator/health." -ForegroundColor Green
