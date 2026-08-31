package com.usic.SistemasActivosFijosUAP.controller.Seguimieto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.usic.SistemasActivosFijosUAP.anotacion.ValidarUsuarioAutenticado;
import com.usic.SistemasActivosFijosUAP.controller.formularios.WordAsignacionActivoService;
import com.usic.SistemasActivosFijosUAP.model.IService.IAsignacionActivoService;
import com.usic.SistemasActivosFijosUAP.model.IService.IConfiguracionGestionService;
import com.usic.SistemasActivosFijosUAP.model.IService.IUsuarioService;
import com.usic.SistemasActivosFijosUAP.model.dao.IAsignacionMovimientoDao;
import com.usic.SistemasActivosFijosUAP.model.dto.FiltrosAsignacionDTO;
import com.usic.SistemasActivosFijosUAP.model.dto.ResumenAsignacionDTO;
import com.usic.SistemasActivosFijosUAP.model.entity.Activo;
import com.usic.SistemasActivosFijosUAP.model.entity.AsignacionActivo;
import com.usic.SistemasActivosFijosUAP.model.entity.ConfiguracionGestion;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;
import com.usic.SistemasActivosFijosUAP.model.service.asignacion.AsignacionEdicionService;
import com.usic.SistemasActivosFijosUAP.model.service.asignacion.EdicionCabeceraActaDTO;
import com.usic.SistemasActivosFijosUAP.model.service.asignacion.ResultadoOperacionActa;
import com.usic.SistemasActivosFijosUAP.model.service.asignacion.SeparacionActaDTO;
import com.usic.SistemasActivosFijosUAP.model.service.asignacion.TrasladoActaDTO;
import com.usic.SistemasActivosFijosUAP.config.Encriptar;

import jakarta.servlet.http.HttpServletRequest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller
@RequestMapping("/administracion/asignacion")
@RequiredArgsConstructor
public class CAsignacionActivoController {

    private final IAsignacionActivoService asignacionActivoService;
    private final IUsuarioService usuarioService;
    private final IConfiguracionGestionService configuracionGestionService;
    private final WordAsignacionActivoService wordAsignacionActivoService;
    private final AsignacionEdicionService asignacionEdicionService;
    private final IAsignacionMovimientoDao asignacionMovimientoDao;

    /** Tamaños de página permitidos. Un valor libre por parámetro sería un pedido de "traeme todo". */
    private static final List<Integer> TAMANOS_PAGINA = List.of(10, 25, 50, 100);
    private static final int TAMANO_POR_DEFECTO = 25;

    @ValidarUsuarioAutenticado
    @GetMapping("/vista")
    public String vista_activos_nuevos(Model model) {
        // El combo de gestión se llena con los años que realmente tienen actas.
        model.addAttribute("gestiones", asignacionActivoService.gestionesConActas());
        model.addAttribute("tamanosPagina", TAMANOS_PAGINA);
        return "/seguimiento/asignacion/vista";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/tabla_activos_nuevos")
    public String tabla_activos_nuevos(
        @RequestParam(required = false) String tipo,
        @RequestParam(required = false) String estado,
        @RequestParam(required = false) String buscar,
        @RequestParam(required = false) String desde,
        @RequestParam(required = false) String hasta,
        @RequestParam(required = false) String sincronizacion,
        @RequestParam(required = false) Integer gestion,
        @RequestParam(required = false) Long idResponsable,
        @RequestParam(required = false) Boolean soloConError,
        @RequestParam(required = false) String orden,
        @RequestParam(defaultValue = "true") boolean desc,
        @RequestParam(defaultValue = "0") int pagina,
        @RequestParam(defaultValue = "25") int tamano,
        Model model) {

        FiltrosAsignacionDTO filtros = FiltrosAsignacionDTO.normalizar(
                tipo, estado, buscar, desde, hasta, sincronizacion, gestion, idResponsable, soloConError);

        if (!TAMANOS_PAGINA.contains(tamano)) tamano = TAMANO_POR_DEFECTO;
        if (pagina < 0) pagina = 0;

        // Sin Sort en el Pageable: el orden lo arma el servicio, que es el único que puede
        // ordenar por cantidad de bienes y por costo (son agregados de los detalles).
        Page<AsignacionActivo> paginaActual = asignacionActivoService.buscarConFiltros(
                filtros, orden, desc, PageRequest.of(pagina, tamano));

        // Al endurecer un filtro se puede quedar parado en una página que ya no existe.
        // Devolver una tabla vacía con "página 7 de 3" haría pensar que no hay resultados.
        if (paginaActual.isEmpty() && paginaActual.getTotalElements() > 0) {
            pagina = Math.max(0, paginaActual.getTotalPages() - 1);
            paginaActual = asignacionActivoService.buscarConFiltros(
                    filtros, orden, desc, PageRequest.of(pagina, tamano));
        }

        List<AsignacionActivo> asignaciones = paginaActual.getContent();

        // 1. Recolectar IDs únicos (usamos Set para que no haya repetidos)
        Set<Long> idsUsuarios = new HashSet<>();
        for (AsignacionActivo asig : asignaciones) {
            if (asig.getRegistroIdUsuario() != null) idsUsuarios.add(asig.getRegistroIdUsuario());
            if (asig.getModificacionIdUsuario() != null) idsUsuarios.add(asig.getModificacionIdUsuario());
        }

        // 2. Buscar todos esos usuarios en una sola consulta
        // (Asegúrate de tener este método en tu usuarioService/Repository)
        List<Usuario> usuariosAuditores = usuarioService.findAllByIdUsuarioIn(idsUsuarios);

        // 3. Crear el Mapa <IdUsuario, Nombre Completo>
        Map<Long, String> mapaUsuarios = usuariosAuditores.stream()
            .collect(Collectors.toMap(
                Usuario::getIdUsuario, 
                u -> {
                    String nombreAMostrar;
                    if (u.getPersona() != null) {
                        nombreAMostrar = u.getUsuario();
                    } else {
                        nombreAMostrar = String.valueOf(u.getUsuario()); 
                    }
                    return nombreAMostrar;
                }
            ));

        // 4. Carpeta de Drive por gestión (año de la asignación), para el botón "Ver en Drive".
        //    Solo se incluye la gestión que tenga carpeta configurada.
        Set<Integer> gestiones = new HashSet<>();
        for (AsignacionActivo asig : asignaciones) {
            if (asig.getFechaAsignacion() != null) gestiones.add(asig.getFechaAsignacion().getYear());
        }
        Map<Integer, String> carpetasPorGestion = new HashMap<>();
        for (Integer g : gestiones) {
            configuracionGestionService.findByGestion(g)
                .map(ConfiguracionGestion::getCarpetaDrive)
                .filter(id -> id != null && !id.isBlank())
                .ifPresent(id -> carpetasPorGestion.put(g, id));
        }

        // 5. Totales por asignación (costo y avance hacia el VSIAF) de LA PÁGINA, en una
        //    sola consulta agregada. Antes se pedían los de todas las actas del filtro.
        Map<Long, ResumenAsignacionDTO> resumenes = asignacionActivoService.resumenPorAsignacion(
            asignaciones.stream().map(AsignacionActivo::getIdAsignacionActivo).toList());

        // 6. Enviar los datos a la vista
        model.addAttribute("asignaciones", asignaciones);
        model.addAttribute("mapaUsuarios", mapaUsuarios);
        model.addAttribute("carpetasPorGestion", carpetasPorGestion);
        model.addAttribute("resumenes", resumenes);

        // 7. Paginación, orden y tarjetas. Las tarjetas se calculan sobre el conjunto
        //    filtrado completo: contarlas en el navegador daría los totales de la página.
        model.addAttribute("paginaActual", paginaActual);
        model.addAttribute("paginasVisibles", ventanaDePaginas(paginaActual.getNumber(), paginaActual.getTotalPages()));
        model.addAttribute("tamanosPagina", TAMANOS_PAGINA);
        model.addAttribute("orden", orden != null ? orden : "fecha");
        model.addAttribute("desc", desc);
        model.addAttribute("filtros", filtros);
        model.addAttribute("stats", asignacionActivoService.resumenListado(filtros));

        return "/seguimiento/asignacion/tabla_registro";
    }

    /**
     * Separa parte de los bienes de un acta hacia un acta nueva.
     * <p>
     * Devuelve 200 con {@code ok:false} y el motivo cuando la solicitud no es válida —el
     * usuario eligió todos los bienes, olvidó el motivo, la oficina destino es de otro
     * predio—, porque son cosas que se corrigen en el formulario, no errores del servidor.
     */
    @ValidarUsuarioAutenticado
    @PostMapping(value = "/asignaciones/{id}/separar", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<?> separarActa(@PathVariable Long id,
                                         @RequestBody SeparacionActaRequest req,
                                         HttpServletRequest httpReq) {
        Usuario usuario = (Usuario) httpReq.getSession().getAttribute("usuario");
        if (usuario == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("ok", false, "msg", "Sesión expirada. Volvé a iniciar sesión."));
        }

        try {
            SeparacionActaDTO solicitud = new SeparacionActaDTO(
                    id, req.getIdsActivos(), req.getIdConfigGestion(), req.getNroDocumento(),
                    req.getIdResponsableDestino(), req.getIdOficinaDestino(), req.getMotivo());

            ResultadoOperacionActa resultado = asignacionEdicionService.separar(solicitud, usuario);

            Map<String, Object> cuerpo = new LinkedHashMap<>();
            cuerpo.put("ok", true);
            cuerpo.put("msg", resultado.mensaje());
            cuerpo.put("idActaNueva", resultado.getIdActaNueva());
            cuerpo.put("numeroActaNueva", resultado.getNumeroActaNueva());
            cuerpo.put("bienesMovidos", resultado.getBienesMovidos());
            cuerpo.put("vsiaf", resultado.getResultadoVsiaf());
            cuerpo.put("avisos", resultado.getAvisos());
            return ResponseEntity.ok(cuerpo);

        } catch (SecurityException sinPermiso) {
            log.warn("[SEPARAR] '{}' intentó separar el acta {} sin permiso.", usuario.getUsuario(), id);
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("ok", false, "msg", sinPermiso.getMessage()));
        } catch (IllegalArgumentException datoInvalido) {
            return ResponseEntity.ok(Map.of("ok", false, "msg", datoInvalido.getMessage()));
        } catch (Exception e) {
            log.error("[SEPARAR] Error separando el acta {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("ok", false, "msg", "No se pudo separar el acta: " + e.getMessage()));
        }
    }

    /**
     * Mueve bienes hacia esta acta desde donde estén.
     * <p>
     * Es el mismo endpoint para las dos entradas de la pantalla: "incorporar" cuando se
     * llama desde el acta que recibe, y "trasladar" cuando se llama desde el acta que
     * entrega eligiendo destino. El origen de cada bien lo deduce el servicio.
     */
    @ValidarUsuarioAutenticado
    @PostMapping(value = "/asignaciones/{id}/trasladar", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<?> trasladarBienes(@PathVariable Long id,
                                             @RequestBody TrasladoActaRequest req,
                                             HttpServletRequest httpReq) {
        Usuario usuario = (Usuario) httpReq.getSession().getAttribute("usuario");
        if (usuario == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("ok", false, "msg", "Sesión expirada. Volvé a iniciar sesión."));
        }

        try {
            ResultadoOperacionActa resultado = asignacionEdicionService.trasladar(
                    new TrasladoActaDTO(id, req.getIdsActivos(), req.isAdoptarDestino(), req.getMotivo()),
                    usuario);

            Map<String, Object> cuerpo = new LinkedHashMap<>();
            cuerpo.put("ok", true);
            cuerpo.put("msg", resultado.mensaje());
            cuerpo.put("bienesMovidos", resultado.getBienesMovidos());
            cuerpo.put("vsiaf", resultado.getResultadoVsiaf());
            cuerpo.put("avisos", resultado.getAvisos());
            return ResponseEntity.ok(cuerpo);

        } catch (SecurityException sinPermiso) {
            log.warn("[TRASLADAR] '{}' intentó mover bienes al acta {} sin permiso.", usuario.getUsuario(), id);
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("ok", false, "msg", sinPermiso.getMessage()));
        } catch (IllegalArgumentException datoInvalido) {
            return ResponseEntity.ok(Map.of("ok", false, "msg", datoInvalido.getMessage()));
        } catch (Exception e) {
            log.error("[TRASLADAR] Error moviendo bienes al acta {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("ok", false, "msg", "No se pudieron mover los bienes: " + e.getMessage()));
        }
    }

    /**
     * Cambia el responsable y/o la oficina de la cabecera de un acta ya emitida.
     * <p>
     * No mueve bienes ni crea un acta nueva: es la corrección directa del papel. Si el
     * pedido marca {@code propagarABienes}, el cambio también se aplica a los bienes
     * vigentes del acta.
     */
    @ValidarUsuarioAutenticado
    @PostMapping(value = "/asignaciones/{id}/editar-cabecera", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<?> editarCabecera(@PathVariable Long id,
                                            @RequestBody EdicionCabeceraRequest req,
                                            HttpServletRequest httpReq) {
        Usuario usuario = (Usuario) httpReq.getSession().getAttribute("usuario");
        if (usuario == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("ok", false, "msg", "Sesión expirada. Volvé a iniciar sesión."));
        }

        try {
            EdicionCabeceraActaDTO solicitud = new EdicionCabeceraActaDTO(
                    id, req.getIdResponsableDestino(), req.getIdOficinaDestino(),
                    req.isPropagarABienes(), req.getMotivo());

            ResultadoOperacionActa resultado = asignacionEdicionService.editarCabecera(solicitud, usuario);

            String msg = "Cabecera actualizada."
                    + (req.isPropagarABienes()
                        ? " Se aplicó a " + resultado.getBienesMovidos() + " bien(es)."
                        : "");
            if (resultado.tieneAvisos()) {
                msg += " Revisá " + resultado.getAvisos().size()
                     + (resultado.getAvisos().size() == 1 ? " advertencia." : " advertencias.");
            }

            Map<String, Object> cuerpo = new LinkedHashMap<>();
            cuerpo.put("ok", true);
            cuerpo.put("msg", msg);
            cuerpo.put("bienesActualizados", resultado.getBienesMovidos());
            cuerpo.put("vsiaf", resultado.getResultadoVsiaf());
            cuerpo.put("avisos", resultado.getAvisos());
            return ResponseEntity.ok(cuerpo);

        } catch (SecurityException sinPermiso) {
            log.warn("[EDITAR-CABECERA] '{}' intentó editar la cabecera del acta {} sin permiso.", usuario.getUsuario(), id);
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("ok", false, "msg", sinPermiso.getMessage()));
        } catch (IllegalArgumentException datoInvalido) {
            return ResponseEntity.ok(Map.of("ok", false, "msg", datoInvalido.getMessage()));
        } catch (Exception e) {
            log.error("[EDITAR-CABECERA] Error editando la cabecera del acta {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("ok", false, "msg", "No se pudo editar la cabecera: " + e.getMessage()));
        }
    }

    /** Cuerpo del pedido de edición de cabecera. */
    @lombok.Getter @lombok.Setter
    public static class EdicionCabeceraRequest {
        private Long idResponsableDestino;
        private Long idOficinaDestino;
        private boolean propagarABienes;
        private String motivo;
    }

    /**
     * Reintento manual de la sincronización con el VSIAF, por fila o por lote desde el
     * modal — para cuando un bien quedó en {@code ERROR} y ya se corrigió la causa, o el
     * sync automático no lo va a volver a tocar solo. Ver
     * {@link AsignacionEdicionService#subirAlVsiaf} para el detalle de qué se descarta y
     * por qué.
     */
    @ValidarUsuarioAutenticado
    @PostMapping(value = "/asignaciones/{id}/subir-vsiaf", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<?> subirAlVsiaf(@PathVariable Long id,
                                          @RequestBody SubirVsiafRequest req,
                                          HttpServletRequest httpReq) {
        Usuario usuario = (Usuario) httpReq.getSession().getAttribute("usuario");
        if (usuario == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("ok", false, "msg", "Sesión expirada. Volvé a iniciar sesión."));
        }
        try {
            ResultadoOperacionActa resultado = asignacionEdicionService.subirAlVsiaf(req.getIdsActivos(), usuario);

            String msg = resultado.getBienesMovidos() + (resultado.getBienesMovidos() == 1
                    ? " bien enviado al VSIAF." : " bienes enviados al VSIAF.");
            if (resultado.tieneAvisos()) {
                msg += " Revisá " + resultado.getAvisos().size()
                     + (resultado.getAvisos().size() == 1 ? " advertencia." : " advertencias.");
            }

            Map<String, Object> cuerpo = new LinkedHashMap<>();
            cuerpo.put("ok", true);
            cuerpo.put("msg", msg);
            cuerpo.put("bienesEnviados", resultado.getBienesMovidos());
            cuerpo.put("vsiaf", resultado.getResultadoVsiaf());
            cuerpo.put("avisos", resultado.getAvisos());
            return ResponseEntity.ok(cuerpo);

        } catch (SecurityException sinPermiso) {
            log.warn("[SUBIR-VSIAF] '{}' intentó subir al VSIAF desde el acta {} sin permiso.", usuario.getUsuario(), id);
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("ok", false, "msg", sinPermiso.getMessage()));
        } catch (IllegalArgumentException datoInvalido) {
            return ResponseEntity.ok(Map.of("ok", false, "msg", datoInvalido.getMessage()));
        } catch (Exception e) {
            log.error("[SUBIR-VSIAF] Error subiendo bienes del acta {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("ok", false, "msg", "No se pudo subir al VSIAF: " + e.getMessage()));
        }
    }

    /** Cuerpo del pedido de subida manual al VSIAF. */
    @lombok.Getter @lombok.Setter
    public static class SubirVsiafRequest {
        private List<Long> idsActivos;
    }

    /**
     * Consigue —o crea, la primera vez en la gestión— el acta de regularización, para
     * usarla como destino en el modal de Trasladar cuando no hay una acta clara.
     */
    @ValidarUsuarioAutenticado
    @PostMapping("/acta-regularizacion")
    @ResponseBody
    public ResponseEntity<?> actaRegularizacion(HttpServletRequest httpReq) {
        Usuario usuario = (Usuario) httpReq.getSession().getAttribute("usuario");
        if (usuario == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("ok", false, "msg", "Sesión expirada. Volvé a iniciar sesión."));
        }
        try {
            AsignacionActivo acta = asignacionEdicionService.obtenerOCrearActaRegularizacion(usuario);
            return ResponseEntity.ok(Map.of("ok", true, "id", acta.getIdAsignacionActivo(),
                    "numero", acta.getNumeroAsignacion()));
        } catch (SecurityException sinPermiso) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("ok", false, "msg", sinPermiso.getMessage()));
        } catch (Exception e) {
            log.error("[REGULARIZACION] Error consiguiendo el acta: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("ok", false, "msg", "No se pudo obtener el acta de regularización: " + e.getMessage()));
        }
    }

    /**
     * Busca bienes por código o descripción, diciendo en qué acta está cada uno.
     * <p>
     * Es lo que hace usable "incorporar": el usuario sabe el código del bien que se
     * olvidaron de incluir, no en qué acta quedó. Mostrar el acta actual evita moverlo
     * sin darse cuenta de dónde sale.
     */
    @ValidarUsuarioAutenticado
    @GetMapping("/buscar-bien")
    @ResponseBody
    public ResponseEntity<?> buscarBien(@RequestParam String q,
                                        @RequestParam(required = false) Long excluirActa) {
        if (q == null || q.trim().length() < 3) {
            return ResponseEntity.ok(Map.of("ok", true, "bienes", List.of()));
        }
        try {
            List<Map<String, Object>> bienes = asignacionActivoService
                    .buscarBienesConSuActa(q.trim(), excluirActa).stream()
                    .map(d -> {
                        Map<String, Object> fila = new LinkedHashMap<>();
                        fila.put("idActivo", d.getActivo().getIdActivo());
                        fila.put("codigo", d.getActivo().getCodigo());
                        fila.put("descripcion", d.getActivo().getDescripcion());
                        fila.put("estado", d.getActivo().getEstado());
                        fila.put("idActa", d.getAsignacionActivo().getIdAsignacionActivo());
                        fila.put("acta", d.getAsignacionActivo().getNumeroAsignacion());
                        fila.put("documento", d.getAsignacionActivo().getCodigoCompletoNormalizado());
                        return fila;
                    }).toList();
            return ResponseEntity.ok(Map.of("ok", true, "bienes", bienes));
        } catch (Exception e) {
            log.error("[BUSCAR-BIEN] Error buscando '{}': {}", q, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("ok", false, "msg", "No se pudo buscar: " + e.getMessage()));
        }
    }

    /** Busca actas por número o documento, para elegir destino al trasladar. */
    @ValidarUsuarioAutenticado
    @GetMapping("/buscar-acta")
    @ResponseBody
    public ResponseEntity<?> buscarActa(@RequestParam String q,
                                        @RequestParam(required = false) Long excluir) {
        if (q == null || q.trim().length() < 2) {
            return ResponseEntity.ok(Map.of("ok", true, "actas", List.of()));
        }
        try {
            List<Map<String, Object>> actas = asignacionActivoService.buscarActasPorTexto(q.trim(), excluir).stream()
                    .map(a -> {
                        Map<String, Object> fila = new LinkedHashMap<>();
                        fila.put("id", a.getIdAsignacionActivo());
                        fila.put("numero", a.getNumeroAsignacion());
                        fila.put("documento", a.getCodigoCompletoNormalizado());
                        fila.put("fecha", a.getFechaAsignacion() != null ? a.getFechaAsignacion().toLocalDate().toString() : null);
                        fila.put("responsable", a.getResponsable() != null && a.getResponsable().getPersona() != null
                                ? a.getResponsable().getPersona().getNombreCompleto() : null);
                        fila.put("oficina", a.getOficinaDestino() != null ? a.getOficinaDestino().getNombre() : null);
                        return fila;
                    }).toList();
            return ResponseEntity.ok(Map.of("ok", true, "actas", actas));
        } catch (Exception e) {
            log.error("[BUSCAR-ACTA] Error buscando '{}': {}", q, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("ok", false, "msg", "No se pudo buscar: " + e.getMessage()));
        }
    }

    /** Cuerpo del pedido de traslado / incorporación. */
    @lombok.Getter @lombok.Setter
    public static class TrasladoActaRequest {
        private List<Long> idsActivos;
        private boolean adoptarDestino;
        private String motivo;
    }

    /** Historial de operaciones del acta, para la pestaña del modal. */
    @ValidarUsuarioAutenticado
    @GetMapping("/asignaciones/{id}/historial-json")
    @ResponseBody
    public ResponseEntity<?> historialDeActa(@PathVariable Long id) {
        try {
            List<Map<String, Object>> movimientos = asignacionMovimientoDao.historialDeActa(id).stream()
                .map(m -> {
                    Map<String, Object> fila = new LinkedHashMap<>();
                    fila.put("tipo", m.getTipo());
                    fila.put("fecha", m.getFecha() != null ? m.getFecha().toString() : null);
                    fila.put("usuario", m.getNombreUsuario());
                    fila.put("motivo", m.getMotivo());
                    fila.put("resultadoVsiaf", m.getResultadoVsiaf());
                    fila.put("mensajeVsiaf", m.getMensajeVsiaf());
                    // Desde qué lado se está mirando: la misma operación se lee distinto
                    // en el acta que perdió bienes y en la que los recibió.
                    boolean esOrigen = m.getAsignacionOrigen() != null
                            && id.equals(m.getAsignacionOrigen().getIdAsignacionActivo());
                    fila.put("rol", esOrigen ? "ORIGEN" : "DESTINO");
                    fila.put("actaOrigen", m.getAsignacionOrigen() != null
                            ? m.getAsignacionOrigen().getNumeroAsignacion() : null);
                    fila.put("idActaOrigen", m.getAsignacionOrigen() != null
                            ? m.getAsignacionOrigen().getIdAsignacionActivo() : null);
                    fila.put("actaDestino", m.getAsignacionDestino() != null
                            ? m.getAsignacionDestino().getNumeroAsignacion() : null);
                    fila.put("idActaDestino", m.getAsignacionDestino() != null
                            ? m.getAsignacionDestino().getIdAsignacionActivo() : null);
                    fila.put("bienes", m.getDetalles().stream()
                            .map(d -> d.getCodigoActivo()).filter(Objects::nonNull).toList());
                    return fila;
                }).toList();

            return ResponseEntity.ok(Map.of("ok", true, "movimientos", movimientos));

        } catch (Exception e) {
            log.error("[ASIGNACION-HISTORIAL] Error cargando el historial del acta {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("ok", false, "msg", "No se pudo cargar el historial: " + e.getMessage()));
        }
    }

    /** Cuerpo del pedido de separación. */
    @lombok.Getter @lombok.Setter
    public static class SeparacionActaRequest {
        private List<Long> idsActivos;
        private Long idConfigGestion;
        private String nroDocumento;
        private Long idResponsableDestino;
        private Long idOficinaDestino;
        private String motivo;
    }

    /**
     * Acta del documento, en Word (.docx) — editable, y con el membrete institucional
     * como imagen de fondo detrás del texto (se repite en cada página).
     * <p>
     * Reusa {@link WordAsignacionActivoService}, el mismo generador que ya usa
     * {@code ReportesController} para las actas que salen de Pendientes; ese generador
     * es también el que arma la nota de traslado al pie, cuando el acta tiene
     * movimientos que contar.
     */
    @ValidarUsuarioAutenticado
    @GetMapping("/asignaciones/{id}/word")
    public ResponseEntity<byte[]> actaWord(@PathVariable Long id) {
        try {
            AsignacionActivo asignacion = asignacionActivoService.findByIdConDetalles(id).orElse(null);
            if (asignacion == null) return ResponseEntity.notFound().build();

            int gestion = asignacion.getFechaAsignacion() != null
                    ? asignacion.getFechaAsignacion().getYear()
                    : LocalDate.now().getYear();

            // Sin configuración de la gestión el acta se emite igual, con los datos que
            // haya: no poder imprimir un documento ya firmado sería peor que imprimirlo
            // sin el membrete de la ciudad.
            ConfiguracionGestion config = configuracionGestionService.findByGestion(gestion)
                    .orElseGet(() -> {
                        ConfiguracionGestion c = new ConfiguracionGestion();
                        c.setGestion(gestion);
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

            byte[] docx = wordAsignacionActivoService.generarActaAsignacion(asignacion, config, nombreUsuario);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
            headers.setContentDisposition(ContentDisposition.attachment()
                    .filename("acta_" + (asignacion.getNumeroAsignacion() != null
                            ? asignacion.getNumeroAsignacion() : id) + ".docx")
                    .build());
            return new ResponseEntity<>(docx, headers, HttpStatus.OK);

        } catch (Exception e) {
            log.error("[ASIGNACION-WORD] No se pudo generar el acta {}: {}", id, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Números de página a dibujar alrededor de la actual: como mucho cinco.
     * <p>
     * Se calcula acá y no en la plantilla porque en Thymeleaf la misma cuenta queda como
     * tres condicionales anidados dentro de un atributo, ilegible y difícil de corregir.
     */
    private List<Integer> ventanaDePaginas(int actual, int total) {
        if (total <= 1) return List.of();
        int inicio = Math.max(0, Math.min(actual - 2, total - 5));
        int fin = Math.min(total - 1, inicio + 4);
        List<Integer> paginas = new ArrayList<>();
        for (int i = inicio; i <= fin; i++) paginas.add(i);
        return paginas;
    }

    /** Roles que pueden corregir un activo ya registrado (mismo criterio que el endpoint que lo aplica). */
    private static final Set<String> ROLES_EDICION = Set.of("ADMINISTRADOR", "SUPER USUARIO");

    /**
     * Id cifrado del activo, para usarlo desde el navegador. Si el cifrado falla se
     * devuelve null: la fila se muestra igual, solo pierde el botón de corregir.
     */
    private String cifrarId(Activo activo) {
        if (activo == null || activo.getIdActivo() == null) return null;
        try {
            return Encriptar.encrypt(String.valueOf(activo.getIdActivo()));
        } catch (Exception e) {
            log.warn("[ASIGNACION-DETALLE] No se pudo cifrar el id del activo {}: {}",
                     activo.getIdActivo(), e.getMessage());
            return null;
        }
    }

    @ValidarUsuarioAutenticado
    @GetMapping("/asignaciones/{id}/detalles-json")
    @ResponseBody
    public ResponseEntity<?> obtenerDetallesAsignacionJson(@PathVariable Long id, HttpServletRequest httpReq) {
        try {
            Optional<AsignacionActivo> asigOpt = asignacionActivoService.findByIdConDetalles(id);

            if (asigOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("ok", false, "msg", "Asignación no encontrada"));
            }

            AsignacionActivo asig = asigOpt.get();

            List<Map<String, Object>> listaDetalles = asig.getDetalles().stream().map(d -> {
                Map<String, Object> map = new LinkedHashMap<>();

                Activo activo = d.getActivo();

                // Código — primero snapshot, luego entidad viva
                map.put("codigo",
                    d.getCodigoActivoSnapshot() != null
                        ? d.getCodigoActivoSnapshot()
                        : (activo != null ? activo.getCodigo() : "—"));

                // Descripción
                map.put("descripcion",
                    d.getDescripcionActivoSnapshot() != null
                        ? d.getDescripcionActivoSnapshot()
                        : (activo != null ? activo.getDescripcion() : "—"));

                // Estado del activo (snapshot primero)
                map.put("estadoActivo",
                    d.getEstadoActivoSnapshot() != null
                        ? d.getEstadoActivoSnapshot()
                        : (activo != null && activo.getEstadoActivo() != null
                            ? activo.getEstadoActivo().getNombre()
                            : "—"));

                // Oficina actual del activo
                String oficinaNombre = "—";
                String predioNombre  = "—";
                if (activo != null && activo.getOficina() != null) {
                    oficinaNombre = activo.getOficina().getNombre() != null
                        ? activo.getOficina().getNombre() : "—";

                    if (activo.getOficina().getPredio() != null) {
                        predioNombre = activo.getOficina().getPredio().getDescrip() != null
                            ? activo.getOficina().getPredio().getDescrip()
                            : (activo.getOficina().getPredio().getUnidad() != null
                                ? activo.getOficina().getPredio().getUnidad()
                                : "—");
                    }
                }
                map.put("oficina", oficinaNombre);
                map.put("predio",  predioNombre);

                // Fecha de adquisición
                map.put("fechaAdquisicion",
                    activo != null && activo.getFechaAdquisicion() != null
                        ? activo.getFechaAdquisicion().toString()   // ISO: "2023-04-15"
                        : "—");

                // Responsable actual del activo
                map.put("responsable",
                    activo != null && activo.getResponsable() != null
                        && activo.getResponsable().getPersona() != null
                        ? activo.getResponsable().getPersona().getNombreCompleto()
                        : "—");

                // Grupo contable
                map.put("grupoContable",
                    activo != null && activo.getGrupoContable() != null
                        ? activo.getGrupoContable().getNombre()
                        : "—");

                // Costo: el snapshot es lo que se registró en el acta; el del activo es
                // el de hoy. Se mandan los dos para poder avisar cuando divergen (el
                // activo se editó después de asignarlo).
                BigDecimal costoSnap = d.getCostoActivoSnapshot();
                BigDecimal costoHoy  = (activo != null && activo.getCosto() != null)
                    ? BigDecimal.valueOf(activo.getCosto()) : null;
                map.put("costo",       costoSnap != null ? costoSnap : costoHoy);
                map.put("costoActual", costoHoy);

                // Estado de SINCRONIZACIÓN (no confundir con estadoActivo, que es la
                // condición física del bien: BUENO / MALO / …).
                String estadoReg = activo != null && activo.getEstado() != null
                    ? activo.getEstado().toUpperCase() : "—";
                map.put("estadoRegistro", estadoReg);

                // Confirmación real del VSIAF. `estadoRegistro` dice que el activo se dio
                // por subido; esto dice si el worker VFPOLEDB lo confirmó de verdad, sigue
                // en cola o lo rechazó. En activos anteriores a este control viene null:
                // no se sabe, y decir que sí sería inventarlo.
                map.put("sincVsiaf", activo != null ? activo.getSincVsiaf() : null);
                map.put("sincVsiafMensaje", activo != null ? activo.getSincVsiafMensaje() : null);

                // Id cifrado para poder abrir la corrección desde esta pantalla.
                map.put("idEnc", cifrarId(activo));

                // Ids planos para las operaciones sobre el acta (separar). Van en claro
                // como el {id} del acta en esta misma ruta: la barrera es el rol que
                // valida el servicio, no la opacidad del identificador.
                map.put("idActivo", activo != null ? activo.getIdActivo() : null);
                map.put("idOficina", activo != null && activo.getOficina() != null
                        ? activo.getOficina().getIdOficina() : null);
                map.put("idPredio", activo != null && activo.getOficina() != null
                        && activo.getOficina().getPredio() != null
                        ? activo.getOficina().getPredio().getIdPredio() : null);
                // Cancelados y bajas no se corrigen desde acá.
                map.put("editable", "ACTIVO".equals(estadoReg) || "PENDIENTE".equals(estadoReg));

                return map;
            }).toList();

            // Totales del acta, para las tarjetas de resumen del modal.
            Map<String, Object> resumen = new LinkedHashMap<>();
            resumen.put("total", listaDetalles.size());
            resumen.put("subidos", listaDetalles.stream()
                .filter(m -> "ACTIVO".equals(m.get("estadoRegistro"))).count());
            resumen.put("pendientes", listaDetalles.stream()
                .filter(m -> "PENDIENTE".equals(m.get("estadoRegistro"))).count());
            resumen.put("sinCosto", listaDetalles.stream()
                .filter(m -> m.get("costo") == null).count());
            // Lo que el worker todavía no resolvió y lo que rechazó: es la diferencia
            // entre "lo mandamos" y "está en el VSIAF", que antes no se distinguía.
            resumen.put("enCola", listaDetalles.stream()
                .filter(m -> "EN_COLA".equals(m.get("sincVsiaf"))).count());
            resumen.put("conError", listaDetalles.stream()
                .filter(m -> "ERROR".equals(m.get("sincVsiaf"))).count());
            resumen.put("costoTotal", listaDetalles.stream()
                .map(m -> (BigDecimal) m.get("costo"))
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP));

            // El botón de corregir se dibuja solo para quien puede usarlo. La barrera
            // real está en el endpoint que aplica el cambio; esto es solo la interfaz.
            Usuario usuario = (Usuario) httpReq.getSession().getAttribute("usuario");
            boolean puedeEditar = usuario != null && usuario.getRol() != null
                && usuario.getRol().getNombre() != null
                && ROLES_EDICION.contains(usuario.getRol().getNombre().trim().toUpperCase());

            // Ids planos de la cabecera, para precargar el modal de "Editar cabecera"
            // sin pedir otro endpoint: esta ruta ya se llama cada vez que se abre el
            // detalle del acta.
            Map<String, Object> cabecera = new LinkedHashMap<>();
            cabecera.put("idResponsable", asig.getResponsable() != null
                    ? asig.getResponsable().getIdResponsable() : null);
            cabecera.put("idOficinaDestino", asig.getOficinaDestino() != null
                    ? asig.getOficinaDestino().getIdOficina() : null);
            cabecera.put("idPredioOficina", asig.getOficinaDestino() != null
                    && asig.getOficinaDestino().getPredio() != null
                    ? asig.getOficinaDestino().getPredio().getIdPredio() : null);

            return ResponseEntity.ok(Map.of(
                "ok", true,
                "puedeEditar", puedeEditar,
                "detalles", listaDetalles,
                "resumen", resumen,
                "cabecera", cabecera));

        } catch (Exception e) {
            log.error("[ASIGNACION-DETALLE] Error cargando detalles id={}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("ok", false, "msg", "Error al cargar detalles: " + e.getMessage()));
        }
    }
}
