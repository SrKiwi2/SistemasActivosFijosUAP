package com.usic.SistemasActivosFijosUAP.model.service.importacion;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
        private int omitidosCampos; // ENTIDAD/UNIDAD/CODOFI incompletos
        private int omitidosSinEntidad; // ENTIDAD no encontrada
        private int omitidosSinPredio; // PREDIO no encontrado
        private int erroresExcepcion; // errores inesperados

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

            Map<String, Oficina> cache = new HashMap<>(2000);
            Set<String> added = new HashSet<>(2000); // para evitar meter la misma instancia múltiples veces al batch
            Object[] row;

            while ((row = reader.nextRecord()) != null) {
                res.leidas++;
                try {
                    String entidadCodRaw = asString(row[0], cs);
                    String unidadRaw = asString(row[1], cs);
                    Short codOfi = asShort(row[2]);
                    String nomOfic = asString(row[3], cs);
                    String observ = asString(row[4], cs);
                    LocalDate feult = asLocalDate(row[5]);
                    String usuario = asString(row[6], cs);
                    Short apiEstado = asShort(row[7]);

                    String entidadCod = normEntidadCodigo(entidadCodRaw);
                    String unidad = normUnidad(unidadRaw);

                    if (isBlank(entidadCod) || isBlank(unidad) || codOfi == null) {
                        res.omitidosCampos++;
                        res.errores.add("Fila " + res.leidas + ": ENTIDAD/UNIDAD/CODOFI incompletos. ENTIDAD='" +
                                entidadCodRaw + "', UNIDAD='" + unidadRaw + "', CODOFI=" + codOfi);
                        continue;
                    }

                    // Entidad
                    Entidad entidad = (gestionPreferida != null)
                            ? entidadService.findByGestionAndEntidadCodigo(gestionPreferida, entidadCod).orElse(null)
                            : entidadService.findTopByEntidadCodigoOrderByGestionDesc(entidadCod).orElse(null);
                    if (entidad == null) {
                        res.omitidosSinEntidad++;
                        res.errores.add("Fila " + res.leidas + ": ENTIDAD código " + entidadCod + " no encontrada.");
                        continue;
                    }

                    // Predio
                    Predio predio = predioServicio.findByEntidadAndUnidadIgnoreCase(entidad, unidad).orElse(null);
                    if (predio == null) {
                        res.omitidosSinPredio++;
                        res.errores.add("Fila " + res.leidas + ": Predio no encontrado para ENTIDAD=" +
                                entidadCod + " UNIDAD=" + unidad);
                        continue;
                    }

                    String k = key(predio, codOfi);
                    Oficina o = cache.get(k);
                    if (o == null) {
                        o = oficinaService.findByPredioAndCodOfi(predio, codOfi).orElse(null);
                        if (o == null) {
                            o = new Oficina();
                            o.setPredio(predio);
                            o.setCodOfi(codOfi);
                        }
                        cache.put(k, o);
                    }

                    boolean nuevo = (o.getIdOficina() == null);

                    String nombreFinal = !isBlank(nomOfic) ? nomOfic.trim() : ("OFICINA " + codOfi);
                    if (nombreFinal.length() > 255)
                        nombreFinal = nombreFinal.substring(0, 255);
                    o.setNombre(nombreFinal);

                    // Observ: evita guardar el literal "(memo)"
                    String observFinal = nvl(observ);
                    if (observFinal != null && observFinal.equalsIgnoreCase("(memo)"))
                        observFinal = null;
                    o.setObserv(observFinal);

                    o.setFechaUlt(feult);
                    o.setUsuario(truncate(nvl(usuario), 60));
                    o.setApiEstado(apiEstado);
                    o.setEstado("ACTIVO");

                    try {
                        oficinaService.save(o); // guarda por fila: contador exacto y sin sorpresas
                        if (nuevo)
                            res.insertados++;
                        else
                            res.actualizados++;
                    } catch (org.springframework.dao.DataIntegrityViolationException e) {
                        res.erroresExcepcion++;
                        res.errores.add("Fila " + res.leidas + ": " + e.getMostSpecificCause().getMessage());
                    }

                } catch (Exception exRow) {
                    res.erroresExcepcion++;
                    res.errores.add("Fila " + res.leidas + ": " + exRow.getMessage());
                }
            }
        }

        return res;
    }

    /* ===== Helpers ===== */

    private String truncate(String s, int max) {
        return (s == null || s.length() <= max) ? s : s.substring(0, max);
    }

    String key(Predio p, Short cod) {
        return p.getIdPredio() + "::" + cod;
    }

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
        if (s == null)
            return null;
        return s.trim().toUpperCase(); // igual que guardaste en Predio
    }

    private String normEntidadCodigo(String s) {
        return s == null ? null : s.trim(); // suele venir “148”, no hace falta upper
    }
}