package com.usic.SistemasActivosFijosUAP.model.service.personas;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Calcula, sin tocar la base, qué filas de RESP cambian al juntar varios cargos en uno
 * (pestaña «Cargos» de Personas y cargos). Lo usan la vista previa, la aplicación y la
 * aprobación de una solicitud; la {@link Resultado#firma()} asegura que se aplique lo que se
 * vio.
 * <p>
 * Solo cambia el cargo de las filas: nunca la persona, la oficina, el código ni los bienes.
 * Los cargos que se juntan quedan FUSIONADOS (no se borran).
 */
public final class PlanCargos {

    /** Sobre esta cantidad de filas, la pantalla pide una confirmación extra. */
    public static final int FILAS_CONFIRMACION_EXTRA = 100;

    private PlanCargos() {
    }

    public record Cargo(Long id, String nombre, String estado) {
    }

    public record Fila(Long idResponsable, Long idPersona, String persona, Long idCargo, String referencia,
                       boolean pendienteDbf, String estadoVsiaf) {
    }

    /**
     * @param idElegido  el cargo que queda
     * @param texto      cómo queda escrito (null = como está)
     * @param idsJuntar  los que se juntan con él
     */
    public record Decision(Long idElegido, String texto, List<Long> idsJuntar) {
    }

    public record CambioFila(Fila fila, String cargoAntes, String cargoDespues, boolean enviaVsiaf, String nota) {
    }

    public record Resultado(List<CambioFila> filas, List<String> errores, List<String> avisos, String texto,
                            boolean renombra, List<Long> idsJuntar, String firma) {
        public long filasAlVsiaf() {
            return filas.stream().filter(CambioFila::enviaVsiaf).count();
        }
    }

    /**
     * @param cargos          el elegido y los que se juntan (id → datos)
     * @param filas           filas vigentes que usan alguno de ellos
     * @param otrosConEseTexto ids de cargos vigentes FUERA del grupo con el mismo texto final
     */
    public static Resultado calcular(Decision d, Map<Long, Cargo> cargos, List<Fila> filas, List<Long> otrosConEseTexto) {
        List<String> errores = new ArrayList<>();
        List<String> avisos = new ArrayList<>();
        Cargo elegido = d.idElegido() == null ? null : cargos.get(d.idElegido());
        Set<Long> juntar = new LinkedHashSet<>(d.idsJuntar() == null ? List.of() : d.idsJuntar());
        juntar.remove(d.idElegido());

        if (elegido == null) errores.add("Elija el cargo que queda.");
        else if (!vigente(elegido)) errores.add("El cargo elegido ya no está vigente (" + elegido.estado() + ").");
        for (Long id : juntar) {
            Cargo c = cargos.get(id);
            if (c == null) errores.add("Uno de los cargos a juntar ya no existe (id " + id + ").");
            else if (!vigente(c)) errores.add("«" + c.nombre() + "» ya se juntó con otro cargo.");
        }

        String actual = elegido == null ? null : limpio(elegido.nombre());
        String texto = d.texto() == null ? actual : limpio(d.texto());
        if (texto == null || texto.isEmpty()) errores.add("Escriba cómo queda el cargo.");
        else if (texto.length() > PlanPersona.LARGO_CARGO) {
            errores.add("El cargo no puede pasar de " + PlanPersona.LARGO_CARGO + " caracteres (tiene " + texto.length() + ").");
        }
        if (otrosConEseTexto != null && !otrosConEseTexto.isEmpty()) {
            errores.add("Ya existe otro cargo escrito «" + texto + "» (id " + otrosConEseTexto
                    + "): súmelo a este grupo en vez de duplicarlo.");
        }
        boolean renombra = elegido != null && texto != null && !texto.equals(actual);

        List<CambioFila> cambios = new ArrayList<>();
        List<Fila> ordenadas = new ArrayList<>(filas);
        ordenadas.sort(Comparator.comparing((Fila f) -> nvl(f.referencia())));
        for (Fila f : ordenadas) {
            boolean deElegido = elegido != null && Objects.equals(f.idCargo(), elegido.id());
            if (!deElegido && !juntar.contains(f.idCargo())) continue;
            if (deElegido && !renombra) continue;
            Cargo c = cargos.get(f.idCargo());
            boolean envia = !f.pendienteDbf() && !"ERROR".equals(f.estadoVsiaf());
            String nota = f.pendienteDbf() ? "No está en el VSIAF todavía: su alta llevará el cargo nuevo."
                    : "ERROR".equals(f.estadoVsiaf()) ? "El VSIAF rechazó su último envío: se reenvía desde Responsables."
                    : null;
            cambios.add(new CambioFila(f, c == null ? null : c.nombre(), texto, envia, nota));
        }
        if (juntar.isEmpty() && !renombra) errores.add("No hay nada que cambiar: marque al menos un cargo para juntar.");
        if (cambios.size() > FILAS_CONFIRMACION_EXTRA) {
            avisos.add("Cambian " + cambios.size() + " oficinas: es un cambio grande y se envían " + cambios.size()
                    + " órdenes al VSIAF. Se pedirá una confirmación extra.");
        }
        long sinEnviar = cambios.stream().filter(c -> !c.enviaVsiaf()).count();
        if (sinEnviar > 0) avisos.add(sinEnviar + " oficina(s) no se envían ahora al VSIAF (ver la nota de cada una).");

        String firma = firma(d, texto, cargos, filas, juntar);
        return new Resultado(cambios, errores, avisos, texto, renombra, new ArrayList<>(juntar), firma);
    }

    private static boolean vigente(Cargo c) {
        return c.estado() == null || "ACTIVO".equals(c.estado());
    }

    /** Mayúsculas y espacios simples, como se guarda. */
    public static String limpio(String s) {
        if (s == null) return null;
        return s.trim().replaceAll("\\s+", " ").toUpperCase();
    }

    private static String firma(Decision d, String texto, Map<Long, Cargo> cargos, List<Fila> filas, Set<Long> juntar) {
        StringBuilder sb = new StringBuilder();
        cargos.values().stream().sorted(Comparator.comparing(Cargo::id))
                .forEach(c -> sb.append(c.id()).append('|').append(c.nombre()).append('|').append(c.estado()).append(';'));
        filas.stream().sorted(Comparator.comparing(Fila::idResponsable)).forEach(f -> sb.append(f.idResponsable())
                .append('|').append(f.idCargo()).append('|').append(f.pendienteDbf()).append(';'));
        sb.append(d.idElegido()).append('|').append(texto).append('|');
        juntar.stream().sorted().forEach(id -> sb.append(id).append(','));
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
