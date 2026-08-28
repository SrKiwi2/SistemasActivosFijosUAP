<template>
  <ion-page>
    <ion-header class="ion-no-border">
      <ion-toolbar>
        <ion-buttons slot="start">
          <ion-back-button default-href="/levantamiento" text="" />
        </ion-buttons>
        <ion-title>{{ nombrePredio }}</ion-title>
      </ion-toolbar>
      <ion-toolbar>
        <ion-searchbar
          v-model="filtro"
          placeholder="Buscar oficina"
          :animated="true"
          inputmode="search"
        />
      </ion-toolbar>
    </ion-header>

    <ion-content :fullscreen="true">
      <ion-refresher slot="fixed" @ion-refresh="refrescar">
        <ion-refresher-content />
      </ion-refresher>

      <div class="cuerpo">
        <p v-if="!red.enLinea" class="aviso-cache">
          <ion-icon :icon="cloudOfflineOutline" />
          Sin conexión. Para <strong>iniciar</strong> un recorrido hace falta señal;
          uno ya abierto se puede continuar.
        </p>

        <p v-if="error" class="aviso-error">{{ error }}</p>

        <div v-if="cargando && !oficinas.length" class="centrado">
          <ion-spinner name="crescent" />
        </div>

        <p v-else-if="!visibles.length && !error" class="texto-suave centrado">
          {{ filtro ? 'Ninguna oficina coincide con la búsqueda.' : 'Este predio no tiene oficinas con activos.' }}
        </p>

        <article v-for="o in visibles" :key="o.idOficina" class="sciaf-card oficina">
          <div class="encabezado">
            <div class="titulo">
              <strong>{{ o.nombre || 'Oficina ' + o.idOficina }}</strong>
              <span v-if="o.codOfi != null" class="texto-suave">N.º {{ o.codOfi }}</span>
            </div>
            <estado-control-chip :estado="o.estadoControl" />
          </div>

          <div class="conteos">
            <span><b>{{ o.activos }}</b> activos</span>
            <span><b>{{ o.responsables }}</b> responsables</span>
            <span v-if="o.faltantesAbiertos" class="alerta">
              <b>{{ o.faltantesAbiertos }}</b> faltantes
            </span>
          </div>

          <!-- Barra solo mientras hay un recorrido vivo: fuera de eso el
               porcentaje del último levantamiento se confundiría con el actual. -->
          <div v-if="o.idLevantamientoEnCurso" class="avance">
            <div class="barra"><span :style="{ width: o.porcentajeAvance + '%' }"></span></div>
            <span class="texto-suave">
              {{ o.ultimoEncontrados ?? 0 }} de {{ o.ultimoEsperados ?? o.activos }} encontrados
            </span>
          </div>

          <p v-else-if="o.ultimoLevantamiento" class="texto-suave ultimo">
            Último recorrido: {{ fechaCorta(o.ultimoLevantamiento) }}
          </p>

          <ion-button
            expand="block"
            :fill="o.idLevantamientoEnCurso ? 'solid' : 'outline'"
            :color="o.idLevantamientoEnCurso ? 'warning' : 'primary'"
            :disabled="abriendo === o.idOficina || (!red.enLinea && !o.idLevantamientoEnCurso)"
            @click="entrar(o)"
          >
            <ion-spinner v-if="abriendo === o.idOficina" name="crescent" />
            <template v-else>
              <ion-icon slot="start" :icon="o.idLevantamientoEnCurso ? playForwardOutline : clipboardOutline" />
              {{ o.idLevantamientoEnCurso ? 'Continuar recorrido' : 'Iniciar recorrido' }}
            </template>
          </ion-button>
        </article>
      </div>
    </ion-content>
  </ion-page>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import {
  IonPage, IonHeader, IonToolbar, IonTitle, IonContent, IonButtons, IonButton,
  IonBackButton, IonIcon, IonSpinner, IonSearchbar, IonRefresher,
  IonRefresherContent, alertController,
} from '@ionic/vue';
import { cloudOfflineOutline, clipboardOutline, playForwardOutline } from 'ionicons/icons';
import { levantamientoApi, type TileOficina } from '@/services/levantamiento';
import { levantamientoLocal } from '@/services/levantamientoLocal';
import { mensajeDeError } from '@/services/http';
import { useLevantamientoStore } from '@/stores/levantamiento';
import { useRedStore } from '@/stores/red';
import EstadoControlChip from '@/components/EstadoControlChip.vue';

const route = useRoute();
const router = useRouter();
const red = useRedStore();
const lev = useLevantamientoStore();

const idPredio = Number(route.params.idPredio);
const oficinas = ref<TileOficina[]>([]);
const filtro = ref('');
const cargando = ref(false);
const abriendo = ref<number | null>(null);
const error = ref<string | null>(null);

const nombrePredio = computed(() => oficinas.value[0]?.predio || 'Oficinas');

const visibles = computed(() => {
  const t = filtro.value.trim().toLowerCase();
  if (!t) return oficinas.value;
  return oficinas.value.filter(
    (o) => (o.nombre ?? '').toLowerCase().includes(t) || String(o.codOfi ?? '').includes(t),
  );
});

onMounted(cargar);

async function cargar() {
  cargando.value = true;
  error.value = null;

  const guardado = await levantamientoLocal.oficinas(idPredio);
  if (guardado) oficinas.value = guardado.datos;

  try {
    oficinas.value = await levantamientoApi.oficinas(idPredio);
    await levantamientoLocal.guardarOficinas(idPredio, oficinas.value);
  } catch (e) {
    if (!guardado) error.value = mensajeDeError(e, 'No se pudieron cargar las oficinas');
  } finally {
    cargando.value = false;
  }
}

async function refrescar(evento: CustomEvent) {
  await cargar();
  (evento.target as HTMLIonRefresherElement).complete();
}

async function entrar(o: TileOficina) {
  if (o.idLevantamientoEnCurso) {
    router.push(`/levantamiento/${o.idLevantamientoEnCurso}`);
    return;
  }

  abriendo.value = o.idOficina;
  try {
    const id = await lev.abrir(o);
    router.push(`/levantamiento/${id}`);
  } catch {
    const alerta = await alertController.create({
      header: 'No se pudo abrir',
      message: lev.error ?? 'Intente nuevamente.',
      buttons: ['Entendido'],
    });
    await alerta.present();
  } finally {
    abriendo.value = null;
  }
}

function fechaCorta(iso: string | null): string {
  if (!iso) return '';
  const f = new Date(iso);
  if (Number.isNaN(f.getTime())) return '';
  return f.toLocaleDateString('es-BO', { day: '2-digit', month: '2-digit', year: 'numeric' });
}
</script>

<style scoped>
.cuerpo {
  padding: 4px 16px 32px;
}

.oficina {
  margin-bottom: 12px;
}

.encabezado {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
}

.titulo {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.titulo strong {
  font-size: 1rem;
  line-height: 1.25;
}

.titulo span {
  font-size: 0.72rem;
}

.conteos {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  margin-top: 8px;
  font-size: 0.78rem;
  color: var(--sciaf-texto-suave);
}

.conteos b {
  color: var(--ion-text-color);
  font-weight: 700;
}

.conteos .alerta,
.conteos .alerta b {
  color: var(--ion-color-danger);
}

.avance {
  margin-top: 10px;
}

.barra {
  height: 6px;
  border-radius: 999px;
  background: var(--sciaf-borde);
  overflow: hidden;
}

.barra span {
  display: block;
  height: 100%;
  background: var(--ion-color-warning);
}

.avance .texto-suave {
  display: block;
  margin-top: 4px;
  font-size: 0.72rem;
}

.ultimo {
  margin: 8px 0 0;
  font-size: 0.72rem;
}

ion-button {
  margin-top: 12px;
}

.aviso-cache,
.aviso-error {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0 0 12px;
  padding: 10px 12px;
  border-radius: 12px;
  font-size: 0.8rem;
  line-height: 1.4;
}

.aviso-cache {
  color: var(--ion-color-warning);
  background: rgba(247, 144, 9, 0.12);
}

.aviso-error {
  color: var(--ion-color-danger);
  background: rgba(217, 45, 32, 0.12);
}

.centrado {
  display: grid;
  place-items: center;
  padding: 32px 0;
}
</style>
