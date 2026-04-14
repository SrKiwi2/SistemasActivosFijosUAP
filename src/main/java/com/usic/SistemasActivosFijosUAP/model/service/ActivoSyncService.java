package com.usic.SistemasActivosFijosUAP.model.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.usic.SistemasActivosFijosUAP.componet.SseEmitterRegistry;
import com.usic.SistemasActivosFijosUAP.interoperabilidad.JavaDbfService;
import com.usic.SistemasActivosFijosUAP.model.IService.IActivoService;
import com.usic.SistemasActivosFijosUAP.model.IService.IAuxiliarService;
import com.usic.SistemasActivosFijosUAP.model.IService.IGrupoContableService;
import com.usic.SistemasActivosFijosUAP.model.IService.IOficinaService;
import com.usic.SistemasActivosFijosUAP.model.IService.IOrganismoFinancieroService;
import com.usic.SistemasActivosFijosUAP.model.IService.IResponsableService;
import com.usic.SistemasActivosFijosUAP.model.dto.interoperabilidad.ActivoDbf;
import com.usic.SistemasActivosFijosUAP.model.dto.interoperabilidad.SyncResult;
import com.usic.SistemasActivosFijosUAP.model.entity.Activo;
import com.usic.SistemasActivosFijosUAP.model.entity.Auxiliar;
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
    private final IGrupoContableService grupoContableService;
    private final IOrganismoFinancieroService organismoFinancieroService;
    private final SyncControlService syncControlService;
    private final SseEmitterRegistry sseRegistry;
    private final JavaDbfService dbfService;
    private final ActivoSyncCacheService cache;

    @PersistenceContext
    private EntityManager entityManager;

    // ── Tamaño de chunk para progreso ───────────────────────────────────────
    private static final int CHUNK_SIZE  = 1_000; // emitir progreso cada N filas
    private static final int BATCH_SAVE  = 500;   // guardar en BD cada N registros

    @Transactional
    public ResponseEntity<?> syncFromMounted(String q, boolean forzarCompleto) {
        long inicio = System.currentTimeMillis();

        try {
            List<ActivoDbf> filas = dbfService.listarActualAll(q);
            int totalFilas = filas.size();
            log.info("ACTUAL.DBF — {} registros a procesar", totalFilas);

            // Caches con JPQL que hace JOIN FETCH (no lazy)
            Map<String, Oficina>             oficinasMap     = cargarOficinasCache();
            Map<String, Responsable>         responsablesCache = cargarResponsablesCache();
            Map<Integer, GrupoContable>      gruposCache       = cargarGruposCache();
            Map<String, Auxiliar>            auxiliaresCache   = cargarAuxiliaresCache();
            Map<String, OrganismoFinanciero> organismoCache    = cargarOrganismosCache();
            Map<Short, EstadoActivo>         estadosMap     = cache.estadosActivo();
            Map<String, Activo>              activosCache      = cargarActivosCache();

            EstadoActivo estadoPorDefecto = estadosMap.values().stream()
                .findFirst().orElse(null);

            int inserted = 0, updated = 0, skipped = 0;
            int sinOficina = 0, sinResponsable = 0, sinGrupo = 0, sinCodigo = 0;
            int procesados = 0;

            List<Activo> batch = new ArrayList<>(BATCH_SAVE);

            for (ActivoDbf f : filas) {
                procesados++;

                if (procesados % CHUNK_SIZE == 0) {
                    int pct = (int) ((procesados / (double) totalFilas) * 100);
                    sseRegistry.broadcast("sync-progreso", Map.of(
                        "tabla",        "activo",
                        "procesados",   procesados,
                        "total",        totalFilas,
                        "porcentaje",   pct,
                        "insertados",   inserted,
                        "actualizados", updated
                    ));
                    log.info("ACTUAL.DBF — progreso: {}/{} ({}%)", procesados, totalFilas, pct);
                }

                if (f.getCodigo() == null || f.getCodigo().isBlank()) {
                    sinCodigo++;
                    continue;
                }

                Oficina oficina = null;
                if (f.getEntidadCodigo() != null && f.getUnidad() != null && f.getCodOfi() != null) {
                    String keyOf = f.getEntidadCodigo() + "|" + f.getUnidad() + "|" + f.getCodOfi();
                    oficina = oficinasMap.get(keyOf);
                }
                if (oficina == null) { sinOficina++; continue; }

                 Responsable responsable = null;
                if (f.getCodResp() != null && !f.getCodResp().isBlank()) {
                    responsable = responsablesCache.get(
                        oficina.getIdOficina() + "|" + f.getCodResp().trim());
                }
                if (responsable == null) { sinResponsable++; continue; }

                GrupoContable grupo = null;
                if (f.getCodCont() != null) {
                    grupo = gruposCache.get(f.getCodCont().intValue());
                    if (grupo == null) { sinGrupo++; continue; }
                }

                Auxiliar auxiliar = null;
                if (f.getCodCont() != null && f.getCodAux() != null && grupo != null) {
                    String keyAux = oficina.getPredio().getIdPredio() + "|" +
                                    grupo.getIdGrupoContable() + "|" + f.getCodAux();
                    auxiliar = auxiliaresCache.get(keyAux);
                }

                OrganismoFinanciero organismo = null;
                if (f.getCodOf() != null && !f.getCodOf().isBlank())
                    organismo = organismoCache.get(f.getCodOf().trim());

                EstadoActivo estadoActivo = null;
                if (f.getCodEstado() != null)
                    estadoActivo = estadosMap.get(f.getCodEstado());
                if (estadoActivo == null)
                    estadoActivo = estadoPorDefecto;

                Activo activo = activosCache.get(f.getCodigo().trim());
                boolean esNuevo = (activo == null);

                if (esNuevo) {
                    activo = new Activo();
                    activo.setCodigo(f.getCodigo().trim());
                    activo.setEstado("ACTIVO");
                    activo.setApiEstado(Short.valueOf("1"));
                    activo.setCostoAnterior(0.0);
                    activo.setDepreciacionAcum(0.0);
                    activo.setVidaUtilAnterior(0);
                }

                activo.setOficina(oficina);
                activo.setResponsable(responsable);
                activo.setGrupoContable(grupo);
                activo.setAuxiliar(auxiliar);
                activo.setOrganismoFinanciero(organismo);

                if (organismo != null) {
                    activo.setOrganismoFinanciero(organismo);
                    activo.setOrgFinCode(organismo.getCodOf());
                } else if (f.getCodOf() != null && !f.getCodOf().isBlank()) {
                    // El DBF tiene un código que no resolvió → guardar el texto al menos
                    activo.setOrgFinCode(f.getCodOf().trim());
                }

                if (f.getDescrip() != null && !f.getDescrip().isBlank()) {
                    String desc = f.getDescrip().trim();
                    activo.setDescripcion(desc.length() > 1024 ? desc.substring(0, 1024) : desc);
                } else if (esNuevo) {
                    activo.setDescripcion("SIN DESCRIPCION"); // @NotBlank en entidad
                }

                if (f.getCodigoSec() != null && !f.getCodigoSec().isBlank())
                    activo.setCodigoSec(f.getCodigoSec().trim());

                if (f.getCosto() != null)
                    activo.setCosto(f.getCosto());
                else if (esNuevo)
                    activo.setCosto(0.0);

                if (f.getDepAcu() != null)
                    activo.setDepreciacionAcum(f.getDepAcu());

                if (f.getCostoAnt() != null)
                    activo.setCostoAnterior(f.getCostoAnt());

                if (f.getVidaUtil() != null)
                    activo.setVidaUtil(java.math.BigDecimal.valueOf(f.getVidaUtil()));

                if (f.getVutAnt() != null)
                    activo.setVidaUtilAnterior(f.getVutAnt());

                if (f.getFechaAdq() != null)
                    activo.setFechaAdquisicion(f.getFechaAdq());

                if (f.getFechaAnt() != null)
                    activo.setFechaAnterior(f.getFechaAnt());

                if (f.getBRev() != null)
                    activo.setRevaluado(f.getBRev());

                if (f.getBandUfv() != null)
                    activo.setBandUfv(f.getBandUfv());

                if (f.getCodRube() != null && !f.getCodRube().isBlank())
                    activo.setCodRube(f.getCodRube().trim());

                if (f.getNroConv() != null && !f.getNroConv().isBlank())
                    activo.setNroConv(f.getNroConv().trim());

                if (f.getBanderas() != null && !f.getBanderas().isBlank())
                    activo.setBanderas(f.getBanderas().trim());

                if (f.getObserv() != null)
                    activo.setObserv(f.getObserv());

                if (f.getFechaUlt() != null) activo.setFechaUlt(f.getFechaUlt());

               if (f.getUsuario() != null) {
                    String usr = f.getUsuario().trim();
                    activo.setUsuario(usr.length() > 60 ? usr.substring(0, 60) : usr);
                }
                if (f.getApiEstado() != null)
                    activo.setApiEstado(f.getApiEstado());
                else if (esNuevo)
                    activo.setApiEstado(Short.valueOf("1"));

                if (f.getFecMod() != null) activo.setFecMod(f.getFecMod());
                if (f.getUsuMod() != null) {
                    String usr = f.getUsuMod().trim();
                    activo.setUsuMod(usr.length() > 60 ? usr.substring(0, 60) : usr);
                }

                String nuevoHash = activo.calcularHash();
                if (!esNuevo && !forzarCompleto && nuevoHash.equals(activo.getHashDatos())) {
                    skipped++;
                    continue;
                }

                activo.setHashDatos(nuevoHash);
                activo.setFechaUltimaSync(LocalDateTime.now());

                batch.add(activo);
                if (esNuevo) {
                    inserted++;
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
                .sinResponsable(sinResponsable)
                .sinGrupoContable(sinGrupo)
                .duracionMs(duracion)
                .build();

            syncControlService.registrarSincronizacion("activo", resultado);

            log.info("ACTUAL.DBF sync: +{} nuevos, ~{} actualizados, {} omitidos, " +
                     "{} sin oficina, {} sin responsable, {} sin grupo | {}ms",
                inserted, updated, skipped, sinOficina, sinResponsable, sinGrupo, duracion);

            return ResponseEntity.ok(Map.ofEntries(
                Map.entry("ok",               true),
                Map.entry("totalLeidas",       totalFilas),
                Map.entry("insertados",        inserted),
                Map.entry("actualizados",      updated),
                Map.entry("omitidos",          skipped),
                Map.entry("sinOficina",        sinOficina),
                Map.entry("sinResponsable",    sinResponsable),
                Map.entry("sinGrupoContable",  sinGrupo),
                Map.entry("duracionMs",        duracion),
                Map.entry("mensaje",           String.format("Activos en %.1fs", duracion / 1000.0))
            ));

        } catch (Exception ex) {
            log.error("Error sync ACTUAL.DBF: {}", ex.getMessage(), ex);
            syncControlService.registrarError("activo", ex.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                "ok", false, "message", "Error: " + ex.getMessage()
            ));
        }
    }

    // ── Caches con JOIN FETCH: sin lazy loading, sin LazyInitializationException ──

    @Transactional(readOnly = true)
    public Map<String, Oficina> cargarOficinasCache() {
        // JOIN FETCH trae predio y entidad en una sola query
        List<Oficina> oficinas = entityManager.createQuery(
            "SELECT o FROM Oficina o " +
            "JOIN FETCH o.predio p " +
            "JOIN FETCH p.entidad e " +
            "WHERE o.estado = 'ACTIVO'", Oficina.class)
            .getResultList();

        return oficinas.stream().collect(Collectors.toMap(
            o -> o.getPredio().getEntidad().getEntidadCodigo() + "|" +
                 o.getPredio().getUnidad() + "|" + o.getCodOfi(),
            o -> o,
            (a, b) -> a
        ));
    }

    @Transactional(readOnly = true)
    public Map<String, Responsable> cargarResponsablesCache() {
        List<Responsable> responsables = entityManager.createQuery(
            "SELECT r FROM Responsable r " +
            "JOIN FETCH r.oficina o " +
            "WHERE r.estado = 'ACTIVO' AND r.codigoFuncionario IS NOT NULL", Responsable.class)
            .getResultList();

        return responsables.stream().collect(Collectors.toMap(
            r -> r.getOficina().getIdOficina() + "|" + r.getCodigoFuncionario().trim(),
            r -> r,
            (a, b) -> a
        ));
    }

    @Transactional(readOnly = true)
    public Map<Integer, GrupoContable> cargarGruposCache() {
        return grupoContableService.listarGruposContables().stream()
            .filter(g -> g.getCodContable() != null)
            .collect(Collectors.toMap(GrupoContable::getCodContable, g -> g, (a, b) -> a));
    }

    @Transactional(readOnly = true)
    public Map<String, Auxiliar> cargarAuxiliaresCache() {
        List<Auxiliar> auxiliares = entityManager.createQuery(
            "SELECT a FROM Auxiliar a " +
            "JOIN FETCH a.predio p " +
            "JOIN FETCH a.grupoContable g " +
            "WHERE a.estado = 'ACTIVO'", Auxiliar.class)
            .getResultList();

        return auxiliares.stream().collect(Collectors.toMap(
            a -> a.getPredio().getIdPredio() + "|" +
                 a.getGrupoContable().getIdGrupoContable() + "|" + a.getCodAux(),
            a -> a,
            (a, b) -> a
        ));
    }

    @Transactional(readOnly = true)
    public Map<String, OrganismoFinanciero> cargarOrganismosCache() {
        return organismoFinancieroService.findAll().stream()
            .filter(o -> o.getCodOf() != null)
            .collect(Collectors.toMap(o -> o.getCodOf().trim(), o -> o, (a, b) -> a));
    }

    @Transactional(readOnly = true)
    public Map<String, Activo> cargarActivosCache() {
        // Solo campos necesarios para hash, sin joins costosos
        List<Activo> activos = entityManager.createQuery(
            "SELECT a FROM Activo a WHERE a.estado <> 'ELIMINADO' AND a.codigo IS NOT NULL",
            Activo.class)
            .getResultList();

        return activos.stream().collect(Collectors.toMap(
            Activo::getCodigo, a -> a, (a, b) -> a
        ));
    }
}