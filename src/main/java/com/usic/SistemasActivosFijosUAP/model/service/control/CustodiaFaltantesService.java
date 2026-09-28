package com.usic.SistemasActivosFijosUAP.model.service.control;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.usic.SistemasActivosFijosUAP.model.IService.IResponsableService;
import com.usic.SistemasActivosFijosUAP.model.dao.IOficinaDao;
import com.usic.SistemasActivosFijosUAP.model.dao.IResposableDao;
import com.usic.SistemasActivosFijosUAP.model.entity.Oficina;
import com.usic.SistemasActivosFijosUAP.model.entity.Persona;
import com.usic.SistemasActivosFijosUAP.model.entity.Predio;
import com.usic.SistemasActivosFijosUAP.model.entity.Responsable;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;
import com.usic.SistemasActivosFijosUAP.model.service.ResponsableAltaService;
import com.usic.SistemasActivosFijosUAP.model.service.VsiafApoyoService;
import com.usic.SistemasActivosFijosUAP.model.service.supervision.ActividadService;
import com.usic.SistemasActivosFijosUAP.model.service.supervision.OficinaGestionService;
import com.usic.SistemasActivosFijosUAP.model.service.supervision.ResponsableGestionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Oficina de faltantes de cada predio y responsable de custodia de cada persona.
 * <p>
 * Replica la práctica del VSIAF: en el predio hay una oficina "FALTANTES …" y dentro se
 * da de alta a <b>la misma persona</b> que tiene el faltante (mismo {@code id_persona},
 * su CI y su cargo reales) para transferirle esos bienes. Una persona con faltantes en
 * dos predios tiene un responsable de custodia en cada uno; en el SCIAF se unen por la
 * persona.
 * <p>
 * Todo se crea al primer uso. La oficina, si el predio no tiene (antes se adopta una
 * "FALTANTES…" hecha a mano en el VSIAF, para no duplicarla); el responsable, si la
 * persona todavía no está en ella. Primero se guarda en PostgreSQL y, ya confirmada la
 * transacción, se encola el alta al VSIAF: el worker toma OFICINA_… antes que RESP_…
 * porque procesa por nombre de archivo.
 * <p>
 * <b>Ojo:</b> por ese mismo orden, un ACTUAL_… encolado junto con estas altas se aplica
 * <i>antes</i> que ellas. Quien transfiera bienes a la custodia tiene que esperar a que
 * el worker confirme el alta ({@link VsiafApoyoService#estados}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustodiaFaltantesService {

    /** Nombre de las oficinas nuevas: "FALTANTES CULP", el patrón de las que ya había. */
    public static final String PREFIJO_OFICINA = "FALTANTES ";

    private final IOficinaDao oficinaDao;
    private final IResposableDao responsableDao;
    private final IResponsableService responsableService;
    private final VsiafApoyoService vsiafApoyoService;
    private final ActividadService actividadService;
    private final PlatformTransactionManager txManager;

    /** Qué va a pasar al enviar a custodia un bien de este responsable (para la confirmación). */
    public record Prevision(Long idPredio, String unidad, String oficina, boolean creaOficina,
                            String persona, boolean creaResponsable) {}

    /** Resultado de {@link #asegurar}. */
    public record Custodia(Long idOficina, Long idResponsable, boolean oficinaNueva,
                           boolean responsableNuevo, boolean vsiafOk, String mensaje) {}

    /** Lo que hizo la transacción, para decidir qué encolar después de confirmarla. */
    private record Alta(Long idOficina, Long idResponsable, boolean oficinaNueva, boolean oficinaAdoptada,
                        boolean responsableNuevo) {}

    // ── Consulta ────────────────────────────────────────────────────────────

    /** Oficina de faltantes del predio: la marcada, o una "FALTANTES…" sin marcar; null si no hay. */
    public Oficina buscarOficina(Long idPredio) {
        Oficina marcada = primero(oficinaDao.custodiasDelPredio(idPredio));
        return marcada != null ? marcada : primero(oficinaDao.candidatasCustodia(idPredio));
    }

    /** Qué existe y qué se crearía, sin tocar nada. */
    public Prevision prever(Long idResponsableOriginal) {
        return enTransaccion(() -> {
            Responsable original = cargarOriginal(idResponsableOriginal);
            Predio predio = original.getOficina().getPredio();
            Oficina oficina = buscarOficina(predio.getIdPredio());
            boolean creaResponsable = oficina == null || responsableDe(oficina, original.getPersona()) == null;
            return new Prevision(predio.getIdPredio(), unidad(predio),
                    oficina != null ? OficinaGestionService.referencia(oficina) : nombreOficinaNueva(predio),
                    oficina == null, original.getPersona().getNombreCompleto(), creaResponsable);
        });
    }

    /**
     * Si la custodia ya existe en el VSIAF: el peor estado entre la oficina de faltantes y el
     * responsable de custodia ({@code VSIAF} solo si los dos están confirmados). Antes de eso
     * no se puede encolar el traslado del bien (ver el aviso de la clase).
     */
    public VsiafApoyoService.EstadoVsiaf estadoEnVsiaf(Long idResponsableCustodia) {
        return enTransaccion(() -> {
            Responsable r = responsableService.findByIdWithRelations(idResponsableCustodia);
            if (r == null || r.getOficina() == null) {
                return new VsiafApoyoService.EstadoVsiaf(VsiafApoyoService.EST_ERROR, "Sin custodia",
                        "El responsable de custodia ya no existe.");
            }
            Oficina o = r.getOficina();
            VsiafApoyoService.EstadoVsiaf eo = vsiafApoyoService.estados(VsiafApoyoService.TABLA_OFICINA,
                    java.util.Map.of(o.getIdOficina(), o.isPendienteDbf())).get(o.getIdOficina());
            VsiafApoyoService.EstadoVsiaf er = vsiafApoyoService.estados(VsiafApoyoService.TABLA_RESP,
                    java.util.Map.of(r.getIdResponsable(), r.isPendienteDbf())).get(r.getIdResponsable());
            return gravedad(eo) >= gravedad(er) ? eo : er;
        });
    }

    private static int gravedad(VsiafApoyoService.EstadoVsiaf e) {
        if (e == null) return 0;
        return switch (e.codigo()) {
            case VsiafApoyoService.EST_ERROR     -> 3;
            case VsiafApoyoService.EST_PENDIENTE -> 2;
            case VsiafApoyoService.EST_EN_COLA   -> 1;
            default                              -> 0;
        };
    }

    // ── Alta ────────────────────────────────────────────────────────────────

    /**
     * Deja lista la custodia para los faltantes de este responsable: la oficina de faltantes
     * de su predio y, dentro, la misma persona como responsable. Crea lo que falte y encola
     * sus altas al VSIAF.
     *
     * @param idResponsableOriginal responsable al que se le imputan los faltantes
     * @throws ReglaNegocioException si el responsable no se puede llevar a custodia
     */
    public Custodia asegurar(Long idResponsableOriginal, Usuario autor) {
        Alta alta;
        try {
            alta = enTransaccion(() -> prepararAlta(idResponsableOriginal, autor));
        } catch (DataIntegrityViolationException carrera) {
            // Otro pedido creó la misma oficina o el mismo responsable al mismo tiempo
            // (los índices únicos lo frenaron): ahora ya existe y se reutiliza.
            log.info("[CUSTODIA] Alta concurrente para el responsable {}; se reintenta: {}",
                    idResponsableOriginal, carrera.getMostSpecificCause().getMessage());
            alta = enTransaccion(() -> prepararAlta(idResponsableOriginal, autor));
        }
        Alta hecha = alta;
        Custodia custodia = enTransaccion(() -> enviarAlVsiaf(hecha, autor));
        registrarActividad(hecha, autor);
        return custodia;
    }

    private Alta prepararAlta(Long idResponsableOriginal, Usuario autor) {
        Responsable original = cargarOriginal(idResponsableOriginal);
        Predio predio = original.getOficina().getPredio();

        boolean oficinaNueva = false;
        boolean oficinaAdoptada = false;
        Oficina oficina = primero(oficinaDao.custodiasDelPredio(predio.getIdPredio()));
        if (oficina == null) {
            oficina = primero(oficinaDao.candidatasCustodia(predio.getIdPredio()));
            if (oficina != null) {
                adoptar(oficina, autor);
                oficinaAdoptada = true;
            } else {
                oficina = nuevaOficina(predio, autor);
                oficinaNueva = true;
            }
        }

        boolean responsableNuevo = false;
        Responsable custodio = responsableDe(oficina, original.getPersona());
        if (custodio == null) {
            custodio = nuevoResponsable(oficina, original, autor);
            responsableNuevo = true;
        } else if (!custodio.isEsCustodia() || !"ACTIVO".equals(custodio.getEstado())) {
            // Ya estuvo (y se dio de baja en el SCIAF al quedar vacío): en el VSIAF la fila sigue.
            custodio.setEsCustodia(true);
            custodio.setEstado("ACTIVO");
            tocar(custodio, autor);
            responsableDao.saveAndFlush(custodio);
        }

        return new Alta(oficina.getIdOficina(), custodio.getIdResponsable(),
                oficinaNueva, oficinaAdoptada, responsableNuevo);
    }

    /** Una "FALTANTES…" creada a mano en el VSIAF pasa a ser la oficina de faltantes del predio. */
    private void adoptar(Oficina oficina, Usuario autor) {
        oficina.setEsCustodia(true);
        oficina.setModificacion(new Date());
        if (autor != null) oficina.setModificacionIdUsuario(autor.getIdUsuario());
        oficinaDao.saveAndFlush(oficina);
        for (Responsable r : responsableDao.findByOficinaIdOficina(oficina.getIdOficina())) {
            if (!r.isEsCustodia()) {
                r.setEsCustodia(true);
                responsableDao.save(r);
            }
        }
        responsableDao.flush();
        log.info("[CUSTODIA] Oficina {} adoptada como oficina de faltantes", OficinaGestionService.referencia(oficina));
    }

    private Oficina nuevaOficina(Predio predio, Usuario autor) {
        Short max = oficinaDao.maxCodOfiPorPredio(predio.getIdPredio());
        Oficina o = new Oficina();
        o.setPredio(predio);
        o.setCodOfi((short) ((max != null ? max : 0) + 1));
        o.setNombre(nombreOficinaNueva(predio));
        o.setObserv("Oficina de faltantes del predio. La creó el SCIAF al registrar el primer faltante.");
        o.setEsCustodia(true);
        o.setEstado("ACTIVO");
        o.setFechaUlt(LocalDate.now());
        o.setUsuario(nombreUsuario(autor));
        o.setApiEstado(Short.valueOf("1"));
        if (autor != null) o.setRegistroIdUsuario(autor.getIdUsuario());
        oficinaDao.saveAndFlush(o);
        log.info("[CUSTODIA] Oficina de faltantes creada: {}", OficinaGestionService.referencia(o));
        return o;
    }

    /** La misma persona, con su cargo real, como responsable dentro de la oficina de faltantes. */
    private Responsable nuevoResponsable(Oficina oficina, Responsable original, Usuario autor) {
        int codigo = siguienteCodResp(oficina);
        if (codigo > ResponsableAltaService.MAX_COD_RESP) {
            throw new ReglaNegocioException("La oficina de faltantes " + OficinaGestionService.referencia(oficina)
                    + " ya no tiene códigos de responsable libres.");
        }
        Responsable r = new Responsable();
        r.setPersona(original.getPersona());
        r.setOficina(oficina);
        r.setCargo(original.getCargo());
        r.setCodigoFuncionario(String.valueOf(codigo));
        r.setCodExp(original.getCodExp() != null ? original.getCodExp() : Short.valueOf("9"));
        r.setFechaUlt(LocalDate.now());
        r.setUsuario(nombreUsuario(autor));
        r.setApiEstado(Short.valueOf("1"));
        r.setEsCustodia(true);
        r.setEstado("ACTIVO");
        if (autor != null) r.setRegistroIdUsuario(autor.getIdUsuario());
        responsableDao.saveAndFlush(r);
        log.info("[CUSTODIA] {} registrado en la oficina de faltantes {} (código {})",
                original.getPersona().getNombreCompleto(), OficinaGestionService.referencia(oficina), codigo);
        return r;
    }

    /**
     * Encola al VSIAF lo que no está allá: lo recién creado, o lo que quedó pendiente de un
     * intento anterior. Una oficina adoptada o un responsable reactivado ya vienen del VSIAF.
     */
    private Custodia enviarAlVsiaf(Alta alta, Usuario autor) {
        String usuario = nombreUsuario(autor);
        Oficina oficina = oficinaDao.findById(alta.idOficina()).orElseThrow();
        Responsable custodio = responsableService.findByIdWithRelations(alta.idResponsable());

        List<String> fallas = new ArrayList<>();
        boolean encolo = false;
        // necesitaAlta: nunca salió (pendiente) o el worker rechazó la última orden.
        if (alta.oficinaNueva() || vsiafApoyoService.necesitaAlta(
                VsiafApoyoService.TABLA_OFICINA, oficina.getIdOficina(), oficina.isPendienteDbf())) {
            VsiafApoyoService.Envio e = alta.oficinaNueva()
                    ? vsiafApoyoService.insertarOficina(oficina, usuario)
                    : vsiafApoyoService.reenviarOficina(oficina, usuario);
            encolo = true;
            if (!e.ok()) fallas.add("Oficina: " + e.mensaje());
        }
        if (alta.responsableNuevo() || vsiafApoyoService.necesitaAlta(
                VsiafApoyoService.TABLA_RESP, custodio.getIdResponsable(), custodio.isPendienteDbf())) {
            VsiafApoyoService.Envio e = alta.responsableNuevo()
                    ? vsiafApoyoService.insertarResponsable(custodio, usuario)
                    : vsiafApoyoService.reenviarResponsable(custodio, usuario);
            encolo = true;
            if (!e.ok()) fallas.add("Responsable: " + e.mensaje());
        }

        String mensaje;
        if (!fallas.isEmpty()) {
            mensaje = String.join(" ", fallas);
        } else if (encolo) {
            mensaje = "Custodia lista en " + OficinaGestionService.referencia(oficina)
                    + ". Enviada al VSIAF: el worker la aplica en unos segundos.";
        } else {
            mensaje = "Custodia existente: " + ResponsableGestionService.referencia(custodio) + ".";
        }
        return new Custodia(oficina.getIdOficina(), custodio.getIdResponsable(),
                alta.oficinaNueva(), alta.responsableNuevo(), fallas.isEmpty(), mensaje);
    }

    private void registrarActividad(Alta alta, Usuario autor) {
        if (!alta.oficinaNueva() && !alta.oficinaAdoptada() && !alta.responsableNuevo()) return;
        enTransaccion(() -> {
            if (alta.oficinaNueva() || alta.oficinaAdoptada()) {
                Oficina o = oficinaDao.findById(alta.idOficina()).orElseThrow();
                String ref = OficinaGestionService.referencia(o);
                actividadService.registrar(autor, ActividadService.MOD_OFICINA, ActividadService.ACC_REGISTRO, ref,
                        (alta.oficinaNueva() ? "Creó la oficina de faltantes " : "Marcó como oficina de faltantes a ") + ref,
                        o.getIdOficina());
            }
            if (alta.responsableNuevo()) {
                Responsable r = responsableService.findByIdWithRelations(alta.idResponsable());
                String ref = ResponsableGestionService.referencia(r);
                actividadService.registrar(autor, ActividadService.MOD_RESPONSABLE, ActividadService.ACC_REGISTRO, ref,
                        "Registró a " + ref + " en la oficina de faltantes", r.getIdResponsable());
            }
            return null;
        });
    }

    // ── Apoyo ───────────────────────────────────────────────────────────────

    private Responsable cargarOriginal(Long idResponsable) {
        Responsable r = idResponsable != null ? responsableService.findByIdWithRelations(idResponsable) : null;
        if (r == null) throw new ReglaNegocioException("No se encontró el responsable.");
        if (r.getPersona() == null) {
            throw new ReglaNegocioException("El responsable no tiene una persona registrada: "
                    + "no se puede saber de quién es el faltante.");
        }
        Oficina oficina = r.getOficina();
        if (oficina == null || oficina.getPredio() == null) {
            throw new ReglaNegocioException("El responsable no tiene oficina o predio.");
        }
        Predio predio = oficina.getPredio();
        if (predio.getEntidad() == null || predio.getUnidad() == null || predio.getUnidad().isBlank()) {
            throw new ReglaNegocioException("El predio " + predio.getDescrip()
                    + " no tiene entidad o unidad: no se puede registrar en el VSIAF.");
        }
        if (r.isEsCustodia() || oficina.isEsCustodia()) {
            throw new ReglaNegocioException("Este responsable ya es de la oficina de faltantes: "
                    + "sus bienes ya están en custodia.");
        }
        return r;
    }

    /** La persona dentro de la oficina, en cualquier estado; null si nunca estuvo. */
    private Responsable responsableDe(Oficina oficina, Persona persona) {
        return responsableDao.findByOficinaIdOficina(oficina.getIdOficina()).stream()
                .filter(r -> r.getPersona() != null
                        && Objects.equals(r.getPersona().getIdPersona(), persona.getIdPersona()))
                .findFirst().orElse(null);
    }

    /** CODRESP siguiente dentro de la oficina (cuenta también los dados de baja: en el VSIAF siguen). */
    private int siguienteCodResp(Oficina oficina) {
        return responsableDao.findByOficinaIdOficina(oficina.getIdOficina()).stream()
                .map(r -> VsiafApoyoService.codResp(r.getCodigoFuncionario()))
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .max().orElse(0) + 1;
    }

    private static String nombreOficinaNueva(Predio predio) {
        String nombre = PREFIJO_OFICINA + unidad(predio);
        return nombre.length() > OficinaGestionService.MAX_NOMBRE_OFICINA
                ? nombre.substring(0, OficinaGestionService.MAX_NOMBRE_OFICINA) : nombre;
    }

    private static String unidad(Predio predio) {
        return predio.getUnidad() != null ? predio.getUnidad().trim() : "";
    }

    private static void tocar(Responsable r, Usuario autor) {
        r.setFechaUlt(LocalDate.now());
        r.setUsuario(nombreUsuario(autor));
        if (autor != null) r.setModificacionIdUsuario(autor.getIdUsuario());
    }

    private static String nombreUsuario(Usuario autor) {
        return autor != null ? autor.getUsuario() : "SISTEMA";
    }

    private static <T> T primero(List<T> lista) {
        return (lista == null || lista.isEmpty()) ? null : lista.get(0);
    }

    private <T> T enTransaccion(Supplier<T> trabajo) {
        return new TransactionTemplate(txManager).execute(estado -> trabajo.get());
    }
}
