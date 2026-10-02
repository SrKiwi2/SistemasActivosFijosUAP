package com.usic.SistemasActivosFijosUAP.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class MvcConfig implements WebMvcConfigurer{

    private final PermisoOpcionInterceptor permisoOpcionInterceptor;
    private final SesionPermisosInterceptor sesionPermisosInterceptor;
    private final SesionInactividadInterceptor sesionInactividadInterceptor;
    private final SesionControlInterceptor sesionControlInterceptor;

    public MvcConfig(PermisoOpcionInterceptor permisoOpcionInterceptor,
            SesionPermisosInterceptor sesionPermisosInterceptor,
            SesionInactividadInterceptor sesionInactividadInterceptor,
            SesionControlInterceptor sesionControlInterceptor) {
        this.permisoOpcionInterceptor = permisoOpcionInterceptor;
        this.sesionPermisosInterceptor = sesionPermisosInterceptor;
        this.sesionInactividadInterceptor = sesionInactividadInterceptor;
        this.sesionControlInterceptor = sesionControlInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry interceptorRegistry) {
        // Lo primero: cortar sesiones cerradas desde otro equipo y rearmar las recordadas
        // ("mantener la sesión iniciada") antes de que nadie mire si hay usuario.
        interceptorRegistry.addInterceptor(sesionControlInterceptor)
            .excludePathPatterns(
                "/api/eventos/**",
                "/api/movil/**",
                "/assets/**",
                "/css/**",
                "/js/**",
                "/iniciar-sesion/**",
                "/cerrar_sesion"
            );

        // Después: una sesión vencida por inactividad no atiende nada más.
        // El login y el cierre de sesión quedan fuera: manejan la sesión por su cuenta.
        interceptorRegistry.addInterceptor(sesionInactividadInterceptor)
            .excludePathPatterns(
                "/api/eventos/**",
                "/api/movil/**",
                "/assets/**",
                "/css/**",
                "/js/**",
                "/iniciar-sesion/**",
                "/cerrar_sesion"
            );

        // Primero: poner la sesión al día con los permisos vigentes (cambios del
        // administrador sin cerrar sesión). Los otros dos ya ven los permisos nuevos.
        interceptorRegistry.addInterceptor(sesionPermisosInterceptor)
            .excludePathPatterns(
                "/api/eventos/**",
                "/api/movil/**",
                "/assets/**",
                "/css/**",
                "/js/**",
                "/iniciar-sesion/**",
                "/cerrar_sesion"
            );

        interceptorRegistry.addInterceptor(new UsuarioAutenticadoInterceptor())
            .excludePathPatterns(
                "/api/eventos/**",
                // La app móvil no usa HttpSession: se autentica por JWT en su
                // propia cadena de seguridad (MovilSecurityConfig).
                "/api/movil/**",
                "/assets/**",
                "/css/**",
                "/js/**"
            );

        // Bloqueo por permiso de menú (Fase 3): solo aplica a las rutas del módulo.
        interceptorRegistry.addInterceptor(permisoOpcionInterceptor)
            .addPathPatterns("/administracion/**");
    }

    @Configuration
    public class WebConfig implements WebMvcConfigurer {
        @Override
        public void addResourceHandlers(ResourceHandlerRegistry registry) {
            registry.addResourceHandler("/pdfs/activos-ajenos/**")
                    .addResourceLocations("file:pdfs/activos-ajenos/");

            // Adjuntos subidos por los módulos de baja e ingreso de bienes ajenos
            // (informe de hardware, nota del inmediato superior, fotos de activos).
            registry.addResourceHandler("/uploads/**")
                    .addResourceLocations("file:uploads/");
        }
    }

}
