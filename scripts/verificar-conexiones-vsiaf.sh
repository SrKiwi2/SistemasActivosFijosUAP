#!/usr/bin/env bash
# =============================================================================
#  Diagnóstico de los montajes CIFS del VSIAF — se ejecuta en la VM Ubuntu.
#
#  Responde la pregunta "¿hay comunicación con los DBF o no?" a nivel sistema,
#  sin depender de que SCIAF esté levantado.
#
#  Uso:
#     ./verificar-conexiones-vsiaf.sh          # informe legible
#     ./verificar-conexiones-vsiaf.sh --quiet  # solo el código de salida
#
#  Códigos de salida:  0 = todo OK   1 = advertencias   2 = alguna caída
#
#  Sirve también para cron/systemd:
#     */5 * * * * /opt/sciaf/verificar-conexiones-vsiaf.sh --quiet || \
#                 logger -t sciaf "montaje VSIAF caído"
# =============================================================================
set -uo pipefail

MONTAJES=(
    "/mnt/dbfwin|ACTUAL.DBF|DBF maestros"
    "/mnt/vsiaf_transferencias|sol_transferencias.DBF|Transferencias"
)

# Cuánto esperamos a una operación de disco antes de declarar el montaje colgado.
TIMEOUT=5

QUIET=0
[[ "${1:-}" == "--quiet" ]] && QUIET=1

if [[ -t 1 && $QUIET -eq 0 ]]; then
    ROJO=$'\e[31m'; VERDE=$'\e[32m'; AMBAR=$'\e[33m'; GRIS=$'\e[90m'; FIN=$'\e[0m'
else
    ROJO=''; VERDE=''; AMBAR=''; GRIS=''; FIN=''
fi

decir() { [[ $QUIET -eq 0 ]] && echo -e "$@"; return 0; }

SALIDA=0
peor() { (( $1 > SALIDA )) && SALIDA=$1; return 0; }

decir "${GRIS}Verificación de conexiones VSIAF — $(date '+%d/%m/%Y %H:%M:%S')${FIN}\n"

for entrada in "${MONTAJES[@]}"; do
    IFS='|' read -r RUTA CENTINELA ETIQUETA <<< "$entrada"

    decir "${GRIS}────────────────────────────────────────────────${FIN}"
    decir "  ${ETIQUETA}  ${GRIS}(${RUTA})${FIN}"

    # 1. ¿Existe el punto de montaje?
    if [[ ! -d "$RUTA" ]]; then
        decir "  ${ROJO}✗ CAÍDO${FIN} — la carpeta no existe."
        decir "    ${GRIS}sudo mkdir -p $RUTA${FIN}"
        peor 2; continue
    fi

    # 2. ¿Está realmente montado?  Esta es la trampa: si el mount se cae, la
    #    carpeta local sigue ahí (vacía) y todo "parece" funcionar.
    if ! mountpoint -q "$RUTA" 2>/dev/null; then
        decir "  ${ROJO}✗ CAÍDO${FIN} — la carpeta existe pero NO hay montaje activo."
        decir "    ${GRIS}SCIAF no está leyendo nada del VSIAF. Volver a montar.${FIN}"
        peor 2; continue
    fi

    ORIGEN=$(findmnt -n -o SOURCE --target "$RUTA" 2>/dev/null)
    TIPO=$(findmnt -n -o FSTYPE --target "$RUTA" 2>/dev/null)
    decir "    ${GRIS}origen: ${ORIGEN} (${TIPO})${FIN}"

    # 3. ¿Responde?  Un share colgado bloquea el ls durante minutos, por eso
    #    va con timeout en vez de esperar indefinidamente.
    if ! timeout "$TIMEOUT" ls "$RUTA" >/dev/null 2>&1; then
        decir "  ${ROJO}✗ CAÍDO${FIN} — montado pero sin respuesta en ${TIMEOUT}s (montaje zombie)."
        decir "    ${GRIS}sudo umount -l $RUTA  &&  volver a montar${FIN}"
        peor 2; continue
    fi

    # 4. ¿Se ven los archivos?
    N_DBF=$(timeout "$TIMEOUT" bash -c "ls -1 '$RUTA' 2>/dev/null | grep -ci '\.dbf$'" || echo 0)
    if [[ "$N_DBF" -eq 0 ]]; then
        decir "  ${AMBAR}! ADVERTENCIA${FIN} — montado pero sin ningún archivo .DBF a la vista."
        peor 1; continue
    fi

    # 5. ¿Se puede LEER contenido?  Que el nombre aparezca en el listado no
    #    garantiza que los bytes lleguen.
    ARCHIVO=$(timeout "$TIMEOUT" bash -c "ls -1 '$RUTA' 2>/dev/null | grep -i '^${CENTINELA}$' | head -1")
    if [[ -z "$ARCHIVO" ]]; then
        decir "  ${AMBAR}! ADVERTENCIA${FIN} — ${N_DBF} archivos .DBF pero falta ${CENTINELA}."
        peor 1; continue
    fi

    if ! timeout "$TIMEOUT" head -c 32 "$RUTA/$ARCHIVO" >/dev/null 2>&1; then
        decir "  ${ROJO}✗ CAÍDO${FIN} — ${CENTINELA} está pero no se puede leer."
        peor 2; continue
    fi

    TAM=$(timeout "$TIMEOUT" stat -c %s "$RUTA/$ARCHIVO" 2>/dev/null || echo 0)
    MOD=$(timeout "$TIMEOUT" stat -c %y "$RUTA/$ARCHIVO" 2>/dev/null | cut -d. -f1)

    # 6. ¿Se puede ESCRIBIR?  SCIAF necesita dejar órdenes en _cola/.
    if timeout "$TIMEOUT" touch "$RUTA/.sciaf_prueba_escritura" 2>/dev/null; then
        rm -f "$RUTA/.sciaf_prueba_escritura" 2>/dev/null
        ESCRITURA="lectura/escritura"
    else
        ESCRITURA="${AMBAR}SOLO LECTURA${FIN}"
        peor 1
    fi

    decir "  ${VERDE}✓ OK${FIN} — ${N_DBF} archivos .DBF · ${CENTINELA} $((TAM/1024)) KB · ${ESCRITURA}"
    decir "    ${GRIS}último cambio: ${MOD}${FIN}"
done

decir "\n${GRIS}────────────────────────────────────────────────${FIN}"
case $SALIDA in
    0) decir "${VERDE}Todas las conexiones responden.${FIN}" ;;
    1) decir "${AMBAR}Hay advertencias: revisar el detalle de arriba.${FIN}" ;;
    2) decir "${ROJO}Hay al menos una conexión caída.${FIN}"
       decir "${GRIS}Recordatorio: //172.16.21.4 usa el usuario Activo8 (no Admin).${FIN}" ;;
esac

exit $SALIDA
