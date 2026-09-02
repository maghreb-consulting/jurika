# JURIKA -- Demarrage SERVEUR (acces LAN par IPv4)
# Meme principe que start-all.ps1 (services en processus individuels, terminaux visibles),
# MAIS configure pour l'acces reseau : URLs VITE_* / CORS sur l'IP du serveur + Vite --host.
# Usage : .\scripts\start-server.ps1   (optionnel : -LanHost 192.168.1.250)

param([string]$LanHost = "")

$ErrorActionPreference = "Stop"
$Root = Resolve-Path "$PSScriptRoot\.."
$Backend = Join-Path $Root "backend-java"
$Frontend = Join-Path $Root "frontend-react"
$RealtimeNode = Join-Path $Root "backend-node"
$OcrPy = Join-Path $Root "backend-python\ocr-service"
$KiePy = Join-Path $Root "backend-python\kie-service"
$Infra = Join-Path $Root "infrastructure"
$EnvFile = Join-Path $Root ".env"
$EnvLocalFile = Join-Path $Root ".env.local"

$ProjectTmp = Join-Path $Root ".tmp"
New-Item -ItemType Directory -Path $ProjectTmp -Force | Out-Null
$env:TEMP = $ProjectTmp
$env:TMP = $ProjectTmp
$EnvVarsFile = Join-Path $ProjectTmp 'env-vars.json'
$EnvVarsMap = @{ TEMP = $ProjectTmp; TMP = $ProjectTmp }

function Load-EnvFile($path) {
    if (-not (Test-Path $path)) { return @{} }
    $local = @{}
    Get-Content $path | ForEach-Object {
        $line = $_.Trim()
        if ($line -and -not $line.StartsWith("#") -and $line.Contains("=")) {
            $idx = $line.IndexOf("=")
            $key = $line.Substring(0, $idx).Trim()
            $val = $line.Substring($idx + 1).Trim()
            if ($val.StartsWith('"') -and $val.EndsWith('"')) { $val = $val.Substring(1, $val.Length - 2) }
            $local[$key] = $val
        }
    }
    return $local
}

foreach ($kv in (Load-EnvFile $EnvFile).GetEnumerator())      { $EnvVarsMap[$kv.Key] = $kv.Value }
foreach ($kv in (Load-EnvFile $EnvLocalFile).GetEnumerator()) { $EnvVarsMap[$kv.Key] = $kv.Value }

# --- Determination de l'IP du serveur ---
if (-not $LanHost) { $LanHost = $EnvVarsMap['JURIKA_LAN_HOST'] }
if (-not $LanHost) {
    $LanHost = (Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue |
        Where-Object { $_.IPAddress -notmatch '^127\.' -and $_.IPAddress -notmatch '^169\.254\.' } |
        Sort-Object -Property PrefixOrigin -Descending | Select-Object -First 1).IPAddress
}
if (-not $LanHost) {
    Write-Host "IMPOSSIBLE de determiner l'IP du serveur. Relance avec -LanHost <IP> ou renseigne JURIKA_LAN_HOST dans .env.local." -ForegroundColor Red
    exit 1
}

# --- Override des URLs pour l'acces LAN (IPv4) ---
$EnvVarsMap['JURIKA_LAN_HOST']      = $LanHost
$EnvVarsMap['VITE_API_URL']         = "http://${LanHost}:8080/api/v1"
$EnvVarsMap['VITE_REALTIME_URL']    = "http://${LanHost}:3000"
$EnvVarsMap['FRONTEND_URL']         = "http://${LanHost}:5173"
$EnvVarsMap['CORS_ALLOWED_ORIGINS'] = "http://${LanHost}:5173,http://localhost:5173"

Write-Host "Mode SERVEUR (LAN) -- IP du serveur : $LanHost" -ForegroundColor Cyan
Write-Host "  VITE_API_URL       = $($EnvVarsMap['VITE_API_URL'])" -ForegroundColor DarkGray
Write-Host "  VITE_REALTIME_URL  = $($EnvVarsMap['VITE_REALTIME_URL'])" -ForegroundColor DarkGray
Write-Host "  CORS_ALLOWED_ORIGINS = $($EnvVarsMap['CORS_ALLOWED_ORIGINS'])" -ForegroundColor DarkGray
$ragChatOk = ($EnvVarsMap['RAG_ENABLED'] -eq 'true' -and $EnvVarsMap['RAG_CHAT_API_KEY'] -and $EnvVarsMap['RAG_CHAT_API_KEY'] -notmatch 'COLLE_|REPLACE')
$ragEmbedOk = ($EnvVarsMap['RAG_EMBED_API_KEY'] -and $EnvVarsMap['RAG_EMBED_API_KEY'] -notmatch 'COLLE_|REPLACE')
if ($ragChatOk -and $ragEmbedOk) {
    Write-Host "  RAG chatbot        = LLM actif (chat Groq + embeddings Gemini)" -ForegroundColor Green
} elseif ($ragChatOk) {
    Write-Host "  RAG chatbot        = chat LLM OK, cle EMBEDDINGS Gemini manquante -> recherche FTS (reingere apres l'avoir mise)" -ForegroundColor Yellow
} else {
    Write-Host "  RAG chatbot        = FTS + base de connaissances (RAG_ENABLED=false ou cle chat absente)" -ForegroundColor Yellow
}

# 1) Inject dans la session courante + 2) serialise pour les sous-processus
foreach ($kv in $EnvVarsMap.GetEnumerator()) {
    [System.Environment]::SetEnvironmentVariable($kv.Key, $kv.Value, "Process")
}
$EnvVarsMap | ConvertTo-Json -Depth 1 | Set-Content -LiteralPath $EnvVarsFile -Encoding UTF8
$EnvExports = @"
`$envMap = Get-Content -LiteralPath '$EnvVarsFile' -Raw | ConvertFrom-Json;
foreach (`$p in `$envMap.PSObject.Properties) {
    [System.Environment]::SetEnvironmentVariable(`$p.Name, `$p.Value, 'Process')
};
"@

function Stop-PortIfBusy([int]$Port) {
    $proc = (Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue).OwningProcess
    if ($proc) { Stop-Process -Id $proc -Force -ErrorAction SilentlyContinue; Start-Sleep -Milliseconds 500 }
}
function Wait-ForHealth([string]$Url, [int]$TimeoutSec = 60) {
    $sw = [Diagnostics.Stopwatch]::StartNew()
    while ($sw.Elapsed.TotalSeconds -lt $TimeoutSec) {
        try { if ((Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 2).StatusCode -eq 200) { return $true } }
        catch { Start-Sleep -Seconds 2 }
    }
    return $false
}

Write-Host "`n[1/6] Liberation des ports..." -ForegroundColor Green
8080,8081,8082,8083,8084,8085,8086,8087,8088,8089,8090,8761,3000,5173 | ForEach-Object { Stop-PortIfBusy $_ }

Write-Host "`n[2/6] Infra (Docker : Postgres/Redis/RabbitMQ/MinIO/Mailhog)..." -ForegroundColor Green
Push-Location $Infra; docker compose up -d; Pop-Location

Write-Host "`n[3/6] Install parent POM + jurika-common..." -ForegroundColor Green
Push-Location $Backend
mvn install -N -DskipTests -q
if ($LASTEXITCODE -ne 0) { Pop-Location; Write-Host "Echec parent POM" -ForegroundColor Red; exit 1 }
mvn -pl jurika-common install -DskipTests -q
if ($LASTEXITCODE -ne 0) { Pop-Location; Write-Host "Echec jurika-common" -ForegroundColor Red; exit 1 }
Pop-Location

Write-Host "`n[4/6] Microservices Java..." -ForegroundColor Green
$services = @(
    @{ Name="discovery"; Module="discovery-service"; Port=8761 },
    @{ Name="auth"; Module="auth-service"; Port=8081 },
    @{ Name="gateway"; Module="gateway-service"; Port=8080 },
    @{ Name="billing"; Module="billing-service"; Port=8090 },
    @{ Name="ticket"; Module="ticket-service"; Port=8082 },
    @{ Name="dashboard"; Module="dashboard-service"; Port=8087 },
    @{ Name="workflow"; Module="workflow-service"; Port=8083 },
    @{ Name="dataroom"; Module="dataroom-service"; Port=8084 },
    @{ Name="ai"; Module="ai-service"; Port=8085 },
    @{ Name="supervision"; Module="supervision-service"; Port=8086 }
)
foreach ($s in $services) {
    Write-Host "  -> $($s.Name) (port $($s.Port))" -ForegroundColor Gray
    $cmd = "$EnvExports Set-Location '$Backend'; mvn -pl $($s.Module) spring-boot:run"
    Start-Process powershell -ArgumentList "-NoExit","-Command",$cmd
    if ($s.Name -eq "discovery") { Wait-ForHealth "http://localhost:8761/actuator/health" 60 | Out-Null; Start-Sleep -Seconds 3 }
    else { Start-Sleep -Seconds 2 }
}

Write-Host "`n[4.5/6] ocr-service Python (port 8089)..." -ForegroundColor Green
$ocrVenvPy = Join-Path $OcrPy ".venv\Scripts\python.exe"
if ((Test-Path (Join-Path $OcrPy "app\main.py")) -and (Test-Path $ocrVenvPy)) {
    $ocrCmd = "$EnvExports Set-Location '$OcrPy'; & '$ocrVenvPy' -m uvicorn app.main:app --host 0.0.0.0 --port 8089"
    Start-Process powershell -ArgumentList "-NoExit","-Command",$ocrCmd
} else { Write-Host "  ocr-service inactif (venv absent) -- fallback vision/manuel." -ForegroundColor DarkYellow }

Write-Host "`n[4.6/6] kie-service Python (port 8088, Donut CIN/CN)..." -ForegroundColor Green
$kieVenvPy = Join-Path $KiePy ".venv\Scripts\python.exe"
$donutDir = $EnvVarsMap['DONUT_MODEL_DIR']
if ((Test-Path (Join-Path $KiePy "app\main.py")) -and (Test-Path $kieVenvPy) -and $donutDir -and (Test-Path $donutDir)) {
    $kieCmd = "$EnvExports Set-Location '$KiePy'; & '$kieVenvPy' -m uvicorn app.main:app --host 0.0.0.0 --port 8088"
    Start-Process powershell -ArgumentList "-NoExit","-Command",$kieCmd
} else { Write-Host "  kie-service inactif (venv/DONUT_MODEL_DIR absent) -- extraction CIN/CN indisponible." -ForegroundColor DarkYellow }

Write-Host "`n[5/6] realtime-service Node (port 3000)..." -ForegroundColor Green
if (Test-Path (Join-Path $RealtimeNode "package.json")) {
    if (-not (Test-Path (Join-Path $RealtimeNode "node_modules"))) { Push-Location $RealtimeNode; npm install --silent; Pop-Location }
    $realtimeCmd = "$EnvExports Set-Location '$RealtimeNode'; npm start"
    Start-Process powershell -ArgumentList "-NoExit","-Command",$realtimeCmd
}

Write-Host "`n[6/6] Frontend Vite (port 5173, --host pour l'acces LAN)..." -ForegroundColor Green
if (-not (Test-Path (Join-Path $Frontend "node_modules"))) { Push-Location $Frontend; npm install --silent; Pop-Location }
# --host 0.0.0.0 : Vite ecoute sur toutes les interfaces -> joignable depuis les autres postes.
$frontCmd = "$EnvExports Set-Location '$Frontend'; npm run dev -- --host 0.0.0.0"
Start-Process powershell -ArgumentList "-NoExit","-Command",$frontCmd

Write-Host "`n================================================" -ForegroundColor Cyan
Write-Host " JURIKA -- stack SERVEUR en cours de demarrage" -ForegroundColor Cyan
Write-Host "================================================" -ForegroundColor Cyan
Write-Host " Depuis un AUTRE poste du reseau :" -ForegroundColor Green
Write-Host "   Application  : http://${LanHost}:5173" -ForegroundColor Green
Write-Host " Sur le serveur (diagnostic) :"
Write-Host "   Gateway      : http://localhost:8080/actuator/health"
Write-Host "   AI / Chatbot : http://localhost:8085/actuator/health"
Write-Host "   Realtime WS  : http://localhost:3000/health"
Write-Host "   Eureka       : http://localhost:8761"
Write-Host "================================================" -ForegroundColor Cyan
Write-Host " Rappel : pare-feu ouvert (5173,8080,3000,8085...) + meme reseau LAN." -ForegroundColor DarkGray
