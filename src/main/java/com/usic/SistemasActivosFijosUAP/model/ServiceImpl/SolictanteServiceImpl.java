package com.usic.SistemasActivosFijosUAP.model.ServiceImpl;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.usic.SistemasActivosFijosUAP.model.IService.ISolictanteService;
import com.usic.SistemasActivosFijosUAP.model.dao.ISolicitanteDao;
import com.usic.SistemasActivosFijosUAP.model.entity.Solicitante;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SolictanteServiceImpl implements ISolictanteService {

    private final ISolicitanteDao dao;

    /** Por nombre: es como se buscan en las listas. */
    @Override
    public List<Solicitante> findAll() {
        return dao.findAll(Sort.by("nombre"));
    }

    @Override
    public Solicitante findById(Long idEntidad) {
        return dao.findById(idEntidad).orElse(null);
    }

    @Override
    public Solicitante save(Solicitante entidad) {
        return dao.save(entidad);
    }

    @Override
    public void deleteById(Long idEntidad) {
        dao.deleteById(idEntidad);
    }

    @Override
    public Optional<Solicitante> buscarIgual(String nombre, String cargo) {
        return dao.findFirstByNombreIgnoreCaseAndCargoIgnoreCase(nombre, cargo);
    }
}
