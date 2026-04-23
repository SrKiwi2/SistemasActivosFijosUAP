package com.usic.SistemasActivosFijosUAP.model.ServiceImpl;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import com.usic.SistemasActivosFijosUAP.interoperabilidad.JavaDbfService;
import com.usic.SistemasActivosFijosUAP.model.IService.IActivoService;
import com.usic.SistemasActivosFijosUAP.model.IService.IAuxiliarService;
import com.usic.SistemasActivosFijosUAP.model.IService.IGrupoContableService;
import com.usic.SistemasActivosFijosUAP.model.IService.IOficinaService;
import com.usic.SistemasActivosFijosUAP.model.IService.IPersonaService;
import com.usic.SistemasActivosFijosUAP.model.IService.IPredioServicio;
import com.usic.SistemasActivosFijosUAP.model.IService.IResponsableService;
import com.usic.SistemasActivosFijosUAP.model.IService.ITransferenciaLondraService;
import com.usic.SistemasActivosFijosUAP.model.dao.IAuxiliarDao;
import com.usic.SistemasActivosFijosUAP.model.dao.IPersonasDao;
import com.usic.SistemasActivosFijosUAP.model.dao.IResposableDao;
import com.usic.SistemasActivosFijosUAP.model.dao.ITransferenciaCabeceraDao;
import com.usic.SistemasActivosFijosUAP.model.dao.ITransferenciaLondraDao;
import com.usic.SistemasActivosFijosUAP.model.dto.interoperabilidad.SolTransferenciaDbf;
import com.usic.SistemasActivosFijosUAP.model.dto.interoperabilidad.TransferenciaValidadaDto;
import com.usic.SistemasActivosFijosUAP.model.dto.transferencia.TransferenciaActivoDetalleDto;
import com.usic.SistemasActivosFijosUAP.model.dto.transferencia.TransferenciaAgrupadaDto;
import com.usic.SistemasActivosFijosUAP.model.entity.Activo;
import com.usic.SistemasActivosFijosUAP.model.entity.Auxiliar;
import com.usic.SistemasActivosFijosUAP.model.entity.GrupoContable;
import com.usic.SistemasActivosFijosUAP.model.entity.Oficina;
import com.usic.SistemasActivosFijosUAP.model.entity.Persona;
import com.usic.SistemasActivosFijosUAP.model.entity.Predio;
import com.usic.SistemasActivosFijosUAP.model.entity.Responsable;
import com.usic.SistemasActivosFijosUAP.model.entity.TransferenciaCabecera;
import com.usic.SistemasActivosFijosUAP.model.entity.TransferenciaDetalleLondra;
import com.usic.SistemasActivosFijosUAP.util.NombreParser;

import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransferenciaLondraService implements ITransferenciaLondraService {

    private final JavaDbfService         dbfService;
    private final ITransferenciaLondraDao transferenciaRepo;
    private final IPredioServicio        predioServicio;
    private final IOficinaService        oficinaService;
    private final IResponsableService    responsableService;
    private final IActivoService         activoService;
    private final IGrupoContableService grupoContableService;
    private final ITransferenciaCabeceraDao cabeceraRepo;
    private final IAuxiliarDao auxiliarRepository;
    private final IAuxiliarService auxiliarService;
    private final IPersonasDao personaDao;
    private final IResposableDao responsableDao;

    private final RestTemplate restTemplate;

    @Value("${londra.callback.url}")
    private String londraCallbackUrl;

    @Value("${londra.callback.api-key}")
    private String londraApiKey;

    @Value("${legacy.dbf.transferencias.path}")
    private String transferenciasPath;

    private static final Set<String> ESTADOS_PENDIENTES = Set.of(
        "ENVIADO", "PENDIENTE", "PEND", "P", "0"
    );

    @Builder @Getter
    private static class ContextoDestino {
        private final Predio      predioD;
        private final Oficina     oficinaD;
        private final Responsable responsableD;
        private final Short       codRespDbf;
    }

    // =========================================================================
    //  aprobar() — COMPLETAMENTE REFACTORIZADO
    // =========================================================================
    @Override
    @Transactional
    public TransferenciaCabecera aprobar(String corrT, String usuarioNombre) throws Exception {

        // ── 1. Guardia: duplicado ─────────────────────────────────────────────
        if (cabeceraRepo.existsByCorrT(corrT)) {
            throw new IllegalStateException(
                "La transferencia '" + corrT + "' ya fue aprobada anteriormente");
        }

        // ── 2. Leer grupo del DBF ─────────────────────────────────────────────
        List<SolTransferenciaDbf> grupo = dbfService
            .listarSolTransferenciasAll(Path.of(transferenciasPath), null)
            .stream()
            .filter(f -> corrT.equalsIgnoreCase(f.getCorrT()))
            .collect(Collectors.toList());

        if (grupo.isEmpty()) {
            throw new IllegalArgumentException(
                "No se encontró ningún activo con CORR_T=" + corrT);
        }

        SolTransferenciaDbf primero = grupo.get(0);

        // ── 3. Tipo: interna vs externa ───────────────────────────────────────
        boolean esExterna = !NombreParser.normalizar(primero.getUnidadO())
            .equals(NombreParser.normalizar(primero.getUnidadD()));

        log.info("📋 Aprobando transferencia corrT={} tipo={} activos={}",
            corrT, esExterna ? "EXTERNA" : "INTERNA", grupo.size());

        // ── 4. Validación previa (fail-fast) ──────────────────────────────────
        Map<String, String> cacheAux = cargarCacheNombresAuxiliar(grupo);
        List<String> erroresValidacion = new ArrayList<>();
        for (SolTransferenciaDbf f : grupo) {
            TransferenciaActivoDetalleDto v = validarEnriquecido(f, cacheAux);
            if (!v.isValida()) {
                erroresValidacion.add("• " + f.getCodigoO() + ": "
                    + String.join(", ", v.getErrores()));
            }
        }
        if (!erroresValidacion.isEmpty()) {
            throw new IllegalStateException(
                "No se puede aprobar. Errores:\n" + String.join("\n", erroresValidacion));
        }

        // ── 5. Resolver contexto destino (predio → oficina → responsable) ─────
        //    ESTE BLOQUE CORRIGE EL BUG PRINCIPAL
        ContextoDestino contextoD = resolverContextoDestino(primero, usuarioNombre);

        // ── 6. Resolver Auxiliar destino por cada activo (solo EXTERNA) ───────────
        Map<String, Auxiliar> auxiliarDestinoMap = new HashMap<>();
        if (esExterna) {
            for (SolTransferenciaDbf f : grupo) {
                Auxiliar auxD = resolverOCrearAuxiliarDestino(
                    f, contextoD.getPredioD(), usuarioNombre);
                auxiliarDestinoMap.put(f.getCodigoO(), auxD);
            }
        }

        // ── 7. Resolver predio origen (para obtener entidadCodigo) ────────────
        Predio predioO = predioServicio
            .findByUnidadIgnoreCase(primero.getUnidadO())
            .orElseThrow(() -> new IllegalStateException(
                "Predio origen no encontrado: " + primero.getUnidadO()));

        // ── 8. Persistir cabecera + detalles en PostgreSQL ────────────────────
        TransferenciaCabecera cabecera = construirYPersistirCabecera(
            corrT, grupo, primero, esExterna, usuarioNombre);

        // ── 9. Actualizar ACTUAL.DBF + BD para cada activo ────────────────────────
        List<String> erroresDbf = new ArrayList<>();

        for (SolTransferenciaDbf f : grupo) {
            try {
                String  unidadFinal   = esExterna ? primero.getUnidadD() : primero.getUnidadO();
                Auxiliar auxiliarFinal = esExterna
                    ? auxiliarDestinoMap.get(f.getCodigoO())   // ← del mapa, NO llamar otro método
                    : null;
                Short codAuxFinal = auxiliarFinal != null
                    ? auxiliarFinal.getCodAux()
                    : f.getCodAuxO();

                // ── 9a. Actualizar ACTUAL.DBF ──────────────────────────────────────
                dbfService.actualizarActivoParaTransferencia(
                    f.getCodigoO(),
                    predioO.getEntidad().getEntidadCodigo(),
                    f.getUnidadO(),
                    unidadFinal,
                    primero.getCodOficD(),
                    contextoD.getCodRespDbf(),
                    codAuxFinal,
                    LocalDate.now(),
                    usuarioNombre
                );
                log.info("✅ ACTUAL.DBF — activo={} actualizado", f.getCodigoO());

                // ── 9b. Actualizar entidad Activo en PostgreSQL ────────────────────
                actualizarActivoEnBd(
                    f.getCodigoO(),
                    contextoD.getOficinaD(),
                    contextoD.getResponsableD(),
                    auxiliarFinal,          // ← null para interna, entidad para externa
                    esExterna,
                    LocalDate.now(),
                    usuarioNombre
                );
                log.info("✅ PostgreSQL — activo={} actualizado en BD", f.getCodigoO());

            } catch (Exception e) {
                erroresDbf.add("• " + f.getCodigoO() + ": " + e.getMessage());
                log.error("❌ Error procesando activo {}: {}", f.getCodigoO(), e.getMessage(), e);
            }
        }

        // ── 10. Marcar APROBADO en sol_transferencias.DBF ─────────────────────
        try {
            dbfService.actualizarEstadoTransferenciaDbf(
                Path.of(transferenciasPath), corrT, "APROBADO");
        } catch (Exception e) {
            log.error("❌ Error marcando APROBADO en sol_transferencias.DBF: {}", e.getMessage());
        }

         // ── 11. Notificar a Londra (VSIAF) ────────────────────────────────────
        notificarLondra(
            cabecera.getIdCabecera(),
            String.format("Transferencia %s aprobada — %d activo(s) procesado(s) por %s",
                corrT, grupo.size(), usuarioNombre)
        );

        if (!erroresDbf.isEmpty()) {
            log.warn("⚠️ Transferencia {} con {}/{} error(es) en ACTUAL.DBF:\n{}",
                corrT, erroresDbf.size(), grupo.size(), String.join("\n", erroresDbf));
        }

        log.info("✅ Transferencia {} ({}) completada — {} activo(s) por {}",
            corrT, esExterna ? "EXTERNA" : "INTERNA", grupo.size(), usuarioNombre);

        return cabecera;
    }

    // =========================================================================
    //  RESOLUCIÓN DEL CONTEXTO DESTINO
    // =========================================================================

    /**
     * Centraliza la resolución de: predio destino → oficina destino → responsable receptor.
     * Solo hace esto UNA vez para todo el grupo (no por cada activo).
     */
    private ContextoDestino resolverContextoDestino(
            SolTransferenciaDbf primero,
            String usuarioNombre) {

        // ── Predio destino ────────────────────────────────────────────────────
        Predio predioD = predioServicio
            .findByUnidadIgnoreCase(primero.getUnidadD())
            .orElseThrow(() -> new IllegalStateException(
                "Predio destino no encontrado: " + primero.getUnidadD()));

        // ── Oficina destino ───────────────────────────────────────────────────
        Oficina oficinaD = oficinaService
            .findByCodOfiAndPredio(primero.getCodOficD(), predioD)
            .orElseThrow(() -> new IllegalStateException(String.format(
                "Oficina codOfi=%d no existe en predio '%s'",
                primero.getCodOficD(), predioD.getUnidad())));

        // ── Responsable receptor ──────────────────────────────────────────────
        Responsable respD = resolverOCrearResponsableDestino(
            primero.getCiRecep(),
            primero.getNomRecep(),
            oficinaD,
            usuarioNombre
        );

        Short codRespDbf = parsearCodigoFuncionario(respD);

        log.info("📍 Contexto destino resuelto — predio={} | oficina={}(cod={}) | "
            + "receptor='{}' (CI={}) | codigoFuncionario→CODRESP={}",
            predioD.getUnidad(),
            oficinaD.getNombre(), oficinaD.getCodOfi(),
            respD.getPersona() != null ? respD.getPersona().getNombreCompleto() : "?",
            primero.getCiRecep(),
            codRespDbf);

        return ContextoDestino.builder()
            .predioD(predioD)
            .oficinaD(oficinaD)
            .responsableD(respD)
            .codRespDbf(codRespDbf)
            .build();
    }

    /**
     * Busca la oficina en el predio destino por codOfi.
     * Si no existe (sistema fuera de sincronía), lanza excepción descriptiva.
     */
    private Oficina resolverOficinaDestino(Short codOficD, Predio predioD) {
        return oficinaService.findByCodOfiAndPredio(codOficD, predioD)
            .orElseThrow(() -> new IllegalStateException(String.format(
                "Oficina codOfi=%d no existe en predio '%s' — verifique sincronización de OFICINA.DBF",
                codOficD, predioD.getUnidad())));
    }

    /**
     * Resuelve el Responsable receptor en la oficina destino.
     *
     * Flujo:
     *  1. Buscar si ya tiene vínculo en ESA oficina destino (caso ideal)
     *  2. Buscar la Persona por CI
     *  3. Verificar si tiene vínculo por Persona (CI pudo haber variado en el DBF)
     *  4. Si no hay vínculo → crearlo con siguiente codigoFuncionario de esa oficina
     */
    private Responsable resolverOCrearResponsableDestino(
            String ciRecep,
            String nomRecepReferencia,
            Oficina oficinaD,
            String usuarioNombre) {

        String ciLimpio = ciRecep != null ? ciRecep.trim() : null;

        // ── Paso 1: ya tiene vínculo directo (CI + oficina destino) ──────────────
        // Es el caso más común en transferencias internas repetidas
        if (ciLimpio != null && !ciLimpio.isBlank()) {
            Optional<Responsable> yaVinculado =
                responsableDao.findByOficinaAndPersonaCi(oficinaD, ciLimpio);

            if (yaVinculado.isPresent()) {
                Responsable r = yaVinculado.get();
                log.info("✅ Paso 1 — receptor CI={} ya vinculado en oficina={}(cod={}) | "
                    + "codigoFuncionario={}",
                    ciLimpio, oficinaD.getNombre(), oficinaD.getCodOfi(),
                    r.getCodigoFuncionario());
                return r;
            }
        }

        // ── Paso 2: buscar Persona por CI ─────────────────────────────────────────
        Persona persona = resolverPersonaReceptora(ciLimpio, nomRecepReferencia);

        // ── Paso 3: puede que ya tenga vínculo pero con CI levemente diferente ────
        // (ej. "4741353" vs "4741353 LP" — mismo CI, diferente formato en DBF)
        Optional<Responsable> vinculoPorPersona =
            responsableDao.findByOficinaAndPersona(oficinaD, persona);

        if (vinculoPorPersona.isPresent()) {
            Responsable r = vinculoPorPersona.get();
            log.info("✅ Paso 3 — receptor encontrado por Persona id={} en oficina={}(cod={}) | "
                + "codigoFuncionario={}",
                persona.getIdPersona(), oficinaD.getNombre(),
                oficinaD.getCodOfi(), r.getCodigoFuncionario());
            return r;
        }

        // ── Paso 4: no existe vínculo → crear ─────────────────────────────────────
        log.info("📋 Paso 4 — receptor CI={} ('{}') sin vínculo en oficina={}(cod={}) → creando",
            ciLimpio, nomRecepReferencia,
            oficinaD.getNombre(), oficinaD.getCodOfi());

        return crearVinculoResponsable(persona, oficinaD, usuarioNombre);
    }

    /**
     * Busca la Persona receptora ÚNICAMENTE por CI.
     *
     * Si no se encuentra, lanza excepción descriptiva con los datos del registro
     * para que el operador pueda identificar el problema en el sistema.
     *
     * NO se usa el nombre para buscar — el CI es el identificador único confiable.
     */
    private Persona resolverPersonaReceptora(String ciRecep, String nomRecepReferencia) {

        if (ciRecep == null || ciRecep.isBlank()) {
            throw new IllegalStateException(
                "El registro DBF no tiene CI_RECEP — nombre de referencia: '"
                + nomRecepReferencia + "'. "
                + "Verifique el archivo sol_transferencias.DBF.");
        }

        String ciLimpio = ciRecep.trim();

        return personaDao.findByCi(ciLimpio)
            .orElseThrow(() -> new IllegalStateException(String.format(
                "No se encontró Persona activa con CI='%s' (receptor: '%s'). %n"
                + "Causas posibles: %n"
                + "  1. La persona no está sincronizada en el sistema. %n"
                + "  2. El CI en sol_transferencias.DBF difiere del registrado en BD. %n"
                + "  3. La persona existe pero su estado no es ACTIVO. %n"
                + "Acción: verifique en Gestión de Personas → CI=%s.",
                ciLimpio, nomRecepReferencia, ciLimpio)));
    }

    /**
     * Crea el vínculo Responsable para una Persona en la oficina destino,
     * asignando el siguiente codigoFuncionario correlativo de ESA oficina.
     */
    private Responsable crearVinculoResponsable(
        Persona persona,
        Oficina oficinaD,
        String usuarioNombre) {

        String nextCodFunc = calcularNextCodigoFuncionario(oficinaD.getIdOficina());

        log.info("  → Persona: '{}' CI={} id={}",
            persona.getNombreCompleto(), persona.getCi(), persona.getIdPersona());
        log.info("  → Oficina: '{}' cod={} predio={}",
            oficinaD.getNombre(), oficinaD.getCodOfi(),
            oficinaD.getPredio() != null ? oficinaD.getPredio().getUnidad() : "?");
        log.info("  → codigoFuncionario asignado: {}", nextCodFunc);

        Responsable nuevo = new Responsable();
        nuevo.setPersona(persona);
        nuevo.setOficina(oficinaD);
        nuevo.setCodigoFuncionario(nextCodFunc);
        nuevo.setFechaUlt(LocalDate.now());
        nuevo.setUsuario(usuarioNombre);
        nuevo.setEstado("ACTIVO");

        // Heredar cargo de cualquier responsable existente de esta persona
        responsableDao.findAllByPersonaIdPersona(persona.getIdPersona())
            .stream()
            .filter(r -> r.getCargo() != null)
            .findFirst()
            .ifPresent(r -> nuevo.setCargo(r.getCargo()));

        Responsable guardado = responsableService.save(nuevo);
        log.info("  → PostgreSQL: Responsable id={} guardado ✅", guardado.getIdResponsable());

        // Sincronizar con RESP.DBF
        try {
            dbfService.upsertResponsableDesdeEntidad(guardado);
            log.info("  → RESP.DBF: sincronizado CODOFIC={} CODRESP={} ✅",
                oficinaD.getCodOfi(), nextCodFunc);
        } catch (Exception e) {
            log.error("  → RESP.DBF: ERROR al sincronizar — BD actualizada pero DBF no: {}",
                e.getMessage(), e);
        }

        return guardado;
    }

    // =========================================================================
    //  RESOLUCIÓN DEL AUXILIAR DESTINO (solo para EXTERNA)
    // =========================================================================

    /**
     * Retorna la entidad Auxiliar en el predio destino.
     * La crea si no existe.
     */
    private Auxiliar resolverOCrearAuxiliarDestino(
            SolTransferenciaDbf f,
            Predio predioD,
            String usuarioNombre) {

        Auxiliar auxOrigen = auxiliarRepository
            .findByUnidadGrupoAndCodAux(
                f.getUnidadO().trim(),
                f.getCodContO().intValue(),
                f.getCodAuxO())
            .orElseThrow(() -> new IllegalStateException(String.format(
                "Auxiliar origen no encontrado — unidad='%s' codCont=%d codAux=%d",
                f.getUnidadO(), f.getCodContO(), f.getCodAuxO())));

        GrupoContable grupo = auxOrigen.getGrupoContable();

        // Buscar en destino por grupo + nombre
        Optional<Auxiliar> enDestino = auxiliarRepository
            .findByPredioIdPredioAndGrupoContableIdGrupoContableAndNombreIgnoreCase(
                predioD.getIdPredio(),
                grupo.getIdGrupoContable(),
                auxOrigen.getNombre());

        if (enDestino.isPresent()) {
            log.debug("✅ Auxiliar '{}' ya existe en predio '{}' — codAux={}",
                auxOrigen.getNombre(), predioD.getUnidad(),
                enDestino.get().getCodAux());
            return enDestino.get(); // ← retorna entidad completa
        }

        // Crear nuevo
        Integer maxActual = auxiliarRepository.findMaxCodAux(
            predioD.getIdPredio(), grupo.getIdGrupoContable());
        short nextCodAux = (short)((maxActual == null ? 0 : maxActual) + 1);

        log.info("📋 Creando Auxiliar '{}' en predio='{}' codAux={}",
            auxOrigen.getNombre(), predioD.getUnidad(), nextCodAux);

        Auxiliar nuevoAux = new Auxiliar();
        nuevoAux.setPredio(predioD);
        nuevoAux.setGrupoContable(grupo);
        nuevoAux.setCodAux(nextCodAux);
        nuevoAux.setNombre(auxOrigen.getNombre());
        nuevoAux.setObserv(auxOrigen.getObserv());
        nuevoAux.setFechaUlt(LocalDate.now());
        nuevoAux.setUsuario(usuarioNombre);
        nuevoAux.setEstado("ACTIVO");

        Auxiliar savedAux = auxiliarService.save(nuevoAux);

        try {
            dbfService.upsertAuxiliarDesdeEntidad(savedAux);
            log.info("✅ AUXILIAR.DBF sincronizado codAux={}", nextCodAux);
        } catch (Exception e) {
            log.error("❌ AUXILIAR.DBF no sincronizado: {}", e.getMessage(), e);
        }

        return savedAux; // ← retorna entidad completa
    }

    // =========================================================================
    //  HELPERS PRIVADOS
    // =========================================================================

    /**
     * MAX(codigoFuncionario) + 1 para la oficina dada.
     * REGLA: correlativo POR oficina. Si la oficina 28 tiene [1,2,3], el siguiente es 4.
     * Si esa misma persona luego entra a oficina 10 que tiene [1..10], su código allí es 11.
     */
    private String calcularNextCodigoFuncionario(Long idOficina) {
        List<Responsable> existentes = responsableDao.findByOficinaIdOficina(idOficina);

        int max = existentes.stream()
            .map(Responsable::getCodigoFuncionario)
            .filter(Objects::nonNull)
            .mapToInt(s -> {
                try { return Integer.parseInt(s.trim()); }
                catch (NumberFormatException e) { return 0; }
            })
            .max()
            .orElse(0);

        String siguiente = String.valueOf(max + 1);

        log.debug("  calcularNextCodigoFuncionario: oficina={} | existentes={} | max={} | siguiente={}",
            idOficina, existentes.size(), max, siguiente);

        return siguiente;
    }

    /**
     * Convierte codigoFuncionario (String) → Short para escribir en ACTUAL.DBF.
     */
    private Short parsearCodigoFuncionario(Responsable r) {
        String codFunc = r.getCodigoFuncionario();
        if (codFunc == null || codFunc.isBlank()) {
            throw new IllegalStateException(String.format(
                "Responsable id=%d no tiene codigoFuncionario — oficina=%s",
                r.getIdResponsable(),
                r.getOficina() != null ? r.getOficina().getCodOfi() : "?"));
        }
        try {
            return Short.parseShort(codFunc.trim());
        } catch (NumberFormatException e) {
            throw new IllegalStateException(
                "codigoFuncionario='" + codFunc + "' no es numérico para "
                + "Responsable id=" + r.getIdResponsable(), e);
        }
    }

    private TransferenciaCabecera construirYPersistirCabecera(
            String corrT,
            List<SolTransferenciaDbf> grupo,
            SolTransferenciaDbf primero,
            boolean esExterna,
            String usuarioNombre) {

        TransferenciaCabecera cabecera = TransferenciaCabecera.builder()
            .corrT(corrT)
            .nombreT(primero.getNombreT())
            .fechaT(primero.getFechaT())
            .estadoTDbf(primero.getEstadoT())
            .unidadO(primero.getUnidadO())
            .unidadD(primero.getUnidadD())
            .codOficO(primero.getCodOficO())
            .ciSolicitante(primero.getCiSolO())
            .codOficD(primero.getCodOficD())
            .ciReceptor(primero.getCiRecep())
            .nomReceptor(primero.getNomRecep())
            .tipo(esExterna
                ? TransferenciaValidadaDto.TipoTransferencia.EXTERNA
                : TransferenciaValidadaDto.TipoTransferencia.INTERNA)
            .estado(TransferenciaCabecera.EstadoTransferencia.APROBADO)
            .fechaAprobacion(LocalDateTime.now())
            .usuarioAprobacion(usuarioNombre)
            .build();

        for (SolTransferenciaDbf f : grupo) {
            cabecera.getDetalles().add(
                TransferenciaDetalleLondra.builder()
                    .cabecera(cabecera)
                    .idTDbf(f.getIdT())
                    .codigoActivo(f.getCodigoO())
                    .codContO(f.getCodContO())
                    .codAuxO(f.getCodAuxO())
                    .estadoActivoO(f.getEstadoO())
                    .codOficO(f.getCodOficO())
                    .codRespO(f.getCodRespO())
                    .estadoDetalle(TransferenciaDetalleLondra.EstadoActivo.APROBADO)
                    .build()
            );
        }

        cabeceraRepo.save(cabecera);
        log.info("✅ PostgreSQL — {} detalles persistidos para corrT={}",
            grupo.size(), corrT);
        return cabecera;
    }

    // =========================================================================
    //  MÉTODOS DE LECTURA/VALIDACIÓN (sin cambios estructurales)
    // =========================================================================

    private boolean esPendiente(String estadoT) {
        if (estadoT == null) return false;
        return ESTADOS_PENDIENTES.contains(estadoT.trim().toUpperCase());
    }

    @Override
    public long contarPendientesEnDbf() {
        try {
            return dbfService.listarSolTransferenciasAll(Path.of(transferenciasPath), null)
                .stream()
                .filter(f -> esPendiente(f.getEstadoT()))  // ← mismo cambio
                .count();
        } catch (Exception e) {
            log.warn("No se pudo contar pendientes: {}", e.getMessage());
            return 0L;
        }
    }

    @Override
    public List<TransferenciaActivoDetalleDto> leerYValidarPendientes() {
        try {
            List<SolTransferenciaDbf> filas = dbfService
                .listarSolTransferenciasAll(Path.of(transferenciasPath), null);
            Map<String, String> cache = cargarCacheNombresAuxiliar(filas);
            return filas.stream()
                .filter(f -> esPendiente(f.getEstadoT()))
                .map(f -> validarEnriquecido(f, cache))
                .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("No se pudo leer sol_transferencias.dbf: {}", e.getMessage());
            return List.of();
        }
    }

    @Override
    public List<TransferenciaAgrupadaDto> leerYValidarAgrupado() {
        List<SolTransferenciaDbf> filas;
        try {
            filas = dbfService.listarSolTransferenciasAll(Path.of(transferenciasPath), null);
        } catch (Exception e) {
            log.error("No se pudo leer sol_transferencias.dbf: {}", e.getMessage());
            return List.of();
        }

        List<SolTransferenciaDbf> pendientes = filas.stream()
            .filter(f -> esPendiente(f.getEstadoT()))
            .collect(Collectors.toList());

        if (pendientes.isEmpty()) return List.of();

        Map<String, String> cacheAux = cargarCacheNombresAuxiliar(pendientes);

        return pendientes.stream()
            .map(f -> validarEnriquecido(f, cacheAux))
            .collect(Collectors.groupingBy(
                dto -> dto.getDatos().getCorrT() != null
                    ? dto.getDatos().getCorrT() : "SIN_CORR",
                LinkedHashMap::new, Collectors.toList()
            ))
            .entrySet().stream()
            .map(e -> construirGrupo(e.getKey(), e.getValue()))
            .collect(Collectors.toList());
    }

    private TransferenciaActivoDetalleDto validarEnriquecido(
        SolTransferenciaDbf f,
        Map<String, String> cacheAuxiliar) {

        List<String> errores = new ArrayList<>();

        boolean mismaUnidad = f.getUnidadO() != null &&
                            f.getUnidadO().equalsIgnoreCase(f.getUnidadD());
        var tipo = mismaUnidad
            ? TransferenciaValidadaDto.TipoTransferencia.INTERNA
            : TransferenciaValidadaDto.TipoTransferencia.EXTERNA;

        boolean activoOk  = false;
        boolean predioOOk = false;
        boolean oficOOk   = false;
        boolean respOOk   = false;
        boolean predioD   = false;
        boolean oficDOk   = false;
        boolean respDOk   = false;

        String descripcionActivo    = null;
        String nombreGrupoContable  = null;
        String nombreAuxiliar       = null;
        String nombreOficinaOrigen  = null;
        String nomRespOrigen        = null;
        String ciRespOrigen         = null;

        // ── Activo ───────────────────────────────────────────────────────────────
        try {
            var activoOpt = activoService.findByCodigo(f.getCodigoO());
            activoOk = activoOpt.isPresent();
            if (activoOk) {
                descripcionActivo = activoOpt.get().getDescripcion(); // ⚠️ adaptar getter
            } else {
                errores.add("Activo '" + f.getCodigoO() + "' no existe en BD");
            }
        } catch (Exception e) {
            errores.add("Error buscando activo: " + e.getMessage());
        }

        // ── Grupo contable ───────────────────────────────────────────────────────
        if (f.getCodContO() != null) {
            try {
                nombreGrupoContable = grupoContableService.findByCodContable(f.getCodContO().intValue())
                    .map(g -> g.getNombre()) // Extrae el nombre si el grupo existe
                    .orElse(nombreGrupoContable); // Si no existe, mantiene el valor que ya tenía
            } catch (Exception e) {
                log.debug("Grupo contable no resuelto: {}", f.getCodContO());
            }
        }

        // ── Auxiliar desde caché (O(1), sin query) ───────────────────────────────
        if (f.getUnidadO() != null && f.getCodContO() != null && f.getCodAuxO() != null) {
            String clave = f.getUnidadO().trim().toLowerCase()
                        + "|" + f.getCodContO().intValue()
                        + "|" + f.getCodAuxO();
            nombreAuxiliar = cacheAuxiliar.get(clave);
        }

        // ── Predio origen ────────────────────────────────────────────────────────
        Optional<Predio> predioOpt = Optional.empty();
        try {
            predioOpt = predioServicio.findByUnidadIgnoreCase(f.getUnidadO());
            predioOOk = predioOpt.isPresent();
            if (!predioOOk) errores.add("Unidad origen '" + f.getUnidadO() + "' no existe");
        } catch (Exception e) {
            errores.add("Error buscando predio origen: " + e.getMessage());
        }

        // ── Oficina + Responsable ORIGEN (por codigoFuncionario) ─────────────────
        if (predioOOk && f.getCodOficO() != null) {
            String[] resuelto = resolverOficinaYResponsable(
                f.getUnidadO(),
                f.getCodOficO(),
                f.getCodRespO() != null ? String.valueOf(f.getCodRespO()) : null,
                false   // false = buscar por codigoFuncionario
            );

            nombreOficinaOrigen = resuelto[0];
            nomRespOrigen       = resuelto[1];
            ciRespOrigen        = resuelto[2];

            oficOOk = (nombreOficinaOrigen != null);
            respOOk = (nomRespOrigen != null);

            if (!oficOOk) errores.add("Oficina origen " + f.getCodOficO()
                                    + " no existe en unidad " + f.getUnidadO());
            if (oficOOk && !respOOk)
                errores.add("Responsable origen " + f.getCodRespO() + " no existe");
        }

        // ── Predio destino ───────────────────────────────────────────────────────
        Optional<Predio> predioDOpt = Optional.empty();
        try {
            predioDOpt = predioServicio.findByUnidadIgnoreCase(f.getUnidadD());
            predioD    = predioDOpt.isPresent();
            if (!predioD) errores.add("Unidad destino '" + f.getUnidadD() + "' no existe");
        } catch (Exception e) {
            errores.add("Error buscando predio destino: " + e.getMessage());
        }

        // ── Oficina destino ──────────────────────────────────────────────────────
        if (predioD && f.getCodOficD() != null) {
            String[] resueltoD = resolverOficinaYResponsable(
                f.getUnidadD(),
                f.getCodOficD(),
                null,   // oficina destino no necesita responsable aquí
                false
            );
            oficDOk = (resueltoD[0] != null);
            if (!oficDOk) errores.add("Oficina destino " + f.getCodOficD()
                                    + " no existe en unidad " + f.getUnidadD());
        }

        // ── Receptor por CI + oficina destino ────────────────────────────────────
        if (f.getCiRecep() != null && !f.getCiRecep().isBlank()) {
            try {
                respDOk = responsableService.existsByPersonaCi(f.getCiRecep());
                if (!respDOk) errores.add("CI receptor '" + f.getCiRecep() + "' no registrado");
            } catch (Exception e) {
                errores.add("Error buscando receptor: " + e.getMessage());
            }
        } else {
            respDOk = true;
        }

        boolean yaAprobada = transferenciaRepo.existsByCorrT(f.getCorrT());
        boolean valida     = errores.isEmpty()
            && activoOk && predioOOk && oficOOk && respOOk
            && predioD  && oficDOk   && !yaAprobada;

        return TransferenciaActivoDetalleDto.builder()
            .datos(f)
            .tipo(tipo)
            .valida(valida)
            .errores(errores)
            .activoExiste(activoOk)
            .predioOrigenExiste(predioOOk)
            .oficinaOrigenExiste(oficOOk)
            .responsableOrigenExiste(respOOk)
            .predioDestinoExiste(predioD)
            .oficinaDestinoExiste(oficDOk)
            .responsableDestinoExiste(respDOk)
            .yaAprobadaEnBd(yaAprobada)
            .descripcionActivo(descripcionActivo)
            .nombreGrupoContable(nombreGrupoContable)
            .nombreAuxiliar(nombreAuxiliar)
            .nombreOficinaOrigen(nombreOficinaOrigen)
            .nombreResponsableOrigen(nomRespOrigen)
            .ciResponsableOrigen(ciRespOrigen != null ? ciRespOrigen : f.getCiSolO())
            .build();
    }

    private TransferenciaAgrupadaDto construirGrupo(
        String corrT,
        List<TransferenciaActivoDetalleDto> activos) {

        SolTransferenciaDbf primero = activos.get(0).getDatos();
        boolean mismaUnidad = primero.getUnidadO() != null &&
                            primero.getUnidadO().equalsIgnoreCase(primero.getUnidadD());

        // Origen — tomar del primer activo que lo resolvió
        String nomOficO = activos.stream()
            .map(TransferenciaActivoDetalleDto::getNombreOficinaOrigen)
            .filter(Objects::nonNull).findFirst().orElse(null);
        String nomRespO = activos.stream()
            .map(TransferenciaActivoDetalleDto::getNombreResponsableOrigen)
            .filter(Objects::nonNull).findFirst().orElse(null);
        String ciRespO  = activos.stream()
            .map(TransferenciaActivoDetalleDto::getCiResponsableOrigen)
            .filter(Objects::nonNull).findFirst().orElse(null);

        // Destino — resolver aquí usando CI_RECEP + CODOFIC_D + UNIDAD_D
        String[] resueltoD = resolverOficinaYResponsable(
            primero.getUnidadD(),
            primero.getCodOficD(),
            primero.getCiRecep(),
            true   // true = buscar por CI (destino)
        );

        return TransferenciaAgrupadaDto.builder()
            .corrT(corrT)
            .nombreT(primero.getNombreT())
            .fechaT(primero.getFechaT())
            .estadoT(primero.getEstadoT())
            .unidadO(primero.getUnidadO())
            .codOficO(primero.getCodOficO())
            .codRespO(primero.getCodRespO())
            .ciSolO(primero.getCiSolO())
            .unidadD(primero.getUnidadD())
            .codOficD(primero.getCodOficD())
            .ciRecep(primero.getCiRecep())
            .nomRecep(primero.getNomRecep())
            .tipo(mismaUnidad
                ? TransferenciaValidadaDto.TipoTransferencia.INTERNA
                : TransferenciaValidadaDto.TipoTransferencia.EXTERNA)
            .activos(activos)
            .nombreOficinaOrigen(nomOficO)
            .nombreResponsableOrigen(nomRespO)
            .ciResponsableOrigen(ciRespO)
            .nombreOficinaDestino(resueltoD[0])
            .nombreResponsableDestino(resueltoD[1])
            .ciResponsableDestino(resueltoD[2])
            .build();
    }

    private Map<String, String> cargarCacheNombresAuxiliar(
        List<SolTransferenciaDbf> filas) {

        // 1. Recopilar todas las unidades origen únicas del lote
        List<String> unidades = filas.stream()
            .map(f -> f.getUnidadO() != null ? f.getUnidadO().trim().toLowerCase() : null)
            .filter(Objects::nonNull)
            .distinct()
            .collect(Collectors.toList());

        if (unidades.isEmpty()) return Map.of();

        // 2. Una sola consulta para todos los auxiliares de esas unidades
        List<Auxiliar> todos = auxiliarRepository.findAllByUnidadesIn(unidades);

        // 3. Construir mapa con clave compuesta
        return todos.stream().collect(Collectors.toMap(
            a -> a.getPredio().getUnidad().trim().toLowerCase()
                + "|" + a.getGrupoContable().getCodContable()
                + "|" + a.getCodAux(),
            Auxiliar::getNombre,
            (existing, duplicate) -> existing   // en caso de colisión, conservar primero
        ));
    }

    // =========================================================================
    //  HELPERS PRIVADOS — al final de la clase, antes del último }
    // =========================================================================

    private void actualizarActivoEnBd(
            String      codigoActivo,
            Oficina     oficinaD,
            Responsable responsableD,
            Auxiliar    auxiliarD,      // null = interna, no tocar auxiliar
            boolean     esExterna,
            LocalDate   fechaUlt,
            String      usuario) {

        Activo activo = activoService.fetchFullByCodigo(codigoActivo)
            .orElseThrow(() -> new IllegalStateException(
                "Activo no encontrado en BD — CODIGO=" + codigoActivo));

        activo.setOficina(responsableD.getOficina() != null
            ? responsableD.getOficina() : oficinaD);
        activo.setResponsable(responsableD);

        if (esExterna && auxiliarD != null) {
            activo.setAuxiliar(auxiliarD);
        }

        activo.setFechaUlt(fechaUlt);
        activo.setUsuario(usuario);
        activo.setFecMod(fechaUlt);
        activo.setUsuMod(usuario);
        activo.setHashDatos(activo.calcularHash());

        activoService.save(activo);

        log.info("  BD Activo id={} codigo={} | oficina='{}' | "
            + "responsable codFunc={} | auxiliar={}",
            activo.getIdActivo(),
            codigoActivo,
            oficinaD.getNombre(),
            responsableD.getCodigoFuncionario(),
            auxiliarD != null ? auxiliarD.getCodAux() : "sin cambio");
    }

    /**
     * Resuelve oficina y responsable dado unidad, codOfic y el identificador
     * del responsable (codigoFuncionario para origen, ci para destino).
     *
     * @param unidad       valor de UNIDAD_O o UNIDAD_D del DBF
     * @param codOfic      CODOFIC_O o CODOFIC_D del DBF
     * @param identificador codigoFuncionario (origen) o CI (destino)
     * @param esPorCi      true = buscar por CI (destino), false = por codigoFuncionario (origen)
     */
    private String[] resolverOficinaYResponsable(
            String unidad, Short codOfic,
            String identificador, boolean esPorCi) {

        // Retorna: [0]=nombreOficina, [1]=nombreResponsable, [2]=ciResponsable
        String[] resultado = {null, null, null};

        if (unidad == null || codOfic == null) return resultado;

        try {
            Optional<Predio> predioOpt =
                predioServicio.findByUnidadIgnoreCase(unidad.trim());
            if (predioOpt.isEmpty()) return resultado;

            Optional<Oficina> oficOpt =
                oficinaService.findByCodOfiAndPredio(codOfic, predioOpt.get());
            if (oficOpt.isEmpty()) return resultado;

            // ⚠️ Adapta al getter real del nombre en tu entidad Oficina
            resultado[0] = oficOpt.get().getNombre();

            if (identificador == null || identificador.isBlank()) return resultado;

            Optional<Responsable> respOpt;
            if (esPorCi) {
                // DESTINO: buscar por CI de persona + oficina
                respOpt = responsableService.findByOficinaAndPersonaCi(
                    oficOpt.get(), identificador.trim());
            } else {
                // ORIGEN: buscar por codigoFuncionario + oficina
                respOpt = responsableService.findByOficinaAndCodigoFuncionario(
                    oficOpt.get(), identificador.trim());
            }

            respOpt.ifPresent(r -> {
                if (r.getPersona() != null) {
                    resultado[1] = r.getPersona().getNombreCompleto();
                    resultado[2] = r.getPersona().getCi();
                }
            });

        } catch (Exception e) {
            log.debug("No se pudo resolver oficina/responsable [{} / {}]: {}",
                unidad, codOfic, e.getMessage());
        }

        return resultado;
    }

    /**
     * Notifica al sistema Londra (VSIAF) que una transferencia fue aprobada.
     * Es BEST-EFFORT: si falla, se loguea pero NO interrumpe el proceso.
     *
     * @param transferenciaId  ID interno de la cabecera guardada en BD
     * @param observacion      mensaje descriptivo
     */
    private void notificarLondra(Long transferenciaId, String observacion) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("x-api-key", londraApiKey);

            Map<String, Object> body = Map.of(
                "TransferenciaId", transferenciaId,
                "Estado",          "FINALIZADO",
                "Observacion",     observacion
            );

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                londraCallbackUrl,
                HttpMethod.POST,
                request,
                String.class
            );

            log.info("✅ Londra notificado — TransferenciaId={} | HTTP {}",
                transferenciaId, response.getStatusCode());

        } catch (Exception e) {
            // No lanzar — la transferencia ya fue aprobada, el callback es secundario
            log.error("❌ Error notificando a Londra — TransferenciaId={} | Error: {}",
                transferenciaId, e.getMessage());
        }
    }
}