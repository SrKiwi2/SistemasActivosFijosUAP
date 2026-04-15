package com.usic.SistemasActivosFijosUAP.model.ServiceImpl;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.usic.SistemasActivosFijosUAP.model.dao.IActivoDao;
import com.usic.SistemasActivosFijosUAP.model.dto.hardware.ActivoMantenimientoDTO;
import com.usic.SistemasActivosFijosUAP.model.service.hardware.ActivoNoEncontradoException;
import com.usic.SistemasActivosFijosUAP.model.service.hardware.IActivoMantenimientoService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ActivoMantenimientoServiceImpl implements IActivoMantenimientoService {
    
    private final IActivoDao activoDao;
    
    @Override
    public Page<ActivoMantenimientoDTO> listarEquiposComputacion(Pageable pageable) {
        log.debug("Listando equipos de computación - página: {}, tamaño: {}",
                  pageable.getPageNumber(), pageable.getPageSize());

        Page<ActivoMantenimientoDTO> resultado = activoDao.findAllEquiposComputacion(
            IActivoDao.GRUPO_EQUIPOS_COMPUTACION,
            pageable
        );

        log.info("Equipos de computación encontrados: {} de {} total",
                 resultado.getNumberOfElements(), resultado.getTotalElements());

        return resultado;
    }

    @Override
    public ActivoMantenimientoDTO buscarPorCodigo(String codigo) {
        log.debug("Buscando equipo de computación por código: {}", codigo);

        return activoDao.findEquipoComputacionByCodigo(
                codigo,
                IActivoDao.GRUPO_EQUIPOS_COMPUTACION
            )
            .orElseThrow(() -> {
                log.warn("Equipo de computación no encontrado para código: {}", codigo);
                return new ActivoNoEncontradoException(
                    "No se encontró un equipo de computación con código: " + codigo
                );
            });
    }
}
