import { defineStore } from 'pinia';
import axios from 'axios';
import { mensajeDeError } from '@/services/http';
import {
  ahoraDelDispositivo, claveCodigo, levantamientoApi,
  type EstadoActivo, type Levantamiento, type MarcaEnvio, type OrigenMarca,
  type ResumenCierre, type Situacion, type TileOficina,
} from '@/services/levantamiento';
import {
  levantamientoLocal, type MarcaLocal, type PunteroRecorrido,
} from '@/services/levantamientoLocal';
import { leerEtiqueta } from '@/services/qr';

/**
 * Orden de las marcas dentro de esta ejecución de la app. Se reanuda desde lo
 * guardado al cargar un recorrido para que no se repitan números tras reabrir.
 */
let contadorMarca = 0;

/** Una línea del recorrido tal como se pinta: lo esperado más lo marcado en campo. */
export interface FilaRecorrido {
  clave: string;
  idDetalle: number | null;
  idActivo: number | null;
  codigo: string;
  descripcion: string | null;
  responsable: string | null;
  situacion: Situacion;
  origen: OrigenMarca | null;
  fecha: string | null;
  observacion: string | null;
  idEstadoObservado: number | null;
  estadoObservado: string | null;
  /** Apareció algo que no estaba en la lista esperada de esta oficina. */
  sobrante: boolean;
  /** Marcado en el teléfono y todavía sin confirmar por el servidor. */
  sinEnviar: boolean;
}

export type ResultadoLectura =
  | { tipo: 'MARCADO'; fila: FilaRecorrido; mensaje: string }
  | { tipo: 'YA_MARCADO'; fila: FilaRecorrido; mensaje: string }
  | { tipo: 'SOBRANTE'; fila: FilaRecorrido; mensaje: string }
  | { tipo: 'ILEGIBLE'; mensaje: string };

interface EstadoLevantamiento {
  paquete: Levantamiento | null;
  /** Marcas hechas en este teléfono, por clave. Es a la vez estado y cola de salida. */
  marcas: Record<string, MarcaLocal>;
  uuidCliente: string | null;
  estados: EstadoActivo[];
  punteros: PunteroRecorrido[];
  cargando: boolean;
  sincronizando: boolean;
  error: string | null;
  ultimoCierre: ResumenCierre | null;
}

/**
 * El recorrido en curso.
 *
 * Dos reglas gobiernan todo lo que hay aquí:
 *
 * 1. **Se marca solo lo que se encuentra** (modo por ausencia). Lo que quede
 *    PENDIENTE al cerrar se imputa como faltante a su responsable.
 * 2. **Primero el teléfono, después el servidor.** Cada marca se guarda en disco
 *    antes de intentar enviarla; la red es un detalle de sincronización, no la
 *    condición para trabajar.
 */
export const useLevantamientoStore = defineStore('levantamiento', {
  state: (): EstadoLevantamiento => ({
    paquete: null,
    marcas: {},
    uuidCliente: null,
    estados: [],
    punteros: [],
    cargando: false,
    sincronizando: false,
    error: null,
    ultimoCierre: null,
  }),

  getters: {
    /**
     * La lista esperada con las marcas locales aplicadas encima.
     *
     * La marca local manda sobre la situación que trajo el paquete: puede ser
     * más nueva que lo que el servidor conoce, y es justo el trabajo que
     * todavía no se pudo enviar.
     */
    filas(s): FilaRecorrido[] {
      const esperadas = (s.paquete?.detalle ?? []).map((d): FilaRecorrido => {
        const m = s.marcas[`det:${d.idDetalle}`];
        return {
          clave: `det:${d.idDetalle}`,
          idDetalle: d.idDetalle,
          idActivo: d.idActivo,
          codigo: d.codigo,
          descripcion: d.descripcion,
          responsable: d.responsable,
          situacion: m?.situacion ?? d.situacion,
          origen: m?.origen ?? d.origenMarca,
          fecha: m?.fecha ?? d.fechaMarca,
          observacion: m?.observacion ?? d.observacion,
          idEstadoObservado: m?.idEstadoObservado ?? d.idEstadoObservado,
          estadoObservado: nombreEstado(s.estados, m?.idEstadoObservado) ?? d.estadoObservado,
          sobrante: false,
          sinEnviar: m ? !m.enviada : false,
        };
      });

      // Los sobrantes no existen en el paquete: solo viven como marca local
      // hasta que el servidor los convierte en hallazgo.
      const sobrantes = Object.values(s.marcas)
        .filter((m) => m.idDetalle == null && m.codigo)
        .map((m): FilaRecorrido => ({
          clave: m.clave,
          idDetalle: null,
          idActivo: null,
          codigo: m.codigo as string,
          descripcion: 'No figura en esta oficina',
          responsable: null,
          situacion: m.situacion,
          origen: m.origen,
          fecha: m.fecha,
          observacion: m.observacion,
          idEstadoObservado: m.idEstadoObservado,
          estadoObservado: nombreEstado(s.estados, m.idEstadoObservado),
          sobrante: true,
          sinEnviar: !m.enviada,
        }));

      return [...esperadas, ...sobrantes];
    },

    /** Índice por código para resolver una lectura sin consultar al servidor. */
    indicePorCodigo(): Map<string, FilaRecorrido> {
      const indice = new Map<string, FilaRecorrido>();
      for (const f of this.filas) {
        const clave = claveCodigo(f.codigo);
        if (clave && !indice.has(clave)) indice.set(clave, f);
      }
      return indice;
    },

    esperados(s): number {
      return s.paquete?.detalle.length ?? 0;
    },

    encontrados(): number {
      return this.filas.filter((f) => !f.sobrante && f.situacion === 'ENCONTRADO').length;
    },

    pendientes(): number {
      return this.filas.filter((f) => !f.sobrante && f.situacion === 'PENDIENTE').length;
    },

    conNovedad(): number {
      return this.filas.filter((f) => f.situacion === 'ENCONTRADO' && tieneNovedad(f)).length;
    },

    sobrantes(): number {
      return this.filas.filter((f) => f.sobrante).length;
    },

    porcentaje(): number {
      return this.esperados === 0 ? 0 : Math.round((this.encontrados * 100) / this.esperados);
    },

    /** Marcas guardadas que el servidor todavía no confirmó. */
    cola(s): MarcaLocal[] {
      return Object.values(s.marcas).filter((m) => !m.enviada);
    },

    abierto(s): boolean {
      return s.paquete?.estado === 'EN_EJECUCION';
    },
  },

  actions: {
    // ── Catálogos y estado local ─────────────────────────────────────────────

    async cargarPunteros(): Promise<void> {
      this.punteros = await levantamientoLocal.punteros();
    },

    /**
     * Catálogo de condiciones. Se sirve de la caché al instante y se refresca si
     * hay red: sin él no se puede anotar "roto" estando sin señal.
     */
    async cargarEstados(): Promise<void> {
      const guardados = await levantamientoLocal.estados();
      if (guardados?.length) this.estados = guardados;

      try {
        const frescos = await levantamientoApi.estados();
        this.estados = frescos;
        await levantamientoLocal.guardarEstados(frescos);
      } catch {
        /* sin red se sigue con el catálogo guardado */
      }
    },

    // ── Apertura y retoma ────────────────────────────────────────────────────

    /**
     * Abre (o retoma) el recorrido de una oficina y deja el paquete offline en
     * disco.
     *
     * El uuid se guarda ANTES de llamar. Si la respuesta se pierde, el reintento
     * viaja con el mismo uuid y el servidor devuelve el levantamiento que ya
     * había abierto en vez de crear un segundo sobre la misma oficina.
     */
    async abrir(oficina: TileOficina): Promise<number> {
      this.cargando = true;
      this.error = null;
      try {
        const uuid = (await levantamientoLocal.uuidPendiente(oficina.idOficina)) ?? crearUuid();
        await levantamientoLocal.guardarUuidPendiente(oficina.idOficina, uuid);

        const paquete = await levantamientoApi.abrir(oficina.idOficina, uuid);

        this.paquete = paquete;
        this.uuidCliente = uuid;
        this.marcas = adoptar(await levantamientoLocal.marcas(paquete.idInventario));

        await levantamientoLocal.guardarPaquete(paquete);
        await levantamientoLocal.guardarPuntero({
          idInventario: paquete.idInventario,
          uuidCliente: uuid,
          idOficina: paquete.idOficina,
          oficina: paquete.oficina,
          predio: paquete.predio,
          abiertoEn: paquete.fechaInicio ?? ahoraDelDispositivo(),
        });
        await levantamientoLocal.borrarUuidPendiente(oficina.idOficina);
        await this.cargarPunteros();

        return paquete.idInventario;
      } catch (e) {
        this.error = mensajeDeError(e, 'No se pudo abrir el levantamiento');
        throw e;
      } finally {
        this.cargando = false;
      }
    },

    /**
     * Carga un recorrido ya abierto. Primero de disco —para poder trabajar en un
     * sótano sin señal— y solo si no hay nada guardado se recurre a la red.
     */
    async retomar(idInventario: number): Promise<void> {
      if (this.paquete?.idInventario === idInventario) return;

      this.cargando = true;
      this.error = null;
      try {
        const guardado = await levantamientoLocal.paquete(idInventario);
        this.marcas = adoptar(await levantamientoLocal.marcas(idInventario));

        const punteros = await levantamientoLocal.punteros();
        this.uuidCliente =
          punteros.find((p) => p.idInventario === idInventario)?.uuidCliente ?? null;

        if (guardado) {
          this.paquete = guardado;
        } else {
          // Sin caché no hay alternativa a la red: teléfono nuevo, reinstalación
          // o levantamiento abierto desde la web.
          const paquete = await levantamientoApi.paquete(idInventario);
          this.paquete = paquete;
          await levantamientoLocal.guardarPaquete(paquete);
        }
      } catch (e) {
        this.error = mensajeDeError(e, 'No se pudo cargar el levantamiento');
        throw e;
      } finally {
        this.cargando = false;
      }
    },

    /** Trae del servidor lo que se marcó desde la web o desde otro teléfono. */
    async refrescarPaquete(): Promise<void> {
      if (!this.paquete) return;
      const paquete = await levantamientoApi.paquete(this.paquete.idInventario);
      this.paquete = paquete;
      await levantamientoLocal.guardarPaquete(paquete);
    },

    // ── Marcado ──────────────────────────────────────────────────────────────

    /** Guarda una marca en disco y la deja en la cola de salida. */
    async marcar(
      fila: FilaRecorrido,
      situacion: Situacion,
      origen: OrigenMarca,
      novedad?: { observacion: string | null; idEstadoObservado: number | null },
    ): Promise<void> {
      if (!this.paquete) return;

      const previa = this.marcas[fila.clave];
      const marca: MarcaLocal = {
        clave: fila.clave,
        idDetalle: fila.idDetalle,
        codigo: fila.idDetalle == null ? fila.codigo : null,
        situacion,
        origen,
        fecha: ahoraDelDispositivo(),
        // Una novedad ya anotada no se pierde por volver a tocar la fila.
        observacion: novedad ? novedad.observacion : previa?.observacion ?? fila.observacion,
        idEstadoObservado: novedad
          ? novedad.idEstadoObservado
          : previa?.idEstadoObservado ?? fila.idEstadoObservado,
        seq: ++contadorMarca,
        enviada: false,
      };

      this.marcas = { ...this.marcas, [fila.clave]: marca };
      await levantamientoLocal.guardarMarcas(this.paquete.idInventario, this.marcas);

      // Se intenta enviar en el acto, pero sin bloquear: la marca ya está a salvo.
      void this.sincronizar();
    },

    /** Tocar la fila: encontrado ⇄ sin revisar. */
    alternar(fila: FilaRecorrido, origen: OrigenMarca = 'MANUAL'): Promise<void> {
      const siguiente: Situacion = fila.situacion === 'ENCONTRADO' ? 'PENDIENTE' : 'ENCONTRADO';
      return this.marcar(fila, siguiente, origen);
    },

    /**
     * Novedad de condición sobre un activo que sí apareció ("está roto", "le
     * falta una rueda"). Anotarla lo da además por encontrado: si el operador
     * pudo describir su estado, es porque lo tuvo delante.
     */
    anotarNovedad(
      fila: FilaRecorrido,
      observacion: string | null,
      idEstadoObservado: number | null,
    ): Promise<void> {
      return this.marcar(fila, 'ENCONTRADO', fila.origen ?? 'MANUAL', {
        observacion,
        idEstadoObservado,
      });
    },

    /**
     * Resuelve una lectura contra la lista esperada, en el propio teléfono.
     *
     * No consulta al servidor a propósito: el recorrido tiene que funcionar sin
     * señal, y la respuesta tiene que ser inmediata porque en campo se mira la
     * etiqueta, no la pantalla.
     */
    async registrarLectura(textoCrudo: string, origen: OrigenMarca): Promise<ResultadoLectura> {
      const lectura = leerEtiqueta(textoCrudo);
      if (!lectura.legible || !lectura.codigo) {
        return { tipo: 'ILEGIBLE', mensaje: 'No se reconoce un código de activo en la etiqueta' };
      }

      const fila = this.indicePorCodigo.get(claveCodigo(lectura.codigo));

      if (fila) {
        if (fila.situacion === 'ENCONTRADO') {
          return { tipo: 'YA_MARCADO', fila, mensaje: 'Ya estaba marcado en este recorrido' };
        }
        await this.marcar(fila, 'ENCONTRADO', origen);
        return {
          tipo: 'MARCADO',
          fila: this.filas.find((f) => f.clave === fila.clave) ?? fila,
          mensaje: 'Encontrado',
        };
      }

      // No estaba en la lista de esta oficina: se registra igual. Un activo que
      // está donde no debería es tan hallazgo como uno que falta, y decidirlo
      // acá —sin la base delante— sería adivinar.
      const clave = `cod:${claveCodigo(lectura.codigo)}`;
      const yaRegistrado = this.marcas[clave] != null;
      const sobrante: FilaRecorrido = {
        clave,
        idDetalle: null,
        idActivo: null,
        // Sin el prefijo de entidad: es como el servidor busca el activo.
        codigo: lectura.codigo,
        descripcion: 'No figura en esta oficina',
        responsable: null,
        situacion: 'ENCONTRADO',
        origen,
        fecha: null,
        observacion: null,
        idEstadoObservado: null,
        estadoObservado: null,
        sobrante: true,
        sinEnviar: true,
      };

      if (!yaRegistrado) await this.marcar(sobrante, 'ENCONTRADO', origen);

      const actualizada = this.filas.find((f) => f.clave === clave) ?? sobrante;
      return yaRegistrado
        ? { tipo: 'YA_MARCADO', fila: actualizada, mensaje: 'Ya registrado como sobrante' }
        : { tipo: 'SOBRANTE', fila: actualizada, mensaje: 'No pertenece a esta oficina' };
    },

    // ── Sincronización ───────────────────────────────────────────────────────

    /**
     * Envía la cola en un lote.
     *
     * Reenviar es seguro: el servidor descarta una marca si ya tiene otra más
     * nueva para ese activo, y por eso cada marca viaja con la hora del
     * dispositivo y no con la del envío.
     *
     * @param silencioso sin red no se molesta al usuario; el cierre, en cambio,
     *        necesita que un fallo se propague.
     */
    async sincronizar(silencioso = true): Promise<boolean> {
      if (!this.paquete || this.sincronizando) return false;

      const lote = this.cola;
      if (lote.length === 0) return true;

      this.sincronizando = true;
      try {
        const resumen = await levantamientoApi.marcas(
          this.paquete.idInventario,
          this.uuidCliente,
          lote.map(aEnvio),
        );

        // Solo se da por enviada la marca que se envió: si el operador volvió a
        // tocar esa fila mientras el lote viajaba, la nueva sigue en la cola.
        for (const enviada of lote) {
          const guardada = this.marcas[enviada.clave];
          if (guardada && guardada.seq === enviada.seq) guardada.enviada = true;
        }
        await levantamientoLocal.guardarMarcas(this.paquete.idInventario, this.marcas);

        // `ignoradas` no es un error: el servidor ya tenía algo igual o más nuevo.
        if (resumen.ok) this.error = null;
        return true;
      } catch (e) {
        const sinRed = axios.isAxiosError(e) && !e.response;
        const mensaje = mensajeDeError(e, 'No se pudieron enviar las marcas');

        // Quedarse sin señal en medio del recorrido es lo esperable y no se
        // anuncia. Que el servidor RECHACE el lote es distinto: esa cola no va a
        // drenar sola, así que el error queda a la vista junto con la salida de
        // emergencia. Se registra ANTES de propagar, porque el caso que más lo
        // necesita —cerraron el recorrido desde la web mientras este teléfono
        // tenía marcas sin enviar— aparece justamente al intentar cerrar.
        if (!sinRed) this.error = mensaje;

        if (!silencioso) throw new Error(mensaje);
        return false;
      } finally {
        this.sincronizando = false;
      }
    },

    // ── Cierre ───────────────────────────────────────────────────────────────

    /**
     * Cierra el recorrido: lo que quedó pendiente pasa a faltante y se le imputa
     * a su responsable.
     *
     * La cola tiene que estar vacía antes. Cerrar con marcas sin enviar
     * convertiría en faltante un activo que el operador sí encontró y se lo
     * cargaría a una persona, así que el envío va primero y un fallo aborta el
     * cierre en lugar de seguir adelante.
     */
    async cerrar(observ: string | null): Promise<ResumenCierre> {
      if (!this.paquete) throw new Error('No hay un recorrido cargado');

      await this.sincronizar(false);

      const resumen = await levantamientoApi.cerrar(
        this.paquete.idInventario,
        this.uuidCliente,
        observ,
      );

      this.ultimoCierre = resumen;
      await levantamientoLocal.olvidar(this.paquete.idInventario);
      await this.cargarPunteros();
      this.paquete = null;
      this.marcas = {};
      this.uuidCliente = null;

      return resumen;
    },

    /**
     * Borra del teléfono un recorrido sin cerrarlo en el servidor.
     *
     * Salida de emergencia para cuando la cola no puede enviarse nunca —el
     * levantamiento se cerró desde la web, por ejemplo— y el teléfono queda
     * atascado reintentando. No toca nada en el servidor.
     */
    async descartar(idInventario: number): Promise<void> {
      await levantamientoLocal.olvidar(idInventario);
      if (this.paquete?.idInventario === idInventario) this.salir();
      await this.cargarPunteros();
    },

    /** Suelta el recorrido de memoria. Lo guardado en disco no se toca. */
    salir(): void {
      this.paquete = null;
      this.marcas = {};
      this.uuidCliente = null;
      this.error = null;
    },
  },
});

/**
 * Retoma el contador de orden desde lo que ya estaba guardado.
 *
 * Sin esto, al reabrir la app el contador arrancaría en cero y una marca nueva
 * podría llevar el mismo número que una vieja de la misma fila.
 */
function adoptar(marcas: Record<string, MarcaLocal>): Record<string, MarcaLocal> {
  for (const m of Object.values(marcas)) {
    if ((m.seq ?? 0) > contadorMarca) contadorMarca = m.seq;
  }
  return marcas;
}

function aEnvio(m: MarcaLocal): MarcaEnvio {
  const comun = {
    situacion: m.situacion,
    origen: m.origen,
    fecha: m.fecha,
    observacion: m.observacion,
    idEstadoObservado: m.idEstadoObservado,
  };
  return m.idDetalle != null
    ? { idDetalle: m.idDetalle, ...comun }
    : { codigo: m.codigo ?? undefined, ...comun };
}

function nombreEstado(estados: EstadoActivo[], id: number | null | undefined): string | null {
  if (id == null) return null;
  return estados.find((e) => e.id === id)?.nombre ?? null;
}

export function tieneNovedad(f: FilaRecorrido): boolean {
  return Boolean(f.observacion?.trim()) || f.idEstadoObservado != null;
}

/** `crypto.randomUUID` no existe en contextos no seguros; el respaldo alcanza. */
function crearUuid(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID();
  }
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}
