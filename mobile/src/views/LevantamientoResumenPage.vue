<template>
  <ion-page>
    <ion-header class="ion-no-border">
      <ion-toolbar>
        <ion-buttons slot="start">
          <ion-back-button default-href="/levantamiento" text="" />
        </ion-buttons>
        <ion-title>Recorrido cerrado</ion-title>
      </ion-toolbar>
    </ion-header>

    <ion-content :fullscreen="true">
      <div v-if="cargando" class="centrado">
        <ion-spinner name="crescent" />
      </div>

      <p v-else-if="error" class="aviso-error">{{ error }}</p>

      <div v-else-if="resumen" class="cuerpo">
        <div class="sello">
          <ion-icon :icon="checkmarkCircle" />
          <strong>{{ resumen.numeroInventario }}</strong>
          <span v-if="oficina" class="texto-suave">{{ oficina }}</span>
        </div>

        <div class="rejilla">
          <div class="dato">
            <span class="cifra">{{ resumen.esperados }}</span>
            <span class="etiqueta">Esperados</span>
          </div>
          <div class="dato bien">
            <span class="cifra">{{ resumen.encontrados }}</span>
            <span class="etiqueta">Encontrados</span>
          </div>
          <div class="dato" :class="{ mal: resumen.faltantes > 0 }">
            <span class="cifra">{{ resumen.faltantes }}</span>
            <span class="etiqueta">Faltantes</span>
          </div>
          <div class="dato" :class="{ aviso: resumen.observados > 0 }">
            <span class="cifra">{{ resumen.observados }}</span>
            <span class="etiqueta">Con novedad</span>
          </div>
        </div>

        <div class="sciaf-card nota">
          <p v-if="resumen.hallazgosCreados">
            Se registraron <strong>{{ resumen.hallazgosCreados }}</strong> hallazgos.
            Los faltantes quedaron abiertos a nombre de su responsable y se resuelven
            desde el sistema web.
          </p>
          <p v-else>
            No quedaron hallazgos pendientes: todo lo esperado apareció y sin novedades.
          </p>
        </div>

        <ion-button expand="block" @click="router.replace('/levantamiento')">
          <ion-icon slot="start" :icon="clipboardOutline" />
          Levantar otra oficina
        </ion-button>
        <ion-button expand="block" fill="clear" @click="router.replace('/tabs/inicio')">
          Volver al inicio
        </ion-button>
      </div>
    </ion-content>
  </ion-page>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import {
  IonPage, IonHeader, IonToolbar, IonTitle, IonContent, IonButtons, IonButton,
  IonBackButton, IonIcon, IonSpinner,
} from '@ionic/vue';
import { checkmarkCircle, clipboardOutline } from 'ionicons/icons';
import { levantamientoApi, type ResumenCierre } from '@/services/levantamiento';
import { mensajeDeError } from '@/services/http';
import { useLevantamientoStore } from '@/stores/levantamiento';

const route = useRoute();
const router = useRouter();
const lev = useLevantamientoStore();

const idInventario = Number(route.params.id);
const resumen = ref<ResumenCierre | null>(null);
const oficina = ref<string | null>(null);
const cargando = ref(false);
const error = ref<string | null>(null);

onMounted(async () => {
  // Lo normal es venir de cerrar y tener el resumen en memoria. Si se llegó por
  // enlace directo o tras reabrir la app, se reconstruye desde el servidor.
  if (lev.ultimoCierre?.idInventario === idInventario) {
    resumen.value = lev.ultimoCierre;
  }

  cargando.value = !resumen.value;
  try {
    const paquete = await levantamientoApi.paquete(idInventario);
    oficina.value = [paquete.oficina, paquete.predio].filter(Boolean).join(' · ') || null;

    if (!resumen.value) {
      resumen.value = {
        ok: true,
        idInventario: paquete.idInventario,
        numeroInventario: paquete.numeroInventario,
        esperados: paquete.totalEsperados,
        encontrados: paquete.totalEncontrados,
        faltantes: paquete.totalFaltantes,
        // El detalle sí distingue las novedades; los hallazgos creados no se
        // pueden reconstruir sin el cierre, así que no se inventan.
        observados: paquete.detalle.filter((d) => d.observacion || d.idEstadoObservado).length,
        hallazgosCreados: 0,
      };
    }
  } catch (e) {
    if (!resumen.value) error.value = mensajeDeError(e, 'No se pudo cargar el resumen');
  } finally {
    cargando.value = false;
  }
});
</script>

<style scoped>
.cuerpo {
  padding: 8px 20px 32px;
}

.sello {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 24px 0 20px;
  text-align: center;
}

.sello ion-icon {
  font-size: 60px;
  color: var(--ion-color-success);
}

.sello strong {
  font-family: 'Roboto Mono', monospace;
  font-size: 1.1rem;
}

.rejilla {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px;
}

.dato {
  background: var(--ion-card-background);
  border-radius: var(--sciaf-radio);
  box-shadow: var(--sciaf-sombra);
  padding: 16px 12px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
}

.cifra {
  font-size: 1.8rem;
  font-weight: 800;
  line-height: 1;
}

.etiqueta {
  font-size: 0.72rem;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  color: var(--sciaf-texto-suave);
}

.dato.bien .cifra  { color: var(--ion-color-success); }
.dato.mal .cifra   { color: var(--ion-color-danger); }
.dato.aviso .cifra { color: var(--ion-color-warning); }

.nota {
  margin-top: 16px;
}

.nota p {
  margin: 0;
  font-size: 0.85rem;
  line-height: 1.5;
}

ion-button {
  margin-top: 12px;
}

.centrado {
  display: grid;
  place-items: center;
  padding: 60px 0;
}

.aviso-error {
  margin: 20px;
  padding: 12px;
  border-radius: 12px;
  font-size: 0.85rem;
  color: var(--ion-color-danger);
  background: rgba(217, 45, 32, 0.12);
}
</style>
