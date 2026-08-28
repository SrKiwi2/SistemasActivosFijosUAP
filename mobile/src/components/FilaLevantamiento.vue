<template>
  <div class="fila" :class="[situacionClase, { sobrante: fila.sobrante }]">
    <button class="zona-marca" :aria-label="etiquetaAccion" @click="$emit('alternar', fila)">
      <span class="casilla">
        <ion-icon v-if="fila.situacion === 'ENCONTRADO'" :icon="checkmarkOutline" />
      </span>

      <span class="datos">
        <codigo-activo :codigo="fila.codigo" />
        <span class="descripcion">{{ fila.descripcion || 'Sin descripción' }}</span>

        <span class="pie">
          <span v-if="fila.responsable" class="responsable">{{ fila.responsable }}</span>
          <span v-if="fila.sobrante" class="marca-sobrante">Sobrante</span>
          <span v-if="fila.origen === 'ESCANEO'" class="via"><ion-icon :icon="qrCodeOutline" /></span>
          <span v-if="fila.sinEnviar" class="via" title="Sin enviar"><ion-icon :icon="cloudUploadOutline" /></span>
        </span>

        <span v-if="tieneNovedad(fila)" class="novedad">
          <ion-icon :icon="alertCircleOutline" />
          <span>{{ textoNovedad }}</span>
        </span>
      </span>
    </button>

    <button class="zona-novedad" aria-label="Anotar novedad" @click="$emit('novedad', fila)">
      <ion-icon :icon="createOutline" />
    </button>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { IonIcon } from '@ionic/vue';
import {
  checkmarkOutline, createOutline, qrCodeOutline, alertCircleOutline, cloudUploadOutline,
} from 'ionicons/icons';
import CodigoActivo from '@/components/CodigoActivo.vue';
import { tieneNovedad, type FilaRecorrido } from '@/stores/levantamiento';

const props = defineProps<{ fila: FilaRecorrido }>();
defineEmits<{ alternar: [FilaRecorrido]; novedad: [FilaRecorrido] }>();

const situacionClase = computed(() => props.fila.situacion.toLowerCase());

const etiquetaAccion = computed(() =>
  props.fila.situacion === 'ENCONTRADO' ? 'Desmarcar' : 'Marcar como encontrado',
);

const textoNovedad = computed(() =>
  [props.fila.estadoObservado, props.fila.observacion].filter(Boolean).join(' · '),
);
</script>

<style scoped>
.fila {
  display: flex;
  align-items: stretch;
  gap: 4px;
  background: var(--ion-card-background);
  border-radius: 14px;
  border-left: 4px solid transparent;
  box-shadow: var(--sciaf-sombra);
  margin-bottom: 8px;
  overflow: hidden;
}

.fila.encontrado { border-left-color: var(--ion-color-success); }
.fila.faltante   { border-left-color: var(--ion-color-danger); }
.fila.sobrante   { border-left-color: var(--ion-color-warning); }

.zona-marca {
  flex: 1;
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 12px;
  background: none;
  border: none;
  text-align: left;
  color: var(--ion-text-color);
  min-height: 60px;
}

/* La casilla es grande a propósito: se toca con guantes y a contraluz. */
.casilla {
  flex: 0 0 auto;
  width: 26px;
  height: 26px;
  margin-top: 2px;
  border-radius: 8px;
  border: 2px solid var(--sciaf-borde);
  display: grid;
  place-items: center;
  font-size: 17px;
}

.fila.encontrado .casilla {
  background: var(--ion-color-success);
  border-color: var(--ion-color-success);
  color: #fff;
}

.datos {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.descripcion {
  font-size: 0.85rem;
  line-height: 1.3;
}

.pie {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 2px;
}

.responsable {
  font-size: 0.72rem;
  color: var(--sciaf-texto-suave);
}

.marca-sobrante {
  font-size: 0.65rem;
  font-weight: 700;
  text-transform: uppercase;
  color: var(--ion-color-warning);
}

.via {
  display: inline-flex;
  font-size: 13px;
  color: var(--sciaf-texto-suave);
}

.novedad {
  display: flex;
  align-items: center;
  gap: 5px;
  margin-top: 4px;
  font-size: 0.74rem;
  line-height: 1.3;
  color: var(--ion-color-warning);
}

.novedad ion-icon {
  flex: 0 0 auto;
  font-size: 14px;
}

.zona-novedad {
  flex: 0 0 auto;
  width: 46px;
  background: none;
  border: none;
  border-left: 1px solid var(--sciaf-borde);
  color: var(--sciaf-texto-suave);
  font-size: 19px;
}
</style>
