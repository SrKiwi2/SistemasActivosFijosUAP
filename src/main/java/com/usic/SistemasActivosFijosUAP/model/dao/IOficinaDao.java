package com.usic.SistemasActivosFijosUAP.model.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.usic.SistemasActivosFijosUAP.model.entity.Entidad;
import com.usic.SistemasActivosFijosUAP.model.entity.Oficina;
import com.usic.SistemasActivosFijosUAP.model.entity.Predio;

public interface IOficinaDao extends JpaRepository<Oficina, Long> {

    /** Lo que muestra un buscador de oficinas (select2): sin cargar entidades ni su predio. */
    interface OficinaOpcion {
        Long getId();
        String getUnidad();
        Short getCodOfi();
        String getNombre();
    }

    /**
     * Oficinas activas para un buscador, en UNA consulta (predio en el mismo JOIN).
     * {@code q} ya viene armado: "%CAUN%5%SISTEMAS%" encuentra "CAUN — 5 | SISTEMAS".
     */
    @Query("""
            SELECT o.idOficina AS id, p.unidad AS unidad, o.codOfi AS codOfi, o.nombre AS nombre
            FROM Oficina o JOIN o.predio p
            WHERE o.estado = 'ACTIVO'
              AND UPPER(CONCAT(COALESCE(p.unidad, ''), ' ', CAST(o.codOfi AS String), ' ', COALESCE(o.nombre, ''))) LIKE :q
            ORDER BY p.unidad, o.codOfi
            """)
    List<OficinaOpcion> opciones(@Param("q") String q, Pageable pageable);

    @Query("""
            SELECT o.idOficina AS id, p.unidad AS unidad, o.codOfi AS codOfi, o.nombre AS nombre
            FROM Oficina o JOIN o.predio p
            WHERE o.idOficina = :id
            """)
    Optional<OficinaOpcion> opcion(@Param("id") Long id);

    /** Fila de la tabla de Oficinas: solo lo que se muestra, sin cargar entidades. */
    interface OficinaFila {
        Long getId();
        Short getCodOfi();
        String getNombre();
        String getObserv();
        String getUnidad();
        String getEntidadCodigo();
        Boolean getPendienteDbf();
        Boolean getEsCustodia();
        Long getRegistroIdUsuario();
        Long getModificacionIdUsuario();
        java.util.Date getRegistro();
        java.util.Date getModificacion();
    }

    /**
     * Página de la tabla de Oficinas (paginada en el servidor): predio y entidad en el mismo
     * JOIN. {@code q} ya armado ("%CAUN%5%" encuentra la oficina 5 de CAUN); {@code idPredio}
     * = -1 para todos.
     */
    @Query(value = """
            SELECT o.idOficina AS id, o.codOfi AS codOfi, o.nombre AS nombre, o.observ AS observ,
                   p.unidad AS unidad, e.entidadCodigo AS entidadCodigo, o.pendienteDbf AS pendienteDbf,
                   o.esCustodia AS esCustodia, o.registroIdUsuario AS registroIdUsuario,
                   o.modificacionIdUsuario AS modificacionIdUsuario, o.registro AS registro, o.modificacion AS modificacion
            FROM Oficina o JOIN o.predio p LEFT JOIN p.entidad e
            WHERE o.estado = 'ACTIVO' AND (:idPredio = -1 OR p.idPredio = :idPredio)
              AND UPPER(CONCAT(COALESCE(p.unidad, ''), ' ', CAST(o.codOfi AS String), ' ', COALESCE(o.nombre, ''), ' ',
                               COALESCE(e.entidadCodigo, ''))) LIKE :q
            ORDER BY p.unidad, o.codOfi
            """,
            countQuery = """
            SELECT COUNT(o) FROM Oficina o JOIN o.predio p LEFT JOIN p.entidad e
            WHERE o.estado = 'ACTIVO' AND (:idPredio = -1 OR p.idPredio = :idPredio)
              AND UPPER(CONCAT(COALESCE(p.unidad, ''), ' ', CAST(o.codOfi AS String), ' ', COALESCE(o.nombre, ''), ' ',
                               COALESCE(e.entidadCodigo, ''))) LIKE :q
            """)
    Page<OficinaFila> pagina(@Param("q") String q, @Param("idPredio") Long idPredio, Pageable pageable);

    @Query("SELECT COUNT(o) FROM Oficina o WHERE o.estado = 'ACTIVO'")
    long contarActivas();

    /** [idOficina, responsables vigentes] de las oficinas indicadas (la página visible). */
    @Query("SELECT r.oficina.idOficina, COUNT(r) FROM Responsable r WHERE r.oficina.idOficina IN :ids AND r.estado = 'ACTIVO' GROUP BY r.oficina.idOficina")
    List<Object[]> responsablesPorOficina(@Param("ids") java.util.Collection<Long> ids);

    /** [idOficina, activos no eliminados] de las oficinas indicadas (la página visible). */
    @Query("SELECT a.oficina.idOficina, COUNT(a) FROM Activo a WHERE a.oficina.idOficina IN :ids AND (a.estado IS NULL OR a.estado <> 'ELIMINADO') GROUP BY a.oficina.idOficina")
    List<Object[]> activosPorOficina(@Param("ids") java.util.Collection<Long> ids);

    /** [idPredio, cantidad] de oficinas activas por predio: una consulta agrupada. */
    @Query("SELECT o.predio.idPredio, COUNT(o) FROM Oficina o WHERE o.estado = 'ACTIVO' GROUP BY o.predio.idPredio")
    List<Object[]> contarPorPredio();
    @Query("SELECT o FROM Oficina o WHERE LOWER(o.nombre) = LOWER(?1) AND o.estado = 'ACTIVO'")
    Optional<Oficina> buscarPorNombre(String nombre);

    @Query("SELECT o FROM Oficina o WHERE o.estado = 'ACTIVO'")
    List<Oficina> listarOficinas();

    @Query("SELECT o FROM Oficina o WHERE o.codOfi = ?1 AND o.estado = 'ACTIVO'")
    List<Oficina> buscarPorCodigo(Short codOfi);

    @Query("SELECT o FROM Oficina o WHERE LOWER(o.nombre) LIKE LOWER(CONCAT('%', :termino, '%')) AND o.estado = 'ACTIVO'")
    List<Oficina> buscarPorNombreParcial(@Param("termino") String termino);

    Optional<Oficina> findByPredioAndCodOfi(Predio predio, Short codOfi);

    Optional<Oficina> findByPredioIdPredioAndNombreIgnoreCase(Long idPredio, String nombre);

    @Query("select coalesce(max(o.codOfi), 0) + 1 from Oficina o where o.predio.idPredio = :idPredio")
    Short siguienteCodigo(@Param("idPredio") Long idPredio);

    @Query("select coalesce(max(o.codOfi), 0) from Oficina o where o.predio.id = :idPredio")
    Short maxCodOfiPorPredio(@Param("idPredio") Long idPredio);

    Optional<Oficina> findByNombre(String nombre);

    /* PARA PILLAR OFICINA CON UNIDAD Y CODOFIC */

    @Query("""
            select o
            from Oficina o
            join o.predio p
            where upper(trim(p.unidad)) = upper(trim(:unidad))
                and o.codOfi = :codOfi
            """)
    Optional<Oficina> findByUnidadAndCodOfi(@Param("unidad") String unidad,
            @Param("codOfi") Short codOfi);

    @Query("""
              SELECT o FROM Oficina o
              WHERE (:q IS NULL OR
                     LOWER(o.nombre) LIKE LOWER(CONCAT('%',:q,'%')) OR
                     LOWER(o.usuario) LIKE LOWER(CONCAT('%',:q,'%')) OR
                     LOWER(o.predio.unidad) LIKE LOWER(CONCAT('%',:q,'%')) OR
                     LOWER(o.predio.entidad.entidadCodigo) LIKE LOWER(CONCAT('%',:q,'%')))
              ORDER BY o.nombre ASC
            """)
    List<Oficina> buscarPorQ(@Param("q") String q);

    @Query("""
               select o from Oficina o
               where o.predio.entidad = :entidad
                 and lower(o.predio.unidad) = lower(:unidad)
                 and o.codOfi = :codOfi
            """)
    Optional<Oficina> findByEntidadUnidadAndCodOfi(@Param("entidad") Entidad entidad,
            @Param("unidad") String unidad,
            @Param("codOfi") Short codOfi);

    @Query(value = "SELECT COALESCE(MAX(cod_ofi), 0) + 1 " +
                   "FROM oficina " +
                   "WHERE id_predio = :idPredio AND _estado = 'ACTIVO'",
           nativeQuery = true)
    Short findNextCodOfiByPredioId(@Param("idPredio") Long idPredio);

    List<Oficina> findByPredioIdPredio(Long idPredio);

    Optional<Oficina> findByCodOfiAndPredio(Short codOfi, Predio predio);

    /**
     * Responsables vigentes de la oficina. En el VSIAF cada fila de RESP.DBF apunta a la
     * oficina por ENTIDAD+UNIDAD+CODOFIC: si hay responsables, esa clave ya no se puede
     * cambiar sin dejarlos apuntando a nada.
     */
    @Query("select count(r) from Responsable r where r.oficina.idOficina = :idOficina and r.estado = 'ACTIVO'")
    long contarResponsablesVigentes(@Param("idOficina") Long idOficina);

    /** Oficina de faltantes vigente del predio (a lo sumo una: índice uk_oficina_custodia_predio). */
    @Query("select o from Oficina o where o.predio.idPredio = :idPredio and o.esCustodia = true and o.estado = 'ACTIVO'")
    List<Oficina> custodiasDelPredio(@Param("idPredio") Long idPredio);

    /**
     * Oficinas vigentes del predio que se llaman "FALTANTES…" pero todavía no están marcadas:
     * las que alguien creó a mano en el VSIAF. Se adoptan antes de crear otra.
     */
    @Query("""
            select o from Oficina o
            where o.predio.idPredio = :idPredio and o.esCustodia = false and o.estado = 'ACTIVO'
              and upper(trim(o.nombre)) like 'FALTANTES%'
            order by o.codOfi
            """)
    List<Oficina> candidatasCustodia(@Param("idPredio") Long idPredio);

    /** Activos (no eliminados) ubicados en la oficina; mismo motivo que los responsables. */
    @Query("select count(a) from Activo a where a.oficina.idOficina = :idOficina and (a.estado is null or a.estado <> 'ELIMINADO')")
    long contarActivos(@Param("idOficina") Long idOficina);
}