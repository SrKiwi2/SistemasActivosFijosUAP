package com.usic.SistemasActivosFijosUAP.model.service.asignacion;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.usic.SistemasActivosFijosUAP.interoperabilidad.registroDbf.ActualDbfWriterService;
import com.usic.SistemasActivosFijosUAP.model.dao.IAsignacionActivoDao;
import com.usic.SistemasActivosFijosUAP.model.dao.IAsignacionMovimientoDao;
import com.usic.SistemasActivosFijosUAP.model.dao.IDetalleAsignacionDao;
import com.usic.SistemasActivosFijosUAP.model.dao.IHistorialActivoDao;
import com.usic.SistemasActivosFijosUAP.model.IService.IActivoService;
import com.usic.SistemasActivosFijosUAP.model.IService.IConfiguracionGestionService;
import com.usic.SistemasActivosFijosUAP.model.IService.IOficinaService;
import com.usic.SistemasActivosFijosUAP.model.IService.IResponsableService;
import com.usic.SistemasActivosFijosUAP.model.entity.Activo;
import com.usic.SistemasActivosFijosUAP.model.entity.AsignacionActivo;
import com.usic.SistemasActivosFijosUAP.model.entity.AsignacionMovimiento;
import com.usic.SistemasActivosFijosUAP.model.entity.AsignacionMovimientoDetalle;
import com.usic.SistemasActivosFijosUAP.model.entity.ConfiguracionGestion;
import com.usic.SistemasActivosFijosUAP.model.entity.DetalleAsignacionActivo;
import com.usic.SistemasActivosFijosUAP.model.entity.HistorialActivo;
import com.usic.SistemasActivosFijosUAP.model.entity.Oficina;
import com.usic.SistemasActivosFijosUAP.model.entity.Responsable;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Operaciones que reorganizan actas ya emitidas: separar, y lo que venga después.
 *
 * <h4>El principio que ordena todo</h4>
 * El VSIAF no conoce el concepto de acta. En {@code ACTUAL.DBF} hay activos con su
 * oficina, su responsable y su descripción, nada más. Por eso <b>separar un acta en dos
 * no toca el sistema legacy</b>: lo que sí lo toca es cambiarle a un bien el responsable,
 * la oficina o la descripción. La distinción importa, porque decide cuándo hay que
 * encolar y cuándo no.
 *
 * <h4>Por qué se copia la línea en vez de moverla</h4>
 * El acta original conserva sus líneas, con las trasladadas marcadas como
 * {@code TRASLADADO}, y el acta nueva recibe líneas propias. Mover la fila dejaría al
 * papel ya firmado diciendo diez bienes y al sistema ocho, sin rastro de la diferencia.
 * Así el acta impresa sigue siendo verificable y el traslado queda documentado en las dos.
 *
 * <h4>Rol</h4>
 * La comprobación va acá y no en {@code SeguridadConfig}: en este proyecto casi todas las
 * rutas están en {@code permitAll()}, así que un matcher por URL no protegería nada.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AsignacionEdicionService {

    /** Quiénes pueden reorganizar actas ya emitidas. */
    private static final Set<String> ROLES_EDICION = Set.of("ADMINISTRADOR", "SUPER USUARIO");

    private final IAsignacionActivoDao asignacionDao;
    private final IAsignacionMovimientoDao movimientoDao;
    private final IDetalleAsignacionDao detalleDao;
    private final IHistorialActivoDao historialDao;
    private final IActivoService activoService;
    private final IResponsableService responsableService;
    private final IOficinaService oficinaService;
    private final IConfiguracionGestionService configuracionGestionService;
    private final ActualDbfWriterService actualDbfWriterService;

    public boolean puedeEditar(Usuario usuario) {
        return usuario != null && usuario.getRol() != null && usuario.getRol().getNombre() != null
            && ROLES_EDICION.contains(usuario.getRol().getNombre().trim().toUpperCase());
    }

    /**
     * Separa parte de los bienes de un acta hacia un acta nueva.
     *
     * @throws IllegalArgumentException con un motivo legible cuando la solicitud no es
     *         válida; el controlador lo devuelve tal cual como 400
     */
    @Transactional
    public ResultadoOperacionActa separar(SeparacionActaDTO solicitud, Usuario usuario) {

        if (!puedeEditar(usuario)) {
            throw new SecurityException("Solo un ADMINISTRADOR o SUPER USUARIO puede separar un acta.");
        }
        validar(solicitud);

        AsignacionActivo origen = asignacionDao.findByIdConDetalles(solicitud.idActaOrigen())
                .orElseThrow(() -> new IllegalArgumentException("El acta de origen no existe."));

        List<DetalleAsignacionActivo> aMover = seleccionarDetalles(origen, solicitud.idsActivos());

        ConfiguracionGestion config = configuracionGestionService.findById(solicitud.idConfigGestion());
        if (config == null) throw new IllegalArgumentException("El tipo de documento elegido no existe.");

        Responsable responsableDestino = solicitud.idResponsableDestino() != null
                ? responsableService.findById(solicitud.idResponsableDestino())
                : origen.getResponsable();
        Oficina oficinaDestino = solicitud.idOficinaDestino() != null
                ? oficinaService.findById(solicitud.idOficinaDestino())
                : origen.getOficinaDestino();

        validarMismoPredio(aMover, oficinaDestino);

        // ── El acta nueva ────────────────────────────────────────────────────────
        AsignacionActivo destino = new AsignacionActivo();
        destino.setFechaAsignacion(LocalDateTime.now());
        destino.setTipoAsignacion(AsignacionMovimiento.SEPARACION);
        destino.setEstadoAsignacion("ACTIVA");
        destino.setEstado("ACTIVO");
        destino.setResponsable(responsableDestino);
        destino.setResponsableOrigen(origen.getResponsable());
        destino.setOficinaDestino(oficinaDestino);
        destino.setAsignacionPadre(origen);
        destino.setRegistroIdUsuario(usuario.getIdUsuario());
        destino.setObservacion("Separada del acta "
                + (origen.getNumeroAsignacion() != null ? origen.getNumeroAsignacion() : "#" + origen.getIdAsignacionActivo())
                + ". Motivo: " + solicitud.motivo().trim());
        destino.asignarDocumento(config.getGestion(), config.getPrefijoDocumento(), solicitud.nroDocumento());
        destino = asignacionDao.save(destino);

        AsignacionMovimiento movimiento = new AsignacionMovimiento();
        movimiento.setAsignacionOrigen(origen);
        movimiento.setAsignacionDestino(destino);
        movimiento.setTipo(AsignacionMovimiento.SEPARACION);
        movimiento.setMotivo(solicitud.motivo().trim());
        movimiento.setIdUsuario(usuario.getIdUsuario());
        movimiento.setNombreUsuario(usuario.getUsuario());

        List<String> avisos = new ArrayList<>();
        List<Activo> aEnviarAlVsiaf = new ArrayList<>();

        // Primera pasada: marcar las líneas viejas. Va entera y con flush antes de crear
        // las nuevas porque el índice único parcial no admite dos líneas vigentes para el
        // mismo activo ni por un instante.
        for (DetalleAsignacionActivo original : aMover) {
            original.setEstadoDetalle(DetalleAsignacionActivo.TRASLADADO);
            original.setObservacionDetalle(recortar("Trasladado al acta "
                    + destino.getNumeroAsignacion() + " el "
                    + LocalDate.now() + " por " + usuario.getUsuario() + ".", 500));
            detalleDao.save(original);
        }
        detalleDao.flush();

        for (DetalleAsignacionActivo original : aMover) {
            Activo activo = original.getActivo();
            String respAntes = nombreDe(activo.getResponsable());
            String ofiAntes  = activo.getOficina() != null ? activo.getOficina().getNombre() : null;

            DetalleAsignacionActivo nueva = new DetalleAsignacionActivo();
            nueva.setAsignacionActivo(destino);
            nueva.setActivo(activo);
            nueva.setEstadoDetalle(DetalleAsignacionActivo.VIGENTE);
            // Los snapshots se copian del acta original: son lo que decía el documento
            // cuando se emitió, y esa foto no cambia porque el bien cambie de acta.
            nueva.setCodigoActivoSnapshot(original.getCodigoActivoSnapshot() != null
                    ? original.getCodigoActivoSnapshot() : activo.getCodigo());
            nueva.setDescripcionActivoSnapshot(original.getDescripcionActivoSnapshot());
            nueva.setCostoActivoSnapshot(original.getCostoActivoSnapshot());
            nueva.setEstadoActivoSnapshot(original.getEstadoActivoSnapshot());
            nueva.setRegistroIdUsuario(usuario.getIdUsuario());
            detalleDao.save(nueva);

            boolean cambio = aplicarDestinoAlActivo(activo, responsableDestino, oficinaDestino, origen, destino);
            if (cambio) {
                activo.setFecMod(LocalDate.now());
                activo.setUsuMod(usuario.getUsuario());
                activoService.save(activo);
            }

            AsignacionMovimientoDetalle det = new AsignacionMovimientoDetalle();
            det.setActivo(activo);
            det.setCodigoActivo(activo.getCodigo());
            det.setResponsableAntes(respAntes);
            det.setResponsableDespues(nombreDe(activo.getResponsable()));
            det.setOficinaAntes(ofiAntes);
            det.setOficinaDespues(activo.getOficina() != null ? activo.getOficina().getNombre() : null);
            movimiento.agregarDetalle(det);

            // Solo viaja al VSIAF lo que el VSIAF conoce: bienes ya publicados allá.
            if ("ACTIVO".equalsIgnoreCase(activo.getEstado())) {
                if (activo.getOficina() == null || activo.getOficina().getPredio() == null
                        || activo.getOficina().getPredio().getEntidad() == null) {
                    avisos.add("El bien " + activo.getCodigo() + " no tiene oficina, predio o entidad completos: "
                             + "el cambio quedó en la base pero NO se envió al VSIAF.");
                } else {
                    aEnviarAlVsiaf.add(activo);
                    det.setEnvioVsiaf(true);
                }
            }

            registrarHistorial(activo, origen, destino, respAntes, ofiAntes, solicitud.motivo(), usuario,
                    "Separación de acta");
        }

        String resultadoVsiaf = enviarAlVsiaf(aEnviarAlVsiaf, usuario, avisos);
        movimiento.setResultadoVsiaf(resultadoVsiaf);
        if (!avisos.isEmpty()) movimiento.setMensajeVsiaf(String.join(" | ", avisos));
        movimientoDao.save(movimiento);

        // El acta original cambió de contenido después de emitida: que se note.
        origen.setEstadoAsignacion("MODIFICADA");
        origen.setModificacionIdUsuario(usuario.getIdUsuario());
        asignacionDao.save(origen);

        log.info("[SEPARAR] Acta {} → {} · {} bien(es) · usuario {}",
                origen.getNumeroAsignacion(), destino.getNumeroAsignacion(), aMover.size(), usuario.getUsuario());

        return new ResultadoOperacionActa(destino.getIdAsignacionActivo(), destino.getNumeroAsignacion(),
                aMover.size(), resultadoVsiaf, avisos);
    }

    /**
     * Mueve bienes hacia un acta existente.
     * <p>
     * Es la misma operación que en la pantalla se ofrece de dos formas: "trasladar"
     * cuando se sale desde el acta que los tiene, e "incorporar" cuando se entra desde el
     * acta que los quiere. El origen se deduce: cada bien está en una sola acta vigente.
     * <p>
     * Se registra un movimiento por cada acta de origen distinta. Si los cinco bienes
     * elegidos vienen de tres actas, quedan tres registros: cada acta de origen tiene que
     * poder contar en su propio historial qué perdió y por qué.
     */
    @Transactional
    public ResultadoOperacionActa trasladar(TrasladoActaDTO solicitud, Usuario usuario) {

        if (!puedeEditar(usuario)) {
            throw new SecurityException("Solo un ADMINISTRADOR o SUPER USUARIO puede mover bienes entre actas.");
        }
        if (solicitud.idsActivos() == null || solicitud.idsActivos().isEmpty()) {
            throw new IllegalArgumentException("Elegí al menos un bien para mover.");
        }
        if (solicitud.motivo() == null || solicitud.motivo().trim().length() < SeparacionActaDTO.MOTIVO_MINIMO) {
            throw new IllegalArgumentException("Explicá el motivo del traslado (mínimo "
                    + SeparacionActaDTO.MOTIVO_MINIMO + " caracteres). Queda en el historial de las dos actas.");
        }

        AsignacionActivo destino = asignacionDao.findByIdConDetalles(solicitud.idActaDestino())
                .orElseThrow(() -> new IllegalArgumentException("El acta de destino no existe."));

        List<DetalleAsignacionActivo> vigentes = detalleDao.vigentesDeActivos(solicitud.idsActivos());
        validarQueTodosTenganActa(solicitud.idsActivos(), vigentes, destino);

        Responsable responsableDestino = destino.getResponsable();
        Oficina oficinaDestino = destino.getOficinaDestino();
        if (solicitud.adoptarDestino()) {
            validarMismoPredio(vigentes, oficinaDestino);
        }

        List<String> avisos = new ArrayList<>();
        List<Activo> aEnviarAlVsiaf = new ArrayList<>();

        // Igual que al separar: primero se marcan TODAS las líneas viejas y se descarga el
        // cambio, porque el índice único parcial no admite dos líneas vigentes del mismo
        // bien ni por un instante.
        for (DetalleAsignacionActivo original : vigentes) {
            original.setEstadoDetalle(DetalleAsignacionActivo.TRASLADADO);
            original.setObservacionDetalle(recortar("Trasladado al acta " + textoActa(destino)
                    + " el " + LocalDate.now() + " por " + usuario.getUsuario() + ".", 500));
            detalleDao.save(original);
        }
        detalleDao.flush();

        // Un movimiento por acta de origen: son historias distintas que contar.
        Map<Long, AsignacionMovimiento> movimientosPorOrigen = new LinkedHashMap<>();

        for (DetalleAsignacionActivo original : vigentes) {
            Activo activo = original.getActivo();
            AsignacionActivo origen = original.getAsignacionActivo();

            String respAntes = nombreDe(activo.getResponsable());
            String ofiAntes  = activo.getOficina() != null ? activo.getOficina().getNombre() : null;

            DetalleAsignacionActivo nueva = new DetalleAsignacionActivo();
            nueva.setAsignacionActivo(destino);
            nueva.setActivo(activo);
            nueva.setEstadoDetalle(DetalleAsignacionActivo.VIGENTE);
            nueva.setCodigoActivoSnapshot(original.getCodigoActivoSnapshot() != null
                    ? original.getCodigoActivoSnapshot() : activo.getCodigo());
            nueva.setDescripcionActivoSnapshot(original.getDescripcionActivoSnapshot());
            nueva.setCostoActivoSnapshot(original.getCostoActivoSnapshot());
            nueva.setEstadoActivoSnapshot(original.getEstadoActivoSnapshot());
            nueva.setRegistroIdUsuario(usuario.getIdUsuario());
            detalleDao.save(nueva);

            boolean cambio = solicitud.adoptarDestino()
                    && aplicarDestinoAlActivo(activo, responsableDestino, oficinaDestino, origen, destino);
            if (cambio) {
                activo.setFecMod(LocalDate.now());
                activo.setUsuMod(usuario.getUsuario());
                activoService.save(activo);
            }

            AsignacionMovimiento movimiento = movimientosPorOrigen.computeIfAbsent(
                    origen.getIdAsignacionActivo(), id -> {
                        AsignacionMovimiento m = new AsignacionMovimiento();
                        m.setAsignacionOrigen(origen);
                        m.setAsignacionDestino(destino);
                        m.setTipo(AsignacionMovimiento.TRASLADO);
                        m.setMotivo(solicitud.motivo().trim());
                        m.setIdUsuario(usuario.getIdUsuario());
                        m.setNombreUsuario(usuario.getUsuario());
                        return m;
                    });

            AsignacionMovimientoDetalle det = new AsignacionMovimientoDetalle();
            det.setActivo(activo);
            det.setCodigoActivo(activo.getCodigo());
            det.setResponsableAntes(respAntes);
            det.setResponsableDespues(nombreDe(activo.getResponsable()));
            det.setOficinaAntes(ofiAntes);
            det.setOficinaDespues(activo.getOficina() != null ? activo.getOficina().getNombre() : null);
            movimiento.agregarDetalle(det);

            if (cambio && "ACTIVO".equalsIgnoreCase(activo.getEstado())) {
                if (activo.getOficina() == null || activo.getOficina().getPredio() == null
                        || activo.getOficina().getPredio().getEntidad() == null) {
                    avisos.add("El bien " + activo.getCodigo() + " no tiene oficina, predio o entidad completos: "
                             + "el cambio quedó en la base pero NO se envió al VSIAF.");
                } else {
                    aEnviarAlVsiaf.add(activo);
                    det.setEnvioVsiaf(true);
                }
            }

            registrarHistorial(activo, origen, destino, respAntes, ofiAntes, solicitud.motivo(), usuario,
                    solicitud.adoptarDestino() ? "Traslado de acta" : "Traslado de acta (sin cambio de responsable/oficina)");

            // El acta que perdió bienes también cambió después de emitida.
            origen.setEstadoAsignacion("MODIFICADA");
            origen.setModificacionIdUsuario(usuario.getIdUsuario());
            asignacionDao.save(origen);
        }

        String resultadoVsiaf = enviarAlVsiaf(aEnviarAlVsiaf, usuario, avisos);
        for (AsignacionMovimiento m : movimientosPorOrigen.values()) {
            m.setResultadoVsiaf(resultadoVsiaf);
            if (!avisos.isEmpty()) m.setMensajeVsiaf(String.join(" | ", avisos));
            movimientoDao.save(m);
        }

        if (!solicitud.adoptarDestino()) {
            avisos.add("Los bienes se movieron de acta pero conservan su responsable y su oficina: "
                     + "no se envió nada al VSIAF.");
        }

        log.info("[TRASLADAR] {} bien(es) desde {} acta(s) hacia {} · usuario {}",
                vigentes.size(), movimientosPorOrigen.size(), destino.getNumeroAsignacion(), usuario.getUsuario());

        return new ResultadoOperacionActa(destino.getIdAsignacionActivo(), destino.getNumeroAsignacion(),
                vigentes.size(), resultadoVsiaf, avisos);
    }

    /**
     * Cambia el responsable y/o la oficina de la cabecera de un acta ya emitida.
     * <p>
     * No mueve bienes ni crea un acta nueva: es la operación que falta cuando lo único
     * que está mal es el papel, no el contenido. Si se marca {@code propagarABienes}, el
     * cambio también se aplica a los bienes vigentes del acta —mismo criterio y misma
     * validación de predio que usan {@link #trasladar}/incorporar—; si no, solo cambia la
     * cabecera y el VSIAF ni se entera.
     */
    @Transactional
    public ResultadoOperacionActa editarCabecera(EdicionCabeceraActaDTO solicitud, Usuario usuario) {

        if (!puedeEditar(usuario)) {
            throw new SecurityException("Solo un ADMINISTRADOR o SUPER USUARIO puede editar la cabecera de un acta.");
        }
        if (solicitud.motivo() == null || solicitud.motivo().trim().length() < SeparacionActaDTO.MOTIVO_MINIMO) {
            throw new IllegalArgumentException("Explicá el motivo del cambio (mínimo "
                    + SeparacionActaDTO.MOTIVO_MINIMO + " caracteres). Queda en el historial del acta.");
        }
        if (solicitud.idResponsableDestino() == null && solicitud.idOficinaDestino() == null) {
            throw new IllegalArgumentException("Elegí al menos un dato para cambiar: responsable u oficina.");
        }

        AsignacionActivo acta = asignacionDao.findByIdConDetalles(solicitud.idActa())
                .orElseThrow(() -> new IllegalArgumentException("El acta no existe."));

        Responsable nuevoResponsable = solicitud.idResponsableDestino() != null
                ? responsableService.findById(solicitud.idResponsableDestino())
                : acta.getResponsable();
        Oficina nuevaOficina = solicitud.idOficinaDestino() != null
                ? oficinaService.findById(solicitud.idOficinaDestino())
                : acta.getOficinaDestino();

        boolean cambioResponsable = !mismoId(nuevoResponsable, acta.getResponsable(), Responsable::getIdResponsable);
        boolean cambioOficina = !mismoId(nuevaOficina, acta.getOficinaDestino(), Oficina::getIdOficina);
        if (!cambioResponsable && !cambioOficina) {
            throw new IllegalArgumentException("No hay ningún cambio para guardar: elegiste los mismos datos "
                    + "que ya tenía el acta.");
        }

        List<DetalleAsignacionActivo> vigentes = new ArrayList<>();
        if (solicitud.propagarABienes()) {
            for (DetalleAsignacionActivo d : acta.getDetalles()) {
                if (d.estaVigente() && d.getActivo() != null) vigentes.add(d);
            }
            if (vigentes.isEmpty()) {
                throw new IllegalArgumentException("El acta no tiene bienes vigentes para propagar el cambio.");
            }
            validarMismoPredio(vigentes, nuevaOficina);
        }

        acta.setResponsable(nuevoResponsable);
        acta.setOficinaDestino(nuevaOficina);
        acta.setEstadoAsignacion("MODIFICADA");
        acta.setModificacionIdUsuario(usuario.getIdUsuario());
        asignacionDao.save(acta);

        AsignacionMovimiento movimiento = new AsignacionMovimiento();
        movimiento.setAsignacionOrigen(acta);
        movimiento.setAsignacionDestino(null);
        movimiento.setTipo(AsignacionMovimiento.EDICION_CABECERA);
        movimiento.setMotivo(solicitud.motivo().trim());
        movimiento.setIdUsuario(usuario.getIdUsuario());
        movimiento.setNombreUsuario(usuario.getUsuario());

        List<String> avisos = new ArrayList<>();
        List<Activo> aEnviarAlVsiaf = new ArrayList<>();
        int bienesActualizados = 0;

        for (DetalleAsignacionActivo detalle : vigentes) {
            Activo activo = detalle.getActivo();
            String respAntes = nombreDe(activo.getResponsable());
            String ofiAntes  = activo.getOficina() != null ? activo.getOficina().getNombre() : null;

            boolean cambio = aplicarDestinoAlActivo(activo, nuevoResponsable, nuevaOficina, acta, acta);
            if (!cambio) continue;

            activo.setFecMod(LocalDate.now());
            activo.setUsuMod(usuario.getUsuario());
            activoService.save(activo);
            bienesActualizados++;

            AsignacionMovimientoDetalle det = new AsignacionMovimientoDetalle();
            det.setActivo(activo);
            det.setCodigoActivo(activo.getCodigo());
            det.setResponsableAntes(respAntes);
            det.setResponsableDespues(nombreDe(activo.getResponsable()));
            det.setOficinaAntes(ofiAntes);
            det.setOficinaDespues(activo.getOficina() != null ? activo.getOficina().getNombre() : null);
            movimiento.agregarDetalle(det);

            if ("ACTIVO".equalsIgnoreCase(activo.getEstado())) {
                if (activo.getOficina() == null || activo.getOficina().getPredio() == null
                        || activo.getOficina().getPredio().getEntidad() == null) {
                    avisos.add("El bien " + activo.getCodigo() + " no tiene oficina, predio o entidad completos: "
                             + "el cambio quedó en la base pero NO se envió al VSIAF.");
                } else {
                    aEnviarAlVsiaf.add(activo);
                    det.setEnvioVsiaf(true);
                }
            }

            registrarHistorial(activo, acta, acta, respAntes, ofiAntes, solicitud.motivo(), usuario,
                    "Edición de cabecera");
        }

        String resultadoVsiaf = enviarAlVsiaf(aEnviarAlVsiaf, usuario, avisos);
        movimiento.setResultadoVsiaf(resultadoVsiaf);
        if (!avisos.isEmpty()) movimiento.setMensajeVsiaf(String.join(" | ", avisos));
        movimientoDao.save(movimiento);

        log.info("[EDITAR-CABECERA] Acta {} · {} bien(es) actualizado(s) · usuario {}",
                acta.getNumeroAsignacion(), bienesActualizados, usuario.getUsuario());

        return new ResultadoOperacionActa(acta.getIdAsignacionActivo(), acta.getNumeroAsignacion(),
                bienesActualizados, resultadoVsiaf, avisos);
    }

    /**
     * Consigue —o crea, la primera vez que hace falta en la gestión— el acta que sirve
     * de destino cuando un traslado no tiene a dónde ir con claridad.
     * <p>
     * No lleva responsable ni oficina: es un cajón, no un responsable real. Por eso el
     * traslado hacia acá siempre va con {@code adoptarDestino=false} en el front — el
     * bien conserva su responsable y su oficina reales, solo cambia de acta en el papel.
     */
    @Transactional
    public AsignacionActivo obtenerOCrearActaRegularizacion(Usuario usuario) {
        if (!puedeEditar(usuario)) {
            throw new SecurityException("Solo un ADMINISTRADOR o SUPER USUARIO puede usar el acta de regularización.");
        }
        int gestion = LocalDate.now().getYear();
        String numero = "REG-" + gestion;
        return asignacionDao.findFirstByNumeroAsignacion(numero).orElseGet(() -> {
            AsignacionActivo acta = new AsignacionActivo();
            acta.setNumeroAsignacion(numero);
            acta.setTipoAsignacion("REGULARIZACION");
            acta.setEstadoAsignacion("ACTIVA");
            acta.setEstado("ACTIVO");
            acta.setFechaAsignacion(LocalDateTime.now());
            acta.setObservacion("Acta de regularización de la gestión " + gestion
                    + ": agrupa bienes trasladados sin un acta de destino clara.");
            acta.setRegistroIdUsuario(usuario.getIdUsuario());
            AsignacionActivo guardada = asignacionDao.save(acta);
            log.info("[REGULARIZACION] Creada el acta {} · usuario {}", numero, usuario.getUsuario());
            return guardada;
        });
    }

    /**
     * Reintento manual de la sincronización con el VSIAF, para bienes puntuales de un
     * acta — por fila o por lote, desde el modal.
     * <p>
     * No es una operación sobre el acta: no crea {@link AsignacionMovimiento} ni
     * {@link HistorialActivo}. Es un empujón puntual para cuando el sync automático no
     * reintenta solo (quedó en {@code ERROR} y ya se corrigió la causa, o el montaje
     * CIFS estuvo caído y ya volvió). No sirve para el primer envío de un bien
     * {@code PENDIENTE}: eso se publica desde Activos pendientes, otro flujo — por eso
     * esos bienes se descartan acá con aviso en vez de intentarse.
     */
    @Transactional
    public ResultadoOperacionActa subirAlVsiaf(List<Long> idsActivos, Usuario usuario) {
        if (!puedeEditar(usuario)) {
            throw new SecurityException("Solo un ADMINISTRADOR o SUPER USUARIO puede reintentar la subida al VSIAF.");
        }
        if (idsActivos == null || idsActivos.isEmpty()) {
            throw new IllegalArgumentException("Elegí al menos un bien para subir al VSIAF.");
        }

        List<String> avisos = new ArrayList<>();
        List<Activo> aEnviar = new ArrayList<>();

        for (Long id : idsActivos) {
            Activo activo = activoService.findById(id);
            if (activo == null) {
                avisos.add("El bien #" + id + " ya no existe.");
                continue;
            }
            if (!"ACTIVO".equalsIgnoreCase(activo.getEstado())) {
                avisos.add("El bien " + activo.getCodigo() + " está " + activo.getEstado()
                        + ": se publica por primera vez desde Activos pendientes, no desde acá.");
                continue;
            }
            aEnviar.add(activo);
        }

        String resultadoVsiaf = enviarAlVsiaf(aEnviar, usuario, avisos);

        log.info("[SUBIR-VSIAF] {} bien(es) reintentados · usuario {}", aEnviar.size(), usuario.getUsuario());

        return new ResultadoOperacionActa(null, null, aEnviar.size(), resultadoVsiaf, avisos);
    }

    private <T> boolean mismoId(T a, T b, java.util.function.Function<T, Long> idDe) {
        if (a == null || b == null) return a == b;
        Long idA = idDe.apply(a), idB = idDe.apply(b);
        return idA != null && idA.equals(idB);
    }

    /**
     * Todos los bienes elegidos tienen que estar hoy en un acta, y ninguno ya en la de
     * destino.
     * <p>
     * Un bien sin acta vigente no se incorpora desde acá: si nunca se le asignó documento,
     * el lugar para hacerlo es la bandeja de Pendientes, que es donde se carga el
     * preventivo. Traerlo por este camino lo dejaría en un acta ya emitida sin haber
     * pasado por ese control.
     */
    private void validarQueTodosTenganActa(List<Long> pedidos, List<DetalleAsignacionActivo> vigentes,
                                           AsignacionActivo destino) {
        Map<Long, DetalleAsignacionActivo> porActivo = new LinkedHashMap<>();
        for (DetalleAsignacionActivo d : vigentes) porActivo.put(d.getActivo().getIdActivo(), d);

        List<String> sinActa = new ArrayList<>();
        for (Long id : pedidos) {
            if (!porActivo.containsKey(id)) sinActa.add(String.valueOf(id));
        }
        if (!sinActa.isEmpty()) {
            throw new IllegalArgumentException("Hay bienes que no están en ninguna acta vigente ("
                    + String.join(", ", sinActa) + "). A esos se les asigna documento desde Activos pendientes.");
        }

        for (DetalleAsignacionActivo d : vigentes) {
            if (d.getAsignacionActivo().getIdAsignacionActivo().equals(destino.getIdAsignacionActivo())) {
                throw new IllegalArgumentException("El bien " + d.getActivo().getCodigo()
                        + " ya está en esta acta.");
            }
        }
    }

    /* ════════════════════════════ validaciones ════════════════════════════ */

    private void validar(SeparacionActaDTO s) {
        if (s.idActaOrigen() == null) throw new IllegalArgumentException("Falta el acta de origen.");
        if (s.idsActivos() == null || s.idsActivos().isEmpty()) {
            throw new IllegalArgumentException("Elegí al menos un bien para separar.");
        }
        if (s.idConfigGestion() == null) {
            throw new IllegalArgumentException("Elegí el tipo de documento de la nueva acta.");
        }
        if (s.nroDocumento() == null || s.nroDocumento().trim().isEmpty()) {
            throw new IllegalArgumentException("Cargá el número de documento de la nueva acta: "
                    + "es el que la identifica.");
        }
        if (s.motivo() == null || s.motivo().trim().length() < SeparacionActaDTO.MOTIVO_MINIMO) {
            throw new IllegalArgumentException("Explicá el motivo de la separación (mínimo "
                    + SeparacionActaDTO.MOTIVO_MINIMO + " caracteres). Queda en el historial del acta.");
        }
    }

    /** Los detalles elegidos, comprobando que sean de esta acta y estén vigentes. */
    private List<DetalleAsignacionActivo> seleccionarDetalles(AsignacionActivo origen, List<Long> idsActivos) {
        Set<Long> pedidos = new LinkedHashSet<>(idsActivos);
        List<DetalleAsignacionActivo> vigentes = new ArrayList<>();
        List<DetalleAsignacionActivo> elegidos = new ArrayList<>();

        for (DetalleAsignacionActivo d : origen.getDetalles()) {
            if (!d.estaVigente() || d.getActivo() == null) continue;
            vigentes.add(d);
            if (pedidos.contains(d.getActivo().getIdActivo())) elegidos.add(d);
        }

        if (elegidos.size() != pedidos.size()) {
            throw new IllegalArgumentException("Alguno de los bienes elegidos ya no está vigente en esta acta. "
                    + "Cerrá y volvé a abrir el detalle para ver el estado actual.");
        }
        if (elegidos.size() == vigentes.size()) {
            throw new IllegalArgumentException("No se pueden separar TODOS los bienes: el acta original quedaría "
                    + "vacía. Si lo que necesitás es cambiarle los datos, editá la cabecera del acta.");
        }
        return elegidos;
    }

    /**
     * La oficina destino tiene que estar en el mismo predio que el bien.
     * <p>
     * El auxiliar del activo depende del predio y del grupo contable, y viaja al VSIAF
     * como {@code CODAUX}. Separar hacia otro predio dejaría ese código apuntando a un
     * auxiliar que allá no corresponde, y el bien se vería sin auxiliar. Mover un bien
     * entre predios es una transferencia, no una separación de acta: para eso está el
     * módulo de transferencias.
     */
    private void validarMismoPredio(List<DetalleAsignacionActivo> detalles, Oficina oficinaDestino) {
        if (oficinaDestino == null || oficinaDestino.getPredio() == null) return;
        Long predioDestino = oficinaDestino.getPredio().getIdPredio();

        for (DetalleAsignacionActivo d : detalles) {
            Oficina actual = d.getActivo().getOficina();
            if (actual == null || actual.getPredio() == null) continue;
            if (!actual.getPredio().getIdPredio().equals(predioDestino)) {
                throw new IllegalArgumentException("El bien " + d.getActivo().getCodigo()
                        + " está en el predio " + actual.getPredio().getDescrip()
                        + " y la oficina destino pertenece a otro. Un cambio de predio es una transferencia, "
                        + "no una separación de acta.");
            }
        }
    }

    /* ════════════════════════════ efectos sobre el bien ════════════════════════════ */

    /**
     * Deja el bien con el responsable, la oficina y la descripción del acta nueva.
     *
     * @return si algo cambió y por lo tanto hay que guardar
     */
    private boolean aplicarDestinoAlActivo(Activo activo, Responsable responsable, Oficina oficina,
                                           AsignacionActivo origen, AsignacionActivo destino) {
        boolean cambio = false;

        if (responsable != null && (activo.getResponsable() == null
                || !responsable.getIdResponsable().equals(activo.getResponsable().getIdResponsable()))) {
            activo.setResponsable(responsable);
            cambio = true;
        }
        if (oficina != null && (activo.getOficina() == null
                || !oficina.getIdOficina().equals(activo.getOficina().getIdOficina()))) {
            activo.setOficina(oficina);
            cambio = true;
        }

        String nueva = reemplazarEtiquetaDocumento(activo.getDescripcion(),
                origen.getEtiquetaDocumento(), destino.getEtiquetaDocumento());
        if (nueva != null && !nueva.equals(activo.getDescripcion())) {
            activo.setDescripcion(nueva);
            cambio = true;
        }
        return cambio;
    }

    /**
     * Cambia el número de documento que va pegado al principio de la descripción.
     * <p>
     * Al asignar el documento, el sistema antepone {@code "(Prev. 1234) "} a la
     * descripción del bien, y esa descripción es la que viajó a {@code ACTUAL.DBF}. Si el
     * bien pasa a un acta con otro documento y el prefijo se queda, la descripción miente
     * en los dos sistemas y apunta a un acta donde el bien ya no está.
     */
    private String reemplazarEtiquetaDocumento(String descripcion, String etiquetaVieja, String etiquetaNueva) {
        if (descripcion == null || etiquetaNueva == null) return descripcion;

        String limpia = descripcion;
        if (etiquetaVieja != null && limpia.startsWith(etiquetaVieja)) {
            limpia = limpia.substring(etiquetaVieja.length()).trim();
        } else if (limpia.startsWith("(")) {
            // Prefijo de otro formato: se saca igual, es el mismo lugar de la cadena.
            int cierre = limpia.indexOf(')');
            if (cierre > 0 && cierre < 40) limpia = limpia.substring(cierre + 1).trim();
        }

        String resultado = (etiquetaNueva + " " + limpia).trim();
        return resultado.length() > 1024 ? resultado.substring(0, 1024) : resultado;
    }

    /** Encola el UPDATE de cada bien publicado. Un fallo se informa, no se traga. */
    private String enviarAlVsiaf(List<Activo> activos, Usuario usuario, List<String> avisos) {
        if (activos.isEmpty()) return AsignacionMovimiento.VSIAF_NO_APLICA;

        boolean huboError = false;
        for (Activo a : activos) {
            try {
                actualDbfWriterService.actualizarDesdeActivo(
                        a.getCodigo(), a,
                        a.getOficina().getPredio().getEntidad().getEntidadCodigo(),
                        a.getOficina().getPredio().getUnidad(),
                        usuario.getUsuario());

                a.setSincVsiaf(actualDbfWriterService.esModoCola()
                        ? Activo.SINC_EN_COLA : Activo.SINC_CONFIRMADO);
                a.setSincVsiafMensaje(null);
                a.setSincVsiafFecha(LocalDateTime.now());
                activoService.save(a);

            } catch (Exception e) {
                huboError = true;
                log.error("[SEPARAR] No se pudo encolar el UPDATE del bien {}: {}", a.getCodigo(), e.getMessage());
                avisos.add("El bien " + a.getCodigo() + " se movió en el SCIAF pero NO se pudo enviar al VSIAF ("
                         + e.getMessage() + "). Los dos sistemas quedaron distintos.");
                a.setSincVsiaf(Activo.SINC_ERROR);
                a.setSincVsiafMensaje("No se pudo dejar la orden para el VSIAF: " + e.getMessage());
                a.setSincVsiafFecha(LocalDateTime.now());
                activoService.save(a);
            }
        }
        return huboError ? AsignacionMovimiento.VSIAF_ERROR : AsignacionMovimiento.VSIAF_ENCOLADO;
    }

    private void registrarHistorial(Activo activo, AsignacionActivo origen, AsignacionActivo destino,
                                    String respAntes, String ofiAntes, String motivo, Usuario usuario,
                                    String verbo) {
        try {
            HistorialActivo h = new HistorialActivo();
            h.setActivo(activo);
            h.setCodigoActivo(activo.getCodigo());
            h.setTipoEvento("ASIGNACION");
            h.setFechaEvento(LocalDateTime.now());
            h.setIdUsuario(usuario.getIdUsuario());
            h.setNombreUsuario(usuario.getUsuario());
            h.setNombreOficinaAnterior(ofiAntes);
            h.setNombreRespAnterior(respAntes);
            h.setOficinaNueva(activo.getOficina());
            h.setNombreOficinaNueva(activo.getOficina() != null ? activo.getOficina().getNombre() : null);
            h.setResponsableNuevo(activo.getResponsable());
            h.setNombreRespNuevo(nombreDe(activo.getResponsable()));
            String detalleActa = (destino != null
                    && !destino.getIdAsignacionActivo().equals(origen.getIdAsignacionActivo()))
                    ? "pasó de " + textoActa(origen) + " a " + textoActa(destino)
                    : "en el acta " + textoActa(origen);
            h.setDescripcionEvento(verbo + ": " + detalleActa + ". Motivo: " + motivo.trim());
            historialDao.save(h);
        } catch (Exception e) {
            // El historial es importante, pero no al punto de tumbar una separación válida.
            log.warn("[SEPARAR] No se pudo registrar el historial del bien {}: {}",
                    activo.getCodigo(), e.getMessage());
        }
    }

    private String textoActa(AsignacionActivo a) {
        return a.getNumeroAsignacion() != null ? a.getNumeroAsignacion() : "#" + a.getIdAsignacionActivo();
    }

    private String nombreDe(Responsable r) {
        return (r != null && r.getPersona() != null) ? r.getPersona().getNombreCompleto() : null;
    }

    private String recortar(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
