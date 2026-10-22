package com.usic.SistemasActivosFijosUAP.model.service.adicional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import com.usic.SistemasActivosFijosUAP.config.RolesSciaf;
import com.usic.SistemasActivosFijosUAP.model.IService.IEstadoActivoService;
import com.usic.SistemasActivosFijosUAP.model.IService.IGrupoContableService;
import com.usic.SistemasActivosFijosUAP.model.IService.IOficinaService;
import com.usic.SistemasActivosFijosUAP.model.IService.IOrganismoFinancieroService;
import com.usic.SistemasActivosFijosUAP.model.IService.IPredioServicio;
import com.usic.SistemasActivosFijosUAP.model.IService.IResponsableService;
import com.usic.SistemasActivosFijosUAP.model.dao.IActivoAdicionalDao;
import com.usic.SistemasActivosFijosUAP.model.dao.IActivoDao;
import com.usic.SistemasActivosFijosUAP.model.dao.IActivoFotoDao;
import com.usic.SistemasActivosFijosUAP.model.dao.IAuxiliarAdicionalDao;
import com.usic.SistemasActivosFijosUAP.model.dao.IHistorialActivoDao;
import com.usic.SistemasActivosFijosUAP.model.dto.adicional.AdicionalRequest;
import com.usic.SistemasActivosFijosUAP.model.dto.adicional.AdicionalLotesRequest;
import com.usic.SistemasActivosFijosUAP.model.entity.Activo;
import com.usic.SistemasActivosFijosUAP.model.entity.ActivoAdicional;
import com.usic.SistemasActivosFijosUAP.model.entity.ActivoFoto;
import com.usic.SistemasActivosFijosUAP.model.entity.AuxiliarAdicional;
import com.usic.SistemasActivosFijosUAP.model.entity.EstadoActivo;
import com.usic.SistemasActivosFijosUAP.model.entity.GrupoContable;
import com.usic.SistemasActivosFijosUAP.model.entity.HistorialActivo;
import com.usic.SistemasActivosFijosUAP.model.entity.Oficina;
import com.usic.SistemasActivosFijosUAP.model.entity.OrganismoFinanciero;
import com.usic.SistemasActivosFijosUAP.model.entity.Persona;
import com.usic.SistemasActivosFijosUAP.model.entity.Predio;
import com.usic.SistemasActivosFijosUAP.model.entity.Responsable;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;
import com.usic.SistemasActivosFijosUAP.model.service.ArchivoStorageService;
import com.usic.SistemasActivosFijosUAP.model.service.PdfEtiquetasQrService;
import com.usic.SistemasActivosFijosUAP.model.service.documento.WordEtiquetasQrService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Activos adicionales (Administración de activos → Adicionales): bienes que no estaban en el
 * sistema y se registran con lo que se sepa de ellos.
 * <ul>
 *   <li><b>Ningún dato es obligatorio</b>, salvo algo con qué reconocer el bien: la
 *       descripción o el código antiguo.</li>
 *   <li><b>Código antiguo</b>: la codificación que usaba Activos Fijos (columna propia
 *       {@code activo.codigo_antiguo}; no es el CODIGOSEC del VSIAF).</li>
 *   <li><b>Código nuevo</b> con serie propia y una A al final ({@code 01-02-04-00001A}),
 *       solo cuando el bien tiene predio y grupo contable; si no, queda sin código hasta que
 *       se completen (al corregirlo se le asigna).</li>
 *   <li>Existen <b>solo en el SCIAF</b> ({@code procedencia = ADICIONAL}, {@code sinc_vsiaf
 *       = NO_APLICA}): la barrera central de {@code ActualDbfWriterService} los descarta.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdicionalService {

    public static final String PERMISO = "opcion_adicional";
    public static final int MAX_FOTOS = 3;
    /** Etiquetas por impresión: 10 hojas de 24. */
    public static final int MAX_ETIQUETAS = 240;

    private static final long TURNO_ADICIONALES = 0x5C1AFAD01L;
    private static final int TOPE_UNIDADES = 200;
    private static final int MAX_DESCRIPCION = 1024;
    private static final int MAX_CORRELATIVO = 99_999;
    private static final long MAX_BYTES_FOTO = 6L * 1024 * 1024;
    private static final String ELIMINADO = "ELIMINADO";
    private static final String SIN_DESCRIPCION = "SIN DESCRIPCIÓN";

    private final IActivoAdicionalDao adicionalDao;
    private final IActivoFotoDao fotoDao;
    private final IActivoDao activoDao;
    private final IHistorialActivoDao historialDao;
    private final IOficinaService oficinaService;
    private final IResponsableService responsableService;
    private final IPredioServicio predioService;
    private final IGrupoContableService grupoService;
    private final IAuxiliarAdicionalDao auxiliarAdicionalDao;
    private final IOrganismoFinancieroService financiadorService;
    private final IEstadoActivoService estadoActivoService;
    private final ArchivoStorageService archivos;
    private final WordEtiquetasQrService hojaEtiquetas;
    private final PlatformTransactionManager txManager;

    /** ¿Puede usar el módulo? ADMINISTRADOR / SUPER USUARIO o la opción de menú. */
    public static boolean puede(Usuario usuario, Object opcionesSesion) {
        if (usuario == null || !"ACTIVO".equalsIgnoreCase(usuario.getEstado())) return false;
        if (RolesSciaf.esAdministrativo(usuario)) return true;
        return opcionesSesion instanceof Set<?> set && set.contains(PERMISO);
    }

    private static void exigirPermiso(Usuario usuario, Object opciones) {
        if (!puede(usuario, opciones)) {
            throw new SecurityException("No tiene permiso para Activos adicionales (opción «Registro de Adicionales»).");
        }
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> auxiliares(Long grupoId, Usuario usuario, Object opciones) {
        exigirPermiso(usuario, opciones);
        if (grupoId == null) return List.of();
        return auxiliarAdicionalDao.findByGrupoContableIdGrupoContableAndEstadoNotOrderByNombreAsc(grupoId, ELIMINADO)
                .stream().map(a -> Map.<String, Object>of("id", a.getIdAuxiliarAdicional(),
                        "text", (a.getCodigo() == null ? "" : a.getCodigo() + " · ") + a.getNombre()))
                .toList();
    }

    @Transactional
    public Map<String, Object> crearAuxiliar(Long grupoId, String nombre, String codigo, Usuario usuario, Object opciones) {
        exigirPermiso(usuario, opciones);
        GrupoContable grupo = grupoId == null ? null : grupoService.findById(grupoId);
        if (grupo == null) throw new IllegalArgumentException("Seleccione un grupo contable para el auxiliar.");
        String nombreLimpio = mayus(nombre), codigoLimpio = mayus(codigo);
        if (nombreLimpio == null) throw new IllegalArgumentException("Escriba el nombre del auxiliar.");
        exigirLargo(nombreLimpio, 255, "El nombre del auxiliar");
        exigirLargo(codigoLimpio, 30, "El código del auxiliar");
        if (auxiliarAdicionalDao.existsByGrupoContableIdGrupoContableAndNombreIgnoreCase(grupoId, nombreLimpio))
            throw new IllegalArgumentException("Ya existe un auxiliar adicional con ese nombre en el grupo.");
        AuxiliarAdicional auxiliar = new AuxiliarAdicional();
        auxiliar.setGrupoContable(grupo);
        auxiliar.setNombre(nombreLimpio);
        auxiliar.setCodigo(codigoLimpio);
        auxiliar.setEstado("ACTIVO");
        auxiliarAdicionalDao.saveAndFlush(auxiliar);
        return Map.of("id", auxiliar.getIdAuxiliarAdicional(),
                "text", (codigoLimpio == null ? "" : codigoLimpio + " · ") + nombreLimpio);
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Validación común (alta y corrección)
    // ════════════════════════════════════════════════════════════════════════

    /** Los datos del formulario ya validados y en mayúsculas. */
    private static final class Datos {
        Predio predio;
        Oficina oficina;
        Responsable responsable;
        GrupoContable grupo;
        AuxiliarAdicional auxiliar;
        EstadoActivo estadoActivo;
        List<EstadoActivo> estados = new ArrayList<>();
        OrganismoFinanciero financiador;
        LocalDate fecha;
        Double valor;
        Integer vidaUtil;
        String base, marca, modelo, color, material, dimensiones, caracteristicas, observacion;
        int cantidad;
        List<String> codigosAntiguos = new ArrayList<>();
        List<String> series = new ArrayList<>();

        String codigoAntiguo(int u) { return u < codigosAntiguos.size() ? codigosAntiguos.get(u) : null; }
        String serie(int u) { return u < series.size() ? series.get(u) : null; }
        EstadoActivo estado(int u) { return estados.isEmpty() ? estadoActivo : estados.get(u); }

        /** MUN-PRED-GRP si hay predio configurado y grupo; si no, null (queda sin código). */
        String prefijo() {
            if (predio == null || grupo == null || grupo.getCodDbf() == null) return null;
            String mun = predio.getMunicipio() != null ? limpiar(predio.getMunicipio().getCodigo()) : null;
            String pred = limpiar(predio.getCodigo());
            if (mun == null || pred == null) return null;
            return mun + "-" + pred + "-" + String.format("%02d", grupo.getCodDbf());
        }

        /** Descripción del activo: descripción, material, medidas, color, M:, MOD:, N/S:. */
        String descripcionCon(String serie, String codigoAntiguo) {
            String b = base != null ? base : (codigoAntiguo != null ? "BIEN ADICIONAL" : SIN_DESCRIPCION);
            StringBuilder sb = new StringBuilder(b);
            if (material != null)    sb.append(" MATERIAL ").append(material);
            if (dimensiones != null) sb.append(" MEDIDAS ").append(dimensiones);
            if (color != null)       sb.append(" COLOR ").append(color);
            List<String> t = new ArrayList<>();
            if (marca != null)  t.add("M:" + marca);
            if (modelo != null) t.add("MOD:" + modelo);
            if (serie != null)  t.add("N/S:" + serie);
            if (!t.isEmpty()) sb.append(' ').append(String.join(", ", t));
            return sb.toString();
        }
    }

    /**
     * @param actual al corregir, el activo tal como está: si la oficina o el responsable no
     *               cambian, no se les vuelve a exigir que sigan vigentes.
     */
    private Datos validar(AdicionalRequest r, Activo actual) {
        if (r == null) throw new IllegalArgumentException("No llegaron datos.");
        Datos d = new Datos();
        d.base = mayus(r.getDescripcion());
        exigirLargo(d.base, 600, "La descripción");
        d.cantidad = r.getCantidad() == null ? 1 : r.getCantidad();
        if (d.cantidad < 1 || d.cantidad > TOPE_UNIDADES) throw new IllegalArgumentException("La cantidad va de 1 a " + TOPE_UNIDADES + ".");
        d.codigosAntiguos = posicionales(r.getCodigosAntiguos(), "código antiguo", 60, d.cantidad, false);
        d.series = posicionales(r.getSeries(), "serie", 80, d.cantidad, true);
        if (d.base == null && d.codigosAntiguos.stream().allMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("Escriba al menos el nombre o descripción del bien, o su código antiguo.");
        }
        if (d.base == null && d.codigosAntiguos.size() < d.cantidad) {
            throw new IllegalArgumentException("Sin descripción, cada unidad necesita su código antiguo para reconocerla.");
        }

        // Ubicación: oficina → su predio; sin oficina, el predio elegido (o ninguno).
        boolean mismaOficina = actual != null && actual.getOficina() != null && actual.getOficina().getIdOficina().equals(r.getIdOficina());
        boolean mismoResp = mismaOficina && actual.getResponsable() != null && actual.getResponsable().getIdResponsable().equals(r.getIdResponsable());
        if (r.getIdOficina() != null) {
            d.oficina = oficinaService.findById(r.getIdOficina());
            if (d.oficina == null || (!mismaOficina && ELIMINADO.equalsIgnoreCase(d.oficina.getEstado()))) {
                throw new IllegalArgumentException("La oficina elegida no existe o está dada de baja.");
            }
            if (!mismaOficina && d.oficina.isEsCustodia()) throw new IllegalArgumentException("La oficina elegida es de faltantes (custodia).");
            if (!mismaOficina && d.oficina.isEsDuplicados()) throw new IllegalArgumentException("La oficina elegida es de duplicados.");
            d.predio = d.oficina.getPredio();
            if (r.getIdPredio() != null && d.predio != null && !d.predio.getIdPredio().equals(r.getIdPredio())) {
                throw new IllegalArgumentException("La oficina elegida no es del predio elegido.");
            }
        } else if (r.getIdPredio() != null) {
            d.predio = predioService.findById(r.getIdPredio());
            if (d.predio == null) throw new IllegalArgumentException("El predio elegido no existe.");
        }
        if (r.getIdResponsable() != null) {
            if (d.oficina == null) throw new IllegalArgumentException("Para elegir responsable, elija primero la oficina.");
            d.responsable = responsableService.findById(r.getIdResponsable());
            if (d.responsable == null || (!mismoResp && (ELIMINADO.equalsIgnoreCase(d.responsable.getEstado())
                    || (d.responsable.getApiEstado() != null && d.responsable.getApiEstado() == 3)))) {
                throw new IllegalArgumentException("El responsable elegido no existe o está inactivo.");
            }
            if (!mismoResp && (d.responsable.getOficina() == null || !d.responsable.getOficina().getIdOficina().equals(d.oficina.getIdOficina()))) {
                throw new IllegalArgumentException("El responsable elegido no pertenece a la oficina elegida.");
            }
            if (!mismoResp && d.responsable.isEsCustodia()) throw new IllegalArgumentException("El responsable elegido es de custodia de faltantes.");
            if (!mismoResp && d.responsable.isEsDuplicados()) throw new IllegalArgumentException("El responsable elegido es de la oficina de duplicados.");
        }

        // Clasificación
        if (r.getIdGrupoContable() != null) {
            d.grupo = grupoService.findById(r.getIdGrupoContable());
            if (d.grupo == null) throw new IllegalArgumentException("El grupo contable elegido no existe.");
        }
        if (r.getIdAuxiliarAdicional() != null) {
            d.auxiliar = auxiliarAdicionalDao.findById(r.getIdAuxiliarAdicional())
                    .orElseThrow(() -> new IllegalArgumentException("El auxiliar adicional elegido ya no existe."));
            if (ELIMINADO.equalsIgnoreCase(d.auxiliar.getEstado()) || d.grupo == null
                    || !d.auxiliar.getGrupoContable().getIdGrupoContable().equals(d.grupo.getIdGrupoContable())) {
                throw new IllegalArgumentException("El auxiliar adicional no pertenece al grupo contable elegido.");
            }
        }
        if (r.getIdEstadoActivo() != null) {
            d.estadoActivo = estadoActivoService.findById(r.getIdEstadoActivo());
            if (d.estadoActivo == null) throw new IllegalArgumentException("El estado de conservación elegido no existe.");
        } else {
            // Sin dato, el estado por defecto del sistema: otras pantallas y reportes leen el
            // nombre del estado sin comprobar que exista.
            d.estadoActivo = estadoActivoService.findById(1L);
        }
        if (r.getEstadosPorUnidad() != null && !r.getEstadosPorUnidad().isEmpty()) {
            if (r.getEstadosPorUnidad().size() > d.cantidad)
                throw new IllegalArgumentException("Hay más estados de conservación que unidades.");
            for (int u = 0; u < d.cantidad; u++) {
                Long idEstado = u < r.getEstadosPorUnidad().size() ? r.getEstadosPorUnidad().get(u) : null;
                EstadoActivo estado = idEstado == null ? d.estadoActivo : estadoActivoService.findById(idEstado);
                if (estado == null) throw new IllegalArgumentException("El estado de conservación de la unidad " + (u + 1) + " no existe.");
                d.estados.add(estado);
            }
        }
        if (r.getIdOrganismoFinanciero() != null) {
            d.financiador = financiadorService.findById(r.getIdOrganismoFinanciero());
            if (d.financiador == null) throw new IllegalArgumentException("El financiador elegido no existe.");
        }

        // Datos
        d.fecha = r.getFechaAdquisicion();
        if (d.fecha != null && d.fecha.isAfter(LocalDate.now())) throw new IllegalArgumentException("La fecha de adquisición no puede ser futura.");
        if (r.getValorUnitario() != null) {
            double v = r.getValorUnitario();
            if (v < 0 || Double.isNaN(v) || Double.isInfinite(v)) throw new IllegalArgumentException("El valor no puede ser negativo.");
            d.valor = Math.round(v * 100.0) / 100.0;
        }
        d.vidaUtil = r.getVidaUtil() != null ? r.getVidaUtil() : (d.grupo != null ? d.grupo.getVidaUtil() : null);
        if (d.vidaUtil != null && (d.vidaUtil < 0 || d.vidaUtil > 100)) throw new IllegalArgumentException("Vida útil fuera de rango.");
        d.marca = mayus(r.getMarca());             exigirLargo(d.marca, 80, "La marca");
        d.modelo = mayus(r.getModelo());           exigirLargo(d.modelo, 80, "El modelo");
        d.color = mayus(r.getColor());             exigirLargo(d.color, 60, "El color");
        d.material = mayus(r.getMaterial());       exigirLargo(d.material, 80, "El material");
        d.dimensiones = mayus(r.getDimensiones()); exigirLargo(d.dimensiones, 80, "Las medidas");
        d.caracteristicas = texto(r.getCaracteristicas());
        if (d.caracteristicas != null) d.caracteristicas = d.caracteristicas.toUpperCase();
        exigirLargo(d.caracteristicas, 4000, "Las características");
        d.observacion = texto(r.getObservacion());
        exigirLargo(d.observacion, 4000, "La observación");

        for (int u = 0; u < d.cantidad; u++) {
            int largo = d.descripcionCon(d.serie(u), d.codigoAntiguo(u)).length();
            if (largo > MAX_DESCRIPCION) {
                throw new IllegalArgumentException("La descripción completa queda de " + largo + " caracteres y el máximo es "
                        + MAX_DESCRIPCION + ". Lleve el detalle largo a «Características».");
            }
        }
        return d;
    }

    /** Lista por posición (la línea N es la unidad N; vacía = null), sin repetidos si se pide. */
    private static List<String> posicionales(List<String> in, String que, int max, int cantidad, boolean sinRepetir) {
        List<String> out = new ArrayList<>();
        if (in == null) return out;
        Set<String> vistas = new HashSet<>();
        for (String s : in) {
            String v = mayus(s);
            if (v != null) {
                exigirLargo(v, max, "El " + que + " " + v);
                if (sinRepetir && !vistas.add(v)) throw new IllegalArgumentException("El " + que + " " + v + " está repetido.");
            }
            out.add(v);
        }
        while (!out.isEmpty() && out.get(out.size() - 1) == null) out.remove(out.size() - 1);
        if (out.size() > cantidad) throw new IllegalArgumentException("Hay más líneas de " + que + " que unidades.");
        return out;
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Alta
    // ════════════════════════════════════════════════════════════════════════

    /** @return los activos creados: {idActivo, codigo, codigoAntiguo} */
    @Transactional
    public List<Map<String, Object>> registrar(AdicionalRequest req, Usuario usuario, Object opciones) {
        exigirPermiso(usuario, opciones);
        String token = limpiar(req == null ? null : req.getToken());
        if (token != null && token.length() > 40) throw new IllegalArgumentException("Marca de registro inválida.");
        Datos d = validar(req, null);
        adicionalDao.turno(TURNO_ADICIONALES);
        return registrarValidado(d, token, usuario);
    }

    /** Todos los lotes se validan y guardan en una sola transacción. El token permite reintentar. */
    @Transactional
    public List<List<Map<String, Object>>> registrarLotes(AdicionalLotesRequest req, Usuario usuario, Object opciones) {
        exigirPermiso(usuario, opciones);
        if (req == null || req.getLotes() == null || req.getLotes().isEmpty() || req.getLotes().size() > 20)
            throw new IllegalArgumentException("Registre entre 1 y 20 lotes.");
        String token = limpiar(req.getToken());
        if (token == null || token.length() > 32)
            throw new IllegalArgumentException("Marca de registro de lotes inválida.");
        List<Datos> lotes = new ArrayList<>();
        int total = 0;
        for (int i = 0; i < req.getLotes().size(); i++) {
            try {
                Datos d = validar(req.getLotes().get(i), null);
                total += d.cantidad;
                if (total > TOPE_UNIDADES) throw new IllegalArgumentException("El total supera " + TOPE_UNIDADES + " bienes.");
                lotes.add(d);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Lote " + (i + 1) + ": " + e.getMessage(), e);
            }
        }
        adicionalDao.turno(TURNO_ADICIONALES);
        List<List<Map<String, Object>>> resultado = new ArrayList<>();
        for (int i = 0; i < lotes.size(); i++) resultado.add(registrarValidado(lotes.get(i), token + "-" + i, usuario));
        return resultado;
    }

    private List<Map<String, Object>> registrarValidado(Datos d, String token, Usuario usuario) {
        if (token != null) {
            List<ActivoAdicional> previos = adicionalDao.findByTokenRegistro(token);
            if (!previos.isEmpty()) {
                log.warn("[ADICIONAL] Reintento de un alta ya hecha (token repetido): se devuelven {} activo(s)", previos.size());
                return previos.stream().map(x -> resumen(x.getActivo())).toList();
            }
        }
        String prefijo = d.prefijo();
        int siguiente = prefijo == null ? 0 : adicionalDao.maxCorrelativoAdicional(prefijo) + 1;
        if (prefijo != null && siguiente + d.cantidad - 1 > MAX_CORRELATIVO) {
            throw new IllegalStateException("La serie de adicionales del prefijo " + prefijo + " llegó a 99999A.");
        }
        LocalDate hoy = LocalDate.now();
        List<Map<String, Object>> out = new ArrayList<>();
        List<HistorialActivo> historiales = new ArrayList<>();
        for (int u = 0; u < d.cantidad; u++) {
            Activo a = new Activo();
            a.setProcedencia(Activo.PROCEDENCIA_ADICIONAL);   // antes que el sinc: ver setSincVsiaf
            a.setCodigo(prefijo == null ? null : prefijo + "-" + String.format("%05d", siguiente++) + "A");
            a.setEstado(Activo.ESTADO_ACTIVO);
            a.setApiEstado((short) 1);
            a.setVidaUtilAnterior(0);
            a.setCostoAnterior(0.0);
            a.setDepreciacionAcum(0.0);
            a.setUsuario(usuario.getUsuario());
            a.setSincVsiaf(Activo.SINC_NO_APLICA);
            a.setSincVsiafMensaje("Activo adicional: existe solo en el SCIAF, no se registra en el VSIAF.");
            a.setSincVsiafFecha(LocalDateTime.now());
            ActivoAdicional x = new ActivoAdicional();
            x.setActivo(a);
            x.setTokenRegistro(token);
            x.setUsuario(usuario.getUsuario());
            x.setEstado("ACTIVO");
            volcar(a, x, d, d.serie(u), d.codigoAntiguo(u), d.estado(u), usuario.getUsuario(), hoy, true);
            activoDao.save(a);
            adicionalDao.save(x);
            historiales.add(historialAlta(a, usuario));
            out.add(resumen(a));
        }
        guardarHistorialAlConfirmar(historiales);
        log.info("[ADICIONAL] {} activo(s) adicional(es) registrados por {} ({}).", d.cantidad, usuario.getUsuario(),
                prefijo == null ? "sin código: falta predio o grupo" : "prefijo " + prefijo);
        return out;
    }

    /** Copia los datos validados al activo y a su detalle. */
    private static void volcar(Activo a, ActivoAdicional x, Datos d, String serie, String codigoAntiguo, EstadoActivo estado,
                               String usuario, LocalDate hoy, boolean moverUbicacion) {
        a.setDescripcion(d.descripcionCon(serie, codigoAntiguo));
        a.setCodigoAntiguo(codigoAntiguo);
        a.setGrupoContable(d.grupo);
        if (d.auxiliar != null) a.setAuxiliar(null);
        x.setAuxiliarAdicional(d.auxiliar);
        a.setEstadoActivo(estado);
        a.setOrganismoFinanciero(d.financiador);
        a.setOrgFinCode(d.financiador != null ? d.financiador.getCodOf() : null);
        a.setFechaAdquisicion(d.fecha);
        // Sin dato, 0: otros módulos (actas, reportes) suman o formatean costo y vida útil sin
        // comprobar que existan.
        a.setCosto(d.valor != null ? d.valor : 0.0);
        a.setVidaUtil(BigDecimal.valueOf(d.vidaUtil != null ? d.vidaUtil : 0));
        a.setObserv("ACTIVO ADICIONAL" + (codigoAntiguo != null ? " · CÓD. ANTIGUO " + codigoAntiguo : ""));
        a.setFecMod(hoy);
        a.setFechaUlt(hoy);
        a.setUsuMod(usuario);
        if (moverUbicacion) {
            a.setOficina(d.oficina);
            a.setResponsable(d.responsable);
            x.setPredio(d.oficina == null ? d.predio : null);
        } else if (a.getOficina() == null) {
            x.setPredio(d.predio);   // sin oficina, el predio no es «ubicación movida»: forma el código
        }
        x.setDescripcionBase(d.base);
        x.setMarca(d.marca);
        x.setModelo(d.modelo);
        x.setSerie(serie);
        x.setColor(d.color);
        x.setMaterial(d.material);
        x.setDimensiones(d.dimensiones);
        x.setCaracteristicas(d.caracteristicas);
        x.setObservacion(d.observacion);
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Corregir
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Corrige un activo adicional. Completar el predio y el grupo le asigna su código; cambiarlos
     * le da un código nuevo (otro prefijo). Cambiar el código o la ubicación solo si el bien
     * todavía no tuvo vida propia (asignación, transferencia, levantamiento…): después se
     * mueve con esos módulos. Los demás datos se corrigen siempre.
     */
    @Transactional
    public Map<String, Object> editar(Long idActivo, AdicionalRequest req, Usuario usuario, Object opciones) {
        exigirPermiso(usuario, opciones);
        if (req != null) req.setCantidad(1);
        adicionalDao.turno(TURNO_ADICIONALES);
        adicionalDao.bloquearActivo(idActivo);
        ActivoAdicional x = adicionalDao.buscarCompleto(idActivo)
                .orElseThrow(() -> new IllegalArgumentException("El activo adicional no existe."));
        Activo a = x.getActivo();
        if (!Activo.ESTADO_ACTIVO.equals(a.getEstado())) throw new IllegalArgumentException("El activo ya no está vigente (" + a.getEstado() + ").");
        Datos d = validar(req, a);
        boolean conVida = adicionalDao.contarVida(idActivo) > 0 || a.enCustodiaODuplicados() || Boolean.TRUE.equals(a.getBloqueado());

        boolean cambiaUbicacion = !mismoId(a.getOficina() == null ? null : a.getOficina().getIdOficina(), d.oficina == null ? null : d.oficina.getIdOficina())
                || !mismoId(a.getResponsable() == null ? null : a.getResponsable().getIdResponsable(), d.responsable == null ? null : d.responsable.getIdResponsable());
        if (cambiaUbicacion && conVida) {
            throw new IllegalArgumentException("El activo ya tiene movimientos: su oficina y responsable se cambian con Transferencias o Asignaciones.");
        }
        String prefijo = d.prefijo();
        String actual = a.getCodigo();
        String prefijoActual = actual == null ? null : actual.substring(0, actual.lastIndexOf('-'));
        String nuevoCodigo = actual;
        if (prefijo == null && actual != null) {
            throw new IllegalArgumentException("El activo ya tiene código (" + actual + "): no se le puede quitar el predio ni el grupo contable.");
        }
        if (prefijo != null && !prefijo.equals(prefijoActual)) {
            if (actual != null && conVida) {
                throw new IllegalArgumentException("El activo ya tiene movimientos: no se puede cambiar su predio ni su grupo contable (cambiaría su código).");
            }
            int n = adicionalDao.maxCorrelativoAdicional(prefijo) + 1;
            if (n > MAX_CORRELATIVO) throw new IllegalStateException("La serie de adicionales del prefijo " + prefijo + " llegó a 99999A.");
            nuevoCodigo = prefijo + "-" + String.format("%05d", n) + "A";
        }
        a.setCodigo(nuevoCodigo);
        volcar(a, x, d, d.serie(0), d.codigoAntiguo(0), d.estado(0), usuario.getUsuario(), LocalDate.now(), !conVida);
        if (!java.util.Objects.equals(actual, nuevoCodigo)) {
            activoDao.flush();
            adicionalDao.recodificarHistorialAlta(idActivo, Activo.codigoReferencia(a));
        }
        log.info("[ADICIONAL] Activo {} corregido por {}{}", idActivo, usuario.getUsuario(),
                java.util.Objects.equals(actual, nuevoCodigo) ? "" : " (código " + actual + " → " + nuevoCodigo + ")");
        Map<String, Object> out = resumen(a);
        out.put("codigoAnterior", actual);
        return out;
    }

    private static boolean mismoId(Long a, Long b) { return java.util.Objects.equals(a, b); }

    // ════════════════════════════════════════════════════════════════════════
    //  Eliminar (un registro equivocado que nadie usó)
    // ════════════════════════════════════════════════════════════════════════

    /** Borra el activo y su detalle (libera su código). Solo administradores y si no tuvo vida propia. */
    @Transactional
    public String eliminar(Long idActivo, Usuario usuario) {
        if (usuario == null || !RolesSciaf.esAdministrativo(usuario)) {
            throw new SecurityException("Solo un ADMINISTRADOR o SUPER USUARIO puede eliminar un activo adicional.");
        }
        adicionalDao.turno(TURNO_ADICIONALES);
        adicionalDao.bloquearActivo(idActivo);
        ActivoAdicional x = adicionalDao.buscarCompleto(idActivo)
                .orElseThrow(() -> new IllegalArgumentException("El activo adicional no existe."));
        Activo a = x.getActivo();
        if (adicionalDao.contarVida(idActivo) > 0 || a.enCustodiaODuplicados() || Boolean.TRUE.equals(a.getBloqueado())) {
            throw new IllegalArgumentException("El activo ya tiene movimientos (asignación, transferencia, levantamiento, faltante o baja): no se puede eliminar.");
        }
        String ref = a.getCodigo() != null ? a.getCodigo() : (a.getCodigoAntiguo() != null ? "cód. antiguo " + a.getCodigoAntiguo() : "id " + idActivo);
        adicionalDao.delete(x);
        adicionalDao.flush();
        fotoDao.soltarDeActivo(idActivo);
        adicionalDao.borrarHistorialAlta(idActivo);
        activoDao.delete(a);
        activoDao.flush();
        log.warn("[ADICIONAL] Activo adicional {} ELIMINADO por {}.", ref, usuario.getUsuario());
        return ref;
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Consulta
    // ════════════════════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public Map<String, Object> tabla(int draw, int start, int length, String q, Usuario usuario, Object opciones) {
        exigirPermiso(usuario, opciones);
        int tam = length <= 0 ? 100 : Math.min(length, 100);
        String filtro = q == null || q.isBlank() ? "" : "%" + q.trim().toUpperCase() + "%";
        Page<ActivoAdicional> p = adicionalDao.listar(filtro, PageRequest.of(Math.max(0, start) / tam, tam));
        List<Long> ids = p.getContent().stream().map(x -> x.getActivo().getIdActivo()).toList();
        Map<Long, Long> fotos = new HashMap<>();
        if (!ids.isEmpty()) for (Object[] f : fotoDao.contarPorActivo(ids)) fotos.put(((Number) f[0]).longValue(), ((Number) f[1]).longValue());
        List<Map<String, Object>> filas = new ArrayList<>();
        for (ActivoAdicional x : p.getContent()) {
            Activo a = x.getActivo();
            Map<String, Object> m = resumen(a);
            m.put("descripcion", a.getDescripcion());
            m.put("grupo", a.getGrupoContable() != null ? a.getGrupoContable().getNombre() : null);
            m.put("oficina", a.getOficina() != null ? etiquetaOficina(a.getOficina()) : null);
            m.put("predio", a.getOficina() != null && a.getOficina().getPredio() != null ? a.getOficina().getPredio().getDescrip()
                    : x.getPredio() != null ? x.getPredio().getDescrip() : null);
            m.put("responsable", nombreDe(a.getResponsable()));
            m.put("valor", a.getCosto());
            m.put("estado", a.getEstado());
            m.put("fotos", fotos.getOrDefault(a.getIdActivo(), 0L));
            filas.add(m);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("draw", draw);
        out.put("recordsTotal", filtro.isEmpty() ? p.getTotalElements() : adicionalDao.contarTodos());
        out.put("recordsFiltered", p.getTotalElements());
        out.put("data", filas);
        out.put("totales", totales());
        return out;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> totales() {
        List<Object[]> t = adicionalDao.totales();
        Object[] f = t.isEmpty() ? new Object[] { 0L, 0L, 0d } : t.get(0);
        Map<String, Object> out = new LinkedHashMap<>();
        long total = ((Number) f[0]).longValue(), conCodigo = ((Number) f[1]).longValue();
        out.put("total", total);
        out.put("conCodigo", conCodigo);
        out.put("sinCodigo", total - conCodigo);
        out.put("valor", Math.round(((Number) f[2]).doubleValue() * 100.0) / 100.0);
        return out;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> detalle(Long idActivo, Usuario usuario, Object opciones) {
        exigirPermiso(usuario, opciones);
        ActivoAdicional x = adicionalDao.buscarCompleto(idActivo)
                .orElseThrow(() -> new IllegalArgumentException("El activo adicional no existe."));
        Activo a = x.getActivo();
        Map<String, Object> m = resumen(a);
        Predio predio = a.getOficina() != null ? a.getOficina().getPredio() : x.getPredio();
        m.put("descripcion", a.getDescripcion());
        m.put("descripcionBase", x.getDescripcionBase());
        m.put("marca", x.getMarca());
        m.put("modelo", x.getModelo());
        m.put("serie", x.getSerie());
        m.put("color", x.getColor());
        m.put("material", x.getMaterial());
        m.put("dimensiones", x.getDimensiones());
        m.put("caracteristicas", x.getCaracteristicas());
        m.put("observacion", x.getObservacion());
        m.put("idPredio", predio != null ? predio.getIdPredio() : null);
        m.put("predio", predio != null ? predio.getDescrip() : null);
        m.put("idOficina", a.getOficina() != null ? a.getOficina().getIdOficina() : null);
        m.put("oficina", a.getOficina() != null ? etiquetaOficina(a.getOficina()) : null);
        m.put("idResponsable", a.getResponsable() != null ? a.getResponsable().getIdResponsable() : null);
        m.put("responsable", nombreDe(a.getResponsable()));
        m.put("idGrupoContable", a.getGrupoContable() != null ? a.getGrupoContable().getIdGrupoContable() : null);
        m.put("grupo", a.getGrupoContable() != null ? a.getGrupoContable().getNombre() : null);
        m.put("idAuxiliarAdicional", x.getAuxiliarAdicional() != null ? x.getAuxiliarAdicional().getIdAuxiliarAdicional() : null);
        m.put("auxiliar", x.getAuxiliarAdicional() != null ? x.getAuxiliarAdicional().getNombre()
                : a.getAuxiliar() != null ? a.getAuxiliar().getNombre() : null);
        m.put("auxiliarLegado", x.getAuxiliarAdicional() == null && a.getAuxiliar() != null);
        m.put("idEstadoActivo", a.getEstadoActivo() != null ? a.getEstadoActivo().getIdEstadoActivo() : null);
        m.put("conservacion", a.getEstadoActivo() != null ? a.getEstadoActivo().getNombre() : null);
        m.put("idOrganismoFinanciero", a.getOrganismoFinanciero() != null ? a.getOrganismoFinanciero().getIdOrganismoFinanciero() : null);
        m.put("financiador", a.getOrganismoFinanciero() != null ? a.getOrganismoFinanciero().getDescripcion() : null);
        m.put("fechaAdquisicion", a.getFechaAdquisicion() != null ? a.getFechaAdquisicion().toString() : null);
        m.put("valor", a.getCosto());
        m.put("vidaUtil", a.getVidaUtil() != null ? a.getVidaUtil().intValue() : null);
        m.put("estado", a.getEstado());
        m.put("registradoPor", x.getUsuario());
        m.put("registro", x.getRegistro());
        boolean conVida = adicionalDao.contarVida(idActivo) > 0 || a.enCustodiaODuplicados() || Boolean.TRUE.equals(a.getBloqueado());
        m.put("conVida", conVida);
        m.put("eliminable", !conVida && Activo.ESTADO_ACTIVO.equals(a.getEstado()));
        List<Map<String, Object>> fotos = new ArrayList<>();
        for (ActivoFoto f : fotoDao.findByActivoIdActivoAndEstadoOrderByIdFotoAsc(idActivo, "ACTIVO")) {
            fotos.add(Map.of("id", f.getIdFoto(), "url", f.getRuta()));
        }
        m.put("fotos", fotos);
        m.put("maxFotos", MAX_FOTOS);
        return m;
    }

    /** Qué código tomaría un bien con este predio y grupo (orientativo, no reserva nada). */
    @Transactional(readOnly = true)
    public Map<String, Object> previsualizar(Long idPredio, Long idOficina, Long idGrupo, int cantidad, Usuario usuario, Object opciones) {
        exigirPermiso(usuario, opciones);
        Predio predio = null;
        if (idOficina != null) {
            Oficina o = oficinaService.findById(idOficina);
            predio = o != null ? o.getPredio() : null;
        } else if (idPredio != null) {
            predio = predioService.findById(idPredio);
        }
        Datos d = new Datos();
        d.predio = predio;
        d.grupo = idGrupo == null ? null : grupoService.findById(idGrupo);
        String prefijo = d.prefijo();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        if (prefijo == null) {
            out.put("conCodigo", false);
            out.put("motivo", predio == null ? "sin predio" : d.grupo == null ? "sin grupo contable" : "predio sin configurar");
            return out;
        }
        int n = adicionalDao.maxCorrelativoAdicional(prefijo) + 1;
        int c = Math.max(1, Math.min(TOPE_UNIDADES, cantidad));
        out.put("conCodigo", true);
        out.put("desde", prefijo + "-" + String.format("%05d", n) + "A");
        out.put("hasta", prefijo + "-" + String.format("%05d", n + c - 1) + "A");
        return out;
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Fotos (hasta 3 por activo)
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Agrega fotos a uno o varios activos (tras un alta de N unidades, las de la tarjeta van a
     * cada una: el archivo se guarda una vez y cada activo tiene su fila).
     */
    @Transactional
    public List<Map<String, Object>> subirFotos(List<Long> idsActivo, List<MultipartFile> archivosSubidos, Usuario usuario, Object opciones) {
        exigirPermiso(usuario, opciones);
        List<MultipartFile> lista = archivosSubidos == null ? List.of() : archivosSubidos.stream().filter(f -> f != null && !f.isEmpty()).toList();
        if (lista.isEmpty()) throw new IllegalArgumentException("No llegó ninguna imagen.");
        if (idsActivo == null || idsActivo.isEmpty()) throw new IllegalArgumentException("Indique a qué activo van las fotos.");
        adicionalDao.turno(TURNO_ADICIONALES);
        List<Activo> activos = new ArrayList<>();
        for (Long id : new java.util.LinkedHashSet<>(idsActivo)) {
            ActivoAdicional x = adicionalDao.findFirstByActivoIdActivo(id)
                    .orElseThrow(() -> new IllegalArgumentException("El activo " + id + " no es un activo adicional."));
            long ya = fotoDao.countByActivoIdActivoAndEstado(id, "ACTIVO");
            if (ya + lista.size() > MAX_FOTOS) {
                throw new IllegalArgumentException("Cada activo admite hasta " + MAX_FOTOS + " fotos; uno de ellos ya tiene " + ya + ".");
            }
            activos.add(x.getActivo());
        }
        for (MultipartFile f : lista) {
            if (f.getSize() > MAX_BYTES_FOTO) throw new IllegalArgumentException("La foto «" + f.getOriginalFilename() + "» pasa de 6 MB.");
            if (!esImagen(f)) throw new IllegalArgumentException("«" + f.getOriginalFilename() + "» no es una imagen JPG, PNG o WEBP.");
        }
        List<String> escritas = new ArrayList<>();
        borrarSiSeDeshace(escritas);
        List<Map<String, Object>> out = new ArrayList<>();
        for (MultipartFile f : lista) {
            String sufijo = java.util.UUID.randomUUID().toString().substring(0, 8);
            String ruta = archivos.guardar(f, "adicionales/fotos", "ADI_" + activos.get(0).getIdActivo() + "_" + sufijo);
            escritas.add(ruta);
            String nombre = f.getOriginalFilename();
            for (Activo a : activos) {
                ActivoFoto foto = new ActivoFoto();
                foto.setActivo(a);
                foto.setRuta(ruta);
                foto.setNombreOriginal(nombre != null && nombre.length() > 200 ? nombre.substring(0, 200) : nombre);
                foto.setUsuario(usuario.getUsuario());
                foto.setEstado("ACTIVO");
                fotoDao.save(foto);
                out.add(Map.of("id", foto.getIdFoto(), "url", ruta, "idActivo", a.getIdActivo()));
            }
        }
        return out;
    }

    @Transactional
    public void quitarFoto(Long idFoto, Usuario usuario, Object opciones) {
        exigirPermiso(usuario, opciones);
        ActivoFoto f = fotoDao.findById(idFoto).orElseThrow(() -> new IllegalArgumentException("La foto no existe."));
        if (f.getActivo() == null || adicionalDao.findFirstByActivoIdActivo(f.getActivo().getIdActivo()).isEmpty()) {
            throw new IllegalArgumentException("Esa foto no es de un activo adicional.");
        }
        f.setEstado(ELIMINADO);   // el archivo queda: puede compartirlo otro activo del mismo alta
    }

    /** Si la transacción se deshace, borra del disco los archivos que ya se escribieron. */
    private static void borrarSiSeDeshace(List<String> rutas) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int estado) {
                if (estado == STATUS_COMMITTED) return;
                for (String ruta : rutas) {
                    try {
                        java.nio.file.Path p = java.nio.file.Paths.get(ruta.replaceFirst("^/", "")).toAbsolutePath().normalize();
                        if (p.startsWith(java.nio.file.Paths.get("uploads").toAbsolutePath().normalize())) java.nio.file.Files.deleteIfExists(p);
                    } catch (Exception e) {
                        log.warn("[ADICIONAL] No se pudo borrar la foto huérfana {}: {}", ruta, e.getMessage());
                    }
                }
            }
        });
    }

    /** Firma de los primeros bytes: JPEG, PNG o WEBP (y la extensión del nombre). */
    private static boolean esImagen(MultipartFile f) {
        String n = f.getOriginalFilename() == null ? "" : f.getOriginalFilename().toLowerCase();
        if (!n.matches(".*\\.(jpe?g|png|webp)$")) return false;
        try (var in = f.getInputStream()) {
            byte[] b = in.readNBytes(12);
            if (b.length < 4) return false;
            boolean jpg = (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF;
            boolean png = (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G';
            boolean webp = b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                    && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P';
            return jpg || png || webp;
        } catch (java.io.IOException e) {
            return false;
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Apoyo
    // ════════════════════════════════════════════════════════════════════════

    // ════════════════════════════════════════════════════════════════════════
    //  Etiquetas con QR
    // ════════════════════════════════════════════════════════════════════════

    /**
     * PDF de etiquetas de los adicionales pedidos, ordenados por código. Solo llevan etiqueta los
     * vigentes con código (el QR lleva el código, con su A); los demás se saltan.
     */
    @Transactional(readOnly = true)
    public byte[] etiquetas(List<Long> ids, Usuario usuario, Object opciones) {
        exigirPermiso(usuario, opciones);
        Set<Long> pedidos = ids == null ? Set.of() : new java.util.LinkedHashSet<>(ids);
        if (pedidos.isEmpty()) throw new IllegalArgumentException("Marque al menos un activo adicional con código.");
        if (pedidos.size() > MAX_ETIQUETAS) {
            throw new IllegalArgumentException("Hasta " + MAX_ETIQUETAS + " etiquetas por impresión; marcó " + pedidos.size() + ".");
        }
        List<ActivoAdicional> vigentes = adicionalDao.buscarVarios(pedidos).stream()
                .filter(x -> Activo.ESTADO_ACTIVO.equals(x.getActivo().getEstado()) && x.getActivo().getCodigo() != null)
                .toList();
        List<PdfEtiquetasQrService.Etiqueta> etiquetas = vigentes.stream()
                .sorted(java.util.Comparator.comparing(x -> x.getActivo().getCodigo()))
                .map(x -> {
                    Activo a = x.getActivo();
                    Predio predio = a.getOficina() != null && a.getOficina().getPredio() != null ? a.getOficina().getPredio() : x.getPredio();
                    String pie = a.getCodigoAntiguo() != null ? "ANT. " + PdfEtiquetasQrService.limpio(a.getCodigoAntiguo()) : null;
                    return PdfEtiquetasQrService.deActivo(a, predio, "ADICIONAL", pie);
                })
                .toList();
        if (etiquetas.isEmpty()) {
            throw new IllegalArgumentException("Ninguno de los marcados está vigente y con código: no hay etiquetas que imprimir.");
        }
        // Si algún marcado se quedó fuera (otro usuario lo eliminó o le quitó el código), la hoja lo dice:
        // quien pega etiquetas no debe creer que salieron todas.
        int saltados = pedidos.size() - etiquetas.size();
        String aviso = saltados > 0 ? "Atención: " + saltados + " de los " + pedidos.size()
                + " marcados no salen (ya no están vigentes o no tienen código)." : null;
        return hojaEtiquetas.generar(etiquetas, aviso);
    }

    /** Predios para la vista, con el prefijo (o null si falta configurarlo). */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> predios() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Predio p : predioService.findAll()) {
            Map<String, Object> m = new LinkedHashMap<>();
            String mun = p.getMunicipio() != null ? limpiar(p.getMunicipio().getCodigo()) : null;
            String pred = limpiar(p.getCodigo());
            m.put("id", p.getIdPredio());
            m.put("nombre", p.getDescrip());
            m.put("prefijo", mun != null && pred != null ? mun + "-" + pred : null);
            out.add(m);
        }
        out.sort((a, b) -> String.valueOf(a.get("nombre")).compareToIgnoreCase(String.valueOf(b.get("nombre"))));
        return out;
    }

    private static Map<String, Object> resumen(Activo a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("idActivo", a.getIdActivo());
        m.put("codigo", a.getCodigo());
        m.put("codigoAntiguo", a.getCodigoAntiguo());
        return m;
    }

    private HistorialActivo historialAlta(Activo a, Usuario usuario) {
        HistorialActivo h = new HistorialActivo();
        h.setActivo(a);
        h.setCodigoActivo(Activo.codigoReferencia(a));
        // historial_activo tiene un CHECK con los tipos permitidos: REGISTRO es uno de ellos.
        h.setTipoEvento("REGISTRO");
        h.setFechaEvento(LocalDateTime.now());
        h.setIdUsuario(usuario.getIdUsuario());
        h.setNombreUsuario(usuario.getUsuario());
        h.setDescripcionEvento("Alta como activo adicional" + (a.getCodigoAntiguo() != null ? " (código antiguo " + a.getCodigoAntiguo() + ")" : "")
                + ". Bien solo del SCIAF: no se registra en el VSIAF.");
        if (a.getOficina() != null) {
            h.setOficinaNueva(a.getOficina());
            h.setNombreOficinaNueva(a.getOficina().getNombre());
        }
        if (a.getResponsable() != null) {
            h.setResponsableNuevo(a.getResponsable());
            h.setNombreRespNuevo(nombreDe(a.getResponsable()));
        }
        return h;
    }

    /** Después de confirmar y en su propia transacción: si el historial falla, el alta queda. */
    private void guardarHistorialAlConfirmar(List<HistorialActivo> historiales) {
        if (historiales.isEmpty()) return;
        Runnable guardar = () -> {
            try {
                TransactionTemplate tx = new TransactionTemplate(txManager);
                tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                tx.executeWithoutResult(s -> historialDao.saveAll(historiales));
            } catch (Exception e) {
                log.warn("[ADICIONAL] No se pudo guardar el historial de {} activo(s): {}", historiales.size(), e.getMessage());
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { guardar.run(); }
            });
        } else {
            guardar.run();
        }
    }

    private static String etiquetaOficina(Oficina o) {
        return (o.getCodOfi() != null ? String.format("%03d", o.getCodOfi()) + " - " : "") + o.getNombre();
    }

    private static String nombreDe(Responsable r) {
        if (r == null) return null;
        Persona p = r.getPersona();
        String nombre = p != null ? p.getNombreCompleto() : null;
        return (r.getCodigoFuncionario() != null ? r.getCodigoFuncionario() + " — " : "") + (nombre != null ? nombre : "");
    }

    private static String limpiar(String s) {
        if (s == null) return null;
        String t = s.trim().replaceAll("\\s+", " ");
        return t.isEmpty() ? null : t;
    }

    private static String mayus(String s) {
        String t = limpiar(s);
        return t == null ? null : t.toUpperCase();
    }

    private static String texto(String s) {
        if (s == null) return null;
        String t = s.strip();
        return t.isEmpty() ? null : t;
    }

    private static void exigirLargo(String s, int max, String que) {
        if (s != null && s.length() > max) throw new IllegalArgumentException(que + " admite hasta " + max + " caracteres (tiene " + s.length() + ").");
    }
}
