# Traspaso — Custodia de faltantes

> Estado al 28-sep-2026. Diseño completo: anexo "Custodia de faltantes" en
> [`PLAN_CONTROL_ACTIVOS.md`](PLAN_CONTROL_ACTIVOS.md). Este archivo dice **dónde quedamos
> y qué sigue**, para retomar en otra máquina u otra sesión sin el historial del chat.

## El requerimiento

Un responsable tiene 10 activos y al cierre de gestión faltan 2. Esos 2 no pueden figurar
como devueltos ni disponibles: un responsable nuevo no debe recibir bienes perdidos.

En el VSIAF esto se resolvía a mano: se creaba una oficina y un responsable ficticio, y se
le transferían los faltantes hasta aclarar el caso. Cuando todo se resolvía, ese responsable
quedaba vacío. El SCIAF va a reproducir esa práctica de forma controlada.

## Decisiones tomadas con el usuario

| # | Decisión |
|---|---|
| 1 | Custodias **nuevas** (no se reutilizan las viejas del VSIAF). Los faltantes históricos se moverán a mano más adelante |
| 2 | **Una custodia genérica por predio** (oficina + responsable `CUSTODIA DE FALTANTES`). Se descartó una por persona ("JUAN – FALTANTES"): llenaría el VSIAF de ficticios. En el VSIAF la custodia solo **separa**; de quién es cada faltante se sabe en el SCIAF |
| 3 | Por predio, porque en el VSIAF oficina/responsable/auxiliar dependen de la UNIDAD: el envío es una **transferencia interna** y no cambia CODAUX |
| 4 | Un bien en custodia **no se asigna, transfiere, traslada ni separa**. Solo se consulta y se resuelve |
| 5 | El envío a custodia es **manual** (lo confirma un administrador), nunca automático al cerrar |
| 6 | **Dos puertas de entrada:** cierre de levantamiento y **registro directo** (se seleccionan bienes del responsable, con documento de respaldo y la casilla "Enviar a custodia ahora") |
| 7 | **En consulta con los encargados, NO implementar:** que un responsable con faltantes abiertos no reciba bienes nuevos |
| 8 | **Pendiente:** escribir en `OBSERV` de ACTUAL. Es un campo MEMO; el worker lo escribe, pero JavaDBF lo lee como `"(memo)"` y no se puede verificar desde el SCIAF |

## Proceso operativo acordado

0. **Preparación (una vez por predio):** alta de la oficina y el responsable de custodia.
1. **Identificación:** levantamiento de la oficina (web o APK). Se marca lo encontrado; lo
   no marcado queda como faltante al cerrar. Antes de cerrar hay que buscar razonablemente
   (preguntar al responsable, oficinas vecinas, mantenimiento o préstamo, transferencias en
   curso). Al cerrar, el faltante queda **ABIERTO** e imputado al responsable. **ABIERTO no
   toca el VSIAF.**
2. **Aclaración:** el faltante queda ABIERTO un plazo que definen los encargados. Si aparece,
   se resuelve como APARECIO y nunca va a custodia.
3. **Envío a custodia (manual):** desde Faltantes, o desde el registro directo. Hace una
   transferencia interna a la custodia del predio por la cola. Cuando el worker confirma, el
   faltante pasa a **EN_CUSTODIA** y se emite una constancia PDF. En la devolución de fin de
   gestión, el acta lleva solo lo que el responsable entregó.
4. **En custodia:** todo movimiento del bien está bloqueado.
5. **Resolución:** si apareció, se transfiere desde la custodia; si hubo reposición o
   justificación, se registra el documento; si va a baja, queda pendiente hasta que la cola
   soporte bajas. La custodia vacía significa que el predio está aclarado.

Ciclo del hallazgo: `ABIERTO` → `EN_CUSTODIA` → `RESUELTO`. "Pendiente" = ABIERTO o EN_CUSTODIA.

## Estado de las fases

| Fase | Estado |
|---|---|
| **1 · Datos** | ✅ Código en `main` (commit `parteIFaltantes`). **Falta aplicar el SQL** (ver abajo) |
| 2 · Alta de custodias por predio | ⏳ Siguiente. Bloqueada por dos datos del usuario (abajo) |
| 3 · Registro directo + envío a custodia + constancia PDF | — |
| 4 · Bloqueos (asignación, transferencia masiva, traslado/separación; no abrir levantamiento en la oficina de custodia) | — |
| 5 · Resolución (sacar de custodia) | — |
| 6 · Conciliación: alerta si un bien EN_CUSTODIA ya no está a cargo de la custodia | — |

### Qué hizo la fase 1

- `oficina.es_custodia` y `responsable.es_custodia` (boolean, default false). La sync desde
  el DBF actualiza sin recrear, así que no borra la marca.
- `hallazgo_inventario`: `id_inventario` admite vacío (faltante directo); campos nuevos
  `origen` (LEVANTAMIENTO/DIRECTO), `id_oficina_origen`, `documento_respaldo`,
  `fecha_documento`, `id_responsable_custodia`, `fecha_envio_custodia`, `usuario_envio_custodia`.
- `ControlActivosService`: constantes `EN_CUSTODIA`, `PENDIENTES`, `ORIGEN_*`; no crea un
  segundo faltante pendiente del mismo bien.
- `ControlActivosRepo`: la oficina del hallazgo sale de
  `coalesce(h.id_oficina_origen, i.id_oficina)` con `left join inventario`. Así funciona antes
  y después del script. Los conteos de faltantes incluyen EN_CUSTODIA.
- `faltantes.html`: filtro "En custodia" y la etiqueta "Registro directo".

### Aplicar la fase 1 en la base

1. Desplegar y arrancar la app una vez (hbm2ddl crea las columnas).
2. Correr `scripts/sql/custodia_faltantes_fase1.sql` con el usuario **`postgres`**: el
   usuario de la app no es dueño de las tablas y el DDL falla.
3. La última consulta debe dar `0 | 0 | 0 | YES`. Si el paso 5 avisa duplicados, resolverlos
   desde la vista Faltantes y volver a correr.

## Lo que falta para arrancar la fase 2

Preguntar al usuario:

- **El CI genérico** del responsable de custodia en el VSIAF (un valor fijo que el VSIAF acepte).
- **El cargo.** Propuesta: `CUSTODIA – ACTIVOS FIJOS`.

Idea para la fase 2: reutilizar el alta normal de oficina y responsable (ya encola INSERT de
OFICINA y RESP al worker) con `es_custodia = true`, una sola vez por predio, y marcarla
distinta en el mapa de Control de Activos.

## Trampas conocidas

- **Compilación:** `mvnw -q compile` con salida vacía **no** garantiza éxito. Verificar sin
  `-q` buscando `BUILD SUCCESS`.
- **No probar el envío a la cola desde Windows local:** `/mnt/dbfwin/_cola` no existe, el
  encolado falla y marca los activos con `sinc_vsiaf = ERROR` en datos reales.
- **Probar en otro puerto** para no chocar con la instancia del 9696:
  `mvnw.cmd -o spring-boot:run "-Dspring-boot.run.arguments=--server.port=9797"`.
- **`application.properties`** tiene cambios locales que no se commitean a propósito
  (`legacy.dbf.write.mode`, datasource). No hacer `git add -A`.

## Para retomar con Claude en otra máquina

Abrir el proyecto y decirle:

> Lee `docs/HANDOFF_CUSTODIA_FALTANTES.md` y el anexo de custodia en
> `docs/PLAN_CONTROL_ACTIVOS.md`. Continuamos con la fase 2. El CI genérico es … y el cargo es …
