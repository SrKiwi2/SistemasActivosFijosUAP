<#
    Inspeccion-Vsiaf.ps1
    ------------------------------------------------------------------
    Vuelca TODA la carpeta de instalacion del VSIAF para diagnostico, en archivos
    de texto listos para enviar. NO modifica nada (solo lectura).

    Genera, en una carpeta de salida (por defecto en el Escritorio: reporte_vsiaf):
      1_inventario_y_dbf.txt  -> arbol recursivo de archivos, resumen por extension,
                                 y estructura de cada .DBF (campos, flags CDX/memo,
                                 y si los .cdx/.fpt existen). Marca el Error 26.
      2_fuentes_texto.txt     -> contenido de los archivos de texto/fuente
                                 (.prg .h .ini .txt .cfg .config .json .xml .sql
                                  .qpr .mpr .spr .bat .cmd .log), cada uno con cabecera.

    Los binarios (.exe .app .fxp .dll .dbf .cdx .fpt .dbt .frx .scx .vcx .mnx
    .pjx imagenes, etc.) NO se vuelcan: solo aparecen en el inventario.

    USO (en la VM donde esta instalado el VSIAF):
      powershell -ExecutionPolicy Bypass -File .\Inspeccion-Vsiaf.ps1 -Raiz "C:\vsiaf"
#>
param(
    [string]$Raiz       = "C:\vsiaf",
    [string]$Salida     = "$env:USERPROFILE\Desktop\reporte_vsiaf",
    [int]   $MaxKbTexto = 500
)

if (-not (Test-Path $Raiz)) {
    Write-Host "ERROR: no existe la carpeta '$Raiz'." -ForegroundColor Red
    return
}
New-Item -ItemType Directory -Force -Path $Salida | Out-Null

$ansi  = [System.Text.Encoding]::GetEncoding(1252)
$tipos = @{
    'C'='Character';'N'='Numeric';'F'='Float';'D'='Date';'L'='Logical';'M'='Memo';
    'I'='Integer';'T'='DateTime';'Y'='Currency';'B'='Double';'G'='General';'P'='Picture';
    'Q'='Varbinary';'V'='Varchar'
}
# Extensiones que SI se vuelcan como texto
$extTexto = @('.prg','.h','.ini','.txt','.cfg','.config','.json','.xml','.sql',
              '.qpr','.mpr','.spr','.bat','.cmd','.log','.csv','.me','.lst')

$todos = Get-ChildItem -Path $Raiz -Recurse -File -ErrorAction SilentlyContinue

# ── 1. INVENTARIO + DBF ───────────────────────────────────────────────────
$inv = New-Object System.Text.StringBuilder
function AddI($t=""){ [void]$inv.AppendLine($t) }

AddI ("INVENTARIO VSIAF  -  Raiz: {0}" -f $Raiz)
AddI ("Generado: {0}" -f (Get-Date))
AddI ("=" * 76)

AddI ""
AddI "RESUMEN POR EXTENSION:"
$todos | Group-Object Extension | Sort-Object Count -Descending | ForEach-Object {
    $sz = ($_.Group | Measure-Object Length -Sum).Sum
    AddI ("  {0,-10} {1,5} archivos   {2,14:N0} bytes" -f ($_.Name.ToLower()), $_.Count, $sz)
}

AddI ""
AddI "ARBOL DE ARCHIVOS (ruta relativa | tamano | fecha):"
$todos | Sort-Object FullName | ForEach-Object {
    $rel = $_.FullName.Substring($Raiz.Length).TrimStart('\')
    AddI ("  {0,-50} {1,12:N0}  {2}" -f $rel, $_.Length, $_.LastWriteTime)
}

# Estructura de cada .DBF (lectura de cabecera, recursivo)
$dbfs = $todos | Where-Object { $_.Extension -ieq '.dbf' } | Sort-Object FullName
foreach ($f in $dbfs) {
    $rel = $f.FullName.Substring($Raiz.Length).TrimStart('\')
    AddI ""
    AddI ("=" * 76)
    AddI ("TABLA: {0}" -f $rel)
    try {
        $fs = [System.IO.File]::OpenRead($f.FullName)
        try {
            $head = New-Object byte[] 32
            [void]$fs.Read($head,0,32)
            $ver=$head[0]; $numRec=[BitConverter]::ToInt32($head,4)
            $headerLen=[BitConverter]::ToUInt16($head,8); $recLen=[BitConverter]::ToUInt16($head,10)
            $flags=$head[28]
            $declaraCdx=($flags -band 0x01)-ne 0; $declaraMemo=($flags -band 0x02)-ne 0
            $dir=$f.DirectoryName; $base=[System.IO.Path]::GetFileNameWithoutExtension($f.Name)
            $cdxExiste=Test-Path (Join-Path $dir "$base.cdx")
            $fptExiste=Test-Path (Join-Path $dir "$base.fpt")
            $dbtExiste=Test-Path (Join-Path $dir "$base.dbt")
            AddI ("  Version: 0x{0:X2}   Registros: {1}   HeaderLen: {2}   RecLen: {3}" -f $ver,$numRec,$headerLen,$recLen)
            AddI ("  Declara CDX : {0,-5} -> .cdx existe: {1}" -f $declaraCdx,$cdxExiste)
            AddI ("  Declara memo: {0,-5} -> .fpt existe: {1} / .dbt existe: {2}" -f $declaraMemo,$fptExiste,$dbtExiste)
            if ($declaraCdx -and -not $cdxExiste) {
                AddI "  *** ALERTA: espera un .CDX que NO existe -> Error 26 en VSIAF ***"
            }
            $flen=[int]$headerLen-32
            if ($flen -gt 0) {
                $fb=New-Object byte[] $flen; [void]$fs.Read($fb,0,$flen)
                AddI "  CAMPOS (nombre | tipo | largo | dec):"
                $p=0
                while (($p+32)-le $flen -and $fb[$p]-ne 0x0D) {
                    $nom=([System.Text.Encoding]::ASCII.GetString($fb,$p,11)).Trim([char]0).Trim()
                    $tc=[char]$fb[$p+11]; $lg=$fb[$p+16]; $dc=$fb[$p+17]
                    $tn=$tipos[[string]$tc]; if(-not $tn){$tn="$tc"}
                    AddI ("    {0,-12} {1,-10} {2,4} {3,3}" -f $nom,$tn,$lg,$dc)
                    $p+=32
                }
            }
        } finally { $fs.Close() }
    } catch { AddI ("  ERROR leyendo: {0}" -f $_.Exception.Message) }
}

[System.IO.File]::WriteAllText((Join-Path $Salida "1_inventario_y_dbf.txt"), $inv.ToString(), [System.Text.Encoding]::UTF8)

# ── 2. CONTENIDO DE ARCHIVOS DE TEXTO/FUENTE ──────────────────────────────
$src = New-Object System.Text.StringBuilder
function AddS($t=""){ [void]$src.AppendLine($t) }
AddS ("FUENTES DE TEXTO VSIAF  -  Raiz: {0}" -f $Raiz)
AddS ("Generado: {0}" -f (Get-Date))

$textos = $todos | Where-Object { $extTexto -contains $_.Extension.ToLower() } | Sort-Object FullName
foreach ($f in $textos) {
    $rel = $f.FullName.Substring($Raiz.Length).TrimStart('\')
    AddS ""
    AddS ("=" * 76)
    AddS ("ARCHIVO: {0}   ({1:N0} bytes)" -f $rel, $f.Length)
    AddS ("-" * 76)
    if ($f.Length -gt ($MaxKbTexto * 1024)) {
        AddS ("  [OMITIDO: supera {0} KB; pidemelo aparte si lo necesitas]" -f $MaxKbTexto)
        continue
    }
    try {
        $txt = [System.IO.File]::ReadAllText($f.FullName, $ansi)
        AddS $txt
    } catch { AddS ("  [ERROR leyendo: {0}]" -f $_.Exception.Message) }
}

[System.IO.File]::WriteAllText((Join-Path $Salida "2_fuentes_texto.txt"), $src.ToString(), [System.Text.Encoding]::UTF8)

# ── Resumen en pantalla ───────────────────────────────────────────────────
$invKb = [math]::Round((Get-Item (Join-Path $Salida "1_inventario_y_dbf.txt")).Length/1KB,1)
$srcKb = [math]::Round((Get-Item (Join-Path $Salida "2_fuentes_texto.txt")).Length/1KB,1)
Write-Host "Listo. Reportes generados en: $Salida" -ForegroundColor Green
Write-Host ("  1_inventario_y_dbf.txt   ({0} KB)  <- enviame ESTE primero" -f $invKb)
Write-Host ("  2_fuentes_texto.txt      ({0} KB)" -f $srcKb)
Write-Host "Si pesan mucho para pegar, adjuntalos como archivo."
