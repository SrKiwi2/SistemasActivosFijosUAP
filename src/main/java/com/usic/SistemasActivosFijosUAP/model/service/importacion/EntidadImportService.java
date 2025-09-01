package com.usic.SistemasActivosFijosUAP.model.service.importacion;

import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.linuxense.javadbf.*;
import com.usic.SistemasActivosFijosUAP.model.IService.IEntidadService;
import com.usic.SistemasActivosFijosUAP.model.entity.Entidad;

import org.springframework.transaction.annotation.Transactional;

import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

@Service
@RequiredArgsConstructor
public class EntidadImportService {

    private final IEntidadService entidadService;

    @Data
    public static class ImportResult {
        private int leidas;
        private int insertados;
        private int actualizados;
        private List<String> errores = new ArrayList<>();
    }

    @Transactional
    public ImportResult importar(MultipartFile dbfFile, Charset cs) throws IOException {
        ImportResult res = new ImportResult();

        try (InputStream in = new BufferedInputStream(dbfFile.getInputStream());
             DBFReader reader = new DBFReader(in, cs)) {  // ✅ API 1.13.1

            List<Entidad> buffer = new ArrayList<>(1000);
            Object[] row;

            while ((row = reader.nextRecord()) != null) {
                res.leidas++;

                try {
                    // Campos según tu DBF:
                    Short  gestion        = asShort(row[0]);                       // GESTION (SmallInt)
                    String entidadCodigo  = asString(row[1], cs);                  // ENTIDAD (Text) => código
                    String descEnt        = asString(row[2], cs);                  // DESC_ENT
                    String sigla          = asString(row[3], cs);                  // SIGLA_ENT
                    Short  sector         = asShort(row[4]);                       // SECTOR_ENT
                    Short  subsector      = asShort(row[5]);                       // SUBSEC_ENT
                    Short  area           = asShort(row[6]);                       // AREA_ENT
                    Short  subarea        = asShort(row[7]);                       // SUBAREAENT
                    Short  nivel          = asShort(row[8]);                       // NIVEL_INST

                    // upsert por clave de negocio (gestion + entidadCodigo)
                    Entidad e = entidadService
                            .findByGestionAndEntidadCodigo(gestion, entidadCodigo)
                            .orElseGet(() -> {
                                Entidad n = new Entidad();
                                n.setGestion(gestion);
                                n.setEntidadCodigo(entidadCodigo);
                                return n;
                            });

                    boolean esNuevo = (e.getIdEntidad() == null);

                    // mapear el resto
                    e.setDescripcion(descEnt);
                    e.setSigla(sigla);
                    e.setSectorEnt(sector);
                    e.setSubsecEnt(subsector);
                    e.setAreaEnt(area);
                    e.setSubareaEnt(subarea);
                    e.setNivelInst(nivel);
                    e.setEstado("A");

                    buffer.add(e);
                    if (esNuevo) res.insertados++; else res.actualizados++;

                    // flush por lotes
                    if (buffer.size() == 1000) {
                        entidadService.saveAll(buffer); // ✅ lista completa
                        buffer.clear();
                    }
                } catch (Exception exRow) {
                    res.getErrores().add("Fila " + res.leidas + ": " + exRow.getMessage());
                }
            }

            if (!buffer.isEmpty()) {
                entidadService.saveAll(buffer);
            }
        }

        return res;
    }

    // --- helpers seguros ---
    private Short asShort(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.shortValue();
        if (o instanceof String s && !s.isBlank()) return Short.valueOf(s.trim());
        return null;
    }

    private String asString(Object o, Charset cs) {
        if (o == null) return null;
        if (o instanceof String s) return s.trim();
        if (o instanceof byte[] b) return new String(b, cs).trim();
        return o.toString().trim();
    }

}
