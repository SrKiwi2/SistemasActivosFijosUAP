package com.usic.SistemasActivosFijosUAP.model.service;

import java.io.ByteArrayOutputStream;
import java.util.List;

import org.springframework.stereotype.Service;

import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Document;
import com.itextpdf.text.Element;
import com.itextpdf.text.Font;
import com.itextpdf.text.Image;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.Rectangle;
import com.itextpdf.text.pdf.BarcodeQRCode;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;
import com.usic.SistemasActivosFijosUAP.model.entity.Activo;
import com.usic.SistemasActivosFijosUAP.model.entity.Predio;

/**
 * Hoja A4 de etiquetas con QR (3 × 8), común a Donaciones y Adicionales.
 * <p>
 * El QR lleva el mismo formato que las etiquetas actuales, que ya lee la app móvil
 * ({@code ParserQrActivoService}): {@code sigla|municipio|predio|grupo|código|descripción}.
 * El código va completo, con su letra (D / A): es el que identifica al bien al escanearlo.
 */
@Service
public class PdfEtiquetasQrService {

    private static final int COLUMNAS = 3;
    private static final float ALTO_ETIQUETA = 99f;   // ~35 mm: 8 filas en A4
    private static final BaseColor GRIS = new BaseColor(90, 98, 112);

    /** Una etiqueta: cabecera (p. ej. «UAP · DONACIÓN»), código, descripción, pie y texto del QR. */
    public record Etiqueta(String cabecera, String codigo, String descripcion, String pie, String qr) {}

    /** Arma la etiqueta de un activo con código; {@code predio} da sigla, municipio y nombre. */
    public static Etiqueta deActivo(Activo a, Predio predio, String rotulo, String pie) {
        String sigla = predio != null && predio.getEntidad() != null && predio.getEntidad().getSigla() != null
                ? predio.getEntidad().getSigla().trim() : "UAP";
        String municipio = predio != null && predio.getMunicipio() != null ? limpio(predio.getMunicipio().getNombre()) : "";
        String nombrePredio = predio != null ? limpio(predio.getDescrip()) : "";
        String grupo = a.getGrupoContable() != null ? limpio(a.getGrupoContable().getNombre()) : "";
        String desc = recortar(limpio(a.getDescripcion()), 90);
        String qr = String.join("|", sigla, municipio, nombrePredio, grupo, a.getCodigo(), recortar(desc, 60));
        String piePredio = nombrePredio.isEmpty() ? "" : nombrePredio;
        String pieFinal = pie == null || pie.isBlank() ? piePredio : piePredio.isEmpty() ? pie : pie + " · " + piePredio;
        return new Etiqueta(sigla + " · " + rotulo, a.getCodigo(), desc, pieFinal, qr);
    }

    public byte[] generar(String titulo, List<Etiqueta> etiquetas, BaseColor color) {
        return generar(titulo, etiquetas, color, null);
    }

    /** @param aviso texto opcional arriba de la hoja (p. ej. cuántos marcados se saltaron y por qué). */
    public byte[] generar(String titulo, List<Etiqueta> etiquetas, BaseColor color, String aviso) {
        Font fCab = new Font(Font.FontFamily.HELVETICA, 6.5f, Font.BOLD, color);
        Font fCodigo = new Font(Font.FontFamily.COURIER, 10.5f, Font.BOLD, BaseColor.BLACK);
        Font fDesc = new Font(Font.FontFamily.HELVETICA, 6.2f, Font.NORMAL, BaseColor.BLACK);
        Font fPie = new Font(Font.FontFamily.HELVETICA, 5.6f, Font.NORMAL, GRIS);

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 18, 18, 22, 22);
            PdfWriter.getInstance(doc, out);
            doc.addTitle(titulo);
            doc.addCreator("SCIAF");
            doc.open();
            if (aviso != null && !aviso.isBlank()) {
                Paragraph pa = new Paragraph(aviso, new Font(Font.FontFamily.HELVETICA, 8f, Font.BOLD, new BaseColor(180, 83, 9)));
                pa.setSpacingAfter(6);
                doc.add(pa);
            }

            PdfPTable hoja = new PdfPTable(COLUMNAS);
            hoja.setWidthPercentage(100);
            hoja.getDefaultCell().setBorder(Rectangle.NO_BORDER);   // las celdas de relleno de la última fila
            for (Etiqueta e : etiquetas) {
                Image qr = new BarcodeQRCode(e.qr(), 1, 1, null).getImage();
                qr.scaleAbsolute(66, 66);

                PdfPTable et = new PdfPTable(new float[] { 34, 66 });
                et.setWidthPercentage(100);
                PdfPCell cQr = new PdfPCell(qr, false);
                cQr.setBorder(Rectangle.NO_BORDER);
                cQr.setVerticalAlignment(Element.ALIGN_MIDDLE);
                cQr.setHorizontalAlignment(Element.ALIGN_CENTER);
                et.addCell(cQr);

                PdfPCell cTxt = new PdfPCell();
                cTxt.setBorder(Rectangle.NO_BORDER);
                cTxt.setPaddingLeft(2);
                cTxt.addElement(new Paragraph(e.cabecera(), fCab));
                Paragraph pc = new Paragraph(e.codigo(), fCodigo);
                pc.setSpacingBefore(1);
                cTxt.addElement(pc);
                Paragraph pd = new Paragraph(e.descripcion(), fDesc);
                pd.setLeading(7.2f);
                cTxt.addElement(pd);
                if (e.pie() != null && !e.pie().isBlank()) cTxt.addElement(new Paragraph(e.pie(), fPie));
                et.addCell(cTxt);

                PdfPCell celda = new PdfPCell(et);
                celda.setFixedHeight(ALTO_ETIQUETA);
                celda.setPadding(4);
                celda.setBorderColor(new BaseColor(200, 200, 210));
                celda.setBorderWidth(0.4f);
                celda.setVerticalAlignment(Element.ALIGN_MIDDLE);
                hoja.addCell(celda);
            }
            hoja.completeRow();   // rellena la última fila con celdas vacías
            doc.add(hoja);
            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo generar el PDF de etiquetas: " + e.getMessage(), e);
        }
    }

    public static String limpio(String s) {
        return s == null ? "" : s.trim().replaceAll("\\s+", " ").replace('|', '/');
    }

    private static String recortar(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
