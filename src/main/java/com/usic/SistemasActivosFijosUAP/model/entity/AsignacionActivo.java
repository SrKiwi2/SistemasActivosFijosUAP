package com.usic.SistemasActivosFijosUAP.model.entity;

import java.time.LocalDateTime;
import java.util.List;

import com.usic.SistemasActivosFijosUAP.config.AuditoriaConfig;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "asignacion_activo")
@Setter @Getter
public class AsignacionActivo extends AuditoriaConfig{

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idAsignacionActivo;

    /**
     * Número del acta: es el <b>preventivo</b> que se carga al asignar el documento, no
     * un correlativo del sistema.
     * <p>
     * El acta no se numera sola. El número que vale es el del PREV que emite el área
     * contable y que se escribe en la vista de Pendientes; inventar acá un segundo
     * correlativo dejaría al mismo documento con dos identificadores distintos, uno en el
     * papel y otro en la pantalla.
     * <p>
     * Por eso <b>no lleva {@code unique}</b>: el preventivo se repite. Hoy hay 38 actas
     * con {@code S/N} y algunos números cargados dos veces. Un índice único sobre esta
     * columna rechazaría actas perfectamente válidas.
     *
     * @see #asignarDocumento(String, String)
     */
    @Column(name = "numero_asignacion", length = 30)
    private String numeroAsignacion;

    @Column(name = "codigo_documento")
    private String codigoDocumento;

    @Column(name = "codigo_completo")
    private String codigoCompleto;
    
    @Column(name = "fecha_asignacion")
    private LocalDateTime fechaAsignacion;

    @Column(name = "tipo_asignacion", length = 20)
    private String tipoAsignacion = "NUEVA";

    @Column(name = "estado_asignacion", length = 30)
    private String estadoAsignacion = "ACTIVA";

    @Column(name = "documento_referencia", length = 100)
    private String documentoReferencia;

    @Column(name = "observacion", columnDefinition = "text")
    private String observacion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_responsable")
    private Responsable responsable;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_responsable_origen")
    private Responsable responsableOrigen;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_oficina_destino")
    private Oficina oficinaDestino;

    @OneToMany(mappedBy = "asignacionActivo", cascade = CascadeType.ALL)
    private List<DetalleAsignacionActivo> detalles;

    /**
     * Acta de la que salió esta, cuando nació de una separación.
     * <p>
     * Da la trazabilidad en los dos sentidos: desde el acta hija se sabe de dónde vino,
     * y desde la original se pueden listar las que se desprendieron. Sin esto, un acta
     * que aparece con dos bienes el mismo día que otra perdió dos no se puede vincular
     * más que por intuición.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_asignacion_padre")
    private AsignacionActivo asignacionPadre;

    /*
     * El número de documento se guardaba con dos formatos según por dónde se hubiera
     * creado el acta: "(Prev. 1234)" desde Pendientes y "Prev. 1234" desde Reportes. El
     * mismo dato se veía distinto en la misma tabla y las búsquedas fallaban según cómo
     * lo escribiera el usuario.
     *
     * Ahora se guarda siempre sin paréntesis —son presentación, no parte del código— y
     * los dos métodos de abajo dan la forma que necesita cada consumidor. Toleran el
     * formato viejo para que las actas ya registradas se sigan viendo igual.
     */

    /** Lo que se guarda como número de documento cuando todavía no hay uno. */
    private static final String SIN_NUMERO = "S/N";

    /**
     * Carga el documento en el acta, dejando los tres campos coherentes.
     * <p>
     * Los dos datos vienen de la ventana <em>Asignar documento</em> de Pendientes: el
     * tipo de documento (que aporta el prefijo, {@code PREV:}) y el número, que es el
     * identificador real de la asignación. De ahí sale el número del acta:
     * {@code ASG-<gestión>-<número>}. El sistema no inventa un correlativo propio —
     * hacerlo dejaba el mismo documento con un identificador en el papel y otro en la
     * pantalla.
     * <p>
     * El número se usa <b>tal como se tecleó</b>, sin rellenar con ceros: rellenarlo lo
     * convertiría en otro número ({@code 65} no es {@code 0065}).
     * <p>
     * Mientras no haya número —lo que la pantalla muestra como {@code S/N}— el acta se
     * queda sin numerar a propósito: todavía no es una asignación, sigue en la bandeja
     * de Pendientes y no llega a este módulo.
     *
     * @param gestion año del documento; si es nulo se usa el de la fecha del acta
     * @param prefijo etiqueta del tipo de documento ({@code PREV:}); puede ser nula
     * @param nro     número de documento tal como lo escribió el usuario
     */
    public void asignarDocumento(Integer gestion, String prefijo, String nro) {
        String numero   = nro != null ? nro.trim() : "";
        String etiqueta = prefijo != null ? prefijo.trim() : "";

        this.codigoDocumento = numero.isEmpty() ? null : numero;
        this.codigoCompleto  = (etiqueta + " " + numero).trim();
        if (this.codigoCompleto.isEmpty()) this.codigoCompleto = null;

        this.numeroAsignacion = construirNumeroAsignacion(gestion, numero);
    }

    /** {@code ASG-2026-272}, o null mientras el acta no tenga número de documento. */
    private String construirNumeroAsignacion(Integer gestion, String numero) {
        if (numero.isEmpty() || SIN_NUMERO.equalsIgnoreCase(numero)) return null;

        int anio = gestion != null ? gestion
                 : (fechaAsignacion != null ? fechaAsignacion.getYear() : java.time.LocalDate.now().getYear());

        String candidato = "ASG-" + anio + "-" + numero;
        // La columna admite 30 caracteres. Un número absurdamente largo se recorta antes
        // que dejar reventar el guardado del acta.
        return candidato.length() <= 30 ? candidato : candidato.substring(0, 30);
    }

    /** Código del documento sin paréntesis: la forma canónica, para comparar y buscar. */
    public String getCodigoCompletoNormalizado() {
        if (codigoCompleto == null) return null;
        String limpio = codigoCompleto.trim();
        if (limpio.startsWith("(") && limpio.endsWith(")")) {
            limpio = limpio.substring(1, limpio.length() - 1).trim();
        }
        return limpio.isEmpty() ? null : limpio;
    }

    /**
     * Código entre paréntesis, tal como se imprime en el acta y como se antepone a la
     * descripción del activo: {@code "(Prev. 1234)"}.
     */
    public String getEtiquetaDocumento() {
        String limpio = getCodigoCompletoNormalizado();
        return limpio == null ? null : "(" + limpio + ")";
    }
}