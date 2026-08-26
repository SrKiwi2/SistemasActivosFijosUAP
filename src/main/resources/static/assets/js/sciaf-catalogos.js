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

    // ── Alta de auxiliar al vuelo ────────────────────────────────────────────
    //
    // El auxiliar depende del PREDIO y del GRUPO CONTABLE: cada predio arma su propia
    // lista por grupo, con numeración propia. Por eso no alcanza con pedir el nombre —
    // sin esos dos datos el servidor no sabe dónde crearlo, y por eso el botón queda
    // deshabilitado hasta que ambos estén elegidos.
    //
    // Sirve para cuando un activo llega sin auxiliar y el que corresponde todavía no
    // existe en ese predio: se crea sin salir de la pantalla. Lo atiende el mismo
    // servicio que el ABM del módulo Auxiliar, así que las reglas son idénticas y el
    // auxiliar se encola al VSIAF igual que si se hubiera creado allá.

    /**
     * Pide el nombre y crea el auxiliar en (predio, grupo). Si ya existe uno con ese
     * nombre en ese ámbito devuelve el existente en vez de fallar: quien está cargando
     * activos quiere que el activo tenga ese auxiliar, no le importa quién lo creó.
     *
     * @param opts.idPredio        predio donde vive el auxiliar (o usar idOficina)
     * @param opts.idOficina       alternativa: el predio se deduce de esta oficina
     * @param opts.idGrupoContable grupo contable del activo
     * @param opts.contextoTexto   línea de contexto para el diálogo ("Predio X · Grupo Y")
     * @returns Promise<obj|null>  el auxiliar creado/encontrado, o null si se canceló
     */
    function crearAuxiliarRapido(opts) {
        const o = opts || {};

        if (!o.idGrupoContable || (!o.idPredio && !o.idOficina)) {
            Swal.fire('Faltan datos',
                'Primero elegí el predio (o la oficina) y el grupo contable: el auxiliar se numera dentro de esa combinación.',
                'info');
            return Promise.resolve(null);
        }

        return Swal.fire({
            title: 'Nuevo auxiliar',
            html: o.contextoTexto
                ? `<div class="text-muted small mb-2">${o.contextoTexto}</div>`
                : '',
            input: 'text',
            inputLabel: 'Nombre del auxiliar',
            inputPlaceholder: 'Ej: MUEBLES Y ENSERES',
            inputAttributes: { maxlength: 60, autocapitalize: 'characters' },
            footer: '<small class="text-muted">El código correlativo lo asigna el sistema según el predio y el grupo.</small>',
            showCancelButton: true,
            confirmButtonText: 'Crear',
            cancelButtonText: 'Cancelar',
            inputValidator: (valor) => {
                if (!valor || !valor.trim()) return 'Escribí el nombre del auxiliar.';
                return null;
            }
        }).then((res) => {
            if (!res.isConfirmed) return null;

            if (typeof showGlobalLoader === 'function') {
                showGlobalLoader({ texto: 'Creando auxiliar...' });
            }

            return fetch('/api/auxiliares/registrar-rapido', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json', 'X-Requested-With': 'XMLHttpRequest' },
                body: JSON.stringify({
                    idPredio: o.idPredio || null,
                    idOficina: o.idOficina || null,
                    idGrupoContable: o.idGrupoContable,
                    nombre: res.value.trim()
                })
            })
            .then(r => r.json().then(data => ({ ok: r.ok, data })))
            .then(({ ok, data }) => {
                if (!ok || !data.ok) {
                    Swal.fire('No se pudo crear', data.msg || 'Error desconocido.', 'error');
                    return null;
                }
                // vsiaf ERROR = está en la base pero no llegó al VSIAF. Se avisa fuerte:
                // si se suben los activos así, allá se van a ver sin auxiliar.
                Swal.fire(data.vsiaf === 'ERROR' ? 'Creado, con una advertencia' : 'Listo',
                          data.msg,
                          data.vsiaf === 'ERROR' ? 'warning' : 'success');
                return data;
            })
            .catch(err => {
                console.error('[AUX-RAPIDO]', err);
                Swal.fire('Error', 'No se pudo crear el auxiliar: ' + err.message, 'error');
                return null;
            })
            .finally(() => {
                if (typeof hideGlobalLoader === 'function') hideGlobalLoader();
            });
        });
    }

    /**
     * Mete el auxiliar recién creado en un <select> y lo deja seleccionado, sin recargar
     * el catálogo entero. Si ya estaba en la lista (caso "ya existía"), solo lo selecciona.
     * Soporta selects con y sin select2.
     */
    function ponerAuxiliarEnSelect($select, aux, etiqueta) {
        if (!$select || !$select.length || !aux) return;
        const id = String(aux.idAuxiliar ?? aux.id);
        const txt = etiqueta || aux.nombre || aux.text;

        if (!$select.find(`option[value="${id}"]`).length) {
            $select.append(new Option(txt, id, false, false));
        }
        $select.val(id);
        // select2 no se entera de un cambio por .val(): hay que avisarle.
        $select.trigger('change');
    }

    global.SciafCatalogos = {
        etiquetaCodigo, codigoGrupoVisible, normalizarTexto,
        MARCA_ACCESORIOS, tieneAccesorios, quitarAccesorios, ponerAccesorios,
        estadoAccesoriosLote, aArreglo,
        crearAuxiliarRapido, ponerAuxiliarEnSelect
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
