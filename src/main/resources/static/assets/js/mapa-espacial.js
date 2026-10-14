(function () {
  'use strict';
  var BASE = '/administracion/mapa-espacial/api';
  var ZOOM_MIN = .1, ZOOM_MAX = 32;
  var FIGURAS = ['MESA', 'SILLA', 'EQUIPO', 'ESTANTE', 'ARCHIVADOR', 'OTRO'];
  var ICONOS = ['CPU', 'MONITOR', 'MESA', 'SILLA', 'ROUTER', 'AIRE', 'CAMARA', 'IMPRESORA', 'PORTATIL', 'PROYECTOR', 'ESTANTE', 'ARCHIVADOR', 'BIOMETRICO', 'TELEFONO', 'ESCANER', 'UPS', 'TELEVISOR', 'PIZARRA', 'VENTILADOR', 'EXTINTOR', 'OTRO'];
  var NOMBRES_ICONO = { CPU: 'CPU / torre', MONITOR: 'Monitor', MESA: 'Mesa / escritorio', SILLA: 'Silla', ROUTER: 'Router / red', AIRE: 'Aire acondicionado', CAMARA: 'Cámara', IMPRESORA: 'Impresora', PORTATIL: 'Portátil', PROYECTOR: 'Proyector', ESTANTE: 'Estante', ARCHIVADOR: 'Archivador', BIOMETRICO: 'Lector biométrico', TELEFONO: 'Teléfono', ESCANER: 'Escáner', UPS: 'UPS / respaldo', TELEVISOR: 'Televisor', PIZARRA: 'Pizarra', VENTILADOR: 'Ventilador', EXTINTOR: 'Extintor', OTRO: 'Genérico' };
  var TRAZOS_ICONO = {
    CPU: 'M6 2h12v20H6z M9 6h6 M9 10h6 M12 17h.01', MONITOR: 'M3 4h18v13H3z M8 21h8 M12 17v4',
    MESA: 'M2 8h20v5H2z M5 13v8 M19 13v8', SILLA: 'M5 3v8h14V3 M5 11v5h14v-5 M7 16v5 M17 16v5',
    ROUTER: 'M3 13h18v7H3z M7 13V7 M17 13V7 M5 4l3 3 M19 4l-3 3 M7 17h.01 M11 17h.01',
    AIRE: 'M2 5h20v9H2z M5 10h14 M6 17c2-2 4-2 6 0 M12 17c2-2 4-2 6 0',
    CAMARA: 'M2 8h14v9H2z M16 11l6-4v11l-6-4 M7 17v4 M4 21h9',
    IMPRESORA: 'M6 3h12v5H6z M4 8h16a2 2 0 0 1 2 2v6h-4v-6a2 2 0 0 1 2-2z M6 14h12v7H6z M16 11h.01',
    PORTATIL: 'M5 4h14v12H5z M2 20h20l-3-4H5z', PROYECTOR: 'M3 8h18v9H3z M8 12h.01 M14 12a2 2 0 1 0 4 0a2 2 0 1 0-4 0 M6 17v4 M18 17v4',
    ESTANTE: 'M4 2v20 M20 2v20 M4 8h16 M4 15h16 M8 3v5 M13 9v6 M17 16v6',
    ARCHIVADOR: 'M4 3h16v18H4z M4 9h16 M4 15h16 M10 6h4 M10 12h4 M10 18h4',
    BIOMETRICO: 'M5 2h14v20H5z M8 6h8v5H8z M9 15c0-2 6-2 6 0 M9 18c0-3 6-3 6 0 M12 14v5',
    TELEFONO: 'M7 2h10v20H7z M10 5h4 M10 18h4',
    ESCANER: 'M3 9h18v11H3z M6 5h12l2 4H4z M7 14h10 M7 17h10',
    UPS: 'M6 2h12v20H6z M9 6h6 M9 12l3-3v3h3l-3 4v-3H9z',
    TELEVISOR: 'M2 4h20v14H2z M8 22h8 M12 18v4 M6 8h12',
    PIZARRA: 'M3 3h18v15H3z M8 22h8 M12 18v4 M6 14h6 M6 11l3-3 3 2',
    VENTILADOR: 'M12 10a2 2 0 1 0 0 4a2 2 0 1 0 0-4 M12 10c-4-7 5-9 4-2 M14 13c8-1 8 8 1 6 M10 13c-6 6-10-2-3-6 M12 14v8 M8 22h8',
    EXTINTOR: 'M8 7h8v14H8z M9 7V4h6v3 M12 4V2h5 M17 2l3 2 M9 11h6 M10 21h4',
    OTRO: 'M12 2l9 5v10l-9 5l-9-5V7z M8 9h8 M8 13h8'
  };
  function sugerirIcono(nombre) {
    var t = String(nombre || '').normalize('NFD').replace(/[\u0300-\u036f]/g, '').toUpperCase();
    if (/BIOMETR|HUELLA|CONTROL DE ACCESO/.test(t)) return 'BIOMETRICO';
    if (/EXTINTOR/.test(t)) return 'EXTINTOR';
    if (/VENTILADOR/.test(t)) return 'VENTILADOR';
    if (/PIZARRA/.test(t)) return 'PIZARRA';
    if (/TELEVISOR|TV LED|SMART TV/.test(t)) return 'TELEVISOR';
    if (/TELEFONO|CELULAR|MOVIL/.test(t)) return 'TELEFONO';
    if (/ESCANER|SCANNER|DIGITALIZADOR/.test(t)) return 'ESCANER';
    if (/UPS|NO BREAK|REGULADOR DE VOLTAJE/.test(t)) return 'UPS';
    if (/CAMARA|CCTV|VIDEOVIGILANCIA/.test(t)) return 'CAMARA';
    if (/AIRE ACONDICIONADO|CLIMATIZADOR|SPLIT/.test(t)) return 'AIRE';
    if (/ROUTER|SWITCH|PUNTO DE ACCESO|ACCESS POINT|MODEM|RED/.test(t)) return 'ROUTER';
    if (/IMPRESORA|MULTIFUNCIONAL|PLOTTER/.test(t)) return 'IMPRESORA';
    if (/PROYECTOR|DATA SHOW/.test(t)) return 'PROYECTOR';
    if (/PORTATIL|LAPTOP|NOTEBOOK/.test(t)) return 'PORTATIL';
    if (/MONITOR|PANTALLA|DISPLAY/.test(t)) return 'MONITOR';
    if (/CPU|COMPUTADOR|ORDENADOR|SERVIDOR|TORRE/.test(t)) return 'CPU';
    if (/SILLA|SILLON|BUTACA/.test(t)) return 'SILLA';
    if (/MESA|ESCRITORIO|MESON/.test(t)) return 'MESA';
    if (/ARCHIVADOR|GAVETERO/.test(t)) return 'ARCHIVADOR';
    if (/ESTANTE|ESTANTERIA|LIBRERO/.test(t)) return 'ESTANTE';
    return 'OTRO';
  }
  function zoomDelControl(valor) { return ZOOM_MIN * Math.pow(ZOOM_MAX / ZOOM_MIN, Number(valor) / 1000); }
  function valorDelZoom(zoom) { return Math.round(1000 * Math.log(zoom / ZOOM_MIN) / Math.log(ZOOM_MAX / ZOOM_MIN)); }
  function esc(s) { return String(s == null ? '' : s).replace(/[&<>"']/g, function (c) { return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]; }); }
  function uid() { return 'm' + Date.now().toString(36) + Math.random().toString(36).slice(2, 10); }
  function request(url, options) {
    return fetch(url, Object.assign({ credentials: 'same-origin', headers: { 'Accept': 'application/json' } }, options || {})).then(function (r) {
      if (!r.ok) return r.text().then(function (t) { throw new Error(r.status === 409 ? 'Otro usuario cambió el mapa. Recarga el predio antes de guardar.' : (r.status === 403 ? 'No tienes permiso para editar el mapa.' : (t.slice(0, 180) || 'Error ' + r.status))); });
      return r.json();
    });
  }
  function svg(tag, attrs, text) {
    var n = document.createElementNS('http://www.w3.org/2000/svg', tag);
    Object.keys(attrs || {}).forEach(function (k) { n.setAttribute(k, attrs[k]); });
    if (text != null) n.textContent = text;
    return n;
  }
  function caja(puntos) {
    var xs = puntos.map(function (p) { return Number(p.x); }), ys = puntos.map(function (p) { return Number(p.y); });
    var x = Math.min.apply(null, xs), y = Math.min.apply(null, ys);
    return { x: x, y: y, ancho: Math.max.apply(null, xs) - x, fondo: Math.max.apply(null, ys) - y };
  }
  function puntosRect(o) {
    return [{ x: o.x, y: o.y }, { x: o.x + o.ancho, y: o.y },
      { x: o.x + o.ancho, y: o.y + o.fondo }, { x: o.x, y: o.y + o.fondo }];
  }
  function normalizarGeometria(escena) {
    (escena.edificios || []).forEach(function (b) {
      if (!Array.isArray(b.puntos) || b.puntos.length < 3) b.puntos = puntosRect(b);
      (b.pisos || []).forEach(function (p) {
        if (!isFinite(Number(p.altura)) || Number(p.altura) < 1) p.altura = 4;
        if (!Array.isArray(p.trazos)) p.trazos = [];
        (p.espacios || []).forEach(function (r) {
          if (!Array.isArray(r.puntos) || r.puntos.length < 3) r.puntos = puntosRect(r);
          if (!Array.isArray(r.cuartos)) r.cuartos = [];
          if (!Array.isArray(r.mobiliario)) r.mobiliario = [];
          if (!Array.isArray(r.activos)) r.activos = [];
          r.cuartos.forEach(function (c) { if (!Array.isArray(c.puntos) || c.puntos.length < 3) c.puntos = puntosRect(c); });
          r.activos.forEach(function (z) { if (!z.figura || FIGURAS.indexOf(z.figura) < 0) z.figura = 'OTRO'; });
        });
      });
      if (b.pisos && b.pisos.length) b.altura = redondear(b.pisos.reduce(function (total, p) { return total + Number(p.altura); }, 0));
    });
    return escena;
  }
  function redondear(n) { return Math.round(n * 10) / 10; }
  function redondearFino(n) { return Math.round(n * 10000) / 10000; }
  function contienePunto(puntos, x, y) {
    var dentro = false;
    for (var i = 0, j = puntos.length - 1; i < puntos.length; j = i++) {
      var a = puntos[i], b = puntos[j];
      if (((a.y > y) !== (b.y > y)) && x < (b.x - a.x) * (y - a.y) / (b.y - a.y) + a.x) dentro = !dentro;
    }
    return dentro;
  }
  function iniciar(root) {
    if (!root || root.dataset.iniciado) return;
    root.dataset.iniciado = '1';
    var edita = root.dataset.modo === 'diseno';
    var sel = root.querySelector('.me-predio'), canvas = root.querySelector('.me-canvas');
    var panel = root.querySelector('.me-panel-contenido'), estado = root.querySelector('.me-estado');
    var filtro = root.querySelector('.me-filtro');
    var s = { predioId: null, escena: { edificios: [] }, version: -1, modoEdicion: edita, puedePublicar: false, numeroPublicacion: 0, oficinas: {}, activos: {}, iconosAuxiliares: {}, iconoRevision: {}, edificio: null, piso: null, espacio: null, vista3d: false, cargaVista3d: 0, dirty: false, q: '', inventario: null, consultaInventario: 0,
      historial: [], indice: 0, guardado: '', modo: '', dibujo: [], tipoDibujo: 'EDIFICIO', editarEdificio: null, editarEspacio: null, vertice: -1,
      snap: false, cuadricula: true, capas: { edificios: true, espacios: true, detalles: true, fondo: true },
      zoom: 1, panX: 0, panY: 0, camara: 38, inclinacion: 38, techo3d: true, tieneFondo: false, fondoRevision: 0, detallesCargados: {}, cargaEdificio: 0, espacioPulsado: false, enfoqueInicial: false, marco3dFijo: null, cuartoSeleccionado: null, muebleSeleccionado: null, detallesActivos: {}, destacadoEdificio: null, destacados: {}, ubicacionDestacada: null, busquedaSeleccionada: null, resultadosBusqueda: [], panelTab: 'editar', panelSelectionKey: '', filtroConstrucciones: '', publicacionEnCarga: 0 };
    function avisar(msg, error) { if (estado) { estado.textContent = msg; estado.className = 'sm-dato me-estado ' + (error ? 'me-status-error' : 'me-status-ok'); } else if (error) panel.insertAdjacentHTML('afterbegin', '<p class="me-status-error">' + esc(msg) + '</p>'); }
    function activarPanel(tab) {
      if (!edita) return;
      var pagina = panel.querySelector('[data-inspector-page="' + tab + '"]');
      if (tab !== 'historial' && (!pagina || !pagina.children.length)) tab = 'editar';
      s.panelTab = tab;
      Array.prototype.forEach.call(root.querySelectorAll('[data-panel-tab]'), function (b) {
        var disponible = b.dataset.panelTab === 'historial' || !!panel.querySelector('[data-inspector-page="' + b.dataset.panelTab + '"] > *');
        b.hidden = !disponible;
        b.classList.toggle('me-tab-active', b.dataset.panelTab === tab);
        b.setAttribute('aria-selected', b.dataset.panelTab === tab ? 'true' : 'false');
      });
      Array.prototype.forEach.call(panel.querySelectorAll('[data-inspector-page]'), function (paginaActual) {
        paginaActual.hidden = paginaActual.dataset.inspectorPage !== tab;
      });
      panel.hidden = tab === 'historial';
      var historial = root.querySelector('.me-publicaciones'); if (historial) historial.hidden = tab !== 'historial';
    }
    function aplicarFiltroConstrucciones() {
      var lista = panel.querySelector('[data-inspector-page="elementos"] > .me-list');
      if (!lista) return;
      var q = s.filtroConstrucciones.trim().toLocaleLowerCase();
      Array.prototype.forEach.call(lista.children, function (item) { item.hidden = item.textContent.toLocaleLowerCase().indexOf(q) < 0; });
    }
    function iconoActivo(info) {
      var configurado = info && info.auxiliarId != null && s.iconosAuxiliares[info.auxiliarId];
      return configurado && (TRAZOS_ICONO[configurado] || configurado === 'PERSONALIZADO') ? configurado : sugerirIcono((info && info.auxiliarNombre) || (info && info.descripcion));
    }
    function urlIconoAuxiliar(auxiliarId) {
      return BASE + '/predios/' + s.predioId + '/auxiliares/' + auxiliarId + '/icono-imagen' +
        (s.iconoRevision[auxiliarId] ? '?v=' + s.iconoRevision[auxiliarId] : '');
    }
    function iconoMini(icono, auxiliarId) {
      if (icono === 'PERSONALIZADO' && auxiliarId)
        return '<img src="' + esc(urlIconoAuxiliar(auxiliarId)) + '" width="26" height="26" alt="">';
      return '<svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" focusable="false"><path d="' + (TRAZOS_ICONO[icono] || TRAZOS_ICONO.OTRO) + '"></path></svg>';
    }
    function selectorIconoAuxiliar(info) {
      if (!info || info.auxiliarId == null) return '<p class="me-muted">Este activo no tiene auxiliar contable vinculado; se muestra un símbolo sugerido.</p>';
      var actual = iconoActivo(info);
      return '<details class="me-details"><summary>Icono del auxiliar · ' + esc(info.auxiliarNombre || info.auxiliarId) + '</summary><label class="me-field">Elegir icono' +
        '<select class="me-icono-auxiliar" data-auxiliar-id="' + info.auxiliarId + '">' +
        ICONOS.map(function (tipo) { return '<option value="' + tipo + '"' + (tipo === actual ? ' selected' : '') + '>' + NOMBRES_ICONO[tipo] + '</option>'; }).join('') +
        (actual === 'PERSONALIZADO' ? '<option value="PERSONALIZADO" selected>Icono propio</option>' : '') +
        '</select></label><label class="me-field">¿No aparece el tipo de activo? Subir icono propio (PNG o JPG, hasta 150 KB)<input type="file" class="me-icono-file" data-auxiliar-id="' + info.auxiliarId + '" accept="image/png,image/jpeg"></label>' +
        '<p class="me-muted">Se aplica a todos los activos de este auxiliar en este predio. Se guarda al elegirlo.</p></details>';
    }
    function cambiarTamanoMarcador(a, z, tamano) {
      if (!a || !z || !isFinite(tamano) || tamano < .1 || tamano > Math.min(12, a.ancho, a.fondo)) return false;
      var centroX = z.x + z.ancho / 2, centroY = z.y + z.fondo / 2;
      z.ancho = z.fondo = redondearFino(tamano);
      z.x = redondearFino(Math.max(0, Math.min(a.ancho - z.ancho, centroX - z.ancho / 2)));
      z.y = redondearFino(Math.max(0, Math.min(a.fondo - z.fondo, centroY - z.fondo / 2)));
      return true;
    }
    function organizarPanel(e, p, a, stageKey) {
      if (!edita) return;
      var paginas = {}, nombres = ['editar', 'elementos', 'activos', 'ajustes'];
      nombres.forEach(function (nombre) { var pagina = document.createElement('div'); pagina.dataset.inspectorPage = nombre; pagina.setAttribute('role', 'tabpanel'); pagina.setAttribute('aria-label', nombre); paginas[nombre] = pagina; });
      var nodos = Array.prototype.slice.call(panel.children), grupo = 'editar';
      nodos.forEach(function (n) {
        var titulo = n.textContent.trim();
        if (!e) {
          if (n.classList.contains('me-details')) grupo = 'ajustes';
          else if (n.classList.contains('me-resumen') || n.classList.contains('me-list')) grupo = 'elementos';
          else grupo = 'editar';
        } else if (!p) {
          if (n.tagName === 'H3' && titulo.indexOf('Pisos de este edificio') === 0) grupo = 'elementos';
        } else if (!a) {
          if (n.classList.contains('me-resumen')) grupo = 'elementos';
        } else {
          if (n.classList.contains('me-interior-section')) grupo = n.classList.contains('me-assets-section') ? 'activos' : 'elementos';
          else if (n.tagName === 'H3' && titulo.indexOf('Inventario de esta oficina') === 0) grupo = 'activos';
        }
        paginas[grupo].appendChild(n);
      });
      if (!e && paginas.elementos.children.length) paginas.elementos.insertAdjacentHTML('afterbegin', '<h2>Construcciones del predio</h2><p class="me-muted">Selecciona una construcción para entrar a sus pisos y oficinas.</p><input class="me-list-filter" placeholder="Filtrar construcciones por nombre" aria-label="Filtrar construcciones del predio" value="' + esc(s.filtroConstrucciones) + '">');
      panel.innerHTML = '';
      nombres.forEach(function (nombre) { panel.appendChild(paginas[nombre]); });
      var seleccion = stageKey + '|' + (s.editarEdificio || s.editarEspacio || s.cuartoSeleccionado || s.muebleSeleccionado || s.activoSeleccionado || '');
      var cambioSeleccion = s.panelSelectionKey !== seleccion;
      if (s.panelSelectionKey !== seleccion) {
        s.panelSelectionKey = seleccion;
        s.panelTab = s.activoSeleccionado ? 'activos' : s.cuartoSeleccionado || s.muebleSeleccionado ? 'elementos' :
          (s.editarEdificio || s.editarEspacio || (a && !a.oficinaId)) ? 'editar' : a ? 'activos' : 'elementos';
      }
      activarPanel(s.panelTab);
      if (cambioSeleccion && s.panelTab === 'activos') panel.scrollTop = 0;
      if (!e) aplicarFiltroConstrucciones();
    }
    function confirmarModal(titulo, mensaje, accion) {
      return new Promise(function (resolve) {
        var modal = root.querySelector('.me-modal');
        if (!modal) { resolve(false); return; }
        var previo = document.activeElement, aceptar = modal.querySelector('[data-modal-aceptar]'), cancelar = modal.querySelector('[data-modal-cancelar]');
        modal.querySelector('#me-modal-titulo').textContent = titulo;
        modal.querySelector('#me-modal-texto').textContent = mensaje;
        aceptar.textContent = accion;
        modal.hidden = false; aceptar.focus();
        function cerrar(resultado) {
          modal.hidden = true;
          modal.removeEventListener('click', clic);
          modal.removeEventListener('keydown', teclado);
          if (previo && previo.focus) previo.focus();
          resolve(resultado);
        }
        function clic(ev) {
          if (ev.target === modal || ev.target.closest('[data-modal-cancelar]')) cerrar(false);
          else if (ev.target.closest('[data-modal-aceptar]')) cerrar(true);
        }
        function teclado(ev) {
          if (ev.key === 'Escape') { ev.preventDefault(); cerrar(false); }
          else if (ev.key === 'Tab') {
            if (ev.shiftKey && document.activeElement === cancelar) { ev.preventDefault(); aceptar.focus(); }
            else if (!ev.shiftKey && document.activeElement === aceptar) { ev.preventDefault(); cancelar.focus(); }
          }
        }
        modal.addEventListener('click', clic); modal.addEventListener('keydown', teclado);
      });
    }
    var modalDatos = root.querySelector('.me-data-modal'), focoAnteriorModal = null, modalDatosVersion = 0;
    function abrirModalDatos(titulo, contenido) {
      if (!modalDatos) return null;
      focoAnteriorModal = document.activeElement;
      modalDatosVersion++;
      modalDatos.querySelector('#me-data-modal-titulo').textContent = titulo;
      modalDatos.querySelector('.me-data-modal-body').innerHTML = contenido;
      modalDatos.hidden = false;
      modalDatos.querySelector('[data-data-modal-close]').focus();
      return modalDatos.querySelector('.me-data-modal-body');
    }
    function cerrarModalDatos() {
      if (!modalDatos || modalDatos.hidden) return;
      modalDatosVersion++;
      modalDatos.hidden = true;
      modalDatos.querySelector('.me-data-modal-body').innerHTML = '';
      if (focoAnteriorModal && focoAnteriorModal.isConnected) focoAnteriorModal.focus();
    }
    function mostrarActivosUbicados() {
      var a = espacio(); if (!a) return;
      var contenido = '<p class="me-muted">' + a.activos.length + ' activos con posición en este espacio. Selecciona uno para moverlo o ajustar su tamaño.</p>' +
        '<input class="me-modal-filtro" type="search" placeholder="Filtrar por código, descripción o cuarto" aria-label="Filtrar activos ubicados"><div class="me-modal-lista">' +
        a.activos.map(function (z) {
          var info = Object.assign({}, s.activos[z.activoId] || {}, s.detallesActivos[z.activoId] || {}), sala = cuartoDeElemento(a, z);
          return '<button type="button" class="me-modal-activo" data-modal-select-asset="' + z.activoId + '"><span class="me-asset-symbol">' + iconoMini(iconoActivo(info), info.auxiliarId) + '</span><span><strong>' + esc(info.codigo || z.activoId) + '</strong><small>' + esc(info.descripcion || 'Activo') + (sala ? ' · ' + esc(sala.nombre) : '') + '</small></span></button>';
        }).join('') + (a.activos.length ? '' : '<p class="me-muted">Todavía no se ubicaron activos. Usa «+ Ubicar» desde el inventario.</p>') + '</div>';
      abrirModalDatos('Activos ubicados', contenido);
    }
    function mostrarFichaActivo(id) {
      var info = s.detallesActivos[id] || s.activos[id] || {};
      var cuerpo = abrirModalDatos('Ficha del activo ' + (info.codigo || id), '<p class="me-muted">Cargando datos actuales…</p>');
      if (!cuerpo) return;
      var versionFicha = modalDatosVersion;
      request(BASE + '/activos/' + id + '/detalle').then(function (dato) {
        if (modalDatos.hidden || versionFicha !== modalDatosVersion) return;
        s.detallesActivos[id] = dato;
        cuerpo.innerHTML = '<p class="me-modal-descripcion">' + esc(dato.descripcion || 'Sin descripción') + '</p>' +
          '<dl class="me-asset-details"><dt>Código</dt><dd>' + esc(dato.codigo || 'Sin dato') + '</dd>' +
          '<dt>Auxiliar</dt><dd>' + esc(dato.auxiliarNombre || 'Sin dato') + '</dd>' +
          '<dt>Responsable</dt><dd>' + esc(dato.responsable || 'Sin dato') + '</dd>' +
          '<dt>Estado físico</dt><dd>' + esc(dato.estadoFisico || 'Sin dato') + '</dd>' +
          '<dt>Situación</dt><dd>' + esc(dato.estado || 'Sin dato') + '</dd>' +
          '<dt>Oficina actual</dt><dd>' + esc(dato.oficinaNombre || nombreOficina(dato.oficinaId)) + '</dd></dl>';
      }).catch(function (error) { if (!modalDatos.hidden && versionFicha === modalDatosVersion) cuerpo.textContent = 'No se pudo cargar la ficha: ' + error.message; });
    }
    if (modalDatos) {
      modalDatos.addEventListener('click', function (ev) {
        if (ev.target === modalDatos || ev.target.closest('[data-data-modal-close]')) { cerrarModalDatos(); return; }
        var item = ev.target.closest('[data-modal-select-asset]');
        if (item) {
          s.activoSeleccionado = Number(item.dataset.modalSelectAsset);
          s.cuartoSeleccionado = s.muebleSeleccionado = null;
          cerrarModalDatos(); cargarDetalleActivo(s.activoSeleccionado); pintar();
        }
      });
      modalDatos.addEventListener('input', function (ev) {
        if (!ev.target.classList.contains('me-modal-filtro')) return;
        var q = ev.target.value.trim().toLocaleLowerCase();
        Array.prototype.forEach.call(modalDatos.querySelectorAll('.me-modal-activo'), function (item) {
          item.hidden = item.textContent.toLocaleLowerCase().indexOf(q) < 0;
        });
      });
      modalDatos.addEventListener('keydown', function (ev) {
        if (ev.key === 'Escape') { ev.preventDefault(); cerrarModalDatos(); return; }
        if (ev.key !== 'Tab') return;
        var controles = Array.prototype.slice.call(modalDatos.querySelectorAll('button:not([hidden]),input:not([hidden])'));
        if (!controles.length) return;
        if (ev.shiftKey && document.activeElement === controles[0]) { ev.preventDefault(); controles[controles.length - 1].focus(); }
        else if (!ev.shiftKey && document.activeElement === controles[controles.length - 1]) { ev.preventDefault(); controles[0].focus(); }
      });
    }
    function actualizarPublicacion() {
      if (!edita) return;
      var etiqueta = root.querySelector('.me-publicacion-estado');
      if (etiqueta) etiqueta.textContent = s.numeroPublicacion ? 'Versión ' + s.numeroPublicacion + ' visible en el explorador' : 'Sin publicar';
      var boton = root.querySelector('.me-publicar');
      if (boton) { boton.hidden = !s.puedePublicar; boton.disabled = !s.predioId; }
    }
    function cargarPublicaciones() {
      if (!edita || !s.predioId) return;
      var lista = root.querySelector('.me-publicaciones-lista');
      if (!lista) return;
      var id = s.predioId;
      request(BASE + '/predios/' + id + '/publicaciones').then(function (items) {
        if (s.predioId !== id) return;
        lista.innerHTML = items.length ? items.map(function (item) {
          return '<div class="me-publicacion-item"><span><strong>Versión ' + item.numero + (item.actual ? ' · actual' : '') + '</strong><small>' + esc(item.fecha || '') + ' · ' + esc(item.usuario || '') + '</small></span>' +
            '<button type="button" class="me-mini" data-restaurar="' + item.numero + '">Recuperar en borrador</button></div>';
        }).join('') : '<p class="me-muted">Aún no hay versiones publicadas.</p>';
        var tab = root.querySelector('[data-panel-tab="historial"]');
        if (tab) tab.textContent = 'Historial' + (items.length ? ' (' + items.length + ')' : '');
      }).catch(function (error) { if (s.predioId === id) lista.textContent = error.message; });
    }
    function dirty() {
      var actual = JSON.stringify(s.escena);
      if (s.historial[s.indice] !== actual) {
        s.historial = s.historial.slice(0, s.indice + 1);
        s.historial.push(actual);
        if (s.historial.length > 50) s.historial.shift();
        s.indice = s.historial.length - 1;
      }
      s.dirty = actual !== s.guardado;
      avisar(s.dirty ? 'Cambios sin guardar' : 'Sin cambios', false);
      actualizarHerramientas();
    }
    function viajarHistorial(direccion) {
      var destino = s.indice + direccion;
      if (destino < 0 || destino >= s.historial.length) return;
      s.indice = destino; s.escena = normalizarGeometria(JSON.parse(s.historial[destino]));
      if (!edificio()) s.edificio = s.piso = s.espacio = null;
      else if (!piso()) s.piso = s.espacio = null;
      else if (!espacio()) s.espacio = null;
      if (!cuarto()) s.cuartoSeleccionado = null;
      if (!mueble()) s.muebleSeleccionado = null;
      if (!espacio() || !espacio().activos.some(function (z) { return Number(z.activoId) === Number(s.activoSeleccionado); })) s.activoSeleccionado = null;
      s.modo = ''; s.dibujo = []; s.editarEdificio = null; s.editarEspacio = null;
      s.dirty = JSON.stringify(s.escena) !== s.guardado;
      avisar(s.dirty ? 'Cambios sin guardar' : 'Sin cambios', false);
      pintar();
    }
    function edificio() { return s.escena.edificios.find(function (e) { return e.id === s.edificio; }); }
    function piso() { var e = edificio(); return e && e.pisos.find(function (p) { return p.id === s.piso; }); }
    function espacio() { var p = piso(); return p && p.espacios.find(function (a) { return a.id === s.espacio; }); }
    function cuarto() { var a = espacio(); return a && (a.cuartos || []).find(function (c) { return c.id === s.cuartoSeleccionado; }); }
    function mueble() { var a = espacio(); return a && (a.mobiliario || []).find(function (m) { return m.id === s.muebleSeleccionado; }); }
    function contexto() { return !edificio() ? 'predio' : !piso() ? 'edificio' : !espacio() ? 'piso' : 'espacio'; }
    function objetoEnVertices() {
      if (contexto() === 'predio' && s.editarEdificio) return s.escena.edificios.find(function (b) { return b.id === s.editarEdificio; });
      if (contexto() === 'edificio') return edificio();
      if (contexto() === 'piso' && s.editarEspacio) return piso().espacios.find(function (r) { return r.id === s.editarEspacio; });
      if (contexto() === 'espacio' && s.cuartoSeleccionado) return cuarto();
      return null;
    }
    function alturaTotal(e) { return redondear((e.pisos || []).reduce(function (total, p) { return total + Number(p.altura || 4); }, 0)); }
    function sincronizarAltura(e) { if (e) e.altura = alturaTotal(e); }
    function ajustarNumero(v) { return s.snap ? Math.round(v / 5) * 5 : redondearFino(v); }
    function coordenadaPlano(ev) {
      var punto = puntoSvg(ev), e = edificio();
      var limite = contexto() === 'predio' ? { ancho: 2000, fondo: 2000 } : contexto() === 'espacio' ? espacio() : e;
      return { x: Math.max(0, Math.min(limite.ancho, ajustarNumero(punto.x))),
        y: Math.max(0, Math.min(limite.fondo, ajustarNumero(punto.y))) };
    }
    function actualizarZoomIndicador() {
      var barraZoom = root.querySelector('.me-herramientas'); if (!barraZoom) return;
      var control = barraZoom.querySelector('.me-zoom-range'), etiqueta = barraZoom.querySelector('.me-zoom-label');
      if (control) control.value = valorDelZoom(s.zoom);
      if (etiqueta) etiqueta.textContent = Math.round(s.zoom * 100) + '%';
    }
    function actualizarHerramientas() {
      var barra = root.querySelector('.me-herramientas'); if (!barra) return;
      var stage = contexto();
      Array.prototype.forEach.call(barra.querySelectorAll('[data-context]'), function (b) { b.hidden = b.dataset.context !== stage; });
      Array.prototype.forEach.call(barra.querySelectorAll('[data-tool]'), function (b) { b.classList.toggle('me-tool-active', b.dataset.tool === (s.modo || 'select')); });
      var deshacer = barra.querySelector('[data-action="undo"]'), rehacer = barra.querySelector('[data-action="redo"]');
      if (deshacer) deshacer.disabled = s.indice <= 0;
      if (rehacer) rehacer.disabled = s.indice >= s.historial.length - 1;
      var fin = barra.querySelector('[data-action="finish"]'), cancelar = barra.querySelector('[data-action="cancel"]');
      var dibujando = s.modo === 'polygonBuilding' || s.modo === 'polygonOffice' || s.modo === 'polygonRoom' || ['MURO', 'PUERTA', 'VENTANA', 'ESCALERA'].indexOf(s.modo) >= 0;
      if (fin) { fin.hidden = !dibujando || s.modo[0] !== 'p'; fin.disabled = s.dibujo.length < 3; }
      if (cancelar) cancelar.hidden = !dibujando;
      Array.prototype.forEach.call(root.querySelectorAll('[data-quick-context]'), function (grupo) { grupo.hidden = dibujando || s.vista3d || grupo.dataset.quickContext !== stage; });
      var dibujoRapido = root.querySelector('.me-quick-drawing');
      if (dibujoRapido) {
        dibujoRapido.hidden = !dibujando;
        dibujoRapido.querySelector('strong').textContent = s.modo[0] === 'p' ? 'Marca las esquinas en el plano (' + s.dibujo.length + ' puntos)' : 'Marca los dos extremos en el plano';
        var terminarRapido = dibujoRapido.querySelector('[data-quick-action="finish"]');
        terminarRapido.hidden = s.modo[0] !== 'p'; terminarRapido.disabled = s.dibujo.length < 3;
      }
      var estadoTool = barra.querySelector('.me-tool-status');
      if (estadoTool) estadoTool.textContent = s.vista3d ? ('Cámara ' + Math.round(s.camara) + '° · inclinación ' + Math.round(s.inclinacion) + '°. ' + (s.modo === 'move3d' ? 'Arrastra un edificio para moverlo.' : 'Arrastra para girar; selecciona un piso para ver oficinas.')) : s.modo === 'polygonBuilding'
        ? 'Marca las esquinas del edificio sobre el plano y pulsa Terminar contorno. Se rellenará al cerrarlo.' : s.modo === 'polygonOffice' || s.modo === 'polygonRoom'
        ? 'Marca al menos 3 puntos y pulsa Terminar contorno.' : ['MURO', 'PUERTA', 'VENTANA', 'ESCALERA'].indexOf(s.modo) >= 0
          ? 'Marca el inicio y el final del detalle.' : s.editarEdificio ? 'Puntos naranjas: forma · azul: escala · verde: giro. Mayús fija un eje; desactiva Ajustar puntos para precisión libre.' : s.modo === 'vertices' || s.editarEspacio || s.cuartoSeleccionado ? 'Arrastra puntos naranjas con precisión libre; Mayús fija un eje. Los círculos blancos añaden lados.' : s.modo === 'pan' ? 'Arrastra el plano.' : '';
      var guia = root.querySelector('.me-canvas-hint');
      if (guia) guia.textContent = s.vista3d ? 'Arrastra: girar · Botón derecho, rueda pulsada o Mayús + arrastrar: mover vista · Rueda: zoom · Flechas: cámara · Clic: abrir edificio' :
        edita ? 'Rueda: zoom bajo el cursor · Espacio + arrastrar o rueda pulsada: mover plano · Clic: seleccionar · Arrastra un punto naranja: cambiar contorno' :
          'Arrastra el fondo para mover el plano · Rueda: zoom bajo el cursor · Clic en una figura para entrar';
      Array.prototype.forEach.call(barra.querySelectorAll('[data-3d]'), function (b) { b.hidden = !s.vista3d; });
      var techo = barra.querySelector('.me-techo3d'); if (techo) techo.checked = s.techo3d;
      actualizarZoomIndicador();
    }
    function recalcularForma(o) {
      var antesX = o.x, antesY = o.y, b = caja(o.puntos);
      o.x = redondearFino(b.x); o.y = redondearFino(b.y);
      o.ancho = redondearFino(b.ancho); o.fondo = redondearFino(b.fondo);
      var dx = o.x - antesX, dy = o.y - antesY;
      if (o.pisos && (dx || dy)) o.pisos.forEach(function (p) {
        p.espacios.forEach(function (r) {
          r.puntos.forEach(function (pt) { pt.x = redondearFino(pt.x - dx); pt.y = redondearFino(pt.y - dy); });
          r.x = redondearFino(r.x - dx); r.y = redondearFino(r.y - dy);
        });
        p.trazos.forEach(function (t) { t.x1 = redondearFino(t.x1 - dx); t.x2 = redondearFino(t.x2 - dx); t.y1 = redondearFino(t.y1 - dy); t.y2 = redondearFino(t.y2 - dy); });
      });
      if (o.activos && (dx || dy)) {
        o.activos.concat(o.mobiliario || []).forEach(function (z) { z.x = redondearFino(z.x - dx); z.y = redondearFino(z.y - dy); });
        (o.cuartos || []).forEach(function (c) { c.x = redondearFino(c.x - dx); c.y = redondearFino(c.y - dy);
          c.puntos.forEach(function (pt) { pt.x = redondearFino(pt.x - dx); pt.y = redondearFino(pt.y - dy); }); });
      }
    }
    function poligonoSimple(puntos) {
      if (puntos.length < 3) return false;
      var area = 0;
      for (var i = 0; i < puntos.length; i++) {
        var a = puntos[i], b = puntos[(i + 1) % puntos.length]; area += a.x * b.y - b.x * a.y;
        if (Math.hypot(a.x - b.x, a.y - b.y) < .05) return false;
      }
      if (Math.abs(area) <= .02) return false;
      function orientacion(a, b, c) { return (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x); }
      function enSegmento(a, b, c) { return Math.min(a.x, b.x) <= c.x && c.x <= Math.max(a.x, b.x) && Math.min(a.y, b.y) <= c.y && c.y <= Math.max(a.y, b.y); }
      for (var j = 0; j < puntos.length; j++) {
        var p1 = puntos[j], p2 = puntos[(j + 1) % puntos.length];
        if (p1.x === p2.x && p1.y === p2.y) return false;
        for (var k = j + 1; k < puntos.length; k++) {
          if (k === j + 1 || (j === 0 && k === puntos.length - 1)) continue;
          var q1 = puntos[k], q2 = puntos[(k + 1) % puntos.length];
          var o1 = orientacion(p1, p2, q1), o2 = orientacion(p1, p2, q2);
          var o3 = orientacion(q1, q2, p1), o4 = orientacion(q1, q2, p2);
          if ((o1 * o2 < 0 && o3 * o4 < 0) ||
              (o1 === 0 && enSegmento(p1, p2, q1)) || (o2 === 0 && enSegmento(p1, p2, q2)) ||
              (o3 === 0 && enSegmento(q1, q2, p1)) || (o4 === 0 && enSegmento(q1, q2, p2))) return false;
        }
      }
      return true;
    }
    function terminarDibujo() {
      if (s.modo !== 'polygonBuilding' && s.modo !== 'polygonOffice' && s.modo !== 'polygonRoom') return;
      if (!poligonoSimple(s.dibujo)) { avisar('El contorno necesita al menos 3 puntos y no puede cruzarse.', true); return; }
      var b = caja(s.dibujo);
      if (b.ancho < .1 || b.fondo < .1) { avisar('El contorno es demasiado pequeño.', true); return; }
      if (s.modo === 'polygonBuilding') {
        var tipoConstruccion = s.tipoDibujo === 'OFICINA_INDEPENDIENTE' ? 'OFICINA_INDEPENDIENTE' : 'EDIFICIO';
        var e = { id: uid(), nombre: tipoConstruccion === 'EDIFICIO' ? 'NUEVO EDIFICIO' : 'OFICINA INDEPENDIENTE', tipo: tipoConstruccion, x: redondearFino(b.x), y: redondearFino(b.y), ancho: redondearFino(b.ancho), fondo: redondearFino(b.fondo), altura: 4,
          puntos: s.dibujo.slice(), pisos: [{ id: uid(), nombre: 'PLANTA BAJA', altura: 4, espacios: [], trazos: [] }] };
        s.escena.edificios.push(e); s.edificio = s.piso = s.espacio = null; s.editarEdificio = e.id;
      } else if (s.modo === 'polygonOffice') {
        var r = { id: uid(), nombre: 'NUEVA OFICINA', tipo: 'OFICINA', x: redondearFino(b.x), y: redondearFino(b.y), ancho: redondearFino(b.ancho), fondo: redondearFino(b.fondo), puntos: s.dibujo.slice(), oficinaId: null, cuartos: [], mobiliario: [], activos: [] };
        piso().espacios.push(r); s.espacio = r.id;
      } else {
        var a = espacio();
        var c = { id: uid(), nombre: 'CUARTO ' + ((a.cuartos || []).length + 1), tipo: 'CUARTO_INTERIOR',
          x: redondearFino(b.x), y: redondearFino(b.y), ancho: redondearFino(b.ancho), fondo: redondearFino(b.fondo), puntos: s.dibujo.slice() };
        a.cuartos.push(c); s.cuartoSeleccionado = c.id;
      }
      s.dibujo = []; s.modo = ''; s.tipoDibujo = 'EDIFICIO'; dirty(); pintar();
      var foco = panel.querySelector(s.cuartoSeleccionado ? '[data-prop="cuartoNombre"]' : s.espacio ? '.me-buscar-oficina' : '[data-prop="beNombre"]'); if (foco) foco.focus();
    }
    function cambiarPredio(id) {
      if (s.dirty && !confirm('Hay cambios sin guardar. ¿Cambiar de predio?')) { sel.value = s.predioId; return; }
      s.cargaVista3d++;
    s.predioId = Number(id); s.edificio = s.piso = s.espacio = null; s.vista3d = false; s.inventario = null; s.consultaInventario++; s.detallesCargados = {}; s.detallesActivos = {}; s.iconosAuxiliares = {}; s.iconoRevision = {}; s.cuartoSeleccionado = s.muebleSeleccionado = s.activoSeleccionado = null; s.cargaEdificio++; s.viewport = null; s.enfoqueInicial = edita; s.marco3dFijo = null; s.destacadoEdificio = s.busquedaSeleccionada = null; s.destacados = {}; s.ubicacionDestacada = null; s.resultadosBusqueda = []; s.filtroConstrucciones = '';
      if (filtro) filtro.value = '';
      var resultadosAnteriores = root.querySelector('.me-search-results'); if (resultadosAnteriores) resultadosAnteriores.hidden = true;
      s.modo = ''; s.dibujo = []; s.tipoDibujo = 'EDIFICIO'; s.editarEdificio = null; s.editarEspacio = null; s.zoom = 1; s.panX = s.panY = 0; s.camara = 38; s.inclinacion = 38;
      var predioSolicitado = s.predioId, solicitud = s.cargaVista3d;
      request(BASE + '/predios/' + predioSolicitado + (edita ? '' : '/resumen')).then(function (d) {
        if (solicitud !== s.cargaVista3d || predioSolicitado !== s.predioId) return;
        s.escena = normalizarGeometria(d.escena || { edificios: [] }); s.version = d.version;
        s.puedePublicar = !!d.puedePublicar; s.numeroPublicacion = Number(d.numeroPublicacion || 0);
        s.publicacionEnCarga = 0;
        s.oficinas = d.oficinas || {}; s.activos = d.activos || {}; s.dirty = false;
        request(BASE + '/predios/' + predioSolicitado + '/iconos-auxiliares').then(function (iconos) {
          if (predioSolicitado === s.predioId) {
            s.iconosAuxiliares = iconos || {};
            Object.keys(s.iconosAuxiliares).forEach(function (auxiliarId) {
              if (s.iconosAuxiliares[auxiliarId] === 'PERSONALIZADO') s.iconoRevision[auxiliarId] = Date.now();
            });
            pintar();
          }
        }).catch(function () { /* Se mantienen los iconos sugeridos. */ });
        s.tieneFondo = !!d.tieneFondo; s.fondoRevision = edita ? Date.now() : s.numeroPublicacion;
        s.guardado = JSON.stringify(s.escena); s.historial = [s.guardado]; s.indice = 0;
        if (edita) { avisar('Borrador cargado', false); actualizarPublicacion(); cargarPublicaciones(); pintar(); return; }
        avisar('Cargando vista 3D del predio…', false);
        cargar3d().then(function () {
          if (solicitud !== s.cargaVista3d || predioSolicitado !== s.predioId) return;
          s.vista3d = true; avisar('Modelo 3D cargado', false); pintar();
        }).catch(function (error) {
          if (solicitud !== s.cargaVista3d || predioSolicitado !== s.predioId) return;
          pintar(); avisar(error.message, true);
        });
      }).catch(function (e) { if (solicitud === s.cargaVista3d) { s.publicacionEnCarga = 0; avisar(e.message, true); } });
    }
    function recibirPublicacion(datos) {
      if (edita || !root.isConnected || !datos || Number(datos.predioId) !== s.predioId || Number(datos.numero) <= Math.max(s.numeroPublicacion, s.publicacionEnCarga)) return;
      s.publicacionEnCarga = Number(datos.numero);
      cambiarPredio(s.predioId);
      avisar('Se publicó una nueva versión. Actualizando el mapa…', false);
    }
    window.addEventListener('mapaEspacialPublicado', function (ev) { recibirPublicacion(ev.detail); });
    document.addEventListener('sciaf:mapa-espacial-publicado', function (ev) { recibirPublicacion(ev.detail); });
    window.addEventListener('storage', function (ev) {
      if (ev.key !== 'mapaEspacialPublicado' || !ev.newValue) return;
      try { recibirPublicacion(JSON.parse(ev.newValue)); } catch (ignorado) { /* evento ajeno */ }
    });
    function notificarPublicacion(predioId, numero) {
      var dato = { predioId: predioId, numero: numero, fecha: Date.now() };
      window.dispatchEvent(new CustomEvent('mapaEspacialPublicado', { detail: dato }));
      try { window.localStorage.setItem('mapaEspacialPublicado', JSON.stringify(dato)); } catch (ignorado) { /* almacenamiento deshabilitado */ }
    }
    function abrirEdificio(id, pisoId, espacioId, activoId) {
      var edificioActual = s.escena.edificios.find(function (b) { return b.id === id; });
      var pisoDestino = pisoId || (!edita && edificioActual && edificioActual.pisos.length === 1 ? edificioActual.pisos[0].id : null);
      function seleccionar() { limpiarHerramienta(); if (s.espacio !== (espacioId || null)) s.cuartoSeleccionado = s.muebleSeleccionado = s.activoSeleccionado = null;
        var elegido = s.escena.edificios.find(function (b) { return b.id === id; });
        var planta = pisoId || (!edita && elegido && elegido.pisos.length === 1 ? elegido.pisos[0].id : null);
        s.edificio = id; s.piso = planta; s.espacio = espacioId || null; s.destacadoEdificio = null; s.marco3dFijo = null;
        if (activoId && espacioId) { s.activoSeleccionado = Number(activoId); cargarDetalleActivo(activoId); }
        if (!edita) s.vista3d = !planta;
        pintar(); }
      var solicitud = ++s.cargaEdificio;
      if (edita || !pisoDestino || s.detallesCargados[id + ':' + pisoDestino]) { seleccionar(); return; }
      var predioSolicitado = s.predioId;
      avisar('Cargando oficinas del piso…', false);
      request(BASE + '/predios/' + predioSolicitado + '/edificios/' + encodeURIComponent(id) + '/pisos/' + encodeURIComponent(pisoDestino)).then(function (d) {
        if (predioSolicitado !== s.predioId || solicitud !== s.cargaEdificio) return;
        var indice = s.escena.edificios.findIndex(function (b) { return b.id === id; });
        if (indice < 0) return;
        var pisoIndice = s.escena.edificios[indice].pisos.findIndex(function (p) { return p.id === pisoDestino; });
        if (pisoIndice < 0) return;
        s.escena.edificios[indice].pisos[pisoIndice] = d.piso;
        normalizarGeometria({ edificios: [s.escena.edificios[indice]] });
        Object.assign(s.oficinas, d.oficinas || {}); Object.assign(s.activos, d.activos || {});
        s.detallesCargados[id + ':' + pisoDestino] = true; seleccionar(); avisar('Piso y oficinas cargados', false);
      }).catch(function (error) { if (solicitud === s.cargaEdificio) avisar(error.message, true); });
    }
    function agregarEdificio(tipo) {
      s.tipoDibujo = tipo === 'OFICINA_INDEPENDIENTE' ? tipo : 'EDIFICIO';
      s.vista3d = false; s.edificio = s.piso = s.espacio = null; s.editarEdificio = null;
      s.modo = 'polygonBuilding'; s.dibujo = []; s.vertice = -1;
      pintar(); avisar('Marca las esquinas de la construcción sobre el plano y pulsa Terminar contorno.', false);
    }
    function agregarPiso() {
      var e = edificio(); if (!e) return;
      var p = { id: uid(), nombre: 'PISO ' + e.pisos.length, altura: 4, espacios: [] };
      p.trazos = [];
      e.pisos.push(p); sincronizarAltura(e); s.piso = p.id; s.espacio = null; dirty(); pintar();
    }
    function agregarEspacio(tipo, oficinaId) {
      var e = edificio(), p = piso(); if (!e || !p) return;
      var count = p.espacios.length;
      tipo = tipo || 'OFICINA';
      var etiqueta = tipo === 'OFICINA' ? 'NUEVA OFICINA' : tipo === 'PASILLO' ? 'PASILLO' : tipo === 'CUARTO' ? 'CUARTO' : 'OTRO ESPACIO';
      s.vista3d = false;
      var a = { id: uid(), nombre: etiqueta, tipo: tipo,
        x: Math.min(Math.max(0, e.ancho - 88), 8 + (count % 2) * 100),
        y: Math.min(Math.max(0, e.fondo - 54), 8 + Math.floor(count / 2) * 62),
        ancho: Math.min(88, e.ancho), fondo: Math.min(54, e.fondo), oficinaId: oficinaId || null, cuartos: [], mobiliario: [], activos: [] };
      a.puntos = puntosRect(a);
      p.espacios.push(a); s.espacio = a.id; dirty(); pintar();
      if (tipo === 'OFICINA' && !oficinaId) {
        var buscar = panel.querySelector('.me-buscar-oficina'); if (buscar) buscar.focus();
      }
    }
    function agregarMueble(tipo) {
      var a = espacio(); if (!edita || !a || FIGURAS.indexOf(tipo) < 0) return;
      var medidas = { MESA: [12, 7], SILLA: [5, 5], EQUIPO: [8, 6], ESTANTE: [10, 4], ARCHIVADOR: [8, 5], OTRO: [8, 6] }[tipo];
      var w = Math.min(a.ancho, medidas[0]), h = Math.min(a.fondo, medidas[1]);
      var zona = cuarto() || { x: 0, y: 0, ancho: a.ancho, fondo: a.fondo }, n = a.mobiliario.length;
      var x = Math.max(0, Math.min(a.ancho - w, zona.x + 2 + (n % 3) * (w + 2)));
      var y = Math.max(0, Math.min(a.fondo - h, zona.y + 2 + Math.floor(n / 3) * (h + 2)));
      var m = { id: uid(), nombre: tipo.charAt(0) + tipo.slice(1).toLowerCase() + ' ' + (n + 1), figura: tipo,
        x: redondear(x), y: redondear(y), ancho: redondear(w), fondo: redondear(h), giro: 0 };
      limpiarHerramienta(); a.mobiliario.push(m); s.muebleSeleccionado = m.id; s.cuartoSeleccionado = s.activoSeleccionado = null; dirty(); pintar();
    }
    function campo(label, prop, val, type, min, max) {
      return '<label class="me-field">' + esc(label) + '<input data-prop="' + prop + '" type="' + (type || 'text') + '" value="' + esc(val) + '"' + (type === 'number' ? ' step="any"' : '') + (min == null ? '' : ' min="' + min + '"') + (max == null ? '' : ' max="' + max + '"') + '></label>';
    }
    function boton(label, act, extra) { return '<button type="button" class="me-mini ' + (extra || '') + '" data-act="' + act + '">' + label + '</button>'; }
    function editorEstilo(o, prefijo, colorDefecto) {
      var color = o.color || colorDefecto;
      var paleta = ['#f8eb69', '#9ad9e9', '#b9e6c4', '#e9c2dd', '#f6c89f', '#b9c8f0', '#dce4e8'];
      return '<div class="me-style-editor"><label class="me-field">Color de la figura<input type="color" data-prop="' + prefijo + 'color" value="' + esc(color) + '"></label>' +
        '<div class="me-swatches" aria-label="Colores rápidos">' + paleta.map(function (c) {
          return '<button type="button" data-color="' + c + '" data-color-target="' + prefijo + '" style="background:' + c + '" title="Usar color ' + c + '" aria-label="Usar color ' + c + '"></button>';
        }).join('') + '</div>' +
        campo('Giro del nombre (−180 a 180°)', prefijo + 'textoGiro', o.textoGiro == null ? redondear(anguloEtiqueta(o)) : o.textoGiro, 'number', -180, 180) +
        '<p class="me-muted">El nombre gira con el lado principal. Puedes corregir su ángulo aquí.</p></div>';
    }
    function nombreOficina(id) { var o = s.oficinas[id]; return o ? (o.codigo + ' · ' + o.nombre) : 'Sin oficina vinculada'; }
    function contarOficinas(espacios) { return new Set(espacios.map(function (r) { return r.oficinaId; }).filter(Boolean)).size; }
    function ubicacionActivo(id) {
      for (var bi = 0; bi < s.escena.edificios.length; bi++) {
        var b = s.escena.edificios[bi];
        for (var pi = 0; pi < b.pisos.length; pi++) {
          var p = b.pisos[pi];
          for (var ri = 0; ri < p.espacios.length; ri++) {
            var r = p.espacios[ri];
            if (r.activos.some(function (z) { return Number(z.activoId) === Number(id); })) return { edificio: b.nombre, piso: p.nombre, espacio: r.nombre, espacioId: r.id };
          }
        }
      }
      return null;
    }
    function mostrarInventario() {
      var cont = panel.querySelector('.me-inventario'); if (!cont) return;
      var inv = s.inventario, a = espacio();
      if (!a || !a.oficinaId) return;
      if (!inv || inv.oficinaId !== a.oficinaId || inv.cargando) { cont.innerHTML = '<p class="me-muted">Cargando activos de la oficina…</p>'; return; }
      if (inv.error) { cont.innerHTML = '<p class="me-status-error">' + esc(inv.error) + '</p>'; return; }
      var h = '<p class="me-muted">' + inv.total + ' activos vigentes · ' + inv.items.length + ' en esta página. Pulsa Ubicar y arrastra el símbolo en el plano.</p>';
      if (!inv.items.length) h += '<p class="me-muted">' + (inv.q ? 'Sin resultados para esa búsqueda.' : 'Esta oficina no tiene activos vigentes registrados.') + '</p>';
      h += '<div class="me-inventory-list">' + inv.items.map(function (it) {
        var u = ubicacionActivo(it.id), mismo = u && u.espacioId === a.id;
        var ubicacion = mismo ? 'Ubicado en este espacio' : u ? 'Ubicado en ' + u.espacio + ' · ' + u.piso : 'Sin posición dibujada';
        var icono = iconoActivo(it);
        return '<article class="me-activo-fila me-inventory-compact"><div class="me-asset-symbol" aria-hidden="true">' + iconoMini(icono, it.auxiliarId) + '</div><div class="me-inventory-main"><strong>' + esc(it.codigo || 'Sin código') + '</strong>' +
          '<div>' + esc(it.descripcion || 'Sin descripción') + '</div>' +
          '<small>' + esc(it.auxiliarNombre || NOMBRES_ICONO[icono]) + ' · ' + esc(ubicacion) + '</small>' +
          (edita && !u ? '<button type="button" class="me-mini me-action-primary" data-place-asset="' + it.id + '">+ Ubicar</button>' : '') + '</div>' +
          '</article>';
      }).join('') + '</div>';
      if (inv.paginas > 1) h += '<div class="me-paginacion"><button type="button" class="me-mini" data-inventory-page="' + (inv.pagina - 1) + '"' + (inv.pagina === 0 ? ' disabled' : '') + '>← Anterior</button><span>Página ' + (inv.pagina + 1) + ' de ' + inv.paginas + '</span><button type="button" class="me-mini" data-inventory-page="' + (inv.pagina + 1) + '"' + (inv.pagina + 1 >= inv.paginas ? ' disabled' : '') + '>Siguiente →</button></div>';
      cont.innerHTML = h;
    }
    function cargarInventario(oficinaId, q, pagina) {
      if (!oficinaId) return;
      var numero = ++s.consultaInventario;
      s.inventario = { oficinaId: oficinaId, q: q || '', pagina: pagina || 0, total: 0, paginas: 0, items: [], cargando: true, error: null };
      mostrarInventario();
      request(BASE + '/oficinas/' + oficinaId + '/activos?q=' + encodeURIComponent(q || '') + '&pagina=' + (pagina || 0))
        .then(function (datos) {
          if (numero !== s.consultaInventario || !espacio() || espacio().oficinaId !== oficinaId) return;
          s.inventario = { oficinaId: oficinaId, q: q || '', pagina: datos.pagina, total: datos.total, paginas: datos.totalPaginas, items: datos.items || [], cargando: false, error: null };
          mostrarInventario();
        }).catch(function (error) {
          if (numero !== s.consultaInventario) return;
          s.inventario.cargando = false; s.inventario.error = error.message; mostrarInventario();
        });
    }
    function cargarDetalleActivo(id) {
      if (!id || s.detallesActivos[id]) return;
      s.detallesActivos[id] = { cargando: true };
      request(BASE + '/activos/' + id + '/detalle').then(function (dato) {
        s.detallesActivos[id] = dato;
        s.activos[id] = { codigo: dato.codigo, descripcion: dato.descripcion, oficinaId: dato.oficinaId,
          auxiliarId: dato.auxiliarId, auxiliarNombre: dato.auxiliarNombre };
        if (Number(s.activoSeleccionado) === Number(id)) {
          if (drag && drag.kind === 'asset') drag.detalleActualizado = true;
          else pintar();
        }
      }).catch(function (error) {
        s.detallesActivos[id] = { error: error.message };
        if (Number(s.activoSeleccionado) === Number(id)) {
          if (drag && drag.kind === 'asset') drag.detalleActualizado = true;
          else pintar();
        }
      });
    }
    function renderPanel() {
      var e = edificio(), p = piso(), a = espacio(), h = '';
      var stageKey = a ? 's:' + a.id : p ? 'p:' + p.id : e ? 'e:' + e.id : 'predio';
      var mismoStage = s.panelStageKey === stageKey;
      var configAbierta = mismoStage && panel.querySelector('.me-config') && panel.querySelector('.me-config').open;
      var medidasAbiertas = mismoStage && panel.querySelector('.me-details') && panel.querySelector('.me-details').open;
      if (!e) {
        h = '<h2>Construcciones del predio</h2>';
        if (edita) h += '<p class="me-instruccion">Traza el edificio marcando sus esquinas sobre el plano de fondo; al terminar se rellena el contorno y se crea la planta baja.</p>' + boton('+ Trazar edificio', 'addBuilding', 'me-action-primary') + ' ' + boton('+ Trazar oficina independiente', 'addIndependent');
        var edificioElegido = s.editarEdificio && s.escena.edificios.find(function (b) { return b.id === s.editarEdificio; });
        if (edita && edificioElegido) h += '<section class="me-selected-editor"><h3>' + esc(edificioElegido.nombre) + '</h3>' +
          '<p class="me-muted">' + edificioElegido.pisos.length + ' piso(s) · altura total ' + alturaTotal(edificioElegido) + ' unidades visuales.</p>' +
          '<p class="me-instruccion">Ajusta el tamaño con el cuadrado azul y gira con el círculo verde. Arrastra los puntos naranjas para cambiar la forma y pulsa los círculos blancos para crear entrantes. El zoom solo acerca la vista.</p>' +
          campo('Nombre del edificio', 'beNombre', edificioElegido.nombre) +
          '<details class="me-config"><summary>Forma, color y medidas del edificio</summary>' +
          editorEstilo(edificioElegido, 'be', edificioElegido.tipo === 'EDIFICIO' ? '#c7e4ef' : '#e5dbc2') +
          '<div class="me-scale-editor"><strong>Escala proporcional</strong><p>Conserva la forma y adapta también las oficinas y detalles interiores. 50 % reduce a la mitad; 200 % duplica.</p>' +
          '<div class="me-row"><button type="button" class="me-mini" data-scale-building="0.75">−25 %</button>' +
          '<button type="button" class="me-mini" data-scale-building="0.9">−10 %</button>' +
          '<button type="button" class="me-mini" data-scale-building="1.1">+10 %</button>' +
          '<button type="button" class="me-mini" data-scale-building="1.25">+25 %</button></div>' +
          campo('Escalar desde el tamaño actual (%)', 'beEscala', 100, 'number', 5, 500) + '</div>' +
          '<div class="me-scale-editor"><strong>Girar edificio</strong><p>El giro incluye la huella, las oficinas y sus activos dibujados.</p>' +
          '<div class="me-row"><button type="button" class="me-mini" data-rotate-building="-90">↶ 90°</button>' +
          '<button type="button" class="me-mini" data-rotate-building="-15">↶ 15°</button>' +
          '<button type="button" class="me-mini" data-rotate-building="15">15° ↷</button>' +
          '<button type="button" class="me-mini" data-rotate-building="90">90° ↷</button></div>' +
          campo('Ángulo del edificio (grados)', 'beGiro', edificioElegido.giro || 0, 'number', 0, 360) + '</div>' +
          campo('Posición horizontal X', 'beX', edificioElegido.x, 'number', 0, 2000) +
          campo('Posición vertical Y', 'beY', edificioElegido.y, 'number', 0, 2000) +
          campo('Ancho del edificio', 'beAncho', edificioElegido.ancho, 'number', .1, 2000) +
          campo('Fondo del edificio', 'beFondo', edificioElegido.fondo, 'number', .1, 2000) + '</details>' +
          '<div class="me-row">' + boton('Abrir pisos y oficinas', 'openSelectedBuilding', 'me-action-primary') +
          boton('Quitar punto seleccionado', 'removeVertex') + boton('Deseleccionar', 'endVertices') + '</div></section>';
        if (edita) {
          var vista = s.escena.vista || marcoAutomatico();
          h += '<details class="me-details me-view-settings" open><summary>Campo visual del predio</summary><p>Define cuánto terreno se ve al ajustar. Aumenta ancho y fondo para alejarte; usa zoom y arrastra el plano para recorrerlo.</p>' +
            campo('Inicio horizontal X', 'vistaX', vista.x, 'number', -5000, 5000) +
            campo('Inicio vertical Y', 'vistaY', vista.y, 'number', -5000, 5000) +
            campo('Ancho visible', 'vistaAncho', vista.ancho, 'number', 100, 5000) +
            campo('Fondo visible', 'vistaFondo', vista.fondo, 'number', 100, 5000) +
            boton('Encuadre automático', 'autoView') + '</details>';
        }
        if (edita && s.tieneFondo) {
          var f = s.escena.fondo || { x: 0, y: 0, ancho: 1000, fondo: 700, opacidad: .5, giro: 0 };
          h += '<details class="me-details"><summary>Imagen de referencia</summary><p class="me-muted">Ajusta el fondo visual del plano. No representa medidas reales.</p>' +
            campo('Posición horizontal de la imagen', 'fondoX', f.x, 'number', 0, 2000) +
            campo('Posición vertical de la imagen', 'fondoY', f.y, 'number', 0, 2000) +
            campo('Ancho visual de la imagen', 'fondoAncho', f.ancho, 'number', .1, 2000) +
            campo('Fondo visual de la imagen', 'fondoFondo', f.fondo, 'number', .1, 2000) +
            '<p class="me-muted">Ancho y fondo mantienen la proporción original de la imagen.</p>' +
            campo('Opacidad (0 a 1)', 'fondoOpacidad', f.opacidad, 'number', 0, 1) +
            campo('Giro (grados)', 'fondoGiro', f.giro, 'number', 0, 360) + '</details>';
        }
        h += '<div class="me-resumen"><span>' + s.escena.edificios.length + ' construcciones</span></div>';
        if (edita) h += '<div class="me-list">' + s.escena.edificios.map(function (b) { return '<button class="me-item" data-open-building="' + esc(b.id) + '"><strong>' + esc(b.nombre) + '</strong><small>' + (b.tipo === 'EDIFICIO' ? 'Edificio' : 'Oficina independiente') + ' · ' + b.pisos.length + ' nivel(es) · ' + (b.resumenOficinas == null ? contarOficinas(b.pisos.reduce(function (todos, n) { return todos.concat(n.espacios); }, [])) : b.resumenOficinas) + ' oficina(s)</small></button>'; }).join('') + '</div>';
        else if (s.busquedaSeleccionada) {
          var lugar = s.busquedaSeleccionada;
          h += '<section class="me-search-selection"><span class="me-tag">' + esc(lugar.tipo === 'ACTIVO' ? 'ACTIVO' : lugar.tipo === 'RESPONSABLE' ? 'RESPONSABLE' : lugar.tipo === 'OFICINA' ? 'OFICINA' : 'EDIFICIO') + '</span><h3>' + esc(lugar.etiqueta) + '</h3>' +
            '<p>' + esc(lugar.oficinaNombre || lugar.edificioNombre || lugar.detalle || 'Sin ubicación dibujada') +
            (lugar.edificioNombre && lugar.oficinaNombre ? ' · ' + esc(lugar.edificioNombre) : '') + (lugar.pisoNombre ? ' · ' + esc(lugar.pisoNombre) : '') +
            (lugar.lugar && lugar.lugar.etiqueta ? ' · ' + esc(lugar.lugar.etiqueta) : '') + '</p>' +
            (lugar.detalle ? '<p class="me-muted">' + esc(lugar.detalle) + '</p>' : '') +
            (lugar.edificioId ? boton(lugar.espacioId ? 'Abrir oficina' : 'Entrar al edificio', 'openSearchResult', 'me-action-primary') : '<p class="me-muted">Esta oficina aún no se ha vinculado al plano publicado.</p>') + '</section>';
          if (lugar.tipo === 'RESPONSABLE' && lugar.personaId) {
            var otras = s.resultadosBusqueda.map(function (r, i) { return { dato: r, indice: i }; }).filter(function (r) {
              return r.dato.personaId === lugar.personaId && r.dato !== lugar;
            });
            if (otras.length) h += '<h3>Otras ubicaciones de esta persona</h3><div class="me-list">' + otras.map(function (r) {
              return '<button class="me-item" data-search-index="' + r.indice + '"><strong>' + esc(r.dato.lugar && r.dato.lugar.etiqueta || 'Oficina sin plano') + '</strong><small>' + esc(r.dato.edificioNombre || 'Sin ubicación dibujada') + (r.dato.pisoNombre ? ' · ' + esc(r.dato.pisoNombre) : '') + '</small></button>';
            }).join('') + '</div>';
          }
        } else h += '<p class="me-muted">Busca un edificio u oficina arriba, o selecciona una construcción en el mapa. El plano interior se abre al entrar.</p>';
      } else if (!p) {
        h = '<h2>' + esc(e.nombre) + '</h2><span class="me-tag">' + (e.tipo === 'EDIFICIO' ? 'EDIFICIO' : 'OFICINA INDEPENDIENTE') + '</span>';
        h += '<p class="me-muted">Altura total del modelo: ' + alturaTotal(e) + ' unidades visuales · ' + e.pisos.length + ' piso(s). Ajusta la altura de cada piso por separado.</p>';
        if (edita) h += '<p class="me-instruccion">Selecciona un piso para distribuir oficinas. Su altura se ajusta desde el modelo 3D o el panel del piso.</p>' + campo('Nombre del edificio o construcción', 'nombre', e.nombre) + '<div class="me-row">' + boton('+ Añadir otro piso', 'addFloor', 'me-action-primary') + boton(s.modo === 'vertices' ? 'Terminar contorno' : 'Editar contorno', 'editBuilding') + '</div>' + '<details class="me-config"><summary>Presentación y medidas del edificio</summary><label class="me-field">Presentación<select data-prop="tipo"><option value="EDIFICIO"' + (e.tipo === 'EDIFICIO' ? ' selected' : '') + '>Edificio</option><option value="OFICINA_INDEPENDIENTE"' + (e.tipo !== 'EDIFICIO' ? ' selected' : '') + '>Oficina independiente</option></select></label>' + editorEstilo(e, '', e.tipo === 'EDIFICIO' ? '#c7e4ef' : '#e5dbc2') + medidas(e, true) + campo('Giro de toda la construcción (grados)', 'giroEdificio', e.giro || 0, 'number', 0, 360) + '<div class="me-row">' + boton('Quitar punto', 'removeVertex') + boton('Eliminar construcción', 'deleteBuilding', 'me-danger') + '</div></details>';
        h += '<h3>Pisos de este edificio</h3><div class="me-list">' + e.pisos.map(function (n) { return '<button class="me-item" data-open-floor="' + esc(n.id) + '"><strong>' + (s.vista3d ? 'Ver en 3D: ' : 'Entrar a ') + esc(n.nombre) + ' →</strong><small>Altura ' + esc(n.altura) + ' · ' + contarOficinas(n.espacios) + ' oficinas · ' + n.espacios.length + ' espacios</small></button>'; }).join('') + '</div>';
      } else if (!a) {
        h = '<h2>' + esc(p.nombre) + '</h2><p>' + esc(e.nombre) + '</p>';
        h += controlesPlanoReferencia(p, 'piso');
        if (s.vista3d) h += '<p class="me-muted">Altura del piso: ' + esc(p.altura) + ' unidades visuales. Al elegir un piso se ocultan los superiores para ver sus oficinas.</p>' +
          '<div class="me-floor-tabs" aria-label="Elegir piso">' + e.pisos.map(function (n) {
            return '<button type="button" class="me-mini' + (n.id === p.id ? ' me-floor-active' : '') + '" data-open-floor="' + esc(n.id) + '">' + esc(n.nombre) + '</button>';
          }).join('') + '</div>';
        if (edita) h += '<p class="me-instruccion">' + (s.vista3d ? 'Arrastra el control verde del piso o cambia su altura aquí. Pulsa una oficina en el modelo para abrir su plano 2D.' : 'Añade oficinas dentro de esta planta y vincúlalas a registros existentes. Pasillos y servicios pueden quedar sin vínculo.') + '</p>' + campo('Nombre del piso', 'nombre', p.nombre) + campo('Altura visual de este piso', 'altura', p.altura, 'number', 1, 20) + '<div class="me-row">' + boton('+ Añadir oficina', 'addOffice', 'me-action-primary') + boton('+ Pasillo', 'addHall') + boton('+ Otro espacio', 'addSpace') + (e.pisos.length > 1 ? boton('Eliminar piso', 'deleteFloor', 'me-danger') : '') + '</div>';
        var elegido = s.editarEspacio && p.espacios.find(function (r) { return r.id === s.editarEspacio; });
        if (edita && elegido) h += '<section class="me-selected-editor"><h3>' + esc(elegido.nombre) + '</h3><p class="me-muted">Arrastra la figura para moverla; arrastra sus puntos para recortar o estirar. Los círculos blancos añaden lados.</p>' +
          '<label class="me-field">Vincular oficina del predio<input class="sm-input me-buscar-oficina" data-office-target="' + esc(elegido.id) + '" placeholder="Buscar por código o nombre" aria-label="Buscar oficina de este predio"></label><div class="me-resultados-oficina me-list"></div>' +
          '<p class="me-muted">' + esc(nombreOficina(elegido.oficinaId)) + '. Al seleccionar una oficina se aplican su código y nombre.</p>' +
          campo('Nombre visible (editable)', 'selNombre', elegido.nombre) + '<details class="me-config"><summary>Forma y medidas de esta oficina</summary>' + editorEstilo(elegido, 'sel', elegido.oficinaId ? '#cde9ee' : '#e6e9e8') +
          campo('Posición horizontal X', 'selX', elegido.x, 'number', 0, e.ancho) +
          campo('Posición vertical Y', 'selY', elegido.y, 'number', 0, e.fondo) +
          campo('Ancho del espacio', 'selAncho', elegido.ancho, 'number', .1, e.ancho) +
          campo('Fondo del espacio', 'selFondo', elegido.fondo, 'number', .1, e.fondo) +
          campo('Giro del espacio (grados)', 'selGiro', elegido.giro || 0, 'number', 0, 360) + '</details>' +
          '<div class="me-row">' + boton('Abrir oficina y activos', 'openSelectedSpace', 'me-action-primary') + boton('Quitar punto seleccionado', 'removeVertex') + boton('Deseleccionar', 'endVertices') + '</div></section>';
        h += '<div class="me-resumen"><span>' + contarOficinas(p.espacios) + ' oficinas vinculadas</span><span>' + p.espacios.length + ' espacios dibujados</span></div>';
        if (p.trazos && p.trazos.length) h += '<h3>Paredes y detalles</h3><div class="me-list">' + p.trazos.map(function (t) { return '<div class="me-detail-row"><span>' + esc(t.tipo) + '</span>' + (edita ? boton('Eliminar', 'deleteLine:' + esc(t.id), 'me-danger') : '') + '</div>'; }).join('') + '</div>';
        h += '<h3>Espacios de esta planta</h3><div class="me-list">' + p.espacios.map(function (r) { return '<button class="me-item" data-open-space="' + esc(r.id) + '"><strong>' + esc(r.nombre) + '</strong><small>' + esc(r.oficinaId ? nombreOficina(r.oficinaId) : r.tipo + ' · sin oficina vinculada') + '</small></button>'; }).join('') + '</div>';
      } else {
        h = '<h2>' + esc(a.nombre) + '</h2><p>' + esc(p.nombre) + ' · ' + esc(e.nombre) + '</p>';
        h += controlesPlanoReferencia(a, 'espacio');
        h += '<h3>Oficina registrada</h3><p>' + esc(nombreOficina(a.oficinaId)) + '</p>';
        if (edita) {
          h += '<details class="me-config"' + (!a.oficinaId ? ' open' : '') + '><summary>Editar este espacio y su oficina</summary>' +
            '<p class="me-instruccion">Elige primero una oficina activa de este predio. Su código y nombre se aplicarán automáticamente al espacio; el nombre visible se puede ajustar después.</p>' +
            '<label class="me-field">Oficina de la base de datos<input class="sm-input me-buscar-oficina" data-office-target="' + esc(a.id) + '" placeholder="Buscar por código o nombre" aria-label="Buscar oficina de este predio"></label><div class="me-resultados-oficina me-list"></div>' +
            campo('Nombre visible (editable)', 'nombre', a.nombre) + '<label class="me-field">Tipo<select data-prop="tipo">' + ['OFICINA', 'CUARTO', 'PASILLO', 'BAÑO', 'OTRO'].map(function (t) { return '<option value="' + t + '"' + (a.tipo === t ? ' selected' : '') + '>' + t + '</option>'; }).join('') + '</select></label>' + editorEstilo(a, '', a.oficinaId ? '#cde9ee' : '#e6e9e8') + medidas(a, false) + campo('Giro del espacio (grados)', 'giroEspacio', a.giro || 0, 'number', 0, 360) + boton('Editar contorno en el piso', 'editSpace') +
            (a.oficinaId ? boton('Desvincular oficina', 'unlinkOffice') : '') + boton('Eliminar espacio', 'deleteSpace', 'me-danger') + '</details>';
          if (a.oficinaId) h += boton('+ Añadir otro espacio en el piso', 'addRoom');
        }
        h += '<section class="me-interior-section"><h3>Cuartos interiores <span class="me-count">' + a.cuartos.length + '</span></h3>' +
          '<p class="me-muted">Cada cuarto se dibuja dentro de este espacio; sigue perteneciendo a la misma oficina.</p>' +
          (edita ? boton('+ Dibujar cuarto por puntos', 'drawRoom', 'me-action-primary') : '') +
          '<div class="me-list">' + a.cuartos.map(function (c) { return '<button class="me-item' + (s.cuartoSeleccionado === c.id ? ' me-item-selected' : '') + '" data-open-room="' + esc(c.id) + '"><strong>' + esc(c.nombre) + '</strong><small>Cuarto interior · ' + c.puntos.length + ' puntos</small></button>'; }).join('') +
          (a.cuartos.length ? '' : '<p class="me-muted">Todavía no hay cuartos dibujados.</p>') + '</div>';
        if (edita && cuarto()) h += '<div class="me-selected-editor"><h4>Editar cuarto</h4>' + campo('Nombre del cuarto', 'cuartoNombre', cuarto().nombre) +
          '<p class="me-muted">Arrastra los puntos naranjas o los lados. Los círculos blancos agregan un punto.</p>' +
          boton('Quitar punto', 'removeVertex') + boton('Eliminar cuarto', 'deleteInteriorRoom', 'me-danger') + '</div>';
        h += '</section>';
        h += '<section class="me-interior-section"><h3>Mobiliario de referencia <span class="me-count">' + a.mobiliario.length + '</span></h3>' +
          '<p class="me-muted">Estas figuras ayudan a distribuir el espacio; no crean registros de activos.</p>' +
          (edita ? '<div class="me-furniture-palette">' + FIGURAS.map(function (tipo) { return '<button type="button" class="me-mini" data-add-furniture="' + tipo + '">+ ' + (tipo === 'OTRO' ? 'Otra figura' : tipo.charAt(0) + tipo.slice(1).toLowerCase()) + '</button>'; }).join('') + '</div>' : '') +
          '<div class="me-list">' + a.mobiliario.map(function (m) { return '<button class="me-item' + (s.muebleSeleccionado === m.id ? ' me-item-selected' : '') + '" data-open-furniture="' + esc(m.id) + '"><strong>' + esc(m.nombre) + '</strong><small>' + esc(m.figura) + '</small></button>'; }).join('') + '</div>';
        if (edita && mueble()) h += '<div class="me-selected-editor"><h4>Editar mobiliario</h4>' + campo('Nombre visible', 'muebleNombre', mueble().nombre) +
          selectorFigura('muebleFigura', mueble().figura) + campo('Ancho visual', 'muebleAncho', mueble().ancho, 'number', .1, a.ancho) +
          campo('Fondo visual', 'muebleFondo', mueble().fondo, 'number', .1, a.fondo) +
          campo('Giro (grados)', 'muebleGiro', mueble().giro || 0, 'number', 0, 360) +
          boton('Eliminar figura', 'removeFurniture', 'me-danger') + '</div>';
        h += '</section>';
        h += '<section class="me-interior-section me-assets-section"><div class="me-asset-panel-title"><h3>Ubicación de activos</h3><button type="button" class="me-mini" data-show-placed>Ubicados (' + a.activos.length + ')</button></div>' +
          '<p class="me-muted">Arrastra un símbolo para moverlo. Mayús + arrastrar mantiene un eje.</p>';
        if (edita && a.oficinaId) {
          var maxTamano = Math.min(12, a.ancho, a.fondo), minTamano = Math.min(.5, maxTamano);
          var tamanoGeneral = Math.min(maxTamano, Number(a.tamanoMarcador) || 3);
          h += '<label class="me-field me-size-field">Tamaño general de los marcadores<input type="range" data-prop="tamanoGeneral" min="' + minTamano + '" max="' + maxTamano + '" step="0.25" value="' + tamanoGeneral + '"><output>' + tamanoGeneral + '</output></label>' +
            '<p class="me-muted">El cambio ajusta todos los activos de esta oficina; también será el tamaño inicial de los próximos.</p>';
        }
        if (s.activoSeleccionado) { var z = a.activos.find(function (t) { return Number(t.activoId) === Number(s.activoSeleccionado); }); if (z) { var d = Object.assign({}, s.activos[z.activoId] || {}, s.detallesActivos[z.activoId] || {});
          h += '<div class="me-selected-editor me-asset-editor"><div class="me-asset-editor-head"><span class="me-asset-symbol" aria-hidden="true">' + iconoMini(iconoActivo(d), d.auxiliarId) + '</span><div><h4>Seleccionado: ' + esc(d.codigo || z.activoId) + '</h4><button type="button" class="me-mini" data-show-asset-detail="' + z.activoId + '">Ver ficha del activo</button></div></div><p class="me-muted">X ' + esc(z.x) + ' · Y ' + esc(z.y) + ' · Para afinar, enfoca el plano y usa las flechas (Mayús: paso grande).</p>' +
            (d.oficinaId != null && Number(d.oficinaId) !== Number(a.oficinaId) ? '<p class="me-asset-warning">La oficina oficial cambió. Reubica o quita este marcador.</p>' : '') +
            (edita ? '<label class="me-field">Tamaño de este activo<input type="number" data-prop="tamanoIndividual" min="0.1" max="' + Math.min(12, a.ancho, a.fondo) + '" step="0.25" value="' + esc(z.ancho) + '"></label>' +
              selectorIconoAuxiliar(d) + '<details class="me-details"><summary>Giro opcional</summary>' + campo('Giro (grados)', 'giro', z.giro, 'number', 0, 360) + '</details>' + boton('Quitar del plano', 'removeAsset', 'me-danger') : '') + '</div>'; }
          else h += '<button type="button" class="me-mini" data-show-asset-detail="' + s.activoSeleccionado + '">Ver ficha del activo seleccionado</button>';
        }
        h += '</section>';
        if (a.oficinaId) h += '<h3>Inventario de esta oficina</h3><label class="me-field">Buscar en el inventario<input class="me-filtro-inventario" placeholder="Código o descripción" value="' + esc(s.inventario && s.inventario.oficinaId === a.oficinaId ? s.inventario.q : '') + '"></label><div class="me-inventario"></div>';
      }
      panel.innerHTML = h;
      s.panelStageKey = stageKey;
      if (configAbierta && panel.querySelector('.me-config')) panel.querySelector('.me-config').open = true;
      if (medidasAbiertas && panel.querySelector('.me-details')) panel.querySelector('.me-details').open = true;
      organizarPanel(e, p, a, stageKey);
      mostrarInventario();
    }
    function referenciaActual() { return espacio() || piso(); }
    function controlesPlanoReferencia(objeto, nombre) {
      var ref = objeto.planoReferencia;
      if (!edita && !ref) return '';
      var h = '<details class="me-details me-reference-settings"><summary>Plano de referencia del ' + nombre + '</summary>';
      h += '<p class="me-muted">Imagen visual para calcar; las posiciones y medidas siguen siendo referenciales.</p>';
      if (edita) h += boton(ref ? 'Sustituir imagen' : 'Subir PNG o JPG', 'uploadReference') +
        (ref ? boton('Quitar imagen', 'removeReference', 'me-danger') : '');
      if (ref && edita) h += campo('Posición horizontal X', 'refX', ref.x, 'number', -2000, 2000) +
        campo('Posición vertical Y', 'refY', ref.y, 'number', -2000, 2000) +
        campo('Ancho visual', 'refAncho', ref.ancho, 'number', .1, 2000) +
        campo('Fondo visual', 'refFondo', ref.fondo, 'number', .1, 2000) +
        campo('Opacidad (0 a 1)', 'refOpacidad', ref.opacidad, 'number', 0, 1) +
        campo('Giro (grados)', 'refGiro', ref.giro, 'number', 0, 360);
      return h + '</details>';
    }
    function medidas(o, building) { return '<details class="me-details"><summary>Posición y tamaño (opcional)</summary><p>Unidades visuales referenciales, sin metros reales. También puedes arrastrar la figura para moverla.</p>' +
      campo('Posición horizontal (X) ' + (building ? 'en el predio' : 'en el piso'), 'x', o.x, 'number', 0, 2000) +
      campo('Posición vertical (Y) ' + (building ? 'en el predio' : 'en el piso'), 'y', o.y, 'number', 0, 2000) +
      campo('Ancho: izquierda a derecha', 'ancho', o.ancho, 'number', .1, 2000) +
      campo('Fondo: arriba a abajo', 'fondo', o.fondo, 'number', .1, 2000) + '</details>'; }
    function selectorFigura(prop, valor) { return '<label class="me-field">Figura<select data-prop="' + prop + '">' + FIGURAS.map(function (f) { return '<option value="' + f + '"' + (f === valor ? ' selected' : '') + '>' + (f === 'OTRO' ? 'Otra figura' : f.charAt(0) + f.slice(1).toLowerCase()) + '</option>'; }).join('') + '</select></label>'; }
    function anguloEtiqueta(obj) {
      if (obj.textoGiro != null) return Number(obj.textoGiro);
      if (!obj.puntos || obj.puntos.length < 2) return 0;
      var mejor = 0, largo = 0;
      obj.puntos.forEach(function (p, i) {
        var q = obj.puntos[(i + 1) % obj.puntos.length], dx = q.x - p.x, dy = q.y - p.y;
        var d = dx * dx + dy * dy;
        if (d > largo) { largo = d; mejor = Math.atan2(dy, dx) * 180 / Math.PI; }
      });
      if (mejor > 90) mejor -= 180;
      if (mejor < -90) mejor += 180;
      return mejor;
    }
    function colorDeTexto(fondo) {
      var hex = /^#[0-9a-fA-F]{6}$/.test(fondo || '') ? fondo.slice(1) : 'cde9ee';
      var r = parseInt(hex.slice(0, 2), 16), g = parseInt(hex.slice(2, 4), 16), b = parseInt(hex.slice(4), 16);
      return r * .299 + g * .587 + b * .114 < 140 ? '#ffffff' : '#173e53';
    }
    function tono(hex, factor) {
      var valor = /^#[0-9a-fA-F]{6}$/.test(hex || '') ? hex.slice(1) : 'd9eff8';
      return '#' + [0, 2, 4].map(function (i) {
        return ('0' + Math.round(parseInt(valor.slice(i, i + 2), 16) * factor).toString(16)).slice(-2);
      }).join('');
    }
    function puntoEtiqueta(obj, puntos) {
      var cx = obj.x + obj.ancho / 2, cy = obj.y + obj.fondo / 2;
      if (!puntos || contienePunto(puntos, cx, cy)) return { x: cx, y: cy };
      for (var radio = .1; radio <= .5; radio += .1) {
        for (var i = 0; i < 12; i++) {
          var ang = i * Math.PI / 6, x = cx + Math.cos(ang) * obj.ancho * radio, y = cy + Math.sin(ang) * obj.fondo * radio;
          if (contienePunto(puntos, x, y)) return { x: x, y: y };
        }
      }
      return { x: cx, y: cy };
    }
    function dibujarRect(parent, obj, kind, fill, title) {
      var color = obj.color || fill;
      var attrs = { x: obj.x, y: obj.y, width: obj.ancho, height: obj.fondo, rx: 2, fill: color,
        'fill-opacity': kind === 'building' && s.editarEdificio === obj.id && s.tieneFondo ? .42 :
          kind === 'space' && piso() && piso().planoReferencia && s.capas.fondo ? .48 :
          kind === 'room' && espacio() && espacio().planoReferencia && s.capas.fondo ? .48 : 1,
        stroke: '#477088', 'stroke-width': 1.6, 'vector-effect': 'non-scaling-stroke', class: 'me-shape' + ((kind === 'building' && (s.edificio === obj.id || s.editarEdificio === obj.id)) || (kind === 'space' && (s.espacio === obj.id || s.editarEspacio === obj.id)) || (kind === 'room' && s.cuartoSeleccionado === obj.id) ? ' me-selected' : '') +
          (kind === 'space' && s.ubicacionDestacada && s.ubicacionDestacada.espacioId === obj.id ? ' me-found-space' : ''), 'data-kind': kind, 'data-id': obj.id || obj.activoId };
      if (kind === 'asset' && obj.giro) attrs.transform = 'rotate(' + obj.giro + ' ' + (obj.x + obj.ancho / 2) + ' ' + (obj.y + obj.fondo / 2) + ')';
      var puntos = (kind !== 'asset' && obj.puntos && obj.puntos.length >= 3) ? obj.puntos : null;
      if (puntos) attrs.points = puntos.map(function (pt) { return pt.x + ',' + pt.y; }).join(' ');
      var r = svg(puntos ? 'polygon' : 'rect', attrs);
      r.appendChild(svg('title', {}, title));
      parent.appendChild(r);
      var box = canvas.viewBox.baseVal;
      var escala = Math.max(box.width / Math.max(1, canvas.clientWidth), box.height / Math.max(1, canvas.clientHeight));
      var tam = Math.min(Math.max(.005, escala * 13 / s.zoom), obj.ancho / 3, obj.fondo / 2.6);
      if (tam < .005 || kind === 'asset' && obj.ancho < tam * 2) return;
      var angulo = kind === 'asset' ? 0 : anguloEtiqueta(obj);
      var longitud = Math.abs(Math.sin(angulo * Math.PI / 180)) > .7 ? obj.fondo : obj.ancho;
      var limite = Math.max(3, Math.floor((longitud - tam * 1.4) / (tam * .54)));
      var palabras = String(title).split(/\s+/), lineas = [''];
      palabras.forEach(function (palabra) {
        var ultima = lineas.length - 1;
        if (lineas[ultima] && (lineas[ultima] + ' ' + palabra).length > limite && lineas.length < 2) lineas.push('');
        ultima = lineas.length - 1;
        lineas[ultima] += (lineas[ultima] ? ' ' : '') + palabra;
      });
      lineas = lineas.map(function (linea) { return linea.length > limite ? linea.slice(0, Math.max(1, limite - 1)) + '…' : linea; });
      if (obj.fondo < tam * 3) lineas = lineas.slice(0, 1);
      var clipId = 'me-clip-' + uid();
      var defs = svg('defs'); var clip = svg('clipPath', { id: clipId });
      clip.appendChild(puntos ? svg('polygon', { points: attrs.points }) : svg('rect', { x: obj.x + 2, y: obj.y + 1, width: Math.max(.1, obj.ancho - 4), height: Math.max(.1, obj.fondo - 2) }));
      defs.appendChild(clip); parent.appendChild(defs);
      var centro = puntoEtiqueta(obj, puntos);
      var etiqueta = svg('text', { x: centro.x, y: centro.y, 'font-size': tam,
        'data-base-font': Math.max(.005, escala * 13), 'data-max-font': Math.min(obj.ancho / 3, obj.fondo / 2.6), 'text-anchor': 'middle',
        'dominant-baseline': 'middle', style: 'fill:' + colorDeTexto(color), 'clip-path': 'url(#' + clipId + ')',
        transform: 'rotate(' + angulo + ' ' + centro.x + ' ' + centro.y + ')', 'pointer-events': 'none' });
      lineas.forEach(function (linea, i) {
        var factorLinea = i === 0 ? (lineas.length === 1 ? 0 : -.55) : 1.1;
        etiqueta.appendChild(svg('tspan', { x: centro.x, dy: tam * factorLinea, 'data-line-factor': factorLinea }, linea));
      });
      etiqueta.appendChild(svg('title', {}, title));
      parent.appendChild(etiqueta);
    }
    function dibujarElemento(obj, vinculado, titulo, alerta) {
      var tipo = FIGURAS.indexOf(obj.figura) >= 0 ? obj.figura : 'OTRO';
      var x = obj.x, y = obj.y, w = obj.ancho, h = obj.fondo;
      var infoActivo = vinculado ? Object.assign({}, s.activos[obj.activoId] || {}, s.detallesActivos[obj.activoId] || {}) : null;
      var color = alerta ? '#f5adb0' : vinculado ? '#7dc7e2' : '#f5bf77';
      var seleccionado = vinculado ? Number(obj.activoId) === Number(s.activoSeleccionado) : obj.id === s.muebleSeleccionado;
      var grupo = svg('g', { class: 'me-shape me-elemento' + (vinculado ? ' me-elemento-activo' : ' me-elemento-mueble') + (seleccionado ? ' me-elemento-seleccionado' : ''),
        'data-kind': vinculado ? 'asset' : 'furniture', 'data-id': vinculado ? obj.activoId : obj.id,
        transform: 'rotate(' + Number(obj.giro || 0) + ' ' + (x + w / 2) + ' ' + (y + h / 2) + ')' });
      grupo.appendChild(svg('rect', { x: x, y: y, width: w, height: h, rx: Math.min(w, h) * .12,
        fill: color, stroke: seleccionado ? '#ec8737' : '#30677f', 'stroke-width': seleccionado ? 2.5 : 1.5,
        'vector-effect': 'non-scaling-stroke' }));
      var trazo = { stroke: '#265870', 'stroke-width': 1, 'vector-effect': 'non-scaling-stroke', 'pointer-events': 'none' };
      if (vinculado) {
        var icono = iconoActivo(infoActivo);
        if (icono === 'PERSONALIZADO' && infoActivo.auxiliarId) {
          var urlImagen = urlIconoAuxiliar(infoActivo.auxiliarId);
          var imagenIcono = svg('image', { x: x + w * .1, y: y + h * .1, width: w * .8, height: h * .8,
            href: urlImagen, preserveAspectRatio: 'xMidYMid meet', 'pointer-events': 'none' });
          imagenIcono.setAttributeNS('http://www.w3.org/1999/xlink', 'xlink:href', urlImagen);
          grupo.appendChild(imagenIcono);
        } else {
          var marca = svg('svg', { x: x + w * .12, y: y + h * .12, width: w * .76, height: h * .76,
            viewBox: '0 0 24 24', preserveAspectRatio: 'xMidYMid meet', 'pointer-events': 'none' });
          marca.appendChild(svg('path', { d: TRAZOS_ICONO[icono] || TRAZOS_ICONO.OTRO, fill: 'none', stroke: '#16465e',
            'stroke-width': 1.8, 'stroke-linecap': 'round', 'stroke-linejoin': 'round' }));
          grupo.appendChild(marca);
        }
      } else if (tipo === 'SILLA') grupo.appendChild(svg('rect', { x: x + w * .2, y: y + h * .23, width: w * .6, height: h * .55, rx: Math.min(w, h) * .12,
        fill: '#fff', 'fill-opacity': .65, stroke: '#30677f', 'stroke-width': 1, 'vector-effect': 'non-scaling-stroke', 'pointer-events': 'none' }));
      else if (tipo === 'MESA') grupo.appendChild(svg('rect', { x: x + w * .1, y: y + h * .12, width: w * .8, height: h * .76, rx: Math.min(w, h) * .08,
        fill: '#fff', 'fill-opacity': .55, stroke: '#30677f', 'stroke-width': 1, 'vector-effect': 'non-scaling-stroke', 'pointer-events': 'none' }));
      else if (tipo === 'EQUIPO') {
        grupo.appendChild(svg('rect', { x: x + w * .13, y: y + h * .12, width: w * .74, height: h * .62, fill: '#e9f7fc',
          stroke: '#30677f', 'stroke-width': 1, 'vector-effect': 'non-scaling-stroke', 'pointer-events': 'none' }));
        grupo.appendChild(svg('line', Object.assign({ x1: x + w * .32, y1: y + h * .86, x2: x + w * .68, y2: y + h * .86 }, trazo)));
      } else if (tipo === 'ESTANTE' || tipo === 'ARCHIVADOR') {
        [0.34, 0.67].forEach(function (parte) { grupo.appendChild(svg('line', Object.assign({ x1: x + w * .12, y1: y + h * parte,
          x2: x + w * .88, y2: y + h * parte }, trazo))); });
      }
      grupo.appendChild(svg('title', {}, titulo));
      canvas.appendChild(grupo);
    }
    function cuartoDeElemento(a, z) {
      var centroX = z.x + z.ancho / 2, centroY = z.y + z.fondo / 2;
      return (a.cuartos || []).find(function (c) { return contienePunto(c.puntos, centroX, centroY); });
    }
    function marcoAutomatico() {
      var lista = s.escena.edificios.slice();
      if (s.tieneFondo && s.capas.fondo && s.escena.fondo) lista.push(s.escena.fondo);
      if (!lista.length) return { x: 0, y: 0, ancho: 1000, fondo: 700 };
      var x1 = Math.min.apply(null, lista.map(function (b) { return b.x; })) - 30;
      var y1 = Math.min.apply(null, lista.map(function (b) { return b.y; })) - 30;
      var x2 = Math.max.apply(null, lista.map(function (b) { return b.x + b.ancho; })) + 30;
      var y2 = Math.max.apply(null, lista.map(function (b) { return b.y + b.fondo; })) + 30;
      return { x: redondear(x1), y: redondear(y1), ancho: redondear(Math.max(260, x2 - x1)), fondo: redondear(Math.max(190, y2 - y1)) };
    }
    function ajustarVistaPredio() {
      var v = s.escena.vista || marcoAutomatico();
      canvas.setAttribute('viewBox', v.x + ' ' + v.y + ' ' + v.ancho + ' ' + v.fondo);
    }
    function centrarEdificios() {
      var edificios = s.escena.edificios;
      if (!edificios.length || s.vista3d || contexto() !== 'predio') return false;
      var box = canvas.viewBox.baseVal;
      var minX = Math.min.apply(null, edificios.map(function (e) { return e.x; }));
      var minY = Math.min.apply(null, edificios.map(function (e) { return e.y; }));
      var maxX = Math.max.apply(null, edificios.map(function (e) { return e.x + e.ancho; }));
      var maxY = Math.max.apply(null, edificios.map(function (e) { return e.y + e.fondo; }));
      s.zoom = Math.max(1, Math.min(6, box.width / Math.max(80, maxX - minX + 80), box.height / Math.max(80, maxY - minY + 80)));
      s.panX = (minX + maxX) / 2 - (box.x + box.width / 2);
      s.panY = (minY + maxY) / 2 - (box.y + box.height / 2);
      return true;
    }
    function puntosTexto(puntos) { return puntos.map(function (pt) { return pt.x + ',' + pt.y; }).join(' '); }
    function cuadricula() {
      if (!s.cuadricula || s.vista3d) return;
      var box = canvas.viewBox.baseVal, defs = svg('defs');
      var patron = svg('pattern', { id: 'me-grid', width: 25, height: 25, patternUnits: 'userSpaceOnUse' });
      patron.appendChild(svg('path', { d: 'M 25 0 L 0 0 0 25', fill: 'none', stroke: '#c7d9e1', 'stroke-width': .6,
        'vector-effect': 'non-scaling-stroke' }));
      defs.appendChild(patron); canvas.appendChild(defs);
      canvas.appendChild(svg('rect', { x: box.x, y: box.y, width: box.width, height: box.height, fill: 'url(#me-grid)', 'pointer-events': 'none' }));
    }
    function dibujarFondo() {
      if (!s.tieneFondo || !s.capas.fondo) return;
      var f = s.escena.fondo || { x: 0, y: 0, ancho: 1000, fondo: 700, opacidad: .5, giro: 0 };
      var img = svg('image', { x: f.x, y: f.y, width: f.ancho, height: f.fondo,
        opacity: f.opacidad, preserveAspectRatio: 'xMidYMid meet', 'pointer-events': 'none',
        transform: 'rotate(' + f.giro + ' ' + (f.x + f.ancho / 2) + ' ' + (f.y + f.fondo / 2) + ')' });
      var urlFondo = BASE + '/predios/' + s.predioId + '/fondo' + (edita ? '' : '/publicado') + '?v=' + s.fondoRevision;
      img.setAttribute('href', urlFondo);
      img.setAttributeNS('http://www.w3.org/1999/xlink', 'href', urlFondo);
      canvas.appendChild(img);
    }
    function dibujarPlanoReferencia(objeto) {
      if (!objeto || !objeto.planoReferencia || !s.capas.fondo) return;
      var f = objeto.planoReferencia;
      var img = svg('image', { x: f.x, y: f.y, width: f.ancho, height: f.fondo,
        opacity: f.opacidad, preserveAspectRatio: 'none', 'pointer-events': 'none',
        transform: 'rotate(' + f.giro + ' ' + (f.x + f.ancho / 2) + ' ' + (f.y + f.fondo / 2) + ')' });
      var url = BASE + '/predios/' + s.predioId + '/planos/' + encodeURIComponent(f.archivo);
      img.setAttribute('href', url);
      img.setAttributeNS('http://www.w3.org/1999/xlink', 'href', url);
      canvas.appendChild(img);
    }
    function dibujarVertices(o) {
      if (!o || !o.puntos || (s.modo !== 'vertices' && !(contexto() === 'predio' && s.editarEdificio === o.id) && !(contexto() === 'piso' && s.editarEspacio === o.id) && !(contexto() === 'espacio' && s.cuartoSeleccionado === o.id))) return;
      var vb = canvas.viewBox.baseVal;
      var radioBase = 6 * Math.max(vb.width / Math.max(1, canvas.clientWidth), vb.height / Math.max(1, canvas.clientHeight));
      var r = Math.min(7, radioBase / s.zoom);
      o.puntos.forEach(function (pt, i) {
        var siguiente = o.puntos[(i + 1) % o.puntos.length];
        var lado = svg('circle', { cx: (pt.x + siguiente.x) / 2, cy: (pt.y + siguiente.y) / 2, r: r * .85,
          'data-base-radius': radioBase * .85,
          class: 'me-add-vertex', 'data-add-index': i, 'aria-label': 'Clic para añadir punto; arrastrar para mover lado' });
        lado.appendChild(svg('title', {}, 'Clic: añadir punto · Arrastrar: mover este lado'));
        canvas.appendChild(lado);
        canvas.appendChild(svg('circle', { cx: pt.x, cy: pt.y, r: r, 'data-base-radius': radioBase,
          class: 'me-vertex' + (s.vertice === i ? ' me-vertex-selected' : ''), 'data-vertex-index': i }));
      });
      if (contexto() === 'predio' && s.editarEdificio === o.id) {
        var rx = o.x + o.ancho / 2, ry = o.y - r * 3.2;
        canvas.appendChild(svg('line', { x1: rx, y1: o.y, x2: rx, y2: ry,
          stroke: '#17835d', 'stroke-width': 1.5, 'vector-effect': 'non-scaling-stroke', 'pointer-events': 'none' }));
        var giro = svg('circle', { cx: rx, cy: ry, r: r * 1.4,
          class: 'me-rotate-handle', 'data-rotate-building-handle': o.id });
        giro.appendChild(svg('title', {}, 'Arrastra para girar el edificio'));
        canvas.appendChild(giro);
        canvas.appendChild(svg('text', { x: rx, y: ry, 'font-size': r * 1.7, 'text-anchor': 'middle',
          'dominant-baseline': 'middle', class: 'me-rotate-symbol', 'pointer-events': 'none' }, '↻'));
        var hx = o.x + o.ancho + r * 2.5, hy = o.y + o.fondo + r * 2.5;
        canvas.appendChild(svg('line', { x1: o.x + o.ancho, y1: o.y + o.fondo, x2: hx, y2: hy,
          stroke: '#1779aa', 'stroke-width': 1.5, 'vector-effect': 'non-scaling-stroke', 'pointer-events': 'none' }));
        var tirador = svg('rect', { x: hx - r, y: hy - r, width: r * 2, height: r * 2,
          class: 'me-scale-handle', 'data-scale-building-handle': o.id });
        tirador.appendChild(svg('title', {}, 'Arrastra para cambiar el tamaño del edificio proporcionalmente'));
        canvas.appendChild(tirador);
      }
    }
    function dibujarTrazos() {
      if (!s.capas.detalles || !piso()) return;
      (piso().trazos || []).forEach(function (t) {
        var estilo = { MURO: ['#29465d', 3.5], PUERTA: ['#ad7337', 2.5], VENTANA: ['#1685b1', 2.5], ESCALERA: ['#7765ab', 4] }[t.tipo] || ['#29465d', 2];
        var linea = svg('line', { x1: t.x1, y1: t.y1, x2: t.x2, y2: t.y2, stroke: estilo[0], 'stroke-width': estilo[1],
          'stroke-linecap': 'round', 'vector-effect': 'non-scaling-stroke', class: 'me-line', 'data-line-id': t.id });
        linea.appendChild(svg('title', {}, t.tipo)); canvas.appendChild(linea);
      });
    }
    function dibujarBorrador() {
      if (!s.dibujo.length) return;
      var linea = s.modo === 'polygonBuilding' || s.modo === 'polygonOffice' || s.modo === 'polygonRoom';
      var box = canvas.viewBox.baseVal;
      var radio = 5 * Math.max(box.width / Math.max(1, canvas.clientWidth), box.height / Math.max(1, canvas.clientHeight)) / s.zoom;
      canvas.appendChild(svg('polyline', { points: puntosTexto(s.dibujo), fill: 'none', stroke: '#dc712b',
        'stroke-width': 2.5, 'stroke-dasharray': linea ? '4 3' : '', 'vector-effect': 'non-scaling-stroke', 'pointer-events': 'none' }));
      s.dibujo.forEach(function (pt) { canvas.appendChild(svg('circle', { cx: pt.x, cy: pt.y, r: radio,
        'data-base-radius': radio * s.zoom, 'data-max-radius': 100000, fill: '#dc712b', 'pointer-events': 'none' })); });
      if (linea) {
        var ultimo = s.dibujo[s.dibujo.length - 1];
        canvas.appendChild(svg('line', { x1: ultimo.x, y1: ultimo.y, x2: ultimo.x, y2: ultimo.y,
          class: 'me-draft-cursor', stroke: '#dc712b', 'stroke-width': 2,
          'stroke-dasharray': '5 4', 'vector-effect': 'non-scaling-stroke', 'pointer-events': 'none' }));
        if (s.dibujo.length > 1) canvas.appendChild(svg('line', { x1: s.dibujo[0].x, y1: s.dibujo[0].y,
          x2: ultimo.x, y2: ultimo.y, class: 'me-draft-close', stroke: '#dc712b', 'stroke-width': 1.2,
          'stroke-dasharray': '3 5', 'vector-effect': 'non-scaling-stroke', 'pointer-events': 'none' }));
      }
    }
    function actualizarEtiquetasZoom() {
      Array.prototype.forEach.call(canvas.querySelectorAll('[data-base-font]'), function (n) {
        var tamano = Math.min(Number(n.dataset.baseFont) / s.zoom, Number(n.dataset.maxFont));
        n.setAttribute('font-size', tamano);
        Array.prototype.forEach.call(n.querySelectorAll('[data-line-factor]'), function (linea) {
          linea.setAttribute('dy', tamano * Number(linea.dataset.lineFactor));
        });
      });
    }
    var etiquetasZoomPendientes = null;
    function programarEtiquetasZoom() {
      if (etiquetasZoomPendientes) clearTimeout(etiquetasZoomPendientes);
      etiquetasZoomPendientes = setTimeout(function () {
        etiquetasZoomPendientes = null;
        if (!root.isConnected || s.vista3d) return;
        if (vistaFueraDeRegion()) pintar();
        else actualizarEtiquetasZoom();
      }, 180);
    }
    function regionConMargen(box) {
      var ancho = box.width / s.zoom, alto = box.height / s.zoom;
      var centroX = box.x + box.width / 2 + s.panX, centroY = box.y + box.height / 2 + s.panY;
      return { x1: centroX - ancho * 2.5, y1: centroY - alto * 2.5,
        x2: centroX + ancho * 2.5, y2: centroY + alto * 2.5 };
    }
    function vistaFueraDeRegion() {
      if (!s.regionDibujo) return false;
      if (s.zoom <= 3) return true;
      var box = canvas.viewBox.baseVal, region = s.regionDibujo;
      return box.x < region.x1 + box.width * .5 || box.y < region.y1 + box.height * .5 ||
        box.x + box.width > region.x2 - box.width * .5 || box.y + box.height > region.y2 - box.height * .5;
    }
    function cruzaRegion(o, region) {
      return o.x + o.ancho >= region.x1 && o.x <= region.x2 && o.y + o.fondo >= region.y1 && o.y <= region.y2;
    }
    function aplicarZoom(actualizarControles, actualizarEtiquetas) {
      var box = s.baseVista;
      if (!box) return;
      s.vistaVersion = (s.vistaVersion || 0) + 1;
      var w = box.width / s.zoom, h = box.height / s.zoom;
      canvas.setAttribute('viewBox', (box.x + (box.width - w) / 2 + s.panX) + ' ' +
        (box.y + (box.height - h) / 2 + s.panY) + ' ' + w + ' ' + h);
      if (actualizarControles === false) return;
      Array.prototype.forEach.call(canvas.querySelectorAll('[data-base-radius]'), function (n) {
        var limiteRadio = n.dataset.maxRadius ? Number(n.dataset.maxRadius) : n.classList.contains('me-add-vertex') ? 5.95 : 7;
        n.setAttribute('r', Math.min(limiteRadio, Number(n.dataset.baseRadius) / s.zoom));
      });
      if (actualizarEtiquetas !== false) actualizarEtiquetasZoom();
    }
    function acercar(factor, ev, diferirEtiquetas) {
      var anterior = s.zoom, siguiente = Math.max(ZOOM_MIN, Math.min(ZOOM_MAX, anterior * factor));
      if (siguiente === anterior) return;
      if (ev) {
        var punto = puntoSvg(ev), box = canvas.viewBox.baseVal;
        s.panX += (punto.x - box.x - box.width / 2) * (1 - anterior / siguiente);
        s.panY += (punto.y - box.y - box.height / 2) * (1 - anterior / siguiente);
      }
      s.zoom = siguiente;
      if (s.vista3d) repintarMovimiento();
      else {
        if (etiquetasZoomPendientes && !diferirEtiquetas) { clearTimeout(etiquetasZoomPendientes); etiquetasZoomPendientes = null; }
        aplicarZoom(true, !diferirEtiquetas);
        if (diferirEtiquetas) {
          if (s.regionDibujo && s.zoom <= 3) { pintar(); return; }
          programarEtiquetasZoom();
        }
        else if (vistaFueraDeRegion()) { pintar(); return; }
        actualizarZoomIndicador();
      }
    }
    function dibujar3d() {
      if (window.MapaEspacial3D) window.MapaEspacial3D.dibujar(canvas, s, {
        svg: svg, tono: tono, puntosRect: puntosRect, edita: edita
      });
      else canvas.setAttribute('viewBox', '0 0 1000 700');
    }
    function cargar3d() {
      if (window.MapaEspacial3D) return Promise.resolve();
      if (window.MapaEspacial3DCarga) return window.MapaEspacial3DCarga;
      window.MapaEspacial3DCarga = new Promise(function (resolve, reject) {
        var script = document.createElement('script');
        script.src = '/assets/js/mapa-espacial-3d.js?v=20261014-localizar';
        script.onload = function () { window.MapaEspacial3D ? resolve() : reject(new Error('No se pudo iniciar la vista 3D.')); };
        script.onerror = function () { reject(new Error('No se pudo cargar la vista 3D.')); };
        document.head.appendChild(script);
      }).catch(function (error) { window.MapaEspacial3DCarga = null; throw error; });
      return window.MapaEspacial3DCarga;
    }
    function pintar() {
      if (etiquetasZoomPendientes) { clearTimeout(etiquetasZoomPendientes); etiquetasZoomPendientes = null; }
      s.regionDibujo = null;
      var e = edificio(), p = piso(), a = espacio();
      root.classList.toggle('me-office-view', !!a && !s.vista3d);
      var viewport = a ? 's:' + a.id : p ? 'p:' + p.id : e ? 'e:' + e.id : 'predio';
      if (s.viewport !== viewport) { s.viewport = viewport; s.zoom = viewport === 'predio' ? (edita && s.tieneFondo ? 2.2 : 1.4) : 1; s.panX = s.panY = 0; s.marco3dFijo = null; }
      canvas.innerHTML = '';
      canvas.style.cursor = drag && (drag.kind === 'orbit' || drag.kind === 'pan') ? 'grabbing' :
        s.vista3d || s.modo === 'pan' || s.espacioPulsado ? 'grab' :
          (s.modo === 'polygonBuilding' || s.modo === 'polygonOffice' || s.modo === 'polygonRoom' || ['MURO', 'PUERTA', 'VENTANA', 'ESCALERA'].indexOf(s.modo) >= 0) ? 'crosshair' : '';
      root.querySelector('.me-ruta').textContent = [sel.options[sel.selectedIndex] && sel.options[sel.selectedIndex].text, e && e.nombre, p && p.nombre, a && a.nombre].filter(Boolean).join(' › ');
      root.querySelector('.me-volver').style.display = e || s.editarEdificio ? '' : 'none';
      Array.prototype.forEach.call(root.querySelectorAll('.me-pasos [data-paso]'), function (paso) {
        var nombre = paso.dataset.paso;
        paso.disabled = (nombre === 'edificio' && !e) || (nombre === 'piso' && !p) || (nombre === 'espacio' && !a);
        paso.classList.toggle('me-actual', nombre === (a ? 'espacio' : p ? 'piso' : e ? 'edificio' : 'predio'));
      });
      if (root.querySelector('.me-vista')) root.querySelector('.me-vista').textContent = s.vista3d ? 'Volver a 2D' : 'Abrir modelo 3D';
      var guiaExplorar = root.querySelector('.me-explore-guide');
      if (guiaExplorar) guiaExplorar.textContent = s.vista3d ? 'Arrastra para girar · Rueda para acercar · Botón derecho para mover' :
        'Arrastra el fondo para mover · Rueda para acercar · Clic para entrar';
      if (s.vista3d) dibujar3d();
      else if (!e) {
        ajustarVistaPredio();
        if (s.enfoqueInicial) { s.enfoqueInicial = false; centrarEdificios(); }
        dibujarFondo(); cuadricula();
        if (s.capas.edificios) s.escena.edificios.forEach(function (b) { dibujarRect(canvas, b, 'building', b.tipo === 'EDIFICIO' ? '#c7e4ef' : '#e5dbc2', b.nombre); });
        dibujarVertices(objetoEnVertices());
      } else if (!p) {
        canvas.setAttribute('viewBox', (e.x - 25) + ' ' + (e.y - 25) + ' ' + (e.ancho + 50) + ' ' + (e.fondo + 50));
        cuadricula();
        dibujarRect(canvas, e, 'building', '#c7e4ef', e.nombre);
        dibujarVertices(e);
      } else if (!a) {
        canvas.setAttribute('viewBox', '-5 -5 ' + (e.ancho + 10) + ' ' + (e.fondo + 10));
        if (p.espacios.length > 24 && s.zoom > 3) s.regionDibujo = regionConMargen(canvas.viewBox.baseVal);
        var local = (e.puntos || puntosRect(e)).map(function (pt) { return { x: redondearFino(pt.x - e.x), y: redondearFino(pt.y - e.y) }; });
        canvas.appendChild(svg('polygon', { points: puntosTexto(local), fill: '#f7fafb', stroke: e.color || '#678298', 'stroke-width': 1.8, 'vector-effect': 'non-scaling-stroke' }));
        dibujarPlanoReferencia(p);
        cuadricula();
        if (s.capas.espacios) p.espacios.forEach(function (r) {
          if (!s.regionDibujo || cruzaRegion(r, s.regionDibujo)) dibujarRect(canvas, r, 'space', r.oficinaId ? '#cde9ee' : '#e6e9e8', r.nombre);
        });
        dibujarTrazos();
        dibujarVertices(objetoEnVertices());
      } else {
        canvas.setAttribute('viewBox', '-2 -2 ' + (a.ancho + 4) + ' ' + (a.fondo + 4));
        var puntosLocal = (a.puntos || puntosRect(a)).map(function (pt) { return { x: redondearFino(pt.x - a.x), y: redondearFino(pt.y - a.y) }; });
        canvas.appendChild(svg('polygon', { points: puntosTexto(puntosLocal), fill: '#fbfaf6', stroke: a.color || '#678298', 'stroke-width': 1.8, 'vector-effect': 'non-scaling-stroke' }));
        dibujarPlanoReferencia(a);
        cuadricula();
        (a.cuartos || []).forEach(function (c) { dibujarRect(canvas, c, 'room', '#edf5df', c.nombre); });
        (a.mobiliario || []).forEach(function (m) { dibujarElemento(m, false, m.nombre + ' · mobiliario de referencia', false); });
        a.activos.forEach(function (z) { var info = s.activos[z.activoId] || {}; dibujarElemento(z, true,
          (info.codigo || z.activoId) + ' · ' + (info.descripcion || 'Activo'),
          info.oficinaId != null && Number(info.oficinaId) !== Number(a.oficinaId)); });
        dibujarVertices(cuarto());
      }
      dibujarBorrador();
      var vista = canvas.viewBox.baseVal;
      s.baseVista = { x: vista.x, y: vista.y, width: vista.width, height: vista.height };
      aplicarZoom(); actualizarHerramientas();
      renderPanel();
      if (a && a.oficinaId && (!s.inventario || s.inventario.oficinaId !== a.oficinaId)) cargarInventario(a.oficinaId, '', 0);
    }
    function limpiarHerramienta() { s.modo = ''; s.dibujo = []; s.tipoDibujo = 'EDIFICIO'; s.editarEdificio = null; s.editarEspacio = null; s.vertice = -1; }
    function volver() { limpiarHerramienta(); if (s.espacio) { s.espacio = null; s.activoSeleccionado = s.cuartoSeleccionado = s.muebleSeleccionado = null; } else if (s.piso) { s.piso = null; if (!edita) s.vista3d = true; } else { s.edificio = null; if (!edita) s.vista3d = true; } if (!s.edificio) { s.enfoqueInicial = edita; s.destacadoEdificio = s.busquedaSeleccionada && s.busquedaSeleccionada.edificioId; } pintar(); }
    root.querySelector('.me-volver').addEventListener('click', volver);
    var botonEstudio = root.querySelector('.me-ampliar');
    if (window.matchMedia && window.matchMedia('(min-width: 900px)').matches) {
      root.classList.add('me-ampliado'); botonEstudio.textContent = edita ? '✕ Salir del estudio' : '✕ Salir de vista ampliada';
    }
    botonEstudio.addEventListener('click', function () {
      root.classList.toggle('me-ampliado');
      this.textContent = root.classList.contains('me-ampliado') ? (edita ? '✕ Salir del estudio' : '✕ Salir de vista ampliada') : (edita ? '⛶ Abrir estudio' : '⛶ Ampliar vista');
      pintar();
    });
    var accionesRapidas = root.querySelector('.me-canvas-actions');
    if (accionesRapidas) accionesRapidas.addEventListener('click', function (ev) {
      var boton = ev.target.closest('[data-quick-tool],[data-quick-action],[data-quick-tab]');
      if (!boton || boton.disabled) return;
      if (boton.dataset.quickTab) { activarPanel(boton.dataset.quickTab); return; }
      if (boton.dataset.quickTool) { var herramienta = root.querySelector('.me-herramientas [data-tool="' + boton.dataset.quickTool + '"]'); if (herramienta) herramienta.click(); return; }
      if (boton.dataset.quickAction === 'addOffice') { agregarEspacio('OFICINA'); return; }
      if (boton.dataset.quickAction === 'addHall') { agregarEspacio('PASILLO'); return; }
      var accion = root.querySelector('.me-herramientas [data-action="' + boton.dataset.quickAction + '"]'); if (accion) accion.click();
    });
    var tabsPanel = root.querySelector('.me-panel-tabs');
    if (tabsPanel) tabsPanel.addEventListener('click', function (ev) {
      var boton = ev.target.closest('[data-panel-tab]'); if (boton) activarPanel(boton.dataset.panelTab);
    });
    panel.addEventListener('input', function (ev) {
      if (!ev.target.classList.contains('me-list-filter')) return;
      s.filtroConstrucciones = ev.target.value; aplicarFiltroConstrucciones();
    });
    Array.prototype.forEach.call(root.querySelectorAll('.me-nuevo-edificio'), function (b) {
      b.addEventListener('click', function () { agregarEdificio(b.dataset.tipo); });
    });
    root.querySelector('.me-pasos').addEventListener('click', function (ev) {
      var b = ev.target.closest('[data-paso]'); if (!b || b.disabled) return;
      limpiarHerramienta();
      if (b.dataset.paso === 'predio') { s.edificio = s.piso = s.espacio = null; s.enfoqueInicial = edita; s.destacadoEdificio = s.busquedaSeleccionada && s.busquedaSeleccionada.edificioId; if (!edita) s.vista3d = true; }
      else if (b.dataset.paso === 'edificio') { s.piso = s.espacio = null; if (!edita) s.vista3d = true; }
      else if (b.dataset.paso === 'piso') { s.espacio = null; s.vista3d = false; }
      else if (b.dataset.paso === 'espacio') s.vista3d = false;
      pintar();
    });
    root.querySelector('.me-vista') && root.querySelector('.me-vista').addEventListener('click', function () {
      limpiarHerramienta(); s.zoom = contexto() === 'predio' ? (edita && s.tieneFondo ? 2.2 : 1.4) : 1; s.panX = s.panY = 0; s.marco3dFijo = null;
      var botonVista = this;
      if (s.vista3d) { s.cargaVista3d++; s.vista3d = false; pintar(); return; }
      var solicitud = ++s.cargaVista3d;
      botonVista.disabled = true;
      avisar('Cargando modelo 3D…', false);
      cargar3d().then(function () {
        botonVista.disabled = false;
        if (solicitud !== s.cargaVista3d || !root.isConnected) return;
        s.espacio = null; s.vista3d = true; pintar();
      }).catch(function (error) {
        botonVista.disabled = false;
        if (solicitud !== s.cargaVista3d || !root.isConnected) return;
        s.vista3d = false; pintar(); avisar(error.message, true);
      });
    });
    function guardarBorrador() {
      if (!s.predioId) return Promise.reject(new Error('Selecciona un predio.'));
      var predio = s.predioId, instantanea = JSON.stringify(s.escena);
      return request(BASE + '/predios/' + predio, { method: 'PUT', headers: { 'Accept': 'application/json', 'Content-Type': 'application/json' }, body: '{"version":' + s.version + ',"escena":' + instantanea + '}' })
        .then(function (d) {
          if (s.predioId !== predio) throw new Error('El predio cambió durante el guardado. Vuelve a cargarlo.');
          s.version = d.version; s.guardado = instantanea; s.dirty = JSON.stringify(s.escena) !== instantanea;
          avisar(s.dirty ? 'Hay cambios posteriores al guardado' : 'Borrador guardado', false); actualizarHerramientas(); return d;
        });
    }
    root.querySelector('.me-guardar') && root.querySelector('.me-guardar').addEventListener('click', function () {
      guardarBorrador().catch(function (e) { avisar(e.message, true); });
    });
    root.querySelector('.me-publicar') && root.querySelector('.me-publicar').addEventListener('click', function () {
      var boton = this, predio = s.predioId;
      if (!predio || boton.disabled) return;
      confirmarModal('Publicar este predio', 'La versión guardada quedará visible de inmediato para quienes exploran el mapa. Si hay cambios pendientes, primero se guardarán en el borrador.', 'Publicar versión').then(function (aceptado) {
      if (!aceptado) return;
      boton.disabled = true;
      (s.dirty || s.version < 0 ? guardarBorrador() : Promise.resolve()).then(function () {
        if (s.predioId !== predio) throw new Error('El predio cambió durante la publicación.');
        if (s.dirty) throw new Error('Hubo cambios mientras se guardaba. Guarda de nuevo antes de publicar.');
        return request(BASE + '/predios/' + predio + '/publicaciones', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ version: s.version }) });
      }).then(function (d) {
        s.version = d.version; s.numeroPublicacion = d.numero; actualizarPublicacion(); cargarPublicaciones(); notificarPublicacion(predio, d.numero); avisar('Versión ' + d.numero + ' publicada y visible en el explorador', false);
      }).catch(function (e) { avisar(e.message, true); }).then(function () { boton.disabled = false; });
      });
    });
    var listaPublicaciones = root.querySelector('.me-publicaciones-lista');
    if (listaPublicaciones) listaPublicaciones.addEventListener('click', function (ev) {
      var boton = ev.target.closest('[data-restaurar]'); if (!boton || !s.predioId) return;
      confirmarModal('Recuperar versión ' + boton.dataset.restaurar, 'Esta versión sustituirá el borrador actual. El explorador conservará la publicación vigente hasta que publiques de nuevo.', 'Recuperar en borrador').then(function (aceptado) {
      if (!aceptado) return;
      var predio = s.predioId;
      boton.disabled = true;
      request(BASE + '/predios/' + predio + '/publicaciones/' + boton.dataset.restaurar + '/restaurar',
        { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ version: s.version }) })
        .then(function () { if (s.predioId === predio) { s.dirty = false; cambiarPredio(predio); avisar('Versión recuperada en el borrador', false); } })
        .catch(function (e) { avisar(e.message, true); boton.disabled = false; });
      });
    });
    root.querySelector('.me-predio').addEventListener('change', function () { cambiarPredio(this.value); });
    var consultaMapa = 0, tiempoMapa;
    var resultadosMapa = root.querySelector('.me-search-results');
    var tipoBusqueda = root.querySelector('.me-search-type');
    if (filtro && resultadosMapa) {
      function seleccionarResultado(indice) {
        var elegido = s.resultadosBusqueda[Number(indice)]; if (!elegido) return;
        ++consultaMapa; resultadosMapa.hidden = true; filtro.value = elegido.etiqueta;
        s.busquedaSeleccionada = elegido; s.destacadoEdificio = elegido.edificioId || null; s.ubicacionDestacada = elegido;
        s.destacados = {};
        var relacionados = elegido.tipo === 'RESPONSABLE' && elegido.personaId ? s.resultadosBusqueda.filter(function (r) { return r.personaId === elegido.personaId; }) : [elegido];
        relacionados.forEach(function (r) {
          if (!r.edificioId) return;
          if (!s.destacados[r.edificioId]) s.destacados[r.edificioId] = [];
          if (r.pisoId && s.destacados[r.edificioId].indexOf(r.pisoId) < 0) s.destacados[r.edificioId].push(r.pisoId);
        });
        s.q = ''; s.edificio = s.piso = s.espacio = null; s.marco3dFijo = null; s.panX = s.panY = 0; s.viewport = null;
        if (elegido.tipo === 'RESPONSABLE' && elegido.personaId) {
          var predioPersona = s.predioId;
          request(BASE + '/predios/' + predioPersona + '/localizar?tipo=RESPONSABLE&personaId=' + encodeURIComponent(elegido.personaId)).then(function (datos) {
            if (s.predioId !== predioPersona || s.busquedaSeleccionada !== elegido) return;
            s.resultadosBusqueda = datos.map(function (r) { return Object.assign({}, r.lugar || {}, r); });
            s.busquedaSeleccionada = s.resultadosBusqueda.find(function (r) {
              return r.responsableId === elegido.responsableId && r.espacioId === elegido.espacioId;
            }) || elegido;
            s.destacados = {};
            s.resultadosBusqueda.forEach(function (r) {
              if (!r.edificioId) return;
              if (!s.destacados[r.edificioId]) s.destacados[r.edificioId] = [];
              if (r.pisoId && s.destacados[r.edificioId].indexOf(r.pisoId) < 0) s.destacados[r.edificioId].push(r.pisoId);
            });
            pintar();
          }).catch(function (error) { avisar(error.message, true); });
        }
        if (!elegido.edificioId) { pintar(); avisar('La oficina oficial todavía no tiene ubicación en el plano publicado.', false); return; }
        cargar3d().then(function () { s.vista3d = true; pintar(); }).catch(function (error) { avisar(error.message, true); });
      }
      filtro.addEventListener('input', function () {
        var q = this.value.trim(), token = ++consultaMapa, predio = s.predioId, tipo = tipoBusqueda ? tipoBusqueda.value : 'LUGAR';
        clearTimeout(tiempoMapa);
        if (!q || !predio) { resultadosMapa.hidden = true; s.resultadosBusqueda = []; return; }
        if (tipo !== 'LUGAR' && q.length < 2) { resultadosMapa.hidden = false; resultadosMapa.innerHTML = '<p class="me-muted">Escribe al menos dos caracteres.</p>'; return; }
        resultadosMapa.hidden = false; resultadosMapa.innerHTML = '<p class="me-muted">Buscando en este predio…</p>';
        tiempoMapa = setTimeout(function () {
          var url = BASE + '/predios/' + predio + (tipo === 'LUGAR' ? '/ubicaciones?q=' : '/localizar?tipo=' + encodeURIComponent(tipo) + '&q=') + encodeURIComponent(q);
          request(url).then(function (datos) {
            if (token !== consultaMapa || predio !== s.predioId) return;
            s.resultadosBusqueda = datos.map(function (r) { return Object.assign({}, r.lugar || {}, r); }); resultadosMapa.hidden = false;
            resultadosMapa.innerHTML = s.resultadosBusqueda.map(function (r, i) { return '<button type="button" data-search-index="' + i + '"><strong>' + esc(r.etiqueta) + '</strong><small>' +
              esc([r.oficinaNombre, r.edificioNombre, r.pisoNombre, r.lugar && r.lugar.etiqueta, r.detalle].filter(Boolean).join(' · ') || 'Edificio del predio') + '</small></button>'; }).join('') || '<p class="me-muted">No se encontraron coincidencias en este predio.</p>';
          }).catch(function (error) { if (token === consultaMapa) resultadosMapa.innerHTML = '<p class="me-status-error">' + esc(error.message) + '</p>'; });
        }, 200);
      });
      if (tipoBusqueda) tipoBusqueda.addEventListener('change', function () {
        ++consultaMapa; clearTimeout(tiempoMapa); resultadosMapa.hidden = true; s.resultadosBusqueda = [];
        filtro.value = ''; filtro.placeholder = this.value === 'ACTIVO' ? 'Código o descripción del activo' : this.value === 'RESPONSABLE' ? 'Nombre, CI o código' : 'Nombre o código de oficina';
        filtro.focus();
      });
      filtro.addEventListener('focus', function () { if (this.value.trim()) this.dispatchEvent(new Event('input', { bubbles: true })); });
      filtro.addEventListener('keydown', function (ev) { if (ev.key === 'Escape') { resultadosMapa.hidden = true; this.blur(); } });
      resultadosMapa.addEventListener('click', function (ev) {
        var botonResultado = ev.target.closest('[data-search-index]'); if (!botonResultado) return;
        seleccionarResultado(botonResultado.dataset.searchIndex);
      });
      panel.addEventListener('click', function (ev) {
        var otra = ev.target.closest('[data-search-index]'); if (otra) seleccionarResultado(otra.dataset.searchIndex);
      });
      root.addEventListener('click', function (ev) { if (!ev.target.closest('.me-map-search')) resultadosMapa.hidden = true; });
    }
    function resaltar() {
      Array.prototype.forEach.call(canvas.querySelectorAll('.me-shape'), function (n) {
        var id = n.getAttribute('data-id'), b = s.escena.edificios.find(function (e) { return e.id === id; });
        var r = piso() && piso().espacios.find(function (x) { return x.id === id; });
        var a = espacio() && espacio().activos.find(function (x) { return String(x.activoId) === id; });
        var c = espacio() && espacio().cuartos.find(function (x) { return x.id === id; });
        var m = espacio() && espacio().mobiliario.find(function (x) { return x.id === id; });
        var label = b ? b.nombre : r ? r.nombre + ' ' + nombreOficina(r.oficinaId) : a ? ((s.activos[a.activoId] || {}).codigo || '') + ' ' + ((s.activos[a.activoId] || {}).descripcion || '') : c ? c.nombre : m ? m.nombre : '';
        n.style.opacity = !s.q || label.toUpperCase().indexOf(s.q) >= 0 ? '1' : '.22';
      });
    }
    var drag = null, ignorarClick = false, repintadoPendiente = false;
    var vista2dPendiente = false, ruedaPendiente = false, ruedaAcumulada = 0, ruedaEvento = null, ruedaVista = null;
    function programarVista2d() {
      if (vista2dPendiente) return;
      vista2dPendiente = true;
      (window.requestAnimationFrame || function (fn) { return setTimeout(fn, 16); })(function () {
        if (!vista2dPendiente) return;
        vista2dPendiente = false;
        if (root.isConnected && !s.vista3d) {
          aplicarZoom(false);
          if (vistaFueraDeRegion()) pintar();
        }
      });
    }
    function puntoSvg(ev) { var punto = canvas.createSVGPoint(); punto.x = ev.clientX; punto.y = ev.clientY; return punto.matrixTransform(canvas.getScreenCTM().inverse()); }
    function puntoConMatriz(ev, matriz) {
      return { x: matriz.a * ev.clientX + matriz.c * ev.clientY + matriz.e,
        y: matriz.b * ev.clientX + matriz.d * ev.clientY + matriz.f };
    }
    function puntoVerticeArrastrado(movimiento, ev) {
      var actual = movimiento.vistaVersion === s.vistaVersion ? puntoConMatriz(ev, movimiento.matrizInversa) : puntoSvg(ev);
      var original = movimiento.original;
      var x = ajustarNumero(original.x + actual.x - movimiento.inicio.x);
      var y = ajustarNumero(original.y + actual.y - movimiento.inicio.y);
      if (ev.shiftKey) {
        if (Math.abs(x - original.x) >= Math.abs(y - original.y)) y = original.y;
        else x = original.x;
      }
      return { x: Math.max(0, Math.min(movimiento.maxX, x)),
        y: Math.max(0, Math.min(movimiento.maxY, y)) };
    }
    function umbralEnPlano(pixeles) { var b = canvas.viewBox.baseVal; return pixeles * Math.max(b.width / Math.max(1, canvas.clientWidth), b.height / Math.max(1, canvas.clientHeight)); }
    function repintarMovimiento() {
      if (repintadoPendiente) return;
      repintadoPendiente = true;
      (window.requestAnimationFrame || function (fn) { return setTimeout(fn, 16); })(function () {
        repintadoPendiente = false;
        if (root.isConnected) pintar();
      });
    }
    function fijarMarco3d() {
      if (s.marco3dFijo) return;
      var box = canvas.viewBox.baseVal, ancho = box.width * s.zoom, alto = box.height * s.zoom;
      s.marco3dFijo = { x: box.x - (ancho - box.width) / 2 - s.panX,
        y: box.y - (alto - box.height) / 2 - s.panY, ancho: ancho, alto: alto };
    }
    function actualizarNavegacion(ev) {
      if (!drag) return;
      if (drag.kind === 'orbit') {
        s.camara = ((drag.angulo + (ev.clientX - drag.x) * (edita ? .14 : .1)) % 360 + 360) % 360;
        s.inclinacion = Math.max(edita ? 12 : 8, Math.min(edita ? 78 : 85, drag.inclinacion + (ev.clientY - drag.y) * (edita ? .1 : .08)));
        repintarMovimiento();
      } else if (drag.kind === 'pan') {
        if (Math.abs(ev.clientX - drag.x) + Math.abs(ev.clientY - drag.y) > 2) drag.movio = true;
        s.panX = drag.panX - (ev.clientX - drag.x) * drag.unidadesPorPixel;
        s.panY = drag.panY - (ev.clientY - drag.y) * drag.unidadesPorPixel;
        if (s.vista3d) repintarMovimiento(); else programarVista2d();
      }
    }
    function anguloArrastrado(movimiento, ev) {
      var pt = puntoSvg(ev);
      var final = Math.atan2(pt.y - movimiento.centro.y, pt.x - movimiento.centro.x);
      return ((Math.round((final - movimiento.angulo) * 180 / Math.PI) + 540) % 360) - 180;
    }
    function figuraCabe(o) {
      if (o.x < 0 || o.y < 0 || o.x + o.ancho > 2000 || o.y + o.fondo > 2000 || o.ancho < .1 || o.fondo < .1) return false;
      if (o.tipo === 'CUARTO_INTERIOR' && espacio() &&
          (o.x + o.ancho > espacio().ancho + .01 || o.y + o.fondo > espacio().fondo + .01)) return false;
      if (o.pisos) return o.pisos.every(function (p) {
        return p.espacios.every(function (r) { return r.x >= 0 && r.y >= 0 && r.x + r.ancho <= o.ancho + .01 && r.y + r.fondo <= o.fondo + .01 && figuraCabe(r); }) &&
          (p.trazos || []).every(function (t) { return Math.min(t.x1, t.x2, t.y1, t.y2) >= 0 && Math.max(t.x1, t.x2) <= o.ancho && Math.max(t.y1, t.y2) <= o.fondo; });
      });
      if (o.activos) return o.activos.concat(o.mobiliario || []).every(function (z) { return z.x >= 0 && z.y >= 0 && z.x + z.ancho <= o.ancho + .01 && z.y + z.fondo <= o.fondo + .01; }) &&
        (o.cuartos || []).every(function (c) { return c.x >= 0 && c.y >= 0 && c.x + c.ancho <= o.ancho + .01 && c.y + c.fondo <= o.fondo + .01 && poligonoSimple(c.puntos); });
      return true;
    }
    function escalarEdificio(o, factor) {
      if (!o || !isFinite(factor) || factor < .05 || factor > 5) { avisar('La escala debe estar entre 5 % y 500 %.', true); return; }
      if (factor === 1) return;
      var anterior = JSON.stringify(s.escena);
      o.puntos.forEach(function (pt) {
        pt.x = redondear(o.x + (pt.x - o.x) * factor);
        pt.y = redondear(o.y + (pt.y - o.y) * factor);
      });
      var limites = caja(o.puntos);
      o.ancho = redondear(limites.ancho); o.fondo = redondear(limites.fondo);
      (o.pisos || []).forEach(function (p) {
        escalarReferencia(p.planoReferencia, factor);
        p.espacios.forEach(function (r) {
          escalarReferencia(r.planoReferencia, factor);
          r.x = redondear(r.x * factor); r.y = redondear(r.y * factor);
          r.ancho = redondear(r.ancho * factor); r.fondo = redondear(r.fondo * factor);
          (r.puntos || []).forEach(function (pt) { pt.x = redondear(pt.x * factor); pt.y = redondear(pt.y * factor); });
          (r.activos || []).forEach(function (a) {
            a.x = redondear(a.x * factor); a.y = redondear(a.y * factor);
            a.ancho = redondear(a.ancho * factor); a.fondo = redondear(a.fondo * factor);
          });
          (r.mobiliario || []).forEach(function (m) {
            m.x = redondear(m.x * factor); m.y = redondear(m.y * factor);
            m.ancho = redondear(m.ancho * factor); m.fondo = redondear(m.fondo * factor);
          });
          (r.cuartos || []).forEach(function (c) {
            c.x = redondear(c.x * factor); c.y = redondear(c.y * factor);
            c.ancho = redondear(c.ancho * factor); c.fondo = redondear(c.fondo * factor);
            c.puntos.forEach(function (pt) { pt.x = redondear(pt.x * factor); pt.y = redondear(pt.y * factor); });
          });
        });
        (p.trazos || []).forEach(function (t) {
          t.x1 = redondear(t.x1 * factor); t.x2 = redondear(t.x2 * factor);
          t.y1 = redondear(t.y1 * factor); t.y2 = redondear(t.y2 * factor);
        });
      });
      var figurasValidas = poligonoSimple(o.puntos) && (o.pisos || []).every(function (p) {
        return p.espacios.every(function (r) {
          return poligonoSimple(r.puntos) && (r.activos || []).concat(r.mobiliario || []).every(function (a) { return a.ancho >= .1 && a.fondo >= .1; }) &&
            (r.cuartos || []).every(function (c) { return c.ancho >= .1 && c.fondo >= .1 && poligonoSimple(c.puntos); });
        }) && (p.trazos || []).every(function (t) { return Math.hypot(t.x2 - t.x1, t.y2 - t.y1) >= .1; });
      });
      if (!figurasValidas || !figuraCabe(o)) {
        s.escena = JSON.parse(anterior);
        avisar('Ese tamaño dejaría alguna figura fuera del plano o demasiado pequeña.', true);
        pintar(); return;
      }
      dirty(); pintar();
    }
    function escalarReferencia(ref, factor) {
      if (!ref) return;
      ['x', 'y', 'ancho', 'fondo'].forEach(function (clave) { ref[clave] = redondearFino(ref[clave] * factor); });
    }
    function modificarVertice(o, indice, pt) {
      if (o.puntos[indice].x === pt.x && o.puntos[indice].y === pt.y) return;
      var anterior = JSON.stringify(s.escena), maxX = o.pisos ? 2000 : o.tipo === 'CUARTO_INTERIOR' ? espacio().ancho : edificio().ancho, maxY = o.pisos ? 2000 : o.tipo === 'CUARTO_INTERIOR' ? espacio().fondo : edificio().fondo;
      if (pt.x < 0 || pt.y < 0 || pt.x > maxX || pt.y > maxY) return;
      o.puntos[indice] = pt;
      if (!poligonoSimple(o.puntos)) { s.escena = JSON.parse(anterior); avisar('El contorno no puede cruzarse ni perder superficie.', true); pintar(); return; }
      recalcularForma(o);
      if (!figuraCabe(o)) { s.escena = JSON.parse(anterior); avisar('El contorno dejaría oficinas o activos fuera de la figura.', true); pintar(); return; }
      dirty(); pintar();
    }
    function modificarLado(o, indice, dx, dy) {
      if (!o) return;
      var anterior = JSON.stringify(s.escena), maxX = o.pisos ? 2000 : o.tipo === 'CUARTO_INTERIOR' ? espacio().ancho : edificio().ancho, maxY = o.pisos ? 2000 : o.tipo === 'CUARTO_INTERIOR' ? espacio().fondo : edificio().fondo;
      var a = o.puntos[indice], b = o.puntos[(indice + 1) % o.puntos.length];
      var nuevos = [{ x: ajustarNumero(a.x + dx), y: ajustarNumero(a.y + dy) },
        { x: ajustarNumero(b.x + dx), y: ajustarNumero(b.y + dy) }];
      if (nuevos.some(function (p) { return p.x < 0 || p.y < 0 || p.x > maxX || p.y > maxY; })) {
        avisar('El lado debe permanecer dentro del plano.', true); return;
      }
      o.puntos[indice] = nuevos[0]; o.puntos[(indice + 1) % o.puntos.length] = nuevos[1];
      if (!poligonoSimple(o.puntos)) { s.escena = JSON.parse(anterior); avisar('Al mover el lado, el contorno se cruzaría.', true); pintar(); return; }
      recalcularForma(o);
      if (!figuraCabe(o)) { s.escena = JSON.parse(anterior); avisar('El lado dejaría contenido fuera del edificio o espacio.', true); pintar(); return; }
      dirty(); pintar();
    }
    function girarEdificio(grados) {
      var e = edificio() || (s.editarEdificio && s.escena.edificios.find(function (b) { return b.id === s.editarEdificio; }));
      if (!e || !grados) return;
      var anterior = JSON.stringify(s.escena), ox = e.x, oy = e.y;
      var cx = ox + e.ancho / 2, cy = oy + e.fondo / 2;
      var rad = grados * Math.PI / 180, co = Math.cos(rad), si = Math.sin(rad);
      function rotar(pt) { var dx = pt.x - cx, dy = pt.y - cy; return { x: redondear(cx + dx * co - dy * si), y: redondear(cy + dx * si + dy * co) }; }
      e.puntos = e.puntos.map(rotar);
      var bb = caja(e.puntos), mx = Math.max(0, -bb.x) - Math.max(0, bb.x + bb.ancho - 2000);
      var my = Math.max(0, -bb.y) - Math.max(0, bb.y + bb.fondo - 2000);
      e.puntos.forEach(function (pt) { pt.x = redondear(pt.x + mx); pt.y = redondear(pt.y + my); });
      bb = caja(e.puntos); e.x = bb.x; e.y = bb.y; e.ancho = redondear(bb.ancho); e.fondo = redondear(bb.fondo);
      e.pisos.forEach(function (p) {
        if (p.planoReferencia) {
          var fp = p.planoReferencia, centroP = rotar({ x: ox + fp.x + fp.ancho / 2, y: oy + fp.y + fp.fondo / 2 });
          fp.x = redondearFino(centroP.x + mx - e.x - fp.ancho / 2);
          fp.y = redondearFino(centroP.y + my - e.y - fp.fondo / 2);
          fp.giro = ((Number(fp.giro || 0) + grados) % 360 + 360) % 360;
        }
        (p.trazos || []).forEach(function (t) {
          var a = rotar({ x: ox + t.x1, y: oy + t.y1 }), b = rotar({ x: ox + t.x2, y: oy + t.y2 });
          t.x1 = redondear(a.x + mx - e.x); t.y1 = redondear(a.y + my - e.y);
          t.x2 = redondear(b.x + mx - e.x); t.y2 = redondear(b.y + my - e.y);
        });
        p.espacios.forEach(function (r) {
          var rx = r.x, ry = r.y;
          var ref = r.planoReferencia;
          var centroRef = ref && rotar({ x: ox + rx + ref.x + ref.ancho / 2, y: oy + ry + ref.y + ref.fondo / 2 });
          var elementos = r.activos.concat(r.mobiliario || []);
          var centros = elementos.map(function (z) { return rotar({ x: ox + rx + z.x + z.ancho / 2, y: oy + ry + z.y + z.fondo / 2 }); });
          var cuartosRotados = (r.cuartos || []).map(function (c) { return c.puntos.map(function (pt) { return rotar({ x: ox + rx + pt.x, y: oy + ry + pt.y }); }); });
          r.puntos = r.puntos.map(function (pt) { var nuevo = rotar({ x: ox + pt.x, y: oy + pt.y }); return { x: redondear(nuevo.x + mx - e.x), y: redondear(nuevo.y + my - e.y) }; });
          var cajaR = caja(r.puntos); r.x = cajaR.x; r.y = cajaR.y; r.ancho = redondear(cajaR.ancho); r.fondo = redondear(cajaR.fondo);
          if (ref) {
            ref.x = redondearFino(centroRef.x + mx - e.x - r.x - ref.ancho / 2);
            ref.y = redondearFino(centroRef.y + my - e.y - r.y - ref.fondo / 2);
            ref.giro = ((Number(ref.giro || 0) + grados) % 360 + 360) % 360;
          }
          elementos.forEach(function (z, i) {
            z.x = redondear(centros[i].x + mx - e.x - r.x - z.ancho / 2);
            z.y = redondear(centros[i].y + my - e.y - r.y - z.fondo / 2);
            z.giro = ((Number(z.giro || 0) + grados) % 360 + 360) % 360;
          });
          (r.cuartos || []).forEach(function (c, i) {
            c.puntos = cuartosRotados[i].map(function (pt) { return { x: redondear(pt.x + mx - e.x - r.x), y: redondear(pt.y + my - e.y - r.y) }; });
            var cajaC = caja(c.puntos); c.x = cajaC.x; c.y = cajaC.y; c.ancho = redondear(cajaC.ancho); c.fondo = redondear(cajaC.fondo);
          });
          if (r.textoGiro != null) r.textoGiro = ((Number(r.textoGiro) + grados + 180) % 360 + 360) % 360 - 180;
        });
      });
      e.giro = ((Number(e.giro || 0) + grados) % 360 + 360) % 360;
      if (e.textoGiro != null) e.textoGiro = ((Number(e.textoGiro) + grados + 180) % 360 + 360) % 360 - 180;
      if (!poligonoSimple(e.puntos) || !figuraCabe(e)) { s.escena = JSON.parse(anterior); avisar('Ese giro dejaría espacios o activos fuera del edificio.', true); pintar(); return; }
      dirty(); pintar();
    }
    function girarEspacio(grados) {
      var r = espacio() || (piso() && piso().espacios.find(function (v) { return v.id === s.editarEspacio; }));
      if (!r || !grados) return;
      var anterior = JSON.stringify(s.escena), ox = r.x, oy = r.y;
      var cx = ox + r.ancho / 2, cy = oy + r.fondo / 2;
      var rad = grados * Math.PI / 180, co = Math.cos(rad), si = Math.sin(rad);
      function rotar(pt) { var dx = pt.x - cx, dy = pt.y - cy; return { x: redondear(cx + dx * co - dy * si), y: redondear(cy + dx * si + dy * co) }; }
      var ref = r.planoReferencia;
      var centroRef = ref && rotar({ x: ox + ref.x + ref.ancho / 2, y: oy + ref.y + ref.fondo / 2 });
      var elementos = r.activos.concat(r.mobiliario || []);
      var centros = elementos.map(function (z) { return rotar({ x: ox + z.x + z.ancho / 2, y: oy + z.y + z.fondo / 2 }); });
      var cuartosRotados = (r.cuartos || []).map(function (c) { return c.puntos.map(function (pt) { return rotar({ x: ox + pt.x, y: oy + pt.y }); }); });
      r.puntos = r.puntos.map(rotar);
      var bb = caja(r.puntos); r.x = bb.x; r.y = bb.y; r.ancho = redondear(bb.ancho); r.fondo = redondear(bb.fondo);
      if (ref) {
        ref.x = redondearFino(centroRef.x - r.x - ref.ancho / 2);
        ref.y = redondearFino(centroRef.y - r.y - ref.fondo / 2);
        ref.giro = ((Number(ref.giro || 0) + grados) % 360 + 360) % 360;
      }
      elementos.forEach(function (z, i) {
        z.x = redondear(centros[i].x - r.x - z.ancho / 2);
        z.y = redondear(centros[i].y - r.y - z.fondo / 2);
        z.giro = ((Number(z.giro || 0) + grados) % 360 + 360) % 360;
      });
      (r.cuartos || []).forEach(function (c, i) {
        c.puntos = cuartosRotados[i].map(function (pt) { return { x: redondear(pt.x - r.x), y: redondear(pt.y - r.y) }; });
        var cajaC = caja(c.puntos); c.x = cajaC.x; c.y = cajaC.y; c.ancho = redondear(cajaC.ancho); c.fondo = redondear(cajaC.fondo);
      });
      r.giro = ((Number(r.giro || 0) + grados) % 360 + 360) % 360;
      if (r.textoGiro != null) r.textoGiro = ((Number(r.textoGiro) + grados + 180) % 360 + 360) % 360 - 180;
      if (!poligonoSimple(r.puntos) || !figuraCabe(r) || r.x < 0 || r.y < 0 || r.x + r.ancho > edificio().ancho || r.y + r.fondo > edificio().fondo) {
        s.escena = JSON.parse(anterior); avisar('El giro dejaría la oficina o sus activos fuera del plano.', true); pintar(); return;
      }
      dirty(); pintar();
    }
    canvas.addEventListener('click', function (ev) {
      if (ignorarClick) { ignorarClick = false; return; }
      var vertice = ev.target.closest('[data-vertex-index]'), medio = ev.target.closest('[data-add-index]');
      if (edita && (s.modo === 'vertices' || s.editarEdificio || s.editarEspacio || s.cuartoSeleccionado) && vertice) { s.vertice = Number(vertice.dataset.vertexIndex); pintar(); return; }
      if (edita && (s.modo === 'vertices' || s.editarEdificio || s.editarEspacio || s.cuartoSeleccionado) && medio) {
        var o = objetoEnVertices(), i = Number(medio.dataset.addIndex), a = o.puntos[i], b = o.puntos[(i + 1) % o.puntos.length];
        if (o.puntos.length >= 128) { avisar('Máximo 128 puntos por contorno.', true); return; }
        var medioPunto = { x: redondearFino((a.x + b.x) / 2), y: redondearFino((a.y + b.y) / 2) };
        if ((medioPunto.x === a.x && medioPunto.y === a.y) || (medioPunto.x === b.x && medioPunto.y === b.y)) { avisar('Amplía el plano para añadir un punto en este lado.', true); return; }
        o.puntos.splice(i + 1, 0, medioPunto);
        s.vertice = i + 1; dirty(); pintar(); return;
      }
      if (edita && (s.modo === 'polygonBuilding' || s.modo === 'polygonOffice' || s.modo === 'polygonRoom')) {
        if (s.dibujo.length >= 128) { avisar('Máximo 128 puntos por contorno.', true); return; }
        var pt = coordenadaPlano(ev), ultimo = s.dibujo[s.dibujo.length - 1];
        if (s.dibujo.length >= 3 && Math.hypot(pt.x - s.dibujo[0].x, pt.y - s.dibujo[0].y) <= umbralEnPlano(10)) { terminarDibujo(); return; }
        if (!ultimo || ultimo.x !== pt.x || ultimo.y !== pt.y) { s.dibujo.push(pt); pintar(); }
        return;
      }
      if (edita && ['MURO', 'PUERTA', 'VENTANA', 'ESCALERA'].indexOf(s.modo) >= 0) {
        var punto = coordenadaPlano(ev);
        if (!s.dibujo.length) { s.dibujo = [punto]; pintar(); return; }
        var inicio = s.dibujo[0];
        if (Math.hypot(punto.x - inicio.x, punto.y - inicio.y) < .1) { avisar('Marca dos puntos separados.', true); return; }
        piso().trazos.push({ id: uid(), tipo: s.modo, x1: inicio.x, y1: inicio.y, x2: punto.x, y2: punto.y });
        s.dibujo = []; dirty(); pintar(); return;
      }
      if (s.modo === 'pan' || s.modo === 'vertices') return;
      var n = ev.target.closest('.me-shape'); if (!n) return;
      var kind = n.getAttribute('data-kind'), id = n.getAttribute('data-id');
      if (kind === 'building') {
        if (edita && !s.vista3d && contexto() === 'predio') { s.editarEdificio = id; s.vertice = -1; }
        else { abrirEdificio(id); return; }
      }
      if (kind === 'floor3d') {
        abrirEdificio(n.getAttribute('data-building-id'), n.getAttribute('data-floor-id')); return;
      }
      if (kind === 'space3d') {
        s.vista3d = false; abrirEdificio(n.getAttribute('data-building-id'), n.getAttribute('data-floor-id'), id); return;
      }
      if (kind === 'space') {
        if (edita && contexto() === 'piso') { s.modo = ''; s.editarEspacio = id; s.vertice = -1; }
        else { limpiarHerramienta(); s.espacio = id; s.cuartoSeleccionado = s.muebleSeleccionado = s.activoSeleccionado = null; }
      }
      if (kind === 'room') { s.cuartoSeleccionado = id; s.muebleSeleccionado = s.activoSeleccionado = null; s.vertice = -1; }
      if (kind === 'furniture') { s.muebleSeleccionado = id; s.cuartoSeleccionado = s.activoSeleccionado = null; }
      if (kind === 'asset') { s.activoSeleccionado = Number(id); s.cuartoSeleccionado = s.muebleSeleccionado = null; cargarDetalleActivo(id); canvas.focus(); }
      pintar();
    });
    canvas.addEventListener('mousedown', function (ev) {
      if (ev.button !== 0 && ev.button !== 1 && ev.button !== 2) return;
      canvas.focus({ preventScroll: true });
      if (ev.button === 1 || ev.button === 2 || s.espacioPulsado || s.modo === 'pan' ||
          (!edita && (s.vista3d && ev.shiftKey || !s.vista3d && !ev.target.closest('.me-shape')))) {
        ev.preventDefault();
        var matrizVista = canvas.getScreenCTM();
        drag = { kind: 'pan', x: ev.clientX, y: ev.clientY, panX: s.panX, panY: s.panY,
          unidadesPorPixel: 1 / Math.hypot(matrizVista.a, matrizVista.b) };
        canvas.classList.add('me-panning');
        canvas.style.cursor = 'grabbing'; return;
      }
      if (s.vista3d) {
        var alturaMando = ev.target.closest('[data-height-floor-id]');
        if (edita && alturaMando) {
          var edificioAltura = s.escena.edificios.find(function (b) { return b.id === alturaMando.getAttribute('data-building-id'); });
          var pisoAltura = edificioAltura && edificioAltura.pisos.find(function (p) { return p.id === alturaMando.getAttribute('data-height-floor-id'); });
          if (pisoAltura) drag = { kind: 'floorHeight', e: edificioAltura, p: pisoAltura, inicio: puntoSvg(ev), altura: Number(pisoAltura.altura), clientY: ev.clientY };
          return;
        }
        var figura3d = ev.target.closest('.me-shape[data-building-id]');
        if (edita && figura3d && (s.modo === 'move3d' || ev.shiftKey)) {
          var edificio3d = s.escena.edificios.find(function (v) { return v.id === figura3d.getAttribute('data-building-id'); });
          var p3d = puntoSvg(ev);
          drag = { kind: 'building3d', o: edificio3d, x: p3d.x, y: p3d.y, clientX: ev.clientX, clientY: ev.clientY, ox: edificio3d.x, oy: edificio3d.y };
        } else { fijarMarco3d(); drag = { kind: 'orbit', x: ev.clientX, y: ev.clientY, angulo: s.camara, inclinacion: s.inclinacion }; canvas.style.cursor = 'grabbing'; }
        return;
      }
      if (!edita || s.vista3d || (s.modo && s.modo !== 'vertices')) return;
      var mandoGiro = ev.target.closest('[data-rotate-building-handle]');
      if (mandoGiro) {
        var edificioGiro = s.escena.edificios.find(function (b) { return b.id === mandoGiro.getAttribute('data-rotate-building-handle'); });
        if (edificioGiro) {
          var centroGiro = { x: edificioGiro.x + edificioGiro.ancho / 2, y: edificioGiro.y + edificioGiro.fondo / 2 };
          var inicioGiro = puntoSvg(ev);
          drag = { kind: 'rotateBuilding', x: ev.clientX, y: ev.clientY, centro: centroGiro,
            angulo: Math.atan2(inicioGiro.y - centroGiro.y, inicioGiro.x - centroGiro.x) };
          drag.preview = svg('polygon', { points: puntosTexto(edificioGiro.puntos), fill: 'none', stroke: '#17835d',
            'stroke-width': 2.5, 'stroke-dasharray': '7 5', 'vector-effect': 'non-scaling-stroke', 'pointer-events': 'none' });
          canvas.appendChild(drag.preview);
        }
        return;
      }
      var tirador = ev.target.closest('[data-scale-building-handle]');
      if (tirador) {
        var construccion = s.escena.edificios.find(function (b) { return b.id === tirador.getAttribute('data-scale-building-handle'); });
        if (construccion) drag = { kind: 'scaleBuilding', o: construccion, x: ev.clientX, y: ev.clientY,
          punto: puntoSvg(ev), ancho: construccion.ancho, fondo: construccion.fondo };
        return;
      }
      var vertice = ev.target.closest('[data-vertex-index]');
      var lado = ev.target.closest('[data-add-index]');
      if ((s.modo === 'vertices' || s.editarEdificio || s.editarEspacio || s.cuartoSeleccionado) && lado) {
        drag = { kind: 'edge', o: objetoEnVertices(), index: Number(lado.dataset.addIndex), inicio: puntoSvg(ev), x: ev.clientX, y: ev.clientY };
        return;
      }
      if ((s.modo === 'vertices' || s.editarEdificio || s.editarEspacio || s.cuartoSeleccionado) && vertice) {
        var objetoVertice = objetoEnVertices(), indiceVertice = Number(vertice.dataset.vertexIndex);
        var matrizVertice = canvas.getScreenCTM().inverse();
        var anteriorVertice = puntosTexto(objetoVertice.puntos.slice(0, indiceVertice));
        var posteriorVertice = puntosTexto(objetoVertice.puntos.slice(indiceVertice + 1));
        drag = { kind: 'vertex', o: objetoVertice, index: indiceVertice, x: ev.clientX, y: ev.clientY,
          inicio: puntoConMatriz(ev, matrizVertice), matrizInversa: matrizVertice, vistaVersion: s.vistaVersion,
          maxX: objetoVertice.pisos ? 2000 : objetoVertice.tipo === 'CUARTO_INTERIOR' ? espacio().ancho : edificio().ancho,
          maxY: objetoVertice.pisos ? 2000 : objetoVertice.tipo === 'CUARTO_INTERIOR' ? espacio().fondo : edificio().fondo,
          original: { x: objetoVertice.puntos[indiceVertice].x, y: objetoVertice.puntos[indiceVertice].y },
          previewAntes: anteriorVertice ? anteriorVertice + ' ' : '', previewDespues: posteriorVertice ? ' ' + posteriorVertice : '', ultimaGuia: 0 };
        drag.preview = svg('polygon', { points: puntosTexto(objetoVertice.puntos), class: 'me-vertex-preview',
          'pointer-events': 'none' });
        drag.previewNode = svg('circle', { cx: drag.original.x, cy: drag.original.y,
          r: Number(vertice.getAttribute('r')), class: 'me-vertex-preview-node', 'pointer-events': 'none' });
        canvas.appendChild(drag.preview); canvas.appendChild(drag.previewNode);
        drag.guia = root.querySelector('.me-canvas-hint');
        if (drag.guia) drag.guiaAnterior = drag.guia.textContent;
        canvas.classList.add('me-dragging-vertex');
        return;
      }
      if (s.modo === 'vertices') return;
      var n = ev.target.closest('.me-shape'); if (!n) return;
      var kind = n.getAttribute('data-kind'), id = n.getAttribute('data-id');
      var o = kind === 'building' ? s.escena.edificios.find(function (v) { return v.id === id; }) : kind === 'space' ? (piso() || { espacios: [] }).espacios.find(function (v) { return v.id === id; }) :
        kind === 'room' ? espacio().cuartos.find(function (v) { return v.id === id; }) : kind === 'furniture' ? espacio().mobiliario.find(function (v) { return v.id === id; }) : espacio().activos.find(function (v) { return String(v.activoId) === id; });
      if (!o) return;
      if (kind === 'building' && contexto() === 'predio') s.editarEdificio = id;
      if (kind === 'space') s.editarEspacio = id;
      var pos = puntoSvg(ev);
      drag = { o: o, x: pos.x, y: pos.y, ox: o.x, oy: o.y, kind: kind,
        clientX: ev.clientX, clientY: ev.clientY };
      if (kind === 'asset') {
        ev.preventDefault();
        drag.elemento = n;
        drag.transformacion = n.getAttribute('transform') || '';
        drag.matrizInversa = canvas.getScreenCTM().inverse();
        drag.inicio = puntoConMatriz(ev, drag.matrizInversa);
        drag.guia = root.querySelector('.me-canvas-hint');
        if (drag.guia) drag.guiaAnterior = drag.guia.textContent;
        canvas.classList.add('me-dragging-asset');
      }
    });
    canvas.addEventListener('mousemove', function (ev) {
      if (drag && drag.kind === 'pan' || !s.dibujo.length || (s.modo !== 'polygonBuilding' && s.modo !== 'polygonOffice' && s.modo !== 'polygonRoom')) return;
      var guia = canvas.querySelector('.me-draft-cursor'), cierre = canvas.querySelector('.me-draft-close');
      if (!guia) return;
      var punto = coordenadaPlano(ev);
      guia.setAttribute('x2', punto.x); guia.setAttribute('y2', punto.y);
      if (cierre) { cierre.setAttribute('x2', punto.x); cierre.setAttribute('y2', punto.y); }
    });
    document.addEventListener('mousemove', function (ev) {
      if (!drag || !root.isConnected) return;
      if (drag.kind === 'orbit' || drag.kind === 'pan') { actualizarNavegacion(ev); return; }
      if (drag.kind === 'asset') {
        var movimientoActivo = drag;
        movimientoActivo.ultimoEvento = { clientX: ev.clientX, clientY: ev.clientY, shiftKey: ev.shiftKey };
        if (movimientoActivo.pendiente) return;
        movimientoActivo.pendiente = true;
        (window.requestAnimationFrame || function (fn) { return setTimeout(fn, 16); })(function () {
          movimientoActivo.pendiente = false;
          if (drag !== movimientoActivo || !movimientoActivo.elemento.isConnected) return;
          var puntoActual = puntoConMatriz(movimientoActivo.ultimoEvento, movimientoActivo.matrizInversa);
          var dxActivo = puntoActual.x - movimientoActivo.inicio.x, dyActivo = puntoActual.y - movimientoActivo.inicio.y;
          if (movimientoActivo.ultimoEvento.shiftKey) {
            if (Math.abs(dxActivo) >= Math.abs(dyActivo)) dyActivo = 0; else dxActivo = 0;
          }
          var destinoX = Math.max(0, Math.min(espacio().ancho - movimientoActivo.o.ancho, movimientoActivo.ox + dxActivo));
          var destinoY = Math.max(0, Math.min(espacio().fondo - movimientoActivo.o.fondo, movimientoActivo.oy + dyActivo));
          movimientoActivo.elemento.setAttribute('transform', 'translate(' + (destinoX - movimientoActivo.ox) + ' ' + (destinoY - movimientoActivo.oy) + ') ' + movimientoActivo.transformacion);
          if (movimientoActivo.guia) movimientoActivo.guia.textContent = 'Activo · X ' + destinoX.toFixed(2) + ' · Y ' + destinoY.toFixed(2) + ' · Mayús: un solo eje';
        });
        return;
      }
      if (drag.kind === 'rotateBuilding') {
        var grados = anguloArrastrado(drag, ev);
        drag.preview.setAttribute('transform', 'rotate(' + grados + ' ' + drag.centro.x + ' ' + drag.centro.y + ')');
      } else if (drag.kind === 'vertex') {
        var movimientoVertice = drag;
        movimientoVertice.ultimoEvento = { clientX: ev.clientX, clientY: ev.clientY, shiftKey: ev.shiftKey };
        if (movimientoVertice.pendiente) return;
        movimientoVertice.pendiente = true;
        (window.requestAnimationFrame || function (fn) { return setTimeout(fn, 16); })(function () {
          movimientoVertice.pendiente = false;
          if (drag !== movimientoVertice || !root.isConnected) return;
          var puntoArrastre = puntoVerticeArrastrado(movimientoVertice, movimientoVertice.ultimoEvento);
          movimientoVertice.preview.setAttribute('points', movimientoVertice.previewAntes + puntoArrastre.x + ',' + puntoArrastre.y + movimientoVertice.previewDespues);
          movimientoVertice.previewNode.setAttribute('cx', puntoArrastre.x);
          movimientoVertice.previewNode.setAttribute('cy', puntoArrastre.y);
          if (movimientoVertice.validacionPendiente) clearTimeout(movimientoVertice.validacionPendiente);
          movimientoVertice.preview.classList.remove('me-invalid-preview');
          movimientoVertice.validacionPendiente = setTimeout(function () {
            movimientoVertice.validacionPendiente = null;
            if (drag !== movimientoVertice || !root.isConnected) return;
            var puntosValidacion = movimientoVertice.o.puntos.slice();
            puntosValidacion[movimientoVertice.index] = puntoArrastre;
            movimientoVertice.preview.classList.toggle('me-invalid-preview', !poligonoSimple(puntosValidacion));
          }, 120);
          var ahora = Date.now();
          if (movimientoVertice.guia && ahora - movimientoVertice.ultimaGuia >= 80) {
            movimientoVertice.guia.textContent = 'Punto ' + (movimientoVertice.index + 1) + ' · X: ' + puntoArrastre.x.toFixed(4) + ' · Y: ' + puntoArrastre.y.toFixed(4) + ' · Mayús: mover en un solo eje';
            movimientoVertice.ultimaGuia = ahora;
          }
        });
      }
    });
    function finalizarArrastre(ev) {
      if (!drag) return;
      if (drag.kind === 'asset') {
        var movimiento = drag;
        drag = null;
        canvas.classList.remove('me-dragging-asset');
        movimiento.elemento.setAttribute('transform', movimiento.transformacion);
        if (movimiento.guia) movimiento.guia.textContent = movimiento.guiaAnterior || '';
        if (Math.hypot(ev.clientX - movimiento.clientX, ev.clientY - movimiento.clientY) >= 1) {
          var puntoFinal = puntoConMatriz(ev, movimiento.matrizInversa);
          var deltaX = puntoFinal.x - movimiento.inicio.x, deltaY = puntoFinal.y - movimiento.inicio.y;
          if (ev.shiftKey) { if (Math.abs(deltaX) >= Math.abs(deltaY)) deltaY = 0; else deltaX = 0; }
          var nuevoX = redondearFino(Math.max(0, Math.min(espacio().ancho - movimiento.o.ancho, movimiento.ox + deltaX)));
          var nuevoY = redondearFino(Math.max(0, Math.min(espacio().fondo - movimiento.o.fondo, movimiento.oy + deltaY)));
          if (nuevoX !== movimiento.o.x || nuevoY !== movimiento.o.y) {
            movimiento.o.x = nuevoX; movimiento.o.y = nuevoY;
            s.activoSeleccionado = Number(movimiento.o.activoId); dirty(); pintar();
          } else if (movimiento.detalleActualizado) pintar();
          ignorarClick = true; setTimeout(function () { ignorarClick = false; }, 0);
        }
        else if (movimiento.detalleActualizado) pintar();
        return;
      }
      if (drag.kind === 'floorHeight') {
        if (Math.abs(ev.clientY - drag.clientY) > 2) {
          var deltaAltura = (drag.inicio.y - puntoSvg(ev).y) /
            (window.MapaEspacial3D.escalaAltura * Math.cos(s.inclinacion * Math.PI / 180));
          var alturaNueva = Math.max(1, Math.min(20, redondear(drag.altura + deltaAltura)));
          if (alturaNueva !== drag.p.altura) {
            drag.p.altura = alturaNueva; sincronizarAltura(drag.e); dirty(); pintar();
          }
          ignorarClick = true; setTimeout(function () { ignorarClick = false; }, 0);
        }
        drag = null; canvas.style.cursor = 'grab'; return;
      }
      if (drag.kind === 'rotateBuilding') {
        if (drag.preview.parentNode) drag.preview.parentNode.removeChild(drag.preview);
        if (Math.abs(ev.clientX - drag.x) + Math.abs(ev.clientY - drag.y) > 2) {
          var diferenciaGiro = anguloArrastrado(drag, ev);
          if (diferenciaGiro) girarEdificio(diferenciaGiro);
          ignorarClick = true; setTimeout(function () { ignorarClick = false; }, 0);
        }
        drag = null; return;
      }
      if (drag.kind === 'scaleBuilding') {
        var puntoEscala = puntoSvg(ev);
        var cambioX = (puntoEscala.x - drag.punto.x) / drag.ancho;
        var cambioY = (puntoEscala.y - drag.punto.y) / drag.fondo;
        var cambio = Math.abs(cambioX) >= Math.abs(cambioY) ? cambioX : cambioY;
        if (Math.abs(ev.clientX - drag.x) + Math.abs(ev.clientY - drag.y) > 2) {
          escalarEdificio(drag.o, Math.max(.05, Math.min(5, 1 + cambio)));
          ignorarClick = true; setTimeout(function () { ignorarClick = false; }, 0);
        }
        drag = null; return;
      }
      if (drag.kind === 'building3d') {
        var p3 = puntoSvg(ev), yaw = s.camara * Math.PI / 180, senP = Math.sin(s.inclinacion * Math.PI / 180);
        var lateral = (p3.x - drag.x) / .95, profundo = (p3.y - drag.y) / (.95 * senP);
        var dx3 = lateral * Math.cos(yaw) + profundo * Math.sin(yaw);
        var dy3 = -lateral * Math.sin(yaw) + profundo * Math.cos(yaw);
        if (Math.abs(ev.clientX - drag.clientX) + Math.abs(ev.clientY - drag.clientY) > 2 && (Math.abs(dx3) + Math.abs(dy3) > .2)) {
          var nx = redondear(Math.max(0, Math.min(2000 - drag.o.ancho, drag.ox + dx3)));
          var ny = redondear(Math.max(0, Math.min(2000 - drag.o.fondo, drag.oy + dy3)));
          var tx3 = nx - drag.o.x, ty3 = ny - drag.o.y;
          drag.o.puntos.forEach(function (pt) { pt.x = redondear(pt.x + tx3); pt.y = redondear(pt.y + ty3); });
          drag.o.x = nx; drag.o.y = ny; s.edificio = drag.o.id; dirty(); pintar();
          ignorarClick = true; setTimeout(function () { ignorarClick = false; }, 0);
        }
        drag = null; return;
      }
      if (drag.kind === 'orbit') {
        if (Math.abs(ev.clientX - drag.x) + Math.abs(ev.clientY - drag.y) > 3) {
          actualizarNavegacion(ev);
          ignorarClick = true; setTimeout(function () { ignorarClick = false; }, 0); pintar();
        }
        drag = null; return;
      }
      if (drag.kind === 'pan') {
        var px = ev.clientX - drag.x, py = ev.clientY - drag.y;
        if (drag.movio || Math.abs(px) + Math.abs(py) > 2) {
          actualizarNavegacion(ev);
          ignorarClick = true; setTimeout(function () { ignorarClick = false; }, 0);
          if (s.vista3d) pintar();
        }
        drag = null; canvas.classList.remove('me-panning'); canvas.style.cursor = s.vista3d || s.modo === 'pan' ? 'grab' : ''; return;
      }
      if (drag.kind === 'vertex') {
        if (drag.validacionPendiente) clearTimeout(drag.validacionPendiente);
        drag.preview.remove(); drag.previewNode.remove();
        canvas.classList.remove('me-dragging-vertex');
        if (drag.guia) drag.guia.textContent = drag.guiaAnterior || '';
        if (Math.hypot(ev.clientX - drag.x, ev.clientY - drag.y) >= 1) {
          modificarVertice(drag.o, drag.index, puntoVerticeArrastrado(drag, ev));
          ignorarClick = true; setTimeout(function () { ignorarClick = false; }, 0);
        }
        drag = null; return;
      }
      if (drag.kind === 'edge') {
        if (Math.abs(ev.clientX - drag.x) + Math.abs(ev.clientY - drag.y) > 2) {
          var finalLado = puntoSvg(ev);
          var dxLado = finalLado.x - drag.inicio.x, dyLado = finalLado.y - drag.inicio.y;
          if (ev.shiftKey) { if (Math.abs(dxLado) >= Math.abs(dyLado)) dyLado = 0; else dxLado = 0; }
          modificarLado(drag.o, drag.index, dxLado, dyLado);
          ignorarClick = true; setTimeout(function () { ignorarClick = false; }, 0);
        }
        drag = null; return;
      }
      var pos = puntoSvg(ev), dx = pos.x - drag.x, dy = pos.y - drag.y;
      if (Math.abs(dx) + Math.abs(dy) > 2) {
        var maxX = drag.kind === 'building' ? 2000 : drag.kind === 'space' ? edificio().ancho : espacio().ancho;
        var maxY = drag.kind === 'building' ? 2000 : drag.kind === 'space' ? edificio().fondo : espacio().fondo;
        var x = redondearFino(Math.max(0, Math.min(maxX - drag.o.ancho, drag.ox + dx)));
        var y = redondearFino(Math.max(0, Math.min(maxY - drag.o.fondo, drag.oy + dy)));
        if (x !== drag.o.x || y !== drag.o.y) {
          var tx = x - drag.o.x, ty = y - drag.o.y;
          if (drag.o.puntos) drag.o.puntos.forEach(function (pt) { pt.x = redondearFino(pt.x + tx); pt.y = redondearFino(pt.y + ty); });
          drag.o.x = x; drag.o.y = y; dirty(); pintar();
        }
        ignorarClick = true; setTimeout(function () { ignorarClick = false; }, 0);
      }
      drag = null;
    }
    canvas.addEventListener('mouseup', finalizarArrastre);
    document.addEventListener('mouseup', function (ev) {
      if (drag && !canvas.contains(ev.target)) {
        if (!root.isConnected) { drag = null; return; }
        finalizarArrastre(ev);
      }
    });
    canvas.addEventListener('dblclick', function (ev) {
      if (s.modo === 'polygonBuilding' || s.modo === 'polygonOffice' || s.modo === 'polygonRoom') { ev.preventDefault(); terminarDibujo(); }
    });
    canvas.addEventListener('wheel', function (ev) {
      ev.preventDefault();
      var unidad = ev.deltaMode === 1 ? 16 : ev.deltaMode === 2 ? canvas.clientHeight : 1;
      ruedaAcumulada += Math.max(-180, Math.min(180, ev.deltaY * unidad));
      ruedaEvento = { clientX: ev.clientX, clientY: ev.clientY };
      ruedaVista = s.viewport;
      if (ruedaPendiente) return;
      ruedaPendiente = true;
      (window.requestAnimationFrame || function (fn) { return setTimeout(fn, 16); })(function () {
        ruedaPendiente = false;
        var delta = ruedaAcumulada, posicion = ruedaEvento, vista = ruedaVista;
        ruedaAcumulada = 0; ruedaEvento = ruedaVista = null;
        if (!root.isConnected || !delta || vista !== s.viewport) return;
        if (vista2dPendiente) { vista2dPendiente = false; aplicarZoom(false); }
        acercar(Math.exp(-Math.max(-720, Math.min(720, delta)) * .0015), posicion, true);
        if (drag && drag.kind === 'pan') {
          var matriz = canvas.getScreenCTM();
          drag.x = posicion.clientX; drag.y = posicion.clientY;
          drag.panX = s.panX; drag.panY = s.panY;
          drag.unidadesPorPixel = 1 / Math.hypot(matriz.a, matriz.b);
        }
      });
    }, { passive: false });
    canvas.addEventListener('auxclick', function (ev) { if (ev.button === 1) ev.preventDefault(); });
    canvas.addEventListener('contextmenu', function (ev) { if (s.vista3d || s.espacioPulsado) ev.preventDefault(); });
    root.addEventListener('keydown', function (ev) {
      if (/INPUT|TEXTAREA|SELECT/.test((ev.target.tagName || '').toUpperCase())) return;
      var tecla = ev.key.toLowerCase();
      if ((ev.ctrlKey || ev.metaKey) && edita) {
        if (tecla === 'z') { ev.preventDefault(); viajarHistorial(ev.shiftKey ? 1 : -1); return; }
        if (tecla === 'y') { ev.preventDefault(); viajarHistorial(1); return; }
        if (tecla === 's') { ev.preventDefault(); var guardar = root.querySelector('.me-guardar'); if (guardar) guardar.click(); return; }
      }
      if (ev.ctrlKey || ev.metaKey || ev.altKey) return;
      if (edita && ev.target === canvas && !s.vista3d && espacio() && s.activoSeleccionado && /^Arrow(Left|Right|Up|Down)$/.test(ev.key)) {
        var activoFino = espacio().activos.find(function (z) { return Number(z.activoId) === Number(s.activoSeleccionado); });
        if (activoFino) {
          ev.preventDefault();
          var pasoFino = ev.shiftKey ? 1 : .1;
          var deltaFinoX = ev.key === 'ArrowLeft' ? -pasoFino : ev.key === 'ArrowRight' ? pasoFino : 0;
          var deltaFinoY = ev.key === 'ArrowUp' ? -pasoFino : ev.key === 'ArrowDown' ? pasoFino : 0;
          activoFino.x = redondearFino(Math.max(0, Math.min(espacio().ancho - activoFino.ancho, activoFino.x + deltaFinoX)));
          activoFino.y = redondearFino(Math.max(0, Math.min(espacio().fondo - activoFino.fondo, activoFino.y + deltaFinoY)));
          dirty(); pintar(); canvas.focus(); return;
        }
      }
      if (ev.code === 'Space' && ev.target === canvas) { ev.preventDefault(); s.espacioPulsado = true; canvas.style.cursor = 'grab'; return; }
      if (ev.key === 'Escape') {
        if (s.modo || s.dibujo.length || s.editarEdificio || s.editarEspacio) { limpiarHerramienta(); pintar(); }
        else if (s.espacio || s.piso || s.edificio) volver();
        return;
      }
      if (tecla === '+' || tecla === '=') { ev.preventDefault(); acercar(1.25); return; }
      if (tecla === '-') { ev.preventDefault(); acercar(1 / 1.25); return; }
      if (s.vista3d && /^Arrow(Left|Right|Up|Down)$/.test(ev.key)) {
        ev.preventDefault(); fijarMarco3d();
        if (ev.key === 'ArrowLeft') s.camara = (s.camara + 352) % 360;
        else if (ev.key === 'ArrowRight') s.camara = (s.camara + 8) % 360;
        else if (ev.key === 'ArrowUp') s.inclinacion = Math.min(edita ? 78 : 85, s.inclinacion + 6);
        else s.inclinacion = Math.max(edita ? 12 : 8, s.inclinacion - 6);
        pintar(); return;
      }
      if (s.vista3d && tecla === '0') { ev.preventDefault(); s.camara = 38; s.inclinacion = 38; s.panX = s.panY = 0; s.zoom = 1; s.marco3dFijo = null; pintar(); return; }
      if (tecla === 'f') { ev.preventDefault(); s.zoom = 1; s.panX = s.panY = 0; s.marco3dFijo = null; pintar(); return; }
      if (tecla === '?') { var ayuda = root.querySelector('.me-shortcuts'); if (ayuda) ayuda.open = !ayuda.open; return; }
      if (edita && tecla === 'v') { s.modo = ''; s.dibujo = []; pintar(); return; }
      if (tecla === 'm') { s.modo = s.modo === 'pan' ? '' : 'pan'; s.dibujo = []; pintar(); }
    });
    root.addEventListener('keyup', function (ev) { if (ev.code === 'Space') { s.espacioPulsado = false; canvas.style.cursor = drag && drag.kind === 'pan' ? 'grabbing' : s.vista3d || s.modo === 'pan' ? 'grab' : ''; } });
    window.addEventListener('blur', function () {
      s.espacioPulsado = false;
      if (drag && drag.kind === 'pan') { drag = null; canvas.classList.remove('me-panning'); canvas.style.cursor = s.vista3d || s.modo === 'pan' ? 'grab' : ''; }
      else if (drag && drag.kind === 'vertex') {
        if (drag.validacionPendiente) clearTimeout(drag.validacionPendiente);
        drag.preview.remove(); drag.previewNode.remove();
        if (drag.guia) drag.guia.textContent = drag.guiaAnterior || '';
        canvas.classList.remove('me-dragging-vertex'); drag = null;
      }
      else if (drag && drag.kind === 'asset') {
        var arrastreActivo = drag;
        arrastreActivo.elemento.setAttribute('transform', arrastreActivo.transformacion);
        if (arrastreActivo.guia) arrastreActivo.guia.textContent = arrastreActivo.guiaAnterior || '';
        canvas.classList.remove('me-dragging-asset'); drag = null;
        if (arrastreActivo.detalleActualizado) pintar();
      }
    });
    var barra = root.querySelector('.me-herramientas');
    if (barra) {
      barra.addEventListener('input', function (ev) {
        if (ev.target.classList.contains('me-zoom-range')) {
          s.zoom = zoomDelControl(ev.target.value);
          if (s.vista3d) repintarMovimiento();
          else { aplicarZoom(true, false); programarEtiquetasZoom(); }
          var etiquetaZoom = barra.querySelector('.me-zoom-label');
          if (etiquetaZoom) etiquetaZoom.textContent = Math.round(s.zoom * 100) + '%';
        }
      });
      barra.addEventListener('click', function (ev) {
        var n = ev.target.closest('[data-tool],[data-action]'); if (!n || n.disabled) return;
        if (n.dataset.tool) {
          var modo = n.dataset.tool;
          if (n.dataset.context && n.dataset.context !== contexto()) return;
          if (s.vista3d && modo !== 'pan' && modo !== 'select' && modo !== 'move3d') { avisar('Cambia a vista plana para dibujar.', true); return; }
          if (modo === 'polygonBuilding') s.tipoDibujo = 'EDIFICIO';
          s.modo = modo === 'select' || s.modo === modo ? '' : modo; s.dibujo = []; s.editarEdificio = null; s.editarEspacio = null; s.vertice = -1;
          pintar(); return;
        }
        switch (n.dataset.action) {
          case 'undo': viajarHistorial(-1); break;
          case 'redo': viajarHistorial(1); break;
          case 'finish': terminarDibujo(); break;
          case 'cancel': limpiarHerramienta(); pintar(); break;
          case 'zoomIn': acercar(1.4); break;
          case 'zoomOut': acercar(1 / 1.4); break;
          case 'fit': s.zoom = 1; s.panX = s.panY = 0; s.marco3dFijo = null; pintar(); break;
          case 'focusBuildings': ajustarVistaPredio(); if (centrarEdificios()) pintar(); else avisar('Primero dibuja un edificio para centrarlo.', true); break;
          case 'cameraLeft': fijarMarco3d(); s.camara = (s.camara + 352) % 360; pintar(); break;
          case 'cameraRight': fijarMarco3d(); s.camara = (s.camara + 8) % 360; pintar(); break;
          case 'cameraUp': fijarMarco3d(); s.inclinacion = Math.min(edita ? 78 : 85, s.inclinacion + 6); pintar(); break;
          case 'cameraDown': fijarMarco3d(); s.inclinacion = Math.max(edita ? 12 : 8, s.inclinacion - 6); pintar(); break;
          case 'cameraReset': s.camara = 38; s.inclinacion = 38; s.panX = s.panY = 0; s.zoom = 1; s.marco3dFijo = null; pintar(); break;
          case 'upload': barra.querySelector('.me-fondo-file').click(); break;
        }
      });
      barra.addEventListener('change', function (ev) {
        if (ev.target.classList.contains('me-zoom-range')) return;
        else if (ev.target.classList.contains('me-cuadricula')) s.cuadricula = ev.target.checked;
        else if (ev.target.classList.contains('me-ajuste')) s.snap = ev.target.checked;
        else if (ev.target.classList.contains('me-techo3d')) s.techo3d = ev.target.checked;
        else if (ev.target.dataset.capa) s.capas[ev.target.dataset.capa] = ev.target.checked;
        else return;
        pintar();
      });
      var archivoFondo = barra.querySelector('.me-fondo-file');
      if (archivoFondo) archivoFondo.addEventListener('change', function () {
        if (!this.files.length) return;
        var archivo = this.files[0]; this.value = '';
        if (archivo.size > 20_000_000 || !/^image\/(png|jpeg)$/.test(archivo.type)) { avisar('Usa PNG o JPG de hasta 20 MB.', true); return; }
        var datos = new FormData(); datos.append('archivo', archivo); datos.append('version', s.version);
        avisar('Procesando imagen de referencia…', false);
        request(BASE + '/predios/' + s.predioId + '/fondo', { method: 'POST', body: datos }).then(function (res) {
          if (!s.escena.fondo) {
            var escala = 1000 / Math.max(res.ancho, res.alto);
            s.escena.fondo = { x: 0, y: 0, ancho: redondear(res.ancho * escala), fondo: redondear(res.alto * escala), opacidad: .5, giro: 0 };
          } else {
            s.escena.fondo.fondo = redondear(s.escena.fondo.ancho * res.alto / res.ancho);
            if (s.escena.fondo.fondo > 2000) {
              s.escena.fondo.fondo = 2000;
              s.escena.fondo.ancho = redondear(2000 * res.ancho / res.alto);
            }
          }
          s.escena.fondo.relacion = res.ancho / res.alto;
          if (res.version != null) s.version = res.version;
          s.tieneFondo = true; s.fondoRevision = Date.now(); dirty(); pintar();
          avisar('Imagen optimizada; ajusta su posición y guarda el plano.', false);
        }).catch(function (error) { avisar(error.message, true); });
      });
      var archivoPlano = barra.querySelector('.me-plano-file');
      if (archivoPlano) archivoPlano.addEventListener('change', function () {
        if (!this.files.length) return;
        var archivo = this.files[0], objetivo = referenciaActual();
        var objetivoId = objetivo && objetivo.id, predio = s.predioId;
        this.value = '';
        if (!objetivoId || s.vista3d) { avisar('Abre un piso u oficina en 2D para añadir su plano.', true); return; }
        if (archivo.size > 20_000_000 || !/^image\/(png|jpeg)$/.test(archivo.type)) { avisar('Usa PNG o JPG de hasta 20 MB.', true); return; }
        var datos = new FormData(); datos.append('archivo', archivo);
        avisar('Optimizando el plano de referencia…', false);
        request(BASE + '/predios/' + predio + '/planos', { method: 'POST', body: datos }).then(function (res) {
          var actual = referenciaActual();
          if (s.predioId !== predio || !actual || actual.id !== objetivoId) return;
          var ancho = actual.ancho, fondo = actual.fondo, relacion = res.ancho / res.alto;
          var anchoImagen = Math.min(ancho, fondo * relacion), fondoImagen = anchoImagen / relacion;
          actual.planoReferencia = { archivo: res.archivo, x: redondearFino((ancho - anchoImagen) / 2),
            y: redondearFino((fondo - fondoImagen) / 2), ancho: redondearFino(anchoImagen),
            fondo: redondearFino(fondoImagen), opacidad: .55, giro: 0 };
          dirty(); pintar(); avisar('Plano añadido al borrador. Ajusta la imagen y guarda.', false);
        }).catch(function (error) { avisar(error.message, true); });
      });
    }
    panel.addEventListener('click', function (ev) {
      var n = ev.target.closest('[data-act],[data-color],[data-open-building],[data-open-floor],[data-open-space],[data-open-room],[data-open-furniture],[data-add-furniture],[data-open-asset],[data-office-id],[data-place-asset],[data-inventory-page],[data-scale-building],[data-rotate-building],[data-show-placed],[data-show-asset-detail]'); if (!n) return;
      if (n.hasAttribute('data-show-placed')) { mostrarActivosUbicados(); return; }
      if (n.dataset.showAssetDetail) { mostrarFichaActivo(Number(n.dataset.showAssetDetail)); return; }
      if (n.dataset.rotateBuilding) { girarEdificio(Number(n.dataset.rotateBuilding)); return; }
      if (n.dataset.scaleBuilding) {
        escalarEdificio(s.escena.edificios.find(function (b) { return b.id === s.editarEdificio; }), Number(n.dataset.scaleBuilding));
        return;
      }
      if (n.dataset.color) {
        var elegidoColor = n.dataset.colorTarget === 'sel' ? (piso() && piso().espacios.find(function (r) { return r.id === s.editarEspacio; })) :
          n.dataset.colorTarget === 'be' ? s.escena.edificios.find(function (b) { return b.id === s.editarEdificio; }) : (espacio() || edificio());
        if (elegidoColor) { elegidoColor.color = n.dataset.color; dirty(); pintar(); }
        return;
      }
      if (n.dataset.openBuilding) { abrirEdificio(n.dataset.openBuilding); return; }
      if (n.dataset.openFloor) { abrirEdificio(s.edificio, n.dataset.openFloor); return; }
      if (n.dataset.openSpace) { limpiarHerramienta(); s.vista3d = false; s.espacio = n.dataset.openSpace; s.cuartoSeleccionado = s.muebleSeleccionado = s.activoSeleccionado = null; pintar(); return; }
      if (n.dataset.openRoom) { limpiarHerramienta(); s.cuartoSeleccionado = n.dataset.openRoom; s.muebleSeleccionado = s.activoSeleccionado = null; pintar(); return; }
      if (n.dataset.openFurniture) { limpiarHerramienta(); s.muebleSeleccionado = n.dataset.openFurniture; s.cuartoSeleccionado = s.activoSeleccionado = null; pintar(); return; }
      if (n.dataset.addFurniture) { agregarMueble(n.dataset.addFurniture); return; }
      if (n.dataset.openAsset) { limpiarHerramienta(); s.activoSeleccionado = Number(n.dataset.openAsset); s.cuartoSeleccionado = s.muebleSeleccionado = null; cargarDetalleActivo(s.activoSeleccionado); pintar(); return; }
      if (n.dataset.officeId) {
        var a = espacio() || (piso() && piso().espacios.find(function (r) { return r.id === n.dataset.officeTarget; })), nueva = Number(n.dataset.officeId);
        if (!a) return;
        if (a.oficinaId && a.oficinaId !== nueva && a.activos.length) {
          if (!confirm('Cambiar de oficina quitará las figuras de activos de este espacio. ¿Continuar?')) return;
          a.activos = []; s.activoSeleccionado = null;
        }
        a.oficinaId = nueva; s.oficinas[a.oficinaId] = { codigo: n.dataset.officeCode, nombre: n.dataset.officeName };
        s.inventario = null;
        a.nombre = n.dataset.officeCode + ' · ' + n.dataset.officeName;
        dirty(); pintar(); return;
      }
      if (n.dataset.inventoryPage) { cargarInventario(espacio().oficinaId, s.inventario.q, Number(n.dataset.inventoryPage)); return; }
      if (n.dataset.placeAsset) {
        var a = espacio(), id = Number(n.dataset.placeAsset), it = s.inventario.items.find(function (v) { return v.id === id; });
        if (!it || ubicacionActivo(id)) return;
        var tamanoInicial = Math.max(.1, Math.min(Number(a.tamanoMarcador) || 3, a.ancho, a.fondo));
        var w = redondearFino(tamanoInicial), d = w, cuenta = a.activos.length;
        var descripcion = (it.descripcion || '').toUpperCase();
        var figura = /SILLA|SILLON/.test(descripcion) ? 'SILLA' : /MESA|ESCRITORIO/.test(descripcion) ? 'MESA' : /ESTANTE|ESTANTERIA/.test(descripcion) ? 'ESTANTE' : /ARCHIVADOR|GAVETERO/.test(descripcion) ? 'ARCHIVADOR' : /COMPUTADOR|MONITOR|IMPRESORA|EQUIPO/.test(descripcion) ? 'EQUIPO' : 'OTRO';
        a.activos.push({ activoId: id, x: redondearFino(Math.min(Math.max(0, a.ancho - w), 2 + (cuenta % 4) * 6)),
          y: redondearFino(Math.min(Math.max(0, a.fondo - d), 2 + Math.floor(cuenta / 4) * 6)), ancho: w, fondo: d, giro: 0, figura: figura });
        s.activos[id] = { codigo: it.codigo, descripcion: it.descripcion, oficinaId: a.oficinaId,
          auxiliarId: it.auxiliarId, auxiliarNombre: it.auxiliarNombre };
        limpiarHerramienta(); s.activoSeleccionado = id; s.cuartoSeleccionado = s.muebleSeleccionado = null; cargarDetalleActivo(id); dirty(); pintar(); return;
      }
      if (n.dataset.act && n.dataset.act.indexOf('deleteLine:') === 0) {
        var idTrazo = n.dataset.act.slice('deleteLine:'.length);
        piso().trazos = piso().trazos.filter(function (t) { return t.id !== idTrazo; });
        dirty(); pintar(); return;
      }
      switch (n.dataset.act) {
        case 'uploadReference': { var inputPlano = root.querySelector('.me-plano-file'); if (inputPlano) inputPlano.click(); break; }
        case 'removeReference': if (referenciaActual()) { delete referenciaActual().planoReferencia; dirty(); pintar(); } break;
        case 'openSearchResult': if (s.busquedaSeleccionada) { var lugar = s.busquedaSeleccionada;
          abrirEdificio(lugar.edificioId, lugar.espacioId ? lugar.pisoId : null,
            lugar.espacioId || null, lugar.activoId); } break;
        case 'autoView': delete s.escena.vista; s.zoom = 1; s.panX = s.panY = 0; dirty(); pintar(); break;
        case 'openSelectedBuilding': s.edificio = s.editarEdificio; limpiarHerramienta(); pintar(); break;
        case 'openSelectedSpace': s.espacio = s.editarEspacio; limpiarHerramienta(); pintar(); break;
        case 'editBuilding': s.vista3d = false; s.modo = s.modo === 'vertices' ? '' : 'vertices'; s.vertice = -1; pintar(); break;
        case 'editSpace': s.editarEspacio = espacio().id; s.espacio = null; s.modo = ''; s.vertice = -1; pintar(); break;
        case 'endVertices': limpiarHerramienta(); pintar(); break;
        case 'removeVertex': {
          var forma = objetoEnVertices();
          if (!forma || s.vertice < 0) { avisar('Selecciona un punto del contorno.', true); break; }
          if (forma.puntos.length <= 3) { avisar('El contorno requiere al menos 3 puntos.', true); break; }
          var anterior = JSON.stringify(s.escena);
          forma.puntos.splice(s.vertice, 1); s.vertice = -1;
          if (!poligonoSimple(forma.puntos)) { s.escena = JSON.parse(anterior); avisar('No se puede quitar ese punto: el contorno se cruzaría.', true); pintar(); break; }
          recalcularForma(forma);
          if (!figuraCabe(forma)) { s.escena = JSON.parse(anterior); avisar('El cambio dejaría contenido fuera del contorno.', true); pintar(); break; }
          dirty(); pintar(); break;
        }
        case 'addBuilding': agregarEdificio(); break;
        case 'addIndependent': agregarEdificio('OFICINA_INDEPENDIENTE'); break;
        case 'addFloor': agregarPiso(); break;
        case 'addOffice': agregarEspacio('OFICINA'); break;
        case 'addHall': agregarEspacio('PASILLO'); break;
        case 'addSpace': agregarEspacio('OTRO'); break;
        case 'addRoom': agregarEspacio('CUARTO', espacio().oficinaId); break;
        case 'drawRoom': s.modo = 'polygonRoom'; s.dibujo = []; s.cuartoSeleccionado = null; pintar(); break;
        case 'deleteInteriorRoom': if (cuarto() && confirm('¿Eliminar este cuarto interior?')) { espacio().cuartos = espacio().cuartos.filter(function (c) { return c.id !== s.cuartoSeleccionado; }); s.cuartoSeleccionado = null; dirty(); pintar(); } break;
        case 'removeFurniture': if (mueble()) { espacio().mobiliario = espacio().mobiliario.filter(function (m) { return m.id !== s.muebleSeleccionado; }); s.muebleSeleccionado = null; dirty(); pintar(); } break;
        case 'refreshAsset': if (s.activoSeleccionado) { delete s.detallesActivos[s.activoSeleccionado]; cargarDetalleActivo(s.activoSeleccionado); pintar(); } break;
        case 'deleteBuilding': if (confirm('¿Eliminar construcción y sus planos?')) { s.escena.edificios = s.escena.edificios.filter(function (b) { return b.id !== s.edificio; }); s.edificio = null; dirty(); pintar(); } break;
        case 'deleteFloor': if (confirm('¿Eliminar piso y sus espacios?')) { edificio().pisos = edificio().pisos.filter(function (p) { return p.id !== s.piso; }); sincronizarAltura(edificio()); s.piso = null; dirty(); pintar(); } break;
        case 'deleteSpace': if (confirm('¿Eliminar espacio y figuras?')) { piso().espacios = piso().espacios.filter(function (a) { return a.id !== s.espacio; }); s.espacio = null; dirty(); pintar(); } break;
        case 'unlinkOffice': if (espacio().activos.length) { avisar('Quita primero las figuras de activos.', true); return; } espacio().oficinaId = null; s.inventario = null; dirty(); pintar(); break;
        case 'removeAsset': espacio().activos = espacio().activos.filter(function (z) { return z.activoId !== s.activoSeleccionado; }); s.activoSeleccionado = null; dirty(); pintar(); break;
      }
    });
    panel.addEventListener('change', function (ev) {
      if (ev.target.classList.contains('me-icono-file')) {
        var archivoIcono = ev.target.files && ev.target.files[0];
        var auxiliarArchivo = Number(ev.target.dataset.auxiliarId), predioArchivo = s.predioId;
        if (!archivoIcono || !auxiliarArchivo) return;
        if (!/^image\/(png|jpeg)$/.test(archivoIcono.type) || archivoIcono.size > 150000) {
          avisar('Usa PNG o JPG de hasta 150 KB.', true); ev.target.value = ''; return;
        }
        var datosIcono = new FormData(); datosIcono.append('archivo', archivoIcono);
        ev.target.disabled = true;
        request(BASE + '/predios/' + predioArchivo + '/auxiliares/' + auxiliarArchivo + '/icono-imagen',
          { method: 'POST', body: datosIcono }).then(function () {
          if (s.predioId !== predioArchivo) return;
          s.iconosAuxiliares[auxiliarArchivo] = 'PERSONALIZADO'; s.iconoRevision[auxiliarArchivo] = Date.now();
          avisar('Icono propio guardado para este auxiliar.', false); pintar();
        }).catch(function (error) { avisar(error.message, true); pintar(); });
        return;
      }
      if (ev.target.dataset.prop === 'tamanoGeneral') {
        var oficinaTamano = espacio(), valorGeneral = Number(ev.target.value);
        if (!oficinaTamano || !isFinite(valorGeneral) || valorGeneral < .1 || valorGeneral > Math.min(12, oficinaTamano.ancho, oficinaTamano.fondo)) {
          avisar('Tamaño fuera del espacio.', true); pintar(); return;
        }
        oficinaTamano.tamanoMarcador = valorGeneral;
        oficinaTamano.activos.forEach(function (z) { cambiarTamanoMarcador(oficinaTamano, z, valorGeneral); });
        dirty(); pintar(); return;
      }
      if (ev.target.dataset.prop === 'tamanoIndividual') {
        var oficinaIndividual = espacio(), elegidoIndividual = oficinaIndividual && oficinaIndividual.activos.find(function (z) { return Number(z.activoId) === Number(s.activoSeleccionado); });
        if (!cambiarTamanoMarcador(oficinaIndividual, elegidoIndividual, Number(ev.target.value))) {
          avisar('Tamaño fuera del espacio.', true); pintar(); return;
        }
        dirty(); pintar(); return;
      }
      if (ev.target.classList.contains('me-icono-auxiliar')) {
        var auxiliarId = Number(ev.target.dataset.auxiliarId), icono = ev.target.value;
        if (!edita || !auxiliarId || !TRAZOS_ICONO[icono]) return;
        ev.target.disabled = true;
        request(BASE + '/predios/' + s.predioId + '/auxiliares/' + auxiliarId + '/icono', {
          method: 'PUT', headers: { 'Accept': 'application/json', 'Content-Type': 'application/json' }, body: JSON.stringify({ icono: icono })
        }).then(function () { s.iconosAuxiliares[auxiliarId] = icono; avisar('Icono del auxiliar guardado.', false); pintar(); })
          .catch(function (error) { avisar(error.message, true); pintar(); });
        return;
      }
      var key = ev.target.dataset.prop; if (!key) return;
      if (key === 'cuartoNombre' || /^mueble/.test(key) || key === 'figuraActivo' || key === 'anchoActivo' || key === 'fondoActivo' || key === 'giro') {
        var objetivo = key === 'cuartoNombre' ? cuarto() : /^mueble/.test(key) ? mueble() : espacio() && espacio().activos.find(function (z) { return Number(z.activoId) === Number(s.activoSeleccionado); });
        if (!objetivo) return;
        var atributo = { cuartoNombre: 'nombre', muebleNombre: 'nombre', muebleFigura: 'figura', muebleAncho: 'ancho', muebleFondo: 'fondo', muebleGiro: 'giro', figuraActivo: 'figura', anchoActivo: 'ancho', fondoActivo: 'fondo', giro: 'giro' }[key];
        var nuevo = ev.target.type === 'number' ? Number(ev.target.value) : ev.target.value.trim();
        if (nuevo === '' || (ev.target.type === 'number' && (!isFinite(nuevo) || nuevo < Number(ev.target.min) || nuevo > Number(ev.target.max))) ||
            (atributo === 'figura' && FIGURAS.indexOf(nuevo) < 0)) { avisar('Valor inválido', true); pintar(); return; }
        var anteriorInterior = JSON.stringify(s.escena); objetivo[atributo] = nuevo;
        if ((atributo === 'ancho' || atributo === 'fondo') &&
            (objetivo.x + objetivo.ancho > espacio().ancho + .01 || objetivo.y + objetivo.fondo > espacio().fondo + .01)) {
          s.escena = JSON.parse(anteriorInterior); avisar('La figura debe quedar dentro del espacio.', true); pintar(); return;
        }
        dirty(); pintar(); return;
      }
      if (key === 'beEscala') {
        escalarEdificio(s.escena.edificios.find(function (b) { return b.id === s.editarEdificio; }), Number(ev.target.value) / 100);
        return;
      }
      if (['vistaX', 'vistaY', 'vistaAncho', 'vistaFondo'].indexOf(key) >= 0) {
        var valorVista = Number(ev.target.value);
        if (!isFinite(valorVista) || valorVista < Number(ev.target.min) || valorVista > Number(ev.target.max)) { avisar('Valor inválido para el campo visual.', true); pintar(); return; }
        if (!s.escena.vista) s.escena.vista = marcoAutomatico();
        s.escena.vista[{ vistaX: 'x', vistaY: 'y', vistaAncho: 'ancho', vistaFondo: 'fondo' }[key]] = valorVista;
        s.zoom = 1; s.panX = s.panY = 0; dirty(); pintar(); return;
      }
      if (key === 'giroEdificio' || key === 'beGiro') {
        var angulo = Number(ev.target.value);
        if (!isFinite(angulo) || angulo < 0 || angulo > 360) { avisar('El giro debe estar entre 0 y 360 grados.', true); pintar(); return; }
        var figuraEdificio = edificio() || s.escena.edificios.find(function (b) { return b.id === s.editarEdificio; });
        if (figuraEdificio) girarEdificio(angulo - Number(figuraEdificio.giro || 0)); return;
      }
      if (key === 'selGiro' || key === 'giroEspacio') {
        var nuevoGiro = Number(ev.target.value);
        if (!isFinite(nuevoGiro) || nuevoGiro < 0 || nuevoGiro > 360) { avisar('El giro debe estar entre 0 y 360 grados.', true); pintar(); return; }
        var figuraGiro = espacio() || (piso() && piso().espacios.find(function (r) { return r.id === s.editarEspacio; }));
        if (figuraGiro) girarEspacio(nuevoGiro - Number(figuraGiro.giro || 0));
        return;
      }
      if (['fondoX', 'fondoY', 'fondoAncho', 'fondoFondo', 'fondoOpacidad', 'fondoGiro'].indexOf(key) >= 0 && s.escena.fondo) {
        var subcampo = { fondoX: 'x', fondoY: 'y', fondoAncho: 'ancho', fondoFondo: 'fondo', fondoOpacidad: 'opacidad', fondoGiro: 'giro' }[key];
        var valor = Number(ev.target.value);
        if (!subcampo || !isFinite(valor) || valor < Number(ev.target.min) || valor > Number(ev.target.max)) { avisar('Valor inválido para la imagen.', true); pintar(); return; }
        var fondo = s.escena.fondo;
        if (subcampo === 'ancho' || subcampo === 'fondo') {
          var anchoAnterior = fondo.ancho, fondoAnterior = fondo.fondo;
          var relacion = fondo.relacion || fondo.ancho / fondo.fondo;
          if (subcampo === 'ancho') fondo.fondo = redondear(valor / relacion);
          else fondo.ancho = redondear(valor * relacion);
          if (fondo.ancho > 2000 || fondo.fondo > 2000 || fondo.ancho < .1 || fondo.fondo < .1) {
            fondo.ancho = anchoAnterior; fondo.fondo = fondoAnterior;
            avisar('El tamaño proporcional supera el límite del plano.', true); pintar(); return;
          }
        }
        fondo[subcampo] = valor; dirty(); pintar(); return;
      }
      if (['refX', 'refY', 'refAncho', 'refFondo', 'refOpacidad', 'refGiro'].indexOf(key) >= 0) {
        var objetivoPlano = referenciaActual(), imagen = objetivoPlano && objetivoPlano.planoReferencia;
        if (!imagen) return;
        var clavePlano = { refX: 'x', refY: 'y', refAncho: 'ancho', refFondo: 'fondo', refOpacidad: 'opacidad', refGiro: 'giro' }[key];
        var nuevoValor = Number(ev.target.value), limite = clavePlano === 'opacidad' ? 1 : clavePlano === 'giro' ? 360 : 2000;
        if (!isFinite(nuevoValor) || nuevoValor < (clavePlano === 'x' || clavePlano === 'y' ? -2000 : clavePlano === 'ancho' || clavePlano === 'fondo' ? .1 : 0) || nuevoValor > limite) {
          avisar('Valor fuera del tamaño del plano.', true); pintar(); return;
        }
        imagen[clavePlano] = nuevoValor; dirty(); pintar(); return;
      }
      var seleccionado = /^sel[A-Z]/.test(key);
      var edificioSeleccionado = /^be[A-Z]/.test(key);
      var o = edificioSeleccionado ? s.escena.edificios.find(function (b) { return b.id === s.editarEdificio; }) :
        seleccionado ? (piso() && piso().espacios.find(function (r) { return r.id === s.editarEspacio; })) : (espacio() || piso() || edificio());
      if (!o) return;
      if (seleccionado) key = key.charAt(3).toLowerCase() + key.slice(4);
      if (edificioSeleccionado) key = key.charAt(2).toLowerCase() + key.slice(3);
      if (key === 'anchoActivo' || key === 'fondoActivo' || key === 'giro') { o = espacio().activos.find(function (z) { return z.activoId === s.activoSeleccionado; }); key = key === 'anchoActivo' ? 'ancho' : key === 'fondoActivo' ? 'fondo' : key; }
      var v = ev.target.type === 'number' ? Number(ev.target.value) : ev.target.value.trim();
      if (v === '' || (ev.target.type === 'number' && (!isFinite(v) || v < Number(ev.target.min || 0) || v > Number(ev.target.max || 2000)))) { avisar('Valor inválido', true); pintar(); return; }
      if (key === 'color' && !/^#[0-9a-fA-F]{6}$/.test(v)) { avisar('Color inválido', true); pintar(); return; }
      var anterior = JSON.stringify(s.escena);
      if ((key === 'x' || key === 'y') && o.puntos) {
        var diferencia = v - o[key];
        o.puntos.forEach(function (pt) { pt[key] = redondear(pt[key] + diferencia); });
      }
      if ((key === 'ancho' || key === 'fondo') && v !== o[key]) {
        var escala = v / o[key], hijos = o.pisos ? o.pisos.reduce(function (todos, p) { return todos.concat(p.espacios); }, []) : (o.activos || []).concat(o.mobiliario || [], o.cuartos || []);
        var coord = key === 'ancho' ? 'x' : 'y';
        if (o.puntos) o.puntos.forEach(function (pt) { pt[coord] = redondear(o[coord] + (pt[coord] - o[coord]) * escala); });
        hijos.forEach(function (h) {
          if (key === 'ancho') { h.x = Math.round(h.x * escala * 10) / 10; h.ancho = Math.round(h.ancho * escala * 10) / 10; }
          else { h.y = Math.round(h.y * escala * 10) / 10; h.fondo = Math.round(h.fondo * escala * 10) / 10; }
          (h.puntos || []).forEach(function (pt) { pt[coord] = redondear(pt[coord] * escala); });
          (h.activos || []).forEach(function (z) {
            if (key === 'ancho') { z.x = Math.round(z.x * escala * 10) / 10; z.ancho = Math.round(z.ancho * escala * 10) / 10; }
            else { z.y = Math.round(z.y * escala * 10) / 10; z.fondo = Math.round(z.fondo * escala * 10) / 10; }
          });
          (h.mobiliario || []).concat(h.cuartos || []).forEach(function (z) {
            if (key === 'ancho') { z.x = redondear(z.x * escala); z.ancho = redondear(z.ancho * escala); }
            else { z.y = redondear(z.y * escala); z.fondo = redondear(z.fondo * escala); }
            (z.puntos || []).forEach(function (pt) { pt[coord] = redondear(pt[coord] * escala); });
          });
        });
        if (o.pisos) o.pisos.forEach(function (p) { (p.trazos || []).forEach(function (t) {
          if (key === 'ancho') { t.x1 = redondear(t.x1 * escala); t.x2 = redondear(t.x2 * escala); }
          else { t.y1 = redondear(t.y1 * escala); t.y2 = redondear(t.y2 * escala); }
        }); });
      }
      o[key] = v;
      if (key === 'altura' && !o.pisos && piso() === o) sincronizarAltura(edificio());
      if ((seleccionado || o.puntos) && (!figuraCabe(o) || (o.activos && edificio() &&
          (o.x + o.ancho > edificio().ancho + .01 || o.y + o.fondo > edificio().fondo + .01)))) {
        s.escena = JSON.parse(anterior); avisar('La figura o su contenido quedaría fuera del plano.', true); pintar(); return;
      }
      dirty(); pintar();
    });
    var buscarTimer;
    panel.addEventListener('input', function (ev) {
      if (ev.target.dataset.prop === 'tamanoGeneral') {
        var salidaTamano = ev.target.parentElement.querySelector('output');
        if (salidaTamano) salidaTamano.textContent = ev.target.value;
        return;
      }
      var office = ev.target.classList.contains('me-buscar-oficina'), inventario = ev.target.classList.contains('me-filtro-inventario');
      if (!office && !inventario) return;
      clearTimeout(buscarTimer);
      var q = ev.target.value, a = espacio(), destino = panel.querySelector('.me-resultados-oficina');
      var objetivoOficina = ev.target.dataset.officeTarget;
      buscarTimer = setTimeout(function () {
        if (inventario) { if (espacio() && espacio().id === a.id) cargarInventario(a.oficinaId, q, 0); return; }
        request(BASE + '/predios/' + s.predioId + '/oficinas?q=' + encodeURIComponent(q))
          .then(function (items) { if (!destino.isConnected) return; destino.innerHTML = items.map(function (it) {
            return '<button class="me-item" data-office-id="' + it.id + '" data-office-target="' + esc(objetivoOficina) + '" data-office-code="' + esc(it.codOfi) + '" data-office-name="' + esc(it.nombre) + '">' + esc(it.codOfi + ' · ' + it.nombre) + '</button>'; }).join('') || '<span class="me-muted">Sin resultados en este predio</span>'; })
          .catch(function (e) { avisar(e.message, true); });
      }, 220);
    });
    panel.addEventListener('focusin', function (ev) {
      if (ev.target.classList.contains('me-buscar-oficina') && !ev.target.value.trim())
        ev.target.dispatchEvent(new Event('input', { bubbles: true }));
    });
    if (typeof ResizeObserver !== 'undefined') {
      var ultimoAncho = 0, ultimoAlto = 0;
      var observador = new ResizeObserver(function () {
        if (!root.isConnected) { observador.disconnect(); return; }
        var ancho = canvas.clientWidth, alto = canvas.clientHeight;
        if (ancho > 0 && alto > 0 && (Math.abs(ancho - ultimoAncho) > 2 || Math.abs(alto - ultimoAlto) > 2)) {
          ultimoAncho = ancho; ultimoAlto = alto;
          if (s.predioId != null) pintar();
        }
      });
      observador.observe(canvas.parentElement);
    } else window.addEventListener('resize', function () { if (root.isConnected && s.predioId != null) pintar(); });
    request(BASE + '/predios').then(function (items) {
      sel.innerHTML = items.map(function (p) { return '<option value="' + p.id + '">' + esc(p.nombre) + '</option>'; }).join('');
      var lasPalmas = items.find(function (p) { return /LAS PALMAS/i.test(p.nombre); });
      if (items.length) { sel.value = lasPalmas ? lasPalmas.id : items[0].id; cambiarPredio(sel.value); }
      else panel.innerHTML = '<p>No hay predios disponibles.</p>';
    }).catch(function (e) { avisar(e.message, true); });
  }
  window.MapaEspacial = { iniciar: iniciar };
}());
