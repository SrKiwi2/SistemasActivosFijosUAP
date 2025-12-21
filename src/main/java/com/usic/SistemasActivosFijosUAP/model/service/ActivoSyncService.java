package com.usic.SistemasActivosFijosUAP.model.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
import com.usic.SistemasActivosFijosUAP.model.entity.Activo;
import com.usic.SistemasActivosFijosUAP.model.entity.Auxiliar;
import com.usic.SistemasActivosFijosUAP.model.entity.Entidad;
import com.usic.SistemasActivosFijosUAP.model.entity.EstadoActivo;
import com.usic.SistemasActivosFijosUAP.model.entity.GrupoContable;
import com.usic.SistemasActivosFijosUAP.model.entity.OrganismoFinanciero;
import com.usic.SistemasActivosFijosUAP.model.entity.Responsable;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ActivoSyncService {

    // private final ActivoSyncTracker tracker;
    // private final IEntidadService entidadService;
    // private final IPredioServicio predioServicio;
    // private final IOficinaService oficinaService;
    // private final IResponsableService responsableService;
    // private final IGrupoContableService grupoContableService;
    // private final IAuxiliarService auxiliarService;
    // private final IEstadoActivoService estadoActivoService;
    // private final IOrganismoFinancieroService organismoFinancieroService;
    // private final IActivoService activoService;
    // private final JavaDbfService dbfService;
    // @PersistenceContext
    // private EntityManager em;

    // @Async
    // public void startSync(String q, Short gestionPreferida) {
    //     try {
    //         var filas = dbfService.listarActualAll(q);
    //         tracker.reset(filas.size());

    //         final int batchSize = 300;
    //         List<ActualDbf> chunk = new ArrayList<>(batchSize);

    //         for (var f : filas) {
    //             chunk.add(f);
    //             if (chunk.size() == batchSize) {
    //                 processChunk(chunk, gestionPreferida);
    //                 chunk.clear();
    //             }
    //         }
    //         if (!chunk.isEmpty()) {
    //             processChunk(chunk, gestionPreferida);
    //         }

    //         tracker.set("running", false);
    //     } catch (Exception ex) {
    //         tracker.set("error", ex.getMessage());
    //         tracker.set("running", false);
    //     }
    // }

    // @Transactional
    // protected void processChunk(List<ActualDbf> chunk, Short gestionPreferida) {
    //     for (var f : chunk) {
    //         tracker.inc("procesadas");

    //         if (isBlank(f.getEntidadCodigo()) || isBlank(f.getUnidad()) || isBlank(f.getCodigo())) {
    //             continue;
    //         }

    //         // ENTIDAD (0148/148/pad4)
    //         String cod = f.getEntidadCodigo().trim();
    //         String codNoZeros = stripLeftZeros(cod);
    //         String codPad4 = leftPad4(codNoZeros);
    //         Entidad entidad = (gestionPreferida != null)
    //                 ? entidadService.findByGestionAndEntidadCodigo(gestionPreferida, cod)
    //                     .or(() -> entidadService.findByGestionAndEntidadCodigo(gestionPreferida, codNoZeros))
    //                     .or(() -> entidadService.findByGestionAndEntidadCodigo(gestionPreferida, codPad4))
    //                     .orElse(null)
    //                 : entidadService.findTopByEntidadCodigoOrderByGestionDesc(cod)
    //                     .or(() -> entidadService.findTopByEntidadCodigoOrderByGestionDesc(codNoZeros))
    //                     .or(() -> entidadService.findTopByEntidadCodigoOrderByGestionDesc(codPad4))
    //                     .orElse(null);
    //         if (entidad == null) { tracker.inc("sinEntidad"); continue; }

    //         // PREDIO
    //         var predio = predioServicio.findByEntidadAndUnidadIgnoreCase(entidad, f.getUnidad()).orElse(null);
    //         if (predio == null) { tracker.inc("sinPredio"); continue; }

    //         // OFICINA
    //         if (f.getCodOfi() == null) { tracker.inc("sinOficina"); continue; }
    //         var oficina = oficinaService.findByPredioAndCodOfi(predio, f.getCodOfi()).orElse(null);
    //         if (oficina == null) { tracker.inc("sinOficina"); continue; }

    //         // RESPONSABLE
    //         Responsable responsable = null;
    //         if (!isBlank(f.getCodRespTxt())) {
    //             responsable = responsableService
    //                 .findByOficinaAndCodigoFuncionario(oficina, f.getCodRespTxt().trim()).orElse(null);
    //             if (responsable == null) { tracker.inc("sinResponsable"); }
    //         }

    //         // GRUPO
    //         GrupoContable grupo = null;
    //         if (f.getCodCont() != null) {
    //             grupo = grupoContableService.findByCodContable(f.getCodCont().intValue()).orElse(null);
    //             if (grupo == null) { tracker.inc("sinGrupo"); continue; }
    //         }

    //         // AUXILIAR
    //         Auxiliar aux = null;
    //         if (grupo != null && f.getCodAux() != null) {
    //             aux = auxiliarService
    //                 .findByPredio_IdPredioAndGrupoContable_IdGrupoContableAndCodAux(
    //                     predio.getIdPredio(), grupo.getIdGrupoContable(), f.getCodAux()
    //                 ).orElse(null);
    //             if (aux == null) { tracker.inc("sinAuxiliar"); } // NO detenemos
    //         }

    //         // ESTADO
    //         EstadoActivo estado = null;
    //         if (f.getCodEstado() != null) {
    //             estado = estadoActivoService.buscarPorCodigo(String.valueOf(f.getCodEstado()));
    //             if (estado == null){ tracker.inc("sinEstado"); } // NO detenemos
    //         }

    //         // ORG FIN
    //         OrganismoFinanciero orgFin = null;
    //         if (!isBlank(f.getOrgFinCode())) {
    //             Short ges = (f.getAno() != null) ? f.getAno().shortValue()
    //                       : (f.getFechaUlt() != null ? (short) f.getFechaUlt().getYear() : null);
    //             if (ges != null) {
    //                 String code = f.getOrgFinCode().trim().toUpperCase(Locale.ROOT);
    //                 orgFin = organismoFinancieroService.findByGestionAndCodOf(ges, code).orElse(null);
    //             }
    //             if (orgFin == null){ tracker.inc("sinOrgFin"); } // NO detenemos
    //         }

    //         // UPSERT
    //         String codigo = f.getCodigo().trim();
    //         Activo act = activoService.findByCodigo(codigo)
    //             .orElseGet(() -> activoService.findByOficinaAndCodigo(oficina, codigo).orElse(null));
    //         boolean nuevo = (act == null);
    //         if (act == null) {
    //             act = new Activo();
    //             act.setCodigo(codigo);
    //         }

    //         // Mapear campos
    //         act.setCodigoSec(nvl(f.getCodigoSec()));
    //         act.setDescripcion(nvl(f.getDescripcion()));
    //         act.setNombre(trunc(nvl(f.getDescripcion()), 255));
    //         act.setCosto(f.getCosto());
    //         act.setDepreciacionAcum(f.getDepAcum());
    //         // act.setVidaUtil(f.getVidaUtil());
    //         act.setVidaUtilAnterior(f.getVidaUtilAnt());
    //         act.setFechaAdquisicion(buildDate(f.getAno(), f.getMes(), f.getDia()));
    //         act.setFechaAnterior(buildDate(f.getAnoAnt(), f.getMesAnt(), f.getDiaAnt()));
    //         act.setRevaluado(boolVal(f.getBRev()));
    //         act.setBandUfv(boolVal(f.getBandUfv()));
    //         act.setBanderas(nvl(f.getBanderas()));
    //         act.setOficina(oficina);
    //         act.setResponsable(responsable);
    //         act.setGrupoContable(grupo);
    //         act.setAuxiliar(aux);
    //         act.setEstadoActivo(estado);
    //         act.setOrgFinCode(nvl(f.getOrgFinCode()));
    //         act.setOrganismoFinanciero(orgFin);
    //         act.setCodRube(nvl(f.getCodRube()));
    //         act.setNroConv(nvl(f.getNroConv()));
    //         act.setFechaUlt(f.getFechaUlt());
    //         act.setUsuario(nvl(f.getUsuario()));
    //         act.setApiEstado(f.getApiEstado());
    //         act.setFecMod(f.getFecMod());
    //         act.setUsuMod(nvl(f.getUsuMod()));
    //         act.setObserv(nvl(f.getObserv()));
    //         act.setEstado("ACTIVO");

    //         try {
    //             activoService.save(act);
    //             if (nuevo) tracker.inc("insertados");
    //             else       tracker.inc("actualizados");
    //         } catch (org.springframework.dao.DataIntegrityViolationException ignore) {
    //             // continúa
    //         }
    //     }
    //     em.flush();
    //     em.clear();
    // }

    // // ==== helpers ====

    // private static boolean isBlank(String s) {
    //     return s == null || s.trim().isEmpty();
    // }
    // private static String nvl(String s) { return (s == null) ? "" : s; }
    // private static Boolean boolVal(Boolean b) { return (b == null) ? null : b; }

    // private LocalDate buildDate(Integer y, Integer m, Integer d) {
    //     try {
    //         if (y == null || m == null || d == null || y <= 0 || m <= 0 || d <= 0) return null;
    //         return LocalDate.of(y, m, d);
    //     } catch (Exception e) { return null; }
    // }

    // private String trunc(String s, int n) {
    //     if (s == null) return null;
    //     return s.length() > n ? s.substring(0, n) : s;
    // }

    // private String stripLeftZeros(String s) {
    //     if (s == null) return null;
    //     String out = s.replaceFirst("^0+", "");
    //     return out.isEmpty() ? "0" : out;
    // }
    // private String leftPad4(String s) {
    //     String base = stripLeftZeros(s);
    //     try { return String.format("%04d", Integer.parseInt(base)); }
    //     catch (Exception e) { return s; }
    // }
}