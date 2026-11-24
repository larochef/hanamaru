import { defineConfig } from 'vite'

const scalajsMode = process.env.SCALAJS_MODE || "fast";

export default defineConfig({
  root: 'frontend',
  build: {
    outDir: process.env.VITE_OUT_DIR || 'dist',
    emptyOutDir: true,
  },
  server: {
    port: 5175,
    proxy: { '/api': { target: 'http://localhost:4323' } },
  },
  resolve: {
    alias: [
      {
        find: /^scalajs:(.*)$/,
        // where the build linked it (it may build in another output folder),
        // else Mill's usual output folder
        replacement: process.env.SCALAJS_OUTPUT
          ? `${process.env.SCALAJS_OUTPUT}/$1`
          : `/../out/frontend/${scalajsMode}LinkJS.dest/$1`
      }
    ]
  }
})
