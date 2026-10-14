package com.usic.SistemasActivosFijosUAP.controller.rest;

import java.util.HashMap;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.text.Normalizer;
import java.util.Locale;
import java.util.UUID;
import java.io.IOException;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.Duration;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.CacheControl;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.usic.SistemasActivosFijosUAP.config.RolesSciaf;
import com.usic.SistemasActivosFijosUAP.componet.SseEmitterRegistry;
import com.usic.SistemasActivosFijosUAP.model.dao.IActivoDao;
import com.usic.SistemasActivosFijosUAP.model.dao.IAuxiliarDao;
import com.usic.SistemasActivosFijosUAP.model.dao.IMapaEspacialDao;
import com.usic.SistemasActivosFijosUAP.model.dao.IMapaEspacialIconoAuxiliarDao;
import com.usic.SistemasActivosFijosUAP.model.dao.IMapaEspacialPublicacionDao;
import com.usic.SistemasActivosFijosUAP.model.dao.IOficinaDao;
import com.usic.SistemasActivosFijosUAP.model.dao.IPredioDao;
import com.usic.SistemasActivosFijosUAP.model.dao.IResposableDao;
import com.usic.SistemasActivosFijosUAP.model.entity.Activo;
import com.usic.SistemasActivosFijosUAP.model.entity.Auxiliar;
import com.usic.SistemasActivosFijosUAP.model.entity.MapaEspacial;
import com.usic.SistemasActivosFijosUAP.model.entity.MapaEspacialIconoAuxiliar;
import com.usic.SistemasActivosFijosUAP.model.entity.MapaEspacialPublicacion;
import com.usic.SistemasActivosFijosUAP.model.entity.Oficina;
import com.usic.SistemasActivosFijosUAP.model.entity.Predio;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

/** Primer editor espacial: datos geométricos separados del catálogo VSIAF. */
@Controller
@RequestMapping("/administracion/mapa-espacial")
@RequiredArgsConstructor
public class MapaEspacialController {
    private final IMapaEspacialDao mapas;
    private final IMapaEspacialPublicacionDao publicaciones;
    private final SseEmitterRegistry eventos;
    private final IPredioDao predios;
    private final IOficinaDao oficinas;
    private final IActivoDao activos;
    private final IResposableDao responsables;
    private final IAuxiliarDao auxiliares;
    private final IMapaEspacialIconoAuxiliarDao iconosAuxiliares;
    private final ObjectMapper json;
    private static final Path FONDOS = Path.of("uploads", "mapa-espacial");
    private static final Path PLANOS = FONDOS.resolve("planos");
    private static final Path ICONOS = FONDOS.resolve("iconos");

    private void exigirSesion(HttpServletRequest request) {
        if (RolesSciaf.usuarioDe(request) == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    }

    private boolean puedeEditar(HttpServletRequest request) {
        if (RolesSciaf.esAdministrativo(request)) return true;
        Object opciones = request.getSession(false) == null ? null : request.getSession(false).getAttribute("opciones");
        return opciones instanceof Set<?> set && set.contains("opcion_mapa_diseno");
    }

    private boolean puedePublicar(HttpServletRequest request) {
        if (RolesSciaf.esAdministrador(RolesSciaf.usuarioDe(request))) return true;
        Object opciones = request.getSession(false) == null ? null : request.getSession(false).getAttribute("opciones");
        return opciones instanceof Set<?> set && set.contains("opcion_mapa_publicar");
    }

    private void exigirPublicacion(HttpServletRequest request) {
        exigirEdicion(request);
        if (!puedePublicar(request)) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }

    private String usuario(HttpServletRequest request) {
        String nombre = RolesSciaf.usuarioDe(request).getUsuario();
        return nombre == null ? "Usuario" : nombre.substring(0, Math.min(120, nombre.length()));
    }

    private String escenaPublicada(MapaEspacial mapa) {
        if (mapa == null || Boolean.FALSE.equals(mapa.getPublicada())) return "{\"edificios\":[]}";
        return mapa.getPublicada() == null ? mapa.getEscena() : mapa.getEscenaPublicada();
    }

    private Path fondoBorrador(Long predioId) { return FONDOS.resolve(predioId + ".jpg"); }

    private Path fondoPublicado(MapaEspacial mapa, Long predioId) {
        if (mapa == null || Boolean.FALSE.equals(mapa.getPublicada())) return null;
        if (mapa.getPublicada() == null) return fondoBorrador(predioId);
        return mapa.getFondoPublicado() == null ? null : FONDOS.resolve(mapa.getFondoPublicado());
    }

    private String copiarFondoVersion(Long predioId, long numero) {
        Path origen = fondoBorrador(predioId);
        if (!Files.isRegularFile(origen)) return null;
        String nombre = "versiones/" + predioId + "-" + numero + "-" + UUID.randomUUID() + ".jpg";
        try {
            Path destino = FONDOS.resolve(nombre);
            Files.createDirectories(destino.getParent());
            Files.copy(origen, destino);
            return nombre;
        } catch (IOException e) { throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo conservar el plano de fondo"); }
    }

    private void registrarPublicacion(MapaEspacial mapa, long numero, String escena, String fondo,
            LocalDateTime fecha, String actor) {
        MapaEspacialPublicacion registro = new MapaEspacialPublicacion();
        registro.setPredio(mapa.getPredio()); registro.setNumero(numero); registro.setEscena(escena);
        registro.setFondoArchivo(fondo); registro.setFecha(fecha); registro.setUsuario(actor);
        publicaciones.save(registro);
    }

    /** Conserva el mapa ya visible al modificar por primera vez una fila creada antes de esta fase. */
    private void conservarLegado(MapaEspacial mapa, Long predioId) {
        if (mapa == null || mapa.getPublicada() != null) return;
        String fondo = copiarFondoVersion(predioId, 1);
        LocalDateTime fecha = LocalDateTime.now();
        mapa.setPublicada(true); mapa.setEscenaPublicada(mapa.getEscena()); mapa.setNumeroPublicacion(1L);
        mapa.setFondoPublicado(fondo); mapa.setFechaPublicacion(fecha); mapa.setPublicadoPor("Migración del plano anterior");
        registrarPublicacion(mapa, 1, mapa.getEscena(), fondo, fecha, "Migración del plano anterior");
    }

    private void exigirEdicion(HttpServletRequest request) {
        exigirSesion(request);
        if (!puedeEditar(request)) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }

    @GetMapping("/diseno")
    public String diseno(Model model, HttpServletRequest request) {
        exigirEdicion(request);
        return "mapaEspacial/diseno";
    }

    @GetMapping("/explorar")
    public String explorar(HttpServletRequest request) {
        exigirSesion(request);
        return "mapaEspacial/explorar";
    }

    @GetMapping("/api/predios") @ResponseBody
    @Transactional(readOnly = true)
    public List<Map<String, Object>> predios(HttpServletRequest request) {
        exigirSesion(request);
        return predios.listarPredios().stream()
                .sorted(java.util.Comparator.comparing(Predio::getDescrip))
                .map(p -> Map.<String, Object>of("id", p.getIdPredio(), "nombre", p.getDescrip(), "unidad", p.getUnidad()))
                .toList();
    }

    @GetMapping("/api/predios/{predioId}") @ResponseBody
    @Transactional(readOnly = true)
    public Map<String, Object> mapa(@PathVariable Long predioId, HttpServletRequest request) {
        exigirEdicion(request);
        if (!predios.existsById(predioId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        MapaEspacial mapa = mapas.findByPredioIdPredio(predioId).orElse(null);
        JsonNode escena = leer(mapa == null ? "{\"edificios\":[]}" : mapa.getEscena());
        Map<String, Object> respuesta = datosEscena(escena, mapa == null ? -1L : mapa.getVersion(),
                Files.isRegularFile(fondoBorrador(predioId)));
        respuesta.put("puedePublicar", puedePublicar(request));
        respuesta.put("numeroPublicacion", mapa == null || Boolean.FALSE.equals(mapa.getPublicada()) ? 0L :
                mapa.getPublicada() == null ? 1L : mapa.getNumeroPublicacion());
        return respuesta;
    }

    @GetMapping("/api/predios/{predioId}/resumen") @ResponseBody
    @Transactional(readOnly = true)
    public Map<String, Object> resumen(@PathVariable Long predioId, HttpServletRequest request) {
        exigirSesion(request);
        if (!predios.existsById(predioId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        MapaEspacial mapa = mapas.findByPredioIdPredio(predioId).orElse(null);
        ObjectNode escena = (ObjectNode) leer(escenaPublicada(mapa)).deepCopy();
        for (JsonNode edificio : escena.path("edificios")) {
            Set<Long> oficinasVinculadas = new HashSet<>();
            for (JsonNode piso : edificio.path("pisos")) {
                int espacios = 0;
                for (JsonNode espacio : piso.path("espacios")) {
                    espacios++;
                    if (espacio.path("oficinaId").canConvertToLong()) oficinasVinculadas.add(espacio.path("oficinaId").asLong());
                }
                ((ObjectNode) piso).put("resumenEspacios", espacios);
                ((ObjectNode) piso).set("espacios", json.createArrayNode());
                ((ObjectNode) piso).remove("trazos");
            }
            ((ObjectNode) edificio).put("resumenOficinas", oficinasVinculadas.size());
        }
        Path fondo = fondoPublicado(mapa, predioId);
        return Map.of("escena", escena, "version", mapa == null ? -1L : mapa.getVersion(),
                "tieneFondo", fondo != null && Files.isRegularFile(fondo),
                "numeroPublicacion", mapa == null || Boolean.FALSE.equals(mapa.getPublicada()) ? 0L :
                        mapa.getPublicada() == null ? 1L : mapa.getNumeroPublicacion());
    }

    @GetMapping("/api/predios/{predioId}/edificios/{edificioId}") @ResponseBody
    @Transactional(readOnly = true)
    public Map<String, Object> detalleEdificio(@PathVariable Long predioId, @PathVariable String edificioId,
            HttpServletRequest request) {
        exigirSesion(request);
        if (!predios.existsById(predioId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        MapaEspacial mapa = mapas.findByPredioIdPredio(predioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        JsonNode edificio = null;
        for (JsonNode actual : leer(escenaPublicada(mapa)).path("edificios"))
            if (edificioId.equals(actual.path("id").asText())) { edificio = actual; break; }
        if (edificio == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        ObjectNode escena = json.createObjectNode();
        ArrayNode lista = escena.putArray("edificios"); lista.add(edificio);
        Path fondo = fondoPublicado(mapa, predioId);
        Map<String, Object> respuesta = datosEscena(escena, mapa.getVersion(), fondo != null && Files.isRegularFile(fondo));
        respuesta.remove("escena"); respuesta.put("edificio", edificio);
        return respuesta;
    }

    @GetMapping("/api/predios/{predioId}/edificios/{edificioId}/pisos/{pisoId}") @ResponseBody
    @Transactional(readOnly = true)
    public Map<String, Object> detallePiso(@PathVariable Long predioId, @PathVariable String edificioId,
            @PathVariable String pisoId, HttpServletRequest request) {
        exigirSesion(request);
        MapaEspacial mapa = mapas.findByPredioIdPredio(predioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        for (JsonNode edificio : leer(escenaPublicada(mapa)).path("edificios")) {
            if (!edificioId.equals(edificio.path("id").asText())) continue;
            for (JsonNode piso : edificio.path("pisos")) {
                if (!pisoId.equals(piso.path("id").asText())) continue;
                ObjectNode edificioReducido = ((ObjectNode) edificio).deepCopy();
                ArrayNode pisos = json.createArrayNode(); pisos.add(piso);
                edificioReducido.set("pisos", pisos);
                ObjectNode escena = json.createObjectNode(); escena.putArray("edificios").add(edificioReducido);
                Path fondo = fondoPublicado(mapa, predioId);
                Map<String, Object> respuesta = datosEscena(escena, mapa.getVersion(),
                        fondo != null && Files.isRegularFile(fondo));
                respuesta.remove("escena"); respuesta.put("piso", piso);
                return respuesta;
            }
        }
        throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }

    private Map<String, Object> datosEscena(JsonNode escena, long version, boolean tieneFondo) {
        Set<Long> idsOficina = new HashSet<>(), idsActivo = new HashSet<>();
        for (JsonNode edificio : escena.path("edificios"))
            for (JsonNode piso : edificio.path("pisos"))
                for (JsonNode espacio : piso.path("espacios")) {
                    if (espacio.path("oficinaId").canConvertToLong()) idsOficina.add(espacio.path("oficinaId").asLong());
                    for (JsonNode a : espacio.path("activos"))
                        if (a.path("activoId").canConvertToLong()) idsActivo.add(a.path("activoId").asLong());
                }
        Map<Long, Map<String, Object>> nombresOficina = new HashMap<>();
        for (Oficina o : oficinas.findAllById(idsOficina))
            nombresOficina.put(o.getIdOficina(), Map.of("codigo", o.getCodOfi(), "nombre", o.getNombre()));
        Map<Long, Map<String, Object>> nombresActivo = new HashMap<>();
        for (Activo a : activos.findAllById(idsActivo)) {
            Map<String, Object> dato = new HashMap<>();
            dato.put("codigo", a.getCodigo()); dato.put("descripcion", a.getDescripcion());
            dato.put("oficinaId", a.getOficina() == null ? null : a.getOficina().getIdOficina());
            dato.put("auxiliarId", a.getAuxiliar() == null ? null : a.getAuxiliar().getIdAuxiliar());
            dato.put("auxiliarNombre", a.getAuxiliar() == null ? null : a.getAuxiliar().getNombre());
            nombresActivo.put(a.getIdActivo(), dato);
        }
        Map<String, Object> respuesta = new HashMap<>();
        respuesta.put("escena", escena);
        respuesta.put("version", version);
        respuesta.put("oficinas", nombresOficina);
        respuesta.put("activos", nombresActivo);
        respuesta.put("tieneFondo", tieneFondo);
        return respuesta;
    }

    @GetMapping(value = "/api/predios/{predioId}/fondo", produces = MediaType.IMAGE_JPEG_VALUE)
    @ResponseBody
    public ResponseEntity<byte[]> verFondo(@PathVariable Long predioId, HttpServletRequest request) {
        exigirEdicion(request);
        return responderFondo(fondoBorrador(predioId), false);
    }

    @GetMapping(value = "/api/predios/{predioId}/fondo/publicado", produces = MediaType.IMAGE_JPEG_VALUE)
    @ResponseBody
    public ResponseEntity<byte[]> verFondoPublicado(@PathVariable Long predioId, HttpServletRequest request) {
        exigirSesion(request);
        MapaEspacial mapa = mapas.findByPredioIdPredio(predioId).orElse(null);
        return responderFondo(fondoPublicado(mapa, predioId), true);
    }

    private ResponseEntity<byte[]> responderFondo(Path ruta, boolean publicado) {
        try {
            if (ruta == null || !Files.isRegularFile(ruta)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG)
                    .cacheControl(publicado ? CacheControl.maxAge(Duration.ofDays(7)).cachePrivate() :
                            CacheControl.noCache().cachePrivate()).body(Files.readAllBytes(ruta));
        } catch (IOException e) { throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo leer el fondo"); }
    }

    @GetMapping(value = "/api/predios/{predioId}/planos/{archivo}", produces = MediaType.IMAGE_JPEG_VALUE)
    @ResponseBody
    public ResponseEntity<byte[]> verPlano(@PathVariable Long predioId, @PathVariable String archivo,
            HttpServletRequest request) {
        exigirSesion(request);
        if (!archivo.matches("[0-9a-fA-F-]{36}")) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return responderFondo(PLANOS.resolve(predioId.toString()).resolve(archivo + ".jpg"), true);
    }

    @PostMapping(value = "/api/predios/{predioId}/planos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseBody
    public Map<String, Object> subirPlano(@PathVariable Long predioId,
            @RequestParam("archivo") MultipartFile archivo, HttpServletRequest request) {
        exigirEdicion(request);
        if (!predios.existsById(predioId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        if (archivo.isEmpty() || archivo.getSize() > 20_000_000)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Usa PNG o JPG de hasta 20 MB");
        String id = UUID.randomUUID().toString();
        Path destino = PLANOS.resolve(predioId.toString()).resolve(id + ".jpg");
        try (ImageInputStream input = ImageIO.createImageInputStream(archivo.getInputStream())) {
            if (input == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Imagen inválida");
            var lectores = ImageIO.getImageReaders(input);
            if (!lectores.hasNext()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Formato de imagen no admitido");
            ImageReader lector = lectores.next();
            try {
                if (!Set.of("JPEG", "PNG").contains(lector.getFormatName().toUpperCase(Locale.ROOT)))
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Solo PNG o JPG");
                lector.setInput(input, true, true);
                int ancho = lector.getWidth(0), alto = lector.getHeight(0);
                if (ancho < 10 || alto < 10 || (long) ancho * alto > 50_000_000)
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dimensiones de imagen no admitidas");
                int paso = Math.max(1, (int) Math.ceil(Math.max(ancho, alto) / 3200.0));
                ImageReadParam lectura = lector.getDefaultReadParam();
                lectura.setSourceSubsampling(paso, paso, 0, 0);
                BufferedImage origen = lector.read(0, lectura);
                BufferedImage rgb = new BufferedImage(origen.getWidth(), origen.getHeight(), BufferedImage.TYPE_INT_RGB);
                Graphics2D g = rgb.createGraphics();
                try { g.setColor(Color.WHITE); g.fillRect(0, 0, rgb.getWidth(), rgb.getHeight()); g.drawImage(origen, 0, 0, null); }
                finally { g.dispose(); }
                Files.createDirectories(destino.getParent());
                ImageWriter escritor = ImageIO.getImageWritersByFormatName("jpg").next();
                try (ImageOutputStream salida = ImageIO.createImageOutputStream(destino.toFile())) {
                    escritor.setOutput(salida);
                    ImageWriteParam calidad = escritor.getDefaultWriteParam();
                    calidad.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                    calidad.setCompressionQuality(.88f);
                    escritor.write(null, new javax.imageio.IIOImage(rgb, null, null), calidad);
                } finally { escritor.dispose(); }
                return Map.of("archivo", id, "ancho", rgb.getWidth(), "alto", rgb.getHeight());
            } finally { lector.dispose(); }
        } catch (IOException e) {
            try { Files.deleteIfExists(destino); } catch (IOException ignored) { }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No se pudo procesar la imagen");
        }
    }

    @PostMapping(value = "/api/predios/{predioId}/fondo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseBody
    @Transactional
    public Map<String, Object> subirFondo(@PathVariable Long predioId, @RequestParam("archivo") MultipartFile archivo,
            @RequestParam(value = "version", required = false) Long version,
            HttpServletRequest request) {
        exigirEdicion(request);
        if (!predios.existsById(predioId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        if (archivo.isEmpty() || archivo.getSize() > 20_000_000)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Usa una imagen PNG o JPG de hasta 20 MB");
        try (ImageInputStream input = ImageIO.createImageInputStream(archivo.getInputStream())) {
            if (input == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Imagen inválida");
            var lectores = ImageIO.getImageReaders(input);
            if (!lectores.hasNext()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Formato de imagen no admitido");
            ImageReader lector = lectores.next();
            try {
                if (!Set.of("JPEG", "PNG").contains(lector.getFormatName().toUpperCase()))
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Solo PNG o JPG");
                lector.setInput(input, true, true);
                int ancho = lector.getWidth(0), alto = lector.getHeight(0);
                if (ancho < 10 || alto < 10 || (long) ancho * alto > 50_000_000)
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dimensiones de imagen no admitidas");
                int ladoMayor = Math.max(ancho, alto);
                int paso = Math.max(1, ladoMayor / 3200);
                ImageReadParam parametro = lector.getDefaultReadParam();
                parametro.setSourceSubsampling(paso, paso, 0, 0);
                BufferedImage origen = lector.read(0, parametro);
                double escala = Math.min(1, 3200.0 / Math.max(origen.getWidth(), origen.getHeight()));
                int salidaAncho = Math.max(1, (int) Math.round(ancho * escala / paso));
                int salidaAlto = Math.max(1, (int) Math.round(alto * escala / paso));
                BufferedImage rgb = new BufferedImage(salidaAncho, salidaAlto, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = rgb.createGraphics();
                try {
                    g.setColor(Color.WHITE); g.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
                    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                    g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                    g.drawImage(origen, 0, 0, rgb.getWidth(), rgb.getHeight(), null);
                }
                finally { g.dispose(); }
                Files.createDirectories(FONDOS);
                Path temporal = Files.createTempFile(FONDOS, "fondo-", ".jpg");
                try {
                    ImageWriter escritor = ImageIO.getImageWritersByFormatName("jpg").next();
                    try (ImageOutputStream salida = ImageIO.createImageOutputStream(temporal.toFile())) {
                        escritor.setOutput(salida);
                        ImageWriteParam calidad = escritor.getDefaultWriteParam();
                        calidad.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                        calidad.setCompressionQuality(.91f);
                        escritor.write(null, new javax.imageio.IIOImage(rgb, null, null), calidad);
                    } finally { escritor.dispose(); }
                    MapaEspacial mapa = mapas.findByPredioIdPredio(predioId).orElse(null);
                    if ((mapa == null && version != null && version != -1L) ||
                            (mapa != null && version != null && !version.equals(mapa.getVersion())))
                        throw new ResponseStatusException(HttpStatus.CONFLICT, "El mapa cambió. Recarga antes de subir el fondo.");
                    if (mapa != null) {
                        conservarLegado(mapa, predioId);
                        mapa.setFechaModificacion(LocalDateTime.now()); mapa.setModificadoPor(usuario(request));
                        mapas.saveAndFlush(mapa);
                    }
                    Files.move(temporal, fondoBorrador(predioId), StandardCopyOption.REPLACE_EXISTING);
                } finally { Files.deleteIfExists(temporal); }
                MapaEspacial mapaActual = mapas.findByPredioIdPredio(predioId).orElse(null);
                return Map.of("ancho", rgb.getWidth(), "alto", rgb.getHeight(),
                        "bytes", Files.size(fondoBorrador(predioId)),
                        "version", mapaActual == null ? -1L : mapaActual.getVersion());
            } finally { lector.dispose(); }
        } catch (IOException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No se pudo procesar la imagen"); }
    }

    @GetMapping("/api/predios/{predioId}/oficinas") @ResponseBody
    @Transactional(readOnly = true)
    public List<IOficinaDao.OficinaOpcion> buscarOficinas(@PathVariable Long predioId,
            @RequestParam(defaultValue = "") String q, HttpServletRequest request) {
        exigirSesion(request);
        return oficinas.opcionesDePredio(predioId, termino(q), PageRequest.of(0, 30));
    }

    public record UbicacionMapa(String tipo, String edificioId, String edificioNombre,
            String pisoId, String pisoNombre, String espacioId, String etiqueta) {}

    public record ResultadoBusquedaMapa(String tipo, String etiqueta, String detalle, String oficinaNombre,
            Long oficinaId, Long activoId, Long responsableId, Long personaId,
            UbicacionMapa lugar, boolean figuraUbicada) {}

    @GetMapping("/api/predios/{predioId}/localizar") @ResponseBody
    @Transactional(readOnly = true)
    public List<ResultadoBusquedaMapa> localizar(@PathVariable Long predioId,
            @RequestParam String tipo, @RequestParam(defaultValue = "") String q,
            @RequestParam(required = false) Long personaId,
            HttpServletRequest request) {
        exigirSesion(request);
        String filtro = termino(q);
        if (filtro.length() < 2 && personaId == null) return List.of();
        if (!Set.of("ACTIVO", "RESPONSABLE").contains(tipo))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tipo de búsqueda inválido");
        if (personaId != null && (!"RESPONSABLE".equals(tipo) || personaId < 1))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Persona inválida");
        if (!predios.existsById(predioId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        MapaEspacial mapa = mapas.findByPredioIdPredio(predioId).orElse(null);
        Map<Long, List<UbicacionMapa>> lugares = new HashMap<>();
        Map<String, Set<Long>> figuras = new HashMap<>();
        if (mapa != null) for (JsonNode edificio : leer(escenaPublicada(mapa)).path("edificios"))
            for (JsonNode piso : edificio.path("pisos"))
                for (JsonNode espacio : piso.path("espacios")) {
                    if (!espacio.path("oficinaId").canConvertToLong()) continue;
                    long oficinaId = espacio.path("oficinaId").asLong();
                    UbicacionMapa lugar = new UbicacionMapa("OFICINA", edificio.path("id").asText(),
                            edificio.path("nombre").asText(), piso.path("id").asText(),
                            piso.path("nombre").asText(), espacio.path("id").asText(),
                            espacio.path("nombre").asText());
                    lugares.computeIfAbsent(oficinaId, k -> new ArrayList<>()).add(lugar);
                    Set<Long> colocados = new HashSet<>();
                    for (JsonNode figura : espacio.path("activos"))
                        if (figura.path("activoId").canConvertToLong()) colocados.add(figura.path("activoId").asLong());
                    figuras.put(lugar.edificioId() + ":" + lugar.pisoId() + ":" + lugar.espacioId(), colocados);
                }
        List<ResultadoBusquedaMapa> respuesta = new ArrayList<>();
        if ("ACTIVO".equals(tipo)) {
            List<IActivoDao.ActivoBusquedaMapa> hallados = activos.buscarEnMapa(predioId, filtro, PageRequest.of(0, 20));
            Set<Long> ids = new HashSet<>();
            for (IActivoDao.ActivoBusquedaMapa a : hallados) if (a.getOficinaId() != null) ids.add(a.getOficinaId());
            Map<Long, String> nombres = nombresOficinasMapa(ids);
            for (IActivoDao.ActivoBusquedaMapa activo : hallados) {
                List<UbicacionMapa> destinos = lugares.getOrDefault(activo.getOficinaId(), List.of());
                List<UbicacionMapa> ubicados = destinos.stream().filter(l ->
                        figuras.getOrDefault(l.edificioId() + ":" + l.pisoId() + ":" + l.espacioId(), Set.of())
                                .contains(activo.getId())).toList();
                if (!ubicados.isEmpty()) destinos = ubicados;
                if (destinos.isEmpty()) destinos = java.util.Collections.singletonList(null);
                for (UbicacionMapa lugar : destinos) {
                    boolean dibujado = lugar != null && !ubicados.isEmpty();
                    respuesta.add(new ResultadoBusquedaMapa(tipo, activo.getCodigo() + " · " + activo.getDescripcion(),
                            dibujado ? "Activo ubicado en el plano" : lugar == null ? "Oficina sin plano publicado" : "Activo sin posición dibujada en esta oficina",
                            nombres.getOrDefault(activo.getOficinaId(), "Oficina sin nombre"),
                            activo.getOficinaId(), activo.getId(), null, null, lugar, dibujado));
                    if (respuesta.size() >= 50) return respuesta;
                }
            }
        } else {
            List<IResposableDao.ResponsableBusquedaMapa> hallados = personaId == null ?
                    responsables.buscarEnMapa(predioId, filtro, PageRequest.of(0, 30)) :
                    responsables.oficinasDePersonaEnMapa(predioId, personaId);
            Set<Long> ids = new HashSet<>();
            for (IResposableDao.ResponsableBusquedaMapa r : hallados) if (r.getOficinaId() != null) ids.add(r.getOficinaId());
            Map<Long, String> nombres = nombresOficinasMapa(ids);
            for (IResposableDao.ResponsableBusquedaMapa responsable : hallados) {
                List<UbicacionMapa> destinos = lugares.getOrDefault(responsable.getOficinaId(), List.of());
                if (destinos.isEmpty()) destinos = java.util.Collections.singletonList(null);
                for (UbicacionMapa lugar : destinos) {
                    respuesta.add(new ResultadoBusquedaMapa(tipo,
                            responsable.getNombre() == null || responsable.getNombre().isBlank() ? responsable.getCodigo() : responsable.getNombre(),
                            lugar == null ? "Oficina sin plano publicado" : "Responsable asignado a esta oficina",
                            nombres.getOrDefault(responsable.getOficinaId(), "Oficina sin nombre"),
                            responsable.getOficinaId(), null, responsable.getId(), responsable.getPersonaId(), lugar, false));
                    if (respuesta.size() >= (personaId == null ? 50 : 200)) return respuesta;
                }
            }
        }
        return respuesta;
    }

    private Map<Long, String> nombresOficinasMapa(Set<Long> ids) {
        Map<Long, String> nombres = new HashMap<>();
        if (ids.isEmpty()) return nombres;
        for (Oficina oficina : oficinas.findAllById(ids))
            nombres.put(oficina.getIdOficina(),
                    (oficina.getCodOfi() == null ? "" : oficina.getCodOfi() + " · ") + oficina.getNombre());
        return nombres;
    }

    @GetMapping("/api/predios/{predioId}/ubicaciones") @ResponseBody
    @Transactional(readOnly = true)
    public List<UbicacionMapa> buscarUbicaciones(@PathVariable Long predioId,
            @RequestParam(defaultValue = "") String q, HttpServletRequest request) {
        exigirSesion(request);
        String filtro = normalizarBusqueda(termino(q));
        if (filtro.isBlank()) return List.of();
        if (!predios.existsById(predioId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        MapaEspacial mapa = mapas.findByPredioIdPredio(predioId).orElse(null);
        if (mapa == null) return List.of();
        JsonNode edificios = leer(escenaPublicada(mapa)).path("edificios");
        Set<Long> idsOficina = new HashSet<>();
        for (JsonNode edificio : edificios)
            for (JsonNode piso : edificio.path("pisos"))
                for (JsonNode espacio : piso.path("espacios"))
                    if (espacio.path("oficinaId").canConvertToLong()) idsOficina.add(espacio.path("oficinaId").asLong());
        Map<Long, Oficina> catalogo = new HashMap<>();
        if (!idsOficina.isEmpty()) for (Oficina oficina : oficinas.findAllById(idsOficina))
            if (oficina.getPredio() != null && predioId.equals(oficina.getPredio().getIdPredio()))
                catalogo.put(oficina.getIdOficina(), oficina);
        List<UbicacionMapa> respuesta = new ArrayList<>();
        for (JsonNode edificio : edificios) {
            String edificioId = edificio.path("id").asText(), edificioNombre = edificio.path("nombre").asText();
            if (normalizarBusqueda(edificioNombre).contains(filtro))
                respuesta.add(new UbicacionMapa("EDIFICIO", edificioId, edificioNombre, null, null, null, edificioNombre));
            for (JsonNode piso : edificio.path("pisos"))
                for (JsonNode espacio : piso.path("espacios")) {
                    if (!espacio.path("oficinaId").canConvertToLong()) continue;
                    Oficina oficina = catalogo.get(espacio.path("oficinaId").asLong());
                    if (oficina == null) continue;
                    String etiqueta = oficina.getCodOfi() + " · " + oficina.getNombre();
                    if (!normalizarBusqueda(etiqueta + " " + espacio.path("nombre").asText()).contains(filtro)) continue;
                    respuesta.add(new UbicacionMapa("OFICINA", edificioId, edificioNombre,
                            piso.path("id").asText(), piso.path("nombre").asText(), espacio.path("id").asText(), etiqueta));
                    if (respuesta.size() >= 25) return respuesta;
                }
            if (respuesta.size() >= 25) return respuesta;
        }
        return respuesta;
    }

    private String normalizarBusqueda(String valor) {
        return Normalizer.normalize(valor == null ? "" : valor, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").toUpperCase(Locale.ROOT);
    }

    public record ActivoInventarioItem(Long id, String codigo, String descripcion, String responsable,
            String estadoFisico, Long auxiliarId, String auxiliarNombre) {}
    public record PaginaInventario(long total, int pagina, int totalPaginas, List<ActivoInventarioItem> items) {}

    @GetMapping("/api/oficinas/{oficinaId}/activos") @ResponseBody
    @Transactional(readOnly = true)
    public PaginaInventario buscarActivos(@PathVariable Long oficinaId,
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "0") int pagina, HttpServletRequest request) {
        exigirSesion(request);
        if (pagina < 0 || pagina > 10000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Página inválida");
        Page<IActivoDao.ActivoInventarioMapa> resultado = activos.inventarioMapa(oficinaId, termino(q), PageRequest.of(pagina, 10));
        return new PaginaInventario(resultado.getTotalElements(), resultado.getNumber(), resultado.getTotalPages(),
                resultado.getContent().stream().map(a -> new ActivoInventarioItem(
                        a.getId(), a.getCodigo(), a.getDescripcion(), a.getResponsable(), a.getEstadoFisico(),
                        a.getAuxiliarId(), a.getAuxiliarNombre())).toList());
    }

    private static final Set<String> ICONOS_MAPA = Set.of("CPU", "MONITOR", "MESA", "SILLA", "ROUTER",
            "AIRE", "CAMARA", "IMPRESORA", "PORTATIL", "PROYECTOR", "ESTANTE", "ARCHIVADOR",
            "BIOMETRICO", "TELEFONO", "ESCANER", "UPS", "TELEVISOR", "PIZARRA", "VENTILADOR", "EXTINTOR", "OTRO");

    @GetMapping("/api/predios/{predioId}/iconos-auxiliares") @ResponseBody
    @Transactional(readOnly = true)
    public Map<Long, String> iconosAuxiliares(@PathVariable Long predioId, HttpServletRequest request) {
        exigirSesion(request);
        if (!predios.existsById(predioId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        Map<Long, String> resultado = new HashMap<>();
        // Solo se envían preferencias del predio abierto.
        for (IMapaEspacialIconoAuxiliarDao.IconoConfigurado icono : iconosAuxiliares.listarPorPredio(predioId))
            resultado.put(icono.getAuxiliarId(), icono.getIcono());
        return resultado;
    }

    public record IconoAuxiliarRequest(String icono) {}

    @PutMapping("/api/predios/{predioId}/auxiliares/{auxiliarId}/icono") @ResponseBody
    @Transactional
    public Map<String, Object> configurarIconoAuxiliar(@PathVariable Long predioId, @PathVariable Long auxiliarId,
            @RequestBody IconoAuxiliarRequest body, HttpServletRequest request) {
        exigirEdicion(request);
        if (body == null || body.icono() == null || !ICONOS_MAPA.contains(body.icono()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Icono inválido");
        Auxiliar auxiliar = auxiliares.findById(auxiliarId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!predioId.equals(auxiliar.getPredio().getIdPredio()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El auxiliar pertenece a otro predio");
        MapaEspacialIconoAuxiliar config = iconosAuxiliares.findByAuxiliarIdAuxiliar(auxiliarId)
                .orElseGet(MapaEspacialIconoAuxiliar::new);
        config.setAuxiliar(auxiliar);
        config.setIcono(body.icono());
        iconosAuxiliares.save(config);
        return Map.of("auxiliarId", auxiliarId, "icono", config.getIcono());
    }

    private Path archivoIcono(Long predioId, Long auxiliarId) {
        return ICONOS.resolve(predioId.toString()).resolve(auxiliarId + ".png");
    }

    @PostMapping(value = "/api/predios/{predioId}/auxiliares/{auxiliarId}/icono-imagen",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE) @ResponseBody
    @Transactional
    public Map<String, Object> subirIconoAuxiliar(@PathVariable Long predioId, @PathVariable Long auxiliarId,
            @RequestParam("archivo") MultipartFile archivo, HttpServletRequest request) {
        exigirEdicion(request);
        Auxiliar auxiliar = auxiliares.findById(auxiliarId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!predioId.equals(auxiliar.getPredio().getIdPredio()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El auxiliar pertenece a otro predio");
        if (archivo == null || archivo.isEmpty() || archivo.getSize() > 150_000)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Usa una imagen PNG o JPG de hasta 150 KB");
        Path destino = archivoIcono(predioId, auxiliarId), temporal = null;
        try (ImageInputStream entrada = ImageIO.createImageInputStream(archivo.getInputStream())) {
            if (entrada == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Imagen inválida");
            var lectores = ImageIO.getImageReaders(entrada);
            if (!lectores.hasNext()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Imagen inválida");
            ImageReader lector = lectores.next();
            try {
                String formato = lector.getFormatName();
                lector.setInput(entrada, true, true);
                int ancho = lector.getWidth(0), alto = lector.getHeight(0);
                if (!("png".equalsIgnoreCase(formato) || "jpeg".equalsIgnoreCase(formato) || "jpg".equalsIgnoreCase(formato))
                        || ancho < 1 || alto < 1 || ancho > 2048 || alto > 2048)
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Usa PNG o JPG de hasta 2048 píxeles por lado");
                BufferedImage origen = lector.read(0);
                BufferedImage reducido = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = reducido.createGraphics();
                try {
                    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                    double escala = Math.min(64.0 / ancho, 64.0 / alto);
                    int w = Math.max(1, (int) Math.round(ancho * escala)), h = Math.max(1, (int) Math.round(alto * escala));
                    g.drawImage(origen, (64 - w) / 2, (64 - h) / 2, w, h, null);
                } finally { g.dispose(); }
                Files.createDirectories(destino.getParent());
                temporal = Files.createTempFile(destino.getParent(), "icono-", ".png");
                if (!ImageIO.write(reducido, "png", temporal.toFile())) throw new IOException("No se pudo codificar el icono");
                Files.move(temporal, destino, StandardCopyOption.REPLACE_EXISTING);
            } finally { lector.dispose(); }
        } catch (ResponseStatusException e) { throw e; }
        catch (IOException e) { throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo guardar el icono"); }
        finally { if (temporal != null) try { Files.deleteIfExists(temporal); } catch (IOException ignored) { } }
        MapaEspacialIconoAuxiliar config = iconosAuxiliares.findByAuxiliarIdAuxiliar(auxiliarId)
                .orElseGet(MapaEspacialIconoAuxiliar::new);
        config.setAuxiliar(auxiliar); config.setIcono("PERSONALIZADO"); iconosAuxiliares.save(config);
        return Map.of("auxiliarId", auxiliarId, "icono", "PERSONALIZADO");
    }

    @GetMapping(value = "/api/predios/{predioId}/auxiliares/{auxiliarId}/icono-imagen",
            produces = MediaType.IMAGE_PNG_VALUE) @ResponseBody
    public ResponseEntity<byte[]> obtenerIconoAuxiliar(@PathVariable Long predioId, @PathVariable Long auxiliarId,
            HttpServletRequest request) {
        exigirSesion(request);
        Auxiliar auxiliar = auxiliares.findById(auxiliarId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!predioId.equals(auxiliar.getPredio().getIdPredio())) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        try {
            Path archivo = archivoIcono(predioId, auxiliarId);
            if (!Files.isRegularFile(archivo)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofDays(1))).contentType(MediaType.IMAGE_PNG)
                    .body(Files.readAllBytes(archivo));
        } catch (IOException e) { throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo leer el icono"); }
    }

    @GetMapping("/api/activos/{activoId}/detalle") @ResponseBody
    @Transactional(readOnly = true)
    public IActivoDao.ActivoDetalleMapa detalleActivo(@PathVariable Long activoId, HttpServletRequest request) {
        exigirSesion(request);
        return activos.detalleMapa(activoId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @PutMapping("/api/predios/{predioId}") @ResponseBody
    @Transactional
    public Map<String, Object> guardar(@PathVariable Long predioId, @RequestBody JsonNode body,
            HttpServletRequest request) {
        exigirEdicion(request);
        Predio predio = predios.findById(predioId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        JsonNode escena = body.path("escena");
        validar(escena, predioId);
        MapaEspacial mapa = mapas.findByPredioIdPredio(predioId).orElse(null);
        long versionRecibida = body.path("version").asLong(Long.MIN_VALUE);
        if (mapa == null) {
            if (versionRecibida != -1) throw new ResponseStatusException(HttpStatus.CONFLICT, "El mapa cambió. Recarga antes de guardar.");
            mapa = new MapaEspacial(); mapa.setPredio(predio); mapa.setPublicada(false); mapa.setNumeroPublicacion(0L);
        } else if (versionRecibida != mapa.getVersion()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El mapa cambió. Recarga antes de guardar.");
        }
        conservarLegado(mapa, predioId);
        try { mapa.setEscena(json.writeValueAsString(escena)); }
        catch (JsonProcessingException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Plano inválido"); }
        mapa.setFechaModificacion(LocalDateTime.now()); mapa.setModificadoPor(usuario(request));
        mapas.saveAndFlush(mapa);
        return Map.of("version", mapa.getVersion());
    }

    public record PublicacionItem(long numero, LocalDateTime fecha, String usuario, boolean actual) {}

    @GetMapping("/api/predios/{predioId}/publicaciones") @ResponseBody
    @Transactional(readOnly = true)
    public List<PublicacionItem> listarPublicaciones(@PathVariable Long predioId, HttpServletRequest request) {
        exigirEdicion(request);
        if (!predios.existsById(predioId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        MapaEspacial mapa = mapas.findByPredioIdPredio(predioId).orElse(null);
        return publicaciones.findByPredioIdPredioOrderByNumeroDesc(predioId).stream()
                .map(p -> new PublicacionItem(p.getNumero(), p.getFecha(), p.getUsuario(),
                        mapa != null && p.getNumero().equals(mapa.getNumeroPublicacion()))).toList();
    }

    @PostMapping("/api/predios/{predioId}/publicaciones") @ResponseBody
    @Transactional
    public Map<String, Object> publicar(@PathVariable Long predioId, @RequestBody JsonNode body,
            HttpServletRequest request) {
        exigirPublicacion(request);
        MapaEspacial mapa = mapas.findByPredioIdPredio(predioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Guarda primero el borrador"));
        comprobarVersion(mapa, body);
        validar(leer(mapa.getEscena()), predioId);
        conservarLegado(mapa, predioId);
        long numero = (mapa.getNumeroPublicacion() == null ? 0 : mapa.getNumeroPublicacion()) + 1;
        String fondo = copiarFondoVersion(predioId, numero);
        LocalDateTime fecha = LocalDateTime.now();
        String actor = usuario(request);
        registrarPublicacion(mapa, numero, mapa.getEscena(), fondo, fecha, actor);
        mapa.setPublicada(true); mapa.setEscenaPublicada(mapa.getEscena());
        mapa.setNumeroPublicacion(numero); mapa.setFondoPublicado(fondo);
        mapa.setFechaPublicacion(fecha); mapa.setPublicadoPor(actor);
        mapas.saveAndFlush(mapa);
        Map<String, Object> aviso = Map.of("predioId", predioId, "numero", numero);
        if (TransactionSynchronizationManager.isSynchronizationActive())
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { eventos.broadcast("mapa-espacial-publicado", aviso); }
            });
        else eventos.broadcast("mapa-espacial-publicado", aviso);
        return Map.of("numero", numero, "version", mapa.getVersion());
    }

    @PostMapping("/api/predios/{predioId}/publicaciones/{numero}/restaurar") @ResponseBody
    @Transactional
    public Map<String, Object> restaurar(@PathVariable Long predioId, @PathVariable Long numero,
            @RequestBody JsonNode body, HttpServletRequest request) {
        exigirEdicion(request);
        MapaEspacial mapa = mapas.findByPredioIdPredio(predioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        comprobarVersion(mapa, body);
        MapaEspacialPublicacion anterior = publicaciones.findByPredioIdPredioAndNumero(predioId, numero)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        validar(leer(anterior.getEscena()), predioId);
        conservarLegado(mapa, predioId);
        try {
            Path fondo = fondoBorrador(predioId);
            if (anterior.getFondoArchivo() == null) Files.deleteIfExists(fondo);
            else {
                Path origen = FONDOS.resolve(anterior.getFondoArchivo());
                if (!Files.isRegularFile(origen)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Falta el fondo de esta versión");
                Files.createDirectories(FONDOS);
                Files.copy(origen, fondo, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) { throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo recuperar el fondo"); }
        mapa.setEscena(anterior.getEscena()); mapa.setFechaModificacion(LocalDateTime.now());
        mapa.setModificadoPor(usuario(request));
        mapas.saveAndFlush(mapa);
        return Map.of("version", mapa.getVersion(), "numeroRestaurado", numero);
    }

    private void comprobarVersion(MapaEspacial mapa, JsonNode body) {
        if (mapa.getVersion() == null || body.path("version").asLong(Long.MIN_VALUE) != mapa.getVersion())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El mapa cambió. Recarga antes de continuar.");
    }

    private String termino(String q) {
        String limpio = (q == null ? "" : q.trim()).replace("%", "").replace("_", "");
        return limpio.substring(0, Math.min(limpio.length(), 80));
    }

    private JsonNode leer(String value) {
        try { return json.readTree(value); }
        catch (JsonProcessingException e) { throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Mapa dañado"); }
    }

    private void validar(JsonNode escena, Long predioId) {
        JsonNode vista = escena.path("vista");
        if (!vista.isMissingNode() && !vista.isNull()) {
            numero(vista, "x", -5000, 5000);
            numero(vista, "y", -5000, 5000);
            numero(vista, "ancho", 100, 5000);
            numero(vista, "fondo", 100, 5000);
        }
        JsonNode fondo = escena.path("fondo");
        if (!fondo.isMissingNode() && !fondo.isNull()) {
            rect(fondo, 2000);
            numero(fondo, "opacidad", 0, 1);
            numero(fondo, "giro", 0, 360);
        }
        JsonNode edificios = escena.path("edificios");
        if (!edificios.isArray() || edificios.size() > 100) malo("Límite de edificios excedido");
        Set<String> ids = new HashSet<>();
        Set<Long> oficinaIds = new HashSet<>(), activoIds = new HashSet<>();
        for (JsonNode e : edificios) {
            id(e, ids); nombre(e, "nombre");
            if (!Set.of("EDIFICIO", "OFICINA_INDEPENDIENTE").contains(e.path("tipo").asText())) malo("Tipo de edificio inválido");
            rect(e, 2000); numero(e, "altura", 1, 800);
            if (e.has("giro")) numero(e, "giro", 0, 360);
            estilo(e);
            poligono(e, 2000, 2000);
            JsonNode pisos = e.path("pisos");
            if (!pisos.isArray() || pisos.size() < 1 || pisos.size() > 40) malo("Un edificio requiere pisos");
            for (JsonNode p : pisos) {
                id(p, ids); nombre(p, "nombre"); numero(p, "altura", 1, 20);
                validarPlano(p.path("planoReferencia"), predioId);
                JsonNode trazos = p.path("trazos");
                if (!trazos.isMissingNode()) {
                    if (!trazos.isArray() || trazos.size() > 500) malo("Demasiados detalles de plano");
                    for (JsonNode trazo : trazos) {
                        id(trazo, ids);
                        if (!Set.of("MURO", "PUERTA", "VENTANA", "ESCALERA").contains(trazo.path("tipo").asText()))
                            malo("Tipo de detalle inválido");
                        numero(trazo, "x1", 0, e.path("ancho").asDouble());
                        numero(trazo, "y1", 0, e.path("fondo").asDouble());
                        numero(trazo, "x2", 0, e.path("ancho").asDouble());
                        numero(trazo, "y2", 0, e.path("fondo").asDouble());
                        if (Math.hypot(trazo.path("x1").asDouble() - trazo.path("x2").asDouble(),
                                trazo.path("y1").asDouble() - trazo.path("y2").asDouble()) < .1)
                            malo("Detalle demasiado corto");
                    }
                }
                JsonNode espacios = p.path("espacios");
                if (!espacios.isArray() || espacios.size() > 300) malo("Demasiados espacios");
                for (JsonNode s : espacios) {
                    id(s, ids); nombre(s, "nombre"); rect(s, Math.max(e.path("ancho").asDouble(), e.path("fondo").asDouble()));
                    validarPlano(s.path("planoReferencia"), predioId);
                    poligono(s, e.path("ancho").asDouble(), e.path("fondo").asDouble());
                    estilo(s);
                    if (s.path("x").asDouble() + s.path("ancho").asDouble() > e.path("ancho").asDouble() + .001
                            || s.path("y").asDouble() + s.path("fondo").asDouble() > e.path("fondo").asDouble() + .001)
                        malo("Espacio fuera del edificio");
                    Long oficinaId = s.path("oficinaId").isIntegralNumber() ? s.path("oficinaId").asLong() : null;
                    if (oficinaId != null) oficinaIds.add(oficinaId);
                    JsonNode cuartos = s.path("cuartos");
                    if (!cuartos.isMissingNode()) {
                        if (!cuartos.isArray() || cuartos.size() > 100) malo("Demasiados cuartos en un espacio");
                        for (JsonNode cuarto : cuartos) {
                            id(cuarto, ids); nombre(cuarto, "nombre");
                            rect(cuarto, Math.max(s.path("ancho").asDouble(), s.path("fondo").asDouble()));
                            poligono(cuarto, s.path("ancho").asDouble(), s.path("fondo").asDouble());
                            estilo(cuarto);
                            if (cuarto.path("x").asDouble() + cuarto.path("ancho").asDouble() > s.path("ancho").asDouble() + .001
                                    || cuarto.path("y").asDouble() + cuarto.path("fondo").asDouble() > s.path("fondo").asDouble() + .001)
                                malo("Cuarto fuera del espacio");
                        }
                    }
                    JsonNode mobiliario = s.path("mobiliario");
                    if (!mobiliario.isMissingNode()) {
                        if (!mobiliario.isArray() || mobiliario.size() > 300) malo("Demasiado mobiliario en un espacio");
                        for (JsonNode mueble : mobiliario) {
                            id(mueble, ids); nombre(mueble, "nombre");
                            figura(mueble, true);
                            rect(mueble, Math.max(s.path("ancho").asDouble(), s.path("fondo").asDouble()));
                            numero(mueble, "giro", 0, 360);
                            if (mueble.path("x").asDouble() + mueble.path("ancho").asDouble() > s.path("ancho").asDouble() + .001
                                    || mueble.path("y").asDouble() + mueble.path("fondo").asDouble() > s.path("fondo").asDouble() + .001)
                                malo("Mobiliario fuera del espacio");
                        }
                    }
                    JsonNode colocados = s.path("activos");
                    if (!colocados.isArray() || colocados.size() > 1000) malo("Demasiados activos en un espacio");
                    for (JsonNode a : colocados) {
                        long activoId = a.path("activoId").asLong(-1);
                        if (oficinaId == null || activoId < 1 || !activoIds.add(activoId)) malo("Activo repetido o sin oficina");
                        rect(a, Math.max(s.path("ancho").asDouble(), s.path("fondo").asDouble()));
                        if (a.path("x").asDouble() + a.path("ancho").asDouble() > s.path("ancho").asDouble() + .001
                                || a.path("y").asDouble() + a.path("fondo").asDouble() > s.path("fondo").asDouble() + .001)
                            malo("Activo fuera del espacio");
                        numero(a, "giro", 0, 360);
                        figura(a, false);
                    }
                }
            }
        }
        Map<Long, Oficina> oficinaPorId = new HashMap<>();
        for (Oficina o : oficinas.findAllById(oficinaIds)) oficinaPorId.put(o.getIdOficina(), o);
        if (oficinaPorId.size() != oficinaIds.size() || oficinaPorId.values().stream().anyMatch(o ->
                !predioId.equals(o.getPredio().getIdPredio()) || !"ACTIVO".equals(o.getEstado()))) malo("Oficina ajena al predio");
        Map<Long, Activo> activoPorId = new HashMap<>();
        for (Activo a : activos.findAllById(activoIds)) activoPorId.put(a.getIdActivo(), a);
        if (activoPorId.size() != activoIds.size()) malo("Activo inexistente");
        for (JsonNode e : edificios) for (JsonNode p : e.path("pisos")) for (JsonNode s : p.path("espacios"))
            for (JsonNode a : s.path("activos")) {
                Activo actual = activoPorId.get(a.path("activoId").asLong());
                if (actual.getOficina() == null || actual.getOficina().getIdOficina() != s.path("oficinaId").asLong()
                        || !"ACTIVO".equals(actual.getEstado())) malo("El activo cambió de oficina o de estado");
            }
    }

    private void validarPlano(JsonNode plano, Long predioId) {
        if (plano.isMissingNode() || plano.isNull()) return;
        String archivo = plano.path("archivo").asText("");
        if (!archivo.matches("[0-9a-fA-F-]{36}") || !Files.isRegularFile(PLANOS.resolve(predioId.toString()).resolve(archivo + ".jpg")))
            malo("Plano de referencia inválido");
        numero(plano, "x", -2000, 2000);
        numero(plano, "y", -2000, 2000);
        numero(plano, "ancho", .1, 2000);
        numero(plano, "fondo", .1, 2000);
        numero(plano, "opacidad", 0, 1);
        numero(plano, "giro", 0, 360);
    }

    private void id(JsonNode node, Set<String> ids) {
        String id = node.path("id").asText("");
        if (id.isBlank() || id.length() > 70 || !ids.add(id)) malo("ID duplicado o inválido");
    }
    private void nombre(JsonNode node, String campo) {
        String valor = node.path(campo).asText("").trim();
        if (valor.isEmpty() || valor.length() > 120) malo("Nombre obligatorio (máximo 120 caracteres)");
    }
    private void estilo(JsonNode node) {
        if (node.has("color") && !node.path("color").asText("").matches("#[0-9a-fA-F]{6}")) malo("Color inválido");
        if (node.has("textoGiro")) numero(node, "textoGiro", -180, 180);
        if (node.has("giro")) numero(node, "giro", 0, 360);
    }
    private void figura(JsonNode node, boolean obligatoria) {
        JsonNode valor = node.path("figura");
        if (valor.isMissingNode() && !obligatoria) return;
        if (!Set.of("MESA", "SILLA", "EQUIPO", "ESTANTE", "ARCHIVADOR", "OTRO").contains(valor.asText("")))
            malo("Figura de mobiliario inválida");
    }
    private void rect(JsonNode node, double max) {
        numero(node, "x", 0, 2000); numero(node, "y", 0, 2000);
        numero(node, "ancho", .1, max); numero(node, "fondo", .1, max);
    }
    /** Los rectángulos antiguos siguen siendo válidos; todo polígono nuevo debe ser simple. */
    private void poligono(JsonNode node, double maxX, double maxY) {
        JsonNode puntos = node.path("puntos");
        if (puntos.isMissingNode() || puntos.isNull()) return;
        if (!puntos.isArray() || puntos.size() < 3 || puntos.size() > 128) malo("El contorno necesita entre 3 y 128 puntos");
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY;
        double xMayor = 0, yMayor = 0, area = 0;
        for (int i = 0; i < puntos.size(); i++) {
            JsonNode punto = puntos.get(i), siguiente = puntos.get((i + 1) % puntos.size());
            numero(punto, "x", 0, maxX); numero(punto, "y", 0, maxY);
            double x = punto.path("x").asDouble(), y = punto.path("y").asDouble();
            if (Math.hypot(x - siguiente.path("x").asDouble(), y - siguiente.path("y").asDouble()) < .05)
                malo("El contorno contiene puntos repetidos");
            minX = Math.min(minX, x); minY = Math.min(minY, y);
            xMayor = Math.max(xMayor, x); yMayor = Math.max(yMayor, y);
            area += x * siguiente.path("y").asDouble() - siguiente.path("x").asDouble() * y;
        }
        if (Math.abs(area) < .02) malo("Contorno sin superficie");
        if (Math.abs(minX - node.path("x").asDouble()) > .11 || Math.abs(minY - node.path("y").asDouble()) > .11
                || Math.abs(xMayor - minX - node.path("ancho").asDouble()) > .11
                || Math.abs(yMayor - minY - node.path("fondo").asDouble()) > .11)
            malo("Las medidas no coinciden con el contorno");
        int n = puntos.size();
        for (int i = 0; i < n; i++) for (int j = i + 1; j < n; j++) {
            if (j == i + 1 || (i == 0 && j == n - 1)) continue;
            if (cruzan(puntos.get(i), puntos.get((i + 1) % n), puntos.get(j), puntos.get((j + 1) % n)))
                malo("El contorno se cruza consigo mismo");
        }
    }
    private boolean cruzan(JsonNode a, JsonNode b, JsonNode c, JsonNode d) {
        double ax = a.path("x").asDouble(), ay = a.path("y").asDouble();
        double bx = b.path("x").asDouble(), by = b.path("y").asDouble();
        double cx = c.path("x").asDouble(), cy = c.path("y").asDouble();
        double dx = d.path("x").asDouble(), dy = d.path("y").asDouble();
        double abC = (bx - ax) * (cy - ay) - (by - ay) * (cx - ax);
        double abD = (bx - ax) * (dy - ay) - (by - ay) * (dx - ax);
        double cdA = (dx - cx) * (ay - cy) - (dy - cy) * (ax - cx);
        double cdB = (dx - cx) * (by - cy) - (dy - cy) * (bx - cx);
        double epsilon = 1e-8;
        if (abC * abD < -epsilon && cdA * cdB < -epsilon) return true;
        return (Math.abs(abC) <= epsilon && entre(ax, bx, cx) && entre(ay, by, cy))
                || (Math.abs(abD) <= epsilon && entre(ax, bx, dx) && entre(ay, by, dy))
                || (Math.abs(cdA) <= epsilon && entre(cx, dx, ax) && entre(cy, dy, ay))
                || (Math.abs(cdB) <= epsilon && entre(cx, dx, bx) && entre(cy, dy, by));
    }
    private boolean entre(double a, double b, double valor) {
        return valor >= Math.min(a, b) - 1e-8 && valor <= Math.max(a, b) + 1e-8;
    }
    private void numero(JsonNode node, String key, double min, double max) {
        JsonNode n = node.path(key);
        if (!n.isNumber() || !Double.isFinite(n.asDouble()) || n.asDouble() < min || n.asDouble() > max)
            malo("Medida inválida: " + key);
    }
    private void malo(String mensaje) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, mensaje); }
}
