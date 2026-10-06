# Traspaso — Custodia de faltantes

> Estado al 28-sep-2026 (tarde). Diseño resumido: anexo "Custodia de faltantes" en
> [`PLAN_CONTROL_ACTIVOS.md`](PLAN_CONTROL_ACTIVOS.md). Este archivo dice **dónde quedamos
> y qué sigue**, para retomar en otra máquina u otra sesión sin el historial del chat.

## El requerimiento

Un responsable tiene 10 activos y al cierre de gestión faltan 2. Esos 2 no pueden figurar
como devueltos ni disponibles: un responsable nuevo no debe recibir bienes perdidos.

En el VSIAF esto ya se hace a mano: en el predio hay una oficina de faltantes, dentro se
da de alta a **la misma persona** que tiene el faltante (su nombre, CI y cargo reales) y se
le transfieren los bienes. El SCIAF va a reproducir esa práctica de forma controlada, y a
mostrar con detalle de quién es cada faltante, de qué predio y de qué oficina salió.

Respuesta de los encargados (ING. Saul, 28-sep-2026): se crea la oficina, en ella los
responsables, cada responsable tiene sus activos; los faltantes vienen de distintas
oficinas y el SCIAF debe mostrar de cuáles; y el documento se imprime desde el SCIAF para
firmarlo.

## Decisiones tomadas con el usuario

| # | Decisión |
|---|---|
| 1 | **Una oficina de faltantes por predio**, y dentro **un responsable por persona con faltantes** (opción B). Se descartó el responsable genérico `CUSTODIA DE FALTANTES` |
| 2 | El responsable de custodia es **la misma persona**: mismo `id_persona`, mismo CI y su **cargo real**. No hay CI genérico |
| 3 | Una persona con faltantes en dos predios tiene un responsable de custodia en **cada** predio. En el SCIAF se unen por la persona (CI) |
| 4 | El envío es una **transferencia interna** dentro del predio: no cambia CODAUX |
| 5 | **Se reutilizan las 3 oficinas de faltantes que ya existen** (ver abajo). En los demás predios la oficina se **crea sola**, con confirmación, la primera vez que se registra un faltante ahí |
| 6 | Oficina nueva: código **correlativo** del predio (`max(cod_ofi) + 1`) y nombre `FALTANTES <UNIDAD>` (el patrón de CULP y CUSP) |
| 7 | El responsable de custodia también se **crea solo** la primera vez que esa persona tiene un faltante en ese predio; si ya está en la oficina de faltantes, se reutiliza |
| 8 | Un bien en custodia **no se asigna, transfiere, traslada ni separa**. Solo se consulta y se resuelve |
| 9 | El envío es **manual**: se seleccionan los bienes y se confirma. Nunca es automático al cerrar un levantamiento |
| 10 | Dos puertas de entrada: **cierre de levantamiento** y **registro directo** desde la vista del responsable |
| 11 | **El acta se genera al registrar** el envío. Se descarga, se imprime y se firma. Una acta **por persona** |
| 12 | El acta lleva **QR de verificación** que apunta a `https://sciaf.uap.edu.bo` |
| 13 | **En consulta con los encargados, NO implementar:** que un responsable con faltantes abiertos no reciba bienes nuevos |
| 14 | **Pendiente:** escribir en `OBSERV` de ACTUAL (campo MEMO; JavaDBF lo lee como `"(memo)"`) |

## Oficinas de faltantes existentes (verificado en `bd_a3` el 28-sep-2026)

| Predio | `id_oficina` | CODOFI | Nombre | Responsables | Activos |
|---|---|---|---|---|---|
| CULP | 858 | 372 | FALTANTES CULP | 3 (1 con 55 bienes) | 55 |
| CUSP | 873 | 194 | FALTANTES CUSP | 2 | 6 |
| POST | 749 | 42 | FALTANTES EDIFICIO POSGRADO | 2 | 1 |

Ninguna tiene todavía `es_custodia = true`. Sus responsables ya son personas reales con CI y
cargo reales, o sea que la práctica del VSIAF coincide con la opción B. POST conserva su
nombre (no se renombra en el VSIAF).

En la base, cada persona tiene una fila de `responsable` **por oficina** (343 personas en
más de una oficina, 112 en más de un predio, hasta 75 filas para una sola persona). Todas
las filas tienen `id_persona`. Por eso "de quién" se resuelve por **persona**, no por fila
de responsable.

## El acta de faltantes

- Una acta por persona. Si la selección abarca a varias personas, se genera una por cada una.
- Número correlativo `AF-<año>-<nnnnnn>`.
- Cabecera con la persona (nombre, CI), la fecha, quién la registró y el documento de respaldo.
- Tabla agrupada por **predio → oficina de origen**, con código y descripción, más
  subtotales y total.
- Pie de firmas: **a la izquierda** la persona (línea, nombre completo, cargo y CI si lo
  tiene); **a la derecha** Activos Fijos, en forma genérica (firma y sello), y sus datos
  se ajustan en un solo lugar cuando se definan.
- QR con `https://sciaf.uap.edu.bo/verificar/acta-faltantes/<token>`. Es una página pública
  que muestra lo que se emitió y su estado: `VIGENTE`, `ANULADA` o `RESUELTA`.
- Se guarda al emitir: la foto del contenido (persona y líneas) y el hash SHA-256 de esa
  foto. El `token` es aleatorio (no es el número correlativo), para que no se puedan
  recorrer las actas.
- Reimprimir da el mismo número y el mismo QR. Una acta con error se **anula** con motivo;
  no se borra.
- No es firma digital legal: el papel se puede alterar, pero el QR muestra el original.
- Las actas de asignación ya llevan QR, pero solo con texto plano: no verifican nada.

## Reportes

1. Acta de faltantes por persona (firmable), en PDF.
2. Custodia por predio: lo que hay en la oficina de faltantes, agrupado por persona y
   oficina de origen. En PDF y en Excel.
3. Consolidado por persona: faltantes por predio y por estado. En PDF y en Excel.
4. Excel de la vista Faltantes, con persona, CI, predio, oficina de origen y acta.

La vista Faltantes pasa a agruparse por **persona → predio → oficina de origen**, con
totales en cada nivel; al hacer clic en una oficina se ven sus bienes faltantes.
**Hoy agrupa por fila de responsable** (`faltantes.html`, `h.idResponsable`), así que una
persona con faltantes en varias oficinas aparece repetida. Se corrige en la fase 3.

## Proceso operativo

0. **Oficina de faltantes:** las 3 existentes se marcan; en los demás predios se crea sola
   la primera vez.
1. **Identificación:** levantamiento de la oficina (web o APK), o registro directo. Al
   cerrar el levantamiento, lo no marcado queda como faltante **ABIERTO**. ABIERTO no toca
   el VSIAF.
2. **Registro y envío:** se seleccionan los faltantes, se confirma, y el sistema:
   - asegura la oficina de faltantes del predio y el responsable de custodia de la persona
     (los crea y los encola si no existen);
   - encola la transferencia interna;
   - emite el acta.

   Cuando el worker confirma, el faltante pasa a **EN_CUSTODIA**.
3. **En custodia:** todo movimiento del bien está bloqueado.
4. **Resolución:** si apareció, se transfiere desde la custodia; si hubo reposición o
   justificación, se registra el documento; si va a baja, queda pendiente hasta que la cola
   soporte bajas.

Ciclo del hallazgo: `ABIERTO` → `EN_CUSTODIA` → `RESUELTO`. "Pendiente" = ABIERTO o EN_CUSTODIA.

## Estado de las fases

| Fase | Estado |
|---|---|
| **1 · Datos** | ✅ Código en `main` (commit `parteIFaltantes`). Columnas aplicadas por el usuario el 28-sep; lo que falta va en el script de la fase 2 |
| **2 · Oficinas y responsables de custodia** | ✅ Código compilado, **sin commitear**. Script aplicado por el usuario el 28-sep (3 oficinas, 7 responsables, `YES \| 3`). Sin probar en vivo: el servicio lo usa la fase 3 |
| **3 · Registro + envío + acta con QR + página de verificación + vista por persona** | 🟡 Código compilado, **sin commitear**. Script aplicado por el usuario el 28-sep (`3 \| 1 \| true`); consultas nuevas verificadas en lectura. PDF y página de verificación probados sin base; el registro y el traslado, sin probar en vivo |
| **4 · Bloqueos** | 🟡 Código compilado, **sin commitear**, sin probar en vivo |
| **5 · Resolución (sacar de custodia)** | 🟡 Código compilado, **sin commitear**, sin probar en vivo |
| **6 · Conciliación + reportes** | 🟡 Código compilado, **sin commitear**. Reportes y conciliación probados contra `bd_a3` en solo lectura (CULP: 55 históricos; conciliación: 62 sin registro) |

### Qué hizo la fase 1

- `oficina.es_custodia` y `responsable.es_custodia` (boolean, default false). La sync desde
  el DBF actualiza sin recrear, así que no borra la marca.
- `hallazgo_inventario`: `id_inventario` admite vacío (faltante directo); campos nuevos
  `origen` (LEVANTAMIENTO/DIRECTO), `id_oficina_origen`, `documento_respaldo`,
  `fecha_documento`, `id_responsable_custodia`, `fecha_envio_custodia`, `usuario_envio_custodia`.
- `ControlActivosService`: constantes `EN_CUSTODIA`, `PENDIENTES`, `ORIGEN_*`; no crea un
  segundo faltante pendiente del mismo bien.
- `ControlActivosRepo`: la oficina del hallazgo sale de
  `coalesce(h.id_oficina_origen, i.id_oficina)` con `left join inventario`. Los conteos de
  faltantes incluyen EN_CUSTODIA.
- `faltantes.html`: filtro "En custodia" y la etiqueta "Registro directo".

### Estado de la fase 1 en `bd_a3`

El script de la fase 1 nunca se subió al repositorio. El 28-sep el usuario corrió a mano
las columnas y las FK (`fk_hall_oficina_origen`, `fk_hall_responsable_custodia`). Lo que
faltaba (quitar el NOT NULL de `id_inventario` e índice `uk_hall_faltante_pendiente`) va
en el script de la fase 2. `hallazgo_inventario` tenía 0 filas.

## Fase 2 — qué se hizo

- **`scripts/sql/custodia_faltantes_fase2.sql`** (lo corre `postgres`, en pgAdmin o psql;
  una sola transacción, idempotente):
  - lo pendiente de la fase 1;
  - marca `es_custodia` en CULP 372, CUSP 194 y POST 42 (por unidad + código) y en sus 7
    responsables;
  - `uk_oficina_custodia_predio`: una sola oficina de faltantes vigente por predio;
  - `uk_resp_custodia_persona`: una persona una sola vez por oficina de faltantes;
  - verificación: 3 filas y `YES | 3`.

  Probado en lectura el 28-sep: toca exactamente 3 oficinas y 7 responsables (7 personas
  distintas), sin duplicados que rompan los índices.
- **`model/service/control/CustodiaFaltantesService`**:
  - `prever(idResponsable)`: qué existe y qué se crearía (para el diálogo de confirmación);
  - `asegurar(idResponsable, autor)`: busca la oficina de faltantes del predio; si no hay,
    adopta una "FALTANTES…" sin marcar y, si tampoco hay, la crea (`max(cod_ofi)+1` contando
    las eliminadas, `FALTANTES <UNIDAD>`). Dentro busca a la misma persona (reactiva si
    estaba dada de baja) o la crea (siguiente CODRESP, cargo y cod_exp del responsable
    original). Guarda en una transacción y, ya confirmada, encola OFICINA y RESP.
    Si dos pedidos chocan, los índices únicos frenan al segundo y se reintenta una vez.
  - Rechaza (`ReglaNegocioException`) al responsable sin persona, sin predio completo o que
    ya es de custodia.
- **`IOficinaDao`**: `custodiasDelPredio`, `candidatasCustodia`.
- **Mapa**: `TileOficinaDTO.esCustodia`; la oficina de faltantes sale en violeta con la
  bandera "FALTANTES", "N en custodia · M personas", y está en la leyenda.

## Fase 3 — qué se hizo

**Orden de despliegue:** primero `scripts/sql/custodia_faltantes_fase3.sql` con `postgres`
(tiene que dar `3 | 1 | t`), después la app. Sin el script, la vista Faltantes y el ciclo de
envío fallan: consultan `acta_faltante` y `hallazgo_inventario.id_acta/estado_envio`.

- **Datos:** tabla `acta_faltante` (la crea el script; permisos copiados de
  `hallazgo_inventario`) y en el hallazgo `id_acta`, `estado_envio`, `mensaje_envio`. Un
  faltante anulado queda `estado_hallazgo = ANULADO` (fuera de "pendientes").
- **`ActaFaltanteService`:** buscar persona, bienes de la persona (todas sus oficinas, fuera
  de la custodia), `registrar` (valida todo o nada; reutiliza el ABIERTO de un
  levantamiento; emite el acta con foto JSON + SHA-256 + token aleatorio), `anular` (solo
  si ningún bien se trasladó; directos → ANULADO, de levantamiento → vuelven a ABIERTO).
- **`EnvioCustodiaService`:** `estado_envio` ESPERANDO_ALTA → ENVIADO → CONFIRMADO (o
  ERROR). Asegura la custodia, espera que el VSIAF confirme oficina y responsable de
  custodia, y recién ahí mueve el bien (transferencia INTERNA con número e historial, doc.
  de referencia = número de acta) y encola ACTUAL. Confirma con `dbf_cola_orden`. Ciclo
  `@Scheduled` cada 30 s (`custodia.envio.interval.ms`). Bloqueo de filas
  (`hallazgoDao.bloquear`) contra la anulación simultánea.
- **`PdfActaFaltanteService`:** carta, membrete `0.jpg`, bienes por predio → oficina de
  origen con subtotales, firmas ancladas al pie de la última hoja (persona a la izquierda,
  "SECCIÓN DE ACTIVOS FIJOS / Firma y sello" a la derecha: constantes `FIRMA_DERECHA_*`),
  franja con QR + URL + huella + "página X de Y" en cada hoja, marca de agua si está anulada.
- **Verificación pública:** `GET /verificar/acta-faltantes/{token}` (`permitAll`), plantilla
  `publico/verificarActaFaltantes.html`. C.I. enmascarado. Dice si el acta es auténtica,
  anulada, resuelta, o si el contenido guardado no coincide con su hash. URL base:
  `sciaf.url.publica` (por omisión `https://sciaf.uap.edu.bo`).
- **Endpoints** (`CustodiaFaltantesController`, `/administracion/control-activos/custodia`):
  `GET personas?q=`, `GET personas/{id}/bienes`, `GET actas`, `GET actas/{id}/pdf`,
  `POST actas`, `POST actas/{id}/anular`, `POST actas/{id}/reintentar`. Registrar, anular y
  reintentar exigen ADMINISTRADOR, SUPER USUARIO u `opcion_control_resolver`.
- **Vista Faltantes:** agrupada por persona → predio → oficina de origen; estado con el
  paso del traslado; filtro por defecto "Pendientes" (abiertos + en custodia); pestaña
  "Actas" (imprimir, reintentar, anular); diálogo "Registrar faltantes" (buscar persona,
  tildar bienes por oficina, documento, confirmación con `sciafConfirmar`). Excel con
  persona, C.I., predio, oficina, acta y traslado.
- **Freno provisorio:** un faltante con `estado_envio` no se resuelve ni se reabre desde la
  vista (hasta la fase 5).

## Fase 4 — qué se hizo

Dos reglas, aplicadas en todos los caminos que cambian oficina o responsable de un bien:

- **Salida:** un bien en custodia no se mueve. `Activo.enCustodia()` (oficina **o**
  responsable con `es_custodia`, así cubre lo que llegue por sync) y
  `Activo.motivoInmovilizado(accion)` (bloqueado o en custodia). `exigirNoBloqueado` ahora
  también frena la custodia.
- **Entrada:** la oficina de faltantes no se elige como destino; los bienes entran solo por
  el acta (`model/service/control/ReglasCustodia.motivoDestino/exigirDestinoValido`).

| Camino | Salida | Entrada |
|---|---|---|
| `ActivosController` `/modificar-activo` | ✅ | ✅ |
| `ActivosController` `/api/editar-registrado` y `/api/editar-lote` (`aplicarCambios`) | ✅ (antes **no** revisaba ni el bloqueo) | ✅ |
| `ActivosController` `/transferencia-masiva` y su vista previa (lista `enCustodia` en `transferencia.html`) | ✅ | ✅ |
| `ActivosController` `/asignacion-masiva` | ✅ | ✅ |
| Aprobación de altas (`aprobarActivo`, `aprobarMasivo`): por ahí pasan las 5 altas | — | ✅ |
| `ReportesController` (acta de asignación que reasigna) | ✅ | ✅ |
| `AsignacionEdicionService.aplicarDestinoAlActivo` (traslado/separación) | ✅ | ✅ |
| `TransferenciaLondraService.aprobar` | ✅ | ✅ |
| Levantamiento: `ControlActivosService.abrir` rechaza la oficina de faltantes; la API móvil `/oficinas` no la lista; el mapa web oculta "Levantar esta oficina" | | |

`EnvioCustodiaService` (entrada por el acta) no pasa por estas reglas. La salida por
resolución (fase 5) tampoco deberá pasar por `exigirNoBloqueado`.

**No se bloquea la baja:** no cambia de responsable. Un faltante en custodia que termine en
baja se resuelve en la fase 5.

**Efecto inmediato:** los 62 bienes históricos de CULP 372, CUSP 194 y POST 42 quedan
inmovilizados (acordado).

## Fase 5 — resolución

`ResolucionFaltanteService` (endpoints `GET custodia/hallazgos/{id}/situacion|destinos`,
`POST custodia/hallazgos/{id}/resolver`; mismo permiso que registrar). La vista usa este
camino para **todos** los faltantes: los que no tienen acta se delegan al resolver de siempre.

| Situación del faltante | Apareció | Justificado / Derivado a baja |
|---|---|---|
| Sin acta (levantamiento, nunca registrado) | Resuelve como siempre | Resuelve como siempre |
| Esperando la custodia (ESPERANDO_ALTA) o error antes de moverse | Cancela el traslado; el bien no se mueve | ✗ (primero tiene que llegar a la custodia) |
| Trasladándose (ENVIADO) | ✗ esperar la confirmación | ✗ |
| En custodia | Vuelve a un responsable **del mismo predio** (por omisión quien lo tenía): transferencia INTERNA + ACTUAL | Exige documento; el bien **se queda** en custodia, "pendiente de baja" |
| Error al trasladar (el SCIAF lo movió, el VSIAF no) | Vuelve igual | ✗ reintentar el traslado primero |
| EN_CUSTODIA pero el bien está en otra oficina (movido en el VSIAF) | Se cierra sin mover | ✗ |

- Al resolver se cierra el paso de traslado pendiente (`estado_envio` → null) y la
  confirmación/reintento/conteos solo miran faltantes ABIERTOS: si no, la orden de ACTUAL de
  la devolución se tomaba como confirmación del traslado y el faltante volvía a EN_CUSTODIA.
- Un faltante resuelto dentro de un acta **no se reabre** (si vuelve a faltar, acta nueva).
- La salida de la custodia no pasa por `exigirNoBloqueado` (es el único camino de salida),
  pero sí exige que el bien no esté bloqueado y que el destino sea válido y vigente.
- **No hay baja real**: `/baja-activo` es "desaprobar" (API_ESTADO=1). Los justificados y
  derivados a baja quedan en custodia hasta que la cola soporte bajas. **Confirmado por el
  usuario el 28-sep-2026:** se quedan en la oficina de faltantes hasta que se regularice y se
  defina cómo serán las bajas.

## Fase 6 — conciliación y reportes

`ReportesCustodiaService` + `CustodiaFaltantesRepo` (endpoints bajo `custodia/`):

- `GET predios` — predios con oficina de faltantes.
- `GET reportes/predio/{idPredio}?formato=pdf|xlsx` — lo que hay en la oficina de faltantes,
  por persona, con oficina de origen, acta y situación (En custodia / Resuelto — pendiente de
  baja / Histórico sin registro / Trasladándose).
- `GET reportes/consolidado?soloPendientes=&formato=pdf|xlsx` — por persona y predio:
  abiertos, en custodia, resueltos.
- `GET conciliacion` — tres listas: faltante EN_CUSTODIA con el bien en otra oficina; bienes
  en una oficina de faltantes sin acta; traslados con error o sin avanzar en 30 min.
- PDF con el membrete (`PdfCustodiaComun`, compartido con el acta) y pie con quién lo generó.
  Excel con POI, anchos calculados (sin `autoSizeColumn`: necesita fuentes AWT en el servidor).
- Vista Faltantes: menú **Reportes** y pestaña **Conciliación** (con contador).

## Regularización de históricos (28-sep-2026)

Decisiones del usuario: los faltantes históricos son **de la persona que figura en la oficina
de faltantes** (incluidos los 5 de JUAN CARLOS PELAEZ, aunque el historial diga que antes los
tenía CARLA SORIA en Odontología: eso queda para consultar con Activos Fijos), y se emite un
**acta de regularización**.

- Pestaña Conciliación → sección "sin acta (históricos)", agrupada por persona, botón
  **Regularizar** (elige bienes, documento y observación opcionales, doble confirmación).
- `ActaFaltanteService.historicos()` / `regularizar()`; endpoints `GET custodia/historicos`,
  `POST custodia/regularizar` (mismo permiso que registrar).
- Acta **AR-año-nnnnnn** (el tipo va en el prefijo y en el contenido con hash; sin columna
  nueva ni script). PDF y verificación con título y texto propios.
- Faltantes creados: `origen = HISTORICO`, **EN_CUSTODIA**, `estado_envio = CONFIRMADO`,
  `responsable = responsable_custodia =` la fila de la oficina de faltantes. Oficina de
  origen: la del historial (último movimiento hacia esa oficina) o, si no hay, la propia
  oficina de faltantes, que las pantallas muestran como "origen no registrada".
- **No mueve nada ni encola nada al VSIAF.** Se relee de la base que cada bien siga siendo
  histórico de esa persona antes de crear nada.
- Anular un acta AR: permitido mientras ningún faltante esté resuelto; sus faltantes quedan
  ANULADOS y los bienes vuelven a "sin acta".
- Al resolver "Apareció" un regularizado hay que elegir el destino (no hay fila original fuera
  de la custodia); la lista pone primero a los responsables de la oficina de origen.

Probado en solo lectura: 62 históricos, 5 con origen (Odontología, CUSP 91).

## Pendiente a propósito

- Baja real por la cola.
- Nota en `OBSERV` de ACTUAL (MEMO).
- Consultado a los encargados: que un responsable con faltantes no reciba bienes nuevos.

## Orden de la cola (hallazgo del 28-sep, condiciona la fase 3)

El worker (`tools/Worker-Vsiaf.ps1`) procesa `_cola/*.json` **por nombre de archivo**, y el
nombre empieza por la tabla: `ACTUAL_…` < `OFICINA_…` < `RESP_…`. Si la transferencia a
custodia se encola junto con el alta, **se aplica antes** que la oficina y el responsable,
y si el alta falla el bien queda apuntando a nada en el VSIAF.

**Regla para la fase 3:** la transferencia a custodia se encola recién cuando el worker
confirmó el alta del responsable de custodia (`VsiafApoyoService.estados` = `VSIAF`).
Mientras tanto el faltante espera; un scheduler la despacha cuando llega la confirmación.
No se cambia el nombre de los archivos: tocaría el orden de todo el sistema.

## Bienes históricos de las 3 oficinas (acordado el 28-sep)

**Qué se les hizo (verificado en `bd_a3` el 28-sep):** nada en sus datos. El script de la
fase 2 solo marcó `es_custodia` en las 3 **oficinas** y sus 7 **responsables**. Los 62 bienes
siguen con la misma oficina y el mismo responsable, sin faltante registrado (0 hallazgos),
sin acta y sin bloqueo. No aparecen en la vista Faltantes ni en el consolidado. Su
`_fecha_modificacion` de hoy es de la sincronización de las 06:13 (tocó 31.092 bienes), no
de la custodia; `fec_mod` del VSIAF sigue siendo la original.
Con el código nuevo desplegado: quedan inmovilizados en el SCIAF (no en el VSIAF) y salen en
el reporte de custodia como "Histórico sin registro" y en Conciliación como "sin acta".

**Convivencia con faltantes nuevos:** si una persona que ya tiene históricos (p. ej. DIEGO
MORALES en CULP 372) recibe faltantes nuevos en ese predio, se reutiliza su misma fila de
responsable de custodia: en el VSIAF quedan juntos (como ya se hacía); en el SCIAF los nuevos
tienen acta y los históricos no, así que no se confunden.

62 bienes (CULP 55, CUSP 6, POST 1) sin faltante registrado en el SCIAF. Al marcar las
oficinas quedan bloqueados (fase 4). Se muestran en el reporte de custodia como
**"Histórico sin registro"** y se regularizan después; la oficina de origen puede salir de
`historial_activo` si está.

## Trampas conocidas

- **`bd_a3` es producción.** Consultas de lectura con `set default_transaction_read_only = on`.
  Toda escritura se confirma antes con el usuario.
- **Compilación:** `mvnw -q compile` con salida vacía **no** garantiza éxito. Verificar sin
  `-q` buscando `BUILD SUCCESS`. En Linux usar `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64`.
- **No probar el envío a la cola desde una máquina sin `/mnt/dbfwin/_cola`**: el encolado
  falla y marca los activos con `sinc_vsiaf = ERROR` en datos reales.
- **Probar en otro puerto** para no chocar con la instancia del 9696 (`--server.port=9797`).
- **`application.properties`** tiene cambios locales que no se commitean a propósito. No
  hacer `git add -A`.
- **La página de verificación tiene que ser pública:** agregar su ruta al `permitAll()` de
  `SeguridadConfig` sin abrir nada más.

## Para retomar con Claude en otra máquina

Abrir el proyecto y decirle:

> Lee `docs/HANDOFF_CUSTODIA_FALTANTES.md` y el anexo de custodia en
> `docs/PLAN_CONTROL_ACTIVOS.md`. Las 6 fases están escritas; toca la prueba en vivo.

## Notificación de activos físicos faltantes (2026-10-02) — sin commitear, sin probar en vivo

Pedido de Activos Fijos: los faltantes de un responsable se notifican con su formato
(carta "NOTIFICACIÓN DE ACTIVOS FÍSICOS FALTANTES") y el plazo se escribe al registrar.

Decisiones del usuario: la notificación **reemplaza** al acta en los faltantes nuevos; el
plazo viene **vacío y es obligatorio** (1 a 90 días hábiles); las **regularizaciones (AR-)
siguen con su acta**. Las actas AF- ya emitidas se reimprimen igual que antes.

- Número: correlativo por gestión, guardado `NOT-AF-001/2026` (la columna `numero` es de 20)
  e impreso `NOT:SCIAF:AF N° 001/2026`. Se saca con `pg_advisory_xact_lock` en la misma
  transacción del registro (`IActaFaltanteDao.turnoNumeracion/ultimoCorrelativo`).
- **Sin cambio de esquema** (`acta_faltante` es de postgres): plazo, ciudad, firmante y
  unidad del destinatario van en `contenido` → bloque `notificacion`, y cada bien lleva
  `descripcionCorta/marca/modelo/serie`. Todo queda bajo la huella SHA-256.
- Firmante = `configuracion_gestion.responsable_activos_nombre` de la gestión (hoy "Lic.
  Verónica Layme Cori"). Si falta, **no deja emitir** (si no, saldría sin firma para siempre).
- Marca/modelo/serie no tienen columna en `activo`: `DatosTecnicos` los separa de la
  descripción (`M:`, `MARCA`, `MOD.`, `N/S:`, `S:`, `SERIE:`...). Si el valor parece texto
  libre, no lo separa y queda en la descripción ("—" en la columna).
- PDF: `PdfActaFaltanteService.notificacion` (membrete, QR y huella como el acta; plazo en
  letras "5 (cinco) días hábiles"). Página pública de verificación adaptada.
- Pantalla Faltantes: campo "Plazo para responder (días hábiles)", textos "notificación",
  pestaña "Notificaciones y actas", números mostrados como en el papel.

Pendiente: probar un registro real (escribe en bd_a3 y encola el traslado al VSIAF). En
otras pantallas (seguimiento, ruta de activo) el número aparece como `NOT-AF-001/2026`.

### Vista previa de la notificación (2026-10-02)

Botón **Vista previa** en el modal "Registrar faltantes" (pide el plazo, igual que registrar).
`POST /administracion/control-activos/custodia/actas/vista-previa` → PDF con marca de agua
"VISTA PREVIA", "N° ___/año", sin QR ni código de verificación. **No registra nada**:
`ActaFaltanteService.vistaPrevia` usa las mismas validaciones (`preparar`) y datos (`armarActa`,
`datosNotificacion`, `contenido`) que el registro, en un `TransactionTemplate` de solo lectura
que además se deshace; no toma el turno de numeración ni llama a `EnvioCustodiaService`.
Mismo permiso que registrar (`exigirPermiso`).

## Arreglo temporal: misma persona en dos registros (06-oct-2026)

**Problema.** La sincronización de RESP.DBF crea dos `persona` para el mismo funcionario
cuando en el VSIAF tiene filas con C.I. y filas sin C.I. (`ResponsableController.syncFromMounted`:
con C.I. busca solo por C.I.; sin C.I. busca por nombre). El alta automática de custodia busca
por `id_persona`, así que habría dado de alta a "los dos Richard" en la oficina de faltantes.
Acordado con ING. Saul: por ahora se resuelve **a mano en el registro**; la limpieza de la base
(unificar personas, corregir la sync) queda para después.

**Qué hace ahora el registro de faltantes** (sin cambio de esquema):
- Paso 2: "Sumar otro registro de esta persona" junta los bienes de otro `id_persona` en la
  misma notificación. La notificación sale **a nombre del registro con C.I.** (decisión del
  usuario). El servidor rechaza un registro con otro C.I. o con un nombre sin al menos dos
  palabras en común.
- Por cada predio se elige a quién de la oficina de faltantes van los bienes
  (`GET /custodia/destinos-custodia`). Se preselecciona solo a la misma persona o a un registro
  sumado; un homónimo (mismo nombre, otra persona) se elige a mano o se marca "Es otra persona"
  (`crearNuevo`). Solo se puede elegir a quien esté confirmado en el VSIAF (o en cola).
- El elegido se guarda en `hallazgo.id_responsable_custodia` al registrar; el ciclo de envío
  salta los que ya lo traen. Si su oficina era una "FALTANTES…" sin marcar, se adopta.
- El alta automática usa la persona **del acta** y se niega a crear si en la oficina hay alguien
  con el mismo nombre (barrera `exigirSinHomonimo`), salvo `crearNuevo`. El permiso de
  `crearNuevo` vale solo para el primer intento (no hay dónde guardarlo sin esquema): si ese
  intento falla por algo transitorio, el faltante queda en ERROR y hay que reintentar o anular.
- La vista Faltantes agrupa por `coalesce(acta.id_persona, responsable.id_persona)`.
- **Pantalla guiada (pedido del usuario: que lo entienda cualquiera):** pasos numerados (1 Persona,
  2 Bienes, 3 A quién se entregan, 4 Datos). Al elegir a la persona, el sistema busca solo otro
  registro con el mismo nombre y pregunta "¿Es la misma persona? Sí / No". El paso 3 dice "No
  necesita hacer nada" cuando no hay decisión, y si hay homónimo pregunta con opciones de un clic.
  Si cambian las personas juntadas, el paso 3 se vuelve a preguntar (no se arrastra la respuesta).
- **Filtro por oficina (paso 2):** select2 con las oficinas de los bienes cargados, agrupadas por
  predio, con cuántos bienes tiene cada una. Se escribe el código (12 = 012) o el nombre. Lo marcado
  en otras oficinas sigue elegido y el contador lo dice ("· N en otras oficinas"). Solo pantalla.
- **Botones del pie ocultos:** `modal-dialog-scrollable` solo da scroll a `.modal-body`;
  `.sm-modal-cuerpo` no lo tenía y `.sm-modal` recorta. Arreglado en `sciaf-modulo.css` para todos
  los modales con ese patrón (también mapa, menú, usuario, ingreso).
- Sin probar en vivo. Prueba del modal con navegador simulado (jsdom, 30 comprobaciones) y
  capturas con Chrome sin pantalla, en el scratchpad de la sesión, fuera del repo.

**Pendiente conocido:** el reporte consolidado por persona y el contador de faltantes del
buscador siguen agrupando por la persona del responsable (los del registro sin C.I. salen aparte).
