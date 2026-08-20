<template>
  <ion-page>
    <ion-header class="ion-no-border">
      <ion-toolbar>
        <ion-title>Escáner</ion-title>
      </ion-toolbar>
    </ion-header>

    <ion-content :fullscreen="true">
      <div class="intro">
        <h2>¿Qué desea hacer?</h2>
        <p class="texto-suave">
          Los datos que valen son los del sistema. Si la etiqueta dice otra cosa,
          la aplicación se lo advertirá.
        </p>
      </div>

      <div class="opciones">
        <button class="opcion" @click="router.push('/escaner/camara')">
          <span class="icono azul"><ion-icon :icon="qrCodeOutline" /></span>
          <span class="texto">
            <strong>Escaneo de activo</strong>
            <small>Leer una etiqueta y ver su ficha completa</small>
          </span>
          <ion-icon :icon="chevronForwardOutline" class="flecha" />
        </button>

        <button class="opcion" :disabled="true">
          <span class="icono teal"><ion-icon :icon="documentTextOutline" /></span>
          <span class="texto">
            <strong>Emitir informe</strong>
            <small>Reunir varios activos y generar un PDF</small>
          </span>
          <span class="fase">Fase 5</span>
        </button>

        <button class="opcion" :disabled="true">
          <span class="icono verde"><ion-icon :icon="clipboardOutline" /></span>
          <span class="texto">
            <strong>Toma de inventario</strong>
            <small>Verificar una oficina activo por activo</small>
          </span>
          <span class="fase">Fase 8</span>
        </button>
      </div>

      <div class="manual">
        <ion-button expand="block" fill="outline" @click="router.push('/escaner/camara?manual=1')">
          <ion-icon slot="start" :icon="keypadOutline" />
          Escribir el código a mano
        </ion-button>
        <p class="texto-suave nota">
          Para etiquetas ilegibles o activos sin etiqueta.
        </p>
      </div>
    </ion-content>
  </ion-page>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router';
import {
  IonPage, IonHeader, IonToolbar, IonTitle, IonContent, IonIcon, IonButton,
} from '@ionic/vue';
import {
  qrCodeOutline, documentTextOutline, clipboardOutline,
  chevronForwardOutline, keypadOutline,
} from 'ionicons/icons';

const router = useRouter();
</script>

<style scoped>
.intro {
  padding: 20px 20px 4px;
}

.intro h2 {
  margin: 0 0 6px;
  font-size: 1.2rem;
  font-weight: 700;
}

.intro p {
  margin: 0;
  line-height: 1.5;
}

.opciones {
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.opcion {
  display: flex;
  align-items: center;
  gap: 14px;
  width: 100%;
  border: none;
  background: var(--ion-card-background);
  border-radius: var(--sciaf-radio);
  box-shadow: var(--sciaf-sombra);
  padding: 16px;
  text-align: left;
  color: var(--ion-text-color);
  cursor: pointer;
}

.opcion:disabled {
  opacity: 0.55;
}

.icono {
  width: 46px;
  height: 46px;
  border-radius: 14px;
  display: grid;
  place-items: center;
  flex-shrink: 0;
}

.icono ion-icon {
  font-size: 23px;
}

.icono.azul  { background: rgba(20, 67, 145, 0.1);  color: var(--ion-color-primary); }
.icono.teal  { background: rgba(3, 195, 166, 0.12); color: #059e88; }
.icono.verde { background: rgba(18, 183, 106, 0.12); color: var(--ion-color-success); }

.texto {
  display: flex;
  flex-direction: column;
  gap: 2px;
  flex: 1;
  min-width: 0;
}

.texto strong {
  font-size: 0.95rem;
}

.texto small {
  font-size: 0.78rem;
  color: var(--sciaf-texto-suave);
  line-height: 1.35;
}

.flecha {
  color: var(--sciaf-texto-suave);
  font-size: 1.1rem;
}

.fase {
  font-size: 0.65rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: var(--sciaf-texto-suave);
  background: rgba(107, 114, 128, 0.12);
  padding: 4px 8px;
  border-radius: 999px;
  flex-shrink: 0;
}

.manual {
  padding: 8px 16px 32px;
}

.nota {
  text-align: center;
  margin: 10px 0 0;
  font-size: 0.75rem;
}
</style>
