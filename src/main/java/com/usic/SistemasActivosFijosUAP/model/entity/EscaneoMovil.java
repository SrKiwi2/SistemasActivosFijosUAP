package com.usic.SistemasActivosFijosUAP.model.entity;

import java.time.LocalDateTime;

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
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Registro de cada lectura de etiqueta hecha desde la app.
 *
 * <p>Sirve para dos cosas concretas: saber <b>quién revisó qué y cuándo</b>
 * (trazabilidad de los recorridos en campo) y detectar etiquetas que hay que
 * reimprimir, porque queda anotado cuándo lo impreso no coincidió con la BD.
 */
@Entity
@Table(
    name = "escaneo_movil",
    indexes = {
        @Index(name = "idx_escmovil_usuario",  columnList = "id_usuario"),
        @Index(name = "idx_escmovil_codigo",   columnList = "codigo_detectado"),
        @Index(name = "idx_escmovil_fecha",    columnList = "fecha"),
        @Index(name = "idx_escmovil_veredicto",columnList = "veredicto")
    }
)
@Getter
@Setter
public class EscaneoMovil {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idEscaneo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario", foreignKey = @ForeignKey(name = "fk_escmovil_usuario"))
    private Usuario usuario;

    @Size(max = 60)
    @Column(name = "codigo_detectado", length = 60)
    private String codigoDetectado;

    @Size(max = 10)
    @Column(name = "prefijo_entidad", length = 10)
    private String prefijoEntidad;

    /** Contenido íntegro de la etiqueta: si el formato cambia, aquí queda la prueba. */
    @Column(name = "payload_crudo", columnDefinition = "text")
    private String payloadCrudo;

    /** CAMARA | MANUAL */
    @Size(max = 20)
    @Column(name = "origen", length = 20)
    private String origen;

    @Size(max = 40)
    @Column(name = "veredicto", length = 40)
    private String veredicto;

    /** Cuántas diferencias se encontraron entre la etiqueta y la BD. */
    @Column(name = "cantidad_discrepancias")
    private Integer cantidadDiscrepancias;

    @Column(name = "fecha", nullable = false)
    private LocalDateTime fecha = LocalDateTime.now();
}
