package com.usic.SistemasActivosFijosUAP.model.IService;

import java.util.Optional;

import com.usic.SistemasActivosFijosUAP.model.entity.ConfiguracionGestion;

public interface IConfiguracionGestionService extends IServiceGenerico<ConfiguracionGestion, Long>{
    Optional<ConfiguracionGestion> findByGestion(Integer gestion);
    ConfiguracionGestion findByPrefijoDocumento(String PrefijoDocumento);
}
