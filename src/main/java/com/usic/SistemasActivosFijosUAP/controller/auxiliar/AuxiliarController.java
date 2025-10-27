package com.usic.SistemasActivosFijosUAP.controller.auxiliar;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
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
import com.usic.SistemasActivosFijosUAP.model.IService.IAuxiliarService;
import com.usic.SistemasActivosFijosUAP.model.IService.IEntidadService;
import com.usic.SistemasActivosFijosUAP.model.IService.IGrupoContableService;
import com.usic.SistemasActivosFijosUAP.model.IService.IPredioServicio;
import com.usic.SistemasActivosFijosUAP.model.entity.Auxiliar;
import com.usic.SistemasActivosFijosUAP.model.entity.Entidad;
import com.usic.SistemasActivosFijosUAP.model.entity.GrupoContable;
import com.usic.SistemasActivosFijosUAP.model.entity.Predio;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/administracion/auxiliar")
@RequiredArgsConstructor
public class AuxiliarController {

    private final IAuxiliarService auxiliarService;
    private final IPredioServicio predioServicio;
    private final IEntidadService entidadService;
    private final IGrupoContableService grupoContableService;
    private final JavaDbfService dbfService;


    @ValidarUsuarioAutenticado
    @GetMapping("/vista")
    public String inicio_auxiliar() {
        return "auxiliar/vista";
    }

    // LISTA: BD -> si vacío, DBF (solo lectura)
    @ValidarUsuarioAutenticado
    @PostMapping("/tabla-registros")
    public String tablaRegistros_auxiliar(Model model,
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "gestion", required = false) Short gestionPreferida) throws Exception {

        List<Auxiliar> lista = auxiliarService.buscarPorQ(q);
        boolean fromDb = (lista != null && !lista.isEmpty());

        if (!fromDb) {
            // Fallback DBF
            var filas = dbfService.listarAuxiliarAll(q);
            lista = new ArrayList<>(filas.size());
            for (var f : filas) {
                // Resolver Entidad (varias variantes)
                String cod = f.getEntidadCodigo();
                String codNoZeros = stripLeftZeros(cod);
                String codPad4 = leftPad4(cod);

                Entidad ent = (gestionPreferida != null)
                        ? entidadService.findByGestionAndEntidadCodigo(gestionPreferida, cod)
                                .or(() -> entidadService.findByGestionAndEntidadCodigo(gestionPreferida, codNoZeros))
                                .or(() -> entidadService.findByGestionAndEntidadCodigo(gestionPreferida, codPad4))
                                .orElse(null)
                        : entidadService.findTopByEntidadCodigoOrderByGestionDesc(cod)
                                .or(() -> entidadService.findTopByEntidadCodigoOrderByGestionDesc(codNoZeros))
                                .or(() -> entidadService.findTopByEntidadCodigoOrderByGestionDesc(codPad4))
                                .orElse(null);

                // Predio “ligero” (solo para mostrar); si no hay entidad, igual mostramos
                // unidad/codigos
                Predio p = new Predio();
                p.setEntidad(ent);
                p.setUnidad(normUnidad(f.getUnidad()));

                GrupoContable g = new GrupoContable();
                g.setCodContable(f.getCodCont() == null ? null : f.getCodCont().intValue());

                Auxiliar a = new Auxiliar();
                a.setIdAuxiliar(null);
                a.setPredio(p);
                a.setGrupoContable(g);
                a.setCodAux(f.getCodAux());
                a.setNombre((f.getNomAux() != null && !f.getNomAux().isBlank()) ? limit(f.getNomAux().trim(), 255)
                        : ("AUX " + f.getCodAux()));
                a.setObserv(f.getObserv());
                a.setFechaUlt(f.getFechaUlt());
                a.setUsuario(limit(f.getUsuario(), 60));
                a.setEstado("ACTIVO");

                lista.add(a);
            }
        }

        var encryptedIds = new ArrayList<String>();
        for (Auxiliar a : lista)
            encryptedIds.add(a.getIdAuxiliar() == null ? "" : Encriptar.encrypt(String.valueOf(a.getIdAuxiliar())));

        model.addAttribute("listasAuxiliares", lista);
        model.addAttribute("id_encryptado", encryptedIds);
        model.addAttribute("sourceUsed", fromDb ? "db" : "dbf");
        return "auxiliar/tabla_registro";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/formulario")
    public String formulario_auxiliar(Model model, Auxiliar auxiliar) {
        model.addAttribute("predios", predioServicio.listarPredios());
        model.addAttribute("gruposContables", grupoContableService.listarGruposContables());
        return "auxiliar/formulario";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/formulario-edit/{id_auxiliar}")
    public String formularioEdit_auxiliar(Model model, @PathVariable("id_auxiliar") String idAuxiliar)
            throws Exception {
        Long id = Long.parseLong(Encriptar.decrypt(idAuxiliar));
        model.addAttribute("auxiliar", auxiliarService.findById(id));
        model.addAttribute("predios", predioServicio.listarPredios());
        model.addAttribute("gruposContables", grupoContableService.listarGruposContables());
        model.addAttribute("edit", "true");
        return "auxiliar/formulario";
    }

    @PostMapping(value = "/modificar-auxiliar")
    public ResponseEntity<String> modificar_auxiliar(HttpServletRequest request, Auxiliar auxiliar,
            RedirectAttributes redirectAttrs) {
        Usuario usuario = (Usuario) request.getSession().getAttribute("usuario");
        auxiliar.setModificacionIdUsuario(usuario.getIdUsuario());
        auxiliar.setEstado("ACTIVO");
        auxiliarService.save(auxiliar);
        return ResponseEntity.ok("Se realizó el registro correctamente");
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/eliminar/{id_auxiliar}")
    public ResponseEntity<String> eliminar(Model model, @PathVariable("id_auxiliar") String idAuxiliar)
            throws Exception {
        Long id = Long.parseLong(Encriptar.decrypt(idAuxiliar));
        Auxiliar auxiliar = auxiliarService.findById(id);
        auxiliar.setEstado("ELIMINADO");
        auxiliarService.save(auxiliar);
        return ResponseEntity.ok("Registro Eliminado");
    }

    String key(long predioId, long grupoId, short codAux) {
        return predioId + "|" + grupoId + "|" + codAux;
    }

    // SYNC: AUXILIAR.DBF -> upsert por (Predio, GrupoContable, codAux)
    @ValidarUsuarioAutenticado
    @PostMapping("/sync-from-mounted")
    @ResponseBody
    public ResponseEntity<?> syncFromMounted(
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "gestion", required = false) Short gestionPreferida) {
        try {
            var filas = dbfService.listarAuxiliarAll(q);
            int inserted = 0, updated = 0, sinEntidad = 0, sinPredio = 0, sinGrupo = 0, repetidos = 0;
            Map<String, Auxiliar> cache = new HashMap<>(filas.size());
            List<Auxiliar> batch = new ArrayList<>(500);

            for (var f : filas) {
                if (f.getEntidadCodigo() == null || f.getUnidad() == null || f.getCodCont() == null
                        || f.getCodAux() == null) {
                    continue;
                }

                // Resolver ENTIDAD (0148/148)
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
                if (entidad == null) {
                    sinEntidad++;
                    continue;
                }

                var predio = predioServicio
                        .findByEntidadAndUnidadIgnoreCase(entidad, normUnidad(f.getUnidad()))
                        .orElse(null);
                if (predio == null) {
                    sinPredio++;
                    continue;
                }

                var grupo = grupoContableService
                        .findByCodContable(f.getCodCont().intValue())
                        .orElse(null);
                if (grupo == null) {
                    sinGrupo++;
                    continue;
                }

                String k = predio.getIdPredio() + "|" + grupo.getIdGrupoContable() + "|" + f.getCodAux();
                if (cache.containsKey(k)) { // el DBF trajo repetidos
                    repetidos++;
                    continue;
                }

                // Busca por IDs (evita líos de proxies)
                Auxiliar aux = auxiliarService
                        .findByPredio_IdPredioAndGrupoContable_IdGrupoContableAndCodAux(predio.getIdPredio(), grupo.getIdGrupoContable(),
                                f.getCodAux())
                        .orElse(null);

                boolean nuevo = (aux == null);
                if (aux == null) {
                    aux = new Auxiliar();
                    aux.setPredio(predio);
                    aux.setGrupoContable(grupo);
                    aux.setCodAux(f.getCodAux());
                }

                // Campos de negocio
                String nombre = (f.getNomAux() != null && !f.getNomAux().isBlank())
                        ? f.getNomAux().trim()
                        : ("AUX " + f.getCodAux());
                if (nombre.length() > 255)
                    nombre = nombre.substring(0, 255);

                String observ = f.getObserv();
                if (observ != null && "(memo)".equalsIgnoreCase(observ.trim()))
                    observ = null;

                aux.setNombre(nombre);
                aux.setObserv(observ);
                aux.setFechaUlt(f.getFechaUlt());
                aux.setUsuario(f.getUsuario() == null ? null
                        : (f.getUsuario().length() > 60 ? f.getUsuario().substring(0, 60) : f.getUsuario()));
                aux.setEstado("ACTIVO");

                cache.put(k, aux);
                batch.add(aux);
                if (nuevo)
                    inserted++;
                else
                    updated++;

                if (batch.size() == 500) {
                    auxiliarService.saveAll(batch);
                    batch.clear();
                }
            }
            if (!batch.isEmpty())
                auxiliarService.saveAll(batch);

            return ResponseEntity.ok(Map.of(
                    "ok", true,
                    "totalLeidas", filas.size(),
                    "insertados", inserted,
                    "actualizados", updated,
                    "sinEntidad", sinEntidad,
                    "sinPredio", sinPredio,
                    "sinGrupoContable", sinGrupo,
                    "duplicadosEnDbf", repetidos // 👈 útil para diagnosticar
            ));
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "ok", false,
                    "message", "Error sincronizando AUXILIAR: " + ex.getMessage()));
        }
    }

    /* HELPERS */
    
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
        } catch (Exception e) {
            return s;
        }
    }

    private String normUnidad(String u) {
        return u == null ? null : u.trim();
    }

    private String limit(String s, int n) {
        return (s != null && s.length() > n) ? s.substring(0, n) : s;
    }
}
