package com.usic.SistemasActivosFijosUAP;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.usic.SistemasActivosFijosUAP.componet.SseEmitterRegistry;
import com.usic.SistemasActivosFijosUAP.model.dao.IActividadSistemaDao;
import com.usic.SistemasActivosFijosUAP.model.dao.IUsuarioDao;
import com.usic.SistemasActivosFijosUAP.model.entity.ActividadSistema;
import com.usic.SistemasActivosFijosUAP.model.entity.Rol;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;
import com.usic.SistemasActivosFijosUAP.model.service.supervision.ActividadService;

class ActividadServiceTest {

    @Test
    void registraActividadDeUsuarioDesacopladoConRolCargadoEnNuevaTransaccion() {
        IActividadSistemaDao actividades = mock(IActividadSistemaDao.class);
        IUsuarioDao usuarios = mock(IUsuarioDao.class);
        SseEmitterRegistry sse = mock(SseEmitterRegistry.class);
        Usuario autorDesacoplado = new Usuario();
        autorDesacoplado.setIdUsuario(7L);
        autorDesacoplado.setUsuario("operador");
        Usuario autorActual = new Usuario();
        autorActual.setIdUsuario(7L);
        Rol rol = new Rol();
        rol.setNombre("ADMINISTRADOR");
        autorActual.setRol(rol);
        when(usuarios.findById(7L)).thenReturn(Optional.of(autorActual));
        when(sse.getUsuariosConectadosEnRol(any())).thenReturn(Set.of());

        new ActividadService(actividades, usuarios, sse).registrar(autorDesacoplado,
                ActividadService.MOD_TRANSFERENCIA, ActividadService.ACC_MOVIMIENTO,
                "POST-42", "Envío a custodia", 1, null);

        ArgumentCaptor<ActividadSistema> registro = ArgumentCaptor.forClass(ActividadSistema.class);
        verify(actividades).save(registro.capture());
        assertEquals("ADMINISTRADOR", registro.getValue().getRol());
        assertEquals("operador", registro.getValue().getUsuario());
    }
}
