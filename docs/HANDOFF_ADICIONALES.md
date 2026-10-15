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

## Pendiente / ofrecer

1. En Consulta, un adicional sin código no lleva la pastilla «ADICIONAL» (se reconoce por el
   código; haría falta mandar la procedencia en `ActivoDTO`).
