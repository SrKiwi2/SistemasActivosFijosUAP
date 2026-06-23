package com.usic.SistemasActivosFijosUAP.controller.consulta;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.usic.SistemasActivosFijosUAP.anotacion.ValidarUsuarioAutenticado;
import com.usic.SistemasActivosFijosUAP.model.IService.IGrupoContableService;
import com.usic.SistemasActivosFijosUAP.model.IService.IMunicipioService;
import com.usic.SistemasActivosFijosUAP.model.IService.IPredioServicio;
import com.usic.SistemasActivosFijosUAP.model.dao.IActivoDao;
import com.usic.SistemasActivosFijosUAP.model.dto.CorrelativoActivoDTO;
import com.usic.SistemasActivosFijosUAP.model.entity.GrupoContable;
import com.usic.SistemasActivosFijosUAP.model.entity.Municipio;
import com.usic.SistemasActivosFijosUAP.model.entity.Predio;
import com.usic.SistemasActivosFijosUAP.model.repository.FuncionesActivoRepo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Revisión de correlativos por municipio + predio + grupo contable.
 *
 * Pantalla de SOLO LECTURA pensada para auditar/comparar contra el VSIAF:
 * dada una combinación, lista los activos ya codificados ordenados por su
 * correlativo y muestra cuál sería el SIGUIENTE código a asignar
 * ({@code municipio-predio-grupo(2)-correlativo(5)}), además de huecos en la
 * serie e incoherencias de prefijo.
 *
 * No modifica nada ni toca la secuencia: el "siguiente código" según la BD se
 * obtiene con {@link FuncionesActivoRepo#previewCodigoPorCodes} (preview, no
 * incrementa).
 *
 * La vista se carga como fragmento del SPA (#contenido); los datos se piden de
 * forma asíncrona a {@code /administracion/correlativo/datos}.
 */
@Slf4j
@Controller
@RequestMapping("/administracion/correlativo")
@RequiredArgsConstructor
public class CorrelativoController {

    private final IActivoDao            activoDao;
    private final FuncionesActivoRepo   funciones;
    private final IMunicipioService     municipioService;
    private final IPredioServicio       predioServicio;
    private final IGrupoContableService grupoContableService;

    /** Estructura general del código: mun-pred-grupo(num)-correlativo(num). */
    private static final Pattern CODIGO_VALIDO = Pattern.compile("^[^-]+-[^-]+-[0-9]+-[0-9]+$");

    @ValidarUsuarioAutenticado
    @GetMapping("/vista")
    public String vista() {
        return "correlativo/revision";
    }

    @ValidarUsuarioAutenticado
    @GetMapping(value = "/datos", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    @Transactional(readOnly = true)
    public ResponseEntity<?> datos(
            @RequestParam Long predioId,
            @RequestParam Long grupoId) {
        try {
            Predio predio = predioServicio.findById(predioId);
            GrupoContable grupo = grupoContableService.findById(grupoId);

            if (predio == null || grupo == null) {
                return ResponseEntity.badRequest().body(Map.of(
                        "ok", false, "message", "Predio o grupo contable inexistente."));
            }

            Municipio municipio = predio.getMunicipio();
            String munCod  = municipio != null ? municipio.getCodigo() : null;
            String predCod = predio.getCodigo();
            String grpCod  = grupo.getCodDbf() != null
                    ? String.format("%02d", grupo.getCodDbf())
                    : null;

            if (munCod == null || predCod == null || grpCod == null) {
                return ResponseEntity.ok(Map.of(
                        "ok", false,
                        "message", "La combinación no tiene los códigos completos "
                                 + "(municipio/predio/grupo). Revise el catálogo antes de auditar."));
            }

            String prefijo = String.join("-", munCod, predCod, grpCod);

            // Activos ya registrados para este predio + grupo, ordenados correlativamente.
            List<CorrelativoActivoDTO> registros =
                    activoDao.listarParaRevisionCorrelativo(predioId, grupoId);

            List<Map<String, Object>> items = new ArrayList<>();
            long maxCorrelativo = 0;          // máximo sobre códigos con prefijo correcto
            int  conPrefijoOk   = 0;
            int  conPrefijoMal  = 0;
            List<Long> correlativosOk = new ArrayList<>();

            for (CorrelativoActivoDTO r : registros) {
                Long correlativo = extraerCorrelativo(r.getCodigo());
                boolean formatoOk = correlativo != null;
                boolean prefijoOk = formatoOk && r.getCodigo().startsWith(prefijo + "-");

                if (prefijoOk) {
                    conPrefijoOk++;
                    correlativosOk.add(correlativo);
                    if (correlativo > maxCorrelativo) maxCorrelativo = correlativo;
                } else {
                    conPrefijoMal++;
                }

                Map<String, Object> item = new LinkedHashMap<>();
                item.put("idActivo",    r.getIdActivo());
                item.put("codigo",      r.getCodigo());
                item.put("correlativo", correlativo);          // puede ser null si formato inválido
                item.put("descripcion", r.getDescripcion());
                item.put("oficina",     r.getOficinaNombre());
                item.put("codOfi",      r.getCodOfi());
                item.put("estado",      r.getEstado());
                item.put("prefijoOk",   prefijoOk);
                item.put("formatoOk",   formatoOk);
                items.add(item);
            }

            // Huecos en la serie (1..maxCorrelativo) entre los códigos con prefijo correcto.
            List<Long> huecos = new ArrayList<>();
            if (maxCorrelativo > 0) {
                java.util.Set<Long> usados = new java.util.HashSet<>(correlativosOk);
                for (long n = 1; n <= maxCorrelativo; n++) {
                    if (!usados.contains(n)) huecos.add(n);
                }
            }

            // "Siguiente" según los datos reales (max + 1) y según la función de BD.
            long siguienteReal = maxCorrelativo + 1;
            String siguienteCodigoReal = String.format("%s-%05d", prefijo, siguienteReal);

            String siguienteCodigoFuncion;
            try {
                siguienteCodigoFuncion = funciones.previewCodigoPorCodes(munCod, predCod, grpCod);
            } catch (Exception e) {
                log.warn("preview_codigo_activo_by_codes falló para {}: {}", prefijo, e.getMessage());
                siguienteCodigoFuncion = null;
            }

            boolean coincideFuncion = siguienteCodigoFuncion != null
                    && siguienteCodigoFuncion.equals(siguienteCodigoReal);

            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("ok", true);
            resp.put("municipio", Map.of(
                    "nombre", municipio.getNombre() != null ? municipio.getNombre() : "",
                    "codigo", munCod));
            resp.put("predio", Map.of(
                    "descripcion", predio.getDescrip() != null ? predio.getDescrip() : "",
                    "unidad",      predio.getUnidad()  != null ? predio.getUnidad()  : "",
                    "codigo",      predCod));
            resp.put("grupo", Map.of(
                    "nombre", grupo.getNombre() != null ? grupo.getNombre() : "",
                    "codigo", grpCod));
            resp.put("prefijo",                prefijo);
            resp.put("totalRegistros",         registros.size());
            resp.put("conPrefijoCorrecto",     conPrefijoOk);
            resp.put("conPrefijoIncorrecto",   conPrefijoMal);
            resp.put("correlativoMax",         maxCorrelativo);
            resp.put("siguienteCorrelativo",   siguienteReal);
            resp.put("siguienteCodigoReal",    siguienteCodigoReal);
            resp.put("siguienteCodigoFuncion", siguienteCodigoFuncion);
            resp.put("coincideFuncion",        coincideFuncion);
            resp.put("huecos",                 huecos);
            resp.put("items",                  items);

            return ResponseEntity.ok(resp);

        } catch (Exception e) {
            log.error("Error en revisión de correlativos (predio={}, grupo={}): {}",
                    predioId, grupoId, e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "ok", false,
                    "message", "No se pudo calcular la revisión: " + e.getMessage()));
        }
    }

    /**
     * Extrae el correlativo (último tramo numérico) de un código bien formado
     * {@code mun-pred-grupo-correlativo}. Devuelve {@code null} si el código no
     * respeta la estructura esperada.
     */
    private Long extraerCorrelativo(String codigo) {
        if (codigo == null) return null;
        Matcher m = CODIGO_VALIDO.matcher(codigo);
        if (!m.matches()) return null;
        try {
            int ultimoGuion = codigo.lastIndexOf('-');
            return Long.parseLong(codigo.substring(ultimoGuion + 1));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
