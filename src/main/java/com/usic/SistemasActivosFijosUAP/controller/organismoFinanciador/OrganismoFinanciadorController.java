package com.usic.SistemasActivosFijosUAP.controller.organismoFinanciador;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.usic.SistemasActivosFijosUAP.anotacion.ValidarUsuarioAutenticado;
import com.usic.SistemasActivosFijosUAP.config.Encriptar;
import com.usic.SistemasActivosFijosUAP.model.IService.IOrganismoFinancieroService;
import com.usic.SistemasActivosFijosUAP.model.entity.OrganismoFinanciero;
import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/administracion/organismo")
@RequiredArgsConstructor
public class OrganismoFinanciadorController {

    private final IOrganismoFinancieroService organismoFinancieroService;
    
    @ValidarUsuarioAutenticado
    @GetMapping("/vista")
    public String inicio_of() {
        return "organismoFinanciador/vista";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/tabla-registros")
    public String tablaRegistros_of(Model model) throws Exception {
        List<OrganismoFinanciero> listasOrganismoFinanciero = organismoFinancieroService.findAll();
        List<String> encryptedIds = new ArrayList<>();
        for (OrganismoFinanciero organismos : listasOrganismoFinanciero) {
            String id_encryptado = Encriptar.encrypt(Long.toString(organismos.getIdOrganismoFinanciero()));
            encryptedIds.add(id_encryptado);
        }
        model.addAttribute("listasOrganismoFinanciero", listasOrganismoFinanciero);
        model.addAttribute("id_encryptado", encryptedIds);
        return "organismoFinanciador/tabla_registro";
    }
}