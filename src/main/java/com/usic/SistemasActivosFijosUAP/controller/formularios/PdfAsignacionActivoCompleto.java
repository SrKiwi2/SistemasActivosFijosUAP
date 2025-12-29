package com.usic.SistemasActivosFijosUAP.controller.formularios;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;

import javax.swing.border.Border;
import javax.swing.text.StyleConstants.ColorConstants;

import org.springframework.stereotype.Service;

import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Chunk;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Element;
import com.itextpdf.text.Font;
import com.itextpdf.text.FontFactory;
import com.itextpdf.text.Image;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.Rectangle;
import com.itextpdf.text.pdf.PdfContentByte;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfPageEventHelper;
import com.itextpdf.text.pdf.PdfWriter;
import com.usic.SistemasActivosFijosUAP.model.entity.Activo;
import com.usic.SistemasActivosFijosUAP.model.entity.AsignacionActivo;
import com.usic.SistemasActivosFijosUAP.model.entity.ConfiguracionGestion;
import com.usic.SistemasActivosFijosUAP.model.entity.DetalleAsignacionActivo;


@Service
public class PdfAsignacionActivoCompleto {
    private static final String LOGO_PATH = "/static/assets/img/fondo/0.jpg";

    public byte[] generarActaAsignacion(AsignacionActivo asignacion, ConfiguracionGestion config) throws DocumentException, IOException {
        Document document = new Document(PageSize.LETTER, 50, 50, 100, 50); // Márgenes: Izq, Der, Arr, Abj
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter writer = PdfWriter.getInstance(document, baos);

        // Evento para el fondo (Membrete)
        writer.setPageEvent(new MembreteEvento(LOGO_PATH));

        document.open();

        // Fuentes
        Font fontTitulo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, Font.UNDERLINE);
        Font fontNegrita = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11);
        Font fontNormal = FontFactory.getFont(FontFactory.HELVETICA, 11);
        Font fontTablaHeader = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);
        Font fontTablaBody = FontFactory.getFont(FontFactory.HELVETICA, 8);
        Font fontPie = FontFactory.getFont(FontFactory.HELVETICA, 7);

        // 1. TÍTULO
        Paragraph titulo = new Paragraph("ACTA DE ASIGNACIÓN INDIVIDUAL\nDE BIENES NUEVOS-" + config.getGestion(), fontTitulo);
        titulo.setAlignment(Element.ALIGN_CENTER);
        titulo.setSpacingAfter(20);
        document.add(titulo);

        // 2. PÁRRAFO DE INTRODUCCIÓN
        String fechaLit = obtenerFechaLiteral(asignacion.getFechaAsignacion());
        String horaLit = asignacion.getFechaAsignacion().format(DateTimeFormatter.ofPattern("HH:mm a"));
        String nombreRespActivos = config.getResponsableActivosNombre();
        String nombreReceptor = asignacion.getResponsable().getPersona().getNombreCompleto(); // Concatenar en entidad o aquí

        Paragraph intro = new Paragraph();
        intro.setAlignment(Element.ALIGN_JUSTIFIED);
        intro.setSpacingAfter(10);
        
        intro.add(new Chunk("En la ciudad de " + config.getCiudad() + " a los " + fechaLit + ", a horas " + horaLit + 
                " en los predios de la Universidad Amazónica de Pando en presencia de la ", fontNormal));
        intro.add(new Chunk("Lic. Verónica Layme Cori", fontNegrita));
        intro.add(new Chunk(" Responsable de Activos Fijos dando el visto bueno a la ", fontNormal));
        intro.add(new Chunk("Lic. Ruth Yoryina Espejo Cartagena", fontNegrita));
        intro.add(new Chunk(", se procedió a la ", fontNormal));
        intro.add(new Chunk("ASIGNACIÓN", fontNegrita));
        intro.add(new Chunk(" de los Activos Fijos de acuerdo a las siguientes características:", fontNormal));
        document.add(intro);

        // 3. NÚMERO PREVENTIVO (El que guardamos)
        // Ejemplo: "PREV. 1234"
        String textoCodigo = (asignacion.getCodigoCompleto() != null && !asignacion.getCodigoCompleto().isBlank()) 
                         ? asignacion.getCodigoCompleto() 
                         : "-"; // O dejar vacío ""

        Paragraph pPrev = new Paragraph(textoCodigo, fontNegrita);
        pPrev.setSpacingAfter(10);
        document.add(pPrev);

        // 4. TABLA DE ACTIVOS
        PdfPTable table = new PdfPTable(5); // 5 Columnas
        table.setWidthPercentage(100);
        // Anchos relativos: Item(5%), Desc(40%), Ubic(20%), Cod(20%), Est(15%)
        table.setWidths(new float[]{7f, 43f, 20f, 20f, 10f}); 

        // Cabeceras
        agregarCeldaHeader(table, "Item", fontTablaHeader);
        agregarCeldaHeader(table, "Descripción", fontTablaHeader);
        agregarCeldaHeader(table, "Ubicación", fontTablaHeader);
        agregarCeldaHeader(table, "Código", fontTablaHeader);
        agregarCeldaHeader(table, "Estado", fontTablaHeader);

        // Filas
        int item = 1;
        for (DetalleAsignacionActivo det : asignacion.getDetalles()) {
            Activo a = det.getActivo();
            String codigoVisual = "148-" + a.getCodigo(); // Formato 148-XXXX
            String ubicacion = (a.getOficina() != null) ? a.getOficina().getNombre() : "S/N";

            agregarCeldaBody(table, String.valueOf(item++), fontTablaBody, Element.ALIGN_CENTER);
            agregarCeldaBody(table, a.getDescripcion(), fontTablaBody, Element.ALIGN_LEFT);
            agregarCeldaBody(table, ubicacion, fontTablaBody, Element.ALIGN_CENTER);
            agregarCeldaBody(table, codigoVisual, fontTablaBody, Element.ALIGN_CENTER);
            agregarCeldaBody(table, "NUEVO", fontTablaBody, Element.ALIGN_CENTER);
        }
        document.add(table);

        // 5. DATOS DEL RESPONSABLE (AL:)
        Paragraph pAl = new Paragraph("Al:", fontNormal);
        pAl.setSpacingBefore(10);
        document.add(pAl);

        Paragraph pDatosResp = new Paragraph();
        pDatosResp.setAlignment(Element.ALIGN_CENTER);
        pDatosResp.setSpacingAfter(10);
        pDatosResp.add(new Chunk(nombreReceptor + "C.I: " +  asignacion.getResponsable().getPersona().getCi() +"\n", fontTablaBody));
        
        String cargo = (asignacion.getResponsable().getCargo() != null) ? asignacion.getResponsable().getCargo().getNombre() : "";
        pDatosResp.add(new Chunk(cargo, fontTablaBody));
        document.add(pDatosResp);

        // 6. PÁRRAFO LEGAL
        Paragraph legal = new Paragraph();
        legal.setAlignment(Element.ALIGN_JUSTIFIED);
        legal.setSpacingAfter(10);
        legal.add(new Chunk("Entrega que es realizada de acuerdo al reglamentos y Normas Básicas del SABS y Decreto Supremo 0181 que a la letra dice, ", fontNormal));
        legal.add(new Chunk("es de responsabilidad total de la persona y/o funcionario, que recibe el activo, por cualquier pérdida, deterioro y/o por mal uso de los Bienes de la institución.", fontNegrita));
        document.add(legal);

        Paragraph constancia = new Paragraph("Para constancia de la recepción firmamos al pie del presente documento.", fontNormal);
        constancia.setAlignment(Element.ALIGN_JUSTIFIED);
        constancia.setSpacingAfter(40); // Espacio para firmar
        document.add(constancia);

        // 7. FIRMAS (Tabla invisible)
        PdfPTable tFirmas = new PdfPTable(3);
        tFirmas.setWidthPercentage(100);
        tFirmas.setWidths(new float[]{33f, 33f, 33f});

        String linea = "\n\n______________________\n";
        agregarCeldaFirma(tFirmas, linea + "RECIBE CONFORME", fontTablaHeader);
        agregarCeldaFirma(tFirmas, linea + "Vo.Bo.", fontTablaHeader);
        agregarCeldaFirma(tFirmas, linea + "ENTREGUE CONFORME", fontTablaHeader);
        
        document.add(tFirmas);

        // 8. PIE PEQUEÑO
        Paragraph footer = new Paragraph("\nC.c/Arch.\nRYEC/A-F", fontPie);
        document.add(footer);

        document.close();
        return baos.toByteArray();
    }

    // --- HELPERS ---

    private void agregarCeldaHeader(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setBackgroundColor(BaseColor.LIGHT_GRAY);
        cell.setPadding(5);
        table.addCell(cell);
    }

    private void agregarCeldaBody(PdfPTable table, String text, Font font, int align) {
        PdfPCell cell = new PdfPCell(new Phrase(text != null ? text : "", font));
        cell.setHorizontalAlignment(align);
        cell.setPadding(4);
        table.addCell(cell);
    }

    private void agregarCeldaFirma(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setBorder(Rectangle.NO_BORDER);
        table.addCell(cell);
    }

    private String obtenerFechaLiteral(LocalDateTime fecha) {
        int dia = fecha.getDayOfMonth();
        String mes = fecha.getMonth().getDisplayName(TextStyle.FULL, new Locale("es", "ES"));
        int anio = fecha.getYear();
        return dia + " días del mes de " + mes + " del " + anio;
    }

    // CLASE INTERNA PARA EL MEMBRETE EN ITEXT 5
    class MembreteEvento extends PdfPageEventHelper {
        String imgPath;
        
        public MembreteEvento(String imgPath) { 
            this.imgPath = imgPath; 
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            try {
                PdfContentByte canvas = writer.getDirectContentUnder();
                
                // 2. CORRECCIÓN DE CARGA DE IMAGEN
                // Usamos getClass().getResource() para buscar dentro del JAR/Classpath
                java.net.URL imageUrl = getClass().getResource(this.imgPath);
                
                if (imageUrl == null) {
                    // Fallback por si acaso no lo encuentra (para depuración)
                    System.err.println("❌ No se encontró la imagen en: " + this.imgPath);
                    return;
                }

                Image image = Image.getInstance(imageUrl);

                // Escalar imagen al tamaño de la hoja (carta)
                image.scaleAbsolute(document.getPageSize());
                image.setAbsolutePosition(0, 0); 
                
                canvas.addImage(image);

            } catch (Exception e) {
                System.err.println("Error cargando membrete: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
        
}
