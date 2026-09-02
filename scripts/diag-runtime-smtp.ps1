#!/usr/bin/env pwsh
# Diagnostic ULTIME : quelles vars SMTP la JVM auth-service utilise REELLEMENT
# au runtime (pas ce que tu as dans .env.local, ce qui est charge en memoire).
#
# Si SMTP_HOST runtime != smtp-relay.brevo.com -> .env.local n'est pas lu
# par start-all.ps1. Le mail part vers MailHog/localhost, jamais vers Brevo.

$ErrorActionPreference = 'Continue'

function ok($m)   { Write-Host "  [OK]   $m" -ForegroundColor Green }
function ko($m)   { Write-Host "  [KO]   $m" -ForegroundColor Red }
function info($m) { Write-Host "  [info] $m" -ForegroundColor Gray }

Write-Host "`n[1] Vars SMTP effectives de la JVM auth-service (via actuator/env)" -ForegroundColor Cyan
try {
    $envEndpoint = Invoke-RestMethod -Uri 'http://localhost:8081/actuator/env' -ErrorAction Stop
    # Cherche les clefs spring.mail.* et SMTP_* dans toutes les propertySources
    $found = $false
    foreach ($src in $envEndpoint.propertySources) {
        foreach ($key in 'spring.mail.host','spring.mail.port','spring.mail.username','SMTP_HOST','SMTP_PORT','SMTP_USER','SMTP_FROM','JURIKA_SIGNUP_DEFER_CREDENTIALS_ALLOWED') {
            if ($src.properties.PSObject.Properties.Name -contains $key) {
                $val = $src.properties.$key.value
                if ($key -match 'password|PASSWORD') { $val = '***' }
                info "[$($src.name)] $key = $val"
                $found = $true
            }
        }
    }
    if (-not $found) {
        ko "Endpoint /actuator/env repond mais ne contient aucune var SMTP -- securite ferme ces details ?"
        info "Active management.endpoint.env.show-values=ALWAYS dans application.yml pour debug (puis remettre)."
    }
} catch {
    ko "Impossible de lire /actuator/env : $($_.Exception.Message)"
    info "Si 401/403 : il faut basic auth ops password (cf jurika.observability.prometheus.password)"
}

Write-Host "`n[2] Verdict :" -ForegroundColor Cyan
Write-Host @"
   * Si tu vois 'spring.mail.host = smtp-relay.brevo.com' -> JVM lit bien Brevo,
     le probleme est cote Brevo (sender pas verifie, mail bloque, etc).

   * Si tu vois 'spring.mail.host = localhost' (ou pas de surcharge) -> JVM utilise
     le defaut, le mail part vers MailHog (qui n'existe pas). Brevo n'a JAMAIS rien vu.
     Cause : start-all.ps1 ne source pas .env.local. Fix : voir ci-dessous.

"@ -ForegroundColor Yellow
