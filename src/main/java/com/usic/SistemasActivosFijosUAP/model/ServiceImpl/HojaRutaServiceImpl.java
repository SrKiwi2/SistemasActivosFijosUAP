package com.usic.SistemasActivosFijosUAP.model.ServiceImpl;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.usic.SistemasActivosFijosUAP.model.IService.IHojaRutaService;
import com.usic.SistemasActivosFijosUAP.model.dao.IHojaRutaDao;
import com.usic.SistemasActivosFijosUAP.model.dto.HojaRutaTablaDTO;
import com.usic.SistemasActivosFijosUAP.model.entity.HojaRuta;
import com.usic.SistemasActivosFijosUAP.model.entity.Movimiento;

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

    @Override
    public HojaRuta findByTipoAndCodigoAndGestion(String tipo, String codigo, Integer gestion) {
        return dao.findByTipoAndCodigoAndGestion(tipo, codigo, gestion);
    }

    public List<HojaRutaTablaDTO> listarFiltrados(Integer gestion, Long unidadOrigenId) {
        List<HojaRuta> lista = dao.filtrarPorGestionYUnidad(gestion, unidadOrigenId);

        return lista.stream().map(hr -> {
            HojaRutaTablaDTO dto = new HojaRutaTablaDTO();
            dto.setIdHojaRuta(hr.getIdHojaRuta());
            dto.setCodigo(hr.getCodigo());
            dto.setTipo(hr.getTipo());
            dto.setGestion(hr.getGestion());
            dto.setDescripcion(hr.getDescripcion());
            dto.setCertificacion(hr.getCertificacion());
            dto.setMonto(hr.getMonto());
            dto.setSolicitanteNombre(hr.getSolicitante().getNombre());
            dto.setSolicitanteCargo(hr.getSolicitante().getCargo());

            // Último movimiento = estado actual
            List<Movimiento> movs = hr.getMovimientos();
            if (!movs.isEmpty()) {
                Movimiento ultimo = movs.get(movs.size() - 1);
                dto.setEstadoActual(ultimo.getEstadoMovimiento());
                dto.setUnidadOrigenNombre(ultimo.getUnidadOrigen().getNombre());
            } else {
                dto.setEstadoActual("SIN MOVIMIENTO");
                dto.setUnidadOrigenNombre("-");
            }
            return dto;
        }).collect(Collectors.toList());
    }
}
