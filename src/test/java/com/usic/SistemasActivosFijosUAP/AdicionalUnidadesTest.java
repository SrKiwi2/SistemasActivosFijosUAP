package com.usic.SistemasActivosFijosUAP;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.usic.SistemasActivosFijosUAP.model.IService.*;
import com.usic.SistemasActivosFijosUAP.model.dao.*;
import com.usic.SistemasActivosFijosUAP.model.dto.adicional.AdicionalRequest;
import com.usic.SistemasActivosFijosUAP.model.entity.*;
import com.usic.SistemasActivosFijosUAP.model.service.ArchivoStorageService;
import com.usic.SistemasActivosFijosUAP.model.service.adicional.AdicionalService;
import com.usic.SistemasActivosFijosUAP.model.service.documento.WordEtiquetasQrService;

@ExtendWith(MockitoExtension.class)
class AdicionalUnidadesTest {
    @Mock IActivoAdicionalDao adicionalDao;
    @Mock IActivoFotoDao fotoDao;
    @Mock IActivoDao activoDao;
    @Mock IHistorialActivoDao historialDao;
    @Mock IOficinaService oficinaService;
    @Mock IResponsableService responsableService;
    @Mock IPredioServicio predioService;
    @Mock IGrupoContableService grupoService;
    @Mock IAuxiliarAdicionalDao auxiliarAdicionalDao;
    @Mock IOrganismoFinancieroService financiadorService;
    @Mock IEstadoActivoService estadoActivoService;
    @Mock ArchivoStorageService archivos;
    @Mock WordEtiquetasQrService hojaEtiquetas;
    @Mock PlatformTransactionManager txManager;
    @InjectMocks AdicionalService servicio;

    @Test
    void cadaUnidadConservaSuEstadoYAyudanteHistoricoSeparado() {
        GrupoContable grupo = new GrupoContable(); grupo.setIdGrupoContable(9L);
        AuxiliarAdicional auxiliar = new AuxiliarAdicional();
        auxiliar.setIdAuxiliarAdicional(4L); auxiliar.setGrupoContable(grupo); auxiliar.setEstado("ACTIVO");
        EstadoActivo bueno = new EstadoActivo(); bueno.setIdEstadoActivo(1L); bueno.setNombre("BUENO");
        EstadoActivo regular = new EstadoActivo(); regular.setIdEstadoActivo(2L); regular.setNombre("REGULAR");
        when(grupoService.findById(9L)).thenReturn(grupo);
        when(auxiliarAdicionalDao.findById(4L)).thenReturn(Optional.of(auxiliar));
        when(estadoActivoService.findById(1L)).thenReturn(bueno);
        when(estadoActivoService.findById(2L)).thenReturn(regular);
        AdicionalRequest r = new AdicionalRequest();
        r.setDescripcion("Silla"); r.setCantidad(3); r.setIdGrupoContable(9L);
        r.setIdAuxiliarAdicional(4L); r.setCodigosAntiguos(List.of("A1", "A2", "A3"));
        r.setEstadosPorUnidad(List.of(1L, 2L, 1L));
        Usuario usuario = new Usuario(); usuario.setEstado("ACTIVO"); usuario.setUsuario("operador");
        TransactionSynchronizationManager.initSynchronization();
        try {
            servicio.registrar(r, usuario, Set.of(AdicionalService.PERMISO));
        } finally { TransactionSynchronizationManager.clearSynchronization(); }
        ArgumentCaptor<Activo> activos = ArgumentCaptor.forClass(Activo.class);
        verify(activoDao, times(3)).save(activos.capture());
        assertEquals(List.of(bueno, regular, bueno), activos.getAllValues().stream().map(Activo::getEstadoActivo).toList());
        assertTrue(activos.getAllValues().stream().allMatch(a -> a.getAuxiliar() == null));
        ArgumentCaptor<ActivoAdicional> detalles = ArgumentCaptor.forClass(ActivoAdicional.class);
        verify(adicionalDao, times(3)).save(detalles.capture());
        assertTrue(detalles.getAllValues().stream().allMatch(x -> x.getAuxiliarAdicional() == auxiliar));
    }
}
