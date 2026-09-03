package com.usic.SistemasActivosFijosUAP.controller.report;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.usic.SistemasActivosFijosUAP.anotacion.ValidarUsuarioAutenticado;
import com.usic.SistemasActivosFijosUAP.config.Encriptar;
import com.usic.SistemasActivosFijosUAP.controller.formularios.PdfAsignacionActivoCompleto;
import com.usic.SistemasActivosFijosUAP.controller.formularios.WordAsignacionActivoService;
import com.usic.SistemasActivosFijosUAP.model.IService.IActivoService;
import com.usic.SistemasActivosFijosUAP.model.IService.IAsignacionActivoService;
import com.usic.SistemasActivosFijosUAP.model.IService.IConfiguracionGestionService;
import com.usic.SistemasActivosFijosUAP.model.IService.IUsuarioService;
import com.usic.SistemasActivosFijosUAP.model.dao.IHistorialActivoDao;
import com.usic.SistemasActivosFijosUAP.model.dto.FiltrosAsignacionDTO;
import com.usic.SistemasActivosFijosUAP.model.entity.Activo;
import com.usic.SistemasActivosFijosUAP.model.entity.AsignacionActivo;
import com.usic.SistemasActivosFijosUAP.model.entity.ConfiguracionGestion;
import com.usic.SistemasActivosFijosUAP.model.entity.DetalleAsignacionActivo;
import com.usic.SistemasActivosFijosUAP.model.entity.Oficina;
import com.usic.SistemasActivosFijosUAP.model.entity.Responsable;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;
import com.usic.SistemasActivosFijosUAP.model.service.ExcelAsignacionReportService;
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
    private final ExcelAsignacionReportService excelAsignacionReportService;

    // Tipo MIME para documentos Word (.docx)
    private static final String DOCX_MIME = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final String XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final List<Integer> TAMANOS_PAGINA_REPORTE = List.of(10, 25, 50, 100);

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

    /* ════════════════════════════════════════════════════════════════════════════
     * REPORTE DE ASIGNACIONES (Excel)
     *
     * Pantalla propia bajo Reportes: lista actas con sus datos administrativos
     * (hoja de ruta, certificación, sistematizado por, comprobante, observación),
     * permite completarlos en actas que ya existían antes de este módulo, y exporta
     * a Excel una acta, el conjunto filtrado, o todo (sin filtros).
     * ════════════════════════════════════════════════════════════════════════════ */

    @ValidarUsuarioAutenticado
    @GetMapping("/asignaciones/vista")
    public String vistaReporteAsignaciones(Model model) {
        model.addAttribute("gestiones", asignacionActivoService.gestionesConActas());
        model.addAttribute("usuarios", usuarioService.findAll());
        return "report/asignaciones_reporte";
    }

    @ValidarUsuarioAutenticado
    @GetMapping(value = "/asignaciones/datos", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<?> datosReporteAsignaciones(@ModelAttribute FiltroReporteParams p,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "25") int tamano) {
        try {
            if (!TAMANOS_PAGINA_REPORTE.contains(tamano)) tamano = 25;
            if (pagina < 0) pagina = 0;

            Page<AsignacionActivo> resultado = asignacionActivoService.buscarConFiltros(
                    filtrosDe(p), p.getOrden(), descOrDefault(p), PageRequest.of(pagina, tamano));

            List<Long> ids = resultado.getContent().stream()
                    .map(AsignacionActivo::getIdAsignacionActivo).toList();

            List<AsignacionActivo> completas = asignacionActivoService.findAllByIdInConDetalles(ids);
            Map<Long, AsignacionActivo> completasPorId = completas.stream()
                    .collect(Collectors.toMap(AsignacionActivo::getIdAsignacionActivo, a -> a));
            Map<Long, String> nombresPorUsuario = resolverNombresUsuarios(completas);

            List<Map<String, Object>> filas = new ArrayList<>();
            for (Long id : ids) {
                AsignacionActivo a = completasPorId.get(id);
                if (a != null) filas.add(mapaActaReporte(a, nombresPorUsuario));
            }

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("ok", true);
            body.put("filas", filas);
            body.put("pagina", resultado.getNumber());
            body.put("totalPaginas", resultado.getTotalPages());
            body.put("totalElementos", resultado.getTotalElements());
            return ResponseEntity.ok(body);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError()
                    .body(Map.of("ok", false, "msg", "No se pudo cargar el reporte: " + e.getMessage()));
        }
    }

    /** Excel de todas las actas que cumplen el filtro — sin filtros, es "todo"; con filtros, es "por rango". */
    @ValidarUsuarioAutenticado
    @GetMapping("/asignaciones/exportar")
    public ResponseEntity<byte[]> exportarReporteAsignaciones(@ModelAttribute FiltroReporteParams p) {
        try {
            List<AsignacionActivo> actas = asignacionActivoService
                    .buscarConFiltrosConDetalles(filtrosDe(p), p.getOrden(), descOrDefault(p));
            if (actas.isEmpty()) return ResponseEntity.noContent().build();

            byte[] xlsx = excelAsignacionReportService.generar(actas, resolverNombresUsuarios(actas));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(XLSX_MIME));
            headers.setContentDispositionFormData("attachment",
                    "reporte_asignaciones_" + LocalDate.now() + ".xlsx");
            return new ResponseEntity<>(xlsx, headers, HttpStatus.OK);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }

    /** Excel de una sola acta, con el mismo layout que el reporte general. */
    @ValidarUsuarioAutenticado
    @GetMapping("/asignaciones/{idEnc}/exportar")
    public ResponseEntity<byte[]> exportarUnaAsignacion(@PathVariable String idEnc) {
        try {
            Long id = Long.parseLong(Encriptar.decrypt(idEnc));
            List<AsignacionActivo> actas = asignacionActivoService.findAllByIdInConDetalles(List.of(id));
            if (actas.isEmpty()) return ResponseEntity.notFound().build();

            AsignacionActivo asignacion = actas.get(0);
            byte[] xlsx = excelAsignacionReportService.generar(actas, resolverNombresUsuarios(actas));

            String nombre = asignacion.getNumeroAsignacion() != null
                    ? asignacion.getNumeroAsignacion() : String.valueOf(asignacion.getIdAsignacionActivo());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(XLSX_MIME));
            headers.setContentDispositionFormData("attachment",
                    limpiarNombreArchivo("asignacion_" + nombre) + ".xlsx");
            return new ResponseEntity<>(xlsx, headers, HttpStatus.OK);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }

    private FiltrosAsignacionDTO filtrosDe(FiltroReporteParams p) {
        return FiltrosAsignacionDTO.normalizar(
                p.getTipo(), p.getEstado(), p.getBuscar(), p.getDesde(), p.getHasta(), p.getSincronizacion(),
                p.getGestion(), p.getIdResponsable(), p.getSoloConError(),
                p.getOficina(), p.getIdUsuarioRegistro(), p.getComprobante());
    }

    private boolean descOrDefault(FiltroReporteParams p) {
        return p.getDesc() == null || p.getDesc();
    }

    /**
     * "Sistematizado por" para el reporte: quien registró el acta
     * ({@code registroIdUsuario}), resuelto a nombre en una sola consulta por lote — no
     * es un dato propio de {@code AsignacionActivo}, ver la nota en esa entidad.
     */
    private Map<Long, String> resolverNombresUsuarios(List<AsignacionActivo> actas) {
        Set<Long> ids = actas.stream()
                .map(AsignacionActivo::getRegistroIdUsuario)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) return Map.of();
        return usuarioService.findAllByIdUsuarioIn(ids).stream()
                .collect(Collectors.toMap(Usuario::getIdUsuario,
                        u -> u.getPersona() != null ? u.getPersona().getNombreCompleto() : u.getUsuario()));
    }

    /** Fila del reporte: datos del acta + sus bienes vigentes (código y descripción). */
    private Map<String, Object> mapaActaReporte(AsignacionActivo a, Map<Long, String> nombresPorUsuario) throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        // "id" (cifrado) es para exportar esta acta sola; "idPlano" es el que espera
        // /administracion/asignacion/asignaciones/{id}/editar-reporte — ese endpoint
        // vive en CAsignacionActivoController y ahí, como en sus vecinos (editar-cabecera,
        // separar, trasladar), el id NO va cifrado.
        m.put("id", Encriptar.encrypt(String.valueOf(a.getIdAsignacionActivo())));
        m.put("idPlano", a.getIdAsignacionActivo());
        m.put("numero", a.getNumeroAsignacion());
        m.put("fecha", a.getFechaAsignacion() != null ? a.getFechaAsignacion().toLocalDate().toString() : null);
        m.put("hojaRuta", a.getHojaRuta());
        m.put("certificacion", a.getCertificacion());
        m.put("prev", a.getCodigoCompletoNormalizado());
        m.put("sistematizadoPor", nombresPorUsuario.get(a.getRegistroIdUsuario()));
        m.put("comprobante", a.getComprobante());
        m.put("oficina", a.getOficinaDestino() != null ? a.getOficinaDestino().getNombre() : null);

        Responsable resp = a.getResponsable();
        m.put("nombreCompleto", resp != null && resp.getPersona() != null ? resp.getPersona().getNombreCompleto() : null);
        m.put("ci", resp != null && resp.getPersona() != null ? resp.getPersona().getCi() : null);
        m.put("cargo", resp != null && resp.getCargo() != null ? resp.getCargo().getNombre() : null);
        m.put("observacion", a.getObservacion());

        List<Map<String, Object>> detalles = new ArrayList<>();
        if (a.getDetalles() != null) {
            for (DetalleAsignacionActivo d : a.getDetalles()) {
                if (!d.estaVigente()) continue;
                Map<String, Object> dm = new LinkedHashMap<>();
                dm.put("codigo", d.getCodigoActivoSnapshot() != null
                        ? d.getCodigoActivoSnapshot() : (d.getActivo() != null ? d.getActivo().getCodigo() : null));
                dm.put("descripcion", d.getDescripcionActivoSnapshot() != null
                        ? d.getDescripcionActivoSnapshot() : (d.getActivo() != null ? d.getActivo().getDescripcion() : null));
                detalles.add(dm);
            }
        }
        m.put("detalles", detalles);
        return m;
    }

    /** Filtros del reporte de asignaciones, tal como llegan por query string (GET). */
    @lombok.Getter @lombok.Setter
    public static class FiltroReporteParams {
        private String tipo;
        private String estado;
        private String buscar;
        private String desde;
        private String hasta;
        private String sincronizacion;
        private Integer gestion;
        private Long idResponsable;
        private Boolean soloConError;
        private String oficina;
        private Long idUsuarioRegistro;
        private Boolean comprobante;
        private String orden;
        private Boolean desc;
    }

}
