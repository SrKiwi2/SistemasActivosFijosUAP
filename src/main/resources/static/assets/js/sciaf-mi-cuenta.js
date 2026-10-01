/**
 * sciaf-mi-cuenta.js — "Cambiar mi contraseña" desde el menú del avatar.
 * Cualquier usuario cambia la suya (pide la actual). Opcionalmente cierra sus otras
 * sesiones abiertas (otra PC, el celular); la sesión desde la que se cambia sigue abierta.
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
                </div></div>`,
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
})();
