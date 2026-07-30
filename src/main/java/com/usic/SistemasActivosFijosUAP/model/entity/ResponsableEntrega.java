package com.usic.SistemasActivosFijosUAP.model.entity;

import java.time.LocalDate;

import com.usic.SistemasActivosFijosUAP.config.AuditoriaConfig;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "responsable_entrega")
@Setter
@Getter
public class ResponsableEntrega extends AuditoriaConfig {
    private static final long serialVersionUID = 2629195288020321925L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idResponsableEntrega;

    @NotBlank
    @Size(max = 255)
    @Column(name = "nombre", length = 255, nullable = false)
    private String nombre;

    @Size(max = 255)
    @Column(name = "cargo", length = 255)
    private String cargo;

    @Column(name = "genero", length = 1)
    private String genero;

    @Column(name = "_seleccionado")
    private Boolean seleccionado = false;

    @Column(name = "_usuario")
    private String usuario;

    @Column(name = "_fecha_ult")
    private LocalDate fechaUlt;
}
