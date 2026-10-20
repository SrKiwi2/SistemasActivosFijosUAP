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
    /** Prefijo de entidad que el acta de asignación del VSIAF antepone al código. */
    private static final String PREFIJO_CODIGO = "148-";

    private static final Font TITULO = new Font(Font.FontFamily.TIMES_ROMAN, 14, Font.BOLD | Font.UNDERLINE);
    private static final Font NORMAL = new Font(Font.FontFamily.HELVETICA, 10.5f);
    private static final Font NEGRITA = new Font(Font.FontFamily.HELVETICA, 10.5f, Font.BOLD);
    private static final Font CAB_TABLA = new Font(Font.FontFamily.HELVETICA, 10, Font.BOLD);
    private static final Font TABLA = new Font(Font.FontFamily.HELVETICA, 9);
    private static final Font TABLA_NOTA = new Font(Font.FontFamily.HELVETICA, 7.5f, Font.ITALIC, new BaseColor(70, 70, 70));
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
        intro.add(new Chunk(" de los Activos Fijos que se encuentran físicamente en la ", NORMAL));
        intro.add(new Chunk(ubicacion(oficina), NEGRITA));
        intro.add(new Chunk(" que no fueron inventariados ni codificados por la empresa que realizó el Revalúo, "
                + "mencionando así los activos con las siguientes características:", NORMAL));
        doc.add(intro);

        PdfPTable tabla = new PdfPTable(new float[] { 1f, 4.6f, 2.5f, 1.9f });
        tabla.setWidthPercentage(100);
        tabla.setHeaderRows(1);
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

        Paragraph al = new Paragraph("Al:", NORMAL);
        al.setSpacingAfter(10);
        doc.add(al);
        doc.add(recuadroResponsable(responsable));

        Paragraph legal = new Paragraph();
        legal.setAlignment(Element.ALIGN_JUSTIFIED);
        legal.setLeading(14.5f);
        legal.setSpacingBefore(16);
        legal.add(new Chunk("Entrega que es realizada de acuerdo al reglamentos y Normas Básicas del SABS y Decreto "
                + "Supremo 0181, que a la letra dice, ", NORMAL));
        legal.add(new Chunk("es de responsabilidad total de la persona y/o funcionario, que recibe el activo, por "
                + "cualquier perdida, deterioro y/o por mal uso de los Bienes de la institución.", NEGRITA));
        doc.add(legal);
        Paragraph constancia = new Paragraph("Para constancia de la recepción firmamos al pie del presente documento.", NORMAL);
        constancia.setLeading(14.5f);
        doc.add(constancia);

        doc.add(firmas(iniciales));
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
        c.setHorizontalAlignment(Element.ALIGN_CENTER);
        c.setVerticalAlignment(Element.ALIGN_MIDDLE);
        c.setPaddingTop(5);
        c.setPaddingBottom(7);
        return c;
    }

    private static PdfPCell celda(Phrase valor, int alineacion) {
        PdfPCell c = new PdfPCell(valor);
        c.setHorizontalAlignment(alineacion);
        c.setVerticalAlignment(Element.ALIGN_MIDDLE);
        c.setPaddingTop(3);
        c.setPaddingBottom(5);
        c.setPaddingLeft(5);
        c.setPaddingRight(5);
        return c;
    }

    /**
     * Código como en el acta de asignación del VSIAF ("148-" + código sin la letra de la
     * serie). El código antiguo va debajo; sin ninguno de los dos, «S/CD».
     */
    private static Phrase codigo(Activo a) {
        String antiguo = vacio(a.getCodigoAntiguo()) ? null : a.getCodigoAntiguo().trim();
        if (vacio(a.getCodigo())) return new Phrase(antiguo != null ? antiguo : "S/CD", TABLA);
        Phrase p = new Phrase(PREFIJO_CODIGO + Activo.codigoDocumento(a.getCodigo()), TABLA);
        if (antiguo != null) p.add(new Chunk("\nAnt.: " + antiguo, TABLA_NOTA));
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
