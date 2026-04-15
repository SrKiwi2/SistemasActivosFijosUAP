package com.usic.SistemasActivosFijosUAP.model.service.hardware;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.usic.SistemasActivosFijosUAP.model.dto.hardware.ActivoMantenimientoDTO;

public interface IActivoMantenimientoService {
    /**
     * Retorna todos los equipos de computación paginados.
     *
     * @param pageable Configuración de paginación y ordenamiento.
     * @return Página de DTOs ligeros.
     */
    Page<ActivoMantenimientoDTO> listarEquiposComputacion(Pageable pageable);

    /**
     * Busca un equipo de computación por su código único.
     *
     * @param codigo Código del activo (campo indexado).
     * @return DTO del activo encontrado.
     * @throws com.app.mantenimiento.exception.ActivoNoEncontradoException
     *         si el código no existe o no pertenece al grupo de computación.
     */     
    ActivoMantenimientoDTO buscarPorCodigo(String codigo);
}
