package com.usic.SistemasActivosFijosUAP.model.IService;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Service;

import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;

@Service
public interface IUsuarioService extends IServiceGenerico <Usuario, Long>{
    
    Usuario UsuarioyContraseña(String usuario, String password);

    Usuario buscarUsuarioPorNombre(String usuario);

    List<Usuario> listarUsuarios();

    boolean existsByUsuario(String usuario);

    Optional<Usuario> buscarConPersonaRol(String usuario);

    Optional<Usuario> findByIdUsuario(Long idUsuario);

    List<Usuario> findAllByIdUsuarioIn(Set<Long> idUsuario);

    List<Usuario> findByRolNombreAndEstado(
        @Param("nombreRol") String nombreRol,
        @Param("estado")    String estado
    );
}
