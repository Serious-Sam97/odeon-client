import { useCallback, useEffect, useState } from "react";
import {
  api,
  COLLECTION_KINDS,
  formatDuration,
  type Collection,
  type CollectionNode,
  type WorkListItem,
} from "./api";

/// Coleções: franquia → série → temporada, quantos níveis houver, mais as
/// playlists e ordens de exibição criadas na mão. Tudo sai da mesma tabela.
export default function Collections({ onPlay }: { onPlay: (w: WorkListItem) => void }) {
  const [tree, setTree] = useState<CollectionNode[]>([]);
  const [selected, setSelected] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

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

  const create = async () => {
    const title = prompt("Nome da coleção:");
    if (!title?.trim()) return;
    const kind = confirm("É uma ordem de exibição? (Cancelar = playlist comum)")
      ? "watch_order"
      : "playlist";
    await api.createCollection(kind, title.trim());
    load();
  };

  if (loading) return <p className="muted">carregando coleções…</p>;

  return (
    <div className="collections">
      <aside className="collection-tree">
        <div className="tree-head">
          <span className="section-title" style={{ margin: 0 }}>
            Coleções
          </span>
          <button className="ghost small-btn" onClick={create}>
            + nova
          </button>
        </div>

        {tree.length === 0 ? (
          <p className="muted small">
            Nenhuma ainda. Séries aparecem sozinhas quando você identifica episódios.
          </p>
        ) : (
          <Tree nodes={tree} selected={selected} onSelect={setSelected} depth={0} />
        )}
      </aside>

      <section className="collection-detail">
        {selected ? (
          <CollectionView id={selected} onPlay={onPlay} onDeleted={() => { setSelected(null); load(); }} />
        ) : (
          <div className="empty">
            <p>Escolha uma coleção.</p>
            <p className="muted">
              Séries e temporadas são criadas pelo matcher. Playlists e ordens de exibição são
              suas.
            </p>
          </div>
        )}
      </section>
    </div>
  );
}

function Tree({
  nodes,
  selected,
  onSelect,
  depth,
}: {
  nodes: CollectionNode[];
  selected: string | null;
  onSelect: (id: string) => void;
  depth: number;
}) {
  return (
    <ul className="tree" style={{ paddingLeft: depth === 0 ? 0 : 14 }}>
      {nodes.map((node) => (
        <li key={node.id}>
          <button
            className={selected === node.id ? "tree-node on" : "tree-node"}
            onClick={() => onSelect(node.id)}
          >
            <span className="tree-title">{node.title}</span>
            <span className="tree-meta">
              {COLLECTION_KINDS[node.kind] ?? node.kind} · {node.item_count}
            </span>
          </button>
          {node.children.length > 0 && (
            <Tree nodes={node.children} selected={selected} onSelect={onSelect} depth={depth + 1} />
          )}
        </li>
      ))}
    </ul>
  );
}

function CollectionView({
  id,
  onPlay,
  onDeleted,
}: {
  id: string;
  onPlay: (w: WorkListItem) => void;
  onDeleted: () => void;
}) {
  const [data, setData] = useState<{
    collection: Collection;
    children: Collection[];
    items: WorkListItem[];
  } | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(() => {
    api.collection(id).then(setData).catch((e) => setError(String(e)));
  }, [id]);

  useEffect(load, [load]);

  if (error) return <p className="error">{error}</p>;
  if (!data) return <p className="muted">carregando…</p>;

  const { collection, items } = data;
  const ordered = collection.kind === "watch_order" || collection.kind === "playlist";

  // Troca com o vizinho e persiste as duas posições. Simples e reversível —
  // arrastar-e-soltar é refinamento do M3.
  const move = async (index: number, delta: number) => {
    const target = index + delta;
    if (target < 0 || target >= items.length) return;
    await api.reorderCollection(id, [
      { work_id: items[index].id, position: target + 1 },
      { work_id: items[target].id, position: index + 1 },
    ]);
    load();
  };

  const remove = async () => {
    if (!confirm(`Apagar "${collection.title}"?`)) return;
    try {
      await api.deleteCollection(id);
      onDeleted();
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    }
  };

  return (
    <>
      <header className="collection-head">
        <div>
          <p className="kind-label">{COLLECTION_KINDS[collection.kind] ?? collection.kind}</p>
          <h2>{collection.title}</h2>
          {collection.description && <p className="muted">{collection.description}</p>}
        </div>
        {collection.origin === "manual" && (
          <button className="ghost small-btn" onClick={remove}>
            apagar
          </button>
        )}
      </header>

      {items.length === 0 ? (
        <p className="muted">Vazia. Adicione obras pelo painel de detalhe de cada uma.</p>
      ) : (
        <ol className="collection-items">
          {items.map((work, index) => (
            <li key={work.id}>
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
                    {[work.year, formatDuration(work.duration_seconds)].filter(Boolean).join(" · ")}
                  </p>
                </div>
              </button>

              <div className="item-actions">
                {ordered && (
                  <>
                    <button className="ghost small-btn" onClick={() => move(index, -1)}>
                      ↑
                    </button>
                    <button className="ghost small-btn" onClick={() => move(index, 1)}>
                      ↓
                    </button>
                  </>
                )}
                {collection.origin === "manual" && (
                  <button
                    className="ghost small-btn"
                    onClick={async () => {
                      await api.removeFromCollection(id, work.id);
                      load();
                    }}
                  >
                    ✕
                  </button>
                )}
              </div>
            </li>
          ))}
        </ol>
      )}
    </>
  );
}
