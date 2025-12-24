package com.usic.SistemasActivosFijosUAP.controller.report;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.usic.SistemasActivosFijosUAP.controller.formularios.PdfAsignacionActivoCompleto;
import com.usic.SistemasActivosFijosUAP.model.IService.IActivoService;
import com.usic.SistemasActivosFijosUAP.model.IService.IAsignacionActivoService;
import com.usic.SistemasActivosFijosUAP.model.IService.IConfiguracionGestionService;
import com.usic.SistemasActivosFijosUAP.model.entity.Activo;
import com.usic.SistemasActivosFijosUAP.model.entity.AsignacionActivo;
import com.usic.SistemasActivosFijosUAP.model.entity.ConfiguracionGestion;
import com.usic.SistemasActivosFijosUAP.model.entity.DetalleAsignacionActivo;
import com.usic.SistemasActivosFijosUAP.model.entity.Responsable;

import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/reportes")
@RequiredArgsConstructor
public class ReportesController {
    
    private final IActivoService activoService;
    private final IAsignacionActivoService asignacionActivoService;
    private final IConfiguracionGestionService configuracionGestionService;
    private final PdfAsignacionActivoCompleto pdfAsignacionActivoCompleto;

    @PostMapping("/generar-asignacion")
    public ResponseEntity<byte[]> generarReporte(
            @RequestParam("ids") List<Long> ids,
            @RequestParam("nroPreventivo") String nroPreventivo) {

        try {
            // 1. Validar Activos
            List<Activo> activos = activoService.findAllById(ids);
            if(activos.isEmpty()) throw new RuntimeException("Sin activos seleccionados");
            
            // Asumimos que todos van al mismo responsable (tomamos el del primero)
            Responsable resp = activos.get(0).getResponsable();

            // 2. Obtener/Crear Configuración del Año
            int anio = LocalDate.now().getYear();
            ConfiguracionGestion config = configuracionGestionService.findByGestion(anio)
                .orElseGet(() -> {
                    ConfiguracionGestion c = new ConfiguracionGestion();
                    c.setGestion(anio);
                    c.setPrefijoDocumento("PREV."); // Por defecto
                    c.setCiudad("Cobija");
                    c.setResponsableActivosNombre("Lic. Verónica Layme Cori"); // Por defecto
                    return configuracionGestionService.save(c);
                });

            // 3. Crear Registro Histórico (Asignación)
            AsignacionActivo asignacion = new AsignacionActivo();
            asignacion.setCodigoDocumento(nroPreventivo);
            // Concatenar distintivo + número (Ej: "PREV. 1234")
            asignacion.setCodigoCompleto(config.getPrefijoDocumento() + " " + nroPreventivo);
            asignacion.setFechaAsignacion(LocalDateTime.now());
            asignacion.setResponsable(resp);
            
            // Crear detalles
            List<DetalleAsignacionActivo> detalles = new ArrayList<>();
            for (Activo a : activos) {
                DetalleAsignacionActivo d = new DetalleAsignacionActivo();
                d.setAsignacionActivo(asignacion);
                d.setActivo(a);
                d.setCodigoActivoSnapshot(a.getCodigo());
                detalles.add(d);
            }
            asignacion.setDetalles(detalles);

            asignacionActivoService.save(asignacion); // Guardar en BD

            // 4. Generar PDF con iText 5
            byte[] pdfBytes = pdfAsignacionActivoCompleto.generarActaAsignacion(asignacion, config);

            // 5. Descargar
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            String filename = "Acta_" + nroPreventivo + ".pdf";
            headers.setContentDispositionFormData("attachment", filename);

            return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }

}
