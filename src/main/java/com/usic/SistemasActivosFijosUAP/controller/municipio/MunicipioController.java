package com.usic.SistemasActivosFijosUAP.controller.municipio;

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
import com.usic.SistemasActivosFijosUAP.model.IService.IMunicipioService;
import com.usic.SistemasActivosFijosUAP.model.entity.Municipio;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/administracion/municipio")
@RequiredArgsConstructor
public class MunicipioController {
    private final IMunicipioService municipioService;

    @ValidarUsuarioAutenticado
    @GetMapping("/vista")
    public String inicioMunicipio() {
        return "municipio/vista";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/tabla-registros")
    public String tablaRegistrosMunicipio(Model model) throws Exception {
        List<Municipio> listasMunicipios = municipioService.listarMunicipios();
        List<String> encryptedIds = new ArrayList<>();
        for (Municipio municipios : listasMunicipios) {
            String id_encryptado = Encriptar.encrypt(Long.toString(municipios.getIdMunicipio()));
            encryptedIds.add(id_encryptado);
        }
        model.addAttribute("listasMunicipios", listasMunicipios);
        model.addAttribute("id_encryptado", encryptedIds);
        return "municipio/tabla_registro";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/formulario")
    public String formularioMunicipio(Model model, Municipio municipio) {
        return "municipio/formulario";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/formulario-edit/{id_municipio}")
    public String formularioEditMunicipio(Model model, @PathVariable("id_municipio") String idMunicipio) throws Exception{
        Long id = Long.parseLong(Encriptar.decrypt(idMunicipio));
        model.addAttribute("municipio", municipioService.findById(id));
        model.addAttribute("edit", "true");
        return "municipio/formulario";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/registrar-municipio")
    public ResponseEntity<String> RegistrarMunicipio(HttpServletRequest request, @Validated Municipio municipio) {
        if (municipioService.buscarPorNombre(municipio.getNombre()) == null) {
            municipio.setModificacionIdUsuario(request.getSession().getAttribute(null) != null
                    ? ((Usuario) request.getSession().getAttribute("usuario")).getIdUsuario()
                    : null);
            municipio.setEstado("ACTIVO");
            municipioService.save(municipio);
            return ResponseEntity.ok("Se realizó el registro correctamente");
        } else {
            return ResponseEntity.ok("Ya existe un rol con este nombre");
        }
    }

    @PostMapping(value = "/modificar-municipio")
    public ResponseEntity<String> modificarEntidad(HttpServletRequest request, Municipio municipio,
            RedirectAttributes redirectAttrs) {
        Usuario usuario = (Usuario) request.getSession().getAttribute("usuario");
        municipio.setModificacionIdUsuario(usuario.getIdUsuario());
        municipio.setEstado("ACTIVO");
        municipioService.save(municipio);
        return ResponseEntity.ok("Se realizó el registro correctamente");
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/eliminar/{id_municipio}")
    public ResponseEntity<String> eliminar(Model model, @PathVariable("id_municipio") String idMunicipio) throws Exception {
        Long id = Long.parseLong(Encriptar.decrypt(idMunicipio));
        Municipio municipio = municipioService.findById(id);
        municipio.setEstado("ELIMINADO");
        municipioService.save(municipio);
        return ResponseEntity.ok("Registro Eliminado");
    }
}
