package com.usic.SistemasActivosFijosUAP;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.apache.poi.xwpf.usermodel.XWPFDocument;

import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.parser.PdfTextExtractor;
import com.usic.SistemasActivosFijosUAP.model.IService.IResponsableEntregaService;
import com.usic.SistemasActivosFijosUAP.model.dao.IActivoAdicionalDao;
import com.usic.SistemasActivosFijosUAP.model.dao.IConfiguracionGestionDao;
import com.usic.SistemasActivosFijosUAP.model.entity.Activo;
import com.usic.SistemasActivosFijosUAP.model.entity.ActivoAdicional;
import com.usic.SistemasActivosFijosUAP.model.entity.Cargo;
import com.usic.SistemasActivosFijosUAP.model.entity.ConfiguracionGestion;
import com.usic.SistemasActivosFijosUAP.model.entity.EstadoActivo;
import com.usic.SistemasActivosFijosUAP.model.entity.Oficina;
import com.usic.SistemasActivosFijosUAP.model.entity.Persona;
import com.usic.SistemasActivosFijosUAP.model.entity.Responsable;
import com.usic.SistemasActivosFijosUAP.model.entity.ResponsableEntrega;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;
import com.usic.SistemasActivosFijosUAP.model.service.adicional.AdicionalService;
import com.usic.SistemasActivosFijosUAP.model.service.adicional.ReporteAdicionalService;

class ReporteAdicionalServiceTest {
    private static final LocalDateTime FECHA = LocalDateTime.of(2026, 4, 6, 16, 30);

    @Test
    void wordConservaBienesYActaPorResponsable() throws Exception {
        IActivoAdicionalDao dao = mock(IActivoAdicionalDao.class);
        ReporteAdicionalService servicio = servicio(dao);
        Oficina oficina = oficina();
        when(dao.paraReporte(7L, null)).thenReturn(List.of(
                bien(oficina, responsable(11L, "ANA"), "CÁMARA", "01-02-03-00001A", null),
                bien(oficina, responsable(12L, "LUIS"), "ROUTER", null, null)));
        byte[] bytes = servicio.generarWord(7L, null, FECHA, usuario(), Set.of(AdicionalService.PERMISO));
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            assertEquals(4, doc.getTables().size()); // bienes y destinatario de cada acta
            String texto = doc.getParagraphs().stream().map(p -> p.getText()).reduce("", (a, b) -> a + b);
            assertTrue(texto.contains("DE BIENES - ADICIONAL 2026"));
            assertTrue(doc.getTables().get(0).getText().contains("CÁMARA"));
            assertTrue(doc.getTables().get(2).getText().contains("ROUTER"));
        }
    }

    @Test
    void separaResponsablesYNoPierdeBienesSinCodigo() throws Exception {
        IActivoAdicionalDao dao = mock(IActivoAdicionalDao.class);
        ReporteAdicionalService servicio = servicio(dao);
        Oficina oficina = oficina();
        Responsable uno = responsable(11L, "ANA");
        Responsable dos = responsable(12L, "LUIS");
        when(dao.paraReporte(7L, null)).thenReturn(List.of(bien(oficina, uno, "CÁMARA", "01-02-03-00001A", null),
                bien(oficina, dos, "ROUTER", null, null)));
        byte[] pdf = servicio.generar(7L, null, FECHA, usuario(), Set.of(AdicionalService.PERMISO));
        PdfReader reader = new PdfReader(pdf);
        try {
            assertEquals(2, reader.getNumberOfPages());
            assertTrue(PdfTextExtractor.getTextFromPage(reader, 1).contains("CÁMARA"));
            assertTrue(PdfTextExtractor.getTextFromPage(reader, 2).contains("ROUTER"));
            assertTrue(PdfTextExtractor.getTextFromPage(reader, 2).contains("S/CD"));
        } finally { reader.close(); }
    }

    @Test
    void sigueElFormatoDelActaEnPapel() throws Exception {
        IActivoAdicionalDao dao = mock(IActivoAdicionalDao.class);
        ReporteAdicionalService servicio = servicio(dao);
        Responsable r = responsable(11L, "MANUEL");
        r.getPersona().setCi("1767163"); r.getPersona().setExtension("PDO");
        Cargo cargo = new Cargo(); cargo.setNombre("ADMINISTRATIVO SALA DE INFORMATICA ACSYH"); r.setCargo(cargo);
        when(dao.paraReporte(7L, 11L)).thenReturn(List.of(
                bien(oficina(), r, "CAMARA DE VIGILANCIA", "01-02-03-00001A", "UAP - 016303620044")));
        byte[] pdf = servicio.generar(7L, 11L, FECHA, usuario(), Set.of(AdicionalService.PERMISO));
        PdfReader reader = new PdfReader(pdf);
        try {
            String t = PdfTextExtractor.getTextFromPage(reader, 1).replaceAll("\\s+", " ");
            assertTrue(t.contains("DE BIENES - ADICIONAL 2026"), t);
            assertTrue(t.contains("a 06 días del mes de ABRIL del 2026, a horas 16:30"), t);
            assertTrue(t.contains("Lic. Verónica Layme Cori. Responsable de Activos Fijos"), t);
            assertTrue(t.contains("visto bueno del Ing. Mayko A. Antezana Sosa"), t);
            assertTrue(t.contains("Oficina 106 LABORATORIO"), t);
            assertTrue(t.contains("148-01-02-03-00001"), t);
            assertTrue(t.contains("UAP - 016303620044"), t);
            assertTrue(t.contains("REGULAR"), t);
            assertTrue(t.contains("C.I. 1767163 PDO."), t);
            assertTrue(t.contains("ADMINISTRATIVO SALA DE INFORMATICA ACSYH – U.A.P."), t);
            assertTrue(t.contains("Decreto Supremo 0181"), t);
            assertTrue(t.contains("Vo. Bo."), t);
            assertTrue(t.contains("KC/A-F"), t);
            // Membrete de fondo: una imagen en la hoja.
            assertTrue(new String(reader.getPageContent(1), "ISO-8859-1").contains(" Do"));
        } finally { reader.close(); }
    }

    @Test
    void rechazaSeleccionSinBienes() {
        IActivoAdicionalDao dao = mock(IActivoAdicionalDao.class);
        ReporteAdicionalService servicio = servicio(dao);
        assertThrows(IllegalArgumentException.class,
                () -> servicio.generar(7L, 12L, FECHA, usuario(), Set.of(AdicionalService.PERMISO)));
    }

    private static ReporteAdicionalService servicio(IActivoAdicionalDao dao) {
        IConfiguracionGestionDao conf = mock(IConfiguracionGestionDao.class);
        ConfiguracionGestion c = new ConfiguracionGestion();
        c.setCiudad("Cobija"); c.setResponsableActivosNombre("Lic. Verónica Layme Cori");
        when(conf.findFirstByGestionAndEstadoOrderByIdConfigDesc(anyInt(), anyString())).thenReturn(Optional.of(c));
        IResponsableEntregaService entrega = mock(IResponsableEntregaService.class);
        ResponsableEntrega e = new ResponsableEntrega(); e.setNombre("Ing. Mayko A. Antezana Sosa"); e.setGenero("M");
        when(entrega.findSeleccionado()).thenReturn(e);
        return new ReporteAdicionalService(dao, conf, entrega);
    }

    private static Usuario usuario() {
        Persona p = new Persona(); p.setNombre("KEVIN"); p.setPaterno("CALLISAYA");
        Usuario u = new Usuario(); u.setEstado("ACTIVO"); u.setPersona(p);
        return u;
    }

    private static Oficina oficina() {
        Oficina oficina = new Oficina(); oficina.setIdOficina(7L); oficina.setNombre("LABORATORIO"); oficina.setCodOfi((short) 106);
        return oficina;
    }

    private static Responsable responsable(long id, String nombre) {
        Persona p = new Persona(); p.setNombre(nombre); p.setPaterno("PEREZ");
        Responsable r = new Responsable(); r.setIdResponsable(id); r.setPersona(p);
        return r;
    }

    private static ActivoAdicional bien(Oficina oficina, Responsable responsable, String descripcion, String codigo, String antiguo) {
        Activo a = new Activo(); a.setOficina(oficina); a.setResponsable(responsable);
        a.setDescripcion(descripcion); a.setCodigo(codigo); a.setCodigoAntiguo(antiguo);
        EstadoActivo estado = new EstadoActivo(); estado.setNombre("Regular"); a.setEstadoActivo(estado);
        ActivoAdicional x = new ActivoAdicional(); x.setActivo(a);
        return x;
    }
}
