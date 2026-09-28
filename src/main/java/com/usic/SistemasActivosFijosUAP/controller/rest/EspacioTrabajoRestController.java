package com.usic.SistemasActivosFijosUAP.controller.rest;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.usic.SistemasActivosFijosUAP.config.RolesSciaf;
import com.usic.SistemasActivosFijosUAP.model.dao.IEspacioUsuarioDao;
import com.usic.SistemasActivosFijosUAP.model.entity.EspacioUsuario;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Respaldo del trabajo a medias de cada usuario: pestañas abiertas y borrador de cada
 * pantalla. Lo consume {@code sciaf-pestanas.js} ({@code window.sciafEspacio} y
 * {@code window.sciafBorrador}).
 * <p>
 * Cada usuario solo ve y escribe lo suyo: la clave es {@code (usuario en sesión, clave)},
 * nunca un id que venga del navegador.
 * <p>
 * La clave viaja como parámetro y no dentro de la ruta porque contiene barras (p. ej.
 * {@code tab./adm/inicio}): una barra codificada en la ruta la rechaza el servidor y todas
 * las lecturas y guardados respondían 404.
 */
@Slf4j
@RestController
@RequestMapping("/api/espacio")
@RequiredArgsConstructor
public class EspacioTrabajoRestController {

    /** Tope por borrador: es estado de pantalla, no un archivo. */
    private static final int MAX_CARACTERES = 300_000;

    private final IEspacioUsuarioDao dao;

    @GetMapping
    public ResponseEntity<?> leer(HttpServletRequest request, @RequestParam String clave) {
        Usuario u = RolesSciaf.usuarioDe(request);
        if (u == null) return ResponseEntity.status(401).body(Map.of("ok", false));
        // Nunca 500: esto es una comodidad (recordar la pantalla a medias). Si algo falla
        // —la tabla todavía no existe tras un despliegue, por ejemplo— se responde "sin
        // datos" y el sistema sigue funcionando igual.
        try {
            return dao.findByIdUsuarioAndClave(u.getIdUsuario(), clave)
                    .map(e -> {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("ok", true);
                        m.put("clave", clave);
                        m.put("datos", e.getDatosJson());
                        m.put("fecha", e.getFechaActualizacion() != null ? e.getFechaActualizacion().toString() : null);
                        return ResponseEntity.ok(m);
                    })
                    .orElseGet(() -> ResponseEntity.ok(Map.of("ok", true, "clave", clave, "datos", (Object) null)));
        } catch (Exception e) {
            log.warn("[ESPACIO] No se pudo leer '{}' de {}: {}", clave, u.getUsuario(), e.getMessage());
            return ResponseEntity.ok(Map.of("ok", false, "clave", clave, "datos", (Object) null));
        }
    }

    @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Transactional
    public ResponseEntity<?> guardar(HttpServletRequest request, @RequestParam String clave,
                                     @RequestBody(required = false) String datosJson) {
        Usuario u = RolesSciaf.usuarioDe(request);
        if (u == null) return ResponseEntity.status(401).body(Map.of("ok", false));
        if (datosJson != null && datosJson.length() > MAX_CARACTERES) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "msg", "El borrador es demasiado grande."));
        }
        try {
            EspacioUsuario e = dao.findByIdUsuarioAndClave(u.getIdUsuario(), clave)
                    .orElseGet(() -> {
                        EspacioUsuario nuevo = new EspacioUsuario();
                        nuevo.setIdUsuario(u.getIdUsuario());
                        nuevo.setClave(clave);
                        return nuevo;
                    });
            e.setDatosJson(datosJson);
            e.setFechaActualizacion(LocalDateTime.now());
            dao.save(e);
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (Exception e) {
            log.warn("[ESPACIO] No se pudo guardar '{}' de {}: {}", clave, u.getUsuario(), e.getMessage());
            return ResponseEntity.ok(Map.of("ok", false));
        }
    }

    @DeleteMapping
    @Transactional
    public ResponseEntity<?> borrar(HttpServletRequest request, @RequestParam String clave) {
        Usuario u = RolesSciaf.usuarioDe(request);
        if (u == null) return ResponseEntity.status(401).body(Map.of("ok", false));
        try {
            dao.deleteByIdUsuarioAndClave(u.getIdUsuario(), clave);
        } catch (Exception e) {
            log.warn("[ESPACIO] No se pudo borrar '{}': {}", clave, e.getMessage());
        }
        return ResponseEntity.ok(Map.of("ok", true));
    }

    /** Borradores sin tocar en 30 días: se limpian de madrugada. */
    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void limpiarViejos() {
        try {
            int n = dao.borrarAnterioresA(LocalDateTime.now().minusDays(30));
            if (n > 0) log.info("[ESPACIO] {} borradores de más de 30 días eliminados", n);
        } catch (Exception e) {
            log.warn("[ESPACIO] No se pudieron limpiar los borradores viejos: {}", e.getMessage());
        }
    }
}
