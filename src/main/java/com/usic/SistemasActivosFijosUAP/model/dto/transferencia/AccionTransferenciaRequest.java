package com.usic.SistemasActivosFijosUAP.model.dto.transferencia;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Setter @Getter
public class AccionTransferenciaRequest {
    
    @NotBlank(message = "corrT es requerido")
    private String corrT;

    // Obligatorio para RECHAZADO y OBSERVADO, opcional para FINALIZADO
    private String motivo;
}
