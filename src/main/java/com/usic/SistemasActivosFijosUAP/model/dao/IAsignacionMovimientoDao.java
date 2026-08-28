package com.usic.SistemasActivosFijosUAP.model.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.usic.SistemasActivosFijosUAP.model.entity.AsignacionMovimiento;

public interface IAsignacionMovimientoDao extends JpaRepository<AsignacionMovimiento, Long> {

    /**
     * Historial del acta: lo que le pasó como origen y también como destino.
     * <p>
     * Las dos puntas importan. En el acta original hay que ver "salieron 2 bienes al
     * acta X"; en la hija, "estos 2 bienes vinieron del acta Y". Filtrar solo por origen
     * dejaría a las actas nuevas sin explicación de su propio nacimiento.
     */
    @Query("""
        SELECT DISTINCT m FROM AsignacionMovimiento m
        LEFT JOIN FETCH m.detalles
        WHERE m.asignacionOrigen.idAsignacionActivo = :idActa
           OR m.asignacionDestino.idAsignacionActivo = :idActa
        ORDER BY m.fecha DESC
        """)
    List<AsignacionMovimiento> historialDeActa(@Param("idActa") Long idActa);

    /** ¿Cuántas operaciones registra cada acta? Para marcar en la lista las que se tocaron. */
    @Query("""
        SELECT m.asignacionOrigen.idAsignacionActivo, COUNT(m)
        FROM AsignacionMovimiento m
        WHERE m.asignacionOrigen.idAsignacionActivo IN :ids
        GROUP BY m.asignacionOrigen.idAsignacionActivo
        """)
    List<Object[]> conteoPorActa(@Param("ids") List<Long> ids);
}
