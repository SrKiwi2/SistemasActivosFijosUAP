(function () {
  'use strict';

  var ESCALA_ALTURA = 8;

  function dibujar(canvas, estado, util) {
    var edificios = estado.capas.edificios ? estado.escena.edificios : [];
    var angulo = estado.camara * Math.PI / 180;
    var inclinacion = estado.inclinacion * Math.PI / 180;
    var coseno = Math.cos(angulo), seno = Math.sin(angulo);
    var caras = [], rotulos = [], oficinas = [], marcasNivel = [], puntosVista = [], puntosSeleccionados = [];
    var detalleReducido = edificios.length > 35;
    var foco = edificios.find(function (e) { return e.id === (estado.destacadoEdificio || estado.edificio); });
    var amplitud = foco ? [foco] : edificios;
    var centroX = amplitud.length ? (Math.min.apply(null, amplitud.map(function (e) { return e.x; })) +
      Math.max.apply(null, amplitud.map(function (e) { return e.x + e.ancho; }))) / 2 : 0;
    var centroY = amplitud.length ? (Math.min.apply(null, amplitud.map(function (e) { return e.y; })) +
      Math.max.apply(null, amplitud.map(function (e) { return e.y + e.fondo; }))) / 2 : 0;

    function profundidad(x, y) { return x * seno + y * coseno; }
    function proyectar(x, y, altura, registrar) {
      var relativoX = x - centroX, relativoY = y - centroY;
      var lateral = relativoX * coseno - relativoY * seno;
      var punto = [500 + lateral * .95,
        300 + profundidad(relativoX, relativoY) * .95 * Math.sin(inclinacion) - altura * ESCALA_ALTURA * Math.cos(inclinacion)];
      if (registrar !== false) puntosVista.push(punto);
      return punto;
    }
    function listaPuntos(vertices) { return vertices.map(function (p) { return p[0] + ',' + p[1]; }).join(' '); }
    function cara(vertices, color, profundidadCara, datos, titulo, opacidad) {
      caras.push({ vertices: vertices, color: color, profundidad: profundidadCara, datos: datos,
        titulo: titulo, opacidad: opacidad || 1 });
    }
    function datosEdificio(e, tipo, piso) {
      var marcas = estado.destacados && estado.destacados[e.id];
      var atributos = { class: 'me-shape me-3d-face' + (estado.edificio === e.id || estado.destacadoEdificio === e.id ? ' me-selected-3d' : '') +
          (marcas ? ' me-found-building' : '') + (piso && marcas && marcas.indexOf(piso.id) >= 0 ? ' me-found-floor' : ''),
        'data-kind': tipo, 'data-id': e.id, 'data-building-id': e.id };
      if (piso) atributos['data-floor-id'] = piso.id;
      return atributos;
    }

    edificios.forEach(function (e) {
      var inicioPuntos = puntosVista.length;
      var huella = (e.puntos || util.puntosRect(e)).map(function (p) { return [Number(p.x), Number(p.y)]; });
      if (huella.length < 3 || !(e.pisos || []).length) return;
      var seleccionado = estado.edificio === e.id || estado.destacadoEdificio === e.id || !!(estado.destacados && estado.destacados[e.id]);
      var simplificar = detalleReducido && !seleccionado;
      var indicePiso = seleccionado && estado.piso ? e.pisos.findIndex(function (p) { return p.id === estado.piso; }) : -1;
      var limite = indicePiso < 0 ? e.pisos.length - 1 : indicePiso;
      var color = e.color || (e.tipo === 'EDIFICIO' ? '#b9dce9' : '#e5dbc2');
      var bandas = simplificar ? [{ piso: null, abajo: 0, arriba: e.pisos.reduce(function (n, p) { return n + Number(p.altura || 4); }, 0) }] : [];
      if (!simplificar) {
        var altura = 0;
        e.pisos.forEach(function (p, i) {
          var siguiente = altura + Number(p.altura || 4);
          if (i <= limite) bandas.push({ piso: p, abajo: altura, arriba: siguiente });
          altura = siguiente;
        });
      }
      bandas.forEach(function (banda, bi) {
        huella.forEach(function (p, i) {
          var siguiente = huella[(i + 1) % huella.length];
          var vertices = [proyectar(p[0], p[1], banda.abajo), proyectar(siguiente[0], siguiente[1], banda.abajo),
            proyectar(siguiente[0], siguiente[1], banda.arriba), proyectar(p[0], p[1], banda.arriba)];
          var fondo = profundidad((p[0] + siguiente[0]) / 2, (p[1] + siguiente[1]) / 2);
          var tonoPared = util.tono(color, (i % 2 ? .66 : .77) * (bi % 2 ? .94 : 1));
          cara(vertices, tonoPared, fondo, datosEdificio(e, banda.piso ? 'floor3d' : 'building', banda.piso),
            e.nombre + (banda.piso ? ' · ' + banda.piso.nombre : ''), 1);
        });
      });
      if (!simplificar && bandas.length > 1) {
        var profundidadCentro = huella.reduce(function (n, p) { return n + profundidad(p[0], p[1]); }, 0) / huella.length;
        bandas.slice(0, -1).forEach(function (banda) {
          huella.forEach(function (p, i) {
            var siguiente = huella[(i + 1) % huella.length];
            if (profundidad((p[0] + siguiente[0]) / 2, (p[1] + siguiente[1]) / 2) < profundidadCentro) return;
            marcasNivel.push({ inicio: proyectar(p[0], p[1], banda.arriba, false),
              fin: proyectar(siguiente[0], siguiente[1], banda.arriba, false), destacado: seleccionado });
          });
        });
      }
      var ultimo = bandas[bandas.length - 1];
      if (!ultimo) return;
      var cubierta = indicePiso < 0 && (estado.techo3d || !util.edita);
      var techo = huella.map(function (p) { return proyectar(p[0], p[1], ultimo.arriba); });
      var profundidadTecho = Math.max.apply(null, huella.map(function (p) { return profundidad(p[0], p[1]); })) + .01;
      cara(techo, cubierta ? color : '#f5fafb', profundidadTecho,
        Object.assign(datosEdificio(e, cubierta || !ultimo.piso ? 'building' : 'floor3d', cubierta ? null : ultimo.piso), { 'data-face': 'roof' }),
        e.nombre + (cubierta ? ' · techo' : ' · ' + (ultimo.piso ? ultimo.piso.nombre : 'último nivel')), 1);

      if (util.edita && seleccionado && (indicePiso >= 0 || !estado.techo3d)) {
        var pisoVisible = indicePiso >= 0 ? e.pisos[indicePiso] : e.pisos[e.pisos.length - 1];
        (pisoVisible.espacios || []).forEach(function (r) {
          var local = r.puntos || util.puntosRect(r);
          var vertices = local.map(function (pt) { return proyectar(e.x + Number(pt.x), e.y + Number(pt.y), ultimo.arriba + .08); });
          oficinas.push({ edificio: e, piso: pisoVisible, espacio: r, vertices: vertices,
            centro: proyectar(e.x + r.x + r.ancho / 2, e.y + r.y + r.fondo / 2, ultimo.arriba + .1) });
        });
        if (util.edita && indicePiso >= 0) {
          var cercano = huella.reduce(function (previo, p) { return profundidad(p[0], p[1]) > profundidad(previo[0], previo[1]) ? p : previo; });
          var base = proyectar(cercano[0], cercano[1], ultimo.arriba);
          rotulos.push({ tipo: 'altura', e: e, piso: pisoVisible, base: base, punto: [base[0], base[1] - 24] });
          puntosVista.push([base[0], base[1] - 32]);
        }
      }
      if (!simplificar && e.pisos.length > 1 && indicePiso < 0) {
        var alturaAcumulada = 0;
        e.pisos.forEach(function (p) {
          var mitad = alturaAcumulada + Number(p.altura || 4) / 2;
          var cercano = huella.reduce(function (previo, punto) { return profundidad(punto[0], punto[1]) > profundidad(previo[0], previo[1]) ? punto : previo; });
          rotulos.push({ tipo: 'nivel', texto: p.nombre, punto: proyectar(cercano[0], cercano[1], mitad) });
          alturaAcumulada += Number(p.altura || 4);
        });
      }
      if (!seleccionado || indicePiso < 0) {
        rotulos.push({ tipo: 'edificio', texto: e.nombre,
          punto: proyectar(e.x + e.ancho / 2, e.y + e.fondo / 2, ultimo.arriba + .2), color: color });
      }
      if (seleccionado) puntosSeleccionados = puntosVista.slice(inicioPuntos);
    });

    var fondo = estado.tieneFondo && estado.capas.fondo &&
      (estado.escena.fondo || { x: 0, y: 0, ancho: 1000, fondo: 700, opacidad: .5, giro: 0 });
    var esquinasFondo = null;
    if (fondo) {
      var centroFondoX = fondo.x + fondo.ancho / 2, centroFondoY = fondo.y + fondo.fondo / 2;
      var giro = Number(fondo.giro || 0) * Math.PI / 180;
      var cosGiro = Math.cos(giro), sinGiro = Math.sin(giro);
      esquinasFondo = [[fondo.x, fondo.y], [fondo.x + fondo.ancho, fondo.y],
        [fondo.x, fondo.y + fondo.fondo]].map(function (p) {
          var dx = p[0] - centroFondoX, dy = p[1] - centroFondoY;
          return proyectar(centroFondoX + dx * cosGiro - dy * sinGiro,
            centroFondoY + dx * sinGiro + dy * cosGiro, 0, false);
        });
      if (!puntosVista.length) esquinasFondo.forEach(function (p) { puntosVista.push(p); });
    }
    if (!puntosVista.length) { canvas.setAttribute('viewBox', '0 0 1000 700'); return; }
    var enfoque = puntosSeleccionados.length ? puntosSeleccionados : puntosVista;
    var xs = enfoque.map(function (p) { return p[0]; }), ys = enfoque.map(function (p) { return p[1]; });
    var minX = Math.min.apply(null, xs) - 60, maxX = Math.max.apply(null, xs) + 60;
    var minY = Math.min.apply(null, ys) - 50, maxY = Math.max.apply(null, ys) + 50;
    var marcoFijo = estado.marco3dFijo;
    canvas.setAttribute('viewBox', marcoFijo && marcoFijo.ancho > 0 && marcoFijo.alto > 0
      ? marcoFijo.x + ' ' + marcoFijo.y + ' ' + marcoFijo.ancho + ' ' + marcoFijo.alto
      : minX + ' ' + minY + ' ' + Math.max(250, maxX - minX) + ' ' + Math.max(180, maxY - minY));

    var lote = document.createDocumentFragment();
    if (fondo && esquinasFondo) {
      var origen = esquinasFondo[0], horizontal = esquinasFondo[1], vertical = esquinasFondo[2];
      var imagen = util.svg('image', { x: 0, y: 0, width: fondo.ancho, height: fondo.fondo,
        transform: 'matrix(' + ((horizontal[0] - origen[0]) / fondo.ancho) + ' ' + ((horizontal[1] - origen[1]) / fondo.ancho) + ' ' +
          ((vertical[0] - origen[0]) / fondo.fondo) + ' ' + ((vertical[1] - origen[1]) / fondo.fondo) + ' ' + origen[0] + ' ' + origen[1] + ')',
        opacity: fondo.opacidad, preserveAspectRatio: 'xMidYMid meet', 'pointer-events': 'none' });
      var urlFondo = '/administracion/mapa-espacial/api/predios/' + estado.predioId + '/fondo' + (estado.modoEdicion ? '' : '/publicado') + '?v=' + estado.fondoRevision;
      imagen.setAttribute('href', urlFondo);
      imagen.setAttributeNS('http://www.w3.org/1999/xlink', 'href', urlFondo);
      lote.appendChild(imagen);
    }
    caras.sort(function (a, b) { return a.profundidad - b.profundidad; });
    caras.forEach(function (dato) {
        var attrs = { points: listaPuntos(dato.vertices), fill: dato.color, stroke: dato.datos['data-face'] === 'roof' ? '#456d83' : '#54788e',
        'stroke-width': 1.3, 'stroke-linejoin': 'round', 'vector-effect': 'non-scaling-stroke',
        'fill-opacity': dato.opacidad };
      Object.keys(dato.datos).forEach(function (key) { attrs[key] = dato.datos[key]; });
      var poligono = util.svg('polygon', attrs);
      poligono.appendChild(util.svg('title', {}, dato.titulo));
      lote.appendChild(poligono);
    });
    marcasNivel.forEach(function (linea) {
      lote.appendChild(util.svg('line', { x1: linea.inicio[0], y1: linea.inicio[1], x2: linea.fin[0], y2: linea.fin[1],
        stroke: linea.destacado ? '#27536c' : '#355f76', 'stroke-width': linea.destacado ? 2.5 : 1.8,
        'vector-effect': 'non-scaling-stroke', 'pointer-events': 'none' }));
    });
    oficinas.forEach(function (dato) {
      var r = dato.espacio;
      var poligono = util.svg('polygon', { points: listaPuntos(dato.vertices),
        fill: r.color || (r.oficinaId ? '#78c9df' : '#c9d8df'), 'fill-opacity': 1,
        stroke: r.oficinaId ? '#0f7798' : '#6d8897', 'stroke-width': 1.8,
        'vector-effect': 'non-scaling-stroke', class: 'me-shape me-3d-office',
        'data-kind': 'space3d', 'data-building-id': dato.edificio.id,
        'data-floor-id': dato.piso.id, 'data-id': r.id });
      poligono.appendChild(util.svg('title', {}, r.nombre));
      lote.appendChild(poligono);
    });
    var vb = canvas.viewBox.baseVal;
    var escalaTexto = Math.max(vb.width / Math.max(1, canvas.clientWidth), vb.height / Math.max(1, canvas.clientHeight));
    var tam = Math.min(15, Math.max(.005, 12 * escalaTexto / estado.zoom));
    rotulos.forEach(function (r) {
      if (r.tipo === 'altura') {
        var controlY = r.base[1] - 24 * escalaTexto / estado.zoom;
        lote.appendChild(util.svg('line', { x1: r.base[0], y1: r.base[1], x2: r.base[0], y2: controlY,
          stroke: '#24816a', 'stroke-width': 2, 'vector-effect': 'non-scaling-stroke', 'pointer-events': 'none' }));
        var mando = util.svg('circle', { cx: r.base[0], cy: controlY, r: 8 * escalaTexto / estado.zoom, fill: '#17835d', stroke: '#fff',
          'stroke-width': 2, 'vector-effect': 'non-scaling-stroke', class: 'me-height-handle', 'data-height-floor-id': r.piso.id,
          'data-building-id': r.e.id });
        mando.appendChild(util.svg('title', {}, 'Arrastra para cambiar la altura de ' + r.piso.nombre));
        lote.appendChild(mando);
        return;
      }
      var texto = r.texto.length > 28 ? r.texto.slice(0, 27) + '…' : r.texto;
      var el = util.svg('text', { x: r.punto[0], y: r.punto[1] - (r.tipo === 'edificio' ? 4 : 0),
        'font-size': r.tipo === 'nivel' ? tam * .58 : tam,
        'vector-effect': 'non-scaling-stroke',
        'text-anchor': 'middle', 'dominant-baseline': 'middle',
        class: r.tipo === 'nivel' ? 'me-3d-floor-label' : 'me-3d-building-label',
        'pointer-events': 'none' }, texto);
      el.appendChild(util.svg('title', {}, r.texto));
      lote.appendChild(el);
    });
    if (oficinas.length <= 25) oficinas.forEach(function (dato) {
      if (dato.espacio.ancho < tam * 2 || dato.espacio.fondo < tam * 2) return;
      var nombre = dato.espacio.nombre;
      if (nombre.length > 20) nombre = nombre.slice(0, 19) + '…';
      lote.appendChild(util.svg('text', { x: dato.centro[0], y: dato.centro[1], 'font-size': tam * .7,
        'vector-effect': 'non-scaling-stroke',
        'text-anchor': 'middle', 'dominant-baseline': 'middle', class: 'me-3d-office-label',
        'pointer-events': 'none' }, nombre));
    });
    canvas.appendChild(lote);
  }

  window.MapaEspacial3D = { dibujar: dibujar, escalaAltura: ESCALA_ALTURA };
}());
