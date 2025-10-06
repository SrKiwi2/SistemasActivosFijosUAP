package com.usic.SistemasActivosFijosUAP.model.dto.interoperabilidad;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class OrganismoFinDbf {
    
      private Short  gestion;
  private String codOf;
  private String descripcion;
  private String sigla;
}
