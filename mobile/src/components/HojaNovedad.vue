<template>
  <ion-modal
    :is-open="abierta"
    :breakpoints="[0, 0.75, 0.95]"
    :initial-breakpoint="0.75"
    @did-dismiss="$emit('cerrar')"
  >
    <ion-content>
      <div class="hoja">
        <header>
          <p class="titulo-seccion">Novedad de condición</p>
          <codigo-activo v-if="fila" :codigo="fila.codigo" />
          <p class="descripcion">{{ fila?.descripcion || 'Sin descripción' }}</p>
        </header>

        <p class="texto-suave nota">
          Se registra sobre un activo que <strong>sí apareció</strong>. Al cerrar el
          recorrido queda como hallazgo para que alguien lo atienda.
        </p>

        <template v-if="estados.length">
          <p class="titulo-seccion">Estado en que se lo encontró</p>
          <div class="estados">
            <button
              v-for="e in estados"
              :key="e.id"
              class="estado"
              :class="{ elegido: idEstado === e.id }"
              @click="idEstado = idEstado === e.id ? null : e.id"
            >
              {{ e.nombre }}
            </button>
          </div>
        </template>

        <p class="titulo-seccion">Observación</p>
        <ion-textarea
          v-model="observacion"
          fill="outline"
          :rows="4"
          :counter="true"
          :maxlength="500"
          placeholder="Ej.: le falta una rueda, la pantalla no enciende…"
          class="campo"
        />

        <div class="acciones">
          <ion-button expand="block" :disabled="!hayAlgo" @click="guardar">
            <ion-icon slot="start" :icon="saveOutline" />
            Guardar novedad
          </ion-button>
          <ion-button
            v-if="teniaNovedad"
            expand="block"
            fill="clear"
            color="medium"
            @click="quitar"
          >
            Quitar la novedad
          </ion-button>
        </div>
      </div>
    </ion-content>
  </ion-modal>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { IonModal, IonContent, IonTextarea, IonButton, IonIcon } from '@ionic/vue';
import { saveOutline } from 'ionicons/icons';
import CodigoActivo from '@/components/CodigoActivo.vue';
import type { EstadoActivo } from '@/services/levantamiento';
import { tieneNovedad, type FilaRecorrido } from '@/stores/levantamiento';

const props = defineProps<{
  abierta: boolean;
  fila: FilaRecorrido | null;
  estados: EstadoActivo[];
}>();

const emit = defineEmits<{
  cerrar: [];
  guardar: [{ observacion: string | null; idEstadoObservado: number | null }];
}>();

const observacion = ref('');
const idEstado = ref<number | null>(null);

// Al abrir sobre otra fila se parte de lo que esa fila ya tenía anotado, no de
// lo que quedó del activo anterior.
watch(
  () => [props.abierta, props.fila?.clave],
  () => {
    if (!props.abierta) return;
    observacion.value = props.fila?.observacion ?? '';
    idEstado.value = props.fila?.idEstadoObservado ?? null;
  },
  { immediate: true },
);

const hayAlgo = computed(() => Boolean(observacion.value.trim()) || idEstado.value !== null);
const teniaNovedad = computed(() => Boolean(props.fila && tieneNovedad(props.fila)));

function guardar() {
  emit('guardar', {
    observacion: observacion.value.trim() || null,
    idEstadoObservado: idEstado.value,
  });
}

function quitar() {
  emit('guardar', { observacion: null, idEstadoObservado: null });
}
</script>

<style scoped>
.hoja {
  padding: 20px 20px 32px;
}

header .titulo-seccion {
  margin-top: 0;
}

.descripcion {
  margin: 6px 0 0;
  font-size: 0.9rem;
  line-height: 1.35;
}

.nota {
  margin: 14px 0 0;
  line-height: 1.5;
}

.estados {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.estado {
  min-height: 44px;
  padding: 8px 16px;
  border-radius: 999px;
  border: 1.5px solid var(--sciaf-borde);
  background: var(--ion-card-background);
  color: var(--ion-text-color);
  font-size: 0.85rem;
  font-weight: 600;
}

.estado.elegido {
  border-color: var(--ion-color-primary);
  background: rgba(var(--ion-color-primary-rgb), 0.1);
  color: var(--ion-color-primary);
}

.campo {
  --border-radius: 12px;
  margin-top: 4px;
}

.acciones {
  margin-top: 22px;
}
</style>
