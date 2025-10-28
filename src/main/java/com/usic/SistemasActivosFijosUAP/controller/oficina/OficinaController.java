package com.usic.SistemasActivosFijosUAP.controller.oficina;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.usic.SistemasActivosFijosUAP.anotacion.ValidarUsuarioAutenticado;
import com.usic.SistemasActivosFijosUAP.config.Encriptar;
import com.usic.SistemasActivosFijosUAP.interoperabilidad.JavaDbfService;
import com.usic.SistemasActivosFijosUAP.interoperabilidad.registroDbf.OficinaDbfWriterService;
import com.usic.SistemasActivosFijosUAP.model.IService.IEntidadService;
import com.usic.SistemasActivosFijosUAP.model.IService.IOficinaService;
import com.usic.SistemasActivosFijosUAP.model.IService.IPredioServicio;
import com.usic.SistemasActivosFijosUAP.model.entity.Entidad;
import com.usic.SistemasActivosFijosUAP.model.entity.Oficina;
import com.usic.SistemasActivosFijosUAP.model.entity.Predio;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/administracion/oficina")
@RequiredArgsConstructor
public class OficinaController {

    private final IOficinaService oficinaService;
    private final IPredioServicio predioServicio;
    private final IEntidadService entidadService;
    private final JavaDbfService dbfService;
    private final OficinaDbfWriterService oficinaDbfWriterService;

    private static final Logger log = LoggerFactory.getLogger(OficinaController.class);

    @ValidarUsuarioAutenticado
    @GetMapping("/vista")
    public String inicio_oficina() {
        return "oficina/vista";
    }

    // LISTAR: intenta BD; si vacío, lee DBF y arma objetos "transient" para la tabla
    @ValidarUsuarioAutenticado
    @PostMapping("/tabla-registros")
    public String tablaRegistros_oficina(Model model,
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "gestion", required = false) Short gestionPreferida) throws Exception {

        List<Oficina> listasOficinas = oficinaService.buscarPorQ(q);
        boolean fromDb = listasOficinas != null && !listasOficinas.isEmpty();

        if (!fromDb) {
            var filas = dbfService.listarOficinaAll(q);
            listasOficinas = new ArrayList<>(filas.size());

            for (var f : filas) {
                // Resolver ENTIDAD para mostrarla en la tabla (si no está, igual mostramos el código)
                String cod = f.getEntidadCodigo();
                String codNoZeros = stripLeftZeros(cod);
                String codPad4 = leftPad4(cod);

                Entidad ent = null;
                if (gestionPreferida != null) {
                    ent = entidadService.findByGestionAndEntidadCodigo(gestionPreferida, cod)
                            .or(() -> entidadService.findByGestionAndEntidadCodigo(gestionPreferida, codNoZeros))
                            .or(() -> entidadService.findByGestionAndEntidadCodigo(gestionPreferida, codPad4))
                            .orElse(null);
                } else {
                    ent = entidadService.findTopByEntidadCodigoOrderByGestionDesc(cod)
                            .or(() -> entidadService.findTopByEntidadCodigoOrderByGestionDesc(codNoZeros))
                            .or(() -> entidadService.findTopByEntidadCodigoOrderByGestionDesc(codPad4))
                            .orElse(null);
                }

                // Construimos una Oficina "de vista" SIN persistir:
                Oficina o = new Oficina();
                o.setIdOficina(null);

                // armamos Predio "ligero" sólo para la tabla
                Predio p = new Predio();
                p.setEntidad(ent != null ? ent : null);
                p.setUnidad(normUnidad(f.getUnidad()));
                o.setPredio(p);

                o.setCodOfi(f.getCodOfi());
                o.setNombre((f.getNomOfic() != null && !f.getNomOfic().isBlank())
                        ? f.getNomOfic().trim()
                        : ("OFICINA " + f.getCodOfi()));
                o.setObserv(f.getObserv());
                o.setFechaUlt(f.getFeult());
                o.setUsuario(f.getUsuario());
                o.setApiEstado(f.getApiEstado());
                o.setEstado("ACTIVO");

                listasOficinas.add(o);
            }
        }

        List<String> encryptedIds = new ArrayList<>();
        for (Oficina o : listasOficinas) {
            encryptedIds.add(o.getIdOficina() == null ? "" : Encriptar.encrypt(Long.toString(o.getIdOficina())));
        }

        model.addAttribute("listasOficinas", listasOficinas);
        model.addAttribute("id_encryptado", encryptedIds);
        model.addAttribute("sourceUsed", fromDb ? "db" : "dbf");
        return "oficina/tabla_registro";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/formulario")
    public String formulario_oficina(Model model, Oficina oficina) {
        model.addAttribute("oficina", new Oficina());
        model.addAttribute("predios", predioServicio.findAll());
        return "oficina/formulario";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/formulario-edit/{id_oficina}")
    public String formularioEdit_oficina(Model model, @PathVariable("id_oficina") String idOficina) throws Exception {
        Long id = Long.parseLong(Encriptar.decrypt(idOficina));
        model.addAttribute("oficina", oficinaService.findById(id));
        model.addAttribute("predios", predioServicio.findAll());
        model.addAttribute("edit", "true");
        return "oficina/formulario";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/registrar-oficina")
    @ResponseBody
    public ResponseEntity<?> registrar_oficina(
            HttpServletRequest request,
            @Validated @ModelAttribute Oficina oficina,
            BindingResult br) {

        Usuario usuario = (Usuario) request.getSession().getAttribute("usuario");
        
        if (br.hasErrors()) {
            return ResponseEntity.badRequest().body(Map.of(
                "ok", false,
                "errors", br.getFieldErrors().stream()
                        .map(e -> Map.of("field", e.getField(), "message", e.getDefaultMessage()))
                        .toList()
            ));
        }
        
        String usuarioNombre = usuario.getUsuario();
        
        // Establecer valores por defecto
        oficina.setEstado("ACTIVO");
        oficina.setFechaUlt(LocalDate.now());
        oficina.setUsuario(usuarioNombre);
        oficina.setApiEstado(Short.valueOf("1"));
        if (usuario != null) {
            oficina.setRegistroIdUsuario(usuario.getIdUsuario());
        }

        Entidad entidad = oficina.getPredio().getEntidad();
        Predio preido = oficina.getPredio();
        
        // Obtener ENTIDAD y UNIDAD desde el predio
        String entidadCode = entidad.getEntidadCodigo(); // O desde configuración
        String unidadCode = preido.getUnidad();  // Valor por defecto
        
        if (oficina.getPredio() != null && oficina.getPredio().getCodigo() != null) {
            unidadCode = oficina.getPredio().getCodigo();
        }
        
        // Verificar si ya existe en DBF
        Short codOfic = oficina.getCodOfi();
        
        if (codOfic != null) {
            if (oficinaDbfWriterService.existsByCodOfic(codOfic, entidadCode, unidadCode)) {
                return ResponseEntity.badRequest().body(Map.of(
                    "ok", false,
                    "msg", "Ya existe una oficina con CODOFIC=" + codOfic + " en el DBF"
                ));
            }
        }
        
        // 1) Guardar en PostgreSQL
        oficinaService.save(oficina);
        
        // 2) Insertar en OFICINA.DBF
        try {
            oficinaDbfWriterService.insertarDesdeOficina(oficina, entidadCode, unidadCode, usuarioNombre);
            log.info("Oficina {} registrada en PostgreSQL y DBF", oficina.getIdOficina());
        } catch (Exception e) {
            log.error("Error insertando oficina en DBF: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                "ok", false,
                "msg", "Se guardó en la base de datos pero falló el registro en DBF: " + e.getMessage()
            ));
        }
        
        return ResponseEntity.ok(Map.of(
            "ok", true,
            "msg", "Se realizó el registro correctamente en PostgreSQL y DBF",
            "id", oficina.getIdOficina()
        ));
    }


    @ValidarUsuarioAutenticado
    @PostMapping("/modificar-oficina")
    @ResponseBody
    public ResponseEntity<?> modificar_oficina(
            HttpServletRequest request,
            @Validated @ModelAttribute Oficina oficinaForm,
            BindingResult br) {
        
        if (br.hasErrors()) {
            return ResponseEntity.badRequest().body(Map.of(
                "ok", false,
                "errors", br.getFieldErrors().stream()
                        .map(e -> Map.of("field", e.getField(), "message", e.getDefaultMessage()))
                        .toList()
            ));
        }
        
        Usuario usuario = (Usuario) request.getSession().getAttribute("usuario");
        String usuarioNombre = usuario.getUsuario();
        
        // Obtener la oficina original
        Oficina oficinaOriginal = oficinaService.findById(oficinaForm.getIdOficina());
        if (oficinaOriginal == null) {
            return ResponseEntity.badRequest().body(Map.of(
                "ok", false,
                "msg", "No se encontró la oficina con ID: " + oficinaForm.getIdOficina()
            ));
        }
        Entidad entidad = oficinaOriginal.getPredio().getEntidad();
        Predio preido = oficinaOriginal.getPredio();
        // Guardar valores originales para buscar en DBF
        Short codOficOriginal = oficinaOriginal.getCodOfi();
        String entidadOriginal = entidad.getEntidadCodigo();
        String unidadOriginal = preido.getUnidad();
        
        // Actualizar campos
        oficinaOriginal.setPredio(oficinaForm.getPredio());
        oficinaOriginal.setCodOfi(oficinaForm.getCodOfi());
        oficinaOriginal.setNombre(oficinaForm.getNombre());
        oficinaOriginal.setObserv(oficinaForm.getObserv());
        oficinaOriginal.setFechaUlt(LocalDate.now());
        oficinaOriginal.setUsuario(usuarioNombre);
        if (usuario != null) {
            oficinaOriginal.setModificacionIdUsuario(usuario.getIdUsuario());
        }
        oficinaOriginal.setEstado("ACTIVO");
        
        // 1) Guardar en PostgreSQL
        oficinaService.save(oficinaOriginal);
        
        // 2) Actualizar en OFICINA.DBF
        try {
            String entidadCode = entidad.getEntidadCodigo();
            String unidadCode = preido.getUnidad();
            
            oficinaDbfWriterService.actualizarDesdeOficina(
                codOficOriginal,
                entidadOriginal,
                unidadOriginal,
                oficinaOriginal,
                entidadCode,
                unidadCode,
                usuarioNombre
            );
            
            log.info("Oficina {} actualizada en PostgreSQL y DBF", oficinaOriginal.getIdOficina());
            
        } catch (Exception e) {
            log.error("Error actualizando oficina en DBF: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                "ok", false,
                "msg", "Se guardó en la base de datos pero falló la actualización en DBF: " + e.getMessage()
            ));
        }
        
        return ResponseEntity.ok(Map.of(
            "ok", true,
            "msg", "Se modificó correctamente en PostgreSQL y DBF"
        ));
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

    // SYNC: OFICINA.DBF -> upsert por (Predio, codOfi)
    @ValidarUsuarioAutenticado
    @PostMapping("/sync-from-mounted")
    @ResponseBody
    public ResponseEntity<?> syncFromMounted(@RequestParam(name="q", required=false) String q,
                                             @RequestParam(name="gestion", required=false) Short gestionPreferida) {
        try {
            var filas = dbfService.listarOficinaAll(q);
            int inserted=0, updated=0, sinEntidad=0, sinPredio=0;
            List<Oficina> batch = new ArrayList<>(500);

            for (var f : filas) {
                String cod = f.getEntidadCodigo();
                String codNoZeros = stripLeftZeros(cod);
                String codPad4 = leftPad4(cod);

                Entidad entidad = (gestionPreferida != null)
                        ? entidadService.findByGestionAndEntidadCodigo(gestionPreferida, cod)
                          .or(() -> entidadService.findByGestionAndEntidadCodigo(gestionPreferida, codNoZeros))
                          .or(() -> entidadService.findByGestionAndEntidadCodigo(gestionPreferida, codPad4))
                          .orElse(null)
                        : entidadService.findTopByEntidadCodigoOrderByGestionDesc(cod)
                          .or(() -> entidadService.findTopByEntidadCodigoOrderByGestionDesc(codNoZeros))
                          .or(() -> entidadService.findTopByEntidadCodigoOrderByGestionDesc(codPad4))
                          .orElse(null);

                if (entidad == null) { sinEntidad++; continue; }

                var predio = predioServicio.findByEntidadAndUnidadIgnoreCase(entidad, normUnidad(f.getUnidad())).orElse(null);
                if (predio == null) { sinPredio++; continue; }

                Oficina o = oficinaService.findByPredioAndCodOfi(predio, f.getCodOfi()).orElse(null);
                boolean nuevo = (o == null);
                if (o == null) {
                    o = new Oficina();
                    o.setPredio(predio);
                    o.setCodOfi(f.getCodOfi());
                }

                String nombreFinal = (f.getNomOfic()!=null && !f.getNomOfic().isBlank())
                        ? f.getNomOfic().trim() : ("OFICINA " + f.getCodOfi());
                if (nombreFinal.length() > 255) nombreFinal = nombreFinal.substring(0, 255);

                String observ = f.getObserv();
                if (observ != null && "(memo)".equalsIgnoreCase(observ.trim())) observ = null;

                o.setNombre(nombreFinal);
                o.setObserv(observ);
                o.setFechaUlt(f.getFeult());
                o.setUsuario( f.getUsuario()==null? null : (f.getUsuario().length()>60 ? f.getUsuario().substring(0,60) : f.getUsuario()));
                o.setApiEstado(f.getApiEstado());
                o.setEstado("ACTIVO");

                batch.add(o);
                if (nuevo) inserted++; else updated++;
                if (batch.size()==500) { oficinaService.saveAll(batch); batch.clear(); }
            }
            if (!batch.isEmpty()) oficinaService.saveAll(batch);

            return ResponseEntity.ok(Map.of(
                "ok", true,
                "totalLeidas", filas.size(),
                "insertados", inserted,
                "actualizados", updated,
                "sinEntidad", sinEntidad,
                "sinPredio", sinPredio
            ));
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().body(Map.of(
                "ok", false,
                "message", "Error sincronizando OFICINA: " + ex.getMessage()
            ));
        }
    }

    private String stripLeftZeros(String s) {
        if (s == null)
            return null;
        String out = s.replaceFirst("^0+", "");
        return out.isEmpty() ? "0" : out;
    }

    private String leftPad4(String s) {
        String base = stripLeftZeros(s);
        try {
            return String.format("%04d", Integer.parseInt(base));
        } catch (NumberFormatException e) {
            return s;
        }
    }

    // si tu UNIDAD en BD está normalizada (trim/upper):
    private String normUnidad(String u) {
        return u == null ? null : u.trim();
    }

}
