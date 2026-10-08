package com.usic.SistemasActivosFijosUAP.model.service.control;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.usic.SistemasActivosFijosUAP.model.dao.IActaFaltanteDao;
import com.usic.SistemasActivosFijosUAP.model.dao.IActivoDao;
import com.usic.SistemasActivosFijosUAP.model.dao.IConfiguracionGestionDao;
import com.usic.SistemasActivosFijosUAP.model.dao.IHallazgoInventarioDao;
import com.usic.SistemasActivosFijosUAP.model.dao.IOficinaDao;
import com.usic.SistemasActivosFijosUAP.model.dao.IPersonasDao;
import com.usic.SistemasActivosFijosUAP.model.dto.control.ActaFaltanteDTO;
import com.usic.SistemasActivosFijosUAP.model.dto.control.ActaResumenDTO;
import com.usic.SistemasActivosFijosUAP.model.dto.control.BienPersonaDTO;
import com.usic.SistemasActivosFijosUAP.model.dto.control.CustodiaDTOs;
import com.usic.SistemasActivosFijosUAP.model.dto.control.PersonaFaltanteDTO;
import com.usic.SistemasActivosFijosUAP.model.dto.control.RegistrarFaltantesRequest;
import com.usic.SistemasActivosFijosUAP.model.entity.ActaFaltante;
import com.usic.SistemasActivosFijosUAP.model.entity.Activo;
import com.usic.SistemasActivosFijosUAP.model.entity.ConfiguracionGestion;
import com.usic.SistemasActivosFijosUAP.model.entity.HallazgoInventario;
import com.usic.SistemasActivosFijosUAP.model.entity.Oficina;
import com.usic.SistemasActivosFijosUAP.model.entity.Persona;
import com.usic.SistemasActivosFijosUAP.model.entity.Responsable;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;
import com.usic.SistemasActivosFijosUAP.model.repository.CustodiaFaltantesRepo;
import com.usic.SistemasActivosFijosUAP.model.service.supervision.ActividadService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Registro de faltantes de una persona y su acta.
 * <p>
 * Se eligen bienes de cualquiera de las oficinas de la persona; se registran como faltantes
 * (o se toman los que ya dejó ABIERTOS un levantamiento), se emite <b>una</b> acta con la
 * foto de lo registrado, y se pide el traslado a la custodia del predio
 * ({@link EnvioCustodiaService}). El acta sale en el momento: el traslado en el VSIAF
 * puede tardar unos segundos más.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ActaFaltanteService {

    /** Tope de bienes por acta: más que esto no es un acta, es un inventario. */
    private static final int TOPE_BIENES = 500;
    /** Plazo de la notificación, en días hábiles: lo escribe quien registra. */
    private static final int PLAZO_MAXIMO = 90;
    /** Clave del turno (pg_advisory_xact_lock) para numerar notificaciones sin repetir. */
    private static final long TURNO_NUMERACION = 0x5C1AF0001L;
    private static final String CIUDAD_POR_DEFECTO = "Cobija";
    private static final int MINIMO_BUSQUEDA = 2;
    private static final int TOPE_PERSONAS = 30;

    public static final String ANULADO = "ANULADO";
    public static final String RESUELTA = "RESUELTA";
    /** Notificación vigente cuyos faltantes pendientes pasaron a una reiterativa. */
    public static final String REITERADA = "REITERADA";

    /** Faltante que ya estaba en la oficina de faltantes del VSIAF antes del SCIAF. */
    public static final String ORIGEN_HISTORICO = "HISTORICO";
    /** Lo que se imprime como oficina de origen cuando el historial no la tiene. */
    static final String ORIGEN_NO_REGISTRADO = "No registrada";

    private final IActaFaltanteDao actaDao;
    private final IOficinaDao oficinaDao;
    private final IHallazgoInventarioDao hallazgoDao;
    private final IActivoDao activoDao;
    private final IPersonasDao personaDao;
    private final IConfiguracionGestionDao configuracionDao;
    private final CustodiaFaltantesRepo repo;
    private final EnvioCustodiaService envioService;
    private final CustodiaFaltantesService custodiaService;
    private final ActividadService actividadService;
    private final PlatformTransactionManager txManager;

    private final ObjectMapper json = new ObjectMapper();
    private final SecureRandom azar = new SecureRandom();

    public record Registro(Long idActa, String numero, int total, String mensaje) {}

    // ── Consultas para la pantalla ──────────────────────────────────────────

    public List<PersonaFaltanteDTO> buscarPersonas(String texto) {
        String q = texto == null ? "" : texto.trim();
        if (q.length() < MINIMO_BUSQUEDA) return List.of();
        return repo.buscarPersonas(q, TOPE_PERSONAS);
    }

    public List<BienPersonaDTO> bienesDePersona(Long idPersona) {
        return repo.bienesDePersona(idPersona);
    }

    public List<ActaResumenDTO> actas(Long idPersona) {
        return repo.actas(idPersona, 300);
    }

    /**
     * Para cada predio, quién está ya en su oficina de faltantes y a quién se sugiere mandar
     * los bienes. Solo lee.
     */
    public List<CustodiaFaltantesService.OpcionesPredio> opcionesCustodia(Long idPersona, List<Long> idsVinculadas,
                                                                         List<Long> idsPredios) {
        if (idPersona == null) throw new ReglaNegocioException("Indique la persona.");
        return soloLectura(() -> {
            Persona principal = personaDao.findById(idPersona)
                    .orElseThrow(() -> new ReglaNegocioException("La persona no existe."));
            Set<Long> idsPersonas = personasDelPedido(idPersona, idsVinculadas);
            return (idsPredios == null ? List.<Long>of() : idsPredios).stream()
                    .filter(Objects::nonNull).distinct()
                    .map(idPredio -> custodiaService.opciones(idPredio, principal, idsPersonas))
                    .toList();
        });
    }

    /** El principal y los otros registros de la misma persona que se sumaron. */
    private static Set<Long> personasDelPedido(Long idPersona, List<Long> idsVinculadas) {
        Set<Long> s = new LinkedHashSet<>();
        s.add(idPersona);
        if (idsVinculadas != null) idsVinculadas.stream().filter(Objects::nonNull).forEach(s::add);
        return s;
    }

    // ── Registro ────────────────────────────────────────────────────────────

    /**
     * Registra los faltantes, emite el acta y pide el traslado a la custodia.
     *
     * @throws ReglaNegocioException si algún bien no se puede registrar (dice cuál y por qué)
     */
    public Registro registrar(RegistrarFaltantesRequest req, Usuario autor) {
        Creada creada = enTransaccion(() -> crearActa(req, autor));
        String numero = ActaFaltante.numeroImpreso(creada.numero());

        actividadService.registrar(autor, ActividadService.MOD_ACTIVO, ActividadService.ACC_REGISTRO,
                creada.numero(), "Registró la notificación de faltantes " + numero + " de " + creada.persona()
                        + ": " + creada.idsHallazgo().size() + " bien(es)" + creada.detalle(),
                creada.idsHallazgo().size(), creada.idActa());

        // La notificación ya existe pase lo que pase con el VSIAF: lo que falle queda en cada faltante.
        envioService.iniciar(creada.idsHallazgo(), autor, creada.conHomonimoPermitido());

        return new Registro(creada.idActa(), numero, creada.idsHallazgo().size(),
                "Notificación " + numero + " registrada con " + creada.idsHallazgo().size()
                        + " bien(es). El traslado a la custodia continuará cuando el worker confirme el alta de la oficina y del responsable.");
    }

    /**
     * @param conHomonimoPermitido faltantes cuyo predio puede recibir a la persona aunque haya
     *                             alguien con su nombre en la oficina de faltantes
     * @param detalle              para el monitoreo: registros sumados y destinos elegidos a mano
     */
    private record Creada(Long idActa, String numero, String persona, List<Long> idsHallazgo,
                          Set<Long> conHomonimoPermitido, String detalle) {}

    private Creada crearActa(RegistrarFaltantesRequest req, Usuario autor) {
        Preparado p = preparar(req, "No se registró nada. Revise estos bienes:");
        Persona persona = p.persona();
        List<Activo> ordenados = p.ordenados();
        Map<Long, HallazgoInventario> previos = p.previos();
        LocalDateTime ahora = LocalDateTime.now();
        // Antes de tomar el turno y de insertar: si falta el firmante, no se toca nada.
        Map<String, Object> notificacion = datosNotificacion(req.plazoDias(), ahora, ordenados);
        notificacion.put("inicioPlazo", ahora.withNano(0).toString());
        // Aplicar override de unidad si se proporciona (para el documento, no actualiza BD)
        if (vacioANull(req.personaUnidadOverride()) != null) {
            notificacion.put("unidad", req.personaUnidadOverride());
        }

        // Turno para el correlativo de la gestión: lo tiene esta transacción hasta confirmar.
        actaDao.turnoNumeracion(TURNO_NUMERACION);
        String numero = String.format("%s%03d/%d", ActaFaltante.PREFIJO_NOTIFICACION,
                actaDao.ultimoCorrelativo(String.valueOf(ahora.getYear())) + 1, ahora.getYear());

        ActaFaltante acta = armarActa(req, p, autor, ahora);
        // contenido y hash son NOT NULL y dependen del número, que sale del id: primero un borrador.
        acta.setContenido("{}");
        acta.setHashContenido("-");
        actaDao.saveAndFlush(acta);

        acta.setNumero(numero);
        acta.setContenido(contenido(acta, persona, ordenados, ActaFaltante.TIPO_FALTANTES, Activo::getOficina,
                notificacion));
        acta.setHashContenido(sha256(acta.getContenido()));

        List<Long> idsHallazgo = new ArrayList<>();
        Set<Long> conHomonimoPermitido = new HashSet<>();
        Set<Long> reactivados = new HashSet<>();
        for (Activo a : ordenados) {
            HallazgoInventario h = previos.get(a.getIdActivo());
            if (h == null) {
                h = new HallazgoInventario();
                h.setOrigen(ControlActivosService.ORIGEN_DIRECTO);
                h.setTipoHallazgo(ControlActivosService.TIPO_FALTANTE);
                h.setEstadoHallazgo(ControlActivosService.ABIERTO);
                h.setActivo(a);
                h.setCodigoFisico(a.getCodigo());
                h.setDescripcionFisica(recortar(a.getDescripcion(), 1024));
                h.setDescripcionDiscrepancia("Registrado como faltante en la notificación "
                        + ActaFaltante.numeroImpreso(acta.getNumero()));
                h.setEstado("ACTIVO");
                if (autor != null) h.setRegistroIdUsuario(autor.getIdUsuario());
            }
            if (h.getResponsable() == null) h.setResponsable(a.getResponsable());
            if (h.getOficinaOrigen() == null) h.setOficinaOrigen(a.getOficina());
            h.setActa(acta);
            h.setDocumentoRespaldo(acta.getDocumentoRespaldo());
            h.setFechaDocumento(acta.getFechaDocumento());
            h.setEstadoEnvio(EnvioCustodiaService.ESPERANDO_ALTA);
            h.setMensajeEnvio(null);
            // Destino elegido a mano: el ciclo de envío no busca ni crea custodia para este faltante.
            CustodiaFaltantesService.Destino destino = p.destinos().get(a.getOficina().getPredio().getIdPredio());
            if (destino != null && destino.custodio() != null) {
                Responsable custodio = destino.custodio();
                if (reactivados.add(custodio.getIdResponsable())) custodiaService.reactivarSiHaceFalta(custodio, autor);
                h.setResponsableCustodia(custodio);
            }
            hallazgoDao.save(h);
            idsHallazgo.add(h.getIdHallazgo());
            if (destino != null && destino.permitirHomonimo()) conHomonimoPermitido.add(h.getIdHallazgo());
        }
        hallazgoDao.flush();

        log.info("[ACTA-FALTANTES] {} emitida para {} con {} bien(es)", acta.getNumero(), acta.getPersonaNombre(), ordenados.size());
        return new Creada(acta.getIdActa(), acta.getNumero(), acta.getPersonaNombre(), idsHallazgo,
                conHomonimoPermitido, detalleMonitoreo(p));
    }

    /** Lo que se salió de lo normal, para que quede en el monitoreo. */
    private String detalleMonitoreo(Preparado p) {
        List<String> partes = new ArrayList<>();
        if (p.idsPersonas().size() > 1) {
            partes.add("sumó otro(s) registro(s) de la misma persona: id_persona "
                    + p.idsPersonas().stream().filter(id -> !id.equals(p.persona().getIdPersona()))
                            .map(String::valueOf).collect(Collectors.joining(", ")));
        }
        p.destinos().values().stream().filter(d -> d.custodio() != null)
                .map(d -> d.custodio().getIdResponsable()).distinct()
                .forEach(id -> partes.add("custodia elegida: responsable " + id));
        if (p.destinos().values().stream().anyMatch(CustodiaFaltantesService.Destino::permitirHomonimo)) {
            partes.add("indicó que es otra persona que el homónimo de la oficina de faltantes");
        }
        return partes.isEmpty() ? "" : " (" + String.join("; ", partes) + ")";
    }

    /**
     * Lo validado y ordenado para emitir: lo usan igual el registro y la vista previa.
     *
     * @param idsPersonas el principal y los otros registros de la misma persona
     * @param destinos    id del predio → a quién de su oficina de faltantes van los bienes
     */
    private record Preparado(Persona persona, List<Activo> ordenados, Map<Long, HallazgoInventario> previos,
                             Set<Long> idsPersonas, Map<Long, CustodiaFaltantesService.Destino> destinos) {}

    /**
     * Valida el pedido y los bienes (solo lee) y los ordena como van en el documento.
     *
     * @param encabezado cómo empieza el mensaje cuando hay bienes que no se pueden incluir
     * @throws ReglaNegocioException con todo lo que falta o sobra
     */
    private Preparado preparar(RegistrarFaltantesRequest req, String encabezado) {
        if (req == null || req.idPersona() == null) throw new ReglaNegocioException("Indique la persona.");
        List<Long> ids = req.idsActivos() == null ? List.of()
                : req.idsActivos().stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) throw new ReglaNegocioException("Seleccione al menos un bien.");
        if (ids.size() > TOPE_BIENES) {
            throw new ReglaNegocioException("Un acta admite hasta " + TOPE_BIENES + " bienes; se eligieron " + ids.size() + ".");
        }
        if (req.plazoDias() == null || req.plazoDias() < 1 || req.plazoDias() > PLAZO_MAXIMO) {
            throw new ReglaNegocioException("Escriba el plazo para responder, en días hábiles (de 1 a " + PLAZO_MAXIMO + ").");
        }
        Persona persona = personaDao.findById(req.idPersona())
                .orElseThrow(() -> new ReglaNegocioException("La persona no existe."));
        Set<Long> idsPersonas = personasDelPedido(persona.getIdPersona(), req.idsPersonasVinculadas());
        validarVinculadas(persona, idsPersonas);

        Map<Long, Activo> activos = activoDao.findAllById(ids).stream()
                .collect(Collectors.toMap(Activo::getIdActivo, Function.identity()));
        List<String> problemas = new ArrayList<>();
        Map<Long, HallazgoInventario> previos = new LinkedHashMap<>();
        for (Long id : ids) {
            Activo a = activos.get(id);
            if (a == null) { problemas.add("#" + id + ": no existe"); continue; }
            String p = problemaParaRegistrar(a, persona, idsPersonas);
            if (p == null) {
                List<HallazgoInventario> pend = hallazgoDao.pendientesDelActivo(id);
                if (!pend.isEmpty()) {
                    HallazgoInventario h = pend.get(0);
                    if (h.getActa() != null) {
                        String n = h.getActa().getNumero();
                        p = "ya está en " + (n != null && n.startsWith(ActaFaltante.PREFIJO_NOTIFICACION)
                                ? "la notificación " : "el acta ") + ActaFaltante.numeroImpreso(n);
                    }
                    else if (!ControlActivosService.ABIERTO.equals(h.getEstadoHallazgo())) p = "ya está en custodia";
                    else previos.put(id, h);
                }
            }
            if (p != null) problemas.add(a.getCodigo() + ": " + p);
        }
        if (!problemas.isEmpty()) {
            throw new ReglaNegocioException(encabezado + "\n• " + String.join("\n• ", problemas));
        }

        List<Activo> ordenados = ids.stream().map(activos::get)
                .sorted(Comparator.comparing((Activo a) -> a.getOficina().getPredio().getDescrip(), Comparator.nullsLast(String::compareTo))
                        .thenComparing(a -> a.getOficina().getCodOfi(), Comparator.nullsLast(Short::compareTo))
                        .thenComparing(Activo::getCodigo, Comparator.nullsLast(String::compareTo)))
                .toList();

        // A quién de la oficina de faltantes de cada predio van los bienes.
        Map<Long, RegistrarFaltantesRequest.DestinoCustodia> elegidos = new HashMap<>();
        if (req.destinos() != null) {
            req.destinos().stream().filter(d -> d != null && d.idPredio() != null)
                    .forEach(d -> elegidos.put(d.idPredio(), d));
        }
        Map<Long, CustodiaFaltantesService.Destino> destinos = new LinkedHashMap<>();
        for (Activo a : ordenados) {
            var predio = a.getOficina().getPredio();
            if (destinos.containsKey(predio.getIdPredio())) continue;
            try {
                destinos.put(predio.getIdPredio(), custodiaService.resolverDestino(predio.getIdPredio(), persona,
                        idsPersonas, elegidos.get(predio.getIdPredio())));
            } catch (ReglaNegocioException e) {
                problemas.add("Predio " + predio.getUnidad() + ": " + e.getMessage());
            }
        }
        if (!problemas.isEmpty()) {
            throw new ReglaNegocioException(encabezado + "\n• " + String.join("\n• ", problemas));
        }
        return new Preparado(persona, ordenados, previos, idsPersonas, destinos);
    }

    /**
     * Los registros sumados existen y la notificación sale a nombre del que tiene C.I.
     * (acordado con Activos Fijos: no debe quedar en faltantes un responsable sin C.I.).
     */
    private void validarVinculadas(Persona principal, Set<Long> idsPersonas) {
        if (idsPersonas.size() == 1) return;
        String ciPrincipal = ciLimpio(principal.getCi());
        for (Long id : idsPersonas) {
            if (id.equals(principal.getIdPersona())) continue;
            Persona otra = personaDao.findById(id)
                    .orElseThrow(() -> new ReglaNegocioException("Uno de los registros sumados ya no existe (id " + id + ")."));
            String ciOtra = ciLimpio(otra.getCi());
            if (ciOtra != null && ciPrincipal == null) {
                throw new ReglaNegocioException("La notificación debe salir a nombre del registro con C.I.: "
                        + otra.getNombreCompleto() + " (C.I. " + otra.getCi() + ").");
            }
            if (ciOtra != null && !ciOtra.equals(ciPrincipal)) {
                throw new ReglaNegocioException(otra.getNombreCompleto() + " tiene otro C.I. (" + otra.getCi()
                        + "): es otra persona y no se puede sumar a esta notificación.");
            }
            // La pantalla ya pide confirmar si los nombres difieren; acá se frena lo que no tiene
            // relación. Dos palabras en común toleran el nombre recortado del VSIAF (35 letras).
            if (palabrasEnComun(principal, otra) < 2) {
                throw new ReglaNegocioException(otra.getNombreCompleto() + " no se parece a " + principal.getNombreCompleto()
                        + ": no se puede sumar como otro registro de la misma persona.");
            }
        }
    }

    private static long palabrasEnComun(Persona a, Persona b) {
        Set<String> pa = new HashSet<>(java.util.Arrays.asList(CustodiaFaltantesService.nombreNormalizado(a).split(" ")));
        return java.util.Arrays.stream(CustodiaFaltantesService.nombreNormalizado(b).split(" "))
                .filter(w -> w.length() > 1 && pa.contains(w)).distinct().count();
    }

    private static String ciLimpio(String ci) {
        if (ci == null) return null;
        String s = ci.replaceAll("[.\\-\\s]", "").toUpperCase(java.util.Locale.ROOT);
        return s.isEmpty() ? null : s;
    }

    /** El acta en memoria con los datos de la persona y del pedido (sin número, sin contenido, sin guardar). */
    private ActaFaltante armarActa(RegistrarFaltantesRequest req, Preparado p, Usuario autor, LocalDateTime ahora) {
        ActaFaltante acta = new ActaFaltante();
        acta.setToken(nuevoToken());
        acta.setPersona(p.persona());
        // Usar override si se proporciona, si no, el nombre de la persona en BD
        String nombreParaDocumento = vacioANull(req.personaNombreOverride()) != null
                ? req.personaNombreOverride()
                : p.persona().getNombreCompleto();
        acta.setPersonaNombre(recortar(nombreParaDocumento, 160));
        acta.setPersonaCi(recortar(p.persona().getCi(), 20));
        // Usar override si se proporciona, si no, el cargo calculado
        String cargoParaDocumento = vacioANull(req.personaCargoOverride()) != null
                ? req.personaCargoOverride()
                : cargoPrincipal(p.ordenados());
        acta.setPersonaCargo(recortar(cargoParaDocumento, 120));
        acta.setFechaEmision(ahora);
        acta.setUsuarioEmision(autor != null ? autor.getUsuario() : "SISTEMA");
        acta.setDocumentoRespaldo(recortar(vacioANull(req.documentoRespaldo()), 120));
        acta.setFechaDocumento(req.fechaDocumento());
        acta.setObservacion(vacioANull(req.observacion()));
        acta.setTotalBienes(p.ordenados().size());
        acta.setEstadoActa(ActaFaltante.VIGENTE);
        acta.setEstado("ACTIVO");
        if (autor != null) acta.setRegistroIdUsuario(autor.getIdUsuario());
        return acta;
    }

    // ── Vista previa ────────────────────────────────────────────────────────

    /** Estado del DTO de una vista previa (nunca se guarda: el PDF la marca como tal). */
    public static final String VISTA_PREVIA = "VISTA_PREVIA";

    /**
     * La notificación tal como saldría al registrar, <b>sin registrar nada</b>: mismas
     * validaciones, mismos datos y mismo formato, pero sin número, sin guardar, sin turno de
     * numeración y sin tocar el VSIAF.
     * <p>
     * Corre en una transacción de solo lectura que además se deshace al terminar: aunque algo
     * intentara escribir, PostgreSQL lo rechaza ("read-only transaction") y nada queda.
     *
     * @throws ReglaNegocioException lo mismo que diría el registro (bienes que no se pueden
     *         incluir, plazo, firmante sin configurar...)
     */
    public ActaFaltanteDTO vistaPrevia(RegistrarFaltantesRequest req, Usuario autor) {
        TransactionTemplate tx = new TransactionTemplate(txManager);
        tx.setReadOnly(true);
        return tx.execute(estado -> {
            estado.setRollbackOnly();
            Preparado p = preparar(req, "Estos bienes no se pueden incluir en la notificación:");
            LocalDateTime ahora = LocalDateTime.now();
            Map<String, Object> notificacion = datosNotificacion(req.plazoDias(), ahora, p.ordenados());
            // Aplicar override de unidad si se proporciona (para el documento, no actualiza BD)
            if (vacioANull(req.personaUnidadOverride()) != null) {
                notificacion.put("unidad", req.personaUnidadOverride());
            }
            ActaFaltante acta = armarActa(req, p, autor, ahora);
            acta.setNumero(ActaFaltante.PREFIJO_NOTIFICACION + "___/" + ahora.getYear());
            acta.setContenido(contenido(acta, p.persona(), p.ordenados(), ActaFaltante.TIPO_FALTANTES,
                    Activo::getOficina, notificacion));
            acta.setHashContenido(sha256(acta.getContenido()));
            return aDto(acta, VISTA_PREVIA);
        });
    }

    // ── Acciones sobre una notificación vigente ─────────────────────────────
    //
    // Cambiar el plazo, corregir sus datos y emitir la reiterativa se hacen sobre la
    // NOTIFICACIÓN COMPLETA (una por persona, con todos sus bienes), no sobre un bien suelto.
    // Antes los tres se hacían desde la fila de un bien: cambiar el plazo o corregir datos
    // reescribía el documento con ese único bien (la verificación por QR mostraba 1 de 5), y la
    // reiterativa emitía un documento por bien.

    /** Una notificación sobre la que se puede actuar: vigente, con plazo y con faltantes pendientes. */
    private record NotificacionVigente(ActaFaltante acta, Persona persona, JsonNode notificacion,
                                       List<HallazgoInventario> pendientes) {}

    /**
     * @param bloquear true en las acciones que escriben: los faltantes quedan bloqueados hasta el
     *                 fin de la transacción (como en anular), así no se pisan con el despacho a la
     *                 custodia ni con otra acción sobre la misma notificación. Quien llama ya tomó el
     *                 turno de las notificaciones ({@link #turnoNotificaciones()}).
     */
    private NotificacionVigente notificacionVigente(Long idActa, boolean bloquear) {
        if (idActa == null) throw new ReglaNegocioException("Indique la notificación.");
        ActaFaltante acta = actaDao.findById(idActa)
                .orElseThrow(() -> new ReglaNegocioException("La notificación no existe."));
        String numero = ActaFaltante.numeroImpreso(acta.getNumero());
        if (ActaFaltante.ANULADA.equals(acta.getEstadoActa())) {
            throw new ReglaNegocioException("La notificación " + numero + " está anulada.");
        }
        JsonNode not = nodoNotificacion(acta.getContenido());
        if (not == null || !not.hasNonNull("plazoDiasHabiles")) {
            throw new ReglaNegocioException(numero + " es un acta, no una notificación con plazo.");
        }
        List<HallazgoInventario> todos;
        if (bloquear) {
            List<Long> ids = hallazgoDao.idsDeLaActa(idActa);
            todos = ids.isEmpty() ? List.of() : hallazgoDao.bloquear(ids);
        } else {
            todos = hallazgoDao.deLaActa(idActa);
        }
        List<HallazgoInventario> pendientes = todos.stream()
                .filter(h -> h.getActivo() != null && ControlActivosService.PENDIENTES.contains(h.getEstadoHallazgo()))
                .toList();
        if (pendientes.isEmpty()) {
            throw new ReglaNegocioException("La notificación " + numero + " ya no tiene faltantes pendientes "
                    + "(se resolvieron o pasaron a una reiterativa).");
        }
        if (acta.getPersona() == null) {
            throw new ReglaNegocioException("La notificación " + numero + " no tiene persona asociada.");
        }
        return new NotificacionVigente(acta, acta.getPersona(), not, pendientes);
    }

    /**
     * Turno de las acciones sobre notificaciones (el mismo del correlativo): dos personas que
     * actúan a la vez sobre la misma notificación quedan en fila, y la segunda ve lo que hizo la
     * primera. Sin esto, dos «emitir reiterativa» simultáneos sacaban dos «primera reiterativa».
     */
    private void turnoNotificaciones() {
        actaDao.turnoNumeracion(TURNO_NUMERACION);
    }

    private JsonNode nodoNotificacion(String contenido) {
        if (contenido == null || contenido.isBlank()) return null;
        try {
            JsonNode n = json.readTree(contenido).path("notificacion");
            return n.isObject() ? n : null;
        } catch (Exception e) {
            log.warn("[ACTA-FALTANTES] Contenido ilegible: {}", e.getMessage());
            return null;
        }
    }

    private static void validarPlazo(Integer plazo) {
        if (plazo == null || plazo < 1 || plazo > PLAZO_MAXIMO) {
            throw new ReglaNegocioException("Escriba el plazo para responder, en días hábiles (de 1 a " + PLAZO_MAXIMO + ").");
        }
    }

    /**
     * El contenido nuevo de una notificación que se vuelve a emitir con el mismo número: la
     * MISMA foto de bienes (tal como se notificaron), con los datos de respaldo y el bloque de
     * la notificación nuevos. No se rearma desde los bienes de hoy: el papel tiene que seguir
     * diciendo lo que se notificó.
     */
    @SuppressWarnings("unchecked")
    private String contenidoReemitido(ActaFaltante acta, Map<String, Object> notificacion) {
        try {
            Map<String, Object> raiz = json.readValue(acta.getContenido(), LinkedHashMap.class);
            raiz.put("documentoRespaldo", acta.getDocumentoRespaldo());
            raiz.put("fechaDocumento", acta.getFechaDocumento() != null ? acta.getFechaDocumento().toString() : null);
            raiz.put("observacion", acta.getObservacion());
            raiz.put("notificacion", notificacion);
            return json.writeValueAsString(raiz);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo rearmar la notificación: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> comoMapa(JsonNode n) {
        return n == null ? new LinkedHashMap<>() : json.convertValue(n, LinkedHashMap.class);
    }

    /** Copia sin guardar, para las vistas previas (no se toca la entidad administrada). */
    private static ActaFaltante copia(ActaFaltante a) {
        ActaFaltante c = new ActaFaltante();
        c.setIdActa(a.getIdActa());
        c.setNumero(a.getNumero());
        c.setToken(a.getToken());
        c.setPersona(a.getPersona());
        c.setPersonaNombre(a.getPersonaNombre());
        c.setPersonaCi(a.getPersonaCi());
        c.setPersonaCargo(a.getPersonaCargo());
        c.setFechaEmision(a.getFechaEmision());
        c.setUsuarioEmision(a.getUsuarioEmision());
        c.setDocumentoRespaldo(a.getDocumentoRespaldo());
        c.setFechaDocumento(a.getFechaDocumento());
        c.setObservacion(a.getObservacion());
        c.setTotalBienes(a.getTotalBienes());
        c.setEstadoActa(a.getEstadoActa());
        c.setEstado(a.getEstado());
        c.setTipoNotificacion(a.getTipoNotificacion());
        c.setActaAnterior(a.getActaAnterior());
        c.setNumeroReiterativa(a.getNumeroReiterativa());
        c.setContenido(a.getContenido());
        c.setHashContenido(a.getHashContenido());
        return c;
    }

    // ── Cambiar el plazo (mismo número; el plazo corre desde hoy) ───────────

    /**
     * Vuelve a emitir la notificación vigente con un plazo nuevo que corre desde hoy: el papel
     * sale con la fecha de hoy y el nuevo plazo (decisión del usuario, 05-oct-2026). Mismo
     * número y misma lista de bienes. <b>No toca el VSIAF</b>.
     */
    public Registro notificarPlazo(CustodiaDTOs.NotificarPlazoRequest req, Usuario autor) {
        if (req == null) throw new ReglaNegocioException("Faltan datos.");
        validarPlazo(req.plazoDias());
        return enTransaccion(() -> {
            turnoNotificaciones();
            NotificacionVigente nv = notificacionVigente(req.idActa(), true);
            ActaFaltante acta = nv.acta();
            Integer anterior = nv.notificacion().path("plazoDiasHabiles").asInt();
            aplicarPlazo(acta, nv, req.plazoDias(), req.documentoRespaldo(), req.fechaDocumento(), req.observacion(),
                    req.personaNombreOverride(), req.personaCargoOverride(), req.personaUnidadOverride(), LocalDateTime.now());
            if (autor != null) acta.setModificacionIdUsuario(autor.getIdUsuario());
            actaDao.save(acta);
            respaldoEnHallazgos(nv.pendientes(), acta);

            String numero = ActaFaltante.numeroImpreso(acta.getNumero());
            actividadService.registrar(autor, ActividadService.MOD_ACTIVO, ActividadService.ACC_MODIFICACION,
                    acta.getNumero(), "Cambió el plazo de la notificación " + numero + " de " + anterior + " a "
                            + req.plazoDias() + " día(s) hábil(es), desde hoy", nv.pendientes().size(), acta.getIdActa());
            return new Registro(acta.getIdActa(), numero, nv.pendientes().size(),
                    "Notificación " + numero + " actualizada: " + req.plazoDias()
                            + " día(s) hábil(es) desde hoy. Reimprímala y entréguela.");
        });
    }

    public ActaFaltanteDTO notificarPlazoVistaPrevia(CustodiaDTOs.NotificarPlazoRequest req, Usuario autor) {
        if (req == null) throw new ReglaNegocioException("Faltan datos.");
        validarPlazo(req.plazoDias());
        return soloLectura(() -> {
            NotificacionVigente nv = notificacionVigente(req.idActa(), false);
            ActaFaltante previa = copia(nv.acta());
            aplicarPlazo(previa, nv, req.plazoDias(), req.documentoRespaldo(), req.fechaDocumento(), req.observacion(),
                    req.personaNombreOverride(), req.personaCargoOverride(), req.personaUnidadOverride(), LocalDateTime.now());
            return aDto(previa, VISTA_PREVIA);
        });
    }

    private void aplicarPlazo(ActaFaltante acta, NotificacionVigente nv, int plazo, String doc,
                              java.time.LocalDate fechaDoc, String obs, String nombreOverride, String cargoOverride, String unidadOverride, LocalDateTime ahora) {
        // Se conserva lo demás de la notificación (firmante, ciudad, unidad, a quién reitera).
        Map<String, Object> not = comoMapa(nv.notificacion());
        not.put("plazoDiasHabiles", plazo);
        not.put("inicioPlazo", ahora.withNano(0).toString());
        // Aplicar overrides para el documento (no actualizan BD)
        if (vacioANull(nombreOverride) != null) acta.setPersonaNombre(recortar(nombreOverride, 160));
        if (vacioANull(cargoOverride) != null) acta.setPersonaCargo(recortar(cargoOverride, 120));
        if (vacioANull(unidadOverride) != null) not.put("unidad", unidadOverride);
        conservarOReemplazar(acta, doc, fechaDoc, obs);
        acta.setContenido(contenidoReemitido(acta, not));
        acta.setHashContenido(sha256(acta.getContenido()));
    }

    // ── Corregir datos (sin cambiar el plazo ni su fecha) ───────────────────

    /**
     * Corrige el documento de respaldo, su fecha y la observación de la notificación vigente.
     * El plazo y desde cuándo corre no cambian. Queda en la auditoría.
     */
    public Registro regenerarNotificacion(CustodiaDTOs.RegenerarNotificacionRequest req, Usuario autor) {
        if (req == null) throw new ReglaNegocioException("Faltan datos.");
        return enTransaccion(() -> {
            turnoNotificaciones();
            NotificacionVigente nv = notificacionVigente(req.idActa(), true);
            ActaFaltante acta = nv.acta();
            aplicarCorreccion(acta, nv, req.documentoRespaldo(), req.fechaDocumento(), req.observacion(),
                    req.personaNombreOverride(), req.personaCargoOverride(), req.personaUnidadOverride());
            if (autor != null) acta.setModificacionIdUsuario(autor.getIdUsuario());
            actaDao.save(acta);
            respaldoEnHallazgos(nv.pendientes(), acta);

            String numero = ActaFaltante.numeroImpreso(acta.getNumero());
            actividadService.registrar(autor, ActividadService.MOD_ACTIVO, ActividadService.ACC_MODIFICACION,
                    acta.getNumero(), "Corrigió los datos de la notificación " + numero + " (documento: "
                            + nvl(acta.getDocumentoRespaldo()) + "; observación: " + nvl(acta.getObservacion()) + ")",
                    nv.pendientes().size(), acta.getIdActa());
            return new Registro(acta.getIdActa(), numero, nv.pendientes().size(),
                    "Notificación " + numero + " corregida. El plazo no cambió.");
        });
    }

    public ActaFaltanteDTO regenerarNotificacionVistaPrevia(CustodiaDTOs.RegenerarNotificacionRequest req, Usuario autor) {
        if (req == null) throw new ReglaNegocioException("Faltan datos.");
        return soloLectura(() -> {
            NotificacionVigente nv = notificacionVigente(req.idActa(), false);
            ActaFaltante previa = copia(nv.acta());
            aplicarCorreccion(previa, nv, req.documentoRespaldo(), req.fechaDocumento(), req.observacion(),
                    req.personaNombreOverride(), req.personaCargoOverride(), req.personaUnidadOverride());
            return aDto(previa, VISTA_PREVIA);
        });
    }

    /**
     * Lo que se escribe reemplaza; lo que se deja vacío se conserva. Antes cambiar solo el plazo
     * borraba del papel el documento de respaldo, su fecha y la observación.
     */
    private static void conservarOReemplazar(ActaFaltante acta, String doc, java.time.LocalDate fechaDoc, String obs) {
        if (vacioANull(doc) != null) acta.setDocumentoRespaldo(recortar(vacioANull(doc), 120));
        if (fechaDoc != null) acta.setFechaDocumento(fechaDoc);
        if (vacioANull(obs) != null) acta.setObservacion(vacioANull(obs));
    }

    private void aplicarCorreccion(ActaFaltante acta, NotificacionVigente nv, String doc,
                                   java.time.LocalDate fechaDoc, String obs, String nombreOverride, String cargoOverride, String unidadOverride) {
        // Aplicar overrides para el documento (no actualizan BD)
        if (vacioANull(nombreOverride) != null) acta.setPersonaNombre(recortar(nombreOverride, 160));
        if (vacioANull(cargoOverride) != null) acta.setPersonaCargo(recortar(cargoOverride, 120));
        if (vacioANull(unidadOverride) != null) {
            Map<String, Object> not = comoMapa(nv.notificacion());
            not.put("unidad", unidadOverride);
            acta.setContenido(contenidoReemitido(acta, not));
            acta.setHashContenido(sha256(acta.getContenido()));
            return;
        }
        conservarOReemplazar(acta, doc, fechaDoc, obs);
        acta.setContenido(contenidoReemitido(acta, comoMapa(nv.notificacion())));
        acta.setHashContenido(sha256(acta.getContenido()));
    }

    private void respaldoEnHallazgos(List<HallazgoInventario> hallazgos, ActaFaltante acta) {
        for (HallazgoInventario h : hallazgos) {
            h.setDocumentoRespaldo(acta.getDocumentoRespaldo());
            h.setFechaDocumento(acta.getFechaDocumento());
            hallazgoDao.save(h);
        }
    }

    // ── Reiterativa (documento nuevo, plazo nuevo) ──────────────────────────

    /**
     * Emite la notificación reiterativa de la notificación vigente: un documento NUEVO, con
     * número correlativo, que reitera al anterior, pide un plazo nuevo (corre desde hoy) y
     * lleva todos los bienes que siguen pendientes. Los faltantes pasan a ella: desde ahí es la
     * notificación vigente de la persona. Hasta 2 reiterativas (la segunda es la última).
     */
    public Registro generarReiterativa(CustodiaDTOs.GenerarReiterativaRequest req, Usuario autor) {
        if (req == null) throw new ReglaNegocioException("Faltan datos.");
        validarPlazo(req.plazoDias());
        return enTransaccion(() -> {
            // Turno ANTES de leer: si otra persona emitió la reiterativa recién, se ve acá
            // («ya no tiene faltantes pendientes») en vez de emitir una segunda «primera».
            turnoNotificaciones();
            NotificacionVigente nv = notificacionVigente(req.idActaAnterior(), true);
            LocalDateTime ahora = LocalDateTime.now();
            String numero = String.format("%s%03d/%d", ActaFaltante.PREFIJO_NOTIFICACION,
                    actaDao.ultimoCorrelativo(String.valueOf(ahora.getYear())) + 1, ahora.getYear());

            ActaFaltante nueva = armarReiterativa(nv, req, autor, ahora);
            // contenido y hash son NOT NULL y dependen del número: primero un borrador.
            nueva.setContenido("{}");
            nueva.setHashContenido("-");
            actaDao.saveAndFlush(nueva);
            nueva.setNumero(numero);
            completarReiterativa(nueva, nv, req.plazoDias(), ahora, req.personaUnidadOverride());
            actaDao.save(nueva);

            for (HallazgoInventario h : nv.pendientes()) {
                h.setActa(nueva);
                h.setDocumentoRespaldo(nueva.getDocumentoRespaldo());
                h.setFechaDocumento(nueva.getFechaDocumento());
                hallazgoDao.save(h);
            }

            String impreso = ActaFaltante.numeroImpreso(numero);
            String anterior = ActaFaltante.numeroImpreso(nv.acta().getNumero());
            String cual = nueva.getNumeroReiterativa() == 1 ? "primera reiterativa" : "segunda y última reiterativa";
            actividadService.registrar(autor, ActividadService.MOD_ACTIVO, ActividadService.ACC_REGISTRO,
                    numero, "Emitió la " + cual + " " + impreso + " (reitera a " + anterior + ") a "
                            + nueva.getPersonaNombre() + ": " + nv.pendientes().size() + " bien(es), "
                            + req.plazoDias() + " día(s) hábil(es)", nv.pendientes().size(), nueva.getIdActa());
            return new Registro(nueva.getIdActa(), impreso, nv.pendientes().size(),
                    "Notificación " + impreso + " (" + cual + " de " + anterior + ") emitida con "
                            + nv.pendientes().size() + " bien(es) y " + req.plazoDias() + " día(s) hábil(es) de plazo.");
        });
    }

    public ActaFaltanteDTO generarReiterativaVistaPrevia(CustodiaDTOs.GenerarReiterativaRequest req, Usuario autor) {
        if (req == null) throw new ReglaNegocioException("Faltan datos.");
        validarPlazo(req.plazoDias());
        return soloLectura(() -> {
            NotificacionVigente nv = notificacionVigente(req.idActaAnterior(), false);
            LocalDateTime ahora = LocalDateTime.now();
            ActaFaltante previa = armarReiterativa(nv, req, autor, ahora);
            previa.setNumero(ActaFaltante.PREFIJO_NOTIFICACION + "___/" + ahora.getYear());
            // Aplicar overrides para la vista previa
            if (vacioANull(req.personaNombreOverride()) != null) previa.setPersonaNombre(recortar(req.personaNombreOverride(), 160));
            if (vacioANull(req.personaCargoOverride()) != null) previa.setPersonaCargo(recortar(req.personaCargoOverride(), 120));
            completarReiterativa(previa, nv, req.plazoDias(), ahora, req.personaUnidadOverride());
            return aDto(previa, VISTA_PREVIA);
        });
    }

    /** La reiterativa en memoria (sin número ni contenido). */
    private ActaFaltante armarReiterativa(NotificacionVigente nv, CustodiaDTOs.GenerarReiterativaRequest req,
                                          Usuario autor, LocalDateTime ahora) {
        ActaFaltante ant = nv.acta();
        int previas = ant.getNumeroReiterativa() != null ? ant.getNumeroReiterativa() : 0;
        if (previas >= 2) {
            throw new ReglaNegocioException("La notificación " + ActaFaltante.numeroImpreso(ant.getNumero())
                    + " ya es la segunda y última reiterativa: no se emiten más.");
        }
        int siguiente = previas + 1;
        // Si la pantalla dice cuál cree que es, tiene que coincidir (otra pestaña pudo emitirla antes).
        if (req.numeroReiterativa() != null && req.numeroReiterativa() != siguiente) {
            throw new ReglaNegocioException("Ya se emitió otra reiterativa de esta notificación. Actualice la pantalla.");
        }
        ActaFaltante a = new ActaFaltante();
        a.setToken(nuevoToken());
        a.setPersona(nv.persona());
        // Usar override si se proporciona, si no, el nombre de la persona en BD
        String nombreParaDocumento = vacioANull(req.personaNombreOverride()) != null
                ? req.personaNombreOverride()
                : nv.persona().getNombreCompleto();
        a.setPersonaNombre(recortar(nombreParaDocumento, 160));
        a.setPersonaCi(recortar(nv.persona().getCi(), 20));
        // Usar override si se proporciona, si no, el cargo del acta anterior
        String cargoParaDocumento = vacioANull(req.personaCargoOverride()) != null
                ? req.personaCargoOverride()
                : ant.getPersonaCargo();
        a.setPersonaCargo(recortar(cargoParaDocumento, 120));
        a.setFechaEmision(ahora);
        a.setUsuarioEmision(autor != null ? autor.getUsuario() : "SISTEMA");
        a.setDocumentoRespaldo(recortar(vacioANull(req.documentoRespaldo()), 120));
        a.setFechaDocumento(req.fechaDocumento());
        a.setObservacion(vacioANull(req.observacion()));
        a.setTotalBienes(nv.pendientes().size());
        a.setEstadoActa(ActaFaltante.VIGENTE);
        a.setEstado("ACTIVO");
        if (autor != null) a.setRegistroIdUsuario(autor.getIdUsuario());
        a.setTipoNotificacion(siguiente == 1 ? ActaFaltante.TipoNotificacion.REITERATIVA_1.name()
                                             : ActaFaltante.TipoNotificacion.REITERATIVA_2.name());
        a.setActaAnterior(ant);
        a.setNumeroReiterativa(siguiente);
        return a;
    }

    /**
     * Contenido de la reiterativa: los bienes pendientes con su oficina de ORIGEN (los que ya
     * están en custodia figuran hoy en la oficina de faltantes, que no es de donde faltan) y la
     * referencia a la notificación que reitera.
     */
    private void completarReiterativa(ActaFaltante nueva, NotificacionVigente nv, int plazo, LocalDateTime ahora, String unidadOverride) {
        Map<Long, Oficina> origen = new java.util.HashMap<>();
        List<Activo> bienes = new ArrayList<>();
        for (HallazgoInventario h : nv.pendientes()) {
            Activo a = h.getActivo();
            if (a == null) continue;
            bienes.add(a);
            origen.put(a.getIdActivo(), h.getOficinaOrigen() != null ? h.getOficinaOrigen() : a.getOficina());
        }
        Function<Activo, Oficina> deOrigen = a -> origen.get(a.getIdActivo());
        List<Activo> ordenados = bienes.stream().sorted(ordenDocumento(deOrigen)).toList();

        ActaFaltante ant = nv.acta();
        Map<String, Object> not = datosNotificacion(plazo, ahora, ordenados, deOrigen);
        // Aplicar override de unidad si se proporciona
        if (vacioANull(unidadOverride) != null) {
            not.put("unidad", unidadOverride);
        }
        not.put("inicioPlazo", ahora.withNano(0).toString());
        not.put("numeroReiterativa", nueva.getNumeroReiterativa());
        Map<String, Object> reitera = new LinkedHashMap<>();
        reitera.put("numero", ant.getNumero());
        reitera.put("numeroImpreso", ActaFaltante.numeroImpreso(ant.getNumero()));
        JsonNode notAnt = nv.notificacion();
        reitera.put("fecha", notAnt.hasNonNull("inicioPlazo") ? notAnt.path("inicioPlazo").asText()
                : ant.getFechaEmision().withNano(0).toString());
        reitera.put("plazoDias", notAnt.path("plazoDiasHabiles").asInt());
        not.put("notificacionAnterior", reitera);

        nueva.setContenido(contenido(nueva, nv.persona(), ordenados, ActaFaltante.TIPO_FALTANTES, deOrigen, not));
        nueva.setHashContenido(sha256(nueva.getContenido()));
    }

    /** Orden de los bienes en el documento: predio, oficina (de origen) y código. */
    private static Comparator<Activo> ordenDocumento(Function<Activo, Oficina> origen) {
        return Comparator.comparing((Activo a) -> origen.apply(a) != null && origen.apply(a).getPredio() != null
                        ? origen.apply(a).getPredio().getDescrip() : null, Comparator.nullsLast(String::compareTo))
                .thenComparing(a -> origen.apply(a) != null ? origen.apply(a).getCodOfi() : null,
                        Comparator.nullsLast(Short::compareTo))
                .thenComparing(Activo::getCodigo, Comparator.nullsLast(String::compareTo));
    }

    private <T> T soloLectura(Supplier<T> trabajo) {
        TransactionTemplate tx = new TransactionTemplate(txManager);
        tx.setReadOnly(true);
        return tx.execute(estado -> {
            estado.setRollbackOnly();
            return trabajo.get();
        });
    }

    private static String nvl(String s) {
        return s == null ? "—" : s;
    }

    /**
     * Por qué este bien no puede ir en un acta de esta persona; null si puede.
     *
     * @param idsPersonas la persona y los otros registros suyos que se sumaron
     */
    private String problemaParaRegistrar(Activo a, Persona persona, Set<Long> idsPersonas) {
        if (!Activo.ESTADO_ACTIVO.equals(a.getEstado())) return "no está vigente";
        Responsable r = a.getResponsable();
        if (r == null || r.getPersona() == null || !idsPersonas.contains(r.getPersona().getIdPersona())) {
            return "no está a cargo de " + persona.getNombreCompleto();
        }
        Oficina o = a.getOficina();
        if (o == null || o.getPredio() == null) return "no tiene oficina o predio";
        if (r.isEsCustodia() || o.isEsCustodia()) return "ya está en una oficina de faltantes";
        if (Boolean.TRUE.equals(a.getBloqueado())) return "está bloqueado";
        return null;
    }

    /**
     * Lo propio de la notificación, que queda en la foto (y por lo tanto en su huella): el
     * plazo, la ciudad, quién firma por Activos Fijos y la unidad del destinatario. Se toma
     * de la configuración de la gestión el día que se emite; si después cambia, el papel ya
     * impreso y la reimpresión siguen diciendo lo mismo.
     */
    private Map<String, Object> datosNotificacion(int plazoDias, LocalDateTime fecha, List<Activo> activos) {
        return datosNotificacion(plazoDias, fecha, activos, Activo::getOficina);
    }

    /** @param origen de qué oficina falta cada bien (la unidad del destinatario sale de ahí) */
    private Map<String, Object> datosNotificacion(int plazoDias, LocalDateTime fecha, List<Activo> activos,
                                                  Function<Activo, Oficina> origen) {
        // Una gestión puede tener varias configuraciones (una por prefijo): la activa más reciente.
        ConfiguracionGestion conf = configuracionDao
                .findFirstByGestionAndEstadoOrderByIdConfigDesc(fecha.getYear(), "ACTIVO")
                .or(() -> configuracionDao.findFirstByGestionOrderByIdConfigDesc(fecha.getYear()))
                .orElse(null);
        String firmante = conf != null ? vacioANull(conf.getResponsableActivosNombre()) : null;
        if (firmante == null) {
            // Queda grabado en la notificación: sin nombre saldría para siempre sin firmante.
            throw new ReglaNegocioException("Falta el nombre del Responsable de Activos Fijos en la configuración de la gestión "
                    + fecha.getYear() + ". Cárguelo en Configuración antes de emitir la notificación.");
        }
        Map<String, Object> n = new LinkedHashMap<>();
        n.put("plazoDiasHabiles", plazoDias);
        n.put("ciudad", conf != null && conf.getCiudad() != null && !conf.getCiudad().isBlank()
                ? conf.getCiudad().trim() : CIUDAD_POR_DEFECTO);
        n.put("firmante", firmante);
        // Unidad del destinatario: la oficina donde tenía más de los bienes notificados.
        n.put("unidad", activos.stream()
                .map(a -> origen.apply(a) != null ? origen.apply(a).getNombre() : null)
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(Function.identity(), LinkedHashMap::new, Collectors.counting()))
                .entrySet().stream().max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey).orElse(null));
        return n;
    }

    /** El cargo de la fila de responsable que aporta más bienes al acta (una persona tiene uno por oficina). */
    private static String cargoPrincipal(List<Activo> activos) {
        return activos.stream()
                .map(a -> a.getResponsable().getCargo() != null ? a.getResponsable().getCargo().getNombre() : null)
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(Function.identity(), LinkedHashMap::new, Collectors.counting()))
                .entrySet().stream().max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey).orElse(null);
    }

    // ── Regularización de históricos ────────────────────────────────────────

    /**
     * Bienes que ya están en una oficina de faltantes sin acta, agrupados por la persona que
     * figura ahí, con la oficina de origen que dice el historial (si la hay).
     */
    public List<CustodiaDTOs.GrupoRegularizacion> historicos() {
        Map<Long, List<CustodiaDTOs.HistoricoFila>> porCustodio = new LinkedHashMap<>();
        for (CustodiaDTOs.HistoricoFila f : repo.historicos(null)) {
            porCustodio.computeIfAbsent(f.idResponsableCustodia(), k -> new ArrayList<>()).add(f);
        }
        List<CustodiaDTOs.GrupoRegularizacion> grupos = new ArrayList<>();
        porCustodio.forEach((idCustodio, filas) -> {
            CustodiaDTOs.HistoricoFila f0 = filas.get(0);
            grupos.add(new CustodiaDTOs.GrupoRegularizacion(idCustodio, f0.idPersona(), f0.persona(), f0.ci(),
                    f0.unidad(), f0.predio(), f0.codOfi(), f0.oficina(),
                    filas.stream().map(f -> new CustodiaDTOs.BienHistorico(f.idActivo(), f.codigo(), f.descripcion(),
                            f.codOfiOrigen(), f.oficinaOrigen(), f.fechaIngreso())).toList()));
        });
        return grupos;
    }

    /**
     * Registra en el SCIAF los faltantes que ya estaban en la oficina de faltantes del VSIAF,
     * con su acta de regularización (AR-…). <b>No mueve nada ni envía nada al VSIAF</b>: los
     * bienes ya están en custodia; quedan como faltantes EN_CUSTODIA de la persona que figura
     * en la oficina de faltantes.
     *
     * @throws ReglaNegocioException si algún bien ya no es un histórico de esa persona
     */
    public Registro regularizar(CustodiaDTOs.RegularizarRequest req, Usuario autor) {
        Creada creada = enTransaccion(() -> crearRegularizacion(req, autor));
        actividadService.registrar(autor, ActividadService.MOD_ACTIVO, ActividadService.ACC_REGISTRO,
                creada.numero(), "Regularizó " + creada.idsHallazgo().size() + " faltante(s) histórico(s) de "
                        + creada.persona() + " (acta " + creada.numero() + ")", creada.idsHallazgo().size(), creada.idActa());
        return new Registro(creada.idActa(), creada.numero(), creada.idsHallazgo().size(),
                "Acta de regularización " + creada.numero() + " con " + creada.idsHallazgo().size()
                        + " bien(es). No se movió nada: los bienes siguen en la oficina de faltantes.");
    }

    private Creada crearRegularizacion(CustodiaDTOs.RegularizarRequest req, Usuario autor) {
        if (req == null || req.idResponsableCustodia() == null) {
            throw new ReglaNegocioException("Indique de quién son los bienes a regularizar.");
        }
        List<Long> ids = req.idsActivos() == null ? List.of()
                : req.idsActivos().stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) throw new ReglaNegocioException("Seleccione al menos un bien.");
        if (ids.size() > TOPE_BIENES) {
            throw new ReglaNegocioException("Un acta admite hasta " + TOPE_BIENES + " bienes; se eligieron " + ids.size() + ".");
        }

        // Se relee de la base qué sigue siendo histórico de esa persona: la pantalla pudo quedar vieja.
        Map<Long, CustodiaDTOs.HistoricoFila> vigentes = repo.historicos(req.idResponsableCustodia()).stream()
                .collect(Collectors.toMap(CustodiaDTOs.HistoricoFila::idActivo, Function.identity()));
        List<String> problemas = new ArrayList<>();
        for (Long id : ids) {
            if (!vigentes.containsKey(id)) {
                problemas.add("#" + id + ": ya no está en la oficina de faltantes a nombre de esta persona, o ya tiene acta");
            }
        }
        if (!problemas.isEmpty()) {
            throw new ReglaNegocioException("No se registró nada. Vuelva a cargar la lista:\n• " + String.join("\n• ", problemas));
        }

        Map<Long, Activo> activos = activoDao.findAllById(ids).stream()
                .collect(Collectors.toMap(Activo::getIdActivo, Function.identity()));
        Activo primero = activos.get(ids.get(0));
        Responsable custodio = primero.getResponsable();
        Persona persona = custodio.getPersona();
        if (persona == null) {
            throw new ReglaNegocioException("El responsable de la oficina de faltantes no tiene persona registrada.");
        }

        // Oficina de origen de cada bien, según el historial (null = no registrada).
        Map<Long, Oficina> origen = new LinkedHashMap<>();
        for (Long id : ids) {
            Long idOfi = vigentes.get(id).idOficinaOrigen();
            origen.put(id, idOfi != null ? oficinaDao.findById(idOfi).orElse(null) : null);
        }

        List<Activo> ordenados = ids.stream().map(activos::get)
                .sorted(Comparator.comparing((Activo a) -> origen.get(a.getIdActivo()) != null
                                ? origen.get(a.getIdActivo()).getCodOfi() : null, Comparator.nullsLast(Short::compareTo))
                        .thenComparing(Activo::getCodigo, Comparator.nullsLast(String::compareTo)))
                .toList();

        String usuario = autor != null ? autor.getUsuario() : "SISTEMA";
        LocalDateTime ahora = LocalDateTime.now();

        ActaFaltante acta = new ActaFaltante();
        acta.setToken(nuevoToken());
        acta.setPersona(persona);
        acta.setPersonaNombre(recortar(persona.getNombreCompleto(), 160));
        acta.setPersonaCi(recortar(persona.getCi(), 20));
        acta.setPersonaCargo(recortar(custodio.getCargo() != null ? custodio.getCargo().getNombre() : null, 120));
        acta.setFechaEmision(ahora);
        acta.setUsuarioEmision(usuario);
        acta.setDocumentoRespaldo(recortar(vacioANull(req.documentoRespaldo()), 120));
        acta.setObservacion(vacioANull(req.observacion()));
        acta.setTotalBienes(ordenados.size());
        acta.setEstadoActa(ActaFaltante.VIGENTE);
        acta.setEstado("ACTIVO");
        if (autor != null) acta.setRegistroIdUsuario(autor.getIdUsuario());
        acta.setContenido("{}");
        acta.setHashContenido("-");
        actaDao.saveAndFlush(acta);

        acta.setNumero(numeroDe(ActaFaltante.TIPO_REGULARIZACION, ahora, acta.getIdActa()));
        acta.setContenido(contenido(acta, persona, ordenados, ActaFaltante.TIPO_REGULARIZACION,
                a -> origen.get(a.getIdActivo()), null));
        acta.setHashContenido(sha256(acta.getContenido()));

        List<Long> idsHallazgo = new ArrayList<>();
        for (Activo a : ordenados) {
            Oficina o = origen.get(a.getIdActivo());
            HallazgoInventario h = new HallazgoInventario();
            h.setOrigen(ORIGEN_HISTORICO);
            h.setTipoHallazgo(ControlActivosService.TIPO_FALTANTE);
            h.setEstadoHallazgo(ControlActivosService.EN_CUSTODIA);
            h.setActivo(a);
            h.setCodigoFisico(a.getCodigo());
            h.setDescripcionFisica(recortar(a.getDescripcion(), 1024));
            h.setDescripcionDiscrepancia("Faltante registrado antes en la oficina de faltantes del VSIAF; "
                    + "regularizado en el acta " + acta.getNumero());
            // De quién: la persona que figura en la oficina de faltantes (decisión del 28-sep-2026).
            h.setResponsable(custodio);
            h.setResponsableCustodia(custodio);
            // Sin origen conocido apunta a la propia oficina de faltantes: así el faltante se
            // ubica en su predio; las pantallas lo muestran como "origen no registrado".
            h.setOficinaOrigen(o != null ? o : a.getOficina());
            h.setActa(acta);
            h.setDocumentoRespaldo(acta.getDocumentoRespaldo());
            h.setEstadoEnvio(EnvioCustodiaService.CONFIRMADO);
            h.setMensajeEnvio("Ya estaba en la oficina de faltantes del VSIAF: no se trasladó.");
            h.setUsuarioEnvioCustodia(usuario);
            h.setEstado("ACTIVO");
            if (autor != null) h.setRegistroIdUsuario(autor.getIdUsuario());
            hallazgoDao.save(h);
            idsHallazgo.add(h.getIdHallazgo());
        }
        hallazgoDao.flush();

        log.info("[ACTA-FALTANTES] {} (regularización) para {} con {} bien(es)", acta.getNumero(),
                acta.getPersonaNombre(), ordenados.size());
        return new Creada(acta.getIdActa(), acta.getNumero(), acta.getPersonaNombre(), idsHallazgo, Set.of(), "");
    }

    private static String numeroDe(String tipo, LocalDateTime fecha, Long id) {
        return String.format("%s-%d-%06d", ActaFaltante.prefijo(tipo), fecha.getYear(), id);
    }

    // ── Anulación ───────────────────────────────────────────────────────────

    /**
     * Anula un acta emitida por error. Solo mientras ningún bien se haya trasladado: después
     * ya hay una transferencia en el VSIAF y lo que corresponde es resolver cada faltante.
     * Los faltantes directos quedan ANULADOS; los que vinieron de un levantamiento vuelven a
     * ABIERTO, sin acta. El acta no se borra: la verificación la muestra ANULADA.
     */
    public void anular(Long idActa, String motivo, Usuario autor) {
        String m = vacioANull(motivo);
        if (m == null) throw new ReglaNegocioException("Indique el motivo de la anulación.");
        String numero = enTransaccion(() -> {
            turnoNotificaciones();   // en fila con las reiterativas y los cambios de plazo
            ActaFaltante acta = actaDao.findById(idActa)
                    .orElseThrow(() -> new ReglaNegocioException("El acta no existe."));
            if (ActaFaltante.ANULADA.equals(acta.getEstadoActa())) {
                throw new ReglaNegocioException("El acta " + acta.getNumero() + " ya está anulada.");
            }
            // Una notificación ya reiterada no se anula: la reiterativa quedaría reiterando un papel
            // anulado y sin forma de anularse. Se anula primero la reiterativa.
            actaDao.findFirstByActaAnteriorIdActaAndEstadoActaNotOrderByIdActaDesc(idActa, ActaFaltante.ANULADA)
                    .ifPresent(r -> {
                        throw new ReglaNegocioException("La " + ActaFaltante.numeroImpreso(acta.getNumero())
                                + " fue reiterada por la " + ActaFaltante.numeroImpreso(r.getNumero())
                                + ": anule primero esa reiterativa.");
                    });
            // Se bloquea antes de leer: el despacho a la custodia puede estar moviendo estos bienes.
            List<Long> ids = hallazgoDao.idsDeLaActa(idActa);
            List<HallazgoInventario> hs = ids.isEmpty() ? List.of() : hallazgoDao.bloquear(ids);
            ActaFaltante reiterada = acta.getActaAnterior();
            if (reiterada != null) {
                // Una reiterativa no movió ningún bien: al anularla, sus faltantes vuelven a la
                // notificación que reiteraba (que vuelve a ser la vigente). No se les toca el estado
                // ni el traslado a la custodia.
                long resueltos = hs.stream().filter(h -> ControlActivosService.RESUELTO.equals(h.getEstadoHallazgo())).count();
                if (resueltos > 0) {
                    throw new ReglaNegocioException(resueltos + " faltante(s) de la reiterativa ya se resolvieron: "
                            + "no se puede anular.");
                }
                if (ActaFaltante.ANULADA.equals(reiterada.getEstadoActa())) {
                    throw new ReglaNegocioException("La notificación que reitera está anulada: no hay a dónde devolver los faltantes.");
                }
                for (HallazgoInventario h : hs) {
                    h.setActa(reiterada);
                    h.setDocumentoRespaldo(reiterada.getDocumentoRespaldo());
                    h.setFechaDocumento(reiterada.getFechaDocumento());
                }
                acta.setEstadoActa(ActaFaltante.ANULADA);
                acta.setMotivoAnulacion(m);
                acta.setFechaAnulacion(LocalDateTime.now());
                acta.setUsuarioAnulacion(autor != null ? autor.getUsuario() : "SISTEMA");
                if (autor != null) acta.setModificacionIdUsuario(autor.getIdUsuario());
                return acta.getNumero();
            }
            if (acta.esRegularizacion()) {
                // No movió nada: se puede anular mientras ningún faltante se haya resuelto.
                long resueltos = hs.stream().filter(h -> ControlActivosService.RESUELTO.equals(h.getEstadoHallazgo())).count();
                if (resueltos > 0) {
                    throw new ReglaNegocioException(resueltos + " faltante(s) del acta ya se resolvieron: "
                            + "el acta no se puede anular.");
                }
            } else {
                long movidos = hs.stream().filter(h -> h.getFechaEnvioCustodia() != null
                        || ControlActivosService.EN_CUSTODIA.equals(h.getEstadoHallazgo())
                        || ControlActivosService.RESUELTO.equals(h.getEstadoHallazgo())).count();
                if (movidos > 0) {
                    throw new ReglaNegocioException(movidos + " bien(es) del acta ya se trasladaron a la custodia: "
                            + "el acta no se puede anular. Corrija cada faltante resolviéndolo.");
                }
            }
            for (HallazgoInventario h : hs) {
                h.setEstadoEnvio(null);
                h.setMensajeEnvio(null);
                h.setResponsableCustodia(null);
                if (ControlActivosService.ORIGEN_DIRECTO.equals(h.getOrigen())
                        || ORIGEN_HISTORICO.equals(h.getOrigen())) {
                    // El histórico vuelve a "sin registro": el bien nunca se movió.
                    h.setEstadoHallazgo(ANULADO);
                } else {
                    h.setActa(null);
                    h.setDocumentoRespaldo(null);
                    h.setFechaDocumento(null);
                }
            }
            acta.setEstadoActa(ActaFaltante.ANULADA);
            acta.setMotivoAnulacion(m);
            acta.setFechaAnulacion(LocalDateTime.now());
            acta.setUsuarioAnulacion(autor != null ? autor.getUsuario() : "SISTEMA");
            if (autor != null) acta.setModificacionIdUsuario(autor.getIdUsuario());
            return acta.getNumero();
        });
        actividadService.registrar(autor, ActividadService.MOD_ACTIVO, ActividadService.ACC_ELIMINACION, numero,
                "Anuló el acta de faltantes " + numero + ": " + m, idActa);
    }

    public int reintentarEnvio(Long idActa, Usuario autor) {
        return envioService.reintentar(idActa, autor);
    }

    // ── Lectura del acta tal como se emitió ─────────────────────────────────

    public ActaFaltanteDTO porId(Long idActa) {
        return enTransaccion(() -> aDto(actaDao.findById(idActa)
                .orElseThrow(() -> new ReglaNegocioException("El acta no existe."))));
    }

    /** Para la página pública; null si el token no corresponde a ninguna acta. */
    public ActaFaltanteDTO porToken(String token) {
        if (token == null || token.isBlank() || token.length() > 40) return null;
        return enTransaccion(() -> actaDao.findByToken(token.trim()).map(this::aDto).orElse(null));
    }

    private ActaFaltanteDTO aDto(ActaFaltante acta) {
        String estado = acta.getEstadoActa();
        if (ActaFaltante.VIGENTE.equals(estado)) {
            // Reiterada: sus faltantes pendientes pasaron a la reiterativa. Antes, con algún bien
            // resuelto antes de reiterar, la verificación decía «faltantes resueltos».
            ActaFaltante reiterativa = actaDao
                    .findFirstByActaAnteriorIdActaAndEstadoActaNotOrderByIdActaDesc(acta.getIdActa(), ActaFaltante.ANULADA)
                    .orElse(null);
            if (reiterativa != null) {
                return aDto(acta, REITERADA, ActaFaltante.numeroImpreso(reiterativa.getNumero()));
            }
            List<HallazgoInventario> hs = hallazgoDao.deLaActa(acta.getIdActa());
            if (!hs.isEmpty() && hs.stream().allMatch(h -> ControlActivosService.RESUELTO.equals(h.getEstadoHallazgo()))) {
                estado = RESUELTA;
            }
        }
        return aDto(acta, estado);
    }

    private ActaFaltanteDTO aDto(ActaFaltante acta, String estado) {
        return aDto(acta, estado, null);
    }

    /**
     * @param estado VIGENTE | ANULADA | RESUELTA | REITERADA | VISTA_PREVIA
     * @param reiteradaPor la reiterativa que la reemplaza (solo en REITERADA)
     */
    private ActaFaltanteDTO aDto(ActaFaltante acta, String estado, String reiteradaPor) {
        List<ActaFaltanteDTO.Predio> predios = new ArrayList<>();
        String tipo = acta.esRegularizacion() ? ActaFaltante.TIPO_REGULARIZACION : ActaFaltante.TIPO_FALTANTES;
        Integer plazo = null;
        String ciudad = null, firmante = null, unidadDestino = null;
        LocalDateTime inicioPlazo = null, reiteraAFecha = null;
        String reiteraA = null;
        Integer numeroReiterativa = acta.getNumeroReiterativa();
        try {
            JsonNode raiz = json.readTree(acta.getContenido());
            if (raiz.hasNonNull("tipo")) tipo = raiz.path("tipo").asText(tipo);
            JsonNode not = raiz.path("notificacion");
            if (not.hasNonNull("plazoDiasHabiles")) {
                plazo = not.path("plazoDiasHabiles").asInt();
                ciudad = texto(not, "ciudad");
                firmante = texto(not, "firmante");
                unidadDestino = texto(not, "unidad");
                inicioPlazo = fechaHora(texto(not, "inicioPlazo"));
                if (numeroReiterativa == null && not.hasNonNull("numeroReiterativa")) {
                    numeroReiterativa = not.path("numeroReiterativa").asInt();
                }
                JsonNode ant = not.path("notificacionAnterior");
                if (ant.isObject()) {
                    reiteraA = texto(ant, "numeroImpreso");
                    reiteraAFecha = fechaHora(ant.hasNonNull("fecha") ? texto(ant, "fecha") : texto(ant, "fechaEmision"));
                }
            }
            Map<String, Map<String, List<ActaFaltanteDTO.Bien>>> arbol = new LinkedHashMap<>();
            Map<String, String> nombrePredio = new LinkedHashMap<>();
            Map<String, Short> codOficina = new LinkedHashMap<>();
            for (JsonNode b : raiz.path("bienes")) {
                String unidad = b.path("unidad").asText("");
                String claveOfi = b.path("codOfi").asText("") + "|" + b.path("oficina").asText("");
                nombrePredio.putIfAbsent(unidad, b.path("predio").asText(""));
                codOficina.putIfAbsent(unidad + "#" + claveOfi, b.hasNonNull("codOfi") ? (short) b.path("codOfi").asInt() : null);
                arbol.computeIfAbsent(unidad, k -> new LinkedHashMap<>())
                        .computeIfAbsent(claveOfi, k -> new ArrayList<>())
                        .add(new ActaFaltanteDTO.Bien(b.path("codigo").asText(""), b.path("descripcion").asText(""),
                                texto(b, "descripcionCorta"), texto(b, "marca"), texto(b, "modelo"), texto(b, "serie")));
            }
            arbol.forEach((unidad, oficinas) -> {
                List<ActaFaltanteDTO.Oficina> lista = new ArrayList<>();
                oficinas.forEach((claveOfi, bienes) -> lista.add(new ActaFaltanteDTO.Oficina(
                        codOficina.get(unidad + "#" + claveOfi), claveOfi.substring(claveOfi.indexOf('|') + 1), bienes)));
                predios.add(new ActaFaltanteDTO.Predio(unidad, nombrePredio.get(unidad), lista));
            });
        } catch (Exception e) {
            log.error("[ACTA-FALTANTES] Contenido ilegible en {}: {}", acta.getNumero(), e.getMessage());
        }

        return new ActaFaltanteDTO(acta.getIdActa(), acta.getNumero(), acta.getToken(), acta.getFechaEmision(),
                acta.getUsuarioEmision(), acta.getPersonaNombre(), acta.getPersonaCi(), acta.getPersonaCargo(),
                acta.getDocumentoRespaldo(), acta.getFechaDocumento(), acta.getObservacion(),
                acta.getTotalBienes() != null ? acta.getTotalBienes() : 0, estado, acta.getMotivoAnulacion(),
                acta.getFechaAnulacion(), acta.getHashContenido(),
                Objects.equals(sha256(acta.getContenido()), acta.getHashContenido()), predios, tipo,
                plazo, ciudad, firmante, unidadDestino, inicioPlazo,
                numeroReiterativa != null && numeroReiterativa > 0 ? numeroReiterativa : null, reiteraA, reiteraAFecha,
                reiteradaPor);
    }

    private static LocalDateTime fechaHora(String s) {
        if (s == null) return null;
        try {
            return LocalDateTime.parse(s);
        } catch (Exception e) {
            return null;
        }
    }

    private static String texto(JsonNode n, String campo) {
        return n.hasNonNull(campo) && !n.path(campo).asText().isBlank() ? n.path(campo).asText() : null;
    }

    // ── Apoyo ───────────────────────────────────────────────────────────────

    /**
     * Foto de lo emitido. El orden de las claves es fijo: el hash depende del texto exacto.
     *
     * @param origen oficina que se imprime como "de origen" de cada bien; null = no registrada
     * @param notificacion datos propios de la notificación; null en las actas de regularización
     */
    private String contenido(ActaFaltante acta, Persona persona, List<Activo> activos, String tipo,
                             Function<Activo, Oficina> origen, Map<String, Object> notificacion) {
        Map<String, Object> raiz = new LinkedHashMap<>();
        raiz.put("numero", acta.getNumero());
        raiz.put("tipo", tipo);
        raiz.put("fechaEmision", acta.getFechaEmision().withNano(0).toString());
        raiz.put("usuario", acta.getUsuarioEmision());
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("idPersona", persona.getIdPersona());
        p.put("nombre", acta.getPersonaNombre());
        p.put("ci", acta.getPersonaCi());
        p.put("cargo", acta.getPersonaCargo());
        raiz.put("persona", p);
        raiz.put("documentoRespaldo", acta.getDocumentoRespaldo());
        raiz.put("fechaDocumento", acta.getFechaDocumento() != null ? acta.getFechaDocumento().toString() : null);
        raiz.put("observacion", acta.getObservacion());
        List<Map<String, Object>> bienes = new ArrayList<>();
        Set<Long> vistos = new HashSet<>();
        for (Activo a : activos) {
            if (!vistos.add(a.getIdActivo())) continue;
            Map<String, Object> b = new LinkedHashMap<>();
            b.put("idActivo", a.getIdActivo());
            b.put("codigo", a.getCodigo());
            b.put("descripcion", a.getDescripcion());
            b.put("unidad", a.getOficina().getPredio().getUnidad());
            b.put("predio", a.getOficina().getPredio().getDescrip());
            Oficina o = origen.apply(a);
            b.put("codOfi", o != null ? o.getCodOfi() : null);
            b.put("oficina", o != null ? o.getNombre() : ORIGEN_NO_REGISTRADO);
            if (notificacion != null) {
                // Columnas de la notificación, separadas de la descripción tal como estaba ese día.
                DatosTecnicos d = DatosTecnicos.de(a.getDescripcion());
                b.put("descripcionCorta", d.descripcion());
                b.put("marca", d.marca());
                b.put("modelo", d.modelo());
                b.put("serie", d.serie());
            }
            bienes.add(b);
        }
        raiz.put("bienes", bienes);
        if (notificacion != null) raiz.put("notificacion", notificacion);
        try {
            return json.writeValueAsString(raiz);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo armar el contenido del acta: " + e.getMessage(), e);
        }
    }

    /** 24 caracteres aleatorios, seguros para una URL. */
    private String nuevoToken() {
        byte[] b = new byte[18];
        azar.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    static String sha256(String texto) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(
                    (texto == null ? "" : texto).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte x : d) sb.append(String.format("%02x", x));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String vacioANull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private static String recortar(String s, int max) {
        return (s == null || s.length() <= max) ? s : s.substring(0, max);
    }

    private <T> T enTransaccion(Supplier<T> trabajo) {
        return new TransactionTemplate(txManager).execute(estado -> trabajo.get());
    }
}
