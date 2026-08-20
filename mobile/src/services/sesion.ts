import { almacen } from './almacen';

export interface UsuarioMovil {
  idUsuario: number;
  usuario: string;
  nombreCompleto: string;
  rol: string;
  permisos: string[];
}

export interface Sesion {
  accessToken: string;
  refreshToken: string;
  expiraEn: number;
  usuario: UsuarioMovil;
}

const CLAVE_SESION = 'sciaf.sesion';
const CLAVE_DEVICE = 'sciaf.deviceId';

/**
 * Sesión en curso, en memoria y en disco.
 *
 * Se guarda el refresh token (que no caduca por tiempo) para que el usuario
 * quede "logueado siempre" entre aperturas de la app. La sesión solo termina
 * cuando el usuario cierra sesión o el servidor revoca el dispositivo.
 */
let actual: Sesion | null = null;

/** Suscriptores a la expiración definitiva de la sesión (el router redirige al login). */
const oyentesExpiracion: Array<() => void> = [];

export const sesion = {
  get(): Sesion | null {
    return actual;
  },

  get accessToken(): string | null {
    return actual?.accessToken ?? null;
  },

  get refreshToken(): string | null {
    return actual?.refreshToken ?? null;
  },

  /** Restaura la sesión guardada al arrancar la app. */
  async restaurar(): Promise<Sesion | null> {
    actual = await almacen.leerJson<Sesion>(CLAVE_SESION);
    return actual;
  },

  async guardar(nueva: Sesion): Promise<void> {
    actual = nueva;
    await almacen.escribirJson(CLAVE_SESION, nueva);
  },

  /** Actualiza solo los tokens (tras un refresh) conservando el perfil. */
  async actualizarTokens(accessToken: string, refreshToken: string, expiraEn: number): Promise<void> {
    if (!actual) return;
    actual = { ...actual, accessToken, refreshToken, expiraEn };
    await almacen.escribirJson(CLAVE_SESION, actual);
  },

  async limpiar(): Promise<void> {
    actual = null;
    await almacen.borrar(CLAVE_SESION);
  },

  alExpirar(oyente: () => void): void {
    oyentesExpiracion.push(oyente);
  },

  notificarExpiracion(): void {
    oyentesExpiracion.forEach((o) => o());
  },

  /**
   * Identificador estable del dispositivo. Se genera una vez y se conserva:
   * permite reutilizar la fila de `dispositivo_movil` en cada login en lugar de
   * acumular una por sesión, y es lo que se revoca al perder el teléfono.
   */
  async deviceId(): Promise<string> {
    let id = await almacen.leer(CLAVE_DEVICE);
    if (!id) {
      id = crypto.randomUUID();
      await almacen.escribir(CLAVE_DEVICE, id);
    }
    return id;
  },
};
