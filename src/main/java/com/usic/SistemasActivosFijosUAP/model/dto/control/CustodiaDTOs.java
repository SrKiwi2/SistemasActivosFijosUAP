package com.usic.SistemasActivosFijosUAP.model.dto.control;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTOs de la resolución, los reportes y la conciliación de la custodia de faltantes.
 * Van juntos porque son lecturas chicas del mismo módulo y ninguno se usa por fuera.
 */
public final class CustodiaDTOs {

    private CustodiaDTOs() {}

    /** Responsable al que puede volver un bien que apareció (mismo predio, fuera de la custodia). */
    public record Destino(
            Long    idResponsable,
            String  codigoFuncionario,
            String  nombre,
            String  ci,
            String  cargo,
            Long    idOficina,
            Short   codOfi,
            String  oficina,
            /** Es la misma fila de responsable a la que se le imputó el faltante. */
            boolean original
    ) {}

    /** Predio que tiene oficina de faltantes, para elegir el reporte de custodia. */
    public record PredioCustodia(
            Long   idPredio,
            String unidad,
            String predio,
            Long   idOficina,
            Short  codOfi,
            String oficina,
            long   bienes
    ) {}

    /** Un bien que está hoy en la oficina de faltantes de un predio. */
    public record BienCustodia(
            Long          idActivo,
            String        codigo,
            String        descripcion,
            Long          idPersona,
            String        persona,
            String        ci,
            /** De dónde salió; null en los históricos sin registro. */
            Short         codOfiOrigen,
            String        oficinaOrigen,
            String        numeroActa,
            LocalDateTime fechaEnvio,
            /** EN_CUSTODIA | ABIERTO | RESUELTO | null (histórico sin registro) */
            String        estadoHallazgo,
            String        tipoResolucion
    ) {
        /** Lo que se imprime en la columna "Situación". */
        public String situacion() {
            if (estadoHallazgo == null) return "Histórico sin registro";
            return switch (estadoHallazgo) {
                case "RESUELTO" -> "Resuelto — pendiente de baja";
                case "ABIERTO"  -> "Trasladándose";
                default         -> "En custodia";
            };
        }
    }

    /** Faltantes de una persona en un predio, para el consolidado. */
    public record ConsolidadoFila(
            Long   idPersona,
            String persona,
            String ci,
            String unidad,
            String predio,
            long   abiertos,
            long   enCustodia,
            long   resueltos
    ) {
        public long total() { return abiertos + enCustodia + resueltos; }
    }

    /** Algo que no cierra entre los faltantes y dónde está el bien. */
    public record Alerta(
            /** FUERA_DE_CUSTODIA | SIN_REGISTRO | TRASLADO_TRABADO */
            String        tipo,
            Long          idHallazgo,
            Long          idActivo,
            String        codigo,
            String        descripcion,
            String        persona,
            String        unidad,
            String        oficinaActual,
            String        numeroActa,
            Long          idActa,
            LocalDateTime desde,
            String        detalle
    ) {}

    /** Un bien histórico (en la oficina de faltantes sin acta) propuesto para regularizar. */
    public record BienHistorico(
            Long          idActivo,
            String        codigo,
            String        descripcion,
            /** De dónde vino, según el historial del SCIAF; null si no hay registro. */
            Short         codOfiOrigen,
            String        oficinaOrigen,
            LocalDateTime fechaIngreso
    ) {}

    /** Fila plana de la consulta de históricos (el servicio la agrupa por responsable de custodia). */
    public record HistoricoFila(
            Long          idResponsableCustodia,
            Long          idPersona,
            String        persona,
            String        ci,
            String        unidad,
            String        predio,
            Short         codOfi,
            String        oficina,
            Long          idActivo,
            String        codigo,
            String        descripcion,
            Long          idOficinaOrigen,
            Short         codOfiOrigen,
            String        oficinaOrigen,
            LocalDateTime fechaIngreso
    ) {}

    /** Los históricos de una persona en la oficina de faltantes de un predio. */
    public record GrupoRegularizacion(
            Long                idResponsableCustodia,
            Long                idPersona,
            String              persona,
            String              ci,
            String              unidad,
            String              predio,
            Short               codOfi,
            String              oficina,
            List<BienHistorico> bienes
    ) {}

    /** Pedido de regularización: bienes de UNA persona en UNA oficina de faltantes. */
    public record RegularizarRequest(
            Long       idResponsableCustodia,
            List<Long> idsActivos,
            String     documentoRespaldo,
            String     observacion
    ) {}

    /**
     * Pedido para cambiar el plazo de una notificación vigente (la notificación completa: se
     * identifica por {@code idActa}; {@code idHallazgo} ya no se usa). El plazo corre desde hoy.
     */
    public record NotificarPlazoRequest(
            Long           idHallazgo,
            Long           idActa,
            Integer        plazoDias,
            String         documentoRespaldo,
            java.time.LocalDate fechaDocumento,
            String         observacion
    ) {}

    /** Pedido para corregir los datos de una notificación vigente SIN cambiar el plazo (por {@code idActa}). */
    public record RegenerarNotificacionRequest(
            Long           idHallazgo,
            Long           idActa,
            String         documentoRespaldo,
            java.time.LocalDate fechaDocumento,
            String         observacion
    ) {}

    /**
     * Pedido para emitir la reiterativa de una notificación vigente ({@code idActaAnterior}):
     * documento nuevo con todos sus bienes pendientes y un plazo nuevo (obligatorio).
     * {@code numeroReiterativa} es la que la pantalla cree que sigue (1 o 2): si no coincide, se
     * rechaza (otra pestaña pudo emitirla antes). {@code idHallazgo} ya no se usa.
     */
    public record GenerarReiterativaRequest(
            Long           idHallazgo,
            Long           idActaAnterior,
            Integer        numeroReiterativa,
            String         documentoRespaldo,
            java.time.LocalDate fechaDocumento,
            String         observacion,
            Integer        plazoDias
    ) {}

    public record Conciliacion(
            List<Alerta> fueraDeCustodia,
            List<Alerta> sinRegistro,
            List<Alerta> trasladosTrabados
    ) {
        public int total() {
            return fueraDeCustodia.size() + sinRegistro.size() + trasladosTrabados.size();
        }
    }
}
