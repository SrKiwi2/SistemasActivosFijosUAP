package com.usic.SistemasActivosFijosUAP.model.service.supervision;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.usic.SistemasActivosFijosUAP.model.IService.IOficinaService;
import com.usic.SistemasActivosFijosUAP.model.IService.IPredioServicio;
import com.usic.SistemasActivosFijosUAP.model.dao.IOficinaDao;
import com.usic.SistemasActivosFijosUAP.model.entity.Oficina;
import com.usic.SistemasActivosFijosUAP.model.entity.Predio;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;
import com.usic.SistemasActivosFijosUAP.model.service.VsiafApoyoService;

import lombok.RequiredArgsConstructor;

/**
 * Edición y baja de oficinas. La usan el controlador (cuando quien edita puede aplicarlo
 * directo) y {@link AutorizacionService} (cuando se aprueba la solicitud de alguien que
 * no puede): así las dos vías aplican exactamente las mismas reglas.
 */
@Service
@RequiredArgsConstructor
public class OficinaGestionService implements EjecutorAutorizacion {

    public static final String TIPO_MODIFICAR = "OFICINA_MODIFICAR";
    public static final String TIPO_ELIMINAR = "OFICINA_ELIMINAR";

    /** Largo de NOMOFIC en OFICINA.DBF: un nombre más largo el VSIAF lo recorta. */
    public static final int MAX_NOMBRE_OFICINA = 65;

    private final IOficinaService oficinaService;
    private final IPredioServicio predioServicio;
    private final IOficinaDao oficinaDao;
    private final VsiafApoyoService vsiafApoyoService;
    private final ActividadService actividadService;

    /** Datos del formulario de edición. */
    public record DatosOficina(Long idOficina, Long idPredio, Short codOfi, String nombre, String observ) {

        public Map<String, Object> aMapa() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("idOficina", idOficina);
            m.put("idPredio", idPredio);
            m.put("codOfi", codOfi);
            m.put("nombre", nombre);
            m.put("observ", observ);
            return m;
        }

        public static DatosOficina deMapa(Map<String, Object> m) {
            return new DatosOficina(aLong(m.get("idOficina")), aLong(m.get("idPredio")),
                    m.get("codOfi") == null ? null : Short.valueOf(m.get("codOfi").toString()),
                    (String) m.get("nombre"), (String) m.get("observ"));
        }
    }

    public record Resultado(boolean vsiafOk, String msg) {}

    public Oficina buscar(Long id) {
        Oficina o = (id != null) ? oficinaService.findById(id) : null;
        if (o == null || o.getPredio() == null || o.getPredio().getEntidad() == null) {
            throw new IllegalArgumentException("No se encontró la oficina, o no tiene predio/entidad asociados.");
        }
        return o;
    }

    /** ¿El cambio toca la clave en el VSIAF (predio o código)? Eso requiere autorización. */
    public boolean cambiaClave(Oficina o, DatosOficina d) {
        return !Objects.equals(d.idPredio(), o.getPredio().getIdPredio())
                || !Objects.equals(d.codOfi(), o.getCodOfi());
    }

    /**
     * Reglas del alta y la edición. Devuelve el mensaje para el usuario, o null si está bien.
     *
     * @param idPropio id de la oficina que se edita (null en el alta), para no chocar consigo misma
     */
    public String validar(Predio predio, Short codOfi, String nombre, Long idPropio) {
        if (predio == null) return "Debe seleccionar un predio válido.";
        if (predio.getEntidad() == null || predio.getUnidad() == null || predio.getUnidad().isBlank()) {
            return "El predio seleccionado no tiene entidad o unidad: no se puede registrar en el VSIAF.";
        }
        if (codOfi == null || codOfi < 1) return "El código de oficina debe ser un número mayor a 0.";
        if (nombre == null || nombre.isBlank()) return "El nombre de la oficina es obligatorio.";
        if (nombre.trim().length() > MAX_NOMBRE_OFICINA) {
            return "El nombre no puede tener más de " + MAX_NOMBRE_OFICINA
                    + " caracteres (es el largo del campo en el VSIAF).";
        }
        Optional<Oficina> mismaClave = oficinaService.findByPredioAndCodOfi(predio, codOfi);
        if (mismaClave.isPresent() && !mismaClave.get().getIdOficina().equals(idPropio)) {
            Oficina otra = mismaClave.get();
            return String.format("Ya existe la oficina %d en el predio %s: \"%s\"%s. Use el siguiente código libre.",
                    codOfi, predio.getUnidad(), otra.getNombre(),
                    "ELIMINADO".equals(otra.getEstado()) ? " (eliminada)" : "");
        }
        return null;
    }

    /**
     * RESP.DBF y ACTUAL.DBF apuntan a la oficina por ENTIDAD+UNIDAD+CODOFIC: con
     * responsables o bienes, esa clave no se cambia (ni con autorización).
     *
     * @return mensaje si hay dependencias, null si no
     */
    public String dependenciasQueImpidenClave(Oficina o) {
        long responsables = oficinaDao.contarResponsablesVigentes(o.getIdOficina());
        long activos = oficinaDao.contarActivos(o.getIdOficina());
        if (responsables + activos == 0) return null;
        return String.format("La oficina tiene %d responsable(s) y %d activo(s): no se puede cambiar su predio ni su código "
                + "(quedarían apuntando a una oficina inexistente en el VSIAF). Solo se pueden editar nombre y observaciones.",
                responsables, activos);
    }

    public String dependenciasQueImpidenEliminar(Oficina o) {
        long responsables = oficinaDao.contarResponsablesVigentes(o.getIdOficina());
        long activos = oficinaDao.contarActivos(o.getIdOficina());
        if (responsables + activos == 0) return null;
        return String.format("No se puede eliminar: tiene %d responsable(s) y %d activo(s).", responsables, activos);
    }

    /** Valida una edición sin aplicarla (se usa antes de crear la solicitud). */
    public void validarModificacion(DatosOficina d) {
        Oficina original = buscar(d.idOficina());
        Predio predioNuevo = d.idPredio() != null ? predioServicio.findById(d.idPredio()) : null;
        String invalido = validar(predioNuevo, d.codOfi(), d.nombre(), original.getIdOficina());
        if (invalido != null) throw new IllegalArgumentException(invalido);
        if (cambiaClave(original, d)) {
            String dep = dependenciasQueImpidenClave(original);
            if (dep != null) throw new IllegalArgumentException(dep);
        }
    }

    /** Texto "antes → después" de lo que cambia, para la solicitud y el monitoreo. */
    public String describirCambios(Oficina o, DatosOficina d) {
        List<String> c = new ArrayList<>();
        Predio predioNuevo = d.idPredio() != null ? predioServicio.findById(d.idPredio()) : null;
        if (predioNuevo != null && !Objects.equals(predioNuevo.getIdPredio(), o.getPredio().getIdPredio())) {
            c.add("Predio: " + o.getPredio().getUnidad() + " → " + predioNuevo.getUnidad());
        }
        if (!Objects.equals(d.codOfi(), o.getCodOfi())) c.add("Código: " + o.getCodOfi() + " → " + d.codOfi());
        String nombre = d.nombre() != null ? d.nombre().trim() : null;
        if (!Objects.equals(nombre, o.getNombre())) c.add("Nombre: «" + o.getNombre() + "» → «" + nombre + "»");
        String obs = (d.observ() != null && !d.observ().isBlank()) ? d.observ().trim() : null;
        if (!Objects.equals(obs, o.getObserv())) c.add("Observaciones modificadas");
        return c.isEmpty() ? "Sin cambios de datos" : String.join("; ", c);
    }

    /** Aplica la edición y la envía al VSIAF. */
    public Resultado modificar(DatosOficina d, Usuario autor) {
        validarModificacion(d);
        Oficina original = buscar(d.idOficina());
        Predio predioNuevo = predioServicio.findById(d.idPredio());
        String cambios = describirCambios(original, d);
        String usuarioNombre = autor != null ? autor.getUsuario() : "SISTEMA";

        // Clave con la que está hoy en OFICINA.DBF — ANTES de aplicar el cambio.
        VsiafApoyoService.ClaveOficina claveOriginal = vsiafApoyoService.claveDe(original);
        boolean necesitaAlta = vsiafApoyoService.necesitaAlta(
                VsiafApoyoService.TABLA_OFICINA, original.getIdOficina(), original.isPendienteDbf());

        original.setPredio(predioNuevo);
        original.setCodOfi(d.codOfi());
        original.setNombre(d.nombre().trim());
        original.setObserv(d.observ() != null && !d.observ().isBlank() ? d.observ().trim() : null);
        original.setFechaUlt(LocalDate.now());
        original.setModificacion(new Date());
        original.setUsuario(usuarioNombre);
        if (autor != null) original.setModificacionIdUsuario(autor.getIdUsuario());
        original.setEstado("ACTIVO");
        oficinaService.save(original);

        // Si nunca llegó al VSIAF (o el worker rechazó el alta), no hay fila que actualizar.
        VsiafApoyoService.Envio envio = necesitaAlta
                ? vsiafApoyoService.reenviarOficina(original, usuarioNombre)
                : vsiafApoyoService.actualizarOficina(original, claveOriginal, usuarioNombre);

        actividadService.registrar(autor, ActividadService.MOD_OFICINA, ActividadService.ACC_MODIFICACION,
                referencia(original), "Modificó la oficina " + referencia(original) + ": " + cambios,
                original.getIdOficina());
        return new Resultado(envio.ok(), "Oficina modificada. " + envio.mensaje());
    }

    /** Baja lógica en el SCIAF (en el VSIAF la fila de OFICINA.DBF sigue existiendo). */
    public Resultado eliminar(Long id, Usuario autor) {
        Oficina o = buscar(id);
        String dep = dependenciasQueImpidenEliminar(o);
        if (dep != null) throw new IllegalArgumentException(dep);
        o.setEstado("ELIMINADO");
        oficinaService.save(o);
        actividadService.registrar(autor, ActividadService.MOD_OFICINA, ActividadService.ACC_ELIMINACION,
                referencia(o), "Eliminó la oficina " + referencia(o), o.getIdOficina());
        return new Resultado(true, "Oficina eliminada del SCIAF.");
    }

    /** "CAUN-5 SISTEMAS": unidad, código y nombre, lo que se busca en el módulo. */
    public static String referencia(Oficina o) {
        String unidad = (o.getPredio() != null && o.getPredio().getUnidad() != null) ? o.getPredio().getUnidad() : "?";
        return unidad + "-" + o.getCodOfi() + " " + (o.getNombre() != null ? o.getNombre() : "");
    }

    // ── Autorizaciones ──────────────────────────────────────────────────────

    @Override
    public Set<String> tipos() {
        return Set.of(TIPO_MODIFICAR, TIPO_ELIMINAR);
    }

    @Override
    public String ejecutar(String tipo, Map<String, Object> datos, Usuario autor) {
        if (TIPO_MODIFICAR.equals(tipo)) return modificar(DatosOficina.deMapa(datos), autor).msg();
        if (TIPO_ELIMINAR.equals(tipo)) return eliminar(aLong(datos.get("idOficina")), autor).msg();
        throw new IllegalArgumentException("Tipo no soportado: " + tipo);
    }

    static Long aLong(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        String s = v.toString().trim();
        return s.isEmpty() ? null : Long.valueOf(s);
    }
}
