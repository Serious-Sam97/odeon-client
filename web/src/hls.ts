import Hls from "hls.js";
import { api, auth } from "./api";

/**
 * Liga um `<video>` a uma playlist HLS do Odeon.
 *
 * Extraído porque o player sob demanda e o de canal ao vivo precisam
 * exatamente da mesma coisa — e o que está aqui é conhecimento caro demais pra
 * viver em duplicata: cada cópia divergiria na primeira correção.
 *
 * **A ordem importa.** O Chromium responde `"maybe"` para
 * `canPlayType('application/vnd.apple.mpegurl')` e não toca nada; testar o
 * nativo primeiro faz o player carregar a playlist como se fosse mídia e travar
 * em silêncio. hls.js primeiro; nativo só onde ele não existe (Safari/iOS).
 *
 * **O token vai por header, não por query.** O `?token=` da URL da playlist NÃO
 * chega nos segmentos: o ffmpeg escreve os nomes de forma relativa
 * (`seg00000.ts`), e resolução relativa descarta a query string. O segmento saía
 * sem credencial, o servidor devolvia 401, e o hls.js reportava `fragLoadError`
 * — sem dizer que era autenticação. O `xhrSetup` vale pra todo pedido, então o
 * header resolve playlist e segmento de uma vez.
 *
 * Devolve a função de limpeza, ou uma mensagem de erro quando o navegador não
 * aguenta HLS de jeito nenhum.
 */
export function ligarHls(
  video: HTMLVideoElement,
  playlistUrl: string,
  onFatal: (detalhe: string) => void,
): (() => void) | string {
  const url = api.hlsUrl(playlistUrl);

  if (!Hls.isSupported()) {
    if (video.canPlayType("application/vnd.apple.mpegurl")) {
      video.src = url;
      return () => {
        video.removeAttribute("src");
        video.load();
      };
    }
    return "este navegador não toca HLS";
  }

  const hls = new Hls({
    enableWorker: true,
    lowLatencyMode: false,
    xhrSetup: (xhr: XMLHttpRequest, requestUrl: string) => {
      xhr.open("GET", requestUrl, true);
      const token = auth.token();
      if (token) xhr.setRequestHeader("Authorization", `Bearer ${token}`);
    },
  });

  hls.loadSource(url);
  hls.attachMedia(video);
  hls.on(Hls.Events.ERROR, (_e, data) => {
    if (data.fatal) onFatal(data.details);
  });

  return () => hls.destroy();
}
