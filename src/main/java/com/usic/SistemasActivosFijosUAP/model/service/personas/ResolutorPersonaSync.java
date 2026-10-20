package com.usic.SistemasActivosFijosUAP.model.service.personas;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import com.usic.SistemasActivosFijosUAP.model.entity.Persona;

/**
 * Decide a qué {@link Persona} pertenece cada fila de RESP.DBF durante una sincronización.
 * Se crea uno por sync: carga todas las personas una vez y mantiene sus índices al día.
 * <p>
 * Reglas (docs/PLAN_PERSONAS_UNIFICADAS.md, sección 6):
 * <ol>
 * <li><b>No mover lo que ya está bien:</b> si la persona actual del responsable concuerda con
 *     la fila (mismo C.I., o mismo nombre cuando la fila no trae C.I.), se queda. Así la sync
 *     no reparte de nuevo filas ya enlazadas ni deshace una unificación.</li>
 * <li><b>Fusiones:</b> una persona {@code FUSIONADA} nunca recibe filas. Su C.I. y su nombre
 *     siguen valiendo, pero apuntan a la persona en la que se unificó: una fila que todavía
 *     trae los datos viejos (el UPDATE al VSIAF no llegó) no rehace el duplicado.</li>
 * <li><b>C.I.:</b> se compara solo el número (sin extensión ni complemento).</li>
 * <li><b>Fila nueva:</b> como antes, por C.I. y si no por nombre (si hay una persona sin
 *     C.I. con ese nombre, a ella; si no, a la única con C.I.; con dos o más no se adivina).</li>
 * <li><b>Ñ:</b> un nombre que la sync vieja guardó sin Ñ se reconoce como el mismo (solo la
 *     Ñ borrada para filas nuevas: PENA y PEÑA son apellidos distintos).</li>
 * <li><b>No corrige nada por su cuenta</b> (decisión de Kevin, 15-oct-2026): no completa
 *     C.I., no repone la Ñ en nombres ya guardados. Lo deja como <i>sugerencia</i> para que
 *     un usuario lo aplique desde el módulo, sabiendo qué cambia y por qué.</li>
 * <li><b>Nombres «especiales»</b> (DEVOLUCION, FALTANTES, ACEFALO…): una fila ya enlazada no
 *     se mueve; una nueva se enlaza solo por nombre exacto a una persona sin C.I. y nunca le
 *     guarda un C.I. (D8: los corrige Saul en el VSIAF).</li>
 * </ol>
 */
public class ResolutorPersonaSync {

    public static final String ESTADO_FUSIONADA = "FUSIONADA";
    private static final String SIN_DATOS = "SIN DATOS";

    private final Map<Long, Persona> porId = new HashMap<>();
    /** Persona destino → personas unificadas en ella (sus C.I. y nombres siguen valiendo). */
    private final Map<Long, List<Persona>> unificadasEn = new HashMap<>();
    private final Map<String, List<Persona>> porCi = new HashMap<>();
    private final Map<String, List<Persona>> porNombre = new HashMap<>();
    private final Set<String> ciInvalidos;
    private final UnaryOperator<Persona> guardar;
    private final Function<String, String[]> partirNombre;

    private int creadas;
    private int nombresCompletados;
    private int redirigidasPorFusion;
    private int sugerenciasEnie;
    private int sugerenciasCompletarCi;
    private int sugerenciasMismoNombre;
    private final List<String> sugerencias = new ArrayList<>();
    private static final int MAX_SUGERENCIAS_EN_LOG = 200;

    /**
     * @param todas        todas las personas (cualquier estado)
     * @param ciInvalidos  C.I. falsos ({@link IdentidadPersona#ciInvalidos})
     * @param guardar      persiste y devuelve la persona guardada
     * @param partirNombre NOMRESP → {nombre, paterno, materno}
     */
    public ResolutorPersonaSync(List<Persona> todas, Set<String> ciInvalidos,
                                UnaryOperator<Persona> guardar, Function<String, String[]> partirNombre) {
        this.ciInvalidos = ciInvalidos;
        this.guardar = guardar;
        this.partirNombre = partirNombre;
        for (Persona p : todas) porId.put(p.getIdPersona(), p);
        for (Persona p : todas) {
            if (!ESTADO_FUSIONADA.equals(p.getEstado())) continue;
            Persona destino = destinoDeFusion(p);
            if (destino != null) unificadasEn.computeIfAbsent(destino.getIdPersona(), k -> new ArrayList<>()).add(p);
        }
        for (Persona p : todas) indexar(p);
    }

    /**
     * @param nombreDbf        NOMRESP tal como se leyó (con la Ñ ya repuesta)
     * @param ciDbf            CI tal como se leyó
     * @param idPersonaActual  persona del responsable en el SCIAF (null si es nuevo)
     */
    public Persona resolver(String nombreDbf, String ciDbf, Long idPersonaActual) {
        String nombre = IdentidadPersona.normalizarNombre(nombreDbf);
        boolean especial = IdentidadPersona.esNombreEspecial(nombre);
        // Un nombre especial no se identifica por C.I. (si trae uno, es el de quien lo cargó).
        String ci = especial ? null : IdentidadPersona.numeroCi(ciDbf, ciInvalidos);

        Persona actual = null;
        if (idPersonaActual != null) {
            Persona antes = porId.get(idPersonaActual);
            actual = ESTADO_FUSIONADA.equals(antes == null ? null : antes.getEstado()) ? destinoDeFusion(antes) : antes;
            if (actual != null && actual != antes) redirigidasPorFusion++;
        }
        if (actual != null && (especial || concuerda(actual, nombre, ci))) {
            return conservar(actual, nombre, ci, especial);
        }

        if (ci != null) {
            Persona conCi = elegir(porCi.get(ci));
            if (conCi != null) return conservar(conCi, nombre, null, false);

            if (!nombre.isEmpty()) {
                List<Persona> sinCi = candidatosEstrictos(nombre).stream().filter(p -> ciDe(p) == null).toList();
                if (!sinCi.isEmpty()) {
                    sugerenciasCompletarCi++;
                    sugerir("COMPLETAR_CI", "fila con C.I. " + ci + " y nombre " + nombre
                            + ": hay persona(s) sin C.I. con ese nombre " + ids(sinCi) + "; se creó otra (como antes)");
                }
            }
            return crear(nombre, ci);
        }

        if (nombre.isEmpty()) {
            Persona sd = elegir(porNombre.get(SIN_DATOS));
            return sd != null ? sd : crear(SIN_DATOS, null);
        }

        // Como la sync vieja, por nombre: primero una persona sin C.I. con ese nombre.
        List<Persona> candidatos = candidatosEstrictos(nombre);
        List<Persona> sinCiL = candidatos.stream().filter(p -> ciDe(p) == null).toList();
        List<Persona> conCiL = candidatos.stream().filter(p -> ciDe(p) != null).toList();
        if (!sinCiL.isEmpty()) {
            if (!conCiL.isEmpty() && !especial) {
                sugerenciasMismoNombre++;
                sugerir("MISMO_NOMBRE", nombre + ": personas sin C.I. " + ids(sinCiL) + " y con C.I. " + ids(conCiL));
            }
            return conservar(elegir(sinCiL), nombre, null, especial);
        }
        if (!especial && conCiL.size() == 1) return conservar(conCiL.get(0), nombre, null, false);
        // Dos o más personas con C.I. y ese nombre (o un especial sin persona propia): no se
        // adivina. La fila va a una persona nueva sin C.I., que el módulo muestra en «Duplicados».
        return crear(nombre, null);
    }

    // ── Reglas ────────────────────────────────────────────────────────────

    /** ¿La fila sigue describiendo a esta persona (o a una unificada en ella)? */
    private boolean concuerda(Persona p, String nombre, String ci) {
        for (Persona q : conUnificadas(p)) {
            String ciQ = ciDe(q);
            if (ci != null) {
                if (ci.equals(ciQ)) return true;
                // Persona sin C.I. y mismo nombre, y nadie tiene ese C.I.: la fila se queda (no
                // se le completa el C.I.: queda como sugerencia, ver conservar).
                if (q == p && ciQ == null && elegir(porCi.get(ci)) == null
                        && mismoNombre(q, nombre)) return true;
            } else if (nombre.isEmpty()) {
                if (SIN_DATOS.equals(nombreDe(q))) return true;
            } else if (mismoNombre(q, nombre)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Devuelve la persona tal cual. Lo único que cambia es lo que ya hacía la sync vieja: una
     * persona con C.I. y sin nombre toma el de la fila. Lo demás que no cuadra se anota como
     * sugerencia (C.I. que le falta, Ñ que perdió el nombre guardado).
     */
    private Persona conservar(Persona p, String nombre, String ci, boolean especial) {
        if (!especial && completarNombre(p, nombre)) {
            desindexar(p);
            p = guardar.apply(p);
            porId.put(p.getIdPersona(), p);
            indexar(p);
            nombresCompletados++;
            return p;
        }
        if (ci != null && !especial && ciDe(p) == null) {
            sugerenciasCompletarCi++;
            sugerir("COMPLETAR_CI", "persona " + p.getIdPersona() + " " + nombreDe(p) + ": la fila trae C.I. " + ci);
        }
        String guardado = nombreDe(p);
        if (nombre.indexOf('Ñ') >= 0 && !guardado.equals(nombre)
                && IdentidadPersona.nombresEquivalentes(guardado, nombre)) {
            sugerenciasEnie++;
            sugerir("ENIE", "persona " + p.getIdPersona() + ": guardado «" + guardado + "», en el VSIAF «" + nombre + "»");
        }
        return p;
    }

    /**
     * Persona identificada por C.I. que quedó sin nombre («SIN DATOS» o vacío): toma el de la
     * fila. Lo hacía la sync vieja; la persona comodín (sin C.I.) no se toca.
     */
    private boolean completarNombre(Persona p, String nombre) {
        if (nombre.isEmpty() || SIN_DATOS.equals(nombre) || ciDe(p) == null) return false;
        String guardado = nombreDe(p);
        if (!guardado.isEmpty() && !SIN_DATOS.equals(guardado)) return false;
        String[] partes = partirNombre.apply(nombre);
        if (partes[0] == null || partes[0].isEmpty()) return false;
        p.setNombre(partes[0]);
        p.setPaterno(partes[1]);
        p.setMaterno(partes[2]);
        return true;
    }

    private Persona crear(String nombre, String ci) {
        String[] partes = SIN_DATOS.equals(nombre) ? new String[] { SIN_DATOS, null, null } : partirNombre.apply(nombre);
        Persona p = new Persona();
        p.setNombre(partes[0] == null || partes[0].isEmpty() ? SIN_DATOS : partes[0]);
        p.setPaterno(partes[1]);
        p.setMaterno(partes[2]);
        p.setCi(ci);
        p.setEstado("ACTIVO");
        p = guardar.apply(p);
        porId.put(p.getIdPersona(), p);
        indexar(p);
        creadas++;
        return p;
    }

    /**
     * Persona en la que terminó unificada {@code p}. Null si la cadena está rota (sin destino,
     * destino inexistente o un ciclo): una FUSIONADA nunca se devuelve.
     */
    private Persona destinoDeFusion(Persona p) {
        int saltos = 0;
        while (p != null && ESTADO_FUSIONADA.equals(p.getEstado())) {
            if (p.getFusionadaEn() == null || saltos++ >= 10) return null;
            p = porId.get(p.getFusionadaEn());
        }
        return p;
    }

    // ── Índices ───────────────────────────────────────────────────────────

    /**
     * Indexa a {@code p} por su C.I. y sus nombres, y también por los de las personas
     * unificadas en ella. Una FUSIONADA no se indexa por sí misma.
     */
    private void indexar(Persona p) {
        // Una persona dada de baja no recibe filas nuevas (las que ya tiene se quedan: ver concuerda).
        if (ESTADO_FUSIONADA.equals(p.getEstado()) || "ELIMINADO".equals(p.getEstado())) return;
        for (Persona q : conUnificadas(p)) {
            String ci = ciDe(q);
            if (ci != null) porCi.computeIfAbsent(ci, k -> new ArrayList<>()).add(p);
            for (String n : nombresDe(q)) {
                for (String forma : IdentidadPersona.formasAnteriores(n)) {
                    porNombre.computeIfAbsent(forma, k -> new ArrayList<>()).add(p);
                }
            }
        }
    }

    private void desindexar(Persona p) {
        porCi.values().forEach(l -> l.removeIf(x -> Objects.equals(x.getIdPersona(), p.getIdPersona())));
        porNombre.values().forEach(l -> l.removeIf(x -> Objects.equals(x.getIdPersona(), p.getIdPersona())));
    }

    private List<Persona> conUnificadas(Persona p) {
        List<Persona> l = new ArrayList<>();
        l.add(p);
        l.addAll(unificadasEn.getOrDefault(p.getIdPersona(), List.of()));
        return l;
    }

    /**
     * Personas cuyo nombre (o el de una unificada en ellas) es el de la fila, salvo la Ñ
     * borrada. Sin repetir. Es la comparación para enlazar filas nuevas.
     */
    private List<Persona> candidatosEstrictos(String nombre) {
        Map<Long, Persona> unicos = new LinkedHashMap<>();
        for (String forma : IdentidadPersona.formasAnteriores(nombre)) {
            for (Persona p : porNombre.getOrDefault(forma, List.of())) {
                for (Persona q : conUnificadas(p)) {
                    if (nombresDe(q).stream().anyMatch(n -> IdentidadPersona.nombresEquivalentesEstricto(n, nombre))) {
                        unicos.put(p.getIdPersona(), p);
                        break;
                    }
                }
            }
        }
        return new ArrayList<>(unicos.values());
    }

    /** Entre varias: la ACTIVA y, a igualdad, la más antigua. */
    private static Persona elegir(List<Persona> lista) {
        if (lista == null || lista.isEmpty()) return null;
        return lista.stream()
                .min(Comparator.comparing((Persona p) -> "ACTIVO".equals(p.getEstado()) ? 0 : 1)
                        .thenComparing(Persona::getIdPersona))
                .orElse(null);
    }

    private String ciDe(Persona p) {
        return IdentidadPersona.numeroCi(p.getCi(), ciInvalidos);
    }

    /**
     * El nombre de la persona y cómo queda en NOMRESP: el SCIAF lo escribe recortado a 35
     * caracteres, y una persona sin C.I. con nombre largo se lee después así del VSIAF.
     */
    private static List<String> nombresDe(Persona p) {
        String completo = nombreDe(p);
        String recortado = IdentidadPersona.normalizarNombre(PlanPersona.nomresp(new PlanPersona.DatosPersona(
                p.getIdPersona(), p.getNombre(), p.getPaterno(), p.getMaterno(), null, null, false)));
        return completo.equals(recortado) ? List.of(completo) : List.of(completo, recortado);
    }

    private static boolean mismoNombre(Persona p, String nombre) {
        return nombresDe(p).stream().anyMatch(n -> IdentidadPersona.nombresEquivalentes(n, nombre));
    }

    private static String nombreDe(Persona p) {
        return IdentidadPersona.normalizarNombre(String.join(" ",
                nvl(p.getNombre()), nvl(p.getPaterno()), nvl(p.getMaterno())));
    }

    private static String ids(List<Persona> l) {
        return l.stream().map(p -> String.valueOf(p.getIdPersona())).toList().toString();
    }

    private void sugerir(String tipo, String detalle) {
        if (sugerencias.size() < MAX_SUGERENCIAS_EN_LOG) sugerencias.add(tipo + " · " + detalle);
    }

    private static String nvl(String s) {
        return s == null ? "" : s;
    }

    // ── Contadores para el resultado de la sync ───────────────────────────

    public Map<String, Object> contadores() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("personasCreadas", creadas);
        m.put("nombresCompletados", nombresCompletados);
        m.put("redirigidasPorFusion", redirigidasPorFusion);
        m.put("sugerenciasEnie", sugerenciasEnie);
        m.put("sugerenciasCompletarCi", sugerenciasCompletarCi);
        m.put("sugerenciasMismoNombre", sugerenciasMismoNombre);
        return m;
    }

    /** Detalle de las sugerencias de esta pasada (hasta 200), para el log. */
    public List<String> sugerencias() {
        return sugerencias;
    }
}
