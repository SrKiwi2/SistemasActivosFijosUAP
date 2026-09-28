package com.usic.SistemasActivosFijosUAP.model.service.supervision;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.usic.SistemasActivosFijosUAP.componet.SseEmitterRegistry;
import com.usic.SistemasActivosFijosUAP.config.RolesSciaf;
import com.usic.SistemasActivosFijosUAP.model.dao.IActividadSistemaDao;
import com.usic.SistemasActivosFijosUAP.model.entity.ActividadSistema;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Bitácora de actividad de los usuarios, para el módulo de Monitoreo.
 * <p>
 * Cada registro se avisa en vivo (SSE, evento {@code actividad}) a los ADMINISTRADOR y
 * SUPER USUARIO conectados —menos al propio autor—, que lo ven como aviso emergente y,
 * si tienen abierto el Monitoreo, como una fila nueva.
 * <p>
 * Nunca propaga: perder una línea de bitácora es malo, pero tumbar el registro de una
 * oficina porque no se pudo anotar sería peor.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ActividadService {

    public static final String MOD_OFICINA = "OFICINA";
    public static final String MOD_RESPONSABLE = "RESPONSABLE";
    public static final String MOD_ACTIVO = "ACTIVO";
    public static final String MOD_ASIGNACION = "ASIGNACION";
    public static final String MOD_TRANSFERENCIA = "TRANSFERENCIA";
    public static final String MOD_BLOQUEO = "BLOQUEO";
    public static final String MOD_AUTORIZACION = "AUTORIZACION";

    public static final String ACC_REGISTRO = "REGISTRO";
    public static final String ACC_MODIFICACION = "MODIFICACION";
    public static final String ACC_ELIMINACION = "ELIMINACION";
    public static final String ACC_APROBACION = "APROBACION";
    public static final String ACC_SOLICITUD = "SOLICITUD";
    public static final String ACC_RECHAZO = "RECHAZO";
    public static final String ACC_MOVIMIENTO = "MOVIMIENTO";
    public static final String ACC_BLOQUEO = "BLOQUEO";
    public static final String ACC_DESBLOQUEO = "DESBLOQUEO";
    public static final String ACC_REENVIO = "REENVIO";

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private final IActividadSistemaDao dao;
    private final SseEmitterRegistry sseRegistry;

    /** Acción sobre un solo registro. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrar(Usuario u, String modulo, String accion, String referencia,
                          String descripcion, Long idRegistro) {
        registrar(u, modulo, accion, referencia, descripcion, 1, idRegistro);
    }

    /** Acción sobre varios registros (p. ej. un lote de activos). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrar(Usuario u, String modulo, String accion, String referencia,
                          String descripcion, Integer cantidad, Long idRegistro) {
        try {
            ActividadSistema a = new ActividadSistema();
            a.setFecha(LocalDateTime.now());
            if (u != null) {
                a.setIdUsuario(u.getIdUsuario());
                a.setUsuario(recortar(u.getUsuario(), 60));
                a.setRol(recortar(RolesSciaf.rolDe(u), 40));
            } else {
                a.setUsuario("SISTEMA");
            }
            a.setModulo(modulo);
            a.setAccion(accion);
            a.setReferencia(recortar(referencia, 200));
            a.setDescripcion(descripcion);
            a.setCantidad(cantidad);
            a.setIdRegistro(idRegistro);
            dao.save(a);
            avisar(a);
        } catch (Exception e) {
            log.warn("[ACTIVIDAD] No se pudo registrar {} {} ({}): {}", accion, modulo, referencia, e.getMessage());
        }
    }

    /** Lo que viaja por SSE y lo que devuelve la tabla del monitoreo. */
    public static Map<String, Object> aMapa(ActividadSistema a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("idActividad", a.getIdActividad());
        m.put("fecha", a.getFecha() != null ? a.getFecha().format(FMT) : "");
        m.put("usuario", a.getUsuario());
        m.put("rol", a.getRol());
        m.put("modulo", a.getModulo());
        m.put("accion", a.getAccion());
        m.put("referencia", a.getReferencia());
        m.put("descripcion", a.getDescripcion());
        m.put("cantidad", a.getCantidad());
        return m;
    }

    private void avisar(ActividadSistema a) {
        try {
            Map<String, Object> payload = aMapa(a);
            for (String rol : RolesSciaf.ADMINISTRATIVOS) {
                Set<Long> conectados = sseRegistry.getUsuariosConectadosEnRol(rol);
                for (Long id : conectados) {
                    if (id != null && id.equals(a.getIdUsuario())) continue;   // no avisarle al autor
                    sseRegistry.enviarAUsuario(id, "actividad", payload);
                }
            }
        } catch (Exception e) {
            log.debug("[ACTIVIDAD] No se pudo avisar por SSE: {}", e.getMessage());
        }
    }

    private static String recortar(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
