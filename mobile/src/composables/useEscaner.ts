import { ref, onUnmounted } from 'vue';
import { Capacitor } from '@capacitor/core';
import { Haptics, ImpactStyle, NotificationType } from '@capacitor/haptics';
import type { PluginListenerHandle } from '@capacitor/core';
import {
  BarcodeScanner,
  BarcodeFormat,
  LensFacing,
  type Barcode,
} from '@capacitor-mlkit/barcode-scanning';

/**
 * Lector de QR sobre ML Kit.
 *
 * Decisiones que importan para el uso en campo:
 *
 * - **La cámara se dibuja DETRÁS del WebView.** Hay que volver la página
 *   transparente mientras se escanea y devolverla a su color al salir; si se
 *   olvida, el usuario ve una pantalla en blanco y cree que la app se colgó.
 * - **Deduplicación por ventana de tiempo.** ML Kit dispara muchas lecturas por
 *   segundo sobre la misma etiqueta; sin filtro, un solo activo generaría
 *   decenas de consultas.
 * - **Vibración en cada lectura.** En campo se mira la etiqueta, no la pantalla:
 *   el háptico es la confirmación real de que se leyó.
 */
export function useEscaner() {
  const activo = ref(false);
  const linterna = ref(false);
  const error = ref<string | null>(null);
  const soportado = ref(true);

  let listener: PluginListenerHandle | null = null;
  let ultimoTexto = '';
  let ultimoInstante = 0;

  /** Ventana de silencio para el mismo código, en milisegundos. */
  const VENTANA_REPETICION = 3000;

  function transparentar(encender: boolean) {
    document.documentElement.classList.toggle('escaner-activo', encender);
    document.body.classList.toggle('escaner-activo', encender);
  }

  async function asegurarPermiso(): Promise<boolean> {
    const { camera } = await BarcodeScanner.checkPermissions();
    if (camera === 'granted' || camera === 'limited') return true;

    const pedido = await BarcodeScanner.requestPermissions();
    if (pedido.camera === 'granted' || pedido.camera === 'limited') return true;

    error.value = 'Se necesita permiso de cámara para escanear. Actívelo en los ajustes del teléfono.';
    return false;
  }

  /**
   * En algunos teléfonos el módulo de escaneo de Google se descarga aparte.
   * Se pide antes de empezar para no fallar con un error opaco en pleno uso.
   */
  async function asegurarModulo(): Promise<void> {
    try {
      const { available } = await BarcodeScanner.isGoogleBarcodeScannerModuleAvailable();
      if (!available) {
        await BarcodeScanner.installGoogleBarcodeScannerModule();
      }
    } catch {
      /* si no está disponible la comprobación, se intenta escanear igual */
    }
  }

  /**
   * @param alLeer se invoca con el texto crudo de cada lectura nueva
   */
  async function iniciar(alLeer: (texto: string) => void): Promise<void> {
    error.value = null;

    if (!Capacitor.isNativePlatform()) {
      soportado.value = false;
      error.value = 'El escáner solo funciona en la aplicación instalada en el teléfono.';
      return;
    }

    const { supported } = await BarcodeScanner.isSupported();
    if (!supported) {
      soportado.value = false;
      error.value = 'Este dispositivo no permite escanear códigos.';
      return;
    }

    if (!(await asegurarPermiso())) return;
    await asegurarModulo();

    listener = await BarcodeScanner.addListener('barcodeScanned', async (evento) => {
      const codigo: Barcode = evento.barcode;
      const texto = codigo.rawValue ?? '';
      if (!texto) return;

      const ahora = Date.now();
      if (texto === ultimoTexto && ahora - ultimoInstante < VENTANA_REPETICION) return;

      ultimoTexto = texto;
      ultimoInstante = ahora;

      await Haptics.impact({ style: ImpactStyle.Medium }).catch(() => {});
      alLeer(texto);
    });

    transparentar(true);

    await BarcodeScanner.startScan({
      formats: [BarcodeFormat.QrCode],
      lensFacing: LensFacing.Back,
    });

    activo.value = true;
  }

  async function detener(): Promise<void> {
    if (listener) {
      await listener.remove();
      listener = null;
    }
    if (activo.value) {
      await BarcodeScanner.stopScan().catch(() => {});
      activo.value = false;
    }
    if (linterna.value) {
      await BarcodeScanner.disableTorch().catch(() => {});
      linterna.value = false;
    }
    transparentar(false);
  }

  async function alternarLinterna(): Promise<void> {
    try {
      await BarcodeScanner.toggleTorch();
      linterna.value = !linterna.value;
    } catch {
      error.value = 'Este dispositivo no tiene linterna disponible.';
    }
  }

  /** Permite volver a leer el mismo código de inmediato (botón "escanear otro"). */
  function olvidarUltimo(): void {
    ultimoTexto = '';
    ultimoInstante = 0;
  }

  async function vibrarExito() {
    await Haptics.notification({ type: NotificationType.Success }).catch(() => {});
  }

  async function vibrarError() {
    await Haptics.notification({ type: NotificationType.Error }).catch(() => {});
  }

  // Salir de la pantalla sin apagar la cámara dejaría el teléfono grabando y la
  // interfaz transparente: se apaga siempre.
  onUnmounted(() => {
    detener();
  });

  return {
    activo,
    linterna,
    error,
    soportado,
    iniciar,
    detener,
    alternarLinterna,
    olvidarUltimo,
    vibrarExito,
    vibrarError,
  };
}
