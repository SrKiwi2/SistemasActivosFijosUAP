<template>
  <span class="chip-control" :class="estado.toLowerCase().replace('_', '-')">{{ etiqueta }}</span>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import type { EstadoControl } from '@/services/levantamiento';

const props = defineProps<{ estado: EstadoControl }>();

const ETIQUETAS: Record<EstadoControl, string> = {
  SIN_LEVANTAR: 'Sin levantar',
  EN_CURSO: 'En curso',
  CONTROLADO: 'Controlado',
  CON_FALTANTES: 'Con faltantes',
};

const etiqueta = computed(() => ETIQUETAS[props.estado] ?? props.estado);
</script>

<style scoped>
/*
 * Mismo semáforo que los tiles del mapa web, para que quien mira las dos
 * pantallas no tenga que traducir colores.
 */
.chip-control {
  display: inline-block;
  font-size: 0.68rem;
  font-weight: 700;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  padding: 3px 9px;
  border-radius: 999px;
  white-space: nowrap;
}

.sin-levantar   { color: var(--ion-color-medium);  background: rgba(107, 114, 128, 0.14); }
.en-curso       { color: var(--ion-color-warning); background: rgba(247, 144, 9, 0.16); }
.controlado     { color: var(--ion-color-success); background: rgba(18, 183, 106, 0.14); }
.con-faltantes  { color: var(--ion-color-danger);  background: rgba(217, 45, 32, 0.14); }
</style>
