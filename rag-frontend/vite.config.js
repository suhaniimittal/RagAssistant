import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// Frontend and backend run as two fully separate processes now -- the
// frontend talks to Spring Boot over a real, direct URL (see
// src/api/config.js), so no dev-server proxy is needed here. CORS on the
// backend (SecurityConfig.java) is what actually allows that cross-origin
// request through.
export default defineConfig({
  plugins: [react()],
})
