*==============================================================================
* reindexa.prg  --  Reindexa automaticamente las tablas del VSIAF que el SCIAF
*                   marco como pendientes (porque les escribio registros).
*
* COMO COMPILARLO (una sola vez, con Visual FoxPro 9 IDE):
*     BUILD EXE reindexa FROM reindexa
*   (genera reindexa.exe, que corre con el runtime VFP9 ya instalado: VFP9r.dll)
*
* COMO EJECUTARLO:
*     reindexa.exe "C:\vsiaf\vsiaf_red\dbfs"
*   (si no se pasa carpeta, usa la de abajo por defecto)
*
* Lo dispara la Tarea Programada de Windows cada 1 minuto. Si no hay marcas,
* termina al instante. Reindexa en modo EXCLUSIVO; si la tabla esta ocupada,
* deja la marca para el proximo ciclo. NO corrompe datos: solo reconstruye el
* indice .CDX desde el .DBF usando los tags que ya estan definidos.
*==============================================================================
LPARAMETERS tcCarpeta

IF EMPTY(tcCarpeta)
    tcCarpeta = "C:\vsiaf\vsiaf_red\dbfs"
ENDIF
tcCarpeta = ADDBS(tcCarpeta)

LOCAL lcFlagDir
lcFlagDir = tcCarpeta + "_reindex\"

* Si no existe la carpeta de marcas, no hay nada que hacer
IF NOT DIRECTORY(lcFlagDir)
    RETURN
ENDIF

* --- Entorno seguro para reindexar sin GUI ---
SET EXCLUSIVE ON
SET SAFETY OFF
SET TALK OFF
SET COLLATE TO "MACHINE"        && misma secuencia que usa el VSIAF (FoxPro 2.x)
SET REPROCESS TO 3 SECONDS      && si hay lock, reintenta hasta 3s y sigue
ON ERROR *                      && neutraliza el manejador del VSIAF (errlog) aqui

LOCAL ARRAY laFlags[1]
LOCAL lnN, i, lcArch, lcTabla, lcDbf, lcFlag
lnN = ADIR(laFlags, lcFlagDir + "*.todo")

FOR i = 1 TO lnN
    lcArch  = laFlags[i, 1]
    lcTabla = UPPER(JUSTSTEM(lcArch))          && RESP.todo -> RESP
    lcDbf   = tcCarpeta + lcTabla + ".DBF"
    lcFlag  = lcFlagDir + lcArch

    IF NOT FILE(lcDbf)
        ERASE (lcFlag)                          && tabla inexistente: descartar marca
        LOOP
    ENDIF

    LOCAL llOk
    llOk = .F.
    TRY
        IF USED("xreidx")
            USE IN xreidx
        ENDIF
        USE (lcDbf) EXCLUSIVE ALIAS xreidx
        REINDEX                                 && reconstruye TODOS los tags del .CDX
        USE IN xreidx
        llOk = .T.
    CATCH
        * Tabla ocupada u otro problema: dejamos la marca para el proximo ciclo
        IF USED("xreidx")
            USE IN xreidx
        ENDIF
    ENDTRY

    IF llOk
        ERASE (lcFlag)                          && reindex OK -> quitar la marca
    ENDIF
ENDFOR

RETURN
