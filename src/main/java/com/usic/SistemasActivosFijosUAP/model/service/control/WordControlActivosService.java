package com.usic.SistemasActivosFijosUAP.model.service.control;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBody;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTbl;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblGrid;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblWidth;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTblLayoutType;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTblWidth;
import org.springframework.stereotype.Service;

import com.usic.SistemasActivosFijosUAP.model.dto.control.ActivoUbicacionDTO;
import com.usic.SistemasActivosFijosUAP.model.dto.control.FaltanteDTO;
import com.usic.SistemasActivosFijosUAP.model.dto.control.TileOficinaDTO;
import com.usic.SistemasActivosFijosUAP.model.dto.control.TileResponsableDTO;
import com.usic.SistemasActivosFijosUAP.model.service.documento.MembreteWord;

import lombok.extern.slf4j.Slf4j;

/**
 * Informes en Word del módulo de Control de Activos.
 *
 * <p>Es un servicio aparte de {@code WordAsignacionActivoService} y de
 * {@code WordInternoTransferenciaService} —que ya emiten actas en producción— para no
 * arriesgar documentos que hoy funcionan al tocar helpers compartidos. Los ayudantes de
 * tabla/celda/membrete se repiten acá adentro, siguiendo lo que ya hacen esos dos
 * servicios; a cambio, los cuatro informes de este módulo comparten una sola copia.
 */
@Slf4j
@Service
public class WordControlActivosService {

    private static final String FUENTE = "Arial";

    /** Carta, en twips, igual que las actas del sistema. */
    private static final int PAGINA_ANCHO = 12240;
    private static final int PAGINA_ALTO  = 15840;
    private static final int MARGEN       = 1440;
    private static final int CONTENIDO_ANCHO = PAGINA_ANCHO - MARGEN - MARGEN;

    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter FECHA      = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /**
     * Informe de verificación / ubicación de los bienes seleccionados.
     *
     * @param activos lo seleccionado, ya releído de la base
     * @param usuario quién lo emite; puede ser null
     */
    public byte[] informeVerificacion(List<ActivoUbicacionDTO> activos, String usuario) throws IOException {
        try (XWPFDocument doc = new XWPFDocument();
             ByteArrayOutputStream salida = new ByteArrayOutputStream()) {

            titulo(doc, "INFORME DE VERIFICACIÓN Y UBICACIÓN DE BIENES");
            subtitulo(doc, activos.size() + " bien(es) · Emitido el " +
                    LocalDateTime.now().format(FECHA_HORA) +
                    (usuario != null && !usuario.isBlank() ? " por " + usuario : ""));

            tablaDeBienes(doc, activos);
            totales(doc, activos);
            firmas(doc);

            configurarPagina(doc);

            doc.write(salida);
            return salida.toByteArray();
        }
    }

    /**
     * Acta de responsabilidad por responsable: documento formal, con membrete
     * institucional y pie de firmas, listando los bienes a su cargo.
     *
     * <p>Es el único de los cuatro informes que lleva el membrete de fondo: los otros
     * son listados de trabajo y sobre una tabla densa el fondo resta legibilidad. Acá
     * sí corresponde, porque es un papel que se firma.
     */
    public byte[] actaPorResponsable(TileResponsableDTO resp, List<ActivoUbicacionDTO> activos,
                                     String usuario) throws IOException {
        try (XWPFDocument doc = new XWPFDocument();
             ByteArrayOutputStream salida = new ByteArrayOutputStream()) {

            titulo(doc, "ACTA DE RESPONSABILIDAD DE BIENES");
            subtitulo(doc, "Emitida el " + LocalDateTime.now().format(FECHA_HORA) +
                    (usuario != null && !usuario.isBlank() ? " por " + usuario : ""));

            // Cabecera con los datos de quien se hace cargo.
            int[] anchosCab = { pct(22), pct(78) };
            XWPFTable cab = doc.createTable(4, 2);
            configurarTabla(cab, anchosCab);
            filaDato(cab.getRow(0), anchosCab, "Responsable", texto(resp.nombre()));
            filaDato(cab.getRow(1), anchosCab, "C.I.",        texto(resp.ci()));
            filaDato(cab.getRow(2), anchosCab, "Cargo",       texto(resp.cargo()));
            filaDato(cab.getRow(3), anchosCab, "Oficina",     texto(resp.oficina()));

            XWPFParagraph intro = doc.createParagraph();
            intro.setSpacingBefore(240);
            intro.setSpacingAfter(180);
            intro.setAlignment(ParagraphAlignment.BOTH);
            run(intro, "Por medio de la presente, quien suscribe declara tener bajo su custodia y "
                    + "responsabilidad los bienes que se detallan a continuación, comprometiéndose a su "
                    + "buen uso, conservación y a informar cualquier cambio de ubicación o estado.", false, 10);

            tablaDeBienes(doc, activos);
            totales(doc, activos);

            // Si el responsable ya no está vigente pero sigue con bienes, el acta tiene
            // que decirlo: es la inconsistencia que este módulo existe para mostrar.
            if (!resp.vigente() && !activos.isEmpty()) {
                XWPFParagraph aviso = doc.createParagraph();
                aviso.setSpacingBefore(180);
                run(aviso, "OBSERVACIÓN: este responsable figura como NO VIGENTE y aún tiene bienes "
                        + "registrados a su nombre.", true, 9);
            }

            firmas(doc);
            configurarPagina(doc);
            MembreteWord.aplicar(doc);

            doc.write(salida);
            return salida.toByteArray();
        }
    }

    /** Fila etiqueta/valor de la cabecera del acta. */
    private void filaDato(XWPFTableRow fila, int[] anchos, String etiqueta, String valor) {
        anchosFila(fila, anchos);
        celda(fila.getCell(0), etiqueta, true,  ParagraphAlignment.LEFT, 9, "F2F2F2");
        celda(fila.getCell(1), valor,    false, ParagraphAlignment.LEFT, 9, null);
    }

    /**
     * Informe de faltantes: los hallazgos tal como los muestra la pantalla de Faltantes.
     *
     * @param faltantes hallazgos ya filtrados
     * @param alcance   qué se filtró, en texto, para que el papel diga sobre qué habla
     */
    public byte[] informeFaltantes(List<FaltanteDTO> faltantes, String alcance, String usuario) throws IOException {
        try (XWPFDocument doc = new XWPFDocument();
             ByteArrayOutputStream salida = new ByteArrayOutputStream()) {

            titulo(doc, "INFORME DE FALTANTES Y OBSERVACIONES");
            subtitulo(doc, alcance + " · " + faltantes.size() + " hallazgo(s) · Emitido el " +
                    LocalDateTime.now().format(FECHA_HORA) +
                    (usuario != null && !usuario.isBlank() ? " por " + usuario : ""));

            int[] anchos = { pct(4), pct(15), pct(24), pct(19), pct(19), pct(11), pct(8) };
            XWPFTable tabla = doc.createTable(1, 7);
            configurarTabla(tabla, anchos);
            encabezados(tabla.getRow(0),
                    "N°", "CÓDIGO", "DESCRIPCIÓN", "UBICACIÓN / RESPONSABLE", "DISCREPANCIA", "DETECTADO", "ESTADO");

            int n = 1;
            for (FaltanteDTO f : faltantes) {
                XWPFTableRow fila = tabla.createRow();
                anchosFila(fila, anchos);
                celda(fila.getCell(0), String.valueOf(n++),               false, ParagraphAlignment.CENTER, 8, null);
                celda(fila.getCell(1), texto(f.codigo()),                 true,  ParagraphAlignment.LEFT,   8, null);
                celda(fila.getCell(2), texto(f.descripcion()),            false, ParagraphAlignment.LEFT,   7, null);
                celda(fila.getCell(3), texto(f.oficina()) + "\n" + texto(f.responsable()),
                                                                          false, ParagraphAlignment.LEFT,   7, null);
                celda(fila.getCell(4), texto(f.descripcionDiscrepancia()), false, ParagraphAlignment.LEFT,  7, null);
                celda(fila.getCell(5), f.fechaDeteccion() == null ? "—" : f.fechaDeteccion().format(FECHA),
                                                                          false, ParagraphAlignment.CENTER, 7, null);
                celda(fila.getCell(6), texto(f.tipoHallazgo()) + "\n" + texto(f.estadoHallazgo()),
                                                                          false, ParagraphAlignment.CENTER, 7, null);
            }

            long abiertos = faltantes.stream().filter(f -> "ABIERTO".equals(f.estadoHallazgo())).count();
            XWPFParagraph p = doc.createParagraph();
            p.setSpacingBefore(240);
            run(p, "Total de hallazgos: " + faltantes.size() + "   ·   Abiertos: " + abiertos
                    + "   ·   Resueltos: " + (faltantes.size() - abiertos), true, 9);

            firmas(doc);
            configurarPagina(doc);
            doc.write(salida);
            return salida.toByteArray();
        }
    }

    /**
     * Resumen de control por oficina: cuántos bienes hay, cuántos faltan y cómo va el
     * levantamiento en cada una. Es la foto gerencial del mismo mapa.
     */
    public byte[] resumenPorOficina(String alcance, List<TileOficinaDTO> oficinas, String usuario) throws IOException {
        try (XWPFDocument doc = new XWPFDocument();
             ByteArrayOutputStream salida = new ByteArrayOutputStream()) {

            titulo(doc, "RESUMEN DE CONTROL DE ACTIVOS");
            subtitulo(doc, alcance + " · " + oficinas.size() + " oficina(s) · Emitido el " +
                    LocalDateTime.now().format(FECHA_HORA) +
                    (usuario != null && !usuario.isBlank() ? " por " + usuario : ""));

            int[] anchos = { pct(5), pct(34), pct(13), pct(13), pct(14), pct(21) };
            XWPFTable tabla = doc.createTable(1, 6);
            configurarTabla(tabla, anchos);
            encabezados(tabla.getRow(0),
                    "N°", "OFICINA", "RESPONS.", "ACTIVOS", "FALTANTES", "ESTADO DE CONTROL");

            long totalActivos = 0, totalFaltantes = 0;
            int n = 1;
            for (TileOficinaDTO o : oficinas) {
                XWPFTableRow fila = tabla.createRow();
                anchosFila(fila, anchos);
                celda(fila.getCell(0), String.valueOf(n++),                false, ParagraphAlignment.CENTER, 8, null);
                celda(fila.getCell(1), "Of. " + (o.codOfi() == null ? "—" : o.codOfi()) + " · " + texto(o.nombre()),
                                                                           false, ParagraphAlignment.LEFT,   8, null);
                celda(fila.getCell(2), String.valueOf(o.responsables()),   false, ParagraphAlignment.CENTER, 8, null);
                celda(fila.getCell(3), String.valueOf(o.activos()),        false, ParagraphAlignment.CENTER, 8, null);
                celda(fila.getCell(4), String.valueOf(o.faltantesAbiertos()),
                                                                           o.faltantesAbiertos() > 0,
                                                                           ParagraphAlignment.CENTER, 8, null);
                celda(fila.getCell(5), estadoLegible(o),                   false, ParagraphAlignment.CENTER, 7, null);

                totalActivos   += o.activos();
                totalFaltantes += o.faltantesAbiertos();
            }

            XWPFParagraph p = doc.createParagraph();
            p.setSpacingBefore(240);
            run(p, "Totales — Oficinas: " + oficinas.size()
                    + "   ·   Activos: " + totalActivos
                    + "   ·   Faltantes abiertos: " + totalFaltantes, true, 9);

            configurarPagina(doc);
            doc.write(salida);
            return salida.toByteArray();
        }
    }

    /** El estado de control en palabras, más el avance si hay un levantamiento en curso. */
    private String estadoLegible(TileOficinaDTO o) {
        String base = switch (o.estadoControl()) {
            case CONTROLADO    -> "Controlado";
            case EN_CURSO      -> "En curso";
            case CON_FALTANTES -> "Con faltantes";
            default            -> "Sin levantar";
        };
        return o.idLevantamientoEnCurso() != null
                ? base + " (" + o.porcentajeAvance() + "%)"
                : base;
    }

    private void encabezados(XWPFTableRow fila, String... titulos) {
        for (int i = 0; i < titulos.length; i++) {
            celda(fila.getCell(i), titulos[i], true, ParagraphAlignment.CENTER, 8, "D9D9D9");
        }
    }

    // ── Bloques del documento ────────────────────────────────────────────────

    private void titulo(XWPFDocument doc, String texto) {
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(ParagraphAlignment.CENTER);
        p.setSpacingAfter(120);
        run(p, texto, true, 14);
    }

    private void subtitulo(XWPFDocument doc, String texto) {
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(ParagraphAlignment.CENTER);
        p.setSpacingAfter(240);
        run(p, texto, false, 9);
    }

    /** Tabla principal: un bien por fila, con dónde está y en qué estado. */
    private void tablaDeBienes(XWPFDocument doc, List<ActivoUbicacionDTO> activos) {
        int[] anchos = { pct(4), pct(17), pct(31), pct(30), pct(10), pct(8) };
        XWPFTable tabla = doc.createTable(1, 6);
        configurarTabla(tabla, anchos);

        XWPFTableRow enc = tabla.getRow(0);
        celda(enc.getCell(0), "N°",          true, ParagraphAlignment.CENTER, 8, "D9D9D9");
        celda(enc.getCell(1), "CÓDIGO",      true, ParagraphAlignment.CENTER, 8, "D9D9D9");
        celda(enc.getCell(2), "DESCRIPCIÓN", true, ParagraphAlignment.CENTER, 8, "D9D9D9");
        celda(enc.getCell(3), "UBICACIÓN",   true, ParagraphAlignment.CENTER, 8, "D9D9D9");
        celda(enc.getCell(4), "ESTADO",      true, ParagraphAlignment.CENTER, 8, "D9D9D9");
        celda(enc.getCell(5), "CONTROL",     true, ParagraphAlignment.CENTER, 8, "D9D9D9");

        int n = 1;
        for (ActivoUbicacionDTO a : activos) {
            XWPFTableRow f = tabla.createRow();
            anchosFila(f, anchos);
            celda(f.getCell(0), String.valueOf(n++),        false, ParagraphAlignment.CENTER, 8, null);
            celda(f.getCell(1), texto(a.codigo()),          true,  ParagraphAlignment.LEFT,   8, null);
            celda(f.getCell(2), texto(a.descripcion()),     false, ParagraphAlignment.LEFT,   8, null);
            celda(f.getCell(3), a.ubicacion(),              false, ParagraphAlignment.LEFT,   7, null);
            celda(f.getCell(4), texto(a.estadoActivo()),    false, ParagraphAlignment.CENTER, 8, null);
            celda(f.getCell(5), control(a),                 false, ParagraphAlignment.CENTER, 8, null);
        }
    }

    /** Qué dice este informe sobre el control del bien: lo que el sistema sabe hoy. */
    private String control(ActivoUbicacionDTO a) {
        if (a.faltanteAbierto())  return "FALTANTE";
        if (a.observadoAbierto()) return "OBSERVADO";
        return "—";
    }

    private void totales(XWPFDocument doc, List<ActivoUbicacionDTO> activos) {
        long faltantes  = activos.stream().filter(ActivoUbicacionDTO::faltanteAbierto).count();
        long observados = activos.stream().filter(ActivoUbicacionDTO::observadoAbierto).count();
        BigDecimal costo = activos.stream()
                .map(a -> a.costo() == null ? BigDecimal.ZERO : BigDecimal.valueOf(a.costo()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        XWPFParagraph p = doc.createParagraph();
        p.setSpacingBefore(240);
        run(p, "Total de bienes: " + activos.size()
                + "   ·   Con faltante abierto: " + faltantes
                + "   ·   Con observación abierta: " + observados
                + "   ·   Costo total: Bs. " + costo.setScale(2, java.math.RoundingMode.HALF_UP),
                true, 9);
    }

    private void firmas(XWPFDocument doc) {
        XWPFParagraph esp = doc.createParagraph();
        esp.setSpacingBefore(700);

        XWPFTable t = doc.createTable(1, 2);
        int[] anchos = { pct(50), pct(50) };
        configurarTabla(t, anchos);
        quitarBordes(t);

        XWPFTableRow f = t.getRow(0);
        celda(f.getCell(0), "\n\n_______________________________\nResponsable de los bienes",
                false, ParagraphAlignment.CENTER, 9, null);
        celda(f.getCell(1), "\n\n_______________________________\nEncargado de Activos Fijos",
                false, ParagraphAlignment.CENTER, 9, null);
    }

    // ── Ayudantes de formato ─────────────────────────────────────────────────

    private String texto(String s) {
        return s == null ? "—" : s;
    }

    private XWPFRun run(XWPFParagraph p, String texto, boolean negrita, int size) {
        XWPFRun r = p.createRun();
        r.setFontFamily(FUENTE);
        r.setFontSize(size);
        r.setBold(negrita);
        r.setText(texto);
        return r;
    }

    /** Porcentaje del ancho útil a twips. */
    private int pct(int porcentaje) {
        return CONTENIDO_ANCHO * porcentaje / 100;
    }

    private void configurarTabla(XWPFTable tabla, int[] anchos) {
        CTTbl ctTbl = tabla.getCTTbl();
        CTTblPr pr = ctTbl.getTblPr() != null ? ctTbl.getTblPr() : ctTbl.addNewTblPr();

        if (pr.isSetTblLayout()) pr.getTblLayout().setType(STTblLayoutType.FIXED);
        else pr.addNewTblLayout().setType(STTblLayoutType.FIXED);

        CTTblWidth ancho = pr.isSetTblW() ? pr.getTblW() : pr.addNewTblW();
        ancho.setType(STTblWidth.DXA);
        ancho.setW(java.math.BigInteger.valueOf(CONTENIDO_ANCHO));

        // <w:tblGrid> es OBLIGATORIO en OOXML y POI no lo crea solo con createTable().
        // Word lo tolera si falta, pero otros lectores rechazan el archivo entero como
        // XML inválido — y sin grilla el ancho fijo de las columnas queda a criterio del
        // que abra el documento.
        CTTblGrid grid = ctTbl.getTblGrid() != null ? ctTbl.getTblGrid() : ctTbl.addNewTblGrid();
        while (grid.sizeOfGridColArray() > 0) grid.removeGridCol(0);
        for (int a : anchos) {
            grid.addNewGridCol().setW(java.math.BigInteger.valueOf(a));
        }

        anchosFila(tabla.getRow(0), anchos);
    }

    private void anchosFila(XWPFTableRow fila, int[] anchos) {
        for (int i = 0; i < anchos.length && i < fila.getTableCells().size(); i++) {
            XWPFTableCell c = fila.getCell(i);
            CTTblWidth w = c.getCTTc().addNewTcPr().addNewTcW();
            w.setType(STTblWidth.DXA);
            w.setW(java.math.BigInteger.valueOf(anchos[i]));
        }
    }

    /** Escribe una celda; soporta saltos de línea con \n. */
    private void celda(XWPFTableCell celda, String texto, boolean negrita,
                       ParagraphAlignment align, int size, String fondoHex) {
        if (fondoHex != null) celda.setColor(fondoHex);
        celda.removeParagraph(0);
        String[] lineas = (texto == null ? "—" : texto).split("\n", -1);
        for (String linea : lineas) {
            XWPFParagraph p = celda.addParagraph();
            p.setAlignment(align);
            p.setSpacingAfter(0);
            run(p, linea, negrita, size);
        }
    }

    private void quitarBordes(XWPFTable tabla) {
        CTTblPr pr = tabla.getCTTbl().getTblPr();
        if (pr == null) pr = tabla.getCTTbl().addNewTblPr();
        if (pr.isSetTblBorders()) pr.unsetTblBorders();
    }

    private void configurarPagina(XWPFDocument doc) {
        CTBody body = doc.getDocument().getBody();
        CTSectPr sect = body.isSetSectPr() ? body.getSectPr() : body.addNewSectPr();

        CTPageSz sz = sect.isSetPgSz() ? sect.getPgSz() : sect.addNewPgSz();
        sz.setW(java.math.BigInteger.valueOf(PAGINA_ANCHO));
        sz.setH(java.math.BigInteger.valueOf(PAGINA_ALTO));

        CTPageMar mar = sect.isSetPgMar() ? sect.getPgMar() : sect.addNewPgMar();
        mar.setTop(java.math.BigInteger.valueOf(MARGEN));
        mar.setBottom(java.math.BigInteger.valueOf(MARGEN));
        mar.setLeft(java.math.BigInteger.valueOf(MARGEN));
        mar.setRight(java.math.BigInteger.valueOf(MARGEN));
        mar.setHeader(java.math.BigInteger.ZERO);
    }

    /*
     * Sobre el membrete institucional: WordAsignacionActivoService lo pone como imagen
     * a página completa DETRÁS del texto, y para eso convierte la imagen "inline" en un
     * anclaje manipulando el XML del dibujo (unas 60 líneas de API tipada de OOXML).
     * Este informe sale por ahora sin ese fondo, a propósito: replicar ese bloque acá
     * sería una segunda copia de algo delicado que ya funciona en producción, y sobre
     * una tabla densa el fondo además resta legibilidad. Cuando toque el "acta por
     * responsable" —que sí es un documento formal para firmar— lo correcto es extraer
     * ese helper a un lugar compartido y que los tres servicios usen una sola
     * implementación probada, en vez de tener tres.
     */
}
