package com.usic.SistemasActivosFijosUAP;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.thymeleaf.context.Context;
import org.thymeleaf.context.IExpressionContext;
import org.thymeleaf.linkbuilder.StandardLinkBuilder;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import com.usic.SistemasActivosFijosUAP.model.dto.FiltrosAsignacionDTO;
import com.usic.SistemasActivosFijosUAP.model.dto.ResumenListadoAsignacionDTO;
import com.usic.SistemasActivosFijosUAP.model.entity.AsignacionActivo;

class TablaAsignacionesTemplateTest {

    @Test
    void tablaSeRenderizaConYSinPermisoFinanciero() {
        String conAcceso = render(true);
        String sinAcceso = render(false);
        assertTrue(conAcceso.contains("onclick=\"ordenarPor('costo')\""));
        assertFalse(sinAcceso.contains("onclick=\"ordenarPor('costo')\""));
        assertTrue(sinAcceso.contains("Reservado"));
        for (String html : List.of(conAcceso, sinAcceso)) {
            String cabecera = html.substring(html.indexOf("<thead"), html.indexOf("</thead>") + 8);
            assertTrue(Pattern.compile("<th\\b").matcher(cabecera).results().count() == 9,
                    "La cabecera debe tener nueve columnas");
            String cuerpo = html.substring(html.indexOf("<tbody"), html.indexOf("</tbody>") + 8);
            assertFalse(cuerpo.contains("colspan="), "DataTables no acepta celdas combinadas en tbody");
            var fila = Pattern.compile("<tr[^>]*class=\"asig-row\"[^>]*>(.*?)</tr>", Pattern.DOTALL)
                    .matcher(cuerpo);
            assertTrue(fila.find(), "Debe renderizarse una fila de asignación");
            assertTrue(Pattern.compile("<td\\b").matcher(fila.group(1)).results().count() == 9,
                    "La fila debe tener las mismas nueve columnas que la cabecera");
        }
    }

    private String render(boolean verFinanzas) {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode("HTML");
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        engine.setLinkBuilder(new StandardLinkBuilder() {
            @Override
            protected String computeContextPath(IExpressionContext context, String base,
                    Map<String, Object> parameters) {
                return "";
            }
        });

        AsignacionActivo acta = new AsignacionActivo();
        acta.setIdAsignacionActivo(1L);
        acta.setFechaAsignacion(LocalDateTime.of(2026, 10, 8, 9, 0));
        Context contexto = new Context();
        contexto.setVariable("puedeVerFinanzasActivo", verFinanzas);
        contexto.setVariable("asignaciones", List.of(acta));
        contexto.setVariable("paginaActual", new PageImpl<>(List.of(acta), PageRequest.of(0, 25), 1));
        contexto.setVariable("stats", ResumenListadoAsignacionDTO.VACIO);
        contexto.setVariable("filtros", FiltrosAsignacionDTO.normalizar(null, null, null, null, null,
                null, null, null, null));
        contexto.setVariable("resumenes", Map.of());
        contexto.setVariable("rubros", Map.of());
        contexto.setVariable("mapaUsuarios", Map.of());
        contexto.setVariable("carpetasPorGestion", Map.of());
        contexto.setVariable("actasPorMes", Map.of());
        contexto.setVariable("tamanosPagina", List.of(25));
        contexto.setVariable("paginasVisibles", List.of(0));
        contexto.setVariable("orden", "fecha");
        contexto.setVariable("desc", true);
        return engine.process("seguimiento/asignacion/tabla_registro", contexto);
    }
}
