-- ============================================================================
-- asignaciones_fase1.sql
-- ----------------------------------------------------------------------------
-- Objetivo:
--   Dejar la base lista para la edición de actas del módulo Movimientos /
--   Asignaciones. Son cuatro cosas que Hibernate NO puede hacer solo:
--
--     1) Rellenar detalle_asignacion.estado_detalle en las filas ya existentes
--        (hbm2ddl agrega la columna, pero la deja en NULL).
--     2) Detectar activos que hoy figuran en más de un acta.
--     3) Crear el índice único parcial que impide que eso vuelva a pasar
--        (Postgres: índice con WHERE; JPA no lo expresa).
--     4) Normalizar codigo_completo, que se venía guardando con dos formatos.
--
--   El paso 5 es opcional: numera las actas viejas para que también tengan
--   identificador propio.
--
-- IMPORTANTE:
--   El paso 3 FALLA a propósito si hay duplicados. Resolvé primero el paso 2:
--   ese error no es un problema del script, es el dato que estaba mal.
--
-- Uso:
--   psql -h virtual.uap.edu.bo -p 5432 -U <user> -d bd_a4 -f asignaciones_fase1.sql
--   (o bloque por bloque en pgAdmin / DBeaver, revisando cada resultado)
--
-- Requisito: la aplicación ya tiene que haber arrancado al menos una vez con el
--   código de la Fase 1, para que hbm2ddl haya creado las columnas nuevas
--   (detalle_asignacion.estado_detalle, activo.sinc_vsiaf, dbf_cola_orden).
-- ============================================================================


-- ----------------------------------------------------------------------------
-- 1) Backfill de estado_detalle
--    Toda línea existente ubica hoy a su activo, así que arranca VIGENTE.
-- ----------------------------------------------------------------------------
UPDATE detalle_asignacion
   SET estado_detalle = 'VIGENTE'
 WHERE estado_detalle IS NULL;


-- ----------------------------------------------------------------------------
-- 2) DIAGNÓSTICO: activos que figuran en más de un acta vigente
--
--    Si esto devuelve filas, el paso 3 va a fallar. Para cada activo hay que
--    decidir cuál acta es la vigente y marcar la otra línea como TRASLADADO:
--
--      UPDATE detalle_asignacion SET estado_detalle = 'TRASLADADO'
--       WHERE id_detalle = <la que ya no corresponde>;
--
--    No borres la fila: el snapshot de costo y descripción que guarda es la
--    prueba de qué decía el acta cuando se firmó.
-- ----------------------------------------------------------------------------
SELECT d.id_activo,
       a.codigo,
       count(*)                                        AS actas_vigentes,
       string_agg(DISTINCT asg.numero_asignacion, ', ') AS numeros,
       string_agg(DISTINCT asg.codigo_completo,   ', ') AS documentos,
       string_agg(d.id_detalle::text, ', ' ORDER BY d.id_detalle) AS detalles
  FROM detalle_asignacion d
  JOIN activo            a   ON a.id_activo = d.id_activo
  JOIN asignacion_activo asg ON asg.id_asignacion_activo = d.id_asignacion_activo
 WHERE d.estado_detalle = 'VIGENTE'
 GROUP BY d.id_activo, a.codigo
HAVING count(*) > 1
 ORDER BY count(*) DESC, a.codigo;


-- ----------------------------------------------------------------------------
-- 3) El blindaje: un activo, un acta vigente
--
--    Es lo que sostiene todas las operaciones de edición del acta (separar,
--    incorporar, trasladar). Sin esto, un fallo a mitad de camino deja el
--    activo en dos actas y las consultas que esperan una sola revientan con
--    NonUniqueResultException en producción.
--
--    Va como índice parcial porque las líneas TRASLADADO sí pueden repetirse:
--    son la historia del acta original.
-- ----------------------------------------------------------------------------
CREATE UNIQUE INDEX IF NOT EXISTS ux_detalle_asignacion_activo_vigente
    ON detalle_asignacion (id_activo)
 WHERE estado_detalle = 'VIGENTE';


-- ----------------------------------------------------------------------------
-- 4) Normalizar codigo_completo
--
--    Venía en dos formatos según el camino de creación: '(Prev. 1234)' desde
--    Pendientes y 'Prev. 1234' desde Reportes. Ahora se guarda siempre sin
--    paréntesis y los agrega el acta al imprimirse
--    (AsignacionActivo.getEtiquetaDocumento), así que los documentos ya
--    emitidos se siguen viendo igual.
--
--    Revisá primero qué va a cambiar:
--
--      SELECT id_asignacion_activo, codigo_completo,
--             btrim(btrim(codigo_completo), '()') AS quedaria
--        FROM asignacion_activo
--       WHERE codigo_completo LIKE '(%)';
-- ----------------------------------------------------------------------------
UPDATE asignacion_activo
   SET codigo_completo = btrim(btrim(btrim(codigo_completo), '()'))
 WHERE codigo_completo LIKE '(%)';


-- ----------------------------------------------------------------------------
-- 5) El número del acta sale del NÚMERO DE DOCUMENTO, no de un correlativo
--
--    numero_asignacion traía correlativos 'ASG-2026-0143' que no salen de ningún
--    lado del negocio. El identificador de la asignación es el número que se carga
--    en la ventana "Asignar documento" de Pendientes; el acta se numera
--    ASG-<gestión>-<ese número>. Tener las dos numeraciones deja el mismo
--    documento con un identificador en el papel y otro en la pantalla.
--
--    5a) Fuera el único. El número de documento SE REPITE: hoy 38 actas están en
--        'S/N' y los números 1206 y 697 aparecen dos veces cada uno. Con el único
--        puesto, el UPDATE de 5b falla y las actas nuevas también.
--
--        Hibernate lo creó como CONSTRAINT, no como índice suelto. Un DROP INDEX
--        sobre el índice que respalda un constraint SIEMPRE falla:
--
--          ERROR: cannot drop index ... because constraint ... requires it
--
--        Por eso primero va el DROP CONSTRAINT, que se lleva el índice con él. El
--        DROP INDEX queda después y solo actúa si en algún entorno el índice
--        existiera suelto; si el constraint ya lo borró, no hace nada.
--
--        Corré esto como 'postgres': el DDL exige ser DUEÑO de la tabla, y no
--        alcanza con tener permisos de UPDATE sobre los datos.
-- ----------------------------------------------------------------------------
ALTER TABLE asignacion_activo
    DROP CONSTRAINT IF EXISTS asignacion_activo_numero_asignacion_key;

DROP INDEX IF EXISTS asignacion_activo_numero_asignacion_key;

--    5b) Reconstruye el número con la misma regla que aplica la aplicación.
--
--        Las actas sin número de documento ('S/N') quedan SIN numerar: todavía no
--        son una asignación, siguen en la bandeja de Pendientes y no llegan al
--        módulo de Asignaciones. Hoy son 38, y ninguna tiene bienes en el VSIAF.
--
--        Antes de correrlo, mirá qué va a cambiar y a qué:
--
--          SELECT numero_asignacion AS antes,
--                 CASE WHEN codigo_documento IS NULL OR upper(codigo_documento) = 'S/N'
--                      THEN NULL
--                      ELSE 'ASG-' || extract(year FROM fecha_asignacion)::int
--                                  || '-' || codigo_documento END AS despues
--            FROM asignacion_activo
--           ORDER BY id_asignacion_activo DESC LIMIT 20;
--
--        Si algún ASG-2026-XXXX ya se usó en papel o en otro sistema, pará acá:
--        esto lo pisa y no hay vuelta atrás.
--
--        El número se usa tal como se cargó, sin rellenar con ceros: rellenarlo lo
--        convertiría en otro número (65 no es 0065).
UPDATE asignacion_activo
   SET numero_asignacion = CASE
           WHEN codigo_documento IS NULL OR upper(trim(codigo_documento)) = 'S/N' THEN NULL
           ELSE left('ASG-' || extract(year FROM fecha_asignacion)::int
                            || '-' || trim(codigo_documento), 30)
       END
 WHERE fecha_asignacion IS NOT NULL;


-- ----------------------------------------------------------------------------
-- 6) AVISO para la Fase 3 — todavía NO hace falta correrlo
--
--    La tabla tiene dos CHECK que limitan los valores permitidos:
--
--      chk_tipo_asignacion    → NUEVA | REASIGNACION | DEVOLUCION
--      chk_estado_asignacion  → ACTIVA | ANULADA | DEVUELTA
--
--    Cuando se implemente separar / incorporar / trasladar, el acta hija va a
--    querer guardarse con tipo 'SEPARACION' y la original pasar a 'MODIFICADA'.
--    Con estos CHECK puestos, la base rechaza el INSERT y la operación se cae
--    entera. Hay que ampliarlos ANTES de esa fase, no durante:
--
--      ALTER TABLE asignacion_activo DROP CONSTRAINT chk_tipo_asignacion;
--      ALTER TABLE asignacion_activo ADD CONSTRAINT chk_tipo_asignacion
--          CHECK (tipo_asignacion IN ('NUEVA','REASIGNACION','DEVOLUCION',
--                                     'SEPARACION','REGULARIZACION'));
--
--      ALTER TABLE asignacion_activo DROP CONSTRAINT chk_estado_asignacion;
--      ALTER TABLE asignacion_activo ADD CONSTRAINT chk_estado_asignacion
--          CHECK (estado_asignacion IN ('ACTIVA','ANULADA','DEVUELTA','MODIFICADA'));
-- ----------------------------------------------------------------------------


-- ----------------------------------------------------------------------------
-- 7) VERIFICACIÓN final
-- ----------------------------------------------------------------------------
SELECT (SELECT count(*) FROM detalle_asignacion WHERE estado_detalle IS NULL)      AS detalles_sin_estado,
       (SELECT count(*) FROM asignacion_activo
         WHERE numero_asignacion IS NOT NULL
           AND numero_asignacion NOT LIKE '%-' || trim(codigo_documento))          AS numeros_que_no_calzan,
       (SELECT count(*) FROM asignacion_activo
         WHERE numero_asignacion IS NOT NULL
           AND upper(trim(codigo_documento)) = 'S/N')                              AS sn_numeradas_por_error,
       (SELECT count(*) FROM asignacion_activo  WHERE codigo_completo LIKE '(%)')  AS documentos_con_parentesis,
       (SELECT count(*) FROM pg_indexes
         WHERE indexname = 'ux_detalle_asignacion_activo_vigente')                 AS blindaje_activo,
       (SELECT count(*) FROM pg_indexes
         WHERE indexname = 'asignacion_activo_numero_asignacion_key')              AS unico_que_sobra;
-- Esperado: 0, 0, 0, 0, 1, 0
