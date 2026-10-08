package com.usic.SistemasActivosFijosUAP;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;
import com.usic.SistemasActivosFijosUAP.model.service.seguridad.PermisosDatosActivos;

class PermisosDatosActivosTest {

    @Test
    void finanzasRequiereRegistroPendientesOCasillaIndividual() {
        assertFalse(PermisosDatosActivos.puedeVerFinanzas(peticion("ACTIVO", Set.of("opcion_ta"))));
        assertTrue(PermisosDatosActivos.puedeVerFinanzas(peticion("ACTIVO", Set.of("opcion_activo"))));
        assertTrue(PermisosDatosActivos.puedeVerFinanzas(peticion("ACTIVO", Set.of("opcion_activop"))));
        assertTrue(PermisosDatosActivos.puedeVerFinanzas(peticion("ACTIVO", Set.of("opcion_ta", PermisosDatosActivos.VER_FINANZAS))));
        assertFalse(PermisosDatosActivos.puedeVerFinanzas(peticion("INACTIVO", Set.of(PermisosDatosActivos.VER_FINANZAS))));
        assertFalse(PermisosDatosActivos.puedeVerFinanzas(new MockHttpServletRequest()));
    }

    private MockHttpServletRequest peticion(String estado, Set<String> opciones) {
        Usuario usuario = new Usuario();
        usuario.setEstado(estado);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession().setAttribute("usuario", usuario);
        request.getSession().setAttribute("opciones", opciones);
        return request;
    }
}
