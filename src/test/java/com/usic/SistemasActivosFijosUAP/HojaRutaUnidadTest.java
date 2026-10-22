package com.usic.SistemasActivosFijosUAP;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import com.usic.SistemasActivosFijosUAP.controller.recepcion.HojaRutaController;
import com.usic.SistemasActivosFijosUAP.model.IService.IHojaRutaService;
import com.usic.SistemasActivosFijosUAP.model.IService.IMovimientoService;
import com.usic.SistemasActivosFijosUAP.model.IService.ISolictanteService;
import com.usic.SistemasActivosFijosUAP.model.IService.IUnidadService;
import com.usic.SistemasActivosFijosUAP.model.entity.Rol;
import com.usic.SistemasActivosFijosUAP.model.entity.Unidad;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;

class HojaRutaUnidadTest {
    @Test
    void registraUnidadEnMayusculasYDevuelveIdSeleccionable() {
        var unidades = mock(IUnidadService.class);
        when(unidades.findAll()).thenReturn(List.of());
        when(unidades.save(any(Unidad.class))).thenAnswer(inv -> {
            Unidad u = inv.getArgument(0);
            u.setIdUnidad(17L);
            return u;
        });
        var controller = controller(unidades);
        var r = controller.registrarUnidad(recepcion(), "  dirección administrativa  ");
        assertTrue((Boolean) r.getBody().get("ok"));
        assertEquals(17L, r.getBody().get("idUnidad"));
        assertEquals("DIRECCIÓN ADMINISTRATIVA", r.getBody().get("nombre"));
    }

    @Test
    void reutilizaUnidadExistenteSinDuplicarla() {
        var unidades = mock(IUnidadService.class);
        Unidad existente = new Unidad();
        existente.setIdUnidad(4L);
        existente.setNombre("Dirección Administrativa");
        when(unidades.findAll()).thenReturn(List.of(existente));
        var controller = controller(unidades);
        var r = controller.registrarUnidad(recepcion(), "DIRECCIÓN ADMINISTRATIVA");
        assertEquals(4L, r.getBody().get("idUnidad"));
        assertEquals("Dirección Administrativa", r.getBody().get("nombre"));
        org.mockito.Mockito.verify(unidades, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void rechazaAltaSinPermiso() {
        var unidades = mock(IUnidadService.class);
        var r = controller(unidades).registrarUnidad(new MockHttpServletRequest(), "NUEVA");
        assertFalse((Boolean) r.getBody().get("ok"));
        assertEquals(403, r.getStatusCode().value());
    }

    private static HojaRutaController controller(IUnidadService unidades) {
        return new HojaRutaController(mock(IHojaRutaService.class), mock(IMovimientoService.class),
                mock(ISolictanteService.class), unidades);
    }

    private static MockHttpServletRequest recepcion() {
        var request = new MockHttpServletRequest();
        Rol rol = new Rol();
        rol.setNombre("RECEPCION");
        Usuario usuario = new Usuario();
        usuario.setIdUsuario(1L);
        usuario.setRol(rol);
        request.getSession().setAttribute("usuario", usuario);
        return request;
    }
}
