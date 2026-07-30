package com.usic.SistemasActivosFijosUAP.config;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.usic.SistemasActivosFijosUAP.model.dao.IOpcionMenuDao;
import com.usic.SistemasActivosFijosUAP.model.entity.OpcionMenu;

/**
 * Siembra el catálogo {@code opcion_menu} como árbol SECCION → GRUPO → ITEM.
 *
 * Upsert idempotente por {@code codigo}: en cada arranque crea los nodos nuevos
 * y sincroniza los metadatos de los existentes (preservando idOpcion y los
 * enlaces usuario_opcion). Es la fuente de verdad del menú hasta que exista el
 * CRUD de gestión. Los ITEM conservan su código "opcion_xxx" original.
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
    };

    // { codigo, padreSeccion, descripcion, icono, color }
    private static final String[][] GRUPOS = {
        { "grp_usuarios",      "sec_admin",       "Usuarios y Accesos",       "ti ti-users-group",            "purple" },
        { "grp_comunicacion",  "sec_admin",       "Comunicación",             "ti ti-mail",                   "cyan" },
        { "grp_contable",      "sec_catalogos",   "Clasificación Contable",   "ti ti-adjustments-horizontal", "teal" },
        { "grp_geo",           "sec_catalogos",   "Ámbito Geográfico",        "ti ti-map-2",                  "blue" },
        { "grp_adminactivos",  "sec_operaciones", "Administración de Activos", "ti ti-packages",              "green" },
        { "grp_transfer",      "sec_operaciones", "Transferencia de Activos", "ti ti-arrows-exchange",        "green" },
        { "grp_asignar",       "sec_operaciones", "Asignación de Activos",    "ti ti-clipboard-plus",         "blue" },
        { "grp_bajasing",      "sec_operaciones", "Bajas e Ingresos",         "ti ti-box-multiple",           "orange" },
        { "grp_hojaruta",      "sec_hojaruta",    "Hojas de Ruta",            "ti ti-file-description",       "orange" },
        { "grp_movimientos",   "sec_seguimiento", "Movimientos",              "ti ti-route",                  "orange" },
        { "grp_historial",     "sec_seguimiento", "Historial",                "ti ti-history",                "blue" },
        { "grp_consulta",      "sec_reportes",    "Consulta de Activos",      "ti ti-zoom-scan",              "blue" },
        { "grp_conciliacion",  "sec_reportes",    "Conciliación VSIAF",       "ti ti-git-compare",            "blue" },
    };

    // { codigo, padreGrupo, descripcion, icono, color, url, rutaBase, badge }
    private static final String[][] ITEMS = {
        { "opcion_rol",            "grp_usuarios",     "Rol",                          "ti ti-shield-lock",      "purple", "/administracion/rol/vista",                          "/administracion/rol",                   "" },
        { "opcion_persona",        "grp_usuarios",     "Persona",                      "ti ti-id-badge-2",       "blue",   "/administracion/persona/vista",                      "/administracion/persona",               "" },
        { "opcion_usuario",        "grp_usuarios",     "Usuario",                      "ti ti-user-cog",         "cyan",   "/administracion/usuario/vista",                      "/administracion/usuario",               "" },
        { "opcion_responsable",    "grp_usuarios",     "Responsables",                 "ti ti-user-check",       "teal",   "/administracion/responsable/vista",                  "/administracion/responsable",           "" },
        { "opcion_menu_admin",     "grp_usuarios",     "Gestión de Menú",              "ti ti-menu-2",           "purple", "/administracion/menu/vista",                         "/administracion/menu",                  "" },

        { "opcion_comunicados",    "grp_comunicacion", "Comunicados",                  "ti ti-send",             "cyan",   "/administracion/comunicados/vista",                  "/administracion/comunicados",           "" },

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

        { "opcion_trInterna",      "grp_transfer",     "Transferencia interna",        "ti ti-building",         "green",  "/administracion/trasnferencia/trasnferenciaInterna", "/administracion/trasnferencia/trasnferenciaInterna", "" },
        { "opcion_trExterna",      "grp_transfer",     "Transferencia externa",        "ti ti-truck-delivery",   "amber",  "/administracion/trasnferencia/trasnferenciaExterna", "/administracion/trasnferencia/trasnferenciaExterna", "" },
        { "opcion_trLondra",       "grp_transfer",     "Transferencia Londra",         "ti ti-truck-delivery",   "amber",  "/administracion/transferenciasLondra/vista",         "/administracion/transferenciasLondra",  "" },

        { "opcion_ActivoAj",       "grp_asignar",      "Asignar Activos",              "ti ti-timeline",         "blue",   "/administracion/asignar/asignacionActivo",           "/administracion/asignar",               "" },

        { "opcion_baja_modulo",    "grp_bajasing",     "Baja de Activos",              "ti ti-trash",            "red",    "/administracion/baja/modulo",                        "/administracion/baja/modulo",           "" },
        { "opcion_ingreso_modulo", "grp_bajasing",     "Ingreso de Bienes Ajenos",     "ti ti-package-import",   "green",  "/administracion/ingreso/modulo",                     "/administracion/ingreso/modulo",        "" },

        { "opcion_hr_seguimiento", "grp_hojaruta",     "Búsqueda y Seguimiento",       "ti ti-zoom-check",       "blue",   "/administracion/hoja-ruta/seguimiento",              "/administracion/hoja-ruta",             "" },

        { "opcion_aan",            "grp_movimientos",  "Asignaciones",                 "ti ti-clipboard-check",  "green",  "/administracion/asignacion/vista",                   "/administracion/asignacion",            "" },
        { "opcion_ta",             "grp_movimientos",  "Transferencias",               "ti ti-arrows-exchange-2","blue",   "/administracion/transferencia/vista",                "/administracion/transferencia",         "" },
        { "opcion_ActivoIngreso",  "grp_movimientos",  "Ingresos",                     "ti ti-arrow-down-circle","orange", "/administracion/ingreso/vista",                      "/administracion/ingreso/vista",         "" },
        { "opcion_ba",             "grp_movimientos",  "Bajas",                        "ti ti-trash",            "red",    "/administracion/baja/modulo",                        "/administracion/baja/modulo",           "" },

        { "opcion_historialA",     "grp_historial",    "Historial Activo",             "ti ti-timeline",         "blue",   "/administracion/historial/vista",                    "/administracion/historial",             "" },

        { "opcion_consulta_activo","grp_consulta",     "Buscar / Filtrar Activos",     "ti ti-search",           "blue",   "/administracion/consulta/activos/vista",             "/administracion/consulta",              "" },
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
    };

    @Bean
    ApplicationRunner initOpcionesMenu(IOpcionMenuDao dao) {
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

            int total = 0;

            // 1) Secciones (padre null)
            for (int i = 0; i < SECCIONES.length; i++) {
                String[] s = SECCIONES[i];
                OpcionMenu o = obtener(dao, s[0]);
                o.setTipo(TIPO_SECCION);
                o.setDescripcion(s[1]);
                o.setOrden(i + 1);
                o.setPadre(null);
                o.setSeccion(null);
                o.setGrupo(null);
                o.setIcono(null);
                o.setColorClase(null);
                o.setUrl(null);
                o.setRutaBase(null);
                o.setBadge(null);
                dao.save(o);
                total++;
            }

            // 2) Grupos (padre = sección)
            for (int i = 0; i < GRUPOS.length; i++) {
                String[] g = GRUPOS[i];
                OpcionMenu o = obtener(dao, g[0]);
                o.setTipo(TIPO_GRUPO);
                o.setPadre(dao.findByCodigo(g[1]));
                o.setDescripcion(g[2]);
                o.setIcono(g[3]);
                o.setColorClase(g[4]);
                o.setOrden(i + 1);
                o.setSeccion(descSeccion.get(g[1]));
                o.setGrupo(null);
                o.setUrl(null);
                o.setRutaBase(null);
                o.setBadge(null);
                dao.save(o);
                total++;
            }

            // 3) Ítems (padre = grupo)
            for (int i = 0; i < ITEMS.length; i++) {
                String[] it = ITEMS[i];
                OpcionMenu o = obtener(dao, it[0]);
                o.setTipo(TIPO_ITEM);
                o.setPadre(dao.findByCodigo(it[1]));
                o.setDescripcion(it[2]);
                o.setIcono(it[3]);
                o.setColorClase(it[4]);
                o.setUrl(it[5]);
                o.setRutaBase(it[6]);
                o.setBadge(vacioANulo(it[7]));
                o.setOrden(i + 1);
                o.setSeccion(descSeccion.get(seccionDeGrupo.get(it[1])));
                o.setGrupo(descGrupo.get(it[1]));
                dao.save(o);
                total++;
            }

            // 4) Permisos puros (ITEM oculto: asignable en permisos, NO en el sidebar)
            for (int i = 0; i < PERMISOS.length; i++) {
                String[] p = PERMISOS[i];
                OpcionMenu o = obtener(dao, p[0]);
                o.setTipo(TIPO_ITEM);
                o.setPadre(dao.findByCodigo(p[1]));
                o.setDescripcion(p[2]);
                o.setIcono(p[3]);
                o.setColorClase(p[4]);
                o.setUrl(null);
                o.setRutaBase(null);
                o.setBadge(null);
                o.setOrden(900 + i); // al final de sus hermanos del grupo
                o.setSeccion(descSeccion.get(seccionDeGrupo.get(p[1])));
                o.setGrupo(descGrupo.get(p[1]));
                o.setVisible(false); // permiso puro: oculto en el sidebar, asignable en permisos
                dao.save(o);
                total++;
            }

            logger.info("Catálogo opcion_menu sincronizado: {} secciones, {} grupos, {} ítems, {} permisos ({} nodos).",
                    SECCIONES.length, GRUPOS.length, ITEMS.length, PERMISOS.length, total);
        };
    }

    /** Busca el nodo por código o crea uno nuevo ACTIVO; siempre visible. */
    private OpcionMenu obtener(IOpcionMenuDao dao, String codigo) {
        OpcionMenu o = dao.findByCodigo(codigo);
        if (o == null) {
            o = new OpcionMenu();
            o.setCodigo(codigo);
            o.setEstado("ACTIVO");
        }
        o.setVisible(true);
        return o;
    }

    private String vacioANulo(String s) {
        return (s == null || s.isEmpty()) ? null : s;
    }
}
