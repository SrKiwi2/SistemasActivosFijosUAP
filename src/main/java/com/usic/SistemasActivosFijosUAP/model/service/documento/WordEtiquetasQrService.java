package com.usic.SistemasActivosFijosUAP.model.service.documento;

import java.util.List;

import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.springframework.stereotype.Service;

import com.usic.SistemasActivosFijosUAP.model.service.PdfEtiquetasQrService;

/** Hoja A4 editable de etiquetas QR: tres columnas y ocho filas por página. */
@Service
public class WordEtiquetasQrService {
    public byte[] generar(List<PdfEtiquetasQrService.Etiqueta> etiquetas, String aviso) {
        try (XWPFDocument doc = new XWPFDocument()) {
            WordDocumentos.pagina(doc, false, 350, 350);
            int indice = 0;
            while (indice < etiquetas.size()) {
                if (indice > 0) doc.createParagraph().setPageBreak(true);
                if (indice == 0 && aviso != null && !aviso.isBlank())
                    WordDocumentos.parrafo(doc, aviso, 8, true, ParagraphAlignment.LEFT);
                XWPFTable hoja = doc.createTable(8, 3);
                hoja.setWidth("100%");
                for (int f = 0; f < 8; f++) {
                    hoja.getRow(f).setHeight(1850);
                    for (int c = 0; c < 3; c++) {
                        XWPFTableCell celda = hoja.getRow(f).getCell(c);
                        if (indice < etiquetas.size()) etiqueta(celda, etiquetas.get(indice++));
                    }
                }
            }
            return WordDocumentos.bytes(doc);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo generar la hoja Word de etiquetas.", e);
        }
    }

    private static void etiqueta(XWPFTableCell celda, PdfEtiquetasQrService.Etiqueta e) throws Exception {
        celda.removeParagraph(0);
        XWPFParagraph qr = celda.addParagraph();
        qr.setAlignment(ParagraphAlignment.CENTER);
        qr.setSpacingAfter(0);
        WordDocumentos.imagenQr(qr.createRun(), e.qr(), 52);
        linea(celda, e.cabecera(), 6, true);
        linea(celda, e.codigo(), 9, true);
        linea(celda, e.descripcion(), 6, false);
        if (e.pie() != null && !e.pie().isBlank()) linea(celda, e.pie(), 6, false);
    }

    private static void linea(XWPFTableCell celda, String texto, int puntos, boolean negrita) {
        XWPFParagraph p = celda.addParagraph();
        p.setAlignment(ParagraphAlignment.CENTER);
        p.setSpacingAfter(0);
        XWPFRun r = p.createRun();
        r.setFontFamily("Arial");
        r.setFontSize(puntos);
        r.setBold(negrita);
        r.setText(texto == null ? "" : texto);
    }
}
