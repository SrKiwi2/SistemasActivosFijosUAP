# Traspaso — Faltantes visibles, Seguimiento de Activo, Usuarios, Permisos en vivo, Gestión de Menú

> Estado al 01-oct-2026. Todo **compilado y sin commitear**. No se levantó la app contra
> `bd_a3` (es producción). Las plantillas se renderizaron sin base con una prueba temporal
> (ya borrada) y los scripts pasaron `node --check`.

## 1. Transferencia por faltante, distinguida

- `Transferencia.getMotivoFaltante()`: `FALTANTE` si el destino es una oficina de faltantes
  (`es_custodia`), `DEVOLUCION_FALTANTE` si el origen lo es. **Sin columna nueva**: también
  marca las ya hechas.
- Se ve en rojo en: Seguimiento → Transferencias (fila, insignia, filtro "Motivo", aviso en
  el detalle, cabecera roja), Historial de Transferencias, Historial Activo, y el Word de la
  transferencia (subtítulo rojo).
- De paso: los filtros de Seguimiento → Transferencias (tipo, estado, fechas, buscar) se
  mandaban pero el controlador los ignoraba. Ahora filtran.

## 2. Seguimiento de Activo (ruta)

- Menú: Seguimiento y Consultas → Historial → **Seguimiento de Activo (ruta)**
  (`opcion_ruta_activo`, `/administracion/ruta-activo/vista`). Lo crea el seeder al arrancar.
  APOYO lo recibe por plantilla; a quien tenga permisos propios hay que asignárselo.
- `RutaActivoService` junta transferencias, actas, traslados/separaciones de actas,
  `historial_activo`, faltantes, levantamientos y bloqueos **por id de activo** (encuentra
  también por un código anterior). Arma la línea de tiempo y las **estancias** por oficina
  (desde/hasta/días/responsables).
- Límite: lo movido directamente en el VSIAF no deja rastro; si la oficina actual no coincide
  con la última registrada, se agrega una estancia "cambio en el VSIAF" con fecha aproximada
  (`fec_mod`).
- Atajo desde el detalle de un activo en Seguimiento → Transferencias.

## 3. Usuarios

- `GestionUsuariosService` concentra las reglas: solo ADMINISTRADOR toca/crea ADMINISTRADOR;
  nadie se desactiva/elimina/cambia el rol a sí mismo; nunca queda el sistema sin
  ADMINISTRADOR activo; contraseña ≥ 8 con letras y números; nombre de usuario único en
  cualquier estado; una persona, un usuario.
- Pantalla: resumen (conectados, activos, inactivos, con intentos fallidos), filtros, último
  ingreso, permisos propios vs plantilla. Acciones: editar, permisos, restablecer contraseña
  (generada o escrita, se muestra una vez), activar/desactivar, cerrar sesiones, historial
  de accesos, eliminar. **Ya no se muestra el hash de la contraseña.**
- "Cambiar mi contraseña" en el menú del avatar (`/adm/mi-cuenta/contrasena`).
- `log_acceso` existía pero no se usaba: ahora el login registra cada intento.
- Corregido: `/administracion/usuario/generar-usuarios` estaba abierto sin sesión → exige
  ADMINISTRADOR. El arranque recreaba `admin1/admin2` con la clave por defecto si estaban
  inactivos → ahora mira todos los estados.

### 3.b Sin acceso, respaldo y auditoría de permisos (01-oct, tarde)

- **Antes:** "Quitar todos" guardaba la lista vacía y eso significaba "usar la plantilla del
  rol": el usuario seguía viendo el mismo menú y no quedaba rastro.
- **Ahora:** guardar sin ninguna opción = **sin acceso**: se desactiva, se cierran sus
  sesiones y se guarda un **respaldo** de sus permisos. **Desactivar** hace lo mismo.
  **Activar** muestra el respaldo y deja elegir si restaurarlo (por omisión sí). Volver a la
  plantilla del rol es un botón aparte ("Usar solo la plantilla del rol").
- **Auditoría:** tabla `historial_permiso_usuario` (entidad `HistorialPermisoUsuario`,
  `AuditoriaPermisosService`): agregados, quitados, respaldo, quién (y su rol), IP, pantalla
  de origen, fecha. Registra: guardar permisos, plantilla, sin acceso, desactivar, activar,
  cambio de rol, eliminar usuario, opción eliminada del menú (a cada usuario que la tenía) y
  la migración automática del seeder. Se ve en Usuarios → ⋮ → Historial de permisos, y desde
  el modal de Permisos.
- **Antes de probar:** correr `scripts/sql/historial_permiso_usuario.sql` con `postgres`
  (`t | t`). La auditoría va en la misma transacción que el cambio: sin la tabla, guardar
  permisos y activar/desactivar fallan. La app solo tiene SELECT/INSERT sobre ella.

### 3.c Usuarios conectados (Supervisión)

- Menú Supervisión → **Usuarios conectados** (`opcion_conectados`,
  `/administracion/conectados/vista`); solo ADMINISTRADOR / SUPER USUARIO. Lo crea el seeder.
- Cada navegador con sesión manda `POST /api/presencia` (`sciaf-presencia.js`) al cambiar de
  pantalla, cada 30 s y al pasar a segundo plano: pantalla actual, pestañas abiertas,
  segundos sin teclado/mouse. `PresenciaService` lo guarda **en memoria** (no toca la base;
  un reinicio lo vacía hasta el próximo aviso). Ausente = pestaña en segundo plano o 5 min sin
  actividad. Sale de la lista al cerrar sesión (`PresenciaSesionListener`) o 100 s sin avisos.
- La pantalla se refresca cada 5 s: tarjetas por sesión (pantalla actual y desde cuándo,
  pestañas, IP/navegador, recorrido reciente), filtro por pantalla, y "Cerrar sesión"
  (mismas reglas que en Usuarios).

## 4. Permisos en vivo

- `SesionPermisosService` + `SesionPermisosInterceptor`: cada sesión guarda la versión de
  permisos; si el administrador cambia permisos/rol/estado de alguien, o el catálogo del
  menú, la próxima petición recarga la sesión desde la base. La versión sube **después del
  commit**.
- SSE `permisos` (por usuario) y `menu` (a todos) → `sciaf-menu-vivo.js` pide
  `/adm/menu/items`, reemplaza el menú, avisa qué se agregó/quitó y cierra las pestañas de
  módulos perdidos. Desactivar o cerrar sesiones saca al usuario al login.
- Las sesiones abiertas antes del despliegue se ponen al día solas en su primera petición.

## 5. Gestión de Menú

- `OpcionMenuSeeder` **solo crea lo que falta**: antes pisaba nombre/ícono/orden/visibilidad
  en cada reinicio. Una vez: los usuarios con `opcion_trInterna`/`opcion_trExterna` pasan a
  `opcion_transferencia` y esas dos quedan ELIMINADAS (ya no aparecen en permisos).
- Estados de un nodo (`_estado`): ACTIVO / BLOQUEADO (nadie entra salvo ADMINISTRADOR; un
  grupo bloquea todo su contenido) / ELIMINADO (borrado lógico, papelera, restaurable).
- Pantalla: arrastrar y soltar (también entre grupos/secciones) con "Guardar orden",
  selector de íconos Tabler, colores, vista previa, autocompletado de URL con las rutas
  reales y aviso de "URL no encontrada". Gestión de Menú y Usuarios están protegidos.
- La plantilla de SUPER USUARIO ya no incluye Gestión de Menú (es del bloque de
  administración del sistema). Quien la tenga asignada a mano la conserva.

## Para probar en vivo (no se hizo)

1. Arrancar en otro puerto (`--server.port=9797`). El seeder retira las dos opciones viejas
   y crea `opcion_ruta_activo` (escribe en `opcion_menu` y `usuario_opcion` de `bd_a3`).
2. Dos navegadores: admin y un usuario APOYO. Quitar/dar un permiso al APOYO → su menú cambia
   sin recargar. Bloquear un módulo que tenga abierto → se cierra con aviso.
3. Seguimiento de Activo con un bien que haya ido a CULP 372.
4. Restablecer contraseña de un usuario de prueba con "cerrar sesiones".

## Pendiente conocido (anterior a este trabajo)

- `WordActaSmokeTest` falla: `WordAsignacionActivoService` necesita `responsableEntregaService`
  y la prueba no lo provee.
