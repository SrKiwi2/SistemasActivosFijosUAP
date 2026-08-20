<template>
  <div class="proxima">
    <div class="icono">
      <ion-icon :icon="icono" />
    </div>
    <h2>{{ titulo }}</h2>
    <p class="texto-suave">{{ descripcion }}</p>
    <span class="etiqueta-fase">{{ fase }}</span>
  </div>
</template>

<script setup lang="ts">
import { IonIcon } from '@ionic/vue';

defineProps<{
  icono: string;
  titulo: string;
  descripcion: string;
  fase: string;
}>();
</script>

<style scoped>
.proxima {
  text-align: center;
  padding: 56px 32px;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.icono {
  width: 84px;
  height: 84px;
  border-radius: 24px;
  background: rgba(20, 67, 145, 0.08);
  display: grid;
  place-items: center;
  margin-bottom: 20px;
}

.icono ion-icon {
  font-size: 40px;
  color: var(--ion-color-primary);
}

h2 {
  margin: 0 0 8px;
  font-size: 1.15rem;
  font-weight: 700;
}

p {
  margin: 0 0 18px;
  max-width: 34ch;
  line-height: 1.5;
}

.etiqueta-fase {
  font-size: 0.7rem;
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--ion-color-primary);
  background: rgba(20, 67, 145, 0.1);
  padding: 6px 12px;
  border-radius: 999px;
}
</style>
