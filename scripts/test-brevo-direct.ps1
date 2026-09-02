#!/usr/bin/env pwsh
# Test SMTP Brevo DIRECT, sans passer par JURIKA.
# Lit tes credentials Brevo (env user OU .env.local) et envoie un mail.
# Affiche l'ERREUR EXACTE de Brevo si refus.

$ErrorActionPreference = 'Continue'

# 1. Recup credentials -- priorite env user, sinon .env.local
function GetVar($name) {
    $val = [Environment]::GetEnvironmentVariable($name, 'Process')
    if ($val) { return $val }
    if (Test-Path '.env.local') {
        $line = Get-Content '.env.local' | Where-Object { $_ -match "^$name\s*=" }
        if ($line) {
            $val = ($line -split '=', 2)[1].Trim().Trim('"').Trim("'")
            return $val
        }
    }
    return $null
}

$host_   = GetVar 'SMTP_HOST'
$port    = GetVar 'SMTP_PORT'
$user    = GetVar 'SMTP_USER'
$pass    = GetVar 'SMTP_PASSWORD'
$from    = GetVar 'SMTP_FROM'

Write-Host "=== Config detectee ===" -ForegroundColor Cyan
Write-Host "  SMTP_HOST     : $host_"
Write-Host "  SMTP_PORT     : $port"
Write-Host "  SMTP_USER     : $user"
Write-Host "  SMTP_PASSWORD : *** ($($pass.Length) chars)"
Write-Host "  SMTP_FROM     : $from"

if (-not ($host_ -and $port -and $user -and $pass -and $from)) {
    Write-Host "`n[KO] Une ou plusieurs vars manquantes. Mets-les en env user :" -ForegroundColor Red
    Write-Host "  [Environment]::SetEnvironmentVariable('SMTP_HOST', '...', 'User')" -ForegroundColor Yellow
    exit 1
}

# 2. Destinataire
$to = Read-Host "`nTon vrai email pour recevoir le test"
if (-not $to) { Write-Host "Abandonne." -ForegroundColor Red; exit 1 }

# 3. Envoi DIRECT via .NET (pas Send-MailMessage qui est deprecated et masque erreurs)
Write-Host "`n=== Envoi vers Brevo ===" -ForegroundColor Cyan
Write-Host "  $from -> $to via $host_`:$port" -ForegroundColor Gray

try {
    $msg = New-Object System.Net.Mail.MailMessage
    $msg.From = New-Object System.Net.Mail.MailAddress($from, 'JURIKA Test Direct')
    $msg.To.Add($to)
    $msg.Subject = "Test direct Brevo $(Get-Date -Format 'HH:mm:ss')"
    $msg.Body = @"
Si tu lis ce mail, ta config Brevo est 100% bonne et le probleme cote JURIKA
est ailleurs (sender verifie cote Brevo + vars env JVM).

Si tu ne le recois pas mais que ce script affiche [OK] -> verifie ton spam.
Si ce script affiche une erreur -> Brevo refuse tes credentials.

Heure d'envoi : $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')
Sender        : $from
Recipient     : $to
SMTP host     : $host_`:$port
"@
    $msg.IsBodyHtml = $false

    $client = New-Object System.Net.Mail.SmtpClient($host_, [int]$port)
    $client.EnableSsl = $true
    $client.DeliveryMethod = [System.Net.Mail.SmtpDeliveryMethod]::Network
    $client.UseDefaultCredentials = $false
    $client.Credentials = New-Object System.Net.NetworkCredential($user, $pass)
    $client.Timeout = 15000  # 15s

    $client.Send($msg)
    Write-Host "  [OK] Brevo a accepte le mail SANS exception" -ForegroundColor Green
    Write-Host "`n  Maintenant :" -ForegroundColor Cyan
    Write-Host "  1. Va sur app.brevo.com > Transactionnel > Logs (ou Statistiques temps reel)"
    Write-Host "  2. Tu DOIS voir un evenement pour $to dans les 10s"
    Write-Host "  3. Check ta boite + ton SPAM"
    Write-Host ""
    Write-Host "  Si Brevo voit le mail mais pas dans ta boite -> SPF/DKIM du sender pas verifie"
    Write-Host "  Si Brevo ne voit RIEN -> contradiction (impossible si ce script dit [OK])"

} catch [System.Net.Mail.SmtpException] {
    $e = $_.Exception
    Write-Host "`n  [KO] BREVO A REFUSE -- voici la VRAIE raison :" -ForegroundColor Red
    Write-Host "  StatusCode : $($e.StatusCode)" -ForegroundColor Yellow
    Write-Host "  Message    : $($e.Message)" -ForegroundColor Yellow
    if ($e.InnerException) {
        Write-Host "  Inner      : $($e.InnerException.Message)" -ForegroundColor Yellow
    }

    Write-Host "`n  Codes Brevo frequents :" -ForegroundColor Cyan
    Write-Host "    535          = auth refusee (SMTP_USER/SMTP_PASSWORD mauvais ou cle desactivee)"
    Write-Host "    550 5.7.1    = sender pas verifie (noreply@jurika.ma a verifier dans Brevo > Senders)"
    Write-Host "    421          = quota depasse ou IP bloquee"
    Write-Host "    554 5.7.1    = compte Brevo en pause / desactive"
} catch {
    Write-Host "`n  [KO] Erreur inattendue : $($_.Exception.GetType().Name)" -ForegroundColor Red
    Write-Host "  $($_.Exception.Message)" -ForegroundColor Yellow
    if ($_.Exception.InnerException) {
        Write-Host "  Inner : $($_.Exception.InnerException.Message)" -ForegroundColor Yellow
    }
}
