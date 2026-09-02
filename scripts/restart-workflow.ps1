# JURIKA — Redemarre UNIQUEMENT le workflow-service (port 8083) pour appliquer
# le correctif Etape 8 "Pieces jointes" (validation CIN par personne + CN repris).
#
# `mvn spring-boot:run` RECOMPILE automatiquement avant de demarrer : le nouveau
# code (CreationSarlWorkflow.java) sera donc bien pris en compte.
#
# Usage : depuis PowerShell (racine projet ou n'importe ou) :
#   .\scripts\restart-workflow.ps1

$ErrorActionPreference = "Stop"
$Root = Resolve-Path "$PSScriptRoot\.."
$Backend = Join-Path $Root "backend-java"

# TEMP redirige vers .tmp/ projet (Controlled Folder Access — cf memoire never-stop-winnat)
$ProjectTmp = Join-Path $Root ".tmp"
New-Item -ItemType Directory -Path $ProjectTmp -Force | Out-Null
$env:TEMP = $ProjectTmp; $env:TMP = $ProjectTmp

# Charge .env puis .env.local (memes regles que start-all.ps1 / restart-after-fixes.ps1)
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

# Stop le workflow-service en cours (port 8083)
$procId = (Get-NetTCPConnection -State Listen -LocalPort 8083 -ErrorAction SilentlyContinue).OwningProcess
if ($procId) {
    Write-Host "Stopping workflow-service (PID $procId, port 8083)..." -ForegroundColor Yellow
    Stop-Process -Id $procId -Force -ErrorAction SilentlyContinue
    Start-Sleep -Seconds 2
} else {
    Write-Host "Aucun process sur le port 8083 (deja arrete)." -ForegroundColor DarkGray
}

# Relance dans une NOUVELLE fenetre (logs visibles). Recompile + demarre.
$envSetup = ""
Get-ChildItem env: | Where-Object { $_.Name -match '^(SMTP|SPRING|JWT|AES|JURIKA|RABBIT|REDIS|POSTGRES|CORS|FRONTEND|VITE|EUREKA|MINIO|STRIPE|BILLING|TWILIO|SMS|OCR|OLLAMA|GROQ|RAG|LLM|TEMP|TMP)' } | ForEach-Object {
    $val = $_.Value -replace "'", "''"
    $envSetup += "`$env:$($_.Name)='$val'; "
}
$cmd = "$envSetup cd '$Backend'; mvn -pl workflow-service spring-boot:run -DskipTests"
Write-Host "Starting workflow-service (recompile + run) dans une nouvelle fenetre..." -ForegroundColor Cyan
Start-Process powershell -ArgumentList "-NoExit", "-Command", $cmd -WindowStyle Normal

Write-Host ""
Write-Host "Patiente ~30-40s (recompilation + boot Spring) puis reteste l'etape 8." -ForegroundColor Green
Write-Host "Le service est pret quand tu vois 'Started WorkflowApplication' dans la nouvelle fenetre." -ForegroundColor Green
