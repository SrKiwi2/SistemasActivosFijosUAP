package com.usic.SistemasActivosFijosUAP;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

import com.usic.SistemasActivosFijosUAP.model.dto.ActivoTransferenciaDTO;
import com.usic.SistemasActivosFijosUAP.model.service.interno.WordInternoTransferenciaService;

/** Test unitario plano (sin contexto Spring / sin BD) para validar el .docx del acta. */
class WordInternoTransferenciaServiceTest {

    @Test
    void generaDocxValido() throws Exception {
        WordInternoTransferenciaService svc = new WordInternoTransferenciaService();

        ActivoTransferenciaDTO d = new ActivoTransferenciaDTO();
        d.setCodigo("123-456");
        d.setDescripcion("ESCRITORIO DE MADERA 2 CAJONES");
        d.setUbicacionOrigen("OFICINA 1 - PLANTA BAJA");
        d.setUbicacionActual("OFICINA 2 - PRIMER PISO");

        byte[] bytes = svc.wordTransferenciaActivo(
                null, "UNIDAD ORIGEN", null, "2026-06-25",
                "UNIDAD DESTINO", null, "2026-06-26", List.of(d));

        assertNotNull(bytes);
        assertTrue(bytes.length > 1000, "El .docx debería tener contenido real");

        // Si POI lo reabre sin excepción, el documento es estructuralmente válido.
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            assertEquals(2, doc.getTables().size(), "Deben existir 2 tablas (datos + activos)");
        }

        // Copia inspeccionable manualmente (nombre único para no chocar con un archivo abierto).
        Path out = Path.of(System.getProperty("java.io.tmpdir"),
                "acta_transferencia_" + System.currentTimeMillis() + ".docx");
        Files.write(out, bytes);
        System.out.println("[TEST] Acta Word generada en: " + out);
    }
}
