# JURIKA -- SETUP SERVEUR (a lancer UNE SEULE FOIS, en PowerShell ADMINISTRATEUR)
# Installe les outils, ouvre le pare-feu, prepare les venvs Python + dependances npm + build de base.
# Ensuite : .\scripts\start-server.ps1  pour demarrer l'app (acces LAN par IPv4).
# ASCII pur (aucun accent / emoji) -> aucun probleme d'encodage.

$ErrorActionPreference = "Continue"
$Root = Resolve-Path "$PSScriptRoot\.."
$Backend = Join-Path $Root "backend-java"
$Frontend = Join-Path $Root "frontend-react"
$RealtimeNode = Join-Path $Root "backend-node"
$OcrPy = Join-Path $Root "backend-python\ocr-service"
$KiePy = Join-Path $Root "backend-python\kie-service"

# --- 0) Verifier les droits admin ---
$admin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltinRole]::Administrator)
if (-not $admin) {
    Write-Host "[ERREUR] Lance ce script en PowerShell ADMINISTRATEUR (clic droit -> Executer en tant qu'administrateur)." -ForegroundColor Red
    exit 1
}

Write-Host "==================================================" -ForegroundColor Cyan
Write-Host " JURIKA -- SETUP SERVEUR (installation initiale)" -ForegroundColor Cyan
Write-Host "==================================================" -ForegroundColor Cyan

$dockerWasPresent = [bool](Get-Command docker -ErrorAction SilentlyContinue)

# --- 1) Installer les outils (si absents) ---
function Ensure-Tool([string]$cmd, [string]$wingetId, [string]$label) {
    if (Get-Command $cmd -ErrorAction SilentlyContinue) {
        Write-Host "[OK]  $label deja installe" -ForegroundColor Green
    } else {
        Write-Host "[..]  Installation de $label ($wingetId)..." -ForegroundColor Yellow
        winget install -e --id $wingetId --accept-source-agreements --accept-package-agreements
    }
}

Write-Host "`n[1/6] Outils (JDK21, Maven, Node, Python 3.12, Docker, LibreOffice)..." -ForegroundColor Green
Ensure-Tool "java"   "EclipseAdoptium.Temurin.21.JDK" "JDK 21"
Ensure-Tool "mvn"    "Apache.Maven"                   "Maven"
Ensure-Tool "node"   "OpenJS.NodeJS.LTS"              "Node.js LTS"
Ensure-Tool "python" "Python.Python.3.12"             "Python 3.12"
Ensure-Tool "docker" "Docker.DockerDesktop"           "Docker Desktop"
# LibreOffice (soffice) : requis pour l'apercu inline des Word/Excel dans la Data Room
# (conversion .docx/.xlsx -> PDF a la volee cote ai-service). soffice n'est pas ajoute au
# PATH par defaut -> winget est idempotent (il skip si LibreOffice est deja installe).
Ensure-Tool "soffice" "TheDocumentFoundation.LibreOffice" "LibreOffice"

# Rafraichir le PATH de la session (recupere les outils fraichement installes sans reboot)
$env:Path = [System.Environment]::GetEnvironmentVariable("Path","Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path","User")

# --- 2) Pare-feu (idempotent) ---
Write-Host "`n[2/6] Regle de pare-feu (ports LAN)..." -ForegroundColor Green
Remove-NetFirewallRule -DisplayName "JURIKA" -ErrorAction SilentlyContinue
New-NetFirewallRule -DisplayName "JURIKA" -Direction Inbound -Protocol TCP `
  -LocalPort 5173,8080,8081,8082,8083,8084,8085,8086,8087,8088,8089,8090,8761,3000 -Action Allow | Out-Null
Write-Host "[OK]  Ports 5173,8080-8090,8761,3000 ouverts" -ForegroundColor Green

# --- 3) Verifier .env.local ---
# IMPORTANT : en mode start-server (processus + infra Docker sur localhost), les hotes doivent etre
# 'localhost' (POSTGRES_HOST=localhost...). On NE copie PAS .env.local.server.example (hotes Docker
# 'postgres'/'redis' = faux pour ce mode). Apporte ton .env.local du poste de DEV.
Write-Host "`n[3/6] Verification .env.local..." -ForegroundColor Green
$envLocal = Join-Path $Root ".env.local"
if (-not (Test-Path $envLocal)) {
    Write-Host "[!!]  .env.local ABSENT." -ForegroundColor Yellow
    Write-Host "      -> Apporte ton .env.local du poste de DEV (celui qui marche avec start-all.ps1)." -ForegroundColor Yellow
    Write-Host "      -> Il a les hotes en localhost + mots de passe + cles. NE PAS utiliser .env.local.server.example (hotes Docker)." -ForegroundColor Yellow
} else {
    Write-Host "[OK]  .env.local present." -ForegroundColor Green
    $envContent = Get-Content $envLocal -Raw
    if ($envContent -match 'COLLE_|REPLACE_ME') {
        Write-Host "[!!]  Des cles ne sont pas renseignees (placeholder COLLE_/REPLACE_ME). Verifie notamment RAG_EMBED_API_KEY (cle Gemini) -> sinon chatbot en FTS." -ForegroundColor Yellow
    }
    if ($envContent -notmatch 'RAG_ENABLED') {
        Write-Host "[i]   Bloc RAG_* absent du .env.local -> chatbot en FTS (ajoute RAG_ENABLED/RAG_CHAT_*/RAG_EMBED_* pour le mode LLM)." -ForegroundColor DarkGray
    }
}

# --- 4) venvs Python (OCR + KIE) — extraction CIN/CN ---
Write-Host "`n[4/6] Environnements Python (OCR + KIE)..." -ForegroundColor Green
function Setup-Venv([string]$dir, [string]$label, [string]$pipArgs) {
    if (-not (Test-Path (Join-Path $dir "requirements.txt"))) { Write-Host "  $label absent, skip" -ForegroundColor DarkGray; return }
    $venvPy = Join-Path $dir ".venv\Scripts\python.exe"
    if (-not (Test-Path $venvPy)) {
        Write-Host "  Creation venv $label..." -ForegroundColor Gray
        py -3.12 -m venv (Join-Path $dir ".venv")
    }
    if (Test-Path $venvPy) {
        Write-Host "  pip install $label (long la 1ere fois)..." -ForegroundColor Gray
        & $venvPy -m pip install --upgrade pip -q
        Invoke-Expression "& `"$venvPy`" -m pip install $pipArgs -r `"$(Join-Path $dir 'requirements.txt')`" -q"
        Write-Host "[OK]  $label pret" -ForegroundColor Green
    } else {
        Write-Host "[!!]  venv $label non cree (Python 3.12 dispo ?)" -ForegroundColor Yellow
    }
}
Setup-Venv $OcrPy "ocr-service" ""
Setup-Venv $KiePy "kie-service" "--extra-index-url https://download.pytorch.org/whl/cpu"

# --- 5) Dependances npm (frontend + realtime) ---
Write-Host "`n[5/6] Dependances npm (frontend + realtime)..." -ForegroundColor Green
if (Test-Path (Join-Path $Frontend "package.json")) {
    Push-Location $Frontend; Write-Host "  npm install frontend..." -ForegroundColor Gray; npm install --silent; Pop-Location
    Write-Host "[OK]  frontend" -ForegroundColor Green
}
if (Test-Path (Join-Path $RealtimeNode "package.json")) {
    Push-Location $RealtimeNode; Write-Host "  npm install realtime..." -ForegroundColor Gray; npm install --silent; Pop-Location
    Write-Host "[OK]  realtime" -ForegroundColor Green
}

# --- 6) Build de base Maven (parent + jurika-common) ---
Write-Host "`n[6/6] Build Maven de base (parent POM + jurika-common)..." -ForegroundColor Green
Push-Location $Backend
mvn install -N -DskipTests -q
mvn -pl jurika-common install -DskipTests -q
Pop-Location
Write-Host "[OK]  parent POM + jurika-common installes" -ForegroundColor Green

Write-Host "`n==================================================" -ForegroundColor Cyan
Write-Host " SETUP TERMINE" -ForegroundColor Cyan
Write-Host "==================================================" -ForegroundColor Cyan
if (-not $dockerWasPresent) {
    Write-Host " Docker Desktop vient d'etre installe :" -ForegroundColor Yellow
    Write-Host "   1) REDEMARRE le PC, 2) lance Docker Desktop (attends 'Running')," -ForegroundColor Yellow
    Write-Host "   3) puis : .\scripts\start-server.ps1" -ForegroundColor Yellow
} else {
    Write-Host " Verifie que Docker Desktop est 'Running', puis lance :" -ForegroundColor Green
    Write-Host "   .\scripts\start-server.ps1" -ForegroundColor Green
}
Write-Host " Rappel : renseigne .env.local (cle Gemini RAG_*, mots de passe, DONUT_MODEL_DIR)." -ForegroundColor DarkGray
