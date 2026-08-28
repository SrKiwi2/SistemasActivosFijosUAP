import { http } from './http';

/**
 * Levantamiento de activos por oficina — `/api/movil/levantamiento/**`.
 *
 * Contrato congelado por el chat `backend` en `docs/HANDOFF_CONTROL_ACTIVOS.md`.
 * Requiere el permiso `MOV_INVENTARIO` (ADMINISTRADOR y SUPER USUARIO pasan siempre).
 *
 * El módulo trabaja **por ausencia**: en campo solo se marca lo que se encuentra,
 * y al cerrar todo lo que quedó `PENDIENTE` se imputa como faltante a su
 * responsable. Ver `stores/levantamiento.ts` para la cola offline.
 */

export type EstadoControl = 'SIN_LEVANTAR' | 'EN_CURSO' | 'CONTROLADO' | 'CON_FALTANTES';
export type Situacion = 'PENDIENTE' | 'ENCONTRADO' | 'FALTANTE';
export type OrigenMarca = 'ESCANEO' | 'MANUAL' | 'WEB';

export interface TilePredio {
  idPredio: number;
  descrip: string | null;
  unidad: string | null;
  ciudad: string | null;
  oficinas: number;
  responsables: number;
  activos: number;
  faltantesAbiertos: number;
  levantamientosEnCurso: number;
  levantamientosTotales: number;
  estadoControl: EstadoControl;
}

export interface TileOficina {
  idOficina: number;
  codOfi: number | null;
  nombre: string | null;
  idPredio: number;
  predio: string | null;
  responsables: number;
  activos: number;
  faltantesAbiertos: number;
  levantamientosTotales: number;
  /** Si no es null, esa oficina ya tiene un recorrido abierto: se continúa, no se inicia. */
  idLevantamientoEnCurso: number | null;
  ultimoLevantamiento: string | null;
  ultimoEncontrados: number | null;
  ultimoEsperados: number | null;
  estadoControl: EstadoControl;
  porcentajeAvance: number;
}

/** Condición para anotar en campo ("roto", "en desuso"…). Va con el paquete offline. */
export interface EstadoActivo {
  id: number;
  nombre: string | null;
  codigo: string | null;
}

export interface DetalleLevantamiento {
  idDetalle: number;
  idActivo: number;
  codigo: string;
  descripcion: string | null;
  idResponsable: number | null;
  responsable: string | null;
  situacion: Situacion;
  origenMarca: OrigenMarca | null;
  fechaMarca: string | null;
  observacion: string | null;
  idEstadoObservado: number | null;
  estadoObservado: string | null;
}

/** Cabecera del recorrido. Con `detalle` cargado es además el paquete offline. */
export interface Levantamiento {
  idInventario: number;
  numeroInventario: string;
  idOficina: number;
  codOfi: number | null;
  oficina: string | null;
  idPredio: number | null;
  predio: string | null;
  estado: 'EN_EJECUCION' | 'COMPLETADO';
  origen: 'WEB' | 'MOVIL';
  fechaInicio: string | null;
  fechaFin: string | null;
  ejecutor: string | null;
  totalEsperados: number;
  totalEncontrados: number;
  totalPendientes: number;
  totalFaltantes: number;
  observ: string | null;
  detalle: DetalleLevantamiento[];
}

/** Una marca tal como viaja al servidor. */
export interface MarcaEnvio {
  /** Preferido: la fila del paquete. */
  idDetalle?: number;
  /** Alternativa cuando se leyó algo que no estaba en la lista → SOBRANTE. */
  codigo?: string;
  situacion: Situacion;
  origen: OrigenMarca;
  /**
   * Hora del **dispositivo**, no del envío. Es lo que vuelve seguro reenviar la
   * cola a ciegas: el servidor descarta una marca si ya tiene otra más nueva.
   */
  fecha: string;
  observacion?: string | null;
  idEstadoObservado?: number | null;
}

export interface ResumenMarcas {
  ok: boolean;
  aplicadas: number;
  /** No es un error: el servidor ya tenía una marca igual o más nueva. */
  ignoradas: number;
  sobrantes: number;
  encontrados: number;
  pendientes: number;
  esperados: number;
}

export interface ResumenCierre {
  ok: boolean;
  idInventario: number;
  numeroInventario: string;
  esperados: number;
  encontrados: number;
  faltantes: number;
  observados: number;
  hallazgosCreados: number;
}

export const levantamientoApi = {
  predios() {
    return http.get<TilePredio[]>('/levantamiento/predios').then((r) => r.data);
  },

  oficinas(idPredio: number) {
    return http
      .get<TileOficina[]>('/levantamiento/oficinas', { params: { idPredio } })
      .then((r) => r.data);
  },

  estados() {
    return http.get<EstadoActivo[]>('/levantamiento/estados').then((r) => r.data);
  },

  /** Abre el recorrido y devuelve el paquete offline completo de la oficina. */
  abrir(idOficina: number, uuidCliente: string, descripcion?: string | null) {
    return http
      .post<Levantamiento>('/levantamiento/abrir', { idOficina, uuidCliente, descripcion: descripcion ?? null })
      .then((r) => r.data);
  },

  /** Re-descarga del paquete: cambio de teléfono, reinstalación o caché perdida. */
  paquete(idInventario: number) {
    return http.get<Levantamiento>(`/levantamiento/${idInventario}/paquete`).then((r) => r.data);
  },

  marcas(idInventario: number, uuidCliente: string | null, marcas: MarcaEnvio[]) {
    return http
      .post<ResumenMarcas>(`/levantamiento/${idInventario}/marcas`, { uuidCliente, marcas })
      .then((r) => r.data);
  },

  cerrar(idInventario: number, uuidCliente: string | null, observ: string | null) {
    return http
      .post<ResumenCierre>(`/levantamiento/${idInventario}/cerrar`, { uuidCliente, observ })
      .then((r) => r.data);
  },

  /** Levantamientos del usuario, más nuevo primero. Vienen con `detalle: []`. */
  mios() {
    return http.get<Levantamiento[]>('/levantamiento/mios').then((r) => r.data);
  },
};

/**
 * Hora local del dispositivo en ISO-8601 **sin zona** (`2026-08-26T09:21:33`),
 * que es lo que espera el `LocalDateTime` del servidor.
 *
 * `toISOString()` no sirve: devuelve UTC con `Z`, y en Bolivia (UTC-4) cada
 * marca llegaría cuatro horas en el futuro. Como la fecha es además el criterio
 * de idempotencia, eso no solo mostraría una hora falsa en el acta: haría que
 * marcas viejas pisaran a las nuevas al reenviar la cola.
 */
export function ahoraDelDispositivo(fecha: Date = new Date()): string {
  const dosDigitos = (n: number) => String(n).padStart(2, '0');
  return (
    `${fecha.getFullYear()}-${dosDigitos(fecha.getMonth() + 1)}-${dosDigitos(fecha.getDate())}` +
    `T${dosDigitos(fecha.getHours())}:${dosDigitos(fecha.getMinutes())}:${dosDigitos(fecha.getSeconds())}`
  );
}

/**
 * Clave de comparación de un código de activo.
 *
 * En el paquete los códigos vienen como los guarda la base (`01-04-02-03609`),
 * pero la etiqueta física lleva el prefijo de entidad (`148-01-04-02-03609`) y
 * el correlativo puede venir con o sin ceros a la izquierda. Comparar el texto
 * crudo dejaría sin marcar activos que sí están en la lista.
 *
 * Se descarta el prefijo y se compara cada tramo por su valor numérico.
 */
export function claveCodigo(codigo: string | null | undefined): string {
  const crudo = (codigo ?? '').trim();

  // Sin un solo dígito no hay código que comparar. Importa: si un texto libre
  // («escritorio») devolviera "0", buscar por descripción traería además todos
  // los activos con un cero en el código, que son casi todos.
  if (!/[0-9]/.test(crudo)) return '';

  const partes = crudo.split('-').filter((p) => p !== '');
  if (partes.length === 0) return '';

  // Cinco tramos = lleva prefijo de entidad; el código de la base tiene cuatro.
  const cuerpo = partes.length >= 5 ? partes.slice(1) : partes;

  return cuerpo.map((p) => String(Number(p.replace(/\D/g, '')) || 0)).join('-');
}
