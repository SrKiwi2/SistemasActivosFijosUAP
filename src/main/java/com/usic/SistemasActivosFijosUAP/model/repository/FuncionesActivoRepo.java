package com.usic.SistemasActivosFijosUAP.model.repository;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class FuncionesActivoRepo {

    // Compartido por todas las altas de la serie normal. El turno vive hasta COMMIT/ROLLBACK.
    private static final long TURNO_REGISTRO_ACTIVOS = 0x5C1AFAC71L;
   
    private final @Qualifier("pgJdbcTemplate") JdbcTemplate jdbc;

    public void tomarTurnoRegistro() {
        jdbc.queryForObject("select count(*) from (select pg_advisory_xact_lock(?)) t",
                Long.class, TURNO_REGISTRO_ACTIVOS);
    }

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
