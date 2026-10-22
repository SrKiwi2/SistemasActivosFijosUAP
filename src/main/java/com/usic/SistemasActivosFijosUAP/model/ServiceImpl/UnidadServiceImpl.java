package com.usic.SistemasActivosFijosUAP.model.ServiceImpl;

import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.usic.SistemasActivosFijosUAP.model.IService.IUnidadService;
import com.usic.SistemasActivosFijosUAP.model.dao.IUnidadDao;
import com.usic.SistemasActivosFijosUAP.model.entity.Unidad;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UnidadServiceImpl implements IUnidadService {
    
    private final IUnidadDao dao;
    private final JdbcTemplate jdbcTemplate;

    /** Por nombre: es como se buscan en las listas. */
    @Override
    public List<Unidad> findAll() {
        return dao.findAll(Sort.by("nombre"));
    }

    @Override
    public Unidad findById(Long idEntidad) {
        return dao.findById(idEntidad).orElse(null);
    }

    @Override
    @Transactional
    public Unidad save(Unidad entidad) {
        if (entidad.getIdUnidad() == null) {
            // Algunas unidades heredadas tienen IDs superiores al siguiente valor de la
            // secuencia. Bloquear la tabla evita que otra alta tome ese ID mientras se
            // alinea la secuencia con los registros existentes.
            jdbcTemplate.execute("LOCK TABLE unidads IN SHARE ROW EXCLUSIVE MODE");
            jdbcTemplate.queryForObject("""
                    SELECT setval(pg_get_serial_sequence('unidads', 'id'),
                                  COALESCE((SELECT MAX(id) FROM unidads), 0) + 1, false)
                    """, Long.class);
        }
        return dao.save(entidad);
    }

    @Override
    public void deleteById(Long idEntidad) {
        dao.deleteById(idEntidad);
    }

    @Override
    public Optional<Unidad> findByNombre(String nombre) {
        return dao.findFirstByNombreOrderByIdUnidadAsc(nombre);
    }
}
