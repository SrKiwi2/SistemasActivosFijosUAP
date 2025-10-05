package com.usic.SistemasActivosFijosUAP.controller.grupo_contable;

import java.io.File;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.usic.SistemasActivosFijosUAP.anotacion.ValidarUsuarioAutenticado;
import com.usic.SistemasActivosFijosUAP.config.Encriptar;
import com.usic.SistemasActivosFijosUAP.interoperabilidad.JavaDbfService;
import com.usic.SistemasActivosFijosUAP.model.IService.IGrupoContableService;
import com.usic.SistemasActivosFijosUAP.model.dto.interoperabilidad.GrupoContableDbf;
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
    // List<GrupoContable> listasGrupoContable =
    // grupoContableService.listarGruposContables();
    // List<String> encryptedIds = new ArrayList<>();
    // for (GrupoContable grupoContables : listasGrupoContable) {
    // String id_encryptado =
    // Encriptar.encrypt(Long.toString(grupoContables.getIdGrupoContable()));
    // encryptedIds.add(id_encryptado);
    // }
    // model.addAttribute("listasGrupoContable", listasGrupoContable);
    // model.addAttribute("id_encryptado", encryptedIds);
    // return "grupoContable/tabla_registro";
    // }

    @ValidarUsuarioAutenticado
    @PostMapping("/tabla-registros")
    public String tablaRegistros(
            @RequestParam(name = "q", required = false) String q,
            Model model) throws Exception {

        // 1) Intentar desde BD
        List<GrupoContable> bd = (q == null || q.isBlank())
                ? grupoContableService.listarGruposContables() // todos
                : grupoContableService.buscarPorNombreLike("%" + q.trim() + "%"); // implementa este finder

        boolean fromDb = (bd != null && !bd.isEmpty());

        if (fromDb) {
            // Mapea a la forma que espera tu fragmento
            var listasGrupoContable = bd.stream().map(gc -> GrupoContableDbf.builder()
                    .codContable(gc.getCodContable() == null ? null : gc.getCodContable().longValue())
                    .nombre(gc.getNombre())
                    .vidaUtil(gc.getVidaUtil())
                    .depreciar(gc.getDepreciar())
                    .actualizar(gc.getActualizar())
                    .idGrupoContable(gc.getIdGrupoContable()) // usa id real de BD aquí
                    .build()).toList();

            var encryptedIds = new ArrayList<String>();
            for (var g : listasGrupoContable) {
                encryptedIds.add(Encriptar.encrypt(String.valueOf(g.getIdGrupoContable())));
            }

            model.addAttribute("listasGrupoContable", listasGrupoContable);
            model.addAttribute("id_encryptado", encryptedIds);
            model.addAttribute("sourceUsed", "db"); // para debug/etiqueta opcional
            return "grupoContable/tabla_registro";
        }

        // 2) Fallback: DBF montado
        var listasGrupoContable = dbfService.listarCodcontAll(q);
        var encryptedIds = new ArrayList<String>();
        for (var g : listasGrupoContable) {
            encryptedIds.add(Encriptar.encrypt(String.valueOf(g.getIdGrupoContable())));
        }

        model.addAttribute("listasGrupoContable", listasGrupoContable);
        model.addAttribute("id_encryptado", encryptedIds);
        model.addAttribute("sourceUsed", "dbf");
        return "grupoContable/tabla_registro";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/formulario")
    public String formularioGrupoContable(Model model, GrupoContable grupoContable) {
        return "grupoContable/formulario";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/formulario-edit/{id_grupo_contable}")
    public String formularioEditGrupoContable(Model model, @PathVariable("id_grupo_contable") String idGrupoContable)
            throws Exception {
        Long id = Long.parseLong(Encriptar.decrypt(idGrupoContable));
        model.addAttribute("grupoContable", grupoContableService.findById(id));
        model.addAttribute("edit", "true");
        return "grupoContable/formulario";
    }

    // @ValidarUsuarioAutenticado
    // @PostMapping("/registrar-grupoc")
    // public ResponseEntity<String> registrarGrupoContable(HttpServletRequest
    // request, @Validated GrupoContable grupoContable) {
    // if (grupoContableService.buscarPorNombre(grupoContable.getNombre()) == null)
    // {
    // grupoContable.setEstado("ACTIVO");
    // grupoContableService.save(grupoContable);
    // return ResponseEntity.ok("Se realizó el registro correctamente");
    // } else {
    // return ResponseEntity.ok("Ya existe un rol con este nombre");
    // }
    // }

    /* para ahcer registros al archivo dbf de windows */
    @ValidarUsuarioAutenticado
    @PostMapping("/registrar-grupoc")
    public ResponseEntity<String> registrarGrupoContableBDF(GrupoContable grupoContable,
            RedirectAttributes ra, HttpServletRequest request) {
        try {
            Usuario usuario = (Usuario) request.getSession().getAttribute("usuario");
            // 1) Datos del formulario
            String nombre = grupoContable.getNombre();
            String codigoStr = String.valueOf(grupoContable.getCodContable()).trim();

            if (nombre == null || nombre.isBlank() || codigoStr.isBlank()) {
                ra.addFlashAttribute("error", "Nombre y Código son obligatorios.");
                return ResponseEntity.ok("Ha ocurrido un error en el registro");
            }

            short codcont = Short.parseShort(codigoStr);

            // 2) Defaults (ajusta a tus reglas)
            short vidautil = 5; // <-- cámbialo si tienes otra regla
            String observ = ""; // o "", como prefieras
            boolean depreciar = true;
            boolean actualizar = true;
            LocalDate feult = LocalDate.now();
            String usuar = (usuario.getUsuario());

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
    public ResponseEntity<String> eliminar(Model model, @PathVariable("id_grupo_contable") String idGrupoContable)
            throws Exception {
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

    // En GrupoContableController
    @ValidarUsuarioAutenticado
    @PostMapping("/sync-from-mounted")
    @ResponseBody
    public ResponseEntity<?> syncFromMounted(@RequestParam(name = "q", required = false) String q) {
        try {
            // 1) Leer todo el CODCONT.DBF desde la carpeta montada
            var registros = dbfService.listarCodcontAll(q);

            // 2) Upsert en BD (lote)
            int inserted = 0, updated = 0;
            List<GrupoContable> batch = new ArrayList<>(500);

            for (var d : registros) {
                Integer cod = d.getCodContable() != null ? d.getCodContable().intValue() : null;

                // Busca por clave natural (cod_contable). Crea si no existe.
                var existente = grupoContableService.findByCodContable(cod).orElse(null);
                boolean nuevo = (existente == null);

                var g = nuevo ? new GrupoContable() : existente;
                g.setCodContable(cod);
                g.setNombre(d.getNombre());
                g.setVidaUtil(d.getVidaUtil());
                g.setDepreciar(Boolean.TRUE.equals(d.getDepreciar()));
                g.setActualizar(Boolean.TRUE.equals(d.getActualizar()));
                // g.setEstado("ACTIVO"); // si tu entidad lo tiene

                batch.add(g);
                if (nuevo)
                    inserted++;
                else
                    updated++;

                if (batch.size() == 500) {
                    grupoContableService.saveAll(batch);
                    batch.clear();
                }
            }
            if (!batch.isEmpty())
                grupoContableService.saveAll(batch);

            return ResponseEntity.ok(Map.of(
                    "ok", true,
                    "totalLeidas", registros.size(),
                    "insertados", inserted,
                    "actualizados", updated));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "ok", false,
                    "message", "Error sincronizando desde DBF montado: " + e.getMessage()));
        }
    }

}