package com.usic.SistemasActivosFijosUAP.model.service.seguridad;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.usic.SistemasActivosFijosUAP.config.RolesSciaf;
import com.usic.SistemasActivosFijosUAP.model.dao.IHistorialPermisoUsuarioDao;
import com.usic.SistemasActivosFijosUAP.model.dao.IOpcionMenuDao;
import com.usic.SistemasActivosFijosUAP.model.entity.HistorialPermisoUsuario;
import com.usic.SistemasActivosFijosUAP.model.entity.OpcionMenu;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Deja constancia de cada cambio en los permisos de menú de un usuario (ver
 * {@link HistorialPermisoUsuario}) y guarda/lee el respaldo que se restaura al activarlo.
 *
 * <p>Quién y desde qué IP se toman de la petición en curso: así quien llama solo dice
 * qué cambió y desde dónde. Sin petición (arranque del sistema) el autor es "SISTEMA".
 *
 * <p>Se escribe en la misma transacción que el cambio: si la auditoría no se puede
 * guardar, el cambio tampoco se aplica (un permiso cambiado sin rastro es justo lo que
 * esto quiere evitar).
 */
@Service
public class AuditoriaPermisosService {

    private static final ZoneId LA_PAZ = ZoneId.of("America/La_Paz");

    private final IHistorialPermisoUsuarioDao dao;
    private final IOpcionMenuDao opcionMenuDao;

    public AuditoriaPermisosService(IHistorialPermisoUsuarioDao dao, IOpcionMenuDao opcionMenuDao) {
        this.dao = dao;
        this.opcionMenuDao = opcionMenuDao;
    }

    /**
     * @param antes   permisos propios antes del cambio
     * @param despues permisos propios después
     * @param respaldo si no es null, se guarda como respaldo restaurable (ver la entidad)
     */
    public HistorialPermisoUsuario registrar(Usuario afectado, String accion, String origen,
            Collection<String> antes, Collection<String> despues, Collection<String> respaldo, String detalle) {
        Set<String> a = antes == null ? Set.of() : new TreeSet<>(antes);
        Set<String> d = despues == null ? Set.of() : new TreeSet<>(despues);

        HistorialPermisoUsuario h = new HistorialPermisoUsuario();
        h.setIdUsuario(afectado.getIdUsuario());
        h.setUsuario(recortar(afectado.getUsuario(), 60));
        h.setAccion(accion);
        h.setOrigen(recortar(origen, 80));
        h.setAgregados(unir(d.stream().filter(c -> !a.contains(c)).toList()));
        h.setQuitados(unir(a.stream().filter(c -> !d.contains(c)).toList()));
        h.setRespaldo(respaldo == null ? null : String.join(",", new TreeSet<>(respaldo)));
        h.setCantidadAntes(a.size());
        h.setCantidadDespues(d.size());
        h.setDetalle(detalle);
        h.setFecha(LocalDateTime.now(LA_PAZ));

        HttpServletRequest req = peticion();
        Usuario actor = req != null ? RolesSciaf.usuarioDe(req) : null;
        if (actor != null) {
            h.setIdActor(actor.getIdUsuario());
            h.setActor(recortar(actor.getUsuario(), 60));
            h.setRolActor(recortar(RolesSciaf.rolDe(actor), 40));
        } else {
            h.setActor("SISTEMA");
        }
        h.setIp(req != null ? recortar(ipDe(req), 45) : null);
        return dao.save(h);
    }

    /**
     * Último respaldo guardado del usuario: vacío = no hay respaldo; un conjunto vacío =
     * usaba la plantilla de su rol.
     */
    public Optional<Set<String>> ultimoRespaldo(Long idUsuario) {
        return dao.ultimosRespaldos(List.of(idUsuario)).stream().findFirst().map(h -> separar(h.getRespaldo()));
    }

    /**
     * Para la tabla de usuarios: id → cantidad de permisos en su último respaldo.
     * Transacción propia: si la tabla todavía no existe, el error no arrastra a la
     * transacción del listado (que en Postgres quedaría abortada y la pantalla no abriría).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Map<Long, Integer> cantidadesRespaldo(Collection<Long> ids) {
        Map<Long, Integer> m = new HashMap<>();
        if (ids == null || ids.isEmpty()) return m;
        for (HistorialPermisoUsuario h : dao.ultimosRespaldos(ids)) {
            m.put(h.getIdUsuario(), separar(h.getRespaldo()).size());
        }
        return m;
    }

    /** Historial del usuario, con los nombres de las opciones ya resueltos. */
    public List<Map<String, Object>> historial(Long idUsuario, int limite) {
        List<HistorialPermisoUsuario> lista = dao.findByIdUsuarioOrderByFechaDescIdHistorialDesc(
                idUsuario, PageRequest.of(0, Math.max(1, limite)));

        Set<String> codigos = new LinkedHashSet<>();
        for (HistorialPermisoUsuario h : lista) {
            codigos.addAll(separar(h.getAgregados()));
            codigos.addAll(separar(h.getQuitados()));
            if (h.getRespaldo() != null) codigos.addAll(separar(h.getRespaldo()));
        }
        Map<String, String> nombres = nombres(codigos);

        List<Map<String, Object>> r = new ArrayList<>();
        for (HistorialPermisoUsuario h : lista) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("fecha", h.getFecha() != null ? h.getFecha().toString() : null);
            m.put("accion", h.getAccion());
            m.put("origen", h.getOrigen());
            m.put("actor", h.getActor());
            m.put("rolActor", h.getRolActor());
            m.put("ip", h.getIp());
            m.put("agregados", etiquetas(h.getAgregados(), nombres));
            m.put("quitados", etiquetas(h.getQuitados(), nombres));
            m.put("respaldo", h.getRespaldo() == null ? null : etiquetas(h.getRespaldo(), nombres));
            m.put("cantidadAntes", h.getCantidadAntes());
            m.put("cantidadDespues", h.getCantidadDespues());
            m.put("detalle", h.getDetalle());
            r.add(m);
        }
        return r;
    }

    /** Código → "Grupo › Opción" (incluye opciones ya eliminadas del menú). */
    private Map<String, String> nombres(Collection<String> codigos) {
        if (codigos.isEmpty()) return Map.of();
        return opcionMenuDao.findByCodigoIn(codigos).stream().collect(Collectors.toMap(
                OpcionMenu::getCodigo,
                o -> (o.getGrupo() != null ? o.getGrupo() + " › " : "") + o.getDescripcion()
                        + (o.eliminado() ? " (eliminada del menú)" : ""),
                (x, y) -> x));
    }

    private List<Map<String, String>> etiquetas(String csv, Map<String, String> nombres) {
        List<Map<String, String>> l = new ArrayList<>();
        for (String c : separar(csv)) {
            l.add(Map.of("codigo", c, "nombre", nombres.getOrDefault(c, c)));
        }
        return l;
    }

    public static Set<String> separar(String csv) {
        if (csv == null || csv.isBlank()) return new LinkedHashSet<>();
        return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String unir(List<String> codigos) {
        return codigos.isEmpty() ? null : String.join(",", codigos);
    }

    private HttpServletRequest peticion() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes s ? s.getRequest() : null;
    }

    private String ipDe(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        return request.getRemoteAddr();
    }

    private String recortar(String s, int max) {
        return (s == null || s.length() <= max) ? s : s.substring(0, max);
    }
}
