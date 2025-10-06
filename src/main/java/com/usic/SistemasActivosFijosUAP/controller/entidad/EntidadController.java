package com.usic.SistemasActivosFijosUAP.controller.entidad;

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
import com.usic.SistemasActivosFijosUAP.model.entity.Entidad;

import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/administracion/entidad")
@RequiredArgsConstructor
public class EntidadController {
    private final IEntidadService entidadService;
    private final JavaDbfService dbfService;

    @ValidarUsuarioAutenticado
    @GetMapping("/vista")
    public String inicioEntidad() {
        return "entidad/vista";
    }

    // LISTA: intenta BD, si vacío cae a DBF
    @ValidarUsuarioAutenticado
    @PostMapping("/tabla-registros")
    public String tablaRegistrosEntidad(
            @RequestParam(name="q", required=false) String q,
            @RequestParam(name="gestion", required=false) Short gestion,
            Model model) throws Exception {

        // 1) BD
        List<Entidad> listasEntidades = entidadService.buscarPorQ(q);
        boolean fromDb = listasEntidades != null && !listasEntidades.isEmpty();

        List<String> encryptedIds = new ArrayList<>();
        if (fromDb) {
            for (Entidad e : listasEntidades) {
                encryptedIds.add(Encriptar.encrypt(Long.toString(e.getIdEntidad())));
            }
            model.addAttribute("listasEntidades", listasEntidades);
            model.addAttribute("id_encryptado", encryptedIds);
            model.addAttribute("sourceUsed", "db");
            return "entidad/tabla_registro";
        }

        // 2) Fallback DBF
        var dbf = dbfService.listarEntidadesAll(gestion, q);
        // mapea a Entidad (solo para pintar la misma tabla; idEntidad = null → sin editar/borrar)
        List<Entidad> vm = new ArrayList<>(dbf.size());
        for (var d : dbf) {
            Entidad e = new Entidad();
            e.setIdEntidad(null); // importante para deshabilitar botones
            e.setGestion(d.getGestion());
            e.setEntidadCodigo(d.getEntidadCodigo());
            e.setDescripcion(d.getDescripcion());
            e.setSigla(d.getSigla());
            e.setSectorEnt(d.getSectorEnt());
            e.setSubsecEnt(d.getSubsecEnt());
            e.setAreaEnt(d.getAreaEnt());
            e.setSubareaEnt(d.getSubareaEnt());
            e.setNivelInst(d.getNivelInst());
            vm.add(e);
        }
        // ids vacíos para mantener el tamaño del arreglo
        for (int i=0; i<vm.size(); i++) encryptedIds.add("");

        model.addAttribute("listasEntidades", vm);
        model.addAttribute("id_encryptado", encryptedIds);
        model.addAttribute("sourceUsed", "dbf");
        return "entidad/tabla_registro";
    }

    // SYNC: importar DBF → BD (upsert por gestion+entidad_codigo)
    @ValidarUsuarioAutenticado
    @PostMapping("/sync-from-mounted")
    @ResponseBody
    public ResponseEntity<?> syncFromMounted(
            @RequestParam(name="q", required=false) String q,
            @RequestParam(name="gestion", required=false) Short gestion) {
        try {
            var registros = dbfService.listarEntidadesAll(gestion, q);
            int inserted=0, updated=0;
            List<Entidad> batch = new ArrayList<>(500);

            for (var d : registros) {
                var opt = entidadService.findByGestionAndEntidadCodigo(d.getGestion(), d.getEntidadCodigo());
                Entidad e = opt.orElseGet(Entidad::new);
                boolean nuevo = e.getIdEntidad()==null;

                e.setGestion(d.getGestion());
                e.setEntidadCodigo(d.getEntidadCodigo());
                e.setDescripcion(d.getDescripcion());
                e.setSigla(d.getSigla());
                e.setSectorEnt(d.getSectorEnt());
                e.setSubsecEnt(d.getSubsecEnt());
                e.setAreaEnt(d.getAreaEnt());
                e.setSubareaEnt(d.getSubareaEnt());
                e.setNivelInst(d.getNivelInst());

                batch.add(e);
                if (nuevo) inserted++; else updated++;
                if (batch.size()==500) { entidadService.saveAll(batch); batch.clear(); }
            }
            if (!batch.isEmpty()) entidadService.saveAll(batch);

            return ResponseEntity.ok(Map.of(
                "ok", true,
                "totalLeidas", registros.size(),
                "insertados", inserted,
                "actualizados", updated
            ));
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().body(Map.of(
                "ok", false,
                "message", "Error sincronizando ENTIDADES: " + ex.getMessage()
            ));
        }
    }
}