/**
 * sciaf-mi-cuenta.js — Menú del avatar: "Cambiar mi contraseña" y "Mis sesiones abiertas".
 *
 * Contraseña: cualquier usuario cambia la suya (pide la actual). Opcionalmente cierra sus
 * otras sesiones abiertas (otra PC, el celular); la sesión desde la que se cambia sigue.
 *
 * Sesiones: un equipo por fila (navegador y sistema, IP, cuándo entró, último uso, si
 * mantiene la sesión iniciada). Se puede cerrar una o todas las demás. La lista la usa
 * también Usuarios → Sesiones abiertas (sciafSesiones.render).
 */
(function () {
    'use strict';

    window.sciafCambiarMiContrasena = function () {
        if (!window.Swal) return;
        Swal.fire({
            title: 'Cambiar mi contraseña',
            html: `<div class="text-start">
                <label class="form-label small mb-1" for="mcActual">Contraseña actual</label>
                <input type="password" id="mcActual" class="form-control mb-2" autocomplete="current-password">
                <label class="form-label small mb-1" for="mcNueva">Contraseña nueva</label>
                <input type="password" id="mcNueva" class="form-control mb-1" autocomplete="new-password"
                       placeholder="Mínimo 8 caracteres, con letras y números">
                <label class="form-label small mb-1 mt-1" for="mcConfirmar">Repetir la nueva</label>
                <input type="password" id="mcConfirmar" class="form-control mb-2" autocomplete="new-password">
                <div class="form-check mb-1">
                  <input class="form-check-input" type="checkbox" id="mcVer">
                  <label class="form-check-label small" for="mcVer">Mostrar contraseñas</label>
                </div>
                <div class="form-check">
                  <input class="form-check-input" type="checkbox" id="mcCerrar" checked>
                  <label class="form-check-label small" for="mcCerrar">Cerrar mis sesiones abiertas en otros equipos</label>
                </div>
                <div class="small text-muted mt-1">Los equipos donde mantuvo la sesión iniciada se cierran siempre.</div></div>`,
            focusConfirm: false,
            showCancelButton: true,
            confirmButtonText: 'Cambiar',
            cancelButtonText: 'Cancelar',
            reverseButtons: true,
            showLoaderOnConfirm: true,
            didOpen: () => {
                document.getElementById('mcActual').focus();
                document.getElementById('mcVer').addEventListener('change', e => {
                    ['mcActual', 'mcNueva', 'mcConfirmar'].forEach(id =>
                        document.getElementById(id).type = e.target.checked ? 'text' : 'password');
                });
            },
            preConfirm: async () => {
                const actual = document.getElementById('mcActual').value;
                const nueva = document.getElementById('mcNueva').value;
                const confirmacion = document.getElementById('mcConfirmar').value;
                if (!actual) { Swal.showValidationMessage('Escriba su contraseña actual.'); return false; }
                if (nueva.length < 8 || !/[A-Za-z]/.test(nueva) || !/\d/.test(nueva)) {
                    Swal.showValidationMessage('La nueva necesita al menos 8 caracteres, con letras y números.'); return false;
                }
                if (nueva !== confirmacion) { Swal.showValidationMessage('La confirmación no coincide.'); return false; }
                try {
                    const r = await fetch('/adm/mi-cuenta/contrasena', {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/x-www-form-urlencoded', 'X-Requested-With': 'XMLHttpRequest' },
                        body: new URLSearchParams({ actual, nueva, confirmacion,
                            cerrarOtras: document.getElementById('mcCerrar').checked })
                    });
                    const j = await r.json().catch(() => ({}));
                    if (!j.ok) { Swal.showValidationMessage(j.msg || 'No se pudo cambiar.'); return false; }
                    return j;
                } catch (e) {
                    Swal.showValidationMessage('Sin conexión con el servidor.');
                    return false;
                }
            }
        }).then(res => {
            if (res.isConfirmed) Swal.fire('Listo', res.value.msg, 'success');
        });
    };

    // ── Sesiones abiertas ───────────────────────────────────────────────────

    const esc = v => String(v ?? '').replace(/[&<>"']/g,
        c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

    const confirmar = o => window.sciafConfirmar
        ? window.sciafConfirmar(o)
        : Promise.resolve(window.confirm(o.titulo));

    async function pedir(url, metodo) {
        const r = await fetch(url, {
            method: metodo || 'GET',
            headers: { 'X-Requested-With': 'XMLHttpRequest' },
            cache: 'no-store'
        });
        const j = await r.json().catch(() => ({}));
        if (!r.ok && !j.msg) j.msg = 'No se pudo completar (HTTP ' + r.status + ').';
        return j;
    }

    /**
     * Pinta la lista de sesiones en {@code cont}. {@code alCerrar(sesion)} se llama al pulsar
     * "Cerrar" en una fila (la actual no tiene botón).
     */
    function render(cont, sesiones, alCerrar) {
        if (!sesiones.length) {
            cont.innerHTML = '<div class="text-muted text-center py-3">No hay sesiones abiertas.</div>';
            return;
        }
        cont.innerHTML = sesiones.map(s => {
            const movil = /Android|iPhone|iPad/.test(s.dispositivo || '');
            return `<div class="d-flex align-items-start gap-3 border rounded-3 p-3 mb-2 text-start">
                <i class="ti ${movil ? 'ti-device-mobile' : 'ti-device-desktop'} ti-lg mt-1
                   ${s.actual ? 'text-success' : 'text-secondary'}"></i>
                <div class="flex-grow-1">
                  <div class="fw-semibold">${esc(s.dispositivo)}
                    ${s.actual ? '<span class="badge bg-label-success ms-1">Este equipo</span>' : ''}
                    ${s.recordada ? '<span class="badge bg-label-info ms-1">Sesión mantenida</span>' : ''}</div>
                  <div class="small text-muted">IP ${esc(s.ip || '—')} · Entró el ${esc(s.inicio)}
                    · Último uso ${esc(s.ultimoUso)}</div>
                  ${s.recordada && s.vence
                    ? `<div class="small text-muted">Si no se usa, pedirá la contraseña desde el ${esc(s.vence)}</div>`
                    : ''}
                </div>
                ${s.actual || !alCerrar ? '' : `<button type="button" class="btn btn-sm btn-outline-danger"
                    data-cerrar="${esc(s.id)}"><i class="ti ti-logout me-1"></i>Cerrar</button>`}
              </div>`;
        }).join('');
        if (alCerrar) {
            cont.querySelectorAll('[data-cerrar]').forEach(b => b.addEventListener('click', () => {
                const s = sesiones.find(x => String(x.id) === b.getAttribute('data-cerrar'));
                if (s) alCerrar(s);
            }));
        }
    }

    window.sciafSesiones = { render };

    /** {@code aviso}: resultado de la última acción ({ok, msg}), se muestra arriba de la lista. */
    window.sciafMisSesiones = async function (aviso) {
        if (!window.Swal) return;
        const j = await pedir('/adm/mi-cuenta/sesiones');
        if (!j.ok) { Swal.fire('Mis sesiones abiertas', j.msg || 'No se pudo cargar.', 'error'); return; }
        const otras = j.sesiones.filter(s => !s.actual).length;
        Swal.fire({
            title: 'Mis sesiones abiertas',
            width: 680,
            html: (aviso && aviso.msg
                     ? `<div class="alert alert-${aviso.ok ? 'success' : 'warning'} small py-2 text-start">${esc(aviso.msg)}</div>`
                     : '')
                 + `<p class="small text-muted mb-3">Equipos donde su usuario tiene la sesión abierta.
                     Si ve uno que no reconoce, ciérrelo y cambie su contraseña.</p>
                   <div id="msLista"></div>`,
            showConfirmButton: otras > 0,
            confirmButtonText: '<i class="ti ti-logout me-1"></i> Cerrar todas las demás',
            showCancelButton: true,
            cancelButtonText: 'Listo',
            reverseButtons: true,
            customClass: { confirmButton: 'btn btn-outline-danger', cancelButton: 'btn btn-label-secondary me-2' },
            buttonsStyling: false,
            didOpen: () => render(document.getElementById('msLista'), j.sesiones, cerrarUna)
        }).then(res => { if (res.isConfirmed) cerrarOtras(otras); });
    };

    async function cerrarUna(s) {
        const si = await confirmar({
            titulo: '¿Cerrar la sesión en ' + s.dispositivo + '?',
            texto: 'En ese equipo tendrán que volver a ingresar con la contraseña.',
            aceptar: 'Sí, cerrarla', peligrosa: true
        });
        const r = si ? await pedir('/adm/mi-cuenta/sesiones/' + encodeURIComponent(s.id) + '/cerrar', 'POST') : null;
        window.sciafMisSesiones(r);
    }

    async function cerrarOtras(n) {
        const si = await confirmar({
            titulo: n === 1 ? '¿Cerrar la sesión del otro equipo?' : '¿Cerrar las ' + n + ' sesiones de otros equipos?',
            texto: 'Esta sesión sigue abierta. En los demás equipos tendrán que volver a ingresar.',
            aceptar: 'Sí, cerrarlas', peligrosa: true
        });
        window.sciafMisSesiones(si ? await pedir('/adm/mi-cuenta/sesiones/cerrar-otras', 'POST') : null);
    }
})();
