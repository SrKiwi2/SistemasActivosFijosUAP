package com.usic.SistemasActivosFijosUAP.config.movil;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.ShallowEtagHeaderFilter;

import lombok.RequiredArgsConstructor;

/**
 * Cadena de seguridad exclusiva de la app móvil: {@code /api/movil/**}.
 *
 * <p><b>No toca la seguridad de la web.</b> Lleva {@code @Order(1)} y un
 * {@code securityMatcher} acotado, así que atiende únicamente al namespace
 * móvil; todo lo demás sigue cayendo en
 * {@link com.usic.SistemasActivosFijosUAP.config.SeguridadConfig}, que no
 * declara orden y por tanto queda con la precedencia más baja.
 *
 * <p>Diferencias clave con la cadena web:
 * <ul>
 *   <li><b>Stateless</b>: sin {@code HttpSession}. La identidad viaja en el JWT.</li>
 *   <li><b>401 en JSON</b> en vez de redirigir a la pantalla de login (un
 *       redirect HTML rompería al cliente móvil).</li>
 *   <li><b>CORS</b> abierto a los orígenes del WebView de Capacitor.</li>
 * </ul>
 */
@Configuration
@RequiredArgsConstructor
public class MovilSecurityConfig {

    public static final String BASE = "/api/movil";

    private final JwtAuthFilter jwtAuthFilter;

    @Value("${movil.cors.origenes}")
    private String origenesPermitidos;

    @Bean
    @Order(1)
    public SecurityFilterChain movilSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher(BASE + "/**")
            .cors(cors -> cors.configurationSource(movilCorsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Endpoints abiertos: iniciar sesión, renovarla y consultar la
                // versión mínima soportada (la app la pide antes de autenticar).
                .requestMatchers(
                    BASE + "/auth/login",
                    BASE + "/auth/refresh",
                    BASE + "/version",
                    BASE + "/salud"
                ).permitAll()
                .anyRequest().authenticated()
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(HttpStatus.UNAUTHORIZED.value());
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.setCharacterEncoding("UTF-8");
                    response.getWriter().write(
                        "{\"ok\":false,\"codigo\":\"TOKEN_INVALIDO\","
                      + "\"mensaje\":\"Sesión no válida o expirada\"}");
                })
                .accessDeniedHandler((request, response, deniedException) -> {
                    response.setStatus(HttpStatus.FORBIDDEN.value());
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.setCharacterEncoding("UTF-8");
                    response.getWriter().write(
                        "{\"ok\":false,\"codigo\":\"SIN_PERMISO\","
                      + "\"mensaje\":\"No tiene permiso para esta operación\"}");
                })
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Orígenes del WebView de Capacitor. En Android la app se sirve desde
     * {@code https://localhost}; en desarrollo, desde el servidor de Vite.
     */
    @Bean
    public CorsConfigurationSource movilCorsConfigurationSource() {
        List<String> origenes = Arrays.stream(origenesPermitidos.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(origenes);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "If-None-Match", "X-App-Version"));
        config.setExposedHeaders(List.of("ETag"));
        config.setAllowCredentials(false); // la sesión va por Bearer, no por cookie
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration(BASE + "/**", config);
        return source;
    }

    /**
     * ETag para {@code /api/movil/**}: cuando la respuesta no cambió, el servidor
     * contesta 304 sin cuerpo. Con conexiones lentas ahorra descargar de nuevo
     * catálogos y fichas que el móvil ya tiene.
     */
    @Bean
    public org.springframework.boot.web.servlet.FilterRegistrationBean<ShallowEtagHeaderFilter> movilEtagFilter() {
        var registro = new org.springframework.boot.web.servlet.FilterRegistrationBean<>(
                new ShallowEtagHeaderFilter());
        registro.addUrlPatterns(BASE + "/*");
        registro.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        registro.setName("movilEtagFilter");
        return registro;
    }
}
