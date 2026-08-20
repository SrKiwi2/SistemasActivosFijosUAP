<template>
  <ion-page class="pagina-escaner">
    <ion-content :fullscreen="true" class="contenido-escaner">
      <!-- Superposición sobre la imagen de la cámara -->
      <div class="capa">
        <div class="barra-superior">
          <button class="redondo" aria-label="Cerrar" @click="salir">
            <ion-icon :icon="closeOutline" />
          </button>
          <span class="titulo">Apunte al código QR</span>
          <button
            class="redondo"
            :class="{ encendido: escaner.linterna.value }"
            aria-label="Linterna"
            @click="escaner.alternarLinterna()"
          >
            <ion-icon :icon="escaner.linterna.value ? flashlightOutline : flashOffOutline" />
          </button>
        </div>

        <div class="marco">
          <span class="esquina ai"></span>
          <span class="esquina ad"></span>
          <span class="esquina bi"></span>
          <span class="esquina bd"></span>
          <div v-if="consultando" class="buscando">
            <ion-spinner name="crescent" />
            <span>Consultando…</span>
          </div>
        </div>

        <div class="barra-inferior">
          <p v-if="escaner.error.value" class="aviso">{{ escaner.error.value }}</p>
          <ion-button expand="block" fill="solid" color="light" @click="abrirManual">
            <ion-icon slot="start" :icon="keypadOutline" />
            Escribir el código
          </ion-button>
        </div>
      </div>

      <!-- Resultado -->
      <ion-modal
        :is-open="!!resultado"
        :breakpoints="[0, 0.6, 0.95]"
        :initial-breakpoint="0.6"
        @did-dismiss="cerrarResultado"
      >
        <ion-content>
          <resultado-escaneo
            v-if="resultado"
            :resultado="resultado"
            @ver-ficha="verFicha"
            @otro="cerrarResultado"
            @elegir="elegirCandidato"
          />
        </ion-content>
      </ion-modal>

      <!-- Entrada manual -->
      <ion-modal :is-open="manualAbierto" @did-dismiss="cerrarManual">
        <ion-header class="ion-no-border">
          <ion-toolbar>
            <ion-title>Código del activo</ion-title>
            <ion-buttons slot="end">
              <ion-button @click="cerrarManual">Cerrar</ion-button>
            </ion-buttons>
          </ion-toolbar>
        </ion-header>
        <ion-content>
          <form class="form-manual" @submit.prevent="buscarManual">
            <p class="texto-suave">
              Se acepta con o sin el prefijo <strong>148-</strong>, con o sin guiones.
              También el número final solo.
            </p>

            <ion-input
              ref="entradaManual"
              v-model="codigoManual"
              label="Código"
              label-placement="stacked"
              fill="outline"
              inputmode="numeric"
              enterkeyhint="search"
              placeholder="148-01-04-02-03609"
              class="campo"
              @ion-input="alEscribir"
            />

            <ion-button type="submit" expand="block" :disabled="!codigoManual || consultando">
              <ion-spinner v-if="consultando" name="crescent" />
              <span v-else>Buscar</span>
            </ion-button>
          </form>
        </ion-content>
      </ion-modal>
    </ion-content>
  </ion-page>
</template>

<script setup lang="ts">
import { onMounted, onBeforeUnmount, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import {
  IonPage, IonContent, IonIcon, IonButton, IonSpinner, IonModal, IonHeader,
  IonToolbar, IonTitle, IonButtons, IonInput, onIonViewWillLeave,
} from '@ionic/vue';
import {
  closeOutline, flashlightOutline, flashOffOutline, keypadOutline,
} from 'ionicons/icons';
import { useEscaner } from '@/composables/useEscaner';
import { activosApi, type ActivoFicha, type ResultadoEscaneo as Resultado } from '@/services/activos';
import { mensajeDeError } from '@/services/http';
import { formatearCodigoManual } from '@/services/qr';
import ResultadoEscaneo from '@/components/ResultadoEscaneo.vue';

const router = useRouter();
const route = useRoute();
const escaner = useEscaner();

const resultado = ref<Resultado | null>(null);
const consultando = ref(false);
const manualAbierto = ref(false);
const codigoManual = ref('');

onMounted(async () => {
  // Si se entró desde "Escribir el código", se abre el teclado directamente y
  // no se enciende la cámara: hay etiquetas ilegibles y activos sin etiqueta.
  if (route.query.manual === '1') {
    manualAbierto.value = true;
    return;
  }
  await escaner.iniciar(alLeer);
});

onIonViewWillLeave(() => {
  escaner.detener();
});

onBeforeUnmount(() => {
  escaner.detener();
});

async function alLeer(texto: string) {
  if (consultando.value || resultado.value) return;

  consultando.value = true;
  try {
    const r = await activosApi.verificarEscaneo(texto);
    resultado.value = r;
    if (r.veredicto === 'OK') await escaner.vibrarExito();
    else await escaner.vibrarError();
  } catch (e) {
    resultado.value = errorComoResultado(mensajeDeError(e, 'No se pudo consultar el activo'));
    await escaner.vibrarError();
  } finally {
    consultando.value = false;
  }
}

async function buscarManual() {
  if (!codigoManual.value.trim()) return;

  consultando.value = true;
  try {
    const r = await activosApi.verificarManual(codigoManual.value.trim());
    manualAbierto.value = false;
    resultado.value = r;
  } catch (e) {
    resultado.value = errorComoResultado(mensajeDeError(e, 'No se pudo consultar el activo'));
    manualAbierto.value = false;
  } finally {
    consultando.value = false;
  }
}

/** Va poniendo los guiones mientras se escribe, para que se vea como la etiqueta. */
function alEscribir(evento: CustomEvent) {
  const valor = String((evento.target as HTMLInputElement).value ?? '');
  codigoManual.value = formatearCodigoManual(valor);
}

function abrirManual() {
  manualAbierto.value = true;
}

function cerrarManual() {
  manualAbierto.value = false;
  codigoManual.value = '';
  // Si se llegó por el atajo de entrada manual y se cierra sin buscar, no tiene
  // sentido quedarse en una pantalla de cámara apagada.
  if (route.query.manual === '1' && !resultado.value && !escaner.activo.value) {
    router.back();
  }
}

function cerrarResultado() {
  resultado.value = null;
  escaner.olvidarUltimo();
}

function elegirCandidato(activo: ActivoFicha) {
  verFicha(activo.codigo);
}

async function verFicha(codigo: string) {
  await escaner.detener();
  router.push(`/activo/${encodeURIComponent(codigo)}`);
}

async function salir() {
  await escaner.detener();
  router.back();
}

function errorComoResultado(mensaje: string): Resultado {
  return {
    codigoDetectado: null,
    codigoVisual: null,
    prefijoEntidad: null,
    entidadValida: true,
    veredicto: 'ILEGIBLE',
    mensaje,
    activo: null,
    discrepancias: [],
    candidatos: [],
  };
}
</script>

<style scoped>
.capa {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: env(safe-area-inset-top, 16px) 20px 28px;
  pointer-events: none;
}

.capa > * {
  pointer-events: auto;
}

/* ── Barra superior ───────────────────────────────────────────────────────── */
.barra-superior {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding-top: 16px;
}

.titulo {
  color: #fff;
  font-size: 0.85rem;
  font-weight: 600;
  text-shadow: 0 1px 4px rgba(0, 0, 0, 0.6);
}

.redondo {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  border: none;
  background: rgba(0, 0, 0, 0.45);
  color: #fff;
  display: grid;
  place-items: center;
  font-size: 1.3rem;
}

.redondo.encendido {
  background: var(--sciaf-rojo);
}

/* ── Marco guía ───────────────────────────────────────────────────────────── */
.marco {
  position: relative;
  width: min(74vw, 300px);
  aspect-ratio: 1;
  align-self: center;
  display: grid;
  place-items: center;
}

.esquina {
  position: absolute;
  width: 34px;
  height: 34px;
  border: 4px solid #fff;
}

.esquina.ai { top: 0; left: 0;  border-right: none; border-bottom: none; border-top-left-radius: 12px; }
.esquina.ad { top: 0; right: 0; border-left: none;  border-bottom: none; border-top-right-radius: 12px; }
.esquina.bi { bottom: 0; left: 0;  border-right: none; border-top: none; border-bottom-left-radius: 12px; }
.esquina.bd { bottom: 0; right: 0; border-left: none;  border-top: none; border-bottom-right-radius: 12px; }

.buscando {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  color: #fff;
  background: rgba(0, 0, 0, 0.55);
  padding: 16px 22px;
  border-radius: 14px;
  font-size: 0.85rem;
}

/* ── Barra inferior ───────────────────────────────────────────────────────── */
.barra-inferior {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.aviso {
  margin: 0;
  color: #fff;
  background: rgba(217, 45, 32, 0.9);
  padding: 10px 12px;
  border-radius: 12px;
  font-size: 0.8rem;
  line-height: 1.4;
}

/* ── Entrada manual ───────────────────────────────────────────────────────── */
.form-manual {
  padding: 20px;
}

.form-manual p {
  margin: 0 0 18px;
  line-height: 1.5;
}

.campo {
  margin-bottom: 20px;
  --border-radius: 12px;
  font-family: 'Roboto Mono', monospace;
}
</style>
