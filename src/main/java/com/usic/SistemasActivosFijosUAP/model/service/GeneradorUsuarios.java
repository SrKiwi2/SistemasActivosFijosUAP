package com.usic.SistemasActivosFijosUAP.model.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.usic.SistemasActivosFijosUAP.model.IService.IPersonaService;
import com.usic.SistemasActivosFijosUAP.model.IService.IRolService;
import com.usic.SistemasActivosFijosUAP.model.IService.IUsuarioService;
import com.usic.SistemasActivosFijosUAP.model.entity.Persona;
import com.usic.SistemasActivosFijosUAP.model.entity.Rol;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GeneradorUsuarios {

    private final IPersonaService personaService;
    private final IRolService rolService;
    private final IUsuarioService usuarioService;
    private final PasswordEncoder passwordEncoder;

    public List<String[]> generarUsuariosMasivos() {
        List<Persona> personas = personaService.listarPersonas();
        Rol rolPorDefecto = rolService.findById(3L);

        List<String[]> credencialesCSV = new ArrayList<>();

        for (Persona persona : personas) {
            if (persona.getIdPersona() < 3 || persona.getIdPersona() > 421) continue;
            if (persona.getUsuario() != null) continue;

            String[] nombres = persona.getNombre().trim().toLowerCase().split(" ");
            if (nombres.length < 2) continue;

            String usuarioStr = nombres[0] + nombres[1].charAt(0);
            String contrasenaSimple = usuarioStr + "_ac25";

            Usuario usuario = new Usuario();
            usuario.setUsuario(usuarioStr);
            usuario.setPassword(passwordEncoder.encode(contrasenaSimple));
            usuario.setPersona(persona);
            usuario.setRol(rolPorDefecto);
            usuario.setEstado("ACTIVO");

            usuarioService.save(usuario);

            credencialesCSV.add(new String[]{usuarioStr, contrasenaSimple});
        }

        return credencialesCSV;
    }
}
