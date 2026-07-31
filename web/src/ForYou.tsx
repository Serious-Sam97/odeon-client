import { useCallback, useEffect, useState } from "react";
import {
  api,
  formatDuration,
  hueFromTitle,
  type ForYou as ForYouData,
  type Recommendation,
  type WorkListItem,
} from "./api";

const TIME_OPTIONS: [string, number | undefined][] = [
  ["qualquer tempo", undefined],
  ["15 min", 15],
  ["30 min", 30],
  ["45 min", 45],
  ["1h", 60],
  ["2h", 120],
];

/// A tela que responde "o que eu assisto agora", não "o que existe na
/// biblioteca". Todo item diz POR QUE foi sugerido — mesma regra do M1.
export default function ForYou({ onPlay }: { onPlay: (w: WorkListItem) => void }) {
  const [data, setData] = useState<ForYouData | null>(null);
  const [minutes, setMinutes] = useState<number | undefined>(undefined);
  const [mood, setMood] = useState<string | undefined>(undefined);
  const [moods, setMoods] = useState<string[]>([]);
  const [showProfile, setShowProfile] = useState(false);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setData(await api.forYou(minutes, mood));
    } finally {
      setLoading(false);
    }
  }, [minutes, mood]);

  useEffect(() => {
    load();
  }, [load]);

  useEffect(() => {
    api
      .tags()
      .then((tags) =>
        setMoods(tags.filter((t) => t.namespace === "mood" && t.work_count > 0).map((t) => t.value)),
      )
      .catch(() => {});
  }, []);

  return (
    <div className="foryou">
      <div className="context-bar">
        <span className="filter-label">Tenho</span>
        <div className="chips">
          {TIME_OPTIONS.map(([label, value]) => (
            <button
              key={label}
              className={minutes === value ? "chip on" : "chip"}
              onClick={() => setMinutes(value)}
            >
              {label}
            </button>
          ))}
        </div>
      </div>

      {moods.length > 0 && (
        <div className="context-bar">
          <span className="filter-label">Pra</span>
          <div className="chips">
            {moods.map((m) => (
              <button
                key={m}
                className={mood === m ? "chip on" : "chip"}
                onClick={() => setMood(mood === m ? undefined : m)}
              >
                {m}
              </button>
            ))}
          </div>
        </div>
      )}

      {data?.cold_start && (
        <div className="notice">
          Ainda não há histórico suficiente pra personalizar — isto é o catálogo, não uma
          recomendação. Assista (e <strong>termine</strong>) algumas coisas e esta tela muda
          sozinha: o sinal vem do que você conclui, não do que você abre.
        </div>
      )}

      {data && !data.cold_start && (
        <button className="chip toggle profile-toggle" onClick={() => setShowProfile(!showProfile)}>
          o que o Odeon acha que você gosta {showProfile ? "▴" : "▾"}
        </button>
      )}

      {showProfile && data && <TasteInspector data={data} />}

      {loading && !data ? (
        <p className="muted">pensando…</p>
      ) : data && data.items.length === 0 ? (
        <div className="empty">
          <p>Nada cabe nesse tempo.</p>
          <p className="muted">Afrouxe o tempo disponível ou o humor.</p>
        </div>
      ) : (
        <div className="rec-list">
          {data?.items.map((item, index) => (
            <RecCard key={item.id} item={item} rank={index + 1} onPlay={onPlay} onChanged={load} />
          ))}
        </div>
      )}
    </div>
  );
}

/// O perfil aberto. Recomendação que não se deixa inspecionar é adivinhação.
function TasteInspector({ data }: { data: ForYouData }) {
  const p = data.profile;
  const positives = p.tag_affinity.filter(([, v]) => v > 0.1).slice(0, 8);
  const negatives = p.tag_affinity.filter(([, v]) => v < -0.1).slice(-8);
  const peakHour = p.hour_histogram.indexOf(Math.max(...p.hour_histogram));

  return (
    <div className="taste-panel">
      <div className="taste-stats">
        <span>
          <strong>{p.works_touched}</strong> obras tocadas
        </span>
        <span>
          <strong>{p.finished}</strong> terminadas
        </span>
        <span>
          <strong>{p.abandoned}</strong> largadas
        </span>
        {p.preferred_minutes && (
          <span>
            termina entre <strong>{p.preferred_minutes[0]}</strong> e{" "}
            <strong>{p.preferred_minutes[1]}</strong> min
          </span>
        )}
        {p.works_touched > 0 && (
          <span>
            costuma assistir por volta das <strong>{peakHour}h</strong>
          </span>
        )}
      </div>

      {positives.length > 0 && (
        <div className="filter-group">
          <span className="filter-label">Termina</span>
          <div className="chips">
            {positives.map(([tag, value]) => (
              <span key={tag} className="chip on">
                {tag.split(":")[1] ?? tag} <span className="muted">+{Math.round(value * 100)}%</span>
              </span>
            ))}
          </div>
        </div>
      )}

      {negatives.length > 0 && (
        <div className="filter-group">
          <span className="filter-label">Larga</span>
          <div className="chips">
            {negatives.map(([tag, value]) => (
              <span key={tag} className="chip negative">
                {tag.split(":")[1] ?? tag} <span className="muted">{Math.round(value * 100)}%</span>
              </span>
            ))}
          </div>
        </div>
      )}

      {!p.has_taste_vector && (
        <p className="muted small">
          Sem vetor de gosto ainda — rode <em>embeddings</em> e termine alguma coisa.
        </p>
      )}
    </div>
  );
}

function RecCard({
  item,
  rank,
  onPlay,
  onChanged,
}: {
  item: Recommendation;
  rank: number;
  onPlay: (w: WorkListItem) => void;
  onChanged: () => void;
}) {
  const accent = item.dominant_color ?? undefined;
  const hue = hueFromTitle(item.title);

  return (
    <article
      className="rec-card"
      style={
        accent
          ? ({ "--hue": hue, "--accent-work": accent } as React.CSSProperties)
          : ({ "--hue": hue } as React.CSSProperties)
      }
    >
      <span className="rank">{rank}</span>

      <button className="rec-poster" onClick={() => item.media_file_id && onPlay(item)}>
        {item.poster ? (
          <img src={api.artworkUrl(item.poster)} alt="" loading="lazy" />
        ) : (
          <div className="rec-noart" />
        )}
      </button>

      <div className="rec-body">
        {item.series_title && <p className="series">{item.series_title}</p>}
        <h3>{item.title}</h3>
        <p className="muted small">
          {[item.year, formatDuration(item.duration_seconds)].filter(Boolean).join(" · ")}
        </p>

        {/* Os motivos. É isto que separa curadoria de sorteio. */}
        <ul className="reasons">
          {item.reasons.map((reason, i) => (
            <li key={i}>{reason}</li>
          ))}
        </ul>
      </div>

      <div className="rec-actions">
        <span className="score">{Math.round(item.score * 100)}</span>
        <button
          className="ghost small-btn"
          title="quero mais coisas assim"
          onClick={async () => {
            await api.feedback(item.id, "love");
            onChanged();
          }}
        >
          ♥
        </button>
        <button
          className="ghost small-btn"
          title="nunca mais me ofereça isto"
          onClick={async () => {
            await api.feedback(item.id, "block");
            onChanged();
          }}
        >
          ✕
        </button>
      </div>
    </article>
  );
}
