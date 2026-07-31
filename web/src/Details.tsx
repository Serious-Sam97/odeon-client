import { useCallback, useEffect, useState } from "react";
import {
  api,
  COLLECTION_KINDS,
  RELATION_KINDS,
  type Collection,
  type WorkDetail,
  type WorkListItem,
} from "./api";

/// Painel do grafo visto de uma obra: suas tags, as coleções em que está, e as
/// arestas que a ligam a outras obras. Sem isto o M2 fica invisível na UI.
export default function Details({
  workId,
  onClose,
  onChanged,
  onPickPerson,
}: {
  workId: string;
  onClose: () => void;
  onChanged: () => void;
  onPickPerson?: (id: string, name: string) => void;
}) {
  const [work, setWork] = useState<WorkDetail | null>(null);
  const [collections, setCollections] = useState<Collection[]>([]);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(() => {
    api.detail(workId).then(setWork).catch((e) => setError(String(e)));
  }, [workId]);

  useEffect(load, [load]);

  useEffect(() => {
    api
      .collectionTree()
      .then((tree) => {
        // achata a árvore e mantém só as que eu posso editar
        const flat: Collection[] = [];
        const walk = (nodes: typeof tree) =>
          nodes.forEach((n) => {
            if (n.origin === "manual") flat.push(n);
            walk(n.children);
          });
        walk(tree);
        setCollections(flat);
      })
      .catch(() => {});
  }, []);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && onClose();
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [onClose]);

  if (error) return <div className="drawer"><p className="error">{error}</p></div>;
  if (!work) return null;

  const touched = () => {
    load();
    onChanged();
  };

  return (
    <div className="drawer-backdrop" onClick={(e) => e.target === e.currentTarget && onClose()}>
      <aside className="drawer">
        <header className="drawer-head">
          <div>
            <h2>{work.title}</h2>
            <p className="muted small">
              {[work.year, work.original_title].filter(Boolean).join(" · ")}
            </p>
          </div>
          <button className="ghost" onClick={onClose}>
            ✕
          </button>
        </header>

        {work.overview && <p className="overview">{work.overview}</p>}

        <CreditSection work={work} onPick={onPickPerson} />
        <TagSection work={work} onChanged={touched} />
        <CollectionSection work={work} options={collections} onChanged={touched} />
        <RelationSection work={work} onChanged={touched} />
      </aside>
    </div>
  );
}

/// Elenco e equipe, agrupados por papel. Clicar numa pessoa filtra a
/// biblioteca por ela — é a razão de a tabela `credit` existir.
function CreditSection({
  work,
  onPick,
}: {
  work: WorkDetail;
  onPick?: (id: string, name: string) => void;
}) {
  if (work.credits.length === 0) return null;

  const groups: { label: string; items: typeof work.credits }[] = [];
  for (const credit of work.credits) {
    const last = groups[groups.length - 1];
    if (last && last.label === credit.role_label) last.items.push(credit);
    else groups.push({ label: credit.role_label, items: [credit] });
  }

  return (
    <section className="drawer-section">
      <h3>Elenco e equipe</h3>
      {groups.map((group) => (
        <div key={group.label} className="credit-group">
          <span className="filter-label">{group.label}</span>
          <div className="credit-people">
            {group.items.map((credit) => (
              <button
                key={`${credit.person_id}-${credit.role}`}
                className="credit-person"
                onClick={() => onPick?.(credit.person_id, credit.name)}
                title={`ver tudo com ${credit.name}`}
              >
                {credit.image_path ? (
                  <img src={api.artworkUrl(credit.image_path)} alt="" loading="lazy" />
                ) : (
                  <span className="credit-initials">
                    {credit.name.slice(0, 1).toUpperCase()}
                  </span>
                )}
                <span className="credit-name">{credit.name}</span>
                {credit.character_name && (
                  <span className="credit-character">{credit.character_name}</span>
                )}
              </button>
            ))}
          </div>
        </div>
      ))}
    </section>
  );
}

function TagSection({ work, onChanged }: { work: WorkDetail; onChanged: () => void }) {
  const [namespace, setNamespace] = useState("mood");
  const [value, setValue] = useState("");

  const add = async () => {
    if (!value.trim()) return;
    await api.attachTag(work.id, namespace, value.trim());
    setValue("");
    onChanged();
  };

  return (
    <section className="drawer-section">
      <h3>Tags</h3>
      <div className="chips">
        {work.tags.length === 0 && <span className="muted small">nenhuma</span>}
        {work.tags.map((t) => (
          <span
            key={t.id}
            className="chip on"
            style={t.color ? { borderColor: t.color, color: t.color } : undefined}
          >
            {t.namespace}:{t.value}
            <button
              className="chip-x"
              title={`colocada por ${t.source}`}
              onClick={async () => {
                await api.detachTag(work.id, t.id);
                onChanged();
              }}
            >
              ✕
            </button>
          </span>
        ))}
      </div>

      <div className="inline-form">
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
          placeholder="valor…"
        />
        <button className="ghost small-btn" onClick={add}>
          + tag
        </button>
      </div>
    </section>
  );
}

function CollectionSection({
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

  return (
    <section className="drawer-section">
      <h3>Coleções</h3>
      <div className="chips">
        {work.collections.length === 0 && <span className="muted small">nenhuma</span>}
        {work.collections.map((c) => (
          <span key={c.id} className="chip">
            {c.title}
            <span className="muted"> · {COLLECTION_KINDS[c.kind] ?? c.kind}</span>
            {c.origin === "manual" && (
              <button
                className="chip-x"
                onClick={async () => {
                  await api.removeFromCollection(c.id, work.id);
                  onChanged();
                }}
              >
                ✕
              </button>
            )}
          </span>
        ))}
      </div>

      {available.length > 0 && (
        <div className="inline-form">
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
        </div>
      )}
    </section>
  );
}

function RelationSection({ work, onChanged }: { work: WorkDetail; onChanged: () => void }) {
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
    <section className="drawer-section">
      <h3>Relações</h3>

      <ul className="relations">
        {work.relations.length === 0 && <li className="muted small">nenhuma</li>}
        {work.relations.map((r) => (
          <li key={`${r.other_id}-${r.kind}-${r.direction}`}>
            <span className="rel-kind">
              {/* a MESMA aresta lida do outro lado é a relação inversa */}
              {r.direction === "out"
                ? (RELATION_KINDS[r.kind] ?? r.kind)
                : `← ${RELATION_KINDS[r.kind] ?? r.kind}`}
            </span>
            <span className="rel-target">
              {r.other_title}
              {r.other_year && <span className="muted"> ({r.other_year})</span>}
            </span>
            <button
              className="chip-x"
              onClick={async () => {
                await api.deleteRelation(work.id, r.other_id, r.kind);
                onChanged();
              }}
            >
              ✕
            </button>
          </li>
        ))}
      </ul>

      <div className="inline-form">
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
      </div>

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
    </section>
  );
}
