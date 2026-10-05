package com.usic.SistemasActivosFijosUAP.model.ServiceImpl;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.usic.SistemasActivosFijosUAP.model.IService.IHojaRutaService;
import com.usic.SistemasActivosFijosUAP.model.dao.IHojaRutaDao;
import com.usic.SistemasActivosFijosUAP.model.dao.IMovimientoDao;
import com.usic.SistemasActivosFijosUAP.model.dto.HojaRutaTablaDTO;
import com.usic.SistemasActivosFijosUAP.model.entity.HojaRuta;
import com.usic.SistemasActivosFijosUAP.model.entity.Movimiento;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class HojaRutaServiceImpl implements IHojaRutaService{

    private final IHojaRutaDao dao;
    private final IMovimientoDao movimientoDao;

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
    public HojaRuta findByTipoAndCodigoAndGestion(String tipo, String codigo, Integer gestion) {
        if (tipo == null || codigo == null || codigo.isBlank() || gestion == null) return null;
        return dao.findFirstByTipoAndCodigoIgnoreCaseAndGestionOrderByIdHojaRutaAsc(tipo.trim(), codigo.trim(), gestion);
    }

    @Override
    public boolean existe(String tipo, String codigo, Integer gestion, Long excluirId) {
        if (tipo == null || codigo == null || gestion == null) return false;
        return excluirId == null
                ? dao.existsByTipoAndCodigoIgnoreCaseAndGestion(tipo.trim(), codigo.trim(), gestion)
                : dao.existsByTipoAndCodigoIgnoreCaseAndGestionAndIdHojaRutaNot(tipo.trim(), codigo.trim(), gestion, excluirId);
    }

    /** Clave del turno (pg_advisory_xact_lock) de las altas de hojas de ruta. */
    private static final long TURNO_REGISTRO = 0x5C1AF0002L;

    @Override
    public void tomarTurnoRegistro() {
        dao.turnoRegistro(TURNO_REGISTRO);
    }

    @Override
    public List<Integer> gestiones() {
        return dao.gestiones();
    }

    /** Antes: la hoja repetida por cada movimiento y 3 a 4 consultas más por hoja (N+1). */
    @Override
    @Transactional(readOnly = true)
    public List<HojaRutaTablaDTO> listarFiltrados(Integer gestion, Long unidadOrigenId) {
        List<HojaRuta> hojas = dao.listarParaTabla(gestion, unidadOrigenId);

        // Resumen de movimientos: vienen ordenados, el primero de cada hoja es el actual.
        Map<Long, Object[]> actual = new HashMap<>();
        Map<Long, Integer> cuantos = new HashMap<>();
        for (Object[] f : movimientoDao.resumenParaTabla(gestion)) {
            Long id = (Long) f[0];
            actual.putIfAbsent(id, f);
            cuantos.merge(id, 1, Integer::sum);
        }

        List<HojaRutaTablaDTO> lista = new ArrayList<>(hojas.size());
        for (HojaRuta hr : hojas) {
            HojaRutaTablaDTO dto = new HojaRutaTablaDTO();
            dto.setIdHojaRuta(hr.getIdHojaRuta());
            dto.setCodigo(hr.getCodigo());
            dto.setTipo(hr.getTipo());
            dto.setGestion(hr.getGestion());
            dto.setDescripcion(hr.getDescripcion());
            dto.setCertificacion(hr.getCertificacion());
            dto.setMonto(hr.getMonto());
            if (hr.getSolicitante() != null) {
                dto.setSolicitanteNombre(hr.getSolicitante().getNombre());
                dto.setSolicitanteCargo(hr.getSolicitante().getCargo());
            }
            Object[] ultimo = actual.get(hr.getIdHojaRuta());
            if (ultimo != null) {
                dto.setEstadoActual(Movimiento.textoEstado((String) ultimo[1]));
                dto.setFechaUltimo((LocalDate) ultimo[2]);
                dto.setUbicacionActual((String) ultimo[3]);
                dto.setMovimientos(cuantos.getOrDefault(hr.getIdHojaRuta(), 0));
            } else {
                dto.setEstadoActual("SIN MOVIMIENTOS");
            }
            lista.add(dto);
        }
        return lista;
    }
}
