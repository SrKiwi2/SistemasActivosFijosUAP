package com.usic.SistemasActivosFijosUAP;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

import com.usic.SistemasActivosFijosUAP.controller.formularios.WordAsignacionActivoService;
import com.usic.SistemasActivosFijosUAP.model.entity.Activo;
import com.usic.SistemasActivosFijosUAP.model.entity.AsignacionActivo;
import com.usic.SistemasActivosFijosUAP.model.entity.Cargo;
import com.usic.SistemasActivosFijosUAP.model.entity.ConfiguracionGestion;
import com.usic.SistemasActivosFijosUAP.model.entity.DetalleAsignacionActivo;
import com.usic.SistemasActivosFijosUAP.model.entity.Oficina;
import com.usic.SistemasActivosFijosUAP.model.entity.Persona;
import com.usic.SistemasActivosFijosUAP.model.entity.Responsable;

/**
 * Prueba de humo SIN Spring/BD: construye un acta de ejemplo, genera el .docx y
 * lo vuelve a abrir con POI para confirmar que el documento está bien formado
 * (Word no pediría "reparar") y que el membrete quedó embebido en el encabezado.
 */
class WordActaSmokeTest {

    @Test
    void generaDocxBienFormadoConMembrete() throws Exception {
        // ── Datos de ejemplo ────────────────────────────────────────────
        ConfiguracionGestion config = new ConfiguracionGestion();
        config.setGestion(2025);
        config.setCiudad("Cobija");

        Persona persona = new Persona();
        persona.setNombre("Juan");
        persona.setPaterno("Pérez");
        persona.setMaterno("Quispe");
        persona.setCi("1234567");

        Cargo cargo = new Cargo();
        cargo.setNombre("Técnico de Sistemas");

        Responsable responsable = new Responsable();
        responsable.setPersona(persona);
        responsable.setCargo(cargo);

        Oficina oficina = new Oficina();
        oficina.setNombre("Oficina Central");

        Activo activo = new Activo();
        activo.setCodigo("0001");
        activo.setDescripcion("(Prev. 5342) MESA DE MADERA DE 4 GAVETAS");
        activo.setOficina(oficina);

        DetalleAsignacionActivo detalle = new DetalleAsignacionActivo();
        detalle.setActivo(activo);

        AsignacionActivo asignacion = new AsignacionActivo();
        asignacion.setFechaAsignacion(LocalDateTime.now());
        asignacion.setResponsable(responsable);
        asignacion.setCodigoDocumento("5342");
        asignacion.setCodigoCompleto("(Prev. 5342)");
        asignacion.setDetalles(List.of(detalle));

        // ── Generar ─────────────────────────────────────────────────────
        byte[] docx = new WordAsignacionActivoService().generarActaAsignacion(asignacion, config);
        assertTrue(docx.length > 0, "El .docx no debe estar vacío");

        Path salida = Path.of("target", "acta-smoke.docx");
        Files.write(salida, docx);
        System.out.println("Acta de prueba escrita en: " + salida.toAbsolutePath());

        // ── Regresión: el <wp:anchor> del membrete DEBE incluir el atributo
        // requerido "simplePos"; sin él, POI acepta el archivo pero Word lo rechaza
        // con "Xml parsing error" en /word/header1.xml.
        String headerXml = leerEntrada(docx, "word/header1.xml");
        assertTrue(headerXml.contains("<wp:anchor"), "El encabezado debe contener el anclaje del membrete");
        String anchorTag = headerXml.substring(headerXml.indexOf("<wp:anchor"),
                headerXml.indexOf(">", headerXml.indexOf("<wp:anchor")));
        assertTrue(anchorTag.contains("simplePos="),
                "El <wp:anchor> debe incluir el atributo requerido simplePos (Word lo exige)");

        // ── Reabrir y validar (well-formed) ─────────────────────────────
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docx));
             XWPFWordExtractor ext = new XWPFWordExtractor(doc)) {

            String texto = ext.getText();
            assertTrue(texto.contains("ACTA DE ASIGNACIÓN INDIVIDUAL"), "Falta el título");
            assertTrue(texto.contains("(Prev. 5342)"), "Falta el código del documento");
            assertTrue(texto.contains("MESA DE MADERA"), "Falta la descripción del activo");
            assertTrue(texto.contains("Juan Pérez Quispe"), "Falta el nombre del responsable");
            assertTrue(texto.contains("RECIBE CONFORME"), "Falta la zona de firmas");

            // El membrete debe haberse embebido como imagen en el encabezado
            boolean hayMembrete = doc.getHeaderList().stream()
                    .anyMatch(h -> !h.getAllPictures().isEmpty());
            assertTrue(hayMembrete, "El membrete no quedó embebido en el encabezado");

            assertFalse(doc.getParagraphs().isEmpty(), "El documento no tiene contenido");
        }
    }

    /** Lee una entrada (por nombre) del .docx en memoria como texto UTF-8. */
    private String leerEntrada(byte[] docx, String nombre) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(docx))) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                if (e.getName().equals(nombre)) {
                    return new String(zis.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        }
        return "";
    }
}
