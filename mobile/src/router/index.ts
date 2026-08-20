import { createRouter, createWebHistory } from '@ionic/vue-router';
import type { RouteRecordRaw } from 'vue-router';
import { useAuthStore } from '@/stores/auth';

const routes: RouteRecordRaw[] = [
  { path: '/', redirect: '/login' },

  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/LoginPage.vue'),
    meta: { publica: true },
  },

  {
    path: '/tabs/',
    component: () => import('@/views/TabsPage.vue'),
    children: [
      { path: '', redirect: '/tabs/inicio' },
      { path: 'inicio',         name: 'inicio',         component: () => import('@/views/InicioPage.vue') },
      { path: 'buscar',         name: 'buscar',         component: () => import('@/views/BuscarPage.vue') },
      { path: 'escaner',        name: 'escaner',        component: () => import('@/views/EscanerPage.vue') },
      { path: 'notificaciones', name: 'notificaciones', component: () => import('@/views/NotificacionesPage.vue') },
      { path: 'mas',            name: 'mas',            component: () => import('@/views/MasPage.vue') },
    ],
  },

  // Pantalla completa, fuera de las pestañas: la cámara necesita todo el alto y
  // la barra inferior estorbaría al apuntar.
  {
    path: '/escaner/camara',
    name: 'escaner-camara',
    component: () => import('@/views/EscanerCamaraPage.vue'),
  },

  {
    path: '/activo/:codigo',
    name: 'activo-detalle',
    component: () => import('@/views/ActivoDetallePage.vue'),
  },

  { path: '/:pathMatch(.*)*', redirect: '/tabs/inicio' },
];

export const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
});

router.beforeEach((to) => {
  const auth = useAuthStore();

  if (!to.meta.publica && !auth.autenticado) {
    return { name: 'login' };
  }
  if (to.name === 'login' && auth.autenticado) {
    return { name: 'inicio' };
  }
  return true;
});

export default router;
