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
    const btnPreConfirmar = document.getElementById("btnConfirmarBaja");          // abre confirmación
    const btnConfirmar = document.getElementById("btnConfirmarGuardarBaja");      // envía al backend
    const spinnerConfirmar = document.getElementById("spinnerConfirmarBaja");

    // Áreas del modal de confirmación
    const loading = document.getElementById("confirmacionLoadingBaja");
    const contenido = document.getElementById("contenidoConfirmacionBaja");
    const listaAdmin = document.getElementById("listaAdminBaja");
    const listaFuncionario = document.getElementById("listaFuncionarioBaja");
    const listaActivo = document.getElementById("listaActivoBaja");
    const listaCausa = document.getElementById("listaCausaBaja");
    const alerta = document.getElementById("confirmacionAlertaBaja");

    // ----- UI utils -----
    const setLoading = (v) => {
        loading.classList.toggle("d-none", !v);
        contenido.classList.toggle("d-none", v);
    };
    function showApiWarningBaja(msg) {
        if (!alerta) return;
        alerta.innerHTML = `<i class="bi bi-exclamation-triangle me-1"></i> ${esc(msg || "Error")}`;
        alerta.classList.remove("d-none");
    }
    function clearApiWarningBaja() {
        alerta?.classList.add("d-none");
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

    // ----- PRE-Confirmar: abre modal de confirmación, consulta APIs y arma resumen -----
    btnPreConfirmar?.addEventListener("click", () => {
        ocultarBajaYAbrirConfirmacion(async () => {
            setLoading(true);
            clearApiWarningBaja();

            const vals = leerValores();

            // Validación mínima
            if (!validarMinimo(vals)) {
                renderResumen({}, {}, vals);
                showApiWarningBaja("Faltan datos obligatorios o hay formato inválido.");
                setLoading(false);
                return;
            }

            // Mixed content (si tu sitio es HTTPS y la API es HTTP)
            const apiUrl = "http://virtual.uap.edu.bo:7174/api/londraPost/v1/obtenerDatos";
            if (location.protocol === "https:" && apiUrl.startsWith("http:")) {
                renderResumen({}, {}, vals);
                showApiWarningBaja(
                    "El navegador bloqueó la consulta (mixed content). Usa la API por HTTPS o un proxy en tu backend."
                );
                setLoading(false);
                return;
            }

            const headers = {
                "Content-Type": "application/json",
                key: "e73b1991c59a67fe182524e4d12da556136ced8a9da310c3af4c4efbde804a10",
            };

            // Consultas (Funcionario + Activo)
            const fetchFuncionario = fetch(apiUrl, {
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

            const fetchActivo = fetch(
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
                const [dataFunc, dataAct] = await Promise.all([fetchFuncionario, fetchActivo]);

                if (!dataFunc || Object.keys(dataFunc).length === 0) {
                    showApiWarningBaja("No se encontró información del funcionario con el código/CI ingresados.");
                }
                if (!dataAct || Object.keys(dataAct).length === 0) {
                    showApiWarningBaja("No se encontró el activo con el código ingresado.");
                }

                renderResumen(dataFunc || {}, dataAct || {}, vals);
            } catch (err) {
                console.error("Error consultando APIs:", err);
                renderResumen({}, {}, vals);
                showApiWarningBaja("No fue posible consultar los datos (ver consola).");
            } finally {
                setLoading(false);
            }
        });
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