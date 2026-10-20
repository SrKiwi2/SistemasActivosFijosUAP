package com.usic.SistemasActivosFijosUAP.model.service.personas;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Calcula, sin tocar la base, qué cambia en cada fila de responsable cuando en «Personas y
 * cargos» se elige una persona principal, opcionalmente se le suman otras (la misma
 * persona registrada varias veces) y se fija nombre, C.I. y cargo.
 * <p>
 * Lo usan la vista previa, la aplicación directa y la aprobación de una solicitud: los tres
 * ven exactamente el mismo plan. La {@link Resultado#firma()} resume el estado de partida;
 * si al aplicar no coincide con la que vio el usuario, alguien cambió algo entretanto y no
 * se aplica.
 * <p>
 * Nunca toca bienes, oficinas ni la clave de la fila (oficina + código): solo a qué persona
 * pertenece la fila y qué nombre, C.I. y cargo lleva en el VSIAF.
 */
public final class PlanPersona {

    /** Largo de NOMRESP que escribe el SCIAF en RESP.DBF (RespDbfWriterService). */
    public static final int LARGO_NOMRESP = 35;
    /** Largo del campo CARGO de RESP.DBF. */
    public static final int LARGO_CARGO = 40;

    private PlanPersona() {
    }

    public record DatosPersona(Long id, String nombre, String paterno, String materno, String ci,
                               String estado, boolean tieneUsuario) {
        public String nombreCompleto() {
            return String.join(" ", nvl(nombre), nvl(paterno), nvl(materno)).replaceAll("\\s+", " ").trim();
        }
    }

    /** Una fila de RESP (responsable en una oficina) con lo que el SCIAF sabe de ella. */
    public record Fila(Long idResponsable, Long idPersona, Long idOficina, String unidad, Short codOfic,
                       String codResp, String oficina, String cargo, long bienes, boolean esCustodia,
                       boolean pendienteDbf, String estadoVsiaf) {
        public String referencia() {
            return nvl(unidad) + " " + (codOfic == null ? "?" : codOfic) + " · cód. " + nvl(codResp);
        }
    }

    /**
     * Lo que pidió el usuario. {@code nombre/paterno/materno/ci} null = dejar los de la
     * principal; {@code cargo} null = no cambiar cargos. {@code idsFilasCargo}: filas a las
     * que se aplica el cargo (de la principal o de las que se suman).
     */
    public record Decision(Long idPrincipal, List<Long> idsSecundarias, String nombre, String paterno,
                           String materno, String ci, String cargo, List<Long> idsFilasCargo,
                           List<Long> idsFilasNoSumar) {
        /** Sin filas excluidas de la unificación. */
        public Decision(Long idPrincipal, List<Long> idsSecundarias, String nombre, String paterno,
                        String materno, String ci, String cargo, List<Long> idsFilasCargo) {
            this(idPrincipal, idsSecundarias, nombre, paterno, materno, ci, cargo, idsFilasCargo, List.of());
        }
    }

    public record CambioFila(Fila fila, Long idPersonaAntes, String nombreAntes, String ciAntes, String cargoAntes,
                             String nombreDespues, String ciDespues, String cargoDespues,
                             boolean cambiaPersona, boolean cambiaDatos, boolean cambiaCargo,
                             boolean enviaVsiaf, String notaVsiaf) {
        public boolean hayCambio() {
            return cambiaPersona || cambiaDatos || cambiaCargo;
        }
    }

    /**
     * @param errores no se puede aplicar mientras haya alguno
     * @param avisos  se puede aplicar, pero hay que leerlos (C.I. distintos, nombre largo…)
     * @param nuevaPersona cómo queda la principal
     * @param ciEnConflicto las personas del grupo tienen C.I. distintos (exige motivo)
     * @param idsSecundarias las que se suman (alguna fila suya pasa a la principal)
     * @param idsFusionar las que quedan FUSIONADAS: se sumaron todas sus filas. Las que
     *                    conservan alguna fila (marcada «no es de esta persona») siguen vigentes.
     */
    public record Resultado(List<CambioFila> filas, List<String> errores, List<String> avisos,
                            DatosPersona nuevaPersona, boolean cambiaDatosPersona, String cargo,
                            boolean ciEnConflicto, List<Long> idsSecundarias, List<Long> idsFusionar,
                            String firma) {
        public long filasQueCambian() {
            return filas.stream().filter(CambioFila::hayCambio).count();
        }

        public long filasAlVsiaf() {
            return filas.stream().filter(f -> f.hayCambio() && f.enviaVsiaf()).count();
        }
    }

    /**
     * @param principal       la persona que queda
     * @param secundarias     las que se unifican en ella (puede ser vacía)
     * @param filas           filas vigentes de la principal y de las secundarias
     * @param d               lo que pidió el usuario
     * @param ciInvalidos     C.I. que no identifican (parámetro; vacío por defecto)
     * @param personasConCi   ids de las personas (no fusionadas) que ya tienen el número de
     *                        C.I. que quedaría: si alguna no está en el grupo, es otra persona
     */
    public static Resultado calcular(DatosPersona principal, List<DatosPersona> secundarias, List<Fila> filas,
                                     Decision d, Set<String> ciInvalidos, Collection<Long> personasConCi) {
        List<String> errores = new ArrayList<>();
        List<String> avisos = new ArrayList<>();
        List<DatosPersona> sec = secundarias == null ? List.of() : secundarias;
        Set<Long> grupo = new LinkedHashSet<>();
        grupo.add(principal.id());
        sec.forEach(s -> grupo.add(s.id()));

        // ── Quiénes pueden entrar ──
        validarPersona(principal, "La persona principal", errores);
        for (DatosPersona s : sec) {
            if (Objects.equals(s.id(), principal.id())) errores.add("La persona principal no puede sumarse a sí misma.");
            validarPersona(s, s.nombreCompleto(), errores);
        }
        // Filas de las sumadas que el usuario marcó como de OTRA persona: no se mueven.
        Set<Long> noSumar = d.idsFilasNoSumar() == null ? Set.of() : new HashSet<>(d.idsFilasNoSumar());
        List<Long> idsFusionar = new ArrayList<>();
        for (DatosPersona s : sec) {
            boolean conservaAlguna = filas.stream()
                    .anyMatch(f -> Objects.equals(f.idPersona(), s.id()) && noSumar.contains(f.idResponsable()));
            if (!conservaAlguna) idsFusionar.add(s.id());
        }
        long conUsuario = (principal.tieneUsuario() ? 1 : 0)
                + sec.stream().filter(s -> idsFusionar.contains(s.id()) && s.tieneUsuario()).count();
        if (conUsuario > 1) {
            errores.add("Más de una de estas personas tiene usuario del sistema: no se pueden unificar desde aquí "
                    + "(un usuario por persona). Avise al administrador.");
        }

        // ── Datos que quedan ──
        String nombre = texto(d.nombre(), principal.nombre());
        String paterno = textoOpcional(d.nombre() != null || d.paterno() != null, d.paterno(), principal.paterno());
        String materno = textoOpcional(d.nombre() != null || d.materno() != null, d.materno(), principal.materno());
        String ci = d.ci() != null ? limpiarCi(d.ci()) : limpiarCi(principal.ci());
        DatosPersona nueva = new DatosPersona(principal.id(), nombre, paterno, materno, ci, principal.estado(),
                conUsuario > 0);

        if (nombre == null || nombre.isBlank()) errores.add("Falta el nombre.");
        if (paterno == null && materno == null) errores.add("Falta al menos un apellido.");
        String nomresp = nomresp(nueva);
        // C.I.: el que queda no puede ser de alguien fuera del grupo.
        String numero = IdentidadPersona.numeroCi(ci, ciInvalidos);
        if (nueva.nombreCompleto().length() > LARGO_NOMRESP) {
            if (numero == null) {
                // Sin C.I. la sync reconoce a la persona por el nombre, y el VSIAF lo guarda
                // recortado: la próxima pasada no la encontraría y crearía otra.
                errores.add("Sin C.I., el nombre no puede pasar de " + LARGO_NOMRESP + " caracteres (tiene "
                        + nueva.nombreCompleto().length() + "): el VSIAF lo recorta y la persona se volvería a duplicar. "
                        + "Acórtelo o cargue el C.I.");
            } else {
                avisos.add("El nombre completo tiene " + nueva.nombreCompleto().length() + " caracteres: el VSIAF guarda "
                        + LARGO_NOMRESP + " y quedará «" + nomresp + "».");
            }
        }
        if (ci != null && numero == null) {
            avisos.add("El C.I. «" + ci + "» no identifica a la persona (menos de 5 dígitos o en la lista de C.I. no válidos).");
        }
        if (numero != null && personasConCi != null) {
            List<Long> ajenas = personasConCi.stream().filter(id -> !grupo.contains(id)).distinct().toList();
            if (!ajenas.isEmpty()) {
                errores.add("El C.I. " + numero + " ya es de otra persona (id " + ajenas + "). Si es la misma, súmela "
                        + "a la unificación; si no, revise el C.I.");
            }
        }
        Set<String> cis = new LinkedHashSet<>();
        for (DatosPersona p : conPrincipal(principal, sec)) {
            String n = IdentidadPersona.numeroCi(p.ci(), ciInvalidos);
            if (n != null) cis.add(n);
        }
        boolean ciEnConflicto = cis.size() > 1;
        if (ciEnConflicto) {
            avisos.add("Las personas tienen C.I. distintos (" + String.join(", ", cis) + "). Queda "
                    + (numero == null ? "sin C.I." : numero) + "; el motivo es obligatorio y quedará en el historial.");
        }
        boolean cambiaDatosPersona = !Objects.equals(nueva.nombreCompleto(), principal.nombreCompleto())
                || !Objects.equals(nvl(ci), nvl(limpiarCi(principal.ci())));
        long filasPrincipal = filas.stream().filter(f -> Objects.equals(f.idPersona(), principal.id())).count();
        if (cambiaDatosPersona && IdentidadPersona.numeroCi(principal.ci(), ciInvalidos) == null && filasPrincipal > 1) {
            // Una persona sin C.I. con varias filas puede ser un registro que junta a dos
            // funcionarios del mismo nombre: el nombre / C.I. nuevos irían a todas sus filas.
            avisos.add("La principal no tenía C.I. y tiene " + filasPrincipal + " oficinas: el nombre y C.I. nuevos van a "
                    + "TODAS. Si alguna es de otro funcionario con el mismo nombre, no aplique: avise para separarla.");
        }

        // ── Cargo ──
        String cargo = d.cargo() == null ? null : d.cargo().trim().replaceAll("\\s+", " ").toUpperCase();
        if (cargo != null && cargo.isEmpty()) cargo = null;
        Set<Long> filasCargo = d.idsFilasCargo() == null ? Set.of() : new HashSet<>(d.idsFilasCargo());
        if (cargo != null && cargo.length() > LARGO_CARGO) {
            errores.add("El cargo no puede pasar de " + LARGO_CARGO + " caracteres (largo del campo en el VSIAF).");
        }
        if (cargo != null && filasCargo.isEmpty()) errores.add("Marque al menos una oficina para el cargo.");

        // ── Filas ──
        Map<Long, Integer> custodiaPorOficina = new HashMap<>();
        List<CambioFila> cambios = new ArrayList<>();
        List<Fila> ordenadas = new ArrayList<>(filas);
        ordenadas.sort(Comparator.comparing((Fila f) -> nvl(f.unidad())).thenComparing(f -> f.codOfic() == null ? 0 : f.codOfic())
                .thenComparing(f -> nvl(f.codResp())));
        DatosPersona[] porId = conPrincipal(principal, sec).toArray(new DatosPersona[0]);
        for (Fila f : ordenadas) {
            if (!grupo.contains(f.idPersona())) continue;
            boolean seQueda = !Objects.equals(f.idPersona(), principal.id()) && noSumar.contains(f.idResponsable());
            if (seQueda) {
                // Marcada como de otra persona: no se toca (ni persona, ni nombre, ni cargo).
                DatosPersona suya = buscar(porId, f.idPersona());
                cambios.add(new CambioFila(f, f.idPersona(), nomresp(suya), limpiarCi(suya == null ? null : suya.ci()),
                        f.cargo(), nomresp(suya), limpiarCi(suya == null ? null : suya.ci()), f.cargo(),
                        false, false, false, false, "No se suma: queda en su registro."));
                continue;
            }
            if (f.esCustodia() && f.idOficina() != null) custodiaPorOficina.merge(f.idOficina(), 1, Integer::sum);

            DatosPersona duenia = buscar(porId, f.idPersona());
            String nombreAntes = nomresp(duenia);
            String ciAntes = limpiarCi(duenia == null ? null : duenia.ci());
            boolean cambiaPersona = !Objects.equals(f.idPersona(), principal.id());
            boolean cambiaDatos = !Objects.equals(nombreAntes, nomresp) || !Objects.equals(nvl(ciAntes), nvl(ci));
            boolean cambiaCargo = cargo != null && filasCargo.contains(f.idResponsable())
                    && !Objects.equals(nvl(f.cargo()).toUpperCase(), cargo);
            String cargoDespues = cambiaCargo ? cargo : f.cargo();

            boolean enviaVsiaf = !f.pendienteDbf() && !"ERROR".equals(f.estadoVsiaf());
            String nota = f.pendienteDbf() ? "No está en el VSIAF todavía: su alta llevará los datos nuevos."
                    : "ERROR".equals(f.estadoVsiaf()) ? "El VSIAF rechazó su último envío: se reenvía desde Responsables."
                    : "EN_COLA".equals(f.estadoVsiaf()) ? "Tiene un envío en cola; este se aplica después."
                    : null;
            cambios.add(new CambioFila(f, f.idPersona(), nombreAntes, ciAntes, f.cargo(), nomresp, ci, cargoDespues,
                    cambiaPersona, cambiaDatos, cambiaCargo, enviaVsiaf, nota));
        }
        for (Long idCargo : filasCargo) {
            if (noSumar.contains(idCargo)) {
                errores.add("Una oficina marcada para el cargo también está marcada como «no es de esta persona».");
                break;
            }
            if (cambios.stream().noneMatch(c -> Objects.equals(c.fila().idResponsable(), idCargo))) {
                errores.add("Una de las oficinas marcadas para el cargo ya no es de estas personas: vuelva a cargar la ficha.");
                break;
            }
        }
        if (custodiaPorOficina.values().stream().anyMatch(n -> n > 1)) {
            errores.add("Dos de estas personas son custodias en la misma oficina de faltantes: no se pueden unificar "
                    + "todavía (habría que juntar primero sus custodias).");
        }
        if (cambios.stream().noneMatch(CambioFila::hayCambio) && sec.isEmpty() && !cambiaDatosPersona) {
            errores.add("No hay nada que cambiar.");
        }

        String firma = firma(principal, sec, filas, d, ci, nombre, paterno, materno, cargo, noSumar);
        return new Resultado(cambios, errores, avisos, nueva, cambiaDatosPersona, cargo, ciEnConflicto,
                sec.stream().map(DatosPersona::id).toList(), idsFusionar, firma);
    }

    /** NOMRESP tal como lo escribe RespDbfWriterService.construirNombreCompleto. */
    public static String nomresp(DatosPersona p) {
        if (p == null) return "";
        // Igual que RespDbfWriterService: «SIN DATOS» va en blanco al VSIAF.
        if ("SIN DATOS".equalsIgnoreCase(nvl(p.nombre()).trim()) && nvl(p.paterno()).isBlank() && nvl(p.materno()).isBlank()) return "";
        String s = String.join(" ", nvl(p.nombre()).trim(), nvl(p.paterno()).trim(), nvl(p.materno()).trim())
                .trim().toUpperCase();
        if (s.length() > LARGO_NOMRESP) s = s.substring(0, LARGO_NOMRESP).trim();
        return s;
    }

    private static void validarPersona(DatosPersona p, String quien, List<String> errores) {
        if (p == null) {
            errores.add("Una de las personas ya no existe.");
            return;
        }
        if (!"ACTIVO".equals(p.estado())) {
            errores.add(quien + " no está vigente (" + p.estado() + ").");
        }
        if (IdentidadPersona.esNombreEspecial(IdentidadPersona.normalizarNombre(p.nombreCompleto()))) {
            errores.add(quien + " es un registro especial del VSIAF (DEVOLUCION, FALTANTES, ACEFALO…): "
                    + "no se unifica ni se cambia desde aquí.");
        }
    }

    private static List<DatosPersona> conPrincipal(DatosPersona principal, List<DatosPersona> sec) {
        List<DatosPersona> l = new ArrayList<>();
        l.add(principal);
        l.addAll(sec);
        return l;
    }

    private static DatosPersona buscar(DatosPersona[] personas, Long id) {
        for (DatosPersona p : personas) if (Objects.equals(p.id(), id)) return p;
        return null;
    }

    private static String texto(String pedido, String actual) {
        String t = pedido != null ? pedido : actual;
        if (t == null) return null;
        t = t.trim().replaceAll("\\s+", " ").toUpperCase();
        return t.isEmpty() ? null : t;
    }

    /** Apellido: si se pidió cambiar el nombre, el apellido vacío queda vacío (no el viejo). */
    private static String textoOpcional(boolean seEditaron, String pedido, String actual) {
        return seEditaron ? texto(pedido, null) : texto(null, actual);
    }

    private static String limpiarCi(String ci) {
        if (ci == null) return null;
        String t = ci.trim().toUpperCase();
        return t.isEmpty() ? null : t;
    }

    private static String firma(DatosPersona principal, List<DatosPersona> sec, List<Fila> filas, Decision d,
                                String ci, String nombre, String paterno, String materno, String cargo,
                                Set<Long> noSumar) {
        StringBuilder sb = new StringBuilder();
        for (DatosPersona p : conPrincipal(principal, sec)) {
            sb.append(p.id()).append('|').append(p.nombreCompleto()).append('|').append(nvl(p.ci())).append('|')
                    .append(p.estado()).append('|').append(p.tieneUsuario()).append(';');
        }
        filas.stream().sorted(Comparator.comparing(Fila::idResponsable)).forEach(f -> sb.append(f.idResponsable())
                .append('|').append(f.idPersona()).append('|').append(nvl(f.cargo())).append('|')
                .append(f.pendienteDbf()).append(';'));
        sb.append(nombre).append('|').append(paterno).append('|').append(materno).append('|').append(ci).append('|')
                .append(cargo).append('|');
        if (d.idsFilasCargo() != null) d.idsFilasCargo().stream().sorted().forEach(id -> sb.append(id).append(','));
        sb.append('|');
        noSumar.stream().sorted().forEach(id -> sb.append(id).append(','));
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(h, 0, 12);
        } catch (Exception e) {
            return Integer.toHexString(sb.toString().hashCode());
        }
    }

    private static String nvl(String s) {
        return s == null ? "" : s;
    }
}
