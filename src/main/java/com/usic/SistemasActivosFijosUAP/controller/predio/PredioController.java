package com.usic.SistemasActivosFijosUAP.controller.predio;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

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

import com.usic.SistemasActivosFijosUAP.anotacion.ValidarUsuarioAutenticado;
import com.usic.SistemasActivosFijosUAP.config.Encriptar;
import com.usic.SistemasActivosFijosUAP.config.RolesSciaf;
import com.usic.SistemasActivosFijosUAP.interoperabilidad.JavaDbfService;
import com.usic.SistemasActivosFijosUAP.model.IService.IEntidadService;
import com.usic.SistemasActivosFijosUAP.model.IService.IMunicipioService;
import com.usic.SistemasActivosFijosUAP.model.IService.IPredioServicio;
import com.usic.SistemasActivosFijosUAP.model.dao.IOficinaDao;
import com.usic.SistemasActivosFijosUAP.model.dao.IPredioDao;
import com.usic.SistemasActivosFijosUAP.model.entity.Entidad;
import com.usic.SistemasActivosFijosUAP.model.entity.Municipio;
import com.usic.SistemasActivosFijosUAP.model.entity.Predio;
import com.usic.SistemasActivosFijosUAP.model.entity.SyncControl;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;
import com.usic.SistemasActivosFijosUAP.model.service.SyncControlService;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

/**
 * Predios (unidades administrativas). Los trae el VSIAF (UNIDADADMIN.DBF): entidad, unidad,
 * descripción, ciudad y estado se consultan y se sincronizan, no se editan aquí.
 * <p>
 * Lo único propio del SCIAF es su <b>configuración de codificación</b>: el municipio y el
 * código del predio, que forman el prefijo del código de cada activo
 * (municipio-predio-grupo). Sin eso no se puede registrar un activo en sus oficinas. La
 * sincronización no toca esos dos datos.
 */
@Controller
@RequestMapping("/administracion/predio")
@RequiredArgsConstructor
public class PredioController {
    private final IPredioServicio predioServicio;
    private final IPredioDao predioDao;
    private final IOficinaDao oficinaDao;
    private final IEntidadService entidadService;
    private final IMunicipioService municipioService;
    private final JavaDbfService dbfService;
    private final SyncControlService syncControlService;
    /** Sin VSIAF a la vista (laptop de desarrollo, montaje caído): no se lee el DBF. */
    private final com.usic.SistemasActivosFijosUAP.componet.VsiafDisponibilidad vsiaf;

    /** Código del predio: letras y números, sin guiones ni símbolos (va dentro del código del activo). */
    private static final java.util.regex.Pattern CODIGO_VALIDO = java.util.regex.Pattern.compile("^[A-Z0-9]{1,6}$");

    /** La pantalla llega con la tabla ya armada: un solo pedido al abrir. */
    @ValidarUsuarioAutenticado
    @GetMapping("/vista")
    public String inicio_predio(Model model, HttpServletRequest request) throws Exception {
        cargarTabla(model, null);
        model.addAttribute("esAdmin", RolesSciaf.esAdministrativo(request));
        return "predio/vista";
    }

    // LISTA: BD; si está vacía, se muestra el DBF montado (sólo lectura)
    @ValidarUsuarioAutenticado
    @PostMapping("/tabla-registros")
    public String tablaRegistros(Model model, HttpServletRequest request,
            @RequestParam(name = "gestion", required = false) Short gestionPreferida) throws Exception {
        cargarTabla(model, gestionPreferida);
        model.addAttribute("esAdmin", RolesSciaf.esAdministrativo(request));
        return "predio/tabla_registro";
    }

    private void cargarTabla(Model model, Short gestionPreferida) throws Exception {
        try {
            SyncControl syncInfo = syncControlService.obtenerInfoSincronizacion("predio");
            if (syncInfo != null) {
                model.addAttribute("ultimaSincronizacion",
                        syncInfo.getUltimaSincronizacion().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")));
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

        // 1) BD: entidad y municipio en la misma consulta (antes, dos consultas por fila).
        List<Predio> lista = predioDao.todosConRelaciones().stream()
                .filter(p -> !"ELIMINADO".equals(p.getEstado()))
                .sorted(Comparator.comparing((Predio p) -> p.getUnidad() == null ? "" : p.getUnidad()))
                .collect(Collectors.toList());
        boolean fromDb = !lista.isEmpty();

        // 2) Base vacía: directo del VSIAF (solo lectura), si está a la vista.
        if (!fromDb && vsiaf.dbf("lista de predios")) {
            try {
                for (var f : dbfService.listarUnidadAdminAll(null)) {
                    Predio p = new Predio();
                    p.setIdPredio(null);
                    p.setEntidad(resolverEntidad(gestionPreferida, f.getEntidadCodigo()));
                    p.setUnidad(f.getUnidad());
                    p.setDescrip(f.getDescrip());
                    p.setCiudad(f.getCiudad());
                    p.setEstadoUni(f.getEstadoUni());
                    p.setEstado("ACTIVO");
                    lista.add(p);
                }
            } catch (Exception e) {
                lista = new ArrayList<>();
            }
        }

        List<String> encryptedIds = new ArrayList<>(lista.size());
        for (Predio p : lista) {
            encryptedIds.add(p.getIdPredio() == null ? "" : Encriptar.encrypt(Long.toString(p.getIdPredio())));
        }
        Map<Long, Long> oficinas = new HashMap<>();
        for (Object[] f : oficinaDao.contarPorPredio()) {
            oficinas.put(((Number) f[0]).longValue(), ((Number) f[1]).longValue());
        }

        model.addAttribute("listasPredios", lista);
        model.addAttribute("id_encryptado", encryptedIds);
        model.addAttribute("oficinasPorPredio", oficinas);
        model.addAttribute("sourceUsed", fromDb ? "db" : "dbf");
    }

    /* ── Configuración de codificación (municipio + código): propia del SCIAF ── */

    @ValidarUsuarioAutenticado
    @PostMapping("/formulario-config/{idEnc}")
    public String formularioConfig(Model model, @PathVariable("idEnc") String idEnc) throws Exception {
        Predio predio = predioServicio.findById(Long.parseLong(Encriptar.decrypt(idEnc)));
        if (predio == null) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND, "El predio ya no existe.");
        }
        model.addAttribute("predio", predio);
        model.addAttribute("idEnc", idEnc);
        model.addAttribute("municipios", municipioService.listarMunicipios().stream()
                .sorted(Comparator.comparing((Municipio m) -> m.getNombre() == null ? "" : m.getNombre()))
                .collect(Collectors.toList()));
        model.addAttribute("activosEnPredio", predioDao.contarActivos(predio.getIdPredio()));
        return "predio/formulario";
    }

    /**
     * Guarda municipio y código del predio. Solo ADMINISTRADOR / SUPER USUARIO: cambia el
     * prefijo con que se codifican los activos nuevos (los ya registrados conservan su código).
     */
    @ValidarUsuarioAutenticado
    @PostMapping("/guardar-config")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> guardarConfig(HttpServletRequest request,
            @RequestParam("idEnc") String idEnc,
            @RequestParam(name = "idMunicipio", required = false) Long idMunicipio,
            @RequestParam(name = "codigo", required = false) String codigo) {
        if (!RolesSciaf.esAdministrativo(request)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("ok", false,
                    "msg", "Solo un ADMINISTRADOR o SUPER USUARIO puede cambiar la codificación de un predio."));
        }
        Predio predio;
        try {
            predio = predioServicio.findById(Long.parseLong(Encriptar.decrypt(idEnc)));
        } catch (Exception e) {
            predio = null;
        }
        if (predio == null || "ELIMINADO".equals(predio.getEstado())) {
            return ResponseEntity.ok(Map.of("ok", false, "msg", "El predio ya no existe."));
        }

        Municipio municipio = idMunicipio != null ? municipioService.findById(idMunicipio) : null;
        if (municipio == null || !"ACTIVO".equals(municipio.getEstado())) {
            return ResponseEntity.ok(Map.of("ok", false, "msg", "Elija un municipio."));
        }
        String cod = codigo == null ? "" : codigo.trim().replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
        if (cod.isEmpty()) return ResponseEntity.ok(Map.of("ok", false, "msg", "Ingrese el código del predio."));
        if (!CODIGO_VALIDO.matcher(cod).matches()) {
            return ResponseEntity.ok(Map.of("ok", false, "msg", "El código solo puede tener letras y números (1 a 6), "
                    + "sin guiones ni símbolos: forma parte del código de los activos."));
        }
        String codMun = municipio.getCodigo() == null ? "" : municipio.getCodigo().trim().toUpperCase(Locale.ROOT);
        if (!CODIGO_VALIDO.matcher(codMun).matches()) {
            return ResponseEntity.ok(Map.of("ok", false, "msg", "El código del municipio " + municipio.getNombre() + " («"
                    + codMun + "») no sirve para codificar activos: corríjalo primero en Municipio (solo letras y números)."));
        }
        for (Predio otro : predioDao.conMunicipioYCodigo(municipio.getIdMunicipio(), cod)) {
            if (!otro.getIdPredio().equals(predio.getIdPredio())) {
                return ResponseEntity.ok(Map.of("ok", false, "msg", "El código " + cod + " ya lo usa el predio "
                        + otro.getUnidad() + " en " + municipio.getNombre() + ": los activos de ambos tendrían el mismo prefijo."));
            }
        }

        // Un prefijo que ya llevan activos de OTRO predio (por una recodificación anterior) no se reutiliza.
        long ajenos = predioDao.contarActivosConPrefijoDeOtro(codMun + "-" + cod + "-%", predio.getIdPredio());
        if (ajenos > 0) {
            return ResponseEntity.ok(Map.of("ok", false, "msg", "El prefijo " + codMun + "-" + cod + " ya lo llevan " + ajenos
                    + " activo(s) de otro predio: los correlativos se mezclarían. Elija otro código."));
        }

        predio.setMunicipio(municipio);
        predio.setCodigo(cod);
        Usuario usuario = (Usuario) request.getSession().getAttribute("usuario");
        if (usuario != null) predio.setModificacionIdUsuario(usuario.getIdUsuario());
        predioServicio.save(predio);
        return ResponseEntity.ok(Map.of("ok", true, "msg", "Predio " + predio.getUnidad() + " configurado: prefijo "
                + municipio.getCodigo() + "-" + cod));
    }

    /* ── Sincronización con el VSIAF ───────────────────────────────────────── */

    @ValidarUsuarioAutenticado
    @PostMapping("/sync-from-mounted")
    @ResponseBody
    public ResponseEntity<?> syncFromMounted(HttpServletRequest request,
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "gestion", required = false) Short gestionPreferida,
            @RequestParam(name = "forzarCompleto", defaultValue = "false") boolean forzarCompleto) {

        long inicio = System.currentTimeMillis();
        if (!RolesSciaf.esAdministrativo(request)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("ok", false,
                    "message", "Solo un ADMINISTRADOR o SUPER USUARIO puede sincronizar con el VSIAF."));
        }
        if (!vsiaf.dbf("sincronización manual")) {
            return ResponseEntity.ok(Map.of("ok", false, "message", vsiaf.motivoDbf()));
        }

        try {
            // Leer DBF
            var filas = dbfService.listarUnidadAdminAll(q);

            // Cargar predios existentes en caché (1 sola consulta)
            Map<String, Predio> prediosExistentes = cargarPrediosEnCache(gestionPreferida);

            int inserted = 0, updated = 0, skipped = 0, sinEntidad = 0;
            List<Predio> batch = new ArrayList<>(500);

            for (var f : filas) {
                // Validar claves obligatorias
                if (isBlank(f.getEntidadCodigo()) || isBlank(f.getUnidad())) {
                    skipped++;
                    continue;
                }

                // Resolver entidad
                Entidad entidad = resolverEntidad(gestionPreferida, f.getEntidadCodigo());
                if (entidad == null) {
                    sinEntidad++;
                    continue;
                }

                // Crear clave única para búsqueda en caché
                String clave = entidad.getIdEntidad() + "-" + f.getUnidad().trim();
                Predio predioExistente = prediosExistentes.get(clave);

                // Determinar si es nuevo o actualización
                Predio predio;
                boolean esNuevo = (predioExistente == null);

                if (esNuevo) {
                    predio = new Predio();
                    predio.setEntidad(entidad);
                    predio.setUnidad(f.getUnidad().trim());
                } else {
                    predio = predioExistente;
                }

                // Mapear datos del DBF (municipio y código son del SCIAF: no se tocan)
                predio.setDescrip(f.getDescrip() != null ? f.getDescrip().trim() : "");
                predio.setCiudad(f.getCiudad() != null ? f.getCiudad().trim() : null);
                predio.setEstadoUni(f.getEstadoUni());
                predio.setEstado("ACTIVO");

                // OPTIMIZACIÓN: Calcular hash y comparar
                String nuevoHash = predio.calcularHash();

                if (!esNuevo && !forzarCompleto) {
                    if (nuevoHash.equals(predio.getHashDatos())) {
                        skipped++;
                        continue; // NO procesar si no hay cambios
                    }
                }

                // Actualizar metadatos de sincronización
                predio.setHashDatos(nuevoHash);
                predio.setFechaUltimaSync(LocalDateTime.now());

                batch.add(predio);
                if (esNuevo) inserted++; else updated++;

                // Guardar en lotes de 500
                if (batch.size() >= 500) {
                    predioServicio.saveAll(batch);
                    batch.clear();
                }
            }

            // Guardar lote final
            if (!batch.isEmpty()) {
                predioServicio.saveAll(batch);
                batch.clear();
            }

            // Registrar en sync_control
            long duracion = System.currentTimeMillis() - inicio;
            syncControlService.registrarSincronizacion("predio", filas.size(), inserted, updated, duracion);

            return ResponseEntity.ok(Map.of(
                "ok", true,
                "totalLeidas", filas.size(),
                "insertados", inserted,
                "actualizados", updated,
                "omitidos", skipped,
                "sinEntidadEnBD", sinEntidad,
                "duracionMs", duracion,
                "mensaje", String.format("Sincronización completada en %.2f segundos", duracion / 1000.0)
            ));

        } catch (Exception ex) {
            syncControlService.registrarError("predio", ex.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                "ok", false,
                "message", "Error sincronizando UNIDADADMIN: " + ex.getMessage()
            ));
        }
    }

    /** Todos los predios en memoria con su entidad (1 sola consulta SQL). */
    private Map<String, Predio> cargarPrediosEnCache(Short gestion) {
        return predioDao.todosConRelaciones().stream()
            .filter(p -> p.getEntidad() != null && p.getUnidad() != null)
            .filter(p -> gestion == null || gestion.equals(p.getEntidad().getGestion()))
            .collect(Collectors.toMap(
                p -> p.getEntidad().getIdEntidad() + "-" + p.getUnidad().trim(),
                p -> p,
                (existing, replacement) -> existing // En caso de duplicados, mantener el existente
            ));
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
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
            int n = Integer.parseInt(base);
            return String.format("%04d", n);
        } catch (NumberFormatException e) {
            return s; // si no es numérico, deja como está
        }
    }

    /**
     * Intenta resolver por gestión preferida; si no, por la más reciente. Prueba 3
     * variantes: como viene, sin ceros a la izquierda y con 4 dígitos.
     */
    private Entidad resolverEntidad(Short gestionPreferida, String codigo) {
        String cod = codigo;
        String codNoZeros = stripLeftZeros(codigo);
        String codPad4 = leftPad4(codigo); // por si en BD está siempre 4 dígitos

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
}
