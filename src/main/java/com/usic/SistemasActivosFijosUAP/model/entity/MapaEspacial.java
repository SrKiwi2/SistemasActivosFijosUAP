package com.usic.SistemasActivosFijosUAP.model.entity;

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
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Geometría referencial propia del SCIAF; nunca se sincroniza al DBF. */
@Entity
@Table(name = "mapa_espacial", uniqueConstraints = @UniqueConstraint(name = "uk_mapa_espacial_predio", columnNames = "id_predio"))
@Getter @Setter
public class MapaEspacial {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_predio", nullable = false)
    private Predio predio;

    @Column(name = "escena", columnDefinition = "text", nullable = false)
    private String escena = "{\"edificios\":[]}";

    /** null identifica planos anteriores a la separación borrador/publicación. */
    @Column(name = "publicada")
    private Boolean publicada;

    @Column(name = "escena_publicada", columnDefinition = "text")
    private String escenaPublicada;

    @Column(name = "numero_publicacion")
    private Long numeroPublicacion;

    @Column(name = "fondo_publicado", length = 180)
    private String fondoPublicado;

    @Column(name = "fecha_publicacion")
    private LocalDateTime fechaPublicacion;

    @Column(name = "publicado_por", length = 120)
    private String publicadoPor;

    @Column(name = "fecha_modificacion")
    private LocalDateTime fechaModificacion;

    @Column(name = "modificado_por", length = 120)
    private String modificadoPor;

    @Version
    private Long version;
}
