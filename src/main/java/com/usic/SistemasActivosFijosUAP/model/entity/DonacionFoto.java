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
 * Fotografía de un bien donado. Es del <b>ítem</b> del acta (una tarjeta del asistente: un
 * tipo de bien), no de cada unidad: las N unidades de «10 sillas iguales» comparten sus
 * fotos. Hasta {@code DonacionService.MAX_FOTOS_ITEM} por ítem.
 * <p>
 * El archivo vive en {@code uploads/donaciones/fotos/} ({@code ArchivoStorageService}) y se
 * sirve en {@code /uploads/...}. Quitar una foto la marca ELIMINADO; el archivo queda.
 */
@Entity
@Table(name = "donacion_foto", indexes = @Index(name = "idx_donacion_foto_acta", columnList = "id_donacion, item"))
@Setter
@Getter
public class DonacionFoto extends AuditoriaConfig {
    private static final long serialVersionUID = 2629195288020321924L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_foto")
    private Long idFoto;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_donacion", nullable = false, foreignKey = @ForeignKey(name = "fk_donacion_foto_acta"))
    private DonacionActa donacion;

    /** Número del ítem del acta (DonacionBien.item). */
    @Column(name = "item", nullable = false)
    private Integer item;

    /** URL pública relativa: /uploads/donaciones/fotos/… */
    @Column(name = "ruta", length = 512, nullable = false)
    private String ruta;

    @Column(name = "nombre_original", length = 200)
    private String nombreOriginal;

    @Column(name = "usuario", length = 60)
    private String usuario;
}
