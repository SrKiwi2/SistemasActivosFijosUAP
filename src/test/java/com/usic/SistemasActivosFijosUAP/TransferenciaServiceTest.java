package com.usic.SistemasActivosFijosUAP;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.usic.SistemasActivosFijosUAP.model.IService.IActivoService;
import com.usic.SistemasActivosFijosUAP.model.IService.ITransferenciaService;
import com.usic.SistemasActivosFijosUAP.model.dao.IHistorialActivoDao;
import com.usic.SistemasActivosFijosUAP.model.dao.ITransferenciaDao;
import com.usic.SistemasActivosFijosUAP.model.entity.Activo;
import com.usic.SistemasActivosFijosUAP.model.entity.Oficina;
import com.usic.SistemasActivosFijosUAP.model.entity.Persona;
import com.usic.SistemasActivosFijosUAP.model.entity.Predio;
import com.usic.SistemasActivosFijosUAP.model.entity.Responsable;
import com.usic.SistemasActivosFijosUAP.model.entity.Transferencia;
import com.usic.SistemasActivosFijosUAP.model.service.TransferenciaService;

/**
 * Lo que rompía las transferencias externas: el documento de referencia y la observación
 * se guardaban con un UPDATE posterior al INSERT, y ese UPDATE mandaba en null el
 * {@code numero_transferencia} que pone el trigger de la base (NOT NULL). Resultado: los
 * activos quedaban movidos en el SCIAF y la transferencia reventaba antes de llegar al VSIAF.
 */
class TransferenciaServiceTest {

    private ITransferenciaService transferenciaService;
    private ITransferenciaDao transferenciaDao;
    private TransferenciaService servicio;
    private Oficina oficinaDestino;
    private Responsable respDestino;

    @BeforeEach
    void preparar() {
        transferenciaService = mock(ITransferenciaService.class);
        transferenciaDao = mock(ITransferenciaDao.class);
        servicio = new TransferenciaService(transferenciaService, mock(IActivoService.class),
                mock(IHistorialActivoDao.class), transferenciaDao);

        when(transferenciaService.save(any())).thenAnswer(i -> {
            Transferencia t = i.getArgument(0);
            t.setIdTransferencia(77L);
            return t;   // como la base: el número lo pone el trigger, no la entidad
        });
        when(transferenciaDao.numeroDe(77L)).thenReturn("TRF-2026-0001");

        Predio predio = new Predio();
        predio.setIdPredio(1L);
        predio.setUnidad("CAUN");
        oficinaDestino = new Oficina();
        oficinaDestino.setIdOficina(5L);
        oficinaDestino.setCodOfi((short) 5);
        oficinaDestino.setNombre("SISTEMAS");
        oficinaDestino.setPredio(predio);

        Persona p = new Persona();
        p.setNombre("JUAN");
        p.setPaterno("PEREZ");
        respDestino = new Responsable();
        respDestino.setIdResponsable(9L);
        respDestino.setPersona(p);
        respDestino.setOficina(oficinaDestino);
    }

    private List<TransferenciaService.ActivoConOrigen> unActivo() {
        Activo a = new Activo();
        a.setIdActivo(100L);
        a.setCodigo("01-02-0123");
        a.setDescripcion("ESCRITORIO");
        a.setOficina(oficinaDestino);
        a.setResponsable(respDestino);
        return List.of(new TransferenciaService.ActivoConOrigen(a));
    }

    @Test
    void elDocumentoYLaObservacionViajanEnElInsert() {
        Transferencia t = servicio.registrarTransferencia(unActivo(), "EXTERNA", oficinaDestino, respDestino,
                3L, "admin1", "NOTA-2026-015", "Cambio de predio por refacción", null);

        // Un solo save: si hiciera falta otro para los extras, volvería el error del número.
        verify(transferenciaService).save(any(Transferencia.class));
        assertEquals("NOTA-2026-015", t.getDocumentoReferencia());
        assertEquals("Cambio de predio por refacción", t.getObservacion());
        assertEquals("EXTERNA", t.getTipo());
    }

    @Test
    void elNumeroDelTriggerSeReleeParaMostrarlo() {
        Transferencia t = servicio.registrarTransferencia(unActivo(), "INTERNA", oficinaDestino, respDestino,
                3L, "admin1", null, null, null);
        assertEquals("TRF-2026-0001", t.getNumeroTransferencia());
    }

    @Test
    void losCamposVaciosNoSeGuardanComoCadenaVacia() {
        Transferencia t = servicio.registrarTransferencia(unActivo(), "INTERNA", oficinaDestino, respDestino,
                3L, "admin1", "   ", "", "  ");
        assertNull(t.getDocumentoReferencia());
        assertNull(t.getObservacion());
        assertNull(t.getInstitucionDestino());
    }

    @Test
    void siLaBaseNoDevuelveNumeroNoSeInventaNinguno() {
        when(transferenciaDao.numeroDe(anyLong())).thenReturn(null);
        Transferencia t = servicio.registrarTransferencia(unActivo(), "INTERNA", oficinaDestino, respDestino,
                3L, "admin1", null, null, null);
        assertNull(t.getNumeroTransferencia());
        verify(transferenciaService, never()).save(null);
    }
}
