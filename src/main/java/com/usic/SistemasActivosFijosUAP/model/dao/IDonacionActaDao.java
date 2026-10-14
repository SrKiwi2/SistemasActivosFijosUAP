package com.usic.SistemasActivosFijosUAP.model.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.usic.SistemasActivosFijosUAP.model.entity.DonacionActa;

public interface IDonacionActaDao extends JpaRepository<DonacionActa, Long> {

    /**
     * Turno de las altas de donaciones: mientras dure la transacción nadie más saca un
     * número de acta ni un correlativo D (se libera solo al confirmar o deshacer).
     */
    @Query(value = "select count(*) from (select pg_advisory_xact_lock(:clave)) t", nativeQuery = true)
    long turno(@Param("clave") long clave);

    Optional<DonacionActa> findFirstByTokenRegistro(String tokenRegistro);

    @Query("select coalesce(max(d.correlativo), 0) from DonacionActa d where d.gestion = :gestion")
    int maxCorrelativo(@Param("gestion") int gestion);

    /**
     * Último correlativo de donación usado en un prefijo {@code MUN-PRED-GRP}: cuenta los
     * códigos {@code <prefijo>-00001D} y también los del formato inicial
     * {@code <prefijo>-D00001}, para que el cambio de formato no repita números. El generador
     * normal solo cuenta 5 dígitos sin letra, así que las dos series no se tocan.
     * <p>
     * A diferencia de la serie normal, un código D <b>no se reutiliza</b>: también cuenta
     * {@code codigo_anulado} de las donaciones anuladas. Sus etiquetas con QR pudieron
     * imprimirse y pegarse, y el historial se busca por código: reutilizarlo haría que una
     * etiqueta vieja o un evento viejo apunten a otro bien.
     */
    @Query(value = """
            select coalesce(max(cast(substring(substring(t.c from char_length(:prefijo) + 2) from '([0-9]{5})') as integer)), 0)
            from (select codigo as c from activo where codigo like concat(:prefijo, '-%')
                  union all
                  select codigo_anulado from activo where codigo_anulado like concat(:prefijo, '-%')) t
            where substring(t.c from char_length(:prefijo) + 2) ~ '^([0-9]{5}D|D[0-9]{5})$'
            """, nativeQuery = true)
    int maxCorrelativoDonacion(@Param("prefijo") String prefijo);

    /**
     * Listado de actas, la más reciente primero. {@code q} (ya en mayúsculas, con %)
     * busca en número, donante, C.I./NIT, documento de respaldo y en los códigos de sus
     * bienes; vacío = todas.
     */
    @Query(value = """
            select d from DonacionActa d
              left join fetch d.oficina o
              left join fetch o.predio
            where d.estado in ('ACTIVO', 'ANULADA')
              and (:q = '' or upper(d.numero) like :q or upper(d.donanteNombre) like :q
                   or upper(coalesce(d.donanteDocumento, '')) like :q
                   or upper(coalesce(d.documentoRespaldo, '')) like :q
                   or exists (select 1 from DonacionBien b where b.donacion = d
                              and upper(coalesce(b.activo.codigo, b.activo.codigoAnulado)) like :q))
            order by d.idDonacion desc
            """,
           countQuery = """
            select count(d) from DonacionActa d
            where d.estado in ('ACTIVO', 'ANULADA')
              and (:q = '' or upper(d.numero) like :q or upper(d.donanteNombre) like :q
                   or upper(coalesce(d.donanteDocumento, '')) like :q
                   or upper(coalesce(d.documentoRespaldo, '')) like :q
                   or exists (select 1 from DonacionBien b where b.donacion = d
                              and upper(coalesce(b.activo.codigo, b.activo.codigoAnulado)) like :q))
            """)
    Page<DonacionActa> listar(@Param("q") String q, Pageable pageable);

    @Query("select count(d) from DonacionActa d where d.estado in ('ACTIVO', 'ANULADA')")
    long contarTodas();

    /** [actas, bienes, valor total] de las actas vigentes. */
    @Query("""
            select count(d), coalesce(sum(d.cantidadBienes), 0), coalesce(sum(d.valorTotal), 0)
            from DonacionActa d where d.estado = 'ACTIVO'
            """)
    List<Object[]> totales();

    @Query("""
            select d from DonacionActa d
              left join fetch d.oficina o
              left join fetch o.predio
              left join fetch d.responsable r
              left join fetch r.persona
            where d.idDonacion = :id
            """)
    Optional<DonacionActa> buscarConDestino(@Param("id") Long id);
}
