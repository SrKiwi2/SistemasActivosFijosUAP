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
import lombok.Setter;

@Setter @Getter
@Entity
@Table(name = "transferencia")
public class Transferencia extends AuditoriaConfig {
    private static final long serialVersionUID = 2629195288020321924L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idTransferencia;

    // QUIÉN TRANSFIERE
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_responsable_origen", nullable = false)
    private Responsable responsableOrigen;

    private LocalDate fechaTransferencia;

    // QUIÉN RECIBE
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_responsable_destino", nullable = false)
    private Responsable responsableDestino;

    private LocalDate fechaRecepcion;

    // Relación con el detalle (1..N)
    @OneToMany(mappedBy = "transferencia", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TransferenciaDetalle> detalles = new ArrayList<>();
}
