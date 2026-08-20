package com.usic.SistemasActivosFijosUAP.model.service.movil;

import org.springframework.stereotype.Component;

import com.usic.SistemasActivosFijosUAP.model.dto.movil.ActivoFichaMovilDTO;
import com.usic.SistemasActivosFijosUAP.model.entity.Activo;
import com.usic.SistemasActivosFijosUAP.model.entity.Auxiliar;
import com.usic.SistemasActivosFijosUAP.model.entity.Entidad;
import com.usic.SistemasActivosFijosUAP.model.entity.GrupoContable;
import com.usic.SistemasActivosFijosUAP.model.entity.Municipio;
import com.usic.SistemasActivosFijosUAP.model.entity.Oficina;
import com.usic.SistemasActivosFijosUAP.model.entity.OrganismoFinanciero;
import com.usic.SistemasActivosFijosUAP.model.entity.Persona;
import com.usic.SistemasActivosFijosUAP.model.entity.Predio;
import com.usic.SistemasActivosFijosUAP.model.entity.Responsable;

/**
 * Convierte la entidad {@code Activo} en la ficha plana que consume la app.
 *
 * <p>Todo el recorrido es a prueba de nulos: en un maestro sincronizado desde
 * ficheros DBF cualquier relación puede faltar, y una ficha incompleta es mucho
 * mejor que un 500 delante de alguien que está en un pasillo con el teléfono.
 */
@Component
public class ActivoMovilMapper {

    public ActivoFichaMovilDTO aFicha(Activo a) {
        if (a == null) return null;

        Oficina  oficina  = a.getOficina();
        Predio   predio   = (oficina != null) ? oficina.getPredio() : null;
        Entidad  entidad  = (predio  != null) ? predio.getEntidad()  : null;
        Municipio municipio = (predio != null) ? predio.getMunicipio() : null;

        String codEntidad = (entidad != null) ? entidad.getEntidadCodigo() : null;

        return new ActivoFichaMovilDTO(
                a.getIdActivo(),
                a.getCodigo(),
                codigoVisual(a.getCodigo(), codEntidad),
                a.getDescripcion(),
                a.getEstado(),
                (a.getEstadoActivo() != null) ? a.getEstadoActivo().getNombre() : null,

                a.getCosto(),
                a.getDepreciacionAcum(),
                a.getVidaUtil(),
                a.getFechaAdquisicion(),
                a.getObserv(),

                grupo(a.getGrupoContable()),
                auxiliar(a.getAuxiliar()),
                organismo(a.getOrganismoFinanciero()),

                ubicacion(oficina, predio, municipio, entidad),
                responsable(a.getResponsable()),

                a.getFecMod() != null ? a.getFecMod() : a.getFechaUlt(),
                a.getUsuMod() != null ? a.getUsuMod() : a.getUsuario());
    }

    /** {@code 01-04-02-03609} + entidad {@code 148} → {@code 148-01-04-02-03609}. */
    public String codigoVisual(String codigo, String codigoEntidad) {
        if (codigo == null) return null;
        if (codigoEntidad == null || codigoEntidad.isBlank()) return codigo;
        return codigoEntidad.trim() + "-" + codigo;
    }

    // ── Bloques ──────────────────────────────────────────────────────────────

    private ActivoFichaMovilDTO.Referencia grupo(GrupoContable g) {
        if (g == null) return null;
        String codigo = (g.getCodDbf() != null) ? String.format("%02d", g.getCodDbf()) : null;
        return new ActivoFichaMovilDTO.Referencia(codigo, g.getNombre());
    }

    private ActivoFichaMovilDTO.Referencia auxiliar(Auxiliar aux) {
        if (aux == null) return null;
        String codigo = (aux.getCodAux() != null) ? String.valueOf(aux.getCodAux()) : null;
        return new ActivoFichaMovilDTO.Referencia(codigo, aux.getNombre());
    }

    private ActivoFichaMovilDTO.Referencia organismo(OrganismoFinanciero of) {
        if (of == null) return null;
        return new ActivoFichaMovilDTO.Referencia(of.getCodOf(), of.getDescripcion());
    }

    private ActivoFichaMovilDTO.Ubicacion ubicacion(Oficina oficina, Predio predio,
                                                    Municipio municipio, Entidad entidad) {
        if (oficina == null && predio == null) return null;

        return new ActivoFichaMovilDTO.Ubicacion(
                (oficina != null) ? oficina.getIdOficina() : null,
                (oficina != null) ? oficina.getNombre() : null,
                (predio  != null) ? predio.getDescrip() : null,
                (predio  != null) ? predio.getCodigo() : null,
                (predio  != null) ? predio.getUnidad() : null,
                (predio  != null) ? predio.getCiudad() : null,
                (municipio != null) ? municipio.getNombre() : null,
                (municipio != null) ? municipio.getCodigo() : null,
                (entidad != null) ? entidad.getDescripcion() : null,
                (entidad != null) ? entidad.getSigla() : null,
                (entidad != null) ? entidad.getEntidadCodigo() : null);
    }

    private ActivoFichaMovilDTO.Responsable responsable(Responsable r) {
        if (r == null) return null;
        Persona p = r.getPersona();
        return new ActivoFichaMovilDTO.Responsable(
                r.getIdResponsable(),
                (p != null) ? p.getNombreCompleto() : null,
                (r.getCargo() != null) ? r.getCargo().getNombre() : null,
                (p != null) ? p.getCi() : null);
    }
}
