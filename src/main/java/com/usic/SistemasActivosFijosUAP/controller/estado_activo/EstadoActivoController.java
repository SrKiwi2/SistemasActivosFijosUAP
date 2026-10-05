package com.usic.SistemasActivosFijosUAP.controller.estado_activo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.usic.SistemasActivosFijosUAP.anotacion.ValidarUsuarioAutenticado;
import com.usic.SistemasActivosFijosUAP.config.Encriptar;
import com.usic.SistemasActivosFijosUAP.model.IService.IEstadoActivoService;
import com.usic.SistemasActivosFijosUAP.model.entity.EstadoActivo;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

/**
 * Catálogo de estados del activo (bueno, regular, malo…). Guardar y eliminar responden
 * JSON { ok, msg }, como espera la plantilla de pantallas (sciaf-modulo.js).
 */
@Controller
@RequestMapping("/administracion/estadoa")
@RequiredArgsConstructor
public class EstadoActivoController {

    private final IEstadoActivoService estadoActivoService;

    /** La pantalla llega con la tabla ya armada: un solo pedido al abrir. */
    @ValidarUsuarioAutenticado
    @GetMapping("/vista")
    public String inicioEstadoActivo(Model model) throws Exception {
        cargarTabla(model);
        return "estadoActivo/vista";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/tabla-registros")
    public String tablaRegistros(Model model) throws Exception {
        cargarTabla(model);
        return "estadoActivo/tabla_registro";
    }

    private void cargarTabla(Model model) throws Exception {
        List<EstadoActivo> listasEstadoActivos = estadoActivoService.listarEstadoActivo();
        List<String> encryptedIds = new ArrayList<>();
        for (EstadoActivo estadoActivo : listasEstadoActivos) {
            encryptedIds.add(Encriptar.encrypt(Long.toString(estadoActivo.getIdEstadoActivo())));
        }
        model.addAttribute("listasEstadoActivos", listasEstadoActivos);
        model.addAttribute("id_encryptado", encryptedIds);
        // Cuántos activos usan cada estado (una consulta agrupada): se muestra y se avisa al quitar.
        model.addAttribute("activosPorEstado", estadoActivoService.activosPorEstado());
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/formulario")
    public String formularioEstadoActivo(Model model, EstadoActivo estadoActivo) {
        return "estadoActivo/formulario";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/formulario-edit/{id_estado_activo}")
    public String formularioEditEstadoActivo(Model model, @PathVariable("id_estado_activo") String idEstadoActivo) throws Exception {
        Long id = Long.parseLong(Encriptar.decrypt(idEstadoActivo));
        model.addAttribute("estadoActivo", estadoActivoService.findById(id));
        model.addAttribute("edit", "true");
        return "estadoActivo/formulario";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/registrar-estadoa")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> registrarEstadoActivo(HttpServletRequest request, EstadoActivo estadoActivo) {
        estadoActivo.setIdEstadoActivo(null);   // un alta nunca pisa otro registro
        String error = normalizarYValidar(estadoActivo, null);
        if (error != null) return ResponseEntity.ok(Map.of("ok", false, "msg", error));

        estadoActivo.setEstado("ACTIVO");
        Usuario usuario = (Usuario) request.getSession().getAttribute("usuario");
        if (usuario != null) estadoActivo.setRegistroIdUsuario(usuario.getIdUsuario());
        estadoActivoService.save(estadoActivo);
        return ResponseEntity.ok(Map.of("ok", true, "msg", "Estado registrado correctamente"));
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/modificar-estadoa")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> modificarEstadoActivo(HttpServletRequest request, EstadoActivo estadoActivo) {
        // Se modifica el registro guardado, no el que llega del formulario (que borraba la auditoría).
        EstadoActivo actual = estadoActivo.getIdEstadoActivo() == null ? null
                : estadoActivoService.findById(estadoActivo.getIdEstadoActivo());
        if (actual == null || !"ACTIVO".equals(actual.getEstado())) {
            return ResponseEntity.ok(Map.of("ok", false, "msg", "El estado ya no existe o fue eliminado."));
        }
        String error = normalizarYValidar(estadoActivo, actual.getIdEstadoActivo());
        if (error != null) return ResponseEntity.ok(Map.of("ok", false, "msg", error));

        actual.setNombre(estadoActivo.getNombre());
        actual.setCodigo(estadoActivo.getCodigo());
        Usuario usuario = (Usuario) request.getSession().getAttribute("usuario");
        if (usuario != null) actual.setModificacionIdUsuario(usuario.getIdUsuario());
        estadoActivoService.save(actual);
        return ResponseEntity.ok(Map.of("ok", true, "msg", "Estado modificado correctamente"));
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/eliminar/{id_estado_activo}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> eliminar(HttpServletRequest request,
            @PathVariable("id_estado_activo") String idEstadoActivo) throws Exception {
        Long id = Long.parseLong(Encriptar.decrypt(idEstadoActivo));
        EstadoActivo estadoActivo = estadoActivoService.findById(id);
        if (estadoActivo == null) return ResponseEntity.ok(Map.of("ok", false, "msg", "El estado no existe."));
        estadoActivo.setEstado("ELIMINADO");
        Usuario usuario = (Usuario) request.getSession().getAttribute("usuario");
        if (usuario != null) estadoActivo.setModificacionIdUsuario(usuario.getIdUsuario());
        estadoActivoService.save(estadoActivo);
        return ResponseEntity.ok(Map.of("ok", true, "msg", "Estado eliminado"));
    }

    /** MAYÚSCULAS y sin espacios sobrantes; devuelve el error o null. Código único sin distinguir mayúsculas. */
    private String normalizarYValidar(EstadoActivo e, Long idPropio) {
        e.setNombre(limpiar(e.getNombre()));
        e.setCodigo(limpiar(e.getCodigo()));
        if (e.getNombre() == null) return "Ingrese el nombre del estado.";
        if (e.getCodigo() == null) return "Ingrese el código.";
        for (EstadoActivo otro : estadoActivoService.listarPorCodigo(e.getCodigo())) {
            if (!otro.getIdEstadoActivo().equals(idPropio)) {
                return "Ya existe un estado con el código " + e.getCodigo() + " (" + otro.getNombre() + ").";
            }
        }
        return null;
    }

    private static String limpiar(String s) {
        if (s == null) return null;
        String t = s.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
        return t.isEmpty() ? null : t;
    }
}
