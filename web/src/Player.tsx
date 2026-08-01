import { useCallback, useEffect, useRef, useState } from "react";
import Hls from "hls.js";
import {
  api,
  auth,
  DEVICE_ID,
  type PlaybackPlan,
  type SpriteInfo,
  type TranscodeSession,
  type WorkListItem,
} from "./api";


const MODE_LABEL: Record<string, string> = {
  direct_play: "Direct Play",
  direct_stream: "Remux",
  transcode: "Transcode",
};

const HEARTBEAT_MS = 10_000;
const CONTROLS_HIDE_MS = 2600;
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

export default function Player({
  work,
  onClose,
}: {
  work: WorkListItem;
  onClose: () => void;
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
  const hlsRef = useRef<Hls | null>(null);
  const [playing, setPlaying] = useState(false);
  const [time, setTime] = useState(0);
  const [duration, setDuration] = useState(0);
  const [buffered, setBuffered] = useState(0);
  const [volume, setVolume] = useState(1);
  const [chrome, setChrome] = useState(true);
  const [hover, setHover] = useState<{ x: number; time: number } | null>(null);
  const [error, setError] = useState<string | null>(null);

  const resumeFrom = work.position_seconds ?? 0;
  const accent = work.dominant_color ?? "#e0b062";

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

  // Anexa o hls.js quando há sessão. Safari toca HLS nativo e dispensa a lib.
  useEffect(() => {
    const video = videoRef.current;
    if (!video || !session) return;

    const url = api.hlsUrl(session.playlist_url);

    // ORDEM IMPORTA. O Chromium responde "maybe" pra
    // canPlayType('application/vnd.apple.mpegurl') e não toca nada — testar o
    // nativo primeiro faz o player carregar a playlist como se fosse mídia e
    // travar em silêncio. hls.js primeiro; nativo só onde ele não existe (Safari/iOS).
    if (!Hls.isSupported()) {
      if (video.canPlayType("application/vnd.apple.mpegurl")) {
        video.src = url;
        return;
      }
      setError("este navegador não toca HLS e o arquivo precisa de transcode");
      return;
    }

    // O `?token=` da URL da playlist NÃO chega nos segmentos: o ffmpeg escreve
    // os nomes de forma relativa (`seg00000.ts`), e resolução relativa descarta
    // a query string. O segmento saía sem credencial, o servidor devolvia 401 e
    // o hls.js reportava `fragLoadError` — sem dizer que era autenticação.
    //
    // O `xhrSetup` vale pra TODO pedido do hls.js (playlist e segmentos), então
    // o header resolve os dois de uma vez. Header e não query: `?token=` existe
    // porque `<video src>` não manda header — aqui quem busca é XHR, que manda.
    const hls = new Hls({
      enableWorker: true,
      lowLatencyMode: false,
      xhrSetup: (xhr: XMLHttpRequest, url: string) => {
        xhr.open("GET", url, true);
        const token = auth.token();
        if (token) xhr.setRequestHeader("Authorization", `Bearer ${token}`);
      },
    });
    hlsRef.current = hls;
    hls.loadSource(url);
    hls.attachMedia(video);
    hls.on(Hls.Events.ERROR, (_e, data) => {
      if (data.fatal) setError(`HLS: ${data.details}`);
    });

    return () => {
      hls.destroy();
      hlsRef.current = null;
    };
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
          position_seconds: video.currentTime,
          duration_seconds: video.duration,
          media_file_id: work.media_file_id,
          event_type,
        })
        .catch(() => {});
    },
    [work.id, work.media_file_id],
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
    const source = new EventSource(api.eventsUrl());
    source.onmessage = (message) => {
      try {
        const event = JSON.parse(message.data);
        if (event.type !== "progress") return;
        if (event.device_id === DEVICE_ID) return;
        if (event.work_id !== work.id) return;
        const video = videoRef.current;
        if (!video) return;
        if (Math.abs(video.currentTime - event.position_seconds) > 5) {
          video.currentTime = event.position_seconds;
        }
      } catch {
        /* mensagem malformada não derruba o player */
      }
    };
    return () => source.close();
  }, [work.id]);

  const wake = useCallback(() => {
    setChrome(true);
    window.clearTimeout(hideTimer.current);
    hideTimer.current = window.setTimeout(() => {
      if (!videoRef.current?.paused) setChrome(false);
    }, CONTROLS_HIDE_MS);
  }, []);

  const toggle = useCallback(() => {
    const video = videoRef.current;
    if (!video) return;
    video.paused ? video.play() : video.pause();
  }, []);

  const seekBy = useCallback((delta: number) => {
    const video = videoRef.current;
    if (!video) return;
    video.currentTime = Math.max(0, Math.min(video.duration || 0, video.currentTime + delta));
  }, []);

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

  /// Converte a posição do cursor na timeline em instante do vídeo.
  const timeAt = (clientX: number): number => {
    const rect = timelineRef.current?.getBoundingClientRect();
    if (!rect || !duration) return 0;
    const ratio = Math.max(0, Math.min(1, (clientX - rect.left) / rect.width));
    return ratio * duration;
  };

  if (!work.media_file_id) return null;

  const progress = duration > 0 ? (time / duration) * 100 : 0;
  const bufferedPercent = duration > 0 ? (buffered / duration) * 100 : 0;

  return (
    <div
      className="player"
      style={{ "--accent-work": accent } as React.CSSProperties}
      onClick={(e) => e.target === e.currentTarget && onClose()}
    >
      <div
        ref={shellRef}
        className={chrome ? "player-shell" : "player-shell idle"}
        onMouseMove={wake}
      >
        <header className="player-head">
          <div>
            {work.series_title && <p className="kind-label">{work.series_title}</p>}
            <h2>{work.title}</h2>
            <p className="muted small">
              {[
                work.year,
                work.video_codec?.toUpperCase(),
                work.height ? `${work.height}p` : null,
                work.audio_codec?.toUpperCase(),
              ]
                .filter(Boolean)
                .join(" · ")}
            </p>
          </div>
          <div className="head-right">
            {plan && (
              <button
                className={`mode-badge ${plan.mode}`}
                onClick={() => setShowWhy(!showWhy)}
                title="por que este modo?"
              >
                {MODE_LABEL[plan.mode] ?? plan.mode}
                {session && plan.video === "encode" && <span> · {session.encoder}</span>}
              </button>
            )}
            <button className="ghost" onClick={onClose}>
              fechar ✕
            </button>
          </div>
        </header>

        {showWhy && plan && (
          <div className="why-panel">
            {/* A resposta que o Jellyfin nunca dá. */}
            <ul className="reasons">
              {plan.reasons.map((reason, i) => (
                <li key={i}>{reason}</li>
              ))}
            </ul>

            {plan.subtitles.length > 0 && (
              <div className="filter-group">
                <span className="filter-label">Legendas</span>
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
                      </button>
                      {/* ASS/PGS: queimar preserva o visual original, ao custo
                          de transcode. É uma escolha, e ela fica explícita. */}
                      {(t.styled || !t.text_based) && (
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
              </div>
            )}
          </div>
        )}

        <div className="stage" onClick={toggle}>
          <video
            ref={videoRef}
            className="video"
            src={plan?.mode === "direct_play" ? api.streamUrl(work.media_file_id) : undefined}
            crossOrigin="anonymous"
            autoPlay
            onLoadedMetadata={(e) => {
              setDuration(e.currentTarget.duration);
              if (resumeFrom > 30) e.currentTarget.currentTime = resumeFrom;
            }}
            onTimeUpdate={(e) => setTime(e.currentTarget.currentTime)}
            // Em HLS a duração só é conhecida depois que a playlist é lida —
            // confiar só no loadedmetadata deixa a timeline mentindo.
            onDurationChange={(e) => {
              const value = e.currentTarget.duration;
              if (isFinite(value) && value > 0) setDuration(value);
            }}
            onProgress={(e) => {
              const v = e.currentTarget;
              if (v.buffered.length > 0) setBuffered(v.buffered.end(v.buffered.length - 1));
            }}
            onPlay={() => {
              setPlaying(true);
              report("start");
              wake();
            }}
            onPause={() => {
              setPlaying(false);
              report("pause");
              setChrome(true);
            }}
            onSeeked={() => report("seek")}
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

        {error && <p className="error">{error}</p>}

        <div className="controls">
          <div
            ref={timelineRef}
            className="timeline"
            onMouseMove={(e) => setHover({ x: e.clientX, time: timeAt(e.clientX) })}
            onMouseLeave={() => setHover(null)}
            onClick={(e) => {
              const video = videoRef.current;
              if (video) video.currentTime = timeAt(e.clientX);
            }}
          >
            <div className="track" />
            <div className="track buffered" style={{ width: `${bufferedPercent}%` }} />
            <div className="track played" style={{ width: `${progress}%` }} />
            <div className="knob" style={{ left: `${progress}%` }} />

            {hover && <ScrubPreview sprite={sprite} hover={hover} timeline={timelineRef.current} />}
          </div>

          <div className="control-row">
            <button className="icon" onClick={() => seekBy(-SKIP_SECONDS)} title="voltar 10s">
              ↺
            </button>
            <button className="icon big" onClick={toggle}>
              {playing ? "❚❚" : "▶"}
            </button>
            <button className="icon" onClick={() => seekBy(SKIP_SECONDS)} title="avançar 10s">
              ↻
            </button>

            <span className="timecode">
              {clock(time)} <span className="muted">/ {clock(duration)}</span>
            </span>

            <div className="spacer" />

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
    </div>
  );
}

/// O preview de seek.
///
/// A folha inteira já está no browser; mostrar o quadro certo é só recortar a
/// célula com `background-position`. Nenhuma requisição ao arrastar.
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
