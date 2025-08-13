/*
         * 
         * INGRESO DE ACTIVOS AJENOS
         * 
         */

document.addEventListener("DOMContentLoaded", () => {
    /* ---------- Helpers ---------- */
    const nv = (v) => (v === null || v === undefined || (typeof v === 'string' && v.trim() === "")) ? "N/A" : String(v).trim();
    const esc = (html) => nv(html).replace(/[&<>"'`=\/]/g, s => ({
        '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;',
        "'": '&#39;', '/': '&#x2F;', '`': '&#x60;', '=': '&#x3D;'
    }[s]));

    /* ---------- Refs UI ---------- */
    const form = document.getElementById("formIngresoActivos");
    const modalIngresoEl = document.getElementById("formularioModalIngresoActivoAjeno");
    const modalConfirmacionEl = document.getElementById("modalConfirmacionIngreso");

    const btnAgregar = document.getElementById("agregarActivoAjenoBtn");
    const btnLimpiar = document.getElementById("limpiarAjenosBtn");
    const contItems = document.getElementById("agregarActivoAjenoContainer");
    const badgeAjenosForm = document.getElementById("badgeTotalAjenos");

    const btnVerificar = document.getElementById("btnVerificarDatosIngreso");
    const loading = document.getElementById("confirmacionLoadingIngreso");
    const contenido = document.getElementById("contenidoConfirmacionIngreso");
    const alerta = document.getElementById("confirmacionAlertaIngreso");

    const listaFechas = document.getElementById("listaFechasIngreso");
    const listaProp = document.getElementById("listaPropietarioIngreso");
    const listaAuto = document.getElementById("listaAutorizadorIngreso");
    const tbodyAjenos = document.getElementById("tbodyAjenosResumen");
    const badgeAjenosResumen = document.getElementById("badgeTotalAjenosResumen");

    const btnConfirmar = document.getElementById("btnConfirmarGuardarIngreso");
    const spinnerConfirmar = document.getElementById("spinnerConfirmarIngreso");

    /* ---------- Estado dinámico de items ---------- */
    let campoIndexA = 0;

    function actualizarBadgeForm() {
        const total = contItems.querySelectorAll(".activo-ajeno-item").length;
        badgeAjenosForm.textContent = `${total} items`;
    }

    function plantillaItem(index) {
        return `
    <div class="col-12 activo-ajeno-item" data-index="${index}">
        <div class="border rounded-3 p-3 position-relative">
        <button type="button" class="btn btn-sm btn-outline-danger position-absolute top-0 end-0 m-2 btn-remove-ajeno" title="Eliminar">
            <i class="bi bi-trash"></i>
        </button>

        <div class="row g-3">
            <div class="col-md-8">
            <label class="form-label fw-semibold">Descripción del activo (Tipo, Marca, Modelo, Color, etc.)</label>
            <input type="text" class="form-control" name="descripcionActivo[]" placeholder="Ej: Laptop Lenovo ThinkPad T14" required oninput="this.value = this.value.toUpperCase()">
            </div>

            <div class="col-md-4">
            <label class="form-label fw-semibold">Estado</label>
            <select class="form-select" name="estadoActivoAjeno[]" required>
                <option value="" selected disabled>Seleccione...</option>
                <option value="BUENO">BUENO</option>
                <option value="REGULAR">REGULAR</option>
            </select>
            </div>
        </div>
        </div>
    </div>
    `;
    }

    function agregarCampoActivoAjeno() {
        campoIndexA++;
        contItems.insertAdjacentHTML("beforeend", plantillaItem(campoIndexA));
        actualizarBadgeForm();
    }

    function limpiarListaAjenos() {
        contItems.innerHTML = "";
        campoIndexA = 0;
        agregarCampoActivoAjeno();
    }

    // Inicial
    agregarCampoActivoAjeno();

    // Botones de items
    btnAgregar?.addEventListener("click", agregarCampoActivoAjeno);

    btnLimpiar?.addEventListener("click", () => {
        limpiarListaAjenos();
    });

    contItems.addEventListener("click", (e) => {
        const btn = e.target.closest(".btn-remove-ajeno");
        if (!btn) return;
        const card = btn.closest(".activo-ajeno-item");
        const total = contItems.querySelectorAll(".activo-ajeno-item").length;

        if (total > 1) {
            card.remove();
        } else {
            // si solo queda uno, limpiamos campos en vez de eliminar
            card.querySelector('input[name="descripcionActivo[]"]').value = "";
            card.querySelector('select[name="estadoActivoAjeno[]"]').value = "";
            alert("Debe haber al menos un activo registrado. Los campos han sido limpiados.");
        }
        actualizarBadgeForm();
    });

    /* ---------- Autocalcular fecha de retiro (+3 meses) ---------- */
    document.getElementById('fechaIncorporacion')?.addEventListener('change', function () {
        const fechaIngreso = new Date(this.value);
        const out = document.getElementById('fechaRetiro');
        if (isNaN(fechaIngreso.getTime())) { out.value = ''; return; }
        const fechaRetiro = new Date(fechaIngreso);
        fechaRetiro.setMonth(fechaRetiro.getMonth() + 3);
        if (fechaIngreso.getDate() !== fechaRetiro.getDate()) fechaRetiro.setDate(0);
        const y = fechaRetiro.getFullYear(), m = ('0' + (fechaRetiro.getMonth() + 1)).slice(-2), d = ('0' + fechaRetiro.getDate()).slice(-2);
        out.value = `${y}-${m}-${d}`;
    });

    /* ---------- Utilidades modal confirmación ---------- */
    const setLoadingModal = (v) => {
        loading.classList.toggle("d-none", !v);
        contenido.classList.toggle("d-none", v);
    };
    const showAlert = (msg) => {
        alerta.classList.remove("d-none");
        if (msg) {
            const wrap = alerta.querySelector('.bi-exclamation-triangle')?.parentNode;
            if (wrap) {
                // reemplaza el texto a continuación del ícono
                wrap.lastChild.nodeType === 3
                    ? (wrap.lastChild.textContent = " " + msg)
                    : wrap.append(document.createTextNode(" " + msg));
            }
        }
    };
    const hideAlert = () => alerta.classList.add("d-none");

    function abrirModalConfirmacion() {
        new bootstrap.Modal(modalConfirmacionEl, { backdrop: 'static', keyboard: false }).show();
    }
    function ocultarIngresoYAbrirConfirmacion(cb) {
        const inst = bootstrap.Modal.getInstance(modalIngresoEl) || new bootstrap.Modal(modalIngresoEl);
        if (modalIngresoEl.classList.contains('show')) {
            const onHidden = () => {
                modalIngresoEl.removeEventListener('hidden.bs.modal', onHidden);
                abrirModalConfirmacion();
                cb && cb();
            };
            modalIngresoEl.addEventListener('hidden.bs.modal', onHidden);
            inst.hide();
        } else {
            abrirModalConfirmacion();
            cb && cb();
        }
    }

    /* ---------- Lectura / Render ---------- */
    function getValores() {
        const vals = {
            fechaIncorporacion: form.fechaIncorporacion?.value || "",
            fechaRetiro: form.fechaRetiro?.value || "",
            codigoFuncionarioPropietario: form.codigoFuncionarioPropietario?.value?.trim() || "",
            ciPropietario: form.ciPropietario?.value?.trim() || "",
            codigoFuncionarioAutorizador: form.codigoFuncionarioAutorizador?.value?.trim() || "",
            ciAutorizador: form.ciAutorizador?.value?.trim() || ""
        };
        const items = [];
        contItems.querySelectorAll('.activo-ajeno-item').forEach((card, i) => {
            items.push({
                idx: i + 1,
                desc: card.querySelector('input[name="descripcionActivo[]"]')?.value || "",
                estado: card.querySelector('select[name="estadoActivoAjeno[]"]')?.value || ""
            });
        });
        return { vals, items };
    }

    function validarCamposPersonas(vals) {
        const req = [
            vals.fechaIncorporacion,
            vals.codigoFuncionarioPropietario,
            vals.ciPropietario,
            vals.codigoFuncionarioAutorizador,
            vals.ciAutorizador
        ];
        return req.every(v => v && String(v).trim());
    }

    function renderFechas(vals) {
        listaFechas.innerHTML = `
    <li class="list-group-item d-flex justify-content-between">
        <span><i class="bi bi-calendar-week"></i> Incorporación</span>
        <span class="fw-semibold">${esc(vals.fechaIncorporacion) || 'N/A'}</span>
    </li>
    <li class="list-group-item d-flex justify-content-between">
        <span><i class="bi bi-calendar2-x"></i> Retiro</span>
        <span class="fw-semibold">${esc(vals.fechaRetiro) || 'Pendiente'}</span>
    </li>
    `;
    }

    function renderPersonas(dataProp, dataAuto, vals) {
        listaProp.innerHTML = `
        <li class="list-group-item d-flex justify-content-between"><span><i class="bi bi-person"></i> Nombre</span><span class="fw-semibold">${nv(dataProp.per_nombres)} ${nv(dataProp.per_ap_paterno)} ${nv(dataProp.per_ap_materno)}</span></li>
        <li class="list-group-item d-flex justify-content-between"><span><i class="bi bi-credit-card-2-front"></i> CI</span><span class="fw-semibold">${nv(dataProp.per_num_doc)}</span></li>
        <li class="list-group-item d-flex justify-content-between"><span><i class="bi bi-envelope"></i> Email</span><span class="fw-semibold">${nv(dataProp.perd_email_personal)}</span></li>
        <li class="list-group-item d-flex justify-content-between"><span><i class="bi bi-building"></i> Oficina</span><span class="fw-semibold">${nv(dataProp.eo_descripcion)}</span></li>
        <li class="list-group-item d-flex justify-content-between"><span><i class="bi bi-briefcase"></i> Cargo</span><span class="fw-semibold">${nv(dataProp.p_descripcion)}</span></li>
        <li class="list-group-item d-flex justify-content-between"><span><i class="bi bi-person-badge"></i> Código func. (ingresado)</span><span class="fw-semibold">${esc(vals.codigoFuncionarioPropietario) || 'N/A'}</span></li>
        `;

        listaAuto.innerHTML = `
        <li class="list-group-item d-flex justify-content-between"><span><i class="bi bi-person-check"></i> Nombre</span><span class="fw-semibold">${nv(dataAuto.per_nombres)} ${nv(dataAuto.per_ap_paterno)} ${nv(dataAuto.per_ap_materno)}</span></li>
        <li class="list-group-item d-flex justify-content-between"><span><i class="bi bi-credit-card-2-front"></i> CI</span><span class="fw-semibold">${nv(dataAuto.per_num_doc)}</span></li>
        <li class="list-group-item d-flex justify-content-between"><span><i class="bi bi-envelope"></i> Email</span><span class="fw-semibold">${nv(dataAuto.perd_email_personal)}</span></li>
        <li class="list-group-item d-flex justify-content-between"><span><i class="bi bi-building"></i> Oficina</span><span class="fw-semibold">${nv(dataAuto.eo_descripcion)}</span></li>
        <li class="list-group-item d-flex justify-content-between"><span><i class="bi bi-briefcase"></i> Cargo</span><span class="fw-semibold">${nv(dataAuto.p_descripcion)}</span></li>
        <li class="list-group-item d-flex justify-content-between"><span><i class="bi bi-person-badge"></i> Código func. (ingresado)</span><span class="fw-semibold">${esc(vals.codigoFuncionarioAutorizador) || 'N/A'}</span></li>
        `;
    }

    function renderItems(items) {
        tbodyAjenos.innerHTML = "";
        items.forEach((it, i) => {
            tbodyAjenos.insertAdjacentHTML("beforeend", `
        <tr>
        <td>${i + 1}</td>
        <td><div class="text-wrap" style="max-width: 900px; white-space: normal; word-break: break-word;">${esc(it.desc)}</div></td>
        </tr>
    `);
        });
        badgeAjenosResumen.textContent = `${items.length} items`;
    }

    // Normaliza la posible forma del JSON de la API
    function takePersonaShape(payload) {
        if (!payload) return {};
        if (payload.data && typeof payload.data === 'object') return payload.data;
        if (payload.persona && typeof payload.persona === 'object') return payload.persona;
        return payload; // campos en raíz
    }

    /* ---------- Abrir verificación ---------- */
    btnVerificar?.addEventListener("click", () => {
        const { vals, items } = getValores();
        ocultarIngresoYAbrirConfirmacion(() => {
            setLoadingModal(true);
            hideAlert();

            // Render inmediato con lo que hay
            renderFechas(vals);
            renderPersonas({}, {}, vals);
            renderItems(items);
            // sincroniza badge del formulario (por si cambió justo antes)
            actualizarBadgeForm();

            // Mixed content
            const apiUrl = "http://virtual.uap.edu.bo:7174/api/londraPost/v1/obtenerDatos";
            const headers = {
                "Content-Type": "application/json",
                "key": "e73b1991c59a67fe182524e4d12da556136ced8a9da310c3af4c4efbde804a10"
            };
            if (location.protocol === "https:" && apiUrl.startsWith("http:")) {
                showAlert("El navegador bloqueó la consulta (mixed content). Usa HTTPS o un proxy en tu backend.");
                setLoadingModal(false);
                return;
            }

            // ¿Podemos consultar personas?
            if (!validarCamposPersonas(vals)) {
                showAlert("Faltan campos obligatorios del propietario o autorizador. Completa y vuelve a verificar.");
                setLoadingModal(false);
                return;
            }

            // Consulta en paralelo
            const reqProp = fetch(apiUrl, {
                method: "POST", headers,
                body: JSON.stringify({ usuario: vals.codigoFuncionarioPropietario, contrasena: vals.ciPropietario })
            }).then(async r => {
                if (!r.ok) throw new Error("Propietario: " + r.status);
                const j = await r.json().catch(() => ({}));
                return takePersonaShape(j);
            });

            const reqAuto = fetch(apiUrl, {
                method: "POST", headers,
                body: JSON.stringify({ usuario: vals.codigoFuncionarioAutorizador, contrasena: vals.ciAutorizador })
            }).then(async r => {
                if (!r.ok) throw new Error("Autorizador: " + r.status);
                const j = await r.json().catch(() => ({}));
                return takePersonaShape(j);
            });

            Promise.allSettled([reqProp, reqAuto])
                .then(([p1, p2]) => {
                    const dataProp = p1.status === "fulfilled" ? p1.value : {};
                    const dataAuto = p2.status === "fulfilled" ? p2.value : {};
                    if (p1.status !== "fulfilled" || p2.status !== "fulfilled") {
                        console.warn("Fallo consulta API:", p1.reason || "", p2.reason || "");
                        showAlert("No fue posible consultar todos los datos. Revisa el código/CI o la conectividad.");
                    }
                    renderFechas(vals);
                    renderPersonas(dataProp, dataAuto, vals);
                    renderItems(items);
                })
                .catch(err => {
                    console.error("Error consultando API:", err);
                    showAlert("No fue posible consultar los datos (ver consola).");
                })
                .finally(() => setLoadingModal(false));
        });
    });

    /* ---------- Confirmar => POST + PDF ---------- */
    btnConfirmar?.addEventListener("click", () => {
        const formData = new FormData(form);
        btnConfirmar.disabled = true;
        spinnerConfirmar.classList.remove("d-none");

        fetch("/ingreso/registrar", { method: "POST", body: formData })
            .then((r) => { if (!r.ok) throw new Error("Error generando PDF"); return r.blob(); })
            .then((blob) => {
                const url = URL.createObjectURL(blob);
                document.getElementById("iframePDF").src = url;

                const pdfModal = new bootstrap.Modal(document.getElementById("modalVisualizarPdf"));
                pdfModal.show();

                const inst = bootstrap.Modal.getInstance(modalConfirmacionEl);
                inst?.hide();
            })
            .catch((err) => alert("Ocurrió un error: " + err.message))
            .finally(() => {
                btnConfirmar.disabled = false;
                spinnerConfirmar.classList.add("d-none");
            });
    });
});