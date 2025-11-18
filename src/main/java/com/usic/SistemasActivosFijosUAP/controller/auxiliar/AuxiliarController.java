package com.usic.SistemasActivosFijosUAP.controller.auxiliar;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
import com.usic.SistemasActivosFijosUAP.model.dto.interoperabilidad.SyncResult;
import com.usic.SistemasActivosFijosUAP.model.entity.Auxiliar;
import com.usic.SistemasActivosFijosUAP.model.entity.Entidad;
import com.usic.SistemasActivosFijosUAP.model.entity.GrupoContable;
import com.usic.SistemasActivosFijosUAP.model.entity.Predio;
import com.usic.SistemasActivosFijosUAP.model.entity.SyncControl;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;
import com.usic.SistemasActivosFijosUAP.model.service.SyncControlService;

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
    private final SyncControlService syncControlService;
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

        try {
            SyncControl syncInfo = syncControlService.obtenerInfoSincronizacion("auxiliar");
            
            if (syncInfo != null) {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
                String fechaFormateada = syncInfo.getUltimaSincronizacion().format(formatter);
                
                model.addAttribute("ultimaSincronizacion", fechaFormateada);
                model.addAttribute("estadoSync", syncInfo.getEstado());
                model.addAttribute("registrosProcesados", syncInfo.getRegistrosProcesados());
                model.addAttribute("registrosNuevos", syncInfo.getRegistrosNuevos());
                model.addAttribute("registrosActualizados", syncInfo.getRegistrosActualizados());
                model.addAttribute("duracionUltimaSync", syncInfo.getDuracionMs() / 1000.0);
            } else {
                model.addAttribute("ultimaSincronizacion", "Nunca sincronizado");
                model.addAttribute("estadoSync", "PENDIENTE");
            }
        } catch (Exception e) {
            model.addAttribute("ultimaSincronizacion", "Error al obtener info");
            model.addAttribute("estadoSync", "ERROR");
        }

        List<Auxiliar> lista = auxiliarService.buscarPorQ(q);
        boolean fromDb = (lista != null && !lista.isEmpty());

        if (!fromDb) {
            // Fallback DBF
            var filas = dbfService.listarAuxiliarAll(q);
            lista = new ArrayList<>(filas.size());

            for (var f : filas) {

                Entidad ent = resolverEntidad(gestionPreferida, f.getEntidadCodigo());

                Predio p = new Predio();
                p.setEntidad(ent);
                p.setUnidad(normUnidad(f.getUnidad()));

                GrupoContable g = new GrupoContable();
                g.setCodContable(f.getCodCont() == null ? null : f.getCodCont().intValue());

                Auxiliar a = new Auxiliar();
                a.setIdAuxiliar(null); // NULL = solo lectura
                a.setPredio(p);
                a.setGrupoContable(g);
                a.setCodAux(f.getCodAux());
                a.setNombre((f.getNomAux() != null && !f.getNomAux().isBlank()) 
                    ? limit(f.getNomAux().trim(), 255)
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
        Long idAuxiliar = auxiliarForm.getIdAuxiliar();
        Auxiliar auxiliarOriginal = auxiliarService.findById(idAuxiliar);
        if (auxiliarOriginal == null) {
            return ResponseEntity.badRequest().body(Map.of(
                "ok", false,
                "msg", "No se encontró el auxiliar con ID: " + auxiliarForm.getIdAuxiliar()
            ));
        }

        Long idGrupoContable = auxiliarForm.getGrupoContable().getIdGrupoContable();
        GrupoContable grupoContableCompleto = grupoContableService.findById(idGrupoContable);

        Long idPredio = auxiliarForm.getPredio().getIdPredio();
        Predio predioCompleto = predioServicio.findById(idPredio);
        
        // Guardar valores originales para buscar en DBF
        Short codContOriginal = auxiliarOriginal.getGrupoContable() != null ? 
                               Short.valueOf(auxiliarOriginal.getGrupoContable().getCodContable().toString()) : null;
        Short codAuxOriginal = auxiliarOriginal.getCodAux();
        String entidadOriginal = auxiliarOriginal.getPredio().getEntidad().getEntidadCodigo();
        String unidadOriginal = auxiliarOriginal.getPredio().getUnidad();
        
        // Actualizar campos
        auxiliarOriginal.setGrupoContable(grupoContableCompleto); // <-- ¡CORREGIDO!
        auxiliarOriginal.setPredio(predioCompleto);             // <-- ¡CORREGIDO!
        auxiliarOriginal.setCodAux(auxiliarForm.getCodAux());
        auxiliarOriginal.setNombre(auxiliarForm.getNombre());
        auxiliarOriginal.setFechaUlt(LocalDate.now());
        auxiliarOriginal.setModificacion(new Date());
        auxiliarOriginal.setUsuario(usuario.getUsuario());
        if (usuario != null) {
            auxiliarOriginal.setModificacionIdUsuario(usuario.getIdUsuario());
        }
        auxiliarOriginal.setEstado("ACTIVO");
        
        // 1) Guardar en PostgreSQL
        auxiliarService.save(auxiliarOriginal);
        
        // 2) Actualizar en auxiliar.DBF
        try {
            String entidadCode = predioCompleto.getEntidad().getEntidadCodigo();
            String unidadCode = predioCompleto.getUnidad();
            
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
    @GetMapping("/api/detalle/{idEnc}")
    @ResponseBody
    public ResponseEntity<?> obtenerDetalle(@PathVariable String idEnc) {
        try {
            Long id = Long.parseLong(Encriptar.decrypt(idEnc));
            Auxiliar auxiliar = auxiliarService.findById(id);
            
            if (auxiliar == null) {
                return ResponseEntity.notFound().build();
            }
            
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("idAuxiliar", auxiliar.getIdAuxiliar());
            response.put("codAux", auxiliar.getCodAux());
            response.put("nombre", auxiliar.getNombre());
            response.put("estado", auxiliar.getEstado());
            
            if (auxiliar.getGrupoContable() != null) {
                response.put("grupoContable", Map.of(
                    "idGrupoContable", auxiliar.getGrupoContable().getIdGrupoContable(),
                    "nombre", auxiliar.getGrupoContable().getNombre(),
                    "codContable", auxiliar.getGrupoContable().getCodContable()
                ));
            }
            
            if (auxiliar.getPredio() != null) {
                response.put("predio", Map.of(
                    "idPredio", auxiliar.getPredio().getIdPredio(),
                    "descrip", auxiliar.getPredio().getDescrip(),
                    "codigo", auxiliar.getPredio().getCodigo()
                ));
            }
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error obteniendo detalle: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                "ok", false,
                "message", "Error al obtener detalle: " + e.getMessage()
            ));
        }
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
        @RequestParam(name = "gestion", required = false) Short gestionPreferida,
        @RequestParam(name = "forzarCompleto", defaultValue = "false") boolean forzarCompleto) {
    
        long inicio = System.currentTimeMillis();
        
        try {
            var filas = dbfService.listarAuxiliarAll(q);
            Map<String, Auxiliar> auxiliaresExistentes = cargarAuxiliaresEnCache(gestionPreferida);
            
            int inserted = 0, updated = 0, skipped = 0, sinEntidad = 0, sinPredio = 0, sinGrupo = 0, repetidos = 0, sinOficina = 0;
            List<Auxiliar> batch = new ArrayList<>(500);
            Set<String> seen = new HashSet<>(filas.size());

            // ... (tu lógica de sincronización existente)

            if (!batch.isEmpty()) {
                auxiliarService.saveAll(batch);
                batch.clear();
            }

            // ✅ NUEVO: Construir SyncResult con toda la información
            long duracion = System.currentTimeMillis() - inicio;
            
            SyncResult resultado = SyncResult.builder()
                .totalLeidas(filas.size())
                .insertados(inserted)
                .actualizados(updated)
                .duracionMs(duracion)
                .omitidos(skipped)
                .sinEntidad(sinEntidad)
                .sinPredio(sinPredio)
                .sinGrupoContable(sinGrupo)
                .sinOficina(sinOficina)
                .build();
            
            // ✅ Registrar usando el método sobrecargado
            syncControlService.registrarSincronizacion("auxiliar", resultado);

            // ✅ Respuesta completa para el frontend
            return ResponseEntity.ok(Map.ofEntries(
                Map.entry("ok", true),
                Map.entry("totalLeidas", resultado.getTotalLeidas()),
                Map.entry("insertados", resultado.getInsertados()),
                Map.entry("actualizados", resultado.getActualizados()),
                Map.entry("duracionMs", resultado.getDuracionMs()),
                Map.entry("omitidos", resultado.getOmitidos()),
                Map.entry("sinEntidad", resultado.getSinEntidad()),
                Map.entry("sinPredio", resultado.getSinPredio()),
                Map.entry("sinGrupoContable", resultado.getSinGrupoContable()),
                Map.entry("sinOficina", resultado.getSinOficina()),
                Map.entry("duplicadosEnDbf", resultado.getDuplicadosEnDbf()),
                Map.entry("mensaje", resultado.getMensaje())
            ));
            
        } catch (Exception ex) {
            syncControlService.registrarError("auxiliar", ex.getMessage());
            
            return ResponseEntity.internalServerError().body(Map.of(
                "ok", false,
                "message", "Error sincronizando AUXILIAR: " + ex.getMessage()
            ));
        }
    }

    /**
     * ✅ OPTIMIZACIÓN: Cargar todos los auxiliares en memoria (1 sola consulta)
     */
    private Map<String, Auxiliar> cargarAuxiliaresEnCache(Short gestion) {
        List<Auxiliar> todos;
        
        if (gestion != null) {
            // Filtrar por gestión de la entidad relacionada
            todos = auxiliarService.findAll().stream()
                .filter(a -> a.getPredio() != null && 
                           a.getPredio().getEntidad() != null &&
                           gestion.equals(a.getPredio().getEntidad().getGestion()))
                .collect(Collectors.toList());
        } else {
            todos = auxiliarService.findAll();
        }
        
        // Crear mapa con clave: "predioId|grupoId|codAux"
        return todos.stream()
            .collect(Collectors.toMap(
                a -> a.getPredio().getIdPredio() + "|" + 
                     a.getGrupoContable().getIdGrupoContable() + "|" + 
                     a.getCodAux(),
                a -> a,
                (existing, replacement) -> existing
            ));
    }

    /**
     * ✅ ENDPOINT AJAX para obtener info de sincronización
     */
    @GetMapping("/sync-info")
    @ResponseBody
    public ResponseEntity<?> obtenerInfoSync() {
        try {
            SyncControl syncInfo = syncControlService.obtenerInfoSincronizacion("auxiliar");
            
            if (syncInfo != null) {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
                
                return ResponseEntity.ok(Map.of(
                    "ultimaSincronizacion", syncInfo.getUltimaSincronizacion().format(formatter),
                    "estado", syncInfo.getEstado(),
                    "registrosProcesados", syncInfo.getRegistrosProcesados(),
                    "registrosNuevos", syncInfo.getRegistrosNuevos(),
                    "registrosActualizados", syncInfo.getRegistrosActualizados(),
                    "duracionSegundos", syncInfo.getDuracionMs() / 1000.0
                ));
            }
            
            return ResponseEntity.ok(Map.of(
                "ultimaSincronizacion", "Nunca sincronizado",
                "estado", "PENDIENTE",
                "registrosProcesados", 0,
                "registrosNuevos", 0,
                "registrosActualizados", 0,
                "duracionSegundos", 0.0
            ));
            
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                "ultimaSincronizacion", "Error al obtener info",
                "estado", "ERROR",
                "registrosProcesados", 0,
                "registrosNuevos", 0,
                "registrosActualizados", 0,
                "duracionSegundos", 0.0
            ));
        }
    }

    /* HELPERS */
    
    private Entidad resolverEntidad(Short gestionPreferida, String codigo) {
        String cod = codigo.trim();
        String codNoZeros = stripLeftZeros(codigo);
        String codPad4 = leftPad4(codigo);

        if (gestionPreferida != null) {
            return entidadService.findByGestionAndEntidadCodigo(gestionPreferida, cod)
                    .or(() -> entidadService.findByGestionAndEntidadCodigo(gestionPreferida, codNoZeros))
                    .or(() -> entidadService.findByGestionAndEntidadCodigo(gestionPreferida, codPad4))
                    .orElse(null);
        } else {
            return entidadService.findTopByEntidadCodigoOrderByGestionDesc(cod)
                    .or(() -> entidadService.findTopByEntidadCodigoOrderByGestionDesc(codNoZeros))
                    .or(() -> entidadService.findTopByEntidadCodigoOrderByGestionDesc(codPad4))
                    .orElse(null);
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