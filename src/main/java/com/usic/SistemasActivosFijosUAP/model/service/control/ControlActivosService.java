package com.usic.SistemasActivosFijosUAP.model.service.control;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.usic.SistemasActivosFijosUAP.componet.SseEmitterRegistry;
import com.usic.SistemasActivosFijosUAP.model.dao.IActivoDao;
import com.usic.SistemasActivosFijosUAP.model.dao.IEstadoActivoDao;
import com.usic.SistemasActivosFijosUAP.model.dao.IHallazgoInventarioDao;
import com.usic.SistemasActivosFijosUAP.model.dao.IInventarioDao;
import com.usic.SistemasActivosFijosUAP.model.dao.IInventarioDetalleDao;
import com.usic.SistemasActivosFijosUAP.model.dao.IOficinaDao;
import com.usic.SistemasActivosFijosUAP.model.dao.IUsuarioDao;
import com.usic.SistemasActivosFijosUAP.model.dto.control.AbrirLevantamientoRequest;
import com.usic.SistemasActivosFijosUAP.model.dto.control.ActivoResponsableDTO;
import com.usic.SistemasActivosFijosUAP.model.dto.control.CerrarLevantamientoRequest;
import com.usic.SistemasActivosFijosUAP.model.dto.control.DetalleLevantamientoDTO;
import com.usic.SistemasActivosFijosUAP.model.dto.control.EventoLevantamientoDTO;
import com.usic.SistemasActivosFijosUAP.model.dto.control.FaltanteDTO;
import com.usic.SistemasActivosFijosUAP.model.dto.control.LevantamientoDTO;
import com.usic.SistemasActivosFijosUAP.model.dto.control.MarcaRequest;
import com.usic.SistemasActivosFijosUAP.model.dto.control.MarcasLoteRequest;
import com.usic.SistemasActivosFijosUAP.model.dto.control.ResolverHallazgoRequest;
import com.usic.SistemasActivosFijosUAP.model.dto.control.ResumenCierreDTO;
import com.usic.SistemasActivosFijosUAP.model.dto.control.ResumenMarcasDTO;
import com.usic.SistemasActivosFijosUAP.model.dto.control.TileOficinaDTO;
import com.usic.SistemasActivosFijosUAP.model.dto.control.TilePredioDTO;
import com.usic.SistemasActivosFijosUAP.model.dto.control.TileResponsableDTO;
import com.usic.SistemasActivosFijosUAP.model.entity.Activo;
import com.usic.SistemasActivosFijosUAP.model.entity.HallazgoInventario;
import com.usic.SistemasActivosFijosUAP.model.entity.Inventario;
import com.usic.SistemasActivosFijosUAP.model.entity.InventarioDetalle;
import com.usic.SistemasActivosFijosUAP.model.entity.Oficina;
import com.usic.SistemasActivosFijosUAP.model.entity.Persona;
import com.usic.SistemasActivosFijosUAP.model.entity.Responsable;
import com.usic.SistemasActivosFijosUAP.model.repository.ControlActivosRepo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Control de activos por responsable: navegación del mapa, levantamientos y
 * hallazgos.
 *
 * <p>Módulo interno. No lee ni escribe DBF: todo ocurre contra PostgreSQL.
 *
 * <p>Las tres operaciones que llegan desde el móvil —abrir, marcar y cerrar—
 * son <b>idempotentes</b>. En campo se trabaja sin señal y la app reintenta a
 * ciegas cuando vuelve la conexión; si repetir una llamada duplicara un
 * levantamiento o revirtiera una marca, el trabajo de una jornada se perdería.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ControlActivosService {

    private final IInventarioDao        inventarioDao;
    private final IInventarioDetalleDao detalleDao;
    private final IHallazgoInventarioDao hallazgoDao;
    private final IActivoDao            activoDao;
    private final IOficinaDao           oficinaDao;
    private final IEstadoActivoDao      estadoActivoDao;
    private final IUsuarioDao           usuarioDao;
    private final ControlActivosRepo    repo;
    private final SseEmitterRegistry    sse;

    public static final String TIPO_FALTANTE      = "FALTANTE";
    public static final String TIPO_SOBRANTE      = "SOBRANTE";
    public static final String TIPO_OBSERVADO     = "OBSERVADO";
    public static final String TIPO_SIN_CODIFICAR = "SIN_CODIFICAR";

    public static final String ABIERTO  = "ABIERTO";
    public static final String RESUELTO = "RESUELTO";

    private static final String AUDIT_ACTIVO = "ACTIVO";
    private static final List<String> ROLES_AVISO = List.of("ADMINISTRADOR", "SUPER USUARIO");

    // ═══════════════════════════════════════════════════════════════════════
    //  Mapa
    // ═══════════════════════════════════════════════════════════════════════

    public List<TilePredioDTO> mapaPredios() {
        return repo.tilesPredio();
    }

    public List<TileOficinaDTO> mapaOficinas(Long idPredio) {
        return repo.tilesOficina(idPredio);
    }

    public List<TileResponsableDTO> mapaResponsables(Long idOficina) {
        return repo.tilesResponsable(idOficina);
    }

    public List<ActivoResponsableDTO> activosDe(Long idResponsable) {
        return repo.activosDeResponsable(idResponsable);
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Levantamiento
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Abre un levantamiento sobre una oficina y congela su lista esperada.
     *
     * <p>Devuelve el existente en dos casos, sin crear nada: si el
     * {@code uuidCliente} ya se usó (la app reintentó una petición que sí llegó),
     * o si esa oficina ya tiene un recorrido abierto. La decisión de permitir uno
     * solo por oficina es deliberada — dos recorridos simultáneos sobre el mismo
     * ambiente producirían dos actas contradictorias del mismo día.
     */
    @Transactional
    public LevantamientoDTO abrir(AbrirLevantamientoRequest req, Long idUsuario, String origen) {

        if (req.uuidCliente() != null && !req.uuidCliente().isBlank()) {
            Optional<Inventario> previo = inventarioDao.findByUuidCliente(req.uuidCliente());
            if (previo.isPresent()) {
                log.debug("Levantamiento reutilizado por uuidCliente={}", req.uuidCliente());
                return aDto(previo.get(), true);
            }
        }

        Optional<Inventario> enCurso = inventarioDao
                .findFirstByOficinaIdOficinaAndEstadoLevantamiento(req.idOficina(), Inventario.EN_EJECUCION);
        if (enCurso.isPresent()) {
            return aDto(enCurso.get(), true);
        }

        Oficina oficina = oficinaDao.findById(req.idOficina())
                .orElseThrow(() -> new ReglaNegocioException("La oficina no existe"));

        Inventario inv = new Inventario();
        inv.setOficina(oficina);
        // Provisorio: la columna es NOT NULL y el número definitivo se arma con el
        // id, que recién existe después del insert. Nunca sale de esta transacción.
        inv.setNumeroInventario("TMP-" + UUID.randomUUID());
        inv.setFechaInicio(LocalDateTime.now());
        inv.setEstadoLevantamiento(Inventario.EN_EJECUCION);
        inv.setOrigen(origen);
        inv.setUuidCliente(vacioANull(req.uuidCliente()));
        inv.setIdUsuarioEjecutor(idUsuario);
        inv.setDescripcion(req.descripcion());
        inv.setEstado(AUDIT_ACTIVO);
        inv = inventarioDao.save(inv);
        inv.setNumeroInventario(numeroDe(inv));

        List<Activo> esperados = activoDao.findParaLevantamiento(req.idOficina());
        List<InventarioDetalle> filas = new ArrayList<>(esperados.size());
        for (Activo a : esperados) {
            InventarioDetalle d = new InventarioDetalle();
            d.setInventario(inv);
            d.setActivo(a);
            d.setResponsable(a.getResponsable());
            d.setCodigo(a.getCodigo());
            d.setDescripcion(a.getDescripcion());
            d.setSituacion(InventarioDetalle.SITUACION_PENDIENTE);
            d.setEstado(AUDIT_ACTIVO);
            filas.add(d);
        }
        detalleDao.saveAll(filas);

        inv.setTotalActivosEsperados(filas.size());
        inv.setTotalActivosEncontrados(0);
        inv.setTotalFaltantes(0);

        log.info("Levantamiento {} abierto en oficina {} con {} activos esperados",
                inv.getNumeroInventario(), oficina.getNombre(), filas.size());

        emitir("levantamiento-abierto", inv, filas.size(), 0, filas.size(), 0);
        return aDto(inv, true);
    }

    @Transactional(readOnly = true)
    public LevantamientoDTO obtener(Long idInventario) {
        return aDto(cargar(idInventario), true);
    }

    @Transactional(readOnly = true)
    public List<LevantamientoDTO> levantamientosDe(Long idUsuario) {
        return inventarioDao.findByIdUsuarioEjecutorOrderByFechaInicioDesc(idUsuario)
                .stream().map(i -> aDto(i, false)).toList();
    }

    /**
     * Aplica un lote de marcas hechas en campo.
     *
     * <p>Es el mismo camino para la web (un solo elemento) y para la cola offline
     * del móvil (decenas). Una marca se descarta si el servidor ya guardó otra
     * más nueva para ese activo: así reenviar la cola nunca hace retroceder el
     * trabajo, que es la única garantía que vuelve seguro el reintento a ciegas.
     */
    @Transactional
    public ResumenMarcasDTO aplicarMarcas(Long idInventario, MarcasLoteRequest req) {

        Inventario inv = cargar(idInventario);
        exigirAbierto(inv);

        int aplicadas = 0, ignoradas = 0, sobrantes = 0;

        for (MarcaRequest m : req.marcas()) {
            InventarioDetalle d = localizar(idInventario, m);

            if (d == null) {
                // Apareció algo que no se esperaba en esta oficina. No se descarta:
                // un activo que está donde no debería es tan hallazgo como uno que falta.
                if (registrarSobrante(inv, m)) sobrantes++;
                continue;
            }

            LocalDateTime cuando = m.fecha() != null ? m.fecha() : LocalDateTime.now();
            if (d.getFechaMarca() != null && d.getFechaMarca().isAfter(cuando)) {
                ignoradas++;
                continue;
            }

            d.setSituacion(normalizarSituacion(m.situacion()));
            d.setOrigenMarca(normalizarOrigen(m.origen()));
            d.setFechaMarca(cuando);
            if (m.observacion() != null && !m.observacion().isBlank()) {
                d.setObservacion(m.observacion().trim());
            }
            if (m.idEstadoObservado() != null) {
                estadoActivoDao.findById(m.idEstadoObservado()).ifPresent(d::setEstadoObservado);
            }
            aplicadas++;
        }

        int esperados   = (int) detalleDao.countByInventarioIdInventario(idInventario);
        int encontrados = (int) detalleDao.countByInventarioIdInventarioAndSituacion(
                idInventario, InventarioDetalle.SITUACION_ENCONTRADO);
        int pendientes  = (int) detalleDao.countByInventarioIdInventarioAndSituacion(
                idInventario, InventarioDetalle.SITUACION_PENDIENTE);

        inv.setTotalActivosEncontrados(encontrados);
        emitir("levantamiento-avance", inv, esperados, encontrados, pendientes, 0);

        return new ResumenMarcasDTO(true, aplicadas, ignoradas, sobrantes,
                encontrados, pendientes, esperados);
    }

    /**
     * Cierra el levantamiento y convierte el recorrido en hallazgos.
     *
     * <p>Todo lo que quedó sin revisar pasa a faltante — ese es el modo "por
     * ausencia": en campo solo se marca lo que se encuentra, porque pedir que
     * además se marque lo ausente garantiza que algo se olvide. Un encontrado con
     * novedad de condición también genera hallazgo: "está roto" es información
     * que alguien tiene que atender, igual que una ausencia.
     *
     * <p>Volver a llamar sobre un levantamiento ya cerrado devuelve el mismo
     * resumen sin duplicar nada.
     */
    @Transactional
    public ResumenCierreDTO cerrar(Long idInventario, CerrarLevantamientoRequest req, String usuario) {

        Inventario inv = cargar(idInventario);
        if (Inventario.COMPLETADO.equals(inv.getEstadoLevantamiento())) {
            return resumenDe(inv);
        }

        List<InventarioDetalle> detalle = detalleDao.findDetalleCompleto(idInventario);
        int faltantes = 0, observados = 0, encontrados = 0, creados = 0;

        for (InventarioDetalle d : detalle) {
            if (d.estaPendiente()) {
                d.setSituacion(InventarioDetalle.SITUACION_FALTANTE);
                if (d.getFechaMarca() == null) d.setFechaMarca(LocalDateTime.now());
            }

            if (InventarioDetalle.SITUACION_FALTANTE.equals(d.getSituacion())) {
                faltantes++;
                if (crearHallazgo(inv, d, TIPO_FALTANTE, descripcionFaltante(d), usuario)) creados++;
            } else {
                encontrados++;
                if (d.tieneNovedad()) {
                    observados++;
                    if (crearHallazgo(inv, d, TIPO_OBSERVADO, descripcionNovedad(d), usuario)) creados++;
                }
            }
        }

        inv.setEstadoLevantamiento(Inventario.COMPLETADO);
        inv.setFechaFin(LocalDateTime.now());
        inv.setTotalActivosEncontrados(encontrados);
        inv.setTotalFaltantes(faltantes);
        if (req != null && req.observ() != null && !req.observ().isBlank()) {
            inv.setObserv(req.observ().trim());
        }

        log.info("Levantamiento {} cerrado: {} encontrados, {} faltantes, {} observados",
                inv.getNumeroInventario(), encontrados, faltantes, observados);

        emitir("levantamiento-cerrado", inv, detalle.size(), encontrados, 0, faltantes);

        return new ResumenCierreDTO(true, inv.getIdInventario(), inv.getNumeroInventario(),
                detalle.size(), encontrados, faltantes, observados, creados);
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Hallazgos
    // ═══════════════════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public List<FaltanteDTO> faltantes(Long idPredio, Long idOficina, Long idResponsable,
                                       String tipo, String estado) {
        return repo.faltantes(idPredio, idOficina, idResponsable, tipo, estado);
    }

    @Transactional
    public void resolver(Long idHallazgo, ResolverHallazgoRequest req, String usuario) {
        HallazgoInventario h = hallazgoDao.findById(idHallazgo)
                .orElseThrow(() -> new ReglaNegocioException("El hallazgo no existe"));

        if (RESUELTO.equals(h.getEstadoHallazgo())) {
            throw new ReglaNegocioException("El hallazgo ya fue resuelto");
        }

        h.setEstadoHallazgo(RESUELTO);
        h.setTipoResolucion(req.tipoResolucion());
        h.setAccionCorrectiva(req.accionCorrectiva());
        h.setFechaResolucion(LocalDateTime.now());
        h.setUsuarioRevisor(usuario);

        recalcularFaltantes(h.getInventario());
    }

    /**
     * Reabre un hallazgo cerrado por error. Sin esto, una resolución equivocada
     * quedaría fija para siempre y el único arreglo sería tocar la base a mano.
     */
    @Transactional
    public void reabrir(Long idHallazgo, String usuario) {
        HallazgoInventario h = hallazgoDao.findById(idHallazgo)
                .orElseThrow(() -> new ReglaNegocioException("El hallazgo no existe"));

        h.setEstadoHallazgo(ABIERTO);
        h.setTipoResolucion(null);
        h.setFechaResolucion(null);
        h.setUsuarioRevisor(usuario);

        recalcularFaltantes(h.getInventario());
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Internos
    // ═══════════════════════════════════════════════════════════════════════

    private Inventario cargar(Long id) {
        return inventarioDao.findConUbicacion(id)
                .orElseThrow(() -> new ReglaNegocioException("El levantamiento no existe"));
    }

    private void exigirAbierto(Inventario inv) {
        if (!Inventario.EN_EJECUCION.equals(inv.getEstadoLevantamiento())) {
            throw new ReglaNegocioException(
                    "El levantamiento " + inv.getNumeroInventario() + " ya está cerrado");
        }
    }

    /** Por id de detalle si la app lo tiene; si no, por el código que leyó el escáner. */
    private InventarioDetalle localizar(Long idInventario, MarcaRequest m) {
        if (m.idDetalle() != null) {
            return detalleDao.findById(m.idDetalle())
                    .filter(d -> d.getInventario().getIdInventario().equals(idInventario))
                    .orElse(null);
        }
        if (m.codigo() != null && !m.codigo().isBlank()) {
            String codigo = m.codigo().trim();
            Optional<InventarioDetalle> exacto =
                    detalleDao.findByInventarioIdInventarioAndCodigo(idInventario, codigo);
            if (exacto.isPresent()) return exacto.get();

            // Si llega la forma visual (con prefijo de entidad) y no la reconocemos,
            // el activo se registraría como sobrante estando en la lista esperada:
            // un hallazgo falso, y además seguiría contando como no encontrado.
            if (codigo.split("-").length >= 5) {
                return detalleDao.findByInventarioIdInventarioAndCodigo(
                        idInventario, codigo.substring(codigo.indexOf('-') + 1)).orElse(null);
            }
        }
        return null;
    }

    /**
     * @return true si creó el hallazgo; false si ya existía (reenvío del lote).
     */
    private boolean registrarSobrante(Inventario inv, MarcaRequest m) {
        String codigo = m.codigo() == null ? null : m.codigo().trim();
        if (codigo == null || codigo.isBlank()) return false;

        Optional<Activo> activo = buscarPorCodigoTolerante(codigo);
        String tipo = activo.isPresent() ? TIPO_SOBRANTE : TIPO_SIN_CODIFICAR;

        Long idActivo = activo.map(Activo::getIdActivo).orElse(null);
        if (idActivo != null && hallazgoDao
                .findByInventarioIdInventarioAndActivoIdActivoAndTipoHallazgo(
                        inv.getIdInventario(), idActivo, tipo).isPresent()) {
            return false;
        }

        HallazgoInventario h = new HallazgoInventario();
        h.setInventario(inv);
        h.setTipoHallazgo(tipo);
        h.setEstadoHallazgo(ABIERTO);
        h.setCodigoFisico(codigo);
        h.setEstado(AUDIT_ACTIVO);
        activo.ifPresent(a -> {
            h.setActivo(a);
            h.setResponsable(a.getResponsable());
            h.setDescripcionFisica(a.getDescripcion());
        });
        h.setDescripcionDiscrepancia(activo.isPresent()
                ? "Encontrado en esta oficina, pero registrado en otra ubicación"
                : "Código leído en campo que no existe en el sistema");
        if (m.observacion() != null && !m.observacion().isBlank()) {
            h.setObserv(m.observacion().trim());
        }
        hallazgoDao.save(h);
        return true;
    }

    /**
     * Busca un activo por código aceptando las dos formas que circulan.
     *
     * <p>{@code activo.codigo} se guarda <b>sin</b> el prefijo de entidad
     * ({@code 01-04-02-03609}); el código impreso en la etiqueta y el que arma
     * {@code ActivoMovilMapper.codigoVisual} sí lo llevan
     * ({@code 148-01-04-02-03609}). Un cliente que mande la forma visual haría
     * fallar la búsqueda exacta y el sobrante caería como SIN_CODIFICAR: el
     * hallazgo quedaría registrado, pero perdiendo de qué activo se trata, que
     * es justo el dato que hace falta para resolverlo.
     */
    private Optional<Activo> buscarPorCodigoTolerante(String codigo) {
        // fetchFullByCodigo y no findByCodigo: hace falta el responsable ya
        // inicializado para imputarle el sobrante fuera de la sesión.
        Optional<Activo> exacto = activoDao.fetchFullByCodigo(codigo);
        if (exacto.isPresent()) return exacto;

        // El código propio tiene 4 tramos; con prefijo de entidad, 5. Solo en ese
        // caso vale la pena reintentar sin el primero.
        int tramos = codigo.split("-").length;
        if (tramos >= 5) {
            return activoDao.fetchFullByCodigo(codigo.substring(codigo.indexOf('-') + 1));
        }
        return Optional.empty();
    }

    private boolean crearHallazgo(Inventario inv, InventarioDetalle d,
                                  String tipo, String discrepancia, String usuario) {

        Long idActivo = d.getActivo() != null ? d.getActivo().getIdActivo() : null;
        if (idActivo != null && hallazgoDao
                .findByInventarioIdInventarioAndActivoIdActivoAndTipoHallazgo(
                        inv.getIdInventario(), idActivo, tipo).isPresent()) {
            return false;
        }

        HallazgoInventario h = new HallazgoInventario();
        h.setInventario(inv);
        h.setActivo(d.getActivo());
        h.setResponsable(d.getResponsable());
        h.setTipoHallazgo(tipo);
        h.setEstadoHallazgo(ABIERTO);
        h.setCodigoFisico(d.getCodigo());
        h.setDescripcionFisica(d.getDescripcion());
        h.setDescripcionDiscrepancia(discrepancia);
        h.setObserv(d.getObservacion());
        h.setEstado(AUDIT_ACTIVO);
        hallazgoDao.save(h);
        return true;
    }

    private String descripcionFaltante(InventarioDetalle d) {
        return "No se encontró en el recorrido de la oficina"
                + (d.getResponsable() != null ? ", imputado a " + nombreDe(d.getResponsable()) : "");
    }

    private String descripcionNovedad(InventarioDetalle d) {
        StringBuilder sb = new StringBuilder("Encontrado con novedad");
        if (d.getEstadoObservado() != null) {
            sb.append(": condición constatada ").append(d.getEstadoObservado().getNombre());
        }
        return sb.toString();
    }

    /** Mantiene al día el contador que pinta los tiles sin recontar hallazgos. */
    private void recalcularFaltantes(Inventario inv) {
        if (inv == null) return;
        long abiertos = hallazgoDao.findByInventarioIdInventario(inv.getIdInventario()).stream()
                .filter(x -> ABIERTO.equals(x.getEstadoHallazgo()))
                .filter(x -> TIPO_FALTANTE.equals(x.getTipoHallazgo()))
                .count();
        inv.setTotalFaltantes((int) abiertos);
    }

    private ResumenCierreDTO resumenDe(Inventario inv) {
        int esperados = inv.getTotalActivosEsperados() == null ? 0 : inv.getTotalActivosEsperados();
        int encontrados = inv.getTotalActivosEncontrados() == null ? 0 : inv.getTotalActivosEncontrados();
        int faltantes = inv.getTotalFaltantes() == null ? 0 : inv.getTotalFaltantes();
        return new ResumenCierreDTO(true, inv.getIdInventario(), inv.getNumeroInventario(),
                esperados, encontrados, faltantes, 0, 0);
    }

    private LevantamientoDTO aDto(Inventario inv, boolean conDetalle) {

        List<DetalleLevantamientoDTO> filas = List.of();
        int encontrados = 0, pendientes = 0, faltantes = 0;

        if (conDetalle) {
            List<InventarioDetalle> detalle = detalleDao.findDetalleCompleto(inv.getIdInventario());
            filas = new ArrayList<>(detalle.size());
            for (InventarioDetalle d : detalle) {
                switch (d.getSituacion()) {
                    case InventarioDetalle.SITUACION_ENCONTRADO -> encontrados++;
                    case InventarioDetalle.SITUACION_FALTANTE   -> faltantes++;
                    default                                      -> pendientes++;
                }
                filas.add(new DetalleLevantamientoDTO(
                        d.getIdDetalle(),
                        d.getActivo() != null ? d.getActivo().getIdActivo() : null,
                        d.getCodigo(),
                        d.getDescripcion(),
                        d.getResponsable() != null ? d.getResponsable().getIdResponsable() : null,
                        nombreDe(d.getResponsable()),
                        d.getSituacion(),
                        d.getOrigenMarca(),
                        d.getFechaMarca(),
                        d.getObservacion(),
                        d.getEstadoObservado() != null ? d.getEstadoObservado().getIdEstadoActivo() : null,
                        d.getEstadoObservado() != null ? d.getEstadoObservado().getNombre() : null));
            }
        } else {
            encontrados = inv.getTotalActivosEncontrados() == null ? 0 : inv.getTotalActivosEncontrados();
            faltantes   = inv.getTotalFaltantes() == null ? 0 : inv.getTotalFaltantes();
            int esperados = inv.getTotalActivosEsperados() == null ? 0 : inv.getTotalActivosEsperados();
            pendientes  = Math.max(0, esperados - encontrados - faltantes);
        }

        Oficina o = inv.getOficina();
        return new LevantamientoDTO(
                inv.getIdInventario(),
                inv.getNumeroInventario(),
                o != null ? o.getIdOficina() : null,
                o != null ? o.getCodOfi() : null,
                o != null ? o.getNombre() : null,
                o != null && o.getPredio() != null ? o.getPredio().getIdPredio() : null,
                o != null && o.getPredio() != null ? o.getPredio().getDescrip() : null,
                inv.getEstadoLevantamiento(),
                inv.getOrigen(),
                inv.getFechaInicio(),
                inv.getFechaFin(),
                nombreUsuario(inv.getIdUsuarioEjecutor()),
                inv.getTotalActivosEsperados() == null ? filas.size() : inv.getTotalActivosEsperados(),
                encontrados,
                pendientes,
                faltantes,
                inv.getObserv(),
                filas);
    }

    private void emitir(String evento, Inventario inv,
                        int esperados, int encontrados, int pendientes, int faltantes) {

        Oficina o = inv.getOficina();
        EventoLevantamientoDTO payload = new EventoLevantamientoDTO(
                inv.getIdInventario(),
                inv.getNumeroInventario(),
                o != null ? o.getIdOficina() : null,
                o != null ? o.getNombre() : null,
                o != null && o.getPredio() != null ? o.getPredio().getIdPredio() : null,
                esperados, encontrados, pendientes, faltantes,
                inv.getEstadoLevantamiento(),
                nombreUsuario(inv.getIdUsuarioEjecutor()));

        try {
            sse.broadcast(evento, payload);
            sse.enviarARoles(ROLES_AVISO, evento, payload);
        } catch (Exception e) {
            // El levantamiento no puede fallar porque un navegador se desconectó.
            log.warn("No se pudo emitir el evento {} del levantamiento {}: {}",
                    evento, inv.getIdInventario(), e.getMessage());
        }
    }

    private String numeroDe(Inventario inv) {
        return String.format("LEV-%d-%06d",
                inv.getFechaInicio().getYear(), inv.getIdInventario());
    }

    private String nombreDe(Responsable r) {
        if (r == null) return null;
        Persona p = r.getPersona();
        return p != null ? p.getNombreCompleto() : r.getCodigoFuncionario();
    }

    private String nombreUsuario(Long idUsuario) {
        if (idUsuario == null) return null;
        return usuarioDao.findById(idUsuario).map(u -> u.getUsuario()).orElse(null);
    }

    private String normalizarSituacion(String s) {
        if (s == null) return InventarioDetalle.SITUACION_ENCONTRADO;
        return switch (s.trim().toUpperCase()) {
            case "FALTANTE"  -> InventarioDetalle.SITUACION_FALTANTE;
            case "PENDIENTE" -> InventarioDetalle.SITUACION_PENDIENTE;
            default          -> InventarioDetalle.SITUACION_ENCONTRADO;
        };
    }

    private String normalizarOrigen(String o) {
        if (o == null) return InventarioDetalle.ORIGEN_MANUAL;
        return switch (o.trim().toUpperCase()) {
            case "ESCANEO" -> InventarioDetalle.ORIGEN_ESCANEO;
            case "WEB"     -> InventarioDetalle.ORIGEN_WEB;
            default        -> InventarioDetalle.ORIGEN_MANUAL;
        };
    }

    private String vacioANull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
