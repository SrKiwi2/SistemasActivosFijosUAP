package com.usic.SistemasActivosFijosUAP.model.dto.interoperabilidad;

import java.time.LocalDate;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ActivoDbf {
    // Claves de ubicación (igual que otros DBFs)
    private String  entidadCodigo;
    private String  unidad;
    private Short   codOfi;
    private String  codResp;

    // Claves contables
    private Short   codCont;   // CODCONT → GrupoContable
    private Short   codAux;    // CODAUX  → Auxiliar

    // Datos del activo
    private String  codigo;        // CODIGO  — clave primaria de negocio
    private String  descrip;       // DESCRIP
    private Double  costo;         // COSTO
    private Integer vidaUtil;      // VIDAUTIL
    private LocalDate fechaAdq;    // FECHAADQ
    private String  codOf;         // CODOF → OrganismoFinanciero
    private Short   apiEstado;     // API_ESTADO
    private LocalDate fechaUlt;    // FEULT
    private String  usuario;       // USUAR
    private String  observ;        // OBSERV
}