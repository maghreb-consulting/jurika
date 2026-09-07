# scripts/start-local.ps1
# Sprint Beta (pricing-deploy) — TASK 8.
#
# Equivalent Windows de start-local.sh. Build + Up + wait healthcheck.

[CmdletBinding()]
param(
    [switch]$NoBuild,
    [switch]$Logs,
    [switch]$Reset
)

$ErrorActionPreference = 'Stop'
$ProjectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $ProjectRoot

# ─── 1) Pre-flight ────────────────────────────────────────────────────
Write-Host "🔍 Pre-flight checks"
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    Write-Host "❌ Docker Desktop absent du PATH. Installe-le et redemarre la session." -ForegroundColor Red
    exit 1
}
$composeVersion = (docker compose version 2>$null) | Out-String
if (-not ($composeVersion -match 'v2\.\d+\.\d+')) {
    Write-Host "❌ docker compose v2.20+ requis." -ForegroundColor Red
    exit 1
}
if (-not (Test-Path .env.local)) {
    Write-Host "❌ .env.local introuvable. Copie .env.local.server.example en .env.local." -ForegroundColor Red
    exit 1
}

# Source .env.local pour recuperer JURIKA_LAN_HOST
$envVars = @{}
Get-Content .env.local | Where-Object { $_ -match '^\s*([A-Z0-9_]+)\s*=\s*(.+)\s*$' } | ForEach-Object {
    if ($_ -match '^\s*([A-Z0-9_]+)\s*=\s*(.+)\s*$') {
        $envVars[$Matches[1]] = $Matches[2].Trim('"', "'")
    }
}
$LanHost = if ($envVars.ContainsKey('JURIKA_LAN_HOST')) { $envVars['JURIKA_LAN_HOST'] } else { 'localhost' }

# Cles JWT
$jwtDir = Join-Path $ProjectRoot 'infrastructure\secrets\jwt'
if (-not (Test-Path (Join-Path $jwtDir 'jwt-public.pem'))) {
    Write-Host "⚠️  Cles JWT RS256 absentes — generation via openssl..." -ForegroundColor Yellow
    New-Item -ItemType Directory -Force -Path $jwtDir | Out-Null
    if (-not (Get-Command openssl -ErrorAction SilentlyContinue)) {
        Write-Host "❌ openssl absent. Installe-le : winget install OpenSSL.OpenSSL" -ForegroundColor Red
        exit 1
    }
    & openssl genrsa -out (Join-Path $jwtDir 'jwt-private.pem') 2048
    & openssl pkcs8 -topk8 -inform PEM -in (Join-Path $jwtDir 'jwt-private.pem') `
        -out (Join-Path $jwtDir 'jwt-private-pkcs8.pem') -nocrypt
    & openssl rsa -in (Join-Path $jwtDir 'jwt-private.pem') -pubout `
        -out (Join-Path $jwtDir 'jwt-public.pem')
    Write-Host "  ✓ Paire RSA 2048 generee." -ForegroundColor Green
}

$composeArgs = @(
    '-f', 'infrastructure/docker-compose.yml',
    '-f', 'infrastructure/docker-compose.services.yml',
    '-f', 'infrastructure/docker-compose.local.yml',
    # Les DEUX fichiers sont requis. `--env-file` REMPLACE le `.env` par defaut
    # (Compose v2) : avec `.env.local` seul, POSTGRES_PASSWORD et REDIS_PASSWORD
    # -- qui ne vivent que dans `.env` -- se resolvent en chaine VIDE, et la pile
    # demarre avec un mot de passe de base vide sans que rien ne le signale.
    '--env-file', '.env',
    '--env-file', '.env.local',
    '-p', 'jurika-local'
)

# ─── 2) Reset optionnel ──────────────────────────────────────────────
if ($Reset) {
    Write-Host "🗑️  --reset : down -v (volumes effaces)" -ForegroundColor Yellow
    & docker compose @composeArgs down -v --remove-orphans 2>&1 | Out-Host
}

# ─── 3) Build ────────────────────────────────────────────────────────
if (-not $NoBuild) {
    Write-Host "🔨 docker compose build (5-10 min la 1ere fois)"
    & docker compose @composeArgs build --parallel
    if ($LASTEXITCODE -ne 0) { Write-Host "❌ Build failed" -ForegroundColor Red; exit $LASTEXITCODE }
}

# ─── 4) Up ───────────────────────────────────────────────────────────
Write-Host "🚀 docker compose up -d"
& docker compose @composeArgs up -d
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

# ─── 5) Attente healthchecks ─────────────────────────────────────────
$expected = @(
    # `jurika-minio` et NON `jurika-mailhog` : MailHog a ete retire quand le SMTP
    # est passe a Brevo (2026-06-03). Le script attendait donc un conteneur qui
    # n'existe plus, et ignorait MinIO qui existe : le compteur plafonnait a
    # 17/18 et le timeout de 5 minutes tombait A CHAQUE LANCEMENT, pile saine.
    'jurika-postgres', 'jurika-redis', 'jurika-rabbitmq', 'jurika-minio',
    'jurika-discovery', 'jurika-gateway', 'jurika-auth', 'jurika-ticket',
    'jurika-workflow', 'jurika-dataroom', 'jurika-ai', 'jurika-supervision',
    'jurika-dashboard', 'jurika-billing',
    'jurika-realtime', 'jurika-ocr', 'jurika-kie',
    'jurika-frontend'
)
Write-Host "⏳ Attente des healthchecks (timeout 5 min)..."
$deadline = (Get-Date).AddMinutes(5)
while ($true) {
    $healthy = 0
    foreach ($c in $expected) {
        $state = (docker inspect --format='{{.State.Health.Status}}' $c 2>$null)
        if ($state -eq 'healthy') { $healthy++ }
    }
    Write-Host "`r   $healthy/$($expected.Count) services healthy   " -NoNewline
    if ($healthy -eq $expected.Count) { Write-Host ""; break }
    if ((Get-Date) -gt $deadline) {
        Write-Host ""
        Write-Host "❌ Timeout — etat actuel :" -ForegroundColor Red
        & docker compose @composeArgs ps
        exit 2
    }
    Start-Sleep -Seconds 5
}

# ─── 6) URLs ─────────────────────────────────────────────────────────
Write-Host ""
Write-Host "✅ Stack JURIKA UP sur LAN $LanHost" -ForegroundColor Green
Write-Host ""
Write-Host "   App (frontend)      :  http://$LanHost/"
# Accolades OBLIGATOIRES : dans "http://$LanHost:8080", PowerShell lit `:` comme
# un qualificateur de portee (a la maniere de $env:PATH) et cherche une variable
# nommee « 8080 » dans la portee « LanHost ». Resultat affiche : « http:/// ».
# La ligne du frontend ci-dessus fonctionnait justement faute de `:`.
Write-Host "   API Gateway         :  http://${LanHost}:8080/actuator/health"
Write-Host "   Realtime (Socket)   :  http://${LanHost}:3000/health"
Write-Host "   Discovery (Eureka)  :  http://${LanHost}:8761"
Write-Host "   OCR (docTR)         :  http://${LanHost}:8089/health"
Write-Host "   KIE (Donut)         :  http://${LanHost}:8088/health"
Write-Host "   RabbitMQ Mgmt       :  http://${LanHost}:15672"
Write-Host "   MinIO Console       :  http://${LanHost}:9001"
Write-Host ""
Write-Host "   Comptes demo (apres seed) :"
Write-Host "     Superviseur : superviseur@demo.jurika.ma / Demo@2026"
Write-Host "     Employe     : employe1@demo.jurika.ma   / Demo@2026"
Write-Host "     Client      : client@demo.jurika.ma     / Demo@2026"
Write-Host "     Code workspace : JUR-DEMO2"
Write-Host ""
Write-Host "ℹ️  Pour seeder : .\scripts\seed-demo.ps1"
Write-Host "ℹ️  Pour le smoke test : .\scripts\smoke-test.ps1"

if ($Logs) {
    & docker compose @composeArgs logs -f --tail=50
}
