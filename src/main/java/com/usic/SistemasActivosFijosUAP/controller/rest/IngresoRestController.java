package com.usic.SistemasActivosFijosUAP.controller.rest;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.usic.SistemasActivosFijosUAP.model.IService.IngresoService;
import com.usic.SistemasActivosFijosUAP.model.entity.Ingreso;
import com.usic.SistemasActivosFijosUAP.model.entity.IngresoDetalle;
import com.usic.SistemasActivosFijosUAP.model.entity.Responsable;

import lombok.RequiredArgsConstructor;

/**
 * Endpoints JSON que alimentan la vista de seguimiento de ingresos de bienes ajenos
 * (lista con filtros + detalle con galería de fotos).
 *
 * Consumido por templates/operaciones/ingreso/modulo.html y seguimiento/ingreso/vista.html.
 */
@RestController
@RequestMapping("/api/ingresos")
@RequiredArgsConstructor
public class IngresoRestController {

    private final IngresoService ingresoService;

    @GetMapping
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listar(
            @RequestParam(required = false) Long oficina,
            @RequestParam(required = false) Long responsable,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String desde,
            @RequestParam(required = false) String hasta) {

        List<Map<String, Object>> salida = new ArrayList<>();

        for (Ingreso ing : ingresoService.findAllWithTodo()) {
            String estadoIngreso = calcularVigencia(ing);

            // Filtros
            if (oficina != null && (ing.getOficinaPropietario() == null
                    || !oficina.equals(ing.getOficinaPropietario().getIdOficina()))) {
                continue;
            }
            if (responsable != null && (ing.getResponsablePropietario() == null
                    || !responsable.equals(ing.getResponsablePropietario().getIdResponsable()))) {
                continue;
            }
            if (estado != null && !estado.isBlank() && !estado.equalsIgnoreCase(estadoIngreso)) {
                continue;
            }
            if (!dentroDeRango(ing.getFechaIngreso(), desde, hasta)) {
                continue;
            }

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("idIngreso", ing.getIdIngreso());
            m.put("numeroIngreso", "ING-" + String.format("%05d", ing.getIdIngreso()));
            m.put("fechaIngreso", ing.getFechaIngreso());
            m.put("fechaFin", ing.getFechaFin());
            m.put("responsablePropietario", nombreResponsable(ing.getResponsablePropietario()));
            m.put("oficinaPropietario", ing.getOficinaPropietario() != null ? ing.getOficinaPropietario().getNombre() : null);
            m.put("responsableAutorizador", nombreResponsable(ing.getResponsableAutoriza()));
            m.put("estadoIngreso", estadoIngreso);
            m.put("totalActivos", ing.getDetalles() != null ? ing.getDetalles().size() : 0);
            // Extras: nota del inmediato superior (la usa la vista del módulo).
            m.put("notaPath", ing.getNotaPath());
            m.put("notaNombre", ing.getNotaNombre());
            m.put("documentoReferencia", ing.getNotaNombre());
            salida.add(m);
        }
        return salida;
    }

    @GetMapping("/{id}/detalle")
    @Transactional(readOnly = true)
    public ResponseEntity<List<Map<String, Object>>> detalle(@PathVariable Long id) {
        Ingreso ing = ingresoService.findById(id);
        if (ing == null) {
            return ResponseEntity.notFound().build();
        }

        List<Map<String, Object>> detalles = new ArrayList<>();
        if (ing.getDetalles() != null) {
            for (IngresoDetalle d : ing.getDetalles()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("descripcion", d.getDescripcion());
                m.put("nombreActivo", d.getDescripcion());
                m.put("codigoActivo", null);
                m.put("estadoActivo", d.getEstadoActivo());
                m.put("marca", d.getMarca());
                m.put("modelo", d.getModelo());
                m.put("serie", d.getSerie());
                m.put("cantidad", d.getCantidad());
                m.put("observacionDetalle", null);

                List<Map<String, Object>> fotos = new ArrayList<>();
                if (d.getFotoPath() != null && !d.getFotoPath().isBlank()) {
                    Map<String, Object> f = new LinkedHashMap<>();
                    f.put("urlFoto", d.getFotoPath());
                    fotos.add(f);
                }
                m.put("fotos", fotos);
                detalles.add(m);
            }
        }
        return ResponseEntity.ok(detalles);
    }

    /** VIGENTE / FINALIZADO / ANULADO según estado y fecha de retiro (+3 meses). */
    private String calcularVigencia(Ingreso ing) {
        if (ing.getEstado() != null && ing.getEstado().equalsIgnoreCase("ANULADO")) {
            return "ANULADO";
        }
        LocalDate fin = parse(ing.getFechaFin());
        if (fin != null && LocalDate.now().isAfter(fin)) {
            return "FINALIZADO";
        }
        return "VIGENTE";
    }

    private boolean dentroDeRango(String fecha, String desde, String hasta) {
        LocalDate f = parse(fecha);
        if (f == null) {
            return true; // sin fecha válida no se filtra
        }
        LocalDate d = parse(desde);
        LocalDate h = parse(hasta);
        if (d != null && f.isBefore(d)) {
            return false;
        }
        if (h != null && f.isAfter(h)) {
            return false;
        }
        return true;
    }

    private LocalDate parse(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(s.trim());
        } catch (Exception ex) {
            return null;
        }
    }

    private String nombreResponsable(Responsable r) {
        if (r == null || r.getPersona() == null) {
            return null;
        }
        return r.getPersona().getNombreCompleto();
    }
}
