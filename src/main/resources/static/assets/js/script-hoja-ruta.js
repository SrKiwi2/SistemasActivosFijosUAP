let hojaRutaActual = null;

// Texto seguro para meter en HTML: los datos (descripción, solicitante, unidades) llegan crudos.
function esc(s) {
    return s == null ? '' : String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;')
        .replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&#39;');
}

// Fecha y hora LOCALES para los campos (toISOString es UTC: en Bolivia, desde las 20:00
// daba la fecha de mañana).
function fechaLocal(d) {
    const z = n => String(n).padStart(2, '0');
    return d.getFullYear() + '-' + z(d.getMonth() + 1) + '-' + z(d.getDate());
}
function horaLocal(d) {
    return String(d.getHours()).padStart(2, '0') + ':' + String(d.getMinutes()).padStart(2, '0');
}
// Theme
const savedTheme = localStorage.getItem('theme') || 'light';
document.documentElement.setAttribute('data-theme', savedTheme);
document.getElementById('themeIcon').className = savedTheme === 'dark' ? 'ti ti-sun' : 'ti ti-moon';

document.getElementById('themeToggle').addEventListener('click', () => {
    const current = document.documentElement.getAttribute('data-theme');
    const newTheme = current === 'dark' ? 'light' : 'dark';
    document.documentElement.setAttribute('data-theme', newTheme);
    localStorage.setItem('theme', newTheme);
    document.getElementById('themeIcon').className = newTheme === 'dark' ? 'ti ti-sun' : 'ti ti-moon';
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

// Abre la hoja exacta de la fila elegida. La búsqueda libre carga coincidencias en el listado.
function buscarHojaRuta(idHojaRuta) {
    if (!idHojaRuta) return;

    $.ajax({
        type: "POST",
        url: "/administracion/hoja-ruta/buscar",
        data: { idHojaRuta },
        success: function (response) {
            if (response.ok) {
                hojaRutaActual = response.hojaRuta;
                mostrarResultados(response);
            } else {
                Swal.fire("No encontrado", response.msg, "info");
                cerrarDetalleHojaRuta();
            }
        },
        error: () => Swal.fire("Error", "Ocurrió un error al buscar", "error")
    });
}

function mostrarResultados(data) {
    const hr = data.hojaRuta;
    let htmlDatos = `<div class="hr-resumen-grid">
        <div><span>Número</span><strong>${esc(hr.codigo)}</strong></div>
        <div><span>Tipo · gestión</span><strong>${esc(hr.tipo)} · ${esc(hr.gestion)}</strong></div>
        <div><span>Solicitante</span><strong>${esc(hr.solicitanteNombre || '—')}</strong></div>
        <div><span>Cargo</span><strong>${esc(hr.solicitanteCargo || '—')}</strong></div>
        <div class="hr-resumen-ancho"><span>Descripción</span><strong>${esc(hr.descripcion || '—')}</strong></div>`;
    if (hr.certificacion) htmlDatos += `<div><span>Certificación</span><strong>${esc(hr.certificacion)}</strong></div>`;
    if (hr.monto != null) htmlDatos += `<div><span>Monto</span><strong>Bs. ${Number(hr.monto).toFixed(2)}</strong></div>`;
    htmlDatos += '</div>';
    $("#datosHojaRuta").html(htmlDatos);
    $("#movimientoActualTexto").text(data.movimientoActual);

    let htmlMovimientos = "";
    if (data.movimientos.length === 0) {
        htmlMovimientos = '<tr><td colspan="4" class="text-center text-muted">No hay movimientos registrados</td></tr>';
    } else {
        data.movimientos.forEach((mov) => {
            const estadoBadge = getEstadoBadge(mov.estado);
            const fecha = mov.fecha ? formatearFecha(mov.fecha) : "Sin fecha";
            const hora = mov.hora ? esc(mov.hora) : "Sin hora";

            htmlMovimientos += `
              <tr>
                <td>${estadoBadge}</td>
                <td>${fecha}<br><small>${hora}</small></td>
                <td class="hr-trayecto"><span>${esc(mov.origen)}</span><i class="ti ti-arrow-right"></i><strong>${esc(mov.destino)}</strong></td>
                <td>
                  <button class="btn btn-sm btn-icon btn-outline-primary" onclick="editarMovimiento(${Number(mov.idMovimiento)})" title="Editar">
                    <i class='ti ti-edit'></i>
                  </button>
                </td>
              </tr>
            `;
        });
    }
    $("#tablaMovimientos").html(htmlMovimientos);
    $('.main-container').addClass('hr-tiene-detalle');
    $("#contenedorResultados").show();
    requestAnimationFrame(renderizarTablaHR);
}

function cerrarDetalleHojaRuta() {
    $('#contenedorResultados').hide();
    $('.main-container').removeClass('hr-tiene-detalle');
    hojaRutaActual = null;
    hrIdSeleccionada = null;
    hrFilaSeleccionada = null;
    renderizarTablaHR();
}

function getEstadoBadge(estado) {
    const badges = {
        RECIBIDO: '<span class="badge bg-label-success"><i class="ti ti-circle-check"></i> RECIBIDO</span>',
        ENVIADO: '<span class="badge bg-label-warning"><i class="ti ti-send"></i> ENVIADO</span>',
        ARCHIVADO: '<span class="badge bg-label-secondary"><i class="ti ti-archive"></i> ARCHIVADO</span>',
    };
    return badges[estado] || '<span class="badge bg-label-secondary">' + esc(estado) + "</span>";
}

function formatearFecha(fecha) {
    const f = new Date(fecha + "T00:00:00");
    return f.toLocaleDateString("es-ES", { day: "2-digit", month: "2-digit", year: "numeric" });
}

function abrirModalNuevaHojaRuta() {
    $("#formHojaRuta")[0].reset();
    $("#formHojaRuta").removeClass('was-validated');
    $("#idHojaRuta").val("");
    $("#tituloModalHojaRuta").text("Nueva Hoja de Ruta");
    // reset() deja los campos como en el HTML (vacíos): se vuelven a poner los de hoy.
    const ahora = new Date();
    $("#hrFecha").val(fechaLocal(ahora));
    $("#hrHora").val(horaLocal(ahora));
    $("#hrGestion").val(ahora.getFullYear());

    // Mostrar campos y restaurar required
    $("#hrUnidadOrigen").closest(".hr-unidad-campo").show();
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
    $("#hrUnidadOrigen").closest(".hr-unidad-campo").hide();
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


// Mientras se guarda, el botón queda bloqueado: un doble clic o Enter dos veces mandaba dos
// pedidos y registraba la hoja (o el movimiento, o el solicitante) dos veces.
function bloquearEnvio(form) {
    const $btn = $(form).find('[type="submit"]');
    if ($btn.prop('disabled')) return null;
    $btn.prop('disabled', true);
    return () => $btn.prop('disabled', false);
}

$("#formHojaRuta").on("submit", function (e) {
    e.preventDefault();
    if (!this.checkValidity()) {
        $(this).addClass("was-validated");
        return;
    }
    const liberar = bloquearEnvio(this);
    if (!liberar) return;
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
                    buscarHojaRuta(idHojaRuta);
                } else {
                    $("#formHojaRuta")[0].reset();
                }
                cargarTablaHojaRutas({ inicial: !$("#filtroCodigo").val() && !$("#filtroTipo").val() && !$("#filtroGestion").val() });
            } else {
                Swal.fire("Error", response.msg, "error");
            }
        },
        error: (xhr) => Swal.fire("Error", (xhr.responseJSON && xhr.responseJSON.msg) || "Ocurrió un error al guardar", "error"),
        complete: liberar
    });
});

function abrirModalNuevoMovimiento() {
    if (!hojaRutaActual) {
        Swal.fire("Atención", "Primero busque una hoja de ruta", "warning");
        return;
    }
    $("#formMovimiento")[0].reset();
    $("#formMovimiento").removeClass('was-validated');
    $("#idMovimiento").val("");
    $("#movHojaRutaId").val(hojaRutaActual.idHojaRuta);
    const hoy = new Date();
    $("#movFecha").val(fechaLocal(hoy));
    $("#movHora").val(horaLocal(hoy));
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
    const liberar = bloquearEnvio(this);
    if (!liberar) return;
    $.ajax({
        type: "POST",
        url: "/administracion/hoja-ruta/movimiento/guardar",
        data: $(this).serialize(),
        success: function (response) {
            if (response.ok) {
                Swal.fire("Éxito", response.msg, "success");
                cerrarModal("modalMovimiento");
                buscarHojaRuta(hojaRutaActual && hojaRutaActual.idHojaRuta);
                cargarTablaHojaRutas({ inicial: !$("#filtroCodigo").val() && !$("#filtroTipo").val() && !$("#filtroGestion").val() });
            } else {
                Swal.fire("Error", response.msg, "error");
            }
        },
        error: (xhr) => Swal.fire("Error", (xhr.responseJSON && xhr.responseJSON.msg) || "Ocurrió un error al guardar", "error"),
        complete: liberar
    });
});

// Abrir modal de nuevo solicitante
function abrirModalNuevoSolicitante() {
    $("#formNuevoSolicitante")[0].reset();
    $("#formNuevoSolicitante").removeClass('was-validated');
    $("#modalNuevoSolicitante").addClass("show");
}

// Alta rápida de unidades desde origen o destino, sin perder el formulario abierto.
let unidadSelectPendiente = null;
function mostrarErrorUnidad(mensaje) {
    $('#unidadError').text(mensaje).prop('hidden', false);
    $('#unidadNombre').trigger('focus');
}

$(document).on('click', '.hr-btn-unidad', function () {
    unidadSelectPendiente = this.dataset.select;
    $('#formNuevaUnidad')[0].reset();
    $('#formNuevaUnidad').removeClass('was-validated');
    $('#unidadError').text('').prop('hidden', true);
    const destino = unidadSelectPendiente === 'movUnidadDestino';
    $('#tituloModalNuevaUnidad').text('Nueva unidad de ' + (destino ? 'destino' : 'origen'));
    $('#modalNuevaUnidad').addClass('show');
    $('#unidadNombre').trigger('focus');
});

$('#formNuevaUnidad').on('submit', function (e) {
    e.preventDefault();
    if (!this.checkValidity()) { $(this).addClass('was-validated'); return; }
    const nombre = $('#unidadNombre').val().trim();
    if (!nombre) { mostrarErrorUnidad('Escriba el nombre de la unidad.'); return; }
    $('#unidadError').text('').prop('hidden', true);
    const liberar = bloquearEnvio(this);
    if (!liberar) return;
    $.ajax({
        type: 'POST', url: '/administracion/hoja-ruta/unidad/registrar',
        data: { nombre },
        success: function (response) {
            if (!response || !response.ok || !response.idUnidad) {
                mostrarErrorUnidad(response && response.msg || 'No se pudo registrar la unidad. Inténtelo de nuevo.');
                return;
            }
            ['hrUnidadOrigen', 'movUnidadOrigen', 'movUnidadDestino'].forEach(id => {
                const $select = $('#' + id);
                if (!$select.find('option[value="' + response.idUnidad + '"]').length) {
                    $select.append(new Option(response.nombre, response.idUnidad));
                }
            });
            const selectDestino = unidadSelectPendiente;
            if (selectDestino) $('#' + selectDestino).val(response.idUnidad).trigger('change');
            cerrarModal('modalNuevaUnidad');
            if (selectDestino) $('#' + selectDestino).next('.select2').find('.select2-selection').trigger('focus');
            Swal.fire({ title: 'Unidad lista', text: response.msg, icon: 'success', timer: 1600,
                showConfirmButton: false, customClass: { container: 'swal-on-top' } });
        },
        error: xhr => mostrarErrorUnidad((xhr.responseJSON && (xhr.responseJSON.msg || xhr.responseJSON.mensaje)) ||
            (xhr.status === 403 ? 'No tiene permiso para registrar unidades.' :
                'No se pudo registrar la unidad. Inténtelo de nuevo.')),
        complete: liberar
    });
});

// Guardar nuevo solicitante
$("#formNuevoSolicitante").on("submit", function (e) {
    e.preventDefault();

    if (!this.checkValidity()) {
        $(this).addClass("was-validated");
        return;
    }
    const liberar = bloquearEnvio(this);
    if (!liberar) return;

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
        },
        complete: liberar
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
                        $('<option>').val(sol.idSolicitante).text(sol.nombre + ' - ' + sol.cargo)
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
let hrPorPagina = 8;
let hrOrdenCol = 'gestion';
let hrOrdenAsc = false;   // desc por defecto
let hrFilaSeleccionada = null;
let hrIdSeleccionada = null;

// La pantalla arranca con la gestión actual; al escribir un número se busca en todas.
let hrPedidoListado = null;
let hrDebounceBusqueda = null;
$(document).ready(function () {
    $('#filtroCodigo').on('input', function () {
        clearTimeout(hrDebounceBusqueda);
        hrDebounceBusqueda = setTimeout(() => cargarTablaHojaRutas({ inicial: !this.value.trim() && !$('#filtroTipo').val() && !$('#filtroGestion').val() }), 380);
    }).on('keydown', function (e) {
        if (e.key === 'Enter') { e.preventDefault(); clearTimeout(hrDebounceBusqueda); cargarTablaHojaRutas(); }
    });
    $('#filtroTipo, #filtroGestion').on('change', function () { cargarTablaHojaRutas(); });
    cargarTablaHojaRutas({ inicial: true });
});

// Coincidencias del servidor: número parcial, tipo y gestión independientes.
function cargarTablaHojaRutas(opciones = {}) {
    const codigo = $('#filtroCodigo').val().trim();
    const tipo = $('#filtroTipo').val();
    const gestionTexto = $('#filtroGestion').val().trim();
    if (gestionTexto && (!$('#filtroGestion')[0].checkValidity() || !/^\d{4}$/.test(gestionTexto))) {
        $('#filtroGestion').trigger('focus');
        return;
    }
    const soloActual = !!opciones.inicial && !codigo && !tipo && !gestionTexto;
    const gestion = gestionTexto || (soloActual ? new Date().getFullYear() : '');
    if (hrPedidoListado) hrPedidoListado.abort();
    $('#cuerpoTablaHR').html('<tr><td colspan="7"><div class="hr-empty-state"><span class="hr-spinner"></span> Buscando hojas…</div></td></tr>');
    $('#hrTableFooter').hide();
    $('#hr-badge-total').text('—');
    $('#hrContexto').text('Buscando…');
    const pedido = $.ajax({
        type: 'POST', url: '/administracion/hoja-ruta/listar',
        data: { codigo, tipo, gestion },
        success: function (response) {
            if (!response.ok) { mostrarEstadoVacioHR('ti-alert-circle', esc(response.msg || 'Error al buscar')); return; }
            hrDatosOriginales = response.hojaRutas || [];
            hrDatosFiltrados = [...hrDatosOriginales];
            hrPaginaActual = 1;
            aplicarOrden();
            const contexto = soloActual ? 'Gestión actual. Escriba un número para buscar en todas las gestiones.'
                : codigo ? 'Coincidencias para “' + codigo + '”' + (tipo ? ' · ' + tipo : '') + (gestionTexto ? ' · ' + gestionTexto : '')
                : (tipo || gestionTexto ? 'Filtro: ' + [tipo, gestionTexto].filter(Boolean).join(' · ') : 'Todas las gestiones y tipos');
            $('#hrContexto').text(contexto);
            renderizarTablaHR();
        },
        error: function (xhr, estado) {
            if (estado !== 'abort') mostrarEstadoVacioHR('ti-wifi-off', 'No se pudo conectar al servidor');
        },
        complete: function () { if (hrPedidoListado === pedido) hrPedidoListado = null; }
    });
    hrPedidoListado = pedido;
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
        if (icon) { icon.className = 'ti ti-arrows-sort sort-icon'; }
    });
    const thActivo = document.querySelector(`#tablaListadoHR thead th[data-col="${col}"]`);
    if (thActivo) {
        thActivo.classList.add('sorted');
        const icon = thActivo.querySelector('.sort-icon');
        if (icon) icon.className = `ti ${hrOrdenAsc ? 'ti-sort-ascending' : 'ti-sort-descending'} sort-icon`;
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
    const altoTabla = document.querySelector('.hr-table-scroll')?.clientHeight || 440;
    hrPorPagina = Math.max(3, Math.min(20, Math.floor((altoTabla - 42) / 52)));
    const total = hrDatosFiltrados.length;
    hrPaginaActual = Math.max(1, Math.min(hrPaginaActual, Math.max(1, Math.ceil(total / hrPorPagina))));
    const inicio = (hrPaginaActual - 1) * hrPorPagina;
    const fin = Math.min(inicio + hrPorPagina, total);

    $('#hr-badge-total').text(total);

    if (total === 0) {
        const busqueda = $('#filtroCodigo').val().trim();
        mostrarEstadoVacioHR(
            'ti-search',
            busqueda ? `Sin resultados para "<strong>${esc(busqueda)}</strong>"` : 'No hay registros con los filtros aplicados'
        );
        return;
    }

    const paginados = hrDatosFiltrados.slice(inicio, fin);
    let html = '';

    paginados.forEach((hr, idx) => {
        const numFila = inicio + idx + 1;
        html += `
                <tr class="animated hr-fila ${String(hr.idHojaRuta) === String(hrIdSeleccionada) ? 'selected-row' : ''}" style="animation-delay:${idx * 0.025}s"
                    data-id="${Number(hr.idHojaRuta)}" data-tipo="${esc(hr.tipo)}" data-codigo="${esc(hr.codigo)}" data-gestion="${esc(hr.gestion)}"
                    title="Ver detalle de ${esc(hr.codigo)}" tabindex="0" role="button" aria-label="Ver hoja ${esc(hr.codigo)}">
                    <td class="hr-numero">${numFila}</td>
                    <td><span class="hr-codigo">${esc(hr.codigo || '-')}</span></td>
                    <td>${renderTipoChip(hr.tipo)}</td>
                    <td class="hr-gestion">${esc(hr.gestion || '-')}</td>
                    <td>
                        <span class="hr-solicitante-nombre">${esc(hr.solicitanteNombre || '-')}</span>
                        <span class="hr-solicitante-cargo">${esc(hr.solicitanteCargo || '')}</span>
                    </td>
                    <td>
                        <span class="hr-descripcion" title="${esc(hr.descripcion)}">${esc(hr.descripcion || '-')}</span>
                    </td>
                    <td style="text-align:center;">
                        <button type="button" class="btn-ver-hr">
                            <i class='ti ti-eye'></i> Ver
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
    const totalPags = Math.ceil(total / hrPorPagina);
    let html = '';

    const btnPrev = `<button ${hrPaginaActual === 1 ? 'disabled' : ''} onclick="cambiarPaginaHR(${hrPaginaActual - 1})">
        <i class='ti ti-chevron-left'></i>
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
        <i class='ti ti-chevron-right'></i>
    </button>`;

    $('#hrControlesPaginacion').html(btnPrev + numeroPags + btnNext);
}

function cambiarPaginaHR(p) {
    hrPaginaActual = p;
    renderizarTablaHR();
    document.querySelector('.hr-table-scroll').scrollTop = 0;
}

// ----- SELECCIONAR DESDE TABLA -----
// Clic en la fila o en «Ver»: los datos van en data-* de la fila.
$(document).on('click', '#cuerpoTablaHR tr.hr-fila', function () {
    seleccionarDesdeTabla(this, this.dataset.tipo, this.dataset.codigo, this.dataset.gestion, this.dataset.id);
});
$(document).on('keydown', '#cuerpoTablaHR tr.hr-fila', function (e) {
    if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); $(this).trigger('click'); }
});
function seleccionarDesdeTabla(fila, tipo, codigo, gestion, idHojaRuta) {
    // Resaltar fila
    if (hrFilaSeleccionada) hrFilaSeleccionada.classList.remove('selected-row');
    fila.classList.add('selected-row');
    hrFilaSeleccionada = fila;
    hrIdSeleccionada = idHojaRuta;

    buscarHojaRuta(idHojaRuta);   // por id: la fila exacta, aunque haya otra hoja con el mismo N°
}

// ----- LIMPIAR -----
function limpiarFiltrosTabla() {
    $('#filtroCodigo').val('');
    $('#filtroTipo').val('');
    $('#filtroGestion').val('');
    clearTimeout(hrDebounceBusqueda);
    cerrarDetalleHojaRuta();
    cargarTablaHojaRutas({ inicial: true });
    $('#filtroCodigo').trigger('focus');
}

// ----- ESTADO VACÍO -----
function mostrarEstadoVacioHR(icon, msg) {
    $('#cuerpoTablaHR').html(`
        <tr><td colspan="7">
            <div class="hr-empty-state">
                <i class='ti ${icon}'></i>
                <p>${msg}</p>
            </div>
        </td></tr>
    `);
    $('#hrTableFooter').hide();
    $('#hr-badge-total').text('0');
}

// ----- HELPERS RENDER -----
function renderTipoChip(tipo) {
    const map = {
        'RECTORADO': 'rectorado',
        'DAF': 'daf',
        'PEDIDO': 'pedido',
    };
    const cls = map[tipo] || '';
    return `<span class="tipo-chip ${cls}">${esc(tipo || '—')}</span>`;
}

$(document).ready(function () {
    const hoy = new Date();
    $("#hrFecha, #movFecha").val(fechaLocal(hoy));
    $("#hrHora, #movHora").val(horaLocal(hoy));
    $("#hrGestion").val(hoy.getFullYear());
});

window.addEventListener('resize', () => {
    if (hrDatosFiltrados.length) renderizarTablaHR();
});

document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape') {
        document.querySelectorAll('.modal.show').forEach(modal => {
            cerrarModal(modal.id);
        });
    }
});
