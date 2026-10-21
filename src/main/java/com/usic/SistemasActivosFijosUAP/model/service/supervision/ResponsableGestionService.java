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
import com.usic.SistemasActivosFijosUAP.model.service.personas.IdentidadPersona;
import com.usic.SistemasActivosFijosUAP.model.service.personas.PersonaHistorialService;
import com.usic.SistemasActivosFijosUAP.model.dao.IParametroSistemaDao;
import com.usic.SistemasActivosFijosUAP.model.dao.IPersonasDao;
import com.usic.SistemasActivosFijosUAP.model.entity.Cargo;
import com.usic.SistemasActivosFijosUAP.model.entity.PersonaCambio;
import com.usic.SistemasActivosFijosUAP.model.entity.ParametroSistema;

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
    public static final String TIPO_ESTADO = "RESP_ESTADO";

    private final IResponsableService responsableService;
    private final IPersonaService personaService;
    private final IOficinaService oficinaService;
    private final IResposableDao responsableDao;
    private final ResponsableAltaService responsableAltaService;
    private final VsiafApoyoService vsiafApoyoService;
    private final ActividadService actividadService;
    private final IParametroSistemaDao parametroDao;
    private final PersonaHistorialService personaHistorial;
    private final IPersonasDao personasDao;

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

    /**
     * Resultado de reenviar al VSIAF el nombre / C.I. de una persona en las filas de RESP.DBF
     * que la usan.
     * <ul>
     *   <li>{@code pendientes}: responsables que todavía no llegaron al VSIAF (o el worker los
     *       rechazó); no se tocan, porque al reenviarlos ya viajan con el dato nuevo.</li>
     *   <li>{@code omitidos}: no se envió nada porque la persona no tenía un C.I. válido (ver
     *       {@link #esIdentificable}): puede ser un registro que la sincronización comparte
     *       entre varios funcionarios distintos del VSIAF.</li>
     * </ul>
     */
    public record Propagacion(int enviados, List<String> fallidos, int pendientes, int omitidos) {
        static final Propagacion VACIA = new Propagacion(0, List.of(), 0, 0);

        public boolean nada() { return enviados == 0 && fallidos.isEmpty() && pendientes == 0 && omitidos == 0; }
        public boolean conProblemas() { return !fallidos.isEmpty() || omitidos > 0; }

        public String mensaje() {
            if (nada()) return "";
            List<String> s = new ArrayList<>();
            if (enviados > 0) {
                s.add(enviados == 1 ? "Se envió el cambio al VSIAF (1 responsable)."
                                    : "Se envió el cambio al VSIAF (" + enviados + " responsables).");
            }
            if (!fallidos.isEmpty()) {
                s.add("NO se pudo enviar al VSIAF: " + String.join("; ", fallidos)
                        + ". Quedaron pendientes; se reenvían desde Responsables.");
            }
            if (pendientes > 0) {
                s.add(pendientes + " responsable(s) de esta persona todavía no están en el VSIAF: "
                        + "llevarán el dato nuevo cuando se reenvíen desde Responsables.");
            }
            if (omitidos > 0) {
                s.add("NO se envió a " + omitidos + " responsable(s) de esta persona en el VSIAF: no tenía un C.I. "
                        + "válido y ese registro puede estar compartido por funcionarios distintos. "
                        + "Revíselos uno por uno en Responsables.");
            }
            return String.join(" ", s);
        }
    }

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
        // Una persona por C.I.: si se le pone el C.I. de otra, la fila pasa a ella con sus datos.
        Persona duena = duenaDelCiNuevo(r, d);
        if (duena != null && !IdentidadPersona.nombresEquivalentes(nombreNormal(duena), nombreNormal(d))) {
            throw new IllegalArgumentException("El C.I. " + duena.getCi() + " ya es de " + duena.getNombreCompleto()
                    + ". Si es la misma persona, use «Cargar sus datos»; si no, revise el C.I.");
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
            Persona duena = duenaDelCiNuevo(r, d);
            if (duena != null) c.add("La oficina pasa a " + duena.getNombreCompleto() + " (ya tiene ese C.I.)");
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
        Persona personaAntes = persona;
        String cargoAntes = r.getCargo() != null ? r.getCargo().getNombre() : null;
        Persona duena = duenaDelCiNuevo(r, d);
        boolean pasaAOtra = duena != null;
        if (pasaAOtra) {
            // Se le puso el C.I. de otra persona (validarModificacion comprobó que el nombre
            // cuadra): la fila pasa a ella en vez de crear un segundo registro con el mismo C.I.
            // Los datos de esa persona no se tocan desde aquí. La que deja tampoco; si se queda
            // sin oficinas, «Personas y cargos» la lista para la baja.
            persona = duena;
        } else if (!esIdentificable(persona) && tieneOtrosResponsables(persona, r)
                && !Objects.equals(nombreYCi(persona), nombreYCi(d))) {
            // Registro "comodín" de la sincronización (sin C.I. válido, compartido con otros
            // responsables): corregirlo aquí cambiaría también a los demás funcionarios que
            // cuelgan de él. Este responsable pasa a su propia persona, nueva.
            persona = new Persona();
            persona.setEstado("ACTIVO");
        }
        String nombreYCiAntes = nombreYCi(persona);
        boolean identificableAntes = esIdentificable(persona);
        Map<String, Object> fotoAntes = persona.getIdPersona() != null ? PersonaHistorialService.foto(persona) : null;
        if (!pasaAOtra) {
            persona.setNombre(ResponsableAltaService.mayus(d.nombre()));
            persona.setPaterno(ResponsableAltaService.mayus(d.paterno()));
            persona.setMaterno(ResponsableAltaService.mayus(d.materno()));
            persona.setCi(ResponsableAltaService.limpiar(d.ci()));
            persona.setCorreo(ResponsableAltaService.limpiar(d.correo()));
            persona = personaService.save(persona);
        }
        r.setPersona(persona);
        if (fotoAntes != null && !Objects.equals(nombreYCiAntes, nombreYCi(persona))) {
            personaHistorial.registrarDatos(fotoAntes, persona, autor, PersonaHistorialService.ORIGEN_RESPONSABLE);
        }

        r.setOficina(nuevaOficina);
        r.setCargo(responsableAltaService.obtenerCargo(cargoEfectivo(r, d), autor));
        r.setCodigoFuncionario(ResponsableAltaService.limpiar(d.codigoFuncionario()));
        r.setCodigoApi(ResponsableAltaService.limpiar(d.codigoApi()));
        r.setCodExp(d.codExp() != null ? d.codExp() : Short.valueOf("9"));
        r.setFechaUlt(LocalDate.now());
        r.setUsuario(usuarioNombre);
        if (autor != null) r.setModificacionIdUsuario(autor.getIdUsuario());
        responsableService.save(r);
        if (personaAntes != null && !personaAntes.getIdPersona().equals(persona.getIdPersona())) {
            personaHistorial.registrarPaseDeFila(r.getIdResponsable(), referencia(r), personaAntes, persona,
                    r.getCargo(), autor, PersonaHistorialService.ORIGEN_RESPONSABLE);
        }

        Responsable cargado = buscar(r.getIdResponsable());
        // Si nunca llegó al VSIAF (o el worker rechazó el alta), no hay fila que actualizar.
        VsiafApoyoService.Envio envio = necesitaAlta
                ? vsiafApoyoService.reenviarResponsable(cargado, usuarioNombre)
                : vsiafApoyoService.actualizarResponsable(cargado, claveOriginal, usuarioNombre);

        actividadService.registrar(autor, ActividadService.MOD_RESPONSABLE, ActividadService.ACC_MODIFICACION,
                referencia(cargado), "Modificó al responsable " + referencia(cargado) + ": " + cambios,
                cargado.getIdResponsable());

        // La misma persona puede ser responsable en otras oficinas: allá también cambian el
        // nombre / C.I. y el cargo (una persona, un cargo: fase 8 del plan de personas).
        Cargo cargoNuevo = cargado.getCargo();
        // Al pasar a otra persona, el cargo de esta oficina queda como el de la persona.
        boolean cambioCargo = cargoNuevo != null && (pasaAOtra || !Objects.equals(cargoAntes, cargoNuevo.getNombre()));
        boolean cambioDatos = !Objects.equals(nombreYCiAntes, nombreYCi(persona));
        Propagacion otras = !cambioDatos && !cambioCargo
                ? Propagacion.VACIA
                : propagarPersona(persona.getIdPersona(), cargado.getIdResponsable(), autor,
                        identificableAntes || esIdentificable(persona), cambioCargo ? cargoNuevo : null, cambioDatos);
        String extra = otras.nada() ? "" : " Otras oficinas de la misma persona: " + otras.mensaje();
        return new Resultado(envio.ok() && !otras.conProblemas(), "Responsable modificado. " + envio.mensaje() + extra);
    }

    /**
     * El VSIAF no tiene una tabla de personas: el nombre (NOMRESP) y el C.I. van copiados en
     * cada fila de RESP.DBF. Si se corrige la persona —desde Personas o desde otro
     * responsable suyo—, hay que mandar el UPDATE a cada responsable activo que la usa.
     * <p>
     * Solo UPDATE de los que ya están en el VSIAF: la clave (oficina + código) no cambia, así
     * que la fila se ubica igual. Los pendientes o rechazados no se tocan: su reenvío lee el
     * dato vigente de la base.
     *
     * <b>Solo si la persona era identificable por su C.I. ANTES del cambio.</b> La sincronización
     * desde RESP.DBF junta en una sola Persona las filas sin C.I. ("SIN DATOS", o el mismo
     * nombre): ahí "misma persona" no quiere decir "mismo funcionario", y propagar escribiría el
     * nombre y C.I. de uno en las filas de otros. En ese caso no se envía nada y se avisa.
     *
     * @param excluirIdResponsable el que ya se envió por su cuenta (puede ser null)
     * @param identificableAntes   {@link #esIdentificable} de la persona antes de editarla
     */
    public Propagacion propagarPersona(Long idPersona, Long excluirIdResponsable, Usuario autor,
                                       boolean identificableAntes) {
        return propagarPersona(idPersona, excluirIdResponsable, autor, identificableAntes, null, true);
    }

    /**
     * @param cargoNuevo   si no es null, también se pone este cargo en las otras oficinas (una
     *                     persona, un cargo) y queda en el historial de la persona.
     * @param cambioDatos  cambió el nombre / C.I.: se reenvían todas. Si no, solo las que
     *                     cambian de cargo (las demás ya dicen lo mismo en el VSIAF).
     * <p>
     * Las filas de la persona que en el VSIAF no tienen C.I. también reciben el dato: si se
     * saltaran, la sync las vería con el nombre viejo y las separaría en otra persona.
     */
    public Propagacion propagarPersona(Long idPersona, Long excluirIdResponsable, Usuario autor,
                                       boolean identificableAntes, Cargo cargoNuevo, boolean cambioDatos) {
        if (idPersona == null) return Propagacion.VACIA;
        String usuarioNombre = autor != null ? autor.getUsuario() : "SISTEMA";
        int enviados = 0, pendientes = 0, omitidos = 0;
        List<String> fallidos = new ArrayList<>();
        String lote = PersonaHistorialService.nuevoLote();

        for (Responsable base : responsableDao.findByPersonaIdPersona(idPersona)) {
            if (!"ACTIVO".equals(base.getEstado()) || base.getIdResponsable().equals(excluirIdResponsable)) continue;
            if (!identificableAntes) {
                omitidos++;
                continue;
            }
            Responsable r = buscar(base.getIdResponsable());
            boolean cambiaCargo = cargoNuevo != null
                    && (r.getCargo() == null || !cargoNuevo.getIdCargo().equals(r.getCargo().getIdCargo()));
            if (!cambioDatos && !cambiaCargo) continue;
            if (cambiaCargo) {
                String cargoAntes = r.getCargo() != null ? r.getCargo().getNombre() : null;
                Long idCargoAntes = r.getCargo() != null ? r.getCargo().getIdCargo() : null;
                r.setCargo(cargoNuevo);
                r.setFechaUlt(LocalDate.now());
                r.setUsuario(usuarioNombre);
                if (autor != null) r.setModificacionIdUsuario(autor.getIdUsuario());
                responsableService.save(r);
                personaHistorial.registrarCargoDeFila(lote, r.getIdResponsable(), referencia(r), r.getPersona(),
                        idCargoAntes, cargoAntes, cargoNuevo, autor, PersonaHistorialService.ORIGEN_RESPONSABLE);
            }
            if (vsiafApoyoService.necesitaAlta(VsiafApoyoService.TABLA_RESP, r.getIdResponsable(), r.isPendienteDbf())) {
                pendientes++;
                continue;
            }
            VsiafApoyoService.Envio envio = vsiafApoyoService.actualizarResponsable(r, vsiafApoyoService.claveDe(r), usuarioNombre);
            if (envio.ok()) enviados++; else fallidos.add(referencia(r));
            actividadService.registrar(autor, ActividadService.MOD_RESPONSABLE, ActividadService.ACC_MODIFICACION,
                    referencia(r), "Envió al VSIAF los datos corregidos de la persona en " + referencia(r)
                    + (cargoNuevo != null ? " (cargo " + cargoNuevo.getNombre() + ")" : "")
                    + (envio.ok() ? "" : " (falló: " + envio.mensaje() + ")"), r.getIdResponsable());
        }
        return new Propagacion(enviados, fallidos, pendientes, omitidos);
    }

    /**
     * Personas ACTIVAS con ese C.I. (se compara el número, sin extensión ni complemento),
     * la más antigua primero. Las marcas del VSIAF (DEVOLUCION, SIN DATOS…) no cuentan.
     */
    public List<Persona> personasConCi(String ci) {
        Set<String> inv = ciInvalidos();
        String numero = IdentidadPersona.numeroCi(ci, inv);
        if (numero == null) return List.of();
        return personasDao.conCiQueEmpiezaCon(numero).stream()
                .filter(p -> numero.equals(IdentidadPersona.numeroCi(p.getCi(), inv)))
                .filter(p -> !IdentidadPersona.esNombreEspecial(nombreNormal(p)))
                .toList();
    }

    /**
     * Si la edición le cambia el número de C.I. a la fila y ese número ya es de otra persona,
     * esa persona. Null si el C.I. no cambió (editar un cargo nunca mueve la fila) o nadie lo tiene.
     */
    private Persona duenaDelCiNuevo(Responsable r, DatosResponsable d) {
        Set<String> inv = ciInvalidos();
        Persona actual = r.getPersona();
        String antes = actual == null ? null : IdentidadPersona.numeroCi(actual.getCi(), inv);
        String despues = IdentidadPersona.numeroCi(d.ci(), inv);
        if (despues == null || despues.equals(antes)) return null;
        Persona duena = duenaDelCi(d.ci());
        if (duena == null || (actual != null && duena.getIdPersona().equals(actual.getIdPersona()))) return null;
        return duena;
    }

    /** La persona dueña de ese C.I. (con varias, la más antigua: el resto se unifica en el módulo). */
    public Persona duenaDelCi(String ci) {
        List<Persona> l = personasConCi(ci);
        return l.isEmpty() ? null : l.get(0);
    }

    /**
     * Para «Modificar responsable»: quién tiene ese C.I., su cargo y sus oficinas, para
     * ofrecer cargar sus datos. Null si nadie lo tiene.
     */
    public Map<String, Object> fichaPorCi(String ci, Long idResponsable) {
        List<Persona> personas = personasConCi(ci);
        if (personas.isEmpty()) return null;
        Persona p = personas.get(0);
        List<Responsable> filas = responsableDao.findByPersonaIdPersona(p.getIdPersona()).stream()
                .filter(x -> "ACTIVO".equals(x.getEstado())).toList();
        Map<String, Integer> cargos = new LinkedHashMap<>();
        for (Responsable x : filas) {
            if (x.getCargo() != null) cargos.merge(x.getCargo().getNombre(), 1, Integer::sum);
        }
        String cargo = cargos.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse("");
        Long idPersonaDeLaFila = null;
        if (idResponsable != null) {
            Responsable r = responsableDao.findById(idResponsable).orElse(null);
            if (r != null && r.getPersona() != null) idPersonaDeLaFila = r.getPersona().getIdPersona();
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("idPersona", p.getIdPersona());
        m.put("nombre", nvl(p.getNombre()));
        m.put("paterno", nvl(p.getPaterno()));
        m.put("materno", nvl(p.getMaterno()));
        m.put("ci", nvl(p.getCi()));
        m.put("nombreCompleto", nvl(p.getNombreCompleto()));
        m.put("cargo", cargo);
        m.put("cargosDistintos", cargos.size());
        m.put("oficinas", filas.stream().limit(8).map(ResponsableGestionService::referencia).toList());
        m.put("nOficinas", filas.size());
        m.put("esLaMisma", p.getIdPersona().equals(idPersonaDeLaFila));
        m.put("otrasConEseCi", personas.size() - 1);
        return m;
    }

    private static String nombreNormal(Persona p) {
        return IdentidadPersona.normalizarNombre(String.join(" ", nvl(p.getNombre()), nvl(p.getPaterno()), nvl(p.getMaterno())));
    }

    private static String nombreNormal(DatosResponsable d) {
        return IdentidadPersona.normalizarNombre(String.join(" ", nvl(d.nombre()), nvl(d.paterno()), nvl(d.materno())));
    }

    /**
     * Manda al VSIAF (UPDATE por la cola) lo que el SCIAF tiene hoy de una fila de responsable:
     * NOMRESP, C.I. y cargo. La usa «Personas y cargos» después de guardar. Devuelve null si la
     * fila todavía no está en el VSIAF (pendiente o rechazada): no hay qué actualizar, su
     * alta leerá el dato vigente.
     */
    public VsiafApoyoService.Envio enviarFilaAlVsiaf(Long idResponsable, String usuarioNombre) {
        return enviarFilaAlVsiaf(idResponsable, usuarioNombre, false);
    }

    /**
     * @param yaEstaEnVsiaf la fila estaba en el VSIAF antes del cambio y quien llama la marcó
     *                      pendiente a propósito (para que la sync no la pise mientras viaja):
     *                      se manda el UPDATE sin tomar esa marca como «nunca se envió».
     */
    public VsiafApoyoService.Envio enviarFilaAlVsiaf(Long idResponsable, String usuarioNombre, boolean yaEstaEnVsiaf) {
        Responsable r = buscar(idResponsable);
        if (!yaEstaEnVsiaf
                && vsiafApoyoService.necesitaAlta(VsiafApoyoService.TABLA_RESP, r.getIdResponsable(), r.isPendienteDbf())) {
            return null;
        }
        return vsiafApoyoService.actualizarResponsable(r, vsiafApoyoService.claveDe(r), usuarioNombre);
    }

    /**
     * ¿La persona es un individuo reconocible y no un registro "comodín" de la sincronización?
     * Lo es si tiene un C.I. válido (el mismo criterio con el que la sincronización enlaza por
     * C.I.: {@link IdentidadPersona#numeroCi}) y su nombre no es una marca del VSIAF.
     */
    public boolean esIdentificable(Persona p) {
        if (p == null) return false;
        // Mismas reglas que la sync: C.I. falsos (000000, 1234567… y los del parámetro) y
        // marcas del VSIAF (ACEFALO, … DEVOLUCION, SIN DATOS) no identifican a nadie.
        String nombre = IdentidadPersona.normalizarNombre(
                String.join(" ", nvl(p.getNombre()), nvl(p.getPaterno()), nvl(p.getMaterno())));
        if (IdentidadPersona.esNombreEspecial(nombre)) return false;
        return IdentidadPersona.numeroCi(p.getCi(), ciInvalidos()) != null;
    }

    /** C.I. falsos vigentes: los de por defecto más el parámetro {@code personas.ci_invalidos}. */
    public Set<String> ciInvalidos() {
        return IdentidadPersona.ciInvalidos(parametroDao.findById(IdentidadPersona.CLAVE_CI_INVALIDOS)
                .map(ParametroSistema::getValor).orElse(null));
    }

    private boolean tieneOtrosResponsables(Persona p, Responsable r) {
        if (p == null || p.getIdPersona() == null) return false;
        return responsableDao.findByPersonaIdPersona(p.getIdPersona()).stream()
                .anyMatch(o -> "ACTIVO".equals(o.getEstado()) && !o.getIdResponsable().equals(r.getIdResponsable()));
    }

    /** {@link #nombreYCi(Persona)} de lo que trae el formulario, normalizado igual que al guardar. */
    private static String nombreYCi(DatosResponsable d) {
        return String.join(" ", nvl(ResponsableAltaService.mayus(d.nombre())), nvl(ResponsableAltaService.mayus(d.paterno())),
                nvl(ResponsableAltaService.mayus(d.materno()))).trim() + "|" + nvl(ResponsableAltaService.limpiar(d.ci())).trim();
    }

    /** Lo que el VSIAF guarda de la persona en RESP.DBF: nombre completo y C.I. */
    public static String nombreYCi(Persona p) {
        if (p == null) return "";
        return String.join(" ", nvl(p.getNombre()), nvl(p.getPaterno()), nvl(p.getMaterno())).trim()
                + "|" + nvl(p.getCi()).trim();
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

    // ── Habilitar / deshabilitar (SCIAF y VSIAF) ────────────────────────────

    /** En RESP.DBF: API_ESTADO 1 = activo, 3 = inactivo (Saul, 28-sep-2026). */
    public static final short API_ACTIVO = 1;
    public static final short API_INACTIVO = 3;
    /** Responsable deshabilitado: en el VSIAF queda con API_ESTADO 3. */
    public static final String ESTADO_INACTIVO = "INACTIVO";

    /** Motivo por el que no se puede deshabilitar, o null si se puede. */
    public String impideDeshabilitar(Responsable r) {
        if (ESTADO_INACTIVO.equals(r.getEstado())) return "Ya está deshabilitado.";
        long activos = responsableDao.contarActivosAsignados(r.getIdResponsable());
        return activos > 0
                ? String.format("Tiene %d activo(s) a cargo: no se puede deshabilitar. Transfiéralos primero "
                        + "(si son faltantes, páselos a la oficina de faltantes).", activos)
                : null;
    }

    /**
     * Deshabilita (API_ESTADO 3) o habilita (1) al responsable en el SCIAF y manda el UPDATE al
     * VSIAF. Deshabilitar solo sin bienes a cargo. La fila no se borra en ningún lado.
     */
    public Resultado cambiarEstado(Long id, boolean habilitar, Usuario autor) {
        // /administracion/** es permitAll: el rol se exige aquí. Una solicitud aprobada entra por ejecutar().
        if (!com.usic.SistemasActivosFijosUAP.config.RolesSciaf.esAdministrativo(autor)) {
            throw new IllegalStateException("Solo ADMINISTRADOR o SUPER USUARIO; el resto envía una solicitud.");
        }
        return aplicarEstado(id, habilitar, autor);
    }

    /** Sin revisar el rol: solo para quien ya lo comprobó (solicitud aprobada, «Personas y cargos» al aplicar). */
    public Resultado aplicarEstado(Long id, boolean habilitar, Usuario autor) {
        Responsable r = buscar(id);
        if (!"ACTIVO".equals(r.getEstado()) && !ESTADO_INACTIVO.equals(r.getEstado())) {
            throw new IllegalArgumentException("El responsable fue eliminado del SCIAF: no se habilita ni deshabilita.");
        }
        if (habilitar) {
            if ("ACTIVO".equals(r.getEstado())) throw new IllegalArgumentException("Ya está habilitado.");
        } else {
            String motivo = impideDeshabilitar(r);
            if (motivo != null) throw new IllegalArgumentException(motivo);
        }
        String usuarioNombre = autor != null ? autor.getUsuario() : "SISTEMA";
        VsiafApoyoService.ClaveResponsable clave = vsiafApoyoService.claveDe(r);
        boolean necesitaAlta = vsiafApoyoService.necesitaAlta(VsiafApoyoService.TABLA_RESP, r.getIdResponsable(), r.isPendienteDbf());

        r.setEstado(habilitar ? "ACTIVO" : ESTADO_INACTIVO);
        r.setApiEstado(habilitar ? API_ACTIVO : API_INACTIVO);
        r.setFechaUlt(LocalDate.now());
        r.setUsuario(usuarioNombre);
        if (autor != null) r.setModificacionIdUsuario(autor.getIdUsuario());
        responsableService.save(r);

        // Si nunca llegó al VSIAF no hay fila que actualizar (si después se reenvía, el UPDATE del reenvío lleva el estado).
        VsiafApoyoService.Envio envio = necesitaAlta ? null
                : vsiafApoyoService.actualizarResponsable(buscar(id), clave, usuarioNombre);
        actividadService.registrar(autor, ActividadService.MOD_RESPONSABLE, ActividadService.ACC_MODIFICACION,
                referencia(r), (habilitar ? "Habilitó" : "Deshabilitó") + " al responsable " + referencia(r)
                + (envio == null ? " (aún no estaba en el VSIAF)" : envio.ok() ? "" : " (VSIAF: " + envio.mensaje() + ")"),
                r.getIdResponsable());
        String hecho = habilitar ? "Responsable habilitado." : "Responsable deshabilitado.";
        if (envio == null) return new Resultado(true, hecho + " Todavía no estaba en el VSIAF.");
        return new Resultado(envio.ok(), hecho + " " + envio.mensaje());
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

    /** Clave del mapa de la solicitud con {@link #nombreYCi} de la persona al momento de pedir. */
    public static final String PERSONA_ANTES = "personaAntes";

    /**
     * La solicitud guarda una foto de los datos de la persona de cuando se pidió (lo que se
     * autoriza es la oficina o el código). Si entretanto alguien corrigió a la persona, aplicar
     * esa foto la devolvería al dato viejo —y ahora además lo mandaría al VSIAF en todas sus
     * oficinas—. Se aplican los datos de persona del pedido solo si siguen como estaban; si no
     * (o la solicitud es anterior a esta marca), se conservan los vigentes.
     */
    private DatosResponsable conPersonaVigente(Map<String, Object> datos) {
        DatosResponsable d = DatosResponsable.deMapa(datos);
        Persona p = buscar(d.idResponsable()).getPersona();
        Object antes = datos.get(PERSONA_ANTES);
        if (p == null || (antes != null && antes.equals(nombreYCi(p)))) return d;
        return new DatosResponsable(d.idResponsable(), p.getCi(), d.codExp(), d.codigoFuncionario(), d.idOficina(),
                p.getNombre(), p.getPaterno(), p.getMaterno(), p.getCorreo(), d.cargo(), d.codigoApi());
    }

    @Override
    public Set<String> tipos() {
        return Set.of(TIPO_MODIFICAR, TIPO_ELIMINAR, TIPO_ESTADO);
    }

    @Override
    public String ejecutar(String tipo, Map<String, Object> datos, Usuario autor) {
        if (TIPO_MODIFICAR.equals(tipo)) return modificar(conPersonaVigente(datos), autor).msg();
        if (TIPO_ELIMINAR.equals(tipo)) return eliminar(OficinaGestionService.aLong(datos.get("idResponsable")), autor).msg();
        if (TIPO_ESTADO.equals(tipo)) {
            return aplicarEstado(OficinaGestionService.aLong(datos.get("idResponsable")),
                    Boolean.TRUE.equals(datos.get("habilitar")), autor).msg();
        }
        throw new IllegalArgumentException("Tipo no soportado: " + tipo);
    }
}
