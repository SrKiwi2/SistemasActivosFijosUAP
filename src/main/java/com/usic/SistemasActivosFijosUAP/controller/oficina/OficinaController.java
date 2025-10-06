package com.usic.SistemasActivosFijosUAP.controller.oficina;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.usic.SistemasActivosFijosUAP.anotacion.ValidarUsuarioAutenticado;
import com.usic.SistemasActivosFijosUAP.config.Encriptar;
import com.usic.SistemasActivosFijosUAP.interoperabilidad.JavaDbfService;
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

    @ValidarUsuarioAutenticado
    @GetMapping("/vista")
    public String inicio_oficina() {
        return "oficina/vista";
    }

    // LISTAR: intenta BD; si vacío, lee DBF y arma objetos "transient" para la
    // tabla
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
                // Resolver ENTIDAD para mostrarla en la tabla (si no está, igual mostramos el
                // código)
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
        model.addAttribute("predios", predioServicio.findAll());
        return "oficina/formulario";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/formulario-edit/{id_oficina}")
    public String formularioEdit_oficina(Model model, @PathVariable("id_oficina") String idOficina) throws Exception {
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
