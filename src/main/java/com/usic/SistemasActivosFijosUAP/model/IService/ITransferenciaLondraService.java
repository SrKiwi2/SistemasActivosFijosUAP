package com.usic.SistemasActivosFijosUAP.model.IService;

import java.util.List;

import com.usic.SistemasActivosFijosUAP.model.dto.interoperabilidad.TransferenciaValidadaDto;
import com.usic.SistemasActivosFijosUAP.model.dto.transferencia.TransferenciaAgrupadaDto;
import com.usic.SistemasActivosFijosUAP.model.entity.TransferenciaLondra;

public interface ITransferenciaLondraService {
    List<TransferenciaValidadaDto> leerYValidarPendientes();
    TransferenciaLondra aprobar(String corrT, String usuarioNombre) throws Exception;
    long contarPendientesEnDbf();
    List<TransferenciaAgrupadaDto> leerYValidarAgrupado();
}
