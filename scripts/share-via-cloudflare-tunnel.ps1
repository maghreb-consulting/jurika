# JURIKA — Partage l'app aux cabinets amis via Cloudflare Tunnel (gratuit, instant)
#
# Cloudflare Tunnel = un tunnel HTTPS sortant qui expose une URL publique
# vers une machine sans IP fixe. Aucun compte requis pour les tunnels
# ephemeres. Reagit en quelques secondes.
#
# Installation cloudflared (1 fois) :
#   winget install Cloudflare.cloudflared    (Windows 10/11)
#   ou : choco install cloudflared
#   ou telecharger : https://github.com/cloudflare/cloudflared/releases
#
# Ce script :
#   1. Verifie que cloudflared est installe
#   2. Verifie que le frontend (5173) et le gateway (8080) sont UP
#   3. Lance 2 tunnels ephemeres dans des fenetres separees
#   4. Affiche les URLs publiques generees (https://*.trycloudflare.com)
#   5. Met a jour CORS_ALLOWED_ORIGINS dans le process gateway (necessite
#      restart manuel apres recuperation des URLs)

$ErrorActionPreference = "Stop"

# 1. Verifier cloudflared
$cf = Get-Command cloudflared -ErrorAction SilentlyContinue
if (-not $cf) {
    Write-Host "cloudflared n'est pas installe." -ForegroundColor Red
    Write-Host ""
    Write-Host "Installation rapide (PowerShell admin) :" -ForegroundColor Yellow
    Write-Host "  winget install Cloudflare.cloudflared" -ForegroundColor White
    Write-Host ""
    Write-Host "Alternative (sans admin, telechargement direct) :" -ForegroundColor Yellow
    Write-Host "  https://github.com/cloudflare/cloudflared/releases/latest" -ForegroundColor White
    Write-Host "  -> telecharger cloudflared-windows-amd64.exe, le placer dans PATH" -ForegroundColor White
    exit 1
}
Write-Host "cloudflared OK : $($cf.Source)" -ForegroundColor Green

# 2. Verifier les ports
foreach ($port in @(5173, 8080, 8025)) {
    $listening = Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue
    if (-not $listening) {
        Write-Host "Port $port pas en ecoute. Lance d'abord start-all.ps1 et MailHog." -ForegroundColor Red
        exit 1
    }
}
Write-Host "Ports 5173 (frontend), 8080 (gateway), 8025 (mailhog) OK" -ForegroundColor Green

# 3. Lancer les tunnels en background dans des fenetres separees
Write-Host ""
Write-Host "Lancement des 3 tunnels..." -ForegroundColor Cyan
Start-Process -FilePath "cloudflared" -ArgumentList "tunnel","--url","http://localhost:5173" -WindowStyle Normal
Start-Sleep -Seconds 2
Start-Process -FilePath "cloudflared" -ArgumentList "tunnel","--url","http://localhost:8080" -WindowStyle Normal
Start-Sleep -Seconds 2
Start-Process -FilePath "cloudflared" -ArgumentList "tunnel","--url","http://localhost:8025" -WindowStyle Normal

Write-Host ""
Write-Host "ETAPES SUIVANTES (manuelles)" -ForegroundColor Yellow
Write-Host "  1. Dans chaque fenetre cloudflared, repere la ligne :" -ForegroundColor White
Write-Host "       Your quick Tunnel has been created! Visit it at: https://xyz.trycloudflare.com" -ForegroundColor DarkGray
Write-Host "  2. Note les 3 URLs (frontend, api, mailhog)." -ForegroundColor White
Write-Host "  3. Edit .env.local :" -ForegroundColor White
Write-Host "       CORS_ALLOWED_ORIGINS=...,<URL-frontend-trycloudflare>" -ForegroundColor DarkGray
Write-Host "       VITE_API_URL=<URL-api-trycloudflare>/api/v1" -ForegroundColor DarkGray
Write-Host "  4. Relance gateway + frontend (Ctrl+C dans les fenetres dev puis npm run dev / mvn spring-boot:run)" -ForegroundColor White
Write-Host "  5. Envoie l'URL frontend a tes amis" -ForegroundColor White
Write-Host ""
Write-Host "NOTE : les URL trycloudflare changent a chaque lancement." -ForegroundColor DarkGray
Write-Host "Pour des URLs persistantes, cree un compte Cloudflare et utilise" -ForegroundColor DarkGray
Write-Host "'cloudflared tunnel create jurika' (gratuit aussi)." -ForegroundColor DarkGray
