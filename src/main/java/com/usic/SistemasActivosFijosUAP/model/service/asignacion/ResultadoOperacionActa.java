package com.usic.SistemasActivosFijosUAP.model.service.asignacion;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;

/**
 * Cómo salió una operación sobre actas, contando por separado lo que se guardó y lo que
 * llegó al VSIAF.
 * <p>
 * Son dos cosas distintas y confundirlas es lo que llevó al módulo a decir "sincronizado"
 * cuando solo había encolado. Una separación puede quedar perfecta en el SCIAF y aun así
 * tener bienes que no se pudieron enviar: eso se informa, no se esconde.
 */
@Getter
public class ResultadoOperacionActa {

    private final Long idActaNueva;
    private final String numeroActaNueva;
    private final int bienesMovidos;
    private final String resultadoVsiaf;
    /** Cosas que salieron distinto de lo ideal y el usuario tiene que saber. */
    private final List<String> avisos;

    public ResultadoOperacionActa(Long idActaNueva, String numeroActaNueva, int bienesMovidos,
                                  String resultadoVsiaf, List<String> avisos) {
        this.idActaNueva = idActaNueva;
        this.numeroActaNueva = numeroActaNueva;
        this.bienesMovidos = bienesMovidos;
        this.resultadoVsiaf = resultadoVsiaf;
        this.avisos = avisos != null ? avisos : new ArrayList<>();
    }

    public boolean tieneAvisos() {
        return !avisos.isEmpty();
    }

    /** Mensaje para el usuario: qué se hizo y, si corresponde, qué quedó pendiente. */
    public String mensaje() {
        StringBuilder sb = new StringBuilder();
        sb.append(bienesMovidos).append(bienesMovidos == 1 ? " bien trasladado" : " bienes trasladados");
        if (numeroActaNueva != null) sb.append(" al acta ").append(numeroActaNueva);
        sb.append('.');
        if (tieneAvisos()) {
            sb.append(" Revisá ").append(avisos.size())
              .append(avisos.size() == 1 ? " advertencia." : " advertencias.");
        }
        return sb.toString();
    }
}
