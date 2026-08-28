package com.usic.SistemasActivosFijosUAP.model.IService;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.repository.query.Param;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.usic.SistemasActivosFijosUAP.model.dto.FiltrosAsignacionDTO;
import com.usic.SistemasActivosFijosUAP.model.dto.ResumenAsignacionDTO;
import com.usic.SistemasActivosFijosUAP.model.dto.ResumenListadoAsignacionDTO;
import com.usic.SistemasActivosFijosUAP.model.entity.Activo;
import com.usic.SistemasActivosFijosUAP.model.entity.AsignacionActivo;
import com.usic.SistemasActivosFijosUAP.model.entity.DetalleAsignacionActivo;

public interface IAsignacionActivoService extends IServiceGenerico<AsignacionActivo, Long>{

    List<AsignacionActivo> listarConDetalles();

    List<Activo> listarPendientesSinAsignacion();

    Optional<AsignacionActivo> findByActivo(@Param("activo") Activo activo);

    Optional<AsignacionActivo> findByIdConDetalles(@Param("id") Long id);

    /**
     * Página del listado de Movimientos, ya filtrada y ordenada.
     *
     * @param orden       columna: fecha (por defecto), numero, documento, responsable,
     *                    oficina, estado, activos o costo
     * @param descendente sentido del orden
     * @param pagina      debe venir SIN {@code Sort}: el orden lo arma el servicio, que es
     *                    el único que puede ordenar por los agregados de los detalles
     */
    Page<AsignacionActivo> buscarConFiltros(FiltrosAsignacionDTO filtros, String orden,
                                            boolean descendente, Pageable pagina);

    /** Totales de las tarjetas, calculados sobre el conjunto filtrado completo. */
    ResumenListadoAsignacionDTO resumenListado(FiltrosAsignacionDTO filtros);

    /** Gestiones que tienen actas, de la más reciente a la más vieja. */
    List<Integer> gestionesConActas();

    /** Bienes que coinciden con el texto, cada uno con el acta vigente donde está hoy. */
    List<DetalleAsignacionActivo> buscarBienesConSuActa(String texto, Long excluirActa);

    /** Actas que coinciden por número o documento, para elegir destino de un traslado. */
    List<AsignacionActivo> buscarActasPorTexto(String texto, Long excluir);

    /** Totales por asignación (costo y avance hacia el VSIAF), indexados por id. */
    Map<Long, ResumenAsignacionDTO> resumenPorAsignacion(List<Long> ids);

}