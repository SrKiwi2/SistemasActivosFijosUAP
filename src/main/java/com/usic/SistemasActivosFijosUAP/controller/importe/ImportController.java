package com.usic.SistemasActivosFijosUAP.controller.importe;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import com.usic.SistemasActivosFijosUAP.model.service.importacion.EntidadImportService;
import com.usic.SistemasActivosFijosUAP.model.service.importacion.PredioImportService;

import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/importe")
@RequiredArgsConstructor
public class ImportController {

    private final EntidadImportService entidadImportService;
    private final PredioImportService predioImportService;

    @PostMapping("/import-entidad")
    @ResponseBody
    public ResponseEntity<?> importDbf(@RequestParam("file") MultipartFile file,
            @RequestParam(value = "charset", defaultValue = "windows-1252") String charsetName) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Archivo vacío o no enviado"));
        }
        try {
            Charset cs = Charset.forName(charsetName);
            EntidadImportService.ImportResult res = entidadImportService.importar(file, cs);
            return ResponseEntity.ok(Map.of(
                    "leidas", res.getLeidas(),
                    "insertados", res.getInsertados(),
                    "actualizados", res.getActualizados(),
                    "errores", res.getErrores()));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Error importando: " + ex.getMessage()));
        }
    }

    @PostMapping("/import-unidadadmin")
    @ResponseBody
    public ResponseEntity<?> importUnidadAdmin(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "charset", defaultValue = "windows-1252") String charset,
            @RequestParam(value = "gestion", required = false) Short gestionPreferida) {

        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Archivo vacío o no enviado"));
        }
        try {
            var res = predioImportService.importarUnidadAdmin(file, Charset.forName(charset), gestionPreferida);
            return ResponseEntity.ok(Map.of(
                    "leidas", res.getLeidas(),
                    "insertados", res.getInsertados(),
                    "actualizados", res.getActualizados(),
                    "errores", res.getErrores()));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Error importando: " + ex.getMessage()));
        }
    }

}
