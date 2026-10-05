/**
 * sciaf-modulo.js
 * Comportamiento de la plantilla de pantallas de mantenimiento (estilos en
 * css/sciaf-modulo.css). La primera pantalla que la usa es Rol: templates/rol/.
 *
 * Tres piezas, cada una opcional:
 *
 *   SciafModulo.tabla({...})       DataTable con el buscador y el selector de filas de la
 *                                  barra de la pantalla. Por defecto muestra las filas que
 *                                  entran en la pantalla sin hacer scroll ("Ajustar").
 *                                  Al recargar la tabla (guardar, eliminar, SSE) conserva
 *                                  la búsqueda, el orden y la página. Con dt.serverSide
 *                                  pagina en el servidor (ver templates/persona/vista.html).
 *
 *   SciafModulo.formulario({...})  Envío AJAX de un formulario en modal que responde
 *                                  JSON { ok, msg }: valida, pregunta antes de guardar
 *                                  (sciafConfirmarEnvio, como el resto del sistema),
 *                                  bloquea mientras guarda, no deja cerrar el modal a
 *                                  medio guardar, avisa.
 *
 *   SciafModulo.eliminar({...})    Confirmación + POST que responde JSON { ok, msg }.
 *
 *   SciafModulo.abrirModal(...)    Abre el modal con un "Cargando…" mientras llega el
 *                                  formulario (antes se veía el formulario anterior).
 */
(function () {
    'use strict';

    const ANCHO_MOVIL = 768;   // igual que sciaf-responsive.js: debajo, la tabla son tarjetas
    const FILAS_MOVIL = 10;

    const IDIOMA = {
        sProcessing: 'Procesando…',
        sLengthMenu: 'Mostrar _MENU_ registros',
        sZeroRecords: 'No se encontraron resultados para la búsqueda',
        sEmptyTable: 'No hay registros todavía',
        sInfo: 'Mostrando <b>_START_</b>–<b>_END_</b> de <b>_TOTAL_</b>',
        sInfoEmpty: 'Sin registros para mostrar',
        sInfoFiltered: '(filtrado de _MAX_)',
        sThousands: '.',
        sSearch: 'Buscar:',
        sLoadingRecords: 'Cargando…',
        oPaginate: {
            sFirst: '«',
            sLast: '»',
            sNext: '<i class="ti ti-chevron-right"></i>',
            sPrevious: '<i class="ti ti-chevron-left"></i>'
        }
    };

    /* Estado por tabla, para que una recarga no le cambie la vista al usuario. Se guarda
       en el elemento "dueño" (el buscador, que vive en la vista y sobrevive a las recargas
       del fragmento): si la pantalla se vuelve a abrir desde el menú, el buscador es otro
       y se empieza de cero. Atado al elemento y no a un id: con varias pestañas abiertas
       los módulos repiten ids (#data-table) y se pisarían. */
    const MEMORIA = 'smMemoria';
    let secuencia = 0;

    /* Filtro por fila (estado, conectado, …): cada tabla guarda su condición en su propio
       elemento; un solo filtro global de DataTables la consulta. */
    let filtroFilaRegistrado = false;
    function registrarFiltroFila() {
        if (filtroFilaRegistrado) return;
        filtroFilaRegistrado = true;
        $.fn.dataTable.ext.search.push(function (settings, data, index) {
            const fn = $.data(settings.nTable, 'smFiltroFila');
            if (typeof fn !== 'function' || settings.oFeatures.bServerSide) return true;
            const fila = settings.aoData[index];
            return !fila || !fila.nTr || fn(fila.nTr) !== false;
        });
    }

    function enPagina(nodo) {
        return !!nodo && document.body.contains(nodo);
    }

    function escaparRegex(t) {
        return String(t).replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    }

    const ALTO_PIE = 58;       // pie de la tabla (info + paginación), antes de existir

    /** Alto libre desde {@code tope} (coordenada de página) hasta el borde inferior de la ventana. */
    function espacioLibre(tope, pie, tarjeta) {
        const altoPie = pie ? pie.getBoundingClientRect().height : ALTO_PIE;
        // Lo que la tarjeta tiene debajo del pie (borde) y el relleno inferior del contenido.
        const bajoPie = (pie && tarjeta) ? Math.max(0, tarjeta.getBoundingClientRect().bottom - pie.getBoundingClientRect().bottom) : 1;
        const contenido = document.getElementById('contenido');
        const relleno = contenido ? (parseFloat(getComputedStyle(contenido).paddingBottom) || 0) : 16;
        return window.innerHeight - tope - altoPie - bajoPie - relleno - 6;
    }

    /** Cuántas filas entran entre el comienzo del cuerpo de la tabla y el borde de la ventana. */
    function filasQueCaben(api, o) {
        if (window.innerWidth < ANCHO_MOVIL) return FILAS_MOVIL;

        const cuerpo = api.table().body();
        const fila = cuerpo.querySelector('tr');
        let alto = 0;
        if (fila && !fila.querySelector('.dataTables_empty')) alto = fila.getBoundingClientRect().height;
        if (!alto) alto = o.altoFila;

        const envoltura = api.table().container();
        const tope = cuerpo.getBoundingClientRect().top + window.scrollY;
        const libre = espacioLibre(tope, envoltura.querySelector('.sm-dt-pie'), envoltura.closest('.sm-card'));
        return Math.max(o.minFilas, Math.floor(libre / alto));
    }

    /** Lo mismo, antes de crear la tabla (aún sin filas): con el alto de fila típico.
        En las tablas paginadas en el servidor evita pedir una página y enseguida otra. */
    function filasEstimadas($t, o) {
        if (window.innerWidth < ANCHO_MOVIL || !$t[0].tHead || !enPagina($t[0])) return FILAS_MOVIL;
        const tope = $t[0].tHead.getBoundingClientRect().bottom + window.scrollY;
        return Math.max(o.minFilas, Math.floor(espacioLibre(tope, null, null) / o.altoFila));
    }

    /**
     * Las referencias (tabla, buscador, largo, total, filtros) pueden ser selectores o
     * elementos/jQuery. En las pantallas conviene pasar elementos buscados dentro de la raíz
     * del módulo ($raiz.find(...)): así una recarga en segundo plano (SSE) de una pestaña
     * oculta no termina tocando la tabla de la pestaña que se está mirando.
     */
    function tabla(opciones) {
        const o = Object.assign({
            tabla: '#data-table',
            buscador: null,          // input de texto de la barra
            largo: null,             // select con 'auto', 10, 25, 50, 100, -1
            total: null,             // elemento donde escribir el total de registros
            filtros: [],             // [{ selector, columna }]: coincidencia exacta en una columna
            filtroFila: null,        // fn(tr) → false para ocultar la fila (datos en data-*)
            orden: [],
            noOrdenables: [],
            minFilas: 5,
            altoFila: 56,            // px de una fila típica, para estimar antes de medir
            dt: {},                  // opciones extra de DataTables (serverSide, ajax, columns…)
            alDibujar: null
        }, opciones);

        const $t = $(o.tabla).first();
        if (!$t.length) return null;

        const servidor = !!o.dt.serverSide;
        const $buscador = o.buscador ? $(o.buscador).first() : $();
        const $largo = o.largo ? $(o.largo).first() : $();
        const $total = o.total ? $(o.total) : $();
        const $tarjeta = $t.closest('.sm-card');
        const duenio = $buscador[0] || $tarjeta[0] || $t.parent()[0];
        const anterior = $.data(duenio, MEMORIA) || null;
        if (!$.data(duenio, 'smId')) $.data(duenio, 'smId', ++secuencia);

        if ($.fn.DataTable.isDataTable($t)) $t.DataTable().destroy();

        if (typeof o.filtroFila === 'function') {
            registrarFiltroFila();
            $.data($t[0], 'smFiltroFila', o.filtroFila);
        }

        const columnDefs = o.noOrdenables.length
            ? [{ targets: o.noOrdenables, orderable: false, searchable: false }]
            : [];

        const largoElegido = ($largo.length && $largo.val() !== 'auto') ? parseInt($largo.val(), 10) : null;
        const largoInicial = largoElegido || filasEstimadas($t, o);

        // Barra de carga dentro de la tarjeta (en vez de tapar toda la pantalla).
        // Se engancha antes de crear la tabla para no perder la primera petición.
        $t.off('processing.dt.smModulo').on('processing.dt.smModulo', function (e, s, procesando) {
            $tarjeta.toggleClass('sm-procesando', !!procesando);
        });

        const api = $t.DataTable(Object.assign({
            dom: 't<"sm-dt-pie"ip>',
            language: IDIOMA,
            autoWidth: false,
            pageLength: largoInicial,
            pagingType: 'simple_numbers',
            order: anterior ? anterior.orden : o.orden,
            columnDefs: columnDefs,
            search: { search: ($buscador.val() || '').trim() },
            // La página donde estaba el usuario antes de recargar.
            displayStart: (anterior && largoInicial > 0) ? anterior.pagina * largoInicial : 0
        }, o.dt, {
            // Textos: los de la pantalla (p. ej. solo sEmptyTable) SE SUMAN al español. Antes un
            // language propio reemplazaba el objeto entero y la tabla quedaba en inglés.
            language: Object.assign({}, IDIOMA, (o.dt && o.dt.language) || {})
        }));

        /* ── Filtros exactos por columna (estado, tipo, …) ─────────────── */
        function aplicarFiltro(f) {
            const v = $(f.selector).val();
            api.column(f.columna).search(v ? '^' + escaparRegex(v) + '$' : '', true, false);
        }
        o.filtros.forEach(function (f) {
            aplicarFiltro(f);
            $(f.selector).off('.smModulo').on('change.smModulo', function () {
                aplicarFiltro(f);
                api.draw();
            });
        });

        /* ── Cantidad de filas: "Ajustar" o un número fijo ─────────────── */
        function modoAuto() {
            return !$largo.length || $largo.val() === 'auto';
        }

        function ajustar() {
            if (!enPagina(api.table().node())) return;   // pestaña oculta: se mide al volver
            if (modoAuto()) {
                const n = filasQueCaben(api, o);
                $largo.find('option[value="auto"]').text('Ajustar a pantalla (' + n + ')');
                if (n !== api.page.len()) api.page.len(n).draw(false);
            } else {
                const n = parseInt($largo.val(), 10);
                if (n !== api.page.len()) api.page.len(n).draw(false);
            }
        }

        $largo.off('.smModulo').on('change.smModulo', ajustar);

        /* ── Buscador general ──────────────────────────────────────────── */
        const $cajaBuscador = $buscador.closest('.sm-buscador');
        let espera = null;
        function marcarTexto() {
            $cajaBuscador.toggleClass('con-texto', !!$buscador.val());
        }
        $buscador.off('.smModulo')
            .on('input.smModulo', function () {
                marcarTexto();
                clearTimeout(espera);
                // En el servidor cada búsqueda es una consulta: se espera a que termine de escribir.
                espera = setTimeout(function () { api.search($buscador.val().trim()).draw(); }, servidor ? 350 : 160);
            })
            .on('keydown.smModulo', function (e) {
                if (e.key === 'Escape' && $buscador.val()) {
                    e.preventDefault();
                    $buscador.val('');
                    marcarTexto();
                    api.search('').draw();
                }
            });
        $cajaBuscador.find('.sm-buscador-limpiar').off('.smModulo').on('click.smModulo', function () {
            $buscador.val('').trigger('focus');
            marcarTexto();
            api.search('').draw();
        });
        marcarTexto();

        /* ── Totales y memoria ─────────────────────────────────────────── */
        function alDibujar() {
            // recordsTotal: total sin filtrar, también cuando la paginación es del servidor.
            $total.text(String(api.page.info().recordsTotal).replace(/\B(?=(\d{3})+(?!\d))/g, '.'));
            $.data(duenio, MEMORIA, { pagina: api.page(), orden: api.order() });
            if (typeof o.alDibujar === 'function') o.alDibujar(api);
        }
        $t.on('draw.dt', alDibujar);

        // Medir con filas reales: en el navegador ya están pintadas; del servidor,
        // cuando llega la primera página (la estimación inicial suele acertar).
        if (servidor) {
            $t.one('draw.dt', ajustar);
        } else {
            ajustar();
            alDibujar();
        }

        // Al cambiar el tamaño de la ventana, recalcular (solo en "Ajustar"). Uno por
        // pantalla: la recarga del fragmento reemplaza el anterior. Con la pestaña oculta
        // no hace nada (ajustar lo descarta) pero sigue enganchado para cuando vuelva.
        const evento = 'resize.smModulo' + $.data(duenio, 'smId');
        let esperaResize = null;
        function reajustar() {
            if (modoAuto()) ajustar();
        }
        $(window).off(evento).on(evento, function () {
            clearTimeout(esperaResize);
            esperaResize = setTimeout(reajustar, 180);
        });
        // Si la fuente de íconos todavía no llegó, las filas miden distinto: se vuelve a medir.
        if (document.fonts && document.fonts.status !== 'loaded') document.fonts.ready.then(reajustar);

        return api;
    }

    /* ══ Pedidos al servidor (un solo viaje) ═════════════════════════════
       Antes cada formulario, eliminación o acción preguntaba primero /adm/cargar-datos
       ("¿sigue la sesión?") y recién después hacía el pedido real: dos viajes por clic,
       y con la base remota cada viaje se nota. Ya no hace falta: sciaf-presencia.js
       mantiene viva la sesión y sciaf-sesion.js reconoce en CUALQUIER respuesta que la
       sesión se perdió (401 o redirección al ingreso) y muestra su aviso sin perder lo que
       hay en pantalla. Acá solo se corta el flujo cuando eso pasa. */

    const CABECERAS = { 'X-Requested-With': 'XMLHttpRequest' };

    /** La sesión se perdió: sciaf-sesion.js ya mostró su aviso; quien llama solo se detiene. */
    class SesionPerdida extends Error {
        constructor() { super('Sesión perdida'); this.sesionPerdida = true; }
    }

    function cuerpoDe(datos, init) {
        if (datos == null) return;
        if (typeof datos === 'string') {                 // $form.serialize()
            init.headers['Content-Type'] = 'application/x-www-form-urlencoded; charset=UTF-8';
            init.body = datos;
            return;
        }
        if (datos instanceof FormData || datos instanceof URLSearchParams) { init.body = datos; return; }
        const p = new URLSearchParams();
        Object.keys(datos).forEach(function (k) {
            const v = datos[k];
            if (v == null) return;
            (Array.isArray(v) ? v : [v]).forEach(function (x) { p.append(k, x); });
        });
        init.body = p;
    }

    /**
     * fetch con las cabeceras del sistema. {@code o.datos}: objeto, FormData o texto
     * serializado; {@code o.json}: cuerpo JSON. Lanza SesionPerdida si volvió el ingreso.
     */
    async function pedir(url, o) {
        o = o || {};
        const init = { method: o.metodo || 'POST', headers: Object.assign({}, CABECERAS), credentials: 'same-origin' };
        if (o.json !== undefined) {
            init.headers['Content-Type'] = 'application/json';
            init.body = JSON.stringify(o.json);
        } else {
            cuerpoDe(o.datos, init);
        }
        if (init.method === 'GET') delete init.body;
        const r = await fetch(url, init);
        if (window.sciafSesion && window.sciafSesion.perdida(r)) throw new SesionPerdida();
        return r;
    }

    /** Pedido que responde JSON { ok, msg, … }. Un error HTTP con JSON se devuelve igual (ok:false). */
    async function pedirJson(url, o) {
        const r = await pedir(url, o);
        let j = null;
        try { j = await r.json(); } catch (e) { /* no era JSON */ }
        if (!j || typeof j !== 'object') {
            throw new Error(r.ok ? 'El servidor respondió algo inesperado.' : 'Error ' + r.status + ' del servidor.');
        }
        if (!r.ok && j.ok === undefined) j.ok = false;
        if (r.status === 403 && !j.msg) j.msg = 'No tiene permiso para esta acción.';
        return j;
    }

    /**
     * Trae un fragmento HTML y lo pone en el contenedor (sus &lt;script&gt; se ejecutan).
     * Inicia las listas .select2 que el fragmento no haya iniciado (como fragment.js).
     */
    async function cargar(contenedor, url, datos, metodo) {
        const $c = $(contenedor);
        const r = await pedir(url, { datos: datos || {}, metodo: metodo || 'POST' });
        if (!r.ok) {
            const e = new Error(window.SciafPrecarga ? SciafPrecarga.motivo(r.status) : 'Error ' + r.status + ' al cargar.');
            e.status = r.status;
            throw e;
        }
        const html = await r.text();
        if (window.SciafPrecarga) SciafPrecarga.cerrarEn($c);   // detiene sus relojes antes de reemplazarlo
        $c.html(html);
        if ($.fn.select2) {
            $c.find('select.select2').each(function () {
                const $s = $(this);
                if ($s.data('select2')) return;
                const $padre = $s.closest('form');
                $s.select2({ dropdownParent: $padre.length ? $padre : $c, width: '100%' });
            });
        }
        return $c;
    }

    /** Aviso de error estándar (la sesión perdida ya la avisó sciaf-sesion.js). */
    function avisarError(e, titulo) {
        if (e && e.sesionPerdida) return;
        Swal.fire(titulo || 'No se pudo completar', (e && e.message) || 'Intente de nuevo.', 'error');
    }

    /* ══ Modal ═══════════════════════════════════════════════════════════ */

    function abrirModal(modal, contenedor, texto) {
        // El preloader del sistema (sciaf-precarga.js), en su versión compacta.
        const carga = SciafPrecarga.montar(contenedor, { compacta: true, texto: texto || 'Cargando formulario…', demora: 0 });
        bootstrap.Modal.getOrCreateInstance($(modal)[0]).show();
        return carga;
    }

    /**
     * Abre el modal con "Cargando…" y trae el formulario en UN solo pedido (reemplaza a
     * cargarFormularioAlert/cargarFormularioEditAlert de fragment.js, que hacían dos).
     */
    function abrirFormulario(modal, contenedor, url, datos, texto) {
        const carga = abrirModal(modal, contenedor, texto);
        return cargar(contenedor, url, datos).catch(function (e) {
            if (e && e.sesionPerdida) {
                bootstrap.Modal.getOrCreateInstance($(modal)[0]).hide();
                return;
            }
            carga.error('No se pudo cargar el formulario', (e && e.status) ? e.message : SciafPrecarga.motivo(0), {
                alReintentar: function () { abrirFormulario(modal, contenedor, url, datos, texto); }
            });
            $(carga.elemento).find('.sp-acciones').append(
                '<button type="button" class="sm-btn sm-btn-sm sm-btn-neutro" data-bs-dismiss="modal"><i class="ti ti-x"></i> Cerrar</button>');
        });
    }

    /**
     * Qué recargar cuando el formulario de ese modal guarda bien. Lo declara la vista
     * (que es la que conoce su tabla) y lo usa SciafModulo.formulario: así el formulario no
     * depende de un cargarTabla global, que con varias pestañas abiertas apunta a otro módulo.
     */
    function alGuardarModal(modal, fn) {
        $(modal).data('smRecargar', fn);
    }

    /**
     * Para contenidos que no son tabla paginada (un árbol, una lista larga): limita el alto
     * del elemento a lo que queda de ventana y le da su propio scroll, así la página no se
     * desplaza. Se recalcula al cambiar el tamaño de la ventana.
     */
    function ajustarAlto(elemento, margen) {
        const el = $(elemento)[0];
        if (!el) return function () {};
        const extra = margen == null ? 8 : margen;
        function calcular() {
            if (!enPagina(el) || window.innerWidth < ANCHO_MOVIL) { el.style.maxHeight = ''; return; }
            const contenido = document.getElementById('contenido');
            const relleno = contenido ? (parseFloat(getComputedStyle(contenido).paddingBottom) || 0) : 16;
            const tarjeta = el.closest('.sm-card');
            const bajo = tarjeta ? Math.max(0, tarjeta.getBoundingClientRect().bottom - el.getBoundingClientRect().bottom) : 0;
            const tope = el.getBoundingClientRect().top + window.scrollY;
            el.style.maxHeight = Math.max(240, window.innerHeight - tope - bajo - relleno - extra) + 'px';
            el.style.overflowY = 'auto';
        }
        const id = 'resize.smAlto' + (++secuencia);
        let espera = null;
        $(window).on(id, function () { clearTimeout(espera); espera = setTimeout(calcular, 150); });
        calcular();
        return calcular;
    }

    /**
     * Acción puntual con confirmación (activar, bloquear, restaurar…): pregunta, hace UN
     * POST que responde { ok, msg } y avisa. {@code alTerminar(respuesta)} recarga lo que haga falta.
     */
    function accion(opciones) {
        const o = Object.assign({ url: null, datos: null, confirmacion: null, alTerminar: null, avisoOk: true }, opciones);
        const pregunta = o.confirmacion && window.sciafConfirmar ? window.sciafConfirmar(o.confirmacion) : Promise.resolve(true);
        return pregunta.then(function (si) {
            if (!si) return null;
            return pedirJson(o.url, { datos: o.datos }).then(function (res) {
                if (!res.ok) {
                    Swal.fire('Atención', res.msg || 'No se pudo completar la acción.', 'warning');
                    return res;
                }
                if (o.avisoOk) {
                    Swal.fire({ toast: true, position: 'top-end', icon: 'success', title: res.msg || 'Listo',
                        showConfirmButton: false, timer: 3500, timerProgressBar: true });
                }
                if (typeof o.alTerminar === 'function') o.alTerminar(res);
                return res;
            }, function (e) { avisarError(e); return null; });
        });
    }

    /* ══ Formulario ══════════════════════════════════════════════════════ */

    const SELECTOR_MAYUS = 'input[type=text]:not([data-sm-mayus=no]), input:not([type]):not([data-sm-mayus=no]), ' +
        'textarea:not([data-sm-mayus=no])';

    /** Pasa el valor a mayúsculas sin mover el cursor de lugar. */
    function aMayusculas(el) {
        const v = el.value;
        const may = v.toLocaleUpperCase('es');
        if (v === may) return;
        const ini = el.selectionStart, fin = el.selectionEnd;
        el.value = may;
        if (document.activeElement === el && ini != null) {
            try { el.setSelectionRange(ini, fin); } catch (e) { /* tipos sin selección */ }
        }
    }

    function formulario(opciones) {
        const o = Object.assign({
            form: null,
            modal: null,
            guardar: null,           // botón submit
            cancelar: null,
            textoGuardando: 'Guardando…',
            msgExito: 'Registro guardado',
            confirmar: true,         // preguntar "¿Registrar…?" antes de enviar (sciafConfirmarEnvio)
            confirmacion: null,      // { titulo, texto, aceptar } (o fn que lo devuelve) si el genérico no sirve
            mayusculas: true,        // texto en MAYÚSCULAS; excluir un campo con data-sm-mayus="no"
            validar: null,           // fn() → true/false, validaciones extra
            alGuardar: null,         // fn(respuesta) extra; la recarga de la tabla la pone la
                                     // vista con SciafModulo.alGuardarModal(modal, fn)
            interceptar: null        // fn(respuesta, api) → true si la manejó el módulo (pedir
                                     // autorización, confirmar algo y reenviar…). api.reenviar(url, extras)
                                     // vuelve a enviar el formulario; api.cancelar() lo libera.
        }, opciones);

        const $form = $(o.form);
        const $modal = $(o.modal);
        const $btnGuardar = $(o.guardar);
        const $btnCancelar = $(o.cancelar);
        const $btnCerrar = $modal.find('.btn-close');
        const htmlOriginal = $btnGuardar.html();
        let enviando = false;

        // No se cierra el modal mientras se guarda.
        $modal.off('hide.bs.modal.smForm').on('hide.bs.modal.smForm', function (e) {
            if (enviando) e.preventDefault();
        });

        // Los datos se registran en MAYÚSCULAS (como en el VSIAF). Se convierte mientras se
        // escribe y también lo que ya traía el registro, para que al guardar quede parejo.
        // Correo, usuario de acceso y similares se excluyen con data-sm-mayus="no".
        if (o.mayusculas) {
            $form.find(SELECTOR_MAYUS).each(function () { aMayusculas(this); });
            $form.on('input', SELECTOR_MAYUS, function () { aMayusculas(this); });
        } else {
            $form.addClass('sm-sin-mayus');   // que tampoco se VEA en mayúsculas
        }

        // Al corregir un campo se le quita la marca de error.
        $form.on('input change', 'input, select, textarea', function () {
            $(this).removeClass('is-invalid');
        });

        // Enter en un campo de texto no envía a medias.
        $form.on('keydown', 'input:not([type=submit])', function (e) {
            if (e.key === 'Enter') e.preventDefault();
        });

        function validarRequeridos() {
            let ok = true;
            // Los deshabilitados no se envían (campos de otro tipo, ocultos): no se exigen.
            $form.find('[required]:not(:disabled)').each(function () {
                const $c = $(this);
                const v = ($c.val() || '').toString().trim();
                const max = parseInt($c.attr('maxlength'), 10);
                const malo = !v || (max > 0 && v.length > max);
                $c.toggleClass('is-invalid', malo);
                if (malo) ok = false;
            });
            return ok;
        }

        /** Sin argumento solo deshabilita (mientras se pregunta); con true muestra "Guardando…". */
        function bloquear(conSpinner) {
            enviando = true;
            $btnGuardar.prop('disabled', true);
            if (conSpinner) $btnGuardar.html(
                '<span class="spinner-border spinner-border-sm" role="status" aria-hidden="true"></span> ' + o.textoGuardando);
            $btnCancelar.prop('disabled', true);
            $btnCerrar.prop('disabled', true).css('pointer-events', 'none');
        }

        function desbloquear() {
            enviando = false;
            $btnGuardar.prop('disabled', false).html(htmlOriginal);
            $btnCancelar.prop('disabled', false);
            $btnCerrar.prop('disabled', false).css('pointer-events', '');
        }

        $form.on('submit', function (e) {
            e.preventDefault();
            if (enviando) return;

            // Primero obligatorios y luego las reglas propias del módulo: así una marca de
            // error de la regla propia (contraseña débil…) no la borra la revisión general.
            const requeridosOk = validarRequeridos();
            const extra = typeof o.validar === 'function' ? o.validar() : true;
            if (!requeridosOk || !extra) {
                $form.find('.is-invalid').first().trigger('focus');
                return;
            }

            // Igual que el resto del sistema (fragment.js): siempre se pregunta antes de
            // guardar. Mientras la pregunta está abierta el botón queda bloqueado, para
            // que un doble clic no abra dos confirmaciones.
            bloquear();
            let pregunta = Promise.resolve(true);
            if (o.confirmar !== false) {
                // confirmacion puede ser una función: el texto se arma con lo que hay al enviar.
                const conf = typeof o.confirmacion === 'function' ? o.confirmacion() : o.confirmacion;
                if (conf && window.sciafConfirmar) pregunta = window.sciafConfirmar(conf);
                else if (window.sciafConfirmarEnvio) pregunta = window.sciafConfirmarEnvio($form[0]);
            }
            pregunta.then(function (seguir) {
                if (seguir) enviar();
                else desbloquear();
            });
        });

        function enviar(url, extras) {
            bloquear(true);
            const datos = $form.serialize() + (extras ? '&' + $.param(extras) : '');
            pedirJson(url || $form.attr('action'), { datos: datos }).then(function (res) {
                if (typeof o.interceptar === 'function' && o.interceptar(res, {
                    reenviar: function (u, x) { enviar(u, x); },
                    cancelar: desbloquear
                })) return;   // el módulo sigue el trámite (y llama a reenviar o cancelar)
                if (res && res.ok) {
                    enviando = false;
                    $modal.modal('hide');
                    const recargar = $modal.data('smRecargar');
                    if (typeof recargar === 'function') recargar(res);
                    // Primero el aviso y después alGuardar: SweetAlert muestra uno solo a la
                    // vez, así un diálogo propio de alGuardar reemplaza al aviso y no al revés.
                    Swal.fire({
                        toast: true,
                        position: 'top-end',
                        icon: 'success',
                        title: res.msg || o.msgExito,
                        showConfirmButton: false,
                        timer: 3500,
                        timerProgressBar: true
                    });
                    if (typeof o.alGuardar === 'function') o.alGuardar(res);
                } else {
                    desbloquear();
                    // Errores por campo (validación del servidor) o un mensaje.
                    const errores = res && Array.isArray(res.errors) && res.errors.length
                        ? res.errors.map(e => escapar(e.message || e.field)).join('<br>') : null;
                    Swal.fire({ icon: 'warning', title: 'Atención',
                        html: errores || escapar((res && res.msg) || 'No se pudo completar la operación.') });
                }
            }, function (e) {
                // Sesión perdida: lo escrito sigue en el formulario para volver a guardar.
                desbloquear();
                avisarError(e, 'Error al guardar');
            });
        }
    }

    /* ══ Eliminar ════════════════════════════════════════════════════════ */

    function eliminar(opciones) {
        const o = Object.assign({
            url: null,
            titulo: '¿Eliminar registro?',
            html: '',
            confirmar: 'Sí, eliminar',
            alTerminar: null
        }, opciones);

        // Un solo pedido: la sesión perdida la detecta sciaf-sesion.js en la respuesta.
        Swal.fire({
            title: o.titulo,
            html: o.html,
            icon: 'warning',
            showCancelButton: true,
            confirmButtonText: o.confirmar,
            cancelButtonText: 'Cancelar',
            confirmButtonColor: '#c92a2a',
            reverseButtons: true,
            focusCancel: true,
            showLoaderOnConfirm: true,
            allowOutsideClick: function () { return !Swal.isLoading(); },
            preConfirm: function () {
                return pedirJson(o.url).then(function (res) {
                    // El servidor explica por qué no (sin permiso, regla de negocio…).
                    if (!res.ok) Swal.showValidationMessage(res.msg || 'No se pudo eliminar.');
                    return res;
                }, function (e) {
                    if (e && e.sesionPerdida) return false;   // ya está el aviso de sesión
                    Swal.showValidationMessage('Hubo un problema al eliminar. Inténtelo de nuevo.');
                    return false;
                });
            }
        }).then(function (r) {
            if (!r.isConfirmed || !r.value || !r.value.ok) return;
            Swal.fire({
                toast: true,
                position: 'top-end',
                icon: 'success',
                title: r.value.msg || 'Registro eliminado',
                showConfirmButton: false,
                timer: 3500,
                timerProgressBar: true
            });
            if (typeof o.alTerminar === 'function') o.alTerminar(r.value);
        });
    }

    /* ══ Catálogos sincronizados con el VSIAF ════════════════════════════
       Grupo contable, auxiliar, organismo financiador…: la tabla se trae del VSIAF con
       /sync-from-mounted. Lo mismo en todos: pregunta (si es completa), bloquea el botón,
       barra de la tarjeta, resumen de lo que pasó y recarga. */

    /**
     * {@code o.url} (…/sync-from-mounted), {@code o.completo}, {@code o.boton}, {@code o.tarjeta},
     * {@code o.extras}: filas más del resumen [[etiqueta, campo, clase]], {@code o.alTerminar(r)}.
     */
    function sincronizarVsiaf(o) {
        const pregunta = o.completo && window.sciafConfirmar
            ? window.sciafConfirmar({ titulo: '¿Sincronización completa?',
                texto: 'Se vuelven a procesar todos los registros aunque no hayan cambiado. Tarda más.',
                aceptar: 'Sí, sincronizar' })
            : Promise.resolve(true);
        return pregunta.then(function (si) {
            if (!si) return null;
            const $b = $(o.boton);
            const html = $b.html();
            $b.prop('disabled', true).html('<span class="spinner-border spinner-border-sm"></span> Sincronizando…');
            $(o.tarjeta).addClass('sm-procesando');
            return pedirJson(o.url, { datos: { forzarCompleto: !!o.completo } })
                .then(function (r) {
                    if (!r.ok) {
                        Swal.fire('No se pudo sincronizar', r.message || r.msg || 'Revise la conexión con el VSIAF.', 'error');
                        return r;
                    }
                    const filas = [['Leídos del VSIAF', 'totalLeidas', ''], ['Nuevos', 'insertados', 'text-success'],
                        ['Actualizados', 'actualizados', 'text-primary'], ['Sin cambios', 'omitidos', 'text-muted']]
                        .concat(o.extras || [])
                        .filter(f => r[f[1]] != null);
                    Swal.fire({
                        icon: 'success', title: 'Sincronización completa',
                        html: '<div class="text-start">' + filas.map(f =>
                                '<div class="d-flex justify-content-between ' + f[2] + '"><span>' + escapar(f[0])
                                + '</span><b>' + escapar(r[f[1]]) + '</b></div>').join('')
                            + '<div class="d-flex justify-content-between text-muted mt-2"><span>Tiempo</span><b>'
                            + (Number(r.duracionMs || 0) / 1000).toFixed(2) + ' s</b></div></div>'
                    });
                    if (typeof o.alTerminar === 'function') o.alTerminar(r);
                    return r;
                }, function (e) { avisarError(e, 'No se pudo sincronizar'); return null; })
                .finally(function () {
                    $b.prop('disabled', false).html(html);
                    $(o.tarjeta).removeClass('sm-procesando');
                });
        });
    }

    /**
     * Pasa a la cabecera el estado de la última sincronización que viene en el fragmento
     * (.js-sync-info con data-ultima, data-estado, data-origen…). {@code chip}: el dato de la
     * cabecera (con un .js-sync-texto adentro); {@code avisoDbf}: aviso de "se ve directo del VSIAF".
     */
    function estadoSync(info, chip, avisoDbf) {
        const el = $(info)[0];
        if (!el) return;
        const s = el.dataset;
        const nunca = !s.ultima || s.ultima.indexOf('Nunca') === 0;
        const $chip = $(chip);
        $chip.find('.js-sync-texto').text(nunca ? 'Nunca sincronizado' : 'Sincronizado ' + s.ultima);
        $chip.attr('title', 'Estado: ' + s.estado + ' · procesados ' + s.procesados + ' · nuevos ' + s.nuevos
            + ' · actualizados ' + s.actualizados + ' · ' + s.duracion + ' s');
        $chip.find('i').first().toggleClass('text-danger', s.estado === 'ERROR').toggleClass('text-success', s.estado === 'COMPLETADO');
        $(avisoDbf).toggleClass('d-none', s.origen !== 'dbf');
    }

    /** Texto seguro para meter en el html de un SweetAlert. */
    function escapar(t) {
        return $('<div>').text(t == null ? '' : t).html();
    }

    window.SciafModulo = {
        tabla, formulario, eliminar, accion,
        abrirModal, abrirFormulario, alGuardarModal, ajustarAlto,
        pedir, pedirJson, cargar, avisarError, escapar,
        sincronizarVsiaf, estadoSync
    };
})();
