import { Preferences } from '@capacitor/preferences';

/**
 * Almacenamiento persistente de la app.
 *
 * Toda la persistencia pasa por aquí a propósito: hoy usa `@capacitor/preferences`
 * (SharedPreferences en Android, privado a la app) y en la fase de endurecimiento
 * se cambia por almacenamiento cifrado con Keystore tocando solo este archivo.
 */
export const almacen = {
  async leer(clave: string): Promise<string | null> {
    const { value } = await Preferences.get({ key: clave });
    return value ?? null;
  },

  async escribir(clave: string, valor: string): Promise<void> {
    await Preferences.set({ key: clave, value: valor });
  },

  async borrar(clave: string): Promise<void> {
    await Preferences.remove({ key: clave });
  },

  async leerJson<T>(clave: string): Promise<T | null> {
    const crudo = await this.leer(clave);
    if (!crudo) return null;
    try {
      return JSON.parse(crudo) as T;
    } catch {
      // Un valor corrupto no debe impedir que la app arranque.
      await this.borrar(clave);
      return null;
    }
  },

  async escribirJson(clave: string, valor: unknown): Promise<void> {
    await this.escribir(clave, JSON.stringify(valor));
  },
};
