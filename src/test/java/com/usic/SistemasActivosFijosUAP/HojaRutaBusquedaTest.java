package com.usic.SistemasActivosFijosUAP;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.usic.SistemasActivosFijosUAP.model.ServiceImpl.HojaRutaServiceImpl;
import com.usic.SistemasActivosFijosUAP.model.dao.IHojaRutaDao;
import com.usic.SistemasActivosFijosUAP.model.dao.IMovimientoDao;
import com.usic.SistemasActivosFijosUAP.model.entity.HojaRuta;
import com.usic.SistemasActivosFijosUAP.model.entity.Solicitante;

class HojaRutaBusquedaTest {
    @Test
    void numeroParcialDevuelveCoincidenciasDeDistintosTiposYGestion() {
        IHojaRutaDao hojas = mock(IHojaRutaDao.class);
        IMovimientoDao movimientos = mock(IMovimientoDao.class);
        HojaRuta primera = hoja(1L, "123-A", "DAF", 2025);
        HojaRuta segunda = hoja(2L, "123-B", "RECTORADO", 2026);
        when(hojas.buscarParaRecepcion("123", null, null)).thenReturn(List.of(segunda, primera));
        when(movimientos.resumenParaHojas(List.of(2L, 1L))).thenReturn(List.<Object[]>of(
                new Object[] { 2L, "1", LocalDate.of(2026, 10, 22), "ACTIVOS FIJOS" }));

        var resultado = new HojaRutaServiceImpl(hojas, movimientos).buscarParaRecepcion(" 123 ", null, null);

        assertEquals(2, resultado.size());
        assertEquals("123-B", resultado.get(0).getCodigo());
        assertEquals(2025, resultado.get(1).getGestion());
        assertEquals("ACTIVOS FIJOS", resultado.get(0).getUbicacionActual());
        assertEquals("SIN MOVIMIENTOS", resultado.get(1).getEstadoActual());
        verify(movimientos).resumenParaHojas(List.of(2L, 1L));
    }

    @Test
    void sinCoincidenciasEvitaConsultaDeMovimientos() {
        IHojaRutaDao hojas = mock(IHojaRutaDao.class);
        IMovimientoDao movimientos = mock(IMovimientoDao.class);
        when(hojas.buscarParaRecepcion("NO EXISTE", null, null)).thenReturn(List.of());

        assertTrue(new HojaRutaServiceImpl(hojas, movimientos)
                .buscarParaRecepcion("NO EXISTE", null, null).isEmpty());
        org.mockito.Mockito.verifyNoInteractions(movimientos);
    }

    private static HojaRuta hoja(Long id, String codigo, String tipo, int gestion) {
        HojaRuta h = new HojaRuta();
        h.setIdHojaRuta(id);
        h.setCodigo(codigo);
        h.setTipo(tipo);
        h.setGestion(gestion);
        Solicitante s = new Solicitante();
        s.setNombre("ANA PÉREZ");
        h.setSolicitante(s);
        return h;
    }
}
