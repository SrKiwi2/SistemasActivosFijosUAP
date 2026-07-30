package com.usic.SistemasActivosFijosUAP.model.ServiceImpl;

import java.util.List;

import org.springframework.stereotype.Service;

import com.usic.SistemasActivosFijosUAP.model.IService.IResponsableEntregaService;
import com.usic.SistemasActivosFijosUAP.model.dao.IResponsableEntregaDao;
import com.usic.SistemasActivosFijosUAP.model.entity.ResponsableEntrega;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ResponsableEntregaServiceImpl implements IResponsableEntregaService {

    private final IResponsableEntregaDao dao;

    @Override
    public List<ResponsableEntrega> findAll() {
        return dao.findAll();
    }

    @Override
    public ResponsableEntrega findById(Long id) {
        return dao.findById(id).orElse(null);
    }

    @Override
    public ResponsableEntrega save(ResponsableEntrega entidad) {
        if (Boolean.TRUE.equals(entidad.getSeleccionado())) {
            unselectAllOthers(entidad.getIdResponsableEntrega());
        }
        return dao.save(entidad);
    }

    @Override
    public void deleteById(Long id) {
        dao.deleteById(id);
    }

    @Override
    public List<ResponsableEntrega> listarActivos() {
        return dao.listarActivos();
    }

    @Override
    public ResponsableEntrega findSeleccionado() {
        return dao.findBySeleccionadoTrueAndEstado("ACTIVO");
    }

    private void unselectAllOthers(Long excludeId) {
        List<ResponsableEntrega> all = dao.listarActivos().stream()
                .filter(r -> !r.getIdResponsableEntrega().equals(excludeId))
                .filter(r -> Boolean.TRUE.equals(r.getSeleccionado()))
                .toList();
        for (ResponsableEntrega r : all) {
            r.setSeleccionado(false);
            dao.save(r);
        }
    }

    @Override
    public List<ResponsableEntrega> buscarPorQ(String q) {
        return (q == null || q.isBlank()) ? dao.listarActivos() : dao.buscarPorQ(q.trim());
    }

    @Override
    public boolean isNombreUnique(String nombre, Long id) {
        if (id == null) {
            return !dao.existsByNombreIgnoreCase(nombre);
        }
        return !dao.existsByNombreIgnoreCaseAndIdResponsableEntregaIsNot(nombre, id);
    }
}
