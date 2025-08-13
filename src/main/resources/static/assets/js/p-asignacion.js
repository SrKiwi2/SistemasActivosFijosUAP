/*
         * 
         * ASIGNACION DE ACTIVO
         * 
         */

document.addEventListener("DOMContentLoaded", () => {
    // para alertar que lso cmapos son olbigatorios
    const campos = [
        { name: "unidad", mensaje: "La unidad es obligatoria" },
        { name: "codigoFuncionario", mensaje: "El código de funcionario es obligatorio" },
        { name: "ci", mensaje: "El C.I. es obligatorio" },
        { name: "hr", mensaje: "El H.R. es obligatorio" },
        { name: "ubicacionActivo", mensaje: "La ubicación es obligatoria" },
        { name: "descripcionActivo", mensaje: "La descripción del activo es obligatoria" },
    ];

    // Helper: Null/undefined/'' => 'N/A'
    const nv = (v) => (v === null || v === undefined || (typeof v === 'string' && v.trim() === '')) ? 'N/A' : v;

    // Enlazar validación live
    campos.forEach(({ name, mensaje }) => {
        const input = document.querySelector(`input[name="${name}"]`);
        if (input) input.addEventListener("input", () => validarCampo(input, mensaje));
    });

    const btnPreGuardar = document.getElementById("btnPreGuardar");
    const btnConfirmarGuardar = document.getElementById("btnConfirmarGuardar");
    const spinnerConfirmar = document.getElementById("spinnerConfirmar");
    const contenidoConfirmacion = document.getElementById("contenidoConfirmacion");
    const confirmacionLoading = document.getElementById("confirmacionLoading");
    const listaResponsable = document.getElementById("listaResponsable");
    const listaAsignacion = document.getElementById("listaAsignacion");

    // Mostrar/ocultar estados del modal
    function setLoadingModal(isLoading) {
        confirmacionLoading.classList.toggle("d-none", !isLoading);
        contenidoConfirmacion.classList.toggle("d-none", isLoading);
    }

    // Construye HTML de las listas
    function renderConfirmacion(apiData, formVals) {
        // Responsable
        listaResponsable.innerHTML = `
        <li class="list-group-item d-flex justify-content-between"><span><i class="bi bi-person"></i> Nombre</span><span class="fw-semibold">${nv(apiData.per_nombres)} ${nv(apiData.per_ap_paterno)} ${nv(apiData.per_ap_materno)}</span></li>
        <li class="list-group-item d-flex justify-content-between"><span><i class="bi bi-credit-card-2-front"></i> CI</span><span class="fw-semibold">${nv(apiData.per_num_doc)}</span></li>
        <li class="list-group-item d-flex justify-content-between"><span><i class="bi bi-envelope"></i> Email</span><span class="fw-semibold">${nv(apiData.perd_email_personal)}</span></li>
        <li class="list-group-item d-flex justify-content-between"><span><i class="bi bi-gender-ambiguous"></i> Género</span><span class="fw-semibold">${nv(apiData.per_sexo)}</span></li>
        <li class="list-group-item d-flex justify-content-between"><span><i class="bi bi-building"></i> Oficina</span><span class="fw-semibold">${nv(apiData.eo_descripcion)}</span></li>
        <li class="list-group-item d-flex justify-content-between"><span><i class="bi bi-briefcase"></i> Cargo</span><span class="fw-semibold">${nv(apiData.p_descripcion)}</span></li>
        `;

        // Asignación (corrige etiqueta HR)
        listaAsignacion.innerHTML = `
        <li class="list-group-item d-flex justify-content-between"><span><i class="bi bi-hash"></i> Nº H.R.</span><span class="fw-semibold">${nv(formVals.hr)}</span></li>
        <li class="list-group-item d-flex justify-content-between"><span><i class="bi bi-diagram-3"></i> Unidad</span><span class="fw-semibold">${nv(formVals.unidad)}</span></li>
        <li class="list-group-item d-flex justify-content-between"><span><i class="bi bi-geo-alt"></i> Ubicación del activo</span><span class="fw-semibold">${nv(formVals.ubicacionActivo)}</span></li>
        <li class="list-group-item d-flex justify-content-between"><span><i class="bi bi-info-circle"></i> Descripción del activo</span><span class="fw-semibold">${nv(formVals.descripcionActivo)}</span></li>
        <li class="list-group-item d-flex justify-content-between"><span><i class="bi bi-person-badge"></i> Código funcionario</span><span class="fw-semibold">${nv(formVals.codigoFuncionario)}</span></li>
        <li class="list-group-item d-flex justify-content-between"><span><i class="bi bi-card-text"></i> CI (ingresado)</span><span class="fw-semibold">${nv(formVals.ci)}</span></li>
        `;
    }

    // Pre-guardar: valida, consulta API y muestra modal
    btnPreGuardar?.addEventListener("click", () => {
        let esValido = true;
        campos.forEach(({ name, mensaje }) => {
            const input = document.querySelector(`input[name="${name}"]`);
            validarCampo(input, mensaje);
            if (!input.value.trim()) esValido = false;
        });
        if (!esValido) return;

        const form = document.getElementById("formResponsable");
        const formVals = {
            codigoFuncionario: form.codigoFuncionario.value.trim(),
            ci: form.ci.value.trim(),
            unidad: form.unidad.value.trim(),
            hr: form.hr.value.trim(),
            ubicacionActivo: form.ubicacionActivo.value.trim(),
            descripcionActivo: form.descripcionActivo.value.trim()
        };

        // Ocultar primer modal
        const modalAsignacion = bootstrap.Modal.getInstance(document.getElementById("formularioModalAsignacionActivo"));
        modalAsignacion?.hide();

        // Pequeño delay para que no se superpongan animaciones
        setTimeout(() => {
            // Mostrar modal de confirmación con estado de carga
            setLoadingModal(true);
            const modalConfirmacion = new bootstrap.Modal(document.getElementById("modalConfirmacionDatos"));
            modalConfirmacion.show();
            btnPreGuardar.disabled = true;
            fetch("http://virtual.uap.edu.bo:7174/api/londraPost/v1/obtenerDatos", {
                method: "POST",
                headers: {
                    "Content-Type": "application/json",
                    key: "e73b1991c59a67fe182524e4d12da556136ced8a9da310c3af4c4efbde804a10",
                },
                body: JSON.stringify({ usuario: formVals.codigoFuncionario, contrasena: formVals.ci }),
            })
                .then(async (response) => {
                    if (!response.ok) {
                        const text = await response.text().catch(() => "");
                        throw new Error(text || `Error API (${response.status})`);
                    }
                    return response.json();
                })
                .then((data) => {
                    renderConfirmacion(data || {}, formVals);
                    setLoadingModal(false);
                })
                .catch((error) => {
                    setLoadingModal(false);
                    contenidoConfirmacion.innerHTML = `
                        <div class="alert alert-danger d-flex align-items-center" role="alert">
                        <i class="bi bi-x-octagon me-2"></i>
                        <div>Ocurrió un error consultando los datos del funcionario. ${error?.message ? `<br><small class="text-muted">${error.message}</small>` : ""}</div>
                        </div>`;
                    contenidoConfirmacion.classList.remove("d-none");
                    console.error(error);
                })
                .finally(() => {
                    btnPreGuardar.disabled = false;
                });
        }, 300);
    });

    // Confirmar y guardar: genera PDF
    btnConfirmarGuardar?.addEventListener("click", () => {
        const form = document.getElementById("formResponsable");
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
                const pdfModal = new bootstrap.Modal(document.getElementById("modalVisualizarPdf"));
                pdfModal.show();
                const confirmModal = bootstrap.Modal.getInstance(document.getElementById("modalConfirmacionDatos"));
                confirmModal?.hide();
            })
            .catch((error) => {
                alert("Ocurrió un error: " + error.message);
            })
            .finally(() => {
                btnConfirmarGuardar.disabled = false;
                spinnerConfirmar.classList.add("d-none");
            });
    });
});