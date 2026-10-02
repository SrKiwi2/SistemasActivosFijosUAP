/**
 * sciaf-menu-vivo.js — Menú y permisos actualizados en vivo, sin cerrar sesión.
 *
 * Cuando el administrador cambia los permisos de un usuario, su rol, o el catálogo del
 * menú (orden, nombres, íconos, bloqueos), el servidor avisa por SSE (topbar.js lo
 * reemite como 'sciaf:permisos' / 'sciaf:menu'). Acá:
 *   1. se pide el menú nuevo (/adm/menu/items) y se reemplaza el contenido del sidebar,
 *      conservando los grupos abiertos y el ítem activo;
 *   2. se avisa qué se habilitó y qué se quitó;
 *   3. las pestañas abiertas de módulos que ya no corresponden (sin permiso o
 *      bloqueados) se cierran, avisando antes si es la que el usuario está mirando;
 *   4. si el administrador cerró la sesión (usuario desactivado, contraseña cambiada),
 *      se vuelve al inicio de sesión.
 *
 * La sesión del servidor ya se puso al día sola (SesionPermisosInterceptor): esto es
 * solo la parte visible.
 */
(function () {
    'use strict';

    const INICIO = '/adm/inicio';
    let enCurso = false;
    let repetir = null;

    function $inner() {
        return document.querySelector('#layout-menu .menu-inner');
    }

    function urlsNavegables() {
        return Array.from(document.querySelectorAll('#layout-menu .menu-item[data-url]'))
            .map(li => li.getAttribute('data-url')).filter(Boolean);
    }

    function tituloDe(url) {
        const li = document.querySelector(`#layout-menu .menu-item[data-ruta="${CSS.escape(url)}"]`);
        const t = li && li.querySelector('.menu-link > div');
        return t ? t.textContent.trim() : url;
    }

    function toast(icono, titulo, texto, ms) {
        if (!window.Swal) return;
        Swal.fire({ toast: true, position: 'top-end', icon: icono, title: titulo,
            text: texto || undefined, showConfirmButton: false, timer: ms || 5000, timerProgressBar: true });
    }

    function sesionCerrada(mensaje) {
        if (window.sciafInactividad && window.sciafInactividad.porInactividad()) {
            window.sciafInactividad.mostrarCerrada('inactividad');
            return;
        }
        const ir = () => { window.location.href = '/'; };
        if (!window.Swal) { ir(); return; }
        Swal.fire({
            icon: 'warning',
            title: 'Su sesión fue cerrada',
            text: mensaje || 'El administrador cerró su sesión. Vuelva a ingresar.',
            confirmButtonText: 'Ir al inicio de sesión',
            allowOutsideClick: false
        }).then(ir);
    }

    async function sesionViva() {
        try {
            const r = await fetch('/adm/cargar-datos', { headers: { 'X-Requested-With': 'XMLHttpRequest' },
                cache: 'no-store' });
            return r.status !== 401;
        } catch (e) {
            return true; // sin red no se sabe: no se saca al usuario
        }
    }

    /** Reemplaza el menú. Devuelve {antes, despues} con las URLs navegables. */
    async function recargarMenu() {
        const inner = $inner();
        if (!inner) return null;
        const r = await fetch('/adm/menu/items', { headers: { 'X-Requested-With': 'XMLHttpRequest' },
            cache: 'no-store' });
        if (r.status === 401) { sesionCerrada(); return null; }
        if (!r.ok) return null;
        const html = await r.text();

        const antes = urlsNavegables();
        const abiertos = Array.from(inner.querySelectorAll('.menu-item.open[data-codigo]'))
            .map(li => li.getAttribute('data-codigo'));
        const activo = inner.querySelector('.menu-item.active[data-url]');
        const urlActiva = activo ? activo.getAttribute('data-url') : null;

        inner.innerHTML = html;

        abiertos.forEach(c => {
            const li = inner.querySelector(`.menu-item[data-codigo="${CSS.escape(c)}"]`);
            if (li) li.classList.add('open');
        });
        if (urlActiva) {
            const li = inner.querySelector(`.menu-item[data-url="${CSS.escape(urlActiva)}"]`);
            if (li) li.classList.add('active');
        }
        const despues = urlsNavegables();
        despues.filter(u => !antes.includes(u)).forEach(u => {
            const li = inner.querySelector(`.menu-item[data-url="${CSS.escape(u)}"]`);
            if (li) {
                li.classList.add('menu-nuevo');
                const grupo = li.closest('.menu-sub') && li.closest('.menu-sub').parentElement;
                if (grupo) grupo.classList.add('open');
            }
        });

        // Tema Vuexy: que el scroll del menú recalcule su alto.
        try {
            const m = document.getElementById('layout-menu');
            if (m && m.menuInstance && typeof m.menuInstance.update === 'function') m.menuInstance.update();
        } catch (e) { /* no es crítico */ }

        return { antes, despues };
    }

    /**
     * Cierra las pestañas de módulos que se acaban de perder: estaban en el menú antes
     * del cambio y ya no (sin permiso o bloqueados). Lo que se abrió desde dentro de otra
     * pantalla y nunca fue una opción del menú no se toca.
     */
    async function cerrarPestanasSinAcceso(antes, despues) {
        const P = window.sciafPestanas;
        if (!P || typeof P.lista !== 'function') return;
        const perdidas = new Set(antes.filter(u => !despues.includes(u)));
        if (!perdidas.size) return;
        // Una pestaña abierta con parámetros (…/vista?codigo=X) es la misma pantalla del menú.
        const sinQuery = u => String(u).split('?')[0];
        const lista = P.lista();
        const sinAcceso = lista.filter(p => p.url && p.url !== INICIO && perdidas.has(sinQuery(p.url)));
        if (!sinAcceso.length) return;

        const motivo = url => document.querySelector(
            `#layout-menu .menu-item[data-bloqueado][data-ruta="${CSS.escape(sinQuery(url))}"]`)
            ? 'bloqueado por el administrador' : 'ya no está entre sus permisos';
        const nombres = sinAcceso.map(p => `«${p.titulo || tituloDe(p.url)}» (${motivo(p.url)})`);

        if (window.Swal) {
            await Swal.fire({
                icon: 'info',
                title: sinAcceso.length === 1 ? 'Se cerrará una pestaña' : `Se cerrarán ${sinAcceso.length} pestañas`,
                html: 'El administrador actualizó sus accesos:<br><br>' + nombres.map(n => '• ' + escapar(n)).join('<br>'),
                confirmButtonText: 'Entendido'
            });
        }
        // Abrir una pestaña ya cargada solo la pone al frente (no la vuelve a pedir):
        // así se puede cerrar con la API que ya existe.
        sinAcceso.forEach(p => {
            try { P.abrir(p.url); P.cerrarActiva(); } catch (e) { /* sigue con las demás */ }
        });
    }

    function escapar(t) {
        const d = document.createElement('div');
        d.textContent = t == null ? '' : String(t);
        return d.innerHTML;
    }

    async function aplicar(tipo, detalle) {
        if (enCurso) { repetir = { tipo, detalle }; return; }
        enCurso = true;
        try {
            if (detalle && detalle.cerrarSesion) {
                if (!(await sesionViva())) { sesionCerrada(detalle.mensaje); return; }
            }
            const cambio = await recargarMenu();
            if (!cambio) return;

            const nuevas = cambio.despues.filter(u => !cambio.antes.includes(u)).map(tituloDe);
            const quitadas = cambio.antes.filter(u => !cambio.despues.includes(u)).map(tituloDe);

            if (tipo === 'permisos') {
                let texto = '';
                if (nuevas.length) texto += 'Nuevo: ' + nuevas.join(', ') + '. ';
                if (quitadas.length) texto += 'Quitado: ' + quitadas.join(', ') + '.';
                toast('info', (detalle && detalle.mensaje) || 'Sus permisos fueron actualizados',
                    texto || 'Su menú ya está al día.', 6500);
            } else if (nuevas.length || quitadas.length) {
                toast('info', 'El menú se actualizó',
                    (nuevas.length ? 'Nuevo: ' + nuevas.join(', ') + '. ' : '')
                    + (quitadas.length ? 'Ya no disponible: ' + quitadas.join(', ') + '.' : ''), 6000);
            }
            await cerrarPestanasSinAcceso(cambio.antes, cambio.despues);

            // La gestión de menú, si está abierta, se redibuja para reflejar el cambio
            // hecho desde otra máquina.
            if (tipo === 'menu' && typeof window.menuGestionRecargar === 'function') {
                window.menuGestionRecargar(true);
            }
        } catch (e) {
            console.warn('[Menú en vivo] No se pudo actualizar el menú:', e);
        } finally {
            enCurso = false;
            if (repetir) { const r = repetir; repetir = null; aplicar(r.tipo, r.detalle); }
        }
    }

    document.addEventListener('sciaf:permisos', e => aplicar('permisos', e.detail || {}));
    document.addEventListener('sciaf:menu', e => aplicar('menu', e.detail || {}));

    // Para probar desde la consola o forzar desde otra pantalla.
    window.sciafMenuVivo = { recargar: () => aplicar('menu', {}) };
})();
