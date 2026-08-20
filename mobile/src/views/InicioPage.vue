<template>
  <ion-page>
    <ion-content :fullscreen="true">
      <!-- Saludo + identidad + estado de conexión -->
      <header class="sciaf-hero">
        <div class="fila">
          <div>
            <p class="saludo">{{ saludo }}</p>
            <h1>{{ primerNombre }}</h1>
            <p class="rol">{{ auth.usuario?.rol }}</p>
          </div>
          <img :src="logoActivos" alt="Activos Fijos" class="logo" />
        </div>

        <div class="chip-estado" :class="red.enLinea ? 'en-linea' : 'sin-conexion'">
          <span class="punto"></span>
          {{ red.enLinea ? 'En línea' : 'Sin conexión' }}
        </div>
      </header>

      <div class="cuerpo">
        <p class="titulo-seccion">Accesos rápidos</p>

        <div class="rejilla">
          <button
            v-for="a in accesos"
            :key="a.ruta"
            class="acceso"
            :class="{ inactivo: !a.disponible }"
            @click="ir(a)"
          >
            <span class="icono"><ion-icon :icon="a.icono" /></span>
            <span class="nombre">{{ a.nombre }}</span>
            <span v-if="!a.disponible" class="proximamente">Próximamente</span>
          </button>
        </div>

        <!-- Diagnóstico: comprueba token + servidor de extremo a extremo -->
        <p class="titulo-seccion">Conexión con el servidor</p>
        <div class="sciaf-card diagnostico">
          <div class="detalle">
            <strong>{{ servidor }}</strong>
            <span class="texto-suave">{{ resultadoPing }}</span>
          </div>
          <ion-button size="small" fill="outline" :disabled="probando" @click="probar">
            <ion-spinner v-if="probando" name="crescent" />
            <span v-else>Probar</span>
          </ion-button>
        </div>
      </div>
    </ion-content>
  </ion-page>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import { useRouter } from 'vue-router';
import { IonPage, IonContent, IonIcon, IonButton, IonSpinner } from '@ionic/vue';
import {
  qrCodeOutline, searchOutline, documentTextOutline, layersOutline,
  clipboardOutline, notificationsOutline,
} from 'ionicons/icons';
import { useAuthStore } from '@/stores/auth';
import { useRedStore } from '@/stores/red';
import { http, API_BASE, mensajeDeError } from '@/services/http';
import logoActivos from '@/assets/logo-activos.png';

const auth = useAuthStore();
const red = useRedStore();
const router = useRouter();

const primerNombre = computed(() => {
  const completo = auth.usuario?.nombreCompleto ?? auth.usuario?.usuario ?? '';
  return completo.split(' ')[0] || completo;
});

const saludo = computed(() => {
  const h = new Date().getHours();
  if (h < 12) return 'Buenos días,';
  if (h < 19) return 'Buenas tardes,';
  return 'Buenas noches,';
});

const servidor = computed(() => API_BASE || 'servidor de desarrollo');

interface Acceso {
  nombre: string;
  icono: string;
  ruta: string;
  disponible: boolean;
}

const accesos: Acceso[] = [
  { nombre: 'Escanear activo', icono: qrCodeOutline,      ruta: '/tabs/escaner',        disponible: false },
  { nombre: 'Buscar',          icono: searchOutline,      ruta: '/tabs/buscar',         disponible: false },
  { nombre: 'Emitir informe',  icono: documentTextOutline, ruta: '/tabs/escaner',       disponible: false },
  { nombre: 'Explorar',        icono: layersOutline,      ruta: '/tabs/buscar',         disponible: false },
  { nombre: 'Asignaciones',    icono: clipboardOutline,   ruta: '/tabs/mas',            disponible: false },
  { nombre: 'Avisos',          icono: notificationsOutline, ruta: '/tabs/notificaciones', disponible: false },
];

function ir(a: Acceso) {
  router.push(a.ruta);
}

const probando = ref(false);
const resultadoPing = ref('Sin comprobar');

async function probar() {
  probando.value = true;
  try {
    const { data } = await http.get('/ping');
    resultadoPing.value = `Sesión válida · ${data.usuario} · ${data.permisos} permisos`;
  } catch (e) {
    resultadoPing.value = mensajeDeError(e, 'No se pudo contactar al servidor');
  } finally {
    probando.value = false;
  }
}
</script>

<style scoped>
.fila {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.saludo {
  margin: 0;
  font-size: 0.85rem;
  opacity: 0.85;
}

h1 {
  margin: 2px 0 0;
  font-size: 1.6rem;
  font-weight: 800;
}

.rol {
  margin: 2px 0 0;
  font-size: 0.75rem;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  opacity: 0.8;
}

.logo {
  width: 52px;
  height: auto;
  background: #fff;
  border-radius: 12px;
  padding: 5px;
}

.sciaf-hero .chip-estado {
  margin-top: 16px;
  background: rgba(255, 255, 255, 0.16);
  color: #fff;
}

.cuerpo {
  padding: 0 16px 32px;
}

.rejilla {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}

.acceso {
  background: var(--ion-card-background);
  border: none;
  border-radius: var(--sciaf-radio);
  box-shadow: var(--sciaf-sombra);
  padding: 16px 8px 12px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  min-height: 104px;
  cursor: pointer;
  color: var(--ion-text-color);
}

.acceso .icono {
  width: 40px;
  height: 40px;
  border-radius: 12px;
  background: rgba(20, 67, 145, 0.09);
  display: grid;
  place-items: center;
}

.acceso .icono ion-icon {
  font-size: 21px;
  color: var(--ion-color-primary);
}

.acceso .nombre {
  font-size: 0.75rem;
  font-weight: 600;
  text-align: center;
  line-height: 1.25;
}

.acceso.inactivo {
  opacity: 0.55;
}

.proximamente {
  font-size: 0.6rem;
  color: var(--sciaf-texto-suave);
}

.diagnostico {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.detalle {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.detalle strong {
  font-size: 0.85rem;
  word-break: break-all;
}
</style>
