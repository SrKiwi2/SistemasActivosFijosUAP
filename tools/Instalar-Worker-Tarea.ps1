<#
    Instalar-Worker-Tarea.ps1
    ------------------------------------------------------------------
    Registra Worker-Vsiaf.ps1 como TAREA PROGRAMADA PERMANENTE en la VM
    Windows del VSIAF, para que arranque solo al encender la maquina y se
    quede corriendo en bucle (poll cada 5 s) sin depender de una ventana
    de PowerShell abierta.

    Que hace la tarea:
      - Arranca AL INICIO del sistema (no requiere que nadie inicie sesion).
      - Corre como SYSTEM, con privilegios elevados.
      - Lanza el PowerShell de 32 BITS (VFPOLEDB es 32-bit) con -Loop.
      - Si el proceso muere, Task Scheduler lo reinicia cada 1 min.
      - Sin limite de tiempo de ejecucion (corre indefinidamente).

    REQUISITOS:
      - Correr este script como ADMINISTRADOR (clic derecho > Ejecutar como admin,
        o desde un PowerShell elevado). Da igual 32 o 64 bits para INSTALAR;
        la tarea ya invoca el de 32 bits para EJECUTAR.
      - VFPOLEDB instalado y registrado en 32-bit (SysWOW64\regsvr32).

    USO (en la VM del VSIAF, PowerShell elevado):
      .\Instalar-Worker-Tarea.ps1 -Carpeta "C:\vsiaf\vsiaf_red\dbfs" -Worker "C:\vsiaf\Worker-Vsiaf.ps1"

    Para QUITAR la tarea:
      Unregister-ScheduledTask -TaskName "Worker-Vsiaf" -Confirm:$false
#>
param(
    # Carpeta local que contiene (o contendra) la subcarpeta _cola. Es la misma
    # que el SCIAF ve como /mnt/dbfwin. AJUSTAR a la ruta real de esta VM.
    [string]$Carpeta  = "C:\vsiaf\vsiaf_red\dbfs",
    # Ruta del Worker-Vsiaf.ps1 ya copiado en esta VM.
    [string]$Worker   = "C:\vsiaf\Worker-Vsiaf.ps1",
    [string]$TaskName = "Worker-Vsiaf"
)

$ErrorActionPreference = "Stop"

# --- Verificaciones previas ----------------------------------------------
$esAdmin = ([Security.Principal.WindowsPrincipal] `
    [Security.Principal.WindowsIdentity]::GetCurrent()
    ).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $esAdmin) {
    Write-Host "DEBE correr como ADMINISTRADOR (para registrar una tarea como SYSTEM)." -ForegroundColor Red
    return
}

if (-not (Test-Path $Worker)) {
    Write-Host ("No encuentro el worker en: {0}" -f $Worker) -ForegroundColor Red
    Write-Host "Copia Worker-Vsiaf.ps1 a esa ruta o pasa -Worker con la ruta correcta." -ForegroundColor Yellow
    return
}
if (-not (Test-Path $Carpeta)) {
    Write-Host ("AVISO: la carpeta {0} no existe todavia." -f $Carpeta) -ForegroundColor Yellow
    Write-Host "Confirma que es la carpeta donde el SCIAF deja _cola (la que monta como /mnt/dbfwin)." -ForegroundColor Yellow
}

$ps32 = "C:\Windows\SysWOW64\WindowsPowerShell\v1.0\powershell.exe"
if (-not (Test-Path $ps32)) {
    Write-Host ("No encuentro el PowerShell de 32 bits en {0}" -f $ps32) -ForegroundColor Red
    return
}

# --- Definir la tarea -----------------------------------------------------
$argumento = '-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File "{0}" -Carpeta "{1}" -Loop' -f $Worker, $Carpeta

$action = New-ScheduledTaskAction -Execute $ps32 -Argument $argumento

$trigger = New-ScheduledTaskTrigger -AtStartup

$principal = New-ScheduledTaskPrincipal -UserId "SYSTEM" -LogonType ServiceAccount -RunLevel Highest

$settings = New-ScheduledTaskSettingsSet `
    -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries `
    -StartWhenAvailable `
    -RestartCount 999 -RestartInterval (New-TimeSpan -Minutes 1) `
    -ExecutionTimeLimit ([TimeSpan]::Zero) `
    -MultipleInstances IgnoreNew

Register-ScheduledTask -TaskName $TaskName -Action $action -Trigger $trigger `
    -Principal $principal -Settings $settings -Force | Out-Null

Write-Host ("Tarea '{0}' registrada." -f $TaskName) -ForegroundColor Green

# --- Arrancarla ya, sin esperar a reiniciar -------------------------------
Start-ScheduledTask -TaskName $TaskName
Start-Sleep -Seconds 2
$info = Get-ScheduledTask -TaskName $TaskName | Get-ScheduledTaskInfo
Write-Host ("Estado: {0} | Ultimo resultado: {1}" -f `
    (Get-ScheduledTask -TaskName $TaskName).State, $info.LastTaskResult) -ForegroundColor Cyan

Write-Host ""
Write-Host "Listo. Comprobaciones utiles:" -ForegroundColor Green
Write-Host ("  - Log del worker:   {0}" -f (Join-Path $Carpeta 'worker.log'))
Write-Host  "  - Ver la tarea:     Get-ScheduledTask -TaskName '$TaskName' | Get-ScheduledTaskInfo"
Write-Host  "  - Reiniciarla:      Restart-ScheduledTask -TaskName '$TaskName'"
Write-Host  "  - Quitarla:         Unregister-ScheduledTask -TaskName '$TaskName' -Confirm:`$false"
