package com.usic.SistemasActivosFijosUAP.model.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.usic.SistemasActivosFijosUAP.componet.SseEmitterRegistry;
import com.usic.SistemasActivosFijosUAP.interoperabilidad.JavaDbfService;
import com.usic.SistemasActivosFijosUAP.model.IService.IActivoService;
import com.usic.SistemasActivosFijosUAP.model.IService.IAuxiliarService;
import com.usic.SistemasActivosFijosUAP.model.IService.IEntidadService;
import com.usic.SistemasActivosFijosUAP.model.IService.IEstadoActivoService;
import com.usic.SistemasActivosFijosUAP.model.IService.IGrupoContableService;
import com.usic.SistemasActivosFijosUAP.model.IService.IOficinaService;
import com.usic.SistemasActivosFijosUAP.model.IService.IOrganismoFinancieroService;
import com.usic.SistemasActivosFijosUAP.model.IService.IPredioServicio;
import com.usic.SistemasActivosFijosUAP.model.IService.IResponsableService;
import com.usic.SistemasActivosFijosUAP.model.dto.interoperabilidad.ActivoDbf;
import com.usic.SistemasActivosFijosUAP.model.dto.interoperabilidad.SyncResult;
import com.usic.SistemasActivosFijosUAP.model.entity.Activo;
import com.usic.SistemasActivosFijosUAP.model.entity.Auxiliar;
import com.usic.SistemasActivosFijosUAP.model.entity.Entidad;
import com.usic.SistemasActivosFijosUAP.model.entity.EstadoActivo;
import com.usic.SistemasActivosFijosUAP.model.entity.GrupoContable;
import com.usic.SistemasActivosFijosUAP.model.entity.Oficina;
import com.usic.SistemasActivosFijosUAP.model.entity.OrganismoFinanciero;
import com.usic.SistemasActivosFijosUAP.model.entity.Responsable;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ActivoSyncService {

    private final IActivoService activoService;
    private final IOficinaService oficinaService;
    private final IResponsableService responsableService;
    private final IGrupoContableService grupoContableService;
    private final IAuxiliarService auxiliarService;
    private final IOrganismoFinancieroService organismoFinancieroService;
    private final SyncControlService syncControlService;
    private final SseEmitterRegistry sseRegistry;
    private final JavaDbfService dbfService;

    // ── Tamaño de chunk para progreso ───────────────────────────────────────
    private static final int CHUNK_SIZE  = 1_000; // emitir progreso cada N filas
    private static final int BATCH_SAVE  = 500;   // guardar en BD cada N registros

    public ResponseEntity<?> syncFromMounted(String q, boolean forzarCompleto) {
        long inicio = System.currentTimeMillis();

        try {
            List<ActivoDbf> filas = dbfService.listarActualAll(q);
            int totalFilas = filas.size();
            log.info("ACTUAL.DBF — {} registros a procesar", totalFilas);

            // ── Cargar caches de entidades relacionadas (1 query cada uno) ──
            Map<String, Oficina>              oficinasCache    = cargarOficinasCache();
            Map<String, Responsable>          responsablesCache = cargarResponsablesCache();
            Map<Integer, GrupoContable>       gruposCache      = cargarGruposCache();
            Map<String, Auxiliar>             auxiliaresCache  = cargarAuxiliaresCache();
            Map<String, OrganismoFinanciero>  organismoCache   = cargarOrganismosCache();
            Map<String, Activo>               activosCache     = cargarActivosCache();

            int inserted = 0, updated = 0, skipped = 0;
            int sinOficina = 0, sinResponsable = 0, sinGrupo = 0, sinCodigo = 0;
            int procesados = 0;

            List<Activo> batch = new ArrayList<>(BATCH_SAVE);

            for (ActivoDbf f : filas) {
                procesados++;

                // ── Emitir progreso cada CHUNK_SIZE filas ────────────────────
                if (procesados % CHUNK_SIZE == 0) {
                    int pct = (int) ((procesados / (double) totalFilas) * 100);
                    sseRegistry.broadcast("sync-progreso", Map.of(
                        "tabla",      "activo",
                        "procesados", procesados,
                        "total",      totalFilas,
                        "porcentaje", pct,
                        "insertados", inserted,
                        "actualizados", updated
                    ));
                    log.info("ACTUAL.DBF — progreso: {}/{} ({}%)", procesados, totalFilas, pct);
                }

                // ── Validar clave obligatoria ────────────────────────────────
                if (f.getCodigo() == null || f.getCodigo().isBlank()) {
                    sinCodigo++;
                    continue;
                }

                // ── Resolver Oficina ─────────────────────────────────────────
                // Clave: entidad|unidad|codOfi
                String keyOficina = f.getEntidadCodigo() + "|" + f.getUnidad() + "|" + f.getCodOfi();
                Oficina oficina = oficinasCache.get(keyOficina);
                if (oficina == null) {
                    sinOficina++;
                    continue;
                }

                // ── Resolver Responsable ─────────────────────────────────────
                // Clave: idOficina|codResp
                Responsable responsable = null;
                if (f.getCodResp() != null && !f.getCodResp().isBlank()) {
                    String keyResp = oficina.getIdOficina() + "|" + f.getCodResp().trim();
                    responsable = responsablesCache.get(keyResp);
                }
                if (responsable == null) {
                    sinResponsable++;
                    continue; // activo sin responsable no tiene sentido
                }

                // ── Resolver GrupoContable (opcional según tu modelo) ────────
                GrupoContable grupo = null;
                if (f.getCodCont() != null) {
                    grupo = gruposCache.get(f.getCodCont().intValue());
                    if (grupo == null) {
                        sinGrupo++;
                        continue;
                    }
                }

                // ── Resolver Auxiliar (puede ser null) ───────────────────────
                Auxiliar auxiliar = null;
                if (f.getCodCont() != null && f.getCodAux() != null) {
                    String keyAux = oficina.getPredio().getIdPredio() + "|" +
                                    (grupo != null ? grupo.getIdGrupoContable() : "?") + "|" +
                                    f.getCodAux();
                    auxiliar = auxiliaresCache.get(keyAux);
                }

                // ── Resolver OrganismoFinanciero (puede ser null) ────────────
                OrganismoFinanciero organismo = null;
                if (f.getCodOf() != null && !f.getCodOf().isBlank()) {
                    organismo = organismoCache.get(f.getCodOf().trim());
                }

                // ── Buscar o crear Activo ────────────────────────────────────
                Activo activo = activosCache.get(f.getCodigo().trim());
                boolean esNuevo = (activo == null);

                if (esNuevo) {
                    activo = new Activo();
                    activo.setCodigo(f.getCodigo().trim());
                    activo.setEstado("ACTIVO"); // sincronizado = activo
                    activo.setApiEstado(Short.valueOf("1"));
                }

                // ── Mapear campos ────────────────────────────────────────────
                activo.setOficina(oficina);
                activo.setResponsable(responsable);
                activo.setGrupoContable(grupo);
                activo.setAuxiliar(auxiliar);
                activo.setOrganismoFinanciero(organismo);
                if (organismo != null) activo.setOrgFinCode(organismo.getCodOf());

                if (f.getDescrip() != null && !f.getDescrip().isBlank()) {
                    String desc = f.getDescrip().trim();
                    activo.setDescripcion(desc.length() > 1024 ? desc.substring(0, 1024) : desc);
                }
                activo.setCosto(f.getCosto() != null ? f.getCosto() : 0.0);
                activo.setFechaAdquisicion(f.getFechaAdq());
                if (f.getVidaUtil() != null)
                    activo.setVidaUtil(java.math.BigDecimal.valueOf(f.getVidaUtil()));
                activo.setFechaUlt(f.getFechaUlt());
                activo.setUsuario(f.getUsuario() != null
                    ? (f.getUsuario().length() > 60 ? f.getUsuario().substring(0, 60) : f.getUsuario())
                    : null);
                activo.setApiEstado(f.getApiEstado() != null ? f.getApiEstado() : Short.valueOf("1"));

                // ── Hash para skip ───────────────────────────────────────────
                String nuevoHash = activo.calcularHash();
                if (!esNuevo && !forzarCompleto) {
                    if (nuevoHash.equals(activo.getHashDatos())) {
                        skipped++;
                        continue;
                    }
                }

                activo.setHashDatos(nuevoHash);
                activo.setFechaUltimaSync(LocalDateTime.now());

                batch.add(activo);
                if (esNuevo) {
                    inserted++;
                    // Actualizar caché para evitar duplicados en la misma run
                    activosCache.put(f.getCodigo().trim(), activo);
                } else {
                    updated++;
                }

                if (batch.size() >= BATCH_SAVE) {
                    activoService.saveAll(batch);
                    batch.clear();
                }
            }

            if (!batch.isEmpty()) {
                activoService.saveAll(batch);
                batch.clear();
            }

            long duracion = System.currentTimeMillis() - inicio;

            SyncResult resultado = SyncResult.builder()
                .totalLeidas(totalFilas)
                .insertados(inserted)
                .actualizados(updated)
                .omitidos(skipped)
                .sinOficina(sinOficina)
                .sinResponsable(sinResponsable) // agrega este campo a SyncResult si no existe
                .sinGrupoContable(sinGrupo)
                .duracionMs(duracion)
                .build();

            syncControlService.registrarSincronizacion("activo", resultado);

            log.info("ACTUAL.DBF sync completo: +{} nuevos, ~{} actualizados, {} omitidos en {}ms",
                inserted, updated, skipped, duracion);

            return ResponseEntity.ok(Map.ofEntries(
                Map.entry("ok",             true),
                Map.entry("totalLeidas",    totalFilas),
                Map.entry("insertados",     inserted),
                Map.entry("actualizados",   updated),
                Map.entry("omitidos",       skipped),
                Map.entry("sinOficina",     sinOficina),
                Map.entry("sinResponsable", sinResponsable),
                Map.entry("sinGrupoContable", sinGrupo),
                Map.entry("duracionMs",     duracion),
                Map.entry("mensaje",        String.format(
                    "Activos sincronizados en %.1fs", duracion / 1000.0))
            ));

        } catch (Exception ex) {
            log.error("Error sync ACTUAL.DBF: {}", ex.getMessage(), ex);
            syncControlService.registrarError("activo", ex.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                "ok",      false,
                "message", "Error sincronizando ACTUAL.DBF: " + ex.getMessage()
            ));
        }
    }

    // ── Caches (1 query por tabla) ───────────────────────────────────────────

    private Map<String, Oficina> cargarOficinasCache() {
        return oficinaService.findAll().stream()
            .filter(o -> o.getPredio() != null && o.getPredio().getEntidad() != null)
            .collect(Collectors.toMap(
                o -> o.getPredio().getEntidad().getEntidadCodigo() + "|" +
                     o.getPredio().getUnidad() + "|" + o.getCodOfi(),
                o -> o,
                (a, b) -> a
            ));
    }

    private Map<String, Responsable> cargarResponsablesCache() {
        return responsableService.findAll().stream()
            .filter(r -> r.getOficina() != null && r.getCodigoFuncionario() != null)
            .collect(Collectors.toMap(
                r -> r.getOficina().getIdOficina() + "|" + r.getCodigoFuncionario().trim(),
                r -> r,
                (a, b) -> a
            ));
    }

    private Map<Integer, GrupoContable> cargarGruposCache() {
        return grupoContableService.listarGruposContables().stream()
            .filter(g -> g.getCodContable() != null)
            .collect(Collectors.toMap(
                GrupoContable::getCodContable,
                g -> g,
                (a, b) -> a
            ));
    }

    private Map<String, Auxiliar> cargarAuxiliaresCache() {
        return auxiliarService.findAll().stream()
            .filter(a -> a.getPredio() != null && a.getGrupoContable() != null)
            .collect(Collectors.toMap(
                a -> a.getPredio().getIdPredio() + "|" +
                     a.getGrupoContable().getIdGrupoContable() + "|" + a.getCodAux(),
                a -> a,
                (a, b) -> a
            ));
    }

    private Map<String, OrganismoFinanciero> cargarOrganismosCache() {
        return organismoFinancieroService.findAll().stream()
            .filter(o -> o.getCodOf() != null)
            .collect(Collectors.toMap(
                o -> o.getCodOf().trim(),
                o -> o,
                (a, b) -> a
            ));
    }

    private Map<String, Activo> cargarActivosCache() {
        // ⚠️ Solo cargamos los campos necesarios para el hash y clave
        // Si tienes muchos activos esto puede ser pesado: considera un @Query projection
        return activoService.findAllForSync().stream()
            .filter(a -> a.getCodigo() != null)
            .collect(Collectors.toMap(
                Activo::getCodigo,
                a -> a,
                (a, b) -> a
            ));
    }
}