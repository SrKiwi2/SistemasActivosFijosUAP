package com.usic.SistemasActivosFijosUAP;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.usic.SistemasActivosFijosUAP.componet.SseEmitterRegistry;
import com.usic.SistemasActivosFijosUAP.model.dao.IParametroSistemaDao;
import com.usic.SistemasActivosFijosUAP.model.dao.ISolicitudAutorizacionDao;
import com.usic.SistemasActivosFijosUAP.model.dao.IUsuarioDao;
import com.usic.SistemasActivosFijosUAP.model.entity.ParametroSistema;
import com.usic.SistemasActivosFijosUAP.model.entity.Rol;
import com.usic.SistemasActivosFijosUAP.model.entity.SolicitudAutorizacion;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;
import com.usic.SistemasActivosFijosUAP.model.service.supervision.ActividadService;
import com.usic.SistemasActivosFijosUAP.model.service.supervision.AutorizacionService;
import com.usic.SistemasActivosFijosUAP.model.service.supervision.EjecutorAutorizacion;

/**
 * Circuito de autorizaciones: nada se aplica al pedirlo; solo el ADMINISTRADOR o el
 * SUPER USUARIO designado resuelven; al aprobar se ejecuta con los datos guardados.
 */
class AutorizacionServiceTest {

    private ISolicitudAutorizacionDao dao;
    private IParametroSistemaDao parametroDao;
    private IUsuarioDao usuarioDao;
    private EjecutorAutorizacion ejecutor;
    private AutorizacionService servicio;

    private Usuario apoyo, superDesignado, otroSuper, admin;

    @BeforeEach
    void preparar() {
        dao = mock(ISolicitudAutorizacionDao.class);
        parametroDao = mock(IParametroSistemaDao.class);
        usuarioDao = mock(IUsuarioDao.class);
        ejecutor = mock(EjecutorAutorizacion.class);
        when(ejecutor.tipos()).thenReturn(Set.of("OFICINA_ELIMINAR"));
        when(dao.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        when(dao.save(any())).thenAnswer(i -> i.getArgument(0));

        servicio = new AutorizacionService(dao, parametroDao, usuarioDao, mock(SseEmitterRegistry.class),
                mock(ActividadService.class), List.of(ejecutor));

        apoyo = usuario(1L, "apoyo1", "APOYO");
        superDesignado = usuario(2L, "super1", "SUPER USUARIO");
        otroSuper = usuario(3L, "super2", "SUPER USUARIO");
        admin = usuario(4L, "admin", "ADMINISTRADOR");

        ParametroSistema p = new ParametroSistema();
        p.setClave(AutorizacionService.CLAVE_REVISOR);
        p.setValor("2");
        when(parametroDao.findById(AutorizacionService.CLAVE_REVISOR)).thenReturn(Optional.of(p));
        when(usuarioDao.findById(1L)).thenReturn(Optional.of(apoyo));
    }

    private Usuario usuario(Long id, String nombre, String rol) {
        Rol r = new Rol();
        r.setNombre(rol);
        Usuario u = new Usuario();
        u.setIdUsuario(id);
        u.setUsuario(nombre);
        u.setRol(r);
        return u;
    }

    private SolicitudAutorizacion pendiente() {
        SolicitudAutorizacion s = new SolicitudAutorizacion();
        s.setIdSolicitud(9L);
        s.setTipo("OFICINA_ELIMINAR");
        s.setModulo("OFICINA");
        s.setIdRegistro(5L);
        s.setIdSolicitante(1L);
        s.setSolicitante("apoyo1");
        s.setDatosJson("{\"idOficina\":5}");
        when(dao.findById(9L)).thenReturn(Optional.of(s));
        return s;
    }

    @Test
    void soloRevisanElAdministradorYElSuperUsuarioDesignado() {
        assertTrue(servicio.puedeRevisar(admin));
        assertTrue(servicio.puedeRevisar(superDesignado));
        assertFalse(servicio.puedeRevisar(otroSuper));
        assertFalse(servicio.puedeRevisar(apoyo));
    }

    @Test
    void pedirNoAplicaNadaYExigeMotivo() {
        assertThrows(IllegalArgumentException.class, () -> servicio.solicitar("OFICINA_ELIMINAR", "OFICINA", 5L,
                "CAUN-5", "Eliminar", Map.of("idOficina", 5), " ", apoyo));
        servicio.solicitar("OFICINA_ELIMINAR", "OFICINA", 5L, "CAUN-5", "Eliminar", Map.of("idOficina", 5),
                "Ya no existe", apoyo);
        verify(dao).save(any(SolicitudAutorizacion.class));
        verify(ejecutor, never()).ejecutar(anyString(), anyMap(), any());
    }

    @Test
    void noSeDuplicaUnaSolicitudPendiente() {
        when(dao.existsByModuloAndIdRegistroAndEstado("OFICINA", 5L, SolicitudAutorizacion.PENDIENTE)).thenReturn(true);
        assertThrows(IllegalStateException.class, () -> servicio.solicitar("OFICINA_ELIMINAR", "OFICINA", 5L,
                "CAUN-5", "Eliminar", Map.of("idOficina", 5), "motivo", apoyo));
    }

    @Test
    void aprobarEjecutaConElSolicitanteComoAutor() {
        pendiente();
        when(ejecutor.ejecutar(eq("OFICINA_ELIMINAR"), anyMap(), eq(apoyo))).thenReturn("Oficina eliminada del SCIAF.");
        SolicitudAutorizacion s = servicio.aprobar(9L, "ok", superDesignado);
        assertEquals(SolicitudAutorizacion.APROBADA, s.getEstado());
        assertEquals("super1", s.getRevisor());
        assertTrue(s.getRespuesta().contains("Oficina eliminada"));
    }

    @Test
    void siYaNoSePuedeAplicarQuedaFallida() {
        pendiente();
        when(ejecutor.ejecutar(anyString(), anyMap(), any()))
                .thenThrow(new IllegalArgumentException("No se puede eliminar: tiene 2 responsable(s)"));
        SolicitudAutorizacion s = servicio.aprobar(9L, null, admin);
        assertEquals(SolicitudAutorizacion.FALLIDA, s.getEstado());
        assertTrue(s.getRespuesta().contains("2 responsable"));
    }

    @Test
    void unSuperUsuarioNoDesignadoNoResuelve() {
        pendiente();
        assertThrows(IllegalStateException.class, () -> servicio.aprobar(9L, null, otroSuper));
        assertThrows(IllegalStateException.class, () -> servicio.rechazar(9L, "no", apoyo));
    }

    @Test
    void rechazarExigeMotivoYNoEjecuta() {
        pendiente();
        assertThrows(IllegalArgumentException.class, () -> servicio.rechazar(9L, "", superDesignado));
        SolicitudAutorizacion s = servicio.rechazar(9L, "No corresponde", superDesignado);
        assertEquals(SolicitudAutorizacion.RECHAZADA, s.getEstado());
        verify(ejecutor, never()).ejecutar(anyString(), anyMap(), any());
    }

    @Test
    void soloElAdministradorDesignaYSoloASuperUsuario() {
        when(usuarioDao.findById(3L)).thenReturn(Optional.of(otroSuper));
        assertThrows(IllegalStateException.class, () -> servicio.designarRevisor(3L, superDesignado));
        assertThrows(IllegalArgumentException.class, () -> servicio.designarRevisor(1L, admin));
        servicio.designarRevisor(3L, admin);
        verify(parametroDao).save(any(ParametroSistema.class));
    }
}
