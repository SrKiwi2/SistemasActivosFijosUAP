package com.usic.SistemasActivosFijosUAP.model.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.usic.SistemasActivosFijosUAP.interoperabilidad.JavaDbfService;
import com.usic.SistemasActivosFijosUAP.model.dao.IActivoDao;
import com.usic.SistemasActivosFijosUAP.model.dto.interoperabilidad.ActivoDbf;
import com.usic.SistemasActivosFijosUAP.model.dto.interoperabilidad.ConciliacionResultadoDto;
import com.usic.SistemasActivosFijosUAP.model.dto.interoperabilidad.DivergenciaActivoDto;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Conciliación entre la BD PostgreSQL (SCIAF) y el archivo {@code ACTUAL.DBF} del
 * VSIAF. NO modifica nada: solo compara por código y reporta las divergencias en
 * las dos direcciones para que un operador las revise.
 *
 * <p>Recordatorio del ciclo de vida de un activo:
 * registrar en SCIAF → estado PENDIENTE → al aprobar se escribe al DBF y pasa a
 * ACTIVO. Por eso un código que está en BD pero no en el DBF suele ser PENDIENTE
 * (aún no empujado); si está ACTIVO sin estar en el DBF es una anomalía a investigar.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConciliacionService {

    private final IActivoDao activoDao;
    private final JavaDbfService dbfService;

    @Transactional(readOnly = true)
    public ConciliacionResultadoDto calcular() {
        long inicio = System.currentTimeMillis();

        // ── 1. Códigos del DBF del VSIAF (fuente legacy) ──────────────────────
        List<ActivoDbf> filasDbf = dbfService.listarActualAll(null);
        Set<String> codigosDbf = new HashSet<>();
        for (ActivoDbf f : filasDbf) {
            String cod = normalizar(f.getCodigo());
            if (cod != null) codigosDbf.add(cod);
        }

        // ── 2. Activos de la BD (proyección ligera, sin ELIMINADO) ────────────
        List<DivergenciaActivoDto> filasBd = activoDao.listarParaConciliacion();
        Set<String> codigosBd = new HashSet<>();
        for (DivergenciaActivoDto d : filasBd) {
            String cod = normalizar(d.getCodigo());
            if (cod != null) codigosBd.add(cod);
        }

        // ── 3. Dirección A: en BD pero NO en el DBF ───────────────────────────
        List<DivergenciaActivoDto> enBdNoEnVsiaf = new ArrayList<>();
        long pendientes = 0, anomalias = 0, otros = 0;
        for (DivergenciaActivoDto d : filasBd) {
            String cod = normalizar(d.getCodigo());
            if (cod != null && codigosDbf.contains(cod)) continue; // sí está en el DBF → ok

            d.setOrigen("BD");
            String estado = d.getEstado() == null ? "" : d.getEstado().trim().toUpperCase();
            switch (estado) {
                case "PENDIENTE" -> { d.setClasificacion("PENDIENTE_SYNC"); pendientes++; }
                case "ACTIVO"    -> { d.setClasificacion("ANOMALIA_ACTIVO"); anomalias++; }
                default          -> { d.setClasificacion("OTRO_ESTADO");     otros++; }
            }
            enBdNoEnVsiaf.add(d);
        }

        // ── 4. Dirección B: en el DBF pero NO en la BD ────────────────────────
        List<DivergenciaActivoDto> enVsiafNoEnBd = new ArrayList<>();
        for (ActivoDbf f : filasDbf) {
            String cod = normalizar(f.getCodigo());
            if (cod == null || codigosBd.contains(cod)) continue;

            DivergenciaActivoDto d = new DivergenciaActivoDto();
            d.setCodigo(f.getCodigo() != null ? f.getCodigo().trim() : null);
            d.setDescripcion(f.getDescrip() != null ? f.getDescrip().trim() : null);
            d.setEstado("ACTIVO");        // en el DBF del VSIAF todo registro vigente es activo
            d.setOrigen("VSIAF");
            d.setClasificacion("SOLO_VSIAF");
            enVsiafNoEnBd.add(d);
        }

        long duracion = System.currentTimeMillis() - inicio;
        log.info("Conciliación BD↔DBF: BD={}, DBF={} | en BD sin DBF={} (pend={}, anom={}, otros={}) | solo VSIAF={} | {}ms",
                codigosBd.size(), codigosDbf.size(), enBdNoEnVsiaf.size(),
                pendientes, anomalias, otros, enVsiafNoEnBd.size(), duracion);

        return ConciliacionResultadoDto.builder()
                .enBdNoEnVsiaf(enBdNoEnVsiaf)
                .enVsiafNoEnBd(enVsiafNoEnBd)
                .totalBd(codigosBd.size())
                .totalVsiaf(codigosDbf.size())
                .pendientesSync(pendientes)
                .anomaliasActivo(anomalias)
                .otroEstado(otros)
                .soloVsiaf(enVsiafNoEnBd.size())
                .generadoEn(LocalDateTime.now())
                .duracionMs(duracion)
                .build();
    }

    /** Normaliza un código para comparar: trim + mayúsculas; null/blanco → null. */
    private String normalizar(String codigo) {
        if (codigo == null) return null;
        String c = codigo.trim();
        return c.isEmpty() ? null : c.toUpperCase();
    }
}
