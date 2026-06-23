-- ============================================================================
-- verificar_codigos_activo.sql
-- ----------------------------------------------------------------------------
-- Objetivo:
--   Corroborar que el CODIGO de cada activo respete el formato institucional
--   que arma la BD (funciones generar_codigo_activo_by_codes / preview_*),
--   reproducido en Java por ActivosController.construirCodigo():
--
--        <municipio>-<predio>-<grupo(2 díg)>-<correlativo(5 díg)>
--        ej:  04-001-02-00037
--
--   El prefijo ESPERADO se reconstruye desde las FK reales del activo:
--        activo -> oficina -> predio -> municipio   (municipio + predio)
--        activo -> grupo_contable                   (grupo, cod_dbf a 2 díg)
--
--   Luego se compara contra lo que realmente está guardado en activo.codigo.
--
-- Uso:
--   psql -h virtual.uap.edu.bo -p 5432 -U <user> -d bd_a4 -f verificar_codigos_activo.sql
--   (o pegar bloque por bloque en pgAdmin / DBeaver)
-- ============================================================================


-- ----------------------------------------------------------------------------
-- 1) DETALLE: diagnóstico fila por fila
--    Quita el WHERE final para ver TODOS los activos (incluidos los 'OK').
-- ----------------------------------------------------------------------------
WITH base AS (
    SELECT
        a.id_activo,
        a.codigo,
        m.codigo                                          AS mun_cod,
        p.codigo                                          AS pred_cod,
        g.cod_dbf,
        lpad(g.cod_dbf::text, 2, '0')                     AS grp_cod,
        -- prefijo esperado: municipio-predio-grupo
        concat_ws('-', m.codigo, p.codigo,
                       lpad(g.cod_dbf::text, 2, '0'))      AS prefijo_esperado,
        -- último tramo del código guardado = correlativo
        substring(a.codigo from '[^-]+$')                 AS corr_segmento,
        o.id_oficina,
        p.id_predio,
        m.id_municipio,
        g.id_grupo_contable
    FROM activo a
    LEFT JOIN oficina        o ON o.id_oficina        = a.id_oficina
    LEFT JOIN predio         p ON p.id_predio         = o.id_predio
    LEFT JOIN municipio      m ON m.id_municipio      = p.id_municipio
    LEFT JOIN grupo_contable g ON g.id_grupo_contable = a.id_grupo_contable
)
SELECT
    id_activo,
    codigo                        AS codigo_actual,
    prefijo_esperado,
    corr_segmento                 AS correlativo,
    CASE
        WHEN id_oficina        IS NULL THEN 'SIN OFICINA'
        WHEN id_predio         IS NULL THEN 'SIN PREDIO'
        WHEN mun_cod           IS NULL THEN 'SIN MUNICIPIO'
        WHEN id_grupo_contable IS NULL THEN 'SIN GRUPO CONTABLE'
        WHEN codigo IS NULL OR codigo = '' THEN 'SIN CODIGO'
        -- estructura general mun-pred-grupo(num)-correlativo(num)
        WHEN codigo !~ '^[^-]+-[^-]+-[0-9]+-[0-9]+$' THEN 'FORMATO INVALIDO'
        -- el prefijo guardado no coincide con el derivado de las FK
        WHEN codigo NOT LIKE prefijo_esperado || '-%' THEN 'PREFIJO NO COINCIDE'
        -- correlativo no tiene los 5 dígitos que arma construirCodigo()
        WHEN corr_segmento !~ '^[0-9]{5}$' THEN 'CORRELATIVO NO ES 5 DIGITOS'
        ELSE 'OK'
    END                           AS diagnostico
FROM base
WHERE
    -- comentar esta línea para listar también los 'OK'
    NOT (
        codigo IS NOT NULL AND codigo <> ''
        AND id_grupo_contable IS NOT NULL AND mun_cod IS NOT NULL
        AND codigo ~ '^[^-]+-[^-]+-[0-9]+-[0-9]+$'
        AND codigo LIKE concat_ws('-', mun_cod, pred_cod, lpad(cod_dbf::text,2,'0')) || '-%'
        AND substring(codigo from '[^-]+$') ~ '^[0-9]{5}$'
    )
ORDER BY diagnostico, prefijo_esperado, codigo;


-- ----------------------------------------------------------------------------
-- 2) RESUMEN: cuántos activos hay por cada tipo de diagnóstico
-- ----------------------------------------------------------------------------
WITH base AS (
    SELECT
        a.codigo,
        m.codigo                       AS mun_cod,
        p.codigo                       AS pred_cod,
        lpad(g.cod_dbf::text, 2, '0')  AS grp_cod,
        o.id_oficina,
        p.id_predio,
        m.id_municipio,
        g.id_grupo_contable
    FROM activo a
    LEFT JOIN oficina        o ON o.id_oficina        = a.id_oficina
    LEFT JOIN predio         p ON p.id_predio         = o.id_predio
    LEFT JOIN municipio      m ON m.id_municipio      = p.id_municipio
    LEFT JOIN grupo_contable g ON g.id_grupo_contable = a.id_grupo_contable
)
SELECT
    CASE
        WHEN id_oficina        IS NULL THEN 'SIN OFICINA'
        WHEN id_predio         IS NULL THEN 'SIN PREDIO'
        WHEN mun_cod           IS NULL THEN 'SIN MUNICIPIO'
        WHEN id_grupo_contable IS NULL THEN 'SIN GRUPO CONTABLE'
        WHEN codigo IS NULL OR codigo = '' THEN 'SIN CODIGO'
        WHEN codigo !~ '^[^-]+-[^-]+-[0-9]+-[0-9]+$' THEN 'FORMATO INVALIDO'
        WHEN codigo NOT LIKE concat_ws('-', mun_cod, pred_cod, grp_cod) || '-%'
            THEN 'PREFIJO NO COINCIDE'
        WHEN substring(codigo from '[^-]+$') !~ '^[0-9]{5}$'
            THEN 'CORRELATIVO NO ES 5 DIGITOS'
        ELSE 'OK'
    END                AS diagnostico,
    count(*)           AS cantidad
FROM base
GROUP BY 1
ORDER BY cantidad DESC;


-- ----------------------------------------------------------------------------
-- 3) CODIGOS DUPLICADOS  (no debería existir ninguno: codigo es único)
--    Si aparece algo aquí => dos activos comparten el mismo correlativo.
-- ----------------------------------------------------------------------------
SELECT
    codigo,
    count(*)              AS veces,
    array_agg(id_activo)  AS ids_activo
FROM activo
WHERE codigo IS NOT NULL AND codigo <> ''
GROUP BY codigo
HAVING count(*) > 1
ORDER BY veces DESC, codigo;
