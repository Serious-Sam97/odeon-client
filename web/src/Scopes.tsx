import { useCallback, useEffect, useState } from "react";
import {
  api,
  type MatchCandidate,
  type ScopePreview,
  type ScopeRow,
} from "./api";

/// Identificação com a PASTA como unidade.
///
/// A fila por arquivo pede a mesma resposta centenas de vezes: uma pasta de
/// série tem um nome só, e todos os arquivos dentro dela são daquela série.
/// Aqui a pergunta é feita uma vez.
///
/// O fluxo é sempre o mesmo, e o passo do meio não é pulável: escolher a obra →
/// **ver o que vai acontecer** → aplicar. Escrever centenas de linhas sem
/// mostrar antes é o oposto do que o projeto defende.
export default function Scopes({ onChanged }: { onChanged: () => void }) {
  const [rows, setRows] = useState<ScopeRow[]>([]);
  const [total, setTotal] = useState(0);
  const [q, setQ] = useState("");
  const [loading, setLoading] = useState(true);
  const [aberta, setAberta] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const page = await api.reviewScopes({ q, limit: 50 });
      setRows(page.items);
      setTotal(page.total);
    } finally {
      setLoading(false);
    }
  }, [q]);

  useEffect(() => {
    const t = setTimeout(load, q ? 300 : 0);
    return () => clearTimeout(t);
  }, [load, q]);

  const resolvida = (dir: string) => {
    setRows((prev) => prev.filter((r) => r.dir_path !== dir));
    setTotal((t) => Math.max(0, t - 1));
    setAberta(null);
    onChanged();
  };

  const pendentesTotais = rows.reduce((s, r) => s + r.pendentes, 0);

  return (
    <div className="scopes">
      <div className="scopes-head">
        <div>
          <strong>{total}</strong> pastas com identificação pendente
          {rows.length > 0 && (
            <span className="muted">
              {" "}
              · {pendentesTotais} arquivos nas {rows.length} mostradas
            </span>
          )}
        </div>
        <input
          className="scopes-filtro"
          placeholder="filtrar por caminho…"
          value={q}
          onChange={(e) => setQ(e.target.value)}
        />
      </div>

      {loading && <p className="muted">carregando pastas…</p>}

      {!loading && rows.length === 0 && (
        <div className="empty">
          <p>Nenhuma pasta pendente.</p>
          <p className="muted">
            {q
              ? "Nada com esse filtro."
              : "Tudo identificado — ou nada varrido ainda."}
          </p>
        </div>
      )}

      {rows.map((row) => (
        <PastaCard
          key={row.dir_path}
          row={row}
          aberta={aberta === row.dir_path}
          onAbrir={() => setAberta(aberta === row.dir_path ? null : row.dir_path)}
          onResolvida={() => resolvida(row.dir_path)}
        />
      ))}
    </div>
  );
}

function PastaCard({
  row,
  aberta,
  onAbrir,
  onResolvida,
}: {
  row: ScopeRow;
  aberta: boolean;
  onAbrir: () => void;
  onResolvida: () => void;
}) {
  return (
    <div className={`pasta-card${aberta ? " aberta" : ""}`}>
      <button className="pasta-head" onClick={onAbrir}>
        <span className="pasta-contagem">{row.pendentes}</span>
        <span className="pasta-info">
          <span className="pasta-titulo">{row.titulo_sugerido || "(sem título)"}</span>
          <span className="pasta-caminho">{row.dir_path}</span>
          <span className="muted pasta-detalhe">
            {row.unmatched > 0 && `${row.unmatched} sem match`}
            {row.unmatched > 0 && row.needs_review > 0 && " · "}
            {row.needs_review > 0 && `${row.needs_review} em dúvida`}
            {row.ja_identificados > 0 && ` · ${row.ja_identificados} já ok`}
            {" · "}
            {row.library_name}
          </span>
        </span>
        {row.sibling_match && (
          <span className="pasta-dica" title="o que os arquivos já identificados desta pasta apontam">
            irmãos: {row.sibling_match.titulo}
          </span>
        )}
      </button>

      {aberta && <Decidir row={row} onResolvida={onResolvida} />}
    </div>
  );
}

/// Escolher a obra, conferir o preview, aplicar.
function Decidir({ row, onResolvida }: { row: ScopeRow; onResolvida: () => void }) {
  const [termo, setTermo] = useState(row.titulo_sugerido);
  const [candidatos, setCandidatos] = useState<MatchCandidate[]>([]);
  const [escolhido, setEscolhido] = useState<MatchCandidate | null>(null);
  const [numbering, setNumbering] = useState("seasonal");
  const [recursive, setRecursive] = useState(false);
  const [preview, setPreview] = useState<ScopePreview | null>(null);
  const [busy, setBusy] = useState<string | null>(null);
  const [erro, setErro] = useState<string | null>(null);

  // Se os irmãos já apontam uma obra, ela é a hipótese mais forte que existe:
  // não é palpite sobre o nome, é o que o próprio acervo já decidiu ali.
  const dica = row.sibling_match;

  const buscar = useCallback(async () => {
    setBusy("busca");
    setErro(null);
    try {
      const r = await api.scopeSearch(row.dir_path, termo || undefined);
      setCandidatos(r.candidatos);
      if (r.candidatos.length === 0) setErro("nada encontrado com esse título");
    } catch (e) {
      setErro(String(e));
    } finally {
      setBusy(null);
    }
  }, [row.dir_path, termo]);

  useEffect(() => {
    buscar();
    // só na abertura; depois é o usuário que dispara
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const corpo = (dry: boolean) => ({
    library_id: row.library_id,
    dir_path: row.dir_path,
    recursive,
    provider: escolhido!.provider,
    provider_id: escolhido!.provider_id,
    provider_kind: escolhido!.provider_kind,
    numbering,
    dry_run: dry,
  });

  const simular = async () => {
    if (!escolhido) return;
    setBusy("preview");
    setErro(null);
    try {
      setPreview(await api.scopeIdentify(corpo(true)));
    } catch (e) {
      setErro(String(e));
    } finally {
      setBusy(null);
    }
  };

  const aplicar = async () => {
    if (!escolhido || !preview) return;
    setBusy("aplicar");
    setErro(null);
    try {
      await api.scopeIdentify(corpo(false));
      onResolvida();
    } catch (e) {
      setErro(String(e));
      setBusy(null);
    }
  };

  return (
    <div className="pasta-corpo">
      <div className="pasta-exemplos">
        {row.exemplos.map((e) => (
          <code key={e}>{e}</code>
        ))}
      </div>

      <div className="pasta-busca">
        <input
          value={termo}
          onChange={(e) => setTermo(e.target.value)}
          onKeyDown={(e) => e.key === "Enter" && buscar()}
          placeholder="título da obra"
        />
        <button onClick={buscar} disabled={busy === "busca"}>
          {busy === "busca" ? "buscando…" : "buscar"}
        </button>
      </div>

      {dica && (
        <p className="muted pasta-sibling">
          Os {dica.obras} arquivos já identificados desta pasta apontam para{" "}
          <strong>{dica.titulo}</strong> ({dica.provider}:{dica.provider_id}).
        </p>
      )}

      <div className="pasta-candidatos">
        {candidatos.map((c) => (
          <button
            key={`${c.provider}:${c.provider_id}`}
            className={`cand${escolhido?.provider_id === c.provider_id ? " escolhido" : ""}`}
            onClick={() => {
              setEscolhido(c);
              setPreview(null);
            }}
          >
            {c.poster_url && <img src={c.poster_url} alt="" loading="lazy" />}
            <span>
              <strong>{c.title}</strong> {c.year && <span className="muted">({c.year})</span>}
              <br />
              <span className="muted">
                {c.provider} · {c.provider_kind}
                {dica?.provider_id === c.provider_id && " · o que os irmãos apontam"}
              </span>
            </span>
          </button>
        ))}
      </div>

      {escolhido && (
        <div className="pasta-opcoes">
          <label>
            numeração
            <select value={numbering} onChange={(e) => { setNumbering(e.target.value); setPreview(null); }}>
              <option value="seasonal">temporada/episódio (SxxExx)</option>
              <option value="absolute">absoluta (1..N, típica de fansub)</option>
              <option value="none">não é série</option>
            </select>
          </label>
          <label className="check">
            <input
              type="checkbox"
              checked={recursive}
              onChange={(e) => { setRecursive(e.target.checked); setPreview(null); }}
            />
            incluir subpastas (temporadas)
          </label>
          <button onClick={simular} disabled={busy === "preview"}>
            {busy === "preview" ? "simulando…" : "simular"}
          </button>
        </div>
      )}

      {erro && <p className="erro">{erro}</p>}

      {preview && (
        <div className="pasta-preview">
          <p>
            <strong>{preview.confirmariam}</strong> seriam identificados
            {(preview.ficariam_em_revisao ?? 0) > 0 && (
              <> · <strong>{preview.ficariam_em_revisao}</strong> ficariam em revisão</>
            )}
            <span className="muted">
              {" "}
              · {preview.chamadas_de_temporada} chamadas ao provider para{" "}
              {preview.afetados} arquivos
            </span>
          </p>

          <table>
            <tbody>
              {(preview.preview ?? []).slice(0, 12).map((p) => (
                <tr key={p.work_id} className={p.estado}>
                  <td className="arq">{p.arquivo}</td>
                  <td className="ep">
                    {p.temporada != null && p.episodio != null
                      ? `S${String(p.temporada).padStart(2, "0")}E${String(p.episodio).padStart(2, "0")}`
                      : "—"}
                  </td>
                  <td>{p.titulo_resolvido ?? <span className="muted">não resolveu</span>}</td>
                </tr>
              ))}
            </tbody>
          </table>

          {(preview.preview?.length ?? 0) > 12 && (
            <p className="muted">
              …e mais {(preview.afetados ?? 0) - 12} arquivos
            </p>
          )}

          <button className="aplicar" onClick={aplicar} disabled={busy === "aplicar"}>
            {busy === "aplicar"
              ? "aplicando…"
              : `aplicar aos ${preview.afetados} arquivos`}
          </button>
        </div>
      )}
    </div>
  );
}
