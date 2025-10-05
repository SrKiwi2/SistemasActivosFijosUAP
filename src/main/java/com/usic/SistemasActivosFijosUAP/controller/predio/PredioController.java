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
                                 @RequestParam(name="q", required=false) String q,
                                 @RequestParam(name="gestion", required=false) Short gestionPreferida) throws Exception {

        // 1) BD
        List<Predio> listasPredios = predioServicio.buscarPorQ(q);
        boolean fromDb = listasPredios != null && !listasPredios.isEmpty();

        if (!fromDb) {
            // 2) Fallback DBF: mapea a VM Predio (id nulo -> solo lectura)
            var filas = dbfService.listarUnidadAdminAll(q);
            listasPredios = new ArrayList<>(filas.size());
            for (var f : filas) {
                // Resolver Entidad por gestión recibida o gestión más reciente
                Entidad ent = (gestionPreferida != null)
                        ? entidadService.findByGestionAndEntidadCodigo(gestionPreferida, f.getEntidadCodigo()).orElse(null)
                        : entidadService.findTopByEntidadCodigoOrderByGestionDesc(f.getEntidadCodigo()).orElse(null);
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
                // Si tu AuditoriaConfig tiene 'estado', descomenta:
                p.setEstado("ACTIVO");

                listasPredios.add(p);
            }
        }

        List<String> encryptedIds = new ArrayList<>();
        for (Predio p : listasPredios) {
            encryptedIds.add(p.getIdPredio()==null ? "" : Encriptar.encrypt(Long.toString(p.getIdPredio())));
        }

        model.addAttribute("listasPredios", listasPredios);
        model.addAttribute("id_encryptado", encryptedIds);
        model.addAttribute("sourceUsed", fromDb? "db" : "dbf");
        return "predio/tabla_registro";
    }

    // SYNC: importar unidadadmin.DBF → BD (upsert por Entidad + Unidad)
    @ValidarUsuarioAutenticado
    @PostMapping("/sync-from-mounted")
    @ResponseBody
    public ResponseEntity<?> syncFromMounted(
            @RequestParam(name="q", required=false) String q,
            @RequestParam(name="gestion", required=false) Short gestionPreferida) {
        try {
            var filas = dbfService.listarUnidadAdminAll(q);
            int inserted=0, updated=0;
            List<Predio> batch = new ArrayList<>(500);

            for (var f : filas) {
                if (f.getEntidadCodigo()==null || f.getEntidadCodigo().isBlank()
                    || f.getUnidad()==null || f.getUnidad().isBlank()) {
                    continue;
                }

                // Entidad: por gestión preferida o la más reciente
                var entidad = (gestionPreferida != null)
                        ? entidadService.findByGestionAndEntidadCodigo(gestionPreferida, f.getEntidadCodigo()).orElse(null)
                        : entidadService.findTopByEntidadCodigoOrderByGestionDesc(f.getEntidadCodigo()).orElse(null);

                if (entidad == null) {
                    // No existe Entidad para ese código → omite o registra error
                    // Podrías acumular un log si quieres reportarlo al front
                    continue;
                }

                Predio p = predioServicio.findByEntidadAndUnidad(entidad, f.getUnidad())
                        .orElseGet(() -> {
                            Predio np = new Predio();
                            np.setEntidad(entidad);
                            np.setUnidad(f.getUnidad());
                            return np;
                        });

                boolean nuevo = (p.getIdPredio()==null);

                p.setDescrip(f.getDescrip());
                p.setCiudad(f.getCiudad());
                p.setEstadoUni(f.getEstadoUni());
                // Si tu AuditoriaConfig tiene 'estado':
                // p.setEstado("ACTIVO");

                batch.add(p);
                if (nuevo) inserted++; else updated++;

                if (batch.size()==500) { predioServicio.saveAll(batch); batch.clear(); }
            }
            if (!batch.isEmpty()) predioServicio.saveAll(batch);

            return ResponseEntity.ok(Map.of(
                    "ok", true,
                    "totalLeidas", filas.size(),
                    "insertados", inserted,
                    "actualizados", updated
            ));
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "ok", false,
                    "message", "Error sincronizando UNIDADADMIN: " + ex.getMessage()
            ));
        }
    }
}