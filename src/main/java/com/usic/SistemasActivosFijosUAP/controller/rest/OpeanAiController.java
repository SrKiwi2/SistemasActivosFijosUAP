package com.usic.SistemasActivosFijosUAP.controller.rest;

import java.io.IOException;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import com.usic.SistemasActivosFijosUAP.model.service.AiDescripcionService;

import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/openai")
@RequiredArgsConstructor
public class OpeanAiController {

    private final AiDescripcionService aiDescripcionService;
    
    @PostMapping("/analizar-descripcion")
    public ResponseEntity<Map<String, String>> analizarDescripcion(@RequestBody String descripcion) {
        try {
            Map<String, String> resultado = aiDescripcionService.analizarDescripcion(descripcion);
            return ResponseEntity.ok(resultado);
        } catch (IOException e) {
            // Puedes loguear el error también
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                 .body(Map.of("error", "Error al analizar descripción con AI."));
        }
    }
}
