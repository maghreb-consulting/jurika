# Démarre la stack JURIKA en injectant les secrets via Doppler.
# Pré-requis : `doppler login` + `doppler setup` exécutés une fois.
# Voir docs/v2/PLAN_SPRINT_1_SECURITE_PROD.md TASK 9.

Param(
    [switch]$Detach = $true
)

$ErrorActionPreference = "Stop"

Write-Host "→ Vérification Doppler CLI..." -ForegroundColor Cyan
if (-not (Get-Command doppler -ErrorAction SilentlyContinue)) {
    Write-Error "Doppler CLI introuvable. Installer : choco install doppler"
    exit 1
}

Write-Host "→ Démarrage Docker Compose avec secrets Doppler..." -ForegroundColor Cyan
$composeFile = "infrastructure/docker-compose.yml"

if ($Detach) {
    doppler run -- docker compose -f $composeFile up -d
} else {
    doppler run -- docker compose -f $composeFile up
}

if ($LASTEXITCODE -ne 0) {
    Write-Error "Échec docker compose up (exit $LASTEXITCODE)"
    exit $LASTEXITCODE
}

Write-Host "→ Stack démarrée. Logs : docker compose -f $composeFile logs -f" -ForegroundColor Green
