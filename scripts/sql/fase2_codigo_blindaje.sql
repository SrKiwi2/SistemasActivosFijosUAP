-- ============================================================================
-- fase2_codigo_blindaje.sql
-- ----------------------------------------------------------------------------
-- Blindaje del código de activo para soportar la CANCELACIÓN (Opción A):
--   - Al cancelar un activo, su código se mueve a 'codigo_anulado' y 'codigo'
--     queda en NULL, liberando el correlativo para que vuelva a estar disponible.
--   - 'codigo' pasa a ser NULLABLE y UNIQUE. En PostgreSQL múltiples NULL NO
--     chocan en un índice único, así que los cancelados no interfieren y los
--     activos vivos quedan garantizados como únicos.
--
-- Ejecutar UNA vez contra bd_a4, ANTES de desplegar la Fase 3 (cancelación).
--   psql -h virtual.uap.edu.bo -p 5432 -U <user> -d bd_a4 -f fase2_codigo_blindaje.sql
--   (o pegar bloque por bloque en pgAdmin / DBeaver)
--
-- Es idempotente: se puede correr más de una vez sin error.
-- hbm2ddl NO hace esto por sí solo (no quita el NOT NULL ni agrega el UNIQUE
-- de forma confiable sobre una tabla ya poblada), por eso se hace manual.
-- ============================================================================

-- 0) PRE-CHEQUEO OBLIGATORIO: debe devolver 0 filas.
--    Si devuelve algo, hay códigos duplicados: resolverlos ANTES del paso 3,
--    o el UNIQUE fallará.
SELECT codigo, COUNT(*) AS veces, array_agg(id_activo) AS ids
FROM activo
WHERE codigo IS NOT NULL AND codigo <> ''
GROUP BY codigo
HAVING COUNT(*) > 1;

-- 1) Columna de respaldo del código al cancelar.
--    (hbm2ddl también la crea al arrancar con la entidad nueva; IF NOT EXISTS por si acaso.)
ALTER TABLE activo ADD COLUMN IF NOT EXISTS codigo_anulado varchar(60);

-- 2) 'codigo' pasa a NULLABLE (necesario para liberar el código al cancelar).
ALTER TABLE activo ALTER COLUMN codigo DROP NOT NULL;

-- 3) UNIQUE en 'codigo' (idempotente). Los NULL de los cancelados no chocan entre sí.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_activo_codigo') THEN
        ALTER TABLE activo ADD CONSTRAINT uk_activo_codigo UNIQUE (codigo);
    END IF;
END $$;

-- 4) (OPCIONAL) El índice plano idx_activo_codigo queda redundante con el UNIQUE
--    (que ya crea su propio índice). Puedes dejarlo o eliminarlo:
-- DROP INDEX IF EXISTS idx_activo_codigo;

-- ----------------------------------------------------------------------------
-- VERIFICACIÓN POST-MIGRACIÓN
-- ----------------------------------------------------------------------------
-- Debe mostrar codigo nullable = 'YES' y la columna codigo_anulado presente:
SELECT column_name, is_nullable, data_type, character_maximum_length
FROM information_schema.columns
WHERE table_name = 'activo' AND column_name IN ('codigo', 'codigo_anulado')
ORDER BY column_name;

-- Debe listar la constraint uk_activo_codigo:
SELECT conname, contype
FROM pg_constraint
WHERE conrelid = 'activo'::regclass AND conname = 'uk_activo_codigo';
