<template>
  <ion-page>
    <ion-header class="ion-no-border">
      <ion-toolbar>
        <ion-buttons slot="start">
          <ion-back-button default-href="/levantamiento" text="" />
        </ion-buttons>
        <ion-title>
          <span class="titulo-oficina">{{ lev.paquete?.oficina || 'Recorrido' }}</span>
          <span class="subtitulo">{{ lev.paquete?.numeroInventario }}</span>
        </ion-title>
        <ion-buttons slot="end">
          <ion-button :disabled="lev.sincronizando || !red.enLinea" @click="sincronizarAhora">
            <ion-spinner v-if="lev.sincronizando" name="crescent" />
            <ion-icon v-else slot="icon-only" :icon="syncOutline" />
          </ion-button>
        </ion-buttons>
      </ion-toolbar>
    </ion-header>

    <ion-content :fullscreen="true">
      <!-- Marcador del recorrido -->
      <section class="marcador">
        <div class="cifras">
          <span class="grande">{{ lev.encontrados }}</span>
          <span class="de">de {{ lev.esperados }}</span>
        </div>

        <div class="barra"><span :style="{ width: lev.porcentaje + '%' }"></span></div>

        <div class="chips">
          <span class="chip pendiente">{{ lev.pendientes }} sin revisar</span>
          <span v-if="lev.conNovedad" class="chip novedad">{{ lev.conNovedad }} con novedad</span>
          <span v-if="lev.sobrantes" class="chip sobrante">{{ lev.sobrantes }} sobrantes</span>
          <span v-if="lev.cola.length" class="chip cola">{{ lev.cola.length }} sin enviar</span>
          <span v-else-if="red.enLinea" class="chip aldia">Todo enviado</span>
          <span v-if="!red.enLinea" class="chip offline">Sin conexión</span>
        </div>
      </section>

      <p v-if="lev.error" class="aviso-error">
        {{ lev.error }}
        <button class="descartar" @click="confirmarDescarte">Descartar recorrido</button>
      </p>

      <p v-if="!lev.abierto && lev.paquete" class="aviso-cerrado">
        <ion-icon :icon="lockClosedOutline" />
        Este recorrido ya está cerrado. Solo se puede consultar.
      </p>

      <ion-segment v-model="segmento" class="segmento">
        <ion-segment-button value="PENDIENTE">
          <ion-label>Sin revisar</ion-label>
        </ion-segment-button>
        <ion-segment-button value="ENCONTRADO">
          <ion-label>Encontrados</ion-label>
        </ion-segment-button>
        <ion-segment-button value="TODOS">
          <ion-label>Todos</ion-label>
        </ion-segment-button>
      </ion-segment>

      <ion-searchbar
        v-model="filtro"
        placeholder="Código o descripción"
        :animated="true"
        class="buscador"
      />

      <div class="lista">
        <div v-if="lev.cargando && !lev.paquete" class="centrado">
          <ion-spinner name="crescent" />
        </div>

        <p v-else-if="!visibles.length" class="texto-suave centrado">
          {{ mensajeVacio }}
        </p>

        <fila-levantamiento
          v-for="f in pagina"
          :key="f.clave"
          :fila="f"
          @alternar="alternar"
          @novedad="abrirNovedad"
        />

        <ion-infinite-scroll :disabled="pagina.length >= visibles.length" @ion-infinite="masFilas">
          <ion-infinite-scroll-content loading-spinner="crescent" />
        </ion-infinite-scroll>
      </div>
    </ion-content>

    <!-- Escanear es la acción principal: pulgar derecho, siempre visible -->
    <ion-fab v-if="lev.abierto" slot="fixed" vertical="bottom" horizontal="end" class="fab-escaner">
      <ion-fab-button @click="router.push(`/levantamiento/${idInventario}/escanear`)">
        <ion-icon :icon="qrCodeOutline" />
      </ion-fab-button>
    </ion-fab>

    <ion-footer v-if="lev.abierto" class="ion-no-border">
      <ion-toolbar>
        <ion-button expand="block" class="cerrar" :disabled="cerrando" @click="confirmarCierre">
          <ion-spinner v-if="cerrando" name="crescent" />
          <template v-else>
            <ion-icon slot="start" :icon="flagOutline" />
            Cerrar recorrido
          </template>
        </ion-button>
      </ion-toolbar>
    </ion-footer>

    <hoja-novedad
      :abierta="novedadAbierta"
      :fila="filaNovedad"
      :estados="lev.estados"
      @cerrar="novedadAbierta = false"
      @guardar="guardarNovedad"
    />
  </ion-page>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import {
  IonPage, IonHeader, IonToolbar, IonTitle, IonContent, IonFooter, IonButtons,
  IonButton, IonBackButton, IonIcon, IonSpinner, IonSearchbar, IonSegment,
  IonSegmentButton, IonLabel, IonFab, IonFabButton, IonInfiniteScroll,
  IonInfiniteScrollContent, alertController, onIonViewWillEnter,
} from '@ionic/vue';
import {
  syncOutline, qrCodeOutline, flagOutline, lockClosedOutline,
} from 'ionicons/icons';
import { claveCodigo } from '@/services/levantamiento';
import { useLevantamientoStore, type FilaRecorrido } from '@/stores/levantamiento';
import { useRedStore } from '@/stores/red';
import FilaLevantamiento from '@/components/FilaLevantamiento.vue';
import HojaNovedad from '@/components/HojaNovedad.vue';

const route = useRoute();
const router = useRouter();
const red = useRedStore();
const lev = useLevantamientoStore();

const idInventario = Number(route.params.id);

/** Cuántas filas se pintan de una vez: una oficina puede tener cientos. */
const PAGINA = 60;

const segmento = ref<'PENDIENTE' | 'ENCONTRADO' | 'TODOS'>('PENDIENTE');
const filtro = ref('');
const limite = ref(PAGINA);
const cerrando = ref(false);
const novedadAbierta = ref(false);
const filaNovedad = ref<FilaRecorrido | null>(null);

const visibles = computed(() => {
  const texto = filtro.value.trim().toLowerCase();
  const clave = claveCodigo(filtro.value);

  return lev.filas.filter((f) => {
    if (segmento.value === 'PENDIENTE' && f.situacion !== 'PENDIENTE') return false;
    if (segmento.value === 'ENCONTRADO' && f.situacion !== 'ENCONTRADO') return false;
    if (!texto) return true;
    return (
      (f.descripcion ?? '').toLowerCase().includes(texto) ||
      f.codigo.toLowerCase().includes(texto) ||
      (clave !== '' && claveCodigo(f.codigo).includes(clave)) ||
      (f.responsable ?? '').toLowerCase().includes(texto)
    );
  });
});

const pagina = computed(() => visibles.value.slice(0, limite.value));

const mensajeVacio = computed(() => {
  if (filtro.value) return 'Ningún activo coincide con la búsqueda.';
  if (segmento.value === 'PENDIENTE') return 'No queda nada sin revisar en esta oficina.';
  if (segmento.value === 'ENCONTRADO') return 'Todavía no se marcó ningún activo.';
  return 'La oficina no tiene activos registrados.';
});

// Volver al principio al cambiar de pestaña o de búsqueda: seguir en la página 5
// de un listado que ya no es el mismo desorienta.
watch([segmento, filtro], () => {
  limite.value = PAGINA;
});

onMounted(async () => {
  lev.cargarEstados();
  try {
    await lev.retomar(idInventario);
  } catch {
    return; // el error ya quedó en el store y se pinta arriba
  }
  await ponerseAlDia();
});

// Al volver del escáner conviene reintentar: pudo recuperarse la señal mientras
// se recorría el ambiente.
onIonViewWillEnter(() => {
  if (lev.paquete && red.enLinea) void lev.sincronizar();
});

// La red vuelve sola en mitad de un pasillo: la cola se va sin que nadie la empuje.
watch(
  () => red.enLinea,
  (enLinea) => {
    if (enLinea && lev.paquete) void ponerseAlDia();
  },
);

async function ponerseAlDia() {
  if (!red.enLinea) return;
  const enviado = await lev.sincronizar();
  // El paquete se refresca DESPUÉS de enviar, para que lo que traiga el servidor
  // ya incluya el trabajo de este teléfono y no lo pise por un instante.
  if (enviado) await lev.refrescarPaquete().catch(() => {});
}

async function sincronizarAhora() {
  await ponerseAlDia();
}

function masFilas(evento: CustomEvent) {
  limite.value += PAGINA;
  (evento.target as HTMLIonInfiniteScrollElement).complete();
}

function alternar(f: FilaRecorrido) {
  if (!lev.abierto) return;
  void lev.alternar(f, 'MANUAL');
}

function abrirNovedad(f: FilaRecorrido) {
  if (!lev.abierto) return;
  filaNovedad.value = f;
  novedadAbierta.value = true;
}

async function guardarNovedad(datos: { observacion: string | null; idEstadoObservado: number | null }) {
  if (filaNovedad.value) {
    await lev.anotarNovedad(filaNovedad.value, datos.observacion, datos.idEstadoObservado);
  }
  novedadAbierta.value = false;
}

/**
 * Cierre del recorrido.
 *
 * Se avisa con el número exacto de faltantes porque es la consecuencia real: no
 * "quedan sin revisar", se le imputan a una persona. Y se exige que la cola esté
 * enviada antes —lo hace `lev.cerrar`—, porque cerrar con marcas en el teléfono
 * convertiría en faltante justo lo que el operador sí encontró.
 */
async function confirmarCierre() {
  if (lev.cola.length && !red.enLinea) {
    const aviso = await alertController.create({
      header: 'Faltan marcas por enviar',
      message:
        `Hay ${lev.cola.length} marca(s) guardadas en el teléfono que todavía no llegaron ` +
        'al servidor. Si se cerrara ahora, esos activos quedarían como faltantes. ' +
        'Conéctese a una red y vuelva a intentar.',
      buttons: ['Entendido'],
    });
    await aviso.present();
    return;
  }

  const alerta = await alertController.create({
    header: 'Cerrar el recorrido',
    message:
      `Se encontraron ${lev.encontrados} de ${lev.esperados} activos. ` +
      `Los ${lev.pendientes} sin revisar quedarán como FALTANTES a nombre de su ` +
      'responsable. Esta acción no se deshace.',
    inputs: [
      {
        name: 'observ',
        type: 'textarea',
        placeholder: 'Observación del recorrido (opcional)',
        attributes: { maxlength: 500 },
      },
    ],
    buttons: [
      { text: 'Seguir revisando', role: 'cancel' },
      {
        text: 'Cerrar',
        role: 'destructive',
        handler: (datos: { observ?: string }) => {
          void cerrar(datos?.observ?.trim() || null);
        },
      },
    ],
  });
  await alerta.present();
}

async function cerrar(observ: string | null) {
  cerrando.value = true;
  try {
    const resumen = await lev.cerrar(observ);
    router.replace(`/levantamiento/${resumen.idInventario}/resumen`);
  } catch (e) {
    const fallo = await alertController.create({
      header: 'No se pudo cerrar',
      message: e instanceof Error ? e.message : 'Intente nuevamente.',
      buttons: ['Entendido'],
    });
    await fallo.present();
  } finally {
    cerrando.value = false;
  }
}

/**
 * Salida de emergencia: borra el recorrido del teléfono sin cerrarlo.
 *
 * Sirve cuando la cola no puede enviarse nunca —el levantamiento se cerró desde
 * la web mientras se recorría, por ejemplo— y el teléfono queda atascado. No
 * toca nada en el servidor, así que el trabajo enviado hasta ahí se conserva.
 */
async function confirmarDescarte() {
  const alerta = await alertController.create({
    header: 'Descartar en este teléfono',
    message:
      'Se borra la copia local del recorrido, incluidas las marcas que no se pudieron ' +
      'enviar. El levantamiento en el servidor no se modifica.',
    buttons: [
      { text: 'Cancelar', role: 'cancel' },
      {
        text: 'Descartar',
        role: 'destructive',
        handler: () => {
          void lev.descartar(idInventario).then(() => router.replace('/levantamiento'));
        },
      },
    ],
  });
  await alerta.present();
}
</script>

<style scoped>
ion-title {
  display: flex;
  flex-direction: column;
  line-height: 1.15;
}

.titulo-oficina {
  font-size: 1rem;
  font-weight: 700;
}

.subtitulo {
  font-size: 0.68rem;
  font-weight: 400;
  color: var(--sciaf-texto-suave);
  font-family: 'Roboto Mono', monospace;
}

/* ── Marcador ─────────────────────────────────────────────────────────────
 * El número que importa en campo es "cuántos me faltan", y tiene que leerse
 * de un vistazo con el teléfono a media distancia.
 */
.marcador {
  padding: 12px 16px 14px;
  background: var(--ion-card-background);
  border-bottom: 1px solid var(--sciaf-borde);
}

.cifras {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.grande {
  font-size: 2.1rem;
  font-weight: 800;
  line-height: 1;
  color: var(--ion-color-primary);
}

.de {
  font-size: 0.9rem;
  color: var(--sciaf-texto-suave);
}

.barra {
  height: 8px;
  margin-top: 10px;
  border-radius: 999px;
  background: var(--sciaf-borde);
  overflow: hidden;
}

.barra span {
  display: block;
  height: 100%;
  background: var(--ion-color-success);
  transition: width 0.25s ease;
}

.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 10px;
}

.chip {
  font-size: 0.68rem;
  font-weight: 700;
  padding: 3px 9px;
  border-radius: 999px;
  white-space: nowrap;
}

.chip.pendiente { color: var(--ion-color-medium);  background: rgba(107, 114, 128, 0.14); }
.chip.novedad   { color: var(--ion-color-warning); background: rgba(247, 144, 9, 0.16); }
.chip.sobrante  { color: var(--ion-color-warning); background: rgba(247, 144, 9, 0.16); }
.chip.cola      { color: var(--ion-color-danger);  background: rgba(217, 45, 32, 0.13); }
.chip.aldia     { color: var(--ion-color-success); background: rgba(18, 183, 106, 0.13); }
.chip.offline   { color: var(--ion-color-warning); background: rgba(247, 144, 9, 0.16); }

.segmento {
  margin: 12px 16px 0;
  --background: rgba(var(--ion-text-color-rgb), 0.05);
}

.buscador {
  --border-radius: 12px;
  padding: 8px 12px 0;
}

.lista {
  padding: 8px 16px 96px;
}

.centrado {
  display: grid;
  place-items: center;
  padding: 40px 0;
  text-align: center;
}

.aviso-error,
.aviso-cerrado {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin: 12px 16px 0;
  padding: 10px 12px;
  border-radius: 12px;
  font-size: 0.8rem;
  line-height: 1.4;
}

.aviso-error {
  color: var(--ion-color-danger);
  background: rgba(217, 45, 32, 0.12);
}

.aviso-cerrado {
  color: var(--sciaf-texto-suave);
  background: rgba(107, 114, 128, 0.12);
}

.descartar {
  background: none;
  border: none;
  padding: 0;
  font-size: 0.78rem;
  font-weight: 700;
  text-decoration: underline;
  color: var(--ion-color-danger);
}

/* Se sube el FAB para que no tape la última fila ni el botón de cerrar. */
.fab-escaner {
  margin-bottom: 60px;
}

ion-footer ion-toolbar {
  --padding-start: 16px;
  --padding-end: 16px;
  --padding-top: 6px;
  --padding-bottom: 6px;
}

.cerrar {
  --background: var(--sciaf-degradado);
  margin: 0;
}
</style>
