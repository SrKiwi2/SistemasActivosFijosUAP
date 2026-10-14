package com.usic.SistemasActivosFijosUAP;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.ByteArrayOutputStream;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.usic.SistemasActivosFijosUAP.controller.rest.MapaEspacialController;
import com.usic.SistemasActivosFijosUAP.model.dao.IActivoDao;
import com.usic.SistemasActivosFijosUAP.model.dao.IMapaEspacialDao;
import com.usic.SistemasActivosFijosUAP.model.dao.IMapaEspacialPublicacionDao;
import com.usic.SistemasActivosFijosUAP.model.dao.IOficinaDao;
import com.usic.SistemasActivosFijosUAP.model.dao.IPredioDao;
import com.usic.SistemasActivosFijosUAP.model.entity.MapaEspacial;
import com.usic.SistemasActivosFijosUAP.model.entity.MapaEspacialPublicacion;
import com.usic.SistemasActivosFijosUAP.model.entity.Activo;
import com.usic.SistemasActivosFijosUAP.model.entity.Oficina;
import com.usic.SistemasActivosFijosUAP.model.entity.Predio;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;

class MapaEspacialControllerTest {
    private final IMapaEspacialDao mapas = mock(IMapaEspacialDao.class);
    private final IMapaEspacialPublicacionDao publicaciones = mock(IMapaEspacialPublicacionDao.class);
    private final IPredioDao predios = mock(IPredioDao.class);
    private final IOficinaDao oficinas = mock(IOficinaDao.class);
    private final IActivoDao activos = mock(IActivoDao.class);
    private final ObjectMapper json = new ObjectMapper();
    private final MapaEspacialController controller = new MapaEspacialController(mapas, publicaciones, predios, oficinas, activos, json);

    private MockHttpServletRequest editor() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession().setAttribute("usuario", new Usuario());
        request.getSession().setAttribute("opciones", Set.of("opcion_mapa_diseno"));
        return request;
    }

    private MockHttpServletRequest publicador() {
        MockHttpServletRequest request = editor();
        request.getSession().setAttribute("opciones", Set.of("opcion_mapa_diseno", "opcion_mapa_publicar"));
        return request;
    }

    private MockHttpServletRequest lector() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession().setAttribute("usuario", new Usuario());
        request.getSession().setAttribute("opciones", Set.of("opcion_mapa_explorar"));
        return request;
    }

    @Test
    void borradorNoApareceEnExploradorHastaPublicarse() throws Exception {
        when(predios.existsById(1L)).thenReturn(true);
        MapaEspacial mapa = new MapaEspacial(); mapa.setVersion(2L); mapa.setPublicada(false);
        mapa.setEscena("{\"edificios\":[{\"id\":\"b1\",\"nombre\":\"BORRADOR\"}]}");
        when(mapas.findByPredioIdPredio(1L)).thenReturn(Optional.of(mapa));
        assertEquals(0, ((com.fasterxml.jackson.databind.JsonNode) controller.resumen(1L, lector()).get("escena"))
                .path("edificios").size());
        assertEquals(0, controller.buscarUbicaciones(1L, "BORRADOR", lector()).size());
        assertEquals("BORRADOR", ((com.fasterxml.jackson.databind.JsonNode) controller.mapa(1L, editor()).get("escena"))
                .path("edificios").get(0).path("nombre").asText());
        assertEquals(403, assertThrows(ResponseStatusException.class,
                () -> controller.mapa(1L, lector())).getStatusCode().value());
        assertEquals(403, assertThrows(ResponseStatusException.class,
                () -> controller.verFondo(1L, lector())).getStatusCode().value());
        assertEquals(404, assertThrows(ResponseStatusException.class,
                () -> controller.detalleEdificio(1L, "b1", editor())).getStatusCode().value());
    }

    @Test
    void soloUsuarioHabilitadoPublicaYSuVersionQuedaCongelada() throws Exception {
        long predioId = Long.MAX_VALUE - 201;
        Predio predio = new Predio(); predio.setIdPredio(predioId);
        MapaEspacial mapa = new MapaEspacial(); mapa.setPredio(predio); mapa.setVersion(0L);
        mapa.setPublicada(false); mapa.setNumeroPublicacion(0L); mapa.setEscena("{\"edificios\":[]}");
        when(mapas.findByPredioIdPredio(predioId)).thenReturn(Optional.of(mapa));
        when(predios.existsById(predioId)).thenReturn(true);
        when(mapas.saveAndFlush(any(MapaEspacial.class))).thenAnswer(inv -> {
            MapaEspacial m = inv.getArgument(0); m.setVersion(m.getVersion() + 1); return m;
        });
        var cuerpo = json.readTree("{\"version\":0}");
        assertEquals(403, assertThrows(ResponseStatusException.class,
                () -> controller.publicar(predioId, cuerpo, editor())).getStatusCode().value());
        assertEquals(1L, controller.publicar(predioId, cuerpo, publicador()).get("numero"));
        assertEquals(true, mapa.getPublicada());
        assertEquals("{\"edificios\":[]}", mapa.getEscenaPublicada());
        verify(publicaciones).save(any(MapaEspacialPublicacion.class));
        mapa.setEscena("{\"edificios\":[{\"id\":\"nuevo\",\"nombre\":\"SOLO BORRADOR\"}]}");
        assertEquals(0, ((com.fasterxml.jackson.databind.JsonNode) controller.resumen(predioId, editor()).get("escena"))
                .path("edificios").size());
        assertEquals(409, assertThrows(ResponseStatusException.class,
                () -> controller.publicar(predioId, cuerpo, publicador())).getStatusCode().value());
    }

    @Test
    void recuperarVersionSoloCambiaBorrador() throws Exception {
        long predioId = Long.MAX_VALUE - 202;
        Predio predio = new Predio(); predio.setIdPredio(predioId);
        MapaEspacial mapa = new MapaEspacial(); mapa.setPredio(predio); mapa.setVersion(3L);
        mapa.setPublicada(true); mapa.setNumeroPublicacion(2L);
        mapa.setEscena("{\"edificios\":[]}"); mapa.setEscenaPublicada("{\"edificios\":[]}");
        MapaEspacialPublicacion anterior = new MapaEspacialPublicacion(); anterior.setNumero(1L);
        anterior.setEscena("{\"edificios\":[],\"vista\":{\"x\":0,\"y\":0,\"ancho\":500,\"fondo\":500}}");
        when(mapas.findByPredioIdPredio(predioId)).thenReturn(Optional.of(mapa));
        when(publicaciones.findByPredioIdPredioAndNumero(predioId, 1L)).thenReturn(Optional.of(anterior));
        when(mapas.saveAndFlush(any(MapaEspacial.class))).thenAnswer(inv -> {
            MapaEspacial m = inv.getArgument(0); m.setVersion(4L); return m;
        });
        assertEquals(1L, controller.restaurar(predioId, 1L, json.readTree("{\"version\":3}"), editor())
                .get("numeroRestaurado"));
        assertEquals(anterior.getEscena(), mapa.getEscena());
        assertEquals("{\"edificios\":[]}", mapa.getEscenaPublicada());
    }

    @Test
    void conservaPlanoAnteriorAlPrimerGuardadoYNoFiltraBorrador() throws Exception {
        long predioId = Long.MAX_VALUE - 203;
        Predio predio = new Predio(); predio.setIdPredio(predioId);
        MapaEspacial mapa = new MapaEspacial(); mapa.setPredio(predio); mapa.setVersion(3L);
        mapa.setEscena("{\"edificios\":[{\"id\":\"anterior\",\"nombre\":\"PLANO ANTERIOR\"}]}");
        when(predios.findById(predioId)).thenReturn(Optional.of(predio));
        when(predios.existsById(predioId)).thenReturn(true);
        when(mapas.findByPredioIdPredio(predioId)).thenReturn(Optional.of(mapa));
        when(mapas.saveAndFlush(any(MapaEspacial.class))).thenAnswer(inv -> {
            MapaEspacial m = inv.getArgument(0); m.setVersion(4L); return m;
        });
        controller.guardar(predioId, json.readTree("{\"version\":3,\"escena\":{\"edificios\":[]}}"), editor());
        assertEquals(1L, mapa.getNumeroPublicacion());
        assertEquals("PLANO ANTERIOR", ((com.fasterxml.jackson.databind.JsonNode)
                controller.resumen(predioId, editor()).get("escena")).path("edificios").get(0).path("nombre").asText());
        verify(publicaciones).save(any(MapaEspacialPublicacion.class));
    }

    @Test
    void imagenPublicadaPermaneceAlCambiarElBorrador() throws Exception {
        long predioId = Long.MAX_VALUE - 204;
        Predio predio = new Predio(); predio.setIdPredio(predioId);
        MapaEspacial mapa = new MapaEspacial(); mapa.setPredio(predio); mapa.setVersion(0L);
        mapa.setPublicada(false); mapa.setNumeroPublicacion(0L); mapa.setEscena("{\"edificios\":[]}");
        when(mapas.findByPredioIdPredio(predioId)).thenReturn(Optional.of(mapa));
        when(mapas.saveAndFlush(any(MapaEspacial.class))).thenAnswer(inv -> {
            MapaEspacial m = inv.getArgument(0); m.setVersion(1L); return m;
        });
        Path fondo = Path.of("uploads", "mapa-espacial", predioId + ".jpg");
        Files.createDirectories(fondo.getParent());
        try {
            Files.write(fondo, new byte[] { 1, 2, 3 });
            controller.publicar(predioId, json.readTree("{\"version\":0}"), publicador());
            Files.write(fondo, new byte[] { 4, 5, 6 });
            assertArrayEquals(new byte[] { 1, 2, 3 }, controller.verFondoPublicado(predioId, editor()).getBody());
            assertArrayEquals(new byte[] { 4, 5, 6 }, controller.verFondo(predioId, editor()).getBody());
        } finally {
            Files.deleteIfExists(fondo);
            if (mapa.getFondoPublicado() != null)
                Files.deleteIfExists(Path.of("uploads", "mapa-espacial").resolve(mapa.getFondoPublicado()));
        }
    }

    @Test
    void exploradorCargaOficinasSoloAlAbrirEdificio() {
        when(predios.existsById(1L)).thenReturn(true);
        MapaEspacial mapa = new MapaEspacial();
        mapa.setVersion(0L);
        mapa.setEscena("""
            {"edificios":[{"id":"b1","nombre":"BIBLIOTECA","x":10,"y":20,"ancho":80,"fondo":60,
              "pisos":[{"id":"pb","nombre":"PLANTA BAJA","altura":4,"trazos":[{"tipo":"MURO"}],
                "espacios":[{"id":"s1","oficinaId":7,"activos":[{"activoId":99}]}]}]}]}
            """);
        when(mapas.findByPredioIdPredio(1L)).thenReturn(Optional.of(mapa));
        var resumen = controller.resumen(1L, editor());
        var escena = (com.fasterxml.jackson.databind.JsonNode) resumen.get("escena");
        assertEquals(0, escena.path("edificios").get(0).path("pisos").get(0).path("espacios").size());
        assertEquals(1, escena.path("edificios").get(0).path("resumenOficinas").asInt());
        verify(oficinas, never()).findAllById(any());
        verify(activos, never()).findAllById(any());

        Oficina oficina = new Oficina(); oficina.setIdOficina(7L); oficina.setCodOfi((short) 101); oficina.setNombre("BIBLIOTECA");
        when(oficinas.findAllById(Set.of(7L))).thenReturn(java.util.List.of(oficina));
        Activo activo = new Activo(); activo.setIdActivo(99L); activo.setCodigo("AF-99"); activo.setDescripcion("MESA");
        when(activos.findAllById(Set.of(99L))).thenReturn(java.util.List.of(activo));
        var detalle = controller.detallePiso(1L, "b1", "pb", editor());
        assertEquals("s1", ((com.fasterxml.jackson.databind.JsonNode) detalle.get("piso"))
                .path("espacios").get(0).path("id").asText());
        assertEquals(1, ((java.util.Map<?, ?>) detalle.get("oficinas")).size());
        assertEquals(1, ((java.util.Map<?, ?>) detalle.get("activos")).size());
    }

    @Test
    void alAbrirUnPisoNoEnvíaOficinasDeOtrosNiveles() {
        when(predios.existsById(1L)).thenReturn(true);
        MapaEspacial mapa = new MapaEspacial(); mapa.setVersion(0L);
        mapa.setEscena("""
            {"edificios":[{"id":"b1","nombre":"BLOQUE","pisos":[
              {"id":"pb","nombre":"PLANTA BAJA","espacios":[{"id":"s1","oficinaId":7,"activos":[]}]},
              {"id":"p1","nombre":"PRIMER PISO","espacios":[{"id":"s2","oficinaId":8,"activos":[]}]}
            ]}]}
            """);
        when(mapas.findByPredioIdPredio(1L)).thenReturn(Optional.of(mapa));
        Oficina oficina = new Oficina(); oficina.setIdOficina(7L); oficina.setCodOfi((short) 101);
        oficina.setNombre("OFICINA BAJA");
        when(oficinas.findAllById(Set.of(7L))).thenReturn(java.util.List.of(oficina));
        var detalle = controller.detallePiso(1L, "b1", "pb", lector());
        assertEquals("pb", ((com.fasterxml.jackson.databind.JsonNode) detalle.get("piso")).path("id").asText());
        assertEquals(1, ((java.util.Map<?, ?>) detalle.get("oficinas")).size());
        verify(oficinas).findAllById(Set.of(7L));
        verify(oficinas, never()).findAllById(Set.of(8L));
    }

    @Test
    void guardaEdificioNombradoSinInventarOficinas() throws Exception {
        Predio predio = new Predio(); predio.setIdPredio(1L);
        when(predios.findById(1L)).thenReturn(Optional.of(predio));
        when(mapas.findByPredioIdPredio(1L)).thenReturn(Optional.empty());
        when(mapas.saveAndFlush(any(MapaEspacial.class))).thenAnswer(inv -> {
            MapaEspacial m = inv.getArgument(0); m.setVersion(0L); return m;
        });
        String entrada = """
            {"version":-1,"escena":{"edificios":[{"id":"ed-1","nombre":"EDIFICIO CENTRAL",
            "tipo":"EDIFICIO","x":20,"y":20,"ancho":200,"fondo":140,"altura":14,
            "pisos":[{"id":"pb","nombre":"PLANTA BAJA","altura":4,"espacios":[]}] }]}}
            """;
        assertEquals(0L, controller.guardar(1L, json.readTree(entrada), editor()).get("version"));
        verify(mapas).saveAndFlush(any(MapaEspacial.class));
    }

    @Test
    void rechazaOficinaDeOtroPredio() throws Exception {
        Predio predio = new Predio(); predio.setIdPredio(1L);
        Predio otro = new Predio(); otro.setIdPredio(2L);
        Oficina oficina = new Oficina(); oficina.setIdOficina(7L); oficina.setPredio(otro); oficina.setEstado("ACTIVO");
        when(predios.findById(1L)).thenReturn(Optional.of(predio));
        when(oficinas.findAllById(Set.of(7L))).thenReturn(java.util.List.of(oficina));
        String entrada = """
            {"version":-1,"escena":{"edificios":[{"id":"ed-1","nombre":"EDIFICIO CENTRAL",
            "tipo":"EDIFICIO","x":20,"y":20,"ancho":200,"fondo":140,"altura":14,
            "pisos":[{"id":"pb","nombre":"PLANTA BAJA","altura":4,"espacios":[
            {"id":"s1","nombre":"OFICINA","tipo":"OFICINA","x":2,"y":2,"ancho":40,"fondo":30,
            "oficinaId":7,"activos":[]}]}]}]}}
            """;
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> controller.guardar(1L, json.readTree(entrada), editor()));
        assertEquals(400, error.getStatusCode().value());
    }

    @Test
    void rechazaActivoQueYaPerteneceAOtraOficina() throws Exception {
        Predio predio = new Predio(); predio.setIdPredio(1L);
        Oficina oficina = new Oficina(); oficina.setIdOficina(7L); oficina.setPredio(predio); oficina.setEstado("ACTIVO");
        Oficina otra = new Oficina(); otra.setIdOficina(8L); otra.setPredio(predio);
        Activo activo = new Activo(); activo.setIdActivo(99L); activo.setOficina(otra); activo.setEstado("ACTIVO");
        when(predios.findById(1L)).thenReturn(Optional.of(predio));
        when(oficinas.findAllById(Set.of(7L))).thenReturn(java.util.List.of(oficina));
        when(activos.findAllById(Set.of(99L))).thenReturn(java.util.List.of(activo));
        String entrada = """
            {"version":-1,"escena":{"edificios":[{"id":"ed-1","nombre":"EDIFICIO CENTRAL",
            "tipo":"EDIFICIO","x":20,"y":20,"ancho":200,"fondo":140,"altura":14,
            "pisos":[{"id":"pb","nombre":"PLANTA BAJA","altura":4,"espacios":[
            {"id":"s1","nombre":"OFICINA","tipo":"OFICINA","x":2,"y":2,"ancho":40,"fondo":30,
            "oficinaId":7,"activos":[{"activoId":99,"x":2,"y":2,"ancho":4,"fondo":3,"giro":0}]}]}]}]}}
            """;
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> controller.guardar(1L, json.readTree(entrada), editor()));
        assertEquals(400, error.getStatusCode().value());
    }

    @Test
    void permiteVariasOficinasYPasilloEnPlantaBaja() throws Exception {
        Predio predio = new Predio(); predio.setIdPredio(1L);
        Oficina primera = new Oficina(); primera.setIdOficina(7L); primera.setPredio(predio); primera.setEstado("ACTIVO");
        Oficina segunda = new Oficina(); segunda.setIdOficina(8L); segunda.setPredio(predio); segunda.setEstado("ACTIVO");
        when(predios.findById(1L)).thenReturn(Optional.of(predio));
        when(oficinas.findAllById(Set.of(7L, 8L))).thenReturn(java.util.List.of(primera, segunda));
        when(mapas.saveAndFlush(any(MapaEspacial.class))).thenAnswer(inv -> {
            MapaEspacial m = inv.getArgument(0); m.setVersion(0L); return m;
        });
        String entrada = """
            {"version":-1,"escena":{"edificios":[{"id":"e","nombre":"EDIFICIO DE PRUEBA",
            "tipo":"EDIFICIO","x":20,"y":20,"ancho":200,"fondo":140,"altura":14,
            "pisos":[{"id":"pb","nombre":"PLANTA BAJA","altura":4,"espacios":[
              {"id":"s1","nombre":"OFICINA 1","tipo":"OFICINA","x":2,"y":2,"ancho":40,"fondo":30,"oficinaId":7,"activos":[]},
              {"id":"s2","nombre":"CUARTO DE OFICINA 1","tipo":"CUARTO","x":44,"y":2,"ancho":40,"fondo":30,"oficinaId":7,"activos":[]},
              {"id":"s3","nombre":"OFICINA 2","tipo":"OFICINA","x":86,"y":2,"ancho":40,"fondo":30,"oficinaId":8,"activos":[]},
              {"id":"s4","nombre":"PASILLO","tipo":"PASILLO","x":2,"y":40,"ancho":124,"fondo":12,"oficinaId":null,"activos":[]}
            ]}]}]}}
            """;
        assertEquals(0L, controller.guardar(1L, json.readTree(entrada), editor()).get("version"));
        verify(mapas).saveAndFlush(any(MapaEspacial.class));
    }

    @Test
    void guardaContornosIrregularesYDetallesDePlano() throws Exception {
        Predio predio = new Predio(); predio.setIdPredio(1L);
        when(predios.findById(1L)).thenReturn(Optional.of(predio));
        when(mapas.saveAndFlush(any(MapaEspacial.class))).thenAnswer(inv -> {
            MapaEspacial m = inv.getArgument(0); m.setVersion(0L); return m;
        });
        String entrada = """
            {"version":-1,"escena":{"vista":{"x":-100,"y":-100,"ancho":1500,"fondo":1100},
            "edificios":[{"id":"e","nombre":"EDIFICIO IRREGULAR",
            "tipo":"EDIFICIO","x":20,"y":20,"ancho":200,"fondo":140,"altura":14,
            "color":"#F8EB69","textoGiro":-75,
            "puntos":[{"x":20,"y":20},{"x":220,"y":20},{"x":220,"y":80},
            {"x":160,"y":80},{"x":160,"y":160},{"x":20,"y":160}],
            "pisos":[{"id":"pb","nombre":"PLANTA BAJA","altura":4,
            "trazos":[{"id":"puerta1","tipo":"PUERTA","x1":5,"y1":40,"x2":15,"y2":40}],
            "espacios":[{"id":"s1","nombre":"OFICINA SIN VINCULO","tipo":"OFICINA",
            "x":5,"y":5,"ancho":40,"fondo":30,"color":"#9AD9E9","giro":15,
            "puntos":[{"x":5,"y":5},{"x":45,"y":5},{"x":45,"y":35},
            {"x":25,"y":25},{"x":5,"y":35}],"oficinaId":null,"activos":[]}]}]}]}}
            """;
        assertEquals(0L, controller.guardar(1L, json.readTree(entrada), editor()).get("version"));
        verify(mapas).saveAndFlush(any(MapaEspacial.class));
    }

    @Test
    void rechazaContornoQueSeCruza() throws Exception {
        Predio predio = new Predio(); predio.setIdPredio(1L);
        when(predios.findById(1L)).thenReturn(Optional.of(predio));
        String entrada = """
            {"version":-1,"escena":{"edificios":[{"id":"e","nombre":"FORMA INVALIDA",
            "tipo":"EDIFICIO","x":0,"y":0,"ancho":100,"fondo":100,"altura":14,
            "puntos":[{"x":0,"y":0},{"x":100,"y":100},{"x":0,"y":100},
            {"x":100,"y":0},{"x":100,"y":50}],
            "pisos":[{"id":"pb","nombre":"PLANTA BAJA","altura":4,"espacios":[]}]}]}}
            """;
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> controller.guardar(1L, json.readTree(entrada), editor()));
        assertEquals(400, error.getStatusCode().value());
    }

    @Test
    void rechazaColorInvalido() throws Exception {
        Predio predio = new Predio(); predio.setIdPredio(1L);
        when(predios.findById(1L)).thenReturn(Optional.of(predio));
        String entrada = """
            {"version":-1,"escena":{"edificios":[{"id":"e","nombre":"EDIFICIO",
            "tipo":"EDIFICIO","x":0,"y":0,"ancho":100,"fondo":100,"altura":14,
            "color":"amarillo","pisos":[{"id":"pb","nombre":"PLANTA BAJA","altura":4,"espacios":[]}]}]}}
            """;
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> controller.guardar(1L, json.readTree(entrada), editor()));
        assertEquals(400, error.getStatusCode().value());
    }

    @Test
    void optimizaImagenReferencialParaElMapa() throws Exception {
        System.setProperty("java.awt.headless", "true");
        long id = Long.MAX_VALUE - 101;
        when(predios.existsById(id)).thenReturn(true);
        BufferedImage imagen = new BufferedImage(20, 10, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(imagen, "png", bytes);
        MockMultipartFile archivo = new MockMultipartFile("archivo", "referencia.png", "image/png", bytes.toByteArray());
        Path ruta = Path.of("uploads", "mapa-espacial", id + ".jpg");
        try {
            var respuesta = controller.subirFondo(id, archivo, -1L, editor());
            assertEquals(20, respuesta.get("ancho"));
            assertEquals(10, respuesta.get("alto"));
            assertEquals(true, Files.isRegularFile(ruta));
        } finally {
            Files.deleteIfExists(ruta);
        }
    }

    @Test
    void listaInventarioDeOficinaConResponsableSinPosicionPrevia() {
        IActivoDao.ActivoInventarioMapa fila = mock(IActivoDao.ActivoInventarioMapa.class);
        when(fila.getId()).thenReturn(99L);
        when(fila.getCodigo()).thenReturn("AF-99");
        when(fila.getDescripcion()).thenReturn("ESCRITORIO");
        when(fila.getResponsable()).thenReturn("ANA PEREZ");
        when(activos.inventarioMapa(7L, "", PageRequest.of(0, 10)))
                .thenReturn(new PageImpl<>(java.util.List.of(fila), PageRequest.of(0, 10), 1));
        var respuesta = controller.buscarActivos(7L, "", 0, editor());
        assertEquals(1L, respuesta.total());
        assertEquals("ANA PEREZ", respuesta.items().get(0).responsable());
        assertEquals("ESCRITORIO", respuesta.items().get(0).descripcion());
    }

    @Test
    void guardaCuartoMobiliarioYActivoVinculado() throws Exception {
        Predio predio = new Predio(); predio.setIdPredio(1L);
        Oficina oficina = new Oficina(); oficina.setIdOficina(7L); oficina.setPredio(predio); oficina.setEstado("ACTIVO");
        Activo activo = new Activo(); activo.setIdActivo(99L); activo.setOficina(oficina); activo.setEstado("ACTIVO");
        when(predios.findById(1L)).thenReturn(Optional.of(predio));
        when(oficinas.findAllById(Set.of(7L))).thenReturn(java.util.List.of(oficina));
        when(activos.findAllById(Set.of(99L))).thenReturn(java.util.List.of(activo));
        when(mapas.saveAndFlush(any(MapaEspacial.class))).thenAnswer(inv -> {
            MapaEspacial m = inv.getArgument(0); m.setVersion(0L); return m;
        });
        String entrada = """
            {"version":-1,"escena":{"edificios":[{"id":"e","nombre":"BIBLIOTECA",
            "tipo":"EDIFICIO","x":0,"y":0,"ancho":100,"fondo":80,"altura":4,
            "pisos":[{"id":"pb","nombre":"PLANTA BAJA","altura":4,"espacios":[
            {"id":"s","nombre":"OFICINA","tipo":"OFICINA","x":0,"y":0,"ancho":80,"fondo":60,
            "oficinaId":7,"cuartos":[{"id":"c","nombre":"SALA 1","x":2,"y":2,"ancho":30,"fondo":20,
            "puntos":[{"x":2,"y":2},{"x":32,"y":2},{"x":32,"y":22},{"x":2,"y":22}]}],
            "mobiliario":[{"id":"m","nombre":"Mesa auxiliar","figura":"MESA","x":4,"y":4,"ancho":10,"fondo":5,"giro":0}],
            "activos":[{"activoId":99,"figura":"EQUIPO","x":15,"y":10,"ancho":8,"fondo":6,"giro":30}]}]}]}]}}
            """;
        assertEquals(0L, controller.guardar(1L, json.readTree(entrada), editor()).get("version"));
        verify(mapas).saveAndFlush(any(MapaEspacial.class));
    }

    @Test
    void rechazaMobiliarioFueraDeLaOficina() throws Exception {
        Predio predio = new Predio(); predio.setIdPredio(1L);
        when(predios.findById(1L)).thenReturn(Optional.of(predio));
        String entrada = """
            {"version":-1,"escena":{"edificios":[{"id":"e","nombre":"BIBLIOTECA",
            "tipo":"EDIFICIO","x":0,"y":0,"ancho":100,"fondo":80,"altura":4,
            "pisos":[{"id":"pb","nombre":"PLANTA BAJA","altura":4,"espacios":[
            {"id":"s","nombre":"OFICINA","tipo":"OFICINA","x":0,"y":0,"ancho":80,"fondo":60,
            "oficinaId":null,"mobiliario":[{"id":"m","nombre":"Mesa","figura":"MESA",
            "x":75,"y":4,"ancho":10,"fondo":5,"giro":0}],"activos":[]}]}]}]}}
            """;
        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> controller.guardar(1L, json.readTree(entrada), editor())).getStatusCode().value());
    }

    @Test
    void devuelveFichaActualDelActivoSeleccionado() {
        IActivoDao.ActivoDetalleMapa ficha = mock(IActivoDao.ActivoDetalleMapa.class);
        when(ficha.getCodigo()).thenReturn("AF-99");
        when(ficha.getResponsable()).thenReturn("ANA PEREZ");
        when(activos.detalleMapa(99L)).thenReturn(Optional.of(ficha));
        assertEquals("AF-99", controller.detalleActivo(99L, editor()).getCodigo());
        assertEquals("ANA PEREZ", controller.detalleActivo(99L, editor()).getResponsable());
    }

    @Test
    void buscaOficinaDelPlanoPorNombreSinCargarTodoElEdificio() {
        when(predios.existsById(1L)).thenReturn(true);
        MapaEspacial mapa = new MapaEspacial();
        mapa.setEscena("""
            {"edificios":[{"id":"e1","nombre":"BIBLIOTECA","pisos":[
              {"id":"pb","nombre":"PLANTA BAJA","espacios":[
                {"id":"s1","nombre":"SALA PRINCIPAL","oficinaId":7}]}]}]}
            """);
        when(mapas.findByPredioIdPredio(1L)).thenReturn(Optional.of(mapa));
        Predio predio = new Predio(); predio.setIdPredio(1L);
        Oficina oficina = new Oficina(); oficina.setIdOficina(7L); oficina.setPredio(predio); oficina.setCodOfi((short) 101);
        oficina.setNombre("CENTRO DE COMPUTACIÓN");
        when(oficinas.findAllById(Set.of(7L))).thenReturn(java.util.List.of(oficina));
        var resultado = controller.buscarUbicaciones(1L, "computacion", editor());
        assertEquals(1, resultado.size());
        assertEquals("OFICINA", resultado.get(0).tipo());
        assertEquals("e1", resultado.get(0).edificioId());
        assertEquals("s1", resultado.get(0).espacioId());
        assertEquals("BIBLIOTECA", controller.buscarUbicaciones(1L, "biblioteca", editor()).get(0).etiqueta());
    }
}
