package com.usic.SistemasActivosFijosUAP.model.dto.interoperabilidad;

import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class SolTransferenciaDbf {
    
    private Long    idT;        // ID_T
    private String  nombreT;    // NOMBRE_T
    private LocalDate fechaT;   // FECHA_T
    private String  estadoT;    // ESTADO_T  (PENDIENTE | APROBADO | ANULADO)
    private String  corrT;      // CORR_T    (correlativo, clave de negocio)

    // Datos del activo ORIGEN
    private String  unidadO;    // UNIDAD_O
    private Short   codContO;   // CODCONT_O
    private Short   codAuxO;    // CODAUX_O
    private String  codigoO;    // CODIGO_O  (código del activo)
    private Short   estadoO;    // ESTADO_O
    private Short   codOficO;   // CODOFIC_O
    private Short   codRespO;   // CODRESP_O
    private String  ciSolO;     // CI_SOL_O

    // Datos del DESTINO
    private String  unidadD;    // UNIDAD_D
    private Short   codOficD;   // CODOFIC_D
    private String  ciRecep;    // CI_RECEP
    private String  nomRecep;   // NOM_RECEP
}
