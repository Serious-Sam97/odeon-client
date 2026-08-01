import { useCallback, useEffect, useState } from "react";
import { api, type MatchCandidate, type ReviewItem } from "./api";

/// A fila de revisão. O que o Jellyfin nunca te mostra: o que ele entendeu do
/// nome do arquivo, quais opções considerou, e POR QUE deu o score que deu.
export default function Review({ onChanged }: { onChanged: () => void }) {
  const [items, setItems] = useState<ReviewItem[]>([]);
  const [total, setTotal] = useState(0);
  const [offset, setOffset] = useState(0);
  const [estado, setEstado] = useState("needs_review");
  // `null` = tanto faz. É o filtro que separa "escolher qual" de "corrigir o
  // nome do arquivo" — dois problemas com ações diferentes que a fila antiga
  // misturava.
  const [comCandidatos, setComCandidatos] = useState<boolean | null>(null);
  const [q, setQ] = useState("");
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState<string | null>(null);

  const PAGINA = 50;

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const page = await api.review({
        state: estado,
        hasCandidates: comCandidatos ?? undefined,
        q,
        limit: PAGINA,
        offset,
      });
      setItems(page.items);
      setTotal(page.total);
    } finally {
      setLoading(false);
    }
  }, [estado, comCandidatos, q, offset]);

  useEffect(() => {
    load();
  }, [load]);

  const confirm = async (workId: string, candidateId: string) => {
    setBusy(workId);
    try {
      await api.confirmMatch(workId, candidateId);
      setItems((prev) => prev.filter((i) => i.work.id !== workId));
      setTotal((t) => Math.max(0, t - 1));
      onChanged();
    } finally {
      setBusy(null);
    }
  };

  const pagina = Math.floor(offset / PAGINA) + 1;
  const paginas = Math.max(1, Math.ceil(total / PAGINA));

  const trocar = (fn: () => void) => {
    setOffset(0);
    fn();
  };

  return (
    <div>
      <div className="fila-filtros">
        <select
          value={estado}
          onChange={(e) => trocar(() => setEstado(e.target.value))}
        >
          <option value="needs_review">em dúvida</option>
          <option value="unmatched">sem match</option>
          <option value="needs_review,unmatched">as duas</option>
        </select>

        <select
          value={comCandidatos === null ? "" : String(comCandidatos)}
          onChange={(e) =>
            trocar(() =>
              setComCandidatos(e.target.value === "" ? null : e.target.value === "true"),
            )
          }
        >
          <option value="">qualquer</option>
          <option value="true">tem candidato — é escolher</option>
          <option value="false">sem candidato — é o nome do arquivo</option>
        </select>

        <input
          placeholder="filtrar por nome…"
          value={q}
          onChange={(e) => trocar(() => setQ(e.target.value))}
        />

        <span className="muted fila-total">
          {total} {total === 1 ? "obra" : "obras"}
        </span>
      </div>

      {loading && <p className="muted">carregando fila…</p>}

      {!loading && items.length === 0 && (
        <div className="empty">
          <p>Nada aqui.</p>
          <p className="muted">
            {q || comCandidatos !== null
              ? "Nenhuma obra com esses filtros."
              : "Nada em dúvida — ou nada foi identificado ainda."}
          </p>
        </div>
      )}

      <div className="review-list">
        {items.map((item) => (
          <ReviewCard
            key={item.work.id}
            item={item}
            busy={busy === item.work.id}
            onConfirm={confirm}
          />
        ))}
      </div>

      {/* A fila antiga mostrava 50 itens e escondia os outros 2.338 sem dizer
          que existiam. A paginação é o que torna o resto alcançável. */}
      {total > PAGINA && (
        <div className="fila-paginacao">
          <button
            disabled={offset === 0}
            onClick={() => setOffset(Math.max(0, offset - PAGINA))}
          >
            anterior
          </button>
          <span className="muted">
            página {pagina} de {paginas}
          </span>
          <button
            disabled={offset + PAGINA >= total}
            onClick={() => setOffset(offset + PAGINA)}
          >
            próxima
          </button>
        </div>
      )}
    </div>
  );
}

function ReviewCard({
  item,
  busy,
  onConfirm,
}: {
  item: ReviewItem;
  busy: boolean;
  onConfirm: (workId: string, candidateId: string) => void;
}) {
  const [candidates, setCandidates] = useState(item.candidates);
  const [query, setQuery] = useState(item.guess.title);
  const [searching, setSearching] = useState(false);
  const [corrigindo, setCorrigindo] = useState(false);
  const [acao, setAcao] = useState(false);
  const [g, setG] = useState(item.guess);
  const [parse, setParse] = useState({
    title: item.guess.title,
    season: item.guess.season?.toString() ?? "",
    episode: (item.guess.episode ?? item.guess.absolute_episode)?.toString() ?? "",
  });

  const search = async (termo = query) => {
    setSearching(true);
    try {
      setCandidates(await api.searchCandidates(item.work.id, termo));
    } finally {
      setSearching(false);
    }
  };

  const num = (s: string) => (s.trim() === "" ? null : Number(s));

  const salvarParse = async () => {
    setAcao(true);
    try {
      const r = await api.setParse(item.work.id, {
        title: parse.title,
        season: num(parse.season),
        episode: num(parse.episode),
      });
      setG(r.guess);
      setQuery(r.guess.title);
      // Buscar em seguida é o ponto: corrigir o parse só vale se o resultado
      // aparecer na hora.
      await search(r.guess.title);
    } finally {
      setAcao(false);
    }
  };

  const limparParse = async () => {
    setAcao(true);
    try {
      const r = await api.clearParse(item.work.id);
      setG(r.guess);
      setParse({
        title: r.guess.title,
        season: r.guess.season?.toString() ?? "",
        episode: (r.guess.episode ?? r.guess.absolute_episode)?.toString() ?? "",
      });
    } finally {
      setAcao(false);
    }
  };

  const reset = async () => {
    setAcao(true);
    try {
      const r = await api.resetMatch(item.work.id);
      setG(r.guess);
      setCandidates([]);
    } finally {
      setAcao(false);
    }
  };

  return (
    <article className="review-card">
      <header className="review-head">
        <div>
          <h3>{item.work.filename}</h3>
          <p className="muted small">
            O parser entendeu:{" "}
            <strong className="parsed">{g.title || "(nada)"}</strong>
            {g.year && ` · ${g.year}`}
            {g.season != null && g.episode != null && ` · S${g.season}E${g.episode}`}
            {g.absolute_episode != null && ` · ep ${g.absolute_episode}`}
            {g.release_group && ` · grupo ${g.release_group}`}
            {g.looks_like_anime && " · parece anime"}
          </p>
        </div>
        <span className="confidence">
          {item.work.match_confidence != null
            ? `${Math.round(item.work.match_confidence * 100)}% de confiança`
            : "sem score"}
        </span>
      </header>

      {/* Por que a obra está aqui, quando o motivo não veio de um candidato.
          É o caso das que foram devolvidas por contradizer o provider, e das
          que entraram por propagação de escopo — sem isto a pessoa abre a fila
          e não tem como saber o que houve. */}
      {item.work.match_reasons?.length > 0 && (
        <ul className="review-motivos">
          {item.work.match_reasons.map((m) => (
            <li key={m}>{m}</li>
          ))}
        </ul>
      )}

      <div className="review-search">
        <input
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          onKeyDown={(e) => e.key === "Enter" && search()}
          placeholder="buscar com outro nome…"
        />
        <button className="ghost" onClick={() => search()} disabled={searching}>
          {searching ? "buscando…" : "buscar de novo"}
        </button>
        <button className="ghost" onClick={() => setCorrigindo((v) => !v)}>
          {corrigindo ? "fechar" : "corrigir o parse"}
        </button>
        <button className="ghost" onClick={reset} disabled={acao}>
          desfazer
        </button>
      </div>

      {/* Corrigir o PARSE é diferente de escolher outro candidato: quando o
          nome do arquivo não diz a temporada, nenhum candidato resolve — é o
          número que está faltando. Esta é a saída para as 6.750 obras cujo
          problema é o nome, não a escolha. */}
      {corrigindo && (
        <div className="parse-form">
          <label>
            título
            <input
              value={parse.title}
              onChange={(e) => setParse({ ...parse, title: e.target.value })}
            />
          </label>
          <label>
            temporada
            <input
              type="number"
              value={parse.season}
              onChange={(e) => setParse({ ...parse, season: e.target.value })}
            />
          </label>
          <label>
            episódio
            <input
              type="number"
              value={parse.episode}
              onChange={(e) => setParse({ ...parse, episode: e.target.value })}
            />
          </label>
          <button onClick={salvarParse} disabled={acao}>
            salvar e buscar
          </button>
          <button className="ghost" onClick={limparParse} disabled={acao}>
            voltar ao nome do arquivo
          </button>
        </div>
      )}

      <div className="candidates">
        {candidates.length === 0 && (
          <p className="muted small">Nenhum candidato. Tente outro nome acima.</p>
        )}
        {candidates.map((c) => (
          <CandidateRow
            key={c.id}
            candidate={c}
            busy={busy}
            onPick={() => onConfirm(item.work.id, c.id)}
          />
        ))}
      </div>
    </article>
  );
}

function CandidateRow({
  candidate,
  busy,
  onPick,
}: {
  candidate: MatchCandidate;
  busy: boolean;
  onPick: () => void;
}) {
  const percent = Math.round(candidate.score * 100);
  const tone = percent >= 85 ? "good" : percent >= 55 ? "maybe" : "weak";

  return (
    <div className={`candidate ${tone}`}>
      {candidate.poster_url ? (
        <img src={candidate.poster_url} alt="" loading="lazy" />
      ) : (
        <div className="candidate-noart" />
      )}

      <div className="candidate-body">
        <h4>
          {candidate.title}
          {candidate.year && <span className="muted"> ({candidate.year})</span>}
        </h4>
        <p className="muted small">
          {candidate.provider} · {candidate.provider_kind}
          {candidate.original_title && candidate.original_title !== candidate.title && (
            <> · {candidate.original_title}</>
          )}
        </p>

        {/* Os motivos do score. É isto que torna o match auditável. */}
        <ul className="reasons">
          {(candidate.reasons ?? []).map((reason, i) => (
            <li key={i}>{reason}</li>
          ))}
        </ul>
      </div>

      <div className="candidate-action">
        <span className={`score ${tone}`}>{percent}%</span>
        <button className="primary small-btn" onClick={onPick} disabled={busy}>
          é esse
        </button>
      </div>
    </div>
  );
}
