# JURIKA -- Build des images Docker pour deploiement production
# Usage : .\scripts\deploy\build-images.ps1 [-Tag latest]

param(
    [string]$Tag = "latest"
)

$ErrorActionPreference = "Stop"
$Root = Resolve-Path "$PSScriptRoot\..\.."
Push-Location $Root

Write-Host "================================================" -ForegroundColor Cyan
Write-Host " JURIKA -- Build images Docker prod (tag: $Tag)" -ForegroundColor Cyan
Write-Host "================================================" -ForegroundColor Cyan

# Build des JARs Java
Write-Host "`n[1/3] Build des JARs Spring Boot..." -ForegroundColor Green
Push-Location backend-java
mvn -DskipTests clean package
if ($LASTEXITCODE -ne 0) { Pop-Location; Pop-Location; exit 1 }
Pop-Location

# Build des images Docker (Spring Boot Maven Plugin a build-image)
Write-Host "`n[2/3] Build images Spring Boot..." -ForegroundColor Green
$services = @("discovery-service","gateway-service","auth-service","ticket-service","workflow-service","dataroom-service","supervision-service","ai-service")
foreach ($svc in $services) {
    Write-Host " -> $svc" -ForegroundColor Gray
    Push-Location "backend-java/$svc"
    mvn spring-boot:build-image -DskipTests "-Dspring-boot.build-image.imageName=jurika/$svc`:$Tag"
    Pop-Location
}

# Build realtime Node
Write-Host "`n[3/3] Build image realtime Node.js..." -ForegroundColor Green
Push-Location backend-node
if (Test-Path "Dockerfile") {
    docker build -t "jurika/realtime-service:$Tag" .
} else {
    Write-Host " backend-node/Dockerfile absent, skip" -ForegroundColor Yellow
}
Pop-Location

Pop-Location

Write-Host "`n================================================" -ForegroundColor Cyan
Write-Host " Build images termine." -ForegroundColor Cyan
Write-Host "================================================" -ForegroundColor Cyan
Write-Host " Verifier : docker images | grep jurika"
