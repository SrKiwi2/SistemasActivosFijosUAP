package com.usic.SistemasActivosFijosUAP.controller.persona;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.usic.SistemasActivosFijosUAP.anotacion.ValidarUsuarioAutenticado;
import com.usic.SistemasActivosFijosUAP.config.Encriptar;
import com.usic.SistemasActivosFijosUAP.model.IService.IPersonaService;
import com.usic.SistemasActivosFijosUAP.model.IService.IUsuarioService;
import com.usic.SistemasActivosFijosUAP.model.dao.IPersonasDao;
import com.usic.SistemasActivosFijosUAP.model.entity.Persona;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;
import com.usic.SistemasActivosFijosUAP.model.service.seguridad.SesionPermisosService;
import com.usic.SistemasActivosFijosUAP.model.service.supervision.ResponsableGestionService;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

/**
 * Mantenimiento de personas. Guardar y eliminar responden JSON { ok, msg }, que es lo
 * que espera la plantilla de pantallas (sciaf-modulo.js). La tabla se pagina en el
 * servidor (/api/datatables) porque hay miles de personas.
 */
@Controller
@RequestMapping("/administracion/persona")
@RequiredArgsConstructor
public class PersonaController {

    private final IPersonaService personaService;
    private final IUsuarioService usuarioService;
    private final SesionPermisosService sesionPermisos;
    private final ResponsableGestionService responsableGestionService;

    @ValidarUsuarioAutenticado
    @GetMapping("/vista")
    public String inicio() {
        return "persona/vista";
    }

    // API DataTables (JSON)
    @ValidarUsuarioAutenticado
    @PostMapping(value="/api/datatables", produces=MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    @Transactional(readOnly = true)
    public Map<String,Object> apiDataTables(
        @RequestParam(name="draw",   defaultValue="1") int draw,
        @RequestParam(name="start",  defaultValue="0") int start,
        @RequestParam(name="length", defaultValue="25") int length,
        @RequestParam(name="search[value]", required=false) String search
    ) {
        int size = (length < 0) ? 1000 : length;
        int page = Math.max(start, 0) / Math.max(size, 1);
        Pageable pageable = PageRequest.of(page, size); // el ORDER BY ya está fijo (nombre, paterno, materno)

        Page<IPersonasDao.PersonaRow> p = personaService.datatable(search, pageable);

        List<Map<String,Object>> data = new ArrayList<>(p.getNumberOfElements());
        for (var row : p.getContent()) {
            Map<String,Object> m = new HashMap<>();
            String enc;
            try { enc = Encriptar.encrypt(String.valueOf(row.getIdPersona())); }
            catch (Exception e) { enc = ""; }
            m.put("idEnc",        enc);
            m.put("nombre",       nvl(row.getNombre()));
            m.put("paterno",      nvl(row.getPaterno()));
            m.put("materno",      nvl(row.getMaterno()));
            m.put("ci",           nvl(row.getCi()));
            m.put("usuarios",     row.getUsuarios() == null ? 0 : row.getUsuarios());
            m.put("responsables", row.getResponsables() == null ? 0 : row.getResponsables());
            data.add(m);
        }

        long total = personaService.countActivos(); // sin filtro

        Map<String,Object> res = new HashMap<>();
        res.put("draw", draw);
        res.put("recordsTotal", total);                 // total sin filtro
        res.put("recordsFiltered", p.getTotalElements()); // total con filtro
        res.put("data", data);
        return res;
    }

    private static String nvl(String s){ return s==null? "": s; }

    /** En MAYÚSCULAS (como en el VSIAF) y sin espacios sobrantes; vacío pasa a null. */
    private static String limpiar(String s) {
        if (s == null) return null;
        String t = s.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
        return t.isEmpty() ? null : t;
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/formulario")
    public String formulario(Model model, Persona persona) {
        return "persona/formulario";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/formulario-edit/{id_persona}")
    public String formularioEdit(Model model, @PathVariable("id_persona") String idPersona) throws Exception {

        Long id = Long.parseLong(Encriptar.decrypt(idPersona));
        Persona persona = personaService.findById(id);
        model.addAttribute("persona", persona);
        model.addAttribute("edit", "true");
        if (persona != null) {
            model.addAttribute("registradoPor", nombreUsuario(persona.getRegistroIdUsuario()));
            model.addAttribute("modificadoPor", nombreUsuario(persona.getModificacionIdUsuario()));
        }

        return "persona/formulario";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/registrar-persona")
    public ResponseEntity<Map<String, Object>> registrar(HttpServletRequest request, Persona persona) {
        // Un alta nunca pisa a otra persona aunque alguien mande un idPersona en la petición.
        persona.setIdPersona(null);
        String error = normalizarYValidar(persona, null);
        if (error != null) {
            Map<String, Object> r = new HashMap<>();
            r.put("ok", false);
            r.put("msg", error);
            // C.I. repetido: se dice quién es, para que el formulario de Usuario la ofrezca.
            if (persona.getCi() != null) {
                personaService.listarPorCi(persona.getCi()).stream().findFirst().ifPresent(p -> {
                    r.put("idExistente", p.getIdPersona());
                    r.put("textoExistente", nombreCompleto(p) + " - " + p.getCi());
                });
            }
            return ResponseEntity.ok(r);
        }

        Usuario usuario = (Usuario) request.getSession().getAttribute("usuario");
        if (usuario != null) {
            persona.setRegistroIdUsuario(usuario.getIdUsuario());
        }
        persona.setEstado("ACTIVO");
        Persona guardada = personaService.save(persona);

        // id y texto: el formulario de Usuario la agrega a su lista y la deja elegida.
        Map<String, Object> r = new HashMap<>();
        r.put("ok", true);
        r.put("msg", "Persona registrada correctamente");
        r.put("idPersona", guardada.getIdPersona());
        r.put("texto", nombreCompleto(guardada) + " - " + guardada.getCi());
        return ResponseEntity.ok(r);
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/modificar-persona")
    public ResponseEntity<Map<String, Object>> modificar(HttpServletRequest request, Persona persona) {
        // Se modifica la persona guardada y no la que llega del formulario: esa trae solo
        // C.I. y nombres, y al guardarla tal cual se perdían correo, extensión,
        // nacionalidad, género y quién/cuándo la registró.
        Persona actual = persona.getIdPersona() == null ? null : personaService.findById(persona.getIdPersona());
        if (actual == null || !"ACTIVO".equals(actual.getEstado())) {
            return ResponseEntity.ok(Map.of("ok", false, "msg", "La persona ya no existe o fue eliminada."));
        }

        String error = normalizarYValidar(persona, actual.getIdPersona());
        if (error != null) {
            return ResponseEntity.ok(Map.of("ok", false, "msg", error));
        }

        String nombreYCiAntes = ResponsableGestionService.nombreYCi(actual);
        boolean identificableAntes = ResponsableGestionService.esIdentificable(actual);
        actual.setCi(persona.getCi());
        actual.setNombre(persona.getNombre());
        actual.setPaterno(persona.getPaterno());
        actual.setMaterno(persona.getMaterno());
        Usuario usuario = (Usuario) request.getSession().getAttribute("usuario");
        if (usuario != null) {
            actual.setModificacionIdUsuario(usuario.getIdUsuario());
        }
        personaService.save(actual);

        // Si la persona tiene usuario, su sesión guarda el nombre viejo (barra superior):
        // se rearma sola y el navegador lo cambia al momento.
        sesionPermisos.personaCambio(actual.getIdPersona(), nombreCompleto(actual));

        // Si es responsable de alguna oficina, el VSIAF tiene el nombre y el C.I. copiados en
        // RESP.DBF: el cambio se manda allá también (por la cola del worker).
        if (!nombreYCiAntes.equals(ResponsableGestionService.nombreYCi(actual))) {
            ResponsableGestionService.Propagacion p =
                    responsableGestionService.propagarPersona(actual.getIdPersona(), null, usuario, identificableAntes);
            if (!p.nada()) {
                return ResponseEntity.ok(Map.of("ok", true, "vsiafOk", !p.conProblemas(),
                        "msg", "Persona modificada. " + p.mensaje()));
            }
        }

        return ResponseEntity.ok(Map.of("ok", true, "msg", "Persona modificada correctamente"));
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/eliminar/{id_persona}")
    public ResponseEntity<Map<String, Object>> eliminar(HttpServletRequest request,
            @PathVariable("id_persona") String idPersona) throws Exception {

        Long id = Long.parseLong(Encriptar.decrypt(idPersona));
        Persona persona = personaService.findById(id);
        if (persona == null) {
            return ResponseEntity.ok(Map.of("ok", false, "msg", "La persona no existe."));
        }
        persona.setEstado("ELIMINADO");
        Usuario usuario = (Usuario) request.getSession().getAttribute("usuario");
        if (usuario != null) {
            persona.setModificacionIdUsuario(usuario.getIdUsuario());
        }
        personaService.save(persona);

        return ResponseEntity.ok(Map.of("ok", true, "msg", "Persona eliminada"));
    }

    /**
     * Limpia los campos que llegan del formulario (sobre el mismo objeto) y devuelve el
     * mensaje de error, o null si están bien. {@code idPropio} excluye a la persona que se
     * está editando de la búsqueda de C.I. repetido.
     */
    private String normalizarYValidar(Persona persona, Long idPropio) {
        persona.setCi(limpiar(persona.getCi()));
        persona.setNombre(limpiar(persona.getNombre()));
        persona.setPaterno(limpiar(persona.getPaterno()));
        persona.setMaterno(limpiar(persona.getMaterno()));

        if (persona.getCi() == null) {
            return "Ingrese el C.I.";
        }
        if (persona.getNombre() == null) {
            return "Ingrese el nombre.";
        }
        for (Persona existente : personaService.listarPorCi(persona.getCi())) {
            if (!existente.getIdPersona().equals(idPropio)) {
                return "Ya existe una persona con el C.I. " + persona.getCi()
                        + " (" + nombreCompleto(existente) + ").";
            }
        }
        return null;
    }

    /** getNombreCompleto() de la entidad escribe "null" si faltan los dos apellidos. */
    private static String nombreCompleto(Persona p) {
        return String.join(" ", java.util.stream.Stream.of(p.getNombre(), p.getPaterno(), p.getMaterno())
                .filter(s -> s != null && !s.isBlank()).toList());
    }

    private String nombreUsuario(Long idUsuario) {
        if (idUsuario == null) {
            return null;
        }
        return usuarioService.findByIdUsuario(idUsuario).map(Usuario::getUsuario).orElse(null);
    }
}
