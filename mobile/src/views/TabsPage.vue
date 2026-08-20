<template>
  <ion-page>
    <ion-tabs>
      <ion-router-outlet />

      <ion-tab-bar slot="bottom">
        <ion-tab-button tab="inicio" href="/tabs/inicio">
          <ion-icon :icon="homeOutline" />
          <ion-label>Inicio</ion-label>
        </ion-tab-button>

        <ion-tab-button tab="buscar" href="/tabs/buscar">
          <ion-icon :icon="searchOutline" />
          <ion-label>Buscar</ion-label>
        </ion-tab-button>

        <!-- Acción principal de la app: destacada y más grande -->
        <ion-tab-button tab="escaner" href="/tabs/escaner" class="tab-escaner">
          <span class="boton-escaner">
            <ion-icon :icon="qrCodeOutline" />
          </span>
          <ion-label>Escanear</ion-label>
        </ion-tab-button>

        <ion-tab-button tab="notificaciones" href="/tabs/notificaciones">
          <ion-icon :icon="notificationsOutline" />
          <ion-label>Avisos</ion-label>
        </ion-tab-button>

        <ion-tab-button tab="mas" href="/tabs/mas">
          <ion-icon :icon="ellipsisHorizontalOutline" />
          <ion-label>Más</ion-label>
        </ion-tab-button>
      </ion-tab-bar>
    </ion-tabs>
  </ion-page>
</template>

<script setup lang="ts">
import {
  IonPage, IonTabs, IonTabBar, IonTabButton, IonRouterOutlet, IonIcon, IonLabel,
} from '@ionic/vue';
import {
  homeOutline, searchOutline, qrCodeOutline,
  notificationsOutline, ellipsisHorizontalOutline,
} from 'ionicons/icons';
</script>

<style scoped>
ion-tab-bar {
  --border: 1px solid var(--sciaf-borde);
  height: 62px;
  padding-bottom: 4px;
}

ion-tab-button {
  --color: var(--sciaf-texto-suave);
  --color-selected: var(--ion-color-primary);
  font-size: 0.7rem;
}

/* Botón central: círculo azul elevado sobre la barra */
.tab-escaner {
  position: relative;
}

.boton-escaner {
  width: 50px;
  height: 50px;
  margin-top: -18px;
  margin-bottom: 2px;
  border-radius: 50%;
  background: var(--sciaf-degradado);
  display: grid;
  place-items: center;
  box-shadow: 0 6px 16px rgba(20, 67, 145, 0.4);
  border: 3px solid var(--ion-tab-bar-background);
}

.boton-escaner ion-icon {
  font-size: 26px;
  color: #fff;
}
</style>
