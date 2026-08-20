# SCIAF Móvil

App Android del Sistema de Control de Activos Fijos — Universidad Amazónica de Pando.
Vue 3 + Ionic + Capacitor sobre el backend Spring Boot de este mismo repositorio.

Plan completo y hoja de ruta: [`../docs/PLAN_APP_MOVIL.md`](../docs/PLAN_APP_MOVIL.md)

---

## Estado

| Fase | Módulo | Estado |
|---|---|---|
| 0 | Cimientos: JWT, CORS, gzip, scaffold, APK | ✅ Hecho |
| 1 | Login y sesión permanente | ✅ Hecho |
| 2 | Escáner QR y ficha del activo | ✅ Hecho |
| 3 | Búsqueda y exploración | ⏳ |
| 4 | Captura offline | ⏳ |
| 5 | Informes PDF | ⏳ |
| 6 | Asignaciones | ⏳ |
| 7 | Notificaciones | ⏳ |
| 8 | Push (FCM) e inventario | ⏳ |

---

## Requisitos

- Node.js 20+
- Android Studio (SDK 34+, JDK 17 o 21)
- El backend corriendo (`../mvnw.cmd spring-boot:run`, puerto 9696) o acceso a
  `https://sciaf.uap.edu.bo`

---

## Desarrollo en el navegador

```bash
npm install
npm run dev            # http://localhost:5173
```

Las llamadas a `/api/**` las reenvía el proxy de Vite al backend, así que en
desarrollo no hay que pelear con CORS. Para apuntar a otro backend:

```bash
# .env.development
VITE_DEV_BACKEND=http://192.168.x.x:9696
```

## Generar el APK (Android Studio)

El APK se compila **manualmente desde Android Studio**. Desde `mobile/`:

```bash
npm run android
```

Ese único comando encadena los tres pasos:

```bash
npm run build          # 1. Vue + TypeScript  →  dist/
npx cap sync android   # 2. copia dist/ y los plugins al proyecto nativo
npx cap open android   # 3. abre Android Studio
```

Ya en Android Studio:

- **Probar en el teléfono** → botón `Run ▶` con el dispositivo conectado (Depuración USB activada).
- **Generar el APK** → `Build ▸ Build Bundle(s) / APK(s) ▸ Build APK(s)`.
  Queda en `android/app/build/outputs/apk/debug/app-debug.apk`.
- **APK firmado para repartir** → `Build ▸ Generate Signed Bundle / APK…` (requiere el keystore, ver pendientes).

Si solo cambiaste código web y Android Studio ya está abierto, basta con:

```bash
npm run sync           # build + cap sync, sin reabrir el IDE
```

> Después de **cada** cambio en el código web hay que ejecutar `npm run sync`.
> Android sirve una copia estática de `dist/`, no el servidor de Vite: sin
> sincronizar, la app seguirá mostrando la versión anterior.

### A qué servidor apunta el APK

Lo decide `.env.production`, que se lee **al compilar**. Si falta, el APK usa
rutas relativas: el servidor local de Capacitor devuelve `index.html` a todo y la
app cree que el backend responde cuando en realidad no existe.

**Pruebas locales** (configuración actual):

```properties
VITE_API_BASE=http://192.168.20.145:9696
```

Requisitos para que el teléfono llegue a esa IP:

1. Teléfono y PC en la misma red.
2. Regla de firewall en Windows (PowerShell como administrador, una sola vez):

   ```powershell
   New-NetFirewallRule -DisplayName "SCIAF backend 9696" -Direction Inbound `
     -Protocol TCP -LocalPort 9696 -Action Allow -Profile Private
   ```

3. Comprobar desde el navegador del teléfono: `http://192.168.20.145:9696/api/movil/salud`

El HTTP en claro está permitido **solo** para esa IP y otras direcciones privadas,
en `android/app/src/main/res/xml/network_security_config.xml`. El resto de
internet sigue exigiendo HTTPS.

**Producción** — un solo cambio:

```properties
VITE_API_BASE=https://sciaf.uap.edu.bo
```

Y opcionalmente, para volver a cerrar del todo el tráfico sin cifrar: poner
`allowMixedContent: false` en `capacitor.config.ts` y vaciar el `domain-config`
del `network_security_config.xml`.

---

## Estructura

```
src/
├── assets/          logos institucionales
├── components/      componentes compartidos
├── router/          rutas + guard de sesión
├── services/
│   ├── almacen.ts   persistencia (única puerta al almacenamiento del dispositivo)
│   ├── sesion.ts    tokens, perfil y deviceId
│   └── http.ts      axios + Bearer + refresh silencioso
├── stores/          Pinia: auth, red
├── theme/           paleta institucional (azul #144391 / rojo #E40613)
└── views/           pantallas
```

### Cómo funciona la sesión

1. `login` devuelve un **access token** (JWT, 24 h) y un **refresh token** (sin
   caducidad), que se guardan en el dispositivo.
2. Cada petición lleva `Authorization: Bearer <access>`.
3. Ante un `401`, el interceptor renueva la sesión **en silencio** y reintenta la
   petición. El usuario no se entera.
4. La sesión solo termina si: el usuario cierra sesión, un administrador revoca
   el dispositivo, o el usuario pasa a `INACTIVO`/`ELIMINADO`.
5. **Sin red la app entra igual**, con el perfil cacheado. Bloquear la entrada
   por no poder validar el token dejaría la app inútil justo en campo.

### Colores

| Uso | Color |
|---|---|
| Acción (botones, FAB, enlaces) | azul `#144391` |
| Identidad de marca (acentos, cabeceras) | rojo `#E40613` |
| Error / aviso / éxito | `#D92D20` / `#F79009` / `#12B76A` |

El rojo de marca **no** se usa para errores: si el mismo rojo significara
identidad y peligro, nadie sabría cuándo preocuparse.

---

## Endpoints que consume

Todos bajo `/api/movil` (cadena de seguridad propia, JWT, `STATELESS`):

| Método | Ruta | Auth | Permiso |
|---|---|---|---|
| POST | `/auth/login` | — | — |
| POST | `/auth/refresh` | — | — |
| GET | `/version` · `/salud` | — | — |
| GET | `/auth/me` | Bearer | — |
| POST | `/auth/logout` | Bearer | — |
| GET | `/ping` | Bearer | — |
| POST | `/escaneo/verificar` | Bearer | `MOV_ESCANER` |
| GET | `/activos/{codigo}` | Bearer | `MOV_ESCANER` |
| GET | `/activos/{codigo}/detalle` | Bearer | `MOV_ESCANER` |
| POST | `/activos/lote` | Bearer | `MOV_ESCANER` |

### Permisos

Los códigos `MOV_*` son ítems ocultos del catálogo `opcion_menu`, así que se
otorgan desde **la misma pantalla de permisos por usuario de la web**. No hay un
sistema de permisos aparte para el móvil.

ADMINISTRADOR y SUPER USUARIO los tienen todos. APOYO recibe por defecto
`MOV_ACCESO`, `MOV_ESCANER`, `MOV_BUSQUEDA`, `MOV_INFORME`, `MOV_INVENTARIO` y
`MOV_ASIGNACIONES`; quedan fuera `MOV_ASIGNACIONES_SUBIR` (escribe al VSIAF) y
`MOV_NOTIFICACIONES`.

---

## Pendiente antes de publicar

- [ ] Iconos y splash a partir de `activofijos.png` (`@capacitor/assets`)
- [ ] Keystore de firma para el APK de release
- [ ] Cambiar `movil.jwt.secret` en el servidor de producción
- [ ] Sustituir `@capacitor/preferences` por almacenamiento cifrado (Keystore)
      para los tokens — solo hay que tocar `services/almacen.ts`
