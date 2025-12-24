package com.usic.SistemasActivosFijosUAP.model.ServiceImpl;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.usic.SistemasActivosFijosUAP.model.IService.IConfiguracionGestionService;
import com.usic.SistemasActivosFijosUAP.model.dao.IConfiguracionGestionDao;
import com.usic.SistemasActivosFijosUAP.model.entity.ConfiguracionGestion;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ConfiguracionGestionServiceImpl implements IConfiguracionGestionService {
    private final IConfiguracionGestionDao dao;

    @Override
    public List<ConfiguracionGestion> findAll() {
        return dao.findAll();
    }

    @Override
    public ConfiguracionGestion findById(Long idEntidad) {
        return dao.findById(idEntidad).orElse(null);
    }

    @Override
    public ConfiguracionGestion save(ConfiguracionGestion entidad) {
        return dao.save(entidad);
    }

    @Override
    public void deleteById(Long idEntidad) {
        dao.deleteById(idEntidad);
    }

    @Override
    public Optional<ConfiguracionGestion> findByGestion(Integer gestion) {
        return dao.findByGestion(gestion);
    }
}
