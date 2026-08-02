import { useCallback, useEffect, useState } from "react";
import { api, type MatchCandidate, type WorkDetail } from "./api";
import { ficha } from "./Details";

/// Gerenciar a obra: o que se faz **com o registro e com o arquivo**, não com a
/// obra enquanto filme.
///
/// A divisão que esta gaveta fecha: clicar no cartão abre a ficha (o cartaz da
/// R7 — sinopse, elenco, assistir), e os três pontinhos abrem isto. Antes o
/// cartão tocava direto e os pontinhos abriam a ficha, então não havia lugar
/// nenhum para "esse arquivo está identificado errado" ou "apaga isso daqui".
export default function Gerenciar({
  workId,
  onClose,
  onChanged,
}: {
  workId: string;
  onClose: () => void;
  onChanged: () => void;
}) {
  const [work, setWork] = useState<WorkDetail | null>(null);
  const [erro, setErro] = useState<string | null>(null);
  const [podeApagar, setPodeApagar] = useState<{ pode: boolean; motivo: string | null } | null>(
    null,
  );

  const carregar = useCallback(() => {
    api.detail(workId).then(setWork).catch((e) => setErro(String(e)));
  }, [workId]);

  useEffect(carregar, [carregar]);

  useEffect(() => {
    api
      .storage()
      .then((s) => setPodeApagar({ pode: s.pode_apagar, motivo: s.motivo }))
      .catch(() => setPodeApagar({ pode: false, motivo: "não consegui perguntar ao servidor" }));
  }, []);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && onClose();
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [onClose]);

  const mexeu = () => {
    carregar();
    onChanged();
  };

  return (
    <div className="cartaz-fundo" onClick={(e) => e.target === e.currentTarget && onClose()}>
      <div className="gerenciar">
        <header className="gerenciar-topo">
          <div>
            <p className="gerenciar-sobre">gerenciar</p>
            <h2>{work?.title ?? "…"}</h2>
          </div>
          <button className="cartaz-x" onClick={onClose} title="fechar">
            ✕
          </button>
        </header>

        {erro && <p className="error">{erro}</p>}

        {work && (
          <div className="gerenciar-corpo">
            <Arquivos work={work} />
            <Identificacao work={work} onChanged={mexeu} />
            <Parser work={work} onChanged={mexeu} />
            <ZonaDeRisco
              work={work}
              podeApagar={podeApagar}
              onSumiu={() => {
                onChanged();
                onClose();
              }}
              onChanged={mexeu}
            />
          </div>
        )}
      </div>
    </div>
  );
}

// ------------------------------------------------------------- o arquivo

function Arquivos({ work }: { work: WorkDetail }) {
  const [copiado, setCopiado] = useState<string | null>(null);

  if (work.files.length === 0) {
    return (
      <section className="ger-secao">
        <h3>Arquivo</h3>
        <p className="vazio">nenhum — esta obra não tem arquivo no disco</p>
      </section>
    );
  }

  return (
    <section className="ger-secao">
      <h3>
        {work.files.length === 1 ? "Arquivo" : `Arquivos (${work.files.length})`}
      </h3>
      {work.files.map((f) => (
        <div key={f.id} className="ger-arquivo">
          <code
            title="clique pra copiar"
            onClick={() => {
              navigator.clipboard?.writeText(f.path).then(
                () => {
                  setCopiado(f.id);
                  window.setTimeout(() => setCopiado(null), 1400);
                },
                () => {},
              );
            }}
          >
            {f.path}
          </code>
          <ul className="cartaz-ficha">
            {ficha(f).map((x) => (
              <li key={x}>{x}</li>
            ))}
            {/* `probed` é o estado normal (17.498 dos 17.503 arquivos) — só
                `error` e `missing` merecem ser gritados. */}
            {f.status !== "probed" && <li className="alerta">{f.status}</li>}
          </ul>
          {copiado === f.id && <span className="ger-copiado">caminho copiado</span>}
        </div>
      ))}
    </section>
  );
}

// -------------------------------------------------------- identificação

const ESTADOS: Record<string, string> = {
  confirmed: "confirmada por uma pessoa",
  auto: "automática",
  needs_review: "esperando revisão",
  unmatched: "sem identificação",
  ignored: "ignorada",
};

/// Localizar manualmente. É a rota `POST /api/works/{id}/search`, a mesma que a
/// fila de revisão usa — a diferença é o ponto de partida: aqui a obra já está
/// identificada e alguém discorda, ali ela nunca foi.
function Identificacao({ work, onChanged }: { work: WorkDetail; onChanged: () => void }) {
  const [busca, setBusca] = useState(work.title);
  const [ano, setAno] = useState<string>(work.year ? String(work.year) : "");
  const [resultados, setResultados] = useState<MatchCandidate[] | null>(null);
  const [ocupado, setOcupado] = useState(false);
  const [aviso, setAviso] = useState<string | null>(null);

  const procurar = async () => {
    if (!busca.trim()) return;
    setOcupado(true);
    setAviso(null);
    try {
      const r = await api.searchCandidates(work.id, busca.trim(), ano ? Number(ano) : undefined);
      setResultados(r);
      if (r.length === 0) setAviso("o provider não devolveu nada para essa busca");
    } catch (e) {
      setAviso(String(e));
    } finally {
      setOcupado(false);
    }
  };

  return (
    <section className="ger-secao">
      <h3>Identificação</h3>

      <p className="ger-estado">
        <b>{ESTADOS[work.match_state] ?? work.match_state}</b>
        {work.match_confidence != null && (
          <span className="muted"> · {Math.round(work.match_confidence * 100)}% de confiança</span>
        )}
        {Object.entries(work.external_ids).map(([p, id]) => (
          <span key={p} className="cartaz-chip">
            {p}
            <b>{id}</b>
          </span>
        ))}
      </p>

      <div className="ger-linha">
        <input
          value={busca}
          onChange={(e) => setBusca(e.target.value)}
          onKeyDown={(e) => e.key === "Enter" && procurar()}
          placeholder="título pra procurar no provider…"
        />
        <input
          className="ger-ano"
          value={ano}
          onChange={(e) => setAno(e.target.value.replace(/\D/g, "").slice(0, 4))}
          placeholder="ano"
        />
        <button className="ghost small-btn" onClick={procurar} disabled={ocupado}>
          {ocupado ? "procurando…" : "procurar"}
        </button>
        {work.match_state !== "unmatched" && (
          <button
            className="ghost small-btn"
            onClick={async () => {
              await api.resetMatch(work.id);
              setResultados(null);
              onChanged();
            }}
            title="tira tudo que veio do provider e devolve pra fila"
          >
            desfazer identificação
          </button>
        )}
      </div>

      {aviso && <p className="muted small">{aviso}</p>}

      {resultados && resultados.length > 0 && (
        <ul className="ger-candidatos">
          {resultados.map((c) => (
            <li key={c.id}>
              <button
                onClick={async () => {
                  await api.confirmMatch(work.id, c.id);
                  setResultados(null);
                  onChanged();
                }}
              >
                {c.poster_url && <img src={c.poster_url} alt="" loading="lazy" />}
                <span className="ger-cand-texto">
                  <b>{c.title}</b>
                  <span className="muted small">
                    {[c.year, c.provider, `${Math.round(c.score * 100)}%`]
                      .filter(Boolean)
                      .join(" · ")}
                  </span>
                </span>
              </button>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

/// O que o parser entendeu do caminho — e a correção, que é humana e fica.
function Parser({ work, onChanged }: { work: WorkDetail; onChanged: () => void }) {
  const [titulo, setTitulo] = useState("");
  const [temporada, setTemporada] = useState("");
  const [episodio, setEpisodio] = useState("");
  const [salvo, setSalvo] = useState(false);

  const salvar = async () => {
    const parse: Parameters<typeof api.setParse>[1] = {};
    if (titulo.trim()) parse.title = titulo.trim();
    if (temporada) parse.season = Number(temporada);
    if (episodio) parse.episode = Number(episodio);
    if (Object.keys(parse).length === 0) return;
    await api.setParse(work.id, parse);
    setSalvo(true);
    window.setTimeout(() => setSalvo(false), 1600);
    onChanged();
  };

  return (
    <section className="ger-secao">
      <h3>Corrigir o que o parser entendeu</h3>
      <p className="muted small">
        Sobrevive a nova varredura e a nova identificação — é decisão humana, não
        resultado.
      </p>
      <div className="ger-linha">
        <input
          value={titulo}
          onChange={(e) => setTitulo(e.target.value)}
          placeholder={`título (hoje: ${work.title})`}
        />
        <input
          className="ger-num"
          value={temporada}
          onChange={(e) => setTemporada(e.target.value.replace(/\D/g, ""))}
          placeholder={work.season_number != null ? `T${work.season_number}` : "T"}
        />
        <input
          className="ger-num"
          value={episodio}
          onChange={(e) => setEpisodio(e.target.value.replace(/\D/g, ""))}
          placeholder={work.episode_number != null ? `E${work.episode_number}` : "E"}
        />
        <button className="ghost small-btn" onClick={salvar}>
          {salvo ? "guardado" : "guardar"}
        </button>
        <button
          className="ghost small-btn"
          onClick={async () => {
            await api.clearParse(work.id);
            onChanged();
          }}
        >
          limpar correção
        </button>
      </div>
    </section>
  );
}

// ------------------------------------------------------------ zona de risco

function ZonaDeRisco({
  work,
  podeApagar,
  onSumiu,
  onChanged,
}: {
  work: WorkDetail;
  podeApagar: { pode: boolean; motivo: string | null } | null;
  onSumiu: () => void;
  onChanged: () => void;
}) {
  const [confirmando, setConfirmando] = useState(false);
  const [digitado, setDigitado] = useState("");
  const [erro, setErro] = useState<string | null>(null);
  const [ocupado, setOcupado] = useState(false);

  const bytes = work.files.reduce((n, f) => n + f.size_bytes, 0);
  const liberado = bytes ? `${(bytes / 1e9).toFixed(1).replace(".", ",")} GB` : "";

  return (
    <section className="ger-secao risco">
      <h3>Zona de risco</h3>

      <div className="ger-risco-linha">
        <div>
          <b>Ignorar</b>
          <p className="muted small">
            Some da biblioteca e não volta na varredura. O arquivo fica onde está.
          </p>
        </div>
        <button
          className="ghost small-btn"
          disabled={work.match_state === "ignored"}
          onClick={async () => {
            await api.ignoreWork(work.id, "ignorada pela gaveta de gerenciar");
            onChanged();
          }}
        >
          {work.match_state === "ignored" ? "já ignorada" : "ignorar"}
        </button>
      </div>

      <div className="ger-risco-linha">
        <div>
          <b>Apagar do disco</b>
          <p className="muted small">
            {podeApagar === null
              ? "perguntando ao servidor se dá…"
              : podeApagar.pode
                ? `Apaga ${work.files.length} arquivo${work.files.length > 1 ? "s" : ""}${
                    liberado ? ` (${liberado})` : ""
                  } e o registro. Não tem volta.`
                : podeApagar.motivo}
          </p>
        </div>
        <button
          className="ghost small-btn perigo"
          disabled={!podeApagar?.pode || work.files.length === 0}
          onClick={() => setConfirmando(true)}
        >
          apagar
        </button>
      </div>

      {confirmando && (
        <div className="ger-confirma">
          <p>
            Digite <b>apagar</b> para confirmar. Vão sumir do disco:
          </p>
          <ul>
            {work.files.map((f) => (
              <li key={f.id}>
                <code>{f.path}</code>
              </li>
            ))}
          </ul>
          {erro && <p className="error small">{erro}</p>}
          <div className="ger-linha">
            <input
              autoFocus
              value={digitado}
              onChange={(e) => setDigitado(e.target.value)}
              placeholder="apagar"
            />
            <button
              className="ghost small-btn perigo"
              disabled={digitado.trim().toLowerCase() !== "apagar" || ocupado}
              onClick={async () => {
                setOcupado(true);
                setErro(null);
                try {
                  await api.deleteWork(work.id, true);
                  onSumiu();
                } catch (e) {
                  setErro(String(e));
                } finally {
                  setOcupado(false);
                }
              }}
            >
              {ocupado ? "apagando…" : "apagar de vez"}
            </button>
            <button className="ghost small-btn" onClick={() => setConfirmando(false)}>
              cancelar
            </button>
          </div>
        </div>
      )}
    </section>
  );
}
