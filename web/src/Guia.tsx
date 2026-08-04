import { useCallback, useEffect, useState, type CSSProperties } from "react";
import { useArrastoDeFileira } from "./arrasto";
import {
  api,
  hueFromTitle,
  type Filters,
  type GuiaEixos,
  type PessoaDoGuia,
  type Revista,
  type FaixaDoGuia,
  type WorkListItem,
} from "./api";

/// R18 — o guia de cinema.
///
/// A tese, numa frase: **um guia que qualquer site tem é a Wikipédia com passos
/// extras; um que cruza o cânone com o SEU acervo e o SEU histórico não existe
/// em lugar nenhum.**
///
/// Por isso nenhuma tela daqui é biografia. Toda pessoa responde três coisas ao
/// mesmo tempo: quem é, o que disso você tem, e o que você fez com isso. As
/// duas últimas saem do `credit` (§8h) e do `playback_state` (M0) — o backend
/// desta fase não criou uma tabela sequer.
///
/// A regra da §24 vale em cada cartão: **linha limpa some.** Num acervo com
/// zero obras terminadas, escrever "0 terminadas" em 127 cartões é ruído que
/// ensina a não ler o cartão — e no dia em que houver um número, ele também não
/// será lido.

interface Props {
  /// Abre o cartaz da obra (a modal da R7).
  onDetails: (workId: string) => void;
  /// Manda a biblioteca filtrar — gênero e década não têm tela própria, e não
  /// deveriam ter: `/api/works` já resolve os dois desde o M2.
  onExplorar: (filtros: Filters) => void;
}

/// A revista da semana.
///
/// **Igual pra todo mundo, e virando na mesma segunda-feira que a vitrine da
/// locadora** — é o que dá assunto em comum (`IDEIAS.md` §2.4). Os desafios
/// (fase 8) são o oposto: sorteados por pessoa.
function Capa({ onDetails }: { onDetails: (id: string) => void }) {
  const [r, setR] = useState<Revista | null>(null);

  useEffect(() => {
    api.revista().then(setR).catch(() => {});
  }, []);

  // A capa não tem esqueleto: enquanto não chega, ela não existe. Um bloco
  // cinza piscando no topo do guia é pior que o guia começar no índice (§24).
  if (!r || r.filmes.length === 0) return null;

  const qual = {
    genero: "gênero da semana",
    decada: "década da semana",
    pais: "país da semana",
    diretor: "diretor da semana",
    saga: "saga da semana",
  }[r.eixo];

  return (
    <section className="revista">
      <header className="revista-topo">
        <span className="revista-eixo">{qual}</span>
        <h1>{r.tema}</h1>
        <span className="revista-vira">até {viraQuando(r.vira_em)}</span>
      </header>

      {/* O ensaio, quando existe. Sem chave do LLM ele simplesmente não está
          aqui — a tela não escreve "em breve" nem inventa prosa. E quando está,
          leva o selo do modelo, como a curiosidade da Wikipédia leva o dela. */}
      {r.ensaio && (
        <div className="revista-ensaio">
          {r.ensaio.split("\n").filter(Boolean).map((p, i) => (
            <p key={i}>{p}</p>
          ))}
          {r.ensaio_por && <span className="revista-selo">escrito por {r.ensaio_por}</span>}
        </div>
      )}

      <div className="revista-filmes">
        {r.filmes.map((f) => (
          <button
            key={f.id}
            className={`revista-filme${f.visto ? " visto" : ""}`}
            onClick={() => onDetails(f.id)}
            title={f.diretor ? `${f.titulo} · ${f.diretor}` : f.titulo}
          >
            {f.poster ? (
              <img src={api.artworkUrl(f.poster)} alt="" loading="lazy" />
            ) : (
              <span className="revista-sem-arte" />
            )}
            <b>{f.titulo}</b>
            <i>{[f.ano, f.diretor].filter(Boolean).join(" · ")}</i>
          </button>
        ))}
      </div>

      {/* O EVENTO. É o que amarra a revista com o resto: participar dá XP e
          conquista, e quem participou aparece pra todo mundo — que é o ponto de
          ele ser coletivo. */}
      {r.evento && (
        <div className={`revista-evento${r.evento.participou ? " feito" : ""}`}>
          {/* Sem pôster, o quadro **não existe** — uma moldura vazia ao lado do
              texto lê como imagem quebrada. §24: linha limpa some. Coleção do
              TMDB nem sempre traz arte própria. */}
          {r.evento.poster && (
            <div className="evento-arte">
              <img src={api.artworkUrl(r.evento.poster)} alt="" />
            </div>
          )}
          <div className="evento-texto">
            <span className="evento-selo">em cartaz esta semana</span>
            <h3>{r.evento.titulo}</h3>
            <p>
              {r.evento.participou
                ? "Você participou."
                : r.evento.obras > 1
                  ? `Termine uma das ${r.evento.obras} obras até segunda pra participar.`
                  : "Termine até segunda pra participar."}
              {r.evento.obras > 1 && ` Você já viu ${r.evento.suas} de ${r.evento.obras}.`}
            </p>
            {/* Quem já esteve. Um evento coletivo em que ninguém sabe quem foi
                não é coletivo. */}
            {r.evento.participantes.length > 0 && (
              <span className="evento-gente">
                {r.evento.participantes.join(", ")}{" "}
                {r.evento.participantes.length === 1 ? "participou" : "participaram"}
              </span>
            )}
          </div>
        </div>
      )}
    </section>
  );
}

/// "segunda", "amanhã". A mesma gramática da vitrine da locadora, porque é a
/// mesma virada — e duas telas dizendo o mesmo instante com palavras diferentes
/// fariam parecer dois relógios.
function viraQuando(iso: string): string {
  const dias = Math.ceil((new Date(iso).getTime() - Date.now()) / 86_400_000);
  if (dias <= 1) return "amanhã";
  return "segunda";
}

/// Os eixos de pessoa, na ordem em que a medição os colocou.
///
/// Direção primeiro porque a cobertura é total: 548 dos 548 filmes
/// identificados têm diretor. Trilha entra porque são 5.879 créditos de
/// compositor que nunca tiveram tela nenhuma — "tudo com trilha do Zimmer" é
/// uma pergunta que não dava pra fazer.
///
/// **Produção não entra**, apesar de ser o segundo maior volume (45.741
/// créditos). A allowlist do §8h já tinha decidido isso uma vez: um eixo de
/// produção enterraria os 1.191 de direção em assistente de efeitos.
const EIXOS = [
  { role: "director", chave: "direcao", titulo: "Direção" },
  { role: "actor", chave: "elenco", titulo: "Elenco" },
  { role: "composer", chave: "trilha", titulo: "Trilha" },
] as const;

export default function Guia({ onDetails, onExplorar }: Props) {
  const [eixos, setEixos] = useState<GuiaEixos | null>(null);
  const [erro, setErro] = useState<string | null>(null);
  /// A pessoa vem **com o eixo por onde se entrou**. Sem isso a ficha dizia
  /// "7 títulos" e listava 12 obras logo abaixo: o guia conta um papel
  /// (direção) e a filmografia traz todos os créditos — o Columbus dirigiu 7 e
  /// assina roteiro ou produção em outros. Os dois números estavam certos e a
  /// tela parecia quebrada, que é o pior dos casos.
  const [pessoa, setPessoa] = useState<{ p: PessoaDoGuia; papel: string } | null>(null);
  const [lista, setLista] = useState<(typeof EIXOS)[number] | null>(null);
  /// Um gancho só pras várias fileiras desta tela — uma por eixo. O `ref` é de
  /// função justamente pra isso (R48).
  const arrastar = useArrastoDeFileira();

  useEffect(() => {
    api
      .guia()
      .then(setEixos)
      .catch((e) => setErro(e instanceof Error ? e.message : String(e)));
  }, []);

  if (pessoa) {
    return (
      <FichaDaPessoa
        pessoa={pessoa.p}
        papel={pessoa.papel}
        onVoltar={() => setPessoa(null)}
        onDetails={onDetails}
      />
    );
  }

  if (lista) {
    return (
      <ListaDoEixo
        eixo={lista}
        onVoltar={() => setLista(null)}
        onAbrir={(p) => setPessoa({ p, papel: lista.titulo })}
      />
    );
  }

  if (erro) return <div className="error banner">{erro}</div>;
  if (!eixos) return <p className="muted guia-carregando">montando o guia…</p>;

  return (
    <div className="guia">
      {/* R34: A CAPA. O índice do §30 não morreu — ele desceu, e virou a parte
          de consulta atrás da revista. É a diferença entre uma enciclopédia e
          uma revista: a enciclopédia continua ali, mas não é o que se vê ao
          abrir. */}
      <Capa onDetails={onDetails} />

      <header className="guia-capa">
        <h1>Wiki de cinema</h1>
        <p>
          Não é a Wikipédia com passos extras. Cada nome aqui vem com{" "}
          <strong>o que você tem</strong> e <strong>o que você fez com isso</strong>.
        </p>
      </header>

      {EIXOS.map((eixo) => {
        const pessoas = eixos[eixo.chave];
        if (pessoas.length === 0) return null;
        const total = pessoas[0]?.total ?? 0;
        return (
          <section key={eixo.role} className="guia-secao">
            <div className="strip">
              <h2>{eixo.titulo}</h2>
              <span className="rule" />
              {total > pessoas.length && (
                <button className="chip" onClick={() => setLista(eixo)}>
                  ver as {total}
                </button>
              )}
            </div>
            <div className="guia-fileira" ref={arrastar}>
              {pessoas.map((p) => (
                <CartaoDePessoa
                  key={p.id}
                  pessoa={p}
                  onAbrir={(escolhida) => setPessoa({ p: escolhida, papel: eixo.titulo })}
                />
              ))}
            </div>
          </section>
        );
      })}

      {eixos.paises.length > 0 && (
        <section className="guia-secao">
          <div className="strip">
            <h2>De onde vêm</h2>
            <span className="rule" />
            {/* A legenda diz a forma do acervo, e não só o tamanho dele.
                Sem ela a seção lê como "você tem filme americano" — que é
                verdade e não é informação. */}
            <span className="strip-meta">
              {eixos.fora_de_hollywood} fora dos Estados Unidos
            </span>
          </div>
          <div className="guia-faixas">
            {eixos.paises.map((p) => (
              <CartaoDeFaixa
                key={p.chave}
                faixa={p}
                onAbrir={() =>
                  onExplorar({ tags: [p.chave], tagMode: "any", kind: "movie", sort: "featured" })
                }
              />
            ))}
          </div>
        </section>
      )}

      <section className="guia-secao">
        <div className="strip">
          <h2>Gênero</h2>
          <span className="rule" />
          <span className="strip-meta">só filmes</span>
        </div>
        <div className="guia-faixas">
          {eixos.generos.map((g) => (
            <CartaoDeFaixa
              key={g.chave}
              faixa={g}
              onAbrir={() =>
                onExplorar({ tags: [g.chave], tagMode: "any", kind: "movie", sort: "featured" })
              }
            />
          ))}
        </div>
      </section>

      <section className="guia-secao">
        <div className="strip">
          <h2>Década</h2>
          <span className="rule" />
          <span className="strip-meta">só filmes</span>
        </div>
        <div className="guia-faixas">
          {eixos.decadas.map((d) => {
            const inicio = Number(d.chave);
            return (
              <CartaoDeFaixa
                key={d.chave}
                faixa={{ ...d, rotulo: `${d.rotulo}s` }}
                onAbrir={() =>
                  onExplorar({
                    yearFrom: inicio,
                    yearTo: inicio + 9,
                    kind: "movie",
                    sort: "year",
                  })
                }
              />
            );
          })}
        </div>
      </section>
    </div>
  );
}

/// O cartão de uma pessoa.
///
/// Retrato quando há; capas empilhadas quando não — que é o cartão de coleção
/// da R4 reaproveitado, e é melhor que um círculo cinza com inicial: as capas
/// dizem *o que* é o trabalho dela, que é a pergunta seguinte de qualquer jeito.
function CartaoDePessoa({
  pessoa,
  onAbrir,
}: {
  pessoa: PessoaDoGuia;
  onAbrir: (p: PessoaDoGuia) => void;
}) {
  const vistos = pessoa.terminadas + pessoa.comecadas;
  const capas = pessoa.posters ?? [];

  return (
    <button className="guia-pessoa" onClick={() => onAbrir(pessoa)}>
      <div className="guia-retrato">
        {pessoa.image_path ? (
          <img src={api.artworkUrl(pessoa.image_path)} alt="" loading="lazy" />
        ) : capas.length > 0 ? (
          <div className="guia-pilha">
            {capas.slice(0, 3).map((c, i) => (
              <img
                key={c}
                src={api.artworkUrl(c)}
                alt=""
                loading="lazy"
                style={{ "--i": i } as CSSProperties}
              />
            ))}
          </div>
        ) : (
          <span className="guia-inicial">{pessoa.name.slice(0, 1)}</span>
        )}
      </div>
      <h3>{pessoa.name}</h3>
      <p className="muted small">
        {pessoa.obras} {pessoa.obras === 1 ? "título" : "títulos"}
      </p>
      {/* Linha limpa some (§24). Sem histórico, o cartão fica quieto em vez de
          anunciar zero — e quando houver número, ele vai ser lido. */}
      {vistos > 0 && (
        <p className="guia-seu">
          <span
            className="guia-medidor"
            style={{ width: `${Math.min(100, (vistos / pessoa.obras) * 100)}%` }}
          />
          {pessoa.terminadas > 0 && `${pessoa.terminadas} terminado${pessoa.terminadas > 1 ? "s" : ""}`}
          {pessoa.terminadas > 0 && pessoa.comecadas > 0 && " · "}
          {pessoa.comecadas > 0 && `${pessoa.comecadas} começado${pessoa.comecadas > 1 ? "s" : ""}`}
        </p>
      )}
    </button>
  );
}

function CartaoDeFaixa({ faixa, onAbrir }: { faixa: FaixaDoGuia; onAbrir: () => void }) {
  const capas = faixa.posters ?? [];
  return (
    <button className="guia-faixa" onClick={onAbrir}>
      <div className="guia-faixa-capas">
        {capas.slice(0, 4).map((c, i) => (
          <img
            key={c}
            src={api.artworkUrl(c)}
            alt=""
            loading="lazy"
            style={{ "--i": i } as CSSProperties}
          />
        ))}
      </div>
      <div className="guia-faixa-texto">
        <h3>{faixa.rotulo}</h3>
        <p className="muted small">{faixa.obras} filmes</p>
      </div>
    </button>
  );
}

/// A lista completa de um eixo, paginada e com busca.
///
/// Paginação de verdade porque a R5 (§16) já ensinou o custo de não ter: a fila
/// de revisão mostrava 50 de 421 pastas e as outras 371 só eram alcançáveis por
/// quem adivinhasse o caminho no filtro.
function ListaDoEixo({
  eixo,
  onVoltar,
  onAbrir,
}: {
  eixo: (typeof EIXOS)[number];
  onVoltar: () => void;
  onAbrir: (p: PessoaDoGuia) => void;
}) {
  const [pessoas, setPessoas] = useState<PessoaDoGuia[]>([]);
  const [q, setQ] = useState("");
  const [carregando, setCarregando] = useState(false);

  const buscar = useCallback(
    async (termo: string) => {
      const achadas = await api.guiaPessoas(eixo.role, termo);
      setPessoas(achadas);
    },
    [eixo.role],
  );

  useEffect(() => {
    const t = setTimeout(() => buscar(q).catch(() => {}), 250);
    return () => clearTimeout(t);
  }, [q, buscar]);

  const total = pessoas[0]?.total ?? 0;

  const mais = async () => {
    setCarregando(true);
    try {
      setPessoas([...pessoas, ...(await api.guiaPessoas(eixo.role, q, pessoas.length))]);
    } finally {
      setCarregando(false);
    }
  };

  return (
    <div className="guia">
      <div className="guia-voltar">
        <button className="chip" onClick={onVoltar}>
          ‹ wiki
        </button>
        <input
          className="search"
          placeholder={`buscar em ${eixo.titulo.toLowerCase()}…`}
          value={q}
          onChange={(e) => setQ(e.target.value)}
        />
      </div>

      <div className="strip">
        <h2>{eixo.titulo}</h2>
        <span className="rule" />
        <span className="strip-meta">
          {pessoas.length < total ? `${pessoas.length} de ${total}` : total}
        </span>
      </div>

      <div className="guia-grade">
        {pessoas.map((p) => (
          <CartaoDePessoa key={p.id} pessoa={p} onAbrir={onAbrir} />
        ))}
      </div>

      {pessoas.length < total && (
        <div className="mais">
          <button className="chip" onClick={mais} disabled={carregando}>
            {carregando ? "carregando…" : "carregar mais"}
          </button>
        </div>
      )}
    </div>
  );
}

/// A ficha de uma pessoa: quem é, o que disso você tem, o que você fez.
///
/// A lista de obras vem de `/api/works?person=`, e não de `/api/people/{id}`,
/// por um motivo prático: aquela projeção devolve `series_title`, e sem ele um
/// diretor de série apareceria como uma parede de 69 episódios sem nada que
/// diga de que série são. É a mesma rota que o clique num nome no cartaz já
/// usa desde o M2.
function FichaDaPessoa({
  pessoa,
  papel,
  onVoltar,
  onDetails,
}: {
  pessoa: PessoaDoGuia;
  /// O eixo por onde se entrou — "Direção", "Elenco", "Trilha". A contagem do
  /// cartão é desse papel, e dizer isso é o que reconcilia os dois números.
  papel: string;
  onVoltar: () => void;
  onDetails: (workId: string) => void;
}) {
  const [obras, setObras] = useState<WorkListItem[] | null>(null);

  useEffect(() => {
    setObras(null);
    api
      .works({ person: pessoa.id, sort: "year" })
      .then(setObras)
      .catch(() => setObras([]));
  }, [pessoa.id]);

  const grupos = agruparPorSerie(obras ?? []);

  return (
    <div className="guia">
      <div className="guia-voltar">
        <button className="chip" onClick={onVoltar}>
          ‹ wiki
        </button>
      </div>

      <header className="guia-ficha">
        <div className="guia-ficha-retrato">
          {pessoa.image_path ? (
            <img src={api.artworkUrl(pessoa.image_path)} alt="" />
          ) : (
            <span className="guia-inicial">{pessoa.name.slice(0, 1)}</span>
          )}
        </div>
        <div className="guia-ficha-texto">
          <h1>{pessoa.name}</h1>
          {pessoa.known_for && <p className="guia-conhecido">{pessoa.known_for}</p>}
          <p className="guia-ficha-linha">
            <strong>{pessoa.obras}</strong> {pessoa.obras === 1 ? "título" : "títulos"} em{" "}
            {papel.toLowerCase()}
            {obras !== null && obras.length > pessoa.obras && (
              <>
                {" "}
                · <strong>{obras.length}</strong> obras no acervo contando os outros papéis
              </>
            )}
          </p>
          {/* O que só o Odeon pode dizer. Quando não há histórico, a frase diz
              isso em vez de mostrar zeros — é convite, não relatório vazio. */}
          {pessoa.terminadas + pessoa.comecadas > 0 ? (
            <p className="guia-ficha-linha seu">
              você terminou <strong>{pessoa.terminadas}</strong>
              {pessoa.comecadas > 0 && (
                <>
                  {" "}
                  e começou <strong>{pessoa.comecadas}</strong>
                </>
              )}
            </p>
          ) : (
            <p className="guia-ficha-linha muted">você ainda não abriu nenhum</p>
          )}
        </div>
      </header>

      {obras === null ? (
        <p className="muted guia-carregando">carregando…</p>
      ) : (
        grupos.map((g) => (
          <section key={g.titulo} className="guia-secao">
            <div className="strip">
              <h2>{g.titulo}</h2>
              <span className="rule" />
              <span className="strip-meta">
                {g.itens.length} {g.itens.length === 1 ? "obra" : "obras"}
              </span>
            </div>
            <div className="guia-obras">
              {g.itens.map((w) => (
                <button key={w.id} className="guia-obra" onClick={() => onDetails(w.id)}>
                  <div
                    className={w.poster ?? w.still ? "guia-obra-arte com-arte" : "guia-obra-arte"}
                    style={{ "--hue": hueFromTitle(w.title) } as CSSProperties}
                  >
                    {w.poster ?? w.still ? (
                      <img src={api.artworkUrl((w.poster ?? w.still)!)} alt="" loading="lazy" />
                    ) : (
                      <span className="poster-title">{w.title}</span>
                    )}
                    {/* Os dois juntos dizem coisas diferentes, e é isso que se
                        quer: o selo é histórico ("já terminei alguma vez") e a
                        barra é o agora ("estou em 20% de uma revisão").

                        O selo só voltou a ser possível depois que o `finished`
                        virou acumulativo (§31) — antes ele era o estado do
                        instante, e um filme terminado e reaberto voltava a
                        constar como não visto. */}
                    {w.finished && <span className="badge visto">visto</span>}
                    {w.position_seconds != null &&
                      w.duration_seconds != null &&
                      w.duration_seconds > 0 && (
                        <div
                          className="progress"
                          style={{
                            width: `${Math.min(100, (w.position_seconds / w.duration_seconds) * 100)}%`,
                          }}
                        />
                      )}
                  </div>
                  <h3>{w.title}</h3>
                  {w.year && <p className="muted small">{w.year}</p>}
                </button>
              ))}
            </div>
          </section>
        ))
      )}
    </div>
  );
}

/// Quebra a filmografia em "Filmes" e uma seção por série.
///
/// Sem isto, um diretor de série vira uma parede de episódios em ordem de ano —
/// que é exatamente a falha que a R3 (§14) corrigiu na biblioteca e a R18
/// corrigiu na contagem. Aqui é a terceira vez que o mesmo agrupamento resolve
/// o mesmo problema, o que é um bom argumento pra ele estar certo.
function agruparPorSerie(obras: WorkListItem[]) {
  const grupos = new Map<string, WorkListItem[]>();
  for (const w of obras) {
    const chave = w.series_title ?? "Filmes e avulsos";
    const atual = grupos.get(chave);
    if (atual) atual.push(w);
    else grupos.set(chave, [w]);
  }
  // Filmes na frente; séries depois, da maior pra menor.
  return [...grupos.entries()]
    .map(([titulo, itens]) => ({ titulo, itens }))
    .sort((a, b) => {
      if (a.titulo === "Filmes e avulsos") return -1;
      if (b.titulo === "Filmes e avulsos") return 1;
      return b.itens.length - a.itens.length;
    });
}
