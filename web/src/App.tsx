import { useCallback, useEffect, useLayoutEffect, useRef, useState, type CSSProperties } from "react";
import { useLocation, useMatch, useNavigate } from "react-router-dom";
import AoVivo, { AvisoDePrograma } from "./AoVivo";
import Collections from "./Collections";
import Details, { paraLista } from "./Details";
import Admin from "./Admin";
import Avatar from "./Avatar";
import { useArrastoDeFileira } from "./arrasto";
import Gerenciar from "./Gerenciar";
import Guia from "./Guia";
import Locadora from "./Locadora";
import Mural from "./Mural";
import Perfil from "./Perfil";
import ForYou from "./ForYou";
import Libraries from "./Libraries";
import Login from "./Login";
import FilterBar from "./FilterBar";
import Junto from "./Junto";
import Player from "./Player";
import Review from "./Review";
import Scopes from "./Scopes";
import Servidor from "./Servidor";
import {
  api,
  API,
  auth, midia,
  mixedContentProblem,
  DEVICE_ID,
  Unauthorized,
  type AuthUser,
  formatDuration,
  formatSize,
  hueFromTitle,
  PAGE_SIZE,
  PERFIL_MUDOU,
  WORK_KINDS,
  type LibraryEntry,
  type Filters,
  type MatchStatus,
  type Sala,
  type ScanStatus,
  type ScrubStatus,
  type WorkListItem,
} from "./api";

/// As abas, depois da R36.
///
/// **"Experimentação" acabou.** Ela era um estacionamento: locadora, wiki,
/// retrospectiva e perfil moravam lá dentro porque estavam sendo construídas.
/// Estão prontas — e uma feature pronta escondida atrás de uma palavra que não
/// descreve nada é uma feature que ninguém acha.
///
/// A barra passou a ter dois lados, e a divisão é o que o §12 já tinha decidido
/// pras operações de servidor: **navegação de um lado, ferramenta do outro**.
/// À esquerda o que é acervo; à direita o que é você e o que é manutenção.
type Tab =
  | "foryou"
  | "library"
  | "collections"
  | "locadora"
  | "guia"
  | "live"
  | "mural"
  | "perfil"
  | "review"
  | "settings"
  | "admin";

/// O endereço de cada tela (R43).
///
/// Em português, como todo o resto do projeto — as rotas da API já são
/// `/api/locadora/prateleira` e `/api/guia/revista`, e um `/library` no meio
/// disso seria a única palavra em inglês que alguém lê em voz alta.
///
/// A raiz é o "para você" porque é onde se entra: quem abre o Odeon sem
/// endereço nenhum cai na tela que responde *"o que eu assisto agora"*.
const CAMINHO_DE: Record<Tab, string> = {
  foryou: "/",
  library: "/biblioteca",
  collections: "/colecoes",
  locadora: "/locadora",
  guia: "/guia",
  live: "/ao-vivo",
  mural: "/mural",
  perfil: "/perfil",
  review: "/revisao",
  settings: "/pastas",
  admin: "/admin",
};

const ABA_DE: Record<string, Tab> = Object.fromEntries(
  Object.entries(CAMINHO_DE).map(([aba, caminho]) => [caminho, aba as Tab]),
);

/// O que fica na barra, à esquerda. **Sete**, e a ordem é de "onde você entra"
/// pra "onde você vai depois".
const ABAS: { chave: Tab; rotulo: string }[] = [
  { chave: "foryou", rotulo: "para você" },
  { chave: "library", rotulo: "biblioteca" },
  { chave: "collections", rotulo: "coleções" },
  { chave: "locadora", rotulo: "locadora" },
  { chave: "guia", rotulo: "guia" },
  { chave: "live", rotulo: "ao vivo" },
  { chave: "mural", rotulo: "mural" },
];

/// R36 — a barra de cima.
///
/// ## O que ela era
///
/// Nove entradas em fileira, mais quatro salas escondidas dentro de uma delas
/// chamada **"experimentação"** — uma palavra que não descreve nada e que
/// existia porque a locadora, o guia, a retrospectiva e o perfil estavam sendo
/// construídos. Estão prontos.
///
/// E as nove entradas misturavam três coisas diferentes na mesma fileira, com o
/// mesmo peso: **acervo** (biblioteca, coleções), **produto** (locadora, mural)
/// e **manutenção** (revisão, pastas, admin). É o mesmo defeito que o §12
/// corrigiu quando tirou as operações de servidor daqui — *"misturadas, elas
/// competiam com as abas, e a mais gritante da tela era `identificar`"* —, só
/// que meia dúzia de fases depois ele tinha voltado por outro caminho.
///
/// ## O que ela é
///
/// Dois lados. À esquerda **sete** entradas, todas do mesmo tipo: lugares do
/// acervo. À direita o que não é acervo — a manutenção atrás de uma engrenagem,
/// e você atrás do seu próprio nome.
///
/// ## Os efeitos, e o que cada um faz
///
/// Nenhum é enfeite solto; cada um responde uma pergunta que a barra antiga
/// deixava a tela responder sozinha:
///
/// | efeito | o que ele diz |
/// |---|---|
/// | o traço que **desliza** entre as abas | de onde você veio, não só onde está |
/// | o **holofote** que segue o mouse | onde o dedo está, numa fileira de sete alvos pequenos |
/// | a barra que **condensa** ao rolar | você saiu do topo; o conteúdo é que importa agora |
/// | o **anel** em volta do seu nome | quanto falta pro próximo nível, sem abrir o perfil |
/// | a marca que **pulsa** | tem trabalho rodando no servidor |
///
/// Tudo isso desliga em `prefers-reduced-motion`. Alma não pode custar enjoo.
function BarraDeCima({
  aba,
  aoTrocar,
  eu,
  isAdmin,
  paraRevisar,
  trabalhando,
  aoAbrirServidor,
  aoSair,
  busca,
}: {
  aba: Tab;
  aoTrocar: (t: Tab) => void;
  eu: AuthUser;
  isAdmin: boolean;
  paraRevisar: number;
  trabalhando: boolean;
  aoAbrirServidor: () => void;
  aoSair: () => void;
  busca: React.ReactNode;
}) {
  const fileira = useRef<HTMLElement | null>(null);
  const arrastarFileira = useArrastoDeFileira();
  /// Dois `ref` no mesmo elemento: o de medir o traço, que já existia, e o de
  /// arrastar (R48).
  ///
  /// **E ele precisa ser estável.** Um `ref` escrito na marca no JSX é uma
  /// função nova a cada render, e o React desliga e religa o anterior toda vez —
  /// no meio de um gesto isso apagaria o `pointerdown` que estava em curso, e a
  /// fileira pararia de responder sem erro nenhum aparecer.
  const abas = useCallback(
    (el: HTMLElement | null) => {
      fileira.current = el;
      const soltar = arrastarFileira(el);
      return () => {
        fileira.current = null;
        soltar?.();
      };
    },
    [arrastarFileira],
  );
  const [traco, setTraco] = useState<{ x: number; w: number } | null>(null);
  const [condensada, setCondensada] = useState(false);
  const [menu, setMenu] = useState<"nenhum" | "eu" | "manutencao">("nenhum");
  /// R47 — o rosto, o nível e a moldura saem da **mesma** resposta do perfil,
  /// e por isso moram no mesmo estado: pedir três vezes o que vem numa vez só
  /// seria três chances de a barra mostrar um estado que não existe.
  const [insignia, setInsignia] = useState<{
    n: number;
    fatia: number;
    /// O rosto escolhido (R43). `null` cai na marca derivada do nome (R42) —
    /// que é o padrão de quem não escolheu, e não um buraco no cabeçalho.
    rosto: string | null;
    /// A cor da moldura, já em hex. `null` mantém o âmbar da casa.
    cor: string | null;
  } | null>(null);

  /// O traço que desliza.
  ///
  /// Medido do DOM e não calculado de larguras fixas: os rótulos têm tamanhos
  /// diferentes e a fonte é do sistema, então a única fonte de verdade sobre
  /// onde a aba está é a própria aba. `ResizeObserver` porque a barra encolhe
  /// quando a janela encolhe, e um traço que fica pra trás lê como defeito.
  useLayoutEffect(() => {
    const medir = () => {
      const el = fileira.current?.querySelector<HTMLElement>(".aba.on");
      if (!el || !fileira.current) return setTraco(null);
      const p = fileira.current.getBoundingClientRect();
      const r = el.getBoundingClientRect();
      setTraco({ x: r.left - p.left, w: r.width });
    };
    medir();
    const ro = new ResizeObserver(medir);
    if (fileira.current) ro.observe(fileira.current);
    window.addEventListener("resize", medir);
    return () => {
      ro.disconnect();
      window.removeEventListener("resize", medir);
    };
  }, [aba, isAdmin]);

  /// Condensa ao rolar. O limiar tem histerese — 24px pra condensar, 8px pra
  /// voltar — porque sem ela a barra pisca quando a rolagem para em cima do
  /// número.
  useEffect(() => {
    const aoRolar = () => {
      const y = window.scrollY;
      setCondensada((c) => (c ? y > 8 : y > 24));
    };
    aoRolar();
    window.addEventListener("scroll", aoRolar, { passive: true });
    return () => window.removeEventListener("scroll", aoRolar);
  }, []);

  /// A insígnia. Uma requisição, na montagem: o número muda devagar e a barra
  /// não é lugar de ficar perguntando.
  const lerInsignia = useCallback(() => {
    api
      .perfil()
      .then((p) => {
        const g = p.progresso;
        const faixa = Math.max(1, g.xp_do_proximo - g.xp_do_nivel);
        setInsignia({
          n: g.nivel,
          fatia: Math.min(1, (g.xp - g.xp_do_nivel) / faixa),
          rosto: p.avatar?.arte ?? null,
          cor: p.moldura,
        });
      })
      .catch(() => {});
  }, []);

  /// Ela também é relida quando você troca de rosto em `/perfil` — o `PUT` é
  /// numa tela e o efeito é noutra, e sem este aviso o cabeçalho ficaria com a
  /// cara velha até um F5.
  useEffect(() => {
    lerInsignia();
    window.addEventListener(PERFIL_MUDOU, lerInsignia);
    return () => window.removeEventListener(PERFIL_MUDOU, lerInsignia);
  }, [lerInsignia]);

  /// Fechar o menu ao clicar fora e no Escape. Sem isso ele fica aberto atrás
  /// da tela seguinte, que é o defeito que todo menu tem uma vez.
  useEffect(() => {
    if (menu === "nenhum") return;
    const fora = (e: MouseEvent) => {
      if (!(e.target as Element).closest(".gaveta, .gaveta-abre")) setMenu("nenhum");
    };
    const esc = (e: KeyboardEvent) => e.key === "Escape" && setMenu("nenhum");
    window.addEventListener("mousedown", fora);
    window.addEventListener("keydown", esc);
    return () => {
      window.removeEventListener("mousedown", fora);
      window.removeEventListener("keydown", esc);
    };
  }, [menu]);

  /// O holofote: um brilho que segue o mouse pela barra. Duas variáveis CSS, e
  /// o resto é um `radial-gradient` — nada de elemento extra pra posicionar.
  const holofote = (e: React.MouseEvent<HTMLElement>) => {
    const r = e.currentTarget.getBoundingClientRect();
    e.currentTarget.style.setProperty("--hx", `${e.clientX - r.left}px`);
    e.currentTarget.style.setProperty("--hy", `${e.clientY - r.top}px`);
  };

  const ir = (t: Tab) => {
    setMenu("nenhum");
    aoTrocar(t);
  };

  return (
    <header
      className={`topbar${condensada ? " condensada" : ""}`}
      onMouseMove={holofote}
    >
      <button className="brand" onClick={() => ir("foryou")} title="para você">
        {/* A marca pulsa quando há trabalho rodando no servidor. É o único
            lugar da tela que diz isso sem ocupar espaço — a barra de varredura
            aparece embaixo, mas só quando alguém está olhando pra lá. */}
        <span className={`brand-mark${trabalhando ? " trabalhando" : ""}`}>◉</span>
        <span className="brand-name">ODEON</span>
      </button>


      {/* As abas também são uma lista horizontal — só que só rolam em janela
          estreita, onde elas viram uma linha inteira (o `@media` de 900px). O
          gancho fica inerte enquanto couberem, e por isso não precisa de
          condição aqui. */}
      <nav className="abas" ref={abas}>
        {ABAS.map(({ chave, rotulo }) => (
          <button
            key={chave}
            className={`aba${aba === chave ? " on" : ""}`}
            onClick={() => ir(chave)}
          >
            {rotulo}
          </button>
        ))}
        {/* O traço, posicionado. Ele existe **fora** dos botões de propósito:
            uma borda por botão não desliza de um pro outro — ela aparece num e
            some do outro, que é o que a barra fazia antes. */}
        {traco && (
          <span
            className="aba-traco"
            style={{ transform: `translateX(${traco.x}px)`, width: `${traco.w}px` }}
          />
        )}
      </nav>

      <div className="barra-fim">
        {busca}

        {/* A MANUTENÇÃO, atrás de uma engrenagem. Ela saiu da fileira porque
            não é um lugar do acervo — e o contador continua visível por fora,
            porque "3.238 esperando revisão" é a única coisa daqui que pede
            alguma coisa de alguém. */}
        <div className="gaveta-caixa">
          <button
            /* Aceso também quando você **está** numa das telas de dentro: sem
               isso, ir pra "revisão" apaga a barra inteira — nenhuma aba fica
               marcada e o traço some, que lê como defeito. */
            className={`gaveta-abre${
              menu === "manutencao" || ["review", "settings", "admin"].includes(aba) ? " on" : ""
            }`}
            onClick={() => setMenu((m) => (m === "manutencao" ? "nenhum" : "manutencao"))}
            title="manutenção"
          >
            {/* Desenhado, e não o `⚙` do sistema: aquele é emoji, e emoji vem
                colorido — um ícone azul e vermelho no meio de uma barra âmbar e
                cinza.

                E são controles, não uma engrenagem: a primeira tentativa foi um
                gear de quatro dentes, e a 16px ele lia como estrela. Três
                trilhos com um botão cada dizem "ajustes" em qualquer tamanho —
                e os botões deslizam no hover, que é o gesto do próprio ícone. */}
            <svg className="controles" viewBox="0 0 20 20" aria-hidden="true">
              {[4.5, 10, 15.5].map((y, i) => (
                <g key={y}>
                  <rect x="2" y={y - 0.7} width="16" height="1.4" rx="0.7" opacity="0.45" />
                  <circle className={`knob k${i}`} cx={i === 1 ? 13 : 7} cy={y} r="2.4" />
                </g>
              ))}
            </svg>
            {paraRevisar > 0 && <span className="pill">{paraRevisar}</span>}
          </button>
          {menu === "manutencao" && (
            <div className="gaveta">
              <button onClick={() => ir("review")}>
                revisão
                {paraRevisar > 0 && <i>{paraRevisar}</i>}
              </button>
              {isAdmin && <button onClick={() => ir("settings")}>pastas</button>}
              {isAdmin && <button onClick={() => ir("admin")}>admin</button>}
              {isAdmin && (
                <button
                  onClick={() => {
                    setMenu("nenhum");
                    aoAbrirServidor();
                  }}
                >
                  servidor…
                </button>
              )}
            </div>
          )}
        </div>

        {/* VOCÊ. Três coisas no mesmo lugar de 38px: o rosto no miolo, o arco
            contando quanto falta pro próximo nível, e o número num selo — a
            fase 5 na barra, sem ocupar uma entrada. */}
        <div className="gaveta-caixa">
          <button
            className={`gaveta-abre eu${menu === "eu" || aba === "perfil" ? " on" : ""}`}
            onClick={() => setMenu((m) => (m === "eu" ? "nenhum" : "eu"))}
            title={`${isAdmin ? "administrador" : "morador"} · ${API}`}
          >
            <span
              className="anel"
              style={{
                ["--fatia" as string]: `${(insignia?.fatia ?? 0) * 360}deg`,
                /* A moldura escolhida (R43) tinge o arco e o selo. Ela já tinge
                   o perfil inteiro; aqui a escolha passa a valer em toda tela,
                   que é o que faz escolher uma cor significar alguma coisa. */
                ...(insignia?.cor ? { ["--anel-cor" as string]: insignia.cor } : {}),
              }}
            >
              <Avatar nome={eu.display_name} arte={insignia?.rosto} tamanho={33} />
              <b>{insignia?.n ?? "·"}</b>
            </span>
            <span className="eu-nome">{eu.display_name}</span>
            {API.startsWith("https://") && <span className="lock">🔒</span>}
          </button>
          {menu === "eu" && (
            <div className="gaveta">
              <button onClick={() => ir("perfil")}>perfil</button>
              <button
                onClick={() => {
                  setMenu("nenhum");
                  void aoSair();
                }}
              >
                sair
              </button>
            </div>
          )}
        </div>
      </div>
    </header>
  );
}

export default function App() {
  const [me, setMe] = useState<AuthUser | null>(null);
  const [checking, setChecking] = useState(true);

  /// R43 — **a aba é o endereço**, e não mais um `useState`.
  ///
  /// A troca é pequena de propósito: o corpo continua desenhando por `tab`, e o
  /// que mudou é de onde `tab` vem. Reescrever as onze telas como `<Routes>`
  /// aninhadas seria uma reforma num arquivo de mil linhas pra chegar no mesmo
  /// lugar — e o que foi pedido é que cada tela tenha endereço, não que este
  /// componente mude de forma.
  const location = useLocation();
  const navigate = useNavigate();
  /// `/p/<quem>` — o perfil de alguém, por nome de usuário ou por id.
  const noPerfilDeAlguem = useMatch("/p/:quem");
  const tab: Tab = noPerfilDeAlguem
    ? "perfil"
    : (ABA_DE[location.pathname] ?? "foryou");
  const perfilDe = noPerfilDeAlguem?.params.quem;
  const setTab = useCallback(
    (t: Tab) => navigate(CAMINHO_DE[t]),
    [navigate],
  );
  const [works, setWorks] = useState<WorkListItem[]>([]);
  /// A biblioteca agrupada. `works` continua existindo pro modo plano —
  /// dentro de uma série o que se quer é a lista de episódios.
  const [entries, setEntries] = useState<LibraryEntry[]>([]);
  const [total, setTotal] = useState(0);
  const [carregandoMais, setCarregandoMais] = useState(false);
  const [resume, setResume] = useState<WorkListItem[]>([]);
  const [filters, setFilters] = useState<Filters>({ sort: "featured" });
  const [scan, setScan] = useState<ScanStatus | null>(null);
  const [match, setMatch] = useState<MatchStatus | null>(null);
  const [scrub, setScrub] = useState<ScrubStatus | null>(null);
  const [playing, setPlaying] = useState<WorkListItem | null>(null);
  /// R46 — a sala de assistir junto, quando há uma.
  ///
  /// Mora no App e não no player porque ela **sobrevive ao player**: quem
  /// fecha o vídeo continua na sala, e quem entra numa sala pelo convite
  /// precisa que o vídeo abra sozinho.
  const [sala, setSala] = useState<Sala | null>(null);
  const [detailsOf, setDetailsOf] = useState<string | null>(null);
  const [managing, setManaging] = useState<string | null>(null);
  const [serverOpen, setServerOpen] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  /// A sala, e o barramento que a mantém viva.
  ///
  /// O evento **não carrega estado** — ele diz qual sala mexeu, e a tela relê
  /// (§46). É o que faz quem entra atrasado chegar no ponto certo em vez de
  /// depender de ter ouvido o evento anterior.
  const recarregarSala = useCallback(() => {
    api.junto().then(setSala).catch(() => {});
  }, []);

  /// **Entrar numa sala abre o filme.**
  ///
  /// Sem isto, aceitar o convite deixava a pessoa numa sala invisível: o
  /// estado existia, o vídeo não. E o filme é o motivo da sala — é o §8b outra
  /// vez, um clique que parece não fazer nada.
  ///
  /// A obra é buscada inteira, e não montada do que a sala manda: o player
  /// precisa da **duração da obra** pra barra não crescer sozinha (R39), e
  /// improvisar um item de lista aqui repetiria aquele defeito por outro
  /// caminho.
  useEffect(() => {
    if (!sala?.media_file_id || playing) return;
    let vivo = true;
    api
      .detail(sala.work_id)
      .then((w) => {
        if (!vivo) return;
        const arquivo = w.files.find((f) => f.id === sala.media_file_id) ?? w.files[0];
        setPlaying(paraLista(w, arquivo, null));
      })
      .catch(() => {});
    return () => {
      vivo = false;
    };
  }, [sala?.id, sala?.media_file_id, sala?.work_id, playing, sala]);

  useEffect(() => {
    if (!me) return;
    recarregarSala();
    return api.ouvirEventos((e) => {
      if (e.type !== "junto") return;
      if (e.o_que === "fim") return void api.junto().then(setSala).catch(() => setSala(null));
      recarregarSala();
    });
  }, [me, recarregarSala]);

  /// Dar play. **R56: sem funil, porque não há mais o que afunilar.**
  ///
  /// A R50 punha aqui um `conferirLiberada` que reperguntava ao servidor antes
  /// de abrir o vídeo e, num "não", desviava pra locadora. Era a garantia de que
  /// nenhum dos nove botões abriria um player sem bytes.
  ///
  /// A R56 desfez a regra que ele guardava: **a biblioteca é modo livre**. Um
  /// morador toca o que quiser, e o servidor não nega mais — então uma pergunta
  /// que só pode ser respondida com "sim" é uma ida à rede antes de todo play,
  /// pra nada.
  ///
  /// O que sobrou de "não" é do `guest`, e ele nunca passou por aqui: a
  /// biblioteca de convidado já é filtrada antes, e o 403 do servidor continua
  /// de pé pra ele.
  ///
  /// A locadora não perdeu nada — ela nunca usou este caminho. Os botões dela
  /// decidem com `comigo`, que é o estado da caixa na sua mão.
  const tocar = useCallback((w: WorkListItem) => setPlaying(w), []);

  useEffect(() => {
    if (!auth.token()) {
      setChecking(false);
      return;
    }
    api
      .me()
      .then((u) => {
        setMe(u);
        // R27: o token de mídia é curto (8h) e separado do de sessão, então
        // ele é renovado a cada boot. Sem `await`: a arte carrega quando ele
        // chegar, e a API não depende dele pra nada.
        void midia.renovar();
      })
      .catch(() => auth.clear())
      .finally(() => setChecking(false));
  }, []);

  /// Dentro de uma coleção a lista é plana (os episódios daquela série); fora
  /// dela é agrupada (uma entrada por série). São duas perguntas diferentes, e
  /// por isso duas rotas.
  const refresh = useCallback(async (f: Filters) => {
    try {
      const pendente = api.continueWatching();
      if (f.collection) {
        const list = await api.works(f);
        setWorks(list);
        setEntries([]);
        setTotal(list.length);
      } else {
        const list = await api.library(f);
        setEntries(list);
        setWorks([]);
        setTotal(list[0]?.total ?? 0);
      }
      setResume(await pendente);
      setError(null);
    } catch (e) {
      if (e instanceof Unauthorized) {
        setMe(null);
        return;
      }
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setLoading(false);
    }
  }, []);

  const filtersRef = useRef(filters);
  filtersRef.current = filters;

  useEffect(() => {
    const t = setTimeout(() => refresh(filters), 250);
    return () => clearTimeout(t);
  }, [filters, refresh]);

  useEffect(() => {
    api.matchStatus().then(setMatch).catch(() => {});
    api.scrubStatus().then(setScrub).catch(() => {});
  }, []);

  // Sync ao vivo. Sem isto, dois aparelhos só se falam quando um recarrega.
  useEffect(() => {
    return api.ouvirEventos((event) => {
      if (event.type === "progress" && event.device_id === DEVICE_ID) return;
      refresh(filtersRef.current);
      if (event.type === "match_finished") api.matchStatus().then(setMatch).catch(() => {});
      if (event.type === "scrub_finished") api.scrubStatus().then(setScrub).catch(() => {});
    });
  }, [refresh]);

  useEffect(() => {
    if (!scan?.running) return;
    const t = setInterval(async () => {
      const next = await api.scanStatus();
      setScan(next);
      if (!next.running) refresh(filters);
    }, 1000);
    return () => clearInterval(t);
  }, [scan?.running, filters, refresh]);

  useEffect(() => {
    if (!match?.running) return;
    const t = setInterval(async () => {
      const next = await api.matchStatus();
      setMatch(next);
      if (!next.running) refresh(filters);
    }, 1500);
    return () => clearInterval(t);
  }, [match?.running, filters, refresh]);

  /// Paginação de verdade. O backend sempre soube responder `offset`; a tela é
  /// que pedia 300 fixos e chamava aquilo de "a biblioteca".
  const carregarMais = async () => {
    setCarregandoMais(true);
    try {
      if (filters.collection) {
        setWorks([...works, ...(await api.works(filters, works.length))]);
      } else {
        setEntries([...entries, ...(await api.library(filters, entries.length))]);
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setCarregandoMais(false);
    }
  };

  const startScan = async () => {
    await api.scan();
    setScan(await api.scanStatus());
  };

  const startMatch = async () => {
    await api.match(false);
    setMatch(await api.matchStatus());
  };

  if (checking) return null;
  if (!me)
    return (
      <Login
        onAuthenticated={(u) => {
          setMe(u);
          void midia.renovar();
        }}
      />
    );

  const isAdmin = me.role === "admin";
  // Quantas estão na tela agora. Dentro de coleção a lista é plana.
  const mostrando = filters.collection ? works.length : entries.length;

  return (
    <div className="app">
      <BarraDeCima
        aba={tab}
        // Ir pro perfil PELA BARRA é sempre ir pro seu (R42) — e agora isso
        // sai de graça, porque `/perfil` e `/p/rudney` são endereços
        // diferentes.
        aoTrocar={setTab}
        eu={me}
        isAdmin={isAdmin}
        paraRevisar={match?.needs_review ?? 0}
        trabalhando={!!scan?.running || !!match?.running}
        aoAbrirServidor={() => setServerOpen(true)}
        aoSair={async () => {
          await api.logout().catch(() => {});
          auth.clear();
          setMe(null);
        }}
        busca={
          tab === "library" ? (
            <input
              className="search"
              placeholder="buscar na biblioteca…"
              value={filters.q ?? ""}
              onChange={(e) => setFilters({ ...filters, q: e.target.value })}
            />
          ) : null
        }
      />

      {serverOpen && isAdmin && (
        <Servidor
          onClose={() => setServerOpen(false)}
          scan={scan}
          match={match}
          scrub={scrub}
          onScan={startScan}
          onMatch={startMatch}
          onScrubChanged={setScrub}
        />
      )}

      {match && !match.tmdb_enabled && (
        <div className="notice">
          Sem <code>TMDB_API_KEY</code> — filmes e séries não são identificados. Pegue uma chave
          grátis em <code>themoviedb.org/settings/api</code>, coloque no <code>.env</code> e rode{" "}
          <code>docker compose up -d api</code>. Anime funciona sem chave (AniList).
        </div>
      )}

      {scan?.running && (
        <div className="scanbar">
          <strong>{scan.files_seen}</strong> vistos · <strong>{scan.files_added}</strong> novos
          <span className="scanfile">{scan.current_file}</span>
        </div>
      )}

      {match?.running && (
        <div className="scanbar">
          {/* R55 — o DENOMINADOR era o que faltava aqui. A faixa dizia "63
              obras" e a pessoa não tinha como saber se eram 63 de 70 ou de
              5.000 — foi o que fez o contador parado em 713 parecer errado em
              vez de inacabado. */}
          <strong>{match.works_seen}</strong>
          {match.nao_identificadas > 0 && (
            <span className="muted"> de {match.works_seen + match.nao_identificadas}</span>
          )}{" "}
          obras · <strong>{match.matched_auto}</strong> casadas ·{" "}
          <strong>{match.needs_review}</strong> pra revisar
          <span className="scanfile">{match.current}</span>
        </div>
      )}

      {mixedContentProblem() && (
        <div className="error banner">{mixedContentProblem()}</div>
      )}

      {error && <div className="error banner">{error}</div>}

      {/* Fora do `main`: o aviso de programa agendado tem que aparecer em
          qualquer aba, senão agendar não serviria pra nada. */}
      <AvisoDePrograma userId={me.id} />

      {/* R36: a troca de aba não é um corte seco.

          `key={tab}` remonta o conteúdo, e a animação de entrada dá a ele um
          instante pra chegar — sem isso, ir de "biblioteca" (600 capas) pra
          "mural" (três linhas) é um piscar que o olho lê como falha de
          carregamento. Sobe 8px e clareia; 0,28s, que é curto o bastante pra
          não atrasar quem já sabe pra onde vai. */}
      <main key={tab} className="troca">
        {tab === "settings" && isAdmin && (
          <Libraries onChanged={() => refresh(filters)} />
        )}

        {tab === "foryou" && <ForYou onPlay={tocar} />}

        {tab === "review" && (
          <RevisaoTabs onChanged={() => refresh(filters)} />
        )}

        {tab === "locadora" && (
          <Locadora
            onPlay={tocar}
            onAbrirColecao={(id, titulo) => {
              setTab("library");
              setFilters({ collection: id, collectionName: titulo });
            }}
          />
        )}

        {tab === "guia" && (
          <Guia
            onDetails={setDetailsOf}
            onExplorar={(f) => {
              setTab("library");
              setFilters(f);
            }}
          />
        )}

        {/* A `key` troca o componente quando a pessoa olhada muda: `Perfil`
            guarda quem está olhando em estado próprio, semeado pela prop, e sem
            isto o segundo clique num amigo diferente não mudaria nada. */}
        {tab === "perfil" && <Perfil key={perfilDe ?? "eu"} quem={perfilDe} />}
        {tab === "admin" && isAdmin && <Admin eu={me?.username ?? ""} />}
        {tab === "mural" && <Mural aoVerPerfil={(quem) => navigate(`/p/${quem}`)} />}
        {tab === "collections" && <Collections onPlay={tocar} />}

        {tab === "live" && <AoVivo isAdmin={isAdmin} onDetails={setDetailsOf} />}

        {tab === "library" && (
          <>
            {/* Entrou numa série pelo cartão. Reaproveita o filtro de coleção,
                que o backend já resolve pela subárvore inteira (§8c). */}
            {filters.collection && (
              <div className="person-filter">
                <span className="filter-label">Dentro de</span>
                <button
                  className="chip on"
                  onClick={() =>
                    setFilters({ ...filters, collection: undefined, collectionName: undefined })
                  }
                  title="voltar pra biblioteca"
                >
                  {filters.collectionName ?? "esta coleção"} ✕
                </button>
              </div>
            )}

            {filters.person && (
              <div className="person-filter">
                <span className="filter-label">Com</span>
                <button
                  className="chip on"
                  onClick={() => setFilters({ ...filters, person: undefined, personName: undefined })}
                >
                  {filters.personName ?? "esta pessoa"} ✕
                </button>
              </div>
            )}

            <FilterBar
              filters={filters}
              onChange={setFilters}
              /* R55 — o que falta pros contadores contarem tudo. Vem do status
                 da identificação, que esta tela já pergunta. */
              naoIdentificadas={match?.nao_identificadas ?? 0}
              aoAbrirRevisao={() => setTab("review")}
            />

            {resume.length > 0 && (
              <section>
                <h2 className="section-title">Continuar assistindo</h2>
                <div className="grid">
                  {resume.map((w) => (
                    <Card key={w.id} work={w} onDetails={setDetailsOf} onManage={setManaging} />
                  ))}
                </div>
              </section>
            )}

            <section>
              <h2 className="section-title">
                {filters.collection ? "Episódios" : "Biblioteca"}{" "}
                {/* O número diz o que é: quantas estão na tela DE quantas existem.
                    Antes dizia "300" com 17.498 no banco. */}
                <span className="count">
                  {mostrando < total ? `${mostrando} de ${total}` : total}
                </span>
              </h2>

              {loading ? (
                <p className="muted">carregando…</p>
              ) : mostrando === 0 ? (
                <div className="empty">
                  <p>Nada encontrado.</p>
                  <p className="muted">Afrouxe os filtros ou rode uma varredura.</p>
                </div>
              ) : (
                <>
                  {/* Dentro da série, a temporada volta a ser um nível. Uma
                      grade de 77 cartões é uma parede; em Malcolm, de 151. */}
                  {filters.collection ? (
                    porTemporada(works).map((t) => (
                      <section key={t.chave} className="temporada">
                        <div className="strip">
                          <h2>{t.titulo}</h2>
                          <span className="rule" />
                          <span className="strip-meta">
                            {t.itens.length} ep
                            {t.vistos > 0 && ` · ${t.vistos} vistos`}
                          </span>
                        </div>
                        <div className="grid larga">
                          {t.itens.map((w) => (
                            <EpisodeCard
                              key={w.id}
                              work={w}
                              onDetails={setDetailsOf}
                              onManage={setManaging}
                            />
                          ))}
                        </div>
                      </section>
                    ))
                  ) : (
                    <div className="grid">
                      {entries.map((e) => (
                        <EntryCard
                          key={e.id}
                          entry={e}
                          onOpenSeries={(id, title) =>
                            setFilters({ ...filters, collection: id, collectionName: title })
                          }
                          onDetails={setDetailsOf}
                          onManage={setManaging}
                        />
                      ))}
                    </div>
                  )}

                  {mostrando < total && (
                    <div className="mais">
                      <button className="chip" onClick={carregarMais} disabled={carregandoMais}>
                        {carregandoMais ? "carregando…" : `carregar mais ${Math.min(PAGE_SIZE, total - mostrando)}`}
                      </button>
                    </div>
                  )}
                </>
              )}
            </section>
          </>
        )}
      </main>

      {detailsOf && (
        <Details
          workId={detailsOf}
          onClose={() => setDetailsOf(null)}
          onChanged={() => refresh(filters)}
          isAdmin={isAdmin}
          // A ficha era beco sem saída: mostrava a obra e não deixava tocar.
          onPlay={(w) => {
            setDetailsOf(null);
            tocar(w);
          }}
          /// R46 — abrir a sala e cair dentro dela. O convite não é um segundo
          /// gesto: a sala aberta já aparece pros amigos (§4.6), porque a
          /// amizade é o aceite e não há convite a inventar (§44).
          onJunto={(w) => {
            setDetailsOf(null);
            api
              .criarJunto({ work_id: w.id, media_file_id: w.media_file_id })
              .then((s) => {
                setSala(s);
                setPlaying(w);
              })
              .catch((e) => setError(e instanceof Error ? e.message : String(e)));
          }}
          onPickPerson={(id, name) => {
            setDetailsOf(null);
            setTab("library");
            setFilters({ sort: "year", person: id, personName: name });
          }}
        />
      )}

      {managing && (
        <Gerenciar
          workId={managing}
          onClose={() => setManaging(null)}
          onChanged={() => refresh(filters)}
        />
      )}

      {playing && (
        <Player
          work={playing}
          sala={sala && sala.work_id === playing.id ? sala : null}
          aoMudarSala={setSala}
          aoLado={
            sala && sala.work_id === playing.id ? (
              <Junto
                sala={sala}
                aoMudar={setSala}
                aoSair={() => {
                  void api.sairJunto(sala.id).catch(() => {});
                  setSala(null);
                  setPlaying(null);
                }}
              />
            ) : undefined
          }
          onClose={() => {
            setPlaying(null);
            refresh(filters);
          }}
        />
      )}
    </div>
  );
}

/// O cartão de episódio, dentro da série.
///
/// Usa o **`still`** — o quadro daquele episódio — em vez do pôster da série.
/// Com o pôster, uma temporada de 21 episódios eram 21 cópias da mesma imagem:
/// a arte ocupava a tela inteira sem distinguir nada. O `still` está baixado
/// desde o M1 e passou a sair na API na R1; 7.258 dos 14.657 episódios têm um,
/// e nesta série a cobertura é 77 de 77.
///
/// Daí o formato ser 16:9 e não 2:3: os stills são 780×439. Enfiar isso numa
/// moldura de pôster cortaria dois terços do quadro.
function EpisodeCard({
  work,
  onDetails,
  onManage,
}: {
  work: WorkListItem;
  onDetails: (id: string) => void;
  onManage: (id: string) => void;
}) {
  const hue = hueFromTitle(work.title);
  const progress =
    work.position_seconds && work.duration_seconds
      ? Math.min(100, (work.position_seconds / work.duration_seconds) * 100)
      : 0;

  // Sem o quadro do episódio, o pôster da série ainda é melhor que nada — e
  // sem nenhum dos dois sobra o gradiente, que ao menos carrega o título.
  const arte = work.still ?? work.poster;
  const episodio =
    work.episode_number != null ? `E${String(work.episode_number).padStart(2, "0")}` : null;

  return (
    <div
      className="card-wrap"
      style={
        {
          "--hue": hue,
          ...(work.dominant_color ? { "--accent-work": work.dominant_color } : {}),
        } as CSSProperties
      }
    >
      <button
        className="card"
        // Clicar no cartão abre a FICHA, não o player. Começar um filme é
        // decisão, e a decisão precisa da sinopse na frente — o botão de
        // assistir mora no cartaz, a um clique de distância.
        onClick={() => onDetails(work.id)}
      >
        <div className={arte ? "thumb has-art" : "thumb"}>
          {arte ? (
            <img src={api.artworkUrl(arte)} alt="" loading="lazy" />
          ) : (
            <span className="poster-title">{work.title}</span>
          )}
          {episodio && <span className="badge episodio">{episodio}</span>}
          {work.match_state === "unmatched" && <span className="badge">sem metadata</span>}
          {progress > 0 && <div className="progress" style={{ width: `${progress}%` }} />}
        </div>
        <div className="card-body">
          <h3>{work.title}</h3>
          <p className="muted small">
            {[formatDuration(work.duration_seconds), work.height ? `${work.height}p` : null]
              .filter((parte) => parte && parte !== "—")
              .join(" · ")}
          </p>
        </div>
      </button>
      <button className="card-info" title="gerenciar" onClick={() => onManage(work.id)}>
        ⋯
      </button>
    </div>
  );
}

/// Quebra os episódios de uma série em temporadas.
///
/// Agrupa por `work.season_number` e não pela coleção `season`, porque medi que
/// dá no mesmo: das 8.410 obras dentro de uma temporada, **nenhuma** está sem
/// `season_number`, e ele nunca diverge do número no título da coleção. Agrupar
/// pelo campo dispensa uma coluna nova em `WorkListItem` — e cada coluna nova ali
/// custa quatro projeções SQL, porque a struct é `sqlx::FromRow` (ver §14).
///
/// A ordem vem pronta do backend (`season_number`, depois `episode_number`), então
/// aqui é só quebrar — não reordena nada.
function porTemporada(works: WorkListItem[]) {
  const grupos = new Map<number, WorkListItem[]>();
  for (const w of works) {
    // `-1` recolhe quem não tem temporada: episódio ainda não identificado
    // continua visível, num grupo próprio, em vez de sumir da série.
    const chave = w.season_number ?? -1;
    const atual = grupos.get(chave);
    if (atual) atual.push(w);
    else grupos.set(chave, [w]);
  }

  return [...grupos.entries()]
    .sort(([a], [b]) => a - b)
    .map(([chave, itens]) => ({
      chave,
      titulo:
        chave === -1 ? "Sem temporada" : chave === 0 ? "Especiais" : `Temporada ${chave}`,
      itens,
      vistos: itens.filter((w) => w.finished).length,
    }));
}

/// Uma entrada da biblioteca. Série abre; obra avulsa toca.
///
/// A série reaproveita o mesmo cartão da obra — mesma proporção de pôster,
/// mesma tipografia — e se distingue pelo que só ela tem: contagem de
/// temporadas/episódios e a barra de quanto você já terminou.
function EntryCard({
  entry,
  onOpenSeries,
  onDetails,
  onManage,
}: {
  entry: LibraryEntry;
  onOpenSeries: (id: string, title: string) => void;
  onDetails: (id: string) => void;
  onManage: (id: string) => void;
}) {
  const hue = hueFromTitle(entry.title);

  // Obra avulsa: o cartão de sempre, montado a partir da entrada.
  if (!entry.is_series) {
    const work: WorkListItem = {
      id: entry.id,
      kind: entry.kind ?? "unknown",
      title: entry.title,
      year: entry.year,
      season_number: null,
      episode_number: null,
      match_state: entry.match_state ?? "unmatched",
      match_confidence: null,
      dominant_color: entry.dominant_color,
      poster: entry.poster,
      backdrop: null,
      still: null,
      series_title: null,
      media_file_id: entry.media_file_id,
      duration_seconds: entry.duration_seconds,
      width: null,
      height: null,
      video_codec: null,
      audio_codec: null,
      container: null,
      size_bytes: null,
      position_seconds: entry.position_seconds,
      finished: entry.finished_count > 0,
      tags: null,
    };
    return <Card work={work} onDetails={onDetails} onManage={onManage} />;
  }

  const visto = entry.work_count > 0 ? (entry.finished_count / entry.work_count) * 100 : 0;

  return (
    <div
      className="card-wrap"
      style={
        {
          "--hue": hue,
          ...(entry.dominant_color ? { "--accent-work": entry.dominant_color } : {}),
        } as CSSProperties
      }
    >
      <button className="card" onClick={() => onOpenSeries(entry.id, entry.title)}>
        <div className={entry.poster ? "poster has-art" : "poster"}>
          {entry.poster ? (
            <img src={api.artworkUrl(entry.poster)} alt="" loading="lazy" />
          ) : (
            <span className="poster-title">{entry.title}</span>
          )}
          <span className="badge serie">{entry.work_count} ep</span>
          {visto > 0 && <div className="progress" style={{ width: `${visto}%` }} />}
        </div>
        <div className="card-body">
          <h3>{entry.title}</h3>
          <p className="muted small">
            {[
              entry.year,
              entry.season_count > 1 ? `${entry.season_count} temporadas` : "1 temporada",
              entry.finished_count > 0 ? `${entry.finished_count} vistos` : null,
            ]
              .filter(Boolean)
              .join(" · ")}
          </p>
        </div>
      </button>
    </div>
  );
}

function Card({
  work,
  onDetails,
  onManage,
}: {
  work: WorkListItem;
  onDetails: (id: string) => void;
  onManage: (id: string) => void;
}) {
  const hue = hueFromTitle(work.title);
  const progress =
    work.position_seconds && work.duration_seconds
      ? Math.min(100, (work.position_seconds / work.duration_seconds) * 100)
      : 0;

  const episode =
    work.season_number != null && work.episode_number != null
      ? `S${String(work.season_number).padStart(2, "0")}E${String(work.episode_number).padStart(2, "0")}`
      : null;

  return (
    <div
      className="card-wrap"
      style={
        {
          "--hue": hue,
          // a cor extraída do pôster no M1 assume quando existe
          ...(work.dominant_color ? { "--accent-work": work.dominant_color } : {}),
        } as CSSProperties
      }
    >
      <button
        className="card"
        // Clicar no cartão abre a FICHA, não o player. Começar um filme é
        // decisão, e a decisão precisa da sinopse na frente — o botão de
        // assistir mora no cartaz, a um clique de distância.
        onClick={() => onDetails(work.id)}
      >
        <div className={work.poster ? "poster has-art" : "poster"}>
          {work.poster ? (
            <img src={api.artworkUrl(work.poster)} alt="" loading="lazy" />
          ) : (
            <>
              {/* Sem arte, o cartão precisa informar por conta própria: o que é
                  e em que pé está a identificação. */}
              <span className="poster-title">{work.series_title ?? work.title}</span>
              <span className="sem-arte">
                {[WORK_KINDS[work.kind] ?? null, episode, work.year]
                  .filter(Boolean)
                  .join(" · ")}
              </span>
            </>
          )}

          {work.match_state === "unmatched" && <span className="badge">sem metadata</span>}
          {work.match_state === "needs_review" && (
            <span className="badge warn">
              revisar {Math.round((work.match_confidence ?? 0) * 100)}%
            </span>
          )}

          {progress > 0 && <div className="progress" style={{ width: `${progress}%` }} />}
        </div>

        <div className="card-body">
          {work.series_title && <p className="series">{work.series_title}</p>}
          <h3>{work.title}</h3>
          <p className="muted small">
            {[
              episode,
              work.year,
              work.height ? `${work.height}p` : null,
              formatDuration(work.duration_seconds),
              formatSize(work.size_bytes),
            ]
              // `formatSize`/`formatDuration` devolvem "—" quando não sabem, e
              // "—" é truthy: sem isto todo cartão terminava em " · —".
              .filter((parte) => parte && parte !== "—")
              .join(" · ")}
          </p>
          {work.tags && work.tags.length > 0 && (
            <p className="card-tags">
              {work.tags.slice(0, 3).map((t) => (
                <span key={t}>{t.split(":")[1]}</span>
              ))}
            </p>
          )}
        </div>
      </button>

      {/* O ⋯ deixou de ser um atalho pra ficha (que agora é o cartão inteiro)
          e passou a ser o que faltava: identificar à mão, corrigir o parser,
          ignorar, apagar do disco. */}
      <button className="card-info" title="gerenciar" onClick={() => onManage(work.id)}>
        ⋯
      </button>
    </div>
  );
}

/// A aba `experimentação` passou a ter duas salas, e o gesto de trocar entre
/// elas é o mesmo da revisão logo abaixo — repetir o vocabulário é o que faz as
/// telas parecerem o mesmo produto (§14).
///
/// A revisão tem duas entradas, e a ordem delas é a recomendação.
///
/// **Pastas** primeiro porque é onde uma decisão vale centenas de arquivos —
/// medido: 7.568 arquivos pendentes em 578 pastas. **Arquivos** é o caso que
/// sobra: o que a pasta não resolve, porque é exceção de verdade.
function RevisaoTabs({ onChanged }: { onChanged: () => void }) {
  const [modo, setModo] = useState<"pastas" | "arquivos">("pastas");

  return (
    <div className="revisao">
      <div className="revisao-tabs">
        <button
          className={modo === "pastas" ? "on" : ""}
          onClick={() => setModo("pastas")}
        >
          pastas
        </button>
        <button
          className={modo === "arquivos" ? "on" : ""}
          onClick={() => setModo("arquivos")}
        >
          arquivos
        </button>
      </div>
      {modo === "pastas" ? (
        <Scopes onChanged={onChanged} />
      ) : (
        <Review onChanged={onChanged} />
      )}
    </div>
  );
}
