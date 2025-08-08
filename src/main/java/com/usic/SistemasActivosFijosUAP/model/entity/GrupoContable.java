package com.usic.SistemasActivosFijosUAP.model.entity;

import com.usic.SistemasActivosFijosUAP.config.AuditoriaConfig;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "grupo_contable")
@Setter
@Getter
public class GrupoContable extends AuditoriaConfig{
    private static final long serialVersionUID = 2629195288020321924L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idGrupoContable;
    private Integer codContable;
    private String nombre;
    private Integer vidaUtil;
    private Boolean depreciar;
    private Boolean actualizar;
    private String codigo;
}
