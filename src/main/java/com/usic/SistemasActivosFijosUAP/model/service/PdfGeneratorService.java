package com.usic.SistemasActivosFijosUAP.model.service;

import java.io.ByteArrayOutputStream;

import org.springframework.stereotype.Service;

import com.itextpdf.text.Document;
import com.itextpdf.text.Element;
import com.itextpdf.text.Font;
import com.itextpdf.text.Image;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.pdf.PdfPCell;
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
			String imagePath = "/home/usic03/Documentos/SISTEMAS USIC/SistemasActivosFijosUAP/src/main/resources/static/assets/img/fondo/0.jpg"; // Ruta relativa o
																					// absoluta
			Image background = Image.getInstance(imagePath);
			background.setAbsolutePosition(0, 0);
			background.scaleToFit(PageSize.LETTER.getWidth(), PageSize.LETTER.getHeight());
			document.add(background);
		} catch (Exception e) {
			// Log de error si no encuentra imagen
			System.err.println("No se pudo cargar el membrete: " + e.getMessage());
		}

		document.add(new Paragraph("\n\n\n\n\n"));

		// Encabezado
		Font tituloFont = new Font(Font.FontFamily.HELVETICA, 12, Font.BOLD);
		Paragraph encabezado = new Paragraph("UNIVERSIDAD AMAZÓNICA DE PANDO\nSECCIÓN DE ACTIVOS FIJOS\n\n" +
				"DATOS DEL RESPONSABLE PARA ASIGNACIÓN DE BIENES NUEVOS 2025", tituloFont);
		encabezado.setAlignment(Element.ALIGN_CENTER);
		document.add(encabezado);

		// H/R alineado a la derecha
        Paragraph numeroHR = new Paragraph("Nº H/R: " + hr, new Font(Font.FontFamily.HELVETICA, 10));
        numeroHR.setAlignment(Element.ALIGN_RIGHT);
        document.add(numeroHR);

		document.add(new Paragraph("\n"));

		PdfPTable tabla = new PdfPTable(2);
        tabla.setWidthPercentage(100);
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

		document.add(new Paragraph("\n"));

		Font negrita = new Font(Font.FontFamily.HELVETICA, 10, Font.BOLD);
		Paragraph leyenda = new Paragraph(
				"De acuerdo al D.S. 0181 Art. 146 (Asignación de Activos Fijos Muebles) I. " +
						"La asignación de activos fijos muebles es el acto administrativo mediante el cual se entrega a un servidor público un activo o conjunto de estos, "
						+
						"generando la consiguiente responsabilidad sobre su debido uso y custodia.",
				negrita);
		leyenda.setAlignment(Element.ALIGN_JUSTIFIED);
		document.add(leyenda);

		document.close();
		return baos.toByteArray();
	}

	private PdfPCell celda(String texto, boolean encabezado) {
		Font font = encabezado ? new Font(Font.FontFamily.HELVETICA, 10, Font.BOLD)
				: new Font(Font.FontFamily.HELVETICA, 10);
		PdfPCell cell = new PdfPCell(new Phrase(texto, font));
		cell.setPadding(8);
		return cell;
	}
}
