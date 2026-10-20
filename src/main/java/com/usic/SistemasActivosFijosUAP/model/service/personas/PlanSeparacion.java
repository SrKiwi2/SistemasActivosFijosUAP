package com.usic.SistemasActivosFijosUAP.model.service.personas;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.usic.SistemasActivosFijosUAP.model.service.personas.PlanPersona.DatosPersona;
import com.usic.SistemasActivosFijosUAP.model.service.personas.PlanPersona.Fila;

/**
 * Calcula, sin tocar la base, cómo pasar oficinas de una persona a otra (fase 6 de
 * docs/PLAN_PERSONAS_UNIFICADAS.md): el caso «SIN DATOS» (oficinas sin nombre en el VSIAF) o
 * dos funcionarios registrados como uno. El destino es una persona que ya existe o una nueva.
 * <p>
 * Solo cambia de qué persona es cada oficina (y por eso su NOMRESP / C.I. en el VSIAF); nunca
 * la oficina, el código, el cargo ni los bienes.
 */
public final class PlanSeparacion {

    private PlanSeparacion() {
    }

    /** Persona nueva a crear como destino (si {@code idDestino} es null). */
    public record Nueva(String nombre, String paterno, String materno, String ci) {
    }

    public record Decision(Long idOrigen, List<Long> idsFilas, Long idDestino, Nueva nueva) {
    }

    public record CambioFila(Fila fila, String nombreAntes, String ciAntes, String nombreDespues, String ciDespues,
                             boolean enviaVsiaf, String nota) {
    }

    public record Resultado(List<CambioFila> filas, List<String> errores, List<String> avisos,
                            DatosPersona destino, boolean destinoNuevo, String firma) {
        public long filasAlVsiaf() {
            return filas.stream().filter(CambioFila::enviaVsiaf).count();
        }
    }

    /**
     * @param origen               la persona de la que salen las oficinas
     * @param filasOrigen          sus oficinas vigentes
     * @param destino              la persona existente elegida (null si es nueva)
     * @param personasConCi        personas (no fusionadas) con el número de C.I. de la nueva
     * @param custodiaDestino      oficinas donde el destino ya es custodia (cualquier estado)
     */
    public static Resultado calcular(Decision d, DatosPersona origen, List<Fila> filasOrigen, DatosPersona destino,
                                     Set<String> ciInvalidos, Collection<Long> personasConCi, Set<Long> custodiaDestino) {
        return calcular(d, origen, filasOrigen, destino, ciInvalidos, personasConCi, custodiaDestino, List.of());
    }

    /**
     * @param homonimos personas vigentes que se llaman igual que la persona nueva (normalizado)
     */
    public static Resultado calcular(Decision d, DatosPersona origen, List<Fila> filasOrigen, DatosPersona destino,
                                     Set<String> ciInvalidos, Collection<Long> personasConCi, Set<Long> custodiaDestino,
                                     Collection<Long> homonimos) {
        List<String> errores = new ArrayList<>();
        List<String> avisos = new ArrayList<>();

        String nombreOrigen = IdentidadPersona.normalizarNombre(origen == null ? "" : origen.nombreCompleto());
        if (origen == null) errores.add("La persona de origen ya no existe.");
        else {
            if (!"ACTIVO".equals(origen.estado())) errores.add("La persona de origen no está vigente (" + origen.estado() + ").");
            // «SIN DATOS» sí: es justamente lo que hay que repartir. Las otras marcas las corrige Saul.
            if (IdentidadPersona.esNombreEspecial(nombreOrigen) && !"SIN DATOS".equals(nombreOrigen)) {
                errores.add("La persona de origen es un registro especial del VSIAF (DEVOLUCION, FALTANTES, ACEFALO…): no se reparte desde aquí.");
            }
        }

        // ── Destino ──
        boolean nuevo = d.idDestino() == null;
        DatosPersona dest;
        if (nuevo) {
            Nueva n = d.nueva();
            if (n == null || vacio(n.nombre()) == null) {
                errores.add("Escriba el nombre de la persona nueva.");
                dest = new DatosPersona(null, null, null, null, null, "ACTIVO", false);
            } else {
                dest = new DatosPersona(null, mayus(n.nombre()), mayus(n.paterno()), mayus(n.materno()), mayus(n.ci()), "ACTIVO", false);
                if (dest.paterno() == null && dest.materno() == null) errores.add("Falta al menos un apellido de la persona nueva.");
                String numero = IdentidadPersona.numeroCi(dest.ci(), ciInvalidos);
                if (dest.ci() != null && numero == null) {
                    avisos.add("El C.I. «" + dest.ci() + "» no identifica a la persona (menos de 5 dígitos o en la lista de no válidos).");
                }
                if (numero != null && personasConCi != null && !personasConCi.isEmpty()) {
                    errores.add("El C.I. " + numero + " ya es de otra persona (id " + personasConCi + "): elíjala como destino en vez de crear otra.");
                }
                if (IdentidadPersona.esNombreEspecial(IdentidadPersona.normalizarNombre(dest.nombreCompleto()))) {
                    errores.add("El nombre de la persona nueva es una marca del VSIAF (SIN DATOS, DEVOLUCION…): escriba el nombre real.");
                }
                if (homonimos != null && !homonimos.isEmpty()) {
                    if (numero == null) {
                        errores.add("Ya existe(n) persona(s) con ese nombre (id " + homonimos + "). Sin C.I. la sincronización no las "
                                + "distinguiría: si es la misma, elíjala como destino; si es otra, cargue su C.I.");
                    } else {
                        avisos.add("Ya existe(n) persona(s) con ese nombre (id " + homonimos + "). Si es la misma, elíjala como destino.");
                    }
                }
                if (numero == null && dest.nombreCompleto().length() > PlanPersona.LARGO_NOMRESP) {
                    errores.add("Sin C.I., el nombre no puede pasar de " + PlanPersona.LARGO_NOMRESP + " caracteres: el VSIAF lo recorta "
                            + "y la sincronización no la reconocería.");
                }
            }
        } else {
            dest = destino;
            if (dest == null) errores.add("La persona de destino ya no existe.");
            else {
                if (Objects.equals(dest.id(), d.idOrigen())) errores.add("El destino es la misma persona de origen.");
                if (!"ACTIVO".equals(dest.estado())) errores.add("La persona de destino no está vigente (" + dest.estado() + ").");
                if (IdentidadPersona.esNombreEspecial(IdentidadPersona.normalizarNombre(dest.nombreCompleto()))) {
                    errores.add("El destino es un registro especial del VSIAF: elija una persona real.");
                }
            }
        }
        // Que la sync las pueda distinguir: con C.I. el destino, o con otro nombre.
        if (dest != null && origen != null && vacio(dest.nombre()) != null
                && IdentidadPersona.numeroCi(dest.ci(), ciInvalidos) == null
                && IdentidadPersona.nombresEquivalentes(IdentidadPersona.normalizarNombre(dest.nombreCompleto()), nombreOrigen)) {
            errores.add("El destino no tiene C.I. y se llama igual que el origen: la sincronización no las distinguiría y "
                    + "volvería a juntarlas. Cargue el C.I. del destino.");
        }

        // ── Oficinas ──
        Set<Long> marcadas = new HashSet<>(d.idsFilas() == null ? List.of() : d.idsFilas());
        if (marcadas.isEmpty()) errores.add("Marque al menos una oficina para pasar.");
        List<CambioFila> cambios = new ArrayList<>();
        String nomAntes = PlanPersona.nomresp(origen);
        String ciAntes = origen == null ? null : vacio(origen.ci());
        String nomDespues = dest == null ? null : PlanPersona.nomresp(dest);
        String ciDespues = dest == null ? null : vacio(dest.ci());
        List<Fila> ordenadas = new ArrayList<>(filasOrigen);
        ordenadas.sort(Comparator.comparing(Fila::referencia));
        Set<Long> vistas = new HashSet<>();
        for (Fila f : ordenadas) {
            if (!marcadas.contains(f.idResponsable())) continue;
            vistas.add(f.idResponsable());
            if (f.esCustodia()) {
                // Las custodias de faltantes llevan actas y plazos: se manejan en Control de Activos › Faltantes.
                errores.add(f.referencia() + ": es una oficina de custodia de faltantes; se maneja desde Faltantes, no desde aquí.");
            }
            boolean envia = !f.pendienteDbf() && !"ERROR".equals(f.estadoVsiaf());
            String nota = f.pendienteDbf() ? "No está en el VSIAF todavía: su alta llevará el nombre nuevo."
                    : "ERROR".equals(f.estadoVsiaf()) ? "El VSIAF rechazó su último envío: se reenvía desde Responsables." : null;
            cambios.add(new CambioFila(f, nomAntes, ciAntes, nomDespues, ciDespues, envia, nota));
        }
        if (!vistas.containsAll(marcadas)) errores.add("Una de las oficinas marcadas ya no es de esta persona: vuelva a cargar la ficha.");
        if (origen != null && "SIN DATOS".equals(nombreOrigen) && !cambios.isEmpty()) {
            avisos.add("Las oficinas de «SIN DATOS» pueden traer un C.I. en el VSIAF que el SCIAF no guarda: revíselo en el VSIAF "
                    + "antes de pasarlas (es la mejor pista de a quién pertenecen); al pasarlas se reemplaza por el del destino.");
        }
        if (!cambios.isEmpty() && cambios.size() == filasOrigen.size()) {
            avisos.add("Pasan TODAS las oficinas: la persona de origen queda sin oficinas (no se borra).");
        }

        return new Resultado(cambios, errores, avisos, dest, nuevo, firma(d, origen, filasOrigen, dest));
    }

    private static String mayus(String s) {
        String t = vacio(s);
        return t == null ? null : t.replaceAll("\\s+", " ").toUpperCase();
    }

    private static String vacio(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String firma(Decision d, DatosPersona origen, List<Fila> filas, DatosPersona dest) {
        StringBuilder sb = new StringBuilder();
        for (DatosPersona p : new DatosPersona[] { origen, dest }) {
            if (p == null) { sb.append("null;"); continue; }
            sb.append(p.id()).append('|').append(p.nombreCompleto()).append('|').append(p.ci()).append('|')
                    .append(p.estado()).append(';');
        }
        filas.stream().sorted(Comparator.comparing(Fila::idResponsable)).forEach(f -> sb.append(f.idResponsable())
                .append('|').append(f.idPersona()).append('|').append(f.pendienteDbf()).append(';'));
        if (d.idsFilas() != null) d.idsFilas().stream().sorted().forEach(id -> sb.append(id).append(','));
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(h, 0, 12);
        } catch (Exception e) {
            return Integer.toHexString(sb.toString().hashCode());
        }
    }
}
