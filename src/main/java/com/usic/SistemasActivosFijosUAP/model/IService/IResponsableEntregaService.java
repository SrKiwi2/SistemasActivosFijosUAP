package com.usic.SistemasActivosFijosUAP.model.IService;

import java.util.List;

import com.usic.SistemasActivosFijosUAP.model.entity.ResponsableEntrega;

public interface IResponsableEntregaService extends IServiceGenerico<ResponsableEntrega, Long> {
    List<ResponsableEntrega> listarActivos();
    List<ResponsableEntrega> buscarPorQ(String q);
    boolean isNombreUnique(String nombre, Long id);
    ResponsableEntrega findSeleccionado();
}
