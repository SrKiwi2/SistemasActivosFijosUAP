package com.usic.SistemasActivosFijosUAP.model.service.movil;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.usic.SistemasActivosFijosUAP.model.dao.IActivoMovilDao;
import com.usic.SistemasActivosFijosUAP.model.dao.IEntidadDao;
import com.usic.SistemasActivosFijosUAP.model.dao.IEscaneoMovilDao;
import com.usic.SistemasActivosFijosUAP.model.dto.movil.ActivoFichaMovilDTO;
import com.usic.SistemasActivosFijosUAP.model.dto.movil.DiscrepanciaDTO;
import com.usic.SistemasActivosFijosUAP.model.dto.movil.EscaneoRequest;
import com.usic.SistemasActivosFijosUAP.model.dto.movil.EscaneoResultadoDTO;
import com.usic.SistemasActivosFijosUAP.model.dto.movil.PayloadQr;
import com.usic.SistemasActivosFijosUAP.model.entity.Activo;
import com.usic.SistemasActivosFijosUAP.model.entity.EscaneoMovil;
import com.usic.SistemasActivosFijosUAP.model.entity.Entidad;
import com.usic.SistemasActivosFijosUAP.model.entity.Municipio;
import com.usic.SistemasActivosFijosUAP.model.entity.Oficina;
import com.usic.SistemasActivosFijosUAP.model.entity.Predio;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Resuelve un escaneo contra la base de datos y explica las diferencias.
 *
 * <p><b>Principio rector:</b> la etiqueta es papel impreso en el pasado; la base
 * de datos es el presente. Cuando difieren, gana la base de datos y la etiqueta
 * se señala como desactualizada — nunca al revés.
 *
 * <p>La comparación se hace en tres capas (ver {@link DiscrepanciaDTO}) porque
 * no todas las diferencias significan lo mismo: una descripción cambiada es una
 * etiqueta vieja, pero un código que apunta a otro predio es simplemente un
 * activo que se transfirió, y confundir ambas cosas haría que el operador
 * desconfiara de avisos que son normales.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EscaneoMovilService {

    private static final int MAX_CANDIDATOS = 20;

    private final ParserQrActivoService parser;
    private final IActivoMovilDao       activoDao;
    private final IEntidadDao           entidadDao;
    private final IEscaneoMovilDao      escaneoDao;
    private final ActivoMovilMapper     mapper;

    // =========================================================================
    //  Entrada principal
    // =========================================================================

    @Transactional
    public EscaneoResultadoDTO verificar(EscaneoRequest req, Usuario usuario) {

        PayloadQr qr = req.esManual()
                ? parser.parsearEntradaManual(req.payload())
                : parser.parsear(req.payload());

        EscaneoResultadoDTO resultado = resolver(qr);
        registrar(qr, req, resultado, usuario);
        return resultado;
    }

    private EscaneoResultadoDTO resolver(PayloadQr qr) {

        if (!qr.legible()) {
            return vacio(qr, "ILEGIBLE",
                    "No se reconoció ningún código de activo en la etiqueta. "
                  + "Pruebe de nuevo o escriba el código a mano.");
        }

        // Solo correlativo tecleado: puede repetirse entre predios/grupos.
        if (qr.soloCorrelativo()) {
            return resolverPorCorrelativo(qr);
        }

        boolean entidadValida = prefijoValido(qr.prefijoEntidad());

        Optional<Activo> encontrado = activoDao.fichaPorCodigo(qr.codigo());

        if (encontrado.isEmpty()) {
            String mensaje = entidadValida
                    ? "El código " + qr.codigoVisual() + " no existe en el sistema."
                    : "La etiqueta pertenece a otra entidad (prefijo " + qr.prefijoEntidad()
                      + "), no a la Universidad.";
            return new EscaneoResultadoDTO(
                    qr.codigo(), qr.codigoVisual(), qr.prefijoEntidad(), entidadValida,
                    entidadValida ? "NO_ENCONTRADO" : "OTRA_ENTIDAD",
                    mensaje, null, List.of(), List.of());
        }

        Activo activo = encontrado.get();
        ActivoFichaMovilDTO ficha = mapper.aFicha(activo);

        List<DiscrepanciaDTO> discrepancias = new ArrayList<>();
        discrepancias.addAll(compararTextos(qr, activo));     // capa 1
        discrepancias.addAll(compararCodigo(qr, activo));     // capa 2
        discrepancias.addAll(revisarEstado(activo));          // capa 3

        String veredicto = veredictoDe(discrepancias, entidadValida);

        return new EscaneoResultadoDTO(
                qr.codigo(), ficha.codigoVisual(), qr.prefijoEntidad(), entidadValida,
                veredicto, mensajeDe(veredicto, discrepancias),
                ficha, discrepancias, List.of());
    }

    private EscaneoResultadoDTO resolverPorCorrelativo(PayloadQr qr) {
        List<Activo> candidatos = activoDao.porCorrelativo(
                "-" + qr.correlativo(), PageRequest.of(0, MAX_CANDIDATOS));

        if (candidatos.isEmpty()) {
            return vacio(qr, "NO_ENCONTRADO",
                    "Ningún activo termina en " + qr.correlativo() + ".");
        }

        if (candidatos.size() == 1) {
            Activo unico = candidatos.get(0);
            ActivoFichaMovilDTO ficha = mapper.aFicha(unico);
            List<DiscrepanciaDTO> discrepancias = revisarEstado(unico);
            String veredicto = veredictoDe(discrepancias, true);
            return new EscaneoResultadoDTO(
                    unico.getCodigo(), ficha.codigoVisual(), null, true,
                    veredicto, mensajeDe(veredicto, discrepancias),
                    ficha, discrepancias, List.of());
        }

        return new EscaneoResultadoDTO(
                null, null, null, true, "VARIOS_CANDIDATOS",
                candidatos.size() + " activos terminan en " + qr.correlativo()
                + ". Elija cuál corresponde.",
                null, List.of(),
                candidatos.stream().map(mapper::aFicha).toList());
    }

    // =========================================================================
    //  Capa 1 — texto impreso en la etiqueta vs base de datos
    // =========================================================================

    private List<DiscrepanciaDTO> compararTextos(PayloadQr qr, Activo a) {
        List<DiscrepanciaDTO> lista = new ArrayList<>();

        Oficina   oficina   = a.getOficina();
        Predio    predio    = (oficina != null) ? oficina.getPredio() : null;
        Municipio municipio = (predio  != null) ? predio.getMunicipio() : null;
        Entidad   entidad   = (predio  != null) ? predio.getEntidad() : null;

        if (qr.siglaEntidad() != null && entidad != null) {
            comparar(lista, 1, "entidad", "Entidad",
                    qr.siglaEntidad(), entidad.getSigla(), false);
        }

        if (qr.municipio() != null) {
            String enBd = (municipio != null && municipio.getNombre() != null)
                    ? municipio.getNombre()
                    : (predio != null ? predio.getCiudad() : null);
            comparar(lista, 1, "municipio", "Municipio", qr.municipio(), enBd, false);
        }

        if (qr.predio() != null && predio != null) {
            comparar(lista, 1, "predio", "Predio", qr.predio(), predio.getDescrip(), true);
        }

        if (qr.grupoContable() != null && a.getGrupoContable() != null) {
            comparar(lista, 1, "grupoContable", "Grupo contable",
                    qr.grupoContable(), a.getGrupoContable().getNombre(), true);
        }

        if (qr.descripcion() != null) {
            compararDescripcion(lista, qr.descripcion(), a.getDescripcion());
        }

        return lista;
    }

    /**
     * La descripción del sistema suele ser la de la etiqueta <i>más</i> los
     * datos que se le fueron añadiendo ({@code M:}, {@code MOD:}, {@code S:}).
     * Si una contiene a la otra no es una diferencia real, es enriquecimiento:
     * marcarlo como discrepancia llenaría la pantalla de avisos que el operador
     * aprendería a ignorar, y entonces también ignoraría los de verdad.
     */
    private void compararDescripcion(List<DiscrepanciaDTO> lista, String enQr, String enBd) {
        String a = normalizar(enQr);
        String b = normalizar(enBd);
        if (a.isEmpty() || b.isEmpty() || a.equals(b)) return;
        if (b.startsWith(a) || a.startsWith(b)) return;

        lista.add(DiscrepanciaDTO.aviso(1, "descripcion", "Descripción", enQr, enBd));
    }

    // =========================================================================
    //  Capa 2 — segmentos del propio código vs ubicación actual
    // =========================================================================

    /**
     * El código lleva grabados municipio, predio y grupo del día en que se
     * emitió, y no cambia nunca. Si el activo se transfirió, dejará de coincidir
     * con su ubicación actual: es esperable y se informa como tal.
     */
    private List<DiscrepanciaDTO> compararCodigo(PayloadQr qr, Activo a) {
        List<DiscrepanciaDTO> lista = new ArrayList<>();

        Oficina   oficina   = a.getOficina();
        Predio    predio    = (oficina != null) ? oficina.getPredio() : null;
        Municipio municipio = (predio  != null) ? predio.getMunicipio() : null;

        if (qr.codMunicipio() != null && municipio != null) {
            comparar(lista, 2, "codMunicipio", "Municipio (según el código)",
                    qr.codMunicipio(), municipio.getCodigo(), false);
        }

        if (qr.codPredio() != null && predio != null) {
            comparar(lista, 2, "codPredio", "Predio (según el código)",
                    qr.codPredio(), predio.getCodigo(), true);
        }

        if (qr.codGrupo() != null && a.getGrupoContable() != null
                && a.getGrupoContable().getCodDbf() != null) {
            comparar(lista, 2, "codGrupo", "Grupo contable (según el código)",
                    qr.codGrupo(), String.format("%02d", a.getGrupoContable().getCodDbf()), false);
        }

        return lista;
    }

    // =========================================================================
    //  Capa 3 — estado del registro
    // =========================================================================

    private List<DiscrepanciaDTO> revisarEstado(Activo a) {
        String estado = a.getEstado();
        if (estado == null || "ACTIVO".equalsIgnoreCase(estado)) {
            return List.of();
        }

        String explicacion = switch (estado.toUpperCase()) {
            case "PENDIENTE" -> "Registrado en SCIAF, aún no subido al VSIAF";
            case "CANCELADO" -> "El activo fue cancelado y su código quedó liberado";
            case "BAJA"      -> "El activo fue dado de baja";
            case "ELIMINADO" -> "El registro fue eliminado";
            default          -> "Estado no habitual";
        };

        return List.of(new DiscrepanciaDTO(3, "estado", "Estado del registro",
                null, estado + " — " + explicacion, "AVISO"));
    }

    // =========================================================================
    //  Veredicto
    // =========================================================================

    private String veredictoDe(List<DiscrepanciaDTO> discrepancias, boolean entidadValida) {
        if (!entidadValida)                          return "OTRA_ENTIDAD";
        if (tieneCapa(discrepancias, 3))             return "REVISAR_ESTADO";
        if (tieneCapa(discrepancias, 2))             return "REUBICADO";
        if (tieneCapa(discrepancias, 1))             return "ETIQUETA_DESACTUALIZADA";
        return "OK";
    }

    private String mensajeDe(String veredicto, List<DiscrepanciaDTO> d) {
        return switch (veredicto) {
            case "OK" -> "La etiqueta coincide con el sistema.";
            case "ETIQUETA_DESACTUALIZADA" ->
                    "La etiqueta está desactualizada (" + d.size() + " "
                    + (d.size() == 1 ? "diferencia" : "diferencias")
                    + "). Los datos correctos son los del sistema.";
            case "REUBICADO" ->
                    "El activo cambió de ubicación desde que se emitió su código. "
                    + "Los datos correctos son los del sistema.";
            case "REVISAR_ESTADO" -> "Atención: revise el estado de este activo.";
            case "OTRA_ENTIDAD"   -> "La etiqueta no pertenece a la Universidad.";
            default -> "";
        };
    }

    // =========================================================================
    //  Auxiliares
    // =========================================================================

    private void comparar(List<DiscrepanciaDTO> lista, int capa, String campo, String etiqueta,
                          String enQr, String enBd, boolean importante) {
        if (enQr == null || enBd == null) return;
        if (normalizar(enQr).equals(normalizar(enBd))) return;

        lista.add(importante
                ? DiscrepanciaDTO.aviso(capa, campo, etiqueta, enQr, enBd)
                : DiscrepanciaDTO.info(capa, campo, etiqueta, enQr, enBd));
    }

    /** Mayúsculas, sin tildes y sin espacios de más: comparar texto impreso con texto tecleado. */
    private String normalizar(String texto) {
        if (texto == null) return "";
        String sinTildes = Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return sinTildes.toUpperCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private boolean tieneCapa(List<DiscrepanciaDTO> lista, int capa) {
        return lista.stream().anyMatch(d -> d.capa() == capa);
    }

    private EscaneoResultadoDTO vacio(PayloadQr qr, String veredicto, String mensaje) {
        return new EscaneoResultadoDTO(
                qr.codigo(), qr.codigoVisual(), qr.prefijoEntidad(), true,
                veredicto, mensaje, null, List.of(), List.of());
    }

    /**
     * Códigos de entidad válidos ({@code 148}). Se consultan en cada verificación
     * porque son pocas filas y así un alta de entidad no obliga a reiniciar.
     */
    private boolean prefijoValido(String prefijo) {
        if (prefijo == null || prefijo.isBlank()) return true; // etiqueta sin prefijo
        Set<String> codigos = entidadDao.findAll().stream()
                .map(Entidad::getEntidadCodigo)
                .filter(c -> c != null && !c.isBlank())
                .map(String::trim)
                .collect(Collectors.toSet());
        return codigos.isEmpty() || codigos.contains(prefijo.trim());
    }

    private void registrar(PayloadQr qr, EscaneoRequest req,
                           EscaneoResultadoDTO resultado, Usuario usuario) {
        try {
            EscaneoMovil registro = new EscaneoMovil();
            registro.setUsuario(usuario);
            registro.setCodigoDetectado(resultado.codigoDetectado());
            registro.setPrefijoEntidad(qr.prefijoEntidad());
            registro.setPayloadCrudo(req.payload());
            registro.setOrigen(req.esManual() ? "MANUAL" : "CAMARA");
            registro.setVeredicto(resultado.veredicto());
            registro.setCantidadDiscrepancias(resultado.discrepancias().size());
            registro.setFecha(LocalDateTime.now());
            escaneoDao.save(registro);
        } catch (Exception e) {
            // La auditoría nunca debe impedir que el operador vea su activo.
            log.warn("[MOVIL] No se pudo registrar el escaneo: {}", e.getMessage());
        }
    }
}
