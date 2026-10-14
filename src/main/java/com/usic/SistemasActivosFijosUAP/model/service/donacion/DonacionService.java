package com.usic.SistemasActivosFijosUAP.model.service.donacion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.usic.SistemasActivosFijosUAP.config.RolesSciaf;
import com.usic.SistemasActivosFijosUAP.model.IService.IAuxiliarService;
import com.usic.SistemasActivosFijosUAP.model.IService.IEstadoActivoService;
import com.usic.SistemasActivosFijosUAP.model.IService.IGrupoContableService;
import com.usic.SistemasActivosFijosUAP.model.IService.IOficinaService;
import com.usic.SistemasActivosFijosUAP.model.IService.IOrganismoFinancieroService;
import com.usic.SistemasActivosFijosUAP.model.IService.IPredioServicio;
import com.usic.SistemasActivosFijosUAP.model.IService.IResponsableService;
import com.usic.SistemasActivosFijosUAP.model.dao.IActivoDao;
import com.usic.SistemasActivosFijosUAP.model.dao.IDonacionActaDao;
import com.usic.SistemasActivosFijosUAP.model.dao.IDonacionBienDao;
import com.usic.SistemasActivosFijosUAP.model.dao.IHistorialActivoDao;
import com.usic.SistemasActivosFijosUAP.model.dto.donacion.DonacionRegistroRequest;
import com.usic.SistemasActivosFijosUAP.model.entity.Activo;
import com.usic.SistemasActivosFijosUAP.model.entity.Auxiliar;
import com.usic.SistemasActivosFijosUAP.model.entity.DonacionActa;
import com.usic.SistemasActivosFijosUAP.model.entity.DonacionBien;
import com.usic.SistemasActivosFijosUAP.model.entity.EstadoActivo;
import com.usic.SistemasActivosFijosUAP.model.entity.GrupoContable;
import com.usic.SistemasActivosFijosUAP.model.entity.HistorialActivo;
import com.usic.SistemasActivosFijosUAP.model.entity.Oficina;
import com.usic.SistemasActivosFijosUAP.model.entity.OrganismoFinanciero;
import com.usic.SistemasActivosFijosUAP.model.entity.Persona;
import com.usic.SistemasActivosFijosUAP.model.entity.Predio;
import com.usic.SistemasActivosFijosUAP.model.entity.Responsable;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Registro de bienes donados (Administración de activos → Registro de donaciones).
 * <p>
 * Un bien donado es un {@link Activo} común con tres diferencias:
 * <ul>
 *   <li>su correlativo empieza con D y lleva su propia serie por prefijo
 *       {@code MUN-PRED-GRP}: {@code 01-04-06-D00001, D00002…}. No toca la serie normal
 *       (el generador del VSIAF solo cuenta correlativos de 5 dígitos);</li>
 *   <li>queda ACTIVO al registrarse, sin pasar por Pendientes, y con
 *       {@code sincVsiaf = NO_APLICA}: no se registra en el VSIAF. La barrera está en
 *       {@code ActualDbfWriterService}, así que asignarlo o transferirlo después lo mueve
 *       en el SCIAF sin mandar nada al VSIAF;</li>
 *   <li>pertenece a un {@link DonacionActa} (donante, documento, fecha), y su detalle
 *       completo queda en {@link DonacionBien}.</li>
 * </ul>
 * Todo el alta es una transacción bajo un turno {@code pg_advisory_xact_lock}: dos
 * registros simultáneos no sacan el mismo número de acta ni el mismo código D.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DonacionService {

    /** Clave del turno (pg_advisory_xact_lock) de las altas de donaciones. */
    private static final long TURNO_DONACIONES = 0x5C1AFD0A1L;
    /** Tope de unidades por acta y por bien: más que esto es una importación, no un acta. */
    private static final int TOPE_UNIDADES_ACTA = 500;
    private static final int TOPE_UNIDADES_BIEN = 200;
    private static final int MAX_DESCRIPCION = 1024;
    private static final int MAX_CORRELATIVO = 99_999;
    private static final long ESTADO_ACTIVO_POR_DEFECTO = 1L;
    private static final String ELIMINADO = "ELIMINADO";

    private final IDonacionActaDao actaDao;
    private final IDonacionBienDao bienDao;
    private final IActivoDao activoDao;
    private final IHistorialActivoDao historialDao;
    private final IOficinaService oficinaService;
    private final IResponsableService responsableService;
    private final IGrupoContableService grupoService;
    private final IAuxiliarService auxiliarService;
    private final IOrganismoFinancieroService financiadorService;
    private final IEstadoActivoService estadoActivoService;
    private final IPredioServicio predioService;
    private final PlatformTransactionManager txManager;
    private final com.usic.SistemasActivosFijosUAP.model.dao.IDonacionFotoDao fotoDao;
    private final com.usic.SistemasActivosFijosUAP.model.service.ArchivoStorageService archivos;

    /** Fotos por ítem del acta (las comparten todas sus unidades). */
    public static final int MAX_FOTOS_ITEM = 3;
    /** Tope por foto: la pantalla ya las reduce a ~1600 px antes de subir. */
    private static final long MAX_BYTES_FOTO = 6L * 1024 * 1024;

    /** Resultado del alta: el acta y los códigos que se generaron, por bien. */
    /** {@code repetido}: el token ya se había usado y se devolvió el acta registrada antes. */
    public record Resultado(Long idDonacion, String numero, int cantidad, List<Map<String, Object>> bienes, boolean repetido) {}

    // ════════════════════════════════════════════════════════════════════════
    //  Registro
    // ════════════════════════════════════════════════════════════════════════

    /** Opción de menú que habilita el módulo. */
    public static final String PERMISO = "opcion_donacion";

    /**
     * ¿Puede registrar donaciones? ADMINISTRADOR / SUPER USUARIO o la opción de menú. La
     * autorización se valida aquí (y no solo en el controlador): casi todo
     * {@code /administracion/**} es {@code permitAll()} en la capa HTTP.
     */
    public static boolean puede(Usuario usuario, Object opcionesSesion) {
        if (usuario == null || !"ACTIVO".equalsIgnoreCase(usuario.getEstado())) return false;
        if (RolesSciaf.esAdministrativo(usuario)) return true;
        return opcionesSesion instanceof java.util.Set<?> set && set.contains(PERMISO);
    }

    @Transactional
    public Resultado registrar(DonacionRegistroRequest req, Usuario usuario, Object opcionesSesion) {
        if (!puede(usuario, opcionesSesion)) {
            throw new SecurityException("No tiene permiso para registrar donaciones (opción «Registro de Donaciones»).");
        }
        String usuarioNombre = usuario.getUsuario();
        Long idUsuario = usuario.getIdUsuario();
        if (req == null) throw new IllegalArgumentException("No llegaron datos.");
        String token = limpiar(req.getToken());
        if (token != null && token.length() > 40) throw new IllegalArgumentException("Marca de registro inválida.");

        Cabecera c = validarCabecera(req, null);

        // ── 3. Bienes ──
        List<DonacionRegistroRequest.Bien> bienes = req.getBienes();
        if (bienes == null || bienes.isEmpty()) throw new IllegalArgumentException("Agregue al menos un bien.");
        List<BienValidado> validados = new ArrayList<>();
        int totalUnidades = 0;
        double valorTotal = 0;
        for (int i = 0; i < bienes.size(); i++) {
            BienValidado b = validarBien(bienes.get(i), i + 1, c.predio());
            validados.add(b);
            totalUnidades += b.cantidad;
            valorTotal += b.valor * b.cantidad;
        }
        if (totalUnidades > TOPE_UNIDADES_ACTA) {
            throw new IllegalArgumentException("Un acta admite hasta " + TOPE_UNIDADES_ACTA
                    + " unidades; esta tiene " + totalUnidades + ". Divídala en varias actas.");
        }

        // ── 4. Turno: desde aquí nadie más numera hasta que esta transacción termine ──
        actaDao.turno(TURNO_DONACIONES);

        // Reintento de un registro que ya se hizo (la respuesta se perdió): no se duplica.
        if (token != null) {
            DonacionActa previa = actaDao.findFirstByTokenRegistro(token).orElse(null);
            if (previa != null) {
                log.warn("[DONACION] Reintento del registro {} (token repetido): se devuelve el acta existente", previa.getNumero());
                return resultadoDe(previa, true);
            }
        }

        int gestion = c.fecha().getYear();
        int correlativoActa = actaDao.maxCorrelativo(gestion) + 1;
        DonacionActa acta = new DonacionActa();
        acta.setGestion(gestion);
        acta.setCorrelativo(correlativoActa);
        acta.setNumero(String.format("DON-%d-%04d", gestion, correlativoActa));
        aplicarCabecera(acta, c);
        acta.setCantidadBienes(totalUnidades);
        acta.setValorTotal(redondear(valorTotal));
        acta.setUsuario(usuarioNombre);
        acta.setTokenRegistro(token);
        acta.setEstado("ACTIVO");
        actaDao.save(acta);

        // ── 5. Activos con correlativo D ──
        Map<String, Integer> siguientePorPrefijo = new HashMap<>();
        List<Map<String, Object>> resumen = new ArrayList<>();
        List<HistorialActivo> historiales = new ArrayList<>();
        LocalDate hoy = LocalDate.now();
        int item = 0;
        for (BienValidado b : validados) {
            item++;
            List<String> codigos = new ArrayList<>();
            for (int u = 0; u < b.cantidad; u++) {
                String codigo = siguienteCodigo(siguientePorPrefijo, prefijoDe(c, b));
                Activo a = crearUnidad(acta, c, b, codigo, b.serie(u), item, usuarioNombre, hoy);
                historiales.add(historialAlta(a, acta, c.oficina(), c.responsable(), nombreDe(c.responsable()), usuarioNombre, idUsuario));
                codigos.add(codigo);
            }
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("item", item);
            r.put("descripcion", b.base);
            r.put("cantidad", b.cantidad);
            r.put("codigos", codigos);
            resumen.add(r);
        }

        log.info("[DONACION] {} registrada por {}: {} bien(es), {} unidad(es), Bs {}",
                acta.getNumero(), usuarioNombre, validados.size(), totalUnidades, acta.getValorTotal());
        guardarHistorialAlConfirmar(historiales);
        return new Resultado(acta.getIdDonacion(), acta.getNumero(), totalUnidades, resumen, false);
    }

    /** Donante, documento y destino ya validados (comunes al alta y a la edición). */
    private record Cabecera(String tipo, String donante, String docDonante, String contacto, String respaldo,
                            LocalDate fecha, String observacion, Oficina oficina, Responsable responsable,
                            OrganismoFinanciero financiador, Predio predio, String codMun, String codPred) {}

    /**
     * @param actual al editar, el acta tal como está: si la oficina o el responsable no
     *               cambian, no se les vuelve a exigir que estén vigentes (si después se dieron de
     *               baja, el acta igual tiene que poder corregirse, por ejemplo el donante).
     */
    private Cabecera validarCabecera(DonacionRegistroRequest req, DonacionActa actual) {
        String tipo = mayus(req.getDonanteTipo());
        if (!DonacionActa.TIPO_PERSONA.equals(tipo) && !DonacionActa.TIPO_INSTITUCION.equals(tipo)) {
            throw new IllegalArgumentException("Indique si el donante es una persona o una institución.");
        }
        String donante = mayus(req.getDonanteNombre());
        if (donante == null) throw new IllegalArgumentException("Escriba el nombre del donante.");
        exigirLargo(donante, 200, "El nombre del donante");
        String docDonante = mayus(req.getDonanteDocumento());
        exigirLargo(docDonante, 30, "El C.I. / NIT");
        String contacto = limpiar(req.getDonanteContacto());   // puede ser un correo: sin mayúsculas
        exigirLargo(contacto, 120, "El contacto");
        String respaldo = mayus(req.getDocumentoRespaldo());
        exigirLargo(respaldo, 120, "El documento de respaldo");
        LocalDate fecha = req.getFechaDonacion();
        if (fecha == null) throw new IllegalArgumentException("Indique la fecha de la donación.");
        if (fecha.isAfter(LocalDate.now())) throw new IllegalArgumentException("La fecha de la donación no puede ser futura.");
        String observacion = texto(req.getObservacion());   // texto libre largo: sin mayúsculas
        exigirLargo(observacion, 4000, "La observación");

        boolean mismaOficina = actual != null && actual.getOficina() != null
                && actual.getOficina().getIdOficina().equals(req.getIdOficina());
        boolean mismoResp = mismaOficina && actual.getResponsable() != null
                && actual.getResponsable().getIdResponsable().equals(req.getIdResponsable());
        Oficina oficina = req.getIdOficina() == null ? null : oficinaService.findById(req.getIdOficina());
        if (oficina == null || (!mismaOficina && ELIMINADO.equalsIgnoreCase(oficina.getEstado()))) {
            throw new IllegalArgumentException("Elija la oficina donde quedan los bienes (la elegida no existe o está dada de baja).");
        }
        if (!mismaOficina && oficina.isEsCustodia()) {
            throw new IllegalArgumentException("La oficina " + oficina.getNombre()
                    + " es de faltantes (custodia): los bienes donados no pueden registrarse ahí.");
        }
        Responsable responsable = req.getIdResponsable() == null ? null : responsableService.findById(req.getIdResponsable());
        if (responsable == null || (!mismoResp && (ELIMINADO.equalsIgnoreCase(responsable.getEstado())
                || (responsable.getApiEstado() != null && responsable.getApiEstado() == 3)))) {
            throw new IllegalArgumentException("Elija el responsable que recibe los bienes (el elegido no existe o está inactivo).");
        }
        if (!mismoResp && (responsable.getOficina() == null
                || !responsable.getOficina().getIdOficina().equals(oficina.getIdOficina()))) {
            throw new IllegalArgumentException("El responsable elegido no pertenece a la oficina elegida.");
        }
        if (!mismoResp && responsable.isEsCustodia()) {
            throw new IllegalArgumentException("El responsable elegido es de custodia de faltantes.");
        }
        Predio predio = oficina.getPredio();
        String codMun = predio != null && predio.getMunicipio() != null ? limpiar(predio.getMunicipio().getCodigo()) : null;
        String codPred = predio != null ? limpiar(predio.getCodigo()) : null;
        if (codMun == null || codPred == null) {
            throw new IllegalArgumentException("El predio " + (predio != null ? predio.getDescrip() : "")
                    + " no tiene configurados el municipio y su código (forman el inicio del código del activo). "
                    + "Un administrador lo configura en Ámbito geográfico → Predio.");
        }
        OrganismoFinanciero financiador = req.getIdOrganismoFinanciero() == null ? null
                : financiadorService.findById(req.getIdOrganismoFinanciero());
        if (req.getIdOrganismoFinanciero() != null && (financiador == null || ELIMINADO.equalsIgnoreCase(financiador.getEstado()))) {
            throw new IllegalArgumentException("El financiador elegido no existe o está dado de baja.");
        }
        return new Cabecera(tipo, donante, docDonante, contacto, respaldo, fecha, observacion,
                oficina, responsable, financiador, predio, codMun, codPred);
    }

    private static void aplicarCabecera(DonacionActa acta, Cabecera c) {
        acta.setDonanteTipo(c.tipo());
        acta.setDonanteNombre(c.donante());
        acta.setDonanteDocumento(c.docDonante());
        acta.setDonanteContacto(c.contacto());
        acta.setDocumentoRespaldo(c.respaldo());
        acta.setFechaDonacion(c.fecha());
        acta.setObservacion(c.observacion());
        acta.setOficina(c.oficina());
        acta.setResponsable(c.responsable());
    }

    private static String prefijoDe(Cabecera c, BienValidado b) {
        return c.codMun() + "-" + c.codPred() + "-" + String.format("%02d", b.grupo.getCodDbf());
    }


    /** Siguiente código D libre del prefijo (lo lee de la base la primera vez y sigue en memoria). */
    private String siguienteCodigo(Map<String, Integer> siguientePorPrefijo, String prefijo) {
        int siguiente = siguientePorPrefijo.computeIfAbsent(prefijo, p -> actaDao.maxCorrelativoDonacion(p) + 1);
        if (siguiente > MAX_CORRELATIVO) {
            throw new IllegalStateException("La serie de donaciones del prefijo " + prefijo + " llegó a 99999D.");
        }
        siguientePorPrefijo.put(prefijo, siguiente + 1);
        return prefijo + "-" + String.format("%05d", siguiente) + "D";
    }

    /** Crea una unidad (activo + su detalle de donación). */
    private Activo crearUnidad(DonacionActa acta, Cabecera c, BienValidado b, String codigo, String serie, int item,
                               String usuarioNombre, LocalDate hoy) {
        Activo a = new Activo();
        a.setProcedencia(Activo.PROCEDENCIA_DONACION);   // antes que el sinc: ver setSincVsiaf
        a.setCodigo(codigo);
        a.setOficina(c.oficina());
        a.setResponsable(c.responsable());
        a.setEstado(Activo.ESTADO_ACTIVO);
        a.setApiEstado((short) 1);
        a.setVidaUtilAnterior(0);
        a.setCostoAnterior(0.0);
        a.setDepreciacionAcum(0.0);
        a.setUsuario(usuarioNombre);
        a.setSincVsiaf(Activo.SINC_NO_APLICA);
        a.setSincVsiafMensaje("Donación " + acta.getNumero() + ": existe solo en el SCIAF, no se registra en el VSIAF.");
        a.setSincVsiafFecha(LocalDateTime.now());
        DonacionBien d = new DonacionBien();
        d.setDonacion(acta);
        d.setActivo(a);
        d.setEstado("ACTIVO");
        volcarDatos(a, d, acta, c, b, serie, item, usuarioNombre, hoy);
        activoDao.save(a);
        bienDao.save(d);
        return a;
    }

    /** Copia los datos del bien (y de la cabecera) a una unidad nueva o existente. */
    private static void volcarDatos(Activo a, DonacionBien d, DonacionActa acta, Cabecera c, BienValidado b,
                                    String serie, int item, String usuarioNombre, LocalDate hoy) {
        a.setDescripcion(b.descripcionCon(serie));
        a.setFechaAdquisicion(c.fecha());
        a.setCosto(b.valor);
        a.setVidaUtil(BigDecimal.valueOf(b.vidaUtil));
        a.setGrupoContable(b.grupo);
        a.setAuxiliar(b.auxiliar);
        a.setOrganismoFinanciero(c.financiador());
        a.setOrgFinCode(c.financiador() != null ? c.financiador().getCodOf() : null);
        a.setEstadoActivo(b.estadoActivo);
        a.setObserv("DONACIÓN " + acta.getNumero() + " · " + c.donante());
        a.setFecMod(hoy);
        a.setFechaUlt(hoy);
        a.setUsuMod(usuarioNombre);
        d.setItem(item);
        d.setDescripcionBase(b.base);
        d.setMarca(b.marca);
        d.setModelo(b.modelo);
        d.setSerie(serie);
        d.setColor(b.color);
        d.setMaterial(b.material);
        d.setDimensiones(b.dimensiones);
        d.setCaracteristicas(b.caracteristicas);
        d.setValorReferencial(b.valor);
    }

    /** El resultado de un acta ya registrada, armado desde sus bienes. */
    private Resultado resultadoDe(DonacionActa acta, boolean repetido) {
        Map<Integer, Map<String, Object>> porItem = new LinkedHashMap<>();
        for (DonacionBien b : bienDao.listarDeActa(acta.getIdDonacion())) {
            Map<String, Object> r = porItem.computeIfAbsent(b.getItem(), k -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("item", k);
                m.put("descripcion", b.getDescripcionBase());
                m.put("cantidad", 0);
                m.put("codigos", new ArrayList<String>());
                return m;
            });
            r.put("cantidad", (Integer) r.get("cantidad") + 1);
            @SuppressWarnings("unchecked")
            List<String> codigos = (List<String>) r.get("codigos");
            codigos.add(b.getActivo().getCodigo());
        }
        return new Resultado(acta.getIdDonacion(), acta.getNumero(), acta.getCantidadBienes(), new ArrayList<>(porItem.values()), repetido);
    }

    /** Un bien del pedido ya validado y en mayúsculas. */
    private static final class BienValidado {
        GrupoContable grupo;
        Auxiliar auxiliar;
        EstadoActivo estadoActivo;
        String base, marca, modelo, color, material, dimensiones, caracteristicas;
        int cantidad, vidaUtil;
        double valor;
        /** Una serie por unidad, en orden; null = esa unidad no tiene serie. */
        List<String> series = new ArrayList<>();

        String serie(int unidad) { return unidad < series.size() ? series.get(unidad) : null; }

        /**
         * Descripción del activo en el orden del registro normal
         * (descripción, material, medidas, color, M:, MOD:, N/S:).
         */
        String descripcionCon(String serie) {
            StringBuilder sb = new StringBuilder(base);
            if (material != null)    sb.append(" MATERIAL ").append(material);
            if (dimensiones != null) sb.append(" MEDIDAS ").append(dimensiones);
            if (color != null)       sb.append(" COLOR ").append(color);
            List<String> tecnicos = new ArrayList<>();
            if (marca != null)  tecnicos.add("M:" + marca);
            if (modelo != null) tecnicos.add("MOD:" + modelo);
            if (serie != null)  tecnicos.add("N/S:" + serie);
            if (!tecnicos.isEmpty()) sb.append(' ').append(String.join(", ", tecnicos));
            return sb.toString();
        }
    }

    private BienValidado validarBien(DonacionRegistroRequest.Bien in, int n, Predio predio) {
        String en = " (bien " + n + ")";
        if (in == null) throw new IllegalArgumentException("Bien " + n + " vacío.");
        BienValidado b = new BienValidado();

        b.grupo = in.getIdGrupoContable() == null ? null : grupoService.findById(in.getIdGrupoContable());
        if (b.grupo == null || ELIMINADO.equalsIgnoreCase(b.grupo.getEstado())) {
            throw new IllegalArgumentException("Elija un grupo contable vigente" + en + ".");
        }
        if (b.grupo.getCodDbf() == null) throw new IllegalArgumentException("El grupo contable " + b.grupo.getNombre() + " no tiene código" + en + ".");

        if (in.getIdAuxiliar() != null) {
            Auxiliar aux = auxiliarService.findById(in.getIdAuxiliar());
            if (aux == null || "ELIMINADO".equalsIgnoreCase(aux.getEstado())) {
                throw new IllegalArgumentException("El auxiliar elegido ya no existe" + en + ".");
            }
            if (aux.getGrupoContable() == null || !aux.getGrupoContable().getIdGrupoContable().equals(b.grupo.getIdGrupoContable())
                    || aux.getPredio() == null || !aux.getPredio().getIdPredio().equals(predio.getIdPredio())) {
                throw new IllegalArgumentException("El auxiliar '" + aux.getNombre()
                        + "' no es del predio y grupo contable del bien" + en + ". Vuelva a elegirlo.");
            }
            b.auxiliar = aux;
        }

        Long idEstado = in.getIdEstadoActivo() != null ? in.getIdEstadoActivo() : ESTADO_ACTIVO_POR_DEFECTO;
        b.estadoActivo = estadoActivoService.findById(idEstado);
        if (b.estadoActivo == null) throw new IllegalArgumentException("El estado de conservación elegido no existe" + en + ".");

        b.base = mayus(in.getDescripcion());
        if (b.base == null) throw new IllegalArgumentException("Escriba la descripción" + en + ".");
        exigirLargo(b.base, 600, "La descripción" + en);
        b.marca = mayus(in.getMarca());               exigirLargo(b.marca, 80, "La marca" + en);
        b.modelo = mayus(in.getModelo());             exigirLargo(b.modelo, 80, "El modelo" + en);
        b.color = mayus(in.getColor());               exigirLargo(b.color, 60, "El color" + en);
        b.material = mayus(in.getMaterial());         exigirLargo(b.material, 80, "El material" + en);
        b.dimensiones = mayus(in.getDimensiones());   exigirLargo(b.dimensiones, 80, "Las medidas" + en);
        b.caracteristicas = texto(in.getCaracteristicas());
        if (b.caracteristicas != null) b.caracteristicas = b.caracteristicas.toUpperCase();
        exigirLargo(b.caracteristicas, 4000, "Las características" + en);

        b.cantidad = in.getCantidad() == null ? 1 : in.getCantidad();
        if (b.cantidad < 1 || b.cantidad > TOPE_UNIDADES_BIEN) {
            throw new IllegalArgumentException("La cantidad debe estar entre 1 y " + TOPE_UNIDADES_BIEN + en + ".");
        }
        double valor = in.getValorUnitario() == null ? 0 : in.getValorUnitario();
        if (valor < 0 || Double.isNaN(valor) || Double.isInfinite(valor)) {
            throw new IllegalArgumentException("El valor referencial no puede ser negativo" + en + ".");
        }
        b.valor = redondear(valor);
        Integer vida = in.getVidaUtil() != null ? in.getVidaUtil() : b.grupo.getVidaUtil();
        b.vidaUtil = vida == null ? 0 : vida;
        if (b.vidaUtil < 0 || b.vidaUtil > 100) throw new IllegalArgumentException("Vida útil fuera de rango" + en + ".");

        if (in.getSeries() != null) {
            // Posicionales: la línea N es la serie de la unidad N; una línea vacía = sin serie.
            java.util.Set<String> vistas = new java.util.HashSet<>();
            for (String s : in.getSeries()) {
                String serie = mayus(s);
                if (serie != null) {
                    exigirLargo(serie, 80, "La serie " + serie + en);
                    if (!vistas.add(serie)) throw new IllegalArgumentException("La serie " + serie + " está repetida" + en + ".");
                }
                b.series.add(serie);
            }
            while (!b.series.isEmpty() && b.series.get(b.series.size() - 1) == null) b.series.remove(b.series.size() - 1);
            if (b.series.size() > b.cantidad) {
                throw new IllegalArgumentException("Hay más series que unidades" + en + ".");
            }
        }

        // La descripción más larga posible (con la serie más larga) tiene que entrar en el activo.
        String serieMasLarga = b.series.stream().filter(java.util.Objects::nonNull).reduce("", (x, y) -> y.length() > x.length() ? y : x);
        int largo = b.descripcionCon(serieMasLarga.isEmpty() ? null : serieMasLarga).length();
        if (largo > MAX_DESCRIPCION) {
            throw new IllegalArgumentException("La descripción completa queda de " + largo + " caracteres" + en
                    + " y el máximo es " + MAX_DESCRIPCION + ". Lleve el detalle largo a «Características».");
        }
        return b;
    }

    /**
     * El historial se guarda después de confirmar el alta, en su propia transacción: si la
     * base rechaza la fila (historial_activo tiene un CHECK sobre tipo_evento), solo se pierde
     * ese rastro y no la donación entera. Así falló la primera prueba en vivo (13-oct).
     */
    private void guardarHistorialAlConfirmar(List<HistorialActivo> historiales) {
        if (historiales.isEmpty()) return;
        Runnable guardar = () -> {
            try {
                TransactionTemplate tx = new TransactionTemplate(txManager);
                tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                tx.executeWithoutResult(s -> historialDao.saveAll(historiales));
            } catch (Exception e) {
                log.warn("[DONACION] No se pudo guardar el historial de {} bien(es): {}", historiales.size(), e.getMessage());
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

    private HistorialActivo historialAlta(Activo a, DonacionActa acta, Oficina oficina, Responsable resp,
                                          String nombreResp, String usuarioNombre, Long idUsuario) {
        HistorialActivo h = new HistorialActivo();
        h.setActivo(a);
        h.setCodigoActivo(a.getCodigo());
        // historial_activo tiene un CHECK con los tipos permitidos (REGISTRO, MODIFICACION,
        // TRANSFERENCIA_INT…): un tipo nuevo hace fallar el INSERT y deshace toda el alta.
        h.setTipoEvento("REGISTRO");
        h.setFechaEvento(LocalDateTime.now());
        h.setIdUsuario(idUsuario);
        h.setNombreUsuario(usuarioNombre);
        h.setDescripcionEvento("Alta por donación " + acta.getNumero() + " de " + acta.getDonanteNombre()
                + (acta.getDocumentoRespaldo() != null ? " (respaldo: " + acta.getDocumentoRespaldo() + ")" : "")
                + ". Bien solo del SCIAF: no se registra en el VSIAF.");
        h.setOficinaNueva(oficina);
        h.setNombreOficinaNueva(oficina.getNombre());
        h.setResponsableNuevo(resp);
        h.setNombreRespNuevo(nombreResp);
        return h;
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Anulación (una donación registrada por error)
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Anula un acta registrada por error: el acta queda ANULADA con su motivo y sus bienes
     * pasan a CANCELADO liberando el código D (queda en {@code codigo_anulado}), como la
     * cancelación de pendientes. Nada que deshacer en el VSIAF: nunca estuvieron allá.
     * <p>
     * Solo si ningún bien tuvo vida propia después del alta: si alguno se asignó, transfirió,
     * está en un acta de asignación o tiene un faltante abierto, cancelarlo dejaría esos
     * registros apuntando a un bien que ya no existe. Solo ADMINISTRADOR / SUPER USUARIO.
     */
    @Transactional
    public String anular(Long idDonacion, String motivo, Usuario usuario) {
        if (usuario == null || !RolesSciaf.esAdministrativo(usuario)) {
            throw new SecurityException("Solo un ADMINISTRADOR o SUPER USUARIO puede anular una donación.");
        }
        String m = texto(motivo);
        if (m == null) throw new IllegalArgumentException("Indique el motivo de la anulación.");
        exigirLargo(m, 1000, "El motivo");

        actaDao.turno(TURNO_DONACIONES);
        DonacionActa acta = actaDao.buscarConDestino(idDonacion)
                .orElseThrow(() -> new IllegalArgumentException("La donación no existe."));
        if (DonacionActa.ANULADA.equals(acta.getEstado())) {
            throw new IllegalArgumentException("La donación " + acta.getNumero() + " ya está anulada.");
        }

        // Las filas de los bienes, bloqueadas antes de leerlas: una transferencia simultánea
        // espera a que esta anulación termine (o la anulación ve el bien ya movido).
        bienDao.bloquearActivosDeActa(idDonacion);
        List<DonacionBien> bienes = bienDao.listarDeActa(idDonacion);
        List<Long> ids = new ArrayList<>();
        for (DonacionBien b : bienes) {
            Activo a = b.getActivo();
            ids.add(a.getIdActivo());
            String cod = a.getCodigo();
            if (!Activo.ESTADO_ACTIVO.equals(a.getEstado())) {
                throw new IllegalArgumentException("El bien " + cod + " ya no está vigente (" + a.getEstado() + "): la donación no se puede anular.");
            }
            boolean mismoLugar = a.getOficina() != null && acta.getOficina() != null
                    && a.getOficina().getIdOficina().equals(acta.getOficina().getIdOficina())
                    && a.getResponsable() != null && acta.getResponsable() != null
                    && a.getResponsable().getIdResponsable().equals(acta.getResponsable().getIdResponsable());
            if (!mismoLugar || a.enCustodia() || Boolean.TRUE.equals(a.getBloqueado())) {
                throw new IllegalArgumentException("El bien " + cod + " ya no está donde se registró (o está bloqueado): "
                        + "devuélvalo a " + nombreDe(acta.getResponsable()) + " antes de anular.");
            }
        }
        if (!ids.isEmpty()) {
            long movs = bienDao.contarMovimientos(ids);
            if (movs > 0) throw new IllegalArgumentException(movs + " movimiento(s) registrados sobre los bienes de esta donación "
                    + "(asignaciones o transferencias): ya no se puede anular.");
            long falt = bienDao.contarFaltantesPendientes(ids);
            if (falt > 0) throw new IllegalArgumentException(falt + " bien(es) de esta donación tienen un faltante abierto: "
                    + "resuélvalo antes de anular.");
            long lev = bienDao.contarEnLevantamientos(ids);
            if (lev > 0) throw new IllegalArgumentException(lev + " bien(es) de esta donación están en un levantamiento abierto: "
                    + "ciérrelo o espere a que termine antes de anular.");
            long asig = bienDao.contarEnAsignaciones(ids);
            if (asig > 0) throw new IllegalArgumentException(asig + " bien(es) de esta donación figuran en un acta de asignación: "
                    + "quítelos de ella antes de anular.");
        }

        LocalDate hoy = LocalDate.now();
        for (DonacionBien b : bienes) {
            Activo a = b.getActivo();
            a.setCodigoAnulado(a.getCodigo());   // respaldo para auditoría
            a.setCodigo(null);                    // el código D no se reutiliza (ver maxCorrelativoDonacion)
            a.setEstado("CANCELADO");
            a.setApiEstado((short) 0);
            a.setFecMod(hoy);
            a.setFechaUlt(hoy);
            a.setUsuMod(usuario.getUsuario());
            a.setSincVsiafMensaje("Donación " + acta.getNumero() + " anulada: " + m);
            b.setEstado("ANULADO");
        }
        acta.setEstado(DonacionActa.ANULADA);
        acta.setMotivoAnulacion(m);
        acta.setFechaAnulacion(LocalDateTime.now());
        acta.setUsuarioAnulacion(usuario.getUsuario());
        log.info("[DONACION] {} anulada por {}: {} bien(es) cancelado(s). Motivo: {}",
                acta.getNumero(), usuario.getUsuario(), bienes.size(), m);
        return acta.getNumero();
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Eliminar (un acta sin uso: datos de prueba o un error detectado enseguida)
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Borra el acta, sus bienes (activos), sus fotos y el alta en el historial: el número del
     * acta y los códigos D quedan libres para volver a usarse. Para datos de prueba o un
     * registro equivocado que nadie usó todavía.
     * <p>
     * Solo si ninguna unidad tuvo vida propia (asignación, transferencia, levantamiento,
     * faltante, o se movió de donde se registró): para esas está «Anular», que deja rastro.
     * Solo ADMINISTRADOR / SUPER USUARIO, y hay que escribir el número del acta para confirmar.
     * Vale también para un acta ya anulada.
     */
    @Transactional
    public String eliminar(Long idDonacion, String confirmacion, Usuario usuario) {
        if (usuario == null || !RolesSciaf.esAdministrativo(usuario)) {
            throw new SecurityException("Solo un ADMINISTRADOR o SUPER USUARIO puede eliminar una donación.");
        }
        actaDao.turno(TURNO_DONACIONES);
        DonacionActa acta = actaDao.buscarConDestino(idDonacion)
                .orElseThrow(() -> new IllegalArgumentException("La donación no existe."));
        if (confirmacion == null || !acta.getNumero().equalsIgnoreCase(confirmacion.trim())) {
            throw new IllegalArgumentException("Para eliminar, escriba exactamente el número del acta: " + acta.getNumero() + ".");
        }
        bienDao.bloquearActivosDeActa(idDonacion);
        List<DonacionBien> bienes = bienDao.listarDeActa(idDonacion);
        boolean anulada = DonacionActa.ANULADA.equals(acta.getEstado());
        java.util.Set<Long> vida = unidadesConVida(acta, bienes, anulada);
        if (!vida.isEmpty()) {
            throw new IllegalArgumentException(vida.size() + " bien(es) de esta donación ya tienen movimientos (asignación, transferencia, "
                    + "levantamiento o faltante) o no están donde se registraron: no se puede eliminar. "
                    + (anulada ? "Queda anulada." : "Use «Anular»: deja el acta y sus bienes como registro."));
        }
        List<com.usic.SistemasActivosFijosUAP.model.entity.DonacionFoto> fotos = fotoDao.findByDonacionIdDonacion(idDonacion);
        List<String> rutas = fotos.stream().map(com.usic.SistemasActivosFijosUAP.model.entity.DonacionFoto::getRuta).toList();
        fotoDao.deleteAll(fotos);
        borrarUnidades(bienes);
        actaDao.delete(acta);
        actaDao.flush();
        borrarArchivosAlConfirmar(rutas);
        log.warn("[DONACION] {} ELIMINADA por {}: {} bien(es) y {} foto(s) borrados; número y códigos D libres.",
                acta.getNumero(), usuario.getUsuario(), bienes.size(), fotos.size());
        return acta.getNumero();
    }

    /**
     * Unidades que ya tienen vida propia y por eso no se borran ni cambian de código ni de
     * lugar desde aquí. En un acta vigente cuenta además no estar donde se registró, estar
     * en custodia o bloqueada, o no estar ACTIVO; en una anulada sus bienes ya son CANCELADO.
     */
    private java.util.Set<Long> unidadesConVida(DonacionActa acta, List<DonacionBien> bienes, boolean anulada) {
        java.util.Set<Long> out = new java.util.HashSet<>();
        List<Long> ids = bienes.stream().map(b -> b.getActivo().getIdActivo()).toList();
        if (ids.isEmpty()) return out;
        bienDao.idsConVida(ids).forEach(n -> out.add(n.longValue()));
        if (!anulada) {
            for (DonacionBien b : bienes) {
                Activo a = b.getActivo();
                if (!Activo.ESTADO_ACTIVO.equals(a.getEstado()) || !enSuLugar(a, acta)
                        || a.enCustodia() || Boolean.TRUE.equals(a.getBloqueado())) {
                    out.add(a.getIdActivo());
                }
            }
        }
        return out;
    }

    private static boolean enSuLugar(Activo a, DonacionActa acta) {
        return a.getOficina() != null && acta.getOficina() != null
                && a.getOficina().getIdOficina().equals(acta.getOficina().getIdOficina())
                && a.getResponsable() != null && acta.getResponsable() != null
                && a.getResponsable().getIdResponsable().equals(acta.getResponsable().getIdResponsable());
    }

    /** Borra unidades sin vida: detalle de donación, alta en el historial y el activo. */
    private void borrarUnidades(List<DonacionBien> bienes) {
        if (bienes.isEmpty()) return;
        List<Activo> activos = bienes.stream().map(DonacionBien::getActivo).toList();
        List<Long> ids = activos.stream().map(Activo::getIdActivo).toList();
        fotoDao.flush();                 // lo pendiente de las fotos, antes del UPDATE nativo
        fotoDao.soltarDeActivos(ids);
        bienDao.deleteAll(bienes);
        bienDao.flush();
        bienDao.borrarHistorialAlta(ids);
        activoDao.deleteAll(activos);
        activoDao.flush();
    }

    /** La unidad que representa al ítem: la primera sin vida propia (sus datos son los del acta). */
    private static DonacionBien referencia(List<DonacionBien> unidades, java.util.Set<Long> vida) {
        return unidades.stream().filter(d -> !vida.contains(d.getActivo().getIdActivo())).findFirst().orElse(unidades.get(0));
    }

    private static void exigirSinVida(List<DonacionBien> unidades, java.util.Set<Long> vida, String accion) {
        long n = unidades.stream().filter(d -> vida.contains(d.getActivo().getIdActivo())).count();
        if (n > 0) {
            throw new IllegalArgumentException("No se puede " + accion + ": " + n + " de sus unidades ya tienen movimientos "
                    + "(asignación, transferencia, levantamiento o faltante) o ya no están donde se registraron.");
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Editar (corregir todo lo cargado)
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Corrige un acta vigente con el mismo formulario del alta. Cada tarjeta trae el ítem que
     * corrige ({@code itemOriginal}; null = bien nuevo):
     * <ul>
     *   <li>mismo grupo y predio: sus unidades se corrigen <b>conservando su código</b>; si
     *       sube la cantidad se agregan unidades con códigos D nuevos; si baja, se borran las
     *       últimas;</li>
     *   <li>otro grupo contable u otro predio: el código cambia de prefijo, así que sus
     *       unidades se rehacen con códigos nuevos;</li>
     *   <li>un ítem que ya no viene se borra.</li>
     * </ul>
     * Borrar, rehacer o cambiar el destino solo vale para unidades sin vida propia (ver
     * {@link #unidadesConVida}); corregir datos (descripción, marca, series, valor…) vale
     * siempre. El número del acta no cambia.
     */
    @Transactional
    public Resultado editar(Long idDonacion, DonacionRegistroRequest req, Usuario usuario, Object opcionesSesion) {
        if (!puede(usuario, opcionesSesion)) {
            throw new SecurityException("No tiene permiso para editar donaciones (opción «Registro de Donaciones»).");
        }
        if (req == null) throw new IllegalArgumentException("No llegaron datos.");

        actaDao.turno(TURNO_DONACIONES);
        DonacionActa acta = actaDao.buscarConDestino(idDonacion)
                .orElseThrow(() -> new IllegalArgumentException("La donación no existe."));
        if (DonacionActa.ANULADA.equals(acta.getEstado())) throw new IllegalArgumentException("Una donación anulada no se edita.");
        int revision = acta.getRevision() == null ? 0 : acta.getRevision();
        if (req.getRevision() == null || req.getRevision() != revision) {
            throw new IllegalArgumentException("Alguien guardó cambios en esta donación mientras usted la editaba. "
                    + "Cierre y vuelva a abrir la edición para ver lo último (no se guardó nada).");
        }
        Cabecera c = validarCabecera(req, acta);
        List<DonacionRegistroRequest.Bien> entrada = req.getBienes();
        if (entrada == null || entrada.isEmpty()) throw new IllegalArgumentException("El acta debe tener al menos un bien.");
        List<BienValidado> validados = new ArrayList<>();
        int totalUnidades = 0;
        for (int i = 0; i < entrada.size(); i++) {
            BienValidado b = validarBien(entrada.get(i), i + 1, c.predio());
            validados.add(b);
            totalUnidades += b.cantidad;
        }
        if (totalUnidades > TOPE_UNIDADES_ACTA) {
            throw new IllegalArgumentException("Un acta admite hasta " + TOPE_UNIDADES_ACTA + " unidades; quedaría con " + totalUnidades + ".");
        }
        bienDao.bloquearActivosDeActa(idDonacion);
        List<DonacionBien> existentes = bienDao.listarDeActa(idDonacion);
        Map<Integer, List<DonacionBien>> porItem = new LinkedHashMap<>();
        for (DonacionBien d : existentes) porItem.computeIfAbsent(d.getItem(), k -> new ArrayList<>()).add(d);
        java.util.Set<Long> vida = unidadesConVida(acta, existentes, false);

        boolean cambiaDestino = !c.oficina().getIdOficina().equals(acta.getOficina().getIdOficina())
                || !c.responsable().getIdResponsable().equals(acta.getResponsable().getIdResponsable());
        if (cambiaDestino && !vida.isEmpty()) {
            throw new IllegalArgumentException("No se puede cambiar el destino del acta: " + vida.size() + " bien(es) ya tienen movimientos "
                    + "o ya no están donde se registraron. Muévalos con Transferencias o Asignaciones.");
        }

        // ── Plan: qué unidades se conservan, cuáles se borran y cuántas se crean ──
        // reemplaza: al rehacer (otro grupo o predio), las unidades viejas cuyas fotos pasan,
        // por posición, a las nuevas.
        record Paso(BienValidado b, int item, List<DonacionBien> conservar, int crear, List<DonacionBien> reemplaza) {}
        List<Paso> pasos = new ArrayList<>();
        List<DonacionBien> aBorrar = new ArrayList<>();
        Map<Integer, Integer> nuevoItemDe = new HashMap<>();
        for (int i = 0; i < entrada.size(); i++) {
            BienValidado b = validados.get(i);
            int item = i + 1;
            Integer orig = entrada.get(i).getItemOriginal();
            List<DonacionBien> viejas = List.of();
            if (orig != null) {
                viejas = porItem.get(orig);
                if (viejas == null) throw new IllegalArgumentException("El bien " + orig + " ya no está en el acta: vuelva a abrir la edición.");
                if (nuevoItemDe.putIfAbsent(orig, item) != null) throw new IllegalArgumentException("El bien " + orig + " vino dos veces.");
            }
            // Se conservan los códigos si no cambian el grupo contable ni el predio (por id, igual
            // que la pantalla). Comparar el prefijo del código fallaba si el grupo se había
            // cambiado en Registro de activos o si dos grupos comparten codDbf.
            Activo ref = viejas.isEmpty() ? null : referencia(viejas, vida).getActivo();
            boolean mismoPrefijo = ref == null
                    || (ref.getGrupoContable() != null && ref.getGrupoContable().getIdGrupoContable().equals(b.grupo.getIdGrupoContable())
                        && c.predio().getIdPredio().equals(acta.getOficina().getPredio().getIdPredio()));
            if (!mismoPrefijo) {
                exigirSinVida(viejas, vida, "cambiar el grupo contable o el predio del bien " + item);
                aBorrar.addAll(viejas);
                pasos.add(new Paso(b, item, List.of(), b.cantidad, viejas));
            } else {
                int quedan = Math.min(viejas.size(), b.cantidad);
                List<DonacionBien> sobran = new ArrayList<>(viejas.subList(quedan, viejas.size()));
                exigirSinVida(sobran, vida, "bajar la cantidad del bien " + item);
                aBorrar.addAll(sobran);
                pasos.add(new Paso(b, item, new ArrayList<>(viejas.subList(0, quedan)), b.cantidad - quedan, List.of()));
            }
        }
        for (Map.Entry<Integer, List<DonacionBien>> e : porItem.entrySet()) {
            if (nuevoItemDe.containsKey(e.getKey())) continue;
            exigirSinVida(e.getValue(), vida, "quitar el bien " + e.getKey());
            aBorrar.addAll(e.getValue());
        }

        // ── Fotos: siguen a su bien; las de un bien quitado se dan de baja ──
        for (com.usic.SistemasActivosFijosUAP.model.entity.DonacionFoto f
                : fotoDao.findByDonacionIdDonacionAndEstadoOrderByItemAscIdFotoAsc(idDonacion, "ACTIVO")) {
            Integer nuevo = nuevoItemDe.get(f.getItem());
            if (nuevo == null) f.setEstado(ELIMINADO);
            else f.setItem(nuevo);
        }

        // Borrar activos (quitar bienes, bajar cantidades, rehacer por grupo o predio) es tan
        // definitivo como «Eliminar»: lo hace un administrador. Corregir y agregar, el módulo.
        if (!aBorrar.isEmpty() && !RolesSciaf.esAdministrativo(usuario)) {
            throw new IllegalArgumentException("Quitar bienes, bajar cantidades o cambiar el grupo contable o el predio (rehace los códigos) "
                    + "borra activos: lo hace un ADMINISTRADOR o SUPER USUARIO. Corrija solo los datos o pídaselo.");
        }

        // Fotos de las unidades que se rehacen: se anotan antes de borrar para dárselas a las nuevas.
        Map<Paso, List<List<com.usic.SistemasActivosFijosUAP.model.entity.DonacionFoto>>> fotosQuePasan = new HashMap<>();
        for (Paso paso : pasos) {
            if (paso.reemplaza().isEmpty()) continue;
            repartirFotosDeItem(acta, paso.reemplaza().get(0).getItem());
            List<List<com.usic.SistemasActivosFijosUAP.model.entity.DonacionFoto>> porPosicion = new ArrayList<>();
            for (DonacionBien vieja : paso.reemplaza()) {
                porPosicion.add(fotoDao.findByActivoIdActivoAndEstado(vieja.getActivo().getIdActivo(), "ACTIVO"));
            }
            fotosQuePasan.put(paso, porPosicion);
        }

        // ── Aplicar: primero lo que se borra (libera sus códigos), después el resto ──
        borrarUnidades(aBorrar);
        aplicarCabecera(acta, c);
        String usuarioNombre = usuario.getUsuario();
        LocalDate hoy = LocalDate.now();
        Map<String, Integer> siguientePorPrefijo = new HashMap<>();
        List<HistorialActivo> historiales = new ArrayList<>();
        double valorTotal = 0;
        int corregidas = 0, creadas = 0, intactas = 0;
        List<Long> movidas = new ArrayList<>();
        for (Paso paso : pasos) {
            BienValidado b = paso.b();
            int u = 0;
            for (DonacionBien d : paso.conservar()) {
                Activo a = d.getActivo();
                String serie = b.serie(u++);
                if (vida.contains(a.getIdActivo())) {
                    // Ya tiene vida propia (se movió, se dio de baja, está en un acta…): sus datos
                    // son suyos y se corrigen desde su módulo; aquí solo sigue a su ítem.
                    d.setItem(paso.item());
                    intactas++;
                    continue;
                }
                volcarDatos(a, d, acta, c, b, serie, paso.item(), usuarioNombre, hoy);
                if (cambiaDestino) {
                    a.setOficina(c.oficina());
                    a.setResponsable(c.responsable());
                    movidas.add(a.getIdActivo());
                }
                corregidas++;
            }
            for (int k = 0; k < paso.crear(); k++) {
                String codigo = siguienteCodigo(siguientePorPrefijo, prefijoDe(c, b));
                Activo a = crearUnidad(acta, c, b, codigo, b.serie(u++), paso.item(), usuarioNombre, hoy);
                historiales.add(historialAlta(a, acta, c.oficina(), c.responsable(), nombreDe(c.responsable()), usuarioNombre, usuario.getIdUsuario()));
                List<List<com.usic.SistemasActivosFijosUAP.model.entity.DonacionFoto>> pasan = fotosQuePasan.get(paso);
                if (pasan != null && k < pasan.size()) {
                    for (var foto : pasan.get(k)) {
                        com.usic.SistemasActivosFijosUAP.model.entity.DonacionFoto copia = new com.usic.SistemasActivosFijosUAP.model.entity.DonacionFoto();
                        copia.setDonacion(acta);
                        copia.setActivo(a);
                        copia.setItem(paso.item());
                        copia.setRuta(foto.getRuta());
                        copia.setNombreOriginal(foto.getNombreOriginal());
                        copia.setUsuario(foto.getUsuario());
                        copia.setEstado("ACTIVO");
                        fotoDao.save(copia);
                    }
                }
                creadas++;
            }
            valorTotal += b.valor * b.cantidad;
        }
        if (!movidas.isEmpty()) {
            activoDao.flush();
            bienDao.moverHistorialAlta(movidas, c.oficina().getIdOficina(), c.oficina().getNombre(),
                    c.responsable().getIdResponsable(), nombreDe(c.responsable()));
        }
        acta.setCantidadBienes(totalUnidades);
        acta.setValorTotal(redondear(valorTotal));
        acta.setRevision(revision + 1);
        guardarHistorialAlConfirmar(historiales);
        log.info("[DONACION] {} editada por {}: {} unidad(es) corregidas, {} sin tocar (con movimientos), {} nuevas, {} borradas.",
                acta.getNumero(), usuarioNombre, corregidas, intactas, creadas, aBorrar.size());
        return resultadoDe(acta, false);
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Vista previa de los códigos (sin turno: es solo orientativa)
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Qué códigos tomaría cada bien si se registrara ahora. No reserva nada: si otro
     * usuario registra antes, al guardar salen los siguientes libres.
     *
     * @param bienes pares {idGrupoContable, cantidad} en el orden de la pantalla
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> previsualizar(Long idOficina, List<Map<String, Object>> bienes) {
        List<Map<String, Object>> out = new ArrayList<>();
        Oficina oficina = idOficina == null ? null : oficinaService.findById(idOficina);
        if (oficina == null || bienes == null) return out;
        Predio predio = oficina.getPredio();
        String codMun = predio != null && predio.getMunicipio() != null ? limpiar(predio.getMunicipio().getCodigo()) : null;
        String codPred = predio != null ? limpiar(predio.getCodigo()) : null;
        Map<String, Integer> siguiente = new HashMap<>();
        Map<Long, GrupoContable> grupos = new HashMap<>();
        for (Map<String, Object> b : bienes) {
            Map<String, Object> r = new LinkedHashMap<>();
            Long idGrupo = aLong(b.get("idGrupoContable"));
            int cantidad = Math.max(1, Math.min(TOPE_UNIDADES_BIEN, aLong(b.get("cantidad")) == null ? 1 : aLong(b.get("cantidad")).intValue()));
            GrupoContable g = idGrupo == null ? null : grupos.computeIfAbsent(idGrupo, grupoService::findById);
            if (codMun == null || codPred == null || g == null || g.getCodDbf() == null) {
                r.put("ok", false);
                out.add(r);
                continue;
            }
            String prefijo = codMun + "-" + codPred + "-" + String.format("%02d", g.getCodDbf());
            int desde = siguiente.computeIfAbsent(prefijo, p -> actaDao.maxCorrelativoDonacion(p) + 1);
            int hasta = desde + cantidad - 1;
            siguiente.put(prefijo, hasta + 1);
            r.put("ok", true);
            r.put("prefijo", prefijo);
            r.put("desde", prefijo + "-" + String.format("%05d", desde) + "D");
            r.put("hasta", prefijo + "-" + String.format("%05d", hasta) + "D");
            r.put("cantidad", cantidad);
            out.add(r);
        }
        return out;
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Consulta
    // ════════════════════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public Map<String, Object> totales() {
        List<Object[]> t = actaDao.totales();
        Object[] fila = t.isEmpty() ? new Object[] { 0L, 0L, 0d } : t.get(0);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("actas", ((Number) fila[0]).longValue());
        out.put("bienes", ((Number) fila[1]).longValue());
        out.put("valor", redondear(((Number) fila[2]).doubleValue()));
        return out;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> detalle(Long idDonacion) {
        DonacionActa d = actaDao.buscarConDestino(idDonacion)
                .orElseThrow(() -> new IllegalArgumentException("La donación no existe."));
        Map<String, Object> out = resumenActa(d);
        out.put("donanteDocumento", d.getDonanteDocumento());
        out.put("donanteContacto", d.getDonanteContacto());
        out.put("observacion", d.getObservacion());
        out.put("responsable", nombreDe(d.getResponsable()));
        out.put("registro", d.getRegistro());
        out.put("motivoAnulacion", d.getMotivoAnulacion());
        out.put("fechaAnulacion", d.getFechaAnulacion() != null ? d.getFechaAnulacion().toString() : null);
        out.put("usuarioAnulacion", d.getUsuarioAnulacion());

        List<DonacionBien> unidades = bienDao.listarDeActa(idDonacion);
        List<Map<String, Object>> filas = new ArrayList<>();
        for (DonacionBien b : unidades) {
            Activo a = b.getActivo();
            Map<String, Object> f = new LinkedHashMap<>();
            f.put("item", b.getItem());
            f.put("idActivo", a.getIdActivo());
            f.put("codigo", a.getCodigo() != null ? a.getCodigo() : a.getCodigoAnulado());
            f.put("descripcion", a.getDescripcion());
            f.put("descripcionBase", b.getDescripcionBase());
            f.put("marca", b.getMarca());
            f.put("modelo", b.getModelo());
            f.put("serie", b.getSerie());
            f.put("color", b.getColor());
            f.put("material", b.getMaterial());
            f.put("dimensiones", b.getDimensiones());
            f.put("caracteristicas", b.getCaracteristicas());
            f.put("valor", b.getValorReferencial());
            f.put("grupo", a.getGrupoContable() != null ? a.getGrupoContable().getNombre() : null);
            f.put("auxiliar", a.getAuxiliar() != null ? a.getAuxiliar().getNombre() : null);
            f.put("conservacion", a.getEstadoActivo() != null ? a.getEstadoActivo().getNombre() : null);
            f.put("estado", a.getEstado());
            // Dónde está hoy: pudo moverse después del alta.
            f.put("oficinaActual", a.getOficina() != null ? etiquetaOficina(a.getOficina()) : null);
            f.put("responsableActual", nombreDe(a.getResponsable()));
            f.put("movido", a.getOficina() == null || d.getOficina() == null
                    || !a.getOficina().getIdOficina().equals(d.getOficina().getIdOficina())
                    || a.getResponsable() == null || d.getResponsable() == null
                    || !a.getResponsable().getIdResponsable().equals(d.getResponsable().getIdResponsable()));
            filas.add(f);
        }
        out.put("bienes", filas);

        // Fotos por activo; las del formato inicial (del ítem) se muestran en cada unidad del ítem.
        Map<Long, List<Map<String, Object>>> porActivo = new HashMap<>();
        Map<Integer, List<Map<String, Object>>> porItem = new HashMap<>();
        for (com.usic.SistemasActivosFijosUAP.model.entity.DonacionFoto f
                : fotoDao.findByDonacionIdDonacionAndEstadoOrderByItemAscIdFotoAsc(idDonacion, "ACTIVO")) {
            Map<String, Object> m = Map.of("id", f.getIdFoto(), "url", f.getRuta());
            if (f.getActivo() != null) porActivo.computeIfAbsent(f.getActivo().getIdActivo(), k -> new ArrayList<>()).add(m);
            else porItem.computeIfAbsent(f.getItem(), k -> new ArrayList<>()).add(m);
        }
        for (int k = 0; k < filas.size(); k++) {
            Map<String, Object> fila = filas.get(k);
            List<Map<String, Object>> lista = new ArrayList<>(porActivo.getOrDefault((Long) fila.get("idActivo"), List.of()));
            lista.addAll(porItem.getOrDefault((Integer) fila.get("item"), List.of()));
            fila.put("fotos", lista);
        }
        out.put("maxFotos", MAX_FOTOS_ITEM);

        java.util.Set<Long> vida = unidadesConVida(d, unidades, DonacionActa.ANULADA.equals(d.getEstado()));
        out.put("conVida", vida.size());
        out.put("eliminable", vida.isEmpty());
        if (!DonacionActa.ANULADA.equals(d.getEstado())) out.put("edicion", datosParaEditar(d, unidades, vida));
        return out;
    }

    /** Lo que la pantalla necesita para abrir el asistente con el acta cargada. */
    private Map<String, Object> datosParaEditar(DonacionActa d, List<DonacionBien> unidades, java.util.Set<Long> vida) {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("donanteTipo", d.getDonanteTipo());
        e.put("donanteNombre", d.getDonanteNombre());
        e.put("donanteDocumento", d.getDonanteDocumento());
        e.put("donanteContacto", d.getDonanteContacto());
        e.put("documentoRespaldo", d.getDocumentoRespaldo());
        e.put("fechaDonacion", d.getFechaDonacion() != null ? d.getFechaDonacion().toString() : null);
        e.put("observacion", d.getObservacion());
        Oficina of = d.getOficina();
        e.put("idPredio", of != null && of.getPredio() != null ? of.getPredio().getIdPredio() : null);
        e.put("idOficina", of != null ? of.getIdOficina() : null);
        e.put("idResponsable", d.getResponsable() != null ? d.getResponsable().getIdResponsable() : null);
        e.put("responsableTexto", nombreDe(d.getResponsable()));
        e.put("revision", d.getRevision() == null ? 0 : d.getRevision());
        Activo primero = unidades.isEmpty() ? null : referencia(unidades, vida).getActivo();
        e.put("idOrganismoFinanciero", primero != null && primero.getOrganismoFinanciero() != null
                ? primero.getOrganismoFinanciero().getIdOrganismoFinanciero() : null);
        e.put("destinoBloqueado", !vida.isEmpty());

        Map<Integer, List<DonacionBien>> grupos = new LinkedHashMap<>();
        for (DonacionBien b : unidades) grupos.computeIfAbsent(b.getItem(), k -> new ArrayList<>()).add(b);
        Map<Integer, DonacionBien> refDe = new HashMap<>();
        grupos.forEach((k, l) -> refDe.put(k, referencia(l, vida)));
        Map<Integer, Map<String, Object>> items = new LinkedHashMap<>();
        for (DonacionBien unidad : unidades) {
            Activo a = refDe.get(unidad.getItem()).getActivo();   // los datos del ítem, de su unidad de referencia
            DonacionBien b = refDe.get(unidad.getItem());
            Map<String, Object> it = items.computeIfAbsent(unidad.getItem(), k -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("item", k);
                m.put("idGrupoContable", a.getGrupoContable() != null ? a.getGrupoContable().getIdGrupoContable() : null);
                m.put("idAuxiliar", a.getAuxiliar() != null ? a.getAuxiliar().getIdAuxiliar() : null);
                m.put("idEstadoActivo", a.getEstadoActivo() != null ? a.getEstadoActivo().getIdEstadoActivo() : null);
                m.put("descripcion", b.getDescripcionBase());
                m.put("marca", b.getMarca());
                m.put("modelo", b.getModelo());
                m.put("color", b.getColor());
                m.put("material", b.getMaterial());
                m.put("dimensiones", b.getDimensiones());
                m.put("caracteristicas", b.getCaracteristicas());
                m.put("valorUnitario", b.getValorReferencial());
                m.put("vidaUtil", a.getVidaUtil() != null ? a.getVidaUtil().intValue() : null);
                m.put("cantidad", 0);
                m.put("conVida", 0);
                m.put("series", new ArrayList<String>());
                m.put("codigos", new ArrayList<String>());
                return m;
            });
            it.put("cantidad", (Integer) it.get("cantidad") + 1);
            boolean conVida = vida.contains(unidad.getActivo().getIdActivo());
            if (conVida) it.put("conVida", (Integer) it.get("conVida") + 1);
            @SuppressWarnings("unchecked") List<String> series = (List<String>) it.get("series");
            series.add(unidad.getSerie() == null ? "" : unidad.getSerie());
            @SuppressWarnings("unchecked") List<String> codigos = (List<String>) it.get("codigos");
            codigos.add(unidad.getActivo().getCodigo());
            @SuppressWarnings("unchecked") List<Boolean> vivas = (List<Boolean>) it.computeIfAbsent("unidadesConVida", k -> new ArrayList<Boolean>());
            vivas.add(conVida);
        }
        e.put("bienes", new ArrayList<>(items.values()));
        return e;
    }

    /** Página de actas en el formato de DataTables (draw, recordsTotal, recordsFiltered, data). */
    @Transactional(readOnly = true)
    public Map<String, Object> tabla(int draw, int start, int length, String q) {
        int tam = length <= 0 ? 100 : Math.min(length, 100);
        String filtro = q == null || q.isBlank() ? "" : "%" + q.trim().toUpperCase() + "%";
        Page<DonacionActa> p = actaDao.listar(filtro, PageRequest.of(Math.max(0, start) / tam, tam));
        List<Map<String, Object>> filas = new ArrayList<>();
        for (DonacionActa d : p.getContent()) filas.add(resumenActa(d));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("draw", draw);
        out.put("recordsTotal", filtro.isEmpty() ? p.getTotalElements() : actaDao.contarTodas());
        out.put("recordsFiltered", p.getTotalElements());
        out.put("data", filas);
        out.put("totales", totales());
        return out;
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Fotografías (por ítem del acta)
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Agrega fotos (JPG, PNG o WEBP; se mira el contenido, no solo la extensión), hasta
     * {@link #MAX_FOTOS_ITEM} por activo:
     * <ul>
     *   <li>con {@code idActivo}: a ese activo (detalle del acta);</li>
     *   <li>con {@code item}: a cada unidad de ese bien (tras registrar, la foto de la tarjeta).
     *       El archivo se guarda una vez y cada unidad tiene su fila.</li>
     * </ul>
     * Bajo el turno de donaciones, para que dos pestañas a la vez no pasen el tope.
     */
    @Transactional
    public List<Map<String, Object>> subirFotos(Long idDonacion, Integer item, Long idActivo,
                                               List<org.springframework.web.multipart.MultipartFile> archivosSubidos,
                                               Usuario usuario, Object opcionesSesion) {
        if (!puede(usuario, opcionesSesion)) throw new SecurityException("No tiene permiso para Donaciones.");
        List<org.springframework.web.multipart.MultipartFile> lista = archivosSubidos == null ? List.of()
                : archivosSubidos.stream().filter(f -> f != null && !f.isEmpty()).toList();
        if (lista.isEmpty()) throw new IllegalArgumentException("No llegó ninguna imagen.");

        actaDao.turno(TURNO_DONACIONES);
        DonacionActa acta = actaDao.findById(idDonacion).orElseThrow(() -> new IllegalArgumentException("La donación no existe."));
        if (DonacionActa.ANULADA.equals(acta.getEstado())) throw new IllegalArgumentException("La donación está anulada.");
        List<DonacionBien> unidades;
        if (idActivo != null) {
            unidades = bienDao.listarDeActa(idDonacion).stream()
                    .filter(d -> d.getActivo().getIdActivo().equals(idActivo)).toList();
            if (unidades.isEmpty()) throw new IllegalArgumentException("Ese activo no es de esta donación.");
        } else {
            unidades = bienDao.listarDeActa(idDonacion).stream().filter(d -> d.getItem().equals(item)).toList();
            if (item == null || unidades.isEmpty()) throw new IllegalArgumentException("El bien " + item + " no es de esta donación.");
        }
        repartirFotosDeItem(acta, unidades.get(0).getItem());
        for (DonacionBien d : unidades) {
            long ya = fotoDao.countByActivoIdActivoAndEstado(d.getActivo().getIdActivo(), "ACTIVO");
            if (ya + lista.size() > MAX_FOTOS_ITEM) {
                throw new IllegalArgumentException("Cada activo admite hasta " + MAX_FOTOS_ITEM + " fotos; "
                        + d.getActivo().getCodigo() + " ya tiene " + ya + ".");
            }
        }
        for (var f : lista) {
            if (f.getSize() > MAX_BYTES_FOTO) throw new IllegalArgumentException("La foto «" + f.getOriginalFilename() + "» pasa de 6 MB.");
            if (!esImagen(f)) throw new IllegalArgumentException("«" + f.getOriginalFilename() + "» no es una imagen JPG, PNG o WEBP.");
        }
        List<Map<String, Object>> out = new ArrayList<>();
        List<String> escritas = new ArrayList<>();
        borrarSiSeDeshace(escritas);
        for (var f : lista) {
            // Un sufijo al azar por foto: el nombre lleva milisegundos y dos fotos del mismo
            // pedido podían caer en el mismo y pisarse (el guardado usa REPLACE_EXISTING).
            String sufijo = java.util.UUID.randomUUID().toString().substring(0, 8);
            String ruta = archivos.guardar(f, "donaciones/fotos", acta.getNumero() + "_" + unidades.get(0).getItem() + "_" + sufijo);
            escritas.add(ruta);
            String nombre = f.getOriginalFilename();
            for (DonacionBien d : unidades) {
                com.usic.SistemasActivosFijosUAP.model.entity.DonacionFoto foto = nuevaFoto(acta, d, ruta, nombre, usuario.getUsuario());
                fotoDao.save(foto);
                out.add(Map.of("id", foto.getIdFoto(), "url", ruta, "idActivo", d.getActivo().getIdActivo()));
            }
        }
        return out;
    }

    private static com.usic.SistemasActivosFijosUAP.model.entity.DonacionFoto nuevaFoto(DonacionActa acta, DonacionBien d,
                                                                                    String ruta, String nombre, String usuario) {
        com.usic.SistemasActivosFijosUAP.model.entity.DonacionFoto foto = new com.usic.SistemasActivosFijosUAP.model.entity.DonacionFoto();
        foto.setDonacion(acta);
        foto.setActivo(d.getActivo());
        foto.setItem(d.getItem());
        foto.setRuta(ruta);
        foto.setNombreOriginal(nombre != null && nombre.length() > 200 ? nombre.substring(0, 200) : nombre);
        foto.setUsuario(usuario);
        foto.setEstado("ACTIVO");
        return foto;
    }

    /**
     * Fotos del formato inicial (del ítem, sin activo): las pasa a cada unidad del ítem (una
     * fila por unidad, mismo archivo) y da de baja la fila compartida. Así cada activo puede
     * quitar o agregar las suyas sin tocar las de los demás.
     */
    private void repartirFotosDeItem(DonacionActa acta, Integer item) {
        List<com.usic.SistemasActivosFijosUAP.model.entity.DonacionFoto> viejas =
                fotoDao.findByDonacionIdDonacionAndItemAndEstadoAndActivoIsNull(acta.getIdDonacion(), item, "ACTIVO");
        if (viejas.isEmpty()) return;
        List<DonacionBien> unidades = bienDao.listarDeActa(acta.getIdDonacion()).stream()
                .filter(d -> d.getItem().equals(item)).toList();
        for (var v : viejas) {
            for (DonacionBien d : unidades) fotoDao.save(nuevaFoto(acta, d, v.getRuta(), v.getNombreOriginal(), v.getUsuario()));
            v.setEstado(ELIMINADO);
        }
        fotoDao.flush();
    }

    /**
     * Si la transacción se deshace (falla otra foto, un INSERT o el commit), borra del disco
     * los archivos que ya se escribieron: si no, quedan en uploads/ sin fila que los nombre.
     */
    private void borrarSiSeDeshace(List<String> rutas) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int estado) {
                if (estado == STATUS_COMMITTED) return;
                rutas.forEach(DonacionService::borrarArchivo);
            }
        });
    }

    /** Borra del disco, después de confirmar, los archivos de fotos de un acta eliminada. */
    private void borrarArchivosAlConfirmar(List<String> rutas) {
        if (rutas.isEmpty()) return;
        Runnable borrar = () -> rutas.forEach(DonacionService::borrarArchivo);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { borrar.run(); }
            });
        } else {
            borrar.run();
        }
    }

    /** ruta = /uploads/donaciones/fotos/x.jpg → borra uploads/donaciones/fotos/x.jpg (nunca fuera de uploads/). */
    private static void borrarArchivo(String ruta) {
        try {
            java.nio.file.Path p = java.nio.file.Paths.get(ruta.replaceFirst("^/", "")).toAbsolutePath().normalize();
            if (p.startsWith(java.nio.file.Paths.get("uploads").toAbsolutePath().normalize())) java.nio.file.Files.deleteIfExists(p);
        } catch (Exception e) {
            log.warn("[DONACION] No se pudo borrar el archivo {}: {}", ruta, e.getMessage());
        }
    }

    /**
     * Quita una foto de un activo. Si es una foto del formato inicial (del ítem), primero se
     * reparte a cada unidad y se quita solo la del activo indicado.
     */
    @Transactional
    public void quitarFoto(Long idFoto, Long idActivo, Usuario usuario, Object opcionesSesion) {
        if (!puede(usuario, opcionesSesion)) throw new SecurityException("No tiene permiso para Donaciones.");
        actaDao.turno(TURNO_DONACIONES);
        com.usic.SistemasActivosFijosUAP.model.entity.DonacionFoto f = fotoDao.findById(idFoto)
                .orElseThrow(() -> new IllegalArgumentException("La foto no existe."));
        if (DonacionActa.ANULADA.equals(f.getDonacion().getEstado())) throw new IllegalArgumentException("La donación está anulada.");
        if (f.getActivo() != null) {
            f.setEstado(ELIMINADO);   // el archivo queda: la baja es lógica
            return;
        }
        // Foto del formato inicial (compartida por el ítem): se quita solo la del activo indicado.
        if (idActivo == null) throw new IllegalArgumentException("Indique de qué activo se quita la foto (recargue la pantalla).");
        String ruta = f.getRuta();
        repartirFotosDeItem(f.getDonacion(), f.getItem());
        fotoDao.findByActivoIdActivoAndEstado(idActivo, "ACTIVO").stream()
                .filter(x -> ruta.equals(x.getRuta())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Esa foto no es de este activo."))
                .setEstado(ELIMINADO);
    }

    /** Firma de los primeros bytes: JPEG (FF D8 FF), PNG (89 50 4E 47) o WEBP (RIFF....WEBP). */
    private static boolean esImagen(org.springframework.web.multipart.MultipartFile f) {
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

    /** Predios con lo necesario para armar el código (para la vista). */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> predios() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Predio p : predioService.findAll()) {
            Map<String, Object> m = new LinkedHashMap<>();
            String codMun = p.getMunicipio() != null ? limpiar(p.getMunicipio().getCodigo()) : null;
            String codPred = limpiar(p.getCodigo());
            m.put("id", p.getIdPredio());
            m.put("nombre", p.getDescrip());
            m.put("prefijo", codMun != null && codPred != null ? codMun + "-" + codPred : null);
            out.add(m);
        }
        out.sort((a, b) -> String.valueOf(a.get("nombre")).compareToIgnoreCase(String.valueOf(b.get("nombre"))));
        return out;
    }

    private Map<String, Object> resumenActa(DonacionActa d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", d.getIdDonacion());
        m.put("numero", d.getNumero());
        m.put("fecha", d.getFechaDonacion() != null ? d.getFechaDonacion().toString() : null);
        m.put("donanteTipo", d.getDonanteTipo());
        m.put("donante", d.getDonanteNombre());
        m.put("documentoRespaldo", d.getDocumentoRespaldo());
        m.put("cantidad", d.getCantidadBienes());
        m.put("valor", d.getValorTotal());
        m.put("oficina", d.getOficina() != null ? etiquetaOficina(d.getOficina()) : null);
        m.put("predio", d.getOficina() != null && d.getOficina().getPredio() != null ? d.getOficina().getPredio().getDescrip() : null);
        m.put("usuario", d.getUsuario());
        m.put("estado", d.getEstado());
        return m;
    }

    // ── utilidades ──

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

    /** Texto libre de varias líneas: solo se recortan los extremos. */
    private static String texto(String s) {
        if (s == null) return null;
        String t = s.strip();
        return t.isEmpty() ? null : t;
    }

    private static String mayus(String s) {
        String t = limpiar(s);
        return t == null ? null : t.toUpperCase();
    }

    private static void exigirLargo(String s, int max, String que) {
        if (s != null && s.length() > max) {
            throw new IllegalArgumentException(que + " admite hasta " + max + " caracteres (tiene " + s.length() + ").");
        }
    }

    private static double redondear(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static Long aLong(Object v) {
        if (v instanceof Number n) return n.longValue();
        if (v instanceof String s && !s.isBlank()) {
            try { return Long.parseLong(s.trim()); } catch (NumberFormatException e) { return null; }
        }
        return null;
    }
}
