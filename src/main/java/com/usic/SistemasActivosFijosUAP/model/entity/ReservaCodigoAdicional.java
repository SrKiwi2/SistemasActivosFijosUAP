package com.usic.SistemasActivosFijosUAP.model.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "reserva_codigo_adicional", indexes = @Index(name = "idx_reserva_adicional_prefijo_vence", columnList = "prefijo, vence"))
@Getter
@Setter
public class ReservaCodigoAdicional {
    @Id
    @Column(length = 40, nullable = false)
    private String token;

    @Column(nullable = false, length = 50)
    private String prefijo;

    @Column(nullable = false)
    private int desde;

    @Column(nullable = false)
    private int cantidad;

    @Column(nullable = false, length = 100)
    private String usuario;

    @Column(nullable = false)
    private LocalDateTime vence;
}
