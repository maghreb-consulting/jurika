# Redemarre dataroom-service (8084) + ai-service (8085) apres le lot
# "Dashboards (nom du dataroom + requetes + employe responsable) & correctifs chatbot" (2026-07-25).
#  - dataroom-service : nouvel endpoint GET /api/v1/dataroom/supervision/demandes (vue superviseur enrichie).
#  - ai-service       : @EnableMethodSecurity + restriction /chatbot/sources (EMPLOYE/SUPERVISEUR/SUPER_ADMIN).
# Pre-requis : stack postgres/redis/rabbit deja up ; jars installes (mvn install prealable).
# Usage : pwsh .\scripts\restart-dashboards-requetes-chatbot-2026-07-25.ps1

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

foreach ($svc in @(@{name="dataroom-service"; port=8084}, @{name="ai-service"; port=8085})) {
    Write-Host ""
    Write-Host "=== RESTART $($svc.name) (port $($svc.port)) ===" -ForegroundColor Magenta
    Stop-PortIfBusy $svc.port $svc.name
    $cmd = "$envSetup cd '$Backend'; mvn -pl $($svc.name) spring-boot:run -DskipTests"
    Start-Process powershell -ArgumentList "-NoExit", "-Command", $cmd -WindowStyle Normal
    Start-Sleep -Seconds 3
}

Write-Host ""
Write-Host "dataroom (8084) + ai (8085) relances en fenetres separees. Patiente ~60s puis /actuator/health." -ForegroundColor Green
