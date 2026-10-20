package com.usic.SistemasActivosFijosUAP.model.service.personas;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Arma los grupos de cargos que probablemente son el mismo (pestaña «Cargos» de Personas y
 * cargos, fase 3 de docs/PLAN_PERSONAS_UNIFICADAS.md). No toca la base.
 * <ul>
 * <li><b>SEGURO</b>: solo cambian puntos, guiones, espacios o tildes
 *     ({@code DIRECTORA ACBN} / {@code DIRECTORA A.C.B.N.}).</li>
 * <li><b>PROBABLE</b>: mismas palabras salvo una abreviada ({@code CARRE.} / {@code CARRERA})
 *     o con un error de tipeo ({@code ADMINITRATIVO} / {@code ADMINISTRATIVO}). Una palabra
 *     de 3 letras solo cuenta como abreviatura si en el cargo va con punto ({@code ADM.}):
 *     las siglas cortas ({@code ACS} / {@code ACSYH}) suelen ser áreas distintas.
 *     <b>Nunca</b> junta variantes de género o número ({@code DIRECTOR} / {@code DIRECTORA},
 *     {@code TECNICO} / {@code TECNICOS}): el cargo de cada persona es el suyo.</li>
 * <li><b>REVISAR</b>: uno es el otro con palabras de más al final
 *     ({@code ADMINISTRATIVO} / {@code ADMINISTRATIVO ACYT}). Suelen ser cargos distintos de
 *     verdad: se muestran sin marcar y nunca se aplican en bloque.</li>
 * </ul>
 * Los pares que alguien marcó como «no son el mismo cargo» no vuelven a juntarse.
 */
public final class AgrupadorCargos {

    public static final String SEGURO = "SEGURO";
    public static final String PROBABLE = "PROBABLE";
    public static final String REVISAR = "REVISAR";

    private AgrupadorCargos() {
    }

    /** Un cargo vigente con cuántas filas (responsables activos) y personas lo usan. */
    public record CargoUso(Long id, String nombre, long filas, long personas) {
    }

    /**
     * @param sinMarcar  cargos que la pantalla NO marca al abrir: los que difieren en género o
     *                   número del primero o de cualquier otro del grupo ({@code DIRECTOR} /
     *                   {@code DIRECTORA}), y en REVISAR todos. Se juntan solo si alguien lo decide.
     * @param conflictos id → ids del grupo con los que difiere en género o número. Al cambiar
     *                   el elegido, la pantalla desmarca los que chocan con él.
     */
    public record Grupo(String nivel, List<CargoUso> cargos, List<Long> sinMarcar, Map<Long, List<Long>> conflictos) {
        public long filas() {
            return cargos.stream().mapToLong(CargoUso::filas).sum();
        }
    }

    /**
     * @param cargos      cargos vigentes en uso
     * @param descartados pares ya revisados como distintos: {@link #par(Long, Long)}
     */
    public static List<Grupo> agrupar(List<CargoUso> cargos, Set<String> descartados) {
        int n = cargos.size();
        String[] clave = new String[n];
        String[][] palabras = new String[n][];
        List<Set<String>> conPunto = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            String norm = IdentidadPersona.normalizarNombre(cargos.get(i).nombre());
            clave[i] = norm.replace(" ", "");
            palabras[i] = norm.isEmpty() ? new String[0] : norm.split(" ");
            conPunto.add(abreviadasConPunto(cargos.get(i).nombre()));
        }

        // SEGURO y PROBABLE se encadenan (A~B, B~C → un grupo); REVISAR va aparte.
        int[] padre = new int[n];
        for (int i = 0; i < n; i++) padre[i] = i;
        boolean[] probableEn = new boolean[n];
        for (int i = 0; i < n; i++) {
            if (clave[i].isEmpty()) continue;
            for (int j = i + 1; j < n; j++) {
                if (clave[j].isEmpty()) continue;
                if (descartados.contains(par(cargos.get(i).id(), cargos.get(j).id()))) continue;
                String nivel = clave[i].equals(clave[j]) ? SEGURO
                        : mismasPalabras(palabras[i], palabras[j], conPunto.get(i), conPunto.get(j)) ? PROBABLE : null;
                if (nivel == null) continue;
                int a = raiz(padre, i), b = raiz(padre, j);
                if (a != b) padre[a] = b;
                if (PROBABLE.equals(nivel)) { probableEn[i] = true; probableEn[j] = true; }
            }
        }
        Map<Integer, List<Integer>> comp = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) comp.computeIfAbsent(raiz(padre, i), k -> new ArrayList<>()).add(i);

        List<Grupo> grupos = new ArrayList<>();
        for (List<Integer> idx : comp.values()) {
            if (idx.size() < 2) continue;
            boolean probable = idx.stream().anyMatch(i -> probableEn[i]);
            List<CargoUso> l = ordenar(idx.stream().map(cargos::get).toList());
            Map<Long, List<Long>> conf = conflictos(l);
            // Sin marcar: los que chocan con el primero (el que queda al abrir). Si el primero es
            // neutro («DIR. ACBN»: no choca con nadie), los que chocan entre sí, sin adivinar.
            Long primero = l.get(0).id();
            boolean primeroNeutro = conf.get(primero).isEmpty();
            List<Long> sin = l.subList(1, l.size()).stream().map(CargoUso::id)
                    .filter(id -> conf.get(id).contains(primero) || (primeroNeutro && !conf.get(id).isEmpty())).toList();
            grupos.add(new Grupo(probable ? PROBABLE : SEGURO, l, sin, conf));
        }

        // REVISAR: un cargo base y los que lo extienden con palabras de más.
        Map<Integer, List<Integer>> extensiones = new HashMap<>();
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (i == j || palabras[i].length == 0 || palabras[j].length <= palabras[i].length) continue;
                if (palabras[j].length - palabras[i].length > 3) continue;
                if (raiz(padre, i) == raiz(padre, j)) continue;               // ya están juntos
                if (descartados.contains(par(cargos.get(i).id(), cargos.get(j).id()))) continue;
                if (empiezaCon(palabras[j], palabras[i])) extensiones.computeIfAbsent(i, k -> new ArrayList<>()).add(j);
            }
        }
        for (Map.Entry<Integer, List<Integer>> e : extensiones.entrySet()) {
            List<CargoUso> l = new ArrayList<>();
            l.add(cargos.get(e.getKey()));
            l.addAll(ordenar(e.getValue().stream().map(cargos::get).toList()));
            grupos.add(new Grupo(REVISAR, l, l.subList(1, l.size()).stream().map(CargoUso::id).toList(), conflictos(l)));
        }

        grupos.sort(Comparator.comparing((Grupo g) -> REVISAR.equals(g.nivel()) ? 1 : 0)
                .thenComparing(Comparator.comparingLong(Grupo::filas).reversed()));
        return grupos;
    }

    /** Para cada cargo del grupo, con cuáles difiere en género o número. */
    static Map<Long, List<Long>> conflictos(List<CargoUso> l) {
        Map<Long, List<Long>> m = new LinkedHashMap<>();
        List<String[]> pal = l.stream().map(c -> palabrasDe(c.nombre())).toList();
        for (int i = 0; i < l.size(); i++) {
            List<Long> con = new ArrayList<>();
            for (int j = 0; j < l.size(); j++) {
                if (i != j && difiereGeneroONumero(pal.get(i), pal.get(j))) con.add(l.get(j).id());
            }
            m.put(l.get(i).id(), con);
        }
        return m;
    }

    /** Palabras para comparar género: las letras sueltas de una sigla se juntan (A C B N → ACBN). */
    private static String[] palabrasDe(String nombre) {
        String n = IdentidadPersona.normalizarNombre(nombre);
        if (n.isEmpty()) return new String[0];
        List<String> out = new ArrayList<>();
        StringBuilder sigla = new StringBuilder();
        for (String w : n.split(" ")) {
            if (w.length() == 1) { sigla.append(w); continue; }
            if (sigla.length() > 0) { out.add(sigla.toString()); sigla.setLength(0); }
            out.add(w);
        }
        if (sigla.length() > 0) out.add(sigla.toString());
        return out.toArray(new String[0]);
    }

    /** Alguna palabra en la misma posición es de otro género o número (abreviaturas aparte). */
    static boolean difiereGeneroONumero(String[] a, String[] b) {
        // Distinta cantidad de palabras: se compara la primera (DIRECTOR…, SECRETARIA…).
        int hasta = a.length == b.length ? a.length : Math.min(1, Math.min(a.length, b.length));
        for (int k = 0; k < hasta; k++) {
            if (a[k].equals(b[k])) continue;
            if (generoONumero(a[k], b[k])) return true;
            char ga = genero(a[k]), gb = genero(b[k]);
            if (ga != '-' && gb != '-' && ga != gb) return true;
        }
        return false;
    }

    /** F si termina en A; M si en O u OR; '-' si no se sabe (abreviaturas, siglas, -E…). */
    private static char genero(String w) {
        String t = sinPlural(w);
        if (t.length() < 4) return '-';
        if (t.endsWith("A")) return 'F';
        if (t.endsWith("O") || t.endsWith("OR")) return 'M';
        return '-';
    }

    /** {@code AUXILIARES} → {@code AUXILIAR}, {@code TECNICOS} → {@code TECNICO}. */
    private static String sinPlural(String w) {
        if (w.endsWith("ES") && w.length() > 5 && !esVocal(w.charAt(w.length() - 3))) return w.substring(0, w.length() - 2);
        if (w.endsWith("S") && w.length() > 4) return w.substring(0, w.length() - 1);
        return w;
    }

    private static boolean esVocal(char c) {
        return "AEIOU".indexOf(c) >= 0;
    }

    /** Clave de un par de cargos, sin importar el orden. */
    public static String par(Long a, Long b) {
        return Math.min(a, b) + "-" + Math.max(a, b);
    }

    /** Mismo número de palabras y cada una igual, abreviada o con un error de tipeo. */
    static boolean mismasPalabras(String[] a, String[] b, Set<String> puntoA, Set<String> puntoB) {
        if (a.length != b.length || a.length == 0) return false;
        boolean alguna = false;
        for (int k = 0; k < a.length; k++) {
            if (a[k].equals(b[k])) continue;
            if (generoONumero(a[k], b[k])) return false;
            boolean ultima = k == a.length - 1;
            boolean abrev = a[k].length() <= b[k].length() ? abreviatura(a[k], b[k], puntoA, ultima)
                    : abreviatura(b[k], a[k], puntoB, ultima);
            if (!abrev && !tipeo(a[k], b[k])) return false;
            alguna = true;
        }
        return alguna;
    }

    /**
     * {@code CARRE.} / {@code CARRERA}, {@code UNIV.} / {@code UNIVERSITARIO}: la corta empieza la
     * larga y en su cargo va con punto (3+ letras). Sin punto solo si es la última palabra y
     * tiene 4+ letras: el texto quedó cortado por el largo del campo ({@code TURISMO SOSTEN}).
     * Así {@code VENTA} / {@code VENTANILLA} no se juntan.
     */
    private static boolean abreviatura(String corta, String larga, Set<String> conPunto, boolean ultima) {
        if (corta.length() >= larga.length() || !larga.startsWith(corta) || corta.length() < 3) return false;
        return conPunto.contains(corta) || (ultima && corta.length() >= 4);
    }

    /**
     * {@code DIRECTOR}/{@code DIRECTORA}, {@code DECANO}/{@code DECANA},
     * {@code PRESIDENTE}/{@code PRESIDENTA}, {@code TECNICO}/{@code TECNICOS},
     * {@code AUXILIAR}/{@code AUXILIARES}.
     */
    static boolean generoONumero(String x, String y) {
        return !x.equals(y) && base(x).equals(base(y));
    }

    private static String base(String w) {
        String t = sinPlural(w);
        if (t.length() > 3 && (t.endsWith("A") || t.endsWith("O") || t.endsWith("E"))) t = t.substring(0, t.length() - 1);
        return t;
    }

    /** Palabras escritas con punto en el cargo original: {@code ADM. CAMPUS} → {ADM}. */
    private static Set<String> abreviadasConPunto(String nombre) {
        Set<String> s = new java.util.HashSet<>();
        if (nombre == null) return s;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("([A-ZÑÁÉÍÓÚ]+)\\.").matcher(nombre.toUpperCase());
        while (m.find()) s.add(IdentidadPersona.normalizarNombre(m.group(1)));
        return s;
    }

    /**
     * Un error de tipeo: distancia 1 en palabras de 5+ letras; 2 solo en las de 10+ que empiezan
     * igual ({@code BIOLOGIA} / {@code ECOLOGIA} no). Si solo cambia la última letra no es tipeo
     * sino otra palabra o forma ({@code PERSONAL} / {@code PERSONAS}).
     */
    private static boolean tipeo(String x, String y) {
        int min = Math.min(x.length(), y.length());
        if (min < 5) return false;
        if (x.length() == y.length() && x.substring(0, x.length() - 1).equals(y.substring(0, y.length() - 1))) return false;
        int d = distancia(x, y, 2);
        if (d <= 1) return true;
        return d == 2 && min >= 10 && x.charAt(0) == y.charAt(0) && x.charAt(1) == y.charAt(1);
    }

    private static boolean empiezaCon(String[] largo, String[] corto) {
        for (int k = 0; k < corto.length; k++) if (!largo[k].equals(corto[k])) return false;
        return true;
    }

    /** Levenshtein con tope (devuelve tope+1 si se pasa). */
    static int distancia(String a, String b, int tope) {
        if (Math.abs(a.length() - b.length()) > tope) return tope + 1;
        int[] prev = new int[b.length() + 1], cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            int minFila = cur[0];
            for (int j = 1; j <= b.length(); j++) {
                int c = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + c);
                minFila = Math.min(minFila, cur[j]);
            }
            if (minFila > tope) return tope + 1;
            int[] t = prev; prev = cur; cur = t;
        }
        return prev[b.length()];
    }

    private static int raiz(int[] padre, int i) {
        while (padre[i] != i) { padre[i] = padre[padre[i]]; i = padre[i]; }
        return i;
    }

    /** El más usado primero (suele ser el correcto). */
    private static List<CargoUso> ordenar(List<CargoUso> l) {
        return l.stream().sorted(Comparator.comparingLong(CargoUso::filas).reversed().thenComparing(CargoUso::id)).toList();
    }
}
