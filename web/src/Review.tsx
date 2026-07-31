import { useCallback, useEffect, useState } from "react";
import { api, type MatchCandidate, type ReviewItem } from "./api";

/// A fila de revisão. O que o Jellyfin nunca te mostra: o que ele entendeu do
/// nome do arquivo, quais opções considerou, e POR QUE deu o score que deu.
export default function Review({ onChanged }: { onChanged: () => void }) {
  const [items, setItems] = useState<ReviewItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setItems(await api.review());
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const confirm = async (workId: string, candidateId: string) => {
    setBusy(workId);
    try {
      await api.confirmMatch(workId, candidateId);
      setItems((prev) => prev.filter((i) => i.work.id !== workId));
      onChanged();
    } finally {
      setBusy(null);
    }
  };

  if (loading) return <p className="muted">carregando fila…</p>;

  if (items.length === 0) {
    return (
      <div className="empty">
        <p>Fila vazia.</p>
        <p className="muted">
          Nada em dúvida — ou nada foi identificado ainda. Rode <em>identificar</em>.
        </p>
      </div>
    );
  }

  return (
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

  const search = async () => {
    setSearching(true);
    try {
      setCandidates(await api.searchCandidates(item.work.id, query));
    } finally {
      setSearching(false);
    }
  };

  const g = item.guess;

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
          {Math.round((item.work.match_confidence ?? 0) * 100)}% de confiança
        </span>
      </header>

      <div className="review-search">
        <input
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          onKeyDown={(e) => e.key === "Enter" && search()}
          placeholder="buscar com outro nome…"
        />
        <button className="ghost" onClick={search} disabled={searching}>
          {searching ? "buscando…" : "buscar de novo"}
        </button>
      </div>

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
