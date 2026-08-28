<template>
  <ion-page>
    <ion-header class="ion-no-border">
      <ion-toolbar>
        <ion-buttons slot="start">
          <ion-back-button default-href="/levantamiento" text="" />
        </ion-buttons>
        <ion-title>Mis levantamientos</ion-title>
      </ion-toolbar>
    </ion-header>

    <ion-content :fullscreen="true">
      <ion-refresher slot="fixed" @ion-refresh="refrescar">
        <ion-refresher-content />
      </ion-refresher>

      <div class="cuerpo">
        <p v-if="error" class="aviso-error">{{ error }}</p>

        <div v-if="cargando && !lista.length" class="centrado">
          <ion-spinner name="crescent" />
        </div>

        <p v-else-if="!lista.length && !error" class="texto-suave centrado">
          Todavía no realizó ningún levantamiento.
        </p>

        <button
          v-for="l in lista"
          :key="l.idInventario"
          class="sciaf-card recorrido"
          :class="l.estado === 'EN_EJECUCION' ? 'abierto' : 'cerrado'"
          @click="entrar(l)"
        >
          <div class="encabezado">
            <div class="titulo">
              <strong>{{ l.oficina || 'Oficina ' + l.idOficina }}</strong>
              <span class="numero">{{ l.numeroInventario }}</span>
            </div>
            <span class="estado">{{ l.estado === 'EN_EJECUCION' ? 'En curso' : 'Cerrado' }}</span>
          </div>

          <p class="texto-suave predio">{{ l.predio }} · {{ fechaCorta(l.fechaInicio) }}</p>

          <div class="conteos">
            <span><b>{{ l.totalEncontrados }}</b> de {{ l.totalEsperados }} encontrados</span>
            <span v-if="l.totalFaltantes" class="alerta"><b>{{ l.totalFaltantes }}</b> faltantes</span>
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
  IonPage, IonHeader, IonToolbar, IonTitle, IonContent, IonButtons,
  IonBackButton, IonSpinner, IonRefresher, IonRefresherContent,
} from '@ionic/vue';
import { levantamientoApi, type Levantamiento } from '@/services/levantamiento';
import { mensajeDeError } from '@/services/http';

const router = useRouter();

const lista = ref<Levantamiento[]>([]);
const cargando = ref(false);
const error = ref<string | null>(null);

onMounted(cargar);

async function cargar() {
  cargando.value = true;
  error.value = null;
  try {
    lista.value = await levantamientoApi.mios();
  } catch (e) {
    error.value = mensajeDeError(e, 'No se pudo cargar el historial');
  } finally {
    cargando.value = false;
  }
}

async function refrescar(evento: CustomEvent) {
  await cargar();
  (evento.target as HTMLIonRefresherElement).complete();
}

function entrar(l: Levantamiento) {
  // Uno cerrado ya no se recorre: se va directo a su comprobante.
  const destino =
    l.estado === 'EN_EJECUCION'
      ? `/levantamiento/${l.idInventario}`
      : `/levantamiento/${l.idInventario}/resumen`;
  router.push(destino);
}

function fechaCorta(iso: string | null): string {
  if (!iso) return '';
  const f = new Date(iso);
  if (Number.isNaN(f.getTime())) return '';
  return f.toLocaleString('es-BO', {
    day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit',
  });
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
  border-left: 4px solid transparent;
  margin-bottom: 10px;
  color: var(--ion-text-color);
}

.recorrido.abierto { border-left-color: var(--ion-color-warning); }
.recorrido.cerrado { border-left-color: var(--ion-color-success); }

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
  font-size: 0.98rem;
  line-height: 1.25;
}

.numero {
  font-family: 'Roboto Mono', monospace;
  font-size: 0.7rem;
  color: var(--sciaf-texto-suave);
}

.estado {
  flex: 0 0 auto;
  font-size: 0.68rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  padding: 3px 9px;
  border-radius: 999px;
}

.abierto .estado { color: var(--ion-color-warning); background: rgba(247, 144, 9, 0.16); }
.cerrado .estado { color: var(--ion-color-success); background: rgba(18, 183, 106, 0.14); }

.predio {
  margin: 6px 0 0;
  font-size: 0.75rem;
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

.centrado {
  display: grid;
  place-items: center;
  padding: 40px 0;
  text-align: center;
}

.aviso-error {
  margin: 0 0 12px;
  padding: 10px 12px;
  border-radius: 12px;
  font-size: 0.8rem;
  color: var(--ion-color-danger);
  background: rgba(217, 45, 32, 0.12);
}
</style>
