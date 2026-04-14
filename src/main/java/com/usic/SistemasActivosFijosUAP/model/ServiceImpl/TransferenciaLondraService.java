package com.usic.SistemasActivosFijosUAP.model.ServiceImpl;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
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

        SolTransferenciaDbf dbfRec = dbfService.listarSolTransferenciasAll(
                Path.of(transferenciasPath), null)
            .stream()
            .filter(f -> corrT.equalsIgnoreCase(f.getCorrT()))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException(
                "No se encontró la transferencia con CORR_T=" + corrT));

        TransferenciaValidadaDto validado = validar(dbfRec);
        if (!validado.isValida()) {
            throw new IllegalStateException(
                "La transferencia no puede aprobarse. Errores: " +
                String.join(", ", validado.getErrores()));
        }

        TransferenciaLondra t = TransferenciaLondra.builder()
            .corrT(dbfRec.getCorrT())
            .idTDbf(dbfRec.getIdT())
            .nombreT(dbfRec.getNombreT())
            .fechaT(dbfRec.getFechaT())
            .estadoTDbf(dbfRec.getEstadoT())
            .tipo(validado.getTipo())
            .unidadO(dbfRec.getUnidadO())
            .codContO(dbfRec.getCodContO())
            .codAuxO(dbfRec.getCodAuxO())
            .codigoActivo(dbfRec.getCodigoO())
            .estadoActivoO(dbfRec.getEstadoO())
            .codOficO(dbfRec.getCodOficO())
            .codRespO(dbfRec.getCodRespO())
            .ciSolicitante(dbfRec.getCiSolO())
            .unidadD(dbfRec.getUnidadD())
            .codOficD(dbfRec.getCodOficD())
            .ciReceptor(dbfRec.getCiRecep())
            .nomReceptor(dbfRec.getNomRecep())
            .estado(TransferenciaLondra.EstadoTransferencia.APROBADO)
            .fechaAprobacion(LocalDateTime.now())
            .usuarioAprobacion(usuarioNombre)
            .build();

        transferenciaRepo.save(t);

        Predio predioO = predioServicio
            .findByUnidadIgnoreCase(dbfRec.getUnidadO())
            .orElseThrow(() -> new IllegalStateException(
                "Predio origen no encontrado al aprobar: " + dbfRec.getUnidadO()));

        dbfService.actualizarActivoParaTransferencia(
            dbfRec.getCodigoO(),
            predioO.getEntidad().getEntidadCodigo(),
            dbfRec.getUnidadO(),
            dbfRec.getUnidadD(),
            dbfRec.getCodOficD(),
            dbfRec.getCodRespO(),
            LocalDate.now(),
            usuarioNombre
        );

        dbfService.actualizarEstadoTransferenciaDbf(
            Path.of(transferenciasPath), corrT, "APROBADO");

        log.info("✅ Transferencia {} aprobada por {}", corrT, usuarioNombre);
        return t;
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
}