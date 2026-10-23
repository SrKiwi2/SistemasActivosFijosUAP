package com.usic.SistemasActivosFijosUAP.model.dto.responsable;

import java.util.List;

public class ResponsableCardDataDTO {
    // Bloque A: Información del Responsable
    private String nombreCompleto;
    private String ci;
    private String cargo;

    // Bloque B: Datos de la Ubicación
    private String oficinaNumero;
    private String oficinaNombre;
    private String predio;
    private Long idOficina;
    private Long idPredio;
    private String idEnc;
    private String estado;

    // Bloque C: Resumen de Activos
    private Long totalActivos;
    private List<ResponsableActivoGrupoDTO> desglosePorGrupo;

    public ResponsableCardDataDTO() {}

    public ResponsableCardDataDTO(String nombreCompleto, String ci, String cargo,
                                   String oficinaNumero, String oficinaNombre, String predio,
                                   Long totalActivos, List<ResponsableActivoGrupoDTO> desglosePorGrupo) {
        this.nombreCompleto = nombreCompleto;
        this.ci = ci;
        this.cargo = cargo;
        this.oficinaNumero = oficinaNumero;
        this.oficinaNombre = oficinaNombre;
        this.predio = predio;
        this.totalActivos = totalActivos;
        this.desglosePorGrupo = desglosePorGrupo;
    }

    public String getNombreCompleto() {
        return nombreCompleto;
    }

    public void setNombreCompleto(String nombreCompleto) {
        this.nombreCompleto = nombreCompleto;
    }

    public String getCi() {
        return ci;
    }

    public void setCi(String ci) {
        this.ci = ci;
    }

    public String getCargo() {
        return cargo;
    }

    public void setCargo(String cargo) {
        this.cargo = cargo;
    }

    public String getOficinaNumero() {
        return oficinaNumero;
    }

    public void setOficinaNumero(String oficinaNumero) {
        this.oficinaNumero = oficinaNumero;
    }

    public String getOficinaNombre() {
        return oficinaNombre;
    }

    public void setOficinaNombre(String oficinaNombre) {
        this.oficinaNombre = oficinaNombre;
    }

    public String getPredio() {
        return predio;
    }

    public void setPredio(String predio) {
        this.predio = predio;
    }

    public Long getIdOficina() { return idOficina; }
    public void setIdOficina(Long idOficina) { this.idOficina = idOficina; }
    public Long getIdPredio() { return idPredio; }
    public void setIdPredio(Long idPredio) { this.idPredio = idPredio; }
    public String getIdEnc() { return idEnc; }
    public void setIdEnc(String idEnc) { this.idEnc = idEnc; }
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public Long getTotalActivos() {
        return totalActivos;
    }

    public void setTotalActivos(Long totalActivos) {
        this.totalActivos = totalActivos;
    }

    public List<ResponsableActivoGrupoDTO> getDesglosePorGrupo() {
        return desglosePorGrupo;
    }

    public void setDesglosePorGrupo(List<ResponsableActivoGrupoDTO> desglosePorGrupo) {
        this.desglosePorGrupo = desglosePorGrupo;
    }
}
