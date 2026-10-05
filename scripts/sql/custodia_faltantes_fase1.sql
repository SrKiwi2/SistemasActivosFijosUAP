-- =====================================================================
-- Custodia de faltantes · Fase 1 (datos)
--
-- Correr con el usuario DUEÑO de las tablas (postgres): el DDL no lo
-- admite un usuario que solo tenga UPDATE.
--
-- Orden recomendado:
--   1. Desplegar y arrancar la aplicación una vez: hbm2ddl crea solo
--        oficina.es_custodia, responsable.es_custodia,
--        hallazgo_inventario.origen, id_oficina_origen, documento_respaldo,
--        fecha_documento, id_responsable_custodia, fecha_envio_custodia,
--        usuario_envio_custodia
--   2. Correr este script.
--
-- Lo que hbm2ddl NO hace y por eso va aquí:
--   · quitar el NOT NULL de hallazgo_inventario.id_inventario (un faltante
--     registrado directo no tiene levantamiento);
--   · completar origen y oficina de origen en los hallazgos que ya existen;
--   · el índice que impide dos faltantes pendientes del mismo bien.
--
-- Idempotente: se puede correr más de una vez sin daño.
-- =====================================================================

-- 0) Las columnas nuevas tienen que existir (paso 1 del orden de arriba).
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_name = 'hallazgo_inventario' AND column_name = 'id_oficina_origen') THEN
        RAISE EXCEPTION 'Falta hallazgo_inventario.id_oficina_origen: arrancar la aplicación una vez antes de este script';
    END IF;
END $$;

-- 1) Un hallazgo ya no exige levantamiento.
ALTER TABLE hallazgo_inventario ALTER COLUMN id_inventario DROP NOT NULL;

-- 2) Hallazgos existentes: todos nacieron de un levantamiento.
UPDATE hallazgo_inventario h
   SET origen = 'LEVANTAMIENTO'
 WHERE h.origen IS NULL
   AND h.id_inventario IS NOT NULL;

UPDATE hallazgo_inventario h
   SET id_oficina_origen = i.id_oficina
  FROM inventario i
 WHERE i.id_inventario = h.id_inventario
   AND h.id_oficina_origen IS NULL;

-- 3) Por si alguna marca quedó en NULL (hbm2ddl sobre una tabla con filas).
UPDATE oficina     SET es_custodia = false WHERE es_custodia IS NULL;
UPDATE responsable SET es_custodia = false WHERE es_custodia IS NULL;

-- 4) CHECK sobre estado_hallazgo: si alguno existe, tiene que admitir EN_CUSTODIA.
--    Solo avisa; no se toca a ciegas una restricción que no creamos nosotros.
DO $$
DECLARE r record;
BEGIN
    FOR r IN SELECT conname, pg_get_constraintdef(oid) AS def
               FROM pg_constraint
              WHERE conrelid = 'hallazgo_inventario'::regclass
                AND contype = 'c'
                AND pg_get_constraintdef(oid) ILIKE '%estado_hallazgo%'
    LOOP
        IF r.def NOT ILIKE '%EN_CUSTODIA%' THEN
            RAISE WARNING 'El CHECK % no admite EN_CUSTODIA: %', r.conname, r.def;
        END IF;
    END LOOP;
END $$;

-- 5) Un bien tiene a lo sumo UN faltante pendiente (ABIERTO o EN_CUSTODIA).
--    Si ya hay duplicados, NO se crea el índice: se listan para resolverlos a mano
--    (resolver o reabrir uno de los dos desde la vista Faltantes) y se vuelve a correr.
DO $$
DECLARE dup int;
BEGIN
    SELECT count(*) INTO dup FROM (
        SELECT id_activo
          FROM hallazgo_inventario
         WHERE tipo_hallazgo = 'FALTANTE'
           AND estado_hallazgo IN ('ABIERTO', 'EN_CUSTODIA')
           AND id_activo IS NOT NULL
         GROUP BY id_activo
        HAVING count(*) > 1
    ) x;

    IF dup > 0 THEN
        RAISE WARNING '% activo(s) con más de un faltante pendiente: índice NO creado. Ver la consulta 6.', dup;
    ELSE
        CREATE UNIQUE INDEX IF NOT EXISTS uk_hall_faltante_pendiente
            ON hallazgo_inventario (id_activo)
         WHERE tipo_hallazgo = 'FALTANTE'
           AND estado_hallazgo IN ('ABIERTO', 'EN_CUSTODIA');
        RAISE NOTICE 'Índice uk_hall_faltante_pendiente listo';
    END IF;
END $$;

-- 6) Duplicados (debe salir vacío).
SELECT h.id_activo, a.codigo, count(*) AS pendientes,
       string_agg(h.id_hallazgo::text, ', ' ORDER BY h.id_hallazgo) AS hallazgos
  FROM hallazgo_inventario h
  JOIN activo a ON a.id_activo = h.id_activo
 WHERE h.tipo_hallazgo = 'FALTANTE'
   AND h.estado_hallazgo IN ('ABIERTO', 'EN_CUSTODIA')
 GROUP BY h.id_activo, a.codigo
HAVING count(*) > 1;

-- 7) Verificación. Esperado: 0 | 0 | 0 | YES
SELECT
  (SELECT count(*) FROM hallazgo_inventario WHERE origen IS NULL)            AS sin_origen,
  (SELECT count(*) FROM hallazgo_inventario WHERE id_oficina_origen IS NULL) AS sin_oficina_origen,
  (SELECT count(*) FROM oficina WHERE es_custodia)                           AS oficinas_custodia,
  (SELECT is_nullable FROM information_schema.columns
    WHERE table_name = 'hallazgo_inventario' AND column_name = 'id_inventario') AS inventario_nullable;
