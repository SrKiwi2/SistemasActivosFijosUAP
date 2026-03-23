package com.usic.SistemasActivosFijosUAP.model.service.importacion;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.linuxense.javadbf.DBFField;
import com.linuxense.javadbf.DBFReader;
import com.linuxense.javadbf.DBFRow;
import com.usic.SistemasActivosFijosUAP.model.IService.IActivoService;
import com.usic.SistemasActivosFijosUAP.model.IService.IAuxiliarService;
import com.usic.SistemasActivosFijosUAP.model.IService.IEntidadService;
import com.usic.SistemasActivosFijosUAP.model.IService.IEstadoActivoService;
import com.usic.SistemasActivosFijosUAP.model.IService.IGrupoContableService;
import com.usic.SistemasActivosFijosUAP.model.IService.IOficinaService;
import com.usic.SistemasActivosFijosUAP.model.IService.IOrganismoFinancieroService;
import com.usic.SistemasActivosFijosUAP.model.IService.IPredioServicio;
import com.usic.SistemasActivosFijosUAP.model.IService.IResponsableService;
import com.usic.SistemasActivosFijosUAP.model.entity.Activo;
import com.usic.SistemasActivosFijosUAP.model.entity.Auxiliar;
import com.usic.SistemasActivosFijosUAP.model.entity.Entidad;
import com.usic.SistemasActivosFijosUAP.model.entity.EstadoActivo;
import com.usic.SistemasActivosFijosUAP.model.entity.GrupoContable;
import com.usic.SistemasActivosFijosUAP.model.entity.Oficina;
import com.usic.SistemasActivosFijosUAP.model.entity.OrganismoFinanciero;
import com.usic.SistemasActivosFijosUAP.model.entity.Predio;
import com.usic.SistemasActivosFijosUAP.model.entity.Responsable;

import jakarta.persistence.EntityManager;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ActualImportService {
    private final IEntidadService entidadService;
    private final IPredioServicio predioServicio;
    private final IOficinaService oficinaService;
    private final IResponsableService responsableService;
    private final IGrupoContableService grupoContableService;
    private final IAuxiliarService auxiliarService;
    private final IEstadoActivoService estadoActivoService;
    private final IOrganismoFinancieroService organismoFinancieroService;
    private final IActivoService activoService;

    @Data
    public static class ImportResult {
        private int totalFisicos, leidas, insertados, actualizados, marcadosBorrados, erroresLectura;
        private int omitidosCampos, omitidosSinEntidad, omitidosSinPredio, omitidosSinOficina;
        private int omitidosSinResponsable, omitidosSinGrupo, omitidosSinEstado, omitidosSinAuxiliar, erroresExcepcion;
        private List<String> errores = new ArrayList<>();
    }

    @Autowired EntityManager em;

    @Transactional
    public ImportResult importarActual(String importId, Path dbfPath, Charset cs, Short gestionPreferida) throws IOException {
        ImportResult res = new ImportResult();

        try (InputStream in = new BufferedInputStream(Files.newInputStream(dbfPath));
             DBFReader reader = new DBFReader(in, cs)) {

                int total = reader.getRecordCount();

                int batchSize = 1000, sinceLastFlush = 0;
                DBFRow r; int filaLocal = 0;

            int fc = reader.getFieldCount();
            StringBuilder sb = new StringBuilder("DBF FIELDS (ACTUAL):\n");
            Map<String,Integer> idx = new HashMap<>();
            for (int i = 0; i < fc; i++) {
                DBFField f = reader.getField(i);
                String raw = f.getName();
                String key = normalize(raw);
                idx.put(key, i);
                sb.append(String.format("  %02d) name='%s' norm='%s' type=%s len=%d dec=%d%n",
                        i, raw, key, String.valueOf(f.getType()), f.getLength(), f.getDecimalCount()));
            }
            log.info(sb.toString());

            int iUNIDAD     = pick(idx, "UNIDAD");
            int iENTIDAD    = pick(idx, "ENTIDAD");
            int iCODIGO     = pick(idx, "CODIGO");
            int iCODCONT    = pick(idx, "CODCONT");
            int iCODAUX     = pick(idx, "CODAUX");
            int iVIDAUTIL   = pick(idx, "VIDAUTIL");
            int iDESCRIP    = pick(idx, "DESCRIP");
            int iCOSTO      = pick(idx, "COSTO");
            int iDEPACU     = pick(idx, "DEPACU");
            int iMES        = pick(idx, "MES");
            int iANO        = pick(idx, "ANO");
            int iB_REV      = pick(idx, "B_REV");
            int iDIA        = pick(idx, "DIA");
            int iCODOFIC    = pick(idx, "CODOFIC");
            int iCODRESP    = pick(idx, "CODRESP");
            int iOBSERV     = pick(idx, "OBSERV");
            int iDIA_ANT    = pick(idx, "DIA_ANT");
            int iMES_ANT    = pick(idx, "MES_ANT");
            int iANO_ANT    = pick(idx, "ANO_ANT");
            int iVUT_ANT    = pick(idx, "VUT_ANT");
            int iCOSTO_ANT  = pick(idx, "COSTO_ANT");
            int iBAND_UFV   = pick(idx, "BAND_UFV");
            int iCODESTADO  = pick(idx, "CODESTADO");
            int iCOD_RUBE   = pick(idx, "COD_RUBE");
            int iNRO_CONV   = pick(idx, "NRO_CONV");
            int iORG_FIN    = pick(idx, "ORG_FIN");
            int iFEULT      = pick(idx, "FEULT");
            int iUSUAR      = pick(idx, "USUAR");
            int iAPI_ESTADO = pick(idx, "API_ESTADO");
            int iCODIGOSEC  = pick(idx, "CODIGOSEC");
            int iBANDERAS   = pick(idx, "BANDERAS");
            int iFEC_MOD    = pick(idx, "FEC_MOD");
            int iUSU_MOD    = pick(idx, "USU_MOD");

            res.setTotalFisicos(reader.getRecordCount());

            // ===== 3) Lectura =====
            int updatesDetectados = 0;
            String primerUpdateResumen = null;

                        Map<String, Entidad> cacheEntidad = new HashMap<>();
                        Map<String, Predio>  cachePredio  = new HashMap<>();
                        Map<String, Oficina> cacheOficina = new HashMap<>();
                        Map<String, Responsable> cacheResp = new HashMap<>();
                        Map<Integer, GrupoContable> cacheGrupo = new HashMap<>();
                        Map<String, EstadoActivo> cacheEstado = new HashMap<>();
                        Map<String, OrganismoFinanciero> cacheOF = new HashMap<>();

            Set<String> codigosExistentes = new HashSet<>(
                activoService.findAllCodigos()
            );

            log.info("IMPORT ACTUAL → códigos existentes en BD: {}", codigosExistentes.size());

            while ((r = reader.nextRow()) != null) {
                try {
                    filaLocal++;
                    res.leidas++;

                    // --- Campos DBF básicos
                    String entidadCodRaw = nvl(r.getString(iENTIDAD));
                    String unidadRaw     = nvl(r.getString(iUNIDAD));
                    String codigo        = limit(nvl(r.getString(iCODIGO)), 60);
                    String descripcion   = limit(nvl(r.getString(iDESCRIP)), 1024);
                    String codigoSec     = limit(nvl(r.getString(iCODIGOSEC)), 60);

                    Short  codCont       = bdToShort(r.getBigDecimal(iCODCONT), "CODCONT", res.leidas, res);
                    Short  codAux        = bdToShort(r.getBigDecimal(iCODAUX),  "CODAUX",  res.leidas, res);

                    Double costo         = bdToDouble(r.getBigDecimal(iCOSTO), "COSTO", res.leidas, res);
                    Double depAcum       = bdToDouble(r.getBigDecimal(iDEPACU), "DEPACU", res.leidas, res);
                    BigDecimal vidaUtil  = bdToBigDecimal(r.getBigDecimal(iVIDAUTIL), "VIDAUTIL", res.leidas, res);

                    Integer dia          = bdToInt(r.getBigDecimal(iDIA), "DIA", res.leidas, res);
                    Integer mes          = bdToInt(r.getBigDecimal(iMES), "MES", res.leidas, res);
                    Integer ano          = bdToInt(r.getBigDecimal(iANO), "ANO", res.leidas, res);

                    Integer diaAnt       = bdToInt(r.getBigDecimal(iDIA_ANT), "DIA_ANT", res.leidas, res);
                    Integer mesAnt       = bdToInt(r.getBigDecimal(iMES_ANT), "MES_ANT", res.leidas, res);
                    Integer anoAnt       = bdToInt(r.getBigDecimal(iANO_ANT), "ANO_ANT", res.leidas, res);

                    Integer vutAnt       = bdToInt(r.getBigDecimal(iVUT_ANT), "VUT_ANT", res.leidas, res);
                    Double  costoAnt     = bdToDouble(r.getBigDecimal(iCOSTO_ANT), "COSTO_ANT", res.leidas, res);

                    Boolean bRev         = r.getBoolean(iB_REV);
                    Boolean bandUfv      = r.getBoolean(iBAND_UFV);

                    Short   codEstado    = bdToShort(r.getBigDecimal(iCODESTADO), "CODESTADO", res.leidas, res);

                    String  codRube      = limit(nvl(r.getString(iCOD_RUBE)), 60);
                    String  nroConv      = limit(nvl(r.getString(iNRO_CONV)), 60);
                    String  orgFinCode   = limit(nvl(r.getString(iORG_FIN)), 60);

                    LocalDate fechaUlt   = toLocalDate(r.getDate(iFEULT));
                    String  usu          = limit(nvl(r.getString(iUSUAR)), 60);
                    Short   apiEstado    = bdToShort(r.getBigDecimal(iAPI_ESTADO), "API_ESTADO", res.leidas, res);
                    String  banderas     = limit(nvl(r.getString(iBANDERAS)), 120);
                    LocalDate fecMod     = toLocalDate(r.getDate(iFEC_MOD));
                    String  usuMod       = limit(nvl(r.getString(iUSU_MOD)), 60);

                    String entidadCod = norm(entidadCodRaw);
                    String unidad     = norm(unidadRaw);

                    String observ = nvl(r.getString(iOBSERV));

                    // Validación mínima
                    if (isBlank(entidadCod) || isBlank(unidad) || isBlank(codigo)) {
                        res.omitidosCampos++;
                        res.errores.add(msgFila(res.leidas, "ENTIDAD/UNIDAD/CODIGO incompletos. ENTIDAD=" +
                                entidadCod + " UNIDAD=" + unidad + " CODIGO=" + codigo));
                        continue;
                    }



                    // ===== Resolver Predio -> Oficina -> Responsable =====

                    Entidad entidad = cacheEntidad.computeIfAbsent(
                        gestionKey(entidadCod, gestionPreferida),
                        k -> (gestionPreferida != null)
                                ? entidadService.findByGestionAndEntidadCodigo(gestionPreferida, entidadCod).orElse(null)
                                : entidadService.findTopByEntidadCodigoOrderByGestionDesc(entidadCod).orElse(null)
                    );

                    if (entidad == null) {
                        res.omitidosSinEntidad++;
                        res.errores.add(msgFila(res.leidas, "Entidad no encontrada: " + entidadCod));
                        continue;
                    }

                    // Predio
                    Predio predio = predioServicio.findByEntidadAndUnidadIgnoreCase(entidad, unidad).orElse(null);
                    if (predio == null) {
                        res.omitidosSinPredio++;
                        res.errores.add(msgFila(res.leidas, "Predio no encontrado para ENTIDAD=" + entidadCod +
                                " UNIDAD=" + unidad));
                        continue;
                    }

                    // Oficina
                    Short codOfi = bdToShort(r.getBigDecimal(iCODOFIC), "CODOFIC", res.leidas, res);
                    if (codOfi == null) {
                        res.omitidosSinOficina++;
                        res.errores.add(msgFila(res.leidas, "CODOFIC nulo para CODIGO=" + codigo));
                        continue;
                    }
                    Oficina oficina = oficinaService.findByPredioAndCodOfi(predio, codOfi).orElse(null);
                    if (oficina == null) {
                        res.omitidosSinOficina++;
                        res.errores.add(msgFila(res.leidas, "Oficina no encontrada UNIDAD=" + unidad + " CODOFI=" + codOfi));
                        continue;
                    }

                    // Responsable (por código funcionario en esa oficina)
                    String codRespTxt = numberToPlain(r.getBigDecimal(iCODRESP)); // Numerico -> texto
                    Responsable responsable = null;
                    if (!isBlank(codRespTxt)) {
                        responsable = responsableService
                                .findByOficinaAndCodigoFuncionario(oficina, codRespTxt.trim())
                                .orElse(null);
                    }
                    if (responsable == null) {
                        res.omitidosSinResponsable++;
                        res.errores.add(msgFila(res.leidas, "Responsable no encontrado Oficina=" + codOfi +
                                " CODRESP=" + codRespTxt));
                        continue;
                    }

                    // ===== Grupo / Auxiliar / Estado / OrgFin =====
                    GrupoContable grupo = null;
                    if (codCont != null) {
                        grupo = grupoContableService.findByCodContable(Integer.valueOf(codCont)).orElse(null);
                        if (grupo == null) {
                            res.omitidosSinGrupo++;
                            res.errores.add(msgFila(res.leidas, "Grupo no encontrado CODCONT=" + codCont));
                            continue;
                        }
                    }

                    Auxiliar aux = null;
                    if (codAux != null) {
                        if (codCont == null) {
                            // No tenemos cómo desambiguar: registra error o intenta heurística si así lo decides
                            res.omitidosSinAuxiliar++;
                            res.errores.add(msgFila(res.leidas,
                                "No se puede resolver Auxiliar: falta CODCONT para desambiguar (UNIDAD=" + unidad + ", CODAUX=" + codAux + ")"));
                        } else {
                            GrupoContable grupo2 = grupoContableService
                                    .findByCodContable(Integer.valueOf(codCont))
                                    .orElse(null);
                            if (grupo2 == null) {
                                res.omitidosSinGrupo++;
                                res.errores.add(msgFila(res.leidas, "Grupo no encontrado CODCONT=" + codCont));
                                continue;
                            }
                            aux = auxiliarService
                                    .findByPredio_IdPredioAndGrupoContable_IdGrupoContableAndCodAux(predio.getIdPredio(), grupo2.getIdGrupoContable(), codAux)
                                    .orElse(null);

                            if (aux == null) {
                                res.omitidosSinAuxiliar++;
                                res.errores.add(msgFila(res.leidas, "Auxiliar no encontrado (UNIDAD=" + unidad +
                                        ", CODCONT=" + codCont + ", CODAUX=" + codAux + ")"));
                                // aquí decides: ¿continuar sin auxiliar o cortar? yo sugiero continuar
                            }
                        }
                    }

                    EstadoActivo estado = null;
                    if (codEstado != null) {
                        // tu EstadoActivo tiene "codigo" String; comparamos contra String.valueOf(codEstado)
                        estado = estadoActivoService.buscarPorCodigo(String.valueOf(codEstado));
                        if (estado == null) {
                            res.omitidosSinEstado++;
                            res.errores.add(msgFila(res.leidas, "EstadoActivo no encontrado CODESTADO=" + codEstado));
                            // si lo consideras crítico, haz 'continue;'
                        }
                    }

                    OrganismoFinanciero orgFin = null;
                    if (!isBlank(orgFinCode)) {
                        // estrategia: usar gestión del ANO (si no hay FEULT) para resolver el OF
                        Short gestion = (ano != null) ? ano.shortValue()
                                : (fechaUlt != null ? (short) fechaUlt.getYear() : null);
                        if (gestion != null) {
                            orgFin = organismoFinancieroService
                                    .findByGestionAndCodOf(gestion, orgFinCode).orElse(null);
                        }
                    }

                    // ===== Upsert Activo por CODIGO =====
                    Activo act = new Activo();
        
                    act.setCodigo(codigo);
                    act.setCodigoSec(codigoSec);
                    act.setDescripcion(descripcion);
                    act.setNombre(truncOrNull(descripcion, 255));
                    act.setCosto(costo);
                    act.setDepreciacionAcum(depAcum);
                    act.setVidaUtil(vidaUtil);
                    act.setVidaUtilAnterior(vutAnt);
                    act.setFechaAdquisicion(buildDate(ano, mes, dia));
                    act.setFechaAnterior(buildDate(anoAnt, mesAnt, diaAnt));
                    act.setCostoAnterior(costoAnt);
                    act.setRevaluado(boolVal(bRev));
                    act.setBandUfv(boolVal(bandUfv));
                    act.setBanderas(banderas);

                    act.setOficina(oficina);
                    act.setResponsable(responsable);
                    act.setGrupoContable(grupo);
                    act.setAuxiliar(aux);
                    act.setEstadoActivo(estado);

                    act.setOrgFinCode(orgFinCode);
                    act.setOrganismoFinanciero(orgFin);

                    act.setCodRube(codRube);
                    act.setNroConv(nroConv);

                    act.setFechaUlt(fechaUlt);
                    act.setUsuario(usu);
                    act.setApiEstado(apiEstado);
                    act.setFecMod(fecMod);
                    act.setUsuMod(usuMod);

                    act.setObserv(observ);

                    act.setEstado("ACTIVO");

                    if (!codigosExistentes.contains(codigo)) {
                        // Código nuevo → insertar directamente
                        em.persist(act);
                        res.insertados++;

                    } else {
                        // Código ya existe → fetch puntual y comparar
                        Activo existing = activoService.findByCodigo(codigo).orElse(null);
                        if (existing != null && hasChanges(existing, act)) {
                            applyChanges(existing, act);
                            em.merge(existing);
                            res.actualizados++;
                        }
                        // Sin cambios → no hace nada
                    }

                    sinceLastFlush++;
                    if (sinceLastFlush >= batchSize) {
                        em.flush();
                        em.clear();
                        sinceLastFlush = 0;
                    }

                    if (filaLocal % 200 == 0) {
                        pushProgress(importId, res, total);
                    }

                } catch (Exception exRow) {
                    res.erroresExcepcion++;
                    res.errores.add(msgFila(res.leidas, root(exRow)));

                        // ← CLAVE: limpia la sesión antes de continuar procesando
                        try { em.clear(); } catch (Exception ignore) {}
                        sinceLastFlush = 0; // reinicia el batch
                }
            }

            em.flush();
            pushProgress(importId, res, total);

            // ===== resumen final de updates (una sola línea, y si hubo alguno)
            if (updatesDetectados > 0) {
                log.warn("IMPORT ACTUAL → actualizaciones detectadas: {}. Primera actualización: {}", updatesDetectados, primerUpdateResumen);
            } else {
                log.info("IMPORT ACTUAL → no se detectaron actualizaciones (solo inserts).");
            }

            // conteo sanity
            int resto = res.totalFisicos - (res.leidas + res.marcadosBorrados + res.erroresLectura);
            if (resto != 0) {
                log.debug("ACTUAL conteo: total={} leidas={} borrados={} errLectura={} resto={}",
                        res.totalFisicos, res.leidas, res.marcadosBorrados, res.erroresLectura, resto);
            }
        }

        return res;
    }

    @Autowired ImportProgressService progress;

    private void pushProgress(String id, ImportResult r, int total) {
        progress.inc(id, s -> {
            s.setTotal(total);
            s.setLeidas(r.getLeidas());
            s.setInsertados(r.getInsertados());
            s.setActualizados(r.getActualizados());
            s.setErrores(r.getErrores() != null ? r.getErrores().size() : 0);

            if (r.getErrores() != null && s.getMuestrasErrores().size() < 10) {
                for (String e : r.getErrores()) {
                    if (s.getMuestrasErrores().size() >= 10) break;
                    s.getMuestrasErrores().add(e);
                }
            }
        });
    }


    /* ===== Helpers ===== */

    /**
     * Detecta si algún campo relevante cambió entre el activo del DBF y el que ya está en BD.
     * Solo compara campos que pueden ser modificados en VSIAF.
     */
    private boolean hasChanges(Activo existing, Activo incoming) {
        return !Objects.equals(existing.getDescripcion(),       incoming.getDescripcion())
            || !Objects.equals(existing.getCosto(),             incoming.getCosto())
            || !Objects.equals(existing.getDepreciacionAcum(),  incoming.getDepreciacionAcum())
            || !Objects.equals(existing.getVidaUtil(),          incoming.getVidaUtil())
            || !Objects.equals(existing.getFechaAdquisicion(),  incoming.getFechaAdquisicion())
            || !Objects.equals(existing.getCodigoSec(),         incoming.getCodigoSec())
            || !Objects.equals(existing.getCostoAnterior(),     incoming.getCostoAnterior())
            || !Objects.equals(existing.getVidaUtilAnterior(),  incoming.getVidaUtilAnterior())
            || !Objects.equals(existing.getFechaAnterior(),     incoming.getFechaAnterior())
            || !Objects.equals(existing.getRevaluado(),         incoming.getRevaluado())
            || !Objects.equals(existing.getBandUfv(),           incoming.getBandUfv())
            || !Objects.equals(existing.getBanderas(),          incoming.getBanderas())
            || !Objects.equals(existing.getObserv(),            incoming.getObserv())
            || !Objects.equals(existing.getApiEstado(),         incoming.getApiEstado())
            || !Objects.equals(existing.getFecMod(),            incoming.getFecMod())
            // Relaciones (comparar por ID para no cargar objetos completos)
            || !Objects.equals(
                existing.getEstadoActivo() == null ? null : existing.getEstadoActivo().getIdEstadoActivo(),
                incoming.getEstadoActivo() == null ? null : incoming.getEstadoActivo().getIdEstadoActivo())
            || !Objects.equals(
                existing.getOficina() == null ? null : existing.getOficina().getIdOficina(),
                incoming.getOficina() == null ? null : incoming.getOficina().getIdOficina())
            || !Objects.equals(
                existing.getResponsable() == null ? null : existing.getResponsable().getIdResponsable(),
                incoming.getResponsable() == null ? null : incoming.getResponsable().getIdResponsable())
            || !Objects.equals(
                existing.getGrupoContable() == null ? null : existing.getGrupoContable().getIdGrupoContable(),
                incoming.getGrupoContable() == null ? null : incoming.getGrupoContable().getIdGrupoContable());
    }

    /**
     * Copia los campos modificables del activo entrante al existente.
     * NO toca el ID ni campos que no debe modificar la sincronización.
     */
    private void applyChanges(Activo existing, Activo incoming) {
        existing.setDescripcion(incoming.getDescripcion());
        existing.setNombre(incoming.getNombre());
        existing.setCosto(incoming.getCosto());
        existing.setDepreciacionAcum(incoming.getDepreciacionAcum());
        existing.setVidaUtil(incoming.getVidaUtil());
        existing.setVidaUtilAnterior(incoming.getVidaUtilAnterior());
        existing.setFechaAdquisicion(incoming.getFechaAdquisicion());
        existing.setFechaAnterior(incoming.getFechaAnterior());
        existing.setCostoAnterior(incoming.getCostoAnterior());
        existing.setCodigoSec(incoming.getCodigoSec());
        existing.setRevaluado(incoming.getRevaluado());
        existing.setBandUfv(incoming.getBandUfv());
        existing.setBanderas(incoming.getBanderas());
        existing.setObserv(incoming.getObserv());
        existing.setOficina(incoming.getOficina());
        existing.setResponsable(incoming.getResponsable());
        existing.setGrupoContable(incoming.getGrupoContable());
        existing.setAuxiliar(incoming.getAuxiliar());
        existing.setEstadoActivo(incoming.getEstadoActivo());
        existing.setOrganismoFinanciero(incoming.getOrganismoFinanciero());
        existing.setOrgFinCode(incoming.getOrgFinCode());
        existing.setCodRube(incoming.getCodRube());
        existing.setNroConv(incoming.getNroConv());
        existing.setFechaUlt(incoming.getFechaUlt());
        existing.setUsuario(incoming.getUsuario());
        existing.setApiEstado(incoming.getApiEstado());
        existing.setFecMod(incoming.getFecMod());
        existing.setUsuMod(incoming.getUsuMod());
        // estado del sistema lo dejamos como estaba (no lo pisa el DBF)
    }

    private BigDecimal bdToBigDecimal(BigDecimal bd, String nombreCampo, int fila, ImportResult res) {
        if (bd == null) return null;
        try {
            return new BigDecimal(bd.toPlainString());
        } catch (Exception ex) {
            res.errores.add(msgFila(fila, nombreCampo + " inválido '" + bd + "', se deja nulo."));
            return null;
        }
    }


    public class CodigoHelper {
        public static String nextUniqueCodigo(String base, List<String> existentes) {
            int max = 0;
            for (String c : existentes) {
                if (c.equals(base)) {
                    max = Math.max(max, 1);
                } else if (c.startsWith(base + "-")) {
                    String tail = c.substring(base.length() + 1);
                    if (tail.matches("\\d+")) max = Math.max(max, Integer.parseInt(tail));
                }
            }
            int next = max + 1;
            return (next == 1) ? base : base + "-" + next;
        }
    }


    private String msgFila(int n, String m) { return "Fila " + n + ": " + m; }

    private String normalize(String raw) {
        if (raw == null) return "";
        return raw.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_]", "");
    }

    private int pick(Map<String,Integer> idx, String... names) {
        for (String n : names) {
            Integer i = idx.get(normalize(n));
            if (i != null) return i;
        }
        throw new IllegalArgumentException("Campo no encontrado: " + Arrays.toString(names));
    }

    private boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }

    private String nvl(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty()) return null;
        if (t.matches("^[.\\-_\\s]{3,}$")) return null;
        return t;
    }

    private String norm(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty()) return null;
        return t.toUpperCase(java.util.Locale.ROOT);
    }

    private String truncOrNull(String s, int max) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty()) return null;
        return t.length() <= max ? t : t.substring(0, max);
    }

    private String limit(String s, int max) { return (s == null || s.length() <= max) ? s : s.substring(0, max); }

    private Boolean boolVal(Boolean b) { return (b == null) ? null : b; }

    private LocalDate buildDate(Integer y, Integer m, Integer d) {
        try {
            if (y == null || m == null || d == null || y <= 0 || m <= 0 || d <= 0) return null;
            return LocalDate.of(y, m, d);
        } catch (Exception e) {
            return null;
        }
    }

    private LocalDate toLocalDate(Date d) {
        return (d == null) ? null : d.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    private Short bdToShort(BigDecimal bd, String campo, int fila, ImportResult res) {
        if (bd == null) return null;
        try {
            BigInteger bi = bd.toBigIntegerExact();
            int val = bi.intValueExact();
            if (val < Short.MIN_VALUE || val > Short.MAX_VALUE) {
                res.errores.add("Fila " + fila + ": " + campo + " fuera de rango (short): " + val + ", se deja nulo.");
                return null;
            }
            return (short) val;
        } catch (ArithmeticException ex) {
            res.errores.add("Fila " + fila + ": " + campo + " inválido '" + bd + "', se deja nulo.");
            return null;
        }
    }

    private Integer bdToInt(BigDecimal bd, String campo, int fila, ImportResult res) {
        if (bd == null) return null;
        try {
            return bd.toBigIntegerExact().intValueExact();
        } catch (ArithmeticException ex) {
            res.errores.add("Fila " + fila + ": " + campo + " inválido '" + bd + "', se deja nulo.");
            return null;
        }
    }

    private Double bdToDouble(BigDecimal bd, String campo, int fila, ImportResult res) {
        if (bd == null) return null;
        try {
            return bd.doubleValue();
        } catch (Exception ex) {
            res.errores.add("Fila " + fila + ": " + campo + " inválido '" + bd + "', se deja nulo.");
            return null;
        }
    }

    private String numberToPlain(BigDecimal bd) {
        if (bd == null) return null;
        try {
            return bd.toBigIntegerExact().toString();
        } catch (ArithmeticException ex) {
            // tenía decimales; igual devolvemos representación sin espacios
            return bd.stripTrailingZeros().toPlainString();
        }
    }

    private String root(Throwable t) {
        Throwable x = t;
        while (x.getCause() != null) x = x.getCause();
        return x.getClass().getSimpleName() + ": " + String.valueOf(x.getMessage());
    }

    private String gestionKey(String entidadCod, Short gestionPreferida) {
        return (gestionPreferida != null ? "g:" + gestionPreferida : "last") + "|" + entidadCod;
    }

}
