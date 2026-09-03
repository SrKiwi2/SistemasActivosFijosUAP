package com.usic.SistemasActivosFijosUAP.config;

import java.util.Optional;

import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;

import jakarta.servlet.http.HttpSession;

/**
 * Quién es "el usuario actual" para {@code @CreatedBy}/{@code @LastModifiedBy}.
 * <p>
 * Este sistema NO autentica con Spring Security — {@code SeguridadConfig} tiene
 * {@code .formLogin()} pero no se usa de verdad: el login real es la sesión HTTP
 * custom que arma {@code LoginController} (POST /iniciar-sesion,
 * {@code session.setAttribute("usuario", ...)}). Por eso el auditor se lee de esa
 * sesión y no de {@code SecurityContextHolder}, que en este proyecto nunca se autentica.
 * <p>
 * Fuera de una petición HTTP —tareas {@code @Scheduled} (sync con el VSIAF, cola de
 * confirmación), el {@code ApplicationRunner} de arranque (seeders de menú/usuarios)—
 * no hay sesión: se devuelve {@code Optional.empty()} y Spring Data simplemente no
 * toca el campo, igual que pasaba antes de activar esta auditoría (esos campos ya
 * quedaban en null en esos casos). Por eso este método NUNCA debe propagar una
 * excepción: un error acá tumbaría el guardado de cualquier entidad del sistema,
 * incluida la sincronización con el VSIAF.
 */
@Component
public class UsuarioAuditorAware implements AuditorAware<Long> {

    @Override
    public Optional<Long> getCurrentAuditor() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) return Optional.empty();

            HttpSession session = attrs.getRequest().getSession(false);
            if (session == null) return Optional.empty();

            Usuario usuario = (Usuario) session.getAttribute("usuario");
            return usuario != null ? Optional.ofNullable(usuario.getIdUsuario()) : Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
