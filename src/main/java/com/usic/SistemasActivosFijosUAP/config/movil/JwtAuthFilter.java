package com.usic.SistemasActivosFijosUAP.config.movil;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * Lee el {@code Authorization: Bearer <jwt>} de las peticiones a
 * {@code /api/movil/**} y, si es válido, deja el usuario en el
 * {@code SecurityContext}.
 *
 * <p>No rechaza por sí mismo: si no hay token o es inválido simplemente no
 * autentica, y es la cadena de seguridad la que devuelve 401. Así los endpoints
 * públicos del namespace móvil (login, refresh, version) siguen funcionando con
 * el mismo filtro puesto.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String CABECERA = "Authorization";
    private static final String PREFIJO  = "Bearer ";

    private final JwtService jwtService;

    /** Este filtro solo tiene sentido en el namespace móvil. */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/movil");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String cabecera = request.getHeader(CABECERA);

        if (cabecera != null && cabecera.startsWith(PREFIJO)) {
            String token  = cabecera.substring(PREFIJO.length()).trim();
            Claims claims = jwtService.validar(token);

            if (claims != null) {
                Long idUsuario = jwtService.idUsuarioDe(claims);

                if (idUsuario != null) {
                    String      rol      = jwtService.rolDe(claims);
                    Set<String> permisos = jwtService.permisosDe(claims);

                    UsuarioMovilPrincipal principal = new UsuarioMovilPrincipal(
                            idUsuario,
                            jwtService.usuarioDe(claims),
                            rol,
                            permisos);

                    var authorities = List.of(
                            new SimpleGrantedAuthority("ROLE_" + principal.rolNormalizado()));

                    var auth = new UsernamePasswordAuthenticationToken(
                            principal, null, authorities);

                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            }
        }

        filterChain.doFilter(request, response);
    }
}
