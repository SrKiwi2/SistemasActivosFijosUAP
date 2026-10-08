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
 *   4. se recargan las vistas de las pestañas permitidas para actualizar también
 *      sus botones y datos condicionados por permisos;
 *   5. si el administrador cerró la sesión (usuario desactivado, contraseña cambiada),
 *      se vuelve al inicio de sesión.
 *
 * La sesión del servidor se pone al día en la siguiente petición
 * (SesionPermisosInterceptor); las vistas se piden después de esa actualización.
 */
(function () {
    'use strict';

    const INICIO = '/adm/inicio';
    let enCurso = false;
    let repetir = null;

    const estilo = document.createElement('style');
    estilo.textContent = `
        .sciaf-aviso-permisos.swal2-popup { max-width: calc(100vw - 24px); padding: 1.1rem 1.2rem; border-radius: 14px; box-shadow: 0 14px 38px rgba(26, 35, 60, .18); }
        .sciaf-aviso-permisos .swal2-title { font-size: 1.05rem; line-height: 1.3; text-align: left; padding-right: 1.4rem; }
        .sciaf-aviso-permisos .swal2-html-container { margin: .45rem 0 0; text-align: left; font-size: .88rem; max-height: min(48vh, 360px); overflow-y: auto; }
        .sciaf-aviso-permisos .swal2-close { font-size: 1.35rem; }
        .sciaf-aviso-texto { color: #697a8d; margin-bottom: .65rem; line-height: 1.4; }
        .sciaf-aviso-bloque { border-top: 1px solid #e6e9ef; padding-top: .55rem; margin-top: .55rem; }
        .sciaf-aviso-bloque > strong { display: block; font-size: .8rem; letter-spacing: .02em; margin-bottom: .35rem; }
        .sciaf-aviso-bloque.habilitados > strong { color: #198754; }
        .sciaf-aviso-bloque.retirados > strong { color: #b94343; }
        .sciaf-aviso-items { display: grid; gap: .35rem; }
        .sciaf-aviso-item { padding: .4rem .55rem; border-radius: 7px; background: #f7f8fb; }
        .sciaf-aviso-item span, .sciaf-aviso-item small { display: block; overflow-wrap: anywhere; }
        .sciaf-aviso-item span { font-weight: 600; line-height: 1.3; }
        .sciaf-aviso-item small { color: #6b7280; font-size: .74rem; margin-top: .1rem; }
    `;
    document.head.appendChild(estilo);

    function $inner() {
        return document.querySelector('#layout-menu .menu-inner');
    }

    function accesosNavegables() {
        return Array.from(document.querySelectorAll('#layout-menu .menu-item[data-url]'))
            .map(li => {
                const grupo = li.closest('.menu-sub')?.parentElement;
                return {
                    url: li.getAttribute('data-url'),
                    nombre: li.querySelector('.menu-link > div')?.textContent.trim() || 'Módulo',
                    modulo: grupo?.querySelector(':scope > .menu-link > div')?.textContent.trim() || '',
                    tipo: 'MODULO'
                };
            }).filter(item => item.url);
    }

    function cambiosVisibles(antes, despues) {
        const porUrlAntes = new Map(antes.map(a => [a.url, a]));
        const urlsAntes = new Set(porUrlAntes.keys());
        const urlsDespues = new Set(despues.map(a => a.url));
        return {
            habilitados: despues.filter(a => !urlsAntes.has(a.url)),
            retirados: antes.filter(a => !urlsDespues.has(a.url)),
            actualizados: despues.filter(a => {
                const previo = porUrlAntes.get(a.url);
                return previo && (previo.nombre !== a.nombre || previo.modulo !== a.modulo);
            }).map(a => ({ ...a, anterior: porUrlAntes.get(a.url).nombre }))
        };
    }

    function listaCambios(titulo, items, clase) {
        if (!items.length) return '';
        return `<div class="sciaf-aviso-bloque ${clase}">
            <strong>${titulo} <small>(${items.length})</small></strong>
            <div class="sciaf-aviso-items">${items.map(item => {
                const nombre = escapar(item.nombre || 'Acceso');
                const ubicacion = [item.modulo, item.seccion].filter(Boolean).map(escapar).join(' · ');
                const tipo = item.tipo === 'ACCION' ? 'Acción' : 'Módulo';
                return `<div class="sciaf-aviso-item"><span>${nombre}</span>
                    <small>${escapar(tipo)}${ubicacion ? ' · ' + ubicacion : ''}${item.anterior ? ' · Antes: ' + escapar(item.anterior) : ''}</small></div>`;
            }).join('')}</div>
        </div>`;
    }

    function avisarCambios(tipo, detalle, cambios) {
        if (!window.Swal) return;
        const habilitados = Array.isArray(cambios.habilitados) ? cambios.habilitados : [];
        const retirados = Array.isArray(cambios.retirados) ? cambios.retirados : [];
        const actualizados = Array.isArray(cambios.actualizados) ? cambios.actualizados : [];
        const total = habilitados.length + retirados.length + actualizados.length;
        const mensaje = detalle?.mensaje || (tipo === 'permisos' ? 'Sus permisos fueron actualizados' : 'El menú se actualizó');
        if (!total && tipo === 'menu') return;
        Swal.fire({
            toast: true,
            position: 'top-end',
            icon: total ? 'info' : 'success',
            title: tipo === 'permisos' ? 'Accesos actualizados' : 'Opciones del menú actualizadas',
            html: `<div class="sciaf-aviso-texto">${escapar(mensaje)}</div>`
                + listaCambios('Habilitados', habilitados, 'habilitados')
                + listaCambios('Retirados', retirados, 'retirados')
                + listaCambios('Actualizados', actualizados, 'actualizados'),
            width: 480,
            showConfirmButton: false,
            showCloseButton: true,
            timer: Math.min(16000, 6500 + total * 650),
            timerProgressBar: true,
            customClass: { popup: 'sciaf-aviso-permisos' }
        });
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

        const accesosAntes = accesosNavegables();
        const antes = accesosAntes.map(a => a.url);
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
        const accesosDespues = accesosNavegables();
        const despues = accesosDespues.map(a => a.url);
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

        return { antes, despues, accesosAntes, accesosDespues };
    }

    /**
     * Cierra las pestañas de módulos que se acaban de perder: estaban en el menú antes
     * del cambio y ya no (sin permiso o bloqueados). Lo que se abrió desde dentro de otra
     * pantalla y nunca fue una opción del menú no se toca.
     */
    async function cerrarPestanasSinAcceso(antes, despues, accesosAntes) {
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
        const nombres = sinAcceso.map(p => {
            const nombre = accesosAntes.find(a => a.url === sinQuery(p.url))?.nombre || 'Módulo';
            return `«${nombre}» (${motivo(p.url)})`;
        });

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

            const cambios = detalle?.cambios || cambiosVisibles(cambio.accesosAntes, cambio.accesosDespues);
            await cerrarPestanasSinAcceso(cambio.antes, cambio.despues, cambio.accesosAntes);

            // El sidebar nuevo no cambia el HTML ya cargado dentro de una pestaña.
            // Volver a pedir la vista activa aplica los th:if de botones (Editar,
            // Desaprobar, subir al VSIAF, etc.). Las demás se vuelven a pedir al abrirlas.
            let recargandoPagina = false;
            if (tipo === 'permisos' && window.sciafPestanas
                    && typeof window.sciafPestanas.actualizarPorPermisos === 'function') {
                recargandoPagina = window.sciafPestanas.actualizarPorPermisos({ tipo, detalle, cambios });
            }
            if (!recargandoPagina) avisarCambios(tipo, detalle, cambios);

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

    // Inicio necesita una recarga de página completa; mostrar el aviso al volver.
    try {
        const guardado = sessionStorage.getItem('sciaf.aviso.permisos');
        if (guardado) {
            sessionStorage.removeItem('sciaf.aviso.permisos');
            const aviso = JSON.parse(guardado);
            setTimeout(() => avisarCambios(aviso.tipo, aviso.detalle, aviso.cambios), 450);
        }
    } catch (_) { /* el navegador puede bloquear sessionStorage */ }

    // Para probar desde la consola o forzar desde otra pantalla.
    window.sciafMenuVivo = { recargar: () => aplicar('menu', {}) };
})();
