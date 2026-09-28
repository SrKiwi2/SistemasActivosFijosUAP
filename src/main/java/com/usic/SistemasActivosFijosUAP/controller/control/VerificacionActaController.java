package com.usic.SistemasActivosFijosUAP.controller.control;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.usic.SistemasActivosFijosUAP.model.dto.control.ActaFaltanteDTO;
import com.usic.SistemasActivosFijosUAP.model.service.control.ActaFaltanteService;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * Página pública a la que lleva el QR del acta de faltantes. Sin sesión: la abre quien
 * tenga el papel, desde el celular. Muestra el acta tal como se emitió y su estado, para
 * comparar con el papel. La ruta está en el {@code permitAll} de {@code SeguridadConfig}.
 * <p>
 * El C.I. se muestra enmascarado: la página es pública para quien tenga el token.
 */
@Controller
@RequiredArgsConstructor
public class VerificacionActaController {

    private final ActaFaltanteService actaService;

    @GetMapping("/verificar/acta-faltantes/{token}")
    public String verificar(@PathVariable String token, Model model, HttpServletResponse response) {
        ActaFaltanteDTO acta = actaService.porToken(token);
        if (acta == null) {
            response.setStatus(HttpStatus.NOT_FOUND.value());
        }
        model.addAttribute("acta", acta);
        model.addAttribute("ciOculto", acta != null ? enmascarar(acta.personaCi()) : null);
        return "publico/verificarActaFaltantes";
    }

    /** 4201103 → •••••103 */
    static String enmascarar(String ci) {
        if (ci == null || ci.isBlank()) return null;
        String t = ci.trim();
        if (t.length() <= 3) return "•••";
        return "•".repeat(t.length() - 3) + t.substring(t.length() - 3);
    }
}
