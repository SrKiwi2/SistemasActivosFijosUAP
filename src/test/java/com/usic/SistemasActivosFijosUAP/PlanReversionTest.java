package com.usic.SistemasActivosFijosUAP;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.usic.SistemasActivosFijosUAP.model.service.personas.PlanReversion;
import com.usic.SistemasActivosFijosUAP.model.service.personas.PlanReversion.CargoHoy;
import com.usic.SistemasActivosFijosUAP.model.service.personas.PlanReversion.Estado;
import com.usic.SistemasActivosFijosUAP.model.service.personas.PlanReversion.FilaHoy;
import com.usic.SistemasActivosFijosUAP.model.service.personas.PlanReversion.PersonaHoy;
import com.usic.SistemasActivosFijosUAP.model.service.personas.PlanReversion.Registro;
import com.usic.SistemasActivosFijosUAP.model.service.personas.PlanReversion.Resultado;

/** Reversión de un lote de Personas y cargos (fase 5): solo si nada cambió después. */
class PlanReversionTest {

    // ── Lote de cargos: «RESP. CARRE. DE ARQ.» (2) juntado en «RESP. CARRERA DE ARQ.» (1), renombrado ──

    private static List<Registro> loteCargos() {
        Map<String, Object> antesF = mapa("idElegido", 1L, "nombreElegido", "RESP. CARRERA ARQ",
                "juntados", List.of(mapa("id", 2L, "nombre", "RESP. CARRE. ARQ")));
        Map<String, Object> despuesF = mapa("idElegido", 1L, "nombreElegido", "RESP. CARRERA DE ARQUITECTURA", "idAlias", 3L);
        return List.of(
                new Registro("CARGO_FUSION", null, 0L, "Cargo", antesF, despuesF, "CARGOS", null, null),
                new Registro("CARGO", 10L, 7L, "CULP 184 · cód. 3", mapa("cargo", "RESP. CARRE. ARQ", "idCargo", 2L),
                        mapa("cargo", "RESP. CARRERA DE ARQUITECTURA", "idCargo", 1L), "CARGOS", null, null),
                new Registro("CARGO", 11L, 7L, "CULP 100 · cód. 3", mapa("cargo", "RESP. CARRERA ARQ", "idCargo", 1L),
                        mapa("cargo", "RESP. CARRERA DE ARQUITECTURA", "idCargo", 1L), "CARGOS", null, null));
    }

    private static Estado hoyCargos(long cargoFila10) {
        Map<Long, FilaHoy> filas = new HashMap<>();
        filas.put(10L, new FilaHoy(7L, cargoFila10, "ACTIVO", "CULP 184", false, "VSIAF"));
        filas.put(11L, new FilaHoy(7L, 1L, "ACTIVO", "CULP 100", false, "VSIAF"));
        Map<Long, CargoHoy> cargos = new HashMap<>();
        cargos.put(1L, new CargoHoy("RESP. CARRERA DE ARQUITECTURA", "ACTIVO", null));
        cargos.put(2L, new CargoHoy("RESP. CARRE. ARQ", "FUSIONADO", 1L));
        cargos.put(3L, new CargoHoy("RESP. CARRERA ARQ", "FUSIONADO", 1L));
        return new Estado(filas, Map.of(), cargos, Map.of(), Map.of(), new HashMap<>());
    }

    @Test
    void cargosSinCambiosDespuesSeRevierten() {
        Resultado r = PlanReversion.calcular("L1", loteCargos(), hoyCargos(1L));
        assertTrue(r.errores().isEmpty(), r.errores().toString());
        assertEquals(2, r.filas().size());
        assertEquals(2L, r.filas().stream().filter(f -> f.idResponsable() == 10L).findFirst().orElseThrow().idCargo());
        assertEquals(1, r.cargos().size());
        assertEquals(List.of(2L), r.cargos().get(0).reactivar());
        assertEquals("RESP. CARRERA ARQ", r.cargos().get(0).nombreAntes());
        assertEquals(3L, r.cargos().get(0).idAlias());
    }

    @Test
    void siUnaOficinaCambioDespuesNoSeRevierte() {
        Resultado r = PlanReversion.calcular("L1", loteCargos(), hoyCargos(99L));
        assertTrue(r.errores().stream().anyMatch(e -> e.contains("CULP 184") && e.contains("cambió de cargo")), r.errores().toString());
    }

    @Test
    void nombreViejoOcupadoPorOtroCargoBloquea() {
        Estado hoy = hoyCargos(1L);
        hoy.cargosVigentesPorNombre().put("RESP. CARRERA ARQ", List.of(55L));
        Resultado r = PlanReversion.calcular("L1", loteCargos(), hoy);
        assertTrue(r.errores().stream().anyMatch(e -> e.contains("no puede volver a ese nombre")), r.errores().toString());
    }

    @Test
    void laFirmaCambiaSiCambiaElEstado() {
        assertNotEquals(PlanReversion.calcular("L1", loteCargos(), hoyCargos(1L)).firma(),
                PlanReversion.calcular("L1", loteCargos(), hoyCargos(2L)).firma());
    }

    // ── Lote de personas: Richard sin C.I. (189) unificado en Richard con C.I. (275) ──

    private static List<Registro> lotePersonas() {
        Map<String, Object> fotoSec = mapa("idPersona", 189L, "nombre", "RICHARD", "paterno", "ROJAS", "materno", "LOPEZ", "ci", null);
        Map<String, Object> fus = mapa("estado", "FUSIONADA", "fusionadaEn", 275L, "actas", List.of(501L), "usuarios", List.of());
        List<Registro> l = new ArrayList<>();
        l.add(new Registro("FUSION_PERSONA", null, 275L, "RICHARD ROJAS LOPEZ (id 189) → id 275", fotoSec, fus,
                "PERSONAS_CARGOS", null, null));
        l.add(new Registro("FUSION_FILA+NOMBRE_CI", 20L, 275L, "CULP 5 · cód. 1",
                mapa("idPersona", 189L, "nomresp", "RICHARD ROJAS LOPEZ", "ci", null, "cargo", "X", "idCargo", 4L),
                mapa("idPersona", 275L, "nomresp", "RICHARD ROJAS LOPEZ", "ci", "1769812", "cargo", "X", "idCargo", 4L),
                "PERSONAS_CARGOS", null, null));
        return l;
    }

    private static Estado hoyPersonas(Long actaEn) {
        Map<Long, FilaHoy> filas = Map.of(20L, new FilaHoy(275L, 4L, "ACTIVO", "CULP 5", false, "VSIAF"));
        Map<Long, PersonaHoy> personas = new HashMap<>();
        personas.put(189L, new PersonaHoy("RICHARD", "ROJAS", "LOPEZ", null, "FUSIONADA", 275L));
        personas.put(275L, new PersonaHoy("RICHARD", "ROJAS", "LOPEZ", "1769812", "ACTIVO", null));
        Map<Long, Long> actas = new HashMap<>();
        actas.put(501L, actaEn);
        return new Estado(filas, personas, Map.of(), actas, Map.of(), Map.of());
    }

    @Test
    void unificacionSeDeshaceConSusActas() {
        Resultado r = PlanReversion.calcular("L2", lotePersonas(), hoyPersonas(275L));
        assertTrue(r.errores().isEmpty(), r.errores().toString());
        assertEquals(1, r.personas().size());
        assertEquals(189L, r.personas().get(0).idPersona());
        assertEquals(List.of(501L), r.personas().get(0).actas());
        assertEquals(189L, r.filas().get(0).idPersona());
        assertEquals(4L, r.filas().get(0).idCargo());
    }

    @Test
    void siElActaYaNoEstaEnLaPrincipalNoSeRevierte() {
        Resultado r = PlanReversion.calcular("L2", lotePersonas(), hoyPersonas(999L));
        assertTrue(r.errores().stream().anyMatch(e -> e.contains("acta 501")), r.errores().toString());
    }

    @Test
    void noVuelveAUnaPersonaQueYaNoEstaVigente() {
        // Unificación parcial (sin FUSION_PERSONA): la fila volvería a 189, que hoy está FUSIONADA en otra.
        List<Registro> parcial = List.of(lotePersonas().get(1));
        Estado hoy = hoyPersonas(275L);
        hoy.personas().put(189L, new PersonaHoy("RICHARD", "ROJAS", "LOPEZ", null, "FUSIONADA", 300L));
        Resultado r = PlanReversion.calcular("L2", parcial, hoy);
        assertTrue(r.errores().stream().anyMatch(e -> e.contains("ya no está vigente")), r.errores().toString());
    }

    @Test
    void noVuelveAUnCargoQueSeJuntoDespues() {
        List<Registro> l = List.of(new Registro("CARGO", 10L, 7L, "CULP 184", mapa("cargo", "A", "idCargo", 2L),
                mapa("cargo", "B", "idCargo", 1L), "PERSONAS_CARGOS", null, null));
        Estado hoy = hoyCargos(1L);   // el 2 está FUSIONADO y este lote no lo reactiva
        Resultado r = PlanReversion.calcular("L5", l, hoy);
        assertTrue(r.errores().stream().anyMatch(e -> e.contains("cargo al que volvería")), r.errores().toString());
    }

    private static List<Registro> loteDatos() {
        return List.of(new Registro("NOMBRE_CI", null, 275L, "RICHARD",
                mapa("idPersona", 275L, "nombre", "RICHARD", "paterno", "ROJAS", "materno", "LOPEZ", "ci", "1700000"),
                mapa("idPersona", 275L, "nombre", "RICHARD", "paterno", "ROJAS", "materno", "LOPEZ", "ci", "1769812"),
                "PERSONAS_CARGOS", null, null));
    }

    private static Estado hoyDatos(long fuera, List<Long> conCiViejo) {
        Map<Long, PersonaHoy> personas = new HashMap<>();
        personas.put(275L, new PersonaHoy("RICHARD", "ROJAS", "LOPEZ", "1769812", "ACTIVO", null));
        return new Estado(Map.of(), personas, Map.of(), Map.of(), Map.of(), Map.of(),
                Map.of(275L, fuera), Map.of(), Map.of("1700000", conCiViejo));
    }

    @Test
    void nombreCiSeRevierteSiNoHayNadaDespues() {
        Resultado r = PlanReversion.calcular("L6", loteDatos(), hoyDatos(0, List.of()));
        assertTrue(r.errores().isEmpty(), r.errores().toString());
        assertEquals("1700000", r.datos().get(0).ci());
    }

    @Test
    void nombreCiNoSeRevierteConOficinasNuevasNiConCiAjeno() {
        assertTrue(PlanReversion.calcular("L6", loteDatos(), hoyDatos(2, List.of())).errores().stream()
                .anyMatch(e -> e.contains("recibió después")));
        assertTrue(PlanReversion.calcular("L6", loteDatos(), hoyDatos(0, List.of(900L))).errores().stream()
                .anyMatch(e -> e.contains("ya es de otra persona")));
    }

    @Test
    void cargoRenombradoConOficinasNuevasNoSeRevierte() {
        Estado base = hoyCargos(1L);
        Estado hoy = new Estado(base.filas(), base.personas(), base.cargos(), base.actas(), base.usuarios(),
                base.cargosVigentesPorNombre(), Map.of(), Map.of(1L, 4L), Map.of());
        assertTrue(PlanReversion.calcular("L1", loteCargos(), hoy).errores().stream().anyMatch(e -> e.contains("recibió después")));
    }

    @Test
    void juntadoConElMismoNombreQueElElegidoSiSeRevierte() {
        // «DOCENTE» (1) y «Docente » (2): mismo nombre normalizado; el elegido no cuenta como «otro».
        Map<String, Object> antesF = mapa("idElegido", 1L, "nombreElegido", "DOCENTE",
                "juntados", List.of(mapa("id", 2L, "nombre", "Docente ")));
        Map<String, Object> despuesF = mapa("idElegido", 1L, "nombreElegido", "DOCENTE", "idAlias", null);
        List<Registro> l = List.of(new Registro("CARGO_FUSION", null, 0L, "Cargo", antesF, despuesF, "CARGOS", null, null),
                new Registro("CARGO", 10L, 7L, "CULP 1", mapa("cargo", "Docente ", "idCargo", 2L),
                        mapa("cargo", "DOCENTE", "idCargo", 1L), "CARGOS", null, null));
        Map<Long, CargoHoy> cargos = new HashMap<>();
        cargos.put(1L, new CargoHoy("DOCENTE", "ACTIVO", null));
        cargos.put(2L, new CargoHoy("Docente ", "FUSIONADO", 1L));
        Map<String, List<Long>> vig = new HashMap<>();
        vig.put("DOCENTE", List.of(1L));
        Estado hoy = new Estado(Map.of(10L, new FilaHoy(7L, 1L, "ACTIVO", "CULP 1", false, "VSIAF")), Map.of(), cargos,
                Map.of(), Map.of(), vig);
        Resultado r = PlanReversion.calcular("L7", l, hoy);
        assertTrue(r.errores().isEmpty(), r.errores().toString());
    }

    // ── Reglas generales ──

    @Test
    void yaRevertidoReversionYOtroOrigenNoSeRevierten() {
        List<Registro> rev = loteCargos().stream().map(x -> new Registro(x.tipo(), x.idResponsable(), x.idPersona(),
                x.referencia(), x.antes(), x.despues(), x.origen(), null, "L9")).toList();
        assertTrue(PlanReversion.calcular("L1", rev, hoyCargos(1L)).errores().stream().anyMatch(e -> e.contains("ya se revirtió")));

        List<Registro> esRev = loteCargos().stream().map(x -> new Registro(x.tipo(), x.idResponsable(), x.idPersona(),
                x.referencia(), x.antes(), x.despues(), "HISTORIAL", "L0", null)).toList();
        Resultado r2 = PlanReversion.calcular("L1", esRev, hoyCargos(1L));
        assertTrue(r2.errores().stream().anyMatch(e -> e.contains("no se revierte una reversión")), r2.errores().toString());

        List<Registro> otro = List.of(new Registro("NOMBRE_CI", null, 5L, "X", mapa("idPersona", 5L), mapa("idPersona", 5L),
                "PERSONA", null, null));
        assertTrue(PlanReversion.calcular("L3", otro, hoyCargos(1L)).errores().stream().anyMatch(e -> e.contains("Solo se revierten")));
    }

    @Test
    void loteSinCargoDeAntesNoSeRevierte() {
        List<Registro> viejo = List.of(new Registro("CARGO", 10L, 7L, "CULP 184", mapa("cargo", "A"), mapa("cargo", "B"),
                "CARGOS", null, null));
        assertTrue(PlanReversion.calcular("L4", viejo, hoyCargos(1L)).errores().stream()
                .anyMatch(e -> e.contains("no guardó el cargo de antes")));
    }

    @Test
    void loteInexistente() {
        Resultado r = PlanReversion.calcular("NO", List.of(), hoyCargos(1L));
        assertTrue(r.errores().stream().anyMatch(e -> e.contains("no existe")));
        assertNull(r.origen());
    }

    private static Map<String, Object> mapa(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }
}
