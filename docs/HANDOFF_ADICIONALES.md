# Handoff — Activos adicionales (15-oct-2026)

> Para el agente que retoma: lee esto, `docs/HANDOFF_DONACIONES.md` (el módulo hermano, del que
> sale la mayoría de las decisiones) y `docs/PLANTILLA_MODULOS.md`. Si algo contradice al código,
> manda el código.

## Estado

- **Sin commitear, sin probar en vivo.** Compila. La vista se renderizó con Thymeleaf offline y
  pasó una prueba de navegador simulado (jsdom, 20 comprobaciones: tabla, alta vacía rechazada,
  alta de 2 con códigos antiguos y foto a cada unidad, «registrar otro», detalle, quitar foto,
  corregir completando predio y grupo, eliminar). Se vio en Chrome sin pantalla.
- **Primer arranque = cambio de esquema en producción** (`hbm2ddl=update`): tablas
  `activo_adicional` y `activo_foto`, columna `activo.codigo_antiguo`, opción de menú
  `opcion_adicional`.

## Qué hace

Administración de activos → **Registro de Adicionales** (`/administracion/adicional/vista`).
Bienes que no estaban en el sistema. Decisiones del usuario (preguntadas):

- **Solo SCIAF**: `procedencia = ADICIONAL`, `sinc_vsiaf = NO_APLICA`. Las mismas barreras que las
  donaciones (`Activo.esSoloSciaf()` en `ActualDbfWriterService`, Londra, conciliación, custodia).
- **Ningún dato obligatorio**, salvo algo con qué reconocer el bien: el nombre/descripción o el
  código antiguo (sin descripción, cada unidad necesita su código antiguo).
- **Código antiguo**: la codificación vieja de Activos Fijos. Columna propia
  `activo.codigo_antiguo` (el usuario confirmó que NO es el CODIGOSEC del VSIAF). Puede repetirse.
- **Código nuevo** `MUN-PRED-GRP-00001A`, serie propia por prefijo, **solo si tiene predio
  configurado y grupo contable**; si no, `codigo = NULL` («SIN CÓDIGO») y se le asigna al
  corregirlo cuando se completen. Cambiar predio o grupo de uno con código → código nuevo (solo sin
  vida propia). No se puede quitar predio o grupo a uno que ya tiene código.
- **Bien por bien**: cantidad N crea N activos (códigos antiguos y series una por línea, por
  posición). «Guardar y registrar otro» conserva clasificación y ubicación.
- **Fotos**: hasta 3 por activo (`activo_foto`, archivos en `uploads/adicionales/fotos/`). En el
  alta, las fotos van a cada unidad (un archivo, una fila por unidad).
- **Corregir** (cualquier dato; ubicación solo si no tuvo vida propia) y **Eliminar** (solo
  administradores y sin vida propia; libera su código).
- Sin dato, se guarda costo 0, vida útil 0 y el estado de conservación 1: otros módulos usan esos
  campos sin comprobar que existan.

## Piezas

| Archivo | Qué |
|---|---|
| `model/entity/ActivoAdicional.java` | detalle (predio sin oficina, marca, modelo, serie, color, material, medidas, características, observación, token) |
| `model/entity/ActivoFoto.java` | fotos por activo |
| `model/dao/IActivoAdicionalDao.java` | turno, `maxCorrelativoAdicional`, listado, vida propia, borrado del alta |
| `model/service/adicional/AdicionalService.java` | todo el negocio |
| `controller/adicional/AdicionalController.java` | `/administracion/adicional` (vista, datatables, detalle, previsualizar, registrar, editar, eliminar, fotos) |
| `templates/adicional/vista.html` | tabla + formulario por secciones + detalle en ventana; verde azulado sólido (`.ad-pantalla`) |

También: `Activo` (`PROCEDENCIA_ADICIONAL`, `codigoAntiguo`, `esCodigoAdicional`,
`esCodigoSoloSciaf`), lector de QR del servidor y de la app (`mobile/src/services/qr.ts`,
`levantamiento.ts`: la A al final es parte del código → **APK nuevo**), pastilla «ADICIONAL» en
Consulta, Asignar y Transferencia (`SciafCatalogos.marcaDonacion`).

## Arreglos del `revisor` (15-oct)

- **Grave:** un adicional sin código que quedaba faltante trababa la custodia de todo su lote
  (`historial_activo.codigo_activo` y `transferencia_detalle.codigo_activo` son NOT NULL). Ahora
  `Activo.codigoReferencia` guarda el código, o «ANT <código antiguo>», o «ID <n>» (máx. 50), en
  `HistorialActivo.crear` y `TransferenciaService`. Al asignarle código después, el alta del
  historial pasa a ese código.
- XSS: el aviso al eliminar llevaba el código antiguo en el `title` de Swal (HTML). Escapado.
- Permiso también en las lecturas del servicio (tabla, detalle, vista previa).
- «Vida propia» suma `mantenimiento`, `alerta_mantenimiento` y `revaluacion` (también en Donaciones).
- **Registro de activos ya no modifica donaciones ni adicionales** (`modificar-activo` responde
  «corríjalo en su módulo»): su detalle vive en el módulo y el código podía salirse de su serie.
- Responsable de otra oficina por API, predio sin oficina en un bien con movimientos y quitar fotos
  que no son de un adicional: corregidos.

## Alto del formulario (15-oct)

El formulario abría más alto que la ventana: `SciafModulo.ajustarAlto` se llamaba al arrancar con
el panel oculto (mide 0 → tope = alto de la ventana) y solo se corregía al redimensionar (por eso
«arreglaba» al pulsar Alt). Ahora `mostrar('form')` recalcula con la función que devuelve
`ajustarAlto`. Lo mismo en Donaciones (`ajustarPanel`). **Regla para módulos nuevos:** recalcular
el alto cada vez que se muestra un panel que estaba oculto.

## Segundo lote (15-oct, respuestas del usuario)

- **Documentos impresos sin la A** (como la D): `Activo.codigoDocumento` quita la letra de las dos
  series. Las etiquetas, las pantallas y los Excel la conservan. Consecuencia aceptada: en un acta
  un adicional `…-00001A` y el bien VSIAF `…-00001` se imprimen igual.
- **Etiquetas con QR**: hoja común `model/service/PdfEtiquetasQrService` (Donaciones ahora también
  la usa, sin cambio visible). En Adicionales, casillas en la tabla (solo vigentes con código; los
  recién registrados se marcan solos; la selección sobrevive a páginas y búsquedas, tope 240) →
  «Etiquetas (n)»; y botón «Etiqueta» en el detalle. `GET /administracion/adicional/api/etiquetas?ids=…`.
  Si algún marcado se salta (eliminado o sin código), la hoja lo avisa arriba.
- **Altas rápidas de oficina/responsable** en el formulario, iguales a Donaciones (subir al VSIAF o
  dejar pendiente con aviso a los administradores; `desde = Registro de adicionales`). Permisos en
  `PermisoOpcionInterceptor` (`opcion_adicional`).
- **Consulta de activos busca por código antiguo** (buscador general y filtro «Código»), lo muestra
  bajo el código, muestra «SIN CÓDIGO» y lo exporta a Excel (`ActivoDTO.codigoAntiguo`).
- `revisor`: sin hallazgos graves; aplicados el aviso de etiquetas saltadas y el error claro si el
  PDF falla (también en Donaciones).

## Bloqueadores de anuncios (15-oct)

En Brave el botón «Nuevo adicional» no abría nada (en incógnito sí): EasyList oculta `.ad-panel`
(el recuadro del formulario) y `.ad-a` (la A del código). El prefijo del módulo pasó de `ad-` a
`adi-`; ninguna clase del sistema queda en EasyList (comparado contra EasyList + EasyList Spanish).

## Responsive (15-oct)

Había scroll de página (fuera del formulario) en laptops: la cabecera se partía en 3 filas y la
tabla tenía filas altísimas. Ahora no hay scroll de página en 1920×1080, 1600×900, 1440×900,
1366×768, 1280×720 ni 1024×768 (medido con el marco real: barra superior, pestañas, menú
lateral), ni en el listado ni en el formulario. Celular: tabla en tarjetas, pie del formulario
pegado abajo en una línea. Detalle en `docs/PLANTILLA_MODULOS.md` («Que la pantalla entre…»).
Solo cambió `templates/adicional/vista.html`.

## Acta por oficina / responsable (19-oct)

Pedido de Activos Fijos: «imprimir un reporte por oficina y responsable, así como los reportes de
asignaciones del VSIAF». Botón «Reporte por oficina / responsable» → modal (oficina obligatoria,
responsable opcional, **fecha y hora del acta**, por defecto ahora) →
`GET /administracion/adicional/api/reportes/asignacion.pdf?oficinaId=&responsableId=&fecha=`.
`model/service/adicional/ReporteAdicionalService` copia el acta en papel «ACTA DE ASIGNACION
INDIVIDUAL DE BIENES - ADICIONAL <año>» (ejemplo: C06-04-26 SALA DE INFORMATICA ACSYH):
membrete `fondo/0.jpg` en cada hoja, firmantes de `configuracion_gestion` de ese año +
responsable de entrega seleccionado (como `PdfAsignacionActivoCompleto`; sin visto bueno
configurado, la frase se omite), tabla Ítem/Descripción/Código/Observación, recuadro «Al:» (nombre,
C.I. + extensión, cargo + « – U.A.P.»), párrafo SABS/D.S. 0181, firmas con Vo. Bo. y
«C.c./Arch. <iniciales>/A-F». Toda la oficina → un acta por responsable, cada una en hoja nueva.
Código: `148-` + código sin la A (igual que el acta del VSIAF), el antiguo debajo; sin ninguno,
`S/CD`. Observación: estado de conservación y, debajo, la observación del registro.
Pruebas: `ReporteAdicionalServiceTest` (3). **Sin probar contra la base ni en vivo.**

## Formulario reordenado (19-oct)

Queja de Activos Fijos: «arriba pone descripción y abajo pone las características… la idea es que
sea intuitivo». El nombre estaba en «Identificación» y marca/modelo/color/material/medidas/
características en «Datos», al final, con la vista previa de la descripción al fondo. Ahora:
1. **Descripción del bien**: nombre, marca, modelo, color, material, medidas → recuadro «Así
   quedará la descripción» → características (aclarado que no entran en la descripción).
2. **Unidades y códigos**: cantidad, código antiguo, N° de serie (aclarado que va en la descripción).
3. Clasificación · 4. Ubicación (sin cambios) · 5. **Adquisición y observación** (fecha, valor,
   vida útil, financiador, observación) · 6. Fotos.
El detalle (ventana) sigue el mismo orden: tarjeta ancha «Descripción del bien» arriba con todo lo
que la forma y las características; el financiador pasó a «Adquisición». Solo cambió el HTML/CSS
de `templates/adicional/vista.html`; el JS no se tocó (todo va por clases `js-*`). Visto en una
captura estática a 1366 px; **sin probar en vivo** ni medir otra vez el «sin scroll de página».

## Formulario en carrusel, sin scroll (20-oct)

Pedido: lo mismo que en Donaciones, «si vas a hacer scroll hay que agrupar bien la información que
va a pedir». Antes el formulario tenía 6 secciones apiladas: 1.141 px de scroll interno en
1366×657 (y 1.001 px aun en 1920×950). Solo cambió `adicional/vista.html`.

- **4 pasos** (`.adi-pasos` / `.adi-etapa`):
  1. **El bien** – nombre, marca, modelo, color, material, medidas y la vista previa de la descripción.
  2. **Unidades y valor** – cantidad, códigos antiguos, series (una línea por unidad), fecha de
     adquisición, valor, vida útil y financiador.
  3. **Clasificación y ubicación** – predio, grupo, auxiliar, estado, oficina, responsable y el
     código que recibirá. Un alta rápida abierta ocupa el lugar de los campos (`.adi-con-alta`).
  4. **Notas y fotos** – características, observación y fotos (en la corrección, sin fotos).
- **Nada es obligatorio:** cualquier paso se abre con un clic; Atrás / Siguiente / Ctrl+Enter; se
  guarda desde cualquier paso. Los pasos con datos se marcan (`marcarPasos`). Al guardar, cada aviso
  lleva al paso del dato que falta o sobra. «Guardar y registrar otro» vuelve al paso 1.
- Códigos antiguos y series: la caja muestra hasta 5 líneas (3 en ventanas ≤700 px de alto); con
  más unidades se desplaza dentro de la caja.
- Ventana baja: el encabezado del formulario queda en una línea (≤820 px) y la cabecera del módulo
  se oculta con el formulario abierto (≤700 px). Listado: Valor se oculta con listado ≤780 px y el
  botón de reporte queda solo con ícono ≤900 px. Celular: «Guardar y otro» solo ícono.

**Verificado** en Chrome sin pantalla con el marco real: 0 px de scroll en todos los pasos en
1366×657, 1366×768, 1280×600, 1024×700, 800×700, 1536×730 y 1920×950 (el listado a 800×700
queda con 3 px por el mínimo de 3 filas); 23 comprobaciones de comportamiento. Thymeleaf renderiza.
**No probado en vivo.**

**Arreglos del `revisor` (20-oct):** sin hallazgos graves. (1) Ctrl+Enter con un select2 enfocado
abría su lista y cambiaba de paso; un segundo Ctrl+Enter **elegía la opción resaltada** (p. ej. el
primer financiador) sin que nadie la viera. Ahora el atajo no actúa con un select2 enfocado o
abierto (también en Donaciones, donde el fallo venía de antes). (2) Al cambiar de paso el foco caía
en `<body>` y Ctrl+Enter dejaba de responder: `irAPaso` pasa el foco al paso nuevo. Probado con
teclas reales (CDP `Input.dispatchKeyEvent`).

## Pendiente / ofrecer

1. En Consulta, un adicional sin código no lleva la pastilla «ADICIONAL» (se reconoce por el
   código; haría falta mandar la procedencia en `ActivoDTO`).

## Registro por lotes (21-oct-2026)

- El alta permite agregar hasta 20 lotes y 200 bienes en total. Cada lote conserva sus propios
  datos, cantidad, códigos antiguos, series, fotos, grupo contable, predio, oficina y responsable.
  Al agregar otro se heredan clasificación y destino para agilizar registros repetidos; se pueden
  cambiar y volver a cualquier lote antes de guardar. La confirmación muestra el resumen de todos.
- `POST /administracion/adicional/api/registrar-lotes` recibe `AdicionalLotesRequest` con un token
  de operación y la lista de `AdicionalRequest`. El servicio valida todos antes de guardar y usa
  una sola transacción, el candado de correlativos existente y un token distinto por lote para
  reintentos. Devuelve los activos agrupados por lote para colocar las fotos de cada uno.
- Compila con JDK 21; el JavaScript pasa `node --check`. Pasaron
  `ReporteAdicionalServiceTest` y `WordInternoTransferenciaServiceTest`. No se probó en vivo ni
  contra PostgreSQL. El test `WordActaSmokeTest` falla por una dependencia nula en
  `WordAsignacionActivoService` (fuera de este módulo).
