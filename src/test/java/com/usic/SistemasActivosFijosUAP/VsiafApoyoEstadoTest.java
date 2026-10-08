package com.usic.SistemasActivosFijosUAP;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.usic.SistemasActivosFijosUAP.interoperabilidad.registroDbf.OficinaDbfWriterService;
import com.usic.SistemasActivosFijosUAP.interoperabilidad.registroDbf.RespDbfWriterService;
import com.usic.SistemasActivosFijosUAP.model.IService.IOficinaService;
import com.usic.SistemasActivosFijosUAP.model.IService.IResponsableService;
import com.usic.SistemasActivosFijosUAP.model.dao.IDbfColaOrdenDao;
import com.usic.SistemasActivosFijosUAP.model.dao.IUsuarioDao;
import com.usic.SistemasActivosFijosUAP.model.service.VsiafApoyoService;

class VsiafApoyoEstadoTest {

    @Test
    void noConfirmaUnaOficinaCuandoLaColaNoSePuedeLeer() {
        IDbfColaOrdenDao cola = mock(IDbfColaOrdenDao.class);
        when(cola.findByTablaAndIdRegistroInAndEstadoNotOrderByIdOrdenDesc(
                eq(VsiafApoyoService.TABLA_OFICINA), any(), eq("REINTENTADA")))
                .thenThrow(new IllegalStateException("sin conexión"));
        VsiafApoyoService servicio = new VsiafApoyoService(
                mock(OficinaDbfWriterService.class), mock(RespDbfWriterService.class),
                mock(IOficinaService.class), mock(IResponsableService.class), cola, mock(IUsuarioDao.class));

        var estado = servicio.estados(VsiafApoyoService.TABLA_OFICINA, Map.of(9L, false)).get(9L);

        assertEquals(VsiafApoyoService.EST_EN_COLA, estado.codigo());
        assertTrue(estado.detalle().contains("No se pudo consultar"));
    }
}
