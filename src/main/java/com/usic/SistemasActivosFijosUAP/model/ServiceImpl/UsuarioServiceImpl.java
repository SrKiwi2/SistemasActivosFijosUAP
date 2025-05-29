package com.usic.SistemasActivosFijosUAP.model.ServiceImpl;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.usic.SistemasActivosFijosUAP.model.IService.IUsuarioService;
import com.usic.SistemasActivosFijosUAP.model.dao.IUsuarioDao;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;

@Service
public class UsuarioServiceImpl implements IUsuarioService{

    @Autowired
    private IUsuarioDao usuarioDao;

    @Override
    public List<Usuario> findAll() {
        return usuarioDao.findAll();
    }

    @Override
    public Usuario findById(Long idEntidad) {
        return usuarioDao.findById(idEntidad).orElse(null);
    }

    @Override
    public Usuario save(Usuario entidad) {
        return usuarioDao.save(entidad);
    }

    @Override
    public void deleteById(Long idEntidad) {
        usuarioDao.deleteById(idEntidad);
    }

    @Override
    public Usuario UsuarioyContraseña(String usuario, String password) {
        return usuarioDao.UsuarioyContraseña(usuario, password);
    }

    @Override
    public Usuario buscarUsuarioPorNombre(String usuario) {
        return usuarioDao.buscarUsuarioPorNombre(usuario);
    }

    @Override
    public List<Usuario> listarUsuarios() {
        return usuarioDao.listarUsuarios();
    }

    @Override
    public boolean existsByUsuario(String usuario) {
        return usuarioDao.existsByUsuario(usuario);
    }
    
}
