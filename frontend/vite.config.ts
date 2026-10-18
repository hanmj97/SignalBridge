import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    host: '0.0.0.0',
    port: 5173,
    strictPort: true,
    // Within Docker Compose, Vite can reach the Spring Boot container by service name.
    // The browser calls /api/v1/... on port 5173, then Vite forwards to api:8080.
    proxy: { '/api': { target: 'http://api:8080', changeOrigin: true } },
  },
});
