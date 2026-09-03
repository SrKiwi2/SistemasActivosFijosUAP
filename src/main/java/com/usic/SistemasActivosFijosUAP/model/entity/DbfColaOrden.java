package com.usic.SistemasActivosFijosUAP.model.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Una orden encolada para el worker VFPOLEDB, con su resultado real.
 * <p>
 * Sin esta tabla el SCIAF escribe el JSON en {@code _cola/} y da por hecho que llegó
 * al VSIAF: {@code aprobarActivo} marca el activo como ACTIVO y {@code editarRegistrado}
 * responde "enviado al VSIAF" en el mismo instante, antes de que el worker haya tocado
 * nada. El worker sí deja constancia —mueve cada orden a {@code _hechos/} o a
 * {@code _errores/} junto a un {@code .error.txt}— pero nadie la leía.
 * <p>
 * Acá queda el otro extremo del hilo: una fila por orden emitida, que
 * {@code ColaConfirmacionScheduler} cierra cuando el worker responde. El nombre del
 * archivo es la llave que une las dos puntas.
 */
@Entity
@Table(name = "dbf_cola_orden", indexes = {
    @Index(name = "ix_cola_orden_estado", columnList = "estado"),
    @Index(name = "ix_cola_orden_activo", columnList = "id_activo")
})
@Getter @Setter @NoArgsConstructor
public class DbfColaOrden {

    /** Orden emitida; el worker todavía no la resolvió. */
    public static final String ENCOLADA = "ENCOLADA";
    /** El worker la aplicó: el dato está en el DBF. */
    public static final String OK = "OK";
    /** El worker la rechazó. {@link #mensaje} trae el motivo textual. */
    public static final String ERROR = "ERROR";
    /** Falló y se volvió a emitir en otra orden; deja de contar como error vigente. */
    public static final String REINTENTADA = "REINTENTADA";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_orden")
    private Long idOrden;

    /**
     * Nombre del {@code .json} dejado en {@code _cola/}. Es la llave de correlación con
     * el worker, que conserva el nombre al mover el archivo a {@code _hechos/} o a
     * {@code _errores/}.
     */
    @Column(name = "archivo", length = 120, nullable = false, unique = true)
    private String archivo;

    /** Tabla DBF destino: ACTUAL, RESP, OFICINA o AUXILIAR. */
    @Column(name = "tabla", length = 20, nullable = false)
    private String tabla;

    /** INSERT o UPDATE. */
    @Column(name = "operacion", length = 10, nullable = false)
    private String operacion;

    /** Clave del WHERE, legible, para diagnosticar sin abrir el JSON. */
    @Column(name = "clave", length = 300)
    private String clave;

    @Column(name = "estado", length = 20, nullable = false)
    private String estado = ENCOLADA;

    /** Motivo del rechazo, tal cual lo escribió el worker en el {@code .error.txt}. */
    @Column(name = "mensaje", columnDefinition = "text")
    private String mensaje;

    /** Activo al que corresponde la orden, cuando la tabla es ACTUAL. */
    @Column(name = "id_activo")
    private Long idActivo;

    /** Código del activo, o del auxiliar / oficina / responsable según la tabla. */
    @Column(name = "referencia", length = 120)
    private String referencia;

    @Column(name = "usuario", length = 60)
    private String usuario;

    @Column(name = "fecha_encolado", nullable = false)
    private LocalDateTime fechaEncolado = LocalDateTime.now();

    @Column(name = "fecha_resuelto")
    private LocalDateTime fechaResuelto;

    /**
     * Cuántas veces se reintentó esta cadena antes de llegar a esta orden.
     * <p>
     * 0 en el primer envío. Cuando {@code ColaVsiafReintentoScheduler} reintenta un
     * ACTUAL/UPDATE rechazado, la orden nueva hereda este valor +1; al llegar al tope
     * deja de insistir y el rechazo queda firme (con su aviso ya emitido).
     */
    @Column(name = "intentos", nullable = false)
    private Integer intentos = 0;

    /** ¿Sigue esperando al worker? */
    public boolean estaPendiente() {
        return ENCOLADA.equals(estado);
    }
}
