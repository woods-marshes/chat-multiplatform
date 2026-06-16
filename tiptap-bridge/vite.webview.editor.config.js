import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import { fileURLToPath } from 'url'
import { viteSingleFile } from 'vite-plugin-singlefile'

export default defineConfig({
  plugins: [react(), tailwindcss(), viteSingleFile()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  root: './src/webview',
  build: {
    outDir: '../../dist-webview/editor',
    emptyOutDir: false,
    rollupOptions: {
      input: {
        editor: fileURLToPath(new URL('./src/webview/editor.html', import.meta.url)),
      },
    },
  },
})
