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
@Table(name = "reserva_correlativo", indexes = {
        @Index(name = "idx_reserva_corr_serie", columnList = "tipo, prefijo, vence"),
        @Index(name = "idx_reserva_corr_token", columnList = "token") })
@Getter
@Setter
public class ReservaCorrelativo {
    @Id
    @Column(length = 50)
    private String id;
    @Column(nullable = false, length = 40)
    private String token;
    @Column(nullable = false)
    private int indice;
    @Column(nullable = false, length = 12)
    private String tipo;
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
