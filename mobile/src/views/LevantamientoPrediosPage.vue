<template>
  <ion-page>
    <ion-header class="ion-no-border">
      <ion-toolbar>
        <ion-buttons slot="start">
          <ion-back-button default-href="/tabs/inicio" text="" />
        </ion-buttons>
        <ion-title>Levantamiento</ion-title>
        <ion-buttons slot="end">
          <ion-button @click="router.push('/levantamiento/mios')">
            <ion-icon slot="icon-only" :icon="timeOutline" />
          </ion-button>
        </ion-buttons>
      </ion-toolbar>
    </ion-header>

    <ion-content :fullscreen="true">
      <ion-refresher slot="fixed" @ion-refresh="refrescar">
        <ion-refresher-content />
      </ion-refresher>

      <div class="cuerpo">
        <!-- Lo primero que tiene que ver quien dejó un recorrido a medias -->
        <template v-if="lev.punteros.length">
          <p class="titulo-seccion">Recorridos abiertos en este teléfono</p>
          <button
            v-for="p in lev.punteros"
            :key="p.idInventario"
            class="sciaf-card en-curso"
            @click="continuar(p.idInventario)"
          >
            <div class="texto">
              <strong>{{ p.oficina || 'Oficina' }}</strong>
              <span class="texto-suave">{{ p.predio }} · abierto {{ fechaCorta(p.abiertoEn) }}</span>
            </div>
            <span class="continuar">Continuar</span>
          </button>
        </template>

        <p class="titulo-seccion">Predios</p>

        <p v-if="!red.enLinea && desdeCache" class="aviso-cache">
          <ion-icon :icon="cloudOfflineOutline" />
          Datos guardados del {{ fechaCorta(fechaCache) }}. Para iniciar un recorrido
          nuevo hace falta conexión.
        </p>

        <p v-if="error" class="aviso-error">{{ error }}</p>

        <div v-if="cargando && !predios.length" class="centrado">
          <ion-spinner name="crescent" />
        </div>

        <p v-else-if="!predios.length && !error" class="texto-suave centrado">
          No hay predios con activos para levantar.
        </p>

        <button
          v-for="p in predios"
          :key="p.idPredio"
          class="sciaf-card predio"
          @click="router.push(`/levantamiento/predio/${p.idPredio}`)"
        >
          <div class="encabezado">
            <strong>{{ p.descrip || 'Predio ' + p.idPredio }}</strong>
            <estado-control-chip :estado="p.estadoControl" />
          </div>

          <p class="texto-suave ubicacion">
            {{ [p.unidad, p.ciudad].filter(Boolean).join(' · ') || 'Sin ubicación' }}
          </p>

          <div class="conteos">
            <span><b>{{ p.oficinas }}</b> oficinas</span>
            <span><b>{{ p.activos }}</b> activos</span>
            <span v-if="p.faltantesAbiertos" class="alerta">
              <b>{{ p.faltantesAbiertos }}</b> faltantes
            </span>
          </div>
        </button>
      </div>
    </ion-content>
  </ion-page>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import {
  IonPage, IonHeader, IonToolbar, IonTitle, IonContent, IonButtons, IonButton,
  IonBackButton, IonIcon, IonSpinner, IonRefresher, IonRefresherContent,
} from '@ionic/vue';
import { timeOutline, cloudOfflineOutline } from 'ionicons/icons';
import { levantamientoApi, type TilePredio } from '@/services/levantamiento';
import { levantamientoLocal } from '@/services/levantamientoLocal';
import { mensajeDeError } from '@/services/http';
import { useLevantamientoStore } from '@/stores/levantamiento';
import { useRedStore } from '@/stores/red';
import EstadoControlChip from '@/components/EstadoControlChip.vue';

const router = useRouter();
const red = useRedStore();
const lev = useLevantamientoStore();

const predios = ref<TilePredio[]>([]);
const cargando = ref(false);
const error = ref<string | null>(null);
const desdeCache = ref(false);
const fechaCache = ref<string | null>(null);

onMounted(async () => {
  await lev.cargarPunteros();
  // El catálogo de condiciones se baja acá, con señal, y no en la oficina.
  lev.cargarEstados();
  await cargar();
});

async function cargar() {
  cargando.value = true;
  error.value = null;

  // Primero lo guardado, para que la pantalla no aparezca vacía; después la red.
  const guardado = await levantamientoLocal.predios();
  if (guardado) {
    predios.value = guardado.datos;
    desdeCache.value = true;
    fechaCache.value = guardado.fecha;
  }

  try {
    predios.value = await levantamientoApi.predios();
    desdeCache.value = false;
    await levantamientoLocal.guardarPredios(predios.value);
  } catch (e) {
    if (!guardado) error.value = mensajeDeError(e, 'No se pudieron cargar los predios');
  } finally {
    cargando.value = false;
  }
}

async function refrescar(evento: CustomEvent) {
  await cargar();
  (evento.target as HTMLIonRefresherElement).complete();
}

function continuar(idInventario: number) {
  router.push(`/levantamiento/${idInventario}`);
}

function fechaCorta(iso: string | null): string {
  if (!iso) return '';
  const f = new Date(iso);
  if (Number.isNaN(f.getTime())) return '';
  return f.toLocaleString('es-BO', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' });
}
</script>

<style scoped>
.cuerpo {
  padding: 4px 16px 32px;
}

button.sciaf-card {
  display: block;
  width: 100%;
  text-align: left;
  border: none;
  margin-bottom: 10px;
  color: var(--ion-text-color);
}

.en-curso {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  border-left: 4px solid var(--ion-color-warning);
}

.en-curso .texto {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.en-curso .continuar {
  flex: 0 0 auto;
  font-size: 0.8rem;
  font-weight: 700;
  color: var(--ion-color-primary);
}

.encabezado {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.encabezado strong {
  font-size: 1rem;
  line-height: 1.25;
}

.ubicacion {
  margin: 4px 0 0;
  font-size: 0.78rem;
}

.conteos {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  margin-top: 10px;
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
