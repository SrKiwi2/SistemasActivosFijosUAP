package com.usic.SistemasActivosFijosUAP.model.service.movil;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import com.usic.SistemasActivosFijosUAP.config.movil.UsuarioMovilPrincipal;

/**
 * Comprobación de permisos de la app móvil.
 *
 * <p>Los códigos {@code MOV_*} son ítems ocultos del catálogo {@code opcion_menu}
 * (ver {@code OpcionMenuSeeder}), así que se otorgan desde la <b>misma pantalla
 * de permisos por usuario que ya existe en la web</b>. No hay un sistema de
 * permisos paralelo para el móvil.
 *
 * <p>El JWT lleva una copia de los permisos para que la app pinte el menú sin
 * pedirlos, pero la decisión se toma <b>aquí, en el servidor</b>, en cada
 * llamada: el token es una caché, no la autoridad.
 */
@Component
public class PermisosMovil {

    public static final String ACCESO             = "MOV_ACCESO";
    public static final String ESCANER            = "MOV_ESCANER";
    public static final String BUSQUEDA           = "MOV_BUSQUEDA";
    public static final String INFORME            = "MOV_INFORME";
    public static final String INVENTARIO         = "MOV_INVENTARIO";
    public static final String ASIGNACIONES       = "MOV_ASIGNACIONES";
    public static final String ASIGNACIONES_SUBIR = "MOV_ASIGNACIONES_SUBIR";
    public static final String NOTIFICACIONES     = "MOV_NOTIFICACIONES";

    /**
     * Exige un permiso y devuelve el usuario de la petición.
     *
     * @throws AccessDeniedException si no lo tiene (la cadena de seguridad lo
     *         traduce a un 403 con {@code codigo: SIN_PERMISO})
     */
    public UsuarioMovilPrincipal exigir(String codigo) {
        UsuarioMovilPrincipal principal = UsuarioMovilPrincipal.actual();

        if (principal == null) {
            throw new AccessDeniedException("Sesión no válida");
        }

        // ADMINISTRADOR y SUPER USUARIO pasan siempre. Es el mismo criterio de
        // OpcionMenuServiceImpl#opcionesEfectivas y evita el autobloqueo: que un
        // catálogo mal sembrado deje al administrador fuera de su propia app.
        if (principal.esAdministrador()) {
            return principal;
        }

        if (!principal.tienePermiso(codigo)) {
            throw new AccessDeniedException("Falta el permiso " + codigo);
        }

        return principal;
    }

    /** Comprobación sin excepción, para decidir qué incluir en una respuesta. */
    public boolean puede(String codigo) {
        UsuarioMovilPrincipal principal = UsuarioMovilPrincipal.actual();
        return principal != null && (principal.esAdministrador() || principal.tienePermiso(codigo));
    }
}
