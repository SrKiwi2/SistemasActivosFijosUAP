package com.usic.SistemasActivosFijosUAP.model.service.duplicados;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

/** Sugerencias de "posibles gemelos": sin base de datos. */
class SimilitudActivosTest {

    private static SimilitudActivos.Datos d(String desc) {
        return new SimilitudActivos.Datos(desc, null, null, null, null, null, null, null, null);
    }

    private static SimilitudActivos.Datos d(String desc, Double costoAnt, LocalDate fechaAnt, Boolean rev) {
        return new SimilitudActivos.Datos(desc, null, costoAnt, null, fechaAnt, rev, null, null, null);
    }

    @Test
    void mismaSerieAunqueLaDescripcionCambie() {
        SimilitudActivos.Resultado r = SimilitudActivos.comparar(
                d("COMPUTADORA PORTATIL COLOR NEGRO M:HP, MOD:240 G7, N/S:5CD1234XYZ"),
                d("LAPTOP HP N/S: 5CD1234XYZ"));
        assertTrue(r.puntaje() >= 60, "puntaje " + r.puntaje());
        assertTrue(r.motivos().stream().anyMatch(m -> m.startsWith("Mismo N° de serie")));
    }

    @Test
    void seriesDistintasNoSonElMismoBien() {
        SimilitudActivos.Resultado r = SimilitudActivos.comparar(
                d("SILLA GIRATORIA COLOR NEGRO N/S:AB1234"),
                d("SILLA GIRATORIA COLOR NEGRO N/S:AB9999"));
        assertTrue(r.puntaje() < SimilitudActivos.MINIMO_SUGERENCIA, "puntaje " + r.puntaje());
    }

    @Test
    void mismaDescripcionConTildesYSignosDistintos() {
        SimilitudActivos.Resultado r = SimilitudActivos.comparar(
                d("ESCRITORIO DE MADERA, 3 CAJONES (COLOR CAFÉ)"),
                d("escritorio de madera 3 cajones color cafe"));
        assertTrue(r.motivos().contains("Misma descripción"), r.motivos().toString());
    }

    @Test
    void elRevaluoCopiaCostoYFechaAnteriores() {
        LocalDate f = LocalDate.of(2009, 5, 14);
        SimilitudActivos.Resultado r = SimilitudActivos.comparar(
                d("ESTANTE METALICO DE 5 BANDEJAS", 850.0, f, true),
                d("ESTANTE METALICO 5 BANDEJAS GRIS", 850.0, f, false));
        assertTrue(r.puntaje() >= SimilitudActivos.MINIMO_SUGERENCIA, "puntaje " + r.puntaje());
        assertTrue(r.motivos().contains("Mismo costo anterior al revalúo"));
        assertTrue(r.motivos().contains("Uno revaluado y el otro no"));
    }

    @Test
    void costoIgualSinDescripcionParecidaNoAlcanza() {
        LocalDate f = LocalDate.of(2009, 5, 14);
        SimilitudActivos.Resultado r = SimilitudActivos.comparar(
                d("MESA DE REUNIONES", 850.0, f, true),
                d("IMPRESORA LASER", 850.0, f, false));
        assertTrue(r.puntaje() < SimilitudActivos.MINIMO_SUGERENCIA, "puntaje " + r.puntaje());
    }

    @Test
    void seriesQueNoIdentificanSeIgnoran() {
        assertNull(SimilitudActivos.serieDe("SILLA N/S: S/N"));
        assertNull(SimilitudActivos.serieDe("SILLA SERIE: 000"));
        assertEquals("5CD1234XYZ", SimilitudActivos.serieDe("LAPTOP N/S: 5CD-1234-XYZ"));
    }

    @Test
    void laEnieSeConserva() {
        assertEquals("ARMARIO PEQUEÑO", SimilitudActivos.normalizar("Armario pequeño"));
    }
}
