import {
  useCallback,
  useEffect,
  useLayoutEffect,
  useMemo,
  useRef,
  useState,
  type CSSProperties,
} from "react";
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
  type Tag,
  type Collection,
  type MatchStatus,
  type Sala,
  type ScanStatus,
  type ScrubStatus,
  type Versao,
  type WorkListItem,
  comPrazo,
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
  /// ⚠️ **As séries viraram aba** — 18/08/2026. Elas eram uma prateleira dentro
  /// da biblioteca, escolhida por uma pílula, e o dono disse o que estava
  /// errado: a separação parecia um filtro. Duas bibliotecas separadas, como o
  /// Jellyfin faz. Ver `docs/SERIES.md §13`.
  | "series"
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
  library: "/filmes",
  series: "/series",
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
  { chave: "library", rotulo: "filmes" },
  { chave: "series", rotulo: "séries" },
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
  /// Qual temporada está aberta dentro da série. `null` é a ficha da série.
  ///
  /// ⚠️ Ele é zerado sempre que a coleção muda — ver o `useEffect` abaixo. Sem
  /// isso, sair de uma série e entrar noutra abriria a temporada de número igual
  /// da série nova, que é uma tela que ninguém pediu.
  const [temporadaAberta, setTemporadaAberta] = useState<number | null>(null);
  const [managing, setManaging] = useState<string | null>(null);
  /// A entrada cuja escolha de versão está aberta — ver `EscolhaDeVersao`.
  /// Guarda a **entrada** e não o id: as versões já vieram dentro dela.
  const [escolhendoVersao, setEscolhendoVersao] = useState<LibraryEntry | null>(null);
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

  /// ## ⚠️ As duas bibliotecas — 18/08/2026
  ///
  /// `filmes` é «tudo que não é série»; `séries` é a prateleira `format:série`.
  /// As chaves vêm do espaço `format` que o **servidor** declara, e não de uma
  /// lista escrita aqui.
  ///
  /// ⚠️ O anime entra na exclusão dos filmes: `tags_not=format:série` sozinho
  /// deixa passar o `Beyblade` — 43 episódios que carregam `format:anime` e não
  /// `format:série`. Medido pelo servidor.
  const [formatos, setFormatos] = useState<Tag[]>([]);
  useEffect(() => {
    api
      .tags()
      .then((t) => setFormatos(t.filter((x) => x.namespace === "format" && x.work_count > 0)))
      .catch(() => {});
  }, []);

  useEffect(() => {
    if (!formatos.length) return;
    const serie = formatos.find((t) => t.value.startsWith("série"));
    const anime = formatos.find((t) => t.value.startsWith("anime"));
    const chaveDe = (t?: Tag) => (t ? `${t.namespace}:${t.value}` : undefined);
    if (tab === "series") {
      setFilters((f) => ({ ...f, shelf: chaveDe(serie), tagsNot: undefined }));
    } else if (tab === "library") {
      setFilters((f) => ({
        ...f,
        shelf: undefined,
        tagsNot: [chaveDe(serie), chaveDe(anime)].filter(Boolean) as string[],
      }));
    }
  }, [tab, formatos]);

  useEffect(() => {
    setTemporadaAberta(null);
  }, [filters.collection]);

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
        const list = await comPrazo(api.works(f), "episódios");
        setWorks(list);
        setEntries([]);
        setTotal(list.length);
      } else {
        const list = await comPrazo(api.library(f), "biblioteca");
        setEntries(list);
        setWorks([]);
        setTotal(list[0]?.total ?? 0);
      }
      /// ⚠️ A fileira de continuar **não derruba a tela**: ela é enfeite ao lado
      /// da grade, e um servidor que demorou pra ela não é motivo pra dizer que a
      /// biblioteca não abriu.
      setResume(await comPrazo(pendente, "continuar").catch(() => []));
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

  /// O «continuar» que **esta** aba mostra.
  ///
  /// A rota `/api/continue` devolve tudo que está pela metade, sem saber de
  /// abas; quem separa é a tela, com a mesma chave que o resto do app usa pra
  /// dizer o que é série: `series_title`.
  const continuarDaAba = useMemo(
    () =>
      tab === "series"
        ? resume.filter((w) => w.series_title)
        : resume.filter((w) => !w.series_title),
    [resume, tab],
  );

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

        {tab === "live" && (
          <AoVivo
            isAdmin={isAdmin}
            onDetails={setDetailsOf}
            /// ⚠️ **Vai pra aba das séries, e não pra biblioteca**: a ficha é de
            /// uma série, e o `voltar` dela tem de cair onde séries moram. Levar
            /// pra `library` faria o caminho de volta desaguar nos filmes.
            aoVerSerie={(id, titulo) => {
              setTab("series");
              setFilters({ collection: id, collectionName: titulo });
            }}
          />
        )}

        {(tab === "library" || tab === "series") && (
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

            {/* ⚠️ A fileira de prateleiras saiu daqui — 18/08/2026. Ela parecia
                o que não era: uma segunda barra de filtros. Séries virou aba. */}
            <FilterBar
              filters={filters}
              onChange={setFilters}
              /* R55 — o que falta pros contadores contarem tudo. Vem do status
                 da identificação, que esta tela já pergunta. */
              naoIdentificadas={match?.nao_identificadas ?? 0}
              aoAbrirRevisao={() => setTab("review")}
            />

            {/* ## ⚠️ Cada aba continua **o que ela guarda** — 19/08/2026
                
                > «arruma o filtro da aba séries também»

                A grade já vinha filtrada (`?tags=format:série` numa, `tags_not`
                na outra), mas esta fileira vem de **outra rota** — `/api/continue`,
                que devolve por obra e não sabe de aba. O resultado era 007 e
                Resident Evil abrindo a aba das séries, e um episódio de série
                aparecendo na dos filmes.

                É a mesma correção que o celular fez em 18/08 e pelo mesmo motivo,
                que está escrito lá: «série começada é assunto da aba das séries».

                ⚠️ Dentro de uma coleção a fileira **some**: ali a pergunta já é
                outra — a temporada lista os episódios, e um «continuar» genérico
                por cima disso competiria com a própria lista. */}
            {continuarDaAba.length > 0 && !filters.collection && (
              <section>
                <h2 className="section-title">Continuar assistindo</h2>
                <div className="grid">
                  {continuarDaAba.map((w) => (
                    <Card key={w.id} work={w} onDetails={setDetailsOf} onManage={setManaging} />
                  ))}
                </div>
              </section>
            )}

            <section>
              <h2 className="section-title">
                {filters.collection ? "Episódios" : tab === "series" ? "Séries" : "Filmes"}{" "}
                {/* O número diz o que é: quantas estão na tela DE quantas existem.
                    Antes dizia "300" com 17.498 no banco. */}
                <span className="count">
                  {mostrando < total ? `${mostrando} de ${total}` : total}
                </span>
              </h2>

              {loading ? (
                <p className="muted">carregando…</p>
              ) : /* ⚠️ **Erro não é acervo vazio** — 19/08/2026. Com a grade sem
                     nada por causa de um pedido que falhou, a tela dizia «Nada
                     encontrado · afrouxe os filtros ou rode uma varredura»: manda
                     a pessoa mexer no que não está quebrado, e esconde que o
                     problema foi outro. É o mesmo §18 que a aba do ao vivo levou
                     hoje — não saber é diferente de não ter. */
              error && mostrando === 0 ? (
                <div className="empty">
                  <p>a biblioteca não abriu</p>
                  <p className="muted">{error}</p>
                  <button className="chip" onClick={() => { setError(null); setLoading(true); refresh(filtersRef.current); }}>
                    tentar de novo
                  </button>
                </div>
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
                    /* Dois níveis, e não sete blocos empilhados: a série
                       apresenta e oferece continuar; a temporada lista. */
                    temporadaAberta === null ? (
                      <FichaDaSerie
                        id={filters.collection}
                        titulo={filters.collectionName ?? "esta série"}
                        works={works}
                        onAbrirTemporada={setTemporadaAberta}
                        onPlay={tocar}
                      />
                    ) : (
                      (() => {
                        const t = porTemporada(works).find((x) => x.chave === temporadaAberta);
                        /* A temporada some se a lista mudar debaixo dela — outro
                           filtro, outra série. Volta pra ficha em vez de sumir. */
                        if (!t) return <FichaDaSerie
                          id={filters.collection}
                          titulo={filters.collectionName ?? "esta série"}
                          works={works}
                          onAbrirTemporada={setTemporadaAberta}
                          onPlay={tocar}
                        />;
                        return (
                          <ListaDaTemporada
                            serie={filters.collectionName ?? "a série"}
                            temporada={t}
                            onVoltar={() => setTemporadaAberta(null)}
                            onDetails={setDetailsOf}
                          />
                        );
                      })()
                    )
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
                          onChooseVersion={setEscolhendoVersao}
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

      {escolhendoVersao && (
        <EscolhaDeVersao
          entry={escolhendoVersao}
          onClose={() => setEscolhendoVersao(null)}
          onEscolher={(id) => {
            setEscolhendoVersao(null);
            setDetailsOf(id);
          }}
        />
      )}

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
          /// ⚠️ Trocar a obra que toca é **aqui**, e não dentro do player: quem
          /// guarda o que está tocando é esta tela, e um player que trocasse o
          /// próprio assunto teria duas fontes de verdade sobre a mesma coisa.
          aoTrocarDeObra={setPlaying}
        />
      )}
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

/// Onde a pessoa parou dentro da série — o que o botão principal vai oferecer.
///
/// O **começado** ganha do primeiro não visto: ele é onde a pessoa estava, e o
/// não visto é só onde ela chegaria. Havendo os dois, volta-se pro meio do que
/// se estava assistindo, que é o que um "continuar" promete.
///
/// ⚠️ Série inteira vista NÃO fica sem botão — volta o primeiro episódio, como
/// "começar". Um `null` aqui apagaria a única ação da tela justamente de quem
/// mais gostou dela.
///
/// Mesma regra do Android e do iOS. Ver `ondeParar` nos dois.

function ondeParar(works: WorkListItem[]): { ep: WorkListItem; comecado: boolean } | null {
  const comecado = works.find((w) => (w.position_seconds ?? 0) > 0 && !w.finished);
  if (comecado) return { ep: comecado, comecado: true };
  const proximo = works.find((w) => !w.finished);
  if (proximo) return { ep: proximo, comecado: false };
  return works[0] ? { ep: works[0], comecado: false } : null;
}

/// `S01E04`. ⚠️ `null` quando falta um dos dois — meio código é pior que nenhum.
function codigoDoEpisodio(w: WorkListItem): string | null {
  if (w.season_number != null && w.episode_number != null) {
    return `S${String(w.season_number).padStart(2, "0")}E${String(w.episode_number).padStart(2, "0")}`;
  }
  return w.episode_number != null ? `ep ${w.episode_number}` : null;
}

/// A ficha de uma série — o desenho aprovado pelo dono em 18/08/2026.
///
/// ## ⚠️ Ela substitui a pilha de temporadas
///
/// A web já navegava série → temporada → episódio desde a R3, e era a única que
/// não estava quebrada. O que ela fazia era empilhar TODAS as temporadas na
/// mesma rolagem, cada uma com sua grade: em Malcolm são 151 cartões em sete
/// blocos. O desenho novo separa os dois níveis — a série apresenta e oferece
/// continuar; a temporada lista.
///
/// ⚠️ A arte de cada temporada é o `still` do primeiro episódio dela: o servidor
/// ainda não manda pôster de temporada. Ver `PEDIDOS-AO-SERVIDOR.md, «já entregue» 10`.
function FichaDaSerie({
  id,
  titulo,
  works,
  onAbrirTemporada,
  onPlay,
}: {
  id?: string;
  titulo: string;
  works: WorkListItem[];
  onAbrirTemporada: (chave: number) => void;
  onPlay: (w: WorkListItem) => void;
}) {
  /// ## ⚠️ A coleção **enriquece e não bloqueia** — 18/08/2026
  ///
  /// Pôster de temporada, sinopse e backdrop da série vieram do servidor. Campo
  /// a campo, sempre com reserva: 12 temporadas não têm pôster e 5 séries não
  /// têm sinopse, e nenhuma delas pode piorar por causa disso.
  const [colecao, setColecao] = useState<{
    collection: Collection;
    children: Collection[];
  } | null>(null);

  useEffect(() => {
    let vivo = true;
    if (!id) return;
    api
      .collection(id)
      .then((r) => {
        if (vivo) setColecao(r);
      })
      .catch(() => {});
    return () => {
      vivo = false;
    };
  }, [id]);

  const porPosicao = new Map(
    (colecao?.children ?? []).filter((c) => c.position != null).map((c) => [c.position!, c]),
  );

  const temporadas = porTemporada(works);
  const onde = ondeParar(works);
  const vistos = works.filter((w) => w.finished).length;
  const ano = works.find((w) => w.year)?.year;
  const primeiro = works[0];
  const fundo =
    colecao?.collection.backdrop ?? primeiro?.backdrop ?? primeiro?.still ?? primeiro?.poster ?? null;
  const sinopse = colecao?.collection.overview ?? null;

  const conta = [
    ano ? String(ano) : null,
    temporadas.length > 0 ? `${temporadas.length} temporada${temporadas.length > 1 ? "s" : ""}` : null,
    works.length > 0 ? `${works.length} episódio${works.length > 1 ? "s" : ""}` : null,
    // §24: nada visto não escreve "0 vistos" — simplesmente não fala do assunto.
    vistos > 0 ? `${vistos} visto${vistos > 1 ? "s" : ""}` : null,
  ].filter(Boolean).join("  ·  ");

  return (
    <div className="ficha-serie">
      {fundo && (
        <div className="ficha-serie-pano">
          <img src={api.artworkUrl(fundo)} alt="" />
        </div>
      )}
      <h1 className="ficha-serie-titulo">{titulo}</h1>
      <p className="ficha-serie-conta">{conta}</p>
      {/* ⚠️ 115 das 120 têm sinopse. As 5 que não têm não ganham parágrafo. */}
      {sinopse && <p className="ficha-serie-sinopse">{sinopse}</p>}

      {onde && (
        <div className="ficha-serie-acoes">
          <button className="chip principal" onClick={() => onPlay(onde.ep)}>
            ▸ {onde.comecado ? "continuar" : "começar"}
            {"  "}
            {[codigoDoEpisodio(onde.ep), onde.ep.title].filter(Boolean).join(" · ")}
          </button>
          {/* §24: "do começo" só existe havendo meio. */}
          {onde.comecado && (
            <button
              className="chip"
              onClick={() => onPlay({ ...onde.ep, position_seconds: 0 })}
            >
              do começo
            </button>
          )}
        </div>
      )}

      <div className="strip">
        <h2>Temporadas</h2>
        <span className="rule" />
        <span className="strip-meta">{temporadas.length}</span>
      </div>

      <div className="fileira-temporadas">
        {temporadas.map((t) => {
          const doServidor = porPosicao.get(t.chave);
          const arte = t.itens.find((w) => w.still ?? w.backdrop ?? w.poster);
          /* ⚠️ O pôster da temporada ganha do still — e a moldura vira retrato
             junto, porque pôster é 2:3. Ver `.cartao-temporada`. */
          const capa =
            doServidor?.poster ?? (arte ? (arte.still ?? arte.backdrop ?? arte.poster) : null);
          const nome = doServidor?.title && doServidor.title !== t.titulo ? doServidor.title : t.titulo;
          const andado = t.itens.length > 0 ? (t.vistos / t.itens.length) * 100 : 0;
          return (
            <button
              key={t.chave}
              className="cartao-temporada"
              onClick={() => onAbrirTemporada(t.chave)}
            >
              <div className="thumb has-art retrato">
                {capa && <img src={api.artworkUrl(capa)} alt="" loading="lazy" />}
                {/* Barra só havendo visto — uma barra vazia afirma "começou". */}
                {andado > 0 && <div className="progress" style={{ width: `${andado}%` }} />}
              </div>
              <h3>{nome}</h3>
              <p className="muted small">
                {t.itens.length} ep{t.vistos > 0 && ` · ${t.vistos} vistos`}
              </p>
            </button>
          );
        })}
      </div>
    </div>
  );
}

/// Os episódios de uma temporada, em LISTA.
///
/// ⚠️ Lista e não grade, e é o ponto do desenho: um episódio não se escolhe pela
/// imagem — a capa é a mesma nos dezoito. Escolhe-se pelo número e pelo que já
/// aconteceu com ele.
///
/// ⚠️ Falta a sinopse por episódio, e é falta do servidor: `WorkListItem` não
/// traz `overview`. Ver `PEDIDOS-AO-SERVIDOR.md, «já entregue» 10`.
function ListaDaTemporada({
  serie,
  temporada,
  onVoltar,
  onDetails,
}: {
  serie: string;
  temporada: { chave: number; titulo: string; itens: WorkListItem[]; vistos: number };
  onVoltar: () => void;
  onDetails: (id: string) => void;
}) {
  return (
    <div className="lista-temporada">
      <button className="chip voltar" onClick={onVoltar}>
        ‹ {serie}
      </button>
      <h1 className="ficha-serie-titulo">{temporada.titulo}</h1>
      <p className="ficha-serie-conta">
        {temporada.itens.length} episódio{temporada.itens.length > 1 ? "s" : ""}
        {temporada.vistos > 0 && `  ·  ${temporada.vistos} visto${temporada.vistos > 1 ? "s" : ""}`}
      </p>

      <ul className="episodios">
        {temporada.itens.map((w) => {
          const arte = w.still ?? w.backdrop ?? w.poster;
          const andado =
            w.position_seconds && w.duration_seconds
              ? Math.min(100, (w.position_seconds / w.duration_seconds) * 100)
              : 0;
          const faltam =
            andado > 0 && !w.finished && w.duration_seconds
              ? Math.round((w.duration_seconds - (w.position_seconds ?? 0)) / 60)
              : 0;
          const sub = [
            codigoDoEpisodio(w),
            w.duration_seconds ? `${Math.round(w.duration_seconds / 60)}min` : null,
            faltam > 0 ? `faltam ${faltam}min` : null,
          ].filter(Boolean).join("  ·  ");

          return (
            <li key={w.id}>
              <button
                className={w.finished ? "episodio visto" : "episodio"}
                onClick={() => onDetails(w.id)}
              >
                <span className="episodio-quadro thumb has-art">
                  {arte && <img src={api.artworkUrl(arte)} alt="" loading="lazy" />}
                  {/* Um OU outro: quem terminou não parou no meio. */}
                  {w.finished ? (
                    <span className="marca-visto">✓</span>
                  ) : (
                    andado > 0 && <div className="progress" style={{ width: `${andado}%` }} />
                  )}
                </span>
                <span className="episodio-corpo">
                  <span className="episodio-linha">
                    {w.episode_number != null && (
                      <span className="episodio-numero">{w.episode_number}</span>
                    )}
                    <span className="episodio-titulo">{w.title}</span>
                  </span>
                  <span className={andado > 0 && !w.finished ? "episodio-sub aqui" : "episodio-sub"}>
                    {sub}
                  </span>
                  {/* ⚠️ A sinopse chegou em 18/08/2026 — era "falta do servidor,
                      não desenho". Metade não tem, e aí a linha não existe. */}
                  {w.overview && <span className="episodio-sinopse">{w.overview}</span>}
                </span>
              </button>
            </li>
          );
        })}
      </ul>
    </div>
  );
}

/// Uma entrada da biblioteca. Série abre; obra avulsa toca.
///
/// A série reaproveita o mesmo cartão da obra — mesma proporção de pôster,
/// mesma tipografia — e se distingue pelo que só ela tem: contagem de
/// temporadas/episódios e a barra de quanto você já terminou.
/**
 * O nome de um idioma em português, a partir do código do contêiner.
 *
 * ⚠️ É a **segunda** cópia desta tabela — a outra é `ui/Idioma.kt` do Android.
 * Não há tipo compartilhado nem código gerado entre a web e o app (a espec
 * registrou isso como a dívida que a separação dos repositórios comprou), e este
 * é mais um lugar onde ela aparece.
 *
 * Não dava pra evitar pedindo o nome pronto ao servidor: aqui ele manda
 * **código** de propósito, e traduzir código em nome é desenho — a mesma regra
 * que deixa a frase do mural e o rótulo do eixo da revista no cliente.
 *
 * ⚠️ **Código desconhecido não vira texto**, vira `null`, e a linha omite. Mostrar
 * `hun` numa escolha é mostrar dado de contêiner com cara de idioma.
 */
const IDIOMAS: Record<string, string> = {
  por: "Português", pt: "Português",
  eng: "Inglês", en: "Inglês",
  spa: "Espanhol", es: "Espanhol",
  fra: "Francês", fre: "Francês", fr: "Francês",
  deu: "Alemão", ger: "Alemão", de: "Alemão",
  ita: "Italiano", it: "Italiano",
  jpn: "Japonês", ja: "Japonês",
  kor: "Coreano", ko: "Coreano",
  zho: "Chinês", chi: "Chinês", zh: "Chinês",
  rus: "Russo", ru: "Russo",
  ara: "Árabe", ar: "Árabe",
  nld: "Holandês", dut: "Holandês", nl: "Holandês",
  swe: "Sueco", sv: "Sueco",
  dan: "Dinamarquês", da: "Dinamarquês",
  nor: "Norueguês", no: "Norueguês",
  fin: "Finlandês", fi: "Finlandês",
  pol: "Polonês", pl: "Polonês",
  tur: "Turco", tr: "Turco",
  hin: "Hindi", hi: "Hindi",
};

function idiomasEmPortugues(codigos: string[]): string | null {
  const nomes = [
    ...new Set(codigos.map((c) => IDIOMAS[c.trim().toLowerCase()]).filter(Boolean)),
  ];
  if (nomes.length === 0) return null;
  if (nomes.length === 1) return nomes[0];
  return `${nomes.slice(0, -1).join(", ")} e ${nomes[nomes.length - 1]}`;
}

/**
 * A escolha de versão, quando o mesmo filme está no acervo mais de uma vez.
 *
 * ## Por que ela existe
 *
 * O dono baixou alguns filmes **duas vezes** — um em pt-BR e outro em inglês —
 * porque não achou dual audio, e até 14/08/2026 os dois ocupavam cartões
 * separados na grade, com a mesma capa e o mesmo ano. Agora o servidor os agrupa
 * (`LibraryEntry.versions`) e a escolha acontece aqui. O pedido inteiro está no
 * `android/docs/PEDIDOS-AO-SERVIDOR.md` §2.1.
 *
 * ⚠️ **Ela escolhe uma obra, e não um arquivo.** Cada versão tem id, progresso e
 * ficha próprios — escolher abre a ficha daquela obra, com o botão de assistir de
 * sempre. Nada é fundido: fundir apagaria o `position_seconds` de uma das duas,
 * que é a objeção que segurou este pedido desde 04/08/2026.
 *
 * ⚠️ **Nem toda versão tem nome.** O 007 em inglês deste acervo não declara idioma
 * na faixa de áudio, então sai como «versão 2» — queda posicional, a mesma que o
 * menu de faixas usa. Quem distingue as duas ali é o «parou em».
 *
 * A forma reaproveita o `drawer-backdrop` + `modal-programa` do guia ao vivo: é a
 * sobreposição que esta interface já tem, e o clique no fundo fecha.
 */
function EscolhaDeVersao({
  entry,
  onClose,
  onEscolher,
}: {
  entry: LibraryEntry;
  onClose: () => void;
  onEscolher: (id: string) => void;
}) {
  const versoes = (entry.versions ?? []).filter((v) => v.id);

  useEffect(() => {
    const aoTeclar = (e: KeyboardEvent) => e.key === "Escape" && onClose();
    window.addEventListener("keydown", aoTeclar);
    return () => window.removeEventListener("keydown", aoTeclar);
  }, [onClose]);

  return (
    <div
      className="drawer-backdrop"
      onClick={(e) => e.target === e.currentTarget && onClose()}
    >
      <aside className="modal-programa">
        <div className="modal-corpo">
          <header className="drawer-head">
            <div>
              <p className="kind-label">{versoes.length} versões no acervo</p>
              <h2>{entry.title}</h2>
            </div>
            <button className="ghost" onClick={onClose}>
              fechar
            </button>
          </header>
          <div className="versoes">
            {versoes.map((v, i) => (
              <LinhaDeVersao key={v.id} versao={v} posicao={i} onEscolher={onEscolher} />
            ))}
          </div>
        </div>
      </aside>
    </div>
  );
}

function LinhaDeVersao({
  versao,
  posicao,
  onEscolher,
}: {
  versao: Versao;
  posicao: number;
  onEscolher: (id: string) => void;
}) {
  const nome = idiomasEmPortugues(versao.audio_langs) ?? `versão ${posicao + 1}`;

  // `818p · 2,3 GB` — item por item; a linha some inteira se não houver nenhum.
  const tecnico = [
    versao.height ? `${versao.height}p` : null,
    versao.size_bytes ? formatSize(versao.size_bytes) : null,
  ]
    .filter(Boolean)
    .join(" · ");

  // ⚠️ Filme terminado não tem «parou em»: a posição de quem viu até o fim **é**
  // o fim. O piso de 5s separa o toque acidental de ter assistido um teco.
  const parou =
    versao.position_seconds && versao.position_seconds > 5 && !versao.finished
      ? `parou em ${formatDuration(versao.position_seconds)}`
      : null;

  return (
    <button className="versao" onClick={() => onEscolher(versao.id)}>
      <span className="versao-nome">
        {nome}
        {parou && <span className="muted small">{parou}</span>}
      </span>
      {tecnico && <span className="muted small">{tecnico}</span>}
    </button>
  );
}

function EntryCard({
  entry,
  onOpenSeries,
  onDetails,
  onManage,
  onChooseVersion,
}: {
  entry: LibraryEntry;
  onOpenSeries: (id: string, title: string) => void;
  onDetails: (id: string) => void;
  onManage: (id: string) => void;
  onChooseVersion: (entry: LibraryEntry) => void;
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
    /**
     * ⚠️ **O cartão não muda; muda só pra onde o toque leva.**
     *
     * Um filme que existe no acervo em mais de uma versão pergunta qual antes de
     * abrir a ficha — ver `EscolhaDeVersao`. Uma versão só cai direto na ficha,
     * como sempre. `Card` continua sem saber que isto existe, o que é o ponto:
     * versão é assunto da grade, não do cartão.
     *
     * O `filter` por `id` é a mesma guarda do Android: versão sem id não tem
     * ficha pra onde mandar, então ela não conta pro «há escolha».
     */
    const versoes = (entry.versions ?? []).filter((v) => v.id);
    return (
      <Card
        work={work}
        onDetails={versoes.length > 1 ? () => onChooseVersion(entry) : onDetails}
        onManage={onManage}
      />
    );
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
