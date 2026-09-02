# Reset complet de la DB JURIKA (perte de TOUTES les donnees)
# Usage : .\scripts\reset-db.ps1

Write-Host "================================================" -ForegroundColor Red
Write-Host " RESET COMPLET DE LA DB JURIKA" -ForegroundColor Red
Write-Host " (toutes les donnees seront perdues)" -ForegroundColor Red
Write-Host "================================================" -ForegroundColor Red
$confirm = Read-Host "Confirmer ? (tape OUI)"
if ($confirm -ne "OUI") {
    Write-Host "Annule." -ForegroundColor Yellow
    exit 0
}

$Root = Resolve-Path "$PSScriptRoot\.."
$Infra = Join-Path $Root "infrastructure"

Write-Host "`n1. Arret des services Java..." -ForegroundColor Cyan
8080, 8081, 8082, 8083, 8085, 8761 | ForEach-Object {
    $proc = (Get-NetTCPConnection -State Listen -LocalPort $_ -ErrorAction SilentlyContinue).OwningProcess
    if ($proc) { Stop-Process -Id $proc -Force -ErrorAction SilentlyContinue }
}

Write-Host "2. Suppression du volume postgres_data..." -ForegroundColor Cyan
Push-Location $Infra
docker compose down -v
docker volume rm infrastructure_postgres_data -f 2>$null
Pop-Location

Write-Host "3. Redemarrage Docker (postgres recree fresh)..." -ForegroundColor Cyan
Push-Location $Infra
docker compose up -d
Pop-Location

Write-Host "`nDB resetee. Tu peux maintenant lancer .\scripts\start-all.ps1" -ForegroundColor Green
