package com.usic.SistemasActivosFijosUAP.model.ServiceImpl;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import com.usic.SistemasActivosFijosUAP.model.IService.IAsignacionActivoService;
import com.usic.SistemasActivosFijosUAP.model.dao.IAsignacionActivoDao;
import com.usic.SistemasActivosFijosUAP.model.dto.FiltrosAsignacionDTO;
import com.usic.SistemasActivosFijosUAP.model.dto.ResumenAsignacionDTO;
import com.usic.SistemasActivosFijosUAP.model.dto.ResumenListadoAsignacionDTO;
import com.usic.SistemasActivosFijosUAP.model.entity.Activo;
import com.usic.SistemasActivosFijosUAP.model.entity.AsignacionActivo;
import com.usic.SistemasActivosFijosUAP.model.entity.DetalleAsignacionActivo;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AsignacionActivoServiceImpl implements IAsignacionActivoService {
    private final IAsignacionActivoDao dao;

    @Override
    public List<AsignacionActivo> findAll() {
        return dao.findAll();
    }

    @Override
    public AsignacionActivo findById(Long idEntidad) {
        return dao.findById(idEntidad).orElse(null);
    }

    @Override
    public AsignacionActivo save(AsignacionActivo entidad) {
        return dao.save(entidad);
    }

    @Override
    public void deleteById(Long idEntidad) {
        dao.deleteById(idEntidad);
    }

    @Override
    public List<AsignacionActivo> listarConDetalles() {
        return dao.listarConDetalles();
    }

    @Override
    public List<Activo> listarPendientesSinAsignacion() {
        return dao.listarPendientesSinAsignacion();
    }

    @Override
    public Map<Long, ResumenAsignacionDTO> resumenPorAsignacion(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return Map.of();
        return dao.resumenPorAsignacion(ids).stream()
                .map(ResumenAsignacionDTO::desdeFila)
                .collect(Collectors.toMap(ResumenAsignacionDTO::getIdAsignacionActivo, r -> r));
    }

    @Override
    public Optional<AsignacionActivo> findByActivo(Activo activo) {
        // La consulta puede traer más de una: si así fuera, la vigente es la más
        // reciente. Antes esto reventaba con NonUniqueResultException en vez de elegir.
        List<AsignacionActivo> actas = dao.findByActivo(activo);
        return actas.isEmpty() ? Optional.empty() : Optional.of(actas.get(0));
    }

    /*
     * Acá vivía siguienteNumeroAsignacion(), que emitía correlativos ASG-2026-0144.
     * Se quitó: el número del acta es el preventivo que carga el área contable, y
     * generar un segundo correlativo dejaba el mismo documento con dos identificadores,
     * uno en el papel y otro en la pantalla. Lo escribe AsignacionActivo.asignarDocumento().
     */

    @Override
    public Optional<AsignacionActivo> findByIdConDetalles(Long id) {
        return dao.findByIdConDetalles(id);
    }

    @Override
    public List<AsignacionActivo> findAllByIdInConDetalles(List<Long> ids) {
        return dao.findAllByIdInConDetalles(ids);
    }

    @Override
    public List<Integer> gestionesConActas() {
        return dao.gestionesConActas();
    }

    @Override
    public List<DetalleAsignacionActivo> buscarBienesConSuActa(String texto, Long excluirActa) {
        // Tope corto a propósito: es un buscador para elegir, no un listado. Traer cientos
        // de filas a un desplegable lo vuelve inutilizable.
        List<DetalleAsignacionActivo> encontrados = dao.buscarBienesConSuActa(texto, excluirActa);
        return encontrados.size() > 25 ? encontrados.subList(0, 25) : encontrados;
    }

    @Override
    public List<AsignacionActivo> buscarActasPorTexto(String texto, Long excluir) {
        List<AsignacionActivo> encontradas = dao.buscarActasPorTexto(texto, excluir);
        return encontradas.size() > 25 ? encontradas.subList(0, 25) : encontradas;
    }

    @Override
    public Page<AsignacionActivo> buscarConFiltros(FiltrosAsignacionDTO filtros, String orden,
                                                   boolean descendente, Pageable pagina) {
        return dao.findAll(especificacion(filtros, orden, descendente), pagina);
    }

    @Override
    public List<AsignacionActivo> buscarConFiltrosConDetalles(FiltrosAsignacionDTO filtros, String orden,
                                                               boolean descendente) {
        List<AsignacionActivo> resumen = dao.findAll(
                especificacion(filtros, orden, descendente), Pageable.unpaged()).getContent();

        List<Long> ids = resumen.stream().map(AsignacionActivo::getIdAsignacionActivo).toList();
        if (ids.isEmpty()) return List.of();

        // El fetch join de findAllByIdInConDetalles no respeta el orden de :ids — se
        // reordena según el orden que ya resolvió la especificación de arriba.
        Map<Long, AsignacionActivo> porId = dao.findAllByIdInConDetalles(ids).stream()
                .collect(Collectors.toMap(AsignacionActivo::getIdAsignacionActivo, a -> a));
        return ids.stream().map(porId::get).filter(Objects::nonNull).toList();
    }

    @Override
    public ResumenListadoAsignacionDTO resumenListado(FiltrosAsignacionDTO filtros) {
        Specification<AsignacionActivo> base = especificacion(filtros, null, false);

        long total = dao.count(base);
        if (total == 0) return ResumenListadoAsignacionDTO.VACIO;

        // Cada tarjeta es un count sobre el MISMO conjunto filtrado más una condición.
        // Son consultas de conteo con un EXISTS, no recorridos de la colección.
        long completas = dao.count(base.and(soloConSincronizacion(FiltrosAsignacionDTO.COMPLETAS)));
        long parciales = dao.count(base.and(soloConSincronizacion(FiltrosAsignacionDTO.PARCIALES)));
        long conError  = dao.count(base.and((root, query, cb) ->
                existeDetalle(root, query, cb,
                        (d, c) -> c.equal(d.get("activo").get("sincVsiaf"), Activo.SINC_ERROR))));

        return new ResumenListadoAsignacionDTO(total, completas, parciales, conError);
    }

    /* ════════════════════════════════════════════════════════════════════════════
     * Armado de la consulta
     *
     * Todo lo que involucra los detalles del acta va por subconsultas EXISTS y no por
     * joins. Un join a una colección multiplica las filas del acta y rompe dos cosas a
     * la vez: el conteo de la paginación (cuenta líneas, no actas) y las tarjetas de
     * resumen. Con EXISTS el acta se cuenta una sola vez, esté con uno o con cien bienes.
     * ════════════════════════════════════════════════════════════════════════════ */

    private Specification<AsignacionActivo> especificacion(FiltrosAsignacionDTO f, String orden, boolean descendente) {
        return (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();

            if (f.tipo() != null)   ps.add(cb.equal(root.get("tipoAsignacion"), f.tipo()));
            if (f.estado() != null) ps.add(cb.equal(root.get("estadoAsignacion"), f.estado()));

            if (f.desde() != null) {
                ps.add(cb.greaterThanOrEqualTo(root.get("fechaAsignacion"),
                        LocalDate.parse(f.desde()).atStartOfDay()));
            }
            if (f.hasta() != null) {
                ps.add(cb.lessThanOrEqualTo(root.get("fechaAsignacion"),
                        LocalDate.parse(f.hasta()).atTime(LocalTime.MAX)));
            }
            if (f.gestion() != null) {
                // Rango de fechas en vez de EXTRACT(YEAR ...): así el filtro puede usar el
                // índice de fecha_asignacion en lugar de calcular el año fila por fila.
                ps.add(cb.between(root.get("fechaAsignacion"),
                        LocalDate.of(f.gestion(), 1, 1).atStartOfDay(),
                        LocalDate.of(f.gestion(), 12, 31).atTime(LocalTime.MAX)));
            }
            if (f.idResponsable() != null) {
                ps.add(cb.equal(root.get("responsable").get("idResponsable"), f.idResponsable()));
            }
            if (f.oficina() != null) {
                ps.add(cb.like(cb.lower(root.get("oficinaDestino").get("nombre")),
                        "%" + f.oficina().toLowerCase() + "%"));
            }
            if (f.idUsuarioRegistro() != null) {
                ps.add(cb.equal(root.get("registroIdUsuario"), f.idUsuarioRegistro()));
            }
            if (f.comprobante() != null) {
                ps.add(cb.equal(root.get("comprobante"), f.comprobante()));
            }

            Predicate sincronizacion = predicadoSincronizacion(f.sincronizacion(), root, query, cb);
            if (sincronizacion != null) ps.add(sincronizacion);

            if (f.soloConError()) {
                ps.add(existeDetalle(root, query, cb,
                        (d, c) -> c.equal(d.get("activo").get("sincVsiaf"), Activo.SINC_ERROR)));
            }

            if (f.buscar() != null) ps.add(predicadoBusqueda(f.buscar(), root, query, cb));

            aplicarOrden(orden, descendente, root, query, cb);

            return ps.isEmpty() ? cb.conjunction() : cb.and(ps.toArray(new Predicate[0]));
        };
    }

    /** Specification suelta con solo el criterio de sincronización, para las tarjetas. */
    private Specification<AsignacionActivo> soloConSincronizacion(String modo) {
        return (root, query, cb) -> {
            Predicate p = predicadoSincronizacion(modo, root, query, cb);
            return p != null ? p : cb.conjunction();
        };
    }

    /**
     * Qué actas entran según su avance hacia el VSIAF.
     * <p>
     * Este módulo lista movimientos ya registrados, así que por defecto quedan fuera las
     * actas que nadie subió todavía: esas se atienden en la bandeja de Pendientes. Las
     * parciales —con parte de los bienes arriba y parte no— sí entran, porque son
     * justamente las que hay que terminar de completar.
     */
    private Predicate predicadoSincronizacion(String modo, Root<AsignacionActivo> root,
                                              CriteriaQuery<?> query, CriteriaBuilder cb) {
        if (modo == null || FiltrosAsignacionDTO.TODAS.equals(modo)) return null;

        Predicate haySubidos    = hayActivoEnEstado(root, query, cb, "ACTIVO");
        Predicate hayPendientes = hayActivoEnEstado(root, query, cb, "PENDIENTE");

        return switch (modo) {
            case FiltrosAsignacionDTO.COMPLETAS -> cb.and(haySubidos, cb.not(hayPendientes));
            case FiltrosAsignacionDTO.PARCIALES -> cb.and(haySubidos, hayPendientes);
            default -> haySubidos;   // SUBIDAS
        };
    }

    private Predicate hayActivoEnEstado(Root<AsignacionActivo> root, CriteriaQuery<?> query,
                                        CriteriaBuilder cb, String estado) {
        return existeDetalle(root, query, cb,
                (d, c) -> c.equal(c.upper(d.get("activo").get("estado")), estado));
    }

    /**
     * Texto libre sobre lo que la gente realmente escribe para encontrar un acta.
     * <p>
     * Antes solo miraba {@code numeroAsignacion} —que nunca se llenaba— y
     * {@code codigoCompleto}, aunque el campo invitara a buscar por responsable. Buscar
     * por apellido o por el código de un bien no devolvía nada y el usuario concluía que
     * el acta no existía.
     */
    private Predicate predicadoBusqueda(String texto, Root<AsignacionActivo> root,
                                        CriteriaQuery<?> query, CriteriaBuilder cb) {
        // Se sacan los paréntesis del término: el documento se guarda como "Prev. 1234"
        // pero en el acta impresa aparece como "(Prev. 1234)" y la gente lo copia así.
        String patron = "%" + texto.toLowerCase().replace("(", "").replace(")", "").trim() + "%";

        Join<Object, Object> responsable = root.join("responsable", JoinType.LEFT);
        Join<Object, Object> persona     = responsable.join("persona", JoinType.LEFT);

        List<Predicate> ors = new ArrayList<>();
        ors.add(cb.like(cb.lower(root.get("numeroAsignacion")), patron));
        ors.add(cb.like(cb.lower(root.get("codigoCompleto")), patron));
        ors.add(cb.like(cb.lower(root.get("codigoDocumento")), patron));
        ors.add(cb.like(cb.lower(persona.get("nombre")), patron));
        ors.add(cb.like(cb.lower(persona.get("paterno")), patron));
        ors.add(cb.like(cb.lower(persona.get("materno")), patron));
        ors.add(cb.like(cb.lower(persona.get("ci")), patron));

        // Encontrar el acta por el bien que contiene es el caso más frecuente de todos.
        ors.add(existeDetalle(root, query, cb, (d, c) -> c.or(
                c.like(c.lower(d.get("activo").get("codigo")), patron),
                c.like(c.lower(d.get("codigoActivoSnapshot")), patron),
                c.like(c.lower(d.get("activo").get("descripcion")), patron))));

        return cb.or(ors.toArray(new Predicate[0]));
    }

    /** {@code EXISTS} sobre los detalles vigentes del acta que cumplan la condición dada. */
    private Predicate existeDetalle(Root<AsignacionActivo> root, CriteriaQuery<?> query, CriteriaBuilder cb,
                                    BiFunction<Root<DetalleAsignacionActivo>, CriteriaBuilder, Predicate> condicion) {
        Subquery<Integer> sub = query.subquery(Integer.class);
        Root<DetalleAsignacionActivo> d = sub.from(DetalleAsignacionActivo.class);
        sub.select(cb.literal(1)).where(
                cb.equal(d.get("asignacionActivo"), root),
                detalleVigente(d, cb),
                condicion.apply(d, cb));
        return cb.exists(sub);
    }

    /** Las líneas trasladadas son historia del acta: no cuentan como contenido actual. */
    private Predicate detalleVigente(Root<DetalleAsignacionActivo> d, CriteriaBuilder cb) {
        return cb.or(cb.isNull(d.get("estadoDetalle")),
                     cb.equal(d.get("estadoDetalle"), DetalleAsignacionActivo.VIGENTE));
    }

    /**
     * Ordena la consulta de datos.
     * <p>
     * El orden se arma acá y no con el {@code Sort} del {@code Pageable} por dos motivos:
     * "cantidad de bienes" y "costo" no son columnas del acta sino agregados de sus
     * detalles, y ordenar por responsable necesita un LEFT JOIN explícito —con el join
     * implícito, las actas sin responsable desaparecerían al ordenar por esa columna.
     * <p>
     * Se omite en la consulta de conteo: Spring Data reutiliza esta misma Specification
     * para el {@code count} de la paginación y un ORDER BY ahí genera SQL inválido.
     */
    private void aplicarOrden(String orden, boolean descendente, Root<AsignacionActivo> root,
                              CriteriaQuery<?> query, CriteriaBuilder cb) {
        if (query == null) return;
        Class<?> tipoResultado = query.getResultType();
        if (tipoResultado == Long.class || tipoResultado == long.class) return;

        Expression<?> expresion = switch (orden == null ? "" : orden) {
            case "numero"      -> root.get("numeroAsignacion");
            case "documento"   -> ordenPorNumeroDeDocumento(root, cb);
            case "responsable" -> root.join("responsable", JoinType.LEFT)
                                      .join("persona", JoinType.LEFT).get("paterno");
            case "oficina"     -> root.join("oficinaDestino", JoinType.LEFT).get("nombre");
            case "estado"      -> root.get("estadoAsignacion");
            case "activos"     -> conteoDeDetalles(root, query, cb);
            case "costo"       -> sumaDeCostos(root, query, cb);
            default            -> root.get("fechaAsignacion");
        };

        query.orderBy(descendente ? cb.desc(expresion) : cb.asc(expresion));
    }

    /**
     * Clave de orden del número de documento, rellenada con ceros a la izquierda.
     * <p>
     * El documento es un número pero se guarda como texto, y ordenar texto pone
     * {@code 2087} antes que {@code 272} y a este antes que {@code 65}. Rellenar a un
     * ancho fijo hace que el orden alfabético coincida con el numérico. Se hace así y no
     * con un {@code CAST} a entero porque la columna también admite {@code S/N}: el cast
     * reventaría al toparse con esas filas.
     */
    private Expression<String> ordenPorNumeroDeDocumento(Root<AsignacionActivo> root, CriteriaBuilder cb) {
        return cb.function("lpad", String.class,
                cb.coalesce(root.get("codigoDocumento"), cb.literal("")),
                cb.literal(12), cb.literal("0"));
    }

    private Subquery<Long> conteoDeDetalles(Root<AsignacionActivo> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
        Subquery<Long> sub = query.subquery(Long.class);
        Root<DetalleAsignacionActivo> d = sub.from(DetalleAsignacionActivo.class);
        sub.select(cb.count(d)).where(cb.equal(d.get("asignacionActivo"), root), detalleVigente(d, cb));
        return sub;
    }

    private Subquery<Double> sumaDeCostos(Root<AsignacionActivo> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
        Subquery<Double> sub = query.subquery(Double.class);
        Root<DetalleAsignacionActivo> d = sub.from(DetalleAsignacionActivo.class);
        Join<Object, Object> activo = d.join("activo", JoinType.LEFT);
        // COALESCE para que un acta sin costos cargados ordene como 0 y no como nulo,
        // que en PostgreSQL se va al final o al principio según la dirección.
        sub.select(cb.coalesce(cb.sum(activo.<Double>get("costo")), 0.0))
           .where(cb.equal(d.get("asignacionActivo"), root), detalleVigente(d, cb));
        return sub;
    }
}
