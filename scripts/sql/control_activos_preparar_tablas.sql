-- =====================================================================
-- Módulo: Control de Activos por Responsable
-- Ejecutar UNA VEZ, ANTES de arrancar la aplicación con el módulo nuevo.
--
-- Por qué hace falta
-- ------------------
-- Las tablas `inventario` y `hallazgo_inventario` ya existían en la base:
-- las creó `hbm2ddl.auto=update` a partir de dos entidades que nadie usaba.
--
-- La entidad `Inventario` declaraba un campo `estado` que choca con el
-- `estado` heredado de AuditoriaConfig (columna `_estado`). Al renombrarlo a
-- `estadoLevantamiento`, hbm2ddl crea la columna nueva pero NUNCA borra la
-- vieja: quedaría `inventario.estado NOT NULL` sin que nada la complete, y
-- todo INSERT fallaría.
--
-- Este script es idempotente: se puede correr más de una vez sin daño.
-- =====================================================================

-- 1) Renombrar la columna vieja si todavía no existe la nueva.
--    Preserva cualquier dato que hubiera (no debería haber ninguno).
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
                WHERE table_name = 'inventario' AND column_name = 'estado')
       AND NOT EXISTS (SELECT 1 FROM information_schema.columns
                WHERE table_name = 'inventario' AND column_name = 'estado_levantamiento')
    THEN
        ALTER TABLE inventario RENAME COLUMN estado TO estado_levantamiento;
        RAISE NOTICE 'inventario.estado renombrada a estado_levantamiento';
    END IF;
END $$;

-- 2) Si la aplicación ya arrancó y hbm2ddl creó la columna nueva al lado de la
--    vieja, la vieja queda huérfana: soltarle el NOT NULL para que no bloquee.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
                WHERE table_name = 'inventario' AND column_name = 'estado')
       AND EXISTS (SELECT 1 FROM information_schema.columns
                WHERE table_name = 'inventario' AND column_name = 'estado_levantamiento')
    THEN
        ALTER TABLE inventario ALTER COLUMN estado DROP NOT NULL;
        RAISE NOTICE 'inventario.estado (huérfana) ya no es NOT NULL';
    END IF;
END $$;

-- 3) Comprobación. Debe listar estado_levantamiento; si además aparece `estado`,
--    tiene que salir con is_nullable = YES.
SELECT column_name, data_type, is_nullable
FROM information_schema.columns
WHERE table_name = 'inventario'
  AND column_name IN ('estado', 'estado_levantamiento', '_estado')
ORDER BY column_name;

-- =====================================================================
-- Lo demás lo crea hbm2ddl solo al arrancar:
--   inventario            + uuid_cliente, origen, id_usuario_ejecutor, total_faltantes
--   hallazgo_inventario   + id_responsable, estado_hallazgo, tipo_resolucion
--   inventario_detalle    (tabla nueva)
-- =====================================================================
