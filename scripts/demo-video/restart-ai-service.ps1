# Redemarre UNIQUEMENT ai-service (8085) en lui passant les variables RAG_* et LLM_*.
#
# Pourquoi ce script existe :
#   Les scripts de restart existants filtrent les variables d'environnement
#   transmises au processus enfant avec le motif
#     ^(SMTP|SPRING|JWT|AES|JURIKA|RABBIT|REDIS|POSTGRES|CORS|FRONTEND|VITE|
#       EUREKA|MINIO|STRIPE|BILLING|TWILIO|SMS|OCR|OLLAMA|GROQ|TEMP|TMP)
#   -> ni RAG_ENABLED ni RAG_CHAT_* / RAG_EMBED_* ne passent. Resultat : meme avec
#   RAG_ENABLED=true dans .env.local, ai-service demarre avec jurika.rag.enabled=false
#   et le chatbot reste en repli KB + Full-Text Search (ragMode="fts"), sans
#   generation ancree ni embeddings.
#
# Ce script ajoute RAG|LLM au motif. Rien d'autre ne change.
#
# Usage : pwsh .\scripts\demo-video\restart-ai-service.ps1

$ErrorActionPreference = "Stop"
$Root = Resolve-Path "$PSScriptRoot\..\.."
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

$envSetup = ""
Get-ChildItem env: | Where-Object {
    $_.Name -match '^(SMTP|SPRING|JWT|AES|JURIKA|RABBIT|REDIS|POSTGRES|CORS|FRONTEND|VITE|EUREKA|MINIO|STRIPE|BILLING|TWILIO|SMS|OCR|OLLAMA|GROQ|RAG|LLM|TEMP|TMP)'
} | ForEach-Object {
    $val = $_.Value -replace "'", "''"
    $envSetup += "`$env:$($_.Name)='$val'; "
}

$port = 8085
$procId = (Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue).OwningProcess
if ($procId) {
    Write-Host "Stopping ai-service (PID $procId, port $port)..." -ForegroundColor Yellow
    Stop-Process -Id $procId -Force -ErrorAction SilentlyContinue
    Start-Sleep -Seconds 3
}

$LogDir = Join-Path $Root ".logs"
New-Item -ItemType Directory -Path $LogDir -Force | Out-Null
$LogFile = Join-Path $LogDir "ai-service-demo-video.log"
$cmd = "$envSetup cd '$Backend'; mvn -pl ai-service spring-boot:run -DskipTests *>&1 | Tee-Object -FilePath '$LogFile'"
Start-Process powershell -ArgumentList "-NoExit", "-Command", $cmd -WindowStyle Minimized
Write-Host "Journal : $LogFile" -ForegroundColor DarkGray
Write-Host "ai-service relance (fenetre minimisee). Patiente ~60 s puis http://localhost:8085/actuator/health" -ForegroundColor Green
