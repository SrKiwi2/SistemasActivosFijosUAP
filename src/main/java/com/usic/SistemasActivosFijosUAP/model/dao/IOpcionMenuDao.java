package com.usic.SistemasActivosFijosUAP.model.dao;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.usic.SistemasActivosFijosUAP.model.entity.OpcionMenu;

public interface IOpcionMenuDao extends JpaRepository<OpcionMenu, Long> {

    OpcionMenu findByCodigo(String codigo);

    boolean existsByCodigo(String codigo);

    List<OpcionMenu> findAllByOrderByOrdenAsc();

    List<OpcionMenu> findByCodigoIn(Collection<String> codigos);

    /** Solo nodos hoja (opciones reales con permiso), ordenados. */
    List<OpcionMenu> findByTipoOrderByOrdenAsc(String tipo);

    /** Hermanos: hijos de un padre con cierto tipo, ordenados (para reordenar). */
    List<OpcionMenu> findByPadre_IdOpcionAndTipoOrderByOrdenAsc(Long idPadre, String tipo);

    /** Nodos raíz (secciones) ordenados. */
    List<OpcionMenu> findByPadreIsNullAndTipoOrderByOrdenAsc(String tipo);

    /** ¿El nodo tiene hijos? (para impedir borrar un padre con contenido). */
    long countByPadre_IdOpcion(Long idPadre);

    /** Quita los enlaces usuario_opcion de una opción antes de borrarla. */
    @Modifying
    @Transactional
    @Query(value = "DELETE FROM usuario_opcion WHERE id_opcion = :idOpcion", nativeQuery = true)
    void desvincularDeUsuarios(@Param("idOpcion") Long idOpcion);

    /** [id_opcion, cantidad de usuarios] con esa opción asignada explícitamente. */
    @Query(value = "SELECT id_opcion, count(*) FROM usuario_opcion GROUP BY id_opcion", nativeQuery = true)
    List<Object[]> contarUsuariosPorOpcion();

    /** ¿Alguien tiene asignada la opción? (migraciones del seeder). */
    @Query(value = "SELECT id_usuario FROM usuario_opcion WHERE id_opcion = :idOpcion", nativeQuery = true)
    List<Long> usuariosConOpcion(@Param("idOpcion") Long idOpcion);

    /** Asigna una opción a un usuario si no la tenía (migraciones del seeder). */
    @Modifying
    @Transactional
    @Query(value = "INSERT INTO usuario_opcion (id_usuario, id_opcion) "
            + "SELECT :idUsuario, :idOpcion WHERE NOT EXISTS ("
            + "  SELECT 1 FROM usuario_opcion WHERE id_usuario = :idUsuario AND id_opcion = :idOpcion)",
            nativeQuery = true)
    void asignarSiFalta(@Param("idUsuario") Long idUsuario, @Param("idOpcion") Long idOpcion);

    /** Códigos de las opciones asignadas explícitamente a un usuario. */
    @Query("SELECT o.codigo FROM Usuario u JOIN u.opciones o WHERE u.idUsuario = :idUsuario")
    List<String> findCodigosByUsuario(@Param("idUsuario") Long idUsuario);
}
