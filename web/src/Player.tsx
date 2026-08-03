import { useCallback, useEffect, useRef, useState } from "react";
import {
  api,
  DEVICE_ID,
  type PlaybackPlan,
  type Sala,
  type SpriteInfo,
  type TranscodeSession,
  type WorkListItem,
} from "./api";
import { ligarHls } from "./hls";


const MODE_LABEL: Record<string, string> = {
  direct_play: "Direct Play",
  direct_stream: "Remux",
  transcode: "Transcode",
};

const HEARTBEAT_MS = 10_000;
const CONTROLS_HIDE_MS = 3000;
const SKIP_SECONDS = 10;

function clock(seconds: number): string {
  if (!isFinite(seconds) || seconds < 0) return "0:00";
  const h = Math.floor(seconds / 3600);
  const m = Math.floor((seconds % 3600) / 60);
  const s = Math.floor(seconds % 60);
  return h > 0
    ? `${h}:${String(m).padStart(2, "0")}:${String(s).padStart(2, "0")}`
    : `${m}:${String(s).padStart(2, "0")}`;
}

/// A sala escura.
///
/// **Tempo do arquivo × tempo da sessão.** Quando há transcode/remux, o ffmpeg
/// recebe `-ss` e produz a partir dali: o `<video>` acha que o filme começa no
/// ponto onde a sessão começou. Medido nesta máquina — sessão com `start=600`
/// num arquivo de 1355s entrega um stream de 755s, com `currentTime` em zero.
///
/// Confiar no `<video>` fazia três coisas errarem de uma vez: a duração total
/// (mostrava o que já tinha sido produzido), o "continuar de onde parou"
/// (`currentTime = resumeFrom` sobre um stream JÁ deslocado pulava o dobro) e o
/// progresso reportado ao servidor (deslocado pelo offset).
///
/// Por isso tudo aqui trabalha em **tempo do arquivo**: `offset + currentTime`,
/// com o total vindo do ffprobe, que é quem sabe o tamanho real.
export default function Player({
  work,
  onClose,
  sala,
  aoMudarSala,
  aoLado,
}: {
  work: WorkListItem;
  onClose: () => void;
  /// R46 — a sala de assistir junto, quando é uma sessão junta.
  ///
  /// **Quem manda é o host** (§4.6): pro membro o player vira um espelho — ele
  /// obedece `rodando` e `posicao_segundos`, e os controles somem. Não é
  /// desabilitar botão: é não oferecer o que a regra não permite, que é a
  /// mesma linha do §53.
  sala?: Sala | null;
  aoMudarSala?: (s: Sala) => void;
  /// A conversa, montada por quem chamou. O player não sabe o que é uma sala —
  /// ele sabe onde ela cabe.
  aoLado?: React.ReactNode;
}) {
  const videoRef = useRef<HTMLVideoElement>(null);
  const shellRef = useRef<HTMLDivElement>(null);
  const timelineRef = useRef<HTMLDivElement>(null);
  const hideTimer = useRef<number | undefined>(undefined);

  const [sprite, setSprite] = useState<SpriteInfo | null>(null);
  const [plan, setPlan] = useState<PlaybackPlan | null>(null);
  const [session, setSession] = useState<TranscodeSession | null>(null);
  const [subtitle, setSubtitle] = useState<number | null>(null);
  const [burn, setBurn] = useState<number | null>(null);
  const [showWhy, setShowWhy] = useState(false);
  const [showSubs, setShowSubs] = useState(false);
  const [playing, setPlaying] = useState(false);
  const [time, setTime] = useState(0);
  const [streamDuration, setStreamDuration] = useState(0);
  const [buffered, setBuffered] = useState(0);
  const [volume, setVolume] = useState(1);
  const [chrome, setChrome] = useState(true);
  const [hover, setHover] = useState<{ x: number; time: number } | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [aviso, setAviso] = useState<string | null>(null);

  /// Numa sala, o ponto de partida é o da sala — e não o "continuar de onde
  /// parou" de quem entrou. Entrar numa sessão junta e cair vinte minutos à
  /// frente dos outros seria a sessão nascendo dessincronizada.
  const emSala = !!sala;
  const souHost = sala?.sou_host ?? false;
  const mando = !emSala || souHost;
  const resumeFrom = emSala ? (sala?.posicao_segundos ?? 0) : (work.position_seconds ?? 0);
  const accent = work.dominant_color ?? "#e0b062";

  // Onde esta sessão começou, em tempo de arquivo. Direct Play é sempre 0.
  const offset = session?.start_seconds ?? 0;
  // O ffprobe é a fonte da verdade do tamanho do arquivo. O `<video>` só
  // conhece o pedaço que a sessão produziu — e ele cresce enquanto o ffmpeg
  // escreve, porque a playlist é EXT-X-PLAYLIST-TYPE:EVENT.
  const total =
    work.duration_seconds && work.duration_seconds > 0
      ? work.duration_seconds
      : offset + streamDuration;
  const fileTime = offset + time;
  // O fim do que ESTA sessão pode entregar. Além disso é preciso outra sessão.
  const produzido = offset + streamDuration;

  const offsetRef = useRef(offset);
  offsetRef.current = offset;
  const sessionRef = useRef<TranscodeSession | null>(null);
  sessionRef.current = session;

  // A folha de sprites pode não existir ainda — o player só perde o preview.
  useEffect(() => {
    if (!work.media_file_id) return;
    api.spriteInfo(work.media_file_id).then(setSprite).catch(() => {});
  }, [work.media_file_id]);

  // Pergunta ao servidor o caminho mais barato que este navegador aguenta.
  useEffect(() => {
    if (!work.media_file_id) return;
    let cancelled = false;
    api
      .playbackPlan(work.media_file_id, burn ?? undefined)
      .then((p) => {
        if (cancelled) return;
        setPlan(p);
        if (p.mode === "direct_play") {
          setSession(null);
          return;
        }
        return api
          .startSession(work.media_file_id!, resumeFrom > 30 ? resumeFrom : 0, burn ?? undefined)
          .then((s) => !cancelled && setSession(s));
      })
      .catch((e) => setError(String(e)));
    return () => {
      cancelled = true;
    };
    // `burn` recria a sessão porque queimar legenda muda o plano inteiro.
  }, [work.media_file_id, burn]);

  // Anexa o hls.js quando há sessão. Toda a sutileza (ordem de detecção,
  // token por header) mora em `hls.ts`, compartilhada com o player ao vivo.
  useEffect(() => {
    const video = videoRef.current;
    if (!video || !session) return;
    const r = ligarHls(video, session.playlist_url, (d) => setError(`HLS: ${d}`));
    if (typeof r === "string") {
      setError(`${r} e o arquivo precisa de transcode`);
      return;
    }
    return r;
  }, [session]);

  // Encerra a sessão ao sair: sem isto o ffmpeg fica vivo até o reaper passar.
  useEffect(() => {
    return () => {
      if (session) api.stopSession(session.id).catch(() => {});
    };
  }, [session]);

  const report = useCallback(
    (event_type: string) => {
      const video = videoRef.current;
      if (!video || !work.media_file_id || !video.duration || !isFinite(video.duration)) return;
      api
        .progress(work.id, {
          // Tempo de ARQUIVO. Mandar o `currentTime` cru gravava a posição
          // deslocada pelo offset da sessão, e o "continuar assistindo" voltava
          // pro lugar errado na próxima vez.
          position_seconds: offsetRef.current + video.currentTime,
          duration_seconds: work.duration_seconds ?? video.duration,
          media_file_id: work.media_file_id,
          event_type,
        })
        .catch(() => {});
    },
    [work.id, work.media_file_id, work.duration_seconds],
  );

  useEffect(() => {
    const timer = setInterval(() => {
      if (!videoRef.current?.paused) report("progress");
    }, HEARTBEAT_MS);
    return () => {
      clearInterval(timer);
      report("abandon");
    };
  }, [report]);

  // Outro aparelho mexeu nesta mesma obra: acompanha, se a diferença justificar.
  // O eco do próprio device é descartado pelo DEVICE_ID.
  useEffect(() => {
    return api.ouvirEventos((event) => {
      if (event.type !== "progress") return;
      if (event.device_id === DEVICE_ID) return;
      if (event.work_id !== work.id) return;
      const video = videoRef.current;
      if (!video) return;
      // O evento vem em tempo de arquivo; o `<video>` vive em tempo de sessão.
      const alvo = (event.position_seconds as number) - offsetRef.current;
      if (alvo < 0 || (video.duration && alvo > video.duration)) return;
      if (Math.abs(video.currentTime - alvo) > 5) video.currentTime = alvo;
    });
  }, [work.id]);

  /// **A sala mandando no vídeo.**
  ///
  /// Três coisas, e as três vêm do servidor: tocar, parar, e estar no ponto.
  /// `rodando` já é `tocando && todo mundo pronto` — a tela não recalcula a
  /// regra, ela obedece o número que o servidor deu (§46).
  ///
  /// A tolerância de 1,5s existe porque `currentTime` não é exato e porque
  /// perseguir o último décimo faria o vídeo pular pra sempre. Acima disso é
  /// dessincronia de verdade, e aí vale o solavanco.
  useEffect(() => {
    const video = videoRef.current;
    if (!video || !sala) return;

    const alvo = sala.posicao_segundos - offsetRef.current;
    if (alvo >= 0 && Math.abs(video.currentTime - alvo) > 1.5) {
      video.currentTime = alvo;
    }

    if (sala.rodando && video.paused) void video.play().catch(() => {});
    if (!sala.rodando && !video.paused) video.pause();
    // Sem o objeto `sala` nas dependências: ele muda de identidade a cada
    // batimento de qualquer participante, e reexecutar isto a cada batimento
    // faria o vídeo ser recolocado no lugar dezenas de vezes por minuto.
  }, [sala?.rodando, sala?.posicao_segundos, sala?.atualizado_em]);

  /// **Eu, dizendo se estou pronto.**
  ///
  /// É este sinal que faz "quando um trava, todo mundo para" ser um fato e não
  /// um acordo: a tela avisa quando está esperando dado e quando voltou, e o
  /// servidor soma. O batimento de 20s serve de sinal de vida — quem fecha a
  /// aba não avisa, e uma sala que espera pra sempre é uma sala travada.
  ///
  /// ## O laço que isto quase virou
  ///
  /// A primeira versão dependia do objeto `sala` inteiro e mandava o estado a
  /// cada execução. Só que `prontoJunto` **devolve a sala** — então cada aviso
  /// trocava a identidade do objeto, o efeito rodava de novo e avisava outra
  /// vez. Medido no segundo participante: uma fila de `/api/junto/…/pronto` sem
  /// fim, o pool de conexões do navegador saturado, e **o vídeo dele nunca
  /// carregava** — o que fazia a sala inteira esperar por ele pra sempre.
  ///
  /// ## E o impasse do "pronto" alto demais
  ///
  /// A régua era `readyState >= 3` (HAVE_FUTURE_DATA). Numa sala isso **trava
  /// sozinho**: a sala nasce parada, o vídeo de todo mundo começa pausado, e um
  /// vídeo pausado pode nunca passar de `HAVE_CURRENT_DATA` porque o navegador
  /// não vê motivo pra encher o buffer. Ninguém fica pronto, nada toca, e nada
  /// tocar é o que impede de encher o buffer — o impasse se alimenta.
  ///
  /// A régua passou a ser `>= 2`: **tenho o quadro deste ponto**. É a promessa
  /// honesta pra sincronia — "estou onde você está" —, e quem travar de
  /// verdade no meio avisa pelo `waiting`, que é o evento que existe pra isso.
  ///
  /// Dois consertos, e os dois importam: as dependências são só o `id` da sala
  /// (o resto do objeto muda o tempo todo por natureza), e o aviso só sai
  /// quando o valor **muda**. O batimento continua saindo sempre, porque o
  /// silêncio é que é o sinal de morte.
  const ultimoPronto = useRef<boolean | null>(null);
  useEffect(() => {
    const salaId = sala?.id;
    const video = videoRef.current;
    if (!salaId || !video) return;

    const dizer = (pronto: boolean, forcar = false) => {
      if (!forcar && ultimoPronto.current === pronto) return;
      ultimoPronto.current = pronto;
      api.prontoJunto(salaId, pronto).then((s) => aoMudarSala?.(s)).catch(() => {});
    };

    const carregando = () => dizer(false);
    const voltou = () => dizer(true);

    video.addEventListener("waiting", carregando);
    video.addEventListener("stalled", carregando);
    video.addEventListener("canplay", voltou);
    video.addEventListener("playing", voltou);
    video.addEventListener("loadeddata", voltou);

    // `>= 2` é HAVE_CURRENT_DATA: **tenho o quadro deste ponto**. Ver o porquê
    // no cabeçalho — com `>= 3` a sala trava sozinha.
    const carregado = () => video.readyState >= 2;

    // O primeiro aviso sai na hora: sem ele, um membro que ainda nem carregou
    // conta como pronto e a sala começa sem ele.
    dizer(carregado(), true);

    const batida = window.setInterval(() => dizer(carregado(), true), 20_000);

    return () => {
      video.removeEventListener("waiting", carregando);
      video.removeEventListener("stalled", carregando);
      video.removeEventListener("canplay", voltou);
      video.removeEventListener("playing", voltou);
      video.removeEventListener("loadeddata", voltou);
      window.clearInterval(batida);
    };
  }, [sala?.id, aoMudarSala]);

  const wake = useCallback(() => {
    setChrome(true);
    window.clearTimeout(hideTimer.current);
    // Esconde mesmo pausado. A condição `!paused` que havia aqui fazia o cromo
    // ficar pra sempre na tela de quem pausou — e o botão grande de tocar
    // continua visível no palco, então não se perde o caminho de volta.
    hideTimer.current = window.setTimeout(() => setChrome(false), CONTROLS_HIDE_MS);
  }, []);

  /// O play/pausa. **Numa sala, quem não é host não mexe** — e o gesto não
  /// falha em silêncio: a tela nem mostra o botão (ver o cromo lá embaixo).
  const toggle = useCallback(() => {
    const video = videoRef.current;
    if (!video || !mando) return;
    video.paused ? video.play() : video.pause();
  }, [mando]);

  /// O host publicando o que fez. A sala inteira lê isto e obedece.
  const publicar = useCallback(
    (tocando: boolean) => {
      if (!sala || !souHost) return;
      const video = videoRef.current;
      const posicao = offsetRef.current + (video?.currentTime ?? 0);
      api
        .estadoJunto(sala.id, {
          tocando,
          posicao_segundos: posicao,
          // No modo compartilhado é a sessão do host que a sala inteira lê.
          transcode_id: sala.modo === "compartilhado" ? (sessionRef.current?.id ?? null) : null,
        })
        .then((s) => aoMudarSala?.(s))
        .catch(() => {});
    },
    [sala, souHost, aoMudarSala],
  );

  /// Pula pra um instante do ARQUIVO.
  ///
  /// Fora do que a sessão produziu não dá: o ffmpeg escreve do início ao fim, e
  /// alcançar outro ponto exige outra sessão (é por isso que `start_seconds` é
  /// parte da identidade da sessão — ver transcode/session.rs). Em vez de falhar
  /// em silêncio, avisa.
  const seekToFile = useCallback(
    (alvo: number) => {
      const video = videoRef.current;
      if (!video) return;
      const local = alvo - offsetRef.current;
      const limite = video.duration || 0;
      if (local < 0 || local > limite) {
        setAviso(
          "esse trecho não está nesta sessão de transcode — feche e abra de novo a partir dali",
        );
        window.setTimeout(() => setAviso(null), 4000);
        return;
      }
      video.currentTime = local;
    },
    [],
  );

  const seekBy = useCallback(
    (delta: number) => {
      const video = videoRef.current;
      if (!video) return;
      seekToFile(offsetRef.current + video.currentTime + delta);
    },
    [seekToFile],
  );

  /// Começa a contar assim que o player monta.
  ///
  /// Sem isto o cronômetro só nascia na primeira interação — quem abrisse um
  /// vídeo e não mexesse em nada ficava com a barra na tela para sempre, que é
  /// exatamente o caso de quem só quer assistir.
  useEffect(() => {
    wake();
    return () => window.clearTimeout(hideTimer.current);
  }, [wake]);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if ((e.target as HTMLElement)?.tagName === "INPUT") return;
      switch (e.key) {
        case "Escape":
          onClose();
          break;
        case " ":
        case "k":
          e.preventDefault();
          toggle();
          break;
        case "ArrowRight":
          seekBy(SKIP_SECONDS);
          break;
        case "ArrowLeft":
          seekBy(-SKIP_SECONDS);
          break;
        case "f":
          shellRef.current?.requestFullscreen?.().catch(() => {});
          break;
        case "m":
          if (videoRef.current) videoRef.current.muted = !videoRef.current.muted;
          break;
      }
      wake();
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [onClose, toggle, seekBy, wake]);

  /// Converte a posição do cursor na timeline em instante do ARQUIVO.
  const timeAt = (clientX: number): number => {
    const rect = timelineRef.current?.getBoundingClientRect();
    if (!rect || !total) return 0;
    const ratio = Math.max(0, Math.min(1, (clientX - rect.left) / rect.width));
    return ratio * total;
  };

  if (!work.media_file_id) return null;

  const pct = (value: number) => (total > 0 ? Math.max(0, Math.min(100, (value / total) * 100)) : 0);
  const pctPlayed = pct(fileTime);
  const pctInicio = pct(offset);
  const pctProduzido = pct(produzido);
  const pctBuffered = pct(offset + buffered);

  return (
    <div
      ref={shellRef}
      className={[chrome ? "player" : "player idle", aoLado ? "com-sala" : ""]
        .filter(Boolean)
        .join(" ")}
      style={{ "--accent-work": accent } as React.CSSProperties}
      onMouseMove={wake}
    >
      {/* A cor dominante da obra vive AQUI e em nenhum outro lugar do player:
          controle é sistema, e sistema é amarelo. Ver docs/DESIGN.md §12. */}
      <div className="player-halo" />

      {/* A conversa da sala. Ao LADO do filme, e não por cima: o §4.6 pediu
          "conversa ao lado durante a sessão", e uma caixa flutuando sobre a
          imagem seria a conversa disputando espaço com o que se veio ver. */}
      {aoLado}

      <div className="player-stage" onClick={toggle}>
        <video
          ref={videoRef}
          className="video"
          src={plan?.mode === "direct_play" ? api.streamUrl(work.media_file_id) : undefined}
          crossOrigin="anonymous"
          autoPlay
          /* R46: numa sala o vídeo nasce PAUSADO (a sala manda), e um vídeo
             pausado sem `preload` pode nunca carregar quadro nenhum — o que
             trava a sala esperando por quem não vai ficar pronto nunca. Pedir o
             dado desde já é o que quebra esse impasse. */
          preload="auto"
          onLoadedMetadata={(e) => {
            setStreamDuration(e.currentTarget.duration);
            // Só no Direct Play. Com sessão, o ffmpeg JÁ começou no ponto de
            // retomada — pular `resumeFrom` de novo saltaria o dobro.
            if (!session && resumeFrom > 30) e.currentTarget.currentTime = resumeFrom;
          }}
          onTimeUpdate={(e) => setTime(e.currentTarget.currentTime)}
          // Em HLS a duração só é conhecida depois que a playlist é lida, e
          // continua crescendo enquanto o ffmpeg escreve.
          onDurationChange={(e) => {
            const value = e.currentTarget.duration;
            if (isFinite(value) && value > 0) setStreamDuration(value);
          }}
          onProgress={(e) => {
            const v = e.currentTarget;
            if (v.buffered.length > 0) setBuffered(v.buffered.end(v.buffered.length - 1));
          }}
          onPlay={() => {
            setPlaying(true);
            report("start");
            publicar(true);
            wake();
          }}
          onPause={() => {
            setPlaying(false);
            report("pause");
            publicar(false);
            wake();
          }}
          onSeeked={() => {
            report("seek");
            // O pulo do host arrasta a sala. O do membro não existe — ele não
            // tem timeline clicável.
            publicar(!videoRef.current?.paused);
          }}
          onEnded={() => report("finish")}
          onVolumeChange={(e) => setVolume(e.currentTarget.muted ? 0 : e.currentTarget.volume)}
          onError={() => {
            // Com plano de transcode o erro vem do hls.js, não daqui.
            if (plan?.mode === "direct_play") {
              setError("o navegador recusou o arquivo mesmo com plano de Direct Play");
            }
          }}
        >
          {/* Legendas de texto entram como faixa nativa: sem transcode. */}
          {plan?.subtitles
            .filter((t) => t.text_based)
            .map((t) => (
              <track
                key={t.index}
                kind="subtitles"
                src={api.subtitleUrl(work.media_file_id!, t.index)}
                srcLang={t.language ?? "und"}
                label={t.label}
                default={subtitle === t.index}
              />
            ))}
        </video>

        {!playing && !error && (
          <button className="big-play" onClick={toggle} aria-label="tocar">
            ▶
          </button>
        )}
      </div>

      <header className="player-top">
        <div>
          {work.series_title && <p className="player-series">{work.series_title}</p>}
          <h2 className="player-title">{work.title}</h2>
          <p className="player-tech">
            {[
              work.year,
              work.season_number != null && work.episode_number != null
                ? `T${work.season_number} E${work.episode_number}`
                : null,
              work.video_codec?.toUpperCase(),
              work.height ? `${work.height}p` : null,
              work.audio_codec?.toUpperCase(),
            ]
              .filter(Boolean)
              .join(" · ")}
          </p>
        </div>
        <button className="player-close" onClick={onClose} title="fechar (Esc)">
          ✕
        </button>
      </header>

      {error && <div className="player-card erro">{error}</div>}

      {/* A resposta que o Jellyfin nunca dá. */}
      {showWhy && plan && (
        <div className="player-card why-card">
          <h4>Por que {MODE_LABEL[plan.mode] ?? plan.mode}</h4>
          <ul>
            {plan.reasons.map((reason, i) => (
              <li key={i}>{reason}</li>
            ))}
          </ul>
          <p className="rodape">
            {plan.video === "copy" ? "vídeo copiado bit a bit" : `vídeo recodificado`}
            {" · "}
            {plan.audio === "copy" ? "áudio copiado" : "áudio recodificado"}
            {/* O encoder só diz alguma coisa quando há encode: em remux o
                backend devolve "copy", e "· copy" no fim da frase é ruído. */}
            {session && plan.video === "encode" && ` · ${session.encoder}`}
          </p>
        </div>
      )}

      {showSubs && plan && (
        <div className="player-card subs-card">
          <h4>Legendas</h4>
          {plan.subtitles.length === 0 ? (
            <p className="muted small">este arquivo não tem faixa de legenda.</p>
          ) : (
            <div className="chips">
              <button
                className={subtitle === null && burn === null ? "chip on" : "chip"}
                onClick={() => {
                  setSubtitle(null);
                  setBurn(null);
                }}
              >
                nenhuma
              </button>
              {plan.subtitles.map((t) => (
                <span key={t.index} className="sub-option">
                  <button
                    className={subtitle === t.index ? "chip on" : "chip"}
                    disabled={!t.text_based}
                    title={
                      t.text_based
                        ? "faixa de texto — sem transcode"
                        : `${t.codec} é bitmap: só queimando`
                    }
                    onClick={() => {
                      setSubtitle(t.index);
                      setBurn(null);
                    }}
                  >
                    {t.label}
                    {t.origem === "arquivo" && <span className="muted"> · arquivo</span>}
                  </button>
                  {/* ASS/PGS: queimar preserva o visual original, ao custo de
                      transcode. É uma escolha, e ela fica explícita.

                      Só para faixa EMBUTIDA: queimar passa o índice pro filtro
                      `subtitles=si=N` do ffmpeg, que conta faixas do container.
                      Um índice negativo (legenda em arquivo) não existe pra ele
                      — o caminho pra queimar externa é `subtitles=filename=`, e
                      isso ainda não está feito. */}
                  {t.index >= 0 && (t.styled || !t.text_based) && (
                    <button
                      className={burn === t.index ? "chip on" : "chip"}
                      title="queima na imagem — preserva o estilo, força transcode"
                      onClick={() => {
                        setBurn(t.index);
                        setSubtitle(null);
                      }}
                    >
                      queimar
                    </button>
                  )}
                </span>
              ))}
            </div>
          )}
        </div>
      )}

      <div className="player-scrim">
        <div className="bulbs" />

        {aviso && <p className="player-aviso">{aviso}</p>}

        <div
          ref={timelineRef}
          className="timeline"
          onMouseMove={(e) => setHover({ x: e.clientX, time: timeAt(e.clientX) })}
          onMouseLeave={() => setHover(null)}
          onClick={(e) => mando && seekToFile(timeAt(e.clientX))}
        >
          <div className="track" />
          {/* O que esta sessão NÃO entrega, marcado em vez de escondido: a
              timeline mostra o arquivo inteiro, mas só parte dele é alcançável. */}
          {offset > 0 && <div className="track fora" style={{ width: `${pctInicio}%` }} />}
          {produzido < total - 1 && (
            <div
              className="track fora"
              style={{ left: `${pctProduzido}%`, width: `${100 - pctProduzido}%` }}
            />
          )}
          <div
            className="track buffered"
            style={{ left: `${pctInicio}%`, width: `${Math.max(0, pctBuffered - pctInicio)}%` }}
          />
          <div className="track played" style={{ width: `${pctPlayed}%` }} />
          <div className="knob" style={{ left: `${pctPlayed}%` }} />

          {hover && <ScrubPreview sprite={sprite} hover={hover} timeline={timelineRef.current} />}
        </div>

        <div className="control-row">
          {/* R46 — **numa sala, quem não é host não comanda.** Os botões não
              ficam desabilitados: eles somem. Um controle apagado convida a
              tentar, e tentar aqui é levar um "não" que a tela já sabia. */}
          {mando ? (
            <>
              <button className="icon" onClick={() => seekBy(-SKIP_SECONDS)} title="voltar 10s">
                ↺
              </button>
              <button className="icon big" onClick={toggle} title={playing ? "pausar" : "tocar"}>
                {playing ? "❚❚" : "▶"}
              </button>
              <button className="icon" onClick={() => seekBy(SKIP_SECONDS)} title="avançar 10s">
                ↻
              </button>
            </>
          ) : (
            <span className="quem-manda">quem manda é {sala?.host_nome}</span>
          )}

          <span className="timecode">
            {clock(fileTime)} <span className="muted">/ {clock(total)}</span>
          </span>

          <div className="spacer" />

          <button
            className={showSubs ? "player-btn on" : "player-btn"}
            onClick={() => {
              setShowSubs(!showSubs);
              setShowWhy(false);
            }}
          >
            legendas
            {plan && plan.subtitles.length > 0 && (
              <span className="muted"> {plan.subtitles.length}</span>
            )}
          </button>

          {plan && (
            <button
              className={`mode-badge ${plan.mode}`}
              onClick={() => {
                setShowWhy(!showWhy);
                setShowSubs(false);
              }}
              title="por que este modo?"
            >
              {MODE_LABEL[plan.mode] ?? plan.mode}
              <span className="q">?</span>
            </button>
          )}

          <div className="volume-wrap">
            <button
              className="icon"
              onClick={() => {
                const video = videoRef.current;
                if (video) video.muted = !video.muted;
              }}
              title="mudo (m)"
            >
              {/* Glifo de texto e não emoji: emoji vem colorido da fonte do
                  sistema e destoa dos outros controles, que são monocromáticos. */}
              {volume === 0 ? "◀" : "◀)"}
            </button>
            <input
              className="volume"
              type="range"
              min={0}
              max={1}
              step={0.05}
              value={volume}
              onChange={(e) => {
                const video = videoRef.current;
                if (!video) return;
                video.volume = Number(e.target.value);
                video.muted = Number(e.target.value) === 0;
              }}
            />
          </div>

          <button
            className="icon"
            onClick={() =>
              document.fullscreenElement
                ? document.exitFullscreen()
                : shellRef.current?.requestFullscreen?.().catch(() => {})
            }
            title="tela cheia (f)"
          >
            ⛶
          </button>
        </div>
      </div>
    </div>
  );
}

/// O preview de seek.
///
/// A folha inteira já está no browser; mostrar o quadro certo é só recortar a
/// célula com `background-position`. Nenhuma requisição ao arrastar.
///
/// `hover.time` é tempo de ARQUIVO, que é o mesmo eixo em que a folha foi
/// gerada — antes isto usava o tempo do `<video>` e mostrava o quadro errado
/// sempre que a sessão começava com offset.
function ScrubPreview({
  sprite,
  hover,
  timeline,
}: {
  sprite: SpriteInfo | null;
  hover: { x: number; time: number };
  timeline: HTMLDivElement | null;
}) {
  const rect = timeline?.getBoundingClientRect();
  if (!rect) return null;

  // Mantém o balão dentro da timeline em vez de escapar pelas bordas.
  const half = (sprite?.thumb_width ?? 120) / 2;
  const left = Math.max(half, Math.min(rect.width - half, hover.x - rect.left));

  let cell = null;
  if (sprite && sprite.frame_count > 0) {
    const index = Math.max(
      0,
      Math.min(sprite.frame_count - 1, Math.floor(hover.time / sprite.interval_seconds)),
    );
    const column = index % sprite.columns;
    const row = Math.floor(index / sprite.columns);
    cell = (
      <div
        className="scrub-frame"
        style={{
          width: sprite.thumb_width,
          height: sprite.thumb_height,
          backgroundImage: `url(${api.spriteUrl(sprite.path)})`,
          backgroundPosition: `-${column * sprite.thumb_width}px -${row * sprite.thumb_height}px`,
          backgroundSize: `${sprite.columns * sprite.thumb_width}px ${
            sprite.rows * sprite.thumb_height
          }px`,
        }}
      />
    );
  }

  return (
    <div className="scrub-preview" style={{ left }}>
      {cell}
      <span className="scrub-time">{clock(hover.time)}</span>
    </div>
  );
}
