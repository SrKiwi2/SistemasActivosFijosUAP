package com.usic.SistemasActivosFijosUAP.controller.responsable;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.client.RestTemplate;

import com.usic.SistemasActivosFijosUAP.anotacion.ValidarUsuarioAutenticado;
import com.usic.SistemasActivosFijosUAP.config.Encriptar;
import com.usic.SistemasActivosFijosUAP.interoperabilidad.JavaDbfService;
import com.usic.SistemasActivosFijosUAP.model.IService.ICargoService;
import com.usic.SistemasActivosFijosUAP.model.IService.IEntidadService;
import com.usic.SistemasActivosFijosUAP.model.IService.IGeneroService;
import com.usic.SistemasActivosFijosUAP.model.IService.IOficinaService;
import com.usic.SistemasActivosFijosUAP.model.IService.IPersonaService;
import com.usic.SistemasActivosFijosUAP.model.IService.IResponsableService;
import com.usic.SistemasActivosFijosUAP.model.dao.IResposableDao;
import com.usic.SistemasActivosFijosUAP.model.dto.ResponsableRegistroDTO;
import com.usic.SistemasActivosFijosUAP.model.dto.interoperabilidad.ResponsableDbf;
import com.usic.SistemasActivosFijosUAP.model.dto.responsable.ResponsableApiDataDTO;
import com.usic.SistemasActivosFijosUAP.model.entity.Cargo;
import com.usic.SistemasActivosFijosUAP.model.entity.Entidad;
import com.usic.SistemasActivosFijosUAP.model.entity.Genero;
import com.usic.SistemasActivosFijosUAP.model.entity.Oficina;
import com.usic.SistemasActivosFijosUAP.model.entity.Persona;
import com.usic.SistemasActivosFijosUAP.model.entity.Responsable;
import com.usic.SistemasActivosFijosUAP.model.repository.FuncionesResponsableRepo;

import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/administracion/responsable")
@RequiredArgsConstructor
public class ResponsableController {

    private final IResponsableService responsableService;
    private final IPersonaService personaService;
    private final IOficinaService oficinaService;
    private final ICargoService cargoService;
    private final IGeneroService generoService;
    private final FuncionesResponsableRepo funcionesResponsableRepo;
    private final JavaDbfService dbfService;
    private final IEntidadService entidadService;

    @ValidarUsuarioAutenticado
    @GetMapping("/vista")
    public String inicioResponsable(Model model) {
        model.addAttribute("oficinas", oficinaService.listarOficinas());
        return "responsable/vista";
    }

    @ValidarUsuarioAutenticado
    @PostMapping(value = "/api/datatables", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    @Transactional(readOnly = true)
    public Map<String, Object> apiDataTables(
            @RequestParam(name = "draw", defaultValue = "1") int draw,
            @RequestParam(name = "start", defaultValue = "0") int start,
            @RequestParam(name = "length", defaultValue = "25") int length,
            @RequestParam(name = "search[value]", required = false) String search,
            @RequestParam(name = "oficinaId", required = false) Long oficinaId) {

        int size = (length < 0) ? 1000 : length;
        int page = Math.max(start, 0) / Math.max(size, 1);
        Pageable pageable = PageRequest.of(page, size);

        // 1) Intento BD con server-side paging
        Page<IResposableDao.ResponsableRow> p = responsableService.datatable(search, oficinaId, pageable);
        if (p.getTotalElements() > 0) {
            List<Map<String, Object>> data = new ArrayList<>(p.getNumberOfElements());
            for (var row : p.getContent()) {
                String idEnc;
                try {
                    idEnc = Encriptar.encrypt(String.valueOf(row.getIdResponsable()));
                } catch (Exception e) {
                    idEnc = "";
                }

                Map<String, Object> m = new HashMap<>();
                m.put("idEnc", idEnc);
                m.put("codFun", nvl(row.getCodFun()));
                m.put("nombre", nvl(row.getNombre()));
                m.put("paterno", nvl(row.getPaterno()));
                m.put("materno", nvl(row.getMaterno()));
                m.put("ci", nvl(row.getCi()));
                m.put("oficina", nvl(row.getOficina()));
                m.put("cargo", nvl(row.getCargo()));
                data.add(m);
            }

            long total = responsableService.countActivos(); // total sin filtro BD
            Map<String, Object> res = new HashMap<>();
            res.put("draw", draw);
            res.put("recordsTotal", total);
            res.put("recordsFiltered", p.getTotalElements());
            res.put("data", data);
            res.put("source", "db"); // opcional, por si quieres mostrar un badge en el front
            return res;
        }

        // 2) Fallback: leer RESP.DBF y devolver el mismo JSON para DataTables
        try {
            // a) leer y filtrar por 'search' desde el propio lector
            List<ResponsableDbf> filas = dbfService.listarResponsableAll(search);

            // b) si viene oficinaId, filtramos por esa oficina (unidad + codOfi)
            Oficina oficinaFiltro = null;
            if (oficinaId != null) {
                oficinaFiltro = oficinaService.findById(oficinaId);
            }
            if (oficinaFiltro != null) {
                String unidad = oficinaFiltro.getPredio().getUnidad();
                Short codOf = oficinaFiltro.getCodOfi();
                // además, si quieres, podrías matchear ENTIDAD por sigla/código, pero con
                // unidad+codOf suele bastar
                String unidadNorm = unidad == null ? null : unidad.trim().toUpperCase(Locale.ROOT);
                List<ResponsableDbf> filtradas = new ArrayList<>();
                for (var r : filas) {
                    String u = r.getUnidad() == null ? null : r.getUnidad().trim().toUpperCase(Locale.ROOT);
                    if (Objects.equals(u, unidadNorm) && Objects.equals(r.getCodOfi(), codOf)) {
                        filtradas.add(r);
                    }
                }
                filas = filtradas;
            }

            // c) total después de filtros (para DataTables fallback)
            int totalAfterFilter = filas.size();

            // d) paginar con start/length
            int from = Math.min(start, totalAfterFilter);
            int to = Math.min(from + size, totalAfterFilter);
            List<ResponsableDbf> pageList = filas.subList(from, to);

            // e) mapear a las mismas columnas del DataTable
            List<Map<String, Object>> data = new ArrayList<>(pageList.size());
            for (var r : pageList) {
                // nombre → nombre/paterno/materno (simple split)
                String[] np = splitNombrePersona(nvl(r.getNombre()));
                String nombre = np[0];
                String paterno = np[1];
                String materno = np[2];

                // etiqueta oficina: UNIDAD - CODOFI (SIGLA) si se puede, sino UNIDAD - CODOFI
                String oficinaLabel;
                if (oficinaFiltro != null) {
                    String sig = (oficinaFiltro.getPredio().getEntidad().getSigla() == null) ? ""
                            : oficinaFiltro.getPredio().getEntidad().getSigla();
                    oficinaLabel = oficinaFiltro.getPredio().getUnidad() + " - " + oficinaFiltro.getCodOfi()
                            + (isBlank(sig) ? "" : (" (" + sig + ")"));
                } else {
                    oficinaLabel = (nvl(r.getUnidad()) == null ? "" : r.getUnidad()) + " - "
                            + (r.getCodOfi() == null ? "" : r.getCodOfi());
                }

                Map<String, Object> m = new HashMap<>();
                m.put("idEnc", ""); // viene de DBF, no hay id
                m.put("codFun", nvl(r.getCodResp())); // en tu tabla es "CODIGO FUNCIONARIO"
                m.put("nombre", nvl(nombre));
                m.put("paterno", nvl(paterno));
                m.put("materno", nvl(materno));
                m.put("ci", nvl(r.getCi()));
                m.put("oficina", oficinaLabel);
                m.put("cargo", nvl(r.getCargo()));
                data.add(m);
            }

            // f) armar respuesta
            Map<String, Object> res = new HashMap<>();
            res.put("draw", draw);
            // Como es fallback, usamos el mismo total para ambos campos
            res.put("recordsTotal", totalAfterFilter);
            res.put("recordsFiltered", totalAfterFilter);
            res.put("data", data);
            res.put("source", "dbf"); // opcional: badge en front
            return res;

        } catch (Exception ex) {
            // Si el DBF tampoco pudo leerse, devolvemos vacío y un mensaje si gustas
            Map<String, Object> res = new HashMap<>();
            res.put("draw", draw);
            res.put("recordsTotal", 0);
            res.put("recordsFiltered", 0);
            res.put("data", Collections.emptyList());
            res.put("source", "error");
            return res;
        }
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/formulario")
    public String formularioResponsable(Model model) { 
        model.addAttribute("responsable", new Responsable());
        model.addAttribute("oficinas", oficinaService.listarOficinas());
        return "responsable/formulario";
    }

    @GetMapping("/consultar-api-datos")
    @ResponseBody
    public ResponseEntity<ResponsableApiDataDTO> consultarApiDatos(
            @RequestParam String codigoFuncionario,
            @RequestParam String ci) {
        try {
            ResponsableApiDataDTO dto = responsableService.getResponsableDataFromApi(codigoFuncionario, ci);
            return ResponseEntity.ok(dto);
        } catch (RuntimeException e) {
            // Manejo de error de la API (ej: 500 Internal Server Error o 404 Not Found)
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/registrar-responsable")
    public ResponseEntity<ResponsableRegistroDTO> registroResponsable(@RequestParam String codigoFuncionario,
            @RequestParam String ci) {

        Responsable responsable = responsableService.buscarPorCodigo(codigoFuncionario);
        if (responsable != null)
            return ResponseEntity.ok(new ResponsableRegistroDTO(responsable));

        Map<String, String> requestBody = Map.of(
                "usuario", codigoFuncionario,
                "contrasena", ci);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("key", "e73b1991c59a67fe182524e4d12da556136ced8a9da310c3af4c4efbde804a10");

        RestTemplate restTemplate = new RestTemplate();
        HttpEntity<Map<String, String>> request = new HttpEntity<>(requestBody, headers);

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                "http://virtual.uap.edu.bo:7174/api/londraPost/v1/obtenerDatos",
                HttpMethod.POST,
                request,
                new ParameterizedTypeReference<Map<String, Object>>() {
                });

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new RuntimeException("Error consultando API externa.");
        }

        Map<String, Object> datos = Objects.requireNonNull(response.getBody());
        String nombre = (String) datos.get("per_nombres");
        String paterno = (String) datos.get("per_ap_paterno");
        String materno = (String) datos.get("per_ap_materno");
        String ciPersona = (String) datos.get("per_num_doc");
        String correo = (String) datos.get("perd_email_personal");
        String sexo = (String) datos.get("per_sexo");
        String nombreOficina = (String) datos.get("eo_descripcion");
        String nombreCargo = (String) datos.get("p_descripcion");

        Persona persona = personaService.buscarPersonaPorCI(ciPersona);
        if (persona == null) {
            persona = personaService.buscarPersonaPorNombreCompletoUno(nombre, paterno, materno);
        }

        if (persona == null) {
            persona = new Persona();
            persona.setNombre(nombre);
            persona.setPaterno(paterno);
            persona.setMaterno(materno);
            persona.setCi(ciPersona);
            persona.setCorreo(correo);
            persona.setEstado("ACTIVO");

            Genero genero = generoService.buscarGeneroPorNombre(sexo);
            if (genero == null) {
                genero = new Genero();
                genero.setNombre(sexo);
                genero.setEstado("ACTIVO");
                genero.setRegistro(new Date());
                genero.setRegistroIdUsuario(1L);
                generoService.save(genero);
            }
            persona.setGenero(genero);

            personaService.save(persona);
        }

        Oficina oficina = oficinaService.buscarPorNombre(nombreOficina).orElseGet(() -> {
            Oficina o = new Oficina();
            o.setNombre(nombreOficina);
            o.setEstado("ACTIVO");
            o.setRegistro(new Date());
            o.setRegistroIdUsuario(1L);
            return oficinaService.save(o);
        });

        Cargo cargo = cargoService.buscarPorNombre(nombreCargo);
        if (cargo == null) {
            cargo = new Cargo();
            cargo.setNombre(nombreCargo);
            cargo.setEstado("ACTIVO");
            cargo.setRegistro(new Date());
            cargo.setRegistroIdUsuario(1L);
            cargoService.save(cargo);
        }

        List<Responsable> relacionados = responsableService.findByPersonaAndEstado(persona, "ACTIVO");
        if (relacionados.isEmpty()) {
            // Si NO existe ninguno y quieres crear uno "base", créalo aquí.
            // Si NO quieres crear nada cuando no hay, simplemente retorna.
            String codigo_responsable = funcionesResponsableRepo.siguienteCodigoPorOficinaStr(oficina.getIdOficina());
            Responsable r = new Responsable();
            r.setPersona(persona);
            r.setCargo(cargo);
            r.setOficina(oficina);
            r.setEstado("ACTIVO");
            r.setCodigoFuncionario(codigo_responsable);
            r.setApiEstado((short) 1);
            r.setRegistroIdUsuario(1L);
            r.setRegistro(new Date());
            return ResponseEntity.ok(new ResponsableRegistroDTO(responsableService.save(r)));
        }

        // 2) Actualizas SOLO lo que corresponde (campo a campo)
        for (Responsable r : relacionados) {
            // Si tu intención es solo actualizar estos campos en los relacionados:
            if (cargo != null) {
                r.setCargo(cargo);
            }
            r.setModificacion(new Date());
            r.setModificacionIdUsuario(1L);
        }
        responsableService.saveAll(relacionados);

        Optional<Responsable> exactoMismaOficina = relacionados.stream()
                .filter(r -> r.getOficina() != null && r.getOficina().getIdOficina().equals(oficina.getIdOficina()))
                .findFirst();

        if (exactoMismaOficina.isPresent()) {
            Responsable rSel = exactoMismaOficina.get();
            if (rSel.getCodigoFuncionario() == null || rSel.getCodigoFuncionario().isBlank()) {
                rSel.setCodigoFuncionario(codigoFuncionario);
            }
            rSel.setModificacion(new Date());
            rSel.setModificacionIdUsuario(1L);
            return ResponseEntity.ok(new ResponsableRegistroDTO(responsableService.save(rSel)));
        }

        // 6.c) No hay responsable de ESA oficina: crear uno nuevo para esa oficina y
        // devolverlo
        Responsable nuevo = new Responsable();
        nuevo.setPersona(persona);
        nuevo.setCargo(cargo);
        nuevo.setOficina(oficina);
        nuevo.setEstado("ACTIVO");
        nuevo.setCodigoFuncionario(codigoFuncionario);
        nuevo.setApiEstado((short) 1);
        nuevo.setRegistroIdUsuario(1L);
        nuevo.setRegistro(new Date());

        return ResponseEntity.ok(new ResponsableRegistroDTO(responsableService.save(nuevo)));
    }

    // En ResponsableController
    @ValidarUsuarioAutenticado
    @PostMapping("/sync-from-mounted")
    @ResponseBody
    public ResponseEntity<?> syncFromMounted(
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "gestion", required = false) Short gestionPreferida) {
        try {
            var filas = dbfService.listarResponsableAll(q);

            int inserted = 0, updated = 0, sinEntidad = 0, sinPredio = 0, sinOficina = 0;
            int creadosPersona = 0, creadosCargo = 0, duplicadosEnDbf = 0;

            // caches para acelerar (mismo patrón que usaste)
            Map<String, Persona> cachePersonaPorCI = new HashMap<>(4000); // ci -> Persona
            Map<String, Persona> cachePersonaPorNombre = new HashMap<>(8000); // canon -> Persona
            Map<String, Cargo> cacheCargoPorNombre = new HashMap<>(2000);
            Set<String> antiDupDBF = new HashSet<>(filas.size());

            List<Responsable> batch = new ArrayList<>(500);

            for (var f : filas) {
                // ENTIDAD
                String cod = nvl(f.getEntidadCodigo());
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

                // PREDIO (por unidad) y OFICINA (por codOfi)
                String unidad = normUnidad(f.getUnidad());
                Short codOfi = f.getCodOfi();
                if (isBlank(unidad) || codOfi == null) {
                    sinPredio++;
                    continue;
                }

                var oficina = oficinaService.findByEntidadUnidadAndCodOfi(entidad, unidad, codOfi).orElse(null);
                if (oficina == null) {
                    sinOficina++;
                    continue;
                }

                // Deduplicación por (oficinaId|codFunc|nombre|ci)
                String k = oficina.getIdOficina() + "|" + (f.getCodResp() == null ? "" : f.getCodResp().trim()) + "|" +
                        (f.getNombre() == null ? "" : f.getNombre().trim()) + "|"
                        + (f.getCi() == null ? "" : f.getCi().trim());
                if (!antiDupDBF.add(k)) {
                    duplicadosEnDbf++;
                    continue;
                }

                // PERSONA (upsert)
                Persona persona = null;
                String ci = nvl(f.getCi());
                if (!isBlank(ci)) {
                    persona = cachePersonaPorCI.get(ci);
                    if (persona == null) {
                        persona = personaService.findFirstByCi(ci).orElse(null);
                        if (persona != null)
                            cachePersonaPorCI.put(ci, persona);
                    }
                }
                if (persona == null) {
                    String canon = canonNombre(nvl(f.getNombre()));
                    if (!isBlank(canon)) {
                        persona = cachePersonaPorNombre.get(canon);
                    }
                    if (persona == null) {
                        // crear persona básica a partir del nombre “Nombres ApellidoP ApellidoM”
                        String[] np = splitNombrePersona(nvl(f.getNombre())); // implementa simple: [nombre, paterno,
                                                                              // materno]
                        persona = new Persona();
                        persona.setNombre(clip(np[0], 120));
                        persona.setPaterno(clip(np[1], 60));
                        persona.setMaterno(clip(np[2], 60));
                        persona.setCi(isBlank(ci) ? null : ci.trim());
                        persona.setEstado("ACTIVO");
                        personaService.save(persona);
                        creadosPersona++;
                        if (!isBlank(ci))
                            cachePersonaPorCI.put(ci, persona);
                        if (!isBlank(canon))
                            cachePersonaPorNombre.put(canon, persona);
                    }
                }

                // CARGO (upsert por nombre)
                Cargo cargo = null;
                String cargoNom = nvl(f.getCargo());
                if (!isBlank(cargoNom)) {
                    String keyCargo = cargoNom.trim().toLowerCase(Locale.ROOT);
                    cargo = cacheCargoPorNombre.get(keyCargo);
                    if (cargo == null) {
                        cargo = cargoService.findFirstByNombreIgnoreCase(cargoNom.trim()).orElse(null);
                        if (cargo == null) {
                            Cargo c = new Cargo();
                            c.setNombre(clip(cargoNom.trim(), 120));
                            c.setDescripcion(null);
                            c.setEstado("ACTIVO");
                            cargo = cargoService.save(c);
                            creadosCargo++;
                        }
                        cacheCargoPorNombre.put(keyCargo, cargo);
                    }
                }

                // UPSERT Responsable:
                // Regla: si viene CODRESP -> llave (oficina, codigoFuncionario)
                // si NO viene -> llave (oficina, persona)
                Responsable resp = null;
                String codFun = isBlank(f.getCodResp()) ? null : f.getCodResp().trim();

                if (!isBlank(codFun)) {
                    resp = responsableService.findByOficinaAndCodigoFuncionario(oficina, codFun).orElse(null);
                } else {
                    resp = responsableService.findByOficinaAndPersona(oficina, persona).orElse(null);
                }

                boolean nuevo = (resp == null);
                if (resp == null) {
                    resp = new Responsable();
                    resp.setOficina(oficina);
                    resp.setPersona(persona);
                }

                resp.setCodigoFuncionario(codFun);
                resp.setCargo(cargo);
                resp.setObserv(nvl(f.getObserv()));
                resp.setFechaUlt(f.getFechaUlt());
                resp.setUsuario(trunc(nvl(f.getUsuario()), 60));
                resp.setCodExp(f.getCodExp());
                resp.setApiEstado(f.getApiEstado());
                resp.setEstado("ACTIVO");

                if (nuevo)
                    inserted++;
                else
                    updated++;
                // Guarda por fila (para ver errores exactos) o en lote. Aquí guardo en lote
                // como en otros:
                batch.add(resp);
                if (batch.size() == 500) {
                    responsableService.saveAll(batch);
                    batch.clear();
                }
            }
            if (!batch.isEmpty())
                responsableService.saveAll(batch);

            return ResponseEntity.ok(Map.of(
                    "ok", true,
                    "totalLeidas", filas.size(),
                    "insertados", inserted,
                    "actualizados", updated,
                    "sinEntidad", sinEntidad,
                    "sinPredio", sinPredio,
                    "sinOficina", sinOficina,
                    "creadosPersona", creadosPersona,
                    "creadosCargo", creadosCargo,
                    "duplicadosEnDbf", duplicadosEnDbf));
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "ok", false,
                    "message", "Error sincronizando RESPONSABLE: " + ex.getMessage()));
        }
    }

    
    // HELPERS

    private static String nvl(String s) {
        return s == null ? "" : s;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private String stripLeftZeros(String s) {
        if (s == null)
            return null;
        return s.replaceFirst("^0+(?!$)", "");
    }

    private String leftPad4(String s) {
        if (s == null)
            return null;
        String t = stripLeftZeros(s);
        if (t == null)
            return null;
        return String.format("%04d", Integer.parseInt(t));
    }

    private String normUnidad(String u) {
        return u == null ? null : u.trim();
    }

    private String trunc(String s, int n) {
        if (s == null)
            return null;
        return s.length() > n ? s.substring(0, n) : s;
    }

    private String clip(String s, int n) {
        return trunc(nvl(s), n);
    }

    private static String canonNombre(String s) {
        String t = nvl(s);
        if (t == null)
            return null;
        // quita acentos
        t = Normalizer.normalize(t, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        // mayúsculas
        t = t.toUpperCase(Locale.ROOT);
        // deja solo letras/números/espacios
        t = t.replaceAll("[^A-Z0-9\\s]+", " ");
        // colapsa espacios
        t = t.trim().replaceAll("\\s+", " ");
        return t.isEmpty() ? null : t;
    }

    // divide lo más básico: "Nombres ApellidoP ApellidoM"
    private String[] splitNombrePersona(String full) {
        String n = nvl(full);
        if (n == null)
            return new String[] { "DESCONOCIDO", null, null };
        String[] parts = n.split("\\s+");
        if (parts.length == 1)
            return new String[] { parts[0], null, null };
        if (parts.length == 2)
            return new String[] { parts[0], parts[1], null };
        // nombre = todo salvo los dos últimos
        String nombre = String.join(" ", Arrays.copyOf(parts, parts.length - 2));
        String paterno = parts[parts.length - 2];
        String materno = parts[parts.length - 1];
        return new String[] { nombre, paterno, materno };
    }
}
