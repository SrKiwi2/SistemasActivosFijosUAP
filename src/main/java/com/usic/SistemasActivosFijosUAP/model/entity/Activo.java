package com.usic.SistemasActivosFijosUAP.model.entity;

import java.time.LocalDate;

import com.usic.SistemasActivosFijosUAP.config.AuditoriaConfig;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(
    name = "activo",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_activo_codigo", columnNames = {"codigo"})
    },
    indexes = {
        @Index(name = "idx_activo_codigo", columnList = "codigo"),
        @Index(name = "idx_activo_codigosec", columnList = "codigo_sec"),
        @Index(name = "idx_activo_oficina", columnList = "id_oficina"),
        @Index(name = "idx_activo_responsable", columnList = "id_responsable"),
        @Index(name = "idx_activo_grupo", columnList = "id_grupo_contable"),
        @Index(name = "idx_activo_auxiliar", columnList = "id_auxiliar")
    }
)
@Setter
@Getter
public class Activo extends AuditoriaConfig{
    private static final long serialVersionUID = 2629195288020321924L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idActivo;

    // DBF: CODIGO (Text) — suele ser único a nivel institución
    @NotBlank
    @Size(max = 60)
    @Column(name = "codigo", length = 60, nullable = false)
    private String codigo;

    // DBF: CODIGOSEC (Text)
    @Size(max = 60)
    @Column(name = "codigo_sec", length = 60)
    private String codigoSec;

    // Alias corto opcional (puedes poblar con un trunc de descripción si quieres)
    @Size(max = 255)
    @Column(name = "nombre", length = 255)
    private String nombre;

    // DBF: DESCRIP (Text)
    @NotBlank
    @Size(max = 1024)
    @Column(name = "descripcion", length = 1024, nullable = false)
    private String descripcion;

    // DBF: Costo y depreciación acumulada (Float)
    private Double costo;

    private Double depreciacionAcum;

    private Integer vidaUtil;

    private Integer vidaUtilAnterior;

    private LocalDate fechaAdquisicion;

    private LocalDate fechaAnterior;

    private Double costoAnterior;

    private Boolean revaluado;

    private Boolean bandUfv;

     @Size(max = 120)
    @Column(name = "banderas", length = 120)
    private String banderas;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_estado_activo", foreignKey = @ForeignKey(name = "fk_activo_estado"))
    private EstadoActivo estadoActivo;

    // DBF: CODCONT -> GrupoContable
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_grupo_contable", foreignKey = @ForeignKey(name = "fk_activo_grupo"))
    private GrupoContable grupoContable;

    // DBF: CODAUX -> Auxiliar (si existe, opcional)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_auxiliar", foreignKey = @ForeignKey(name = "fk_activo_auxiliar"))
    private Auxiliar auxiliar;

    // DBF: ENTIDAD+UNIDAD+CODOFIC -> Oficina; CODRESP -> Responsable (en esa oficina)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_oficina", foreignKey = @ForeignKey(name = "fk_activo_oficina"))
    private Oficina oficina;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_responsable", foreignKey = @ForeignKey(name = "fk_activo_responsable"))
    private Responsable responsable;

    // DBF: ORG_FIN (Text) -> OrganismoFinanciero (opcional, si lo resolvemos)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_organismo_financiero", foreignKey = @ForeignKey(name = "fk_activo_orgfin"))
    private OrganismoFinanciero organismoFinanciero;

    // Otros campos DBF directos
    @Lob
    @Column(name = "observ", columnDefinition = "text")
    private String observ;

    @Size(max = 60)
    @Column(name = "org_fin_code", length = 60)
    private String orgFinCode;          // DBF: ORG_FIN tal cual texto

    @Size(max = 60)
    @Column(name = "cod_rube", length = 60)
    private String codRube;             // DBF: COD_RUBE

    @Size(max = 60)
    @Column(name = "nro_conv", length = 60)
    private String nroConv;             // DBF: NRO_CONV

    @Column(name = "api_estado", columnDefinition = "SMALLINT")
    private Short apiEstado;            // DBF: API_ESTADO

    // DBF: FEULT / USUAR + FEC_MOD / USU_MOD
    @Column(name = "fecha_ult")
    private LocalDate fechaUlt;

    @Size(max = 60)
    @Column(name = "usuario", length = 60)
    private String usuario;

    @Column(name = "fec_mod")
    private LocalDate fecMod;

    @Size(max = 60)
    @Column(name = "usu_mod", length = 60)
    private String usuMod;
}
