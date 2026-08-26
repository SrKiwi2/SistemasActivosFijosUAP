package com.usic.SistemasActivosFijosUAP.controller.auxiliar;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
import org.springframework.dao.DataIntegrityViolationException;
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
import com.usic.SistemasActivosFijosUAP.model.service.AuxiliarRegistroService;
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
    /** Alta de auxiliares — compartida con el registro rápido de los módulos de activos. */
    private final AuxiliarRegistroService auxiliarRegistroService;
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

        // El alta la resuelve AuxiliarRegistroService, el mismo que usan el registro rápido
        // desde Registro de Activos y desde Activos Pendientes: ámbito (predio + grupo),
        // unicidad del nombre dentro de ese ámbito, correlativo calculado en el servidor y
        // envío al VSIAF por la cola. Acá es un ABM, así que un nombre repetido se rechaza
        // en vez de reutilizar el existente.
        AuxiliarRegistroService.Resultado alta;
        try {
            alta = auxiliarRegistroService.registrar(
                    auxiliar.getPredio() != null ? auxiliar.getPredio().getIdPredio() : null,
                    auxiliar.getGrupoContable() != null ? auxiliar.getGrupoContable().getIdGrupoContable() : null,
                    auxiliar.getNombre(),
                    auxiliar.getCodAux(),
                    usuario,
                    false);
        } catch (IllegalArgumentException datoInvalido) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "msg", datoInvalido.getMessage()));
        } catch (DataIntegrityViolationException dup) {
            log.error("No se pudo asignar un codAux libre para el auxiliar '{}': {}",
                    auxiliar.getNombre(), dup.getMessage());
            return ResponseEntity.status(409).body(Map.of(
                "ok", false,
                "msg", "No se pudo asignar el correlativo del auxiliar (otro registro lo tomó al mismo tiempo). "
                     + "Volvé a intentarlo."));
        }

        Auxiliar guardado = alta.auxiliar();

        if (!alta.enviadoAlVsiaf()) {
            // No se revierte la BD ni se responde error: el auxiliar YA quedó guardado y, si
            // se respondiera "falló", el usuario volvería a cargarlo y crearía un segundo
            // auxiliar con otro correlativo. Se avisa y se deja el reenvío como acción aparte.
            return ResponseEntity.ok(Map.of(
                "ok", true,
                "vsiaf", "ERROR",
                "id", guardado.getIdAuxiliar(),
                "codAux", guardado.getCodAux(),
                "msg", "Se realizó el registro en la base con el código " + guardado.getCodAux()
                     + ", pero NO se pudo enviar al VSIAF: " + alta.motivoFalloVsiaf()
                     + ". No lo cargues de nuevo: usá el botón Reenviar al VSIAF de la tabla."));
        }

        return ResponseEntity.ok(Map.of(
            "ok", true,
            "vsiaf", "OK",
            "msg", "Se realizó el registro correctamente con el código " + guardado.getCodAux(),
            "id", guardado.getIdAuxiliar(),
            "codAux", guardado.getCodAux()
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
        
        Usuario usuario =  (Usuario) request.getSession().getAttribute("usuario");
        String usuarioNombre = (usuario != null ? usuario.getUsuario() : "SISTEMA");

        // Obtener el auxiliar original
        Long idAuxiliar = auxiliarForm.getIdAuxiliar();
        Auxiliar auxiliarOriginal = auxiliarService.findById(idAuxiliar);
        if (auxiliarOriginal == null) {
            return ResponseEntity.badRequest().body(Map.of(
                "ok", false,
                "msg", "No se encontró el auxiliar con ID: " + auxiliarForm.getIdAuxiliar()
            ));
        }

        if (auxiliarForm.getPredio() == null || auxiliarForm.getPredio().getIdPredio() == null
                || auxiliarForm.getGrupoContable() == null || auxiliarForm.getGrupoContable().getIdGrupoContable() == null) {
            return ResponseEntity.badRequest().body(Map.of(
                "ok", false, "msg", "Debe elegir predio y grupo contable."));
        }

        GrupoContable grupoContableCompleto = grupoContableService.findById(auxiliarForm.getGrupoContable().getIdGrupoContable());
        Predio predioCompleto = predioServicio.findById(auxiliarForm.getPredio().getIdPredio());
        if (predioCompleto == null || grupoContableCompleto == null) {
            return ResponseEntity.badRequest().body(Map.of(
                "ok", false, "msg", "El predio o el grupo contable elegido no existe."));
        }
        if (predioCompleto.getEntidad() == null
                || predioCompleto.getEntidad().getEntidadCodigo() == null
                || predioCompleto.getUnidad() == null || predioCompleto.getUnidad().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                "ok", false,
                "msg", "El predio '" + predioCompleto.getDescrip() + "' no tiene entidad/unidad configurada."));
        }

        String nombre = AuxiliarRegistroService.normalizarNombre(auxiliarForm.getNombre());
        if (nombre.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "msg", "El nombre del auxiliar es obligatorio."));
        }
        if (!auxiliarService.isNombreUnique(nombre, predioCompleto.getIdPredio(),
                grupoContableCompleto.getIdGrupoContable(), idAuxiliar)) {
            return ResponseEntity.badRequest().body(Map.of(
                "ok", false,
                "msg", "Ya existe otro auxiliar llamado '" + nombre + "' en el predio "
                     + predioCompleto.getUnidad() + " para el grupo contable "
                     + grupoContableCompleto.getCodContable() + "."));
        }

        // Guardar valores originales: son la CLAVE con la que se ubica la fila en el DBF
        // (ENTIDAD + UNIDAD + CODCONT + CODAUX). Si cambian, el UPDATE va contra la vieja.
        Short codContOriginal = auxiliarOriginal.getGrupoContable() != null
                && auxiliarOriginal.getGrupoContable().getCodContable() != null
                ? auxiliarOriginal.getGrupoContable().getCodContable().shortValue() : null;
        Short codAuxOriginal = auxiliarOriginal.getCodAux();
        String entidadOriginal = auxiliarOriginal.getPredio().getEntidad().getEntidadCodigo();
        String unidadOriginal = auxiliarOriginal.getPredio().getUnidad();

        // El codAux es relativo al predio + grupo: si alguno de los dos cambia, el número
        // viejo puede estar ocupado en el destino. Se revalida y, si hace falta, se renumera.
        boolean cambioAmbito =
                !predioCompleto.getIdPredio().equals(auxiliarOriginal.getPredio().getIdPredio())
             || !grupoContableCompleto.getIdGrupoContable().equals(auxiliarOriginal.getGrupoContable().getIdGrupoContable());

        Short codAuxDeseado = (auxiliarForm.getCodAux() != null) ? auxiliarForm.getCodAux() : codAuxOriginal;
        Short codAuxFinal = codAuxDeseado;
        if (cambioAmbito || !codAuxDeseado.equals(codAuxOriginal)) {
            codAuxFinal = auxiliarRegistroService.codAuxLibre(
                    predioCompleto.getIdPredio(), grupoContableCompleto.getIdGrupoContable(),
                    codAuxDeseado, idAuxiliar);
            if (!codAuxFinal.equals(codAuxDeseado)) {
                log.info("Auxiliar {}: codAux {} ocupado en el destino; se renumera a {}",
                        idAuxiliar, codAuxDeseado, codAuxFinal);
            }
        }

        // Actualizar campos
        auxiliarOriginal.setGrupoContable(grupoContableCompleto);
        auxiliarOriginal.setPredio(predioCompleto);
        auxiliarOriginal.setCodAux(codAuxFinal);
        auxiliarOriginal.setNombre(nombre);
        auxiliarOriginal.setFechaUlt(LocalDate.now());
        auxiliarOriginal.setModificacion(new Date());
        auxiliarOriginal.setUsuario(usuarioNombre);
        if (usuario != null) {
            auxiliarOriginal.setModificacionIdUsuario(usuario.getIdUsuario());
        }
        auxiliarOriginal.setEstado("ACTIVO");

        // 1) Guardar en PostgreSQL
        try {
            auxiliarService.save(auxiliarOriginal);
        } catch (DataIntegrityViolationException dup) {
            log.error("Choque de correlativo modificando auxiliar {}: {}", idAuxiliar, dup.getMessage());
            return ResponseEntity.status(409).body(Map.of(
                "ok", false,
                "msg", "Ese código auxiliar ya está ocupado en el predio/grupo elegido. Volvé a intentarlo."));
        }

        // 2) Actualizar en auxiliar.DBF (misma vía que el resto: cola → worker VFPOLEDB)
        try {
            auxiliarDbfWriterService.actualizarDesdeAuxiliar(
                codContOriginal,
                codAuxOriginal,
                entidadOriginal,
                unidadOriginal,
                auxiliarOriginal,
                predioCompleto.getEntidad().getEntidadCodigo(),
                predioCompleto.getUnidad(),
                usuarioNombre
            );

            log.info("Auxiliar {} actualizado en PostgreSQL y encolado al VSIAF", auxiliarOriginal.getIdAuxiliar());

        } catch (Exception e) {
            // La BD ya cambió; devolver error haría que se reintente y se duplique el envío.
            // Se avisa que los dos sistemas quedaron distintos.
            log.error("Auxiliar {} modificado en BD pero NO enviado al VSIAF: {}",
                    auxiliarOriginal.getIdAuxiliar(), e.getMessage(), e);
            return ResponseEntity.ok(Map.of(
                "ok", true,
                "vsiaf", "ERROR",
                "msg", "Se modificó en la base, pero NO se pudo actualizar el VSIAF: " + e.getMessage()
                     + ". Los dos sistemas quedaron distintos: revisá la cola o usá Conciliación."));
        }

        return ResponseEntity.ok(Map.of(
            "ok", true,
            "vsiaf", "OK",
            "codAux", auxiliarOriginal.getCodAux(),
            "msg", "Se modificó correctamente en PostgreSQL y se envió al VSIAF"
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

    /**
     * Vuelve a mandar un auxiliar al VSIAF.
     * <p>
     * Para los que quedaron sólo en la base: si el envío original falló (cola caída, montaje
     * no disponible) el auxiliar existe acá pero no allá, y los activos que lo usan se ven
     * en el VSIAF sin auxiliar. La orden que se encola es un INSERT idempotente —el worker
     * inserta sólo si no existe la clave (ENTIDAD, UNIDAD, CODCONT, CODAUX)—, así que
     * reenviar de más no duplica nada.
     */
    @ValidarUsuarioAutenticado
    @PostMapping("/reenviar-vsiaf/{id_auxiliar}")
    @ResponseBody
    public ResponseEntity<?> reenviarAlVsiaf(HttpServletRequest request,
                                             @PathVariable("id_auxiliar") String idAuxiliarEnc) {
        try {
            Long id = Long.parseLong(Encriptar.decrypt(idAuxiliarEnc));
            Auxiliar auxiliar = auxiliarService.findById(id);
            if (auxiliar == null) {
                return ResponseEntity.badRequest().body(Map.of("ok", false, "msg", "No se encontró el auxiliar."));
            }

            Usuario usuario = (Usuario) request.getSession().getAttribute("usuario");
            String usuarioNombre = (usuario != null ? usuario.getUsuario() : "SISTEMA");

            auxiliarDbfWriterService.asegurarEnVsiaf(auxiliar, usuarioNombre);

            log.info("Auxiliar {} (codAux={}) reenviado al VSIAF por {}",
                    auxiliar.getIdAuxiliar(), auxiliar.getCodAux(), usuarioNombre);
            return ResponseEntity.ok(Map.of(
                "ok", true,
                "msg", "Auxiliar '" + auxiliar.getNombre() + "' (código " + auxiliar.getCodAux()
                     + ") enviado al VSIAF. Si ya estaba, el worker lo deja como está."));

        } catch (Exception e) {
            log.error("Error reenviando auxiliar al VSIAF: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                "ok", false, "msg", "No se pudo enviar al VSIAF: " + e.getMessage()));
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

            for (var f : filas) {
                // ✅ Validar campos obligatorios
                if (f.getEntidadCodigo() == null || f.getUnidad() == null || 
                    f.getCodCont() == null || f.getCodAux() == null) {
                    continue;
                }

                // 3️⃣ Detectar duplicados en el DBF
                String keyDbf = f.getEntidadCodigo() + "|" + f.getUnidad() + "|" + 
                               f.getCodCont() + "|" + f.getCodAux();
                if (!seen.add(keyDbf)) {
                    repetidos++;
                    continue;
                }

                // 4️⃣ Resolver entidad
                Entidad entidad = resolverEntidad(gestionPreferida, f.getEntidadCodigo());
                if (entidad == null) {
                    sinEntidad++;
                    continue;
                }

                // 5️⃣ Resolver predio
                Predio predio = predioServicio
                    .findByEntidadAndUnidadIgnoreCase(entidad, normUnidad(f.getUnidad()))
                    .orElse(null);
                if (predio == null) {
                    sinPredio++;
                    continue;
                }

                // 6️⃣ Resolver grupo contable
                GrupoContable grupo = grupoContableService
                    .findByCodContable(f.getCodCont().intValue())
                    .orElse(null);
                if (grupo == null) {
                    sinGrupo++;
                    continue;
                }

                // 7️⃣ Crear clave única para búsqueda en caché
                String clave = predio.getIdPredio() + "|" + 
                              grupo.getIdGrupoContable() + "|" + 
                              f.getCodAux();
                
                Auxiliar aux = auxiliaresExistentes.get(clave);
                
                // 8️⃣ Determinar si es nuevo
                boolean esNuevo = (aux == null);
                
                if (esNuevo) {
                    aux = new Auxiliar();
                    aux.setPredio(predio);
                    aux.setGrupoContable(grupo);
                    aux.setCodAux(f.getCodAux());
                }

                // 9️⃣ Mapear datos del DBF
                String nombre = (f.getNomAux() != null && !f.getNomAux().isBlank())
                    ? f.getNomAux().trim()
                    : ("AUX " + f.getCodAux());
                if (nombre.length() > 255) {
                    nombre = nombre.substring(0, 255);
                }

                String observ = f.getObserv();
                if (observ != null && "(memo)".equalsIgnoreCase(observ.trim())) {
                    observ = null;
                }

                aux.setNombre(nombre);
                aux.setObserv(observ);
                aux.setFechaUlt(f.getFechaUlt());
                aux.setUsuario(f.getUsuario() == null 
                    ? null 
                    : (f.getUsuario().length() > 60 
                        ? f.getUsuario().substring(0, 60) 
                        : f.getUsuario()));
                aux.setEstado("ACTIVO");

                // 🔟 OPTIMIZACIÓN: Calcular hash y comparar
                String nuevoHash = aux.calcularHash();
                
                if (!esNuevo && !forzarCompleto) {
                    // Verificar si realmente cambió
                    if (nuevoHash.equals(aux.getHashDatos())) {
                        skipped++;
                        continue; // ⭐ NO procesar si no hay cambios
                    }
                }

                // 1️⃣1️⃣ Actualizar metadatos
                aux.setHashDatos(nuevoHash);
                aux.setFechaUltimaSync(LocalDateTime.now());

                batch.add(aux);
                if (esNuevo) inserted++; else updated++;

                // 1️⃣2️⃣ Guardar en lotes
                if (batch.size() >= 500) {
                    auxiliarService.saveAll(batch);
                    batch.clear();
                }
            }

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

    /**
     * ¿El nombre está libre en ESE predio + grupo contable?
     * <p>
     * El ámbito no es opcional: cada predio arma su propia lista de auxiliares y el mismo
     * nombre convive en varios predios con distinto codAux. Si no llegan predio y grupo se
     * responde {@code true} para no bloquear un alta legítima.
     */
    @ValidarUsuarioAutenticado
    @GetMapping("/validar-nombre-unico")
    @ResponseBody
    public boolean validarNombreUnico(
        @RequestParam("nombre") String nombre,
        @RequestParam(value = "idPredio", required = false) Long idPredio,
        @RequestParam(value = "idGrupoContable", required = false) Long idGrupoContable,
        @RequestParam(value = "idAuxiliar", required = false) Long idAuxiliar) {
        return auxiliarService.isNombreUnique(
                AuxiliarRegistroService.normalizarNombre(nombre), idPredio, idGrupoContable, idAuxiliar);
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