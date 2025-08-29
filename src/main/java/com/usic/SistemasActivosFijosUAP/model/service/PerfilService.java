package com.usic.SistemasActivosFijosUAP.model.service;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;

import com.usic.SistemasActivosFijosUAP.model.IService.IActivoService;
import com.usic.SistemasActivosFijosUAP.model.dto.OficinaDTO;
import com.usic.SistemasActivosFijosUAP.model.dto.PerfilDTO;
import com.usic.SistemasActivosFijosUAP.model.dto.ResponsableDTO;
import com.usic.SistemasActivosFijosUAP.model.endpoint.OficinaConteo;
import com.usic.SistemasActivosFijosUAP.model.entity.Persona;
import com.usic.SistemasActivosFijosUAP.model.entity.Responsable;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PerfilService {
    // Usa el repository que tiene las queries de conteo/suma
    private final IActivoService activoRepository;
    private final com.usic.SistemasActivosFijosUAP.model.IService.IResponsableService responsableService;

    @Transactional
    public PerfilDTO buildPerfilDTO(Persona persona, String rol, String username) {
        PerfilDTO dto = new PerfilDTO();

        // Encabezado
        dto.setNombreCompleto(persona.getNombreCompleto());
        dto.setCi(persona.getCi() + (persona.getExtension() != null ? " " + persona.getExtension() : ""));
        dto.setRol(rol);
        dto.setUsuario(username);
        dto.setCorreo(persona.getCorreo());
        dto.setNacionalidad(persona.getNacionalidad() != null ? persona.getNacionalidad().getNombre() : "-");
        dto.setGenero(persona.getGenero() != null ? persona.getGenero().getNombre() : "-");
        String ini = ((persona.getNombre() != null && !persona.getNombre().isBlank())
                ? persona.getNombre().substring(0, 1)
                : "A")
                + ((persona.getPaterno() != null && !persona.getPaterno().isBlank())
                        ? persona.getPaterno().substring(0, 1)
                        : "Z");
        dto.setAvatarIniciales(ini.toUpperCase());

        // Métricas
        List<OficinaConteo> conteos = activoRepository.conteoPorOficinaDePersona(persona.getIdPersona());
        long total = conteos.stream().mapToLong(OficinaConteo::getTotal).sum();
        dto.setActivosTotal(total);

        Double suma = activoRepository.sumaCostoPorPersona(persona.getIdPersona());
        NumberFormat nf = NumberFormat.getNumberInstance(new Locale("es", "BO"));
        nf.setMinimumFractionDigits(2);
        nf.setMaximumFractionDigits(2);
        dto.setCostoTotal(nf.format(suma != null ? suma : 0.0));

        // Responsables -> a tu ResponsableDTO (ahora con ctor vacío)
        List<Responsable> resps = responsableService.findAllByPersona(persona);
        List<ResponsableDTO> rlist = new ArrayList<>();
        for (Responsable r : resps) {
            ResponsableDTO rd = new ResponsableDTO();
            rd.setId(r.getIdResponsable());
            rd.setNombre(r.getPersona() != null ? r.getPersona().getNombreCompleto() : "-");
            rd.setCodigoFuncionario(r.getCodigo_funcionario());
            rd.setCargo(r.getCargo() != null ? r.getCargo().getNombre() : "—");
            rd.setOficina(r.getOficina() != null ? r.getOficina().getNombre() : "—");
            rlist.add(rd);
        }
        dto.setResponsables(rlist);

        // Oficinas -> a tu OficinaDTO (con ctor vacío)
        List<OficinaDTO> olist = new ArrayList<>();
        for (OficinaConteo oc : conteos) {
            OficinaDTO od = new OficinaDTO();
            if (oc.getOficina() != null) {
                od.setId(oc.getOficina().getIdOficina());
                od.setNombre(oc.getOficina().getNombre());
                od.setCodigo(oc.getOficina().getCodigo());
            } else {
                od.setNombre("-");
                od.setCodigo("-");
            }
            od.setTotal(oc.getTotal());
            olist.add(od);
        }
        dto.setOficinas(olist);

        return dto;
    }
}