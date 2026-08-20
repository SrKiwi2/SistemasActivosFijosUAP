<template>
  <ion-page>
    <ion-header class="ion-no-border">
      <ion-toolbar>
        <ion-title>Buscar</ion-title>
      </ion-toolbar>
    </ion-header>

    <ion-content :fullscreen="true">
      <proxima-fase
        :icono="searchOutline"
        titulo="Búsqueda y exploración"
        descripcion="Búsqueda por código o descripción con filtros, y navegación por predio, oficina, responsable, grupo contable y auxiliar."
        fase="Fase 3"
      />
    </ion-content>
  </ion-page>
</template>

<script setup lang="ts">
import { IonPage, IonHeader, IonToolbar, IonTitle, IonContent } from '@ionic/vue';
import { searchOutline } from 'ionicons/icons';
import ProximaFase from '@/components/ProximaFase.vue';
</script>
