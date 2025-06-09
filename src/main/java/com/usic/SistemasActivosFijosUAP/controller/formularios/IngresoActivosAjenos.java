package com.usic.SistemasActivosFijosUAP.controller.formularios;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;


@Controller
@RequestMapping("/ingreso")
@RequiredArgsConstructor
public class IngresoActivosAjenos {
    @PostMapping("/registrar")
    public ResponseEntity<byte[]> ingresoActivosAjenos(
        @RequestParam String fechaIngresoActivo
        
        ) {
        
        try{
            byte[] pdfBytes = pdfTransferenciaService.generarPdfTransferencia(
                fechaIngresoActivo
            );


            HttpHeaders headers1 = new HttpHeaders();
            headers1.setContentType(MediaType.APPLICATION_PDF);
            headers1.setContentDisposition(ContentDisposition.inline().filename("asignacion_activo_nuevo.pdf").build());

            headers1.setContentLength(pdfBytes.length);

            return new ResponseEntity<>(pdfBytes, headers1, HttpStatus.OK);
        } catch (Exception ex) {
            ex.printStackTrace(); // Esto mostrará el error real en consola
            String errorMsg = "Error procesando: " + ex.getMessage();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorMsg.getBytes());
        }
    }
    
}