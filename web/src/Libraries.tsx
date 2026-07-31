import { useCallback, useEffect, useState } from "react";
import { api, type BrowseListing, type Library } from "./api";

const KINDS: [string, string][] = [
  ["movie", "Filmes"],
  ["episode", "Séries"],
  ["documentary", "Documentários"],
  ["standup", "Stand-up"],
  ["concert", "Shows"],
  ["other", "Outros"],
];

const HINTS: [string, string][] = [
  ["auto", "automático"],
  ["tmdb", "só TMDB"],
  ["anilist", "só AniList (anime)"],
  ["none", "não identificar"],
];

/// Gerenciamento de bibliotecas.
///
/// A restrição que molda esta tela: o servidor só enxerga o que está montado
/// nele. Por isso o navegador parte das raízes e não deixa sair delas — e a
/// mensagem quando não há o que escolher precisa dizer o que fazer, não só
/// "vazio".
export default function Libraries({ onChanged }: { onChanged: () => void }) {
  const [libraries, setLibraries] = useState<Library[]>([]);
  const [adding, setAdding] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      setLibraries(await api.libraries());
      setError(null);
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const remove = async (library: Library) => {
    const ok = confirm(
      `Apagar "${library.name}"?\n\nAs obras dessa pasta somem da biblioteca, junto com ` +
        `o histórico de reprodução delas. Os arquivos no disco não são tocados.`,
    );
    if (!ok) return;
    try {
      const result = await api.deleteLibrary(library.id);
      await load();
      onChanged();
      if (result.works_removed > 0) {
        setError(`${library.name} removida — ${result.works_removed} obras saíram da biblioteca.`);
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    }
  };

  return (
    <div className="libraries">
      <div className="tree-head">
        <span className="section-title" style={{ margin: 0 }}>
          Bibliotecas
        </span>
        <button className="primary small-btn" onClick={() => setAdding(true)}>
          + adicionar pasta
        </button>
      </div>

      {error && <div className="error banner">{error}</div>}

      {libraries.length === 0 && !adding && (
        <div className="empty">
          <p>Nenhuma biblioteca.</p>
          <p className="muted">
            Adicione uma pasta pra o Odeon varrer. Separe por tipo — Filmes, Séries, Anime — que
            cada uma ganha seu próprio identificador.
          </p>
        </div>
      )}

      <div className="library-list">
        {libraries.map((library) => (
          <LibraryRow
            key={library.id}
            library={library}
            onRemove={() => remove(library)}
            onSaved={load}
          />
        ))}
      </div>

      {adding && (
        <AddLibrary
          onCancel={() => setAdding(false)}
          onCreated={() => {
            setAdding(false);
            load();
            onChanged();
          }}
        />
      )}
    </div>
  );
}

function LibraryRow({
  library,
  onRemove,
  onSaved,
}: {
  library: Library;
  onRemove: () => void;
  onSaved: () => void;
}) {
  const [kind, setKind] = useState(library.default_kind);
  const [hint, setHint] = useState(library.provider_hint);
  const dirty = kind !== library.default_kind || hint !== library.provider_hint;

  return (
    <article className="library-row">
      <div className="library-main">
        <h3>{library.name}</h3>
        <p className="path">{library.root_path}</p>
      </div>

      <select className="select" value={kind} onChange={(e) => setKind(e.target.value)}>
        {KINDS.map(([value, label]) => (
          <option key={value} value={value}>
            {label}
          </option>
        ))}
      </select>

      <select className="select" value={hint} onChange={(e) => setHint(e.target.value)}>
        {HINTS.map(([value, label]) => (
          <option key={value} value={value}>
            {label}
          </option>
        ))}
      </select>

      {dirty && (
        <button
          className="primary small-btn"
          onClick={async () => {
            await api.updateLibrary(library.id, { default_kind: kind, provider_hint: hint });
            onSaved();
          }}
        >
          salvar
        </button>
      )}

      <button className="ghost small-btn" onClick={onRemove}>
        remover
      </button>
    </article>
  );
}

function AddLibrary({ onCancel, onCreated }: { onCancel: () => void; onCreated: () => void }) {
  const [listing, setListing] = useState<BrowseListing | null>(null);
  const [name, setName] = useState("");
  const [kind, setKind] = useState("movie");
  const [hint, setHint] = useState("auto");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const go = useCallback(async (path?: string) => {
    try {
      const next = await api.browse(path);
      setListing(next);
      setError(null);
      // Sugere o nome da pasta escolhida; quase sempre é o que se quer.
      const leaf = next.path.split("/").filter(Boolean).pop();
      if (leaf && leaf !== "media") setName(leaf);
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    }
  }, []);

  useEffect(() => {
    go();
  }, [go]);

  const create = async () => {
    if (!listing) return;
    setBusy(true);
    try {
      await api.createLibrary({
        name: name.trim() || listing.path,
        root_path: listing.path,
        default_kind: kind,
        provider_hint: hint,
      });
      onCreated();
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="drawer-backdrop" onClick={(e) => e.target === e.currentTarget && onCancel()}>
      <aside className="drawer">
        <header className="drawer-head">
          <h2>Adicionar pasta</h2>
          <button className="ghost" onClick={onCancel}>
            ✕
          </button>
        </header>

        {error && <p className="error small">{error}</p>}

        {listing && (
          <>
            <p className="path current">{listing.path}</p>
            <p className="muted small">
              {listing.video_count > 0
                ? `${listing.video_count} vídeos direto aqui`
                : "nenhum vídeo direto aqui — entre numa subpasta ou use assim mesmo"}
            </p>

            <div className="browser">
              {listing.parent && (
                <button className="browse-row up" onClick={() => go(listing.parent!)}>
                  ↰ subir
                </button>
              )}
              {listing.entries.length === 0 && !listing.parent && (
                <p className="muted small">
                  Nada aqui. Confira o <code>MEDIA_PATH</code> no <code>.env</code> — o servidor só
                  enxerga o que estiver montado nele.
                </p>
              )}
              {listing.entries.map((entry) => (
                <button key={entry.path} className="browse-row" onClick={() => go(entry.path)}>
                  <span className="browse-name">{entry.name}</span>
                  <span className="muted small">
                    {entry.video_count > 0 && `${entry.video_count} vídeos`}
                    {entry.has_subdirs && entry.video_count > 0 && " · "}
                    {entry.has_subdirs && "subpastas"}
                  </span>
                </button>
              ))}
            </div>

            <div className="drawer-section">
              <label className="stacked">
                <span className="filter-label">Nome</span>
                <input value={name} onChange={(e) => setName(e.target.value)} />
              </label>

              <div className="inline-form">
                <select className="select" value={kind} onChange={(e) => setKind(e.target.value)}>
                  {KINDS.map(([value, label]) => (
                    <option key={value} value={value}>
                      {label}
                    </option>
                  ))}
                </select>
                <select className="select" value={hint} onChange={(e) => setHint(e.target.value)}>
                  {HINTS.map(([value, label]) => (
                    <option key={value} value={value}>
                      {label}
                    </option>
                  ))}
                </select>
              </div>

              <button className="primary" onClick={create} disabled={busy}>
                {busy ? "…" : "usar esta pasta"}
              </button>
            </div>
          </>
        )}
      </aside>
    </div>
  );
}
