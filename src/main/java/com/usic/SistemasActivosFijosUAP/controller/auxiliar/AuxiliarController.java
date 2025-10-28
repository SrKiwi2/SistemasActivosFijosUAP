package com.usic.SistemasActivosFijosUAP.controller.auxiliar;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
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
import com.usic.SistemasActivosFijosUAP.interoperabilidad.registroDbf.AuxiliarDbfWriterService;
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
    private final AuxiliarDbfWriterService auxiliarDbfWriterService;
    private static final Logger log = LoggerFactory.getLogger(AuxiliarController.class);


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

    @ValidarUsuarioAutenticado
    @PostMapping("/registrar-auxiliar")
    public ResponseEntity<?> registrar_auxiliar(
            HttpServletRequest request,
            @Validated @ModelAttribute Auxiliar auxiliar,
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
        String usuarioNombre = (usuario != null ? usuario.getUsuario() : "SISTEMA");
        
        // Establecer valores por defecto
        auxiliar.setEstado("ACTIVO");
        auxiliar.setFechaUlt(LocalDate.now());
        auxiliar.setUsuario(usuarioNombre);
        if (usuario != null) {
            auxiliar.setRegistroIdUsuario(usuario.getIdUsuario());
        }

        Predio predioCompleto = predioServicio.findById(auxiliar.getPredio().getIdPredio());
        auxiliar.setPredio(predioCompleto);
        
        GrupoContable grupoContable = grupoContableService.findById(auxiliar.getGrupoContable().getIdGrupoContable());
        auxiliar.setGrupoContable(grupoContable);

        String entidadCode = predioCompleto.getEntidad().getEntidadCodigo();
        String unidadCode = predioCompleto.getUnidad();
        
        if (auxiliar.getPredio() != null && auxiliar.getPredio().getCodigo() != null) {
            unidadCode = auxiliar.getPredio().getUnidad();
        }
        
        // Verificar si ya existe en DBF
        Short codCont = auxiliar.getGrupoContable() != null ? 
                       Short.valueOf(auxiliar.getGrupoContable().getCodContable().toString()) : null;
        Short codAux = auxiliar.getCodAux();
        
        if (codCont != null && codAux != null) {
            if (auxiliarDbfWriterService.existsByCodContYCodAux(codCont, codAux, entidadCode, unidadCode)) {
                return ResponseEntity.badRequest().body(Map.of(
                    "ok", false,
                    "msg", "Ya existe un auxiliar con CODCONT=" + codCont + " y CODAUX=" + codAux + " en el DBF"
                ));
            }
        }
        
        // 1) Guardar en PostgreSQL
        auxiliarService.save(auxiliar);
        
        // 2) Insertar en auxiliar.DBF
        try {
            auxiliarDbfWriterService.insertarDesdeAuxiliar(auxiliar, entidadCode, unidadCode, usuarioNombre);
            log.info("Auxiliar {} registrado en PostgreSQL y DBF", auxiliar.getIdAuxiliar());
        } catch (Exception e) {
            log.error("Error insertando auxiliar en DBF: {}", e.getMessage(), e);
            // Opcional: podrías hacer rollback del PostgreSQL aquí
            return ResponseEntity.status(500).body(Map.of(
                "ok", false,
                "msg", "Se guardó en la base de datos pero falló el registro en DBF: " + e.getMessage()
            ));
        }
        
        return ResponseEntity.ok(Map.of(
            "ok", true,
            "msg", "Se realizó el registro correctamente",
            "id", auxiliar.getIdAuxiliar()
        ));
    }


    @ValidarUsuarioAutenticado
    @PostMapping("/modificar-auxiliar")
    public ResponseEntity<?> modificar_auxiliar(
            HttpServletRequest request,
            @Validated @ModelAttribute Auxiliar auxiliarForm,
            BindingResult br) {
        
        if (br.hasErrors()) {
            return ResponseEntity.badRequest().body(Map.of(
                "ok", false,
                "errors", br.getFieldErrors().stream()
                        .map(e -> Map.of("field", e.getField(), "message", e.getDefaultMessage()))
                        .toList()
            ));
        }
        
        Usuario usuario =  (Usuario) request.getSession().getAttribute("usuario");;
        String usuarioNombre = usuario.getUsuario();
        
        // Obtener el auxiliar original
        Auxiliar auxiliarOriginal = auxiliarService.findById(auxiliarForm.getIdAuxiliar());
        if (auxiliarOriginal == null) {
            return ResponseEntity.badRequest().body(Map.of(
                "ok", false,
                "msg", "No se encontró el auxiliar con ID: " + auxiliarForm.getIdAuxiliar()
            ));
        }
        
        // Guardar valores originales para buscar en DBF
        Short codContOriginal = auxiliarOriginal.getGrupoContable() != null ? 
                               Short.valueOf(auxiliarOriginal.getGrupoContable().getCodContable().toString()) : null;
        Short codAuxOriginal = auxiliarOriginal.getCodAux();
        String entidadOriginal = auxiliarOriginal.getPredio().getEntidad().getEntidadCodigo();
        String unidadOriginal = auxiliarOriginal.getPredio().getUnidad();
        
        // Actualizar campos
        auxiliarOriginal.setGrupoContable(auxiliarForm.getGrupoContable());
        auxiliarOriginal.setPredio(auxiliarForm.getPredio());
        auxiliarOriginal.setCodAux(auxiliarForm.getCodAux());
        auxiliarOriginal.setNombre(auxiliarForm.getNombre());
        auxiliarOriginal.setFechaUlt(LocalDate.now());
        auxiliarOriginal.setUsuario(usuario.getUsuario());
        if (usuario != null) {
            auxiliarOriginal.setModificacionIdUsuario(usuario.getIdUsuario());
        }
        auxiliarOriginal.setEstado("ACTIVO");
        
        // 1) Guardar en PostgreSQL
        auxiliarService.save(auxiliarOriginal);
        
        // 2) Actualizar en auxiliar.DBF
        try {
            String entidadCode = entidadOriginal;
            String unidadCode = unidadOriginal;
            
            auxiliarDbfWriterService.actualizarDesdeAuxiliar(
                codContOriginal,
                codAuxOriginal,
                entidadOriginal,
                unidadOriginal,
                auxiliarOriginal,
                entidadCode,
                unidadCode,
                usuarioNombre
            );
            
            log.info("Auxiliar {} actualizado en PostgreSQL y DBF", auxiliarOriginal.getIdAuxiliar());
            
        } catch (Exception e) {
            log.error("Error actualizando auxiliar en DBF: {}", e.getMessage(), e);
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
    @PostMapping("/eliminar/{id_auxiliar}")
    public ResponseEntity<String> eliminar(Model model, @PathVariable("id_auxiliar") String idAuxiliar)
            throws Exception {
        Long id = Long.parseLong(Encriptar.decrypt(idAuxiliar));
        Auxiliar auxiliar = auxiliarService.findById(id);
        auxiliar.setEstado("ELIMINADO");
        auxiliarService.save(auxiliar);
        return ResponseEntity.ok("Registro Eliminado");
    }

    /*SINCRONIZADOR DBF - BD*/
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
                if (cache.containsKey(k)) { 
                    repetidos++;
                    continue;
                }

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
                    "duplicadosEnDbf", repetidos
            ));
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "ok", false,
                    "message", "Error sincronizando AUXILIAR: " + ex.getMessage()));
        }
    }

    @ValidarUsuarioAutenticado
    @GetMapping("/obtener-cod-auxiliar")
    @ResponseBody
    public Short obtenerCodAuxiliar(
        @RequestParam("idPredio") Long idPredio,
        @RequestParam("idGrupoContable") Long idGrupoContable) {
        return auxiliarService.getNextCodAux(idPredio, idGrupoContable);
    }

    @ValidarUsuarioAutenticado
    @GetMapping("/validar-nombre-unico")
    @ResponseBody
    public boolean validarNombreUnico(
        @RequestParam("nombre") String nombre,
        @RequestParam(value = "idAuxiliar", required = false) Long idAuxiliar) {
        return auxiliarService.isNombreUnique(nombre, idAuxiliar);
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