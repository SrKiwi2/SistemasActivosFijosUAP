package com.usic.SistemasActivosFijosUAP.controller.Seguimieto;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.usic.SistemasActivosFijosUAP.anotacion.ValidarUsuarioAutenticado;
import com.usic.SistemasActivosFijosUAP.model.IService.ITransferenciaService;
import com.usic.SistemasActivosFijosUAP.model.IService.IUsuarioService;
import com.usic.SistemasActivosFijosUAP.model.entity.Activo;
import com.usic.SistemasActivosFijosUAP.model.entity.Oficina;
import com.usic.SistemasActivosFijosUAP.model.entity.Responsable;
import com.usic.SistemasActivosFijosUAP.model.entity.Transferencia;
import com.usic.SistemasActivosFijosUAP.model.entity.TransferenciaDetalle;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;

import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/administracion/transferencia")
@RequiredArgsConstructor
public class CTransferenciaActivoController {

    private final ITransferenciaService transferenciaService;
    private final IUsuarioService usuarioService;

    @ValidarUsuarioAutenticado
    @GetMapping("/vista")
    public String vista_activos_trans() {
        return "/seguimiento/transferencia/vista";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/tabla_transferencias")
    public String tabla_activos_trans(Model model,
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String motivo,
            @RequestParam(required = false) String buscar,
            @RequestParam(required = false) String desde,
            @RequestParam(required = false) String hasta) {
        // La vista siempre mandó estos filtros, pero el controlador los ignoraba y la
        // tabla mostraba todo igual. Se filtra en memoria: es la misma lista que ya se leía.
        LocalDate fDesde = fecha(desde);
        LocalDate fHasta = fecha(hasta);
        String texto = buscar != null ? buscar.trim().toUpperCase() : "";

        // Más reciente primero: el listado venía en el orden que devolvía la base, que
        // no es ninguno en particular, y así el corte por mes de la tabla es coherente.
        List<Transferencia> transferencias = transferenciaService.findAll().stream()
                .filter(t -> vacio(tipo) || tipo.equals(t.getTipo()))
                .filter(t -> vacio(estado) || estado.equals(t.getEstadoProceso()))
                .filter(t -> coincideMotivo(t, motivo))
                .filter(t -> fDesde == null || (t.getFechaTransferencia() != null && !t.getFechaTransferencia().isBefore(fDesde)))
                .filter(t -> fHasta == null || (t.getFechaTransferencia() != null && !t.getFechaTransferencia().isAfter(fHasta)))
                .filter(t -> texto.isEmpty() || textoDe(t).contains(texto))
                .sorted(Comparator.comparing(Transferencia::getFechaTransferencia,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        model.addAttribute("transferencias", transferencias);
        // La auditoría guarda el id del usuario; sin este mapa la pantalla mostraba
        // el número crudo ("12") en vez de quién registró o modificó.
        model.addAttribute("mapaUsuarios", mapaDeUsuarios(transferencias));
        return "/seguimiento/transferencia/tabla_registro";
    }

    /**
     * Detalle de los activos de una transferencia.
     *
     * <p>La pestaña "Activos Transferidos" ya lo llamaba, pero el endpoint no existía:
     * de ahí el "Error al cargar activos". Devuelve, por cada bien, el snapshot que
     * quedó guardado en la transferencia (código, descripción, ubicaciones) y el estado
     * actual del activo, para poder mostrar la ficha completa al desplegar la fila.
     *
     * <p>{@code @Transactional} para que las relaciones perezosas (oficina, predio,
     * responsable, grupo contable) se puedan leer al armar la respuesta.
     */
    @ValidarUsuarioAutenticado
    @GetMapping("/{id}/detalles-json")
    @ResponseBody
    @Transactional(readOnly = true)
    public ResponseEntity<?> detallesJson(@PathVariable Long id) {
        Transferencia trf = transferenciaService.findById(id);
        if (trf == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("ok", false, "msg", "Transferencia no encontrada"));
        }

        List<Map<String, Object>> detalles = new ArrayList<>();
        List<TransferenciaDetalle> lista = trf.getDetalles() != null
                ? new ArrayList<>(trf.getDetalles())
                : List.of();

        for (TransferenciaDetalle d : lista) {
            Activo a = d.getActivo();
            Map<String, Object> m = new LinkedHashMap<>();

            m.put("idDetalle", d.getIdDetalle());
            m.put("idActivo", a != null ? a.getIdActivo() : null);

            // Snapshot de la transferencia primero; si no quedó guardado, el activo vivo.
            m.put("codigoActivo", primero(d.getCodigoActivo(), a != null ? a.getCodigo() : null));
            m.put("descripcionActivo", primero(d.getDescripcionActivo(), a != null ? a.getDescripcion() : null));
            m.put("costoActivo", d.getCostoActivo() != null
                    ? d.getCostoActivo()
                    : (a != null ? a.getCosto() : null));

            m.put("ubicacionOrigen", d.getUbicacionOrigen());
            m.put("ubicacionActual", d.getUbicacionActual());
            m.put("observacionDetalle", d.getObservacionDetalle());

            m.put("oficinaAnterior", datosOficina(d.getOficinaAnterior()));
            m.put("oficinaDestino", datosOficina(d.getOficinaDestino()));
            m.put("responsableAnterior", datosResponsable(d.getResponsableAnterior()));
            m.put("responsableDestino", datosResponsable(d.getResponsableDestino()));

            // Estado actual del bien: dónde está hoy y si el cambio llegó al VSIAF.
            Map<String, Object> actual = new LinkedHashMap<>();
            if (a != null) {
                actual.put("codigo", a.getCodigo());
                actual.put("descripcion", a.getDescripcion());
                actual.put("estado", a.getEstadoActivo() != null ? a.getEstadoActivo().getNombre() : null);
                actual.put("grupoContable", a.getGrupoContable() != null ? a.getGrupoContable().getNombre() : null);
                actual.put("auxiliar", a.getAuxiliar() != null ? a.getAuxiliar().getNombre() : null);
                actual.put("oficina", datosOficina(a.getOficina()));
                actual.put("responsable", datosResponsable(a.getResponsable()));
                actual.put("fechaAdquisicion", a.getFechaAdquisicion() != null
                        ? a.getFechaAdquisicion().toString() : null);
                actual.put("vidaUtil", a.getVidaUtil());
                actual.put("observ", a.getObserv());
                actual.put("sincVsiaf", a.getSincVsiaf());
                actual.put("sincVsiafMensaje", a.getSincVsiafMensaje());
                actual.put("sincVsiafFecha", a.getSincVsiafFecha() != null
                        ? a.getSincVsiafFecha().toString() : null);
            }
            m.put("activoActual", actual);

            detalles.add(m);
        }

        return ResponseEntity.ok(detalles);
    }

    /** Oficina con su código: en el VSIAF la gente identifica la oficina por el código. */
    private Map<String, Object> datosOficina(Oficina o) {
        if (o == null) {
            return null;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", o.getIdOficina());
        m.put("codigo", o.getCodOfi());
        m.put("nombre", o.getNombre());
        if (o.getPredio() != null) {
            m.put("predio", primero(o.getPredio().getDescrip(), o.getPredio().getUnidad()));
        }
        return m;
    }

    private Map<String, Object> datosResponsable(Responsable r) {
        if (r == null) {
            return null;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getIdResponsable());
        m.put("codigo", r.getCodigoFuncionario());
        m.put("nombre", r.getPersona() != null ? r.getPersona().getNombreCompleto() : null);
        m.put("cargo", r.getCargo() != null ? r.getCargo().getNombre() : null);
        return m;
    }

    /** Id de usuario → nombre, para las fechas de registro/modificación. */
    private Map<Long, String> mapaDeUsuarios(List<Transferencia> transferencias) {
        Set<Long> ids = new HashSet<>();
        for (Transferencia t : transferencias) {
            if (t.getRegistroIdUsuario() != null) {
                ids.add(t.getRegistroIdUsuario());
            }
            if (t.getModificacionIdUsuario() != null) {
                ids.add(t.getModificacionIdUsuario());
            }
        }
        if (ids.isEmpty()) {
            return Map.of();
        }
        return usuarioService.findAllByIdUsuarioIn(ids).stream()
                .collect(Collectors.toMap(Usuario::getIdUsuario, Usuario::getUsuario, (a, b) -> a));
    }

    /**
     * "FALTANTES" = ida y vuelta de la custodia; "NORMAL" = el resto; también acepta
     * un motivo puntual (FALTANTE / DEVOLUCION_FALTANTE).
     */
    private boolean coincideMotivo(Transferencia t, String motivo) {
        if (vacio(motivo)) {
            return true;
        }
        String m = t.getMotivoFaltante();
        return switch (motivo) {
            case "FALTANTES" -> m != null;
            case "NORMAL" -> m == null;
            default -> motivo.equals(m);
        };
    }

    private String textoDe(Transferencia t) {
        StringBuilder sb = new StringBuilder();
        sb.append(t.getNumeroTransferencia()).append(' ')
          .append(t.getDocumentoReferencia()).append(' ')
          .append(t.getObservacion()).append(' ');
        for (Responsable r : new Responsable[] { t.getResponsableOrigen(), t.getResponsableDestino() }) {
            if (r != null) {
                sb.append(r.getCodigoFuncionario()).append(' ');
                if (r.getPersona() != null) sb.append(r.getPersona().getNombreCompleto()).append(' ');
            }
        }
        for (Oficina o : new Oficina[] { t.getOficinaOrigen(), t.getOficinaDestino() }) {
            if (o != null) sb.append(o.getCodOfi()).append(' ').append(o.getNombre()).append(' ');
        }
        return sb.toString().toUpperCase();
    }

    private LocalDate fecha(String valor) {
        if (vacio(valor)) {
            return null;
        }
        try {
            return LocalDate.parse(valor.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private boolean vacio(String s) {
        return s == null || s.isBlank();
    }

    private String primero(String preferido, String alternativo) {
        if (preferido != null && !preferido.isBlank()) {
            return preferido;
        }
        return (alternativo != null && !alternativo.isBlank()) ? alternativo : null;
    }
}
