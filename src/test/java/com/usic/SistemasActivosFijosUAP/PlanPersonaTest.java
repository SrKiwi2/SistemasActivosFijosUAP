package com.usic.SistemasActivosFijosUAP;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.usic.SistemasActivosFijosUAP.model.service.personas.PlanPersona;
import com.usic.SistemasActivosFijosUAP.model.service.personas.PlanPersona.CambioFila;
import com.usic.SistemasActivosFijosUAP.model.service.personas.PlanPersona.DatosPersona;
import com.usic.SistemasActivosFijosUAP.model.service.personas.PlanPersona.Decision;
import com.usic.SistemasActivosFijosUAP.model.service.personas.PlanPersona.Fila;
import com.usic.SistemasActivosFijosUAP.model.service.personas.PlanPersona.Resultado;

/** Cálculo de «Personas y cargos»: qué cambia en cada fila (sin base de datos). */
class PlanPersonaTest {

    private static final DatosPersona ALEIDA = persona(696, "ALEIDA", "ROJAS", "POQUECHOQUE", "4087388", false);

    @Test
    void cargoSoloEnLasOficinasMarcadas() {
        List<Fila> filas = List.of(
                fila(1, 696, 100, "RESP. CARRERA DE ARQUITECTURA"),
                fila(2, 696, 184, "RESP. CARRE. DE ARQUITECTURA"),
                fila(3, 696, 200, "DOCENTE"));
        Resultado r = calcular(ALEIDA, List.of(), filas,
                new Decision(696L, List.of(), null, null, null, null, "resp. carrera de arquitectura", List.of(1L, 2L)), List.of());
        assertTrue(r.errores().isEmpty(), r.errores().toString());
        assertEquals(1, r.filasQueCambian());          // la 1 ya tenía ese cargo; la 3 no está marcada
        CambioFila c = cambio(r, 2);
        assertTrue(c.cambiaCargo());
        assertEquals("RESP. CARRERA DE ARQUITECTURA", c.cargoDespues());
        assertFalse(c.cambiaPersona());
        assertFalse(c.cambiaDatos());
        assertFalse(cambio(r, 3).hayCambio());
    }

    @Test
    void unificarPasaLasFilasYUniformaNombreYCi() {
        DatosPersona alvaro = persona(43, "ALVARO MARCELO", "CACERES", "BEJARANO", "7479551", false);
        DatosPersona aMarcelo = persona(688, "A MARCELO", "CACERES", "BEJARANO", null, false);
        List<Fila> filas = List.of(fila(10, 43, 177, "RESP. PARQUE AUTOMOTOR UAP"), fila(11, 688, 177, "RESP. PARQUE AUTOMOTOR UAP"));
        Resultado r = calcular(alvaro, List.of(aMarcelo), filas,
                new Decision(43L, List.of(688L), "ALVARO MARCELO", "CACERES", "BEJARANO", "7479551", null, List.of()), List.of(43L));
        assertTrue(r.errores().isEmpty(), r.errores().toString());
        CambioFila c = cambio(r, 11);
        assertTrue(c.cambiaPersona());
        assertTrue(c.cambiaDatos());
        assertEquals("A MARCELO CACERES BEJARANO", c.nombreAntes());
        assertEquals("ALVARO MARCELO CACERES BEJARANO", c.nombreDespues());
        assertEquals("7479551", c.ciDespues());
        assertFalse(cambio(r, 10).hayCambio());
        assertEquals(List.of(688L), r.idsSecundarias());
    }

    @Test
    void ciDistintosSeAvisanPeroNoBloquean() {
        DatosPersona a = persona(43, "ALVARO MARCELO", "CACERES", "BEJARANO", "7479551", false);
        DatosPersona b = persona(126, "ALVARO MARCELO", "CACERES", "BEJARANO", "7479561", false);
        Resultado r = calcular(a, List.of(b), List.of(fila(1, 126, 355, "X")),
                new Decision(43L, List.of(126L), "ALVARO MARCELO", "CACERES", "BEJARANO", "7479551", null, List.of()), List.of(43L));
        assertTrue(r.errores().isEmpty(), r.errores().toString());
        assertTrue(r.ciEnConflicto());
    }

    @Test
    void ciDeOtraPersonaFueraDelGrupoBloquea() {
        Resultado r = calcular(ALEIDA, List.of(), List.of(fila(1, 696, 100, "X")),
                new Decision(696L, List.of(), null, null, null, null, "Y", List.of(1L)), List.of(696L, 999L));
        assertTrue(r.errores().stream().anyMatch(e -> e.contains("ya es de otra persona")));
    }

    @Test
    void especialSeSumaConAvisoPeroNoEsPrincipal() {
        DatosPersona dev = persona(586, "NANCY", "ACUNA ALVARES", "DEVOLUCION", null, false);
        Resultado r = calcular(ALEIDA, List.of(dev), List.of(fila(1, 586, 9, "X")),
                new Decision(696L, List.of(586L), null, null, null, null, null, List.of()), List.of());
        assertTrue(r.errores().stream().noneMatch(e -> e.contains("marca del VSIAF")), r.errores().toString());
        assertTrue(r.avisos().stream().anyMatch(a -> a.contains("marca del VSIAF")));
        Resultado comoPrincipal = calcular(dev, List.of(), List.of(fila(1, 586, 9, "X")),
                new Decision(586L, List.of(), null, null, null, null, null, List.of()), List.of());
        assertTrue(comoPrincipal.errores().stream().anyMatch(e -> e.contains("no puede ser la principal")));
    }

    @Test
    void elComodinSinDatosNoSeSuma() {
        DatosPersona sd = persona(250, "SIN DATOS", null, null, null, false);
        Resultado r = calcular(ALEIDA, List.of(sd), List.of(fila(1, 250, 9, "X")),
                new Decision(696L, List.of(250L), null, null, null, null, null, List.of()), List.of());
        assertTrue(r.errores().stream().anyMatch(e -> e.contains("filas sin nombre")), r.errores().toString());
    }

    @Test
    void dosPersonasConUsuarioNoSeUnifican() {
        DatosPersona a = persona(1, "ANA", "PEREZ", "LIMA", "5000001", true);
        DatosPersona b = persona(2, "ANA", "PEREZ", "LIMA", null, true);
        Resultado r = calcular(a, List.of(b), List.of(fila(1, 2, 9, "X")),
                new Decision(1L, List.of(2L), null, null, null, null, null, List.of()), List.of(1L));
        assertTrue(r.errores().stream().anyMatch(e -> e.contains("usuario del sistema")));
    }

    @Test
    void dosCustodiasEnLaMismaOficinaBloquean() {
        DatosPersona b = persona(2, "ALEIDA", "ROJAS", "POQUECHOQUE", null, false);
        Fila c1 = new Fila(1L, 696L, 50L, "CULP", (short) 372, "1", "FALTANTES", "X", 0, true, false, "VSIAF");
        Fila c2 = new Fila(2L, 2L, 50L, "CULP", (short) 372, "2", "FALTANTES", "X", 0, true, false, "VSIAF");
        Resultado r = calcular(ALEIDA, List.of(b), List.of(c1, c2),
                new Decision(696L, List.of(2L), null, null, null, null, null, List.of()), List.of(696L));
        assertTrue(r.errores().stream().anyMatch(e -> e.contains("custodias")));
    }

    @Test
    void filaPendienteNoSeEnviaAlVsiaf() {
        Fila pend = new Fila(1L, 696L, 9L, "CULP", (short) 9, "1", "OF", "X", 0, false, true, "PENDIENTE");
        Resultado r = calcular(ALEIDA, List.of(), List.of(pend),
                new Decision(696L, List.of(), null, null, null, null, "Y", List.of(1L)), List.of(696L));
        assertTrue(cambio(r, 1).hayCambio());
        assertFalse(cambio(r, 1).enviaVsiaf());
        assertEquals(0, r.filasAlVsiaf());
    }

    @Test
    void cargoLargoYSinOficinasMarcadasBloquean() {
        Resultado largo = calcular(ALEIDA, List.of(), List.of(fila(1, 696, 1, "X")),
                new Decision(696L, List.of(), null, null, null, null, "X".repeat(41), List.of(1L)), List.of());
        assertTrue(largo.errores().stream().anyMatch(e -> e.contains("40 caracteres")));
        Resultado sin = calcular(ALEIDA, List.of(), List.of(fila(1, 696, 1, "X")),
                new Decision(696L, List.of(), null, null, null, null, "Y", List.of()), List.of());
        assertTrue(sin.errores().stream().anyMatch(e -> e.contains("Marque al menos una")));
    }

    @Test
    void nadaQueCambiarEsError() {
        Resultado r = calcular(ALEIDA, List.of(), List.of(fila(1, 696, 1, "X")),
                new Decision(696L, List.of(), null, null, null, null, null, List.of()), List.of());
        assertTrue(r.errores().stream().anyMatch(e -> e.contains("nada que cambiar")));
    }

    @Test
    void laFirmaCambiaSiCambiaElEstadoDePartida() {
        Decision d = new Decision(696L, List.of(), null, null, null, null, "Y", List.of(1L));
        String f1 = calcular(ALEIDA, List.of(), List.of(fila(1, 696, 1, "X")), d, List.of()).firma();
        String f2 = calcular(ALEIDA, List.of(), List.of(fila(1, 696, 1, "OTRO")), d, List.of()).firma();
        String f3 = calcular(ALEIDA, List.of(), List.of(fila(1, 696, 1, "X")), d, List.of()).firma();
        assertNotEquals(f1, f2);
        assertEquals(f1, f3);
    }

    @Test
    void nomrespIgualQueElWriterYRecortaA35() {
        DatosPersona larga = persona(1, "MARIA DE LOS ANGELES", "FERNANDEZ", "VILLAVICENCIO", "5000002", false);
        assertTrue(PlanPersona.nomresp(larga).length() <= PlanPersona.LARGO_NOMRESP);
        assertTrue(PlanPersona.nomresp(larga).startsWith("MARIA DE LOS ANGELES FERNANDEZ"));
    }

    @Test
    void filaDeOtraPersonaNoSeSumaYEseRegistroNoSeFusiona() {
        DatosPersona b = persona(2, "ALEIDA", "ROJAS", "POQUECHOQUE", null, false);
        List<Fila> filas = List.of(fila(1, 696, 100, "X"), fila(20, 2, 101, "X"), fila(21, 2, 102, "X"));
        Resultado r = PlanPersona.calcular(ALEIDA, List.of(b), filas,
                new Decision(696L, List.of(2L), "ALEIDA", "ROJAS", "POQUECHOQUE", "4087388", null, List.of(), List.of(21L)),
                Set.of(), List.of(696L));
        assertTrue(r.errores().isEmpty(), r.errores().toString());
        assertTrue(cambio(r, 20).cambiaPersona());
        assertFalse(cambio(r, 21).hayCambio());
        assertTrue(r.idsFusionar().isEmpty());          // conserva la 21: sigue vigente
        assertEquals(List.of(2L), r.idsSecundarias());
    }

    @Test
    void sumadaEnteraSeFusiona() {
        DatosPersona b = persona(2, "ALEIDA", "ROJAS", "POQUECHOQUE", null, false);
        Resultado r = calcular(ALEIDA, List.of(b), List.of(fila(20, 2, 101, "X")),
                new Decision(696L, List.of(2L), "ALEIDA", "ROJAS", "POQUECHOQUE", "4087388", null, List.of()), List.of(696L));
        assertEquals(List.of(2L), r.idsFusionar());
    }

    @Test
    void nombreLargoSinCiBloquea() {
        DatosPersona a = persona(1, "MARIA DE LOS ANGELES", "FERNANDEZ", "GUTIERREZ", null, false);
        DatosPersona b = persona(2, "MARIA", "FERNANDEZ", "GUTIERREZ", null, false);
        Resultado r = calcular(a, List.of(b), List.of(fila(1, 2, 1, "X")),
                new Decision(1L, List.of(2L), "MARIA DE LOS ANGELES", "FERNANDEZ", "GUTIERREZ", "", null, List.of()), List.of());
        assertTrue(r.errores().stream().anyMatch(e -> e.contains("no puede pasar de 35")), r.errores().toString());
        // Con C.I. solo avisa.
        Resultado c = calcular(a, List.of(b), List.of(fila(1, 2, 1, "X")),
                new Decision(1L, List.of(2L), "MARIA DE LOS ANGELES", "FERNANDEZ", "GUTIERREZ", "5000003", null, List.of()), List.of());
        assertTrue(c.errores().isEmpty(), c.errores().toString());
    }

    // ── Apoyo ─────────────────────────────────────────────────────────────

    private static Resultado calcular(DatosPersona p, List<DatosPersona> sec, List<Fila> filas, Decision d, List<Long> conCi) {
        return PlanPersona.calcular(p, sec, filas, d, Set.of(), conCi);
    }

    private static DatosPersona persona(long id, String n, String pa, String ma, String ci, boolean usuario) {
        return new DatosPersona(id, n, pa, ma, ci, "ACTIVO", usuario);
    }

    private static Fila fila(long idResp, long idPersona, int codOfic, String cargo) {
        return new Fila(idResp, idPersona, (long) codOfic, "CULP", (short) codOfic, "1", "OFICINA " + codOfic, cargo, 3,
                false, false, "VSIAF");
    }

    private static CambioFila cambio(Resultado r, long idResp) {
        return r.filas().stream().filter(c -> c.fila().idResponsable() == idResp).findFirst().orElseThrow();
    }
}
