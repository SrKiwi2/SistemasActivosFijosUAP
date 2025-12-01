package com.usic.SistemasActivosFijosUAP.model.ServiceImpl;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.usic.SistemasActivosFijosUAP.model.IService.IHojaRutaService;
import com.usic.SistemasActivosFijosUAP.model.dao.IHojaRutaDao;
import com.usic.SistemasActivosFijosUAP.model.entity.HojaRuta;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class HojaRutaServiceImpl implements IHojaRutaService{
    
    private final IHojaRutaDao dao;

    @Override
    public List<HojaRuta> findAll() {
        return dao.findAll();
    }

    @Override
    public HojaRuta findById(Long idEntidad) {
        return dao.findById(idEntidad).orElse(null);
    }

    @Override
    public HojaRuta save(HojaRuta entidad) {
        return dao.save(entidad);
    }

    @Override
    public void deleteById(Long idEntidad) {
        dao.deleteById(idEntidad);
    }

    @Override
    public Optional<HojaRuta> findByCodigo(String codigo) {
        return dao.findByCodigo(codigo);
    }

    @Override
    public List<HojaRuta> findByGestion(Integer gestion) {
        return dao.findByGestion(gestion);
    }

    @Override
    public List<HojaRuta> findByTipo(String tipo) {
        return dao.findByTipo(tipo);
    }

    @Override
    public List<HojaRuta> findBySolicitante(Long solicitanteId) {
        return dao.findBySolicitante(solicitanteId);
    }

    @Override
    public List<HojaRuta> findByGestionAndTipo(Integer gestion, String tipo) {
        return dao.findByGestionAndTipo(gestion, tipo);
    }

    @Override
    public List<HojaRuta> findByDescripcionContaining(String descripcion) {
        return dao.findByDescripcionContaining(descripcion);
    }
}
