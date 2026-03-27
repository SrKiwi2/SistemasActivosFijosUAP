package com.usic.SistemasActivosFijosUAP.controller.activo;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.usic.SistemasActivosFijosUAP.anotacion.ValidarUsuarioAutenticado;
import com.usic.SistemasActivosFijosUAP.model.IService.IAsignacionActivoService;
import com.usic.SistemasActivosFijosUAP.model.IService.IPredioServicio;
import com.usic.SistemasActivosFijosUAP.model.entity.AsignacionActivo;
import com.usic.SistemasActivosFijosUAP.model.entity.Predio;

import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/administracion/asignar")
@RequiredArgsConstructor
public class AsignacionActivoController {

    private final IPredioServicio predioServicio;
    private final IAsignacionActivoService asignacionActivoService;
    
    @ValidarUsuarioAutenticado
    @GetMapping("/asignacionActivo")
    public String asignarActivo(Model model) {
        List<Predio> listarDePredio = predioServicio.listarPredios();
        model.addAttribute("predios", listarDePredio);
        return "activo/asignacionActivos";
    }

    @GetMapping("/asignaciones/{id}/detalles-json")
    @ResponseBody
    public ResponseEntity<?> obtenerDetallesAsignacionJson(@PathVariable Long id) {
        try {
            // Buscamos la asignación con sus detalles (usando el método que arreglamos en el DAO)
            Optional<AsignacionActivo> asigOpt = asignacionActivoService.findByIdConDetalles(id);
            
            if (asigOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("ok", false, "msg", "Asignación no encontrada"));
            }

            AsignacionActivo asig = asigOpt.get();
            
            // Transformamos los detalles a una lista de Maps (JSON)
            // Esto es CRÍTICO para evitar el error de recursión infinita de Jackson
            List<Map<String, Object>> listaDetalles = asig.getDetalles().stream().map(d -> {
                Map<String, Object> map = new HashMap<>();
                map.put("idDetalle", d.getIdDetalle());
                
                // Usamos los campos snapshot que guardan la "foto" del activo al momento de asignar
                map.put("codigo", d.getCodigoActivoSnapshot() != null ? d.getCodigoActivoSnapshot() : (d.getActivo() != null ? d.getActivo().getCodigo() : "—"));
                map.put("descripcion", d.getDescripcionActivoSnapshot() != null ? d.getDescripcionActivoSnapshot() : "—");
                map.put("estado", d.getEstadoActivoSnapshot() != null ? d.getEstadoActivoSnapshot() : "—");
                map.put("costo", d.getCostoActivoSnapshot() != null ? d.getCostoActivoSnapshot() : 0.0);
                map.put("observacion", d.getObservacionDetalle() != null ? d.getObservacionDetalle() : "");
                
                // Si necesitas algún dato "vivo" del activo, puedes sacarlo de d.getActivo()
                if (d.getActivo() != null && d.getActivo().getGrupoContable() != null) {
                    map.put("grupoContable", d.getActivo().getGrupoContable().getNombre());
                } else {
                    map.put("grupoContable", "—");
                }
                
                return map;
            }).toList();

            return ResponseEntity.ok(listaDetalles);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("ok", false, "msg", "Error al cargar detalles: " + e.getMessage()));
        }
    }
}
