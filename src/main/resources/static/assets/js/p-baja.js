// ================ BAJA ACTIVO =====================
document.addEventListener("DOMContentLoaded", () => {
    // ----- Helpers -----
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

    const takeActivoShape = (payload) => {
        if (!payload) return {};
        if (payload.data && typeof payload.data === "object") return payload.data;
        return payload;
    };

    // ----- Refs formulario y modales -----
    const form = document.getElementById("formBajaActivos");
    const modalBajaEl = document.getElementById("formularioModalBajaActivo");
    const modalConfirmacionEl = document.getElementById("modalConfirmacionBaja");

    // Botones
    const btnPreConfirmar = document.getElementById("btnConfirmarBaja");           // abre confirmación
    const btnConfirmar = document.getElementById("btnConfirmarGuardarBaja");       // envía al backend
    const spinnerConfirmar = document.getElementById("spinnerConfirmarBaja");

    // Áreas del modal de confirmación
    const loading = document.getElementById("confirmacionLoadingBaja");
    const contenido = document.getElementById("contenidoConfirmacionBaja");
    const listaAdmin = document.getElementById("listaAdminBaja");
    const listaFuncionario = document.getElementById("listaFuncionarioBaja");
    const listaActivo = document.getElementById("listaActivoBaja");
    const listaCausa = document.getElementById("listaCausaBaja");
    const alertaConfirm = document.getElementById("confirmacionAlertaBaja");

    // ----- UI utils -----
    const setLoading = (v) => {
        loading.classList.toggle("d-none", !v);
        contenido.classList.toggle("d-none", v);
    };

    // Alert dinámico en el FORM (antes de abrir confirmación)
    function ensureFormAlert() {
        let el = document.getElementById("alertaBajaForm");
        if (!el) {
            el = document.createElement("div");
            el.id = "alertaBajaForm";
            el.className = "alert d-none";
            const body = modalBajaEl.querySelector(".modal-body");
            body && body.prepend(el);
        }
        return el;
    }

    function showFormAlert(msg, type = "warning") {
        const el = ensureFormAlert();
        el.className = `alert alert-${type} d-flex align-items-center`;
        el.innerHTML = `<i class="bi bi-exclamation-triangle me-2"></i><div>${esc(msg)}</div>`;
        el.classList.remove("d-none");
    }

    function hideFormAlert() {
        const el = ensureFormAlert();
        el.classList.add("d-none");
    }

    function markInvalid(names = []) {
        // Limpia estados previos
        form.querySelectorAll(".is-invalid").forEach((n) => n.classList.remove("is-invalid"));
        // Marca inválidos
        names.forEach((name) => {
            const input = form.querySelector(`[name="${name}"]`);
            if (input) input.classList.add("is-invalid");
        });
        // Enfoca primero
        if (names[0]) {
            const first = form.querySelector(`[name="${names[0]}"]`);
            first?.focus();
        }
    }

    function clearInvalid() {
        form.querySelectorAll(".is-invalid").forEach((n) => n.classList.remove("is-invalid"));
    }

    function abrirConfirmacion() {
        new bootstrap.Modal(modalConfirmacionEl, { backdrop: "static", keyboard: false }).show();
    }

    function ocultarBajaYAbrirConfirmacion(cb) {
        const inst = bootstrap.Modal.getInstance(modalBajaEl) || new bootstrap.Modal(modalBajaEl);
        if (modalBajaEl.classList.contains("show")) {
            const onHidden = () => {
                modalBajaEl.removeEventListener("hidden.bs.modal", onHidden);
                abrirConfirmacion();
                cb && cb();
            };
            modalBajaEl.addEventListener("hidden.bs.modal", onHidden);
            inst.hide();
        } else {
            abrirConfirmacion();
            cb && cb();
        }
    }

    // ----- Lectura y validación -----
    function leerValores() {
        return {
            fechaBaja: form.fechaBaja?.value || "",
            numeroDocumento: form.numeroDocumento?.value || "",
            codigoFuncionarioBaja: (form.codigoFuncionarioBaja?.value || "").trim(),
            ciFuncionarioBaja: (form.ciFuncionarioBaja?.value || "").trim(),
            codigoActivoBaja: (form.codigoActivoBaja?.value || "").trim(),
            causa: form.causa?.value || "",
            descripcionBaja: form.descripcionBaja?.value || "",
        };
    }

    function validarMinimo(vals) {
        const req = [
            vals.fechaBaja,
            vals.numeroDocumento,
            vals.codigoFuncionarioBaja,
            vals.ciFuncionarioBaja,
            vals.codigoActivoBaja,
            vals.causa,
        ];
        return req.every((v) => v && String(v).trim());
    }

    // ----- Render resumen -----
    function renderResumen(dataFunc, dataActivo, vals) {
        // Admin
        listaAdmin.innerHTML = `
        <li class="list-group-item d-flex justify-content-between">
          <span><i class="bi bi-calendar3"></i> Fecha de baja</span>
          <span class="fw-semibold">${esc(vals.fechaBaja) || "N/A"}</span>
        </li>
        <li class="list-group-item d-flex justify-content-between">
          <span><i class="bi bi-123"></i> N° de documento</span>
          <span class="fw-semibold">${esc(vals.numeroDocumento) || "N/A"}</span>
        </li>
      `;

        // Funcionario (API + ingresado)
        const f = takePersonaShape(dataFunc || {});
        listaFuncionario.innerHTML = `
        <li class="list-group-item d-flex justify-content-between"><span><i class="bi bi-person"></i> Nombre</span><span class="fw-semibold">${nv(f.per_nombres)} ${nv(f.per_ap_paterno)} ${nv(f.per_ap_materno)}</span></li>
        <li class="list-group-item d-flex justify-content-between"><span><i class="bi bi-credit-card-2-front"></i> CI</span><span class="fw-semibold">${nv(f.per_num_doc)}</span></li>
        <li class="list-group-item d-flex justify-content-between"><span><i class="bi bi-envelope"></i> Email</span><span class="fw-semibold">${nv(f.perd_email_personal)}</span></li>
        <li class="list-group-item d-flex justify-content-between"><span><i class="bi bi-building"></i> Oficina</span><span class="fw-semibold">${nv(f.eo_descripcion)}</span></li>
        <li class="list-group-item d-flex justify-content-between"><span><i class="bi bi-briefcase"></i> Cargo</span><span class="fw-semibold">${nv(f.p_descripcion)}</span></li>
        <li class="list-group-item d-flex justify-content-between"><span><i class="bi bi-person-vcard"></i> Código func. (ingresado)</span><span class="fw-semibold">${esc(vals.codigoFuncionarioBaja) || "N/A"}</span></li>
      `;

        // Activo (API + ingresado)
        const a = takeActivoShape(dataActivo || {});
        const ubic = a.oficinaTexto || a.oficinaNombre || "";
        listaActivo.innerHTML = `
        <li class="list-group-item d-flex justify-content-between"><span><i class="bi bi-upc-scan"></i> Código</span><span class="fw-semibold">${esc(vals.codigoActivoBaja) || "N/A"}</span></li>
        <li class="list-group-item d-flex justify-content-between align-items-start">
          <span><i class="bi bi-info-circle"></i> Descripción</span>
          <span class="fw-semibold text-wrap" style="max-width: 420px; white-space: normal; word-break: break-word;">${esc(a.descripcion || "-")}</span>
        </li>
        <li class="list-group-item d-flex justify-content-between align-items-start">
          <span><i class="bi bi-geo-alt"></i> Ubicación actual</span>
          <span class="fw-semibold text-wrap" style="max-width: 420px; white-space: normal; word-break: break-word;">${esc(ubic)}</span>
        </li>
      `;

        // Causa / Descripción
        listaCausa.innerHTML = `
        <li class="list-group-item d-flex justify-content-between">
          <span><i class="bi bi-exclamation-triangle"></i> Causa</span>
          <span class="fw-semibold">${esc(vals.causa) || "N/A"}</span>
        </li>
        <li class="list-group-item">
          <i class="bi bi-journal-text me-1"></i>
          <span class="text-wrap" style="white-space: normal; word-break: break-word;">${esc(vals.descripcionBaja) || "-"}</span>
        </li>
      `;
    }

    // Alert dentro del modal de confirmación
    function showApiWarningBaja(msg) {
        if (!alertaConfirm) return;
        alertaConfirm.innerHTML = `<i class="bi bi-exclamation-triangle me-1"></i> ${esc(msg)}`;
        alertaConfirm.classList.remove("d-none");
    }
    function clearApiWarningBaja() {
        alertaConfirm?.classList.add("d-none");
    }

    // ----- PRE-Confirmar: valida con API ANTES de abrir confirmación -----
    btnPreConfirmar?.addEventListener("click", async () => {
        hideFormAlert();
        clearInvalid();

        const vals = leerValores();

        // Validación mínima (HTML5 + visual)
        if (!validarMinimo(vals)) {
            form.classList.add("was-validated");
            showFormAlert("Completa todos los campos obligatorios marcados con *.");
            // Marca campos faltantes
            const faltantes = [];
            if (!vals.fechaBaja) faltantes.push("fechaBaja");
            if (!vals.numeroDocumento) faltantes.push("numeroDocumento");
            if (!vals.codigoFuncionarioBaja) faltantes.push("codigoFuncionarioBaja");
            if (!vals.ciFuncionarioBaja) faltantes.push("ciFuncionarioBaja");
            if (!vals.codigoActivoBaja) faltantes.push("codigoActivoBaja");
            if (!vals.causa) faltantes.push("causa");
            markInvalid(faltantes);
            return;
        }

        // Mixed content (si tu sitio es HTTPS y la API es HTTP)
        const apiUrl = "http://virtual.uap.edu.bo:7174/api/londraPost/v1/obtenerDatos";
        if (location.protocol === "https:" && apiUrl.startsWith("http:")) {
            showFormAlert("No se pudo consultar la API (mixed content). Expón la API en HTTPS o usa un proxy.", "danger");
            return;
        }

        // Estado de "cargando" en el botón
        const prevHtml = btnPreConfirmar.innerHTML;
        btnPreConfirmar.disabled = true;
        btnPreConfirmar.innerHTML = `<span class="spinner-border spinner-border-sm me-2" role="status" aria-hidden="true"></span>Verificando…`;

        const headers = {
            "Content-Type": "application/json",
            key: "e73b1991c59a67fe182524e4d12da556136ced8a9da310c3af4c4efbde804a10",
        };

        // Llamadas a API (Funcionario + Activo)
        const reqFuncionario = fetch(apiUrl, {
            method: "POST",
            headers,
            body: JSON.stringify({
                usuario: vals.codigoFuncionarioBaja,
                contrasena: vals.ciFuncionarioBaja,
            }),
        }).then(async (r) => {
            if (!r.ok) {
                const t = await r.text().catch(() => "");
                throw new Error(`Funcionario ${r.status}: ${t || "sin detalle"}`);
            }
            const j = await r.json().catch(() => ({}));
            return takePersonaShape(j);
        });

        const reqActivo = fetch(
            "/api/buscar-activo?codigo=" + encodeURIComponent(vals.codigoActivoBaja)
        ).then(async (r) => {
            if (!r.ok) {
                const t = await r.text().catch(() => "");
                throw new Error(`Activo ${r.status}: ${t || "no encontrado"}`);
            }
            const j = await r.json().catch(() => ({}));
            return takeActivoShape(j);
        });

        try {
            const [dataFunc, dataAct] = await Promise.allSettled([reqFuncionario, reqActivo]);

            // Verificaciones y bloqueos ANTES de abrir el modal:
            if (dataFunc.status !== "fulfilled" || !dataFunc.value || Object.keys(dataFunc.value).length === 0) {
                showFormAlert("No se encontró al funcionario con el Código y C.I. ingresados.", "danger");
                markInvalid(["codigoFuncionarioBaja", "ciFuncionarioBaja"]);
                return;
            }

            if (dataAct.status !== "fulfilled" || !dataAct.value || Object.keys(dataAct.value).length === 0) {
                showFormAlert("No se encontró el activo con el código ingresado.", "danger");
                markInvalid(["codigoActivoBaja"]);
                return;
            }

            // Si todo OK: abrimos confirmación ya con datos
            ocultarBajaYAbrirConfirmacion(() => {
                setLoading(true);
                clearApiWarningBaja();
                renderResumen(dataFunc.value, dataAct.value, vals);
                setLoading(false);
            });
        } catch (err) {
            console.error("Error en verificación previa:", err);
            showFormAlert("Ocurrió un error verificando los datos. Intenta nuevamente.", "danger");
        } finally {
            // restaurar botón
            btnPreConfirmar.disabled = false;
            btnPreConfirmar.innerHTML = prevHtml;
        }
    });

    // ----- Confirmar => POST + PDF -----
    btnConfirmar?.addEventListener("click", () => {
        const formData = new FormData(form);
        btnConfirmar.disabled = true;
        spinnerConfirmar.classList.remove("d-none");

        fetch("/baja/registro", { method: "POST", body: formData })
            .then((r) => {
                if (!r.ok) throw new Error("Error generando PDF");
                return r.blob();
            })
            .then((blob) => {
                const url = URL.createObjectURL(blob);
                document.getElementById("iframePDF").src = url;

                const pdfModal = new bootstrap.Modal(document.getElementById("modalVisualizarPdf"));
                pdfModal.show();

                bootstrap.Modal.getInstance(modalConfirmacionEl)?.hide();
            })
            .catch((err) => alert("Ocurrió un error: " + err.message))
            .finally(() => {
                btnConfirmar.disabled = false;
                spinnerConfirmar.classList.add("d-none");
            });
    });
});