package com.usic.SistemasActivosFijosUAP.model.dto.movil;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Ficha completa del activo con todo su rastro documental.
 *
 * <p>Va en una sola respuesta para que la app no tenga que encadenar cinco
 * llamadas en una conexión lenta.
 */
public record ActivoDetalleDTO(

        ActivoFichaMovilDTO       ficha,
        List<EventoHistorial>     historial,
        List<TransferenciaResumen> transferencias,
        List<AsignacionResumen>   asignaciones,
        List<MantenimientoResumen> mantenimientos
) {

    /** Un evento de {@code historial_activo}: qué pasó, cuándo y quién lo hizo. */
    public record EventoHistorial(
            Long          idHistorial,
            String        tipoEvento,
            LocalDateTime fecha,
            String        descripcion,
            String        oficinaAnterior,
            String        oficinaNueva,
            String        responsableAnterior,
            String        responsableNuevo,
            String        usuario
    ) {}

    public record TransferenciaResumen(
            Long       idTransferencia,
            String     numero,
            String     tipo,
            LocalDate  fecha,
            String     estado,
            String     oficinaOrigen,
            String     oficinaDestino,
            String     responsableAnterior,
            String     documentoReferencia
    ) {}

    public record AsignacionResumen(
            Long          idAsignacion,
            String        numero,
            String        codigoCompleto,
            LocalDateTime fecha,
            String        tipo,
            String        estado,
            String        responsable,
            String        oficinaDestino,
            String        observacion
    ) {}

    public record MantenimientoResumen(
            Long          idMantenimiento,
            String        tipo,
            LocalDateTime fecha,
            String        responsableTecnico,
            String        problema,
            String        solucion,
            Double        costo,
            LocalDate     proximaFecha,
            String        numeroTicket
    ) {}
}
