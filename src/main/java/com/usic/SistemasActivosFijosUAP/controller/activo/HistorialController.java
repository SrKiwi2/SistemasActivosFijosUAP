package com.usic.SistemasActivosFijosUAP.controller.activo;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.usic.SistemasActivosFijosUAP.anotacion.ValidarUsuarioAutenticado;
import com.usic.SistemasActivosFijosUAP.model.dao.IHistorialActivoDao;
import com.usic.SistemasActivosFijosUAP.model.entity.HistorialActivo;

import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/administracion/historial")
@RequiredArgsConstructor
public class HistorialController {

    private final IHistorialActivoDao historialRepo;

    @ValidarUsuarioAutenticado
    @GetMapping("/vista")
    public String vistaHistorialActivo() {
        return "activo/historialActivo";
    }

    @GetMapping("/historial")
    @ResponseBody
    public ResponseEntity<?> historialPorCodigo(@RequestParam String codigo) {
        try {
            List<HistorialActivo> lista = historialRepo.findByCodigoActivoOrderByFechaEventoDesc(codigo);
            List<Map<String, Object>> result = lista.stream().map(h -> {
                Map<String, Object> m = new java.util.LinkedHashMap<>();
                m.put("id",            h.getIdHistorial());
                m.put("tipoEvento",    h.getTipoEvento());
                m.put("fechaEvento",   h.getFechaEvento().toString());
                m.put("descripcion",   h.getDescripcionEvento());
                m.put("ofAnterior",    h.getNombreOficinaAnterior());
                m.put("ofNueva",       h.getNombreOficinaNueva());
                m.put("respAnterior",  h.getNombreRespAnterior());
                m.put("respNuevo",     h.getNombreRespNuevo());
                m.put("usuario",       h.getNombreUsuario());
                m.put("nroTrf",        h.getTransferencia() != null
                                    ? h.getTransferencia().getNumeroTransferencia() : null);
                return m;
            }).toList();
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("ok", false, "msg", e.getMessage()));
        }
    }
    
    // ─────────────────────────────────────────────────────────────────────────────
    // POST /administracion/activo/historial/por-codigos
    // ─────────────────────────────────────────────────────────────────────────────
    @PostMapping("/historial/por-codigos")
    @ResponseBody
    public ResponseEntity<?> historialPorCodigos(@RequestBody Map<String, List<String>> body) {
        try {
            List<String> codigos = body.get("codigos");
            if (codigos == null || codigos.isEmpty())
                return ResponseEntity.badRequest().body(Map.of("ok", false, "msg", "Sin códigos."));
    
            List<HistorialActivo> lista = historialRepo.findByCodigos(codigos);
            List<Map<String, Object>> result = lista.stream().map(h -> {
                Map<String, Object> m = new java.util.LinkedHashMap<>();
                m.put("id",           h.getIdHistorial());
                m.put("codigoActivo", h.getCodigoActivo());
                m.put("tipoEvento",   h.getTipoEvento());
                m.put("fechaEvento",  h.getFechaEvento().toString());
                m.put("descripcion",  h.getDescripcionEvento());
                m.put("ofAnterior",   h.getNombreOficinaAnterior());
                m.put("ofNueva",      h.getNombreOficinaNueva());
                m.put("respAnterior", h.getNombreRespAnterior());
                m.put("respNuevo",    h.getNombreRespNuevo());
                m.put("usuario",      h.getNombreUsuario());
                m.put("nroTrf",       h.getTransferencia() != null
                                    ? h.getTransferencia().getNumeroTransferencia() : null);
                return m;
            }).toList();
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("ok", false, "msg", e.getMessage()));
        }
    }
}
