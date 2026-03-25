package com.usic.SistemasActivosFijosUAP.model.entity;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.usic.SistemasActivosFijosUAP.config.AuditoriaConfig;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter @Getter @NoArgsConstructor
@Entity
@Table(name = "transferencia")
public class Transferencia extends AuditoriaConfig {

    private static final long serialVersionUID = 2629195288020321924L;
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idTransferencia;

    @Column(name = "numero_transferencia", length = 30, unique = true)
    private String numeroTransferencia;

    @Column(name = "tipo", length = 20, nullable = false)
    private String tipo;

    @Column(name = "fecha_transferencia", nullable = false)
    private LocalDate fechaTransferencia;

    @Column(name = "fecha_recepcion")
    private LocalDate fechaRecepcion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_responsable_origen", nullable = false)
    private Responsable responsableOrigen;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_oficina_origen")
    private Oficina oficinaOrigen;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_responsable_destino", nullable = false)
    private Responsable responsableDestino;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_oficina_destino", nullable = false)
    private Oficina oficinaDestino;

    @Column(name = "institucion_destino", length = 200)
    private String institucionDestino;

    @Column(name = "documento_referencia", length = 100)
    private String documentoReferencia;

    @Column(name = "observacion", columnDefinition = "TEXT")
    private String observacion;

    @Column(name = "estado_proceso", length = 30, nullable = false)
    private String estadoProceso = "COMPLETADA";

    @OneToMany(mappedBy = "transferencia", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TransferenciaDetalle> detalles = new ArrayList<>();

    public void addDetalle(TransferenciaDetalle d) {
        d.setTransferencia(this);
        this.detalles.add(d);
    }
}
