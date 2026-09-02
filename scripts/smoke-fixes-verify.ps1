# JURIKA — Smoke test post-restart : valide que les 3 fixes sont actifs
#   1. POST /api/v1/public/events => 201 (route gateway ordonnee)
#   2. POST /api/v1/public/signup/cabinet => 201 + email arrivé dans MailHog
#   3. (apres login) /api/v1/auth/setup-2fa => pas 403 SETUP_2FA_REQUIRED bloquant

$ErrorActionPreference = "Continue"

function Test-Endpoint([string]$Label, [string]$Method, [string]$Url, [string]$Body, [int]$ExpectedCode) {
    try {
        if ($Body) {
            $r = Invoke-WebRequest -Method $Method -Uri $Url -ContentType "application/json" -Body $Body -UseBasicParsing -TimeoutSec 30
        } else {
            $r = Invoke-WebRequest -Method $Method -Uri $Url -UseBasicParsing -TimeoutSec 30
        }
        $code = [int]$r.StatusCode
        $ok = if ($code -eq $ExpectedCode) { "PASS" } else { "FAIL" }
        Write-Host "[$ok] $Label : HTTP $code (attendu $ExpectedCode)" -ForegroundColor $(if ($ok -eq "PASS") { "Green" } else { "Red" })
        if ($r.Content.Length -lt 400) { Write-Host "       $($r.Content)" -ForegroundColor DarkGray }
    } catch {
        $resp = $_.Exception.Response
        if ($resp) {
            $code = [int]$resp.StatusCode
            $ok = if ($code -eq $ExpectedCode) { "PASS" } else { "FAIL" }
            Write-Host "[$ok] $Label : HTTP $code (attendu $ExpectedCode)" -ForegroundColor $(if ($ok -eq "PASS") { "Green" } else { "Red" })
        } else {
            Write-Host "[ERR ] $Label : $($_.Exception.Message)" -ForegroundColor Red
        }
    }
}

Write-Host "=== Health checks ===" -ForegroundColor Cyan
Test-Endpoint "Gateway health"          "GET"  "http://localhost:8080/actuator/health" $null 200
Test-Endpoint "Auth-service health"     "GET"  "http://localhost:8081/actuator/health" $null 200

Write-Host ""
Write-Host "=== Fix #1 : route gateway events ===" -ForegroundColor Cyan
$evt = '{"eventType":"SIGNUP_STARTED","source":"smoke","occurredAt":"2026-06-03T11:00:00Z","properties":{}}'
Test-Endpoint "POST /api/v1/public/events (via gateway)" "POST" "http://localhost:8080/api/v1/public/events" $evt 201

Write-Host ""
Write-Host "=== Fix #2 : signup + email MailHog ===" -ForegroundColor Cyan
$mhBefore = (Invoke-RestMethod "http://localhost:8025/api/v2/messages?limit=1").total
Write-Host "MailHog count avant signup : $mhBefore"
$rand = Get-Random
$signup = @{
    workspaceName="Cabinet Smoke $rand"; contactEmail="contact-$rand@example.com";
    ice="001234567890123"; ifFiscal="12345678"; rcNumber="98765"; city="Casablanca";
    firstName="Oussama"; lastName="BENATIK"; email="admin-$rand@example.com";
    phone="+212612345678"; selectedPlan="essentiel"; cguAccepted=$true
} | ConvertTo-Json
Test-Endpoint "POST /api/v1/public/signup/cabinet" "POST" "http://localhost:8080/api/v1/public/signup/cabinet" $signup 201
Start-Sleep -Seconds 3
$mhAfter = (Invoke-RestMethod "http://localhost:8025/api/v2/messages?limit=1").total
$mhDelta = $mhAfter - $mhBefore
$mhOk = if ($mhDelta -ge 1) { "PASS" } else { "FAIL" }
Write-Host "[$mhOk] MailHog : +$mhDelta email(s) recu(s) apres signup" -ForegroundColor $(if ($mhOk -eq "PASS") { "Green" } else { "Red" })

Write-Host ""
Write-Host "=== Fix #3 : 2FA setup options (necessite JWT, donc skipe en smoke nu) ===" -ForegroundColor DarkGray
Write-Host "  Verifier manuellement via le navigateur : signup -> login -> /account/2fa-choose" -ForegroundColor DarkGray

Write-Host ""
Write-Host "Ouvre http://localhost:8025 pour voir les emails dans MailHog" -ForegroundColor Cyan
