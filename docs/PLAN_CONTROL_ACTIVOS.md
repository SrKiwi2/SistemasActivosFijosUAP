# Plan — Control de Activos por Responsable

> Módulo **interno** de control. No lee ni escribe VSIAF/DBF: trabaja solo contra PostgreSQL.
> Web (Thymeleaf, chat `backend`) + APK (Vue/Ionic en `mobile/`, chat `frontend`).
>
> Estado: **backend entregado** (fases 1 a 4) · 26-ago-2026
> Contrato congelado para el chat `frontend`: [`HANDOFF_CONTROL_ACTIVOS.md`](HANDOFF_CONTROL_ACTIVOS.md)
> Antes del primer arranque: `scripts/sql/control_activos_preparar_tablas.sql`

---

## 1. Objetivo

Hacer seguimiento físico de los activos que tiene cada responsable y registrar los
**faltantes** — los que no se encontraron en su oficina — con un ciclo de vida propio
hasta que se resuelven.

Dos caminos que terminan en el mismo dato:

- **Web** — navegación gráfica Predio → Oficina → Responsable → Activos, y desde ahí
  abrir un levantamiento y marcar faltantes a mano.
- **APK** — el operador recorre físicamente la oficina, escanea etiquetas, y lo que no
  apareció queda como faltante. Se refleja en la web en vivo por SSE.

---

## 2. La decisión de alcance que ordena todo el módulo

El pedido describe el seguimiento **por responsable**, pero el levantamiento en campo
ocurre **por oficina**: uno entra a un ambiente, no a una persona.

**Resolución:** el levantamiento se scopea a la **oficina**; cada fila de detalle guarda
el **responsable imputado** de ese activo. Así:

- el móvil recorre un ambiente completo de una sola pasada (que es lo que pasa en la realidad),
- la vista *Faltantes* agrupa por responsable (que es donde está la responsabilidad),
- y no se duplica ni un dato.

---

## 3. Lo que ya existe y se reutiliza

| Pieza | Estado | Uso en este módulo |
|---|---|---|
| `Predio → Oficina → Responsable → Activo` | Vivo | La jerarquía del mapa es nativa. `Activo` tiene `id_oficina` **y** `id_responsable` |
| `Inventario` | **Tabla huérfana** (0 usos) | Se reutiliza como cabecera del levantamiento |
| `HallazgoInventario` | **Tabla huérfana** (0 usos) | Se reutiliza como el faltante con ciclo de vida |
| `_estado = 'ACTIVO'` (`AuditoriaConfig`) | Vivo | Es el criterio de **vigencia** del responsable |
| `SseEmitterRegistry` + `/api/eventos` | Vivo | Empuje asíncrono móvil → web |
| `MovilSecurityConfig` (JWT, `@Order(1)`, CORS, ETag) | Vivo | La API móvil cuelga de `/api/movil/**` sin tocar nada |
| `PermisosMovil.exigir(...)` + `MOV_INVENTARIO` | Sembrado, sin usar | Permiso de la app para este módulo |
| `OpcionMenuSeeder` | Vivo | Alta de las opciones de menú nuevas |

**Colisión de nombres a evitar:** `SeguimientoController` ya existe en `/seguimiento-activo`
y es solo generación de PDFs de actas. Este módulo usa `/administracion/control-activos`.

---

## 4. Modelo de datos

`hbm2ddl.auto=update` agrega columnas solo; no hay migración manual.

### 4.1 `inventario` — cabecera del levantamiento (se extiende)

Ya tiene: `numeroInventario`, `oficina`, `fechaInicio`, `fechaFin`, `estado`,
`totalActivosEsperados`, `totalActivosEncontrados`, `observ`, auditoría.

Columnas nuevas:

| Campo | Tipo | Para qué |
|---|---|---|
| `uuid_cliente` | varchar(36) unique | Idempotencia: el móvil puede reintentar sin duplicar |
| `origen` | varchar(10) | `WEB` / `MOVIL` |
| `id_usuario_ejecutor` | FK usuario | Quién lo ejecutó |
| `total_faltantes` | int | Denormalizado, para pintar los tiles del mapa sin contar hallazgos |

Estados usados: `EN_EJECUCION` → `COMPLETADO`. (`PLANIFICADO` / `CONCILIADO` quedan disponibles.)

### 4.2 `inventario_detalle` — **tabla nueva**

Es la que hace posible el modo "por ausencia": congela la lista esperada al abrir.

| Campo | Tipo | Nota |
|---|---|---|
| `id_detalle` | PK | |
| `id_inventario` | FK NN | |
| `id_activo` | FK NN | |
| `id_responsable` | FK | Snapshot del imputado al abrir |
| `codigo`, `descripcion` | varchar | Snapshot: el acta debe seguir diciendo lo que decía |
| `situacion` | varchar(15) | `PENDIENTE` / `ENCONTRADO` / `FALTANTE` |
| `origen_marca` | varchar(10) | `ESCANEO` / `MANUAL` / `WEB` |
| `fecha_marca` | timestamp | |
| `observacion` | text | |

Índices: `(id_inventario, situacion)`, `(id_activo)`, `(id_responsable)`.

### 4.3 `hallazgo_inventario` — el faltante (se extiende)

Ya tiene: `tipoHallazgo` (`FALTANTE` / `SOBRANTE` / `SIN_CODIFICAR` / `DESACUERDO_DATOS`),
`activo`, `codigoFisico`, `accionCorrectiva`, `fechaResolucion`, `usuarioRevisor`, `observ`.

Columnas nuevas:

| Campo | Tipo | Para qué |
|---|---|---|
| `id_responsable` | FK | A quién se le imputa el faltante |
| `estado_hallazgo` | varchar(20) | `ABIERTO` / `RESUELTO` |
| `tipo_resolucion` | varchar(30) | `APARECIO` / `JUSTIFICADO` / `DERIVADO_BAJA` |

### 4.4 La regla de cierre

Al cerrar un levantamiento, en una sola transacción:

1. Todo `inventario_detalle` en `PENDIENTE` pasa a `FALTANTE`.
2. Por cada `FALTANTE` se crea un `HallazgoInventario` tipo `FALTANTE`,
   `estado_hallazgo = ABIERTO`, con su `id_responsable`.
3. Se recalculan `totalActivosEncontrados` y `total_faltantes`, y `estado = COMPLETADO`.
4. Se emite SSE `levantamiento-cerrado`.

Detalle = trabajo de campo (operativo, se congela con el acta). Hallazgo = registro de
control (persistente, con resolución). Separarlos evita que "resolver un faltante" tenga
que reescribir el acta.

---

## 5. Web — el mapa

Fragmento SPA (mismo patrón que `conciliacion/activos.html`: el sidebar trae `data-url` y
el shell inyecta el fragmento por AJAX).

### 5.1 Navegación por tiles cuadrados

Grid CSS de cuadrados, sin librerías nuevas — el tema Bootstrap/Tabler actual alcanza.

```
NIVEL 1 · PREDIOS              NIVEL 2 · OFICINAS del predio
┌──────┐ ┌──────┐ ┌──────┐     ┌──────┐ ┌──────┐ ┌──────┐
│ SEDE │ │ FAC. │ │ BLOQ.│ ->  │ 201  │ │ 204  │ │ 207  │
│ CENT.│ │ ING. │ │  C   │     │ 12 a │ │ 47 a │ │  8 a │
│ 18of │ │  7of │ │  4of │     │ 2 rp │ │ 3 rp │ │ 1 rp │
└──────┘ └──────┘ └──────┘     └──────┘ └──────┘ └──────┘

NIVEL 3 · RESPONSABLES         NIVEL 4 · ACTIVOS + acciones
┌──────────┐ ┌──────────┐      tabla de activos del responsable
│ J. PEREZ │ │ M. LOPEZ │  ->  [ Iniciar levantamiento ]
│ VIGENTE  │ │ INACTIVO │      [ Ver faltantes abiertos ]
│  31 act. │ │  0 act.  │
└──────────┘ └──────────┘
```

**Color del tile** = estado de control, que es el dato que este módulo aporta:

| Color | Significado |
|---|---|
| Gris | Sin levantamiento registrado |
| Ámbar | Levantamiento `EN_EJECUCION` |
| Verde | `COMPLETADO`, sin faltantes abiertos |
| Rojo | Tiene faltantes `ABIERTO` |

**Vigencia del responsable** (`_estado='ACTIVO'`) es un badge aparte, no el color del tile:
son dos ejes distintos y mezclarlos haría ilegible el mapa. Un responsable inactivo **con
activos a su nombre** se marca en el tile — es justamente la anomalía que este módulo debe
sacar a la luz.

### 5.2 Vista Faltantes

Lista agrupada por responsable, con filtros por predio / oficina / estado / rango de fechas,
acciones de resolución, y exportación a Excel (Apache POI ya está en el proyecto).

---

## 6. Endpoints

### 6.1 Web — `/administracion/control-activos` (sesión, `@ValidarUsuarioAutenticado`)

| Método | Ruta | Devuelve |
|---|---|---|
| GET | `/vista` | Fragmento del mapa |
| GET | `/faltantes/vista` | Fragmento de faltantes |
| GET | `/mapa/predios` | Tiles nivel 1 con conteos y estado de control |
| GET | `/mapa/predios/{id}/oficinas` | Tiles nivel 2 |
| GET | `/mapa/oficinas/{id}/responsables` | Tiles nivel 3 (+ vigencia, + nº activos) |
| GET | `/responsables/{id}/activos` | Nivel 4 |
| POST | `/levantamientos` | Abre uno (`{idOficina}`) y congela el detalle |
| GET | `/levantamientos/{id}` | Cabecera + detalle |
| PATCH | `/levantamientos/{id}/detalle/{idDetalle}` | Marca `ENCONTRADO` / `FALTANTE` |
| POST | `/levantamientos/{id}/cerrar` | Aplica la regla de §4.4 |
| GET | `/faltantes` | Hallazgos filtrados, agrupados por responsable |
| POST | `/hallazgos/{id}/resolver` | `{tipoResolucion, accionCorrectiva}` |
| GET | `/faltantes/excel` | Export |

### 6.2 Móvil — `/api/movil/levantamiento` (JWT + `MOV_INVENTARIO`)

Contrato para el chat `frontend`. Todo bajo `PermisosMovil.exigir(PermisosMovil.INVENTARIO)`.

| Método | Ruta | Nota |
|---|---|---|
| GET | `/predios` | Predios con nº de oficinas |
| GET | `/oficinas?idPredio=` | Oficinas con nº de activos esperados |
| POST | `/abrir` | `{idOficina, uuidCliente}` → devuelve el **paquete offline** completo |
| GET | `/{id}/paquete` | Re-descarga (cambio de teléfono / reinstalación) |
| POST | `/{id}/marcas` | Lote idempotente de marcas |
| POST | `/{id}/cerrar` | `{uuidCliente, observ}` |
| GET | `/mios` | Levantamientos abiertos del usuario |

**Paquete offline** (`POST /abrir`) — es el caso del roadmap móvil §6.3 nivel 5, acotado a
una oficina (cientos de activos, no 30.000):

```json
{
  "idInventario": 12,
  "numeroInventario": "LEV-2026-000012",
  "oficina": { "id": 204, "codOfi": 204, "nombre": "Secretaría", "predio": "Sede Central" },
  "estado": "EN_EJECUCION",
  "fechaInicio": "2026-08-26T09:14:00",
  "totalEsperados": 47,
  "detalle": [
    {
      "idDetalle": 881,
      "idActivo": 3609,
      "codigo": "148-01-04-02-03609",
      "descripcion": "Escritorio de melamina 1.20m",
      "idResponsable": 77,
      "responsable": "PEREZ JUAN",
      "situacion": "PENDIENTE"
    }
  ]
}
```

**Marcas** (`POST /{id}/marcas`) — idempotente por `idDetalle`; reenviar el mismo lote no
duplica ni pisa una marca más nueva:

```json
{
  "uuidCliente": "…",
  "marcas": [
    {
      "idDetalle": 881,
      "situacion": "ENCONTRADO",
      "origen": "ESCANEO",
      "fecha": "2026-08-26T09:21:33",
      "observacion": null
    }
  ]
}
```

Respuesta: `{ "ok": true, "aplicadas": 12, "ignoradas": 0, "encontrados": 12, "pendientes": 35 }`

**Cierre** (`POST /{id}/cerrar`) → `{ "ok": true, "encontrados": 44, "faltantes": 3, "hallazgosCreados": 3 }`

### 6.3 SSE — reflejo asíncrono en la web

Eventos emitidos al `SseEmitterRegistry` (broadcast + dirigido a `ADMINISTRADOR` / `SUPER USUARIO`):

| Evento | Cuándo | Payload |
|---|---|---|
| `levantamiento-abierto` | `POST /abrir` | `{idInventario, idOficina, idPredio, esperados, usuario}` |
| `levantamiento-avance` | lote de marcas | `{idInventario, idOficina, encontrados, pendientes}` |
| `levantamiento-cerrado` | cierre | `{idInventario, idOficina, idPredio, encontrados, faltantes}` |

La vista del mapa se suscribe a `/api/eventos/stream` y repinta el tile afectado sin recargar.

---

## 7. Permisos

En `OpcionMenuSeeder`, grupo nuevo `grp_control` bajo la sección existente `sec_seguimiento`:

| Código | Tipo | Descripción |
|---|---|---|
| `opcion_control_mapa` | ITEM | Control por Responsable → `/administracion/control-activos/vista` |
| `opcion_control_faltantes` | ITEM | Faltantes → `/administracion/control-activos/faltantes/vista` |
| `opcion_control_resolver` | PERMISO (oculto) | Resolver / justificar faltantes |
| `MOV_INVENTARIO` | PERMISO (ya sembrado) | Levantamiento desde la app |

Como la autorización HTTP está abierta (`SeguridadConfig`), el chequeo va **en la capa de
servicio**, igual que el resto del sistema.

---

## 8. Reparto del trabajo

### Chat `backend` (este) — entrega 1

1. Entidades: extender `Inventario` y `HallazgoInventario`, crear `InventarioDetalle`.
2. DAOs: `IInventarioDao`, `IInventarioDetalleDao`, `IHallazgoInventarioDao` + consultas de conteo del mapa.
3. `ControlActivosService` — apertura, marcado, cierre transaccional, resolución, y las
   agregaciones del mapa.
4. `ControlActivosController` (web) + `LevantamientoMovilController` (`/api/movil/levantamiento`).
5. DTOs compartidos + emisión SSE.
6. Vistas Thymeleaf: `controlActivos/mapa.html`, `controlActivos/faltantes.html`.
7. Alta en `OpcionMenuSeeder`.
8. **Handoff** → `docs/HANDOFF_CONTROL_ACTIVOS.md` con el contrato congelado y ejemplos reales.

### Chat `frontend` — entrega 2

Pantallas en `mobile/src/views/` contra `/api/movil/levantamiento/**`:

1. Selector Predio → Oficina.
2. Pantalla de levantamiento: lista esperada, marcado por escaneo (reutiliza el escáner de
   la Fase 2) y por toque, contador `encontrados / pendientes`.
3. Cola offline en SQLite: las marcas se guardan localmente y se envían por lote al recuperar
   red (idempotencia por `idDetalle` y `uuidCliente`).
4. Confirmación de cierre con resumen previo de cuántos van a quedar como faltantes.
5. Historial de levantamientos del usuario.

### Chat `backend` — entrega 3 (verificación)

Al avisar `frontend`: revisar el consumo real del contrato, cerrar huecos, y dejar el
backend listo para que **el usuario** compile el APK en Android Studio.

---

## 9. Fases

| Fase | Entregable | Criterio de aceptación |
|---|---|---|
| ✅ **1 · Datos** | Entidades + DAOs + servicio con la regla de cierre | Abrir un levantamiento de una oficina congela N detalles; cerrarlo genera exactamente los hallazgos que faltaban |
| ✅ **2 · Mapa web** | Endpoints del mapa + `mapa.html` | Navegar Predio → Oficina → Responsable → Activos y ver el estado de control por color |
| ✅ **3 · Faltantes web** | Vista, filtros, resolución, Excel | Un faltante se resuelve con motivo y queda quién y cuándo |
| ✅ **4 · API móvil + SSE** | `/api/movil/levantamiento/**` + eventos | Un lote de marcas por Postman mueve el tile de la web sin recargar |
| **5 · APK** (`frontend`) | Pantallas Vue/Ionic + cola offline | Recorrer una oficina sin señal, cerrar la app, y que al volver la red se sincronice y se refleje en la web |
| **6 · Verificación** (`backend`) | Ajustes de contrato | Levantamiento completo de punta a punta desde el teléfono |

---

## 10. Decisiones tomadas

| # | Tema | Decisión |
|---|---|---|
| 1 | Tablas huérfanas | **Reutilizar y extender** `Inventario` + `HallazgoInventario`; se suma `inventario_detalle` |
| 2 | Detección de faltantes | **Por ausencia**: se marca lo encontrado, lo no marcado cae a `FALTANTE` al cerrar |
| 3 | Ciclo de vida del faltante | **Con resolución**: `ABIERTO` → `RESUELTO` (`APARECIO` / `JUSTIFICADO` / `DERIVADO_BAJA`) |
| 4 | Scope del levantamiento | **Oficina**, con responsable imputado por fila de detalle |
| 5 | Alcance VSIAF | **Ninguno**: módulo interno, no toca DBF ni la cola de escritura |
| 6 | Ruta web | `/administracion/control-activos` (evita la colisión con `/seguimiento-activo`) |
| 7 | Persistencia offline del móvil | **Capacitor Preferences**, no SQLite — ver §12 |
| 8 | Errores de negocio | `ReglaNegocioException` propia → 400 con mensaje mostrable, en web y móvil |

---

## 12. Implementación móvil — decisiones del chat `frontend`

**SQLite descartado.** `@capacitor-community/sqlite` es un plugin nativo: sumarlo
obliga a rehacer la configuración de Android Studio justo para el módulo que menos
lo necesita, porque una oficina son cientos de filas y no las 30.000 del maestro.
Se persiste sobre **Capacitor Preferences** detrás de
`mobile/src/services/levantamientoLocal.ts`, con las escrituras encadenadas por
clave (escaneando en ráfaga, dos `set` en vuelo pueden completarse al revés y
dejar guardado un mapa viejo — ese activo volvería a figurar pendiente y al cerrar
se le imputaría a alguien).

*Límite conocido:* Preferences es `SharedPreferences` en Android, pensado para
valores chicos. Para oficinas de unos pocos cientos de activos va bien; si
aparece una con varios miles conviene volver a mirarlo. El cambio queda acotado a
ese único archivo.

**El cierre exige la cola vacía.** `POST /cerrar` solo se llama después de que la
cola drenó con éxito; sin señal y con marcas pendientes, el cierre se bloquea con
un diálogo. Sin esta regla, un activo que el operador **sí** encontró se cerraría
como faltante y se le imputaría a una persona por una falla de red.

**La salida de emergencia se ofrece solo ante rechazo del servidor.** Si el
servidor rechaza el lote (400 `REGLA_NEGOCIO` — típicamente porque el
levantamiento se cerró desde la web mientras el teléfono tenía cola sin enviar),
esa cola no va a drenar nunca: la app registra el error y pinta "Descartar
recorrido". Si en cambio es pura falta de señal, **no** se ofrece descartar —
proponérselo a alguien que apenas se quedó sin cobertura es invitarlo a tirar el
trabajo de campo; ahí lo correcto es reintentar más tarde.

**Otras decisiones del cliente:** el `uuidCliente` se escribe a disco antes de
`POST /abrir`; los catálogos (`/predios`, `/oficinas`, `/estados`) se cachean en
la pantalla de predios, donde todavía hay señal; se admiten varios levantamientos
abiertos a la vez en el teléfono (con un puntero único, una oficina empezada
quedaba abierta del lado del servidor sin forma de retomarla sin señal); y cada
marca lleva un `seq` local además de la fecha, porque dos marcas sobre el mismo
activo dentro del mismo segundo no se distinguen por hora. El `seq` es interno del
cliente — el servidor lo ignora sin error.

---

## 11. Supuestos a confirmar sobre la marcha

- Un responsable **inactivo** con activos a su nombre **sí** entra en el levantamiento, y se
  marca como anomalía en el tile.
- **No** se permite más de un levantamiento `EN_EJECUCION` por oficina: si ya hay uno abierto,
  se reabre el existente en vez de crear otro.
