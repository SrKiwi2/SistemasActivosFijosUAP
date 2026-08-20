import { createApp } from 'vue';
import { createPinia } from 'pinia';
import { IonicVue } from '@ionic/vue';

import App from './App.vue';
import router from './router';
import { useAuthStore } from './stores/auth';
import { useRedStore } from './stores/red';
import { sesion } from './services/sesion';

/* Núcleo de Ionic */
import '@ionic/vue/css/core.css';
import '@ionic/vue/css/normalize.css';
import '@ionic/vue/css/structure.css';
import '@ionic/vue/css/typography.css';
import '@ionic/vue/css/padding.css';
import '@ionic/vue/css/flex-utils.css';
import '@ionic/vue/css/text-alignment.css';

/* Tema institucional SCIAF */
import './theme/variables.css';
import './theme/global.css';

const app = createApp(App)
  .use(createPinia())
  .use(IonicVue, { mode: 'md' })
  .use(router);

async function arrancar() {
  const auth = useAuthStore();
  const red = useRedStore();

  // Restaurar la sesión ANTES de montar: así el guard del router ya conoce el
  // estado real y no se ve un parpadeo de la pantalla de login en cada apertura.
  await auth.restaurar();
  red.iniciar().catch(() => { /* en navegador puede no estar disponible */ });

  // Único caso en que la sesión se cierra sin que el usuario lo pida: el
  // servidor rechazó el refresh (dispositivo revocado o usuario dado de baja).
  sesion.alExpirar(() => {
    auth.usuario = null;
    router.replace({ name: 'login' });
  });

  await router.isReady();
  app.mount('#app');
}

arrancar();
