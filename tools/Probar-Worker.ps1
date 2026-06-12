<#
    Probar-Worker.ps1
    ------------------------------------------------------------------
    Prueba el Worker-Vsiaf.ps1 de punta a punta, de forma SEGURA:
      1. Copia la tabla a una carpeta temporal.
      2. Deja una orden de ejemplo en _cola\.
      3. Corre el worker contra esa carpeta temporal.
      4. Verifica que inserto el registro Y que el indice .CDX se actualizo.
    NO toca tus DBF reales.

    REQUISITO: VFPOLEDB instalado. Worker-Vsiaf.ps1 en la misma carpeta que este script.
    Correr con PowerShell de 32 BITS.

    USO:
      C:\Windows\SysWOW64\WindowsPowerShell\v1.0\powershell.exe -ExecutionPolicy Bypass -File "C:\vsiaf\Probar-Worker.ps1" -Origen "C:\vsiaf\dbfs"
#>
param(
    [string]$Origen = "C:\vsiaf\dbfs"
)

if ([Environment]::Is64BitProcess) {
    Write-Host "DEBE CORRER EN POWERSHELL DE 32 BITS:" -ForegroundColor Red
    Write-Host ("  C:\Windows\SysWOW64\WindowsPowerShell\v1.0\powershell.exe -ExecutionPolicy Bypass -File `"{0}`" -Origen `"{1}`"" -f $PSCommandPath, $Origen)
    return
}

$worker = Join-Path $PSScriptRoot "Worker-Vsiaf.ps1"
if (-not (Test-Path $worker)) { Write-Host "No encuentro Worker-Vsiaf.ps1 junto a este script." -ForegroundColor Red; return }

function Get-RecCount($dbf) {
    try {
        $fs=[System.IO.File]::Open($dbf,'Open','Read','ReadWrite')
        $h=New-Object byte[] 8; [void]$fs.Read($h,0,8); $fs.Close()
        return [BitConverter]::ToInt32($h,4)
    } catch { return -1 }
}

# 1. Carpeta temporal con una copia de RESP
$test = Join-Path $env:TEMP ("vsiaf_worker_test_" + (Get-Date -Format "HHmmss"))
New-Item -ItemType Directory -Force -Path $test | Out-Null
Get-ChildItem -Path $Origen -Filter "RESP.*" -File | ForEach-Object { Copy-Item $_.FullName -Destination $test }
$dbf = Join-Path $test "RESP.DBF"
$cdx = Get-ChildItem -Path $test -Filter "RESP.cdx" -File | Select-Object -First 1
if (-not (Test-Path $dbf) -or -not $cdx) { Write-Host "No encontre RESP.DBF/.CDX en $Origen" -ForegroundColor Red; return }

$recAntes = Get-RecCount $dbf
$cdxAntes = (Get-Item $cdx.FullName).LastWriteTime
Write-Host ("ANTES   -> registros: {0} | RESP.CDX {1:N0} bytes  {2}" -f $recAntes, $cdx.Length, $cdxAntes)

# 2. Orden de ejemplo en _cola (valores como TEXTO)
$cola = Join-Path $test "_cola"
New-Item -ItemType Directory -Force -Path $cola | Out-Null
$orden = [ordered]@{
    tabla  = "RESP"
    op     = "INSERT"
    campos = [ordered]@{
        ENTIDAD="148"; UNIDAD="TEST"; CODOFIC="9999"; CODRESP="99997"
        NOMRESP="PRUEBA WORKER"; CARGO="CARGO X"; OBSERV=""; CI="12345678"
        FEULT="2026-06-12"; USUAR="WK"; COD_EXP="1"; API_ESTADO="1"
    }
}
$ordenJson = $orden | ConvertTo-Json
[System.IO.File]::WriteAllText((Join-Path $cola "RESP_demo.json"), $ordenJson, (New-Object System.Text.UTF8Encoding($false)))
Write-Host ("`nOrden dejada en _cola\RESP_demo.json")

# 3. Correr el worker contra la carpeta temporal (una pasada)
Write-Host "`n--- Ejecutando Worker-Vsiaf.ps1 ---" -ForegroundColor Cyan
& $worker -Carpeta $test
Write-Host "--- Fin worker ---`n" -ForegroundColor Cyan

# 4. Verificar
Start-Sleep -Milliseconds 500
$recDespues = Get-RecCount $dbf
$cdxItem = Get-Item $cdx.FullName
$enHechos = Test-Path (Join-Path $test "_hechos\RESP_demo.json")
$enErrores = Test-Path (Join-Path $test "_errores\RESP_demo.json")
Write-Host ("DESPUES -> registros: {0} | RESP.CDX {1:N0} bytes  {2}" -f $recDespues, $cdxItem.Length, $cdxItem.LastWriteTime)
Write-Host ("Orden movida a: {0}" -f $(if($enHechos){"_hechos (OK)"}elseif($enErrores){"_errores (FALLO)"}else{"?"}))

Write-Host "`n=================== RESULTADO ==================="
if ($enHechos -and $recDespues -gt $recAntes -and $cdxItem.LastWriteTime -gt $cdxAntes) {
    Write-Host "EXITO: el worker inserto via VFPOLEDB y el indice se mantuvo solo." -ForegroundColor Green
    Write-Host "=> El worker esta listo. Siguiente paso: que el SCIAF deje las ordenes." -ForegroundColor Green
} else {
    Write-Host "Reviso: algo no cuadro. Si la orden quedo en _errores, mira el .error.txt en:" -ForegroundColor Yellow
    Write-Host ("  {0}" -f (Join-Path $test "_errores"))
    Write-Host "Copiame la salida y el contenido del .error.txt si existe."
}
Write-Host "================================================="
Write-Host ("Carpeta de prueba (la podes borrar luego): {0}" -f $test)
