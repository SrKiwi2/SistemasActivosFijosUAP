package com.usic.SistemasActivosFijosUAP.model.service.duplicados;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.usic.SistemasActivosFijosUAP.model.dto.duplicados.DuplicadosDTOs;
import com.usic.SistemasActivosFijosUAP.model.entity.Activo;
import com.usic.SistemasActivosFijosUAP.model.entity.NotificacionDuplicado;
import com.usic.SistemasActivosFijosUAP.model.service.documento.MembreteWord;
import com.usic.SistemasActivosFijosUAP.model.service.documento.WordDocumentos;

/** Notificación editable del registro duplicado, con QR y huella de verificación. */
@Service
public class WordNotificacionDuplicadoService {
    private static final String UAP = "UNIVERSIDAD AMAZÓNICA DE PANDO";
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es-BO"));

    @Value("${sciaf.url.publica:https://sciaf.uap.edu.bo}")
    private String urlPublica;

    public byte[] generar(DuplicadosDTOs.Notificacion n, boolean vistaPrevia) {
        var c = n.c();
        LocalDateTime fecha = null;
        try { fecha = LocalDateTime.parse(c.fecha()); } catch (Exception ignored) { }
        String codigoSobra = codigo(c.sobrante());
        String codigoQueda = codigo(c.principal());
        String url = !vistaPrevia && n.token() != null
                ? urlPublica.replaceAll("/$", "") + "/verificar/duplicado/" + n.token() : null;
        try (XWPFDocument doc = new XWPFDocument()) {
            WordDocumentos.pagina(doc, true, 1960, 1640);
            MembreteWord.aplicar(doc);
            WordDocumentos.parrafo(doc, UAP + "\nDIRECCIÓN ADMINISTRATIVA FINANCIERA\nSECCIÓN DE ACTIVOS FIJOS", 10, true, ParagraphAlignment.CENTER);
            WordDocumentos.parrafo(doc, vistaPrevia ? "NOT:SCIAF:AD N° ___/____" : n.numeroImpreso(), 10, true, ParagraphAlignment.RIGHT);
            WordDocumentos.parrafo(doc, texto(c.ciudad(), "Cobija") + ", " + (fecha == null ? "" : fecha.format(FECHA)), 10, true, ParagraphAlignment.RIGHT);
            if (vistaPrevia) WordDocumentos.parrafo(doc, "VISTA PREVIA — no registrada, sin número ni validez", 10, true, ParagraphAlignment.CENTER);
            if (NotificacionDuplicado.ANULADA.equals(n.estado())) WordDocumentos.parrafo(doc,
                    "NOTIFICACIÓN ANULADA" + (n.fechaAnulacion() == null ? "" : " el " + n.fechaAnulacion().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")))
                    + (n.motivoAnulacion() == null ? "" : " — " + n.motivoAnulacion()), 10, true, ParagraphAlignment.CENTER);
            WordDocumentos.parrafo(doc, "Señor(a):\n" + texto(c.destinatario(), "").toUpperCase() + "\n"
                    + texto(c.cargo(), "") + "\n" + texto(c.unidad(), "") + "\n" + UAP + "\nPresente.-", 10, true, ParagraphAlignment.LEFT);
            WordDocumentos.parrafo(doc, "REF.: NOTIFICACIÓN DE ACTIVO REGISTRADO COMO DUPLICADO", 10, true, ParagraphAlignment.CENTER);
            WordDocumentos.parrafo(doc, "De mi consideración:", 10, true, ParagraphAlignment.LEFT);
            parrafo(doc, "Mediante la presente, la Sección de Activos Fijos de la Universidad Amazónica de Pando, en el marco de las actividades de control, verificación y actualización de los registros de activos fijos, pone en su conocimiento que el activo con código "
                    + codigoSobra + ", registrado bajo su responsabilidad, fue identificado como un REGISTRO DUPLICADO: corresponde al mismo bien físico que el activo con código "
                    + codigoQueda + ", registrado dos veces en el Sistema de Información de Activos Fijos (vSIAF) como si se tratara de dos bienes distintos.");
            WordDocumentos.parrafo(doc, "DETALLE DE LOS REGISTROS", 10, true, ParagraphAlignment.LEFT);
            XWPFTable tabla = WordDocumentos.tabla(doc, "Registro", "Código", "Descripción", "Marca / modelo", "N.º serie", "Ubicación");
            fila(tabla, "DUPLICADO", c.sobrante());
            fila(tabla, "SE CONSERVA", c.principal());
            parrafo(doc, "El registro fue dispuesto como duplicado por " + texto(c.registradoPor(), "—")
                    + (fecha == null ? "" : " en fecha " + fecha.format(FECHA))
                    + ", por el siguiente motivo: " + texto(c.motivo(), "—"));
            parrafo(doc, "En consecuencia, el registro " + codigoSobra + " no cuenta con un bien físico propio y se considera FALTANTE; por ello se registra en la ubicación de DUPLICADOS de su predio"
                    + (c.oficinaDuplicados() == null ? "" : " (" + c.oficinaDuplicados() + ")")
                    + ", a su nombre, hasta que el nuevo revalúo regularice su situación. Esta medida no implica la corrección ni la baja del registro. El activo "
                    + codigoQueda + " se mantiene sin cambios.");
            if (c.faltanteNumero() != null) parrafo(doc, "El activo " + codigoSobra + " había sido notificado como faltante mediante la "
                    + c.faltanteNumero() + ". Con la presente se aclara que su ausencia física se debe a un registro duplicado; queda sin efecto el requerimiento de informar sobre su paradero respecto de este bien.");
            parrafo(doc, "La presente comunicación queda registrada en el Sistema de Control Interno de Activos Fijos (SCIAF) para fines de control y seguimiento.");
            parrafo(doc, "Sin otro particular, saludo a usted con las consideraciones más distinguidas.");
            WordDocumentos.parrafo(doc, "Atentamente,\n\n_______________________________\n"
                    + texto(c.firmante(), " ").toUpperCase() + "\nRESPONSABLE DE ACTIVOS FIJOS\n" + UAP, 10, true, ParagraphAlignment.CENTER);
            WordDocumentos.parrafo(doc, "C.c.: Archivo – Sección de Activos Fijos\nDocumento generado por: Sistema de Control Interno de Activos Fijos – SCIAF\nCódigo de verificación: "
                    + (vistaPrevia ? "— (vista previa: sin validez)" : texto(n.huella(), "—")), 8, false, ParagraphAlignment.LEFT);
            if (url != null) {
                XWPFParagraph p = doc.createParagraph();
                WordDocumentos.imagenQr(p.createRun(), url, 55);
                WordDocumentos.parrafo(doc, "Verifique su autenticidad: " + url + "\nHuella del contenido: " + texto(n.huella(), "—"), 8, false, ParagraphAlignment.LEFT);
            }
            return WordDocumentos.bytes(doc);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo generar la notificación Word de duplicado.", e);
        }
    }

    private static void fila(XWPFTable t, String nombre, DuplicadosDTOs.BienDoc b) {
        WordDocumentos.fila(t, nombre, codigo(b), texto(b.descripcionCorta(), texto(b.descripcion(), "—")),
                texto(b.marca(), "—") + " / " + texto(b.modelo(), "—"), texto(b.serie(), "—"),
                texto(b.oficina(), "—") + (b.responsable() == null ? "" : "\n" + b.responsable()));
    }
    private static String codigo(DuplicadosDTOs.BienDoc b) { return texto(Activo.codigoDocumento(b.codigo()), "—"); }
    private static String texto(String valor, String defecto) { return valor == null || valor.isBlank() ? defecto : valor; }
    private static void parrafo(XWPFDocument doc, String texto) {
        WordDocumentos.parrafo(doc, texto, 10, false, ParagraphAlignment.BOTH);
    }
}
