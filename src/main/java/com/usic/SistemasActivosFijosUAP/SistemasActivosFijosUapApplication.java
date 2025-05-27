package com.usic.SistemasActivosFijosUAP;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.usic.SistemasActivosFijosUAP.model.IService.IPersonaService;
import com.usic.SistemasActivosFijosUAP.model.IService.IRolService;
import com.usic.SistemasActivosFijosUAP.model.IService.IUsuarioService;
import com.usic.SistemasActivosFijosUAP.model.entity.Persona;
import com.usic.SistemasActivosFijosUAP.model.entity.Rol;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;

@SpringBootApplication
public class SistemasActivosFijosUapApplication {

	private static final Logger logger = LoggerFactory.getLogger(SistemasActivosFijosUapApplication.class);

	public static void main(String[] args) {
		SpringApplication.run(SistemasActivosFijosUapApplication.class, args);
	}

	@Bean
    ApplicationRunner init(IUsuarioService usuarioService, IPersonaService personaService, IRolService rolService, PasswordEncoder passwordEncoder) {
        return args -> {
            logger.info("SISTEMA DE ACTIVOS FIJOS INICIADO...");
            String[] roles = { "SUPER USUARIO", "ADMINISTRADOR" };
            Rol[] rolObjects = new Rol[roles.length];
            for (int i = 0; i < roles.length; i++) {
                Rol rol = rolService.buscarRolPorNombre(roles[i]);
                if (rol == null) {
                    rol = new Rol();
                    rol.setNombre(roles[i]);
					rol.setEstado("ACTIVO");
                    rolService.save(rol);
                }
                rolObjects[i] = rol;
            }

            String[] cis = { "12345678", "87654321" };
            String[] nombres = { "PRIMER USUARIO", "SEGUNDO USUARIO" };
            String[] usuarios = { "admin1", "admin2" };
			String[] password = { "usuario&25", "admin&25" };
            for (int i = 0; i < cis.length; i++) {

                Persona persona = personaService.buscarPersonaPorCI(cis[i]);
                if (persona == null) {
                    persona = new Persona();
                    persona.setNombre(nombres[i]);
                    persona.setPaterno("ApellidoP" + (i + 1));
                    persona.setMaterno("ApellidoM" + (i + 1));
                    persona.setCi(cis[i]);
					persona.setEstado("ACTIVO");
                    personaService.save(persona);
                }

                Usuario usuario = usuarioService.buscarUsuarioPorNombre(usuarios[i]);
                if (usuario == null) {
                    usuario = new Usuario();
                    usuario.setUsuario(usuarios[i]);
                    usuario.setPassword(passwordEncoder.encode(password[i]));
                    usuario.setPersona(persona);
                    usuario.setRol(rolObjects[i % roles.length]);
					usuario.setEstado("ACTIVO");
                    usuarioService.save(usuario);
                }
            }
        };
    }
}
