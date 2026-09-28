package com.usic.SistemasActivosFijosUAP.model.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import com.usic.SistemasActivosFijosUAP.model.entity.AsignacionActivo;
import com.usic.SistemasActivosFijosUAP.model.entity.DetalleAsignacionActivo;

/**
 * Arma el reporte Excel de actas de asignación replicando la planilla de control que
 * ya se llevaba a mano: una fila por acta con sus datos fusionados verticalmente y una
 * sub-fila por cada bien vigente que contiene.
 * <p>
 * Quien llama a {@link #generar(List)} es responsable de traer las actas con sus
 * {@code detalles}, {@code responsable} y {@code oficinaDestino} ya cargados (ver
 * {@code IAsignacionActivoDao.findAllByIdInConDetalles}) — este servicio no toca la
 * base de datos.
 */
@Service
public class ExcelAsignacionReportService {

    private static final String[] ENCABEZADOS = {
        "N°", "FECHA DE ASIGNACIÓN", "HOJA DE RUTA / ARD", "CERTIFICACIÓN", "PREV.",
        "SISTEMATIZADO POR", "COMPROBANTE", "UBICACIÓN", "NOMBRE COMPLETO", "C.I.",
        "CARGO", "CÓDIGO", "DESCRIPCIÓN DEL ACTIVO", "OBS."
    };

    /** Ancho de cada columna, en caracteres (se multiplica x256 para POI). */
    private static final int[] ANCHOS = { 5, 13, 15, 15, 14, 18, 12, 24, 26, 13, 22, 16, 34, 32 };

    /** Columnas de dato de la ACTA (se fusionan verticalmente); quedan fuera CÓDIGO (11) y DESCRIPCIÓN (12), que son por bien. */
    private static final int[] COLUMNAS_ACTA = { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 13 };

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /**
     * @param nombresPorUsuario nombre a mostrar por id de usuario, para la columna
     *                          SISTEMATIZADO POR — se resuelve a partir de
     *                          {@code registroIdUsuario} (quien registró el acta), no
     *                          es un dato propio de {@code AsignacionActivo}.
     */
    public byte[] generar(List<AsignacionActivo> asignaciones, Map<Long, String> nombresPorUsuario) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Asignaciones");
            sheet.createFreezePane(0, 1);
            for (int col = 0; col < ANCHOS.length; col++) {
                sheet.setColumnWidth(col, ANCHOS[col] * 256);
            }

            CellStyle estiloEncabezado = estiloEncabezado(workbook);
            CellStyle estiloCelda = estiloCelda(workbook);
            CellStyle estiloCeldaCentrado = estiloCeldaCentrado(workbook);

            escribirEncabezado(sheet, estiloEncabezado);

            int fila = 1;
            int numero = 1;
            for (AsignacionActivo asignacion : asignaciones) {
                fila = escribirActa(sheet, fila, numero++, asignacion, nombresPorUsuario, estiloCelda, estiloCeldaCentrado);
            }

            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            workbook.write(salida);
            return salida.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo generar el reporte Excel de asignaciones", e);
        }
    }

    /** Escribe una acta a partir de {@code filaInicio} y devuelve la fila siguiente libre. */
    private int escribirActa(Sheet sheet, int filaInicio, int numero, AsignacionActivo asignacion,
                              Map<Long, String> nombresPorUsuario,
                              CellStyle estiloCelda, CellStyle estiloCeldaCentrado) {

        List<DetalleAsignacionActivo> vigentes = asignacion.getDetalles() == null ? List.of()
                : asignacion.getDetalles().stream().filter(DetalleAsignacionActivo::estaVigente).toList();

        int filas = Math.max(vigentes.size(), 1);

        String fecha = asignacion.getFechaAsignacion() != null
                ? asignacion.getFechaAsignacion().format(FORMATO_FECHA) : "";
        String prev = valorOVacio(asignacion.getCodigoCompletoNormalizado());
        String comprobante = asignacion.getComprobante() == null ? ""
                : (asignacion.getComprobante() ? "SI" : "NO");
        String ubicacion = asignacion.getOficinaDestino() != null
                ? valorOVacio(asignacion.getOficinaDestino().getNombre()) : "";
        // "Sistematizado por" es quien registró el acta (registroIdUsuario), no un
        // dato propio: ver la nota en AsignacionActivo.
        String sistematizadoPor = valorOVacio(nombresPorUsuario.get(asignacion.getRegistroIdUsuario()));

        String nombreCompleto = "";
        String ci = "";
        String cargo = "";
        if (asignacion.getResponsable() != null) {
            if (asignacion.getResponsable().getPersona() != null) {
                nombreCompleto = valorOVacio(asignacion.getResponsable().getPersona().getNombreCompleto());
                ci = valorOVacio(asignacion.getResponsable().getPersona().getCi());
            }
            if (asignacion.getResponsable().getCargo() != null) {
                cargo = valorOVacio(asignacion.getResponsable().getCargo().getNombre());
            }
        }

        for (int i = 0; i < filas; i++) {
            Row row = sheet.createRow(filaInicio + i);
            DetalleAsignacionActivo detalle = i < vigentes.size() ? vigentes.get(i) : null;

            if (i == 0) {
                escribirCelda(row, 0, String.valueOf(numero), estiloCeldaCentrado);
                escribirCelda(row, 1, fecha, estiloCeldaCentrado);
                escribirCelda(row, 2, valorOVacio(asignacion.getHojaRuta()), estiloCeldaCentrado);
                escribirCelda(row, 3, valorOVacio(asignacion.getCertificacion()), estiloCeldaCentrado);
                escribirCelda(row, 4, prev, estiloCeldaCentrado);
                escribirCelda(row, 5, sistematizadoPor, estiloCelda);
                escribirCelda(row, 6, comprobante, estiloCeldaCentrado);
                escribirCelda(row, 7, ubicacion, estiloCelda);
                escribirCelda(row, 8, nombreCompleto, estiloCelda);
                escribirCelda(row, 9, ci, estiloCeldaCentrado);
                escribirCelda(row, 10, cargo, estiloCelda);
                escribirCelda(row, 13, valorOVacio(asignacion.getObservacion()), estiloCelda);
            } else {
                // Filas de continuación: las celdas de acta quedan vacías pero con el
                // mismo estilo — la fusión de abajo las oculta visualmente igual, pero
                // sin esto la región fusionada quedaría sin borde en Excel.
                for (int col : COLUMNAS_ACTA) escribirCelda(row, col, "", estiloCelda);
            }

            escribirCelda(row, 11, codigoDetalle(detalle), estiloCeldaCentrado);
            escribirCelda(row, 12, descripcionDetalle(detalle), estiloCelda);
        }

        if (filas > 1) {
            int filaFin = filaInicio + filas - 1;
            for (int col : COLUMNAS_ACTA) {
                sheet.addMergedRegion(new CellRangeAddress(filaInicio, filaFin, col, col));
            }
        }

        return filaInicio + filas;
    }

    private String codigoDetalle(DetalleAsignacionActivo detalle) {
        if (detalle == null) return "";
        if (detalle.getCodigoActivoSnapshot() != null) return detalle.getCodigoActivoSnapshot();
        return detalle.getActivo() != null ? valorOVacio(detalle.getActivo().getCodigo()) : "";
    }

    private String descripcionDetalle(DetalleAsignacionActivo detalle) {
        if (detalle == null) return "";
        if (detalle.getDescripcionActivoSnapshot() != null) return detalle.getDescripcionActivoSnapshot();
        return detalle.getActivo() != null ? valorOVacio(detalle.getActivo().getDescripcion()) : "";
    }

    private String valorOVacio(String s) {
        return s == null ? "" : s;
    }

    private void escribirEncabezado(Sheet sheet, CellStyle estilo) {
        Row row = sheet.createRow(0);
        row.setHeightInPoints(30f);
        for (int i = 0; i < ENCABEZADOS.length; i++) {
            escribirCelda(row, i, ENCABEZADOS[i], estilo);
        }
    }

    private void escribirCelda(Row row, int columna, String valor, CellStyle estilo) {
        Cell cell = row.createCell(columna);
        cell.setCellValue(valor);
        cell.setCellStyle(estilo);
    }

    private CellStyle estiloEncabezado(XSSFWorkbook workbook) {
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        CellStyle estilo = workbook.createCellStyle();
        estilo.setFont(font);
        estilo.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        estilo.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        estilo.setAlignment(HorizontalAlignment.CENTER);
        estilo.setVerticalAlignment(VerticalAlignment.CENTER);
        estilo.setWrapText(true);
        bordear(estilo);
        return estilo;
    }

    private CellStyle estiloCelda(XSSFWorkbook workbook) {
        CellStyle estilo = workbook.createCellStyle();
        estilo.setVerticalAlignment(VerticalAlignment.CENTER);
        estilo.setWrapText(true);
        bordear(estilo);
        return estilo;
    }

    private CellStyle estiloCeldaCentrado(XSSFWorkbook workbook) {
        CellStyle estilo = estiloCelda(workbook);
        estilo.setAlignment(HorizontalAlignment.CENTER);
        return estilo;
    }

    private void bordear(CellStyle estilo) {
        estilo.setBorderTop(BorderStyle.THIN);
        estilo.setBorderBottom(BorderStyle.THIN);
        estilo.setBorderLeft(BorderStyle.THIN);
        estilo.setBorderRight(BorderStyle.THIN);
    }
}
