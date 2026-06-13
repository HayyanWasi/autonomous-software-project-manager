import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

/**
 * Vite config — Zeptex frontend.
 * Dev server proxies /api to the Spring Boot backend (com.autonomouspm) on :8080,
 * so the SSE stream and REST calls are same-origin during development.
 */
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
