package com.usic.SistemasActivosFijosUAP;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Optional;
import java.util.Set;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.InOrder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.usic.SistemasActivosFijosUAP.model.IService.*;
import com.usic.SistemasActivosFijosUAP.model.dao.*;
import com.usic.SistemasActivosFijosUAP.model.dto.adicional.AdicionalRequest;
import com.usic.SistemasActivosFijosUAP.model.dto.adicional.AdicionalLotesRequest;
import com.usic.SistemasActivosFijosUAP.model.entity.*;
import com.usic.SistemasActivosFijosUAP.model.service.ArchivoStorageService;
import com.usic.SistemasActivosFijosUAP.model.service.ClienteReserva;
import com.usic.SistemasActivosFijosUAP.model.service.adicional.AdicionalService;
import com.usic.SistemasActivosFijosUAP.model.service.documento.WordEtiquetasQrService;

@ExtendWith(MockitoExtension.class)
class AdicionalUnidadesTest {
    @Mock IActivoAdicionalDao adicionalDao;
    @Mock IReservaCodigoAdicionalDao reservaDao;
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
    @Mock IUsuarioDao usuarioDao;
    @InjectMocks AdicionalService servicio;

    @Test
    void ampliarYCancelarReubicaReservasSinPisarOtrosRangos() {
        Municipio municipio = new Municipio(); municipio.setCodigo("04");
        Predio predio = new Predio(); predio.setCodigo("15"); predio.setMunicipio(municipio);
        GrupoContable grupo = new GrupoContable(); grupo.setCodDbf(2);
        when(predioService.findById(7L)).thenReturn(predio);
        when(grupoService.findById(2L)).thenReturn(grupo);
        when(adicionalDao.maxCorrelativoAdicional("04-15-02")).thenReturn(7);
        Map<String, ReservaCodigoAdicional> reservas = new HashMap<>();
        ReservaCodigoAdicional abandonada = new ReservaCodigoAdicional();
        abandonada.setToken("saul-anterior"); abandonada.setPrefijo("04-15-02");
        abandonada.setDesde(8); abandonada.setCantidad(1); abandonada.setUsuario("saul");
        abandonada.setVence(LocalDateTime.now().minusMinutes(1));
        reservas.put(abandonada.getToken(), abandonada);
        when(reservaDao.eliminarVencidas(any(LocalDateTime.class))).thenAnswer(i -> {
            int antes = reservas.size();
            reservas.values().removeIf(r -> !r.getVence().isAfter(i.getArgument(0)));
            return antes - reservas.size();
        });
        when(reservaDao.findById(anyString())).thenAnswer(i -> Optional.ofNullable(reservas.get(i.getArgument(0))));
        when(reservaDao.save(any())).thenAnswer(i -> {
            ReservaCodigoAdicional r = i.getArgument(0); reservas.put(r.getToken(), r); return r;
        });
        when(reservaDao.findByPrefijoAndVenceAfterOrderByDesdeAsc(eq("04-15-02"), any(LocalDateTime.class)))
                .thenAnswer(i -> reservas.values().stream().filter(r -> r.getVence().isAfter(i.getArgument(1)))
                        .sorted(java.util.Comparator.comparingInt(ReservaCodigoAdicional::getDesde)).toList());
        doAnswer(i -> { ReservaCodigoAdicional r = i.getArgument(0); reservas.remove(r.getToken()); return null; })
                .when(reservaDao).delete(any());
        Usuario ana = new Usuario(); ana.setEstado("ACTIVO"); ana.setUsuario("ana");
        Usuario luis = new Usuario(); luis.setEstado("ACTIVO"); luis.setUsuario("luis");
        Map<String, Object> primera = servicio.reservarCodigo("ana-token", 7L, 2L, 1, ana, Set.of(AdicionalService.PERMISO));
        Map<String, Object> segunda = servicio.reservarCodigo("luis-token", 7L, 2L, 1, luis, Set.of(AdicionalService.PERMISO));
        assertEquals("04-15-02-00008A", primera.get("desde"));
        assertEquals("04-15-02-00009A", segunda.get("desde"));
        assertEquals(0, primera.get("reservasPrevias"));
        assertEquals(1, segunda.get("reservasPrevias"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> previas = (List<Map<String, Object>>) segunda.get("previas");
        assertEquals(1, previas.size());
        assertEquals("04-15-02-00008A", previas.get(0).get("desde"));
        assertEquals("ana", previas.get(0).get("usuario"));
        assertEquals(false, previas.get(0).get("propia"));
        Map<String, Object> ampliada = servicio.reservarCodigo("luis-token", 7L, 2L, 2, luis, Set.of(AdicionalService.PERMISO));
        assertEquals("04-15-02-00009A", ampliada.get("desde"));
        assertEquals("04-15-02-00010A", ampliada.get("hasta"));
        servicio.reservarCodigo("luis-token", 7L, 2L, 1, luis, Set.of(AdicionalService.PERMISO));
        Map<String, Object> terceraReserva = servicio.reservarCodigo("ana-tercera", 7L, 2L, 1, ana, Set.of(AdicionalService.PERMISO));
        assertEquals("04-15-02-00010A", terceraReserva.get("desde"));
        Map<String, Object> desplazada = servicio.reservarCodigo("luis-token", 7L, 2L, 2, luis, Set.of(AdicionalService.PERMISO));
        assertEquals("04-15-02-00011A", desplazada.get("desde"));
        assertEquals("04-15-02-00012A", desplazada.get("hasta"));
        servicio.liberarCodigo("ana-token", ana, Set.of(AdicionalService.PERMISO));
        Map<String, Object> compactada = servicio.reservarCodigo("luis-token", 7L, 2L, 2, luis, Set.of(AdicionalService.PERMISO));
        assertEquals("04-15-02-00008A", compactada.get("desde"));
        assertEquals("04-15-02-00009A", compactada.get("hasta"));
        Map<String, Object> tercera = servicio.reservarCodigo("ana-otro", 7L, 2L, 1, ana, Set.of(AdicionalService.PERMISO));
        assertEquals("04-15-02-00011A", tercera.get("desde"));
        verify(adicionalDao, times(9)).turno(anyLong());
    }

    @Test
    void reservaOchoAbandonadaDejaPasarLaNueveAlPrimerCodigoLibre() {
        Municipio municipio = new Municipio(); municipio.setCodigo("04");
        Predio predio = new Predio(); predio.setCodigo("15"); predio.setMunicipio(municipio);
        GrupoContable grupo = new GrupoContable(); grupo.setCodDbf(2);
        when(predioService.findById(7L)).thenReturn(predio);
        when(grupoService.findById(2L)).thenReturn(grupo);
        when(adicionalDao.maxCorrelativoAdicional("04-15-02")).thenReturn(7);
        Map<String, ReservaCodigoAdicional> reservas = new HashMap<>();
        ReservaCodigoAdicional vieja = new ReservaCodigoAdicional();
        vieja.setToken("saul-vieja"); vieja.setPrefijo("04-15-02"); vieja.setDesde(8);
        vieja.setCantidad(1); vieja.setUsuario("saul"); vieja.setVence(LocalDateTime.now().minusMinutes(1));
        reservas.put(vieja.getToken(), vieja);
        ReservaCodigoAdicional siguiente = new ReservaCodigoAdicional();
        siguiente.setToken("admin2-token"); siguiente.setPrefijo("04-15-02"); siguiente.setDesde(9);
        siguiente.setCantidad(1); siguiente.setUsuario("admin2"); siguiente.setVence(LocalDateTime.now().plusMinutes(30));
        reservas.put(siguiente.getToken(), siguiente);
        when(reservaDao.eliminarVencidas(any(LocalDateTime.class))).thenAnswer(i -> {
            int antes = reservas.size();
            reservas.values().removeIf(r -> !r.getVence().isAfter(i.getArgument(0)));
            return antes - reservas.size();
        });
        when(reservaDao.findById(anyString())).thenAnswer(i -> Optional.ofNullable(reservas.get(i.getArgument(0))));
        when(reservaDao.findByPrefijoAndVenceAfterOrderByDesdeAsc(eq("04-15-02"), any(LocalDateTime.class)))
                .thenAnswer(i -> reservas.values().stream().filter(r -> r.getVence().isAfter(i.getArgument(1)))
                        .sorted(java.util.Comparator.comparingInt(ReservaCodigoAdicional::getDesde)).toList());
        when(reservaDao.save(any())).thenAnswer(i -> {
            ReservaCodigoAdicional r = i.getArgument(0); reservas.put(r.getToken(), r); return r;
        });
        Usuario admin2 = new Usuario(); admin2.setEstado("ACTIVO"); admin2.setUsuario("admin2");
        Map<String, Object> renovada = servicio.reservarCodigo("admin2-token", 7L, 2L, 1, admin2,
                Set.of(AdicionalService.PERMISO));
        assertEquals("04-15-02-00008A", renovada.get("desde"));
        assertEquals(0, renovada.get("reservasPrevias"));
        assertEquals(false, reservas.containsKey("saul-vieja"));
        Map<String, Object> nueva = servicio.reservarCodigo("admin2-nueva", 7L, 2L, 1, admin2,
                Set.of(AdicionalService.PERMISO));
        assertEquals("04-15-02-00009A", nueva.get("desde"));
    }

    @Test
    void guardaElCodigoReservadoSinRecalcularElMaximo() {
        Municipio municipio = new Municipio(); municipio.setCodigo("04");
        Predio predio = new Predio(); predio.setIdPredio(7L); predio.setCodigo("15"); predio.setMunicipio(municipio);
        GrupoContable grupo = new GrupoContable(); grupo.setCodDbf(2);
        EstadoActivo bueno = new EstadoActivo(); bueno.setIdEstadoActivo(1L);
        when(predioService.findById(7L)).thenReturn(predio);
        when(grupoService.findById(2L)).thenReturn(grupo);
        when(estadoActivoService.findById(1L)).thenReturn(bueno);
        ReservaCodigoAdicional reserva = new ReservaCodigoAdicional();
        reserva.setToken("primero"); reserva.setPrefijo("04-15-02"); reserva.setDesde(8);
        reserva.setCantidad(1); reserva.setUsuario("ana"); reserva.setVence(LocalDateTime.now().plusMinutes(25));
        when(reservaDao.findById("primero")).thenReturn(Optional.of(reserva));
        AdicionalRequest r = new AdicionalRequest();
        r.setToken("primero"); r.setCodigoReservado(true); r.setDescripcion("Silla"); r.setCantidad(1);
        r.setIdPredio(7L); r.setIdGrupoContable(2L); r.setIdEstadoActivo(1L);
        Usuario ana = new Usuario(); ana.setEstado("ACTIVO"); ana.setUsuario("ana");
        r.setCodigoPrevisto("04-15-02-00009A");
        assertThrows(IllegalStateException.class, () -> servicio.registrar(r, ana, Set.of(AdicionalService.PERMISO)));
        r.setCodigoPrevisto("04-15-02-00008A");
        TransactionSynchronizationManager.initSynchronization();
        try { servicio.registrar(r, ana, Set.of(AdicionalService.PERMISO)); }
        finally { TransactionSynchronizationManager.clearSynchronization(); }
        ArgumentCaptor<Activo> activo = ArgumentCaptor.forClass(Activo.class);
        verify(activoDao).save(activo.capture());
        assertEquals("04-15-02-00008A", activo.getValue().getCodigo());
        verify(reservaDao).delete(reserva);
    }

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
        r.setMaterial("madera"); r.setDimensiones("12*4*5m");
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
        // Formato pedido por Activos Fijos: «DE material» y «D:medidas».
        assertEquals("SILLA DE MADERA D:12*4*5M", activos.getAllValues().get(0).getDescripcion());
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
    void correlativoAvanzaEntreLotesDelMismoPredioYGrupo() throws NoSuchMethodException {
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
        verify(adicionalDao, times(2)).maxCorrelativoAdicional("01-04-02");
        InOrder orden = inOrder(adicionalDao);
        orden.verify(adicionalDao).turno(anyLong());
        orden.verify(adicionalDao).maxCorrelativoAdicional("01-04-02");
        assertNotNull(AdicionalService.class.getMethod("registrarLotes", AdicionalLotesRequest.class,
                Usuario.class, Object.class).getAnnotation(Transactional.class));
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

    @Test
    void trasRecargarLaPaginaAnteriorNoReservaYSeDescartaLoSuyo() {
        Municipio municipio = new Municipio(); municipio.setCodigo("04");
        Predio predio = new Predio(); predio.setCodigo("15"); predio.setMunicipio(municipio);
        GrupoContable grupo = new GrupoContable(); grupo.setCodDbf(2);
        when(predioService.findById(7L)).thenReturn(predio);
        when(grupoService.findById(2L)).thenReturn(grupo);
        Usuario saul = new Usuario(); saul.setEstado("ACTIVO"); saul.setUsuario("saul");
        Set<String> permiso = Set.of(AdicionalService.PERMISO);

        // La renovación tardía de la carga 1 llega cuando la carga 2 ya reservó.
        when(reservaDao.existsByPaginaAndUsuarioAndCargaGreaterThan("P", "saul", 1)).thenReturn(true);
        assertThrows(ClienteReserva.ReservaObsoletaException.class, () -> servicio.reservarCodigo("viejo", 7L, 2L, 1, saul,
                permiso, new ClienteReserva(2, "P", 1, List.of())));
        verify(reservaDao, never()).save(any());

        // La carga 2 descarta lo que haya dejado la carga 1 de la misma pestaña.
        when(reservaDao.findById(anyString())).thenReturn(Optional.empty());
        when(reservaDao.save(any())).thenAnswer(i -> i.getArgument(0));
        servicio.reservarCodigo("nuevo", 7L, 2L, 1, saul, permiso, new ClienteReserva(2, "P", 2, List.of("lote-2")));
        verify(reservaDao).eliminarDeCargasAnteriores("P", "saul", List.of("lote-2", "nuevo"), 2);

        // Sin versión (pantalla de una versión anterior) no se renueva.
        assertThrows(IllegalStateException.class, () -> servicio.reservarCodigo("nuevo", 7L, 2L, 1, saul,
                permiso, new ClienteReserva(null, "P", 2, List.of())));
    }
}
