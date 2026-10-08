package com.usic.SistemasActivosFijosUAP.model.service.seguimiento;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.usic.SistemasActivosFijosUAP.model.dao.IUsuarioDao;
import com.usic.SistemasActivosFijosUAP.model.entity.Activo;
import com.usic.SistemasActivosFijosUAP.model.entity.AsignacionActivo;
import com.usic.SistemasActivosFijosUAP.model.entity.AsignacionMovimiento;
import com.usic.SistemasActivosFijosUAP.model.entity.AsignacionMovimientoDetalle;
import com.usic.SistemasActivosFijosUAP.model.entity.DetalleAsignacionActivo;
import com.usic.SistemasActivosFijosUAP.model.entity.HallazgoInventario;
import com.usic.SistemasActivosFijosUAP.model.entity.HistorialActivo;
import com.usic.SistemasActivosFijosUAP.model.entity.HistorialBloqueoActivo;
import com.usic.SistemasActivosFijosUAP.model.entity.Inventario;
import com.usic.SistemasActivosFijosUAP.model.entity.InventarioDetalle;
import com.usic.SistemasActivosFijosUAP.model.entity.Oficina;
import com.usic.SistemasActivosFijosUAP.model.entity.Responsable;
import com.usic.SistemasActivosFijosUAP.model.entity.Transferencia;
import com.usic.SistemasActivosFijosUAP.model.entity.TransferenciaDetalle;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;
import com.usic.SistemasActivosFijosUAP.model.service.control.ReglaNegocioException;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Seguimiento de un activo: por qué oficinas y manos pasó, cuándo y por qué documento.
 *
 * <p>No hay una tabla única con la "ruta" de un bien: cada módulo dejó su rastro en su
 * propia tabla. Este servicio junta todo, por id de activo (no por código: el código se
 * pudo corregir, ver CAMBIO_CODIGO):
 * <ul>
 *   <li>{@code transferencia_detalle} — transferencias internas/externas (y las del ciclo de faltantes);</li>
 *   <li>{@code detalle_asignacion} — actas de asignación;</li>
 *   <li>{@code asignacion_movimiento_detalle} — traslados y separaciones de actas;</li>
 *   <li>{@code historial_activo} — el resto de los eventos que registra el SCIAF;</li>
 *   <li>{@code hallazgo_inventario} — faltantes y su resolución;</li>
 *   <li>{@code inventario_detalle} — en qué levantamiento se lo vio (o no) y dónde;</li>
 *   <li>{@code historial_bloqueo_activo} — bloqueos y desbloqueos.</li>
 * </ul>
 * Con eso arma (1) la línea de tiempo completa y (2) las estancias: cada oficina en la
 * que estuvo, desde cuándo, hasta cuándo y con qué responsables.
 *
 * <p>Límite honesto: lo que se movió directamente en el VSIAF, antes del SCIAF o por
 * fuera de él, no dejó rastro acá. Si la oficina actual no coincide con la última
 * registrada, se agrega una estancia marcada como "cambio hecho en el VSIAF".
 */
@Service
public class RutaActivoService {

    private static final ZoneId LA_PAZ = ZoneId.of("America/La_Paz");

    @PersistenceContext
    private EntityManager em;

    private final IUsuarioDao usuarioDao;

    public RutaActivoService(IUsuarioDao usuarioDao) {
        this.usuarioDao = usuarioDao;
    }

    // ── Sugerencias para el buscador ───────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Map<String, Object>> sugerencias(String texto) {
        String q = texto == null ? "" : texto.trim().toUpperCase();
        if (q.length() < 2) return List.of();
        List<Activo> lista = em.createQuery(
                "select a from Activo a where upper(a.codigo) like :q order by a.codigo", Activo.class)
                .setParameter("q", q + "%")
                .setMaxResults(12)
                .getResultList();
        List<Map<String, Object>> r = new ArrayList<>();
        for (Activo a : lista) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("codigo", a.getCodigo());
            m.put("descripcion", a.getDescripcion());
            r.add(m);
        }
        return r;
    }

    // ── Ruta completa ──────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Map<String, Object> ruta(String codigoBuscado, boolean verFinanzas) {
        String codigo = codigoBuscado == null ? "" : codigoBuscado.trim().toUpperCase();
        if (codigo.isEmpty()) throw new ReglaNegocioException("Escriba el código del activo.");

        Activo a = buscarActivo(codigo);
        if (a == null) {
            throw new ReglaNegocioException("No se encontró ningún activo con el código «" + codigo + "».");
        }
        Long id = a.getIdActivo();

        List<Evento> eventos = new ArrayList<>();
        eventosAlta(a, eventos, verFinanzas);
        eventosTransferencias(id, eventos);
        eventosActas(id, eventos);
        eventosMovimientosActa(id, eventos);
        eventosHistorial(id, eventos);
        eventosFaltantes(id, eventos);
        eventosLevantamientos(id, eventos);
        eventosBloqueos(id, eventos);

        eventos.sort(Comparator.comparing((Evento e) -> e.fecha, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(e -> e.orden));

        List<Map<String, Object>> estancias = estancias(a, eventos);

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("ok", true);
        r.put("codigoBuscado", codigo);
        r.put("codigoCambiado", !codigo.equalsIgnoreCase(a.getCodigo()));
        r.put("activo", datosActivo(a, verFinanzas));
        List<Map<String, Object>> evs = new ArrayList<>();
        for (int i = eventos.size() - 1; i >= 0; i--) evs.add(eventos.get(i).aMapa()); // más reciente primero
        r.put("eventos", evs);
        r.put("estancias", estancias);
        r.put("resumen", resumen(eventos, estancias));
        return r;
    }

    /** Por código actual; si no, por un código que tuvo antes (snapshots de los movimientos). */
    private Activo buscarActivo(String codigo) {
        List<Activo> exacto = em.createQuery("select a from Activo a where upper(a.codigo) = :c", Activo.class)
                .setParameter("c", codigo).setMaxResults(1).getResultList();
        if (!exacto.isEmpty()) return exacto.get(0);

        for (String jpql : List.of(
                "select h.activo.idActivo from HistorialActivo h where upper(h.codigoActivo) = :c",
                "select d.activo.idActivo from TransferenciaDetalle d where upper(d.codigoActivo) = :c",
                "select d.activo.idActivo from DetalleAsignacionActivo d where upper(d.codigoActivoSnapshot) = :c")) {
            List<Long> ids = em.createQuery(jpql, Long.class).setParameter("c", codigo).setMaxResults(1).getResultList();
            if (!ids.isEmpty() && ids.get(0) != null) {
                Activo a = em.find(Activo.class, ids.get(0));
                if (a != null) return a;
            }
        }
        return null;
    }

    // ── Fuentes ────────────────────────────────────────────────────────────

    private void eventosAlta(Activo a, List<Evento> eventos, boolean verFinanzas) {
        if (a.getFechaAdquisicion() != null) {
            Evento e = new Evento("ADQUISICION", "Adquisición del bien", inicioDelDia(a.getFechaAdquisicion()), true);
            e.icono = "ti ti-shopping-cart";
            e.color = "secondary";
            e.detalle = verFinanzas && a.getCosto() != null ? "Costo: Bs " + String.format("%,.2f", a.getCosto()) : null;
            e.orden = -2;
            eventos.add(e);
        }
        if (a.getRegistro() != null) {
            Evento e = new Evento("REGISTRO", "Registrado en el sistema", aLocal(a.getRegistro()), false);
            e.icono = "ti ti-database-plus";
            e.color = "secondary";
            e.detalle = "Primera vez que el SCIAF lo tuvo (alta o primera sincronización con el VSIAF).";
            e.orden = -1;
            eventos.add(e);
        }
    }

    private void eventosTransferencias(Long id, List<Evento> eventos) {
        List<TransferenciaDetalle> lista = em.createQuery(
                "select d from TransferenciaDetalle d join fetch d.transferencia t where d.activo.idActivo = :id",
                TransferenciaDetalle.class).setParameter("id", id).getResultList();
        Map<Long, String> usuarios = nombresUsuarios(lista.stream()
                .map(d -> d.getTransferencia().getRegistroIdUsuario()).filter(Objects::nonNull).toList());
        // Respaldo de "quién la hizo": las transferencias viejas no tienen el usuario en la
        // auditoría, pero historial_activo sí lo guardó junto a la transferencia.
        Map<Long, String> autoresHistorial = new HashMap<>();
        for (Object[] f : em.createQuery(
                "select h.transferencia.idTransferencia, h.nombreUsuario from HistorialActivo h "
                        + "where h.activo.idActivo = :id and h.transferencia is not null and h.nombreUsuario is not null",
                Object[].class).setParameter("id", id).getResultList()) {
            autoresHistorial.putIfAbsent((Long) f[0], (String) f[1]);
        }

        for (TransferenciaDetalle d : lista) {
            Transferencia t = d.getTransferencia();
            String motivo = t.getMotivoFaltante();
            boolean externa = "EXTERNA".equalsIgnoreCase(t.getTipo());
            String titulo = Transferencia.MOTIVO_FALTANTE.equals(motivo) ? "Enviado a la oficina de faltantes"
                    : Transferencia.MOTIVO_DEVOLUCION_FALTANTE.equals(motivo) ? "Faltante devuelto (apareció)"
                    : externa ? "Transferencia externa (otro predio)" : "Transferencia interna";
            LocalDateTime fecha = combinar(t.getFechaTransferencia(), t.getRegistro());
            Evento e = new Evento(Transferencia.MOTIVO_FALTANTE.equals(motivo) ? "FALTANTE_ENVIO"
                    : externa ? "TRANSFERENCIA_EXT" : "TRANSFERENCIA_INT", titulo, fecha, false);
            e.icono = motivo != null ? "ti ti-alert-triangle" : "ti ti-arrows-exchange";
            e.color = motivo != null ? "danger" : externa ? "warning" : "info";
            e.ofOrigen = oficina(d.getOficinaAnterior() != null ? d.getOficinaAnterior() : t.getOficinaOrigen());
            e.respOrigen = responsable(d.getResponsableAnterior() != null ? d.getResponsableAnterior() : t.getResponsableOrigen());
            e.ofDestino = oficina(d.getOficinaDestino() != null ? d.getOficinaDestino() : t.getOficinaDestino());
            e.respDestino = responsable(d.getResponsableDestino() != null ? d.getResponsableDestino() : t.getResponsableDestino());
            e.documento = t.getNumeroTransferencia();
            e.documentoRef = t.getDocumentoReferencia();
            e.enlace = "/seguimiento-activo/transferencia/" + t.getIdTransferencia() + "/pdf";
            e.estado = t.getEstadoProceso();
            e.usuario = primero(usuarios.get(t.getRegistroIdUsuario()), autoresHistorial.get(t.getIdTransferencia()));
            e.detalle = primero(d.getObservacionDetalle(), t.getObservacion());
            if (externa && t.getInstitucionDestino() != null) {
                e.detalle = "Institución destino: " + t.getInstitucionDestino() + (e.detalle != null ? ". " + e.detalle : "");
            }
            e.motivoFaltante = motivo;
            e.mueve = !"ANULADA".equalsIgnoreCase(t.getEstadoProceso());
            e.idTransferencia = t.getIdTransferencia();
            eventos.add(e);
        }
    }

    private void eventosActas(Long id, List<Evento> eventos) {
        List<DetalleAsignacionActivo> lista = em.createQuery(
                "select d from DetalleAsignacionActivo d join fetch d.asignacionActivo a where d.activo.idActivo = :id",
                DetalleAsignacionActivo.class).setParameter("id", id).getResultList();
        Map<Long, String> usuarios = nombresUsuarios(lista.stream()
                .map(d -> d.getAsignacionActivo().getRegistroIdUsuario()).filter(Objects::nonNull).toList());

        for (DetalleAsignacionActivo d : lista) {
            AsignacionActivo acta = d.getAsignacionActivo();
            LocalDateTime fecha = acta.getFechaAsignacion() != null ? acta.getFechaAsignacion() : aLocal(acta.getRegistro());
            Evento e = new Evento("ASIGNACION", "Acta de asignación"
                    + (acta.getTipoAsignacion() != null && !"NUEVA".equalsIgnoreCase(acta.getTipoAsignacion())
                        ? " (" + acta.getTipoAsignacion().toLowerCase() + ")" : ""), fecha, false);
            e.icono = "ti ti-clipboard-check";
            e.color = "primary";
            Oficina ofDestino = acta.getOficinaDestino() != null ? acta.getOficinaDestino()
                    : (acta.getResponsable() != null ? acta.getResponsable().getOficina() : null);
            e.ofDestino = oficina(ofDestino);
            e.respDestino = responsable(acta.getResponsable());
            if (acta.getResponsableOrigen() != null) {
                e.respOrigen = responsable(acta.getResponsableOrigen());
                e.ofOrigen = oficina(acta.getResponsableOrigen().getOficina());
            }
            e.documento = primero(acta.getCodigoCompleto(), acta.getNumeroAsignacion());
            e.documentoRef = acta.getDocumentoReferencia();
            e.estado = primero(d.getEstadoDetalle(), acta.getEstadoAsignacion());
            e.usuario = usuarios.get(acta.getRegistroIdUsuario());
            e.detalle = primero(d.getObservacionDetalle(), acta.getObservacion());
            e.mueve = !"ANULADA".equalsIgnoreCase(acta.getEstadoAsignacion());
            eventos.add(e);
        }
    }

    private void eventosMovimientosActa(Long id, List<Evento> eventos) {
        List<AsignacionMovimientoDetalle> lista = em.createQuery(
                "select d from AsignacionMovimientoDetalle d join fetch d.movimiento m where d.activo.idActivo = :id",
                AsignacionMovimientoDetalle.class).setParameter("id", id).getResultList();
        for (AsignacionMovimientoDetalle d : lista) {
            AsignacionMovimiento m = d.getMovimiento();
            String tipo = m.getTipo() != null ? m.getTipo() : "MOVIMIENTO";
            Evento e = new Evento("MOVIMIENTO_ACTA", "Acta: " + tipo.toLowerCase().replace('_', ' '), m.getFecha(), false);
            e.icono = "ti ti-git-branch";
            e.color = "primary";
            e.ofOrigen = textoUbicacion(d.getOficinaAntes());
            e.ofDestino = textoUbicacion(d.getOficinaDespues());
            e.respOrigen = textoUbicacion(d.getResponsableAntes());
            e.respDestino = textoUbicacion(d.getResponsableDespues());
            e.usuario = m.getNombreUsuario();
            e.detalle = m.getMotivo();
            e.documento = m.getAsignacionDestino() != null
                    ? primero(m.getAsignacionDestino().getCodigoCompleto(), m.getAsignacionDestino().getNumeroAsignacion())
                    : null;
            e.estado = m.getResultadoVsiaf();
            eventos.add(e);
        }
    }

    /**
     * historial_activo: lo que no esté ya contado por las fuentes de arriba. Las
     * transferencias tienen id_transferencia (se saltean), y una ASIGNACION a menos de 5
     * minutos de un acta o movimiento con el mismo destino es el mismo hecho registrado dos
     * veces.
     */
    private void eventosHistorial(Long id, List<Evento> eventos) {
        List<HistorialActivo> lista = em.createQuery(
                "select h from HistorialActivo h where h.activo.idActivo = :id", HistorialActivo.class)
                .setParameter("id", id).getResultList();
        List<Evento> yaContados = new ArrayList<>(eventos);
        for (HistorialActivo h : lista) {
            if (h.getTransferencia() != null) continue;
            String tipo = h.getTipoEvento() != null ? h.getTipoEvento() : "EVENTO";
            Map<String, Object> destino = h.getOficinaNueva() != null ? oficina(h.getOficinaNueva())
                    : textoUbicacion(h.getNombreOficinaNueva());
            if ("ASIGNACION".equals(tipo) && duplicado(yaContados, h.getFechaEvento(), destino)) continue;

            Evento e = new Evento(tipo, tituloHistorial(tipo), h.getFechaEvento(), false);
            e.icono = switch (tipo) {
                case "CAMBIO_CODIGO" -> "ti ti-barcode";
                case "EDICION", "MODIFICACION" -> "ti ti-pencil";
                case "BAJA" -> "ti ti-trash";
                case "REACTIVACION" -> "ti ti-refresh";
                default -> "ti ti-point";
            };
            e.color = "BAJA".equals(tipo) ? "danger" : "CAMBIO_CODIGO".equals(tipo) ? "warning" : "secondary";
            e.ofOrigen = h.getOficinaAnterior() != null ? oficina(h.getOficinaAnterior()) : textoUbicacion(h.getNombreOficinaAnterior());
            e.ofDestino = destino;
            e.respOrigen = h.getResponsableAnterior() != null ? responsable(h.getResponsableAnterior()) : textoUbicacion(h.getNombreRespAnterior());
            e.respDestino = h.getResponsableNuevo() != null ? responsable(h.getResponsableNuevo()) : textoUbicacion(h.getNombreRespNuevo());
            e.usuario = h.getNombreUsuario();
            e.detalle = h.getDescripcionEvento();
            e.documento = "CAMBIO_CODIGO".equals(tipo) ? h.getCodigoActivo() : null;
            // Una edición que no cambia de oficina no es un movimiento.
            e.mueve = !"CAMBIO_CODIGO".equals(tipo);
            eventos.add(e);
        }
    }

    private void eventosFaltantes(Long id, List<Evento> eventos) {
        List<HallazgoInventario> lista = em.createQuery(
                "select h from HallazgoInventario h where h.activo.idActivo = :id", HallazgoInventario.class)
                .setParameter("id", id).getResultList();
        for (HallazgoInventario h : lista) {
            if (!"FALTANTE".equalsIgnoreCase(h.getTipoHallazgo())) continue;
            Inventario inv = h.getInventario();
            LocalDateTime fecha = aLocal(h.getRegistro());
            if (fecha == null && inv != null) fecha = inv.getFechaInicio();
            Evento e = new Evento("FALTANTE", "Registrado como faltante", fecha, false);
            e.icono = "ti ti-alert-octagon";
            e.color = "danger";
            Oficina origen = h.getOficinaOrigen() != null ? h.getOficinaOrigen() : (inv != null ? inv.getOficina() : null);
            e.ofOrigen = oficina(origen);
            e.respOrigen = responsable(h.getResponsable());
            e.documento = h.getActa() != null ? h.getActa().getNumero() : (inv != null ? inv.getNumeroInventario() : null);
            e.documentoRef = h.getDocumentoRespaldo();
            e.estado = h.getEstadoHallazgo();
            e.detalle = "ANULADO".equalsIgnoreCase(h.getEstadoHallazgo()) ? "Faltante anulado."
                    : (inv != null ? "Detectado en el levantamiento " + inv.getNumeroInventario() : "Registro directo")
                    + (h.getDescripcionDiscrepancia() != null ? ". " + h.getDescripcionDiscrepancia() : "");
            e.motivoFaltante = Transferencia.MOTIVO_FALTANTE;
            eventos.add(e);

            if (h.getFechaResolucion() != null) {
                Evento r = new Evento("FALTANTE_RESUELTO", "Faltante resuelto", h.getFechaResolucion(), false);
                r.icono = "ti ti-circle-check";
                r.color = "success";
                r.estado = h.getTipoResolucion();
                r.usuario = h.getUsuarioRevisor();
                r.detalle = primero(h.getAccionCorrectiva(), h.getObserv());
                r.documento = e.documento;
                eventos.add(r);
            }
        }
    }

    private void eventosLevantamientos(Long id, List<Evento> eventos) {
        List<InventarioDetalle> lista = em.createQuery(
                "select d from InventarioDetalle d join fetch d.inventario i where d.activo.idActivo = :id",
                InventarioDetalle.class).setParameter("id", id).getResultList();
        for (InventarioDetalle d : lista) {
            Inventario inv = d.getInventario();
            String sit = d.getSituacion() != null ? d.getSituacion() : InventarioDetalle.SITUACION_PENDIENTE;
            boolean encontrado = InventarioDetalle.SITUACION_ENCONTRADO.equals(sit);
            LocalDateTime fecha = d.getFechaMarca() != null ? d.getFechaMarca()
                    : (inv.getFechaFin() != null ? inv.getFechaFin() : inv.getFechaInicio());
            Evento e = new Evento("LEVANTAMIENTO",
                    encontrado ? "Verificado en levantamiento" : "Levantamiento: " + sit.toLowerCase(), fecha, false);
            e.icono = encontrado ? "ti ti-scan" : "ti ti-zoom-question";
            e.color = encontrado ? "success" : InventarioDetalle.SITUACION_FALTANTE.equals(sit) ? "danger" : "secondary";
            e.ofDestino = oficina(inv.getOficina());
            e.respDestino = responsable(d.getResponsable());
            e.documento = inv.getNumeroInventario();
            e.estado = sit;
            e.detalle = primero(d.getObservacion(),
                    d.getEstadoObservado() != null ? "Estado observado: " + d.getEstadoObservado().getNombre() : null);
            e.usuario = d.getOrigenMarca();
            // Un levantamiento no lo mueve: confirma dónde estaba.
            e.mueve = false;
            e.verifica = encontrado;
            eventos.add(e);
        }
    }

    private void eventosBloqueos(Long id, List<Evento> eventos) {
        List<HistorialBloqueoActivo> lista = em.createQuery(
                "select b from HistorialBloqueoActivo b where b.activo.idActivo = :id", HistorialBloqueoActivo.class)
                .setParameter("id", id).getResultList();
        for (HistorialBloqueoActivo b : lista) {
            boolean bloqueo = b.getAccion() != null && b.getAccion().toUpperCase().startsWith("BLOQ");
            Evento e = new Evento(bloqueo ? "BLOQUEO" : "DESBLOQUEO", bloqueo ? "Bloqueado" : "Desbloqueado", b.getFecha(), false);
            e.icono = bloqueo ? "ti ti-lock" : "ti ti-lock-open";
            e.color = bloqueo ? "dark" : "secondary";
            e.respOrigen = responsable(b.getResponsable());
            e.usuario = b.getUsuario();
            e.detalle = b.getObservacion();
            e.mueve = false;
            eventos.add(e);
        }
    }

    // ── Estancias ──────────────────────────────────────────────────────────

    /**
     * Recorre los eventos en orden y abre una estancia cada vez que cambia la oficina.
     * La primera oficina conocida es el origen del primer movimiento; si nunca se movió,
     * es la oficina actual.
     */
    private List<Map<String, Object>> estancias(Activo a, List<Evento> eventos) {
        List<Estancia> lista = new ArrayList<>();
        LocalDateTime inicio = a.getFechaAdquisicion() != null ? inicioDelDia(a.getFechaAdquisicion()) : aLocal(a.getRegistro());

        Estancia actual = null;
        for (Evento e : eventos) {
            if (!e.mueve || e.ofDestino == null) {
                if (e.verifica && actual != null && mismaOficina(actual.oficina, e.ofDestino)) actual.verificaciones++;
                if (e.respDestino != null && actual != null && mismaOficina(actual.oficina, e.ofDestino)) {
                    actual.agregarResponsable(e.respDestino, e.fecha);
                }
                continue;
            }
            if (actual == null) {
                // Primera oficina conocida: de dónde salió en el primer movimiento.
                if (e.ofOrigen != null && !mismaOficina(e.ofOrigen, e.ofDestino)) {
                    Estancia previa = new Estancia(e.ofOrigen, inicio, true);
                    if (e.respOrigen != null) previa.agregarResponsable(e.respOrigen, null);
                    previa.hasta = e.fecha;
                    lista.add(previa);
                }
                actual = new Estancia(e.ofDestino, e.fecha, false);
                actual.via = e;
                if (e.respDestino != null) actual.agregarResponsable(e.respDestino, e.fecha);
                lista.add(actual);
                continue;
            }
            if (mismaOficina(actual.oficina, e.ofDestino)) {
                // Cambio de responsable dentro de la misma oficina.
                if (e.respOrigen != null) actual.agregarResponsable(e.respOrigen, null);
                if (e.respDestino != null) actual.agregarResponsable(e.respDestino, e.fecha);
                continue;
            }
            // Sale de la oficina: quien lo entregó también lo tuvo en esta estancia.
            if (e.respOrigen != null && (e.ofOrigen == null || mismaOficina(actual.oficina, e.ofOrigen))) {
                actual.agregarResponsable(e.respOrigen, null);
            }
            actual.hasta = e.fecha;
            actual = new Estancia(e.ofDestino, e.fecha, false);
            actual.via = e;
            if (e.respDestino != null) actual.agregarResponsable(e.respDestino, e.fecha);
            lista.add(actual);
        }

        Map<String, Object> ofActual = oficina(a.getOficina());
        if (actual == null) {
            if (ofActual != null) {
                Estancia unica = new Estancia(ofActual, inicio, true);
                if (a.getResponsable() != null) unica.agregarResponsable(responsable(a.getResponsable()));
                lista.add(unica);
                actual = unica;
            }
        } else if (ofActual != null && !mismaOficina(actual.oficina, ofActual)) {
            // La última oficina registrada no es la de hoy: el cambio se hizo en el VSIAF.
            LocalDateTime cuando = a.getFecMod() != null ? inicioDelDia(a.getFecMod()) : null;
            if (cuando == null || (actual.desde != null && cuando.isBefore(actual.desde))) cuando = null;
            actual.hasta = cuando;
            Estancia vsiaf = new Estancia(ofActual, cuando, true);
            vsiaf.cambioVsiaf = true;
            if (a.getResponsable() != null) vsiaf.agregarResponsable(responsable(a.getResponsable()));
            lista.add(vsiaf);
            actual = vsiaf;
        }
        if (actual != null) {
            actual.actual = true;
            if (a.getResponsable() != null) actual.agregarResponsable(responsable(a.getResponsable()));
        }

        List<Map<String, Object>> r = new ArrayList<>();
        for (Estancia e : lista) r.add(e.aMapa());
        return r;
    }

    private Map<String, Object> resumen(List<Evento> eventos, List<Map<String, Object>> estancias) {
        Set<String> oficinas = new HashSet<>();
        Set<String> predios = new HashSet<>();
        Set<String> responsables = new HashSet<>();
        for (Map<String, Object> e : estancias) {
            @SuppressWarnings("unchecked") Map<String, Object> of = (Map<String, Object>) e.get("oficina");
            if (of != null) {
                oficinas.add(String.valueOf(of.get("clave")));
                if (of.get("predio") != null) predios.add(String.valueOf(of.get("predio")));
            }
            @SuppressWarnings("unchecked") List<Map<String, Object>> rs = (List<Map<String, Object>>) e.get("responsables");
            if (rs != null) rs.forEach(x -> responsables.add(String.valueOf(x.get("clave"))));
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("oficinas", oficinas.size());
        m.put("predios", predios.size());
        m.put("responsables", responsables.size());
        m.put("transferencias", eventos.stream().filter(e -> e.tipo.startsWith("TRANSFERENCIA") || "FALTANTE_ENVIO".equals(e.tipo)).count());
        m.put("asignaciones", eventos.stream().filter(e -> "ASIGNACION".equals(e.tipo) || "MOVIMIENTO_ACTA".equals(e.tipo)).count());
        m.put("faltantes", eventos.stream().filter(e -> "FALTANTE".equals(e.tipo)).count());
        m.put("levantamientos", eventos.stream().filter(e -> "LEVANTAMIENTO".equals(e.tipo)).count());
        m.put("eventos", eventos.size());
        return m;
    }

    // ── Datos de presentación ──────────────────────────────────────────────

    private Map<String, Object> datosActivo(Activo a, boolean verFinanzas) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.getIdActivo());
        m.put("codigo", a.getCodigo());
        m.put("descripcion", a.getDescripcion());
        m.put("estado", a.getEstadoActivo() != null ? a.getEstadoActivo().getNombre() : null);
        m.put("grupoContable", a.getGrupoContable() != null ? a.getGrupoContable().getNombre() : null);
        m.put("auxiliar", a.getAuxiliar() != null ? a.getAuxiliar().getNombre() : null);
        m.put("costo", verFinanzas ? a.getCosto() : null);
        m.put("fechaAdquisicion", a.getFechaAdquisicion() != null ? a.getFechaAdquisicion().toString() : null);
        m.put("oficina", oficina(a.getOficina()));
        m.put("responsable", responsable(a.getResponsable()));
        m.put("enCustodia", a.enCustodia());
        m.put("bloqueado", a.getBloqueadoPor() != null);
        m.put("sincVsiaf", a.getSincVsiaf());
        m.put("fecModVsiaf", a.getFecMod() != null ? a.getFecMod().toString() : null);
        m.put("observ", a.getObserv());
        return m;
    }

    private Map<String, Object> oficina(Oficina o) {
        if (o == null) return null;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("clave", "id:" + o.getIdOficina());
        m.put("id", o.getIdOficina());
        m.put("codigo", o.getCodOfi());
        m.put("nombre", o.getNombre());
        m.put("predio", o.getPredio() != null ? primero(o.getPredio().getUnidad(), o.getPredio().getDescrip()) : null);
        m.put("predioNombre", o.getPredio() != null ? o.getPredio().getDescrip() : null);
        m.put("custodia", o.isEsCustodia());
        return m;
    }

    private Map<String, Object> responsable(Responsable r) {
        if (r == null) return null;
        Map<String, Object> m = new LinkedHashMap<>();
        String nombre = r.getPersona() != null ? r.getPersona().getNombreCompleto() : null;
        // La misma persona tiene una fila por oficina: para contar "responsables" se usa la persona.
        m.put("clave", r.getPersona() != null ? "p:" + r.getPersona().getIdPersona() : "r:" + r.getIdResponsable());
        m.put("codigo", r.getCodigoFuncionario());
        m.put("nombre", nombre);
        m.put("ci", r.getPersona() != null ? r.getPersona().getCi() : null);
        m.put("cargo", r.getCargo() != null ? r.getCargo().getNombre() : null);
        return m;
    }

    /** Ubicación que solo quedó como texto (movimientos de acta, historial sin FK). */
    private Map<String, Object> textoUbicacion(String texto) {
        if (texto == null || texto.isBlank()) return null;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("clave", "t:" + texto.trim().toUpperCase());
        m.put("nombre", texto.trim());
        return m;
    }

    private boolean mismaOficina(Map<String, Object> a, Map<String, Object> b) {
        if (a == null || b == null) return false;
        if (Objects.equals(a.get("clave"), b.get("clave"))) return true;
        // Una con id y otra solo con texto: se comparan por nombre.
        String na = String.valueOf(a.get("nombre")).trim().toUpperCase();
        String nb = String.valueOf(b.get("nombre")).trim().toUpperCase();
        return !na.isEmpty() && na.equals(nb)
                && (String.valueOf(a.get("clave")).startsWith("t:") || String.valueOf(b.get("clave")).startsWith("t:"));
    }

    private boolean duplicado(List<Evento> otros, LocalDateTime fecha, Map<String, Object> destino) {
        if (fecha == null) return false;
        for (Evento e : otros) {
            if (!("ASIGNACION".equals(e.tipo) || "MOVIMIENTO_ACTA".equals(e.tipo)) || e.fecha == null) continue;
            if (Math.abs(Duration.between(e.fecha, fecha).toMinutes()) <= 5
                    && (destino == null || e.ofDestino == null || mismaOficina(e.ofDestino, destino))) {
                return true;
            }
        }
        return false;
    }

    private String tituloHistorial(String tipo) {
        return switch (tipo) {
            case "ASIGNACION" -> "Asignación";
            case "CAMBIO_CODIGO" -> "Corrección del código";
            case "EDICION", "MODIFICACION" -> "Corrección de datos";
            case "REGISTRO" -> "Registro";
            case "BAJA" -> "Baja";
            case "REACTIVACION" -> "Reactivación";
            case "DESASIGNACION" -> "Desasignación";
            default -> tipo.charAt(0) + tipo.substring(1).toLowerCase().replace('_', ' ');
        };
    }

    /** id → "Nombre Apellido (usuario)": el login solo no le dice nada a quien lee el reporte. */
    private Map<Long, String> nombresUsuarios(List<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        Map<Long, String> m = new HashMap<>();
        for (Usuario u : usuarioDao.findAllByIdUsuarioIn(new HashSet<>(ids))) {
            String nombre = u.getPersona() != null ? u.getPersona().getNombreCompleto() : null;
            m.put(u.getIdUsuario(), nombre != null && !nombre.isBlank()
                    ? nombre.trim() + " (" + u.getUsuario() + ")" : u.getUsuario());
        }
        return m;
    }

    /** Fecha del documento con la hora en que se registró (para ordenar dentro del mismo día). */
    private LocalDateTime combinar(LocalDate dia, Date registro) {
        LocalDateTime reg = aLocal(registro);
        if (dia == null) return reg;
        if (reg != null && reg.toLocalDate().equals(dia)) return reg;
        return dia.atTime(12, 0);
    }

    private LocalDateTime aLocal(Date d) {
        return d == null ? null : LocalDateTime.ofInstant(d.toInstant(), LA_PAZ);
    }

    private LocalDateTime inicioDelDia(LocalDate d) {
        return d == null ? null : d.atStartOfDay();
    }

    private static String primero(String a, String b) {
        if (a != null && !a.isBlank()) return a;
        return (b != null && !b.isBlank()) ? b : null;
    }

    // ── Tipos internos ─────────────────────────────────────────────────────

    private static class Evento {
        final String tipo;
        final String titulo;
        final LocalDateTime fecha;
        final boolean soloDia;
        int orden;
        String icono, color, documento, documentoRef, enlace, estado, usuario, detalle, motivoFaltante;
        Map<String, Object> ofOrigen, ofDestino, respOrigen, respDestino;
        boolean mueve = true;
        boolean verifica;
        Long idTransferencia;

        Evento(String tipo, String titulo, LocalDateTime fecha, boolean soloDia) {
            this.tipo = tipo;
            this.titulo = titulo;
            this.fecha = fecha;
            this.soloDia = soloDia;
        }

        Map<String, Object> aMapa() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("tipo", tipo);
            m.put("titulo", titulo);
            m.put("fecha", fecha != null ? fecha.toString() : null);
            m.put("soloDia", soloDia);
            m.put("icono", icono);
            m.put("color", color);
            m.put("ofOrigen", ofOrigen);
            m.put("ofDestino", ofDestino);
            m.put("respOrigen", respOrigen);
            m.put("respDestino", respDestino);
            m.put("documento", documento);
            m.put("documentoRef", documentoRef);
            m.put("enlace", enlace);
            m.put("estado", estado);
            m.put("usuario", usuario);
            m.put("detalle", detalle);
            m.put("motivoFaltante", motivoFaltante);
            m.put("idTransferencia", idTransferencia);
            m.put("cambiaOficina", mueve && ofDestino != null && ofOrigen != null
                    && !Objects.equals(ofOrigen.get("clave"), ofDestino.get("clave")));
            return m;
        }
    }

    private static class Estancia {
        final Map<String, Object> oficina;
        final LocalDateTime desde;
        final boolean desdeAproximado;
        LocalDateTime hasta;
        boolean actual;
        boolean cambioVsiaf;
        int verificaciones;
        Evento via;
        final Map<String, Map<String, Object>> responsables = new LinkedHashMap<>();

        Estancia(Map<String, Object> oficina, LocalDateTime desde, boolean desdeAproximado) {
            this.oficina = oficina;
            this.desde = desde;
            this.desdeAproximado = desdeAproximado;
        }

        void agregarResponsable(Map<String, Object> r) {
            agregarResponsable(r, null);
        }

        /**
         * Responsable que tuvo el bien en esta oficina. {@code desde}: cuándo se le
         * entregó (si se sabe). Uno solo por persona, en el orden en que aparecieron.
         */
        void agregarResponsable(Map<String, Object> r, LocalDateTime cuando) {
            if (r == null) return;
            String clave = String.valueOf(r.get("clave"));
            Map<String, Object> ya = responsables.get(clave);
            if (ya == null) {
                Map<String, Object> copia = new LinkedHashMap<>(r);
                if (cuando != null) copia.put("desde", cuando.toString());
                responsables.put(clave, copia);
            } else if (cuando != null && ya.get("desde") == null) {
                ya.put("desde", cuando.toString());
            }
        }

        Map<String, Object> aMapa() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("oficina", oficina);
            m.put("desde", desde != null ? desde.toString() : null);
            m.put("desdeAproximado", desdeAproximado);
            m.put("hasta", actual ? null : (hasta != null ? hasta.toString() : null));
            m.put("actual", actual);
            m.put("cambioVsiaf", cambioVsiaf);
            m.put("verificaciones", verificaciones);
            LocalDateTime fin = actual ? LocalDateTime.now(LA_PAZ) : hasta;
            m.put("dias", desde != null && fin != null ? Math.max(0, ChronoUnit.DAYS.between(desde, fin)) : null);
            m.put("responsables", new ArrayList<>(responsables.values()));
            if (via != null) {
                Map<String, Object> v = new LinkedHashMap<>();
                v.put("titulo", via.titulo);
                v.put("documento", via.documento);
                v.put("enlace", via.enlace);
                v.put("motivoFaltante", via.motivoFaltante);
                v.put("usuario", via.usuario);
                v.put("fecha", via.fecha != null ? via.fecha.toString() : null);
                m.put("via", v);
            }
            return m;
        }
    }
}
