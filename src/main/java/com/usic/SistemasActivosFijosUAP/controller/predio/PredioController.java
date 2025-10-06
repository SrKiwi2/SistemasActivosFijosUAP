package com.usic.SistemasActivosFijosUAP.controller.predio;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.usic.SistemasActivosFijosUAP.anotacion.ValidarUsuarioAutenticado;
import com.usic.SistemasActivosFijosUAP.config.Encriptar;
import com.usic.SistemasActivosFijosUAP.interoperabilidad.JavaDbfService;
import com.usic.SistemasActivosFijosUAP.model.IService.IEntidadService;
import com.usic.SistemasActivosFijosUAP.model.IService.IPredioServicio;
import com.usic.SistemasActivosFijosUAP.model.entity.Entidad;
import com.usic.SistemasActivosFijosUAP.model.entity.Predio;

import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/administracion/predio")
@RequiredArgsConstructor
public class PredioController {
    private final IPredioServicio predioServicio;
    private final IEntidadService entidadService;
    private final JavaDbfService dbfService;

    @ValidarUsuarioAutenticado
    @GetMapping("/vista")
    public String inicio_predio() {
        return "predio/vista";
    }

    // LISTA: intenta BD; si vacío, usa DBF montado (sólo lectura)
    @ValidarUsuarioAutenticado
    @PostMapping("/tabla-registros")
    public String tablaRegistros(Model model,
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "gestion", required = false) Short gestionPreferida) throws Exception {

        // 1) BD
        List<Predio> listasPredios = predioServicio.buscarPorQ(q);
        boolean fromDb = listasPredios != null && !listasPredios.isEmpty();

        if (!fromDb) {
            // 2) Fallback DBF: mapea a VM Predio (id nulo -> solo lectura)
            var filas = dbfService.listarUnidadAdminAll(q);
            listasPredios = new ArrayList<>(filas.size());
            for (var f : filas) {
                // Resolver Entidad por gestión recibida o gestión más reciente
                Entidad ent = resolverEntidad(entidadService, gestionPreferida, f.getEntidadCodigo());

                if (ent == null) {
                    // Si no está la Entidad aún en BD, puedes dejarla nula y mostrar “-”, o saltar
                    // Aquí la asignamos nula y la vista debería tolerarlo
                }

                Predio p = new Predio();
                p.setIdPredio(null);
                p.setEntidad(ent);
                p.setUnidad(f.getUnidad());
                p.setDescrip(f.getDescrip());
                p.setCiudad(f.getCiudad());
                p.setEstadoUni(f.getEstadoUni());
                p.setCodigo(f.getEntidadCodigo());
                // Si tu AuditoriaConfig tiene 'estado', descomenta:
                p.setEstado("ACTIVO");

                listasPredios.add(p);
            }
        }

        List<String> encryptedIds = new ArrayList<>();
        for (Predio p : listasPredios) {
            encryptedIds.add(p.getIdPredio() == null ? "" : Encriptar.encrypt(Long.toString(p.getIdPredio())));
        }

        model.addAttribute("listasPredios", listasPredios);
        model.addAttribute("id_encryptado", encryptedIds);
        model.addAttribute("sourceUsed", fromDb ? "db" : "dbf");
        return "predio/tabla_registro";
    }

    // SYNC: importar unidadadmin.DBF → BD (upsert por Entidad + Unidad)
    @ValidarUsuarioAutenticado
    @PostMapping("/sync-from-mounted")
    @ResponseBody
    public ResponseEntity<?> syncFromMounted(
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "gestion", required = false) Short gestionPreferida) {
        try {
            var filas = dbfService.listarUnidadAdminAll(q);
            int inserted = 0, updated = 0, skipped=0;
            List<Predio> batch = new ArrayList<>(500);

            for (var f : filas) {
                if (f.getEntidadCodigo() == null || f.getEntidadCodigo().isBlank()
                        || f.getUnidad() == null || f.getUnidad().isBlank()) {
                    skipped++;
                    continue;
                }

                // Entidad: por gestión preferida o la más reciente
                var entidad = resolverEntidad(entidadService, gestionPreferida, f.getEntidadCodigo());
                if (entidad == null) { skipped++; continue; }

                Predio p = predioServicio.findByEntidadAndUnidad(entidad, f.getUnidad())
                        .orElseGet(() -> {
                            Predio np = new Predio();
                            np.setEntidad(entidad);
                            np.setUnidad(f.getUnidad());
                            return np;
                        });

                boolean nuevo = (p.getIdPredio() == null);

                p.setDescrip(f.getDescrip());
                p.setCiudad(f.getCiudad());
                p.setEstadoUni(f.getEstadoUni());
                p.setEstado("ACTIVO");

                batch.add(p);
                if (nuevo) inserted++; else updated++;

                if (batch.size() == 500) { predioServicio.saveAll(batch); batch.clear(); }
            }
            if (!batch.isEmpty()) predioServicio.saveAll(batch);

            return ResponseEntity.ok(Map.of(
                    "ok", true,
                    "totalLeidas", filas.size(),
                    "insertados", inserted,
                    "actualizados", updated,
                    "sinEntidadEnBD", skipped));
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "ok", false,
                    "message", "Error sincronizando UNIDADADMIN: " + ex.getMessage()));
        }
    }

    private String stripLeftZeros(String s) {
        if (s == null)
            return null;
        String out = s.replaceFirst("^0+", "");
        return out.isEmpty() ? "0" : out;
    }

    private String leftPad4(String s) {
        String base = stripLeftZeros(s);
        try {
            int n = Integer.parseInt(base);
            return String.format("%04d", n);
        } catch (NumberFormatException e) {
            return s; // si no es numérico, deja como está
        }
    }

    /**
     * Intenta resolver por gestión preferida; si no, por la más reciente. Prueba 2
     * variantes: como viene y normalizada.
     */
    private Entidad resolverEntidad(IEntidadService entidadService, Short gestionPreferida, String codigo) {
        String cod = codigo;
        String codNoZeros = stripLeftZeros(codigo);
        String codPad4 = leftPad4(codigo); // por si en BD está siempre 4 dígitos

        // orden de prueba: tal cual -> sin ceros -> padded 4
        if (gestionPreferida != null) {
            return entidadService.findByGestionAndEntidadCodigo(gestionPreferida, cod)
                    .or(() -> entidadService.findByGestionAndEntidadCodigo(gestionPreferida, codNoZeros))
                    .or(() -> entidadService.findByGestionAndEntidadCodigo(gestionPreferida, codPad4))
                    .orElse(null);
        } else {
            return entidadService.findTopByEntidadCodigoOrderByGestionDesc(cod)
                    .or(() -> entidadService.findTopByEntidadCodigoOrderByGestionDesc(codNoZeros))
                    .or(() -> entidadService.findTopByEntidadCodigoOrderByGestionDesc(codPad4))
                    .orElse(null);
        }
    }

}