/* ══════════════════════════════════════════════════════════════════════════
   ESTADO DE CONEXIONES — indicador del topbar (solo administradores)

   Muestra si los montajes CIFS del VSIAF (/mnt/dbfwin, /mnt/vsiaf_transferencias)
   y la base de datos están respondiendo.

   El backend sondea cada 30 s por su cuenta; esta pantalla solo lee el último
   estado cacheado, así que refrescar es barato (no toca disco). Cuando el
   backend detecta un cambio de estado, empuja el evento SSE
   'estado-conexiones' — topbar.js lo reemite como evento del documento.
   ══════════════════════════════════════════════════════════════════════════ */
(function () {
    'use strict';

    const contenedor = document.getElementById('li-estado-conexiones');
    if (!contenedor) return;   // el usuario no es administrador: nada que hacer

    const API = {
        estado:    '/api/estado/conexiones',
        verificar: '/api/estado/conexiones/verificar'
    };

    /** Cada cuánto releemos el estado cacheado del backend. */
    const REFRESCO_MS = 60000;

    const dot        = document.getElementById('dot-conexiones');
    const icono      = document.getElementById('iconoConexiones');
    const lista      = document.getElementById('lista-conexiones');
    const resumen    = document.getElementById('conexiones-resumen');
    const verificado = document.getElementById('conexiones-verificado');
    const btnVerif   = document.getElementById('btn-verificar-conexiones');

    const ESTILO = {
        OK:        { punto: 'bg-success',   badge: 'bg-label-success',   texto: 'Todo conectado',       icono: 'ti-plug-connected'   },
        DEGRADADO: { punto: 'bg-warning',   badge: 'bg-label-warning',   texto: 'Con advertencias',     icono: 'ti-plug-connected'   },
        CAIDO:     { punto: 'bg-danger',    badge: 'bg-label-danger',    texto: 'Hay una caída',        icono: 'ti-plug-connected-x' },
        SIN_DATOS: { punto: 'bg-secondary', badge: 'bg-label-secondary', texto: 'Sin datos',            icono: 'ti-plug-connected'   }
    };

    let ultimoGlobal = null;

    // ══════════════════════════════════════════════════════════════════
    //  PINTADO
    // ══════════════════════════════════════════════════════════════════

    function pintarPunto(estadoGlobal) {
        const est = ESTILO[estadoGlobal] || ESTILO.SIN_DATOS;

        dot.className = 'position-absolute rounded-circle ' + est.punto;
        dot.classList.toggle('conexion-pulse', estadoGlobal === 'CAIDO');

        icono.className = 'ti ti-md ' + est.icono;

        const titulo = 'Estado de las conexiones: ' + est.texto;
        document.getElementById('btnConexiones').setAttribute('title', titulo);
    }

    function pintarLista(conexiones) {
        lista.innerHTML = '';

        if (!conexiones || !conexiones.length) {
            lista.innerHTML =
                '<li class="list-group-item text-center text-muted py-4">' +
                '<small>Sin información de conexiones</small></li>';
            return;
        }

        conexiones.forEach(c => {
            const est   = ESTILO[c.estado] || ESTILO.SIN_DATOS;
            const li    = document.createElement('li');
            li.className = 'list-group-item conexion-item est-' + c.estado + ' px-3 py-2';

            // Origen → ruta local (solo tiene sentido en los montajes CIFS)
            let destino = '';
            if (c.ruta) {
                destino = (c.origen ? esc(c.origen) + ' &rarr; ' : '') + esc(c.ruta);
            } else if (c.origen) {
                destino = esc(c.origen);
            }

            // Pie: latencia, último cambio del DBF centinela, hora de la sonda
            const pie = [];
            if (c.latenciaMs != null)  pie.push(c.latenciaMs + ' ms');
            if (c.ultimoCambio)        pie.push('último cambio ' + esc(c.ultimoCambio));
            if (c.verificadoEn)        pie.push('visto ' + esc(c.verificadoEn));

            li.innerHTML =
                '<div class="d-flex justify-content-between align-items-start">' +
                    '<div class="me-2 flex-grow-1">' +
                        '<div class="fw-semibold small">' + esc(c.nombre) +
                            '<span class="badge bg-label-secondary ms-1" style="font-size:.6rem">' +
                                esc(c.tipo) + '</span>' +
                        '</div>' +
                        (destino ? '<div class="conexion-ruta text-muted">' + destino + '</div>' : '') +
                        '<div class="conexion-meta text-muted mt-1">' + esc(c.detalle || '') + '</div>' +
                        (c.error
                            ? '<div class="conexion-meta text-danger mt-1">' +
                              '<i class="ti ti-alert-triangle ti-xs me-1"></i>' + esc(c.error) + '</div>'
                            : '') +
                    '</div>' +
                    '<span class="badge ' + est.badge + '" style="font-size:.65rem">' +
                        esc(c.estado) + '</span>' +
                '</div>' +
                (pie.length
                    ? '<div class="conexion-meta text-muted mt-1">' + pie.join(' · ') + '</div>'
                    : '');

            lista.appendChild(li);
        });
    }

    function pintar(data) {
        const global = data.estadoGlobal || 'SIN_DATOS';
        pintarPunto(global);
        pintarLista(data.conexiones);

        const caidas = (data.conexiones || []).filter(c => c.estado === 'CAIDO').length;
        const avisos = (data.conexiones || []).filter(c => c.estado === 'DEGRADADO').length;

        resumen.textContent = caidas
            ? caidas + (caidas === 1 ? ' conexión caída' : ' conexiones caídas')
            : (avisos ? avisos + (avisos === 1 ? ' advertencia' : ' advertencias')
                      : 'Todo funcionando');

        const primera = (data.conexiones || [])[0];
        verificado.textContent = 'Última verificación: ' +
            (primera && primera.verificadoEn ? primera.verificadoEn : '—');

        // Aviso discreto solo cuando el estado global empeora.
        if (ultimoGlobal && ultimoGlobal !== global && global === 'CAIDO') {
            avisar('Se perdió una conexión', 'Revisá el indicador del topbar.', 'error');
        } else if (ultimoGlobal === 'CAIDO' && global === 'OK') {
            avisar('Conexiones restablecidas', 'Todo volvió a la normalidad.', 'success');
        }
        ultimoGlobal = global;
    }

    // ══════════════════════════════════════════════════════════════════
    //  DATOS
    // ══════════════════════════════════════════════════════════════════

    function cargar(forzar) {
        const opciones = forzar ? { method: 'POST' } : {};
        const url      = forzar ? API.verificar : API.estado;

        if (forzar) marcarCargando(true);

        fetch(url, opciones)
            .then(r => {
                if (r.status === 403) {          // dejó de ser admin / sesión caída
                    contenedor.style.display = 'none';
                    throw new Error('sin permiso');
                }
                if (!r.ok) throw new Error('HTTP ' + r.status);
                return r.json();
            })
            .then(pintar)
            .catch(err => {
                if (err.message === 'sin permiso') return;
                pintarPunto('SIN_DATOS');
                resumen.textContent = 'No se pudo consultar el estado';
                lista.innerHTML =
                    '<li class="list-group-item text-center text-muted py-4">' +
                    '<small>El servidor no respondió (' + esc(err.message) + ')</small></li>';
            })
            .finally(() => { if (forzar) marcarCargando(false); });
    }

    function marcarCargando(cargando) {
        if (!btnVerif) return;
        btnVerif.disabled = cargando;
        btnVerif.innerHTML = cargando
            ? '<span class="spinner-border spinner-border-sm"></span>'
            : '<i class="ti ti-refresh ti-xs me-1"></i><span style="font-size:.75rem">Verificar</span>';
    }

    function avisar(titulo, texto, icono) {
        if (typeof Swal === 'undefined') return;
        Swal.fire({
            toast: true, position: 'bottom-end', icon: icono,
            title: titulo, text: texto,
            showConfirmButton: false, timer: 6000, timerProgressBar: true
        });
    }

    function esc(str) {
        return String(str == null ? '' : str)
            .replace(/&/g, '&amp;').replace(/</g, '&lt;')
            .replace(/>/g, '&gt;').replace(/"/g, '&quot;');
    }

    // ══ Monitor de la cola VFPOLEDB (solo administradores) ═════════════
    const btnCola = document.getElementById('btn-monitor-cola-vsiaf');
    let monitorCola = null;
    let paginaCola = 0;
    let paginasCola = 0;
    let versionCola = 0;
    const ESTADOS_COLA = {
        ENCOLADA: ['bg-label-warning', 'En cola'],
        OK: ['bg-label-success', 'Confirmada'],
        ERROR: ['bg-label-danger', 'Con error'],
        REINTENTADA: ['bg-label-info', 'Reintentada']
    };

    function crearMonitorCola() {
        const nodo = document.createElement('div');
        nodo.className = 'modal fade';
        nodo.id = 'monitor-cola-vsiaf';
        nodo.tabIndex = -1;
        nodo.setAttribute('aria-labelledby', 'monitor-cola-titulo');
        nodo.setAttribute('aria-hidden', 'true');
        nodo.innerHTML = `
          <div class="modal-dialog modal-dialog-centered sm-detalle-dialog">
            <div class="modal-content sm-modal sm-detalle-content">
              <div class="sm-detalle-head">
                <div class="sm-cabecera-icono"><i class="ti ti-list-details"></i></div>
                <div class="flex-grow-1"><span class="sm-detalle-kicker">Supervisión · VSIAF</span>
                  <h5 class="sm-modal-titulo" id="monitor-cola-titulo">Órdenes enviadas al worker</h5>
                  <small>Estado de la cola, confirmaciones y errores del worker VFPOLEDB</small></div>
                <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Cerrar"></button>
              </div>
              <div class="sm-detalle-scroll">
                <div class="sm-aviso mb-3" id="cola-worker-estado" role="status">Consultando actividad del worker…</div>
                <div class="row g-2 mb-3" id="cola-contadores"></div>
                <div class="d-flex gap-2 align-items-end flex-wrap mb-3">
                  <label class="sm-label mb-0">Mostrar
                    <select class="sm-select d-block" id="cola-filtro" aria-label="Filtrar órdenes por estado">
                      <option value="TODAS">Todas</option><option value="ENCOLADA">En cola</option>
                      <option value="ERROR">Con error</option><option value="OK">Confirmadas</option>
                      <option value="REINTENTADA">Reintentadas</option>
                    </select></label>
                  <button type="button" class="sm-btn sm-btn-sm sm-btn-neutro" id="cola-actualizar"><i class="ti ti-refresh"></i> Actualizar</button>
                  <small class="text-muted ms-auto" id="cola-total">—</small>
                </div>
                <div class="table-responsive" style="max-height:48vh;overflow:auto">
                  <table class="table sm-tabla table-sm align-middle mb-0"><thead><tr>
                    <th>Orden / fecha</th><th>Tabla y operación</th><th>Referencia</th><th>Estado</th><th>Resultado del worker</th>
                  </tr></thead><tbody id="cola-filas"><tr><td colspan="5" class="text-center py-4">Cargando órdenes…</td></tr></tbody></table>
                </div>
              </div>
              <div class="sm-detalle-footer">
                <span class="sm-detalle-hint" id="cola-pagina">—</span>
                <button type="button" class="sm-btn sm-btn-sm sm-btn-neutro" id="cola-anterior" disabled><i class="ti ti-chevron-left"></i> Anterior</button>
                <button type="button" class="sm-btn sm-btn-sm sm-btn-neutro" id="cola-siguiente" disabled>Siguiente <i class="ti ti-chevron-right"></i></button>
                <button type="button" class="sm-btn sm-btn-sm" data-bs-dismiss="modal">Cerrar</button>
              </div>
            </div>
          </div>`;
        document.body.appendChild(nodo);
        nodo.querySelector('#cola-filtro').addEventListener('change', () => { paginaCola = 0; cargarOrdenesCola(); });
        nodo.querySelector('#cola-actualizar').addEventListener('click', () => cargarMonitorCola());
        nodo.querySelector('#cola-anterior').addEventListener('click', () => { if (paginaCola > 0) { paginaCola--; cargarOrdenesCola(); } });
        nodo.querySelector('#cola-siguiente').addEventListener('click', () => { if (paginaCola + 1 < paginasCola) { paginaCola++; cargarOrdenesCola(); } });
        return nodo;
    }

    async function cargarDiagnosticoCola() {
        const estado = monitorCola.querySelector('#cola-worker-estado');
        estado.textContent = 'Consultando actividad del worker…';
        try {
            const r = await fetch('/api/estado/cola-vsiaf', { credentials: 'same-origin' });
            if (!r.ok) throw new Error('No se pudo consultar el worker (HTTP ' + r.status + ').');
            const data = await r.json();
            const worker = data.worker || {};
            const base = data.base || {};
            const esperaMinutos = Number(base.esperaMasLargaMinutos || 0);
            const demorada = Number(base.encoladas || 0) > 0 && esperaMinutos >= 2;
            estado.className = 'sm-aviso mb-3 ' + ((worker.estado === 'AL_DIA' || worker.estado === 'TRABAJANDO') && !demorada ? 'sm-aviso-exito' : 'sm-aviso-peligro');
            estado.innerHTML = '<i class="ti ti-cpu me-2"></i><strong>' + esc(worker.estado || 'Sin datos') + '</strong> · ' + esc(worker.detalle || 'Sin diagnóstico') +
                (demorada ? '<div class="small mt-1"><i class="ti ti-alert-triangle me-1"></i>La orden más antigua espera ' + esc(esperaMinutos) + ' min. Revise su archivo y el registro del worker.</div>' : '') +
                (worker.ultimaActividad ? '<div class="small mt-1">Última actividad: ' + esc(String(worker.ultimaActividad).replace('T', ' ')) + '</div>' : '');
            monitorCola.querySelector('#cola-contadores').innerHTML = [
                ['En cola', base.encoladas, 'ti-clock'], ['Confirmadas', base.confirmadas, 'ti-circle-check'],
                ['Con error', base.conError, 'ti-alert-triangle']
            ].map(([nombre, valor, icono]) => '<div class="col-4"><div class="sm-detalle-panel p-3 h-100"><small class="sm-detalle-label"><i class="ti ' + icono + '"></i> ' + nombre + '</small><strong class="fs-4 d-block">' + esc(valor ?? '—') + '</strong></div></div>').join('');
        } catch (e) {
            estado.className = 'sm-aviso sm-aviso-peligro mb-3';
            estado.textContent = e.message;
        }
    }

    async function cargarOrdenesCola() {
        const version = ++versionCola;
        const cuerpo = monitorCola.querySelector('#cola-filas');
        cuerpo.innerHTML = '<tr><td colspan="5" class="text-center py-4">Cargando órdenes…</td></tr>';
        try {
            const filtro = monitorCola.querySelector('#cola-filtro').value;
            const r = await fetch('/api/estado/cola-vsiaf/ordenes?estado=' + encodeURIComponent(filtro) + '&pagina=' + paginaCola, { credentials: 'same-origin' });
            if (!r.ok) throw new Error('No se pudieron consultar las órdenes (HTTP ' + r.status + ').');
            const data = await r.json();
            if (version !== versionCola) return;
            paginaCola = data.pagina;
            paginasCola = data.paginas;
            monitorCola.querySelector('#cola-total').textContent = data.total + ' orden(es)';
            monitorCola.querySelector('#cola-pagina').textContent = data.total ? 'Página ' + (paginaCola + 1) + ' de ' + paginasCola : 'Sin órdenes';
            monitorCola.querySelector('#cola-anterior').disabled = paginaCola === 0;
            monitorCola.querySelector('#cola-siguiente').disabled = paginaCola + 1 >= paginasCola;
            cuerpo.innerHTML = data.ordenes.length ? data.ordenes.map(o => {
                const estado = ESTADOS_COLA[o.estado] || ['bg-label-secondary', o.estado || '—'];
                return '<tr><td><strong>#' + esc(o.id) + '</strong><small class="d-block text-muted">' + esc(String(o.fechaEncolado || '—').replace('T', ' ')) + '</small></td>' +
                    '<td><strong>' + esc(o.tabla || '—') + '</strong><small class="d-block text-muted">' + esc(o.operacion || '—') + '</small></td>' +
                    '<td>' + esc(o.referencia || o.clave || '—') + '<small class="d-block text-muted">' + esc(o.usuario || '—') + '</small></td>' +
                    '<td><span class="badge ' + estado[0] + '">' + esc(estado[1]) + '</span></td>' +
                    '<td style="min-width:220px;white-space:normal;overflow-wrap:anywhere">' + esc(o.mensaje || (o.estado === 'ERROR' ? 'Sin detalle del error' : '—')) +
                    (o.fechaResuelto ? '<small class="d-block text-muted">Resuelto: ' + esc(String(o.fechaResuelto).replace('T', ' ')) + '</small>' : '') +
                    '<details class="small mt-1"><summary>Datos de la orden</summary><div>Archivo: ' + esc(o.archivo || '—') +
                    '</div><div>Clave: ' + esc(o.clave || '—') + '</div><div>Reintentos: ' + esc(o.intentos ?? 0) + '</div></details></td></tr>';
            }).join('') : '<tr><td colspan="5" class="text-center text-muted py-4">No hay órdenes con este estado.</td></tr>';
        } catch (e) {
            if (version === versionCola) cuerpo.innerHTML = '<tr><td colspan="5" class="text-center text-danger py-4">' + esc(e.message) + '</td></tr>';
        }
    }

    function cargarMonitorCola() {
        if (!monitorCola) return;
        cargarDiagnosticoCola();
        cargarOrdenesCola();
    }
    if (btnCola) btnCola.addEventListener('click', () => {
        if (!monitorCola) monitorCola = crearMonitorCola();
        bootstrap.Dropdown.getOrCreateInstance(document.getElementById('btnConexiones')).hide();
        bootstrap.Modal.getOrCreateInstance(monitorCola).show();
        cargarMonitorCola();
    });
    if (window.jQuery) $(document).on('sciaf:cola-vsiaf.monitor', () => {
        if (monitorCola && monitorCola.classList.contains('show')) cargarMonitorCola();
    });

    // ══════════════════════════════════════════════════════════════════
    //  ARRANQUE
    // ══════════════════════════════════════════════════════════════════

    cargar(false);
    setInterval(() => cargar(false), REFRESCO_MS);

    if (btnVerif) btnVerif.addEventListener('click', e => {
        e.stopPropagation();      // que no cierre el dropdown
        cargar(true);
    });

    // Push del backend cuando una conexión cambia de estado
    // (topbar.js reemite el evento SSE como evento del documento).
    document.addEventListener('sciaf:estado-conexiones', () => cargar(false));
})();
