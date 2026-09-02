# JURIKA -- Arret de tous les services V2 + Docker
function Stop-PortIfBusy([int]$Port) {
    $procIds = Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue |
               Select-Object -ExpandProperty OwningProcess -Unique
    foreach ($procId in $procIds) {
        if ($procId -and $procId -ne 0) {
            Stop-Process -Id $procId -Force -ErrorAction SilentlyContinue
            Write-Host "Port $Port libere (PID $procId)" -ForegroundColor Yellow
        }
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
docker compose stop        # <-- ajoute -f docker-compose.local.yml si tu demarres avec ce fichier
Pop-Location

Write-Host "Stack arretee." -ForegroundColor Green