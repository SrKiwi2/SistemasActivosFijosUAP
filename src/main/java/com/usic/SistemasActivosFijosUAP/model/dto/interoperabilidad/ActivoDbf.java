package com.usic.SistemasActivosFijosUAP.model.dto.interoperabilidad;

import java.time.LocalDate;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ActivoDbf {
    
    // ── Claves de ubicación ──────────────────────────────────────────────────
    private String  entidadCodigo;  // ENTIDAD
    private String  unidad;         // UNIDAD
    private Short   codOfi;         // CODOFIC
    private String  codResp;        // CODRESP (Numeric en DBF pero lo tratamos como String)

    // ── Claves contables ─────────────────────────────────────────────────────
    private Short   codCont;        // CODCONT
    private Short   codAux;         // CODAUX

    // ── Identificación del activo ────────────────────────────────────────────
    private String  codigo;         // CODIGO
    private String  codigoSec;      // CODIGOSEC
    private String  descrip;        // DESCRIP

    // ── Valores económicos ───────────────────────────────────────────────────
    private Double  costo;          // COSTO
    private Double  depAcu;         // DEPACU
    private Double  costoAnt;       // COSTO_ANT
    private Integer vidaUtil;       // VIDAUTIL (Float → int para años)
    private Integer vutAnt;         // VUT_ANT

    // ── Fecha de adquisición (3 campos SmallInt en DBF) ──────────────────────
    private LocalDate fechaAdq;     // construida desde DIA+MES+ANO

    // ── Fecha anterior (para revaluaciones) ──────────────────────────────────
    private LocalDate fechaAnt;     // construida desde DIA_ANT+MES_ANT+ANO_ANT

    // ── Flags ────────────────────────────────────────────────────────────────
    private Boolean bRev;           // B_REV   (revaluado)
    private Boolean bandUfv;        // BAND_UFV

    // ── Estado y clasificación ───────────────────────────────────────────────
    private Short   codEstado;      // CODESTADO → EstadoActivo
    private String  codRube;        // COD_RUBE
    private String  nroConv;        // NRO_CONV
    private String  codOf;          // ORG_FIN → OrganismoFinanciero
    private String  banderas;       // BANDERAS

    // ── Auditoría DBF ────────────────────────────────────────────────────────
    private LocalDate fechaUlt;     // FEULT
    private String  usuario;        // USUAR
    private Short   apiEstado;      // API_ESTADO
    private LocalDate fecMod;       // FEC_MOD
    private String  usuMod;         // USU_MOD
    private String  observ;         // OBSERV
}