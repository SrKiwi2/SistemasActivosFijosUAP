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
public class GrupoContableDbf {
    private Long codContable; // CODCONT del DBF
    private String nombre; // NOMBRE
    private Integer vidaUtil; // VIDAUTIL
    private Boolean depreciar; // DEPRECIAR
    private Boolean actualizar; // ACTUALIZAR
    private Long idGrupoContable; // usaremos el mismo CODCONT como “id”
}
