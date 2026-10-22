package com.usic.SistemasActivosFijosUAP.model.service.control;

import java.math.BigInteger;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import org.apache.poi.wp.usermodel.HeaderFooterType;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFFooter;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.usic.SistemasActivosFijosUAP.model.dto.control.ActaFaltanteDTO;
import com.usic.SistemasActivosFijosUAP.model.entity.ActaFaltante;
import com.usic.SistemasActivosFijosUAP.model.entity.Activo;
import com.usic.SistemasActivosFijosUAP.model.service.documento.MembreteWord;
import com.usic.SistemasActivosFijosUAP.model.service.documento.WordDocumentos;

/** Actas y notificaciones de faltantes editables, a partir de la foto guardada al emitir. */
@Service
public class WordActaFaltanteService {
    private static final String UAP = "UNIVERSIDAD AMAZÓNICA DE PANDO";
    private static final Locale ES = Locale.forLanguageTag("es-BO");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter FECHA_LARGA = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", ES);

    @Value("${sciaf.url.publica:https://sciaf.uap.edu.bo}")
    private String urlPublica;

    public byte[] generar(ActaFaltanteDTO acta, boolean adjuntos) throws Exception {
        return crear(acta, false, adjuntos);
    }

    public byte[] generarVistaPrevia(ActaFaltanteDTO acta) throws Exception {
        if (!acta.esNotificacion()) throw new IllegalArgumentException("La vista previa es solo para notificaciones.");
        return crear(acta, true, false);
    }

    private byte[] crear(ActaFaltanteDTO acta, boolean previa, boolean adjuntos) throws Exception {
        try (XWPFDocument doc = new XWPFDocument()) {
            WordDocumentos.pagina(doc, true, 1950, 3000);
            MembreteWord.aplicar(doc);
            pieVerificacion(doc, acta, previa);
            if (acta.esNotificacion()) notificacion(doc, acta, previa, adjuntos);
            else actaHistorica(doc, acta);
            return WordDocumentos.bytes(doc);
        }
    }

    private void notificacion(XWPFDocument doc, ActaFaltanteDTO a, boolean previa, boolean adjuntos) {
        String fecha = a.fechaDelDocumento() == null ? "" : a.fechaDelDocumento().format(FECHA_LARGA);
        linea(doc, valor(a.ciudad(), "Cobija") + ", " + fecha, true, ParagraphAlignment.RIGHT);
        linea(doc, previa ? "NOT:SCIAF:AF N° ___/____" : a.numeroImpreso(), true, ParagraphAlignment.RIGHT);
        if (previa) linea(doc, "VISTA PREVIA — no registrada, sin número ni validez", true, ParagraphAlignment.CENTER);
        if (ActaFaltante.ANULADA.equals(a.estado())) {
            linea(doc, "NOTIFICACIÓN ANULADA" + (a.fechaAnulacion() == null ? "" : " el " + a.fechaAnulacion().format(FECHA_HORA))
                    + (a.motivoAnulacion() == null ? "" : " — " + a.motivoAnulacion()), true, ParagraphAlignment.CENTER);
        }
        linea(doc, "Señor(a)\n" + valor(a.personaNombre(), "").toUpperCase(ES), false, ParagraphAlignment.LEFT);
        if (a.personaCargo() != null) linea(doc, a.personaCargo().toUpperCase(ES), true, ParagraphAlignment.LEFT);
        if (a.unidad() != null) linea(doc, a.unidad().toUpperCase(ES), true, ParagraphAlignment.LEFT);
        linea(doc, UAP + "\nPresente.-", true, ParagraphAlignment.LEFT);
        linea(doc, "REF.: " + a.titulo(), true, ParagraphAlignment.CENTER);
        linea(doc, "De mi consideración:", true, ParagraphAlignment.LEFT);

        if (a.esReiterativa()) {
            String reitera = a.esUnion()
                    ? "En atención a las notificaciones " + enLista(a.unificaA())
                    : "En atención a la " + valor(a.reiteraA(), "notificación anterior")
                        + (a.reiteraAFecha() == null ? "" : " de fecha " + a.reiteraAFecha().format(FECHA_LARGA));
            parrafo(doc, reitera + ", y considerando que a la fecha no se regularizó la situación de los bienes detallados a continuación, "
                    + "mediante la presente se le " + (a.esUnion() ? "reiteran dichas notificaciones" : "reitera dicha notificación")
                    + (Integer.valueOf(2).equals(a.numeroReiterativa()) ? " con carácter de segunda y última notificación." : "."));
        }
        parrafo(doc, "Mediante la presente, la Sección de Activos Fijos de la Universidad Amazónica de Pando, en el marco de las actividades de control, verificación y actualización de los registros de activos fijos, pone en su conocimiento los resultados de la verificación física realizada a los bienes registrados bajo su responsabilidad.");
        parrafo(doc, "Como resultado del cotejo entre los registros institucionales y la verificación física, se identificaron activos fijos que no fueron encontrados físicamente, detallados a continuación:");
        linea(doc, "DETALLE DE ACTIVOS FÍSICOS FALTANTES", true, ParagraphAlignment.LEFT);
        int total = 0;
        boolean variosPredios = a.predios().size() > 1;
        for (ActaFaltanteDTO.Predio p : a.predios()) {
            for (ActaFaltanteDTO.Oficina o : p.oficinas()) {
                linea(doc, "Oficina " + (o.codOfi() == null ? "" : o.codOfi() + " — ")
                        + valor(o.nombre(), ActaFaltanteService.ORIGEN_NO_REGISTRADO)
                        + (variosPredios ? " (" + valor(p.nombre(), p.unidad()) + ")" : ""), true, ParagraphAlignment.LEFT);
                XWPFTable tabla = WordDocumentos.tabla(doc, "N.º", "Código", "Descripción", "Identificación y ubicación");
                int n = 0;
                for (ActaFaltanteDTO.Bien b : o.bienes()) {
                    WordDocumentos.fila(tabla, String.format("%02d", ++n), valor(Activo.codigoDocumento(b.codigo()), "—"),
                            valor(b.descripcionCorta(), valor(b.descripcion(), "—")),
                            "Marca: " + valor(b.marca(), "—") + "\nModelo: " + valor(b.modelo(), "—")
                            + "\nSerie: " + valor(b.serie(), "—") + "\nUbicación: " + valor(o.nombre(), "—"));
                }
                total += n;
            }
        }
        linea(doc, "TOTAL DE ACTIVOS FALTANTES: " + total, true, ParagraphAlignment.LEFT);
        parrafo(doc, "De acuerdo con los registros del Sistema de Información de Activos Fijos (vSIAF) y el levantamiento físico realizado, los bienes detallados se encuentran registrados bajo su responsabilidad y no fueron ubicados físicamente en las dependencias registradas.");
        parrafo(doc, "Se solicita a usted informar a la Sección de Activos Fijos sobre la situación y paradero de cada bien, con la información o documentación que permita establecer su ubicación actual y las circunstancias de su ausencia.");
        parrafo(doc, "La información deberá presentarse dentro del plazo de " + PdfActaFaltanteService.plazoEnTexto(a.plazoDias())
                + (Integer.valueOf(1).equals(a.plazoDias()) ? " día hábil" : " días hábiles")
                + ", computables a partir de la recepción de la presente notificación.");
        parrafo(doc, "En caso de incumplimiento del plazo o de no proporcionarse información que esclarezca la situación, la Sección de Activos Fijos remitirá los antecedentes a las instancias competentes de la Universidad Amazónica de Pando para que determinen las acciones administrativas correspondientes, incluida cuando proceda la Unidad de Asesoría Jurídica, conforme al régimen de Responsabilidad por la Función Pública.");
        parrafo(doc, "La notificación y los antecedentes quedarán registrados en el Sistema de Control Interno de Activos Fijos (SCIAF) para control y seguimiento.");
        parrafo(doc, "Sin otro particular, saludo a usted con las consideraciones más distinguidas.");
        linea(doc, "Atentamente,\n\n________________________________________\n"
                + valor(a.firmante(), " ").toUpperCase(ES) + "\nRESPONSABLE DE ACTIVOS FIJOS\n" + UAP,
                true, ParagraphAlignment.CENTER);
        linea(doc, "C.c.: Archivo – Sección de Activos Fijos"
                + (adjuntos ? "\nSe adjunta documentación de respaldo" : "")
                + "\nDocumento generado por: Sistema de Control Interno de Activos Fijos – SCIAF"
                + "\nCódigo de verificación: " + (previa ? "— (vista previa: sin validez)" : valor(a.huella(), "—")),
                false, ParagraphAlignment.LEFT);
    }

    private void actaHistorica(XWPFDocument doc, ActaFaltanteDTO a) {
        linea(doc, "SECCIÓN DE ACTIVOS FIJOS", true, ParagraphAlignment.CENTER);
        linea(doc, a.titulo(), true, ParagraphAlignment.CENTER);
        linea(doc, "N° " + a.numero(), true, ParagraphAlignment.CENTER);
        if (ActaFaltante.ANULADA.equals(a.estado())) linea(doc, "ACTA ANULADA"
                + (a.fechaAnulacion() == null ? "" : " el " + a.fechaAnulacion().format(FECHA_HORA))
                + (a.motivoAnulacion() == null ? "" : " — " + a.motivoAnulacion()), true, ParagraphAlignment.CENTER);
        XWPFTable datos = WordDocumentos.tabla(doc, "Dato", "Información");
        WordDocumentos.fila(datos, "Responsable", valor(a.personaNombre(), "—") + " · C.I. " + valor(a.personaCi(), "—"));
        WordDocumentos.fila(datos, "Cargo", valor(a.personaCargo(), "—"));
        WordDocumentos.fila(datos, "Fecha de emisión", a.fechaEmision() == null ? "—" : a.fechaEmision().format(FECHA_HORA));
        WordDocumentos.fila(datos, "Documento de respaldo", valor(a.documentoRespaldo(), "—")
                + (a.fechaDocumento() == null ? "" : " (" + a.fechaDocumento().format(FECHA) + ")"));
        WordDocumentos.fila(datos, "Registrado por", valor(a.usuarioEmision(), "—"));
        if (a.observacion() != null) WordDocumentos.fila(datos, "Observación", a.observacion());
        String intro = a.esRegularizacion() && a.conTraslado()
                ? "Se regularizan en el SCIAF los bienes que figuraban como faltantes en un registro repetido de la misma oficina. Quedan como FALTANTES y se trasladan a la oficina de faltantes de su predio hasta que el caso se aclare."
                : a.esRegularizacion()
                ? "Se regularizan en el SCIAF los bienes registrados como FALTANTES en la oficina de faltantes de su predio. Permanecen allí hasta que el caso se aclare; si la oficina de origen no consta en el historial, figura como No registrada."
                : "Se deja constancia de que los bienes detallados no fueron encontrados en su ubicación registrada. Quedan como FALTANTES y se trasladan a la oficina de faltantes de su predio hasta que el caso se aclare.";
        parrafo(doc, intro + " Responsable: " + valor(a.personaNombre(), "—") + ".");
        XWPFTable bienes = WordDocumentos.tabla(doc, "N.º", "Código", "Descripción / ubicación de origen");
        int n = 0;
        for (ActaFaltanteDTO.Predio p : a.predios()) for (ActaFaltanteDTO.Oficina o : p.oficinas()) {
            for (ActaFaltanteDTO.Bien b : o.bienes()) {
                WordDocumentos.fila(bienes, String.valueOf(++n), valor(Activo.codigoDocumento(b.codigo()), "—"),
                        valor(b.descripcion(), "—") + "\nPredio: " + valor(p.unidad(), "—") + " — " + valor(p.nombre(), "—")
                        + "\nOficina: " + (o.codOfi() == null ? "" : o.codOfi() + " — ")
                        + valor(o.nombre(), ActaFaltanteService.ORIGEN_NO_REGISTRADO));
            }
        }
        linea(doc, "TOTAL DE BIENES FALTANTES: " + n, true, ParagraphAlignment.RIGHT);
        XWPFTable firmas = WordDocumentos.tabla(doc, "Firma del responsable", "Sección de Activos Fijos");
        WordDocumentos.fila(firmas, "\n\n________________________\n" + valor(a.personaNombre(), "—")
                + "\n" + valor(a.personaCargo(), "") + "\nC.I.: " + valor(a.personaCi(), "—"),
                "\n\n________________________\nSECCIÓN DE ACTIVOS FIJOS\nFirma y sello");
    }

    private void pieVerificacion(XWPFDocument doc, ActaFaltanteDTO a, boolean previa) throws Exception {
        CTSectPr sect = doc.getDocument().getBody().isSetSectPr()
                ? doc.getDocument().getBody().getSectPr() : doc.getDocument().getBody().addNewSectPr();
        CTPageMar mar = sect.isSetPgMar() ? sect.getPgMar() : sect.addNewPgMar();
        mar.setFooter(BigInteger.valueOf(2000));
        XWPFFooter pie = doc.createFooter(HeaderFooterType.DEFAULT);
        XWPFTable bloque = pie.createTable(1, 2);
        bloque.setWidth("100%");
        bloque.removeBorders();
        bloque.setCellMargins(0, 0, 0, 0);
        XWPFTableCell celdaQr = bloque.getRow(0).getCell(0);
        XWPFTableCell celdaTexto = bloque.getRow(0).getCell(1);
        celdaQr.setWidth("1050");
        celdaTexto.setWidth("9090");
        celdaTexto.setVerticalAlignment(XWPFTableCell.XWPFVertAlign.CENTER);
        XWPFParagraph qr = celdaQr.getParagraphs().get(0);
        XWPFParagraph p = celdaTexto.getParagraphs().get(0);
        qr.setSpacingBefore(0);
        qr.setSpacingAfter(0);
        p.setSpacingBefore(0);
        p.setSpacingAfter(0);
        String url = !previa && a.token() != null ? urlPublica.replaceAll("/$", "") + "/verificar/acta-faltantes/" + a.token() : null;
        if (url != null) WordDocumentos.imagenQr(qr.createRun(), url, 38);
        var texto = p.createRun();
        texto.setFontFamily("Arial");
        texto.setFontSize(7);
        texto.setText(previa ? "VISTA PREVIA — SIN VALIDEZ" : "SCIAF · " + a.numeroImpreso() + " · Huella " + valor(a.huella(), "—"));
        if (url != null) { texto.addBreak(); texto.setText(url); }
    }

    private static String enLista(List<String> textos) {
        if (textos == null || textos.isEmpty()) return "notificaciones anteriores";
        return textos.size() == 1 ? textos.get(0) : String.join(", ", textos.subList(0, textos.size() - 1)) + " y " + textos.get(textos.size() - 1);
    }
    private static String valor(String s, String defecto) { return s == null || s.isBlank() ? defecto : s; }
    private static void linea(XWPFDocument doc, String texto, boolean negrita, ParagraphAlignment alineacion) {
        WordDocumentos.parrafo(doc, texto, 10, negrita, alineacion);
    }
    private static void parrafo(XWPFDocument doc, String texto) {
        WordDocumentos.parrafo(doc, texto, 10, false, ParagraphAlignment.BOTH);
    }
}
