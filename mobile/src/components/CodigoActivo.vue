<template>
  <span class="codigo-activo" :class="{ grande }">
    <span v-if="partes.prefijo" class="prefijo">{{ partes.prefijo }}</span><span
      class="medio"
    >{{ partes.medio }}</span><span class="correlativo">{{ partes.correlativo }}</span>
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { partirCodigoVisual } from '@/services/qr';

const props = defineProps<{
  /** Código con prefijo de entidad: 148-01-04-02-03609 */
  codigo: string | null | undefined;
  grande?: boolean;
}>();

const partes = computed(() => partirCodigoVisual(props.codigo));
</script>

<style scoped>
/*
 * El prefijo de entidad va atenuado y el correlativo en negrita: al comparar
 * con la etiqueta física, la vista salta directo a la parte que distingue un
 * activo de otro.
 */
.codigo-activo {
  font-family: 'Roboto Mono', 'Courier New', monospace;
  white-space: nowrap;
}

.codigo-activo.grande {
  font-size: 1.35rem;
}

.prefijo {
  color: var(--sciaf-texto-suave);
  font-weight: 400;
}

.medio {
  font-weight: 600;
}

.correlativo {
  font-weight: 800;
}
</style>
