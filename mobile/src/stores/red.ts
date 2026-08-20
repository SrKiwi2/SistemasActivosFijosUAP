import { defineStore } from 'pinia';
import { Network } from '@capacitor/network';

/**
 * Estado de conexión.
 *
 * La app debe decir siempre si lo que muestra viene del servidor o de la caché.
 * Presentar un dato viejo como si fuera actual es peor que no mostrarlo.
 */
export const useRedStore = defineStore('red', {
  state: () => ({
    enLinea: true,
    tipo: 'unknown' as string,
  }),

  actions: {
    async iniciar(): Promise<void> {
      const estado = await Network.getStatus();
      this.enLinea = estado.connected;
      this.tipo = estado.connectionType;

      Network.addListener('networkStatusChange', (s) => {
        this.enLinea = s.connected;
        this.tipo = s.connectionType;
      });
    },
  },
});
