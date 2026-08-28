<template>
  <ion-page class="pagina-escaner">
    <ion-content :fullscreen="true" class="contenido-escaner">
      <div class="capa">
        <div class="barra-superior">
          <button class="redondo" aria-label="Volver a la lista" @click="salir">
            <ion-icon :icon="closeOutline" />
          </button>

          <div class="marcador">
            <strong>{{ lev.encontrados }}</strong>
            <span>de {{ lev.esperados }}</span>
          </div>

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
        </div>

        <div class="barra-inferior">
          <p v-if="escaner.error.value" class="aviso">{{ escaner.error.value }}</p>

          <!-- Última lectura: la cámara sigue viva, no hay que cerrar nada para
               seguir escaneando el siguiente activo del ambiente. -->
          <transition name="subir">
            <div v-if="ultima" class="tarjeta" :class="claseTarjeta">
              <div class="icono"><ion-icon :icon="iconoTarjeta" /></div>
              <div class="texto">
                <span class="estado">{{ ultima.mensaje }}</span>
                <codigo-activo v-if="'fila' in ultima" :codigo="ultima.fila.codigo" />
                <span v-if="'fila' in ultima" class="descripcion">
                  {{ ultima.fila.descripcion }}
                </span>
              </div>
              <button
                v-if="'fila' in ultima && !ultima.fila.sobrante"
                class="novedad"
                aria-label="Anotar novedad"
                @click="abrirNovedad(ultima.fila)"
              >
                <ion-icon :icon="createOutline" />
              </button>
            </div>
          </transition>

          <ion-button expand="block" fill="solid" color="light" @click="manualAbierto = true">
            <ion-icon slot="start" :icon="keypadOutline" />
            Escribir el código
          </ion-button>
        </div>
      </div>

      <!-- Entrada manual: hay etiquetas ilegibles y activos sin etiqueta -->
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
          <form class="form-manual" @submit.prevent="marcarManual">
            <p class="texto-suave">
              Se acepta con o sin el prefijo <strong>148-</strong>, con o sin guiones.
            </p>
            <ion-input
              v-model="codigoManual"
              label="Código"
              label-placement="stacked"
              fill="outline"
              inputmode="numeric"
              enterkeyhint="done"
              placeholder="148-01-04-02-03609"
              class="campo"
              @ion-input="alEscribir"
            />
            <ion-button type="submit" expand="block" :disabled="!codigoManual">
              Marcar como encontrado
            </ion-button>
          </form>
        </ion-content>
      </ion-modal>

      <hoja-novedad
        :abierta="novedadAbierta"
        :fila="filaNovedad"
        :estados="lev.estados"
        @cerrar="novedadAbierta = false"
        @guardar="guardarNovedad"
      />
    </ion-content>
  </ion-page>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import {
  IonPage, IonContent, IonIcon, IonButton, IonModal, IonHeader, IonToolbar,
  IonTitle, IonButtons, IonInput, onIonViewWillLeave,
} from '@ionic/vue';
import {
  closeOutline, flashlightOutline, flashOffOutline, keypadOutline, createOutline,
  checkmarkCircleOutline, alertCircleOutline, informationCircleOutline,
} from 'ionicons/icons';
import { useEscaner } from '@/composables/useEscaner';
import { formatearCodigoManual } from '@/services/qr';
import { useLevantamientoStore, type FilaRecorrido, type ResultadoLectura } from '@/stores/levantamiento';
import CodigoActivo from '@/components/CodigoActivo.vue';
import HojaNovedad from '@/components/HojaNovedad.vue';

const route = useRoute();
const router = useRouter();
const escaner = useEscaner();
const lev = useLevantamientoStore();

const idInventario = Number(route.params.id);

const ultima = ref<ResultadoLectura | null>(null);
const manualAbierto = ref(false);
const codigoManual = ref('');
const novedadAbierta = ref(false);
const filaNovedad = ref<FilaRecorrido | null>(null);

const claseTarjeta = computed(() => {
  switch (ultima.value?.tipo) {
    case 'MARCADO': return 'ok';
    case 'YA_MARCADO': return 'repetido';
    case 'SOBRANTE': return 'sobrante';
    default: return 'error';
  }
});

const iconoTarjeta = computed(() => {
  switch (ultima.value?.tipo) {
    case 'MARCADO': return checkmarkCircleOutline;
    case 'YA_MARCADO': return informationCircleOutline;
    default: return alertCircleOutline;
  }
});

onMounted(async () => {
  // Si se entró por enlace directo sin pasar por la lista, el paquete no está
  // cargado y no habría contra qué comparar las lecturas.
  if (lev.paquete?.idInventario !== idInventario) {
    try {
      await lev.retomar(idInventario);
    } catch {
      router.replace(`/levantamiento/${idInventario}`);
      return;
    }
  }
  await escaner.iniciar(alLeer);
});

onIonViewWillLeave(() => {
  escaner.detener();
});

onBeforeUnmount(() => {
  escaner.detener();
});

/**
 * Cada lectura se resuelve contra la lista que ya está en el teléfono, sin
 * tocar la red: en un depósito sin señal el recorrido tiene que seguir, y la
 * respuesta tiene que ser instantánea porque el operador mira la etiqueta y no
 * la pantalla. El háptico es la confirmación real.
 */
async function alLeer(texto: string) {
  const resultado = await lev.registrarLectura(texto, 'ESCANEO');
  ultima.value = resultado;

  if (resultado.tipo === 'MARCADO') await escaner.vibrarExito();
  else await escaner.vibrarError();
}

async function marcarManual() {
  const texto = codigoManual.value.trim();
  if (!texto) return;

  ultima.value = await lev.registrarLectura(texto, 'MANUAL');
  manualAbierto.value = false;
  codigoManual.value = '';
}

function alEscribir(evento: CustomEvent) {
  const valor = String((evento.target as HTMLInputElement).value ?? '');
  codigoManual.value = formatearCodigoManual(valor);
}

function cerrarManual() {
  manualAbierto.value = false;
  codigoManual.value = '';
}

function abrirNovedad(f: FilaRecorrido) {
  filaNovedad.value = f;
  novedadAbierta.value = true;
}

async function guardarNovedad(datos: { observacion: string | null; idEstadoObservado: number | null }) {
  if (filaNovedad.value) {
    await lev.anotarNovedad(filaNovedad.value, datos.observacion, datos.idEstadoObservado);
    // La tarjeta refleja la novedad recién anotada sin salir de la cámara.
    const actualizada = lev.filas.find((f) => f.clave === filaNovedad.value?.clave);
    if (actualizada && ultima.value && 'fila' in ultima.value) ultima.value.fila = actualizada;
  }
  novedadAbierta.value = false;
}

async function salir() {
  await escaner.detener();
  router.back();
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

/* El avance se mira de reojo mientras se escanea: va arriba y en grande. */
.marcador {
  display: flex;
  align-items: baseline;
  gap: 6px;
  color: #fff;
  background: rgba(0, 0, 0, 0.45);
  padding: 6px 14px;
  border-radius: 999px;
  text-shadow: 0 1px 3px rgba(0, 0, 0, 0.5);
}

.marcador strong {
  font-size: 1.25rem;
  font-weight: 800;
}

.marcador span {
  font-size: 0.78rem;
  opacity: 0.85;
}

/* ── Marco guía ───────────────────────────────────────────────────────────── */
.marco {
  position: relative;
  width: min(70vw, 280px);
  aspect-ratio: 1;
  align-self: center;
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

.tarjeta {
  display: flex;
  align-items: center;
  gap: 12px;
  background: var(--ion-card-background);
  border-radius: 14px;
  border-left: 5px solid var(--ion-color-medium);
  padding: 12px 14px;
  box-shadow: 0 6px 20px rgba(0, 0, 0, 0.35);
}

.tarjeta.ok        { border-left-color: var(--ion-color-success); }
.tarjeta.repetido  { border-left-color: var(--ion-color-medium); }
.tarjeta.sobrante  { border-left-color: var(--ion-color-warning); }
.tarjeta.error     { border-left-color: var(--ion-color-danger); }

.tarjeta .icono {
  flex: 0 0 auto;
  font-size: 26px;
  display: grid;
  place-items: center;
}

.tarjeta.ok .icono       { color: var(--ion-color-success); }
.tarjeta.repetido .icono { color: var(--ion-color-medium); }
.tarjeta.sobrante .icono { color: var(--ion-color-warning); }
.tarjeta.error .icono    { color: var(--ion-color-danger); }

.tarjeta .texto {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
  flex: 1;
}

.tarjeta .estado {
  font-size: 0.72rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: var(--sciaf-texto-suave);
}

.tarjeta .descripcion {
  font-size: 0.8rem;
  line-height: 1.3;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.tarjeta .novedad {
  flex: 0 0 auto;
  width: 44px;
  height: 44px;
  border: none;
  border-radius: 12px;
  background: rgba(var(--ion-color-primary-rgb), 0.1);
  color: var(--ion-color-primary);
  font-size: 20px;
}

.subir-enter-active,
.subir-leave-active {
  transition: opacity 0.18s ease, transform 0.18s ease;
}

.subir-enter-from,
.subir-leave-to {
  opacity: 0;
  transform: translateY(10px);
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
