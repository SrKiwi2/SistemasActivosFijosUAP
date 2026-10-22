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
import com.usic.SistemasActivosFijosUAP.model.dto.adicional.AdicionalLotesRequest;
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
        assertEquals(List.of("A1", "A2", "A3"), activos.getAllValues().stream().map(Activo::getCodigoAntiguo).toList());
    }

    @Test
    void noAceptaAuxiliaresDelCatalogoGeneral() {
        AdicionalRequest r = new AdicionalRequest();
        r.setDescripcion("Silla");
        r.setIdAuxiliar(8L);
        Usuario usuario = new Usuario(); usuario.setEstado("ACTIVO");
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> servicio.registrar(r, usuario, Set.of(AdicionalService.PERMISO)));
        assertTrue(error.getMessage().contains("auxiliares propios"));
        verifyNoInteractions(activoDao, adicionalDao);
    }

    @Test
    void exigeEstadoParaCadaUnidadSinAsignarUnoPorDefecto() {
        AdicionalRequest r = new AdicionalRequest();
        r.setDescripcion("Dispensador"); r.setCantidad(2);
        r.setEstadosPorUnidad(java.util.Arrays.asList(1L, null));
        EstadoActivo bueno = new EstadoActivo(); bueno.setIdEstadoActivo(1L);
        when(estadoActivoService.findById(1L)).thenReturn(bueno);
        Usuario usuario = new Usuario(); usuario.setEstado("ACTIVO");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> servicio.registrar(r, usuario, Set.of(AdicionalService.PERMISO)));
        assertTrue(error.getMessage().contains("unidad 2"));
        verifyNoInteractions(activoDao);
    }

    @Test
    void correlativoAvanzaEntreLotesDelMismoPredioYGrupo() {
        Municipio municipio = new Municipio(); municipio.setCodigo("01");
        Predio predio = new Predio(); predio.setIdPredio(7L); predio.setCodigo("04"); predio.setMunicipio(municipio);
        GrupoContable grupo = new GrupoContable(); grupo.setIdGrupoContable(2L); grupo.setCodDbf(2);
        EstadoActivo bueno = new EstadoActivo(); bueno.setIdEstadoActivo(1L);
        when(predioService.findById(7L)).thenReturn(predio);
        when(grupoService.findById(2L)).thenReturn(grupo);
        when(estadoActivoService.findById(1L)).thenReturn(bueno);
        when(adicionalDao.maxCorrelativoAdicional("01-04-02")).thenReturn(0);
        AdicionalRequest primero = new AdicionalRequest();
        primero.setDescripcion("Dispensador"); primero.setCantidad(1); primero.setIdPredio(7L);
        primero.setIdGrupoContable(2L); primero.setIdEstadoActivo(1L);
        AdicionalRequest segundo = new AdicionalRequest();
        segundo.setDescripcion("Dispensador de mesa"); segundo.setCantidad(2); segundo.setIdPredio(7L);
        segundo.setIdGrupoContable(2L); segundo.setIdEstadoActivo(1L);
        AdicionalLotesRequest lotes = new AdicionalLotesRequest();
        lotes.setToken("prueba-correlativo"); lotes.setLotes(List.of(primero, segundo));
        Usuario usuario = new Usuario(); usuario.setEstado("ACTIVO"); usuario.setUsuario("operador");
        TransactionSynchronizationManager.initSynchronization();
        try {
            servicio.registrarLotes(lotes, usuario, Set.of(AdicionalService.PERMISO));
        } finally { TransactionSynchronizationManager.clearSynchronization(); }

        ArgumentCaptor<Activo> activos = ArgumentCaptor.forClass(Activo.class);
        verify(activoDao, times(3)).save(activos.capture());
        assertEquals(List.of("01-04-02-00001A", "01-04-02-00002A", "01-04-02-00003A"),
                activos.getAllValues().stream().map(Activo::getCodigo).toList());
        verify(adicionalDao, times(1)).maxCorrelativoAdicional("01-04-02");
    }

    @Test
    void permiteCorregirElGrupoDeUnAuxiliarSinBienes() {
        GrupoContable anterior = new GrupoContable(); anterior.setIdGrupoContable(2L);
        GrupoContable correcto = new GrupoContable(); correcto.setIdGrupoContable(5L);
        AuxiliarAdicional auxiliar = new AuxiliarAdicional();
        auxiliar.setIdAuxiliarAdicional(4L); auxiliar.setGrupoContable(anterior);
        auxiliar.setNombre("DISPENSADOR/BEBEDERO"); auxiliar.setEstado("ACTIVO");
        when(auxiliarAdicionalDao.findById(4L)).thenReturn(Optional.of(auxiliar));
        when(grupoService.findById(5L)).thenReturn(correcto);

        servicio.actualizarAuxiliar(4L, 5L, "Dispensador/Bebedero", null, administrador());

        assertSame(correcto, auxiliar.getGrupoContable());
        verify(auxiliarAdicionalDao).saveAndFlush(auxiliar);
    }

    @Test
    void impideMoverOEliminarUnAuxiliarConBienesAsignados() {
        GrupoContable anterior = new GrupoContable(); anterior.setIdGrupoContable(2L);
        GrupoContable correcto = new GrupoContable(); correcto.setIdGrupoContable(5L);
        AuxiliarAdicional auxiliar = new AuxiliarAdicional();
        auxiliar.setIdAuxiliarAdicional(4L); auxiliar.setGrupoContable(anterior);
        auxiliar.setNombre("DISPENSADOR/BEBEDERO"); auxiliar.setEstado("ACTIVO");
        when(auxiliarAdicionalDao.findById(4L)).thenReturn(Optional.of(auxiliar));
        when(grupoService.findById(5L)).thenReturn(correcto);
        when(adicionalDao.contarPorAuxiliarAdicional(4L)).thenReturn(1L);

        assertThrows(IllegalArgumentException.class,
                () -> servicio.actualizarAuxiliar(4L, 5L, "DISPENSADOR/BEBEDERO", null, administrador()));
        assertThrows(IllegalArgumentException.class, () -> servicio.eliminarAuxiliar(4L, administrador()));
        assertSame(anterior, auxiliar.getGrupoContable());
        verify(auxiliarAdicionalDao, never()).delete(any());
    }

    @Test
    void eliminaUnAuxiliarSinBienesYExigeAdministrador() {
        AuxiliarAdicional auxiliar = new AuxiliarAdicional(); auxiliar.setEstado("ACTIVO");
        when(auxiliarAdicionalDao.findById(4L)).thenReturn(Optional.of(auxiliar));
        Usuario operador = new Usuario(); operador.setEstado("ACTIVO");
        assertThrows(SecurityException.class, () -> servicio.eliminarAuxiliar(4L, operador));

        servicio.eliminarAuxiliar(4L, administrador());
        verify(auxiliarAdicionalDao).delete(auxiliar);
        verify(auxiliarAdicionalDao).flush();
    }

    private static Usuario administrador() {
        Rol rol = new Rol(); rol.setNombre("ADMINISTRADOR");
        Usuario usuario = new Usuario(); usuario.setEstado("ACTIVO"); usuario.setRol(rol);
        return usuario;
    }
}
