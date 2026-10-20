package com.usic.SistemasActivosFijosUAP.controller.duplicados;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.usic.SistemasActivosFijosUAP.model.dto.duplicados.DuplicadosDTOs;
import com.usic.SistemasActivosFijosUAP.model.service.duplicados.DuplicadosService;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * Página pública a la que lleva el QR de la notificación de duplicado. Sin sesión: la abre
 * quien tenga el papel. Muestra la notificación tal como se emitió y su estado. La ruta está
 * en el {@code permitAll} de {@code SeguridadConfig} ({@code /verificar/**}).
 * <p>
 * El C.I. se muestra enmascarado: la página es pública para quien tenga el token.
 */
@Controller
@RequiredArgsConstructor
public class VerificacionDuplicadoController {

    private final DuplicadosService service;

    @GetMapping("/verificar/duplicado/{token}")
    public String verificar(@PathVariable String token, Model model, HttpServletResponse response) {
        DuplicadosDTOs.Notificacion n = service.porToken(token);
        if (n == null) response.setStatus(HttpStatus.NOT_FOUND.value());
        model.addAttribute("n", n);
        model.addAttribute("ciOculto", n != null ? enmascarar(n.c().ci()) : null);
        return "publico/verificarDuplicado";
    }

    /** 4201103 → •••••103 */
    static String enmascarar(String ci) {
        if (ci == null || ci.isBlank()) return null;
        String t = ci.trim();
        if (t.length() <= 3) return "•••";
        return "•".repeat(t.length() - 3) + t.substring(t.length() - 3);
    }
}
