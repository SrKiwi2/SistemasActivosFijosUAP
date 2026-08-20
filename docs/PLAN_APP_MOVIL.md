# Plan — SCIAF Móvil (Vue 3 + Capacitor)

App Android (APK) que consume el backend Spring Boot existente (`SistemasActivosFijosUAP`).
Arranque con un núcleo funcional y crecimiento por módulos.

> Estado: **propuesta / no implementado** — v2 (2026-08-19), incorpora las respuestas del usuario sobre formato de QR, HTTPS, distribución, offline y diseño.

---

## 1. Objetivo y alcance

| # | Módulo | Resumen |
|---|---|---|
| 1 | Login | Mismas credenciales que la web. **Sesión persistente hasta cerrar sesión explícitamente** |
| 2 | Escáner QR | Escaneo rápido + **la BD manda sobre lo impreso en la etiqueta** + ficha completa e historial + emisión de informes PDF |
| 3 | Búsqueda de activo | Filtros múltiples sobre código/descripción/etc. |
| 4 | Listados por dimensión | Por predio, oficina, responsable, grupo contable, auxiliar |
| 5 | Notificaciones | Eventos que ocurren en la web, push a ADMINISTRADOR / SUPER USUARIO, historial y detalle |
| 6 | Asignaciones | Ver pendientes y subidas al VSIAF |

**Requisitos transversales (explícitos del usuario):**

- Obtención de datos **fluida, rápida y eficiente**, incluso con poco internet.
- **Capacidad offline** donde tenga sentido.
- **Sesión siempre activa** hasta cerrar sesión.
- Diseño **bonito, moderno e intuitivo**, con la identidad visual del sistema (azul + rojo) y los dos logos.

**Fuera de alcance v1:** escritura al VSIAF desde el móvil, aprobación de transferencias Londra, bajas, revalúos, importación Excel.

---

## 2. Decisiones de arquitectura

### 2.1 Stack móvil

| Pieza | Elección | Por qué |
|---|---|---|
| Framework | **Vue 3 + `<script setup>` + TypeScript** | Pedido |
| Build | **Vite** | Estándar de Vue 3 |
| UI | **Ionic Vue** (`@ionic/vue`, `@ionic/vue-router`) | Componentes con look nativo, navegación stack/tabs, gestos, pull-to-refresh e infinite-scroll ya resueltos, y **theming por variables CSS** — se le inyecta la paleta institucional sin pelear con el framework |
| Estado | **Pinia** (+ `pinia-plugin-persistedstate`) | Sesión y cola de escaneos sobreviven al cierre |
| Base local | **`@capacitor-community/sqlite`** | Miles de activos en caché. `Preferences`/`localStorage` no sirven para ese volumen |
| HTTP | **Axios** con interceptor de token, refresh y reintentos | Un solo punto para auth, errores, timeouts y `baseUrl` |
| Shell nativo | **Capacitor 6** | Pedido |
| Compilación APK | **Android Studio** (`npx cap open android`) | Confirmado por el usuario |

**Plugins Capacitor:**

| Plugin | Uso |
|---|---|
| `@capacitor-mlkit/barcode-scanning` | **Escáner QR (clave).** ML Kit de Google: lee offline, tolera QR borrosos/torcidos/con poca luz, linterna y zoom nativos. Muy superior a `html5-qrcode` (lo que usa la web hoy) y a `@capacitor-community/barcode-scanner` (ZXing, deprecado) |
| `@capacitor-community/sqlite` | Caché offline de catálogos, fichas y cola de salida |
| `capacitor-secure-storage-plugin` (Keystore) | Tokens de sesión |
| `@capacitor/preferences` | Config no sensible (URL del servidor, último predio usado) |
| `@capacitor/filesystem` + `@capacitor/share` | Guardar y compartir el PDF del informe |
| `@capacitor/network` | Detectar sin conexión → modo offline |
| `@capacitor/push-notifications` + FCM | Notificaciones con la app cerrada (Fase 7) |
| `@capacitor/haptics`, `@capacitor/status-bar`, `@capacitor/splash-screen` | Feedback de escaneo y pulido |

### 2.2 Dónde vive el código

```
SistemasActivosFijosUAP/
├── src/main/java/...            ← backend actual
└── mobile/                      ← NUEVO
    ├── capacitor.config.ts      appId: bo.edu.uap.sciaf · appName: SCIAF
    ├── package.json
    ├── src/
    │   ├── main.ts · App.vue
    │   ├── theme/           variables.css (paleta institucional)
    │   ├── router/
    │   ├── stores/          auth · escaneo · informe · notificaciones · catalogos · red
    │   ├── services/        http.ts · db.ts (SQLite) · sync.ts · outbox.ts · qr.ts
    │   ├── composables/     useScanner · useCodigoActivo · usePermisos · useOffline
    │   ├── views/           Login · Home · Escaner · ActivoDetalle · InformeNuevo ·
    │   │                    InformePreview · Buscar · Explorar · Notificaciones ·
    │   │                    NotificacionDetalle · Asignaciones · Inventario · Ajustes
    │   └── components/
    └── android/                 ← proyecto que se abre en Android Studio
```

`mobile/` se excluye del build Maven (no toca `mvnw package`).

### 2.3 Autenticación y **sesión permanente**

El sistema web usa `HttpSession` + `UsuarioAutenticadoInterceptor`, que ante falta de sesión hace `sendRedirect("/form-login")`. Eso no sirve para una app: el WebView de Capacitor tiene origen `http://localhost`, las cookies `SameSite` se pierden y un redirect HTML rompe cualquier cliente JSON.

**Propuesta:** namespace propio `/api/movil/**`, *stateless*, con JWT.

- `SecurityFilterChain` con `@Order(1)` y `securityMatcher("/api/movil/**")` → `SessionCreationPolicy.STATELESS` + `JwtAuthFilter`.
  La cadena actual ([SeguridadConfig.java](../src/main/java/com/usic/SistemasActivosFijosUAP/config/SeguridadConfig.java), sin `@Order` → precedencia mínima) **no se toca**: la web sigue igual.
- El login reutiliza **exactamente** la lógica de [LoginController.java](../src/main/java/com/usic/SistemasActivosFijosUAP/controller/login/LoginController.java): `usuarioService.buscarUsuarioPorNombre` + `passwordEncoder.matches` + rechazo de `INACTIVO`/`ELIMINADO`. Mismas credenciales, cero migración.

**Cómo se logra "logueado siempre hasta cerrar sesión":**

| Token | Vida | Dónde se guarda |
|---|---|---|
| Access token (JWT HS256, claims `sub`, `usr`, `rol`, `perm[]`) | 24 h | Memoria + secure storage |
| Refresh token (UUID opaco, **rotativo**) | Sin caducidad — vive hasta que se revoque | Keystore (`capacitor-secure-storage-plugin`) |

- El interceptor de Axios renueva el access token **en silencio** ante un 401, reintenta la petición original y el usuario no se entera.
- La sesión **solo** termina si: (a) el usuario pulsa "Cerrar sesión", (b) un ADMINISTRADOR revoca el dispositivo desde la web, (c) el usuario pasa a `INACTIVO`/`ELIMINADO`.
- **Sin red al abrir la app → entra igual**, en modo offline con los datos cacheados. No se bloquea la entrada esperando validar el token (error clásico que hace inútil una app de campo).

El refresh token se persiste en `dispositivo_movil`, lo que además permite ver y revocar dispositivos desde la web.

Dependencia nueva en `pom.xml`: `io.jsonwebtoken:jjwt-api|impl|jackson` 0.12.x. Clave en `application.properties` (`movil.jwt.secret`).

### 2.4 CORS

No existe configuración CORS en el proyecto. Se añade **solo** para `/api/movil/**`:

```
allowedOrigins: https://localhost, http://localhost, capacitor://localhost, http://localhost:5173 (dev)
allowedMethods: GET, POST, PUT, DELETE, OPTIONS
allowedHeaders: Authorization, Content-Type, If-None-Match
exposedHeaders: ETag
```

### 2.5 Red — ✅ resuelto

**El servidor tiene dominio y HTTPS: `https://sciaf.uap.edu.bo`.**

Consecuencias, todas buenas:

- **No hace falta** excepción de cleartext ni `network-security-config.xml`. Android acepta el tráfico sin trucos.
- Credenciales y tokens viajan cifrados.
- Habilita **HTTP/2** en el proxy → multiplexación de peticiones: varias llamadas en paralelo sobre una sola conexión, mucho mejor con señal débil.
- Habilita FCM sin fricción (Fase 7).
- La URL base queda fija en el build; se mantiene un override oculto en Ajustes para pruebas contra otro host.

**A verificar en el proxy:** que el certificado sea de una CA pública (Let's Encrypt sirve) y no autofirmado — Android rechaza autofirmados salvo que se empaquete el certificado en la app.

### 2.6 Nota de seguridad sobre la API actual

En `SeguridadConfig`, `/api/**` está en `permitAll()`. Endpoints como `/api/activos/por-codigos` o `/api/buscar-activo-responsable` son **públicos hoy**. La app móvil no los usará: consumirá su namespace autenticado. A mediano plazo conviene cerrar `/api/**` legacy o moverlo a `/api/publico/**`.

---

## 3. Reutilización — qué ya existe

Casi todo el dominio ya está en el backend. El trabajo es exponerlo bien, autenticado y en DTOs ligeros.

| Necesidad | Ya existe | Falta |
|---|---|---|
| Ficha de activo por código | `GET /api/activos/{codigo}/ficha` ([ActivoConsultaApiController.java](../src/main/java/com/usic/SistemasActivosFijosUAP/controller/rest/ActivoConsultaApiController.java)) | Versión ampliada (auxiliar, grupo, costo, estado) |
| Activos en lote por códigos | `POST /api/activos/por-codigos` ([CatalogoRestController.java](../src/main/java/com/usic/SistemasActivosFijosUAP/controller/rest/CatalogoRestController.java)) | Añadir auxiliar + predio, y proyección ligera |
| Catálogos | `/api/buscar_predios`, `/api/oficinas/por-predio`, `/api/responsables/por-oficina`, `/api/buscar_auxiliar_lista`, `/api/grupos/listar` | Versión autenticada + sync delta |
| Normalización de código QR | Regex `(\d{3}-)?\d{2}-\d{2}-\d{2}-\d{5}` y strip del prefijo en [p-scanner.js:37](../src/main/resources/static/assets/js/p-scanner.js#L37) | Parser completo del payload con pipes (§4) |
| Prefijo `148-` de la entidad | Ya se usa como "código visual" en `PdfAsignacionActivoCompleto` y `WordAsignacionActivoService` | Formalizarlo desde `Entidad.entidadCodigo` |
| Historial de activo | Entidad `HistorialActivo` (tipoEvento, antes/después, usuario) | Endpoint de lectura |
| Mantenimientos | Entidad `Mantenimiento` | Endpoint |
| Notificaciones persistentes | `Notificacion` + `NotificacionServiceImpl` + `TransferenciasNotificadorService` | Nuevos tipos de evento + emisor central |
| Push en tiempo real | SSE (`SseEmitterRegistry`, por usuario y por rol) | Variante con token + FCM |
| Generación de PDF con QR | `PdfInterno*Service` con `BarcodeQRCode` de iText | Servicio de informe móvil |
| Permisos por usuario | `OpcionMenu` + `usuario_opcion` + `opcionesEfectivas()` | Códigos `MOV_*` |
| Asignaciones pendientes | `AsignacionActivo`/`DetalleAsignacionActivo`, `Activo.estado = "PENDIENTE"` | Endpoint paginado |
| Inventario físico | Entidades `Inventario` y `HallazgoInventario` (ya modeladas, sin uso) | Lógica y endpoints |
| **Sync incremental** | Todas las entidades maestras tienen `fechaUltimaSync` y `hashDatos` | Endpoint `sync/catalogos?desde=` que lo aproveche para el offline |

---

## 4. El código de activo y el QR — especificación

Esta es la pieza más delicada del proyecto y ahora está confirmada.

### 4.1 Anatomía del código

En BD, `Activo.codigo` se construye en `ActivosController.construirCodigo()`:

```java
String.format("%s-%s-%s-%05d", mun, pred, grup, numero)
```

```
01  -  04  -  02  -  03609
│      │      │      └── correlativo (5 dígitos, por combinación mun+pred+grupo)
│      │      └───────── código de GRUPO CONTABLE
│      └──────────────── código de PREDIO
└─────────────────────── código de MUNICIPIO
```

Y el **código visual impreso** antepone el código de entidad: `148-01-04-02-03609`, donde `148` = `Entidad.entidadCodigo` (la Universidad). En BD **nunca** se guarda el `148-`.

> Dato aprovechable: **el propio código ya dice municipio, predio y grupo contable.** Eso permite filtrar y agrupar sin consultar nada, incluso offline, y detectar un tipo de discrepancia que de otro modo pasa desapercibido (§4.3, capa 2).

### 4.2 Anatomía del QR

Formato real de las etiquetas, campos separados por `|`:

```
UAP|COBIJA|CAMPUS UNIVERSITARIO LAS PALMAS|MUEBLES Y ENSERES|148-01-04-02-03609|MUEBLE PARA COMPUTADORA MELAMINA COLOR NARANJA 3 DIVISIONES 1 BANDEJA D:0,60*0,46*0,69 M
 1  |  2   |              3                |        4         |        5          |                      6
```

| # | Campo del QR | Contraparte en BD |
|---|---|---|
| 1 | Sigla de entidad — `UAP` | `Entidad.sigla` |
| 2 | Municipio / ciudad — `COBIJA` | `Municipio.nombre` / `Predio.ciudad` |
| 3 | Predio — `CAMPUS UNIVERSITARIO LAS PALMAS` | `Predio.descrip` |
| 4 | Grupo contable — `MUEBLES Y ENSERES` | `GrupoContable.nombre` |
| 5 | **Código visual** — `148-01-04-02-03609` | `"148-" + Activo.codigo` |
| 6 | Descripción — `MUEBLE PARA COMPUTADORA…` | `Activo.descripcion` |

**Regla de extracción del código (la que manda):**

1. Separar por `|`. Buscar el campo que case con `^\s*(\d{2,4}-)?(\d{2})-(\d{2})-(\d{2})-(\d{4,6})\s*$`.
2. Si no hay pipes o ningún campo casa → aplicar la misma regex como búsqueda sobre todo el texto (fallback, cubre etiquetas viejas de solo-código).
3. **Quitar el prefijo de entidad** (`148-`) → queda `01-04-02-03609`, que es lo que hay en `Activo.codigo`.
4. El prefijo no se descarta: se guarda y se **valida contra `Entidad.entidadCodigo`**. Un `149-…` significa que el activo no es de la universidad → aviso explícito, no un "no encontrado" críptico.

**Entrada manual — tolerante a propósito.** El usuario dijo que es probable que escriban el código *con* el `148-`. Se aceptan todas estas formas y se normalizan a la misma:

| Se escribe | Se interpreta |
|---|---|
| `148-01-04-02-03609` | prefijo detectado y removido |
| `01-04-02-03609` | tal cual |
| `148010402 03609` / `01040203609` | se insertan los guiones por posición |
| `3609` o `03609` | solo correlativo → busca coincidencias en todos los predios/grupos y ofrece elegir |

Campo con teclado numérico, máscara automática de guiones, y búsqueda a medida que se escribe.

### 4.3 Comparación QR vs sistema — **tres capas**

El usuario lo dijo claro: *"los reales y fieles son los que tenemos en la BD"*. La app muestra siempre el dato del sistema como principal, y la etiqueta como advertencia.

**Capa 1 — Texto del QR vs BD.** Campos 1–4 y 6 contra los valores actuales. Detecta la etiqueta desactualizada: cambió la descripción, el activo se reclasificó de grupo contable, se renombró el predio.

**Capa 2 — Segmentos del código vs BD.** El código es inmutable (lleva el correlativo), así que si el activo se transfirió de predio, el código sigue diciendo `pred=04` mientras la oficina actual pertenece a otro predio. Es una discrepancia **legítima y esperable**, y hay que mostrarla como informativa —no como error— porque explica por qué el QR y la realidad no coinciden:
*"El código fue emitido en CAMPUS LAS PALMAS; hoy el activo está en <predio actual>."*

**Capa 3 — Existencia y estado.** Código no encontrado, activo `CANCELADO`, `BAJA` o `PENDIENTE` de subir al VSIAF. Cada uno con su mensaje propio.

```
POST /api/movil/escaneo/verificar
{ "payload": "<texto crudo del QR>", "origen": "CAMARA" | "MANUAL" }

→ {
  "codigoDetectado": "01-04-02-03609",
  "prefijoEntidad": "148",
  "entidadValida": true,
  "veredicto": "OK" | "ETIQUETA_DESACTUALIZADA" | "REUBICADO" |
               "NO_ENCONTRADO" | "OTRA_ENTIDAD" | "ILEGIBLE",
  "activo": { …ficha real de la BD… },
  "discrepancias": [
    { "capa": 1, "campo": "descripcion", "valorQr": "MUEBLE…NARANJA…", "valorSistema": "MUEBLE…NARANJA… M:—", "severidad": "INFO" },
    { "capa": 2, "campo": "predio",      "valorQr": "CAMPUS LAS PALMAS", "valorSistema": "EDIFICIO CENTRAL",   "severidad": "AVISO" }
  ]
}
```

**Dónde vive el parseo — enfoque híbrido, por velocidad:**

- **En el dispositivo**: extracción del código y de los campos del pipe, *instantánea*, apenas ML Kit decodifica. Permite feedback háptico y pintar los datos de la etiqueta en <50 ms, sin esperar la red.
- **En el servidor**: la verificación, la comparación y el veredicto — la BD es la fuente de verdad.
- Las **reglas de parseo se descargan** del servidor (`GET /api/movil/config/parser-qr`, cacheadas y versionadas). Si mañana cambia el formato de etiqueta, se actualiza el servidor y **no hay que redistribuir el APK**.

### 4.4 Velocidad de escaneo

- ML Kit en modo continuo, `formats: [QrCode]`, cámara trasera, resolución 1280×720 (suficiente para QR y bastante más rápida que 1080p).
- **Deduplicación**: el mismo código no se re-consulta dentro de una ventana de 3 s.
- **Sin salir de la cámara**: en modo ráfaga (informe / inventario) el resultado aparece como tarjeta inferior y el escáner sigue vivo. Vibración corta = leído, doble = ya estaba en la lista, larga = error.
- **Una sola llamada por escaneo**: `verificar` devuelve ficha + discrepancias juntas. Las pestañas pesadas (historial, mantenimientos) se piden solo al abrirlas.
- **Golpe local primero**: si el código ya está en la caché SQLite, se pinta al instante y la respuesta del servidor solo lo refresca. Percepción de latencia cero.
- Linterna y zoom accesibles con un toque (etiquetas altas, mal iluminadas o pequeñas).

---

## 5. Módulos

### 5.1 Módulo 1 — Login

Logo **Activos Fijos** grande, logo **UAP** al pie, campos usuario/contraseña, botón azul institucional, y selector de servidor oculto (tap largo en el logo) para pruebas.

| Método | Ruta | Descripción |
|---|---|---|
| POST | `/api/movil/auth/login` | `{usuario, contrasena, deviceId, plataforma, modelo, appVersion}` → `{accessToken, refreshToken, expiraEn, usuario:{id, usuario, nombreCompleto, rol, permisos[]}}` |
| POST | `/api/movil/auth/refresh` | `{refreshToken}` → nuevo par (rotación) |
| POST | `/api/movil/auth/logout` | Revoca el refresh token del dispositivo |
| GET | `/api/movil/auth/me` | Perfil + permisos vigentes |
| GET | `/api/movil/version` | Versión mínima soportada → aviso de actualización |

**Reglas:**

- Roles admitidos en v1: `ADMINISTRADOR`, `SUPER USUARIO`, `APOYO`. Otros → 403 con mensaje claro.
  ⚠️ `SUPER USUARIO` lleva **espacio** — normalizar (`SUPER_USUARIO`) antes de usarlo como *authority* de Spring.
- El token trae los permisos, pero **cada endpoint los revalida en el servidor**. El cliente solo los usa para pintar/ocultar menús.
- Tras el primer login se lanza la **descarga inicial de catálogos** (§6.3) con barra de progreso: "Preparando la app para trabajar sin conexión…".

---

### 5.2 Módulo 2 — Escáner QR

Pantalla del módulo con **tres opciones**:

```
┌──────────────────────────────┐
│  ESCÁNER                     │
│  ┌────────────────────────┐  │
│  │ 🔍  Escaneo de activo  │  │  ← (b) consulta individual
│  ├────────────────────────┤  │
│  │ 📄  Emitir informe     │  │  ← (a) multi-escaneo → PDF
│  ├────────────────────────┤  │
│  │ 📋  Toma de inventario │  │  ← (c) PROPUESTA
│  └────────────────────────┘  │
└──────────────────────────────┘
```

#### (b) Escaneo de activo

Flujo, parseo y comparación: ver §4 completa.

**Ficha completa** — `GET /api/movil/activos/{codigo}/detalle`, en pestañas cargadas bajo demanda:

| Pestaña | Contenido | Fuente |
|---|---|---|
| Datos | código (visual con `148-`), descripción, estado, costo, depreciación, vida útil, fecha adquisición, grupo contable, auxiliar, organismo financiero, observaciones | `Activo` |
| Ubicación | predio/unidad, oficina, responsable (+ cargo, CI) | `Oficina`, `Predio`, `Responsable`, `Persona` |
| Historial | línea de tiempo: registro, modificaciones, asignaciones, transferencias, bajas — con antes/después y **quién lo hizo** | `HistorialActivo` |
| Transferencias | internas y Londra, con estado y correlativo | `Transferencia`, `TransferenciaLondra` |
| Asignaciones | actas en las que aparece | `AsignacionActivo` / `DetalleAsignacionActivo` |
| Mantenimientos | tipo, fecha, técnico, problema/solución, costo, próximo | `Mantenimiento` |

Acciones al pie: *Agregar al informe* · *Compartir ficha* · *Reportar etiqueta desactualizada*.

#### (a) Emitir informe

1. Cola de captura: escanear varios seguidos (háptico + contador, sin salir de la cámara) y/o **escribir el código a mano** (§4.2).
2. Lista editable: cada ítem resuelto contra la BD (`POST /api/movil/activos/lote`). Los no encontrados quedan en rojo y se corrigen o quitan.
3. Formulario: **título**, **descripción**, y opcionales (motivo, fecha, observaciones).
4. `POST /api/movil/informes` → el backend genera el PDF con iText (mismo patrón que `PdfInternoAsignacionService`) y lo persiste.
5. Vista previa → descargar / compartir (`Filesystem` + `Share`).

**Contenido del PDF v1:**

```
┌────────────────────────────────────────────────┐
│ [logoUap]  UNIVERSIDAD AMAZÓNICA DE PANDO      │
│            Dirección Administrativa Financiera │
│            Activos Fijos          [activofijos]│
├────────────────────────────────────────────────┤
│  TÍTULO DEL INFORME                            │
│  Descripción …                                 │
├──┬───────────────┬─────────┬────────┬──────────┤
│ #│ Código        │ Detalle │Auxiliar│ Oficina  │ … Responsable
├──┼───────────────┼─────────┼────────┼──────────┤
│ 1│148-01-04-02-  │ …       │ …      │ …        │
│  │03609          │         │        │          │
├──┴───────────────┴─────────┴────────┴──────────┤
│  Total: N activos                              │
│  Emitido por: <usuario> · <fecha y hora>       │
│                                    ┌─────────┐ │
│                                    │ [ QR ]  │ │ ← identificador del sistema
│                                    │INF-2026 │ │
│                                    │  -0042  │ │
│                                    └─────────┘ │
└────────────────────────────────────────────────┘
```

El código se imprime en **formato visual con el `148-`**, igual que las etiquetas y que los PDF que ya emite la web.

El QR inferior derecho codifica `https://sciaf.uap.edu.bo/seguimiento-activo/informe/<codigoVerificacion>` → **verificación de autenticidad**: quien reciba el informe impreso escanea y ve el informe real registrado. Por eso el informe se **persiste** (`informe_movil` + `informe_movil_detalle`), lo que además da historial y re-descarga.

| Método | Ruta |
|---|---|
| POST | `/api/movil/activos/lote` — resuelve una lista de códigos |
| POST | `/api/movil/informes` — crea y genera PDF (idempotente por `uuidCliente`) |
| GET | `/api/movil/informes` — historial (propios; todos si es ADMINISTRADOR) |
| GET | `/api/movil/informes/{id}/pdf` — re-descarga |
| GET | `/seguimiento-activo/informe/{codigo}` — verificación pública (web) |

#### (c) Propuesta: Toma de inventario por oficina

**La opción de mayor valor**, y las entidades **ya existen** (`Inventario`, `HallazgoInventario`).

Predio → oficina → la app descarga la lista esperada → se escanea en ráfaga y cada activo se pinta:

- 🟢 **Encontrado** — está en la lista esperada
- ⚪ **Faltante** — en el sistema pero no apareció físicamente
- 🔴 **Intruso** — está físicamente aquí, pero el sistema lo tiene en otra oficina/responsable

Al cerrar: acta de inventario en PDF + `HallazgoInventario` registrados para seguimiento desde la web. **Funciona 100% offline** con la lista precargada (§6.3) — decisivo en depósitos y sótanos sin señal.

*Ideas para más adelante:* reporte de incidencia con foto desde la ficha (→ `Mantenimiento`/`AlertaMantenimiento`); cambio de responsable/oficina en lote por escaneo (solo ADMINISTRADOR, escribe a la cola VSIAF).

---

### 5.3 Módulos 3 y 4 — Búsqueda y listados: **un solo endpoint**

Los puntos 3 y 4 son la misma consulta con filtros distintos:

```
GET /api/movil/activos/buscar
    ?q=              texto libre (código o descripción)
    &codigo=         &descripcion=
    &municipioId=    &predioId=       &oficinaId=      &responsableId=
    &auxiliarId=     &grupoContableId=
    &estado=         ACTIVO | PENDIENTE | CANCELADO | BAJA
    &orden=codigo,asc
    &page=0&size=25
→ { contenido:[…], pagina, totalPaginas, totalElementos,
    resumen:{ cantidad, costoTotal } }
```

Implementación: `Specification<Activo>` dinámico en un nuevo `ActivoBusquedaDao`, con **proyección** (interface projection de Spring Data) — no cargar entidades completas para pintar una lista. Los índices ya están en `Activo` para código, oficina, responsable, grupo y auxiliar.

**Truco de rendimiento gracias a §4.1:** si el término de búsqueda parece un código o un prefijo de código, se filtra con `LIKE 'mm-pp-gg-%'` sobre `idx_activo_codigo` en lugar de hacer un join a predio/grupo. Búsqueda por prefijo = *index range scan*, muy barato.

**Pantalla "Buscar"** (módulo 3): campo con debounce de 300 ms, chips de filtro activos, scroll infinito, resultados de la caché local mientras llega la red.

**Pantalla "Explorar"** (módulo 4): navegación jerárquica, cada nivel con conteo y costo acumulado.

```
Municipio ▸ Predio ▸ Oficina ▸ Responsable ▸ Activos
Grupo contable ▸ Activos
Predio ▸ Auxiliar ▸ Activos
```

✅ **Confirmado:** `Auxiliar` tiene `id_predio` **y** `id_grupo_contable`, ambos `nullable = false`. El auxiliar es efectivamente *por predio* (y además por grupo contable), tal como suponía el usuario. La navegación correcta es **Predio → Auxiliar**, opcionalmente filtrada por grupo.

Extra barato y muy útil: botón **"Exportar este listado"** → mismo motor de informes PDF. Un inventario por oficina o por responsable sale en dos toques.

---

### 5.4 Módulo 5 — Notificaciones de eventos web

El módulo con **más backend nuevo**. Hoy `Notificacion` solo se genera para transferencias y comunicados.

#### Catálogo de eventos

| Tipo | Se dispara cuando |
|---|---|
| `ASIGNACION_REGISTRADA` | Un usuario registra una asignación de activos en la web |
| `ASIGNACION_SUBIDA_VSIAF` | Se suben al VSIAF las asignaciones pendientes |
| `ACTIVO_REGISTRADO` | Alta de activo |
| `ACTIVO_MODIFICADO` | Edición de un activo |
| `ACTIVO_CANCELADO` | Cancelación / liberación de código |
| `RESPONSABLE_REGISTRADO` | Alta de responsable |
| `OFICINA_REGISTRADA` | Alta de oficina |
| `AUXILIAR_REGISTRADO` | Alta de auxiliar |
| `TRANSFERENCIA_*` | Ya existe (Londra) — se integra al mismo feed |

#### Emisión — eventos de Spring, no llamadas dispersas

```
Controlador  →  ApplicationEventPublisher.publishEvent(new EventoSistema(...))
                          ↓
     @TransactionalEventListener(AFTER_COMMIT)   ← solo notifica si el commit salió bien
                          ↓
              EventoSistemaListener
                ├─ crea Notificacion para cada ADMINISTRADOR / SUPER USUARIO (excepto el actor)
                ├─ push SSE a los conectados  (SseEmitterRegistry ya soporta por rol)
                └─ push FCM a los dispositivos registrados  (Fase 7)
```

`AFTER_COMMIT` evita el bug clásico de notificar algo que luego se revierte.

#### Entrega al APK

| Situación | Mecanismo |
|---|---|
| App abierta | SSE con token: `GET /api/movil/eventos/stream`. `EventSource` no admite cabeceras → usar `fetch` con streaming |
| App cerrada / background | **FCM** (`@capacitor/push-notifications` + `firebase-admin`). SSE se corta al ir a background: para notificaciones reales **FCM es imprescindible** |
| Reconexión / arranque | `GET /api/movil/notificaciones?desde=<timestamp>` — recupera lo perdido |

| Método | Ruta |
|---|---|
| GET | `/api/movil/notificaciones?tipo=&leida=&page=` |
| GET | `/api/movil/notificaciones/{id}` — detalle + enlace profundo al objeto |
| POST | `/api/movil/notificaciones/{id}/leer` · `/leer-todas` |
| GET | `/api/movil/notificaciones/no-leidas/contador` — badge |
| POST | `/api/movil/dispositivos/token-push` |

`Notificacion` ya distingue `entregada` de `leida` — se aprovecha tal cual.

---

### 5.5 Módulo 6 — Asignaciones subidas y pendientes

**v1: solo lectura.**

```
GET /api/movil/asignaciones?estado=PENDIENTE|SUBIDA&responsableId=&desde=&hasta=&page=
GET /api/movil/asignaciones/{id}
GET /api/movil/asignaciones/{id}/pdf     ← reutiliza PdfInternoAsignacionService
```

"Pendiente" = la asignación tiene activos con `Activo.estado = "PENDIENTE"` (registrados en SCIAF pero aún no escritos al VSIAF). Dos pestañas con contador, tarjeta por asignación (nº, fecha, responsable, oficina destino, cantidad, quién registró) y detalle expandible.

**Fase 2 (opcional, solo ADMINISTRADOR/SUPER USUARIO):** botón "Subir al VSIAF". Se deja para después a propósito: escribe al DBF a través de la cola/worker.

---

## 6. Rendimiento, red pobre y modo offline

Requisito explícito del usuario. Se ataca en tres frentes.

### 6.1 Que viajen menos bytes

| Medida | Dónde | Ganancia |
|---|---|---|
| **Compresión gzip** — `server.compression.enabled=true`, `min-response-size=1KB`, mime-types con `application/json` | `application.properties` (**hoy no está configurado**) | 70–85 % menos en JSON. La medida más barata y de mayor impacto |
| **DTOs `record` planos** en vez de `Map`/entidades JPA | Controladores móviles | Evita serializar relaciones lazy completas; respuestas 3–5× más chicas |
| **Proyecciones de Spring Data** para listados | `ActivoBusquedaDao` | Menos columnas leídas y cero hidratación de entidades |
| **ETag / `If-None-Match`** en catálogos y fichas | `ShallowEtagHeaderFilter` sobre `/api/movil/**` | Respuesta 304 sin cuerpo cuando nada cambió |
| **HTTP/2** | Proxy TLS (ya hay dominio) | Varias peticiones en paralelo sobre una conexión |
| **Paginación estricta** `size=25` | Todos los listados | Nada de "traer todo" |

### 6.2 Que se sienta instantáneo

- **Stale-while-revalidate en todas partes**: se pinta lo cacheado de inmediato y se refresca cuando llega la red. El usuario nunca ve un *spinner* en blanco si ya vio ese dato antes.
- **Skeletons**, no *spinners*: la pantalla tiene forma desde el primer frame.
- **Prefetch inteligente**: al abrir una oficina se precargan sus activos; al escanear se precarga la pestaña Historial en segundo plano.
- **Cola de escaneo en lote**: en modo ráfaga no se hace una petición por activo, se acumulan y se resuelven en bloques de 20 con `POST /activos/lote`.
- **Timeouts cortos con reintento** (8 s, 2 reintentos con *backoff*): con señal mala es mejor fallar rápido y reintentar que dejar la UI colgada.
- **Indicador de conexión** discreto en la cabecera: en línea / sin conexión / sincronizando.

### 6.3 Offline — **captura primero, datos después**

> **Decisión (usuario, ago-2026):** hay **más de 30.000 activos**. Espejar todo el maestro en el teléfono se descartó: la descarga inicial sería lenta con mala señal, quedaría desactualizada enseguida y obligaría a mantener una réplica que nadie pidió. En su lugar, el offline se limita a **no perder el trabajo de campo**.

**El principio:** lo que no puede fallar sin internet es *capturar*. Escanear una etiqueta y anotar un código no necesita servidor —ML Kit decodifica en el propio dispositivo—, así que la app captura siempre, y **resuelve los datos cuando vuelve la conexión**.

| Nivel | Qué | Cómo | Fase |
|---|---|---|---|
| **1 · Captura offline** *(el importante)* | Códigos escaneados y tecleados quedan guardados con su hora. La lista de un informe se arma completa sin señal | Cola local en SQLite. Cada ítem nace "sin resolver" y muestra solo el código; al recuperar red se completa con descripción, oficina, responsable y auxiliar en un único `POST /activos/lote` | 4 |
| **2 · Cola de salida (outbox)** | El informe se termina y se envía cuando hay conexión | Se guarda con `uuidCliente`; el servidor es **idempotente** por ese UUID, así que reintentar no duplica | 4 |
| **3 · Catálogos** | Predios, municipios, oficinas, grupos, auxiliares — **no** activos | Son pocos miles de filas en total y cambian poco. Sync delta con `GET /api/movil/sync/catalogos?desde=<ts>` aprovechando `fechaUltimaSync`/`hashDatos` que **ya existen** en las entidades | 4 |
| **4 · Fichas vistas** | Caché LRU de los últimos ~500 activos consultados | Permite volver a mirar un activo recién escaneado si se pierde la señal, con sello "datos del &lt;fecha&gt;" | 4 |
| **5 · Paquete por oficina** | Solo para la toma de inventario: se descarga **una oficina** (cientos de activos, no 30.000) estando en WiFi | Único caso donde sí hace falta la lista esperada por adelantado. Acotado por oficina, el volumen deja de ser problema | 8 |

**Lo que NO va offline**, y la UI lo dice: consultar la ficha de un activo nunca visto, notificaciones, y subida al VSIAF. Cuando un dato viene de caché se marca con su fecha de sincronización — nunca se presenta un dato viejo como si fuera actual.

**Consecuencia de diseño en el informe:** el flujo se parte en dos momentos —*capturar* (offline) y *completar y emitir* (en línea)—. Un informe con ítems sin resolver se puede guardar como borrador, y la app avisa cuántos faltan por completar. Esto es exactamente lo que planteó el usuario y además simplifica el resto: sin réplica del maestro, no hay que resolver el problema de mantenerla al día.

---

## 7. Sistema de diseño

### 7.1 Paleta

Colores extraídos del logo institucional de Activos Fijos y del tema web actual:

| Rol | Color | Origen |
|---|---|---|
| **Azul primario** | `#144391` | Color dominante del logo `activofijos.png` |
| Azul del sistema web | `#1A56A0` | Acento del sidebar actual — se usa para hovers y enlaces |
| Azul claro | `#7AB3F0` | Ya presente en el tema web |
| **Rojo institucional** | `#E40613` | Rojo del logo `activofijos.png` |
| Verde institucional | del logo UAP | Solo en contextos de la Universidad, no como color de UI |
| Superficies | `#FFFFFF` / `#F6F8FB` | Fondo de tarjetas y de página |
| Texto | `#111827` / `#6B7280` | Principal / secundario |

**Regla importante sobre el rojo.** El rojo es color de marca *y* color de peligro; usarlo para las dos cosas confunde. Se separa:

- **Rojo de marca `#E40613`** — solo identidad: cabeceras, acento del logo, subrayado activo de pestañas, badge de la app.
- **Semáforo de estado** — escala aparte: error `#D92D20`, aviso `#F79009`, éxito `#12B76A`, informativo `#1A56A0`.
- **Azul `#144391` = acción.** Botón primario, FAB de escaneo, elementos interactivos. El rojo nunca es el botón de "guardar".

En el escáner el semáforo es el lenguaje principal: 🟢 encontrado · 🟡 discrepancia · 🔴 no encontrado / otra entidad.

Se implementa como variables CSS de Ionic en `src/theme/variables.css` (`--ion-color-primary`, etc.), con **modo oscuro** derivado de la misma paleta.

### 7.2 Logos

| Archivo | Dónde se usa |
|---|---|
| [activofijos.png](../src/main/resources/static/assets/img/logo/activofijos.png) — Dirección Administrativa Financiera · Activos Fijos | Ícono de la app, splash screen, cabecera del login, cabecera interna, marca de agua de los PDF |
| [logoUap.png](../src/main/resources/static/assets/img/logo/logoUap.png) — Universidad Amazónica de Pando | Pie del login, encabezado de los PDF/informes, pantalla "Acerca de" |

Ambos se copian a `mobile/src/assets/` y se exportan en las densidades que pide Android (mdpi→xxxhdpi) para el ícono y el splash. Para el PDF se siguen usando los del backend.

> Nota: el logo institucional dice **Universidad Amazónica de Pando** (Cobija - Bolivia). Los encabezados de informe deben decir eso.

### 7.3 Lenguaje visual

- **Tarjetas** con radio 16 px, sombra suave, mucho espacio en blanco. Nada de tablas densas en móvil: cada activo es una tarjeta con código en monoespaciada destacada, descripción en dos líneas y chips de oficina/responsable.
- **Código monoespaciado y segmentado**: `148‑01‑04‑02‑03609` con el prefijo en gris y el correlativo en negrita — se lee de un vistazo y se compara con la etiqueta física sin esfuerzo.
- **Tabs inferiores** (Inicio · Buscar · Escanear · Notificaciones · Más) con el **botón de escaneo central destacado en azul**, más grande — es la acción principal de la app.
- **Pantalla de escaneo a pantalla completa**, marco guía animado, linterna y entrada manual siempre a un toque.
- **Resultado del escaneo** como hoja inferior deslizable (*bottom sheet*): aparece sobre la cámara sin cortar el flujo, se arrastra hacia arriba para la ficha completa.
- Tipografía: **Inter** (o la del sistema), tamaños generosos — se usa con guantes, a contraluz y con prisa.
- Accesibilidad: contraste AA mínimo, área táctil ≥ 44 px, estados nunca comunicados solo por color (siempre color + ícono + texto).
- Feedback háptico en cada lectura correcta: en campo se escanea mirando la etiqueta, no la pantalla.

---

## 8. Roles y permisos

### 8.1 Matriz propuesta

| Capacidad | SUPER USUARIO | ADMINISTRADOR | APOYO |
|---|:---:|:---:|:---:|
| Ingresar a la app | ✅ | ✅ | ✅ |
| Escanear y ver ficha completa | ✅ | ✅ | ✅ |
| Ver historial / transferencias / mantenimientos | ✅ | ✅ | ✅ |
| Buscar y explorar activos | ✅ | ✅ | ✅ (lectura) |
| Emitir informes PDF | ✅ | ✅ | ✅ (queda registrado quién lo emitió) |
| Toma de inventario | ✅ | ✅ | ✅ (levanta hallazgos, no corrige) |
| Descargar paquetes offline | ✅ | ✅ | ✅ |
| Ver asignaciones pendientes / subidas | ✅ | ✅ | ✅ (lectura) |
| Subir asignaciones al VSIAF | ✅ | ✅ | ❌ |
| Notificaciones de eventos del sistema | ✅ | ✅ | ❌ |
| Reportar etiqueta desactualizada | ✅ | ✅ | ✅ |
| Cambiar responsable/oficina, cancelar activos | ✅ | ✅ | ❌ |

**Criterio para APOYO:** **consulta y levanta información** (es quien anda en campo con el celular), pero **no altera el maestro de activos** ni recibe el flujo de auditoría. Es el rol de "operador de inventario".

*Fase futura — rol `RESPONSABLE`:* acceso restringido a **sus** activos.

### 8.2 Implementación — reutilizar el módulo de permisos existente

Se agregan códigos `MOV_*` a `opcion_menu` (mismo árbol que ya administran `OpcionMenuSeeder` / `usuario_opcion`):

```
MOV_ACCESO              entrar a la app
MOV_ESCANER             escanear y consultar
MOV_INFORME             emitir informes
MOV_INVENTARIO          toma de inventario
MOV_BUSQUEDA            buscar / explorar
MOV_NOTIFICACIONES      recibir eventos del sistema
MOV_ASIGNACIONES        ver asignaciones
MOV_ASIGNACIONES_SUBIR  subir al VSIAF
```

Ventaja doble: el ADMINISTRADOR otorga permisos móviles **desde la pantalla web que ya existe**, y `opcionesEfectivas()` ya resuelve la herencia (ADMINISTRADOR ve todo → asignación explícita → plantilla por rol).

---

## 9. Modelo de datos nuevo

Hibernate está en `hbm2ddl.auto=update`: las tablas se crean solas.

| Entidad | Campos principales |
|---|---|
| `DispositivoMovil` | id, usuario, deviceId, plataforma, modelo, appVersion, refreshToken, tokenFcm, ultimoAcceso, activo |
| `InformeMovil` | id, codigoVerificacion (para el QR), uuidCliente (idempotencia), titulo, descripcion, usuarioEmisor, fechaEmision, cantidadActivos, tipo |
| `InformeMovilDetalle` | id, informe, activo, snapshots (codigo, descripcion, oficina, responsable, auxiliar), orden |
| `EscaneoActivo` | id, usuario, codigoDetectado, prefijoEntidad, payloadCrudo, veredicto, discrepancias (JSON), origen, fecha |

`InformeMovilDetalle` guarda **snapshots** a propósito: un informe emitido debe seguir mostrando lo que decía el día que se emitió, aunque el activo se transfiera después.

---

## 10. Roadmap

| Fase | Entregable | Criterio de aceptación |
|---|---|---|
| **0 · Cimientos** | `mobile/` con Vue+Ionic+Capacitor y tema institucional; `MovilSecurityConfig` `@Order(1)`, `JwtService`, `JwtAuthFilter`, CORS, **gzip**; APK debug abierto en Android Studio e instalado en un teléfono | La app abre contra `https://sciaf.uap.edu.bo` y llama a un `/api/movil/ping` autenticado |
| **1 · Login + sesión** | Login, secure storage, refresh silencioso, arranque offline, shell con tabs y menú por permisos | Un ADMINISTRADOR entra, cierra la app días después y **sigue logueado**; sin red, entra igual |
| **2 · Escáner + ficha** | ML Kit, parser híbrido, `/escaneo/verificar` con las 3 capas, ficha por pestañas, entrada manual tolerante | Escanear una etiqueta real muestra los datos del sistema y **señala si la etiqueta miente**; escribir `148-01-04-02-03609` a mano funciona igual |
| **3 · Búsqueda + explorar** | Endpoint único paginado con proyección, pantallas Buscar y Explorar, búsqueda por prefijo de código | Encontrar un activo por código en <3 s con señal mediana; listar los activos de una oficina |
| **4 · Captura offline** | SQLite, cola de captura, outbox idempotente, sync delta de catálogos, caché LRU de fichas, indicador de conexión | Escanear 20 activos **sin señal**, cerrar la app, y que al recuperar conexión la lista se complete sola y el informe se envíe |
| **5 · Informes** | Cola de captura, formulario, `PdfInformeMovilService`, QR de verificación, compartir, historial | PDF con N activos (código visual + detalle + auxiliar + oficina + responsable) y QR que resuelve en el navegador |
| **6 · Asignaciones** | Pestañas pendientes/subidas, detalle, PDF | Los conteos coinciden con la vista `vista_pendientes` de la web |
| **7 · Notificaciones** | `EventoSistema` + listener `AFTER_COMMIT`, tipos nuevos, feed, detalle, badge, SSE en foreground | Un registro hecho en la web aparece en la app de un ADMINISTRADOR en <5 s con la app abierta |
| **8 · Push real + inventario** | FCM extremo a extremo; toma de inventario con paquete offline y hallazgos | Notificación con la app **cerrada**; acta de inventario de una oficina completa sin señal |
| **9 · Endurecimiento** | Rate limiting en login, APK firmado, versionado con aviso de actualización, guía de instalación | APK de release distribuible internamente |

La fase 4 se adelantó respecto a la v1 de este plan: el offline deja de ser un extra final y pasa a ser cimiento, porque las fases 5 y 8 se apoyan en él.
Las fases 2 y 3 pueden ir en paralelo (tocan capas distintas).

---

## 11. Decisiones — estado

| # | Tema | Estado |
|---|---|---|
| 1 | Formato del QR | ✅ **Resuelto** — 6 campos separados por `\|`, código visual con prefijo `148-` de entidad. Especificado en §4 |
| 2 | HTTPS | ✅ **Resuelto** — `https://sciaf.uap.edu.bo`. Falta confirmar que el certificado sea de CA pública, no autofirmado |
| 3 | Distribución del APK | ✅ **Resuelto** — compilación con Android Studio. Pendiente definir el canal de reparto (link interno / MDM) y el *keystore* de firma |
| 4 | Auxiliar ↔ predio | ✅ **Resuelto** — `Auxiliar` tiene `id_predio` y `id_grupo_contable`, ambos obligatorios. Navegación Predio → Auxiliar |
| 5 | Alcance de APOYO | ✅ **Resuelto** — matriz de §8.1 aprobada |
| 6 | Firebase | ✅ **Resuelto** — el usuario puede crear el proyecto. En la Fase 8 se genera el `google-services.json` en la consola de Firebase y se coloca en `mobile/android/app/` |
| 7 | Volumen de datos | ✅ **Resuelto** — más de 30.000 activos ⇒ **sin espejo completo offline**. El offline se limita a captura + outbox + catálogos (§6.3) |
| 8 | Doc desactualizado | `CLAUDE.md` dice BD `bd_a4`; `application.properties` apunta a `bd_a3`. Corregir |

---

## 12. Avance

### ✅ Fase 0 — Cimientos (hecha)

**Backend** (`/api/movil/**`):

| Archivo | Qué hace |
|---|---|
| `config/movil/JwtService` | Emite y verifica los JWT (HS256). Falla al arrancar si la clave es débil |
| `config/movil/JwtAuthFilter` | Lee el `Bearer` y deja el usuario en el `SecurityContext` |
| `config/movil/MovilSecurityConfig` | Cadena `@Order(1)` + `securityMatcher("/api/movil/**")`, `STATELESS`, CORS de Capacitor, 401/403 en JSON, filtro ETag |
| `config/movil/UsuarioMovilPrincipal` | Identidad del usuario móvil en la petición |
| `config/movil/MovilExceptionHandler` | Sobre de error uniforme `{ok, codigo, mensaje}` |
| `model/entity/DispositivoMovil` + DAO | Sesión por dispositivo, revocable |
| `model/service/movil/AuthMovilService` | Login, refresh rotativo, logout, perfil |
| `controller/movil/AuthMovilController` | `login · refresh · logout · me · ping · version · salud` |
| `application.properties` | `movil.*` + **gzip activado** |
| `MvcConfig` | `/api/movil/**` excluido del interceptor de sesión web |

La cadena web (`SeguridadConfig`) no se tocó: sigue con sesión HTTP.

**App** (`mobile/`): Vue 3 + TypeScript + Ionic + Capacitor 6, paleta institucional, proyecto Android generado, `npm run build` en verde.

### ✅ Fase 1 — Login y sesión permanente (hecha)

Pantalla de login con los dos logos, sesión persistida en el dispositivo, refresh silencioso ante 401 (uno solo en vuelo aunque fallen varias peticiones a la vez), arranque sin red con perfil cacheado, shell de 5 pestañas con el botón de escaneo central destacado, pantalla de inicio con diagnóstico de conexión (`/ping`) y pantalla "Más" con perfil, estado y cierre de sesión.

### ✅ Fase 2 — Escáner y ficha del activo (hecha)

**Backend:**

| Archivo | Qué hace |
|---|---|
| `model/service/movil/ParserQrActivoService` | Interpreta la etiqueta (6 campos con `\|`), extrae el código, retira el prefijo `148-` y normaliza la entrada manual en todas sus formas |
| `model/service/movil/EscaneoMovilService` | Resuelve contra la BD y compara en las **tres capas** de §4.3; emite veredicto y mensaje ya redactado |
| `model/service/movil/ActivoDetalleMovilService` | Ficha + historial + transferencias + asignaciones + mantenimientos en una sola respuesta |
| `model/service/movil/ActivoMovilMapper` | Entidad → DTO plano, a prueba de nulos (el maestro viene de DBF y cualquier relación puede faltar) |
| `model/service/movil/PermisosMovil` | Exige los códigos `MOV_*` en el servidor |
| `model/dao/IActivoMovilDao` | Ficha completa en una consulta con `join fetch`; búsqueda por correlativo |
| `controller/movil/ActivoMovilController` | `escaneo/verificar · activos/{codigo} · activos/{codigo}/detalle · activos/lote` |
| `config/OpcionMenuSeeder` | Siembra los permisos `MOV_*` como ítems ocultos, asignables desde la web |

**App:** ML Kit a pantalla completa con marco guía, linterna, deduplicación de 3 s y feedback háptico; resultado en hoja inferior con el veredicto en color + icono + texto y las diferencias en formato *etiqueta → sistema*; entrada manual con formato automático de guiones; ficha del activo con seis pestañas y línea de tiempo del historial.

### ▶ Siguiente: Fase 3 — Búsqueda y exploración

1. Backend: `ActivoBusquedaDao` con `Specification` y proyección; endpoint único paginado (§5.3).
2. App: pantalla Buscar con debounce y scroll infinito; pantalla Explorar con navegación Predio → Oficina → Responsable.
