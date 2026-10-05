package com.usic.SistemasActivosFijosUAP.model.IService;

import java.util.List;

import com.usic.SistemasActivosFijosUAP.model.dto.HojaRutaTablaDTO;
import com.usic.SistemasActivosFijosUAP.model.entity.HojaRuta;

public interface IHojaRutaService extends IServiceGenerico<HojaRuta, Long> {

    /** Por tipo + código + gestión (el código sin distinguir mayúsculas); null si no existe. */
    HojaRuta findByTipoAndCodigoAndGestion(String tipo, String codigo, Integer gestion);

    /**
     * Listado de hojas con su estado actual, en dos consultas (hojas + resumen de
     * movimientos) y sin repetir filas. Sin gestión: todas; con unidad: las que tienen algún
     * movimiento que salió de esa unidad.
     */
    List<HojaRutaTablaDTO> listarFiltrados(Integer gestion, Long unidadOrigenId);

    /**
     * ¿Ya existe una hoja con esa combinación (sin contar {@code excluirId})? Considera todas
     * las coincidencias, también los duplicados viejos y las variantes de mayúsculas.
     */
    boolean existe(String tipo, String codigo, Integer gestion, Long excluirId);

    /**
     * Turno de registro del módulo: llamarlo dentro de la transacción, antes de comprobar si
     * algo ya existe. Se libera al terminar la transacción.
     */
    void tomarTurnoRegistro();

    /** Gestiones con hojas registradas, la más reciente primero. */
    List<Integer> gestiones();
}
