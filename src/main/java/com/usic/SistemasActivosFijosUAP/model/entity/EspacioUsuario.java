package com.usic.SistemasActivosFijosUAP.model.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * Lo que cada usuario dejó a medio hacer: las pestañas que tenía abiertas y el borrador de
 * cada pantalla (activos cargados, destino elegido, textos escritos…).
 * <p>
 * Existe para que ir a consultar un dato a otra pantalla —o cerrar el sistema y volver
 * mañana— no obligue a rehacer la selección desde cero. Es estado de trabajo, no dato del
 * dominio: si se borra, no se pierde nada registrado.
 */
@Entity
@Table(name = "espacio_usuario",
       uniqueConstraints = @UniqueConstraint(name = "uk_espacio_usuario_clave", columnNames = { "id_usuario", "clave" }))
@Getter @Setter
public class EspacioUsuario {

    /** Clave reservada con la lista de pestañas abiertas. */
    public static final String CLAVE_PESTANAS = "__pestanas";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_espacio")
    private Long idEspacio;

    @Column(name = "id_usuario", nullable = false)
    private Long idUsuario;

    /** {@link #CLAVE_PESTANAS} o el nombre de la pantalla ("transferencia", "activo"…). */
    @Column(name = "clave", length = 80, nullable = false)
    private String clave;

    @Column(name = "datos_json", columnDefinition = "text")
    private String datosJson;

    @Column(name = "fecha_actualizacion")
    private LocalDateTime fechaActualizacion = LocalDateTime.now();
}
