#!/usr/bin/env pwsh
# Diagnostic envoi email signup -- detecte automatiquement Brevo vs MailHog
# et trace exactement pourquoi le mail n'arrive pas. Une commande, une reponse.

$ErrorActionPreference = 'Stop'
$BASE = 'http://localhost:8080'

function ok($m)   { Write-Host "  [OK]   $m" -ForegroundColor Green }
function ko($m)   { Write-Host "  [KO]   $m" -ForegroundColor Red }
function info($m) { Write-Host "  [info] $m" -ForegroundColor Gray }
function warn($m) { Write-Host "  [WARN] $m" -ForegroundColor Yellow }
function step($n, $m) { Write-Host "`n[$n] $m" -ForegroundColor Cyan }

# -------------------------------------------------------------------------
step 1 'Lecture config SMTP des .env*'
$envFile = if (Test-Path '.env.local') { '.env.local' } elseif (Test-Path '.env') { '.env' } else { $null }
$cfg = @{}
if (-not $envFile) {
    ko 'aucun .env.local ni .env trouve a la racine du projet'
} else {
    info "fichier source : $envFile"
    $envContent = Get-Content $envFile -Raw
    foreach ($key in 'JURIKA_EMAIL_PROVIDER','SMTP_HOST','SMTP_PORT','SMTP_USER','SMTP_AUTH','SMTP_STARTTLS','SMTP_FROM','SMTP_FROM_NAME','SMTP_PASSWORD') {
        if ($envContent -match "(?m)^$key\s*=\s*(.+)$") {
            $val = $matches[1].Trim('"',"'",' ')
            $cfg[$key] = $val
            $display = if ($key -eq 'SMTP_PASSWORD') { if ($val) { '*** (' + $val.Length + ' chars)' } else { '(vide!)' } } else { $val }
            info "$key = $display"
        }
    }
}

$provider = 'unknown'
if ($cfg['SMTP_HOST'] -match 'brevo|sendinblue') { $provider = 'brevo' }
elseif ($cfg['SMTP_HOST'] -eq 'localhost' -and $cfg['SMTP_PORT'] -eq '1025') { $provider = 'mailhog' }
elseif ($cfg['JURIKA_EMAIL_PROVIDER'] -eq 'log') { $provider = 'log' }
elseif ($cfg['SMTP_HOST']) { $provider = 'smtp-autre' }
Write-Host "  -> Provider detecte : $provider" -ForegroundColor Magenta

# -------------------------------------------------------------------------
step 2 'Verification config Brevo (si applicable)'
if ($provider -eq 'brevo') {
    $issues = @()
    if ($cfg['SMTP_PORT'] -ne '587' -and $cfg['SMTP_PORT'] -ne '465') {
        $issues += "SMTP_PORT=$($cfg['SMTP_PORT']) -- Brevo veut 587 (STARTTLS) ou 465 (SSL)"
    }
    if ($cfg['SMTP_AUTH'] -ne 'true') {
        $issues += "SMTP_AUTH=$($cfg['SMTP_AUTH']) -- Brevo OBLIGE auth, mets SMTP_AUTH=true"
    }
    if ($cfg['SMTP_STARTTLS'] -ne 'true' -and $cfg['SMTP_PORT'] -eq '587') {
        $issues += "SMTP_STARTTLS=$($cfg['SMTP_STARTTLS']) -- port 587 EXIGE STARTTLS, mets SMTP_STARTTLS=true"
    }
    if (-not $cfg['SMTP_USER']) {
        $issues += "SMTP_USER vide -- mets l identifiant SMTP Brevo (type 8XXXXX@smtp-brevo.com)"
    } elseif ($cfg['SMTP_USER'] -notmatch '@') {
        $issues += "SMTP_USER='$($cfg['SMTP_USER'])' suspect -- l'identifiant Brevo c'est 8XXXXX@smtp-brevo.com"
    }
    if (-not $cfg['SMTP_PASSWORD']) {
        $issues += 'SMTP_PASSWORD vide -- genere une cle SMTP dans Brevo > Transactionnel > SMTP & API'
    } elseif ($cfg['SMTP_PASSWORD'].Length -lt 30) {
        $issues += "SMTP_PASSWORD = $($cfg['SMTP_PASSWORD'].Length) chars -- trop court (Brevo : 60-90 chars)"
    } else {
        # Brevo accepte 2 formats valides : master SMTP password (~90 chars) ou API key xkeysib- (~60-70 chars)
        $kind = if ($cfg['SMTP_PASSWORD'] -match '^xkeysib-') { 'API key xkeysib-' } else { 'master SMTP password' }
        info "SMTP_PASSWORD format : $kind ($($cfg['SMTP_PASSWORD'].Length) chars) -- les deux marchent cote Brevo"
    }
    if (-not $cfg['SMTP_FROM']) {
        $issues += 'SMTP_FROM vide -- doit etre un sender verifie cote Brevo (ex noreply@jurika.ma)'
    }
    if ($issues.Count -eq 0) {
        ok 'config Brevo coherente'
    } else {
        foreach ($i in $issues) { ko $i }
    }
} else {
    info "provider='$provider', skip checks Brevo"
}

# -------------------------------------------------------------------------
step 3 'Etat services : auth-service + dataroom-service repondent ?'
$authUp = $false
try {
    Invoke-RestMethod -Uri "http://localhost:8081/actuator/health" -ErrorAction Stop | Out-Null
    $authUp = $true
    ok 'auth-service UP (port 8081)'
} catch {
    ko "auth-service KO -- est-ce que la stack tourne ? (.\scripts\start-all.ps1)"
}

# -------------------------------------------------------------------------
step 4 'Test envoi reel : POST /public/signup/cabinet avec un email unique'
if (-not $authUp) {
    warn 'stack down, je skip le test signup'
} else {
    $stamp = (Get-Random)
    $testEmail = "diag-$stamp@example.com"
    # Note 2026-06-07 : rcNumber DOIT etre 1-20 chiffres purs (regex backend SignupCabinetRequest).
    # ice = 15 chiffres, ifFiscal = 7-8 chiffres. Ne pas mettre de tirets ni de prefixes.
    $payload = @{
        workspaceName = "DiagCab$stamp"
        contactEmail  = $testEmail
        ice           = '001234567000001'
        ifFiscal      = '12345678'
        rcNumber      = '12345'
        city          = 'Casablanca'
        firstName     = 'Diag'
        lastName      = 'Test'
        email         = $testEmail
        phone         = '+212600000000'
        selectedPlan  = 'essentiel'
        cguAccepted   = $true
    } | ConvertTo-Json
    # Fix 2026-06-07 : utilise Invoke-WebRequest -SkipHttpErrorCheck pour eviter
    # le bug PowerShell 7 (HttpResponseMessage.GetResponseStream() inexistant).
    $r = Invoke-WebRequest -Method POST -Uri "$BASE/api/v1/public/signup/cabinet" `
        -Body $payload -ContentType 'application/json' -SkipHttpErrorCheck -ErrorAction SilentlyContinue
    if (-not $r) {
        ko 'signup KO : pas de reponse (gateway down ?)'
    } elseif ($r.StatusCode -ge 400) {
        ko "signup KO : HTTP $($r.StatusCode)"
        info "body : $($r.Content)"
    } else {
        $signup = $r.Content | ConvertFrom-Json
        ok "signup $($r.StatusCode) -- ws=$($signup.workspaceCode) email=$($signup.adminEmail)"

        if ($null -ne $signup.verifyEmailSent) {
            if ($signup.verifyEmailSent) {
                ok 'verifyEmailSent=TRUE -- le back A ENVOYE le mail (verifie Brevo > Transactionnel > Logs pour confirmer la livraison)'
            } else {
                ko 'verifyEmailSent=FALSE -- le back a TENTE mais SMTP a echoue (regarde section [5] tout de suite)'
            }
        } else {
            warn "verifyEmailSent absent -- code en deferCredentials probable (mail differe jusqu'au paiement)"
            info "Si tu as bien mis JURIKA_SIGNUP_DEFER_CREDENTIALS_ALLOWED=false dans .env.local + redemarre auth-service,"
            info '  alors verifyEmailSent DEVRAIT etre present. Si absent, le restart de auth-service n a pas pris la var.'
            info '  Verifie : (Get-Process java | %% { (Get-CimInstance Win32_Process -Filter "ProcessId=$($_.Id)").CommandLine }) | sls auth-service'
        }
        info "message : $($signup.message)"
    }
}

# -------------------------------------------------------------------------
step 5 'Tail logs auth-service : trace email/SMTP/Brevo'
$logFile = 'logs/auth-service.log'
if (Test-Path $logFile) {
    $lines = Get-Content $logFile -Tail 400 -ErrorAction SilentlyContinue
    $emailHits = $lines | Select-String -Pattern 'Echec envoi email|email .* envoye|SmtpEmailSender|deferCredentials|535|Authentication failed|Connection refused|MailException|brevo|sendgrid' -CaseSensitive:$false | Select-Object -Last 15
    if ($emailHits) {
        info 'dernieres traces pertinentes :'
        foreach ($m in $emailHits) { Write-Host "    $($m.Line.Trim())" -ForegroundColor DarkGray }
    } else {
        warn 'aucune trace email/SMTP/Brevo dans les 400 dernieres lignes'
        info 'soit auth-service ne tente meme pas l envoi (deferCredentials), soit logs/ pointe ailleurs.'
    }
} else {
    warn "$logFile introuvable"
}

# -------------------------------------------------------------------------
Write-Host "`n=== Verdict ===" -ForegroundColor Yellow
Write-Host @"

CAS A -- deferCredentials (le plus probable sur ta branche billing) :
   Le wizard 5 etapes ne mail PAS apres l'etape Recap. Il faut aller jusqu a
   la Step Paiement et valider (cocher TEST_BYPASS en dev). IssueCredentialsUseCase
   envoie alors le welcome.html via Brevo.

CAS B -- config Brevo incomplete dans .env.local :
   Variables OBLIGATOIRES pour Brevo :
     SMTP_HOST=smtp-relay.brevo.com
     SMTP_PORT=587
     SMTP_AUTH=true
     SMTP_STARTTLS=true
     SMTP_USER=8XXXXX@smtp-brevo.com    (PAS ton email Brevo, l identifiant SMTP dedie)
     SMTP_PASSWORD=xkeysib-...           (cle generee dans Brevo > Transactionnel > SMTP & API)
     SMTP_FROM=noreply@jurika.ma         (DOIT etre un sender VERIFIE cote Brevo)
   Apres modif : redemarrer auth-service pour relire le .env.local.

CAS C -- sender pas valide cote Brevo (le piege classique) :
   Brevo refuse les mails dont le From est un domaine non verifie.
   Va sur app.brevo.com > Senders, Domains > Domains et verifie que jurika.ma
   a 3 feux verts (SPF + DKIM + DMARC). Si pas le cas : Brevo rejette en 550 5.7.1
   et tu vois 'cause=SendFailedException smtpCode=550' dans les logs.

CAS D -- mail envoye mais bloque par Brevo (spam / liste noire / quota) :
   Si verifyEmailSent=TRUE mais ton client mail ne le voit pas :
   1. app.brevo.com > Transactionnel > Statistiques en temps reel : voir si 'envoye' / 'remis' / 'bloque'
   2. Verifie tes spam
   3. Quota free tier Brevo = 300/j -- si depasse, Brevo accepte mais ne livre pas

PROCHAIN GESTE selon ce que ce script t'a affiche :
   * 'verifyEmailSent absent'        -> finis le paiement (TEST_BYPASS coche)
   * 'verifyEmailSent=FALSE'          -> regarde le smtpCode dans logs/auth-service.log
   * 'config Brevo coherente' + rien -> verifie Brevo > Transactionnel > Logs
                                         (le mail a peut-etre ete refuse cote Brevo, pas cote nous)
"@ -ForegroundColor Yellow
