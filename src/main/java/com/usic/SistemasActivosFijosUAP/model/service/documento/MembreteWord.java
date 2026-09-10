package com.usic.SistemasActivosFijosUAP.model.service.documento;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import org.apache.poi.util.Units;
import org.apache.poi.wp.usermodel.HeaderFooterType;
import org.apache.poi.xwpf.usermodel.Document;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFHeader;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.openxmlformats.schemas.drawingml.x2006.main.CTNonVisualDrawingProps;
import org.openxmlformats.schemas.drawingml.x2006.main.CTPoint2D;
import org.openxmlformats.schemas.drawingml.x2006.main.CTPositiveSize2D;
import org.openxmlformats.schemas.drawingml.x2006.wordprocessingDrawing.CTAnchor;
import org.openxmlformats.schemas.drawingml.x2006.wordprocessingDrawing.CTEffectExtent;
import org.openxmlformats.schemas.drawingml.x2006.wordprocessingDrawing.CTPosH;
import org.openxmlformats.schemas.drawingml.x2006.wordprocessingDrawing.CTPosV;
import org.openxmlformats.schemas.drawingml.x2006.wordprocessingDrawing.STRelFromH;
import org.openxmlformats.schemas.drawingml.x2006.wordprocessingDrawing.STRelFromV;

/**
 * Membrete institucional a página completa, detrás del texto, para los documentos Word
 * del sistema.
 *
 * <p>Vive acá y no adentro de cada servicio porque son ~50 líneas de manipulación fina
 * del XML de OOXML que ya estaban duplicándose: cada informe nuevo que necesitara el
 * membrete iba a traer otra copia, y cualquier corrección habría que hacerla en todas.
 * El código se movió tal cual desde {@code WordAsignacionActivoService}, que lo venía
 * usando en producción para las actas de asignación.
 */
public final class MembreteWord {

    /** El membrete institucional dentro del classpath. */
    public static final String LOGO_PATH = "/static/assets/img/fondo/0.jpg";

    private MembreteWord() { }

    /**
     * Inserta el membrete a página completa, detrás del texto, en el encabezado
     * (se repite en todas las páginas).
     *
     * <p>Si algo falla —no está la imagen, el XML no coopera— el documento se genera
     * <em>sin</em> el fondo en lugar de romperse: quedarse sin poder emitir el papel
     * por un elemento decorativo sería peor que emitirlo sin él.
     */
    public static void aplicar(XWPFDocument doc) {
        try (InputStream is = MembreteWord.class.getResourceAsStream(LOGO_PATH)) {
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
}
