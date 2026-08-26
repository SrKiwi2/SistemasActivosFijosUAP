package com.usic.SistemasActivosFijosUAP.model.service;

import java.time.LocalDate;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.usic.SistemasActivosFijosUAP.interoperabilidad.registroDbf.AuxiliarDbfWriterService;
import com.usic.SistemasActivosFijosUAP.model.IService.IAuxiliarService;
import com.usic.SistemasActivosFijosUAP.model.IService.IGrupoContableService;
import com.usic.SistemasActivosFijosUAP.model.IService.IPredioServicio;
import com.usic.SistemasActivosFijosUAP.model.entity.Auxiliar;
import com.usic.SistemasActivosFijosUAP.model.entity.GrupoContable;
import com.usic.SistemasActivosFijosUAP.model.entity.Predio;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;

import lombok.RequiredArgsConstructor;

/**
 * Alta de auxiliares — el <b>único</b> lugar donde se crea un auxiliar a pedido de un
 * usuario, lo pida el módulo Auxiliar, Registro de Activos o Activos Pendientes.
 *
 * <p>Existe porque el alta no es un simple {@code save()}: hay tres reglas que tienen que
 * cumplirse igual en los tres módulos, y tenerlas copiadas es garantía de que una se
 * corrija y las otras no.</p>
 *
 * <ol>
 *   <li><b>El ámbito es (predio, grupo contable).</b> En el VSIAF un auxiliar se identifica
 *       por la tupla (ENTIDAD, UNIDAD, CODCONT, CODAUX), no por un id. Cada predio arma su
 *       propia lista de auxiliares por grupo contable, con numeración propia y en distinto
 *       orden: el auxiliar 3 de un predio no tiene nada que ver con el 3 de otro.</li>
 *   <li><b>El nombre es único dentro de ese ámbito, no globalmente.</b> El mismo nombre
 *       existe legítimamente en varios predios. Validarlo contra toda la tabla bloqueaba
 *       altas correctas.</li>
 *   <li><b>El correlativo se calcula en el servidor.</b> El del formulario se pidió cuando
 *       se abrió el modal; entre medio pudo entrar otra alta o una sincronización desde el
 *       VSIAF.</li>
 * </ol>
 *
 * <p>Después de guardar encola el auxiliar al VSIAF por la misma vía que el resto
 * ({@link AuxiliarDbfWriterService}, cola → worker VFPOLEDB). Si eso falla <b>no</b> se
 * revierte la base: se informa en {@link Resultado#motivoFalloVsiaf()} para que quien llama
 * lo diga sin dar el alta por sincronizada.</p>
 */
@Service
@RequiredArgsConstructor
public class AuxiliarRegistroService {

    private static final Logger log = LoggerFactory.getLogger(AuxiliarRegistroService.class);

    /** Ancho de NOMAUX en auxiliar.DBF. Más largo, el VSIAF corta y los nombres dejan de coincidir. */
    public static final int MAX_NOMBRE = 60;

    private final IAuxiliarService auxiliarService;
    private final IPredioServicio predioServicio;
    private final IGrupoContableService grupoContableService;
    private final AuxiliarDbfWriterService auxiliarDbfWriterService;

    /**
     * @param auxiliar          el auxiliar en la base (nuevo o el que ya existía)
     * @param yaExistia         true si se reutilizó uno existente en vez de crear
     * @param motivoFalloVsiaf  null si se encoló bien; el motivo si el envío falló
     */
    public record Resultado(Auxiliar auxiliar, boolean yaExistia, String motivoFalloVsiaf) {
        public boolean enviadoAlVsiaf() { return motivoFalloVsiaf == null; }
    }

    /**
     * Da de alta un auxiliar en un predio + grupo contable.
     *
     * @param codAuxDeseado    correlativo sugerido (el del formulario); si es null o ya está
     *                         tomado se asigna el siguiente libre del ámbito
     * @param reutilizarSiExiste qué hacer si ya hay un auxiliar con ese nombre en el ámbito:
     *                         {@code true} devuelve el existente — es lo que corresponde en el
     *                         alta al vuelo desde el registro de activos, donde el usuario
     *                         quiere que el activo tenga ese auxiliar y le da igual si lo creó
     *                         él u otro; {@code false} lo rechaza, que es lo correcto en el ABM.
     * @throws IllegalArgumentException si faltan datos o el nombre está duplicado y
     *         {@code reutilizarSiExiste} es false. El mensaje es apto para mostrar al usuario.
     */
    public Resultado registrar(Long idPredio,
                               Long idGrupoContable,
                               String nombreCrudo,
                               Short codAuxDeseado,
                               Usuario usuario,
                               boolean reutilizarSiExiste) {

        if (idPredio == null || idGrupoContable == null) {
            throw new IllegalArgumentException("Debe elegir predio y grupo contable para el auxiliar.");
        }

        Predio predio = predioServicio.findById(idPredio);
        GrupoContable grupo = grupoContableService.findById(idGrupoContable);
        if (predio == null || grupo == null) {
            throw new IllegalArgumentException("El predio o el grupo contable elegido no existe.");
        }
        if (predio.getEntidad() == null
                || predio.getEntidad().getEntidadCodigo() == null
                || predio.getUnidad() == null || predio.getUnidad().isBlank()) {
            throw new IllegalArgumentException(
                "El predio '" + predio.getDescrip() + "' no tiene entidad/unidad configurada: "
              + "sin eso el auxiliar no se puede ubicar en el VSIAF.");
        }

        String nombre = normalizarNombre(nombreCrudo);
        if (nombre.isEmpty()) {
            throw new IllegalArgumentException("El nombre del auxiliar es obligatorio.");
        }

        // ── ¿Ya existe con ese nombre en ESTE predio + grupo? ────────────────────
        Optional<Auxiliar> existente = auxiliarService
                .findByPredioIdPredioAndGrupoContableIdGrupoContableAndNombreIgnoreCase(
                        idPredio, idGrupoContable, nombre);

        if (existente.isPresent() && !"ELIMINADO".equalsIgnoreCase(existente.get().getEstado())) {
            if (!reutilizarSiExiste) {
                throw new IllegalArgumentException(
                    "Ya existe un auxiliar llamado '" + nombre + "' en el predio " + predio.getUnidad()
                  + " para el grupo contable " + grupo.getCodContable() + ".");
            }
            Auxiliar yaEsta = existente.get();
            log.info("[AUX-ALTA] '{}' ya existía en predio={} grupo={} (codAux={}): se reutiliza",
                    nombre, predio.getUnidad(), grupo.getCodContable(), yaEsta.getCodAux());
            // Se reenvía igual: pudo haberse creado antes y no haber llegado nunca al VSIAF.
            return new Resultado(yaEsta, true, enviarAlVsiaf(yaEsta, nombreUsuario(usuario)));
        }

        // ── Alta ─────────────────────────────────────────────────────────────────
        Auxiliar nuevo = new Auxiliar();
        nuevo.setPredio(predio);
        nuevo.setGrupoContable(grupo);
        nuevo.setNombre(nombre);
        nuevo.setEstado("ACTIVO");
        nuevo.setFechaUlt(LocalDate.now());
        nuevo.setUsuario(nombreUsuario(usuario));
        if (usuario != null) {
            nuevo.setRegistroIdUsuario(usuario.getIdUsuario());
        }
        nuevo.setCodAux(resolverCodAuxLibre(predio, grupo, codAuxDeseado));

        guardarConCorrelativoLibre(nuevo, predio, grupo);

        log.info("[AUX-ALTA] Auxiliar '{}' creado en predio={} grupo={} con codAux={} (id={})",
                nombre, predio.getUnidad(), grupo.getCodContable(), nuevo.getCodAux(), nuevo.getIdAuxiliar());

        return new Resultado(nuevo, false, enviarAlVsiaf(nuevo, nombreUsuario(usuario)));
    }

    /** Reenvía un auxiliar ya existente al VSIAF. El INSERT del worker es insert-if-not-exists. */
    public String enviarAlVsiaf(Auxiliar auxiliar, String usuarioNombre) {
        try {
            auxiliarDbfWriterService.asegurarEnVsiaf(auxiliar, usuarioNombre);
            return null;
        } catch (Exception e) {
            log.error("[AUX-ALTA] Auxiliar {} guardado en BD pero NO enviado al VSIAF: {}",
                    auxiliar.getIdAuxiliar(), e.getMessage(), e);
            return e.getMessage();
        }
    }

    /** Trim, espacios colapsados y recorte al ancho de NOMAUX. */
    public static String normalizarNombre(String nombre) {
        if (nombre == null) return "";
        String n = nombre.trim().replaceAll("\\s+", " ");
        return n.length() > MAX_NOMBRE ? n.substring(0, MAX_NOMBRE) : n;
    }

    /**
     * Correlativo libre en el predio + grupo: respeta el sugerido si sigue disponible y,
     * si no, entrega el siguiente de la serie. Nunca reutiliza números dados de baja —
     * en el VSIAF pueden seguir referenciados por activos históricos.
     *
     * @param idAuxiliarQueSeEdita el auxiliar que se está modificando, para que no choque
     *                             con su propio codAux; {@code null} en un alta.
     */
    public Short codAuxLibre(Long idPredio, Long idGrupo, Short codAuxDeseado, Long idAuxiliarQueSeEdita) {
        if (codAuxDeseado != null && codAuxDeseado > 0) {
            Optional<Auxiliar> ocupante = auxiliarService
                    .findByPredio_IdPredioAndGrupoContable_IdGrupoContableAndCodAux(idPredio, idGrupo, codAuxDeseado);
            boolean libre = ocupante.isEmpty()
                    || (idAuxiliarQueSeEdita != null
                        && idAuxiliarQueSeEdita.equals(ocupante.get().getIdAuxiliar()));
            if (libre) return codAuxDeseado;
        }
        Short siguiente = auxiliarService.getNextCodAux(idPredio, idGrupo);
        if (siguiente == null || siguiente <= 0) siguiente = 1;
        if (codAuxDeseado != null && !siguiente.equals(codAuxDeseado)) {
            log.info("[AUX-ALTA] codAux {} ya estaba tomado en predio={} grupo={}; se asigna {}",
                    codAuxDeseado, idPredio, idGrupo, siguiente);
        }
        return siguiente;
    }

    private Short resolverCodAuxLibre(Predio predio, GrupoContable grupo, Short codAuxDeseado) {
        return codAuxLibre(predio.getIdPredio(), grupo.getIdGrupoContable(), codAuxDeseado, null);
    }

    /**
     * Guarda reintentando con el siguiente correlativo si el UNIQUE
     * (predio, grupo, codAux) salta por un alta simultánea.
     */
    private void guardarConCorrelativoLibre(Auxiliar auxiliar, Predio predio, GrupoContable grupo) {
        DataIntegrityViolationException ultimo = null;
        for (int intento = 0; intento < 3; intento++) {
            try {
                Auxiliar guardado = auxiliarService.save(auxiliar);
                auxiliar.setIdAuxiliar(guardado.getIdAuxiliar());
                return;
            } catch (DataIntegrityViolationException e) {
                ultimo = e;
                auxiliar.setIdAuxiliar(null);
                auxiliar.setCodAux(resolverCodAuxLibre(predio, grupo, null));
                log.warn("[AUX-ALTA] Choque de correlativo; reintentando con codAux={}", auxiliar.getCodAux());
            }
        }
        throw ultimo;
    }

    private static String nombreUsuario(Usuario usuario) {
        return (usuario != null && usuario.getUsuario() != null) ? usuario.getUsuario() : "SISTEMA";
    }
}
