package com.usic.SistemasActivosFijosUAP.model.service.donacion;

import java.io.ByteArrayOutputStream;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
import com.usic.SistemasActivosFijosUAP.model.dao.IDonacionActaDao;
import com.usic.SistemasActivosFijosUAP.model.dao.IDonacionBienDao;
import com.usic.SistemasActivosFijosUAP.model.entity.Activo;
import com.usic.SistemasActivosFijosUAP.model.entity.DonacionActa;
import com.usic.SistemasActivosFijosUAP.model.entity.DonacionBien;
import com.usic.SistemasActivosFijosUAP.model.entity.Predio;

import lombok.RequiredArgsConstructor;

/**
 * Etiquetas con QR de los bienes de un acta de donación: hoja A4 de 3 × 8 etiquetas.
 * <p>
 * El QR lleva el mismo formato que las etiquetas actuales, que ya lee la app móvil
 * ({@code ParserQrActivoService}): {@code sigla|municipio|predio|grupo|código|descripción}.
 * Cada etiqueta dice además «DONACIÓN» para distinguirla a simple vista.
 */
@Service
@RequiredArgsConstructor
public class PdfEtiquetasDonacionService {

    private static final int COLUMNAS = 3;
    private static final float ALTO_ETIQUETA = 99f;   // ~35 mm: 8 filas en A4
    private static final BaseColor VIOLETA = new BaseColor(124, 58, 237);
    private static final BaseColor GRIS = new BaseColor(90, 98, 112);

    private final IDonacionActaDao actaDao;
    private final IDonacionBienDao bienDao;

    @Transactional(readOnly = true)
    public byte[] generar(Long idDonacion) {
        DonacionActa acta = actaDao.buscarConDestino(idDonacion)
                .orElseThrow(() -> new IllegalArgumentException("La donación no existe."));
        if (DonacionActa.ANULADA.equals(acta.getEstado())) {
            throw new IllegalArgumentException("La donación " + acta.getNumero() + " está anulada: no se imprimen etiquetas.");
        }
        List<DonacionBien> bienes = bienDao.listarDeActa(idDonacion).stream()
                .filter(b -> Activo.ESTADO_ACTIVO.equals(b.getActivo().getEstado()) && b.getActivo().getCodigo() != null)
                .toList();
        if (bienes.isEmpty()) throw new IllegalArgumentException("La donación no tiene bienes vigentes para etiquetar.");

        Predio predio = acta.getOficina() != null ? acta.getOficina().getPredio() : null;
        String sigla = predio != null && predio.getEntidad() != null && predio.getEntidad().getSigla() != null
                ? predio.getEntidad().getSigla().trim() : "UAP";
        String municipio = predio != null && predio.getMunicipio() != null ? limpio(predio.getMunicipio().getNombre()) : "";
        String nombrePredio = predio != null ? limpio(predio.getDescrip()) : "";

        Font fCab = new Font(Font.FontFamily.HELVETICA, 6.5f, Font.BOLD, VIOLETA);
        Font fCodigo = new Font(Font.FontFamily.COURIER, 10.5f, Font.BOLD, BaseColor.BLACK);
        Font fDesc = new Font(Font.FontFamily.HELVETICA, 6.2f, Font.NORMAL, BaseColor.BLACK);
        Font fPie = new Font(Font.FontFamily.HELVETICA, 5.6f, Font.NORMAL, GRIS);

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 18, 18, 22, 22);
            PdfWriter.getInstance(doc, out);
            doc.addTitle("Etiquetas " + acta.getNumero());
            doc.addCreator("SCIAF");
            doc.open();

            PdfPTable hoja = new PdfPTable(COLUMNAS);
            hoja.setWidthPercentage(100);
            hoja.getDefaultCell().setBorder(Rectangle.NO_BORDER);   // las celdas de relleno de la última fila
            for (DonacionBien b : bienes) {
                Activo a = b.getActivo();
                String grupo = a.getGrupoContable() != null ? limpio(a.getGrupoContable().getNombre()) : "";
                String desc = recortar(limpio(a.getDescripcion()), 90);
                String qrTexto = String.join("|", sigla, municipio, nombrePredio, grupo, a.getCodigo(), recortar(desc, 60));

                Image qr = new BarcodeQRCode(qrTexto, 1, 1, null).getImage();
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
                cTxt.addElement(new Paragraph(sigla + " · DONACIÓN", fCab));
                Paragraph pc = new Paragraph(a.getCodigo(), fCodigo);
                pc.setSpacingBefore(1);
                cTxt.addElement(pc);
                Paragraph pd = new Paragraph(desc, fDesc);
                pd.setLeading(7.2f);
                cTxt.addElement(pd);
                cTxt.addElement(new Paragraph(acta.getNumero() + " · " + nombrePredio, fPie));
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
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo generar el PDF de etiquetas: " + e.getMessage(), e);
        }
    }

    private static String limpio(String s) {
        return s == null ? "" : s.trim().replaceAll("\\s+", " ").replace('|', '/');
    }

    private static String recortar(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
