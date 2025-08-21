package com.usic.SistemasActivosFijosUAP.model.service.interno;

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
import com.itextpdf.text.pdf.BarcodeQRCode;
import com.itextpdf.text.pdf.PdfContentByte;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPCellEvent;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;
import com.usic.SistemasActivosFijosUAP.model.entity.Persona;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PdfInternoService {
    
    public byte[] pdfActivoNuevo(Usuario usuario, String unidad, String nombreCompleto, String cargo, String ci,
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
        tabla.setWidthPercentage(95);
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
		leyendaTable.addCell(celdaLeyenda);

		document.add(leyendaTable);

       // --------- BLOQUE DE FIRMAS Y QRs ---------
        Font fText = new Font(Font.FontFamily.TIMES_ROMAN, 7); // un poco más legible que 6

        // Datos del USUARIO (inmediato superior)
        final Persona pUser = (usuario != null && usuario.getPersona() != null) ? usuario.getPersona() : null;
        final String userNombre = pUser != null ? nvl(pUser.getNombreCompleto()) : "N/D";
        final String userCi     = pUser != null ? nvl(pUser.getCi())             : "N/D";

        // Datos del RESPONSABLE
        final String respNombre = nvl(nombreCompleto);
        final String respCi     = nvl(ci);

        final String userNombreUC = nvl(userNombre).toUpperCase();
        final String respNombreUC = nvl(respNombre).toUpperCase();

        // ---------- Payloads en TEXTO PLANO (sin JSON) ----------
        String payloadSuperior =
                "FIRMA ASIGNACIÓN - INMEDIATO SUPERIOR\n" +
                "Nombre: " + userNombreUC + "\n" +
                "CI: " + userCi + "\n" +
                "Unidad: " + nvl(unidad) + "\n" +
                "H.R.: " + nvl(hr) + "\n" +
                "Fecha: " + today();

        String payloadResponsable =
                "FIRMA ASIGNACIÓN - RESPONSABLE\n" +
                "Nombre: " + respNombreUC + "\n" +
                "CI: " + respCi + "\n" +
                "Unidad: " + nvl(unidad) + "\n" +
                "H.R.: " + nvl(hr) + "\n" +
                "Fecha: " + today();

        // QRs (recomiendo 20x20 si notas que 15x15 no escanea bien)
        Image qrSuperior    = qr(payloadSuperior, 120);
        Image qrResponsable = qr(payloadResponsable, 120);
        qrSuperior.setInterpolation(true);
        qrResponsable.setInterpolation(true);
        qrSuperior.scaleAbsolute(15, 15);     // prueba 20,20 si falla lectura
        qrResponsable.scaleAbsolute(15, 15);

        // --------- Tabla de 3 columnas (gutter al centro) ---------
        PdfPTable firmas = new PdfPTable(3);
        firmas.setKeepTogether(true);
        firmas.setWidthPercentage(50);            // +aire general; sube/baja 45–55 a gusto
        firmas.setHorizontalAlignment(Element.ALIGN_CENTER);
        firmas.setSpacingBefore(10f);
        // 1f (col izquierda), 0.35f (espaciador), 1f (col derecha)
        firmas.setWidths(new float[]{1f, 1f, 1f});

        // padding homogéneo (pegamos nombres al QR con poco padding)
        float padTopQR = 4f;
        float padBottomQR = 1f;   // <— reduce separación QR -> texto
        float padTopTexto = 0f;   // <— texto más pegado al QR
        float padBottomTexto = 4f;

        // -------- Columna IZQUIERDA: Inmediato Superior --------
        {
            PdfPTable col = new PdfPTable(1);
            col.setWidthPercentage(100);

            PdfPCell qrCell = new PdfPCell(qrSuperior, true);
            qrCell.setBorder(Rectangle.NO_BORDER);
            qrCell.setHorizontalAlignment(Element.ALIGN_LEFT);
            qrCell.setPaddingTop(padTopQR);
            qrCell.setPaddingBottom(padBottomQR);
            col.addCell(qrCell);

            Paragraph datos = new Paragraph(
                String.format("%s\nC.I.: %s", userNombreUC, userCi), fText
            );
            datos.setAlignment(Element.ALIGN_CENTER);
            datos.setLeading(8f); // interlineado bajo (= texto más compacto)

            PdfPCell dataCell = cellNoBorder(datos);
            dataCell.setPaddingTop(padTopTexto);
            dataCell.setPaddingBottom(padBottomTexto);
            col.addCell(dataCell);

            firmas.addCell(cellNoBorder(col));
        }

        // -------- Columna CENTRAL: ESPACIADOR --------
        PdfPCell spacer = new PdfPCell(new Phrase(""));
        spacer.setBorder(Rectangle.NO_BORDER);
        spacer.setMinimumHeight(1f);  // no ocupa alto, solo ancho de columna
        firmas.addCell(spacer);

        // -------- Columna DERECHA: Responsable --------
        {
            PdfPTable col = new PdfPTable(1);
            col.setWidthPercentage(100);

            PdfPCell qrCell = new PdfPCell(qrResponsable, true);
            qrCell.setBorder(Rectangle.NO_BORDER);
            qrCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            qrCell.setPaddingTop(padTopQR);
            qrCell.setPaddingBottom(padBottomQR);
            col.addCell(qrCell);

            Paragraph datos = new Paragraph(
                String.format("%s\nC.I.: %s", respNombreUC, respCi), fText
            );
            datos.setAlignment(Element.ALIGN_CENTER);
            datos.setLeading(8f);

            PdfPCell dataCell = cellNoBorder(datos);
            dataCell.setPaddingTop(padTopTexto);
            dataCell.setPaddingBottom(padBottomTexto);
            col.addCell(dataCell);

            firmas.addCell(cellNoBorder(col));
        }

        document.add(firmas);

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

    private static String nvl(String s) { return s == null ? "" : s; }
    private static String today() { return java.time.LocalDate.now().toString(); }

    // Crea imagen QR (iText 5)
    private Image qr(String payload, int size) throws Exception {
        BarcodeQRCode qr = new BarcodeQRCode(payload, size, size, null);
        Image img = qr.getImage();
        return img;
    }

    // Celda sin bordes (para maquetar firmas/QR)
    private PdfPCell cellNoBorder(Element element) {
        PdfPCell c = new PdfPCell();
        c.setBorder(Rectangle.NO_BORDER);
        c.addElement(element);
        return c;
    }
}
