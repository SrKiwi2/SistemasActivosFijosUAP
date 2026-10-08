package com.usic.SistemasActivosFijosUAP;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import com.usic.SistemasActivosFijosUAP.controller.activo.ActivosController;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;

class SubidaPendientesVsiafPermisoTest {

    private final ActivosController controller = newController();

    @Test
    void requierePermisoIndividualYAccesoAPendientes() {
        Set<String> habilitados = Set.of("opcion_activop", "opcion_activop_subir_vsiaf");
        assertTrue(puedeSubir("usuario-prueba", "ACTIVO", habilitados));
        assertFalse(puedeSubir("saul", "ACTIVO", Set.of("opcion_activop")));
        assertFalse(puedeSubir("vero", "ACTIVO", Set.of("opcion_activop")));
        assertFalse(puedeSubir("admin2", "ACTIVO", Set.of("opcion_activop")));
        assertFalse(puedeSubir("usuario-prueba", "INACTIVO", habilitados));
        assertFalse(puedeSubir("usuario-prueba", "ACTIVO", Set.of("opcion_activop_subir_vsiaf")));
    }

    @Test
    void subidaIndividualYMasivaRechazanAUnUsuarioNoAutorizadoAntesDeProcesarActivos() {
        MockHttpServletRequest request = request("cesar", "ACTIVO", Set.of("opcion_activo", "opcion_activop"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        Map<String, Object> individual = controller.aprobarActivo("id-irrelevante", request, response);
        assertEquals(403, response.getStatus());
        assertEquals(false, individual.get("ok"));

        var masiva = controller.aprobarMasivo(List.of("id-irrelevante"), request);
        assertEquals(403, masiva.getStatusCode().value());
    }

    private boolean puedeSubir(String nombre, String estado, Set<String> opciones) {
        return Boolean.TRUE.equals(ReflectionTestUtils.invokeMethod(controller,
                "puedeSubirPendientesAlVsiaf", request(nombre, estado, opciones)));
    }

    private MockHttpServletRequest request(String nombre, String estado, Set<String> opciones) {
        Usuario usuario = new Usuario();
        usuario.setUsuario(nombre);
        usuario.setEstado(estado);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession().setAttribute("usuario", usuario);
        request.getSession().setAttribute("opciones", opciones);
        return request;
    }

    private static ActivosController newController() {
        try {
            Constructor<?> constructor = ActivosController.class.getDeclaredConstructors()[0];
            constructor.setAccessible(true);
            return (ActivosController) constructor.newInstance(new Object[constructor.getParameterCount()]);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
