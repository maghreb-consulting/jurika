# JURIKA — Redemarrage ORDONNE du backend pour le lot DIVERS (2026-08-13).
#
# ---------------------------------------------------------------------------
# POURQUOI UN SCRIPT DEDIE PLUTOT QUE restart-all-backend-2026-06-08.ps1
# ---------------------------------------------------------------------------
# Ce lot ajoute deux migrations Flyway — V16 (societe mere etrangere sur
# `entreprise_dossiers`) et V17 (`date_fermeture` / `motif_fermeture` sur
# `succursales`) — qui appartiennent toutes deux a TICKET-SERVICE : c'est lui
# qui possede ces deux tables.
#
# Or workflow-service ECRIT dans ces colonnes (creation de la succursale saisie
# manuellement, fermeture reelle, dossier mere etranger). Le restart "tous en
# parallele" les demarre simultanement : si workflow-service gagne la course, ses
# premieres ecritures partent sur un schema qui n'a pas encore les colonnes, et
# echouent avec une erreur SQL cryptique — sans que rien n'indique que la cause
# est un ordre de demarrage.
#
# D'ou la sequence ci-dessous : ticket-service d'abord, SEUL, et on attend que
# son actuator reponde UP (donc Flyway termine) avant de lancer les autres.
#
# Usage :
#   pwsh .\scripts\restart-lot-divers-2026-08-13.ps1
#
# Pre-requis : infra docker compose up, `mvn -o install -DskipTests` sur
# backend-java si jurika-common a bouge.
# ---------------------------------------------------------------------------

$ErrorActionPreference = "Stop"
$Root = Resolve-Path "$PSScriptRoot\.."
$Backend = Join-Path $Root "backend-java"
$LogDir = Join-Path $Root ".tmp"
New-Item -ItemType Directory -Path $LogDir -Force | Out-Null

# Piege d'execution #1 — Controlled Folder Access (Windows Defender) bloque les
# sockets AF_UNIX et les fichiers temporaires dans %TEMP%. On redirige TEMP/TMP
# vers `.tmp/` du projet. Le pendant durable cote tests vit dans
# backend-java/pom.xml (configuration maven-surefire-plugin).
$env:TEMP = $LogDir; $env:TMP = $LogDir

# Charge .env puis .env.local (defauts puis overrides locaux).
foreach ($f in @("$Root\.env", "$Root\.env.local")) {
    if (Test-Path $f) {
        Get-Content $f | ForEach-Object {
            $line = $_.Trim()
            if ($line -and -not $line.StartsWith("#") -and $line.Contains("=")) {
                $i = $line.IndexOf("=")
                $k = $line.Substring(0, $i).Trim()
                $v = $line.Substring($i + 1).Trim()
                if ($v.StartsWith('"') -and $v.EndsWith('"')) { $v = $v.Substring(1, $v.Length - 2) }
                [Environment]::SetEnvironmentVariable($k, $v, "Process")
            }
        }
        Write-Host "Charge $(Split-Path $f -Leaf)" -ForegroundColor DarkGray
    }
}

$Mvn = if ($env:JURIKA_MVN) { $env:JURIKA_MVN } else { "C:\Program Files\apache-maven-3.9.9\bin\mvn.cmd" }
if (-not (Test-Path $Mvn)) {
    $fromPath = (Get-Command mvn -ErrorAction SilentlyContinue).Source
    if (-not $fromPath) { throw "Maven introuvable. Renseigne `$env:JURIKA_MVN avec le chemin de mvn.cmd." }
    $Mvn = $fromPath
}

# Proprietaire des migrations V16/V17 : DOIT demarrer en premier et SEUL.
$Migrator = @{ Port = 8082; Module = "ticket-service"; Label = "ticket" }

# Les autres, une fois le schema a jour. workflow-service ecrit dans les
# colonnes creees par V16/V17 : il est necessairement dans ce second groupe.
$Followers = @(
    @{ Port = 8081; Module = "auth-service";        Label = "auth" },
    @{ Port = 8083; Module = "workflow-service";    Label = "workflow" },
    @{ Port = 8084; Module = "dataroom-service";    Label = "dataroom" },
    @{ Port = 8085; Module = "ai-service";          Label = "ai" },
    @{ Port = 8086; Module = "supervision-service"; Label = "supervision" },
    @{ Port = 8087; Module = "dashboard-service";   Label = "dashboard" }
)

function Stop-Service-OnPort($svc) {
    $procId = (Get-NetTCPConnection -State Listen -LocalPort $svc.Port -ErrorAction SilentlyContinue |
        Select-Object -First 1).OwningProcess
    if ($procId) {
        Stop-Process -Id $procId -Force -ErrorAction SilentlyContinue
        Write-Host "  killed $($svc.Label) (PID $procId on $($svc.Port))" -ForegroundColor Yellow
    } else {
        Write-Host "  $($svc.Label) already down ($($svc.Port))" -ForegroundColor DarkGray
    }
}

function Start-Service-Bg($svc) {
    $logFile = Join-Path $LogDir "$($svc.Label)-service-restart.log"
    "=== restart $(Get-Date -Format o) ===" | Out-File -FilePath $logFile
    $moduleDir = Join-Path $Backend $svc.Module
    $cmd = "cd /d `"$moduleDir`" && `"$Mvn`" -o spring-boot:run -DskipTests >> `"$logFile`" 2>&1"
    Start-Process -FilePath "cmd.exe" -ArgumentList "/c", $cmd -WindowStyle Hidden | Out-Null
    Write-Host "  started $($svc.Label) -> $logFile" -ForegroundColor Cyan
}

function Test-Up($svc) {
    try {
        $resp = Invoke-WebRequest -Uri "http://localhost:$($svc.Port)/actuator/health" `
            -TimeoutSec 2 -UseBasicParsing -ErrorAction Stop
        return $resp.StatusCode -eq 200
    } catch { return $false }
}

function Wait-Up($services, $timeoutMinutes) {
    $deadline = (Get-Date).AddMinutes($timeoutMinutes)
    do {
        $pending = @($services | Where-Object { -not (Test-Up $_) })
        if ($pending.Count -eq 0) { return $true }
        Start-Sleep -Seconds 5
    } while ((Get-Date) -lt $deadline)
    return $false
}

# --- 1) STOP (tout, y compris le migrateur) --------------------------------
Write-Host "`n=== STOP backend services ===" -ForegroundColor Magenta
Stop-Service-OnPort $Migrator
foreach ($svc in $Followers) { Stop-Service-OnPort $svc }
Start-Sleep -Seconds 3

# --- 2) START ticket-service SEUL (Flyway V16 + V17) -----------------------
Write-Host "`n=== START ticket-service SEUL (migrations V16/V17) ===" -ForegroundColor Magenta
Start-Service-Bg $Migrator
Write-Host "  attente de la fin des migrations (actuator/health)..." -ForegroundColor DarkGray
if (-not (Wait-Up @($Migrator) 5)) {
    Write-Host "  [KO] ticket-service n'est pas UP : les migrations V16/V17 n'ont PAS tourne." -ForegroundColor Red
    Write-Host "       Les autres services ne sont volontairement PAS demarres — ils ecriraient" -ForegroundColor Red
    Write-Host "       sur un schema incomplet. Voir $LogDir\ticket-service-restart.log" -ForegroundColor Red
    exit 1
}
Write-Host "  [OK] ticket-service UP — schema a jour." -ForegroundColor Green

# --- 3) START les autres (en parallele, le schema est fige) ----------------
Write-Host "`n=== START des autres services (parallele) ===" -ForegroundColor Magenta
foreach ($svc in $Followers) { Start-Service-Bg $svc }

Write-Host "`n=== WAIT actuator/health ===" -ForegroundColor Magenta
if (Wait-Up $Followers 5) {
    Write-Host "  tous les services UP" -ForegroundColor Green
}

# --- 4) STATUT FINAL -------------------------------------------------------
foreach ($svc in (@($Migrator) + $Followers)) {
    if (Test-Up $svc) {
        Write-Host "  [OK] $($svc.Label) ($($svc.Port))" -ForegroundColor Green
    } else {
        Write-Host "  [KO] $($svc.Label) ($($svc.Port)) -- voir $LogDir\$($svc.Label)-service-restart.log" -ForegroundColor Red
    }
}

Write-Host "`nGateway (8080) doit etre demarre separement (start-all.ps1)." -ForegroundColor DarkGray
