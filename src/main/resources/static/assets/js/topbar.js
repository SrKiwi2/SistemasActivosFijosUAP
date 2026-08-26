/**
 * notificaciones.js
 * Maneja la campana, panel, historial y SSE de notificaciones.
 * Debe cargarse DESPUÉS de Bootstrap y jQuery.
 */
(function () {
    'use strict';

    // ── Config ────────────────────────────────────────────────────────────────
    const API = {
        noLeidas:   '/api/notificaciones/no-leidas',
        conteo:     '/api/notificaciones/conteo',
        historial:  '/api/notificaciones/historial',
        leer:       (id) => `/api/notificaciones/${id}/leer`,
        leerTodas:  '/api/notificaciones/leer-todas',
        entregadas: '/api/notificaciones/entregadas',
        sseUsuario: '/api/eventos/sse/usuario'
    };

    const PAGE_SIZE = 15;
    let historialPagina   = 0;
    let historialTotal    = 0;
    let historialTotalPag = 0;

    // ── Referencias DOM ───────────────────────────────────────────────────────
    const badge          = document.getElementById('badge-notif');
    const iconoCampana   = document.getElementById('iconoCampana');
    const listaEl        = document.getElementById('lista-notificaciones');
    const vacioEl        = document.getElementById('notif-vacio');
    const countTextoEl   = document.getElementById('notif-count-texto');
    const btnMarcarTodas = document.getElementById('btn-marcar-todas-leidas');
    const toastEl        = document.getElementById('toast-notificacion');
    const toastTitulo    = document.getElementById('toast-titulo');
    const toastMensaje   = document.getElementById('toast-mensaje');

    // Verificar que el DOM de notificaciones existe
    // (la campana solo aparece para roles autorizados)
    if (!badge || !listaEl) {
        console.debug('[Notif] Campana no presente en esta vista.');
        return;
    }

    // ════════════════════════════════════════════════════════════════
    //  BADGE
    // ════════════════════════════════════════════════════════════════
    function actualizarBadge(count) {
        if (count > 0) {
            badge.textContent   = count > 99 ? '99+' : count;
            badge.style.cssText = ''; // limpia display:none!important
            badge.style.display = 'inline-block';
            badge.classList.add('notif-pulse');
            if (countTextoEl) countTextoEl.textContent =
                count + ' notificación' + (count > 1 ? 'es' : '') + ' sin leer';
            if (btnMarcarTodas) btnMarcarTodas.style.display = '';
        } else {
            badge.style.cssText = 'display:none!important';
            badge.classList.remove('notif-pulse');
            detenerShake();
            if (countTextoEl) countTextoEl.textContent = 'Sin notificaciones nuevas';
            if (btnMarcarTodas) btnMarcarTodas.style.display = 'none';
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  ANIMACIÓN DE LA CAMPANA
    // ════════════════════════════════════════════════════════════════
    function dispararShake() {
        if (!iconoCampana) return;
        iconoCampana.classList.remove('campana-shake');
        void iconoCampana.offsetWidth;            // reinicia la animación
        iconoCampana.classList.add('campana-shake');
        setTimeout(detenerShake, 2000);
    }

    function detenerShake() {
        if (iconoCampana) iconoCampana.classList.remove('campana-shake');
    }

    // ════════════════════════════════════════════════════════════════
    //  ACUSE DE ENTREGA ("le llegó")
    // ════════════════════════════════════════════════════════════════
    function marcarEntregadas(ids) {
        if (!ids || !ids.length) return;
        fetch(API.entregadas, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(ids)
        }).catch(() => {});
    }

    // ════════════════════════════════════════════════════════════════
    //  CONFIRMAR LECTURA (acuse explícito)
    // ════════════════════════════════════════════════════════════════
    function confirmarLectura(id) {
        fetch(API.leer(id), {
            method: 'POST',
            headers: { 'X-Requested-With': 'XMLHttpRequest' }
        })
        .then(r => r.ok ? r.json() : null)
        .then(d => {
            if (d) actualizarBadge(d.noLeidasRestantes || 0);
            cargarNoLeidas();
        })
        .catch(() => {});
    }

    // ════════════════════════════════════════════════════════════════
    //  LISTA NO LEÍDAS (dropdown)
    // ════════════════════════════════════════════════════════════════
    function cargarNoLeidas() {
        fetch(API.noLeidas)
            .then(r => r.ok ? r.json() : null)
            .then(data => {
                if (!data) return;
                const notifs = data.notificaciones || [];
                renderListaNoLeidas(notifs);
                actualizarBadge(data.total || 0);
                // Acuse de entrega: el cliente ya las "recibió".
                marcarEntregadas(notifs.map(n => n.id));
            })
            .catch(() => {});
    }

    function renderListaNoLeidas(notifs) {
        Array.from(listaEl.querySelectorAll('.notif-item'))
             .forEach(el => el.remove());

        if (notifs.length === 0) {
            if (vacioEl) vacioEl.style.display = '';
            return;
        }
        if (vacioEl) vacioEl.style.display = 'none';

        notifs.slice(0, 8).forEach(n => {
            const tieneUrl = n.urlDestino && n.urlDestino !== '#';
            const li = document.createElement('li');
            li.className = 'list-group-item notif-item px-3 py-2 '
                + (n.leida ? '' : 'bg-light');
            li.innerHTML =
                '<div class="d-flex gap-2 align-items-start">'
                + '<div class="flex-shrink-0 mt-1">' + iconoPorTipo(n.tipo) + '</div>'
                + '<div class="flex-grow-1 overflow-hidden' + (tieneUrl ? ' notif-cuerpo" style="cursor:pointer"' : '"') + '>'
                    + '<div class="d-flex justify-content-between">'
                        + '<small class="fw-semibold text-truncate" style="max-width:200px">'
                        +   escHtml(n.titulo) + '</small>'
                        + '<span class="text-muted ms-1 flex-shrink-0" style="font-size:.65rem">'
                        +   (n.fechaCreacion || '') + '</span>'
                    + '</div>'
                    + '<small class="text-muted d-block text-truncate">'
                    +   escHtml(n.mensaje) + '</small>'
                    + '<div class="mt-1 d-flex gap-2">'
                        + '<button type="button" class="btn btn-xs btn-outline-success py-0 px-2 notif-confirmar"'
                        +   ' style="font-size:.7rem"><i class="ti ti-check ti-xs me-1"></i>Confirmar</button>'
                        + (tieneUrl
                            ? '<button type="button" class="btn btn-xs btn-outline-primary py-0 px-2 notif-ir"'
                              + ' style="font-size:.7rem"><i class="ti ti-arrow-right ti-xs me-1"></i>Ir</button>'
                            : '')
                    + '</div>'
                + '</div>'
                + '<div class="flex-shrink-0 ms-1">'
                  + '<span class="badge bg-primary rounded-circle p-1"'
                  + ' style="width:8px;height:8px;display:block"></span></div>'
                + '</div>';

            // Confirmar lectura (acuse explícito) — no navega.
            li.querySelector('.notif-confirmar')
                ?.addEventListener('click', e => { e.stopPropagation(); confirmarLectura(n.id); });

            // Ir al destino — NO confirma (sigue sin leer hasta el acuse).
            const irFn = () => {
                cerrarDropdown();
                setTimeout(() => { window.location.href = n.urlDestino; }, 150);
            };
            li.querySelector('.notif-ir')?.addEventListener('click', e => { e.stopPropagation(); irFn(); });
            li.querySelector('.notif-cuerpo')?.addEventListener('click', irFn);

            listaEl.insertBefore(li, vacioEl);
        });
    }

    function cerrarDropdown() {
        const btnCampana = document.getElementById('btnCampana');
        if (btnCampana) {
            try { bootstrap.Dropdown.getInstance(btnCampana)?.hide(); } catch (e) {}
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  MARCAR TODAS COMO LEÍDAS
    // ════════════════════════════════════════════════════════════════
    if (btnMarcarTodas) {
        btnMarcarTodas.addEventListener('click', () => {
            fetch(API.leerTodas, { method: 'POST' })
                .then(() => { actualizarBadge(0); cargarNoLeidas(); })
                .catch(() => {});
        });
    }

    document.getElementById('btn-historial-marcar-todas')
        ?.addEventListener('click', () => {
            fetch(API.leerTodas, { method: 'POST' })
                .then(() => { actualizarBadge(0); cargarHistorial(0); })
                .catch(() => {});
        });

    // ════════════════════════════════════════════════════════════════
    //  TOAST
    // ════════════════════════════════════════════════════════════════
    function mostrarToast(titulo, mensaje, importante) {
        if (!toastEl) return;
        if (toastTitulo)  toastTitulo.textContent  = titulo  || 'Notificación';
        if (toastMensaje) toastMensaje.textContent = mensaje || '';
        // Resalta en rojo los comunicados importantes/urgentes.
        toastEl.classList.toggle('text-bg-danger', !!importante);
        toastEl.classList.toggle('text-bg-primary', !importante);
        bootstrap.Toast.getOrCreateInstance(toastEl).show();

        // Sonido sutil
        try {
            const ctx  = new (window.AudioContext || window.webkitAudioContext)();
            const osc  = ctx.createOscillator();
            const gain = ctx.createGain();
            osc.connect(gain);
            gain.connect(ctx.destination);
            osc.frequency.value = 880;
            gain.gain.setValueAtTime(0.1, ctx.currentTime);
            gain.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + 0.3);
            osc.start(ctx.currentTime);
            osc.stop(ctx.currentTime + 0.3);
        } catch (e) {}
    }

    // ════════════════════════════════════════════════════════════════
    //  HISTORIAL
    // ════════════════════════════════════════════════════════════════
    function cargarHistorial(pagina) {
        const loader  = document.getElementById('historial-loader');
        const lista   = document.getElementById('historial-lista');
        const vacio   = document.getElementById('historial-vacio');
        const infoEl  = document.getElementById('historial-info-pagina');
        const btnAnt  = document.getElementById('btn-historial-anterior');
        const btnSig  = document.getElementById('btn-historial-siguiente');

        if (loader) loader.style.display = '';
        if (lista)  lista.style.display  = 'none';
        if (vacio)  vacio.style.display  = 'none';

        fetch(API.historial + '?page=' + pagina + '&size=' + PAGE_SIZE)
            .then(r => r.ok ? r.json() : null)
            .then(data => {
                if (loader) loader.style.display = 'none';
                if (!data) return;

                historialTotal    = data.total        || 0;
                historialTotalPag = data.totalPaginas || 0;
                historialPagina   = pagina;

                const tipoFiltro = document.getElementById('historial-filtro-tipo')?.value || '';
                const leidaFilt  = document.getElementById('historial-filtro-leida')?.value || '';

                const filtrados = (data.notificaciones || []).filter(n => {
                    if (tipoFiltro && n.tipo !== tipoFiltro) return false;
                    if (leidaFilt === 'true'  && !n.leida)  return false;
                    if (leidaFilt === 'false' &&  n.leida)  return false;
                    return true;
                });

                if (filtrados.length === 0) {
                    if (vacio) vacio.style.display = '';
                } else {
                    if (lista) {
                        lista.innerHTML    = '';
                        lista.style.display = '';
                        filtrados.forEach(n => lista.appendChild(crearItemHistorial(n)));
                    }
                }

                const desde = pagina * PAGE_SIZE + 1;
                const hasta = Math.min(desde + PAGE_SIZE - 1, historialTotal);
                if (infoEl) infoEl.textContent = historialTotal > 0
                    ? 'Mostrando ' + desde + '–' + hasta + ' de ' + historialTotal
                    : 'Sin resultados';
                if (btnAnt) btnAnt.disabled = (pagina === 0);
                if (btnSig) btnSig.disabled = (pagina >= historialTotalPag - 1);
            })
            .catch(() => { if (loader) loader.style.display = 'none'; });
    }

    function crearItemHistorial(n) {
        const li = document.createElement('li');
        li.className = 'list-group-item list-group-item-action px-4 py-3 '
            + (n.leida ? '' : 'bg-light border-start border-3 border-primary');
        li.innerHTML =
            '<div class="d-flex gap-3 align-items-start">'
            + '<div class="flex-shrink-0 mt-1">' + iconoPorTipo(n.tipo) + '</div>'
            + '<div class="flex-grow-1">'
                + '<div class="d-flex justify-content-between align-items-start">'
                    + '<span class="fw-semibold small">' + escHtml(n.titulo) + '</span>'
                    + '<div class="d-flex align-items-center gap-2 ms-2 flex-shrink-0">'
                        + (!n.leida
                            ? '<span class="badge bg-primary" style="font-size:.6rem">NUEVA</span>'
                            : '<span class="badge bg-secondary" style="font-size:.6rem">LEÍDA</span>')
                        + '<small class="text-muted" style="font-size:.65rem">'
                        + (n.fechaCreacion || '') + '</small>'
                    + '</div>'
                + '</div>'
                + '<small class="text-muted d-block mt-1">' + escHtml(n.mensaje) + '</small>'
                + (n.fechaLectura
                    ? '<small class="text-success d-block mt-1" style="font-size:.65rem">'
                      + '<i class="ti ti-circle-check ti-xs me-1"></i>Confirmada el '
                      + n.fechaLectura + '</small>'
                    : '')
                + '<div class="mt-2 d-flex gap-2">'
                + (!n.leida
                    ? '<button class="btn btn-xs btn-outline-success py-0 px-2 btn-historial-confirmar" '
                      + 'data-id="' + n.id + '" style="font-size:.7rem">'
                      + '<i class="ti ti-check ti-xs me-1"></i>Confirmar lectura</button>'
                    : '')
                + (n.urlDestino && n.urlDestino !== '#'
                    ? '<button class="btn btn-xs btn-outline-primary py-0 px-2 '
                      + 'btn-historial-ir" data-url="' + escHtml(n.urlDestino) + '" '
                      + 'style="font-size:.7rem">'
                      + '<i class="ti ti-arrow-right ti-xs me-1"></i>Ir</button>'
                    : '')
                + '</div>'
            + '</div>'
            + '</div>';

        li.querySelector('.btn-historial-confirmar')
            ?.addEventListener('click', e => {
                e.stopPropagation();
                const id = parseInt(e.currentTarget.dataset.id);
                fetch(API.leer(id), { method: 'POST' })
                    .finally(() => { actualizarConteoYRecargarHistorial(); });
            });

        li.querySelector('.btn-historial-ir')
            ?.addEventListener('click', e => {
                e.stopPropagation();
                // Ir NO confirma la lectura (acuse explícito).
                window.location.href = e.currentTarget.dataset.url;
            });
        return li;
    }

    function actualizarConteoYRecargarHistorial() {
        fetch(API.conteo).then(r => r.json())
            .then(d => actualizarBadge(d.noLeidas || 0)).catch(() => {});
        cargarHistorial(historialPagina);
    }

    // Eventos historial
    document.getElementById('btn-historial-anterior')
        ?.addEventListener('click', () => cargarHistorial(historialPagina - 1));
    document.getElementById('btn-historial-siguiente')
        ?.addEventListener('click', () => cargarHistorial(historialPagina + 1));
    ['historial-filtro-tipo','historial-filtro-leida'].forEach(id =>
        document.getElementById(id)?.addEventListener('change', () => cargarHistorial(0)));
    document.getElementById('modalHistorialNotificaciones')
        ?.addEventListener('show.bs.modal', () => cargarHistorial(0));

    // ════════════════════════════════════════════════════════════════
    //  SSE AUTENTICADO
    // ════════════════════════════════════════════════════════════════
    function conectarSseUsuario() {
        const sse = new EventSource(API.sseUsuario);

        sse.addEventListener('notificacion', e => {
            try {
                const data = JSON.parse(e.data);
                actualizarBadge(data.noLeidasTotal || 0);
                dispararShake();
                cargarNoLeidas();
                mostrarToast(data.titulo, data.mensaje, data.importante);
                // Acuse de entrega inmediato de la notificación recibida.
                if (data.idNotificacion) marcarEntregadas([data.idNotificacion]);
            } catch (err) {}
        });

        sse.addEventListener('nueva-transferencia', e => {
            try {
                const data = JSON.parse(e.data);
                if (data.recargarTabla && typeof window.cargarTabla === 'function') {
                    window.cargarTabla();
                }
                if (typeof window.verificarPendientes === 'function') {
                    window.verificarPendientes();
                }
            } catch (err) {}
        });

        // Estado de las conexiones (montajes VSIAF / BD): lo emite el backend
        // solo cuando algo cambia. Lo reemitimos como evento del documento
        // para que estado-conexiones.js lo consuma sin abrir otro EventSource.
        sse.addEventListener('estado-conexiones', e => {
            try {
                document.dispatchEvent(new CustomEvent('sciaf:estado-conexiones', {
                    detail: JSON.parse(e.data)
                }));
            } catch (err) {}
        });

        sse.addEventListener('connected', () =>
            console.debug('[SSE usuario] ✅ Conectado'));

        sse.onerror = () => {
            sse.close();
            setTimeout(conectarSseUsuario, 5000);
        };
    }

    // ════════════════════════════════════════════════════════════════
    //  HELPERS
    // ════════════════════════════════════════════════════════════════
    function iconoPorTipo(tipo) {
        const m = {
            'TRANSFERENCIA_NUEVA':    '<span class="badge bg-label-warning p-1"><i class="ti ti-arrows-exchange ti-xs"></i></span>',
            'TRANSFERENCIA_APROBADA': '<span class="badge bg-label-success p-1"><i class="ti ti-circle-check ti-xs"></i></span>',
            'TRANSFERENCIA_ERROR':    '<span class="badge bg-label-danger p-1"><i class="ti ti-alert-triangle ti-xs"></i></span>',
            'SYNC_COMPLETADO':        '<span class="badge bg-label-info p-1"><i class="ti ti-refresh ti-xs"></i></span>',
            'SISTEMA':                '<span class="badge bg-label-secondary p-1"><i class="ti ti-settings ti-xs"></i></span>',
            'AVISO':                  '<span class="badge bg-label-info p-1"><i class="ti ti-info-circle ti-xs"></i></span>',
            'TRABAJO':                '<span class="badge bg-label-primary p-1"><i class="ti ti-clipboard-text ti-xs"></i></span>',
            'URGENTE':                '<span class="badge bg-label-danger p-1"><i class="ti ti-alert-octagon ti-xs"></i></span>',
            'GENERAL':                '<span class="badge bg-label-secondary p-1"><i class="ti ti-bell ti-xs"></i></span>',
        };
        return m[tipo] || m['SISTEMA'];
    }

    function escHtml(str) {
        if (!str) return '';
        return str.replace(/&/g,'&amp;').replace(/</g,'&lt;')
                  .replace(/>/g,'&gt;').replace(/"/g,'&quot;');
    }

    // ════════════════════════════════════════════════════════════════
    //  INICIALIZACIÓN
    // ════════════════════════════════════════════════════════════════
    // Conteo inicial al cargar la página
    fetch(API.conteo)
        .then(r => r.json())
        .then(data => actualizarBadge(data.noLeidas || 0))
        .catch(() => {});

    // Cargar lista al abrir dropdown y detener la sacudida (ya las "vio").
    document.getElementById('btnCampana')
        ?.addEventListener('show.bs.dropdown', () => {
            detenerShake();
            cargarNoLeidas();
        });

    // Conectar SSE autenticado
    conectarSseUsuario();

    // Exponer para pruebas desde consola
    window._notif = {
        cargarNoLeidas, actualizarBadge, mostrarToast,
        cargarHistorial, confirmarLectura, dispararShake
    };

})();
