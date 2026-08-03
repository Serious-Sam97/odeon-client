import { useCallback, useEffect, useMemo, useState } from "react";
import Retrospectiva from "./Retrospectiva";
import {
  api,
  type Cadencia,
  type CamadaDaConquista,
  type ConquistaNaTela,
  type MeusDesafios,
  type PerfilCompleto,
} from "./api";

/// R32 — o perfil.
///
/// ## O que ele substitui
///
/// O §40 entregou um "placar" com quatro números, numa aba escondida, com um
/// aviso impresso na própria tela mandando ignorar o número: *"se este número
/// começar a escolher o que você assiste, ignore-o"*. Aquilo foi construído
/// contrariado.
///
/// **O aviso sai.** Um produto que entrega uma feature e imprime na tela um
/// pedido de desculpas por ela não entregou a feature — entregou a discussão
/// sobre ela.
///
/// ## E o que ele não faz
///
/// Não inventa número. Tudo aqui é contagem de fato: obra terminada, fita
/// rebobinada, nota dada, saga completa. O XP é derivado desses fatos na hora,
/// então nada desincroniza e **tudo é retroativo** — no dia em que isto ligou,
/// o que já era verdade virou medalha.
///
/// Com o histórico de hoje isso vale pouco: **2 obras terminadas**. A lista foi
/// escrita para o histórico que ela vai criar, e é honesto que ela comece quase
/// toda trancada — uma lista que abre cheia não é uma lista, é um troféu de
/// participação.
const NOME_DA_CAMADA: Record<CamadaDaConquista, string> = {
  facil: "fáceis",
  media: "médias",
  dificil: "difíceis",
  impossivel: "impossíveis",
  nivel: "marcos de nível",
  saga: "sagas",
};

/// A ordem em que as camadas aparecem. Da que quase todo mundo tem para a que
/// ninguém vai ter — que é a ordem em que se lê uma lista de conquistas.
const ORDEM: CamadaDaConquista[] = ["facil", "media", "saga", "dificil", "impossivel", "nivel"];

export default function Perfil({ userId }: { userId?: string }) {
  const [p, setP] = useState<PerfilCompleto | null>(null);
  const [erro, setErro] = useState<string | null>(null);
  const [editando, setEditando] = useState(false);
  const [olhando, setOlhando] = useState<string | undefined>(userId);

  const carregar = useCallback(() => {
    api
      .perfil(olhando)
      .then(setP)
      .catch((e) => setErro(String(e)));
  }, [olhando]);

  useEffect(carregar, [carregar]);

  const porCamada = useMemo(() => {
    const m = new Map<CamadaDaConquista, ConquistaNaTela[]>();
    for (const c of p?.conquistas ?? []) {
      const l = m.get(c.camada) ?? [];
      l.push(c);
      m.set(c.camada, l);
    }
    return m;
  }, [p]);

  if (erro) return <p className="error">{erro}</p>;
  if (!p) return <p className="muted small">abrindo o perfil…</p>;

  const { progresso: g } = p;
  // A barra é a fatia do nível atual, não do total: o XP absoluto já está
  // escrito ao lado, e uma barra que anda 0,4% por filme não anda.
  const fatia = Math.max(
    0,
    Math.min(100, ((g.xp - g.xp_do_nivel) / Math.max(1, g.xp_do_proximo - g.xp_do_nivel)) * 100),
  );

  return (
    <div className="perfil">
      <header className="perfil-topo">
        <div className="perfil-quem">
          <h2>{p.display_name}</h2>
          {p.titulo_nome && <span className="perfil-titulo">{p.titulo_nome}</span>}
          {p.bio && <p className="perfil-bio">{p.bio}</p>}
          {p.tags.length > 0 && (
            <div className="perfil-tags">
              {p.tags.map((t) => (
                <span key={t} className="perfil-tag">
                  #{t}
                </span>
              ))}
            </div>
          )}
        </div>

        <div className="perfil-nivel">
          <span className="perfil-n">{g.nivel}</span>
          <span className="perfil-n-rotulo">nível</span>
        </div>
      </header>

      <div className="perfil-barra">
        <span style={{ width: `${fatia}%` }} />
      </div>
      <p className="perfil-xp">
        {g.xp.toLocaleString("pt-BR")} XP · faltam{" "}
        {(g.xp_do_proximo - g.xp).toLocaleString("pt-BR")} pro nível {g.nivel + 1} ·{" "}
        <b>
          {g.desbloqueadas} de {g.total}
        </b>{" "}
        conquistas
      </p>

      {p.meu && (
        <button className="perfil-editar" onClick={() => setEditando((e) => !e)}>
          {editando ? "fechar" : "editar perfil"}
        </button>
      )}

      {editando && p.meu && <Editor p={p} aoSalvar={() => (setEditando(false), carregar())} />}

      {/* R35: OS DESAFIOS. Moram no perfil porque são individuais — o §2.4
          separa o coletivo (guia, eventos) do individual (desafios, XP,
          conquistas), e o perfil já é onde o individual vive. Só no seu: os
          desafios de outra pessoa não são assunto de ninguém. */}
      {p.meu && <Desafios />}

      {p.vitrine.length > 0 && (
        <section className="perfil-vitrine">
          <h3>Vitrine</h3>
          <div className="vitrine-caixas">
            {p.vitrine.map((v) => (
              <div key={v.id} className="vitrine-caixa" title={v.titulo}>
                {v.poster ? (
                  <img src={api.artworkUrl(v.poster)} alt={v.titulo} loading="lazy" />
                ) : (
                  <span className="vitrine-sem-arte">{v.titulo}</span>
                )}
              </div>
            ))}
          </div>
        </section>
      )}

      {/* R36: A RETROSPECTIVA veio pra cá. O §40 a separou do placar "pra a
          decisão ser reversível", e o `IDEIAS.md` §4 previu o destino dela:
          *"pode sobreviver como tela de perfil"*. Sobreviveu — e faz mais
          sentido aqui que numa aba: ela **descreve quem você é**, que é
          literalmente o assunto desta tela.

          Só no seu perfil. A retrospectiva de outra pessoa não é assunto de
          ninguém, e a rota nem aceita usuário. */}
      {p.meu && (
        <section className="perfil-retro">
          <Retrospectiva />
        </section>
      )}

      {/* A COMPARAÇÃO. Ela mora aqui e não numa aba própria de propósito: o §40
          separou o placar "pra a decisão ser reversível" e o efeito foi ele
          ficar escondido. Comparar com os amigos foi pedido — então fica onde
          alguém vai olhar. */}
      {p.amigos.length > 1 && (
        <section className="perfil-placar">
          <h3>Você e seus amigos</h3>
          <ol className="placar-linhas">
            {p.amigos.map((a, i) => (
              <li key={a.id} className={a.eu ? "eu" : ""}>
                <span className="placar-pos">{i + 1}</span>
                <button className="placar-nome" onClick={() => setOlhando(a.eu ? undefined : a.id)}>
                  {a.display_name}
                </button>
                {a.titulo && <i className="placar-titulo">{a.titulo}</i>}
                <span className="placar-nivel">nível {a.nivel}</span>
                <span className="placar-xp">{a.xp.toLocaleString("pt-BR")} XP</span>
              </li>
            ))}
          </ol>
        </section>
      )}

      <section className="perfil-conquistas">
        <h3>Conquistas</h3>
        {ORDEM.filter((c) => porCamada.has(c)).map((camada) => {
          const lista = porCamada.get(camada) ?? [];
          const abertas = lista.filter((c) => c.em).length;
          return (
            <div key={camada} className={`camada camada-${camada}`}>
              <header>
                <h4>{NOME_DA_CAMADA[camada]}</h4>
                <span>
                  {abertas} de {lista.length}
                </span>
              </header>
              <div className="camada-grade">
                {lista.map((c) => (
                  <div key={c.chave} className={`conq${c.em ? " aberta" : ""}`}>
                    <b>{c.nome}</b>
                    <span>{c.descricao}</span>
                    {/* Os pontos só aparecem na aberta: numa trancada eles
                        seriam uma promessa, e a lista já promete o suficiente. */}
                    {c.em ? <i>+{c.pontos} XP</i> : c.pontos > 0 && <i className="tranca">🔒</i>}
                  </div>
                ))}
              </div>
            </div>
          );
        })}
      </section>
    </div>
  );
}

/// Os desafios da janela.
///
/// **Três, e eles fazem trabalhos diferentes**: um fácil, um de tema e um que
/// empurra pra fora do seu gosto — que é o único dos três que faz o desafio
/// servir ao terceiro pilar (§1).
///
/// **Falhar não custa nada.** A janela fecha, o desafio some, outro é sorteado.
/// Sem perda de XP, sem sequência quebrada, sem aviso — este projeto tem uma
/// punição só e ela é social (a fita mal devolvida), porque funciona entre
/// pessoas. Punir alguém por não ter assistido um filme é o placar do §40
/// voltando com outra roupa.
function Desafios() {
  const [d, setD] = useState<MeusDesafios | null>(null);

  const carregar = useCallback(() => {
    api.desafios().then(setD).catch(() => {});
  }, []);

  useEffect(carregar, [carregar]);

  const trocar = (c: Cadencia) =>
    void api.salvarCadencia(c).then(carregar).catch(() => {});

  if (!d || d.desafios.length === 0) return null;

  const feitos = d.desafios.filter((x) => x.cumprido_em).length;

  return (
    <section className="desafios">
      <header>
        <h3>Seus desafios</h3>
        <span className="desafios-prazo">até {vence(d.desafios[0].vence_em)}</span>
        {/* A cadência é escolhida pela pessoa, entre opções definidas. Três, e
            não cinco: a diferença entre "a cada 4 dias" e "a cada 5" não é uma
            escolha, é um número. */}
        <div className="desafios-cadencia">
          {(
            [
              ["diaria", "todo dia"],
              ["tres_dias", "3 em 3 dias"],
              ["semanal", "toda semana"],
            ] as const
          ).map(([v, r]) => (
            <button key={v} className={d.cadencia === v ? "on" : ""} onClick={() => trocar(v)}>
              {r}
            </button>
          ))}
        </div>
      </header>

      <ul className="desafio-lista">
        {d.desafios.map((x) => (
          <li key={x.id} className={x.cumprido_em ? "feito" : ""}>
            <span className="desafio-marca">{x.cumprido_em ? "✓" : "□"}</span>
            <b>{x.rotulo}</b>
            <i>+{x.xp} XP</i>
          </li>
        ))}
      </ul>

      {feitos === d.desafios.length && (
        <p className="desafios-fim">Você limpou a janela. A próxima vem {vence(d.desafios[0].vence_em)}.</p>
      )}
    </section>
  );
}

/// "domingo", "amanhã". Um prazo em data absoluta faz contar nos dedos.
function vence(iso: string): string {
  const ms = new Date(iso).getTime() - Date.now();
  const dias = Math.ceil(ms / 86_400_000);
  if (dias <= 1) return "amanhã";
  if (dias <= 7)
    return new Date(iso).toLocaleDateString("pt-BR", { weekday: "long" });
  return new Date(iso).toLocaleDateString("pt-BR", { day: "numeric", month: "long" });
}

/// A edição.
///
/// Título e tags saem **do que foi desbloqueado** — o servidor manda a lista
/// pronta, e a tela nunca oferece o que a validação vai recusar. Levar 403
/// escolhendo de um menu que o produto mostrou seria o produto mentindo pra si
/// mesmo.
function Editor({ p, aoSalvar }: { p: PerfilCompleto; aoSalvar: () => void }) {
  const [titulo, setTitulo] = useState(p.titulo ?? "");
  const [tags, setTags] = useState<string[]>(p.tags);
  const [bio, setBio] = useState(p.bio ?? "");
  const [erro, setErro] = useState<string | null>(null);
  const [salvando, setSalvando] = useState(false);

  const alternar = (t: string) =>
    setTags((atual) =>
      atual.includes(t) ? atual.filter((x) => x !== t) : atual.length < 5 ? [...atual, t] : atual,
    );

  const salvar = async () => {
    setSalvando(true);
    setErro(null);
    try {
      await api.salvarPerfil({
        titulo: titulo || null,
        tags,
        bio: bio.trim() || null,
        vitrine: p.vitrine.map((v) => v.id),
      });
      aoSalvar();
    } catch (e) {
      setErro(e instanceof Error ? e.message : String(e));
    } finally {
      setSalvando(false);
    }
  };

  return (
    <div className="perfil-editor">
      {erro && <p className="error">{erro}</p>}

      <label className="editor-campo">
        <span>Título</span>
        <select value={titulo} onChange={(e) => setTitulo(e.target.value)}>
          <option value="">nenhum</option>
          {p.titulos_disponiveis.map(([chave, nome]) => (
            <option key={chave} value={chave}>
              {nome}
            </option>
          ))}
        </select>
        {p.titulos_disponiveis.length === 0 && (
          <i>nenhum título desbloqueado ainda — eles vêm das conquistas</i>
        )}
      </label>

      <div className="editor-campo">
        <span>Tags · até 5</span>
        <div className="editor-tags">
          {p.tags_disponiveis.map((t) => (
            <button
              key={t}
              className={`perfil-tag escolha${tags.includes(t) ? " on" : ""}`}
              onClick={() => alternar(t)}
            >
              #{t}
            </button>
          ))}
        </div>
        {p.tags_disponiveis.length === 0 && <i>nenhuma tag desbloqueada ainda</i>}
      </div>

      <label className="editor-campo">
        <span>Uma linha sua · até 140</span>
        <input
          value={bio}
          maxLength={140}
          placeholder="só filme ruim depois das 2h"
          onChange={(e) => setBio(e.target.value)}
        />
      </label>

      <button className="perfil-salvar" disabled={salvando} onClick={() => void salvar()}>
        {salvando ? "salvando…" : "salvar"}
      </button>
    </div>
  );
}
