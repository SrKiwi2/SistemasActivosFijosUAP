package com.usic.SistemasActivosFijosUAP.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

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
                        .requestMatchers(HttpMethod.GET, "/", "/login", "/informacion").permitAll()
                        .requestMatchers("/assets/**", "/uploads/**", "/pdfs/**").permitAll()
                        .anyRequest().authenticated())
                .formLogin(login -> login
                        .loginPage("/")
                        .permitAll())
                .headers(h -> h.frameOptions(f -> f.sameOrigin()))
                .csrf(csrf -> csrf.disable())
                .exceptionHandling(ex -> ex
                        // Devuelve 401 (no 302) para rutas protegidas cuando es AJAX
                        .defaultAuthenticationEntryPointFor(
                                new org.springframework.security.web.authentication.HttpStatusEntryPoint(
                                        org.springframework.http.HttpStatus.UNAUTHORIZED),
                                new org.springframework.security.web.util.matcher.AntPathRequestMatcher("/adm/**"))
                        .defaultAuthenticationEntryPointFor(
                                new org.springframework.security.web.authentication.HttpStatusEntryPoint(
                                        org.springframework.http.HttpStatus.UNAUTHORIZED),
                                new org.springframework.security.web.util.matcher.AntPathRequestMatcher(
                                        "/administracion/**")));

        return http.build();
    }

}
