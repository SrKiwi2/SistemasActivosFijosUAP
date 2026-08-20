import axios, {
  AxiosError,
  type AxiosInstance,
  type InternalAxiosRequestConfig,
} from 'axios';
import { sesion } from './sesion';

/**
 * Cliente HTTP contra el backend SCIAF.
 *
 * En producción apunta a https://sciaf.uap.edu.bo (VITE_API_BASE).
 * En desarrollo queda vacío y lo resuelve el proxy de Vite, así no hace falta
 * pelear con CORS mientras se programa.
 */
export const API_BASE = import.meta.env.VITE_API_BASE ?? '';

/** Timeout corto a propósito: con señal mala es mejor fallar y reintentar que colgar la UI. */
const TIMEOUT_MS = 15000;

export const http: AxiosInstance = axios.create({
  baseURL: `${API_BASE}/api/movil`,
  timeout: TIMEOUT_MS,
  headers: { 'Content-Type': 'application/json' },
});

/** Cliente sin interceptores: lo usa el propio refresh para no morderse la cola. */
const httpDesnudo: AxiosInstance = axios.create({
  baseURL: `${API_BASE}/api/movil`,
  timeout: TIMEOUT_MS,
  headers: { 'Content-Type': 'application/json' },
});

// ── Petición: adjunta el token ────────────────────────────────────────────────

http.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = sesion.accessToken;
  if (token) {
    config.headers.set('Authorization', `Bearer ${token}`);
  }
  return config;
});

// ── Respuesta: renueva la sesión en silencio ante un 401 ──────────────────────

/**
 * Un único refresh en vuelo. Si cinco peticiones reciben 401 a la vez, todas
 * esperan al mismo refresh en lugar de disparar cinco rotaciones de token (que
 * se invalidarían entre sí y terminarían echando al usuario).
 */
let refrescoEnCurso: Promise<string | null> | null = null;

async function renovarSesion(): Promise<string | null> {
  const refreshToken = sesion.refreshToken;
  if (!refreshToken) return null;

  try {
    const { data } = await httpDesnudo.post('/auth/refresh', { refreshToken });
    await sesion.actualizarTokens(data.accessToken, data.refreshToken, data.expiraEn);
    return data.accessToken as string;
  } catch {
    // Refresh rechazado = sesión revocada o usuario dado de baja. Es el único
    // caso en que se cierra sesión sin que el usuario lo pida.
    await sesion.limpiar();
    sesion.notificarExpiracion();
    return null;
  }
}

/**
 * Red de seguridad: la API solo habla JSON (o PDF).
 *
 * Si la app apunta a una URL equivocada, el servidor local de Capacitor
 * responde `index.html` con **200** a cualquier ruta desconocida. Sin esta
 * comprobación, cada llamada parecería exitosa con un cuerpo sin sentido —que
 * es justo como un login falso llegaría a darse por bueno—. Mejor un error
 * explícito que apunte a la causa real.
 */
function rechazarSiEsHtml(respuesta: { headers: unknown; config: { url?: string } }) {
  const tipo = String(
    (respuesta.headers as Record<string, string> | undefined)?.['content-type'] ?? '',
  );
  if (tipo.includes('text/html')) {
    throw new Error(
      'El servidor configurado no responde a la API. Revise la dirección del servidor.',
    );
  }
  return respuesta;
}

http.interceptors.response.use(
  (respuesta) => rechazarSiEsHtml(respuesta) as typeof respuesta,
  async (error: AxiosError) => {
    const original = error.config as InternalAxiosRequestConfig & { _reintentado?: boolean };

    const esNoAutorizado = error.response?.status === 401;
    const esRutaDeAuth = original?.url?.includes('/auth/login') || original?.url?.includes('/auth/refresh');

    if (esNoAutorizado && original && !original._reintentado && !esRutaDeAuth && sesion.refreshToken) {
      original._reintentado = true;

      refrescoEnCurso = refrescoEnCurso ?? renovarSesion();
      const nuevoToken = await refrescoEnCurso;
      refrescoEnCurso = null;

      if (nuevoToken) {
        original.headers.set('Authorization', `Bearer ${nuevoToken}`);
        return http.request(original);
      }
    }

    return Promise.reject(error);
  },
);

/** Mensaje presentable de un error de red o de negocio. */
export function mensajeDeError(error: unknown, porDefecto = 'No se pudo completar la operación'): string {
  if (axios.isAxiosError(error)) {
    const cuerpo = error.response?.data as { mensaje?: string } | undefined;
    if (cuerpo?.mensaje) return cuerpo.mensaje;
    if (error.code === 'ECONNABORTED') return 'El servidor tardó demasiado en responder';
    if (!error.response) return 'Sin conexión con el servidor';
  }
  if (error instanceof Error && error.message) return error.message;
  return porDefecto;
}

export { httpDesnudo };
