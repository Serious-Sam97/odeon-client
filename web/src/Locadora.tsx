import { forwardRef, useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState } from "react";
import {
  api,
  type AlvoDaCaixa,
  type CaixaExposta,
  type Emprestada,
  type Fita,
  type Loja,
  type Prateleira,
  type WorkListItem,
} from "./api";
import { duracao, ficha, paraLista } from "./Details";
import { RuidoDeFita } from "./RuidoDeFita";
import MenuDVD from "./MenuDVD";
import { useArrastoDeFileira } from "./arrasto";

/// Por que uma caixa que não está com você não abre — **aqui dentro**.
///
/// ## Ela morava em `liberadas.ts`, e mudou de dono na R56
///
/// A R50 espalhou esta frase por nove telas, porque a regra valia em nove. A
/// R56 devolveu a biblioteca ao modo livre (§71) e a exigência ficou sendo o que
/// o pedido dizia desde o começo: uma regra **da locadora**.
///
/// Com um lugar só, ela mora no lugar. O arquivo que a guardava existia pra
/// sincronizar a resposta entre telas que não perguntam mais.
const POR_QUE_PEGAR =
  "a locadora está no modo escassez: uma cópia por caixa, e assistir exige pegar a fita";

/// A locadora: a biblioteca vista como uma loja de aluguel.
///
/// A unidade aqui **não é a obra, é a caixa**. Um acervo de 17.498 registros
/// vira 600 caixas, e a conta de por que é assim está em `DESIGN.md` (repositório do servidor) §20:
/// uma série é uma caixa de coleção e não 21 fitas, e o que não tem capa não
/// entra numa estante — uma estante é feita de capas.
///
/// Isso torna `/api/library` (a listagem agrupada da R3) a fonte exata: ela já
/// devolve uma entrada por série e uma por obra avulsa. Nenhum backend novo.
///
/// **A R19 deu memória à loja.** Até aqui a locadora era uma vitrine: 600 caixas
/// e estado nenhum, e voltar amanhã encontrava exatamente a mesma loja. Agora
/// alguém está com a fita, e ela volta em algum estado — e o estado não é
/// inventado, é o `playback_state` de quem a teve (§35).

/// As estantes **moram no servidor** desde a R20 (§36).
///
/// Elas eram uma constante deste arquivo, e mudaram de lado por correção, não
/// por arrumação: reivindicar e sortear são a mesma decisão, e decisão só pode
/// morar num lugar.
///
/// **E desde a R29 a estante não é uma cota, é um endereço.** O sorteio pega as
/// caixas do estoque na loja inteira; a estante é só onde cada uma foi cair. Por
/// isso a loja muda de silhueta toda semana — e por isso uma estante pode
/// simplesmente não existir numa segunda-feira.
///
/// A tela ficou com o que é dela: desenhar a caixa e responder ao gesto.

/// O corte entre fita e disco **vem do servidor**, em `loja.ultimo_ano_vhs`.
///
/// Era uma constante daqui, e deixou de ser quando a R19 fez o mesmo número
/// decidir se uma caixa rebobina. Um número de regra escrito nos dois lados é o
/// botão que dizia "ver as 644" e abria 1.424 (§30) esperando pra acontecer de
/// novo — só que desta vez o sintoma seria pior: uma caixa desenhada como VHS
/// que recusa o rebobinar.
export interface Caixa {
  id: string;
  titulo: string;
  ano: number | null;
  poster: string;
  cor: string;
  serie: boolean;
  temporadas: number;
  /// Se existe arquivo. Sabido no momento em que a estante é montada, e é o
  /// que permite a lombada responder ao clique **antes** de o detalhe chegar.
  temArquivo: boolean;
  posicao: number;
}

/// A caixa como a locadora a serve. Uma conversão só, e trivial — o servidor
/// já responde no vocabulário desta tela desde a R20.
function daLoja(c: CaixaExposta): Caixa {
  return {
    id: c.id,
    titulo: c.titulo,
    ano: c.ano,
    poster: c.poster,
    cor: c.dominant_color ?? "#3a3a44",
    serie: c.serie,
    temporadas: c.temporadas,
    temArquivo: !!c.media_file_id || c.serie,
    posicao: c.position_seconds ?? 0,
  };
}

function deLista(w: WorkListItem): Caixa | null {
  if (!w.poster) return null;
  return {
    id: w.id,
    titulo: w.title,
    ano: w.year,
    poster: w.poster,
    cor: w.dominant_color ?? "#3a3a44",
    serie: false,
    temporadas: 0,
    temArquivo: !!w.media_file_id,
    posicao: w.position_seconds ?? 0,
  };
}

/// A caixa que está em mãos, desenhada a partir do empréstimo.
///
/// Existe porque o estoque é curto: a fita que alguém levou quase nunca está
/// entre as 40 da semana, e desde a R29 ela sai da prateleira mesmo quando está.
/// Sem esta conversão ela ficaria invisível — junto com o "pedir de volta", que
/// é a única saída do bloqueio.
function doEmprestimo(e: Emprestada): Caixa | null {
  if (!e.poster) return null;
  return {
    id: e.caixa_id,
    titulo: e.titulo,
    ano: e.ano,
    poster: e.poster,
    cor: e.dominant_color ?? "#3a3a44",
    serie: e.serie,
    temporadas: 0,
    temArquivo: true,
    posicao: 0,
  };
}

/// O alvo que o backend espera: obra avulsa **ou** coleção, nunca as duas.
function alvoDe(c: Caixa): AlvoDaCaixa {
  return c.serie ? { collection_id: c.id } : { work_id: c.id };
}

/// "desde terça", "há 3 dias". Uma data crua num balcão não diz há quanto tempo
/// a fita está fora, que é a única coisa que se quer saber olhando pra ela.
function desde(iso: string): string {
  const dias = Math.floor((Date.now() - new Date(iso).getTime()) / 86_400_000);
  if (dias <= 0) return "hoje";
  if (dias === 1) return "ontem";
  if (dias < 7) return `há ${dias} dias`;
  const semanas = Math.floor(dias / 7);
  return semanas === 1 ? "há uma semana" : `há ${semanas} semanas`;
}

/// Quanto falta do prazo, do ponto de vista de quem está com a fita.
function prazo(iso: string): { texto: string; vencendo: boolean } {
  const dias = Math.ceil((new Date(iso).getTime() - Date.now()) / 86_400_000);
  if (dias <= 0) return { texto: "vence hoje", vencendo: true };
  if (dias === 1) return { texto: "vence amanhã", vencendo: true };
  return { texto: `${dias} dias`, vencendo: dias <= 2 };
}

/// O contador do videocassete: `h:mm:ss`, sempre com as três casas.
///
/// Não reusa o `duracao()` da ficha de propósito — aquele arredonda pra "2h14",
/// que é a resposta certa pra "quanto dura" e a errada pra um ponteiro voltando
/// no tempo. Um contador que pula de 47min pra 46min não parece rebobinar.
function ponteiro(segundos: number): string {
  const s = Math.max(0, Math.round(segundos));
  const h = Math.floor(s / 3600);
  const m = Math.floor((s % 3600) / 60);
  return `${h}:${String(m).padStart(2, "0")}:${String(s % 60).padStart(2, "0")}`;
}

/// Quando a vitrine vira. "segunda" quando ainda falta, "amanhã" na véspera —
/// porque a véspera é o único dia em que o número de dias importa.
function viraQuando(iso: string): string {
  const dias = Math.ceil((new Date(iso).getTime() - Date.now()) / 86_400_000);
  if (dias <= 1) return "amanhã";
  if (dias === 7) return "na próxima segunda";
  return "segunda";
}

/// Como a fita voltou, em português de balcão.
const COMO_VOLTOU: Record<string, string> = {
  rebobinada: "rebobinada",
  "no-meio": "sem rebobinar",
  terminada: "até o fim",
};

export default function Locadora({
  onPlay,
  onAbrirColecao,
}: {
  onPlay: (w: WorkListItem) => void;
  onAbrirColecao: (id: string, titulo: string) => void;
}) {
  const [prateleiras, setPrateleiras] = useState<
    { nome: string; caixas: Caixa[]; total: number }[]
  >([]);
  const [comecadas, setComecadas] = useState<Caixa[]>([]);
  const [erro, setErro] = useState<string | null>(null);
  const [carregando, setCarregando] = useState(true);
  const [naMao, setNaMao] = useState<{ caixa: Caixa; origem: DOMRect } | null>(null);
  /// O balcão: quem está com o quê. Recarrega a cada ação.
  const [loja, setLoja] = useState<Prateleira | null>(null);
  /// A vitrine desta semana. Não recarrega com as ações — a rotação é semanal,
  /// e uma loja que mudasse de layout ao devolver uma fita não seria uma loja.
  const [vitrine, setVitrine] = useState<Loja | null>(null);
  /// O que acabou de acontecer na loja, pra aparecer no balcão por alguns
  /// segundos. É o pedido de volta chegando na hora que justifica isto: um
  /// bloqueio só vira porta se quem está com a fita souber que bateram nela.
  const [recado, setRecado] = useState<string | null>(null);
  /// A caixa cujo menu está aberto. Só disco chega aqui — a fita vai direto
  /// pro filme, porque fita não tem menu (§37).
  const [noMenu, setNoMenu] = useState<Caixa | null>(null);

  const recarregarLoja = useCallback(() => {
    api
      .prateleira()
      .then(setLoja)
      .catch(() => {
        /* a loja sem prateleira ainda é uma loja: as estantes continuam. */
      });
  }, []);

  useEffect(recarregarLoja, [recarregarLoja]);

  /// O barramento do M3 entregando a locadora.
  ///
  /// `EventSource` próprio, como o ao vivo e o player já fazem — a tela que
  /// precisa de evento abre o dela. É o que faz o **pedido de volta** ser uma
  /// conversa em vez de um bilhete no mural: quem está com a fita descobre na
  /// hora, sem recarregar.
  ///
  /// Todas as quatro ações levam ao mesmo `fetch`, e é por isso que o backend
  /// manda um evento só com um campo `acao` em vez de quatro variantes.
  useEffect(() => {
    return api.ouvirEventos((d) => {
      if (d.type !== "locadora") return;
      recarregarLoja();
      setRecado(
        d.acao === "pediu"
          ? `${d.quem_nome} pediu uma fita de volta`
          : d.acao === "pegou"
            ? `${d.quem_nome} pegou ${d.titulo ?? "uma caixa"}`
            : d.acao === "venceu"
              ? "uma fita venceu e voltou pra prateleira"
              : `${d.quem_nome} devolveu uma fita`,
      );
    });
  }, [recarregarLoja]);

  // O recado some sozinho. Um aviso que fica pra sempre vira parte da moldura
  // e deixa de ser lido — que é o mesmo motivo do §24 pra linha limpa sumir.
  useEffect(() => {
    if (!recado) return;
    const t = window.setTimeout(() => setRecado(null), 6000);
    return () => window.clearTimeout(t);
  }, [recado]);

  useEffect(() => {
    let vivo = true;

    // Uma requisição, e não doze. A montagem das estantes — quem reivindica o
    // quê, e o sorteio das caixas do estoque — é uma decisão só, e mora no
    // servidor.
    api
      .estantes()
      .then((l) => {
        if (!vivo) return;
        setVitrine(l);
        const montadas = l.estantes.map((e) => ({
          nome: e.nome,
          total: e.total,
          caixas: e.caixas.map(daLoja),
        }));
        setPrateleiras(montadas);
        setCarregando(false);
      })
      .catch((e) => vivo && (setErro(String(e)), setCarregando(false)));

    api
      .continueWatching()
      .then((ws) => {
        if (vivo) setComecadas(ws.map(deLista).filter((c): c is Caixa => !!c).slice(0, 8));
      })
      .catch(() => {});

    return () => {
      vivo = false;
    };
  }, []);

  /// Caixa → empréstimo em aberto. É este mapa que faz a estante saber que uma
  /// caixa está fora, e ele custa uma passada por meia dúzia de linhas: a
  /// prateleira devolve só o que está em mãos, não as 746.
  const fora = useMemo(() => {
    const m = new Map<string, Emprestada>();
    for (const e of loja?.emprestadas ?? []) m.set(e.caixa_id, e);
    return m;
  }, [loja]);

  /// **A vitrine menos o que está alugado** (R29).
  ///
  /// A R19 desenhava a caixa alugada na estante com uma cinta de papel por
  /// cima. A ideia é outra, e está escrita: *"se alguém aluga, a caixa some da
  /// prateleira e volta quando devolve"*. Uma loja não deixa a caixa vazia
  /// exposta — ela fica com um buraco na fileira, e é o buraco que faz a
  /// escassez ser vista antes de ser lida.
  ///
  /// **E o buraco não é preenchido.** O estoque da semana é o estoque da
  /// semana: puxar uma caixa nova do acervo pra tapar o vão faria a loja ter 40
  /// sempre, e aí levar uma fita não custaria nada a ninguém.
  ///
  /// Some quando o empréstimo **tranca** (`exclusivo`) ou quando é **seu** —
  /// que são os dois casos em que a caixa não está ao seu alcance. Com a
  /// escassez desligada, a de outra pessoa continua exposta: sumir com ela
  /// seria encenar uma disputa que a opção acabou de desligar.
  const expostas = useMemo(
    () =>
      prateleiras
        .map((p) => ({
          ...p,
          caixas: p.caixas.filter((c) => {
            const e = fora.get(c.id);
            return !e || !(e.exclusivo || e.meu);
          }),
        }))
        // Estante que ficou sem nada não vira placa — a mesma regra do §24 que
        // o servidor aplica ao sortear.
        .filter((p) => p.caixas.length > 0),
    [prateleiras, fora],
  );

  const total = useMemo(
    () => expostas.reduce((n, p) => n + p.caixas.length, 0),
    [expostas],
  );

  /// Lançamentos sai do que está **na prateleira**, não do que foi sorteado:
  /// uma placa de "o que há de mais novo" apontando pra uma caixa que alguém
  /// levou é a vitrine mentindo sobre o próprio estoque.
  const lancamentos = useMemo(
    () =>
      [...expostas.flatMap((e) => e.caixas)]
        .sort((a, b) => (b.ano ?? 0) - (a.ano ?? 0))
        .slice(0, 10),
    [expostas],
  );

  /// As caixas que estão em mãos, como caixas.
  const emMaos = useMemo(
    () =>
      (loja?.emprestadas ?? [])
        .map(doEmprestimo)
        .filter((c): c is Caixa => !!c),
    [loja],
  );

  /// O corte vem da vitrine, que é quem sempre chega — a prateleira da loja
  /// pode falhar sem derrubar a loja, mas sem as estantes não há loja nenhuma.
  const ehVhs = useCallback(
    (c: Caixa) => !!c.ano && !!vitrine && c.ano <= vitrine.ultimo_ano_vhs,
    [vitrine],
  );

  /// Quantas caixas o acervo tem nas estantes, contra quantas estão à vista.
  /// As duas juntas, porque só a primeira esconderia a loja e só a segunda
  /// faria a pessoa concluir que o acervo tem 166 filmes.
  /// Servido pelo servidor, e **não somado aqui**. A soma dos totais das
  /// estantes que apareceram dá outro número: uma estante que não recebeu caixa
  /// no sorteio não vem na resposta, e o acervo dela some junto. Na primeira
  /// semana em que isto rodou a porta da loja disse "597 no acervo" de 600 —
  /// faltavam os 3 do faroeste, que a semana não sorteou. É o §14 de novo.
  const noAcervo = vitrine?.no_acervo ?? 0;

  /// Quantas caixas a semana sorteou, antes de alguém levar alguma. A diferença
  /// pra `total` é o buraco — e é ela que a porta da loja conta.
  const sorteadas = useMemo(
    () => prateleiras.reduce((n, p) => n + p.caixas.length, 0),
    [prateleiras],
  );

  return (
    <div className="locadora">
      <header className="loja-porta">
        <h2>Locadora</h2>
        <p className="muted small">
          {carregando
            ? "acendendo as luzes…"
            : total
              ? // O buraco é dito, e não deduzido. "37 de 40, 3 estão fora" é a
                // frase que faz a caixa que sumiu ler como escassez em vez de
                // defeito — e sem ela a pessoa que viu 40 ontem conclui que a
                // loja quebrou. As três contagens são coisas diferentes: o que
                // está na prateleira, o que a semana sorteou, e o acervo.
                `${total} caixas na prateleira` +
                (sorteadas > total ? `, ${sorteadas - total} fora` : "") +
                ` · ${sorteadas} nesta semana, de ${noAcervo} no acervo`
              : sorteadas
                ? "a prateleira está vazia — está tudo emprestado"
                : "nada com capa por aqui"}
          {/* A promessa: a mesma loja a semana toda, e vira na segunda. Sem
              esta linha a rotação leria como sorteio, e uma caixa que sumiu
              leria como defeito. */}
          {!carregando && vitrine && (
            <span className="vira-em"> · vira {viraQuando(vitrine.vira_em)}</span>
          )}
        </p>
      </header>

      {erro && <p className="error">{erro}</p>}

      {loja && <Balcao loja={loja} recado={recado} />}

      {/* O que está em mãos, sempre visível — independente da rotação.
          Uma caixa emprestada que a semana não expôs não pode sumir: com ela
          sumiria o "pedir de volta", e o bloqueio voltaria a ser parede. */}
      {emMaos.length > 0 && (
        <div className="balcao balcao-unico">
          <Estante
            nome="Em mãos"
            legenda={`${emMaos.length} fora da prateleira`}
            caixas={emMaos}
            fora={fora}
            ehVhs={ehVhs}
            onPegar={(c, r) => setNaMao({ caixa: c, origem: r })}
          />
        </div>
      )}

      {(comecadas.length > 0 || lancamentos.length > 0) && (
        <div className="balcao">
          {comecadas.length > 0 && (
            /* Chamava-se "Devoluções" e devolveu o nome quando a devolução
               virou fato: agora existe uma pilha de fitas que voltaram de
               verdade, e duas coisas diferentes não podem ter a mesma placa. */
            <Estante
              nome="Começadas"
              legenda="você parou no meio"
              caixas={comecadas}
              fora={fora}
              ehVhs={ehVhs}
              onPegar={(c, r) => setNaMao({ caixa: c, origem: r })}
            />
          )}
          {lancamentos.length > 0 && (
            <Estante
              nome="Lançamentos"
              legenda="o que há de mais novo"
              caixas={lancamentos}
              fora={fora}
              ehVhs={ehVhs}
              onPegar={(c, r) => setNaMao({ caixa: c, origem: r })}
            />
          )}
        </div>
      )}

      {/* A loja abrindo (R41). Quatro prateleiras vazias com a madeira já
          desenhada — o que cabe na primeira tela —, e elas ocupam desde o
          primeiro quadro a altura que vão ter. Quando as caixas chegam, elas
          caem dentro do espaço que já estava lá em vez de empurrar a página. */}
      {carregando && [0, 1, 2, 3].map((i) => <EstanteVazia key={i} atrasoBase={i * 120} />)}

      {expostas.map((p, i) => (
        <Estante
          key={p.nome}
          atrasoBase={Math.min(i, TETO_ESTANTES) * PASSO_ESTANTE_MS}
          nome={p.nome}
          /* "16 de 113", e não "16". Um número que esconde o total é o
             "Biblioteca 300" que a R3 (§14) corrigiu: sem o segundo número a
             pessoa conclui que a loja tem 16 filmes de terror. Quando tudo
             cabe na estante, o "de" some — dizer "3 de 3" é ruído. */
          legenda={
            p.total > p.caixas.length
              ? `${p.caixas.length} de ${p.total}`
              : `${p.caixas.length} ${p.caixas.length === 1 ? "título" : "títulos"}`
          }
          caixas={p.caixas}
          fora={fora}
          ehVhs={ehVhs}
          onPegar={(c, r) => setNaMao({ caixa: c, origem: r })}
        />
      ))}

      {noMenu && (
        <MenuDVD
          workId={noMenu.id}
          aoFechar={() => setNoMenu(null)}
          aoTocar={(obra, de) => {
            setNoMenu(null);
            // O ponto escolhido vira a posição de retomada do player, que é o
            // caminho que ele já sabe percorrer desde o M6 — o menu não abre
            // um segundo jeito de começar um filme no meio.
            onPlay({ ...obra, position_seconds: de } as WorkListItem);
          }}
        />
      )}

      {naMao && (
        <NaMao
          caixa={naMao.caixa}
          origem={naMao.origem}
          vhs={ehVhs(naMao.caixa)}
          loja={loja}
          emprestimo={fora.get(naMao.caixa.id) ?? null}
          aoMexer={recarregarLoja}
          onAbrirMenu={(c) => {
            setNaMao(null);
            setNoMenu(c);
          }}
          onFechar={() => setNaMao(null)}
          onPlay={(w) => {
            setNaMao(null);
            onPlay(w);
          }}
          onAbrirColecao={(id, t) => {
            setNaMao(null);
            onAbrirColecao(id, t);
          }}
        />
      )}
    </div>
  );
}

/// O balcão: quem está com o quê, e o que acabou de voltar.
///
/// É a peça que faz a loja parecer habitada **antes** de qualquer feed — o que
/// uma locadora sempre teve: a pilha de devoluções do dia, com o estado em que
/// cada fita voltou.
///
/// **Não tem mais nome de grupo em cima.** Ele mostrava "A casa", o círculo a
/// que você pertencia; com uma loja só (R28) não há o que nomear, e um título
/// que diz sempre a mesma palavra é uma linha que ninguém lê.
///
/// A regra do §24 vale aqui inteira: **linha limpa some**. Um balcão que diz
/// "nenhuma fita fora · nenhuma devolução" em todas as visitas ensina a não
/// olhar pro balcão, e aí o dia em que houver algo também não será lido.
function Balcao({ loja, recado }: { loja: Prateleira; recado: string | null }) {
  /// Quem aparece no balcão: quem está com fita **ou** quem tem fama.
  ///
  /// A segunda metade é a R30. *"As pessoas saberem quem devolveu zoado"* não
  /// funciona se o número só existir enquanto a pessoa está com alguma coisa na
  /// mão — a fama tem que sobreviver à devolução, senão ninguém carrega nada.
  const gente = loja.pessoas.filter(
    (p) => p.na_mao > 0 || p.zoadas > 0 || p.rebobinou > 0 || p.no_meio > 0,
  );
  if (!recado && gente.length === 0 && loja.devolvidas.length === 0) return null;

  return (
    <div className="loja-balcao">
      <div className="balcao-topo">
        {gente.map((p) => (
          <span key={p.id} className="membro-chip">
            {p.display_name}
            {p.na_mao > 0 && <i>{p.na_mao}</i>}
            {/* Quantas fitas dela alguém teve que rebobinar, e quantas ela
                rebobinou dos outros. Zero **some** em vez de virar "0" — a
                regra do §24: linha limpa não vira linha. */}
            {p.zoadas > 0 && (
              <u title={`${p.zoadas} ${p.zoadas === 1 ? "fita que alguém teve" : "fitas que alguém teve"} que rebobinar`}>
                ✕{p.zoadas}
              </u>
            )}
            {p.rebobinou > 0 && (
              <s title={`rebobinou ${p.rebobinou} ${p.rebobinou === 1 ? "fita" : "fitas"} dos outros`}>
                ⟲{p.rebobinou}
              </s>
            )}
          </span>
        ))}
        <span className="balcao-limite">
          {loja.posso_pegar > 0
            ? `você pode pegar mais ${loja.posso_pegar}`
            : "você está no limite — devolva uma pra pegar outra"}
        </span>
      </div>

      {recado && <p className="balcao-recado">{recado}</p>}

      {loja.devolvidas.length > 0 && (
        <ul className="balcao-devolucoes">
          {loja.devolvidas.map((d) => (
            <li key={`${d.caixa_id}-${d.devolvido_em}`}>
              <b>{d.titulo}</b>
              <span>
                {/* Quem devolveu como. Fato sobre pessoa real, sem métrica
                    inventada — e é isto que a R24 vai ler. */}
                {d.devolvido_por === "prazo"
                  ? `venceu na mão de ${d.quem_nome}`
                  : `${d.quem_nome} devolveu ${COMO_VOLTOU[d.devolvido_como]}`}
                {d.atrasada && <i className="atrasada">atrasada</i>}
              </span>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

function Estante({
  nome,
  legenda,
  caixas,
  fora,
  ehVhs,
  onPegar,
  atrasoBase = 0,
}: {
  nome: string;
  legenda: string;
  caixas: Caixa[];
  fora: Map<string, Emprestada>;
  ehVhs: (c: Caixa) => boolean;
  onPegar: (c: Caixa, origem: DOMRect) => void;
  /// De quanto tempo esta estante começa a ser abastecida (R41). Zero pras
  /// estantes do balcão, que já estão na tela quando as outras chegam.
  atrasoBase?: number;
}) {
  const arrastar = useArrastoDeFileira();
  return (
    <section className="estante">
      <div className="placa">
        <span>{nome}</span>
        <i>{legenda}</i>
      </div>
      <div className="prateleira">
        <div className="fileira" ref={arrastar}>
          {caixas.map((c, i) => (
            <CaixaNaEstante
              key={c.id}
              caixa={c}
              vhs={ehVhs(c)}
              emprestimo={fora.get(c.id) ?? null}
              onPegar={(r) => onPegar(c, r)}
              atraso={atrasoBase + i * PASSO_MS}
            />
          ))}
        </div>
        <div className="tabua" />
      </div>
    </section>
  );
}

/// Quanto uma caixa espera depois da anterior da mesma estante.
///
/// 34ms é o que faz quarenta caixas lerem como **abastecimento** e não como
/// lista aparecendo: abaixo disso vira um piscar só, e acima a última fica
/// esperando o suficiente pra alguém notar que está esperando.
const PASSO_MS = 34;

/// E de quanto em quanto uma estante começa depois da de cima.
///
/// Somado, o teto é `TETO_ESTANTES` estantes: a cascata inteira não pode ser
/// mais longa que a paciência de quem só quer pegar um filme, e as estantes de
/// baixo já nascem fora da tela.
const PASSO_ESTANTE_MS = 90;
const TETO_ESTANTES = 8;

/// A loja abrindo, no lugar do spinner.
///
/// **A prateleira nasce com a madeira desenhada e vazia.** É a mesma escolha da
/// grade de capítulos do §47 — moldura vazia em vez da palavra "carregando" —,
/// e ela resolve o defeito que a versão anterior tinha: as quarenta caixas
/// chegavam de uma vez, e a página inteira saltava quando chegavam.
///
/// A altura é a de uma estante de verdade desde o primeiro quadro, então nada
/// se move quando as caixas caem: elas caem **dentro** do espaço que já estava
/// lá.
function EstanteVazia({ atrasoBase }: { atrasoBase: number }) {
  return (
    <section className="estante vazia" style={{ ["--atraso" as string]: `${atrasoBase}ms` }}>
      <div className="placa">
        <span />
        <i />
      </div>
      <div className="prateleira">
        <div className="fileira" />
        <div className="tabua" />
      </div>
    </section>
  );
}

/// A caixa em três faces: capa, lombada e topo.
///
/// `rotateY` **positivo** traz a aresta esquerda pra frente, e é por isso que a
/// lombada mora à esquerda da capa. Com o sinal invertido ela fica atrás da
/// arte e o 3D some — foi o primeiro erro da maquete.
///
/// A lombada gira sobre a própria aresta direita (o truque da capa de livro),
/// o que evita calcular um `translateZ` por face.
function CaixaNaEstante({
  caixa,
  vhs,
  emprestimo,
  onPegar,
  atraso = 0,
}: {
  caixa: Caixa;
  vhs: boolean;
  emprestimo: Emprestada | null;
  onPegar: (origem: DOMRect) => void;
  /// Quando esta caixa cai na prateleira (R41). A animação roda na montagem e
  /// só nela: devolver uma fita não faz a loja inteira cair de novo.
  atraso?: number;
}) {
  const classe = [
    "caixa",
    vhs ? "vhs" : "dvd",
    caixa.serie ? "colecao" : "",
    caixa.posicao > 30 ? "comecada" : "",
    // Fora da prateleira. A caixa continua clicável de propósito: é abrindo
    // que se descobre com quem ela está e que dá pra pedir de volta. Uma caixa
    // que não responde ao clique seria a parede que a escassez social evita.
    emprestimo ? (emprestimo.meu ? "comigo" : "alugada") : "",
  ]
    .filter(Boolean)
    .join(" ");

  return (
    <button
      className={classe}
      style={{ ["--cor" as string]: caixa.cor, ["--atraso" as string]: `${atraso}ms` }}
      // O retângulo de onde a caixa saiu: é dele que o voo até o centro parte.
      // Sem isto ela apareceria pronta no meio da tela, que é o que se queria
      // justamente evitar.
      onClick={(e) => onPegar(e.currentTarget.getBoundingClientRect())}
      title={
        emprestimo
          ? `${caixa.titulo} — ${emprestimo.meu ? "com você" : `com ${emprestimo.quem_nome}`}`
          : `${caixa.titulo}${caixa.ano ? ` (${caixa.ano})` : ""}`
      }
    >
      <span className="lombada">
        <span className="lomb-titulo">{caixa.titulo}</span>
        <span className="lomb-selo">{vhs ? "VHS" : "DVD"}</span>
      </span>
      <span className="topo" />
      <span className="frente">
        <img src={api.artworkUrl(caixa.poster)} alt="" loading="lazy" />
        <span className="brilho" />
        <span className={vhs ? "tampa" : "dobradica"} />
        {caixa.serie && caixa.temporadas > 0 && (
          <span className="temporadas">
            {caixa.temporadas} temporada{caixa.temporadas > 1 ? "s" : ""}
          </span>
        )}
        {/* A cinta de papel que a locadora colava na caixa alugada. Diz o nome
            porque é o nome que transforma o bloqueio em porta.

            **Desde a R29 ela quase não aparece nas estantes de gênero**, e não
            porque foi removida: a caixa que tranca sai da prateleira. Onde ela
            vive agora é em "Em mãos" — e nas raras estantes onde um empréstimo
            não exclusivo (escassez desligada) deixou a caixa exposta, que é
            exatamente onde dizer "fulano está com esta, mas você pode pegar
            também" é a informação certa. */}
        {emprestimo && (
          <span className="cinta">
            {emprestimo.meu ? "com você" : emprestimo.quem_nome}
            {emprestimo.pedido_em && <i>pedida</i>}
          </span>
        )}
      </span>
      <span className="sombra" />
    </button>
  );
}

// ------------------------------------------------------------ a contracapa

interface Verso {
  sinopse: string | null;
  /// Só em caixa de coleção, e só quando não há sinopse: os títulos da
  /// primeira temporada.
  ///
  /// **Nenhuma** das 115 séries do acervo tem `overview` — a coleção-série é
  /// um agrupamento que o scanner cria e que o identificador nunca enriquece
  /// (o `external_ids` dela também vem vazio). Sem isto, 71 das 600 caixas
  /// teriam o verso em branco. E a contracapa de um box de verdade traz mesmo
  /// a lista de episódios, não um resumo — então isto não é remendo.
  episodios: string[];
  linhas: string[];
  cenas: string[];
  /// Filme: dá pra tocar. Série: leva pra coleção.
  paraTocar: WorkListItem | null;
}

/// A caixa na mão: sai da estante, cresce e vira.
///
/// A contracapa é o que justifica o 3D — sem ela a caixa é um pôster com
/// sombra. E o que vai atrás de uma caixa de verdade é exatamente o que a R7
/// já sabe montar: sinopse, algumas cenas e a ficha técnica.
/// A pose em que a caixa aterrissa: três quartos, capa e **abertura** à vista.
///
/// `rotateY` negativo traz a aresta direita pra frente. Na estante o giro é
/// positivo, porque lá o que se vê é a lombada; na mão é o contrário — a
/// dobradiça fica na lombada, à esquerda, e o lado por onde a caixa abre é o
/// direito. Era ele que tinha que estar à vista e clicável.
///
/// Os mesmos números estão no `to` do keyframe `odeon-voar-o-giro` — o voo
/// termina exatamente onde o giro interativo começa, senão haveria um salto no
/// quadro em que a animação sai e o `style` entra.
const POSE = { x: -4, y: -24 };

/// Quanto grau por pixel arrastado. Meio grau na horizontal dá uma volta
/// inteira em ~720px, que é o gesto de girar um objeto na mão sem exagero.
const SENSIBILIDADE = { y: 0.5, x: 0.32 };

/// Onde a mídia fica dentro e fora da caixa. Os dois estados são só valores
/// de `transform`: a ida e a volta são a mesma transição CSS ao contrário.
const DENTRO = "translateZ(0px) scale(0.42) rotateX(-12deg) rotateY(0deg)";
const SAIDA = { z: 300 };

/// Onde o `odeon-encarar` deixa a caixa ao abrir. Voltar pra cá em vez de pro
/// giro anterior evita um salto no quadro em que a animação sai de cena.
const POSE_ABERTA = { x: -3, y: 4 };

function NaMao({
  caixa,
  origem,
  vhs,
  loja,
  emprestimo,
  aoMexer,
  onFechar,
  onPlay,
  onAbrirMenu,
  onAbrirColecao,
}: {
  caixa: Caixa;
  origem: DOMRect;
  vhs: boolean;
  loja: Prateleira | null;
  emprestimo: Emprestada | null;
  aoMexer: () => void;
  onFechar: () => void;
  onPlay: (w: WorkListItem) => void;
  onAbrirMenu: (c: Caixa) => void;
  onAbrirColecao: (id: string, titulo: string) => void;
}) {
  const [verso, setVerso] = useState<Verso | null>(null);
  const [fase, setFase] = useState<
    "voando" | "na-mao" | "abrindo" | "midia" | "guardando" | "fita" | "tocando"
  >("voando");
  /// A mídia está fora da caixa? Dirige a transição — abrir e fechar percorrem
  /// o mesmo caminho em sentidos opostos, sem keyframe e sem fill-mode.
  const [fora, setFora] = useState(false);
  /// Giro do disco/fita, independente do giro da caixa.
  const [giroMidia, setGiroMidia] = useState({ x: -12, y: 0 });
  const [giro, setGiro] = useState(POSE);
  const voo = useRef<HTMLDivElement>(null);
  const arrasto = useRef<{ x: number; y: number; andou: number; alvo: Element } | null>(null);
  /// O relógio da abertura. Guardado porque fechar no meio dela tem que
  /// cancelar o que ela ia disparar — sem isto, apertar Esc durante a animação
  /// fechava a caixa e o filme começava mesmo assim, um segundo depois.
  const relogio = useRef<number | undefined>(undefined);
  /// Onde o toque começou no fundo — é o que separa "cliquei fora" de
  /// "arrastei o disco e soltei fora dele".
  const toqueFundo = useRef<{ x: number; y: number; alvo: Element } | null>(null);
  const [erro, setErro] = useState<string | null>(null);
  const fechar = useRef(onFechar);
  fechar.current = onFechar;

  /// O balcão está ocupado — uma ação de aluguel em voo. Impede o clique duplo
  /// virar dois empréstimos, que o banco recusaria de qualquer jeito, mas com
  /// um erro em vez de silêncio.
  const [ocupado, setOcupado] = useState(false);
  /// Quanto a fita ainda tem no ponteiro, durante a animação. `null` = parada.
  ///
  /// **Só o segundo inteiro mora aqui.** O giro dos carretéis é escrito direto
  /// no elemento, como propriedade CSS — pôr o ângulo em estado repintaria a
  /// tela sessenta vezes por segundo pra mover dois discos, que é a mesma conta
  /// que a agulha do "ao vivo" já tinha feito (§25).
  const [rebobinando, setRebobinando] = useState<number | null>(null);
  const rebobinadorRef = useRef<HTMLDivElement>(null);
  const ruido = useRef<RuidoDeFita | null>(null);

  // A tela pode fechar no meio do gesto — e um oscilador vivo depois disso é um
  // aparelho ligado numa sala vazia.
  useEffect(() => () => ruido.current?.cortar(), []);
  const [aviso, setAviso] = useState<string | null>(null);

  /// **Onde a fita está** (R30).
  ///
  /// Não é mais `caixa.posicao` — aquilo era o *seu* progresso, e a fita é um
  /// objeto: ela está onde a última pessoa a deixou, tenha sido você ou não.
  /// Vem da rota própria, e a estante continua sem saber.
  const [fita, setFita] = useState<Fita | null>(null);
  const posicao = fita?.posicao_segundos ?? 0;
  /// O `concluir` do play roda dentro de um `setTimeout` e enxergaria a posição
  /// do render em que foi criado — que é a de **antes** do rebobinar. A fita
  /// começaria no minuto 47 logo depois de voltar ao zero.
  const posicaoAgora = useRef(0);
  posicaoAgora.current = posicao;

  const balcao = async (acao: () => Promise<string | null>) => {
    if (ocupado) return;
    setOcupado(true);
    setErro(null);
    try {
      const msg = await acao();
      if (msg) setAviso(msg);
      aoMexer();
    } catch (e) {
      // O 403 do aluguel traz o nome de quem está com a caixa — é a mensagem
      // mais útil que esta tela pode mostrar, e ela vem pronta do servidor.
      setErro(e instanceof Error ? e.message : String(e));
    } finally {
      setOcupado(false);
    }
  };

  /// **A fita chega quando alguém vai pôr pra tocar** — e não com a estante.
  ///
  /// A decisão é cena a cena do `IDEIAS.md` §3.9: *"você descobre quando põe
  /// pra tocar — não na estante, não antes"*. Buscar aqui, ao abrir a caixa,
  /// é o mais tarde possível sem deixar o clique no play esperando por uma
  /// requisição — e nada do que chega é desenhado até a tela da fita.
  useEffect(() => {
    if (!vhs || caixa.serie) return;
    let vivo = true;
    api
      .fita(caixa.id)
      .then((f) => vivo && setFita(f))
      .catch(() => {
        /* sem a fita a caixa ainda toca: ela é o tema, não o mecanismo. */
      });
    return () => {
      vivo = false;
    };
  }, [caixa.id, caixa.serie, vhs]);

  /// Quanto tempo o rebobinar leva, de verdade.
  ///
  /// *"Rebobinar leva alguns segundos de verdade, pra simular o que se passava
  /// — mas sem ser massante."* Então é proporcional a quanto a fita andou, e
  /// não um número fixo: uma fita quase no fim custa mais que uma que alguém
  /// largou aos dez minutos, que é a única coisa que a espera precisa ensinar.
  ///
  /// Um segundo pra cada doze minutos de fita, entre 2,5s e 10s. Um VHS de
  /// verdade levava perto de dois minutos pra voltar uma fita inteira; dez
  /// segundos é a caricatura disso — longa o bastante pra irritar um pouco,
  /// curta o bastante pra ninguém sair da sala.
  const duracaoDoRebobinar = (segundos: number) =>
    Math.min(10_000, Math.max(2_500, (segundos / 720) * 1000));

  /// Rebobinar, com a animação que representa o que ele faz.
  ///
  /// O ponteiro volta de onde a fita estava até zero, no tempo real que leva.
  /// O gesto não é decorativo — e desde a R30 ele também não é destrutivo:
  /// **mexe na fita, não no "continuar de onde parou" de ninguém.** É a mesma
  /// mudança que dissolveu a recusa registrada no §35.
  const rebobinar = (depois?: () => void) => {
    const de = posicao;
    const inicio = performance.now();
    const total = duracaoDoRebobinar(de);

    // Os ângulos dos dois carretéis, acumulados quadro a quadro. Acumular em
    // vez de calcular do tempo é o que permite a velocidade variar sem o disco
    // dar um salto quando ela muda.
    let anguloA = 0;
    let anguloB = 0;
    let ultimo = inicio;

    ruido.current = new RuidoDeFita();
    ruido.current.comecar();

    // *"Alma não pode custar enjoo"* — a mesma linha da barra de cima (§52). A
    // regra global do CSS mata `animation` e `transition`, e não alcança um
    // ângulo escrito por JS: quem pediu menos movimento continua vendo os dois
    // discos girarem por dez segundos. Aqui eles ficam parados, e a espera —
    // que é o conteúdo do gesto — continua igual.
    const parado = window.matchMedia?.("(prefers-reduced-motion: reduce)").matches ?? false;

    const quadro = (agora: number) => {
      const t = Math.min(1, (agora - inicio) / total);
      const dt = Math.min(0.05, (agora - ultimo) / 1000);
      ultimo = agora;

      // **A velocidade é proporcional ao que falta** (§4.5): a fita sai
      // rápido e vai perdendo força até parar. Um mínimo de 0,25 existe pra
      // ela não parecer travada no último segundo.
      const velocidade = Math.max(0.25, 1 - t);
      // Sentidos opostos, que é o que os dois carretéis de um VHS fazem: um
      // entrega a fita e o outro recolhe.
      anguloA -= dt * velocidade * 1000;
      anguloB += dt * velocidade * 640;

      const el = rebobinadorRef.current;
      if (el) {
        if (!parado) {
          el.style.setProperty("--giro-a", `${anguloA}deg`);
          el.style.setProperty("--giro-b", `${anguloB}deg`);
        }
        // Quanta fita ainda está no carretel da DIREITA — o mesmo número do
        // ponteiro, normalizado. Vai de 1 a 0, e é ele que faz o rolo da
        // esquerda engordar enquanto o da direita afina: a fita voltando pro
        // lugar de onde saiu, que é o que "rebobinar" quer dizer.
        el.style.setProperty("--restante", `${1 - t}`);
      }
      ruido.current?.velocidade(velocidade);

      const segundos = Math.round(de * (1 - t));
      setRebobinando((atual) => (atual === segundos ? atual : segundos));

      if (t < 1) return requestAnimationFrame(quadro);

      // O TRANCO. A classe fica no elemento os 160ms da animação, e é ela que
      // dá o pulo de um quadro — o mecanismo batendo no fim de curso. Sem ele o
      // movimento simplesmente para, e parar não é chegar.
      rebobinadorRef.current?.classList.add("trancou");
      ruido.current?.parar();
      ruido.current = null;

      window.setTimeout(() => {
        setRebobinando(null);
        void balcao(async () => {
          const { rebobinadas } = await api.rebobinar(alvoDe(caixa));
          setFita((f) => (f ? { ...f, posicao_segundos: 0, minha: true } : f));
          depois?.();
          return rebobinadas > 0 ? "fita rebobinada" : "já estava no começo";
        });
      }, 260);
    };
    requestAnimationFrame(quadro);
  };

  // O aviso some sozinho, como o do balcão.
  useEffect(() => {
    if (!aviso) return;
    const t = window.setTimeout(() => setAviso(null), 4000);
    return () => window.clearTimeout(t);
  }, [aviso]);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && fechar.current();
    window.addEventListener("keydown", onKey);
    return () => {
      window.removeEventListener("keydown", onKey);
      window.clearTimeout(relogio.current);
    };
  }, []);

  /// O voo da estante até o centro, pela técnica do FLIP: o elemento já está no
  /// lugar final, e o que se anima é a diferença entre onde ele estava e onde
  /// ele está. Medir depois do layout e antes da pintura é a razão de ser
  /// `useLayoutEffect` — com `useEffect` o primeiro quadro sairia já centrado.
  useLayoutEffect(() => {
    const el = voo.current;
    if (!el) return;

    // Desligar a animação ANTES de medir, e forçar o reflow.
    //
    // `getBoundingClientRect` devolve o retângulo **transformado**. Como a
    // classe `voando` já está no elemento no primeiro render, medir direto dá
    // a posição que a própria animação impôs — e no StrictMode, que roda o
    // efeito duas vezes, a segunda medição via a caixa já deslocada por `--dx`
    // e concluía que o deslocamento era zero. O voo simplesmente não saía do
    // lugar.
    el.style.animation = "none";
    void el.offsetWidth;
    const fim = el.getBoundingClientRect();
    const dx = origem.left + origem.width / 2 - (fim.left + fim.width / 2);
    const dy = origem.top + origem.height / 2 - (fim.top + fim.height / 2);
    el.style.setProperty("--dx", `${Math.round(dx)}px`);
    el.style.setProperty("--dy", `${Math.round(dy)}px`);
    el.style.setProperty("--s", `${(origem.width / fim.width).toFixed(3)}`);
    el.style.animation = "";
    const t = window.setTimeout(() => setFase("na-mao"), 640);
    return () => window.clearTimeout(t);
  }, [origem]);

  useEffect(() => {
    let vivo = true;

    const carregar = async (): Promise<Verso> => {
      if (caixa.serie) {
        const { collection, children, items } = await api.collection(caixa.id);

        // Os episódios pendem das TEMPORADAS, não da série: pedir a coleção da
        // série devolve as temporadas em `children` e `items` vazio. Sem esta
        // segunda volta o verso da caixa de coleção nunca teria cena nenhuma.
        let quadros = items;
        if (quadros.every((i) => !i.still) && children.length > 0) {
          quadros = await api
            .collection(children[0].id)
            .then((c) => c.items)
            .catch(() => quadros);
        }

        const linhas = [
          `${children.length || caixa.temporadas} temporada${(children.length || caixa.temporadas) > 1 ? "s" : ""}`,
          `${collection.item_count} episódio${collection.item_count > 1 ? "s" : ""}`,
          collection.year ? `desde ${collection.year}` : null,
        ].filter((x): x is string => !!x);
        return {
          sinopse: collection.overview ?? collection.description ?? null,
          episodios: quadros
            .filter((i) => i.kind === "episode")
            .slice(0, 8)
            .map((i) =>
              i.episode_number ? `${i.episode_number}. ${i.title}` : i.title,
            ),
          linhas,
          // Quadros de episódios diferentes: é a única fonte de "cenas" que
          // não é a mesma imagem repetida.
          cenas: quadros
            .map((i) => i.still)
            .filter((s): s is string => !!s)
            .slice(0, 3),
          paraTocar: null,
        };
      }

      const w = await api.detail(caixa.id);
      const arquivo = w.files[0];
      const linhas = [
        w.runtime_seconds ? duracao(w.runtime_seconds) : null,
        ...(arquivo ? ficha(arquivo) : []),
      ].filter((x): x is string => !!x);
      return {
        sinopse: w.overview,
        episodios: [],
        linhas,
        cenas: [w.artwork.backdrop].filter((s): s is string => !!s),
        paraTocar: arquivo ? paraLista(w, arquivo, null) : null,
      };
    };

    carregar()
      .then((v) => vivo && setVerso(v))
      .catch((e) => vivo && setErro(String(e)));

    return () => {
      vivo = false;
    };
  }, [caixa]);

  /// R50 — com a escassez ligada, a caixa só abre pra quem está com ela.
  ///
  /// **É a consequência da própria escassez**, e não uma regra ao lado: "uma
  /// cópia por caixa, e quem pegou tirou da prateleira" só quer dizer alguma
  /// coisa se quem não pegou também não assiste. Com a escassez desligada, nada
  /// aqui muda — a locadora volta a ser um tema.
  ///
  /// E o "não" tem a saída ao lado: o botão de pegar emprestado está no balcão,
  /// a dois centímetros, na mesma tela. Não é uma parede, é uma fila.
  const comigo = !loja?.opcoes.escassez || !!emprestimo?.meu;
  const podeAbrir = caixa.temArquivo && comigo;

  // O `abrir` roda fora do render e precisa do verso mais recente, não do que
  // existia quando ele foi criado.
  const versoAgora = useRef<Verso | null>(null);
  versoAgora.current = verso;

  /// Abrir a caixa **não** toca nada: ela abre e entrega a mídia.
  ///
  /// Antes o filme começava no fim da animação, e a caixa abria só pra dar
  /// lugar ao player — o disco aparecia por meio segundo e sumia. Agora a
  /// abertura termina com o disco (ou a fita) no centro, girável, e é o clique
  /// no meio dele que dá play. São dois gestos porque são duas decisões:
  /// abrir a caixa e pôr pra rodar.
  ///
  /// O gesto não espera o detalhe da obra: a condição sai do `media_file_id`,
  /// que a estante já conhece.
  const abrir = () => {
    if (fase !== "na-mao" || !podeAbrir) return;
    setFase("abrindo");
    // A capa leva ~0,3s pra começar a girar; a mídia sai depois dela.
    relogio.current = window.setTimeout(() => {
      setFora(true);
      relogio.current = window.setTimeout(() => setFase("midia"), 780);
    }, 600);
  };

  /// Guardar: a mídia volta pra dentro, a capa fecha, e a caixa fica de novo
  /// na mão. É o inverso exato de abrir — clicar fora do disco desfaz o gesto
  /// em vez de fechar a locadora inteira.
  const guardar = () => {
    if (fase !== "midia") return;
    window.clearTimeout(relogio.current);
    setFora(false);
    setGiroMidia({ x: -12, y: 0 });
    setGiro(POSE_ABERTA);
    setFase("guardando");
    relogio.current = window.setTimeout(() => setFase("na-mao"), 640);
  };

  /// O play, agora no centro da mídia.
  const tocar = () => {
    if (fase !== "midia") return;

    // **O disco leva ao menu; a fita vai direto pro filme.** (R21, §37)
    //
    // Um menu que se mete entre o clique e o filme sem informar nada é a intro
    // que todo mundo pula — o §22 já tinha movido o "tocar" pra uma decisão
    // consciente, mas ali havia sinopse pra ler. Aqui o menu não é atraso: ele
    // é onde a caixa aberta já leva, e é o objeto que se veio buscar.
    //
    // E a fita não tem menu. Ela tem rebobinar — a mesma diferença de formato
    // que a R19 (§35) transformou em comportamento, vista do outro lado.
    if (!caixa.serie && !vhs) {
      setFase("tocando");
      relogio.current = window.setTimeout(() => onAbrirMenu(caixa), 620);
      return;
    }

    // **A fita se apresenta antes do filme** (R30).
    //
    // Só quando ela está no meio E foi outra pessoa que a deixou assim. Se fui
    // eu, isto é a minha sessão continuando — obrigar alguém a rebobinar o
    // próprio filme porque saiu pra pegar água seria transformar o tema em
    // castigo.
    if (vhs && !caixa.serie && fita && fita.posicao_segundos > 0 && !fita.minha) {
      setFase("fita");
      return;
    }

    rodarFilme();
  };

  /// Do palco pro filme. Separado do gesto porque a tela da fita também termina
  /// aqui — depois do rebobinar, e só depois dele.
  const rodarFilme = () => {
    setFase("tocando");

    const limite = Date.now() + 8000;
    const concluir = () => {
      if (caixa.serie) return onAbrirColecao(caixa.id, caixa.titulo);
      const pronto = versoAgora.current?.paraTocar;
      if (pronto) {
        // **Quem manda no ponto de partida é a fita**, não o seu
        // `playback_state`. É a diferença inteira entre um objeto e uma
        // memória: a fita começa onde ela está, e depois de rebobinada começa
        // do zero — pra você e pra qualquer um.
        return onPlay(
          vhs && !caixa.serie
            ? ({ ...pronto, position_seconds: posicaoAgora.current } as WorkListItem)
            : pronto,
        );
      }
      // Ainda não chegou: espera com a sala já escurecendo, em vez de devolver
      // um clique sem resposta.
      if (Date.now() < limite) {
        relogio.current = window.setTimeout(concluir, 120);
        return;
      }
      setFase("midia");
    };

    relogio.current = window.setTimeout(concluir, 620);
  };

  // Girar arrastando. O limiar de 6px separa "arrastei pra ver o outro lado" de
  // "cliquei na lombada" — sem ele, todo giro que começasse na lombada abriria
  // a caixa no fim do gesto.
  const pegar = (e: React.PointerEvent) => {
    if (fase !== "na-mao" && fase !== "midia") return;
    arrasto.current = { x: e.clientX, y: e.clientY, andou: 0, alvo: e.target as Element };
    // Capturar no `currentTarget` (a caixa), não no alvo: o alvo pode ser uma
    // das faces, e soltar o ponteiro fora dela perderia o resto do gesto.
    //
    // `setPointerCapture` lança quando o ponteiro não está ativo. O giro não
    // depende dele — só melhora o gesto que sai do elemento — então uma falha
    // aqui não pode derrubar o resto.
    try {
      e.currentTarget.setPointerCapture(e.pointerId);
    } catch {
      /* segue sem captura */
    }
  };
  const mover = (e: React.PointerEvent) => {
    const a = arrasto.current;
    if (!a) return;
    const dx = e.clientX - a.x;
    const dy = e.clientY - a.y;
    a.andou += Math.abs(dx) + Math.abs(dy);
    a.x = e.clientX;
    a.y = e.clientY;
    const girar = (g: { x: number; y: number }) => ({
      y: g.y + dx * SENSIBILIDADE.y,
      x: Math.max(-42, Math.min(42, g.x - dy * SENSIBILIDADE.x)),
    });
    if (fase === "midia") setGiroMidia(girar);
    else setGiro(girar);
  };

  /// O toque é resolvido aqui, e não num `onClick`.
  ///
  /// Com o ponteiro capturado — e ele é capturado no `pointerdown`, pra que o
  /// giro não se perca ao sair do elemento — o navegador **redireciona o
  /// `click` para quem capturou**. Ou seja: o `onClick` da abertura nunca
  /// disparava, e clicar nela não fazia nada. Aqui a decisão sai de onde o
  /// dedo desceu, que a captura não altera.
  const soltar = () => {
    const a = arrasto.current;
    arrasto.current = null;
    if (!a || a.andou > 6) return; // foi giro, não toque
    if (a.alvo.closest(".abertura")) abrir();
    else if (a.alvo.closest(".tocar")) tocar();
  };
  const arrastou = () => (arrasto.current?.andou ?? 0) > 6;

  return (
    <div
      className={`mao-fundo${fase === "tocando" ? " apagando" : ""}`}
      // Decidido no ponteiro, não no `click`.
      //
      // O `click` do navegador é disparado no ancestral comum entre onde o
      // dedo desceu e onde subiu — então arrastar o disco pra fora dele
      // gerava um clique em `.mao-fundo` e a caixa se fechava sozinha no meio
      // do gesto. Aqui a decisão olha **onde desceu** e **quanto andou**.
      onPointerDown={(e) => {
        toqueFundo.current = { x: e.clientX, y: e.clientY, alvo: e.target as Element };
      }}
      onPointerUp={(e) => {
        const t = toqueFundo.current;
        toqueFundo.current = null;
        if (!t || fase === "tocando" || fase === "guardando") return;
        if (Math.abs(e.clientX - t.x) + Math.abs(e.clientY - t.y) > 6) return; // girou
        if (t.alvo.closest(".midia3d")) return; // a mídia é assunto do `.voo`
        if (fase === "midia") return guardar();
        if (t.alvo === e.currentTarget) onFechar();
      }}
    >
      {/* Os handlers de ponteiro moram aqui, no ancestral comum — não na caixa.
          A caixa e o palco da mídia são irmãos, e o gesto tem que valer nos
          dois: com os handlers na caixa, arrastar o disco não girava nada e o
          clique no centro não tocava, porque nenhum evento do palco passava
          por ela. */}
      <div
        ref={voo}
        className={`voo${fase === "voando" ? " voando" : ""}`}
        onPointerDown={pegar}
        onPointerMove={mover}
        onPointerUp={soltar}
        onPointerCancel={soltar}
      >
        <div
          className={[
            "caixa grande",
            vhs ? "vhs" : "dvd",
            caixa.serie ? "colecao" : "",
            fase === "abrindo" || fase === "midia" || fase === "tocando" ? "aberta" : "",
            fase === "midia" || fase === "tocando" ? "recuada" : "",
            fase === "na-mao" ? "girável" : "",
            // A mesma classe da estante, pra cinta ter a mesma cor nos dois
            // lugares: papel quando é de outro, âmbar quando é sua.
            emprestimo ? (emprestimo.meu ? "comigo" : "alugada") : "",
          ]
            .filter(Boolean)
            .join(" ")}
          style={{
            ["--cor" as string]: caixa.cor,
            transform: `rotateX(${giro.x.toFixed(1)}deg) rotateY(${giro.y.toFixed(1)}deg)`,
          }}
        >
          <span className="lombada">
            <span className="lomb-titulo">{caixa.titulo}</span>
            <span className="lomb-selo">{vhs ? "VHS" : "DVD"}</span>
          </span>
          <span className="topo" />
          <span className="fundo" />

          {/* A ABERTURA: a aresta livre, do lado oposto à dobradiça. É por aqui
              que se abre uma caixa de verdade, e é por aqui que o filme começa.
              A lombada continua sendo a lombada — o que se lê na estante. */}
          <span
            className={`abertura${podeAbrir ? " acionavel" : ""}`}
            role={podeAbrir ? "button" : undefined}
            title={podeAbrir ? "abrir a caixa e começar" : undefined}
            onClick={() => !arrastou() && abrir()}
          >
            <span className="fresta" />
          </span>

          {/* O que fica à vista quando a capa abre. */}
          <span className="interior">
            <span className="interior-marca">{vhs ? "VHS" : "DVD"}</span>
          </span>

          <span className="frente">
            {/* `draggable={false}`: sem isto o navegador inicia o próprio
                arrasto de imagem no primeiro pixel, o `pointermove` nunca
                chega, e girar a caixa vira arrastar uma miniatura da capa. */}
            <img src={api.artworkUrl(caixa.poster)} alt="" draggable={false} />
            <span className="brilho" />
            <span className={vhs ? "tampa" : "dobradica"} />
            {/* A mesma cinta da estante. Sem ela, pegar uma caixa alugada
                fazia a marca sumir justo quando ela vira o objeto principal
                da tela — e a caixa na mão passava a parecer disponível. */}
            {emprestimo && (
              <span className="cinta">
                {emprestimo.meu ? "com você" : emprestimo.quem_nome}
                {emprestimo.pedido_em && <i>pedida</i>}
              </span>
            )}
          </span>

        <div className="contracapa">
          <header className="verso-topo">
            <h3>{caixa.titulo}</h3>
            <p>
              {[caixa.ano, caixa.serie ? "série" : "filme", vhs ? "VHS" : "DVD"]
                .filter(Boolean)
                .join(" · ")}
            </p>
          </header>

          {erro && <p className="error small">{erro}</p>}

          {/* Sinopse E lista: é o que um box tem atrás. A lista deixou de ser
              substituta da sinopse quando o reparo deu sinopse a 114 das 115
              séries — se continuasse alternativa, viraria código morto. */}
          {(!verso || verso.sinopse || verso.episodios.length === 0) && (
            <p className="verso-sinopse">
              {verso ? (verso.sinopse ?? "Sem sinopse — a caixa veio sem o texto de trás.") : "…"}
            </p>
          )}

          {verso && verso.episodios.length > 0 && (
            <div className="verso-episodios">
              <h4>Nesta caixa</h4>
              <ol>
                {verso.episodios.map((e) => (
                  <li key={e}>{e}</li>
                ))}
              </ol>
            </div>
          )}

          {verso && verso.cenas.length > 0 && (
            <div className={`verso-cenas${verso.cenas.length === 1 ? " unica" : ""}`}>
              {verso.cenas.map((c) => (
                <img key={c} src={api.artworkUrl(c)} alt="" />
              ))}
            </div>
          )}

          <div className="verso-rodape">
            <ul className="verso-ficha">
              {verso?.linhas.map((l) => (
                <li key={l}>{l}</li>
              ))}
            </ul>

            <div className="verso-acao">
              <CodigoDeBarras semente={caixa.id} />
              {caixa.serie ? (
                <button className="cartaz-play" disabled={!comigo} onClick={abrir}>
                  {comigo ? "▸ ver a série" : "▸ pegue emprestado"}
                </button>
              ) : (
                <button
                  className="cartaz-play"
                  disabled={!podeAbrir}
                  onClick={abrir}
                  title={comigo ? undefined : POR_QUE_PEGAR}
                >
                  {!comigo
                    ? "▸ pegue emprestado"
                    : caixa.posicao > 30
                      ? "▸ continuar"
                      : "▸ assistir"}
                </button>
              )}
            </div>
          </div>
          </div>
        </div>

        {/* IRMÃO da caixa, não filho.
            Aninhado dentro dela, o disco herdava o `opacity: 0.18` da caixa
            recuada e sumia junto — "o CD aparece e depois some". E como a
            opacidade tem transição de 0,4s, um print tirado 300ms depois ainda
            pegava o disco quase opaco: eu conferi no instante errado três
            vezes seguidas. */}
        {fase !== "voando" && fase !== "na-mao" && (
          <div className="palco-midia">
            <div
              className={`midia3d ${vhs ? "fita" : "disco"}${
                fase === "midia" ? " assentada" : ""
              }`}
              style={{
                opacity: fora ? 1 : 0,
                transform: fora
                  ? `translateZ(${SAIDA.z}px) scale(1) rotateX(${giroMidia.x.toFixed(1)}deg) rotateY(${giroMidia.y.toFixed(1)}deg)`
                  : DENTRO,
              }}
            >
              {vhs ? <Fita caixa={caixa} /> : <Disco caixa={caixa} />}
            </div>
          </div>
        )}
      </div>

      {/* O ponteiro voltando. Sobrepõe tudo porque é o que está acontecendo:
          a fita está sendo rebobinada, e o número que desce é o progresso
          real sendo apagado. */}
      {rebobinando !== null && (
        <Rebobinando ref={rebobinadorRef} ponteiro={ponteiro(rebobinando)} />
      )}

      {(fase === "na-mao" || fase === "midia") && (
        <div className="mao-rodape">
          <span className="mao-dica">
            {fase === "midia"
              ? `arraste pra girar · centro ${caixa.serie ? "abre a série" : "toca"} · fora, guarda`
              : comigo
                ? "arraste pra girar · clique na abertura pra abrir a caixa"
                : "arraste pra girar · pegue emprestado pra abrir"}
          </span>

          {/* O BALCÃO. Aqui, e não na contracapa, porque é a única parte do
              palco que fica visível sem girar a caixa — e "quem está com esta"
              não pode depender de o usuário descobrir que a caixa vira. */}
          {loja && (
            <div className="mao-balcao">
              {erro && <span className="mao-erro">{erro}</span>}
              {aviso && !erro && <span className="mao-aviso">{aviso}</span>}

              {!emprestimo && (
                <button
                  className="balcao-btn pegar"
                  disabled={ocupado || loja.posso_pegar <= 0}
                  title={
                    loja.posso_pegar <= 0
                      ? `você já está com ${loja.opcoes.limite_por_pessoa}`
                      : `${loja.opcoes.prazo_dias} dias`
                  }
                  onClick={() =>
                    void balcao(async () => {
                      const r = await api.alugar(alvoDe(caixa));
                      return `sua por ${r.vence_em_dias} dias`;
                    })
                  }
                >
                  {loja.posso_pegar > 0 ? "pegar emprestado" : "no limite"}
                </button>
              )}

              {emprestimo?.meu && (
                <>
                  <span className={`balcao-prazo${prazo(emprestimo.vence_em).vencendo ? " vencendo" : ""}`}>
                    {prazo(emprestimo.vence_em).texto}
                    {/* Quem pediu, e quando. É o aviso — e ele não encurta o
                        prazo de ninguém: dar a um membro poder sobre o prazo do
                        outro transformaria a locadora em disputa. */}
                    {emprestimo.pedido_por_nome && (
                      <i> · {emprestimo.pedido_por_nome} pediu de volta</i>
                    )}
                  </span>
                  {/* **Sem confirmação desde a R30.** Ela existia porque o
                      gesto apagava o "continuar de onde parou" de alguém — a
                      regra do §22, aplicada certo. Rebobinar agora mexe na
                      fita e em ninguém, e pedir confirmação pra um gesto que
                      não destrói nada é a ceninha que ensina a clicar em "sim"
                      sem ler.

                      E ele aparece com a fita na mão mesmo sem dizer onde ela
                      está: rebobinar antes de devolver é a cortesia, e ela não
                      precisa entregar a surpresa do próximo. */}
                  {vhs && (
                    <button
                      className="balcao-btn rebobinar"
                      disabled={ocupado || rebobinando !== null}
                      title="rebobina a fita pro próximo"
                      onClick={() => rebobinar()}
                    >
                      ⟲ rebobinar
                    </button>
                  )}
                  <button
                    className="balcao-btn devolver"
                    disabled={ocupado}
                    onClick={() =>
                      void balcao(async () => {
                        const r = await api.devolverEmprestimo(emprestimo.id);
                        return `devolvida ${COMO_VOLTOU[r.devolvido_como]}${r.atrasada ? " · atrasada" : ""}`;
                      })
                    }
                  >
                    devolver
                  </button>
                </>
              )}

              {emprestimo && !emprestimo.meu && (
                <>
                  <span className="balcao-com-quem">
                    {emprestimo.quem_nome} está com esta {desde(emprestimo.pego_em)}
                  </span>
                  {emprestimo.pedido_em ? (
                    <span className="balcao-prazo">já pedida de volta</span>
                  ) : (
                    <button
                      className="balcao-btn pedir"
                      disabled={ocupado}
                      onClick={() =>
                        void balcao(async () => {
                          const r = await api.pedirDeVolta(emprestimo.id);
                          return `pedido enviado a ${r.pedido_a}`;
                        })
                      }
                    >
                      pedir de volta
                    </button>
                  )}
                </>
              )}
            </div>
          )}

          {/* Chamava-se "devolver à estante" — e passou a mentir no dia em que
              "devolver" virou uma ação de verdade ali do lado. Este só fecha o
              palco; aquele encerra um empréstimo. */}
          <button className="mao-devolver" onClick={onFechar}>
            voltar à estante
          </button>
        </div>
      )}

      {/* **A fita, quando você põe pra tocar** (R30).
          Ela é o momento inteiro da fase: você clicou em assistir e o que
          aparece não é o filme, é o descuido de outra pessoa. O nome está
          escrito porque é o nome que faz o atrito existir — uma fita no meio
          sem dono é defeito do sistema; com dono é alguém que não rebobinou. */}
      {fase === "fita" && fita && (
        <div className="fita-achada">
          <div className="fita-quadro">
            <p className="fita-selo">esta fita não está rebobinada</p>

            {/* A fita desenhada como fita: o carretel cheio de um lado, e o
                quanto já rodou. É a mesma informação do ponteiro, dita pelo
                objeto — e é ela que se move durante o rebobinar. */}
            <div className="fita-carretel">
              <span
                className="fita-rodada"
                style={{
                  width: fita.duracao_segundos
                    ? `${Math.min(100, ((rebobinando ?? posicao) / fita.duracao_segundos) * 100)}%`
                    : "50%",
                }}
              />
            </div>

            <p className={`fita-ponteiro${rebobinando !== null ? " rodando" : ""}`}>
              {ponteiro(rebobinando ?? posicao)}
            </p>

            {fita.deixada_por && (
              <p className="fita-quem">
                <b>{fita.deixada_por}</b> deixou assim
                {fita.deixada_em && <i> · {desde(fita.deixada_em)}</i>}
              </p>
            )}

            {/* Obrigatório. Não há "dar play daqui" — a fita de outra pessoa se
                rebobina antes, e os segundos que isso custa são o preço que o
                descuido dela cobra de você. É o atrito sendo a ideia. */}
            <button
              className="fita-btn"
              disabled={rebobinando !== null || ocupado}
              onClick={() => rebobinar(rodarFilme)}
            >
              {rebobinando !== null ? "rebobinando…" : "⟲ rebobinar"}
            </button>

            <button className="fita-desistir" onClick={() => setFase("midia")}>
              deixa pra depois
            </button>
          </div>
        </div>
      )}
    </div>
  );
}

/// O disco: duas faces, porque um DVD tem duas caras e as duas dizem coisas
/// diferentes — o lado impresso e o lado de dados. Girar mostra os dois.
function Disco({ caixa }: { caixa: Caixa }) {
  return (
    <>
      <span className="lado impresso">
        <img src={api.artworkUrl(caixa.poster)} alt="" draggable={false} />
        <span className="verniz" />
        <span className="lustro" />
        <span className="anel-claro" />
        <span className="anel-empilha" />
        <span className="furo" />
        <Play />
      </span>
      <span className="lado dados">
        <span className="iris" />
        <span className="trilhas" />
        <span className="lustro" />
        <span className="anel-claro" />
        <span className="anel-empilha" />
        <span className="furo" />
        <Play />
      </span>
      <span className="borda-disco" />
    </>
  );
}

/// R45 — o rebobinar como objeto.
///
/// ## O que havia
///
/// Um anel girando ao contrário e um número descendo. O §46 já tinha anotado a
/// dívida com todas as letras: *"falta o objeto girando, o ruído e o tranco no
/// fim"*.
///
/// ## O que ele é agora
///
/// A janela de um VHS, com os dois carretéis — os mesmos que a caixa já
/// desenhava na estante, na mesma linguagem. E três coisas que o anel não
/// tinha:
///
/// | | e por que importa |
/// |---|---|
/// | **os dois giram, em sentidos opostos** | é o que os carretéis de uma fita fazem: um entrega, o outro recolhe |
/// | **a velocidade cai com o que falta** | a fita sai rápido e vai perdendo força — é o dado do ponteiro virando movimento |
/// | **o rolo da esquerda engorda** | a fita voltando pro lugar, que é literalmente o que "rebobinar" quer dizer |
///
/// O giro não vem de `@keyframes`: ele é escrito como propriedade CSS a cada
/// quadro, porque uma animação de velocidade constante não sabe desacelerar
/// junto com um número que vem do banco.
const Rebobinando = forwardRef<HTMLDivElement, { ponteiro: string }>(function Rebobinando(
  { ponteiro },
  ref,
) {
  return (
    <div className="rebobinando" ref={ref}>
      <div className="rebo-fita">
        <span className="rebo-janela">
          <span className="rebo-carretel a">
            <span className="rebo-rolo" />
            <span className="rebo-dentes" />
          </span>
          <span className="rebo-carretel b">
            <span className="rebo-rolo" />
            <span className="rebo-dentes" />
          </span>
          <span className="rebo-vidro" />
        </span>
      </div>
      <b>{ponteiro}</b>
      <i>rebobinando…</i>
    </div>
  );
});

/// A fita: uma caixa de verdade, seis faces. A espessura é 13% da largura,
/// que é a proporção de um VHS (187 × 103 × 25 mm) — sem ela o objeto vira
/// um retângulo e o giro não convence.
function Fita({ caixa }: { caixa: Caixa }) {
  return (
    <>
      <span className="lado frente-fita">
        <span className="janela">
          <span className="carretel">
            <span className="rolo" />
            <span className="dentes" />
          </span>
          <span className="carretel cheio">
            <span className="rolo" />
            <span className="dentes" />
          </span>
          <span className="vidro" />
        </span>
        <span className="rotulo">
          <b>{caixa.titulo}</b>
          <i>{[caixa.ano, "VHS"].filter(Boolean).join(" · ")}</i>
        </span>
        <span className="parafuso pa" />
        <span className="parafuso pb" />
        <span className="parafuso pc" />
        <span className="parafuso pd" />
        <Play />
      </span>
      <span className="lado tras-fita">
        <span className="hub-tras" />
        <span className="hub-tras b" />
        <span className="marca">VHS</span>
        <span className="trava" />
        <Play />
      </span>
      <span className="lado borda cima" />
      <span className="lado borda baixo tampa-fita" />
      <span className="lado borda esq" />
      <span className="lado borda dir" />
    </>
  );
}

/// O alvo do play, no centro. Vai em cada face visível, com
/// `backface-visibility` cuidando pra que só apareça o da cara que encara.
function Play() {
  return (
    <span className="tocar" role="button" title="começar">
      <span className="tocar-anel" />
      <span className="tocar-seta">▸</span>
    </span>
  );
}

/// O código de barras: barras de largura variável derivadas do uuid.
///
/// É enfeite, e é enfeite honesto — nasce do id da obra, então a mesma caixa
/// tem sempre o mesmo código, como teria numa loja.
function CodigoDeBarras({ semente }: { semente: string }) {
  const barras = useCallback(() => {
    const limpo = semente.replace(/-/g, "");
    return Array.from({ length: 34 }, (_, i) => (parseInt(limpo[i % limpo.length], 16) % 4) + 1);
  }, [semente])();

  return (
    <div className="barras" aria-hidden="true">
      {barras.map((w, i) => (
        <i key={i} style={{ width: `${w}px`, opacity: i % 2 ? 0.15 : 1 }} />
      ))}
    </div>
  );
}
