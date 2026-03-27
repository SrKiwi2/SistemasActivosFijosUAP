package com.usic.SistemasActivosFijosUAP.controller.Seguimieto;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.usic.SistemasActivosFijosUAP.anotacion.ValidarUsuarioAutenticado;
import com.usic.SistemasActivosFijosUAP.model.IService.IAsignacionActivoService;
import com.usic.SistemasActivosFijosUAP.model.IService.IUsuarioService;
import com.usic.SistemasActivosFijosUAP.model.entity.Activo;
import com.usic.SistemasActivosFijosUAP.model.entity.AsignacionActivo;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller
@RequestMapping("/administracion/asignacion")
@RequiredArgsConstructor
public class CAsignacionActivoController {

    private final IAsignacionActivoService asignacionActivoService;
    private final IUsuarioService usuarioService;

    @ValidarUsuarioAutenticado
    @GetMapping("/vista")
    public String vista_activos_nuevos() {
        return "/seguimiento/asignacion/vista";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/tabla_activos_nuevos")
    public String tabla_activos_nuevos(
        @RequestParam(required = false) String tipo,
        @RequestParam(required = false) String estado,
        @RequestParam(required = false) String buscar,
        @RequestParam(required = false) String desde,
        @RequestParam(required = false) String hasta,
        Model model) {

        // List<AsignacionActivo> asignaciones = asignacionActivoService.findAll();
        List<AsignacionActivo> asignaciones = asignacionActivoService.buscarConFiltros(tipo, estado, buscar, desde, hasta);

        // 1. Recolectar IDs únicos (usamos Set para que no haya repetidos)
        Set<Long> idsUsuarios = new HashSet<>();
        for (AsignacionActivo asig : asignaciones) {
            if (asig.getRegistroIdUsuario() != null) idsUsuarios.add(asig.getRegistroIdUsuario());
            if (asig.getModificacionIdUsuario() != null) idsUsuarios.add(asig.getModificacionIdUsuario());
        }

        // 2. Buscar todos esos usuarios en una sola consulta
        // (Asegúrate de tener este método en tu usuarioService/Repository)
        List<Usuario> usuariosAuditores = usuarioService.findAllByIdUsuarioIn(idsUsuarios);

        // 3. Crear el Mapa <IdUsuario, Nombre Completo>
        Map<Long, String> mapaUsuarios = usuariosAuditores.stream()
            .collect(Collectors.toMap(
                Usuario::getIdUsuario, 
                u -> {
                    String nombreAMostrar;
                    if (u.getPersona() != null) {
                        nombreAMostrar = u.getUsuario();
                    } else {
                        nombreAMostrar = String.valueOf(u.getUsuario()); 
                    }
                    return nombreAMostrar;
                }
            ));

        // 4. Enviar los datos a la vista
        model.addAttribute("asignaciones", asignaciones);
        model.addAttribute("mapaUsuarios", mapaUsuarios);

        return "/seguimiento/asignacion/tabla_registro";
    }

    @GetMapping("/asignaciones/{id}/detalles-json")
    @ResponseBody
    public ResponseEntity<?> obtenerDetallesAsignacionJson(@PathVariable Long id) {
        try {
            Optional<AsignacionActivo> asigOpt = asignacionActivoService.findByIdConDetalles(id);

            if (asigOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("ok", false, "msg", "Asignación no encontrada"));
            }

            AsignacionActivo asig = asigOpt.get();

            List<Map<String, Object>> listaDetalles = asig.getDetalles().stream().map(d -> {
                Map<String, Object> map = new LinkedHashMap<>();

                Activo activo = d.getActivo();

                // Código — primero snapshot, luego entidad viva
                map.put("codigo",
                    d.getCodigoActivoSnapshot() != null
                        ? d.getCodigoActivoSnapshot()
                        : (activo != null ? activo.getCodigo() : "—"));

                // Descripción
                map.put("descripcion",
                    d.getDescripcionActivoSnapshot() != null
                        ? d.getDescripcionActivoSnapshot()
                        : (activo != null ? activo.getDescripcion() : "—"));

                // Estado del activo (snapshot primero)
                map.put("estadoActivo",
                    d.getEstadoActivoSnapshot() != null
                        ? d.getEstadoActivoSnapshot()
                        : (activo != null && activo.getEstadoActivo() != null
                            ? activo.getEstadoActivo().getNombre()
                            : "—"));

                // Oficina actual del activo
                String oficinaNombre = "—";
                String predioNombre  = "—";
                if (activo != null && activo.getOficina() != null) {
                    oficinaNombre = activo.getOficina().getNombre() != null
                        ? activo.getOficina().getNombre() : "—";

                    if (activo.getOficina().getPredio() != null) {
                        predioNombre = activo.getOficina().getPredio().getDescrip() != null
                            ? activo.getOficina().getPredio().getDescrip()
                            : (activo.getOficina().getPredio().getUnidad() != null
                                ? activo.getOficina().getPredio().getUnidad()
                                : "—");
                    }
                }
                map.put("oficina", oficinaNombre);
                map.put("predio",  predioNombre);

                // Fecha de adquisición
                map.put("fechaAdquisicion",
                    activo != null && activo.getFechaAdquisicion() != null
                        ? activo.getFechaAdquisicion().toString()   // ISO: "2023-04-15"
                        : "—");

                return map;
            }).toList();

            return ResponseEntity.ok(listaDetalles);

        } catch (Exception e) {
            log.error("[ASIGNACION-DETALLE] Error cargando detalles id={}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("ok", false, "msg", "Error al cargar detalles: " + e.getMessage()));
        }
    }
}
