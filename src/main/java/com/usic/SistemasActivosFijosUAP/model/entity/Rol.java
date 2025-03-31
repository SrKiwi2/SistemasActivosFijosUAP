package com.usic.SistemasActivosFijosUAP.model.entity;

import java.util.List;

import com.usic.SistemasActivosFijosUAP.config.AuditoriaConfig;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "rol")
@Setter
@Getter
public class Rol extends AuditoriaConfig{
    private static final long serialVersionUID = 2629195288020321924L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idRol;
    private String nombre;

    // Lista de usuarios
    @OneToMany(cascade = CascadeType.ALL, mappedBy = "rol", fetch = FetchType.LAZY)
    private List<Usuario> usuarios;
}
