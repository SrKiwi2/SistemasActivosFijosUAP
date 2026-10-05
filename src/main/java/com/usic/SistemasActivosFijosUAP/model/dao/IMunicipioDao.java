package com.usic.SistemasActivosFijosUAP.model.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.usic.SistemasActivosFijosUAP.model.entity.Municipio;

public interface IMunicipioDao extends JpaRepository<Municipio, Long>{
    @Query("SELECT m FROM Municipio m WHERE m.nombre = ?1 AND m.estado = 'ACTIVO'")
    Municipio buscarPorNombre(String nombre);

    @Query("SELECT m FROM Municipio m WHERE m.estado = 'ACTIVO'")
    List<Municipio> listarMunicipios();

    /**
     * Municipios que chocan con un alta o edición: con ese nombre (entre los vigentes) o con
     * ese código (entre TODOS, también los eliminados: los activos ya codificados llevan el
     * código del municipio en su prefijo, así que un código no se reutiliza nunca).
     */
    @Query("""
            SELECT m FROM Municipio m
            WHERE (m.estado = 'ACTIVO' AND UPPER(TRIM(m.nombre)) = :nombre)
               OR UPPER(TRIM(m.codigo)) = :codigo
            """)
    List<Municipio> conNombreOCodigo(@Param("nombre") String nombre, @Param("codigo") String codigo);
}
