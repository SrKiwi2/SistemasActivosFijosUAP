package com.usic.SistemasActivosFijosUAP.model.service.control;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Document;
import com.itextpdf.text.Element;
import com.itextpdf.text.Font;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;
import com.usic.SistemasActivosFijosUAP.model.dto.control.CustodiaDTOs;
import com.usic.SistemasActivosFijosUAP.model.repository.CustodiaFaltantesRepo;

import lombok.RequiredArgsConstructor;

/**
 * Reportes de la custodia de faltantes (PDF para archivar o firmar, Excel para trabajar) y
 * la conciliación.
 * <ul>
 *   <li><b>Custodia por predio</b>: lo que hay hoy en la oficina de faltantes, por persona,
 *       con oficina de origen, acta y situación. Cuando queda vacía, el predio está aclarado.</li>
 *   <li><b>Consolidado por persona</b>: faltantes de cada persona por predio y estado.</li>
 *   <li><b>Conciliación</b>: lo que no cierra entre los faltantes y dónde está cada bien.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class ReportesCustodiaService {

    /** Un traslado que no avanza en este tiempo se muestra en la conciliación. */
    private static final int MINUTOS_TRABADO = 30;

    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final Font F_TITULO = new Font(Font.FontFamily.HELVETICA, 13, Font.BOLD, PdfCustodiaComun.AZUL);
    private static final Font F_SUB    = new Font(Font.FontFamily.HELVETICA, 9, Font.NORMAL, PdfCustodiaComun.GRIS_TEXTO);
    private static final Font F_CAB    = new Font(Font.FontFamily.HELVETICA, 7.5f, Font.BOLD, BaseColor.WHITE);
    private static final Font F_GRUPO  = new Font(Font.FontFamily.HELVETICA, 8, Font.BOLD, BaseColor.WHITE);
    private static final Font F_CELDA  = new Font(Font.FontFamily.HELVETICA, 7.5f);
    private static final Font F_CODIGO = new Font(Font.FontFamily.COURIER, 7.5f, Font.BOLD);
    private static final Font F_NEGRITA = new Font(Font.FontFamily.HELVETICA, 8, Font.BOLD);
    private static final Font F_RESUMEN = new Font(Font.FontFamily.HELVETICA, 8.5f);

    private final CustodiaFaltantesRepo repo;

    public List<CustodiaDTOs.PredioCustodia> prediosConCustodia() {
        return repo.prediosConCustodia();
    }

    public CustodiaDTOs.Conciliacion conciliacion() {
        return new CustodiaDTOs.Conciliacion(repo.enCustodiaFueraDeCustodia(), repo.enCustodiaSinRegistro(),
                repo.trasladosTrabados(MINUTOS_TRABADO));
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Custodia por predio
    // ═══════════════════════════════════════════════════════════════════════

    private CustodiaDTOs.PredioCustodia predio(Long idPredio) {
        return repo.prediosConCustodia().stream()
                .filter(p -> Objects.equals(p.idPredio(), idPredio)).findFirst()
                .orElseThrow(() -> new ReglaNegocioException("Ese predio no tiene oficina de faltantes."));
    }

    public byte[] custodiaPredioPdf(Long idPredio, String usuario) throws Exception {
        CustodiaDTOs.PredioCustodia p = predio(idPredio);
        List<CustodiaDTOs.BienCustodia> bienes = repo.bienesEnCustodia(idPredio);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = abrir(out, "Reporte de custodia " + p.unidad(), usuario);

        titulo(doc, "REPORTE DE CUSTODIA DE FALTANTES",
                "Predio " + p.unidad() + " — " + p.predio() + "  ·  Oficina " + p.codOfi() + " — " + p.oficina());

        long enCustodia = bienes.stream().filter(b -> "En custodia".equals(b.situacion())).count();
        long pendienteBaja = bienes.stream().filter(b -> "RESUELTO".equals(b.estadoHallazgo())).count();
        long historicos = bienes.stream().filter(b -> b.estadoHallazgo() == null).count();
        long otros = bienes.size() - enCustodia - pendienteBaja - historicos;
        resumen(doc, "Total de bienes en la oficina de faltantes: " + bienes.size()
                + "   ·   En custodia: " + enCustodia
                + "   ·   Resueltos, pendientes de baja: " + pendienteBaja
                + "   ·   Históricos sin registro: " + historicos
                + (otros > 0 ? "   ·   Trasladándose: " + otros : ""));

        if (bienes.isEmpty()) {
            doc.add(new Paragraph("La oficina de faltantes está vacía: el predio no tiene faltantes en custodia.", F_RESUMEN));
        } else {
            PdfPTable t = tabla(new float[] { 0.5f, 1.9f, 4.2f, 2.6f, 1.5f, 1.9f },
                    "N°", "CÓDIGO", "DESCRIPCIÓN", "OFICINA DE ORIGEN", "ACTA", "SITUACIÓN");
            Map<String, List<CustodiaDTOs.BienCustodia>> porPersona = new LinkedHashMap<>();
            for (CustodiaDTOs.BienCustodia b : bienes) {
                String clave = nvl(b.persona(), "Sin responsable") + (b.ci() != null ? "  (C.I. " + b.ci() + ")" : "");
                porPersona.computeIfAbsent(clave, k -> new ArrayList<>()).add(b);
            }
            int n = 0;
            for (Map.Entry<String, List<CustodiaDTOs.BienCustodia>> e : porPersona.entrySet()) {
                grupo(t, 6, e.getKey() + "  ·  " + e.getValue().size() + " bien(es)");
                for (CustodiaDTOs.BienCustodia b : e.getValue()) {
                    n++;
                    t.addCell(celda(String.valueOf(n), F_CELDA, Element.ALIGN_CENTER));
                    t.addCell(celda(b.codigo(), F_CODIGO, Element.ALIGN_LEFT));
                    t.addCell(celda(b.descripcion(), F_CELDA, Element.ALIGN_LEFT));
                    t.addCell(celda(b.oficinaOrigen() != null ? b.codOfiOrigen() + " — " + b.oficinaOrigen() : "—",
                            F_CELDA, Element.ALIGN_LEFT));
                    t.addCell(celda(nvl(b.numeroActa(), "—"), F_CELDA, Element.ALIGN_LEFT));
                    t.addCell(celda(b.situacion(), F_CELDA, Element.ALIGN_LEFT));
                }
            }
            doc.add(t);
        }
        doc.close();
        return out.toByteArray();
    }

    public byte[] custodiaPredioExcel(Long idPredio) throws Exception {
        CustodiaDTOs.PredioCustodia p = predio(idPredio);
        List<CustodiaDTOs.BienCustodia> bienes = repo.bienesEnCustodia(idPredio);
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet s = wb.createSheet("Custodia " + p.unidad());
            CellStyle cab = estiloCabecera(wb);
            int fila = encabezadoExcel(s, cab, "Custodia de faltantes — " + p.unidad() + " — " + p.predio(),
                    "Persona", "C.I.", "Código", "Descripción", "Cód. oficina origen", "Oficina de origen",
                    "Acta", "Enviado a custodia", "Situación", "Resolución");
            for (CustodiaDTOs.BienCustodia b : bienes) {
                Row r = s.createRow(fila++);
                texto(r, 0, b.persona());
                texto(r, 1, b.ci());
                texto(r, 2, b.codigo());
                texto(r, 3, b.descripcion());
                texto(r, 4, b.codOfiOrigen() != null ? String.valueOf(b.codOfiOrigen()) : null);
                texto(r, 5, b.oficinaOrigen());
                texto(r, 6, b.numeroActa());
                texto(r, 7, b.fechaEnvio() != null ? b.fechaEnvio().format(FECHA_HORA) : null);
                texto(r, 8, b.situacion());
                texto(r, 9, b.tipoResolucion());
            }
            return cerrarExcel(wb, s, 10);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Consolidado por persona
    // ═══════════════════════════════════════════════════════════════════════

    public byte[] consolidadoPdf(boolean soloPendientes, String usuario) throws Exception {
        List<CustodiaDTOs.ConsolidadoFila> filas = repo.consolidado(soloPendientes);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = abrir(out, "Consolidado de faltantes por persona", usuario);
        titulo(doc, "CONSOLIDADO DE FALTANTES POR PERSONA",
                soloPendientes ? "Personas con faltantes sin aclarar" : "Todos los faltantes registrados (sin anulados)");

        long abiertos = filas.stream().mapToLong(CustodiaDTOs.ConsolidadoFila::abiertos).sum();
        long custodia = filas.stream().mapToLong(CustodiaDTOs.ConsolidadoFila::enCustodia).sum();
        long resueltos = filas.stream().mapToLong(CustodiaDTOs.ConsolidadoFila::resueltos).sum();
        long personas = filas.stream().map(CustodiaDTOs.ConsolidadoFila::idPersona).distinct().count();
        resumen(doc, "Personas: " + personas + "   ·   Abiertos: " + abiertos + "   ·   En custodia: " + custodia
                + "   ·   Resueltos: " + resueltos);

        if (filas.isEmpty()) {
            doc.add(new Paragraph("No hay faltantes con este alcance.", F_RESUMEN));
        } else {
            PdfPTable t = tabla(new float[] { 0.5f, 4f, 1.4f, 2.6f, 1.1f, 1.1f, 1.1f, 1f },
                    "N°", "PERSONA", "C.I.", "PREDIO", "ABIERTOS", "EN CUSTODIA", "RESUELTOS", "TOTAL");
            int n = 0;
            Long personaAnterior = null;
            for (CustodiaDTOs.ConsolidadoFila f : filas) {
                boolean nueva = !Objects.equals(f.idPersona(), personaAnterior);
                if (nueva) n++;
                personaAnterior = f.idPersona();
                t.addCell(celda(nueva ? String.valueOf(n) : "", F_CELDA, Element.ALIGN_CENTER));
                t.addCell(celda(nueva ? f.persona() : "", nueva ? F_NEGRITA : F_CELDA, Element.ALIGN_LEFT));
                t.addCell(celda(nueva ? nvl(f.ci(), "—") : "", F_CELDA, Element.ALIGN_LEFT));
                t.addCell(celda(f.unidad() + " — " + f.predio(), F_CELDA, Element.ALIGN_LEFT));
                t.addCell(celda(String.valueOf(f.abiertos()), F_CELDA, Element.ALIGN_CENTER));
                t.addCell(celda(String.valueOf(f.enCustodia()), F_CELDA, Element.ALIGN_CENTER));
                t.addCell(celda(String.valueOf(f.resueltos()), F_CELDA, Element.ALIGN_CENTER));
                t.addCell(celda(String.valueOf(f.total()), F_NEGRITA, Element.ALIGN_CENTER));
            }
            doc.add(t);
        }
        doc.close();
        return out.toByteArray();
    }

    public byte[] consolidadoExcel(boolean soloPendientes) throws Exception {
        List<CustodiaDTOs.ConsolidadoFila> filas = repo.consolidado(soloPendientes);
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet s = wb.createSheet("Consolidado");
            CellStyle cab = estiloCabecera(wb);
            int fila = encabezadoExcel(s, cab, "Consolidado de faltantes por persona",
                    "Persona", "C.I.", "Unidad", "Predio", "Abiertos", "En custodia", "Resueltos", "Total");
            for (CustodiaDTOs.ConsolidadoFila f : filas) {
                Row r = s.createRow(fila++);
                texto(r, 0, f.persona());
                texto(r, 1, f.ci());
                texto(r, 2, f.unidad());
                texto(r, 3, f.predio());
                r.createCell(4).setCellValue(f.abiertos());
                r.createCell(5).setCellValue(f.enCustodia());
                r.createCell(6).setCellValue(f.resueltos());
                r.createCell(7).setCellValue(f.total());
            }
            return cerrarExcel(wb, s, 8);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Piezas PDF
    // ═══════════════════════════════════════════════════════════════════════

    private Document abrir(ByteArrayOutputStream out, String tituloDoc, String usuario) throws Exception {
        Document doc = new Document(PageSize.LETTER, PdfCustodiaComun.MARGEN_LADO, PdfCustodiaComun.MARGEN_LADO,
                PdfCustodiaComun.MARGEN_ARRIBA, PdfCustodiaComun.PIE_MEMBRETE + 22);
        PdfWriter writer = PdfWriter.getInstance(doc, out);
        writer.setPageEvent(new PdfCustodiaComun.MembreteYPie(PdfCustodiaComun.cargarMembrete(),
                "SCIAF · " + tituloDoc + " · generado el " + LocalDateTime.now().format(FECHA_HORA)
                        + (usuario != null ? " por " + usuario : "")));
        doc.addTitle(tituloDoc);
        doc.addAuthor("Sección de Activos Fijos - UAP");
        doc.addCreator("SCIAF");
        doc.open();
        return doc;
    }

    private void titulo(Document doc, String titulo, String sub) throws Exception {
        Paragraph seccion = new Paragraph("SECCIÓN DE ACTIVOS FIJOS", F_SUB);
        seccion.setAlignment(Element.ALIGN_CENTER);
        doc.add(seccion);
        Paragraph t = new Paragraph(titulo, F_TITULO);
        t.setAlignment(Element.ALIGN_CENTER);
        doc.add(t);
        Paragraph s = new Paragraph(sub + "  ·  al " + LocalDateTime.now().format(FECHA), F_SUB);
        s.setAlignment(Element.ALIGN_CENTER);
        s.setSpacingAfter(6);
        doc.add(s);
    }

    private void resumen(Document doc, String texto) throws Exception {
        PdfPTable r = new PdfPTable(1);
        r.setWidthPercentage(100);
        PdfPCell c = new PdfPCell(new Phrase(texto, F_RESUMEN));
        c.setBackgroundColor(PdfCustodiaComun.GRIS_CLARO);
        c.setBorder(PdfPCell.NO_BORDER);
        c.setPadding(6);
        r.addCell(c);
        r.setSpacingAfter(8);
        doc.add(r);
    }

    private PdfPTable tabla(float[] anchos, String... cabeceras) throws Exception {
        PdfPTable t = new PdfPTable(anchos.length);
        t.setWidthPercentage(100);
        t.setWidths(anchos);
        t.setHeaderRows(1);
        for (String c : cabeceras) {
            PdfPCell cell = new PdfPCell(new Phrase(c, F_CAB));
            cell.setBackgroundColor(PdfCustodiaComun.AZUL);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setPadding(4);
            t.addCell(cell);
        }
        return t;
    }

    private void grupo(PdfPTable t, int columnas, String texto) {
        PdfPCell c = new PdfPCell(new Phrase(texto, F_GRUPO));
        c.setColspan(columnas);
        c.setBackgroundColor(PdfCustodiaComun.PREDIO);
        c.setPadding(4);
        t.addCell(c);
    }

    private PdfPCell celda(String texto, Font f, int alineacion) {
        PdfPCell c = new PdfPCell(new Phrase(texto == null ? "" : texto, f));
        c.setHorizontalAlignment(alineacion);
        c.setPadding(3);
        return c;
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Piezas Excel
    // ═══════════════════════════════════════════════════════════════════════

    private CellStyle estiloCabecera(XSSFWorkbook wb) {
        CellStyle st = wb.createCellStyle();
        org.apache.poi.ss.usermodel.Font f = wb.createFont();
        f.setBold(true);
        f.setColor(IndexedColors.WHITE.getIndex());
        st.setFont(f);
        st.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        st.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        st.setBorderBottom(BorderStyle.THIN);
        return st;
    }

    /** Título en la fila 1, cabeceras en la 3; devuelve la primera fila libre. */
    private int encabezadoExcel(Sheet s, CellStyle cab, String titulo, String... columnas) {
        Row t = s.createRow(0);
        t.createCell(0).setCellValue(titulo + " — " + LocalDateTime.now().format(FECHA_HORA));
        Row h = s.createRow(2);
        for (int i = 0; i < columnas.length; i++) {
            Cell c = h.createCell(i);
            c.setCellValue(columnas[i]);
            c.setCellStyle(cab);
        }
        s.createFreezePane(0, 3);
        return 3;
    }

    private void texto(Row r, int col, String valor) {
        r.createCell(col).setCellValue(valor == null ? "" : valor);
    }

    /**
     * Ancho por el texto más largo de cada columna. No usa {@code autoSizeColumn}: mide con
     * fuentes de AWT y en un servidor sin fuentes instaladas falla.
     */
    private byte[] cerrarExcel(XSSFWorkbook wb, Sheet s, int columnas) throws Exception {
        int[] largo = new int[columnas];
        for (Row r : s) {
            if (r.getRowNum() < 2) continue;   // el título ocupa una sola celda ancha
            for (int i = 0; i < columnas; i++) {
                Cell c = r.getCell(i);
                if (c == null) continue;
                String v = c.getCellType() == org.apache.poi.ss.usermodel.CellType.NUMERIC
                        ? String.valueOf((long) c.getNumericCellValue()) : c.getStringCellValue();
                largo[i] = Math.max(largo[i], v.length());
            }
        }
        for (int i = 0; i < columnas; i++) {
            s.setColumnWidth(i, Math.min(60, Math.max(8, largo[i] + 2)) * 256);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        wb.write(out);
        return out.toByteArray();
    }

    private static String nvl(String s, String otro) {
        return (s == null || s.isBlank()) ? otro : s;
    }
}
