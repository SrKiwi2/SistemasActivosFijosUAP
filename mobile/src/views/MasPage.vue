<template>
  <ion-page>
    <ion-header class="ion-no-border">
      <ion-toolbar>
        <ion-title>Más</ion-title>
      </ion-toolbar>
    </ion-header>

    <ion-content :fullscreen="true">
      <!-- Perfil -->
      <div class="perfil">
        <div class="avatar">{{ iniciales }}</div>
        <div>
          <strong>{{ auth.usuario?.nombreCompleto }}</strong>
          <span class="texto-suave">{{ auth.usuario?.usuario }} · {{ auth.usuario?.rol }}</span>
        </div>
      </div>

      <p class="titulo-seccion">Módulos</p>
      <ion-list inset>
        <ion-item v-for="m in modulos" :key="m.nombre" :detail="false" class="tocable">
          <ion-icon slot="start" :icon="m.icono" :color="m.disponible ? 'primary' : 'medium'" />
          <ion-label>
            <h3>{{ m.nombre }}</h3>
            <p>{{ m.detalle }}</p>
          </ion-label>
          <ion-note slot="end">{{ m.disponible ? '' : m.fase }}</ion-note>
        </ion-item>
      </ion-list>

      <p class="titulo-seccion">Aplicación</p>
      <ion-list inset>
        <ion-item :detail="false">
          <ion-icon slot="start" :icon="serverOutline" color="medium" />
          <ion-label>
            <h3>Servidor</h3>
            <p>{{ servidor }}</p>
          </ion-label>
        </ion-item>
        <ion-item :detail="false">
          <ion-icon slot="start" :icon="informationCircleOutline" color="medium" />
          <ion-label>
            <h3>Versión</h3>
            <p>{{ version }}</p>
          </ion-label>
        </ion-item>
        <ion-item :detail="false">
          <ion-icon
            slot="start"
            :icon="red.enLinea ? wifiOutline : cloudOfflineOutline"
            :color="red.enLinea ? 'success' : 'warning'"
          />
          <ion-label>
            <h3>Conexión</h3>
            <p>{{ red.enLinea ? `En línea (${red.tipo})` : 'Sin conexión' }}</p>
          </ion-label>
        </ion-item>
      </ion-list>

      <div class="zona-salir">
        <ion-button expand="block" fill="outline" color="danger" @click="confirmarSalida">
          <ion-icon slot="start" :icon="logOutOutline" />
          Cerrar sesión
        </ion-button>
        <p class="texto-suave nota">
          La sesión se mantiene abierta indefinidamente. Solo se cierra aquí o si un
          administrador revoca este dispositivo.
        </p>
      </div>

      <footer class="pie">
        <img :src="logoUap" alt="Universidad Amazónica de Pando" />
        <span class="texto-suave">Universidad Amazónica de Pando<br />Dirección Administrativa Financiera</span>
      </footer>
    </ion-content>
  </ion-page>
</template>

<script setup lang="ts">
import { computed, ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import {
  IonPage, IonHeader, IonToolbar, IonTitle, IonContent, IonList, IonItem,
  IonLabel, IonIcon, IonNote, IonButton, alertController,
} from '@ionic/vue';
import {
  qrCodeOutline, searchOutline, documentTextOutline, clipboardOutline,
  notificationsOutline, serverOutline, informationCircleOutline, wifiOutline,
  cloudOfflineOutline, logOutOutline,
} from 'ionicons/icons';
import { App } from '@capacitor/app';
import { useAuthStore } from '@/stores/auth';
import { useRedStore } from '@/stores/red';
import { API_BASE } from '@/services/http';
import logoUap from '@/assets/logo-uap.png';

const auth = useAuthStore();
const red = useRedStore();
const router = useRouter();

const iniciales = computed(() => {
  const partes = (auth.usuario?.nombreCompleto ?? '?').trim().split(/\s+/);
  return partes.slice(0, 2).map((p) => p.charAt(0).toUpperCase()).join('');
});

const servidor = computed(() => API_BASE || 'desarrollo (proxy de Vite)');

const version = ref('1.0.0');
onMounted(async () => {
  try {
    const info = await App.getInfo();
    version.value = `${info.version} (${info.build})`;
  } catch {
    /* en navegador no está disponible */
  }
});

const modulos = [
  { nombre: 'Escáner de activos', detalle: 'Lectura QR y ficha completa',        icono: qrCodeOutline,       fase: 'Fase 2', disponible: false },
  { nombre: 'Búsqueda',           detalle: 'Filtros por código y descripción',   icono: searchOutline,       fase: 'Fase 3', disponible: false },
  { nombre: 'Informes',           detalle: 'Captura de códigos y PDF',           icono: documentTextOutline, fase: 'Fase 5', disponible: false },
  { nombre: 'Asignaciones',       detalle: 'Pendientes y subidas al VSIAF',      icono: clipboardOutline,    fase: 'Fase 6', disponible: false },
  { nombre: 'Notificaciones',     detalle: 'Eventos del sistema web',            icono: notificationsOutline, fase: 'Fase 7', disponible: false },
];

async function confirmarSalida() {
  const alerta = await alertController.create({
    header: 'Cerrar sesión',
    message: 'Tendrá que escribir su usuario y contraseña la próxima vez.',
    buttons: [
      { text: 'Cancelar', role: 'cancel' },
      {
        text: 'Cerrar sesión',
        role: 'destructive',
        handler: async () => {
          await auth.logout();
          router.replace({ name: 'login' });
        },
      },
    ],
  });
  await alerta.present();
}
</script>

<style scoped>
.perfil {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 20px 20px 4px;
}

.avatar {
  width: 52px;
  height: 52px;
  border-radius: 16px;
  background: var(--sciaf-degradado);
  color: #fff;
  display: grid;
  place-items: center;
  font-weight: 700;
  font-size: 1.05rem;
}

.perfil div {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.perfil strong {
  font-size: 1rem;
}

ion-list {
  --ion-item-background: var(--ion-card-background);
  border-radius: var(--sciaf-radio);
  overflow: hidden;
}

ion-item h3 {
  font-size: 0.9rem;
  font-weight: 600;
  margin: 0;
}

ion-item p {
  font-size: 0.78rem;
  color: var(--sciaf-texto-suave);
  margin: 2px 0 0;
}

.zona-salir {
  padding: 24px 20px 8px;
}

.nota {
  margin: 10px 2px 0;
  font-size: 0.75rem;
  line-height: 1.45;
}

.pie {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 24px 20px 36px;
  text-align: left;
}

.pie img {
  width: 40px;
  height: auto;
}

.pie span {
  font-size: 0.7rem;
  line-height: 1.4;
}
</style>
