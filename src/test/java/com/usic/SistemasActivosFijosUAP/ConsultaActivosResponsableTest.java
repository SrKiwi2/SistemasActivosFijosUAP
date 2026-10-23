package com.usic.SistemasActivosFijosUAP;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.usic.SistemasActivosFijosUAP.config.Encriptar;
import com.usic.SistemasActivosFijosUAP.controller.rest.CatalogoRestController;
import com.usic.SistemasActivosFijosUAP.model.IService.IActivoService;
import com.usic.SistemasActivosFijosUAP.model.IService.IResponsableService;
import com.usic.SistemasActivosFijosUAP.model.dao.IResposableDao;
import com.usic.SistemasActivosFijosUAP.model.dto.responsable.ResponsableCardDataDTO;
import com.usic.SistemasActivosFijosUAP.model.entity.Oficina;
import com.usic.SistemasActivosFijosUAP.model.entity.Predio;
import com.usic.SistemasActivosFijosUAP.model.entity.Responsable;

@ExtendWith(MockitoExtension.class)
class ConsultaActivosResponsableTest {
    @Mock IResponsableService responsableService;
    @Mock IActivoService activoService;
    @Mock IResposableDao responsableDao;
    @InjectMocks CatalogoRestController controller;

    @Test
    void tarjetaTraeUbicacionYCuentaTodosLosBienesAsignados() throws Exception {
        Predio predio = new Predio(); predio.setIdPredio(12L); predio.setDescrip("Edificio central");
        Oficina oficina = new Oficina(); oficina.setIdOficina(34L); oficina.setCodOfi((short) 7); oficina.setPredio(predio);
        Responsable responsable = new Responsable(); responsable.setEstado("ACTIVO"); responsable.setOficina(oficina);
        when(responsableService.findById(8L)).thenReturn(responsable);
        when(activoService.conteoPorGrupoContableDeResponsable(8L)).thenReturn(List.of());
        when(responsableDao.contarActivosAsignados(8L)).thenReturn(1L);

        ResponsableCardDataDTO tarjeta = controller.obtenerCardResponsable(8L).getBody();

        assertNotNull(tarjeta);
        assertEquals(34L, tarjeta.getIdOficina());
        assertEquals(12L, tarjeta.getIdPredio());
        assertEquals("ACTIVO", tarjeta.getEstado());
        assertEquals(1L, tarjeta.getTotalActivos());
        assertEquals("8", Encriptar.decrypt(tarjeta.getIdEnc()));
    }
}
