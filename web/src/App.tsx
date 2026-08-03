import { useCallback, useEffect, useRef, useState, type CSSProperties } from "react";
import AoVivo, { AvisoDePrograma } from "./AoVivo";
import Collections from "./Collections";
import Details from "./Details";
import Admin from "./Admin";
import Gerenciar from "./Gerenciar";
import Guia from "./Guia";
import Locadora from "./Locadora";
import Mural from "./Mural";
import Perfil from "./Perfil";
import Retrospectiva from "./Retrospectiva";
import ForYou from "./ForYou";
import Libraries from "./Libraries";
import Login from "./Login";
import FilterBar from "./FilterBar";
import Player from "./Player";
import Review from "./Review";
import Scopes from "./Scopes";
import Servidor from "./Servidor";
import {
  api,
  API,
  auth, midia,
  mixedContentProblem,
  DEVICE_ID,
  Unauthorized,
  type AuthUser,
  formatDuration,
  formatSize,
  hueFromTitle,
  PAGE_SIZE,
  WORK_KINDS,
  type LibraryEntry,
  type Filters,
  type MatchStatus,
  type ScanStatus,
  type ScrubStatus,
  type WorkListItem,
} from "./api";

type Tab =
  | "foryou"
  | "library"
  | "locadora"
  | "mural"
  | "collections"
  | "live"
  | "review"
  | "settings"
  | "admin";

export default function App() {
  const [me, setMe] = useState<AuthUser | null>(null);
  const [checking, setChecking] = useState(true);
  const [tab, setTab] = useState<Tab>("foryou");
  const [works, setWorks] = useState<WorkListItem[]>([]);
  /// A biblioteca agrupada. `works` continua existindo pro modo plano —
  /// dentro de uma série o que se quer é a lista de episódios.
  const [entries, setEntries] = useState<LibraryEntry[]>([]);
  const [total, setTotal] = useState(0);
  const [carregandoMais, setCarregandoMais] = useState(false);
  const [resume, setResume] = useState<WorkListItem[]>([]);
  const [filters, setFilters] = useState<Filters>({ sort: "featured" });
  const [scan, setScan] = useState<ScanStatus | null>(null);
  const [match, setMatch] = useState<MatchStatus | null>(null);
  const [scrub, setScrub] = useState<ScrubStatus | null>(null);
  const [playing, setPlaying] = useState<WorkListItem | null>(null);
  const [detailsOf, setDetailsOf] = useState<string | null>(null);
  const [managing, setManaging] = useState<string | null>(null);
  const [serverOpen, setServerOpen] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!auth.token()) {
      setChecking(false);
      return;
    }
    api
      .me()
      .then((u) => {
        setMe(u);
        // R27: o token de mídia é curto (8h) e separado do de sessão, então
        // ele é renovado a cada boot. Sem `await`: a arte carrega quando ele
        // chegar, e a API não depende dele pra nada.
        void midia.renovar();
      })
      .catch(() => auth.clear())
      .finally(() => setChecking(false));
  }, []);

  /// Dentro de uma coleção a lista é plana (os episódios daquela série); fora
  /// dela é agrupada (uma entrada por série). São duas perguntas diferentes, e
  /// por isso duas rotas.
  const refresh = useCallback(async (f: Filters) => {
    try {
      const pendente = api.continueWatching();
      if (f.collection) {
        const list = await api.works(f);
        setWorks(list);
        setEntries([]);
        setTotal(list.length);
      } else {
        const list = await api.library(f);
        setEntries(list);
        setWorks([]);
        setTotal(list[0]?.total ?? 0);
      }
      setResume(await pendente);
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

  /// Paginação de verdade. O backend sempre soube responder `offset`; a tela é
  /// que pedia 300 fixos e chamava aquilo de "a biblioteca".
  const carregarMais = async () => {
    setCarregandoMais(true);
    try {
      if (filters.collection) {
        setWorks([...works, ...(await api.works(filters, works.length))]);
      } else {
        setEntries([...entries, ...(await api.library(filters, entries.length))]);
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setCarregandoMais(false);
    }
  };

  const startScan = async () => {
    await api.scan();
    setScan(await api.scanStatus());
  };

  const startMatch = async () => {
    await api.match(false);
    setMatch(await api.matchStatus());
  };

  if (checking) return null;
  if (!me)
    return (
      <Login
        onAuthenticated={(u) => {
          setMe(u);
          void midia.renovar();
        }}
      />
    );

  const isAdmin = me.role === "admin";
  // Quantas estão na tela agora. Dentro de coleção a lista é plana.
  const mostrando = filters.collection ? works.length : entries.length;

  return (
    <div className="app">
      <header className="topbar">
        <div className="brand">
          <span className="brand-mark">◉</span>
          <span className="brand-name">ODEON</span>
        </div>

        {/* Só navegação. As operações de servidor foram pra gaveta `Servidor`:
            misturadas aqui, elas competiam com as abas e a mais gritante da
            tela era `identificar`. Ver docs/DESIGN.md §12. */}
        <nav className="tabs">
          {(
            [
              ["foryou", "para você"],
              ["library", "biblioteca"],
              ["locadora", "experimentação"],
              // R33: a rede social saiu de dentro de "experimentação" e virou
              // aba própria. *"Uma aba separada, que talvez venha a ser algo
              // separado do Odeon"* — e daqui ela sai sem arrastar a locadora.
              ["mural", "mural"],
              ["collections", "coleções"],
              ["live", "ao vivo"],
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
            <>
              <button
                className={tab === "settings" ? "tab on" : "tab"}
                onClick={() => setTab("settings")}
              >
                pastas
              </button>
              <button
                className={tab === "admin" ? "tab on" : "tab"}
                onClick={() => setTab("admin")}
              >
                admin
              </button>
            </>
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
          {isAdmin && (
            <button
              className="ghost"
              onClick={() => setServerOpen(true)}
              title="varrer, identificar, sprites, embeddings"
            >
              servidor
            </button>
          )}
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
        </div>
      </header>

      {serverOpen && isAdmin && (
        <Servidor
          onClose={() => setServerOpen(false)}
          scan={scan}
          match={match}
          scrub={scrub}
          onScan={startScan}
          onMatch={startMatch}
          onScrubChanged={setScrub}
        />
      )}

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

      {/* Fora do `main`: o aviso de programa agendado tem que aparecer em
          qualquer aba, senão agendar não serviria pra nada. */}
      <AvisoDePrograma userId={me.id} />

      <main>
        {tab === "settings" && isAdmin && (
          <Libraries onChanged={() => refresh(filters)} />
        )}

        {tab === "foryou" && <ForYou onPlay={setPlaying} />}

        {tab === "review" && (
          <RevisaoTabs onChanged={() => refresh(filters)} />
        )}

        {tab === "locadora" && (
          <Experimentacao
            onPlay={setPlaying}
            onDetails={setDetailsOf}
            onAbrirColecao={(id, titulo) => {
              setTab("library");
              setFilters({ collection: id, collectionName: titulo });
            }}
            onExplorar={(f) => {
              setTab("library");
              setFilters(f);
            }}
          />
        )}
        {tab === "admin" && isAdmin && <Admin eu={me?.username ?? ""} />}
        {tab === "mural" && <Mural />}
        {tab === "collections" && <Collections onPlay={setPlaying} />}

        {tab === "live" && <AoVivo isAdmin={isAdmin} />}

        {tab === "library" && (
          <>
            {/* Entrou numa série pelo cartão. Reaproveita o filtro de coleção,
                que o backend já resolve pela subárvore inteira (§8c). */}
            {filters.collection && (
              <div className="person-filter">
                <span className="filter-label">Dentro de</span>
                <button
                  className="chip on"
                  onClick={() =>
                    setFilters({ ...filters, collection: undefined, collectionName: undefined })
                  }
                  title="voltar pra biblioteca"
                >
                  {filters.collectionName ?? "esta coleção"} ✕
                </button>
              </div>
            )}

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
                    <Card key={w.id} work={w} onDetails={setDetailsOf} onManage={setManaging} />
                  ))}
                </div>
              </section>
            )}

            <section>
              <h2 className="section-title">
                {filters.collection ? "Episódios" : "Biblioteca"}{" "}
                {/* O número diz o que é: quantas estão na tela DE quantas existem.
                    Antes dizia "300" com 17.498 no banco. */}
                <span className="count">
                  {mostrando < total ? `${mostrando} de ${total}` : total}
                </span>
              </h2>

              {loading ? (
                <p className="muted">carregando…</p>
              ) : mostrando === 0 ? (
                <div className="empty">
                  <p>Nada encontrado.</p>
                  <p className="muted">Afrouxe os filtros ou rode uma varredura.</p>
                </div>
              ) : (
                <>
                  {/* Dentro da série, a temporada volta a ser um nível. Uma
                      grade de 77 cartões é uma parede; em Malcolm, de 151. */}
                  {filters.collection ? (
                    porTemporada(works).map((t) => (
                      <section key={t.chave} className="temporada">
                        <div className="strip">
                          <h2>{t.titulo}</h2>
                          <span className="rule" />
                          <span className="strip-meta">
                            {t.itens.length} ep
                            {t.vistos > 0 && ` · ${t.vistos} vistos`}
                          </span>
                        </div>
                        <div className="grid larga">
                          {t.itens.map((w) => (
                            <EpisodeCard
                              key={w.id}
                              work={w}
                              onDetails={setDetailsOf}
                              onManage={setManaging}
                            />
                          ))}
                        </div>
                      </section>
                    ))
                  ) : (
                    <div className="grid">
                      {entries.map((e) => (
                        <EntryCard
                          key={e.id}
                          entry={e}
                          onOpenSeries={(id, title) =>
                            setFilters({ ...filters, collection: id, collectionName: title })
                          }
                          onDetails={setDetailsOf}
                          onManage={setManaging}
                        />
                      ))}
                    </div>
                  )}

                  {mostrando < total && (
                    <div className="mais">
                      <button className="chip" onClick={carregarMais} disabled={carregandoMais}>
                        {carregandoMais ? "carregando…" : `carregar mais ${Math.min(PAGE_SIZE, total - mostrando)}`}
                      </button>
                    </div>
                  )}
                </>
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
          // A ficha era beco sem saída: mostrava a obra e não deixava tocar.
          onPlay={(w) => {
            setDetailsOf(null);
            setPlaying(w);
          }}
          onPickPerson={(id, name) => {
            setDetailsOf(null);
            setTab("library");
            setFilters({ sort: "year", person: id, personName: name });
          }}
        />
      )}

      {managing && (
        <Gerenciar
          workId={managing}
          onClose={() => setManaging(null)}
          onChanged={() => refresh(filters)}
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

/// O cartão de episódio, dentro da série.
///
/// Usa o **`still`** — o quadro daquele episódio — em vez do pôster da série.
/// Com o pôster, uma temporada de 21 episódios eram 21 cópias da mesma imagem:
/// a arte ocupava a tela inteira sem distinguir nada. O `still` está baixado
/// desde o M1 e passou a sair na API na R1; 7.258 dos 14.657 episódios têm um,
/// e nesta série a cobertura é 77 de 77.
///
/// Daí o formato ser 16:9 e não 2:3: os stills são 780×439. Enfiar isso numa
/// moldura de pôster cortaria dois terços do quadro.
function EpisodeCard({
  work,
  onDetails,
  onManage,
}: {
  work: WorkListItem;
  onDetails: (id: string) => void;
  onManage: (id: string) => void;
}) {
  const hue = hueFromTitle(work.title);
  const progress =
    work.position_seconds && work.duration_seconds
      ? Math.min(100, (work.position_seconds / work.duration_seconds) * 100)
      : 0;

  // Sem o quadro do episódio, o pôster da série ainda é melhor que nada — e
  // sem nenhum dos dois sobra o gradiente, que ao menos carrega o título.
  const arte = work.still ?? work.poster;
  const episodio =
    work.episode_number != null ? `E${String(work.episode_number).padStart(2, "0")}` : null;

  return (
    <div
      className="card-wrap"
      style={
        {
          "--hue": hue,
          ...(work.dominant_color ? { "--accent-work": work.dominant_color } : {}),
        } as CSSProperties
      }
    >
      <button
        className="card"
        // Clicar no cartão abre a FICHA, não o player. Começar um filme é
        // decisão, e a decisão precisa da sinopse na frente — o botão de
        // assistir mora no cartaz, a um clique de distância.
        onClick={() => onDetails(work.id)}
      >
        <div className={arte ? "thumb has-art" : "thumb"}>
          {arte ? (
            <img src={api.artworkUrl(arte)} alt="" loading="lazy" />
          ) : (
            <span className="poster-title">{work.title}</span>
          )}
          {episodio && <span className="badge episodio">{episodio}</span>}
          {work.match_state === "unmatched" && <span className="badge">sem metadata</span>}
          {progress > 0 && <div className="progress" style={{ width: `${progress}%` }} />}
        </div>
        <div className="card-body">
          <h3>{work.title}</h3>
          <p className="muted small">
            {[formatDuration(work.duration_seconds), work.height ? `${work.height}p` : null]
              .filter((parte) => parte && parte !== "—")
              .join(" · ")}
          </p>
        </div>
      </button>
      <button className="card-info" title="gerenciar" onClick={() => onManage(work.id)}>
        ⋯
      </button>
    </div>
  );
}

/// Quebra os episódios de uma série em temporadas.
///
/// Agrupa por `work.season_number` e não pela coleção `season`, porque medi que
/// dá no mesmo: das 8.410 obras dentro de uma temporada, **nenhuma** está sem
/// `season_number`, e ele nunca diverge do número no título da coleção. Agrupar
/// pelo campo dispensa uma coluna nova em `WorkListItem` — e cada coluna nova ali
/// custa quatro projeções SQL, porque a struct é `sqlx::FromRow` (ver §14).
///
/// A ordem vem pronta do backend (`season_number`, depois `episode_number`), então
/// aqui é só quebrar — não reordena nada.
function porTemporada(works: WorkListItem[]) {
  const grupos = new Map<number, WorkListItem[]>();
  for (const w of works) {
    // `-1` recolhe quem não tem temporada: episódio ainda não identificado
    // continua visível, num grupo próprio, em vez de sumir da série.
    const chave = w.season_number ?? -1;
    const atual = grupos.get(chave);
    if (atual) atual.push(w);
    else grupos.set(chave, [w]);
  }

  return [...grupos.entries()]
    .sort(([a], [b]) => a - b)
    .map(([chave, itens]) => ({
      chave,
      titulo:
        chave === -1 ? "Sem temporada" : chave === 0 ? "Especiais" : `Temporada ${chave}`,
      itens,
      vistos: itens.filter((w) => w.finished).length,
    }));
}

/// Uma entrada da biblioteca. Série abre; obra avulsa toca.
///
/// A série reaproveita o mesmo cartão da obra — mesma proporção de pôster,
/// mesma tipografia — e se distingue pelo que só ela tem: contagem de
/// temporadas/episódios e a barra de quanto você já terminou.
function EntryCard({
  entry,
  onOpenSeries,
  onDetails,
  onManage,
}: {
  entry: LibraryEntry;
  onOpenSeries: (id: string, title: string) => void;
  onDetails: (id: string) => void;
  onManage: (id: string) => void;
}) {
  const hue = hueFromTitle(entry.title);

  // Obra avulsa: o cartão de sempre, montado a partir da entrada.
  if (!entry.is_series) {
    const work: WorkListItem = {
      id: entry.id,
      kind: entry.kind ?? "unknown",
      title: entry.title,
      year: entry.year,
      season_number: null,
      episode_number: null,
      match_state: entry.match_state ?? "unmatched",
      match_confidence: null,
      dominant_color: entry.dominant_color,
      poster: entry.poster,
      backdrop: null,
      still: null,
      series_title: null,
      media_file_id: entry.media_file_id,
      duration_seconds: entry.duration_seconds,
      width: null,
      height: null,
      video_codec: null,
      audio_codec: null,
      container: null,
      size_bytes: null,
      position_seconds: entry.position_seconds,
      finished: entry.finished_count > 0,
      tags: null,
    };
    return <Card work={work} onDetails={onDetails} onManage={onManage} />;
  }

  const visto = entry.work_count > 0 ? (entry.finished_count / entry.work_count) * 100 : 0;

  return (
    <div
      className="card-wrap"
      style={
        {
          "--hue": hue,
          ...(entry.dominant_color ? { "--accent-work": entry.dominant_color } : {}),
        } as CSSProperties
      }
    >
      <button className="card" onClick={() => onOpenSeries(entry.id, entry.title)}>
        <div className={entry.poster ? "poster has-art" : "poster"}>
          {entry.poster ? (
            <img src={api.artworkUrl(entry.poster)} alt="" loading="lazy" />
          ) : (
            <span className="poster-title">{entry.title}</span>
          )}
          <span className="badge serie">{entry.work_count} ep</span>
          {visto > 0 && <div className="progress" style={{ width: `${visto}%` }} />}
        </div>
        <div className="card-body">
          <h3>{entry.title}</h3>
          <p className="muted small">
            {[
              entry.year,
              entry.season_count > 1 ? `${entry.season_count} temporadas` : "1 temporada",
              entry.finished_count > 0 ? `${entry.finished_count} vistos` : null,
            ]
              .filter(Boolean)
              .join(" · ")}
          </p>
        </div>
      </button>
    </div>
  );
}

function Card({
  work,
  onDetails,
  onManage,
}: {
  work: WorkListItem;
  onDetails: (id: string) => void;
  onManage: (id: string) => void;
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
        // Clicar no cartão abre a FICHA, não o player. Começar um filme é
        // decisão, e a decisão precisa da sinopse na frente — o botão de
        // assistir mora no cartaz, a um clique de distância.
        onClick={() => onDetails(work.id)}
      >
        <div className={work.poster ? "poster has-art" : "poster"}>
          {work.poster ? (
            <img src={api.artworkUrl(work.poster)} alt="" loading="lazy" />
          ) : (
            <>
              {/* Sem arte, o cartão precisa informar por conta própria: o que é
                  e em que pé está a identificação. */}
              <span className="poster-title">{work.series_title ?? work.title}</span>
              <span className="sem-arte">
                {[WORK_KINDS[work.kind] ?? null, episode, work.year]
                  .filter(Boolean)
                  .join(" · ")}
              </span>
            </>
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
              // `formatSize`/`formatDuration` devolvem "—" quando não sabem, e
              // "—" é truthy: sem isto todo cartão terminava em " · —".
              .filter((parte) => parte && parte !== "—")
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

      {/* O ⋯ deixou de ser um atalho pra ficha (que agora é o cartão inteiro)
          e passou a ser o que faltava: identificar à mão, corrigir o parser,
          ignorar, apagar do disco. */}
      <button className="card-info" title="gerenciar" onClick={() => onManage(work.id)}>
        ⋯
      </button>
    </div>
  );
}

/// A aba `experimentação` passou a ter duas salas, e o gesto de trocar entre
/// elas é o mesmo da revisão logo abaixo — repetir o vocabulário é o que faz as
/// telas parecerem o mesmo produto (§14).
///
/// A locadora vem primeiro porque é a que já existia e é a mais visual. O guia
/// (R18) é a sala ao lado: a mesma biblioteca, lida por quem fez em vez de por
/// capa.
function Experimentacao({
  onPlay,
  onDetails,
  onAbrirColecao,
  onExplorar,
}: {
  onPlay: (w: WorkListItem) => void;
  onDetails: (id: string) => void;
  onAbrirColecao: (id: string, titulo: string) => void;
  onExplorar: (f: Filters) => void;
}) {
  const [sala, setSala] = useState<"locadora" | "guia" | "retro" | "perfil">("locadora");

  return (
    <div className="revisao">
      <div className="revisao-tabs">
        <button className={sala === "locadora" ? "on" : ""} onClick={() => setSala("locadora")}>
          locadora
        </button>
        <button className={sala === "guia" ? "on" : ""} onClick={() => setSala("guia")}>
          wiki
        </button>
        {/* R24: duas salas, e elas são separadas de propósito.
            O §6.2 decidiu "os dois, separados" porque isso é o que torna a
            decisão reversível. Ela foi: o placar saiu na R32 e o **perfil**
            entrou no lugar — nível, XP, conquistas, títulos e a comparação com
            os amigos, que foi pedida e nunca existiu. A retrospectiva ficou,
            porque descrever quem você é continua sendo outra coisa que dar
            ponto. Nenhuma das duas cita a outra. */}

        <button className={sala === "retro" ? "on" : ""} onClick={() => setSala("retro")}>
          retrospectiva
        </button>
        <button className={sala === "perfil" ? "on" : ""} onClick={() => setSala("perfil")}>
          perfil
        </button>
      </div>
      {sala === "locadora" && <Locadora onPlay={onPlay} onAbrirColecao={onAbrirColecao} />}
      {sala === "guia" && <Guia onDetails={onDetails} onExplorar={onExplorar} />}
      {sala === "retro" && <Retrospectiva />}
      {sala === "perfil" && <Perfil />}
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
