package com.usic.SistemasActivosFijosUAP.model.service.control;

import com.usic.SistemasActivosFijosUAP.model.entity.Oficina;
import com.usic.SistemasActivosFijosUAP.model.entity.Responsable;

/**
 * La oficina de faltantes de un predio solo recibe bienes por el registro de faltantes
 * ({@link ActaFaltanteService}), que deja el acta y de quién es cada faltante. Si se la
 * pudiera elegir como destino en una transferencia, una asignación o un alta, entrarían
 * bienes a la custodia sin acta ni responsable imputado: justo lo que el módulo evita.
 * <p>
 * La salida es la regla inversa: {@code Activo.exigirNoBloqueado} frena todo movimiento de
 * un bien que está en custodia.
 */
public final class ReglasCustodia {

    private ReglasCustodia() {}

    /** Por qué no se puede usar este destino; null si se puede. */
    public static String motivoDestino(Oficina oficina, Responsable responsable) {
        boolean esCustodia = (oficina != null && oficina.isEsCustodia())
                || (responsable != null && responsable.isEsCustodia())
                || (responsable != null && responsable.getOficina() != null && responsable.getOficina().isEsCustodia());
        if (!esCustodia) return null;
        return "La oficina de faltantes no se puede elegir como destino: los bienes llegan ahí solo "
                + "registrando el faltante (Control de Activos → Faltantes), que emite el acta.";
    }

    /** @throws IllegalStateException si el destino es la oficina de faltantes */
    public static void exigirDestinoValido(Oficina oficina, Responsable responsable) {
        String motivo = motivoDestino(oficina, responsable);
        if (motivo != null) throw new IllegalStateException(motivo);
    }
}
