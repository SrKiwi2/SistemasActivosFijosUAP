/**
 * sciaf-sesion.js — Qué hacer cuando la sesión se pierde en medio de un trabajo.
 *
 * Antes: si alguien tardaba (por ejemplo cargando marcas y series de muchas cámaras) y la
 * sesión vencía, al pulsar Guardar el servidor respondía con la página de ingreso y la
 * pantalla mostraba "Unexpected token <" o "Ir al login", que recargaba la página y se
 * perdía lo cargado.
 *
 * Ahora:
 *  - Mientras la página está abierta la sesión no vence: sciaf-presencia.js avisa cada 30 s.
 *  - Si igual se pierde (reinicio del servidor, sesión cerrada en otro lado), cualquier
 *    pedido que vuelva con la página de ingreso o 401 muestra UN aviso claro: lo que está
 *    en pantalla no se perdió; se inicia sesión en OTRA pestaña y se vuelve a pulsar
 *    Guardar acá. La cookie nueva vale para todo el navegador, así que esta pestaña sigue
 *    trabajando sin recargarse.
 *
 * API: sciafSesion.perdida(response) → ¿esta respuesta es "sesión perdida"?
 *      sciafSesion.avisar(texto)    → muestra el aviso (una sola vez aunque se repita).
 */
(function () {
    'use strict';

    // Pedidos que manejan la sesión por su cuenta (el chequeo de fragment.js, el aviso de
    // presencia, el menú en vivo): no deben disparar este aviso.
    const PROPIOS = ['/adm/cargar-datos', '/api/presencia', '/adm/menu/items', '/api/eventos'];
    const RUTAS_INGRESO = ['/', '/login', '/form-login'];

    let mostrando = false;
    let ultimo = 0;

    function esIngreso(url) {
        try {
            const u = new URL(url, window.location.origin);
            return u.origin === window.location.origin && RUTAS_INGRESO.includes(u.pathname.replace(/\/$/, '') || '/');
        } catch (e) { return false; }
    }

    function perdida(r) {
        if (!r) return false;
        if (r.status === 401) return true;
        return !!(r.redirected && esIngreso(r.url));
    }

    async function sesionViva() {
        try {
            const r = await fetch('/adm/cargar-datos', { headers: { 'X-Requested-With': 'XMLHttpRequest' }, cache: 'no-store' });
            return r.ok;
        } catch (e) { return false; }
    }

    function avisar(texto) {
        if (mostrando || Date.now() - ultimo < 8000 || !window.Swal) return;
        mostrando = true;
        ultimo = Date.now();
        Swal.fire({
            icon: 'warning',
            title: 'Su sesión se cerró',
            html: `<div class="text-start">
                     ${texto ? '<p class="mb-2">' + texto + '</p>' : ''}
                     <p class="mb-2"><strong>Lo que tiene en pantalla no se perdió.</strong> No cierre ni recargue esta pestaña.</p>
                     <ol class="mb-0 ps-3">
                       <li>Pulse <em>Iniciar sesión en otra pestaña</em> e ingrese con su usuario.</li>
                       <li>Vuelva a esta pestaña y pulse <em>Ya ingresé</em>.</li>
                       <li>Repita la acción (por ejemplo, Guardar).</li>
                     </ol></div>`,
            showCancelButton: true,
            confirmButtonText: 'Ya ingresé',
            cancelButtonText: 'Iniciar sesión en otra pestaña',
            reverseButtons: true,
            allowOutsideClick: false,
            preConfirm: async () => {
                if (await sesionViva()) return true;
                Swal.showValidationMessage('Todavía no hay sesión: ingrese en la otra pestaña y vuelva a intentar.');
                return false;
            }
        }).then(res => {
            if (res.dismiss === Swal.DismissReason.cancel) {
                window.open('/', '_blank');
                mostrando = false;
                ultimo = 0;
                setTimeout(() => avisar(texto), 400);      // queda el aviso para confirmar al volver
                return;
            }
            mostrando = false;
            if (res.isConfirmed) {
                Swal.fire({ toast: true, position: 'top-end', icon: 'success', timer: 3500, showConfirmButton: false,
                    title: 'Sesión recuperada: ya puede repetir la acción' });
            }
        });
    }

    window.sciafSesion = { perdida, avisar };

    // ── fetch: cualquier pedido que vuelva con la página de ingreso ─────────
    if (window.fetch && !window.fetch.__sciafSesion) {
        const original = window.fetch.bind(window);
        const envuelto = async function (recurso, opciones) {
            const r = await original(recurso, opciones);
            try {
                const url = typeof recurso === 'string' ? recurso : (recurso && recurso.url) || '';
                const ruta = new URL(url, window.location.origin).pathname;
                if (!PROPIOS.some(p => ruta.startsWith(p)) && perdida(r)) avisar();
            } catch (e) { /* nunca romper el pedido original */ }
            return r;
        };
        envuelto.__sciafSesion = true;
        window.fetch = envuelto;
    }

    // ── jQuery AJAX: lo mismo para las pantallas que usan $.ajax ───────────
    if (window.jQuery) {
        jQuery(document).ajaxComplete(function (ev, xhr, ajaxOpts) {
            try {
                const ruta = new URL(ajaxOpts.url, window.location.origin).pathname;
                if (PROPIOS.some(p => ruta.startsWith(p))) return;
                if (xhr.status === 401 || (xhr.responseURL && esIngreso(xhr.responseURL) && !esIngreso(ajaxOpts.url))) avisar();
            } catch (e) { /* nada */ }
        });
    }
})();
