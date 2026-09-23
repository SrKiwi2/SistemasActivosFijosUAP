/**
 * sciaf-pestanas.js
 * Pestañas internas del SCIAF + respaldo de lo que el usuario está haciendo.
 *
 * Problema que resuelve: el sistema carga cada módulo dentro de #contenido, así que ir a
 * Consulta de Activos a verificar un dato en medio de una transferencia (o de un registro)
 * borraba todo lo cargado y había que empezar de nuevo.
 *
 * Cómo funciona:
 *  1. Cada opción del menú abre (o trae al frente) una pestaña. El clic del menú se
 *     intercepta en fase de captura: el layout tiene su propio manejador que reemplaza
 *     #contenido, y si corriera se perdería el estado igual que antes.
 *  2. Al dejar una pestaña, su DOM se DESPRENDE (detach) y vuelve intacto al regresar —con
 *     lo escrito, lo seleccionado, las tablas ya cargadas y el scroll—. Se desprende en vez
 *     de ocultarse porque los módulos comparten ids (#data-table, #tablaRegistro…) y si
 *     convivieran en la página se pisarían entre sí.
 *  3. Además, el contenido de los campos de la pestaña activa se guarda en el servidor cada
 *     pocos segundos y al cerrar. Si se cierra el navegador, se corta la conexión o se
 *     vuelve al día siguiente, al reabrir la pestaña se recarga la pantalla y se repone lo
 *     que había escrito o elegido.
 *  4. Una pantalla con estado que no vive en sus campos (las filas de un lote, una lista
 *     armada a mano…) se registra con window.sciafModulo.registrar(clave, api) para
 *     completar lo que el guardado genérico no alcanza.
 */
(function () {
    'use strict';

    const API = clave => `/api/espacio/${encodeURIComponent(clave)}`;
    const CLAVE_PESTANAS = '__pestanas';
    const INICIO = '/adm/inicio';
    const MAX_PESTANAS = 12;
    const AUTOGUARDADO_MS = 4000;

    /* ══════════ Respaldo (servidor + copia local por si falla la red) ══════════ */

    const Espacio = {
        async leer(clave) {
            try {
                const r = await fetch(API(clave), { headers: { 'X-Requested-With': 'XMLHttpRequest' } });
                if (r.ok) {
                    const d = await r.json();
                    if (d && d.datos) return JSON.parse(d.datos);
                }
            } catch (_) { /* sin red: se usa la copia local */ }
            try {
                const local = localStorage.getItem('sciaf.espacio.' + clave);
                return local ? JSON.parse(local) : null;
            } catch (_) { return null; }
        },
        async guardar(clave, obj, alCerrar) {
            const texto = JSON.stringify(obj ?? null);
            try { localStorage.setItem('sciaf.espacio.' + clave, texto); } catch (_) {}
            try {
                await fetch(API(clave), {
                    method: 'PUT',
                    headers: { 'Content-Type': 'application/json', 'X-Requested-With': 'XMLHttpRequest' },
                    body: texto,
                    keepalive: !!alCerrar      // que el guardado sobreviva al cierre de la pestaña
                });
            } catch (_) { /* queda la copia local */ }
        },
        async limpiar(clave) {
            try { localStorage.removeItem('sciaf.espacio.' + clave); } catch (_) {}
            try { await fetch(API(clave), { method: 'DELETE', headers: { 'X-Requested-With': 'XMLHttpRequest' } }); } catch (_) {}
        }
    };
    window.sciafEspacio = Espacio;

    /** Borrador explícito de una pantalla (lo usa, por ejemplo, la vista de transferencia). */
    const pendientes = {};
    window.sciafBorrador = {
        guardar(clave, obj) {
            clearTimeout(pendientes[clave]);
            pendientes[clave] = setTimeout(() => Espacio.guardar('borrador.' + clave, obj), 700);
        },
        leer(clave) { return Espacio.leer('borrador.' + clave); },
        limpiar(clave) { clearTimeout(pendientes[clave]); return Espacio.limpiar('borrador.' + clave); }
    };

    /* ══════════ Estado de los campos de una pantalla ══════════ */

    /**
     * Identifica un campo de forma estable entre recargas: el módulo vuelve a renderizarse
     * igual, así que tipo + id/nombre + posición entre los de su misma firma alcanza.
     */
    function firmaDe(el, vistos) {
        const tag = el.tagName.toLowerCase();
        const base = `${tag}:${(el.type || '').toLowerCase()}:${el.id || ''}:${el.name || ''}`;
        const n = (vistos[base] = (vistos[base] || 0) + 1);
        return base + '#' + n;
    }

    function guardable(el) {
        const tipo = (el.type || '').toLowerCase();
        if (tipo === 'file' || tipo === 'password') return false;
        if (el.hasAttribute('data-sciaf-no-guardar')) return false;
        if (el.classList.contains('select2-search__field')) return false;   // input interno de select2
        // Los ocultos suelen llevar ids internos (el activo que se está editando, tokens…):
        // reponerlos metería a la pantalla en un contexto que ya no corresponde. Una pantalla
        // puede pedir que se guarde uno marcándolo con data-sciaf-guardar.
        if (tipo === 'hidden' && !el.hasAttribute('data-sciaf-guardar')) return false;
        return true;
    }

    function capturarCampos($root) {
        const vistos = {};
        const campos = [];
        $root.find('input, select, textarea').each(function () {
            const el = this;
            if (!guardable(el)) return;
            const tipo = (el.type || '').toLowerCase();
            const k = firmaDe(el, vistos);
            if (tipo === 'checkbox' || tipo === 'radio') {
                if (el.checked) campos.push({ k, c: true });
            } else if (el.multiple) {
                const v = $(el).val();
                if (v && v.length) campos.push({ k, m: v });
            } else if (el.value !== '' && el.value != null) {
                const campo = { k, v: el.value };
                // El texto de la opción elegida: los desplegables que buscan en el servidor
                // (select2 con ajax, como el responsable en Consulta de Activos) no tienen
                // ninguna opción cargada al volver, y sin el texto no se podrían reponer.
                if (el.tagName.toLowerCase() === 'select' && el.selectedOptions && el.selectedOptions[0]) {
                    const t = (el.selectedOptions[0].text || '').trim();
                    if (t && t !== el.value) campo.t = t.substring(0, 140);
                }
                campos.push(campo);
            }
        });
        return campos;
    }

    /**
     * Repone los valores guardados.
     *
     * Se hace en varias pasadas y revisando SIEMPRE todos los campos, no solo los que
     * faltan, por dos motivos reales de este sistema:
     *  - hay desplegables en cascada (predio → oficina → responsable): al reponer el padre,
     *    el módulo vacía a los hijos, así que un valor ya puesto puede volver a perderse;
     *  - hay desplegables que traen sus opciones del servidor y tardan.
     * Si al final un desplegable sigue sin tener la opción (los que buscan por AJAX nunca
     * la tienen), se le agrega con el texto que se había guardado.
     */
    async function aplicarCampos($root, campos, opciones) {
        const pasadas = (opciones && opciones.pasadas) || 8;
        const espera = (opciones && opciones.espera) || 400;
        if (!campos || !campos.length) return;

        for (let paso = 0; paso < pasadas; paso++) {
            const vistos = {};
            const porClave = {};
            $root.find('input, select, textarea').each(function () {
                if (!guardable(this)) return;
                porClave[firmaDe(this, vistos)] = this;
            });

            let pendientes = 0;
            const ultimasPasadas = paso >= pasadas - 3;

            campos.forEach(c => {
                const el = porClave[c.k];
                if (!el) { pendientes++; return; }
                const $el = $(el);
                try {
                    if (c.c !== undefined) {
                        if (!el.checked) { el.checked = true; $el.trigger('change'); pendientes++; }
                        return;
                    }
                    if (c.m) {
                        const actual = $el.val() || [];
                        if (JSON.stringify(actual) !== JSON.stringify(c.m)) {
                            $el.val(c.m).trigger('change');
                            pendientes++;
                        }
                        return;
                    }
                    if (el.value === c.v) return;          // ya está puesto
                    pendientes++;
                    if (el.tagName.toLowerCase() === 'select') {
                        const existe = Array.prototype.some.call(el.options, o => o.value === c.v);
                        if (!existe) {
                            if (!ultimasPasadas || !c.t) return;   // se le da tiempo a cargar
                            el.appendChild(new Option(c.t, c.v, true, true));
                        }
                        $el.val(c.v).trigger('change');
                        return;
                    }
                    $el.val(c.v).trigger('change');
                } catch (_) { /* un campo raro no debe frenar al resto */ }
            });

            if (!pendientes && paso > 0) return;          // todo puesto y estable
            await new Promise(r => setTimeout(r, espera));
        }
    }

    /** Pantallas que saben guardar y reponer más que sus campos. */
    const modulos = {};
    window.sciafModulo = {
        registrar(clave, api) { modulos[clave] = api || {}; },
        olvidar(clave) { delete modulos[clave]; }
    };

    /* ══════════ Pestañas ══════════ */

    $(function () {
        // Arranca donde hay menú lateral y área de contenido, que es la pantalla de
        // administración. (Antes se exigía data-ctx="admin" en el body: esa marca solo la
        // tiene la página pública, así que el gestor de pestañas nunca llegaba a arrancar.)
        const $contenido = $('#contenido');
        if (!$contenido.length || !document.getElementById('layout-menu')) return;
        if (typeof window.cargarContenido !== 'function') {
            console.warn('[Pestañas] No se encontró la navegación del layout; se omiten las pestañas.');
            return;
        }

        const cargarOriginal = window.cargarContenido;
        const pestanas = [];          // { url, titulo, icono, $dom, scroll, cargada, modulo }
        let activa = -1;
        let restaurando = false;
        let ultimoGuardado = '';

        estilos();
        const $barra = barra();

        /* ── Título e icono desde el menú ───────────────────────────── */
        function infoMenu(url) {
            const $item = $(`#layout-menu .menu-item[data-url="${url}"]`).first();
            if ($item.length) {
                return {
                    titulo: $item.find('> a > div').first().text().trim() || url,
                    icono: $item.find('> a i').attr('class') || 'ti ti-file'
                };
            }
            if (url === INICIO) return { titulo: 'Inicio', icono: 'ti ti-home' };
            return { titulo: url.split('/').filter(Boolean).pop() || 'Pantalla', icono: 'ti ti-file' };
        }

        /* ── Barra ──────────────────────────────────────────────────── */
        function render() {
            $barra.empty();
            pestanas.forEach((p, i) => {
                const $t = $(`
                    <div class="sciaf-tab${i === activa ? ' activa' : ''}" title="${escapar(p.titulo)}">
                        <i class="${escapar(p.icono)}"></i>
                        <span class="sciaf-tab-txt">${escapar(p.titulo)}</span>
                        ${p.url === INICIO ? '' : '<button class="sciaf-tab-x" title="Cerrar">&times;</button>'}
                    </div>`);
                $t.on('click', e => {
                    if ($(e.target).hasClass('sciaf-tab-x')) { cerrar(i); return; }
                    activar(i);
                });
                $t.on('contextmenu', e => { e.preventDefault(); menuContextual(e, i); });
                $barra.append($t);
            });
            $barra.append($('<button class="sciaf-tabs-menu" title="Opciones de las pestañas">⋯</button>')
                .on('click', e => menuContextual(e, activa)));
            const $act = $barra.find('.sciaf-tab.activa')[0];
            if ($act && $act.scrollIntoView) $act.scrollIntoView({ block: 'nearest', inline: 'nearest' });
        }

        /* ── Guardado del estado de la pestaña activa ───────────────── */
        function claveTab(url) { return 'tab.' + url; }

        function estadoActual() {
            if (activa < 0) return null;
            const p = pestanas[activa];
            const estado = { campos: capturarCampos($contenido), scroll: scrollActual() };
            const api = p.modulo ? modulos[p.modulo] : null;
            if (api && typeof api.guardar === 'function') {
                try { estado.modulo = { clave: p.modulo, datos: api.guardar() }; } catch (_) {}
            }
            return estado;
        }

        function guardarEstado(alCerrar) {
            if (activa < 0 || restaurando) return;
            const p = pestanas[activa];
            const estado = estadoActual();
            if (!estado) return;
            const texto = JSON.stringify(estado);
            if (!alCerrar && texto === ultimoGuardado) return;   // nada cambió
            ultimoGuardado = texto;
            Espacio.guardar(claveTab(p.url), estado, alCerrar);
        }

        setInterval(() => guardarEstado(false), AUTOGUARDADO_MS);
        window.addEventListener('beforeunload', () => { guardarEstado(true); guardarPestanas(true); });

        async function reponerEstado(p) {
            const estado = await Espacio.leer(claveTab(p.url));
            if (!estado || !((estado.campos || []).length || estado.modulo)) return;
            restaurando = true;
            try {
                const api = estado.modulo ? modulos[estado.modulo.clave] : null;
                // Primero lo que solo sabe la pantalla (abrir el formulario, crear las filas…)
                if (api && api.antes) { try { await api.antes(estado.modulo.datos); } catch (_) {} }
                await aplicarCampos($contenido, estado.campos);
                if (api && api.despues) { try { await api.despues(estado.modulo.datos); } catch (_) {} }
                restaurarScroll(estado.scroll);
            } finally {
                restaurando = false;
                ultimoGuardado = '';
            }
        }

        /* ── Abrir / activar / cerrar ───────────────────────────────── */
        function abrir(url, forzarRecarga) {
            const i = pestanas.findIndex(p => p.url === url);
            if (i >= 0) { activar(i, forzarRecarga); return; }
            if (pestanas.length >= MAX_PESTANAS) {
                const victima = pestanas.findIndex((p, idx) => p.url !== INICIO && idx !== activa);
                if (victima >= 0) cerrar(victima, true);
            }
            const info = infoMenu(url);
            pestanas.push({ url, titulo: info.titulo, icono: info.icono, $dom: null, scroll: 0, cargada: false });
            activar(pestanas.length - 1);
        }

        function activar(i, forzarRecarga) {
            if (i < 0 || i >= pestanas.length) return;
            if (activa === i && !forzarRecarga && pestanas[i].cargada) { marcarMenu(pestanas[i].url); return; }

            // Guardar y desprender la pestaña que se deja.
            if (activa >= 0 && activa < pestanas.length) {
                guardarEstado(false);
                const ant = pestanas[activa];
                ant.scroll = scrollActual();
                if (activa !== i) {
                    ant.$dom = $contenido.children().detach();
                    // Si se salió antes de que la pantalla terminara de cargar, no hay nada
                    // que devolver: se marca para que se cargue de nuevo al volver (antes
                    // se guardaba un DOM vacío y la pestaña volvía en blanco).
                    if (!ant.$dom.length) { ant.$dom = null; ant.cargada = false; }
                }
            }

            activa = i;
            ultimoGuardado = '';
            const p = pestanas[i];
            window._buscadorModulo = null;

            if (p.$dom && p.$dom.length && p.cargada && !forzarRecarga) {
                $contenido.empty().append(p.$dom);
                p.$dom = null;
                restaurarScroll(p.scroll);
                render(); marcarMenu(p.url); guardarPestanas();
                return;
            }

            $contenido.empty();
            p.$dom = null;
            render(); marcarMenu(p.url);
            cargarOriginal(p.url);
            p.cargada = true;
            esperarContenido().then(() => {
                if (pestanas[activa] !== p) return;
                const t = $contenido.find('h4,h5').first().text().trim();
                if (t && infoMenu(p.url).titulo === p.url) { p.titulo = t.substring(0, 34); render(); }
                reponerEstado(p);
            });
            guardarPestanas();
        }

        function cerrar(i, silencioso) {
            const p = pestanas[i];
            if (!p || p.url === INICIO) return;
            Espacio.limpiar(claveTab(p.url));      // se cerró a propósito: no hay nada que reponer
            pestanas.splice(i, 1);
            if (activa === i) {
                $contenido.empty();
                activa = -1;
                const destino = Math.min(i, pestanas.length - 1);
                if (destino >= 0) activar(destino); else abrir(INICIO);
            } else {
                if (activa > i) activa--;
                render();
            }
            if (!silencioso) guardarPestanas();
        }

        function cerrarOtras(i) {
            const conservar = pestanas[i];
            const inicio = pestanas.find(p => p.url === INICIO);
            pestanas.filter(p => p !== conservar && p !== inicio).forEach(p => Espacio.limpiar(claveTab(p.url)));
            pestanas.length = 0;
            if (inicio && inicio !== conservar) pestanas.push(inicio);
            pestanas.push(conservar);
            activa = pestanas.indexOf(conservar);
            render(); guardarPestanas();
        }

        function cerrarTodas() {
            pestanas.filter(p => p.url !== INICIO).forEach(p => Espacio.limpiar(claveTab(p.url)));
            const inicio = pestanas.find(p => p.url === INICIO);
            pestanas.length = 0;
            activa = -1;
            $contenido.empty();
            if (inicio) { inicio.cargada = false; inicio.$dom = null; pestanas.push(inicio); activar(0); }
            else abrir(INICIO);
            guardarPestanas();
        }

        /* ── Menú contextual ────────────────────────────────────────── */
        function menuContextual(e, i) {
            $('.sciaf-tabs-pop').remove();
            if (i < 0) return;
            const $m = $(`
                <div class="sciaf-tabs-pop">
                    <button data-a="recargar"><i class="ti ti-refresh"></i> Recargar esta pestaña</button>
                    <button data-a="cerrar"><i class="ti ti-x"></i> Cerrar</button>
                    <button data-a="otras"><i class="ti ti-layout-columns"></i> Cerrar las demás</button>
                    <button data-a="todas"><i class="ti ti-trash"></i> Cerrar todas</button>
                </div>`);
            $m.css({ top: (e.clientY + 6) + 'px', left: Math.min(e.clientX, window.innerWidth - 240) + 'px' });
            $m.on('click', 'button', function () {
                const a = $(this).data('a');
                $m.remove();
                if (a === 'recargar') activar(i, true);
                if (a === 'cerrar') cerrar(i);
                if (a === 'otras') cerrarOtras(i);
                if (a === 'todas') cerrarTodas();
            });
            $('body').append($m);
            setTimeout(() => $(document).one('click', () => $m.remove()), 0);
        }

        /* ── Utilidades ─────────────────────────────────────────────── */
        function esperarContenido() {
            return new Promise(resolve => {
                const t0 = Date.now();
                (function ver() {
                    if ($contenido.children().length || Date.now() - t0 > 4000) return setTimeout(resolve, 250);
                    setTimeout(ver, 80);
                })();
            });
        }
        function contenedorScroll() {
            const el = document.querySelector('.layout-page');
            return (el && el.scrollHeight > el.clientHeight) ? el : window;
        }
        function scrollActual() {
            const c = contenedorScroll();
            return c === window ? window.scrollY : c.scrollTop;
        }
        function restaurarScroll(v) {
            if (!v) return;
            const c = contenedorScroll();
            setTimeout(() => { if (c === window) window.scrollTo(0, v); else c.scrollTop = v; }, 80);
        }
        function marcarMenu(url) {
            $('#layout-menu .menu-item').removeClass('active');
            $(`#layout-menu .menu-item[data-url="${url}"]`).addClass('active');
        }
        function escapar(t) { return $('<div>').text(t == null ? '' : t).html(); }

        /* ── Lista de pestañas abiertas ─────────────────────────────── */
        let tGuardar;
        function guardarPestanas(alCerrar) {
            if (restaurando) return;
            const datos = {
                activa: activa,
                pestanas: pestanas.map(p => ({ url: p.url, titulo: p.titulo, icono: p.icono }))
            };
            if (alCerrar) { Espacio.guardar(CLAVE_PESTANAS, datos, true); return; }
            clearTimeout(tGuardar);
            tGuardar = setTimeout(() => Espacio.guardar(CLAVE_PESTANAS, datos), 600);
        }

        /**
         * El layout dispara la carga de Inicio antes de que exista el gestor de pestañas.
         * Si restauramos otra pantalla mientras esa carga sigue en vuelo, su respuesta
         * caería encima. Se espera a que termine (o 1,5 s) antes de tocar #contenido.
         */
        function esperarInicio() {
            return new Promise(resolve => {
                const t0 = Date.now();
                (function ver() {
                    if ($contenido.children().length || Date.now() - t0 > 1500) return resolve();
                    setTimeout(ver, 80);
                })();
            });
        }

        async function restaurar() {
            const d = await Espacio.leer(CLAVE_PESTANAS);
            await esperarInicio();
            // Sin pestañas guardadas: se queda el Inicio que ya cargó el layout.
            if (!d || !Array.isArray(d.pestanas) || !d.pestanas.length) { render(); return; }
            restaurando = true;
            pestanas.length = 0;
            d.pestanas.forEach(p => {
                if (!p.url) return;
                // Solo se reabre lo que el usuario sigue teniendo permitido (está en su menú).
                if (p.url !== INICIO && !$(`#layout-menu .menu-item[data-url="${p.url}"]`).length) return;
                pestanas.push({ url: p.url, titulo: p.titulo || infoMenu(p.url).titulo,
                                icono: p.icono || infoMenu(p.url).icono, $dom: null, scroll: 0, cargada: false });
            });
            if (!pestanas.some(p => p.url === INICIO)) {
                pestanas.unshift({ url: INICIO, ...infoMenu(INICIO), $dom: null, scroll: 0, cargada: false });
            }
            activa = -1;
            render();
            restaurando = false;
            activar(Math.min(Math.max(d.activa ?? 0, 0), pestanas.length - 1));
            if (pestanas.length > 1 && window.Swal) {
                setTimeout(() => Swal.fire({
                    toast: true, position: 'bottom-end', icon: 'info',
                    title: `Se recuperaron ${pestanas.length} pestañas de su última sesión`,
                    showConfirmButton: false, timer: 4000
                }), 900);
            }
        }

        /* ── Enganche con la navegación ─────────────────────────────── */
        window.cargarContenido = function (url) { if (url) abrir(url); };

        // El layout tiene su propio manejador que reemplaza #contenido directamente. Se
        // intercepta en captura para que la navegación pase SIEMPRE por las pestañas: sin
        // esto, cada clic del menú seguía borrando la pantalla anterior.
        const menu = document.getElementById('layout-menu');
        if (menu) {
            menu.addEventListener('click', function (e) {
                const item = e.target.closest('.menu-item[data-url]');
                if (!item || !menu.contains(item)) return;
                const url = item.getAttribute('data-url');
                if (!url) return;
                e.preventDefault();
                e.stopPropagation();
                if (e.stopImmediatePropagation) e.stopImmediatePropagation();
                abrir(url);
            }, true);
        }

        window.sciafPestanas = {
            recargarActiva: () => activar(activa, true),
            abrir: url => abrir(url),
            cerrarActiva: () => cerrar(activa),
            guardarAhora: () => guardarEstado(false),
            lista: () => pestanas.map(p => ({ url: p.url, titulo: p.titulo })),
            /** La pantalla activa declara qué módulo es, para su guardado a medida. */
            moduloActivo: clave => { if (activa >= 0) pestanas[activa].modulo = clave; }
        };

        pestanas.push({ url: INICIO, ...infoMenu(INICIO), $dom: null, scroll: 0, cargada: true });
        activa = 0;
        render();
        restaurar();
        console.info('[Pestañas] Activas: la navegación del menú pasa por el gestor de pestañas.');
    });

    /* ══════════ Estilos ══════════ */
    function estilos() {
        if (document.getElementById('sciaf-tabs-css')) return;
        const st = document.createElement('style');
        st.id = 'sciaf-tabs-css';
        st.textContent = `
            #sciaf-tabs {
                position: sticky; top: 0; z-index: 6;
                background: #f5f5f9; border-bottom: 1px solid #dfe3ec;
                box-shadow: 0 2px 6px rgba(34,41,47,.05);
                padding: 8px 1.25rem 0;
            }
            .sciaf-tabs-lista {
                display: flex; align-items: stretch; gap: 4px;
                overflow-x: auto; overflow-y: hidden; scrollbar-width: thin;
            }
            .sciaf-tabs-lista::-webkit-scrollbar { height: 4px; }
            .sciaf-tabs-lista::-webkit-scrollbar-thumb { background: #c9d0e0; border-radius: 4px; }
            .sciaf-tab {
                display: inline-flex; align-items: center; gap: 7px; padding: 7px 10px 7px 12px;
                border-radius: 9px 9px 0 0; background: rgba(255,255,255,.55); color: #5a6a8a;
                font-size: 13px; font-weight: 500; cursor: pointer; white-space: nowrap;
                border: 1px solid transparent; border-bottom: none; max-width: 230px;
                transition: background .15s, color .15s;
            }
            .sciaf-tab:hover { background: #fff; color: #1a2340; }
            .sciaf-tab.activa {
                background: #fff; color: #3b5bdb; border-color: #e4e8f0;
                box-shadow: 0 -2px 6px rgba(34,41,47,.06); font-weight: 600;
            }
            .sciaf-tab-txt { overflow: hidden; text-overflow: ellipsis; }
            .sciaf-tab-x {
                border: 0; background: transparent; color: #9aa3b8; font-size: 16px; line-height: 1;
                cursor: pointer; padding: 0 2px; border-radius: 4px;
            }
            .sciaf-tab-x:hover { background: rgba(240,68,56,.12); color: #f04438; }
            .sciaf-tabs-menu {
                border: 0; background: transparent; color: #9aa3b8; font-size: 17px; cursor: pointer;
                padding: 0 8px; border-radius: 6px;
            }
            .sciaf-tabs-menu:hover { background: rgba(0,0,0,.05); color: #1a2340; }
            .sciaf-tabs-pop {
                position: fixed; z-index: 3000; background: #fff; border: 1px solid #e4e8f0;
                border-radius: 10px; box-shadow: 0 8px 24px rgba(34,41,47,.16); padding: 6px;
                display: flex; flex-direction: column; min-width: 225px;
            }
            .sciaf-tabs-pop button {
                display: flex; align-items: center; gap: 9px; border: 0; background: transparent;
                padding: 8px 10px; border-radius: 7px; font-size: 13.5px; color: #5a6a8a;
                cursor: pointer; text-align: left;
            }
            .sciaf-tabs-pop button:hover { background: #f0f4ff; color: #3b5bdb; }
            @media (max-width: 720px) { .sciaf-tab { max-width: 150px; } }`;
        document.head.appendChild(st);
    }

    /**
     * La franja de pestañas va como primer elemento del área de contenido: justo debajo de
     * la barra superior (buscador, tema, conexiones, notificaciones, perfil) y encima de
     * donde se cargan las vistas. Queda fija al hacer scroll, a la altura de esa barra.
     */
    function barra() {
        let $b = $('#sciaf-tabs');
        if ($b.length) return $b.find('.sciaf-tabs-lista');
        $b = $('<div id="sciaf-tabs" role="tablist"><div class="sciaf-tabs-lista"></div></div>');
        const $wrapper = $('#contenido').closest('.content-wrapper');
        if ($wrapper.length) $wrapper.prepend($b); else $('#contenido').before($b);
        ajustarAltura($b);
        $(window).on('resize.sciafTabs', () => ajustarAltura($b));
        return $b.find('.sciaf-tabs-lista');
    }

    function ajustarAltura($b) {
        const nav = document.getElementById('layout-navbar');
        const alto = nav ? Math.round(nav.getBoundingClientRect().bottom) : 0;
        $b.css('top', (alto > 0 ? alto : 0) + 'px');
    }
})();
