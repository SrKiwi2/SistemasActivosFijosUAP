package com.usic.SistemasActivosFijosUAP.model.service.movil;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.usic.SistemasActivosFijosUAP.model.dao.IActivoMovilDao;
import com.usic.SistemasActivosFijosUAP.model.dao.IDetalleAsignacionDao;
import com.usic.SistemasActivosFijosUAP.model.dao.IHistorialActivoDao;
import com.usic.SistemasActivosFijosUAP.model.dao.IMantenimientoDao;
import com.usic.SistemasActivosFijosUAP.model.dao.ITranferenciaDetalleDao;
import com.usic.SistemasActivosFijosUAP.model.dto.movil.ActivoDetalleDTO;
import com.usic.SistemasActivosFijosUAP.model.dto.movil.ActivoFichaMovilDTO;
import com.usic.SistemasActivosFijosUAP.model.entity.Activo;
import com.usic.SistemasActivosFijosUAP.model.entity.AsignacionActivo;
import com.usic.SistemasActivosFijosUAP.model.entity.Oficina;
import com.usic.SistemasActivosFijosUAP.model.entity.Responsable;
import com.usic.SistemasActivosFijosUAP.model.entity.Transferencia;

import lombok.RequiredArgsConstructor;

/**
 * Arma la ficha completa de un activo: datos, historial, transferencias,
 * asignaciones y mantenimientos.
 *
 * <p>Se devuelve todo junto en una respuesta. Podría partirse en un endpoint por
 * pestaña, pero cada viaje extra se paga caro con conexión mala, y estos
 * listados son cortos (unidades o decenas de filas por activo).
 */
@Service
@RequiredArgsConstructor
public class ActivoDetalleMovilService {

    private final IActivoMovilDao         activoDao;
    private final IHistorialActivoDao     historialDao;
    private final ITranferenciaDetalleDao transferenciaDetalleDao;
    private final IDetalleAsignacionDao   detalleAsignacionDao;
    private final IMantenimientoDao       mantenimientoDao;
    private final ActivoMovilMapper       mapper;

    @Transactional(readOnly = true)
    public Optional<ActivoDetalleDTO> detallePorCodigo(String codigo) {

        Optional<Activo> encontrado = activoDao.fichaPorCodigo(codigo);
        if (encontrado.isEmpty()) return Optional.empty();

        Activo a = encontrado.get();
        ActivoFichaMovilDTO ficha = mapper.aFicha(a);

        return Optional.of(new ActivoDetalleDTO(
                ficha,
                historial(a),
                transferencias(a),
                asignaciones(a),
                mantenimientos(a)));
    }

    /** Solo la ficha, sin los listados: es lo que basta para una tarjeta de resultado. */
    @Transactional(readOnly = true)
    public Optional<ActivoFichaMovilDTO> fichaPorCodigo(String codigo) {
        return activoDao.fichaPorCodigo(codigo).map(mapper::aFicha);
    }

    /**
     * Resuelve un lote de códigos. Devuelve solo los encontrados; el que falta lo
     * detecta la app comparando con lo que pidió, y así puede señalar en rojo
     * exactamente qué código no existe.
     */
    @Transactional(readOnly = true)
    public List<ActivoFichaMovilDTO> fichasPorCodigos(List<String> codigos) {
        if (codigos == null || codigos.isEmpty()) return List.of();
        return activoDao.fichasPorCodigos(codigos).stream().map(mapper::aFicha).toList();
    }

    // ── Bloques del detalle ──────────────────────────────────────────────────

    private List<ActivoDetalleDTO.EventoHistorial> historial(Activo a) {
        return historialDao.findByActivoIdActivoOrderByFechaEventoDesc(a.getIdActivo())
                .stream()
                .map(h -> new ActivoDetalleDTO.EventoHistorial(
                        h.getIdHistorial(),
                        h.getTipoEvento(),
                        h.getFechaEvento(),
                        h.getDescripcionEvento(),
                        h.getNombreOficinaAnterior(),
                        h.getNombreOficinaNueva(),
                        h.getNombreRespAnterior(),
                        h.getNombreRespNuevo(),
                        h.getNombreUsuario()))
                .toList();
    }

    private List<ActivoDetalleDTO.TransferenciaResumen> transferencias(Activo a) {
        return transferenciaDetalleDao.historialDeActivo(a.getIdActivo())
                .stream()
                .map(d -> {
                    Transferencia t = d.getTransferencia();
                    return new ActivoDetalleDTO.TransferenciaResumen(
                            (t != null) ? t.getIdTransferencia() : null,
                            (t != null) ? t.getNumeroTransferencia() : null,
                            (t != null) ? t.getTipo() : null,
                            (t != null) ? t.getFechaTransferencia() : null,
                            (t != null) ? t.getEstadoProceso() : null,
                            nombre(d.getOficinaAnterior()),
                            nombre(d.getOficinaDestino()),
                            nombre(d.getResponsableAnterior()),
                            (t != null) ? t.getDocumentoReferencia() : null);
                })
                .toList();
    }

    private List<ActivoDetalleDTO.AsignacionResumen> asignaciones(Activo a) {
        return detalleAsignacionDao.historialDeActivo(a.getIdActivo())
                .stream()
                .map(d -> {
                    AsignacionActivo aa = d.getAsignacionActivo();
                    return new ActivoDetalleDTO.AsignacionResumen(
                            (aa != null) ? aa.getIdAsignacionActivo() : null,
                            (aa != null) ? aa.getNumeroAsignacion() : null,
                            (aa != null) ? aa.getCodigoCompleto() : null,
                            (aa != null) ? aa.getFechaAsignacion() : null,
                            (aa != null) ? aa.getTipoAsignacion() : null,
                            (aa != null) ? aa.getEstadoAsignacion() : null,
                            (aa != null) ? nombre(aa.getResponsable()) : null,
                            (aa != null) ? nombre(aa.getOficinaDestino()) : null,
                            (aa != null) ? aa.getObservacion() : null);
                })
                .toList();
    }

    private List<ActivoDetalleDTO.MantenimientoResumen> mantenimientos(Activo a) {
        return mantenimientoDao.findByActivoIdActivoOrderByFechaMantenimientoDesc(a.getIdActivo())
                .stream()
                .map(m -> new ActivoDetalleDTO.MantenimientoResumen(
                        m.getIdMantenimiento(),
                        m.getTipoMantenimiento(),
                        m.getFechaMantenimiento(),
                        m.getResponsableTecnico(),
                        m.getDescripcionProblema(),
                        m.getDescripcionSolucion(),
                        m.getCosto(),
                        m.getProximaFechaMantenimiento(),
                        m.getNumeroTicket()))
                .toList();
    }

    // ── Auxiliares ───────────────────────────────────────────────────────────

    private String nombre(Oficina o) {
        return (o != null) ? o.getNombre() : null;
    }

    private String nombre(Responsable r) {
        return (r != null && r.getPersona() != null) ? r.getPersona().getNombreCompleto() : null;
    }
}
