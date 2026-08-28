# Handoff → chat `frontend` · Levantamiento de activos en la APK

> **Backend listo y compilando.** Este documento es el contrato congelado.
> Fecha: 26-ago-2026 · Emisor: chat `backend`
>
> Contexto y decisiones de diseño: [`PLAN_CONTROL_ACTIVOS.md`](PLAN_CONTROL_ACTIVOS.md)

---

## 1. Qué hay que construir

Las pantallas de **levantamiento** en `mobile/` (Vue 3 + Ionic + Capacitor).

El operador entra a una oficina física, recorre el ambiente y marca lo que
encuentra. Lo que no marcó, al cerrar, queda como **faltante** a nombre de su
responsable. Puede además anotar novedades de condición ("está roto", "le falta
una rueda") sobre los activos que sí aparecieron.

**No hay que tocar Java.** Si falta un dato o un endpoint, avisá al chat
`backend` en vez de improvisar en el cliente.

---

## 2. Autenticación

Igual que el resto de `/api/movil/**`: **JWT** en `Authorization: Bearer <token>`.
Ya está resuelto por `MovilSecurityConfig` y el store de sesión existente — no hay
nada nuevo que hacer.

**Permiso requerido: `MOV_INVENTARIO`.** Ya estaba sembrado en el catálogo.
Si el usuario no lo tiene, el servidor responde:

```json
{ "ok": false, "codigo": "SIN_PERMISO", "mensaje": "No tiene permiso para esta operación" }
```

`ADMINISTRADOR` y `SUPER USUARIO` pasan siempre. Consultá `permisos` de
`GET /api/movil/auth/me` para decidir si mostrar la sección en el menú.

---

## 3. Endpoints — `/api/movil/levantamiento`

### 3.1 `GET /predios`

Predios con sus conteos. Sirve para el primer selector.

```json
[{ "idPredio": 3, "descrip": "Sede Central", "unidad": "01", "ciudad": "La Paz",
   "oficinas": 18, "responsables": 42, "activos": 1230,
   "faltantesAbiertos": 7, "levantamientosEnCurso": 1, "levantamientosTotales": 12,
   "estadoControl": "CON_FALTANTES" }]
```

`estadoControl`: `SIN_LEVANTAR` | `EN_CURSO` | `CONTROLADO` | `CON_FALTANTES`.

### 3.2 `GET /oficinas?idPredio=3`

```json
[{ "idOficina": 204, "codOfi": 204, "nombre": "Secretaría",
   "idPredio": 3, "predio": "Sede Central",
   "responsables": 3, "activos": 47, "faltantesAbiertos": 0,
   "levantamientosTotales": 2,
   "idLevantamientoEnCurso": null,
   "ultimoLevantamiento": "2026-07-14T10:02:00",
   "ultimoEncontrados": 45, "ultimoEsperados": 47,
   "estadoControl": "CONTROLADO", "porcentajeAvance": 95 }]
```

> Si `idLevantamientoEnCurso` no es null, esa oficina **ya tiene un recorrido
> abierto**. Ofrecé "Continuar" en vez de "Iniciar".

### 3.3 `GET /estados`

Catálogo de condiciones para anotar en campo. **Descargalo junto con el paquete
offline**: sin él no se puede registrar "roto" sin señal.

```json
[{ "id": 2, "nombre": "BUENO", "codigo": "B" },
 { "id": 3, "nombre": "REGULAR", "codigo": "R" },
 { "id": 4, "nombre": "MALO", "codigo": "M" }]
```

### 3.4 `POST /abrir` — el paquete offline

```json
{ "idOficina": 204, "uuidCliente": "8f2c…", "descripcion": null }
```

`uuidCliente` lo genera el teléfono (uno por recorrido). **Guardalo antes de
llamar**: si la respuesta se pierde y reintentás con el mismo uuid, el servidor
devuelve el mismo levantamiento en vez de abrir otro.

Respuesta — es la lista esperada completa, todo lo que hace falta para recorrer
sin señal:

```json
{
  "idInventario": 12,
  "numeroInventario": "LEV-2026-000012",
  "idOficina": 204, "codOfi": 204, "oficina": "Secretaría",
  "idPredio": 3, "predio": "Sede Central",
  "estado": "EN_EJECUCION", "origen": "MOVIL",
  "fechaInicio": "2026-08-26T09:14:00", "fechaFin": null,
  "ejecutor": "kcallisaya",
  "totalEsperados": 47, "totalEncontrados": 0,
  "totalPendientes": 47, "totalFaltantes": 0,
  "observ": null,
  "detalle": [
    { "idDetalle": 881, "idActivo": 3609,
      "codigo": "01-04-02-03609",
      "descripcion": "Escritorio de melamina 1.20m",
      "idResponsable": 77, "responsable": "PEREZ JUAN",
      "situacion": "PENDIENTE", "origenMarca": null, "fechaMarca": null,
      "observacion": null, "idEstadoObservado": null, "estadoObservado": null }
  ]
}
```

**Guardá el `detalle` completo en almacenamiento local.** Es el único caso del
módulo móvil donde se descarga la lista por adelantado: acotada a una oficina son
cientos de filas, no las más de 30.000 del maestro.

> ### ⚠ Formato de `codigo` — corregido el 26-ago-2026
>
> `detalle[].codigo` es el `activo.codigo` de la base, que va **SIN el prefijo de
> entidad**: `01-04-02-03609`, no `148-01-04-02-03609`. El prefijo lo antepone
> `ActivoMovilMapper.codigoVisual()` solo para mostrar, y es lo que está impreso
> en la etiqueta.
>
> Al mandar marcas por `codigo` (sin `idDetalle`), preferí la forma **sin
> prefijo**. Desde esta corrección el servidor **acepta las dos**: si el código
> trae 5 tramos o más, reintenta sin el primero. Vale tanto para localizar el
> detalle como para resolver un sobrante.

### 3.5 `GET /{id}/paquete`

El mismo cuerpo que `/abrir`. Para cambio de teléfono, reinstalación o caché
perdida.

### 3.6 `POST /{id}/marcas` — la cola offline

```json
{ "uuidCliente": "8f2c…",
  "marcas": [
    { "idDetalle": 881, "situacion": "ENCONTRADO", "origen": "ESCANEO",
      "fecha": "2026-08-26T09:21:33", "observacion": null, "idEstadoObservado": null },
    { "idDetalle": 884, "situacion": "ENCONTRADO", "origen": "MANUAL",
      "fecha": "2026-08-26T09:23:10", "observacion": "Le falta una rueda",
      "idEstadoObservado": 4 }
  ] }
```

| Campo | Valores | Nota |
|---|---|---|
| `idDetalle` | del paquete | Preferido |
| `codigo` | string | **Alternativa** a `idDetalle` cuando el escáner lee algo que no está en la lista esperada → el servidor lo registra como **SOBRANTE** |
| `situacion` | `ENCONTRADO` \| `FALTANTE` \| `PENDIENTE` | `PENDIENTE` deshace una marca |
| `origen` | `ESCANEO` \| `MANUAL` | de dónde salió |
| `fecha` | ISO-8601 **sin zona** (`2026-08-26T09:21:33`) | **hora del dispositivo**, no del servidor |

Respuesta:

```json
{ "ok": true, "aplicadas": 12, "ignoradas": 0, "sobrantes": 1,
  "encontrados": 12, "pendientes": 35, "esperados": 47 }
```

> **Reenviar el mismo lote es seguro.** Una marca se descarta si el servidor ya
> tiene otra más nueva para ese activo — por eso `fecha` importa y tiene que ser
> la del momento del escaneo, no la del envío. `ignoradas` no es un error: podés
> borrar esas marcas de la cola igual.

### 3.7 `POST /{id}/cerrar`

```json
{ "uuidCliente": "8f2c…", "observ": "Recorrido completo con el encargado" }
```

```json
{ "ok": true, "idInventario": 12, "numeroInventario": "LEV-2026-000012",
  "esperados": 47, "encontrados": 44, "faltantes": 3,
  "observados": 2, "hallazgosCreados": 5 }
```

**Mostrá una confirmación antes de llamar**: al cerrar, los `pendientes` pasan a
faltante y se le imputan a su responsable. Es la acción que no se deshace sola.

Cerrar dos veces devuelve el mismo resumen sin duplicar nada.

### 3.8 `GET /mios`

Levantamientos del usuario, más nuevo primero. Mismo formato que `/abrir` pero
con `detalle: []` — pedí el paquete si hace falta el detalle.

---

## 4. Flujo esperado en la app

```
Predios  →  Oficinas  →  [Iniciar | Continuar levantamiento]
                              ↓
                     Lista esperada (offline)
                     · escanear QR      → marca ENCONTRADO
                     · tocar la fila    → marca ENCONTRADO
                     · botón novedad    → observación + condición
                     contador: encontrados / sin revisar
                              ↓
                     [Cerrar]  → confirmación con nº de faltantes
                              ↓
                     Resumen del recorrido
```

**Modo por ausencia:** solo se marca lo que se encuentra. No pidas que además se
marque lo ausente — es lo que garantiza que algo se olvide.

**Cola offline:** cada marca se guarda primero en SQLite y se envía por lote al
recuperar red. Idempotencia por `idDetalle` + `fecha`, así que reintentar a ciegas
es seguro.

---

## 5. Reflejo en la web

No hay que hacer nada del lado móvil. El servidor emite SSE
(`levantamiento-abierto`, `levantamiento-avance`, `levantamiento-cerrado`) y el
mapa web repinta el tile en vivo mientras el operador recorre.

---

## 6. Errores

| HTTP | Cuerpo | Cuándo |
|---|---|---|
| 401 | `{"ok":false,"codigo":"TOKEN_INVALIDO","mensaje":"…"}` | sesión vencida → refresh |
| 403 | `{"ok":false,"codigo":"SIN_PERMISO","mensaje":"…"}` | falta `MOV_INVENTARIO` |
| 400 | `{"ok":false,"codigo":"REGLA_NEGOCIO","mensaje":"El levantamiento LEV-… ya está cerrado"}` | negocio; mostrar el mensaje tal cual |
| 400 | `{"ok":false,"codigo":"DATOS_INVALIDOS","mensaje":"…"}` | validación del cuerpo |

> **Corregido el 26-ago-2026.** Antes las condiciones de negocio caían en el
> manejador genérico y volvían `500 ERROR_SERVIDOR` con un texto opaco. Ahora hay
> una `ReglaNegocioException` propia y `MovilExceptionHandler` la traduce a 400
> con el mensaje real. El campo es **`mensaje`** (la cadena móvil siempre usa ese
> sobre); `message` solo aparece en los endpoints web.

---

## 7. Al terminar

Avisá al chat `backend` con:

1. Qué pantallas quedaron y en qué rutas de `mobile/src/views/`.
2. Qué campos del contrato **no** usaste o te sobraron.
3. Qué te faltó y tuviste que resolver en el cliente.

`backend` verifica el consumo real, cierra huecos y deja todo listo para que
**el usuario** compile el APK en Android Studio.

---

## 8. Estado del backend

| Pieza | Estado |
|---|---|
| Entidades `Inventario`, `InventarioDetalle`, `HallazgoInventario` | ✅ |
| `ControlActivosService` (abrir / marcar / cerrar / resolver) | ✅ |
| `LevantamientoMovilController` (`/api/movil/levantamiento/**`) | ✅ |
| `ControlActivosController` (web) | ✅ |
| Vistas `controlActivos/mapa.html` y `faltantes.html` | ✅ |
| Permisos en `OpcionMenuSeeder` | ✅ |
| Emisión SSE | ✅ |
| Compilación | ✅ `mvnw compile` limpio |
| **Prueba contra base real** | ⏳ pendiente — requiere correr `scripts/sql/control_activos_preparar_tablas.sql` y arrancar la app |
