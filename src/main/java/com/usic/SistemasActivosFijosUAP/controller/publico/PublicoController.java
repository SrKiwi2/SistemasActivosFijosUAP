package com.usic.SistemasActivosFijosUAP.controller.publico;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.usic.SistemasActivosFijosUAP.config.RolesSciaf;
import com.usic.SistemasActivosFijosUAP.model.IService.IHojaRutaService;
import com.usic.SistemasActivosFijosUAP.model.IService.IMovimientoService;
import com.usic.SistemasActivosFijosUAP.model.entity.HojaRuta;
import com.usic.SistemasActivosFijosUAP.model.entity.Movimiento;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class PublicoController {

    private final IHojaRutaService hojaRutaService;
    private final IMovimientoService movimientoService;

    /**
     * Portada pública con el inicio de sesión. Quien ya tiene sesión va a su pantalla de
     * inicio: antes, al volver con "Atrás" hasta acá, se veía la portada con el usuario
     * arriba pero sin el sistema, y no quedaba claro si estaba adentro o afuera.
     */
    @GetMapping(value = "/")
    public String inicioPublico(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        // Con usuario Y persona: es lo mismo que exige @ValidarUsuarioAutenticado en la pantalla
        // de destino. Con uno solo, el destino devolvería acá y quedaría en un bucle.
        if (session != null && session.getAttribute("usuario") != null
                && session.getAttribute("persona") != null) {
            return "redirect:" + RolesSciaf.rutaInicio((String) session.getAttribute("nombre_rol"));
        }
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
            // Búsqueda exacta por la combinación única tipo+código+gestión (igual que el
            // módulo interno). NO usar findByCodigo: el código puede repetirse entre
            // tipos/gestiones, y la query derivada lanzaría NonUniqueResultException.
            HojaRuta hr = hojaRutaService.findByTipoAndCodigoAndGestion(
                    tipo.trim(), codigo.trim(), gestion);

            if (hr == null) {
                resp.put("ok", false);
                resp.put("msg", "No se encontró una hoja de ruta con esos datos.");
                return ResponseEntity.ok(resp);
            }

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
