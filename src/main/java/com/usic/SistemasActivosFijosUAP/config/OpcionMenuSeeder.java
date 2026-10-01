package com.usic.SistemasActivosFijosUAP.config;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.usic.SistemasActivosFijosUAP.model.dao.IOpcionMenuDao;
import com.usic.SistemasActivosFijosUAP.model.dao.IUsuarioDao;
import com.usic.SistemasActivosFijosUAP.model.entity.HistorialPermisoUsuario;
import com.usic.SistemasActivosFijosUAP.model.entity.OpcionMenu;
import com.usic.SistemasActivosFijosUAP.model.service.seguridad.AuditoriaPermisosService;

/**
 * Siembra el catálogo {@code opcion_menu} como árbol SECCION → GRUPO → ITEM.
 *
 * <p><b>Solo crea lo que falta</b>, por {@code codigo}. Lo que ya existe no se toca:
 * desde que hay pantalla de Gestión de Menú, el nombre, ícono, color, orden, ubicación,
 * visibilidad y bloqueo de cada opción los decide el administrador ahí, y antes este
 * seeder los pisaba en cada reinicio. Tampoco vuelve a crear lo que se eliminó (el
 * borrado es lógico: la fila queda con estado ELIMINADO).
 *
 * <p>Para agregar un módulo nuevo desde el código: sumar su fila a {@link #ITEMS} (o a
 * {@link #PERMISOS} si es una capacidad sin pantalla). Aparece en el próximo arranque al
 * final de su grupo, y los usuarios conectados lo ven sin volver a entrar. Para cambiar
 * uno que ya existe, usar Gestión de Menú.
 */
@Configuration
public class OpcionMenuSeeder {

    private static final Logger logger = LoggerFactory.getLogger(OpcionMenuSeeder.class);

    private static final String TIPO_SECCION = "SECCION";
    private static final String TIPO_GRUPO = "GRUPO";
    private static final String TIPO_ITEM = "ITEM";

    // { codigo, descripcion }
    private static final String[][] SECCIONES = {
        { "sec_admin",        "Administración del Sistema" },
        { "sec_catalogos",    "Catálogos" },
        { "sec_operaciones",  "Operaciones de Activos" },
        { "sec_hojaruta",     "Hojas de Ruta" },
        { "sec_seguimiento",  "Seguimiento y Consultas" },
        { "sec_reportes",     "Reportes y Consultas" },
        { "sec_movil",        "Aplicación Móvil" },
    };

    // { codigo, padreSeccion, descripcion, icono, color }
    private static final String[][] GRUPOS = {
        { "grp_usuarios",      "sec_admin",       "Usuarios y Accesos",       "ti ti-users-group",            "purple" },
        { "grp_comunicacion",  "sec_admin",       "Comunicación",             "ti ti-mail",                   "cyan" },
        { "grp_supervision",   "sec_admin",       "Supervisión",              "ti ti-eye-check",              "red" },
        { "grp_contable",      "sec_catalogos",   "Clasificación Contable",   "ti ti-adjustments-horizontal", "teal" },
        { "grp_geo",           "sec_catalogos",   "Ámbito Geográfico",        "ti ti-map-2",                  "blue" },
        { "grp_adminactivos",  "sec_operaciones", "Administración de Activos", "ti ti-packages",              "green" },
        { "grp_transfer",      "sec_operaciones", "Transferencia de Activos", "ti ti-arrows-exchange",        "green" },
        { "grp_asignar",       "sec_operaciones", "Asignación de Activos",    "ti ti-clipboard-plus",         "blue" },
        { "grp_bajasing",      "sec_operaciones", "Bajas e Ingresos",         "ti ti-box-multiple",           "orange" },
        { "grp_hojaruta",      "sec_hojaruta",    "Hojas de Ruta",            "ti ti-file-description",       "orange" },
        { "grp_control",       "sec_seguimiento", "Control por Responsable",  "ti ti-map-search",             "red" },
        { "grp_movimientos",   "sec_seguimiento", "Movimientos",              "ti ti-route",                  "orange" },
        { "grp_historial",     "sec_seguimiento", "Historial",                "ti ti-history",                "blue" },
        { "grp_consulta",      "sec_reportes",    "Consulta de Activos",      "ti ti-zoom-scan",              "blue" },
        { "grp_conciliacion",  "sec_reportes",    "Conciliación VSIAF",       "ti ti-git-compare",            "blue" },
        { "grp_movil",         "sec_movil",       "SCIAF Móvil",              "ti ti-device-mobile",          "blue" },
    };

    // { codigo, padreGrupo, descripcion, icono, color, url, rutaBase, badge }
    private static final String[][] ITEMS = {
        { "opcion_rol",            "grp_usuarios",     "Rol",                          "ti ti-shield-lock",      "purple", "/administracion/rol/vista",                          "/administracion/rol",                   "" },
        { "opcion_persona",        "grp_usuarios",     "Persona",                      "ti ti-id-badge-2",       "blue",   "/administracion/persona/vista",                      "/administracion/persona",               "" },
        { "opcion_usuario",        "grp_usuarios",     "Usuario",                      "ti ti-user-cog",         "cyan",   "/administracion/usuario/vista",                      "/administracion/usuario",               "" },
        { "opcion_responsable",    "grp_usuarios",     "Responsables",                 "ti ti-user-check",       "teal",   "/administracion/responsable/vista",                  "/administracion/responsable",           "" },
        { "opcion_menu_admin",     "grp_usuarios",     "Gestión de Menú",              "ti ti-menu-2",           "purple", "/administracion/menu/vista",                         "/administracion/menu",                  "" },

        { "opcion_comunicados",    "grp_comunicacion", "Comunicados",                  "ti ti-send",             "cyan",   "/administracion/comunicados/vista",                  "/administracion/comunicados",           "" },

        // Solo ADMINISTRADOR / SUPER USUARIO (los controladores lo vuelven a revisar por rol).
        { "opcion_actividad",      "grp_supervision",  "Monitoreo de actividad",       "ti ti-activity",         "red",    "/administracion/actividad/vista",                    "/administracion/actividad",             "" },
        { "opcion_autorizaciones", "grp_supervision",  "Autorizaciones",               "ti ti-shield-check",     "amber",  "/administracion/autorizaciones/vista",               "/administracion/autorizaciones",        "" },

        { "opcion_contable",       "grp_contable",     "Grupo Contable",               "ti ti-category-2",       "teal",   "/administracion/grupoc/vista",                       "/administracion/grupoc",                "" },
        { "opcion_auxiliar",       "grp_contable",     "Auxiliar",                     "ti ti-folders",          "blue",   "/administracion/auxiliar/vista",                     "/administracion/auxiliar",              "" },
        { "opcion_of",             "grp_contable",     "Organismo Financiador",        "ti ti-building-bank",    "amber",  "/administracion/organismo/vista",                    "/administracion/organismo",             "" },
        { "opcion_estadoA",        "grp_contable",     "Estado del Activo",            "ti ti-badge",            "green",  "/administracion/estadoa/vista",                      "/administracion/estadoa",               "" },
        { "opcion_responsable_entrega", "grp_contable", "Responsable de Entrega",       "ti ti-user-check",       "cyan",   "/administracion/responsable-entrega/vista",           "/administracion/responsable-entrega",    "" },

        { "opcion_entidad",        "grp_geo",          "Entidad",                      "ti ti-building-community","blue",   "/administracion/entidad/vista",                      "/administracion/entidad",               "" },
        { "opcion_municipio",      "grp_geo",          "Municipio",                    "ti ti-map-pin",          "cyan",   "/administracion/municipio/vista",                    "/administracion/municipio",             "" },
        { "opcion_predio",         "grp_geo",          "Predio",                       "ti ti-home-2",           "teal",   "/administracion/predio/vista",                       "/administracion/predio",                "" },
        { "opcion_oficina",        "grp_geo",          "Oficinas",                     "ti ti-door",             "purple", "/administracion/oficina/vista",                      "/administracion/oficina",               "" },

        { "opcion_activo",         "grp_adminactivos", "Registro Activos",             "ti ti-clipboard-list",   "green",  "/administracion/activo/vista",                       "/administracion/activo",                "" },
        { "opcion_activop",        "grp_adminactivos", "Registro Activos Pendientes",  "ti ti-clock-exclamation","amber",  "/administracion/activo/vistap",                      "/administracion/activo/vistap",         "PEND." },

        { "opcion_transferencia",  "grp_transfer",     "Transferencia de Activos",     "ti ti-arrows-exchange",  "green",  "/administracion/trasnferencia/transferencia",        "/administracion/trasnferencia/transferencia", "" },
        // Interna y externa se unificaron en la opción de arriba; estos dos se retiran al
        // arrancar y sus usuarios pasan a la nueva (ver retirarTransferenciasViejas).
        { "opcion_trInterna",      "grp_transfer",     "Transferencia interna",        "ti ti-building",         "green",  "/administracion/trasnferencia/trasnferenciaInterna", "/administracion/trasnferencia/trasnferenciaInterna", "" },
        { "opcion_trExterna",      "grp_transfer",     "Transferencia externa",        "ti ti-truck-delivery",   "amber",  "/administracion/trasnferencia/trasnferenciaExterna", "/administracion/trasnferencia/trasnferenciaExterna", "" },
        { "opcion_trLondra",       "grp_transfer",     "Transferencia Londra",         "ti ti-truck-delivery",   "amber",  "/administracion/transferenciasLondra/vista",         "/administracion/transferenciasLondra",  "" },

        { "opcion_ActivoAj",       "grp_asignar",      "Asignar Activos",              "ti ti-timeline",         "blue",   "/administracion/asignar/asignacionActivo",           "/administracion/asignar",               "" },

        { "opcion_baja_modulo",    "grp_bajasing",     "Baja de Activos",              "ti ti-trash",            "red",    "/administracion/baja/modulo",                        "/administracion/baja/modulo",           "" },
        { "opcion_ingreso_modulo", "grp_bajasing",     "Ingreso de Bienes Ajenos",     "ti ti-package-import",   "green",  "/administracion/ingreso/modulo",                     "/administracion/ingreso/modulo",        "" },

        { "opcion_hr_seguimiento", "grp_hojaruta",     "Búsqueda y Seguimiento",       "ti ti-zoom-check",       "blue",   "/administracion/hoja-ruta/seguimiento",              "/administracion/hoja-ruta",             "" },

        { "opcion_control_mapa",      "grp_control",   "Mapa de Control",              "ti ti-layout-grid",      "red",    "/administracion/control-activos/vista",              "/administracion/control-activos",       "" },
        { "opcion_control_faltantes", "grp_control",   "Faltantes",                    "ti ti-alert-triangle",   "red",    "/administracion/control-activos/faltantes/vista",    "/administracion/control-activos/faltantes", "" },

        { "opcion_aan",            "grp_movimientos",  "Asignaciones",                 "ti ti-clipboard-check",  "green",  "/administracion/asignacion/vista",                   "/administracion/asignacion",            "" },
        { "opcion_ta",             "grp_movimientos",  "Transferencias",               "ti ti-arrows-exchange-2","blue",   "/administracion/transferencia/vista",                "/administracion/transferencia",         "" },
        { "opcion_ActivoIngreso",  "grp_movimientos",  "Ingresos",                     "ti ti-arrow-down-circle","orange", "/administracion/ingreso/vista",                      "/administracion/ingreso/vista",         "" },
        { "opcion_ba",             "grp_movimientos",  "Bajas",                        "ti ti-trash",            "red",    "/administracion/baja/modulo",                        "/administracion/baja/modulo",           "" },

        { "opcion_historialA",     "grp_historial",    "Historial Activo",             "ti ti-timeline",         "blue",   "/administracion/historial/vista",                    "/administracion/historial",             "" },
        { "opcion_ruta_activo",    "grp_historial",    "Seguimiento de Activo (ruta)", "ti ti-route",            "red",    "/administracion/ruta-activo/vista",                  "/administracion/ruta-activo",           "" },
        { "opcion_trHistorial",    "grp_historial",    "Historial de Transferencias",  "ti ti-refresh-dot",      "amber",  "/administracion/activo/transferencias/historial/vista", "/administracion/activo/transferencias/historial", "" },

        { "opcion_consulta_activo","grp_consulta",     "Buscar / Filtrar Activos",     "ti ti-search",           "blue",   "/administracion/consulta/activos/vista",             "/administracion/consulta",              "" },
        { "opcion_reporte_asignaciones", "grp_consulta", "Reporte de Asignaciones (Excel)", "ti ti-file-spreadsheet", "green", "/reportes/asignaciones/vista",              "/reportes/asignaciones",                "" },
        { "opcion_conciliacion",   "grp_conciliacion", "BD ↔ VSIAF (divergencias)",    "ti ti-arrows-diff",      "blue",   "/administracion/conciliacion/vista",                 "/administracion/conciliacion",          "" },
        { "opcion_correlativo",    "grp_conciliacion", "Revisión de correlativos",     "ti ti-list-numbers",     "teal",   "/administracion/correlativo/vista",                  "/administracion/correlativo",           "" },
    };

    /**
     * Permisos puros (capacidades), NO navegables. Se modelan como ITEM oculto
     * ({@code visible=false}): aparecen como casilla asignable en la pantalla de
     * permisos por usuario, pero NO se renderizan en el sidebar ni tienen URL.
     * Un SUPER USUARIO/ADMINISTRADOR los otorga a un usuario (p. ej. de rol APOYO)
     * para habilitarle una acción sensible puntual.
     *
     * { codigo, padreGrupo, descripcion, icono, color }
     */
    private static final String[][] PERMISOS = {
        { "opcion_activo_editar_codigo", "grp_adminactivos", "Editar código de activo (urgente)", "ti ti-barcode-off", "red" },
        { "opcion_activo_editar",        "grp_adminactivos", "Editar activo",                     "ti ti-pencil",      "amber" },
        { "opcion_activo_desaprobar",    "grp_adminactivos", "Desaprobar activo (VSIAF)",          "ti ti-arrow-down-circle", "red" },

        // Cerrar un faltante es la acción sensible del módulo de control: da por
        // zanjado un bien que no apareció. Mirar el mapa no la requiere.
        { "opcion_control_resolver",     "grp_control",      "Resolver / justificar faltantes",   "ti ti-checkup-list", "red" },

        // Permisos de la app móvil (SCIAF Móvil). Se administran desde la misma
        // pantalla de permisos por usuario que el resto: no hay un sistema de
        // permisos aparte para el móvil.
        { "MOV_ACCESO",             "grp_movil", "Móvil · Ingresar a la aplicación",   "ti ti-device-mobile",   "blue"  },
        { "MOV_ESCANER",            "grp_movil", "Móvil · Escanear y consultar",       "ti ti-qrcode",          "blue"  },
        { "MOV_BUSQUEDA",           "grp_movil", "Móvil · Buscar y explorar",          "ti ti-search",          "cyan"  },
        { "MOV_INFORME",            "grp_movil", "Móvil · Emitir informes",            "ti ti-file-text",       "teal"  },
        { "MOV_INVENTARIO",         "grp_movil", "Móvil · Toma de inventario",         "ti ti-clipboard-check", "green" },
        { "MOV_ASIGNACIONES",       "grp_movil", "Móvil · Ver asignaciones",           "ti ti-clipboard-list",  "blue"  },
        { "MOV_ASIGNACIONES_SUBIR", "grp_movil", "Móvil · Subir asignaciones al VSIAF", "ti ti-cloud-upload",   "red"   },
        { "MOV_NOTIFICACIONES",     "grp_movil", "Móvil · Notificaciones del sistema", "ti ti-bell",            "amber" },
    };

    /**
     * Ítems cuya pantalla se unificó en "Transferencia de Activos" (opcion_transferencia).
     * Ver {@link #retirarTransferenciasViejas}.
     */
    private static final List<String> RETIRADOS_POR_TRANSFERENCIA = List.of(
        "opcion_trInterna", "opcion_trExterna"
    );

    @Bean
    ApplicationRunner initOpcionesMenu(IOpcionMenuDao dao, IUsuarioDao usuarioDao, AuditoriaPermisosService auditoria) {
        return args -> {
            // Mapas auxiliares para denormalizar seccion/grupo en los ITEM
            // (lo usa la pantalla de asignación de permisos).
            Map<String, String> descSeccion = new HashMap<>();
            Map<String, String> descGrupo = new HashMap<>();
            Map<String, String> seccionDeGrupo = new HashMap<>();
            for (String[] s : SECCIONES) {
                descSeccion.put(s[0], s[1]);
            }
            for (String[] g : GRUPOS) {
                descGrupo.put(g[0], g[2]);
                seccionDeGrupo.put(g[0], g[1]);
            }

            int creados = 0;

            // 1) Secciones (padre null)
            for (int i = 0; i < SECCIONES.length; i++) {
                String[] s = SECCIONES[i];
                OpcionMenu o = nuevo(dao, s[0]);
                if (o == null) continue;
                o.setTipo(TIPO_SECCION);
                o.setDescripcion(s[1]);
                o.setOrden(siguienteOrden(dao, null, TIPO_SECCION));
                dao.save(o);
                creados++;
            }

            // 2) Grupos (padre = sección)
            for (String[] g : GRUPOS) {
                OpcionMenu o = nuevo(dao, g[0]);
                if (o == null) continue;
                OpcionMenu padre = dao.findByCodigo(g[1]);
                o.setTipo(TIPO_GRUPO);
                o.setPadre(padre);
                o.setDescripcion(g[2]);
                o.setIcono(g[3]);
                o.setColorClase(g[4]);
                o.setOrden(siguienteOrden(dao, padre, TIPO_GRUPO));
                o.setSeccion(descSeccion.get(g[1]));
                dao.save(o);
                creados++;
            }

            // 3) Ítems (padre = grupo)
            for (String[] it : ITEMS) {
                OpcionMenu o = nuevo(dao, it[0]);
                if (o == null) continue;
                OpcionMenu padre = dao.findByCodigo(it[1]);
                o.setTipo(TIPO_ITEM);
                o.setPadre(padre);
                o.setDescripcion(it[2]);
                o.setIcono(it[3]);
                o.setColorClase(it[4]);
                o.setUrl(it[5]);
                o.setRutaBase(it[6]);
                o.setBadge(vacioANulo(it[7]));
                o.setOrden(siguienteOrden(dao, padre, TIPO_ITEM));
                o.setSeccion(descSeccion.get(seccionDeGrupo.get(it[1])));
                o.setGrupo(descGrupo.get(it[1]));
                dao.save(o);
                creados++;
            }

            // 4) Permisos puros (ITEM oculto: asignable en permisos, NO en el sidebar)
            for (int i = 0; i < PERMISOS.length; i++) {
                String[] p = PERMISOS[i];
                OpcionMenu o = nuevo(dao, p[0]);
                if (o == null) continue;
                o.setTipo(TIPO_ITEM);
                o.setPadre(dao.findByCodigo(p[1]));
                o.setDescripcion(p[2]);
                o.setIcono(p[3]);
                o.setColorClase(p[4]);
                o.setOrden(900 + i); // al final de sus hermanos del grupo
                o.setSeccion(descSeccion.get(seccionDeGrupo.get(p[1])));
                o.setGrupo(descGrupo.get(p[1]));
                o.setVisible(false); // permiso puro: oculto en el sidebar, asignable en permisos
                dao.save(o);
                creados++;
            }

            int retirados = retirarTransferenciasViejas(dao, usuarioDao, auditoria);

            logger.info("Catálogo opcion_menu: {} nodo(s) nuevo(s){}; lo existente se administra desde Gestión de Menú.",
                    creados, retirados > 0 ? ", " + retirados + " opción(es) vieja(s) retirada(s)" : "");
        };
    }

    /**
     * Interna y externa se unificaron en {@code opcion_transferencia}. Quien tenía alguna
     * de las dos pasa a tener la nueva, y las viejas se eliminan (lógicamente) para que
     * dejen de aparecer en la pantalla de permisos. Se hace una sola vez: después quedan
     * en estado ELIMINADO y este paso no las vuelve a tocar.
     */
    private int retirarTransferenciasViejas(IOpcionMenuDao dao, IUsuarioDao usuarioDao, AuditoriaPermisosService auditoria) {
        OpcionMenu nueva = dao.findByCodigo("opcion_transferencia");
        if (nueva == null || nueva.eliminado()) {
            return 0;
        }
        int retirados = 0;
        for (String codigo : RETIRADOS_POR_TRANSFERENCIA) {
            OpcionMenu vieja = dao.findByCodigo(codigo);
            if (vieja == null || vieja.eliminado()) continue;
            List<Long> usuarios = dao.usuariosConOpcion(vieja.getIdOpcion());
            for (Long idUsuario : usuarios) {
                java.util.Set<String> antes = new java.util.TreeSet<>(dao.findCodigosByUsuario(idUsuario));
                dao.asignarSiFalta(idUsuario, nueva.getIdOpcion());
                java.util.Set<String> despues = new java.util.TreeSet<>(antes);
                despues.remove(codigo);
                despues.add(nueva.getCodigo());
                usuarioDao.findById(idUsuario).ifPresent(u -> {
                    try {
                        auditoria.registrar(u, HistorialPermisoUsuario.MIGRACION, "Sistema (al arrancar)",
                                antes, despues, null, "Transferencia interna y externa se unificaron en «Transferencia de Activos»");
                    } catch (Exception e) {
                        logger.warn("No se pudo auditar la migración del usuario {}: {}", idUsuario, e.getMessage());
                    }
                });
            }
            dao.desvincularDeUsuarios(vieja.getIdOpcion());
            vieja.setEstado(OpcionMenu.ESTADO_ELIMINADO);
            vieja.setVisible(false);
            dao.save(vieja);
            logger.info("Opción {} retirada: {} usuario(s) pasan a opcion_transferencia.", codigo, usuarios.size());
            retirados++;
        }
        return retirados;
    }

    /**
     * Devuelve un nodo nuevo si el código no existe, o null si ya existe (en cualquier
     * estado, también ELIMINADO: lo que el administrador borró no se vuelve a crear).
     */
    private OpcionMenu nuevo(IOpcionMenuDao dao, String codigo) {
        if (dao.findByCodigo(codigo) != null) {
            return null;
        }
        OpcionMenu o = new OpcionMenu();
        o.setCodigo(codigo);
        o.setEstado(OpcionMenu.ESTADO_ACTIVO);
        o.setVisible(true);
        return o;
    }

    /** Al final de sus hermanos (sin contar los permisos puros, que van en 900+). */
    private int siguienteOrden(IOpcionMenuDao dao, OpcionMenu padre, String tipo) {
        List<OpcionMenu> hermanos = (padre == null)
                ? dao.findByPadreIsNullAndTipoOrderByOrdenAsc(tipo)
                : dao.findByPadre_IdOpcionAndTipoOrderByOrdenAsc(padre.getIdOpcion(), tipo);
        int max = 0;
        for (OpcionMenu h : hermanos) {
            if (h.getOrden() != null && h.getOrden() < 900 && h.getOrden() > max) max = h.getOrden();
        }
        return max + 1;
    }

    private String vacioANulo(String s) {
        return (s == null || s.isEmpty()) ? null : s;
    }
}
