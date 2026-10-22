# Handoff — Registro de donaciones (13-oct-2026, dos tandas)

> Para el agente que retoma: lee esto, después `docs/PLANTILLA_MODULOS.md` y `CLAUDE.md`.
> Si algo de aquí contradice al código, manda el código.

## Estado

- **Sin commitear, sin probar en vivo.** Compila (copia en el scratchpad, JDK 21). La vista
  se renderizó con Thymeleaf offline, pasó una prueba de navegador simulado (jsdom, 31
  comprobaciones: pasos, validaciones, vista previa de códigos, duplicar, pedido enviado,
  éxito, detalle, escapado) y se vio en Chrome sin pantalla en modo claro y oscuro.
- El `revisor` pasó; sus hallazgos se aplicaron (ver «Decisiones y arreglos»).
- **Primera prueba en vivo del usuario (13-oct, tarde):** el registro falló y se deshizo entero
  (no quedó nada): `historial_activo` tiene un CHECK sobre `tipo_evento` y se usaba
  `ALTA_DONACION`. Ahora el alta usa `REGISTRO` (valores permitidos en el comentario de
  `HistorialActivo.tipoEvento`). Ojo: `ActivosController` usa `CAMBIO_CODIGO` y `EDICION`,
  que tampoco están en esa lista; ahí el historial va aparte y solo se pierde la fila (log WARN).
- **Primer arranque con este código = cambio de esquema en producción** (`hbm2ddl=update`):
  crea las tablas `donacion_acta` y `donacion_bien`, la columna `activo.procedencia` y la
  opción de menú `opcion_donacion` (el seeder solo inserta). Todo es aditivo y anulable.

## Qué hace

Administración de activos → **Registro de Donaciones** (`/administracion/donacion/vista`).

- Cada registro es un **acta** `DON-<gestión>-<0001>`: donante (persona o institución, C.I./NIT,
  contacto), documento de respaldo, fecha, observación y un **destino común** (predio → oficina →
  responsable, financiador opcional).
- Cada bien del acta: grupo contable, auxiliar (con alta rápida), estado de conservación,
  cantidad, valor unitario, vida útil, descripción y detalle (marca, modelo, color, material,
  medidas, series —una por línea, una por unidad— y características). Se crea **un activo por
  unidad**.
- **Código:** `MUN-PRED-GRP-D00001`. Serie D propia **por prefijo** municipio-predio-grupo (decisión
  del usuario). No toca la serie normal: `preview_codigo_activo_by_codes` solo cuenta 5 dígitos.
- **Estado:** `ACTIVO` directo (decisión del usuario), sin pasar por Pendientes;
  `procedencia = DONACION`, `sinc_vsiaf = NO_APLICA`. Se asignan y transfieren en el SCIAF.
- **Registro rápido:** asistente en 4 pasos, Ctrl+Enter / Alt+N, «Duplicar» un bien, vista previa
  en vivo de los códigos D, «Otra donación al mismo destino», copiar códigos.
- Acta imprimible desde el detalle (ventana del navegador, sin PDF de servidor).
- **Etiquetas con QR** (PDF A4, 3 × 8): `GET /administracion/donacion/api/acta/{id}/etiquetas`
  (`PdfEtiquetasDonacionService`). El QR usa el formato que ya lee la app móvil
  (`sigla|municipio|predio|grupo|código|descripción`) y la etiqueta dice «DONACIÓN».
- **Altas rápidas de oficina y responsable** en el paso 2 (decisión del usuario: preguntar cada
  vez). Botones «Crear y subir al VSIAF» / «Crear y dejar pendiente». Pendiente = no se encola,
  queda `pendienteDbf=true` y se avisa a **todos los ADMINISTRADOR y SUPER USUARIO**
  (`AvisoPendienteVsiafService`: notificación + SSE). Lo suben luego con «Subir al VSIAF» en
  Oficinas / Responsables, o se sube solo cuando un bien normal se mueve allí
  (`VsiafApoyoService.asegurarEnVsiaf`). Los endpoints `registrar-oficina` y
  `registrar-responsable(-forzado)` aceptan `subirVsiaf` (por omisión `true`: los demás módulos
  no cambian) y `desde`. Rutas extra para `opcion_donacion` en `PermisoOpcionInterceptor`.
- **Anular un acta de donación** (solo administradores, con motivo): el acta queda ANULADA y sus
  bienes CANCELADO con el código D liberado (`codigo_anulado`). Se rechaza si algún bien se movió,
  tiene historial distinto de REGISTRO, un faltante abierto o figura en un acta de asignación.
- **Anular un acta de faltantes con bienes donados ya en custodia**: si todo lo trasladado son
  donaciones, se anula y vuelven a su responsable original (transferencia interna solo SCIAF).
  Columna nueva `movidos_vsiaf` en el listado de actas (`CustodiaFaltantesRepo.actas`).
- **Distintivo «DONACIÓN»** junto al código en Consulta de activos, Asignar activos y
  Transferencia (`SciafCatalogos.marcaDonacion`, clase global `.sciaf-don`).

## Barreras para que nada llegue al VSIAF

| Dónde | Qué |
|---|---|
| `Activo.esSoloSciaf()` / `esCodigoDonacion()` | procedencia DONACION **o** código `…-D\d{5}` |
| `Activo.setSincVsiaf` (sobrescrito) | un bien solo-SCIAF queda siempre `NO_APLICA` aunque un flujo marque EN_COLA/ERROR |
| `ActualDbfWriterService` | insertar / actualizar / lote descartan los bienes solo-SCIAF (cubre todos los flujos) |
| `JavaDbfService.actualizarActivoParaTransferencia` | camino directo de Londra: omite códigos D |
| `AsignacionEdicionService.enviarAlVsiaf` | filtra donaciones (no manda oficina/responsable si solo hay donaciones) |
| `ActivosController` modificar | responde «solo SCIAF» en vez de «enviado al VSIAF» |
| `ActivosController` cambio urgente de código | rechazado para donaciones (pasar a la serie normal chocaría con el VSIAF) |
| `EnvioCustodiaService` | el traslado a custodia de un bien donado queda CONFIRMADO / EN_CUSTODIA al instante (si no, quedaba ENVIADO para siempre) |
| `IActivoDao.listarParaConciliacion` | la conciliación BD↔VSIAF no las marca como anomalía |
| `CorrelativoController` | la revisión de correlativos no las cuenta como «mal formadas» |
| `ParserQrActivoService` (app móvil) | acepta `…-D00001` y `D7` tecleado |

Siguen viajando al VSIAF, a propósito, los **catálogos**: un auxiliar creado con el «+» y la
oficina/responsable cuando una transferencia mixta lleva bienes normales.

## Decisiones y arreglos del revisor

- Turno único `pg_advisory_xact_lock(0x5C1AFD0A1)` para número de acta y correlativos D.
- Token por intento (`donacion_acta.token_registro`, único): si la respuesta se pierde y se pulsa
  Registrar otra vez, devuelve el acta ya creada.
- Permiso validado en el servicio (`DonacionService.puede`: ADMINISTRADOR / SUPER USUARIO u
  `opcion_donacion`) además del controlador y del interceptor de menú.
- Rechaza oficina/responsable de custodia, dados de baja o inactivos (API_ESTADO 3), grupo o
  financiador ELIMINADO, auxiliar de otro predio/grupo, fecha futura, descripción > 1024.

## Arreglos del segundo `revisor` (13-oct)

- El historial del alta se guarda **después** de confirmar y en su propia transacción: si el CHECK
  de `tipo_evento` lo rechaza, solo se pierde esa fila (log WARN), no la donación.
- «Subir al VSIAF» de un responsable (`/responsable/subir-dbf`) sube antes su oficina si estaba pendiente.
- Transferir o asignar **solo donaciones** (`ActivosController` transferenciaMasiva / asignacionMasiva)
  ya no sube la oficina ni el responsable destino pendientes.
- Anular donación: bloquea las filas (`FOR UPDATE`) y rechaza si algún bien está en un
  levantamiento EN_EJECUCION. Los **códigos D no se reutilizan** (el máximo cuenta `codigo_anulado`):
  sus etiquetas pudieron pegarse y el historial se busca por código.
- Un reintento con el mismo token avisa que devolvió el acta del intento anterior.

## Tercera tanda (13-oct, noche): tabla, ventana de detalle y fotos

Pedido del usuario tras probar la primera versión.
- **Listado en tabla** (DataTables paginado en el servidor, `POST /api/datatables`): N° de acta,
  fecha, donante, documento, destino, bienes, valor y estado. Clic (o Enter) en una fila abre el
  **detalle en una ventana** (`.js-modal-acta`, patrón `sm-detalle-*` como Movimientos →
  Transferencias) con Imprimir acta, Etiquetas y Anular arriba y los datos debajo. Se quitaron la
  lista de tarjetas, `/api/actas` y `DonacionService.listar`.
- **Nueva donación** oculta la tabla y abre el asistente en su tarjeta; la pantalla de éxito tiene
  «Volver al listado».
- **Sin animaciones de movimiento en íconos** (se quitaron `dn-flotar` y `dn-rebote`).
- **Fotos: hasta 3 por bien (ítem)**, compartidas por sus unidades (decisión del usuario).
  Tabla `donacion_foto`; archivos en `uploads/donaciones/fotos/` (`ArchivoStorageService`).
  En el asistente se eligen por tarjeta, se reducen en el navegador (1600 px, JPG 85 %) y se suben
  después de registrar (`POST /api/acta/{id}/fotos`); si alguna falla, la donación igual queda y
  se agregan desde el detalle. En el detalle: galería por bien, visor, agregar y quitar
  (`POST /api/foto/{id}/quitar`, baja lógica).

## Cuarta tanda (14-oct): editar, eliminar, morado sólido

Pedido del usuario: liberar el número y los códigos de un acta de prueba, poder corregir todo, y
estética (todo morado, íconos sólidos sin degradado ni relieve).
- **Eliminar** (`POST /api/acta/{id}/eliminar`, solo administradores, escribiendo el número del
  acta): borra el acta, sus bienes (activos), sus fotos (filas y archivos, después de confirmar) y
  el alta en el historial. Número de acta y códigos D quedan libres (los generadores son por
  máximo). Solo si ninguna unidad tiene «vida»: movimientos en el historial, faltantes,
  asignaciones, levantamientos, o (en vigente) no estar donde se registró. Vale para actas anuladas.
  Para lo que ya tuvo uso sigue «Anular». Queda en Monitoreo de actividad (ELIMINACION).
- **Editar** (`POST /api/acta/{id}/editar`): el asistente se abre con el acta cargada
  (`detalle.edicion`). Cada tarjeta lleva `itemOriginal`. Mismo grupo y predio → las unidades
  conservan su código; más cantidad → códigos nuevos; menos → se borran las últimas. Otro grupo o
  predio → se rehacen con códigos nuevos. Bienes quitados → se borran. Borrar, rehacer o cambiar
  el destino solo con unidades sin vida; los datos descriptivos se corrigen siempre. Fotos de
  bienes existentes: desde el detalle; bienes nuevos: en el asistente. El número no cambia.
- **Series por posición**: la línea N es la unidad N; una línea vacía = sin serie (antes se
  compactaban y una serie podía pasar a otra unidad).
- **Estética:** `.dn-pantalla` redefine `--sm-primario*` y `--bs-primary` en morado (botones,
  segmentos, paginación, foco). Íconos de cabecera de color sólido sin sombra; sin rosa ni
  degradados; distintivo global `.sciaf-don` también sólido.

**Arreglos del `revisor` a la cuarta tanda:**
- Editar **no toca** las unidades con vida (movidas, dadas de baja, en un acta…): antes les
  pisaba conservación, costo y descripción (p. ej. extendía «BAJA DEFINITIVA» a las demás). Los
  datos del ítem en el formulario salen de su primera unidad sin vida.
- `idsConVida` suma `historial_bloqueo_activo`, `transferencia_detalle`, `baja_activo` y
  `asignacion_movimiento_detalle` (antes «Eliminar» se ofrecía y fallaba por FK).
- Borrar activos al editar (quitar bienes, bajar cantidades, rehacer por grupo o predio) exige
  ADMINISTRADOR / SUPER USUARIO; el resto del módulo corrige datos y agrega.
- Conservar códigos se decide por **id de grupo y de predio** (igual que la pantalla), no por el
  prefijo del código.
- `donacion_acta.revision`: una edición con datos viejos (otra pestaña u otro usuario guardó
  antes) se rechaza en vez de borrar lo agregado.
- Cambiar el destino actualiza el alta (REGISTRO) en el historial de las unidades movidas.
- Un acta cuya oficina o responsable se dieron de baja después sigue pudiendo corregirse si no
  se cambia el destino.
- Pantalla: en edición muestra qué código va con cada línea de serie y qué códigos se borran.
- Pendiente fuera del módulo (no tocado): en `activo/formulario.html` cambiar el grupo al
  modificar un activo arma un código `…-XXXXX` y `modificar-activo` lo guarda sin validarlo.

## Quinta tanda (14-oct): D al final, documentos sin D, fotos por activo

- **Formato del código:** `MUN-PRED-GRP-00001D` (la D al final, decisión del usuario tras
  consultarlo). Serie propia por prefijo desde 00001. Se descartó quitar la D: el código es único
  (`uk_activo_codigo`) y todo el sistema (buscador, QR, app, levantamientos, sincronización con el
  VSIAF) identifica al bien solo por el código; un 00001 de donación chocaría con el 00001 normal.
  El sistema reconoce también el formato inicial `…-D00001` y la numeración sigue después de los
  números usados en cualquiera de los dos. Migración opcional de los ya registrados:
  `scripts/sql/donaciones_codigo_d_al_final.sql` (la corre el usuario; reimprimir etiquetas).
- **Documentos sin la D:** `Activo.codigoDocumento(…)` en actas y reportes (faltantes, custodia,
  Word de control, transferencias, bajas, asignaciones, Excel de asignaciones) y en el acta de
  donación impresa. La D sigue en la base, las pantallas y las **etiquetas** (identifican al bien).
- **Fotos por activo** (hasta 3 por activo, antes 3 por ítem compartidas): `donacion_foto.id_activo`.
  En el detalle, columna Fotos en cada fila. En el asistente, la foto de una tarjeta se pone a cada
  unidad (una fila por unidad, mismo archivo). Las fotos del formato inicial (por ítem) se
  reparten a cada unidad la primera vez que se tocan (`repartirFotosDeItem`).

**Arreglos del `revisor` a la quinta tanda:**
- **App móvil (grave):** `mobile/src/services/qr.ts` no conocía la D y leía `01-04-06-00001D`
  como `01-04-06-00001` (otro activo): en un levantamiento marcaba encontrado al normal o creaba
  un sobrante sobre él. Corregido el patrón (D al final o adelante), `claveCodigo` conserva la D
  (`mobile/src/services/levantamiento.ts`) y el ingreso manual acepta la D. **Hay que compilar y
  publicar un APK nuevo antes de hacer levantamientos con etiquetas de donaciones.**
- Los **Excel** conservan la D (custodia por predio, reporte de asignaciones): son datos que pueden
  volver a cargarse (Transferencias → importar Excel) y sin la D el número es el de otro activo.
  Sin D quedan los PDF y Word.
- Al editar, si se rehacen unidades (otro grupo o predio), sus fotos pasan a las nuevas por posición.
- Las fotos del formato inicial se reparten a todas las unidades del ítem (también las que no están
  ACTIVO); quitar una de ellas exige indicar el activo.
- Migración: suma `detalle_asignacion`, `transferencia_detalle`, `asignacion_movimiento_detalle` y
  `hallazgo_inventario`; ya no hace COMMIT solo.
- Ojo con lo impreso sin D: `01-02-04-00001` en papel es también el código de un activo normal.
  Decisión del usuario; si un papel se usa para buscar el bien, buscar con la D.

## Sexta tanda (20-oct): sin scroll en pantallas chicas (solo la vista)

Pedido: «no debe tener scroll; en pantallas más pequeñas se crea scroll dentro del formulario en el
carrusel». Antes, en 1366×768 el paso 1 tenía 121 px de scroll interno, el paso 3 con un bien 747 px
y con dos bienes 1.615 px, más scroll de página. Solo cambió `donacion/vista.html`; el servidor no.

- **Asistente en columna:** pasos arriba, `.js-etapas` al medio y el pie con los botones siempre a la
  vista. `ajustarAlto` mide `.js-etapas` (y `.js-exito`), ya no `.js-panel`; solo si la ventana es
  más baja de lo previsto se desplaza la etapa, nunca la página. Con el asistente abierto la cabecera
  pierde totales, «Nueva donación» y subtítulo (`.dn-en-form`, como Adicionales).
- **Paso 3 = carrusel:** se ve un bien a la vez (`.dn-bien.activo`); las fichas de arriba
  (`pintarChips`) pasan de uno a otro y se ponen en rojo si el bien tiene algo por revisar. Cada
  tarjeta tiene tres secciones: Datos · Detalle · Fotos y resultado (`seccionBien`). Al validar se
  muestra el bien con el primer error **en la sección de ese campo** (grupo/descripción/cantidad/valor
  → Datos; series → Detalle; descripción final larga → Fotos y resultado). Se quitó «contraer».
  El estado de las series va en la línea de la etiqueta; el mapa línea → código (edición) en
  `.js-series-mapa`. El aviso «unidades con movimientos» (edición) es un candado en la cabecera de la
  tarjeta que abre el texto completo.
- **Paso 2:** un alta rápida abierta ocupa el lugar de los campos (`.dn-con-alta`); su título dice en
  qué predio u oficina se crea.
- **Pasos 3 y 4:** los totales van en el pie. Paso 4: dos tarjetas (datos en 2 columnas, el texto
  completo en el `title`) y la tabla de bienes ocupa lo que queda; solo sus filas se desplazan.
- **Listado:** columnas cortas al ancho de su contenido, donante en 2 líneas, documento y destino en 1;
  por `@container` (ancho real del listado) se ocultan Documento (≤1150), Fecha (≤930) y Valor
  (≤800). `minFilas: 3` (con 5 fijas no entraban y salía scroll de página).
- **Por alto de ventana:** `max-height` 820 / 680 / 620 px compactan; a ≤620 (1280×720 con barras
  del navegador) se oculta la cabecera del módulo mientras el asistente está abierto.
- **Celular (<768):** la página se desplaza entera y el pie queda pegado abajo (como Adicionales).

**Verificado** en Chrome sin pantalla con un arnés del marco real (menú, barra, pestañas) y datos
simulados: 0 px de scroll en todos los pasos en 1366×657, 1366×768, 1536×730, 1280×600, 1024×700,
800×700 y 1920×950; 34 comprobaciones de comportamiento (agregar, quitar, duplicar, validar y saltar
al error, altas, cancelar, edición con candado y mapa de series). Thymeleaf renderiza. **No probado en
vivo** ni con datos reales.

**Arreglos del `revisor` (20-oct):**
1. **XSS (grave):** los `title` nuevos del listado y del resumen se armaban con `esc`, que no
   escapa comillas: un donante `ACME" ONCLICK="…` (la carga en octal sobrevive a las mayúsculas)
   metía un `onclick` que corría al abrir el acta un administrador. Ahora usan `escAttr`
   (convierte `"` y `'`). Regla: **dentro de un atributo, `escAttr` o `.attr()`; nunca `esc` solo.**
2. Pulsar una ficha la recreaba y el foco caía en `<body>` (Ctrl+Enter y Alt+N dejaban de andar):
   `pintarChips` devuelve el foco a la ficha activa.
3. Paso 4 en tablet/celular: el pie no mostraba todos los totales; se agregó `.dn-resumen-totales`
   (bienes, activos que se crean, valor) debajo de las tarjetas por debajo de 992 px.
4. (Del revisor de Adicionales, mismo atajo, venía de antes) Ctrl+Enter con un select2 enfocado
   abría la lista y avanzaba; otro Ctrl+Enter elegía la opción resaltada sin verla. Ahora el atajo
   no actúa con un select2 enfocado o abierto, y al cambiar de paso el foco queda en el paso nuevo.

## Riesgo abierto (explicado al usuario)

**Una oficina o un responsable «pendiente» por días puede chocar con uno creado directo en el VSIAF.**
El código lo propone el SCIAF (siguiente libre en el SCIAF). Si mientras tanto alguien crea en el
VSIAF otra oficina en ese predio, o un responsable en esa oficina, el VSIAF puede darle el mismo
número. La sincronización (cada 20 s) empareja por esa clave y pisaría el nombre de la oficina, o la
persona del responsable, con lo del VSIAF. Mitigación hoy: subir los pendientes pronto (los avisos
llegan a los administradores). Arreglo posible, sin hacer: que la sincronización no pise un registro
`pendienteDbf` cuyo nombre o C.I. no coincide y lo avise como conflicto.

## Pendiente / ofrecer al usuario

1. Hecho en la 2ª tanda: anular acta, etiquetas, distintivo, altas rápidas con pendiente.
2. **Mover donaciones** (transferir/asignar) sigue mandando al VSIAF la oficina y el responsable
   destino si estaban pendientes (`asegurarEnVsiaf`), aunque el bien no viaje. Es ruido, no daño.
3. Etiquetas: no hay «empezar desde la fila N» para hojas de etiquetas ya usadas.
4. **Subirlas al VSIAF en el futuro:** el VSIAF no acepta la D; haría falta reasignarles un
   código de la serie normal con un flujo propio.
5. Al **modificar** una donación desde Registro de activos, el formulario sigue poniendo el
   estado de conservación en 1 (comportamiento previo del formulario, igual para todos).

## Verificación de solo lectura tras el primer arranque (pasarle el SQL al usuario)

```sql
select count(*) from donacion_acta;
select column_name from information_schema.columns where table_name='activo' and column_name='procedencia';
select codigo, descripcion from opcion_menu where codigo = 'opcion_donacion';
-- columnas nuevas de la 2ª tanda
select column_name from information_schema.columns
 where table_name = 'donacion_acta' and column_name in ('token_registro','motivo_anulacion','fecha_anulacion','usuario_anulacion');
```

## Documentos Word (22-oct-2026)

El acta se descarga como `.docx` desde `GET /administracion/donacion/api/acta/{id}/word`,
generada desde los datos guardados por `WordActaDonacionService`. Las etiquetas QR también se
descargan como `.docx` desde su ruta existente; conservan el texto QR que lee la app móvil.
