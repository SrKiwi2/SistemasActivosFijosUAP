import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';
import path from 'path';

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  server: {
    port: 5173,
    // En desarrollo el navegador pega a /api/movil del propio Vite y este lo
    // reenvía al backend: así se prueba sin depender de la configuración CORS.
    proxy: {
      '/api': {
        target: process.env.VITE_DEV_BACKEND ?? 'http://localhost:9696',
        changeOrigin: true,
      },
    },
  },
});
