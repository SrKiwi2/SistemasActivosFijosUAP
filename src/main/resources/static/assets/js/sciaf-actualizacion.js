/** Aviso de versión nueva para pestañas abiertas durante un despliegue. */
(function () {
    'use strict';

    const inicial = document.querySelector('meta[name="sciaf-version"]')?.content;
    if (!inicial) return;

    let nuevaVersion = null;
    let consultando = false;
    let pospuestoHasta = 0;
    const INTERVALO_MS = 20000;

    function banner() {
        let contenedor = document.getElementById('sciaf-aviso-actualizacion');
        if (contenedor) return contenedor;

        const estilos = document.createElement('style');
        estilos.id = 'sciaf-actualizacion-estilos';
        estilos.textContent = `
            #sciaf-aviso-actualizacion { position:fixed; left:50%; bottom:18px; transform:translateX(-50%);
                z-index:1100; width:min(680px,calc(100vw - 28px)); padding:14px 16px; border-radius:14px;
                background:#123c42; color:#fff; box-shadow:0 12px 35px rgba(0,0,0,.28);
                display:flex; align-items:center; gap:14px; flex-wrap:wrap; font:inherit; }
            #sciaf-aviso-actualizacion[hidden] { display:none !important; }
            #sciaf-aviso-actualizacion .sciaf-actualizacion-texto { flex:1 1 270px; line-height:1.35; }
            #sciaf-aviso-actualizacion small { display:block; opacity:.88; margin-top:3px; }
            #sciaf-aviso-actualizacion .sciaf-actualizacion-acciones { display:flex; gap:8px; flex-wrap:wrap; }
            #sciaf-aviso-actualizacion button { border:1px solid rgba(255,255,255,.7); border-radius:8px;
                padding:7px 11px; background:transparent; color:#fff; font-weight:700; cursor:pointer; }
            #sciaf-aviso-actualizacion .sciaf-actualizar-ahora { background:#fff; color:#123c42; }
            #sciaf-aviso-actualizacion button:focus-visible { outline:3px solid #fbbf24; outline-offset:2px; }
        `;
        document.head.appendChild(estilos);

        contenedor = document.createElement('aside');
        contenedor.id = 'sciaf-aviso-actualizacion';
        contenedor.setAttribute('role', 'status');
        contenedor.setAttribute('aria-live', 'polite');
        contenedor.innerHTML = `
            <div class="sciaf-actualizacion-texto"><strong>Hay una actualización del SCIAF</strong>
                <small>Termine y guarde los registros en curso. Algunos lotes y archivos adjuntos no se recuperan al recargar.</small>
            </div>
            <div class="sciaf-actualizacion-acciones">
                <button type="button" class="sciaf-actualizar-despues">Después</button>
                <button type="button" class="sciaf-actualizar-ahora">Actualizar ahora</button>
            </div>`;
        contenedor.querySelector('.sciaf-actualizar-despues').addEventListener('click', function () {
            pospuestoHasta = Date.now() + 5 * 60 * 1000;
            contenedor.hidden = true;
        });
        contenedor.querySelector('.sciaf-actualizar-ahora').addEventListener('click', async function () {
            if (!window.confirm('¿Ya terminó y guardó el registro en curso? El respaldo automático no recupera todos los lotes ni los archivos adjuntos seleccionados.')) return;
            const boton = this;
            boton.disabled = true;
            try {
                if (window.sciafPestanas && typeof window.sciafPestanas.guardarAhora === 'function') {
                    const respaldado = await window.sciafPestanas.guardarAhora();
                    if (!respaldado) {
                        window.alert('No se pudo respaldar el borrador en el servidor. Revise su sesión y guarde el registro antes de actualizar.');
                        return;
                    }
                }
                window.location.reload();
            } catch (_) {
                window.alert('No se pudo respaldar el borrador en el servidor. Revise su sesión y guarde el registro antes de actualizar.');
            } finally {
                boton.disabled = false;
            }
        });
        document.body.appendChild(contenedor);
        return contenedor;
    }

    async function verificar() {
        if (consultando || document.visibilityState === 'hidden') return;
        consultando = true;
        try {
            const respuesta = await fetch('/api/eventos/version', {
                cache: 'no-store', credentials: 'same-origin', headers: { Accept: 'application/json' }
            });
            if (!respuesta.ok) return;
            const datos = await respuesta.json();
            if (datos.version && datos.version !== inicial) nuevaVersion = datos.version;
            if (nuevaVersion && Date.now() >= pospuestoHasta) banner().hidden = false;
        } catch (_) { /* El servidor puede estar reiniciándose: se intenta de nuevo. */ }
        finally { consultando = false; }
    }

    window.addEventListener('load', verificar, { once: true });
    document.addEventListener('visibilitychange', function () {
        if (document.visibilityState === 'visible') verificar();
    });
    setInterval(verificar, INTERVALO_MS);
})();
