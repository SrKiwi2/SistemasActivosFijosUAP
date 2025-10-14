package com.usic.SistemasActivosFijosUAP.model.repository;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class FuncionesActivoRepo {
   
    private final @Qualifier("pgJdbcTemplate") JdbcTemplate jdbc;

    public String generarCodigoPorCodes(String mun, String pred, String grp) {
        // SELECT simple a la función
        return jdbc.queryForObject(
                "select generar_codigo_activo_by_codes(?, ?, ?)",
                String.class, mun, pred, grp);
    }

    // Para PREVIEW (NO incrementa)
    public String previewCodigoPorCodes(String mun, String pred, String grp) {
        return jdbc.queryForObject(
            "select preview_codigo_activo_by_codes(?, ?, ?)",
            String.class, mun, pred, grp
        );
    }
}
