package com.usic.SistemasActivosFijosUAP.model.dto;

import java.util.List;

import lombok.Data;

/**
 * Petición para registrar VARIOS activos en CÓDIGOS PUNTUALES (huecos) de una misma
 * serie, en un solo lote, desde el módulo de revisión de correlativos.
 *
 * Modelo (confirmado con el área): un lote = una misma {@code oficina} + un mismo
 * {@code responsable} + un mismo {@code grupoContable}. Todos los activos del lote
 * comparten esos datos, por eso luego se puede emitir UN acta de asignación
 * reutilizando {@code POST /reportes/generar-asignacion} (igual que el registro masivo).
 *
 * El {@code auxiliar} es POR ACTIVO (no del lote): dentro de un mismo grupo/acta cada
 * activo puede tener un auxiliar distinto, por eso va en cada {@link Item}.
 *
 * Cada ítem aporta su código fijo (un hueco) y el detalle del activo
 * (descripción + serie/marca/modelo/color + auxiliar, opcionales salvo la descripción).
 * El backend valida, por cada ítem, que el prefijo del código corresponda al
 * predio/grupo de la oficina y que el código esté libre (el índice único
 * uk_activo_codigo es la red final ante carreras de concurrencia).
 */
@Data
public class RegistroHuecosLoteRequest {

    // ── Datos compartidos por todo el lote ──────────────────────────────────
    private Long    idOficina;              // obligatorio
    private Long    idResponsable;          // obligatorio
    private Long    idGrupoContable;        // obligatorio (debe coincidir con el grupo de los códigos)
    private Long    idOrganismoFinanciero;  // opcional (financiador)
    private String  fechaAdquisicion;       // opcional (ISO yyyy-MM-dd)
    private Double  costo;                  // opcional
    private Integer vidaUtil;               // opcional

    // ── Un ítem por hueco a registrar ───────────────────────────────────────
    private List<Item> items;

    @Data
    public static class Item {
        private String codigo;       // código fijo del hueco (mun-pred-grupo-correlativo)
        private String descripcion;  // obligatorio
        private String serie;        // opcional
        private String marca;        // opcional
        private String modelo;       // opcional
        private String color;        // opcional
        private Long   idAuxiliar;   // opcional, POR ACTIVO
    }
}
