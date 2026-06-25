package com.usic.SistemasActivosFijosUAP.model.dto;

import java.util.List;

import com.usic.SistemasActivosFijosUAP.model.entity.Activo;

import lombok.Data;

@Data
public class ActivoPendienteItemDTO {
    private Activo activo;
    private String encryptedActivoId;
    private String codigoSnapshot;

    /** true cuando el activo tiene todos los campos obligatorios para subir al VSIAF. */
    private boolean completo;
    /** Lista de campos obligatorios que aún faltan (vacía si está completo). */
    private List<String> faltantes;
}
