package com.usic.SistemasActivosFijosUAP;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.usic.SistemasActivosFijosUAP.model.service.personas.PlanPersona.DatosPersona;
import com.usic.SistemasActivosFijosUAP.model.service.personas.PlanPersona.Fila;
import com.usic.SistemasActivosFijosUAP.model.service.personas.PlanReversion;
import com.usic.SistemasActivosFijosUAP.model.service.personas.PlanSeparacion;
import com.usic.SistemasActivosFijosUAP.model.service.personas.PlanSeparacion.Decision;
import com.usic.SistemasActivosFijosUAP.model.service.personas.PlanSeparacion.Nueva;
import com.usic.SistemasActivosFijosUAP.model.service.personas.PlanSeparacion.Resultado;

/** Fase 6: pasar oficinas a otra persona (sin base de datos). */
class PlanSeparacionTest {

    private static final DatosPersona SIN_DATOS = new DatosPersona(250L, "SIN DATOS", null, null, null, "ACTIVO", false);
    private static final DatosPersona ANA = new DatosPersona(10L, "ANA", "PEREZ", "LIMA", "5000001", "ACTIVO", false);

    private static List<Fila> filas(long idPersona) {
        return List.of(fila(1, idPersona, false), fila(2, idPersona, false), fila(3, idPersona, true));
    }

    private static Fila fila(long id, long idPersona, boolean custodia) {
        return new Fila(id, idPersona, 100L + id, "CULP", (short) (100 + id), "1", "OF " + id, "CARGO", 2, custodia, false, "VSIAF");
    }

    private static Resultado calcular(Decision d, DatosPersona origen, DatosPersona destino, List<Long> conCi, Set<Long> custodia) {
        return PlanSeparacion.calcular(d, origen, filas(origen.id()), destino, Set.of(), conCi, custodia);
    }

    @Test
    void sinDatosAUnaPersonaExistente() {
        Resultado r = calcular(new Decision(250L, List.of(1L, 2L), 10L, null), SIN_DATOS, ANA, List.of(), Set.of());
        assertTrue(r.errores().isEmpty(), r.errores().toString());
        assertEquals(2, r.filas().size());
        assertEquals("ANA PEREZ LIMA", r.filas().get(0).nombreDespues());
        assertEquals("5000001", r.filas().get(0).ciDespues());
    }

    @Test
    void aUnaPersonaNuevaConCi() {
        Resultado r = calcular(new Decision(250L, List.of(1L), null, new Nueva("luis", "arce", "cata", "6000001")),
                SIN_DATOS, null, List.of(), Set.of());
        assertTrue(r.errores().isEmpty(), r.errores().toString());
        assertTrue(r.destinoNuevo());
        assertEquals("LUIS ARCE CATA", r.destino().nombreCompleto());
    }

    @Test
    void nuevaConCiDeOtraPersonaBloquea() {
        Resultado r = calcular(new Decision(250L, List.of(1L), null, new Nueva("LUIS", "ARCE", "CATA", "5000001")),
                SIN_DATOS, null, List.of(10L), Set.of());
        assertTrue(r.errores().stream().anyMatch(e -> e.contains("ya es de otra persona")), r.errores().toString());
    }

    @Test
    void sinCiYMismoNombreQueElOrigenBloquea() {
        DatosPersona jorge = new DatosPersona(421L, "JORGE", "ARROYO", "Z", null, "ACTIVO", false);
        Resultado r = calcular(new Decision(421L, List.of(1L), null, new Nueva("JORGE", "ARROYO", "Z", "")),
                jorge, null, List.of(), Set.of());
        assertTrue(r.errores().stream().anyMatch(e -> e.contains("no las distinguiría")), r.errores().toString());
    }

    @Test
    void origenEspecialSalvoSinDatosBloquea() {
        DatosPersona dev = new DatosPersona(586L, "NANCY", "ACUNA", "DEVOLUCION", null, "ACTIVO", false);
        Resultado r = calcular(new Decision(586L, List.of(1L), 10L, null), dev, ANA, List.of(), Set.of());
        assertTrue(r.errores().stream().anyMatch(e -> e.contains("registro especial")), r.errores().toString());
    }

    @Test
    void oficinaDeCustodiaNoSePasaDesdeAqui() {
        Resultado r = calcular(new Decision(250L, List.of(3L), 10L, null), SIN_DATOS, ANA, List.of(), Set.of());
        assertTrue(r.errores().stream().anyMatch(e -> e.contains("custodia de faltantes")), r.errores().toString());
    }

    @Test
    void sinOficinasMarcadasOMismaPersonaBloquea() {
        assertTrue(calcular(new Decision(250L, List.of(), 10L, null), SIN_DATOS, ANA, List.of(), Set.of()).errores()
                .stream().anyMatch(e -> e.contains("Marque al menos")));
        assertTrue(calcular(new Decision(10L, List.of(1L), 10L, null), ANA, ANA, List.of(), Set.of()).errores()
                .stream().anyMatch(e -> e.contains("misma persona")));
    }

    @Test
    void personaNuevaConNombreEspecialOHomonimoSinCiBloquea() {
        assertTrue(calcular(new Decision(250L, List.of(1L), null, new Nueva("JUAN", "PEREZ", "DEVOLUCION", "6000002")),
                SIN_DATOS, null, List.of(), Set.of()).errores().stream().anyMatch(e -> e.contains("marca del VSIAF")));
        Resultado sinCi = PlanSeparacion.calcular(new Decision(250L, List.of(1L), null, new Nueva("JUAN", "PEREZ", "LIMA", "")),
                SIN_DATOS, filas(250L), null, Set.of(), List.of(), Set.of(), List.of(33L));
        assertTrue(sinCi.errores().stream().anyMatch(e -> e.contains("Ya existe(n) persona(s) con ese nombre")), sinCi.errores().toString());
        Resultado conCi = PlanSeparacion.calcular(new Decision(250L, List.of(1L), null, new Nueva("JUAN", "PEREZ", "LIMA", "6000003")),
                SIN_DATOS, filas(250L), null, Set.of(), List.of(), Set.of(), List.of(33L));
        assertTrue(conCi.errores().isEmpty(), conCi.errores().toString());
        assertTrue(conCi.avisos().stream().anyMatch(a -> a.contains("ese nombre")));
    }

    @Test
    void sinDatosVaEnBlancoAlVsiafYAvisaDelCi() {
        assertEquals("", com.usic.SistemasActivosFijosUAP.model.service.personas.PlanPersona.nomresp(SIN_DATOS));
        Resultado r = calcular(new Decision(250L, List.of(1L), 10L, null), SIN_DATOS, ANA, List.of(), Set.of());
        assertTrue(r.avisos().stream().anyMatch(a -> a.contains("C.I. en el VSIAF")), r.avisos().toString());
    }

    @Test
    void todasLasOficinasAvisa() {
        Resultado r = calcular(new Decision(250L, List.of(1L, 2L, 3L), 10L, null), SIN_DATOS, ANA, List.of(), Set.of());
        assertTrue(r.avisos().stream().anyMatch(a -> a.contains("TODAS")), r.avisos().toString());
    }

    // ── Revertir una separación: la persona creada se da de baja ──

    @Test
    void revertirSeparacionDaDeBajaALaPersonaNueva() {
        Map<String, Object> foto = mapa("idPersona", 700L, "nombre", "LUIS", "paterno", "ARCE", "materno", "CATA", "ci", "6000001");
        List<PlanReversion.Registro> lote = List.of(
                new PlanReversion.Registro("PERSONA_NUEVA", null, 700L, "Persona nueva", mapa(), foto, "PERSONAS_CARGOS", null, null),
                new PlanReversion.Registro("SEPARACION", 1L, 700L, "CULP 101 · cód. 1",
                        mapa("idPersona", 250L, "idCargo", 4L, "cargo", "X"), mapa("idPersona", 700L, "idCargo", 4L, "cargo", "X"),
                        "PERSONAS_CARGOS", null, null));
        Map<Long, PlanReversion.PersonaHoy> personas = new HashMap<>();
        personas.put(700L, new PlanReversion.PersonaHoy("LUIS", "ARCE", "CATA", "6000001", "ACTIVO", null));
        personas.put(250L, new PlanReversion.PersonaHoy("SIN DATOS", null, null, null, "ACTIVO", null));
        Map<Long, PlanReversion.FilaHoy> filas = Map.of(1L, new PlanReversion.FilaHoy(700L, 4L, "ACTIVO", "CULP 101", false, "VSIAF"));
        PlanReversion.Estado hoy = new PlanReversion.Estado(filas, personas, Map.of(), Map.of(), Map.of(), Map.of(),
                Map.of(700L, 0L), Map.of(), Map.of(), Set.of());
        PlanReversion.Resultado r = PlanReversion.calcular("L", lote, hoy);
        assertTrue(r.errores().isEmpty(), r.errores().toString());
        assertEquals(1, r.bajas().size());
        assertEquals(250L, r.filas().get(0).idPersona());

        // Si ya tiene usuario u otras oficinas, no se da de baja.
        PlanReversion.Estado conUsuario = new PlanReversion.Estado(filas, personas, Map.of(), Map.of(), Map.of(), Map.of(),
                Map.of(700L, 0L), Map.of(), Map.of(), Set.of(700L));
        assertTrue(PlanReversion.calcular("L", lote, conUsuario).errores().stream().anyMatch(e -> e.contains("usuario")));
        PlanReversion.Estado conOtras = new PlanReversion.Estado(filas, personas, Map.of(), Map.of(), Map.of(), Map.of(),
                Map.of(700L, 3L), Map.of(), Map.of(), Set.of());
        assertTrue(PlanReversion.calcular("L", lote, conOtras).errores().stream().anyMatch(e -> e.contains("oficina(s) después")));
    }

    private static Map<String, Object> mapa(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }
}
