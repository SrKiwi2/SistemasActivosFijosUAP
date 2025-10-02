(() => {
    // ====== TUS ELEMENTOS EXISTENTES ======
    const btnPhoto = document.getElementById('btnScanPhoto');
    const file = document.getElementById('qrFile');

    const modalResultadoEl = document.getElementById('modalQrResultado');
    const qrRawModal = document.getElementById('qrRawModal');
    const qrParsedModal = document.getElementById('qrParsedModal');
    const btnCopiar = document.getElementById('btnCopiarQr');
    let bsResultadoModal = modalResultadoEl ? bootstrap.Modal.getOrCreateInstance(modalResultadoEl) : null;

    const modalErrEl = document.getElementById('modalQrError');
    const bsErrModal = modalErrEl ? bootstrap.Modal.getOrCreateInstance(modalErrEl) : null;
    const qrErrorMsgEl = document.getElementById('qrErrorMsg');
    const btnQrRetry = document.getElementById('btnQrRetry');

    // ====== NUEVOS ELEMENTOS PARA LIVE ======
    const btnLive = document.getElementById('btnScanLive');
    const modalLiveEl = document.getElementById('modalQrLive');
    const bsLiveModal = modalLiveEl ? bootstrap.Modal.getOrCreateInstance(modalLiveEl) : null;
    const liveRegionId = 'qrLiveRegion';
    const liveStatusEl = document.getElementById('qrLiveStatus');
    const btnLiveStop = document.getElementById('btnLiveStop');
    const cameraSelect = document.getElementById('cameraSelect');
    const btnTorch = document.getElementById('btnTorch');

    // Estado live
    let liveQr = null;           // instancia Html5Qrcode
    let currentCameraId = null;  // id de cámara activa
    let lastFocusedEl = null;
    let pendingRetry = false;
    let decodeErrorCooldown = 0;
    let torchOn = false;

    // ====== Helpers (tus mismas funciones) ======
    function extraerCodigoDesdeQR(text) {
        const t = String(text || '').trim();
        const m = t.match(/(\d{3}-)?\d{2}-\d{2}-\d{2}-\d{5}/);
        if (!m) return null;
        return m[0].replace(/^\d{3}-/, '');
    }

    async function consultarActivo(codigo) {
        if (!qrParsedModal) return;
        qrParsedModal.innerHTML = `
      <div class="d-flex align-items-center gap-2 text-muted">
        <div class="spinner-border spinner-border-sm" role="status"></div>
        <span>Buscando activo...</span>
      </div>`;
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
        </ul>`;
        } catch (err) {
            console.error(err);
            qrParsedModal.innerHTML = `<div class="alert alert-danger mb-0">Error consultando el activo.</div>`;
        }
    }

    function showQrError(msg) {
        if (qrErrorMsgEl) qrErrorMsgEl.textContent = msg || 'El QR está borroso o no es válido.';
        lastFocusedEl = document.activeElement;
        bsErrModal?.show();
    }

    modalErrEl?.addEventListener('shown.bs.modal', () => btnQrRetry?.focus());
    modalErrEl?.addEventListener('hidden.bs.modal', () => {
        if (pendingRetry) {
            pendingRetry = false;
            setTimeout(() => file?.click(), 0);
            return;
        }
        setTimeout(() => {
            (lastFocusedEl && typeof lastFocusedEl.focus === 'function' ? lastFocusedEl : btnPhoto || document.body).focus();
        }, 0);
    });

    btnQrRetry?.addEventListener('click', () => {
        pendingRetry = true;
        bsErrModal?.hide();
    });

    // ====== Fallback: Foto/Archivo (lo tuyo) ======
    btnPhoto?.addEventListener('click', () => file?.click());
    file?.addEventListener('change', async () => {
        if (!file.files?.length) return;
        const img = file.files[0];
        try {
            let decodedText;
            if (typeof Html5Qrcode !== 'undefined' && typeof Html5Qrcode.scanFile === 'function') {
                decodedText = await Html5Qrcode.scanFile(img, true);
            } else {
                if (typeof Html5Qrcode === 'undefined') {
                    alert('Error: la librería html5-qrcode no cargó.');
                    return;
                }
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
                    decodedText = await qr.scanFile(img, false);
                } finally { try { await qr.clear(); } catch { } }
            }

            if (qrRawModal) qrRawModal.textContent = decodedText ?? '';
            const codigo = extraerCodigoDesdeQR(decodedText);
            if (!codigo) {
                if (qrParsedModal) {
                    qrParsedModal.innerHTML = `<div class="alert alert-warning mb-0">No se encontró un código con el formato esperado.</div>`;
                }
            } else {
                await consultarActivo(codigo);
            }

            bsResultadoModal?.show();
        } catch (e) {
            showQrError('No se detectó un QR válido. Intenta nuevamente.');
        } finally {
            file.value = '';
        }
    });

    // ====== LIVE SCAN ======
    // Selección de cámara (elige trasera si existe)
    function pickBackCamera(cameras) {
        if (!Array.isArray(cameras)) return null;
        const back = cameras.find(c =>
            /back|rear|environment/i.test(c.label || '') ||
            /back|rear|environment/i.test(c.id || '')
        );
        return back || cameras[0] || null;
    }

    async function populateCameraList() {
        cameraSelect.innerHTML = '';
        try {
            const devices = await Html5Qrcode.getCameras();
            if (!devices || !devices.length) {
                cameraSelect.innerHTML = `<option value="">(No hay cámaras)</option>`;
                return [];
            }
            devices.forEach(d => {
                const opt = document.createElement('option');
                opt.value = d.id;
                opt.textContent = d.label || d.id;
                cameraSelect.appendChild(opt);
            });
            return devices;
        } catch (e) {
            console.error(e);
            cameraSelect.innerHTML = `<option value="">(Error listando cámaras)</option>`;
            return [];
        }
    }

    async function startLiveScan(cameraId) {
        if (liveQr) {
            try { await liveQr.stop(); await liveQr.clear(); } catch { }
            liveQr = null;
        }
        if (!document.getElementById(liveRegionId)) return;

        liveQr = new Html5Qrcode(liveRegionId, { verbose: false });

        // Callbacks
        const onSuccess = async (decodedText/*, decodedResult*/) => {
            // Parar para que no dispare múltiples veces
            try { await liveQr.stop(); } catch { }
            try { await liveQr.clear(); } catch { }
            liveQr = null;

            if (qrRawModal) qrRawModal.textContent = decodedText ?? '';

            const codigo = extraerCodigoDesdeQR(decodedText);
            if (!codigo) {
                qrParsedModal.innerHTML = `<div class="alert alert-warning mb-0">No se encontró un código con el formato esperado.</div>`;
            } else {
                await consultarActivo(codigo);
            }

            // Cierra modal live y muestra resultado
            bsLiveModal?.hide();
            bsResultadoModal?.show();
        };

        const onError = (err) => {
            // Throttle de mensajes de error (ruidoso en dispositivos)
            const now = Date.now();
            if (now - decodeErrorCooldown > 1000) {
                liveStatusEl.textContent = 'Buscando QR…';
                decodeErrorCooldown = now;
            }
        };

        const config = {
            fps: 10,
            qrbox: { width: 250, height: 250 },
            aspectRatio: 1.7778,
            // facingMode sólo funciona si usas media constraints,
            // pero al pasar cameraId, html5-qrcode ya usa ese dispositivo.
        };

        liveStatusEl.textContent = 'Abriendo cámara…';

        try {
            await liveQr.start(
                { deviceId: { exact: cameraId } },
                config,
                onSuccess,
                onError
            );
            liveStatusEl.textContent = 'Apunta la cámara al código QR';
            currentCameraId = cameraId;
        } catch (e) {
            console.error(e);
            liveStatusEl.textContent = 'No se pudo iniciar la cámara';
            // Fallback: avisa y ofrece ir al modo foto/archivo
            showQrError('No se pudo acceder a la cámara. Prueba con “Escanear QR (foto)”.');
            try { await liveQr.clear(); } catch { }
            liveQr = null;
        }

        if (isTorchSupported()) {
            btnTorch?.classList.remove('d-none');
        } else {
            btnTorch?.classList.add('d-none');
        }
    }

    async function stopLiveScan() {
        if (!liveQr) return;
        try {
            liveStatusEl.textContent = 'Deteniendo…';
            await liveQr.stop();
            await liveQr.clear();
        } catch { }
        liveQr = null;
        liveStatusEl.textContent = 'Cámara detenida';
        torchOn = false;
        btnTorch?.classList.add('d-none');
    }

    btnTorch?.addEventListener('click', async () => {
        if (!isTorchSupported()) return;
        await setTorch(!torchOn);
    });


    function isTorchSupported() {
        try {
            // html5-qrcode expone info del track en tiempo de ejecución
            if (!liveQr?.isScanning) return false;
            // Preferir capacidades si existen
            const caps = liveQr.getRunningTrackCapabilities?.();
            if (caps && 'torch' in caps) return !!caps.torch;

            // Fallback: algunos dispositivos exponen 'torch' en settings
            const settings = liveQr.getRunningTrackSettings?.();
            return !!(settings && 'torch' in settings);
        } catch { return false; }
    }

    async function setTorch(on) {
        // Usa applyVideoConstraints de html5-qrcode
        // Ver: applyVideoConstraints({ advanced: [{ torch: true|false }] })
        try {
            await liveQr.applyVideoConstraints({ advanced: [{ torch: !!on }] });
            torchOn = !!on;
            // Actualiza etiqueta
            if (btnTorch) btnTorch.innerHTML = torchOn
                ? '<i class="bi bi-lightning-charge-fill"></i> Linterna ON'
                : '<i class="bi bi-lightning"></i> Linterna';
        } catch (e) {
            console.warn('No se pudo cambiar la linterna:', e);
        }
    }

    // Abrir modal live
    btnLive?.addEventListener('click', async () => {
        // Si no hay soporte mínimo, ir directo a archivo
        if (typeof Html5Qrcode === 'undefined' || !navigator.mediaDevices?.getUserMedia) {
            file?.click();
            return;
        }

        try {
            const cams = await Html5Qrcode.getCameras();
            if (!cams || cams.length === 0) {
                // Sin cámaras detectadas → fallback a archivo
                file?.click();
                return;
            }
            // Hay al menos una cámara → abrimos el modal live
            bsLiveModal?.show();
        } catch (e) {
            // Error enumerando cámaras (permisos denegados u otros) → fallback
            file?.click();
        }
    });

    // Al mostrar el modal live → listar cámaras y arrancar
    modalLiveEl?.addEventListener('shown.bs.modal', async () => {
        liveStatusEl.textContent = 'Inicializando cámara…';
        let cams = [];
        try {
            cams = await populateCameraList();
        } catch (e) {
            cams = [];
        }

        if (!cams.length) {
            // Nada de cámaras → cerrar modal y abrir archivo
            liveStatusEl.textContent = 'No se encontraron cámaras';
            bsLiveModal?.hide();
            setTimeout(() => file?.click(), 0);
            return;
        }

        // Selecciona la trasera si existe
        const back = pickBackCamera(cams);
        try {
            cameraSelect.value = (back ? back.id : cams[0].id);
            await startLiveScan(cameraSelect.value);
        } catch (e) {
            // Falló iniciar la cámara (permiso denegado u otro) → fallback
            bsLiveModal?.hide();
            setTimeout(() => file?.click(), 0);
        }
    });

    // Al ocultar el modal live → detener cámara
    modalLiveEl?.addEventListener('hide.bs.modal', async () => {
        await stopLiveScan();
    });

    // Cambiar de cámara
    cameraSelect?.addEventListener('change', async () => {
        const newId = cameraSelect.value;
        if (!newId || newId === currentCameraId) return;
        await startLiveScan(newId);
    });

    // Botón detener
    btnLiveStop?.addEventListener('click', async () => {
        await stopLiveScan();
    });

    // Copiar al portapapeles (tu flujo)
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