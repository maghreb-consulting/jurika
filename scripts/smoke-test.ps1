# scripts/smoke-test.ps1 — wrapper PowerShell
# Sprint Beta (pricing-deploy) — TASK 9.

$ErrorActionPreference = 'Stop'
$ProjectRoot = Split-Path -Parent $PSScriptRoot
& node (Join-Path $PSScriptRoot 'smoke-test.mjs') @args
exit $LASTEXITCODE
