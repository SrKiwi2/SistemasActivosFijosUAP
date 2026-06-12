<#
    Probar-Insert-Vfpoledb.ps1  (v2)
    ------------------------------------------------------------------
    Prueba si VFPOLEDB inserta un registro en una tabla del VSIAF
    MANTENIENDO el indice .CDX automaticamente. NO toca datos reales:
    copia la tabla a una carpeta temporal y prueba ahi.

    REQUISITO: VFPOLEDB instalado. Correr con PowerShell de 32 BITS.

    USO:
      C:\Windows\SysWOW64\WindowsPowerShell\v1.0\powershell.exe -ExecutionPolicy Bypass -File "C:\vsiaf\Probar-Insert-Vfpoledb.ps1" -Origen "C:\vsiaf\dbfs" -Tabla RESP
#>
param(
    [string]$Origen = "C:\vsiaf\dbfs",
    [string]$Tabla  = "RESP"
)

if ([Environment]::Is64BitProcess) {
    Write-Host "DEBE CORRER EN POWERSHELL DE 32 BITS:" -ForegroundColor Red
    Write-Host ("  C:\Windows\SysWOW64\WindowsPowerShell\v1.0\powershell.exe -ExecutionPolicy Bypass -File `"{0}`" -Origen `"{1}`" -Tabla {2}" -f $PSCommandPath,$Origen,$Tabla)
    return
}

function Get-RecCount($dbf) {
    try {
        $fs=[System.IO.File]::Open($dbf,'Open','Read','ReadWrite')
        $h=New-Object byte[] 8; [void]$fs.Read($h,0,8); $fs.Close()
        return [BitConverter]::ToInt32($h,4)
    } catch { return -1 }
}

$test = Join-Path $env:TEMP ("vsiaf_test_insert_" + (Get-Date -Format "HHmmss"))
New-Item -ItemType Directory -Force -Path $test | Out-Null
Write-Host ("Carpeta de prueba: {0}" -f $test)

Get-ChildItem -Path $Origen -Filter "$Tabla.*" -File | ForEach-Object { Copy-Item $_.FullName -Destination $test }
$dbf = Join-Path $test "$Tabla.DBF"
$cdx = Get-ChildItem -Path $test -Filter "$Tabla.cdx" -File | Select-Object -First 1
if (-not (Test-Path $dbf) -or -not $cdx) { Write-Host "No se encontro $Tabla.DBF/.CDX" -ForegroundColor Red; return }

$recAntes = Get-RecCount $dbf
$cdxAntes = (Get-Item $cdx.FullName).LastWriteTime
Write-Host ("ANTES   -> registros: {0} | {1}: {2:N0} bytes  {3}" -f $recAntes, $cdx.Name, $cdx.Length, $cdxAntes)

$marca = 99999
$conn = $null
try {
    $conn = New-Object -ComObject ADODB.Connection
    $conn.Open("Provider=VFPOLEDB.1;Data Source=$test;Collating Sequence=machine;")

    # Los 12 campos de RESP (todos obligatorios, incluido el memo OBSERV)
    $sql = "INSERT INTO $Tabla (ENTIDAD, UNIDAD, CODOFIC, CODRESP, NOMRESP, CARGO, OBSERV, CI, FEULT, USUAR, COD_EXP, API_ESTADO) " +
           "VALUES ('148', 'TEST', 9999, $marca, 'PRUEBA VFPOLEDB', 'CARGO PRUEBA', '', '00000000', {^2026-06-12}, 'TEST', 1, 1)"
    Write-Host ("`nINSERT -> {0}" -f $sql)
    $conn.Execute($sql) | Out-Null
    Write-Host "  INSERT ejecutado SIN error." -ForegroundColor Green

    $rs = $conn.Execute("SELECT CODRESP, NOMRESP, CARGO FROM $Tabla WHERE CODRESP = $marca")
    if (-not $rs.EOF) {
        Write-Host ("  Leido de vuelta: CODRESP={0}  NOMRESP={1}  CARGO={2}" -f `
            $rs.Fields.Item("CODRESP").Value, $rs.Fields.Item("NOMRESP").Value, $rs.Fields.Item("CARGO").Value) -ForegroundColor Green
    } else {
        Write-Host "  No se pudo leer de vuelta el registro." -ForegroundColor Yellow
    }
    $rs.Close()
} catch {
    Write-Host ("  ERROR: {0}" -f $_.Exception.Message) -ForegroundColor Red
} finally {
    if ($conn -ne $null -and $conn.State -eq 1) { $conn.Close() }
}

Start-Sleep -Milliseconds 500
$recDespues = Get-RecCount $dbf
$cdxItem = Get-Item $cdx.FullName
Write-Host ("`nDESPUES -> registros: {0} | {1}: {2:N0} bytes  {3}" -f $recDespues, $cdxItem.Name, $cdxItem.Length, $cdxItem.LastWriteTime)

Write-Host "`n=================== RESULTADO ==================="
$insertOk = ($recDespues -gt $recAntes) -and ($recAntes -ge 0)
$indiceOk = $cdxItem.LastWriteTime -gt $cdxAntes
if ($insertOk -and $indiceOk) {
    Write-Host "EXITO TOTAL: VFPOLEDB inserto el registro Y actualizo el indice .CDX solo." -ForegroundColor Green
    Write-Host "=> Camino confirmado: el SCIAF escribe via VFPOLEDB y el .CDX nunca se rompe." -ForegroundColor Green
} elseif ($insertOk) {
    Write-Host "PARCIAL: inserto pero el .CDX no cambio de fecha. Revisar mantenimiento de indice." -ForegroundColor Yellow
} else {
    Write-Host "NO inserto. Copiame todo lo que imprimio." -ForegroundColor Yellow
}
Write-Host "================================================="
