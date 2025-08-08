package com.usic.SistemasActivosFijosUAP.model.service;

import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

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
import com.usic.SistemasActivosFijosUAP.model.dto.ActivoTransferenciaDTO;
import com.usic.SistemasActivosFijosUAP.model.entity.Responsable;

@Service
public class PdfTransferenciaService {

    public byte[] generarPdfTransferencia(
        String unidadOrigen, Responsable responsableOrigen, String fechaTransferencia,
        String unidadDestino, Responsable responsableDestino, String fechaRecepcion,
        List<ActivoTransferenciaDTO> activos) throws Exception {

        // Usamos hoja carta horizontal con márgenes de 36 puntos (~0.5 inch)
        Rectangle pageSize = PageSize.LETTER.rotate();
        Document document = new Document(pageSize, 36, 36, 36, 36);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter writer = PdfWriter.getInstance(document, baos);
        document.open();

        // Fondo
        try {
            Path projectPath = Paths.get("").toAbsolutePath();
            String imagePath = projectPath + "/src/main/resources/static/assets/img/fondo/1.png";
            Image background = Image.getInstance(imagePath);
            background.scaleAbsolute(pageSize.getWidth(), pageSize.getHeight()); // rotado
            background.setAbsolutePosition(0, 0);

            PdfContentByte canvas = writer.getDirectContentUnder();
            canvas.addImage(background);
        } catch (Exception e) {
            System.err.println("No se pudo cargar el membrete: " + e.getMessage());
        }

        document.add(new Paragraph("\n\n"));

        // Fuentes
        Font normal = new Font(Font.FontFamily.TIMES_ROMAN, 12);
        Font titulo = new Font(Font.FontFamily.TIMES_ROMAN, 14, Font.BOLD);

        //Fuente para la tabla
        Font normal_tabla = new Font(Font.FontFamily.HELVETICA, 7);
        Font negrita = new Font(Font.FontFamily.HELVETICA, 7, Font.BOLD);

        // Encabezado
        Paragraph linea1 = new Paragraph("UNIVERSIDAD AMAZÓNICA DE PANDO", normal);
        linea1.setAlignment(Element.ALIGN_CENTER);
        linea1.setSpacingAfter(0.01f);
        document.add(linea1);

        Paragraph linea2 = new Paragraph("SECCIÓN DE ACTIVOS FIJOS", normal);
        linea2.setAlignment(Element.ALIGN_CENTER);
        linea2.setSpacingAfter(0.01f);
        document.add(linea2);

        Paragraph linea3 = new Paragraph("TRANSFERENCIA DE BIENES", titulo);
        linea3.setAlignment(Element.ALIGN_CENTER);
        linea3.setSpacingAfter(0.1f);
        document.add(linea3);

        
        // Tabla de datos
        PdfPTable tabla = new PdfPTable(4);
        tabla.setWidthPercentage(100);
        tabla.setSpacingBefore(10f);
        tabla.setSpacingAfter(10f);
        tabla.setWidths(new float[]{2.5f, 4f, 2.5f, 4f}); // columnas equilibradas

        BaseColor cyanCustom = new BaseColor(251, 233, 161);
        // Fila 1: encabezados combinados
        PdfPCell celda1 = new PdfPCell(new Phrase("TRANSFIERE", normal));
        celda1.setColspan(2);
        celda1.setHorizontalAlignment(Element.ALIGN_CENTER);
        celda1.setBackgroundColor(cyanCustom);
        celda1.setPaddingTop(8f);
        celda1.setPaddingBottom(8f);
        tabla.addCell(celda1);

        PdfPCell celda2 = new PdfPCell(new Phrase("RECIBE Y RESGUARDA", normal));
        celda2.setColspan(2);
        celda2.setHorizontalAlignment(Element.ALIGN_CENTER);
        celda2.setBackgroundColor(cyanCustom);
        celda2.setPaddingTop(8f);
        celda2.setPaddingBottom(8f);
        tabla.addCell(celda2);

        // Fila 2
        PdfPCell c1 = new PdfPCell(new Phrase(" DIRECCION, UNIDAD Y/O SECCION: ", negrita));
        c1.setPaddingTop(4f); c1.setPaddingBottom(4f);
        tabla.addCell(c1);

        PdfPCell c2 = new PdfPCell(new Phrase(" "+unidadOrigen, normal_tabla));
        c2.setPaddingTop(4f); c2.setPaddingBottom(4f);
        tabla.addCell(c2);

        PdfPCell c3 = new PdfPCell(new Phrase(" DIRECCION, UNIDAD Y/O SECCION: ", negrita));
        c3.setPaddingTop(4f); c3.setPaddingBottom(4f);
        tabla.addCell(c3);

        PdfPCell c4 = new PdfPCell(new Phrase(" "+unidadDestino, normal_tabla));
        c4.setPaddingTop(4f); c4.setPaddingBottom(4f);
        tabla.addCell(c4);

        // Fila 3
        PdfPCell firma1 = new PdfPCell(new Phrase(" FIRMA DEL INMEDIATO SUPERIOR: ", negrita));
        firma1.setFixedHeight(30f);
        firma1.setVerticalAlignment(PdfPCell.ALIGN_MIDDLE); 
        tabla.addCell(firma1);
        tabla.addCell(new PdfPCell()); // celda vacía

        PdfPCell firma2 = new PdfPCell(new Phrase(" FIRMA DEL INMEDIATO SUPERIOR: ", negrita));
        firma2.setFixedHeight(30f);
        firma2.setVerticalAlignment(PdfPCell.ALIGN_MIDDLE); 
        tabla.addCell(firma2);
        tabla.addCell(new PdfPCell());

        //Fila 4
        PdfPCell c5 = new PdfPCell(new Phrase("NOMBRE O PIE DE FIRMA DEL RESPONSBALE: ", negrita));
        c5.setPaddingTop(4f); c5.setPaddingBottom(4f);
        tabla.addCell(c5);

        PdfPCell c6 = new PdfPCell(new Phrase(" "+responsableOrigen.getPersona().getNombreCompleto(), normal_tabla));
        c6.setPaddingTop(4f); c6.setPaddingBottom(4f);
        tabla.addCell(c6);

        PdfPCell c7 = new PdfPCell(new Phrase("NOMBRE O PIE DE FIRMA DEL RESPONSBALE: ", negrita));
        c7.setPaddingTop(4f); c7.setPaddingBottom(4f);
        tabla.addCell(c7);

        PdfPCell c8 = new PdfPCell(new Phrase(" "+responsableDestino.getPersona().getNombreCompleto(), normal_tabla));
        c8.setPaddingTop(4f); c8.setPaddingBottom(4f);
        tabla.addCell(c8);

        // Fila 5
        PdfPCell firmaResp1 = new PdfPCell(new Phrase(" FIRMA DEL RESPONSABLE: ", negrita));
        firmaResp1.setFixedHeight(30f);
        firmaResp1.setVerticalAlignment(PdfPCell.ALIGN_MIDDLE); 
        tabla.addCell(firmaResp1);
        tabla.addCell(new PdfPCell());

        PdfPCell firmaResp2 = new PdfPCell(new Phrase(" FIRMA DEL RESPONSABLE: ", negrita));
        firmaResp2.setFixedHeight(30f);
        firmaResp2.setVerticalAlignment(PdfPCell.ALIGN_MIDDLE); 
        tabla.addCell(firmaResp2);
        tabla.addCell(new PdfPCell());

        //Fila 6
        PdfPCell c9 = new PdfPCell(new Phrase(" DIRECCION, UNIDAD Y/O SECCION: ", negrita));
        c9.setPaddingTop(4f); c9.setPaddingBottom(4f);
        tabla.addCell(c9);

        PdfPCell c10 = new PdfPCell(new Phrase(" "+unidadOrigen, normal_tabla));
        c10.setPaddingTop(4f); c10.setPaddingBottom(4f);
        tabla.addCell(c10);

        PdfPCell c11 = new PdfPCell(new Phrase(" DIRECCION, UNIDAD Y/O SECCION: ", negrita));
        c11.setPaddingTop(4f); c11.setPaddingBottom(4f);
        tabla.addCell(c11);

        PdfPCell c12 = new PdfPCell(new Phrase(" "+unidadDestino, normal_tabla));
        c12.setPaddingTop(4f); c12.setPaddingBottom(4f);
        tabla.addCell(c12);

        // Fila 6
        tabla.addCell(new Phrase(" FECHA DE LA TRANSFERENCIA: ", negrita));
        tabla.addCell(new Phrase(fechaTransferencia, normal_tabla));

        tabla.addCell(new Phrase(" FECHA DE LA RECEPCIÓN: ", negrita));
        tabla.addCell(new Phrase(fechaRecepcion, normal_tabla));

        // Agregar tabla
        document.add(tabla);

        //Tabla de datos activos

        // Tabla de datos del activo
        PdfPTable tablaActivo = new PdfPTable(5);
        tablaActivo.setWidthPercentage(100);
        tablaActivo.setSpacingBefore(10f);
        tablaActivo.setWidths(new float[]{1f, 2f, 3f, 3f, 3f});

        // Fuente para encabezados
        Font encabezado = new Font(Font.FontFamily.HELVETICA, 6, Font.BOLD);

        // --- Fila 1: encabezados principales
        PdfPCell itemCell = new PdfPCell(new Phrase("Nº ITEM", encabezado));
        itemCell.setRowspan(2);
        itemCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        itemCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        itemCell.setBackgroundColor(cyanCustom);
        tablaActivo.addCell(itemCell);

        PdfPCell codigoCell = new PdfPCell(new Phrase("CÓDIGO", encabezado));
        codigoCell.setRowspan(2);
        codigoCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        codigoCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        codigoCell.setBackgroundColor(cyanCustom);
        tablaActivo.addCell(codigoCell);

        PdfPCell descCell = new PdfPCell(new Phrase("DESCRIPCIÓN", encabezado));
        descCell.setRowspan(2);
        descCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        descCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        descCell.setBackgroundColor(cyanCustom);
        tablaActivo.addCell(descCell);

        PdfPCell ubiOriCell = new PdfPCell(new Phrase("UBICACIÓN DE ORIGEN Y Nº DE OFICINA", encabezado));
        ubiOriCell.setRowspan(2);
        ubiOriCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        ubiOriCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        ubiOriCell.setBackgroundColor(cyanCustom);
        tablaActivo.addCell(ubiOriCell);

        PdfPCell ubiActCell = new PdfPCell(new Phrase("UBICACIÓN ACTUAL Y Nº DE OFICINA", encabezado));
        ubiActCell.setRowspan(2);
        ubiActCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        ubiActCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        ubiActCell.setBackgroundColor(cyanCustom);
        tablaActivo.addCell(ubiActCell);

        // Combinamos las 4 siguientes columnas
        // PdfPCell indispCell = new PdfPCell(new Phrase("INDISPENSABLE SEGÚN CORRESPONDA", encabezado));
        // indispCell.setColspan(4);
        // indispCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        // indispCell.setBackgroundColor(cyanCustom);
        // tablaActivo.addCell(indispCell);

        // --- Fila 2: subencabezados
        // tablaActivo.addCell(new PdfPCell(new Phrase("MARCA", encabezado)));
        // tablaActivo.addCell(new PdfPCell(new Phrase("MODELO", encabezado)));
        // tablaActivo.addCell(new PdfPCell(new Phrase("Nº DE SERIE", encabezado)));
        // tablaActivo.addCell(new PdfPCell(new Phrase("DIMENSIONES", encabezado)));

        // --- Fila 3: datos del activo (puedes modificar según necesites)
        for (int i = 0; i < activos.size(); i++) {
            ActivoTransferenciaDTO dto = activos.get(i);
        
            tablaActivo.addCell(new PdfPCell(new Phrase(String.valueOf(i + 1), normal_tabla))); // Nº ITEM
            tablaActivo.addCell(new PdfPCell(new Phrase(dto.getCodigo(), normal_tabla)));
            tablaActivo.addCell(new PdfPCell(new Phrase(dto.getDescripcion(), normal_tabla)));
            tablaActivo.addCell(new PdfPCell(new Phrase(dto.getUbicacionOrigen(), normal_tabla)));
            tablaActivo.addCell(new PdfPCell(new Phrase(dto.getUbicacionActual(), normal_tabla)));
            // tablaActivo.addCell(new PdfPCell(new Phrase(dto.getMarca(), normal_tabla)));
            // tablaActivo.addCell(new PdfPCell(new Phrase(dto.getModelo(), normal_tabla)));
            // tablaActivo.addCell(new PdfPCell(new Phrase(dto.getNumeroSerie(), normal_tabla)));
            // tablaActivo.addCell(new PdfPCell(new Phrase(dto.getDimensiones(), normal_tabla)));
        }

        document.add(tablaActivo);

        PdfPTable tablaEstado = new PdfPTable(1);
        tablaEstado.setWidthPercentage(100);
        tablaEstado.setSpacingBefore(10f);
        
        Font fontEstado = new Font(Font.FontFamily.HELVETICA, 8, Font.BOLD);
        Phrase contenidoEstado = new Phrase("ESTADO FÍSICO DEL BIEN:        a) BUEN ESTADO              b) REGULAR              c) MAL ESTADO           d) INCOMPLETO", fontEstado);
        
        PdfPCell cellEstado = new PdfPCell(contenidoEstado);
        cellEstado.setBorder(Rectangle.NO_BORDER);
        tablaEstado.addCell(cellEstado);
        
        document.add(tablaEstado);
        
        // === Párrafo final ===
        Font fontNota = new Font(Font.FontFamily.HELVETICA, 7, Font.BOLD);
        Paragraph nota = new Paragraph("NOTA: A PARTIR DE LA FECHA QUEDA COMO RESPONSABLE DE TODOS LOS ITEMS QUE SE DETALLAN EN EL ACTA, " +
                "CUALQUIER PERDIDA, DESTRUCCION O MALTRATO QUE PUEDA SUFRIR SERA IMPUTADA DIRECTAMENTE A SU PERSONA, MIENTRAS NO DEMUESTRE LO CONTRARIO. " +
                "* Queda prohibida la transferencia de bienes de un servidor a otro sin la participación de la Unidad de Activos Fijos de la Universidad Amazónica de Pando. " +
                "La contravención dará lugar a posible responsabilidad administrativa, civil y penal. De acuerdo al DS 0181 Art. 146 (Asignación de activos fijos Muebles) I. " +
                "La asignación de activos fijos muebles es el acto administrativo mediante el cual se entrega a un servidor público un activo o conjunto de éstos, generando la consiguiente responsabilidad sobre su debido uso y custodia.",
                fontNota);
        nota.setSpacingBefore(20f);
        nota.setAlignment(Element.ALIGN_JUSTIFIED);
        document.add(nota);

        document.close();
        return baos.toByteArray();
    }
}
