package com.usic.SistemasActivosFijosUAP.model.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Parámetro de configuración editable desde la aplicación (clave → valor). */
@Entity
@Table(name = "parametro_sistema")
@Getter @Setter
public class ParametroSistema {

    @Id
    @Column(name = "clave", length = 80)
    private String clave;

    @Column(name = "valor", length = 500)
    private String valor;

    @Column(name = "fecha_modificacion")
    private LocalDateTime fechaModificacion;

    @Column(name = "usuario", length = 60)
    private String usuario;
}
