function cargarTabla() {
    $.ajax({
        type: 'GET',
        url: "/informacion",
        success: function (response) {
            $("#informacionActivosFijos").html(response);
        }
    });
}

//PARA VALIDAR LOS INPUTS
function validarCampo(input, mensaje) {
    if (!input.value.trim()) {
        input.classList.add("is-invalid");
        if (
            !input.nextElementSibling ||
            !input.nextElementSibling.classList.contains("invalid-feedback")
        ) {
            const div = document.createElement("div");
            div.className = "invalid-feedback";
            div.innerText = mensaje;
            input.parentNode.appendChild(div);
        }
    } else {
        input.classList.remove("is-invalid");
        if (
            input.nextElementSibling &&
            input.nextElementSibling.classList.contains("invalid-feedback")
        ) {
            input.nextElementSibling.remove();
        }
    }
}

//FILTROS DE BUSQUEDA API
$(function () {

    $('input[name="unidad"], input[name="ubicacionActivo"]').autocomplete({
        source: function (request, response) {
            $.ajax({
                url: "/api/oficinas/sugerencias",
                data: { termino: request.term },
                success: function (data) {
                    response(data);
                },
            });
        },
        minLength: 2,
        appendTo: "#formularioModalAsignacionActivo",
        delay: 300,
    });

    // $('input[name="codigoFuncionario"]').on("input", function () {
    //     const codigo = $(this).val();
    //     if (codigo.length >= 1) {
    //         $.get(
    //             "/api/responsables/datos",
    //             { codigo: codigo },
    //             function (data) {
    //                 if (data && data.ci) {
    //                     $('input[name="ci"]').val(data.ci);
    //                 } else {
    //                     $('input[name="ci"]').val("");
    //                 }
    //             }
    //         );
    //     }
    // });

    $('input[name="unidadDestino"], input[name="unidadOrigen"], input[name="ubicacionOrigen"], input[name="ubicacionActual"]').autocomplete({
        source: function (request, response) {
            $.ajax({
                url: "/api/oficinas/sugerencias",
                data: { termino: request.term },
                success: function (data) {
                    response(data);
                },
            });
        },
        minLength: 2,
        appendTo: "#formularioModalTranferenciaActivo",
        delay: 300,
    });

    $('input[name="unidadPropietario"], input[name="unidadAutorizador"]').autocomplete({
        source: function (request, response) {
            $.ajax({
                url: "/api/oficinas/sugerencias",
                data: { termino: request.term },
                success: function (data) {
                    response(data);
                },
            });
        },
        minLength: 2,
        appendTo: "#formIngresoActivos",
        delay: 300,
    });

    $('input[name^="codigoFuncionario"]').on("input", function () {
        const $this = $(this);
        const codigo = $this.val();

        if (codigo.length >= 1) {
            const tipo = $this.attr("name").replace("codigoFuncionario", "");
            $.get(
                "/api/responsables/datos",
                { codigo: codigo },
                function (data) {
                    if (data && data.ci) {
                        $(`input[name="ci${tipo}"]`).val(data.ci);
                    } else {
                        $(`input[name="ci${tipo}"]`).val("");
                    }
                }
            );
        }
    });
});

document.addEventListener("DOMContentLoaded", function () {
    const $modal = $("#modalPerfil");
    if ($modal.length === 0) return;

    // Evita duplicar listeners si el script se incluye más de una vez
    $modal.off("show.bs.modal").on("show.bs.modal", function () {
        const $loader = $("#perfilLoader");
        const $resp = $("#perfilResponsables").empty();
        const $ofis = $("#perfilOficinasBody").empty();

        // mostrar loader
        $loader.removeClass("d-none");

        $.getJSON("/adm/perfil/data")
            .done(function (d) {
                try {
                    // Encabezado
                    $("#perfilNombre").text(d?.nombreCompleto || "—");
                    $("#perfilCi").text(d?.ci || "—");
                    $("#perfilRol").text(d?.rol || "—");
                    $("#perfilUsuario").text(d?.usuario || "—");
                    $("#perfilCorreo").text(d?.correo || "—");
                    $("#perfilNacionalidad").text(d?.nacionalidad || "—");
                    $("#perfilGenero").text(d?.genero || "—");
                    $("#perfilActivosTotal").text(d?.activosTotal ?? 0);
                    $("#perfilCostoTotal").text(d?.costoTotal || "0,00");
                    $("#perfilAvatar").text((d?.avatarIniciales || "AZ").toUpperCase());

                    // Responsables
                    if (Array.isArray(d?.responsables) && d.responsables.length) {
                        d.responsables.forEach(r => {
                            $resp.append(`
                  <div class="p-2 rounded border">
                    <div class="fw-semibold">${r?.cargo || "—"}</div>
                    <div class="small text-muted"><i class="ti ti-building me-1"></i>${r?.oficina || "—"}</div>
                    <div class="small">
                      <span class="badge bg-light text-dark border">
                        <i class="ti ti-hash me-1"></i>${r?.codigoFuncionario || "s/c"}
                      </span>
                    </div>
                  </div>
                `);
                        });
                    } else {
                        $resp.append(`<div class="text-muted small">Sin responsabilidades asignadas.</div>`);
                    }

                    // Oficinas
                    if (Array.isArray(d?.oficinas) && d.oficinas.length) {
                        d.oficinas.forEach(o => {
                            $ofis.append(`
                  <tr>
                    <td>${o?.nombre || "-"}</td>
                    <td class="text-muted">${o?.codigo || "-"}</td>
                    <td class="text-end fw-semibold">${o?.total ?? 0}</td>
                  </tr>
                `);
                        });
                    } else {
                        $ofis.append(`<tr><td colspan="3" class="text-center text-muted">Sin activos registrados.</td></tr>`);
                    }
                } catch (err) {
                    console.error("Error renderizando perfil:", err);
                    $ofis.html(`<tr><td colspan="3" class="text-danger">Error al mostrar datos.</td></tr>`);
                }
            })
            .fail(function (xhr) {
                let msg = "No se pudo cargar el perfil.";
                try {
                    const j = xhr.responseJSON || JSON.parse(xhr.responseText);
                    if (j?.message) msg = j.message;
                } catch (e) { }
                $("#perfilOficinasBody").html(`<tr><td colspan="3" class="text-danger">${msg}</td></tr>`);
            })
            .always(function () {
                // ocultar loader SIEMPRE, incluso si hubo error o excepción en .done
                $loader.addClass("d-none");
            });
    });

    // Reseteo de seguridad: si el modal se cierra, oculta el loader
    $modal.off("hidden.bs.modal").on("hidden.bs.modal", function () {
        $("#perfilLoader").addClass("d-none");
    });
});

/* configuracion tabla de responsable */

$(function () {
    // Tooltips
    const initTooltips = () => {
        document.querySelectorAll('[data-bs-toggle="tooltip"]').forEach(el => {
            new bootstrap.Tooltip(el, { container: 'body' });
        });
    };

    const estadoBadge = (txt) => {
        if (!txt) return '<span class="badge bg-secondary-subtle text-secondary">—</span>';
        const t = txt.toString().toUpperCase();
        if (t.includes('BUENO') || t.includes('OPERATIVO')) return `<span class="badge bg-success-subtle text-success">${txt}</span>`;
        if (t.includes('REGULAR') || t.includes('MANT')) return `<span class="badge bg-warning-subtle text-warning">${txt}</span>`;
        if (t.includes('MALO') || t.includes('BAJA') || t.includes('INOPERATIVO'))
            return `<span class="badge bg-danger-subtle text-danger">${txt}</span>`;
        return `<span class="badge bg-info-subtle text-info">${txt}</span>`;
    };

    const getText = (v) => {
        if (v == null) return '';
        if (typeof v === 'string') return v.indexOf('<') !== -1 ? $('<div>').html(v).text() : v;
        if (v.jquery) return v.text();
        if (v.nodeType) return v.textContent || '';
        return String(v);
    };

    function formatDetails(rowData) {
        const codigo = getText(rowData[1]);
        const nombre = getText(rowData[2]);
        const desc = getText(rowData[3]);
        const oficina = getText(rowData[4]);
        const estado = getText(rowData[5]);
        const vida = getText(rowData[6]);
        const fecha = getText(rowData[7]);
        return `
        <div class="row g-3 p-3">
          <div class="col-md-8">
            <div class="small text-muted">Descripción</div>
            <div class="fw-semibold">${desc || '—'}</div>
          </div>
          <div class="col-md-4">
            <div class="small text-muted">Oficina</div>
            <div class="fw-semibold">${oficina || '—'}</div>
          </div>
          <div class="col-md-3">
            <div class="small text-muted">Estado</div>
            <div>${estadoBadge(estado)}</div>
          </div>
          <div class="col-md-2">
            <div class="small text-muted">Vida útil</div>
            <div class="fw-semibold">${vida || '—'}</div>
          </div>
          <div class="col-md-3">
            <div class="small text-muted">Fecha de adquisición</div>
            <div class="fw-semibold">${fecha || '—'}</div>
          </div>
          <div class="col-12">
            <div class="small text-muted">Código</div>
            <div class="fw-semibold">${codigo}</div>
          </div>
        </div>`;
    }

    if ($.fn.DataTable.isDataTable('#tablaActivos')) {
        $('#tablaActivos').DataTable().destroy();
    }

    const tabla = $('#tablaActivos').DataTable({
        responsive: true,
        pageLength: 10,
        lengthMenu: [[10, 25, 50, -1], [10, 25, 50, "Todos"]],
        order: [[1, 'asc']],
        language: {
            sProcessing: "Procesando...",
            sLengthMenu: "Mostrar _MENU_",
            sZeroRecords: "No se encontraron resultados",
            sInfo: "Mostrando _START_ a _END_ de _TOTAL_",
            sInfoEmpty: "Mostrando 0 a 0 de 0",
            sInfoFiltered: "(filtrado de _MAX_ total)",
            sSearch: "Buscar:",
            oPaginate: { sFirst: "Primero", sLast: "Último", sNext: "Siguiente", sPrevious: "Anterior" },
            buttons: { colvis: 'Columnas' }
        },
        dom: 'Bfrtip',
        buttons: [
            { extend: 'copy', className: 'buttons-copy' },
            { extend: 'excel', className: 'buttons-excel' },
            { extend: 'csv', className: 'buttons-csv' },
            {
                extend: 'pdf', className: 'buttons-pdf', orientation: 'landscape', pageSize: 'A4',
                exportOptions: { columns: ':visible' }
            },
            { extend: 'print', className: 'buttons-print' },
            { extend: 'colvis', className: 'buttons-colvis' }
        ],
        columnDefs: [
            { targets: 0, orderable: false, className: 'text-center' },
            { targets: 3, render: (d) => d ? $('<div>').text(d).html() : '—' },
            { targets: 5, render: (d) => estadoBadge(getText(d)), className: 'text-center' }
        ],
        initComplete: function () {
            const api = this.api(); // ✅ usa api dentro del callback

            // montar botones nativos en contenedor oculto
            api.buttons().containers().appendTo('#dtHiddenButtons');

            // tooltips
            initTooltips();

            // filtros dinámicos
            const colOficina = api.column(4);
            const colEstado = api.column(5);
            const unique = arr => [...new Set(arr)];

            const oficinas = unique(colOficina.data().toArray().map(getText).filter(Boolean)).sort();
            const estados = unique(colEstado.data().toArray().map(getText).filter(Boolean)).sort();

            oficinas.forEach(o => $('#filtroOficina').append(new Option(o, o)));
            estados.forEach(e => $('#filtroEstado').append(new Option(e, e)));

            // menú Exportar
            const $export = $('#exportMenu').empty();
            [
                ['Excel', '.buttons-excel', 'ti ti-file-spreadsheet'],
                ['CSV', '.buttons-csv', 'ti ti-file-type-csv'],
                ['PDF', '.buttons-pdf', 'ti ti-file-type-pdf'],
                ['Imprimir', '.buttons-print', 'ti ti-printer'],
                ['Copiar', '.buttons-copy', 'ti ti-copy']
            ].forEach(([label, cls, icon]) => {
                $('<li><a class="dropdown-item" href="#"><i class="' + icon + ' me-2"></i>' + label + '</a></li>')
                    .appendTo($export)
                    .on('click', (e) => { e.preventDefault(); api.button(cls).trigger(); }); // ✅ api
            });

            // menú Columnas
            const $colvis = $('#colvisMenu').empty();
            api.columns().every(function (idx) {
                if (idx === 0) return;
                const title = $(this.header()).text();
                const checked = this.visible();
                const id = 'colvis_' + idx;
                $colvis.append(`
            <div class="form-check">
              <input class="form-check-input" type="checkbox" id="${id}" ${checked ? 'checked' : ''} data-col="${idx}">
              <label class="form-check-label" for="${id}">${title}</label>
            </div>
          `);
            });
            $colvis.on('change', 'input[type=checkbox]', function () {
                const col = parseInt(this.dataset.col, 10);
                api.column(col).visible(this.checked).draw(false); // ✅ api
            });

            // resumen inicial
            actualizarResumen(api);
        }
    });

    // buscador
    $('#buscadorActivos').on('keyup change', function () { tabla.search(this.value).draw(); });

    // filtros
    $('#filtroOficina').on('change', function () {
        tabla.column(4).search(this.value || '', true, false).draw();
    });
    $('#filtroEstado').on('change', function () {
        tabla.column(5).search(this.value || '', true, false).draw();
    });

    // limpiar
    $('#btnReset').on('click', function () {
        $('#buscadorActivos').val('');
        $('#filtroOficina').val('');
        $('#filtroEstado').val('');
        tabla.search('').columns().search('').draw();
    });

    // fila expandible
    $('#tablaActivos tbody')
        .off('click', 'td.details-control')
        .on('click', 'td.details-control', function () {
            const tr = $(this).closest('tr');
            const row = tabla.row(tr);
            if (row.child.isShown()) {
                row.child.hide(); tr.removeClass('shown'); $(this).html('<i class="ti ti-chevron-down"></i>');
            } else {
                row.child(formatDetails(row.data()), 'details-row').show();
                tr.addClass('shown'); $(this).html('<i class="ti ti-chevron-up"></i>');
            }
        });

    // resumen al filtrar/paginar
    tabla.on('draw', function () { actualizarResumen(tabla); });

    function actualizarResumen(api) {
        // api puede ser la instancia (this.api()) o la variable tabla
        const dt = api.rows ? api : tabla;
        const data = dt.rows({ search: 'applied' }).data().toArray();

        // total
        $('#sumTotalActivos').text(data.length);

        // estados
        const estadoIdx = 5;
        const counts = {};
        data.forEach(r => {
            const key = (getText(r[estadoIdx]) || '—').trim();
            counts[key] = (counts[key] || 0) + 1;
        });
        const cont = $('#chipsEstados').empty();
        Object.keys(counts).sort().forEach(k => {
            cont.append(`<span class="badge rounded-pill me-1">${estadoBadge(k)} <span class="ms-1 text-muted">(${counts[k]})</span></span>`);
        });
    }
});