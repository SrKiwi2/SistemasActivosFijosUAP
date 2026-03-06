let hojaRutaActual = null;
// Theme
const savedTheme = localStorage.getItem('theme') || 'light';
document.documentElement.setAttribute('data-theme', savedTheme);
document.getElementById('themeIcon').className = savedTheme === 'dark' ? 'bx bx-sun' : 'bx bx-moon';

document.getElementById('themeToggle').addEventListener('click', () => {
    const current = document.documentElement.getAttribute('data-theme');
    const newTheme = current === 'dark' ? 'light' : 'dark';
    document.documentElement.setAttribute('data-theme', newTheme);
    localStorage.setItem('theme', newTheme);
    document.getElementById('themeIcon').className = newTheme === 'dark' ? 'bx bx-sun' : 'bx bx-moon';
});

// User menu
document.getElementById('userButton').addEventListener('click', (e) => {
    e.stopPropagation();
    document.getElementById('userDropdown').classList.toggle('show');
});

document.addEventListener('click', () => {
    document.getElementById('userDropdown').classList.remove('show');
});

// Funciones principales
function verPerfil() {
    Swal.fire("Perfil", "Función de perfil en desarrollo", "info");
}

function cerrarSesion() {
    Swal.fire({
        title: '¿Cerrar sesión?',
        text: "¿Estás seguro de que deseas salir?",
        icon: 'question',
        showCancelButton: true,
        confirmButtonColor: '#C1272D',
        cancelButtonColor: '#6C757D',
        confirmButtonText: 'Sí, salir',
        cancelButtonText: 'Cancelar'
    }).then((result) => {
        if (result.isConfirmed) {
            window.location.href = '/cerrar_sesion';
        }
    });
}

function inicializarSelect2EnModal(modalId) {
    $('#' + modalId + ' .select2').each(function () {
        if ($(this).hasClass("select2-hidden-accessible")) {
            $(this).select2('destroy');
        }
    });

    $('#' + modalId + ' .select2').select2({
        dropdownParent: $('#' + modalId),
        width: '100%',
        language: {
            noResults: () => "No se encontraron resultados",
            searching: () => "Buscando..."
        },
        placeholder: "Seleccionar",
        allowClear: true
    });
}

function validarFiltros() {
    const tipo = $("#filtroTipo").val();
    const gestion = $("#filtroGestion").val();

    if (tipo && gestion) {
        $("#filtroCodigo").prop("disabled", false);
        $("#btnBuscar").prop("disabled", false);
    } else {
        $("#filtroCodigo").prop("disabled", true).val("");
        $("#btnBuscar").prop("disabled", true);
    }
}

function buscarHojaRuta() {
    const tipo = $("#filtroTipo").val();
    const gestion = $("#filtroGestion").val();
    const codigo = $("#filtroCodigo").val().trim();

    if (!tipo || !gestion || !codigo) {
        Swal.fire("Atención", "Complete todos los filtros de búsqueda", "warning");
        return;
    }

    $.ajax({
        type: "POST",
        url: "/administracion/hoja-ruta/buscar",
        data: { tipo, gestion, codigo },
        success: function (response) {
            if (response.ok) {
                hojaRutaActual = response.hojaRuta;
                mostrarResultados(response);
            } else {
                Swal.fire("No encontrado", response.msg, "info");
                $("#contenedorResultados").hide();
            }
        },
        error: () => Swal.fire("Error", "Ocurrió un error al buscar", "error")
    });
}

function mostrarResultados(data) {
    const hr = data.hojaRuta;
    let htmlDatos = `
          <div class="row g-2">
            <div class="col-md-12">
              <p style="margin-bottom: 0.5rem;"><strong class="text-primary-color">Código:</strong> ${hr.codigo}</p>
              <p style="margin-bottom: 0.5rem;"><strong class="text-primary-color">Tipo:</strong> <span class="badge bg-label-info">${hr.tipo}</span></p>
              <p style="margin-bottom: 0.5rem;"><strong class="text-primary-color">Gestión:</strong> ${hr.gestion}</p>
            </div>
            <div class="col-md-12"><hr style="border-color: var(--border-color); margin: 0.5rem 0;"></div>
            <div class="col-md-12">
              <p style="margin-bottom: 0.5rem;"><strong>Solicitante:</strong> ${hr.solicitanteNombre}</p>
              <p style="margin-bottom: 0.5rem;"><strong>Cargo:</strong> ${hr.solicitanteCargo}</p>
            </div>
            <div class="col-md-12"><hr style="border-color: var(--border-color); margin: 0.5rem 0;"></div>
            <div class="col-md-12">
              <p style="margin-bottom: 0.5rem;"><strong>Descripción:</strong></p>
              <p class="text-muted">${hr.descripcion}</p>
            </div>
        `;

    if (hr.certificacion) htmlDatos += `<div class="col-md-12"><p style="margin-bottom: 0.5rem;"><strong>N° Certificación:</strong> ${hr.certificacion}</p></div>`;
    if (hr.monto) htmlDatos += `<div class="col-md-12"><p style="margin-bottom: 0.5rem;"><strong>Monto:</strong> Bs. ${parseFloat(hr.monto).toFixed(2)}</p></div>`;

    htmlDatos += `</div>`;
    $("#datosHojaRuta").html(htmlDatos);
    $("#movimientoActualTexto").text(data.movimientoActual);

    let htmlMovimientos = "";
    if (data.movimientos.length === 0) {
        htmlMovimientos = '<tr><td colspan="6" class="text-center text-muted">No hay movimientos registrados</td></tr>';
    } else {
        data.movimientos.forEach((mov) => {
            const estadoBadge = getEstadoBadge(mov.estado);
            const fecha = mov.fecha ? formatearFecha(mov.fecha) : "Sin fecha";
            const hora = mov.hora || "Sin hora";

            htmlMovimientos += `
              <tr>
                <td>${estadoBadge}</td>
                <td>${fecha}</td>
                <td>${hora}</td>
                <td><small>${mov.origen}</small></td>
                <td><small>${mov.destino}</small></td>
                <td>
                  <button class="btn btn-sm btn-icon btn-outline-primary" onclick="editarMovimiento(${mov.idMovimiento})" title="Editar">
                    <i class='bx bx-edit'></i>
                  </button>
                </td>
              </tr>
            `;
        });
    }
    $("#tablaMovimientos").html(htmlMovimientos);
    $("#contenedorResultados").fadeIn();
}

function getEstadoBadge(estado) {
    const badges = {
        RECIBIDO: '<span class="badge bg-label-success"><i class="bx bx-check-circle"></i> RECIBIDO</span>',
        ENVIADO: '<span class="badge bg-label-warning"><i class="bx bx-send"></i> ENVIADO</span>',
        ARCHIVADO: '<span class="badge bg-label-secondary"><i class="bx bx-archive"></i> ARCHIVADO</span>',
    };
    return badges[estado] || '<span class="badge bg-label-secondary">' + estado + "</span>";
}

function formatearFecha(fecha) {
    const f = new Date(fecha + "T00:00:00");
    return f.toLocaleDateString("es-ES", { day: "2-digit", month: "2-digit", year: "numeric" });
}

function abrirModalNuevaHojaRuta() {
    $("#formHojaRuta")[0].reset();
    $("#idHojaRuta").val("");
    $("#tituloModalHojaRuta").text("Nueva Hoja de Ruta");

    // Mostrar campos y restaurar required
    $("#hrUnidadOrigen").closest(".col-md-6").show();
    $("#hrUnidadOrigen").attr("required", "required");

    $("#hrFecha").closest(".col-md-6").show();
    $("#hrFecha").attr("required", "required");

    $("#hrHora").closest(".col-md-6").show();
    $("#hrHora").attr("required", "required");

    $("#modalHojaRuta").addClass("show");
    setTimeout(() => inicializarSelect2EnModal('modalHojaRuta'), 200);
}


function editarHojaRuta() {
    if (!hojaRutaActual) return;
    $("#idHojaRuta").val(hojaRutaActual.idHojaRuta);
    $("#hrTipo").val(hojaRutaActual.tipo);
    $("#hrCodigo").val(hojaRutaActual.codigo);
    $("#hrGestion").val(hojaRutaActual.gestion);
    $("#hrDescripcion").val(hojaRutaActual.descripcion);
    $("#hrCertificacion").val(hojaRutaActual.certificacion || "");
    $("#hrMonto").val(hojaRutaActual.monto || "");

    // Ocultar campos y remover required
    $("#hrUnidadOrigen").closest(".col-md-6").hide();
    $("#hrUnidadOrigen").removeAttr("required");

    $("#hrFecha").closest(".col-md-6").hide();
    $("#hrFecha").removeAttr("required");

    $("#hrHora").closest(".col-md-6").hide();
    $("#hrHora").removeAttr("required");

    $("#tituloModalHojaRuta").text("Modificar Hoja de Ruta");
    $("#modalHojaRuta").addClass("show");
    setTimeout(() => {
        inicializarSelect2EnModal('modalHojaRuta');
        $("#hrSolicitante").val(hojaRutaActual.solicitanteId).trigger("change");
    }, 200);
}


$("#formHojaRuta").on("submit", function (e) {
    e.preventDefault();
    if (!this.checkValidity()) {
        $(this).addClass("was-validated");
        return;
    }
    const idHojaRuta = $("#idHojaRuta").val();
    const url = idHojaRuta ? "/administracion/hoja-ruta/modificar" : "/administracion/hoja-ruta/registrar";

    $.ajax({
        type: "POST",
        url: url,
        data: $(this).serialize(),
        success: function (response) {
            if (response.ok) {
                Swal.fire("Éxito", response.msg, "success");
                cerrarModal("modalHojaRuta");
                if (idHojaRuta) {
                    buscarHojaRuta();
                } else {
                    $("#formHojaRuta")[0].reset();
                }
            } else {
                Swal.fire("Error", response.msg, "error");
            }
        },
        error: () => Swal.fire("Error", "Ocurrió un error al guardar", "error")
    });
});

function abrirModalNuevoMovimiento() {
    if (!hojaRutaActual) {
        Swal.fire("Atención", "Primero busque una hoja de ruta", "warning");
        return;
    }
    $("#formMovimiento")[0].reset();
    $("#idMovimiento").val("");
    $("#movHojaRutaId").val(hojaRutaActual.idHojaRuta);
    const hoy = new Date();
    $("#movFecha").val(hoy.toISOString().split("T")[0]);
    $("#movHora").val(hoy.toTimeString().substring(0, 5));
    $("#tituloModalMovimiento").text("Nuevo Movimiento");
    $("#modalMovimiento").addClass("show");
    setTimeout(() => inicializarSelect2EnModal('modalMovimiento'), 200);
}

function editarMovimiento(idMovimiento) {
    $.ajax({
        type: "GET",
        url: "/administracion/hoja-ruta/movimiento/" + idMovimiento,
        success: function (response) {
            if (response.ok) {
                const mov = response.movimiento;
                $("#idMovimiento").val(mov.idMovimiento);
                $("#movHojaRutaId").val(hojaRutaActual.idHojaRuta);
                $("#movEstado").val(mov.estadoNumero);
                $("#movFecha").val(mov.fecha);
                $("#movHora").val(mov.hora);
                $("#movObservacion").val(mov.observacion || "");
                $("#tituloModalMovimiento").text("Modificar Movimiento");
                $("#modalMovimiento").addClass("show");
                setTimeout(() => {
                    inicializarSelect2EnModal('modalMovimiento');
                    $("#movUnidadOrigen").val(mov.unidadOrigenId).trigger("change");
                    $("#movUnidadDestino").val(mov.unidadDestinoId).trigger("change");
                }, 200);
            } else {
                Swal.fire("Error", response.msg, "error");
            }
        },
        error: () => Swal.fire("Error", "Ocurrió un error al cargar el movimiento", "error")
    });
}

$("#formMovimiento").on("submit", function (e) {
    e.preventDefault();
    if (!this.checkValidity()) {
        $(this).addClass("was-validated");
        return;
    }
    $.ajax({
        type: "POST",
        url: "/administracion/hoja-ruta/movimiento/guardar",
        data: $(this).serialize(),
        success: function (response) {
            if (response.ok) {
                Swal.fire("Éxito", response.msg, "success");
                cerrarModal("modalMovimiento");
                buscarHojaRuta();
            } else {
                Swal.fire("Error", response.msg, "error");
            }
        },
        error: () => Swal.fire("Error", "Ocurrió un error al guardar", "error")
    });
});

// Abrir modal de nuevo solicitante
function abrirModalNuevoSolicitante() {
    $("#formNuevoSolicitante")[0].reset();
    $("#modalNuevoSolicitante").addClass("show");
}

// Guardar nuevo solicitante
$("#formNuevoSolicitante").on("submit", function (e) {
    e.preventDefault();

    if (!this.checkValidity()) {
        $(this).addClass("was-validated");
        return;
    }

    $.ajax({
        type: "POST",
        url: "/administracion/hoja-ruta/solicitante/registrar",
        data: $(this).serialize(),
        success: function (response) {
            if (response.ok) {
                cerrarModal("modalNuevoSolicitante");
                Swal.fire({
                    title: "Éxito",
                    text: response.msg,
                    icon: "success",
                    timer: 1500,
                    showConfirmButton: false,
                    customClass: {
                        container: 'swal-on-top'
                    }
                });

                // Recargar lista de solicitantes y seleccionar el nuevo
                recargarSolicitantes(response.idSolicitante);
            } else {
                cerrarModal("modalNuevoSolicitante");
                Swal.fire({
                    title: "Error",
                    text: response.msg,
                    icon: "error",
                    customClass: {
                        container: 'swal-on-top'
                    }
                });
            }
        },
        error: function (xhr) {
            // Cerrar modal antes de mostrar error
            cerrarModal("modalNuevoSolicitante");

            let mensajeError = "Ocurrió un error al guardar el solicitante";

            // Intentar extraer mensaje del servidor
            if (xhr.responseJSON && xhr.responseJSON.msg) {
                mensajeError = xhr.responseJSON.msg;
            }

            Swal.fire({
                title: "Error",
                text: mensajeError,
                icon: "error",
                customClass: {
                    container: 'swal-on-top'
                }
            });
        }
    });
});

// Recargar select de solicitantes
function recargarSolicitantes(idNuevo) {
    $.ajax({
        type: "GET",
        url: "/administracion/hoja-ruta/solicitante/listar",
        success: function (response) {
            if (response.ok) {
                // Limpiar select
                $("#hrSolicitante").empty();
                $("#hrSolicitante").append('<option value="">Seleccionar</option>');

                // Agregar opciones
                response.solicitantes.forEach(function (sol) {
                    $("#hrSolicitante").append(
                        `<option value="${sol.idSolicitante}">${sol.nombre} - ${sol.cargo}</option>`
                    );
                });

                // Seleccionar el nuevo solicitante
                if (idNuevo) {
                    $("#hrSolicitante").val(idNuevo).trigger("change");
                }
            }
        },
        error: () => console.error("Error al recargar solicitantes")
    });
}


function cerrarModal(modalId) {
    $("#" + modalId).removeClass("show");
    $('#' + modalId + ' .select2').each(function () {
        if ($(this).hasClass("select2-hidden-accessible")) {
            $(this).select2('destroy');
        }
    });
}

// ================================================================
// TABLA HOJAS DE RUTA — Script completo
// ================================================================
let hrDatosOriginales = [];   // todos los datos del servidor
let hrDatosFiltrados = [];   // después de búsqueda libre
let hrPaginaActual = 1;
const HR_POR_PAGINA = 12;
let hrOrdenCol = 'gestion';
let hrOrdenAsc = false;   // desc por defecto
let hrFilaSeleccionada = null;

// ----- INIT SELECT2 GESTIÓN EN TOOLBAR -----
(function initToolbarSelect2() {
    const sel = document.getElementById('hrFiltroGestion');
    const anioActual = new Date().getFullYear();
    for (let a = anioActual; a >= 2020; a--) {
        const opt = document.createElement('option');
        opt.value = a;
        opt.textContent = a;
        if (a === anioActual) opt.selected = true;
        sel.appendChild(opt);
    }
})();

$(document).ready(function () {
    // Select2 para el filtro de gestión en toolbar
    $('#hrFiltroGestion').select2({
        width: '130px',
        minimumResultsForSearch: 0,   // siempre muestra búsqueda
        language: {
            noResults: () => "Sin resultados",
            searching: () => "Buscando…"
        },
        placeholder: 'Todas las gestiones',
        allowClear: true
    });

    // Búsqueda libre con debounce
    let debounceTimer;
    $('#hrBusquedaLibre').on('input', function () {
        clearTimeout(debounceTimer);
        debounceTimer = setTimeout(() => {
            hrPaginaActual = 1;
            aplicarBusquedaLibre();
        }, 220);
    });

    // Carga inicial automática
    cargarTablaHojaRutas();
});

// ----- CARGA DESDE SERVIDOR -----
function cargarTablaHojaRutas() {
    const gestion = $('#hrFiltroGestion').val() || '';

    // Estado cargando
    $('#cuerpoTablaHR').html(`
        <tr><td colspan="8">
            <div class="hr-empty-state">
                <span class="hr-spinner"></span>
                <p style="display:inline;">Cargando registros…</p>
            </div>
        </td></tr>
    `);
    $('#hrTableFooter').hide();
    $('#hr-badge-total').text('—');

    $.ajax({
        type: 'POST',
        url: '/administracion/hoja-ruta/listar',
        data: { gestion },
        success: function (response) {
            if (response.ok) {
                hrDatosOriginales = response.hojaRutas || [];
                hrPaginaActual = 1;
                $('#hrBusquedaLibre').val('');
                aplicarBusquedaLibre();
            } else {
                mostrarEstadoVacioHR('bx-error-circle', response.msg || 'Error al obtener datos');
            }
        },
        error: () => mostrarEstadoVacioHR('bx-wifi-off', 'No se pudo conectar al servidor')
    });
}

// ----- FILTRO LOCAL (búsqueda libre) -----
function aplicarBusquedaLibre() {
    const q = ($('#hrBusquedaLibre').val() || '').toLowerCase().trim();

    if (!q) {
        hrDatosFiltrados = [...hrDatosOriginales];
    } else {
        hrDatosFiltrados = hrDatosOriginales.filter(hr => {
            return (
                (hr.codigo || '').toLowerCase().includes(q) ||
                (hr.solicitanteNombre || '').toLowerCase().includes(q) ||
                (hr.solicitanteCargo || '').toLowerCase().includes(q) ||
                (hr.descripcion || '').toLowerCase().includes(q) ||
                (hr.tipo || '').toLowerCase().includes(q) ||
                String(hr.gestion || '').includes(q)
            );
        });
    }

    aplicarOrden();
    renderizarTablaHR();
}

// ----- ORDENAMIENTO -----
function ordenarTablaHR(col) {
    if (hrOrdenCol === col) {
        hrOrdenAsc = !hrOrdenAsc;
    } else {
        hrOrdenCol = col;
        hrOrdenAsc = true;
    }

    // Actualizar iconos de cabecera
    document.querySelectorAll('#tablaListadoHR thead th').forEach(th => {
        th.classList.remove('sorted');
        const icon = th.querySelector('.sort-icon');
        if (icon) { icon.className = 'bx bx-sort sort-icon'; }
    });
    const thActivo = document.querySelector(`#tablaListadoHR thead th[data-col="${col}"]`);
    if (thActivo) {
        thActivo.classList.add('sorted');
        const icon = thActivo.querySelector('.sort-icon');
        if (icon) icon.className = `bx ${hrOrdenAsc ? 'bx-sort-up' : 'bx-sort-down'} sort-icon`;
    }

    hrPaginaActual = 1;
    aplicarOrden();
    renderizarTablaHR();
}

function aplicarOrden() {
    hrDatosFiltrados.sort((a, b) => {
        let va = a[hrOrdenCol] ?? '';
        let vb = b[hrOrdenCol] ?? '';
        if (typeof va === 'string') va = va.toLowerCase();
        if (typeof vb === 'string') vb = vb.toLowerCase();
        if (va < vb) return hrOrdenAsc ? -1 : 1;
        if (va > vb) return hrOrdenAsc ? 1 : -1;
        return 0;
    });
}

// ----- RENDER -----
function renderizarTablaHR() {
    const total = hrDatosFiltrados.length;
    const inicio = (hrPaginaActual - 1) * HR_POR_PAGINA;
    const fin = Math.min(inicio + HR_POR_PAGINA, total);

    $('#hr-badge-total').text(total);

    if (total === 0) {
        const busqueda = $('#hrBusquedaLibre').val().trim();
        mostrarEstadoVacioHR(
            'bx-search-alt',
            busqueda ? `Sin resultados para "<strong>${busqueda}</strong>"` : 'No hay registros con los filtros aplicados'
        );
        return;
    }

    const paginados = hrDatosFiltrados.slice(inicio, fin);
    let html = '';

    paginados.forEach((hr, idx) => {
        const numFila = inicio + idx + 1;
        html += `
                <tr class="animated" style="animation-delay:${idx * 0.025}s"
                    onclick="seleccionarDesdeTabla(this, '${escaparJS(hr.tipo)}', '${escaparJS(hr.codigo)}', ${hr.gestion})"
                    title="Ver detalle de ${escaparAttr(hr.codigo)}">
                    <td style="text-align:center; color:var(--text-muted,#9ca3af); font-size:0.72rem;">${numFila}</td>
                    <td><span class="hr-codigo">${hr.codigo || '-'}</span></td>
                    <td>${renderTipoChip(hr.tipo)}</td>
                    <td style="font-weight:700; font-size:0.8rem;">${hr.gestion || '-'}</td>
                    <td>
                        <span class="hr-solicitante-nombre">${hr.solicitanteNombre || '-'}</span>
                        <span class="hr-solicitante-cargo">${hr.solicitanteCargo || ''}</span>
                    </td>
                    <td>
                        <span class="hr-descripcion" title="${escaparAttr(hr.descripcion)}">${hr.descripcion || '-'}</span>
                    </td>
                    <td style="text-align:center;">
                        <button class="btn-ver-hr"
                            onclick="event.stopPropagation(); seleccionarDesdeTabla(this.closest('tr'), '${escaparJS(hr.tipo)}', '${escaparJS(hr.codigo)}', ${hr.gestion})">
                            <i class='bx bx-show'></i> Ver
                        </button>
                    </td>
                </tr>`;
    });

    $('#cuerpoTablaHR').html(html);

    // Footer
    $('#hrInfoPaginacion').text(`${inicio + 1}–${fin} de ${total} registros`);
    renderPaginacionHR(total);
    $('#hrTableFooter').show();
}

// ----- PAGINACIÓN -----
function renderPaginacionHR(total) {
    const totalPags = Math.ceil(total / HR_POR_PAGINA);
    let html = '';

    const btnPrev = `<button ${hrPaginaActual === 1 ? 'disabled' : ''} onclick="cambiarPaginaHR(${hrPaginaActual - 1})">
        <i class='bx bx-chevron-left'></i>
    </button>`;

    let inicio = Math.max(1, hrPaginaActual - 2);
    let fin = Math.min(totalPags, hrPaginaActual + 2);
    if (fin - inicio < 4) {
        inicio = Math.max(1, fin - 4);
        fin = Math.min(totalPags, inicio + 4);
    }

    let numeroPags = '';
    if (inicio > 1) numeroPags += `<button onclick="cambiarPaginaHR(1)">1</button>${inicio > 2 ? '<button disabled>…</button>' : ''}`;
    for (let p = inicio; p <= fin; p++) {
        numeroPags += `<button class="${p === hrPaginaActual ? 'active' : ''}" onclick="cambiarPaginaHR(${p})">${p}</button>`;
    }
    if (fin < totalPags) {
        numeroPags += `${fin < totalPags - 1 ? '<button disabled>…</button>' : ''}<button onclick="cambiarPaginaHR(${totalPags})">${totalPags}</button>`;
    }

    const btnNext = `<button ${hrPaginaActual === totalPags ? 'disabled' : ''} onclick="cambiarPaginaHR(${hrPaginaActual + 1})">
        <i class='bx bx-chevron-right'></i>
    </button>`;

    $('#hrControlesPaginacion').html(btnPrev + numeroPags + btnNext);
}

function cambiarPaginaHR(p) {
    hrPaginaActual = p;
    renderizarTablaHR();
    // Scroll suave al inicio de la tabla
    document.querySelector('.hr-table-wrapper').scrollIntoView({ behavior: 'smooth', block: 'start' });
}

// ----- SELECCIONAR DESDE TABLA -----
function seleccionarDesdeTabla(fila, tipo, codigo, gestion) {
    // Resaltar fila
    if (hrFilaSeleccionada) hrFilaSeleccionada.classList.remove('selected-row');
    fila.classList.add('selected-row');
    hrFilaSeleccionada = fila;

    // Rellenar filtros del buscador principal
    $('#filtroTipo').val(tipo).trigger('change');
    $('#filtroGestion').val(gestion).trigger('change');

    setTimeout(() => {
        $('#filtroCodigo').val(codigo);
        buscarHojaRuta();
        const target = document.getElementById('contenedorResultados');
        if (target) {
            target.scrollIntoView({ behavior: 'smooth', block: 'start' });
        }
    }, 120);
}

// ----- LIMPIAR -----
function limpiarFiltrosTabla() {
    $('#hrFiltroGestion').val(new Date().getFullYear()).trigger('change');
    $('#hrBusquedaLibre').val('');
    hrPaginaActual = 1;
    cargarTablaHojaRutas();
}

// ----- ESTADO VACÍO -----
function mostrarEstadoVacioHR(icon, msg) {
    $('#cuerpoTablaHR').html(`
        <tr><td colspan="8">
            <div class="hr-empty-state">
                <i class='bx ${icon}'></i>
                <p>${msg}</p>
            </div>
        </td></tr>
    `);
    $('#hrTableFooter').hide();
    $('#hr-badge-total').text('0');
}

// ----- HELPERS RENDER -----
function renderEstadoPill(estado) {
    const map = {
        'RECIBIDO': ['recibido', 'bx-check-circle', 'RECIBIDO'],
        'ENVIADO': ['enviado', 'bx-send', 'ENVIADO'],
        'ARCHIVADO': ['archivado', 'bx-archive', 'ARCHIVADO'],
        'SIN MOVIMIENTO': ['sin-mov', 'bx-minus-circle', 'SIN MOV.'],
    };
    const [cls, ico, lbl] = map[estado] || ['sin-mov', 'bx-question-mark', estado || '—'];
    return `<span class="estado-pill ${cls}"><i class='bx ${ico}'></i>${lbl}</span>`;
}

function renderTipoChip(tipo) {
    const map = {
        'RECTORADO': 'rectorado',
        'DAF': 'daf',
        'PEDIDO': 'pedido',
    };
    const cls = map[tipo] || '';
    return `<span class="tipo-chip ${cls}">${tipo || '—'}</span>`;
}

// Escapar para usar dentro de atributo onclick="..."
function escaparJS(str) {
    return (str || '').replace(/'/g, "\\'").replace(/"/g, '&quot;');
}

function escaparAttr(str) {
    return (str || '').replace(/"/g, '&quot;').replace(/</g, '&lt;');
}

$(document).ready(function () {
    $("#filtroTipo, #filtroGestion").on("change", validarFiltros);
    const hoy = new Date();
    $("#hrFecha, #movFecha").val(hoy.toISOString().split("T")[0]);
    $("#hrHora, #movHora").val(hoy.toTimeString().substring(0, 5));
    $("#hrGestion").val(hoy.getFullYear());
    cargarTablaHojaRutas()
});

document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape') {
        document.querySelectorAll('.modal.show').forEach(modal => {
            cerrarModal(modal.id);
        });
    }
});