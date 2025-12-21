package com.usic.SistemasActivosFijosUAP.model.dto.interoperabilidad;

import java.time.LocalDate;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ActivoDbf {
private String unidad;      // UNIDAD
    private String entidad;     // ENTIDAD
    private String codigo;      // CODIGO (Clave principal)
    private Short codCont;      // CODCONT (Grupo Contable)
    private Short codAux;       // CODAUX (Auxiliar)
    private Double vidaUtil;    // VIDAUTIL
    private String descrip;     // DESCRIP
    private Double costo;       // COSTO
    private Double depAcum;     // DEPACU
    private Short mes;          // MES
    private Short ano;          // ANO
    private Boolean bRev;       // B_REV
    private Short dia;          // DIA
    private Short codOfic;      // CODOFIC (Oficina)
    private Short codResp;      // CODRESP (Responsable)
    private String observ;      // OBSERV
    private Boolean bandUfv;    // BAND_UFV
    private Short codEstado;    // CODESTADO
    private String codRube;     // COD_RUBE
    private String nroConv;     // NRO_CONV
    private String orgFin;      // ORG_FIN
    private LocalDate feUlt;    // FEULT
    private String usuar;       // USUAR
    private Short apiEstado;    // API_ESTADO
    private String codigoSec;   // CODIGOSEC
    private String banderas;    // BANDERAS
    private LocalDate fecMod;   // FEC_MOD
    private String usuMod;      // USU_MOD 
}