package com.usic.SistemasActivosFijosUAP.controller.importe;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.springframework.core.task.TaskExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import com.linuxense.javadbf.DBFReader;
import com.usic.SistemasActivosFijosUAP.model.service.importacion.ActualImportService;
import com.usic.SistemasActivosFijosUAP.model.service.importacion.AuxiliarImportService;
import com.usic.SistemasActivosFijosUAP.model.service.importacion.EntidadImportService;
import com.usic.SistemasActivosFijosUAP.model.service.importacion.GrupoContableImportService;
import com.usic.SistemasActivosFijosUAP.model.service.importacion.ImportProgressService;
import com.usic.SistemasActivosFijosUAP.model.service.importacion.OficinaImportService;
import com.usic.SistemasActivosFijosUAP.model.service.importacion.OrganismoFinImportService;
import com.usic.SistemasActivosFijosUAP.model.service.importacion.PredioImportService;
import com.usic.SistemasActivosFijosUAP.model.service.importacion.ResponsableImportService;

import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/importe")
@RequiredArgsConstructor
public class ImportController {

    private final EntidadImportService entidadImportService;
    private final PredioImportService predioImportService;
    private final OficinaImportService oficinaImportService;
    private final ResponsableImportService responsableImportService;
    private final GrupoContableImportService grupoContableImportService;
    private final AuxiliarImportService auxiliarImportService;
    private final OrganismoFinImportService organismoFinImportService;
    private final ActualImportService actualImportService;
    private final ImportProgressService progress;
    private final TaskExecutor taskExecutor;

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

    @PostMapping("/import-oficina")
    @ResponseBody
    public ResponseEntity<?> importOficina(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "charset", defaultValue = "windows-1252") String charset,
            @RequestParam(value = "gestion", required = false) Short gestionPreferida) {

        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Archivo vacío o no enviado"));
        }
        try {
            var res = oficinaImportService.importarOficina(file, Charset.forName(charset), gestionPreferida);
            return ResponseEntity.ok(Map.of(
                    "leidas", res.getLeidas(),
                    "insertados", res.getInsertados(),
                    "actualizados", res.getActualizados(),
                    "omitidosCampos", res.getOmitidosCampos(),
                    "omitidosSinEntidad", res.getOmitidosSinEntidad(),
                    "omitidosSinPredio", res.getOmitidosSinPredio(),
                    "erroresExcepcion", res.getErroresExcepcion(),
                    "errores", res.getErrores()));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Error importando: " + ex.getMessage()));
        }
    }

    @PostMapping("/import-responsable")
    @ResponseBody
    public ResponseEntity<?> importResponsable(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "charset", defaultValue = "windows-1252") String charset,
            @RequestParam(value = "gestion", required = false) Short gestionPreferida) {

        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Archivo vacío o no enviado"));
        }
        try {
            var res = responsableImportService.importarResponsable(file, Charset.forName(charset), gestionPreferida);
            return ResponseEntity.ok(Map.of(
                    "leidas", res.getLeidas(),
                    "insertados", res.getInsertados(),
                    "actualizados", res.getActualizados(),
                    "omitidosCampos", res.getOmitidosCampos(),
                    "omitidosSinEntidad", res.getOmitidosSinEntidad(),
                    "omitidosSinPredio", res.getOmitidosSinPredio(),
                    "omitidosSinOficina", res.getOmitidosSinOficina(),
                    "erroresExcepcion", res.getErroresExcepcion(),
                    "errores", res.getErrores()));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Error importando: " + ex.getMessage()));
        }
    }

    @PostMapping("/import-codcont")
    @ResponseBody
    public ResponseEntity<?> importarCodcont(
        @RequestParam("file") MultipartFile file,
        @RequestParam(value = "charset", defaultValue = "windows-1252") String charset) {

        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Archivo vacío o no enviado"));
        }
        try {
            var res = grupoContableImportService.importarCodcont(file, Charset.forName(charset));
            return ResponseEntity.ok(Map.of(
                "totalFisicos",       res.getTotalFisicos(),
                "leidas",             res.getLeidas(),
                "insertados",         res.getInsertados(),
                "actualizados",       res.getActualizados(),
                "marcadosBorrados",   res.getMarcadosBorrados(),
                "omitidosCampos",     res.getOmitidosCampos(),
                "erroresLectura",     res.getErroresLectura(),
                "erroresExcepcion",   res.getErroresExcepcion(),
                "errores",            res.getErrores()
            ));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", "Error importando CODCONT: " + ex.getMessage()));
        }
    }

    @PostMapping("/import-auxiliar")
    @ResponseBody
    public ResponseEntity<?> importarAuxiliar(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "charset", defaultValue = "windows-1252") String charset,
            @RequestParam(value = "gestion", required = false) Short gestion) {

        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Archivo vacío o no enviado"));
        }
        try {
            var res = auxiliarImportService.importarAuxiliar(file, Charset.forName(charset), gestion);
            return ResponseEntity.ok(res); 
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Error importando AUXILIAR: " + ex.getMessage()));
        }
    }

    @PostMapping("/import-organismo-fin")
    @ResponseBody
    public ResponseEntity<?> importarOrganismoFin(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "charset", defaultValue = "windows-1252") String charset) {

        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Archivo vacío o no enviado"));
        }
        try {
            var res = organismoFinImportService.importarOrganismoFin(file, Charset.forName(charset));
            return ResponseEntity.ok(res);
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Error importando ORGANISMO_FIN: " + ex.getMessage()));
        }
    }

    // @PostMapping("/import-actual")
    // @ResponseBody
    // public ResponseEntity<?> importarActual(
    //         @RequestParam("file") MultipartFile file,
    //         @RequestParam(value = "charset", defaultValue = "windows-1252") String charset,
    //         @RequestParam(value = "gestion", required = false) Short gestionPreferida) {

    //     if (file == null || file.isEmpty()) {
    //         return ResponseEntity.badRequest().body(Map.of("message", "Archivo vacío o no enviado"));
    //     }
    //     try {
    //         var res = actualImportService.importarActual(file, Charset.forName(charset), gestionPreferida);
    //         return ResponseEntity.ok(res);
    //     } catch (Exception ex) {
    //         return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
    //                 .body(Map.of("message", "Error importando ACTUAL: " + ex.getMessage()));
    //     }
    // }

    @PostMapping("/import-actual/start")
    public ResponseEntity<?> start(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value="charset", defaultValue="windows-1252") String charset,
            @RequestParam(value="gestion", required=false) Short gestion) throws IOException {

        // Primero, leer el header DBF para conocer el total y crear el importId
        int total;
        try (InputStream in = new BufferedInputStream(file.getInputStream());
             DBFReader reader = new DBFReader(in, Charset.forName(charset))) {
            total = reader.getRecordCount();
        }
        String importId = progress.start(total);

        // Guardar temporal y ejecutar async
        Path tmp = Files.createTempFile("import-actual-", ".dbf");
        file.transferTo(tmp.toFile());

        taskExecutor.execute(() -> {
            try {
                actualImportService.importarActual(importId, tmp, Charset.forName(charset), gestion);
                progress.finish(importId, "OK");
            } catch (Exception e) {
                progress.inc(importId, s -> s.setMensaje("ERROR: " + e.getMessage()));
                progress.finish(importId, "ERROR");
            } finally {
                try { Files.deleteIfExists(tmp); } catch (Exception ignore) {}
            }
        });

        return ResponseEntity.ok(Map.of("importId", importId, "total", total));
    }

    @GetMapping("/import-actual/status/{id}")
    public ResponseEntity<?> status(@PathVariable String id) {
        ImportProgressService.Snapshot s = progress.get(id);
        if (s == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(s);
    }

}
