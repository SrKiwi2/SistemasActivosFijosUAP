package com.usic.SistemasActivosFijosUAP.controller.oficina;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.usic.SistemasActivosFijosUAP.anotacion.ValidarUsuarioAutenticado;
import com.usic.SistemasActivosFijosUAP.config.Encriptar;
import com.usic.SistemasActivosFijosUAP.model.IService.IOficinaService;
import com.usic.SistemasActivosFijosUAP.model.IService.IPredioServicio;
import com.usic.SistemasActivosFijosUAP.model.entity.Oficina;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;
import com.usic.SistemasActivosFijosUAP.model.service.OficinaExcelService;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/administracion/oficina")
@RequiredArgsConstructor
public class OficinaController {
    
    private final IOficinaService oficinaService;
    private final OficinaExcelService oficinaExcelService;
    private final IPredioServicio predioServicio;

    @ValidarUsuarioAutenticado
    @GetMapping("/vista")
    public String inicio_oficina() {
        return "oficina/vista";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/tabla-registros")
    public String tablaRegistros_oficina(Model model) throws Exception {
        List<Oficina> listasOficinas = oficinaService.listarOficinas();
        List<String> encryptedIds = new ArrayList<>();
        for (Oficina oficinas : listasOficinas) {
            String id_encryptado = Encriptar.encrypt(Long.toString(oficinas.getIdOficina()));
            encryptedIds.add(id_encryptado);
        }
        model.addAttribute("listasOficinas", listasOficinas);
        model.addAttribute("id_encryptado", encryptedIds);
        return "oficina/tabla_registro";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/formulario")
    public String formulario_oficina(Model model, Oficina oficina) {
        model.addAttribute("predios", predioServicio.findAll());
        return "oficina/formulario";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/formulario-edit/{id_oficina}")
    public String formularioEdit_oficina(Model model, @PathVariable("id_oficina") String idOficina) throws Exception{
        Long id = Long.parseLong(Encriptar.decrypt(idOficina));
        model.addAttribute("oficina", oficinaService.findById(id));
        model.addAttribute("edit", "true");
        return "oficina/formulario";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/registrar-oficina")
    public ResponseEntity<String> Registrar_oficina(HttpServletRequest request, @Validated Oficina oficina) {
        if (oficinaService.buscarPorNombre(oficina.getNombre()) == null) {
            oficina.setEstado("ACTIVO");
            oficinaService.save(oficina);
            return ResponseEntity.ok("Se realizó el registro correctamente");
        } else {
            return ResponseEntity.ok("Ya existe un rol con este nombre");
        }
    }

    @PostMapping(value = "/modificar-oficina")
    public ResponseEntity<String> modificar_oficina(HttpServletRequest request, Oficina oficina,
            RedirectAttributes redirectAttrs) {
        Usuario usuario = (Usuario) request.getSession().getAttribute("usuario");
        oficina.setModificacionIdUsuario(usuario.getIdUsuario());
        oficina.setEstado("ACTIVO");
        oficinaService.save(oficina);
        return ResponseEntity.ok("Se realizó el registro correctamente");
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/eliminar/{id_oficina}")
    public ResponseEntity<String> eliminar(Model model, @PathVariable("id_oficina") String idOficina) throws Exception {
        Long id = Long.parseLong(Encriptar.decrypt(idOficina));
        Oficina oficina = oficinaService.findById(id);
        oficina.setEstado("ELIMINADO");
        oficinaService.save(oficina);
        return ResponseEntity.ok("Registro Eliminado");
    }

    @PostMapping("/importar")
    public ResponseEntity<Map<String, String>> importarOficinas(@RequestParam("archivo") MultipartFile archivo) {
        Map<String, String> respuesta = new HashMap<>();
        try {
            System.out.println("Archivo recibido: " + archivo.getOriginalFilename());

            oficinaExcelService.cargarDesdeExcel(archivo);
            respuesta.put("message", "Archivo importado exitosamente");
            return ResponseEntity.ok(respuesta);
        } catch (Exception e) {
            respuesta.put("message", "Error al importar: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(respuesta);
        }
    }
}
