package com.usic.SistemasActivosFijosUAP.controller.report;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
import com.usic.SistemasActivosFijosUAP.controller.formularios.WordAsignacionActivoService;
import com.usic.SistemasActivosFijosUAP.model.IService.IActivoService;
import com.usic.SistemasActivosFijosUAP.model.IService.IAsignacionActivoService;
import com.usic.SistemasActivosFijosUAP.model.IService.IConfiguracionGestionService;
import com.usic.SistemasActivosFijosUAP.model.IService.IUsuarioService;
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
    private final WordAsignacionActivoService wordAsignacionActivoService;
    private final TransferenciaService transferenciaService;
    private final IUsuarioService usuarioService;

    // Tipo MIME para documentos Word (.docx)
    private static final String DOCX_MIME = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

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
 
            /*
             * Se reutiliza el acta que ya tengan estos activos en vez de crear una nueva
             * siempre. Antes acá se hacía `new AsignacionActivo()` sin mirar: volver a
             * generar el acta de los mismos bienes dejaba dos registros con el mismo
             * preventivo y los mismos activos, y a partir de ahí los conteos y el
             * historial contaban doble.
             */
            AsignacionActivo asignacion = asignacionActivoService
                    .findByActivo(activos.get(0))
                    .orElse(null);
            boolean actaNueva = (asignacion == null);

            if (actaNueva) {
                asignacion = new AsignacionActivo();
                asignacion.setFechaAsignacion(LocalDateTime.now());
                asignacion.setTipoAsignacion("NUEVA");
                asignacion.setEstadoAsignacion("ACTIVA");
                asignacion.setRegistroIdUsuario(usuId);
                asignacion.setEstado("ACTIVO");
            }
            // El número del acta sale del número de documento, no de un correlativo del
            // sistema. Se guarda sin paréntesis: los agrega el acta al imprimirse.
            asignacion.asignarDocumento(config.getGestion(), config.getPrefijoDocumento(), nroPreventivo);
            asignacion.setResponsable(resp);
            asignacion.setOficinaDestino(oficinaDestino);

            if (actaNueva) {
                List<DetalleAsignacionActivo> detalles = new ArrayList<>();
                for (Activo a : activos) {
                    DetalleAsignacionActivo d = new DetalleAsignacionActivo();
                    d.setAsignacionActivo(asignacion);
                    d.setActivo(a);
                    d.setCodigoActivoSnapshot(a.getCodigo());
                    d.setRegistroIdUsuario(usuId);
                    d.setEstado("ACTIVO");
                    d.setEstadoDetalle(DetalleAsignacionActivo.VIGENTE);
                    detalles.add(d);
                }
                asignacion.setDetalles(detalles);
            }

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
 
            String nombreUsuario = (usuario != null && usuario.getPersona() != null)
                    ? usuario.getPersona().getNombreCompleto() : null;

            byte[] docxBytes = wordAsignacionActivoService.generarActaAsignacion(asignacion, config, nombreUsuario);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(DOCX_MIME));
            headers.setContentDispositionFormData("attachment", construirNombreArchivo(asignacion) + ".docx");

            return new ResponseEntity<>(docxBytes, headers, HttpStatus.OK);
 
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

            String nombreUsuario = null;
            if (asignacion.getRegistroIdUsuario() != null) {
                nombreUsuario = usuarioService.findByIdUsuario(asignacion.getRegistroIdUsuario())
                        .map(u -> u.getPersona() != null ? u.getPersona().getNombreCompleto() : null)
                        .orElse(null);
            }

            byte[] docxBytes = wordAsignacionActivoService
                    .generarActaAsignacion(asignacion, config, nombreUsuario);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(DOCX_MIME));
            headers.setContentDispositionFormData("attachment",
                    construirNombreArchivo(asignacion) + ".docx");

            return new ResponseEntity<>(docxBytes, headers, HttpStatus.OK);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Nombre del archivo (sin extensión) de un acta de asignación:
     * {@code asignacion_<dd-MM-yyyy>_<codigoOficina> <nombreOficina>_<n auxiliar...>_<responsable>}.
     * Ej: {@code asignacion_02-07-2026_124 LABORATORIO HCP_3 CPU 4 MONITORES 2 DATASHOW_JUAN PEREZ}.
     * La fecha corresponde a {@code fechaAsignacion} (momento en que se registra la asignación).
     */
    private String construirNombreArchivo(AsignacionActivo asignacion) {
        Oficina oficina = asignacion.getOficinaDestino();
        String codOfi    = (oficina != null && oficina.getCodOfi() != null) ? String.valueOf(oficina.getCodOfi()) : "";
        String nombreOfi = (oficina != null && oficina.getNombre() != null) ? oficina.getNombre().trim() : "";
        String oficinaSeg = codOfi.isEmpty() ? nombreOfi : (codOfi + " " + nombreOfi);

        // Conteo por auxiliar preservando el orden de aparición: "3 CPU 4 MONITORES 2 DATASHOW"
        Map<String, Integer> conteo = new LinkedHashMap<>();
        if (asignacion.getDetalles() != null) {
            for (DetalleAsignacionActivo d : asignacion.getDetalles()) {
                Activo a = d.getActivo();
                if (a == null || a.getAuxiliar() == null) continue;
                String nom = a.getAuxiliar().getNombre();
                if (nom == null || nom.isBlank()) continue;
                conteo.merge(nom.trim().toUpperCase(), 1, Integer::sum);
            }
        }
        StringBuilder auxSeg = new StringBuilder();
        for (Map.Entry<String, Integer> e : conteo.entrySet()) {
            if (auxSeg.length() > 0) auxSeg.append(" ");
            auxSeg.append(e.getValue()).append(" ").append(e.getKey());
        }

        String responsable = (asignacion.getResponsable() != null && asignacion.getResponsable().getPersona() != null)
                ? asignacion.getResponsable().getPersona().getNombreCompleto() : "";

        // Fecha de registro de la asignación en formato dd-MM-yyyy.
        // fechaAsignacion se establece con LocalDateTime.now() al registrar la asignación.
        String fechaSeg = "";
        if (asignacion.getFechaAsignacion() != null) {
            fechaSeg = asignacion.getFechaAsignacion().format(DateTimeFormatter.ofPattern("dd-MM-yyyy"));
        } else if (asignacion.getRegistro() != null) {
            fechaSeg = new java.text.SimpleDateFormat("dd-MM-yyyy").format(asignacion.getRegistro());
        }

        String base = "asignacion_" + fechaSeg + "_" + oficinaSeg + "_" + auxSeg + "_" + responsable;
        return limpiarNombreArchivo(base);
    }

    /** Quita acentos y caracteres inválidos para nombres de archivo, colapsando espacios. */
    private String limpiarNombreArchivo(String s) {
        if (s == null) return "";
        String sinAcentos = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}", "");
        return sinAcentos
                .replaceAll("[\\\\/:*?\"<>|\\r\\n\\t]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

}
