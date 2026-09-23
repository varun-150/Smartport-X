import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  define: {
    // sockjs-client (UMD) references the Node-only free identifier `global`, which
    // breaks the bundle in the browser ("ReferenceError: global is not defined").
    // Redirect it to the standard browser global.
    global: 'globalThis',
  },
  resolve: {
    alias: {
      // Pin sockjs-client to its browser UMD build so Vite doesn't resolve the
      // main entry that leaks the Node `global` reference.
      'sockjs-client': 'sockjs-client/dist/sockjs.min.js',
    },
  },
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