package com.usic.SistemasActivosFijosUAP.model.IService;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.usic.SistemasActivosFijosUAP.model.dto.MenuNodoDto;
import com.usic.SistemasActivosFijosUAP.model.entity.OpcionMenu;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;

@Service
public interface IOpcionMenuService extends IServiceGenerico<OpcionMenu, Long> {

    /** Todos los nodos del catálogo (secciones, grupos e ítems), ordenados. */
    List<OpcionMenu> listarTodas();

    /** Solo los ítems hoja (opciones reales con permiso), ordenados. */
    List<OpcionMenu> listarItems();

    /** Opciones cuyo código está en la colección dada (para guardar permisos). */
    List<OpcionMenu> buscarPorCodigos(Collection<String> codigos);

    /**
     * Árbol del sidebar (SECCION → GRUPO → ITEM) filtrado: solo nodos visibles
     * cuyos ítems estén en {@code opciones}, y grupos/secciones con ≥1 hijo.
     */
    List<MenuNodoDto> obtenerMenuVisible(Set<String> opciones);

    /** Árbol completo de nodos visibles (para ADMIN, sin filtrar por permiso). */
    List<MenuNodoDto> obtenerMenuVisibleCompleto();

    /** Árbol para la pantalla de gestión: incluye también los nodos ocultos. */
    List<MenuNodoDto> obtenerArbolAdmin();

    /** Invalida la caché del árbol de menú (tras editar el catálogo). */
    void limpiarCacheMenu();

    // ── CRUD de gestión de opciones ─────────────────────────────────────────

    /** Secciones (tipo SECCION) ordenadas — para elegir padre de un GRUPO. */
    List<OpcionMenu> listarSecciones();

    /** Grupos (tipo GRUPO) ordenados — para elegir padre de un ITEM. */
    List<OpcionMenu> listarGrupos();

    /**
     * Crea o actualiza un nodo. Resuelve el padre por {@code idPadre}, mantiene
     * las columnas denormalizadas seccion/grupo, asigna el orden al final entre
     * sus hermanos cuando es nuevo, e invalida la caché.
     */
    OpcionMenu guardarNodo(OpcionMenu nodo, Long idPadre);

    /** Sube/baja un nodo intercambiando el orden con su hermano adyacente. */
    void moverArriba(Long idOpcion);

    void moverAbajo(Long idOpcion);

    /** Alterna el flag visible de un nodo. */
    void alternarVisible(Long idOpcion);

    /** Elimina un nodo (falla si tiene hijos; desvincula usuarios si es ITEM). */
    void eliminarNodo(Long idOpcion);

    /** Códigos asignados explícitamente al usuario (tabla usuario_opcion). */
    Set<String> codigosPorUsuario(Long idUsuario);

    /** Plantilla de códigos por defecto para un rol (usada como fallback / pre-llenado). */
    Set<String> plantillaPorRol(String nombreRol);

    /**
     * Conjunto de códigos que el usuario debe ver en el sidebar:
     *   · ADMINISTRADOR  → todas las opciones (fail-safe),
     *   · con permisos asignados → esos permisos,
     *   · sin permisos asignados → la plantilla de su rol (compatibilidad).
     */
    Set<String> opcionesEfectivas(Usuario usuario);
}
