package com.usic.SistemasActivosFijosUAP.model.service;

import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;

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
import com.itextpdf.text.pdf.PdfPCellEvent;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;

@Service
public class PdfGeneratorService {

	public byte[] generarPdfAsignacion(String unidad, String nombreCompleto, String cargo, String ci,
			String extension,
			String ubicacionActivo, String descripcionActivo, String hr) throws Exception {
		Document document = new Document(PageSize.LETTER, 50, 50, 50, 50);
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		PdfWriter writer = PdfWriter.getInstance(document, baos);

		document.open();

		try {
			Path projectPath = Paths.get("").toAbsolutePath();
            String imagePath = projectPath + "/src/main/resources/static/assets/img/fondo/0.jpg";
			Image background = Image.getInstance(imagePath);
			background.setAbsolutePosition(0, 0);
			background.scaleToFit(PageSize.LETTER.getWidth(), PageSize.LETTER.getHeight());
			document.add(background);
		} catch (Exception e) {
			// Log de error si no encuentra imagen
			System.err.println("No se pudo cargar el membrete: " + e.getMessage());
		}

		document.add(new Paragraph("\n\n"));

		// Encabezado personalizado con tamaños distintos
		Paragraph linea1 = new Paragraph("UNIVERSIDAD AMAZÓNICA DE PANDO", new Font(Font.FontFamily.TIMES_ROMAN, 16, Font.BOLD));
		linea1.setAlignment(Element.ALIGN_CENTER);
		document.add(linea1);

		Paragraph linea2 = new Paragraph("SECCIÓN DE ACTIVOS FIJOS", new Font(Font.FontFamily.TIMES_ROMAN, 14, Font.BOLD));
		linea2.setAlignment(Element.ALIGN_CENTER);
		document.add(linea2);

		Paragraph linea3 = new Paragraph("DATOS DEL RESPONSABLE", new Font(Font.FontFamily.TIMES_ROMAN, 12));
		linea3.setAlignment(Element.ALIGN_CENTER);
		document.add(linea3);

		Paragraph linea4 = new Paragraph("PARA ASIGNACIÓN DE BIENES NUEVOS 2025", new Font(Font.FontFamily.TIMES_ROMAN, 12));
		linea4.setAlignment(Element.ALIGN_CENTER);
		document.add(linea4);

		document.add(new Paragraph("\n")); // espacio después del encabezado
				
		// H/R alineado a la derecha
        PdfPTable hrTable = new PdfPTable(1);
		hrTable.setWidthPercentage(30); // solo ocupa el 30% del ancho
		hrTable.setHorizontalAlignment(Element.ALIGN_RIGHT); // alineado a la derecha

		PdfPCell hrCell = new PdfPCell(new Phrase("Nº H/R: " + hr, new Font(Font.FontFamily.TIMES_ROMAN, 12)));
		hrCell.setBorder(Rectangle.NO_BORDER);
		hrCell.setPaddingRight(20f); // margen desde el borde derecho
		hrTable.addCell(hrCell);

		document.add(hrTable);

		PdfPTable tabla = new PdfPTable(2);
        tabla.setWidthPercentage(90);
		tabla.setHorizontalAlignment(Element.ALIGN_CENTER);
        tabla.setSpacingBefore(10f);

		tabla.addCell(celda("DIRECCIÓN, UNIDAD Y/O SECCIÓN", true));
		tabla.addCell(celda(unidad, false));

		tabla.addCell(celda("NOMBRE COMPLETO DEL RESPONSABLE", true));
		tabla.addCell(celda(nombreCompleto, false));

		tabla.addCell(celda("CARGO", true));
		tabla.addCell(celda(cargo, false));

		tabla.addCell(celda("C.I.", true));
        tabla.addCell(celda(ci, false));

		tabla.addCell(celda("EXPEDIDO", true));
        tabla.addCell(celda(extension, false));

		tabla.addCell(celda("UBICACIÓN DEL ACTIVO", true));
		tabla.addCell(celda(ubicacionActivo, false));

		tabla.addCell(celda("DESCRIPCIÓN DEL ACTIVO", true));
		tabla.addCell(celda(descripcionActivo, false));

		document.add(tabla);

		PdfPTable leyendaTable = new PdfPTable(1);
		leyendaTable.setWidthPercentage(93); // achica ancho
		leyendaTable.setHorizontalAlignment(Element.ALIGN_CENTER);

		Font negrita = new Font(Font.FontFamily.TIMES_ROMAN, 11, Font.BOLD);
		Paragraph leyenda = new Paragraph(
			"De acuerdo al D.S. 0181 Art. 146 (Asignación de Activos Fijos Muebles) I. " +
			"La asignación de activos fijos muebles es el acto administrativo mediante el cual se entrega a un servidor público un activo o conjunto de estos, " +
			"generando la consiguiente responsabilidad sobre su debido uso y custodia.",
			negrita);
		leyenda.setAlignment(Element.ALIGN_JUSTIFIED);

		PdfPCell celdaLeyenda = new PdfPCell();
		celdaLeyenda.addElement(leyenda);
		celdaLeyenda.setBorder(Rectangle.NO_BORDER);
		celdaLeyenda.setPadding(8);
		leyendaTable.addCell(celdaLeyenda);

		document.add(leyendaTable);

		document.close();
		return baos.toByteArray();
	}
	
	private PdfPCell celda(String texto, boolean encabezado) {
		Font font = encabezado ? new Font(Font.FontFamily.TIMES_ROMAN, 10, Font.BOLD)
				: new Font(Font.FontFamily.TIMES_ROMAN, 10);
		PdfPCell cell = new PdfPCell(new Phrase(texto, font));
		cell.setPadding(15);
		cell.setBorder(Rectangle.NO_BORDER); // Desactiva el borde normal
		cell.setCellEvent(new DottedBorder()); // Aplica borde punteado
		return cell;
	}

	class DottedBorder implements PdfPCellEvent {
		public void cellLayout(PdfPCell cell, Rectangle rect, PdfContentByte[] canvas) {
			PdfContentByte cb = canvas[PdfPTable.LINECANVAS];
			cb.setLineDash(2f, 2f); // Define punteado (longitud punto, espacio)
			cb.setLineWidth(0.05f); // Grosor del borde
			cb.rectangle(rect.getLeft(), rect.getBottom(), rect.getWidth(), rect.getHeight());
			cb.stroke();
			cb.setLineDash(0); // Resetea el dash después
		}
	}
}