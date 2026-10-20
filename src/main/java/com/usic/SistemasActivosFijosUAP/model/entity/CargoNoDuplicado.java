package com.usic.SistemasActivosFijosUAP.model.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * Par de cargos que alguien revisó en «Personas y cargos › Cargos» y marcó como distintos:
 * el agrupador no vuelve a sugerir juntarlos. {@code idCargoA < idCargoB}.
 */
@Entity
@Table(name = "cargo_no_duplicado", uniqueConstraints = @UniqueConstraint(name = "uk_cargo_no_dup_par",
        columnNames = { "id_cargo_a", "id_cargo_b" }))
@Getter
@Setter
public class CargoNoDuplicado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "id_cargo_a", nullable = false)
    private Long idCargoA;

    @Column(name = "id_cargo_b", nullable = false)
    private Long idCargoB;

    @Column(name = "id_usuario")
    private Long idUsuario;

    @Column(name = "usuario", length = 60)
    private String usuario;

    @Column(name = "fecha", nullable = false)
    private LocalDateTime fecha = LocalDateTime.now();
}
