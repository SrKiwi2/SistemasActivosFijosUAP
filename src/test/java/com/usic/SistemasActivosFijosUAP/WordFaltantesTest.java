package com.usic.SistemasActivosFijosUAP;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.List;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.usic.SistemasActivosFijosUAP.model.dto.control.ActaFaltanteDTO;
import com.usic.SistemasActivosFijosUAP.model.dto.control.CustodiaDTOs;
import com.usic.SistemasActivosFijosUAP.model.repository.CustodiaFaltantesRepo;
import com.usic.SistemasActivosFijosUAP.model.service.control.WordActaFaltanteService;
import com.usic.SistemasActivosFijosUAP.model.service.control.WordReportesCustodiaService;

class WordFaltantesTest {
    @Test
    void notificacionEmitidaIncluyeBienPlazoYVerificacion() throws Exception {
        WordActaFaltanteService service = new WordActaFaltanteService();
        ReflectionTestUtils.setField(service, "urlPublica", "https://sciaf.uap.edu.bo");
        try (XWPFDocument doc = abrir(service.generar(acta(), true))) {
            String texto = texto(doc);
            assertTrue(texto.contains("NOTIFICACIÓN DE ACTIVOS FÍSICOS FALTANTES"));
            assertTrue(texto.contains("SILLA GIRATORIA"));
            assertTrue(texto.contains("5 (cinco) días hábiles"));
            assertTrue(texto.contains("Se adjunta documentación de respaldo"));
            assertTrue(doc.getFooterList().stream().anyMatch(f -> f.getText().contains("/verificar/acta-faltantes/token-123")));
            assertTrue(doc.getFooterList().stream().flatMap(f -> f.getParagraphs().stream())
                    .flatMap(p -> p.getRuns().stream()).anyMatch(r -> !r.getEmbeddedPictures().isEmpty()));
        }
    }

    @Test
    void borradorWordNoContieneNumeroNiVerificacion() throws Exception {
        WordActaFaltanteService service = new WordActaFaltanteService();
        ReflectionTestUtils.setField(service, "urlPublica", "https://sciaf.uap.edu.bo");
        try (XWPFDocument doc = abrir(service.generarVistaPrevia(acta()))) {
            assertTrue(texto(doc).contains("VISTA PREVIA"));
            assertFalse(texto(doc).contains("NOT:SCIAF:AF N° 001/2026"));
            assertFalse(doc.getFooterList().stream().anyMatch(f -> f.getText().contains("/verificar/")));
        }
    }

    @Test
    void actaDeRegularizacionHistoricaEsEditableYConservaFirmas() throws Exception {
        ActaFaltanteDTO n = acta();
        ActaFaltanteDTO historica = new ActaFaltanteDTO(n.idActa(), "AR-001/2026", n.token(), n.fechaEmision(),
                n.usuarioEmision(), n.personaNombre(), n.personaCi(), n.personaCargo(), n.documentoRespaldo(),
                n.fechaDocumento(), n.observacion(), n.totalBienes(), n.estado(), n.motivoAnulacion(),
                n.fechaAnulacion(), n.hash(), n.integro(), n.predios(), "REGULARIZACION", null, n.ciudad(),
                n.firmante(), n.unidad(), null, null, null, null, null, List.of(), null, false);
        WordActaFaltanteService service = new WordActaFaltanteService();
        ReflectionTestUtils.setField(service, "urlPublica", "https://sciaf.uap.edu.bo");
        try (XWPFDocument doc = abrir(service.generar(historica, false))) {
            assertTrue(texto(doc).contains("ACTA DE REGULARIZACIÓN DE FALTANTES"));
            assertTrue(texto(doc).contains("SILLA GIRATORIA"));
            assertTrue(texto(doc).contains("Firma del responsable"));
        }
    }

    @Test
    void reportesWordConservanDetalleYTotales() throws Exception {
        CustodiaFaltantesRepo repo = mock(CustodiaFaltantesRepo.class);
        when(repo.prediosConCustodia()).thenReturn(List.of(new CustodiaDTOs.PredioCustodia(1L, "UAP", "Central", 2L, (short) 3, "Faltantes", 1)));
        when(repo.bienesEnCustodia(1L)).thenReturn(List.of(new CustodiaDTOs.BienCustodia(5L, "01-02-03-D00001", "SILLA GIRATORIA",
                7L, "ANA PEREZ", "123", (short) 8, "OFICINA A", "AF-1", null, "EN_CUSTODIA", null)));
        when(repo.consolidado(true)).thenReturn(List.of(new CustodiaDTOs.ConsolidadoFila(7L, "ANA PEREZ", "123", "UAP", "Central", 1, 2, 0)));
        WordReportesCustodiaService service = new WordReportesCustodiaService(repo);
        try (XWPFDocument predio = abrir(service.custodiaPredioWord(1L, "admin"));
             XWPFDocument consolidado = abrir(service.consolidadoWord(true, "admin"))) {
            assertTrue(texto(predio).contains("SILLA GIRATORIA"));
            assertTrue(texto(predio).contains("ANA PEREZ"));
            assertTrue(texto(consolidado).contains("ANA PEREZ"));
            assertTrue(texto(consolidado).contains("Personas: 1"));
            assertEquals(1, predio.getTables().size());
        }
    }

    private static ActaFaltanteDTO acta() {
        var bien = new ActaFaltanteDTO.Bien("01-02-03-D00001", "SILLA GIRATORIA", "SILLA GIRATORIA", "A", "B", "C");
        var oficina = new ActaFaltanteDTO.Oficina((short) 8, "OFICINA A", List.of(bien));
        return new ActaFaltanteDTO(1L, "NOT-AF-001/2026", "token-123", LocalDateTime.of(2026, 10, 22, 10, 0),
                "admin", "ANA PEREZ", "123", "DOCENTE", "DOC-1", null, null, 1, "VIGENTE", null, null,
                "0123456789ABCDEF", true, List.of(new ActaFaltanteDTO.Predio("UAP", "Central", List.of(oficina))),
                "FALTANTES", 5, "Cobija", "FIRMA", "FACULTAD", null, null, null, null, null, List.of(), null, false);
    }

    private static XWPFDocument abrir(byte[] bytes) throws Exception {
        return new XWPFDocument(new ByteArrayInputStream(bytes));
    }

    private static String texto(XWPFDocument doc) {
        StringBuilder s = new StringBuilder();
        doc.getParagraphs().forEach(p -> s.append(p.getText()).append('\n'));
        doc.getTables().forEach(t -> t.getRows().forEach(r -> r.getTableCells().forEach(c -> s.append(c.getText()).append('\n'))));
        return s.toString();
    }
}
