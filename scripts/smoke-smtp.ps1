# smoke-smtp.ps1 — Test rapide SMTP standalone, lit .env.local
# Usage : .\scripts\smoke-smtp.ps1 [destinataire@test.com]
# Sans argument : envoie vers $SMTP_USER (test loopback).
#
# Objectif : isoler les erreurs SMTP (credentials, TLS, sender autorise)
# AVANT de booter tout auth-service. Te crache le code d'erreur Brevo exact.

param(
    [Parameter(Position=0)]
    [string]$ToEmail
)

$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $PSScriptRoot

# Charge .env.local (precedence) puis .env
function Load-EnvFile {
    param([string]$Path)
    if (-not (Test-Path $Path)) { return @{} }
    $vars = @{}
    Get-Content $Path | ForEach-Object {
        if ($_ -match '^\s*([A-Z0-9_]+)\s*=\s*(.+?)\s*$' -and -not $_.StartsWith('#')) {
            $key = $Matches[1]
            $val = $Matches[2] -replace '^["'']|["'']$', ''
            $vars[$key] = $val
        }
    }
    return $vars
}

$envBase = Load-EnvFile (Join-Path $Root '.env')
$envLocal = Load-EnvFile (Join-Path $Root '.env.local')
$cfg = @{}
foreach ($k in $envBase.Keys) { $cfg[$k] = $envBase[$k] }
foreach ($k in $envLocal.Keys) { $cfg[$k] = $envLocal[$k] }

$smtpHost = $cfg['SMTP_HOST']
$smtpPort = [int]($cfg['SMTP_PORT'] ?? 587)
$smtpUser = $cfg['SMTP_USER']
$smtpPass = $cfg['SMTP_PASSWORD']
$smtpFrom = $cfg['SMTP_FROM'] ?? $smtpUser
$smtpFromName = $cfg['SMTP_FROM_NAME'] ?? 'JURIKA'
$starttls = ($cfg['SMTP_STARTTLS'] ?? 'true') -eq 'true'

if (-not $ToEmail) { $ToEmail = $smtpUser }

Write-Host ""
Write-Host "=== JURIKA SMTP smoke test ===" -ForegroundColor Cyan
Write-Host "Host       : $smtpHost`:$smtpPort"
Write-Host "User       : $smtpUser"
Write-Host "Password   : $($smtpPass.Substring(0, [Math]::Min(8, $smtpPass.Length)))... ($($smtpPass.Length) chars)"
Write-Host "From       : $smtpFrom <$smtpFromName>"
Write-Host "To         : $ToEmail"
Write-Host "STARTTLS   : $starttls"
Write-Host ""

try {
    # IMPORTANT : forcer TLS 1.2 (defaut .NET = 1.0/1.1, refuse par Brevo + Gmail
    # depuis 2020). Sans ce flag, STARTTLS echoue silencieusement et le serveur
    # repond "Please authenticate first" parce que la negotiation TLS n'a pas eu lieu.
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12 -bor [Net.SecurityProtocolType]::Tls13

    $smtp = New-Object Net.Mail.SmtpClient($smtpHost, $smtpPort)
    $smtp.EnableSsl = $starttls
    $smtp.DeliveryMethod = [Net.Mail.SmtpDeliveryMethod]::Network
    $smtp.UseDefaultCredentials = $false
    $smtp.Credentials = New-Object Net.NetworkCredential($smtpUser, $smtpPass)
    $smtp.Timeout = 15000

    $msg = New-Object Net.Mail.MailMessage
    $msg.From = New-Object Net.Mail.MailAddress($smtpFrom, $smtpFromName)
    $msg.To.Add($ToEmail)
    $msg.Subject = "JURIKA SMTP smoke test - $(Get-Date -Format 'HH:mm:ss')"
    $msg.Body = @"
Test SMTP JURIKA reussi.

Si tu recois ce mail, ta configuration SMTP est OK et le signup va marcher.

Host : $smtpHost`:$smtpPort
From : $smtpFrom
Timestamp : $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')
"@
    $msg.IsBodyHtml = $false

    Write-Host "Envoi en cours..." -ForegroundColor Yellow
    $smtp.Send($msg)
    Write-Host ""
    Write-Host "[OK] MAIL ENVOYE ! Verifie $ToEmail (verifie aussi le dossier spam)." -ForegroundColor Green
    Write-Host ""
} catch [Net.Mail.SmtpException] {
    Write-Host ""
    Write-Host "[ERREUR SMTP] $($_.Exception.StatusCode)" -ForegroundColor Red
    Write-Host "Message    : $($_.Exception.Message)" -ForegroundColor Red
    if ($_.Exception.InnerException) {
        Write-Host "Cause      : $($_.Exception.InnerException.Message)" -ForegroundColor Red
    }
    Write-Host ""
    Write-Host "Decodage habituel :" -ForegroundColor Yellow
    Write-Host " - 'MailboxUnavailable' / 550 -> Sender '$smtpFrom' pas valide cote Brevo."
    Write-Host "   Fix : valide jurika.ma dans Brevo OU change SMTP_FROM pour un sender deja valide."
    Write-Host " - 'ClientNotPermitted' / 535 -> SMTP_PASSWORD invalide (regenere la clef Brevo)."
    Write-Host " - 'GeneralFailure' / Timeout -> Pare-feu local bloque le port 587 sortant."
    Write-Host ""
    exit 1
} catch {
    Write-Host ""
    Write-Host "[ERREUR] $($_.Exception.GetType().Name)" -ForegroundColor Red
    Write-Host "Message : $($_.Exception.Message)" -ForegroundColor Red
    if ($_.Exception.InnerException) {
        Write-Host "Cause   : $($_.Exception.InnerException.Message)" -ForegroundColor Red
    }
    Write-Host ""
    exit 1
}
