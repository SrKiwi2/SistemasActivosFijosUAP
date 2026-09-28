package com.usic.SistemasActivosFijosUAP.model.service.control;

import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Document;
import com.itextpdf.text.Element;
import com.itextpdf.text.Font;
import com.itextpdf.text.Image;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.Rectangle;
import com.itextpdf.text.pdf.BarcodeQRCode;
import com.itextpdf.text.pdf.ColumnText;
import com.itextpdf.text.pdf.PdfContentByte;
import com.itextpdf.text.pdf.PdfGState;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfPageEventHelper;
import com.itextpdf.text.pdf.PdfTemplate;
import com.itextpdf.text.pdf.PdfWriter;
import com.usic.SistemasActivosFijosUAP.model.dto.control.ActaFaltanteDTO;
import com.usic.SistemasActivosFijosUAP.model.entity.ActaFaltante;

import lombok.extern.slf4j.Slf4j;

/**
 * PDF del acta de faltantes, para imprimir y firmar.
 * <p>
 * Hoja carta con el membrete institucional en cada página. Sobre el pie de cada hoja va
 * una franja de verificación: QR hacia la página pública del SCIAF, número, huella del
 * contenido y "página X de Y". Las firmas van abajo en la última hoja: la persona a la
 * izquierda (nombre, cargo y C.I.) y Activos Fijos a la derecha, para firma y sello.
 * <p>
 * Se arma siempre desde la foto guardada al emitir: reimprimir da el mismo documento.
 */
@Slf4j
@Service
public class PdfActaFaltanteService {

    /** Firma de la derecha. Cuando Activos Fijos defina sus datos, se cambian acá. */
    static final String FIRMA_DERECHA_TITULO = "SECCIÓN DE ACTIVOS FIJOS";
    static final String FIRMA_DERECHA_DETALLE = "Firma y sello";

    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // Márgenes que dejan libre el encabezado y el pie del membrete, más la franja de verificación.
    private static final float MARGEN_LADO = 50f;
    private static final float MARGEN_ARRIBA = 106f;
    private static final float PIE_MEMBRETE = 72f;
    private static final float FRANJA = 58f;
    private static final float MARGEN_ABAJO = PIE_MEMBRETE + FRANJA;
    private static final float ALTO_FIRMAS = 90f;

    private static final BaseColor AZUL = new BaseColor(33, 52, 128);
    private static final BaseColor GRIS_CLARO = new BaseColor(236, 239, 244);
    private static final BaseColor GRIS_TEXTO = new BaseColor(90, 98, 110);

    private static final Font F_TITULO   = new Font(Font.FontFamily.HELVETICA, 14, Font.BOLD, AZUL);
    private static final Font F_SUB      = new Font(Font.FontFamily.HELVETICA, 9, Font.NORMAL, GRIS_TEXTO);
    private static final Font F_NUMERO   = new Font(Font.FontFamily.HELVETICA, 10, Font.BOLD);
    private static final Font F_ETIQ     = new Font(Font.FontFamily.HELVETICA, 8, Font.BOLD, GRIS_TEXTO);
    private static final Font F_DATO     = new Font(Font.FontFamily.HELVETICA, 9);
    private static final Font F_TEXTO    = new Font(Font.FontFamily.HELVETICA, 9);
    private static final Font F_CAB      = new Font(Font.FontFamily.HELVETICA, 8, Font.BOLD, BaseColor.WHITE);
    private static final Font F_PREDIO   = new Font(Font.FontFamily.HELVETICA, 8.5f, Font.BOLD, BaseColor.WHITE);
    private static final Font F_OFICINA  = new Font(Font.FontFamily.HELVETICA, 8.5f, Font.BOLD);
    private static final Font F_CELDA    = new Font(Font.FontFamily.HELVETICA, 8);
    private static final Font F_CODIGO   = new Font(Font.FontFamily.COURIER, 8, Font.BOLD);
    private static final Font F_TOTAL    = new Font(Font.FontFamily.HELVETICA, 9, Font.BOLD);
    private static final Font F_FIRMA    = new Font(Font.FontFamily.HELVETICA, 8.5f, Font.BOLD);
    private static final Font F_FIRMA_D  = new Font(Font.FontFamily.HELVETICA, 8);
    private static final Font F_FRANJA   = new Font(Font.FontFamily.HELVETICA, 6.8f, Font.NORMAL, GRIS_TEXTO);
    private static final Font F_FRANJA_B = new Font(Font.FontFamily.HELVETICA, 6.8f, Font.BOLD, GRIS_TEXTO);
    private static final Font F_ANULADA  = new Font(Font.FontFamily.HELVETICA, 10, Font.BOLD, new BaseColor(176, 32, 45));

    @Value("${sciaf.url.publica:https://sciaf.uap.edu.bo}")
    private String urlPublica;

    /** Dirección que va en el QR. */
    public String urlVerificacion(String token) {
        String base = urlPublica.endsWith("/") ? urlPublica.substring(0, urlPublica.length() - 1) : urlPublica;
        return base + "/verificar/acta-faltantes/" + token;
    }

    public byte[] generar(ActaFaltanteDTO acta) throws Exception {
        Rectangle hoja = PageSize.LETTER;
        Document doc = new Document(hoja, MARGEN_LADO, MARGEN_LADO, MARGEN_ARRIBA, MARGEN_ABAJO);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfWriter writer = PdfWriter.getInstance(doc, out);

        String url = urlVerificacion(acta.token());
        Image qr = new BarcodeQRCode(url, 1, 1, null).getImage();
        qr.scaleAbsolute(54, 54);
        writer.setPageEvent(new Hoja(acta, url, qr, cargarMembrete()));

        doc.addTitle((acta.esRegularizacion() ? "Acta de regularización de faltantes " : "Acta de faltantes ") + acta.numero());
        doc.addAuthor("Sección de Activos Fijos - UAP");
        doc.addCreator("SCIAF");
        doc.open();

        encabezado(doc, acta);
        datos(doc, acta);

        String texto = acta.esRegularizacion()
                ? String.format("Se regularizan en el SCIAF los bienes detallados a continuación, que ya se encontraban "
                    + "registrados como FALTANTES en la oficina de faltantes de su predio a nombre de %s. Los bienes "
                    + "permanecen en esa oficina hasta que el caso se aclare. Cuando la oficina de origen no consta en "
                    + "el historial del sistema, figura como \"No registrada\".", acta.personaNombre())
                : String.format("Se deja constancia de que los bienes detallados a continuación, a cargo de %s, no fueron "
                    + "encontrados en su ubicación registrada y quedan registrados como FALTANTES. Los bienes se "
                    + "trasladan a la oficina de faltantes de su predio hasta que el caso se aclare.", acta.personaNombre());
        Paragraph intro = new Paragraph(texto, F_TEXTO);
        intro.setAlignment(Element.ALIGN_JUSTIFIED);
        intro.setSpacingBefore(8);
        intro.setSpacingAfter(8);
        doc.add(intro);

        doc.add(tablaBienes(acta));
        firmas(doc, writer, acta);

        doc.close();
        return out.toByteArray();
    }

    // ── Partes ──────────────────────────────────────────────────────────────

    private void encabezado(Document doc, ActaFaltanteDTO acta) throws Exception {
        Paragraph sub = new Paragraph("SECCIÓN DE ACTIVOS FIJOS", F_SUB);
        sub.setAlignment(Element.ALIGN_CENTER);
        doc.add(sub);

        Paragraph titulo = new Paragraph(acta.titulo(), F_TITULO);
        titulo.setAlignment(Element.ALIGN_CENTER);
        doc.add(titulo);

        Paragraph numero = new Paragraph("N° " + acta.numero(), F_NUMERO);
        numero.setAlignment(Element.ALIGN_CENTER);
        numero.setSpacingAfter(6);
        doc.add(numero);

        if (ActaFaltante.ANULADA.equals(acta.estado())) {
            Paragraph anulada = new Paragraph("ACTA ANULADA"
                    + (acta.fechaAnulacion() != null ? " el " + acta.fechaAnulacion().format(FECHA_HORA) : "")
                    + (acta.motivoAnulacion() != null ? " — " + acta.motivoAnulacion() : ""), F_ANULADA);
            anulada.setAlignment(Element.ALIGN_CENTER);
            anulada.setSpacingAfter(6);
            doc.add(anulada);
        }
    }

    private void datos(Document doc, ActaFaltanteDTO acta) throws Exception {
        PdfPTable t = new PdfPTable(4);
        t.setWidthPercentage(100);
        t.setWidths(new float[] { 1.25f, 3f, 1.25f, 2f });

        fila(t, "RESPONSABLE", acta.personaNombre(), "C.I.", nvl(acta.personaCi(), "—"));
        fila(t, "CARGO", nvl(acta.personaCargo(), "—"), "FECHA DE EMISIÓN",
                acta.fechaEmision() != null ? acta.fechaEmision().format(FECHA_HORA) : "—");
        String doc2 = acta.documentoRespaldo() == null ? "—"
                : acta.documentoRespaldo() + (acta.fechaDocumento() != null ? " (" + acta.fechaDocumento().format(FECHA) + ")" : "");
        fila(t, "DOCUMENTO DE RESPALDO", doc2, "REGISTRADO POR", nvl(acta.usuarioEmision(), "—"));
        if (acta.observacion() != null) {
            t.addCell(celdaEtiqueta("OBSERVACIÓN"));
            PdfPCell obs = celdaDato(acta.observacion());
            obs.setColspan(3);
            t.addCell(obs);
        }
        doc.add(t);
    }

    private PdfPTable tablaBienes(ActaFaltanteDTO acta) throws Exception {
        PdfPTable t = new PdfPTable(3);
        t.setWidthPercentage(100);
        t.setWidths(new float[] { 0.55f, 2.2f, 7.25f });
        t.setHeaderRows(1);

        for (String cab : new String[] { "N°", "CÓDIGO", "DESCRIPCIÓN" }) {
            PdfPCell c = new PdfPCell(new Phrase(cab, F_CAB));
            c.setBackgroundColor(AZUL);
            c.setHorizontalAlignment(Element.ALIGN_CENTER);
            c.setPadding(4);
            t.addCell(c);
        }

        int n = 0;
        for (ActaFaltanteDTO.Predio p : acta.predios()) {
            PdfPCell cp = new PdfPCell(new Phrase("PREDIO: " + p.unidad() + " — " + nvl(p.nombre(), ""), F_PREDIO));
            cp.setColspan(3);
            cp.setBackgroundColor(new BaseColor(88, 101, 140));
            cp.setPadding(4);
            t.addCell(cp);

            for (ActaFaltanteDTO.Oficina o : p.oficinas()) {
                PdfPCell co = new PdfPCell(new Phrase("Oficina de origen: "
                        + (o.codOfi() != null ? o.codOfi() + " — " : "") + nvl(o.nombre(), ActaFaltanteService.ORIGEN_NO_REGISTRADO), F_OFICINA));
                co.setColspan(3);
                co.setBackgroundColor(GRIS_CLARO);
                co.setPadding(4);
                t.addCell(co);

                for (ActaFaltanteDTO.Bien b : o.bienes()) {
                    n++;
                    PdfPCell cn = new PdfPCell(new Phrase(String.valueOf(n), F_CELDA));
                    cn.setHorizontalAlignment(Element.ALIGN_CENTER);
                    cn.setPadding(3);
                    t.addCell(cn);
                    PdfPCell cc = new PdfPCell(new Phrase(nvl(b.codigo(), ""), F_CODIGO));
                    cc.setPadding(3);
                    t.addCell(cc);
                    PdfPCell cd = new PdfPCell(new Phrase(nvl(b.descripcion(), ""), F_CELDA));
                    cd.setPadding(3);
                    t.addCell(cd);
                }
                PdfPCell st = new PdfPCell(new Phrase("Subtotal oficina: " + o.bienes().size(), F_CELDA));
                st.setColspan(3);
                st.setHorizontalAlignment(Element.ALIGN_RIGHT);
                st.setPadding(3);
                t.addCell(st);
            }
        }

        PdfPCell total = new PdfPCell(new Phrase("TOTAL DE BIENES FALTANTES: " + n, F_TOTAL));
        total.setColspan(3);
        total.setHorizontalAlignment(Element.ALIGN_RIGHT);
        total.setBackgroundColor(GRIS_CLARO);
        total.setPadding(5);
        t.addCell(total);
        return t;
    }

    /**
     * Las firmas van ancladas al pie de la última hoja. Si ahí no entran debajo de la tabla,
     * pasan a una hoja nueva: una firma nunca queda partida entre dos hojas.
     */
    private void firmas(Document doc, PdfWriter writer, ActaFaltanteDTO acta) throws Exception {
        if (writer.getVerticalPosition(true) - doc.bottom() < ALTO_FIRMAS + 4) {
            doc.newPage();
        }

        PdfPTable t = new PdfPTable(2);
        t.setTotalWidth(doc.right() - doc.left());
        t.setLockedWidth(true);

        Phrase detalleIzq = new Phrase();
        detalleIzq.add(new Phrase(nvl(acta.personaCargo(), ""), F_FIRMA_D));
        if (acta.personaCi() != null) {
            detalleIzq.add(new Phrase("\nC.I.: " + acta.personaCi(), F_FIRMA_D));
        }
        t.addCell(celdaFirma(acta.personaNombre(), detalleIzq));
        t.addCell(celdaFirma(FIRMA_DERECHA_TITULO, new Phrase(FIRMA_DERECHA_DETALLE, F_FIRMA_D)));

        t.writeSelectedRows(0, -1, doc.left(), doc.bottom() + ALTO_FIRMAS, writer.getDirectContent());
    }

    private PdfPCell celdaFirma(String titulo, Phrase detalle) {
        PdfPCell c = new PdfPCell();
        c.setBorder(Rectangle.NO_BORDER);
        c.setHorizontalAlignment(Element.ALIGN_CENTER);
        c.setPaddingLeft(18);
        c.setPaddingRight(18);

        // Espacio para la firma a mano (y el sello, a la derecha).
        Paragraph espacio = new Paragraph(" ");
        espacio.setSpacingBefore(28);
        c.addElement(espacio);

        Paragraph linea = new Paragraph("__________________________________", F_FIRMA_D);
        linea.setAlignment(Element.ALIGN_CENTER);
        c.addElement(linea);

        Paragraph nombre = new Paragraph(titulo, F_FIRMA);
        nombre.setAlignment(Element.ALIGN_CENTER);
        c.addElement(nombre);

        Paragraph det = new Paragraph(detalle);
        det.setAlignment(Element.ALIGN_CENTER);
        c.addElement(det);
        return c;
    }

    // ── Celdas ──────────────────────────────────────────────────────────────

    private void fila(PdfPTable t, String e1, String d1, String e2, String d2) {
        t.addCell(celdaEtiqueta(e1));
        t.addCell(celdaDato(d1));
        t.addCell(celdaEtiqueta(e2));
        t.addCell(celdaDato(d2));
    }

    private PdfPCell celdaEtiqueta(String texto) {
        PdfPCell c = new PdfPCell(new Phrase(texto, F_ETIQ));
        c.setBackgroundColor(GRIS_CLARO);
        c.setPadding(4);
        return c;
    }

    private PdfPCell celdaDato(String texto) {
        PdfPCell c = new PdfPCell(new Phrase(texto, F_DATO));
        c.setPadding(4);
        return c;
    }

    private Image cargarMembrete() {
        return PdfCustodiaComun.cargarMembrete();
    }

    private static String nvl(String s, String otro) {
        return (s == null || s.isBlank()) ? otro : s;
    }

    // ── Membrete, franja de verificación y "página X de Y" ─────────────────

    private static final class Hoja extends PdfPageEventHelper {
        private final ActaFaltanteDTO acta;
        private final String url;
        private final Image qr;
        private final Image membrete;
        private PdfTemplate totalPaginas;

        Hoja(ActaFaltanteDTO acta, String url, Image qr, Image membrete) {
            this.acta = acta;
            this.url = url;
            this.qr = qr;
            this.membrete = membrete;
        }

        @Override
        public void onOpenDocument(PdfWriter writer, Document document) {
            totalPaginas = writer.getDirectContent().createTemplate(30, 10);
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
                // Sin membrete el acta sigue siendo válida.
            }
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            try {
                PdfContentByte cb = writer.getDirectContent();
                float x = document.left();
                float y = PIE_MEMBRETE + 4;

                // Línea separadora de la franja
                cb.saveState();
                cb.setColorStroke(new BaseColor(200, 205, 214));
                cb.setLineWidth(0.5f);
                cb.moveTo(x, y + FRANJA - 6);
                cb.lineTo(document.right(), y + FRANJA - 6);
                cb.stroke();
                cb.restoreState();

                qr.setAbsolutePosition(x, y);
                cb.addImage(qr);

                float tx = x + 62;
                ColumnText.showTextAligned(cb, Element.ALIGN_LEFT,
                        new Phrase((acta.esRegularizacion() ? "Acta de regularización de faltantes N° " : "Acta de faltantes N° ")
                                + acta.numero() + " — generada por el SCIAF", F_FRANJA_B),
                        tx, y + 42, 0);
                ColumnText.showTextAligned(cb, Element.ALIGN_LEFT,
                        new Phrase("Verifique su autenticidad escaneando el código QR o en:", F_FRANJA), tx, y + 32, 0);
                ColumnText.showTextAligned(cb, Element.ALIGN_LEFT, new Phrase(url, F_FRANJA_B), tx, y + 22, 0);
                ColumnText.showTextAligned(cb, Element.ALIGN_LEFT,
                        new Phrase("Huella del contenido: " + acta.huella(), F_FRANJA), tx, y + 12, 0);

                String pagina = "Página " + writer.getPageNumber() + " de ";
                float ancho = F_FRANJA.getCalculatedBaseFont(false).getWidthPoint(pagina, F_FRANJA.getSize());
                float px = document.right() - ancho - 14;
                ColumnText.showTextAligned(cb, Element.ALIGN_LEFT, new Phrase(pagina, F_FRANJA), px, y + 2, 0);
                cb.addTemplate(totalPaginas, px + ancho, y + 2);

                if (ActaFaltante.ANULADA.equals(acta.estado())) {
                    marcaAnulada(writer, document);
                }
            } catch (Exception e) {
                // Un fallo de la franja no debe impedir entregar el acta.
            }
        }

        @Override
        public void onCloseDocument(PdfWriter writer, Document document) {
            ColumnText.showTextAligned(totalPaginas, Element.ALIGN_LEFT,
                    new Phrase(String.valueOf(writer.getPageNumber()), F_FRANJA), 0, 0, 0);
        }

        private void marcaAnulada(PdfWriter writer, Document document) {
            PdfContentByte cb = writer.getDirectContent();
            cb.saveState();
            PdfGState gs = new PdfGState();
            gs.setFillOpacity(0.18f);
            cb.setGState(gs);
            Rectangle hoja = document.getPageSize();
            ColumnText.showTextAligned(cb, Element.ALIGN_CENTER,
                    new Phrase("ANULADA", new Font(Font.FontFamily.HELVETICA, 90, Font.BOLD, new BaseColor(176, 32, 45))),
                    hoja.getWidth() / 2, hoja.getHeight() / 2, 45);
            cb.restoreState();
        }
    }
}
