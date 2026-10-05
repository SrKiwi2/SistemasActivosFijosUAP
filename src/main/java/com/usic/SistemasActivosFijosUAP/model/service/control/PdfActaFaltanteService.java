package com.usic.SistemasActivosFijosUAP.model.service.control;

import java.io.ByteArrayOutputStream;
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
 * PDF del acta de faltantes, para imprimir y firmar. Los faltantes emitidos desde el
 * 2-oct-2026 salen como <b>notificación</b> ({@link #notificacion}), con el formato que
 * definió la Sección de Activos Fijos; las actas anteriores y las de regularización
 * conservan su formato de acta.
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
        if (acta.esNotificacion()) return notificacion(acta, false);
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

    // ── Notificación de activos físicos faltantes ──────────────────────────

    private static final Locale ES = Locale.forLanguageTag("es-BO");
    private static final DateTimeFormatter FECHA_LARGA = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", ES);
    private static final String UAP = "UNIVERSIDAD AMAZÓNICA DE PANDO";

    private static final Font N_CAB    = new Font(Font.FontFamily.HELVETICA, 11, Font.BOLD);
    private static final Font N_TEXTO  = new Font(Font.FontFamily.HELVETICA, 10);
    private static final Font N_NEGRITA = new Font(Font.FontFamily.HELVETICA, 10, Font.BOLD);
    private static final Font N_TABLA_CAB = new Font(Font.FontFamily.HELVETICA, 8, Font.BOLD);
    private static final Font N_TABLA  = new Font(Font.FontFamily.HELVETICA, 8);
    private static final Font N_PIE    = new Font(Font.FontFamily.HELVETICA, 7);
    private static final Font N_PIE_B  = new Font(Font.FontFamily.HELVETICA, 7, Font.BOLD);
    private static final float ALTO_FIRMA_NOTIFICACION = 110f;
    private static final BaseColor AMBAR = new BaseColor(176, 92, 0);
    private static final Font N_VISTA_PREVIA = new Font(Font.FontFamily.HELVETICA, 10, Font.BOLD, AMBAR);
    private static final Font F_FRANJA_PREVIA = new Font(Font.FontFamily.HELVETICA, 7.5f, Font.BOLD, AMBAR);

    /**
     * Vista previa de una notificación que todavía no se registró: el mismo documento, pero
     * con "VISTA PREVIA" de marca de agua en cada hoja y sin número, QR ni código de
     * verificación. No existe en el SCIAF, así que no sirve para entregar.
     */
    public byte[] generarVistaPrevia(ActaFaltanteDTO acta) throws Exception {
        if (!acta.esNotificacion()) {
            throw new IllegalArgumentException("La vista previa es solo para notificaciones de faltantes.");
        }
        return notificacion(acta, true);
    }

    /**
     * Carta dirigida al responsable con el detalle de los bienes no encontrados, el plazo en
     * días hábiles para informar y la firma del Responsable de Activos Fijos. Todo sale de la
     * foto guardada al emitir.
     */
    private byte[] notificacion(ActaFaltanteDTO acta, boolean vistaPrevia) throws Exception {
        Document doc = new Document(PageSize.LETTER, MARGEN_LADO, MARGEN_LADO, MARGEN_ARRIBA, MARGEN_ABAJO);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfWriter writer = PdfWriter.getInstance(doc, out);

        if (vistaPrevia) {
            // Sin QR: llevaría a una página que no existe (la vista previa no se guarda).
            writer.setPageEvent(new Hoja(acta, null, null, cargarMembrete(), true));
        } else {
            String url = urlVerificacion(acta.token());
            Image qr = new BarcodeQRCode(url, 1, 1, null).getImage();
            qr.scaleAbsolute(54, 54);
            writer.setPageEvent(new Hoja(acta, url, qr, cargarMembrete()));
        }

        doc.addTitle((vistaPrevia ? "VISTA PREVIA — " : "")
                + (acta.esReiterativa() ? "Notificación reiterativa de activos físicos faltantes "
                                        : "Notificación de activos físicos faltantes ") + acta.numeroImpreso());
        doc.addAuthor("Sección de Activos Fijos - UAP");
        doc.addCreator("SCIAF");
        doc.open();

        for (String linea : new String[] { UAP, "DIRECCIÓN ADMINISTRATIVA FINANCIERA", "SECCIÓN DE ACTIVOS FIJOS" }) {
            Paragraph p = new Paragraph(linea, N_CAB);
            p.setAlignment(Element.ALIGN_CENTER);
            doc.add(p);
        }

        Paragraph numero = new Paragraph(acta.numeroImpreso(), N_NEGRITA);
        numero.setAlignment(Element.ALIGN_RIGHT);
        numero.setSpacingBefore(12);
        doc.add(numero);
        // La fecha del papel es desde cuándo corre el plazo: la emisión o, si se cambió el plazo, ese día.
        Paragraph fecha = new Paragraph(nvl(acta.ciudad(), "Cobija") + ", "
                + (acta.fechaDelDocumento() != null ? acta.fechaDelDocumento().format(FECHA_LARGA) : ""), N_NEGRITA);
        fecha.setAlignment(Element.ALIGN_RIGHT);
        fecha.setSpacingAfter(10);
        doc.add(fecha);

        if (vistaPrevia) {
            Paragraph aviso = new Paragraph("VISTA PREVIA — no registrada: sin número y sin validez", N_VISTA_PREVIA);
            aviso.setAlignment(Element.ALIGN_CENTER);
            aviso.setSpacingAfter(8);
            doc.add(aviso);
        }

        if (ActaFaltante.ANULADA.equals(acta.estado())) {
            Paragraph anulada = new Paragraph("NOTIFICACIÓN ANULADA"
                    + (acta.fechaAnulacion() != null ? " el " + acta.fechaAnulacion().format(FECHA_HORA) : "")
                    + (acta.motivoAnulacion() != null ? " — " + acta.motivoAnulacion() : ""), F_ANULADA);
            anulada.setAlignment(Element.ALIGN_CENTER);
            anulada.setSpacingAfter(8);
            doc.add(anulada);
        }

        doc.add(new Paragraph("Señor(a):", N_NEGRITA));
        doc.add(new Paragraph(acta.personaNombre().toUpperCase(ES), N_NEGRITA));
        if (acta.personaCargo() != null) doc.add(new Paragraph(acta.personaCargo().toUpperCase(ES), N_NEGRITA));
        if (acta.unidad() != null) doc.add(new Paragraph(acta.unidad().toUpperCase(ES), N_NEGRITA));
        doc.add(new Paragraph(UAP, N_NEGRITA));
        Paragraph presente = new Paragraph("Presente.-", N_NEGRITA);
        presente.setSpacingBefore(8);
        doc.add(presente);

        Paragraph ref = new Paragraph("REF.: " + acta.titulo(), N_NEGRITA);
        ref.setAlignment(Element.ALIGN_CENTER);
        ref.setSpacingBefore(8);
        ref.setSpacingAfter(8);
        doc.add(ref);

        doc.add(new Paragraph("De mi consideración:", N_NEGRITA));
        if (acta.esReiterativa()) {
            // Antes la reiterativa salía igual que la notificación original: no decía que reiteraba.
            doc.add(parrafo("En atención a la **" + nvl(acta.reiteraA(), "notificación anterior") + "**"
                    + (acta.reiteraAFecha() != null ? " de fecha " + acta.reiteraAFecha().format(FECHA_LARGA) : "")
                    + ", y considerando que a la fecha no se regularizó la situación de los bienes detallados a "
                    + "continuación, mediante la presente se le **reitera dicha notificación**"
                    + (Integer.valueOf(2).equals(acta.numeroReiterativa())
                            ? " con carácter de **segunda y última notificación**." : ".")));
        }
        doc.add(parrafo("Mediante la presente, la **Sección de Activos Fijos** de la Universidad Amazónica de Pando, "
                + "en el marco de las actividades de **control, verificación y actualización de los registros de "
                + "activos fijos**, pone en su conocimiento los resultados de la verificación física realizada a los "
                + "bienes registrados bajo su responsabilidad."));
        doc.add(parrafo("Como resultado del proceso de cotejo entre los registros institucionales y la verificación "
                + "física efectuada, se identificaron **activos fijos que no fueron encontrados físicamente**, los "
                + "cuales se detallan a continuación:"));

        Paragraph detalle = new Paragraph("DETALLE DE ACTIVOS FÍSICOS FALTANTES", N_NEGRITA);
        detalle.setSpacingBefore(4);
        detalle.setSpacingAfter(6);
        doc.add(detalle);

        int total = 0;
        boolean variosPredios = acta.predios().size() > 1;
        for (ActaFaltanteDTO.Predio p : acta.predios()) {
            for (ActaFaltanteDTO.Oficina o : p.oficinas()) {
                String titulo = "Oficina " + (o.codOfi() != null ? o.codOfi() + " – " : "– ")
                        + nvl(o.nombre(), ActaFaltanteService.ORIGEN_NO_REGISTRADO)
                        + (variosPredios ? " (" + nvl(p.nombre(), p.unidad()) + ")" : "");
                // El título de la oficina, la cabecera y al menos una fila van juntos: si no
                // entran al pie de esta hoja, la oficina empieza en la siguiente.
                if (writer.getVerticalPosition(true) - doc.bottom() < 85) {
                    doc.newPage();
                }
                Paragraph to = new Paragraph(titulo, N_NEGRITA);
                to.setSpacingBefore(4);
                to.setSpacingAfter(3);
                to.setKeepTogether(true);
                doc.add(to);
                doc.add(tablaOficina(o));
                total += o.bienes().size();
            }
        }
        Paragraph tot = new Paragraph("TOTAL DE ACTIVOS FALTANTES: " + total, N_NEGRITA);
        tot.setSpacingBefore(4);
        tot.setSpacingAfter(8);
        doc.add(tot);

        doc.add(parrafo("De acuerdo con los registros del **Sistema de Información de Activos Fijos (vSIAF)** y el "
                + "levantamiento físico realizado, los bienes detallados precedentemente se encuentran registrados bajo "
                + "su responsabilidad y, a la fecha consignada en el presente documento, **no fueron ubicados "
                + "físicamente en las dependencias y ubicaciones registradas**."));
        doc.add(parrafo("En consecuencia, **se solicita a usted informar a la Sección de Activos Fijos sobre la situación "
                + "y paradero de cada uno de los bienes señalados**, proporcionando la información y/o documentación "
                + "que permita establecer su ubicación actual y las circunstancias relacionadas con su ausencia."));
        doc.add(parrafo("La información solicitada deberá ser presentada dentro del plazo de **"
                + plazoEnTexto(acta.plazoDias()) + (Integer.valueOf(1).equals(acta.plazoDias()) ? " día hábil" : " días hábiles")
                + "**, computables a partir de la recepción de la "
                + "presente notificación, a efectos de realizar el correspondiente seguimiento y actualización de los "
                + "registros patrimoniales."));
        doc.add(parrafo("La presente comunicación queda registrada en el **Sistema de Control Interno de Activos Fijos "
                + "(SCIAF)** para fines de control y seguimiento."));
        doc.add(parrafo("Sin otro particular, saludo a usted con las consideraciones más distinguidas."));
        Paragraph atte = new Paragraph("Atentamente,", N_NEGRITA);
        atte.setSpacingBefore(4);
        doc.add(atte);

        firmaNotificacion(doc, writer, acta, vistaPrevia);
        doc.close();
        return out.toByteArray();
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

    private PdfPTable tablaOficina(ActaFaltanteDTO.Oficina o) throws Exception {
        PdfPTable t = new PdfPTable(7);
        t.setWidthPercentage(100);
        t.setWidths(new float[] { 0.5f, 1.7f, 2.9f, 1.15f, 1.3f, 1.45f, 1.6f });
        t.setHeaderRows(1);
        t.setSpacingAfter(4);
        // Una fila nunca se parte entre dos hojas (ninguna es más alta que una hoja).
        t.setSplitRows(false);
        for (String cab : new String[] { "N.º", "CÓDIGO DE ACTIVO", "DESCRIPCIÓN DEL ACTIVO", "MARCA", "MODELO",
                "N.º DE SERIE", "OFICINA / UBICACIÓN" }) {
            PdfPCell c = celdaTabla(cab, N_TABLA_CAB, Element.ALIGN_CENTER);
            c.setBackgroundColor(GRIS_CLARO);
            t.addCell(c);
        }
        String ubicacion = (o.codOfi() != null ? o.codOfi() + " – " : "") + nvl(o.nombre(), ActaFaltanteService.ORIGEN_NO_REGISTRADO);
        int n = 0;
        for (ActaFaltanteDTO.Bien b : o.bienes()) {
            n++;
            t.addCell(celdaTabla(String.format("%02d", n), N_TABLA, Element.ALIGN_CENTER));
            t.addCell(celdaTabla(nvl(b.codigo(), ""), N_TABLA, Element.ALIGN_CENTER));
            t.addCell(celdaTabla(nvl(b.descripcionCorta(), nvl(b.descripcion(), "")), N_TABLA, Element.ALIGN_LEFT));
            t.addCell(celdaTabla(nvl(b.marca(), "—"), N_TABLA, Element.ALIGN_CENTER));
            t.addCell(celdaTabla(nvl(b.modelo(), "—"), N_TABLA, Element.ALIGN_CENTER));
            t.addCell(celdaTabla(nvl(b.serie(), "—"), N_TABLA, Element.ALIGN_CENTER));
            t.addCell(celdaTabla(ubicacion, N_TABLA, Element.ALIGN_CENTER));
        }
        return t;
    }

    private static PdfPCell celdaTabla(String texto, Font f, int alineacion) {
        PdfPCell c = new PdfPCell(new Phrase(texto, f));
        c.setHorizontalAlignment(alineacion);
        c.setVerticalAlignment(Element.ALIGN_MIDDLE);
        c.setPadding(3.5f);
        c.setBorderColor(new BaseColor(150, 150, 150));
        return c;
    }

    /**
     * Firma del Responsable de Activos Fijos (al centro) y, debajo, copia y código de
     * verificación. Nunca queda partida entre dos hojas.
     */
    private void firmaNotificacion(Document doc, PdfWriter writer, ActaFaltanteDTO acta, boolean vistaPrevia)
            throws Exception {
        if (writer.getVerticalPosition(true) - doc.bottom() < ALTO_FIRMA_NOTIFICACION) {
            doc.newPage();
        }
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
                new Paragraph(acta.firmante() != null ? acta.firmante().toUpperCase(ES) : " ", N_NEGRITA),
                new Paragraph("RESPONSABLE DE ACTIVOS FIJOS", N_NEGRITA),
                new Paragraph(UAP, N_NEGRITA) }) {
            p.setAlignment(Element.ALIGN_CENTER);
            c.addElement(p);
        }
        Paragraph pie = new Paragraph();
        // 1. setLeading define el alto de línea (interlineado). Disminuye este valor para apegar más el texto (ej. 8f o 9f).
        pie.setLeading(9f); 
        pie.add(new Chunk("C.c.: ", N_PIE_B));
        pie.add(new Chunk("Archivo – Sección de Activos Fijos\n", N_PIE));
        pie.add(new Chunk("Documento generado por: ", N_PIE_B));
        pie.add(new Chunk("Sistema de Control Interno de Activos Fijos – SCIAF\n", N_PIE));
        pie.add(new Chunk("Código de verificación: ", N_PIE_B));
        pie.add(new Chunk(vistaPrevia ? "— (vista previa: sin código, no tiene validez)" : nvl(acta.huella(), "—"), N_PIE));

        // 2. Agregamos SOLO la firma al documento flotante
        doc.add(t);

        // 3. Posicionamos el bloque "pie" de forma absoluta justo encima del margen inferior (arriba del QR)
        PdfContentByte cb = writer.getDirectContent();
        ColumnText ct = new ColumnText(cb);
        // Se define un rectángulo invisible al fondo de la hoja (doc.bottom() es donde empieza el área del QR)
        ct.setSimpleColumn(doc.left(), doc.bottom(), doc.right(), doc.bottom() + 40); 
        ct.addElement(pie);
        ct.go();
    }

    private static final String[] UNIDADES = { "cero", "uno", "dos", "tres", "cuatro", "cinco", "seis", "siete",
            "ocho", "nueve", "diez", "once", "doce", "trece", "catorce", "quince", "dieciséis", "diecisiete",
            "dieciocho", "diecinueve", "veinte", "veintiuno", "veintidós", "veintitrés", "veinticuatro",
            "veinticinco", "veintiséis", "veintisiete", "veintiocho", "veintinueve" };
    private static final String[] DECENAS = { "", "", "", "treinta", "cuarenta", "cincuenta", "sesenta",
            "setenta", "ochenta", "noventa" };

    /** 5 → "5 (cinco)", 21 → "21 (veintiún)": como se escribe delante de "días". */
    static String plazoEnTexto(Integer dias) {
        if (dias == null) return "___";
        int d = dias;
        String letras;
        if (d >= 0 && d < 30) letras = UNIDADES[d];
        else if (d < 100) letras = DECENAS[d / 10] + (d % 10 == 0 ? "" : " y " + UNIDADES[d % 10]);
        else return String.valueOf(d);
        // Delante de un sustantivo masculino: "un día", "veintiún días", "treinta y un días".
        if (letras.endsWith("veintiuno")) letras = letras.replace("veintiuno", "veintiún");
        else if (letras.endsWith("uno")) letras = letras.substring(0, letras.length() - 3) + "un";
        return d + " (" + letras + ")";
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
        /** Vista previa: sin QR ni huella en la franja, y "VISTA PREVIA" de marca de agua. */
        private final boolean vistaPrevia;
        private PdfTemplate totalPaginas;

        Hoja(ActaFaltanteDTO acta, String url, Image qr, Image membrete) {
            this(acta, url, qr, membrete, false);
        }

        Hoja(ActaFaltanteDTO acta, String url, Image qr, Image membrete, boolean vistaPrevia) {
            this.acta = acta;
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

                if (vistaPrevia) {
                    ColumnText.showTextAligned(cb, Element.ALIGN_LEFT, new Phrase(
                            "VISTA PREVIA de la notificación de faltantes — no está registrada en el SCIAF", F_FRANJA_PREVIA),
                            x, y + 40, 0);
                    ColumnText.showTextAligned(cb, Element.ALIGN_LEFT, new Phrase(
                            "No tiene número, QR ni código de verificación: no tiene validez y no se debe entregar.", F_FRANJA),
                            x, y + 29, 0);
                    ColumnText.showTextAligned(cb, Element.ALIGN_LEFT, new Phrase(
                            "Para emitirla: Faltantes › Registrar faltantes › Registrar y emitir notificación.", F_FRANJA),
                            x, y + 18, 0);
                } else {
                    qr.setAbsolutePosition(x, y);
                    cb.addImage(qr);

                    float tx = x + 62;
                    String doc = acta.esNotificacion() ? "Notificación de faltantes " + acta.numeroImpreso()
                            : (acta.esRegularizacion() ? "Acta de regularización de faltantes N° " : "Acta de faltantes N° ")
                                    + acta.numero();
                    ColumnText.showTextAligned(cb, Element.ALIGN_LEFT,
                            new Phrase(doc + " — generada por el SCIAF", F_FRANJA_B), tx, y + 42, 0);
                    ColumnText.showTextAligned(cb, Element.ALIGN_LEFT,
                            new Phrase("Verifique su autenticidad escaneando el código QR o en:", F_FRANJA), tx, y + 32, 0);
                    ColumnText.showTextAligned(cb, Element.ALIGN_LEFT, new Phrase(url, F_FRANJA_B), tx, y + 22, 0);
                    ColumnText.showTextAligned(cb, Element.ALIGN_LEFT,
                            new Phrase("Huella del contenido: " + acta.huella(), F_FRANJA), tx, y + 12, 0);
                }

                String pagina = "Página " + writer.getPageNumber() + " de ";
                float ancho = F_FRANJA.getCalculatedBaseFont(false).getWidthPoint(pagina, F_FRANJA.getSize());
                float px = document.right() - ancho - 14;
                ColumnText.showTextAligned(cb, Element.ALIGN_LEFT, new Phrase(pagina, F_FRANJA), px, y + 2, 0);
                cb.addTemplate(totalPaginas, px + ancho, y + 2);

                if (ActaFaltante.ANULADA.equals(acta.estado())) {
                    marcaDeAgua(writer, document, "ANULADA", 90, new BaseColor(176, 32, 45));
                } else if (vistaPrevia) {
                    marcaDeAgua(writer, document, "VISTA PREVIA", 78, AMBAR);
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

        private void marcaDeAgua(PdfWriter writer, Document document, String texto, float tamano, BaseColor color) {
            PdfContentByte cb = writer.getDirectContent();
            cb.saveState();
            PdfGState gs = new PdfGState();
            gs.setFillOpacity(0.18f);
            cb.setGState(gs);
            Rectangle hoja = document.getPageSize();
            ColumnText.showTextAligned(cb, Element.ALIGN_CENTER,
                    new Phrase(texto, new Font(Font.FontFamily.HELVETICA, tamano, Font.BOLD, color)),
                    hoja.getWidth() / 2, hoja.getHeight() / 2, 45);
            cb.restoreState();
        }
    }
}
