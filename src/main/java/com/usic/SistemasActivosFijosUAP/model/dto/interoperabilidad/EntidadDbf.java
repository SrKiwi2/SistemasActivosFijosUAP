package com.usic.SistemasActivosFijosUAP.model.dto.interoperabilidad;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EntidadDbf {
    private Short gestion; // GESTION
    private String entidadCodigo; // ENTIDAD (Text, viene como "148" etc.)
    private String descripcion; // DESC_ENT
    private String sigla; // SIGLA_ENT
    private Short sectorEnt; // SECTOR_ENT
    private Short subsecEnt; // SUBSEC_ENT
    private Short areaEnt; // AREA_ENT
    private Short subareaEnt; // SUBAREAENT
    private Short nivelInst; // NIVEL_INST
}
