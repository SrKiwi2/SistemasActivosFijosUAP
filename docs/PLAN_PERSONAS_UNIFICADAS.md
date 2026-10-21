# Plan: personas unificadas por C.I. (duplicados, cargo por C.I. e historial)

Estado: **plan, sin código.** 15-oct-2026. Decisiones D1-D7 cerradas con Kevin el mismo día (sección 9). Pedido de ING. Saul (chat del 15-oct):

> Trabajar con el CI. Si modifico el cargo a una persona, todos los registros con ese mismo
> CI se deberían actualizar, y que quede en historial esas modificaciones, por si hay otro
> tipo de errores.

Además, la idea previa de Kevin: un módulo que junte los repetidos, se elija uno como
original y, al modificarlo, se actualicen todos. Saul ya empezó a marcar a mano en un Excel
de RESP.DBF (ver «Ejemplos» abajo). Este plan reemplaza al punto 8 de la sección 4 de
`docs/HANDOFF_PLANTILLA_MODULOS.md` («Personas duplicadas: solución de fondo»).

Consultas de medición (solo lectura): `docs/sql/medir_personas_duplicadas.sql`. Se
corrieron el 15-oct-2026 sobre `bd_a3`. Resultados en la sección 2.

---

## 1. Cómo están los datos hoy (verificado en el código)

- **El VSIAF no tiene una tabla de personas.** RESP.DBF tiene una fila por responsable *en
  cada oficina* (`UNIDAD + CODOFIC + CODRESP`), con `NOMRESP` (60), `CI` (15) y `CARGO` (40)
  copiados en cada fila. Una misma persona puede tener decenas de filas, y cada fila tiene
  sus bienes. **Esas filas no se borran ni se juntan**: son la clave de los bienes. Lo que se
  unifica es que todas digan lo mismo (nombre, C.I., cargo).
- **En el SCIAF:** `persona` (nombre, paterno, materno, ci) ← N `responsable` (uno por fila
  de RESP, con `id_cargo`). Nombre y C.I. viven en `persona`; **el cargo vive en cada
  `responsable`**.
- **Cómo la sincronización arma las personas** (`ResponsableController.syncFromMounted`,
  l. 569-637):
  1. Si la fila tiene C.I. «válido» (solo dígitos, 5 o más, después de quitar `.`, `-` y
     espacios) → busca la persona por C.I. o la crea.
  2. Si no, busca por **nombre exacto** (partido en nombre/paterno/materno: las dos últimas
     palabras son los apellidos) o la crea.
  3. Si tampoco hay nombre → persona comodín `SIN DATOS`.
- **De ahí salen los duplicados:**
  - Misma persona con filas con C.I. y filas sin C.I. → dos personas (caso Richard Rojas).
  - **C.I. mal tipeado** (`7479551` / `7479561`) → dos personas «con C.I.» para el mismo
    funcionario. La sync confía en el C.I. y no compara nombres.
  - **C.I. sospechosos** (`000000`, `123456`, `1234567`, `1111000`): según Kevin (15-oct)
    **no son falsos** y siguen identificando.
  - C.I. con extensión o complemento (`1234567 LP`): la regla de «solo dígitos» los trataría
    como sin C.I. La medición encontró **0** en la base, así que hoy no pasa.
  - **La Ñ se pierde al leer el DBF.** `JavaDbfService` lee RESP.DBF como UTF-8 si no se le
    pasa charset, y la sync no lo pasa. La Ñ de un DBF en código de página DOS no es UTF-8
    válido, y `limpiarNombre` borra lo que no es `A-Z0-9`. Resultado: `ACUÑA` → `ACUA`,
    `MUÑOZ` → `MUOZ`, `PIÑEIRO` → `PIEIRO`. Si el DBF se lee bien, `limpiarNombre` igual
    cambia Ñ → N. Además deja sin tildes y cambia `.` por espacio. **Ojo:** como
    `propagarPersona` escribe a RESP.DBF el nombre de `persona`, ya puede estar escribiendo
    `ACUA` sobre `ACUÑA` en el VSIAF.
  - Nombre escrito distinto (`A. MARCELO CACERES BEJARANO`, `ALVARO M. CACERES BEJARANO`,
    `ABRAHIN LEON ROBLES` / `MIGUEL ABRAHIN LEON ROBLES`) → una persona por cada forma.
  - Dos funcionarios distintos sin C.I. y con el mismo nombre → **una sola** persona
    (el problema contrario: hay que separar, no juntar).
- **La sync pisa en cada pasada** la persona y el cargo de cada responsable con lo que diga
  RESP.DBF (`setPersona`, `setCargo`; el hash incluye `id_persona` e `id_cargo`).
  **Consecuencia clave:** unificar solo en PostgreSQL no sirve. La siguiente sync lo deshace.
  Hay que escribir también en RESP.DBF el nombre, C.I. y cargo unificados en cada fila.
- **Lo que ya existe y se reutiliza:**
  - `VsiafApoyoService.actualizarResponsable` → UPDATE de la fila de RESP.DBF con `NOMRESP`,
    `CI` y `CARGO` (por la cola del worker, `legacy.dbf.write.mode=cola`).
  - `ResponsableGestionService.propagarPersona`: ya manda nombre y C.I. a todas las
    oficinas de una persona (no el cargo). Solo lo hace si la persona tenía C.I. válido antes
    del cambio: protege a las personas comodín.
  - `ActividadService.registrar`: monitoreo de actividad (texto libre, no guarda valores
    antes/después).
- **Otras tablas que apuntan a `persona`:** `responsable`, `usuario` (uno a uno),
  `acta_faltante` (destinatario de la notificación). Los responsables de custodia de
  faltantes tienen un índice único por persona y predio (`uk_resp_custodia_persona`, creado
  por SQL, no está en las entidades).
- **Límite conocido:** el writer recorta `NOMRESP` a 35 aunque el campo es de 60
  (`RespDbfWriterService` l. 432). Como el C.I. va en cada fila, el recorte no rompe el enlace.
  Para personas sin C.I. sí importa: el nombre recortado es la clave.

## 2. Medición (corrida el 15-oct-2026)

`docs/sql/medir_personas_duplicadas.sql` corre en una transacción `READ ONLY` y termina en
`ROLLBACK`. Mide:

| # | Qué mide | Para qué |
|---|---|---|
| 0 | Personas, con/sin C.I., responsables vigentes | Tamaño del problema |
| 1 | C.I. con letras (extensión/complemento) | Cuántos trata hoy la sync como «sin C.I.» |
| 2 | Mismo número de C.I. en 2+ personas | Duplicados seguros |
| 3 | Mismo nombre exacto, una con C.I. y otra sin C.I. | Casi seguros (caso Richard) |
| 4 / 4b | Mismos apellidos + un nombre o inicial en común; nombre contenido en otro | Candidatos a revisar a mano (casos de la foto) |
| 5 | Personas sin C.I. con varios responsables | Comodines: posibles personas distintas juntas |
| 6 | Personas con cargos distintos según la oficina | Alcance real del «cargo por C.I.» |
| 7 / 7b | Catálogo de cargos y pares de cargos que parecen el mismo | Volumen de la pestaña «Cargos» |
| 8 | Los casos de la foto de Saul | Control: deben salir agrupados |
| 9 | Mismo nombre exacto con 2+ C.I. distintos | C.I. mal tipeado |
| 10 | C.I. sospechosos (ceros, secuencias) | Revisar con Saul (no se descartan) |
| 11 | Nombres con marcas (DEVOLUCION, FALT, ACEFALO…) | Registros que no son una persona |
| 12 / 13 | Nombres que perdieron la Ñ | Bug de lectura del DBF |

### Resultados

| Qué | Cuánto |
|---|---|
| Personas activas | 767: 557 con C.I. y 210 sin C.I. |
| Responsables vigentes (sin custodia) | 2443, de 679 personas |
| Mismo número de C.I. en dos personas | 2 pares (Danny Dominguez Meza; Guillermina Suarez Noza con `4206518.`) |
| Mismo nombre, una con C.I. y otra sin C.I. | 76 pares (caso Richard Rojas) |
| Mismo nombre, **C.I. distinto** | 15 nombres. Casi todos son un dígito cambiado o de más (`7479551`/`7479561`, `4917730`/`4817730`, `1727774`/`17277774`) |
| Nombre parecido (inicial, nombre de más, apellido recortado) | 40 pares + 19 con un nombre contenido en otro |
| Personas sin C.I. con varios responsables | 40+. Las más grandes: `JORGE ARROYO Z` (36), `WILSON SALAZAR RODAS` (30), `FERNADO FELIZ MEJIA VALDIVIA` (30), `SIN DATOS` (13) |
| C.I. sospechosos (confirmados como **no falsos** el 15-oct) | `000000` (ACEFALO, 28 responsables), `12349000` (10), `123456` (3), `1111000` (2), `1234567` (1) |
| Nombres con marca | `… DEVOLUCION` 21 personas / 35 responsables; `… FALTANTE(S)` / `FALT` 5; `ACEFALO` 28 responsables; `… DEVENGADO` 1; dos personas en un nombre (`GUSTAVO ULAQUE O RICARDO A AYALA`, `DR ISRAEL TARQUI DR HERBER PORCOS`) |
| Ñ perdida | 6 personas (`NANCY ACUA`, `SIMONI MUOZ`, `CARLOS ARIAS PIEIRO`…). Solo 1 persona de toda la base tiene Ñ |
| Personas con cargos distintos según la oficina | **264**, con 879 cargos en juego |
| Catálogo de cargos | 950 cargos; 869 si se quitan puntos y espacios (81 variantes triviales) |
| Pares de cargos parecidos | 150+, muchos **no** son el mismo cargo (`ADMINISTRATIVO` / `ADMINISTRATIVO ACYT`) |

**Lectura:**
- **Duplicados de persona:** del orden de **130 a 150 pares** para revisar entre los
  puntos 2, 3, 9, 4 y 4b (hay solapes). Es trabajo de unas pocas sesiones, no de semanas.
  La mayoría son «mismo nombre, uno sin C.I.»: casi seguros, se confirman rápido en el
  módulo. La sync corregida evita que aparezcan nuevos, pero no junta los existentes.
- **El C.I. no es confiable por sí solo:** hay C.I. tipeados mal (un dígito). La unificación
  **no puede prohibir** juntar personas con C.I. distinto. Tiene que dejar elegir el
  correcto, con advertencia (sección 5).
- **Hay registros que no son una persona** (DEVOLUCION, FALTANTES, ACEFALO, DEVENGADO): son
  convenciones del VSIAF para marcar un estado. **No se unifican con la persona real**, ni
  automáticamente ni por sugerencia (D8).
- **El cargo varía de verdad en muchos casos.** `ALEXANDER VACA DIBIBAY`: ADMINISTRATIVO en
  CULP, RESPONSABLE DEL MUSEO DE HISTORIA y RESP. CIPA en POST. Son responsabilidades
  distintas, no un cargo viejo. Ver el ajuste a D1 en la sección 9.

### Casos de la foto de Saul, vistos en la base

- `ALVARO MARCELO CACERES BEJARANO`: **4 personas** en el SCIAF. Una con C.I. `7479551`, otra
  con `7479561` (un dígito distinto), `A MARCELO CACERES BEJARANO` sin C.I. y otra sin C.I.
  con el nombre completo. Dos cargos (`RESP. PARQUE AUTOMOTOR` y `… UAP`).
- `MIGUEL ABRAHIN LEON ROBLES` (C.I. `1768603`): **ya es una sola persona**. La fila
  `ABRAHIN LEON ROBLES` se enlazó por C.I. Pero en RESP.DBF sigue con el nombre distinto,
  y tiene 6 cargos distintos. Su nombre en el VSIAF se uniforma con un «enviar a todas las
  filas», sin unificar nada.
- `ALEJANDRA ROJAS RODRIGUEZ`: una persona, C.I. `12349000` (sospechoso), 10 filas, 3
  variantes de cargo.
- `ALEIDA ROJAS POQUECHOQUE`: una persona, `RESP. CARRERA DE ARQUITECTURA` / `RESP. CARRE.
  DE ARQUITECTURA`. Solo hay que normalizar el cargo.
- `AGAPITO YUMAA ENRIQUEZ`: quizá `YUMAÑA`, con la Ñ perdida (a confirmar con Saul).

**Lo que la base no muestra:** el `NOMRESP` tal como está escrito en cada fila de RESP.DBF.
El SCIAF guarda solo el nombre de la persona enlazada. Para ver las variantes por fila, el
módulo tiene que leer RESP.DBF (montaje `/mnt/dbfwin`, en el servidor). Esta máquina no
tiene ese montaje.

## 3. El módulo

Nombre propuesto: **Supervisión › Personas y cargos** (`opcion_personas_cargos`). Pantalla
con la plantilla común (`sciaf-modulo.*`) y cuatro pestañas.

**Permiso (D4, decidido):** ADMINISTRADOR y SUPER USUARIO entran siempre. Cualquier otro
usuario, por ejemplo uno de rol APOYO, entra **solo si se le asigna la opción del menú**
desde Usuarios / permisos, como cualquier otra opción. Al quitársela pierde el acceso al
momento (permisos en vivo). Patrón ya usado en `BajaActivoController.tienePermiso`:
`RolesSciaf.esAdministrativo(request)` o la opción presente en `session.opciones`.
`/administracion/**` es `permitAll()`, así que la revisión va **en el controlador y se
repite en el servicio** de cada acción que escribe, no solo al abrir la vista. La opción la
crea `OpcionMenuSeeder`, que solo inserta lo que falta. Todo lo que hace un usuario
asignado queda en el monitoreo de actividad y en el historial con su nombre.

**Autorización (D7, decidido):** ADMINISTRADOR y SUPER USUARIO aplican directo. Un usuario
asignado (APOYO) **no aplica nada**: cada acción que escribe genera una solicitud al revisor.
Eso incluye cambiar el cargo/nombre/C.I. en la ficha, unificar, separar, normalizar cargos y
revertir. Se reutiliza `AutorizacionService`:

- tipos nuevos (`PERSONA_CAMBIO`, `PERSONA_UNIFICAR`, `CARGO_NORMALIZAR`,
  `PERSONA_REVERTIR`) con su ejecutor;
- en `datos` va la decisión completa y el hash de las filas al momento de pedir;
- al aprobar, el ejecutor corre con el solicitante como autor y **revalida** el hash. Si
  alguien tocó esas filas mientras tanto, la solicitud queda FALLIDA y se avisa;
- la vista previa se ve igual desde la solicitud, para que el revisor sepa qué aprueba;
- `solicitar` solo impide dos pendientes sobre el mismo `idRegistro` (la persona original o
  el cargo elegido). Además hay que impedir dos solicitudes pendientes que **compartan
  filas o personas**, y marcar en «Duplicados» los grupos con solicitud en curso.

### 3.1 Pestaña «Duplicados»

- Lista de **grupos candidatos**, armados en el servidor con las reglas 2, 3, 4/4b, 5 y 9
  de la medición. Cada grupo tiene un nivel:
  - **Seguro**: mismo número de C.I.
  - **Probable**: mismo nombre, uno sin C.I.
  - **C.I. en conflicto**: mismo nombre, C.I. que difieren en un dígito, en uno de más o
    uno falso.
  - **Revisar**: nombre parecido.
  - **Separar**: comodín con varios funcionarios.
  - **Especial**: nombres con marca (DEVOLUCION, FALTANTE(S), ACEFALO, DEVENGADO, dos
    personas en un nombre). **Nunca se sugiere juntarlos** con la persona real. Se listan
    aparte para decidir qué hacer con ellos (D8).
- Un grupo se arma **por componentes**, no por pares. Las 4 personas de Alvaro Caceres
  salen en un solo grupo.
- **C.I. que no identifican:** lista editable en el módulo (parámetro `personas.ci_invalidos`,
  **vacía**). Solo se agrega uno con confirmación; mientras tanto ningún C.I. se descarta.
- Filtros: nivel, predio, texto. Contadores arriba.
- Un grupo descartado («no son la misma persona») no vuelve a salir: queda en
  `persona_no_duplicado`.

### 3.2 Detalle de un grupo: «Unificar»

Se ve como el Excel de Saul: una fila por responsable (unidad, oficina, código, nombre y
C.I. como están en el VSIAF, cargo, bienes, estado en la cola). El nombre **tal como está
en cada fila de RESP.DBF** se lee del DBF en el servidor. Si el montaje no está, se muestra
el de la persona y se avisa. El usuario:

1. Marca qué filas son de esta persona. Puede quitar una que sea de otra persona.
2. Elige la **persona original**. Por defecto, la que tiene C.I. y más bienes.
3. Fija el **nombre oficial** (nombre, paterno, materno) y, si quiere, el **C.I.**. Por la
   decisión D2, el C.I. es **opcional**: se puede unificar sin C.I. Si se carga, mejor,
   porque desde ahí la sync enlaza las filas por C.I. y no por nombre. Antes de aceptarlo,
   el servidor revisa que ese C.I. no sea de otra persona: si lo es, se ofrece unificar con
   ella también o se rechaza. **Si las personas del grupo tienen C.I. distintos**
   (`7479551` / `7479561`), no se bloquea: se muestran los dos con sus filas y bienes, el
   usuario elige el correcto y escribe un motivo. Queda en el historial con los dos números.
   Un C.I. de la lista `personas.ci_invalidos` (vacía por ahora) no se puede elegir como oficial.
4. Fija el **cargo** (D1): las filas se muestran **agrupadas por su cargo actual**, con una
   casilla por grupo y por fila. Al principio vienen marcadas todas.
5. Ve la **vista previa**: qué filas de RESP.DBF cambian, con el antes y el después de
   `NOMRESP` / `CI` / `CARGO`.
6. Confirma con `sciafConfirmar`.

### 3.3 Pestaña «Personas»: ficha y cambio por C.I.

Es el pedido directo de Saul. Se busca a la persona por C.I. o nombre y se ve la ficha
(datos y todas sus oficinas). Al **cambiar el cargo** (o nombre / C.I.):

- se aplica a **todas** sus filas vigentes, con casillas por fila y por grupo de cargo
  actual para dejar alguna aparte (D1);
- botón **«Uniformar en el VSIAF»**: manda el nombre y C.I. de la persona a todas sus filas
  de RESP.DBF, sin cambiar nada en el SCIAF. Es para casos como `ABRAHIN LEON ROBLES`, que
  ya es una sola persona en el SCIAF pero tiene filas con otro nombre en el VSIAF;
- un solo lote de historial;
- un UPDATE por fila a RESP.DBF por la cola, con estado por fila (enviado, pendiente,
  falló).

Reemplaza la edición unitaria del cargo en Responsables: allí se avisa «esta persona tiene
N oficinas más, ¿aplicar a todas?».

### 3.4 Pestaña «Cargos»: normalizar el catálogo (D5)

La sync crea un `cargo` por cada texto distinto del DBF, así que hay variantes del mismo
cargo: `RESP. PARQUE AUTOMOTOR` / `RESP. PARQUE AUTOMOTOR UAP`, `RESP. CARRERA DE
ARQUITECTURA` / `RESP. CARRE. DE ARQUITECTURA`, `AUXILIAR ADMINISTRATIVO` / `AUXILIAR
ADMINITRATIVO`.

- **Grupos de cargos parecidos**: mismo texto sin puntos ni espacios (81 casos, casi
  seguros), distancia de edición corta, abreviatura (`CARRE.` / `CARRERA`) o uno contenido
  en otro. Se muestra cuántos responsables y personas usa cada uno. El caso «uno contiene
  al otro» da muchos falsos parecidos (`ADMINISTRATIVO` / `ADMINISTRATIVO ACYT`, 150+
  pares). Va en un nivel aparte, «revisar», y **nunca se aplica en bloque**.
- Se elige **uno como el correcto**; se puede corregir su texto, máximo 40 caracteres
  (campo `CARGO` de RESP.DBF). Los demás del grupo se marcan:
  - todos los responsables con esos cargos pasan al elegido;
  - los cargos sobrantes quedan `FUSIONADO` con `fusionado_en` (no se borran);
  - un UPDATE por fila a RESP.DBF (`CARGO`) por la cola;
  - un lote en el historial.
- Vista previa con el total de filas que cambian, porque un cargo común puede tocar cientos.
  Si pasa de un umbral (p. ej. 100 filas), se pide una confirmación extra y se encola por
  tandas.
- «No son el mismo cargo» descarta el par (`cargo_no_duplicado`).
- En la ficha de persona y al unificar, el campo cargo **solo ofrece cargos del catálogo
  vigentes**, con opción a uno nuevo. Así no nacen variantes nuevas desde el SCIAF.

### 3.5 Pestaña «Historial»

Por persona y por lote: quién, cuándo, qué cambió (antes y después) en cada fila, y el
estado del envío al VSIAF. **Revertir un lote**: devuelve a cada responsable su persona,
nombre, C.I. y cargo anteriores, y reencola el UPDATE. Solo si nada posterior tocó esas filas.

## 4. Base de datos (cambios de esquema)

Se crean con `hbm2ddl=update`: columnas nuevas nulas y tablas nuevas, sin tocar datos.

- `persona.fusionada_en` (FK a `persona`, nulo) + estado `FUSIONADA`. **No se borra
  ninguna persona.** El análisis de `DOCS/analisis-duplicado-richard-rojas.md` §5.1 propone
  un `DELETE`: no seguirlo.
- `persona_cambio`: `id`, `lote` (uuid), `id_persona`, `id_responsable` (nulo si el cambio es
  de la ficha), `tipo` (`FUSION`, `NOMBRE`, `CI`, `CARGO`, `SEPARACION`, `REVERSION`),
  `antes` / `despues` (json: persona, nombre, ci, cargo), `id_usuario`, `fecha`, `id_cola`
  (envío al VSIAF) y `motivo`.
- `persona_no_duplicado`: par `(id_persona_a, id_persona_b)` descartado, quién y cuándo.
- `cargo.fusionado_en` (FK a `cargo`, nulo) + estado `FUSIONADO`.
- `cargo_no_duplicado`: par de cargos descartado.
- `persona_cambio` también registra la normalización de cargos: `tipo = CARGO_NORMALIZADO`,
  con una fila por responsable afectado y el mismo `lote`.

## 5. Cómo se aplica una unificación (servicio, una transacción)

`PersonaUnificacionService.unificar(grupo, decisiones, autor)`:

1. Revalida en el servidor: las filas siguen vigentes y nadie las cambió desde la vista
   previa (hash). Si hay C.I. distintos entre las personas que se juntan, exige el C.I.
   elegido y el motivo. Ninguna persona del grupo puede ser «especial» (D8).
2. Actualiza la persona original con el nombre y C.I. oficiales. Pasa cada responsable
   marcado a la original y le pone el cargo elegido (`obtenerCargo`).
3. Personas sobrantes: `estado = FUSIONADA`, `fusionada_en = original`. `acta_faltante`
   pasa a la original: las actas guardan copia de los datos, solo cambia «de quién».
   `usuario`: si la sobrante tiene usuario y la original no, se mueve; si las dos tienen,
   **se bloquea** y se avisa.
4. **Custodia de faltantes:** si las dos personas tienen responsable de custodia en el
   mismo predio, choca con `uk_resp_custodia_persona`. Primera versión: bloquear y avisar.
   Después: mover los bienes de una custodia a la otra.
5. Escribe `persona_cambio` (un lote) y `ActividadService` (monitoreo).
6. **Después del commit:** encola un `actualizarResponsable` por fila (NOMRESP, CI, CARGO),
   igual que `propagarPersona`, y guarda el resultado de cada envío en el historial. Las
   filas `pendienteDbf` no se encolan: su alta lee el dato vigente.

**No toca bienes, oficinas ni claves de responsable.** Los bienes siguen en la misma fila.

## 6. Cambios en la sincronización (fase 1, antes que nada)

Sin esto, la sync rehace los duplicados o deshace la unificación:

0. **Leer RESP.DBF con el código de página correcto y conservar la Ñ.** Hoy se lee como
   UTF-8 y la Ñ se pierde (sección 1). Hay que confirmar el código de página real del DBF
   (byte de idioma de la cabecera; probablemente CP850). Después, `limpiarNombre` tiene que
   dejar la Ñ. **Antes de corregirlo**, revisar con qué código de página escribe
   `RespDbfWriterService`: si escribe en otro, cada propagación ya estaría deformando nombres
   en el VSIAF. Es lo **primero** que hay que hacer: con nombres deformados, la unificación
   escribiría `ACUA` en todas las filas.
1. **Normalizar el C.I.**: separar número y extensión/complemento (`1234567 LP` →
   `1234567`). Enlazar por el número. Hoy no hay ninguno así, pero la regla evita que
   aparezcan. Los C.I. del parámetro `personas.ci_invalidos` (vacío) se tratan como «sin C.I.».
2. **Fila sin C.I. con el nombre de *una sola* persona con C.I.** → enlazarla a esa (lo
   mismo que hace el alta manual en `ResponsableAltaService` l. 135-141). Con dos o más
   candidatas, no adivinar: se crea o usa la persona por nombre, como hoy, y aparece en
   «Duplicados». Al revés también: fila **sin** C.I. con el nombre de una única persona con
   C.I. → esa. Los nombres «especiales» (D8) no entran en esta regla. **Solo se aplica a
   filas nuevas o que dejaron de concordar con su persona:** una fila ya enlazada que sigue
   concordando no se mueve. Por eso los 76 pares existentes no se resuelven solos; los
   resuelve el módulo, donde alguien los mira.
3. **Respetar las fusiones**: el caché de personas hoy carga *todas* (`findAll`). Una
   persona `FUSIONADA` debe redirigir a su `fusionada_en`, nunca recibir responsables.
4. **No pisar lo que está en la cola**: si un responsable tiene un UPDATE pendiente en la
   cola del worker, la sync no le cambia persona ni cargo hasta que se confirme. Hoy no lo
   revisa: entre que se encola y el worker escribe el DBF, una sync puede devolver el dato
   viejo. **Verificar** si el detector de cambios dispara la sync de responsable en ese
   intervalo.
5. **Respetar los cargos fusionados**: si el DBF trae el texto de un cargo `FUSIONADO`
   (por ejemplo, una fila que el worker todavía no actualizó, o que alguien escribió así en
   el VSIAF), se usa el cargo de `fusionado_en`, no se crea uno nuevo.
6. Contadores nuevos en el resultado de la sync: enlazadas por nombre y candidatas ambiguas.

### Estado de la fase 1 (15-oct-2026): hecha y revisada, sin commitear ni desplegar

**Decisiones de Kevin del 15-oct que cambian lo de arriba:**
- **La sync no corrige nada por su cuenta.** No completa C.I., no repone la Ñ en nombres
  de personas ya guardados y no renombra cargos. Lo deja como **sugerencia** (contadores
  `sugerencias*` y líneas `Sync RESP sugerencia · …` en el log), para que un usuario lo
  aplique paso a paso desde el módulo (fases 2-4). Los puntos 2 (completar C.I.) y 0 (en lo
  que toca a nombres guardados) de esta sección quedan así.
- **No hay C.I. falsos por defecto.** Los sospechosos de la medición (000000, 123456,
  1234567, 1111000, 12349000…) **no son falsos** (Kevin). El parámetro
  `personas.ci_invalidos` existe, pero vacío. Solo se carga un C.I. ahí con confirmación.
- **Activos y oficinas con «¥»:** autorizado. La sync de ACTUAL repone la Ñ en la
  descripción y las observaciones; la de OFICINA, en el nombre y las observaciones. Solo
  cambia el texto en el SCIAF (≈222 activos, 14 oficinas en la siguiente pasada). No toca
  códigos, responsables, oficinas ni valores, y no escribe en el VSIAF.

**Lo que hace la sync de RESP ahora** (`model/service/personas/ResolutorPersonaSync`):
- **No mueve lo ya enlazado:** si la persona de la fila sigue concordando (mismo C.I., o
  mismo nombre salvo la Ñ cuando la fila no trae C.I.), se queda.
- **Fila nueva:** como antes. Si trae C.I., va a la persona con ese C.I.; si nadie lo
  tiene, se crea. Si no trae C.I., va por nombre: primero a una persona sin C.I., si no a
  la única con C.I.; con dos o más con C.I. no se adivina y se crea una sin C.I.
- **Unificaciones del módulo:** una persona FUSIONADA no recibe filas. Su C.I. y su nombre
  viejos apuntan a la persona destino.
- **Nombres especiales (D8):** una fila ya enlazada no se mueve; una nueva no se identifica
  por C.I.
- **Lo único que cambia sola** es lo que ya hacía la sync vieja: una persona con C.I. y sin
  nombre («SIN DATOS») toma el nombre de la fila.
- **Ñ:** se repone «¥»→Ñ en lo leído de RESP (NOMRESP, CARGO, OBSERV). Lo hace
  `interoperabilidad/TextoVsiaf`, dentro de cada sync y no en `JavaDbfService`, porque
  `upsertResponsable` y el alta de oficinas reescriben el DBF entero con ese lector. Para
  personas y cargos ya guardados sin Ñ no corrige nada: los reconoce como el mismo y lo
  sugiere. Las filas nuevas solo se enlazan con la Ñ borrada (PENA ≠ PEÑA).
- **No pisa lo que no llegó al VSIAF:** se saltan los responsables cuya última orden a RESP
  está ENCOLADA o en ERROR, y las filas del DBF con la clave vieja de esas órdenes.
- **Normalizador de nombres** idéntico al viejo salvo que conserva la Ñ.
- **Esquema:** columnas nulas `persona.fusionada_en` y `cargo.fusionado_en` (`hbm2ddl=update`).
- `ResponsableGestionService.esIdentificable` usa la misma lista de C.I. que la sync, y un
  nombre especial no propaga.

**Verificación:**
- `ResolutorPersonaSyncTest`: 21 pruebas, pasan.
- **Prueba en seco con datos reales** (solo lectura): los 2452 responsables quedaron en su
  persona, sin crear ni guardar nada. *Límite:* arma las filas con los datos que ya tiene
  el SCIAF, así que no ejercita los nombres reales de RESP.DBF (sin montaje en esta
  máquina).
- **Revisión independiente** de la primera versión: 2 defectos altos y varios medios, todos
  corregidos.

### Estado de las fases 2 + 4 (19-oct-2026): escritas y revisadas, sin commitear ni probar en navegador

Decisión de Kevin (15-oct): **todo en un solo módulo**. En la ficha se elige la persona
principal y se le **suman** sus otros registros (la unificación de la fase 4 queda dentro de
la ficha). La pestaña de sugerencias automáticas de duplicados queda para después.

- **Pantalla:** Usuarios y Accesos › **Personas y cargos** (`opcion_personas_cargos`,
  `/administracion/personas-cargos/vista`, `templates/personasCargos/vista.html`).
  1. Buscar a la persona.
  2. Sumarle otros registros (opcional): aparece «Datos que quedan» (nombre, C.I.).
  3. Elegir el cargo y marcar oficinas, agrupadas por su cargo actual.
  4. **Vista previa** (no guarda).
  5. **Aplicar**, o **Enviar solicitud** si es APOYO.
  Abajo, el historial.
- **Cálculo:** `PlanPersona` (puro, probado en `PlanPersonaTest`). La vista previa, la
  aplicación y la aprobación usan el mismo cálculo. La **firma** de la vista previa se
  exige al aplicar: si algo cambió entretanto, no se aplica.
- **Bloqueos:**
  - persona especial (D8);
  - más de una con usuario del sistema;
  - un C.I. que es de alguien fuera del grupo;
  - dos custodias en la misma oficina;
  - cargo de más de 40 caracteres;
  - nada que cambiar.

  **Avisos** (no bloquean):
  - C.I. distintos en el grupo (el motivo es obligatorio);
  - nombre de más de 35 caracteres (el VSIAF lo recorta).
- **Aplicar** (`PersonasCargosService.aplicar`):
  1. En una transacción: datos de la principal; sobrantes en estado `FUSIONADA` con
     `fusionada_en` (no se borran), sus actas de faltantes y su usuario pasan a la principal;
     las filas cambian persona y cargo.
  2. Después del commit: un UPDATE por fila a RESP.DBF por la cola. Las filas pendientes
     o con error no se envían: las lleva su alta.
  3. Se escribe el historial en `persona_cambio` (un lote) y en el monitoreo.
- **Sin VSIAF (localhost):** solo consulta. Aplicar está desactivado y el servidor lo
  rechaza, también al aprobar una solicitud.
- **Responsables:** al guardar un cargo nuevo en una persona con más oficinas, se ofrece
  abrir Personas y cargos con la persona y el cargo ya elegidos.
- **Historial de nombre / C.I.:** las ediciones desde Persona y Responsables también se
  anotan (`PersonaHistorialService.registrarDatos`).
- **Revisión independiente (19-oct)** — corregido:
  1. Sin C.I., un nombre de más de 35 caracteres ahora **bloquea**: el VSIAF lo recorta y la
     sync no reconocería a la persona. Además la sync reconoce el nombre recortado.
  2. Aplicar directo se revisa también en el servicio (`aplicarDirecto` exige rol
     administrativo); la aprobación de una solicitud usa el camino interno.
  3. En las oficinas de un registro sumado, la casilla **«es de esta persona»** permite
     dejar fuera una oficina de otro funcionario. Ese registro no se fusiona y sigue vigente.
     Si la principal no tiene C.I. y tiene varias oficinas, se avisa que el nombre / C.I.
     van a todas.
  4. La sync no pisa una fila **pendiente** cuyo DBF dice otra cosa (envío fallido). Si
     coincide, la desmarca como antes.
  5. Dentro de la transacción se bloquean las personas (`FOR UPDATE`), se rehace el plan y se
     compara la firma. Las personas se leen por JDBC, no de la sesión JPA del pedido.
  6. Una fila con el último envío en ERROR se informa como **no llegó** (no como «sin
     enviar»).
  7. Una custodia de la principal en la misma oficina, en cualquier estado, bloquea (el
     índice único no filtra por estado).
  8. Si falla el registro del historial después del commit, ya no se informa «no se aplicó
     nada».

  Pendiente, menor: lo aplicado al aprobar una solicitud no queda enlazado al id de la
  solicitud en `persona_cambio` (el ejecutor no lo recibe). Aprobar desde un servidor sin
  VSIAF deja la solicitud FALLIDA: hay que pedirla de nuevo.
- **Pruebas:** `PlanPersonaTest` (15) y `ResolutorPersonaSyncTest` (22) pasan. Plantillas
  renderizadas con un modelo falso. JS revisado con `node --check`. Prueba en seco de la sync
  con datos reales (solo lectura, nombres recortados a 35): 2453 filas, 0 movidas.
- **Incidente 15-oct:** compilar con la app local corriendo (devtools) la reinició y su
  arranque creó en `bd_a3` las columnas `persona.fusionada_en` y `cargo.fusionado_en`, la
  tabla `persona_cambio` (vacía) y la opción de menú. Kevin eligió dejar la opción (B):
  funciona al desplegar.

### Estado de la fase 3 (19-oct-2026): escrita y revisada, sin commitear ni probar en navegador

- **Pestaña «Cargos»** en el mismo módulo (Personas | Cargos).
  - Grupos sugeridos por `AgrupadorCargos`. Con los datos reales (solo lectura): 913 cargos
    en uso → 158 grupos (53 seguros, 51 probables, 54 a revisar).
  - **Juntar a mano** con un buscador.
  - Por grupo:
    1. elegir el que queda (texto corregible, ≤40);
    2. marcar los que se juntan;
    3. vista previa (no guarda);
    4. aplicar o enviar solicitud. Sobre 100 oficinas se pide una segunda confirmación.
  - **No son el mismo cargo** guarda el par en `cargo_no_duplicado`.
- **Reglas del agrupador** (ajustadas con los datos reales):
  - **Seguro:** solo cambian puntos, guiones o espacios.
  - **Probable:** abreviatura o tipeo. Una palabra de 3 letras solo cuenta como
    abreviatura si va con punto (`ADM.`): `ACS` / `ACSYH` son áreas distintas.
  - **Género o número** (`DIRECTOR` / `DIRECTORA`, `TECNICO` / `TECNICOS`): nunca forman un
    grupo por sí solos. Si entran por cadena (por ejemplo, `DIR.` abrevia a los dos),
    vienen **sin marcar**.
  - **Revisar:** uno es el otro con palabras de más (`ADMINISTRATIVO` /
    `ADMINISTRATIVO ACYT`). Nada viene marcado.
- **Aplicar** (`CargosService`):
  1. Bloquea los cargos (`FOR UPDATE`), rehace el plan y compara la firma.
  2. Renombra el elegido si se pidió. Los juntados pasan a `FUSIONADO` con
     `fusionado_en` (no se borran).
  3. Las filas activas pasan al elegido.
  4. Después del commit, un UPDATE por fila a RESP.DBF (`EnvioFilasVsiaf`, común con la
     ficha). Historial por fila en `persona_cambio` (tipo `CARGO`, origen `CARGOS`) y
     monitoreo (`MOD_CARGO`).
- **En el resto del sistema:** `/api/cargos/search` ya no ofrece los fusionados.
  `ResponsableAltaService.obtenerCargo` usa el cargo destino si alguien escribe el texto de
  uno fusionado.
- **Revisión independiente (19-oct)** — corregido:
  1. **Renombrar** deja un **alias** con el texto viejo (`FUSIONADO` → elegido). Sin él, la
     sync recreaba el cargo viejo y devolvía filas a él (filas aún no enviadas, filas fuera
     del plan, altas del VSIAF con el texto viejo).
  2. No se puede renombrar al texto de un cargo de **otro grupo**, aunque esté fusionado. Un
     bloqueo por texto (`pg_advisory_xact_lock`) impide que dos aplicaciones a la vez dejen
     dos cargos vigentes con el mismo nombre. El caché de la sync prefiere el vigente.
  3. **Agrupador** más estricto:
     - plurales en -ES (`AUXILIAR` / `AUXILIARES`) y género en -E (`PRESIDENTE` /
       `PRESIDENTA`);
     - tipeo de 2 letras solo en palabras de 10+ que empiezan igual (`BIOLOGIA` /
       `ECOLOGIA` no); cambiar solo la última letra no es tipeo (`PERSONAL` / `PERSONAS`);
     - abreviatura sin punto solo en la última palabra (corte por largo).

     Si el primero es neutro (`DIR.`), los que chocan entre sí vienen sin marcar. Al cambiar
     el elegido, la pantalla desmarca los de género o número distinto a él.
  4. El texto del cargo se pone con `.val()`: un nombre con comillas no rompe la pantalla
     ni deja inyectar código.
  5. Las filas cuya oficina no tiene predio también entran (LEFT JOIN).
  6. **No son el mismo cargo** solo lo usan ADMINISTRADOR / SUPER USUARIO. Tope de 40
     cargos, en una transacción, con `ON CONFLICT DO NOTHING`.
  7. `CargoServiceImpl.buscarPorNombre` sigue las fusiones y tolera nombres repetidos. Lo
     usan Bajas, Transferencias, Asignaciones, Ingresos y el alta de responsables.

  **Pendiente / límite conocido:**
  - `ResponsableImportService` (importación de RESP) usa `findFirstByNombreIgnoreCase` y
    no sigue fusiones.
  - Las filas que la sync pasa al elegido por un alias cambian en el SCIAF, pero RESP.DBF
    conserva el texto viejo hasta que alguien edite esa fila. Se podría listar en una fase
    futura.
- **Pruebas:** `CargosTest` (14) más las anteriores: 56 en total, pasan. Con los datos
  reales (solo lectura): 157 grupos (54 seguros, 49 probables, 54 a revisar).

### Estado de la fase 5 (19-oct-2026): escrita y revisada, sin commitear ni probar en navegador

- **Pestaña «Historial»** (Personas | Cargos | Historial).
  - Lotes con fecha, usuario, origen, oficinas, motivo y los que no llegaron al VSIAF.
  - Filtros por texto, usuario y fechas.
  - **Ver detalle**: antes → después de cada oficina.
  - **Revertir…**: vista previa y después revertir (o solicitud si es APOYO), con motivo
    obligatorio.
- **Solo se revierte si nada cambió después** (`PlanReversion`, puro, probado en
  `PlanReversionTest`). Hoy todo tiene que estar como el lote lo dejó. Además:
  - la persona o el cargo a los que vuelve una oficina tienen que seguir vigentes, salvo
    que el mismo lote los reactive;
  - si la persona o el cargo recibieron oficinas después del lote, no se revierte el
    nombre / C.I. ni el nombre del cargo (quedarían distintas en el VSIAF);
  - el C.I. de antes no puede ser hoy de otra persona;
  - no se revierte una reversión, un lote ya revertido ni lo hecho en otras pantallas.
- **Lo que se deshace:**
  - oficinas: vuelven a la persona y al cargo de antes;
  - nombre / C.I. de la principal: vuelven los de antes;
  - personas sumadas: vuelven a vigentes, con los ids exactos de actas y usuario;
  - cargos juntados: vuelven a vigentes, el elegido recupera nombre y estado, y el alias
    pasa a ELIMINADO.
- **Cómo se aplica** (`HistorialLotesService`):
  1. SQL directo en una transacción con `FOR UPDATE`: el lote, las oficinas, las personas
     y los cargos (incluidos a quién y a qué vuelve cada oficina).
  2. Se rehace el plan y se compara la firma.
  3. Queda un lote nuevo (origen `HISTORIAL`, `lote_revertido`) y el original queda con
     `revertido_por`.
- **Filas en camino (las tres operaciones):** las oficinas que ya estaban en el VSIAF quedan
  `pendiente_dbf` dentro de la transacción. La sync no las pisa con el dato viejo mientras
  viaja su UPDATE. `EnvioFilasVsiaf` las manda igual (`enviarFilaAlVsiaf(…, yaEstaEnVsiaf)`)
  y se desmarcan al encolarse. La ficha y la pestaña Cargos ahora también bloquean las
  oficinas que tocan.
- **Revisión independiente (19-oct):** 3 fallos altos o medios sin carrera (filas fuera del
  lote, destino no vigente, C.I. ocupado), 2 con carrera (bloqueos y la sync en el
  intervalo) y 3 menores (estado del elegido, falso bloqueo con nombres iguales, error 500
  en la vista previa). Todos corregidos.
- **Pruebas:** 71 en total (`PlanReversionTest` 15), pasan.

### Estado de la fase 6 (19-oct-2026): escrita y revisada, sin commitear ni probar en navegador

- **Medición previa** (solo lectura): las personas sin C.I. con muchas oficinas (`JORGE ARROYO
  Z` 36, `WILSON SALAZAR RODAS` 30…) tienen un solo cargo: son **una** persona sin C.I. y
  se resuelven uniendo (fase 2). El caso real de separar es **«SIN DATOS»** (13 oficinas
  sin nombre en el VSIAF) y algún homónimo.
- **Pasar oficinas a otra persona**, sección en la ficha:
  1. marcar oficinas propias de la principal;
  2. elegir destino existente o persona nueva;
  3. vista previa;
  4. aplicar o enviar solicitud (motivo obligatorio).

  Se revierte desde Historial: las oficinas vuelven y la persona nueva pasa a ELIMINADO
  si no recibió oficinas, usuario, actas ni unificaciones.
- **Reglas** (`PlanSeparacion`):
  - origen vigente y no especial, salvo «SIN DATOS»;
  - destino vigente, no especial y distinto del origen;
  - la persona nueva no puede ser una marca del VSIAF ni tener el C.I. de otra (si es de
    una unificada, se indica en quién quedó);
  - homónimo sin C.I. → bloqueo; homónimo con C.I. → aviso;
  - destino sin C.I. y con el mismo nombre que el origen → bloqueo;
  - nombre de más de 35 caracteres sin C.I. → bloqueo;
  - las oficinas de **custodia de faltantes no se pasan desde aquí** (se manejan en
    Faltantes).

  En «SIN DATOS» se avisa que el C.I. que traen esas filas en el VSIAF no está en el SCIAF.
- **«SIN DATOS» va en blanco al VSIAF** (`RespDbfWriterService` y `PlanPersona.nomresp`),
  como estaba, en vez del texto literal.
- **Sync:** una persona `ELIMINADO` ya no recibe filas nuevas (las que tiene se quedan). Hoy
  hay 0 personas ELIMINADO, así que no cambia nada existente. Prueba en seco: 2453 filas,
  0 movidas.
- **Revisión independiente (19-oct):** sin hallazgos críticos; corregidos el bloqueo de la
  baja, el C.I. de unificadas, los homónimos, el nombre especial de la nueva, las custodias,
  el recorte de la referencia, el candado de C.I. también en la fase 2 y el C.I. de personas
  ELIMINADO reutilizable.
- **Límite conocido (anterior al módulo):** la sincronización completa forzada de cada 6 h
  carga todos los responsables al empezar. Un cambio que se confirme mientras corre puede
  quedar pisado: `Responsable` no tiene `@Version`. Para evitarlo, aplicar los cambios lejos
  de las 00/06/12/18 h. Arreglarlo de fondo (versión optimista) es un cambio aparte.
- **Pruebas:** 82 en total (`PlanSeparacionTest` 11), pasan.

### Fase 8 (21-oct-2026): «una persona, un cargo» — escrita, sin commitear ni probar en navegador

Origen: Saul habló con la licenciada el 20-oct: **cada funcionario es una sola persona y
tiene un solo cargo**. Si se modifica su nombre o su cargo, cambia en todas sus filas con el
mismo C.I.; las filas sin C.I. no se tocan (Saul les irá poniendo el C.I.). Al escribir un
C.I. en «Modificar», que busque si existe y cargue sus datos.

Modelo: en el SCIAF, **una persona por C.I.**; cada oficina es una fila de `responsable`
que apunta a esa persona (RESP.DBF repite nombre y C.I. en cada fila, eso está bien).

Hecho:

1. **La sync sigue el C.I. recién escrito en el VSIAF.** Columna nueva `responsable.ci_dbf`
   (C.I. leído en la última pasada; `""` = vacío, `null` = aún no leído). Si cambió, la fila
   pasa a la persona con ese C.I. o a una nueva con él (`ResolutorPersonaSync.resolver(...,
   ciRecienEscrito)`). La primera pasada tras el despliegue solo anota `ci_dbf`: no mueve nada
   (sí guarda todas las filas una vez). Los nombres especiales no siguen ningún C.I.
   La persona que se queda sin oficinas no se toca: aparece en «Por arreglar».
2. **Responsables → Modificar:** al cambiar el C.I. busca a la persona (por número, sin
   extensión, sin reventar si hay dos). Si es de otra, carga su nombre y su cargo, y al guardar
   **la fila pasa a esa persona** (antes se creaba un segundo registro con el mismo C.I.). Si el
   nombre no cuadra con el de la dueña del C.I., se rechaza. Queda en el historial (SEPARACION).
3. **Nombre / C.I. / cargo van a las otras oficinas** de la persona al guardar
   (`propagarPersona(..., cargoNuevo, cambioDatos)`), solo las que cambian. Incluye las filas
   de la persona que en el VSIAF no tienen C.I.: si se saltaran, la sync las vería con el
   nombre viejo y las separaría en otra persona (hallazgo de la revisión). La fila solo pasa
   a la dueña del C.I. si **se cambió** el número de C.I. (editar un cargo nunca la mueve), y
   entonces no se tocan los datos de la dueña. Endpoint `buscar-por-ci` con sesión
   obligatoria. Se quitó el «¿abrir Personas y cargos?» de después de guardar.
4. **Módulo: un solo cargo.** Sin casillas por oficina: el cargo elegido va a todas; aviso
   «tiene N cargos distintos». Sigue «es de esta persona» para homónimos.
5. **Pestaña «Por arreglar»:** varios cargos (abrir ficha), C.I. repetido (abrir ficha con
   todas sumadas para unificar) y sin oficinas (sin usuario ni actas/duplicados) con baja
   lógica `ELIMINADO` solo para administradores (`BAJA_PERSONA` en el historial; no se revierte
   desde Historial).
6. **Arreglo de la ficha en blanco:** `SciafPrecarga.montar` vacía su contenedor y se montaba
   sobre la ficha visible (2.ª persona elegida, sumar, Recargar → ficha borrada). Ahora va en
   `.js-carga-ficha`.

Pendiente: probar en navegador; decidir si se guarda también el NOMRESP leído (para listar
oficinas con el nombre escrito distinto en el VSIAF); un solo Richard en buscadores, reportes y
faltantes (parte E, otra fase).

### Fase 9 (21-oct-2026): oficina repetida, habilitar/deshabilitar, faltantes antiguos — escrita y revisada, sin commitear ni probar en navegador

Origen: Saul preguntó qué pasa si en una oficina hay dos Richard (uno con C.I. y otro sin) y
se unen por error. Respuesta: casi siempre el segundo es donde el VSIAF le cargaba sus
**faltantes** (práctica vieja: la misma persona, a veces con un punto al final). Medido en
`bd_a3`: **118 oficinas** con la misma persona repetida (239 filas).

1. **Habilitar / Deshabilitar responsable** (Responsables reemplaza «Eliminar»): estado
   `INACTIVO` + `API_ESTADO 3` al VSIAF por la cola; habilitar = 1. Solo sin bienes. APOYO por
   solicitud (`RESP_ESTADO`). Filtro Activos / Deshabilitados / Todos.
2. **La sync espeja el VSIAF:** `API_ESTADO 3` → `INACTIVO`. Al desplegar, **382 filas** (todas
   sin bienes) pasan a deshabilitadas — confirmado por Kevin.
3. Consecuencias cubiertas (revisión): la sync de ACTUAL ya no ignora bienes de responsables
   INACTIVO; un deshabilitado no es destino válido (`ReglasCustodia.motivoDestino`) ni sale en
   los buscadores; un custodio inactivo en el VSIAF se reactiva allá antes del traslado; el
   siguiente código de funcionario cuenta todas las filas (la función de la base solo contaba
   ACTIVO y podía repetir un código que sigue en el VSIAF).
4. **Oficina repetida** en la ficha (paso 1): por cada registro que sobra, «Son faltantes →
   oficina de faltantes» (acta **AR con traslado**, sin plazo, a nombre de la principal;
   `ActaFaltanteService.regularizarRepetido`, origen `REPETIDO`, `contenido.conTraslado`) o
   «Deshabilitar este registro» si no tiene bienes (sin marcar al abrir; sale en la vista
   previa). Al **unificar** no puede quedar una oficina con dos registros con bienes (los que van
   en camino a la custodia no cuentan); si ya eran de la misma persona, solo aviso.
5. Un registro con DEVOLUCION/FALTANTES en el nombre se puede **sumar** (aviso), nunca ser la
   principal. «SIN DATOS» / sin nombre sigue sin poder sumarse.
6. «Por arreglar»: listas «Oficina repetida» y «C.I. del VSIAF distinto» (filas cuyo C.I. en el
   VSIAF no es el de su persona: cubre C.I. escritos allá antes de desplegar la fase 8).

Pendiente: probar en navegador; los controladores viejos de formularios que crean
responsables `INACTIVO` con API 1 (hoy 0 filas así) se verían como «Deshabilitado».

## 7. Fases

| Fase | Qué | Escribe en producción |
|---|---|---|
| 0 | ~~Correr la medición~~ (hecho el 15-oct). Revisar los resultados con Saul y cerrar D8 | No |
| 1 | Sync: puntos 0-6 de la sección 6. El 0 (Ñ / código de página) va primero | Sí, en la próxima sync: enlaza filas, corrige nombres y no borra nada |
| 2 | Esquema + `persona_cambio` + opción de menú, permiso y tipos de autorización + ficha «Personas» con cargo/nombre/C.I. por C.I. (pedido de Saul) | Sí, por acción del usuario |
| 3 | «Cargos»: normalizar el catálogo | Sí |
| 4 | «Duplicados» + unificar + descartar + botón «Unificar estas personas» en Faltantes | Sí |
| 5 | Historial + revertir lote | Sí |
| 6 | Comodines: separar una persona en varias | Sí |
| 7 | (Si llega) API de Londra por C.I. para nombre y cargo: la ficha propone el dato de Londra | — |

Los cargos van antes que los duplicados: si el catálogo ya está limpio, elegir el cargo al
unificar es elegir entre pocos y no entre variantes del mismo.

La fase 2 sola ya cumple el pedido de Saul para las personas con C.I. bien cargado. Las
fases 3-5 son para que «mismo C.I.» signifique de verdad «misma persona».

## 8. Riesgos

- **Todo pega en producción** (`bd_a3` + RESP.DBF real por la cola). Probar con una persona
  de pocas filas, anotar los ids y tener el revertir (fase 4) o un SQL de reversión listo
  antes de la primera unificación real.
- **Juntar a dos personas distintas** es el peor error: se mezclan los bienes de dos
  funcionarios en los reportes por persona. Mitigación: nunca automático, vista previa,
  C.I. distintos solo con elección explícita y motivo, autorización para APOYO
  y revertir.
- **Volumen de cola**: una persona con 20 filas encola 20 UPDATE. Una unificación masiva
  puede encolar cientos; ir por grupos.
- **Usuarios del VSIAF editando RESP a la vez**: la sync trae su versión. Con el punto 4 de
  la sección 6 el SCIAF no la pisa a ciegas, pero alguien tiene que decidir cuál vale.
- **NOMRESP recortado a 35**: nombres largos sin C.I. pueden no volver a enlazar. Confirmar
  el largo real del campo (pendiente desde el 22-sep).

## 9. Decisiones

Decididas por Kevin el 15-oct-2026:

- **D1. Cargo:** se aplica el mismo cargo a todas las oficinas de la persona, con casillas
  para excluir alguna. *Ajuste por la medición:* 264 personas tienen cargos distintos, y en
  varios casos son responsabilidades distintas de verdad (Alexander Vaca: Administrativo /
  Museo de Historia / CIPA). Por eso la pantalla agrupa las filas por cargo actual, para
  destildar un grupo entero de un clic. La regla no cambia: todas marcadas al empezar.
- **C.I. sospechosos (15-oct):** no son falsos; no se descarta ningún C.I. sin confirmación.
- **Correcciones (15-oct):** la sync no corrige datos por su cuenta; todo pasa como
  sugerencia a un usuario en el módulo, paso a paso.
- **D2. Persona sin C.I.:** se puede unificar sin C.I., identificándola por el nombre. Si
  se carga el C.I. al unificar, mejor: se valida que no sea de otra persona y se manda al
  VSIAF en cada fila.
- **D4. Acceso:** ADMINISTRADOR y SUPER USUARIO siempre. Otros usuarios (APOYO) solo si se
  les asigna la opción del menú (sección 3).
- **D5. Cargos:** se normalizan en este módulo. Se elige uno y se actualizan los demás
  (sección 3.4).

- **D3. Nombre oficial:** lo decide quien unifica. La API de Londra queda para más adelante
  (fase 7): cuando exista, la ficha solo **sugiere** su dato.
- **D6. Arreglo temporal de Faltantes:** **se queda**, por si aparece un duplicado nuevo
  antes de unificarlo. Además, cuando el registro de faltantes detecta otro registro de la
  misma persona, ofrece **«Unificar estas personas»**: abre el detalle de unificación del
  módulo con esas personas ya cargadas. Requiere el permiso del módulo; para APOYO, sale
  como solicitud de autorización. No bloquea el registro de faltantes: se puede seguir con
  «sumar otro registro» mientras la unificación espera.
- **D7. APOYO con la opción asignada:** **todo pasa por solicitud de autorización** al
  revisor, incluido cambiar el cargo en la ficha (sección 3, «Autorización»).

- **D8. Registros «especiales» del VSIAF** (`… DEVOLUCION`: 21 personas / 35 responsables;
  `… FALTANTE(S)` / `FALT`: 5; `ACEFALO`: 28 responsables, C.I. `000000`; `… DEVENGADO`: 1;
  nombres con dos personas): **se excluyen** de la unificación y de los enlaces
  automáticos de la sync. Se listan aparte, sin tocarlos. Saul (15-oct): «devoluciones no
  deberían haber hecho eso, devengado tampoco», y faltantes ya tiene su circuito. Los
  corrige él en el VSIAF; cuando cambien allá, la sync los trae.

- **D9 (20/21-oct-2026, Saul y la licenciada): una persona, un cargo.** Reemplaza las casillas
  de D1: el cargo va a todas las oficinas. Cambiar nombre o cargo cambia todas las filas con
  ese C.I.; las filas sin C.I. no se tocan. La sync **sigue el C.I. que Saul escribe en el
  VSIAF** (cambia la regla del 15-oct solo para C.I. recién escritos; los que ya estaban no
  mueven nada). Personas sin oficinas: baja en el SCIAF desde «Por arreglar»; el VSIAF no se
  toca. Lo de un solo Richard en buscadores y reportes va en otra fase.
