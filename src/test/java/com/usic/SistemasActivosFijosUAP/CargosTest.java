package com.usic.SistemasActivosFijosUAP;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.usic.SistemasActivosFijosUAP.model.service.personas.AgrupadorCargos;
import com.usic.SistemasActivosFijosUAP.model.service.personas.AgrupadorCargos.CargoUso;
import com.usic.SistemasActivosFijosUAP.model.service.personas.AgrupadorCargos.Grupo;
import com.usic.SistemasActivosFijosUAP.model.service.personas.PlanCargos;

/** Pestaña «Cargos» de Personas y cargos: agrupador y cálculo (sin base de datos). */
class CargosTest {

    // ── Agrupador ─────────────────────────────────────────────────────────

    @Test
    void puntosYEspaciosSonSeguro() {
        List<Grupo> g = agrupar(c(1, "DIRECTORA ACBN", 17), c(2, "DIRECTORA A.C.B.N.", 15), c(3, "JEFE MEDICO - SISU", 9),
                c(4, "JEFE MEDICO SISU", 27));
        assertEquals(2, g.size());
        assertTrue(g.stream().allMatch(x -> AgrupadorCargos.SEGURO.equals(x.nivel())));
    }

    @Test
    void abreviaturaYTipeoSonProbable() {
        Grupo g = unico(agrupar(c(1, "RESP. CARRERA DE ARQUITECTURA", 39), c(2, "RESP. CARRE. DE ARQUITECTURA", 166)));
        assertEquals(AgrupadorCargos.PROBABLE, g.nivel());
        assertEquals(2L, g.cargos().get(0).id());        // el más usado primero
        assertEquals(AgrupadorCargos.PROBABLE, unico(agrupar(c(1, "AUXILIAR ADMINISTRATIVO", 17),
                c(2, "AUXILIAR ADMINITRATIVO", 1))).nivel());
    }

    @Test
    void generoNoSeJuntaSolo() {
        List<Grupo> g = agrupar(c(1, "DIRECTOR CARRERA DE MEDICINA", 30), c(2, "DIRECTORA CARRERA DE MEDICINA", 5));
        assertTrue(g.isEmpty(), g.toString());
        assertTrue(agrupar(c(1, "TECNICO ADMINISTRATIVO", 14), c(2, "TECNICOS ADMINISTRATIVOS", 1)).isEmpty());
    }

    @Test
    void generoQueEntraPorCadenaVieneSinMarcar() {
        // DIR. es abreviatura de los dos: quedan en el grupo, pero DIRECTOR no se marca.
        Grupo g = unico(agrupar(c(1, "DIRECTORA CARRERA DE ODONTOLOGIA", 52), c(2, "DIRECTOR CARRERA DE ODONTOLOGIA", 1),
                c(3, "DIR. CARRERA DE ODONTOLOGIA", 1)));
        assertEquals(List.of(2L), g.sinMarcar());
        // Como en la base: con el tipeo estricto, DIRECTORA y DIRECTOR quedan en grupos aparte,
        // y el tipeo DIRETORA va con las DIRECTORA, marcado.
        List<Grupo> s = agrupar(c(1, "DIRECTORA ACBN", 17), c(2, "DIRECTOR A.C.B.N.", 4), c(3, "DIRETORA ACBN", 1),
                c(4, "DIRECTORA A.C.B.N.", 15), c(5, "DIRECTOR ACBN", 3));
        assertEquals(2, s.size(), s.toString());
        Grupo fem = s.stream().filter(x -> x.cargos().get(0).id() == 1L).findFirst().orElseThrow();
        assertEquals(Set.of(1L, 3L, 4L), Set.copyOf(fem.cargos().stream().map(CargoUso::id).toList()));
        assertTrue(fem.sinMarcar().isEmpty());
    }

    @Test
    void pluralYGeneroEnEVienenSeparados() {
        assertTrue(agrupar(c(1, "AUXILIAR DE ENFERMERIA", 5), c(2, "AUXILIARES DE ENFERMERIA", 1)).isEmpty());
        assertTrue(agrupar(c(1, "CHOFER", 5), c(2, "CHOFERES", 1)).isEmpty());
        assertTrue(agrupar(c(1, "PRESIDENTE COMITE", 5), c(2, "PRESIDENTA COMITE", 1)).isEmpty());
        assertTrue(agrupar(c(1, "SUBJEFE DE AREA", 5), c(2, "SUBJEFA DE AREA", 1)).isEmpty());
    }

    @Test
    void palabrasDistintasNoSonTipeo() {
        assertTrue(agrupar(c(1, "DOCENTE BIOLOGIA", 5), c(2, "DOCENTE ECOLOGIA", 1)).isEmpty());
        assertTrue(agrupar(c(1, "DOCENTE ECONOMIA", 5), c(2, "DOCENTE ECOLOGIA", 1)).isEmpty());
        assertTrue(agrupar(c(1, "PERSONAL DE APOYO", 5), c(2, "PERSONAS DE APOYO", 1)).isEmpty());
        assertTrue(agrupar(c(1, "RESP. VENTA CENTRAL", 5), c(2, "RESP. VENTANILLA CENTRAL", 1)).isEmpty());
        // Corte por el largo del campo (última palabra, sin punto): sí.
        assertEquals(1, agrupar(c(1, "DIRECTOR CARRERA TURISMO SOSTENIBLE", 5), c(2, "DIRECTOR CARRERA TURISMO SOSTEN", 1)).size());
    }

    @Test
    void primeroNeutroDejaSinMarcarALosQueChocan() {
        Grupo g = unico(agrupar(c(1, "DIR. CARRERA DE BIOQUIMICA", 9), c(2, "DIRECTOR CARRERA DE BIOQUIMICA", 2),
                c(3, "DIRECTORA CARRERA DE BIOQUIMICA", 1)));
        assertTrue(g.sinMarcar().containsAll(List.of(2L, 3L)), g.toString());
        assertTrue(g.conflictos().get(2L).contains(3L));
    }

    @Test
    void siglaCortaSinPuntoNoEsAbreviatura() {
        assertTrue(agrupar(c(1, "DIRECTOR ACS", 6), c(2, "DIRECTOR ACSYH", 4)).isEmpty());
        // Con punto sí: ADM. CAMPUS ~ ADMINISTRADOR CAMPUS.
        assertEquals(AgrupadorCargos.PROBABLE, unico(agrupar(c(1, "ADMINISTRADOR CAMPUS SAN PEDRO", 53),
                c(2, "ADM. CAMPUS SAN PEDRO", 1))).nivel());
    }

    @Test
    void palabrasDeMasEsRevisarYNadaMarcado() {
        Grupo g = unico(agrupar(c(1, "ADMINISTRATIVO", 217), c(2, "ADMINISTRATIVO ACYT", 10)));
        assertEquals(AgrupadorCargos.REVISAR, g.nivel());
        assertEquals(List.of(2L), g.sinMarcar());
    }

    @Test
    void descartadosNoVuelven() {
        List<Grupo> g = AgrupadorCargos.agrupar(List.of(c(1, "JEFE MEDICO SISU", 27), c(2, "JEFE MEDICO - SISU", 9)),
                Set.of(AgrupadorCargos.par(2L, 1L)));
        assertTrue(g.isEmpty());
    }

    // ── Cálculo ───────────────────────────────────────────────────────────

    @Test
    void juntarCambiaSoloLasFilasDeLosJuntados() {
        Map<Long, PlanCargos.Cargo> cargos = cargos(new PlanCargos.Cargo(1L, "RESP. CARRERA DE ARQUITECTURA", "ACTIVO"),
                new PlanCargos.Cargo(2L, "RESP. CARRE. DE ARQUITECTURA", "ACTIVO"));
        List<PlanCargos.Fila> filas = List.of(fila(10, 1), fila(11, 2), fila(12, 2));
        PlanCargos.Resultado r = PlanCargos.calcular(new PlanCargos.Decision(1L, null, List.of(2L)), cargos, filas, List.of());
        assertTrue(r.errores().isEmpty(), r.errores().toString());
        assertEquals(2, r.filas().size());              // la 10 ya tiene el elegido y no se renombra
        assertTrue(r.filas().stream().allMatch(f -> "RESP. CARRERA DE ARQUITECTURA".equals(f.cargoDespues())));
        assertFalse(r.renombra());
    }

    @Test
    void renombrarElElegidoTocaTambienSusFilas() {
        Map<Long, PlanCargos.Cargo> cargos = cargos(new PlanCargos.Cargo(1L, "RESP. GUARDERIA UNIV.", "ACTIVO"));
        PlanCargos.Resultado r = PlanCargos.calcular(new PlanCargos.Decision(1L, "resp. guarderia universitaria", List.of()),
                cargos, List.of(fila(10, 1)), List.of());
        assertTrue(r.renombra());
        assertEquals("RESP. GUARDERIA UNIVERSITARIA", r.texto());
        assertEquals(1, r.filas().size());
    }

    @Test
    void bloqueos() {
        Map<Long, PlanCargos.Cargo> cargos = cargos(new PlanCargos.Cargo(1L, "A", "ACTIVO"),
                new PlanCargos.Cargo(2L, "B", "FUSIONADO"));
        PlanCargos.Resultado fus = PlanCargos.calcular(new PlanCargos.Decision(1L, null, List.of(2L)), cargos, List.of(), List.of());
        assertTrue(fus.errores().stream().anyMatch(e -> e.contains("ya se juntó")));
        PlanCargos.Resultado largo = PlanCargos.calcular(new PlanCargos.Decision(1L, "X".repeat(41), List.of()), cargos, List.of(), List.of());
        assertTrue(largo.errores().stream().anyMatch(e -> e.contains("40 caracteres")));
        PlanCargos.Resultado dup = PlanCargos.calcular(new PlanCargos.Decision(1L, "C", List.of()), cargos, List.of(), List.of(99L));
        assertTrue(dup.errores().stream().anyMatch(e -> e.contains("Ya existe otro cargo")));
        PlanCargos.Resultado nada = PlanCargos.calcular(new PlanCargos.Decision(1L, null, List.of()), cargos, List.of(), List.of());
        assertTrue(nada.errores().stream().anyMatch(e -> e.contains("nada que cambiar")));
    }

    @Test
    void laFirmaCambiaSiCambiaUnaFila() {
        Map<Long, PlanCargos.Cargo> cargos = cargos(new PlanCargos.Cargo(1L, "A", "ACTIVO"), new PlanCargos.Cargo(2L, "AA", "ACTIVO"));
        PlanCargos.Decision d = new PlanCargos.Decision(1L, null, List.of(2L));
        String f1 = PlanCargos.calcular(d, cargos, List.of(fila(10, 2)), List.of()).firma();
        String f2 = PlanCargos.calcular(d, cargos, List.of(fila(10, 2), fila(11, 2)), List.of()).firma();
        assertTrue(!f1.equals(f2));
    }

    // ── Apoyo ─────────────────────────────────────────────────────────────

    private static CargoUso c(long id, String nombre, long filas) {
        return new CargoUso(id, nombre, filas, filas);
    }

    private static List<Grupo> agrupar(CargoUso... cs) {
        return AgrupadorCargos.agrupar(List.of(cs), Set.of());
    }

    private static Grupo unico(List<Grupo> g) {
        assertEquals(1, g.size(), g.toString());
        return g.get(0);
    }

    private static Map<Long, PlanCargos.Cargo> cargos(PlanCargos.Cargo... cs) {
        Map<Long, PlanCargos.Cargo> m = new HashMap<>();
        for (PlanCargos.Cargo c : cs) m.put(c.id(), c);
        return m;
    }

    private static PlanCargos.Fila fila(long idResp, long idCargo) {
        return new PlanCargos.Fila(idResp, 1L, "PERSONA", idCargo, "CULP " + idResp + " · cód. 1", false, "VSIAF");
    }
}
