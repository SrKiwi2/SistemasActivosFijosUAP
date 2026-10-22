package com.usic.SistemasActivosFijosUAP.model.service.adicional;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.TableRowAlign;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.apache.poi.xwpf.usermodel.XWPFTableCell.XWPFVertAlign;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder;

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
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfPageEventHelper;
import com.itextpdf.text.pdf.PdfWriter;
import com.usic.SistemasActivosFijosUAP.model.IService.IResponsableEntregaService;
import com.usic.SistemasActivosFijosUAP.model.dao.IActivoAdicionalDao;
import com.usic.SistemasActivosFijosUAP.model.dao.IConfiguracionGestionDao;
import com.usic.SistemasActivosFijosUAP.model.entity.Activo;
import com.usic.SistemasActivosFijosUAP.model.entity.ActivoAdicional;
import com.usic.SistemasActivosFijosUAP.model.entity.ConfiguracionGestion;
import com.usic.SistemasActivosFijosUAP.model.entity.Oficina;
import com.usic.SistemasActivosFijosUAP.model.entity.Persona;
import com.usic.SistemasActivosFijosUAP.model.entity.Responsable;
import com.usic.SistemasActivosFijosUAP.model.entity.ResponsableEntrega;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;
import com.usic.SistemasActivosFijosUAP.model.service.documento.MembreteWord;
import com.usic.SistemasActivosFijosUAP.model.service.documento.WordDocumentos;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Acta de asignación individual de bienes adicionales, una por responsable de la oficina
 * elegida. Sigue el acta en papel de Activos Fijos («ACTA DE ASIGNACION INDIVIDUAL DE
 * BIENES - ADICIONAL»): hoja carta con el membrete institucional, firmantes de la
 * configuración de la gestión (como el acta de asignación del VSIAF), recuadro «Al:» con
 * el responsable y firmas con Vo. Bo.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReporteAdicionalService {
    private final IActivoAdicionalDao dao;
    private final IConfiguracionGestionDao configuracionDao;
    private final IResponsableEntregaService responsableEntregaService;

    private static final String MEMBRETE = "/static/assets/img/fondo/0.jpg";
    private static final String CIUDAD_POR_DEFECTO = "Cobija";
    private static final String ACTIVOS_POR_DEFECTO = "Lic. Verónica Layme Cori";

    private static final Font TITULO = new Font(Font.FontFamily.TIMES_ROMAN, 14, Font.BOLD | Font.UNDERLINE);
    private static final Font NORMAL = new Font(Font.FontFamily.HELVETICA, 10.5f);
    private static final Font NEGRITA = new Font(Font.FontFamily.HELVETICA, 10.5f, Font.BOLD);
    private static final Font CAB_TABLA = new Font(Font.FontFamily.HELVETICA, 11, Font.BOLD);
    private static final Font TABLA = new Font(Font.FontFamily.HELVETICA, 10);
    private static final Font TABLA_NOTA = new Font(Font.FontFamily.HELVETICA, 8.5f, Font.ITALIC, new BaseColor(70, 70, 70));
    private static final BaseColor AZUL_CABECERA = new BaseColor(194, 212, 233);
    private static final Font PIE = new Font(Font.FontFamily.HELVETICA, 7);

    @Transactional(readOnly = true)
    public List<Map<String, Object>> opciones(Usuario usuario, Object permisos) {
        exigirPermiso(usuario, permisos);
        Map<Long, Map<String, Object>> oficinas = new LinkedHashMap<>();
        for (ActivoAdicional x : dao.paraReporte(null, null)) {
            Activo a = x.getActivo();
            Oficina o = a.getOficina();
            Map<String, Object> item = oficinas.computeIfAbsent(o.getIdOficina(), id -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", id);
                m.put("nombre", nombreOficina(o));
                m.put("responsables", new ArrayList<Map<String, Object>>());
                return m;
            });
            Responsable r = a.getResponsable();
            if (r != null) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> responsables = (List<Map<String, Object>>) item.get("responsables");
                if (responsables.stream().noneMatch(m -> m.get("id").equals(r.getIdResponsable()))) {
                    responsables.add(Map.of("id", r.getIdResponsable(), "nombre", nombreResponsable(r)));
                }
            }
        }
        return new ArrayList<>(oficinas.values());
    }

    /** @param fecha fecha y hora que dice el acta; sin ella, ahora. */
    @Transactional(readOnly = true)
    public byte[] generar(Long oficinaId, Long responsableId, LocalDateTime fecha, Usuario usuario, Object permisos) {
        exigirPermiso(usuario, permisos);
        if (oficinaId == null || oficinaId <= 0 || responsableId != null && responsableId <= 0)
            throw new IllegalArgumentException("Seleccione una oficina válida.");
        LocalDateTime cuando = fecha != null ? fecha : LocalDateTime.now();
        if (cuando.isAfter(LocalDateTime.now().plusDays(1)))
            throw new IllegalArgumentException("La fecha del acta no puede ser posterior a hoy.");
        List<ActivoAdicional> bienes = dao.paraReporte(oficinaId, responsableId);
        if (bienes.isEmpty()) throw new IllegalArgumentException("No hay bienes adicionales vigentes para esta selección.");
        if (responsableId != null && bienes.stream().anyMatch(x -> x.getActivo().getResponsable() == null
                || !responsableId.equals(x.getActivo().getResponsable().getIdResponsable())))
            throw new IllegalArgumentException("El responsable no corresponde a la oficina.");

        Map<Long, List<ActivoAdicional>> porResponsable = new LinkedHashMap<>();
        for (ActivoAdicional x : bienes) {
            Responsable r = x.getActivo().getResponsable();
            porResponsable.computeIfAbsent(r == null ? 0L : r.getIdResponsable(), k -> new ArrayList<>()).add(x);
        }
        Firmantes firmantes = firmantes(cuando);
        String iniciales = iniciales(usuario != null && usuario.getPersona() != null ? usuario.getPersona().getNombreCompleto() : null);
        try (ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            // Márgenes que dejan libres el encabezado y el pie del membrete.
            Document doc = new Document(PageSize.LETTER, 72, 72, 116, 76);
            PdfWriter writer = PdfWriter.getInstance(doc, salida);
            writer.setPageEvent(new Membrete(cargarMembrete()));
            doc.open();
            boolean primero = true;
            for (List<ActivoAdicional> grupo : porResponsable.values()) {
                if (!primero) doc.newPage();
                primero = false;
                escribirActa(doc, grupo, cuando, firmantes, iniciales);
            }
            doc.close();
            return salida.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo generar el reporte de adicionales.", e);
        }
    }

    /** Versión editable del acta; una página nueva por responsable. */
    @Transactional(readOnly = true)
    public byte[] generarWord(Long oficinaId, Long responsableId, LocalDateTime fecha, Usuario usuario, Object permisos) {
        exigirPermiso(usuario, permisos);
        if (oficinaId == null || oficinaId <= 0 || responsableId != null && responsableId <= 0)
            throw new IllegalArgumentException("Seleccione una oficina válida.");
        LocalDateTime cuando = fecha != null ? fecha : LocalDateTime.now();
        if (cuando.isAfter(LocalDateTime.now().plusDays(1)))
            throw new IllegalArgumentException("La fecha del acta no puede ser posterior a hoy.");
        List<ActivoAdicional> bienes = dao.paraReporte(oficinaId, responsableId);
        if (bienes.isEmpty()) throw new IllegalArgumentException("No hay bienes adicionales vigentes para esta selección.");
        if (responsableId != null && bienes.stream().anyMatch(x -> x.getActivo().getResponsable() == null
                || !responsableId.equals(x.getActivo().getResponsable().getIdResponsable())))
            throw new IllegalArgumentException("El responsable no corresponde a la oficina.");
        Map<Long, List<ActivoAdicional>> porResponsable = new LinkedHashMap<>();
        for (ActivoAdicional x : bienes) {
            Responsable r = x.getActivo().getResponsable();
            porResponsable.computeIfAbsent(r == null ? 0L : r.getIdResponsable(), k -> new ArrayList<>()).add(x);
        }
        Firmantes f = firmantes(cuando);
        String iniciales = iniciales(usuario != null && usuario.getPersona() != null ? usuario.getPersona().getNombreCompleto() : null);
        try (XWPFDocument doc = new XWPFDocument()) {
            WordDocumentos.pagina(doc, true, 2200, 1300);
            MembreteWord.aplicar(doc);
            boolean primero = true;
            for (List<ActivoAdicional> grupo : porResponsable.values()) {
                if (!primero) doc.createParagraph().setPageBreak(true);
                primero = false;
                escribirActaWord(doc, grupo, cuando, f, iniciales);
            }
            return WordDocumentos.bytes(doc);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo generar el acta Word de adicionales.", e);
        }
    }

    private static void escribirActaWord(XWPFDocument doc, List<ActivoAdicional> bienes,
                                         LocalDateTime fecha, Firmantes f, String iniciales) {
        Activo primero = bienes.get(0).getActivo();
        Oficina oficina = primero.getOficina();
        Responsable responsable = primero.getResponsable();
        String mes = fecha.getMonth().getDisplayName(TextStyle.FULL, Locale.forLanguageTag("es")).toUpperCase(Locale.ROOT);
        XWPFParagraph titulo = WordDocumentos.parrafo(doc, "ACTA DE ASIGNACIÓN INDIVIDUAL\nDE BIENES - ADICIONAL " + fecha.getYear(),
                12, true, ParagraphAlignment.CENTER);
        titulo.setSpacingAfter(260);
        titulo.getRuns().forEach(r -> r.setUnderline(org.apache.poi.xwpf.usermodel.UnderlinePatterns.SINGLE));
        XWPFParagraph introduccion = doc.createParagraph();
        introduccion.setAlignment(ParagraphAlignment.BOTH);
        agregarWord(introduccion, "En la ciudad de " + f.ciudad + " a " + String.format("%02d", fecha.getDayOfMonth())
                + " días del mes de " + mes + " del " + fecha.getYear() + ", a horas "
                + fecha.format(DateTimeFormatter.ofPattern("HH:mm")) + " en los predios de la Universidad Amazónica de Pando en presencia de la "
                + f.activos + ". ", false, 10);
        agregarWord(introduccion, "Responsable de Activos Fijos", true, 10);
        if (f.vistoBueno != null) {
            agregarWord(introduccion, " dando el visto bueno " + f.articuloVistoBueno + " ", false, 10);
            agregarWord(introduccion, f.vistoBueno, true, 10);
        }
        agregarWord(introduccion, " se procedió a la ", false, 10);
        agregarWord(introduccion, "ASIGNACION INDIVIDUAL ADICIONAL", true, 10);
        agregarWord(introduccion, " de los Activos Fijos que se encuentran físicamente en la oficina indicada a continuación, "
                + "y que no fueron inventariados ni codificados por la empresa que realizó el Revalúo, mencionando así los activos con las siguientes características:",
                false, 10);
        introduccion.setSpacingAfter(80);
        XWPFParagraph ubicacion = WordDocumentos.parrafo(doc, ubicacion(oficina), 10, true, ParagraphAlignment.CENTER);
        ubicacion.setSpacingAfter(180);
        XWPFTable tabla = WordDocumentos.tabla(doc, "Ítem", "Descripción", "Código", "Observación");
        int[] anchos = {650, 6250, 2300, 1750};
        XWPFTableRow cabecera = tabla.getRow(0);
        cabecera.getCtRow().addNewTrPr().addNewTblHeader();
        cabecera.getCtRow().getTrPr().addNewCantSplit();
        for (int c = 0; c < 4; c++) {
            XWPFTableCell celda = cabecera.getCell(c);
            celda.setWidth(Integer.toString(anchos[c]));
            celda.setColor("C2D4E9");
            celda.setVerticalAlignment(XWPFVertAlign.CENTER);
            celda.getParagraphs().forEach(p -> {
                p.setAlignment(ParagraphAlignment.CENTER);
                p.getRuns().forEach(run -> run.setFontSize(10));
            });
        }
        int numero = 1;
        for (ActivoAdicional x : bienes) {
            Activo a = x.getActivo();
            String antiguo = vacio(a.getCodigoAntiguo()) ? null : a.getCodigoAntiguo().trim();
            String codigo = codigoDocumento(a, antiguo);
            String estado = a.getEstadoActivo() == null ? "—" : a.getEstadoActivo().getNombre().toUpperCase(Locale.ROOT);
            WordDocumentos.fila(tabla, String.format("%02d", numero++), texto(a.getDescripcion(), "SIN DESCRIPCIÓN"),
                    codigo, estado + (vacio(x.getObservacion()) ? "" : "\n" + x.getObservacion().trim()));
            XWPFTableRow fila = tabla.getRow(numero - 1);
            fila.getCtRow().addNewTrPr().addNewCantSplit();
            for (int c = 0; c < 4; c++) {
                XWPFTableCell celda = fila.getCell(c);
                celda.setWidth(Integer.toString(anchos[c]));
                celda.setVerticalAlignment(XWPFVertAlign.CENTER);
                final int columna = c;
                celda.getParagraphs().forEach(p -> {
                    p.setAlignment(columna == 1 ? ParagraphAlignment.LEFT : ParagraphAlignment.CENTER);
                    p.getRuns().forEach(run -> run.setFontSize(10));
                });
            }
        }
        XWPFParagraph al = WordDocumentos.parrafo(doc, "Al:", 10, true, ParagraphAlignment.LEFT);
        al.setSpacingBefore(200);
        al.setSpacingAfter(60);
        mantenerConSiguiente(al);
        XWPFTable receptor = doc.createTable(1, 1);
        receptor.setWidth("82%");
        receptor.setTableAlignment(TableRowAlign.CENTER);
        String persona = responsable == null ? "SIN RESPONSABLE REGISTRADO" : nombreResponsable(responsable);
        String cargoReceptor = null;
        if (responsable != null && responsable.getPersona() != null) {
            Persona p = responsable.getPersona();
            if (!vacio(p.getCi())) persona += "  C.I. " + p.getCi().trim()
                    + (vacio(p.getExtension()) ? "" : " " + p.getExtension().trim().toUpperCase(Locale.ROOT) + ".");
            if (responsable.getCargo() != null && !vacio(responsable.getCargo().getNombre())) {
                String cargo = responsable.getCargo().getNombre().trim().toUpperCase(Locale.ROOT);
                cargoReceptor = cargo.contains("U.A.P") || cargo.matches(".*\\bUAP\\b.*") ? cargo : cargo + " – U.A.P.";
            }
        }
        XWPFTableCell celdaReceptor = receptor.getRow(0).getCell(0);
        celdaReceptor.removeParagraph(0);
        XWPFParagraph nombre = celdaReceptor.addParagraph();
        nombre.setAlignment(ParagraphAlignment.CENTER);
        nombre.setSpacingAfter(cargoReceptor == null ? 0 : 20);
        agregarWord(nombre, persona, false, 10);
        if (cargoReceptor != null) {
            XWPFParagraph cargo = celdaReceptor.addParagraph();
            cargo.setAlignment(ParagraphAlignment.CENTER);
            cargo.setSpacingAfter(0);
            agregarWord(cargo, cargoReceptor, true, 10);
        }
        receptor.getRow(0).getCtRow().addNewTrPr().addNewCantSplit();
        receptor.getRow(0).getCell(0).getParagraphs().forEach(p -> {
            p.setAlignment(ParagraphAlignment.CENTER);
            mantenerConSiguiente(p);
        });
        XWPFParagraph responsabilidad = doc.createParagraph();
        responsabilidad.setAlignment(ParagraphAlignment.BOTH);
        agregarWord(responsabilidad, "La entrega se realiza de acuerdo con los reglamentos, las Normas Básicas del SABS y el Decreto Supremo 0181. ", false, 10);
        agregarWord(responsabilidad, "La persona que recibe los bienes asume la responsabilidad por su pérdida, deterioro o mal uso.", true, 10);
        responsabilidad.setSpacingBefore(230);
        responsabilidad.setSpacingAfter(190);
        mantenerConSiguiente(responsabilidad);
        XWPFParagraph constancia = WordDocumentos.parrafo(doc,
                "Para constancia de la recepción firmamos al pie del presente documento.", 10, false, ParagraphAlignment.LEFT);
        mantenerConSiguiente(constancia);
        XWPFTable firmas = doc.createTable(1, 2);
        firmas.setWidth("100%");
        firmas.getRow(0).getCtRow().addNewTrPr().addNewCantSplit();
        var bordes = firmas.getCTTbl().getTblPr().addNewTblBorders();
        bordes.addNewTop().setVal(STBorder.NIL);
        bordes.addNewBottom().setVal(STBorder.NIL);
        bordes.addNewLeft().setVal(STBorder.NIL);
        bordes.addNewRight().setVal(STBorder.NIL);
        bordes.addNewInsideH().setVal(STBorder.NIL);
        bordes.addNewInsideV().setVal(STBorder.NIL);
        WordDocumentos.celda(firmas.getRow(0).getCell(0), "\n\n\n____________________________\nRECIBÍ CONFORME", true, 9);
        WordDocumentos.celda(firmas.getRow(0).getCell(1), "\n\n\n____________________________\nENTREGUÉ CONFORME", true, 9);
        firmas.getRow(0).getCell(0).getParagraphs().get(0).setAlignment(ParagraphAlignment.CENTER);
        firmas.getRow(0).getCell(1).getParagraphs().get(0).setAlignment(ParagraphAlignment.CENTER);
        firmas.getRow(0).getTableCells().forEach(c -> c.getParagraphs().forEach(ReporteAdicionalService::mantenerConSiguiente));
        XWPFParagraph vistoBueno = WordDocumentos.parrafo(doc, "Vo. Bo.", 9, true, ParagraphAlignment.CENTER);
        vistoBueno.setSpacingBefore(220);
        vistoBueno.setSpacingAfter(80);
        mantenerConSiguiente(vistoBueno);
        WordDocumentos.parrafo(doc, "C.c./Arch. " + (iniciales.isEmpty() ? "" : iniciales) + "/A-F", 8, false, ParagraphAlignment.LEFT);
    }

    private static void mantenerConSiguiente(XWPFParagraph parrafo) {
        parrafo.getCTP().getPPr().addNewKeepNext();
    }

    private static void agregarWord(XWPFParagraph parrafo, String texto, boolean negrita, int puntos) {
        XWPFRun run = parrafo.createRun();
        run.setFontFamily("Arial");
        run.setFontSize(puntos);
        run.setBold(negrita);
        run.setText(texto);
    }

    private static void escribirActa(Document doc, List<ActivoAdicional> bienes, LocalDateTime fecha,
                                     Firmantes f, String iniciales) throws Exception {
        Activo primero = bienes.get(0).getActivo();
        Oficina oficina = primero.getOficina();
        Responsable responsable = primero.getResponsable();

        Paragraph titulo = new Paragraph("ACTA DE ASIGNACION INDIVIDUAL\nDE BIENES - ADICIONAL " + fecha.getYear(), TITULO);
        titulo.setAlignment(Element.ALIGN_CENTER);
        titulo.setLeading(17f);
        titulo.setSpacingAfter(20);
        doc.add(titulo);

        String mes = fecha.getMonth().getDisplayName(TextStyle.FULL, Locale.forLanguageTag("es")).toUpperCase(Locale.ROOT);
        Paragraph intro = new Paragraph();
        intro.setAlignment(Element.ALIGN_JUSTIFIED);
        intro.setLeading(14.5f);
        intro.setSpacingAfter(12);
        intro.add(new Chunk("En la ciudad de " + f.ciudad + " a " + String.format("%02d", fecha.getDayOfMonth())
                + " días del mes de " + mes + " del " + fecha.getYear() + ", a horas "
                + fecha.format(DateTimeFormatter.ofPattern("HH:mm"))
                + " en los predios de la Universidad Amazónica de Pando en presencia de la ", NORMAL));
        intro.add(new Chunk(f.activos + ".", NORMAL));
        intro.add(new Chunk(" Responsable de Activos Fijos", NEGRITA));
        if (f.vistoBueno != null) {
            intro.add(new Chunk(" dando el visto bueno " + f.articuloVistoBueno + " ", NORMAL));
            intro.add(new Chunk(f.vistoBueno, NEGRITA));
        }
        intro.add(new Chunk(" se procedió a la ", NORMAL));
        intro.add(new Chunk("ASIGNACION INDIVIDUAL ADICIONAL", NEGRITA));
        intro.add(new Chunk(" de los Activos Fijos que se encuentran físicamente en la oficina indicada a continuación, ", NORMAL));
        intro.add(new Chunk("y que no fueron inventariados ni codificados por la empresa que realizó el Revalúo, "
                + "mencionando así los activos con las siguientes características:", NORMAL));
        doc.add(intro);

        Paragraph ubicacion = new Paragraph(ubicacion(oficina), NEGRITA);
        ubicacion.setAlignment(Element.ALIGN_CENTER);
        ubicacion.setSpacingAfter(12);
        doc.add(ubicacion);

        PdfPTable tabla = new PdfPTable(new float[] { 1f, 4.6f, 2.5f, 1.9f });
        tabla.setWidthPercentage(100);
        tabla.setHeaderRows(1);
        tabla.setSplitRows(false);
        for (String cab : List.of("Ítem", "Descripción", "Código", "Observación")) tabla.addCell(cabecera(cab));
        int numero = 1;
        for (ActivoAdicional x : bienes) {
            Activo a = x.getActivo();
            tabla.addCell(celda(new Phrase(String.format("%02d", numero++), TABLA), Element.ALIGN_CENTER));
            tabla.addCell(celda(new Phrase(texto(a.getDescripcion(), "SIN DESCRIPCIÓN"), TABLA), Element.ALIGN_LEFT));
            tabla.addCell(celda(codigo(a), Element.ALIGN_CENTER));
            tabla.addCell(celda(observacion(a, x), Element.ALIGN_CENTER));
        }
        tabla.setSpacingAfter(6);
        doc.add(tabla);

        PdfPTable cierre = new PdfPTable(1);
        cierre.setWidthPercentage(100);
        cierre.setKeepTogether(true);
        PdfPCell contenido = new PdfPCell();
        contenido.setBorder(Rectangle.NO_BORDER);
        contenido.setPadding(0);
        Paragraph al = new Paragraph("Al:", NORMAL);
        al.setSpacingAfter(10);
        contenido.addElement(al);
        contenido.addElement(recuadroResponsable(responsable));

        Paragraph legal = new Paragraph();
        legal.setAlignment(Element.ALIGN_JUSTIFIED);
        legal.setLeading(14.5f);
        legal.setSpacingBefore(16);
        legal.add(new Chunk("La entrega se realiza de acuerdo con los reglamentos, las Normas Básicas del SABS y el Decreto "
                + "Supremo 0181. ", NORMAL));
        legal.add(new Chunk("La persona que recibe los bienes asume la responsabilidad por su pérdida, deterioro o mal uso.", NEGRITA));
        contenido.addElement(legal);
        Paragraph constancia = new Paragraph("Para constancia de la recepción firmamos al pie del presente documento.", NORMAL);
        constancia.setLeading(14.5f);
        contenido.addElement(constancia);
        contenido.addElement(firmas(iniciales));
        cierre.addCell(contenido);
        doc.add(cierre);
    }

    /** «Al:» — recuadro centrado con nombre, C.I. y cargo del que recibe. */
    private static PdfPTable recuadroResponsable(Responsable r) {
        Paragraph p = new Paragraph();
        p.setAlignment(Element.ALIGN_CENTER);
        p.setLeading(13f);
        if (r == null) {
            p.add(new Chunk("SIN RESPONSABLE REGISTRADO", NEGRITA));
        } else {
            Persona persona = r.getPersona();
            String ci = persona == null ? null : persona.getCi();
            String ext = persona == null ? null : persona.getExtension();
            String linea = nombreResponsable(r) + (vacio(ci) ? ""
                    : "  C.I. " + ci.trim() + (vacio(ext) ? "" : " " + ext.trim().toUpperCase(Locale.ROOT) + "."));
            p.add(new Chunk(linea, NORMAL));
            String cargo = r.getCargo() == null ? null : r.getCargo().getNombre();
            if (!vacio(cargo)) {
                String c = cargo.trim().toUpperCase(Locale.ROOT);
                if (!c.contains("U.A.P") && !c.matches(".*\\bUAP\\b.*")) c += " – U.A.P.";
                p.add(Chunk.NEWLINE);
                p.add(new Chunk(c, NEGRITA));
            }
        }
        PdfPCell c = new PdfPCell();
        c.addElement(p);
        c.setHorizontalAlignment(Element.ALIGN_CENTER);
        c.setBorderWidth(0.8f);
        c.setPaddingTop(4);
        c.setPaddingBottom(10);
        c.setPaddingLeft(10);
        c.setPaddingRight(10);
        PdfPTable t = new PdfPTable(1);
        t.setWidthPercentage(78);
        t.setHorizontalAlignment(Element.ALIGN_CENTER);
        t.addCell(c);
        return t;
    }

    /** RECIBI / ENTREGUE CONFORME arriba, Vo. Bo. al centro debajo, y la copia de archivo. */
    private static PdfPTable firmas(String iniciales) {
        PdfPTable t = new PdfPTable(2);
        t.setWidthPercentage(100);
        t.setKeepTogether(true);
        t.setSpacingBefore(46);
        t.addCell(firma("RECIBI CONFORME", 1));
        t.addCell(firma("ENTREGUE CONFORME", 1));
        PdfPCell vobo = firma("Vo. Bo.", 2);
        vobo.setPaddingTop(44);
        t.addCell(vobo);
        PdfPCell copia = new PdfPCell(new Phrase("C.c./Arch.\n" + (iniciales.isEmpty() ? "" : iniciales) + "/A-F", PIE));
        copia.setColspan(2);
        copia.setBorder(Rectangle.NO_BORDER);
        copia.setPaddingTop(4);
        t.addCell(copia);
        return t;
    }

    private static PdfPCell firma(String texto, int columnas) {
        PdfPCell c = new PdfPCell(new Phrase(texto, NEGRITA));
        c.setColspan(columnas);
        c.setBorder(Rectangle.NO_BORDER);
        c.setHorizontalAlignment(Element.ALIGN_CENTER);
        return c;
    }

    private static PdfPCell cabecera(String valor) {
        PdfPCell c = new PdfPCell(new Phrase(valor, CAB_TABLA));
        c.setBackgroundColor(AZUL_CABECERA);
        c.setHorizontalAlignment(Element.ALIGN_CENTER);
        c.setVerticalAlignment(Element.ALIGN_MIDDLE);
        c.setPaddingTop(8);
        c.setPaddingBottom(8);
        return c;
    }

    private static PdfPCell celda(Phrase valor, int alineacion) {
        PdfPCell c = new PdfPCell(valor);
        c.setHorizontalAlignment(alineacion);
        c.setVerticalAlignment(Element.ALIGN_MIDDLE);
        c.setPaddingTop(6);
        c.setPaddingBottom(7);
        c.setPaddingLeft(6);
        c.setPaddingRight(6);
        return c;
    }

    /** El acta de adicionales identifica primero el código antiguo, como el modelo en papel. */
    private static String codigoDocumento(Activo a, String antiguo) {
        String nuevo = vacio(a.getCodigo()) ? null : a.getCodigo().trim();
        if (antiguo != null) return antiguo + (nuevo == null ? "" : "\nSCIAF: " + nuevo);
        return nuevo == null ? "S/CD" : nuevo;
    }

    private static Phrase codigo(Activo a) {
        String antiguo = vacio(a.getCodigoAntiguo()) ? null : a.getCodigoAntiguo().trim();
        String nuevo = vacio(a.getCodigo()) ? null : a.getCodigo().trim();
        Phrase p = new Phrase(antiguo != null ? antiguo : nuevo == null ? "S/CD" : nuevo, TABLA);
        if (antiguo != null && nuevo != null) p.add(new Chunk("\nSCIAF: " + nuevo, TABLA_NOTA));
        return p;
    }

    /** Estado de conservación (BUENO / REGULAR / MALO) y, debajo, la observación del registro. */
    private static Phrase observacion(Activo a, ActivoAdicional x) {
        String estado = a.getEstadoActivo() == null ? null : a.getEstadoActivo().getNombre();
        Phrase p = new Phrase(vacio(estado) ? "—" : estado.trim().toUpperCase(Locale.ROOT), TABLA);
        if (!vacio(x.getObservacion())) p.add(new Chunk("\n" + x.getObservacion().trim(), TABLA_NOTA));
        return p;
    }

    /** «Oficina 106 BIBLIOTECA CENTRAL – UAP CAMPUS LAS PALMAS». */
    private static String ubicacion(Oficina o) {
        String predio = o.getPredio() == null ? null : o.getPredio().getDescrip();
        return "Oficina " + (o.getCodOfi() == null ? "" : o.getCodOfi() + " ") + texto(o.getNombre(), "").trim()
                + (vacio(predio) ? "" : " – " + predio.trim());
    }

    /**
     * Quién firma, de la configuración de la gestión del acta y del responsable de entrega
     * seleccionado (lo mismo que el acta de asignación). Sin visto bueno configurado, el
     * párrafo lo omite en vez de inventar un nombre.
     */
    private Firmantes firmantes(LocalDateTime fecha) {
        ConfiguracionGestion conf = configuracionDao
                .findFirstByGestionAndEstadoOrderByIdConfigDesc(fecha.getYear(), "ACTIVO")
                .or(() -> configuracionDao.findFirstByGestionOrderByIdConfigDesc(fecha.getYear()))
                .orElse(null);
        String ciudad = conf != null && !vacio(conf.getCiudad()) ? conf.getCiudad().trim() : CIUDAD_POR_DEFECTO;
        String activos = conf != null && !vacio(conf.getResponsableActivosNombre())
                ? conf.getResponsableActivosNombre().trim() : ACTIVOS_POR_DEFECTO;
        if (activos.endsWith(".")) activos = activos.substring(0, activos.length() - 1);

        ResponsableEntrega entrega = null;
        try {
            entrega = responsableEntregaService.findSeleccionado();
        } catch (Exception e) {
            log.warn("[ADICIONALES] No se pudo leer el responsable de entrega: {}", e.getMessage());
        }
        if (entrega == null && conf != null) entrega = conf.getResponsableEntregaRef();
        String vistoBueno = entrega != null && !vacio(entrega.getNombre()) ? entrega.getNombre().trim()
                : conf != null && !vacio(conf.getResponsableEntrega()) ? conf.getResponsableEntrega().trim() : null;
        String genero = entrega == null ? null : entrega.getGenero();
        String articulo = "F".equalsIgnoreCase(genero) ? "de la" : "M".equalsIgnoreCase(genero) ? "del" : "de";
        return new Firmantes(ciudad, activos, vistoBueno, articulo);
    }

    private record Firmantes(String ciudad, String activos, String vistoBueno, String articuloVistoBueno) {}

    private static Image cargarMembrete() {
        try (InputStream in = ReporteAdicionalService.class.getResourceAsStream(MEMBRETE)) {
            return in == null ? null : Image.getInstance(in.readAllBytes());
        } catch (Exception e) {
            log.warn("[ADICIONALES] No se pudo cargar el membrete: {}", e.getMessage());
            return null;
        }
    }

    /** Membrete institucional de fondo en cada hoja. */
    private static final class Membrete extends PdfPageEventHelper {
        private final Image imagen;

        Membrete(Image imagen) { this.imagen = imagen; }

        @Override
        public void onStartPage(PdfWriter writer, Document document) {
            if (imagen == null) return;
            try {
                Rectangle hoja = document.getPageSize();
                imagen.scaleAbsolute(hoja.getWidth(), hoja.getHeight());
                imagen.setAbsolutePosition(0, 0);
                writer.getDirectContentUnder().addImage(imagen);
            } catch (Exception e) {
                // Sin membrete el acta sigue siendo legible.
            }
        }
    }

    private static String nombreOficina(Oficina o) {
        return (o.getCodOfi() == null ? "" : String.format("%03d - ", o.getCodOfi())) + o.getNombre();
    }

    private static String nombreResponsable(Responsable r) {
        Persona p = r.getPersona();
        return p == null ? texto(r.getCodigoFuncionario(), "SIN NOMBRE") : p.getNombreCompleto();
    }

    /** "Ruth Yoryina Espejo Cartagena" → "RYEC" (pie «C.c./Arch.», como el acta de asignación). */
    private static String iniciales(String nombre) {
        if (vacio(nombre)) return "";
        StringBuilder sb = new StringBuilder();
        for (String parte : nombre.trim().split("\\s+")) sb.append(Character.toUpperCase(parte.charAt(0)));
        return sb.toString();
    }

    private static boolean vacio(String valor) {
        return valor == null || valor.isBlank();
    }

    private static String texto(String valor, String defecto) {
        return vacio(valor) ? defecto : valor;
    }

    private static void exigirPermiso(Usuario usuario, Object permisos) {
        if (!AdicionalService.puede(usuario, permisos)) throw new SecurityException("Sin permiso para Activos adicionales.");
    }
}
