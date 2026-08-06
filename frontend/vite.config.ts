/// <reference types="vitest" />
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { resolve } from 'path';

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src'),
    },
  },
  server: {
    port: 3000,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  build: {
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (!id.includes('node_modules')) return;
          // Heavy libs only needed on specific routes / user actions
          if (id.includes('xlsx')) {
            return 'vendor-xlsx';
          }
          if (id.includes('jszip')) {
            return 'vendor-jszip';
          }
          if (
            id.includes('/react/') ||
            id.includes('/react-dom/') ||
            id.includes('react-router') ||
            id.includes('/scheduler/')
          ) {
            return 'vendor-react';
          }
          if (id.includes('axios')) {
            return 'vendor-axios';
          }
          // recharts is NOT forced into a shared vendor chunk — that caused the
          // entry to statically import it. Route-level lazy loads keep it async.
          // Leave antd un-forced so route-only components land in async chunks.
        },
      },
    },
  },
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    css: false,
  },
});
