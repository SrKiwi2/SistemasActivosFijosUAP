package com.usic.SistemasActivosFijosUAP.model.service.donacion;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.springframework.stereotype.Service;

import com.usic.SistemasActivosFijosUAP.model.entity.Activo;
import com.usic.SistemasActivosFijosUAP.model.service.documento.MembreteWord;
import com.usic.SistemasActivosFijosUAP.model.service.documento.WordDocumentos;

import lombok.RequiredArgsConstructor;

/** Acta editable de recepción de bienes en donación, con los datos guardados del acta. */
@Service
@RequiredArgsConstructor
public class WordActaDonacionService {
    private final DonacionService donaciones;

    public byte[] generar(Long id) {
        Map<String, Object> d = donaciones.detalle(id);
        if ("ANULADA".equals(d.get("estado"))) throw new IllegalArgumentException("La donación está anulada.");
        try (XWPFDocument doc = new XWPFDocument()) {
            WordDocumentos.pagina(doc, true, 1960, 1080);
            MembreteWord.aplicar(doc);
            WordDocumentos.parrafo(doc, "ACTA DE RECEPCIÓN DE BIENES EN DONACIÓN", 14, true, ParagraphAlignment.CENTER);
            WordDocumentos.parrafo(doc, s(d.get("numero")) + " · Universidad Amazónica de Pando", 10, false, ParagraphAlignment.CENTER);
            XWPFTable datos = WordDocumentos.tabla(doc, "Dato", "Detalle");
            WordDocumentos.fila(datos, "Donante", s(d.get("donante")));
            WordDocumentos.fila(datos, "C.I. / NIT", s(d.get("donanteDocumento")));
            WordDocumentos.fila(datos, "Fecha de donación", fecha(d.get("fecha")));
            WordDocumentos.fila(datos, "Documento de respaldo", s(d.get("documentoRespaldo")));
            WordDocumentos.fila(datos, "Oficina", s(d.get("oficina")));
            WordDocumentos.fila(datos, "Responsable", s(d.get("responsable")));
            WordDocumentos.parrafo(doc, "Bienes recibidos", 11, true, ParagraphAlignment.LEFT);
            XWPFTable bienes = WordDocumentos.tabla(doc, "#", "Código", "Descripción", "Estado", "Valor ref.");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> filas = (List<Map<String, Object>>) d.get("bienes");
            for (Map<String, Object> b : filas) {
                String descripcion = s(b.get("descripcion"));
                if (b.get("caracteristicas") != null) descripcion += "\n" + b.get("caracteristicas");
                WordDocumentos.fila(bienes, s(b.get("item")), Activo.codigoDocumento(s(b.get("codigo"))),
                        descripcion, s(b.get("conservacion")), moneda(b.get("valor")));
            }
            WordDocumentos.fila(bienes, "", "", "", "TOTAL (" + s(d.get("cantidad")) + " bienes)", moneda(d.get("valor")));
            if (d.get("observacion") != null) WordDocumentos.parrafo(doc,
                    "Observación: " + d.get("observacion"), 10, false, ParagraphAlignment.LEFT);
            WordDocumentos.parrafo(doc, "\n\n________________________                 ________________________", 10, false, ParagraphAlignment.CENTER);
            WordDocumentos.parrafo(doc, "ENTREGUÉ CONFORME                 RECIBÍ CONFORME", 9, true, ParagraphAlignment.CENTER);
            WordDocumentos.parrafo(doc, s(d.get("donante")) + "                 "
                    + s(d.get("responsable")).replaceFirst("^.*— ", ""), 9, false, ParagraphAlignment.CENTER);
            return WordDocumentos.bytes(doc);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo generar el acta Word de donación.", e);
        }
    }

    private static String s(Object valor) { return valor == null || String.valueOf(valor).isBlank() ? "—" : String.valueOf(valor); }
    private static String fecha(Object valor) {
        String t = s(valor);
        return t.matches("\\d{4}-\\d{2}-\\d{2}") ? t.substring(8) + "/" + t.substring(5, 7) + "/" + t.substring(0, 4) : t;
    }
    private static String moneda(Object valor) {
        return "Bs " + String.format(Locale.forLanguageTag("es-BO"), "%,.2f", valor instanceof Number n ? n.doubleValue() : 0);
    }
}
