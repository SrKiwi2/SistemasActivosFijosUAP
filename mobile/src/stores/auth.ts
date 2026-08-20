import { defineStore } from 'pinia';
import { Device } from '@capacitor/device';
import { App } from '@capacitor/app';
import { Capacitor } from '@capacitor/core';
import { http, mensajeDeError } from '@/services/http';
import { sesion, type UsuarioMovil } from '@/services/sesion';

interface EstadoAuth {
  usuario: UsuarioMovil | null;
  /** true mientras se restaura la sesión guardada al arrancar. */
  iniciando: boolean;
  cargando: boolean;
  error: string | null;
}

export const useAuthStore = defineStore('auth', {
  state: (): EstadoAuth => ({
    usuario: null,
    iniciando: true,
    cargando: false,
    error: null,
  }),

  getters: {
    /**
     * Ojo con la comparación: `usuario !== null` daría `true` para `undefined`,
     * y bastaría una respuesta rara del servidor para colar a cualquiera. Se
     * exige un identificador real.
     */
    autenticado: (s) => Boolean(s.usuario?.idUsuario),

    esAdministrador: (s) => {
      const rol = (s.usuario?.rol ?? '').toUpperCase();
      return rol === 'ADMINISTRADOR' || rol === 'SUPER USUARIO';
    },

    permisos: (s): string[] => s.usuario?.permisos ?? [],
  },

  actions: {
    /** Comprueba un código de permiso (`MOV_*`) para pintar u ocultar opciones. */
    puede(codigo: string): boolean {
      return this.permisos.includes(codigo);
    },

    /**
     * Arranque de la app: restaura la sesión guardada.
     *
     * Si hay sesión, se entra de inmediato y el perfil se revalida en segundo
     * plano. Sin red se entra igual: bloquear la entrada por no poder validar el
     * token dejaría la app inservible justo donde más falta hace, en campo.
     */
    async restaurar(): Promise<void> {
      this.iniciando = true;
      try {
        const guardada = await sesion.restaurar();
        if (guardada && esSesionValida(guardada)) {
          this.usuario = guardada.usuario;
          this.refrescarPerfil().catch(() => {
            /* sin red: se sigue con el perfil cacheado */
          });
        } else if (guardada) {
          // Sesión guardada incompleta (versión anterior de la app o respuesta
          // inválida): se descarta en vez de arrastrar un estado a medias.
          await sesion.limpiar();
        }
      } finally {
        this.iniciando = false;
      }
    },

    async login(usuario: string, contrasena: string): Promise<boolean> {
      this.cargando = true;
      this.error = null;

      try {
        const { data } = await http.post('/auth/login', {
          usuario: usuario.trim(),
          contrasena,
          deviceId: await sesion.deviceId(),
          plataforma: Capacitor.getPlatform(),
          modelo: await modeloDelDispositivo(),
          appVersion: await versionDeLaApp(),
        });

        // Un 200 no basta: solo se abre sesión si la respuesta trae realmente
        // tokens y usuario. Si la app apunta a una URL equivocada, el servidor
        // local de Capacitor devuelve index.html con 200 y sin esta comprobación
        // entraría cualquiera con cualquier contraseña.
        if (!esSesionValida(data)) {
          this.error = 'El servidor no devolvió una sesión válida. Revise la dirección configurada.';
          return false;
        }

        await sesion.guardar(data);
        this.usuario = data.usuario;
        return true;
      } catch (e) {
        this.error = mensajeDeError(e, 'No se pudo iniciar sesión');
        return false;
      } finally {
        this.cargando = false;
      }
    },

    /** Perfil y permisos vigentes desde el servidor (el rol pudo cambiar). */
    async refrescarPerfil(): Promise<void> {
      const { data } = await http.get('/auth/me');
      this.usuario = data;
      const s = sesion.get();
      if (s) await sesion.guardar({ ...s, usuario: data });
    },

    async logout(): Promise<void> {
      const refreshToken = sesion.refreshToken;
      try {
        if (refreshToken) await http.post('/auth/logout', { refreshToken });
      } catch {
        // Sin red no se puede avisar al servidor; la sesión local se cierra igual.
      } finally {
        await sesion.limpiar();
        this.usuario = null;
      }
    },
  },
});

/** Una sesión solo es válida si trae los dos tokens y un usuario identificable. */
function esSesionValida(data: unknown): boolean {
  const s = data as Partial<{ accessToken: string; refreshToken: string; usuario: UsuarioMovil }>;
  return (
    typeof s?.accessToken === 'string' && s.accessToken.length > 0 &&
    typeof s?.refreshToken === 'string' && s.refreshToken.length > 0 &&
    typeof s?.usuario?.idUsuario === 'number'
  );
}

async function modeloDelDispositivo(): Promise<string> {
  try {
    const info = await Device.getInfo();
    return `${info.manufacturer ?? ''} ${info.model ?? ''}`.trim();
  } catch {
    return 'desconocido';
  }
}

async function versionDeLaApp(): Promise<string> {
  try {
    const info = await App.getInfo();
    return info.version;
  } catch {
    return '1.0.0'; // en navegador App.getInfo() no está disponible
  }
}
