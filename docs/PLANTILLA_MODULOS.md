# Plantilla de pantallas de mantenimiento (CRUD)

Referencia: **`templates/rol/`** es el primer módulo migrado. Para migrar otro, copiar sus
tres archivos y adaptar.

## Piezas globales (ya cargadas en `layout/head.html` y `layout/script.html`)

| Archivo | Qué aporta |
|---|---|
| `static/assets/css/sciaf-modulo.css` | Clases `sm-*`: cabecera compacta, tarjeta con relieve, barra de búsqueda, tabla, botones 3D, modal de formulario. Modo claro y oscuro. |
| `static/assets/js/sciaf-modulo.js` | `SciafModulo.tabla()`, `.formulario()`, `.eliminar()`, `.abrirModal()`, `.escapar()` |
| `static/assets/css/sciaf-precarga.css` + `js/sciaf-precarga.js` | `SciafPrecarga`: el preloader del sistema (logo girando, mensajes al azar) dibujado dentro de lo que carga, con diagnóstico de la demora. Ver «Preloader». |

## Regla de oro: un módulo encapsulado (pestañas)

El sistema mantiene varios módulos abiertos en pestañas (`sciaf-pestanas.js`): la pestaña
inactiva se **desprende** del DOM, pero sus funciones globales siguen vivas. Si dos módulos
definen `cargarTabla`, `cargarFormularioEdit` o `eliminar` globales, gana el último cargado y
los botones del otro abren o recargan cosas ajenas. Por eso cada vista:

- envuelve todo en `<div class="sm-pagina" id="modXxx">` y el script en una IIFE, **sin globales**;
- busca todo dentro de su raíz (`$raiz.find('.js-tabla')`…) y se lo pasa como elemento a
  `SciafModulo.tabla()` (una recarga por SSE de una pestaña oculta no toca la visible);
- delega los eventos en la raíz (`$raiz.on('click', '.btn-editar-xxx', …)`), no en `document`;
- declara qué recargar al guardar con `SciafModulo.alGuardarModal($modal, cargarTabla)`; el
  formulario no llama a ningún `cargarTabla` global.

Excepción: un menú desplegable que se lleva al `<body>` (Usuario, «Más acciones») queda fuera
de la raíz; sus acciones se escuchan en `document` con una clase propia del módulo (`usr-accion`).

## Estructura de un módulo

- **`vista.html`**: `.sm-cabecera` (migas, título, subtítulo, totales `.sm-dato`, botón principal)
  + `.sm-card` con `.sm-toolbar` (buscador `.sm-buscador`, selector de filas) y el contenedor
  `.js-tabla`. La barra vive aquí, no en el fragmento: así no se pierde lo escrito al recargar.
- **`tabla_registro.html`**: solo `<table class="table sm-tabla align-middle" id="data-table">`.
  Botones de fila: `sm-btn sm-btn-sm sm-btn-suave` (editar) y `… sm-btn-peligro` (eliminar).
  **No usar** filas de filtros por columna en el `<thead>`: se reemplazan por el buscador
  general y, si hace falta, un `<select>` en la barra conectado con `filtros: [{selector, columna}]`.
- **`formulario.html`**: `.sm-modal-cab` / `.sm-modal-cuerpo` con `.sm-seccion` / `.sm-modal-pie`.
  Campos: `.sm-label` + `.sm-campo` (ícono + `input.sm-input` + `.invalid-feedback`).
  El modal en la vista: `<div class="modal-content sm-modal">`.

## Variante: tabla paginada en el servidor (muchos registros)

Referencia: **`templates/persona/vista.html`**. Sin fragmento `tabla_registro`: el `<table>`
(solo `<thead>`) va en la vista y se pasa `dt: { serverSide: true, ordering: false, ajax, columns }`
a `SciafModulo.tabla()`. La recarga es `api.ajax.reload(null, false)` (expuesta como
`window.cargarTabla`). En los `render` de las columnas **escapar siempre** con
`SciafModulo.escapar()`: los datos llegan crudos del JSON. El endpoint devuelve el formato de
DataTables (`draw`, `recordsTotal`, `recordsFiltered`, `data`).

## Mayúsculas (requisito)

Los datos se **ven y se registran en MAYÚSCULAS**. `SciafModulo.formulario()` convierte los
campos de texto mientras se escribe (y lo que ya traía el registro al editar); el CSS los
muestra así. Excluir con `data-sm-mayus="no"` lo que no debe cambiar: correo, usuario de
acceso, contraseñas (los `type=password/email` ya quedan fuera). En la tabla, `.sm-nombre` y
`.sm-mayus` muestran en mayúsculas los datos viejos. **El controlador también debe pasar a
mayúsculas** al guardar (es la garantía real; ver `PersonaController.limpiar`).

## Trámites propios al guardar (`interceptar`)

Si el servidor responde algo que no es «guardado» ni «error» (pedir autorización, confirmar personas
parecidas…), `SciafModulo.formulario({ interceptar(res, api) { … } })` lo resuelve el módulo: devuelve
`true` y luego llama a `api.reenviar(url, extras)` (vuelve a enviar el formulario, con otra URL o
parámetros extra) o a `api.cancelar()`. Ejemplo: `templates/responsable/formulario.html`.

## Confirmación antes de guardar

`SciafModulo.formulario()` pregunta "¿Registrar los datos?" / "¿Guardar los cambios?" con
`sciafConfirmarEnvio` (fragment.js), igual que `manejarEnvioFormulario` en el resto del
sistema. Se desactiva con `confirmar: false` solo si hay una razón.

## Ojo con Thymeleaf en los `<script>`

Los dobles corchetes de apertura y de cierre son expresiones en línea de Thymeleaf, también
dentro de `<script>` **e incluso dentro de comentarios** de JavaScript, y pueden abarcar varias
líneas. Un arreglo anidado como `orden` o `extras` rompe la pantalla ("Could not parse as
expression"): escribirlo siempre con espacios, `[ [0, 'asc'] ]`, y no escribir los dobles
corchetes literales ni en comentarios.

## Carga eficiente (aplicar en cada módulo)

La base está en un servidor remoto: lo que más se nota no es el cálculo sino **la cantidad de
viajes** (navegador ↔ servidor y servidor ↔ base). Reglas:

1. **Un solo pedido por acción.** No preguntar `/adm/cargar-datos` antes de cada cosa:
   `sciaf-presencia.js` mantiene viva la sesión y `sciaf-sesion.js` detecta en *cualquier*
   respuesta que se perdió (401 o redirección al ingreso) y avisa sin perder lo que hay en
   pantalla. Usar `SciafModulo.abrirFormulario`, `.accion`, `.eliminar`, `.pedirJson`, `.cargar`
   (no `cargarFormularioAlert`, `eliminarRegistroAlert` ni `manejarEnvioFormulario` de fragment.js).
2. **La vista llega con los datos** cuando son pocos (Gestión de Menú: `th:replace … :: arbol`),
   en vez de vista vacía + segundo pedido. Para tablas grandes, paginar en el servidor (Persona).
3. **Sin loader de pantalla completa** (`showGlobalLoader`): al abrir, el preloader va dentro
   del área que carga (ver «Preloader»); al recargar, la barra fina de la tarjeta
   (`.sm-procesando`) y la tabla atenuada; la pantalla no salta.
4. **Una recarga, no dos:** si una acción propia también dispara un aviso SSE del mismo
   cambio, juntar ambas con un retardo corto (ver `programarRecarga` en menu/vista.html).
5. **Conservar el estado al recargar:** búsqueda, página, orden, grupos contraídos, scroll.
6. **Trabajo pesado del navegador solo cuando hace falta** y una vez por página (p. ej. la
   lista de íconos de Tabler o las rutas del sistema en el formulario de menú).
7. **En el servidor:** pocas consultas por pedido (traer todo de una vez y armar en memoria,
   contar con un `GROUP BY` en vez de una consulta por fila), sin N+1.
8. **Estilos del módulo en la vista**, no en el fragmento que se recarga.
9. **Datos iniciales dentro de la vista** cuando la pantalla los pide por JSON: `model.addAttribute("inicial", …)`
   y en el script `let datos = /*[[${inicial}]]*/ …` (con `th:inline="javascript"`).
10. **Consultas periódicas solo con la pantalla a la vista** (`document.visibilityState` y que la raíz esté en
    el DOM) y **redibujar solo lo que cambió** (comparar una "firma" por elemento).

## Preloader (`SciafPrecarga`)

El loader de `inicio-admin.html` (logo girando, barra, mensajes al azar) como componente que se
dibuja **dentro** de lo que carga —la pestaña, una tarjeta, un modal— y no tapa el resto del
sistema. A los 2,5 s mide la ida y vuelta a `GET /api/estado/ping` (no toca base ni disco) y
explica la demora:

| Diagnóstico | Qué significa |
|---|---|
| La red responde bien (N ms) | Es el servidor armando muchos datos. Pasados 15 s: «está tardando más de lo normal». |
| Conexión lenta | Ida y vuelta > 900 ms (o el navegador informa 2G). |
| Sin conexión | El navegador no tiene red: la pantalla se vuelve a pedir sola al volver. |
| El servidor no responde | El latido no contestó en 5 s: reiniciándose o muy ocupado. |

Nunca da por fallida una carga que sigue en curso: el error aparece solo si el pedido falló, con
el motivo (`SciafPrecarga.motivo(status)`) y **Reintentar**. A los 15 s ofrece Reintentar igual.

Dónde ya está, sin hacer nada en el módulo:
- **Cada pestaña** (`sciaf-pestanas.js`): toda pantalla abierta desde el menú. Un solo pedido
  (ya no `/adm/cargar-datos` antes).
- **Formularios en modal** (`SciafModulo.abrirFormulario` / `abrirModal`), en versión compacta.

Para una tabla grande que conviene pedir aparte (la vista abre al instante y la tabla llega
después, como **Auxiliar**):

```html
<div class="js-tabla"></div>   <!-- vacía: sin th:replace del fragmento -->
```
```js
function primeraCarga() {
    SciafPrecarga.durante($cont, SciafModulo.cargar($cont, URL + '/tabla-registros'), {
        texto: 'Cargando auxiliares…',
        tituloError: 'No se pudo cargar la tabla',
        alReintentar: primeraCarga
    }).then(iniciarTabla).catch(() => { /* el error ya quedó en la tarjeta */ });
}
primeraCarga();
```

API: `SciafPrecarga.montar(contenedor, { titulo, texto, compacta, alReintentar, alInicio })` →
`{ cerrar(), error(titulo, detalle, { alReintentar }) }`; `.durante(contenedor, promesa, opciones)`;
`.cerrarEn(contenedor)`; `.diagnosticar()`. Las recargas (guardar, SSE) siguen con la barra fina
de la tarjeta, no con el preloader.

**Cuándo pedir la tabla aparte:** si la vista con la tabla tarda más de ~1 s (el controlador
de Auxiliar deja en el log `[AUXILIAR] Tabla lenta…` con filas, tiempo de consulta y total).
Catálogos chicos (grupo contable, organismo, estado) siguen llegando con la vista: un pedido.

## Contrato con el controlador

Guardar y eliminar responden **JSON `{ ok: boolean, msg: string }`** (ver `RolController`,
`PersonaController`). `SciafModulo.formulario()` y `.eliminar()` cuentan con eso; un texto
plano se toma como error.

Al **modificar**, cargar el registro guardado y copiarle solo los campos del formulario. Guardar
el objeto que llega del formulario borra lo que el formulario no muestra (en Persona se perdían
correo, extensión, nacionalidad, género y la auditoría de registro).

## Módulos migrados

| Módulo | Tabla | Notas |
|---|---|---|
| Rol | navegador | nombres en mayúsculas; columna de usuarios asignados |
| Persona | servidor | columna de vínculos (usuario / responsable) y aviso al eliminar; al editar, el nombre del perfil se actualiza en vivo (`SesionPermisosService.personaCambio`) |
| Usuario | navegador | filtro segmentado con contadores (`.sm-segmento`, `filtroFila`), menú «Más acciones», permisos; usuario y contraseña sin mayúsculas; alta de persona en el mismo formulario; mensaje de credenciales para copiar |
| Comunicados | navegador | llega con la vista; filtro por tipo y "sin completar"; acuse de lectura EN VIVO (SSE `comunicado` al emisor → recarga agrupada); detalle sin N+1 (`destinatariosConPersona`); título en mayúsculas, mensaje y URL no |
| Supervisión › Monitoreo de actividad | servidor | 1ª página + resumen llegan con la vista (`${inicial}`, ajax como función con caché de la 1ª página); refresco en vivo trae página + resumen en una respuesta (`resumen=true`); período Hoy/7/30 días; clic en "más activos" filtra |
| Supervisión › Autorizaciones | tarjetas + tabla | listado llega con la vista; Pendientes/Historial en segmento; tarjetas `.sm-tarjeta`; sin N+1 al listar super usuarios (`listarParaGestion`) |
| Supervisión › Usuarios conectados | tarjetas | sesiones llegan con la vista; consulta cada 5 s SOLO con la pantalla visible; redibuja solo la tarjeta que cambió (firma) y actualiza los "hace X" en el lugar |
| Clasificación › Grupo contable | navegador | catálogo del VSIAF de **solo consulta** + sincronizar (`SciafModulo.sincronizarVsiaf` / `estadoSync`); el alta directa a CODCONT.DBF (escribía el DBF sin la cola y rompía el .CDX) se quitó junto con el resto del código muerto; si hiciera falta dar de alta grupos desde el SCIAF, va por la cola del worker |
| Clasificación › Auxiliar | navegador | la tabla se pide al abrir, con el preloader en la tarjeta (la vista abre al instante); botón «Nuevo auxiliar» (antes faltaba); sin N+1 (`listarParaTabla`, sin eliminados); quitar ≠ baja en VSIAF (se avisa); nombre en mayúsculas en `AuxiliarRegistroService.normalizarNombre` |
| Clasificación › Organismo financiador | navegador | catálogo del VSIAF de solo consulta + sincronizar; filtro por gestión |
| Clasificación › Estado del activo | navegador | JSON + edición sobre el registro guardado; código único sin distinguir mayúsculas; cuántos activos usa cada estado (una consulta agrupada) |
| Clasificación › Responsable de entrega | navegador | quién figura en el acta en la cabecera; eliminar al seleccionado lo deselecciona y avisa; nombre único |
| Responsables | servidor | estado real del envío al VSIAF por fila (cola del worker) y reenvío; filtro y formulario BUSCAN la oficina (`/api/oficinas/opciones`, una consulta) en vez de traer todas con N+1; formulario con `interceptar` (autorización por cambio de oficina/código, personas parecidas 409); nombre ≤ 35 y cargo ≤ 40 (VSIAF); correo sin mayúsculas |
| Ámbito › Entidad | navegador | catálogo del VSIAF de solo consulta + sincronizar; filtro por gestión; el formulario viejo llamaba endpoints inexistentes (borrado) |
| Ámbito › Municipio | navegador | catálogo propio del SCIAF: JSON, edición sobre el registro guardado, nombre y código únicos en MAYÚSCULAS; predios por municipio (una consulta agrupada) |
| Ámbito › Predio | navegador | catálogo del VSIAF + **Configurar** (solo administradores): municipio y código del predio = prefijo de los códigos de activo; antes no había pantalla para asignarlos; filtro «Sin configurar»; entidad/municipio con JOIN FETCH; oficinas por predio agrupado |
| Ámbito › Oficina | servidor | paginada en el servidor con proyección (antes la tabla entera con N+1 por predio y entidad); uso (responsables/activos) y estado VSIAF de la página en consultas agrupadas; formulario con `interceptar` (autorización, personas parecidas) y alta junto con su responsable; se abre embebido desde Responsables |
| Activos › Registro de activos | — | solo el formulario (la tabla se quitó a pedido del usuario: para buscar está Consulta de activos; cargarTabla() queda vacía a propósito, ver vista.html). Antes: SOLO cáscara (cabecera, tabla, filtros, importación, preloaders): el formulario (activo/formulario.html) conserva su diseño y lógica. Bugs: tabla-registros traía TODOS los activos sin usarlos; filtro de responsables traía todos con N+1 (ahora /api/responsables/buscar); el fragmento recargaba Select2 desde un CDN; filas con nulos tumbaban la página; «Eliminar» sin permiso ni escape (ahora «Quitar», solo administradores, JSON); /importe/** abierto sin sesión (ahora solo administradores) |
| Activos › Pendientes | grupos | SOLO cabecera y preloader. Bugs: abría su propio EventSource (ahora escucha sciaf:pendientes-cambio que reemite head.html) y, en segundo plano, cargarTabla escribía en el #tablaRegistro de OTRA pestaña |
| Transferencias › Transferencia de activos | propia (tf-) | conserva su diseño y lógica; cabecera de la plantilla, colores tf- atados a los tokens --sm-* (modo oscuro), alto ajustado a la ventana. Bugs: predio podía mostrar "null", quitar fila armaba el código en un onclick, aviso de personas parecidas sin escapar, mayúsculas en el alta de responsable |
| Movimientos › Asignación de activos | propia (aa-) | conserva diseño y lógica; cabecera de la plantilla, alto ajustado. Bugs: tokens en :root y ~15 reglas de Select2 GLOBALES (cambiaban todo el sistema con la pantalla abierta) → acotados a .aa-pantalla con dropdownParent; registrar un responsable nuevo vaciaba los activos cargados; predio con "null"; aviso sin escapar; mayúsculas |
| Bajas e ingresos › Baja de activos | navegador | operaciones/baja/modulo.html con la plantilla (tabla con SciafModulo.tabla + filtroFila). Bugs: /baja/registro sin sesión y permitía dar de baja dos veces el mismo activo; fecha por defecto en UTC (de noche daba mañana); listado con 4 consultas por fila. seguimiento/baja/vista.html borrada (rota: otro modelo de bajas, endpoints inexistentes); /administracion/baja/vista apunta al módulo |
| Bajas e ingresos › Ingreso de bienes ajenos | propia (ia-) + plantilla | modulo con la plantilla; seguimiento (ia-) con tokens acotados (antes :root), cabecera solo cuando se abre sola (ingresoModulo). Bugs: fecha de retiro +3 meses mal calculada por zona horaria y usada en el PDF (ahora la calcula el servidor); fechas del seguimiento un día antes; filtro de responsables con TODOS + N+1; datos de Londra sin escapar; sin sesión → NPE |
| Gestión de Menú | árbol | llega con la vista; filtro por marca (oculto, bloqueado, permiso, URL rota); papelera y ayuda en modales; **sin mayúsculas** (`mayusculas:false`) porque son textos del menú lateral |

Persona ↔ VSIAF: el VSIAF copia nombre y C.I. en cada fila de RESP.DBF. Editar una persona (en Personas
o desde uno de sus responsables) manda el UPDATE a **todos** sus responsables activos que ya están en el
VSIAF (`ResponsableGestionService.propagarPersona`); los pendientes o rechazados viajan con el dato nuevo
al reenviarse.

## Botones

`sm-btn` (azul) · `sm-btn-exito` (guardar) · `sm-btn-peligro` · `sm-btn-neutro` (cancelar) ·
`sm-btn-suave` (versión clara que se rellena al pasar el cursor) · `sm-btn-sm` (tamaño de fila).

## Filas por página

Por defecto "Ajustar a pantalla": se calculan las filas que entran sin scroll y se recalcula al
cambiar el tamaño de la ventana. Si el usuario elige 25/50/100/Todas, se respeta.
