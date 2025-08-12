package com.usic.SistemasActivosFijosUAP.controller.publico;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class PublicoController {
    @GetMapping(value = "/")
    public String inicioPublico() {
        return "publico/inicio_publico";
    }

    @GetMapping("/informacion")
    public String informacion() {
        return "publico/informacion";
    }
}
