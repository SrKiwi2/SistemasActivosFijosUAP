package com.usic.SistemasActivosFijosUAP;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.usic.SistemasActivosFijosUAP.interoperabilidad.registroDbf.ActualDbfWriterService;
import com.usic.SistemasActivosFijosUAP.model.IService.IActivoService;
import com.usic.SistemasActivosFijosUAP.model.IService.IConfiguracionGestionService;
import com.usic.SistemasActivosFijosUAP.model.IService.IOficinaService;
import com.usic.SistemasActivosFijosUAP.model.IService.IResponsableService;
import com.usic.SistemasActivosFijosUAP.model.dao.IAsignacionActivoDao;
import com.usic.SistemasActivosFijosUAP.model.dao.IAsignacionMovimientoDao;
import com.usic.SistemasActivosFijosUAP.model.dao.IDetalleAsignacionDao;
import com.usic.SistemasActivosFijosUAP.model.dao.IHistorialActivoDao;
import com.usic.SistemasActivosFijosUAP.model.entity.Activo;
import com.usic.SistemasActivosFijosUAP.model.entity.AsignacionActivo;
import com.usic.SistemasActivosFijosUAP.model.entity.DetalleAsignacionActivo;
import com.usic.SistemasActivosFijosUAP.model.entity.Rol;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;
import com.usic.SistemasActivosFijosUAP.model.service.VsiafApoyoService;
import com.usic.SistemasActivosFijosUAP.model.service.asignacion.AsignacionEdicionService;
import com.usic.SistemasActivosFijosUAP.model.service.asignacion.TrasladoActaDTO;

/** Protege la selección si otro movimiento cambió de acta al bien antes de confirmar. */
class AsignacionMovimientoOrigenTest {

    @Test
    void rechazaTrasladoCuandoElBienYaNoEstaEnElActaAbierta() {
        IAsignacionActivoDao actas = mock(IAsignacionActivoDao.class);
        IDetalleAsignacionDao detalles = mock(IDetalleAsignacionDao.class);
        AsignacionEdicionService servicio = new AsignacionEdicionService(actas,
                mock(IAsignacionMovimientoDao.class), detalles, mock(IHistorialActivoDao.class),
                mock(IActivoService.class), mock(IResponsableService.class), mock(IOficinaService.class),
                mock(IConfiguracionGestionService.class), mock(ActualDbfWriterService.class),
                mock(VsiafApoyoService.class));

        Usuario usuario = new Usuario();
        Rol rol = new Rol();
        rol.setNombre("ADMINISTRADOR");
        usuario.setRol(rol);

        AsignacionActivo destino = new AsignacionActivo();
        destino.setIdAsignacionActivo(2L);
        AsignacionActivo otroOrigen = new AsignacionActivo();
        otroOrigen.setIdAsignacionActivo(3L);
        Activo activo = new Activo();
        activo.setIdActivo(10L);
        DetalleAsignacionActivo linea = new DetalleAsignacionActivo();
        linea.setActivo(activo);
        linea.setAsignacionActivo(otroOrigen);

        when(actas.findByIdConDetalles(2L)).thenReturn(Optional.of(destino));
        when(detalles.vigentesDeActivos(List.of(10L))).thenReturn(List.of(linea));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> servicio.trasladar(new TrasladoActaDTO(2L, List.of(10L), false,
                        "Cambio documentado", 1L), usuario));
        assertTrue(error.getMessage().contains("ya no pertenece"));
        verify(detalles, never()).save(any());
    }
}
