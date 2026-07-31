import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  server: {
    host: "0.0.0.0",
    port: 5173,
    // bind mount do Docker no macOS não propaga inotify — sem polling o HMR morre
    watch: { usePolling: true, interval: 300 },
  },
});
