package com.usic.SistemasActivosFijosUAP.model.service.duplicados;

import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.usic.SistemasActivosFijosUAP.model.IService.IResponsableService;
import com.usic.SistemasActivosFijosUAP.model.dao.IOficinaDao;
import com.usic.SistemasActivosFijosUAP.model.dao.IPersonasDao;
import com.usic.SistemasActivosFijosUAP.model.dao.IResposableDao;
import com.usic.SistemasActivosFijosUAP.model.entity.Oficina;
import com.usic.SistemasActivosFijosUAP.model.entity.Persona;
import com.usic.SistemasActivosFijosUAP.model.entity.Predio;
import com.usic.SistemasActivosFijosUAP.model.entity.Responsable;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;
import com.usic.SistemasActivosFijosUAP.model.service.ResponsableAltaService;
import com.usic.SistemasActivosFijosUAP.model.service.VsiafApoyoService;
import com.usic.SistemasActivosFijosUAP.model.service.control.ReglaNegocioException;
import com.usic.SistemasActivosFijosUAP.model.service.supervision.ActividadService;
import com.usic.SistemasActivosFijosUAP.model.service.supervision.OficinaGestionService;
import com.usic.SistemasActivosFijosUAP.model.service.supervision.ResponsableGestionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Oficina de duplicados de cada predio y, dentro, la misma persona que tenía el registro
 * sobrante. Es el mismo esquema que la oficina de faltantes (opción B), pero otra oficina:
 * se reconoce por {@code es_duplicados}, no por {@code es_custodia}.
 * <p>
 * Todo se crea al primer uso: antes se adopta una "DUPLICADOS…" hecha a mano en el VSIAF,
 * para no duplicarla. Primero se guarda en PostgreSQL y, confirmada la transacción, se
 * encola el alta al VSIAF. El traslado del bien espera a que el worker confirme ese alta
 * (el worker procesa ACTUAL_… antes que OFICINA_… y RESP_…; ver EnvioDuplicadosService).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustodiaDuplicadosService {

    /** Nombre de las oficinas nuevas: "DUPLICADOS CULP". */
    public static final String PREFIJO_OFICINA = "DUPLICADOS ";

    private final IOficinaDao oficinaDao;
    private final IResposableDao responsableDao;
    private final IPersonasDao personaDao;
    private final IResponsableService responsableService;
    private final VsiafApoyoService vsiafApoyoService;
    private final ActividadService actividadService;
    private final PlatformTransactionManager txManager;

    /** Qué existe y qué se crearía (para la vista previa). */
    public record Prevision(String oficina, boolean creaOficina, boolean creaResponsable) {}

    public record Custodia(Long idOficina, Long idResponsable, boolean vsiafOk, String mensaje) {}

    private record Alta(Long idOficina, Long idResponsable, boolean oficinaNueva, boolean oficinaAdoptada,
                        boolean responsableNuevo) {}

    // ── Consulta ────────────────────────────────────────────────────────────

    /** La oficina de duplicados del predio: la marcada, o una "DUPLICADOS…" sin marcar; null si no hay. */
    public Oficina buscarOficina(Long idPredio) {
        Oficina marcada = primero(oficinaDao.duplicadosDelPredio(idPredio));
        return marcada != null ? marcada : primero(oficinaDao.candidatasDuplicados(idPredio));
    }

    /** Sin tocar nada. Corre dentro de la transacción de quien llama. */
    public Prevision prever(Predio predio, Persona persona) {
        Oficina oficina = buscarOficina(predio.getIdPredio());
        boolean creaResponsable = oficina == null || persona == null || responsableDe(oficina, persona) == null;
        return new Prevision(oficina != null ? OficinaGestionService.referencia(oficina) : nombreOficinaNueva(predio),
                oficina == null, creaResponsable);
    }

    /** El peor estado en el VSIAF entre la oficina de duplicados y el responsable ({@code VSIAF} si los dos están). */
    public VsiafApoyoService.EstadoVsiaf estadoEnVsiaf(Long idResponsableCustodia) {
        return enTransaccion(() -> {
            Responsable r = responsableService.findByIdWithRelations(idResponsableCustodia);
            if (r == null || r.getOficina() == null) {
                return new VsiafApoyoService.EstadoVsiaf(VsiafApoyoService.EST_ERROR, "Sin destino",
                        "El responsable de la oficina de duplicados ya no existe.");
            }
            Oficina o = r.getOficina();
            VsiafApoyoService.EstadoVsiaf eo = vsiafApoyoService.estados(VsiafApoyoService.TABLA_OFICINA,
                    Map.of(o.getIdOficina(), o.isPendienteDbf())).get(o.getIdOficina());
            VsiafApoyoService.EstadoVsiaf er = vsiafApoyoService.estados(VsiafApoyoService.TABLA_RESP,
                    Map.of(r.getIdResponsable(), r.isPendienteDbf())).get(r.getIdResponsable());
            return gravedad(eo) >= gravedad(er) ? eo : er;
        });
    }

    // ── Alta ────────────────────────────────────────────────────────────────

    /**
     * Deja lista la oficina de duplicados del predio del responsable original y, dentro, a la
     * persona. Crea lo que falte y encola sus altas al VSIAF.
     *
     * @param idResponsableOriginal quién tenía el sobrante (da el predio, el cargo y el código de expedición)
     * @param idPersona             a nombre de quién queda (la del registro)
     */
    public Custodia asegurar(Long idResponsableOriginal, Long idPersona, Usuario autor) {
        Alta alta;
        try {
            alta = enTransaccion(() -> prepararAlta(idResponsableOriginal, idPersona, autor));
        } catch (DataIntegrityViolationException carrera) {
            // Otro pedido creó la misma oficina o el mismo responsable al mismo tiempo: ya existe.
            log.info("[DUPLICADOS] Alta concurrente para el responsable {}; se reintenta: {}",
                    idResponsableOriginal, carrera.getMostSpecificCause().getMessage());
            try {
                alta = enTransaccion(() -> prepararAlta(idResponsableOriginal, idPersona, autor));
            } catch (DataIntegrityViolationException otra) {
                throw new ReglaNegocioException("La base rechazó el alta en la oficina de duplicados: "
                        + String.valueOf(otra.getMostSpecificCause().getMessage()).split("\\R", 2)[0]
                        + ". Revise la oficina (¿la misma persona dos veces?) y pulse Reintentar.");
            }
        }
        Alta hecha = alta;
        Custodia custodia = enTransaccion(() -> enviarAlVsiaf(hecha, autor));
        registrarActividad(hecha, autor);
        return custodia;
    }

    private Alta prepararAlta(Long idResponsableOriginal, Long idPersona, Usuario autor) {
        Responsable original = responsableService.findByIdWithRelations(idResponsableOriginal);
        if (original == null) throw new ReglaNegocioException("No se encontró a quién tenía el activo.");
        if (original.isEsDuplicados()) {
            throw new ReglaNegocioException("El responsable de origen ya es de la oficina de duplicados.");
        }
        Oficina ofOriginal = original.getOficina();
        if (ofOriginal == null || ofOriginal.getPredio() == null) {
            throw new ReglaNegocioException("El responsable de origen no tiene oficina o predio.");
        }
        Predio predio = ofOriginal.getPredio();
        if (predio.getEntidad() == null || predio.getUnidad() == null || predio.getUnidad().isBlank()) {
            throw new ReglaNegocioException("El predio " + predio.getDescrip()
                    + " no tiene entidad o unidad: no se puede registrar en el VSIAF.");
        }
        Persona persona = personaVigente(idPersona != null ? idPersona
                : original.getPersona() != null ? original.getPersona().getIdPersona() : null);
        if (persona == null) {
            throw new ReglaNegocioException("El responsable de origen no tiene una persona registrada.");
        }

        boolean oficinaNueva = false;
        boolean oficinaAdoptada = false;
        Oficina oficina = primero(oficinaDao.duplicadosDelPredio(predio.getIdPredio()));
        if (oficina == null) {
            oficina = primero(oficinaDao.candidatasDuplicados(predio.getIdPredio()));
            if (oficina != null) {
                adoptar(oficina, autor);
                oficinaAdoptada = true;
            } else {
                oficina = nuevaOficina(predio, autor);
                oficinaNueva = true;
            }
        }

        boolean responsableNuevo = false;
        Responsable custodio = responsableDe(oficina, persona);
        if (custodio == null) {
            exigirSinHomonimo(oficina, persona);
            custodio = nuevoResponsable(oficina, original, persona, autor);
            responsableNuevo = true;
        } else if (!custodio.isEsDuplicados() || !"ACTIVO".equals(custodio.getEstado())) {
            // Ya estuvo (y se dio de baja en el SCIAF): en el VSIAF la fila sigue.
            custodio.setEsDuplicados(true);
            custodio.setEstado("ACTIVO");
            tocar(custodio, autor);
            responsableDao.saveAndFlush(custodio);
        }
        return new Alta(oficina.getIdOficina(), custodio.getIdResponsable(), oficinaNueva, oficinaAdoptada, responsableNuevo);
    }

    /** Una "DUPLICADOS…" creada a mano en el VSIAF pasa a ser la oficina de duplicados del predio. */
    private void adoptar(Oficina oficina, Usuario autor) {
        oficina.setEsDuplicados(true);
        oficina.setModificacion(new Date());
        if (autor != null) oficina.setModificacionIdUsuario(autor.getIdUsuario());
        oficinaDao.saveAndFlush(oficina);
        for (Responsable r : responsableDao.findByOficinaIdOficina(oficina.getIdOficina())) {
            if (!r.isEsDuplicados()) {
                r.setEsDuplicados(true);
                responsableDao.save(r);
            }
        }
        responsableDao.flush();
        log.info("[DUPLICADOS] Oficina {} adoptada como oficina de duplicados", OficinaGestionService.referencia(oficina));
    }

    private Oficina nuevaOficina(Predio predio, Usuario autor) {
        Short max = oficinaDao.maxCodOfiPorPredio(predio.getIdPredio());
        Oficina o = new Oficina();
        o.setPredio(predio);
        o.setCodOfi((short) ((max != null ? max : 0) + 1));
        o.setNombre(nombreOficinaNueva(predio));
        o.setObserv("Oficina de duplicados del predio: registros que son el mismo bien que otro activo (error del revalúo). "
                + "La creó el SCIAF al registrar el primer duplicado.");
        o.setEsDuplicados(true);
        // Pendiente hasta que el alta quede encolada (insertarOficina lo baja): si el proceso se
        // corta entre las dos transacciones, el próximo ciclo la vuelve a enviar.
        o.setPendienteDbf(true);
        o.setEstado("ACTIVO");
        o.setFechaUlt(LocalDate.now());
        o.setUsuario(nombreUsuario(autor));
        o.setApiEstado(Short.valueOf("1"));
        if (autor != null) o.setRegistroIdUsuario(autor.getIdUsuario());
        oficinaDao.saveAndFlush(o);
        log.info("[DUPLICADOS] Oficina de duplicados creada: {}", OficinaGestionService.referencia(o));
        return o;
    }

    /**
     * Si en la oficina ya hay otra persona con el mismo nombre (típico de una persona partida
     * en dos por la sincronización), no se da de alta una segunda: primero se unifican.
     */
    private void exigirSinHomonimo(Oficina oficina, Persona persona) {
        String nombre = nombreNormalizado(persona);
        responsableDao.findByOficinaIdOficina(oficina.getIdOficina()).stream()
                .filter(r -> r.getPersona() != null
                        && !Objects.equals(r.getPersona().getIdPersona(), persona.getIdPersona())
                        && nombre.equals(nombreNormalizado(r.getPersona())))
                .findFirst()
                .ifPresent(r -> {
                    throw new ReglaNegocioException("En la oficina de duplicados " + OficinaGestionService.referencia(oficina)
                            + " ya está " + nombreCompleto(r.getPersona()) + " (C.I. "
                            + (r.getPersona().getCi() != null && !r.getPersona().getCi().isBlank() ? r.getPersona().getCi() : "s/d")
                            + ", código " + r.getCodigoFuncionario() + ") con el mismo nombre, como otra persona. "
                            + "Si es la misma, júntelas en Personas y cargos y pulse Reintentar.");
                });
    }

    /** La misma persona, con el cargo del registro original, dentro de la oficina de duplicados. */
    private Responsable nuevoResponsable(Oficina oficina, Responsable original, Persona persona, Usuario autor) {
        int codigo = siguienteCodResp(oficina);
        if (codigo > ResponsableAltaService.MAX_COD_RESP) {
            throw new ReglaNegocioException("La oficina de duplicados " + OficinaGestionService.referencia(oficina)
                    + " ya no tiene códigos de responsable libres.");
        }
        Responsable r = new Responsable();
        r.setPersona(persona);
        r.setOficina(oficina);
        r.setCargo(original.getCargo());
        r.setCodigoFuncionario(String.valueOf(codigo));
        r.setCodExp(original.getCodExp() != null ? original.getCodExp() : Short.valueOf("9"));
        r.setFechaUlt(LocalDate.now());
        r.setUsuario(nombreUsuario(autor));
        r.setApiEstado(Short.valueOf("1"));
        r.setEsDuplicados(true);
        r.setPendienteDbf(true);   // ídem: hasta que insertarResponsable lo encole
        r.setEstado("ACTIVO");
        if (autor != null) r.setRegistroIdUsuario(autor.getIdUsuario());
        responsableDao.saveAndFlush(r);
        log.info("[DUPLICADOS] {} registrado en la oficina de duplicados {} (código {})",
                nombreCompleto(persona), OficinaGestionService.referencia(oficina), codigo);
        return r;
    }

    /** Encola al VSIAF lo que no está allá: lo recién creado o lo que quedó pendiente. */
    private Custodia enviarAlVsiaf(Alta alta, Usuario autor) {
        String usuario = nombreUsuario(autor);
        Oficina oficina = oficinaDao.findById(alta.idOficina()).orElseThrow();
        Responsable custodio = responsableService.findByIdWithRelations(alta.idResponsable());

        List<String> fallas = new ArrayList<>();
        boolean encolo = false;
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
            mensaje = "Oficina de duplicados lista en " + OficinaGestionService.referencia(oficina)
                    + ". Alta encolada: se espera la confirmación del worker VSIAF.";
        } else {
            mensaje = "Destino existente: " + ResponsableGestionService.referencia(custodio) + ".";
        }
        return new Custodia(oficina.getIdOficina(), custodio.getIdResponsable(), fallas.isEmpty(), mensaje);
    }

    private void registrarActividad(Alta alta, Usuario autor) {
        if (!alta.oficinaNueva() && !alta.oficinaAdoptada() && !alta.responsableNuevo()) return;
        enTransaccion(() -> {
            if (alta.oficinaNueva() || alta.oficinaAdoptada()) {
                Oficina o = oficinaDao.findById(alta.idOficina()).orElseThrow();
                String ref = OficinaGestionService.referencia(o);
                actividadService.registrar(autor, ActividadService.MOD_OFICINA, ActividadService.ACC_REGISTRO, ref,
                        (alta.oficinaNueva() ? "Creó la oficina de duplicados " : "Marcó como oficina de duplicados a ") + ref,
                        o.getIdOficina());
            }
            if (alta.responsableNuevo()) {
                Responsable r = responsableService.findByIdWithRelations(alta.idResponsable());
                String ref = ResponsableGestionService.referencia(r);
                actividadService.registrar(autor, ActividadService.MOD_RESPONSABLE, ActividadService.ACC_REGISTRO, ref,
                        "Registró a " + ref + " en la oficina de duplicados", r.getIdResponsable());
            }
            return null;
        });
    }

    // ── Apoyo ───────────────────────────────────────────────────────────────

    /**
     * La persona vigente: si se unificó en otra (Personas y cargos), la principal. El caso
     * guarda la persona del día del registro; sin esto, después de juntar dos registros de la
     * misma persona el alta y el traslado seguirían buscando a la que quedó fusionada.
     */
    public Persona personaVigente(Long idPersona) {
        Persona p = idPersona != null ? personaDao.findById(idPersona).orElse(null) : null;
        for (int i = 0; p != null && p.getFusionadaEn() != null && i < 10; i++) {
            Persona siguiente = personaDao.findById(p.getFusionadaEn()).orElse(null);
            if (siguiente == null) break;
            p = siguiente;
        }
        return p;
    }

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

    static String nombreOficinaNueva(Predio predio) {
        String nombre = PREFIJO_OFICINA + (predio.getUnidad() != null ? predio.getUnidad().trim() : "");
        return nombre.length() > OficinaGestionService.MAX_NOMBRE_OFICINA
                ? nombre.substring(0, OficinaGestionService.MAX_NOMBRE_OFICINA) : nombre;
    }

    private static String nombreCompleto(Persona p) {
        return Stream.of(p.getNombre(), p.getPaterno(), p.getMaterno())
                .filter(s -> s != null && !s.isBlank()).map(String::trim)
                .collect(Collectors.joining(" "));
    }

    private static String nombreNormalizado(Persona p) {
        String s = Normalizer.normalize(nombreCompleto(p), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return s.toUpperCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
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
