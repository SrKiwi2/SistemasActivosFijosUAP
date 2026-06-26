package com.usic.SistemasActivosFijosUAP.model.service.interno;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigInteger;
import java.util.List;

import org.apache.poi.wp.usermodel.HeaderFooterType;
import org.apache.poi.xwpf.usermodel.Document;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFHeader;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.apache.xmlbeans.XmlCursor;
import org.apache.xmlbeans.XmlObject;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import com.usic.SistemasActivosFijosUAP.model.dto.ActivoTransferenciaDTO;
import com.usic.SistemasActivosFijosUAP.model.entity.Responsable;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;

import lombok.extern.slf4j.Slf4j;

/**
 * Genera el "Acta de Transferencia de Bienes" en formato Word (.docx) con Apache POI (XWPF).
 * Replica el contenido del antiguo comprobante PDF: carta horizontal, membrete de fondo,
 * tabla TRANSFIERE / RECIBE Y RESGUARDA, tabla de activos, estado físico y nota legal.
 */
@Slf4j
@Service
public class WordInternoTransferenciaService {

    /** Carta horizontal en twips (1 pulgada = 1440 twips): 11" x 8.5". */
    private static final int PAGE_W_TWIPS = 15840;
    private static final int PAGE_H_TWIPS = 12240;
    /** Mismo tamaño en EMU (1 pulgada = 914400 EMU) para la imagen de fondo a página completa. */
    private static final long PAGE_W_EMU = 10058400L;
    private static final long PAGE_H_EMU = 7772400L;

    /** Ancho útil = página - márgenes izq/der. */
    private static final int CONTENT_W_TWIPS = 14640;
    private static final String AMBER = "FBE9A1";

    public byte[] wordTransferenciaActivo(
            Usuario usuario, String unidadOrigen, Responsable responsableOrigen, String fechaTransferencia,
            String unidadDestino, Responsable responsableDestino, String fechaRecepcion,
            List<ActivoTransferenciaDTO> activos) throws Exception {

        try (XWPFDocument doc = new XWPFDocument();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            // ── 1. Encabezado de texto ──
            addCentro(doc, "UNIVERSIDAD AMAZÓNICA DE PANDO", 11, false);
            addCentro(doc, "SECCIÓN DE ACTIVOS FIJOS", 11, false);
            addCentro(doc, "TRANSFERENCIA DE BIENES", 12, true);

            espacio(doc);

            // ── 2. Tabla TRANSFIERE / RECIBE Y RESGUARDA ──
            construirTablaDatos(doc, unidadOrigen, unidadDestino,
                    nombre(responsableOrigen), nombre(responsableDestino),
                    fechaTransferencia, fechaRecepcion);

            espacio(doc);

            // ── 3. Tabla de activos ──
            construirTablaActivos(doc, activos);

            espacio(doc);

            // ── 4. Estado físico del bien ──
            XWPFParagraph estado = doc.createParagraph();
            XWPFRun rEstado = estado.createRun();
            rEstado.setFontFamily("Arial");
            rEstado.setFontSize(8);
            rEstado.setBold(true);
            rEstado.setText("ESTADO FÍSICO DEL BIEN:        a) BUEN ESTADO              b) REGULAR"
                    + "              c) MAL ESTADO           d) INCOMPLETO");

            // ── 5. Nota legal ──
            XWPFParagraph nota = doc.createParagraph();
            nota.setAlignment(ParagraphAlignment.BOTH);
            nota.setSpacingBefore(120);
            XWPFRun rNota = nota.createRun();
            rNota.setFontFamily("Arial");
            rNota.setFontSize(7);
            rNota.setBold(true);
            rNota.setText("NOTA: A PARTIR DE LA FECHA QUEDA COMO RESPONSABLE DE TODOS LOS ITEMS QUE SE DETALLAN EN EL ACTA, "
                    + "CUALQUIER PERDIDA, DESTRUCCION O MALTRATO QUE PUEDA SUFRIR SERA IMPUTADA DIRECTAMENTE A SU PERSONA, MIENTRAS NO DEMUESTRE LO CONTRARIO. "
                    + "* Queda prohibida la transferencia de bienes de un servidor a otro sin la participación de la Unidad de Activos Fijos de la Universidad Amazónica de Pando. "
                    + "La contravención dará lugar a posible responsabilidad administrativa, civil y penal. De acuerdo al DS 0181 Art. 146 (Asignación de activos fijos Muebles) I. "
                    + "La asignación de activos fijos muebles es el acto administrativo mediante el cual se entrega a un servidor público un activo o conjunto de éstos, generando la consiguiente responsabilidad sobre su debido uso y custodia.");

            // ── 6. Membrete de fondo a página completa (best-effort, no rompe si falla) ──
            agregarFondoPagina(doc);

            // ── 7. Tamaño de página (carta horizontal) + márgenes que dejan ver el membrete ──
            configurarPagina(doc);

            doc.write(baos);
            return baos.toByteArray();
        }
    }

    /* ───────────────────────── Página y fondo ───────────────────────── */

    private void configurarPagina(XWPFDocument doc) {
        CTBody body = doc.getDocument().getBody();
        CTSectPr sectPr = body.isSetSectPr() ? body.getSectPr() : body.addNewSectPr();

        CTPageSz sz = sectPr.isSetPgSz() ? sectPr.getPgSz() : sectPr.addNewPgSz();
        sz.setOrient(STPageOrientation.LANDSCAPE);
        sz.setW(BigInteger.valueOf(PAGE_W_TWIPS));
        sz.setH(BigInteger.valueOf(PAGE_H_TWIPS));

        CTPageMar mar = sectPr.isSetPgMar() ? sectPr.getPgMar() : sectPr.addNewPgMar();
        mar.setTop(BigInteger.valueOf(1500));    // libra la banda superior del membrete
        mar.setBottom(BigInteger.valueOf(1400));  // libra el pie del membrete
        mar.setLeft(BigInteger.valueOf(600));
        mar.setRight(BigInteger.valueOf(600));
        mar.setHeader(BigInteger.ZERO);
        mar.setFooter(BigInteger.ZERO);
        mar.setGutter(BigInteger.ZERO);
    }

    private void agregarFondoPagina(XWPFDocument doc) {
        try (InputStream is = new ClassPathResource(
                "static/assets/img/fondo/membreta-horizontal-uap.jpg").getInputStream()) {

            byte[] fondo = is.readAllBytes();

            XWPFHeader header = doc.createHeader(HeaderFooterType.DEFAULT);
            XWPFParagraph hp = header.createParagraph();
            XWPFRun hr = hp.createRun();
            String rId = header.addPictureData(fondo, Document.PICTURE_TYPE_JPEG);

            String anchorXml =
                "<wp:anchor xmlns:wp=\"http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing\" "
                + "behindDoc=\"1\" distT=\"0\" distB=\"0\" distL=\"0\" distR=\"0\" simplePos=\"0\" "
                + "locked=\"0\" layoutInCell=\"1\" allowOverlap=\"1\" relativeHeight=\"0\">"
                + "<wp:simplePos x=\"0\" y=\"0\"/>"
                + "<wp:positionH relativeFrom=\"page\"><wp:posOffset>0</wp:posOffset></wp:positionH>"
                + "<wp:positionV relativeFrom=\"page\"><wp:posOffset>0</wp:posOffset></wp:positionV>"
                + "<wp:extent cx=\"" + PAGE_W_EMU + "\" cy=\"" + PAGE_H_EMU + "\"/>"
                + "<wp:effectExtent l=\"0\" t=\"0\" r=\"0\" b=\"0\"/>"
                + "<wp:wrapNone/>"
                + "<wp:docPr id=\"1\" name=\"membrete\"/>"
                + "<wp:cNvGraphicFramePr/>"
                + "<a:graphic xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\">"
                + "<a:graphicData uri=\"http://schemas.openxmlformats.org/drawingml/2006/picture\">"
                + "<pic:pic xmlns:pic=\"http://schemas.openxmlformats.org/drawingml/2006/picture\">"
                + "<pic:nvPicPr><pic:cNvPr id=\"1\" name=\"membrete\"/><pic:cNvPicPr/></pic:nvPicPr>"
                + "<pic:blipFill>"
                + "<a:blip xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\" r:embed=\""
                + rId + "\"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill>"
                + "<pic:spPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"" + PAGE_W_EMU + "\" cy=\"" + PAGE_H_EMU
                + "\"/></a:xfrm><a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></pic:spPr>"
                + "</pic:pic></a:graphicData></a:graphic></wp:anchor>";

            // Copiamos el <wp:anchor> parseado DENTRO del <w:drawing> con un cursor.
            // (No usar setAnchorArray/.set(): anidan el anchor dentro de otro anchor.)
            var drawing = hr.getCTR().addNewDrawing();
            XmlObject anchorObj = XmlObject.Factory.parse(anchorXml);
            XmlCursor src = anchorObj.newCursor();
            src.toNextToken();                 // posiciona en el <wp:anchor>
            XmlCursor dst = drawing.newCursor();
            dst.toEndToken();                  // dentro del <w:drawing>, antes de cerrarlo
            src.copyXml(dst);
            src.dispose();
            dst.dispose();

        } catch (Exception e) {
            log.warn("[WORD-TRANSF] No se pudo aplicar el membrete de fondo: {}", e.getMessage());
        }
    }

    /* ───────────────────────── Tablas ───────────────────────── */

    private void construirTablaDatos(XWPFDocument doc, String unidadOrigen, String unidadDestino,
                                     String nombreOrigen, String nombreDestino,
                                     String fechaTransferencia, String fechaRecepcion) {
        XWPFTable t = doc.createTable();
        prepararTabla(t, new int[]{ 2815, 4505, 2815, 4505 });

        // Fila 1: encabezados combinados (cada uno ocupa 2 columnas)
        XWPFTableRow r0 = filaConCeldas(t, 2, true);
        celda(r0.getCell(0), "TRANSFIERE", false, 9, ParagraphAlignment.CENTER);
        spanYsombra(r0.getCell(0), 2, AMBER, 7320);
        celda(r0.getCell(1), "RECIBE Y RESGUARDA", false, 9, ParagraphAlignment.CENTER);
        spanYsombra(r0.getCell(1), 2, AMBER, 7320);

        // Fila 2: dirección / unidad
        XWPFTableRow r1 = filaConCeldas(t, 4, false);
        celda(r1.getCell(0), " DIRECCION, UNIDAD Y/O SECCION: ", true, 7, ParagraphAlignment.LEFT);
        celda(r1.getCell(1), " " + nvl(unidadOrigen), false, 7, ParagraphAlignment.LEFT);
        celda(r1.getCell(2), " DIRECCION, UNIDAD Y/O SECCION: ", true, 7, ParagraphAlignment.LEFT);
        celda(r1.getCell(3), " " + nvl(unidadDestino), false, 7, ParagraphAlignment.LEFT);

        // Fila 3: firma inmediato superior
        XWPFTableRow r2 = filaConCeldas(t, 4, false);
        celda(r2.getCell(0), " FIRMA DEL INMEDIATO SUPERIOR: ", true, 7, ParagraphAlignment.LEFT);
        celda(r2.getCell(1), "", false, 7, ParagraphAlignment.LEFT);
        celda(r2.getCell(2), " FIRMA DEL INMEDIATO SUPERIOR: ", true, 7, ParagraphAlignment.LEFT);
        celda(r2.getCell(3), "", false, 7, ParagraphAlignment.LEFT);

        // Fila 4: nombre / pie de firma del responsable
        XWPFTableRow r3 = filaConCeldas(t, 4, false);
        celda(r3.getCell(0), " NOMBRE O PIE DE FIRMA DEL RESPONSABLE: ", true, 7, ParagraphAlignment.LEFT);
        celda(r3.getCell(1), " " + nvl(nombreOrigen), false, 7, ParagraphAlignment.LEFT);
        celda(r3.getCell(2), " NOMBRE O PIE DE FIRMA DEL RESPONSABLE: ", true, 7, ParagraphAlignment.LEFT);
        celda(r3.getCell(3), " " + nvl(nombreDestino), false, 7, ParagraphAlignment.LEFT);

        // Fila 5: firma del responsable
        XWPFTableRow r4 = filaConCeldas(t, 4, false);
        celda(r4.getCell(0), " FIRMA DEL RESPONSABLE: ", true, 7, ParagraphAlignment.LEFT);
        celda(r4.getCell(1), "", false, 7, ParagraphAlignment.LEFT);
        celda(r4.getCell(2), " FIRMA DEL RESPONSABLE: ", true, 7, ParagraphAlignment.LEFT);
        celda(r4.getCell(3), "", false, 7, ParagraphAlignment.LEFT);

        // Fila 6: fechas
        XWPFTableRow r5 = filaConCeldas(t, 4, false);
        celda(r5.getCell(0), " FECHA DE LA TRANSFERENCIA: ", true, 7, ParagraphAlignment.LEFT);
        celda(r5.getCell(1), " " + nvl(fechaTransferencia), false, 7, ParagraphAlignment.LEFT);
        celda(r5.getCell(2), " FECHA DE LA RECEPCIÓN: ", true, 7, ParagraphAlignment.LEFT);
        celda(r5.getCell(3), " " + nvl(fechaRecepcion), false, 7, ParagraphAlignment.LEFT);
    }

    private void construirTablaActivos(XWPFDocument doc, List<ActivoTransferenciaDTO> activos) {
        XWPFTable t = doc.createTable();
        prepararTabla(t, new int[]{ 1220, 2440, 3660, 3660, 3660 });

        XWPFTableRow head = filaConCeldas(t, 5, true);
        String[] cabeceras = { "Nº ITEM", "CÓDIGO", "DESCRIPCIÓN",
                "UBICACIÓN DE ORIGEN Y Nº DE OFICINA", "UBICACIÓN ACTUAL Y Nº DE OFICINA" };
        for (int c = 0; c < 5; c++) {
            celda(head.getCell(c), cabeceras[c], true, 6, ParagraphAlignment.CENTER);
            sombra(head.getCell(c), AMBER);
            head.getCell(c).setVerticalAlignment(XWPFTableCell.XWPFVertAlign.CENTER);
        }

        if (activos != null) {
            for (int i = 0; i < activos.size(); i++) {
                ActivoTransferenciaDTO dto = activos.get(i);
                XWPFTableRow fila = filaConCeldas(t, 5, false);
                celda(fila.getCell(0), String.valueOf(i + 1), false, 7, ParagraphAlignment.CENTER);
                celda(fila.getCell(1), nvl(dto.getCodigo()), false, 7, ParagraphAlignment.CENTER);
                celda(fila.getCell(2), nvl(dto.getDescripcion()), false, 7, ParagraphAlignment.CENTER);
                celda(fila.getCell(3), nvl(dto.getUbicacionOrigen()), false, 7, ParagraphAlignment.CENTER);
                celda(fila.getCell(4), nvl(dto.getUbicacionActual()), false, 7, ParagraphAlignment.CENTER);
            }
        }
    }

    /* ───────────────────────── Helpers POI ───────────────────────── */

    private void prepararTabla(XWPFTable t, int[] anchos) {
        CTTblPr tblPr = t.getCTTbl().getTblPr() != null ? t.getCTTbl().getTblPr() : t.getCTTbl().addNewTblPr();

        CTTblWidth tblW = tblPr.isSetTblW() ? tblPr.getTblW() : tblPr.addNewTblW();
        tblW.setType(STTblWidth.DXA);
        tblW.setW(BigInteger.valueOf(CONTENT_W_TWIPS));

        tblPr.addNewTblLayout().setType(STTblLayoutType.FIXED);

        // Bordes visibles
        CTTblBorders b = tblPr.addNewTblBorders();
        borde(b.addNewTop()); borde(b.addNewBottom()); borde(b.addNewLeft());
        borde(b.addNewRight()); borde(b.addNewInsideH()); borde(b.addNewInsideV());

        // Rejilla de columnas
        CTTblGrid grid = t.getCTTbl().getTblGrid();
        if (grid == null) grid = t.getCTTbl().addNewTblGrid();
        for (int i = grid.sizeOfGridColArray() - 1; i >= 0; i--) grid.removeGridCol(i);
        for (int a : anchos) grid.addNewGridCol().setW(BigInteger.valueOf(a));
    }

    /** Devuelve una fila con exactamente {@code n} celdas. La primera fila reutiliza la fila por defecto. */
    private XWPFTableRow filaConCeldas(XWPFTable t, int n, boolean primera) {
        XWPFTableRow row = primera ? t.getRow(0) : t.createRow();
        while (row.getTableCells().size() < n) row.addNewTableCell();
        return row;
    }

    private void celda(XWPFTableCell cell, String texto, boolean negrita, int size, ParagraphAlignment align) {
        XWPFParagraph p = cell.getParagraphArray(0);
        if (p == null) p = cell.addParagraph();
        p.setAlignment(align == null ? ParagraphAlignment.LEFT : align);
        p.setSpacingAfter(0);
        for (int i = p.getRuns().size() - 1; i >= 0; i--) p.removeRun(i);
        XWPFRun r = p.createRun();
        r.setText(texto == null ? "" : texto);
        r.setBold(negrita);
        r.setFontFamily("Arial");
        r.setFontSize(size);
    }

    private void spanYsombra(XWPFTableCell cell, int span, String hex, int anchoTwips) {
        CTTcPr tcPr = tcPr(cell);
        tcPr.addNewGridSpan().setVal(BigInteger.valueOf(span));
        CTTblWidth w = tcPr.isSetTcW() ? tcPr.getTcW() : tcPr.addNewTcW();
        w.setType(STTblWidth.DXA);
        w.setW(BigInteger.valueOf(anchoTwips));
        CTShd shd = tcPr.isSetShd() ? tcPr.getShd() : tcPr.addNewShd();
        shd.setVal(STShd.CLEAR);
        shd.setFill(hex);
        cell.setVerticalAlignment(XWPFTableCell.XWPFVertAlign.CENTER);
    }

    private void sombra(XWPFTableCell cell, String hex) {
        CTTcPr tcPr = tcPr(cell);
        CTShd shd = tcPr.isSetShd() ? tcPr.getShd() : tcPr.addNewShd();
        shd.setVal(STShd.CLEAR);
        shd.setFill(hex);
    }

    private CTTcPr tcPr(XWPFTableCell cell) {
        return cell.getCTTc().isSetTcPr() ? cell.getCTTc().getTcPr() : cell.getCTTc().addNewTcPr();
    }

    private void borde(CTBorder border) {
        border.setVal(STBorder.SINGLE);
        border.setSz(BigInteger.valueOf(4));
        border.setColor("000000");
        border.setSpace(BigInteger.ZERO);
    }

    private void addCentro(XWPFDocument doc, String texto, int size, boolean negrita) {
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(ParagraphAlignment.CENTER);
        p.setSpacingAfter(0);
        XWPFRun r = p.createRun();
        r.setText(texto);
        r.setBold(negrita);
        r.setFontFamily("Times New Roman");
        r.setFontSize(size);
    }

    private void espacio(XWPFDocument doc) {
        XWPFParagraph p = doc.createParagraph();
        p.setSpacingAfter(0);
        p.createRun().setFontSize(4);
    }

    private String nombre(Responsable r) {
        return (r != null && r.getPersona() != null) ? r.getPersona().getNombreCompleto() : "—";
    }

    private String nvl(String s) {
        return (s == null || s.isBlank()) ? "—" : s;
    }
}
