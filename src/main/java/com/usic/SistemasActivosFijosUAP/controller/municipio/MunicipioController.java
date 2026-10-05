package com.usic.SistemasActivosFijosUAP.controller.municipio;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.usic.SistemasActivosFijosUAP.anotacion.ValidarUsuarioAutenticado;
import com.usic.SistemasActivosFijosUAP.config.Encriptar;
import com.usic.SistemasActivosFijosUAP.config.RolesSciaf;
import com.usic.SistemasActivosFijosUAP.model.IService.IMunicipioService;
import com.usic.SistemasActivosFijosUAP.model.entity.Municipio;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

/**
 * Municipios: catálogo propio del SCIAF (no existe en el VSIAF) al que se asignan los predios.
 * Guardar y eliminar responden JSON { ok, msg }, como espera la plantilla de pantallas
 * (sciaf-modulo.js). Nombre y código en MAYÚSCULAS y sin repetirse.
 * <p>
 * El código del municipio es la primera parte del código de cada activo
 * (municipio-predio-grupo-correlativo): por eso solo lo administran ADMINISTRADOR / SUPER
 * USUARIO, no se cambia si hay predios que lo usan, un municipio con predios no se elimina y
 * un código no se reutiliza nunca (ni el de un municipio eliminado).
 */
@Controller
@RequestMapping("/administracion/municipio")
@RequiredArgsConstructor
public class MunicipioController {

    private final IMunicipioService municipioService;

    /** Letras y números, sin guiones ni símbolos: el guion separa las partes del código del activo. */
    static final java.util.regex.Pattern CODIGO_VALIDO = java.util.regex.Pattern.compile("^[A-Z0-9]{1,6}$");

    private static ResponseEntity<Map<String, Object>> soloAdmin() {
        return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN).body(Map.of("ok", false,
                "msg", "Solo un ADMINISTRADOR o SUPER USUARIO puede modificar municipios: su código forma parte del código de los activos."));
    }

    /** La pantalla llega con la tabla ya armada: un solo pedido al abrir. */
    @ValidarUsuarioAutenticado
    @GetMapping("/vista")
    public String inicioMunicipio(Model model, HttpServletRequest request) throws Exception {
        cargarTabla(model);
        model.addAttribute("esAdmin", RolesSciaf.esAdministrativo(request));
        return "municipio/vista";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/tabla-registros")
    public String tablaRegistrosMunicipio(Model model, HttpServletRequest request) throws Exception {
        cargarTabla(model);
        model.addAttribute("esAdmin", RolesSciaf.esAdministrativo(request));
        return "municipio/tabla_registro";
    }

    private void cargarTabla(Model model) throws Exception {
        List<Municipio> lista = municipioService.listarMunicipios();
        List<String> encryptedIds = new ArrayList<>(lista.size());
        for (Municipio m : lista) encryptedIds.add(Encriptar.encrypt(Long.toString(m.getIdMunicipio())));
        model.addAttribute("listasMunicipios", lista);
        model.addAttribute("id_encryptado", encryptedIds);
        // Cuántos predios usan cada municipio (una consulta agrupada): se muestra y se avisa al quitar.
        model.addAttribute("prediosPorMunicipio", municipioService.prediosPorMunicipio());
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/formulario")
    public String formularioMunicipio(Model model, Municipio municipio) {
        return "municipio/formulario";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/formulario-edit/{id_municipio}")
    public String formularioEditMunicipio(Model model, @PathVariable("id_municipio") String idMunicipio) throws Exception {
        Long id = Long.parseLong(Encriptar.decrypt(idMunicipio));
        model.addAttribute("municipio", municipioService.findById(id));
        model.addAttribute("edit", "true");
        return "municipio/formulario";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/registrar-municipio")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> registrarMunicipio(HttpServletRequest request, Municipio municipio) {
        if (!RolesSciaf.esAdministrativo(request)) return soloAdmin();
        municipio.setIdMunicipio(null);   // un alta nunca pisa otro registro
        String error = normalizarYValidar(municipio, null, null);
        if (error != null) return ResponseEntity.ok(Map.of("ok", false, "msg", error));

        municipio.setEstado("ACTIVO");
        Usuario usuario = (Usuario) request.getSession().getAttribute("usuario");
        if (usuario != null) municipio.setRegistroIdUsuario(usuario.getIdUsuario());
        municipioService.save(municipio);
        return ResponseEntity.ok(Map.of("ok", true, "msg", "Municipio registrado correctamente"));
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/modificar-municipio")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> modificarMunicipio(HttpServletRequest request, Municipio municipio) {
        if (!RolesSciaf.esAdministrativo(request)) return soloAdmin();
        // Se modifica el registro guardado, no el que llega del formulario (que borraba la
        // auditoría y la entidad asociada).
        Municipio actual = municipio.getIdMunicipio() == null ? null : municipioService.findById(municipio.getIdMunicipio());
        if (actual == null || !"ACTIVO".equals(actual.getEstado())) {
            return ResponseEntity.ok(Map.of("ok", false, "msg", "El municipio ya no existe o fue eliminado."));
        }
        String error = normalizarYValidar(municipio, actual.getIdMunicipio(), actual.getCodigo());
        if (error != null) return ResponseEntity.ok(Map.of("ok", false, "msg", error));
        boolean cambiaCodigo = !municipio.getCodigo().equalsIgnoreCase(actual.getCodigo() == null ? "" : actual.getCodigo().trim());
        long predios = municipioService.prediosPorMunicipio().getOrDefault(actual.getIdMunicipio(), 0L);
        if (cambiaCodigo && predios > 0) {
            return ResponseEntity.ok(Map.of("ok", false, "msg", "No se puede cambiar el código: " + predios
                    + " predio(s) lo usan como prefijo de sus activos. Solo se puede corregir el nombre."));
        }

        actual.setNombre(municipio.getNombre());
        actual.setCodigo(municipio.getCodigo());
        Usuario usuario = (Usuario) request.getSession().getAttribute("usuario");
        if (usuario != null) actual.setModificacionIdUsuario(usuario.getIdUsuario());
        municipioService.save(actual);
        return ResponseEntity.ok(Map.of("ok", true, "msg", "Municipio modificado correctamente"));
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/eliminar/{id_municipio}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> eliminar(HttpServletRequest request,
            @PathVariable("id_municipio") String idMunicipio) throws Exception {
        if (!RolesSciaf.esAdministrativo(request)) return soloAdmin();
        Long id = Long.parseLong(Encriptar.decrypt(idMunicipio));
        Municipio municipio = municipioService.findById(id);
        if (municipio == null) return ResponseEntity.ok(Map.of("ok", false, "msg", "El municipio no existe."));
        long predios = municipioService.prediosPorMunicipio().getOrDefault(id, 0L);
        if (predios > 0) {
            return ResponseEntity.ok(Map.of("ok", false, "msg", "No se puede eliminar: " + predios
                    + " predio(s) lo tienen asignado. Cámbieles el municipio en Predio primero."));
        }
        municipio.setEstado("ELIMINADO");
        Usuario usuario = (Usuario) request.getSession().getAttribute("usuario");
        if (usuario != null) municipio.setModificacionIdUsuario(usuario.getIdUsuario());
        municipioService.save(municipio);
        return ResponseEntity.ok(Map.of("ok", true, "msg", "Municipio eliminado"));
    }

    /**
     * MAYÚSCULAS y sin espacios sobrantes; nombre único entre los vigentes y código único entre
     * todos. El formato del código se exige cuando es nuevo o cambia ({@code codigoActual}): los
     * ya guardados se respetan para poder corregir el nombre. Devuelve el error o null.
     */
    private String normalizarYValidar(Municipio m, Long idPropio, String codigoActual) {
        m.setNombre(limpiar(m.getNombre()));
        m.setCodigo(limpiar(m.getCodigo()));
        if (m.getNombre() == null) return "Ingrese el nombre del municipio.";
        if (m.getCodigo() == null) return "Ingrese el código.";
        boolean codigoNuevo = codigoActual == null || !m.getCodigo().equalsIgnoreCase(codigoActual.trim());
        if (codigoNuevo && !CODIGO_VALIDO.matcher(m.getCodigo()).matches()) {
            return "El código solo puede tener letras y números (1 a 6), sin guiones, espacios ni símbolos: "
                    + "forma parte del código de los activos.";
        }
        for (Municipio otro : municipioService.conNombreOCodigo(m.getNombre(), m.getCodigo())) {
            if (otro.getIdMunicipio().equals(idPropio)) continue;
            boolean vigente = "ACTIVO".equals(otro.getEstado());
            if (vigente && m.getNombre().equalsIgnoreCase(otro.getNombre() == null ? "" : otro.getNombre().trim())) {
                return "Ya existe el municipio " + otro.getNombre() + ".";
            }
            if (m.getCodigo().equalsIgnoreCase(otro.getCodigo() == null ? "" : otro.getCodigo().trim())) {
                return "El código " + m.getCodigo() + " ya lo usa el municipio " + otro.getNombre()
                        + (vigente ? "." : " (eliminado): un código no se reutiliza porque los activos ya codificados lo llevan.");
            }
        }
        return null;
    }

    private static String limpiar(String s) {
        if (s == null) return null;
        String t = s.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
        return t.isEmpty() ? null : t;
    }
}
