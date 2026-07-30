package com.usic.SistemasActivosFijosUAP.controller.formularios;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;

import org.apache.poi.util.Units;
import org.apache.poi.wp.usermodel.HeaderFooterType;
import org.apache.poi.xwpf.usermodel.Document;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.UnderlinePatterns;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFHeader;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.apache.poi.xwpf.usermodel.XWPFTable.XWPFBorderType;
import org.openxmlformats.schemas.drawingml.x2006.main.CTNonVisualDrawingProps;
import org.openxmlformats.schemas.drawingml.x2006.main.CTPoint2D;
import org.openxmlformats.schemas.drawingml.x2006.main.CTPositiveSize2D;
import org.openxmlformats.schemas.drawingml.x2006.wordprocessingDrawing.CTAnchor;
import org.openxmlformats.schemas.drawingml.x2006.wordprocessingDrawing.CTEffectExtent;
import org.openxmlformats.schemas.drawingml.x2006.wordprocessingDrawing.CTPosH;
import org.openxmlformats.schemas.drawingml.x2006.wordprocessingDrawing.CTPosV;
import org.openxmlformats.schemas.drawingml.x2006.wordprocessingDrawing.STRelFromH;
import org.openxmlformats.schemas.drawingml.x2006.wordprocessingDrawing.STRelFromV;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBody;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTbl;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblGrid;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblLayoutType;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblWidth;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTcPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTblLayoutType;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTblWidth;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.usic.SistemasActivosFijosUAP.model.IService.IResponsableEntregaService;
import com.usic.SistemasActivosFijosUAP.model.entity.Activo;
import com.usic.SistemasActivosFijosUAP.model.entity.AsignacionActivo;
import com.usic.SistemasActivosFijosUAP.model.entity.ConfiguracionGestion;
import com.usic.SistemasActivosFijosUAP.model.entity.DetalleAsignacionActivo;
import com.usic.SistemasActivosFijosUAP.model.entity.Oficina;

/**
 * Genera el "ACTA DE ASIGNACIÓN" en formato Word (.docx) editable.
 * Réplica del PDF {@link PdfAsignacionActivoCompleto} pero modificable por el usuario.
 * El membrete institucional se coloca como imagen de fondo (detrás del texto) en el
 * encabezado, repitiéndose en cada página, sin afectar la edición del contenido.
 */
@Service
public class WordAsignacionActivoService {

    private static final String LOGO_PATH = "/static/assets/img/fondo/0.jpg";
    private static final String FUENTE = "Arial";

    @Autowired
    private IResponsableEntregaService responsableEntregaService;

    // Carta: 8.5" x 11" en twips (1" = 1440 twips)
    private static final int PAGINA_ANCHO = 12240;
    private static final int PAGINA_ALTO = 15840;
    // Ancho útil = ancho de página - márgenes izq/der (1440 c/u)
    private static final int CONTENIDO_ANCHO = PAGINA_ANCHO - 1440 - 1440; // 9360

    /** Sobrecarga sin usuario: mantiene compatibilidad (p.ej. tests). */
    public byte[] generarActaAsignacion(AsignacionActivo asignacion, ConfiguracionGestion config) throws IOException {
        return generarActaAsignacion(asignacion, config, null);
    }

    /**
     * @param nombreUsuario nombre completo del usuario que genera el acta; de él se
     *                      derivan las iniciales del pie (ej. "KCR/A-F").
     */
    public byte[] generarActaAsignacion(AsignacionActivo asignacion, ConfiguracionGestion config, String nombreUsuario) throws IOException {

        try (XWPFDocument doc = new XWPFDocument();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            // ── 1. TÍTULO ───────────────────────────────────────────────
            XWPFParagraph titulo = doc.createParagraph();
            titulo.setAlignment(ParagraphAlignment.CENTER);
            titulo.setSpacingAfter(300);
            XWPFRun rTit = titulo.createRun();
            rTit.setFontFamily(FUENTE);
            rTit.setFontSize(13);
            rTit.setBold(true);
            rTit.setUnderline(UnderlinePatterns.SINGLE);
            rTit.setText("ACTA DE ASIGNACIÓN INDIVIDUAL");
            rTit.addBreak();
            rTit.setText("DE BIENES NUEVOS-" + config.getGestion());

            // ── 2. PÁRRAFO DE INTRODUCCIÓN ──────────────────────────────
            String fechaLit = obtenerFechaLiteral(asignacion.getFechaAsignacion());
            String horaLit = asignacion.getFechaAsignacion().format(DateTimeFormatter.ofPattern("HH:mm a"));
            String nombreReceptor = asignacion.getResponsable().getPersona().getNombreCompleto();

            String responsableActivos = (config.getResponsableActivosNombre() != null && !config.getResponsableActivosNombre().isBlank())
                ? config.getResponsableActivosNombre() : "Lic. Verónica Layme Cori";
        var entrSel = responsableEntregaService.findSeleccionado();
        String responsableEntrega;
        String entregaArticulo;
        if (entrSel != null) {
            responsableEntrega = entrSel.getNombre();
            String lic = responsableEntrega.startsWith("Lic.") ? "" : "Lic. ";
            entregaArticulo = "M".equals(entrSel.getGenero()) ? "al " + lic : "a la " + lic;
        } else {
            responsableEntrega = (config.getResponsableEntregaRef() != null)
                    ? config.getResponsableEntregaRef().getNombre()
                    : ((config.getResponsableEntrega() != null && !config.getResponsableEntrega().isBlank())
                        ? config.getResponsableEntrega() : "Lic. Ruth Yoryina Espejo Cartagena");
            entregaArticulo = "a la ";
        }

        XWPFParagraph intro = doc.createParagraph();
            intro.setAlignment(ParagraphAlignment.BOTH);
            intro.setSpacingAfter(160);
            run(intro, "En la ciudad de " + config.getCiudad() + " a los " + fechaLit + ", a horas " + horaLit
                    + " en los predios de la Universidad Amazónica de Pando en presencia de la ", false, 10);
            run(intro, responsableActivos, true, 10);
            run(intro, " Responsable de Activos Fijos dando el visto bueno " + entregaArticulo, false, 10);
            run(intro, responsableEntrega, true, 10);
            run(intro, ", se procedió a la ", false, 10);
            run(intro, "ASIGNACIÓN", true, 10);
            run(intro, " de los Activos Fijos de acuerdo a las siguientes características:", false, 10);

            // ── 3. NÚMERO / CÓDIGO DEL DOCUMENTO ────────────────────────
            String textoCodigo = (asignacion.getCodigoCompleto() != null && !asignacion.getCodigoCompleto().isBlank())
                    ? asignacion.getCodigoCompleto()
                    : "-";
            XWPFParagraph pCod = doc.createParagraph();
            pCod.setSpacingAfter(120);
            run(pCod, textoCodigo, true, 10);

            // ── 4. TABLA DE ACTIVOS ─────────────────────────────────────
            int[] anchos = {
                    pct(7), pct(43), pct(22), pct(19), pct(9)
            };
            XWPFTable table = doc.createTable(1, 5);
            configurarTabla(table, anchos);

            XWPFTableRow head = table.getRow(0);
            celda(head.getCell(0), "Item", true, ParagraphAlignment.CENTER, 8, "D9D9D9");
            celda(head.getCell(1), "Descripción", true, ParagraphAlignment.CENTER, 8, "D9D9D9");
            celda(head.getCell(2), "Ubicación", true, ParagraphAlignment.CENTER, 8, "D9D9D9");
            celda(head.getCell(3), "Código", true, ParagraphAlignment.CENTER, 8, "D9D9D9");
            celda(head.getCell(4), "Estado", true, ParagraphAlignment.CENTER, 8, "D9D9D9");

            int item = 1;
            for (DetalleAsignacionActivo det : asignacion.getDetalles()) {
                Activo a = det.getActivo();
                String codigoVisual = "148-" + a.getCodigo();
                String ubicacion = ubicacionConCodigo(a.getOficina());

                XWPFTableRow fila = table.createRow();
                anchosFila(fila, anchos);
                celda(fila.getCell(0), String.valueOf(item++), false, ParagraphAlignment.CENTER, 8, null);
                celda(fila.getCell(1), a.getDescripcion(), false, ParagraphAlignment.LEFT, 8, null);
                celda(fila.getCell(2), ubicacion, false, ParagraphAlignment.CENTER, 8, null);
                celda(fila.getCell(3), codigoVisual, false, ParagraphAlignment.CENTER, 8, null);
                celda(fila.getCell(4), "NUEVO", false, ParagraphAlignment.CENTER, 8, null);
            }

            // ── 5. DATOS DEL RESPONSABLE ────────────────────────────────
            XWPFParagraph pAl = doc.createParagraph();
            pAl.setSpacingBefore(100);
            run(pAl, "Al:", false, 10);

            XWPFParagraph pResp = doc.createParagraph();
            pResp.setAlignment(ParagraphAlignment.CENTER);
            pResp.setSpacingAfter(200);
            run(pResp, nombreReceptor + "C.I: " + asignacion.getResponsable().getPersona().getCi(), false, 8).addBreak();
            String cargo = (asignacion.getResponsable().getCargo() != null)
                    ? asignacion.getResponsable().getCargo().getNombre() : "";
            run(pResp, cargo, false, 8);

            // ── 6. PÁRRAFO LEGAL ────────────────────────────────────────
            XWPFParagraph legal = doc.createParagraph();
            legal.setAlignment(ParagraphAlignment.BOTH);
            legal.setSpacingAfter(200);
            run(legal, "Entrega que es realizada de acuerdo al reglamentos y Normas Básicas del SABS y Decreto "
                    + "Supremo 0181 que a la letra dice, ", false, 10);
            run(legal, "es de responsabilidad total de la persona y/o funcionario, que recibe el activo, por "
                    + "cualquier pérdida, deterioro y/o por mal uso de los Bienes de la institución.", true, 10);

            XWPFParagraph constancia = doc.createParagraph();
            constancia.setAlignment(ParagraphAlignment.BOTH);
            constancia.setSpacingAfter(400);
            run(constancia, "Para constancia de la recepción firmamos al pie del presente documento.", false, 10);

            // ── 7. FIRMAS (tabla sin bordes) ────────────────────────────
            XWPFTable tFirmas = doc.createTable(1, 3);
            configurarTabla(tFirmas, new int[]{pct(33), pct(34), pct(33)});
            quitarBordes(tFirmas);
            String linea = "\n\n______________________\n";
            XWPFTableRow filaFirmas = tFirmas.getRow(0);
            celda(filaFirmas.getCell(0), linea + "RECIBE CONFORME", true, ParagraphAlignment.CENTER, 8, null);
            celda(filaFirmas.getCell(1), linea + "Vo.Bo.", true, ParagraphAlignment.CENTER, 8, null);
            celda(filaFirmas.getCell(2), linea + "ENTREGUE CONFORME", true, ParagraphAlignment.CENTER, 8, null);

            // ── 8. PIE PEQUEÑO (inferior izquierda, debajo del primer pie de firma) ──
            XWPFParagraph pie = doc.createParagraph();
            pie.setAlignment(ParagraphAlignment.LEFT);
            pie.setSpacingBefore(200);
            run(pie, "C.c/Arch.", false, 6).addBreak();
            run(pie, iniciales(nombreUsuario) + "/A-F", false, 6);

            // ── 9. MEMBRETE DE FONDO + CONFIGURACIÓN DE PÁGINA ──────────
            // (al final: el sectPr debe quedar como último elemento del body)
            agregarMembrete(doc);
            configurarPagina(doc);

            doc.write(baos);
            return baos.toByteArray();
        }
    }

    // ════════════════════════════════════════════════════════════════
    // HELPERS
    // ════════════════════════════════════════════════════════════════

    /** Crea un run con la fuente y tamaño estándar. */
    private XWPFRun run(XWPFParagraph p, String text, boolean bold, int size) {
        XWPFRun r = p.createRun();
        r.setFontFamily(FUENTE);
        r.setFontSize(size);
        r.setBold(bold);
        r.setText(text);
        return r;
    }

    /** Convierte un porcentaje del ancho útil a twips. */
    private int pct(int porcentaje) {
        return Math.round(CONTENIDO_ANCHO * porcentaje / 100f);
    }

    /** Aplica layout fijo, ancho total y anchos de columna a una tabla. */
    private void configurarTabla(XWPFTable table, int[] anchos) {
        CTTbl ctTbl = table.getCTTbl();
        CTTblPr tblPr = ctTbl.getTblPr() != null ? ctTbl.getTblPr() : ctTbl.addNewTblPr();

        CTTblWidth tblW = tblPr.isSetTblW() ? tblPr.getTblW() : tblPr.addNewTblW();
        tblW.setType(STTblWidth.DXA);
        tblW.setW(BigInteger.valueOf(CONTENIDO_ANCHO));

        CTTblLayoutType layout = tblPr.isSetTblLayout() ? tblPr.getTblLayout() : tblPr.addNewTblLayout();
        layout.setType(STTblLayoutType.FIXED);

        CTTblGrid grid = ctTbl.getTblGrid() != null ? ctTbl.getTblGrid() : ctTbl.addNewTblGrid();
        for (int i = 0; i < anchos.length; i++) {
            if (grid.sizeOfGridColArray() <= i) grid.addNewGridCol();
            grid.getGridColArray(i).setW(BigInteger.valueOf(anchos[i]));
        }
        anchosFila(table.getRow(0), anchos);
    }

    /** Fija el ancho de cada celda de una fila. */
    private void anchosFila(XWPFTableRow fila, int[] anchos) {
        for (int i = 0; i < anchos.length && i < fila.getTableCells().size(); i++) {
            XWPFTableCell cell = fila.getCell(i);
            CTTcPr tcPr = cell.getCTTc().getTcPr() != null ? cell.getCTTc().getTcPr() : cell.getCTTc().addNewTcPr();
            CTTblWidth w = tcPr.isSetTcW() ? tcPr.getTcW() : tcPr.addNewTcW();
            w.setType(STTblWidth.DXA);
            w.setW(BigInteger.valueOf(anchos[i]));
        }
    }

    /** Escribe el contenido de una celda (soporta saltos de línea con \n). */
    private void celda(XWPFTableCell cell, String text, boolean bold, ParagraphAlignment align, int size, String bgHex) {
        XWPFParagraph p = (cell.getParagraphArray(0) != null) ? cell.getParagraphArray(0) : cell.addParagraph();
        p.setAlignment(align);
        p.setSpacingAfter(0);
        XWPFRun r = p.createRun();
        r.setFontFamily(FUENTE);
        r.setFontSize(size);
        r.setBold(bold);
        String safe = (text != null) ? text : "";
        String[] lineas = safe.split("\n", -1);
        for (int i = 0; i < lineas.length; i++) {
            if (i > 0) r.addBreak();
            r.setText(lineas[i]);
        }
        if (bgHex != null) cell.setColor(bgHex);
    }

    /** Elimina todos los bordes de una tabla (usado para la zona de firmas). */
    private void quitarBordes(XWPFTable table) {
        table.setTopBorder(XWPFBorderType.NONE, 0, 0, "FFFFFF");
        table.setBottomBorder(XWPFBorderType.NONE, 0, 0, "FFFFFF");
        table.setLeftBorder(XWPFBorderType.NONE, 0, 0, "FFFFFF");
        table.setRightBorder(XWPFBorderType.NONE, 0, 0, "FFFFFF");
        table.setInsideHBorder(XWPFBorderType.NONE, 0, 0, "FFFFFF");
        table.setInsideVBorder(XWPFBorderType.NONE, 0, 0, "FFFFFF");
    }

    /** Tamaño de página Carta y márgenes que dejan espacio al membrete. */
    private void configurarPagina(XWPFDocument doc) {
        CTBody body = doc.getDocument().getBody();
        CTSectPr sectPr = body.isSetSectPr() ? body.getSectPr() : body.addNewSectPr();

        CTPageSz pageSz = sectPr.isSetPgSz() ? sectPr.getPgSz() : sectPr.addNewPgSz();
        pageSz.setW(BigInteger.valueOf(PAGINA_ANCHO));
        pageSz.setH(BigInteger.valueOf(PAGINA_ALTO));

        CTPageMar mar = sectPr.isSetPgMar() ? sectPr.getPgMar() : sectPr.addNewPgMar();
        mar.setTop(BigInteger.valueOf(2160));    // ~1.5" — bajo el banner superior
        mar.setBottom(BigInteger.valueOf(1872));  // ~1.3" — sobre el pie institucional
        mar.setLeft(BigInteger.valueOf(1440));    // 1"
        mar.setRight(BigInteger.valueOf(1440));   // 1"
        mar.setHeader(BigInteger.valueOf(0));
        mar.setFooter(BigInteger.valueOf(0));
    }

    /**
     * Inserta el membrete a página completa, detrás del texto, en el encabezado
     * (se repite en todas las páginas). Si algo falla, el documento se genera sin
     * el fondo en lugar de corromperse.
     */
    private void agregarMembrete(XWPFDocument doc) {
        try (InputStream is = getClass().getResourceAsStream(LOGO_PATH)) {
            if (is == null) {
                System.err.println("Membrete no encontrado en: " + LOGO_PATH);
                return;
            }
            byte[] imagen = is.readAllBytes();

            XWPFHeader header = doc.createHeader(HeaderFooterType.DEFAULT);
            XWPFParagraph p = header.createParagraph();
            XWPFRun run = p.createRun();

            int cx = Units.toEMU(612); // 8.5" en puntos -> EMU
            int cy = Units.toEMU(792); // 11"  en puntos -> EMU

            // Se inserta primero como imagen "inline" para que POI cree la relación
            // y el gráfico; luego se convierte en un anclaje detrás del texto.
            run.addPicture(new ByteArrayInputStream(imagen), Document.PICTURE_TYPE_JPEG, "membrete.jpg", cx, cy);

            // Convertir la imagen "inline" en un anclaje DETRÁS DEL TEXTO a página completa.
            // Se construye el anclaje con la API tipada (no por string) creándolo directamente
            // dentro del dibujo: así el XML queda bien formado y sin elementos anidados.
            var drawing = run.getCTR().getDrawingArray(0);
            var inline = drawing.getInlineArray(0);

            CTAnchor anchor = drawing.addNewAnchor();
            anchor.setBehindDoc(true);
            anchor.setLocked(false);
            anchor.setLayoutInCell(true);
            anchor.setAllowOverlap(true);
            anchor.setRelativeHeight(0);
            anchor.setSimplePos2(false);   // atributo REQUERIDO del anchor (Word lo exige)
            anchor.setDistT(0);
            anchor.setDistB(0);
            anchor.setDistL(0);
            anchor.setDistR(0);

            CTPoint2D simplePos = anchor.addNewSimplePos();   // elemento <wp:simplePos>
            simplePos.setX(0);
            simplePos.setY(0);

            CTPosH posH = anchor.addNewPositionH();
            posH.setRelativeFrom(STRelFromH.PAGE);
            posH.setPosOffset(0);

            CTPosV posV = anchor.addNewPositionV();
            posV.setRelativeFrom(STRelFromV.PAGE);
            posV.setPosOffset(0);

            CTPositiveSize2D extent = anchor.addNewExtent();
            extent.setCx(cx);
            extent.setCy(cy);

            CTEffectExtent ee = anchor.addNewEffectExtent();
            ee.setL(0);
            ee.setT(0);
            ee.setR(0);
            ee.setB(0);

            anchor.addNewWrapNone();

            CTNonVisualDrawingProps docPr = anchor.addNewDocPr();
            docPr.setId(100);
            docPr.setName("Membrete");

            anchor.addNewCNvGraphicFramePr();

            // Copiar el gráfico real de la imagen (conserva la relación r:embed)
            anchor.setGraphic(inline.getGraphic());

            // Quitar la versión inline; queda solo el anclaje de fondo
            drawing.removeInline(0);

        } catch (Exception e) {
            System.err.println("No se pudo agregar el membrete al Word: " + e.getMessage());
        }
    }

    /** "Nombre Oficina (OF. 124)" — nombre + código de la oficina. */
    private String ubicacionConCodigo(Oficina oficina) {
        if (oficina == null) return "S/N";
        String nombre = (oficina.getNombre() != null) ? oficina.getNombre() : "S/N";
        return (oficina.getCodOfi() != null)
                ? nombre + " (OF. " + oficina.getCodOfi() + ")"
                : nombre;
    }

    /**
     * Iniciales en mayúscula de cada palabra del nombre completo del usuario.
     * Ej: "Ruth Yoryina Espejo Cartagena" -> "RYEC".
     */
    private String iniciales(String nombreCompleto) {
        if (nombreCompleto == null || nombreCompleto.isBlank()) return "";
        StringBuilder sb = new StringBuilder();
        for (String parte : nombreCompleto.trim().split("\\s+")) {
            if (!parte.isEmpty()) sb.append(Character.toUpperCase(parte.charAt(0)));
        }
        return sb.toString();
    }

    private String obtenerFechaLiteral(LocalDateTime fecha) {
        int dia = fecha.getDayOfMonth();
        String mes = fecha.getMonth().getDisplayName(TextStyle.FULL, new Locale("es", "ES"));
        int anio = fecha.getYear();
        return dia + " días del mes de " + mes + " del " + anio;
    }
}
