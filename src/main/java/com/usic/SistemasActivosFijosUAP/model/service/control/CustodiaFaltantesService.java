package com.usic.SistemasActivosFijosUAP.model.service.control;

import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.usic.SistemasActivosFijosUAP.model.IService.IResponsableService;
import com.usic.SistemasActivosFijosUAP.model.dao.IOficinaDao;
import com.usic.SistemasActivosFijosUAP.model.dao.IPersonasDao;
import com.usic.SistemasActivosFijosUAP.model.dao.IPredioDao;
import com.usic.SistemasActivosFijosUAP.model.dao.IResposableDao;
import com.usic.SistemasActivosFijosUAP.model.dto.control.RegistrarFaltantesRequest;
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
    private final IPersonasDao personaDao;
    private final IPredioDao predioDao;
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

    // Por qué un responsable de la oficina de faltantes aparece como candidato.
    public static final String MISMA_PERSONA = "MISMA_PERSONA";
    /** Otro registro de la misma persona (el que quien registra sumó a la notificación). */
    public static final String OTRO_REGISTRO = "OTRO_REGISTRO";
    /** Otra persona con el mismo nombre: duplicado de la sincronización o un homónimo real. */
    public static final String MISMO_NOMBRE = "MISMO_NOMBRE";
    public static final String OTRO = "OTRO";

    /** Alguien que ya está en la oficina de faltantes del predio. */
    public record Candidato(Long idResponsable, Long idPersona, String nombre, String ci, String cargo,
                            String codigo, String coincidencia, String estadoVsiaf, boolean elegible) {}

    /**
     * Destinos posibles en la oficina de faltantes de un predio, para que quien registra elija.
     *
     * @param sugerido         candidato preseleccionado; null = alta automática
     * @param requiereEleccion hay alguien con el mismo nombre u otro registro de la persona:
     *                         no se da de alta a nadie sin que se elija
     */
    public record OpcionesPredio(Long idPredio, String unidad, String predio, String oficina, boolean creaOficina,
                                 boolean principalYaEsta, List<Candidato> candidatos, Long sugerido,
                                 boolean requiereEleccion) {}

    /**
     * A quién van los bienes de un predio.
     *
     * @param custodio         elegido a mano; null = el ciclo de envío asegura la custodia de la persona
     * @param permitirHomonimo el alta automática puede crear a la persona aunque haya alguien con su nombre
     */
    public record Destino(Responsable custodio, boolean permitirHomonimo) {}

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

    // ── Elección manual del destino ─────────────────────────────────────────
    //
    // Arreglo temporal: la sincronización con el VSIAF a veces parte a una persona en dos
    // (un registro con C.I. y otro sin). Sin esto, cada registro tendría su propio responsable
    // en la oficina de faltantes. Mientras se limpia la base, quien registra elige a quién van.

    /**
     * Quién está ya en la oficina de faltantes del predio y qué se sugiere, sin tocar nada.
     *
     * @param principal   a nombre de quién sale la notificación
     * @param idsPersonas el principal y los otros registros de la misma persona
     */
    public OpcionesPredio opciones(Long idPredio, Persona principal, Set<Long> idsPersonas) {
        return enTransaccion(() -> {
            Predio predio = predioDao.findById(idPredio)
                    .orElseThrow(() -> new ReglaNegocioException("El predio no existe."));
            Oficina oficina = buscarOficina(idPredio);
            if (oficina == null) {
                return new OpcionesPredio(idPredio, unidad(predio), predio.getDescrip(), nombreOficinaNueva(predio),
                        true, false, List.of(), null, false);
            }
            List<Candidato> candidatos = candidatos(oficina, principal, idsPersonas);
            boolean principalYaEsta = candidatos.stream().anyMatch(c -> MISMA_PERSONA.equals(c.coincidencia()));
            List<Candidato> parecidos = candidatos.stream().filter(c -> esParecido(c.coincidencia())).toList();
            Long sugerido = null;
            if (principalYaEsta) {
                sugerido = candidatos.stream().filter(c -> MISMA_PERSONA.equals(c.coincidencia()) && c.elegible())
                        .map(Candidato::idResponsable).findFirst().orElse(null);
            } else {
                // Solo otro registro que quien registra ya indicó como la misma persona. Uno del
                // mismo nombre puede ser un homónimo real: ese se elige a mano, nunca por defecto.
                sugerido = parecidos.stream().filter(c -> OTRO_REGISTRO.equals(c.coincidencia()) && c.elegible())
                        .map(Candidato::idResponsable).findFirst().orElse(null);
            }
            return new OpcionesPredio(idPredio, unidad(predio), predio.getDescrip(),
                    OficinaGestionService.referencia(oficina), false, principalYaEsta, candidatos, sugerido,
                    !principalYaEsta && !parecidos.isEmpty());
        });
    }

    /**
     * Valida lo que eligió quien registra para un predio y dice a quién van los bienes.
     * Corre dentro de la transacción del registro (y de la vista previa).
     *
     * @param eleccion null = sin elegir
     * @throws ReglaNegocioException si la elección no vale o hace falta elegir
     */
    public Destino resolverDestino(Long idPredio, Persona principal, Set<Long> idsPersonas,
                                   RegistrarFaltantesRequest.DestinoCustodia eleccion) {
        Oficina oficina = buscarOficina(idPredio);
        Long idElegido = eleccion != null ? eleccion.idResponsableCustodia() : null;
        boolean crearNuevo = eleccion != null && Boolean.TRUE.equals(eleccion.crearNuevo());
        if (oficina == null) {
            if (idElegido != null) {
                throw new ReglaNegocioException("el predio todavía no tiene oficina de faltantes: no hay a quién elegir.");
            }
            return new Destino(null, false);   // se crea la oficina y la persona, como siempre
        }
        List<Candidato> candidatos = candidatos(oficina, principal, idsPersonas);

        if (idElegido != null) {
            Candidato c = candidatos.stream().filter(x -> idElegido.equals(x.idResponsable())).findFirst()
                    .orElseThrow(() -> new ReglaNegocioException("el responsable elegido no está en la oficina de faltantes "
                            + OficinaGestionService.referencia(oficina) + "."));
            if (!c.elegible()) {
                // Sin confirmar, el ciclo de envío podría soltarlo y dar de alta a otro.
                throw new ReglaNegocioException(c.nombre() + " todavía no está confirmado en el VSIAF ("
                        + c.estadoVsiaf() + "). Espere a que el VSIAF lo confirme o elija otro.");
            }
            return new Destino(responsableService.findByIdWithRelations(idElegido), false);
        }

        if (candidatos.stream().anyMatch(c -> MISMA_PERSONA.equals(c.coincidencia()))) {
            return new Destino(null, false);   // ya está: el alta automática lo encuentra
        }
        List<Candidato> parecidos = candidatos.stream().filter(c -> esParecido(c.coincidencia())).toList();
        if (parecidos.isEmpty()) return new Destino(null, false);

        Candidato mismoRegistro = parecidos.stream().filter(c -> OTRO_REGISTRO.equals(c.coincidencia()))
                .findFirst().orElse(null);
        if (mismoRegistro != null) {
            throw new ReglaNegocioException("en la oficina de faltantes " + OficinaGestionService.referencia(oficina)
                    + " ya está " + describir(mismoRegistro) + ", que usted indicó como la misma persona: elíjalo como destino.");
        }
        if (!crearNuevo) {
            throw new ReglaNegocioException("en la oficina de faltantes " + OficinaGestionService.referencia(oficina)
                    + " ya está " + describir(parecidos.get(0)) + ", con el mismo nombre. Elija si los bienes van a "
                    + "esa persona o marque que es otra persona.");
        }
        return new Destino(null, true);
    }

    /**
     * Deja vigente al responsable elegido a mano: si su oficina es una "FALTANTES…" hecha a mano
     * en el VSIAF que todavía no se marcó, se adopta (como en el alta automática); si él se dio
     * de baja en el SCIAF al quedar vacío, se reactiva.
     */
    public void reactivarSiHaceFalta(Responsable custodio, Usuario autor) {
        Oficina oficina = custodio.getOficina();
        if (oficina != null && !oficina.isEsCustodia()) adoptar(oficina, autor);
        if (custodio.isEsCustodia() && "ACTIVO".equals(custodio.getEstado())) return;
        custodio.setEsCustodia(true);
        custodio.setEstado("ACTIVO");
        tocar(custodio, autor);
        responsableDao.saveAndFlush(custodio);
    }

    /** Los de la oficina, con su coincidencia y su estado en el VSIAF: los parecidos primero. */
    private List<Candidato> candidatos(Oficina oficina, Persona principal, Set<Long> idsPersonas) {
        List<Responsable> filas = responsableDao.findByOficinaIdOficina(oficina.getIdOficina());
        Map<Long, Boolean> pendientes = new HashMap<>();
        filas.forEach(r -> pendientes.put(r.getIdResponsable(), r.isPendienteDbf()));
        Map<Long, VsiafApoyoService.EstadoVsiaf> estados =
                vsiafApoyoService.estados(VsiafApoyoService.TABLA_RESP, pendientes);

        Set<String> nombres = new HashSet<>();
        nombres.add(nombreNormalizado(principal));
        for (Long id : idsPersonas) {
            personaDao.findById(id).ifPresent(p -> nombres.add(nombreNormalizado(p)));
        }

        List<Candidato> out = new ArrayList<>();
        for (Responsable r : filas) {
            Persona p = r.getPersona();
            Long idP = p != null ? p.getIdPersona() : null;
            String coincidencia;
            if (idP != null && idP.equals(principal.getIdPersona())) coincidencia = MISMA_PERSONA;
            else if (idP != null && idsPersonas.contains(idP))       coincidencia = OTRO_REGISTRO;
            else if (p != null && nombres.contains(nombreNormalizado(p))) coincidencia = MISMO_NOMBRE;
            else                                                       coincidencia = OTRO;
            VsiafApoyoService.EstadoVsiaf e = estados.get(r.getIdResponsable());
            String codigo = e != null ? e.codigo() : VsiafApoyoService.EST_VSIAF;
            boolean elegible = VsiafApoyoService.EST_VSIAF.equals(codigo) || VsiafApoyoService.EST_EN_COLA.equals(codigo);
            out.add(new Candidato(r.getIdResponsable(), idP, p != null ? nombreCompleto(p) : r.getCodigoFuncionario(),
                    p != null ? p.getCi() : null, r.getCargo() != null ? r.getCargo().getNombre() : null,
                    r.getCodigoFuncionario(), coincidencia, e != null ? e.texto() : "En el VSIAF", elegible));
        }
        List<String> orden = List.of(MISMA_PERSONA, OTRO_REGISTRO, MISMO_NOMBRE, OTRO);
        out.sort(Comparator.comparing((Candidato c) -> orden.indexOf(c.coincidencia()))
                .thenComparing(Candidato::nombre, Comparator.nullsLast(String::compareTo)));
        return out;
    }

    private static boolean esParecido(String coincidencia) {
        return OTRO_REGISTRO.equals(coincidencia) || MISMO_NOMBRE.equals(coincidencia);
    }

    private static String describir(Candidato c) {
        return c.nombre() + " (C.I. " + (c.ci() != null && !c.ci().isBlank() ? c.ci() : "s/d")
                + ", código " + c.codigo() + ")";
    }

    private static String nombreCompleto(Persona p) {
        return java.util.stream.Stream.of(p.getNombre(), p.getPaterno(), p.getMaterno())
                .filter(s -> s != null && !s.isBlank()).map(String::trim)
                .collect(java.util.stream.Collectors.joining(" "));
    }

    /** Para comparar nombres: sin tildes, en mayúsculas y con un solo espacio. */
    static String nombreNormalizado(Persona p) {
        String s = Normalizer.normalize(nombreCompleto(p), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return s.toUpperCase(java.util.Locale.ROOT).replaceAll("\\s+", " ").trim();
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
        return asegurar(idResponsableOriginal, null, false, autor);
    }

    /**
     * @param idPersonaDestino quién queda en la oficina de faltantes; null = la persona del
     *                         responsable original. Es la de la notificación: si quien registró
     *                         sumó otro registro de la misma persona, sus bienes van a la principal
     * @param permitirHomonimo dar de alta aunque en la oficina haya alguien con el mismo nombre
     *                         (quien registró indicó que es otra persona)
     */
    public Custodia asegurar(Long idResponsableOriginal, Long idPersonaDestino, boolean permitirHomonimo,
                             Usuario autor) {
        Alta alta;
        try {
            alta = enTransaccion(() -> prepararAlta(idResponsableOriginal, idPersonaDestino, permitirHomonimo, autor));
        } catch (DataIntegrityViolationException carrera) {
            // Otro pedido creó la misma oficina o el mismo responsable al mismo tiempo
            // (los índices únicos lo frenaron): ahora ya existe y se reutiliza.
            log.info("[CUSTODIA] Alta concurrente para el responsable {}; se reintenta: {}",
                    idResponsableOriginal, carrera.getMostSpecificCause().getMessage());
            alta = enTransaccion(() -> prepararAlta(idResponsableOriginal, idPersonaDestino, permitirHomonimo, autor));
        }
        Alta hecha = alta;
        Custodia custodia = enTransaccion(() -> enviarAlVsiaf(hecha, autor));
        registrarActividad(hecha, autor);
        return custodia;
    }

    private Alta prepararAlta(Long idResponsableOriginal, Long idPersonaDestino, boolean permitirHomonimo,
                              Usuario autor) {
        Responsable original = cargarOriginal(idResponsableOriginal);
        Persona persona = idPersonaDestino != null
                ? personaDao.findById(idPersonaDestino)
                        .orElseThrow(() -> new ReglaNegocioException("La persona de la notificación ya no existe."))
                : original.getPersona();
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
        Responsable custodio = responsableDe(oficina, persona);
        if (custodio == null) {
            if (!permitirHomonimo) exigirSinHomonimo(oficina, persona);
            custodio = nuevoResponsable(oficina, original, persona, autor);
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

    /**
     * Barrera contra el duplicado: si en la oficina ya hay alguien con el mismo nombre (otro
     * registro de la misma persona, típico de la sincronización), no se da de alta un segundo.
     * Quien registra elige a quién van los bienes o indica que es otra persona.
     */
    private void exigirSinHomonimo(Oficina oficina, Persona persona) {
        String nombre = nombreNormalizado(persona);
        responsableDao.findByOficinaIdOficina(oficina.getIdOficina()).stream()
                .filter(r -> r.getPersona() != null
                        && !Objects.equals(r.getPersona().getIdPersona(), persona.getIdPersona())
                        && nombre.equals(nombreNormalizado(r.getPersona())))
                .findFirst()
                .ifPresent(r -> {
                    throw new ReglaNegocioException("En la oficina de faltantes " + OficinaGestionService.referencia(oficina)
                            + " ya está " + nombreCompleto(r.getPersona()) + " (C.I. "
                            + (r.getPersona().getCi() != null && !r.getPersona().getCi().isBlank() ? r.getPersona().getCi() : "s/d")
                            + ", código " + r.getCodigoFuncionario() + ") con el mismo nombre. No se dio de alta a otro. "
                            + "Pulse Reintentar; si vuelve a fallar, anule esta notificación y regístrela de nuevo "
                            + "eligiendo a quién van los bienes.");
                });
    }

    /** La misma persona, con su cargo real, como responsable dentro de la oficina de faltantes. */
    private Responsable nuevoResponsable(Oficina oficina, Responsable original, Persona persona, Usuario autor) {
        int codigo = siguienteCodResp(oficina);
        if (codigo > ResponsableAltaService.MAX_COD_RESP) {
            throw new ReglaNegocioException("La oficina de faltantes " + OficinaGestionService.referencia(oficina)
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
        r.setEsCustodia(true);
        r.setEstado("ACTIVO");
        if (autor != null) r.setRegistroIdUsuario(autor.getIdUsuario());
        responsableDao.saveAndFlush(r);
        log.info("[CUSTODIA] {} registrado en la oficina de faltantes {} (código {})",
                nombreCompleto(persona), OficinaGestionService.referencia(oficina), codigo);
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
                    + ". Alta encolada: se espera la confirmación del worker VSIAF.";
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
