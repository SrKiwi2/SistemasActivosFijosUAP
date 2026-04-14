package com.usic.SistemasActivosFijosUAP.model.dto.interoperabilidad;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class TransferenciaValidadaDto {
    
    private SolTransferenciaDbf datos;

    // ── Tipo determinado por el sistema ─────────────────────────────────────
    private TipoTransferencia tipo;          // INTERNA | EXTERNA

    // ── Estado general ───────────────────────────────────────────────────────
    private boolean valida;
    private List<String> errores;            // lista de mensajes por check fallido

    // ── Checks individuales (para mostrar en tabla con íconos) ───────────────
    private boolean activoExiste;
    private boolean predioOrigenExiste;
    private boolean oficinaOrigenExiste;
    private boolean responsableOrigenExiste;
    private boolean predioDestinoExiste;
    private boolean oficinaDestinoExiste;
    private boolean responsableDestinoExiste;

    // ── Si ya fue procesada en BD ────────────────────────────────────────────
    private boolean yaAprobadaEnBd;

    public enum TipoTransferencia { INTERNA, EXTERNA }
}
