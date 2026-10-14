package com.usic.SistemasActivosFijosUAP.model.entity;

import java.time.LocalDate;

import com.usic.SistemasActivosFijosUAP.config.AuditoriaConfig;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * Acta de donación: un donante, un documento de respaldo y los bienes que entregó.
 * <p>
 * Los bienes donados son activos comunes del SCIAF ({@link Activo} con
 * {@code procedencia = DONACION}) con un correlativo propio que empieza en D
 * ({@code MUN-PRED-GRP-D00001}) y que <b>no</b> se registran en el VSIAF. Esta acta
 * los agrupa para saber de dónde vino cada uno; el detalle de cada bien
 * (marca, serie, medidas…) está en {@link DonacionBien}.
 * <p>
 * El número es {@code DON-<gestión>-<4 dígitos>}, correlativo por gestión, y se saca
 * bajo un turno {@code pg_advisory_xact_lock} (ver {@code DonacionService}).
 */
@Entity
@Table(
    name = "donacion_acta",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_donacion_acta_numero", columnNames = "numero"),
        @UniqueConstraint(name = "uk_donacion_acta_token", columnNames = "token_registro")
    },
    indexes = @Index(name = "idx_donacion_acta_gestion", columnList = "gestion")
)
@Setter
@Getter
public class DonacionActa extends AuditoriaConfig {
    private static final long serialVersionUID = 2629195288020321924L;

    public static final String TIPO_PERSONA = "PERSONA";
    public static final String TIPO_INSTITUCION = "INSTITUCION";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_donacion")
    private Long idDonacion;

    /** DON-2026-0001 */
    @Column(name = "numero", length = 20, nullable = false)
    private String numero;

    @Column(name = "gestion", nullable = false)
    private Integer gestion;

    @Column(name = "correlativo", nullable = false)
    private Integer correlativo;

    /** PERSONA o INSTITUCION. */
    @Column(name = "donante_tipo", length = 15, nullable = false)
    private String donanteTipo;

    @Column(name = "donante_nombre", length = 200, nullable = false)
    private String donanteNombre;

    /** C.I. de la persona o NIT de la institución. */
    @Column(name = "donante_documento", length = 30)
    private String donanteDocumento;

    /** Teléfono o correo de contacto: texto libre. */
    @Column(name = "donante_contacto", length = 120)
    private String donanteContacto;

    /** Número de la nota, convenio o acta de entrega que respalda la donación. */
    @Column(name = "documento_respaldo", length = 120)
    private String documentoRespaldo;

    @Column(name = "fecha_donacion", nullable = false)
    private LocalDate fechaDonacion;

    @Column(name = "observacion", columnDefinition = "text")
    private String observacion;

    /** Destino común de los bienes del acta. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_oficina", foreignKey = @ForeignKey(name = "fk_donacion_oficina"))
    private Oficina oficina;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_responsable", foreignKey = @ForeignKey(name = "fk_donacion_responsable"))
    private Responsable responsable;

    @Column(name = "cantidad_bienes", nullable = false)
    private Integer cantidadBienes;

    /** Suma de los valores referenciales de los bienes, en Bs. */
    @Column(name = "valor_total")
    private Double valorTotal;

    /**
     * Marca única que la pantalla genera al abrir el asistente. Si la respuesta del alta se
     * pierde y el usuario vuelve a pulsar Registrar, el mismo token devuelve el acta ya
     * creada en vez de duplicar los bienes.
     */
    @Column(name = "token_registro", length = 40)
    private String tokenRegistro;

    /**
     * Se incrementa en cada edición. La pantalla la manda al guardar: si otro guardó antes,
     * no coincide y la edición se rechaza en vez de borrar lo que el otro agregó.
     */
    @Column(name = "revision")
    private Integer revision;

    /** ACTIVO o ANULADA (en {@code _estado}). */
    public static final String ANULADA = "ANULADA";

    @Column(name = "motivo_anulacion", columnDefinition = "text")
    private String motivoAnulacion;

    @Column(name = "fecha_anulacion")
    private java.time.LocalDateTime fechaAnulacion;

    @Column(name = "usuario_anulacion", length = 60)
    private String usuarioAnulacion;

    /** Nombre de usuario de quien la registró (además de la auditoría por id). */
    @Column(name = "usuario", length = 60)
    private String usuario;
}
