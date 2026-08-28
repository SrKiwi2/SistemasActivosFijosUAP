package com.usic.SistemasActivosFijosUAP.controller.movil;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.usic.SistemasActivosFijosUAP.config.movil.UsuarioMovilPrincipal;
import com.usic.SistemasActivosFijosUAP.model.dao.IEstadoActivoDao;
import com.usic.SistemasActivosFijosUAP.model.dto.control.AbrirLevantamientoRequest;
import com.usic.SistemasActivosFijosUAP.model.dto.control.CerrarLevantamientoRequest;
import com.usic.SistemasActivosFijosUAP.model.dto.control.LevantamientoDTO;
import com.usic.SistemasActivosFijosUAP.model.dto.control.MarcasLoteRequest;
import com.usic.SistemasActivosFijosUAP.model.dto.control.ResumenCierreDTO;
import com.usic.SistemasActivosFijosUAP.model.dto.control.ResumenMarcasDTO;
import com.usic.SistemasActivosFijosUAP.model.dto.control.TileOficinaDTO;
import com.usic.SistemasActivosFijosUAP.model.dto.control.TilePredioDTO;
import com.usic.SistemasActivosFijosUAP.model.entity.Inventario;
import com.usic.SistemasActivosFijosUAP.model.service.control.ControlActivosService;
import com.usic.SistemasActivosFijosUAP.model.service.movil.PermisosMovil;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Levantamiento de activos desde la app — {@code /api/movil/levantamiento/**}.
 *
 * <p>Cuelga de la cadena JWT de {@code /api/movil/**} y exige
 * {@code MOV_INVENTARIO}, el permiso que ya estaba sembrado en el catálogo para
 * la toma de inventario.
 *
 * <p>Las tres operaciones del recorrido son idempotentes por diseño: el trabajo
 * de campo se hace sin señal y la app reenvía su cola a ciegas al recuperar
 * conexión. Ver {@link ControlActivosService}.
 */
@RestController
@RequestMapping("/api/movil/levantamiento")
@RequiredArgsConstructor
public class LevantamientoMovilController {

    private final ControlActivosService servicio;
    private final PermisosMovil permisos;
    private final IEstadoActivoDao estadoActivoDao;

    // ── Dónde levantar ───────────────────────────────────────────────────────

    @GetMapping("/predios")
    public ResponseEntity<List<TilePredioDTO>> predios() {
        permisos.exigir(PermisosMovil.INVENTARIO);
        return ResponseEntity.ok(servicio.mapaPredios());
    }

    @GetMapping("/oficinas")
    public ResponseEntity<List<TileOficinaDTO>> oficinas(@RequestParam Long idPredio) {
        permisos.exigir(PermisosMovil.INVENTARIO);
        return ResponseEntity.ok(servicio.mapaOficinas(idPredio));
    }

    /**
     * Catálogo de condiciones para anotar en campo ("roto", "en desuso"…).
     * Va con el paquete offline: sin él, quien recorre no podría dejar
     * constancia del estado de un activo estando sin señal.
     */
    @GetMapping("/estados")
    public ResponseEntity<List<Map<String, Object>>> estados() {
        permisos.exigir(PermisosMovil.INVENTARIO);
        List<Map<String, Object>> lista = estadoActivoDao.findAll().stream()
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", e.getIdEstadoActivo());
                    m.put("nombre", e.getNombre());
                    m.put("codigo", e.getCodigo());
                    return m;
                })
                .toList();
        return ResponseEntity.ok(lista);
    }

    // ── El recorrido ─────────────────────────────────────────────────────────

    /**
     * Abre el levantamiento y devuelve el <b>paquete offline</b>: la lista
     * completa de activos esperados en esa oficina. Es el único caso del módulo
     * móvil donde se descarga la lista por adelantado — acotada a una oficina
     * son cientos de filas, no las más de 30.000 del maestro.
     */
    @PostMapping("/abrir")
    public ResponseEntity<LevantamientoDTO> abrir(@Valid @RequestBody AbrirLevantamientoRequest req) {
        UsuarioMovilPrincipal quien = permisos.exigir(PermisosMovil.INVENTARIO);
        return ResponseEntity.ok(servicio.abrir(req, quien.idUsuario(), Inventario.ORIGEN_MOVIL));
    }

    /** Re-descarga del paquete: cambio de teléfono, reinstalación o caché perdida. */
    @GetMapping("/{id}/paquete")
    public ResponseEntity<LevantamientoDTO> paquete(@PathVariable Long id) {
        permisos.exigir(PermisosMovil.INVENTARIO);
        return ResponseEntity.ok(servicio.obtener(id));
    }

    /** Descarga de la cola offline. Reenviar el mismo lote es seguro. */
    @PostMapping("/{id}/marcas")
    public ResponseEntity<ResumenMarcasDTO> marcas(@PathVariable Long id,
                                                   @Valid @RequestBody MarcasLoteRequest req) {
        permisos.exigir(PermisosMovil.INVENTARIO);
        return ResponseEntity.ok(servicio.aplicarMarcas(id, req));
    }

    @PostMapping("/{id}/cerrar")
    public ResponseEntity<ResumenCierreDTO> cerrar(@PathVariable Long id,
                                                   @RequestBody(required = false) CerrarLevantamientoRequest req) {
        UsuarioMovilPrincipal quien = permisos.exigir(PermisosMovil.INVENTARIO);
        return ResponseEntity.ok(servicio.cerrar(id, req, quien.usuario()));
    }

    @GetMapping("/mios")
    public ResponseEntity<List<LevantamientoDTO>> mios() {
        UsuarioMovilPrincipal quien = permisos.exigir(PermisosMovil.INVENTARIO);
        return ResponseEntity.ok(servicio.levantamientosDe(quien.idUsuario()));
    }
}
