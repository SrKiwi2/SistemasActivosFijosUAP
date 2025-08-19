/*
* 
* TRANSAFERENCIA DE ACTIVOS
* 
*/

let debounceTimers = {};
let campoIndex = 0;

document.addEventListener("DOMContentLoaded", () => {
    const fechaHoy = new Date();
    const yyyy = fechaHoy.getFullYear();
    const mm = String(fechaHoy.getMonth() + 1).padStart(2, '0');
    const dd = String(fechaHoy.getDate()).padStart(2, '0');
    const hoyFormateado = `${yyyy}-${mm}-${dd}`;

    // Asignar fecha actual a ambos campos si existen
    const transferencia = document.getElementById("fechaTransferencia");
    const recepcion = document.getElementById("fechaRecepcion");

    if (transferencia) transferencia.value = hoyFormateado;
    if (recepcion) recepcion.value = hoyFormateado;
});

(function (window, document, $) {
    "use strict";

    const TransferenciaActivos = (function () {

        const CFG = {
            MODAL_ID: "#formularioModalTranferenciaActivo",
            CONTAINER_ID: "#codigoActivoContainer",
            BADGE_ID: "#badgeTotalActivos",
            BTN_ADD_ID: "#agregarCodigoBtn",
            BTN_CLEAR_ID: "#limpiarCodigosBtn",

            API_OFICINAS_SUG: "/api/oficinas/sugerencias",
            API_BUSCAR_ACTIVO: "/api/buscar-activo",

            DEBOUNCE_MS: 400,
            MIN_CODE_LEN: 4,
            AUTOCOMPLETE_MINLEN: 2,
        };

        let campoIndex = 0;
        const debounceTimers = {};
        const codigosSet = new Set();

        let $container, $badge, $modal;

        // Helpers

        const nv = (v) => (v == null ? "" : String(v).trim());
        const normalizaCodigo = (s) => nv(s).toUpperCase().replace(/\s+/g, "");

        function actualizarBadge() {
            const total = $container.find(".codigo-input").length;
            $badge.text(`${total} seleccionados`);
        }

        function mostrarEstado(index, estado) {
            const $icono = $(`#estado-${index} i`);
            if (estado === true) {
                $icono.attr("class", "bi bi-check-circle-fill text-success");
            } else if (estado === false) {
                $icono.attr("class", "bi bi-x-circle-fill text-danger");
            } else {
                $icono.attr("class", "bi");
            }
        }

        function setUbicacionActual(index, texto) {
            const $row = $container.find(`.row[data-index="${index}"]`);
            const $input = $row.find('input[name="ubicacionActual[]"]');
            $input.val(nv(texto));
        }

        // Autocomplete
        function enlazarAutocompleteUltimos() {

            // Autocomplete para Origen
            $container
                .find('input[name="ubicacionOrigen[]"]')
                .last()
                .autocomplete({
                    source: function (request, response) {
                        $.ajax({
                            url: CFG.API_OFICINAS_SUG,
                            data: { termino: request.term },
                            success: response,
                        });
                    },
                    minLength: CFG.AUTOCOMPLETE_MINLEN,
                    appendTo: CFG.MODAL_ID,
                    delay: 300,
                });

            // Autocomplete para Actual
            $container
                .find('input[name="ubicacionActual[]"]')
                .last()
                .autocomplete({
                    source: function (request, response) {
                        $.ajax({
                            url: CFG.API_OFICINAS_SUG,
                            data: { termino: request.term },
                            success: response,
                        });
                    },
                    minLength: CFG.AUTOCOMPLETE_MINLEN,
                    appendTo: CFG.MODAL_ID,
                    delay: 300,
                });
        }

        // DOM builders
        function agregarCampoCodigo() {
            campoIndex++;
            const idx = campoIndex;

            const rowHtml = `
           <div class="row g-3 mb-2 align-items-start" data-index="${idx}">
           <!-- Código del Activo -->
           <div class="col-md-4">
               <label class="form-label fw-semibold">Código del Activo</label>
               <div class="input-group">
               <span class="input-group-text bg-light"><i class="bi bi-upc-scan"></i></span>
               <input type="text" class="form-control codigo-input" name="codigoActivo[]" data-index="${idx}" placeholder="Ej: xx-xx-xx-xxxxx" autocomplete="off" required>
               <span class="input-group-text estado-icono" id="estado-${idx}" title="Estado">
                   <i class="bi"></i>
               </span>
               <button type="button" class="btn btn-outline-danger removeCodigoBtn" title="Eliminar fila">
                   <i class="bi bi-trash"></i>
               </button>
               </div>
               <small class="text-muted">Escribe el código y espera el indicador verde.</small>
           </div>

           <!-- Ubicación de Origen y N° de Oficina -->
           <div class="col-md-4">
               <label class="form-label fw-semibold">Ubicación de Origen y N° de Oficina</label>
               <input type="text" class="form-control ubicacion-origen-input" style="text-transform: uppercase;" name="ubicacionOrigen[]" placeholder="Ej: Unidad de Sistemas..." autocomplete="off" required>
           </div>

           <!-- Ubicación Actual y N° de Oficina -->
           <div class="col-md-4">
               <label class="form-label fw-semibold">Ubicación Actual y N° de Oficina</label>
               <input type="text" class="form-control ubicacion-actual-input" style="text-transform: uppercase;" name="ubicacionActual[]" placeholder="Se autocompleta al validar el código" autocomplete="off" required>
               <small class="text-muted">Se autocompleta con la oficina actual del activo. Puedes ajustar si corresponde.</small>
           </div>
           </div>`;

            $container.append(rowHtml);
            enlazarAutocompleteUltimos();
            actualizarBadge();
        }

        // Eventos
        function bindEvents() {
            // Agregar fila
            $(CFG.BTN_ADD_ID).on("click", () => agregarCampoCodigo());

            // Limpiar todos
            $(CFG.BTN_CLEAR_ID).on("click", () => {
                codigosSet.clear();
                $container.empty();
                actualizarBadge();
                agregarCampoCodigo();
            });

            // Eliminar fila (delegado)
            $container.on("click", ".removeCodigoBtn", function () {
                const $row = $(this).closest(".row[data-index]");
                const idx = $row.data("index");
                const codigoVal = normalizaCodigo($row.find(".codigo-input").val());
                if (codigoVal) codigosSet.delete(codigoVal);
                $row.remove();
                actualizarBadge();
            });

            // Input de código con debounce + consulta API + autocompletar ubicación actual
            $container.on("input", ".codigo-input", function () {
                const $input = $(this);
                const index = $input.data("index");
                const codigo = normalizaCodigo($input.val());

                // Si cambió, remueve el anterior del set (si existía)
                const prev = $input.data("prev") || null;
                if (prev && prev !== codigo) codigosSet.delete(prev);

                clearTimeout(debounceTimers[index]);
                mostrarEstado(index, null);

                // si es corto, no buscamos
                if (codigo.length < CFG.MIN_CODE_LEN) {
                    setUbicacionActual(index, "");
                    $input.data("prev", "");
                    return;
                }

                debounceTimers[index] = setTimeout(function () {
                    // Duplicado
                    if (codigosSet.has(codigo)) {
                        mostrarEstado(index, false);
                        setUbicacionActual(index, "");
                        return;
                    }

                    $.get(CFG.API_BUSCAR_ACTIVO, { codigo: codigo }, function (data) {
                        // Éxito
                        mostrarEstado(index, true);
                        codigosSet.add(codigo);
                        $input.data("prev", codigo);

                        // Autocompleta ubicación actual con lo que devuelve el backend
                        const ubic = data?.oficinaTexto || data?.oficinaNombre || "";
                        setUbicacionActual(index, ubic);
                    }).fail(() => {
                        // No existe
                        mostrarEstado(index, false);
                        setUbicacionActual(index, "");
                        $input.data("prev", "");
                    });
                }, CFG.DEBOUNCE_MS);
            });

            // (Opcional) al abrir el modal, enfocar primer campo de la sección de “Transfiere”
            $(CFG.MODAL_ID).on("shown.bs.modal", function () {
                $(this).find('input[name="unidadOrigen"]').trigger("focus");
            });
        }

        // Init
        function init() {
            $container = $(CFG.CONTAINER_ID);
            $badge = $(CFG.BADGE_ID);
            $modal = $(CFG.MODAL_ID);

            // Crea la primera fila
            agregarCampoCodigo();

            // Enlaza eventos
            bindEvents();
        }

        // Exponer API pública si hiciera falta en el futuro
        return { init };
    })();

    // Boot
    $(document).ready(function () {
        TransferenciaActivos.init();
    });

    // También disponible en window por si necesitas interactuar desde fuera
    window.TransferenciaActivos = TransferenciaActivos;
})(window, document, jQuery);

(function (window, document, $) {
    "use strict";

    const TransferenciaConfirmacion = (function () {
        // Config
        const CFG = {
            MODAL_ORIG: "#formularioModalTranferenciaActivo",
            MODAL_CONF: "#modalConfirmacionTransferencia",
            FORM_ID: "#formTransferencia",

            BTN_VERIFICAR: "#btnVerificarDatos",
            BTN_CONFIRMAR: "#btnConfirmarTransferencia",
            SPN_CONFIRMAR: "#spinnerConfirmarTransfer",

            // Secciones resumen
            UL_ORIGEN: "#listaOrigenTransfer",
            UL_DESTINO: "#listaDestinoTransfer",
            UL_FECHAS: "#listaFechasTransfer",
            TBODY_ACT: "#tbodyActivosTransfer",
            BADGE_ACT: "#badgeTotalActivosResumen",

            // Estados
            LOADING: "#confirmacionLoadingTransfer",
            CONTENT: "#contenidoConfirmacionTransfer",
            ALERT: "#alertaTransfer",

            // API externa (igual a otros flujos)
            API_DATOS: "http://virtual.uap.edu.bo:7174/api/londraPost/v1/obtenerDatos",
            API_HEADERS: {
                "Content-Type": "application/json",
                "key": "e73b1991c59a67fe182524e4d12da556136ced8a9da310c3af4c4efbde804a10"
            },

            // Endpoint de confirmación (ajústalo a tu backend real)
            POST_URL: "/trasnferencia/buscar-registrar"
        };

        // Refs
        let $form, $modalOrigEl, $modalConfEl, $loading, $content, $alert;
        let $ulOrigen, $ulDestino, $ulFechas, $tbodyAct, $badgeAct;

        // Helpers
        const nv = (v) => (v === null || v === undefined || (typeof v === 'string' && v.trim() === "")) ? "N/A" : String(v).trim();
        const esc = (html) => nv(html).replace(/[&<>"'`=\/]/g, s => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;', '/': '&#x2F;', '`': '&#x60;', '=': '&#x3D;' }[s]));
        const setLoading = (v) => { $loading.toggleClass("d-none", !v); $content.toggleClass("d-none", v); };
        const showAlert = (msg) => { $alert.removeClass("d-none").html(`<i class="bi bi-exclamation-triangle me-1"></i> ${msg || 'Revisa la información indicada.'}`); };
        const hideAlert = () => $alert.addClass("d-none");

        function validarMinimo(vals, activos) {
            const req = [
                vals.unidadOrigen, vals.codigoFuncionarioOrigen, vals.ciOrigen, vals.fechaTransferencia,
                vals.unidadDestino, vals.codigoFuncionarioDestino, vals.ciDestino, vals.fechaRecepcion
            ];
            return req.every(v => v && String(v).trim()) && activos.length > 0;
        }

        function getValoresFormulario() {
            const f = $form[0];
            const filas = document.querySelectorAll('#codigoActivoContainer .row[data-index]');
            const activos = [];
            filas.forEach((row, i) => {
                activos.push({
                    idx: i + 1,
                    codigo: row.querySelector('.codigo-input')?.value || '',
                    descripcion: row.querySelector('.codigo-input')?.value || '',
                    origen: row.querySelector('input[name="ubicacionOrigen[]"]')?.value || '',
                    actual: row.querySelector('input[name="ubicacionActual[]"]')?.value || ''
                });
            });

            return {
                vals: {
                    unidadOrigen: f.unidadOrigen?.value || "",
                    codigoFuncionarioOrigen: f.codigoFuncionarioOrigen?.value?.trim() || "",
                    ciOrigen: f.ciOrigen?.value?.trim() || "",
                    fechaTransferencia: f.fechaTransferencia?.value || "",

                    unidadDestino: f.unidadDestino?.value || "",
                    codigoFuncionarioDestino: f.codigoFuncionarioDestino?.value?.trim() || "",
                    ciDestino: f.ciDestino?.value?.trim() || "",
                    fechaRecepcion: f.fechaRecepcion?.value || "",
                },
                activos
            };
        }

        function renderResumen(dataOrig, dataDest, vals, activos) {
            // Origen
            $ulOrigen.html(`
               <li class="list-group-item d-flex justify-content-between"><span><i class="bi bi-diagram-3"></i> Unidad (ingresado)</span><span class="fw-semibold">${esc(vals.unidadOrigen)}</span></li>
               <li class="list-group-item d-flex justify-content-between"><span><i class="bi bi-person"></i> Nombre</span><span class="fw-semibold">${nv(dataOrig.per_nombres)} ${nv(dataOrig.per_ap_paterno)} ${nv(dataOrig.per_ap_materno)}</span></li>
               <li class="list-group-item d-flex justify-content-between"><span><i class="bi bi-credit-card-2-front"></i> CI</span><span class="fw-semibold">${nv(dataOrig.per_num_doc)}</span></li>
               <li class="list-group-item d-flex justify-content-between"><span><i class="bi bi-envelope"></i> Email</span><span class="fw-semibold">${nv(dataOrig.perd_email_personal)}</span></li>
               <li class="list-group-item d-flex justify-content-between"><span><i class="bi bi-building"></i> Oficina</span><span class="fw-semibold">${nv(dataOrig.eo_descripcion)}</span></li>
               <li class="list-group-item d-flex justify-content-between"><span><i class="bi bi-briefcase"></i> Cargo</span><span class="fw-semibold">${nv(dataOrig.p_descripcion)}</span></li>
               <li class="list-group-item d-flex justify-content-between"><span><i class="bi bi-person-badge"></i> Código func. (ingresado)</span><span class="fw-semibold">${esc(vals.codigoFuncionarioOrigen)}</span></li>
           `);

            // Fechas
            $ulFechas.html(`
               <li class="list-group-item d-flex justify-content-between"><span><i class="bi bi-calendar3"></i> Transferencia</span><span class="fw-semibold">${esc(vals.fechaTransferencia) || 'N/A'}</span></li>
               <li class="list-group-item d-flex justify-content-between"><span><i class="bi bi-calendar-check"></i> Recepción</span><span class="fw-semibold">${esc(vals.fechaRecepcion) || 'N/A'}</span></li>
           `);

            // Destino
            $ulDestino.html(`
               <li class="list-group-item d-flex justify-content-between"><span><i class="bi bi-diagram-3"></i> Unidad (ingresado)</span><span class="fw-semibold">${esc(vals.unidadDestino)}</span></li>
               <li class="list-group-item d-flex justify-content-between"><span><i class="bi bi-person"></i> Nombre</span><span class="fw-semibold">${nv(dataDest.per_nombres)} ${nv(dataDest.per_ap_paterno)} ${nv(dataDest.per_ap_materno)}</span></li>
               <li class="list-group-item d-flex justify-content-between"><span><i class="bi bi-credit-card-2-front"></i> CI</span><span class="fw-semibold">${nv(dataDest.per_num_doc)}</span></li>
               <li class="list-group-item d-flex justify-content-between"><span><i class="bi bi-envelope"></i> Email</span><span class="fw-semibold">${nv(dataDest.perd_email_personal)}</span></li>
               <li class="list-group-item d-flex justify-content-between"><span><i class="bi bi-building"></i> Oficina</span><span class="fw-semibold">${nv(dataDest.eo_descripcion)}</span></li>
               <li class="list-group-item d-flex justify-content-between"><span><i class="bi bi-briefcase"></i> Cargo</span><span class="fw-semibold">${nv(dataDest.p_descripcion)}</span></li>
               <li class="list-group-item d-flex justify-content-between"><span><i class="bi bi-person-badge"></i> Código func. (ingresado)</span><span class="fw-semibold">${esc(vals.codigoFuncionarioDestino)}</span></li>
           `);

            // Activos
            $tbodyAct.empty();
            activos.forEach((a, i) => {
                $tbodyAct.append(`
               <tr>
                   <td>${i + 1}</td>
                   <td>${esc(a.codigo)}</td>
                   <td>${esc(a.codigo)}</td>
                   <td>${esc(a.origen)}</td>
                   <td>${esc(a.actual)}</td>
               </tr>
               `);
            });
            $("#badgeTotalActivosResumen").text(`${activos.length} seleccionados`);
        }

        function abrirConfirmacionOcultandoPrimero(cb) {
            const instOrig = bootstrap.Modal.getInstance($modalOrigEl[0]) || new bootstrap.Modal($modalOrigEl[0]);
            if ($modalOrigEl.hasClass("show")) {
                const onHidden = () => {
                    $modalOrigEl.off("hidden.bs.modal", onHidden);
                    new bootstrap.Modal($modalConfEl[0], { backdrop: 'static', keyboard: false }).show();
                    cb && cb();
                };
                $modalOrigEl.on("hidden.bs.modal", onHidden);
                instOrig.hide();
            } else {
                new bootstrap.Modal($modalConfEl[0], { backdrop: 'static', keyboard: false }).show();
                cb && cb();
            }
        }

        function bindEvents() {
            // Verificar
            $(CFG.BTN_VERIFICAR).on("click", () => {
                abrirConfirmacionOcultandoPrimero(async () => {
                    setLoading(true);
                    hideAlert();

                    const { vals, activos } = getValoresFormulario();

                    // Si no hay mínimos, muestra alerta pero renderiza con lo ingresado
                    if (!validarMinimo(vals, activos)) {
                        renderResumen({}, {}, vals, activos);
                        showAlert("Faltan datos obligatorios o hay formato inválido.");
                        setLoading(false);
                        return;
                    }

                    // Mixed content (https -> http)
                    if (location.protocol === "https:" && CFG.API_DATOS.startsWith("http:")) {
                        renderResumen({}, {}, vals, activos);
                        showAlert("El navegador bloqueó la consulta (mixed content). Usa HTTPS o un proxy en tu backend.");
                        setLoading(false);
                        return;
                    }

                    // Consultas a API (origen/destino)
                    const reqOrigen = fetch(CFG.API_DATOS, {
                        method: "POST",
                        headers: CFG.API_HEADERS,
                        body: JSON.stringify({ usuario: vals.codigoFuncionarioOrigen, contrasena: vals.ciOrigen })
                    }).then(r => r.ok ? r.json() : Promise.reject(new Error(`Origen ${r.status}`)));

                    const reqDestino = fetch(CFG.API_DATOS, {
                        method: "POST",
                        headers: CFG.API_HEADERS,
                        body: JSON.stringify({ usuario: vals.codigoFuncionarioDestino, contrasena: vals.ciDestino })
                    }).then(r => r.ok ? r.json() : Promise.reject(new Error(`Destino ${r.status}`)));

                    try {
                        const [o, d] = await Promise.allSettled([reqOrigen, reqDestino]);
                        const dataOrig = o.status === "fulfilled" ? o.value : {};
                        const dataDest = d.status === "fulfilled" ? d.value : {};
                        if (o.status !== "fulfilled" || d.status !== "fulfilled") {
                            showAlert("No fue posible consultar todos los datos. Revisa lo ingresado.");
                            console.warn("API transferencia:", o.reason || "", d.reason || "");
                        }
                        renderResumen(dataOrig, dataDest, vals, activos);
                    } catch (err) {
                        console.error("Error consultando APIs:", err);
                        renderResumen({}, {}, vals, activos);
                        showAlert("No fue posible consultar los datos (ver consola).");
                    } finally {
                        setLoading(false);
                    }
                });
            });

            // Confirmar => POST al backend
            $(CFG.BTN_CONFIRMAR).on("click", () => {
                const btn = document.querySelector(CFG.BTN_CONFIRMAR);
                const spn = document.querySelector(CFG.SPN_CONFIRMAR);
                btn.disabled = true; spn.classList.remove("d-none");

                const formData = new FormData($form[0]);
                fetch(CFG.POST_URL, { method: "POST", body: formData })
                    .then((r) => {
                        if (!r.ok) throw new Error("Error generando PDF");
                        return r.blob();
                    })
                    .then((blob) => {
                        const url = URL.createObjectURL(blob);
                        document.getElementById("iframePDF").src = url;

                        const pdfModal = new bootstrap.Modal(document.getElementById("modalVisualizarPdf"));
                        pdfModal.show();

                        const inst = bootstrap.Modal.getInstance($modalConfEl[0]);
                        inst?.hide();
                    })
                    .catch((err) => {
                        alert("Ocurrió un error: " + err.message);
                    })
                    .finally(() => {
                        btn.disabled = false; spn.classList.add("d-none");
                    });
            });
        }

        function init() {
            $form = $(CFG.FORM_ID);
            $modalOrigEl = $(CFG.MODAL_ORIG);
            $modalConfEl = $(CFG.MODAL_CONF);

            $loading = $(CFG.LOADING);
            $content = $(CFG.CONTENT);
            $alert = $(CFG.ALERT);

            $ulOrigen = $(CFG.UL_ORIGEN);
            $ulDestino = $(CFG.UL_DESTINO);
            $ulFechas = $(CFG.UL_FECHAS);
            $tbodyAct = $(CFG.TBODY_ACT);
            $badgeAct = $(CFG.BADGE_ACT);
        }

        return { init, bindEvents };
    })();

    $(document).ready(function () {
        TransferenciaConfirmacion.init();
        TransferenciaConfirmacion.bindEvents();
    });

})(window, document, jQuery);