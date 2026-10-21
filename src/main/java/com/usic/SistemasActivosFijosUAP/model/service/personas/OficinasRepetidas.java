package com.usic.SistemasActivosFijosUAP.model.service.personas;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.usic.SistemasActivosFijosUAP.model.service.personas.PlanPersona.Fila;

/**
 * Una oficina tiene un solo responsable (fase 9, Saul 21-oct-2026). Si la persona queda con
 * dos registros en la misma oficina, casi siempre el segundo es donde el VSIAF le cargaba sus
 * faltantes (la práctica vieja: la misma persona, a veces con un punto al final del nombre).
 * <p>
 * Reglas, sin base de datos:
 * <ul>
 * <li>Al <b>unificar</b>, no puede quedar una oficina con dos registros con bienes: los del
 *     que sobra van antes a la oficina de faltantes (acta de regularización) o se transfieren.
 *     Los bienes que ya van en camino a la custodia no cuentan.</li>
 * <li>Si los registros ya eran de la misma persona (no se está uniendo nada), solo se avisa.</li>
 * <li>Se puede deshabilitar en el mismo paso un registro repetido sin bienes, dejando al menos uno.</li>
 * </ul>
 */
public final class OficinasRepetidas {

    private OficinasRepetidas() {
    }

    /** Oficinas con más de un registro de la persona (sin las filas marcadas «no es de esta persona»). */
    public static Map<Long, List<Fila>> grupos(List<Fila> filas, Set<Long> noSumar) {
        Map<Long, List<Fila>> porOficina = new LinkedHashMap<>();
        for (Fila f : filas) {
            if (noSumar.contains(f.idResponsable()) || f.idOficina() == null) continue;
            porOficina.computeIfAbsent(f.idOficina(), k -> new ArrayList<>()).add(f);
        }
        porOficina.values().removeIf(l -> l.size() < 2);
        return porOficina;
    }

    /**
     * @param enCamino idResponsable → bienes que ya tienen acta de faltantes pendiente (van a la custodia)
     * @param baja     registros que se deshabilitan en este mismo paso
     */
    public static void validar(List<Fila> filas, Set<Long> noSumar, Map<Long, Long> enCamino, Set<Long> baja,
                               List<String> errores, List<String> avisos) {
        Map<Long, Fila> porId = new LinkedHashMap<>();
        for (Fila f : filas) if (!noSumar.contains(f.idResponsable())) porId.put(f.idResponsable(), f);
        for (Long id : baja) {
            Fila f = porId.get(id);
            if (f == null) {
                errores.add("Uno de los registros a deshabilitar ya no es de esta persona: vuelva a cargar la ficha.");
            } else if (f.bienes() > 0) {
                errores.add(f.referencia() + ": tiene " + f.bienes() + " bien(es) a cargo; no se puede deshabilitar.");
            }
        }

        for (List<Fila> grupo : grupos(filas, noSumar).values()) {
            String oficina = grupo.get(0).unidad() + " " + grupo.get(0).codOfic();
            List<Fila> quedan = grupo.stream().filter(f -> !baja.contains(f.idResponsable())).toList();
            if (quedan.isEmpty()) {
                errores.add(oficina + ": deje al menos un registro habilitado en la oficina.");
                continue;
            }
            List<Fila> conBienes = quedan.stream()
                    .filter(f -> f.bienes() - enCamino.getOrDefault(f.idResponsable(), 0L) > 0).toList();
            boolean seUnen = grupo.stream().map(Fila::idPersona).filter(Objects::nonNull).distinct().count() > 1;
            if (conBienes.size() > 1) {
                String detalle = conBienes.stream().map(f -> "cód. " + f.codResp() + " (" + f.bienes() + " bienes)").toList().toString();
                if (seUnen) {
                    errores.add(oficina + ": quedaría con " + conBienes.size() + " registros con bienes " + detalle
                            + ". Una oficina tiene un solo responsable: pase a faltantes los bienes del que sobra "
                            + "(o transfiéralos) antes de unificar.");
                } else {
                    avisos.add(oficina + ": la persona ya tiene " + conBienes.size() + " registros con bienes " + detalle
                            + ". Si uno es de faltantes, páselos a la oficina de faltantes.");
                }
            } else if (quedan.size() > 1) {
                avisos.add(oficina + ": quedan " + quedan.size() + " registros de la persona. El que no tiene bienes se "
                        + "puede deshabilitar (o se deshabilita después, cuando lleguen sus faltantes a la custodia).");
            }
        }
    }
}
