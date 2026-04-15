package com.usic.SistemasActivosFijosUAP.model.dto.transferencia;

import java.util.List;

import com.usic.SistemasActivosFijosUAP.model.dto.interoperabilidad.SolTransferenciaDbf;
import com.usic.SistemasActivosFijosUAP.model.dto.interoperabilidad.TransferenciaValidadaDto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class TransferenciaActivoDetalleDto {
    
    // ── Validación (lo que ya tenías) ────────────────────────────────────────
    private SolTransferenciaDbf datos;
    private boolean valida;
    private List<String> errores;
    private boolean activoExiste;
    private boolean predioOrigenExiste;
    private boolean oficinaOrigenExiste;
    private boolean responsableOrigenExiste;
    private boolean predioDestinoExiste;
    private boolean oficinaDestinoExiste;
    private boolean responsableDestinoExiste;
    private boolean yaAprobadaEnBd;
    private TransferenciaValidadaDto.TipoTransferencia tipo;

    // ── Campos enriquecidos — resueltos en backend ───────────────────────────
    private String descripcionActivo;        // Activo.descrip

    private String nombreGrupoContable;      // GrupoContable.nombre
    private String nombreAuxiliar;           // Auxiliar.nombre

    private String nombreOficinaOrigen;      // Oficina.nombre / nomOfic
    private String nombreResponsableOrigen;  // Responsable.nombre
    private String ciResponsableOrigen;      // Responsable.ci
}
