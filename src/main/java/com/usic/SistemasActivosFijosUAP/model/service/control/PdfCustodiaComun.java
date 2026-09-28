package com.usic.SistemasActivosFijosUAP.model.service.control;

import java.io.InputStream;

import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Document;
import com.itextpdf.text.Element;
import com.itextpdf.text.Font;
import com.itextpdf.text.Image;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.Rectangle;
import com.itextpdf.text.pdf.ColumnText;
import com.itextpdf.text.pdf.PdfContentByte;
import com.itextpdf.text.pdf.PdfPageEventHelper;
import com.itextpdf.text.pdf.PdfTemplate;
import com.itextpdf.text.pdf.PdfWriter;

import lombok.extern.slf4j.Slf4j;

/**
 * Lo que comparten los PDF de la custodia de faltantes: el membrete institucional (hoja
 * carta vertical) y un pie con quién lo generó y "página X de Y".
 */
@Slf4j
final class PdfCustodiaComun {

    static final String MEMBRETE = "/static/assets/img/fondo/0.jpg";

    /** Márgenes que dejan libre el encabezado y el pie del membrete. */
    static final float MARGEN_LADO = 50f;
    static final float MARGEN_ARRIBA = 106f;
    static final float PIE_MEMBRETE = 72f;

    static final BaseColor AZUL = new BaseColor(33, 52, 128);
    static final BaseColor GRIS_CLARO = new BaseColor(236, 239, 244);
    static final BaseColor GRIS_TEXTO = new BaseColor(90, 98, 110);
    static final BaseColor PREDIO = new BaseColor(88, 101, 140);

    private static final Font F_PIE = new Font(Font.FontFamily.HELVETICA, 7f, Font.NORMAL, GRIS_TEXTO);

    private PdfCustodiaComun() {}

    static Image cargarMembrete() {
        try (InputStream in = PdfCustodiaComun.class.getResourceAsStream(MEMBRETE)) {
            if (in == null) return null;
            return Image.getInstance(in.readAllBytes());
        } catch (Exception e) {
            log.warn("[CUSTODIA] No se pudo cargar el membrete: {}", e.getMessage());
            return null;
        }
    }

    /** Membrete de fondo en cada hoja y pie "texto · Página X de Y" sobre el pie del membrete. */
    static final class MembreteYPie extends PdfPageEventHelper {
        private final Image membrete;
        private final String pie;
        private PdfTemplate total;

        MembreteYPie(Image membrete, String pie) {
            this.membrete = membrete;
            this.pie = pie;
        }

        @Override
        public void onOpenDocument(PdfWriter writer, Document document) {
            total = writer.getDirectContent().createTemplate(30, 10);
        }

        @Override
        public void onStartPage(PdfWriter writer, Document document) {
            if (membrete == null) return;
            try {
                Rectangle hoja = document.getPageSize();
                membrete.scaleAbsolute(hoja.getWidth(), hoja.getHeight());
                membrete.setAbsolutePosition(0, 0);
                writer.getDirectContentUnder().addImage(membrete);
            } catch (Exception e) {
                // Sin membrete el reporte sigue siendo útil.
            }
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte cb = writer.getDirectContent();
            float y = PIE_MEMBRETE + 6;
            ColumnText.showTextAligned(cb, Element.ALIGN_LEFT, new Phrase(pie, F_PIE), document.left(), y, 0);
            String pagina = "Página " + writer.getPageNumber() + " de ";
            float ancho = F_PIE.getCalculatedBaseFont(false).getWidthPoint(pagina, F_PIE.getSize());
            float x = document.right() - ancho - 14;
            ColumnText.showTextAligned(cb, Element.ALIGN_LEFT, new Phrase(pagina, F_PIE), x, y, 0);
            cb.addTemplate(total, x + ancho, y);
        }

        @Override
        public void onCloseDocument(PdfWriter writer, Document document) {
            ColumnText.showTextAligned(total, Element.ALIGN_LEFT,
                    new Phrase(String.valueOf(writer.getPageNumber()), F_PIE), 0, 0, 0);
        }
    }
}
