import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import path from "path";

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "./src"),
    },
  },
  server: {
    port: 3000,
    proxy: {
      "/api": {
        target: "http://172.19.175.210:8080",
        changeOrigin: true,
      },
    },
  },
  build: {
    outDir: "dist",
    sourcemap: false,
    // Ship modern JS — no transpilation overhead, smaller output
    target: "esnext",
    // Raise the warning threshold; we control chunk sizes via manualChunks
    chunkSizeWarningLimit: 600,
    rollupOptions: {
      output: {
        // Split heavy libraries into stable long-cached chunks.
        // Recharts (~450 KB) and lucide-react (~200 KB) won't bust
        // the vendor cache when app code changes.
        manualChunks: {
          "vendor-react": ["react", "react-dom", "react-router-dom"],
          "vendor-query": ["@tanstack/react-query"],
          "vendor-charts": ["recharts"],
          "vendor-icons": ["lucide-react"],
          "vendor-form": ["react-hook-form", "@hookform/resolvers", "zod"],
          "vendor-utils": ["axios", "zustand", "date-fns"],
        },
      },
    },
  },
});
