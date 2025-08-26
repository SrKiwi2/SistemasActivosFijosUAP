package com.usic.SistemasActivosFijosUAP.controller.gestion;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.usic.SistemasActivosFijosUAP.anotacion.ValidarUsuarioAutenticado;
import com.usic.SistemasActivosFijosUAP.model.IService.IAsignacionService;
import com.usic.SistemasActivosFijosUAP.model.IService.ITransferenciaService;
import com.usic.SistemasActivosFijosUAP.model.IService.IUsuarioService;
import com.usic.SistemasActivosFijosUAP.model.IService.IngresoActivoAjenoService;
import com.usic.SistemasActivosFijosUAP.model.dto.ActivoTransferenciaDTO;
import com.usic.SistemasActivosFijosUAP.model.entity.Activo;
import com.usic.SistemasActivosFijosUAP.model.entity.Asignacion;
import com.usic.SistemasActivosFijosUAP.model.entity.IngresoActivoAjeno;
import com.usic.SistemasActivosFijosUAP.model.entity.Persona;
import com.usic.SistemasActivosFijosUAP.model.entity.Responsable;
import com.usic.SistemasActivosFijosUAP.model.entity.Transferencia;
import com.usic.SistemasActivosFijosUAP.model.entity.TransferenciaDetalle;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;
import com.usic.SistemasActivosFijosUAP.model.service.interno.PdfInternoAsignacionService;
import com.usic.SistemasActivosFijosUAP.model.service.interno.PdfInternoIngresoActivoAjenoService;
import com.usic.SistemasActivosFijosUAP.model.service.interno.PdfInternoTransferenciaService;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/seguimiento-activo")
@RequiredArgsConstructor
public class SeguimientoController {

    private final IngresoActivoAjenoService ingresoActivoAjenoService;
    private final IAsignacionService asignacionService;
    private final ITransferenciaService transferenciaService;
    private final IUsuarioService usuarioService;
    
    private final PdfInternoAsignacionService pdfInternoAsignacionService;
    private final PdfInternoTransferenciaService pdfInternoTransferenciaService;
    private final PdfInternoIngresoActivoAjenoService pdfInternoIngresoActivoAjenoService;

    //ASIGNACION ACTIVOS NUEVOS
    @ValidarUsuarioAutenticado
    @GetMapping("/vista_activos_nuevos")
    public String vista_activos_nuevos() {
        return "/seguimiento/asignacion/vista";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/tabla_activos_nuevos")
    public String tabla_activos_nuevos(Model model) {
        List<Asignacion> asignaciones = asignacionService.findAll();
        model.addAttribute("asignaciones", asignaciones);
        return "/seguimiento/asignacion/tabla_registro";
    }

    @ValidarUsuarioAutenticado
    @GetMapping("/asignaciones/{id}/pdf")
    public ResponseEntity<byte[]> pdfInternoAAN(
            @PathVariable Long id, HttpServletRequest request) {
        
        Asignacion a = asignacionService.findById(id);
        Usuario usuario_encontrado = usuarioService.findById(a.getRegistroIdUsuario());

        // Datos base
        final String unidad           = nvl(a.getUnidadResponsable());
        final String hr               = nvl(a.getHr());
        final String ubicacion        = nvl(a.getUbicacionActivo());
        final String descripcion      = nvl(a.getDescripcionActivo());
 
        // Responsable / Persona (con null-safety)       =
        final Responsable resp        = a.getResponsable();
        final Persona persona         = (resp != null) ? resp.getPersona() : null;
        final String nombreCompleto   = (persona != null) ? nvl(persona.getNombreCompleto()) : "N/D";
        final String ci               = (persona != null) ? nvl(persona.getCi()) : "N/D";
        final String ext              = (persona != null) ? nvl(persona.getExtension()) : "N/D";
        final String cargoNombre      = (resp != null && resp.getCargo() != null) ? nvl(resp.getCargo().getNombre()) : "N/D";

        try {

            byte[] pdfBytes = pdfInternoAsignacionService.pdfActivoNuevo(
                usuario_encontrado,
                unidad,
                nombreCompleto,
                cargoNombre,
                ci,
                ext,
                ubicacion,
                descripcion,
                hr
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDisposition(ContentDisposition.inline().filename("asignacion_activo_nuevo_"+id+"-"+hr+".pdf").build());
            headers.setContentLength(pdfBytes.length);

            return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);

        } catch (Exception ex) {
            String errorMsg = "Error procesando: " + ex.getMessage();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorMsg.getBytes());
        }
    }

    //TRANSFERENCIA ACTIVOS
    @ValidarUsuarioAutenticado
    @GetMapping("/vista_transferencia_activos")
    public String vista_transferencia_activos() {
        return "/seguimiento/transferencia/vista";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/tabla_transferencia_activos")
    public String tabla_transferencia_activos(Model model) {
        List<Transferencia> transferencias = transferenciaService.findAllConTodo();
        model.addAttribute("transferencias", transferencias);
        return "/seguimiento/transferencia/tabla_registro";
    }

    @ValidarUsuarioAutenticado
    @GetMapping("/transferencia/{id}/pdf")
    public ResponseEntity<byte[]> pdfInternoTA(
            @PathVariable Long id, HttpServletRequest request) {
        
        Transferencia t = transferenciaService.findById(id);
        Usuario usuario_encontrado = usuarioService.findById(t.getRegistroIdUsuario());
        
        // 2) Unidades (desde los responsables y sus oficinas; fallback a "—")
        String unidadOrigen  = (t.getResponsableOrigen()  != null && t.getResponsableOrigen().getOficina()  != null)
                ? nvl(t.getResponsableOrigen().getOficina().getNombre())
                : "—";

        String unidadDestino = (t.getResponsableDestino() != null && t.getResponsableDestino().getOficina() != null)
                ? nvl(t.getResponsableDestino().getOficina().getNombre())
                : "—";
        
        // 3) Fechas formateadas
        String fechaTransferencia = fmt(t.getFechaTransferencia());
        String fechaRecepcion     = fmt(t.getFechaRecepcion());

        // 4) Armar detalle de activos para el PDF
        List<ActivoTransferenciaDTO> activos = new ArrayList<>();
        if (t.getDetalles() != null) {
            for (TransferenciaDetalle d : t.getDetalles()) {
                ActivoTransferenciaDTO dto = new ActivoTransferenciaDTO();

                // código y descripción
                Activo a = d.getActivo();
                dto.setCodigo(      a != null ? nvl(a.getCodigo())      : "—");
                dto.setDescripcion( a != null ? nvl(a.getDescripcion())  : "—");

                // ubicación origen: prioriza campo libre; si no, oficinaAnterior; si no, "—"
                String uo = d.getUbicacionOrigen();
                if (uo == null || uo.isBlank()) {
                    uo = (d.getOficinaAnterior() != null ? d.getOficinaAnterior().getNombre() : null);
                }
                dto.setUbicacionOrigen(nvl(uo));

                // ubicación actual: prioriza campo libre; si no, oficina del responsable destino; si no, "—"
                String ua = d.getUbicacionActual();
                if (ua == null || ua.isBlank()) {
                    ua = (t.getResponsableDestino() != null && t.getResponsableDestino().getOficina() != null)
                            ? t.getResponsableDestino().getOficina().getNombre()
                            : null;
                }
                dto.setUbicacionActual(nvl(ua));

                activos.add(dto);
            }
        }

        try {

            byte[] pdfBytes = pdfInternoTransferenciaService.pdfTransferenciaActivo( usuario_encontrado,
                unidadOrigen,  t.getResponsableOrigen(),  fechaTransferencia,
                unidadDestino, t.getResponsableDestino(), fechaRecepcion,
                activos
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDisposition(
                    ContentDisposition.inline()
                            .filename("transferencia_activos_" + id + ".pdf")
                            .build()
            );
            headers.setContentLength(pdfBytes.length);

            return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);

        } catch (Exception ex) {
            String errorMsg = "Error procesando: " + ex.getMessage();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorMsg.getBytes());
        }
    }

    //INGRESO ACTIVO AJENOS
    @ValidarUsuarioAutenticado
    @GetMapping("/vista_ingreso_ajeno")
    public String inicio_ingresos_ajenos() {
        return "/seguimiento/ingresos-ajenos/vista";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/tabla-registros-ajeno")
    public String tabla_ingresos(Model model) {
        List<IngresoActivoAjeno> ingresos = ingresoActivoAjenoService.findAll();
        model.addAttribute("ingresos", ingresos);
        return "/seguimiento/ingresos-ajenos/tabla_registro";
    }

    // @ValidarUsuarioAutenticado
    // @GetMapping("/ingresos/{id}/pdf")
    // public ResponseEntity<byte[]> pdfInternoIAA(
    //         @PathVariable Long id, HttpServletRequest request) {
        
    //     IngresoActivoAjeno i = ingresoActivoAjenoService.findById(id);
    //     Usuario usuario_encontrado = usuarioService.findById(i.getRegistroIdUsuario());

    //     // Datos base
    //     final String unidad           = nvl(a.getUnidadResponsable());
    //     final String hr               = nvl(a.getHr());
    //     final String ubicacion        = nvl(a.getUbicacionActivo());
    //     final String descripcion      = nvl(a.getDescripcionActivo());
 
    //     // Responsable / Persona (con null-safety)       =
    //     final Responsable resp        = a.getResponsable();
    //     final Persona persona         = (resp != null) ? resp.getPersona() : null;
    //     final String nombreCompleto   = (persona != null) ? nvl(persona.getNombreCompleto()) : "N/D";
    //     final String ci               = (persona != null) ? nvl(persona.getCi()) : "N/D";
    //     final String ext              = (persona != null) ? nvl(persona.getExtension()) : "N/D";
    //     final String cargoNombre      = (resp != null && resp.getCargo() != null) ? nvl(resp.getCargo().getNombre()) : "N/D";

    //     try {

    //         byte[] pdfBytes = pdfInternoAsignacionService.pdfActivoNuevo(
    //             usuario_encontrado,
    //             unidad,
    //             nombreCompleto,
    //             cargoNombre,
    //             ci,
    //             ext,
    //             ubicacion,
    //             descripcion,
    //             hr
    //         );

    //         HttpHeaders headers = new HttpHeaders();
    //         headers.setContentType(MediaType.APPLICATION_PDF);
    //         headers.setContentDisposition(ContentDisposition.inline().filename("asignacion_activo_nuevo_"+id+"-"+hr+".pdf").build());
    //         headers.setContentLength(pdfBytes.length);

    //         return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);

    //     } catch (Exception ex) {
    //         String errorMsg = "Error procesando: " + ex.getMessage();
    //         return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorMsg.getBytes());
    //     }
    // }

    private static String nvl(String s) {
        return (s == null) ? "" : s;
    }

    private static String fmt(LocalDate d) {
        return d != null ? d.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) : "—";
    }
}