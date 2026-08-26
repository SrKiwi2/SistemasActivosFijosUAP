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
