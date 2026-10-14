package com.usic.SistemasActivosFijosUAP;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import com.usic.SistemasActivosFijosUAP.controller.rest.ActivoConsultaApiController;
import com.usic.SistemasActivosFijosUAP.model.IService.IActivoService;
import com.usic.SistemasActivosFijosUAP.model.dto.ActivoFichaDTO;
import com.usic.SistemasActivosFijosUAP.model.entity.Activo;
import com.usic.SistemasActivosFijosUAP.model.entity.ConsultaApiActivo;
import com.usic.SistemasActivosFijosUAP.model.entity.GrupoContable;
import com.usic.SistemasActivosFijosUAP.model.entity.Oficina;
import com.usic.SistemasActivosFijosUAP.model.entity.Persona;
import com.usic.SistemasActivosFijosUAP.model.entity.Predio;
import com.usic.SistemasActivosFijosUAP.model.entity.Responsable;
import com.usic.SistemasActivosFijosUAP.model.service.seguridad.ConsultaApiActivoRegistroService;

class ActivoConsultaApiControllerTest {
    private final IActivoService activos = mock(IActivoService.class);
    private final ConsultaApiActivoRegistroService registro = mock(ConsultaApiActivoRegistroService.class);
    private final ActivoConsultaApiController controller =
            new ActivoConsultaApiController(activos, registro, List.of(15));
    private final MockHttpServletRequest request = new MockHttpServletRequest();

    private Activo activo(String codigo, int codContable) {
        GrupoContable gc = new GrupoContable();
        gc.setCodContable(codContable);
        Predio predio = new Predio();
        predio.setUnidad("RECTORADO");
        Oficina oficina = new Oficina();
        oficina.setNombre("CENTRO DE COMPUTO");
        oficina.setPredio(predio);
        Persona persona = new Persona();
        persona.setNombre("ANA");
        persona.setPaterno("ROJAS");
        Responsable responsable = new Responsable();
        responsable.setPersona(persona);
        Activo a = new Activo();
        a.setCodigo(codigo);
        a.setDescripcion("COMPUTADORA DE ESCRITORIO");
        a.setGrupoContable(gc);
        a.setOficina(oficina);
        a.setResponsable(responsable);
        return a;
    }

    @Test
    void equipoDeComputacionDevuelveLaFicha() {
        when(activos.fetchFullByCodigo("01-01-08-00583")).thenReturn(Optional.of(activo("01-01-08-00583", 15)));

        ResponseEntity<?> r = controller.obtenerFichaPorCodigo(" 01-01-08-00583 ", request);

        assertEquals(200, r.getStatusCode().value());
        ActivoFichaDTO ficha = assertInstanceOf(ActivoFichaDTO.class, r.getBody());
        assertEquals("ANA ROJAS", ficha.responsable());
        assertEquals("CENTRO DE COMPUTO", ficha.oficina());
        assertEquals("RECTORADO", ficha.unidad());
        verify(registro).registrar(request, "01-01-08-00583", ConsultaApiActivo.ENCONTRADO);
    }

    @Test
    void otroGrupoContableNoDevuelveDatos() {
        when(activos.fetchFullByCodigo("01-01-02-00010")).thenReturn(Optional.of(activo("01-01-02-00010", 2)));

        ResponseEntity<?> r = controller.obtenerFichaPorCodigo("01-01-02-00010", request);

        assertEquals(422, r.getStatusCode().value());
        assertInstanceOf(java.util.Map.class, r.getBody());
        verify(registro).registrar(request, "01-01-02-00010", ConsultaApiActivo.GRUPO_NO_PERMITIDO);
    }

    @Test
    void activoSinGrupoContableNoDevuelveDatos() {
        Activo a = activo("01-01-08-00001", 15);
        a.setGrupoContable(null);
        when(activos.fetchFullByCodigo("01-01-08-00001")).thenReturn(Optional.of(a));

        assertEquals(422, controller.obtenerFichaPorCodigo("01-01-08-00001", request).getStatusCode().value());
    }

    @Test
    void codigoInexistenteDa404() {
        when(activos.fetchFullByCodigo("99-99-99-99999")).thenReturn(Optional.empty());

        assertEquals(404, controller.obtenerFichaPorCodigo("99-99-99-99999", request).getStatusCode().value());
        verify(registro).registrar(request, "99-99-99-99999", ConsultaApiActivo.NO_ENCONTRADO);
    }

    @Test
    void errorDeConsultaSeRegistraYDa500() {
        when(activos.fetchFullByCodigo(any())).thenThrow(new IncorrectResultSizeDataAccessException(1, 2));

        assertEquals(500, controller.obtenerFichaPorCodigo("01-01-08-00583", request).getStatusCode().value());
        verify(registro).registrar(eq(request), eq("01-01-08-00583"), eq(ConsultaApiActivo.ERROR));
    }
}
