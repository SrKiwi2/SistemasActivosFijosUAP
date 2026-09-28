/**
 * sciaf-responsive.js
 * Adapta solo las pantallas a monitores chicos, tablets y celulares.
 *
 * El problema: las tablas del sistema tienen muchas columnas. En una pantalla angosta se
 * salen del área visible y hay que arrastrar de lado para llegar a los botones, o
 * directamente no se ven. Y ninguna pantalla declara qué columna es prescindible.
 *
 * Qué hace, sin tocar cada módulo:
 *  1. Copia el título de cada columna dentro de su celda (como etiqueta).
 *  2. En pantallas angostas la tabla deja de ser tabla: cada fila pasa a ser una tarjeta
 *     con "Etiqueta: valor", que se lee sin arrastrar nada.
 *  3. De cada tarjeta se muestran los primeros datos y los botones de acción; el resto
 *     queda detrás de "Ver más", para que la lista siga siendo navegable con el dedo.
 *  4. Se vuelve a aplicar cuando el módulo redibuja su tabla (paginar, filtrar, recargar).
 *
 * Una tabla puede quedar fuera con data-sciaf-no-responsive.
 */
(function () {
    'use strict';

    const VISIBLES = 3;          // datos que se ven sin desplegar (más la columna de acciones)
    const ANCHO_MOVIL = 768;

    function estilos() {
        if (document.getElementById('sciaf-responsive-css')) return;
        const st = document.createElement('style');
        st.id = 'sciaf-responsive-css';
        st.textContent = `
            @media (max-width: ${ANCHO_MOVIL - 0.02}px) {
                /* Las tablas pasan a tarjetas: una fila, un bloque legible */
                table.sciaf-apilada > thead { display: none; }
                table.sciaf-apilada,
                table.sciaf-apilada > tbody,
                table.sciaf-apilada > tbody > tr,
                table.sciaf-apilada > tbody > tr > td { display: block; width: auto !important; }
                /* Colores tomados del tema, para que la tarjeta no quede blanca en modo oscuro */
                table.sciaf-apilada > tbody > tr {
                    border: 1px solid var(--bs-border-color, #e4e8f0); border-radius: 12px;
                    margin-bottom: 10px; padding: 10px 12px;
                    background: var(--bs-card-bg, var(--bs-body-bg, #fff));
                    box-shadow: 0 1px 3px rgba(34,41,47,.06);
                }
                table.sciaf-apilada > tbody > tr > td {
                    display: flex; align-items: flex-start; justify-content: space-between; gap: 14px;
                    border: 0 !important; padding: 5px 0 !important; text-align: left !important;
                    white-space: normal !important; max-width: none !important; overflow: visible !important;
                }
                table.sciaf-apilada > tbody > tr > td::before {
                    content: attr(data-sciaf-label); flex: 0 0 38%;
                    font-size: .68rem; font-weight: 700; text-transform: uppercase; letter-spacing: .4px;
                    color: var(--bs-secondary-color, #8a93a8); padding-top: 2px;
                }
                table.sciaf-apilada > tbody > tr > td.sciaf-oculta { display: none; }
                table.sciaf-apilada > tbody > tr.sciaf-abierta > td.sciaf-oculta { display: flex; }
                table.sciaf-apilada .sciaf-vermas {
                    width: 100%; border: 0; background: #f0f4ff; color: #3b5bdb; border-radius: 8px;
                    font-size: .78rem; font-weight: 600; padding: 6px; margin-top: 6px; cursor: pointer;
                }
                table.sciaf-apilada .sciaf-vermas:hover { background: #e3eaff; }
                html.dark-style table.sciaf-apilada .sciaf-vermas {
                    background: rgba(105, 108, 255, .16); color: #a5b4ff;
                }
                html.dark-style table.sciaf-apilada .sciaf-vermas:hover { background: rgba(105, 108, 255, .28); }

                /* Las barras de herramientas dejan de salirse: se acomodan en varias líneas */
                #contenido .card-header,
                #contenido .btn-group,
                #contenido .d-flex.justify-content-between { flex-wrap: wrap; gap: .5rem; }
                #contenido .table-responsive { overflow-x: visible; }
                #contenido .dataTables_wrapper .row > div { width: 100%; }
                #contenido .dataTables_filter,
                #contenido .dataTables_length { text-align: left; float: none; }
                #contenido .dataTables_filter input { width: 100%; }

                /* Campos y listas desplegables: ninguno impone un ancho que no entra.
                   Los ancho-mínimo de estas vistas (190px, 210px…) sumados en una fila
                   empujaban el formulario fuera de la pantalla y había que deslizar. */
                #contenido .tf-group, #contenido .aa-ctrl-group,
                #contenido .tf-search .tf-input {
                    min-width: 0 !important; width: 100% !important; flex: 1 1 100% !important;
                }
                #contenido .tf-row, #contenido .tf-search,
                #contenido .aa-ctrl-row1, #contenido .aa-ctrl-row2 { flex-wrap: wrap !important; }
                #contenido input, #contenido select, #contenido textarea,
                #contenido .form-control, #contenido .form-select { max-width: 100% !important; }
                /* Select2 calcula su ancho en píxeles al iniciarse y se queda con ese
                   valor aunque la pantalla sea más angosta. */
                #contenido .select2-container { width: 100% !important; max-width: 100% !important; }
                #contenido .text-truncate { max-width: 100% !important; }
                /* La lista desplegable de Select2 se cuelga del body, fuera de #contenido */
                .select2-dropdown { max-width: 96vw; }
            }`;
        document.head.appendChild(st);
    }

    function esMovil() {
        return window.innerWidth < ANCHO_MOVIL;
    }

    /** Título de cada columna, para usarlo de etiqueta dentro de la celda. */
    function titulos(tabla) {
        const fila = tabla.querySelector('thead tr');
        if (!fila) return [];
        return Array.prototype.map.call(fila.children, th => (th.textContent || '').trim());
    }

    function adaptarTabla(tabla) {
        if (!tabla || tabla.hasAttribute('data-sciaf-no-responsive')) return;
        const cabeceras = titulos(tabla);
        if (!cabeceras.length) return;                          // tablas de maquetado: se dejan en paz
        tabla.classList.add('sciaf-apilada');

        /* En pantalla ancha solo se ponen las etiquetas (atributos, no cambian nada de lo que
           se ve). El botón y el plegado se agregan únicamente en pantalla angosta, para no
           meter un botón extra en filas que ningún módulo espera. */
        const angosta = esMovil();

        const filas = tabla.querySelectorAll(':scope > tbody > tr');
        filas.forEach(tr => {
            const celdas = Array.prototype.filter.call(tr.children, td => td.tagName === 'TD');
            if (celdas.length <= 1) return;                     // "sin resultados" y similares
            const ultima = celdas.length - 1;

            celdas.forEach((td, i) => {
                const etiqueta = cabeceras[i] || '';
                if (!td.getAttribute('data-sciaf-label')) td.setAttribute('data-sciaf-label', etiqueta);
                // Se ven los primeros datos y la última columna (donde suelen estar los botones).
                const prescindible = angosta && i >= VISIBLES && i !== ultima;
                td.classList.toggle('sciaf-oculta', prescindible);
            });

            const hayOcultas = tr.querySelector('td.sciaf-oculta');
            let boton = tr.querySelector('.sciaf-vermas');
            if (hayOcultas && !boton) {
                boton = document.createElement('button');
                boton.type = 'button';
                boton.className = 'sciaf-vermas';
                boton.textContent = 'Ver más';
                boton.addEventListener('click', function () {
                    const abierta = tr.classList.toggle('sciaf-abierta');
                    boton.textContent = abierta ? 'Ver menos' : 'Ver más';
                });
                celdas[ultima].appendChild(boton);
            } else if (!hayOcultas && boton) {
                boton.remove();
            }
        });
    }

    function adaptarTodo() {
        estilos();
        const raiz = document.getElementById('contenido') || document.body;
        raiz.querySelectorAll('table').forEach(adaptarTabla);
    }

    /* Las tablas se redibujan solas (paginar, filtrar, recargar por SSE): se vuelve a
       aplicar cuando el contenido cambia, sin que cada módulo tenga que avisar. */
    let pendiente = null;
    function programar() {
        clearTimeout(pendiente);
        pendiente = setTimeout(adaptarTodo, 250);
    }

    $(function () {
        adaptarTodo();
        const raiz = document.getElementById('contenido');
        if (raiz && window.MutationObserver) {
            new MutationObserver(programar).observe(raiz, { childList: true, subtree: true });
        }
        window.addEventListener('resize', programar);
        // Para que una pantalla pueda pedirlo a mano si hace algo muy suyo.
        window.sciafResponsive = { adaptar: adaptarTodo, esMovil };
    });
})();
