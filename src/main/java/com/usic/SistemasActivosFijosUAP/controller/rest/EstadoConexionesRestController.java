package com.usic.SistemasActivosFijosUAP.controller.rest;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.usic.SistemasActivosFijosUAP.componet.MonitorConexionesService;
import com.usic.SistemasActivosFijosUAP.model.dto.interoperabilidad.EstadoConexionDto;
import com.usic.SistemasActivosFijosUAP.model.service.ColaVsiafDiagnosticoService;
import com.usic.SistemasActivosFijosUAP.model.dao.IDbfColaOrdenDao;
import com.usic.SistemasActivosFijosUAP.model.entity.DbfColaOrden;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

/**
 * Estado de las conexiones criticas (montajes CIFS del VSIAF + base de datos)
 * para el indicador del topbar.
 *
 * <p>Como la autorizacion HTTP del proyecto es abierta ({@code /api/**} esta en
 * {@code permitAll()}), el control de acceso se hace aqui contra el rol de la
 * sesion, igual que en el resto del sistema.</p>
 */
@RestController
@RequestMapping("/api/estado")
@RequiredArgsConstructor
public class EstadoConexionesRestController {

    private final MonitorConexionesService monitor;
    private final ColaVsiafDiagnosticoService colaDiagnostico;
    private final IDbfColaOrdenDao colaOrdenDao;

    /**
     * Latido para el preloader de las pantallas (sciaf-precarga.js): no toca disco, base ni
     * sesión. Si este responde rápido y la pantalla no, la demora es del servidor procesando;
     * si este también tarda, es la red.
     */
    @GetMapping("/ping")
    public ResponseEntity<Void> ping() {
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    /** Ultimo estado conocido: no toca disco, responde al instante. */
    @GetMapping("/conexiones")
    public ResponseEntity<?> conexiones(HttpSession session) {
        if (!esAdministrador(session)) return denegado();
        return ResponseEntity.ok(envolver(monitor.estadoActual()));
    }

    /** Fuerza una verificacion nueva (boton "Verificar ahora"). */
    @PostMapping("/conexiones/verificar")
    public ResponseEntity<?> verificar(HttpSession session) {
        if (!esAdministrador(session)) return denegado();
        return ResponseEntity.ok(envolver(monitor.verificarAhora()));
    }

    /**
     * Estado de la cola hacia el VSIAF: si el worker está trabajando, qué órdenes siguen
     * esperando y cuáles rechazó.
     * <p>
     * Es la respuesta a "guardé el cambio, el sistema dijo que sí, y en el VSIAF no está":
     * en modo cola el cambio viaja como archivo y lo aplica el worker de la VM Windows, así
     * que sin esto no había forma de ver desde el sistema que la orden seguía en la fila.
     */
    @GetMapping("/cola-vsiaf")
    public ResponseEntity<?> colaVsiaf(HttpSession session) {
        if (!esAdministrador(session)) return denegado();
        return ResponseEntity.ok(colaDiagnostico.diagnostico());
    }

    /** Órdenes reales de la cola, con filtros y paginación para revisar errores y confirmaciones. */
    @GetMapping("/cola-vsiaf/ordenes")
    public ResponseEntity<?> ordenesVsiaf(@RequestParam(defaultValue = "TODAS") String estado,
                                          @RequestParam(defaultValue = "0") int pagina,
                                          HttpSession session) {
        if (!esAdministrador(session)) return denegado();
        String filtro = estado.trim().toUpperCase();
        if (!List.of("TODAS", DbfColaOrden.ENCOLADA, DbfColaOrden.OK,
                DbfColaOrden.ERROR, DbfColaOrden.REINTENTADA).contains(filtro)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Estado de orden no válido."));
        }
        PageRequest paginacion = PageRequest.of(Math.max(0, Math.min(pagina, 100000)), 25,
                Sort.by(Sort.Direction.DESC, "idOrden"));
        Page<DbfColaOrden> ordenes = "TODAS".equals(filtro)
                ? colaOrdenDao.findAll(paginacion)
                : colaOrdenDao.findByEstadoOrderByIdOrdenDesc(filtro, paginacion);
        List<Map<String, Object>> filas = ordenes.getContent().stream().map(o -> {
            Map<String, Object> fila = new LinkedHashMap<>();
            fila.put("id", o.getIdOrden());
            fila.put("tabla", o.getTabla());
            fila.put("operacion", o.getOperacion());
            fila.put("referencia", o.getReferencia());
            fila.put("clave", o.getClave());
            fila.put("archivo", o.getArchivo());
            fila.put("usuario", o.getUsuario());
            fila.put("estado", o.getEstado());
            fila.put("mensaje", o.getMensaje());
            fila.put("intentos", o.getIntentos());
            fila.put("fechaEncolado", o.getFechaEncolado());
            fila.put("fechaResuelto", o.getFechaResuelto());
            return fila;
        }).toList();
        return ResponseEntity.ok(Map.of("ordenes", filas, "pagina", ordenes.getNumber(),
                "paginas", ordenes.getTotalPages(), "total", ordenes.getTotalElements()));
    }

    // =========================================================================

    private Map<String, Object> envolver(List<EstadoConexionDto> conexiones) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("estadoGlobal", monitor.estadoGlobal());
        cuerpo.put("conexiones", conexiones);
        return cuerpo;
    }

    private boolean esAdministrador(HttpSession session) {
        Object rol = session.getAttribute("nombre_rol");
        return rol != null
            && MonitorConexionesService.ROLES_MONITOR.contains(rol.toString().toUpperCase());
    }

    private ResponseEntity<Map<String, String>> denegado() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(Map.of("error", "Solo administradores pueden consultar el estado de las conexiones."));
    }
}
