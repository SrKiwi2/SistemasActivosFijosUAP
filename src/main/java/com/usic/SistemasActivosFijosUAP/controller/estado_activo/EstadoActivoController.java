package com.usic.SistemasActivosFijosUAP.controller.estado_activo;

import java.util.ArrayList;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.usic.SistemasActivosFijosUAP.anotacion.ValidarUsuarioAutenticado;
import com.usic.SistemasActivosFijosUAP.config.Encriptar;
import com.usic.SistemasActivosFijosUAP.model.IService.IEstadoActivoService;
import com.usic.SistemasActivosFijosUAP.model.entity.EstadoActivo;
import com.usic.SistemasActivosFijosUAP.model.entity.GrupoContable;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/administracion/estadoa")
@RequiredArgsConstructor
public class EstadoActivoController {
    
    private final IEstadoActivoService estadoActivoService;

    @ValidarUsuarioAutenticado
    @GetMapping("/vista")
    public String inicioEstadoActivo() {
        return "estadoActivo/vista";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/tabla-registros")
    public String tablaRegistros(Model model) throws Exception {
        List<EstadoActivo> listasEstadoActivos = estadoActivoService.listarEstadoActivo();
        List<String> encryptedIds = new ArrayList<>();
        for (EstadoActivo estadoActivo : listasEstadoActivos) {
            String id_encryptado = Encriptar.encrypt(Long.toString(estadoActivo.getIdEstadoActivo()));
            encryptedIds.add(id_encryptado);
        }
        model.addAttribute("listasEstadoActivos", listasEstadoActivos);
        model.addAttribute("id_encryptado", encryptedIds);
        return "estadoActivo/tabla_registro";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/formulario")
    public String formularioGrupoContable(Model model, EstadoActivo estadoActivo) {
        return "estadoActivo/formulario";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/formulario-edit/{id_estado_activo}")
    public String formularioEditEstadoActivo(Model model, @PathVariable("id_estado_activo") String idEstadoActivo) throws Exception{
        Long id = Long.parseLong(Encriptar.decrypt(idEstadoActivo));
        model.addAttribute("estadoActivo", estadoActivoService.findById(id));
        model.addAttribute("edit", "true");
        return "estadoActivo/formulario";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/registrar-estadoa")
    public ResponseEntity<String> registrarEstadoActivo(HttpServletRequest request, @Validated EstadoActivo estadoActivo) {
        if (estadoActivoService.buscarPorCodigo(estadoActivo.getCodigo()) == null) {
            estadoActivo.setEstado("ACTIVO");
            estadoActivoService.save(estadoActivo);
            return ResponseEntity.ok("Se realizó el registro correctamente");
        } else {
            return ResponseEntity.ok("Ya existe un estado activo con este codigo");
        }
    }

    @PostMapping(value = "/modificar-estadoa")
    public ResponseEntity<String> modificarGrupoContable(HttpServletRequest request, EstadoActivo estadoActivo,
            RedirectAttributes redirectAttrs) {
        Usuario usuario = (Usuario) request.getSession().getAttribute("usuario");
        estadoActivo.setModificacionIdUsuario(usuario.getIdUsuario());
        estadoActivo.setEstado("ACTIVO");
        estadoActivoService.save(estadoActivo);
        return ResponseEntity.ok("Se realizó el registro correctamente");
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/eliminar/{id_estado_activo}")
    public ResponseEntity<String> eliminar(Model model, @PathVariable("id_estado_activo") String idEstadoActivo) throws Exception {
        Long id = Long.parseLong(Encriptar.decrypt(idEstadoActivo));
        EstadoActivo estadoActivo = estadoActivoService.findById(id);
        estadoActivo.setEstado("ELIMINADO");
        estadoActivoService.save(estadoActivo);
        return ResponseEntity.ok("Registro Eliminado");
    }
}
