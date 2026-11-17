import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) }
  },
  server: {
    port: 5174,
    proxy: {
      '/auth': 'http://127.0.0.1:8090',
      '/books': 'http://127.0.0.1:8090',
      '/readers': 'http://127.0.0.1:8090',
      '/loans': 'http://127.0.0.1:8090',
      '/reservations': 'http://127.0.0.1:8090',
      '/reports': 'http://127.0.0.1:8090',
      '/settings': 'http://127.0.0.1:8090',
      '/logs': 'http://127.0.0.1:8090',
      '/health': 'http://127.0.0.1:8090'
    }
  },
  build: { outDir: 'dist', chunkSizeWarningLimit: 1200 }
})