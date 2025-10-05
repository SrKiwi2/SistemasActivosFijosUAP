package com.usic.SistemasActivosFijosUAP.controller.grupo_contable;

import java.io.File;
import java.security.Principal;
import java.time.LocalDate;

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
import com.usic.SistemasActivosFijosUAP.interoperabilidad.JavaDbfService;
import com.usic.SistemasActivosFijosUAP.model.IService.IGrupoContableService;
import com.usic.SistemasActivosFijosUAP.model.entity.GrupoContable;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/administracion/grupoc")
@RequiredArgsConstructor
public class GrupoContableController {

    private final IGrupoContableService grupoContableService;
    private final JavaDbfService dbfService;
    
    @ValidarUsuarioAutenticado
    @GetMapping("/vista")
    public String inicioGrupoContable() {
        return "grupoContable/vista";
    }

    // @ValidarUsuarioAutenticado
    // @PostMapping("/tabla-registros")
    // public String tablaRegistros(Model model) throws Exception {
    //     List<GrupoContable> listasGrupoContable = grupoContableService.listarGruposContables();
    //     List<String> encryptedIds = new ArrayList<>();
    //     for (GrupoContable grupoContables : listasGrupoContable) {
    //         String id_encryptado = Encriptar.encrypt(Long.toString(grupoContables.getIdGrupoContable()));
    //         encryptedIds.add(id_encryptado);
    //     }
    //     model.addAttribute("listasGrupoContable", listasGrupoContable);
    //     model.addAttribute("id_encryptado", encryptedIds);
    //     return "grupoContable/tabla_registro";
    // }

    @ValidarUsuarioAutenticado
    @PostMapping("/tabla-registros")
    public String tablaRegistros(@RequestParam(name="source", required=false) String source,
                                @RequestParam(name="q", required=false) String q,
                                Model model) throws Exception {

        if ("dbf".equalsIgnoreCase(source)) {
        // Leer desde CODCONT.DBF
        var listasGrupoContable = dbfService.listarCodcont(500, q); // limita 500 por UI
        var encryptedIds = new java.util.ArrayList<String>();
        for (var g : listasGrupoContable) {
            String idEnc = Encriptar.encrypt(String.valueOf(g.getIdGrupoContable()));
            encryptedIds.add(idEnc);
        }
        model.addAttribute("listasGrupoContable", listasGrupoContable);
        model.addAttribute("id_encryptado", encryptedIds);
        return "grupoContable/tabla_registro";
        }

        // Comportamiento original (Postgres)
        var listasGrupoContable = grupoContableService.listarGruposContables();
        var encryptedIds = new java.util.ArrayList<String>();
        for (var g : listasGrupoContable) {
        String idEnc = Encriptar.encrypt(Long.toString(g.getIdGrupoContable()));
        encryptedIds.add(idEnc);
        }
        model.addAttribute("listasGrupoContable", listasGrupoContable);
        model.addAttribute("id_encryptado", encryptedIds);
        return "grupoContable/tabla_registro";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/formulario")
    public String formularioGrupoContable(Model model, GrupoContable grupoContable) {
        return "grupoContable/formulario";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/formulario-edit/{id_grupo_contable}")
    public String formularioEditGrupoContable(Model model, @PathVariable("id_grupo_contable") String idGrupoContable) throws Exception{
        Long id = Long.parseLong(Encriptar.decrypt(idGrupoContable));
        model.addAttribute("grupoContable", grupoContableService.findById(id));
        model.addAttribute("edit", "true");
        return "grupoContable/formulario";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/registrar-grupoc")
    public ResponseEntity<String> registrarGrupoContable(HttpServletRequest request, @Validated GrupoContable grupoContable) {
        if (grupoContableService.buscarPorNombre(grupoContable.getNombre()) == null) {
            grupoContable.setEstado("ACTIVO");
            grupoContableService.save(grupoContable);
            return ResponseEntity.ok("Se realizó el registro correctamente");
        } else {
            return ResponseEntity.ok("Ya existe un rol con este nombre");
        }
    }

    /* para ahcer registros al archivo dbf de windows */
    @ValidarUsuarioAutenticado
    @PostMapping("/registrar-grupoc")
    public ResponseEntity<String> registrarGrupoContable(GrupoContable grupoContable,
                                        RedirectAttributes ra,
                                        Principal principal) {
        try {
        // 1) Datos del formulario
        String nombre = grupoContable.getNombre();
        String codigoStr = String.valueOf(grupoContable.getCodContable()).trim();

        if (nombre == null || nombre.isBlank() || codigoStr.isBlank()) {
            ra.addFlashAttribute("error", "Nombre y Código son obligatorios.");
            return ResponseEntity.ok("Ha ocurrido un error en el registro");
        }

        short codcont = Short.parseShort(codigoStr);

        // 2) Defaults (ajusta a tus reglas)
        short vidautil   = 5;              // <-- cámbialo si tienes otra regla
        String observ    = null;           // o "", como prefieras
        boolean depreciar  = true;
        boolean actualizar = true;
        LocalDate feult  = LocalDate.now();
        String usuar     = (principal != null ? principal.getName() : "WEB");

        // 3) Insertar en DBF
        dbfService.insertCodcont(codcont, nombre, vidautil, observ, depreciar, actualizar, feult, usuar);

        ra.addFlashAttribute("ok", "Registrado en CODCONT.DBF: " + codcont + " - " + nombre);
        } catch (NumberFormatException nfe) {
        ra.addFlashAttribute("error", "El código debe ser numérico (SmallInt).");
        } catch (Exception e) {
        ra.addFlashAttribute("error", "Error insertando en CODCONT.DBF: " + e.getMessage());
        }
        return ResponseEntity.ok("Se realizó el registro correctamente");
    }

    @PostMapping(value = "/modificar-grupoc")
    public ResponseEntity<String> modificarGrupoContable(HttpServletRequest request, GrupoContable grupoContable,
            RedirectAttributes redirectAttrs) {
        Usuario usuario = (Usuario) request.getSession().getAttribute("usuario");
        grupoContable.setModificacionIdUsuario(usuario.getIdUsuario());
        grupoContable.setEstado("ACTIVO");
        grupoContableService.save(grupoContable);
        return ResponseEntity.ok("Se realizó el registro correctamente");
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/eliminar/{id_grupo_contable}")
    public ResponseEntity<String> eliminar(Model model, @PathVariable("id_grupo_contable") String idGrupoContable) throws Exception {
        Long id = Long.parseLong(Encriptar.decrypt(idGrupoContable));
        GrupoContable grupoContable = grupoContableService.findById(id);
        grupoContable.setEstado("ELIMINADO");
        grupoContableService.save(grupoContable);
        return ResponseEntity.ok("Registro Eliminado");
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/importar-dbf")
    public ResponseEntity<String> importarDesdeDBF(@RequestParam("archivo") MultipartFile archivo) {
        if (archivo.isEmpty()) {
            return ResponseEntity.badRequest().body("Archivo no proporcionado.");
        }

        try {
            // Convertir MultipartFile a File temporal
            File tempFile = File.createTempFile("grupo_contable_", ".dbf");
            archivo.transferTo(tempFile);

            grupoContableService.importarDesdeDBF(tempFile);
            return ResponseEntity.ok("Importación completada con éxito.");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error durante la importación: " + e.getMessage());
        }
    }
}