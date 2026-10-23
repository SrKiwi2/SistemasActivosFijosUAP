package com.usic.SistemasActivosFijosUAP;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.annotation.Transactional;

import com.usic.SistemasActivosFijosUAP.controller.activo.ActivosController;
import com.usic.SistemasActivosFijosUAP.componet.SseEmitterRegistry;
import com.usic.SistemasActivosFijosUAP.model.IService.IActivoService;
import com.usic.SistemasActivosFijosUAP.model.IService.IEstadoActivoService;
import com.usic.SistemasActivosFijosUAP.model.IService.IGrupoContableService;
import com.usic.SistemasActivosFijosUAP.model.IService.IOficinaService;
import com.usic.SistemasActivosFijosUAP.model.IService.IPredioServicio;
import com.usic.SistemasActivosFijosUAP.model.IService.IResponsableService;
import com.usic.SistemasActivosFijosUAP.model.dto.DetalleActivoDTO;
import com.usic.SistemasActivosFijosUAP.model.dto.DetalleRegistroItem;
import com.usic.SistemasActivosFijosUAP.model.dto.RegistroMasivoRequest;
import com.usic.SistemasActivosFijosUAP.model.entity.Activo;
import com.usic.SistemasActivosFijosUAP.model.entity.EstadoActivo;
import com.usic.SistemasActivosFijosUAP.model.entity.GrupoContable;
import com.usic.SistemasActivosFijosUAP.model.entity.Municipio;
import com.usic.SistemasActivosFijosUAP.model.entity.Oficina;
import com.usic.SistemasActivosFijosUAP.model.entity.Predio;
import com.usic.SistemasActivosFijosUAP.model.entity.Responsable;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;
import com.usic.SistemasActivosFijosUAP.model.repository.FuncionesActivoRepo;
import com.usic.SistemasActivosFijosUAP.model.service.ReservaCorrelativoService;

@ExtendWith(MockitoExtension.class)
class RegistroMasivoCorrelativoTest {
    @Mock IActivoService activoService;
    @Mock IEstadoActivoService estadoActivoService;
    @Mock IGrupoContableService grupoContableService;
    @Mock IOficinaService oficinaService;
    @Mock IPredioServicio predioServicio;
    @Mock IResponsableService responsableService;
    @Mock ReservaCorrelativoService reservaCorrelativoService;
    @Mock FuncionesActivoRepo funciones;
    @Mock SseEmitterRegistry sseRegistry;
    @InjectMocks ActivosController controller;

    @Test
    void dosLotesDelMismoGrupoCompartenSerieYSeBloqueaAntesDeLeerla() throws NoSuchMethodException {
        Municipio municipio = new Municipio(); municipio.setCodigo("01");
        Predio predio = new Predio(); predio.setCodigo("04"); predio.setMunicipio(municipio);
        Oficina oficina = new Oficina(); oficina.setPredio(predio);
        GrupoContable grupo = new GrupoContable(); grupo.setCodDbf(2);
        Responsable responsable = new Responsable();
        EstadoActivo estado = new EstadoActivo();
        when(oficinaService.findById(7L)).thenReturn(oficina);
        when(predioServicio.findById(4L)).thenReturn(predio);
        when(grupoContableService.findById(2L)).thenReturn(grupo);
        when(responsableService.findById(3L)).thenReturn(responsable);
        when(estadoActivoService.findById(1L)).thenReturn(estado);
        when(reservaCorrelativoService.siguienteSinReserva(ReservaCorrelativoService.REGISTRO, "01-04-02")).thenReturn(31);
        AtomicLong ids = new AtomicLong();
        when(activoService.save(any(Activo.class))).thenAnswer(call -> {
            Activo activo = call.getArgument(0);
            activo.setIdActivo(ids.incrementAndGet());
            return activo;
        });

        RegistroMasivoRequest solicitud = new RegistroMasivoRequest();
        solicitud.setItems(List.of(lote("Dispensador"), lote("Dispensador de mesa")));
        Usuario usuario = new Usuario(); usuario.setUsuario("operador");
        MockHttpServletRequest http = new MockHttpServletRequest();
        http.getSession().setAttribute("usuario", usuario);

        var respuesta = controller.registrarMasivo(solicitud, http);
        assertEquals(200, respuesta.getStatusCode().value());
        @SuppressWarnings("unchecked")
        var cuerpo = (java.util.Map<String, Object>) respuesta.getBody();
        assertEquals(List.of("01-04-02-00031", "01-04-02-00032"), cuerpo.get("codigos"));
        ArgumentCaptor<Activo> guardados = ArgumentCaptor.forClass(Activo.class);
        verify(activoService, Mockito.times(2)).save(guardados.capture());
        assertEquals(List.of("01-04-02-00031", "01-04-02-00032"),
                guardados.getAllValues().stream().map(Activo::getCodigo).toList());
        var orden = inOrder(funciones, reservaCorrelativoService, activoService);
        orden.verify(funciones).tomarTurnoRegistro();
        orden.verify(reservaCorrelativoService).siguienteSinReserva(ReservaCorrelativoService.REGISTRO, "01-04-02");
        orden.verify(activoService, Mockito.times(2)).save(any(Activo.class));
        verify(reservaCorrelativoService, Mockito.times(1)).siguienteSinReserva(ReservaCorrelativoService.REGISTRO, "01-04-02");
        assertNotNull(ActivosController.class.getMethod("registrarMasivo", RegistroMasivoRequest.class,
                jakarta.servlet.http.HttpServletRequest.class).getAnnotation(Transactional.class));
    }

    private static DetalleRegistroItem lote(String descripcion) {
        DetalleActivoDTO detalle = new DetalleActivoDTO(); detalle.setDescripcion(descripcion);
        DetalleRegistroItem item = new DetalleRegistroItem();
        item.setIdOficina(7L);
        item.setIdPredio(4L);
        item.setIdGrupoContable(2L);
        item.setIdResponsable(3L);
        item.setCantidad(1);
        item.setDescripcion(descripcion);
        item.setDetalles(List.of(detalle));
        return item;
    }
}
