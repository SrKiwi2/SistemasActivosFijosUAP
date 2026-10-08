package com.usic.SistemasActivosFijosUAP.config;

import java.util.List;
import java.util.Set;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import com.usic.SistemasActivosFijosUAP.model.IService.IOpcionMenuService;
import com.usic.SistemasActivosFijosUAP.model.dto.MenuNodoDto;
import com.usic.SistemasActivosFijosUAP.model.service.seguridad.PermisosDatosActivos;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/**
 * Expone {@code menuArbol} (el árbol del sidebar ya filtrado por los permisos de
 * la sesión) a todas las vistas, para que el sidebar se renderice desde la BD.
 */
@ControllerAdvice
public class MenuModelAdvice {

    @ModelAttribute("puedeVerFinanzasActivo")
    public boolean puedeVerFinanzasActivo(HttpServletRequest request) {
        return PermisosDatosActivos.puedeVerFinanzas(request);
    }

    private final IOpcionMenuService opcionMenuService;

    public MenuModelAdvice(IOpcionMenuService opcionMenuService) {
        this.opcionMenuService = opcionMenuService;
    }

    @ModelAttribute("menuArbol")
    public List<MenuNodoDto> menuArbol(HttpSession session) {
        Object rolAttr = (session != null) ? session.getAttribute("nombre_rol") : null;

        // ADMIN ve siempre el árbol completo vigente (sin depender de la sesión),
        // así una opción recién creada aparece sin necesidad de re-loguear.
        if ("ADMINISTRADOR".equals(rolAttr)) {
            return opcionMenuService.obtenerMenuVisibleCompleto();
        }

        Object attr = (session != null) ? session.getAttribute("opciones") : null;
        @SuppressWarnings("unchecked")
        Set<String> opciones = (attr instanceof Set) ? (Set<String>) attr : Set.of();
        return opcionMenuService.obtenerMenuVisible(opciones);
    }
}
