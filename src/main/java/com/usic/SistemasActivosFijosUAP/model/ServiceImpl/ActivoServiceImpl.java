package com.usic.SistemasActivosFijosUAP.model.ServiceImpl;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.usic.SistemasActivosFijosUAP.model.IService.IActivoService;
import com.usic.SistemasActivosFijosUAP.model.IService.IResponsableService;
import com.usic.SistemasActivosFijosUAP.model.dao.IActivoDao;
import com.usic.SistemasActivosFijosUAP.model.endpoint.OficinaConteo;
import com.usic.SistemasActivosFijosUAP.model.entity.Activo;
import com.usic.SistemasActivosFijosUAP.model.entity.Oficina;
import com.usic.SistemasActivosFijosUAP.model.entity.Persona;
import com.usic.SistemasActivosFijosUAP.model.entity.Responsable;

import jakarta.persistence.criteria.Predicate;

@Service
public class ActivoServiceImpl implements IActivoService{

    @Autowired private IActivoDao dao;
    @Autowired private IResponsableService responsableService;

    @Override
    public List<Activo> findAll() {
        return dao.findAll();
    }

    @Override
    public Activo findById(Long idEntidad) {
        return dao.findById(idEntidad).orElse(null);
    }

    @Override
    public Activo save(Activo entidad) {
        return dao.save(entidad);
    }

    @Override
    public void deleteById(Long idEntidad) {
        dao.deleteById(idEntidad);
    }

    @Override
    public Activo buscarPorNombre(String nombre) {
        return dao.buscarPorNombre(nombre);
    }

    @Override
    public List<Activo> listarActivos() {
        return dao.listarActivos();
    }

    @Override
    public Page<Activo> buscarPorNombreOCodigo(String filtro,Pageable  pageable) {
        if (filtro == null || filtro.isBlank()) {
            return dao.findAll(pageable);
        } else {
            return dao.buscarPorNombreOCodigo(filtro, pageable);
        }
    }


    @Transactional(readOnly = true)
    public Page<Activo> buscarConFiltros(String searchValue, String codigo, String responsableId,
                                        String oficinaId, String fecha, Pageable pageable) {
        Specification<Activo> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (searchValue != null && !searchValue.isBlank()) {
                Predicate p1 = cb.like(cb.lower(root.get("nombre")), "%" + searchValue.toLowerCase() + "%");
                Predicate p2 = cb.like(cb.lower(root.get("codigo")), "%" + searchValue.toLowerCase() + "%");
                predicates.add(cb.or(p1, p2));
            }

            if (codigo != null && !codigo.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("codigo")), "%" + codigo.toLowerCase() + "%"));
            }
            
            if (responsableId != null && !responsableId.isBlank()) {
                predicates.add(cb.equal(root.get("responsable").get("idResponsable"), Long.valueOf(responsableId)));
            }

            if (oficinaId != null && !oficinaId.isBlank()) {
                predicates.add(cb.equal(root.get("oficina").get("idOficina"), Long.valueOf(oficinaId)));
            }

            if (fecha != null && !fecha.isBlank()) {
                try {
                    LocalDate fechaParsed = LocalDate.parse(fecha);
                    predicates.add(cb.equal(root.get("fechaAdquisicion"), fechaParsed));
                } catch (DateTimeParseException e) {
                    e.printStackTrace(); // O manejar adecuadamente
                }
            }
            predicates.add(cb.equal(root.get("estado"), "ACTIVO"));

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return dao.findAll(spec, pageable);
    }

    @Override
    public List<Activo> obtenerActivosDelResponsable(Persona persona) {
        List<Responsable> responsables = responsableService.findAllByPersonaIdPersona(persona.getIdPersona());
        List<Activo> activos = new ArrayList<>();
        for (Responsable responsable : responsables) {
            activos.addAll(dao.findByResponsableIdResponsable(responsable.getIdResponsable()));
        }
        return activos;
    }

    @Transactional(readOnly = true)
    public Optional<Activo> findByCodigo(String codigo) {
       return dao.findByCodigo(codigo);
    }

    @Override
    public Activo buscarPorCodigo(String codigo) {
        return dao.buscarPorCodigo(codigo);
    }

    @Override
    public List<OficinaConteo> conteoPorOficinaDePersona(Long personaId) {
        return dao.conteoPorOficinaDePersona(personaId);
    }

    @Override
    public Double sumaCostoPorPersona(Long personaId) {
        return dao.sumaCostoPorPersona(personaId);
    }

    @Override
    public Optional<Activo> findByOficinaAndCodigo(Oficina oficina, String codigo) {
        return dao.findByOficinaAndCodigo(oficina, codigo);
    }

    @Override
    public void saveAll(List<Activo> batch) {
        dao.saveAll(batch);
    }

    @Override
    public Optional<Activo> fetchFullByCodigo(String codigo) {
        return dao.fetchFullByCodigo(codigo);
    }

    @Override
    public List<Activo> listarActivosPendientes() {
        return dao.listarActivosPendientes();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Activo> findAllById(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return new ArrayList<>();
        }
        
        // JpaRepository.findAllById acepta cualquier Iterable (como List)
        return dao.findAllById(ids);
    }

}