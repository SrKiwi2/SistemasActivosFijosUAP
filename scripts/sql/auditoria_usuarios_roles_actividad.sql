-- Inventario de acceso y trabajo realizado. Solo lectura.
-- Ejecutar en bd_a3 con una cuenta que pueda leer estas tablas.
-- No muestra contraseñas, IP ni datos de identidad personal.

-- 1. Usuarios, rol, estado, permisos propios y actividad por módulo.
WITH permisos AS (
    SELECT uo.id_usuario,
           count(*) AS cantidad,
           string_agg(om.codigo, ', ' ORDER BY om.codigo) AS codigos
    FROM usuario_opcion uo
    JOIN opcion_menu om ON om.id_opcion = uo.id_opcion
    GROUP BY uo.id_usuario
), accesos AS (
    SELECT user_id AS id_usuario,
           max(fecha_hora) FILTER (WHERE exito = true) AS ultimo_ingreso,
           count(*) FILTER (WHERE exito = true) AS ingresos_exitosos
    FROM log_acceso
    WHERE user_id IS NOT NULL
    GROUP BY user_id
), trabajo AS (
    SELECT id_usuario,
           count(*) AS acciones_registradas,
           max(fecha) AS ultima_accion,
           count(*) FILTER (WHERE modulo = 'ACTIVO') AS acciones_activos,
           count(*) FILTER (WHERE modulo = 'ASIGNACION') AS acciones_asignaciones,
           count(*) FILTER (WHERE modulo = 'TRANSFERENCIA') AS acciones_transferencias
    FROM actividad_sistema
    WHERE id_usuario IS NOT NULL
    GROUP BY id_usuario
), movimientos AS (
    SELECT id_usuario,
           count(*) AS eventos_activo,
           max(fecha_evento) AS ultimo_evento_activo
    FROM historial_activo
    WHERE id_usuario IS NOT NULL
    GROUP BY id_usuario
)
SELECT u.id_usuario,
       u.usuario,
       r.nombre AS rol_actual,
       u._estado AS estado_actual,
       u._fecha_registro AS fecha_alta,
       coalesce(p.cantidad, 0) AS permisos_propios,
       p.codigos AS codigos_permisos_propios,
       a.ultimo_ingreso,
       coalesce(a.ingresos_exitosos, 0) AS ingresos_exitosos,
       coalesce(t.acciones_registradas, 0) AS acciones_registradas,
       t.ultima_accion,
       coalesce(t.acciones_activos, 0) AS acciones_activos,
       coalesce(t.acciones_asignaciones, 0) AS acciones_asignaciones,
       coalesce(t.acciones_transferencias, 0) AS acciones_transferencias,
       coalesce(m.eventos_activo, 0) AS eventos_activo,
       m.ultimo_evento_activo
FROM usuario u
LEFT JOIN rol r ON r.id_rol = u.id_rol
LEFT JOIN permisos p ON p.id_usuario = u.id_usuario
LEFT JOIN accesos a ON a.id_usuario = u.id_usuario
LEFT JOIN trabajo t ON t.id_usuario = u.id_usuario
LEFT JOIN movimientos m ON m.id_usuario = u.id_usuario
ORDER BY r.nombre NULLS LAST, u.usuario;

-- 2. Roles existentes, incluidos los que hoy no tienen usuarios.
SELECT r.id_rol, r.nombre, r._estado AS estado,
       count(u.id_usuario) AS usuarios_total,
       count(u.id_usuario) FILTER (WHERE u._estado = 'ACTIVO') AS usuarios_activos
FROM rol r
LEFT JOIN usuario u ON u.id_rol = r.id_rol
GROUP BY r.id_rol, r.nombre, r._estado
ORDER BY r.nombre;

-- 3. Acciones que constan en la bitácora; incluye nombres históricos sin cuenta vigente.
SELECT coalesce(a.usuario, '(sin usuario)') AS usuario_registrado,
       a.rol AS rol_al_operar,
       a.modulo,
       a.accion,
       count(*) AS veces,
       min(a.fecha) AS primera_vez,
       max(a.fecha) AS ultima_vez
FROM actividad_sistema a
GROUP BY a.usuario, a.rol, a.modulo, a.accion
ORDER BY ultima_vez DESC;

-- 4. Usuarios cuyos permisos fueron modificados y quién realizó el cambio.
SELECT h.usuario AS usuario_afectado, h.accion, h.actor,
       h.rol_actor, h.fecha, h.agregados, h.quitados
FROM historial_permiso_usuario h
ORDER BY h.fecha DESC;

-- 5. Eventos por tipo y usuario; un movimiento puede generar varios eventos por activo.
SELECT coalesce(u.usuario, h.nombre_usuario, '(sin usuario)') AS usuario,
       h.tipo_evento,
       count(*) AS eventos,
       min(h.fecha_evento) AS primero,
       max(h.fecha_evento) AS ultimo
FROM historial_activo h
LEFT JOIN usuario u ON u.id_usuario = h.id_usuario
GROUP BY coalesce(u.usuario, h.nombre_usuario, '(sin usuario)'), h.tipo_evento
ORDER BY usuario, h.tipo_evento;
