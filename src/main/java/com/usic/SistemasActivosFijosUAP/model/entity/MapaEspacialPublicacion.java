package com.usic.SistemasActivosFijosUAP.model.entity;

import java.time.LocalDateTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/** Instantánea inmutable de una publicación del mapa, sin alterar datos del VSIAF. */
@Entity
@Table(name = "mapa_espacial_publicacion", uniqueConstraints =
        @UniqueConstraint(name = "uk_mapa_publicacion_predio_numero", columnNames = { "id_predio", "numero" }))
@Getter @Setter
public class MapaEspacialPublicacion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_predio", nullable = false)
    private Predio predio;

    @Column(name = "numero", nullable = false)
    private Long numero;

    @Column(name = "escena", columnDefinition = "text", nullable = false)
    private String escena;

    @Column(name = "fondo_archivo", length = 180)
    private String fondoArchivo;

    @Column(name = "fecha", nullable = false)
    private LocalDateTime fecha;

    @Column(name = "usuario", length = 120)
    private String usuario;
}
