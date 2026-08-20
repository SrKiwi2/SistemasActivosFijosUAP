<template>
  <ion-page>
    <ion-header class="ion-no-border">
      <ion-toolbar>
        <ion-buttons slot="start">
          <ion-back-button default-href="/tabs/escaner" text="" />
        </ion-buttons>
        <ion-title>Ficha del activo</ion-title>
      </ion-toolbar>
    </ion-header>

    <ion-content :fullscreen="true">
      <div v-if="cargando" class="centrado">
        <ion-spinner name="crescent" />
        <p class="texto-suave">Cargando ficha…</p>
      </div>

      <div v-else-if="error" class="centrado">
        <ion-icon :icon="alertCircleOutline" class="icono-error" />
        <p>{{ error }}</p>
        <ion-button fill="outline" size="small" @click="cargar">Reintentar</ion-button>
      </div>

      <template v-else-if="detalle">
        <!-- Encabezado -->
        <header class="sciaf-hero">
          <codigo-activo :codigo="detalle.ficha.codigoVisual" grande class="codigo-blanco" />
          <p class="descripcion">{{ detalle.ficha.descripcion }}</p>
          <div class="chips">
            <ion-chip :color="colorEstado" class="chip">{{ detalle.ficha.estado ?? '—' }}</ion-chip>
            <ion-chip v-if="detalle.ficha.estadoFisico" color="light" class="chip">
              {{ detalle.ficha.estadoFisico }}
            </ion-chip>
          </div>
        </header>

        <!-- Pestañas -->
        <div class="barra-pestanas">
          <ion-segment v-model="pestana" scrollable :value="pestana">
            <ion-segment-button value="datos"><ion-label>Datos</ion-label></ion-segment-button>
            <ion-segment-button value="ubicacion"><ion-label>Ubicación</ion-label></ion-segment-button>
            <ion-segment-button value="historial">
              <ion-label>Historial{{ contador(detalle.historial) }}</ion-label>
            </ion-segment-button>
            <ion-segment-button value="transferencias">
              <ion-label>Transferencias{{ contador(detalle.transferencias) }}</ion-label>
            </ion-segment-button>
            <ion-segment-button value="asignaciones">
              <ion-label>Asignaciones{{ contador(detalle.asignaciones) }}</ion-label>
            </ion-segment-button>
            <ion-segment-button value="mantenimientos">
              <ion-label>Mantenimiento{{ contador(detalle.mantenimientos) }}</ion-label>
            </ion-segment-button>
          </ion-segment>
        </div>

        <div class="panel">
          <!-- DATOS / UBICACIÓN -->
          <dl v-if="pestana === 'datos' || pestana === 'ubicacion'" class="lista-datos">
            <div v-for="c in (pestana === 'datos' ? datosGenerales : datosUbicacion)" :key="c.etiqueta" class="campo">
              <dt>{{ c.etiqueta }}</dt>
              <dd :class="{ vacio: !c.valor }">{{ c.valor || '—' }}</dd>
            </div>
          </dl>

          <!-- HISTORIAL -->
          <div v-else-if="pestana === 'historial'">
            <p v-if="!detalle.historial.length" class="sin-datos texto-suave">Sin eventos registrados</p>
            <ol v-else class="linea-tiempo">
              <li v-for="h in detalle.historial" :key="h.idHistorial">
                <span class="punto"></span>
                <div class="evento">
                  <div class="fila-evento">
                    <strong>{{ legible(h.tipoEvento) }}</strong>
                    <small>{{ fechaHora(h.fecha) }}</small>
                  </div>
                  <p v-if="h.descripcion">{{ h.descripcion }}</p>
                  <p v-if="h.oficinaAnterior || h.oficinaNueva" class="cambio">
                    {{ h.oficinaAnterior ?? '—' }} <ion-icon :icon="arrowForwardOutline" /> {{ h.oficinaNueva ?? '—' }}
                  </p>
                  <p v-if="h.responsableAnterior || h.responsableNuevo" class="cambio">
                    {{ h.responsableAnterior ?? '—' }} <ion-icon :icon="arrowForwardOutline" /> {{ h.responsableNuevo ?? '—' }}
                  </p>
                  <small v-if="h.usuario" class="texto-suave">Por {{ h.usuario }}</small>
                </div>
              </li>
            </ol>
          </div>

          <!-- TRANSFERENCIAS -->
          <div v-else-if="pestana === 'transferencias'">
            <p v-if="!detalle.transferencias.length" class="sin-datos texto-suave">Este activo no ha sido transferido</p>
            <div v-for="(t, i) in detalle.transferencias" :key="i" class="sciaf-card tarjeta">
              <div class="fila-evento">
                <strong>{{ t.numero ?? 'Sin número' }}</strong>
                <small>{{ fecha(t.fecha) }}</small>
              </div>
              <p class="cambio">{{ t.oficinaOrigen ?? '—' }} <ion-icon :icon="arrowForwardOutline" /> {{ t.oficinaDestino ?? '—' }}</p>
              <div class="etiquetas">
                <ion-chip v-if="t.tipo" color="light" class="chip">{{ t.tipo }}</ion-chip>
                <ion-chip v-if="t.estado" color="light" class="chip">{{ t.estado }}</ion-chip>
              </div>
            </div>
          </div>

          <!-- ASIGNACIONES -->
          <div v-else-if="pestana === 'asignaciones'">
            <p v-if="!detalle.asignaciones.length" class="sin-datos texto-suave">Este activo no aparece en actas de asignación</p>
            <div v-for="(a, i) in detalle.asignaciones" :key="i" class="sciaf-card tarjeta">
              <div class="fila-evento">
                <strong>{{ a.codigoCompleto ?? a.numero ?? 'Sin número' }}</strong>
                <small>{{ fechaHora(a.fecha) }}</small>
              </div>
              <p v-if="a.responsable">{{ a.responsable }}</p>
              <p v-if="a.oficinaDestino" class="texto-suave">{{ a.oficinaDestino }}</p>
              <div class="etiquetas">
                <ion-chip v-if="a.tipo" color="light" class="chip">{{ a.tipo }}</ion-chip>
                <ion-chip v-if="a.estado" color="light" class="chip">{{ a.estado }}</ion-chip>
              </div>
            </div>
          </div>

          <!-- MANTENIMIENTOS -->
          <div v-else>
            <p v-if="!detalle.mantenimientos.length" class="sin-datos texto-suave">Sin mantenimientos registrados</p>
            <div v-for="m in detalle.mantenimientos" :key="m.idMantenimiento" class="sciaf-card tarjeta">
              <div class="fila-evento">
                <strong>{{ m.tipo ?? 'Mantenimiento' }}</strong>
                <small>{{ fechaHora(m.fecha) }}</small>
              </div>
              <p v-if="m.problema"><span class="texto-suave">Problema:</span> {{ m.problema }}</p>
              <p v-if="m.solucion"><span class="texto-suave">Solución:</span> {{ m.solucion }}</p>
              <p v-if="m.responsableTecnico" class="texto-suave">Técnico: {{ m.responsableTecnico }}</p>
              <p v-if="m.proximaFecha" class="texto-suave">Próximo: {{ fecha(m.proximaFecha) }}</p>
            </div>
          </div>
        </div>
      </template>
    </ion-content>
  </ion-page>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';
import {
  IonPage, IonHeader, IonToolbar, IonTitle, IonContent, IonButtons, IonBackButton,
  IonSpinner, IonIcon, IonButton, IonChip, IonSegment, IonSegmentButton, IonLabel,
} from '@ionic/vue';
import { alertCircleOutline, arrowForwardOutline } from 'ionicons/icons';
import { activosApi, type ActivoDetalle, type Referencia } from '@/services/activos';
import { mensajeDeError } from '@/services/http';
import CodigoActivo from '@/components/CodigoActivo.vue';

const route = useRoute();
const detalle = ref<ActivoDetalle | null>(null);
const cargando = ref(true);
const error = ref<string | null>(null);
const pestana = ref('datos');

onMounted(cargar);

async function cargar() {
  cargando.value = true;
  error.value = null;
  try {
    detalle.value = await activosApi.detalle(String(route.params.codigo));
  } catch (e) {
    error.value = mensajeDeError(e, 'No se pudo cargar la ficha del activo');
  } finally {
    cargando.value = false;
  }
}

const colorEstado = computed(() => {
  const e = detalle.value?.ficha.estado;
  if (e === 'ACTIVO') return 'success';
  if (e === 'PENDIENTE') return 'warning';
  return 'danger';
});

/* ── Campos de las pestañas de datos ─────────────────────────────────────── */

interface Campo {
  etiqueta: string;
  valor: string | null | undefined;
}

const datosGenerales = computed<Campo[]>(() => {
  const f = detalle.value?.ficha;
  if (!f) return [];
  return [
    { etiqueta: 'Código', valor: f.codigoVisual },
    { etiqueta: 'Descripción', valor: f.descripcion },
    { etiqueta: 'Grupo contable', valor: nombreRef(f.grupoContable) },
    { etiqueta: 'Auxiliar', valor: nombreRef(f.auxiliar) },
    { etiqueta: 'Organismo financiador', valor: nombreRef(f.organismoFinanciero) },
    { etiqueta: 'Costo', valor: moneda(f.costo) },
    { etiqueta: 'Depreciación acumulada', valor: moneda(f.depreciacionAcum) },
    { etiqueta: 'Vida útil', valor: f.vidaUtil ? `${f.vidaUtil} años` : null },
    { etiqueta: 'Fecha de adquisición', valor: fecha(f.fechaAdquisicion) },
    { etiqueta: 'Observaciones', valor: f.observaciones },
    {
      etiqueta: 'Última modificación',
      valor: juntar(fecha(f.fechaUltimaModificacion), f.usuarioUltimaModificacion),
    },
  ];
});

const datosUbicacion = computed<Campo[]>(() => {
  const f = detalle.value?.ficha;
  if (!f) return [];
  return [
    { etiqueta: 'Oficina', valor: f.ubicacion?.oficina },
    { etiqueta: 'Predio', valor: f.ubicacion?.predio },
    { etiqueta: 'Unidad', valor: f.ubicacion?.unidad },
    { etiqueta: 'Municipio', valor: f.ubicacion?.municipio ?? f.ubicacion?.ciudad },
    { etiqueta: 'Entidad', valor: f.ubicacion?.entidad },
    { etiqueta: 'Responsable', valor: f.responsable?.nombre },
    { etiqueta: 'Cargo', valor: f.responsable?.cargo },
    { etiqueta: 'C.I.', valor: f.responsable?.ci },
  ];
});

/* ── Formato ─────────────────────────────────────────────────────────────── */

function nombreRef(r: Referencia | null | undefined): string | null {
  if (!r) return null;
  return r.codigo ? `${r.codigo} · ${r.nombre ?? ''}`.trim() : r.nombre;
}

function moneda(v: number | null | undefined): string | null {
  if (v === null || v === undefined) return null;
  return `Bs ${v.toLocaleString('es-BO', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

function fecha(v: string | null | undefined): string | null {
  if (!v) return null;
  return new Date(v).toLocaleDateString('es-BO', { day: '2-digit', month: 'short', year: 'numeric' });
}

function fechaHora(v: string | null | undefined): string {
  if (!v) return '';
  return new Date(v).toLocaleString('es-BO', {
    day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit',
  });
}

function juntar(...partes: (string | null | undefined)[]): string | null {
  const utiles = partes.filter(Boolean);
  return utiles.length ? utiles.join(' · ') : null;
}

/** ASIGNACION_ACTIVO → "Asignación activo" */
function legible(tipo: string): string {
  const texto = tipo.replace(/_/g, ' ').toLowerCase();
  return texto.charAt(0).toUpperCase() + texto.slice(1);
}

function contador(lista: unknown[]): string {
  return lista.length ? ` (${lista.length})` : '';
}
</script>

<style scoped>
.centrado {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 72px 32px;
  text-align: center;
}

.icono-error {
  font-size: 2.4rem;
  color: var(--ion-color-danger);
}

/* ── Encabezado ───────────────────────────────────────────────────────────── */
.codigo-blanco {
  color: #fff;
}

.codigo-blanco :deep(.prefijo) {
  color: rgba(255, 255, 255, 0.6);
}

.descripcion {
  margin: 10px 0 0;
  font-size: 0.9rem;
  line-height: 1.45;
  opacity: 0.95;
}

.chips {
  margin-top: 12px;
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.chip {
  height: 24px;
  font-size: 0.7rem;
  margin: 0;
}

/* ── Pestañas ─────────────────────────────────────────────────────────────── */
.barra-pestanas {
  position: sticky;
  top: 0;
  z-index: 5;
  background: var(--ion-background-color);
  padding: 8px 0 4px;
}

ion-segment {
  --background: transparent;
}

ion-segment-button {
  --indicator-color: var(--ion-color-primary);
  min-width: 108px;
  font-size: 0.78rem;
  text-transform: none;
}

.panel {
  padding: 8px 16px 40px;
}

/* ── Lista de datos ───────────────────────────────────────────────────────── */
.lista-datos {
  margin: 0;
  background: var(--ion-card-background);
  border-radius: var(--sciaf-radio);
  box-shadow: var(--sciaf-sombra);
  overflow: hidden;
}

.lista-datos :deep(.campo) {
  padding: 12px 16px;
  border-bottom: 1px solid var(--sciaf-borde);
}

.lista-datos :deep(.campo:last-child) {
  border-bottom: none;
}

.lista-datos :deep(dt) {
  font-size: 0.68rem;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: var(--sciaf-texto-suave);
  margin-bottom: 3px;
}

.lista-datos :deep(dd) {
  margin: 0;
  font-size: 0.9rem;
  line-height: 1.4;
}

.lista-datos :deep(dd.vacio) {
  color: var(--sciaf-texto-suave);
}

/* ── Línea de tiempo ──────────────────────────────────────────────────────── */
.linea-tiempo {
  list-style: none;
  margin: 0;
  padding: 8px 0 0 6px;
}

.linea-tiempo li {
  position: relative;
  padding: 0 0 22px 22px;
  border-left: 2px solid var(--sciaf-borde);
}

.linea-tiempo li:last-child {
  border-left-color: transparent;
  padding-bottom: 4px;
}

.punto {
  position: absolute;
  left: -7px;
  top: 3px;
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background: var(--ion-color-primary);
  border: 2px solid var(--ion-background-color);
}

.evento p {
  margin: 4px 0 0;
  font-size: 0.85rem;
  line-height: 1.45;
}

.fila-evento {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
}

.fila-evento strong {
  font-size: 0.9rem;
}

.fila-evento small {
  font-size: 0.7rem;
  color: var(--sciaf-texto-suave);
  white-space: nowrap;
}

.cambio {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  font-size: 0.82rem !important;
}

.cambio ion-icon {
  color: var(--sciaf-texto-suave);
  flex-shrink: 0;
}

/* ── Tarjetas ─────────────────────────────────────────────────────────────── */
.tarjeta {
  margin-bottom: 10px;
}

.tarjeta p {
  margin: 6px 0 0;
  font-size: 0.85rem;
  line-height: 1.45;
}

.etiquetas {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  margin-top: 10px;
}

.sin-datos {
  text-align: center;
  padding: 48px 24px;
}
</style>
