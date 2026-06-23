<#
    Registrar-VFPOLEDB-SYSTEM.ps1
    ------------------------------------------------------------------
    Registra el proveedor OLE DB de FoxPro (VFPOLEDB) BAJO LA CUENTA SYSTEM,
    que es la cuenta con la que corre la tarea Worker-Vsiaf.

    POR QUE ASI Y NO UN regsvr32 NORMAL:
    En esta VM, `regsvr32` ejecutado por un admin -aun elevado- escribe el
    registro COM en el HKCU del admin (HKCU\Software\Classes), NO en HKLM.
    El worker corre como SYSTEM, que tiene su propio perfil, y por eso NO ve
    ese registro -> "No se encontro el proveedor especificado" (FATAL en
    worker.log) y las ordenes se quedan atascadas en _cola.
    La solucion es registrar VFPOLEDB ejecutando regsvr32 COMO SYSTEM, para
    que quede en el perfil de SYSTEM (HKU\S-1-5-18\...\Classes), justo donde
    el worker lo busca. Ese registro es persistente (sobrevive reinicios).

    CUANDO USARLO:
    Si el worker.log vuelve a mostrar "FATAL ... No se encontro el proveedor",
    corre este script (PowerShell ELEVADO) y reinicia el worker.

    USO (PowerShell como administrador):
        .\Registrar-VFPOLEDB-SYSTEM.ps1
#>
param(
    [string]$Dll = "C:\Program Files (x86)\Common Files\System\Ole DB\vfpoledb.dll"
)

$ErrorActionPreference = "Stop"

$esAdmin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()
    ).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $esAdmin) { Write-Host "DEBE correr como ADMINISTRADOR." -ForegroundColor Red; return }

if (-not (Test-Path $Dll)) {
    Write-Host ("No encuentro vfpoledb.dll en: {0}" -f $Dll) -ForegroundColor Red
    Write-Host "Buscalo con: Get-ChildItem C:\ -Recurse -Filter vfpoledb.dll -ErrorAction SilentlyContinue" -ForegroundColor Yellow
    Write-Host "y vuelve a llamar con -Dll <ruta>. Si no existe, instala el redistribuible de VFPOLEDB." -ForegroundColor Yellow
    return
}

$taskName = "Reg-VFPOLEDB-once"
$regsvr   = "C:\Windows\SysWOW64\regsvr32.exe"   # 32-bit: VFPOLEDB es 32-bit

Write-Host "Registrando VFPOLEDB como SYSTEM..." -ForegroundColor Cyan
$action    = New-ScheduledTaskAction -Execute $regsvr -Argument ('/s "{0}"' -f $Dll)
$principal = New-ScheduledTaskPrincipal -UserId "SYSTEM" -LogonType ServiceAccount -RunLevel Highest
Register-ScheduledTask -TaskName $taskName -Action $action -Principal $principal -Force | Out-Null
Start-ScheduledTask -TaskName $taskName
Start-Sleep -Seconds 6
Unregister-ScheduledTask -TaskName $taskName -Confirm:$false

# Verificacion: el registro queda en el perfil de SYSTEM (S-1-5-18), no en HKLM.
$enSystem = Test-Path "Registry::HKEY_USERS\S-1-5-18\SOFTWARE\Classes\VFPOLEDB.1"
$enHklm   = Test-Path "HKLM:\SOFTWARE\WOW6432Node\Classes\VFPOLEDB.1"
Write-Host ("VFPOLEDB.1 en perfil SYSTEM : {0}" -f $enSystem) -ForegroundColor (if ($enSystem) { "Green" } else { "Yellow" })
Write-Host ("VFPOLEDB.1 en HKLM 32-bit   : {0}  (no es necesario que sea True)" -f $enHklm)

Write-Host ""
if ($enSystem -or $enHklm) {
    Write-Host "Listo. Reinicia el worker y verifica:" -ForegroundColor Green
} else {
    Write-Host "No se detecto el registro; revisa worker.log tras reiniciar el worker." -ForegroundColor Yellow
}
Write-Host '  Stop-ScheduledTask  -TaskName "Worker-Vsiaf" -ErrorAction SilentlyContinue'
Write-Host '  Start-ScheduledTask -TaskName "Worker-Vsiaf"'
Write-Host '  Get-Content "C:\vsiaf\vsiaf_red\dbfs\worker.log" -Tail 8'
