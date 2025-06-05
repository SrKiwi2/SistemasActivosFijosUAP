package com.usic.SistemasActivosFijosUAP.model.service;

import java.io.ByteArrayOutputStream;

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
import com.itextpdf.text.pdf.PdfContentByte;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;
import com.usic.SistemasActivosFijosUAP.model.entity.Responsable;

@Service
public class PdfTransferenciaService {

    public byte[] generarPdfTransferencia(
            String unidadOrigen, Responsable responsableOrigen, String fechaTransferencia,
            String unidadDestino, Responsable responsableDestino, String fechaRecepcion,
            String codigoActivo, String descripcionActivoT, String ubicacionOrigen, String ubicacionActual) throws Exception {

        // Usamos hoja carta horizontal con márgenes de 36 puntos (~0.5 inch)
        Rectangle pageSize = PageSize.LETTER.rotate();
        Document document = new Document(pageSize, 36, 36, 36, 36);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter writer = PdfWriter.getInstance(document, baos);
        document.open();

        // Fondo
        try {
            String imagePath = "/home/usic03/Documentos/SISTEMAS USIC/SistemasActivosFijosUAP/src/main/resources/static/assets/img/fondo/1.png";
            Image background = Image.getInstance(imagePath);
            background.scaleAbsolute(PageSize.LETTER.getHeight(), PageSize.LETTER.getWidth()); // rotado
            background.setAbsolutePosition(0, 0);

            PdfContentByte canvas = writer.getDirectContentUnder();
            canvas.addImage(background);
        } catch (Exception e) {
            System.err.println("No se pudo cargar el membrete: " + e.getMessage());
        }

        document.add(new Paragraph("\n\n"));

        // Fuentes
        Font normal = new Font(Font.FontFamily.TIMES_ROMAN, 12);
        Font normal_titulo = new Font(Font.FontFamily.TIMES_ROMAN, 14);
        Font negrita = new Font(Font.FontFamily.TIMES_ROMAN, 12, Font.BOLD);
        Font titulo = new Font(Font.FontFamily.TIMES_ROMAN, 16, Font.BOLD);

        // Encabezado
        Paragraph linea1 = new Paragraph("UNIVERSIDAD AMAZÓNICA DE PANDO", normal_titulo);
        linea1.setAlignment(Element.ALIGN_CENTER);
        document.add(linea1);

        Paragraph linea2 = new Paragraph("SECCIÓN DE ACTIVOS FIJOS", normal_titulo);
        linea2.setAlignment(Element.ALIGN_CENTER);
        document.add(linea2);

        Paragraph linea3 = new Paragraph("TRANSFERENCIA DE BIENES", titulo);
        linea3.setAlignment(Element.ALIGN_CENTER);
        document.add(linea3);

        document.add(new Paragraph("\n"));
        // Tabla de datos (4 columnas)
        PdfPTable tabla = new PdfPTable(4);
        tabla.setWidthPercentage(100);
        tabla.setSpacingBefore(10f);
        tabla.setSpacingAfter(10f);
        tabla.setWidths(new float[]{2.5f, 4f, 2.5f, 4f}); // columnas equilibradas

        // Fila 1: encabezados combinados
        PdfPCell celda1 = new PdfPCell(new Phrase("TRANSFIERE", negrita));
        celda1.setColspan(2);
        celda1.setHorizontalAlignment(Element.ALIGN_CENTER);
        celda1.setBackgroundColor(BaseColor.LIGHT_GRAY);
        tabla.addCell(celda1);

        PdfPCell celda2 = new PdfPCell(new Phrase("RECIBE Y RESGUARDA", negrita));
        celda2.setColspan(2);
        celda2.setHorizontalAlignment(Element.ALIGN_CENTER);
        celda2.setBackgroundColor(BaseColor.LIGHT_GRAY);
        tabla.addCell(celda2);

        // Fila 2
        tabla.addCell(new Phrase("DIRECCION, UNIDAD Y/O SECCION:", negrita));
        tabla.addCell(new Phrase(unidadOrigen, normal));
        tabla.addCell(new Phrase("DIRECCION, UNIDAD Y/O SECCION:", negrita));
        tabla.addCell(new Phrase(unidadDestino, normal));

        // Fila 3
        tabla.addCell(new Phrase("FIRMA DEL INMEDIATO SUPERIOR:", negrita));
        tabla.addCell(new Phrase(""));
        tabla.addCell(new Phrase("FIRMA DEL INMEDIATO SUPERIOR::", negrita));
        tabla.addCell(new Phrase(""));

        // Fila 4
        tabla.addCell(new Phrase("NOMBRE O PIE DE FIRMA DEL RESPONSABLE:", negrita));
        tabla.addCell(new Phrase(responsableOrigen.getPersona().getNombreCompleto(), normal));
        tabla.addCell(new Phrase("NOMBRE O PIE DE FIRMA DEL RESPONSABLE:", negrita));
        tabla.addCell(new Phrase(responsableDestino.getPersona().getNombreCompleto(), normal));

        // Fila 5
        tabla.addCell(new Phrase("FIRMA DEL RESPONSABLE:", negrita));
        tabla.addCell(new Phrase(ubicacionOrigen, normal));
        tabla.addCell(new Phrase("FIRMA DEL RESPONSABLE:", negrita));
        tabla.addCell(new Phrase(ubicacionActual, normal));

        // Fila 6
        tabla.addCell(new Phrase("FECHA DE LA TRANSFERENCIA:", negrita));
        tabla.addCell(new Phrase(fechaTransferencia, normal));
        tabla.addCell(new Phrase("FECHA DE LA RECEPCIÓN:", negrita));
        tabla.addCell(new Phrase(fechaRecepcion, normal));

        // Agregar tabla
        document.add(tabla);

        document.close();
        return baos.toByteArray();
    }
}
