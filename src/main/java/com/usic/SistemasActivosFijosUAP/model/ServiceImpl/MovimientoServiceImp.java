package com.usic.SistemasActivosFijosUAP.model.ServiceImpl;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;

import com.usic.SistemasActivosFijosUAP.model.IService.IMovimientoService;
import com.usic.SistemasActivosFijosUAP.model.dao.IMovimientoDao;
import com.usic.SistemasActivosFijosUAP.model.entity.Movimiento;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MovimientoServiceImp implements IMovimientoService {
    
    private final IMovimientoDao dao;

    @Override
    public List<Movimiento> findAll() {
        return dao.findAll();
    }

    @Override
    public Movimiento findById(Long idEntidad) {
        return dao.findById(idEntidad).orElse(null);
    }

    @Override
    public Movimiento save(Movimiento entidad) {
        return dao.save(entidad);
    }

    @Override
    public void deleteById(Long idEntidad) {
        dao.deleteById(idEntidad);
    }

    @Override
    public List<Movimiento> findByHojaRuta(Long hojaRutaId) {
        return dao.findByHojaRuta(hojaRutaId);
    }

    @Override
    public List<Movimiento> findByEstado(String estado) {
        return dao.findByEstado(estado);
    }

    @Override
    public List<Movimiento> findByFechaBetween(LocalDate fechaInicio, LocalDate fechaFin) {
        return dao.findByFechaBetween(fechaInicio, fechaFin);
    }

    @Override
    public List<Movimiento> findBySolicitante(Long solicitanteId) {
        return dao.findBySolicitante(solicitanteId);
    }

    @Override
    public List<Movimiento> findByUnidad(Long unidadId) {
        return dao.findByUnidad(unidadId);
    }

    @Override
    public List<Movimiento> findByHojaRutaAndEstado(Long hojaRutaId, String estado) {
        return dao.findByHojaRutaAndEstado(hojaRutaId, estado);
    }
}
