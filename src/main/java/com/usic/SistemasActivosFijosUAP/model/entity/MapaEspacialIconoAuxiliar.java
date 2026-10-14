package com.usic.SistemasActivosFijosUAP.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/** Preferencia visual del mapa, separada del catálogo auxiliar sincronizado con DBF. */
@Entity
@Table(name = "mapa_espacial_icono_auxiliar", uniqueConstraints =
        @UniqueConstraint(name = "uk_mapa_icono_auxiliar", columnNames = "id_auxiliar"))
@Getter @Setter
public class MapaEspacialIconoAuxiliar {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_auxiliar", nullable = false,
            foreignKey = @ForeignKey(name = "fk_mapa_icono_auxiliar"))
    private Auxiliar auxiliar;

    @Column(nullable = false, length = 32)
    private String icono;
}
