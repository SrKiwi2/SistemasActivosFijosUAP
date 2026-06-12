<#
    Worker-Vsiaf.ps1
    ------------------------------------------------------------------
    Procesa las "ordenes" que deja el SCIAF y las INSERTA en los DBF del VSIAF
    via VFPOLEDB, que mantiene el indice .CDX automaticamente (sin reindexar).

    Carpetas (dentro de -Carpeta):
        _cola\      ordenes pendientes (*.json escritos por el SCIAF)
        _hechos\    ordenes procesadas OK
        _errores\   ordenes que fallaron (+ un .error.txt con el motivo)
        worker.log  bitacora

    Formato de cada orden (JSON, valores como TEXTO):
        { "tabla":"RESP", "op":"INSERT",
          "campos": { "ENTIDAD":"148", "CODRESP":"123", "NOMRESP":"...", ... } }

    REQUISITO: VFPOLEDB instalado. Correr con PowerShell de 32 BITS.

    USO:
        # una pasada (para Tarea Programada):
        C:\Windows\SysWOW64\WindowsPowerShell\v1.0\powershell.exe -ExecutionPolicy Bypass -File "C:\vsiaf\Worker-Vsiaf.ps1" -Carpeta "C:\vsiaf\dbfs"
        # en bucle (para pruebas):
        ... Worker-Vsiaf.ps1 -Carpeta "C:\vsiaf\dbfs" -Loop
#>
param(
    [string]$Carpeta = "C:\vsiaf\dbfs",
    [switch]$Loop
)

if ([Environment]::Is64BitProcess) {
    Write-Host "DEBE CORRER EN POWERSHELL DE 32 BITS (VFPOLEDB es 32 bits)." -ForegroundColor Red
    Write-Host ("  C:\Windows\SysWOW64\WindowsPowerShell\v1.0\powershell.exe -ExecutionPolicy Bypass -File `"{0}`" -Carpeta `"{1}`"" -f $PSCommandPath, $Carpeta)
    return
}

$colaDir    = Join-Path $Carpeta "_cola"
$hechosDir  = Join-Path $Carpeta "_hechos"
$erroresDir = Join-Path $Carpeta "_errores"
$logFile    = Join-Path $Carpeta "worker.log"
foreach ($d in @($colaDir, $hechosDir, $erroresDir)) {
    if (-not (Test-Path $d)) { New-Item -ItemType Directory -Force -Path $d | Out-Null }
}

function Log($m) {
    $line = "{0}  {1}" -f (Get-Date -Format "yyyy-MM-dd HH:mm:ss"), $m
    try { Add-Content -Path $logFile -Value $line -Encoding UTF8 } catch {}
    Write-Host $line
}

# Conjuntos de tipos ADO
$NUM  = @(2,3,4,5,6,14,16,17,18,19,20,21,131,139)   # enteros, decimales, numeric
$DATE = @(7,133,135)                                 # fecha / datetime
$BOOL = @(11)                                        # logico

function Format-Valor($raw, $adType) {
    $s = if ($null -eq $raw) { "" } else { [string]$raw }
    if ($NUM -contains $adType) {
        $t = ($s.Trim() -replace ',', '.')
        if ($t -eq "") { return "0" }
        return $t
    } elseif ($DATE -contains $adType) {
        $t = $s.Trim()
        if ($t -eq "") { return "{}" }            # fecha vacia (no NULL)
        $t = $t -replace '[/\.]', '-'             # admite 2026/06/12 o 2026.06.12
        return "{^" + $t + "}"                     # {^2026-06-12}
    } elseif ($BOOL -contains $adType) {
        if ($s.Trim().ToUpper() -in @('1','T','.T.','TRUE','SI','S','Y','VERDADERO')) { return ".T." } else { return ".F." }
    } else {
        return "'" + ($s -replace "'", "''") + "'" # texto: comillas simples, duplicando las internas
    }
}

function Procesar-Orden($conn, $archivo, $schemaCache) {
    $json  = (Get-Content -Path $archivo -Raw -Encoding UTF8) | ConvertFrom-Json
    $tabla = ([string]$json.tabla).ToUpper().Trim()
    if ([string]::IsNullOrWhiteSpace($tabla)) { throw "Orden sin 'tabla'." }
    $op = if ($json.op) { ([string]$json.op).ToUpper() } else { "INSERT" }
    if ($op -ne "INSERT") { throw "Operacion '$op' no soportada todavia (solo INSERT)." }

    # Esquema (tipos) de la tabla, cacheado
    if (-not $schemaCache.ContainsKey($tabla)) {
        $rs = New-Object -ComObject ADODB.Recordset
        $rs.Open("SELECT * FROM $tabla WHERE 1=0", $conn, 0, 1)
        $sc = @{}
        foreach ($f in $rs.Fields) { $sc[$f.Name.ToUpper()] = $f.Type }
        $rs.Close()
        $schemaCache[$tabla] = $sc
    }
    $schema = $schemaCache[$tabla]

    # Valores provistos por el SCIAF (claves en mayuscula)
    $provistos = @{}
    foreach ($prop in $json.campos.PSObject.Properties) { $provistos[$prop.Name.ToUpper()] = $prop.Value }

    # Insertamos TODAS las columnas de la tabla: usa el valor provisto, o un
    # default segun el tipo (texto vacio / 0 / fecha vacia / .F.). Asi nunca
    # falla por un campo obligatorio que el SCIAF no mando.
    $cols = @(); $vals = @()
    foreach ($col in $schema.Keys) {
        $raw = if ($provistos.ContainsKey($col)) { $provistos[$col] } else { "" }
        $cols += $col
        $vals += (Format-Valor $raw $schema[$col])
    }
    if ($cols.Count -eq 0) { throw "No se pudo leer el esquema de $tabla." }

    $sql = "INSERT INTO $tabla (" + ($cols -join ", ") + ") VALUES (" + ($vals -join ", ") + ")"
    $conn.Execute($sql) | Out-Null
    return $sql
}

do {
    $archivos = @(Get-ChildItem -Path $colaDir -Filter "*.json" -File -ErrorAction SilentlyContinue | Sort-Object Name)
    if ($archivos.Count -gt 0) {
        $conn = $null
        try {
            $conn = New-Object -ComObject ADODB.Connection
            $conn.Open("Provider=VFPOLEDB.1;Data Source=$Carpeta;Collating Sequence=machine;")
            $schemaCache = @{}
            foreach ($a in $archivos) {
                try {
                    $sql = Procesar-Orden $conn $a.FullName $schemaCache
                    Move-Item $a.FullName -Destination (Join-Path $hechosDir $a.Name) -Force
                    Log ("OK    {0}  ->  {1}" -f $a.Name, $sql)
                } catch {
                    $err = $_.Exception.Message
                    try { Move-Item $a.FullName -Destination (Join-Path $erroresDir $a.Name) -Force } catch {}
                    try { Set-Content -Path (Join-Path $erroresDir ($a.BaseName + ".error.txt")) -Value $err -Encoding UTF8 } catch {}
                    Log ("ERROR {0}  ->  {1}" -f $a.Name, $err)
                }
            }
        } catch {
            Log ("FATAL conexion VFPOLEDB: {0}" -f $_.Exception.Message)
        } finally {
            if ($conn -ne $null -and $conn.State -eq 1) { $conn.Close() }
        }
    }
    if ($Loop) { Start-Sleep -Seconds 5 }
} while ($Loop)
