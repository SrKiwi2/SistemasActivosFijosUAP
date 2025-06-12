package com.usic.SistemasActivosFijosUAP.model.service;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

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
import com.usic.SistemasActivosFijosUAP.model.entity.Activo;
import com.usic.SistemasActivosFijosUAP.model.entity.Responsable;

@Service
public class PdfBajaActivoService {
    
    public byte[] generarPDfBajaActivo(
        String fechaBaja,
        String numeroDocumento,
        Responsable responsbaleBaja,
        Optional<Activo> activoBaja,
        String causa,
        String descripcionBaja)throws Exception{

            Document document = new Document(PageSize.LETTER, 50, 50, 50, 50);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PdfWriter writer = PdfWriter.getInstance(document, baos);
    
            document.open();

            String imagePath = "/home/usic03/Documentos/SISTEMAS USIC/SistemasActivosFijosUAP/src/main/resources/static/assets/img/fondo/0.jpg"; // Ruta relativa o
            // absoluta
            agregarFondo(writer, imagePath);

        document.add(new Paragraph("\n\n"));

		// Encabezado personalizado con tamaños distintos
		Paragraph linea1 = new Paragraph("UNIVERSIDAD AMAZÓNICA DE PANDO", new Font(Font.FontFamily.TIMES_ROMAN, 13, Font.BOLD));
		linea1.setAlignment(Element.ALIGN_CENTER);
		document.add(linea1);

		Paragraph linea2 = new Paragraph("UNIDAD BIENES Y SERVICIOS", new Font(Font.FontFamily.TIMES_ROMAN, 13, Font.BOLD));
		linea2.setAlignment(Element.ALIGN_CENTER);
		document.add(linea2);

		Paragraph linea3 = new Paragraph("SECCIÓN DE ACTIVOS FIJOS", new Font(Font.FontFamily.TIMES_ROMAN, 12, Font.BOLD));
		linea3.setAlignment(Element.ALIGN_CENTER);
		document.add(linea3);

        document.add(new Paragraph("\n"));

		Paragraph linea4 = new Paragraph("SOLICITUD DE BAJA DE BIENES", new Font(Font.FontFamily.TIMES_ROMAN, 12, Font.BOLD));
		linea4.setAlignment(Element.ALIGN_CENTER);
		document.add(linea4);

        // Espacio entre párrafo y fila
        document.add(new Paragraph("\n"));

        // Crear una tabla de 2 columnas sin bordes para mostrar FECHA y UNIDAD
        PdfPTable tablaInfo = new PdfPTable(2);
        tablaInfo.setWidthPercentage(100);

        // Primera celda: FECHA a la izquierda
        PdfPCell celdaFecha = new PdfPCell(new Phrase("FECHA: " + fechaBaja, new Font(Font.FontFamily.TIMES_ROMAN, 11)));
        celdaFecha.setBorder(Rectangle.NO_BORDER);
        celdaFecha.setHorizontalAlignment(Element.ALIGN_LEFT);

        // Segunda celda: UNIDAD a la derecha
        PdfPCell celdaUnidad = new PdfPCell(new Phrase("Nº " + numeroDocumento, new Font(Font.FontFamily.TIMES_ROMAN, 11)));
        celdaUnidad.setBorder(Rectangle.NO_BORDER);
        celdaUnidad.setHorizontalAlignment(Element.ALIGN_RIGHT);

        tablaInfo.addCell(celdaFecha);
        tablaInfo.addCell(celdaUnidad);

        document.add(tablaInfo);

        Paragraph autorizacion = new Paragraph(
            "SE AUTORIZA EL INGRESO DEL ACTIVO CON LAS SIGUIENTES CARACTERISTICAS:",
            new Font(Font.FontFamily.TIMES_ROMAN, 12, Font.BOLD)
        );
        autorizacion.setAlignment(Element.ALIGN_LEFT);
        autorizacion.setSpacingBefore(15f);
        document.add(autorizacion);

        Paragraph nota = new Paragraph(
            "NOTA: SE LE OTORGARA EL PERMISO PARA EL INGRESO DEL ACTIVO POR EL LAPSO DE 3 MESES A PARTIR DE LA FECHA, " +
            "EL PROPIETARIO DEBERA RENOVAR SU PERMISO DE INGRESO DEL ACTIVO, DE NO SER ASI SE LO TOMARA COMO PARTE DE LOS ACTIVOS DE LA UNIVERSIDAD AMAZONICA DE PANDO.",
            new Font(Font.FontFamily.TIMES_ROMAN, 10, Font.NORMAL)
        );
        nota.setAlignment(Element.ALIGN_JUSTIFIED);
        nota.setSpacingBefore(10f);
        
        document.add(nota);

        document.close();

        return baos.toByteArray();
    }

    private void agregarFondo(PdfWriter writer, String imagePath) {
        try {
            PdfContentByte canvas = writer.getDirectContentUnder();
            Image background = Image.getInstance(imagePath);
            background.setAbsolutePosition(0, 0);
            background.scaleToFit(PageSize.LETTER.getWidth(), PageSize.LETTER.getHeight());
            canvas.addImage(background);
        } catch (Exception e) {
            System.err.println("No se pudo cargar el membrete: " + e.getMessage());
        }
    }
}
