package com.usic.SistemasActivosFijosUAP.model.entity;

import java.time.LocalDateTime;

import com.usic.SistemasActivosFijosUAP.config.AuditoriaConfig;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Cabecera de un <b>levantamiento</b>: el recorrido físico de una oficina para
 * comprobar qué activos siguen ahí.
 *
 * <p>El alcance es la <b>oficina</b> y no el responsable, porque en campo se
 * entra a un ambiente, no a una persona. A quién se le imputa cada activo lo
 * guarda {@link InventarioDetalle}, y de ahí sale la vista de faltantes
 * agrupada por responsable.
 *
 * <p>Módulo interno de control: no toca el VSIAF ni los DBF.
 */
@Entity
@Table(
    name = "inventario",
    indexes = {
        @Index(name = "idx_inv_fecha", columnList = "fecha_inicio"),
        @Index(name = "idx_inv_estado", columnList = "_estado"),
        @Index(name = "idx_inv_oficina", columnList = "id_oficina"),
        @Index(name = "idx_inv_estado_lev", columnList = "estado_levantamiento")
    }
)
@Setter @Getter
public class Inventario extends AuditoriaConfig {

    private static final long serialVersionUID = 2629195288020321924L;

    public static final String EN_EJECUCION = "EN_EJECUCION";
    public static final String COMPLETADO   = "COMPLETADO";
    public static final String ORIGEN_WEB   = "WEB";
    public static final String ORIGEN_MOVIL = "MOVIL";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idInventario;
    
    @Column(name = "numero_inventario", nullable = false, unique = true)
    private String numeroInventario;
    
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_oficina", nullable = false)
    private Oficina oficina;
    
    @Column(name = "fecha_inicio", nullable = false)
    private LocalDateTime fechaInicio;
    
    @Column(name = "fecha_fin")
    private LocalDateTime fechaFin;
    
    /**
     * EN_EJECUCION | COMPLETADO (quedan disponibles PLANIFICADO y CONCILIADO).
     *
     * <p>No se llama {@code estado}: {@link AuditoriaConfig} ya define un campo
     * con ese nombre mapeado a la columna {@code _estado}, y dos atributos
     * homónimos en la misma entidad chocan al mapear.
     */
    @Size(max = 20)
    @Column(name = "estado_levantamiento", length = 20, nullable = false)
    private String estadoLevantamiento;
    
    @Column(name = "descripcion", columnDefinition = "text")
    private String descripcion;
    
    // Total de activos esperados en la oficina
    private Integer totalActivosEsperados;
    
    // Total de activos encontrados en el inventario
    private Integer totalActivosEncontrados;
    
    // Archivos de soporte
    @Size(max = 500)
    @Column(name = "ruta_archivo_carga", length = 500)
    private String rutaArchivoCarga;
    
    @Column(name = "fecha_carga_archivo")
    private LocalDateTime fechaCargaArchivo;
    
    // Responsables de la ejecución
    @Size(max = 500)
    @Column(name = "responsables_inventario", length = 500)
    private String responsablesInventario;
    
    // Observaciones del proceso
    @Column(name = "observ", columnDefinition = "text")
    private String observ;

    // ── Control de activos por responsable ───────────────────────────────────

    /**
     * Identificador que genera el teléfono al abrir el levantamiento. Hace
     * idempotente la apertura: si la respuesta se pierde y la app reintenta,
     * se devuelve el mismo levantamiento en vez de crear otro.
     */
    @Size(max = 36)
    @Column(name = "uuid_cliente", length = 36, unique = true)
    private String uuidCliente;

    /** WEB | MOVIL — desde dónde se ejecutó el recorrido. */
    @Size(max = 10)
    @Column(name = "origen", length = 10)
    private String origen;

    /** Quién lo ejecutó. Distinto del usuario de auditoría cuando lo abre un tercero. */
    @Column(name = "id_usuario_ejecutor")
    private Long idUsuarioEjecutor;

    /**
     * Faltantes al cierre. Denormalizado a propósito: el mapa pinta cientos de
     * tiles y contar hallazgos por oficina en cada carga no se sostiene.
     */
    @Column(name = "total_faltantes")
    private Integer totalFaltantes;
}
