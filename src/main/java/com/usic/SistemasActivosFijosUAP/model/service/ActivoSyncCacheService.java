package com.usic.SistemasActivosFijosUAP.model.service;

import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.usic.SistemasActivosFijosUAP.model.IService.IEstadoActivoService;
import com.usic.SistemasActivosFijosUAP.model.IService.IGrupoContableService;
import com.usic.SistemasActivosFijosUAP.model.IService.IOrganismoFinancieroService;
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

@Service
@RequiredArgsConstructor
public class ActivoSyncCacheService {
    @PersistenceContext
    private EntityManager em;

    private final IGrupoContableService grupoContableService;
    private final IOrganismoFinancieroService organismoFinancieroService;
    private final IEstadoActivoService estadoActivoService;

    /**
     * Oficinas con predio y entidad en una sola query.
     * Clave: "entidadCodigo|unidad|codOfi"
     */
    @Transactional(readOnly = true)
    public Map<String, Oficina> oficinas() {
        return em.createQuery(
            "SELECT o FROM Oficina o " +
            "JOIN FETCH o.predio p " +
            "JOIN FETCH p.entidad e " +
            "WHERE o.estado = 'ACTIVO'", Oficina.class)
            .getResultList()
            .stream()
            .collect(Collectors.toMap(
                o -> o.getPredio().getEntidad().getEntidadCodigo() + "|" +
                     o.getPredio().getUnidad() + "|" + o.getCodOfi(),
                o -> o,
                (a, b) -> a
            ));
    }

    /**
     * Responsables con oficina cargada.
     * Clave: "idOficina|codigoFuncionario"
     */
    @Transactional(readOnly = true)
    public Map<String, Responsable> responsables() {
        return em.createQuery(
            "SELECT r FROM Responsable r " +
            "JOIN FETCH r.oficina o " +
            "WHERE r.estado = 'ACTIVO' AND r.codigoFuncionario IS NOT NULL",
            Responsable.class)
            .getResultList()
            .stream()
            .collect(Collectors.toMap(
                r -> r.getOficina().getIdOficina() + "|" + r.getCodigoFuncionario().trim(),
                r -> r,
                (a, b) -> a
            ));
    }

    /**
     * Grupos contables.
     * Clave: codContable (Integer)
     */
    @Transactional(readOnly = true)
    public Map<Integer, GrupoContable> grupos() {
        return grupoContableService.listarGruposContables().stream()
            .filter(g -> g.getCodContable() != null)
            .collect(Collectors.toMap(GrupoContable::getCodContable, g -> g, (a, b) -> a));
    }

    /**
     * Auxiliares con predio y grupoContable.
     * Clave: "idPredio|idGrupoContable|codAux"
     */
    @Transactional(readOnly = true)
    public Map<String, Auxiliar> auxiliares() {
        return em.createQuery(
            "SELECT a FROM Auxiliar a " +
            "JOIN FETCH a.predio p " +
            "JOIN FETCH a.grupoContable g " +
            "WHERE a.estado = 'ACTIVO'", Auxiliar.class)
            .getResultList()
            .stream()
            .collect(Collectors.toMap(
                a -> a.getPredio().getIdPredio() + "|" +
                     a.getGrupoContable().getIdGrupoContable() + "|" + a.getCodAux(),
                a -> a,
                (a, b) -> a
            ));
    }

    /**
     * Organismos financieros.
     * Clave: codOf (String)
     */
    @Transactional(readOnly = true)
    public Map<String, OrganismoFinanciero> organismos() {
        return organismoFinancieroService.findAll().stream()
            .filter(o -> o.getCodOf() != null)
            .collect(Collectors.toMap(o -> o.getCodOf().trim(), o -> o, (a, b) -> a));
    }

    /**
     * Estados de activo.
     * Clave: codEstado (Short) — del DBF CODESTADO
     */
    @Transactional(readOnly = true)
    public Map<Short, EstadoActivo> estadosActivo() {
        return estadoActivoService.findAll().stream()
            // Filtramos nulos y vacíos para evitar NullPointerException o NumberFormatException
            .filter(e -> e.getCodigo() != null && !e.getCodigo().trim().isEmpty())
            .collect(Collectors.toMap(
                // Transformamos el String (ej: "1") a Short
                e -> Short.valueOf(e.getCodigo().trim()), 
                e -> e,
                (a, b) -> a
            ));
    }

    /**
     * Activos existentes en BD (solo código y hash, sin joins).
     * Clave: codigo
     */
    @Transactional(readOnly = true)
    public Map<String, Activo> activos() {
        return em.createQuery(
            "SELECT a FROM Activo a " +
            "WHERE a.estado <> 'ELIMINADO' AND a.codigo IS NOT NULL",
            Activo.class)
            .getResultList()
            .stream()
            .collect(Collectors.toMap(Activo::getCodigo, a -> a, (a, b) -> a));
    }
}
