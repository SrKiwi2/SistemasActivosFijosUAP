package com.usic.SistemasActivosFijosUAP.controller.Seguimieto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
import com.usic.SistemasActivosFijosUAP.model.IService.IConfiguracionGestionService;
import com.usic.SistemasActivosFijosUAP.model.IService.IUsuarioService;
import com.usic.SistemasActivosFijosUAP.model.dto.ResumenAsignacionDTO;
import com.usic.SistemasActivosFijosUAP.model.entity.Activo;
import com.usic.SistemasActivosFijosUAP.model.entity.AsignacionActivo;
import com.usic.SistemasActivosFijosUAP.model.entity.ConfiguracionGestion;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;
import com.usic.SistemasActivosFijosUAP.config.Encriptar;

import jakarta.servlet.http.HttpServletRequest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller
@RequestMapping("/administracion/asignacion")
@RequiredArgsConstructor
public class CAsignacionActivoController {

    private final IAsignacionActivoService asignacionActivoService;
    private final IUsuarioService usuarioService;
    private final IConfiguracionGestionService configuracionGestionService;

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

        // 4. Carpeta de Drive por gestión (año de la asignación), para el botón "Ver en Drive".
        //    Solo se incluye la gestión que tenga carpeta configurada.
        Set<Integer> gestiones = new HashSet<>();
        for (AsignacionActivo asig : asignaciones) {
            if (asig.getFechaAsignacion() != null) gestiones.add(asig.getFechaAsignacion().getYear());
        }
        Map<Integer, String> carpetasPorGestion = new HashMap<>();
        for (Integer g : gestiones) {
            configuracionGestionService.findByGestion(g)
                .map(ConfiguracionGestion::getCarpetaDrive)
                .filter(id -> id != null && !id.isBlank())
                .ifPresent(id -> carpetasPorGestion.put(g, id));
        }

        // 5. Totales por asignación (costo y avance hacia el VSIAF), en una sola consulta.
        Map<Long, ResumenAsignacionDTO> resumenes = asignacionActivoService.resumenPorAsignacion(
            asignaciones.stream().map(AsignacionActivo::getIdAsignacionActivo).toList());

        // 6. Enviar los datos a la vista
        model.addAttribute("asignaciones", asignaciones);
        model.addAttribute("mapaUsuarios", mapaUsuarios);
        model.addAttribute("carpetasPorGestion", carpetasPorGestion);
        model.addAttribute("resumenes", resumenes);

        return "/seguimiento/asignacion/tabla_registro";
    }

    /** Roles que pueden corregir un activo ya registrado (mismo criterio que el endpoint que lo aplica). */
    private static final Set<String> ROLES_EDICION = Set.of("ADMINISTRADOR", "SUPER USUARIO");

    /**
     * Id cifrado del activo, para usarlo desde el navegador. Si el cifrado falla se
     * devuelve null: la fila se muestra igual, solo pierde el botón de corregir.
     */
    private String cifrarId(Activo activo) {
        if (activo == null || activo.getIdActivo() == null) return null;
        try {
            return Encriptar.encrypt(String.valueOf(activo.getIdActivo()));
        } catch (Exception e) {
            log.warn("[ASIGNACION-DETALLE] No se pudo cifrar el id del activo {}: {}",
                     activo.getIdActivo(), e.getMessage());
            return null;
        }
    }

    @GetMapping("/asignaciones/{id}/detalles-json")
    @ResponseBody
    public ResponseEntity<?> obtenerDetallesAsignacionJson(@PathVariable Long id, HttpServletRequest httpReq) {
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

                // Responsable actual del activo
                map.put("responsable",
                    activo != null && activo.getResponsable() != null
                        && activo.getResponsable().getPersona() != null
                        ? activo.getResponsable().getPersona().getNombreCompleto()
                        : "—");

                // Grupo contable
                map.put("grupoContable",
                    activo != null && activo.getGrupoContable() != null
                        ? activo.getGrupoContable().getNombre()
                        : "—");

                // Costo: el snapshot es lo que se registró en el acta; el del activo es
                // el de hoy. Se mandan los dos para poder avisar cuando divergen (el
                // activo se editó después de asignarlo).
                BigDecimal costoSnap = d.getCostoActivoSnapshot();
                BigDecimal costoHoy  = (activo != null && activo.getCosto() != null)
                    ? BigDecimal.valueOf(activo.getCosto()) : null;
                map.put("costo",       costoSnap != null ? costoSnap : costoHoy);
                map.put("costoActual", costoHoy);

                // Estado de SINCRONIZACIÓN (no confundir con estadoActivo, que es la
                // condición física del bien: BUENO / MALO / …).
                String estadoReg = activo != null && activo.getEstado() != null
                    ? activo.getEstado().toUpperCase() : "—";
                map.put("estadoRegistro", estadoReg);

                // Id cifrado para poder abrir la corrección desde esta pantalla.
                map.put("idEnc", cifrarId(activo));
                // Cancelados y bajas no se corrigen desde acá.
                map.put("editable", "ACTIVO".equals(estadoReg) || "PENDIENTE".equals(estadoReg));

                return map;
            }).toList();

            // Totales del acta, para las tarjetas de resumen del modal.
            Map<String, Object> resumen = new LinkedHashMap<>();
            resumen.put("total", listaDetalles.size());
            resumen.put("subidos", listaDetalles.stream()
                .filter(m -> "ACTIVO".equals(m.get("estadoRegistro"))).count());
            resumen.put("pendientes", listaDetalles.stream()
                .filter(m -> "PENDIENTE".equals(m.get("estadoRegistro"))).count());
            resumen.put("sinCosto", listaDetalles.stream()
                .filter(m -> m.get("costo") == null).count());
            resumen.put("costoTotal", listaDetalles.stream()
                .map(m -> (BigDecimal) m.get("costo"))
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP));

            // El botón de corregir se dibuja solo para quien puede usarlo. La barrera
            // real está en el endpoint que aplica el cambio; esto es solo la interfaz.
            Usuario usuario = (Usuario) httpReq.getSession().getAttribute("usuario");
            boolean puedeEditar = usuario != null && usuario.getRol() != null
                && usuario.getRol().getNombre() != null
                && ROLES_EDICION.contains(usuario.getRol().getNombre().trim().toUpperCase());

            return ResponseEntity.ok(Map.of(
                "ok", true,
                "puedeEditar", puedeEditar,
                "detalles", listaDetalles,
                "resumen", resumen));

        } catch (Exception e) {
            log.error("[ASIGNACION-DETALLE] Error cargando detalles id={}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("ok", false, "msg", "Error al cargar detalles: " + e.getMessage()));
        }
    }
}
