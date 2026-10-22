package com.usic.SistemasActivosFijosUAP;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.usic.SistemasActivosFijosUAP.model.dto.duplicados.DuplicadosDTOs;
import com.usic.SistemasActivosFijosUAP.model.service.PdfEtiquetasQrService;
import com.usic.SistemasActivosFijosUAP.model.service.documento.WordEtiquetasQrService;
import com.usic.SistemasActivosFijosUAP.model.service.donacion.DonacionService;
import com.usic.SistemasActivosFijosUAP.model.service.donacion.WordActaDonacionService;
import com.usic.SistemasActivosFijosUAP.model.service.duplicados.WordNotificacionDuplicadoService;

class WordModulosTest {
    @Test
    void actaDonacionEsWordConSusBienes() throws Exception {
        DonacionService datos = mock(DonacionService.class);
        when(datos.detalle(7L)).thenReturn(Map.of(
                "estado", "VIGENTE", "numero", "DON-007", "donante", "ANA PEREZ",
                "fecha", "2026-10-20", "cantidad", 1, "valor", 100.0,
                "bienes", List.of(Map.of("item", 1, "codigo", "01-02-03-D00001",
                        "descripcion", "SILLA", "valor", 100.0))));
        byte[] bytes = new WordActaDonacionService(datos).generar(7L);
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            String texto = texto(doc);
            assertTrue(texto.contains("ACTA DE RECEPCIÓN DE BIENES EN DONACIÓN"));
            assertTrue(texto.contains("SILLA"));
            assertTrue(texto.contains("DON-007"));
        }
    }

    @Test
    void notificacionDuplicadoConservaVerificacionQr() throws Exception {
        var bien = new DuplicadosDTOs.BienDoc("01-02-03-00001", "SILLA", "SILLA", "M", "X", "S1", "OFICINA", "PREDIO", "ANA");
        var contenido = new DuplicadosDTOs.Contenido("NOT-1", "2026-10-20T10:00:00", "Cobija", "FIRMA",
                "ANA", "123", "DOCENTE", "FACULTAD", "SAUL", "saul", "Dos registros", bien, bien, null, "DUPLICADOS");
        var notificacion = new DuplicadosDTOs.Notificacion(1L, "NOT-1", "NOT:SCIAF:AD N° 1/2026", "token-qr",
                "0123456789ABCDEF", "VIGENTE", null, null, "VIGENTE", null, null, true, contenido);
        var service = new WordNotificacionDuplicadoService();
        ReflectionTestUtils.setField(service, "urlPublica", "https://sciaf.uap.edu.bo");
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(service.generar(notificacion, false)))) {
            assertTrue(texto(doc).contains("REGISTRO DUPLICADO"));
            assertTrue(texto(doc).contains("/verificar/duplicado/token-qr"));
            assertTrue(doc.getAllPictures().size() >= 1);
        }
    }

    @Test
    void etiquetasWordDistribuyenVeinticincoEnDosPaginas() throws Exception {
        var etiqueta = new PdfEtiquetasQrService.Etiqueta("UAP · ADICIONAL", "01-02-03-00001A", "SILLA", "PREDIO", "UAP|01|02|03|01-02-03-00001A|SILLA");
        byte[] bytes = new WordEtiquetasQrService().generar(java.util.Collections.nCopies(25, etiqueta), null);
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            assertEquals(2, doc.getTables().size());
            long qr = doc.getTables().stream().flatMap(t -> t.getRows().stream())
                    .flatMap(r -> r.getTableCells().stream()).flatMap(c -> c.getParagraphs().stream())
                    .flatMap(p -> p.getRuns().stream()).mapToLong(r -> r.getEmbeddedPictures().size()).sum();
            assertEquals(25, qr);
        }
    }

    private static String texto(XWPFDocument doc) {
        StringBuilder s = new StringBuilder();
        doc.getParagraphs().forEach(p -> s.append(p.getText()).append('\n'));
        doc.getTables().forEach(t -> t.getRows().forEach(r -> r.getTableCells().forEach(c -> s.append(c.getText()).append('\n'))));
        return s.toString();
    }
}
