package com.usic.SistemasActivosFijosUAP.model.dto.interoperabilidad;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class UnidadAdminDbf {
        private String entidadCodigo; // ENTIDAD
    private String unidad;        // UNIDAD
    private String descrip;       // DESCRIP
    private String ciudad;        // CIUDAD
    private Short  estadoUni;     // ESTADOUNI
}
