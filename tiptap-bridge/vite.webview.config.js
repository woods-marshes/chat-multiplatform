import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import { fileURLToPath } from 'url'
import { viteSingleFile } from 'vite-plugin-singlefile'

export default defineConfig({
  plugins: [react(), tailwindcss(), viteSingleFile()], // 🟢 关键：引入单文件打包插件
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  root: './src/webview',
  build: {
    outDir: '../../dist-webview', // 产物输出到外层的 dist-webview 文件夹
    emptyOutDir: true,
    rollupOptions: {
      input: {
        editor: fileURLToPath(new URL('./src/webview/editor.html', import.meta.url)),
        viewer: fileURLToPath(new URL('./src/webview/viewer.html', import.meta.url)),
      }
    }
  }
})