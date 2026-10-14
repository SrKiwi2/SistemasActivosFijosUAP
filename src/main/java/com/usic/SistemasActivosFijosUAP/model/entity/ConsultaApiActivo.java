package com.usic.SistemasActivosFijosUAP.model.entity;

import java.io.Serializable;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Bitácora de la API pública {@code GET /api/activos/{codigo}/ficha}.
 *
 * <p>La API no pide login, así que esta tabla es lo único que dice quién la usa:
 * una fila por consulta, con el código pedido, el resultado y los datos de la
 * petición (IP, sistema, navegador, origen).
 */
@Entity
@Table(name = "consulta_api_activo",
        indexes = {
            @Index(name = "idx_consultaapi_fecha",  columnList = "fecha_hora"),
            @Index(name = "idx_consultaapi_ip",     columnList = "ip"),
            @Index(name = "idx_consultaapi_codigo", columnList = "codigo_consultado")
        })
@Getter @Setter
public class ConsultaApiActivo implements Serializable {

    public static final String ENCONTRADO = "ENCONTRADO";
    public static final String NO_ENCONTRADO = "NO_ENCONTRADO";
    /** El código existe pero no es de un grupo contable habilitado (p. ej. una mesa). */
    public static final String GRUPO_NO_PERMITIDO = "GRUPO_NO_PERMITIDO";
    public static final String ERROR = "ERROR";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idConsultaApiActivo;

    @Column(name = "fecha_hora", nullable = false)
    private LocalDateTime fechaHora;

    @Column(name = "codigo_consultado", length = 60)
    private String codigoConsultado;

    @Column(length = 20, nullable = false)
    private String resultado;

    /** Nombre que el sistema cliente manda en la cabecera {@code X-Sistema} (opcional). */
    @Column(length = 100)
    private String sistema;

    /** IP del cliente: la primera de {@code X-Forwarded-For} o, si no hay, la de la conexión. */
    @Column(length = 45)
    private String ip;

    /** Cadena completa de {@code X-Forwarded-For}, por si la consulta pasó por varios proxys. */
    @Column(name = "forwarded_for", length = 255)
    private String forwardedFor;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    /** Cabecera {@code Origin}: el sitio web desde el que se llamó, si fue desde un navegador. */
    @Column(length = 255)
    private String origen;

    @Column(length = 512)
    private String referer;
}
