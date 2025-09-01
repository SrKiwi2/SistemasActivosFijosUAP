package com.usic.SistemasActivosFijosUAP.model.service.importacion;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.linuxense.javadbf.DBFReader;
import com.usic.SistemasActivosFijosUAP.model.IService.IEntidadService;
import com.usic.SistemasActivosFijosUAP.model.IService.IPredioServicio;
import com.usic.SistemasActivosFijosUAP.model.entity.Entidad;
import com.usic.SistemasActivosFijosUAP.model.entity.Predio;

import jakarta.transaction.Transactional;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PredioImportService {

    private final IEntidadService entidadService;
    private final IPredioServicio predioServicio;

    @Data
    public static class ImportResult {
        private int leidas;
        private int insertados;
        private int actualizados;
        private List<String> errores = new ArrayList<>();
    }

    @Transactional
    public ImportResult importarUnidadAdmin(MultipartFile dbfFile, Charset cs, Short gestionPreferida)
            throws IOException {
        ImportResult res = new ImportResult();

        try (InputStream in = new BufferedInputStream(dbfFile.getInputStream());
                DBFReader reader = new DBFReader(in, cs)) {

            List<Predio> batch = new ArrayList<>(1000);
            Object[] row;

            while ((row = reader.nextRecord()) != null) {
                res.leidas++;
                try {
                    String entidadCod = asString(row[0], cs); // ENTIDAD
                    String unidad = asString(row[1], cs); // UNIDAD
                    String descrip = asString(row[2], cs); // DESCRIP
                    String ciudad = asString(row[3], cs); // CIUDAD (puede venir vacío)
                    Short estado = asShort(row[4]); // ESTADOUNI

                    if (isBlank(entidadCod) || isBlank(unidad)) {
                        res.errores.add("Fila " + res.leidas + ": ENTIDAD/UNIDAD vacíos.");
                        continue;
                    }

                    // Resolver entidad (si envías gestión en el formulario úsala; si no, toma la
                    // más reciente)
                    Entidad entidad = (gestionPreferida != null)
                            ? entidadService.findByGestionAndEntidadCodigo(gestionPreferida, entidadCod).orElse(null)
                            : entidadService.findTopByEntidadCodigoOrderByGestionDesc(entidadCod).orElse(null);

                    if (entidad == null) {
                        res.errores.add("Fila " + res.leidas + ": ENTIDAD código " + entidadCod + " no encontrada.");
                        continue;
                    }

                    // upsert por (entidad, unidad)
                    Predio p = predioServicio.findByEntidadAndUnidad(entidad, unidad)
                            .orElseGet(() -> {
                                Predio np = new Predio();
                                np.setEntidad(entidad);
                                np.setUnidad(unidad);
                                return np;
                            });

                    boolean nuevo = (p.getIdPredio() == null);

                    p.setDescrip(nvl(descrip));
                    p.setCiudad(nvl(ciudad));
                    p.setEstadoUni(estado);

                    batch.add(p);
                    if (nuevo)
                        res.insertados++;
                    else
                        res.actualizados++;

                    if (batch.size() == 1000) {
                        predioServicio.saveAll(batch);
                        batch.clear();
                    }
                } catch (Exception exRow) {
                    res.getErrores().add("Fila " + res.leidas + ": " + exRow.getMessage());
                }
            }

            if (!batch.isEmpty())
                predioServicio.saveAll(batch);
        }

        return res;
    }

    /* ---------- helpers ---------- */
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
}
