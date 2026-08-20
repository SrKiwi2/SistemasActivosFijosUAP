<template>
  <ion-page>
    <ion-header class="ion-no-border">
      <ion-toolbar>
        <ion-title>Avisos</ion-title>
      </ion-toolbar>
    </ion-header>

    <ion-content :fullscreen="true">
      <proxima-fase
        :icono="notificationsOutline"
        titulo="Notificaciones del sistema"
        descripcion="Avisos de lo que ocurre en la web: asignaciones registradas y subidas al VSIAF, activos modificados o cancelados, altas de responsables, oficinas y auxiliares."
        fase="Fase 7"
      />
    </ion-content>
  </ion-page>
</template>

<script setup lang="ts">
import { IonPage, IonHeader, IonToolbar, IonTitle, IonContent } from '@ionic/vue';
import { notificationsOutline } from 'ionicons/icons';
import ProximaFase from '@/components/ProximaFase.vue';
</script>
