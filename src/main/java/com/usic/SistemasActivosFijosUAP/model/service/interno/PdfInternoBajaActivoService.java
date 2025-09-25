package com.usic.SistemasActivosFijosUAP.model.service.interno;

import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.Locale;

import org.springframework.stereotype.Service;

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
import com.usic.SistemasActivosFijosUAP.model.entity.Activo;
import com.usic.SistemasActivosFijosUAP.model.entity.Responsable;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PdfInternoBajaActivoService {
    public byte[] generarPDfBajaActivo(
        String fechaBaja,
        String numeroDocumento,
        Responsable responsbaleBaja,
        Activo descripcionActivo,
        String causa,
        String descripcionBaja)throws Exception{

            Document document = new Document(PageSize.LETTER, 50, 50, 50, 50);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PdfWriter writer = PdfWriter.getInstance(document, baos);
    
            document.open();

            Path projectPath = Paths.get("").toAbsolutePath();
            String imagePath = projectPath + "/src/main/resources/static/assets/img/fondo/0.jpg";            // absoluta
            agregarFondo(writer, imagePath);

        document.add(new Paragraph("\n\n"));

		// Encabezado personalizado con tamaños distintos
		Paragraph linea1 = new Paragraph("UNIVERSIDAD AMAZÓNICA DE PANDO", new Font(Font.FontFamily.TIMES_ROMAN, 12, Font.BOLD));
		linea1.setAlignment(Element.ALIGN_CENTER);
		document.add(linea1);

		Paragraph linea3 = new Paragraph("SECCIÓN DE ACTIVOS FIJOS", new Font(Font.FontFamily.TIMES_ROMAN, 11, Font.BOLD));
		linea3.setAlignment(Element.ALIGN_CENTER);
		document.add(linea3);

		Paragraph linea4 = new Paragraph("SOLICITUD DE BAJA DE BIENES", new Font(Font.FontFamily.TIMES_ROMAN, 12, Font.BOLD));
		linea4.setAlignment(Element.ALIGN_CENTER);
		document.add(linea4);

        // Espacio entre párrafo y fila
        document.add(new Paragraph("\n"));

        // Crear una tabla de 2 columnas sin bordes para mostrar FECHA y UNIDAD
        PdfPTable tablaInfo = new PdfPTable(2);
        tablaInfo.setWidthPercentage(80);

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

        // Espacio antes de la siguiente sección
        document.add(new Paragraph("\n"));

        // Crear una tabla de una sola columna
        PdfPTable tablaOficina = new PdfPTable(1);
        tablaOficina.setWidthPercentage(90); // No tan ancha, centrada
        tablaOficina.setHorizontalAlignment(Element.ALIGN_CENTER);
        tablaOficina.setSpacingBefore(10f);

        // Contenido: una sola celda con etiqueta + valor
        String nombreOficina = responsbaleBaja.getOficina().getNombre(); // Ajusta según tu modelo
        Phrase contenidoOficina = new Phrase("NOMBRE DE OFICINA: " + nombreOficina, new Font(Font.FontFamily.TIMES_ROMAN, 10, Font.NORMAL));

        PdfPCell celda = new PdfPCell(contenidoOficina);
        celda.setBorder(Rectangle.NO_BORDER);
        celda.setHorizontalAlignment(Element.ALIGN_CENTER);

        tablaOficina.addCell(celda);
        document.add(tablaOficina);

        document.add(new Paragraph("\n"));

        // Agregar el párrafo con el texto solicitado
        Paragraph parrafoSolicitud = new Paragraph(
            "POR MEDIO DE LA PRESENTE, SOLICITO SE REGISTRE LA BAJA DEL BIEN DE ACTIVO FIJO QUE A CONTINUACION SE DETALLA:",
            new Font(Font.FontFamily.TIMES_ROMAN, 10, Font.NORMAL)
        );
        parrafoSolicitud.setAlignment(Element.ALIGN_JUSTIFIED);
        document.add(parrafoSolicitud);

        document.add(new Paragraph("\n"));

        // Crear una tabla de una sola columna con 90% del ancho del documento
        PdfPTable tablaDetalleActivo = new PdfPTable(1);
        tablaDetalleActivo.setWidthPercentage(100);

        // Primera fila: DESCRIPCIÓN
        Phrase descripcionFrase = new Phrase();
        descripcionFrase.add(new Chunk("DESCRIPCIÓN: ", new Font(Font.FontFamily.TIMES_ROMAN, 10, Font.BOLD)));
        descripcionFrase.add(new Chunk(descripcionActivo.getDescripcion(), new Font(Font.FontFamily.TIMES_ROMAN, 10)));
        PdfPCell celdaDescripcion = new PdfPCell(descripcionFrase);
        celdaDescripcion.setPaddingTop(8f);
        celdaDescripcion.setPaddingBottom(8f);
        celdaDescripcion.setHorizontalAlignment(Element.ALIGN_JUSTIFIED);
        celdaDescripcion.setVerticalAlignment(Element.ALIGN_MIDDLE);
        tablaDetalleActivo.addCell(celdaDescripcion);

        // Segunda fila: CÓDIGO
        Phrase codigoFrase = new Phrase();
        codigoFrase.add(new Chunk("CÓDIGO: ", new Font(Font.FontFamily.TIMES_ROMAN, 10, Font.BOLD)));
        codigoFrase.add(new Chunk(descripcionActivo.getCodigo(), new Font(Font.FontFamily.TIMES_ROMAN, 10)));
        PdfPCell celdaCodigo = new PdfPCell(codigoFrase);
        celdaCodigo.setPaddingTop(8f);
        celdaCodigo.setPaddingBottom(8f);
        celdaCodigo.setHorizontalAlignment(Element.ALIGN_JUSTIFIED);
        celdaCodigo.setVerticalAlignment(Element.ALIGN_MIDDLE);
        tablaDetalleActivo.addCell(celdaCodigo);

        // Agregar la tabla al documento
        document.add(tablaDetalleActivo);

        // Convertir la fecha de baja a LocalDate
        LocalDate fecha = descripcionActivo.getFechaAdquisicion(); // Ej: "2022-06-10"

        // Obtener partes de la fecha
        String diaAdquisicion = String.valueOf(fecha.getDayOfMonth());
        String mesAdquisicion = fecha.getMonth().getDisplayName(TextStyle.FULL, new Locale("es")).toUpperCase(); // Ej: "junio"
        String anioAdquisicion = String.valueOf(fecha.getYear());

        String nombreResponsable = responsbaleBaja.getPersona().getNombreCompleto();
        String numeroFuncionario = responsbaleBaja.getCodigoFuncionario();

        Paragraph parrafoRegistro = new Paragraph();
        parrafoRegistro.setAlignment(Element.ALIGN_JUSTIFIED);
        Font normalFont = new Font(Font.FontFamily.TIMES_ROMAN, 10);
        Font boldFont = new Font(Font.FontFamily.TIMES_ROMAN, 10, Font.BOLD);

        // Texto inicial
        parrafoRegistro.add(new Chunk("EL BIEN ANTES DESCRITO FUE ADQUIRIDO Y REGISTRADO COMO BIEN DE ACTIVO FIJO EL DÍA ", normalFont));

        // Día en normal
        parrafoRegistro.add(new Chunk(diaAdquisicion, boldFont));
        parrafoRegistro.add(new Chunk(" DE ", normalFont));
        // Mes en negrita y mayúsculas
        parrafoRegistro.add(new Chunk(mesAdquisicion, boldFont));
        parrafoRegistro.add(new Chunk(" DEL ", normalFont));
        // Año en normal
        parrafoRegistro.add(new Chunk(anioAdquisicion, boldFont));
        parrafoRegistro.add(new Chunk(", SIENDO RESPONSABLE EL SR. ", normalFont));
        // Nombre responsable en negrita
        parrafoRegistro.add(new Chunk(nombreResponsable, boldFont));

        // Continúa texto normal
        parrafoRegistro.add(new Chunk(" CON NÚMERO DE FUNCIONARIO (A) ", normalFont));
        parrafoRegistro.add(new Chunk(numeroFuncionario, boldFont));
        // Número funcionario en negrita
        parrafoRegistro.add(new Chunk(" ADSCRITO EN LA ", normalFont));

        // Unidad responsable en negrita
        parrafoRegistro.add(new Chunk(responsbaleBaja.getOficina().getNombre(), boldFont));
        parrafoRegistro.add(new Chunk(" DE LA UNIVERSIDAD AMAZÓNICA DE PANDO.", normalFont));

        document.add(parrafoRegistro);

        // Crear tabla de una columna para causas con alto fijo
        PdfPTable tablaCausa = new PdfPTable(1);
        tablaCausa.setWidthPercentage(100);
        tablaCausa.setSpacingBefore(15f); // espacio antes de la tabla

        // Celda con título en negrita
        Font fontTitulo = new Font(Font.FontFamily.TIMES_ROMAN, 10, Font.BOLD);
        PdfPCell celdaTitulo = new PdfPCell(new Phrase("CAUSAS QUE ORIGINARON LA BAJA:", fontTitulo));
        celdaTitulo.setBorder(Rectangle.BOX);
        celdaTitulo.setHorizontalAlignment(Element.ALIGN_LEFT);
        celdaTitulo.setPadding(8f);
        tablaCausa.addCell(celdaTitulo);

        // Celda con la variable causas, texto justificado y alto mínimo para verse bien
        Font fontTexto = new Font(Font.FontFamily.TIMES_ROMAN, 10);
        PdfPCell celdaTexto = new PdfPCell(new Phrase(descripcionBaja, fontTexto));
        celdaTexto.setBorder(Rectangle.BOX);
        celdaTexto.setPadding(10f);
        //celdaTexto.setMinimumHeight(80f);  // altura mínima para que se vea bien
        celdaTexto.setVerticalAlignment(Element.ALIGN_TOP);
        celdaTexto.setHorizontalAlignment(Element.ALIGN_JUSTIFIED);
        tablaCausa.addCell(celdaTexto);

        document.add(tablaCausa);

        // Espacio antes de firmas
        document.add(new Paragraph("\n"));

        // Crear tabla con 2 columnas para los pies de firma
        PdfPTable tablaFirmas = new PdfPTable(2);
        tablaFirmas.setWidthPercentage(100);
        tablaFirmas.setSpacingBefore(10f);
        tablaFirmas.setWidths(new float[]{1, 1}); // columnas iguales

        // Primera celda: RESPONSABLE / FUNCIONARIO
        PdfPCell celdaResponsable = new PdfPCell();
        celdaResponsable.setBorder(Rectangle.NO_BORDER);
        celdaResponsable.setHorizontalAlignment(Element.ALIGN_CENTER);

        Paragraph p1 = new Paragraph("RESPONSABLE / FUNCIONARIO", new Font(Font.FontFamily.TIMES_ROMAN, 10, Font.BOLD));
        p1.setAlignment(Element.ALIGN_CENTER);
        celdaResponsable.addElement(p1);

        // Cuadro para firma
        Paragraph espacioFirma1 = new Paragraph("\n\n\n_______________________________\nFIRMA", new Font(Font.FontFamily.TIMES_ROMAN, 10));
        espacioFirma1.setAlignment(Element.ALIGN_CENTER);
        celdaResponsable.addElement(espacioFirma1);

        tablaFirmas.addCell(celdaResponsable);

        // Segunda celda: RESPONSABLE DE ACTIVOS FIJOS
        PdfPCell celdaActivoFijo = new PdfPCell();
        celdaActivoFijo.setBorder(Rectangle.NO_BORDER);
        celdaActivoFijo.setHorizontalAlignment(Element.ALIGN_CENTER);

        Paragraph p2 = new Paragraph("RESPONSABLE DE ACTIVOS FIJOS", new Font(Font.FontFamily.TIMES_ROMAN, 10, Font.BOLD));
        p2.setAlignment(Element.ALIGN_CENTER);
        celdaActivoFijo.addElement(p2);

        // Cuadro para firma
        Paragraph espacioFirma2 = new Paragraph("\n\n\n_______________________________\nFIRMA", new Font(Font.FontFamily.TIMES_ROMAN, 10));
        espacioFirma2.setAlignment(Element.ALIGN_CENTER);
        celdaActivoFijo.addElement(espacioFirma2);

        tablaFirmas.addCell(celdaActivoFijo);

        // Añadir tabla al documento
        document.add(tablaFirmas);

        // Espacio antes de la nota
        document.add(new Paragraph("\n"));

        // Agregar nota final
        Paragraph nota = new Paragraph();
        Chunk notaLabel = new Chunk("NOTA: ", new Font(Font.FontFamily.TIMES_ROMAN, 10, Font.BOLD));
        Chunk notaTexto = new Chunk("EN CASO DE BAJAS DE EQUIPOS INFORMATICOS E INSTALACION DEBE ADJUNTARSE EL INFORME TECNICO EMITIDO POR LA UNIDAD DE SISTEMAS DE INFORMACION Y COMUNICACIÓN (USIC) E INFRAESTRUCTURA.", 
                                    new Font(Font.FontFamily.TIMES_ROMAN, 10));

        nota.add(notaLabel);
        nota.add(notaTexto);
        nota.setAlignment(Element.ALIGN_JUSTIFIED);
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
