<#
    Probar-Reindex-Vfpoledb.ps1
    ------------------------------------------------------------------
    Prueba si se puede REINDEXAR una tabla del VSIAF usando VFPOLEDB (gratis),
    sin abrir el VSIAF y sin compilar nada. NO toca tus datos reales: copia la
    tabla a una carpeta temporal y reindexa ahi.

    REQUISITO: VFPOLEDB instalado (provider de FoxPro de Microsoft, 32 bits).
    IMPORTANTE: VFPOLEDB es de 32 bits -> hay que correr con el PowerShell de 32 bits.

    USO (copialo tal cual):
      C:\Windows\SysWOW64\WindowsPowerShell\v1.0\powershell.exe -ExecutionPolicy Bypass -File "C:\ruta\Probar-Reindex-Vfpoledb.ps1" -Origen "C:\vsiaf\dbfs" -Tabla RESP
#>
param(
    [string]$Origen = "C:\vsiaf\dbfs",
    [string]$Tabla  = "RESP"
)

# --- Debe correr en 32 bits (VFPOLEDB es 32 bits) ---
if ([Environment]::Is64BitProcess) {
    Write-Host "ESTE SCRIPT DEBE CORRER EN POWERSHELL DE 32 BITS." -ForegroundColor Red
    Write-Host "Volve a ejecutarlo asi:" -ForegroundColor Yellow
    Write-Host ("  C:\Windows\SysWOW64\WindowsPowerShell\v1.0\powershell.exe -ExecutionPolicy Bypass -File `"{0}`" -Origen `"{1}`" -Tabla {2}" -f $PSCommandPath, $Origen, $Tabla)
    return
}

# --- Carpeta temporal de prueba (con hora para no pisar nada) ---
$test = Join-Path $env:TEMP ("vsiaf_test_reindex_" + (Get-Date -Format "HHmmss"))
New-Item -ItemType Directory -Force -Path $test | Out-Null
Write-Host ("Carpeta de prueba: {0}" -f $test)

# --- Copiar la tabla (DBF + CDX + FPT + DBT) a la carpeta de prueba ---
Get-ChildItem -Path $Origen -Filter "$Tabla.*" -File | ForEach-Object { Copy-Item $_.FullName -Destination $test }
$cdx = Get-ChildItem -Path $test -Filter "$Tabla.cdx" -File | Select-Object -First 1
if (-not $cdx) {
    Write-Host ("No se encontro {0}.CDX en {1}. Aborto." -f $Tabla, $Origen) -ForegroundColor Red
    return
}
$antesLen = $cdx.Length; $antesFecha = $cdx.LastWriteTime
Write-Host ("ANTES   -> {0}: {1:N0} bytes  {2}" -f $cdx.Name, $antesLen, $antesFecha)

function Probar-Metodo($titulo, $scriptBlockText) {
    Write-Host ("`n--- Metodo: {0} ---" -f $titulo)
    try {
        $conn = New-Object -ComObject ADODB.Connection
        $conn.Open("Provider=VFPOLEDB.1;Data Source=$test;Collating Sequence=machine;")
        & $scriptBlockText $conn
        $conn.Close()
        Write-Host "  Comando ejecutado SIN error." -ForegroundColor Green
        return $true
    } catch {
        Write-Host ("  ERROR: {0}" -f $_.Exception.Message) -ForegroundColor Red
        return $false
    }
}

# Metodo A: EXECSCRIPT con todo junto (lo mas probable que funcione)
$okA = Probar-Metodo "EXECSCRIPT" {
    param($conn)
    $cmd = "EXECSCRIPT([USE $Tabla EXCLUSIVE]+CHR(13)+[REINDEX]+CHR(13)+[USE])"
    $conn.Execute($cmd) | Out-Null
}

# Metodo B: comandos sueltos (por si EXECSCRIPT esta restringido)
$okB = $false
if (-not $okA) {
    $okB = Probar-Metodo "Comandos sueltos" {
        param($conn)
        $conn.Execute("USE $Tabla EXCLUSIVE") | Out-Null
        $conn.Execute("REINDEX") | Out-Null
        $conn.Execute("USE") | Out-Null
    }
}

# --- Verificar si el CDX se reconstruyo ---
Start-Sleep -Milliseconds 400
$cdx2 = Get-Item $cdx.FullName
Write-Host ("`nDESPUES -> {0}: {1:N0} bytes  {2}" -f $cdx2.Name, $cdx2.Length, $cdx2.LastWriteTime)

Write-Host "`n=================== RESULTADO ==================="
if ($cdx2.LastWriteTime -gt $antesFecha) {
    Write-Host "EXITO: VFPOLEDB SI puede reindexar (el CDX se reconstruyo)." -ForegroundColor Green
    Write-Host ("Metodo que funciono: {0}" -f (@("EXECSCRIPT")[0] | ForEach-Object { if($okA){"EXECSCRIPT"}elseif($okB){"Comandos sueltos"}else{"?"} }))
} else {
    Write-Host "NO se detecto reindex (el CDX no cambio). Copiame TODO lo que imprimio para diagnosticar." -ForegroundColor Yellow
}
Write-Host "================================================="
