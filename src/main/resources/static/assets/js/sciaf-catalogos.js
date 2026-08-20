/*
 * sciaf-catalogos.js — helpers compartidos por las pantallas que muestran o editan
 * catálogos de activos (Activos Pendientes, Asignaciones, y las que vengan).
 *
 * Existe para que estas reglas vivan en UN solo lugar. Antes estaban copiadas en
 * cada plantilla, y una copia que se corrige y otra que no es una divergencia
 * silenciosa: dos pantallas mostrando códigos distintos para el mismo grupo.
 *
 * Se carga desde layout/script.html, así que está disponible en todas las vistas
 * de administración (también en las que se inyectan por AJAX en #contenido).
 */
(function (global) {
    'use strict';

    // ── Etiquetas de catálogo ────────────────────────────────────────────────

    /**
     * Etiqueta "código - nombre" para los <option> de los selects.
     * El separador " - " es el que ya usaban los selects de grupo contable en
     * Registro de Activos; se mantiene para que todas las pantallas se lean igual.
     * Si el catálogo no trae código devuelve solo el nombre, sin separador colgando.
     */
    function etiquetaCodigo(cod, nombre) {
        const c = (cod === null || cod === undefined) ? '' : String(cod).trim();
        const n = (nombre === null || nombre === undefined) ? '' : String(nombre).trim();
        if (!c) return n;
        return n ? c + ' - ' + n : c;
    }

    /**
     * Código visible de un grupo contable.
     * Regla del sistema: los grupos 15 y 38 se muestran con su código DBF; el resto,
     * con el código contable. Está replicada en Thymeleaf en activo/formulario.html
     * (dos selects) y en el modal Agregar de activo/vista_pendientes.html — si allá
     * cambia, cambiarla acá también.
     */
    function codigoGrupoVisible(g) {
        if (!g) return '';
        const cc = Number(g.codContable);
        return (cc === 15 || cc === 38) ? g.codDbf : g.codContable;
    }

    // ── Texto ────────────────────────────────────────────────────────────────

    /** Minúsculas y sin acentos: buscar "codigo" tiene que encontrar "Código". */
    function normalizarTexto(t) {
        return (t || '').toString().toLowerCase()
            .normalize('NFD').replace(/[\u0300-\u036f]/g, '');
    }

    // ── Marca "INCLUYE ACCESORIOS" ───────────────────────────────────────────
    // El mismo texto vive en ActivosController.MARCA_ACCESORIOS, que es quien lo
    // aplica de verdad al guardar. Esto es solo para que el usuario vea el cambio
    // mientras edita; el backend es idempotente, así que no se duplica.

    const MARCA_ACCESORIOS = 'INCLUYE ACCESORIOS';

    function tieneAccesorios(desc) {
        return (desc || '').trim().toUpperCase().endsWith(MARCA_ACCESORIOS);
    }

    /** Quita la marca del final, incluso si quedó repetida por ediciones a mano. */
    function quitarAccesorios(desc) {
        let base = (desc || '').trim();
        while (base.toUpperCase().endsWith(MARCA_ACCESORIOS)) {
            base = base.slice(0, base.length - MARCA_ACCESORIOS.length).trim();
        }
        return base;
    }

    function ponerAccesorios(desc) {
        const base = quitarAccesorios(desc);
        return base ? base + ' ' + MARCA_ACCESORIOS : MARCA_ACCESORIOS;
    }

    /** 'todos' | 'ninguno' | 'mixto' — estado de la marca en un conjunto de activos. */
    function estadoAccesoriosLote(descripciones) {
        const con = (descripciones || []).filter(tieneAccesorios).length;
        if (con === 0) return 'ninguno';
        if (con === descripciones.length) return 'todos';
        return 'mixto';
    }

    // ── Utilidades de respuesta ──────────────────────────────────────────────

    /** Normaliza a array cualquier forma de respuesta de los endpoints de catálogo. */
    function aArreglo(res) {
        if (!res) return [];
        if (Array.isArray(res)) return res;
        if (Array.isArray(res.content)) return res.content;
        if (Array.isArray(res.data)) return res.data;
        if (Array.isArray(res.results)) return res.results;
        return [];
    }

    global.SciafCatalogos = {
        etiquetaCodigo, codigoGrupoVisible, normalizarTexto,
        MARCA_ACCESORIOS, tieneAccesorios, quitarAccesorios, ponerAccesorios,
        estadoAccesoriosLote, aArreglo
    };

    // Alias globales: las plantillas ya llamaban a estos nombres directamente.
    global.etiquetaCodigo      = etiquetaCodigo;
    global.codigoGrupoVisible  = codigoGrupoVisible;
    global.MARCA_ACCESORIOS    = MARCA_ACCESORIOS;
    global.tieneAccesorios     = tieneAccesorios;
    global.quitarAccesorios    = quitarAccesorios;
    global.ponerAccesorios     = ponerAccesorios;
    global.estadoAccesoriosLote = estadoAccesoriosLote;

})(window);
