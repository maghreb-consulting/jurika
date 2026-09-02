# scripts/seed-demo.ps1
# Sprint Beta (pricing-deploy) — TASK 7
#
# Wrapper PowerShell pour Windows. Delegue au script Node.js multiplateforme.
# Verifie que Node est disponible et que la dep 'pg' est installee.

$ErrorActionPreference = 'Stop'
$ProjectRoot = Split-Path -Parent $PSScriptRoot

Write-Host "🌱 JURIKA — Seed demo workspace JUR-DEMO2 (Maghreb Consulting Demo)" -ForegroundColor Cyan

# 1. Verifier Node 18+
try {
    $nodeVersion = (node --version) -replace 'v', ''
    $major = [int]($nodeVersion.Split('.')[0])
    if ($major -lt 18) {
        Write-Host "❌ Node 18+ requis (trouve : v$nodeVersion). Update via : winget install OpenJS.NodeJS.LTS" -ForegroundColor Red
        exit 1
    }
    Write-Host "  ✓ Node v$nodeVersion" -ForegroundColor DarkGray
} catch {
    Write-Host "❌ Node non trouve dans le PATH. Installe avec : winget install OpenJS.NodeJS.LTS" -ForegroundColor Red
    exit 1
}

# 2. Verifier que 'pg' est installe
$pgPath = Join-Path $ProjectRoot 'node_modules\pg\package.json'
if (-not (Test-Path $pgPath)) {
    Write-Host "📦 Dep 'pg' absente — installation a la racine..." -ForegroundColor Yellow
    Push-Location $ProjectRoot
    try {
        npm install pg --save-dev --no-audit --no-fund 2>&1 | Out-Host
    } finally {
        Pop-Location
    }
}

# 3. Forward args et lancer
$scriptPath = Join-Path $PSScriptRoot 'seed-demo.mjs'
& node $scriptPath @args
exit $LASTEXITCODE
