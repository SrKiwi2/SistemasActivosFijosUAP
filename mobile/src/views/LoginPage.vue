<template>
  <ion-page>
    <ion-content :fullscreen="true" class="fondo-login">
      <div class="contenedor">
        <!-- Identidad: logo de la unidad sobre el degradado institucional -->
        <header class="cabecera">
          <div class="marco-logo">
            <img :src="logoActivos" alt="Activos Fijos" />
          </div>
          <h1>SCIAF</h1>
          <p>Sistema de Control de Activos Fijos</p>
        </header>

        <!-- Credenciales -->
        <form class="tarjeta" @submit.prevent="entrar">
          <h2>Iniciar sesión</h2>
          <p class="texto-suave ayuda">Use las mismas credenciales del sistema web</p>

          <ion-input
            v-model="usuario"
            label="Usuario"
            label-placement="stacked"
            fill="outline"
            autocapitalize="off"
            autocomplete="username"
            autocorrect="off"
            :spellcheck="false"
            enterkeyhint="next"
            :disabled="auth.cargando"
            class="campo"
          />

          <ion-input
            v-model="contrasena"
            label="Contraseña"
            label-placement="stacked"
            fill="outline"
            :type="verClave ? 'text' : 'password'"
            autocomplete="current-password"
            enterkeyhint="go"
            :disabled="auth.cargando"
            class="campo"
          >
            <ion-button
              slot="end"
              fill="clear"
              type="button"
              :aria-label="verClave ? 'Ocultar contraseña' : 'Mostrar contraseña'"
              @click="verClave = !verClave"
            >
              <ion-icon slot="icon-only" :icon="verClave ? eyeOffOutline : eyeOutline" />
            </ion-button>
          </ion-input>

          <!-- El error se comunica con color + icono + texto, nunca solo color -->
          <div v-if="auth.error" class="aviso-error" role="alert">
            <ion-icon :icon="alertCircleOutline" />
            <span>{{ auth.error }}</span>
          </div>

          <ion-button
            type="submit"
            expand="block"
            size="large"
            :disabled="!formularioValido || auth.cargando"
          >
            <ion-spinner v-if="auth.cargando" name="crescent" />
            <span v-else>Entrar</span>
          </ion-button>

          <div v-if="!red.enLinea" class="chip-estado sin-conexion sin-red">
            <span class="punto"></span> Sin conexión — no se puede iniciar sesión
          </div>
        </form>

        <!-- Institución -->
        <footer class="pie">
          <img :src="logoUap" alt="Universidad Amazónica de Pando" />
          <div>
            <strong>Universidad Amazónica de Pando</strong>
            <span>Dirección Administrativa Financiera</span>
          </div>
        </footer>
      </div>
    </ion-content>
  </ion-page>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import { useRouter } from 'vue-router';
import {
  IonPage, IonContent, IonInput, IonButton, IonIcon, IonSpinner,
} from '@ionic/vue';
import { eyeOutline, eyeOffOutline, alertCircleOutline } from 'ionicons/icons';
import { useAuthStore } from '@/stores/auth';
import { useRedStore } from '@/stores/red';
import logoActivos from '@/assets/logo-activos.png';
import logoUap from '@/assets/logo-uap.png';

const auth = useAuthStore();
const red = useRedStore();
const router = useRouter();

const usuario = ref('');
const contrasena = ref('');
const verClave = ref(false);

const formularioValido = computed(
  () => usuario.value.trim().length > 0 && contrasena.value.length > 0,
);

async function entrar() {
  if (!formularioValido.value) return;
  const ok = await auth.login(usuario.value, contrasena.value);
  if (ok) {
    contrasena.value = '';
    router.replace({ name: 'inicio' });
  }
}
</script>

<style scoped>
.fondo-login {
  --background: var(--ion-background-color);
}

.contenedor {
  min-height: 100%;
  display: flex;
  flex-direction: column;
}

/* ── Cabecera ─────────────────────────────────────────────────────────────── */
.cabecera {
  background: var(--sciaf-degradado);
  color: #fff;
  padding: 48px 24px 56px;
  border-bottom-left-radius: 32px;
  border-bottom-right-radius: 32px;
  text-align: center;
  position: relative;
}

.marco-logo {
  width: 116px;
  height: 116px;
  margin: 0 auto 18px;
  border-radius: 28px;
  background: #fff;
  display: grid;
  place-items: center;
  padding: 12px;
  box-shadow: 0 10px 24px rgba(0, 0, 0, 0.18);
}

.marco-logo img {
  width: 100%;
  height: auto;
}

.cabecera h1 {
  margin: 0;
  font-size: 2rem;
  font-weight: 800;
  letter-spacing: 0.06em;
}

.cabecera p {
  margin: 6px 0 0;
  font-size: 0.875rem;
  opacity: 0.85;
}

/* Filo rojo de marca: identidad presente sin competir con el azul de acción */
.cabecera::after {
  content: '';
  position: absolute;
  left: 50%;
  bottom: 26px;
  transform: translateX(-50%);
  width: 56px;
  height: 3px;
  border-radius: 2px;
  background: var(--sciaf-rojo);
}

/* ── Formulario ───────────────────────────────────────────────────────────── */
.tarjeta {
  background: var(--ion-card-background);
  border-radius: var(--sciaf-radio);
  box-shadow: var(--sciaf-sombra);
  margin: -28px 20px 0;
  padding: 24px 20px;
  position: relative;
  z-index: 1;
}

.tarjeta h2 {
  margin: 0;
  font-size: 1.25rem;
  font-weight: 700;
}

.ayuda {
  margin: 4px 0 20px;
}

.campo {
  margin-bottom: 16px;
  --border-radius: 12px;
}

.aviso-error {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  background: rgba(217, 45, 32, 0.1);
  color: var(--ion-color-danger);
  border-radius: 12px;
  padding: 10px 12px;
  font-size: 0.875rem;
  margin-bottom: 16px;
}

.aviso-error ion-icon {
  font-size: 1.15rem;
  flex-shrink: 0;
}

.sin-red {
  margin-top: 14px;
  width: 100%;
  justify-content: center;
}

/* ── Pie institucional ────────────────────────────────────────────────────── */
.pie {
  margin-top: auto;
  padding: 32px 24px 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
}

.pie img {
  width: 46px;
  height: auto;
}

.pie div {
  display: flex;
  flex-direction: column;
  line-height: 1.3;
}

.pie strong {
  font-size: 0.8rem;
  color: var(--ion-text-color);
}

.pie span {
  font-size: 0.7rem;
  color: var(--sciaf-texto-suave);
}
</style>
