package com.usic.SistemasActivosFijosUAP.model.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.usic.SistemasActivosFijosUAP.interoperabilidad.registroDbf.OficinaDbfWriterService;
import com.usic.SistemasActivosFijosUAP.interoperabilidad.registroDbf.RespDbfWriterService;
import com.usic.SistemasActivosFijosUAP.model.IService.IOficinaService;
import com.usic.SistemasActivosFijosUAP.model.IService.IResponsableService;
import com.usic.SistemasActivosFijosUAP.model.dao.IDbfColaOrdenDao;
import com.usic.SistemasActivosFijosUAP.model.dao.IUsuarioDao;
import com.usic.SistemasActivosFijosUAP.model.entity.DbfColaOrden;
import com.usic.SistemasActivosFijosUAP.model.entity.Oficina;
import com.usic.SistemasActivosFijosUAP.model.entity.Predio;
import com.usic.SistemasActivosFijosUAP.model.entity.Responsable;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Envío al VSIAF de las oficinas y responsables que se registran o editan en el SCIAF,
 * y lectura del estado real de ese envío.
 * <p>
 * Antes cada endpoint armaba por su cuenta la entidad/unidad, llamaba al writer y
 * decidía qué responder; si el encolado fallaba (montaje caído) el registro quedaba
 * marcado como "ya en el VSIAF" igual. Acá hay una sola regla:
 * <ul>
 *   <li>Si la orden se encoló (o se escribió, en modo bytes) → {@code pendienteDbf=false}.</li>
 *   <li>Si no se pudo → {@code pendienteDbf=true}, y la tabla del módulo ofrece reenviarlo.</li>
 * </ul>
 * Qué pasó después con la orden lo dice {@code dbf_cola_orden} (el worker la aplicó, la
 * rechazó o todavía no llegó a ella): {@link #estados} lo traduce para las tablas.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VsiafApoyoService {

    public static final String TABLA_OFICINA = "OFICINA";
    public static final String TABLA_RESP = "RESP";

    /** Ya está en el VSIAF (confirmado por el worker, o vino leído del DBF). */
    public static final String EST_VSIAF = "VSIAF";
    /** La orden está en la cola esperando al worker. */
    public static final String EST_EN_COLA = "EN_COLA";
    /** El worker rechazó la última orden. */
    public static final String EST_ERROR = "ERROR";
    /** Nunca se pudo encolar (o se registró en modo rápido): hay que enviarlo. */
    public static final String EST_PENDIENTE = "PENDIENTE";

    /** Pasado este tiempo en la cola sin respuesta, el worker probablemente está detenido. */
    private static final Duration ESPERA_NORMAL = Duration.ofMinutes(5);

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final OficinaDbfWriterService oficinaDbfWriterService;
    private final RespDbfWriterService respDbfWriterService;
    private final IOficinaService oficinaService;
    private final IResponsableService responsableService;
    private final IDbfColaOrdenDao colaDao;
    private final IUsuarioDao usuarioDao;

    @Value("${legacy.dbf.write.mode:bytes}")
    private String writeMode;

    /** Resultado de un envío: si salió, y el texto para mostrarle al usuario. */
    public record Envio(boolean ok, String mensaje) {}

    /** Estado de sincronización de un registro, listo para pintar en la tabla. */
    public record EstadoVsiaf(String codigo, String texto, String detalle) {}

    /** Clave con la que la oficina está hoy en OFICINA.DBF (antes de editarla). */
    public record ClaveOficina(String entidad, String unidad, Short codOfi) {}

    /** Clave con la que el responsable está hoy en RESP.DBF (antes de editarlo). */
    public record ClaveResponsable(String entidad, String unidad, Short codOfi, Integer codResp) {}

    public boolean modoCola() {
        return "cola".equalsIgnoreCase(writeMode);
    }

    // ── Claves ──────────────────────────────────────────────────────────────

    public ClaveOficina claveDe(Oficina o) {
        Predio p = o.getPredio();
        return new ClaveOficina(entidadDe(p), unidadDe(p), o.getCodOfi());
    }

    public ClaveResponsable claveDe(Responsable r) {
        Oficina o = r.getOficina();
        Predio p = (o != null) ? o.getPredio() : null;
        return new ClaveResponsable(entidadDe(p), unidadDe(p),
                o != null ? o.getCodOfi() : null, codResp(r.getCodigoFuncionario()));
    }

    /** CODRESP numérico a partir del código de funcionario del SCIAF; null si no tiene dígitos. */
    public static Integer codResp(String codigoFuncionario) {
        if (codigoFuncionario == null) return null;
        String digitos = codigoFuncionario.replaceAll("\\D+", "");
        if (digitos.isEmpty()) return null;
        try {
            return Integer.valueOf(digitos);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ── Envío de oficinas ───────────────────────────────────────────────────

    /** Alta en OFICINA.DBF. */
    public Envio insertarOficina(Oficina o, String usuario) {
        return enviarOficina(o, () -> {
            ClaveOficina c = exigirClave(o);
            oficinaDbfWriterService.insertarDesdeOficina(o, c.entidad(), c.unidad(), usuario);
        });
    }

    /** Edición: ubica la fila por la clave que tenía antes del cambio. */
    public Envio actualizarOficina(Oficina o, ClaveOficina original, String usuario) {
        return enviarOficina(o, () -> {
            ClaveOficina c = exigirClave(o);
            oficinaDbfWriterService.actualizarDesdeOficina(original.codOfi(), original.entidad(), original.unidad(),
                    o, c.entidad(), c.unidad(), usuario);
        });
    }

    /**
     * Reenvío de una oficina que quedó pendiente o rechazada: alta si no existe y
     * actualización con los datos actuales. Las dos órdenes son idempotentes (el INSERT
     * es insert-if-not-exists), así sirve tanto si falló el alta como si falló una edición.
     */
    public Envio reenviarOficina(Oficina o, String usuario) {
        return enviarOficina(o, () -> {
            ClaveOficina c = exigirClave(o);
            oficinaDbfWriterService.insertarDesdeOficina(o, c.entidad(), c.unidad(), usuario);
            oficinaDbfWriterService.actualizarDesdeOficina(c.codOfi(), c.entidad(), c.unidad(),
                    o, c.entidad(), c.unidad(), usuario);
        });
    }

    private Envio enviarOficina(Oficina o, Runnable envio) {
        try {
            envio.run();
            o.setPendienteDbf(false);
            oficinaService.save(o);
            return new Envio(true, textoEnviado());
        } catch (Exception e) {
            log.error("[VSIAF] Oficina {} no se pudo enviar: {}", o.getIdOficina(), e.getMessage(), e);
            o.setPendienteDbf(true);
            oficinaService.save(o);
            return new Envio(false, "Se guardó en el SCIAF, pero NO se pudo enviar al VSIAF: "
                    + e.getMessage() + ". Quedó marcada como pendiente; se puede reenviar desde la tabla.");
        }
    }

    // ── Envío de responsables ───────────────────────────────────────────────

    /** Alta en RESP.DBF. */
    public Envio insertarResponsable(Responsable r, String usuario) {
        return enviarResponsable(r, () -> {
            ClaveResponsable c = exigirClave(r);
            respDbfWriterService.insertarDesdeResponsable(r, c.entidad(), c.unidad(), usuario);
        });
    }

    /** Edición: ubica la fila por la clave que tenía antes del cambio. */
    public Envio actualizarResponsable(Responsable r, ClaveResponsable original, String usuario) {
        if (original.codResp() == null) {
            // Nunca tuvo un CODRESP válido: no puede estar en el VSIAF con esa clave.
            return insertarResponsable(r, usuario);
        }
        return enviarResponsable(r, () -> {
            ClaveResponsable c = exigirClave(r);
            respDbfWriterService.actualizarDesdeResponsable(original.codResp(), original.codOfi(),
                    original.entidad(), original.unidad(), r, c.entidad(), c.unidad(), usuario);
        });
    }

    /** Reenvío de un responsable pendiente o rechazado (alta si no existe + actualización). */
    public Envio reenviarResponsable(Responsable r, String usuario) {
        return enviarResponsable(r, () -> {
            ClaveResponsable c = exigirClave(r);
            respDbfWriterService.insertarDesdeResponsable(r, c.entidad(), c.unidad(), usuario);
            respDbfWriterService.actualizarDesdeResponsable(c.codResp(), c.codOfi(), c.entidad(), c.unidad(),
                    r, c.entidad(), c.unidad(), usuario);
        });
    }

    private Envio enviarResponsable(Responsable r, Runnable envio) {
        try {
            envio.run();
            r.setPendienteDbf(false);
            responsableService.save(r);
            return new Envio(true, textoEnviado());
        } catch (Exception e) {
            log.error("[VSIAF] Responsable {} no se pudo enviar: {}", r.getIdResponsable(), e.getMessage(), e);
            r.setPendienteDbf(true);
            responsableService.save(r);
            return new Envio(false, "Se guardó en el SCIAF, pero NO se pudo enviar al VSIAF: "
                    + e.getMessage() + ". Quedó marcado como pendiente; se puede reenviar desde la tabla.");
        }
    }

    private String textoEnviado() {
        return modoCola()
                ? "Enviado al VSIAF: el worker lo aplica en unos segundos."
                : "Registrado en el VSIAF.";
    }

    private ClaveOficina exigirClave(Oficina o) {
        ClaveOficina c = claveDe(o);
        if (c.entidad() == null || c.unidad() == null || c.codOfi() == null) {
            throw new IllegalStateException("la oficina no tiene entidad, unidad o código completos");
        }
        return c;
    }

    private ClaveResponsable exigirClave(Responsable r) {
        ClaveResponsable c = claveDe(r);
        if (c.entidad() == null || c.unidad() == null || c.codOfi() == null) {
            throw new IllegalStateException("la oficina del responsable no tiene entidad, unidad o código completos");
        }
        if (c.codResp() == null) {
            throw new IllegalStateException("el código de funcionario no es numérico");
        }
        return c;
    }

    // ── Estado para las tablas ──────────────────────────────────────────────

    /**
     * Estado de sincronización de cada registro.
     * <p>
     * {@code pendienteDbf} manda: se pone en false exactamente cuando una orden salió, así
     * que si está en true el último cambio no viajó (aunque haya órdenes viejas OK). Si no,
     * decide la orden más reciente del registro. Sin órdenes (vino leído del DBF, o se
     * envió antes de que existiera este seguimiento) se asume que está en el VSIAF.
     *
     * @param tabla     {@link #TABLA_OFICINA} o {@link #TABLA_RESP}
     * @param pendiente id del registro → su {@code pendienteDbf}
     */
    public Map<Long, EstadoVsiaf> estados(String tabla, Map<Long, Boolean> pendiente) {
        Map<Long, EstadoVsiaf> out = new HashMap<>();
        if (pendiente == null || pendiente.isEmpty()) return out;

        Map<Long, DbfColaOrden> ultima = new HashMap<>();
        try {
            List<DbfColaOrden> ordenes = colaDao.findByTablaAndIdRegistroInAndEstadoNotOrderByIdOrdenDesc(
                    tabla, pendiente.keySet(), DbfColaOrden.REINTENTADA);
            for (DbfColaOrden o : ordenes) {
                ultima.putIfAbsent(o.getIdRegistro(), o);   // vienen de la más nueva a la más vieja
            }
        } catch (Exception e) {
            log.warn("[VSIAF] No se pudo leer la cola para {}: {}", tabla, e.getMessage());
        }

        for (Map.Entry<Long, Boolean> e : pendiente.entrySet()) {
            out.put(e.getKey(), estadoDe(Boolean.TRUE.equals(e.getValue()), ultima.get(e.getKey())));
        }
        return out;
    }

    /**
     * ¿La fila todavía no existe en el VSIAF? Pasa si nunca se envió (pendiente) o si el
     * worker rechazó el último envío. En esos casos una edición no puede ser un UPDATE
     * —no encontraría nada que actualizar y el worker lo daría por bueno igual—: hay que
     * reenviar el alta completa.
     */
    public boolean necesitaAlta(String tabla, Long idRegistro, boolean pendienteDbf) {
        if (pendienteDbf) return true;
        EstadoVsiaf e = estados(tabla, Map.of(idRegistro, false)).get(idRegistro);
        return e != null && EST_ERROR.equals(e.codigo());
    }

    private EstadoVsiaf estadoDe(boolean pendienteDbf, DbfColaOrden orden) {
        if (pendienteDbf) {
            return new EstadoVsiaf(EST_PENDIENTE, "Pendiente",
                    "Está en el SCIAF pero todavía no se envió al VSIAF.");
        }
        if (orden == null || DbfColaOrden.OK.equals(orden.getEstado())) {
            String cuando = (orden != null && orden.getFechaResuelto() != null)
                    ? " (confirmado " + orden.getFechaResuelto().format(FMT) + ")" : "";
            return new EstadoVsiaf(EST_VSIAF, "En VSIAF", "Registrado en el VSIAF" + cuando + ".");
        }
        if (DbfColaOrden.ERROR.equals(orden.getEstado())) {
            String motivo = (orden.getMensaje() != null && !orden.getMensaje().isBlank())
                    ? orden.getMensaje() : "el worker rechazó la orden";
            return new EstadoVsiaf(EST_ERROR, "Error VSIAF", "El VSIAF rechazó el último cambio: " + motivo);
        }
        // ENCOLADA
        LocalDateTime desde = orden.getFechaEncolado();
        boolean demorada = desde != null && desde.isBefore(LocalDateTime.now().minus(ESPERA_NORMAL));
        String detalle = "Enviado " + (desde != null ? desde.format(FMT) : "") + "; esperando al worker del VSIAF."
                + (demorada ? " Lleva más de 5 minutos: avise al administrador (el worker puede estar detenido)." : "");
        return new EstadoVsiaf(EST_EN_COLA, demorada ? "En cola (demorado)" : "En cola", detalle);
    }

    // ── Auditoría ───────────────────────────────────────────────────────────

    /** id de usuario → nombre de usuario, para mostrar quién registró / modificó. */
    public Map<Long, String> nombresUsuario(Collection<Long> ids) {
        // HashMap (no Map.of): quien llama hace get(null) para registros sin usuario de auditoría.
        Map<Long, String> out = new HashMap<>();
        List<Long> limpios = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (limpios.isEmpty()) return out;
        try {
            for (Usuario u : usuarioDao.findAllById(limpios)) {
                if (u.getIdUsuario() == null) continue;
                out.put(u.getIdUsuario(), u.getUsuario() != null ? u.getUsuario() : ("#" + u.getIdUsuario()));
            }
        } catch (Exception e) {
            log.warn("[VSIAF] No se pudieron resolver los usuarios de auditoría: {}", e.getMessage());
        }
        return out;
    }

    private static String entidadDe(Predio p) {
        return (p != null && p.getEntidad() != null) ? p.getEntidad().getEntidadCodigo() : null;
    }

    /** UNIDAD del VSIAF = {@code predio.unidad} (texto), nunca {@code predio.codigo}. */
    private static String unidadDe(Predio p) {
        return (p != null) ? p.getUnidad() : null;
    }
}
