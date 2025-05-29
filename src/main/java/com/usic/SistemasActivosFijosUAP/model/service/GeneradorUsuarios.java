package com.usic.SistemasActivosFijosUAP.model.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

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

    private static final String SUFIJO_CONTRASENA = "_ac25";

    public List<String[]> generarUsuariosMasivos() {
        List<Persona> personas = personaService.listarPersonas();
        Rol rolPorDefecto = rolService.findById(3L);
        List<String[]> credencialesCSV = new ArrayList<>();

        int contadorNs = 1;
        Set<String> usuariosGenerados = new HashSet<>();

        for (Persona persona : personas) {
            if (persona.getIdPersona() < 3 || persona.getIdPersona() > 421) continue;
            if (persona.getUsuario() != null) continue;

            String baseUsuario;

            // Extracción básica de nombres
            String nombre = (persona.getNombre() != null) ? persona.getNombre().trim().toLowerCase() : "";
            String paterno = (persona.getPaterno() != null) ? persona.getPaterno().trim().toLowerCase() : "";

            String[] nombres = nombre.split(" ");

            if (nombres.length >= 2) {
                baseUsuario = nombres[0] + nombres[1].charAt(0);
            } else if (nombres.length == 1 && !paterno.isEmpty()) {
                baseUsuario = nombres[0] + paterno.charAt(0);
            } else if (!nombre.isEmpty()) {
                baseUsuario = nombres[0];
            } else if (!paterno.isEmpty()) {
                baseUsuario = paterno;
            } else {
                baseUsuario = "ns" + String.format("%03d", contadorNs++);
            }

            // Asegurar que el usuario sea único
            String usuarioFinal = baseUsuario;
            int sufijo = 1;
            while (usuariosGenerados.contains(usuarioFinal) || usuarioService.existsByUsuario(usuarioFinal)) {
                usuarioFinal = baseUsuario + sufijo;
                sufijo++;
            }

            usuariosGenerados.add(usuarioFinal);

            // Contraseña única
            String contrasenaSimple = generarCodigoAleatorio(4) + SUFIJO_CONTRASENA;

            Usuario usuario = new Usuario();
            usuario.setUsuario(usuarioFinal);
            usuario.setPassword(passwordEncoder.encode(contrasenaSimple));
            usuario.setPersona(persona);
            usuario.setRol(rolPorDefecto);
            usuario.setEstado("ACTIVO");

            usuarioService.save(usuario);

            credencialesCSV.add(new String[]{usuarioFinal, contrasenaSimple});
        }

        return credencialesCSV;
    }

    private String generarCodigoAleatorio(int longitud) {
        String caracteres = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder codigo = new StringBuilder();
        Random random = new Random();
        for (int i = 0; i < longitud; i++) {
            codigo.append(caracteres.charAt(random.nextInt(caracteres.length())));
        }
        return codigo.toString();
    }
}