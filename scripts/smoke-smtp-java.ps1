# smoke-smtp-java.ps1 — Lance SmtpSmokeTest.java avec les env vars de .env.local
# (precedence sur .env). Beaucoup plus fiable que le test .NET pur (smoke-smtp.ps1)
# car JavaMail = meme stack que Spring auth-service en prod.
#
# Usage : .\scripts\smoke-smtp-java.ps1 [destinataire@test.com]

param(
    [Parameter(Position=0)]
    [string]$ToEmail
)

$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $PSScriptRoot

function Load-EnvFile {
    param([string]$Path)
    if (-not (Test-Path $Path)) { return }
    Get-Content $Path | ForEach-Object {
        if ($_ -match '^\s*(SMTP_[A-Z_]+|JURIKA_EMAIL_PROVIDER)\s*=\s*(.+?)\s*$' -and -not $_.StartsWith('#')) {
            $key = $Matches[1]
            $val = $Matches[2] -replace '^["'']|["'']$', ''
            Set-Item -Path "env:$key" -Value $val
        }
    }
}

Write-Host "Chargement .env puis .env.local (precedence)..." -ForegroundColor DarkGray
Load-EnvFile (Join-Path $Root '.env')
Load-EnvFile (Join-Path $Root '.env.local')

if ($ToEmail) { $env:SMTP_TO = $ToEmail }

Push-Location (Join-Path $Root 'backend-java')
try {
    mvn -pl auth-service -q test-compile exec:java `
        "-Dexec.mainClass=ma.jurika.auth.smoke.SmtpSmokeTest" `
        "-Dexec.classpathScope=test"
} finally {
    Pop-Location
}
