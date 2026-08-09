import { useCallback, useEffect, useState } from "react";
import Convites from "./Convites";
import {
  api,
  type Aparelho,
  type ContaUsuario,
  type OpcoesDaLocadora,
  type Trabalho,
} from "./api";

/// A área de administração.
///
/// Não é funcionalidade nova: é a **tela que faltava** para um poder que já
/// estava no backend. Sete rotas existiam sem nenhum cliente — usuários,
/// sessões, histórico de trabalhos e as quatro manutenções —, e quatro delas
/// só eram alcançáveis por `curl`. Ver DESIGN.md (repositório do servidor) §27.
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

  /// Só a lista de trabalhos, e não as quatro chamadas do `carregar`.
  ///
  /// É ela que anda enquanto um aquecimento roda; recarregar contas, aparelhos
  /// e diagnóstico a cada dois segundos seria pagar quatro requisições pra ver
  /// um número mudar.
  const recarregarTrabalhos = useCallback(() => {
    api.trabalhos().then(setTrabalhos).catch(() => {});
  }, []);

  /// O painel acompanha o que está rodando, e para quando nada está.
  const rodando = trabalhos.some((j) => j.state === "running");
  useEffect(() => {
    if (!rodando) return;
    const t = window.setInterval(recarregarTrabalhos, 2000);
    return () => window.clearInterval(t);
  }, [rodando, recarregarTrabalhos]);

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

      {/* R29: a loja vem primeiro entre as configurações porque é a única
          seção desta tela que muda o que as **outras pessoas** veem — o resto
          administra contas, aparelhos e trabalhos. */}
      <Loja />

      <Pessoas usuarios={usuarios} eu={eu} proteger={proteger} />
      {/* R26: convidar vem logo depois de "Pessoas" porque é o mesmo assunto —
          quem tem conta aqui — visto pelo outro lado: quem ainda não tem. */}
      <Convites />
      <Aparelhos lista={aparelhos} proteger={proteger} />
      <Trabalhos lista={trabalhos} proteger={proteger} />
      <Aquecimentos lista={trabalhos} recarregar={recarregarTrabalhos} />
      <Manutencao />
    </div>
  );
}

// -------------------------------------------------------------------- loja

/// As opções da locadora.
///
/// ## Por que estes quatro números existem
///
/// A R20 (§36) escondeu três deles no binário e um em coluna sem tela. O
/// `IDEIAS.md` §3.2 é explícito: *"os números — tamanho do estoque, prazo,
/// quantas por pessoa, escassez ligada ou não — são **opções no menu do
/// servidor**, para serem customizados"*. Esta é a tela que faltava.
///
/// ## Cada campo diz o que muda, e não o que é
///
/// "Estoque: 40" não informa nada a quem não escreveu o código. **"40 das 600
/// caixas ficam expostas por semana"** informa. É a mesma regra que fez a placa
/// da estante dizer "3 de 113" em vez de "3": um número sem o denominador
/// convida à conclusão errada (§14).
function Loja() {
  const [o, setO] = useState<OpcoesDaLocadora | null>(null);
  const [salvo, setSalvo] = useState<OpcoesDaLocadora | null>(null);
  const [erro, setErro] = useState<string | null>(null);
  const [salvando, setSalvando] = useState(false);

  useEffect(() => {
    api
      .opcoesDaLocadora()
      .then((r) => {
        setO(r);
        setSalvo(r);
      })
      .catch((e) => setErro(String(e)));
  }, []);

  if (erro) return <p className="error">{erro}</p>;
  if (!o || !salvo) return null;

  // Botão só aparece quando há o que salvar. Um "salvar" permanentemente
  // clicável ensina a clicar sem olhar.
  const mudou =
    o.estoque !== salvo.estoque ||
    o.prazo_dias !== salvo.prazo_dias ||
    o.limite_por_pessoa !== salvo.limite_por_pessoa ||
    o.escassez !== salvo.escassez;

  const salvar = async () => {
    setSalvando(true);
    setErro(null);
    try {
      const r = await api.salvarOpcoesDaLocadora(o);
      setO(r);
      setSalvo(r);
    } catch (e) {
      setErro(e instanceof Error ? e.message : String(e));
      // Volta pro que está gravado: deixar na tela um número que o servidor
      // recusou faria a próxima visita acreditar que ele valeu.
      setO(salvo);
    } finally {
      setSalvando(false);
    }
  };

  return (
    <Secao
      titulo="A locadora"
      dica="vale pra todo mundo"
      acao={
        mudou ? (
          <button className="chip" disabled={salvando} onClick={() => void salvar()}>
            {salvando ? "salvando…" : "salvar"}
          </button>
        ) : undefined
      }
    >
      <div className="adm-opcoes">
        <Numero
          rotulo="Estoque"
          valor={o.estoque}
          min={1}
          max={1000}
          onChange={(v) => setO({ ...o, estoque: v })}
          explica={`caixas expostas na loja inteira por semana — não por estante. A vitrine vira toda segunda.`}
        />
        <Numero
          rotulo="Prazo"
          valor={o.prazo_dias}
          min={1}
          max={90}
          sufixo={o.prazo_dias === 1 ? "dia" : "dias"}
          onChange={(v) => setO({ ...o, prazo_dias: v })}
          explica="depois disso a fita volta sozinha — menos se alguém estiver assistindo na hora."
        />
        <Numero
          rotulo="Por pessoa"
          valor={o.limite_por_pessoa}
          min={1}
          max={50}
          sufixo={o.limite_por_pessoa === 1 ? "caixa" : "caixas"}
          onChange={(v) => setO({ ...o, limite_por_pessoa: v })}
          explica="quantas cada um segura ao mesmo tempo. O limite é a feature, não o obstáculo."
        />

        {/* A chave é a única opção desta seção que muda uma **regra**, e não um
            número — por isso ela tem a linha mais longa. Desligar sem dizer o
            que sai seria o botão que ninguém entende até já ter clicado. */}
        <label className="adm-chave">
          <input
            type="checkbox"
            checked={o.escassez}
            onChange={(e) => setO({ ...o, escassez: e.target.checked })}
          />
          <span>
            <b>Escassez</b>
            <i>
              {o.escassez
                ? "uma cópia por caixa: quem pegou tirou da prateleira, e os outros pedem de volta."
                : "ninguém barra ninguém — a loja continua curta, mas duas pessoas pegam a mesma caixa."}
            </i>
          </span>
        </label>
      </div>

      {/* Uma fita que saiu sob a escassez continua trancada até voltar, e isso
          precisa ser dito: desligar a chave e ver "fulano está com esta" leria
          como a opção não ter pegado. */}
      {!o.escassez && (
        <p className="adm-nota">
          O que já está emprestado continua exclusivo até voltar — a fita está com
          alguém, e desligar uma opção não a traz de volta.
        </p>
      )}
    </Secao>
  );
}

/// Um número com nome, unidade e uma frase dizendo o que ele faz.
function Numero({
  rotulo,
  valor,
  min,
  max,
  sufixo,
  explica,
  onChange,
}: {
  rotulo: string;
  valor: number;
  min: number;
  max: number;
  sufixo?: string;
  explica: string;
  onChange: (v: number) => void;
}) {
  return (
    <label className="adm-opcao">
      <span className="adm-opcao-nome">{rotulo}</span>
      <span className="adm-opcao-campo">
        <input
          type="number"
          value={valor}
          min={min}
          max={max}
          onChange={(e) => {
            // Campo vazio vira o mínimo, e não `NaN`: um input controlado com
            // NaN dentro para de aceitar digitação.
            const n = Number.parseInt(e.target.value, 10);
            onChange(Number.isFinite(n) ? Math.min(max, Math.max(min, n)) : min);
          }}
        />
        {sufixo && <i>{sufixo}</i>}
      </span>
      <span className="adm-opcao-explica">{explica}</span>
    </label>
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
      // Chamava-se "+ convidar" e mudou de nome na R26 (§42), e não por
      // estética: ele cria um MORADOR — alguém com acesso total ao disco — com
      // a senha definida aqui. O convite de verdade, que cria um convidado, é a
      // seção logo abaixo. Dois botões com o mesmo rótulo e consequências de
      // acesso diferentes é como um estranho vira morador por engano.
      acao={
        <button className="chip" onClick={() => setAbrindo(!abrindo)}>
          {abrindo ? "cancelar" : "+ criar conta"}
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
            <option value="user">morador — vê e assiste tudo</option>
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
                      {/* Renomear existe porque o nome errado costuma ser erro
                          DESTE painel: é aqui e no convite que o admin digita o
                          nome de outra pessoa. Quem errou era o único que não
                          tinha como consertar.

                          Só o `display_name` — o `username` é a identidade de
                          entrada e o endereço `/p/<nome>` que o perfil
                          distribui. Um `prompt` e não um campo na linha pela
                          mesma razão do `confirm` do "remover" logo abaixo:
                          esta tabela é utilitária, e uma edição inline aqui
                          custaria mais estado do que o gesto vale. */}
                      <button
                        className="mini"
                        onClick={proteger(async () => {
                          const novo = window.prompt(
                            `novo nome de ${u.username}:`,
                            u.display_name,
                          );
                          // Cancelar devolve `null`, e o mesmo nome de volta não
                          // é uma mudança — nenhum dos dois merece uma escrita.
                          if (novo === null || novo.trim() === u.display_name) return;
                          await api.mudarUsuario(u.id, { display_name: novo.trim() });
                        })}
                      >
                        renomear
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

// ----------------------------------------------------------- aquecimentos

/// Os três aquecimentos, e o `kind` do job que cada um abre.
///
/// A ordem é a do que se pergunta primeiro: *"como dou refresh nas coleções
/// para pegar filmes novos?"* foi o pedido, e a resposta honesta era **por
/// `curl`**.
const AQUECER = [
  {
    id: "sagas" as const,
    kind: "saga",
    titulo: "Sagas dos filmes",
    texto:
      "Pergunta ao TMDB a que franquia cada filme pertence, cria as coleções que faltam e baixa as capas delas. Roda de novo sem repetir trabalho.",
  },
  {
    id: "trivia" as const,
    kind: "trivia",
    titulo: "Curiosidades",
    texto: "Preenche o cache de trivia das obras identificadas.",
  },
  {
    id: "producao" as const,
    kind: "producao",
    titulo: "Ficha de produção",
    texto: "Orçamento, bilheteria, estúdios e países — um GET por filme.",
  },
];

/// Os aquecimentos, que até aqui não tinham porta.
///
/// **É o defeito do §27 outra vez**: três rotas existiam sem nenhum cliente, e
/// as três só eram alcançáveis por `curl`. A do §3.5 do `IDEIAS-2.md` foi
/// pedida com todas as letras — *"como dou refresh nas coleções para pegar
/// filmes novos?"* —, e as outras duas estavam do lado dela, no mesmo estado.
///
/// Não há ensaio aqui, ao contrário da manutenção logo abaixo: aquecimento não
/// reescreve o acervo, ele preenche o que está vazio. O que ele faz aparece em
/// **Trabalhos**, que é onde o progresso já morava.
function Aquecimentos({ lista, recarregar }: { lista: Trabalho[]; recarregar: () => void }) {
  const [erro, setErro] = useState<string | null>(null);
  const [pedindo, setPedindo] = useState<string | null>(null);

  const chamar = async (qual: (typeof AQUECER)[number]["id"]) => {
    setPedindo(qual);
    setErro(null);
    try {
      const r = await api.aquecer(qual);
      // A rota responde `started: false` com o motivo em vez de erro — e o
      // motivo é a informação. Engoli-lo seria repetir o §8b.
      if (!r.started) setErro(r.reason ?? "o servidor não abriu o trabalho");
      recarregar();
    } catch (e) {
      setErro(e instanceof Error ? e.message : String(e));
    } finally {
      setPedindo(null);
    }
  };

  return (
    <Secao titulo="Aquecimentos" dica="o progresso aparece em Trabalhos">
      {erro && <p className="error">{erro}</p>}
      <div className="mnt-grade">
        {AQUECER.map((a) => {
          // O último job deste tipo. A lista já vem do mais novo pro mais
          // velho, então o primeiro que casa é o que interessa.
          const job = lista.find((j) => j.kind === a.kind) ?? null;
          const rodando = job?.state === "running";
          return (
            <div key={a.id} className="mnt">
              <h4>{a.titulo}</h4>
              <p>{a.texto}</p>
              <div className="mnt-acoes">
                <button
                  className="ghost small-btn"
                  disabled={rodando || pedindo === a.id}
                  onClick={() => void chamar(a.id)}
                >
                  {rodando ? "rodando…" : pedindo === a.id ? "…" : "aquecer"}
                </button>
                <span className="mnt-res">{estadoDoAquecimento(job)}</span>
              </div>
            </div>
          );
        })}
      </div>
    </Secao>
  );
}

/// A frase de um aquecimento, do que o próprio job publica.
///
/// Enquanto roda, o que importa é onde ele está; terminado, o que importa é
/// quando foi e o que rendeu. Nunca rodou é o que se diz quando nunca rodou —
/// e não um "0 de 0" com cara de resultado (§18).
function estadoDoAquecimento(j: Trabalho | null): string {
  if (!j) return "nunca rodou";
  const p = (j.progress ?? {}) as Record<string, unknown>;
  const atual = typeof p.atual === "string" ? p.atual : null;

  if (j.state === "running") {
    const quanto = j.total ? `${j.done ?? 0} de ${j.total}` : `${j.done ?? 0}`;
    return atual ? `${quanto} · ${atual}` : quanto;
  }

  const partes: string[] = [`${ESTADO[j.state] ?? j.state} ${dia(j.started_at)}`];
  if (j.error) partes.push(j.error.slice(0, 60));
  else partes.push(resumo(j));
  return partes.join(" · ");
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
