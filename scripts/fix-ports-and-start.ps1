<#
  JURIKA -- Diagnostic + liberation des ports + correction winnat + demarrage.
  A LANCER EN ADMINISTRATEUR (clic droit PowerShell > Executer en tant qu'administrateur),
  puis :  cd C:\dev\JURIKA\projet\scripts ;  .\fix-ports-and-start.ps1
#>

$ErrorActionPreference = 'SilentlyContinue'
# Ports applicatifs (Java 8080-8090 + Eureka 8761 + realtime 3000 + Vite 5173-5175)
# PLUS ports d'infra publies par Docker sur l'hote : Postgres 5432, Redis 6379,
# RabbitMQ 5672/15672, MinIO 9000-9001, MailHog 8025/1025.
# ATTENTION : 5432 tombe frequemment dans une plage dynamique Hyper-V/winnat
# (ex. 5355-5454) -> le bind Postgres echoue en WinError 10013 alors que RIEN
# n'ecoute. Il DOIT donc figurer ici, sinon la detection cause (b) est aveugle
# a la panne la plus courante (Postgres KO -> toute la stack Java KO en cascade).
$ports = 8080,8081,8082,8083,8084,8085,8086,8087,8088,8089,8090,8761,3000,5173,5174,5175,`
         5432,6379,5672,15672,9000,9001,8025,1025

function Test-Admin {
    $id = [Security.Principal.WindowsIdentity]::GetCurrent()
    (New-Object Security.Principal.WindowsPrincipal($id)).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
}

Write-Host "=== 1. Processus qui occupent les ports du stack ===" -ForegroundColor Cyan
$busy = @()
foreach ($p in $ports) {
    $pids = Get-NetTCPConnection -State Listen -LocalPort $p | Select-Object -ExpandProperty OwningProcess -Unique
    foreach ($procId in $pids) {
        if ($procId -and $procId -ne 0) {
            $name = (Get-Process -Id $procId).ProcessName
            Write-Host ("  Port {0,-5} occupe par {1} (PID {2})" -f $p, $name, $procId) -ForegroundColor Yellow
            $busy += $procId
        }
    }
}
if ($busy.Count -eq 0) {
    Write-Host "  Aucun processus en ecoute sur ces ports." -ForegroundColor Green
} else {
    Write-Host "  -> Arret de ces processus..." -ForegroundColor Yellow
    $busy | Select-Object -Unique | ForEach-Object { Stop-Process -Id $_ -Force }
    Start-Sleep -Seconds 2
    Write-Host "  Processus arretes." -ForegroundColor Green
}

# Java residuels du stack (sans fenetre) via ligne de commande
Get-CimInstance Win32_Process -Filter "Name='java.exe'" |
    Where-Object { $_.CommandLine -match 'jurika|spring-boot' } |
    ForEach-Object { Write-Host "  Arret java PID $($_.ProcessId)" -ForegroundColor Yellow; Stop-Process -Id $_.ProcessId -Force }

Write-Host ""
Write-Host "=== 2. Plages de ports RESERVEES par Windows (Hyper-V/WSL/Docker/winnat) ===" -ForegroundColor Cyan
$excluded = netsh int ipv4 show excludedportrange protocol=tcp
$excluded | Select-Object -Skip 3 | Where-Object { $_ -match '^\s*\d' } | ForEach-Object { Write-Host "  $_" }

# Un des ports du stack tombe-t-il dans une plage exclue ?
$conflict = $false
foreach ($line in $excluded) {
    if ($line -match '^\s*(\d+)\s+(\d+)') {
        $start = [int]$matches[1]; $end = [int]$matches[2]
        foreach ($p in $ports) { if ($p -ge $start -and $p -le $end) { $conflict = $true; Write-Host "  !! Port $p reserve par Windows (plage $start-$end)" -ForegroundColor Red } }
    }
}

if ($conflict) {
    Write-Host ""
    Write-Host "=== 3. Correction : liberation des reservations Windows (winnat) ===" -ForegroundColor Cyan
    if (-not (Test-Admin)) {
        Write-Host "  ! Il faut relancer ce script EN ADMINISTRATEUR pour corriger le winnat." -ForegroundColor Red
        Write-Host "    (clic droit PowerShell > Executer en tant qu'administrateur)" -ForegroundColor Red
        return
    }
    net stop winnat | Out-Null
    net start winnat | Out-Null
    Write-Host "  winnat redemarre : reservations dynamiques liberees." -ForegroundColor Green
    # Reserve TOUS les ports du stack (appli + infra) pour que Hyper-V/winnat ne
    # les reprenne plus (store=persistent survit au reboot). On inclut 5432 & co :
    # c'est LA cause racine des demarrages Postgres qui echouent en WinError 10013.
    $reservations = @(
        @{ start = 8080;  count = 15 },  # Java 8080-8090 (+ marge 8091-8094)
        @{ start = 8761;  count = 1  },  # Eureka
        @{ start = 3000;  count = 1  },  # realtime-service
        @{ start = 5173;  count = 3  },  # Vite 5173-5175
        @{ start = 5432;  count = 1  },  # Postgres  <-- le fix
        @{ start = 6379;  count = 1  },  # Redis
        @{ start = 5672;  count = 1  },  # RabbitMQ AMQP
        @{ start = 15672; count = 1  },  # RabbitMQ mgmt UI
        @{ start = 9000;  count = 2  },  # MinIO 9000-9001
        @{ start = 8025;  count = 1  },  # MailHog UI
        @{ start = 1025;  count = 1  }   # MailHog SMTP
    )
    foreach ($r in $reservations) {
        netsh int ipv4 add excludedportrange protocol=tcp startport=$($r.start) numberofports=$($r.count) store=persistent | Out-Null
    }
    Write-Host "  Ports stack (appli + infra, dont 5432/6379/5672/9000/8025) reserves pour JURIKA." -ForegroundColor Green
} else {
    Write-Host "  OK : aucun port du stack n'est reserve par Windows." -ForegroundColor Green
}

Write-Host ""
Write-Host "=== 4. Demarrage de la stack ===" -ForegroundColor Cyan
$start = Join-Path $PSScriptRoot 'start-local.ps1'
if (Test-Path $start) {
    Write-Host "  Lancement de start-local.ps1 ..." -ForegroundColor Green
    & $start
} else {
    Write-Host "  start-local.ps1 introuvable ici. Demarre ta stack comme d'habitude :" -ForegroundColor Yellow
    Write-Host "    - Infra Docker :  cd ..\infrastructure ; docker compose up -d" -ForegroundColor Gray
    Write-Host "    - Services      :  ton script/commande de demarrage habituel" -ForegroundColor Gray
}
