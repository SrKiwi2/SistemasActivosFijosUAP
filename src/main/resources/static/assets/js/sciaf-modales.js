/**
 * sciaf-modales.js
 * Deja escribir en los diálogos que se abren ENCIMA de un modal de Bootstrap.
 *
 * Bootstrap 5 encierra el foco dentro del modal abierto: cualquier cosa que aparezca por
 * encima —un SweetAlert con campo de texto, el buscador de un Select2— recibe el foco y
 * Bootstrap se lo quita de inmediato, así que no se puede escribir. Es lo que pasaba al
 * registrar un cargo nuevo desde el formulario de Responsable: solo funcionaba después de
 * cerrar el modal principal. También afecta a los motivos que se piden para las
 * solicitudes de autorización, que se escriben con el formulario abierto.
 *
 * La corrección: mientras el foco esté dentro de un diálogo superpuesto, se corta el
 * evento en fase de captura, antes de que Bootstrap lo vea. No se toca el modal ni se
 * desactiva su encierro de foco para el resto de los casos.
 */
(function () {
    'use strict';

    /** Contenedores que se dibujan por encima de un modal y necesitan el foco. */
    const SELECTORES = [
        '.swal2-container',      // SweetAlert2 (avisos y campos de texto)
        '.select2-container',    // buscador de los desplegables
        '.flatpickr-calendar',   // calendarios
        '.daterangepicker'
    ].join(',');

    document.addEventListener('focusin', function (e) {
        const destino = e.target;
        if (!destino || !destino.closest) return;
        if (destino.closest(SELECTORES)) {
            // Bootstrap escucha focusin en document para devolver el foco al modal:
            // cortándolo acá, el campo de arriba conserva el cursor.
            e.stopImmediatePropagation();
        }
    }, true);
})();
