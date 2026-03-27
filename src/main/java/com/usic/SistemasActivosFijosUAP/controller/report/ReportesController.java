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

import com.usic.SistemasActivosFijosUAP.config.Encriptar;
import com.usic.SistemasActivosFijosUAP.controller.formularios.PdfAsignacionActivoCompleto;
import com.usic.SistemasActivosFijosUAP.model.IService.IActivoService;
import com.usic.SistemasActivosFijosUAP.model.IService.IAsignacionActivoService;
import com.usic.SistemasActivosFijosUAP.model.IService.IConfiguracionGestionService;
import com.usic.SistemasActivosFijosUAP.model.dao.IHistorialActivoDao;
import com.usic.SistemasActivosFijosUAP.model.entity.Activo;
import com.usic.SistemasActivosFijosUAP.model.entity.AsignacionActivo;
import com.usic.SistemasActivosFijosUAP.model.entity.ConfiguracionGestion;
import com.usic.SistemasActivosFijosUAP.model.entity.DetalleAsignacionActivo;
import com.usic.SistemasActivosFijosUAP.model.entity.Oficina;
import com.usic.SistemasActivosFijosUAP.model.entity.Responsable;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;
import com.usic.SistemasActivosFijosUAP.model.service.TransferenciaService;

import io.swagger.v3.oas.annotations.parameters.RequestBody;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/reportes")
@RequiredArgsConstructor
public class ReportesController {
    
    private final IActivoService activoService;
    private final IAsignacionActivoService asignacionActivoService;
    private final IConfiguracionGestionService configuracionGestionService;
    private final PdfAsignacionActivoCompleto pdfAsignacionActivoCompleto;
    private final TransferenciaService transferenciaService;

    @PostMapping("/generar-asignacion")
    public ResponseEntity<byte[]> generarReporte(
            @RequestParam("ids") List<String> idsEnc,
            @RequestParam("nroPreventivo") String nroPreventivo,
            HttpServletRequest request) {
 
        Usuario usuario  = (Usuario) request.getSession().getAttribute("usuario");
        String usuNombre = (usuario != null) ? usuario.getUsuario() : "SISTEMA";
        Long   usuId     = (usuario != null) ? usuario.getIdUsuario() : null;
 
        try {
            List<Long> ids = new ArrayList<>();
            for (String enc : idsEnc) {
                try {
                    ids.add(Long.parseLong(Encriptar.decrypt(enc)));
                } catch (Exception e) {
                    System.err.println("Error desencriptando ID: " + enc);
                }
            }
 
            List<Activo> activos = activoService.findAllById(ids);
            if (activos.isEmpty()) throw new RuntimeException("Sin activos seleccionados");
 
            Responsable resp = activos.get(0).getResponsable();
            Oficina oficinaDestino = activos.get(0).getOficina();
 
            int anio = LocalDate.now().getYear();
            ConfiguracionGestion config = configuracionGestionService.findByGestion(anio)
                .orElseGet(() -> {
                    ConfiguracionGestion c = new ConfiguracionGestion();
                    c.setGestion(anio);
                    c.setPrefijoDocumento("-");
                    c.setCiudad("Cobija");
                    c.setResponsableActivosNombre(resp.getPersona().getNombreCompleto());
                    return configuracionGestionService.save(c);
                });
 
            AsignacionActivo asignacion = new AsignacionActivo();
            asignacion.setCodigoDocumento(nroPreventivo);
            asignacion.setCodigoCompleto(config.getPrefijoDocumento() + " " + nroPreventivo);
            asignacion.setFechaAsignacion(LocalDateTime.now());
            asignacion.setResponsable(resp);
            asignacion.setRegistroIdUsuario(usuario.getIdUsuario());
            asignacion.setOficinaDestino(oficinaDestino);
            asignacion.setEstado("ACTIVO");
 
            List<DetalleAsignacionActivo> detalles = new ArrayList<>();
            for (Activo a : activos) {
                DetalleAsignacionActivo d = new DetalleAsignacionActivo();
                d.setAsignacionActivo(asignacion);
                d.setActivo(a);
                d.setCodigoActivoSnapshot(a.getCodigo());
                d.setRegistroIdUsuario(usuario.getIdUsuario());
                d.setEstado("ACTIVO");
                detalles.add(d);
            }
            asignacion.setDetalles(detalles);
 
            asignacionActivoService.save(asignacion);
 
            for (Activo activo : activos) {
 
                Oficina     ofAnterior   = activo.getOficina();
                Responsable respAnterior = activo.getResponsable();
 
                if (respAnterior == null || !respAnterior.getIdResponsable().equals(resp.getIdResponsable())) {
                    activo.setResponsable(resp);
                    activoService.save(activo);
                }
 
                String desc = String.format(
                    "Activo asignado a '%s' | Doc: %s | Por: %s",
                    resp.getPersona().getNombreCompleto(),
                    nroPreventivo,
                    usuNombre
                );
 
                transferenciaService.registrarHistorial(
                    activo,
                    "ASIGNACION",
                    ofAnterior,          respAnterior,
                    activo.getOficina(), resp,
                    desc,
                    usuId, usuNombre
                );
            }
 
            byte[] pdfBytes = pdfAsignacionActivoCompleto.generarActaAsignacion(asignacion, config);
 
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", "Acta_" + nroPreventivo + ".pdf");
 
            return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);
 
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }

    // Endpoint NUEVO para Vista Pendientes — recibe idAsignacionActivo ya existente
    @PostMapping("/regenerar-asignacion")
    public ResponseEntity<byte[]> regenerarReporte(
            @RequestParam("idAsignacionActivo")   String idAsignacionEnc,
            @RequestParam("codigoCompleto") String codigoCompleto) {

        try {
            Long idAsignacionActivo = Long.parseLong(Encriptar.decrypt(idAsignacionEnc));

            // Solo leer — NUNCA hacer save() aquí
            AsignacionActivo asignacion = asignacionActivoService
                    .findByIdConDetalles(idAsignacionActivo)
                    .orElseThrow(() -> new RuntimeException("Asignación no encontrada"));

            // Obtener config por año de la asignación
            int anio = asignacion.getFechaAsignacion().getYear();
            ConfiguracionGestion config = configuracionGestionService
                    .findByGestion(anio)
                    .orElseGet(() -> {
                        // Fallback: config mínima para generar el PDF
                        ConfiguracionGestion c = new ConfiguracionGestion();
                        c.setGestion(anio);
                        c.setPrefijoDocumento("");
                        c.setCiudad("—");
                        return c;
                    });

            byte[] pdfBytes = pdfAsignacionActivoCompleto
                    .generarActaAsignacion(asignacion, config);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment",
                    "Acta_" + asignacion.getCodigoDocumento() + ".pdf");

            return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }

}
