import { useCallback, useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import Avatar from "./Avatar";
import Desafios from "./Desafios";
import Retrospectiva from "./Retrospectiva";
import {
  api,
  auth,
  PERFIL_MUDOU,
  SENHA_MINIMA,
  type CamadaDaConquista,
  type ConquistaNaTela,
  type EnfeiteNaTela,
  type NaVitrine,
  type PerfilCompleto,
  type WorkListItem,
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

export default function Perfil({ quem }: { quem?: string }) {
  const [p, setP] = useState<PerfilCompleto | null>(null);
  const [erro, setErro] = useState<string | null>(null);
  /// Um painel de cada vez. São dois assuntos diferentes — como você aparece e
  /// como você entra — e abrir os dois empilhados só faria a página crescer sem
  /// que ninguém estivesse lendo as duas coisas ao mesmo tempo.
  const [painel, setPainel] = useState<"nenhum" | "editor" | "senha">("nenhum");
  const navegar = useNavigate();

  const carregar = useCallback(() => {
    api
      .perfil(quem)
      .then(setP)
      .catch((e) => setErro(String(e)));
  }, [quem]);

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
    /* A moldura tinge o perfil inteiro por uma variável, e não por classe: são
       quatro cores hoje e uma quinta é uma linha no `enfeites.rs` — uma classe
       por cor faria a lista existir em dois lugares. */
    <div className="perfil" style={{ ["--perfil-cor" as string]: p.moldura ?? "var(--accent)" }}>
      {/* A CAPA. Ela é a arte de um filme do acervo, e o degradê no pé é o que
          deixa o nome legível em cima de qualquer imagem — sem ele, um backdrop
          claro engole o texto branco. */}
      {p.capa?.arte && (
        <div
          className="perfil-capa"
          style={{ backgroundImage: `url(${api.artworkUrl(p.capa.arte)})` }}
          title={p.capa.rotulo}
        />
      )}

      <header className={p.capa?.arte ? "perfil-topo com-capa" : "perfil-topo"}>
        {/* O ROSTO. Quem não escolheu cai na marca derivada do nome (R42) — e
            ela nunca é um buraco. */}
        {p.avatar?.arte ? (
          <img
            className="perfil-rosto"
            src={api.artworkUrl(p.avatar.arte)}
            alt={p.avatar.rotulo}
            title={p.avatar.rotulo}
          />
        ) : (
          <Avatar nome={p.display_name} tamanho={84} />
        )}

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
        <div className="perfil-acoes">
          <button
            className="perfil-editar"
            onClick={() => setPainel((a) => (a === "editor" ? "nenhum" : "editor"))}
          >
            {painel === "editor" ? "fechar" : "editar perfil"}
          </button>
          {/* A troca de senha mora aqui, e não no admin: o admin é do dono do
              servidor, e a senha é de cada um. */}
          <button
            className="perfil-editar"
            onClick={() => setPainel((a) => (a === "senha" ? "nenhum" : "senha"))}
          >
            {painel === "senha" ? "fechar" : "trocar senha"}
          </button>
          <Link username={p.username} />
        </div>
      )}

      {painel === "editor" && p.meu && (
        <Editor p={p} aoSalvar={() => (setPainel("nenhum"), carregar())} />
      )}

      {painel === "senha" && p.meu && <Senha username={p.username} />}

      {/* R35: OS DESAFIOS. Moram no perfil porque são individuais — o §2.4
          separa o coletivo (guia, eventos) do individual (desafios, XP,
          conquistas), e o perfil já é onde o individual vive. Só no seu: os
          desafios de outra pessoa não são assunto de ninguém. */}
      {p.meu && <Desafios />}

      {(p.vitrine.length > 0 || p.meu) && (
        <section className="perfil-vitrine">
          <h3>Vitrine</h3>
          {p.vitrine.length === 0 && p.meu && (
            <p className="muted small">
              Seis caixas suas, na sua ordem. Monte em <b>editar perfil</b>.
            </p>
          )}
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
                <button
                  className="placar-nome"
                  onClick={() => navegar(a.eu ? "/perfil" : `/p/${a.username}`)}
                >
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
  const [avatar, setAvatar] = useState(p.avatar?.chave ?? "");
  const [capa, setCapa] = useState(p.capa?.chave ?? "");
  const [moldura, setMoldura] = useState(
    p.molduras.find((m) => m.cor === p.moldura)?.chave ?? "",
  );
  const [vitrine, setVitrine] = useState<NaVitrine[]>(p.vitrine);
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
        vitrine: vitrine.map((v) => v.id),
        avatar: avatar || null,
        capa: capa || null,
        moldura: moldura || null,
      });
      /// O rosto e a moldura também vivem no cabeçalho (R47). Ele leu o perfil
      /// uma vez, na montagem, e não tem como saber que você acabou de trocar de
      /// cara — a não ser que alguém conte.
      window.dispatchEvent(new Event(PERFIL_MUDOU));
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

      {/* AS TRÊS GALERIAS.

          O trancado **aparece**, e não é contradição com o §48. A regra de lá é
          que a tela nunca deixe escolher o que a validação vai recusar — e é o
          que acontece: a opção trancada não é clicável. Escondê-la seria outra
          coisa, e seria o erro que a própria lista de conquistas não comete ao
          mostrar as 80 com descrição: *"uma conquista secreta é uma conquista
          que ninguém persegue"*. Um rosto secreto é a mesma perda. */}
      <Galeria
        titulo="Rosto"
        dica="o padrão é a marca do seu nome"
        itens={p.rostos}
        escolhido={avatar}
        aoEscolher={setAvatar}
      />
      <Galeria
        titulo="Capa"
        dica="a arte de um filme daqui"
        larga
        itens={p.capas}
        escolhido={capa}
        aoEscolher={setCapa}
      />
      <Galeria
        titulo="Cor"
        dica="tinge o perfil inteiro"
        itens={p.molduras}
        escolhido={moldura}
        aoEscolher={setMoldura}
      />

      <VitrineEditor lista={vitrine} aoMudar={setVitrine} />

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

/// A troca de senha.
///
/// A rota existe no servidor desde o M4 e **nunca teve cliente** — era
/// alcançável só por `curl`, como as sete da R16. Uma senha que não se troca
/// pela tela é uma senha que ninguém troca.
///
/// ## As três recusas que a tela faz sozinha
///
/// Mínimo de caracteres, confirmação que não bate, e senha nova igual à velha.
/// Nenhuma delas precisa do servidor pra ser sabida, e mandar assim mesmo
/// gastaria uma volta pra ouvir de longe o que já dava pra dizer aqui. A regra
/// continua sendo do servidor — a `SENHA_MINIMA` é cópia dela, e o 400 dele
/// passa inteiro pra tela.
///
/// ## E a que só o servidor sabe
///
/// Se a senha atual está certa. Ela volta em 401, e por isso esta tela não usa
/// o caminho comum do `api.ts`: lá, 401 quer dizer "a sessão acabou" e limpa o
/// token. Aqui quer dizer "você errou a senha antiga" — e errar a senha antiga
/// não pode custar a sessão.
function Senha({ username }: { username: string }) {
  const [atual, setAtual] = useState("");
  const [nova, setNova] = useState("");
  const [confirma, setConfirma] = useState("");
  const [erro, setErro] = useState<string | null>(null);
  const [salvando, setSalvando] = useState(false);
  const [pronto, setPronto] = useState(false);

  const trocar = async (e: React.FormEvent) => {
    e.preventDefault();
    setErro(null);

    if (nova.length < SENHA_MINIMA) {
      return setErro(`a senha nova precisa de pelo menos ${SENHA_MINIMA} caracteres`);
    }
    if (nova !== confirma) return setErro("as senhas não conferem");
    if (nova === atual) return setErro("a senha nova é igual à atual");

    setSalvando(true);
    // A troca já aconteceu do lado do servidor? A pergunta importa: depois dela
    // não existe mais "voltar pro formulário", porque o token desta aba morreu
    // junto com as outras sessões.
    let trocada = false;
    try {
      await api.trocarSenha(atual, nova);
      trocada = true;
      // O servidor apaga TODAS as sessões do usuário, inclusive esta — a nota
      // que ele devolve diz "as outras", mas o `DELETE` não poupa ninguém.
      // Entrar de novo aqui, na hora, é o que faz a frase virar verdade: os
      // outros aparelhos saíram, este ficou. Sem isto, trocar a senha seria se
      // deslogar de tudo, inclusive de onde você está.
      const entrada = await api.login(username, nova);
      auth.setToken(entrada.token);
      setPronto(true);
    } catch (e) {
      // Se a senha já mudou e foi a reentrada que falhou, não há formulário pra
      // onde voltar: o token no bolso não abre mais nada. O login é o único
      // lugar honesto pra essa aba, e é pra lá que ela vai.
      if (trocada) {
        auth.clear();
        window.location.reload();
        return;
      }
      setErro(e instanceof Error ? e.message : String(e));
    } finally {
      setSalvando(false);
    }
  };

  if (pronto) {
    return (
      <div className="perfil-editor perfil-senha">
        <p className="perfil-senha-ok">
          senha trocada · <b>os outros aparelhos foram desconectados</b>
        </p>
      </div>
    );
  }

  return (
    /* Um `<form>` de verdade, e não uma pilha de `<input>` com um botão: é o
       que faz o Enter enviar e o gerenciador de senhas entender que esta é a
       tela de trocar — os `autoComplete` abaixo são metade do trabalho, e a
       outra metade é o formulário existir. */
    <form className="perfil-editor perfil-senha" onSubmit={(e) => void trocar(e)}>
      {erro && <p className="error">{erro}</p>}

      {/* O campo de usuário é escondido e só serve ao gerenciador de senhas:
          sem ele, ele não sabe de QUAL conta é a senha que acabou de mudar. */}
      <input type="text" name="username" value={username} autoComplete="username" readOnly hidden />

      <label className="editor-campo">
        <span>Senha atual</span>
        <input
          type="password"
          value={atual}
          onChange={(e) => setAtual(e.target.value)}
          autoComplete="current-password"
          required
        />
      </label>

      <label className="editor-campo">
        <span>Senha nova</span>
        <input
          type="password"
          value={nova}
          onChange={(e) => setNova(e.target.value)}
          autoComplete="new-password"
          required
        />
        <i>mínimo de {SENHA_MINIMA} caracteres · cifrada com Argon2id</i>
      </label>

      <label className="editor-campo">
        <span>Repita a nova</span>
        <input
          type="password"
          value={confirma}
          onChange={(e) => setConfirma(e.target.value)}
          autoComplete="new-password"
          required
        />
      </label>

      {/* Dito ANTES de trocar, e não depois. Derrubar a TV e o celular é o
          efeito principal do gesto pra quem tem mais de um aparelho — descobrir
          isso na tela de sucesso seria descobrir tarde. */}
      <p className="muted small">
        Trocar a senha desconecta os seus <b>outros aparelhos</b>. Este continua
        entrando.
      </p>

      <button className="perfil-salvar" type="submit" disabled={salvando}>
        {salvando ? "trocando…" : "trocar senha"}
      </button>
    </form>
  );
}

/// Uma galeria de enfeites.
///
/// Serve às três porque as três são a mesma pergunta — *"qual destes?"* — com
/// desenhos diferentes: rosto é quadrado, capa é larga, cor é um disco. Três
/// componentes seriam a mesma lógica de seleção escrita três vezes.
function Galeria({
  titulo,
  dica,
  itens,
  escolhido,
  aoEscolher,
  larga = false,
}: {
  titulo: string;
  dica: string;
  itens: EnfeiteNaTela[];
  escolhido: string;
  aoEscolher: (chave: string) => void;
  larga?: boolean;
}) {
  if (itens.length === 0) return null;
  return (
    <div className="editor-campo">
      <span>
        {titulo} <i className="editor-dica">· {dica}</i>
      </span>
      <div className={larga ? "galeria larga" : "galeria"}>
        {/* "nenhum" é uma escolha de verdade, e por isso é um item da galeria e
            não um botão de limpar em outro canto. */}
        <button
          className={`enfeite nenhum${escolhido === "" ? " on" : ""}`}
          onClick={() => aoEscolher("")}
          title="nenhum"
        >
          —
        </button>
        {itens.map((x) => (
          <button
            key={x.chave}
            className={[
              "enfeite",
              escolhido === x.chave ? "on" : "",
              x.aberto ? "" : "trancado",
              x.cor ? "cor" : "",
            ]
              .filter(Boolean)
              .join(" ")}
            disabled={!x.aberto}
            onClick={() => aoEscolher(x.chave)}
            title={
              x.aberto
                ? x.rotulo
                : `${x.rotulo} — abre com a conquista "${x.exige_nome ?? x.exige}"`
            }
            style={x.cor ? { background: x.cor } : undefined}
          >
            {x.arte && <img src={api.artworkUrl(x.arte)} alt="" loading="lazy" />}
            {/* Na cor o nome fica fora do disco: dentro dele, sobre a própria
                cor, nenhuma tinta é legível nas quatro. */}
            {x.cor && <b className="enfeite-nome">{x.aberto ? x.rotulo : "🔒"}</b>}
            {/* O nome só aparece no que está aberto. Numa opção trancada o que
                interessa é **o que falta fazer**, não como ela se chama. */}
            <span>{x.aberto ? x.rotulo : (x.exige_nome ?? "trancado")}</span>
          </button>
        ))}
      </div>
    </div>
  );
}

/// A vitrine: seis caixas, e **a ordem é o conteúdo**.
///
/// A coluna existe desde o §17 e a tela de escolher nunca existiu — a vitrine
/// de todo mundo estava vazia porque não havia como enchê-la. As setas movem;
/// arrastar seria mais bonito e é o que a tela de coleções faz, mas ali a lista
/// tem dezenas de itens e aqui tem seis: um alvo de arrastar de 90px por 130px
/// numa lista de seis não é um ganho, é uma chance de errar.
function VitrineEditor({
  lista,
  aoMudar,
}: {
  lista: NaVitrine[];
  aoMudar: (l: NaVitrine[]) => void;
}) {
  const [busca, setBusca] = useState("");
  const [achados, setAchados] = useState<WorkListItem[]>([]);

  useEffect(() => {
    if (busca.trim().length < 2) return setAchados([]);
    const t = window.setTimeout(() => {
      api
        .works({ q: busca.trim(), sort: "featured" })
        .then((r) => setAchados(r.slice(0, 6)))
        .catch(() => {});
    }, 250);
    return () => window.clearTimeout(t);
  }, [busca]);

  const mover = (i: number, passo: number) => {
    const j = i + passo;
    if (j < 0 || j >= lista.length) return;
    const l = [...lista];
    [l[i], l[j]] = [l[j], l[i]];
    aoMudar(l);
  };

  return (
    <div className="editor-campo">
      <span>
        Vitrine <i className="editor-dica">· até 6, e a ordem é sua</i>
      </span>

      <div className="vitrine-edit">
        {lista.map((v, i) => (
          <div key={v.id} className="vitrine-caixa" title={v.titulo}>
            {v.poster ? (
              <img src={api.artworkUrl(v.poster)} alt={v.titulo} loading="lazy" />
            ) : (
              <span className="vitrine-sem-arte">{v.titulo}</span>
            )}
            <div className="vitrine-mexer">
              <button disabled={i === 0} onClick={() => mover(i, -1)} title="pra esquerda">
                ‹
              </button>
              <button
                className="tirar"
                onClick={() => aoMudar(lista.filter((x) => x.id !== v.id))}
                title="tirar da vitrine"
              >
                ✕
              </button>
              <button
                disabled={i === lista.length - 1}
                onClick={() => mover(i, 1)}
                title="pra direita"
              >
                ›
              </button>
            </div>
          </div>
        ))}
        {lista.length < 6 && <span className="vitrine-vaga">{6 - lista.length} vagas</span>}
      </div>

      {lista.length < 6 && (
        <>
          <input
            className="campo"
            value={busca}
            placeholder="buscar uma obra pra pôr na vitrine…"
            onChange={(e) => setBusca(e.target.value)}
          />
          {achados.length > 0 && (
            <ul className="resultados">
              {achados.map((w) => (
                <li key={w.id}>
                  <button
                    disabled={lista.some((v) => v.id === w.id)}
                    onClick={() => {
                      aoMudar([
                        ...lista,
                        { id: w.id, titulo: w.title, ano: w.year, poster: w.poster },
                      ]);
                      setBusca("");
                    }}
                  >
                    {w.poster ? (
                      <img src={api.artworkUrl(w.poster)} alt="" />
                    ) : (
                      <span className="item-noart" />
                    )}
                    <span>
                      {w.title}
                      {w.year && <span className="muted"> · {w.year}</span>}
                    </span>
                    <span className="muted">
                      {lista.some((v) => v.id === w.id) ? "já está" : "+"}
                    </span>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </>
      )}
    </div>
  );
}

/// O link do perfil.
///
/// **É o endereço da barra**, e não um segundo formato: o botão copia o que já
/// está escrito ali quando você abre o seu perfil por `/p/<nome>`. Inventar uma
/// URL "de compartilhar" diferente da que o produto usa daria duas verdades
/// sobre o mesmo lugar.
function Link({ username }: { username: string }) {
  const [copiado, setCopiado] = useState(false);
  const url = `${window.location.origin}/p/${username}`;

  return (
    <button
      className="perfil-link"
      title={url}
      onClick={() => {
        void navigator.clipboard
          ?.writeText(url)
          .then(() => {
            setCopiado(true);
            window.setTimeout(() => setCopiado(false), 2500);
          })
          .catch(() => {});
      }}
    >
      {copiado ? "copiado" : `copiar link · /p/${username}`}
    </button>
  );
}
