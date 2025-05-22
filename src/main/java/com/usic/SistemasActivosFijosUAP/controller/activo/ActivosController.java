package com.usic.SistemasActivosFijosUAP.controller.activo;

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
import com.usic.SistemasActivosFijosUAP.model.IService.IActivoService;
import com.usic.SistemasActivosFijosUAP.model.entity.Activo;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;
import com.usic.SistemasActivosFijosUAP.model.service.ActivoExcelService;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/activo")
@RequiredArgsConstructor
public class ActivosController {
    private final IActivoService activoService;
    private final ActivoExcelService activoExcelService; 

    @ValidarUsuarioAutenticado
    @GetMapping("/vista")
    public String inicio_oficina() {
        return "activo/vista";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/tabla-registros")
    public String tablaRegistros_activo(Model model) throws Exception {
        List<Activo> listasOficinas = activoService.listarActivos();
        List<String> encryptedIds = new ArrayList<>();
        for (Activo oficinas : listasOficinas) {
            String id_encryptado = Encriptar.encrypt(Long.toString(oficinas.getIdActivo()));
            encryptedIds.add(id_encryptado);
        }
        model.addAttribute("listasOficinas", listasOficinas);
        model.addAttribute("id_encryptado", encryptedIds);
        return "activo/tabla_registro";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/formulario")
    public String formulario_activo(Model model, Activo activo) {
        return "activo/formulario";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/formulario-edit/{id_activo}")
    public String formularioEdit_activo(Model model, @PathVariable("id_activo") String idActivo) throws Exception{
        Long id = Long.parseLong(Encriptar.decrypt(idActivo));
        model.addAttribute("activo", activoService.findById(id));
        model.addAttribute("edit", "true");
        return "activo/formulario";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/registrar-activo")
    public ResponseEntity<String> registrar_activo(HttpServletRequest request, @Validated Activo activo) {
        if (activoService.buscarPorNombre(activo.getNombre()) == null) {
            activo.setEstado("ACTIVO");
            activoService.save(activo);
            return ResponseEntity.ok("Se realizó el registro correctamente");
        } else {
            return ResponseEntity.ok("Ya existe un rol con este nombre");
        }
    }

    @PostMapping(value = "/modificar-activo")
    public ResponseEntity<String> modificar_oficina(HttpServletRequest request, Activo activo,
            RedirectAttributes redirectAttrs) {
        Usuario usuario = (Usuario) request.getSession().getAttribute("usuario");
        activo.setModificacionIdUsuario(usuario.getIdUsuario());
        activo.setEstado("ACTIVO");
        activoService.save(activo);
        return ResponseEntity.ok("Se realizó el registro correctamente");
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/eliminar/{id_activo}")
    public ResponseEntity<String> eliminar(Model model, @PathVariable("id_activo") String idActivo) throws Exception {
        Long id = Long.parseLong(Encriptar.decrypt(idActivo));
        Activo activo = activoService.findById(id);
        activo.setEstado("ELIMINADO");
        activoService.save(activo);
        return ResponseEntity.ok("Registro Eliminado");
    }

    @PostMapping("/importar")
    public ResponseEntity<Map<String, String>> importarActivos(@RequestParam("archivo") MultipartFile archivo) {
        Map<String, String> respuesta = new HashMap<>();
        try {
            System.out.println("Archivo recibido: " + archivo.getOriginalFilename());

            activoExcelService.cargarActivosDesdeExcel(archivo);
            respuesta.put("message", "Archivo importado exitosamente");
            return ResponseEntity.ok(respuesta);
        } catch (Exception e) {
            respuesta.put("message", "Error al importar: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(respuesta);
        }
    }
}
