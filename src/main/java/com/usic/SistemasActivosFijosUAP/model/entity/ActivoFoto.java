package com.usic.SistemasActivosFijosUAP.model.entity;

import com.usic.SistemasActivosFijosUAP.config.AuditoriaConfig;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Fotografía de un activo (hoy, de los activos adicionales), hasta 3 por activo. El archivo
 * vive en {@code uploads/adicionales/fotos/} ({@code ArchivoStorageService}) y puede
 * compartirse entre varias filas (la foto de un alta de N unidades se pone a cada una).
 * Quitar una foto la marca ELIMINADO; el archivo queda.
 */
@Entity
@Table(name = "activo_foto", indexes = @Index(name = "idx_activo_foto_activo", columnList = "id_activo"))
@Setter
@Getter
public class ActivoFoto extends AuditoriaConfig {
    private static final long serialVersionUID = 2629195288020321924L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_foto")
    private Long idFoto;

    /** null solo cuando el activo se borró (la fila queda ELIMINADO). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_activo", foreignKey = @ForeignKey(name = "fk_activo_foto_activo"))
    private Activo activo;

    /** URL pública relativa: /uploads/adicionales/fotos/… */
    @Column(name = "ruta", length = 512, nullable = false)
    private String ruta;

    @Column(name = "nombre_original", length = 200)
    private String nombreOriginal;

    @Column(name = "usuario", length = 60)
    private String usuario;
}
