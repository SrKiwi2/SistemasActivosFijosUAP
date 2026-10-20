package com.usic.SistemasActivosFijosUAP.model.service.duplicados;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Chunk;
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
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfPageEventHelper;
import com.itextpdf.text.pdf.PdfTemplate;
import com.itextpdf.text.pdf.PdfWriter;
import com.usic.SistemasActivosFijosUAP.model.dto.duplicados.DuplicadosDTOs;
import com.usic.SistemasActivosFijosUAP.model.entity.Activo;
import com.usic.SistemasActivosFijosUAP.model.entity.NotificacionDuplicado;

import lombok.extern.slf4j.Slf4j;

/**
 * PDF de la notificación de activo registrado como duplicado, con el mismo formato que la
 * notificación de faltantes (membrete, carta al responsable, firma del Responsable de
 * Activos Fijos y franja de verificación con QR). Dice qué activo se dispuso como duplicado,
 * de cuál, quién lo registró y por qué, y que por eso se considera faltante y pasa a la
 * ubicación de duplicados. Se arma siempre desde la foto guardada: reimprimir da lo mismo.
 */
@Slf4j
@Service
public class PdfNotificacionDuplicadoService {

    private static final String MEMBRETE = "/static/assets/img/fondo/0.jpg";
    private static final Locale ES = Locale.forLanguageTag("es-BO");
    private static final DateTimeFormatter FECHA_LARGA = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", ES);
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final String UAP = "UNIVERSIDAD AMAZÓNICA DE PANDO";

    private static final float MARGEN_LADO = 50f;
    private static final float MARGEN_ARRIBA = 106f;
    private static final float PIE_MEMBRETE = 72f;
    private static final float FRANJA = 58f;
    private static final float MARGEN_ABAJO = PIE_MEMBRETE + FRANJA;
    private static final float ALTO_FIRMA = 110f;

    private static final BaseColor GRIS_CLARO = new BaseColor(236, 239, 244);
    private static final BaseColor GRIS_TEXTO = new BaseColor(90, 98, 110);
    private static final BaseColor AMBAR = new BaseColor(176, 92, 0);
    private static final BaseColor ROJO = new BaseColor(176, 32, 45);

    private static final Font N_CAB = new Font(Font.FontFamily.HELVETICA, 11, Font.BOLD);
    private static final Font N_TEXTO = new Font(Font.FontFamily.HELVETICA, 10);
    private static final Font N_NEGRITA = new Font(Font.FontFamily.HELVETICA, 10, Font.BOLD);
    private static final Font N_TABLA_CAB = new Font(Font.FontFamily.HELVETICA, 8, Font.BOLD);
    private static final Font N_TABLA = new Font(Font.FontFamily.HELVETICA, 8);
    private static final Font N_PIE = new Font(Font.FontFamily.HELVETICA, 7);
    private static final Font N_PIE_B = new Font(Font.FontFamily.HELVETICA, 7, Font.BOLD);
    private static final Font N_VISTA_PREVIA = new Font(Font.FontFamily.HELVETICA, 10, Font.BOLD, AMBAR);
    private static final Font F_ANULADA = new Font(Font.FontFamily.HELVETICA, 10, Font.BOLD, ROJO);
    private static final Font F_FRANJA = new Font(Font.FontFamily.HELVETICA, 6.8f, Font.NORMAL, GRIS_TEXTO);
    private static final Font F_FRANJA_B = new Font(Font.FontFamily.HELVETICA, 6.8f, Font.BOLD, GRIS_TEXTO);
    private static final Font F_FRANJA_PREVIA = new Font(Font.FontFamily.HELVETICA, 7.5f, Font.BOLD, AMBAR);

    @Value("${sciaf.url.publica:https://sciaf.uap.edu.bo}")
    private String urlPublica;

    /** Dirección que va en el QR. */
    public String urlVerificacion(String token) {
        String base = urlPublica.endsWith("/") ? urlPublica.substring(0, urlPublica.length() - 1) : urlPublica;
        return base + "/verificar/duplicado/" + token;
    }

    /**
     * @param vistaPrevia sin número, QR ni código de verificación, con el aviso de que no vale:
     *                    la notificación todavía no se registró
     */
    public byte[] generar(DuplicadosDTOs.Notificacion n, boolean vistaPrevia) throws Exception {
        DuplicadosDTOs.Contenido c = n.c();
        Document doc = new Document(PageSize.LETTER, MARGEN_LADO, MARGEN_LADO, MARGEN_ARRIBA, MARGEN_ABAJO);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfWriter writer = PdfWriter.getInstance(doc, out);
        String url = vistaPrevia ? null : urlVerificacion(n.token());
        Image qr = null;
        if (url != null) {
            qr = new BarcodeQRCode(url, 1, 1, null).getImage();
            qr.scaleAbsolute(54, 54);
        }
        writer.setPageEvent(new Hoja(n, url, qr, cargarMembrete(), vistaPrevia));

        doc.addTitle((vistaPrevia ? "VISTA PREVIA — " : "") + "Notificación de activo registrado como duplicado "
                + (vistaPrevia ? "" : n.numeroImpreso()));
        doc.addAuthor("Sección de Activos Fijos - UAP");
        doc.addCreator("SCIAF");
        doc.open();

        for (String linea : new String[] { UAP, "DIRECCIÓN ADMINISTRATIVA FINANCIERA", "SECCIÓN DE ACTIVOS FIJOS" }) {
            Paragraph p = new Paragraph(linea, N_CAB);
            p.setAlignment(Element.ALIGN_CENTER);
            doc.add(p);
        }

        Paragraph numero = new Paragraph(vistaPrevia ? "NOT:SCIAF:AD N° ___/____" : n.numeroImpreso(), N_NEGRITA);
        numero.setAlignment(Element.ALIGN_RIGHT);
        numero.setSpacingBefore(12);
        doc.add(numero);
        LocalDateTime fecha = fecha(c.fecha());
        Paragraph pf = new Paragraph(nvl(c.ciudad(), "Cobija") + ", " + (fecha != null ? fecha.format(FECHA_LARGA) : ""), N_NEGRITA);
        pf.setAlignment(Element.ALIGN_RIGHT);
        pf.setSpacingAfter(10);
        doc.add(pf);

        if (vistaPrevia) {
            Paragraph aviso = new Paragraph("VISTA PREVIA — no registrada: sin número y sin validez", N_VISTA_PREVIA);
            aviso.setAlignment(Element.ALIGN_CENTER);
            aviso.setSpacingAfter(8);
            doc.add(aviso);
        }
        if (NotificacionDuplicado.ANULADA.equals(n.estado())) {
            Paragraph anulada = new Paragraph("NOTIFICACIÓN ANULADA"
                    + (n.fechaAnulacion() != null ? " el " + n.fechaAnulacion().format(FECHA_HORA) : "")
                    + (n.motivoAnulacion() != null ? " — " + n.motivoAnulacion() : ""), F_ANULADA);
            anulada.setAlignment(Element.ALIGN_CENTER);
            anulada.setSpacingAfter(8);
            doc.add(anulada);
        }

        doc.add(new Paragraph("Señor(a):", N_NEGRITA));
        doc.add(new Paragraph(nvl(c.destinatario(), "").toUpperCase(ES), N_NEGRITA));
        if (c.cargo() != null) doc.add(new Paragraph(c.cargo().toUpperCase(ES), N_NEGRITA));
        if (c.unidad() != null) doc.add(new Paragraph(c.unidad().toUpperCase(ES), N_NEGRITA));
        doc.add(new Paragraph(UAP, N_NEGRITA));
        Paragraph presente = new Paragraph("Presente.-", N_NEGRITA);
        presente.setSpacingBefore(8);
        doc.add(presente);

        Paragraph ref = new Paragraph("REF.: NOTIFICACIÓN DE ACTIVO REGISTRADO COMO DUPLICADO", N_NEGRITA);
        ref.setAlignment(Element.ALIGN_CENTER);
        ref.setSpacingBefore(8);
        ref.setSpacingAfter(8);
        doc.add(ref);

        DuplicadosDTOs.BienDoc b = c.sobrante();
        DuplicadosDTOs.BienDoc a = c.principal();
        String codB = nvl(Activo.codigoDocumento(b.codigo()), "—");
        String codA = nvl(Activo.codigoDocumento(a.codigo()), "—");

        doc.add(new Paragraph("De mi consideración:", N_NEGRITA));
        doc.add(parrafo("Mediante la presente, la **Sección de Activos Fijos** de la Universidad Amazónica de Pando, "
                + "en el marco de las actividades de **control, verificación y actualización de los registros de "
                + "activos fijos**, pone en su conocimiento que el activo con código **" + codB + "**, registrado bajo "
                + "su responsabilidad, fue identificado como un **REGISTRO DUPLICADO**: corresponde al mismo bien físico "
                + "que el activo con código **" + codA + "**, registrado dos veces en el Sistema de Información de Activos "
                + "Fijos (vSIAF) como si se tratara de dos bienes distintos."));

        Paragraph detalle = new Paragraph("DETALLE DE LOS REGISTROS", N_NEGRITA);
        detalle.setSpacingBefore(4);
        detalle.setSpacingAfter(6);
        doc.add(detalle);
        doc.add(tabla(b, a));

        doc.add(parrafo("El registro fue dispuesto como duplicado por **" + nvl(c.registradoPor(), "—") + "**"
                + (fecha != null ? " en fecha " + fecha.format(FECHA_LARGA) : "") + ", por el siguiente motivo: **"
                + nvl(c.motivo(), "—").replace("**", "") + "**"));
        doc.add(parrafo("En consecuencia, el registro **" + codB + "** no cuenta con un bien físico propio y **se considera "
                + "FALTANTE**; por ello **se registra en la ubicación de DUPLICADOS** de su predio"
                + (c.oficinaDuplicados() != null ? " (" + c.oficinaDuplicados() + ")" : "")
                + ", a su nombre, hasta que el **nuevo revalúo** regularice su situación. Esta medida no implica la "
                + "corrección ni la baja del registro. El activo **" + codA + "** se mantiene sin cambios."));
        if (c.faltanteNumero() != null) {
            doc.add(parrafo("El activo **" + codB + "** había sido notificado como faltante mediante la **" + c.faltanteNumero()
                    + "**. Con la presente se aclara que su ausencia física se debe a que se trata de un registro "
                    + "duplicado, por lo que **queda sin efecto el requerimiento de informar sobre su paradero** respecto de "
                    + "este bien."));
        }
        doc.add(parrafo("La presente comunicación queda registrada en el **Sistema de Control Interno de Activos Fijos "
                + "(SCIAF)** para fines de control y seguimiento."));
        doc.add(parrafo("Sin otro particular, saludo a usted con las consideraciones más distinguidas."));
        Paragraph atte = new Paragraph("Atentamente,", N_NEGRITA);
        atte.setSpacingBefore(4);
        doc.add(atte);

        firma(doc, writer, n, vistaPrevia);
        doc.close();
        return out.toByteArray();
    }

    private PdfPTable tabla(DuplicadosDTOs.BienDoc b, DuplicadosDTOs.BienDoc a) throws Exception {
        PdfPTable t = new PdfPTable(7);
        t.setWidthPercentage(100);
        t.setWidths(new float[] { 1.35f, 1.6f, 2.7f, 1.1f, 1.1f, 1.3f, 1.8f });
        t.setHeaderRows(1);
        t.setSpacingAfter(8);
        t.setSplitRows(false);
        for (String cab : new String[] { "REGISTRO", "CÓDIGO DE ACTIVO", "DESCRIPCIÓN DEL ACTIVO", "MARCA", "MODELO",
                "N.º DE SERIE", "OFICINA / UBICACIÓN" }) {
            PdfPCell c = celda(cab, N_TABLA_CAB, Element.ALIGN_CENTER);
            c.setBackgroundColor(GRIS_CLARO);
            t.addCell(c);
        }
        fila(t, "DUPLICADO (va a duplicados)", b);
        fila(t, "SE CONSERVA", a);
        return t;
    }

    private static void fila(PdfPTable t, String registro, DuplicadosDTOs.BienDoc x) {
        t.addCell(celda(registro, N_TABLA_CAB, Element.ALIGN_CENTER));
        t.addCell(celda(nvl(Activo.codigoDocumento(x.codigo()), "—"), N_TABLA, Element.ALIGN_CENTER));
        t.addCell(celda(nvl(x.descripcionCorta(), nvl(x.descripcion(), "")), N_TABLA, Element.ALIGN_LEFT));
        t.addCell(celda(nvl(x.marca(), "—"), N_TABLA, Element.ALIGN_CENTER));
        t.addCell(celda(nvl(x.modelo(), "—"), N_TABLA, Element.ALIGN_CENTER));
        t.addCell(celda(nvl(x.serie(), "—"), N_TABLA, Element.ALIGN_CENTER));
        String ubic = nvl(x.oficina(), "—") + (x.responsable() != null ? "\n" + x.responsable() : "");
        t.addCell(celda(ubic, N_TABLA, Element.ALIGN_CENTER));
    }

    private static PdfPCell celda(String texto, Font f, int alineacion) {
        PdfPCell c = new PdfPCell(new Phrase(texto, f));
        c.setHorizontalAlignment(alineacion);
        c.setVerticalAlignment(Element.ALIGN_MIDDLE);
        c.setPadding(3.5f);
        c.setBorderColor(new BaseColor(150, 150, 150));
        return c;
    }

    /** Párrafo justificado; lo que va entre ** sale en negrita. */
    private static Paragraph parrafo(String texto) {
        Paragraph p = new Paragraph();
        String[] partes = texto.split("\\*\\*", -1);
        for (int i = 0; i < partes.length; i++) {
            if (!partes[i].isEmpty()) p.add(new Chunk(partes[i], i % 2 == 1 ? N_NEGRITA : N_TEXTO));
        }
        p.setAlignment(Element.ALIGN_JUSTIFIED);
        p.setLeading(13.5f);
        p.setSpacingAfter(7);
        return p;
    }

    /** Firma del Responsable de Activos Fijos y, abajo, copia y código de verificación. */
    private void firma(Document doc, PdfWriter writer, DuplicadosDTOs.Notificacion n, boolean vistaPrevia) throws Exception {
        if (writer.getVerticalPosition(true) - doc.bottom() < ALTO_FIRMA) doc.newPage();
        PdfPTable t = new PdfPTable(1);
        t.setWidthPercentage(100);
        t.setKeepTogether(true);
        PdfPCell c = new PdfPCell();
        c.setBorder(Rectangle.NO_BORDER);
        Paragraph espacio = new Paragraph(" ");
        espacio.setSpacingBefore(30);
        c.addElement(espacio);
        for (Paragraph p : new Paragraph[] {
                new Paragraph("________________________________________", N_TEXTO),
                new Paragraph(n.c().firmante() != null ? n.c().firmante().toUpperCase(ES) : " ", N_NEGRITA),
                new Paragraph("RESPONSABLE DE ACTIVOS FIJOS", N_NEGRITA),
                new Paragraph(UAP, N_NEGRITA) }) {
            p.setAlignment(Element.ALIGN_CENTER);
            c.addElement(p);
        }
        t.addCell(c);
        doc.add(t);

        Paragraph pie = new Paragraph();
        pie.setLeading(9f);
        pie.add(new Chunk("C.c.: ", N_PIE_B));
        pie.add(new Chunk("Archivo – Sección de Activos Fijos\n", N_PIE));
        pie.add(new Chunk("Documento generado por: ", N_PIE_B));
        pie.add(new Chunk("Sistema de Control Interno de Activos Fijos – SCIAF\n", N_PIE));
        pie.add(new Chunk("Código de verificación: ", N_PIE_B));
        pie.add(new Chunk(vistaPrevia ? "— (vista previa: sin código, no tiene validez)" : nvl(n.huella(), "—"), N_PIE));
        ColumnText ct = new ColumnText(writer.getDirectContent());
        ct.setSimpleColumn(doc.left(), doc.bottom(), doc.right(), doc.bottom() + 40);
        ct.addElement(pie);
        ct.go();
    }

    private static Image cargarMembrete() {
        try (InputStream in = PdfNotificacionDuplicadoService.class.getResourceAsStream(MEMBRETE)) {
            if (in == null) return null;
            return Image.getInstance(in.readAllBytes());
        } catch (Exception e) {
            log.warn("[DUPLICADOS] No se pudo cargar el membrete: {}", e.getMessage());
            return null;
        }
    }

    private static LocalDateTime fecha(String iso) {
        try {
            return iso != null ? LocalDateTime.parse(iso) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String nvl(String s, String otro) {
        return s != null && !s.isBlank() ? s : otro;
    }

    /** Membrete de fondo y franja de verificación en cada hoja. */
    private static final class Hoja extends PdfPageEventHelper {
        private final DuplicadosDTOs.Notificacion n;
        private final String url;
        private final Image qr;
        private final Image membrete;
        private final boolean vistaPrevia;
        private PdfTemplate totalPaginas;

        Hoja(DuplicadosDTOs.Notificacion n, String url, Image qr, Image membrete, boolean vistaPrevia) {
            this.n = n;
            this.url = url;
            this.qr = qr;
            this.membrete = membrete;
            this.vistaPrevia = vistaPrevia;
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
                // Sin membrete la notificación sigue siendo válida.
            }
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            try {
                PdfContentByte cb = writer.getDirectContent();
                float x = document.left();
                float y = PIE_MEMBRETE + 4;
                cb.saveState();
                cb.setColorStroke(new BaseColor(200, 205, 214));
                cb.setLineWidth(0.5f);
                cb.moveTo(x, y + FRANJA - 6);
                cb.lineTo(document.right(), y + FRANJA - 6);
                cb.stroke();
                cb.restoreState();

                if (vistaPrevia) {
                    ColumnText.showTextAligned(cb, Element.ALIGN_LEFT, new Phrase(
                            "VISTA PREVIA de la notificación de duplicado — no está registrada en el SCIAF", F_FRANJA_PREVIA), x, y + 40, 0);
                    ColumnText.showTextAligned(cb, Element.ALIGN_LEFT, new Phrase(
                            "No tiene número, QR ni código de verificación: no tiene validez y no se debe entregar.", F_FRANJA), x, y + 29, 0);
                } else {
                    qr.setAbsolutePosition(x, y);
                    cb.addImage(qr);
                    float tx = x + 62;
                    ColumnText.showTextAligned(cb, Element.ALIGN_LEFT, new Phrase(
                            "Notificación de duplicado " + n.numeroImpreso() + " — generada por el SCIAF", F_FRANJA_B), tx, y + 42, 0);
                    ColumnText.showTextAligned(cb, Element.ALIGN_LEFT, new Phrase(
                            "Verifique su autenticidad escaneando el código QR o en:", F_FRANJA), tx, y + 32, 0);
                    ColumnText.showTextAligned(cb, Element.ALIGN_LEFT, new Phrase(url, F_FRANJA_B), tx, y + 22, 0);
                    ColumnText.showTextAligned(cb, Element.ALIGN_LEFT, new Phrase("Huella del contenido: " + n.huella(), F_FRANJA), tx, y + 12, 0);
                }
                String pagina = "Página " + writer.getPageNumber() + " de ";
                float ancho = F_FRANJA.getCalculatedBaseFont(false).getWidthPoint(pagina, F_FRANJA.getSize());
                float px = document.right() - ancho - 14;
                ColumnText.showTextAligned(cb, Element.ALIGN_LEFT, new Phrase(pagina, F_FRANJA), px, y, 0);
                cb.addTemplate(totalPaginas, px + ancho, y);
            } catch (Exception e) {
                log.warn("[DUPLICADOS] No se pudo dibujar la franja: {}", e.getMessage());
            }
        }

        @Override
        public void onCloseDocument(PdfWriter writer, Document document) {
            ColumnText.showTextAligned(totalPaginas, Element.ALIGN_LEFT,
                    new Phrase(String.valueOf(writer.getPageNumber()), F_FRANJA), 0, 0, 0);
        }
    }
}
