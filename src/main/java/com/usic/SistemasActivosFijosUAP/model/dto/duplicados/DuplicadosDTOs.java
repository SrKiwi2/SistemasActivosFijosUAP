package com.usic.SistemasActivosFijosUAP.model.dto.duplicados;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Lo que va y viene en el módulo Activos duplicados. */
public final class DuplicadosDTOs {

    private DuplicadosDTOs() {}

    /** Un activo tal como se muestra para comparar el sobrante con el principal. */
    public record Ficha(
            Long idActivo, String codigo, String descripcion, String marca, String modelo, String serie,
            Long idGrupo, String grupo, Long idAuxiliar, String auxiliar,
            Double costo, Double costoAnterior, LocalDate fechaAdquisicion, LocalDate fechaAnterior, Boolean revaluado,
            Long idPredio, String unidad, String predio,
            Long idOficina, Short codOfi, String oficina,
            Long idResponsable, String codResp, Long idPersona, String persona, String ci,
            String estado, boolean bloqueado, boolean enCustodia, boolean enDuplicados, boolean soloSciaf) {}

    /** Un activo parecido, con por qué se sugiere. */
    public record Gemelo(Long idActivo, String codigo, String descripcion, String oficina, String persona,
                         int puntaje, List<String> motivos) {}

    /** Fila del listado de casos. */
    public record Fila(Long idDuplicado, String codigo, String descripcion, String codigoPrincipal,
                       String unidad, String persona, String oficinaOrigen, String estado, String estadoEnvio,
                       String mensajeEnvio, String numero, LocalDateTime fecha, String usuario) {}

    /** Responsable al que puede volver un bien que sale de la oficina de duplicados. */
    public record Destino(Long idResponsable, String codResp, String nombre, String ci, String cargo,
                          Long idOficina, Short codOfi, String oficina, boolean original) {}

    /**
     * Lo que pasaría al registrar, sin tocar nada: los dos activos, qué impide registrar
     * ({@code bloqueos}), qué conviene saber ({@code avisos}) y a dónde va el sobrante.
     */
    public record Situacion(Ficha sobrante, Ficha principal, List<String> bloqueos, List<String> avisos,
                            String faltanteNumero, String oficinaDestino, boolean creaOficina,
                            boolean creaResponsable, boolean puedeRegistrar) {}

    /**
     * Pedido de registro.
     *
     * @param confirmaAvisos quien registra vio los avisos fuertes (p. ej. que el activo que se
     *                       queda figura como faltante) y confirma igual
     */
    public record Registro(Long idSobrante, Long idPrincipal, String motivo, Boolean confirmaAvisos) {}

    /** Pedido de retiro o anulación. */
    public record Retiro(Long idResponsableDestino, String justificacion) {}

    // ── Notificación (foto guardada al emitir, en JSON) ─────────────────────

    /** Un activo como queda escrito en la notificación. */
    public record BienDoc(String codigo, String descripcion, String descripcionCorta, String marca, String modelo,
                          String serie, String oficina, String predio, String responsable) {}

    /**
     * Lo que dice la notificación. Las fechas van como texto ISO (el JSON se arma sin módulos
     * de fechas). Es lo que se guarda en {@code contenido} y lo que cubre el hash.
     */
    public record Contenido(String numero, String fecha, String ciudad, String firmante,
                            String destinatario, String ci, String cargo, String unidad,
                            String registradoPor, String usuario, String motivo,
                            BienDoc sobrante, BienDoc principal,
                            String faltanteNumero, String oficinaDuplicados) {}

    /** La notificación para el PDF y la verificación: la foto más su estado actual. */
    public record Notificacion(Long idNotificacion, String numero, String numeroImpreso, String token, String hash,
                               String estado, LocalDateTime fechaAnulacion, String motivoAnulacion,
                               String estadoCaso, LocalDateTime fechaRetiro, String justificacionRetiro,
                               boolean integra, Contenido c) {

        /** Huella corta para el papel: los primeros 16 caracteres del hash, en grupos de 4. */
        public String huella() {
            if (hash == null || hash.length() < 16) return hash;
            String h = hash.substring(0, 16).toUpperCase();
            return h.substring(0, 4) + "-" + h.substring(4, 8) + "-" + h.substring(8, 12) + "-" + h.substring(12, 16);
        }
    }
}
