package com.usic.SistemasActivosFijosUAP.model.ServiceImpl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.usic.SistemasActivosFijosUAP.model.IService.IOpcionMenuService;
import com.usic.SistemasActivosFijosUAP.model.dao.IOpcionMenuDao;
import com.usic.SistemasActivosFijosUAP.model.dao.IUsuarioDao;
import com.usic.SistemasActivosFijosUAP.model.dto.MenuNodoDto;
import com.usic.SistemasActivosFijosUAP.model.entity.HistorialPermisoUsuario;
import com.usic.SistemasActivosFijosUAP.model.entity.OpcionMenu;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;
import com.usic.SistemasActivosFijosUAP.model.service.seguridad.AuditoriaPermisosService;

@Service
public class OpcionMenuServiceImpl implements IOpcionMenuService {

    public static final String TIPO_SECCION = "SECCION";
    public static final String TIPO_GRUPO = "GRUPO";
    public static final String TIPO_ITEM = "ITEM";

    /**
     * Opciones que no se pueden ocultar, bloquear ni eliminar (ni sus grupos y secciones):
     * sin ellas el administrador se queda sin forma de deshacer el cambio desde la web.
     */
    public static final Set<String> CODIGOS_PROTEGIDOS = Set.of(
        "opcion_menu_admin", "opcion_usuario"
    );

    @Autowired
    private IOpcionMenuDao opcionMenuDao;

    @Autowired
    private IUsuarioDao usuarioDao;

    @Autowired
    private AuditoriaPermisosService auditoriaPermisos;

    /** Caché en memoria del árbol visible (estático; se invalida con limpiarCacheMenu). */
    private volatile List<MenuNodoDto> arbolCache;

    /** Caché de los ítems hoja vigentes (la usa el interceptor en cada request). */
    private volatile List<OpcionMenu> itemsCache;

    /** Caché de los códigos de ítem bloqueados (propio o heredado del grupo/sección). */
    private volatile Set<String> bloqueadosCache;

    /**
     * Opciones del bloque "Administración del Sistema". SUPER USUARIO ve todo lo
     * de activos pero NO estas (mismo criterio que el sidebar actual).
     */
    private static final Set<String> CODIGOS_ADMIN_SISTEMA = Set.of(
        "opcion_rol", "opcion_persona", "opcion_usuario", "opcion_responsable", "opcion_menu_admin"
    );

    /** Estas capacidades requieren una asignación individual; ningún rol las hereda. */
    private static final Set<String> CODIGOS_ASIGNACION_EXPLICITA = Set.of(
        "opcion_activo_ver_finanzas", "opcion_activop_subir_vsiaf"
    );

    /**
     * Transferencia interna y externa se unificaron en {@code opcion_transferencia}: quien
     * tuviera cualquiera de las dos sigue entrando, sin volver a asignarle permisos.
     */
    private static final Set<String> CODIGOS_TRANSFERENCIA_VIEJOS = Set.of(
        "opcion_trInterna", "opcion_trExterna"
    );

    /** Opciones de "Seguimiento y Consultas" + "Reportes" que ve APOYO. */
    private static final Set<String> CODIGOS_CONSULTA = Set.of(
        "opcion_aan", "opcion_ta", "opcion_ActivoIngreso", "opcion_ba",
        "opcion_historialA", "opcion_consulta_activo", "opcion_ruta_activo"
    );

    /**
     * Permisos de la app móvil que APOYO tiene por defecto.
     *
     * APOYO es el rol de campo: consulta, emite informes y levanta inventario,
     * pero no modifica el maestro de activos ni recibe el flujo de auditoría.
     * Quedan fuera a propósito {@code MOV_ASIGNACIONES_SUBIR} (escribe al VSIAF)
     * y {@code MOV_NOTIFICACIONES}. Un ADMINISTRADOR puede otorgarlos
     * individualmente desde la pantalla de permisos.
     */
    private static final Set<String> CODIGOS_MOVIL_APOYO = Set.of(
        "MOV_ACCESO", "MOV_ESCANER", "MOV_BUSQUEDA",
        "MOV_INFORME", "MOV_INVENTARIO", "MOV_ASIGNACIONES"
    );

    /**
     * Supervisión (Monitoreo de actividad y Autorizaciones): la ven siempre ADMINISTRADOR
     * y SUPER USUARIO, aunque el SUPER USUARIO tenga permisos asignados a mano.
     */
    private static final Set<String> CODIGOS_SUPERVISION = Set.of(
        "opcion_actividad", "opcion_autorizaciones", "opcion_conectados"
    );

    /** Opciones que ve RESPONSABLE por defecto (módulo de comunicados). */
    private static final Set<String> CODIGOS_RESPONSABLE = Set.of(
        "opcion_comunicados"
    );

    /**
     * Opción que habilita el módulo de Hojas de Ruta para RECEPCION. La página de
     * registro ({@code /administracion/hoja-ruta/vista}) es independiente (sin
     * sidebar), pero el {@code PermisoOpcionInterceptor} igual exige un permiso
     * cuyo {@code rutaBase} cubra {@code /administracion/hoja-ruta}. El ítem
     * {@code opcion_hr_seguimiento} tiene ese rutaBase y engloba todo el módulo
     * (registro + AJAX), así que basta otorgarlo para desbloquear a RECEPCION.
     */
    private static final Set<String> CODIGOS_RECEPCION = Set.of(
        "opcion_hr_seguimiento"
    );

    @Override
    public List<OpcionMenu> findAll() {
        return opcionMenuDao.findAll();
    }

    @Override
    public OpcionMenu findById(Long idEntidad) {
        return opcionMenuDao.findById(idEntidad).orElse(null);
    }

    @Override
    public OpcionMenu save(OpcionMenu entidad) {
        return opcionMenuDao.save(entidad);
    }

    @Override
    public void deleteById(Long idEntidad) {
        opcionMenuDao.deleteById(idEntidad);
    }

    @Override
    public List<OpcionMenu> listarTodas() {
        return opcionMenuDao.findAllByOrderByOrdenAsc();
    }

    @Override
    public List<OpcionMenu> listarItems() {
        List<OpcionMenu> local = itemsCache;
        if (local == null) {
            local = opcionMenuDao.findByTipoOrderByOrdenAsc(TIPO_ITEM).stream()
                    .filter(o -> !o.eliminado())
                    .toList();
            itemsCache = local;
        }
        return local;
    }

    @Override
    public Set<String> codigosBloqueados() {
        Set<String> local = bloqueadosCache;
        if (local == null) {
            Set<String> s = new HashSet<>();
            for (OpcionMenu o : opcionMenuDao.findAll()) {
                if (TIPO_ITEM.equals(o.getTipo()) && !o.eliminado() && bloqueadoEfectivo(o)) {
                    s.add(o.getCodigo());
                }
            }
            local = Set.copyOf(s);
            bloqueadosCache = local;
        }
        return local;
    }

    /** El nodo o alguno de sus ancestros está bloqueado. */
    private boolean bloqueadoEfectivo(OpcionMenu o) {
        for (OpcionMenu n = o; n != null; n = n.getPadre()) {
            if (n.bloqueado()) return true;
        }
        return false;
    }

    @Override
    public List<OpcionMenu> listarSecciones() {
        return opcionMenuDao.findByTipoOrderByOrdenAsc(TIPO_SECCION).stream().filter(o -> !o.eliminado()).toList();
    }

    @Override
    public List<OpcionMenu> listarGrupos() {
        return opcionMenuDao.findByTipoOrderByOrdenAsc(TIPO_GRUPO).stream().filter(o -> !o.eliminado()).toList();
    }

    @Override
    public List<OpcionMenu> listarEliminados() {
        return opcionMenuDao.findAll().stream()
                .filter(OpcionMenu::eliminado)
                .sorted(Comparator.comparing(o -> o.getModificacion() == null ? new java.util.Date(0) : o.getModificacion(),
                        Comparator.reverseOrder()))
                .toList();
    }

    @Override
    public List<OpcionMenu> buscarPorCodigos(Collection<String> codigos) {
        if (codigos == null || codigos.isEmpty()) {
            return List.of();
        }
        return opcionMenuDao.findByCodigoIn(codigos).stream().filter(o -> !o.eliminado()).toList();
    }

    @Override
    public Set<String> codigosPorUsuario(Long idUsuario) {
        return new HashSet<>(opcionMenuDao.findCodigosByUsuario(idUsuario));
    }

    @Override
    public Set<String> plantillaPorRol(String nombreRol) {
        String rol = nombreRol == null ? "" : nombreRol.toUpperCase();
        Set<String> todos = todosLosCodigos();
        todos.removeAll(CODIGOS_ASIGNACION_EXPLICITA);

        switch (rol) {
            case "ADMINISTRADOR":
                return todos;

            case "SUPER USUARIO":
                // Todo lo de activos, menos el bloque "Administración del Sistema".
                return todos.stream()
                        .filter(c -> !CODIGOS_ADMIN_SISTEMA.contains(c))
                        .collect(Collectors.toCollection(HashSet::new));

            case "APOYO":
                // (Supervisión queda fuera a propósito: es solo para ADMINISTRADOR / SUPER USUARIO.)
                // Consulta/seguimiento/reportes de la web + lo que le corresponde
                // en la app móvil.
                return todos.stream()
                        .filter(c -> CODIGOS_CONSULTA.contains(c) || CODIGOS_MOVIL_APOYO.contains(c))
                        .collect(Collectors.toCollection(HashSet::new));

            case "RESPONSABLE":
                // Por defecto solo el módulo de comunicados (envío/control de lecturas).
                return todos.stream()
                        .filter(CODIGOS_RESPONSABLE::contains)
                        .collect(Collectors.toCollection(HashSet::new));

            case "RECEPCION":
                // Registra hojas de ruta en /administracion/hoja-ruta/vista (página
                // independiente, sin sidebar). Solo necesita el permiso que cubre el
                // módulo para pasar el PermisoOpcionInterceptor.
                return todos.stream()
                        .filter(CODIGOS_RECEPCION::contains)
                        .collect(Collectors.toCollection(HashSet::new));

            default:
                return new HashSet<>();
        }
    }

    @Override
    public Set<String> opcionesEfectivas(Usuario usuario) {
        String rol = (usuario != null && usuario.getRol() != null && usuario.getRol().getNombre() != null)
                ? usuario.getRol().getNombre().toUpperCase()
                : "";

        // ADMINISTRADOR conserva el menú completo. Si tiene una selección propia,
        // los permisos puros (botones/acciones) sí siguen sus casillas: quitar
        // "Desaprobar activo" debe quitar también el botón y la autorización.
        if ("ADMINISTRADOR".equals(rol)) {
            Set<String> efectivos = todosLosCodigos();
            efectivos.removeAll(CODIGOS_ASIGNACION_EXPLICITA);
            if (usuario != null && usuario.getIdUsuario() != null) {
                Set<String> propios = codigosPorUsuario(usuario.getIdUsuario());
                if (!propios.isEmpty()) {
                    Set<String> puros = listarItems().stream()
                            .filter(o -> o.getUrl() == null || o.getUrl().isBlank())
                            .map(OpcionMenu::getCodigo)
                            .collect(Collectors.toSet());
                    efectivos.removeAll(puros);
                    propios.stream().filter(puros::contains).forEach(efectivos::add);
                }
            }
            return efectivos;
        }

        // Permisos asignados explícitamente.
        if (usuario != null && usuario.getIdUsuario() != null) {
            Set<String> asignados = codigosPorUsuario(usuario.getIdUsuario());
            if (!asignados.isEmpty()) {
                if ("SUPER USUARIO".equals(rol)) asignados.addAll(CODIGOS_SUPERVISION);
                if (asignados.stream().anyMatch(CODIGOS_TRANSFERENCIA_VIEJOS::contains)) {
                    asignados.add("opcion_transferencia");
                }
                return asignados;
            }
        }

        // Sin asignación → plantilla del rol (compatibilidad con el comportamiento previo).
        return plantillaPorRol(rol);
    }

    private Set<String> todosLosCodigos() {
        return listarItems().stream()
                .map(OpcionMenu::getCodigo)
                .collect(Collectors.toCollection(HashSet::new));
    }

    // ── Árbol del sidebar ───────────────────────────────────────────────────

    @Override
    public List<MenuNodoDto> obtenerMenuVisible(Set<String> opciones) {
        Set<String> permitidos = (opciones != null) ? opciones : Set.of();

        List<MenuNodoDto> visible = new ArrayList<>();
        for (MenuNodoDto seccion : obtenerArbolCompleto()) {
            List<MenuNodoDto> gruposVisibles = new ArrayList<>();
            for (MenuNodoDto grupo : seccion.getHijos()) {
                List<MenuNodoDto> itemsVisibles = new ArrayList<>();
                for (MenuNodoDto item : grupo.getHijos()) {
                    if (permitidos.contains(item.getCodigo())) {
                        itemsVisibles.add(item);
                    }
                }
                if (!itemsVisibles.isEmpty()) {
                    MenuNodoDto grupoCopia = copiarNodo(grupo);
                    grupoCopia.setHijos(itemsVisibles);
                    gruposVisibles.add(grupoCopia);
                }
            }
            if (!gruposVisibles.isEmpty()) {
                MenuNodoDto seccionCopia = copiarNodo(seccion);
                seccionCopia.setHijos(gruposVisibles);
                visible.add(seccionCopia);
            }
        }
        return visible;
    }

    @Override
    public List<MenuNodoDto> obtenerMenuVisibleCompleto() {
        // ADMIN ve todo el árbol visible vigente (no depende de session.opciones,
        // así un ítem recién creado aparece sin re-loguear).
        return obtenerArbolCompleto();
    }

    @Override
    public List<MenuNodoDto> obtenerArbolAdmin() {
        // Pantalla de gestión: incluye también los nodos ocultos (visible=false).
        List<MenuNodoDto> arbol = construirArbol(true);
        Map<Long, Long> conteo = usuariosPorOpcion();
        recorrer(arbol, n -> n.setUsuariosAsignados(conteo.getOrDefault(n.getIdOpcion(), 0L)));
        return arbol;
    }

    @Override
    public List<MenuNodoDto> obtenerArbolPermisos() {
        // Pantalla de permisos por usuario: todo lo vigente, visible u oculto (los
        // permisos puros son ítems ocultos), sin los eliminados.
        return construirArbol(true);
    }

    @Override
    public void limpiarCacheMenu() {
        arbolCache = null;
        itemsCache = null;
        bloqueadosCache = null;
    }

    private List<MenuNodoDto> obtenerArbolCompleto() {
        List<MenuNodoDto> local = arbolCache;
        if (local == null) {
            local = construirArbol(false);
            arbolCache = local;
        }
        return local;
    }

    /** Arma el árbol SECCION → GRUPO → ITEM; {@code incluirOcultos} para gestión. */
    private List<MenuNodoDto> construirArbol(boolean incluirOcultos) {
        List<OpcionMenu> todas = opcionMenuDao.findAll().stream()
                .filter(o -> !o.eliminado())
                .filter(o -> incluirOcultos || !Boolean.FALSE.equals(o.getVisible()))
                .toList();
        Set<Long> protegidos = idsProtegidos(todas);

        List<MenuNodoDto> secciones = new ArrayList<>();
        for (OpcionMenu seccion : ordenar(filtrarPorTipo(todas, TIPO_SECCION))) {
            MenuNodoDto seccionDto = aDto(seccion, false, protegidos);
            for (OpcionMenu grupo : ordenar(hijosDe(todas, seccion, TIPO_GRUPO))) {
                MenuNodoDto grupoDto = aDto(grupo, seccionDto.isBloqueado(), protegidos);
                for (OpcionMenu item : ordenar(hijosDe(todas, grupo, TIPO_ITEM))) {
                    grupoDto.getHijos().add(aDto(item, grupoDto.isBloqueado(), protegidos));
                }
                seccionDto.getHijos().add(grupoDto);
            }
            secciones.add(seccionDto);
        }
        return secciones;
    }

    /** Los ítems protegidos y todos sus ancestros. */
    private Set<Long> idsProtegidos(List<OpcionMenu> todas) {
        Set<Long> ids = new HashSet<>();
        for (OpcionMenu o : todas) {
            if (CODIGOS_PROTEGIDOS.contains(o.getCodigo())) {
                for (OpcionMenu n = o; n != null; n = n.getPadre()) {
                    ids.add(n.getIdOpcion());
                }
            }
        }
        return ids;
    }

    private boolean esProtegido(OpcionMenu nodo) {
        return idsProtegidos(opcionMenuDao.findAll().stream().filter(o -> !o.eliminado()).toList())
                .contains(nodo.getIdOpcion());
    }

    private List<OpcionMenu> filtrarPorTipo(List<OpcionMenu> todas, String tipo) {
        return todas.stream().filter(o -> tipo.equals(o.getTipo())).collect(Collectors.toList());
    }

    private List<OpcionMenu> hijosDe(List<OpcionMenu> todas, OpcionMenu padre, String tipo) {
        return todas.stream()
                .filter(o -> tipo.equals(o.getTipo()))
                .filter(o -> o.getPadre() != null && padre.getIdOpcion().equals(o.getPadre().getIdOpcion()))
                .collect(Collectors.toList());
    }

    private List<OpcionMenu> ordenar(List<OpcionMenu> nodos) {
        nodos.sort(Comparator.comparing(o -> o.getOrden() != null ? o.getOrden() : Integer.MAX_VALUE));
        return nodos;
    }

    private MenuNodoDto aDto(OpcionMenu o, boolean padreBloqueado, Set<Long> protegidos) {
        MenuNodoDto dto = new MenuNodoDto();
        dto.setIdOpcion(o.getIdOpcion());
        dto.setCodigo(o.getCodigo());
        dto.setDescripcion(o.getDescripcion());
        dto.setTipo(o.getTipo());
        dto.setIcono(o.getIcono());
        dto.setColorClase(o.getColorClase());
        dto.setBadge(o.getBadge());
        dto.setUrl(o.getUrl());
        dto.setRutaBase(o.getRutaBase());
        dto.setOrden(o.getOrden());
        dto.setVisible(!Boolean.FALSE.equals(o.getVisible()));
        dto.setEstado(o.bloqueado() ? OpcionMenu.ESTADO_BLOQUEADO : OpcionMenu.ESTADO_ACTIVO);
        dto.setBloqueado(padreBloqueado || o.bloqueado());
        dto.setProtegido(protegidos.contains(o.getIdOpcion()));
        dto.setPermisoPuro(TIPO_ITEM.equals(o.getTipo()) && (o.getUrl() == null || o.getUrl().isBlank()));
        return dto;
    }

    private MenuNodoDto copiarNodo(MenuNodoDto o) {
        MenuNodoDto dto = new MenuNodoDto();
        dto.setIdOpcion(o.getIdOpcion());
        dto.setCodigo(o.getCodigo());
        dto.setDescripcion(o.getDescripcion());
        dto.setTipo(o.getTipo());
        dto.setIcono(o.getIcono());
        dto.setColorClase(o.getColorClase());
        dto.setBadge(o.getBadge());
        dto.setUrl(o.getUrl());
        dto.setRutaBase(o.getRutaBase());
        dto.setOrden(o.getOrden());
        dto.setVisible(o.getVisible());
        dto.setEstado(o.getEstado());
        dto.setBloqueado(o.isBloqueado());
        dto.setProtegido(o.isProtegido());
        dto.setPermisoPuro(o.isPermisoPuro());
        return dto;
    }

    private void recorrer(List<MenuNodoDto> nodos, java.util.function.Consumer<MenuNodoDto> f) {
        for (MenuNodoDto n : nodos) {
            f.accept(n);
            recorrer(n.getHijos(), f);
        }
    }

    private Map<Long, Long> usuariosPorOpcion() {
        Map<Long, Long> m = new HashMap<>();
        for (Object[] f : opcionMenuDao.contarUsuariosPorOpcion()) {
            m.put(((Number) f[0]).longValue(), ((Number) f[1]).longValue());
        }
        return m;
    }

    // ── CRUD de gestión ─────────────────────────────────────────────────────

    @Override
    @Transactional
    public OpcionMenu guardarNodo(OpcionMenu nodo, Long idPadre) {
        String tipo = nodo.getTipo();

        if (nodo.getVisible() == null) {
            nodo.setVisible(true);
        }

        Long idPadreAnterior = (nodo.getPadre() != null) ? nodo.getPadre().getIdOpcion() : null;

        // Resolver padre y mantener columnas denormalizadas según el tipo.
        if (TIPO_SECCION.equals(tipo)) {
            nodo.setPadre(null);
            nodo.setSeccion(null);
            nodo.setGrupo(null);
            nodo.setUrl(null);
            nodo.setRutaBase(null);
            nodo.setBadge(null);
            nodo.setIcono(null);
            nodo.setColorClase(null);
        } else if (TIPO_GRUPO.equals(tipo)) {
            OpcionMenu seccion = (idPadre != null) ? findById(idPadre) : null;
            nodo.setPadre(seccion);
            nodo.setSeccion(seccion != null ? seccion.getDescripcion() : null);
            nodo.setGrupo(null);
            nodo.setUrl(null);
            nodo.setRutaBase(null);
            nodo.setBadge(null);
        } else { // ITEM
            OpcionMenu grupo = (idPadre != null) ? findById(idPadre) : null;
            nodo.setPadre(grupo);
            nodo.setGrupo(grupo != null ? grupo.getDescripcion() : null);
            OpcionMenu seccion = (grupo != null) ? grupo.getPadre() : null;
            nodo.setSeccion(seccion != null ? seccion.getDescripcion() : null);
            if (nodo.getBadge() != null && nodo.getBadge().isBlank()) {
                nodo.setBadge(null);
            }
        }

        // Nuevo, o movido a otro padre: va al final de sus nuevos hermanos.
        if (nodo.getIdOpcion() == null || !Objects.equals(idPadreAnterior, idPadre)) {
            nodo.setOrden(siguienteOrden(idPadre, tipo));
        }

        OpcionMenu guardado = opcionMenuDao.save(nodo);
        actualizarDescendientes(guardado);
        limpiarCacheMenu();
        return guardado;
    }

    /**
     * Las columnas {@code seccion}/{@code grupo} de los hijos repiten el nombre del padre:
     * al renombrar o mover un grupo o una sección hay que reescribirlas, si no la pantalla
     * de permisos seguía mostrando el nombre viejo.
     */
    private void actualizarDescendientes(OpcionMenu nodo) {
        if (TIPO_SECCION.equals(nodo.getTipo())) {
            for (OpcionMenu grupo : opcionMenuDao.findByPadre_IdOpcionAndTipoOrderByOrdenAsc(nodo.getIdOpcion(), TIPO_GRUPO)) {
                grupo.setSeccion(nodo.getDescripcion());
                opcionMenuDao.save(grupo);
                actualizarDescendientes(grupo);
            }
        } else if (TIPO_GRUPO.equals(nodo.getTipo())) {
            String seccion = nodo.getPadre() != null ? nodo.getPadre().getDescripcion() : null;
            for (OpcionMenu item : opcionMenuDao.findByPadre_IdOpcionAndTipoOrderByOrdenAsc(nodo.getIdOpcion(), TIPO_ITEM)) {
                item.setGrupo(nodo.getDescripcion());
                item.setSeccion(seccion);
                opcionMenuDao.save(item);
            }
        }
    }

    private int siguienteOrden(Long idPadre, String tipo) {
        List<OpcionMenu> hermanos = hermanos(idPadre, tipo);
        int max = 0;
        for (OpcionMenu h : hermanos) {
            // Los permisos puros van al final (orden 900+): no cuentan para el siguiente.
            if (h.getOrden() != null && h.getOrden() > max && h.getOrden() < 900) {
                max = h.getOrden();
            }
        }
        return max + 1;
    }

    private List<OpcionMenu> hermanos(Long idPadre, String tipo) {
        return (idPadre == null)
                ? opcionMenuDao.findByPadreIsNullAndTipoOrderByOrdenAsc(tipo)
                : opcionMenuDao.findByPadre_IdOpcionAndTipoOrderByOrdenAsc(idPadre, tipo);
    }

    @Override
    @Transactional
    public void reordenar(Long idPadre, List<Long> idsEnOrden) {
        if (idsEnOrden == null || idsEnOrden.isEmpty()) {
            return;
        }
        OpcionMenu padre = (idPadre != null) ? findById(idPadre) : null;
        if (idPadre != null && (padre == null || padre.eliminado())) {
            throw new IllegalStateException("El destino ya no existe.");
        }
        String tipoHijo = padre == null ? TIPO_SECCION
                : TIPO_SECCION.equals(padre.getTipo()) ? TIPO_GRUPO
                : TIPO_GRUPO.equals(padre.getTipo()) ? TIPO_ITEM
                : null;
        if (tipoHijo == null) {
            throw new IllegalStateException("Un ítem no puede contener otras opciones.");
        }

        int orden = 1;
        for (Long id : idsEnOrden) {
            OpcionMenu n = findById(id);
            if (n == null || n.eliminado()) continue;
            if (!tipoHijo.equals(n.getTipo())) {
                throw new IllegalStateException("«" + n.getDescripcion() + "» es " + n.getTipo().toLowerCase()
                        + " y no puede ir ahí: " + (padre == null ? "en la raíz solo van secciones."
                        : "dentro de " + padre.getTipo().toLowerCase() + " solo van " + tipoHijo.toLowerCase() + "s."));
            }
            boolean cambiaPadre = !Objects.equals(n.getPadre() != null ? n.getPadre().getIdOpcion() : null, idPadre);
            if (cambiaPadre) {
                n.setPadre(padre);
                if (TIPO_GRUPO.equals(tipoHijo)) {
                    n.setSeccion(padre.getDescripcion());
                } else if (TIPO_ITEM.equals(tipoHijo)) {
                    n.setGrupo(padre.getDescripcion());
                    n.setSeccion(padre.getPadre() != null ? padre.getPadre().getDescripcion() : null);
                }
            }
            // Los permisos puros conservan su lugar al final del grupo.
            boolean permisoPuro = TIPO_ITEM.equals(n.getTipo()) && (n.getUrl() == null || n.getUrl().isBlank())
                    && n.getOrden() != null && n.getOrden() >= 900;
            if (!permisoPuro) n.setOrden(orden++);
            opcionMenuDao.save(n);
            if (cambiaPadre) actualizarDescendientes(n);
        }
        limpiarCacheMenu();
    }

    @Override
    @Transactional
    public void alternarVisible(Long idOpcion) {
        OpcionMenu nodo = findById(idOpcion);
        if (nodo == null) {
            return;
        }
        boolean ocultar = !Boolean.FALSE.equals(nodo.getVisible());
        if (ocultar && esProtegido(nodo)) {
            throw new IllegalStateException("«" + nodo.getDescripcion() + "» no se puede ocultar: "
                    + "contiene Gestión de Menú o Usuarios, y sin eso no se podría deshacer el cambio.");
        }
        nodo.setVisible(!ocultar);
        opcionMenuDao.save(nodo);
        limpiarCacheMenu();
    }

    @Override
    @Transactional
    public void bloquear(Long idOpcion, boolean bloquear) {
        OpcionMenu nodo = findById(idOpcion);
        if (nodo == null || nodo.eliminado()) {
            throw new IllegalStateException("La opción no existe.");
        }
        if (bloquear && esProtegido(nodo)) {
            throw new IllegalStateException("«" + nodo.getDescripcion() + "» no se puede bloquear: "
                    + "contiene Gestión de Menú o Usuarios.");
        }
        nodo.setEstado(bloquear ? OpcionMenu.ESTADO_BLOQUEADO : OpcionMenu.ESTADO_ACTIVO);
        opcionMenuDao.save(nodo);
        limpiarCacheMenu();
    }

    @Override
    @Transactional
    public void eliminarNodo(Long idOpcion) {
        OpcionMenu nodo = findById(idOpcion);
        if (nodo == null || nodo.eliminado()) {
            throw new IllegalStateException("La opción no existe.");
        }
        if (esProtegido(nodo)) {
            throw new IllegalStateException("«" + nodo.getDescripcion() + "» no se puede eliminar: "
                    + "contiene Gestión de Menú o Usuarios.");
        }
        long hijosVigentes = opcionMenuDao.findAll().stream()
                .filter(o -> o.getPadre() != null && idOpcion.equals(o.getPadre().getIdOpcion()))
                .filter(o -> !o.eliminado())
                .count();
        if (hijosVigentes > 0) {
            throw new IllegalStateException("No se puede eliminar: tiene " + hijosVigentes
                    + " opción(es) adentro. Elimínelas o muévalas primero.");
        }
        // Borrado lógico: si se borrara la fila, OpcionMenuSeeder la volvería a crear en el
        // próximo arranque. Se quitan los permisos asignados (ya no hay casilla para ellos),
        // y a cada usuario que la tenía le queda el registro en su historial de permisos.
        List<Long> afectados = opcionMenuDao.usuariosConOpcion(idOpcion);
        if (!afectados.isEmpty()) {
            for (Usuario u : usuarioDao.findAllByIdUsuarioIn(new HashSet<>(afectados))) {
                Set<String> antes = codigosPorUsuario(u.getIdUsuario());
                Set<String> despues = new HashSet<>(antes);
                despues.remove(nodo.getCodigo());
                auditoriaPermisos.registrar(u, HistorialPermisoUsuario.QUITADO_POR_MENU,
                        "Gestión de Menú › Eliminar opción", antes, despues, null,
                        "Se eliminó «" + nodo.getDescripcion() + "» del menú");
            }
        }
        opcionMenuDao.desvincularDeUsuarios(idOpcion);
        nodo.setEstado(OpcionMenu.ESTADO_ELIMINADO);
        nodo.setVisible(false);
        opcionMenuDao.save(nodo);
        limpiarCacheMenu();
    }

    @Override
    @Transactional
    public void restaurarNodo(Long idOpcion) {
        OpcionMenu nodo = findById(idOpcion);
        if (nodo == null || !nodo.eliminado()) {
            throw new IllegalStateException("La opción no está eliminada.");
        }
        if (nodo.getPadre() != null && nodo.getPadre().eliminado()) {
            throw new IllegalStateException("Primero restaure «" + nodo.getPadre().getDescripcion()
                    + "», que es donde va esta opción.");
        }
        nodo.setEstado(OpcionMenu.ESTADO_ACTIVO);
        nodo.setVisible(true);
        Long idPadre = nodo.getPadre() != null ? nodo.getPadre().getIdOpcion() : null;
        nodo.setOrden(siguienteOrden(idPadre, nodo.getTipo()));
        opcionMenuDao.save(nodo);
        limpiarCacheMenu();
    }
}
