package com.usic.SistemasActivosFijosUAP.model.service.supervision;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.usic.SistemasActivosFijosUAP.model.IService.IOficinaService;
import com.usic.SistemasActivosFijosUAP.model.IService.IPersonaService;
import com.usic.SistemasActivosFijosUAP.model.IService.IResponsableService;
import com.usic.SistemasActivosFijosUAP.model.dao.IResposableDao;
import com.usic.SistemasActivosFijosUAP.model.entity.Oficina;
import com.usic.SistemasActivosFijosUAP.model.entity.Persona;
import com.usic.SistemasActivosFijosUAP.model.entity.Responsable;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;
import com.usic.SistemasActivosFijosUAP.model.service.ResponsableAltaService;
import com.usic.SistemasActivosFijosUAP.model.service.VsiafApoyoService;

import lombok.RequiredArgsConstructor;

/**
 * Edición y baja de responsables. La usan el controlador (aplicación directa) y
 * {@link AutorizacionService} (al aprobar una solicitud), con las mismas reglas.
 */
@Service
@RequiredArgsConstructor
public class ResponsableGestionService implements EjecutorAutorizacion {

    public static final String TIPO_MODIFICAR = "RESP_MODIFICAR";
    public static final String TIPO_ELIMINAR = "RESP_ELIMINAR";

    private final IResponsableService responsableService;
    private final IPersonaService personaService;
    private final IOficinaService oficinaService;
    private final IResposableDao responsableDao;
    private final ResponsableAltaService responsableAltaService;
    private final VsiafApoyoService vsiafApoyoService;
    private final ActividadService actividadService;

    /** Datos del formulario de edición. */
    public record DatosResponsable(Long idResponsable, String ci, Short codExp, String codigoFuncionario,
                                   Long idOficina, String nombre, String paterno, String materno,
                                   String correo, String cargo, String codigoApi) {

        public Map<String, Object> aMapa() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("idResponsable", idResponsable);
            m.put("ci", ci);
            m.put("codExp", codExp);
            m.put("codigoFuncionario", codigoFuncionario);
            m.put("idOficina", idOficina);
            m.put("nombre", nombre);
            m.put("paterno", paterno);
            m.put("materno", materno);
            m.put("correo", correo);
            m.put("cargo", cargo);
            m.put("codigoApi", codigoApi);
            return m;
        }

        public static DatosResponsable deMapa(Map<String, Object> m) {
            return new DatosResponsable(OficinaGestionService.aLong(m.get("idResponsable")), (String) m.get("ci"),
                    m.get("codExp") == null ? null : Short.valueOf(m.get("codExp").toString()),
                    (String) m.get("codigoFuncionario"), OficinaGestionService.aLong(m.get("idOficina")),
                    (String) m.get("nombre"), (String) m.get("paterno"), (String) m.get("materno"),
                    (String) m.get("correo"), (String) m.get("cargo"), (String) m.get("codigoApi"));
        }
    }

    public record Resultado(boolean vsiafOk, String msg) {}

    public Responsable buscar(Long id) {
        Responsable r = (id != null) ? responsableService.findByIdWithRelations(id) : null;
        if (r == null) throw new IllegalArgumentException("Responsable no encontrado.");
        return r;
    }

    /** ¿El cambio toca la clave en el VSIAF (oficina o código)? Eso requiere autorización. */
    public boolean cambiaClave(Responsable r, DatosResponsable d) {
        Long idOficinaActual = r.getOficina() != null ? r.getOficina().getIdOficina() : null;
        return !Objects.equals(d.idOficina(), idOficinaActual)
                || !Objects.equals(VsiafApoyoService.codResp(ResponsableAltaService.limpiar(d.codigoFuncionario())),
                                   VsiafApoyoService.codResp(r.getCodigoFuncionario()));
    }

    /** Valida una edición sin aplicarla (se usa antes de crear la solicitud). */
    public void validarModificacion(DatosResponsable d) {
        Responsable r = buscar(d.idResponsable());
        String codFun = ResponsableAltaService.limpiar(d.codigoFuncionario());
        String cargo = cargoEfectivo(r, d);
        responsableAltaService.validar(ResponsableAltaService.limpiar(d.ci()), codFun, cargo);
        if (ResponsableAltaService.mayus(d.nombre()) == null || ResponsableAltaService.mayus(d.paterno()) == null) {
            throw new IllegalArgumentException("Nombres y apellido paterno son obligatorios.");
        }
        Oficina nueva = d.idOficina() != null ? oficinaService.findById(d.idOficina()) : null;
        if (nueva == null || !"ACTIVO".equals(nueva.getEstado())) {
            throw new IllegalArgumentException("La oficina indicada no existe.");
        }
        if (cambiaClave(r, d)) {
            // En ACTUAL.DBF los bienes apuntan al responsable por CODOFIC+CODRESP.
            long activos = responsableDao.contarActivosAsignados(r.getIdResponsable());
            if (activos > 0) {
                throw new IllegalArgumentException(String.format(
                    "Este responsable tiene %d activo(s) a cargo: no se puede cambiar su oficina ni su código. "
                    + "Para moverlo use una transferencia o registre un responsable nuevo.", activos));
            }
            Responsable duplicado = responsableService.findByCodigoFuncionarioYOficina(codFun, nueva.getIdOficina());
            if (duplicado != null && !duplicado.getIdResponsable().equals(r.getIdResponsable())) {
                throw new IllegalArgumentException(String.format(
                    "Ya existe un responsable con código %s en la oficina %s.", codFun, nueva.getNombre()));
            }
        }
    }

    public String describirCambios(Responsable r, DatosResponsable d) {
        List<String> c = new ArrayList<>();
        Persona p = r.getPersona();
        String nombreAntes = p != null ? p.getNombreCompleto() : "";
        String nombreDespues = String.join(" ", nvl(ResponsableAltaService.mayus(d.nombre())),
                nvl(ResponsableAltaService.mayus(d.paterno())), nvl(ResponsableAltaService.mayus(d.materno()))).trim();
        if (!Objects.equals(nombreAntes != null ? nombreAntes.trim() : "", nombreDespues)) {
            c.add("Nombre: «" + nombreAntes + "» → «" + nombreDespues + "»");
        }
        if (p != null && !Objects.equals(p.getCi(), ResponsableAltaService.limpiar(d.ci()))) {
            c.add("C.I.: " + p.getCi() + " → " + d.ci());
        }
        Long idOficinaActual = r.getOficina() != null ? r.getOficina().getIdOficina() : null;
        if (!Objects.equals(idOficinaActual, d.idOficina())) {
            Oficina nueva = d.idOficina() != null ? oficinaService.findById(d.idOficina()) : null;
            c.add("Oficina: " + (r.getOficina() != null ? OficinaGestionService.referencia(r.getOficina()) : "—")
                    + " → " + (nueva != null ? OficinaGestionService.referencia(nueva) : "—"));
        }
        if (!Objects.equals(r.getCodigoFuncionario(), ResponsableAltaService.limpiar(d.codigoFuncionario()))) {
            c.add("Código: " + r.getCodigoFuncionario() + " → " + d.codigoFuncionario());
        }
        String cargoAntes = r.getCargo() != null ? r.getCargo().getNombre() : null;
        String cargoDespues = cargoEfectivo(r, d);
        if (!Objects.equals(cargoAntes, cargoDespues)) c.add("Cargo: " + cargoAntes + " → " + cargoDespues);
        return c.isEmpty() ? "Sin cambios de datos" : String.join("; ", c);
    }

    /** Aplica la edición y la envía al VSIAF. */
    public Resultado modificar(DatosResponsable d, Usuario autor) {
        validarModificacion(d);
        Responsable r = buscar(d.idResponsable());
        String cambios = describirCambios(r, d);
        String usuarioNombre = autor != null ? autor.getUsuario() : "SISTEMA";
        Oficina nuevaOficina = oficinaService.findById(d.idOficina());

        // Clave con la que está hoy en RESP.DBF: el UPDATE la ubica por ahí.
        VsiafApoyoService.ClaveResponsable claveOriginal = vsiafApoyoService.claveDe(r);
        boolean necesitaAlta = vsiafApoyoService.necesitaAlta(
                VsiafApoyoService.TABLA_RESP, r.getIdResponsable(), r.isPendienteDbf());

        Persona persona = r.getPersona();
        persona.setNombre(ResponsableAltaService.mayus(d.nombre()));
        persona.setPaterno(ResponsableAltaService.mayus(d.paterno()));
        persona.setMaterno(ResponsableAltaService.mayus(d.materno()));
        persona.setCi(ResponsableAltaService.limpiar(d.ci()));
        persona.setCorreo(ResponsableAltaService.limpiar(d.correo()));
        personaService.save(persona);

        r.setOficina(nuevaOficina);
        r.setCargo(responsableAltaService.obtenerCargo(cargoEfectivo(r, d), autor));
        r.setCodigoFuncionario(ResponsableAltaService.limpiar(d.codigoFuncionario()));
        r.setCodigoApi(ResponsableAltaService.limpiar(d.codigoApi()));
        r.setCodExp(d.codExp() != null ? d.codExp() : Short.valueOf("9"));
        r.setFechaUlt(LocalDate.now());
        r.setUsuario(usuarioNombre);
        if (autor != null) r.setModificacionIdUsuario(autor.getIdUsuario());
        responsableService.save(r);

        Responsable cargado = buscar(r.getIdResponsable());
        // Si nunca llegó al VSIAF (o el worker rechazó el alta), no hay fila que actualizar.
        VsiafApoyoService.Envio envio = necesitaAlta
                ? vsiafApoyoService.reenviarResponsable(cargado, usuarioNombre)
                : vsiafApoyoService.actualizarResponsable(cargado, claveOriginal, usuarioNombre);

        actividadService.registrar(autor, ActividadService.MOD_RESPONSABLE, ActividadService.ACC_MODIFICACION,
                referencia(cargado), "Modificó al responsable " + referencia(cargado) + ": " + cambios,
                cargado.getIdResponsable());
        return new Resultado(envio.ok(), "Responsable modificado. " + envio.mensaje());
    }

    /** Baja lógica en el SCIAF, solo sin bienes a cargo (el VSIAF no borra responsables). */
    public Resultado eliminar(Long id, Usuario autor) {
        Responsable r = buscar(id);
        String dep = dependenciasQueImpidenEliminar(r);
        if (dep != null) throw new IllegalArgumentException(dep);
        r.setEstado("ELIMINADO");
        responsableService.save(r);
        actividadService.registrar(autor, ActividadService.MOD_RESPONSABLE, ActividadService.ACC_ELIMINACION,
                referencia(r), "Eliminó al responsable " + referencia(r), r.getIdResponsable());
        return new Resultado(true, "Responsable eliminado del SCIAF.");
    }

    public String dependenciasQueImpidenEliminar(Responsable r) {
        long activos = responsableDao.contarActivosAsignados(r.getIdResponsable());
        return activos > 0
                ? String.format("No se puede eliminar: tiene %d activo(s) a cargo. Transfiéralos primero.", activos)
                : null;
    }

    /** "CAUN-5 / 3 JUAN PEREZ": oficina, código de funcionario y nombre. */
    public static String referencia(Responsable r) {
        String ofi = (r.getOficina() != null && r.getOficina().getPredio() != null)
                ? r.getOficina().getPredio().getUnidad() + "-" + r.getOficina().getCodOfi() : "?";
        String nombre = r.getPersona() != null ? r.getPersona().getNombreCompleto() : "";
        return ofi + " / " + r.getCodigoFuncionario() + " " + nvl(nombre);
    }

    private String cargoEfectivo(Responsable r, DatosResponsable d) {
        String cargo = ResponsableAltaService.mayus(d.cargo());
        if (cargo == null && r.getCargo() != null) cargo = r.getCargo().getNombre();
        return cargo;
    }

    private static String nvl(String s) {
        return s == null ? "" : s;
    }

    // ── Autorizaciones ──────────────────────────────────────────────────────

    @Override
    public Set<String> tipos() {
        return Set.of(TIPO_MODIFICAR, TIPO_ELIMINAR);
    }

    @Override
    public String ejecutar(String tipo, Map<String, Object> datos, Usuario autor) {
        if (TIPO_MODIFICAR.equals(tipo)) return modificar(DatosResponsable.deMapa(datos), autor).msg();
        if (TIPO_ELIMINAR.equals(tipo)) return eliminar(OficinaGestionService.aLong(datos.get("idResponsable")), autor).msg();
        throw new IllegalArgumentException("Tipo no soportado: " + tipo);
    }
}
