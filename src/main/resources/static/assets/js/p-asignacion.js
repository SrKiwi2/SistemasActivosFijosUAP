/*
 * ASIGNACIÓN DE ACTIVO
 */
document.addEventListener("DOMContentLoaded", () => {
    // ---------- Helpers ----------
    const nv = (v) =>
        v === null || v === undefined || (typeof v === "string" && v.trim() === "")
            ? "N/A"
            : String(v).trim();

    const esc = (html) =>
        nv(html).replace(/[&<>"'`=\/]/g, (s) =>
        ({
            "&": "&amp;",
            "<": "&lt;",
            ">": "&gt;",
            '"': "&quot;",
            "'": "&#39;",
            "/": "&#x2F;",
            "`": "&#x60;",
            "=": "&#x3D;",
        }[s])
        );

    const takePersonaShape = (payload) => {
        if (!payload) return {};
        if (payload.data && typeof payload.data === "object") return payload.data;
        if (payload.persona && typeof payload.persona === "object") return payload.persona;
        return payload;
    };

    // ---------- Refs ----------
    const form = document.getElementById("formResponsable");
    const btnPreGuardar = document.getElementById("btnPreGuardar");

    // Modales
    const modalAsignacionEl = document.getElementById("formularioModalAsignacionActivo"); // modal origen
    const modalConfirmacionEl = document.getElementById("modalConfirmacionDatos");        // modal confirmación

    // Contenido modal confirmación
    const confirmacionLoading = document.getElementById("confirmacionLoading");
    const contenidoConfirmacion = document.getElementById("contenidoConfirmacion");
    const listaResponsable = document.getElementById("listaResponsable");
    const listaAsignacion = document.getElementById("listaAsignacion");

    // Confirmar/guardar
    const btnConfirmarGuardar = document.getElementById("btnConfirmarGuardar");
    const spinnerConfirmar = document.getElementById("spinnerConfirmar");

    // ---------- Alert dinámico en el FORM ----------
    function ensureFormAlert() {
        let el = document.getElementById("alertaAsignacionForm");
        if (!el) {
            el = document.createElement("div");
            el.id = "alertaAsignacionForm";
            el.className = "alert d-none";
            const body = form.closest(".modal-content")?.querySelector(".modal-body");
            body && body.prepend(el);
        }
        return el;
    }

    function showFormAlert(msg, type = "danger") {
        const el = ensureFormAlert();
        el.className = `alert alert-${type} d-flex align-items-center`;
        el.innerHTML = `
        <i class="bi bi-exclamation-triangle me-2"></i>
        <div>${esc(msg)}</div>`;
        el.classList.remove("d-none");
    }

    function hideFormAlert() {
        ensureFormAlert().classList.add("d-none");
    }

    // ---------- UI utils ----------
    function setLoadingModal(isLoading) {
        confirmacionLoading.classList.toggle("d-none", !isLoading);
        contenidoConfirmacion.classList.toggle("d-none", isLoading);
    }

    function abrirConfirmacion() {
        new bootstrap.Modal(modalConfirmacionEl, { backdrop: "static", keyboard: false }).show();
    }

    function ocultarAsignacionYAbrirConfirmacion(cb) {
        const inst =
            bootstrap.Modal.getInstance(modalAsignacionEl) || new bootstrap.Modal(modalAsignacionEl);
        if (modalAsignacionEl && modalAsignacionEl.classList.contains("show")) {
            const onHidden = () => {
                modalAsignacionEl.removeEventListener("hidden.bs.modal", onHidden);
                abrirConfirmacion();
                cb && cb();
            };
            modalAsignacionEl.addEventListener("hidden.bs.modal", onHidden);
            inst.hide();
        } else {
            abrirConfirmacion();
            cb && cb();
        }
    }

    function markInvalid(names = []) {
        form.querySelectorAll(".is-invalid").forEach((n) => n.classList.remove("is-invalid"));
        names.forEach((name) => {
            const input = form.querySelector(`[name="${name}"]`);
            input?.classList.add("is-invalid");
        });
        if (names[0]) form.querySelector(`[name="${names[0]}"]`)?.focus();
    }

    function clearInvalid() {
        form.querySelectorAll(".is-invalid").forEach((n) => n.classList.remove("is-invalid"));
    }

    // ---------- Validación live ----------
    const campos = [
        { name: "unidad", mensaje: "La unidad es obligatoria" },
        { name: "codigoFuncionario", mensaje: "El código de funcionario es obligatorio" },
        { name: "ci", mensaje: "El C.I. es obligatorio" },
        { name: "hr", mensaje: "El H.R. es obligatorio" },
        { name: "ubicacionActivo", mensaje: "La ubicación es obligatoria" },
        { name: "descripcionActivo", mensaje: "La descripción del activo es obligatoria" },
    ];

    function validarCampo(input) {
        if (!input) return false;
        const ok = !!input.value.trim();
        input.classList.toggle("is-invalid", !ok);
        input.classList.toggle("is-valid", ok);
        return ok;
    }

    campos.forEach(({ name }) => {
        const input = form.querySelector(`input[name="${name}"]`);
        if (input) input.addEventListener("input", () => validarCampo(input));
    });

    // ---------- Lectura + validación mínima ----------
    function leerValores() {
        return {
            unidad: form.unidad?.value.trim() || "",
            codigoFuncionario: form.codigoFuncionario?.value.trim() || "",
            ci: form.ci?.value.trim() || "",
            hr: form.hr?.value.trim() || "",
            ubicacionActivo: form.ubicacionActivo?.value.trim() || "",
            descripcionActivo: form.descripcionActivo?.value.trim() || "",
        };
    }

    // ---------- Render del modal de confirmación ----------
    function renderConfirmacion(apiData, formVals) {
        const d = takePersonaShape(apiData || {});

        listaResponsable.innerHTML = `
        <li class="list-group-item d-flex justify-content-between"><span><i class="bi bi-person"></i> Nombre</span><span class="fw-semibold">${nv(d.per_nombres)} ${nv(d.per_ap_paterno)} ${nv(d.per_ap_materno)}</span></li>
        <li class="list-group-item d-flex justify-content-between"><span><i class="bi bi-credit-card-2-front"></i> CI</span><span class="fw-semibold">${nv(d.per_num_doc)}</span></li>
        <li class="list-group-item d-flex justify-content-between"><span><i class="bi bi-envelope"></i> Email</span><span class="fw-semibold">${nv(d.perd_email_personal)}</span></li>
        <li class="list-group-item d-flex justify-content-between"><span><i class="bi bi-building"></i> Oficina</span><span class="fw-semibold">${nv(d.eo_descripcion)}</span></li>
        <li class="list-group-item d-flex justify-content-between"><span><i class="bi bi-briefcase"></i> Cargo</span><span class="fw-semibold">${nv(d.p_descripcion)}</span></li>
        <li class="list-group-item d-flex justify-content-between"><span><i class="bi bi-person-badge"></i> Código funcionario (ingresado)</span><span class="fw-semibold">${nv(formVals.codigoFuncionario)}</span></li>
      `;

        listaAsignacion.innerHTML = `
        <li class="list-group-item d-flex justify-content-between"><span><i class="bi bi-hash"></i> Nº H.R.</span><span class="fw-semibold">${nv(formVals.hr)}</span></li>
        <li class="list-group-item d-flex justify-content-between"><span><i class="bi bi-diagram-3"></i> Unidad</span><span class="fw-semibold">${nv(formVals.unidad)}</span></li>
        <li class="list-group-item d-flex justify-content-between"><span><i class="bi bi-geo-alt"></i> Ubicación del activo</span><span class="fw-semibold">${nv(formVals.ubicacionActivo)}</span></li>
        <li class="list-group-item d-flex justify-content-between"><span><i class="bi bi-info-circle"></i> Descripción del activo</span><span class="fw-semibold">${nv(formVals.descripcionActivo)}</span></li>
        <li class="list-group-item d-flex justify-content-between"><span><i class="bi bi-card-text"></i> CI (ingresado)</span><span class="fw-semibold">${nv(formVals.ci)}</span></li>
      `;
    }

    // ---------- Pre-guardar (validación + API ANTES de abrir confirmación) ----------
    btnPreGuardar?.addEventListener("click", async () => {
        hideFormAlert();
        clearInvalid();

        const vals = leerValores();

        // Validación de campos requeridos
        let ok = true;
        campos.forEach(({ name }) => {
            const input = form.querySelector(`input[name="${name}"]`);
            if (!validarCampo(input)) ok = false;
        });
        if (!ok) {
            showFormAlert("Completa todos los campos obligatorios marcados con *.");
            return;
        }

        // Mixed content guard
        const apiUrl = "http://virtual.uap.edu.bo:7174/api/londraPost/v1/obtenerDatos";
        if (location.protocol === "https:" && apiUrl.startsWith("http:")) {
            showFormAlert("No se pudo consultar la API (mixed content). Expón la API en HTTPS o usa un proxy.");
            return;
        }

        // Estado "verificando…"
        const prevHtml = btnPreGuardar.innerHTML;
        btnPreGuardar.disabled = true;
        btnPreGuardar.innerHTML =
            `<span class="spinner-border spinner-border-sm me-2" role="status" aria-hidden="true"></span>Verificando…`;

        const headers = {
            "Content-Type": "application/json",
            key: "e73b1991c59a67fe182524e4d12da556136ced8a9da310c3af4c4efbde804a10",
        };

        try {
            const res = await fetch(apiUrl, {
                method: "POST",
                headers,
                body: JSON.stringify({
                    usuario: vals.codigoFuncionario,
                    contrasena: vals.ci,
                }),
            });

            if (!res.ok) {
                const t = await res.text().catch(() => "");
                // Error de API
                showFormAlert(
                    `No se pudo consultar la API del funcionario. (${res.status}) ${t || ""}`
                );
                markInvalid(["codigoFuncionario", "ci"]);
                return;
            }

            const data = await res.json().catch(() => ({}));
            const persona = takePersonaShape(data);

            // ¿Hay datos de persona?
            if (!persona || Object.keys(persona).length === 0) {
                showFormAlert(
                    "No se encontró al funcionario con el Código y C.I. ingresados. Verifica los datos e inténtalo nuevamente."
                );
                markInvalid(["codigoFuncionario", "ci"]);
                return;
            }

            // Éxito: abrir modal de confirmación con resumen
            ocultarAsignacionYAbrirConfirmacion(() => {
                setLoadingModal(true);
                renderConfirmacion(persona, vals);
                setLoadingModal(false);
            });
        } catch (err) {
            // Error de red / fetch
            showFormAlert(
                "No fue posible consultar los datos del funcionario. Revisa tu conexión e inténtalo nuevamente."
            );
            markInvalid(["codigoFuncionario", "ci"]);
            console.error(err);
        } finally {
            btnPreGuardar.disabled = false;
            btnPreGuardar.innerHTML = prevHtml;
        }
    });

    // ---------- Confirmar y guardar (PDF) ----------
    btnConfirmarGuardar?.addEventListener("click", () => {
        const formData = new FormData(form);
        btnConfirmarGuardar.disabled = true;
        spinnerConfirmar.classList.remove("d-none");

        fetch("/asignacion/asignar-activo-nuevo", {
            method: "POST",
            body: formData,
        })
            .then((response) => {
                if (!response.ok) throw new Error("Error generando PDF");
                return response.blob();
            })
            .then((blob) => {
                const url = URL.createObjectURL(blob);
                document.getElementById("iframePDF").src = url;

                const pdfModal = new bootstrap.Modal(
                    document.getElementById("modalVisualizarPdf")
                );
                pdfModal.show();

                const confirmModal = bootstrap.Modal.getInstance(modalConfirmacionEl);
                confirmModal?.hide();
            })
            .catch((err) => {
                alert("Ocurrió un error: " + err.message);
            })
            .finally(() => {
                btnConfirmarGuardar.disabled = false;
                spinnerConfirmar.classList.add("d-none");
            });
    });
});