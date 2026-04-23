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
}
