package com.usic.SistemasActivosFijosUAP.controller.recepcion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.usic.SistemasActivosFijosUAP.anotacion.ValidarUsuarioAutenticado;
import com.usic.SistemasActivosFijosUAP.config.RolesSciaf;
import com.usic.SistemasActivosFijosUAP.model.IService.IHojaRutaService;
import com.usic.SistemasActivosFijosUAP.model.IService.IMovimientoService;
import com.usic.SistemasActivosFijosUAP.model.IService.ISolictanteService;
import com.usic.SistemasActivosFijosUAP.model.IService.IUnidadService;
import com.usic.SistemasActivosFijosUAP.model.dto.HojaRutaTablaDTO;
import com.usic.SistemasActivosFijosUAP.model.entity.HojaRuta;
import com.usic.SistemasActivosFijosUAP.model.entity.Movimiento;
import com.usic.SistemasActivosFijosUAP.model.entity.Solicitante;
import com.usic.SistemasActivosFijosUAP.model.entity.Unidad;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * Hojas de ruta. Dos pantallas sobre los mismos endpoints:
 * <ul>
 *   <li>{@code /vista}: página de RECEPCIÓN (independiente, sin menú lateral). Registra hojas,
 *       movimientos y solicitantes. Solo RECEPCION y ADMINISTRADOR.</li>
 *   <li>{@code /seguimiento}: Búsqueda y Seguimiento, de solo lectura, dentro del sistema.
 *       Solo ADMINISTRADOR y SUPER USUARIO.</li>
 * </ul>
 * Todo cuelga de /administracion/** (permitAll): los permisos se validan acá.
 */
@Controller
@RequestMapping("/administracion/hoja-ruta")
@RequiredArgsConstructor
public class HojaRutaController {

    private final IHojaRutaService hojaRutaService;
    private final IMovimientoService movimientoService;
    private final ISolictanteService solicitanteService;
    private final IUnidadService unidadService;

    /** Unidad que recibe las hojas de ruta al registrarse (sección de Activos Fijos). */
    private static final String UNIDAD_ACTIVOS_FIJOS = "ACTIVOS FIJOS";

    /** Tipos de hoja que ofrece el formulario de Recepción. */
    private static final Set<String> TIPOS = Set.of("RECTORADO", "DAF", "PEDIDO");

    /** Largo de las columnas de texto corto (VARCHAR 255). */
    private static final int MAX_TEXTO = 255;

    /** Quién registra y modifica (la página de Recepción): RECEPCION y ADMINISTRADOR. */
    private static boolean puedeRegistrar(HttpServletRequest request) {
        String rol = RolesSciaf.rolDe(RolesSciaf.usuarioDe(request));
        return "RECEPCION".equals(rol) || RolesSciaf.ADMINISTRADOR.equals(rol);
    }

    /**
     * Quién consulta las hojas (listado, detalle, movimientos, solicitantes): Recepción y los
     * administrativos. Antes bastaba la sesión: cualquier usuario al que le dieran el permiso
     * del menú leía montos y solicitantes aunque la pantalla le dijera «sin permiso».
     */
    private static boolean puedeLeer(HttpServletRequest request) {
        return puedeRegistrar(request) || RolesSciaf.esAdministrativo(request);
    }

    private static ResponseEntity<Map<String, Object>> sinPermisoLectura() {
        return respuesta(HttpStatus.FORBIDDEN, false, "No tiene permiso para consultar hojas de ruta.");
    }

    private static ResponseEntity<Map<String, Object>> respuesta(HttpStatus status, boolean ok, String msg) {
        Map<String, Object> r = new HashMap<>();
        r.put("ok", ok);
        r.put("msg", msg);
        return ResponseEntity.status(status).body(r);
    }

    private static ResponseEntity<Map<String, Object>> sinPermiso() {
        return respuesta(HttpStatus.FORBIDDEN, false, "No tiene permiso para registrar hojas de ruta.");
    }

    /** Texto recortado y en MAYÚSCULAS; vacío → null. */
    private static String mayus(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t.toUpperCase();
    }

    /** Texto libre (observación): recortado, sin cambiar mayúsculas; vacío → null. */
    private static String libre(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static boolean largo(String s) {
        return s != null && s.length() > MAX_TEXTO;
    }

    // ════════════════════════════ Pantallas ════════════════════════════

    @ValidarUsuarioAutenticado
    @GetMapping("/vista")
    public String inicio(Model model, HttpServletRequest request) {
        if (!puedeRegistrar(request)) {
            return "redirect:/acceso-denegado";
        }
        model.addAttribute("listaSolicitantes", solicitanteService.findAll());
        model.addAttribute("listaUnidades", unidadService.findAll());
        return "hojaRuta/vista";
    }

    /**
     * Búsqueda y Seguimiento (solo lectura). La vista llega con las unidades, las gestiones y
     * el listado de la gestión actual: un solo pedido al abrir.
     */
    @ValidarUsuarioAutenticado
    @GetMapping("/seguimiento")
    public String seguimiento(Model model, HttpServletRequest request) {
        if (!RolesSciaf.esAdministrativo(request)) {
            return "supervision/sin_permiso";
        }
        List<Integer> gestiones = hojaRutaService.gestiones();
        int actual = LocalDate.now().getYear();
        // La gestión en curso; si todavía no tiene hojas (enero), la última que tenga.
        Integer inicial = gestiones.isEmpty() || gestiones.contains(actual) ? Integer.valueOf(actual) : gestiones.get(0);
        if (!gestiones.contains(inicial)) {
            gestiones = new ArrayList<>(gestiones);
            gestiones.add(0, inicial);
        }
        model.addAttribute("gestiones", gestiones);
        model.addAttribute("gestionInicial", inicial);
        model.addAttribute("listaUnidades", unidadService.findAll());
        model.addAttribute("filas", hojaRutaService.listarFiltrados(inicial, null));
        return "hojaRuta/seguimiento";
    }

    /** Tabla de Búsqueda y Seguimiento al cambiar la gestión o la unidad. */
    @ValidarUsuarioAutenticado
    @PostMapping("/seguimiento/tabla")
    public String seguimientoTabla(
            @RequestParam(required = false) Integer gestion,
            @RequestParam(required = false) Long unidadOrigenId,
            Model model, HttpServletRequest request, HttpServletResponse response) {
        if (!RolesSciaf.esAdministrativo(request)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return "supervision/sin_permiso";
        }
        model.addAttribute("filas", hojaRutaService.listarFiltrados(gestion, unidadOrigenId));
        return "hojaRuta/tabla :: tabla";
    }

    // ════════════════════════════ Consultas ════════════════════════════

    /** Listado de la página de Recepción (JSON). Antes respondía sin sesión. */
    @ValidarUsuarioAutenticado
    @PostMapping("/listar")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> listar(
            HttpServletRequest request,
            @RequestParam(required = false) Integer gestion,
            @RequestParam(required = false) Long unidadOrigenId) {

        if (!puedeLeer(request)) return sinPermisoLectura();
        Map<String, Object> response = new HashMap<>();
        try {
            List<HojaRutaTablaDTO> lista = hojaRutaService.listarFiltrados(gestion, unidadOrigenId);
            response.put("ok", true);
            response.put("hojaRutas", lista);
        } catch (Exception e) {
            response.put("ok", false);
            response.put("msg", "Error al obtener registros: " + e.getMessage());
        }
        return ResponseEntity.ok(response);
    }

    /**
     * Datos y trayectoria de una hoja: por su id (Seguimiento) o por tipo + código + gestión
     * (Recepción). Si no existe responde ok:false con el motivo; antes era un error 500.
     */
    @ValidarUsuarioAutenticado
    @PostMapping("/buscar")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> buscarHojaRuta(
            HttpServletRequest request,
            @RequestParam(value = "idHojaRuta", required = false) Long idHojaRuta,
            @RequestParam(value = "codigo", required = false) String codigo,
            @RequestParam(value = "tipo", required = false) String tipo,
            @RequestParam(value = "gestion", required = false) Integer gestion) {

        if (!puedeLeer(request)) return sinPermisoLectura();
        Map<String, Object> response = new HashMap<>();

        try {
            HojaRuta hojaRuta = idHojaRuta != null
                    ? hojaRutaService.findById(idHojaRuta)
                    : hojaRutaService.findByTipoAndCodigoAndGestion(tipo, codigo, gestion);

            if (hojaRuta == null) {
                response.put("ok", false);
                response.put("msg", "No se encontró una hoja de ruta con esos datos.");
                return ResponseEntity.ok(response);
            }

            // El primero es el movimiento actual (ordenados por fecha, hora e id).
            List<Movimiento> movimientos = movimientoService.findByHojaRuta(hojaRuta.getIdHojaRuta());
            Solicitante sol = hojaRuta.getSolicitante();

            Map<String, Object> hojaRutaData = new HashMap<>();
            hojaRutaData.put("idHojaRuta", hojaRuta.getIdHojaRuta());
            hojaRutaData.put("codigo", hojaRuta.getCodigo());
            hojaRutaData.put("tipo", hojaRuta.getTipo());
            hojaRutaData.put("descripcion", hojaRuta.getDescripcion());
            hojaRutaData.put("certificacion", hojaRuta.getCertificacion());
            hojaRutaData.put("monto", hojaRuta.getMonto());
            hojaRutaData.put("gestion", hojaRuta.getGestion());
            hojaRutaData.put("solicitanteNombre", sol != null ? sol.getNombre() : null);
            hojaRutaData.put("solicitanteCargo", sol != null ? sol.getCargo() : null);
            hojaRutaData.put("solicitanteId", sol != null ? sol.getIdSolicitante() : null);

            List<Map<String, Object>> movimientosData = new ArrayList<>();
            for (Movimiento m : movimientos) {
                Map<String, Object> movData = new HashMap<>();
                movData.put("idMovimiento", m.getIdMovimiento());
                movData.put("estado", Movimiento.textoEstado(m.getEstadoMovimiento()));
                movData.put("fecha", m.getFecha() != null ? m.getFecha().toString() : "");
                movData.put("hora", m.getHora() != null ? m.getHora().toString() : "00:00:00");
                movData.put("origen", m.getUnidadOrigen() != null ? m.getUnidadOrigen().getNombre() : "SIN ORIGEN");
                movData.put("destino", m.getUnidadDestino() != null ? m.getUnidadDestino().getNombre() : "SIN DESTINO");
                movData.put("observacion", m.getObservacion() != null ? m.getObservacion() : "");
                movData.put("unidadOrigenId", m.getUnidadOrigen() != null ? m.getUnidadOrigen().getIdUnidad() : null);
                movData.put("unidadDestinoId", m.getUnidadDestino() != null ? m.getUnidadDestino().getIdUnidad() : null);
                movimientosData.add(movData);
            }

            response.put("ok", true);
            response.put("hojaRuta", hojaRutaData);
            response.put("movimientos", movimientosData);
            response.put("movimientoActual", movimientos.isEmpty()
                    ? "SIN MOVIMIENTOS" : Movimiento.textoEstado(movimientos.get(0).getEstadoMovimiento()));

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("ok", false);
            response.put("msg", "Error al buscar: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /** Estado del formulario (1 recibir, 2 enviar, 3 archivar) al número que se guarda; otro valor → null. */
    private static String estadoValido(Integer estado) {
        return (estado != null && estado >= 1 && estado <= 3) ? String.valueOf(estado) : null;
    }

    private static Integer estadoAFormulario(String estado) {
        if (estado != null) {
            switch (estado.trim()) {
                case "2": return 2;
                case "3": return 3;
                default: break;
            }
        }
        return 1;
    }

    /**
     * Devuelve la unidad "ACTIVOS FIJOS" (destino de la recepción inicial de toda HR).
     * Si aún no existe en el catálogo de unidades, la crea una sola vez.
     */
    private Unidad obtenerOCrearUnidadActivosFijos(Long idUsuario) {
        return unidadService.findByNombre(UNIDAD_ACTIVOS_FIJOS)
                .orElseGet(() -> {
                    Unidad nueva = new Unidad();
                    nueva.setNombre(UNIDAD_ACTIVOS_FIJOS);
                    nueva.setEstado("ACTIVO");
                    nueva.setRegistroIdUsuario(idUsuario);
                    return unidadService.save(nueva);
                });
    }

    /** Lo que se exige en registrar y modificar; null si todo está bien. */
    private static String validarDatos(String tipo, String codigo, Integer gestion, String descripcion,
                                       String certificacion, BigDecimal monto) {
        if (tipo == null || !TIPOS.contains(tipo)) return "Seleccione el tipo de hoja de ruta.";
        if (codigo == null) return "Escriba el número de la hoja de ruta.";
        if (gestion == null) return "Escriba la gestión.";
        if (descripcion == null) return "Escriba la descripción.";
        if (largo(codigo) || largo(certificacion)) return "El número o la certificación pasan de " + MAX_TEXTO + " caracteres.";
        if (monto != null && monto.signum() < 0) return "El monto no puede ser negativo.";
        return null;
    }

    // ════════════════════════════ Registro (Recepción) ════════════════════════════

    /** Hoja nueva + su primer movimiento (recepción en Activos Fijos), todo o nada. */
    @ValidarUsuarioAutenticado
    @PostMapping("/registrar")
    @ResponseBody
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<Map<String, Object>> registrar(
            HttpServletRequest request,
            @RequestParam("tipo") String tipo,
            @RequestParam("codigo") String codigo,
            @RequestParam("gestion") Integer gestion,
            @RequestParam("solicitanteId") Long solicitanteId,
            @RequestParam("descripcion") String descripcion,
            @RequestParam(value = "certificacion", required = false) String certificacion,
            @RequestParam(value = "monto", required = false) BigDecimal monto,
            @RequestParam("unidadOrigenId") Long unidadOrigenId,
            @RequestParam("fecha") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @RequestParam("hora") @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime hora) {

        if (!puedeRegistrar(request)) return sinPermiso();
        Usuario usuarioLogueado = RolesSciaf.usuarioDe(request);

        tipo = mayus(tipo);
        codigo = mayus(codigo);
        descripcion = mayus(descripcion);
        certificacion = mayus(certificacion);
        String invalido = validarDatos(tipo, codigo, gestion, descripcion, certificacion, monto);
        if (invalido != null) return respuesta(HttpStatus.BAD_REQUEST, false, invalido);

        try {
            // Turno primero: dos envíos a la vez (doble clic) ya no pasan los dos el «¿existe?».
            hojaRutaService.tomarTurnoRegistro();
            // Verificar que no exista el mismo código para ese tipo y gestión.
            // El número puede repetirse en gestiones o tipos distintos.
            if (hojaRutaService.existe(tipo, codigo, gestion, null)) {
                return respuesta(HttpStatus.OK, false, "Ya existe una hoja de ruta " + tipo + " N° " + codigo + " en la gestión " + gestion);
            }

            Solicitante solicitante = solicitanteService.findById(solicitanteId);
            Unidad unidadOrigen = unidadService.findById(unidadOrigenId);
            if (solicitante == null || unidadOrigen == null) {
                return respuesta(HttpStatus.BAD_REQUEST, false, "Solicitante o unidad no encontrados");
            }

            HojaRuta hojaRuta = new HojaRuta();
            hojaRuta.setTipo(tipo);
            hojaRuta.setCodigo(codigo);
            hojaRuta.setGestion(gestion);
            hojaRuta.setSolicitante(solicitante);
            hojaRuta.setDescripcion(descripcion);
            hojaRuta.setCertificacion(certificacion);
            hojaRuta.setMonto(monto);
            hojaRuta.setEstado("ACTIVO");
            hojaRuta.setRegistroIdUsuario(usuarioLogueado.getIdUsuario());
            hojaRutaService.save(hojaRuta);

            // Primer movimiento (RECIBIR). El registro de la HR representa su RECEPCIÓN en la
            // sección: el documento llega desde la unidad de origen hacia ACTIVOS FIJOS.
            Movimiento primerMovimiento = new Movimiento();
            primerMovimiento.setHojaRuta(hojaRuta);
            primerMovimiento.setFecha(fecha);
            primerMovimiento.setHora(hora);
            primerMovimiento.setEstadoMovimiento("1");
            primerMovimiento.setSolicitante(solicitante);
            primerMovimiento.setUnidadOrigen(unidadOrigen);
            primerMovimiento.setUnidadDestino(obtenerOCrearUnidadActivosFijos(usuarioLogueado.getIdUsuario()));
            primerMovimiento.setObservacion("Registro inicial");
            primerMovimiento.setEstado("ACTIVO");
            primerMovimiento.setRegistroIdUsuario(usuarioLogueado.getIdUsuario());
            movimientoService.save(primerMovimiento);

            return respuesta(HttpStatus.OK, true, "Hoja de ruta registrada correctamente");

        } catch (Exception e) {
            // Sin esto el catch confirmaba la transacción: quedaba la hoja sin su primer movimiento.
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            e.printStackTrace();
            return respuesta(HttpStatus.INTERNAL_SERVER_ERROR, false, "Error al registrar: " + e.getMessage());
        }
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/modificar")
    @ResponseBody
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<Map<String, Object>> modificar(
            HttpServletRequest request,
            @RequestParam("idHojaRuta") Long idHojaRuta,
            @RequestParam("tipo") String tipo,
            @RequestParam("codigo") String codigo,
            @RequestParam("gestion") Integer gestion,
            @RequestParam("solicitanteId") Long solicitanteId,
            @RequestParam("descripcion") String descripcion,
            @RequestParam(value = "certificacion", required = false) String certificacion,
            @RequestParam(value = "monto", required = false) BigDecimal monto) {

        if (!puedeRegistrar(request)) return sinPermiso();

        tipo = mayus(tipo);
        codigo = mayus(codigo);
        descripcion = mayus(descripcion);
        certificacion = mayus(certificacion);
        String invalido = validarDatos(tipo, codigo, gestion, descripcion, certificacion, monto);
        if (invalido != null) return respuesta(HttpStatus.BAD_REQUEST, false, invalido);

        try {
            hojaRutaService.tomarTurnoRegistro();
            HojaRuta hojaRuta = hojaRutaService.findById(idHojaRuta);
            if (hojaRuta == null) {
                return respuesta(HttpStatus.BAD_REQUEST, false, "Hoja de ruta no encontrada");
            }

            // La combinación tipo + código + gestión no puede ser de otra hoja (de ninguna:
            // con mirar solo la primera, una hoja duplicada de antes no se podía editar nunca).
            if (hojaRutaService.existe(tipo, codigo, gestion, idHojaRuta)) {
                return respuesta(HttpStatus.OK, false, "Ya existe una hoja de ruta " + tipo + " N° " + codigo + " en la gestión " + gestion);
            }

            Solicitante solicitante = solicitanteService.findById(solicitanteId);
            if (solicitante == null) {
                return respuesta(HttpStatus.BAD_REQUEST, false, "Solicitante no encontrado");
            }

            hojaRuta.setTipo(tipo);
            hojaRuta.setCodigo(codigo);
            hojaRuta.setGestion(gestion);
            hojaRuta.setSolicitante(solicitante);
            hojaRuta.setDescripcion(descripcion);
            hojaRuta.setCertificacion(certificacion);
            hojaRuta.setMonto(monto);
            hojaRuta.setModificacionIdUsuario(RolesSciaf.usuarioDe(request).getIdUsuario());
            hojaRutaService.save(hojaRuta);

            return respuesta(HttpStatus.OK, true, "Hoja de ruta modificada correctamente");

        } catch (Exception e) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            e.printStackTrace();
            return respuesta(HttpStatus.INTERNAL_SERVER_ERROR, false, "Error al modificar: " + e.getMessage());
        }
    }

    /** Registrar (sin idMovimiento) o modificar un movimiento de la hoja. */
    @ValidarUsuarioAutenticado
    @PostMapping("/movimiento/guardar")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> guardarMovimiento(
            HttpServletRequest request,
            @RequestParam(value = "idMovimiento", required = false) Long idMovimiento,
            @RequestParam("hojaRutaId") Long hojaRutaId,
            @RequestParam("estado") Integer estadoNumero,
            @RequestParam("fecha") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @RequestParam("hora") @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime hora,
            @RequestParam("unidadOrigenId") Long unidadOrigenId,
            @RequestParam("unidadDestinoId") Long unidadDestinoId,
            @RequestParam(value = "observacion", required = false) String observacion) {

        if (!puedeRegistrar(request)) return sinPermiso();

        // Antes un estado fuera de 1-3 se guardaba como RECIBIDO sin avisar.
        String estado = estadoValido(estadoNumero);
        if (estado == null) return respuesta(HttpStatus.BAD_REQUEST, false, "Seleccione el estado del movimiento.");
        observacion = libre(observacion);
        if (largo(observacion)) {
            return respuesta(HttpStatus.BAD_REQUEST, false, "La observación pasa de " + MAX_TEXTO + " caracteres.");
        }

        try {
            Unidad origen = unidadService.findById(unidadOrigenId);
            Unidad destino = unidadService.findById(unidadDestinoId);
            if (origen == null || destino == null) {
                return respuesta(HttpStatus.BAD_REQUEST, false, "Unidad de origen o destino no encontrada");
            }
            Long idUsuario = RolesSciaf.usuarioDe(request).getIdUsuario();

            if (idMovimiento != null) {
                Movimiento movimiento = movimientoService.findById(idMovimiento);
                // Que sea de esta hoja: el formulario manda las dos cosas.
                if (movimiento == null || movimiento.getHojaRuta() == null
                        || !movimiento.getHojaRuta().getIdHojaRuta().equals(hojaRutaId)) {
                    return respuesta(HttpStatus.BAD_REQUEST, false, "Movimiento no encontrado en esta hoja de ruta");
                }
                movimiento.setEstadoMovimiento(estado);
                movimiento.setFecha(fecha);
                movimiento.setHora(hora);
                movimiento.setUnidadOrigen(origen);
                movimiento.setUnidadDestino(destino);
                movimiento.setObservacion(observacion);
                movimiento.setModificacionIdUsuario(idUsuario);
                movimientoService.save(movimiento);
                return respuesta(HttpStatus.OK, true, "Movimiento modificado correctamente");
            }

            HojaRuta hojaRuta = hojaRutaService.findById(hojaRutaId);
            if (hojaRuta == null) {
                return respuesta(HttpStatus.BAD_REQUEST, false, "Hoja de ruta no encontrada");
            }
            Movimiento movimiento = new Movimiento();
            movimiento.setHojaRuta(hojaRuta);
            movimiento.setEstadoMovimiento(estado);
            movimiento.setFecha(fecha);
            movimiento.setHora(hora);
            movimiento.setSolicitante(hojaRuta.getSolicitante());
            movimiento.setUnidadOrigen(origen);
            movimiento.setUnidadDestino(destino);
            movimiento.setObservacion(observacion);
            movimiento.setEstado("ACTIVO");
            movimiento.setRegistroIdUsuario(idUsuario);
            movimientoService.save(movimiento);
            return respuesta(HttpStatus.OK, true, "Movimiento registrado correctamente");

        } catch (Exception e) {
            e.printStackTrace();
            return respuesta(HttpStatus.INTERNAL_SERVER_ERROR, false, "Error al guardar movimiento: " + e.getMessage());
        }
    }

    // Obtener datos de movimiento para editar
    @ValidarUsuarioAutenticado
    @GetMapping("/movimiento/{id}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> obtenerMovimiento(HttpServletRequest request,
            @PathVariable("id") Long idMovimiento) {
        if (!puedeLeer(request)) return sinPermisoLectura();
        Map<String, Object> response = new HashMap<>();

        try {
            Movimiento movimiento = movimientoService.findById(idMovimiento);

            if (movimiento == null) {
                response.put("ok", false);
                response.put("msg", "Movimiento no encontrado");
                return ResponseEntity.badRequest().body(response);
            }

            Map<String, Object> movData = new HashMap<>();
            movData.put("idMovimiento", movimiento.getIdMovimiento());
            movData.put("estado", Movimiento.textoEstado(movimiento.getEstadoMovimiento()));
            movData.put("estadoNumero", estadoAFormulario(movimiento.getEstadoMovimiento()));
            movData.put("fecha", movimiento.getFecha() != null ? movimiento.getFecha().toString() : "");
            movData.put("hora", movimiento.getHora() != null ? movimiento.getHora().toString() : "");
            movData.put("unidadOrigenId", movimiento.getUnidadOrigen() != null ? movimiento.getUnidadOrigen().getIdUnidad() : null);
            movData.put("unidadDestinoId", movimiento.getUnidadDestino() != null ? movimiento.getUnidadDestino().getIdUnidad() : null);
            movData.put("observacion", movimiento.getObservacion() != null ? movimiento.getObservacion() : "");

            response.put("ok", true);
            response.put("movimiento", movData);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("ok", false);
            response.put("msg", "Error: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Solicitante nuevo, en MAYÚSCULAS. Si ya hay uno con el mismo nombre y cargo se devuelve
     * ese (antes un doble clic o volver a escribirlo lo duplicaba en la lista).
     */
    @ValidarUsuarioAutenticado
    @PostMapping("/solicitante/registrar")
    @ResponseBody
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<Map<String, Object>> registrarSolicitante(
            HttpServletRequest request,
            @RequestParam("nombre") String nombre,
            @RequestParam("cargo") String cargo) {

        if (!puedeRegistrar(request)) return sinPermiso();

        nombre = mayus(nombre);
        cargo = mayus(cargo);
        if (nombre == null || cargo == null) {
            return respuesta(HttpStatus.BAD_REQUEST, false, "Nombre y cargo son obligatorios");
        }
        if (largo(nombre) || largo(cargo)) {
            return respuesta(HttpStatus.BAD_REQUEST, false, "El nombre o el cargo pasan de " + MAX_TEXTO + " caracteres.");
        }

        try {
            hojaRutaService.tomarTurnoRegistro();
            Optional<Solicitante> igual = solicitanteService.buscarIgual(nombre, cargo);
            if (igual.isPresent()) {
                ResponseEntity<Map<String, Object>> r = respuesta(HttpStatus.OK, true, "El solicitante ya estaba registrado: se seleccionó.");
                r.getBody().put("idSolicitante", igual.get().getIdSolicitante());
                return r;
            }

            Solicitante solicitante = new Solicitante();
            solicitante.setNombre(nombre);
            solicitante.setCargo(cargo);
            solicitante.setEstado("ACTIVO");
            solicitante.setRegistroIdUsuario(RolesSciaf.usuarioDe(request).getIdUsuario());
            solicitanteService.save(solicitante);

            ResponseEntity<Map<String, Object>> r = respuesta(HttpStatus.OK, true, "Solicitante registrado correctamente");
            r.getBody().put("idSolicitante", solicitante.getIdSolicitante());
            return r;

        } catch (Exception e) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            e.printStackTrace();
            return respuesta(HttpStatus.INTERNAL_SERVER_ERROR, false, "Error al registrar: " + e.getMessage());
        }
    }

    // Listar solicitantes (para recargar select)
    @ValidarUsuarioAutenticado
    @GetMapping("/solicitante/listar")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> listarSolicitantes(HttpServletRequest request) {
        if (!puedeLeer(request)) return sinPermisoLectura();
        Map<String, Object> response = new HashMap<>();

        try {
            List<Map<String, Object>> solicitantesData = new ArrayList<>();
            for (Solicitante sol : solicitanteService.findAll()) {
                Map<String, Object> solData = new HashMap<>();
                solData.put("idSolicitante", sol.getIdSolicitante());
                solData.put("nombre", sol.getNombre());
                solData.put("cargo", sol.getCargo());
                solicitantesData.add(solData);
            }

            response.put("ok", true);
            response.put("solicitantes", solicitantesData);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("ok", false);
            response.put("msg", "Error al listar: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

}
