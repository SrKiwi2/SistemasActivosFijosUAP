const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

class Nodo {
  constructor(tag, attrs = {}, content = '') {
    this.tag = tag;
    this.attrs = { ...attrs };
    this.content = content;
    this.children = [];
  }
  appendChild(child) { this.children.push(child); return child; }
  setAttribute(key, value) {
    this.attrs[key] = value;
    if (key === 'viewBox') {
      const [x, y, width, height] = value.split(' ').map(Number);
      this.viewBox.baseVal = { x, y, width, height };
    }
  }
  setAttributeNS(_namespace, key, value) { this.setAttribute(key, value); }
}

const globales = { window: {}, document: { createDocumentFragment: () => new Nodo('fragment') } };
const archivo = path.resolve(__dirname, '../../main/resources/static/assets/js/mapa-espacial-3d.js');
vm.runInNewContext(fs.readFileSync(archivo, 'utf8'), globales, { filename: archivo });
const render = globales.window.MapaEspacial3D.dibujar;
const util = {
  svg: (tag, attrs, texto) => new Nodo(tag, attrs, texto),
  tono: (color) => color,
  puntosRect: (o) => [{ x: o.x, y: o.y }, { x: o.x + o.ancho, y: o.y },
    { x: o.x + o.ancho, y: o.y + o.fondo }, { x: o.x, y: o.y + o.fondo }],
  edita: true
};
const edificio = {
  id: 'edificio', nombre: 'BIBLIOTECA', tipo: 'EDIFICIO', x: 20, y: 30, ancho: 100, fondo: 80,
  color: '#f8eb69', puntos: [{ x: 20, y: 30 }, { x: 120, y: 30 }, { x: 120, y: 80 },
    { x: 80, y: 80 }, { x: 80, y: 110 }, { x: 20, y: 110 }],
  pisos: [
    { id: 'pb', nombre: 'PLANTA BAJA', altura: 4, espacios: [{ id: 'oficina', nombre: 'OFICINA',
      x: 5, y: 5, ancho: 20, fondo: 20, oficinaId: 7,
      puntos: [{ x: 5, y: 5 }, { x: 25, y: 5 }, { x: 25, y: 25 }, { x: 5, y: 25 }] }] },
    { id: 'p1', nombre: 'PISO 1', altura: 5, espacios: [{ id: 'superior', nombre: 'SALA',
      x: 30, y: 10, ancho: 20, fondo: 20, oficinaId: 8 }] }
  ]
};
function canvas() {
  const nodo = new Nodo('svg');
  nodo.clientWidth = 900;
  nodo.clientHeight = 650;
  nodo.viewBox = { baseVal: { x: 0, y: 0, width: 1000, height: 700 } };
  return nodo;
}
function todos(nodo) { return [nodo, ...nodo.children.flatMap(todos)]; }
function estado(piso, techo = true) {
  return { escena: { edificios: [edificio] }, capas: { edificios: true }, edificio: piso ? edificio.id : null,
    piso, camara: 45, inclinacion: 32, zoom: 1, techo3d: techo };
}

const general = canvas();
render(general, estado(null), util);
const elementosGeneral = todos(general);
assert(elementosGeneral.some((n) => n.attrs['data-kind'] === 'floor3d' && n.attrs['data-floor-id'] === 'pb'));
assert(elementosGeneral.some((n) => n.attrs['data-kind'] === 'floor3d' && n.attrs['data-floor-id'] === 'p1'));
assert(elementosGeneral.some((n) => n.attrs['data-kind'] === 'building' && n.content === ''));
assert.equal(globales.window.MapaEspacial3D.escalaAltura, 8);
assert(elementosGeneral.some((n) => n.attrs['data-face'] === 'roof' && n.attrs['fill-opacity'] === 1));
assert(!elementosGeneral.some((n) => String(n.attrs.class || '').includes('me-3d-face') && n.attrs['fill-opacity'] < 1));
assert.equal(elementosGeneral.filter((n) => n.tag === 'polygon' && String(n.attrs.class || '').includes('me-3d-face')).at(-1).attrs['data-face'], 'roof');
assert(elementosGeneral.filter((n) => n.tag === 'text' && String(n.attrs.class || '').includes('me-3d-floor-label')).length >= 2);

const conFondo = canvas();
const mapaConFondo = estado(null);
mapaConFondo.tieneFondo = true;
mapaConFondo.capas.fondo = true;
mapaConFondo.escena.fondo = { x: 10, y: 20, ancho: 200, fondo: 150, giro: 18, opacidad: .55 };
mapaConFondo.predioId = 12;
mapaConFondo.fondoRevision = 4;
render(conFondo, mapaConFondo, util);
const imagen = todos(conFondo).find((n) => n.tag === 'image');
assert(imagen);
assert.equal(imagen.attrs.width, 200);
assert.equal(imagen.attrs.height, 150);
assert.match(imagen.attrs.href, /predios\/12\/fondo\/publicado\?v=4$/);
mapaConFondo.modoEdicion = true;
const fondoEditor = canvas();
render(fondoEditor, mapaConFondo, util);
assert.match(todos(fondoEditor).find((n) => n.tag === 'image').attrs.href, /predios\/12\/fondo\?v=4$/);
assert.equal(imagen.attrs.opacity, .55);

// La huella y la imagen deben compartir la misma proyección, aunque el fondo
// tenga dimensiones y origen distintos del conjunto de edificios.
const alineado = canvas();
const vistaAlineada = estado(null);
vistaAlineada.tieneFondo = true;
vistaAlineada.capas.fondo = true;
vistaAlineada.escena.fondo = { x: 0, y: 0, ancho: 200, fondo: 180, giro: 0, opacidad: .5 };
vistaAlineada.predioId = 12;
render(alineado, vistaAlineada, util);
const nodosAlineados = todos(alineado);
const matriz = nodosAlineados.find((n) => n.tag === 'image').attrs.transform.match(/^matrix\(([^)]+)\)$/)[1].split(' ').map(Number);
const esperadoX = matriz[0] * edificio.x + matriz[2] * edificio.y + matriz[4];
const esperadoY = matriz[1] * edificio.x + matriz[3] * edificio.y + matriz[5];
const verticesEdificio = nodosAlineados.filter((n) => n.tag === 'polygon' && n.attrs['data-building-id'] === edificio.id)
  .flatMap((n) => n.attrs.points.split(' ').map((p) => p.split(',').map(Number)));
assert(verticesEdificio.some(([x, y]) => Math.abs(x - esperadoX) < 1e-6 && Math.abs(y - esperadoY) < 1e-6));

const planta = canvas();
render(planta, estado('pb'), util);
const elementosPlanta = todos(planta);
assert(elementosPlanta.some((n) => n.attrs['data-kind'] === 'space3d' && n.attrs['data-id'] === 'oficina'));
assert(elementosPlanta.some((n) => n.attrs['data-height-floor-id'] === 'pb'));
assert(!elementosPlanta.some((n) => n.attrs['data-floor-id'] === 'p1'));

const camaraEstable = canvas();
const estadoCamara = estado(null);
estadoCamara.camara = 122;
estadoCamara.marco3dFijo = { x: 12, y: 18, ancho: 420, alto: 300 };
render(camaraEstable, estadoCamara, util);
assert.equal(camaraEstable.viewBox.baseVal.x, 12);
assert.equal(camaraEstable.viewBox.baseVal.width, 420);

const sinTecho = canvas();
const vistaSinTecho = estado(null, false);
vistaSinTecho.edificio = edificio.id;
render(sinTecho, vistaSinTecho, util);
assert(todos(sinTecho).some((n) => n.attrs['data-kind'] === 'floor3d' && n.attrs['data-floor-id'] === 'p1'));
assert(todos(sinTecho).some((n) => n.attrs['data-kind'] === 'space3d' && n.attrs['data-id'] === 'superior'));
assert(Number.isFinite(planta.viewBox.baseVal.width) && planta.viewBox.baseVal.width > 0);

const explorador = canvas();
const vistaExplorador = estado(null, false);
vistaExplorador.edificio = edificio.id;
vistaExplorador.destacadoEdificio = edificio.id;
render(explorador, vistaExplorador, { ...util, edita: false });
const elementosExplorador = todos(explorador);
assert(elementosExplorador.some((n) => n.attrs['data-face'] === 'roof'));
assert(!elementosExplorador.some((n) => n.attrs['data-kind'] === 'space3d'));
assert(elementosExplorador.some((n) => String(n.attrs.class || '').includes('me-selected-3d')));

const localizado = canvas();
const vistaLocalizada = estado(null, true);
vistaLocalizada.destacados = { edificio: ['pb'] };
render(localizado, vistaLocalizada, { ...util, edita: false });
const carasLocalizadas = todos(localizado).filter((n) => n.attrs['data-kind'] === 'floor3d');
assert(carasLocalizadas.some((n) => n.attrs['data-floor-id'] === 'pb' && String(n.attrs.class).includes('me-found-floor')));
assert(carasLocalizadas.some((n) => n.attrs['data-floor-id'] === 'p1' && !String(n.attrs.class).includes('me-found-floor')));

const campus = canvas();
const numerosos = estado(null, false);
numerosos.escena.edificios = Array.from({ length: 36 }, (_, i) => ({ ...edificio, id: 'ed-' + i }));
render(campus, numerosos, util);
assert(todos(campus).filter((n) => n.attrs['data-kind'] === 'building').length >= 36);

console.log('Mapa espacial 3D: contorno, pisos, oficina y corte verificados.');
