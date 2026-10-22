package com.usic.SistemasActivosFijosUAP.model.service.documento;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;

import javax.imageio.ImageIO;

import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.qrcode.QRCodeWriter;

/** Piezas compartidas por las actas, notificaciones y etiquetas Word. */
public final class WordDocumentos {
    public static final String MIME = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    private WordDocumentos() {}

    public static void pagina(XWPFDocument doc, boolean carta, int margenSuperior, int margenInferior) {
        CTSectPr s = doc.getDocument().getBody().isSetSectPr()
                ? doc.getDocument().getBody().getSectPr() : doc.getDocument().getBody().addNewSectPr();
        CTPageSz tam = s.isSetPgSz() ? s.getPgSz() : s.addNewPgSz();
        tam.setW(BigInteger.valueOf(carta ? 12240 : 11906));
        tam.setH(BigInteger.valueOf(carta ? 15840 : 16838));
        CTPageMar m = s.isSetPgMar() ? s.getPgMar() : s.addNewPgMar();
        m.setLeft(BigInteger.valueOf(carta ? 1050 : 360));
        m.setRight(BigInteger.valueOf(carta ? 1050 : 360));
        m.setTop(BigInteger.valueOf(margenSuperior));
        m.setBottom(BigInteger.valueOf(margenInferior));
    }

    public static XWPFParagraph parrafo(XWPFDocument doc, String texto, int puntos, boolean negrita,
                                         ParagraphAlignment alineacion) {
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(alineacion);
        p.setSpacingAfter(110);
        XWPFRun r = p.createRun();
        r.setFontFamily("Arial");
        r.setFontSize(puntos);
        r.setBold(negrita);
        texto(r, texto);
        return p;
    }

    public static void celda(XWPFTableCell c, String texto, boolean negrita, int puntos) {
        c.removeParagraph(0);
        XWPFParagraph p = c.addParagraph();
        p.setSpacingAfter(0);
        XWPFRun r = p.createRun();
        r.setFontFamily("Arial");
        r.setFontSize(puntos);
        r.setBold(negrita);
        texto(r, texto);
    }

    public static XWPFTable tabla(XWPFDocument doc, String... cabeceras) {
        XWPFTable t = doc.createTable(1, cabeceras.length);
        t.setWidth("100%");
        XWPFTableRow fila = t.getRow(0);
        for (int i = 0; i < cabeceras.length; i++) {
            celda(fila.getCell(i), cabeceras[i], true, 8);
            fila.getCell(i).setColor("E9EDF2");
        }
        return t;
    }

    private static void texto(XWPFRun run, String valor) {
        String[] lineas = (valor == null ? "" : valor).split("\\n", -1);
        for (int i = 0; i < lineas.length; i++) {
            if (i > 0) run.addBreak();
            run.setText(lineas[i]);
        }
    }

    public static void fila(XWPFTable t, String... valores) {
        XWPFTableRow r = t.createRow();
        for (int i = 0; i < valores.length; i++) celda(r.getCell(i), valores[i], false, 8);
    }

    public static byte[] bytes(XWPFDocument doc) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        doc.write(out);
        return out.toByteArray();
    }

    public static byte[] qr(String contenido, int pixeles) throws Exception {
        var matriz = new QRCodeWriter().encode(contenido, BarcodeFormat.QR_CODE, pixeles, pixeles);
        BufferedImage imagen = new BufferedImage(pixeles, pixeles, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < pixeles; y++) for (int x = 0; x < pixeles; x++)
            imagen.setRGB(x, y, matriz.get(x, y) ? Color.BLACK.getRGB() : Color.WHITE.getRGB());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(imagen, "png", out);
        return out.toByteArray();
    }

    public static void imagenQr(XWPFRun run, String contenido, int puntos) throws Exception {
        byte[] png = qr(contenido, Math.max(160, puntos * 3));
        run.addPicture(new java.io.ByteArrayInputStream(png), XWPFDocument.PICTURE_TYPE_PNG,
                "verificacion.png", Units.toEMU(puntos), Units.toEMU(puntos));
    }
}
