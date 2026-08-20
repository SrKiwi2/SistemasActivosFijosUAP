package com.usic.SistemasActivosFijosUAP.config.movil;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;

/**
 * Emisión y verificación de los JWT de la app móvil.
 *
 * <p>Solo se usa en la cadena de seguridad {@code /api/movil/**}
 * ({@link MovilSecurityConfig}). El sistema web sigue con sesión HTTP y no se ve
 * afectado.
 *
 * <p>Claims que viajan en el token:
 * <ul>
 *   <li>{@code sub}  → idUsuario</li>
 *   <li>{@code usr}  → nombre de usuario</li>
 *   <li>{@code rol}  → nombre del rol tal cual está en la BD (ej. "SUPER USUARIO")</li>
 *   <li>{@code perm} → códigos de opción de menú vigentes</li>
 * </ul>
 *
 * <p>Los permisos viajan en el token para que la app pinte el menú sin una
 * llamada extra, pero <b>cada endpoint los revalida en el servidor</b>: el token
 * es una copia cacheada, no la autoridad.
 */
@Slf4j
@Service
public class JwtService {

    private final SecretKey clave;
    private final long      vidaAccessTokenMs;

    public JwtService(
            @Value("${movil.jwt.secret}") String secreto,
            @Value("${movil.jwt.access-token.horas:24}") long horas) {

        byte[] bytes = secreto.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            // HS256 exige 256 bits. Fallar aquí (al arrancar) es mucho mejor que
            // fallar en el primer login de un usuario en campo.
            throw new IllegalStateException(
                "movil.jwt.secret debe tener al menos 32 caracteres (tiene " + bytes.length + ")");
        }
        this.clave             = Keys.hmacShaKeyFor(bytes);
        this.vidaAccessTokenMs = horas * 60L * 60L * 1000L;
    }

    /** Vida del access token, en segundos (lo que se devuelve como {@code expiraEn}). */
    public long vidaAccessTokenSegundos() {
        return vidaAccessTokenMs / 1000L;
    }

    // ── Emisión ──────────────────────────────────────────────────────────────

    public String generarAccessToken(Usuario usuario, Set<String> permisos) {
        Date ahora   = new Date();
        Date caduca  = new Date(ahora.getTime() + vidaAccessTokenMs);

        String rol = (usuario.getRol() != null && usuario.getRol().getNombre() != null)
                ? usuario.getRol().getNombre().toUpperCase()
                : "";

        return Jwts.builder()
                .subject(String.valueOf(usuario.getIdUsuario()))
                .claim("usr",  usuario.getUsuario())
                .claim("rol",  rol)
                .claim("perm", permisos != null ? List.copyOf(permisos) : List.of())
                .issuedAt(ahora)
                .expiration(caduca)
                .signWith(clave)
                .compact();
    }

    // ── Verificación ─────────────────────────────────────────────────────────

    /**
     * Valida firma y caducidad y devuelve los claims.
     *
     * @return los claims, o {@code null} si el token es inválido, está caducado
     *         o fue manipulado. Nunca lanza: quien llama decide qué hacer.
     */
    public Claims validar(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(clave)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("[MOVIL] Token rechazado: {}", e.getMessage());
            return null;
        }
    }

    public Long idUsuarioDe(Claims claims) {
        try {
            return Long.valueOf(claims.getSubject());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public String rolDe(Claims claims) {
        Object rol = claims.get("rol");
        return rol != null ? rol.toString() : "";
    }

    public String usuarioDe(Claims claims) {
        Object usr = claims.get("usr");
        return usr != null ? usr.toString() : "";
    }

    @SuppressWarnings("unchecked")
    public Set<String> permisosDe(Claims claims) {
        Object perm = claims.get("perm");
        if (perm instanceof List<?> lista) {
            Set<String> codigos = new LinkedHashSet<>();
            for (Object o : (List<Object>) lista) {
                if (o != null) codigos.add(o.toString());
            }
            return codigos;
        }
        return Set.of();
    }
}
