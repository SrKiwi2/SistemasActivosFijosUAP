-- Medición de personas duplicadas y cargos distintos por C.I. — SOLO LECTURA.
-- Correr con: psql ... -v ON_ERROR_STOP=1 -f docs/sql/medir_personas_duplicadas.sql
-- Todo va dentro de una transacción READ ONLY y termina en ROLLBACK: no escribe nada.
-- Sin vistas temporales (no se permiten en READ ONLY): cada consulta repite los CTE rv y pn.
--   rv = responsables vigentes (sin custodia) con predio, oficina, cargo y bienes ACTIVO.
--   pn = personas ACTIVAS con nombre/C.I. normalizados; ci_num = solo el número del C.I.
--        (sin extensión LP/CH ni complemento): la sync hoy trata "1234567 LP" como SIN C.I.
-- Plan del módulo: docs/PLAN_PERSONAS_UNIFICADAS.md

BEGIN TRANSACTION READ ONLY;

\echo '== 0. Totales'
WITH rv AS (
SELECT r.id_responsable, r.codigo_funcionario AS codresp, r.id_persona, r.id_cargo,
       pr.unidad, o.cod_ofi AS codofic, c.nombre AS cargo,
       (SELECT count(*) FROM activo a WHERE a.id_responsable = r.id_responsable AND a._estado = 'ACTIVO') AS bienes
FROM responsable r
JOIN oficina o  ON o.id_oficina = r.id_oficina
JOIN predio pr  ON pr.id_predio = o.id_predio
LEFT JOIN cargo c ON c.id_cargo = r.id_cargo
WHERE r._estado = 'ACTIVO' AND NOT r.es_custodia),
pn AS (
SELECT p.id_persona,
       upper(trim(concat_ws(' ', p.nombre, p.paterno, p.materno))) AS nombre,
       upper(trim(coalesce(p.ci, ''))) AS ci,
       substring(regexp_replace(coalesce(p.ci, ''), '[.\s-]', '', 'g') from '^\d{5,}') AS ci_num,
       upper(trim(coalesce(p.paterno, ''))) AS paterno,
       upper(trim(coalesce(p.materno, ''))) AS materno,
       regexp_split_to_array(upper(trim(coalesce(p.nombre, ''))), '\s+') AS nombres
FROM persona p
WHERE p._estado = 'ACTIVO')
SELECT (SELECT count(*) FROM pn) AS personas_activas,
       (SELECT count(*) FROM pn WHERE ci_num IS NOT NULL) AS con_ci_numerico,
       (SELECT count(*) FROM pn WHERE ci_num IS NULL AND ci <> '') AS ci_raro,
       (SELECT count(*) FROM pn WHERE ci = '') AS sin_ci,
       (SELECT count(*) FROM rv) AS responsables_vigentes,
       (SELECT count(DISTINCT id_persona) FROM rv) AS personas_con_responsable;

\echo '== 1. C.I. con letras (extensión/complemento): hoy la sync los toma como SIN C.I.'
WITH rv AS (
SELECT r.id_responsable, r.codigo_funcionario AS codresp, r.id_persona, r.id_cargo,
       pr.unidad, o.cod_ofi AS codofic, c.nombre AS cargo,
       (SELECT count(*) FROM activo a WHERE a.id_responsable = r.id_responsable AND a._estado = 'ACTIVO') AS bienes
FROM responsable r
JOIN oficina o  ON o.id_oficina = r.id_oficina
JOIN predio pr  ON pr.id_predio = o.id_predio
LEFT JOIN cargo c ON c.id_cargo = r.id_cargo
WHERE r._estado = 'ACTIVO' AND NOT r.es_custodia),
pn AS (
SELECT p.id_persona,
       upper(trim(concat_ws(' ', p.nombre, p.paterno, p.materno))) AS nombre,
       upper(trim(coalesce(p.ci, ''))) AS ci,
       substring(regexp_replace(coalesce(p.ci, ''), '[.\s-]', '', 'g') from '^\d{5,}') AS ci_num,
       upper(trim(coalesce(p.paterno, ''))) AS paterno,
       upper(trim(coalesce(p.materno, ''))) AS materno,
       regexp_split_to_array(upper(trim(coalesce(p.nombre, ''))), '\s+') AS nombres
FROM persona p
WHERE p._estado = 'ACTIVO')
SELECT (SELECT count(*) FROM pn WHERE ci ~ '[A-Z]') AS personas,
       (SELECT string_agg(ci, ', ') FROM (SELECT ci FROM pn WHERE ci ~ '[A-Z]' LIMIT 30) x) AS ejemplos;

\echo '== 2. Mismo número de C.I. en dos o más personas (duplicado seguro)'
WITH rv AS (
SELECT r.id_responsable, r.codigo_funcionario AS codresp, r.id_persona, r.id_cargo,
       pr.unidad, o.cod_ofi AS codofic, c.nombre AS cargo,
       (SELECT count(*) FROM activo a WHERE a.id_responsable = r.id_responsable AND a._estado = 'ACTIVO') AS bienes
FROM responsable r
JOIN oficina o  ON o.id_oficina = r.id_oficina
JOIN predio pr  ON pr.id_predio = o.id_predio
LEFT JOIN cargo c ON c.id_cargo = r.id_cargo
WHERE r._estado = 'ACTIVO' AND NOT r.es_custodia),
pn AS (
SELECT p.id_persona,
       upper(trim(concat_ws(' ', p.nombre, p.paterno, p.materno))) AS nombre,
       upper(trim(coalesce(p.ci, ''))) AS ci,
       substring(regexp_replace(coalesce(p.ci, ''), '[.\s-]', '', 'g') from '^\d{5,}') AS ci_num,
       upper(trim(coalesce(p.paterno, ''))) AS paterno,
       upper(trim(coalesce(p.materno, ''))) AS materno,
       regexp_split_to_array(upper(trim(coalesce(p.nombre, ''))), '\s+') AS nombres
FROM persona p
WHERE p._estado = 'ACTIVO')
SELECT ci_num, count(*) AS personas, string_agg(id_persona || ':' || nombre || ' [' || ci || ']', ' | ') AS detalle
FROM pn WHERE ci_num IS NOT NULL
GROUP BY ci_num HAVING count(*) > 1
ORDER BY count(*) DESC, ci_num;

\echo '== 3. Mismo nombre exacto: una con C.I. y otra(s) sin C.I. (caso Richard Rojas)'
WITH rv AS (
SELECT r.id_responsable, r.codigo_funcionario AS codresp, r.id_persona, r.id_cargo,
       pr.unidad, o.cod_ofi AS codofic, c.nombre AS cargo,
       (SELECT count(*) FROM activo a WHERE a.id_responsable = r.id_responsable AND a._estado = 'ACTIVO') AS bienes
FROM responsable r
JOIN oficina o  ON o.id_oficina = r.id_oficina
JOIN predio pr  ON pr.id_predio = o.id_predio
LEFT JOIN cargo c ON c.id_cargo = r.id_cargo
WHERE r._estado = 'ACTIVO' AND NOT r.es_custodia),
pn AS (
SELECT p.id_persona,
       upper(trim(concat_ws(' ', p.nombre, p.paterno, p.materno))) AS nombre,
       upper(trim(coalesce(p.ci, ''))) AS ci,
       substring(regexp_replace(coalesce(p.ci, ''), '[.\s-]', '', 'g') from '^\d{5,}') AS ci_num,
       upper(trim(coalesce(p.paterno, ''))) AS paterno,
       upper(trim(coalesce(p.materno, ''))) AS materno,
       regexp_split_to_array(upper(trim(coalesce(p.nombre, ''))), '\s+') AS nombres
FROM persona p
WHERE p._estado = 'ACTIVO')
SELECT a.nombre, a.id_persona AS con_ci, a.ci, string_agg(b.id_persona::text, ',') AS sin_ci
FROM pn a JOIN pn b ON b.nombre = a.nombre AND b.id_persona <> a.id_persona AND b.ci_num IS NULL
WHERE a.ci_num IS NOT NULL
GROUP BY a.nombre, a.id_persona, a.ci
ORDER BY a.nombre;

\echo '== 4. Nombre parecido: mismos apellidos y un nombre en común o inicial compatible'
--   "A. MARCELO CACERES BEJARANO" ~ "ALVARO MARCELO CACERES BEJARANO" ~ "ALVARO M. CACERES BEJARANO"
--   "ABRAHIN LEON ROBLES" ~ "MIGUEL ABRAHIN LEON ROBLES"
--   Hermanos (mismos apellidos, otros nombres) NO entran. Revisar a mano: son candidatos.
WITH rv AS (
SELECT r.id_responsable, r.codigo_funcionario AS codresp, r.id_persona, r.id_cargo,
       pr.unidad, o.cod_ofi AS codofic, c.nombre AS cargo,
       (SELECT count(*) FROM activo a WHERE a.id_responsable = r.id_responsable AND a._estado = 'ACTIVO') AS bienes
FROM responsable r
JOIN oficina o  ON o.id_oficina = r.id_oficina
JOIN predio pr  ON pr.id_predio = o.id_predio
LEFT JOIN cargo c ON c.id_cargo = r.id_cargo
WHERE r._estado = 'ACTIVO' AND NOT r.es_custodia),
pn AS (
SELECT p.id_persona,
       upper(trim(concat_ws(' ', p.nombre, p.paterno, p.materno))) AS nombre,
       upper(trim(coalesce(p.ci, ''))) AS ci,
       substring(regexp_replace(coalesce(p.ci, ''), '[.\s-]', '', 'g') from '^\d{5,}') AS ci_num,
       upper(trim(coalesce(p.paterno, ''))) AS paterno,
       upper(trim(coalesce(p.materno, ''))) AS materno,
       regexp_split_to_array(upper(trim(coalesce(p.nombre, ''))), '\s+') AS nombres
FROM persona p
WHERE p._estado = 'ACTIVO')
SELECT a.id_persona AS p1, a.nombre AS nombre1, a.ci AS ci1,
       b.id_persona AS p2, b.nombre AS nombre2, b.ci AS ci2,
       CASE WHEN a.ci_num IS NOT NULL AND b.ci_num IS NOT NULL AND a.ci_num <> b.ci_num
            THEN 'C.I. DISTINTO: probablemente otra persona' ELSE '' END AS ojo
FROM pn a JOIN pn b ON a.id_persona < b.id_persona
 AND a.paterno <> '' AND a.paterno = b.paterno AND a.materno = b.materno
 AND a.nombre <> b.nombre
 AND EXISTS (SELECT 1 FROM unnest(a.nombres) x, unnest(b.nombres) y
             WHERE replace(x, '.', '') = replace(y, '.', '')
                OR (length(replace(x, '.', '')) = 1 AND left(y, 1) = replace(x, '.', ''))
                OR (length(replace(y, '.', '')) = 1 AND left(x, 1) = replace(y, '.', '')))
WHERE a.id_persona IN (SELECT id_persona FROM rv) OR b.id_persona IN (SELECT id_persona FROM rv)
ORDER BY a.paterno, a.materno, a.nombre;

\echo '== 4b. Apellidos corridos: el nombre de una está contenido en el de la otra (sin C.I. que lo contradiga)'
WITH rv AS (
SELECT r.id_responsable, r.codigo_funcionario AS codresp, r.id_persona, r.id_cargo,
       pr.unidad, o.cod_ofi AS codofic, c.nombre AS cargo,
       (SELECT count(*) FROM activo a WHERE a.id_responsable = r.id_responsable AND a._estado = 'ACTIVO') AS bienes
FROM responsable r
JOIN oficina o  ON o.id_oficina = r.id_oficina
JOIN predio pr  ON pr.id_predio = o.id_predio
LEFT JOIN cargo c ON c.id_cargo = r.id_cargo
WHERE r._estado = 'ACTIVO' AND NOT r.es_custodia),
pn AS (
SELECT p.id_persona,
       upper(trim(concat_ws(' ', p.nombre, p.paterno, p.materno))) AS nombre,
       upper(trim(coalesce(p.ci, ''))) AS ci,
       substring(regexp_replace(coalesce(p.ci, ''), '[.\s-]', '', 'g') from '^\d{5,}') AS ci_num,
       upper(trim(coalesce(p.paterno, ''))) AS paterno,
       upper(trim(coalesce(p.materno, ''))) AS materno,
       regexp_split_to_array(upper(trim(coalesce(p.nombre, ''))), '\s+') AS nombres
FROM persona p
WHERE p._estado = 'ACTIVO')
SELECT a.id_persona AS p1, a.nombre AS nombre1, a.ci AS ci1, b.id_persona AS p2, b.nombre AS nombre2, b.ci AS ci2
FROM pn a JOIN pn b ON a.id_persona <> b.id_persona
 AND length(a.nombre) >= 12 AND b.nombre LIKE '%' || a.nombre || '%' AND a.nombre <> b.nombre
 AND NOT (a.ci_num IS NOT NULL AND b.ci_num IS NOT NULL AND a.ci_num <> b.ci_num)
 AND NOT (a.paterno = b.paterno AND a.materno = b.materno)   -- esos ya salen en el punto 4
WHERE a.id_persona IN (SELECT id_persona FROM rv) OR b.id_persona IN (SELECT id_persona FROM rv)
ORDER BY a.nombre;

\echo '== 5. Personas "comodín": una sola persona sin C.I. con muchos responsables (SIN DATOS o mismo nombre)'
WITH rv AS (
SELECT r.id_responsable, r.codigo_funcionario AS codresp, r.id_persona, r.id_cargo,
       pr.unidad, o.cod_ofi AS codofic, c.nombre AS cargo,
       (SELECT count(*) FROM activo a WHERE a.id_responsable = r.id_responsable AND a._estado = 'ACTIVO') AS bienes
FROM responsable r
JOIN oficina o  ON o.id_oficina = r.id_oficina
JOIN predio pr  ON pr.id_predio = o.id_predio
LEFT JOIN cargo c ON c.id_cargo = r.id_cargo
WHERE r._estado = 'ACTIVO' AND NOT r.es_custodia),
pn AS (
SELECT p.id_persona,
       upper(trim(concat_ws(' ', p.nombre, p.paterno, p.materno))) AS nombre,
       upper(trim(coalesce(p.ci, ''))) AS ci,
       substring(regexp_replace(coalesce(p.ci, ''), '[.\s-]', '', 'g') from '^\d{5,}') AS ci_num,
       upper(trim(coalesce(p.paterno, ''))) AS paterno,
       upper(trim(coalesce(p.materno, ''))) AS materno,
       regexp_split_to_array(upper(trim(coalesce(p.nombre, ''))), '\s+') AS nombres
FROM persona p
WHERE p._estado = 'ACTIVO')
SELECT p.id_persona, p.nombre, p.ci, count(*) AS responsables, count(DISTINCT rv.unidad) AS predios
FROM pn p JOIN rv ON rv.id_persona = p.id_persona
WHERE p.ci_num IS NULL
GROUP BY p.id_persona, p.nombre, p.ci HAVING count(*) > 1
ORDER BY count(*) DESC LIMIT 40;

\echo '== 6. Misma persona (mismo id) con cargos distintos según la oficina — alcance del "cargo por C.I."'
WITH rv AS (
SELECT r.id_responsable, r.codigo_funcionario AS codresp, r.id_persona, r.id_cargo,
       pr.unidad, o.cod_ofi AS codofic, c.nombre AS cargo,
       (SELECT count(*) FROM activo a WHERE a.id_responsable = r.id_responsable AND a._estado = 'ACTIVO') AS bienes
FROM responsable r
JOIN oficina o  ON o.id_oficina = r.id_oficina
JOIN predio pr  ON pr.id_predio = o.id_predio
LEFT JOIN cargo c ON c.id_cargo = r.id_cargo
WHERE r._estado = 'ACTIVO' AND NOT r.es_custodia),
pn AS (
SELECT p.id_persona,
       upper(trim(concat_ws(' ', p.nombre, p.paterno, p.materno))) AS nombre,
       upper(trim(coalesce(p.ci, ''))) AS ci,
       substring(regexp_replace(coalesce(p.ci, ''), '[.\s-]', '', 'g') from '^\d{5,}') AS ci_num,
       upper(trim(coalesce(p.paterno, ''))) AS paterno,
       upper(trim(coalesce(p.materno, ''))) AS materno,
       regexp_split_to_array(upper(trim(coalesce(p.nombre, ''))), '\s+') AS nombres
FROM persona p
WHERE p._estado = 'ACTIVO')
SELECT count(*) AS personas_con_varios_cargos, sum(cargos) AS cargos_en_juego
FROM (SELECT id_persona, count(DISTINCT coalesce(cargo, '')) AS cargos
      FROM rv GROUP BY id_persona HAVING count(DISTINCT coalesce(cargo, '')) > 1) x;

WITH rv AS (
SELECT r.id_responsable, r.codigo_funcionario AS codresp, r.id_persona, r.id_cargo,
       pr.unidad, o.cod_ofi AS codofic, c.nombre AS cargo,
       (SELECT count(*) FROM activo a WHERE a.id_responsable = r.id_responsable AND a._estado = 'ACTIVO') AS bienes
FROM responsable r
JOIN oficina o  ON o.id_oficina = r.id_oficina
JOIN predio pr  ON pr.id_predio = o.id_predio
LEFT JOIN cargo c ON c.id_cargo = r.id_cargo
WHERE r._estado = 'ACTIVO' AND NOT r.es_custodia),
pn AS (
SELECT p.id_persona,
       upper(trim(concat_ws(' ', p.nombre, p.paterno, p.materno))) AS nombre,
       upper(trim(coalesce(p.ci, ''))) AS ci,
       substring(regexp_replace(coalesce(p.ci, ''), '[.\s-]', '', 'g') from '^\d{5,}') AS ci_num,
       upper(trim(coalesce(p.paterno, ''))) AS paterno,
       upper(trim(coalesce(p.materno, ''))) AS materno,
       regexp_split_to_array(upper(trim(coalesce(p.nombre, ''))), '\s+') AS nombres
FROM persona p
WHERE p._estado = 'ACTIVO')
SELECT p.nombre, p.ci, rv.unidad, rv.codofic, rv.codresp, rv.cargo, rv.bienes
FROM rv JOIN pn p ON p.id_persona = rv.id_persona
WHERE rv.id_persona IN (SELECT id_persona FROM rv GROUP BY id_persona HAVING count(DISTINCT coalesce(cargo, '')) > 1)
ORDER BY p.nombre, rv.unidad, rv.codofic
LIMIT 200;

\echo '== 7. Tamaño del catálogo de cargos (la sync crea uno por cada texto distinto del DBF)'
SELECT count(*) AS cargos_activos,
       count(DISTINCT regexp_replace(upper(nombre), '[^A-Z]', '', 'g')) AS distintos_sin_puntos_ni_espacios
FROM cargo WHERE _estado = 'ACTIVO';

\echo '== 7b. Cargos en uso que probablemente son el mismo (para la pestaña Cargos)'
--   Mismo texto sin puntos/espacios, o uno empieza con el otro (RESP. PARQUE AUTOMOTOR / ... UAP).
--   Las abreviaturas (CARRE. / CARRERA) y errores de tipeo no salen aquí: el módulo los busca en Java.
WITH rv AS (
SELECT r.id_responsable, r.codigo_funcionario AS codresp, r.id_persona, r.id_cargo,
       pr.unidad, o.cod_ofi AS codofic, c.nombre AS cargo,
       (SELECT count(*) FROM activo a WHERE a.id_responsable = r.id_responsable AND a._estado = 'ACTIVO') AS bienes
FROM responsable r
JOIN oficina o  ON o.id_oficina = r.id_oficina
JOIN predio pr  ON pr.id_predio = o.id_predio
LEFT JOIN cargo c ON c.id_cargo = r.id_cargo
WHERE r._estado = 'ACTIVO' AND NOT r.es_custodia),
pn AS (
SELECT p.id_persona,
       upper(trim(concat_ws(' ', p.nombre, p.paterno, p.materno))) AS nombre,
       upper(trim(coalesce(p.ci, ''))) AS ci,
       substring(regexp_replace(coalesce(p.ci, ''), '[.\s-]', '', 'g') from '^\d{5,}') AS ci_num,
       upper(trim(coalesce(p.paterno, ''))) AS paterno,
       upper(trim(coalesce(p.materno, ''))) AS materno,
       regexp_split_to_array(upper(trim(coalesce(p.nombre, ''))), '\s+') AS nombres
FROM persona p
WHERE p._estado = 'ACTIVO'),
uso AS (
  SELECT c.id_cargo, upper(trim(c.nombre)) AS nombre,
         regexp_replace(upper(c.nombre), '[^A-Z0-9]', '', 'g') AS clave,
         count(rv.id_responsable) AS filas
  FROM cargo c JOIN rv ON rv.id_cargo = c.id_cargo
  WHERE c._estado = 'ACTIVO'
  GROUP BY c.id_cargo, c.nombre)
SELECT a.nombre AS cargo1, a.filas AS filas1, b.nombre AS cargo2, b.filas AS filas2,
       CASE WHEN a.clave = b.clave THEN 'solo puntos/espacios' ELSE 'uno contiene al otro' END AS motivo
FROM uso a JOIN uso b ON a.id_cargo < b.id_cargo
 AND (a.clave = b.clave
      OR (length(a.clave) >= 10 AND length(b.clave) >= 10
          AND (a.clave LIKE b.clave || '%' OR b.clave LIKE a.clave || '%')))
ORDER BY a.filas + b.filas DESC
LIMIT 150;

\echo '== 8. Casos de la foto de Saul (control)'
WITH rv AS (
SELECT r.id_responsable, r.codigo_funcionario AS codresp, r.id_persona, r.id_cargo,
       pr.unidad, o.cod_ofi AS codofic, c.nombre AS cargo,
       (SELECT count(*) FROM activo a WHERE a.id_responsable = r.id_responsable AND a._estado = 'ACTIVO') AS bienes
FROM responsable r
JOIN oficina o  ON o.id_oficina = r.id_oficina
JOIN predio pr  ON pr.id_predio = o.id_predio
LEFT JOIN cargo c ON c.id_cargo = r.id_cargo
WHERE r._estado = 'ACTIVO' AND NOT r.es_custodia),
pn AS (
SELECT p.id_persona,
       upper(trim(concat_ws(' ', p.nombre, p.paterno, p.materno))) AS nombre,
       upper(trim(coalesce(p.ci, ''))) AS ci,
       substring(regexp_replace(coalesce(p.ci, ''), '[.\s-]', '', 'g') from '^\d{5,}') AS ci_num,
       upper(trim(coalesce(p.paterno, ''))) AS paterno,
       upper(trim(coalesce(p.materno, ''))) AS materno,
       regexp_split_to_array(upper(trim(coalesce(p.nombre, ''))), '\s+') AS nombres
FROM persona p
WHERE p._estado = 'ACTIVO')
SELECT p.id_persona, p.nombre, p.ci, rv.unidad, rv.codofic, rv.codresp, rv.cargo, rv.bienes
FROM pn p JOIN rv ON rv.id_persona = p.id_persona
WHERE p.nombre ~ '(CACERES BEJARANO|LEON ROBLES|ROJAS RODRIGUEZ|ROJAS POQUECHOQUE|YUMAA? ENRIQUEZ)'
ORDER BY p.paterno, p.materno, p.nombre, rv.unidad, rv.codofic;

\echo '== 9.  Mismo nombre exacto con 2+ números de C.I. distintos (C.I. mal tipeado o falso)'
WITH pn AS (SELECT id_persona, upper(trim(concat_ws(' ', nombre, paterno, materno))) AS nombre,
  substring(regexp_replace(coalesce(ci,''),'[.\s-]','','g') from '^\d{5,}') AS ci_num FROM persona WHERE _estado='ACTIVO')
SELECT nombre, string_agg(id_persona||':'||ci_num, ' | ' ORDER BY ci_num) FROM pn WHERE ci_num IS NOT NULL
GROUP BY nombre HAVING count(DISTINCT ci_num) > 1 ORDER BY nombre;
\echo '== 10. C.I. sospechosos (repetidos, secuencias, ceros) y cuántas personas/responsables los usan'
WITH pn AS (SELECT id_persona, upper(trim(concat_ws(' ', nombre, paterno, materno))) AS nombre,
  regexp_replace(coalesce(ci,''),'[.\s-]','','g') AS c FROM persona WHERE _estado='ACTIVO')
SELECT c, string_agg(nombre, ' | ') AS personas,
  (SELECT count(*) FROM responsable r WHERE r._estado='ACTIVO' AND r.id_persona = ANY(array_agg(pn.id_persona))) AS resp
FROM pn WHERE c ~ '^(\d)\1{4,}$' OR c ~ '^0+' OR c IN ('123456','1234567','12345678','1111000','12349000') OR c ~ '^\d{0,4}$' AND c<>''
GROUP BY c ORDER BY c;
\echo '== 11. Nombres "especiales" del VSIAF (no son personas o llevan una marca)'
WITH pn AS (SELECT id_persona, upper(trim(concat_ws(' ', nombre, paterno, materno))) AS nombre, ci FROM persona WHERE _estado='ACTIVO')
SELECT m.marca, count(DISTINCT pn.id_persona) AS personas, count(r.id_responsable) AS responsables,
       string_agg(DISTINCT pn.nombre, ' | ') AS ejemplos
FROM pn JOIN (VALUES ('DEVOLUCION'),('FALT'),('DEVENGADO'),('ACEFALO'),('SIN DATOS'),('BAJA'),('CUSTODIA'),('DEPOSITO'),('ALMACEN'),(' O '),('DR ')) m(marca)
  ON pn.nombre LIKE '%'||m.marca||'%'
LEFT JOIN responsable r ON r.id_persona = pn.id_persona AND r._estado='ACTIVO' AND NOT r.es_custodia
GROUP BY m.marca ORDER BY 3 DESC;
\echo '== 12. Posible Ñ perdida al leer el DBF (sílabas típicas sin la Ñ)'
SELECT count(*) AS personas, string_agg(n, ' | ') FROM (SELECT upper(concat_ws(' ', nombre, paterno, materno)) n FROM persona WHERE _estado='ACTIVO'
  AND upper(concat_ws(' ', nombre, paterno, materno)) ~ '(ACUA\M|PIEIRO|SUAANABAR|MUOZ|NUEZ|IBAEZ|PEAL|PEA\M|ORDOEZ|YAEZ|BAOS|CAIZARES|ZUIGA|AASCO|MONTAO|CASTAEDA|URQUIZO)') x;
\echo '== 13. ¿La base guarda Ñ? (personas con Ñ)'
SELECT count(*) FROM persona WHERE _estado='ACTIVO' AND upper(concat_ws(' ', nombre, paterno, materno)) LIKE '%Ñ%';

ROLLBACK;
