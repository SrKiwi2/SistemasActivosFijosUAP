<template>
  <div class="hoja">
    <!-- Veredicto: color + icono + texto. Nunca solo color. -->
    <div class="veredicto" :class="estilo.clase">
      <ion-icon :icon="estilo.icono" />
      <div>
        <strong>{{ estilo.titulo }}</strong>
        <span>{{ resultado.mensaje }}</span>
      </div>
    </div>

    <!-- Ficha del activo: lo que dice el SISTEMA, que es lo que vale -->
    <template v-if="resultado.activo">
      <div class="cabecera">
        <codigo-activo :codigo="resultado.activo.codigoVisual" grande />
        <ion-chip v-if="esEstadoInusual" :color="colorEstado" class="chip-est">
          {{ resultado.activo.estado }}
        </ion-chip>
      </div>

      <p class="descripcion">{{ resultado.activo.descripcion }}</p>

      <div class="datos">
        <div class="dato">
          <ion-icon :icon="businessOutline" />
          <div>
            <small>Oficina</small>
            <span>{{ resultado.activo.ubicacion?.oficina ?? '—' }}</span>
          </div>
        </div>
        <div class="dato">
          <ion-icon :icon="personOutline" />
          <div>
            <small>Responsable</small>
            <span>{{ resultado.activo.responsable?.nombre ?? 'Sin responsable' }}</span>
          </div>
        </div>
        <div class="dato">
          <ion-icon :icon="locationOutline" />
          <div>
            <small>Predio</small>
            <span>{{ resultado.activo.ubicacion?.predio ?? '—' }}</span>
          </div>
        </div>
        <div class="dato">
          <ion-icon :icon="folderOutline" />
          <div>
            <small>Auxiliar</small>
            <span>{{ resultado.activo.auxiliar?.nombre ?? '—' }}</span>
          </div>
        </div>
      </div>

      <!-- Diferencias con la etiqueta -->
      <div v-if="resultado.discrepancias.length" class="diferencias">
        <p class="titulo-seccion sin-margen">
          La etiqueta no coincide en {{ resultado.discrepancias.length }}
          {{ resultado.discrepancias.length === 1 ? 'dato' : 'datos' }}
        </p>

        <div v-for="d in resultado.discrepancias" :key="d.campo" class="diferencia" :class="d.severidad.toLowerCase()">
          <div class="campo">{{ d.etiqueta }}</div>
          <div class="valores">
            <div class="valor etiqueta">
              <small>Etiqueta</small>
              <span>{{ d.valorQr ?? '—' }}</span>
            </div>
            <ion-icon :icon="arrowForwardOutline" class="flecha" />
            <div class="valor sistema">
              <small>Sistema · válido</small>
              <span>{{ d.valorSistema ?? '—' }}</span>
            </div>
          </div>
          <p v-if="d.capa === 2" class="explicacion">
            El código se emitió cuando el activo estaba ahí; después se movió. Es normal.
          </p>
        </div>
      </div>
    </template>

    <!-- Varios activos terminan en el mismo correlativo -->
    <div v-else-if="resultado.candidatos.length" class="candidatos">
      <p class="titulo-seccion sin-margen">Elija el activo</p>
      <button
        v-for="c in resultado.candidatos"
        :key="c.idActivo"
        class="candidato"
        @click="$emit('elegir', c)"
      >
        <codigo-activo :codigo="c.codigoVisual" />
        <small>{{ c.descripcion }}</small>
        <small class="texto-suave">{{ c.ubicacion?.oficina ?? '—' }}</small>
      </button>
    </div>

    <div class="acciones">
      <ion-button
        v-if="resultado.activo"
        expand="block"
        @click="$emit('verFicha', resultado.activo!.codigo)"
      >
        Ver ficha completa
      </ion-button>
      <ion-button expand="block" fill="outline" @click="$emit('otro')">
        Escanear otro
      </ion-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { IonIcon, IonButton, IonChip } from '@ionic/vue';
import {
  checkmarkCircleOutline, alertCircleOutline, informationCircleOutline,
  closeCircleOutline, swapHorizontalOutline, businessOutline, personOutline,
  locationOutline, folderOutline, arrowForwardOutline,
} from 'ionicons/icons';
import type { ActivoFicha, ResultadoEscaneo } from '@/services/activos';
import CodigoActivo from './CodigoActivo.vue';

const props = defineProps<{ resultado: ResultadoEscaneo }>();

defineEmits<{
  (e: 'verFicha', codigo: string): void;
  (e: 'otro'): void;
  (e: 'elegir', activo: ActivoFicha): void;
}>();

/**
 * Cada veredicto tiene su color, icono y titular. El estado nunca se comunica
 * solo con color: siempre color + icono + palabra.
 */
const estilo = computed(() => {
  switch (props.resultado.veredicto) {
    case 'OK':
      return { clase: 'ok', icono: checkmarkCircleOutline, titulo: 'Todo coincide' };
    case 'ETIQUETA_DESACTUALIZADA':
      return { clase: 'aviso', icono: alertCircleOutline, titulo: 'Etiqueta desactualizada' };
    case 'REUBICADO':
      return { clase: 'info', icono: swapHorizontalOutline, titulo: 'El activo fue reubicado' };
    case 'REVISAR_ESTADO':
      return { clase: 'aviso', icono: alertCircleOutline, titulo: 'Revise el estado' };
    case 'NO_ENCONTRADO':
      return { clase: 'error', icono: closeCircleOutline, titulo: 'No encontrado' };
    case 'OTRA_ENTIDAD':
      return { clase: 'error', icono: closeCircleOutline, titulo: 'No es de la Universidad' };
    case 'VARIOS_CANDIDATOS':
      return { clase: 'info', icono: informationCircleOutline, titulo: 'Varios activos posibles' };
    default:
      return { clase: 'error', icono: closeCircleOutline, titulo: 'No se pudo leer' };
  }
});

const esEstadoInusual = computed(() => {
  const e = props.resultado.activo?.estado;
  return Boolean(e) && e !== 'ACTIVO';
});

const colorEstado = computed(() => {
  const e = props.resultado.activo?.estado;
  if (e === 'PENDIENTE') return 'warning';
  if (e === 'CANCELADO' || e === 'BAJA' || e === 'ELIMINADO') return 'danger';
  return 'medium';
});
</script>

<style scoped>
.hoja {
  padding: 8px 20px 28px;
}

/* ── Veredicto ────────────────────────────────────────────────────────────── */
.veredicto {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  border-radius: 14px;
  padding: 14px;
  margin-bottom: 18px;
}

.veredicto ion-icon {
  font-size: 1.5rem;
  flex-shrink: 0;
}

.veredicto div {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.veredicto strong {
  font-size: 0.95rem;
}

.veredicto span {
  font-size: 0.8rem;
  line-height: 1.4;
  opacity: 0.9;
}

.veredicto.ok    { background: rgba(18, 183, 106, 0.12); color: #0b7a47; }
.veredicto.aviso { background: rgba(247, 144, 9, 0.14);  color: #9a5b00; }
.veredicto.info  { background: rgba(20, 67, 145, 0.09);  color: var(--ion-color-primary); }
.veredicto.error { background: rgba(217, 45, 32, 0.11);  color: #a81d13; }

/* ── Ficha ────────────────────────────────────────────────────────────────── */
.cabecera {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.chip-est {
  font-size: 0.7rem;
  height: 24px;
}

.descripcion {
  margin: 6px 0 18px;
  font-size: 0.92rem;
  line-height: 1.45;
}

.datos {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px 12px;
}

.dato {
  display: flex;
  gap: 8px;
  align-items: flex-start;
  min-width: 0;
}

.dato ion-icon {
  color: var(--sciaf-texto-suave);
  font-size: 1.05rem;
  margin-top: 2px;
  flex-shrink: 0;
}

.dato div {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.dato small {
  font-size: 0.68rem;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: var(--sciaf-texto-suave);
}

.dato span {
  font-size: 0.85rem;
  line-height: 1.3;
}

/* ── Diferencias ──────────────────────────────────────────────────────────── */
.diferencias {
  margin-top: 22px;
}

.sin-margen {
  margin-top: 0;
}

.diferencia {
  border-left: 3px solid var(--ion-color-warning);
  background: rgba(247, 144, 9, 0.07);
  border-radius: 0 12px 12px 0;
  padding: 10px 12px;
  margin-bottom: 10px;
}

.diferencia.info {
  border-left-color: var(--ion-color-primary);
  background: rgba(20, 67, 145, 0.06);
}

.diferencia .campo {
  font-size: 0.75rem;
  font-weight: 700;
  margin-bottom: 6px;
}

.valores {
  display: flex;
  align-items: center;
  gap: 8px;
}

.valor {
  display: flex;
  flex-direction: column;
  min-width: 0;
  flex: 1;
}

.valor small {
  font-size: 0.62rem;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: var(--sciaf-texto-suave);
}

.valor span {
  font-size: 0.8rem;
  line-height: 1.3;
}

.valor.etiqueta span {
  text-decoration: line-through;
  opacity: 0.7;
}

.valor.sistema span {
  font-weight: 600;
}

.flecha {
  color: var(--sciaf-texto-suave);
  flex-shrink: 0;
}

.explicacion {
  margin: 8px 0 0;
  font-size: 0.72rem;
  line-height: 1.4;
  color: var(--sciaf-texto-suave);
}

/* ── Candidatos ───────────────────────────────────────────────────────────── */
.candidatos {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.candidato {
  display: flex;
  flex-direction: column;
  gap: 2px;
  text-align: left;
  border: 1px solid var(--sciaf-borde);
  background: transparent;
  border-radius: 12px;
  padding: 12px;
  color: var(--ion-text-color);
}

.candidato small {
  font-size: 0.78rem;
  line-height: 1.35;
}

/* ── Acciones ─────────────────────────────────────────────────────────────── */
.acciones {
  margin-top: 24px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
</style>
