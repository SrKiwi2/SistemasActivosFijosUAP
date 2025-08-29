package com.usic.SistemasActivosFijosUAP.model.IService;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;

@Service
public interface IUsuarioService extends IServiceGenerico <Usuario, Long>{
    
    Usuario UsuarioyContraseña(String usuario, String password);

    Usuario buscarUsuarioPorNombre(String usuario);

    List<Usuario> listarUsuarios();

    boolean existsByUsuario(String usuario);

    Optional<Usuario> buscarConPersonaRol(String usuario);
}
