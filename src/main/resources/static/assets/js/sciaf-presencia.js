/**
 * sciaf-presencia.js — Avisa al servidor qué pantalla está mirando este usuario.
 *
 * Alimenta Supervisión → Usuarios conectados. Manda un aviso chico (POST /api/presencia):
 *   · al cambiar de pantalla (se detecta cuando cambia el contenido de #contenido);
 *   · cada 30 s mientras la página está abierta (si no, el servidor lo da por desconectado);
 *   · cuando la pestaña del navegador pasa a segundo plano o vuelve.
 * Incluye cuánto hace que no usa teclado ni mouse, para distinguir "en línea" de "ausente".
 * No manda nada del contenido de la pantalla: solo su nombre y su dirección.
 */
(function () {
    'use strict';

    if (!document.getElementById('layout-menu') || !document.getElementById('contenido')) return;

    const LATIDO_MS = 30000;
    let ultimaInteraccion = Date.now();
    let ultimoEnviado = '';
    let detenido = false;
    let tCambio = null;

    ['mousemove', 'keydown', 'click', 'scroll', 'touchstart', 'wheel'].forEach(ev =>
        document.addEventListener(ev, () => { ultimaInteraccion = Date.now(); }, { passive: true, capture: true }));

    function texto(el) {
        return el ? el.textContent.replace(/\s+/g, ' ').trim() : '';
    }

    /** Pantalla actual: la opción marcada en el menú o, si no hay, el título de la pantalla. */
    function vistaActual() {
        const activo = document.querySelector('#layout-menu .menu-item.active[data-url]');
        const h4 = document.querySelector('#contenido h4');
        if (activo) {
            const grupo = activo.closest('.menu-sub')?.previousElementSibling?.querySelector('div');
            const nombre = texto(activo.querySelector('.menu-link > div'));
            return {
                url: activo.getAttribute('data-url'),
                titulo: (grupo ? texto(grupo) + ' › ' : '') + nombre,
                icono: activo.querySelector('.menu-link i')?.className || null
            };
        }
        const titulo = texto(h4) || 'Inicio';
        return { url: titulo === 'Inicio' ? '/adm/inicio' : '#' + titulo, titulo: titulo, icono: 'ti ti-home' };
    }

    function pestanas() {
        try {
            return window.sciafPestanas && typeof window.sciafPestanas.lista === 'function'
                ? window.sciafPestanas.lista().map(p => p.titulo || p.url).filter(Boolean)
                : [];
        } catch (e) { return []; }
    }

    function enviar(forzar) {
        if (detenido) return;
        const v = vistaActual();
        const datos = {
            url: v.url, titulo: v.titulo, icono: v.icono,
            pestanas: pestanas(),
            visible: document.visibilityState === 'visible',
            inactivoSeg: Math.round((Date.now() - ultimaInteraccion) / 1000)
        };
        const clave = datos.url + '|' + datos.titulo + '|' + datos.visible;
        if (!forzar && clave === ultimoEnviado) return;
        ultimoEnviado = clave;
        fetch('/api/presencia', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'X-Requested-With': 'XMLHttpRequest' },
            body: JSON.stringify(datos),
            keepalive: true,
            cache: 'no-store'
        }).then(r => {
            // Sesión perdida: se avisa ya, antes de que el usuario pulse Guardar y se entere
            // recién ahí. Se sigue avisando: si vuelve a ingresar (en otra pestaña), esta
            // pestaña retoma sola.
            if (r.status === 401) {
                ultimoEnviado = '';
                if (window.sciafSesion) window.sciafSesion.avisar('Se detectó que su sesión ya no está activa.');
            }
        }).catch(() => { /* sin red: el próximo latido reintenta */ });
    }

    function alCambiar() {
        clearTimeout(tCambio);
        tCambio = setTimeout(() => enviar(false), 1200);
    }

    // Cambio de pantalla: el contenido de #contenido se reemplaza (menú, pestañas, enlaces internos).
    new MutationObserver(alCambiar).observe(document.getElementById('contenido'), { childList: true });
    document.getElementById('layout-menu').addEventListener('click', alCambiar, true);
    document.addEventListener('visibilitychange', () => enviar(true));

    setInterval(() => enviar(true), LATIDO_MS);
    setTimeout(() => enviar(true), 1500);
})();
