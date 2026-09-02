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
    # Carpeta de los DBF: es el Data Source de VFPOLEDB.
    [string]$Carpeta = "C:\vsiaf\dbfs",
    # Carpeta donde el SCIAF deja _cola (y donde se escriben _hechos, _errores y
    # worker.log). Por omision, la misma de los DBF. Se separa cuando esa carpeta
    # esta bajo respaldo y no debe contener archivos de trabajo; tiene que coincidir
    # con legacy.dbf.cola.path del SCIAF.
    [string]$Cola = "",
    [switch]$Loop
)

if ([Environment]::Is64BitProcess) {
    Write-Host "DEBE CORRER EN POWERSHELL DE 32 BITS (VFPOLEDB es 32 bits)." -ForegroundColor Red
    Write-Host ("  C:\Windows\SysWOW64\WindowsPowerShell\v1.0\powershell.exe -ExecutionPolicy Bypass -File `"{0}`" -Carpeta `"{1}`"" -f $PSCommandPath, $Carpeta)
    return
}

if ([string]::IsNullOrWhiteSpace($Cola)) { $Cola = $Carpeta }

$colaDir    = Join-Path $Cola "_cola"
$hechosDir  = Join-Path $Cola "_hechos"
$erroresDir = Join-Path $Cola "_errores"
$logFile    = Join-Path $Cola "worker.log"
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

# Columnas clave por tabla (para "insertar solo si no existe", chequeo por indice .CDX)
$CLAVES = @{
    "RESP"     = @("ENTIDAD","UNIDAD","CODOFIC","CODRESP")
    "OFICINA"  = @("ENTIDAD","UNIDAD","CODOFIC")
    "ACTUAL"   = @("CODIGO")
    "AUXILIAR" = @("ENTIDAD","UNIDAD","CODCONT","CODAUX")
}

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
    if ($op -ne "INSERT" -and $op -ne "UPDATE") { throw "Operacion '$op' no soportada (solo INSERT/UPDATE)." }

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

    if ($op -eq "INSERT") {
        # "Insertar solo si no existe": chequeo rapido por clave usando el indice .CDX
        # (SELECT COUNT con WHERE indexado = instantaneo). Reemplaza el escaneo lento
        # que hacia el SCIAF sobre CIFS.
        $keyCols = $CLAVES[$tabla]
        if ($keyCols) {
            $kw = @()
            foreach ($kc in $keyCols) {
                if ($provistos.ContainsKey($kc) -and $schema.ContainsKey($kc)) {
                    $kw += ("$kc = " + (Format-Valor $provistos[$kc] $schema[$kc]))
                }
            }
            if ($kw.Count -eq $keyCols.Count) {
                $rsc = $conn.Execute("SELECT COUNT(*) FROM $tabla WHERE " + ($kw -join " AND "))
                $existe = (-not $rsc.EOF) -and ([int]$rsc.Fields.Item(0).Value -gt 0)
                $rsc.Close()
                if ($existe) { return "OMITIDO (ya existe en $tabla)" }
            }
        }

        # Insertamos TODAS las columnas: valor provisto o default por tipo (texto vacio /
        # 0 / fecha vacia / .F.). Asi nunca falla por un campo obligatorio que no se mando.
        $cols = @(); $vals = @()
        foreach ($col in $schema.Keys) {
            $raw = if ($provistos.ContainsKey($col)) { $provistos[$col] } else { "" }
            $cols += $col
            $vals += (Format-Valor $raw $schema[$col])
        }
        if ($cols.Count -eq 0) { throw "No se pudo leer el esquema de $tabla." }
        $sql = "INSERT INTO $tabla (" + ($cols -join ", ") + ") VALUES (" + ($vals -join ", ") + ")"
    }
    else {
        # UPDATE: SET con los campos provistos (menos las columnas clave), WHERE con la clave
        $clave = @{}
        if ($json.clave) { foreach ($p in $json.clave.PSObject.Properties) { $clave[$p.Name.ToUpper()] = $p.Value } }
        if ($clave.Count -eq 0) { throw "Orden UPDATE sin 'clave' (no se puede armar el WHERE)." }

        # SET con todos los campos provistos (incluida la clave con su valor NUEVO,
        # para soportar ediciones que cambian la clave). El WHERE usa la clave ORIGINAL.
        $sets = @()
        foreach ($col in $provistos.Keys) {
            if (-not $schema.ContainsKey($col)) { continue }
            $sets += ("$col = " + (Format-Valor $provistos[$col] $schema[$col]))
        }
        if ($sets.Count -eq 0) { throw "Orden UPDATE sin campos para actualizar." }

        $wheres = @()
        foreach ($col in $clave.Keys) {
            if (-not $schema.ContainsKey($col)) { continue }
            $wheres += ("$col = " + (Format-Valor $clave[$col] $schema[$col]))
        }
        if ($wheres.Count -eq 0) { throw "Orden UPDATE con clave invalida." }

        $sql = "UPDATE $tabla SET " + ($sets -join ", ") + " WHERE " + ($wheres -join " AND ")
    }

    Ejecutar-ConReintentos $conn $sql
    return $sql
}

# Reintenta los errores de bloqueo del DBF. VFP no deja escribir un registro que otro
# proceso tiene abierto: si en ese instante alguien esta parado sobre esa fila en el
# VSIAF, el Execute falla con "Record is not locked" o "File is in use". Sin reintento
# la orden se daba por rechazada para siempre y el cambio no llegaba nunca, aunque un
# segundo despues la fila ya estuviera libre.
function Ejecutar-ConReintentos($conn, $sql) {
    $intentos = 4
    for ($i = 1; $i -le $intentos; $i++) {
        try {
            $conn.Execute($sql) | Out-Null
            if ($i -gt 1) { Log ("REINTENTO OK (intento {0})  ->  {1}" -f $i, $sql) }
            return
        } catch {
            $msg = $_.Exception.Message
            $esBloqueo = ($msg -match "not locked|is in use|locked by another|bloque")
            if (-not $esBloqueo -or $i -eq $intentos) { throw }
            Log ("BLOQUEADO (intento {0}/{1}): {2}" -f $i, $intentos, $msg)
            Start-Sleep -Seconds (2 * $i)
        }
    }
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
    if ($Loop) { Start-Sleep -Seconds 2 }
} while ($Loop)
