# JURIKA — Wrapper Maven qui neutralise le piege TEMP de Windows (lot DIVERS, 2026-08-13).
#
# ---------------------------------------------------------------------------
# LE PIEGE
# ---------------------------------------------------------------------------
# Le Controlled Folder Access de Windows Defender bloque la creation de sockets
# et de fichiers temporaires dans %TEMP% (C:\Users\...\AppData\Local\Temp). Les
# JVM forkees par surefire echouent alors des le demarrage, avec une erreur NIO
# qui ne dit rien des tests — on cherche longtemps ailleurs.
#
# Le remede est de rediriger TEMP/TMP vers `projet/.tmp/`, dans l'arborescence du
# projet donc hors du perimetre protege. Ce script le fait une fois pour toutes,
# cree le repertoire s'il manque, puis delegue a Maven avec les arguments recus.
#
# ---------------------------------------------------------------------------
# POURQUOI PAS DANS LE POM
# ---------------------------------------------------------------------------
# Tente puis annule : rediriger `java.io.tmpdir` (ou TEMP/TMP) vers
# `${project.build.directory}` depuis maven-surefire-plugin casse `mvn clean`.
# Mockito s'auto-attache en deposant un `mockitoboot*.jar` dans le repertoire
# temporaire ; sous Windows ce fichier reste verrouille apres la sortie de la JVM
# forkee, et le clean suivant echoue sur « Failed to delete target/mockitoboot*.jar ».
# Tout repertoire sous `target/` a ce defaut. Un repertoire hors `target/` ne peut
# pas etre garanti existant sur un poste vierge, et un `java.io.tmpdir` inexistant
# fait echouer TOUS les tests. Le reglage appartient donc a l'environnement.
#
# ---------------------------------------------------------------------------
# USAGE
# ---------------------------------------------------------------------------
#   .\scripts\mvn-jurika.ps1 -o clean test
#   .\scripts\mvn-jurika.ps1 -o -pl workflow-service test
#   .\scripts\mvn-jurika.ps1 -o install -DskipTests
#
# Maven est cherche dans $env:JURIKA_MVN, puis dans l'emplacement usuel, puis dans
# le PATH. Le code de sortie de Maven est propage tel quel (utilisable en CI).
# ---------------------------------------------------------------------------

$ErrorActionPreference = "Stop"

$Root = Resolve-Path "$PSScriptRoot\.."
$Backend = Join-Path $Root "backend-java"

# Redirection TEMP -> .tmp/ du projet. `-Force` sur un repertoire est idempotent
# (contrairement a un fichier, qu'il tronquerait).
$ProjectTmp = Join-Path $Root ".tmp"
New-Item -ItemType Directory -Path $ProjectTmp -Force | Out-Null
$env:TEMP = $ProjectTmp
$env:TMP = $ProjectTmp

# Les jars d'amorçage laisses par Mockito s'accumulent dans .tmp/ sans consequence,
# mais autant ne pas les laisser grossir indefiniment : purge de ceux d'hier et avant.
Get-ChildItem -Path $ProjectTmp -Filter "mockitoboot*.jar" -ErrorAction SilentlyContinue |
    Where-Object { $_.LastWriteTime -lt (Get-Date).AddDays(-1) } |
    Remove-Item -Force -ErrorAction SilentlyContinue

$Mvn = if ($env:JURIKA_MVN) { $env:JURIKA_MVN } else { "C:\Program Files\apache-maven-3.9.9\bin\mvn.cmd" }
if (-not (Test-Path $Mvn)) {
    $fromPath = (Get-Command mvn -ErrorAction SilentlyContinue).Source
    if (-not $fromPath) {
        throw "Maven introuvable. Renseigne `$env:JURIKA_MVN avec le chemin complet de mvn.cmd."
    }
    $Mvn = $fromPath
}

# PowerShell decoupe « -pl a,b,c » en TABLEAU avant de nous le passer : transmis tel
# quel, Maven recevrait « System.Object[] ». On re-aplatit chaque tableau en liste
# separee par des virgules, la forme qu'attend Maven (-pl, -P, -Dtest=...).
$flat = @()
foreach ($a in $args) {
    if ($a -is [System.Array]) { $flat += ($a -join ',') } else { $flat += [string]$a }
}

Write-Host "TEMP -> $ProjectTmp" -ForegroundColor DarkGray
Write-Host "mvn $($flat -join ' ')" -ForegroundColor Cyan

Push-Location $Backend
try {
    & $Mvn @flat
    $code = $LASTEXITCODE
} finally {
    Pop-Location
}

exit $code
