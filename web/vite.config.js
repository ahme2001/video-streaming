import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    // The API is proxied rather than called across origins, so the browser sees one origin
    // and the Spring side needs no CORS configuration.
    proxy: {
      '/api': 'http://localhost:8081',
    },
  },
})
