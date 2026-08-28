import { almacen } from './almacen';
import type {
  EstadoActivo, Levantamiento, OrigenMarca, Situacion, TileOficina, TilePredio,
} from './levantamiento';

/**
 * Persistencia local del levantamiento.
 *
 * Todo el recorrido tiene que poder hacerse **sin señal**: se guardan el paquete
 * de la oficina (la lista esperada), las marcas hechas en campo y el catálogo de
 * condiciones. Nada de esto se puede perder porque el operador cerró la app o se
 * quedó sin batería en un depósito.
 *
 * ── Sobre el medio de almacenamiento ────────────────────────────────────────
 * Va sobre `almacen` (Capacitor Preferences), no sobre SQLite. El plan móvil
 * reserva SQLite para el maestro de 30.000 activos; aquí el volumen es **una
 * oficina** —cientos de filas—, y sumar un plugin nativo obligaría a rehacer la
 * configuración de Android Studio para el único módulo que no lo necesita.
 *
 * Si algún día se pasa a SQLite, se cambia este archivo y nada más: el resto de
 * la app solo conoce estas funciones.
 */

/** Una marca hecha en campo, tal como se guarda en el teléfono. */
export interface MarcaLocal {
  /** `det:<idDetalle>` para lo esperado, `cod:<clave>` para un sobrante. */
  clave: string;
  idDetalle: number | null;
  codigo: string | null;
  situacion: Situacion;
  origen: OrigenMarca;
  /** Hora del dispositivo al marcar — no la del envío. */
  fecha: string;
  observacion: string | null;
  idEstadoObservado: number | null;
  /**
   * Número de orden de esta marca en el teléfono.
   *
   * Distingue dos marcas sobre el MISMO activo hechas dentro del mismo segundo:
   * la fecha no alcanza para diferenciarlas, y sin eso una marca hecha mientras
   * el lote viajaba podría darse por enviada sin haberlo sido.
   */
  seq: number;
  /** Confirmada por el servidor. Mientras sea false, sigue en la cola. */
  enviada: boolean;
}

/** Recorrido abierto en este teléfono. Es lo que permite ofrecer "Continuar". */
export interface PunteroRecorrido {
  idInventario: number;
  uuidCliente: string;
  idOficina: number;
  oficina: string | null;
  predio: string | null;
  abiertoEn: string;
}

interface Cacheado<T> {
  fecha: string;
  datos: T;
}

const CLAVE_PUNTEROS = 'sciaf.lev.actuales';
const CLAVE_ESTADOS = 'sciaf.lev.estados';
const CLAVE_PREDIOS = 'sciaf.lev.cache.predios';

const clavePaquete = (id: number) => `sciaf.lev.paquete.${id}`;
const claveMarcas = (id: number) => `sciaf.lev.marcas.${id}`;
const claveOficinas = (idPredio: number) => `sciaf.lev.cache.oficinas.${idPredio}`;
const claveUuid = (idOficina: number) => `sciaf.lev.uuid.${idOficina}`;

/**
 * Escrituras en serie por clave.
 *
 * Escaneando en ráfaga se dispara una escritura por lectura. Sin esta cadena,
 * dos `set` en vuelo pueden terminar en orden inverso y dejar guardado un mapa
 * de marcas anterior: el activo escaneado seguiría figurando como pendiente y
 * al cerrar se le imputaría como faltante a alguien que sí lo tenía.
 */
const enVuelo = new Map<string, Promise<unknown>>();

function enSerie<T>(clave: string, tarea: () => Promise<T>): Promise<T> {
  const anterior = enVuelo.get(clave) ?? Promise.resolve();
  const siguiente = anterior.then(tarea, tarea);
  enVuelo.set(
    clave,
    siguiente.catch(() => {
      /* un fallo no debe romper la cadena de la clave */
    }),
  );
  return siguiente;
}

export const levantamientoLocal = {
  // ── Recorridos en curso ────────────────────────────────────────────────────
  // Es una lista, no uno solo: el operador puede empezar una oficina, pasar a la
  // de al lado y volver luego a cerrar la primera. Con un único puntero, el
  // recorrido desplazado quedaría abierto en el servidor y sin forma de
  // retomarlo desde el teléfono estando sin señal.

  async punteros(): Promise<PunteroRecorrido[]> {
    return (await almacen.leerJson<PunteroRecorrido[]>(CLAVE_PUNTEROS)) ?? [];
  },

  /** El más reciente: es el que se ofrece continuar al entrar al módulo. */
  async punteroActual(): Promise<PunteroRecorrido | null> {
    const lista = await this.punteros();
    return lista.length > 0 ? lista[0] : null;
  },

  /** Alta o actualización, dejando el recorrido tocado al frente de la lista. */
  guardarPuntero(p: PunteroRecorrido): Promise<void> {
    return enSerie(CLAVE_PUNTEROS, async () => {
      const lista = (await almacen.leerJson<PunteroRecorrido[]>(CLAVE_PUNTEROS)) ?? [];
      const resto = lista.filter((x) => x.idInventario !== p.idInventario);
      await almacen.escribirJson(CLAVE_PUNTEROS, [p, ...resto]);
    });
  },

  borrarPuntero(idInventario: number): Promise<void> {
    return enSerie(CLAVE_PUNTEROS, async () => {
      const lista = (await almacen.leerJson<PunteroRecorrido[]>(CLAVE_PUNTEROS)) ?? [];
      await almacen.escribirJson(
        CLAVE_PUNTEROS,
        lista.filter((x) => x.idInventario !== idInventario),
      );
    });
  },

  // ── uuid de apertura ───────────────────────────────────────────────────────

  /**
   * El uuid con el que se está por abrir una oficina, guardado ANTES de llamar
   * al servidor.
   *
   * Si la respuesta de POST /abrir se pierde —que es lo normal con señal mala— y el
   * operador reintenta, el mismo uuid hace que el servidor devuelva el mismo
   * levantamiento en vez de abrir un segundo sobre la misma oficina.
   */
  uuidPendiente(idOficina: number): Promise<string | null> {
    return almacen.leer(claveUuid(idOficina));
  },

  guardarUuidPendiente(idOficina: number, uuid: string): Promise<void> {
    const clave = claveUuid(idOficina);
    return enSerie(clave, () => almacen.escribir(clave, uuid));
  },

  borrarUuidPendiente(idOficina: number): Promise<void> {
    const clave = claveUuid(idOficina);
    return enSerie(clave, () => almacen.borrar(clave));
  },

  // ── Paquete offline ────────────────────────────────────────────────────────

  paquete(id: number): Promise<Levantamiento | null> {
    return almacen.leerJson<Levantamiento>(clavePaquete(id));
  },

  guardarPaquete(p: Levantamiento): Promise<void> {
    const clave = clavePaquete(p.idInventario);
    return enSerie(clave, () => almacen.escribirJson(clave, p));
  },

  // ── Marcas ─────────────────────────────────────────────────────────────────

  async marcas(id: number): Promise<Record<string, MarcaLocal>> {
    return (await almacen.leerJson<Record<string, MarcaLocal>>(claveMarcas(id))) ?? {};
  },

  guardarMarcas(id: number, marcas: Record<string, MarcaLocal>): Promise<void> {
    const clave = claveMarcas(id);
    return enSerie(clave, () => almacen.escribirJson(clave, marcas));
  },

  /**
   * Borra lo guardado de un recorrido terminado.
   *
   * Solo se llama después de que el servidor confirmó el cierre: hasta ese
   * momento el paquete es la única copia del trabajo de campo.
   */
  async olvidar(id: number): Promise<void> {
    await enSerie(clavePaquete(id), () => almacen.borrar(clavePaquete(id)));
    await enSerie(claveMarcas(id), () => almacen.borrar(claveMarcas(id)));
    await this.borrarPuntero(id);
  },

  // ── Catálogo de condiciones ────────────────────────────────────────────────

  estados(): Promise<EstadoActivo[] | null> {
    return almacen.leerJson<EstadoActivo[]>(CLAVE_ESTADOS);
  },

  guardarEstados(estados: EstadoActivo[]): Promise<void> {
    return enSerie(CLAVE_ESTADOS, () => almacen.escribirJson(CLAVE_ESTADOS, estados));
  },

  // ── Caché de navegación ────────────────────────────────────────────────────
  // Predios y oficinas se guardan para que elegir dónde levantar no exija señal.
  // Se muestran siempre con su fecha: un dato viejo presentado como actual es
  // peor que no mostrar nada.

  predios(): Promise<Cacheado<TilePredio[]> | null> {
    return almacen.leerJson<Cacheado<TilePredio[]>>(CLAVE_PREDIOS);
  },

  guardarPredios(datos: TilePredio[]): Promise<void> {
    return enSerie(CLAVE_PREDIOS, () =>
      almacen.escribirJson(CLAVE_PREDIOS, { fecha: new Date().toISOString(), datos }),
    );
  },

  oficinas(idPredio: number): Promise<Cacheado<TileOficina[]> | null> {
    return almacen.leerJson<Cacheado<TileOficina[]>>(claveOficinas(idPredio));
  },

  guardarOficinas(idPredio: number, datos: TileOficina[]): Promise<void> {
    const clave = claveOficinas(idPredio);
    return enSerie(clave, () =>
      almacen.escribirJson(clave, { fecha: new Date().toISOString(), datos }),
    );
  },
};
