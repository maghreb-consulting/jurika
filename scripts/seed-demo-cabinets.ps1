# JURIKA — Seed 2 cabinets demo pour les amis (mot de passe partage)
#
# Prerequis :
#   - Tous les services UP (script restart-after-fixes.ps1 + start-all.ps1)
#   - jurika.test.seed.enabled=true (deja dans .env.local apres ce fix)
#   - dataroom-service redemarre apres l'activation du flag
#
# Output : fichier scripts\demo-credentials.txt avec les codes workspace,
# emails et mot de passe pour distribution aux cabinets amis.

$ErrorActionPreference = "Stop"
$Root = Resolve-Path "$PSScriptRoot\.."

function Invoke-Seed([string]$Label) {
    Write-Host "Seeding : $Label ..." -ForegroundColor Cyan
    try {
        # 2026-06-04 (fix bug D) : on cree l'admin du cabinet demo en SUPERVISEUR
        # pour qu'il ait acces au dashboard superviseur, au billing et aux endpoints
        # employe (via la role hierarchy SUPERVISEUR > EMPLOYE).
        $r = Invoke-RestMethod -Method POST -Uri "http://localhost:8080/api/v1/test/seed/workspace?role=SUPERVISEUR" -TimeoutSec 60
        Write-Host "  OK workspace=$($r.workspaceCode)" -ForegroundColor Green
        return $r
    } catch {
        Write-Host "  FAIL : $($_.Exception.Message)" -ForegroundColor Red
        $resp = $_.Exception.Response
        if ($resp) {
            $code = [int]$resp.StatusCode
            Write-Host "  HTTP $code — verifier que dataroom-service a ete redemarre avec JURIKA_TEST_SEED_ENABLED=true" -ForegroundColor Red
        }
        return $null
    }
}

$c1 = Invoke-Seed "Cabinet Demo 1"
$c2 = Invoke-Seed "Cabinet Demo 2"

if (-not $c1 -or -not $c2) {
    Write-Host ""
    Write-Host "Echec partiel — verifier les logs dataroom-service." -ForegroundColor Red
    exit 1
}

# Ecrit un fichier resume pour distribution
$txt = @"
══════════════════════════════════════════════════════════════════
JURIKA — Comptes de demonstration pour cabinets amis
Genere le : $(Get-Date -Format "yyyy-MM-dd HH:mm")
══════════════════════════════════════════════════════════════════

CABINET DEMO #1  (par exemple pour ton 1er ami)
─────────────────────────────────────────────────
URL d'acces      : http://localhost:5173  (ou via le tunnel partage)
Workspace Code   : $($c1.workspaceCode)
Email employe    : $($c1.employeEmail)
Email client     : $($c1.clientEmail)
Mot de passe     : $($c1.adminPassword)
2FA              : DESACTIVE (pour faciliter la demo)
Contenu pre-fait : SARL Demo + 1 exercice fiscal + 4 documents (2 comptables + 2 fiscaux)


CABINET DEMO #2  (par exemple pour ton 2eme ami)
─────────────────────────────────────────────────
URL d'acces      : http://localhost:5173  (ou via le tunnel partage)
Workspace Code   : $($c2.workspaceCode)
Email employe    : $($c2.employeEmail)
Email client     : $($c2.clientEmail)
Mot de passe     : $($c2.adminPassword)
2FA              : DESACTIVE (pour faciliter la demo)
Contenu pre-fait : SARL Demo + 1 exercice fiscal + 4 documents (2 comptables + 2 fiscaux)


COMMENT SE CONNECTER
─────────────────────────────────────────────────
1. Ouvrir l'URL d'acces
2. Cliquer "Connexion" (en haut a droite)
3. Saisir le Workspace Code ($($c1.workspaceCode) ou $($c2.workspaceCode))
4. Email + mot de passe
5. (si demande) Cliquer "Passer le 2FA" ou suivre le wizard simple

UI Mailhog (pour voir tous les emails envoyes) :
  http://localhost:8025

══════════════════════════════════════════════════════════════════
Pour reset un cabinet :  DELETE http://localhost:8080/api/v1/test/cleanup/$($c1.workspaceId)
══════════════════════════════════════════════════════════════════
"@

$file = Join-Path $Root "scripts\demo-credentials.txt"
$txt | Out-File -FilePath $file -Encoding utf8
Write-Host ""
Write-Host "Fichier credentials genere : $file" -ForegroundColor Green
Write-Host ""
Get-Content $file
