-- ============================================================================
-- donaciones_codigo_d_al_final.sql
-- ----------------------------------------------------------------------------
-- Cambio de formato del código de los bienes donados (14-oct-2026):
--     antes:  01-02-04-D00001      ahora:  01-02-04-00001D
-- Los bienes nuevos ya salen con la D al final. Este script pasa al formato nuevo
-- los que se registraron antes. Mismo número, solo se mueve la D.
--
-- No es obligatorio: el sistema reconoce los dos formatos y la numeración nueva
-- sigue después de los números ya usados en cualquiera de ellos. Sirve para que
-- todos queden iguales (y el buscador o la app los encuentren con la D al final).
--
-- OJO: si ya imprimió etiquetas con el formato viejo, hay que reimprimirlas
-- (Registro de donaciones → acta → Etiquetas) después de correr esto.
--
-- Ejecutar contra la base de producción (bd_a3). Paso 1 y 2 son de solo lectura.
-- ============================================================================

-- PASO 1 — Qué cambiaría (solo lectura)
select id_activo, _estado, codigo, codigo_anulado,
       regexp_replace(coalesce(codigo, codigo_anulado), '-D([0-9]{5})$', '-\1D') as nuevo
from activo
where procedencia = 'DONACION'
  and (codigo ~ '-D[0-9]{5}$' or codigo_anulado ~ '-D[0-9]{5}$')
order by coalesce(codigo, codigo_anulado);

-- PASO 2 — Ningún código nuevo debe existir ya (debe devolver 0 filas)
select a.id_activo, a.codigo
from activo a
join (select regexp_replace(codigo, '-D([0-9]{5})$', '-\1D') as nuevo
      from activo where procedencia = 'DONACION' and codigo ~ '-D[0-9]{5}$') n
  on a.codigo = n.nuevo;

-- PASO 3 — El cambio, todo o nada. Revise los conteos antes del COMMIT.
BEGIN;

update activo
   set codigo = regexp_replace(codigo, '-D([0-9]{5})$', '-\1D')
 where procedencia = 'DONACION' and codigo ~ '-D[0-9]{5}$';

update activo
   set codigo_anulado = regexp_replace(codigo_anulado, '-D([0-9]{5})$', '-\1D')
 where procedencia = 'DONACION' and codigo_anulado ~ '-D[0-9]{5}$';

-- El historial guarda una copia del código (búsquedas por código y Ruta del activo).
update historial_activo h
   set codigo_activo = regexp_replace(h.codigo_activo, '-D([0-9]{5})$', '-\1D')
 where h.codigo_activo ~ '-D[0-9]{5}$'
   and exists (select 1 from activo a where a.id_activo = h.id_activo and a.procedencia = 'DONACION');

-- Copias del código que guardan otros módulos (se muestran o se buscan por código).
update detalle_asignacion d
   set codigo_activo_snapshot = regexp_replace(d.codigo_activo_snapshot, '-D([0-9]{5})$', '-\1D')
 where d.codigo_activo_snapshot ~ '-D[0-9]{5}$'
   and exists (select 1 from activo a where a.id_activo = d.id_activo and a.procedencia = 'DONACION');

update transferencia_detalle t
   set codigo_activo = regexp_replace(t.codigo_activo, '-D([0-9]{5})$', '-\1D')
 where t.codigo_activo ~ '-D[0-9]{5}$'
   and exists (select 1 from activo a where a.id_activo = t.id_activo and a.procedencia = 'DONACION');

update asignacion_movimiento_detalle m
   set codigo_activo = regexp_replace(m.codigo_activo, '-D([0-9]{5})$', '-\1D')
 where m.codigo_activo ~ '-D[0-9]{5}$'
   and exists (select 1 from activo a where a.id_activo = m.id_activo and a.procedencia = 'DONACION');

update hallazgo_inventario h
   set codigo_fisico = regexp_replace(h.codigo_fisico, '-D([0-9]{5})$', '-\1D')
 where h.codigo_fisico ~ '-D[0-9]{5}$'
   and exists (select 1 from activo a where a.id_activo = h.id_activo and a.procedencia = 'DONACION');

-- Verificación: las dos deben dar 0
select count(*) as quedan_en_formato_viejo
  from activo where procedencia = 'DONACION' and (codigo ~ '-D[0-9]{5}$' or codigo_anulado ~ '-D[0-9]{5}$');
select count(*) as historial_en_formato_viejo
  from historial_activo h join activo a on a.id_activo = h.id_activo
 where a.procedencia = 'DONACION' and h.codigo_activo ~ '-D[0-9]{5}$';

-- NO se confirma solo. Mire los conteos de arriba y, si los dos dan 0, ejecute a mano:
--     COMMIT;
-- Si algo no cuadra:
--     ROLLBACK;
