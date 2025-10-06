package com.usic.SistemasActivosFijosUAP.model.dto.interoperabilidad;

import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class AuxiliarDbf {
  private String entidadCodigo; // ENTIDAD
  private String unidad;        // UNIDAD
  private Short codCont;        // CODCONT
  private Short codAux;         // CODAUX
  private String nomAux;        // NOMAUX
  private String observ;        // OBSERV (Memo)
  private LocalDate fechaUlt;   // FEULT
  private String usuario;       // USUAR
}
