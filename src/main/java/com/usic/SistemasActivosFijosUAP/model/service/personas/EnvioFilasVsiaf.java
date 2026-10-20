package com.usic.SistemasActivosFijosUAP.model.service.personas;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.usic.SistemasActivosFijosUAP.model.entity.PersonaCambio;
import com.usic.SistemasActivosFijosUAP.model.service.VsiafApoyoService;
import com.usic.SistemasActivosFijosUAP.model.service.supervision.ResponsableGestionService;

import lombok.RequiredArgsConstructor;

/**
 * Después del commit de un cambio de «Personas y cargos» (ficha o cargos): manda cada fila
 * tocada a RESP.DBF por la cola y anota en su entrada de historial cómo le fue. Cada fila
 * atrapa su propio error: una que falla no frena a las demás.
 */
@Component
@RequiredArgsConstructor
public class EnvioFilasVsiaf {

    private static final Logger log = LoggerFactory.getLogger(EnvioFilasVsiaf.class);

    private final ResponsableGestionService responsableGestionService;

    public record Resumen(int enviados, int fallidos, int sinEnviar) {
        /** Texto para el usuario, a continuación de «Cambios guardados en el SCIAF…». */
        public String texto() {
            StringBuilder msg = new StringBuilder();
            if (enviados > 0) msg.append(" Enviados al VSIAF: ").append(enviados).append('.');
            if (sinEnviar > 0) msg.append(" Sin enviar (aún no están en el VSIAF): ").append(sinEnviar).append('.');
            if (fallidos > 0) msg.append(" NO llegaron al VSIAF: ").append(fallidos)
                    .append(" (quedaron pendientes: reenvíelas desde Responsables; la sincronización no las pisa).");
            return msg.toString();
        }
    }

    /** Las filas que ya estaban en el VSIAF se marcan pendientes en la transacción del cambio (ver {@link #marcarEnCamino}). */
    public static boolean marcarEnCamino(String estadoVsiafAntes) {
        return VsiafApoyoService.EST_VSIAF.equals(estadoVsiafAntes) || VsiafApoyoService.EST_EN_COLA.equals(estadoVsiafAntes);
    }

    /**
     * @param porFila      entradas de historial con {@code idResponsable} (las demás se saltan)
     * @param estadoAntes  estado en el VSIAF de cada fila antes del cambio (VsiafApoyoService.EST_*).
     *                     Las que estaban en el VSIAF (o en cola) se mandan aunque hoy figuren
     *                     pendientes: esa marca la puso el propio cambio para que la sync no las
     *                     pise mientras viajan; al encolarse bien, se desmarcan.
     */
    public Resumen enviar(List<PersonaCambio> porFila, Map<Long, String> estadoAntes, String usuarioNombre) {
        int enviados = 0, fallidos = 0, sinEnviar = 0;
        for (PersonaCambio c : porFila) {
            if (c.getIdResponsable() == null) continue;
            try {
                String antes = estadoAntes.get(c.getIdResponsable());
                boolean yaEstaba = marcarEnCamino(antes);
                VsiafApoyoService.Envio e = responsableGestionService.enviarFilaAlVsiaf(c.getIdResponsable(), usuarioNombre, yaEstaba);
                if (e == null && VsiafApoyoService.EST_ERROR.equals(estadoAntes.get(c.getIdResponsable()))) {
                    // Está en el VSIAF pero su último envío fue rechazado: nadie lo reenvía solo.
                    c.setEstadoVsiaf(PersonaCambio.VSIAF_FALLO);
                    c.setDetalleVsiaf("El VSIAF rechazó el envío anterior de esta fila: reenvíela desde Responsables.");
                    fallidos++;
                } else if (e == null) {
                    c.setEstadoVsiaf(PersonaCambio.VSIAF_PENDIENTE_ALTA);
                    c.setDetalleVsiaf("No estaba en el VSIAF: su alta llevará los datos nuevos.");
                    sinEnviar++;
                } else if (e.ok()) {
                    c.setEstadoVsiaf(PersonaCambio.VSIAF_ENVIADO);
                    c.setDetalleVsiaf(recortar(e.mensaje()));
                    enviados++;
                } else {
                    c.setEstadoVsiaf(PersonaCambio.VSIAF_FALLO);
                    c.setDetalleVsiaf(recortar(e.mensaje()));
                    fallidos++;
                }
            } catch (Exception ex) {
                log.warn("[PERSONAS] Fila {} no se pudo enviar al VSIAF: {}", c.getIdResponsable(), ex.getMessage());
                c.setEstadoVsiaf(PersonaCambio.VSIAF_FALLO);
                c.setDetalleVsiaf(recortar(ex.getMessage()));
                fallidos++;
            }
        }
        return new Resumen(enviados, fallidos, sinEnviar);
    }

    private static String recortar(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.length() > 500 ? t.substring(0, 500) : t;
    }
}
