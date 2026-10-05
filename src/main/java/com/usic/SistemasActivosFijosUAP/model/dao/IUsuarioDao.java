package com.usic.SistemasActivosFijosUAP.model.dao;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;

public interface IUsuarioDao extends JpaRepository <Usuario, Long>{
    @Query(value = "select * from usuario u where u._estado='ACTIVO' and u.usuario = ?1 and u.password = ?2", nativeQuery = true)
    Usuario UsuarioyContraseña(String usuario, String password);

    @Query("SELECT u FROM Usuario u WHERE u.usuario = ?1 AND u.estado = 'ACTIVO'")
    Usuario buscarUsuarioPorNombre(String usuario);

    @Query("SELECT u FROM Usuario u WHERE u.estado = 'ACTIVO'")
    List<Usuario> listarUsuarios();

    // Usuarios activos con persona y rol cargados (para selectores del módulo de comunicados).
    @Query("SELECT u FROM Usuario u " +
        "JOIN FETCH u.persona p " +
        "LEFT JOIN FETCH u.rol r " +
        "WHERE u.estado = 'ACTIVO' " +
        "ORDER BY p.nombre ASC")
    List<Usuario> listarConPersona();

    boolean existsByUsuario(String usuario);

    @Query("""
        select u
        from Usuario u
        left join fetch u.persona p
        left join fetch u.rol r
        where u.usuario = :usuario
    """)
    Optional<Usuario> findByUsuarioWithPersonaAndRol(@Param("usuario") String usuario);

    Optional<Usuario> findByIdUsuario(Long idUsuario);

    /** Usuarios (no eliminados) de una persona: a quiénes avisar si cambian sus datos. */
    @Query("select u.idUsuario from Usuario u where u.persona.idPersona = :idPersona "
            + "and (u.estado is null or u.estado <> 'ELIMINADO')")
    List<Long> idsPorPersona(@Param("idPersona") Long idPersona);

    /** Usuario con persona y rol ya cargados: va a quedar guardado en la sesión HTTP. */
    @Query("""
        select u
        from Usuario u
        left join fetch u.persona p
        left join fetch u.rol r
        where u.idUsuario = :id
    """)
    Optional<Usuario> findByIdConPersonaYRol(@Param("id") Long id);

    /** Todos menos los eliminados, con persona y rol (pantalla de gestión de usuarios). */
    @Query("""
        select u
        from Usuario u
        left join fetch u.persona p
        left join fetch u.rol r
        where u.estado is null or u.estado <> 'ELIMINADO'
        order by p.paterno, p.nombre
    """)
    List<Usuario> listarParaGestion();

    /** ¿Ya existe ese nombre de usuario, en cualquier estado, en otro registro? */
    @Query("select count(u) > 0 from Usuario u where lower(u.usuario) = lower(:usuario) and (:idExcluir is null or u.idUsuario <> :idExcluir)")
    boolean existeNombre(@Param("usuario") String usuario, @Param("idExcluir") Long idExcluir);

    /** Inactivos con ese nombre (el login dice "desactivado" si la contraseña coincide con uno). */
    @Query("select u from Usuario u where lower(u.usuario) = lower(:usuario) and u.estado = 'INACTIVO'")
    List<Usuario> inactivosPorNombre(@Param("usuario") String usuario);

    /** ¿La persona ya tiene otro usuario vigente? (la relación usuario-persona es uno a uno). */
    @Query("select count(u) > 0 from Usuario u where u.persona.idPersona = :idPersona and (u.estado is null or u.estado <> 'ELIMINADO') and (:idExcluir is null or u.idUsuario <> :idExcluir)")
    boolean personaTieneUsuario(@Param("idPersona") Long idPersona, @Param("idExcluir") Long idExcluir);

    /** [id_usuario, cantidad] de permisos de menú asignados a mano. */
    @Query(value = "SELECT id_usuario, count(*) FROM usuario_opcion GROUP BY id_usuario", nativeQuery = true)
    List<Object[]> contarPermisosPorUsuario();

    /** Usuarios activos de un rol (para no dejar el sistema sin ADMINISTRADOR). */
    @Query("select count(u) from Usuario u where upper(u.rol.nombre) = upper(:rol) and u.estado = 'ACTIVO'")
    long contarActivosPorRol(@Param("rol") String rol);

    List<Usuario> findAllByIdUsuarioIn(Set<Long> idUsuario);

    // Buscar usuarios activos por nombre de rol
    @Query("SELECT u FROM Usuario u " +
        "JOIN FETCH u.persona p " +
        "WHERE u.rol.nombre = :nombreRol " +
        "AND u.estado = :estado")
    List<Usuario> findByRolNombreAndEstado(
        @Param("nombreRol") String nombreRol,
        @Param("estado")    String estado
    );

    // Usuarios ACTIVOS cuya persona es responsable de una oficina (audiencia "por
    // sección/oficina" de un comunicado).
    @Query("SELECT DISTINCT u FROM Usuario u, Responsable r " +
        "WHERE r.persona = u.persona " +
        "AND r.oficina.idOficina = :idOficina " +
        "AND u.estado = 'ACTIVO'")
    List<Usuario> findActivosPorOficina(@Param("idOficina") Long idOficina);
}
