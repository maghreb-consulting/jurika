# JURIKA -- Arret de tous les services V2 + Docker
# Processus qu'il ne faut JAMAIS tuer, meme s'ils tiennent un de nos ports.
#
# CAUSE RACINE D'UN INCIDENT REEL (2026-09-06, reproduit deux fois). Quand la
# pile CONTENEURISEE tourne, les ports 8080-8090 / 8761 / 3000 ne sont pas tenus
# par nos JVM mais par les publicateurs de ports de Docker Desktop
# (`com.docker.backend`, `wslrelay`). Ce script les tuait donc, et avec eux le
# moteur Docker : `com.docker.backend.exe services: exit status 0xffffffff`.
# Toute la pile tombait, et le symptome n'avait aucun rapport visible.
$ProcessusProteges = @(
    'com.docker.backend', 'com.docker.service', 'com.docker.build',
    'Docker Desktop', 'wslrelay', 'wslhost', 'vpnkit', 'vpnkit-bridge', 'dockerd'
)

function Stop-PortIfBusy([int]$Port) {
    $procIds = Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue |
               Select-Object -ExpandProperty OwningProcess -Unique
    foreach ($procId in $procIds) {
        if (-not $procId -or $procId -eq 0) { continue }
        $proc = Get-Process -Id $procId -ErrorAction SilentlyContinue
        if (-not $proc) { continue }
        if ($ProcessusProteges -contains $proc.Name) {
            Write-Host "Port $Port tenu par $($proc.Name) (Docker) : NON tue." -ForegroundColor Cyan
            continue
        }
        Stop-Process -Id $procId -Force -ErrorAction SilentlyContinue
        Write-Host "Port $Port libere (PID $procId, $($proc.Name))" -ForegroundColor Yellow
    }
}

Write-Host "Arret des services JURIKA..." -ForegroundColor Cyan
8080,8081,8082,8083,8084,8085,8086,8087,8088,8089,8090,8761,3000,5173,5174,5175 |
    ForEach-Object { Stop-PortIfBusy $_ }

# Tuer les process Java du stack via leur ligne de commande (fiable, sans titre de fenetre)
Get-CimInstance Win32_Process -Filter "Name='java.exe'" -ErrorAction SilentlyContinue |
    Where-Object { $_.CommandLine -match 'jurika|spring-boot' } |
    ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }

# Docker : utilise le MEME fichier compose que celui de demarrage
Push-Location "$PSScriptRoot\..\infrastructure"
# ATTENTION : sans `-p`, Compose deduit le nom de projet du repertoire
# (`infrastructure`) et ne trouve AUCUN conteneur -- il reussit en silence sans
# rien arreter. La pile conteneurisee vit sous le projet `jurika-local`.
# Le moteur Docker peut etre a l'arret : ce n'est pas une erreur de ce script.
$conteneurs = @()
if (Get-Command docker -ErrorAction SilentlyContinue) {
    $conteneurs = @(docker ps -q --filter 'label=com.docker.compose.project=jurika-local' 2>$null)
    $global:LASTEXITCODE = 0
}
if ($conteneurs.Count -gt 0) {
    Write-Host ''
    Write-Host "  $($conteneurs.Count) conteneur(s) de la pile 'jurika-local' tournent toujours." -ForegroundColor Yellow
    Write-Host '  Ce script n arrete QUE les processus hote. Pour la pile conteneurisee :' -ForegroundColor Yellow
    Write-Host '    docker compose -p jurika-local stop   (jamais down -v)' -ForegroundColor White
}
Pop-Location

Write-Host "Stack arretee." -ForegroundColor Green