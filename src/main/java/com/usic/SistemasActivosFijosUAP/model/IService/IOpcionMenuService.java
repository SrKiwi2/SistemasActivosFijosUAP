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

    /** Alterna el flag visible de un nodo (falla si es protegido y se quiere ocultar). */
    void alternarVisible(Long idOpcion);

    /**
     * Bloquea o desbloquea un nodo: sigue en el menú pero nadie salvo ADMINISTRADOR
     * puede entrar. Bloquear un grupo o una sección bloquea todo lo que contiene.
     */
    void bloquear(Long idOpcion, boolean bloquear);

    /**
     * Borrado lógico (estado ELIMINADO): falla si tiene hijos vigentes o es protegido;
     * quita los permisos asignados a usuarios.
     */
    void eliminarNodo(Long idOpcion);

    /** Vuelve a poner en el menú un nodo eliminado (su padre tiene que estar vigente). */
    void restaurarNodo(Long idOpcion);

    /** Nodos eliminados, el más reciente primero (papelera de la gestión de menú). */
    List<OpcionMenu> listarEliminados();

    /**
     * Pone a los nodos {@code idsEnOrden} como hijos de {@code idPadre} (null = raíz),
     * en ese orden. Es lo que usa el arrastrar y soltar de la gestión de menú: sirve para
     * reordenar y también para mover un ítem a otro grupo o un grupo a otra sección.
     */
    void reordenar(Long idPadre, List<Long> idsEnOrden);

    /** Códigos de ítems bloqueados, por sí mismos o por su grupo/sección. */
    Set<String> codigosBloqueados();

    /** Árbol para la pantalla de permisos por usuario (vigentes, visibles u ocultos). */
    List<MenuNodoDto> obtenerArbolPermisos();

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
