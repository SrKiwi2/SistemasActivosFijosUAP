package com.usic.SistemasActivosFijosUAP.controller.publico;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.usic.SistemasActivosFijosUAP.model.IService.IHojaRutaService;
import com.usic.SistemasActivosFijosUAP.model.IService.IMovimientoService;
import com.usic.SistemasActivosFijosUAP.model.entity.HojaRuta;
import com.usic.SistemasActivosFijosUAP.model.entity.Movimiento;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class PublicoController {

    private final IHojaRutaService hojaRutaService;
    private final IMovimientoService movimientoService;

    @GetMapping(value = "/")
    public String inicioPublico() {
        return "publico/inicio_publico";
    }

    @GetMapping("/informacion")
    public String informacion() {
        return "publico/informacion";
    }

    // ─────────────────────────────────────────────────────────────────────
    //  SEGUIMIENTO PÚBLICO DE HOJAS DE RUTA  (sin login)
    //  Solo expone estado + trayectoria. NO devuelve monto, certificación
    //  ni solicitante (datos sensibles). Búsqueda exacta por Tipo+Código+Gestión.
    // ─────────────────────────────────────────────────────────────────────

    @GetMapping("/seguimiento-hr")
    public String seguimientoHojaRutaPublico() {
        return "publico/seguimiento_hoja_ruta";
    }

    @GetMapping("/seguimiento-hr/buscar")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> buscarHojaRutaPublico(
            @RequestParam("tipo") String tipo,
            @RequestParam("codigo") String codigo,
            @RequestParam("gestion") Integer gestion) {

        Map<String, Object> resp = new HashMap<>();
        try {
            Optional<HojaRuta> opt = hojaRutaService.findByCodigo(codigo.trim());

            // Coincidencia estricta: tipo y gestión deben corresponder al código.
            if (opt.isEmpty()
                    || opt.get().getTipo() == null
                    || !opt.get().getTipo().equalsIgnoreCase(tipo.trim())
                    || !gestion.equals(opt.get().getGestion())) {
                resp.put("ok", false);
                resp.put("msg", "No se encontró una hoja de ruta con esos datos.");
                return ResponseEntity.ok(resp);
            }

            HojaRuta hr = opt.get();
            List<Movimiento> movimientos = movimientoService.findByHojaRuta(hr.getIdHojaRuta());

            Map<String, Object> hojaData = new HashMap<>();
            hojaData.put("codigo", hr.getCodigo());
            hojaData.put("tipo", hr.getTipo());
            hojaData.put("gestion", hr.getGestion());
            hojaData.put("estadoActual",
                    movimientos.isEmpty()
                            ? "SIN MOVIMIENTOS"
                            : estadoTexto(movimientos.get(0).getEstadoMovimiento()));

            List<Map<String, Object>> movData = new ArrayList<>();
            for (Movimiento m : movimientos) {
                Map<String, Object> d = new HashMap<>();
                d.put("estado", estadoTexto(m.getEstadoMovimiento()));
                d.put("fecha", m.getFecha() != null ? m.getFecha().toString() : "");
                d.put("hora", m.getHora() != null ? m.getHora().toString() : "");
                d.put("origen", m.getUnidadOrigen() != null ? m.getUnidadOrigen().getNombre() : "—");
                d.put("destino", m.getUnidadDestino() != null ? m.getUnidadDestino().getNombre() : "—");
                movData.add(d);
            }

            resp.put("ok", true);
            resp.put("hojaRuta", hojaData);
            resp.put("movimientos", movData);
            return ResponseEntity.ok(resp);

        } catch (Exception e) {
            resp.put("ok", false);
            resp.put("msg", "Ocurrió un error al consultar. Intente nuevamente.");
            return ResponseEntity.ok(resp);
        }
    }

    /** 1=RECIBIDO, 2=ENVIADO, 3=ARCHIVADO (mismo criterio que HojaRutaController). */
    private String estadoTexto(String num) {
        if (num == null) return "DESCONOCIDO";
        switch (num) {
            case "1": return "RECIBIDO";
            case "2": return "ENVIADO";
            case "3": return "ARCHIVADO";
            default:  return "DESCONOCIDO";
        }
    }
}
