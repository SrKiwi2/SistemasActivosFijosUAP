/**
 * sciaf-confirmar.js
 * Una sola forma de preguntar "¿está seguro?" antes de guardar, modificar o eliminar.
 *
 * Antes cada pantalla preguntaba a su manera, o directamente no preguntaba: se hacía clic
 * sin querer y el registro ya estaba hecho (y en varios casos ya viajaba al VSIAF).
 *
 * Uso:
 *   sciafConfirmar({ titulo: '¿Registrar la oficina?', texto: 'Se enviará al VSIAF.' })
 *       .then(function (si) { if (si) guardar(); });
 *
 * Devuelve siempre una promesa que resuelve true/false, aunque no haya SweetAlert.
 */
(function () {
    'use strict';

    window.sciafConfirmar = function (opciones) {
        const o = opciones || {};
        const titulo = o.titulo || '¿Confirma la acción?';
        const texto = o.texto || '';

        // Sin SweetAlert (o en una pantalla que no lo cargó) igual se pregunta.
        if (!window.Swal) {
            const txt = texto ? (titulo + '\n\n' + texto.replace(/<[^>]*>/g, '')) : titulo;
            return Promise.resolve(window.confirm(txt));
        }

        return Swal.fire({
            title: titulo,
            html: texto,
            icon: o.peligrosa ? 'warning' : 'question',
            showCancelButton: true,
            confirmButtonText: o.aceptar || 'Sí, continuar',
            cancelButtonText: o.cancelar || 'Cancelar',
            reverseButtons: true,
            // En lo que no se puede deshacer, el botón preseleccionado es "Cancelar":
            // si alguien viene dándole Enter de corrido, no borra nada sin querer.
            focusCancel: o.peligrosa === true,
            customClass: { confirmButton: o.peligrosa ? 'btn btn-danger' : 'btn btn-primary',
                           cancelButton: 'btn btn-label-secondary ms-2' },
            buttonsStyling: false
        }).then(function (r) { return r.isConfirmed === true; });
    };

    /** Atajo para lo que no se puede deshacer (eliminar). */
    window.sciafConfirmarEliminar = function (queCosa, detalle) {
        return window.sciafConfirmar({
            titulo: '¿Eliminar ' + (queCosa || 'el registro') + '?',
            texto: (detalle ? detalle + '<br><br>' : '') + 'Esta acción no se puede deshacer.',
            aceptar: 'Sí, eliminar',
            peligrosa: true
        });
    };
})();
