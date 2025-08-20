package com.usic.SistemasActivosFijosUAP.model.entity;

import com.usic.SistemasActivosFijosUAP.config.AuditoriaConfig;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "persona")
@Setter
@Getter
public class Persona extends AuditoriaConfig{
    private static final long serialVersionUID = 2629195288020321924L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idPersona;
    private String nombre;
    private String paterno;
    private String materno;
    private String ci;
    private String extension;
    private String correo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_nacionalidad")
    private Nacionalidad nacionalidad;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_genero")
    private Genero genero;

    @OneToOne(cascade = CascadeType.ALL, mappedBy = "persona", fetch = FetchType.LAZY)
    private Usuario Usuario;

    public String getNombreCompleto(){
        if(this.getMaterno() == null){
            return this.getNombre()+" "+this.getPaterno();
        }

        if(this.getPaterno() == null){
            return this.getNombre()+" "+this.getMaterno();
        }

        return this.getNombre()+" "+this.getPaterno()+" "+this.getMaterno();
    }
}
