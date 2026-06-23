-- ============================================================================
-- siguiente_correlativo_por_predio_grupo.sql
-- ----------------------------------------------------------------------------
-- Objetivo:
--   Ver, por cada combinación (predio + grupo contable), el correlativo más
--   alto usado y cuál sería el SIGUIENTE código a asignar:
--
--        <municipio>-<predio>-<grupo(2 díg)>-<correlativo+1 (5 díg)>
--
--   Esto replica en SQL lo que hace la función de BD
--   preview_codigo_activo_by_codes(mun, pred, grp) (y la que usa
--   ActivosController para el registro masivo), para poder auditar / verificar
--   el correlativo sin tocar la secuencia.
--
-- Contiene 3 consultas:
--   (1) Resumen por predio+grupo, derivando el prefijo desde las FK reales.
--   (2) Lo mismo pero agrupando por el PREFIJO TEXTUAL del codigo guardado
--       (esto es exactamente lo que "ve" la función de BD).
--   (3) Versión parametrizada para un solo predio+grupo + huecos en la serie.
-- ============================================================================


-- ----------------------------------------------------------------------------
-- (1) SIGUIENTE CORRELATIVO POR PREDIO + GRUPO  (derivado de las FK)
--     Sólo cuenta activos cuyo código está bien formado (mun-pred-grp-#####).
-- ----------------------------------------------------------------------------
WITH activos_codificados AS (
    SELECT
        m.codigo                       AS mun_cod,
        p.codigo                       AS pred_cod,
        p.id_predio,
        p.descrip                      AS predio_desc,
        lpad(g.cod_dbf::text, 2, '0')  AS grp_cod,
        g.id_grupo_contable,
        g.nombre                       AS grupo_nombre,
        CASE WHEN a.codigo ~ '^[^-]+-[^-]+-[0-9]+-[0-9]+$'
             THEN (substring(a.codigo from '[^-]+$'))::int
        END                            AS correlativo
    FROM activo a
    JOIN oficina        o ON o.id_oficina        = a.id_oficina
    JOIN predio         p ON p.id_predio         = o.id_predio
    JOIN municipio      m ON m.id_municipio      = p.id_municipio
    JOIN grupo_contable g ON g.id_grupo_contable = a.id_grupo_contable
)
SELECT
    mun_cod,
    pred_cod,
    predio_desc,
    grp_cod,
    grupo_nombre,
    count(correlativo)                         AS activos_codificados,
    max(correlativo)                           AS correlativo_max,
    coalesce(max(correlativo), 0) + 1          AS siguiente_correlativo,
    concat_ws('-', mun_cod, pred_cod, grp_cod,
        lpad((coalesce(max(correlativo), 0) + 1)::text, 5, '0')
    )                                          AS siguiente_codigo
FROM activos_codificados
GROUP BY mun_cod, pred_cod, predio_desc, grp_cod, grupo_nombre,
         id_predio, id_grupo_contable
ORDER BY mun_cod, pred_cod, grp_cod;


-- ----------------------------------------------------------------------------
-- (2) SIGUIENTE CORRELATIVO POR PREFIJO TEXTUAL DEL CODIGO
--     Agrupa por el prefijo real almacenado (todo lo que va antes del último
--     guion). Es lo que realmente escanea la función de BD, así que si (1) y (2)
--     difieren para un mismo predio+grupo => hay códigos mal asignados
--     (revisar con verificar_codigos_activo.sql).
-- ----------------------------------------------------------------------------
WITH cods AS (
    SELECT
        substring(codigo from '^(.*)-[^-]+$')   AS prefijo,   -- mun-pred-grp
        (substring(codigo from '[^-]+$'))::int  AS correlativo
    FROM activo
    WHERE codigo ~ '^[^-]+-[^-]+-[0-9]+-[0-9]+$'
)
SELECT
    prefijo,
    count(*)                       AS activos,
    max(correlativo)               AS correlativo_max,
    max(correlativo) + 1           AS siguiente_correlativo,
    prefijo || '-' || lpad((max(correlativo) + 1)::text, 5, '0') AS siguiente_codigo
FROM cods
GROUP BY prefijo
ORDER BY prefijo;


-- ----------------------------------------------------------------------------
-- (3) DETALLE DE UN SOLO PREDIO + GRUPO  +  HUECOS EN LA SERIE
--     Ajustá :mun, :pred, :grp (grp con 2 dígitos, ej. '02').
--     'falta' = TRUE marca correlativos que no existen entre 1 y el máximo
--     (útil para detectar saltos antes de reutilizar números).
-- ----------------------------------------------------------------------------
-- \set mun  '04'
-- \set pred '001'
-- \set grp  '02'
WITH params AS (
    SELECT '04'::text AS mun, '001'::text AS pred, '02'::text AS grp   -- <== EDITAR
),
usados AS (
    SELECT (substring(a.codigo from '[^-]+$'))::int AS correlativo
    FROM activo a, params x
    WHERE a.codigo ~ '^[^-]+-[^-]+-[0-9]+-[0-9]+$'
      AND a.codigo LIKE x.mun || '-' || x.pred || '-' || x.grp || '-%'
),
maxc AS (
    SELECT coalesce(max(correlativo), 0) AS m FROM usados
)
SELECT
    gs.n                                   AS correlativo,
    (SELECT mun  FROM params) || '-' ||
    (SELECT pred FROM params) || '-' ||
    (SELECT grp  FROM params) || '-' ||
    lpad(gs.n::text, 5, '0')               AS codigo,
    (u.correlativo IS NULL)                AS falta
FROM maxc
CROSS JOIN generate_series(1, GREATEST((SELECT m FROM maxc), 1)) AS gs(n)
LEFT JOIN usados u ON u.correlativo = gs.n
ORDER BY gs.n;
