package com.usic.SistemasActivosFijosUAP.model.service.importacion;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.linuxense.javadbf.DBFReader;
import com.usic.SistemasActivosFijosUAP.model.IService.IEntidadService;
import com.usic.SistemasActivosFijosUAP.model.IService.IOficinaService;
import com.usic.SistemasActivosFijosUAP.model.IService.IPredioServicio;
import com.usic.SistemasActivosFijosUAP.model.entity.Entidad;
import com.usic.SistemasActivosFijosUAP.model.entity.Oficina;
import com.usic.SistemasActivosFijosUAP.model.entity.Predio;

import jakarta.transaction.Transactional;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OficinaImportService {

    private final IEntidadService entidadService;
    private final IPredioServicio predioServicio;
    private final IOficinaService oficinaService;

    @Data
    public static class ImportResult {
        private int leidas;
        private int insertados;
        private int actualizados;


        // Desglose de omitidos
        private int omitidosCampos;     // ENTIDAD/UNIDAD/CODOFI incompletos
        private int omitidosSinEntidad; // ENTIDAD no encontrada
        private int omitidosSinPredio;  // PREDIO no encontrado
        private int erroresExcepcion;   // errores inesperados

        private List<String> errores = new ArrayList<>();
    }

    /**
     * Importa OFICINA.DBF
     * 
     * @param dbfFile          archivo DBF
     * @param cs               charset del DBF (p.ej. windows-1252)
     * @param gestionPreferida si la envías, prioriza esa gestión para resolver la
     *                         Entidad;
     *                         si es null, usará la última gestión registrada para
     *                         ese código de ENTIDAD
     */
    @Transactional
    public ImportResult importarOficina(MultipartFile dbfFile, Charset cs, Short gestionPreferida) throws IOException {
        ImportResult res = new ImportResult();

        try (InputStream in = new BufferedInputStream(dbfFile.getInputStream());
                DBFReader reader = new DBFReader(in, cs)) {

            List<Oficina> batch = new ArrayList<>(1000);
            Object[] row;

            while ((row = reader.nextRecord()) != null) {
                res.leidas++;
                try {
                    String entidadCodRaw = asString(row[0], cs); // ENTIDAD (Text)
                    String unidadRaw = asString(row[1], cs); // UNIDAD (Text)
                    Short codOfi = asShort(row[2]); // CODOFI (SmallInt)
                    String nomOfic = asString(row[3], cs); // NOMOFIC (Text)
                    String observ = asString(row[4], cs); // OBSERV (Memo -> String)
                    LocalDate feult = asLocalDate(row[5]); // FEULT (Date)
                    String usuario = asString(row[6], cs); // USUAR (Text)
                    Short apiEstado = asShort(row[7]); // API_ESTADO (SmallInt)

                    String entidadCod = normEntidadCodigo(entidadCodRaw);
                    String unidad     = normUnidad(unidadRaw); // 👈 IMPORTANTE

                    if (isBlank(entidadCod) || isBlank(unidad) || codOfi == null) {
                        res.setOmitidosCampos(res.getOmitidosCampos() + 1);
                        res.getErrores().add("Fila " + res.leidas + ": ENTIDAD/UNIDAD/CODOFI incompletos. ENTIDAD='"
                                + entidadCodRaw + "', UNIDAD='" + unidadRaw + "', CODOFI=" + codOfi);
                        continue;
                    }

                    // Resolver Entidad
                    Entidad entidad = (gestionPreferida != null)
                        ? entidadService.findByGestionAndEntidadCodigo(gestionPreferida, entidadCod).orElse(null)
                        : entidadService.findTopByEntidadCodigoOrderByGestionDesc(entidadCod).orElse(null);
                    if (entidad == null) {
                        res.setOmitidosSinEntidad(res.getOmitidosSinEntidad() + 1);
                        res.getErrores().add("Fila " + res.leidas + ": ENTIDAD código " + entidadCod + " no encontrada.");
                        continue;
                    }

                    // Resolver Predio (Entidad + UNIDAD)
                    Predio predio = predioServicio.findByEntidadAndUnidadIgnoreCase(entidad, unidad).orElse(null);
                    if (predio == null) {
                        res.setOmitidosSinPredio(res.getOmitidosSinPredio() + 1);
                        res.getErrores().add("Fila " + res.leidas + ": Predio no encontrado para ENTIDAD="
                                + entidadCod + " UNIDAD=" + unidad);
                        continue;
                    }

                    // UPSERT por (Predio, CODOFI)
                    Oficina o = oficinaService.findByPredioAndCodOfi(predio, codOfi)
                            .orElseGet(() -> {
                                Oficina no = new Oficina();
                                no.setPredio(predio);
                                no.setCodOfi(codOfi);
                                return no;
                            });

                    boolean nuevo = (o.getIdOficina() == null);

                    String nombreFinal = !isBlank(nomOfic)
                            ? nomOfic.trim()
                            : ("OFICINA " + (codOfi != null ? codOfi : "")); // fallback legible

                    if (nombreFinal.length() > 255) {
                        nombreFinal = nombreFinal.substring(0, 255);
                    }

                    o.setNombre(nombreFinal);
                    if (isBlank(nomOfic)) {
                        res.getErrores().add("Fila " + res.leidas +
                            ": NOMOFIC vacío; se autocompletó con '" + nombreFinal + "'.");
                    }
                    
                    o.setObserv(nvl(observ));
                    o.setFechaUlt(feult);
                    o.setUsuario(nvl(usuario));
                    o.setApiEstado(apiEstado);
                    o.setEstado("ACTIVO");

                    batch.add(o);
                    if (nuevo)
                        res.insertados++;
                    else
                        res.actualizados++;

                    if (batch.size() == 1000) {
                        oficinaService.saveAll(batch);
                        batch.clear();
                    }
                } catch (Exception exRow) {
                    res.errores.add("Fila " + res.leidas + ": " + exRow.getMessage());
                }
            }

            if (!batch.isEmpty())
                oficinaService.saveAll(batch);
        }

        return res;
    }

    /* ===== Helpers ===== */

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private String nvl(String s) {
        return isBlank(s) ? null : s.trim();
    }

    private Short asShort(Object o) {
        if (o == null)
            return null;
        if (o instanceof Number n)
            return n.shortValue();
        if (o instanceof String s && !s.isBlank())
            return Short.valueOf(s.trim());
        return null;
    }

    private String asString(Object o, Charset cs) {
        if (o == null)
            return null;
        if (o instanceof String s)
            return s.trim();
        if (o instanceof byte[] b)
            return new String(b, cs).trim();
        return o.toString().trim();
    }

    private LocalDate asLocalDate(Object o) {
        if (o == null)
            return null;
        if (o instanceof java.util.Date d) {
            return d.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        }
        if (o instanceof String s && !s.isBlank()) {
            // por si algún proveedor entrega date como texto 'yyyy-MM-dd' o 'dd/MM/yyyy'
            String t = s.trim();
            try {
                if (t.contains("-"))
                    return LocalDate.parse(t); // ISO
                if (t.contains("/")) {
                    String[] p = t.split("/");
                    // dd/MM/yyyy
                    return LocalDate.of(Integer.parseInt(p[2]), Integer.parseInt(p[1]), Integer.parseInt(p[0]));
                }
            } catch (Exception ignore) {
            }
        }
        return null;
    }

    private String normUnidad(String s) {
        if (s == null) return null;
        return s.trim().toUpperCase(); // igual que guardaste en Predio
    }

    private String normEntidadCodigo(String s) {
        return s == null ? null : s.trim(); // suele venir “148”, no hace falta upper
    }
}