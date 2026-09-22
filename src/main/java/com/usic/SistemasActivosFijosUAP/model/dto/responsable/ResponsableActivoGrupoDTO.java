package com.usic.SistemasActivosFijosUAP.model.dto.responsable;

/**
 * Cuántos bienes tiene un responsable en cada grupo contable.
 *
 * <p>Lleva el id además del nombre porque la tarjeta de Consulta de Activos usa cada
 * grupo como filtro: al hacer clic, la tabla se recarga por {@code idGrupoContable} y no
 * por el texto, que puede repetirse o cambiar.
 */
public class ResponsableActivoGrupoDTO {
    private Long idGrupoContable;
    private String grupoContable;
    private Long cantidad;

    public ResponsableActivoGrupoDTO(Long idGrupoContable, String grupoContable, Long cantidad) {
        this.idGrupoContable = idGrupoContable;
        this.grupoContable = grupoContable;
        this.cantidad = cantidad;
    }

    public Long getIdGrupoContable() {
        return idGrupoContable;
    }

    public void setIdGrupoContable(Long idGrupoContable) {
        this.idGrupoContable = idGrupoContable;
    }

    public String getGrupoContable() {
        return grupoContable;
    }

    public void setGrupoContable(String grupoContable) {
        this.grupoContable = grupoContable;
    }

    public Long getCantidad() {
        return cantidad;
    }

    public void setCantidad(Long cantidad) {
        this.cantidad = cantidad;
    }
}
