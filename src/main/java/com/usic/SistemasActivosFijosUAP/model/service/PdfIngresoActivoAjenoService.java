package com.usic.SistemasActivosFijosUAP.model.service;

import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

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
import com.usic.SistemasActivosFijosUAP.model.dto.ActivoIngresoAjenoDTO;
import com.usic.SistemasActivosFijosUAP.model.entity.Responsable;

@Service
public class PdfIngresoActivoAjenoService {

    public byte[] generarPdfActivosAjenos(
        String fechaIncorporacion,
        String fechaRetiro,
        Responsable responsablePropietario,
        Responsable responsableAutorizador,
        String nombreIdentificacion,
        String cargoIdentificacion,
        String unidadIdentificacion,
        List<ActivoIngresoAjenoDTO> activos)throws Exception{

            Document document = new Document(PageSize.LETTER, 50, 50, 50, 50);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PdfWriter writer = PdfWriter.getInstance(document, baos);
    
            document.open();
            
            Path projectPath = Paths.get("").toAbsolutePath();
            String imagePath = projectPath + "/src/main/resources/static/assets/img/fondo/0.jpg";
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

		Paragraph linea4 = new Paragraph("INGRESO DE BIENES (ACTIVOS) AJENOS A LA UNIVERSIDAD AMAZÓNICA DE PANDO", new Font(Font.FontFamily.TIMES_ROMAN, 12, Font.BOLD));
		linea4.setAlignment(Element.ALIGN_CENTER);
		document.add(linea4);

		document.add(new Paragraph("\n"));

        // Párrafo justificado con padding (usamos margen mediante setIndentación)
        Paragraph parrafoFinalidad = new Paragraph(
            "Finalidad: Para informar a la Dirección Administrativa Financiera, Sección de Activos Fijos, " +
            "que todos los Activos que ingresan a los predios de la Universidad Amazónica de Pando de Forma Temporal, " +
            "ya sea para ser utilizado en alguna actividad Institucional o Particular.",
            new Font(Font.FontFamily.TIMES_ROMAN, 12)
        );
        parrafoFinalidad.setAlignment(Element.ALIGN_JUSTIFIED);
        document.add(parrafoFinalidad);

        // Espacio entre párrafo y fila
        document.add(new Paragraph("\n"));

        // Crear una tabla de 2 columnas sin bordes para mostrar FECHA y UNIDAD
        PdfPTable tablaInfo = new PdfPTable(2);
        tablaInfo.setWidthPercentage(100);

        // Primera celda: FECHA a la izquierda
        PdfPCell celdaFecha = new PdfPCell(new Phrase("FECHA: " + fechaIncorporacion, new Font(Font.FontFamily.TIMES_ROMAN, 11)));
        celdaFecha.setBorder(Rectangle.NO_BORDER);
        celdaFecha.setHorizontalAlignment(Element.ALIGN_LEFT);

        // Segunda celda: UNIDAD a la derecha
        PdfPCell celdaUnidad = new PdfPCell(new Phrase("UNIDAD: BIENES Y SERVICIOS", new Font(Font.FontFamily.TIMES_ROMAN, 11)));
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

        // Texto "1) DETALLE DE BIENES"
        Paragraph detalleBienes = new Paragraph(
            "1) DETALLE DE BIENES",
            new Font(Font.FontFamily.TIMES_ROMAN, 11, Font.BOLD)
        );
        detalleBienes.setAlignment(Element.ALIGN_LEFT);
        detalleBienes.setSpacingBefore(10f);
        document.add(detalleBienes);

        // Tabla con 3 columnas: Número, Descripción, Estado
        PdfPTable tablaActivos = new PdfPTable(3);
        tablaActivos.setWidthPercentage(100);
        tablaActivos.setSpacingBefore(10f);
        tablaActivos.setWidths(new float[]{0.5f, 8f, 1.5f});

        // Encabezados de la tabla
        Font fontHeader = new Font(Font.FontFamily.TIMES_ROMAN, 11, Font.BOLD);
        PdfPCell cellNum = new PdfPCell(new Phrase("N°", fontHeader));
        cellNum.setHorizontalAlignment(Element.ALIGN_CENTER);
        cellNum.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cellNum.setPadding(8f);
        
        tablaActivos.addCell(cellNum);

        PdfPCell cellDesc = new PdfPCell(new Phrase("DESCRIPCIÓN DEL ACTIVO (TIPO, MARCA, MODELO, COLOR, Etc.)", fontHeader));
        cellDesc.setHorizontalAlignment(Element.ALIGN_CENTER);
        cellDesc.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cellDesc.setPadding(8f);
        tablaActivos.addCell(cellDesc);

        PdfPCell cellEstado = new PdfPCell(new Phrase("ESTADO", fontHeader));
        cellEstado.setHorizontalAlignment(Element.ALIGN_CENTER);
        cellEstado.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cellEstado.setPadding(8f);
        tablaActivos.addCell(cellEstado);

        // Rellenar filas con datos de activosParaPdf
        Font fontBody = new Font(Font.FontFamily.TIMES_ROMAN, 11);
        for (int i = 0; i < activos.size(); i++) {
            ActivoIngresoAjenoDTO activo = activos.get(i);
            PdfPCell numCell = new PdfPCell(new Phrase(String.valueOf(i + 1), fontBody));
            numCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            numCell.setPadding(4f);
            tablaActivos.addCell(numCell);

            PdfPCell descCell = new PdfPCell(new Phrase(activo.getDescripcionA(), fontBody));
            tablaActivos.addCell(descCell);

            PdfPCell estadoCell = new PdfPCell(new Phrase(activo.getEstadoA(), fontBody));
            estadoCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            tablaActivos.addCell(estadoCell);
        }

        document.add(tablaActivos);

        Paragraph nota = new Paragraph(
            "NOTA: SE LE OTORGARA EL PERMISO PARA EL INGRESO DEL ACTIVO POR EL LAPSO DE 3 MESES A PARTIR DE LA FECHA HASTA "+ fechaRetiro +", " +
            "EL PROPIETARIO DEBERA RENOVAR SU PERMISO DE INGRESO DEL ACTIVO, DE NO SER ASI SE LO TOMARA COMO PARTE DE LOS ACTIVOS DE LA UNIVERSIDAD AMAZONICA DE PANDO.",
            new Font(Font.FontFamily.TIMES_ROMAN, 10, Font.NORMAL)
        );
        nota.setAlignment(Element.ALIGN_JUSTIFIED);
        nota.setSpacingBefore(10f);
        
        document.add(nota);

        // 1) Título "2) PROPIETARIO DEL ACTIVO"
        Paragraph tituloPropietario = new Paragraph("2) PROPIETARIO DEL ACTIVO",
        new Font(Font.FontFamily.TIMES_ROMAN, 11, Font.BOLD));
        tituloPropietario.setSpacingBefore(10f);
        tituloPropietario.setSpacingAfter(10f);
        document.add(tituloPropietario);

        // 2) Crear tabla 3 columnas
        PdfPTable tablaPropietario = new PdfPTable(3);
        tablaPropietario.setWidthPercentage(100);
        tablaPropietario.setWidths(new float[]{3f, 5f, 3f});

        // Fila 1 - NOMBRE COMPLETO
        tablaPropietario.addCell(new Phrase("NOMBRE COMPLETO", new Font(Font.FontFamily.TIMES_ROMAN, 11, Font.BOLD)));

        // Segunda columna - variable, por ejemplo:
        PdfPCell nombreCell = new PdfPCell(new Phrase(responsablePropietario.getPersona().getNombreCompleto(), new Font(Font.FontFamily.TIMES_ROMAN, 11)));
        nombreCell.setHorizontalAlignment(Element.ALIGN_LEFT);
        nombreCell.setPadding(4f);
        tablaPropietario.addCell(nombreCell);

        // Tercera columna - combinada (colspan 1, rowspan 3) con texto Firma/Sello alineado al final
        PdfPCell firmaCell = new PdfPCell(new Phrase("Firma/Sello", new Font(Font.FontFamily.TIMES_ROMAN, 11)));
        firmaCell.setVerticalAlignment(Element.ALIGN_BOTTOM);
        firmaCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        firmaCell.setRowspan(3);
        tablaPropietario.addCell(firmaCell);

        // Fila 2 - CARGO
        tablaPropietario.addCell(new Phrase("CARGO", new Font(Font.FontFamily.TIMES_ROMAN, 11, Font.BOLD)));
        PdfPCell cargoCell = new PdfPCell(new Phrase(responsablePropietario.getCargo().getNombre(), new Font(Font.FontFamily.TIMES_ROMAN, 12)));
        cargoCell.setHorizontalAlignment(Element.ALIGN_LEFT);
        cargoCell.setPadding(4f);
        tablaPropietario.addCell(cargoCell);

        // Fila 3 - DIRECCIÓN/UNIDAD/SECCIÓN
        tablaPropietario.addCell(new Phrase("DIRECCIÓN, UNIDAD Y/O SECCIÓN", new Font(Font.FontFamily.TIMES_ROMAN, 11, Font.BOLD)));
        PdfPCell unidadCell = new PdfPCell(new Phrase(responsablePropietario.getOficina().getNombre(), new Font(Font.FontFamily.TIMES_ROMAN, 12)));
        unidadCell.setHorizontalAlignment(Element.ALIGN_LEFT);
        unidadCell.setPadding(4f);
        tablaPropietario.addCell(unidadCell);

        // Añadir la tabla al documento
        document.add(tablaPropietario);

        // 1) Título "3) IDENTIFICACIÓN DE LA UNIDAD EN LA QUE INGRESA"
        Paragraph tituloUnidad = new Paragraph("3) IDENTIFICACIÓN DE LA UNIDAD EN LA QUE INGRESA",
        new Font(Font.FontFamily.TIMES_ROMAN, 11, Font.BOLD));
        tituloUnidad.setSpacingBefore(5f);
        tituloUnidad.setSpacingAfter(10f);
        document.add(tituloUnidad);

        // 2) Crear tabla con la misma estructura de 3 columnas
        PdfPTable tablaUnidad = new PdfPTable(3);
        tablaUnidad.setWidthPercentage(100);
        tablaUnidad.setWidths(new float[]{3f, 5f, 3f});

        // Fila 1 - AUTORIZADO POR
        tablaUnidad.addCell(new Phrase("AUTORIZADO POR", new Font(Font.FontFamily.TIMES_ROMAN, 11, Font.BOLD)));
        PdfPCell autorizadoPorCell = new PdfPCell(new Phrase(responsableAutorizador.getPersona().getNombreCompleto(), new Font(Font.FontFamily.TIMES_ROMAN, 11)));
        autorizadoPorCell.setHorizontalAlignment(Element.ALIGN_LEFT);
        autorizadoPorCell.setPadding(4f);
        tablaUnidad.addCell(autorizadoPorCell);

        // Tercera columna combinada - Firma/Sello
        PdfPCell firmaUnidadCell = new PdfPCell(new Phrase("Firma/Sello", new Font(Font.FontFamily.TIMES_ROMAN, 11)));
        firmaUnidadCell.setVerticalAlignment(Element.ALIGN_BOTTOM);
        firmaUnidadCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        firmaUnidadCell.setRowspan(3);
        firmaUnidadCell.setFixedHeight(60f); // Espacio para firma
        tablaUnidad.addCell(firmaUnidadCell);

        // Fila 2 - CARGO
        tablaUnidad.addCell(new Phrase("CARGO", new Font(Font.FontFamily.TIMES_ROMAN, 11, Font.BOLD)));
        PdfPCell cargoUnidadCell = new PdfPCell(new Phrase(responsableAutorizador.getCargo().getNombre(), new Font(Font.FontFamily.TIMES_ROMAN, 11)));
        cargoUnidadCell.setHorizontalAlignment(Element.ALIGN_LEFT);
        cargoUnidadCell.setPadding(4f);
        tablaUnidad.addCell(cargoUnidadCell);

        // Fila 3 - DIRECCIÓN/UNIDAD/SECCIÓN
        tablaUnidad.addCell(new Phrase("DIRECCIÓN, UNIDAD Y/O SECCIÓN:", new Font(Font.FontFamily.TIMES_ROMAN, 11, Font.BOLD)));
        PdfPCell unidadAutorizadoraCell = new PdfPCell(new Phrase(responsableAutorizador.getOficina().getNombre(), new Font(Font.FontFamily.TIMES_ROMAN, 11)));
        unidadAutorizadoraCell.setHorizontalAlignment(Element.ALIGN_LEFT);
        unidadAutorizadoraCell.setPadding(4f);
        tablaUnidad.addCell(unidadAutorizadoraCell);
        
        document.add(tablaUnidad);

        document.newPage();
        agregarFondo(writer, imagePath);
        document.add(new Paragraph("\n\n"));

        // Título 4)
        Paragraph tituloActivosFijos = new Paragraph("4) IDENTIFICACIÓN EN ACTIVOS FIJOS",
            new Font(Font.FontFamily.TIMES_ROMAN, 11, Font.BOLD));
        tituloActivosFijos.setSpacingBefore(20f);
        tituloActivosFijos.setSpacingAfter(10f);
        document.add(tituloActivosFijos);

        // Crear tabla de 3 columnas
        PdfPTable tablaActivosFijos = new PdfPTable(3);
        tablaActivosFijos.setWidthPercentage(100);
        tablaActivosFijos.setWidths(new float[]{3f, 5f, 3f});

        // Fila 1 - NOMBRE COMPLETO
        tablaActivosFijos.addCell(new Phrase("NOMBRE COMPLETO:", new Font(Font.FontFamily.TIMES_ROMAN, 11, Font.BOLD)));
        PdfPCell nombreAFCell = new PdfPCell(new Phrase(nombreIdentificacion, new Font(Font.FontFamily.TIMES_ROMAN, 11)));
        nombreAFCell.setHorizontalAlignment(Element.ALIGN_LEFT);
        nombreAFCell.setPadding(4f);
        tablaActivosFijos.addCell(nombreAFCell);

        // Celda combinada para Firma/Sello
        PdfPCell firmaAFCell = new PdfPCell(new Phrase("Firma/Sello", new Font(Font.FontFamily.TIMES_ROMAN, 11)));
        firmaAFCell.setRowspan(3);
        firmaAFCell.setFixedHeight(60f);
        firmaAFCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        firmaAFCell.setVerticalAlignment(Element.ALIGN_BOTTOM);
        firmaAFCell.setPadding(4f);
        tablaActivosFijos.addCell(firmaAFCell);

        // Fila 2 - CARGO
        tablaActivosFijos.addCell(new Phrase("CARGO:", new Font(Font.FontFamily.TIMES_ROMAN, 11, Font.BOLD)));
        PdfPCell cargoAFCell = new PdfPCell(new Phrase(cargoIdentificacion, new Font(Font.FontFamily.TIMES_ROMAN, 11)));
        cargoAFCell.setHorizontalAlignment(Element.ALIGN_LEFT);
        cargoAFCell.setPadding(4f);
        tablaActivosFijos.addCell(cargoAFCell);

        // Fila 3 - HORA Y FECHA
        tablaActivosFijos.addCell(new Phrase("HORA Y FECHA:", new Font(Font.FontFamily.TIMES_ROMAN, 11, Font.BOLD)));

        // Obtener hora y fecha actual
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");
        String fechaHoraActual = LocalDateTime.now().format(formatter);

        PdfPCell horaFechaCell = new PdfPCell(new Phrase(fechaHoraActual, new Font(Font.FontFamily.TIMES_ROMAN, 11)));
        horaFechaCell.setHorizontalAlignment(Element.ALIGN_LEFT);
        tablaActivosFijos.addCell(horaFechaCell);

        // Agregar tabla al documento
        document.add(tablaActivosFijos);

        // Título "5) OBSERVACIONES"
        Paragraph tituloObservaciones = new Paragraph("5) OBSERVACIONES", new Font(Font.FontFamily.HELVETICA, 12, Font.BOLD));
        tituloObservaciones.setSpacingBefore(20f); // Espaciado superior
        tituloObservaciones.setSpacingAfter(10f);  // Espaciado inferior
        document.add(tituloObservaciones);

        // Párrafo con contenido legal
        Paragraph parrafoObservaciones = new Paragraph(
            "EL EQUIPO MENCIONADO QUEDA A RESPONSABILIDAD DEL PROPIETARIO EN CUMPLIMIENTO AL: D.S. 0181 Art. 157 inciso g) INGRESAR BIENES PARTICULARES SIN AUTORIZACIÓN DE LA UNIDAD O RESPONSABLE DE ACTIVOS FIJOS TENIENDO QUE LLENAR EL FORMULARIO RESPECTIVO CASO CONTRARIO SE TOMARÁ COMO ACTIVO FIJO DE LA UNIVERSIDAD AMAZÓNICA DE PANDO.",
            new Font(Font.FontFamily.HELVETICA, 10)
        );
        parrafoObservaciones.setAlignment(Element.ALIGN_JUSTIFIED);
        parrafoObservaciones.setIndentationLeft(20f);  // Sangría izquierda
        parrafoObservaciones.setIndentationRight(20f); // Sangría derecha
        parrafoObservaciones.setSpacingAfter(10f);
        document.add(parrafoObservaciones);

        // Espacio antes de la línea
        document.add(new Paragraph("\n\n\n\n"));
        document.add(new Paragraph(" ")); // Salto visual

        // Tabla para simular línea con texto centrado
        PdfPTable lineaFirmas = new PdfPTable(1);
        lineaFirmas.setWidthPercentage(40); // Ajusta el ancho de la línea (más corto que la hoja)
        lineaFirmas.setHorizontalAlignment(Element.ALIGN_CENTER); // Centrado en la página

        PdfPCell celdaLinea = new PdfPCell(new Phrase("ACTIVOS FIJOS", new Font(Font.FontFamily.HELVETICA, 10, Font.BOLD)));
        celdaLinea.setHorizontalAlignment(Element.ALIGN_CENTER);
        celdaLinea.setBorderWidthTop(1f);  // Línea superior visible
        celdaLinea.setBorderWidthBottom(0);
        celdaLinea.setBorderWidthLeft(0);
        celdaLinea.setBorderWidthRight(0);
        celdaLinea.setPaddingTop(10f);
        celdaLinea.setPaddingBottom(5f);

        lineaFirmas.addCell(celdaLinea);
        document.add(lineaFirmas);


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
