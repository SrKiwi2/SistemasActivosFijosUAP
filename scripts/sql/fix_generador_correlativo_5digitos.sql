-- ============================================================================
-- fix_generador_correlativo_5digitos.sql
-- ----------------------------------------------------------------------------
-- PROBLEMA (29-jun-2026, bd_a3):
--   El registro masivo proponía un correlativo con un salto gigantesco:
--       prefijo 01-04-06  ->  máx real = 06907  ->  la BD proponía 01-04-06-60670
--
--   CAUSA RAÍZ: un único activo con el correlativo MAL FORMADO (6 dígitos):
--       id_activo = 26929   codigo = '01-04-06-060669'   (estado ACTIVO)
--       descripcion: (PREV: 2700) SILLA UNIPERSONAL METALICA ...
--   Todos los demás de ese prefijo tienen 5 dígitos. La función generadora
--   preview_codigo_activo_by_codes hace:
--       MAX(CAST(SUBSTRING(codigo FROM len(prefijo)+1) AS INTEGER))
--   con el filtro permisivo  codigo ~ '^<prefijo>[0-9]+$'  (uno o más dígitos),
--   así que '060669' se castea a 60669 y envenena el MAX -> próximo 60670.
--
--   Efecto secundario en la pantalla de revisión: como el máximo "salta" a 60669,
--   se listan ~53.000 "huecos" falsos (6908..60668) -> "varios códigos no registrados".
--
-- En toda la BD hay EXACTAMENTE 1 código así (verificado). Solo el prefijo
-- 01-04-06 está afectado.
--
--   BLINDAJE: la función sólo debe considerar correlativos con el formato
--   institucional EXACTO de 5 dígitos ([0-9]{5}). Así un código mal formado nunca
--   vuelve a envenenar la secuencia. Coincide con construirCodigo() (%05d) y con
--   verificar_codigos_activo.sql ("CORRELATIVO NO ES 5 DIGITOS").
--
--   Ejecutar contra bd_a3 (la BD viva según application.properties).
--   Es idempotente.
-- ============================================================================


-- ----------------------------------------------------------------------------
-- PASO 1 — BLINDAR la función generadora (sólo cuenta correlativos de 5 dígitos)
--   CREATE OR REPLACE no rompe nada: misma firma, misma semántica para los datos
--   bien formados. Cambia '[0-9]+' por '[0-9]{5}'.
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.preview_codigo_activo_by_codes(
        p_mun  character varying,
        p_pred character varying,
        p_grp  character varying)
 RETURNS text
 LANGUAGE plpgsql
AS $function$
DECLARE
    v_prefix  TEXT;
    v_max_seq INTEGER;
BEGIN
    -- Prefijo "MUN-PRED-GRP-"  (ej: "01-04-06-")
    v_prefix := p_mun || '-' || p_pred || '-' || p_grp || '-';

    -- Máximo correlativo SOLO entre códigos con el formato institucional exacto:
    -- prefijo + EXACTAMENTE 5 dígitos. Los mal formados (p.ej. 6 dígitos) se ignoran.
    SELECT COALESCE(MAX(CAST(SUBSTRING(codigo FROM LENGTH(v_prefix) + 1) AS INTEGER)), 0)
    INTO v_max_seq
    FROM activo
    WHERE codigo LIKE v_prefix || '%'
      AND codigo ~ ('^' || v_prefix || '[0-9]{5}$');   -- <== blindaje: {5}, no +

    RETURN v_prefix || LPAD((v_max_seq + 1)::TEXT, 5, '0');
END;
$function$;

-- generar_codigo_activo_by_codes ya reusa preview_*, no hace falta tocarla.

-- Verificación: debe devolver 01-04-06-06908 (06907 + 1), no 60670.
SELECT preview_codigo_activo_by_codes('01','04','06') AS proximo_codigo_01_04_06;


-- ----------------------------------------------------------------------------
-- PASO 2 — (DECISIÓN MANUAL) Normalizar el registro malformado 26929.
--   El blindaje del PASO 1 ya destranca el registro (ignora el código de 6 díg),
--   pero '01-04-06-060669' sigue siendo un código NO institucional. Hay que
--   decidir qué hacer con él. NO ejecutar a ciegas: depende del estado en VSIAF.
--
--   Antes de elegir, mirar el código real correcto disponible:
--       SELECT preview_codigo_activo_by_codes('01','04','06');  -- = 01-04-06-06908
--
--   OPCIÓN A (recomendada si NUNCA se subió correctamente a VSIAF):
--     reasignarle el siguiente correlativo válido (06908), quedando como la cola.
--       UPDATE activo
--          SET codigo = '01-04-06-06908'
--        WHERE id_activo = 26929 AND codigo = '01-04-06-060669';
--
--   OPCIÓN B: meterlo en un hueco real ≤ 06907 (mantiene la serie compacta).
--     Elegir un correlativo libre concreto y usarlo en lugar de 06908.
--
--   OPCIÓN C: si en VSIAF ya quedó como '060669' y no se puede mover, dejarlo:
--     con el PASO 1 la secuencia ya no se rompe. (Queda como código no estándar.)
--
--   *** Cualquier UPDATE de código debe respetar el índice único uk_activo_codigo
--       y, si el activo ya está sincronizado, coordinarse con el VSIAF. ***
-- ----------------------------------------------------------------------------


-- ----------------------------------------------------------------------------
-- PASO 3 — Detector preventivo: ¿algún otro código mal formado en toda la BD?
--   (Hoy: 0 filas además del 26929 ya tratado.)
-- ----------------------------------------------------------------------------
SELECT id_activo, codigo, _estado
FROM activo
WHERE codigo ~ '^[^-]+-[^-]+-[0-9]+-[0-9]+$'
  AND substring(codigo from '[^-]+$') !~ '^[0-9]{5}$'
ORDER BY codigo;
