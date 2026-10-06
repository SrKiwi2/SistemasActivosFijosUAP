# Handoff — Migración de pantallas a la plantilla (sesiones del 03 al 06-oct-2026)

> **Para el agente que retoma:** lee este archivo completo, después `docs/PLANTILLA_MODULOS.md`
> (la guía técnica) y `CLAUDE.md`. Lo más reciente está en la sección 3, «06-oct». El estado
> real de git está en la sección 0. Si algo de aquí contradice al código, manda el código:
> verifícalo antes de actuar.

Este documento es el contexto para continuar en otra máquina. Lo que vivía solo en la memoria
local del agente está resumido al final («Contexto que no está en el código»). La guía técnica
de la plantilla es **`docs/PLANTILLA_MODULOS.md`**: léela antes de tocar una pantalla.

---

## 0. Estado de git (06-oct-2026, 17:30)

- **Commiteado** (lo commitea el usuario, no el agente): toda la migración del 03 al 05-oct
  (`872ea44`, `ffc6243`, `88f7db6`, `e338d6b`, `3aef829`, `3981f3b`) y, el 06-oct, `25fc66d`.
  Ese commit incluye el arreglo de personas duplicadas en Faltantes, el registro guiado y el
  arreglo del scroll de los modales en `sciaf-modulo.css`. Donde más abajo diga «sin
  commitear» para trabajo del 05-oct, ya no es así.
- **Sin commitear:**
  - el **filtro por oficina** del registro de faltantes (`controlActivos/faltantes.html`);
  - la nota en `docs/HANDOFF_CUSTODIA_FALTANTES.md`;
  - este archivo y `docs/PLANTILLA_MODULOS.md`.
- **Nunca commitear:**
  - `application.properties`: es la config real de producción (`legacy.dbf.write.mode=cola`);
  - `application-dev.properties` y `.env`: perfil local;
  - `AGENTS.md` y `tools/Instalar-Worker-Tarea.ps1`.
- **Commit y push solo si el usuario lo pide.** Su despliegue es commit → push → `git pull` en
  producción → reiniciar: cada commit puede llegar a producción.
- **Dos carpetas de documentos:** `docs/` (los handoffs) y `DOCS/`, en mayúsculas, con
  `analisis-duplicado-richard-rojas.md`, que escribió otro asistente. Su sección 5.1 propone un
  `DELETE FROM persona`: **no seguirla**, ver la sección 3 «06-oct».

---

## 1. Qué se está haciendo

Migrar las pantallas del SCIAF, **módulo por módulo**, a una plantilla visual y de
comportamiento común. Esa plantilla nació con Rol, a pedido del usuario. Lo que pidió:
- cabecera compacta;
- tarjeta con relieve, botones 3D y tabla con sombra;
- sin filtros por columna;
- todo dentro de la pantalla sin scroll («Ajustar a pantalla»);
- contenido algo más grande;
- carga eficiente.

En cada módulo, además:
- se corrigen los **bugs** que aparezcan, explicándoselos al usuario en detalle;
- se **quita el código muerto**: endpoints, plantillas y funciones que nada usa. Antes de
  borrar, se verifica buscando la URL en templates, JS, Java y `mobile/`.

### Forma de trabajar que pide el usuario (respetarla)
- **Uno por uno.** Terminas un módulo, le explicas qué cambió y qué bugs había, y **esperas su
  orden** para el siguiente. Él elige cuál sigue.
- Responde siempre en **español**. Explica los bugs con el escenario concreto de cómo fallan.
- **MAYÚSCULAS:** los campos se ven y se registran en mayúsculas.
  - Excepciones: usuario de acceso, contraseñas, correo, URL, textos del menú lateral, y
    textos libres largos como el mensaje de un comunicado o la observación de una transferencia.
  - Se aplica en la plantilla (`SciafModulo.formulario` lo hace solo) **y** en el controlador.
- En pantallas críticas (Activos, Pendientes, Transferencia, Asignación) pidió **«solo lo
  visual, que no falle nada»**. Ahí se cambia solo la cáscara: cabecera, colores, alto y
  preloader. Su lógica no se toca, salvo bugs, que se explican.
- **No hacer commit** salvo que lo pida. **No escribir datos de prueba en la base**: la base
  local ES la de producción.
- Al terminar cada módulo: compilar y lanzar el subagente **`revisor`** sobre el diff. Ha
  encontrado fallos graves en casi todos los módulos; aplicar sus hallazgos antes de cerrar.

---

## 2. Piezas de la plantilla (todas nuevas en esta sesión)

| Archivo | Qué es |
|---|---|
| `static/assets/css/sciaf-modulo.css` + `static/assets/js/sciaf-modulo.js` | Clases `sm-*` y la API `SciafModulo`: `tabla`, `formulario` (con `validar`, `confirmacion`, `interceptar`, `alGuardar`, `mayusculas`), `eliminar`, `accion`, `abrirFormulario`, `alGuardarModal`, `cargar`, `pedirJson`, `sincronizarVsiaf`, `estadoSync`, `escapar` |
| `static/assets/css/sciaf-precarga.css` + `static/assets/js/sciaf-precarga.js` | Preloader `SciafPrecarga`: el logo girando con textos al azar y el diagnóstico de la demora (red lenta, servidor ocupado, sin conexión), midiendo `GET /api/estado/ping`. Integrado en `sciaf-pestanas.js` y en `abrirFormulario` |
| `componet/VsiafDisponibilidad.java` | Pausa las tareas que tocan el VSIAF si el montaje no está, como en la laptop local. Propiedad `sciaf.vsiaf.modo` = `auto` / `desactivado` / `activo` |
| `docs/PLANTILLA_MODULOS.md` | Guía completa: reglas, carga eficiente, preloader, contrato JSON y tabla de módulos migrados con sus notas |

> **Versión portable para otros proyectos (06-oct):** `~/Documentos/RRHH KEVIN/SISTEMAS/PlantillaModulos/`
> (y `PlantillaModulos.zip` al lado), fuera de este repo. Trae los CSS y JS con nombres
> `plantilla-*` (publican `PlantillaModulo`/`PlantillaPrecarga` y también los nombres `Sciaf*`),
> logo, latido y mensajes del preloader configurables, modo oscuro de Bootstrap 5.3, un ejemplo
> que funciona sin servidor (`ejemplo/index.html`) y un esqueleto Spring + Thymeleaf. Su README
> dice qué cambió respecto de estos archivos: si se mejora la plantilla aquí, llevar el cambio allá.

> Despliegue: esos archivos ya están commiteados (`872ea44`, junto con las vistas migradas).
> `layout/head.html` y `layout/script.html` los cargan; si alguna vez se separan, las pantallas
> migradas fallan (`ReferenceError: SciafModulo / SciafPrecarga`).

---

## 3. Módulos migrados (todos compilan; ninguno probado en navegador)

Cada fila de la tabla «Módulos migrados» de `docs/PLANTILLA_MODULOS.md` tiene el detalle. En
resumen:

- **Usuarios y acceso:** Rol, Persona, Usuario, Responsables, Gestión de Menú.
  - **Persona:** al editarla, propaga el nombre y C.I. al VSIAF en todos sus responsables
    (`ResponsableGestionService.propagarPersona`), solo si la persona tenía un C.I. válido antes.
- **Comunicación:** Comunicados.
- **Supervisión:** Monitoreo de actividad, Autorizaciones, Usuarios conectados.
- **Clasificación contable:** Grupo contable, Auxiliar, Organismo financiador, Estado del
  activo, Responsable de entrega.
- **Ámbito geográfico:** Entidad, Municipio, Predio, Oficina.
  - **Predio:** tiene una acción nueva, **Configurar** (municipio + código del predio, que forman
    el prefijo del código de activo), solo para administradores.
  - **Códigos de municipio y predio:** solo letras y números, de 1 a 6. **El usuario debe
    confirmar ese máximo de 6.**
- **Administración de activos:**
  - **Registro de activos:** queda **solo el formulario**; la tabla se quitó a pedido del
    usuario. `cargarTabla()` queda vacía **a propósito**, ver el comentario en `activo/vista.html`.
  - **Pendientes:** solo lo visual.
- **Transferencias:** Transferencia de activos, solo lo visual. Las rutas «interna» y
  «externa» ya apuntan a la misma vista.
- **Asignar activos:** `activo/asignacionActivos.html`, solo lo visual más bugs.
- **Bajas e ingresos:** Baja de activos (`operaciones/baja/modulo.html`) e Ingreso de bienes
  ajenos (`operaciones/ingreso/modulo.html` + `seguimiento/ingreso/vista.html`).
- **Control por responsable:** Mapa de control (rehecho como mosaico) y Faltantes (cáscara + bugs).
- **Hojas de ruta:** Búsqueda y Seguimiento (`hojaRuta/seguimiento.html` + `hojaRuta/tabla.html`).
  La página de Recepción (`hojaRuta/vista.html`) **no** se migró, por decisión del usuario: solo
  se le corrigieron bugs.

### Lo último (06-oct): Faltantes — registro de faltantes (modal `#cf-reg`)

Tres pedidos del usuario sobre el modal «Registrar faltantes» de `controlActivos/faltantes.html`.
Compila. Se probó con un navegador simulado (jsdom, 30 comprobaciones) y con capturas de Chrome
sin pantalla usando el CSS real. **No se probó en vivo**: el usuario lo prueba solo con la
**Vista previa**, porque «Registrar» mueve bienes en el VSIAF real.

**1. La misma persona aparece dos veces (caso «Richard Rojas López», uno con C.I. y otro sin).**
- **Causa:** la sincronización de RESP.DBF (`ResponsableController.syncFromMounted`, l. 543-608).
  Con C.I. busca la persona solo por C.I. y, si no la encuentra, crea otra. Sin C.I. la busca por
  nombre. Si en el VSIAF el funcionario tiene filas con y sin C.I., quedan **dos `persona`**.
  - Además `esCiValido` solo acepta dígitos: un «4567890 LP» se trata como «sin C.I.».
  - Y la caché por nombre se pisa entre homónimos.
- **Consecuencia que vio ING. Saul (encargado de Activos Fijos):** el alta automática de custodia
  busca por `id_persona` y habría dado de alta a «los dos Richard» en la oficina de faltantes.
- **Decisión (usuario + Saul): arreglo temporal y manual en el registro.** No se tocan la base
  ni la sincronización. La notificación sale **a nombre del registro con C.I.**
- **Servidor** (detalle en `docs/HANDOFF_CUSTODIA_FALTANTES.md`, «Arreglo temporal»):
  - `RegistrarFaltantesRequest` suma `idsPersonasVinculadas` y `destinos` (por predio:
    `idResponsableCustodia` o `crearNuevo`).
  - `CustodiaFaltantesService` suma `opciones`, `resolverDestino`, la barrera
    `exigirSinHomonimo` y `asegurar(…, idPersonaDestino, permitirHomonimo, …)`.
  - `EnvioCustodiaService` agrupa por la persona del acta y salta los faltantes que ya traen
    custodia elegida.
  - La vista Faltantes agrupa por `coalesce(acta.id_persona, responsable.id_persona)`.
  - Endpoint nuevo: `GET /administracion/control-activos/custodia/destinos-custodia`.
  - El `revisor` pasó. Se aplicaron sus hallazgos: nunca preseleccionar a un homónimo, adoptar
    la oficina «FALTANTES…» sin marcar y validar en el servidor que los nombres se parezcan.

**2. «Que lo entienda cualquiera»: registro guiado por pasos.**
- Pasos numerados: 1 Persona · 2 Marque los bienes que faltan · 3 ¿A quién se le entregan los
  bienes en la oficina de faltantes? · 4 Datos de la notificación.
- Al elegir a la persona, el sistema **busca solo** otro registro con el mismo nombre y
  pregunta: «Esta persona parece estar registrada dos veces… ¿Es la misma persona? [Sí, incluir
  también sus bienes] [No, es otra persona]». Con C.I. distintos no se sugiere.
- Paso 3 con tarjetas:
  - verde o azul con «No necesita hacer nada» cuando no hay decisión;
  - ámbar con opciones de un clic («Sí, es la misma persona» / «No, es otra persona») si hay un
    homónimo;
  - rojo si falta responder; «Registrar» lleva a la tarjeta.
  - El caso raro («ya está con el nombre escrito distinto») queda escondido detrás de un enlace.
- Si cambian las personas juntadas, el paso 3 **se vuelve a preguntar**. Así una respuesta vieja
  no apunta a un homónimo: lo encontró la prueba.
- CSS propio en el `<style>` de la vista: `.cf-paso-tit`, `.cf-paso-num`, `.cf-ayuda`,
  `.cf-tarjeta` (`.cf-ok`, `.cf-info`, `.cf-atencion`, `.cf-falta`), `.cf-opcion`.
  **Patrón reutilizable** para otros formularios con decisiones; ver `PLANTILLA_MODULOS.md`,
  «Modales con decisiones».

**3. Filtro por oficina en el paso 2 (sin commitear).**
- Un select2 (`#cf-reg-f-oficina`) con las oficinas de los bienes cargados: agrupadas por
  predio, con su cantidad de bienes, y solo si hay más de una.
- Se escribe el código (`12` = `012`), el nombre o el predio. El `matcher` propio ignora el
  «· N bien(es)» del texto.
- Lo marcado en otras oficinas sigue elegido y el contador lo dice: «· N en otras oficinas».

**4. Bug de la plantilla, global: los botones del pie de un modal largo no se veían.**
- `modal-dialog-scrollable` de Bootstrap solo da scroll a `.modal-body`. `.sm-modal-cuerpo` no
  lo tenía, y `.sm-modal` (`overflow: hidden`) recortaba el pie.
- **Arreglado en `sciaf-modulo.css`** con
  `.modal-dialog-scrollable .sm-modal-cuerpo { flex:1 1 auto; min-height:0; overflow-y:auto; }`.
- Afecta para bien a todos los modales con ese patrón: `controlActivos/mapa.html`,
  `menu/vista.html`, `usuario/vista.html` y `operaciones/ingreso/modulo.html`. **No se
  revisaron uno por uno:** conviene mirarlos.

### Antes (05-oct, madrugada): Faltantes — notificaciones por persona
Pedido del usuario: ver el código de responsable, botones claros (imprimir la notificación
vigente, emitir reiterativa con plazo nuevo, cambiar plazo) y un contador de plazo visible.
Decisiones del usuario: las acciones son **por notificación** (una por persona con N bienes), no
por bien; al **cambiar el plazo corre desde el día del cambio** (el papel sale con esa fecha, mismo
número y bienes). Sin commitear; compila, renderiza, PDF de reiterativa generado offline.
- **Pantalla:** cada persona muestra sus códigos de responsable y una tarjeta por notificación
  vigente (siempre a la vista): N°, tipo (1ª/2ª reiterativa), pendientes, contador en días
  hábiles y botones Imprimir · Emitir reiterativa · Cambiar plazo · ⋯ Corregir datos. Las filas
  quedan con Resolver y Notificar falta.
- **Servidor (`ActaFaltanteService`):** cambiar plazo y corregir datos re-emiten el mismo
  documento con la MISMA foto de bienes (`contenidoReemitido`); la reiterativa es un documento
  nuevo con todos los pendientes, pide plazo, conserva la oficina de origen y pasa a ser la
  vigente; `notificacion.inicioPlazo` en el JSON dice desde cuándo corre el plazo; todo bajo el
  turno de notificaciones y con los faltantes bloqueados; anular una reiterativa devuelve sus
  faltantes a la anterior; una notificación reiterada no se anula y su QR dice «reiterada».
- **PDF:** REF con el tipo («PRIMERA NOTIFICACIÓN REITERATIVA…») y párrafo «En atención a la
  NOT… de fecha …, se le reitera…»; la fecha del papel es la del inicio del plazo.
- **Bugs que había:** cambiar plazo/regenerar reescribían el documento con UN solo bien; la
  reiterativa era por bien y no pedía plazo; el PDF de la reiterativa no decía que lo era; la 2ª
  reiterativa era imposible desde la pantalla (faltaba numeroReiterativa en FaltanteDTO); el
  contador contaba desde el traslado a custodia; cambiar plazo borraba documento/observación.
- **En producción todavía no hay ninguna notificación NOT-AF** (solo 4 AR y 1 AF): no hubo datos
  dañados.

### Antes (05-oct, noche): Control por responsable
Sin commitear ni probar en navegador con la app; compila, las plantillas renderizan y el mapa se
vio en Chrome headless con los datos reales (consulta de solo lectura). Revisor pasado y aplicado.
- **Decisión del usuario:** mantener las dos pantallas (Mapa detecta, Faltantes gestiona) y
  rehacer el Mapa como **mosaico de bloques** (elegido entre mosaico / mapa geográfico / solo
  mejorar los cuadros). Predios y oficinas NO tienen coordenadas: un mapa geográfico exigiría
  cargarlas.
- **Datos reales (05-oct):** 8 municipios, 14 predios, 857 oficinas (387 en Las Palmas), 31.403
  bienes, 1 solo levantamiento en la historia → casi todo «sin levantar»; por eso el color
  alternativo «concentración de bienes».
- **head.html** reemite `levantamiento-*` y `faltantes-custodia` como `sciaf:<evento>` y
  `sciaf:sse-estado`: el pendiente 4 de abajo quedó resuelto.

### Antes (05-oct, tarde): Hojas de ruta
Sin commitear y sin probar en navegador; compila (javac) y las plantillas renderizan con
Thymeleaf offline. El `revisor` pasó y sus tres hallazgos se aplicaron.
- **Alcance elegido por el usuario:** migrar Búsqueda y Seguimiento y corregir los bugs del
  controlador (comunes a las dos pantallas) y de la página de Recepción, sin cambiarle el diseño.
- **Seguimiento:** lista y detalle con trayectoria en la misma pantalla; llega con la vista;
  solo ADMINISTRADOR / SUPER USUARIO (controlador → `supervision/sin_permiso`).
- **Permisos del controlador:** leen RECEPCION + administrativos (`puedeLeer`); escriben
  RECEPCION + ADMINISTRADOR (`puedeRegistrar`). Antes `/listar` respondía sin sesión.
- **Datos:** listado sin filas repetidas y sin N+1 (dos consultas); estado actual = primer
  movimiento por fecha, hora, id (también en `/seguimiento-hr` público); código sin distinguir
  mayúsculas; altas bajo un turno `pg_advisory_xact_lock` (doble clic ya no duplica).
- **Recepción:** XSS, fecha de mañana desde las 20:00, modal nuevo sin fecha, abre por id.
- **Código muerto quitado:** `hojaRuta/formulario.html` (vacío), el contenido basura de
  `hojaRuta/tabla.html` (ahora es el fragmento) y los métodos sin uso de los servicios y DAO de
  hoja de ruta, movimiento, solicitante y unidad.

### Antes (05-oct): Bajas e Ingresos
**Bajas e Ingresos.**
- **Vista de bajas borrada:** `seguimiento/baja/vista.html` estaba rota (otro modelo de bajas,
  endpoints inexistentes). La ruta `/administracion/baja/vista` ahora abre el módulo.
- **Registro de baja (`/baja/registro`):** ahora exige sesión y permiso de menú (`opcion_baja_modulo`
  o `opcion_ba`), rechaza una segunda baja del mismo activo (409) y es transaccional, con rollback
  dentro del `catch`.
- **Registro de ingreso (`/ingreso/registrar`):** sesión y permiso (`opcion_ingreso_modulo`), la
  fecha de retiro (+3 meses) la calcula el servidor y se usa en el PDF, y rollback dentro del `catch`.
- **Seguimiento de ingresos:** los colores pasaron de `:root` a `.ia-pantalla`; las fechas ya no
  salen un día antes; el filtro de responsable usa select2 contra `/api/responsables/buscar`.

---

## 4. Pendientes y decisiones abiertas (ofrecidas al usuario, sin respuesta todavía)

1. **Asignar activos:** hoy reasigna **todos** los activos del responsable origen. Se ofreció
   agregar casillas para elegir algunos. Esperando decisión.
2. **Bajas:**
   - **Índice único:** falta un índice único en `baja_activo(id_activo)` para cerrar la doble
     baja simultánea. Es DDL en producción: solo con permiso explícito.
   - **Baja solo en el SCIAF:** la baja deja el activo en BAJA en el SCIAF, pero no toca el VSIAF.
     Allá se desaprueba aparte. Es así por diseño.
3. **Ingreso:** el firmante del comprobante está fijo en el HTML (`LIC. VERONICA LAYME CORI`).
   Se ofreció pasarlo a la configuración.
4. **Control de Activos:**
   - ~~EventSource propio en Mapa y Faltantes~~: resuelto el 05-oct (head.html reemite).
   - **Índice único** parcial `inventario(id_oficina) where estado_levantamiento='EN_EJECUCION'`:
     cerraría de verdad la doble apertura simultánea de un levantamiento (hoy solo lo evita la
     pantalla). DDL en producción: solo con permiso.
   - **Feriados:** el contador de plazo cuenta lunes a viernes; no descuenta feriados.
5. **`SyncOrchestrator.despacharSync`:** no tiene handler para entidad, predio, grupoContable ni
   organismoFinanciero, y en producción loguea «Sin handler de sync».
6. **Transferencia Londra:** el usuario dijo «dejemos ese módulo» por ahora.
7. **Pantallas que faltan migrar,** según `OpcionMenuSeeder`. El usuario elige el orden.
   - Movimientos › Asignaciones, Transferencias.
   - Historial › Historial activo, Seguimiento de activo (ruta), Historial de transferencias.
   - Consulta › Buscar/Filtrar activos, Reporte de asignaciones.
   - Conciliación › BD↔VSIAF, Revisión de correlativos.
   - Transferencia Londra.
8. **Personas duplicadas (solución de fondo, propuesta y no hecha).** El arreglo del 06-oct es
   temporal. Lo propuesto al usuario, sin respuesta todavía:
   - medir cuántos casos hay con la consulta de nombres repetidos que está en el chat y en
     `DOCS/analisis-duplicado-richard-rojas.md`, sección 4;
   - corregir la sincronización: normalizar el C.I. con extensión o complemento y, si una fila
     con C.I. coincide con **una única** persona sin C.I. del mismo nombre, completarle el C.I.
     en vez de crear otra. Es lo que ya hace el alta manual en
     `ResponsableAltaService` l. 135-141;
   - una pantalla «Unificar personas» (solo administradores): mueve los responsables, marca la
     persona sobrante como FUSIONADA, **sin borrar**, y opcionalmente completa el C.I. en el
     VSIAF con `propagarPersona`. Cuidado con `uk_resp_custodia_persona`.
   - Pendientes menores del arreglo temporal:
     - el reporte consolidado por persona y el contador del buscador siguen agrupando por la
       persona del responsable;
     - el permiso «Es otra persona» (`crearNuevo`) solo vale en el primer intento.
9. **Hojas de ruta:**
   - **Duplicados viejos:** no hay índice único en `hoja_rutas (tipo, upper(codigo), gestion)`.
     El turno cierra los nuevos, pero si ya hay repetidas en producción, el buscador manual de
     Recepción abre la primera; desde la tabla se abre la exacta, por id. Revisar con una
     consulta de solo lectura antes de ofrecer el índice (DDL, solo con permiso).
   - **Página de Recepción:** sigue con su diseño propio (`style-hoja-ruta.css`,
     boxicons, CDN). Migrarla a la plantilla queda ofrecido.

---

## 5. Trampas que ya costaron tiempo (léelas)

- **Thymeleaf y los corchetes:** dos corchetes juntos (`[[` o `]]`) dentro de `<script>` son
  sintaxis de inlining y rompen la pantalla.
  - Escribir los arreglos anidados con espacios: `[ [0, 'asc'] ]`.
  - Tampoco poner `[[` en comentarios.
- **Pestañas (`sciaf-pestanas.js`):** al cambiar de pestaña, el DOM de la anterior se desprende,
  pero las **funciones globales y los ids se repiten** entre módulos.
  - Cada vista va en una IIFE con `$raiz` y sin globales.
  - Nunca buscar `$('#tablaRegistro')` en todo el documento: varias pantallas usan ese id. Tomar
    la referencia al cargar la vista.
- **Colores en `:root` y reglas sin prefijo:** las pantallas con diseño propio (tf-, aa-, ia-)
  definían sus colores en `:root` y tenían reglas globales de Select2, que cambiaban todo el
  sistema mientras estaban abiertas.
  - Acotarlas a su contenedor, por ejemplo `.aa-pantalla`, y mapear los colores a `--sm-*`.
  - Abrir sus desplegables con `dropdownParent` dentro de la pantalla.
- **Modales largos:** con `modal-dialog-scrollable`, el cuerpo es `.sm-modal-cuerpo` y no
  `.modal-body`. Desde el 06-oct `sciaf-modulo.css` lo resuelve. Si un modal vuelve a perder el
  pie, revisar que el cuerpo tenga esa clase y que nada le ponga `overflow: visible`.
- **select2 dentro de un modal:** siempre con `dropdownParent` del modal, por ejemplo
  `$('#cf-reg')`. Si no, el desplegable queda detrás del modal o no recibe el foco. Antes de
  rearmar las opciones, `select2('destroy')`.
- **Pruebas de pantalla sin tocar la base (se usó el 06-oct):**
  - **jsdom** en el scratchpad (`npm i jsdom@24`): carga el HTML de la vista, el jQuery y el
    select2 de `static/assets/vendor/libs`, y simula `$.ajax` y `fetch` con datos falsos.
    `SciafPrecarga` se simula con un objeto vacío.
  - **Ojo:** jsdom no calcula el diseño. `:visible` siempre da falso; usar
    `css('display') !== 'none'`.
  - Para ver el diseño: página estática con `core.css`, `theme-default.css` y
    `sciaf-modulo.css` por `file://`, más `google-chrome --headless=new --screenshot`.
- **Fechas `AAAA-MM-DD`:** `new Date('2026-03-01')` las lee en UTC, y en Bolivia (UTC-4) da el
  día anterior. `toISOString()` da la fecha de **mañana** después de las 20:00. Construir las
  fechas locales a mano.
- **Archivos con CRLF:** casi todo el repo usa CRLF.
  - **`sed -i` de Git Bash lo convierte a LF.** Para editar, usar la herramienta Edit o scripts
    Node que normalicen y restauren el CRLF.
  - Los scripts que reemplazan texto a veces fallan por espacios al final de las líneas:
    normalizar con `replace(/[ \t]+\n/g, '\n')`.
- **Escapado en la consola:** las regex y las barras invertidas dentro de `node -e "…"` se
  pierden. Escribir el script en un archivo del scratchpad.
- **Maven:** `./mvnw.cmd -o -q compile` tarda varios minutos en esta laptop. Correrlo en segundo
  plano y verificar con `grep -E "ERROR|FIN"` sobre el log, porque una salida vacía no garantiza
  éxito.
- **Compilar sin reiniciar la app:** si la app corre desde VS Code con devtools, compilar a
  `target/classes` la reinicia, y el arranque escribe en la base de producción. En Linux se
  compiló aparte con `./mvnw -o -q dependency:build-classpath -Dmdep.outputFile=cp.txt` y
  `javac -proc:full -d <scratchpad>/out -cp "$(cat cp.txt)" @fuentes.txt`. Las plantillas se
  validaron renderizándolas con `SpringTemplateEngine` + `FileTemplateResolver` y un modelo falso.
- **Hibernate 6:** validado en esta sesión que funcionan `CAST(x AS String)`, `CONCAT` de varios
  argumentos y `:param = -1` con `Long`. Evitar `upper(trim(?1))` en parámetros: falló al
  arrancar.
- **`GlobalExceptionHandler`:** `@ExceptionHandler(Exception.class)` convierte cualquier
  `ResponseStatusException` en 500. Para devolver otro código, poner un manejador local en el
  controlador, como en `ImportController`.
- **Seguridad:** casi todo es `permitAll()` (`/administracion/**`, `/api/**`, `/baja/**`,
  `/ingreso/**`, `/importe/**`…). **La autorización va en el servidor, en cada endpoint**:
  - `RolesSciaf.esAdministrativo(request)`;
  - o el permiso de menú: la sesión trae `opciones`, el Set de códigos de `opcion_menu`.
  - Ya se cerraron `/importe/**` (estaba abierto sin sesión), `/baja/registro`,
    `/ingreso/registrar` y `consultar-api-datos`, este último borrado.

---

## 6. Contexto que no está en el código (venía de la memoria local)

- **Máquinas.** Se trabaja en dos:
  - **la laptop Windows** de las sesiones del 03 al 05-oct: Git Bash, CRLF, `mvnw.cmd`;
  - **un equipo Linux** (06-oct):
    - repo en `~/Documentos/RRHH KEVIN/SISTEMAS/SistemasActivosFijosUAP`;
    - usar `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64`: el JDK 25 por defecto falla con
      Lombok;
    - la app suele estar **corriendo desde VS Code** en el 9696, con perfil `dev`
      (`application-dev.properties` lee las plantillas de `src/`; `.env` tiene
      `SPRING_PROFILES_ACTIVE=dev`);
    - para compilar sin reiniciarla, se copia `src`, `pom.xml`, `mvnw` y `.mvn` al scratchpad y
      se corre `./mvnw -o -q compile` ahí;
    - las plantillas y el CSS se ven al recargar con Ctrl+F5; los cambios en Java exigen
      reiniciar la app;
    - las lecturas directas a la base desde el agente (psql) fueron denegadas por los permisos:
      pasarle al usuario el SQL de solo lectura.
- **La laptop de desarrollo** usa la **base de producción** (`application.properties`: `bd_a3`,
  no `bd_a4`). No tiene los montajes del VSIAF (`/mnt/dbfwin`, `/mnt/vsiaf_transferencias`).
  - `VsiafDisponibilidad` pausa ahí las tareas DBF.
  - **No probar desde local nada que encole al VSIAF:** la orden falla y marca registros reales
    como pendientes o con error en producción.
- **Escritura al VSIAF:** `legacy.dbf.write.mode=cola`. El SCIAF deja órdenes JSON en `_cola/` y
  un worker PowerShell 32-bit con VFPOLEDB (`tools/Worker-Vsiaf.ps1`) las aplica. Ese worker es
  la tarea programada `Worker-Vsiaf` en la VM Windows del VSIAF y mantiene el índice `.CDX`.
  **Nunca escribir los DBF en crudo**: rompe el índice.
- **Topología:**
  - VSIAF oficial: VM Windows `172.16.21.4`, shares `dbfs` y `sayove`.
  - SCIAF de producción: Ubuntu `172.16.22.7`.
  - VSIAF de prueba: `172.16.22.3`.
  - Las credenciales las tiene el usuario; no van en el repo.
- **Antes de desplegar, verificar:** `write.mode=cola` y `spring.task.scheduling.pool.size=4`
  commiteados; `text/event-stream` NO en `server.compression.mime-types`.
- **Menú dinámico:** `opcion_menu` en árbol, más `usuario_opcion`. El `OpcionMenuSeeder` solo
  inserta (no actualiza filas existentes), y la sesión guarda `opciones`. Los permisos «puros»
  son ítems con `visible=false`, por ejemplo `opcion_activo_editar` y `opcion_activo_desaprobar`.
- **Correlativos:** la función `preview_codigo_activo_by_codes` en producción **sigue sin
  blindar**. El arreglo está en `scripts/sql/fix_generador_correlativo_5digitos.sql`, sin
  aplicar: el usuario es cauto con producción.
- **Otros handoffs del proyecto** (ver CLAUDE.md): `HANDOFF_CUSTODIA_FALTANTES.md`,
  `PLAN/HANDOFF_CONTROL_ACTIVOS.md`, `PLAN_APP_MOVIL.md`, `HANDOFF_USUARIOS_MENU_RUTA.md`.

---

## 7. Cómo retomar

1. `git pull` y leer este archivo, `docs/PLANTILLA_MODULOS.md` y `CLAUDE.md`.
2. Preguntarle al usuario cuál es el siguiente módulo, o si responde alguna de las decisiones
   de la sección 4.
3. Para cada módulo:
   - leer la vista, su fragmento, su formulario y su controlador completos;
   - buscar quién más usa sus endpoints;
   - migrar con el patrón del módulo más parecido: Organismo/Entidad para catálogos del VSIAF,
     Estado del activo/Municipio para un ABM propio, Responsables/Oficinas para tablas paginadas
     en el servidor con VSIAF, Transferencia/Asignación para pantallas propias de solo lo visual;
   - compilar, pasar el `revisor`, corregir;
   - actualizar `docs/PLANTILLA_MODULOS.md` y **este handoff**.
4. Si el usuario retoma Faltantes: lo pendiente es que pruebe en vivo con la Vista previa y que
   decida sobre la solución de fondo de las personas duplicadas (sección 4, punto 8).
   `docs/HANDOFF_CUSTODIA_FALTANTES.md` tiene el detalle del servidor.
