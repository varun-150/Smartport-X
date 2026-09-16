import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    port: 3000,
    proxy: {
      // Everything REST goes through the Spring Cloud Gateway edge (port 8080).
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      // STOMP over SockJS is also proxied through the gateway /ws/** route.
      '/ws': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        ws: true,
      },
    },
  },
})