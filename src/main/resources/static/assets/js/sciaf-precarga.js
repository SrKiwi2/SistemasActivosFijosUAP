/**
 * sciaf-precarga.js
 * Preloader de las pantallas del SCIAF (estilos en css/sciaf-precarga.css).
 *
 * Es el loader de inicio-admin.html (logo girando, barra, mensajes al azar) llevado a un
 * componente que se dibuja DENTRO de lo que se está cargando —la pestaña, una tarjeta, un
 * modal— y que, si la carga se demora, dice POR QUÉ:
 *
 *   · mide la ida y vuelta a /api/estado/ping (no toca base ni disco):
 *       - responde rápido → la red está bien, es el servidor armando muchos datos;
 *       - responde lento  → conexión lenta;
 *       - no responde     → servidor caído o reiniciándose;
 *   · el navegador dice que no hay red → sin conexión (y reintenta solo al volver).
 *
 * Nunca da por fallida una carga que sigue en curso: solo muestra el error cuando el pedido
 * realmente falló (y entonces con el motivo y el botón Reintentar).
 *
 * Uso:
 *   const carga = SciafPrecarga.montar(contenedor, {
 *       titulo: 'Auxiliar',          // "Abriendo «Auxiliar»" (opcional)
 *       compacta: true,              // tarjetas y modales (sin tarjeta propia, menos alto)
 *       alReintentar: fn,            // muestra "Reintentar" si se demora o falla
 *       alInicio: fn                 // muestra "Ir al inicio" (pestañas)
 *   });
 *   ... carga.cerrar()                         // llegó el contenido (o $(c).html(...) encima)
 *   ... carga.error('No se pudo…', motivo)     // falló de verdad
 *
 *   SciafPrecarga.durante(contenedor, promesa, opciones)  // monta, espera y avisa el error
 *   SciafPrecarga.motivo(status)                          // texto para un código HTTP
 *   SciafPrecarga.diagnosticar()                          // Promise<{tipo, ms, texto}>
 */
(function () {
    'use strict';

    const LOGO = '/assets/img/logo/activofijos.png';
    const PING = '/api/estado/ping';
    const PING_TOPE_MS = 5000;      // sin respuesta en este tiempo: el servidor no contesta
    const RED_LENTA_MS = 900;       // ida y vuelta por encima de esto: conexión lenta
    const VIDA_MAX_MS = 10 * 60 * 1000;

    const MENSAJES = [
        'Cargando datos…',
        'Un momento…',
        'Procesando…',
        'SCIAF 2.0',
        'Kiwi estuvo aquí',
        'Preparando vista…',
        'Optimizando…',
        'Ordenando la información…',
        'Consultando la base de datos…',
        'Acomodando las columnas…'
    ];

    const ICONOS = {
        'procesando': 'ti-server-bolt',
        'red-lenta': 'ti-antenna-bars-2',
        'sin-red': 'ti-wifi-off',
        'sin-respuesta': 'ti-server-off',
        'servidor': 'ti-alert-triangle',
        'demora': 'ti-hourglass-high'
    };

    function escapar(t) {
        return String(t == null ? '' : t)
            .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
    }

    function barajar(lista) {
        const a = lista.slice();
        for (let i = a.length - 1; i > 0; i--) {
            const j = Math.floor(Math.random() * (i + 1));
            const t = a[i]; a[i] = a[j]; a[j] = t;
        }
        return a;
    }

    function elemento(c) {
        if (!c) return null;
        if (c.jquery) return c[0] || null;
        if (typeof c === 'string') return document.querySelector(c);
        return c;
    }

    /* ══ Diagnóstico ═══════════════════════════════════════════════════════ */

    // Varias cargas a la vez (la pestaña y su tabla) comparten la misma medición.
    let ultimo = null;          // { t, resultado }
    let enCurso = null;

    function diagnosticar() {
        if (ultimo && Date.now() - ultimo.t < 3000) return Promise.resolve(ultimo.resultado);
        if (enCurso) return enCurso;

        enCurso = (async function () {
            if (navigator.onLine === false) {
                return { tipo: 'sin-red', texto: '<b>Sin conexión a internet.</b> La pantalla se vuelve a pedir sola cuando vuelva la conexión.' };
            }
            const conexion = navigator.connection || {};
            const tope = new AbortController();
            const reloj = setTimeout(function () { tope.abort(); }, PING_TOPE_MS);
            const t0 = performance.now();
            try {
                const r = await fetch(PING + '?_=' + Date.now(), {
                    cache: 'no-store', signal: tope.signal,
                    headers: { 'X-Requested-With': 'XMLHttpRequest' }
                });
                const ms = Math.round(performance.now() - t0);
                if (!r.ok) {
                    return { tipo: 'servidor', ms: ms, texto: '<b>El servidor respondió con un error</b> (código ' + r.status + '). Puede estar reiniciándose.' };
                }
                const lentaSegunNavegador = ['slow-2g', '2g'].indexOf(conexion.effectiveType) >= 0;
                if (ms > RED_LENTA_MS || lentaSegunNavegador) {
                    return { tipo: 'red-lenta', ms: ms, texto: '<b>Conexión lenta</b> (' + formatoMs(ms) + ' de ida y vuelta). La pantalla va a llegar, pero tarda más de lo normal.' };
                }
                return { tipo: 'procesando', ms: ms, texto: '<b>La red responde bien</b> (' + formatoMs(ms) + '). El servidor está preparando los datos: las pantallas con muchos registros tardan un poco más.' };
            } catch (e) {
                if (tope.signal.aborted) {
                    return { tipo: 'sin-respuesta', texto: '<b>El servidor no responde</b> (más de ' + (PING_TOPE_MS / 1000) + ' s sin contestar). Puede estar reiniciándose o muy ocupado.' };
                }
                return { tipo: 'sin-red', texto: '<b>No se pudo contactar al servidor.</b> Revise la conexión a internet o la VPN.' };
            } finally {
                clearTimeout(reloj);
            }
        })().then(function (resultado) {
            ultimo = { t: Date.now(), resultado: resultado };
            enCurso = null;
            return resultado;
        });
        return enCurso;
    }

    function formatoMs(ms) {
        return ms >= 1000 ? (ms / 1000).toFixed(1).replace('.', ',') + ' s' : ms + ' ms';
    }

    /** Texto para un código HTTP de una carga que falló. */
    function motivo(status) {
        if (status === 0) return 'No llegó respuesta del servidor: la conexión se cortó o el servidor se detuvo.';
        if (status === 401) return 'La sesión se cerró. Vuelva a ingresar para continuar.';
        if (status === 403) return 'Su usuario no tiene permiso para abrir esta pantalla.';
        if (status === 404) return 'La pantalla no existe en el servidor (404).';
        if (status === 502 || status === 503 || status === 504) return 'El servidor no está disponible en este momento (' + status + '). Puede estar reiniciándose.';
        if (status >= 500) return 'El servidor tuvo un error al armar la pantalla (' + status + '). Quedó registrado en el log.';
        return 'La carga no se completó' + (status ? ' (' + status + ')' : '') + '.';
    }

    /* ══ Componente ════════════════════════════════════════════════════════ */

    function montar(contenedor, opciones) {
        const c = elemento(contenedor);
        if (!c) return controlVacio();
        cerrarEn(c);

        const o = Object.assign({
            titulo: '',
            texto: null,               // primer mensaje (si no, uno al azar)
            compacta: false,
            mensajes: MENSAJES,
            demora: 150,               // no parpadea en las cargas instantáneas
            rotar: 3200,
            diagnosticarTras: 2500,
            rediagnosticar: 8000,
            reintentarTras: 15000,
            alReintentar: null,
            alInicio: null
        }, opciones || {});

        const el = document.createElement('div');
        el.className = 'sp-precarga' + (o.compacta ? ' sp-compacta' : '');
        el.setAttribute('role', 'status');
        el.setAttribute('aria-live', 'polite');
        el.setAttribute('aria-busy', 'true');
        el.innerHTML =
            '<div class="sp-tarjeta">' +
                '<img class="sp-logo" src="' + LOGO + '" alt="" onerror="this.style.display=\'none\'">' +
                '<i class="ti ti-alert-triangle sp-error-icono" aria-hidden="true"></i>' +
                (o.titulo ? '<p class="sp-titulo">Abriendo «' + escapar(o.titulo) + '»</p>' : '<p class="sp-titulo d-none"></p>') +
                '<div class="sp-barra"></div>' +
                '<div class="sp-mensaje"></div>' +
                '<div class="sp-diagnostico"><i class="ti"></i><div class="sp-diagnostico-texto"></div></div>' +
                '<div class="sp-tiempo"></div>' +
                '<div class="sp-acciones">' +
                    '<button type="button" class="sm-btn sm-btn-sm" data-sp="reintentar"><i class="ti ti-refresh"></i> Reintentar</button>' +
                    '<button type="button" class="sm-btn sm-btn-sm sm-btn-neutro" data-sp="inicio"><i class="ti ti-home"></i> Ir al inicio</button>' +
                '</div>' +
            '</div>';
        c.innerHTML = '';
        c.appendChild(el);

        const $q = function (s) { return el.querySelector(s); };
        const $msg = $q('.sp-mensaje');
        const $diag = $q('.sp-diagnostico');
        const $tiempo = $q('.sp-tiempo');
        const $acciones = $q('.sp-acciones');
        const $btnReintentar = $q('[data-sp="reintentar"]');
        const $btnInicio = $q('[data-sp="inicio"]');

        let reintentar = o.alReintentar;
        let irInicio = o.alInicio;
        function prepararBotones() {
            $btnReintentar.style.display = typeof reintentar === 'function' ? '' : 'none';
            $btnInicio.style.display = typeof irInicio === 'function' ? '' : 'none';
        }
        prepararBotones();
        $btnReintentar.addEventListener('click', function () { if (typeof reintentar === 'function') reintentar(); });
        $btnInicio.addEventListener('click', function () { if (typeof irInicio === 'function') irInicio(); });

        const t0 = Date.now();
        const mensajes = barajar(o.mensajes && o.mensajes.length ? o.mensajes : MENSAJES);
        let idx = 0, ultimoGiro = t0, ultimoDiag = 0, diagnosticando = false;
        let activa = true, conError = false;
        let tCambio = null;

        function ponerMensaje(texto, sinAnimar) {
            if (sinAnimar) { $msg.textContent = texto; return; }
            $msg.classList.add('sp-cambiando');
            clearTimeout(tCambio);
            tCambio = setTimeout(function () {
                $msg.textContent = texto;
                $msg.classList.remove('sp-cambiando');
            }, 280);
        }
        ponerMensaje(o.texto || mensajes[0], true);

        function mostrarDiagnostico(d) {
            $diag.dataset.tipo = d.tipo;
            $diag.querySelector('i').className = 'ti ' + (ICONOS[d.tipo] || 'ti-info-circle');
            $diag.querySelector('.sp-diagnostico-texto').innerHTML = d.texto;
            $diag.classList.add('sp-ver');
        }

        function revisar() {
            if (diagnosticando || !activa) return;
            diagnosticando = true;
            ultimoDiag = Date.now();
            diagnosticar().then(function (d) {
                diagnosticando = false;
                if (!activa || conError) return;
                // Red bien pero ya va largo: que se entienda que es el volumen de datos.
                if (d.tipo === 'procesando' && Date.now() - t0 > o.reintentarTras) {
                    d = { tipo: 'demora', ms: d.ms, texto: '<b>Está tardando más de lo normal.</b> La red responde (' + formatoMs(d.ms) + '): el servidor sigue armando la pantalla. Puede esperar o reintentar.' };
                }
                mostrarDiagnostico(d);
            });
        }

        const visible = setTimeout(function () { el.classList.add('sp-visible'); }, Math.max(0, o.demora));

        const reloj = setInterval(function () {
            if (!activa) return;
            const ahora = Date.now();
            if (ahora - t0 > VIDA_MAX_MS) { cerrar(); return; }
            if (!el.isConnected) return;          // pestaña desprendida: se retoma al volver

            const seg = Math.floor((ahora - t0) / 1000);
            if (seg >= 3) $tiempo.textContent = seg + ' s';

            if (ahora - ultimoGiro >= o.rotar) {
                ultimoGiro = ahora;
                idx = (idx + 1) % mensajes.length;
                ponerMensaje(mensajes[idx]);
            }
            if (ahora - t0 >= o.diagnosticarTras && (!ultimoDiag || ahora - ultimoDiag >= o.rediagnosticar)) revisar();
            if (ahora - t0 >= o.reintentarTras) $acciones.classList.add('sp-ver');
        }, 500);

        function alCortarse() { revisar(); }
        function alVolver() {
            ultimo = null;   // la medición vieja ya no vale
            if (activa && typeof reintentar === 'function') reintentar();
            else revisar();
        }
        window.addEventListener('offline', alCortarse);
        window.addEventListener('online', alVolver);

        function detener() {
            clearInterval(reloj);
            clearTimeout(visible);
            clearTimeout(tCambio);
            window.removeEventListener('offline', alCortarse);
            window.removeEventListener('online', alVolver);
        }

        function cerrar() {
            if (!activa) return;
            activa = false;
            detener();
            el.setAttribute('aria-busy', 'false');
            if (el.parentNode && !conError) el.parentNode.removeChild(el);
        }

        function error(titulo, detalle, extra) {
            if (!el.isConnected && !el.parentNode) return control;
            conError = true;
            activa = false;
            detener();
            extra = extra || {};
            if ('alReintentar' in extra) reintentar = extra.alReintentar;
            if ('alInicio' in extra) irInicio = extra.alInicio;
            prepararBotones();
            el.classList.add('sp-error', 'sp-visible');
            el.setAttribute('aria-busy', 'false');
            const $t = $q('.sp-titulo');
            $t.classList.remove('d-none');
            $t.textContent = titulo || 'No se pudo cargar';
            $msg.classList.remove('sp-cambiando');
            $msg.textContent = detalle || '';
            $diag.classList.remove('sp-ver');
            $acciones.classList.add('sp-ver');
            // Sin red: además del motivo, el diagnóstico lo confirma.
            diagnosticar().then(function (d) {
                if (d.tipo !== 'procesando' && el.isConnected) mostrarDiagnostico(d);
            });
            return control;
        }

        const control = {
            elemento: el,
            cerrar: cerrar,
            error: error,
            activa: function () { return activa; }
        };
        el.__sciafPrecarga = control;
        return control;
    }

    function controlVacio() {
        const nada = function () { return control; };
        const control = { elemento: null, cerrar: nada, error: nada, activa: function () { return false; } };
        return control;
    }

    /** Detiene (y quita) los preloaders que haya dentro de un contenedor. */
    function cerrarEn(contenedor) {
        const c = elemento(contenedor);
        if (!c) return;
        c.querySelectorAll('.sp-precarga').forEach(function (p) {
            if (p.__sciafPrecarga) p.__sciafPrecarga.cerrar();
            if (p.parentNode) p.parentNode.removeChild(p);
        });
    }

    /**
     * Monta el preloader, espera la promesa y, si falla, deja el error con su motivo.
     * La promesa debe poner el contenido ella misma (por ejemplo SciafModulo.cargar).
     * Devuelve la misma promesa (rechazada si falló), para encadenar.
     */
    function durante(contenedor, promesa, opciones) {
        const carga = montar(contenedor, Object.assign({ compacta: true }, opciones));
        return Promise.resolve(promesa).then(function (v) {
            carga.cerrar();
            return v;
        }, function (e) {
            if (e && e.sesionPerdida) {
                carga.error('La sesión se cerró', motivo(401));
            } else {
                carga.error((opciones && opciones.tituloError) || 'No se pudo cargar',
                    (e && e.status !== undefined) ? motivo(e.status) : ((e && e.message) || motivo(0)));
            }
            throw e;
        });
    }

    window.SciafPrecarga = { montar: montar, durante: durante, cerrarEn: cerrarEn, motivo: motivo, diagnosticar: diagnosticar };
})();
