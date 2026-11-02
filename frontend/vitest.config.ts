import react from '@vitejs/plugin-react';
import { defineConfig } from 'vitest/config';

/**
 * Test configuration, kept separate from vite.config.ts.
 *
 * Vitest resolves its own copy of Vite, so declaring the `test` block inside
 * vite.config.ts makes TypeScript compare two different Vite type identities
 * and fail. Two files keep both type-clean.
 */
export default defineConfig({
  plugins: [react()],
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    css: false,
    include: ['src/**/*.{test,spec}.{ts,tsx}'],
  },
});
