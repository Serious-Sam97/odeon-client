import { useCallback, useEffect, useRef, useState } from "react";
import Avatar from "./Avatar";
import {
  api,
  type Achado,
  type Acontecimento,
  type Alguem,
  type Comentario,
  type Conversa,
  type Mensagem,
  type MinhasAmizades,
  type Mural as Dados,
  type Presente,
  type SalaAberta,
} from "./api";

/// R25 — o mural. R28 — de quem ele é. **R33 — a rede social.**
///
/// ## O que esta fase desfaz
///
/// O §41 escreveu que o mural conta *"o que terminou, não o que abriu"* e
/// chamou isso de decisão de privacidade. O §42 fechou a rota que diz quem está
/// assistindo o quê, tratando-a como vazamento.
///
/// A decisão 2.2 do `IDEIAS.md` diz o contrário, e é explícita: **amigo vê o que
/// você está assistindo agora, o que largou no meio, o que terminou, suas
/// notas. Sem chave de privacidade.** O que as duas seções chamaram de vazamento
/// é a feature.
///
/// A poda não era burra — foi escrita quando o escopo era um "círculo" que podia
/// ter um convidado dentro. Com amizade que **se aceita** (§44), o aceite é o
/// consentimento: você só aparece pra quem deixou entrar.
///
/// ## Uma aba, e ela é de primeiro nível
///
/// *"Uma aba separada, que talvez venha a ser algo separado do Odeon."* Ela saiu
/// de dentro de "experimentação" e subiu pra barra de cima — que é o que deixa
/// ela pronta pra um dia sair daqui sem arrastar a locadora junto.
type Sala = "mural" | "conversas" | "gente";

export default function Mural({ aoVerPerfil }: { aoVerPerfil: (id: string) => void }) {
  const [sala, setSala] = useState<Sala>("mural");
  /// Com quem a sala de conversas deve abrir. Vem do atalho da sala de gente
  /// (R42) — quem clica em "falar" já disse com quem, e chegar na aba de
  /// conversas pra escolher de novo seria perder o gesto no meio.
  const [falarCom, setFalarCom] = useState<string | null>(null);
  const [dados, setDados] = useState<Dados | null>(null);
  const [presenca, setPresenca] = useState<Presente[]>([]);
  const [conversas, setConversas] = useState<Conversa[]>([]);
  const [erro, setErro] = useState<string | null>(null);

  const carregar = useCallback(() => {
    api.feed().then(setDados).catch((e) => setErro(String(e)));
    api.presenca().then(setPresenca).catch(() => {});
    api.conversas().then(setConversas).catch(() => {});
  }, []);

  useEffect(carregar, [carregar]);

  /// A presença envelhece sozinha — quem fechou o navegador não avisa. Meio
  /// minuto é curto o bastante pra lista não mentir e longo o bastante pra não
  /// ser um segundo canal aberto.
  useEffect(() => {
    const t = window.setInterval(() => {
      api.presenca().then(setPresenca).catch(() => {});
    }, 30_000);
    return () => window.clearInterval(t);
  }, []);

  /// O barramento do M3: o que acontece na loja e a mensagem que chega entram
  /// aqui sem recarregar. É o que separa uma rede social de um relatório.
  useEffect(() => {
    return api.ouvirEventos((e) => {
      if (e.type === "locadora") carregar();
      if (e.type === "mensagem") api.conversas().then(setConversas).catch(() => {});
    });
  }, [carregar]);

  const naoLidas = conversas.reduce((n, c) => n + c.nao_lidas, 0);

  if (erro) return <p className="error">{erro}</p>;

  return (
    <div className="social">
      <nav className="social-salas">
        {(
          [
            ["mural", "mural"],
            ["conversas", "conversas"],
            ["gente", "gente"],
          ] as const
        ).map(([v, r]) => (
          <button key={v} className={sala === v ? "on" : ""} onClick={() => setSala(v)}>
            {r}
            {/* O contador só existe quando há o que contar (§24). */}
            {v === "conversas" && naoLidas > 0 && <b>{naoLidas}</b>}
          </button>
        ))}
      </nav>

      {sala === "mural" && (
        <div className="social-corpo">
          <div className="social-coluna">
            <SalasAbertas />
            <CaixaDePost aoPostar={carregar} />
            {dados ? <Feed dados={dados} aoMexer={carregar} /> : <p className="muted small">olhando o mural…</p>}
          </div>
          <aside className="social-lado">
            <Presenca lista={presenca} />
          </aside>
        </div>
      )}

      {sala === "conversas" && (
        <Conversas
          lista={conversas}
          aoMexer={carregar}
          comecarCom={falarCom}
          aoComecar={() => setFalarCom(null)}
        />
      )}
      {sala === "gente" && (
        <Gente
          aoMexer={carregar}
          presenca={presenca}
          aoFalar={(id) => {
            setFalarCom(id);
            setSala("conversas");
          }}
          aoVerPerfil={aoVerPerfil}
        />
      )}
    </div>
  );
}

/// R46 — as salas de assistir junto que estão abertas.
///
/// **É o convite, e não há tabela de convite**: a amizade já é o aceite (§44),
/// então uma sala aberta aparece pra quem foi aceito e pra mais ninguém. Um
/// convite individual seria uma segunda permissão em cima de uma que já existe.
///
/// Mora no topo do mural porque é o lugar de "o que está acontecendo na casa
/// agora" — e uma sala aberta é a coisa mais viva que pode estar acontecendo.
function SalasAbertas() {
  const [lista, setLista] = useState<SalaAberta[]>([]);
  const [erro, setErro] = useState<string | null>(null);

  const carregar = useCallback(() => {
    api.juntoAbertas().then(setLista).catch(() => {});
  }, []);

  useEffect(carregar, [carregar]);

  // O barramento avisa quando uma sala abre ou fecha (§46).
  useEffect(() => api.ouvirEventos((e) => e.type === "junto" && carregar()), [carregar]);

  // Some quando não há nada: uma faixa dizendo "nenhuma sala aberta" é a linha
  // que o §24 manda sumir.
  if (lista.length === 0) return null;

  return (
    <div className="salas-abertas">
      {erro && <p className="error small">{erro}</p>}
      {lista.map((s) => (
        <div key={s.id} className="sala-aberta">
          {s.poster && <img src={api.artworkUrl(s.poster)} alt="" loading="lazy" />}
          <div>
            <b>{s.host_nome} está assistindo junto</b>
            <span className="muted small">
              {s.titulo} · {s.gente} {s.gente === 1 ? "pessoa" : "pessoas"}
            </span>
          </div>
          <button
            onClick={() => {
              api
                .entrarJunto(s.id)
                // A sala entra pelo barramento: o App relê e abre o filme.
                .then(() => carregar())
                .catch((e) => setErro(e instanceof Error ? e.message : String(e)));
            }}
          >
            entrar
          </button>
        </div>
      ))}
    </div>
  );
}

// ------------------------------------------------------------------ o post

/// A caixa de escrever.
///
/// **É o único acontecimento do mural que alguém digita** — o resto o produto
/// deduziu de um play, um empréstimo, uma nota. Por isso é o único que pode ser
/// comentado e apagado.
function CaixaDePost({ aoPostar }: { aoPostar: () => void }) {
  const [texto, setTexto] = useState("");
  const [ocupado, setOcupado] = useState(false);
  const [erro, setErro] = useState<string | null>(null);

  const enviar = async () => {
    if (!texto.trim()) return;
    setOcupado(true);
    setErro(null);
    try {
      await api.postar(texto.trim());
      setTexto("");
      aoPostar();
    } catch (e) {
      setErro(e instanceof Error ? e.message : String(e));
    } finally {
      setOcupado(false);
    }
  };

  return (
    <div className="post-caixa">
      <textarea
        value={texto}
        maxLength={500}
        rows={2}
        placeholder="o que você andou vendo?"
        onChange={(e) => setTexto(e.target.value)}
        onKeyDown={(e) => {
          // Enter manda, Shift+Enter quebra linha. É o gesto que todo mundo já
          // tem no dedo, e um botão sozinho faria a caixa parecer formulário.
          if (e.key === "Enter" && !e.shiftKey) {
            e.preventDefault();
            void enviar();
          }
        }}
      />
      <div className="post-rodape">
        {erro && <span className="error">{erro}</span>}
        <span className="muted small">{texto.length}/500</span>
        <button disabled={ocupado || !texto.trim()} onClick={() => void enviar()}>
          postar
        </button>
      </div>
    </div>
  );
}

// ------------------------------------------------------------------ o feed

function Feed({ dados, aoMexer }: { dados: Dados; aoMexer: () => void }) {
  if (dados.acontecimentos.length === 0) {
    return (
      <p className="mural-vazio">
        Nada aconteceu ainda. Poste alguma coisa, termine um filme, ou pegue uma fita
        na locadora.
      </p>
    );
  }

  return (
    <>
      <ol className="mural-linhas">
        {dados.acontecimentos.map((a, i) => (
          <li
            key={a.post_id ?? `${a.tipo}-${a.quando}-${i}`}
            className={[a.meu ? "meu" : "", a.tipo === "assistindo" ? "agora" : ""]
              .filter(Boolean)
              .join(" ")}
          >
            {a.poster ? (
              <img src={api.artworkUrl(a.poster)} alt="" loading="lazy" />
            ) : (
              <span className="mural-sem-arte" />
            )}
            <div className="mural-texto">
              <p>{frase(a)}</p>
              <span className="mural-quando">
                {a.tipo === "assistindo" ? "agora" : quando(a.quando)}
              </span>
              {a.post_id && <Comentarios a={a} aoMexer={aoMexer} />}
            </div>
          </li>
        ))}
      </ol>

      {/* A verdade sobre o silêncio. Um mural em que só uma pessoa fala não está
          funcionando pela metade — está mostrando as coisas como elas são. */}
      {dados.vozes < dados.pessoas && (
        <p className="mural-vozes">
          {dados.vozes === 1
            ? `Só uma das ${dados.pessoas} pessoas apareceu por aqui até agora.`
            : `${dados.vozes} das ${dados.pessoas} pessoas apareceram por aqui.`}
        </p>
      )}
    </>
  );
}

/// Os comentários de um post, e a caixa de responder.
///
/// Decidido: comentário existe **no post e na review**. Post sem comentário é
/// diário, não rede social. A mesma tela serve os dois lugares, porque é a mesma
/// tabela — ver `Details.tsx`.
function Comentarios({ a, aoMexer }: { a: Acontecimento; aoMexer: () => void }) {
  const [texto, setTexto] = useState("");
  const [abrindo, setAbrindo] = useState(false);

  const enviar = async () => {
    if (!texto.trim() || !a.post_id) return;
    await api.comentar({ post_id: a.post_id }, texto.trim()).catch(() => {});
    setTexto("");
    setAbrindo(false);
    aoMexer();
  };

  return (
    <div className="comentarios">
      {a.comentarios.map((c) => (
        <Linha key={c.id} c={c} aoMexer={aoMexer} />
      ))}

      {abrindo ? (
        <div className="comentar-caixa">
          <input
            autoFocus
            value={texto}
            maxLength={500}
            placeholder="responder"
            onChange={(e) => setTexto(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Enter") void enviar();
              if (e.key === "Escape") setAbrindo(false);
            }}
          />
        </div>
      ) : (
        <button className="comentar-abrir" onClick={() => setAbrindo(true)}>
          comentar
        </button>
      )}
    </div>
  );
}

function Linha({ c, aoMexer }: { c: Comentario; aoMexer: () => void }) {
  return (
    <div className="comentario">
      <b>{c.meu ? "Você" : c.quem}</b>
      <span>{c.texto}</span>
      {/* Apagar o próprio comentário é discreto: existe, e não convida. */}
      {c.meu && (
        <button
          className="comentario-x"
          title="apagar"
          onClick={() => void api.apagarComentario(c.id).then(aoMexer).catch(() => {})}
        >
          ×
        </button>
      )}
    </div>
  );
}

// --------------------------------------------------------------- a presença

/// Duas listas, como foi pedido: quem está no servidor e quem está entre os seus
/// amigos. **Uma consulta só** — a segunda é a primeira filtrada, e separá-las
/// daria à tela a chance de discordar de si mesma sobre quem é amigo.
function Presenca({ lista }: { lista: Presente[] }) {
  const amigos = lista.filter((p) => p.amigo);
  const resto = lista.filter((p) => !p.amigo && !p.eu);
  if (lista.length === 0) return null;

  return (
    <div className="presenca">
      <h3>Agora</h3>
      {amigos.length > 0 && <Bloco titulo="seus amigos" gente={amigos} />}
      {resto.length > 0 && <Bloco titulo="no servidor" gente={resto} />}
      {amigos.length === 0 && resto.length === 0 && (
        <p className="muted small">só você por aqui.</p>
      )}
    </div>
  );
}

function Bloco({ titulo, gente }: { titulo: string; gente: Presente[] }) {
  return (
    <div className="presenca-bloco">
      <h4>{titulo}</h4>
      {gente.map((p) => (
        <div key={p.id} className={`presente${p.assistindo ? " vendo" : ""}`}>
          {/* A mesma marca da sala de gente (R42). As duas listas são as mesmas
              pessoas, e uma com retrato e outra sem leria como defeito. */}
          <Avatar nome={p.display_name} arte={p.rosto} tamanho={22} />
          <b>{p.display_name}</b>
          {/* O que está vendo, quando está. Sem frase inventada pro contrário —
              "online" já é o que a luz diz. */}
          {p.assistindo && <i>{p.assistindo}</i>}
        </div>
      ))}
    </div>
  );
}

// -------------------------------------------------------------- as conversas

function Conversas({
  lista,
  aoMexer,
  comecarCom,
  aoComecar,
}: {
  lista: Conversa[];
  aoMexer: () => void;
  /// Com quem abrir de saída, quando se chegou aqui pelo atalho da sala de
  /// gente (R42).
  comecarCom?: string | null;
  /// Avisa que o atalho foi cumprido. **É de uma vez só**: sem isto, entrar em
  /// "conversas" pela aba, dias depois, reabriria a conversa de quem foi
  /// atalhado uma vez — a sala remonta e o efeito rodaria de novo com o mesmo
  /// valor. Um atalho que se repete sozinho deixou de ser atalho.
  aoComecar?: () => void;
}) {
  const [com, setCom] = useState<string | null>(null);
  const [msgs, setMsgs] = useState<Mensagem[]>([]);
  const [texto, setTexto] = useState("");
  const fim = useRef<HTMLDivElement>(null);

  const abrir = useCallback(
    (id: string) => {
      setCom(id);
      api
        .conversa(id)
        .then((m) => {
          setMsgs(m);
          aoMexer();
        })
        .catch(() => {});
    },
    [aoMexer],
  );

  useEffect(() => {
    fim.current?.scrollIntoView({ block: "end" });
  }, [msgs]);

  /// O atalho abrindo a conversa. Depende de `abrir`, e não do primeiro render:
  /// quem entrou aqui sozinho continua escolhendo com quem falar.
  useEffect(() => {
    if (!comecarCom) return;
    abrir(comecarCom);
    aoComecar?.();
  }, [comecarCom, abrir, aoComecar]);

  const enviar = async () => {
    if (!texto.trim() || !com) return;
    await api.mandar(com, texto.trim()).catch(() => {});
    setTexto("");
    abrir(com);
  };

  if (lista.length === 0) {
    return (
      <p className="mural-vazio">
        Você ainda não tem amigos pra conversar. A aba <b>gente</b> resolve isso.
      </p>
    );
  }

  return (
    <div className="conversas">
      <div className="conversa-lista">
        {lista.map((c) => (
          <button
            key={c.com}
            className={`conversa-item${com === c.com ? " on" : ""}`}
            onClick={() => abrir(c.com)}
          >
            <b>{c.display_name}</b>
            {/* Um amigo com quem você nunca falou aparece com a linha vazia — é
                assim que se começa a falar. */}
            {c.ultima && <span>{c.ultima}</span>}
            {c.nao_lidas > 0 && <i>{c.nao_lidas}</i>}
          </button>
        ))}
      </div>

      <div className="conversa-corpo">
        {com ? (
          <>
            <div className="conversa-msgs">
              {msgs.map((m) => (
                <p key={m.id} className={m.minha ? "minha" : ""}>
                  {m.texto}
                </p>
              ))}
              <div ref={fim} />
            </div>
            <input
              value={texto}
              maxLength={2000}
              placeholder="escreva"
              onChange={(e) => setTexto(e.target.value)}
              onKeyDown={(e) => e.key === "Enter" && void enviar()}
            />
          </>
        ) : (
          <p className="muted small">escolha com quem falar.</p>
        )}
      </div>
    </div>
  );
}

// ------------------------------------------------------------------- a gente

/// Amigos, pedidos e busca — tudo que era o painel de cima do mural, agora com
/// espaço pra ser uma tela.
///
/// ## R42 — o que faltava aqui
///
/// A sala listava **nome e um botão**, e era tudo. Quatro coisas entraram, e
/// nenhuma é dado novo: as quatro já existiam em algum lugar do produto e não
/// chegavam nesta tela.
///
/// | | de onde vem |
/// |---|---|
/// | a marca da pessoa | desenhada do nome (`Avatar`) — §12, zero bytes |
/// | o que está vendo agora | a **presença**, que o mural já busca de 30 em 30s |
/// | falar com ela | a sala de conversas, que já existe ao lado |
/// | o perfil dela | a aba perfil, que já sabe abrir o de outra conta (§48) |
///
/// **Sem aba nova e sem painel lateral**, como foi decidido: o que muda é o que
/// cada linha diz, não onde ela mora.
function Gente({
  aoMexer,
  presenca,
  aoFalar,
  aoVerPerfil,
}: {
  aoMexer: () => void;
  presenca: Presente[];
  aoFalar: (id: string) => void;
  aoVerPerfil: (id: string) => void;
}) {
  const [amizades, setAmizades] = useState<MinhasAmizades | null>(null);
  const [busca, setBusca] = useState("");
  const [achados, setAchados] = useState<Achado[] | null>(null);
  const [recado, setRecado] = useState<string | null>(null);

  const recarregar = useCallback(() => {
    api.amigos().then(setAmizades).catch(() => {});
    aoMexer();
  }, [aoMexer]);

  useEffect(recarregar, [recarregar]);

  useEffect(() => {
    const t = window.setTimeout(() => {
      if (!busca.trim()) return setAchados(null);
      api.pessoas(busca).then(setAchados).catch(() => {});
    }, 250);
    return () => window.clearTimeout(t);
  }, [busca]);

  const pedir = (id: string) =>
    void api
      .pedirAmizade(id)
      .then((r) => {
        setRecado(r.recado);
        recarregar();
      })
      .catch((e) => setRecado(String(e)));

  const desfazer = (id: string, frase: string) =>
    void api.desfazerAmizade(id).then(() => {
      setRecado(frase);
      recarregar();
    });

  if (!amizades) return <p className="muted small">…</p>;

  /// Quem está no ar, por id. A presença é **uma consulta que já acontece** —
  /// o mural a busca de 30 em 30 segundos pro painel lateral —, então a sala
  /// de gente não abre uma segunda: ela recebe a mesma lista e cruza aqui.
  const noAr = new Map(presenca.map((p) => [p.id, p]));

  return (
    <div className="gente">
      <input
        className="gente-busca"
        value={busca}
        placeholder="procurar gente"
        onChange={(e) => setBusca(e.target.value)}
      />

      {recado && <p className="amigo-recado">{recado}</p>}

      {achados ? (
        <div className="gente-grupo">
          <h4>encontrados</h4>
          {achados.length === 0 && <p className="muted small">ninguém com esse nome.</p>}
          {achados.map((a) => (
            <LinhaDeGente
              key={a.id}
              nome={a.display_name}
              arroba={a.username}
              presente={noAr.get(a.id) ?? null}
              /* Na busca não há atalho pra conversa: falar é entre amigos (§49),
                 e oferecer o botão pra quem vai levar recusa é o produto
                 mentindo pra si mesmo — a mesma regra do §53. */
              aoVerPerfil={() => aoVerPerfil(a.username)}
            >
              {a.relacao === "nenhuma" && <button onClick={() => pedir(a.id)}>adicionar</button>}
              {a.relacao === "enviado" && <i>esperando</i>}
              {a.relacao === "recebido" && (
                <button className="sim" onClick={() => pedir(a.id)}>
                  aceitar
                </button>
              )}
              {a.relacao === "amigo" && <i>amigo</i>}
            </LinhaDeGente>
          ))}
        </div>
      ) : (
        <>
          {amizades.recebidos.length > 0 && (
            <div className="gente-grupo">
              <h4>querem ser seus amigos</h4>
              {amizades.recebidos.map((p: Alguem) => (
                <LinhaDeGente
                  key={p.id}
                  nome={p.display_name}
                  rosto={p.rosto}
                  presente={noAr.get(p.id) ?? null}
                  aoVerPerfil={() => aoVerPerfil(p.username)}
                >
                  <button className="sim" onClick={() => pedir(p.id)}>
                    aceitar
                  </button>
                  <button onClick={() => desfazer(p.id, `você recusou ${p.display_name}`)}>
                    recusar
                  </button>
                </LinhaDeGente>
              ))}
            </div>
          )}

          {amizades.amigos.length > 0 && (
            <div className="gente-grupo">
              <h4>seus amigos</h4>
              {amizades.amigos.map((p: Alguem) => (
                <LinhaDeGente
                  key={p.id}
                  nome={p.display_name}
                  rosto={p.rosto}
                  presente={noAr.get(p.id) ?? null}
                  aoFalar={() => aoFalar(p.id)}
                  aoVerPerfil={() => aoVerPerfil(p.username)}
                >
                  <button
                    onClick={() => desfazer(p.id, `você e ${p.display_name} não são mais amigos`)}
                  >
                    desfazer
                  </button>
                </LinhaDeGente>
              ))}
            </div>
          )}

          {amizades.enviados.length > 0 && (
            <div className="gente-grupo">
              <h4>esperando resposta</h4>
              {amizades.enviados.map((p: Alguem) => (
                <LinhaDeGente
                  key={p.id}
                  nome={p.display_name}
                  rosto={p.rosto}
                  presente={noAr.get(p.id) ?? null}
                  aoVerPerfil={() => aoVerPerfil(p.username)}
                >
                  <button onClick={() => desfazer(p.id, `pedido pra ${p.display_name} cancelado`)}>
                    cancelar
                  </button>
                </LinhaDeGente>
              ))}
            </div>
          )}

          {amizades.no_servidor.length > 0 && (
            <div className="gente-grupo">
              <h4>também estão aqui</h4>
              {amizades.no_servidor.map((p: Alguem) => (
                <LinhaDeGente
                  key={p.id}
                  nome={p.display_name}
                  rosto={p.rosto}
                  presente={noAr.get(p.id) ?? null}
                  aoVerPerfil={() => aoVerPerfil(p.username)}
                >
                  <button onClick={() => pedir(p.id)}>adicionar</button>
                </LinhaDeGente>
              ))}
            </div>
          )}
        </>
      )}
    </div>
  );
}

/// Uma pessoa na sala, com o que se sabe dela e o que dá pra fazer com ela.
///
/// A ação de amizade continua vindo de fora, como `children`: ela é diferente
/// em cada grupo — adicionar, aceitar, recusar, cancelar, desfazer — e essa é a
/// razão de os grupos existirem. O que a linha padroniza é a **identidade**:
/// marca, nome, o que está vendo, e as duas portas.
function LinhaDeGente({
  nome,
  rosto,
  arroba,
  presente,
  aoFalar,
  aoVerPerfil,
  children,
}: {
  nome: string;
  rosto?: string | null;
  arroba?: string;
  presente: Presente | null;
  aoFalar?: () => void;
  aoVerPerfil: () => void;
  children?: React.ReactNode;
}) {
  return (
    <div className={presente ? "gente-linha no-ar" : "gente-linha"}>
      <Avatar nome={nome} arte={rosto ?? presente?.rosto} tamanho={34} vendo={!!presente?.assistindo} />

      <div className="gente-quem">
        <b>{nome}</b>
        {/* O que está vendo, quando está vendo. Sem frase pro contrário: quem
            está online e parado já é dito pelo anel, e "não está vendo nada" é
            a linha que o §24 manda sumir. */}
        {presente?.assistindo ? (
          <span className="gente-vendo">vendo {presente.assistindo}</span>
        ) : (
          arroba && <span className="muted small">@{arroba}</span>
        )}
      </div>

      <div className="gente-acoes">
        {aoFalar && (
          <button className="gente-atalho" onClick={aoFalar} title={`falar com ${nome}`}>
            falar
          </button>
        )}
        <button className="gente-atalho" onClick={aoVerPerfil} title={`o perfil de ${nome}`}>
          perfil
        </button>
        {children}
      </div>
    </div>
  );
}

// ------------------------------------------------------------------ gramática

/// A frase de cada acontecimento.
///
/// Montada aqui e não no servidor — ao contrário das curiosidades (§32) —
/// porque estas oito são **gramática de lista**, não prosa: o servidor manda o
/// tipo e as peças, e a lista conjuga. Mandar a frase pronta impediria a tela de
/// dizer "você" no lugar do seu próprio nome.
function frase(a: Acontecimento): string {
  const quem = a.meu ? "Você" : a.quem;
  const det = a.detalhe ? ` — ${a.detalhe}` : "";
  switch (a.tipo) {
    case "assistindo":
      return `${quem} ${a.meu ? "está" : "está"} vendo ${a.titulo} agora.`;
    case "largou":
      return `${quem} largou ${a.titulo}${det}.`;
    case "terminou":
      return `${quem} terminou ${a.titulo}.`;
    case "pegou":
      return `${quem} pegou ${a.titulo} na locadora.`;
    case "devolveu":
      return `${quem} devolveu ${a.titulo}${det}.`;
    case "pediu":
      return `${quem} pediu ${a.titulo} de volta${det}.`;
    case "avaliou":
      return `${quem} avaliou ${a.titulo}${det}.`;
    // O post é o único em que o detalhe É a frase — o resto o produto deduziu,
    // este alguém escreveu.
    case "postou":
      return a.titulo ? `${a.detalhe ?? ""} — sobre ${a.titulo}` : (a.detalhe ?? "");
    default:
      return "";
  }
}

/// "hoje", "ontem", "há 3 dias". A data exata de um acontecimento social não
/// interessa — o que interessa é se foi agora ou faz tempo.
function quando(iso: string): string {
  const dias = Math.floor((Date.now() - new Date(iso).getTime()) / 86_400_000);
  if (dias <= 0) return "hoje";
  if (dias === 1) return "ontem";
  if (dias < 7) return `há ${dias} dias`;
  if (dias < 30) {
    const s = Math.floor(dias / 7);
    return s === 1 ? "há uma semana" : `há ${s} semanas`;
  }
  return new Date(iso).toLocaleDateString("pt-BR", { day: "numeric", month: "long" });
}
