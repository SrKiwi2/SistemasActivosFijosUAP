# Activos duplicados: plan y estado (handoff)

**Estado al 19-oct-2026:** escrito y compilado en una copia aparte. Pasa las pruebas
unitarias y las plantillas y el PDF se generaron sin la app. **Sin probar en el navegador y
sin commitear.** Ver la sección 6 antes de desplegar.

**Pedido de los encargados (chat):**

> Son los activos que están 2 veces en sistema, errores del revalúo. Mismo activo con 2
> códigos: lo registraron 2 veces como si fueran 2 activos distintos. Están en VSIAF y se
> identificaron al hacer levantamiento; puede que haya más, no se sabe. La idea es como
> faltantes: tener todo en una oficina. No habrá corrección porque están por demás; la
> corrección la hará el nuevo revalúo.
>
> — No son códigos duplicados, son activos duplicados. — Se van encontrando (en el barrido).

---

## 1. Qué es un duplicado aquí (y por qué la importación no los «detectó»)

- **No es un código repetido.** Los dos códigos son distintos y válidos.
  - La importación inicial (`ActualImportService`, sep-2025) y la sincronización
    (`ActivoSyncService`) identifican el activo por `CODIGO`.
  - Por eso un bien con dos códigos entró como dos activos, **igual que está en el VSIAF**.
    No es un fallo de la importación.
  - La revisión de códigos repetidos (`/administracion/correlativo/duplicados`,
    `uk_activo_codigo`) es otra cosa.
- **Es un mismo bien físico con dos registros.** Solo alguien frente al bien puede
  afirmarlo, así que los identifica la gente durante el barrido.
- **Cómo aparecen hoy.** Al cerrar un levantamiento (`ControlActivosService.cerrar`), el
  código que no se escaneó queda como FALTANTE. El sobrante de un duplicado llega como
  faltante.
- **ACTUAL.DBF no tiene marca, modelo ni serie.** Esos datos van dentro de `DESCRIP`; el
  parser es `control/DatosTecnicos`.

## 2. Decisiones (Kevin, 19-oct)

| # | Decisión |
|---|---|
| D0 | **Módulo aparte** de Faltantes: pantalla y opción de menú propias (`opcion_duplicados`, grupo «Control por responsable») |
| D1 | Una oficina de duplicados **por predio**. Nombre `DUPLICADOS <unidad>`. Se adopta una «DUPLICADOS…» hecha a mano si existe |
| D2 | A la oficina va **solo el código sobrante**; el principal se queda sin tocar; los dos quedan vinculados |
| D3 | Dentro de la oficina, el bien queda a nombre de **la misma persona** (como la opción B de faltantes) |
| D4 | Se emite **una sola notificación**, formato parecido a faltantes: `NOT:SCIAF:AD N° 001/2026`. Dice qué activo dispuso quién como duplicado y por qué (motivo), que por eso se considera faltante y que se registra en la ubicación de duplicados. Sin plazo ni reiterativas |
| D5 | Un faltante, incluso ya notificado, que resulta duplicado **se cierra** con resolución `DUPLICADO`. La notificación de duplicado lo dice: «queda sin efecto el requerimiento de informar sobre su paradero». No se anula la NOT:SCIAF:AF, porque puede tener otros bienes |
| D6 | Puede haber duplicados **entre predios**. Se permite, con un aviso. El sobrante va a la oficina de duplicados de *su* predio |
| D7 | El sobrante queda **bloqueado** para transferencias, pero el bloqueo es **revocable**: «Retirar de duplicados» con justificación |
| D8 | Registran **ADMINISTRADOR, SUPER USUARIO y quien tenga la opción** «Activos duplicados», por ejemplo un usuario temporal «de revalúos» |
| D9 | Marca nueva `es_duplicados` en `oficina` y `responsable`, **no** `es_custodia`. Así el código de faltantes queda intacto |
| D10 | El buscador de parecidos lee **la base del SCIAF** (copia de ACTUAL.DBF que la sync mantiene al día), no un archivo subido. Solo sugiere y no escribe nada en el VSIAF |

**Pendiente con Saul:**
- el texto exacto de la notificación;
- si quiere ver la oficina de duplicados en el Mapa de control con un color propio (hoy no
  está, ver sección 5).

## 3. Cómo funciona

**Registrar** (pantalla «Activos duplicados» → «Registrar duplicado», 4 pasos):
1. Código que sobra.
2. Código que se queda. Se escribe o se elige entre los «parecidos», que el sistema solo
   sugiere. Hay un botón «Invertir».
3. Revisión: qué impide registrar, avisos y a dónde irá el bien.
4. Motivo, obligatorio, de 10 caracteres o más. Va en la notificación.

Bloquea el registro:
- uno de los dos no está vigente;
- el sobrante está bloqueado, ya está en duplicados o ya figura como el que se queda en
  otro caso;
- el sobrante no tiene persona o predio;
- el sobrante se está trasladando a faltantes (o ese traslado tiene error). Hay que esperar.

Avisos:
- el sobrante figura como faltante, y ese faltante se cerrará;
- **el que se queda figura como faltante**: quizá se eligió al revés, y hay que marcar «Lo
  revisé»;
- son de predios o grupos contables distintos, o de personas distintas;
- es un bien solo del SCIAF.

**Traslado** (`EnvioDuplicadosService`, mismo esquema que faltantes):
1. `ESPERANDO_ALTA`: se asegura la oficina y la persona (`CustodiaDuplicadosService`) y
   se espera la confirmación del VSIAF.
2. `ENVIADO`: transferencia INTERNA y `UPDATE` de ACTUAL en la cola.
3. `CONFIRMADO`: el caso pasa a `EN_DUPLICADOS`.

Un error queda anotado y se reintenta desde el detalle.

**Regularizar.** Si el sobrante **ya está** en la oficina de duplicados sin caso, el
registro lo regulariza sin moverlo: el caso nace `EN_DUPLICADOS`, con notificación, y se puede
retirar. Pasa con una oficina «DUPLICADOS…» adoptada o con un bien movido a mano en el VSIAF.
Sin esto, el bien quedaba trabado.

**Cruce con faltantes.** Un bien con un caso pendiente:
- no se abre como faltante al cerrar un levantamiento;
- no se registra en una notificación de faltantes;
- el ciclo de faltantes no lo mueve.

Si a la vez se está trasladando a faltantes, el registro de duplicado espera a que termine.

**Salir:**
- **Retirar**: el bien vuelve a un responsable del mismo predio (por omisión, quien lo
  tenía), con justificación y transferencia INTERNA que viaja al VSIAF.
- **Anular**: solo si el bien todavía no se movió. La notificación queda anulada y el
  faltante que se había cerrado se reabre como estaba, con su paso de envío, si nadie abrió
  otro.
  - Si un intento de traslado dejó una orden ACTUAL en la cola, no se anula. Se pulsa
    «Reintentar»: si el VSIAF ya lo movió, el caso se confirma solo.

**Bloqueo.** El bien en duplicados queda inmovilizado (`Activo.enDuplicados()` →
`inmovilizado()` / `motivoInmovilizado`). Se frena en:
- transferencia interna, externa y Londra; asignaciones; edición del activo;
- donaciones y adicionales;
- registro de faltantes, y el alta de faltantes a nombre de un responsable de duplicados;
- levantamiento: no se abre en la oficina de duplicados y no aparece en el filtro de
  oficinas de Faltantes.

`ReglasCustodia.motivoDestino` impide elegir la oficina de duplicados como destino.

**Verificación pública:** `/verificar/duplicado/{token}`, con el C.I. enmascarado.

### Pantalla sin scroll (20-oct)

A pedido del usuario (igual que Donaciones y Adicionales), solo `duplicados/vista.html`:
- El registro es un **carrusel de 3 pasos**: 1 El que sobra · 2 El que se queda · 3 Revisión y motivo.
  - Paso 2: la ficha del que sobra a la izquierda, para comparar.
  - A la derecha: búsqueda, «Invertir», la ficha del que se queda y los parecidos. Con el que se
    queda elegido, los parecidos se ocultan y vuelven con un botón. La lista ocupa el alto que
    queda (`altoGemelos`).
  - Al paso 2 se pasa con el que sobra elegido; al 3, con los dos. Atrás, siempre.
  - Vista previa y Registrar, solo en el paso 3.
- **Fichas** con «Etiqueta: valor» en una línea, con el texto completo en el `title`. También en
  el detalle del caso.
- **Listado:** filas de alto parejo. Según el ancho real del listado se ocultan Origen (≤1250),
  Registro (≤1100) y Notificación y el selector de filas (≤820).
- **Medido** con el marco real: 0 px de scroll en el formulario en 1366×657, 1366×768, 1280×600,
  1024×700, 800×700, 1536×730 y 1920×950. El listado a 800×700 queda con 3 px.
- 20 comprobaciones de comportamiento. **No probado en vivo.**
- **Arreglos del `revisor` (20-oct):**
  1. **Grave:** con la red lenta se podía registrar un par distinto del confirmado. Una búsqueda
     pendiente cambiaba «el que se queda» con el diálogo abierto y `pedido()` se leía después.
     Ahora:
     - al buscar se suelta la elección anterior, y no se avanza ni se registra hasta que responde;
     - las respuestas viejas se descartan;
     - se envía exactamente el par del diálogo, y si cambió, no se registra.
  2. Revisiones (`/api/situacion`) fuera de orden, por ejemplo al invertir rápido: se descarta la
     que no corresponde al par actual. Venía de antes.
  3. Oyentes globales (`resize.dupGemelos`, `sciaf:duplicados`): se quitaban para siempre al
     cambiar de pestaña, porque el módulo queda desprendido. Ahora solo se ignoran mientras está
     desprendido. Con esto vuelve el refresco en vivo del listado tras cambiar de pestaña, que
     también venía de antes.
  - Probado: 8 comprobaciones más, con búsquedas lentas simuladas.

## 4. Archivos

**Nuevos** (bajo `src/main/java/com/usic/SistemasActivosFijosUAP/`):
- `model/entity/ActivoDuplicado.java` (tabla `activo_duplicado`) y
  `NotificacionDuplicado.java` (tabla `notificacion_duplicado`);
- `model/dao/IActivoDuplicadoDao.java` e `INotificacionDuplicadoDao.java`;
- `model/dto/duplicados/DuplicadosDTOs.java`;
- `model/repository/DuplicadosRepo.java` (lecturas JDBC);
- `model/service/duplicados/`: `DuplicadosService`, `EnvioDuplicadosService`,
  `CustodiaDuplicadosService`, `PdfNotificacionDuplicadoService` y `SimilitudActivos`
  (puntaje de parecidos, con pruebas en `SimilitudActivosTest`);
- `controller/duplicados/DuplicadosController.java` (`/administracion/duplicados/**`) y
  `VerificacionDuplicadoController.java`;
- `templates/duplicados/vista.html` y `templates/publico/verificarDuplicado.html`;
- `scripts/sql/activos_duplicados_fase1.sql`.

**Tocados** (cambios chicos):
- `Activo`, `Oficina` y `Responsable` (marca y bloqueo);
- `ReglasCustodia`;
- `ActivosController`, `TransferenciaLondraService`, `DonacionService`, `AdicionalService`;
- `ControlActivosService`, `ControlActivosRepo`, `CustodiaFaltantesRepo`,
  `ActaFaltanteService`, `CustodiaFaltantesService`;
- `IOficinaDao`, `OpcionMenuSeeder`;
- `layout/head.html` (evento SSE `duplicados`) y `activo/transferencia.html` (texto).

## 5. Pendientes conocidos

- **Mapa de control:** la oficina de duplicados se ve como una oficina común. Si alguien
  pulsa «Levantar», el servidor lo rechaza con un mensaje. La APK la sigue listando en el
  levantamiento, y el servidor la rechaza igual.
- **Personas y cargos:** al juntar personas, no se revisa el índice
  `uk_resp_duplicados_persona`. Si dos personas que se unifican están las dos en la misma
  oficina de duplicados, la base rechaza el cambio y no se guarda nada (no hay daño).
- **Historial de transferencias:** `Transferencia.getMotivoFaltante` no reconoce la oficina
  de duplicados. El motivo se ve en la observación de la transferencia.
- **Ventana en proceso:** mientras el caso está `EN_PROCESO`, el bien todavía puede
  transferirse por los caminos normales. El traslado lo detecta («cambió de responsable») y
  el caso se anula y se vuelve a registrar.
- **Revisión del 19-oct:** el revisor encontró 10 fallos y se corrigieron todos. Quedan dos
  fuera de este módulo, pendientes de decisión de Kevin:
  - `SciafModulo.escapar` no escapa comillas en atributos. Afecta a todos los módulos; acá se
    usa un `esc` local.
  - `EnvioCustodiaService` recorta `documento_referencia` a 120, pero la columna es de 100.
- **Revisión masiva de sospechosos por oficina o predio** (opcional): hoy los parecidos se
  buscan de a un activo.

## 6. Antes de desplegar

1. Correr `scripts/sql/activos_duplicados_fase1.sql`, que es idempotente. Crea las tablas,
   las columnas `es_duplicados` y los **índices parciales** (hbm2ddl no los crea).
2. Verificar que la configuración de la gestión tenga `responsable_activos_nombre`. Sin
   firmante no se emite.
3. Desplegar. El seeder crea la opción «Activos duplicados» en «Control por responsable».
   Hay que asignarla al usuario de revalúos.
4. Probar primero con la **Vista previa**, que no guarda nada. «Registrar» mueve bienes en
   el VSIAF real.

Reglas de trabajo de siempre:
- no commitear sin autorización;
- antes de compilar, verificar que la app no esté corriendo (devtools reinicia y aplica
  `hbm2ddl` en `bd_a3`);
- nada de escrituras en `bd_a3` sin preguntar.
