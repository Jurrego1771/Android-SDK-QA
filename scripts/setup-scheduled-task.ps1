# =============================================================================
# setup-scheduled-task.ps1 — Registra el smoke diario en Windows Task Scheduler
#
# Ejecutar UNA SOLA VEZ como Administrador:
#   powershell -ExecutionPolicy Bypass -File scripts\setup-scheduled-task.ps1
#
# Para ver la tarea luego:  taskschd.msc  (buscá "SDK QA Daily Smoke")
# Para borrarla:            Unregister-ScheduledTask -TaskName "SDK QA Daily Smoke" -Confirm:$false
# =============================================================================

param(
    [string]$Hour   = "09",     # Hora de ejecución (24h)
    [string]$Minute = "00"      # Minuto
)

$ErrorActionPreference = "Stop"

# ─── Detectar Git Bash ────────────────────────────────────────────────────────
$gitBashPaths = @(
    "C:\Program Files\Git\bin\bash.exe",
    "C:\Program Files (x86)\Git\bin\bash.exe",
    "$env:LOCALAPPDATA\Programs\Git\bin\bash.exe"
)

$gitBash = $gitBashPaths | Where-Object { Test-Path $_ } | Select-Object -First 1

if (-not $gitBash) {
    Write-Error "Git Bash no encontrado. Instalalo desde https://git-scm.com"
    exit 1
}

Write-Host "Git Bash encontrado: $gitBash" -ForegroundColor Green

# ─── Paths ────────────────────────────────────────────────────────────────────
$projectRoot = Split-Path -Parent $PSScriptRoot
# Convertir a formato Unix para bash
$scriptUnix  = ($projectRoot -replace '\\', '/') -replace '^([A-Za-z]):', { "/$(($_ -split ':')[0].ToLower())" }
$dailyScript = "$scriptUnix/scripts/daily-smoke.sh"

Write-Host "Proyecto: $projectRoot"
Write-Host "Script Unix: $dailyScript"

# ─── Crear acción ─────────────────────────────────────────────────────────────
$action = New-ScheduledTaskAction `
    -Execute  $gitBash `
    -Argument "--login -c `"bash '$dailyScript'`"" `
    -WorkingDirectory $projectRoot

# ─── Crear trigger (diario a la hora configurada) ─────────────────────────────
$trigger = New-ScheduledTaskTrigger `
    -Daily `
    -At "$($Hour):$($Minute)"

# ─── Configuración ────────────────────────────────────────────────────────────
$settings = New-ScheduledTaskSettingsSet `
    -ExecutionTimeLimit  (New-TimeSpan -Hours 1) `
    -StartWhenAvailable                          `  # Si la máquina estaba apagada, corre al encenderse
    -RunOnlyIfNetworkAvailable                   `
    -WakeToRun:$false

# ─── Registrar tarea ──────────────────────────────────────────────────────────
$taskName = "SDK QA Daily Smoke"

# Borrar si ya existe
Unregister-ScheduledTask -TaskName $taskName -Confirm:$false -ErrorAction SilentlyContinue

Register-ScheduledTask `
    -TaskName   $taskName `
    -Action     $action `
    -Trigger    $trigger `
    -Settings   $settings `
    -RunLevel   Highest `
    -Description "Ejecuta smoke tests del Mediastream SDK Android y notifica a Slack" | Out-Null

Write-Host ""
Write-Host "════════════════════════════════════════" -ForegroundColor Cyan
Write-Host "  Tarea registrada exitosamente" -ForegroundColor Green
Write-Host "  Nombre:  $taskName" -ForegroundColor White
Write-Host "  Horario: todos los días a las $($Hour):$($Minute)" -ForegroundColor White
Write-Host "  Comando: $gitBash --login -c `"bash '$dailyScript'`"" -ForegroundColor White
Write-Host "════════════════════════════════════════" -ForegroundColor Cyan
Write-Host ""
Write-Host "Para cambiar el horario, edita y vuelve a correr este script:"
Write-Host "  powershell -ExecutionPolicy Bypass -File scripts\setup-scheduled-task.ps1 -Hour 08 -Minute 30"
Write-Host ""
Write-Host "Para correr manualmente ahora:"
Write-Host "  Start-ScheduledTask -TaskName '$taskName'"
