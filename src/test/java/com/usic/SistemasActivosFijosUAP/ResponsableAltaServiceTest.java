package com.usic.SistemasActivosFijosUAP;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.usic.SistemasActivosFijosUAP.model.IService.ICargoService;
import com.usic.SistemasActivosFijosUAP.model.IService.IOficinaService;
import com.usic.SistemasActivosFijosUAP.model.IService.IPersonaService;
import com.usic.SistemasActivosFijosUAP.model.IService.IResponsableService;
import com.usic.SistemasActivosFijosUAP.model.entity.Cargo;
import com.usic.SistemasActivosFijosUAP.model.entity.Oficina;
import com.usic.SistemasActivosFijosUAP.model.entity.Persona;
import com.usic.SistemasActivosFijosUAP.model.entity.Responsable;
import com.usic.SistemasActivosFijosUAP.model.service.ResponsableAltaService;
import com.usic.SistemasActivosFijosUAP.model.service.ResponsableAltaService.DatosAlta;
import com.usic.SistemasActivosFijosUAP.model.service.ResponsableAltaService.PersonasSimilaresException;

/**
 * Alta de responsable. El caso que motivó estas pruebas: al encontrar personas con un
 * nombre parecido, el formulario ofrecía "crear de todas formas", pero el endpoint
 * forzado repetía la misma búsqueda y volvía a responder 409: no había forma de
 * registrar a la persona.
 */
class ResponsableAltaServiceTest {

    private IResponsableService responsableService;
    private IPersonaService personaService;
    private ICargoService cargoService;
    private ResponsableAltaService servicio;
    private Oficina oficina;

    @BeforeEach
    void preparar() {
        responsableService = mock(IResponsableService.class);
        personaService = mock(IPersonaService.class);
        cargoService = mock(ICargoService.class);
        servicio = new ResponsableAltaService(responsableService, personaService, cargoService,
                mock(IOficinaService.class));

        oficina = new Oficina();
        oficina.setIdOficina(7L);
        oficina.setNombre("SISTEMAS");

        Persona parecida = new Persona();
        parecida.setIdPersona(1L);
        parecida.setNombre("JUAN");
        parecida.setPaterno("PEREZ");
        parecida.setCi("999");
        when(personaService.buscarPorNombreApellidos(anyString(), anyString(), any())).thenReturn(List.of(parecida));

        Cargo cargo = new Cargo();
        cargo.setNombre("JEFE");
        when(cargoService.buscarPorNombre("JEFE")).thenReturn(cargo);
    }

    private DatosAlta datos(String codFun, String cargo) {
        return new DatosAlta("12345", (short) 9, codFun, null, "Juan", "Perez", null, null, cargo);
    }

    @Test
    void conPersonasParecidasPideConfirmacion() {
        assertThrows(PersonasSimilaresException.class,
                () -> servicio.registrar(datos("3", "JEFE"), oficina, null, false, false));
        verify(responsableService, never()).save(any());
    }

    @Test
    void confirmadoCreaLaPersonaNueva() {
        ResponsableAltaService.ResultadoAlta r = servicio.registrar(datos("3", "jefe"), oficina, null, true, false);
        assertTrue(r.personaNueva());
        assertEquals("12345", r.responsable().getPersona().getCi());
        assertEquals("JUAN", r.responsable().getPersona().getNombre());
        assertEquals("JEFE", r.responsable().getCargo().getNombre());
        verify(responsableService).save(any(Responsable.class));
    }

    @Test
    void mismoNombreSinCiSeReutilizaYSeCompleta() {
        Persona sinCi = new Persona();
        sinCi.setIdPersona(2L);
        sinCi.setNombre("JUAN");
        sinCi.setPaterno("PEREZ");
        when(personaService.buscarPersonaPorNombreCompletoUno("JUAN", "PEREZ", null)).thenReturn(sinCi);

        ResponsableAltaService.ResultadoAlta r = servicio.registrar(datos("3", "JEFE"), oficina, null, false, false);
        assertEquals(2L, r.responsable().getPersona().getIdPersona());
        assertEquals("12345", sinCi.getCi());
    }

    @Test
    void rechazaLoQueNoEntraEnRespDbf() {
        assertThrows(IllegalArgumentException.class,
                () -> servicio.registrar(datos("A1", "JEFE"), oficina, null, true, false));
        assertThrows(IllegalArgumentException.class,
                () -> servicio.registrar(datos("123456", "JEFE"), oficina, null, true, false));
        assertThrows(IllegalArgumentException.class,
                () -> servicio.registrar(datos("3", "X".repeat(41)), oficina, null, true, false));
    }

    @Test
    void codigoRepetidoEnLaOficina() {
        when(responsableService.findByCodigoFuncionarioYOficina("3", 7L)).thenReturn(new Responsable());
        assertThrows(IllegalArgumentException.class,
                () -> servicio.registrar(datos("3", "JEFE"), oficina, null, true, false));
    }
}
