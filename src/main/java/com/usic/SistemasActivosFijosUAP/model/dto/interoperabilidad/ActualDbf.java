package com.usic.SistemasActivosFijosUAP.model.dto.interoperabilidad;

import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ActualDbf {
    private String entidadCodigo;  // ENTIDAD Text 4 (0148/148)
    private String unidad;         // UNIDAD Text 5
    private String codigo;         // CODIGO Text 15
    private String codigoSec;      // CODIGOSEC Text 15
    private String descripcion;    // DESCRIP Text 150

    private Short  codCont;        // CODCONT
    private Short  codAux;         // CODAUX
    private Double costo;          // COSTO
    private Double depAcum;        // DEPACU
    private Integer vidaUtil;      // VIDAULT (VIDAUTIL)
    private Integer vidaUtilAnt;   // VUT_ANT

    private Integer dia;  private Integer mes;  private Integer ano;
    private Integer diaAnt; private Integer mesAnt; private Integer anoAnt;

    private Boolean bRev;   // B_REV
    private Boolean bandUfv; // BAND_UFV
    private Short   codEstado; // CODESTADO

    private String codRube;    // COD_RUBE
    private String nroConv;    // NRO_CONV
    private String orgFinCode; // ORG_FIN

    private LocalDate fechaUlt; // FEULT
    private String usuario;     // USUAR
    private Short apiEstado;    // API_ESTADO
    private String banderas;    // BANDERAS
    private LocalDate fecMod;   // FEC_MOD
    private String usuMod;      // USU_MOD

    private Short codOfi;       // CODOFIC
    private String codRespTxt;  // CODRESP (guardamos como texto)
    private String observ;      // OBSERV (Memo normalizado)

        private String acciones;   // HTML
    private String idEnc;      // "" cuando viene de DBF
    private String source;    
}
