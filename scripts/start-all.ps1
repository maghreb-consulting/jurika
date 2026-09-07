# JURIKA -- Script de demarrage Windows PowerShell
# Usage : .\scripts\start-all.ps1

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

# Redirection TEMP/TMP vers .tmp/ projet (contourne Controlled Folder Access pour AF_UNIX JVM NIO)
$ProjectTmp = Join-Path $Root ".tmp"
New-Item -ItemType Directory -Path $ProjectTmp -Force | Out-Null
$env:TEMP = $ProjectTmp
$env:TMP = $ProjectTmp

# Fix 2026-06-07 : on collectait les vars dans une string PowerShell concatenee
# avec quotes simples ($env:KEY='VAL';). Si VAL contenait une quote simple (cle
# Brevo 90 chars random => 1 chance sur 4), la string etait invalide et les
# sous-processus mvn demarraient SANS les vars d'env -> JVM utilisait les
# fallbacks application.yml (SMTP_HOST=localhost) -> mails partaient dans le vide.
#
# Nouvelle approche : on serialise les vars dans un fichier JSON temporaire, et
# chaque sous-processus le re-injecte au demarrage. Plus de quoting, plus de bug.
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

# Precedence : .env (defauts) puis .env.local (overrides)
foreach ($kv in (Load-EnvFile $EnvFile).GetEnumerator())      { $EnvVarsMap[$kv.Key] = $kv.Value }
foreach ($kv in (Load-EnvFile $EnvLocalFile).GetEnumerator()) { $EnvVarsMap[$kv.Key] = $kv.Value }

# 1) Inject dans la session PowerShell courante
foreach ($kv in $EnvVarsMap.GetEnumerator()) {
    [System.Environment]::SetEnvironmentVariable($kv.Key, $kv.Value, "Process")
}

# 2) Serialise en JSON pour les sous-processus (immune au quoting)
$EnvVarsMap | ConvertTo-Json -Depth 1 | Set-Content -LiteralPath $EnvVarsFile -Encoding UTF8

# 3) Bloc PowerShell injecte dans chaque sous-processus pour recharger ces vars
$EnvExports = @"
`$envMap = Get-Content -LiteralPath '$EnvVarsFile' -Raw | ConvertFrom-Json;
foreach (`$p in `$envMap.PSObject.Properties) {
    [System.Environment]::SetEnvironmentVariable(`$p.Name, `$p.Value, 'Process')
};
"@

Write-Host ".env / .env.local charges : $($EnvVarsMap.Count) vars (via JSON, immune au quoting)" -ForegroundColor DarkGray

# Sanity check des vars SMTP critiques : si SMTP_HOST se termine en brevo, on les affiche
if ($EnvVarsMap['SMTP_HOST'] -match 'brevo|sendinblue') {
    Write-Host "  SMTP_HOST = $($EnvVarsMap['SMTP_HOST']):$($EnvVarsMap['SMTP_PORT'])" -ForegroundColor Green
    Write-Host "  SMTP_USER = $($EnvVarsMap['SMTP_USER'])" -ForegroundColor Green
    Write-Host "  SMTP_FROM = $($EnvVarsMap['SMTP_FROM'])" -ForegroundColor Green
    $pwdLen = if ($EnvVarsMap['SMTP_PASSWORD']) { $EnvVarsMap['SMTP_PASSWORD'].Length } else { 0 }
    Write-Host "  SMTP_PASSWORD = *** ($pwdLen chars)" -ForegroundColor Green
    if ($EnvVarsMap['JURIKA_SIGNUP_DEFER_CREDENTIALS_ALLOWED'] -eq 'false') {
        Write-Host "  JURIKA_SIGNUP_DEFER_CREDENTIALS_ALLOWED = false (mail welcome AU signup)" -ForegroundColor Green
    } else {
        Write-Host "  JURIKA_SIGNUP_DEFER_CREDENTIALS_ALLOWED = $($EnvVarsMap['JURIKA_SIGNUP_DEFER_CREDENTIALS_ALLOWED']) (mail differe jusqu'au paiement)" -ForegroundColor Yellow
    }
    if ($EnvVarsMap['JURIKA_SIGNUP_AUTO_VERIFY_EMAIL'] -eq 'true') {
        Write-Host "  JURIKA_SIGNUP_AUTO_VERIFY_EMAIL = true (email auto-verifie, pas de click sur lien)" -ForegroundColor Green
    } else {
        Write-Host "  JURIKA_SIGNUP_AUTO_VERIFY_EMAIL = $($EnvVarsMap['JURIKA_SIGNUP_AUTO_VERIFY_EMAIL']) (verif email obligatoire avant 2FA)" -ForegroundColor Yellow
    }
}

# Voir la note de stop-all.ps1 : quand la pile CONTENEURISEE tourne, ces ports
# sont tenus par les publicateurs de Docker Desktop. Les tuer tue le moteur.
$ProcessusProteges = @(
    'com.docker.backend', 'com.docker.service', 'com.docker.build',
    'Docker Desktop', 'wslrelay', 'wslhost', 'vpnkit', 'vpnkit-bridge', 'dockerd'
)

function Stop-PortIfBusy([int]$Port) {
    $procId = (Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue).OwningProcess |
              Select-Object -First 1
    if (-not $procId) { return }
    $p = Get-Process -Id $procId -ErrorAction SilentlyContinue
    if (-not $p) { return }
    if ($ProcessusProteges -contains $p.Name) {
        Write-Host "Port $Port tenu par $($p.Name) (Docker) : NON tue." -ForegroundColor Cyan
        return
    }
    Write-Host "Liberation du port $Port (PID $procId, $($p.Name))..." -ForegroundColor Yellow
    Stop-Process -Id $procId -Force -ErrorAction SilentlyContinue
    Start-Sleep -Milliseconds 500
}

function Wait-ForHealth([string]$Url, [int]$TimeoutSec = 60) {
    $sw = [Diagnostics.Stopwatch]::StartNew()
    while ($sw.Elapsed.TotalSeconds -lt $TimeoutSec) {
        try {
            $r = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 2
            if ($r.StatusCode -eq 200) { return $true }
        } catch { Start-Sleep -Seconds 2 }
    }
    return $false
}

Write-Host "================================================" -ForegroundColor Cyan
Write-Host " JURIKA -- demarrage de la stack complete V2" -ForegroundColor Cyan
Write-Host "================================================" -ForegroundColor Cyan

# 0. Une seule pile a la fois.
#
# Ce script est le mode HOTE (JVM lancees par Maven). La pile CONTENEURISEE vit
# sous le projet Compose `jurika-local` et utilise les MEMES ports. Lancer les
# deux ne peut pas marcher : les JVM hote squattent les ports publies, les
# conteneurs perdent leur publication en silence, et l'interface tape dans le
# vide. Depuis le lot 1, la pile de reference est la pile conteneurisee.
$pileConteneurs = @(docker ps -q --filter 'label=com.docker.compose.project=jurika-local' 2>$null)
if ($pileConteneurs.Count -gt 0) {
    Write-Host ''
    Write-Host "  REFUS : $($pileConteneurs.Count) conteneur(s) de la pile 'jurika-local' tournent deja." -ForegroundColor Red
    Write-Host '  Le mode hote et la pile conteneurisee partagent les memes ports.' -ForegroundColor Yellow
    Write-Host ''
    Write-Host '  Pour (re)demarrer la pile conteneurisee :' -ForegroundColor Cyan
    Write-Host '    .\scripts\start-local.ps1 -NoBuild' -ForegroundColor White
    Write-Host ''
    Write-Host '  Pour forcer malgre tout le mode hote, arretez d abord la pile :' -ForegroundColor Cyan
    Write-Host '    docker compose -p jurika-local stop     (jamais down -v)' -ForegroundColor White
    Write-Host ''
    exit 1
}

# 1. Liberer les ports (tous les services V2)
Write-Host "`n[1/6] Liberation des ports occupes..." -ForegroundColor Green
8080, 8081, 8082, 8083, 8084, 8085, 8086, 8087, 8088, 8089, 8090, 8761, 3000, 5173, 5174, 5175 | ForEach-Object { Stop-PortIfBusy $_ }

# 1bis. Detection : plage 8080-8089 RESERVEE par Windows (Hyper-V/Docker/WSL) -> bind interdit
#       (WinError 10013 / "port already in use"). Ce n'est PAS un process a tuer : c'est une
#       reservation systeme. Le correctif est unique, a faire dans un PowerShell ADMINISTRATEUR.
$exclOut = netsh interface ipv4 show excludedportrange protocol=tcp 2>$null | Out-String
$portReserved = $false
foreach ($mm in [regex]::Matches($exclOut, '(?m)^\s*(\d+)\s+(\d+)')) {
    $lo = [int]$mm.Groups[1].Value; $hi = [int]$mm.Groups[2].Value
    if (8085 -ge $lo -and 8085 -le $hi) { $portReserved = $true }
}
if ($portReserved) {
    Write-Host "  [!] Ports 8080-8089 RESERVES par Windows (Hyper-V/Docker) -> bind interdit (WinError 10013)." -ForegroundColor Yellow
    Write-Host "      Corrige-le UNE FOIS dans un PowerShell ADMIN :" -ForegroundColor Yellow
    Write-Host "        net stop winnat" -ForegroundColor Gray
    Write-Host "        netsh int ipv4 add excludedportrange protocol=tcp startport=8080 numberofports=11 store=persistent" -ForegroundColor Gray
    Write-Host "        net start winnat" -ForegroundColor Gray
    Write-Host "      (reserve 8080-8090 pour JURIKA ; a refaire seulement si la plage revient apres reboot)" -ForegroundColor Gray
}

# 2. Docker Compose
Write-Host "`n[2/6] Demarrage de l'infrastructure (Docker)..." -ForegroundColor Green
Push-Location $Infra
docker compose up -d
Pop-Location
Write-Host "Infra UP : PostgreSQL, Redis, RabbitMQ, MinIO, Mailhog" -ForegroundColor Gray

# 3. Build parent POM + jurika-common
Write-Host "`n[3/6] Install parent POM + jurika-common dans le repo local Maven..." -ForegroundColor Green
Push-Location $Backend
mvn install -N -DskipTests -q
if ($LASTEXITCODE -ne 0) {
    Pop-Location
    Write-Host "Echec install parent POM" -ForegroundColor Red
    exit 1
}
mvn -pl jurika-common install -DskipTests -q
if ($LASTEXITCODE -ne 0) {
    Pop-Location
    Write-Host "Echec install jurika-common" -ForegroundColor Red
    exit 1
}
Pop-Location

# 4. Demarrer les 8 microservices Java
Write-Host "`n[4/6] Lancement des 8 microservices Java..." -ForegroundColor Green

$services = @(
    @{ Name = "discovery";   Module = "discovery-service";   Port = 8761 },
    @{ Name = "auth";        Module = "auth-service";        Port = 8081 },
    @{ Name = "gateway";     Module = "gateway-service";     Port = 8080 },
    @{ Name = "billing";     Module = "billing-service";     Port = 8090 },
    @{ Name = "ticket";      Module = "ticket-service";      Port = 8082 },
    @{ Name = "dashboard";   Module = "dashboard-service";   Port = 8087 },
    @{ Name = "workflow";    Module = "workflow-service";    Port = 8083 },
    @{ Name = "dataroom";    Module = "dataroom-service";    Port = 8084 },
    @{ Name = "ai";          Module = "ai-service";          Port = 8085 },
    @{ Name = "supervision"; Module = "supervision-service"; Port = 8086 }
)

foreach ($s in $services) {
    Write-Host "  -> $($s.Name) (port $($s.Port))" -ForegroundColor Gray
    $cmd = "$EnvExports Set-Location '$Backend'; mvn -pl $($s.Module) spring-boot:run"
    Start-Process powershell -ArgumentList "-NoExit", "-Command", $cmd
    if ($s.Name -eq "discovery") {
        Write-Host "  Attente de discovery..." -ForegroundColor Gray
        Wait-ForHealth "http://localhost:8761/actuator/health" 60 | Out-Null
        Start-Sleep -Seconds 3
    } else {
        Start-Sleep -Seconds 2
    }
}

# 4.5 ocr-service Python (PaddleOCR / docTR) — voie OCR rapide CPU.
# Le service ai-service tolere son absence (fallback vision/manuel), donc on saute
# proprement si Python ou le venv ne sont pas dispos.
Write-Host "`n[4.5/6] Demarrage de l'ocr-service Python (port 8089)..." -ForegroundColor Green
$ocrVenvPy = Join-Path $OcrPy ".venv\Scripts\python.exe"
if (Test-Path (Join-Path $OcrPy "app\main.py")) {
    if (Test-Path $ocrVenvPy) {
        $ocrCmd = "$EnvExports Set-Location '$OcrPy'; & '$ocrVenvPy' -m uvicorn app.main:app --host 0.0.0.0 --port 8089"
        Start-Process powershell -ArgumentList "-NoExit", "-Command", $ocrCmd
        Write-Host "  ocr-service lance (engine=$($EnvVarsMap['OCR_ENGINE'] ?? 'doctr'))" -ForegroundColor Gray
    } else {
        Write-Host "  ocr-service venv absent ($ocrVenvPy) — voie OCR rapide INACTIVE (fallback vision)." -ForegroundColor DarkYellow
        Write-Host "  Setup : py -3.12 -m venv backend-python\ocr-service\.venv ; .venv\Scripts\activate ; pip install -r requirements.txt" -ForegroundColor DarkGray
    }
} else {
    Write-Host "  backend-python\ocr-service absent, skip" -ForegroundColor DarkGray
}

# 4.6 kie-service Python (Donut KIE pour CIN/CN marocaines) — branche feat/kie-service-ocr.
# Le service dataroom-service tolere son absence (endpoint identity/extract repond 503
# si KO, le reste de la Data Room continue). On saute proprement si Python ou le venv
# ne sont pas dispos.
Write-Host "`n[4.6/6] Demarrage du kie-service Python (port 8088)..." -ForegroundColor Green
$kieVenvPy = Join-Path $KiePy ".venv\Scripts\python.exe"
$donutDir = $EnvVarsMap['DONUT_MODEL_DIR']
if (Test-Path (Join-Path $KiePy "app\main.py")) {
    if (Test-Path $kieVenvPy) {
        if ($donutDir -and (Test-Path $donutDir)) {
            $kieCmd = "$EnvExports Set-Location '$KiePy'; & '$kieVenvPy' -m uvicorn app.main:app --host 0.0.0.0 --port 8088"
            Start-Process powershell -ArgumentList "-NoExit", "-Command", $kieCmd
            Write-Host "  kie-service lance (model=$donutDir)" -ForegroundColor Gray
        } else {
            Write-Host "  DONUT_MODEL_DIR introuvable ($donutDir) — kie-service INACTIF (extraction CIN/CN indisponible)." -ForegroundColor DarkYellow
            Write-Host "  Renseigner DONUT_MODEL_DIR dans .env.local et redemarrer." -ForegroundColor DarkGray
        }
    } else {
        Write-Host "  kie-service venv absent ($kieVenvPy) — extraction CIN/CN INACTIVE." -ForegroundColor DarkYellow
        Write-Host "  Setup : py -3.12 -m venv backend-python\kie-service\.venv ; .venv\Scripts\activate ; pip install --extra-index-url https://download.pytorch.org/whl/cpu -r requirements.txt" -ForegroundColor DarkGray
    }
} else {
    Write-Host "  backend-python\kie-service absent, skip" -ForegroundColor DarkGray
}

# 5. Node.js realtime-service (Socket.io + chat)
Write-Host "`n[5/6] Demarrage du realtime-service Node.js (port 3000)..." -ForegroundColor Green
if (Test-Path (Join-Path $RealtimeNode "package.json")) {
    if (-not (Test-Path (Join-Path $RealtimeNode "node_modules"))) {
        Write-Host "  Installation des dependances Node..." -ForegroundColor Gray
        Push-Location $RealtimeNode
        npm install --silent
        Pop-Location
    }
    $realtimeCmd = "$EnvExports Set-Location '$RealtimeNode'; npm start"
    Start-Process powershell -ArgumentList "-NoExit", "-Command", $realtimeCmd
} else {
    Write-Host "  backend-node absent, skip" -ForegroundColor DarkGray
}

# 6. Frontend Vite
Write-Host "`n[6/6] Demarrage du frontend Vite (port 5173)..." -ForegroundColor Green
$frontCmd = "$EnvExports Set-Location '$Frontend'; npm run dev"
Start-Process powershell -ArgumentList "-NoExit", "-Command", $frontCmd

Write-Host "`n================================================" -ForegroundColor Cyan
Write-Host " Stack en cours de demarrage." -ForegroundColor Cyan
Write-Host "================================================" -ForegroundColor Cyan
Write-Host " Eureka       : http://localhost:8761"
Write-Host " Gateway      : http://localhost:8080/actuator/health"
Write-Host " Auth         : http://localhost:8081/swagger-ui.html"
Write-Host " Billing      : http://localhost:8090"
Write-Host " Ticket       : http://localhost:8082"
Write-Host " Dashboard    : http://localhost:8087"
Write-Host " Workflow     : http://localhost:8083"
Write-Host " Data Room    : http://localhost:8084"
Write-Host " AI / Chatbot : http://localhost:8085"
Write-Host " Supervision  : http://localhost:8086"
Write-Host " OCR service  : http://localhost:8089/health (Python, voie rapide CPU)"
Write-Host " KIE service  : http://localhost:8088/health (Python, Donut CIN/CN — inactif si venv/DONUT_MODEL_DIR absent)"
Write-Host " Realtime WS  : http://localhost:3000/health"
Write-Host " Frontend     : http://localhost:5173"
Write-Host " RabbitMQ UI  : http://localhost:15672 (jurika / JurikaRabbit2026)"
Write-Host " MinIO UI     : http://localhost:9001 (jurika-admin / JurikaMinIO2026)"
Write-Host " Mailhog UI   : http://localhost:8025"
Write-Host "================================================" -ForegroundColor Cyan
