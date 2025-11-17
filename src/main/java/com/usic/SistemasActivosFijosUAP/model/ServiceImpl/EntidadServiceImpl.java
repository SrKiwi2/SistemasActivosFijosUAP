package com.usic.SistemasActivosFijosUAP.model.ServiceImpl;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.usic.SistemasActivosFijosUAP.model.IService.IEntidadService;
import com.usic.SistemasActivosFijosUAP.model.dao.IEntidadDao;
import com.usic.SistemasActivosFijosUAP.model.entity.Entidad;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EntidadServiceImpl implements IEntidadService{

    @PersistenceContext
    private EntityManager entityManager;

    private final IEntidadDao dao;

    @Override
    public List<Entidad> findAll() {
        return dao.findAll();
    }

    @Override
    public Entidad findById(Long idEntidad) {
        return dao.findById(idEntidad).orElse(null);
    }

    @Override
    public Entidad save(Entidad entidad) {
        return dao.save(entidad);
    }

    @Override
    @Transactional
    public void saveAll(List<Entidad> entidades) {
        int batchSize = 500;
        
        for (int i = 0; i < entidades.size(); i++) {
            entityManager.persist(entidades.get(i));
            
            if (i > 0 && i % batchSize == 0) {
                // ✅ CRÍTICO: Flush y clear cada lote
                entityManager.flush();
                entityManager.clear();
            }
        }
        
        // Flush final
        entityManager.flush();
        entityManager.clear();
    }

    @Override
    public void deleteById(Long idEntidad) {
        dao.deleteById(idEntidad);
    }

    @Override
    public Optional<Entidad> findByGestionAndEntidadCodigo(Short gestion, String entidadCodigo) {
        return dao.findByGestionAndEntidadCodigo(gestion, entidadCodigo);
    }

    @Override
    public List<Entidad> saveAll(Iterable<Entidad> entidades) {
        return dao.saveAll(entidades);
    }

    @Override
    public Optional<Entidad> findTopByEntidadCodigoOrderByGestionDesc(String entidadCodigo) {
        return dao.findTopByEntidadCodigoOrderByGestionDesc(entidadCodigo);
    }

    @Override
    public List<Entidad> buscarPorQ(String q) {
        return dao.buscarPorQ(q);
    }

    @Override
    public List<Entidad> findByGestion(Short gestion) {
        return dao.findByGestion(gestion);
    }

}