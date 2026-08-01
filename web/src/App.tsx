import { useCallback, useEffect, useRef, useState, type CSSProperties } from "react";
import Collections from "./Collections";
import Details from "./Details";
import ForYou from "./ForYou";
import Libraries from "./Libraries";
import Login from "./Login";
import FilterBar from "./FilterBar";
import Player from "./Player";
import Review from "./Review";
import Scopes from "./Scopes";
import {
  api,
  API,
  auth,
  mixedContentProblem,
  DEVICE_ID,
  Unauthorized,
  type AuthUser,
  formatDuration,
  formatSize,
  hueFromTitle,
  type Filters,
  type MatchStatus,
  type ScanStatus,
  type ScrubStatus,
  type WorkListItem,
} from "./api";

type Tab = "foryou" | "library" | "collections" | "review" | "settings";

export default function App() {
  const [me, setMe] = useState<AuthUser | null>(null);
  const [checking, setChecking] = useState(true);
  const [tab, setTab] = useState<Tab>("foryou");
  const [works, setWorks] = useState<WorkListItem[]>([]);
  const [resume, setResume] = useState<WorkListItem[]>([]);
  const [filters, setFilters] = useState<Filters>({ sort: "title" });
  const [scan, setScan] = useState<ScanStatus | null>(null);
  const [match, setMatch] = useState<MatchStatus | null>(null);
  const [scrub, setScrub] = useState<ScrubStatus | null>(null);
  const [playing, setPlaying] = useState<WorkListItem | null>(null);
  const [detailsOf, setDetailsOf] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!auth.token()) {
      setChecking(false);
      return;
    }
    api
      .me()
      .then(setMe)
      .catch(() => auth.clear())
      .finally(() => setChecking(false));
  }, []);

  const refresh = useCallback(async (f: Filters) => {
    try {
      const [list, cont] = await Promise.all([api.works(f), api.continueWatching()]);
      setWorks(list);
      setResume(cont);
      setError(null);
    } catch (e) {
      if (e instanceof Unauthorized) {
        setMe(null);
        return;
      }
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setLoading(false);
    }
  }, []);

  const filtersRef = useRef(filters);
  filtersRef.current = filters;

  useEffect(() => {
    const t = setTimeout(() => refresh(filters), 250);
    return () => clearTimeout(t);
  }, [filters, refresh]);

  useEffect(() => {
    api.matchStatus().then(setMatch).catch(() => {});
    api.scrubStatus().then(setScrub).catch(() => {});
  }, []);

  // Sync ao vivo. Sem isto, dois aparelhos só se falam quando um recarrega.
  useEffect(() => {
    const source = new EventSource(api.eventsUrl());
    source.onmessage = (message) => {
      try {
        const event = JSON.parse(message.data);
        if (event.type === "progress" && event.device_id === DEVICE_ID) return;
        refresh(filtersRef.current);
        if (event.type === "match_finished") api.matchStatus().then(setMatch).catch(() => {});
        if (event.type === "scrub_finished") api.scrubStatus().then(setScrub).catch(() => {});
      } catch {
        /* evento malformado não derruba a tela */
      }
    };
    return () => source.close();
  }, [refresh]);

  useEffect(() => {
    if (!scan?.running) return;
    const t = setInterval(async () => {
      const next = await api.scanStatus();
      setScan(next);
      if (!next.running) refresh(filters);
    }, 1000);
    return () => clearInterval(t);
  }, [scan?.running, filters, refresh]);

  useEffect(() => {
    if (!match?.running) return;
    const t = setInterval(async () => {
      const next = await api.matchStatus();
      setMatch(next);
      if (!next.running) refresh(filters);
    }, 1500);
    return () => clearInterval(t);
  }, [match?.running, filters, refresh]);

  const startScan = async () => {
    await api.scan();
    setScan(await api.scanStatus());
  };

  const startMatch = async () => {
    await api.match(false);
    setMatch(await api.matchStatus());
  };

  if (checking) return null;
  if (!me) return <Login onAuthenticated={setMe} />;

  const isAdmin = me.role === "admin";

  return (
    <div className="app">
      <header className="topbar">
        <div className="brand">
          <span className="brand-mark">◉</span>
          <span className="brand-name">ODEON</span>
        </div>

        <nav className="tabs">
          {(
            [
              ["foryou", "para você"],
              ["library", "biblioteca"],
              ["collections", "coleções"],
              ["review", "revisão"],
            ] as const
          ).map(([value, label]) => (
            <button
              key={value}
              className={tab === value ? "tab on" : "tab"}
              onClick={() => setTab(value)}
            >
              {label}
              {value === "review" && match && match.needs_review > 0 && (
                <span className="pill">{match.needs_review}</span>
              )}
            </button>
          ))}
          {isAdmin && (
            <button
              className={tab === "settings" ? "tab on" : "tab"}
              onClick={() => setTab("settings")}
            >
              pastas
            </button>
          )}
        </nav>

        {tab === "library" && (
          <input
            className="search"
            placeholder="buscar na biblioteca…"
            value={filters.q ?? ""}
            onChange={(e) => setFilters({ ...filters, q: e.target.value })}
          />
        )}

        <div className="actions">
          <span
            className="whoami"
            title={`${isAdmin ? "administrador" : "usuário"} · ${API}`}
          >
            {API.startsWith("https://") && <span className="lock">🔒</span>}
            {me.display_name}
            {isAdmin && <span className="admin-dot">•</span>}
          </span>
          <button
            className="ghost"
            onClick={async () => {
              await api.logout().catch(() => {});
              auth.clear();
              setMe(null);
            }}
          >
            sair
          </button>
          {isAdmin && (
            <>
          <button className="ghost" onClick={startScan} disabled={scan?.running}>
            {scan?.running ? "varrendo…" : "varrer"}
          </button>
          <button
            className="ghost"
            onClick={async () => {
              await api.scrub(false);
              setScrub(await api.scrubStatus());
            }}
            disabled={scrub?.running}
            title="gera as miniaturas de preview de seek"
          >
            {scrub?.running ? `sprites ${scrub.done}/${scrub.total}` : "sprites"}
          </button>
          <button
            className="ghost"
            onClick={() => api.rebuildEmbeddings()}
            title="reconstrói o corpus e os vetores de conteúdo"
          >
            embeddings
          </button>
          <button className="primary" onClick={startMatch} disabled={match?.running}>
            {match?.running ? "identificando…" : "identificar"}
          </button>
          </>
          )}
        </div>
      </header>

      {match && !match.tmdb_enabled && (
        <div className="notice">
          Sem <code>TMDB_API_KEY</code> — filmes e séries não são identificados. Pegue uma chave
          grátis em <code>themoviedb.org/settings/api</code>, coloque no <code>.env</code> e rode{" "}
          <code>docker compose up -d api</code>. Anime funciona sem chave (AniList).
        </div>
      )}

      {scan?.running && (
        <div className="scanbar">
          <strong>{scan.files_seen}</strong> vistos · <strong>{scan.files_added}</strong> novos
          <span className="scanfile">{scan.current_file}</span>
        </div>
      )}

      {match?.running && (
        <div className="scanbar">
          <strong>{match.works_seen}</strong> obras · <strong>{match.matched_auto}</strong> casadas ·{" "}
          <strong>{match.needs_review}</strong> pra revisar
          <span className="scanfile">{match.current}</span>
        </div>
      )}

      {mixedContentProblem() && (
        <div className="error banner">{mixedContentProblem()}</div>
      )}

      {error && <div className="error banner">{error}</div>}

      <main>
        {tab === "settings" && isAdmin && (
          <Libraries onChanged={() => refresh(filters)} />
        )}

        {tab === "foryou" && <ForYou onPlay={setPlaying} />}

        {tab === "review" && (
          <RevisaoTabs onChanged={() => refresh(filters)} />
        )}

        {tab === "collections" && <Collections onPlay={setPlaying} />}

        {tab === "library" && (
          <>
            {filters.person && (
              <div className="person-filter">
                <span className="filter-label">Com</span>
                <button
                  className="chip on"
                  onClick={() => setFilters({ ...filters, person: undefined, personName: undefined })}
                >
                  {filters.personName ?? "esta pessoa"} ✕
                </button>
              </div>
            )}

            <FilterBar filters={filters} onChange={setFilters} />

            {resume.length > 0 && (
              <section>
                <h2 className="section-title">Continuar assistindo</h2>
                <div className="grid">
                  {resume.map((w) => (
                    <Card key={w.id} work={w} onPlay={setPlaying} onDetails={setDetailsOf} />
                  ))}
                </div>
              </section>
            )}

            <section>
              <h2 className="section-title">
                Biblioteca <span className="count">{works.length}</span>
              </h2>

              {loading ? (
                <p className="muted">carregando…</p>
              ) : works.length === 0 ? (
                <div className="empty">
                  <p>Nada encontrado.</p>
                  <p className="muted">Afrouxe os filtros ou rode uma varredura.</p>
                </div>
              ) : (
                <div className="grid">
                  {works.map((w) => (
                    <Card key={w.id} work={w} onPlay={setPlaying} onDetails={setDetailsOf} />
                  ))}
                </div>
              )}
            </section>
          </>
        )}
      </main>

      {detailsOf && (
        <Details
          workId={detailsOf}
          onClose={() => setDetailsOf(null)}
          onChanged={() => refresh(filters)}
          onPickPerson={(id, name) => {
            setDetailsOf(null);
            setTab("library");
            setFilters({ sort: "year", person: id, personName: name });
          }}
        />
      )}

      {playing && (
        <Player
          work={playing}
          onClose={() => {
            setPlaying(null);
            refresh(filters);
          }}
        />
      )}
    </div>
  );
}

function Card({
  work,
  onPlay,
  onDetails,
}: {
  work: WorkListItem;
  onPlay: (w: WorkListItem) => void;
  onDetails: (id: string) => void;
}) {
  const hue = hueFromTitle(work.title);
  const progress =
    work.position_seconds && work.duration_seconds
      ? Math.min(100, (work.position_seconds / work.duration_seconds) * 100)
      : 0;

  const episode =
    work.season_number != null && work.episode_number != null
      ? `S${String(work.season_number).padStart(2, "0")}E${String(work.episode_number).padStart(2, "0")}`
      : null;

  return (
    <div
      className="card-wrap"
      style={
        {
          "--hue": hue,
          // a cor extraída do pôster no M1 assume quando existe
          ...(work.dominant_color ? { "--accent-work": work.dominant_color } : {}),
        } as CSSProperties
      }
    >
      <button
        className="card"
        onClick={() => work.media_file_id && onPlay(work)}
        disabled={!work.media_file_id}
      >
        <div className={work.poster ? "poster has-art" : "poster"}>
          {work.poster ? (
            <img src={api.artworkUrl(work.poster)} alt="" loading="lazy" />
          ) : (
            <span className="poster-title">{work.series_title ?? work.title}</span>
          )}

          {work.match_state === "unmatched" && <span className="badge">sem metadata</span>}
          {work.match_state === "needs_review" && (
            <span className="badge warn">
              revisar {Math.round((work.match_confidence ?? 0) * 100)}%
            </span>
          )}

          {progress > 0 && <div className="progress" style={{ width: `${progress}%` }} />}
        </div>

        <div className="card-body">
          {work.series_title && <p className="series">{work.series_title}</p>}
          <h3>{work.title}</h3>
          <p className="muted small">
            {[
              episode,
              work.year,
              work.height ? `${work.height}p` : null,
              formatDuration(work.duration_seconds),
              formatSize(work.size_bytes),
            ]
              .filter(Boolean)
              .join(" · ")}
          </p>
          {work.tags && work.tags.length > 0 && (
            <p className="card-tags">
              {work.tags.slice(0, 3).map((t) => (
                <span key={t}>{t.split(":")[1]}</span>
              ))}
            </p>
          )}
        </div>
      </button>

      <button
        className="card-info"
        title="tags, coleções e relações"
        onClick={() => onDetails(work.id)}
      >
        ⋯
      </button>
    </div>
  );
}

/// A revisão tem duas entradas, e a ordem delas é a recomendação.
///
/// **Pastas** primeiro porque é onde uma decisão vale centenas de arquivos —
/// medido: 7.568 arquivos pendentes em 578 pastas. **Arquivos** é o caso que
/// sobra: o que a pasta não resolve, porque é exceção de verdade.
function RevisaoTabs({ onChanged }: { onChanged: () => void }) {
  const [modo, setModo] = useState<"pastas" | "arquivos">("pastas");

  return (
    <div className="revisao">
      <div className="revisao-tabs">
        <button
          className={modo === "pastas" ? "on" : ""}
          onClick={() => setModo("pastas")}
        >
          pastas
        </button>
        <button
          className={modo === "arquivos" ? "on" : ""}
          onClick={() => setModo("arquivos")}
        >
          arquivos
        </button>
      </div>
      {modo === "pastas" ? (
        <Scopes onChanged={onChanged} />
      ) : (
        <Review onChanged={onChanged} />
      )}
    </div>
  );
}
