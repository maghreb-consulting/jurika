import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    // Bind 0.0.0.0 → l'app reste joignable depuis localhost ET depuis
    // l'IP LAN du serveur (demo cabinet, app.jurika.ai en prod).
    host: true,
    port: 5173,
    strictPort: true,
  },
})
