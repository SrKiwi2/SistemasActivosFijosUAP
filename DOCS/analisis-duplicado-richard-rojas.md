# Análisis: Duplicado "Richard Rojas López" en buscador de personas (Registrar Faltantes)

**Fecha:** 2026-10-06  
**Módulo:** Control de Activos → Faltantes → Registrar faltantes  
**Archivo relacionado:** `src/main/resources/templates/controlActivos/faltantes.html` (modal `#cf-reg`)

---

## 1. Problema reportado

Al buscar "richard" en el buscador del modal "Registrar faltantes", aparecen **dos filas** para "Richard Rojas López":
- Diferente cargo
- Uno sin CI
- Diferente oficina

El usuario espera que salga **uno solo** o que se puedan distinguir/filtar mejor.

---

## 2. Causa raíz (explicación técnica)

### 2.1 Cómo funciona el buscador (backend)

**Endpoint:** `GET /administracion/control-activos/custodia/personas?q=richard`

**Query SQL (simplificada):**
```sql
SELECT pe.id_persona,
       trim(concat_ws(' ', pe.nombre, pe.paterno, pe.materno)) as nombre,
       pe.ci,
       count(a.id_activo) as bienes,
       count(distinct a.id_oficina) as oficinas,
       count(distinct o.id_predio) as predios,
       (subquery) as faltantes_pendientes,
       cpp.cargo_principal,      -- agregado en fix anterior
       opp.oficina_principal     -- agregado en fix anterior
FROM persona pe
JOIN responsable r ON r.id_persona = pe.id_persona AND NOT r.es_custodia
JOIN activo a ON a.id_responsable = r.id_responsable AND a._estado = 'ACTIVO'
JOIN oficina o ON o.id_oficina = a.id_oficina AND NOT o.es_custodia
LEFT JOIN LATERAL (...) cpp ON true   -- cargo con más bienes
LEFT JOIN LATERAL (...) opp ON true   -- oficina con más bienes
WHERE (nombre match) OR pe.ci LIKE 'richard%'
GROUP BY pe.id_persona, pe.nombre, pe.paterno, pe.materno, pe.ci, cpp.cargo_principal, opp.oficina_principal
ORDER BY nombre
LIMIT 30
```

### 2.2 Por qué salen DOS filas

La query agrupa por **`pe.id_persona`** (clave primaria de tabla `persona`).

> **Si salen dos filas, existen DOS registros distintos en la tabla `persona` con ese nombre.**

| id_persona | nombre | paterno | materno | ci          |
|------------|--------|---------|---------|-------------|
| 123        | Richard| Rojas   | López   | 12345678    |
| 456        | Richard| Rojas   | López   | (NULL/ '')  |

**Esto NO es un bug de la query.** La query respeta la integridad de la BD: 1 `id_persona` = 1 persona en el sistema.

---

## 3. Posibles escenarios en los datos

| Escenario | Descripción | Acción correcta |
|-----------|-------------|-----------------|
| **A. Duplicado real** | Dos `persona` cargadas por error (misma persona real, dos IDs) | **Limpiar en BD:** fusionar o borrar uno, mover sus `responsable`/`activos` al bueno |
| **B. Carga incompleta** | Una persona tiene CI, la otra no (faltó completar) | **Completar CI** en el registro que falta, luego fusionar si es mismo |
| **C. Personas distintas** | Realmente son dos personas con mismo nombre | **Correcto que salgan dos** — distinguir por cargo/oficina (ya hace el fix anterior) |

---

## 4. Verificación en BD (SQL para ejecutar)

```sql
-- 1. Buscar todas las personas que matchean
SELECT id_persona, nombre, paterno, materno, ci
FROM persona
WHERE lower(concat_ws(' ', nombre, paterno, materno)) LIKE '%richard%rojas%';

-- 2. Ver sus responsables, cargos, oficinas y activos
SELECT p.id_persona, p.nombre, p.paterno, p.materno, p.ci,
       r.id_responsable, r.codigo_funcionario,
       c.nombre AS cargo,
       o.id_oficina, o.cod_ofi, o.nombre AS oficina,
       COUNT(a.id_activo) AS activos_a_cargo
FROM persona p
JOIN responsable r ON r.id_persona = p.id_persona
LEFT JOIN cargo c ON c.id_cargo = r.id_cargo
JOIN oficina o ON o.id_oficina = r.id_oficina
LEFT JOIN activo a ON a.id_responsable = r.id_responsable AND a._estado = 'ACTIVO'
WHERE lower(concat_ws(' ', p.nombre, p.paterno, p.materno)) LIKE '%richard%rojas%'
  AND NOT r.es_custodia
  AND NOT o.es_custodia
GROUP BY p.id_persona, p.nombre, p.paterno, p.materno, p.ci,
         r.id_responsable, r.codigo_funcionario, c.nombre,
         o.id_oficina, o.cod_ofi, o.nombre
ORDER BY p.id_persona, o.cod_ofi;
```

### Qué buscar en el resultado:
- **Dos `id_persona` distintos** → Duplicado en tabla `persona` (Escenario A/B)
- **Uno con CI, otro sin CI** → Carga incompleta (Escenario B)
- **Cargos/Oficinas distintas por `id_persona`** → Normal (una persona = un cargo por oficina)

---

## 5. Soluciones

### 5.1 Solución correcta y duradera: Limpiar datos en BD (Recomendada)

```sql
-- Ejemplo: si id_persona=456 es el duplicado sin CI, y 123 es el bueno:
-- 1. Mover responsables al bueno
UPDATE responsable SET id_persona = 123 WHERE id_persona = 456;

-- 2. Verificar que no quede nada referenciando al 456
-- 3. Borrar el duplicado
DELETE FROM persona WHERE id_persona = 456;
```

> **Por qué en BD y no en código:** El sistema está diseñado para que 1 persona = 1 `id_persona`. Forzar "uno solo" en la query rompería casos legítimos de homónimos y ocultaría el problema real.

### 5.2 Workaround temporal en frontend (si no se puede tocar BD ya)

Agregar **filtro por predio/oficina** arriba del buscador en el modal:

```html
<!-- En faltantes.html, dentro de #cf-reg-paso1, antes del input #cf-reg-buscar -->
<div class="row g-2 mb-2">
  <div class="col-md-6">
    <label class="form-label small">Predio</label>
    <select class="form-select form-select-sm" id="cf-reg-f-predio">
      <option value="">Todos</option>
      <!-- se llena desde ${predios} -->
    </select>
  </div>
  <div class="col-md-6">
    <label class="form-label small">Oficina</label>
    <select class="form-select form-select-sm" id="cf-reg-f-oficina">
      <option value="">Todas</option>
    </select>
  </div>
</div>
```

Y en el JS, pasar `idPredio` / `idOficina` al endpoint `/personas` (requiere cambio en controller + repo).

### 5.3 Lo que YA está implementado (fix anterior)

El commit previo agregó **cargo principal** y **oficina principal** a cada resultado:

```
Richard Rojas López          [3 faltantes]
C.I. 12345678 · Contador · 001 — Finanzas · 45 bien(es) en 3 oficina(s) de 2 predio(s)

Richard Rojas López          [0 faltantes]
C.I. s/d · Técnico · 005 — Mantenimiento · 12 bien(es) en 2 oficina(s) de 1 predio(s)
```

Esto permite distinguirlos **sin hacer click**, pero no fusiona filas (porque no deben fusionarse si son IDs distintos).

---

## 6. Conclusión

| Pregunta | Respuesta |
|----------|-----------|
| ¿Por qué salen dos? | Hay dos `id_persona` distintos en tabla `persona` con ese nombre |
| ¿Es bug de la query? | **No** — la query agrupa correctamente por PK |
| ¿Se puede "unir" en el código? | **No debería** — rompería integridad y ocultaría duplicados reales |
| ¿Solución correcta? | **Limpiar en BD:** fusionar los dos `persona` en uno solo |
| ¿Workaround rápido? | Filtro por predio/oficina en el modal (requiere dev) |

---

## 7. Próximos pasos sugeridos

1. **Ejecutar el SQL de verificación** (Sección 4) en la BD de producción
2. **Confirmar cuál es el escenario** (A, B o C)
3. **Ejecutar limpieza en BD** (fusionar/borrar/completar CI)
4. **Verificar** que el buscador ahora devuelve 1 fila para "richard rojas"
5. (Opcional) Si hay homónimos reales en la organización, evaluar agregar filtro por predio/oficina en el modal