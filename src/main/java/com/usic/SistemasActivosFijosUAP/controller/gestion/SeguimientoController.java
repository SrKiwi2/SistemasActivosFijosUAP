package com.usic.SistemasActivosFijosUAP.controller.gestion;

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
import com.usic.SistemasActivosFijosUAP.model.IService.IUsuarioService;
import com.usic.SistemasActivosFijosUAP.model.IService.IngresoActivoAjenoService;
import com.usic.SistemasActivosFijosUAP.model.entity.Asignacion;
import com.usic.SistemasActivosFijosUAP.model.entity.IngresoActivoAjeno;
import com.usic.SistemasActivosFijosUAP.model.entity.Persona;
import com.usic.SistemasActivosFijosUAP.model.entity.Responsable;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;
import com.usic.SistemasActivosFijosUAP.model.service.interno.PdfInternoService;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/seguimiento-activo")
@RequiredArgsConstructor
public class SeguimientoController {

    private final IngresoActivoAjenoService ingresoActivoAjenoService;
    private final IAsignacionService asignacionService;
    private final IUsuarioService usuarioService;
    
    private final PdfInternoService pdfInternoService; 

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

            byte[] pdfBytes = pdfInternoService.pdfActivoNuevo(
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

    private static String nvl(String s) {
        return (s == null) ? "" : s;
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
}