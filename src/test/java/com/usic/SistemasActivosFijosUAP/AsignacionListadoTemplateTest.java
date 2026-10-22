package com.usic.SistemasActivosFijosUAP;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import com.usic.SistemasActivosFijosUAP.model.dto.FiltrosAsignacionDTO;
import com.usic.SistemasActivosFijosUAP.model.dto.ResumenAsignacionDTO;
import com.usic.SistemasActivosFijosUAP.model.dto.RubroAsignacionDTO;
import com.usic.SistemasActivosFijosUAP.model.entity.AsignacionActivo;

class AsignacionListadoTemplateTest {

    @Test
    void renderizaUnaPaginacionYElAccesoAlDetalleSinBaseDeDatos() {
        AsignacionActivo acta = new AsignacionActivo();
        acta.setIdAsignacionActivo(42L);
        acta.setNumeroAsignacion("PREV-42");

        Context context = new Context();
        context.setVariable("asignaciones", List.of(acta));
        context.setVariable("paginaActual", new PageImpl<>(List.of(acta), PageRequest.of(0, 6), 13));
        context.setVariable("paginasVisibles", List.of(0, 1, 2));
        context.setVariable("resumenes", Map.of());
        context.setVariable("rubros", Map.of());
        context.setVariable("orden", "fecha");
        context.setVariable("desc", true);
        context.setVariable("puedeVerFinanzasActivo", false);
        context.setVariable("filtros", FiltrosAsignacionDTO.normalizar(null, null, null, null,
                null, null, null, null, null));

        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode("HTML");
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);

        String html = engine.process("seguimiento/asignacion/tabla_registro", context);

        assertEquals(1, html.split("<nav ", -1).length - 1);
        assertTrue(html.contains("data-id-asignacion=\"42\""));
        assertTrue(html.contains("PREV-42"));
        assertTrue(html.contains("data-pagina=\"1\""));
        assertFalse(html.contains("dataTables_wrapper"));
        assertFalse(html.contains("Costo"));
    }

    @Test
    void muestraFechaRubrosYTotalSoloConPermiso() {
        AsignacionActivo acta = new AsignacionActivo();
        acta.setIdAsignacionActivo(42L);
        acta.setFechaAsignacion(LocalDateTime.of(2026, 10, 22, 9, 0));

        Context context = new Context();
        context.setVariable("asignaciones", List.of(acta));
        context.setVariable("paginaActual", new PageImpl<>(List.of(acta), PageRequest.of(0, 6), 1));
        context.setVariable("paginasVisibles", List.of(0));
        context.setVariable("resumenes", Map.of(42L, ResumenAsignacionDTO.desdeFila(
                new Object[]{42L, 123.45, 0L, 1L, 0L, 0L, 1L})));
        context.setVariable("rubros", Map.of(42L, List.of(new RubroAsignacionDTO("Muebles", "Silla", 1))));
        context.setVariable("orden", "fecha");
        context.setVariable("desc", true);
        context.setVariable("puedeVerFinanzasActivo", true);
        context.setVariable("filtros", FiltrosAsignacionDTO.normalizar(null, null, null, null,
                null, null, null, null, null));

        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode("HTML");
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);

        String html = engine.process("seguimiento/asignacion/tabla_registro", context);
        assertTrue(html.contains("22/10/2026"));
        assertTrue(html.contains("Muebles"));
        assertTrue(html.contains("123,45"));
        assertTrue(html.contains("Completa"));
    }

    @Test
    void renderizaModalConSeccionesYRegresoAlActa() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode("HTML");
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);

        String html = engine.process("seguimiento/asignacion/detalle_asignacion", new Context());
        assertTrue(html.contains("id=\"tab-general\""));
        assertTrue(html.contains("id=\"tab-activos\""));
        assertTrue(html.contains("class=\"asig-descripcion\""));
        assertTrue(html.contains("Volver al acta"));
    }
}
