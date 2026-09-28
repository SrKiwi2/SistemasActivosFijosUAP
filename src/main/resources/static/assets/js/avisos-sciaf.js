/**
 * avisos-sciaf.js
 * Avisos emergentes en vivo (arriba a la derecha) para:
 *   - actividad     → a ADMINISTRADOR / SUPER USUARIO: "fulano registró / modificó / eliminó…"
 *   - autorizacion  → al revisor: solicitud nueva; al solicitante: su solicitud se resolvió.
 * Los eventos llegan por el SSE por usuario (topbar.js los reemite como
 * CustomEvent 'sciaf:actividad' / 'sciaf:autorizacion').
 *
 * Además expone window.sciafEscuchar(evento, clave, fn) para que las pantallas
 * (Monitoreo, Autorizaciones, Oficinas, Responsables) se refresquen solas. La clave
 * reemplaza al handler anterior de la misma pantalla: navegar por el menú no los duplica.
 */
(function () {
    'use strict';

    const MAX_VISIBLES = 4;
    const handlers = { actividad: {}, autorizacion: {} };

    window.sciafEscuchar = function (evento, clave, fn) {
        if (!handlers[evento]) handlers[evento] = {};
        handlers[evento][clave] = fn;
    };

    const ESTILO = {
        REGISTRO:     { color: '#28c76f', icono: 'ti-circle-plus',   verbo: 'Registro' },
        MODIFICACION: { color: '#00a3ff', icono: 'ti-pencil',        verbo: 'Modificación' },
        ELIMINACION:  { color: '#ea5455', icono: 'ti-trash',         verbo: 'Eliminación' },
        APROBACION:   { color: '#28c76f', icono: 'ti-circle-check',  verbo: 'Aprobación' },
        RECHAZO:      { color: '#ea5455', icono: 'ti-circle-x',      verbo: 'Rechazo' },
        SOLICITUD:    { color: '#ff9f43', icono: 'ti-shield-question', verbo: 'Solicitud' },
        MOVIMIENTO:   { color: '#7367f0', icono: 'ti-arrows-exchange', verbo: 'Movimiento' },
        BLOQUEO:      { color: '#ff9f43', icono: 'ti-lock',          verbo: 'Bloqueo' },
        DESBLOQUEO:   { color: '#28c76f', icono: 'ti-lock-open',     verbo: 'Desbloqueo' },
        REENVIO:      { color: '#00cfe8', icono: 'ti-cloud-upload',  verbo: 'Reenvío al VSIAF' }
    };
    const MODULOS = {
        OFICINA: 'Oficinas', RESPONSABLE: 'Responsables', ACTIVO: 'Activos', ASIGNACION: 'Asignación',
        TRANSFERENCIA: 'Transferencia', BLOQUEO: 'Bloqueo de activos', AUTORIZACION: 'Autorizaciones'
    };

    function esc(v) {
        return String(v == null ? '' : v).replace(/[&<>"']/g, c =>
            ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
    }

    function iniciales(u) {
        const s = String(u || '?').trim();
        return s.substring(0, 2).toUpperCase();
    }

    function contenedor() {
        let c = document.getElementById('sciaf-avisos');
        if (c) return c;
        const st = document.createElement('style');
        st.textContent = `
            #sciaf-avisos { position: fixed; top: 16px; right: 16px; z-index: 2100; width: 370px;
                max-width: calc(100vw - 32px); display: flex; flex-direction: column; gap: 10px; pointer-events: none; }
            .sciaf-aviso { pointer-events: auto; position: relative; overflow: hidden; display: flex; gap: 12px;
                background: #fff; border-radius: 14px; padding: 12px 14px 14px 12px;
                box-shadow: 0 10px 30px rgba(34, 41, 47, .18), 0 2px 6px rgba(34, 41, 47, .08);
                border-left: 5px solid var(--c); transform: translateX(120%); opacity: 0;
                transition: transform .45s cubic-bezier(.2, 1.1, .3, 1), opacity .35s ease; }
            .sciaf-aviso.entra { transform: translateX(0); opacity: 1; }
            .sciaf-aviso.sale { transform: translateX(120%); opacity: 0; }
            .sciaf-aviso .av-ico { flex: 0 0 40px; height: 40px; border-radius: 50%; display: flex;
                align-items: center; justify-content: center; font-weight: 700; font-size: .85rem;
                color: #fff; background: var(--c); position: relative; }
            .sciaf-aviso .av-ico i { position: absolute; right: -4px; bottom: -4px; background: #fff; color: var(--c);
                border-radius: 50%; font-size: .8rem; padding: 2px; box-shadow: 0 1px 3px rgba(0,0,0,.15); }
            .sciaf-aviso .av-cuerpo { flex: 1; min-width: 0; }
            .sciaf-aviso .av-titulo { font-size: .8rem; font-weight: 700; color: #4b4b4b; display: flex; gap: 6px; align-items: center; }
            .sciaf-aviso .av-tag { font-size: .65rem; font-weight: 700; text-transform: uppercase; letter-spacing: .4px;
                color: var(--c); background: color-mix(in srgb, var(--c) 12%, transparent); border-radius: 6px; padding: 1px 6px; }
            .sciaf-aviso .av-texto { font-size: .8rem; color: #6e6b7b; margin-top: 3px; line-height: 1.35;
                display: -webkit-box; -webkit-line-clamp: 3; -webkit-box-orient: vertical; overflow: hidden; }
            .sciaf-aviso .av-pie { display: flex; align-items: center; gap: 8px; margin-top: 6px; flex-wrap: wrap; }
            .sciaf-aviso .av-ref { font-family: monospace; font-size: .72rem; background: #f3f2f7; color: #5e5873;
                border-radius: 6px; padding: 2px 7px; cursor: pointer; max-width: 210px; overflow: hidden;
                text-overflow: ellipsis; white-space: nowrap; border: 0; }
            .sciaf-aviso .av-ref:hover { background: #e8e6f0; }
            .sciaf-aviso .av-hora { font-size: .68rem; color: #b9b9c3; margin-left: auto; }
            .sciaf-aviso .av-btn { font-size: .72rem; font-weight: 600; border: 0; border-radius: 7px; padding: 3px 10px;
                background: var(--c); color: #fff; cursor: pointer; }
            .sciaf-aviso .av-cerrar { position: absolute; top: 6px; right: 8px; border: 0; background: transparent;
                color: #b9b9c3; font-size: 1rem; line-height: 1; cursor: pointer; }
            .sciaf-aviso .av-cerrar:hover { color: #5e5873; }
            .sciaf-aviso .av-barra { position: absolute; left: 0; bottom: 0; height: 3px; background: var(--c);
                opacity: .55; width: 100%; transform-origin: left; }
            @media (prefers-color-scheme: dark) {
                .sciaf-aviso { background: #2f3349; }
                .sciaf-aviso .av-titulo { color: #d0d2d6; }
                .sciaf-aviso .av-texto { color: #b4b7bd; }
                .sciaf-aviso .av-ref { background: #3b4056; color: #d0d2d6; }
            }`;
        document.head.appendChild(st);
        c = document.createElement('div');
        c.id = 'sciaf-avisos';
        c.setAttribute('aria-live', 'polite');
        document.body.appendChild(c);
        return c;
    }

    /** Navega a un módulo del menú (si el usuario lo tiene). */
    function irAModulo(url) {
        const item = document.querySelector(`#layout-menu .menu-item[data-url="${url}"]`);
        if (item) item.click();
    }

    /**
     * @param o { color, icono, usuario, etiqueta, titulo, texto, referencia, duracion, boton:{texto, accion} }
     */
    function mostrar(o) {
        const c = contenedor();
        while (c.children.length >= MAX_VISIBLES) c.firstElementChild.remove();

        const el = document.createElement('div');
        el.className = 'sciaf-aviso';
        el.style.setProperty('--c', o.color);
        el.setAttribute('role', 'status');
        const hora = new Date().toLocaleTimeString('es-BO', { hour: '2-digit', minute: '2-digit' });
        el.innerHTML = `
            <div class="av-ico">${esc(iniciales(o.usuario))}<i class="ti ${esc(o.icono)}"></i></div>
            <div class="av-cuerpo">
                <div class="av-titulo"><span>${esc(o.titulo)}</span><span class="av-tag">${esc(o.etiqueta)}</span></div>
                <div class="av-texto">${esc(o.texto)}</div>
                <div class="av-pie">
                    ${o.referencia ? `<button type="button" class="av-ref" title="Copiar código para buscarlo en su módulo">
                        <i class="ti ti-copy"></i> ${esc(o.referencia)}</button>` : ''}
                    ${o.boton ? `<button type="button" class="av-btn">${esc(o.boton.texto)}</button>` : ''}
                    <span class="av-hora">${hora}</span>
                </div>
            </div>
            <button type="button" class="av-cerrar" aria-label="Cerrar">&times;</button>
            <div class="av-barra"></div>`;
        c.appendChild(el);

        const cerrar = () => {
            el.classList.add('sale');
            setTimeout(() => el.remove(), 400);
        };
        el.querySelector('.av-cerrar').addEventListener('click', cerrar);
        el.querySelector('.av-ref')?.addEventListener('click', () => {
            navigator.clipboard?.writeText(o.referencia).then(() => {
                const b = el.querySelector('.av-ref');
                b.innerHTML = '<i class="ti ti-check"></i> Copiado';
            }).catch(() => {});
        });
        el.querySelector('.av-btn')?.addEventListener('click', () => { o.boton.accion(); cerrar(); });

        // Barra de tiempo; se pausa con el mouse encima.
        const duracion = o.duracion || 7000;
        const barra = el.querySelector('.av-barra');
        const anim = barra.animate([{ transform: 'scaleX(1)' }, { transform: 'scaleX(0)' }],
            { duration: duracion, easing: 'linear', fill: 'forwards' });
        anim.onfinish = cerrar;
        el.addEventListener('mouseenter', () => anim.pause());
        el.addEventListener('mouseleave', () => anim.play());

        requestAnimationFrame(() => requestAnimationFrame(() => el.classList.add('entra')));
    }

    function avisarActividad(d) {
        const e = ESTILO[d.accion] || { color: '#82868b', icono: 'ti-bell', verbo: d.accion };
        mostrar({
            color: e.color, icono: e.icono, usuario: d.usuario,
            titulo: d.usuario || 'Sistema',
            etiqueta: (MODULOS[d.modulo] || d.modulo) + ' · ' + e.verbo,
            texto: d.descripcion,
            referencia: d.referencia,
            duracion: 7000
        });
    }

    function avisarAutorizacion(d) {
        if (d.evento === 'NUEVA') {
            mostrar({
                color: '#ff9f43', icono: 'ti-shield-question', usuario: d.solicitante,
                titulo: (d.solicitante || '') + ' pide autorización',
                etiqueta: 'Autorización',
                texto: 'Para ' + (d.tipoTexto || '') + ' ' + (d.referencia || '') + '. Motivo: ' + (d.motivo || ''),
                referencia: d.referencia,
                duracion: 20000,
                boton: { texto: 'Revisar', accion: () => irAModulo('/administracion/autorizaciones/vista') }
            });
        } else if (d.evento === 'RESUELTA') {
            const ok = d.estado === 'APROBADA';
            mostrar({
                color: ok ? '#28c76f' : '#ea5455', icono: ok ? 'ti-circle-check' : 'ti-circle-x',
                usuario: d.revisor,
                titulo: 'Solicitud ' + (d.estado || '').toLowerCase(),
                etiqueta: 'Autorización',
                texto: 'Su pedido para ' + (d.tipoTexto || '') + ' ' + (d.referencia || '') + ' fue '
                    + (d.estado || '').toLowerCase() + ' por ' + (d.revisor || '') + '. ' + (d.respuesta || ''),
                referencia: d.referencia,
                duracion: 15000
            });
        }
    }

    function despachar(evento, detalle) {
        Object.values(handlers[evento] || {}).forEach(fn => {
            try { fn(detalle); } catch (e) { console.warn('[avisos] handler', evento, e); }
        });
    }

    document.addEventListener('sciaf:actividad', e => {
        avisarActividad(e.detail || {});
        despachar('actividad', e.detail || {});
    });
    document.addEventListener('sciaf:autorizacion', e => {
        avisarAutorizacion(e.detail || {});
        despachar('autorizacion', e.detail || {});
    });

    // Para probar desde la consola: _avisos.mostrar({...})
    window._avisos = { mostrar, avisarActividad, avisarAutorizacion };
})();
