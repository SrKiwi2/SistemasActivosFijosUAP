package com.usic.SistemasActivosFijosUAP.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class MvcConfig implements WebMvcConfigurer{

    private final PermisoOpcionInterceptor permisoOpcionInterceptor;

    public MvcConfig(PermisoOpcionInterceptor permisoOpcionInterceptor) {
        this.permisoOpcionInterceptor = permisoOpcionInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry interceptorRegistry) {
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
