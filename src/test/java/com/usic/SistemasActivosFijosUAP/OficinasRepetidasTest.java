package com.usic.SistemasActivosFijosUAP;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.usic.SistemasActivosFijosUAP.model.service.personas.OficinasRepetidas;
import com.usic.SistemasActivosFijosUAP.model.service.personas.PlanPersona.Fila;

/** Fase 9: una oficina, un responsable (sin base de datos). */
class OficinasRepetidasTest {

    private static Fila fila(long id, long idPersona, long idOficina, long bienes) {
        return new Fila(id, idPersona, idOficina, "CULP", (short) idOficina, String.valueOf(id), "OF " + idOficina,
                "CARGO", bienes, false, false, "VSIAF");
    }

    private record Salida(List<String> errores, List<String> avisos) {}

    private static Salida validar(List<Fila> filas, Set<Long> noSumar, Map<Long, Long> enCamino, Set<Long> baja) {
        List<String> e = new ArrayList<>(), a = new ArrayList<>();
        OficinasRepetidas.validar(filas, noSumar, enCamino, baja, e, a);
        return new Salida(e, a);
    }

    @Test
    void alUnificarDosRegistrosConBienesEnLaMismaOficinaBloquea() {
        List<Fila> filas = List.of(fila(1, 10, 184, 20), fila(2, 11, 184, 4));
        Salida s = validar(filas, Set.of(), Map.of(), Set.of());
        assertTrue(s.errores().stream().anyMatch(x -> x.contains("un solo responsable")), s.errores().toString());
    }

    @Test
    void siLosBienesDelQueSobraVanEnCaminoAFaltantesSePuede() {
        List<Fila> filas = List.of(fila(1, 10, 184, 20), fila(2, 11, 184, 4));
        Salida s = validar(filas, Set.of(), Map.of(2L, 4L), Set.of());
        assertTrue(s.errores().isEmpty(), s.errores().toString());
        assertTrue(s.avisos().stream().anyMatch(x -> x.contains("deshabilitar")));
    }

    @Test
    void mismaPersonaYaRepetidaSoloAvisa() {
        List<Fila> filas = List.of(fila(1, 10, 184, 20), fila(2, 10, 184, 4));
        Salida s = validar(filas, Set.of(), Map.of(), Set.of());
        assertTrue(s.errores().isEmpty(), s.errores().toString());
        assertTrue(s.avisos().stream().anyMatch(x -> x.contains("ya tiene 2 registros")));
    }

    @Test
    void deshabilitarSoloSinBienesYDejandoUno() {
        List<Fila> filas = List.of(fila(1, 10, 184, 20), fila(2, 11, 184, 0), fila(3, 10, 200, 0), fila(4, 11, 200, 0));
        assertTrue(validar(filas, Set.of(), Map.of(), Set.of(2L)).errores().isEmpty());
        assertTrue(validar(filas, Set.of(), Map.of(), Set.of(1L)).errores().stream().anyMatch(x -> x.contains("no se puede deshabilitar")));
        assertTrue(validar(filas, Set.of(), Map.of(), Set.of(3L, 4L)).errores().stream().anyMatch(x -> x.contains("al menos un registro")));
    }

    @Test
    void dosRegistrosEnLaOficinaDeFaltantesPidenJuntar() {
        Fila a = new Fila(1L, 10L, 372L, "CULP", (short) 372, "5", "FALTANTES CULP", "CARGO", 27, true, false, "VSIAF");
        Fila b = new Fila(2L, 11L, 372L, "CULP", (short) 372, "6", "FALTANTES CULP", "CARGO", 1, true, false, "VSIAF");
        Salida s = validar(List.of(a, b), Set.of(), Map.of(), Set.of());
        assertTrue(s.errores().stream().anyMatch(x -> x.contains("Juntar")), s.errores().toString());
    }

    @Test
    void laFilaMarcadaComoDeOtraPersonaNoCuenta() {
        List<Fila> filas = List.of(fila(1, 10, 184, 20), fila(2, 11, 184, 4));
        Salida s = validar(filas, Set.of(2L), Map.of(), Set.of());
        assertTrue(s.errores().isEmpty(), s.errores().toString());
        assertEquals(0, OficinasRepetidas.grupos(filas, Set.of(2L)).size());
    }
}
