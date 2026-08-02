import { useCallback, useEffect, useMemo, useState } from "react";
import {
  api,
  COLLECTION_KINDS,
  RELATION_KINDS,
  type Collection,
  type MediaFileSummary,
  type WorkDetail,
  type WorkListItem,
} from "./api";

/// A ficha da obra — o cartaz.
///
/// Era uma gaveta de 480px com quatro seções de texto e três formulários
/// sempre abertos. O diagnóstico da R7 foi que o problema não era a pele:
/// `GET /api/works/{id}` devolve 23 campos e a tela usava 8. Ficavam de fora a
/// arte, a duração, a ficha técnica do arquivo, e — o que mais pesa — o
/// `position_seconds`, de modo que a única tela dedicada a uma obra era a
/// única de onde não dava pra assisti-la.
///
/// Nada disso exigiu backend: é tudo campo que já vinha no mesmo JSON.
export default function Details({
  workId,
  onClose,
  onChanged,
  onPickPerson,
  onPlay,
}: {
  workId: string;
  onClose: () => void;
  onChanged: () => void;
  onPickPerson?: (id: string, name: string) => void;
  onPlay?: (w: WorkListItem) => void;
}) {
  const [work, setWork] = useState<WorkDetail | null>(null);
  const [collections, setCollections] = useState<Collection[]>([]);
  /// Título da série de cada coleção-temporada, pra sobrelinha do episódio.
  const [serieDe, setSerieDe] = useState<Record<string, string>>({});
  const [editando, setEditando] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(() => {
    api.detail(workId).then(setWork).catch((e) => setError(String(e)));
  }, [workId]);

  useEffect(load, [load]);

  useEffect(() => {
    api
      .collectionTree()
      .then((tree) => {
        // achata a árvore: as manuais viram opções de "adicionar a", e o
        // caminho pai→filho vira o mapa temporada→série do cabeçalho.
        const flat: Collection[] = [];
        const pais: Record<string, string> = {};
        const walk = (nodes: typeof tree, paiTitulo: string | null) =>
          nodes.forEach((n) => {
            if (n.origin === "manual") flat.push(n);
            if (paiTitulo) pais[n.id] = paiTitulo;
            walk(n.children, n.title);
          });
        walk(tree, null);
        setCollections(flat);
        setSerieDe(pais);
      })
      .catch(() => {});
  }, []);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && onClose();
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [onClose]);

  /// Erro e carregamento também precisam da moldura.
  ///
  /// O ramo de erro devolvia `<div class="drawer">` **sem** o
  /// `.drawer-backdrop` — e é o backdrop que tem `position: fixed`. Sem ele a
  /// gaveta caía solta no fim do layout e sumia da tela: clicar nos três
  /// pontinhos parecia não fazer nada, quando na verdade havia um erro que
  /// ninguém conseguia ler. E `null` durante o carregamento deixava o clique
  /// sem resposta nenhuma pelo tempo da requisição.
  if (error || !work) {
    return (
      <div className="cartaz-fundo" onClick={(e) => e.target === e.currentTarget && onClose()}>
        <div className="cartaz cartaz-magro">
          <div className="cartaz-aviso">
            <h2>{error ? "Não deu pra abrir" : "carregando…"}</h2>
            {error && <p className="error">{error}</p>}
            <button className="ghost" onClick={onClose}>
              fechar
            </button>
          </div>
        </div>
      </div>
    );
  }

  const touched = () => {
    load();
    onChanged();
  };

  return (
    <div className="cartaz-fundo" onClick={(e) => e.target === e.currentTarget && onClose()}>
      <div
        className="cartaz"
        style={
          work.dominant_color
            ? ({ "--accent-work": work.dominant_color } as React.CSSProperties)
            : undefined
        }
      >
        <Cabeca
          work={work}
          serieDe={serieDe}
          editando={editando}
          onEditar={() => setEditando((v) => !v)}
          onPlay={onPlay}
          onClose={onClose}
          onChanged={touched}
        />

        <div className="cartaz-corpo">
          {work.overview && <p className="cartaz-sinopse">{work.overview}</p>}

          <CreditSection work={work} onPick={onPickPerson} />

          <div className="cartaz-duas">
            <EquipeSection work={work} onPick={onPickPerson} />
            <TagSection work={work} />
            <CollectionSection work={work} />
            <RelationSection work={work} />
          </div>

          {editando && (
            <div className="cartaz-editor">
              <p className="cartaz-editor-t">edição do grafo</p>
              <TagForm work={work} onChanged={touched} />
              <CollectionForm work={work} options={collections} onChanged={touched} />
              <RelationForm work={work} onChanged={touched} />
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

// ------------------------------------------------------------ formatação

export function duracao(segundos: number): string {
  const s = Math.round(segundos);
  const h = Math.floor(s / 3600);
  const m = Math.floor((s % 3600) / 60);
  return h ? `${h}h${String(m).padStart(2, "0")}` : `${m}min`;
}

function tamanho(bytes: number): string {
  return `${(bytes / 1e9).toFixed(1).replace(".", ",")} GB`;
}

const CANAIS: Record<number, string> = { 1: "mono", 2: "estéreo", 6: "5.1", 8: "7.1" };

/// O degrau de resolução vem da **largura**, não da altura.
///
/// Um 1080p em 2.35:1 é 1920×818 — rotular de "818p" seria dizer que o arquivo
/// é pior do que é. A largura é estável entre proporções; a altura não.
///
/// Abaixo de HD a lógica se inverte: 640×480 e 640×360 têm a mesma largura e
/// não são a mesma coisa, e ninguém chama nenhum dos dois de "640px". Aí o
/// degrau volta a ser a altura, que é como o material SD sempre foi nomeado.
function resolucao(f: MediaFileSummary): string | null {
  if (!f.width) return f.height ? `${f.height}p` : null;
  if (f.width >= 3400) return "4K";
  if (f.width >= 2300) return "1440p";
  if (f.width >= 1700) return "1080p";
  if (f.width >= 1100) return "720p";
  return f.height ? `${f.height}p` : `${f.width}px`;
}

export function ficha(f: MediaFileSummary): string[] {
  const audio = f.audio_codec
    ? `${f.audio_codec} ${f.audio_channels ? (CANAIS[f.audio_channels] ?? `${f.audio_channels}ch`) : ""}`.trim()
    : null;
  return [
    resolucao(f),
    f.video_codec,
    audio,
    tamanho(f.size_bytes),
    f.container === "matroska" ? "mkv" : f.container,
  ].filter((x): x is string => !!x);
}

/// A ficha e o item da lista descrevem a mesma obra por caminhos
/// diferentes — o Player fala a língua da lista, então traduzimos aqui em vez
/// de dar a ele um segundo formato pra entender.
export function paraLista(w: WorkDetail, f: MediaFileSummary | undefined, serie: string | null): WorkListItem {
  return {
    id: w.id,
    kind: w.kind,
    title: w.title,
    year: w.year,
    season_number: w.season_number,
    episode_number: w.episode_number,
    match_state: w.match_state,
    match_confidence: w.match_confidence,
    dominant_color: w.dominant_color,
    poster: w.artwork.poster ?? null,
    backdrop: w.artwork.backdrop ?? null,
    still: w.artwork.still ?? null,
    series_title: serie,
    media_file_id: f?.id ?? null,
    duration_seconds: f?.duration_seconds ?? w.runtime_seconds ?? null,
    width: f?.width ?? null,
    height: f?.height ?? null,
    video_codec: f?.video_codec ?? null,
    audio_codec: f?.audio_codec ?? null,
    container: f?.container ?? null,
    size_bytes: f?.size_bytes ?? null,
    position_seconds: w.position_seconds,
    finished: w.finished,
    tags: w.tags.map((t) => `${t.namespace}:${t.value}`),
  };
}

// --------------------------------------------------------------- cabeça

function Cabeca({
  work,
  serieDe,
  editando,
  onEditar,
  onPlay,
  onClose,
  onChanged,
}: {
  work: WorkDetail;
  serieDe: Record<string, string>;
  editando: boolean;
  onEditar: () => void;
  onPlay?: (w: WorkListItem) => void;
  onClose: () => void;
  onChanged: () => void;
}) {
  // O maior arquivo é o que o resto da UI toca (a query ordena por tamanho).
  const arquivo = work.files[0];

  // O topo prefere o quadro do episódio ao backdrop da série: numa temporada
  // inteira o backdrop é a mesma imagem 21 vezes. Mesma escolha da R3.
  const topo = work.artwork.still ?? work.artwork.backdrop ?? work.artwork.poster;
  const poster = work.artwork.poster ?? topo;

  const temporada = work.collections.find((c) => c.kind === "season");
  const serie = temporada ? (serieDe[temporada.id] ?? null) : null;
  const sobrelinha = [serie, temporada?.title].filter(Boolean).join(" · ");

  const total = arquivo?.duration_seconds ?? work.runtime_seconds ?? 0;
  const restam = total - work.position_seconds;
  // 30s é o mesmo piso que o `/api/continue` usa pra decidir o que é "começou".
  const retomando = work.position_seconds > 30 && !work.finished && restam > 60;

  const linha = [
    work.year ? String(work.year) : null,
    total ? duracao(total) : null,
    work.kind === "movie"
      ? "filme"
      : work.kind === "episode"
        ? `T${work.season_number} · E${work.episode_number}`
        : work.kind,
  ]
    .filter(Boolean)
    .join(" · ");

  const tocar = () => onPlay?.(paraLista(work, arquivo, serie));

  return (
    <>
      <div className="cartaz-topo">
        {topo ? (
          <img className="cartaz-arte" src={api.artworkUrl(topo)} alt="" />
        ) : (
          <div className="cartaz-arte cartaz-sem-arte" />
        )}
        <div className="cartaz-veu" />
        <button className="cartaz-x" onClick={onClose} title="fechar">
          ✕
        </button>
      </div>

      <div className="cartaz-cabeca">
        {poster ? (
          <img className="cartaz-poster" src={api.artworkUrl(poster)} alt="" />
        ) : (
          <div className="cartaz-poster cartaz-sem-arte" />
        )}

        <div className="cartaz-id">
          {sobrelinha && <p className="cartaz-serie">{sobrelinha}</p>}
          <h2>{work.title}</h2>
          {work.original_title && work.original_title !== work.title && (
            <p className="cartaz-original">{work.original_title}</p>
          )}
          <p className="cartaz-linha">{linha}</p>

          <div className="cartaz-acoes">
            <button className="cartaz-play" onClick={tocar} disabled={!arquivo}>
              {retomando ? `▸ continuar · faltam ${duracao(restam)}` : "▸ assistir"}
            </button>
            <Veredito work={work} onChanged={onChanged} />
            <button
              className={`cartaz-ed${editando ? " on" : ""}`}
              onClick={onEditar}
              title="tags, coleções e relações"
            >
              ✎ editar
            </button>
          </div>

          {retomando && (
            <div className="cartaz-barra" title={`parou em ${duracao(work.position_seconds)}`}>
              <i style={{ width: `${Math.min(100, (work.position_seconds / total) * 100)}%` }} />
            </div>
          )}

          {arquivo ? (
            <ul className="cartaz-ficha" title={arquivo.filename}>
              {ficha(arquivo).map((x) => (
                <li key={x}>{x}</li>
              ))}
            </ul>
          ) : (
            <p className="cartaz-ficha vazio">sem arquivo — nada pra tocar</p>
          )}
        </div>
      </div>
    </>
  );
}

/// ♥ / ✕ são o mesmo par do painel: alimentam o perfil de gosto do M5.
function Veredito({ work, onChanged }: { work: WorkDetail; onChanged: () => void }) {
  const [enviando, setEnviando] = useState<string | null>(null);

  const votar = async (verdict: "love" | "block") => {
    setEnviando(verdict);
    try {
      await api.feedback(work.id, verdict);
      onChanged();
    } finally {
      setEnviando(null);
    }
  };

  return (
    <>
      <button
        className="cartaz-ico"
        disabled={!!enviando}
        onClick={() => votar("love")}
        title="curti"
      >
        ♥
      </button>
      <button
        className="cartaz-ico"
        disabled={!!enviando}
        onClick={() => votar("block")}
        title="não me mostre isso"
      >
        ✕
      </button>
    </>
  );
}

// --------------------------------------------------------------- leitura

/// Elenco em faixa horizontal. Empilhado em linhas, doze rostos comiam a
/// altura inteira do cartaz e empurravam tags e coleções pra fora da tela.
function CreditSection({
  work,
  onPick,
}: {
  work: WorkDetail;
  onPick?: (id: string, name: string) => void;
}) {
  const elenco = useMemo(
    () => work.credits.filter((c) => c.role_label === "Elenco"),
    [work.credits],
  );
  if (elenco.length === 0) return null;

  return (
    <section className="cartaz-secao">
      <h3>Elenco</h3>
      <div className="cartaz-fila">
        {elenco.map((credit) => (
          <button
            key={`${credit.person_id}-${credit.role}`}
            className="cartaz-pessoa"
            onClick={() => onPick?.(credit.person_id, credit.name)}
            title={`ver tudo com ${credit.name}`}
          >
            {credit.image_path ? (
              <img src={api.artworkUrl(credit.image_path)} alt="" loading="lazy" />
            ) : (
              <span className="cartaz-iniciais">{credit.name.slice(0, 1).toUpperCase()}</span>
            )}
            <span className="cartaz-nome">{credit.name}</span>
            {credit.character_name && (
              <span className="cartaz-personagem">{credit.character_name}</span>
            )}
          </button>
        ))}
      </div>
    </section>
  );
}

function EquipeSection({
  work,
  onPick,
}: {
  work: WorkDetail;
  onPick?: (id: string, name: string) => void;
}) {
  // Agrupado por papel: um episódio de série vem com onze produtores, e onze
  // linhas repetindo "PRODUÇÃO" é ruído, não informação. Os créditos já chegam
  // ordenados por papel, então basta juntar os vizinhos iguais.
  const grupos = useMemo(() => {
    const out: { label: string; gente: WorkDetail["credits"] }[] = [];
    for (const c of work.credits) {
      if (c.role_label === "Elenco") continue;
      const ultimo = out[out.length - 1];
      if (ultimo && ultimo.label === c.role_label) ultimo.gente.push(c);
      else out.push({ label: c.role_label, gente: [c] });
    }
    return out;
  }, [work.credits]);

  if (grupos.length === 0) return null;

  return (
    <section className="cartaz-secao">
      <h3>Produção</h3>
      <ul className="cartaz-equipe">
        {grupos.map((g) => (
          <li key={g.label}>
            <b>{g.label}</b>
            <span className="cartaz-nomes">
              {g.gente.map((c, i) => (
                <span key={`${c.person_id}-${c.role}`}>
                  {i > 0 && <span className="sep">, </span>}
                  <button className="linkish" onClick={() => onPick?.(c.person_id, c.name)}>
                    {c.name}
                  </button>
                </span>
              ))}
            </span>
          </li>
        ))}
      </ul>
    </section>
  );
}

function TagSection({ work }: { work: WorkDetail }) {
  return (
    <section className="cartaz-secao">
      <h3>Tags</h3>
      <div className="cartaz-chips">
        {work.tags.length === 0 && <span className="vazio">nenhuma</span>}
        {work.tags.map((t) => (
          <span
            key={t.id}
            className="cartaz-chip"
            title={`colocada por ${t.source}`}
            style={t.color ? { borderColor: t.color } : undefined}
          >
            {t.namespace}
            <b>{t.value}</b>
          </span>
        ))}
      </div>
    </section>
  );
}

function CollectionSection({ work }: { work: WorkDetail }) {
  return (
    <section className="cartaz-secao">
      <h3>Coleções</h3>
      <div className="cartaz-chips">
        {work.collections.length === 0 && <span className="vazio">nenhuma</span>}
        {work.collections.map((c) => (
          <span key={c.id} className="cartaz-chip">
            {COLLECTION_KINDS[c.kind] ?? c.kind}
            <b>{c.title}</b>
          </span>
        ))}
      </div>
    </section>
  );
}

function RelationSection({ work }: { work: WorkDetail }) {
  return (
    <section className="cartaz-secao">
      <h3>Relações</h3>
      <div className="cartaz-chips">
        {work.relations.length === 0 && <span className="vazio">nenhuma</span>}
        {work.relations.map((r) => (
          <span key={`${r.other_id}-${r.kind}-${r.direction}`} className="cartaz-chip">
            {/* a MESMA aresta lida do outro lado é a relação inversa */}
            {r.direction === "out"
              ? (RELATION_KINDS[r.kind] ?? r.kind)
              : `← ${RELATION_KINDS[r.kind] ?? r.kind}`}
            <b>
              {r.other_title}
              {r.other_year ? ` (${r.other_year})` : ""}
            </b>
          </span>
        ))}
      </div>
    </section>
  );
}

// ---------------------------------------------------------------- edição
//
// Os três formulários viviam abertos no meio da leitura: dois `<select>`, um
// campo de busca e um de texto livre, permanentes numa superfície cuja função
// é mostrar uma obra. Ficam atrás do `✎ editar` — o remover (✕) de cada item
// aparece junto, porque apagar também é edição.

function TagForm({ work, onChanged }: { work: WorkDetail; onChanged: () => void }) {
  const [namespace, setNamespace] = useState("mood");
  const [value, setValue] = useState("");

  const add = async () => {
    if (!value.trim()) return;
    await api.attachTag(work.id, namespace, value.trim());
    setValue("");
    onChanged();
  };

  return (
    <div className="cartaz-linha-ed">
      <select className="select" value={namespace} onChange={(e) => setNamespace(e.target.value)}>
        {["mood", "vibe", "genre", "format", "origin"].map((n) => (
          <option key={n} value={n}>
            {n}
          </option>
        ))}
      </select>
      <input
        value={value}
        onChange={(e) => setValue(e.target.value)}
        onKeyDown={(e) => e.key === "Enter" && add()}
        placeholder="valor da tag…"
      />
      <button className="ghost small-btn" onClick={add}>
        + tag
      </button>
      <div className="cartaz-remover">
        {work.tags.map((t) => (
          <button
            key={t.id}
            className="cartaz-chip removivel"
            onClick={async () => {
              await api.detachTag(work.id, t.id);
              onChanged();
            }}
          >
            {t.namespace}:{t.value} ✕
          </button>
        ))}
      </div>
    </div>
  );
}

function CollectionForm({
  work,
  options,
  onChanged,
}: {
  work: WorkDetail;
  options: Collection[];
  onChanged: () => void;
}) {
  const [pick, setPick] = useState("");
  const available = options.filter((c) => !work.collections.some((wc) => wc.id === c.id));
  const removiveis = work.collections.filter((c) => c.origin === "manual");

  return (
    <div className="cartaz-linha-ed">
      <select className="select" value={pick} onChange={(e) => setPick(e.target.value)}>
        <option value="">escolher coleção…</option>
        {available.map((c) => (
          <option key={c.id} value={c.id}>
            {c.title}
          </option>
        ))}
      </select>
      <button
        className="ghost small-btn"
        disabled={!pick}
        onClick={async () => {
          await api.addToCollection(pick, work.id);
          setPick("");
          onChanged();
        }}
      >
        + adicionar
      </button>
      <div className="cartaz-remover">
        {removiveis.map((c) => (
          <button
            key={c.id}
            className="cartaz-chip removivel"
            onClick={async () => {
              await api.removeFromCollection(c.id, work.id);
              onChanged();
            }}
          >
            {c.title} ✕
          </button>
        ))}
      </div>
    </div>
  );
}

function RelationForm({ work, onChanged }: { work: WorkDetail; onChanged: () => void }) {
  const [kind, setKind] = useState("sequel_of");
  const [search, setSearch] = useState("");
  const [results, setResults] = useState<WorkListItem[]>([]);

  useEffect(() => {
    if (search.trim().length < 2) {
      setResults([]);
      return;
    }
    const t = setTimeout(() => {
      api
        .works({ q: search })
        .then((r) => setResults(r.filter((w) => w.id !== work.id).slice(0, 6)))
        .catch(() => {});
    }, 250);
    return () => clearTimeout(t);
  }, [search, work.id]);

  return (
    <div className="cartaz-linha-ed">
      <select className="select" value={kind} onChange={(e) => setKind(e.target.value)}>
        {Object.entries(RELATION_KINDS).map(([value, label]) => (
          <option key={value} value={value}>
            {label}
          </option>
        ))}
      </select>
      <input
        value={search}
        onChange={(e) => setSearch(e.target.value)}
        placeholder="buscar a outra obra…"
      />
      {results.length > 0 && (
        <ul className="rel-results">
          {results.map((r) => (
            <li key={r.id}>
              <button
                onClick={async () => {
                  await api.createRelation(work.id, r.id, kind);
                  setSearch("");
                  setResults([]);
                  onChanged();
                }}
              >
                {r.title}
                {r.year && <span className="muted"> ({r.year})</span>}
              </button>
            </li>
          ))}
        </ul>
      )}
      <div className="cartaz-remover">
        {work.relations.map((r) => (
          <button
            key={`${r.other_id}-${r.kind}-${r.direction}`}
            className="cartaz-chip removivel"
            onClick={async () => {
              await api.deleteRelation(work.id, r.other_id, r.kind);
              onChanged();
            }}
          >
            {r.other_title} ✕
          </button>
        ))}
      </div>
    </div>
  );
}
