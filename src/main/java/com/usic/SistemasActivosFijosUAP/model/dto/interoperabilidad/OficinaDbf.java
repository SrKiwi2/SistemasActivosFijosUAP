package com.usic.SistemasActivosFijosUAP.model.dto.interoperabilidad;

import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class OficinaDbf {
    private String entidadCodigo; // ENTIDAD (Text 4)
    private String unidad;        // UNIDAD  (Text 5)
    private Short codOfi;         // CODOFIC (SmallInt)
    private String nomOfic;       // NOMOFIC (Text 65)
    private String observ;        // OBSERV  (Memo) -> texto
    private LocalDate feult;      // FEULT   (Date)
    private String usuario;       // USUAR   (Text)
    private Short apiEstado;      // API_ESTADO (SmallInt)
}
