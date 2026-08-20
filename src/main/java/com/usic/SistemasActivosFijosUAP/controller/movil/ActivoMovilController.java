package com.usic.SistemasActivosFijosUAP.controller.movil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.usic.SistemasActivosFijosUAP.config.movil.UsuarioMovilPrincipal;
import com.usic.SistemasActivosFijosUAP.model.IService.IUsuarioService;
import com.usic.SistemasActivosFijosUAP.model.dto.movil.ActivoDetalleDTO;
import com.usic.SistemasActivosFijosUAP.model.dto.movil.ActivoFichaMovilDTO;
import com.usic.SistemasActivosFijosUAP.model.dto.movil.EscaneoRequest;
import com.usic.SistemasActivosFijosUAP.model.dto.movil.EscaneoResultadoDTO;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;
import com.usic.SistemasActivosFijosUAP.model.service.movil.ActivoDetalleMovilService;
import com.usic.SistemasActivosFijosUAP.model.service.movil.EscaneoMovilService;
import com.usic.SistemasActivosFijosUAP.model.service.movil.PermisosMovil;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Escaneo y consulta de activos desde la app — {@code /api/movil/**}.
 */
@RestController
@RequestMapping("/api/movil")
@RequiredArgsConstructor
public class ActivoMovilController {

    private final EscaneoMovilService       escaneoService;
    private final ActivoDetalleMovilService detalleService;
    private final IUsuarioService           usuarioService;
    private final PermisosMovil             permisos;

    /**
     * Verifica una etiqueta escaneada (o un código tecleado) contra la base de
     * datos y devuelve las diferencias encontradas.
     */
    @PostMapping("/escaneo/verificar")
    public ResponseEntity<EscaneoResultadoDTO> verificar(@Valid @RequestBody EscaneoRequest req) {
        UsuarioMovilPrincipal principal = permisos.exigir(PermisosMovil.ESCANER);
        Usuario usuario = usuarioService.findById(principal.idUsuario());
        return ResponseEntity.ok(escaneoService.verificar(req, usuario));
    }

    /** Ficha completa: datos, historial, transferencias, asignaciones y mantenimientos. */
    @GetMapping("/activos/{codigo}/detalle")
    public ResponseEntity<ActivoDetalleDTO> detalle(@PathVariable String codigo) {
        permisos.exigir(PermisosMovil.ESCANER);
        return detalleService.detallePorCodigo(normalizar(codigo))
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** Solo la ficha, sin los listados. */
    @GetMapping("/activos/{codigo}")
    public ResponseEntity<ActivoFichaMovilDTO> ficha(@PathVariable String codigo) {
        permisos.exigir(PermisosMovil.ESCANER);
        return detalleService.fichaPorCodigo(normalizar(codigo))
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Resuelve una lista de códigos de una sola vez.
     *
     * <p>Es la llamada que usa la captura offline al recuperar conexión: se
     * escanearon 20 activos sin red y se completan todos en una petición, no en
     * veinte.
     */
    @PostMapping("/activos/lote")
    public ResponseEntity<Map<String, Object>> lote(@RequestBody Map<String, List<String>> cuerpo) {
        permisos.exigir(PermisosMovil.ESCANER);

        List<String> pedidos = cuerpo.getOrDefault("codigos", List.of()).stream()
                .map(this::normalizar)
                .filter(c -> c != null && !c.isBlank())
                .distinct()
                .toList();

        List<ActivoFichaMovilDTO> encontrados = detalleService.fichasPorCodigos(pedidos);

        List<String> hallados = encontrados.stream().map(ActivoFichaMovilDTO::codigo).toList();
        List<String> faltantes = new ArrayList<>(pedidos);
        faltantes.removeAll(hallados);

        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("ok", true);
        respuesta.put("activos", encontrados);
        respuesta.put("noEncontrados", faltantes);
        return ResponseEntity.ok(respuesta);
    }

    /**
     * Quita el prefijo de entidad si el cliente lo mandó en la URL
     * ({@code 148-01-04-02-03609} → {@code 01-04-02-03609}), para que dé igual
     * cuál de las dos formas se use.
     */
    private String normalizar(String codigo) {
        if (codigo == null) return null;
        String limpio = codigo.trim();
        return limpio.replaceFirst("^\\d{2,4}-(?=\\d{2}-\\d{2}-\\d{2}-\\d{3,6}$)", "");
    }
}
