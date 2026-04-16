package com.usic.SistemasActivosFijosUAP.model.ServiceImpl;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.usic.SistemasActivosFijosUAP.interoperabilidad.JavaDbfService;
import com.usic.SistemasActivosFijosUAP.model.IService.IActivoService;
import com.usic.SistemasActivosFijosUAP.model.IService.IAuxiliarService;
import com.usic.SistemasActivosFijosUAP.model.IService.IGrupoContableService;
import com.usic.SistemasActivosFijosUAP.model.IService.IOficinaService;
import com.usic.SistemasActivosFijosUAP.model.IService.IPredioServicio;
import com.usic.SistemasActivosFijosUAP.model.IService.IResponsableService;
import com.usic.SistemasActivosFijosUAP.model.IService.ITransferenciaLondraService;
import com.usic.SistemasActivosFijosUAP.model.dao.IAuxiliarDao;
import com.usic.SistemasActivosFijosUAP.model.dao.ITransferenciaCabeceraDao;
import com.usic.SistemasActivosFijosUAP.model.dao.ITransferenciaLondraDao;
import com.usic.SistemasActivosFijosUAP.model.dto.interoperabilidad.SolTransferenciaDbf;
import com.usic.SistemasActivosFijosUAP.model.dto.interoperabilidad.TransferenciaValidadaDto;
import com.usic.SistemasActivosFijosUAP.model.dto.transferencia.TransferenciaActivoDetalleDto;
import com.usic.SistemasActivosFijosUAP.model.dto.transferencia.TransferenciaAgrupadaDto;
import com.usic.SistemasActivosFijosUAP.model.entity.Activo;
import com.usic.SistemasActivosFijosUAP.model.entity.Auxiliar;
import com.usic.SistemasActivosFijosUAP.model.entity.Oficina;
import com.usic.SistemasActivosFijosUAP.model.entity.Predio;
import com.usic.SistemasActivosFijosUAP.model.entity.Responsable;
import com.usic.SistemasActivosFijosUAP.model.entity.Transferencia;
import com.usic.SistemasActivosFijosUAP.model.entity.TransferenciaCabecera;
import com.usic.SistemasActivosFijosUAP.model.entity.TransferenciaDetalle;
import com.usic.SistemasActivosFijosUAP.model.entity.TransferenciaDetalleLondra;
import com.usic.SistemasActivosFijosUAP.model.entity.TransferenciaLondra;

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

    @Value("${legacy.dbf.transferencias.path}")
    private String transferenciasPath;

    private static final Set<String> ESTADOS_PENDIENTES = Set.of(
        "ENVIADO", "PENDIENTE", "PEND", "P", "0"
    );

    private boolean esPendiente(String estadoT) {
        if (estadoT == null) return false;
        return ESTADOS_PENDIENTES.contains(estadoT.trim().toUpperCase());
    }
    
    @Override
    public List<TransferenciaActivoDetalleDto> leerYValidarPendientes() {
        List<SolTransferenciaDbf> filas;
        try {
            filas = dbfService.listarSolTransferenciasAll(
                Path.of(transferenciasPath), null);
        } catch (Exception e) {
            log.error("🌐 No se pudo leer sol_transferencias.dbf: {}", e.getMessage());
            return List.of();
        }

        return filas.stream()
            .filter(f -> esPendiente(f.getEstadoT()))
            .map(this::validarEnriquecido)
            .collect(Collectors.toList());
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
    @Transactional
    public TransferenciaCabecera aprobar(String corrT,
                                        String usuarioNombre) throws Exception {

        // ── 1. Verificar duplicado antes de todo ─────────────────────────────────
        if (cabeceraRepo.existsByCorrT(corrT)) {
            throw new IllegalStateException(
                "La transferencia '" + corrT + "' ya fue aprobada anteriormente");
        }

        // ── 2. Leer activos del DBF ───────────────────────────────────────────────
        List<SolTransferenciaDbf> grupo = dbfService
            .listarSolTransferenciasAll(Path.of(transferenciasPath), null)
            .stream()
            .filter(f -> corrT.equalsIgnoreCase(f.getCorrT()))
            .collect(Collectors.toList());

        if (grupo.isEmpty()) throw new IllegalArgumentException(
            "No se encontró ningún activo con CORR_T=" + corrT);

        // ── 3. Validar TODOS — fail fast ──────────────────────────────────────────
        Map<String, String> cache = cargarCacheNombresAuxiliar(grupo);
        List<String> erroresGlobales = new ArrayList<>();

        for (SolTransferenciaDbf f : grupo) {
            TransferenciaActivoDetalleDto v = validarEnriquecido(f, cache);
            if (!v.isValida()) {
                erroresGlobales.add("• " + f.getCodigoO() + ": "
                    + String.join(", ", v.getErrores()));
            }
        }

        if (!erroresGlobales.isEmpty()) {
            throw new IllegalStateException(
                "No se puede aprobar. Errores detectados:\n"
                + String.join("\n", erroresGlobales));
        }

        // ── 4. Construir y persistir cabecera ─────────────────────────────────────
        SolTransferenciaDbf primero = grupo.get(0);
        boolean mismaUnidad = primero.getUnidadO() != null &&
                            primero.getUnidadO().equalsIgnoreCase(primero.getUnidadD());

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
            .tipo(mismaUnidad
                ? TransferenciaValidadaDto.TipoTransferencia.INTERNA
                : TransferenciaValidadaDto.TipoTransferencia.EXTERNA)
            .estado(TransferenciaCabecera.EstadoTransferencia.APROBADO)
            .fechaAprobacion(LocalDateTime.now())
            .usuarioAprobacion(usuarioNombre)
            .build();

        // ── 5. Construir detalles y asociarlos a la cabecera ──────────────────────
        for (SolTransferenciaDbf f : grupo) {
            TransferenciaDetalleLondra detalle = TransferenciaDetalleLondra.builder()
                .cabecera(cabecera)
                .idTDbf(f.getIdT())
                .codigoActivo(f.getCodigoO())
                .codContO(f.getCodContO())
                .codAuxO(f.getCodAuxO())
                .estadoActivoO(f.getEstadoO())
                .codOficO(f.getCodOficO())
                .codRespO(f.getCodRespO())
                .estadoDetalle(TransferenciaDetalleLondra.EstadoActivo.APROBADO)
                .build();

            cabecera.getDetalles().add(detalle);
        }

        // Un solo save guarda cabecera + todos los detalles (CascadeType.ALL)
        cabeceraRepo.save(cabecera);
        log.info("✅ BD — cabecera y {} detalles guardados para {}",
                grupo.size(), corrT);

        // ── 6. Actualizar ACTUAL.DBF para cada activo ─────────────────────────────
        List<String> erroresDbf = new ArrayList<>();

        for (SolTransferenciaDbf f : grupo) {
            try {
                Predio predioO = predioServicio
                    .findByUnidadIgnoreCase(f.getUnidadO())
                    .orElseThrow(() -> new IllegalStateException(
                        "Predio origen no encontrado: " + f.getUnidadO()));

                dbfService.actualizarActivoParaTransferencia(
                    f.getCodigoO(),
                    predioO.getEntidad().getEntidadCodigo(),
                    f.getUnidadO(),
                    f.getUnidadD(),
                    f.getCodOficD(),
                    f.getCodRespO(),
                    LocalDate.now(),
                    usuarioNombre
                );

                log.info("✅ ACTUAL.DBF — activo {} transferido", f.getCodigoO());

            } catch (Exception e) {
                erroresDbf.add("• " + f.getCodigoO() + ": " + e.getMessage());
                log.error("❌ Error en ACTUAL.DBF para activo {}: {}",
                    f.getCodigoO(), e.getMessage(), e);
            }
        }

        // ── 7. Marcar en sol_transferencias.DBF ───────────────────────────────────
        try {
            dbfService.actualizarEstadoTransferenciaDbf(
                Path.of(transferenciasPath), corrT, "APROBADO");
        } catch (Exception e) {
            log.error("❌ Error marcando APROBADO en sol_transferencias.DBF: {}",
                e.getMessage());
        }

        if (!erroresDbf.isEmpty()) {
            log.warn("⚠️ Transferencia {} aprobada en BD pero con {} "
                + "error(es) en ACTUAL.DBF:\n{}",
                    corrT, erroresDbf.size(), String.join("\n", erroresDbf));
        }

        log.info("✅ Transferencia {} completada — {} activo(s) por {}",
                corrT, grupo.size(), usuarioNombre);
        return cabecera;
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

    // Sobrecarga para aprobar() — construye caché propio
    private TransferenciaActivoDetalleDto validarEnriquecido(SolTransferenciaDbf f) {
        return validarEnriquecido(f, cargarCacheNombresAuxiliar(List.of(f)));
    }

    @Override
    public List<TransferenciaAgrupadaDto> leerYValidarAgrupado() {
        List<SolTransferenciaDbf> filas;
        try {
            filas = dbfService.listarSolTransferenciasAll(
                Path.of(transferenciasPath), null);
        } catch (Exception e) {
            log.error("No se pudo leer sol_transferencias.dbf: {}", e.getMessage());
            return List.of();
        }

        List<SolTransferenciaDbf> pendientes = filas.stream()
            .filter(f -> esPendiente(f.getEstadoT()))
            .collect(Collectors.toList());

        if (pendientes.isEmpty()) return List.of();

        // ✅ Una sola consulta batch ANTES del loop de validación
        Map<String, String> cacheAuxiliar = cargarCacheNombresAuxiliar(pendientes);

        Map<String, List<TransferenciaActivoDetalleDto>> porCorr = pendientes.stream()
            .map(f -> validarEnriquecido(f, cacheAuxiliar))  // ← pasar el caché
            .collect(Collectors.groupingBy(
                dto -> dto.getDatos().getCorrT() != null
                    ? dto.getDatos().getCorrT() : "SIN_CORR",
                LinkedHashMap::new,
                Collectors.toList()
            ));

        return porCorr.entrySet().stream()
            .map(entry -> construirGrupo(entry.getKey(), entry.getValue()))
            .collect(Collectors.toList());
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
}