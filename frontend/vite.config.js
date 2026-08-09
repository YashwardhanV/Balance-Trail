import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';
import { defineConfig } from 'vite';
export default defineConfig({
    plugins: [react(), tailwindcss()],
    server: {
        port: 5173,
        proxy: {
            '/auth': 'http://localhost:8080',
            '/reconciliations': 'http://localhost:8080',
            '/actuator': 'http://localhost:8080',
            '/v3': 'http://localhost:8080',
            '/swagger-ui': 'http://localhost:8080',
        },
    },
});
