package com.usic.SistemasActivosFijosUAP.model.IService;

import java.util.List;

import com.usic.SistemasActivosFijosUAP.model.dto.transferencia.TransferenciaActivoDetalleDto;
import com.usic.SistemasActivosFijosUAP.model.dto.transferencia.TransferenciaAgrupadaDto;
import com.usic.SistemasActivosFijosUAP.model.entity.TransferenciaCabecera;

public interface ITransferenciaLondraService {
    List<TransferenciaActivoDetalleDto> leerYValidarPendientes();
    
    long contarPendientesEnDbf();
    List<TransferenciaAgrupadaDto> leerYValidarAgrupado();

    TransferenciaCabecera aprobar(String corrT, String usuarioNombre) throws Exception;
    TransferenciaCabecera rechazar(String corrT, String motivo, String usuarioNombre);
    TransferenciaCabecera observar(String corrT, String motivo, String usuarioNombre);
}
