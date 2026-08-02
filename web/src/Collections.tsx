import { useCallback, useEffect, useState } from "react";
import {
  api,
  COLLECTION_KINDS,
  formatDuration,
  hueFromTitle,
  type Collection,
  type CollectionNode,
  type WorkListItem,
} from "./api";

/// A aba de curadoria.
///
/// Era uma árvore de 576 nós sempre expandida — e desde a R3 a biblioteca já
/// navega série → temporada → episódio, então a árvore só repetia o que a
/// biblioteca faz melhor. O que **só** esta aba faz é o que subiu pro topo:
/// ordens de exibição, playlists e franquias. As séries e temporadas criadas
/// pelo matcher continuam alcançáveis, recolhidas no fim.
///
/// A troca é deliberada: a aba passa a convidar a CRIAR. A "ordem Machete" é
/// caso de uso de primeira classe no DESIGN.md §8c e nunca tinha sido usada uma
/// vez neste acervo.
export default function Collections({ onPlay }: { onPlay: (w: WorkListItem) => void }) {
  const [tree, setTree] = useState<CollectionNode[]>([]);
  const [aberta, setAberta] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [mostrarMatcher, setMostrarMatcher] = useState(false);
  const [criando, setCriando] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setTree(await api.collectionTree());
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  if (loading) return <p className="muted">carregando coleções…</p>;

  if (aberta) {
    return (
      <CollectionView
        id={aberta}
        onPlay={onPlay}
        onVoltar={() => {
          setAberta(null);
          load();
        }}
      />
    );
  }

  // `series` e `season` nascem do matcher; o resto é curadoria humana.
  const daCasa = tree.filter((n) => n.kind !== "series" && n.kind !== "season");
  const doMatcher = tree.filter((n) => n.kind === "series" || n.kind === "season");
  const temporadas = doMatcher.reduce((n, s) => n + s.children.length, 0);

  const faixas: [string, string, string][] = [
    ["watch_order", "Suas ordens", "Uma sequência própria — a ordem Machete de Star Wars é o caso clássico."],
    ["playlist", "Playlists", "Um punhado de obras que você quer juntas, sem ordem obrigatória."],
    ["franchise", "Franquias", "Agrupa séries e filmes de um mesmo universo."],
  ];

  return (
    <div className="curadoria">
      <div className="strip primeira">
        <h2>Coleções</h2>
        <span className="rule" />
        <button className="chip" onClick={() => setCriando(!criando)}>
          {criando ? "cancelar" : "+ criar"}
        </button>
      </div>

      {criando && (
        <CriarColecao
          onPronto={(id) => {
            setCriando(false);
            load();
            setAberta(id);
          }}
        />
      )}

      {faixas.map(([kind, titulo, ajuda]) => {
        const itens = daCasa.filter((n) => n.kind === kind);
        return (
          <section key={kind} className="faixa-colecao">
            <div className="strip">
              <h2>{titulo}</h2>
              <span className="rule" />
              {itens.length > 0 && <span className="strip-meta">{itens.length}</span>}
            </div>
            {itens.length === 0 ? (
              <p className="muted small vazio-faixa">{ajuda}</p>
            ) : (
              <div className="grid larga">
                {itens.map((n) => (
                  <ColecaoCard key={n.id} node={n} onOpen={() => setAberta(n.id)} />
                ))}
              </div>
            )}
          </section>
        );
      })}

      {/* Recolhido de propósito: navegar série → temporada é trabalho da
          biblioteca desde a R3. Aqui elas existem pra serem alcançadas, não
          pra ocupar a tela. */}
      <section className="faixa-colecao">
        <div className="strip">
          <h2>Criadas pelo matcher</h2>
          <span className="rule" />
          <button className="chip" onClick={() => setMostrarMatcher(!mostrarMatcher)}>
            {doMatcher.length} séries · {temporadas} temporadas {mostrarMatcher ? "▴" : "▾"}
          </button>
        </div>
        {mostrarMatcher && (
          <div className="grid larga">
            {doMatcher.map((n) => (
              <ColecaoCard key={n.id} node={n} onOpen={() => setAberta(n.id)} />
            ))}
          </div>
        )}
      </section>
    </div>
  );
}

/// Cartão de coleção: as capas empilhadas dizem o que tem dentro antes de
/// qualquer texto. `posters` vem do backend com até quatro da subárvore.
function ColecaoCard({ node, onOpen }: { node: CollectionNode; onOpen: () => void }) {
  const capas = node.posters ?? [];
  return (
    <button
      className="colecao-card"
      onClick={onOpen}
      style={{ "--hue": hueFromTitle(node.title) } as React.CSSProperties}
    >
      <div className="pilha">
        {capas.length === 0 ? (
          <div className="pilha-vazia">{node.title}</div>
        ) : (
          capas.map((p, i) => (
            <img key={p} src={api.artworkUrl(p)} alt="" loading="lazy" style={{ zIndex: 4 - i }} />
          ))
        )}
      </div>
      <div className="colecao-body">
        <h3>{node.title}</h3>
        <p className="muted small">
          {[
            COLLECTION_KINDS[node.kind] ?? node.kind,
            `${node.item_count} ${node.item_count === 1 ? "obra" : "obras"}`,
            node.children.length > 0 ? `${node.children.length} dentro` : null,
          ]
            .filter(Boolean)
            .join(" · ")}
        </p>
      </div>
    </button>
  );
}

/// Criar sem `prompt()`.
///
/// Era `prompt("Nome da coleção:")` seguido de `confirm("É uma ordem de
/// exibição?")` — duas caixas cinza do navegador no meio da sala escura, e a
/// segunda pergunta era impossível de responder sem saber o vocabulário.
function CriarColecao({ onPronto }: { onPronto: (id: string) => void }) {
  const [titulo, setTitulo] = useState("");
  const [kind, setKind] = useState("watch_order");
  const [erro, setErro] = useState<string | null>(null);

  const tipos: [string, string, string][] = [
    ["watch_order", "ordem de exibição", "a sequência importa"],
    ["playlist", "playlist", "sem ordem obrigatória"],
    ["franchise", "franquia", "agrupa séries e filmes"],
  ];

  const criar = async () => {
    if (!titulo.trim()) return;
    try {
      const nova = await api.createCollection(kind, titulo.trim());
      onPronto(nova.id);
    } catch (e) {
      setErro(e instanceof Error ? e.message : String(e));
    }
  };

  return (
    <div className="criar-colecao">
      <input
        autoFocus
        className="campo"
        placeholder="nome da coleção…"
        value={titulo}
        onChange={(e) => setTitulo(e.target.value)}
        onKeyDown={(e) => e.key === "Enter" && criar()}
      />
      <div className="chips">
        {tipos.map(([value, rotulo, ajuda]) => (
          <button
            key={value}
            className={kind === value ? "chip on" : "chip"}
            title={ajuda}
            onClick={() => setKind(value)}
          >
            {rotulo}
          </button>
        ))}
      </div>
      <button className="play pequeno" onClick={criar} disabled={!titulo.trim()}>
        criar
      </button>
      {erro && <p className="error">{erro}</p>}
    </div>
  );
}

function CollectionView({
  id,
  onPlay,
  onVoltar,
}: {
  id: string;
  onPlay: (w: WorkListItem) => void;
  onVoltar: () => void;
}) {
  const [data, setData] = useState<{
    collection: Collection;
    children: Collection[];
    items: WorkListItem[];
  } | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [arrastando, setArrastando] = useState<number | null>(null);

  const load = useCallback(() => {
    api.collection(id).then(setData).catch((e) => setError(String(e)));
  }, [id]);

  useEffect(load, [load]);

  if (error) return <p className="error">{error}</p>;
  if (!data) return <p className="muted">carregando…</p>;

  const { collection, children, items } = data;
  const ordenada = collection.kind === "watch_order" || collection.kind === "playlist";
  const manual = collection.origin === "manual";

  /// Solta o item arrastado na posição de destino e persiste a lista inteira.
  ///
  /// Manda todas as posições, não só as duas que mudaram: arrastar do 8º pro 2º
  /// desloca seis vizinhos, e mandar par a par deixaria o meio inconsistente se
  /// uma das requisições falhasse.
  const soltar = async (destino: number) => {
    if (arrastando === null || arrastando === destino) return;
    const nova = [...items];
    const [movido] = nova.splice(arrastando, 1);
    nova.splice(destino, 0, movido);
    setData({ ...data, items: nova });
    setArrastando(null);
    await api.reorderCollection(
      id,
      nova.map((w, i) => ({ work_id: w.id, position: i + 1 })),
    );
    load();
  };

  const remover = async () => {
    if (!confirm(`Apagar "${collection.title}"?`)) return;
    try {
      await api.deleteCollection(id);
      onVoltar();
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    }
  };

  return (
    <div className="curadoria">
      <div className="person-filter">
        <span className="filter-label">Dentro de</span>
        <button className="chip on" onClick={onVoltar} title="voltar pras coleções">
          {collection.title} ✕
        </button>
      </div>

      <header className="collection-head">
        <div>
          <p className="kind-label">{COLLECTION_KINDS[collection.kind] ?? collection.kind}</p>
          <h2>{collection.title}</h2>
          {collection.description && <p className="muted">{collection.description}</p>}
        </div>
        {manual && (
          <button className="ghost small-btn" onClick={remover}>
            apagar
          </button>
        )}
      </header>

      {manual && <AdicionarObra id={id} jaTem={items.map((w) => w.id)} onAdicionado={load} />}

      {children.length > 0 && (
        <>
          <div className="strip">
            <h2>Dentro</h2>
            <span className="rule" />
            <span className="strip-meta">{children.length}</span>
          </div>
          <ul className="filhos">
            {children.map((c) => (
              <li key={c.id}>
                {c.title} <span className="muted">· {c.item_count}</span>
              </li>
            ))}
          </ul>
        </>
      )}

      {items.length === 0 ? (
        <p className="muted">
          {manual
            ? "Vazia. Busque acima pra adicionar a primeira obra."
            : "Vazia. As obras desta coleção estão nas subcoleções."}
        </p>
      ) : (
        <ol className={ordenada ? "collection-items ordenavel" : "collection-items"}>
          {items.map((work, index) => (
            <li
              key={work.id}
              draggable={ordenada}
              onDragStart={() => setArrastando(index)}
              onDragOver={(e) => ordenada && e.preventDefault()}
              onDrop={() => soltar(index)}
              className={arrastando === index ? "arrastando" : undefined}
            >
              {ordenada && <span className="alca" title="arraste pra reordenar">⠿</span>}
              <span className="position">{index + 1}</span>

              <button className="item-main" onClick={() => work.media_file_id && onPlay(work)}>
                {work.poster ? (
                  <img src={api.artworkUrl(work.poster)} alt="" />
                ) : (
                  <div className="item-noart" />
                )}
                <div>
                  <strong>{work.title}</strong>
                  <p className="muted small">
                    {[work.year, formatDuration(work.duration_seconds)]
                      .filter((p) => p && p !== "—")
                      .join(" · ")}
                  </p>
                </div>
              </button>

              {manual && (
                <div className="item-actions">
                  <button
                    className="ghost small-btn"
                    title="tirar da coleção"
                    onClick={async () => {
                      await api.removeFromCollection(id, work.id);
                      load();
                    }}
                  >
                    ✕
                  </button>
                </div>
              )}
            </li>
          ))}
        </ol>
      )}
    </div>
  );
}

/// Busca e adiciona sem sair da tela.
///
/// Antes, povoar uma playlist exigia abrir o detalhe de cada obra, uma por uma
/// — o que explica em parte por que não existia nenhuma no acervo.
function AdicionarObra({
  id,
  jaTem,
  onAdicionado,
}: {
  id: string;
  jaTem: string[];
  onAdicionado: () => void;
}) {
  const [q, setQ] = useState("");
  const [resultados, setResultados] = useState<WorkListItem[]>([]);

  useEffect(() => {
    if (q.trim().length < 2) {
      setResultados([]);
      return;
    }
    const t = setTimeout(() => {
      api.works({ q: q.trim(), sort: "featured" }).then(setResultados).catch(() => {});
    }, 250);
    return () => clearTimeout(t);
  }, [q]);

  return (
    <div className="adicionar">
      <input
        className="campo"
        placeholder="buscar obra pra adicionar…"
        value={q}
        onChange={(e) => setQ(e.target.value)}
      />
      {resultados.length > 0 && (
        <ul className="resultados">
          {resultados.slice(0, 8).map((w) => (
            <li key={w.id}>
              <button
                disabled={jaTem.includes(w.id)}
                onClick={async () => {
                  await api.addToCollection(id, w.id);
                  setQ("");
                  onAdicionado();
                }}
              >
                {w.poster ? (
                  <img src={api.artworkUrl(w.poster)} alt="" />
                ) : (
                  <span className="item-noart" />
                )}
                <span>
                  {w.title}
                  {w.year && <span className="muted"> · {w.year}</span>}
                </span>
                <span className="muted">{jaTem.includes(w.id) ? "já está" : "+"}</span>
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
