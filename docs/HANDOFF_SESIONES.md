# Handoff — Sesiones: inactividad, "mantener la sesión iniciada" y control por equipo

Estado al 2026-10-02: **compilado, sin commitear, sin probar en vivo.** Probarlo implica
arrancar contra bd_a3 (crea la tabla `sesion_usuario`) e iniciar sesión (escribe en
`log_acceso` y `sesion_usuario`).

## Qué se pidió

- Que la sesión no se cierre "sola" sin aviso y que "Atrás" no deje la portada con el
  usuario arriba sin poder hacer nada.
- "Que puedan guardar la sesión en sus compus, como Facebook", poder entrar desde varias
  computadoras, pero "con control de sesiones".

Decisiones del usuario: casilla **desmarcada** por defecto (se avisará a todos que vuelvan
a ingresar y la marquen), **5 días hábiles sin uso** (2026-10-02: primero se acordó 30 días, se cambió a 5 hábiles; sábado y domingo no cuentan, feriados sí), **sin límite** de equipos, **sí** avisar de equipos
nuevos.

## Cómo funciona

| Caso | Comportamiento |
|---|---|
| Ingresa sin marcar la casilla | Se cierra tras 30 min sin uso real; aviso "¿Sigue ahí?" 2 min antes. |
| Ingresa marcando la casilla | No se cierra por inactividad. Sobrevive a cerrar el navegador y a reinicios del servidor. Pide la contraseña tras 5 días hábiles **sin uso** (lun–vie; usado un viernes 15:00 vence el viernes siguiente 15:00). |
| Equipo nuevo | Si el usuario ya tenía sesiones y este navegador nunca entró con su cuenta: notificación + aviso en vivo a sus otros equipos. |
| Mis sesiones abiertas | Menú del avatar. Lista equipos (navegador/sistema, IP del último uso, ingreso, último uso). Cerrar una o todas las demás. |
| Administrador | Usuarios → ⋮ → Sesiones abiertas: ver y cerrar una o todas. Un no-ADMINISTRADOR no ve las de un ADMINISTRADOR. |
| Cambio de contraseña | Con "cerrar otras": todas. Sin marcar: igual se cierran los equipos con sesión mantenida. |
| Desactivar / eliminar / admin cierra sesiones | `forzarCierre` también cierra en la base (si no, la cookie volvería a entrar). |

"Uso real" = abrir pantallas + teclado/mouse informado por el navegador. Los pedidos
automáticos (presencia, autoguardado, refrescos) **no** mantienen la sesión.

## Piezas

- `model/entity/SesionUsuario` + `model/dao/ISesionUsuarioDao` — tabla `sesion_usuario`.
- `model/service/seguridad/SesionControlService` — registrar, cerrar, cerrar a distancia
  (memoria + base), listar, aviso de equipo nuevo, limpieza diaria 03:40.
- `model/service/seguridad/RecordarmeService` — cookies `SCIAF_RECORDAR` (serie:token,
  HttpOnly, SameSite=Lax; solo el hash en la base; el token **no** rota a propósito, ver
  comentario de la clase) y `SCIAF_EQUIPO` (identificador del navegador).
- `model/service/seguridad/SesionInactividadService` — reloj propio de inactividad.
- Interceptores (orden en `MvcConfig`): `SesionControlInterceptor` → `SesionInactividadInterceptor`
  → `SesionPermisosInterceptor` → `UsuarioAutenticadoInterceptor` → `PermisoOpcionInterceptor`.
- `config/SesionControlListener` — marca cerrada la fila cuando Tomcat destruye la sesión
  (salvo recordadas sin motivo explícito).
- Respuestas 401 llevan `X-Sciaf-Sesion: inactividad | revocada`; la portada recibe
  `/?sesion=inactividad | revocada | cerrada`.
- Front: `sciaf-inactividad.js` (aviso y capa de sesión cerrada), `sciaf-mi-cuenta.js`
  (Mis sesiones abiertas, `sciafSesiones.render` reutilizado en `usuario/vista.html`),
  `login_publico.js` (casilla, `location.replace`, motivo del cierre).

Propiedades (todas con default en código, no hace falta tocar `application.properties`):
`sciaf.sesion.inactividad-min=30`, `sciaf.sesion.aviso-seg=120`, `sciaf.sesion.recordar-dias-habiles=5`.

## Pruebas pendientes (en vivo)

Bajar `sciaf.sesion.inactividad-min=3` en `application-dev.properties` para probar rápido.

1. Ingresar sin casilla, dejar quieto: aviso al minuto, cierre a los 3, capa "se cerró por inactividad".
2. "Seguir conectado" en una pestaña cierra el aviso en las otras.
3. Ingresar con casilla, cerrar el navegador, volver: entra solo. Reiniciar el servidor: entra solo.
4. Entrar desde otro navegador: llega la notificación "equipo nuevo" al primero.
5. Mis sesiones abiertas → cerrar el otro equipo: ese equipo ve "Se cerró su sesión en este equipo".
6. Cambiar contraseña sin "cerrar otras": el equipo con casilla pide contraseña.
7. Administrador → Usuarios → Sesiones abiertas de otro: ver y cerrar.
8. Cerrar sesión, "Atrás": no vuelve a mostrar el sistema.

## Pendiente / conocido

- Un equipo cortado sigue recibiendo eventos SSE ya abiertos hasta que vence su emitter
  (máx. 1 h). El emitter es por usuario, no por sesión; ya pasaba con `forzarCierre`.
- Los feriados cuentan como días hábiles (no hay calendario de feriados en el sistema).
- La página antigua `/login` (`login/login.html`, plantilla con íconos de Facebook) no tiene
  la casilla; el ingreso real es el modal de la portada.
