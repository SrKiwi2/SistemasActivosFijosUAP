import { http } from './http';

export interface Referencia {
  codigo: string | null;
  nombre: string | null;
}

export interface ActivoFicha {
  idActivo: number;
  codigo: string;
  codigoVisual: string;
  descripcion: string;
  estado: string | null;
  estadoFisico: string | null;
  costo: number | null;
  depreciacionAcum: number | null;
  vidaUtil: number | null;
  fechaAdquisicion: string | null;
  observaciones: string | null;
  grupoContable: Referencia | null;
  auxiliar: Referencia | null;
  organismoFinanciero: Referencia | null;
  ubicacion: {
    idOficina: number | null;
    oficina: string | null;
    predio: string | null;
    predioCodigo: string | null;
    unidad: string | null;
    ciudad: string | null;
    municipio: string | null;
    municipioCodigo: string | null;
    entidad: string | null;
    entidadSigla: string | null;
    entidadCodigo: string | null;
  } | null;
  responsable: {
    idResponsable: number | null;
    nombre: string | null;
    cargo: string | null;
    ci: string | null;
  } | null;
  fechaUltimaModificacion: string | null;
  usuarioUltimaModificacion: string | null;
}

export interface Discrepancia {
  capa: 1 | 2 | 3;
  campo: string;
  etiqueta: string;
  valorQr: string | null;
  valorSistema: string | null;
  severidad: 'INFO' | 'AVISO' | 'ERROR';
}

export type Veredicto =
  | 'OK'
  | 'ETIQUETA_DESACTUALIZADA'
  | 'REUBICADO'
  | 'REVISAR_ESTADO'
  | 'NO_ENCONTRADO'
  | 'OTRA_ENTIDAD'
  | 'ILEGIBLE'
  | 'VARIOS_CANDIDATOS';

export interface ResultadoEscaneo {
  codigoDetectado: string | null;
  codigoVisual: string | null;
  prefijoEntidad: string | null;
  entidadValida: boolean;
  veredicto: Veredicto;
  mensaje: string;
  activo: ActivoFicha | null;
  discrepancias: Discrepancia[];
  candidatos: ActivoFicha[];
}

export interface EventoHistorial {
  idHistorial: number;
  tipoEvento: string;
  fecha: string;
  descripcion: string | null;
  oficinaAnterior: string | null;
  oficinaNueva: string | null;
  responsableAnterior: string | null;
  responsableNuevo: string | null;
  usuario: string | null;
}

export interface TransferenciaResumen {
  idTransferencia: number | null;
  numero: string | null;
  tipo: string | null;
  fecha: string | null;
  estado: string | null;
  oficinaOrigen: string | null;
  oficinaDestino: string | null;
  responsableAnterior: string | null;
  documentoReferencia: string | null;
}

export interface AsignacionResumen {
  idAsignacion: number | null;
  numero: string | null;
  codigoCompleto: string | null;
  fecha: string | null;
  tipo: string | null;
  estado: string | null;
  responsable: string | null;
  oficinaDestino: string | null;
  observacion: string | null;
}

export interface MantenimientoResumen {
  idMantenimiento: number;
  tipo: string | null;
  fecha: string | null;
  responsableTecnico: string | null;
  problema: string | null;
  solucion: string | null;
  costo: number | null;
  proximaFecha: string | null;
  numeroTicket: string | null;
}

export interface ActivoDetalle {
  ficha: ActivoFicha;
  historial: EventoHistorial[];
  transferencias: TransferenciaResumen[];
  asignaciones: AsignacionResumen[];
  mantenimientos: MantenimientoResumen[];
}

export const activosApi = {
  /** Verifica una etiqueta leída con la cámara. */
  verificarEscaneo(payload: string) {
    return http
      .post<ResultadoEscaneo>('/escaneo/verificar', { payload, origen: 'CAMARA' })
      .then((r) => r.data);
  },

  /** Verifica un código tecleado (acepta con y sin prefijo, con y sin guiones). */
  verificarManual(codigo: string) {
    return http
      .post<ResultadoEscaneo>('/escaneo/verificar', { payload: codigo, origen: 'MANUAL' })
      .then((r) => r.data);
  },

  detalle(codigo: string) {
    return http.get<ActivoDetalle>(`/activos/${encodeURIComponent(codigo)}/detalle`)
      .then((r) => r.data);
  },

  ficha(codigo: string) {
    return http.get<ActivoFicha>(`/activos/${encodeURIComponent(codigo)}`).then((r) => r.data);
  },

  /** Resuelve varios códigos de una vez (captura offline al recuperar red). */
  lote(codigos: string[]) {
    return http
      .post<{ activos: ActivoFicha[]; noEncontrados: string[] }>('/activos/lote', { codigos })
      .then((r) => r.data);
  },
};
