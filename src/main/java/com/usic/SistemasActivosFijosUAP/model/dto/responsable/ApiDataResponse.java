package com.usic.SistemasActivosFijosUAP.model.dto.responsable;

import lombok.Getter;
import lombok.Setter;

// Para el mapeo de la respuesta de la API
@Setter @Getter
public class ApiDataResponse {
    // Campos del JSON de la API
    private String per_nombres;
    private String per_ap_paterno;
    private String per_ap_materno;
    private String per_num_doc;
    private String perd_email_personal;
    private String per_sexo;
    private String eo_descripcion; // Nombre de Oficina API
    private String p_descripcion;  // Nombre de Cargo API
}
