package com.usic.SistemasActivosFijosUAP.controller.menu;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * El menú lateral solo (los {@code <li>}), para reemplazarlo en vivo cuando cambian los
 * permisos del usuario o el catálogo (sciaf-menu-vivo.js). El árbol ya filtrado lo pone
 * {@code MenuModelAdvice} en el modelo, igual que para la página completa, y la sesión ya
 * viene puesta al día por {@code SesionPermisosInterceptor}.
 */
@Controller
@RequestMapping("/adm/menu")
public class MenuVivoController {

    @GetMapping("/items")
    public String items(HttpServletRequest request, HttpServletResponse response) throws Exception {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("usuario") == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Sesión expirada");
            return null;
        }
        response.setHeader("Cache-Control", "no-store");
        return "layout/sidebar :: menuItems";
    }
}
