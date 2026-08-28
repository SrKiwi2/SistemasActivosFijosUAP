package com.usic.SistemasActivosFijosUAP.model.entity;

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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Un bien concreto dentro de una operación sobre actas, con lo que cambió para él.
 * <p>
 * Los nombres de responsable y oficina se guardan como texto además de la referencia:
 * el acta es un documento, y dentro de un año la oficina puede haberse renombrado o el
 * responsable haber dejado el cargo. Sin el texto de ese momento, el historial contaría
 * la operación con los datos de hoy y dejaría de coincidir con el papel.
 */
@Entity
@Table(name = "asignacion_movimiento_detalle", indexes = {
    @Index(name = "ix_asig_mov_det_mov",    columnList = "id_movimiento"),
    @Index(name = "ix_asig_mov_det_activo", columnList = "id_activo")
})
@Getter @Setter @NoArgsConstructor
public class AsignacionMovimientoDetalle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_movimiento_detalle")
    private Long idMovimientoDetalle;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_movimiento", nullable = false)
    private AsignacionMovimiento movimiento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_activo")
    private Activo activo;

    /** Código del bien al momento de la operación. */
    @Column(name = "codigo_activo", length = 50)
    private String codigoActivo;

    @Column(name = "resp_antes", length = 200)
    private String responsableAntes;

    @Column(name = "resp_despues", length = 200)
    private String responsableDespues;

    @Column(name = "oficina_antes", length = 200)
    private String oficinaAntes;

    @Column(name = "oficina_despues", length = 200)
    private String oficinaDespues;

    /** ¿Esta línea generó una orden para el VSIAF? */
    @Column(name = "envio_vsiaf")
    private boolean envioVsiaf;

    /** ¿Cambió algo que el VSIAF necesite saber? */
    public boolean hayCambioParaVsiaf() {
        return !igual(responsableAntes, responsableDespues) || !igual(oficinaAntes, oficinaDespues);
    }

    private boolean igual(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }
}
