<#
    Inspeccion-Dbf.ps1
    ------------------------------------------------------------------
    Genera un reporte de texto con la estructura de los archivos DBF del VSIAF:
      - Inventario de archivos de la carpeta (DBF / CDX / FPT / DBT, tamaños, fechas)
      - Por cada .DBF: version, nro de registros, campos (nombre/tipo/largo/decimales)
      - Si la tabla DECLARA indice estructural .CDX y memo, y si esos archivos EXISTEN
      - Alerta cuando la tabla espera un .CDX que no esta presente (causa del Error 26 de FoxPro)

    No modifica nada: solo lee cabeceras. Salida lista para copiar/pegar.

    USO (en la VM Windows donde estan los DBF, o apuntando a un share UNC):
      powershell -ExecutionPolicy Bypass -File .\Inspeccion-Dbf.ps1 -Carpeta "C:\vsiaf\vsiaf_red\dbfs"
      powershell -ExecutionPolicy Bypass -File .\Inspeccion-Dbf.ps1 -Carpeta "\\172.16.22.3\dbfs"

    El reporte se guarda en el Escritorio como reporte_dbf.txt (o usa -Salida).
#>
param(
    [string]$Carpeta = "C:\vsiaf\vsiaf_red\dbfs",
    [string]$Salida  = "$env:USERPROFILE\Desktop\reporte_dbf.txt"
)

$tipos = @{
    'C'='Character'; 'N'='Numeric'; 'F'='Float'; 'D'='Date'; 'L'='Logical';
    'M'='Memo'; 'I'='Integer'; 'T'='DateTime'; 'Y'='Currency'; 'B'='Double';
    'G'='General'; 'P'='Picture'; 'Q'='Varbinary'; 'V'='Varchar'
}

$sb = New-Object System.Text.StringBuilder
function Add-Line($t = "") { [void]$sb.AppendLine($t) }

if (-not (Test-Path $Carpeta)) {
    Write-Host "ERROR: no existe la carpeta '$Carpeta'." -ForegroundColor Red
    return
}

Add-Line ("REPORTE DBF  -  Carpeta: {0}" -f $Carpeta)
Add-Line ("Generado: {0}" -f (Get-Date))
Add-Line ("=" * 72)

# ── 1. Inventario de archivos ────────────────────────────────────────────
Add-Line ""
Add-Line "INVENTARIO DE ARCHIVOS:"
Get-ChildItem -Path $Carpeta -File | Sort-Object Name | ForEach-Object {
    Add-Line ("  {0,-22} {1,14:N0} bytes   {2}" -f $_.Name, $_.Length, $_.LastWriteTime)
}

# ── 2. Estructura de cada .DBF (solo lectura de cabecera) ─────────────────
$dbfs = Get-ChildItem -Path $Carpeta -Filter *.dbf -File | Sort-Object Name
foreach ($f in $dbfs) {
    Add-Line ""
    Add-Line ("=" * 72)
    Add-Line ("TABLA: {0}" -f $f.Name)
    try {
        $fs = [System.IO.File]::OpenRead($f.FullName)
        try {
            $head = New-Object byte[] 32
            [void]$fs.Read($head, 0, 32)

            $ver       = $head[0]
            $numRec    = [BitConverter]::ToInt32($head, 4)
            $headerLen = [BitConverter]::ToUInt16($head, 8)
            $recLen    = [BitConverter]::ToUInt16($head, 10)
            $flags     = $head[28]

            $declaraCdx  = ($flags -band 0x01) -ne 0
            $declaraMemo = ($flags -band 0x02) -ne 0

            $base = [System.IO.Path]::GetFileNameWithoutExtension($f.Name)
            $cdxExiste = Test-Path (Join-Path $Carpeta "$base.cdx")
            $fptExiste = Test-Path (Join-Path $Carpeta "$base.fpt")
            $dbtExiste = Test-Path (Join-Path $Carpeta "$base.dbt")

            Add-Line ("  Version byte : 0x{0:X2}   Registros: {1}   HeaderLen: {2}   RecLen: {3}" -f $ver, $numRec, $headerLen, $recLen)
            Add-Line ("  Declara CDX  : {0,-5}  ->  archivo .cdx existe: {1}" -f $declaraCdx, $cdxExiste)
            Add-Line ("  Declara memo : {0,-5}  ->  .fpt existe: {1}  /  .dbt existe: {2}" -f $declaraMemo, $fptExiste, $dbtExiste)
            if ($declaraCdx -and -not $cdxExiste) {
                Add-Line "  *** ALERTA: la tabla ESPERA un .CDX que NO existe -> esto provoca el Error 26 en VSIAF ***"
            }

            # Leer descriptores de campos
            $fieldBytesLen = [int]$headerLen - 32
            if ($fieldBytesLen -gt 0) {
                $fbuf = New-Object byte[] $fieldBytesLen
                [void]$fs.Read($fbuf, 0, $fieldBytesLen)

                Add-Line "  CAMPOS (nombre | tipo | largo | dec):"
                $pos = 0
                while (($pos + 32) -le $fieldBytesLen -and $fbuf[$pos] -ne 0x0D) {
                    $nombre = ([System.Text.Encoding]::ASCII.GetString($fbuf, $pos, 11)).Trim([char]0).Trim()
                    $tipoCh = [char]$fbuf[$pos + 11]
                    $largo  = $fbuf[$pos + 16]
                    $dec    = $fbuf[$pos + 17]
                    $tnom   = $tipos[[string]$tipoCh]; if (-not $tnom) { $tnom = "$tipoCh" }
                    Add-Line ("    {0,-12} {1,-10} {2,4} {3,3}" -f $nombre, $tnom, $largo, $dec)
                    $pos += 32
                }
            }
        } finally {
            $fs.Close()
        }
    } catch {
        Add-Line ("  ERROR leyendo la tabla: {0}" -f $_.Exception.Message)
    }
}

[System.IO.File]::WriteAllText($Salida, $sb.ToString(), [System.Text.Encoding]::UTF8)
Write-Host "Listo. Reporte generado en:" -ForegroundColor Green
Write-Host "  $Salida"
Write-Host "Abrelo y pega el contenido aqui."
