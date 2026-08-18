import { fileURLToPath, URL } from 'node:url';

import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      // Not `path.resolve(__dirname, …)`: package.json sets "type": "module", so
      // this config is loaded as ESM where __dirname does not exist.
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    // `npm run dev` talks to a backend started outside compose (localhost:8080).
    // Proxying here keeps the dev server same-origin, so the app exercises the
    // same relative /api/v1 paths Nginx serves in the container — no baseURL
    // switch between environments.
    //
    // MEDPAY_CORS_ALLOWED_ORIGINS exists for the case where someone bypasses
    // this proxy and points the app straight at :8080.
    proxy: {
      '/api/v1': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/actuator': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  build: {
    outDir: 'dist',
    sourcemap: true,
  },
});
