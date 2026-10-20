package com.usic.SistemasActivosFijosUAP;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import com.usic.SistemasActivosFijosUAP.interoperabilidad.TextoVsiaf;
import com.usic.SistemasActivosFijosUAP.model.entity.Persona;
import com.usic.SistemasActivosFijosUAP.model.service.personas.IdentidadPersona;
import com.usic.SistemasActivosFijosUAP.model.service.personas.ResolutorPersonaSync;

/** Reglas de la sync de RESP.DBF para personas (docs/PLAN_PERSONAS_UNIFICADAS.md, sección 6). */
class ResolutorPersonaSyncTest {

    // ── IdentidadPersona ──────────────────────────────────────────────────

    @Test
    void numeroCiQuitaExtensionYNoDescartaNadaPorDefecto() {
        var inv = IdentidadPersona.CI_INVALIDOS_POR_DEFECTO;
        assertTrue(inv.isEmpty());
        assertEquals("1234568", IdentidadPersona.numeroCi("1234568 LP", inv));
        assertEquals("4206518", IdentidadPersona.numeroCi("4206518.", inv));
        assertEquals("1234568", IdentidadPersona.numeroCi("1.234.568-1A", inv));
        // Confirmado por Kevin (15-oct): no se tratan como falsos.
        assertEquals("000000", IdentidadPersona.numeroCi("000000", inv));
        assertEquals("1234567", IdentidadPersona.numeroCi("1234567", inv));
        assertEquals("12349000", IdentidadPersona.numeroCi("12349000", inv));
        assertNull(IdentidadPersona.numeroCi("1234", inv));
        assertNull(IdentidadPersona.numeroCi("", inv));
        // Solo con el parámetro (y confirmación) un C.I. deja de identificar.
        assertNull(IdentidadPersona.numeroCi("2222222", IdentidadPersona.ciInvalidos("2.222.222")));
    }

    @Test
    void nombreNormalizadoConservaLaEnie() {
        assertEquals("NANCY ACUÑA ALVAREZ", IdentidadPersona.normalizarNombre(" nancy acuña álvarez "));
        assertEquals("ALVARO M CACERES", IdentidadPersona.normalizarNombre("ALVARO. M. CACERES"));
        assertTrue(IdentidadPersona.nombresEquivalentes("NANCY ACUA", "NANCY ACUÑA"));
        assertTrue(IdentidadPersona.nombresEquivalentes("NANCY ACUNA", "NANCY ACUÑA"));
        assertFalse(IdentidadPersona.nombresEquivalentes("NANCY ACUA", "NANCY ACUNA"));
        assertEquals("PEQUEÑA ñ", TextoVsiaf.repararEnieDos("PEQUE¥A ¤"));
    }

    @Test
    void nombresEspeciales() {
        assertTrue(IdentidadPersona.esNombreEspecial("NANCY ACUNA ALVARES DEVOLUCION"));
        assertTrue(IdentidadPersona.esNombreEspecial("JUAN CARLOS SURCO FALTANTES"));
        assertTrue(IdentidadPersona.esNombreEspecial("ACEFALO"));
        assertTrue(IdentidadPersona.esNombreEspecial("SIN DATOS"));
        assertFalse(IdentidadPersona.esNombreEspecial("HERMAN O ALFARO TIHUAYO"));
    }

    // ── ResolutorPersonaSync ──────────────────────────────────────────────

    @Test
    void noMueveUnaFilaYaEnlazada() {
        Persona richardSinCi = persona(189, "RICHARD", "ROJAS", "LOPEZ", null);
        Persona richardConCi = persona(275, "RICHARD", "ROJAS", "LOPEZ", "1769812");
        Escenario e = new Escenario(richardSinCi, richardConCi);

        // Hoy la fila sin C.I. está en 189: se queda ahí (unificarlas es tarea del módulo).
        assertEquals(189L, e.r.resolver("RICHARD ROJAS LOPEZ", "", 189L).getIdPersona());
        assertEquals(275L, e.r.resolver("RICHARD ROJAS LOPEZ", "1769812", 275L).getIdPersona());
        assertEquals(0, e.guardadas.size());
    }

    @Test
    void filaNuevaSinCiVaComoAntesALaPersonaSinCiYLoSugiere() {
        Escenario e = new Escenario(persona(189, "RICHARD", "ROJAS", "LOPEZ", null),
                persona(275, "RICHARD", "ROJAS", "LOPEZ", "1769812"));
        assertEquals(189L, e.r.resolver("RICHARD ROJAS LOPEZ", null, null).getIdPersona());
        assertEquals(1, e.contador("sugerenciasMismoNombre"));
    }

    @Test
    void filaNuevaSinCiSoloConPersonaConCiVaAElla() {
        Escenario e = new Escenario(persona(275, "RICHARD", "ROJAS", "LOPEZ", "1769812"));
        assertEquals(275L, e.r.resolver("RICHARD ROJAS LOPEZ", null, null).getIdPersona());
    }

    @Test
    void filaConCiNuevoNoCompletaElCiSoloLoSugiere() {
        Persona ana = persona(10, "ANA", "PEREZ", "LIMA", null);
        Escenario e = new Escenario(ana);
        // Fila ya enlazada que ahora trae C.I.: se queda, sin tocar la persona.
        assertEquals(10L, e.r.resolver("ANA PEREZ LIMA", "4444445", 10L).getIdPersona());
        assertNull(ana.getCi());
        // Fila nueva con ese C.I.: se crea otra persona, como antes.
        Persona p = e.r.resolver("ANA PEREZ LIMA", "4444445", null);
        assertNotEquals(10L, p.getIdPersona());
        assertNull(ana.getCi());
        assertEquals(2, e.contador("sugerenciasCompletarCi"));
    }

    @Test
    void ciDistintoNoSeJuntaSoloCreaOtra() {
        Escenario e = new Escenario(persona(43, "ALVARO MARCELO", "CACERES", "BEJARANO", "7479551"));
        Persona p = e.r.resolver("ALVARO MARCELO CACERES BEJARANO", "7479561", null);
        assertNotEquals(43L, p.getIdPersona());
        assertEquals("7479561", p.getCi());
    }

    @Test
    void sinCiConDosPersonasConCiDistintoNoAdivina() {
        Escenario e = new Escenario(persona(43, "ALVARO MARCELO", "CACERES", "BEJARANO", "7479551"),
                persona(126, "ALVARO MARCELO", "CACERES", "BEJARANO", "7479561"),
                persona(190, "ALVARO MARCELO", "CACERES", "BEJARANO", null));
        assertEquals(190L, e.r.resolver("ALVARO MARCELO CACERES BEJARANO", "", null).getIdPersona());
    }

    @Test
    void ciSospechosoSigueIdentificando() {
        Escenario e = new Escenario(persona(29, "CARLOS", "MARADEY", "VIERA", "1234567"),
                persona(30, "CARLOS", "MARADEY", "VIERA", "1750131"));
        assertEquals(29L, e.r.resolver("CARLOS MARADEY VIERA", "1234567", null).getIdPersona());
    }

    @Test
    void enieNoSeCorrigeSolaSoloSeSugiere() {
        Persona nancy = persona(143, "NANCY", "ACUA", "ALVARES", null);
        Escenario e = new Escenario(nancy);
        Persona p = e.r.resolver("NANCY ACUÑA ALVARES", "", 143L);
        assertEquals(143L, p.getIdPersona());
        assertEquals("ACUA", p.getPaterno());
        assertEquals(0, e.guardadas.size());
        assertEquals(1, e.contador("sugerenciasEnie"));
        // Fila nueva con la Ñ: reconoce a la misma persona (no crea otra).
        assertEquals(143L, e.r.resolver("NANCY ACUÑA ALVARES", "", null).getIdPersona());
    }

    @Test
    void personaFusionadaRedirigeYNoRecibeFilas() {
        Persona vieja = persona(688, "A MARCELO", "CACERES", "BEJARANO", null);
        vieja.setEstado(ResolutorPersonaSync.ESTADO_FUSIONADA);
        vieja.setFusionadaEn(43L);
        Persona buena = persona(43, "ALVARO MARCELO", "CACERES", "BEJARANO", "7479551");
        Escenario e = new Escenario(vieja, buena);
        // Tras unificar, el VSIAF ya tiene el nombre y C.I. buenos en la fila.
        assertEquals(43L, e.r.resolver("ALVARO MARCELO CACERES BEJARANO", "7479551", 688L).getIdPersona());
        // Fila nueva con el nombre viejo y sin C.I.: no cae en la fusionada.
        assertNotEquals(688L, e.r.resolver("A MARCELO CACERES BEJARANO", "", null).getIdPersona());
    }

    @Test
    void especialNoSeEnlazaAUnaPersonaConCi() {
        Escenario e = new Escenario(persona(762, "ACEFALO", null, null, "000000"),
                persona(159, "ACEFALO", null, null, null));
        assertEquals(159L, e.r.resolver("ACEFALO", "000000", null).getIdPersona());
        assertEquals(762L, e.r.resolver("ACEFALO", "000000", 762L).getIdPersona());
    }

    @Test
    void parametroDeCiInvalidosSoloTieneLoQueSeCargue() {
        assertTrue(IdentidadPersona.ciInvalidos(null).isEmpty());
        assertEquals(java.util.Set.of("1", "22"), IdentidadPersona.ciInvalidos(" 1, 2.2 ,"));
    }

    @Test
    void normalizadorCompatibleConElViejoSalvoLaEnie() {
        // Mayúsculas con tilde en código DOS leídas en CP1252 («Í» → «Ö»): se descartan como antes.
        assertEquals("MARA RODRGUEZ", IdentidadPersona.normalizarNombre("MARÖA RODRÖGUEZ"));
        assertEquals("ARGELLO", IdentidadPersona.normalizarNombre("ARGšELLO"));
        assertEquals("MARIA", IdentidadPersona.normalizarNombre("María"));
    }

    @Test
    void filaNuevaNoConfundePinaConPinia() {
        Escenario e = new Escenario(persona(1, "JUAN", "PINA", "ROJAS", "5000001"));
        Persona p = e.r.resolver("JUAN PIÑA ROJAS", "", null);
        assertNotEquals(1L, p.getIdPersona());
        assertEquals("PINA", e.r.resolver("JUAN PINA ROJAS", "", null).getPaterno());
    }

    @Test
    void unificadaSigueReconociendoSusDatosViejos() {
        Persona vieja = persona(7, "JUAN", "PEREZ", "LOPEZ", null);
        vieja.setEstado(ResolutorPersonaSync.ESTADO_FUSIONADA);
        vieja.setFusionadaEn(8L);
        Persona buena = persona(8, "JUAN CARLOS", "PEREZ", "LOPEZ", "6000001");
        Escenario e = new Escenario(vieja, buena);
        // El UPDATE al VSIAF todavía no llegó: la fila sigue con el nombre viejo y sin C.I.
        assertEquals(8L, e.r.resolver("JUAN PEREZ LOPEZ", "", 7L).getIdPersona());
        // Una fila nueva con los datos viejos tampoco rehace el duplicado.
        assertEquals(8L, e.r.resolver("JUAN PEREZ LOPEZ", "", null).getIdPersona());
        assertEquals(0, e.contador("personasCreadas"));
    }

    @Test
    void fusionRotaNoRecibeFilas() {
        Persona rota = persona(9, "ANA", "RUIZ", "VACA", null);
        rota.setEstado(ResolutorPersonaSync.ESTADO_FUSIONADA);
        Escenario e = new Escenario(rota);
        assertNotEquals(9L, e.r.resolver("ANA RUIZ VACA", "", 9L).getIdPersona());
    }

    @Test
    void especialEnlazadoNoSeMueveYNuncaGuardaCi() {
        Persona real = persona(20, "NANCY", "ACUÑA", "ALVARES", "7000001");
        Escenario e = new Escenario(real);
        // Hoy enlazada a la persona real (la sync vieja la juntó por C.I.): se queda.
        assertEquals(20L, e.r.resolver("NANCY ACUÑA ALVARES DEVOLUCION", "7000001", 20L).getIdPersona());
        // Nueva: va a una persona propia, sin C.I.
        Persona nueva = e.r.resolver("NANCY ACUÑA ALVARES DEVOLUCION", "7000001", null);
        assertNotEquals(20L, nueva.getIdPersona());
        assertNull(nueva.getCi());
    }

    @Test
    void personaConCiSinNombreTomaElDeLaFila() {
        Escenario e = new Escenario(persona(30, "SIN DATOS", null, null, "8000001"));
        Persona p = e.r.resolver("LUIS ARCE CATA", "8000001", null);
        assertEquals(30L, p.getIdPersona());
        assertEquals("LUIS", p.getNombre());
        assertEquals("ARCE", p.getPaterno());
    }

    @Test
    void nombreLargoRecortadoEnElVsiafSigueSiendoLaMismaPersona() {
        Persona maria = persona(40, "MARIA DE LOS ANGELES", "FERNANDEZ", "GUTIERREZ", null);
        Escenario e = new Escenario(maria);
        // RESP.DBF guarda 35 caracteres: «MARIA DE LOS ANGELES FERNANDEZ GUTI».
        assertEquals(40L, e.r.resolver("MARIA DE LOS ANGELES FERNANDEZ GUTI", "", 40L).getIdPersona());
        assertEquals(40L, e.r.resolver("MARIA DE LOS ANGELES FERNANDEZ GUTI", "", null).getIdPersona());
        assertEquals(0, e.contador("personasCreadas"));
    }

    @Test
    void sinNombreVaASinDatos() {
        Escenario e = new Escenario(persona(250, "SIN DATOS", null, null, null));
        assertEquals(250L, e.r.resolver("", "", null).getIdPersona());
    }

    // ── Apoyo ─────────────────────────────────────────────────────────────

    private static Persona persona(long id, String nombre, String paterno, String materno, String ci) {
        Persona p = new Persona();
        p.setIdPersona(id);
        p.setNombre(nombre);
        p.setPaterno(paterno);
        p.setMaterno(materno);
        p.setCi(ci);
        p.setEstado("ACTIVO");
        return p;
    }

    private static class Escenario {
        final List<Persona> guardadas = new ArrayList<>();
        final ResolutorPersonaSync r;

        Escenario(Persona... personas) {
            AtomicLong secuencia = new AtomicLong(10_000);
            r = new ResolutorPersonaSync(new ArrayList<>(Arrays.asList(personas)),
                    IdentidadPersona.CI_INVALIDOS_POR_DEFECTO,
                    p -> {
                        if (p.getIdPersona() == null) p.setIdPersona(secuencia.incrementAndGet());
                        guardadas.add(p);
                        return p;
                    },
                    ResolutorPersonaSyncTest::partir);
        }

        int contador(String clave) {
            return (Integer) r.contadores().get(clave);
        }
    }

    /** Igual que la sync: las dos últimas palabras son los apellidos. */
    private static String[] partir(String nombreCompleto) {
        String[] w = IdentidadPersona.normalizarNombre(nombreCompleto).split(" ");
        int n = w.length;
        if (n == 1) return new String[] { w[0], null, null };
        if (n == 2) return new String[] { w[0], w[1], null };
        return new String[] { String.join(" ", Arrays.copyOfRange(w, 0, n - 2)), w[n - 2], w[n - 1] };
    }
}
