package com.usic.SistemasActivosFijosUAP.model.ServiceImpl;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import com.usic.SistemasActivosFijosUAP.model.IService.IAsignacionActivoService;
import com.usic.SistemasActivosFijosUAP.model.dao.IAsignacionActivoDao;
import com.usic.SistemasActivosFijosUAP.model.entity.Activo;
import com.usic.SistemasActivosFijosUAP.model.entity.AsignacionActivo;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AsignacionActivoServiceImpl implements IAsignacionActivoService {
    private final IAsignacionActivoDao dao;

    @Override
    public List<AsignacionActivo> findAll() {
        return dao.findAll();
    }

    @Override
    public AsignacionActivo findById(Long idEntidad) {
        return dao.findById(idEntidad).orElse(null);
    }

    @Override
    public AsignacionActivo save(AsignacionActivo entidad) {
        return dao.save(entidad);
    }

    @Override
    public void deleteById(Long idEntidad) {
        dao.deleteById(idEntidad);
    }

    @Override
    public List<AsignacionActivo> listarConDetalles() {
        return dao.listarConDetalles();
    }

    @Override
    public List<Activo> listarPendientesSinAsignacion() {
        return dao.listarPendientesSinAsignacion();
    }

    @Override
    public Optional<AsignacionActivo> findByActivo(Activo activo) {
        return dao.findByActivo(activo);
    }

    @Override
    public Optional<AsignacionActivo> findByIdConDetalles(Long id) {
        return dao.findByIdConDetalles(id);
    }

    @Override
    public List<AsignacionActivo> buscarConFiltros(String tipo, String estado, String buscar, String desde,
            String hasta) {
        Specification<AsignacionActivo> spec = (root, query, cb) -> {
        List<Predicate> predicados = new ArrayList<>();

        // 1. Filtro por Tipo (Match exacto)
        if (tipo != null && !tipo.trim().isEmpty()) {
            predicados.add(cb.equal(root.get("tipoAsignacion"), tipo));
        }

        // 2. Filtro por Estado (Match exacto)
        if (estado != null && !estado.trim().isEmpty()) {
            predicados.add(cb.equal(root.get("estadoAsignacion"), estado));
        }

        // 3. Filtro de Búsqueda de texto (LIKE dinámico)
        if (buscar != null && !buscar.trim().isEmpty()) {
            String patronBusqueda = "%" + buscar.trim().toLowerCase() + "%";
            
            // Busca coincidencias en el número de asignación O en el código completo
            Predicate filtroNumero = cb.like(cb.lower(root.get("numeroAsignacion")), patronBusqueda);
            Predicate filtroCodigo = cb.like(cb.lower(root.get("codigoCompleto")), patronBusqueda);
            
            predicados.add(cb.or(filtroNumero, filtroCodigo));
        }

        // 4. Filtro Rango de Fechas (Desde)
        if (desde != null && !desde.trim().isEmpty()) {
            // Convierte el String "YYYY-MM-DD" a LocalDateTime empezando a las 00:00:00
            LocalDateTime fechaDesde = LocalDate.parse(desde).atStartOfDay();
            predicados.add(cb.greaterThanOrEqualTo(root.get("fechaAsignacion"), fechaDesde));
        }

        // 5. Filtro Rango de Fechas (Hasta)
        if (hasta != null && !hasta.trim().isEmpty()) {
            // Convierte a LocalDateTime terminando a las 23:59:59
            LocalDateTime fechaHasta = LocalDate.parse(hasta).atTime(LocalTime.MAX);
            predicados.add(cb.lessThanOrEqualTo(root.get("fechaAsignacion"), fechaHasta));
        }

        // Unimos todos los predicados con un AND
        return cb.and(predicados.toArray(new Predicate[0]));
    };

    // Ejecutamos la consulta ordenando por fecha de asignación descendente (los más recientes primero)
    return dao.findAll(spec, Sort.by(Sort.Direction.DESC, "fechaAsignacion"));
    }
}
