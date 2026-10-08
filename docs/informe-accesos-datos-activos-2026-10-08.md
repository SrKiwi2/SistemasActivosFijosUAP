# SCIAF: accesos actuales y datos de activos visibles

**Corte:** 8 de octubre de 2026 (Bolivia). **Fuente:** consultas de solo lectura a `bd_a3` y revisión del código vigente en este repositorio. **Estado:** diagnóstico para valoración; no se modificaron permisos ni pantallas.

## Resumen para responsables

El SCIAF tiene **19 cuentas activas**: 1 ADMINISTRADOR, 3 SUPER USUARIO, 14 APOYO y 1 RECEPCION. La tarea de **registrar activos** necesita mostrar y cargar costo, fecha de adquisición, vida útil, clasificación, oficina y responsable. La tarea de **completar activos pendientes** necesita los mismos datos; la pantalla muestra además costo por bien y costo total del acta. No se propone quitar el costo de estos dos flujos a quienes los realizan.

El costo también se muestra en pantallas que no registran activos: **Buscar / Filtrar Activos**, **Asignaciones**, **Asignar Activos** y el detalle de **Transferencias**. En consulta aparece por activo y se exporta; en seguimiento de asignaciones aparece por bien y como total del acta; en detalle de transferencias aparece el costo del bien. Esos accesos merecen una decisión independiente de la autorización para registrar.

Hoy los permisos son principalmente por **módulo**, no por campo ni por conjunto de activos. Un usuario con acceso a una lista puede consultar bienes de toda la institución; no se observó un filtro sistemático por oficina, responsable o activos propios en las rutas revisadas. Además, algunos endpoints de `/api/**` exponen datos aunque la opción correspondiente no figure en el menú. Por eso, ocultar columnas o quitar una opción del menú, por sí solo, no garantiza que el dato deje de estar disponible.

## Qué datos muestra cada módulo relevante

| Módulo | Datos de activos mostrados | Costo necesario para la tarea | Posible valoración |
|---|---|---|---|
| Registro Activos | Código, descripción, costo, fecha de adquisición, vida útil, estado, clasificación, oficina, responsable y datos para corrección | **Sí**, para quien registra | Mantener costo al registrador; decidir quién puede editar un activo ya registrado, cambiar código y desaprobar. Son acciones distintas. |
| Registro Activos Pendientes | Los mismos datos, costo de cada bien, costo total y estado de sincronización | **Sí**, para quien completa o aprueba | Mantener costo al encargado; separar completar, aprobar y cancelar. |
| Transferencia de Activos | Código, descripción, oficina y responsable de origen y destino | No se identifica como necesario para mover la custodia | Valorar ocultar costo y otros datos financieros en consultas auxiliares usadas por esta pantalla, manteniendo la identificación y ubicación del bien. |
| Asignar Activos | Bienes del responsable, descripción, costo, vida útil y destino | No se identifica como necesario para reasignar custodia | Valorar una vista operativa sin costo y una vista financiera autorizada. |
| Buscar / Filtrar Activos | Código, descripción, responsable, oficina, costo, vida útil, fecha de adquisición y estado; exportación con costo | Solo si el usuario tiene función de registro, valoración o control financiero | Valorar permiso separado **ver costos**, sin quitar la búsqueda operativa. |
| Seguimiento de Asignaciones | Responsable, oficina, costo por bien y total de acta; Excel y documentos | Depende de la función de revisión del acta | Valorar lectura operativa, lectura financiera y exportación por separado. |
| Seguimiento de Transferencias | Responsables y oficinas; el detalle incluye costo del activo | No se identifica como necesario para revisar el traslado | Valorar ocultar costo del detalle y del JSON para perfiles operativos. |
| Historial / ruta / control de faltantes | Cambios de ubicación y responsable, usuario que operó; en faltantes puede verse C.I. | En general no para consulta operativa | Valorar alcance por oficina o función; limitar C.I. y datos de auditoría a quienes los necesitan. |

**Otros datos a valorar:** nombre y C.I. del responsable, ubicación exacta, observaciones, documentos, usuario que registró o modificó, historial de movimientos y datos de sincronización. Su acceso puede necesitarse para una tarea concreta aunque no se necesite el costo.

## Acceso por cuenta en producción

Las opciones indicadas como **propias** están asignadas individualmente. Si una cuenta no tiene opciones propias, el sistema le aplica la **plantilla de su rol**. En APOYO, esa plantilla incluye consulta y seguimiento de asignaciones/transferencias, historial y ciertas funciones móviles; por ello `0 permisos propios` no significa `sin acceso`.

| Cuenta | Rol | Acceso relevante actual | Trabajo que consta en bitácoras | Valoración del costo |
|---|---|---|---|---|
| `admin2` | ADMINISTRADOR | Todos los módulos y capacidades | Gestión de usuarios; registros y cambios de activos; asignaciones y transferencia | Acceso administrativo amplio; mantener bajo responsabilidad definida. |
| `admin1` | SUPER USUARIO | Plantilla: operaciones y consultas de activos, sin administración del sistema | Sin acciones en las bitácoras consultadas | Acceso amplio por plantilla; confirmar si la cuenta se utiliza. |
| `saul` | SUPER USUARIO | Propios: registro, pendientes, transferir, asignar, consultas, historial, control y permisos finos de edición/aprobación | Registro y aprobación de activos; asignaciones y transferencias numerosas | **Necesario** para registro y pendientes; revisar por separado el resto de capacidades de alto impacto. |
| `vero` | SUPER USUARIO | Propios: registro, pendientes, transferir, asignar, consultas, catálogos, control y permisos finos | Sin acciones en las bitácoras consultadas; tiene ingreso registrado | **Necesario si realiza registro o pendientes**; confirmar función efectiva. |
| `cesar` | APOYO | Propios: **registro**, transferir, seguimiento de asignaciones/transferencias, consulta, oficinas y responsables | Asignaciones y transferencias; alta de responsables | **Necesario si registra activos**. No tiene opción visible de pendientes, pero el permiso de registro cubre rutas bajo `/administracion/activo`. |
| `yesica` | APOYO | Propios: **registro y pendientes**, transferir, seguimiento de asignaciones/transferencias, oficinas y responsables; cambiar código | Un evento de asignación | **Necesario** para registro y pendientes; valorar por separado el cambio urgente de código. |
| `prueba` | APOYO | Propios: **registro y pendientes**, transferir, asignar, consultas e historiales | Ingresos registrados; sin acciones operativas en bitácoras consultadas | **Necesario si es cuenta registradora**; confirmar si la cuenta de prueba debe conservar acceso de producción. |
| `bertha` | APOYO | Propios: transferir, asignar, seguimiento de asignaciones/transferencias y consulta | Transferencias interna y externa | Costo visible en consulta y asignación; **valorar restricción** si su función es solo movimiento. |
| `carmen` | APOYO | Propios: transferir, seguimiento de transferencias, consulta, oficinas y responsables | Transferencias internas y externas | Costo visible en consulta y detalle de transferencia; **valorar restricción**. |
| `hector` | APOYO | Propios: transferir, seguimiento de transferencias, consulta, oficinas y responsables | Transferencias internas | Costo visible en consulta y detalle de transferencia; **valorar restricción**. |
| `mayko` | APOYO | Propios: transferir, seguimiento de transferencias, consulta, oficinas y responsables | Transferencias internas y externas | Costo visible en consulta y detalle de transferencia; **valorar restricción**. |
| `mario` | APOYO | Propios: transferir, seguimiento de transferencias, consulta, oficinas y responsables | Sin acciones en bitácoras consultadas | Costo visible en consulta y detalle de transferencia; confirmar necesidad. |
| `axel2026` | APOYO | Plantilla: consulta, seguimiento, historial y móvil de campo | Sin ingresos ni acciones en bitácoras consultadas | Costo visible por la plantilla de consulta/seguimiento; confirmar necesidad. |
| `usuario1` | APOYO | Misma plantilla APOYO | Sin ingresos ni acciones en bitácoras consultadas | Costo visible por plantilla; confirmar uso de la cuenta. |
| `usuario2` | APOYO | Misma plantilla APOYO | Sin ingresos ni acciones en bitácoras consultadas | Costo visible por plantilla; confirmar uso de la cuenta. |
| `usuario3` | APOYO | Misma plantilla APOYO | Sin ingresos ni acciones en bitácoras consultadas | Costo visible por plantilla; confirmar uso de la cuenta. |
| `usuario4` | APOYO | Misma plantilla APOYO | Sin ingresos ni acciones en bitácoras consultadas | Costo visible por plantilla; confirmar uso de la cuenta. |
| `usuario5` | APOYO | Misma plantilla APOYO | Sin ingresos ni acciones en bitácoras consultadas | Costo visible por plantilla; confirmar uso de la cuenta. |
| `123qwe` | RECEPCION | Plantilla: hojas de ruta | Ingresos registrados; sin acciones sobre activos en bitácoras consultadas | El menú no da costo; revisar las APIs compartidas antes de afirmar que no puede obtenerlo. |

**Cómo leer la actividad:** `historial_activo` registra eventos por bien. Por ejemplo, una operación masiva puede producir muchos eventos; los números no representan actas ni sesiones. Los registros de acceso y de actividad tienen cobertura temporal distinta. La ausencia de un evento no demuestra que la persona nunca haya usado un módulo.

## Casos que requieren decisión

1. **Registradores:** confirmar que `cesar`, `yesica`, `prueba`, `saul` y `vero` efectivamente registran o completan pendientes. Quien tenga esa función conservaría costo en esos flujos.
2. **Personal de transferencias:** decidir si `bertha`, `carmen`, `hector`, `mayko` y `mario` necesitan costo en consulta, asignación o detalle de transferencias. Sus tareas registradas se relacionan sobre todo con movimientos.
3. **Plantilla APOYO:** decidir si toda cuenta APOYO debe recibir consulta y seguimiento con costo por defecto. Esto afecta también a cuentas sin permisos propios.
4. **Cuentas sin uso comprobado:** verificar la finalidad de `admin1`, `axel2026`, `mario`, `prueba` y `usuario1` a `usuario5` antes de cambiar sus accesos.
5. **Alcance de consulta:** decidir si cada perfil ve activos de toda la institución, de oficinas asignadas, de su responsable o solo los que registró.
6. **Exportaciones y documentos:** decidir por separado si se permite descargar listados y actas que incluyen costos o datos personales.

## Límites de los permisos actuales

- `opcion_activo` cubre el prefijo `/administracion/activo`, que incluye rutas de pendientes y otras operaciones. Las opciones visibles del menú no delimitan de forma exacta las acciones disponibles.
- El interceptor de menú deja pasar rutas sin correspondencia en el catálogo y peticiones sin información de permisos en sesión; solo cubre `/administracion/**`.
- `/api/activos/por-responsable` devuelve costo y vida útil por activo; `/api/responsables/card` devuelve C.I.; `/api/activos/{codigo}/ficha` devuelve responsable y ubicación. Las rutas `/api/**` están marcadas como `permitAll()` en la configuración web. Esto exige controles específicos de servidor antes de confiar en restricciones visuales.
- La tabla de consulta de activos devuelve costo dentro del JSON, y la exportación lo incluye. Ocultar la columna en HTML dejaría el dato en la respuesta.

## Referencias técnicas

- Permisos y plantillas: `model/ServiceImpl/OpcionMenuServiceImpl.java`; catálogo: `config/OpcionMenuSeeder.java`; interceptor: `config/PermisoOpcionInterceptor.java`.
- Registro, pendientes y tabla de consulta: `controller/activo/ActivosController.java`; plantillas `activo/formulario.html`, `activo/vista_pendientes.html`, `activo/tabla_registros_pendientes.html`, `consulta/activos.html`.
- Asignaciones y transferencias: `controller/Seguimieto/CAsignacionActivoController.java`, `controller/Seguimieto/CTransferenciaActivoController.java`; plantillas de `seguimiento/`.
- APIs compartidas: `controller/rest/CatalogoRestController.java`, `controller/rest/ActivoConsultaApiController.java`; seguridad: `config/SeguridadConfig.java`.
- Consulta reproducible de usuarios y actividad: `scripts/sql/auditoria_usuarios_roles_actividad.sql`.
