package com.usic.SistemasActivosFijosUAP;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.usic.SistemasActivosFijosUAP.model.dao.IDonacionActaDao;
import com.usic.SistemasActivosFijosUAP.model.dao.IActivoDao;
import com.usic.SistemasActivosFijosUAP.model.dao.IReservaCorrelativoDao;
import com.usic.SistemasActivosFijosUAP.model.entity.ReservaCorrelativo;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;
import com.usic.SistemasActivosFijosUAP.model.repository.FuncionesActivoRepo;
import com.usic.SistemasActivosFijosUAP.model.service.ClienteReserva;
import com.usic.SistemasActivosFijosUAP.model.service.ReservaCorrelativoService;

@ExtendWith(MockitoExtension.class)
class ReservaCorrelativoServiceTest {
    @Mock IReservaCorrelativoDao dao;
    @Mock IActivoDao activoDao;
    @Mock FuncionesActivoRepo funciones;
    @Mock IDonacionActaDao actaDao;
    @InjectMocks ReservaCorrelativoService service;

    @Test
    void reservaUnCodigoLibreConcretoYRechazaUnoOcupado() {
        Usuario ana = new Usuario(); ana.setUsuario("ana");
        when(dao.findByTipoAndTokenOrderByIndiceAsc("R", "manual")).thenReturn(List.of());
        when(activoDao.findByCodigo("04-15-02-00007")).thenReturn(Optional.empty());
        when(dao.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        ReservaCorrelativo r = service.reservarCodigoLibre("manual", "04-15-02", 7, ana);
        assertEquals(7, r.getDesde());
        assertEquals("04-15-02", r.getPrefijo());
        when(dao.contarOcupando(eq("R"), eq("04-15-02"), eq(8), any(LocalDateTime.class))).thenReturn(1L);
        assertThrows(IllegalStateException.class,
                () -> service.reservarCodigoLibre("manual", "04-15-02", 8, ana));
    }

    @Test
    void dosFormulariosRecibenRangosDistintosYCancelarLiberaElPrimero() {
        Map<String, ReservaCorrelativo> filas = new HashMap<>();
        when(dao.findByTipoAndTokenOrderByIndiceAsc(anyString(), anyString()))
                .thenAnswer(i -> filas.values().stream().filter(r -> r.getTipo().equals(i.getArgument(0))
                        && r.getToken().equals(i.getArgument(1)))
                        .sorted(Comparator.comparingInt(ReservaCorrelativo::getIndice)).toList());
        when(dao.findByTipoAndPrefijoAndVenceAfterOrderByDesdeAsc(anyString(), anyString(), any(LocalDateTime.class)))
                .thenAnswer(i -> filas.values().stream().filter(r -> r.getTipo().equals(i.getArgument(0))
                        && r.getPrefijo().equals(i.getArgument(1)) && r.getVence().isAfter(i.getArgument(2)))
                        .sorted(Comparator.comparingInt(ReservaCorrelativo::getDesde)).toList());
        when(dao.saveAllAndFlush(any())).thenAnswer(i -> {
            List<ReservaCorrelativo> guardadas = new ArrayList<>(i.getArgument(0));
            guardadas.forEach(r -> filas.put(r.getId(), r));
            return guardadas;
        });
        doAnswer(i -> { ((Iterable<ReservaCorrelativo>) i.getArgument(0)).forEach(r -> filas.remove(r.getId())); return null; })
                .when(dao).deleteAll(any());
        when(funciones.previewCodigoPorCodes("04", "15", "02")).thenReturn("04-15-02-00008");
        when(actaDao.maxCorrelativoDonacion("04-15-02")).thenReturn(7);
        var lote = List.of(new ReservaCorrelativoService.Lote("04-15-02", 1));
        Usuario ana = new Usuario(); ana.setUsuario("ana");
        Usuario luis = new Usuario(); luis.setUsuario("luis");

        var a = service.reservar(ReservaCorrelativoService.REGISTRO, "ana", lote, ana);
        var b = service.reservar(ReservaCorrelativoService.REGISTRO, "luis", lote, luis);
        assertEquals(8, a.get(0).getDesde());
        assertEquals(9, b.get(0).getDesde());
        assertEquals(8, service.validarParaGuardar(ReservaCorrelativoService.REGISTRO, "ana", lote, ana).get(0).getDesde());
        assertThrows(IllegalStateException.class, () -> service.validarParaGuardar(
                ReservaCorrelativoService.REGISTRO, "ana",
                List.of(new ReservaCorrelativoService.Lote("04-15-02", 2)), ana));
        service.liberar(ReservaCorrelativoService.REGISTRO, "ana", ana);
        assertEquals(8, service.reservar(ReservaCorrelativoService.REGISTRO, "ana2", lote, ana).get(0).getDesde());

        var varios = service.reservar(ReservaCorrelativoService.REGISTRO, "lotes", List.of(
                new ReservaCorrelativoService.Lote("04-15-02", 2),
                new ReservaCorrelativoService.Lote("04-15-02", 1)), ana);
        assertEquals(10, varios.get(0).getDesde());
        assertEquals(12, varios.get(1).getDesde());

        assertEquals(8, service.reservar(ReservaCorrelativoService.DONACION, "dana", lote, ana).get(0).getDesde());
        assertEquals(9, service.reservar(ReservaCorrelativoService.DONACION, "dluis", lote, luis).get(0).getDesde());
        verify(funciones, atLeast(4)).tomarTurnoRegistro();
        verify(actaDao, times(2)).turno(anyLong());
    }

    @Test
    void trasRecargarLaPaginaAnteriorNoReservaYLaNuevaDescartaSusHuerfanas() {
        Usuario ana = new Usuario(); ana.setUsuario("ana");
        var lote = List.of(new ReservaCorrelativoService.Lote("04-15-02", 1));
        // La renovación tardía de la carga 1 llega cuando la carga 2 ya reservó.
        when(dao.existsByTipoAndPaginaAndUsuarioAndCargaGreaterThan("R", "P", "ana", 1)).thenReturn(true);
        assertThrows(ClienteReserva.ReservaObsoletaException.class, () -> service.reservar(ReservaCorrelativoService.REGISTRO,
                "viejo", lote, ana, new ClienteReserva(2, "P", 1, List.of())));
        verify(dao, never()).saveAllAndFlush(any());

        // La carga 2 descarta lo de cargas anteriores que ya no usa.
        when(funciones.previewCodigoPorCodes("04", "15", "02")).thenReturn("04-15-02-00008");
        when(dao.saveAllAndFlush(any())).thenAnswer(i -> new ArrayList<>(i.getArgument(0)));
        var r = service.reservar(ReservaCorrelativoService.REGISTRO, "nuevo", lote, ana, new ClienteReserva(2, "P", 2, List.of()));
        verify(dao).eliminarDeCargasAnteriores("R", "P", "ana", List.of("nuevo"), 2);
        assertEquals("P", r.get(0).getPagina());
        assertEquals(2, r.get(0).getCarga());

        // Pantalla de una versión anterior (sin versión): se le pide recargar.
        assertThrows(IllegalStateException.class, () -> service.reservar(ReservaCorrelativoService.REGISTRO,
                "nuevo", lote, ana, new ClienteReserva(null, "P", 2, List.of())));
    }
}
