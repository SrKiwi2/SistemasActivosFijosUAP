package com.usic.SistemasActivosFijosUAP.model.dto.donacion;

import java.time.LocalDate;
import java.util.List;

import lombok.Data;

/** Lo que envía la pantalla Registro de donaciones al registrar un acta. */
@Data
public class DonacionRegistroRequest {

    /** Marca del intento de registro (ver DonacionActa.tokenRegistro). */
    private String token;

    /** Al editar: la revisión del acta que se abrió (ver DonacionActa.revision). */
    private Integer revision;

    // ── Donante y respaldo ──
    private String donanteTipo;          // PERSONA / INSTITUCION
    private String donanteNombre;
    private String donanteDocumento;     // C.I. o NIT
    private String donanteContacto;
    private String documentoRespaldo;
    private LocalDate fechaDonacion;
    private String observacion;

    // ── Destino común de los bienes ──
    private Long idOficina;
    private Long idResponsable;
    private Long idOrganismoFinanciero;  // opcional

    private List<Bien> bienes;

    @Data
    public static class Bien {
        /** Al editar: el ítem del acta que esta tarjeta corrige (null = bien nuevo). */
        private Integer itemOriginal;
        private Long idGrupoContable;
        private Long idAuxiliar;         // opcional
        private Long idEstadoActivo;     // estado de conservación; opcional (1 por defecto)
        private String descripcion;
        private Integer cantidad;
        private Double valorUnitario;
        private Integer vidaUtil;        // opcional: el del grupo contable si no viene
        private String marca;
        private String modelo;
        private String color;
        private String material;
        private String dimensiones;
        private String caracteristicas;
        /** Una serie por unidad, en orden; puede venir más corta que la cantidad. */
        private List<String> series;
    }
}
