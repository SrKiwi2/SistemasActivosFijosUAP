package com.usic.SistemasActivosFijosUAP.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SeguridadConfig {
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/",
                "/login",
                "/cerrar_sesion",
                "/error**",
                "/assets/**",
                "/iniciar-sesion/**",
                "/administracion/**",
                "/asignacion/**",
                "/trasnferencia/**",
                "/ingreso/**",
                "/reporte/**",
                "/baja/**",
                "/informacion/**",
                "/seguimiento-activo/**",
                "/pdfs/**",
                "/vista/**",
                "/api/**",
                "/adm/**",
                "/openai/**",
                "/uploads/**",
                "/topic/**",
                "/app/**",
                "/ws/**",
                "/legacy/**"
                /* importaciones dbf */,
                "/importe/**"
                )
                .permitAll()
                .anyRequest().authenticated()
            )
            .formLogin(login -> login
                .loginPage("/")
                .permitAll()
            )
            .headers(headers -> headers
                .frameOptions(frame -> frame.sameOrigin())
            )
            
            .csrf(csrf -> csrf.disable());
        
        return http.build();
    }
}