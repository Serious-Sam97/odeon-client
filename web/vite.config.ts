import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import fs from "node:fs";

/**
 * TLS no dev server, quando houver certificado.
 *
 * Não é capricho: a API é deduzida de `window.location` (ver src/api.ts) —
 * página HTTP procura a API na porta 8080, página HTTPS na 8443. Servir a web
 * em HTTP contra uma API com `ODEON_HTTPS_ONLY=true` faz a dedução apontar pro
 * lugar errado.
 *
 * E o ganho principal do TLS é da PÁGINA, não da API: Service Worker, PWA
 * offline e `crypto.subtle` só existem em contexto seguro (DESIGN §10c). Uma
 * API HTTPS servindo uma página HTTP não destrava nada disso.
 *
 * Usa o mesmo par da API (`tailscale cert`, Let's Encrypt de verdade), então não
 * há CA nova pra instalar em aparelho nenhum. Sem os arquivos, cai em HTTP — que
 * é o caso do desenvolvimento no Mac.
 */
function devServerTls() {
  const cert = process.env.ODEON_TLS_CERT;
  const key = process.env.ODEON_TLS_KEY;
  if (!cert || !key || !fs.existsSync(cert) || !fs.existsSync(key)) return undefined;
  return { cert: fs.readFileSync(cert), key: fs.readFileSync(key) };
}

export default defineConfig({
  plugins: [react()],
  server: {
    host: "0.0.0.0",
    port: 5173,
    https: devServerTls(),
    // bind mount do Docker no macOS não propaga inotify — sem polling o HMR morre
    watch: { usePolling: true, interval: 300 },
  },
});
