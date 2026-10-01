-- ════════════════════════════════════════════════════════════════════════════
-- Custodia de faltantes · fase 2
--
-- Correr con el usuario postgres (dueño de las tablas): con el usuario de la app
-- el DDL falla. Es idempotente: se puede volver a correr sin efectos.
-- Requiere que la fase 1 ya haya creado las columnas (arranque de la app o el
-- script manual del 28-sep-2026).
--
-- Todo va en una transacción: si un paso falla, no queda nada a medias.
-- Sirve en pgAdmin/DBeaver o en psql (en psql, con -v ON_ERROR_STOP=1).
-- ════════════════════════════════════════════════════════════════════════════

BEGIN;

-- 1. Lo que faltó de la fase 1 ───────────────────────────────────────────────

-- Un faltante registrado en forma directa no tiene levantamiento.
ALTER TABLE hallazgo_inventario ALTER COLUMN id_inventario DROP NOT NULL;

-- Un bien tiene a lo sumo un faltante pendiente (abierto o en custodia).
-- Si falla por duplicados, resolverlos desde la vista Faltantes y volver a correr.
CREATE UNIQUE INDEX IF NOT EXISTS uk_hall_faltante_pendiente
    ON hallazgo_inventario (id_activo)
    WHERE tipo_hallazgo = 'FALTANTE' AND estado_hallazgo IN ('ABIERTO', 'EN_CUSTODIA');

-- 2. Las oficinas de faltantes que ya existen en el VSIAF ────────────────────
-- Se ubican por unidad + código (la clave del VSIAF), no por id.

UPDATE oficina o
   SET es_custodia = true
  FROM predio p
 WHERE p.id_predio = o.id_predio
   AND (p.unidad, o.cod_ofi) IN (('CULP', 372), ('CUSP', 194), ('POST', 42))
   AND NOT o.es_custodia;

-- Todos los responsables de una oficina de faltantes son de custodia.
UPDATE responsable r
   SET es_custodia = true
  FROM oficina o
 WHERE o.id_oficina = r.id_oficina
   AND o.es_custodia
   AND NOT r.es_custodia;

-- 3. Reglas que protegen de duplicados ───────────────────────────────────────

-- Una sola oficina de faltantes vigente por predio: dos pedidos a la vez no
-- pueden crear dos.
CREATE UNIQUE INDEX IF NOT EXISTS uk_oficina_custodia_predio
    ON oficina (id_predio)
    WHERE es_custodia AND _estado = 'ACTIVO';

-- Una persona figura una sola vez en cada oficina de faltantes.
CREATE UNIQUE INDEX IF NOT EXISTS uk_resp_custodia_persona
    ON responsable (id_oficina, id_persona)
    WHERE es_custodia;

-- 4. Verificación ────────────────────────────────────────────────────────────
-- Tienen que salir 3 filas (CULP 372, CUSP 194, POST 42), todas ACTIVO y con
-- responsables_custodia = responsables.

SELECT p.unidad, o.cod_ofi, o.nombre, o._estado,
       count(r.id_responsable)                        AS responsables,
       count(r.id_responsable) FILTER (WHERE r.es_custodia) AS responsables_custodia
  FROM oficina o
  JOIN predio p ON p.id_predio = o.id_predio
  LEFT JOIN responsable r ON r.id_oficina = o.id_oficina
 WHERE o.es_custodia
 GROUP BY p.unidad, o.cod_ofi, o.nombre, o._estado
 ORDER BY p.unidad;

-- Tiene que dar: YES | 3 (los tres índices nuevos).
SELECT (SELECT is_nullable FROM information_schema.columns
         WHERE table_name = 'hallazgo_inventario' AND column_name = 'id_inventario') AS id_inventario_admite_vacio,
       (SELECT count(*) FROM pg_indexes
         WHERE indexname IN ('uk_hall_faltante_pendiente', 'uk_oficina_custodia_predio',
                             'uk_resp_custodia_persona'))                            AS indices;

COMMIT;
