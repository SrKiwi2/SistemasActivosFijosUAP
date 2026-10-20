package com.usic.SistemasActivosFijosUAP.model.service.personas;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Calcula, sin tocar la base, cómo deshacer un lote de «Personas y cargos» (fase 5 de
 * docs/PLAN_PERSONAS_UNIFICADAS.md). <b>Solo se revierte si nada cambió después:</b> cada cosa
 * que el lote tocó tiene que estar hoy exactamente como el lote la dejó; si no, se bloquea y
 * se dice qué. Así nunca se pisa un cambio más nuevo (de otro lote, de la sync o de una
 * edición a mano).
 * <p>
 * Qué se deshace, según lo que guardó el lote:
 * <ul>
 * <li>Fila (oficina): vuelve a la persona y al cargo de antes.</li>
 * <li>{@code NOMBRE_CI} de la persona: vuelven nombre, apellidos y C.I.</li>
 * <li>{@code FUSION_PERSONA}: la persona sumada vuelve a estar vigente, con sus actas de
 *     faltantes y su usuario.</li>
 * <li>{@code CARGO_FUSION}: los cargos juntados vuelven a estar vigentes, el elegido recupera
 *     su nombre y el alias del nombre viejo se da de baja.</li>
 * </ul>
 */
public final class PlanReversion {

    private PlanReversion() {
    }

    /** Una entrada del historial del lote. {@code antes} / {@code despues} ya leídos del JSON. */
    public record Registro(String tipo, Long idResponsable, Long idPersona, String referencia,
                           Map<String, Object> antes, Map<String, Object> despues,
                           String origen, String loteRevertido, String revertidoPor) {
    }

    public record FilaHoy(Long idPersona, Long idCargo, String estado, String referencia,
                          boolean pendienteDbf, String estadoVsiaf) {
    }

    public record PersonaHoy(String nombre, String paterno, String materno, String ci, String estado, Long fusionadaEn) {
    }

    public record CargoHoy(String nombre, String estado, Long fusionadoEn) {
    }

    /**
     * Lo que hay hoy en la base de todo lo que el lote tocó.
     *
     * @param filasFueraPorPersona oficinas vigentes de cada persona que NO están en el lote
     * @param filasFueraPorCargo   oficinas vigentes de cada cargo que NO están en el lote
     * @param personasConCi        número de C.I. → personas vigentes (no fusionadas) que lo tienen
     */
    public record Estado(Map<Long, FilaHoy> filas, Map<Long, PersonaHoy> personas, Map<Long, CargoHoy> cargos,
                         Map<Long, Long> actas, Map<Long, Long> usuarios, Map<String, List<Long>> cargosVigentesPorNombre,
                         Map<Long, Long> filasFueraPorPersona, Map<Long, Long> filasFueraPorCargo,
                         Map<String, List<Long>> personasConCi, java.util.Set<Long> personasConUsuario) {
        public Estado(Map<Long, FilaHoy> filas, Map<Long, PersonaHoy> personas, Map<Long, CargoHoy> cargos,
                      Map<Long, Long> actas, Map<Long, Long> usuarios, Map<String, List<Long>> cargosVigentesPorNombre) {
            this(filas, personas, cargos, actas, usuarios, cargosVigentesPorNombre, Map.of(), Map.of(), Map.of(), java.util.Set.of());
        }

        public Estado(Map<Long, FilaHoy> filas, Map<Long, PersonaHoy> personas, Map<Long, CargoHoy> cargos,
                      Map<Long, Long> actas, Map<Long, Long> usuarios, Map<String, List<Long>> cargosVigentesPorNombre,
                      Map<Long, Long> filasFueraPorPersona, Map<Long, Long> filasFueraPorCargo,
                      Map<String, List<Long>> personasConCi) {
            this(filas, personas, cargos, actas, usuarios, cargosVigentesPorNombre, filasFueraPorPersona,
                    filasFueraPorCargo, personasConCi, java.util.Set.of());
        }
    }

    // ── Acciones ──

    public record RestaurarFila(Long idResponsable, String referencia, Long idPersonaHoy, Long idPersona,
                                Long idCargoHoy, Long idCargo, String cargoHoy, String cargo, boolean enviaVsiaf,
                                String estadoVsiaf) {
    }

    public record RestaurarDatos(Long idPersona, String nombre, String paterno, String materno, String ci,
                                 Map<String, Object> hoy) {
    }

    public record ReactivarPersona(Long idPersona, String nombre, Long desde, List<Long> actas, List<Long> usuarios) {
    }

    public record RestaurarCargos(Long idElegido, String nombreHoy, String nombreAntes, List<Long> reactivar, Long idAlias) {
    }

    /** Persona creada por una separación: al revertir queda sin oficinas y se da de baja. */
    public record BajaPersona(Long idPersona, String nombre) {
    }

    public record Resultado(List<RestaurarFila> filas, List<RestaurarDatos> datos, List<ReactivarPersona> personas,
                            List<RestaurarCargos> cargos, List<BajaPersona> bajas, List<String> errores, String origen,
                            String firma) {
        public long filasAlVsiaf() {
            return filas.stream().filter(RestaurarFila::enviaVsiaf).count();
        }
    }

    public static Resultado calcular(String lote, List<Registro> registros, Estado hoy) {
        List<String> errores = new ArrayList<>();
        List<RestaurarFila> filas = new ArrayList<>();
        List<RestaurarDatos> datos = new ArrayList<>();
        List<ReactivarPersona> personas = new ArrayList<>();
        List<RestaurarCargos> cargos = new ArrayList<>();
        List<BajaPersona> bajas = new ArrayList<>();

        if (registros.isEmpty()) {
            errores.add("El lote no existe.");
            return new Resultado(filas, datos, personas, cargos, bajas, errores, null, firma(lote, hoy));
        }
        String origen = registros.get(0).origen();
        if (!PersonaHistorialService.ORIGEN_PERSONAS_CARGOS.equals(origen) && !CargosService.ORIGEN.equals(origen)) {
            errores.add("Solo se revierten los cambios hechos en Personas y cargos (este viene de " + origen
                    + "): corríjalo en su pantalla.");
        }
        if (registros.stream().anyMatch(r -> r.loteRevertido() != null)) {
            errores.add("Este lote es una reversión: no se revierte una reversión. Si hace falta, aplique el cambio de nuevo.");
        }
        if (registros.stream().anyMatch(r -> r.revertidoPor() != null)) {
            errores.add("Este lote ya se revirtió.");
        }

        // Lo que el mismo lote vuelve a poner vigente: una fila puede volver ahí aunque hoy esté
        // FUSIONADA (es justamente lo que se deshace).
        java.util.Set<Long> personasQueVuelven = new java.util.HashSet<>(), cargosQueVuelven = new java.util.HashSet<>();
        for (Registro r : registros) {
            if (com.usic.SistemasActivosFijosUAP.model.entity.PersonaCambio.TIPO_FUSION_PERSONA.equals(r.tipo())) {
                Long id = num(r.antes().get("idPersona"));
                if (id != null) personasQueVuelven.add(id);
            }
            if (r.antes().get("juntados") instanceof Collection<?> js) {
                for (Object o : js) if (o instanceof Map<?, ?> m) { Long id = num(m.get("id")); if (id != null) cargosQueVuelven.add(id); }
            }
        }

        for (Registro r : registros) {
            String tipo = r.tipo() == null ? "" : r.tipo();
            if (r.idResponsable() != null) {
                fila(r, hoy, filas, errores, personasQueVuelven, cargosQueVuelven);
            } else if (tipo.equals(com.usic.SistemasActivosFijosUAP.model.entity.PersonaCambio.TIPO_DATOS)) {
                datosPersona(r, hoy, datos, errores);
            } else if (tipo.equals(com.usic.SistemasActivosFijosUAP.model.entity.PersonaCambio.TIPO_FUSION_PERSONA)) {
                fusionPersona(r, hoy, personas, errores);
            } else if (tipo.equals(com.usic.SistemasActivosFijosUAP.model.entity.PersonaCambio.TIPO_CARGO_FUSION)) {
                fusionCargos(r, hoy, cargos, errores);
            } else if (tipo.equals(com.usic.SistemasActivosFijosUAP.model.entity.PersonaCambio.TIPO_PERSONA_NUEVA)) {
                personaNueva(r, hoy, bajas, errores);
            }
        }
        if (errores.isEmpty() && filas.isEmpty() && datos.isEmpty() && personas.isEmpty() && cargos.isEmpty() && bajas.isEmpty()) {
            errores.add("El lote no tiene nada que revertir.");
        }
        return new Resultado(filas, datos, personas, cargos, bajas, errores, origen, firma(lote, hoy));
    }

    private static void fila(Registro r, Estado hoy, List<RestaurarFila> filas, List<String> errores,
                             java.util.Set<Long> personasQueVuelven, java.util.Set<Long> cargosQueVuelven) {
        FilaHoy f = hoy.filas().get(r.idResponsable());
        String ref = r.referencia() == null ? "oficina " + r.idResponsable() : r.referencia();
        if (f == null) { errores.add(ref + ": la oficina ya no existe."); return; }
        if (!"ACTIVO".equals(f.estado())) { errores.add(ref + ": la oficina ya no está vigente."); return; }
        Long personaDespues = num(r.despues().get("idPersona"));
        Long personaAntes = num(r.antes().get("idPersona"));
        if (!r.despues().containsKey("idCargo") || !r.antes().containsKey("idCargo")) {
            errores.add(ref + ": el lote no guardó el cargo de antes; no se puede revertir con precisión.");
            return;
        }
        Long cargoDespues = num(r.despues().get("idCargo"));
        Long cargoAntes = num(r.antes().get("idCargo"));
        if (personaDespues != null && !Objects.equals(f.idPersona(), personaDespues)) {
            errores.add(ref + ": después del lote cambió de persona.");
            return;
        }
        if (!Objects.equals(f.idCargo(), cargoDespues)) {
            errores.add(ref + ": después del lote cambió de cargo.");
            return;
        }
        Long persona = personaAntes != null ? personaAntes : f.idPersona();
        // A dónde vuelve tiene que seguir vigente (o volver a estarlo con este mismo lote).
        if (!Objects.equals(persona, f.idPersona()) && !personasQueVuelven.contains(persona)) {
            PersonaHoy ph = hoy.personas().get(persona);
            if (ph == null || !"ACTIVO".equals(ph.estado())) {
                errores.add(ref + ": la persona a la que volvería (id " + persona + ") ya no está vigente"
                        + (ph == null ? "." : " (" + ph.estado() + ")."));
                return;
            }
        }
        if (!Objects.equals(cargoAntes, f.idCargo()) && cargoAntes != null && !cargosQueVuelven.contains(cargoAntes)) {
            CargoHoy ch = hoy.cargos().get(cargoAntes);
            if (ch == null || !"ACTIVO".equals(estado(ch))) {
                errores.add(ref + ": el cargo al que volvería (id " + cargoAntes + ") ya no está vigente"
                        + (ch == null ? "." : " (" + ch.estado() + ")."));
                return;
            }
        }
        boolean envia = !f.pendienteDbf() && !"ERROR".equals(f.estadoVsiaf());
        filas.add(new RestaurarFila(r.idResponsable(), ref, f.idPersona(), persona, f.idCargo(), cargoAntes,
                texto(r.despues().get("cargo")), texto(r.antes().get("cargo")), envia, f.estadoVsiaf()));
    }

    private static void datosPersona(Registro r, Estado hoy, List<RestaurarDatos> datos, List<String> errores) {
        Long id = num(r.antes().get("idPersona"));
        PersonaHoy p = id == null ? null : hoy.personas().get(id);
        if (p == null) { errores.add("La persona del lote ya no existe."); return; }
        Map<String, Object> d = r.despues();
        if (!igual(p.nombre(), d.get("nombre")) || !igual(p.paterno(), d.get("paterno"))
                || !igual(p.materno(), d.get("materno")) || !igual(p.ci(), d.get("ci"))) {
            errores.add("El nombre o el C.I. de la persona cambió después del lote.");
            return;
        }
        Long fuera = hoy.filasFueraPorPersona().getOrDefault(id, 0L);
        if (fuera > 0) {
            errores.add("La persona tiene " + fuera + " oficina(s) que recibió después del lote: volver el nombre / C.I. "
                    + "las dejaría distintas en el VSIAF. Corrija el nombre / C.I. desde la ficha en vez de revertir.");
            return;
        }
        Map<String, Object> a = r.antes();
        String numeroAntes = IdentidadPersona.numeroCi(texto(a.get("ci")), java.util.Set.of());
        if (numeroAntes != null) {
            List<Long> otros = hoy.personasConCi().getOrDefault(numeroAntes, List.of()).stream()
                    .filter(x -> !x.equals(id)).toList();
            if (!otros.isEmpty()) {
                errores.add("El C.I. de antes (" + numeroAntes + ") ya es de otra persona (id " + otros + "): no se puede volver a él.");
                return;
            }
        }
        Map<String, Object> hoyM = new TreeMap<>();
        hoyM.put("nombre", p.nombre());
        hoyM.put("paterno", p.paterno());
        hoyM.put("materno", p.materno());
        hoyM.put("ci", p.ci());
        datos.add(new RestaurarDatos(id, texto(a.get("nombre")), texto(a.get("paterno")), texto(a.get("materno")),
                texto(a.get("ci")), hoyM));
    }

    private static void fusionPersona(Registro r, Estado hoy, List<ReactivarPersona> personas, List<String> errores) {
        Long sec = num(r.antes().get("idPersona"));
        Long principal = num(r.despues().get("fusionadaEn"));
        PersonaHoy p = sec == null ? null : hoy.personas().get(sec);
        String nombre = String.join(" ", texto(r.antes().get("nombre")), texto(r.antes().get("paterno")),
                texto(r.antes().get("materno"))).replaceAll("\\s+", " ").trim();
        if (p == null) { errores.add("La persona sumada (" + nombre + ") ya no existe."); return; }
        if (!r.despues().containsKey("actas") || !r.despues().containsKey("usuarios")) {
            errores.add(nombre + ": el lote no guardó qué actas y usuario se movieron; no se puede revertir con precisión.");
            return;
        }
        if (!PersonasCargosService.ESTADO_FUSIONADA.equals(p.estado()) || !Objects.equals(p.fusionadaEn(), principal)) {
            errores.add(nombre + ": ya no está unificada en la principal (cambió después del lote).");
            return;
        }
        List<Long> actas = nums(r.despues().get("actas"));
        List<Long> usuarios = nums(r.despues().get("usuarios"));
        for (Long a : actas) {
            if (!Objects.equals(hoy.actas().get(a), principal)) errores.add(nombre + ": el acta " + a + " ya no está en la principal.");
        }
        for (Long u : usuarios) {
            if (!Objects.equals(hoy.usuarios().get(u), principal)) errores.add(nombre + ": su usuario ya no está en la principal.");
        }
        personas.add(new ReactivarPersona(sec, nombre, principal, actas, usuarios));
    }

    private static void personaNueva(Registro r, Estado hoy, List<BajaPersona> bajas, List<String> errores) {
        Long id = num(r.despues().get("idPersona"));
        PersonaHoy p = id == null ? null : hoy.personas().get(id);
        String nombre = String.join(" ", texto(r.despues().get("nombre")), texto(r.despues().get("paterno")),
                texto(r.despues().get("materno"))).replaceAll("\\s+", " ").trim();
        if (p == null) { errores.add("La persona creada (" + nombre + ") ya no existe."); return; }
        if (!"ACTIVO".equals(p.estado())) { errores.add(nombre + ": ya no está vigente (" + p.estado() + ")."); return; }
        Map<String, Object> d = r.despues();
        if (!igual(p.nombre(), d.get("nombre")) || !igual(p.paterno(), d.get("paterno"))
                || !igual(p.materno(), d.get("materno")) || !igual(p.ci(), d.get("ci"))) {
            errores.add(nombre + ": su nombre o C.I. cambió después del lote.");
            return;
        }
        Long fuera = hoy.filasFueraPorPersona().getOrDefault(id, 0L);
        if (fuera > 0) { errores.add(nombre + ": recibió " + fuera + " oficina(s) después del lote; no se puede dar de baja."); return; }
        if (hoy.personasConUsuario().contains(id)) {
            errores.add(nombre + ": ya tiene usuario del sistema, actas de faltantes o personas unificadas en ella; no se puede dar de baja.");
            return;
        }
        bajas.add(new BajaPersona(id, nombre));
    }

    private static void fusionCargos(Registro r, Estado hoy, List<RestaurarCargos> cargos, List<String> errores) {
        Long idElegido = num(r.antes().get("idElegido"));
        CargoHoy e = idElegido == null ? null : hoy.cargos().get(idElegido);
        if (e == null) { errores.add("El cargo elegido ya no existe."); return; }
        String nombreDespues = texto(r.despues().get("nombreElegido"));
        String nombreAntes = texto(r.antes().get("nombreElegido"));
        if (!"ACTIVO".equals(estado(e)) || !Objects.equals(PlanCargos.limpio(e.nombre()), PlanCargos.limpio(nombreDespues))) {
            errores.add("El cargo «" + nombreDespues + "» cambió después del lote.");
            return;
        }
        List<Long> reactivar = new ArrayList<>();
        Object j = r.antes().get("juntados");
        if (j instanceof Collection<?> lista) {
            for (Object o : lista) {
                if (!(o instanceof Map<?, ?> m)) continue;
                Long id = num(m.get("id"));
                CargoHoy c = id == null ? null : hoy.cargos().get(id);
                if (c == null) { errores.add("Un cargo juntado (id " + id + ") ya no existe."); continue; }
                if (!CargosService.ESTADO_FUSIONADO.equals(c.estado()) || !Objects.equals(c.fusionadoEn(), idElegido)) {
                    errores.add("«" + c.nombre() + "» ya no está juntado en este cargo (cambió después del lote).");
                    continue;
                }
                List<Long> mismos = hoy.cargosVigentesPorNombre().getOrDefault(PlanCargos.limpio(c.nombre()), List.of())
                        .stream().filter(x -> !x.equals(idElegido)).toList();   // «DOCENTE» / «Docente »: era el mismo
                if (!mismos.isEmpty()) {
                    errores.add("Ya hay otro cargo vigente escrito «" + c.nombre() + "» (id " + mismos + "): no se puede reactivar.");
                    continue;
                }
                reactivar.add(id);
            }
        }
        Long idAlias = num(r.despues().get("idAlias"));
        boolean renombra = !Objects.equals(PlanCargos.limpio(nombreAntes), PlanCargos.limpio(nombreDespues));
        if (renombra) {
            Long fuera = hoy.filasFueraPorCargo().getOrDefault(idElegido, 0L);
            if (fuera > 0) {
                errores.add("El cargo «" + nombreDespues + "» tiene " + fuera + " oficina(s) que recibió después del lote: "
                        + "volver su nombre las dejaría distintas en el VSIAF.");
            }
            List<Long> mismos = hoy.cargosVigentesPorNombre().getOrDefault(PlanCargos.limpio(nombreAntes), List.of()).stream()
                    .filter(id -> !id.equals(idElegido)).toList();
            if (!mismos.isEmpty()) {
                errores.add("Ya hay otro cargo vigente escrito «" + nombreAntes + "» (id " + mismos + "): el elegido no puede volver a ese nombre.");
            }
        }
        cargos.add(new RestaurarCargos(idElegido, e.nombre(), renombra ? nombreAntes : null, reactivar, idAlias));
    }

    // ── Apoyo ──

    private static String estado(CargoHoy c) {
        return c.estado() == null ? "ACTIVO" : c.estado();
    }

    private static boolean igual(String hoy, Object guardado) {
        return Objects.equals(vacio(hoy), vacio(guardado == null ? null : guardado.toString()));
    }

    private static String vacio(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String texto(Object o) {
        return o == null ? "" : o.toString();
    }

    static Long num(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.longValue();
        String t = o.toString().trim();
        return t.isEmpty() ? null : Long.valueOf(t);
    }

    static List<Long> nums(Object o) {
        List<Long> out = new ArrayList<>();
        if (o instanceof Collection<?> c) for (Object x : c) { Long l = num(x); if (l != null) out.add(l); }
        return out;
    }

    /** Resume el estado de hoy: si algo cambia entre la vista previa y la aplicación, no coincide. */
    private static String firma(String lote, Estado hoy) {
        StringBuilder sb = new StringBuilder(lote).append('|');
        new TreeMap<>(hoy.filas()).forEach((k, v) -> sb.append(k).append(v).append(';'));
        new TreeMap<>(hoy.personas()).forEach((k, v) -> sb.append(k).append(v).append(';'));
        new TreeMap<>(hoy.cargos()).forEach((k, v) -> sb.append(k).append(v).append(';'));
        new TreeMap<>(hoy.actas()).forEach((k, v) -> sb.append(k).append('=').append(v).append(';'));
        new TreeMap<>(hoy.usuarios()).forEach((k, v) -> sb.append(k).append('=').append(v).append(';'));
        new TreeMap<>(hoy.cargosVigentesPorNombre()).forEach((k, v) -> sb.append(k).append(v).append(';'));
        new TreeMap<>(hoy.filasFueraPorPersona()).forEach((k, v) -> sb.append('p').append(k).append('=').append(v).append(';'));
        new TreeMap<>(hoy.filasFueraPorCargo()).forEach((k, v) -> sb.append('c').append(k).append('=').append(v).append(';'));
        new TreeMap<>(hoy.personasConCi()).forEach((k, v) -> sb.append(k).append(v).append(';'));
        new java.util.TreeSet<>(hoy.personasConUsuario()).forEach(k -> sb.append('u').append(k).append(';'));
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(h, 0, 12);
        } catch (Exception e) {
            return Integer.toHexString(sb.toString().hashCode());
        }
    }
}
