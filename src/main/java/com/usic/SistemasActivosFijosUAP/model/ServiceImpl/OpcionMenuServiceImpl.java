package com.usic.SistemasActivosFijosUAP.model.ServiceImpl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.usic.SistemasActivosFijosUAP.model.IService.IOpcionMenuService;
import com.usic.SistemasActivosFijosUAP.model.dao.IOpcionMenuDao;
import com.usic.SistemasActivosFijosUAP.model.dto.MenuNodoDto;
import com.usic.SistemasActivosFijosUAP.model.entity.OpcionMenu;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;

@Service
public class OpcionMenuServiceImpl implements IOpcionMenuService {

    public static final String TIPO_SECCION = "SECCION";
    public static final String TIPO_GRUPO = "GRUPO";
    public static final String TIPO_ITEM = "ITEM";

    @Autowired
    private IOpcionMenuDao opcionMenuDao;

    /** Caché en memoria del árbol visible (estático; se invalida con limpiarCacheMenu). */
    private volatile List<MenuNodoDto> arbolCache;

    /** Caché de los ítems hoja (la usa el interceptor en cada request). */
    private volatile List<OpcionMenu> itemsCache;

    /**
     * Opciones del bloque "Administración del Sistema". SUPER USUARIO ve todo lo
     * de activos pero NO estas (mismo criterio que el sidebar actual).
     */
    private static final Set<String> CODIGOS_ADMIN_SISTEMA = Set.of(
        "opcion_rol", "opcion_persona", "opcion_usuario", "opcion_responsable"
    );

    /** Opciones de "Seguimiento y Consultas" + "Reportes" que ve APOYO. */
    private static final Set<String> CODIGOS_CONSULTA = Set.of(
        "opcion_aan", "opcion_ta", "opcion_ActivoIngreso", "opcion_ba",
        "opcion_historialA", "opcion_consulta_activo"
    );

    /** Opciones que ve RESPONSABLE por defecto (módulo de comunicados). */
    private static final Set<String> CODIGOS_RESPONSABLE = Set.of(
        "opcion_comunicados"
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
            local = opcionMenuDao.findByTipoOrderByOrdenAsc(TIPO_ITEM);
            itemsCache = local;
        }
        return local;
    }

    @Override
    public List<OpcionMenu> listarSecciones() {
        return opcionMenuDao.findByTipoOrderByOrdenAsc(TIPO_SECCION);
    }

    @Override
    public List<OpcionMenu> listarGrupos() {
        return opcionMenuDao.findByTipoOrderByOrdenAsc(TIPO_GRUPO);
    }

    @Override
    public List<OpcionMenu> buscarPorCodigos(Collection<String> codigos) {
        if (codigos == null || codigos.isEmpty()) {
            return List.of();
        }
        return opcionMenuDao.findByCodigoIn(codigos);
    }

    @Override
    public Set<String> codigosPorUsuario(Long idUsuario) {
        return new HashSet<>(opcionMenuDao.findCodigosByUsuario(idUsuario));
    }

    @Override
    public Set<String> plantillaPorRol(String nombreRol) {
        String rol = nombreRol == null ? "" : nombreRol.toUpperCase();
        Set<String> todos = todosLosCodigos();

        switch (rol) {
            case "ADMINISTRADOR":
                return todos;

            case "SUPER USUARIO":
                // Todo lo de activos, menos el bloque "Administración del Sistema".
                return todos.stream()
                        .filter(c -> !CODIGOS_ADMIN_SISTEMA.contains(c))
                        .collect(Collectors.toCollection(HashSet::new));

            case "APOYO":
                // Solo lo de consulta/seguimiento/reportes que realmente existe en el catálogo.
                return todos.stream()
                        .filter(CODIGOS_CONSULTA::contains)
                        .collect(Collectors.toCollection(HashSet::new));

            case "RESPONSABLE":
                // Por defecto solo el módulo de comunicados (envío/control de lecturas).
                return todos.stream()
                        .filter(CODIGOS_RESPONSABLE::contains)
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

        // ADMINISTRADOR siempre ve todo (evita autobloqueo).
        if ("ADMINISTRADOR".equals(rol)) {
            return todosLosCodigos();
        }

        // Permisos asignados explícitamente.
        if (usuario != null && usuario.getIdUsuario() != null) {
            Set<String> asignados = codigosPorUsuario(usuario.getIdUsuario());
            if (!asignados.isEmpty()) {
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
        return construirArbol(true);
    }

    @Override
    public void limpiarCacheMenu() {
        arbolCache = null;
        itemsCache = null;
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
                .filter(o -> incluirOcultos || !Boolean.FALSE.equals(o.getVisible()))
                .toList();

        List<MenuNodoDto> secciones = new ArrayList<>();
        for (OpcionMenu seccion : ordenar(filtrarPorTipo(todas, TIPO_SECCION))) {
            MenuNodoDto seccionDto = aDto(seccion);
            for (OpcionMenu grupo : ordenar(hijosDe(todas, seccion, TIPO_GRUPO))) {
                MenuNodoDto grupoDto = aDto(grupo);
                for (OpcionMenu item : ordenar(hijosDe(todas, grupo, TIPO_ITEM))) {
                    grupoDto.getHijos().add(aDto(item));
                }
                seccionDto.getHijos().add(grupoDto);
            }
            secciones.add(seccionDto);
        }
        return secciones;
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

    private MenuNodoDto aDto(OpcionMenu o) {
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
        return dto;
    }

    // ── CRUD de gestión ─────────────────────────────────────────────────────

    @Override
    @Transactional
    public OpcionMenu guardarNodo(OpcionMenu nodo, Long idPadre) {
        String tipo = nodo.getTipo();

        if (nodo.getVisible() == null) {
            nodo.setVisible(true);
        }

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

        // Nuevo: asignar el orden al final de sus hermanos.
        if (nodo.getIdOpcion() == null) {
            nodo.setOrden(siguienteOrden(idPadre, tipo));
        }

        OpcionMenu guardado = opcionMenuDao.save(nodo);
        limpiarCacheMenu();
        return guardado;
    }

    private int siguienteOrden(Long idPadre, String tipo) {
        List<OpcionMenu> hermanos = hermanos(idPadre, tipo);
        int max = 0;
        for (OpcionMenu h : hermanos) {
            if (h.getOrden() != null && h.getOrden() > max) {
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
    public void moverArriba(Long idOpcion) {
        intercambiarConVecino(idOpcion, -1);
    }

    @Override
    @Transactional
    public void moverAbajo(Long idOpcion) {
        intercambiarConVecino(idOpcion, 1);
    }

    private void intercambiarConVecino(Long idOpcion, int delta) {
        OpcionMenu nodo = findById(idOpcion);
        if (nodo == null) {
            return;
        }
        Long idPadre = (nodo.getPadre() != null) ? nodo.getPadre().getIdOpcion() : null;
        List<OpcionMenu> hermanos = hermanos(idPadre, nodo.getTipo());

        int pos = -1;
        for (int i = 0; i < hermanos.size(); i++) {
            if (hermanos.get(i).getIdOpcion().equals(idOpcion)) {
                pos = i;
                break;
            }
        }
        int destino = pos + delta;
        if (pos < 0 || destino < 0 || destino >= hermanos.size()) {
            return; // ya está en el extremo
        }

        OpcionMenu vecino = hermanos.get(destino);
        Integer ordenNodo = nodo.getOrden();
        nodo.setOrden(vecino.getOrden());
        vecino.setOrden(ordenNodo);
        opcionMenuDao.save(nodo);
        opcionMenuDao.save(vecino);
        limpiarCacheMenu();
    }

    @Override
    @Transactional
    public void alternarVisible(Long idOpcion) {
        OpcionMenu nodo = findById(idOpcion);
        if (nodo == null) {
            return;
        }
        nodo.setVisible(Boolean.FALSE.equals(nodo.getVisible()));
        opcionMenuDao.save(nodo);
        limpiarCacheMenu();
    }

    @Override
    @Transactional
    public void eliminarNodo(Long idOpcion) {
        OpcionMenu nodo = findById(idOpcion);
        if (nodo == null) {
            throw new IllegalStateException("La opción no existe.");
        }
        if (opcionMenuDao.countByPadre_IdOpcion(idOpcion) > 0) {
            throw new IllegalStateException("No se puede eliminar: tiene elementos hijos. Elimínelos o muévalos primero.");
        }
        // Si es un ítem asignado a usuarios, quitar los enlaces primero.
        opcionMenuDao.desvincularDeUsuarios(idOpcion);
        opcionMenuDao.deleteById(idOpcion);
        limpiarCacheMenu();
    }
}
