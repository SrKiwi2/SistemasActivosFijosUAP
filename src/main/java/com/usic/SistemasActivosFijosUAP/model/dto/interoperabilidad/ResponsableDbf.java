package com.usic.SistemasActivosFijosUAP.model.dto.interoperabilidad;

import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @Builder @AllArgsConstructor @NoArgsConstructor
public class ResponsableDbf {
    private String entidadCodigo; // ENTIDAD (Text 4)
    private String unidad;        // UNIDAD  (Text 5)
    private Short  codOfi;        // CODOFIC (SmallInt)
    private String codResp;       // CODRESP (SmallInt en DBF, pero guárdalo como texto si quieres conservar formato)
    private String nombre;        // NOMRESP (Text 35)
    private String cargo;         // CARGO   (Text 35)
    private String observ;        // OBSERV  (Memo)
    private String ci;            // CI      (Text 20)
    private LocalDate fechaUlt;   // FEULT   (Date)
    private String usuario;       // USUAR   (Text 8)
    private Short codExp;         // COD_EXP (SmallInt)
    private Short apiEstado;      // API_ESTADO (SmallInt)
}
