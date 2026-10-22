package com.usic.SistemasActivosFijosUAP.model.entity;

import com.usic.SistemasActivosFijosUAP.config.AuditoriaConfig;
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

/** Clasificación histórica exclusiva de adicionales; no participa en la sincronización VSIAF. */
@Entity
@Table(name = "auxiliar_adicional", uniqueConstraints =
        @UniqueConstraint(name = "uk_aux_adicional_grupo_nombre", columnNames = {"id_grupo_contable", "nombre"}))
@Getter @Setter
public class AuxiliarAdicional extends AuditoriaConfig {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idAuxiliarAdicional;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_grupo_contable", nullable = false)
    private GrupoContable grupoContable;

    @Column(nullable = false, length = 255)
    private String nombre;

    @Column(length = 30)
    private String codigo;
}
