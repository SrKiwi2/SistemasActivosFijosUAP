/**
 * sciaf-inactividad.js — Aviso antes de cerrar la sesión por inactividad.
 *
 * La sesión se cierra tras un tiempo sin que la persona use el sistema (30 min por
 * defecto, ver SesionInactividadService). Solo cuenta como uso: abrir pantallas y tocar el
 * teclado o el mouse. Los refrescos automáticos de las pantallas NO mantienen la sesión.
 *
 *  - Esta pestaña le informa al servidor hace cuánto no se usa (POST /api/sesion/estado).
 *    El servidor junta lo de todas las pestañas: si se está trabajando en otra, acá no
 *    aparece ningún aviso.
 *  - Un rato antes (2 min por defecto) aparece "¿Sigue ahí?" con la cuenta regresiva y
 *    el botón "Seguir conectado". Mover el mouse sobre el aviso no alcanza: hay que pulsar.
 *  - Si se cumple el tiempo, la pantalla se cubre con "Su sesión se cerró por
 *    inactividad" y no queda nada a medio usar (antes se veía el sistema "adentro", pero
 *    ningún botón funcionaba).
 *  - Volver con "Atrás" a una pantalla guardada por el navegador la recarga: así nunca se
 *    muestra una sesión que ya no existe.
 *  - Con "Mantener la sesión iniciada en este equipo" no hay cierre por inactividad: solo
 *    se vigila que no la hayan cerrado desde otro equipo o el administrador.
 *
 * API: sciafInactividad.porInactividad() → ¿la sesión se cerró (o debió cerrarse) por inactividad?
 *      sciafInactividad.mostrarCerrada(motivo) → aviso de sesión cerrada ('inactividad' o 'revocada').
 */
(function () {
    'use strict';

    // Pantalla restaurada desde la caché de "Atrás/Adelante": se pide de nuevo al servidor,
    // que decide si la sesión sigue (y si no, manda al inicio de sesión).
    window.addEventListener('pageshow', function (e) {
        if (e.persisted) window.location.reload();
    });

    if (window.sciafInactividad) return;

    const LATIDO_MS = 30000;        // consulta normal
    const LATIDO_AVISO_MS = 5000;   // con el aviso abierto (por si siguen en otra pestaña)
    const MOTIVO = 'X-Sciaf-Sesion';

    let ultimaInteraccion = Date.now();
    let vence = null;               // instante estimado (ms) en que vence, según el servidor
    let avisoSeg = 120;
    let limiteSeg = 1800;
    let tConsulta = null;
    let tCuenta = null;
    let estado = 'normal';          // normal | aviso | cerrada
    let esperandoOtraPestana = false;
    let tituloAntes = null;         // título de la pestaña antes de poner la cuenta regresiva
    let caja = null;

    const canal = ('BroadcastChannel' in window) ? new BroadcastChannel('sciaf-sesion') : null;

    // ── Uso real de la persona ──────────────────────────────────────────────
    // Sin 'scroll': también lo dispara el código (una tabla que se recarga sola), y eso no
    // es una persona. Rueda, teclado, toque y mouse cubren el scroll hecho a mano.
    ['mousemove', 'mousedown', 'keydown', 'touchstart', 'wheel'].forEach(ev =>
        document.addEventListener(ev, () => {
            // Con el aviso abierto no cuenta: la sesión sigue solo pulsando "Seguir conectado".
            if (estado === 'normal') ultimaInteraccion = Date.now();
        }, { passive: true, capture: true }));

    function inactivoSeg() {
        return Math.max(0, Math.round((Date.now() - ultimaInteraccion) / 1000));
    }

    // ── Consulta al servidor ────────────────────────────────────────────────
    function programar(ms) {
        clearTimeout(tConsulta);
        tConsulta = setTimeout(consultar, Math.max(1000, ms));
    }

    async function pedir(url, cuerpo) {
        return fetch(url, {
            method: 'POST',
            headers: { 'X-Requested-With': 'XMLHttpRequest', 'Content-Type': 'application/x-www-form-urlencoded' },
            body: cuerpo || '',
            cache: 'no-store'
        });
    }

    async function consultar() {
        clearTimeout(tConsulta);
        // Cerrada: solo se consulta si eligió ingresar en otra pestaña (para retomar acá).
        if (estado === 'cerrada' && !esperandoOtraPestana) return;
        let r;
        try {
            r = await pedir('/api/sesion/estado', 'inactivoSeg=' + inactivoSeg());
        } catch (e) {
            programar(estado === 'normal' ? LATIDO_MS : LATIDO_AVISO_MS); // sin red: se reintenta
            return;
        }
        if (r.status === 401) {
            sinSesion(r);
            return;
        }
        let d;
        try {
            if (!r.ok) throw new Error('HTTP ' + r.status);
            d = await r.json();
        } catch (e) {
            // Error del servidor o respuesta que no es JSON (proxy, mantenimiento): se reintenta.
            programar(estado === 'normal' ? LATIDO_MS : LATIDO_AVISO_MS);
            return;
        }
        aplicar(d);
    }

    function aplicar(d) {
        // Respuesta atrasada de antes del cierre: no "recupera" nada (solo vale si eligió
        // ingresar en otra pestaña y está esperando).
        if (estado === 'cerrada' && !esperandoOtraPestana) return;
        avisoSeg = d.avisoSeg || avisoSeg;
        limiteSeg = d.limiteSeg || limiteSeg;
        vence = d.recordada ? null : Date.now() + d.restanteSeg * 1000;
        if (estado === 'cerrada') {
            // Volvió a ingresar en otra pestaña: esta sigue trabajando donde estaba.
            cerrarCaja();
            estado = 'normal';
            esperandoOtraPestana = false;
            ultimaInteraccion = Date.now();
            if (window.Swal) Swal.fire({ toast: true, position: 'top-end', icon: 'success', timer: 3500,
                showConfirmButton: false, title: 'Sesión recuperada' });
        }
        if (d.recordada) {
            // Sesión mantenida: no vence por inactividad. Se sigue consultando solo para
            // enterarse si la cierran desde otro equipo.
            if (estado === 'aviso') ocultarAviso();
            programar(LATIDO_MS);
        } else if (d.restanteSeg <= avisoSeg) {
            mostrarAviso();
            programar(LATIDO_AVISO_MS);
        } else {
            if (estado === 'aviso') ocultarAviso();
            // Se vuelve a consultar justo cuando correspondería avisar (o antes, si pasa el latido).
            programar(Math.min(LATIDO_MS, (d.restanteSeg - avisoSeg) * 1000 + 500));
        }
    }

    function sinSesion(r) {
        if (estado === 'cerrada') {
            programar(LATIDO_AVISO_MS); // sigue esperando el ingreso en la otra pestaña
            return;
        }
        const motivo = r.headers.get(MOTIVO);
        if (motivo === 'revocada') {
            mostrarCerrada('revocada');
            return;
        }
        if (motivo === 'inactividad' || vencioSegunReloj() || estado !== 'normal') {
            mostrarCerrada('inactividad');
            return;
        }
        // Se cerró por otra causa (reinicio del servidor, el administrador la cerró...):
        // el aviso de siempre, que permite ingresar en otra pestaña sin perder la pantalla.
        if (window.sciafSesion) window.sciafSesion.avisar('Se detectó que su sesión ya no está activa.');
        programar(LATIDO_MS);
    }

    function vencioSegunReloj() {
        return vence !== null && Date.now() >= vence - 1500;
    }

    // ── Interfaz ────────────────────────────────────────────────────────────
    function estilos() {
        if (document.getElementById('sciaf-inactividad-css')) return;
        const st = document.createElement('style');
        st.id = 'sciaf-inactividad-css';
        st.textContent = `
            .sciaf-inact-fondo { position: fixed; inset: 0; z-index: 20000; display: flex;
                align-items: center; justify-content: center; padding: 16px;
                background: rgba(30, 32, 50, .45); }
            .sciaf-inact-fondo.cerrada { background: rgba(30, 32, 50, .82);
                backdrop-filter: blur(6px); -webkit-backdrop-filter: blur(6px); }
            .sciaf-inact-caja { background: #fff; color: #444050; border-radius: 12px;
                box-shadow: 0 10px 40px rgba(0,0,0,.25); max-width: 420px; width: 100%;
                padding: 28px 24px 22px; text-align: center; font-size: .95rem; }
            .sciaf-inact-icono { font-size: 2.6rem; line-height: 1; color: #ff9f43; margin-bottom: 8px; }
            .sciaf-inact-fondo.cerrada .sciaf-inact-icono { color: #ea5455; }
            .sciaf-inact-caja h5 { margin: 0 0 8px; font-weight: 600; color: inherit; }
            .sciaf-inact-cuenta { font-size: 2.4rem; font-weight: 700; letter-spacing: 1px;
                font-variant-numeric: tabular-nums; margin: 6px 0 4px; color: #ff9f43; }
            .sciaf-inact-nota { font-size: .82rem; color: #8a8d93; margin: 6px 0 0; }
            .sciaf-inact-botones { display: flex; gap: 10px; justify-content: center;
                flex-wrap: wrap; margin-top: 18px; }
            /* Botones propios: también se usa en pantallas sin Bootstrap (Hoja de ruta). */
            .sciaf-inact-btn { min-width: 150px; padding: 9px 18px; border-radius: 6px;
                border: 0; font: inherit; font-weight: 500; cursor: pointer;
                background: #ebebed; color: #444050; }
            .sciaf-inact-btn:hover { filter: brightness(.95); }
            .sciaf-inact-btn:disabled { opacity: .6; cursor: default; }
            .sciaf-inact-btn.primario { background: var(--bs-primary, #186dde); color: #fff; }
            .sciaf-inact-btn:focus-visible { outline: 3px solid rgba(24,109,222,.45); outline-offset: 2px; }
            html.dark-style .sciaf-inact-caja { background: #2f3349; color: #cfd3ec; }
            html.dark-style .sciaf-inact-btn:not(.primario) { background: #44475b; color: #cfd3ec; }
        `;
        document.head.appendChild(st);
    }

    function abrirCaja(clase, html) {
        estilos();
        if (!caja) {
            caja = document.createElement('div');
            caja.setAttribute('role', 'alertdialog');
            caja.setAttribute('aria-modal', 'true');
            document.body.appendChild(caja);
        }
        caja.className = 'sciaf-inact-fondo ' + clase;
        caja.innerHTML = '<div class="sciaf-inact-caja">' + html + '</div>';
        const principal = caja.querySelector('[data-principal]');
        if (principal) principal.focus();
    }

    function cerrarCaja() {
        if (caja) { caja.remove(); caja = null; }
        clearInterval(tCuenta);
        tCuenta = null;
        if (tituloAntes !== null) {
            document.title = tituloAntes;
            tituloAntes = null;
        }
    }

    function mmss(seg) {
        seg = Math.max(0, Math.ceil(seg));
        return Math.floor(seg / 60) + ':' + String(seg % 60).padStart(2, '0');
    }

    function mostrarAviso() {
        if (estado === 'aviso') return;
        estado = 'aviso';
        tituloAntes = document.title;
        abrirCaja('aviso', `
            <div class="sciaf-inact-icono"><i class="ti ti-clock-exclamation"></i></div>
            <h5>¿Sigue ahí?</h5>
            <div>Por seguridad, su sesión se cerrará por inactividad en</div>
            <div class="sciaf-inact-cuenta" data-cuenta>—</div>
            <p class="sciaf-inact-nota">Si sigue conectado, lo que tiene en pantalla queda como está.</p>
            <div class="sciaf-inact-botones">
                <button type="button" class="sciaf-inact-btn" data-salir>
                    <i class="ti ti-logout me-1"></i> Cerrar sesión</button>
                <button type="button" class="sciaf-inact-btn primario" data-principal data-seguir>
                    <i class="ti ti-player-play me-1"></i> Seguir conectado</button>
            </div>`);
        caja.querySelector('[data-seguir]').addEventListener('click', seguir);
        caja.querySelector('[data-salir]').addEventListener('click', () => { window.location.href = '/cerrar_sesion'; });
        const pintar = () => {
            const seg = vence === null ? avisoSeg : (vence - Date.now()) / 1000;
            const el = caja && caja.querySelector('[data-cuenta]');
            if (el) el.textContent = mmss(seg);
            if (tituloAntes !== null) document.title = '(' + mmss(seg) + ') ' + tituloAntes;
            if (seg <= 0) {
                clearInterval(tCuenta);
                consultar(); // el servidor confirma el cierre (o que se siguió en otra pestaña)
            }
        };
        pintar();
        tCuenta = setInterval(pintar, 1000);
    }

    function ocultarAviso() {
        estado = 'normal';
        cerrarCaja();
    }

    async function seguir() {
        const btn = caja && caja.querySelector('[data-seguir]');
        if (btn) btn.disabled = true;
        try {
            const r = await pedir('/api/sesion/renovar');
            if (r.status === 401) { sinSesion(r); return; }
            if (!r.ok) throw new Error('HTTP ' + r.status);
            ultimaInteraccion = Date.now();
            ocultarAviso();
            aplicar(await r.json());
            if (canal) canal.postMessage('renovada');
        } catch (e) {
            if (btn) btn.disabled = false;
        }
    }

    function mostrarCerrada(motivo) {
        if (estado === 'cerrada') return;
        estado = 'cerrada';
        clearTimeout(tConsulta);
        cerrarCaja();
        const revocada = motivo === 'revocada';
        const min = Math.round(limiteSeg / 60);
        abrirCaja('cerrada', `
            <div class="sciaf-inact-icono"><i class="ti ti-lock"></i></div>
            <h5>${revocada ? 'Se cerró su sesión en este equipo' : 'Su sesión se cerró por inactividad'}</h5>
            <div>${revocada
                ? 'La cerró usted desde otro equipo, o el administrador. Para seguir, vuelva a ingresar.'
                : 'Pasaron ' + min + ' minutos sin usar el sistema. Para seguir, vuelva a ingresar.'}</div>
            <p class="sciaf-inact-nota" data-nota>Si tenía algo a medio cargar en esta pantalla, ingrese
                en otra pestaña: al volver acá, esta pantalla sigue donde estaba.</p>
            <div class="sciaf-inact-botones">
                <button type="button" class="sciaf-inact-btn" data-otra>
                    <i class="ti ti-external-link me-1"></i> Ingresar en otra pestaña</button>
                <button type="button" class="sciaf-inact-btn primario" data-principal data-ingresar>
                    <i class="ti ti-login me-1"></i> Volver a ingresar</button>
            </div>`);
        caja.querySelector('[data-ingresar]').addEventListener('click', () => {
            window.location.replace('/?sesion=' + (revocada ? 'revocada' : 'inactividad'));
        });
        caja.querySelector('[data-otra]').addEventListener('click', () => {
            window.open('/', '_blank');
            esperandoOtraPestana = true;
            const nota = caja && caja.querySelector('[data-nota]');
            if (nota) nota.textContent = 'Esperando que ingrese en la otra pestaña…';
            programar(LATIDO_AVISO_MS);
        });
        if (canal) canal.postMessage('cerrada');
    }

    // ── Varias pestañas ─────────────────────────────────────────────────────
    if (canal) {
        canal.onmessage = (ev) => {
            if (ev.data === 'renovada' && estado === 'aviso') consultar();
            if (ev.data === 'cerrada' && estado !== 'cerrada') consultar();
        };
    }
    // Al volver a la pestaña (o despertar la computadora) se consulta ya: los temporizadores
    // de una pestaña en segundo plano se atrasan.
    document.addEventListener('visibilitychange', () => {
        if (document.visibilityState === 'visible') {
            if (estado === 'cerrada') { if (esperandoOtraPestana) consultar(); }
            else consultar();
        }
    });

    window.sciafInactividad = {
        porInactividad: () => estado === 'cerrada' || vencioSegunReloj(),
        mostrarCerrada: mostrarCerrada
    };

    programar(1500);
})();
