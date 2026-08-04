import { useCallback, useEffect, useState } from "react";
import {
  api,
  type BrowseEntry,
  type BrowseListing,
  type Cobertura,
  type Library,
  type Palpite,
} from "./api";

/// R49 — o palpite, em palavra.
///
/// O servidor manda a chave e a tela traduz — a mesma divisão do §48. E o que
/// ele **não** manda também é conteúdo: sem palpite, não há chip. §24, linha
/// limpa some, e um "indefinido" seria pior que o silêncio.
const PALPITE: Record<Palpite, string> = {
  filme: "parece filme",
  serie: "parece série",
  mistura: "mistura",
};

/// Quantos vídeos há de verdade ali dentro.
///
/// Direto **mais** subpastas, num número só: quem escolhe uma pasta quer saber
/// se tem coisa dentro, não em que nível ela está. Antes desta fase 30 das 40
/// pastas de série do acervo diziam "0 vídeos", porque episódio mora em pasta
/// de temporada.
function quantos(e: { video_count: number; videos_abaixo: number; truncado: boolean }): string {
  const total = e.video_count + e.videos_abaixo;
  if (total === 0) return "";
  return `${total}${e.truncado ? "+" : ""} vídeo${total === 1 ? "" : "s"}`;
}

/// Esta raiz é a que estou navegando?
///
/// **`startsWith` cru não serve**, e o acervo tem o caso: `/media2` começa com
/// `/media`, então as duas raízes acendiam ao mesmo tempo. A comparação é por
/// segmento — ou é a própria pasta, ou é a pasta seguida de uma barra.
function raizAtiva(caminho: string, raiz: string): boolean {
  return caminho === raiz || caminho.startsWith(raiz.endsWith("/") ? raiz : `${raiz}/`);
}

/// Por que esta pasta não pode virar biblioteca.
///
/// O servidor recusa as duas direções, e a frase precisa dizer **qual** delas —
/// "está dentro de X" e "contém X" pedem consertos diferentes.
function porQueNao(c: Cobertura): string {
  return c.dentro
    ? `já está dentro da biblioteca "${c.biblioteca}"`
    : `contém a biblioteca "${c.biblioteca}"`;
}

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
  /// R49 — a listagem passou a descer dois níveis pra contar o que há de
  /// verdade, e numa pasta fria isso leva segundos. Sem este estado a gaveta
  /// ficava em branco no meio do caminho, que é o §8b: um clique sem resposta.
  const [lendo, setLendo] = useState(false);

  const go = useCallback(async (path?: string) => {
    setLendo(true);
    try {
      const next = await api.browse(path);
      setListing(next);
      setError(null);
      // Sugere o nome da pasta escolhida; quase sempre é o que se quer.
      const leaf = next.path.split("/").filter(Boolean).pop();
      if (leaf && leaf !== "media") setName(leaf);
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setLendo(false);
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
        {lendo && !listing && <p className="muted small">lendo a pasta…</p>}

        {listing && (
          <>
            <p className="path current">{listing.path}</p>
            {/* O que ESTA pasta é, antes de escolhê-la. Era a pergunta que a
                tela não respondia: contava só o que estava solto no primeiro
                nível, e uma pasta de séries tem zero disso. */}
            <p className="muted small">
              {lendo
                ? "lendo a pasta…"
                : [
                    quantos(listing) || "nenhum vídeo aqui dentro",
                    listing.palpite && PALPITE[listing.palpite],
                  ]
                    .filter(Boolean)
                    .join(" · ")}
            </p>
            {listing.coberta_por && (
              <p className="muted small aviso-cobertura">esta pasta {porQueNao(listing.coberta_por)}</p>
            )}

            {/* AS RAÍZES. R49 — e este era um buraco, não um enfeite.
                O navegador começa na primeira raiz e o "subir" para nela. Com
                dois pontos de montagem, **metade do disco não tinha caminho**:
                `/media2` e tudo dentro dele eram inalcançáveis por esta tela,
                que é justamente a tela de escolher onde fica o acervo. O campo
                `roots` já vinha na resposta desde sempre, sem ninguém desenhar.

                Com uma raiz só — o caso comum — nada aparece: um seletor de uma
                opção é ruído (§24). */}
            {listing.roots.length > 1 && (
              <div className="browse-raizes">
                {listing.roots.map((r) => (
                  <button
                    key={r}
                    className={`chip${raizAtiva(listing.path, r) ? " on" : ""}`}
                    onClick={() => go(r)}
                  >
                    {r}
                  </button>
                ))}
              </div>
            )}

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
                <LinhaDePasta key={entry.path} entry={entry} onEntrar={() => go(entry.path)} />
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

              {/* §53: o produto não oferece o que a validação vai negar.
                  `create_library` recusa biblioteca aninhada nos dois sentidos,
                  então o botão sai de cena e o motivo fica no lugar dele — um
                  botão apagado convida a tentar, e tentar aqui é levar um 400
                  que a tela já sabia. */}
              {listing.coberta_por ? (
                <p className="error small">
                  Não dá pra usar esta pasta: ela {porQueNao(listing.coberta_por)}. Remova aquela
                  primeiro, ou entre numa pasta que ainda não esteja no acervo.
                </p>
              ) : (
                <button className="primary" onClick={create} disabled={busy}>
                  {busy ? "…" : "usar esta pasta"}
                </button>
              )}
            </div>
          </>
        )}
      </aside>
    </div>
  );
}

/// R49 — uma pasta na lista, dizendo o que ela é antes de alguém entrar nela.
///
/// Três informações, e cada uma responde uma pergunta que a tela antiga deixava
/// sem resposta:
///
/// | o que aparece | a pergunta |
/// |---|---|
/// | `N vídeos` | tem coisa aqui dentro? — contando as subpastas, que é onde episódio mora |
/// | `parece série` | o que é isto? — palpite do `scanner::guess`, e só quando os nomes dizem |
/// | `Séries (DAS0)` | já é do acervo? — e aí entrar aqui não leva a lugar nenhum |
///
/// A pasta coberta **continua clicável**: navegar por dentro de uma biblioteca
/// é legítimo — é assim que se chega numa subpasta que ainda não é de ninguém.
/// O que ela não faz é oferecer o botão no fim (§53).
function LinhaDePasta({ entry, onEntrar }: { entry: BrowseEntry; onEntrar: () => void }) {
  const total = quantos(entry);
  return (
    <button
      className={`browse-row${entry.coberta_por ? " coberta" : ""}`}
      onClick={onEntrar}
      title={entry.coberta_por ? `esta pasta ${porQueNao(entry.coberta_por)}` : undefined}
    >
      <span className="browse-name">{entry.name}</span>
      <span className="browse-sinais">
        {/* §24: linha limpa some. Uma pasta sem vídeo não escreve "0 vídeos", e
            uma sem palpite não escreve "indefinido". */}
        {total && <span className="muted small">{total}</span>}
        {entry.palpite && <span className="chip-palpite">{PALPITE[entry.palpite]}</span>}
        {entry.coberta_por && (
          <span className="chip-coberta">{entry.coberta_por.biblioteca}</span>
        )}
      </span>
    </button>
  );
}
