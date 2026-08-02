import { useCallback, useEffect, useState } from "react";
import { api, type Aparelho, type ContaUsuario, type Trabalho } from "./api";

/// A área de administração.
///
/// Não é funcionalidade nova: é a **tela que faltava** para um poder que já
/// estava no backend. Sete rotas existiam sem nenhum cliente — usuários,
/// sessões, histórico de trabalhos e as quatro manutenções —, e quatro delas
/// só eram alcançáveis por `curl`. Ver docs/DESIGN.md §27.
type Saude = Awaited<ReturnType<typeof api.diagnostico>>;

export default function Admin({ eu }: { eu: string }) {
  const [saude, setSaude] = useState<Saude | null>(null);
  const [usuarios, setUsuarios] = useState<ContaUsuario[]>([]);
  const [aparelhos, setAparelhos] = useState<Aparelho[]>([]);
  const [trabalhos, setTrabalhos] = useState<Trabalho[]>([]);
  const [erro, setErro] = useState<string | null>(null);

  const carregar = useCallback(() => {
    api.diagnostico().then(setSaude).catch(() => {});
    api.usuarios().then(setUsuarios).catch(() => {});
    api.sessoes().then(setAparelhos).catch(() => {});
    api.trabalhos().then(setTrabalhos).catch(() => {});
  }, []);

  useEffect(carregar, [carregar]);

  const proteger = (fn: () => Promise<unknown>) => async () => {
    setErro(null);
    try {
      await fn();
      carregar();
    } catch (e) {
      setErro(e instanceof Error ? e.message : String(e));
    }
  };

  return (
    <div className="adm">
      <header className="adm-topo">
        <div>
          <p className="adm-selo">administração</p>
          <h1>O servidor por dentro</h1>
        </div>
        <p className="adm-quem">{eu} · administrador</p>
      </header>

      {erro && <p className="error">{erro}</p>}

      {saude && <Saudinha s={saude} />}

      <Pessoas usuarios={usuarios} eu={eu} proteger={proteger} />
      <Aparelhos lista={aparelhos} proteger={proteger} />
      <Trabalhos lista={trabalhos} proteger={proteger} />
      <Manutencao />
    </div>
  );
}

// ------------------------------------------------------------------ saúde

/// Mesma regra do §24: **só mostra o que está torto**. Um painel que repete
/// "0 erros" em cinco linhas ensina a não ser lido.
function Saudinha({ s }: { s: Saude }) {
  const linhas: { rotulo: string; valor: string; grave?: boolean }[] = [];
  const n = (x: number) => x.toLocaleString("pt-BR");

  if (s.arquivos.com_erro > 0)
    linhas.push({ rotulo: "arquivos que o ffprobe recusa", valor: n(s.arquivos.com_erro), grave: true });
  if (s.arquivos.sumidos > 0)
    linhas.push({ rotulo: "arquivos sumidos do disco", valor: n(s.arquivos.sumidos), grave: true });

  const h = s.ao_vivo.horas_de_grade;
  if (h != null) linhas.push({ rotulo: "grade de TV à frente", valor: `${h}h`, grave: h < 12 });
  for (const f of s.ao_vivo.fontes)
    if (f.erro) linhas.push({ rotulo: `fonte ${f.nome}`, valor: f.erro.slice(0, 60), grave: true });

  if (s.identificacao.revisar > 0)
    linhas.push({ rotulo: "esperando revisão", valor: n(s.identificacao.revisar) });
  if (s.identificacao.sem_identificacao > 0)
    linhas.push({ rotulo: "sem identificação", valor: n(s.identificacao.sem_identificacao) });
  if (s.sprites.prontos < s.sprites.de)
    linhas.push({ rotulo: "preview de seek", valor: `${n(s.sprites.prontos)} de ${n(s.sprites.de)}` });

  return (
    <Secao titulo="Saúde" dica="só o que está torto">
      {linhas.length === 0 ? (
        <p className="muted small">nada torto por aqui.</p>
      ) : (
        <ul className="saude-lista">
          {linhas.map((l) => (
            <li key={l.rotulo} className={l.grave ? "grave" : undefined}>
              <span>{l.rotulo}</span>
              <b>{l.valor}</b>
            </li>
          ))}
        </ul>
      )}
      {s.arquivos.amostra.length > 0 && (
        <details className="saude-detalhe">
          <summary>quais arquivos</summary>
          <ul>
            {s.arquivos.amostra.map((a: { arquivo: string; estado: string }) => (
              <li key={a.arquivo}>
                <code>{a.arquivo}</code>
              </li>
            ))}
          </ul>
        </details>
      )}
    </Secao>
  );
}

function Secao({
  titulo,
  dica,
  acao,
  children,
}: {
  titulo: string;
  dica?: string;
  acao?: React.ReactNode;
  children: React.ReactNode;
}) {
  return (
    <section className="adm-secao">
      <header>
        <h3>{titulo}</h3>
        <span className="rule" />
        {dica && <i>{dica}</i>}
        {acao}
      </header>
      {children}
    </section>
  );
}

const dia = (s: string | null) =>
  s ? new Date(s).toLocaleDateString("pt-BR", { day: "2-digit", month: "2-digit" }) : "—";
const hora = (s: string | null) =>
  s ? new Date(s).toLocaleTimeString("pt-BR", { hour: "2-digit", minute: "2-digit" }) : "";

// ---------------------------------------------------------------- pessoas

function Pessoas({
  usuarios,
  eu,
  proteger,
}: {
  usuarios: ContaUsuario[];
  eu: string;
  proteger: (fn: () => Promise<unknown>) => () => Promise<void>;
}) {
  const [abrindo, setAbrindo] = useState(false);
  const [nome, setNome] = useState("");
  const [senha, setSenha] = useState("");
  const [papel, setPapel] = useState<"admin" | "user">("user");

  return (
    <Secao
      titulo="Pessoas"
      acao={
        <button className="chip" onClick={() => setAbrindo(!abrindo)}>
          {abrindo ? "cancelar" : "+ convidar"}
        </button>
      }
    >
      {abrindo && (
        <div className="adm-form">
          <input value={nome} onChange={(e) => setNome(e.target.value)} placeholder="usuário" />
          <input
            type="password"
            value={senha}
            onChange={(e) => setSenha(e.target.value)}
            placeholder="senha"
          />
          <select
            className="select"
            value={papel}
            onChange={(e) => setPapel(e.target.value as "admin" | "user")}
          >
            <option value="user">usuário</option>
            <option value="admin">administrador</option>
          </select>
          <button
            className="ghost small-btn"
            disabled={!nome.trim() || senha.length < 4}
            onClick={proteger(async () => {
              await api.criarUsuario({ username: nome.trim(), password: senha, role: papel });
              setNome("");
              setSenha("");
              setAbrindo(false);
            })}
          >
            criar
          </button>
        </div>
      )}

      <table className="adm-tabela">
        <tbody>
          {usuarios.map((u) => {
            const souEu = u.username === eu;
            return (
              <tr key={u.id}>
                <td>
                  <b>{u.display_name}</b>
                  <i>{u.username}</i>
                </td>
                <td>
                  <span className={u.role === "admin" ? "tag admin" : "tag"}>{u.role}</span>
                </td>
                <td>{u.is_active ? "ativo" : "desativado"}</td>
                <td className="adm-num">{dia(u.last_login_at)}</td>
                <td className="acoes">
                  {/* Sem botão na própria conta: rebaixar-se ou desativar-se é
                      um beco sem saída, e o backend recusa. Melhor não
                      oferecer do que oferecer e negar — mas a célula vazia
                      pareceria falha, então ela diz de quem é a linha. */}
                  {souEu && <span className="adm-eu">você</span>}
                  {!souEu && (
                    <>
                      <button
                        className="mini"
                        onClick={proteger(() =>
                          api.mudarUsuario(u.id, {
                            role: u.role === "admin" ? "user" : "admin",
                          }),
                        )}
                      >
                        {u.role === "admin" ? "rebaixar" : "promover"}
                      </button>
                      <button
                        className="mini"
                        onClick={proteger(() =>
                          api.mudarUsuario(u.id, { is_active: !u.is_active }),
                        )}
                      >
                        {u.is_active ? "desativar" : "reativar"}
                      </button>
                      <button
                        className="mini perigo"
                        onClick={proteger(async () => {
                          if (!window.confirm(`apagar a conta de ${u.username}?`)) return;
                          await api.apagarUsuario(u.id);
                        })}
                      >
                        remover
                      </button>
                    </>
                  )}
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </Secao>
  );
}

// -------------------------------------------------------------- aparelhos

function Aparelhos({
  lista,
  proteger,
}: {
  lista: Aparelho[];
  proteger: (fn: () => Promise<unknown>) => () => Promise<void>;
}) {
  return (
    <Secao
      titulo="Aparelhos"
      dica={`${lista.length} ${lista.length === 1 ? "sessão aberta" : "sessões abertas"}`}
    >
      <table className="adm-tabela">
        <tbody>
          {lista.map((s) => (
            <tr key={s.id}>
              <td>
                <b>{s.device_label || "sem nome"}</b>
                <i>{(s.user_agent || "").slice(0, 60)}</i>
              </td>
              <td className="adm-num">visto {dia(s.last_seen_at)}</td>
              <td className="adm-num">expira {dia(s.expires_at)}</td>
              <td className="acoes">
                <button className="mini perigo" onClick={proteger(() => api.encerrarSessao(s.id))}>
                  encerrar
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </Secao>
  );
}

// -------------------------------------------------------------- trabalhos

const ESTADO: Record<string, string> = {
  succeeded: "concluiu",
  cancelled: "cancelado",
  failed: "falhou",
  interrupted: "interrompido",
  running: "rodando",
  queued: "na fila",
};

function resumo(j: Trabalho): string {
  const p = (j.progress ?? {}) as Record<string, unknown>;
  if (j.kind === "live_import" && p.canais != null) {
    return `${p.canais} canais · ${p.programas} programas`;
  }
  if (j.total) return `${j.done ?? 0} de ${j.total}`;
  if (j.error) return j.error.slice(0, 60);
  return j.done ? `${j.done} feitos` : "—";
}

function Trabalhos({
  lista,
  proteger,
}: {
  lista: Trabalho[];
  proteger: (fn: () => Promise<unknown>) => () => Promise<void>;
}) {
  return (
    <Secao titulo="Trabalhos" dica={`${lista.length} no histórico`}>
      <table className="adm-tabela jobs">
        <tbody>
          {lista.map((j) => (
            <tr key={j.id}>
              <td>
                <b>{j.kind}</b>
              </td>
              <td>
                <span className={`estado ${j.state}`}>{ESTADO[j.state] ?? j.state}</span>
              </td>
              <td className="adm-num">
                {dia(j.started_at)} {hora(j.started_at)}
              </td>
              <td>{resumo(j)}</td>
              <td className="acoes">
                {j.state === "running" && !j.cancel_requested && (
                  <button className="mini perigo" onClick={proteger(() => api.cancelarTrabalho(j.id))}>
                    cancelar
                  </button>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </Secao>
  );
}

// ------------------------------------------------------------- manutenção

const MANUT = [
  {
    id: "repair-series" as const,
    titulo: "Enriquecer as séries",
    texto: "Dá sinopse, ids do provider e arte às coleções-série. Um GET por série.",
  },
  {
    id: "repair-episode-titles" as const,
    titulo: "Reparar títulos de episódio",
    texto: "Acha o nome real dos episódios que ficaram como “Episódio N”.",
  },
  {
    id: "reparse" as const,
    titulo: "Reprocessar o parse",
    texto: "Relê o caminho dos arquivos com o parser atual, sem tocar no provider.",
  },
  {
    id: "artwork-orfao" as const,
    titulo: "Limpar artwork órfão",
    texto: "Apaga imagem em disco que nenhuma linha do banco referencia.",
  },
];

/// As quatro manutenções, e a decisão de desenho que as sustenta: **o ensaio é
/// a interface**.
///
/// Todas aceitam `dry_run`, e todas o têm ligado por padrão. O painel só
/// oferece executar depois de ensaiar, com o número na frente — que é
/// exatamente como elas foram usadas à mão. Um botão "executar" sem o número
/// antes seria pedir confiança que ninguém tem por que dar.
function Manutencao() {
  const [res, setRes] = useState<Record<string, string>>({});
  const [ensaiado, setEnsaiado] = useState<Record<string, boolean>>({});
  const [rodando, setRodando] = useState<string | null>(null);

  const chamar = async (id: (typeof MANUT)[number]["id"], dry: boolean) => {
    setRodando(id);
    try {
      const r = await api.manutencao(id, dry);
      setRes((m) => ({ ...m, [id]: descreve(r) }));
      setEnsaiado((m) => ({ ...m, [id]: dry }));
    } catch (e) {
      setRes((m) => ({ ...m, [id]: e instanceof Error ? e.message : String(e) }));
      setEnsaiado((m) => ({ ...m, [id]: false }));
    } finally {
      setRodando(null);
    }
  };

  return (
    <Secao titulo="Manutenção" dica="o ensaio vem antes">
      <p className="adm-nota">
        Todas rodam em <b>ensaio</b> primeiro: contam o que fariam e não escrevem nada. O
        botão de executar só aparece depois, com o número na frente.
      </p>
      <div className="mnt-grade">
        {MANUT.map((m) => (
          <div key={m.id} className="mnt">
            <h4>{m.titulo}</h4>
            <p>{m.texto}</p>
            <div className="mnt-acoes">
              <button
                className="ghost small-btn"
                disabled={rodando === m.id}
                onClick={() => chamar(m.id, true)}
              >
                {rodando === m.id ? "…" : "ensaiar"}
              </button>
              {ensaiado[m.id] && (
                <button
                  className="ghost small-btn perigo"
                  disabled={rodando === m.id}
                  onClick={() => chamar(m.id, false)}
                >
                  executar
                </button>
              )}
              <span className="mnt-res">{res[m.id] ?? "ainda não ensaiado"}</span>
            </div>
          </div>
        ))}
      </div>
    </Secao>
  );
}

/// Cada manutenção devolve um formato diferente. Em vez de quatro leitores,
/// um: os números que interessam, na ordem em que interessam.
function descreve(r: Record<string, unknown>): string {
  const num = (k: string) => (typeof r[k] === "number" ? (r[k] as number) : null);
  const partes: string[] = [];
  for (const [k, rot] of [
    ["series_enriquecidas", "séries"],
    ["obras_repontadas", "obras repontadas"],
    ["corrigidas", "títulos"],
    ["atualizadas", "obras"],
    ["arquivos", "arquivos"],
  ] as const) {
    const v = num(k);
    if (v != null) partes.push(`${v.toLocaleString("pt-BR")} ${rot}`);
  }
  if (typeof r.gb === "string") partes.push(`${r.gb} GB`);
  if (partes.length === 0) partes.push(JSON.stringify(r).slice(0, 60));
  return (r.dry_run === false ? "feito: " : "ensaio: ") + partes.join(" · ");
}
