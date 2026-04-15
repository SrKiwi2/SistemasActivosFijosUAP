package com.usic.SistemasActivosFijosUAP.model.ServiceImpl;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.usic.SistemasActivosFijosUAP.interoperabilidad.JavaDbfService;
import com.usic.SistemasActivosFijosUAP.model.IService.IActivoService;
import com.usic.SistemasActivosFijosUAP.model.IService.IOficinaService;
import com.usic.SistemasActivosFijosUAP.model.IService.IPredioServicio;
import com.usic.SistemasActivosFijosUAP.model.IService.IResponsableService;
import com.usic.SistemasActivosFijosUAP.model.IService.ITransferenciaLondraService;
import com.usic.SistemasActivosFijosUAP.model.dao.ITransferenciaLondraDao;
import com.usic.SistemasActivosFijosUAP.model.dto.interoperabilidad.SolTransferenciaDbf;
import com.usic.SistemasActivosFijosUAP.model.dto.interoperabilidad.TransferenciaValidadaDto;
import com.usic.SistemasActivosFijosUAP.model.dto.transferencia.TransferenciaAgrupadaDto;
import com.usic.SistemasActivosFijosUAP.model.entity.Oficina;
import com.usic.SistemasActivosFijosUAP.model.entity.Predio;
import com.usic.SistemasActivosFijosUAP.model.entity.Transferencia;
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
    public List<TransferenciaValidadaDto> leerYValidarPendientes() {
        List<SolTransferenciaDbf> filas;
        try {
            filas = dbfService.listarSolTransferenciasAll(
                Path.of(transferenciasPath), null);
        } catch (Exception e) {
            log.error("🌐 No se pudo leer sol_transferencias.dbf: {}", e.getMessage());
            return List.of();
        }

        return filas.stream()
            .filter(f -> esPendiente(f.getEstadoT()))  // ← antes: "PENDIENTE".equalsIgnoreCase(...)
            .map(this::validar)
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
    public TransferenciaLondra aprobar(String corrT, String usuarioNombre) throws Exception {

        // Obtener todos los activos de este CORR_T
        List<SolTransferenciaDbf> grupo = dbfService
            .listarSolTransferenciasAll(Path.of(transferenciasPath), null)
            .stream()
            .filter(f -> corrT.equalsIgnoreCase(f.getCorrT()))
            .collect(Collectors.toList());

        if (grupo.isEmpty()) throw new IllegalArgumentException(
            "No se encontró la transferencia con CORR_T=" + corrT);

        // Validar que TODOS sean válidos antes de procesar cualquiera
        List<String> erroresGlobales = new ArrayList<>();
        for (SolTransferenciaDbf f : grupo) {
            TransferenciaValidadaDto v = validar(f);
            if (!v.isValida()) {
                erroresGlobales.add("Activo " + f.getCodigoO() + ": " +
                                    String.join(", ", v.getErrores()));
            }
        }
        if (!erroresGlobales.isEmpty()) {
            throw new IllegalStateException(
                "Hay activos con errores: " + String.join(" | ", erroresGlobales));
        }

        SolTransferenciaDbf primero = grupo.get(0);
        TransferenciaLondra registroFinal = null;

        // Procesar cada activo individualmente
        for (SolTransferenciaDbf f : grupo) {

            TransferenciaLondra t = TransferenciaLondra.builder()
                .corrT(f.getCorrT())
                .idTDbf(f.getIdT())
                .nombreT(f.getNombreT())
                .fechaT(f.getFechaT())
                .estadoTDbf(f.getEstadoT())
                .tipo(primero.getUnidadO().equalsIgnoreCase(primero.getUnidadD())
                    ? TransferenciaValidadaDto.TipoTransferencia.INTERNA
                    : TransferenciaValidadaDto.TipoTransferencia.EXTERNA)
                .unidadO(f.getUnidadO())
                .codContO(f.getCodContO())
                .codAuxO(f.getCodAuxO())
                .codigoActivo(f.getCodigoO())
                .estadoActivoO(f.getEstadoO())
                .codOficO(f.getCodOficO())
                .codRespO(f.getCodRespO())
                .ciSolicitante(f.getCiSolO())
                .unidadD(f.getUnidadD())
                .codOficD(f.getCodOficD())
                .ciReceptor(f.getCiRecep())
                .nomReceptor(f.getNomRecep())
                .estado(TransferenciaLondra.EstadoTransferencia.APROBADO)
                .fechaAprobacion(LocalDateTime.now())
                .usuarioAprobacion(usuarioNombre)
                .build();

            transferenciaRepo.save(t);
            if (registroFinal == null) registroFinal = t;

            // Actualizar ACTUAL.DBF para este activo específico
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

            log.info("✅ Activo {} de transferencia {} procesado", f.getCodigoO(), corrT);
        }

        // Marcar el CORR_T completo como aprobado en sol_transferencias.DBF
        dbfService.actualizarEstadoTransferenciaDbf(
            Path.of(transferenciasPath), corrT, "APROBADO");

        log.info("✅ Transferencia {} completa — {} activos procesados por {}",
                corrT, grupo.size(), usuarioNombre);
        return registroFinal;
    }

    private TransferenciaValidadaDto validar(SolTransferenciaDbf f) {
        List<String> errores = new ArrayList<>();

        boolean mismaUnidad = f.getUnidadO() != null &&
                              f.getUnidadO().equalsIgnoreCase(f.getUnidadD());
        var tipo = mismaUnidad
            ? TransferenciaValidadaDto.TipoTransferencia.INTERNA
            : TransferenciaValidadaDto.TipoTransferencia.EXTERNA;

        boolean activoOk = false;
        boolean predioOOk = false;
        boolean oficOOk = false;
        boolean respOOk = false;

        try {
            activoOk = activoService.findByCodigo(f.getCodigoO()).isPresent();
            if (!activoOk) errores.add("Activo '" + f.getCodigoO() + "' no existe en BD");
        } catch (Exception e) {
            errores.add("Error buscando activo: " + e.getMessage());
        }

        Optional<Predio> predioOpt = Optional.empty();
        try {
            predioOpt = predioServicio.findByUnidadIgnoreCase(f.getUnidadO());
            predioOOk = predioOpt.isPresent();
            if (!predioOOk) errores.add("Unidad origen '" + f.getUnidadO() + "' no existe");
        } catch (Exception e) {
            errores.add("Error buscando predio origen: " + e.getMessage());
        }

        if (predioOOk && f.getCodOficO() != null) {
            try {
                oficOOk = oficinaService
                    .findByCodOfiAndPredio(f.getCodOficO(), predioOpt.get()) // ⚠️ Adaptar
                    .isPresent();
                if (!oficOOk)
                    errores.add("Oficina origen " + f.getCodOficO() + " no existe en unidad " + f.getUnidadO());
            } catch (Exception e) {
                errores.add("Error buscando oficina origen: " + e.getMessage());
            }
        }

        if (oficOOk && f.getCodRespO() != null) {   
            try {
                Optional<Oficina> ofic = oficinaService
                    .findByCodOfiAndPredio(f.getCodOficO(), predioOpt.get());
                respOOk = ofic.isPresent() && responsableService
                    .findByCodigoFuncionarioAndOficina(String.valueOf(f.getCodRespO()), ofic.get()) 
                    .isPresent();
                if (!respOOk)
                    errores.add("Responsable origen " + f.getCodRespO() + " no existe");
            } catch (Exception e) {
                errores.add("Error buscando responsable origen: " + e.getMessage());
            }
        }

        boolean predioD = false;
        boolean oficDOk = false;
        boolean respDOk = false;

        Optional<Predio> predioDOpt = Optional.empty();
        try {
            predioDOpt = predioServicio.findByUnidadIgnoreCase(f.getUnidadD());
            predioD = predioDOpt.isPresent();
            if (!predioD) errores.add("Unidad destino '" + f.getUnidadD() + "' no existe");
        } catch (Exception e) {
            errores.add("Error buscando predio destino: " + e.getMessage());
        }

        if (predioD && f.getCodOficD() != null) {
            try {
                oficDOk = oficinaService
                    .findByCodOfiAndPredio(f.getCodOficD(), predioDOpt.get())
                    .isPresent();
                if (!oficDOk)
                    errores.add("Oficina destino " + f.getCodOficD() + " no existe en unidad " + f.getUnidadD());
            } catch (Exception e) {
                errores.add("Error buscando oficina destino: " + e.getMessage());
            }
        }

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

        boolean valida = errores.isEmpty()
            && activoOk && predioOOk && oficOOk && respOOk
            && predioD   && oficDOk   && !yaAprobada;

        return TransferenciaValidadaDto.builder()
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
            .build();
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

        // 1. Filtrar pendientes
        // 2. Validar cada fila individualmente
        // 3. Agrupar por CORR_T preservando orden de aparición
        Map<String, List<TransferenciaValidadaDto>> porCorr = filas.stream()
            .filter(f -> esPendiente(f.getEstadoT()))
            .map(this::validar)
            .collect(Collectors.groupingBy(
                dto -> dto.getDatos().getCorrT() != null
                    ? dto.getDatos().getCorrT()
                    : "SIN_CORR",
                LinkedHashMap::new,   // preserva orden de inserción
                Collectors.toList()
            ));

        // 4. Construir un DTO agrupado por cada CORR_T
        return porCorr.entrySet().stream()
            .map(entry -> {
                String corrT = entry.getKey();
                List<TransferenciaValidadaDto> activos = entry.getValue();
                SolTransferenciaDbf primero = activos.get(0).getDatos();

                boolean mismaUnidad = primero.getUnidadO() != null &&
                                    primero.getUnidadO().equalsIgnoreCase(primero.getUnidadD());

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
                    .build();
            })
            .collect(Collectors.toList());
    }
}