package com.usic.SistemasActivosFijosUAP.model.dto.interoperabilidad;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Representa un activo "divergente" entre PostgreSQL y el DBF del VSIAF, es decir,
 * un código que existe en un lado pero no en el otro.
 *
 * <p>El constructor de 6 argumentos lo usa la consulta JPQL de proyección
 * {@code IActivoDao.listarParaConciliacion()}; los campos {@code origen} y
 * {@code clasificacion} los completa {@link com.usic.SistemasActivosFijosUAP.model.service.ConciliacionService}
 * después de comparar los conjuntos de códigos.</p>
 */
@Getter
@Setter
@NoArgsConstructor
public class DivergenciaActivoDto {

    private Long idActivo;
    private String codigo;
    private String nombre;
    private String descripcion;
    private String estado;
    private LocalDateTime fechaUltimaSync;

    /** "BD" (existe en PostgreSQL, falta en el DBF) o "VSIAF" (existe en el DBF, falta en BD). */
    private String origen;

    /**
     * Diagnóstico legible para el operador:
     * <ul>
     *   <li>PENDIENTE_SYNC      → registrado en SCIAF, aún no aprobado/empujado al VSIAF (normal).</li>
     *   <li>ANOMALIA_ACTIVO     → marcado ACTIVO en BD pero sin respaldo en el DBF (revisar: prueba, borrado en VSIAF o inconsistencia).</li>
     *   <li>SOLO_VSIAF          → existe en el DBF del VSIAF pero no llegó a la BD (p. ej. descartado por faltar oficina/responsable/grupo).</li>
     *   <li>OTRO_ESTADO         → cualquier otro estado en BD sin reflejo en el DBF.</li>
     * </ul>
     */
    private String clasificacion;

    /** Constructor de proyección usado por la consulta JPQL (orden y tipos deben coincidir). */
    public DivergenciaActivoDto(Long idActivo, String codigo, String nombre,
                                String descripcion, String estado,
                                LocalDateTime fechaUltimaSync) {
        this.idActivo = idActivo;
        this.codigo = codigo;
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.estado = estado;
        this.fechaUltimaSync = fechaUltimaSync;
    }
}
