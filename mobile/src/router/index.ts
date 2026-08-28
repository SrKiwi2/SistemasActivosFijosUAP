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

  // ── Levantamiento de activos por oficina ──────────────────────────────────
  // Fuera de las pestañas: es un flujo largo con su propia pila de navegación,
  // y la barra inferior invitaría a abandonarlo a medias.
  {
    path: '/levantamiento',
    name: 'levantamiento',
    component: () => import('@/views/LevantamientoPrediosPage.vue'),
    meta: { permiso: 'MOV_INVENTARIO' },
  },
  {
    path: '/levantamiento/mios',
    name: 'levantamiento-mios',
    component: () => import('@/views/LevantamientosMiosPage.vue'),
    meta: { permiso: 'MOV_INVENTARIO' },
  },
  {
    path: '/levantamiento/predio/:idPredio(\\d+)',
    name: 'levantamiento-oficinas',
    component: () => import('@/views/LevantamientoOficinasPage.vue'),
    meta: { permiso: 'MOV_INVENTARIO' },
  },
  {
    path: '/levantamiento/:id(\\d+)',
    name: 'levantamiento-recorrido',
    component: () => import('@/views/LevantamientoRecorridoPage.vue'),
    meta: { permiso: 'MOV_INVENTARIO' },
  },
  // Pantalla completa: la cámara necesita todo el alto (ver EscanerCamaraPage).
  {
    path: '/levantamiento/:id(\\d+)/escanear',
    name: 'levantamiento-escaner',
    component: () => import('@/views/LevantamientoEscanerPage.vue'),
    meta: { permiso: 'MOV_INVENTARIO' },
  },
  {
    path: '/levantamiento/:id(\\d+)/resumen',
    name: 'levantamiento-resumen',
    component: () => import('@/views/LevantamientoResumenPage.vue'),
    meta: { permiso: 'MOV_INVENTARIO' },
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

  // Los permisos MOV_* deciden qué módulos existen para cada usuario. El
  // servidor los vuelve a comprobar en cada llamada —el token es una caché, no
  // la autoridad—; esto solo evita llegar a una pantalla que respondería 403.
  const permiso = to.meta.permiso as string | undefined;
  if (permiso && !auth.esAdministrador && !auth.puede(permiso)) {
    return { name: 'inicio' };
  }

  return true;
});

export default router;
