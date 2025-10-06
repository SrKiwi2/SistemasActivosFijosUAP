package com.usic.SistemasActivosFijosUAP.controller.organismoFinanciador;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
import com.usic.SistemasActivosFijosUAP.model.IService.IOrganismoFinancieroService;
import com.usic.SistemasActivosFijosUAP.model.entity.OrganismoFinanciero;

import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/administracion/organismo")
@RequiredArgsConstructor
public class OrganismoFinanciadorController {

    private final IOrganismoFinancieroService organismoFinancieroService;
    private final JavaDbfService dbfService;

    @ValidarUsuarioAutenticado
    @GetMapping("/vista")
    public String inicio_of() {
        return "organismoFinanciador/vista";
    }

    // Lista: primero BD; si vacío, muestra DBF (solo lectura)
    @ValidarUsuarioAutenticado
    @PostMapping("/tabla-registros")
    public String tablaRegistros_of(Model model,
            @RequestParam(name = "q", required = false) String q) throws Exception {
        List<OrganismoFinanciero> lista = (q == null || q.isBlank())
                ? organismoFinancieroService.findAll()
                : organismoFinancieroService.buscarPorQ(q);

        boolean fromDb = lista != null && !lista.isEmpty();
        List<String> encryptedIds = new ArrayList<>();

        if (fromDb) {
            for (var of : lista) {
                encryptedIds.add(Encriptar.encrypt(String.valueOf(of.getIdOrganismoFinanciero())));
            }
            model.addAttribute("listasOrganismoFinanciero", lista);
            model.addAttribute("id_encryptado", encryptedIds);
            model.addAttribute("sourceUsed", "db");
            return "organismoFinanciador/tabla_registro";
        }

        // Fallback: DBF
        var filas = dbfService.listarOrganismoFinAll(q);
        // Mapea DTO→Entidad-like (id nulo)
        var fantasma = new ArrayList<OrganismoFinanciero>(filas.size());
        for (var f : filas) {
            var of = new OrganismoFinanciero();
            of.setIdOrganismoFinanciero(null);
            of.setGestion(f.getGestion());
            of.setCodOf(f.getCodOf());
            of.setDescripcion(f.getDescripcion());
            of.setSigla(f.getSigla());
            of.setEstado("ACTIVO"); // si aplica
            fantasma.add(of);
            encryptedIds.add(""); // sin id real
        }
        model.addAttribute("listasOrganismoFinanciero", fantasma);
        model.addAttribute("id_encryptado", encryptedIds);
        model.addAttribute("sourceUsed", "dbf");
        return "organismoFinanciador/tabla_registro";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/sync-from-mounted")
    @ResponseBody
    public ResponseEntity<?> syncFromMounted(@RequestParam(name = "q", required = false) String q) {
        try {
            var filas = dbfService.listarOrganismoFinAll(q);
            int inserted = 0, updated = 0, repetidosDbf = 0;
            List<OrganismoFinanciero> batch = new ArrayList<>(500);
            Set<String> seen = new HashSet<>(filas.size()); // por si el DBF tiene repetidos exactos

            for (var f : filas) {
                if (f.getGestion() == null || f.getCodOf() == null || f.getCodOf().isBlank()
                        || f.getDescripcion() == null || f.getDescripcion().isBlank())
                    continue;

                String key = f.getGestion() + "|" + f.getCodOf().trim().toUpperCase();
                if (!seen.add(key)) {
                    repetidosDbf++;
                    continue;
                }

                var of = organismoFinancieroService
                        .findByGestionAndCodOf(f.getGestion(), f.getCodOf().trim())
                        .orElseGet(() -> {
                            if (f.getSigla() != null && !f.getSigla().isBlank()) {
                                return organismoFinancieroService
                                        .findByGestionAndSiglaIgnoreCase(f.getGestion(), f.getSigla().trim())
                                        .orElse(null);
                            }
                            return null;
                        });

                boolean nuevo = (of == null);
                if (of == null) {
                    of = new OrganismoFinanciero();
                    of.setGestion(f.getGestion());
                    of.setCodOf(f.getCodOf().trim());
                } else {
                    // si vino por sigla y codOf difiere, actualiza codOf
                    if (f.getCodOf() != null && !f.getCodOf().isBlank())
                        of.setCodOf(f.getCodOf().trim());
                }

                of.setDescripcion(f.getDescripcion().trim());
                of.setSigla(f.getSigla() == null ? null : f.getSigla().trim());
                of.setEstado("ACTIVO");

                batch.add(of);
                if (nuevo)
                    inserted++;
                else
                    updated++;

                if (batch.size() == 500) {
                    organismoFinancieroService.saveAll(batch);
                    batch.clear();
                }
            }
            if (!batch.isEmpty())
                organismoFinancieroService.saveAll(batch);

            return ResponseEntity.ok(Map.of(
                    "ok", true,
                    "totalLeidas", filas.size(),
                    "insertados", inserted,
                    "actualizados", updated,
                    "duplicadosEnDbf", repetidosDbf));
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "ok", false,
                    "message", "Error sincronizando ORGANISMO_FIN: " + ex.getMessage()));
        }
    }
}