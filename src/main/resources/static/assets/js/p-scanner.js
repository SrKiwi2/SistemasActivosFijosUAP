(() => {
    const btn = document.getElementById('btnScanPhoto');
    const file = document.getElementById('qrFile');

    // Modal & elementos
    const modalEl = document.getElementById('modalQrResultado');
    const qrRawModal = document.getElementById('qrRawModal');
    const qrParsedModal = document.getElementById('qrParsedModal');
    const btnCopiar = document.getElementById('btnCopiarQr');
    let bsModal = null;

    const modalErrEl = document.getElementById('modalQrError');
    const bsErrModal = modalErrEl ? bootstrap.Modal.getOrCreateInstance(modalErrEl) : null;
    const qrErrorMsgEl = document.getElementById('qrErrorMsg');
    const btnQrRetry = document.getElementById('btnQrRetry');

    // Para recordar quién tenía el foco antes de abrir el modal de error
    let lastFocusedEl = null;
    // Flag para disparar el reintento justo después que el modal termine de cerrarse
    let pendingRetry = false;

    function ensureModal() {
        if (!bsModal && modalEl) bsModal = bootstrap.Modal.getOrCreateInstance(modalEl);
    }

    // 1) Extraer el código útil del texto del QR
    //    Busca algo como 148-01-01-08-00488 o 01-01-08-00488 y devuelve SIN los 3 dígitos iniciales:
    //    -> 01-01-08-00488
    function extraerCodigoDesdeQR(text) {
        const t = String(text || '').trim();
        const m = t.match(/(\d{3}-)?\d{2}-\d{2}-\d{2}-\d{5}/);
        if (!m) return null;
        return m[0].replace(/^\d{3}-/, ''); // quita prefijo 3 dígitos si existe
    }

    // 2) Consultar tu API y renderizar “Responsable” en el modal
    async function consultarActivo(codigo) {
        if (!qrParsedModal) return;
        qrParsedModal.innerHTML = `
        <div class="d-flex align-items-center gap-2 text-muted">
          <div class="spinner-border spinner-border-sm" role="status"></div>
          <span>Buscando activo...</span>
        </div>
      `;
        try {
            const resp = await fetch(`/api/buscar-activo-responsable?codigo=${encodeURIComponent(codigo)}`);
            if (!resp.ok) {
                qrParsedModal.innerHTML = `<div class="alert alert-danger mb-0">Activo no encontrado.</div>`;
                return;
            }
            const data = await resp.json();
            qrParsedModal.innerHTML = `
          <ul class="list-group list-group-flush">
            <li class="list-group-item"><strong>Código:</strong> ${data.codigo}</li>
            <li class="list-group-item"><strong>Descripción:</strong> ${data.descripcion}</li>
            <li class="list-group-item"><strong>Oficina:</strong> ${data.oficinaNombre ?? 'N/A'}</li>
            <li class="list-group-item"><strong>Responsable:</strong> ${data.responsableNombre}</li>
          </ul>
        `;
        } catch (err) {
            console.error(err);
            qrParsedModal.innerHTML = `<div class="alert alert-danger mb-0">Error consultando el activo.</div>`;
        }
    }

    // 3) Decodificar imagen (modo foto)
    async function decodeFile(imgFile) {
        let decodedText;
        if (typeof Html5Qrcode !== 'undefined' && typeof Html5Qrcode.scanFile === 'function') {
            decodedText = await Html5Qrcode.scanFile(imgFile, true);
        } else {
            if (typeof Html5Qrcode === 'undefined') {
                alert('Error: la librería html5-qrcode no cargó.');
                return;
            }
            // Fallback: instancia temporal
            const tmpId = 'qrReaderTmp';
            let tmp = document.getElementById(tmpId);
            if (!tmp) {
                tmp = document.createElement('div');
                tmp.id = tmpId;
                tmp.style.display = 'none';
                document.body.appendChild(tmp);
            }
            const qr = new Html5Qrcode(tmpId);
            try {
                decodedText = await qr.scanFile(imgFile, false);
            } finally {
                try { await qr.clear(); } catch { }
            }
        }

        // Pinta contenido crudo
        if (qrRawModal) qrRawModal.textContent = decodedText ?? '';

        // Extrae el código útil y consulta
        const codigo = extraerCodigoDesdeQR(decodedText);
        if (!codigo) {
            if (qrParsedModal) {
                qrParsedModal.innerHTML = `<div class="alert alert-warning mb-0">No se encontró un código con el formato esperado.</div>`;
            }
        } else {
            await consultarActivo(codigo);
        }

        // Abre el modal (una sola vez)
        ensureModal();
        bsModal?.show();
    }

    function showQrError(msg) {
        if (qrErrorMsgEl) qrErrorMsgEl.textContent = msg || 'El QR está borroso o no es válido.';
        lastFocusedEl = document.activeElement;
        bsErrModal?.show();
    }

    modalErrEl?.addEventListener('shown.bs.modal', () => {
        // dale foco al botón Reintentar (tiene sentido para el usuario)
        btnQrRetry?.focus();
    });

    modalErrEl?.addEventListener('hidden.bs.modal', () => {
        if (pendingRetry) {
            pendingRetry = false;
            // Reabrir selector de imagen *después* de cerrar, evitando conflicto ARIA
            setTimeout(() => file?.click(), 0);
            return;
        }
        // Restaurar foco a un elemento seguro
        setTimeout(() => {
            (lastFocusedEl && typeof lastFocusedEl.focus === 'function' ? lastFocusedEl : btn || document.body).focus();
        }, 0);
    });

    btnQrRetry?.addEventListener('click', () => {
        pendingRetry = true;
        bsErrModal?.hide();
    });

    // Abrir cámara (foto)
    btn?.addEventListener('click', () => file?.click());



    // Al seleccionar imagen
    file?.addEventListener('change', async () => {
        if (!file.files?.length) return;
        const img = file.files[0];
        try {
            await decodeFile(img);
        } catch (e) {
            // console.error(e);
            showQrError('No se detectó un QR válido. Intenta nuevamente.');
        } finally {
            file.value = '';
        }
    });

    // Copiar contenido crudo al portapapeles
    btnCopiar?.addEventListener('click', async () => {
        const txt = qrRawModal?.textContent || '';
        try {
            await navigator.clipboard.writeText(txt);
            btnCopiar.innerHTML = '<i class="bi bi-clipboard-check"></i> Copiado';
            setTimeout(() => btnCopiar.innerHTML = '<i class="bi bi-clipboard-check"></i> Copiar', 1500);
        } catch {
            alert('No se pudo copiar. Copia manualmente desde el área de texto.');
        }
    });
})();  