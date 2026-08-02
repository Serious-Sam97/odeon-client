import { useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState } from "react";
import { api, type LibraryEntry, type WorkListItem } from "./api";
import { duracao, ficha, paraLista } from "./Details";

/// A locadora: a biblioteca vista como uma loja de aluguel.
///
/// A unidade aqui **não é a obra, é a caixa**. Um acervo de 17.498 registros
/// vira 600 caixas, e a conta de por que é assim está em `docs/DESIGN.md` §20:
/// uma série é uma caixa de coleção e não 21 fitas, e o que não tem capa não
/// entra numa estante — uma estante é feita de capas.
///
/// Isso torna `/api/library` (a listagem agrupada da R3) a fonte exata: ela já
/// devolve uma entrada por série e uma por obra avulsa. Nenhum backend novo.

/// As estantes, na ordem em que reivindicam os títulos.
///
/// A ordem importa porque cada título fica numa estante só, como numa loja de
/// verdade. Os gêneros distintivos vêm primeiro: se DRAMA viesse antes, ele
/// engoliria metade do acervo e as outras estantes ficariam vazias.
///
/// Cada estante junta vários rótulos crus porque o acervo tem **dois**
/// vocabulários: o do provider de filme é em pt-BR ("Ficção científica") e o
/// de série é em inglês ("Sci-Fi & Fantasy"). Sem a união, uma estante teria
/// só filmes e a outra só séries.
const ESTANTES: { nome: string; generos: string[] }[] = [
  { nome: "Terror", generos: ["Terror"] },
  { nome: "Faroeste", generos: ["Faroeste"] },
  { nome: "Guerra", generos: ["Guerra"] },
  { nome: "Documentário", generos: ["Documentário", "História"] },
  { nome: "Animação", generos: ["Animação"] },
  { nome: "Infantil", generos: ["Família", "Kids"] },
  {
    nome: "Ficção científica",
    generos: ["Ficção científica", "Sci-Fi & Fantasy", "Sci-Fi", "Fantasia"],
  },
  {
    nome: "Ação e aventura",
    generos: ["Ação", "Aventura", "Action", "Adventure", "Action & Adventure", "Sports"],
  },
  { nome: "Crime e suspense", generos: ["Crime", "Mistério", "Thriller"] },
  { nome: "Comédia", generos: ["Comédia", "Comedy"] },
  { nome: "Romance", generos: ["Romance", "Música"] },
  { nome: "Drama", generos: ["Drama"] },
];

/// O corte entre fita e disco.
///
/// O DVD chegou ao Brasil em 1998–99, mas a locadora só virou de verdade
/// depois de 2000. 1996 deixa o acervo em 96 fitas e 504 discos — a proporção
/// certa: a prateleira de VHS é o cantinho dos clássicos, não a loja inteira.
const ULTIMO_ANO_VHS = 1996;

export interface Caixa {
  id: string;
  titulo: string;
  ano: number | null;
  poster: string;
  cor: string;
  serie: boolean;
  temporadas: number;
  vhs: boolean;
  /// Se existe arquivo. Sabido no momento em que a estante é montada, e é o
  /// que permite a lombada responder ao clique **antes** de o detalhe chegar.
  temArquivo: boolean;
  posicao: number;
}

function deLibrary(e: LibraryEntry): Caixa | null {
  if (!e.poster) return null;
  return {
    id: e.id,
    titulo: e.title,
    ano: e.year,
    poster: e.poster,
    cor: e.dominant_color ?? "#3a3a44",
    serie: e.is_series,
    temporadas: e.season_count,
    vhs: !!e.year && e.year <= ULTIMO_ANO_VHS,
    temArquivo: !!e.media_file_id || e.is_series,
    posicao: e.position_seconds ?? 0,
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
    vhs: !!w.year && w.year <= ULTIMO_ANO_VHS,
    temArquivo: !!w.media_file_id,
    posicao: w.position_seconds ?? 0,
  };
}

export default function Locadora({
  onPlay,
  onAbrirColecao,
}: {
  onPlay: (w: WorkListItem) => void;
  onAbrirColecao: (id: string, titulo: string) => void;
}) {
  const [prateleiras, setPrateleiras] = useState<{ nome: string; caixas: Caixa[] }[]>([]);
  const [devolucoes, setDevolucoes] = useState<Caixa[]>([]);
  const [lancamentos, setLancamentos] = useState<Caixa[]>([]);
  const [erro, setErro] = useState<string | null>(null);
  const [carregando, setCarregando] = useState(true);
  const [naMao, setNaMao] = useState<{ caixa: Caixa; origem: DOMRect } | null>(null);

  useEffect(() => {
    let vivo = true;

    // As 12 estantes vão juntas; sequencial isso levava 3s.
    Promise.all(
      ESTANTES.map((e) =>
        api
          .library(
            { tags: e.generos.map((g) => `genre:${g}`), tagMode: "any", sort: "title" },
            0,
            500,
          )
          .catch(() => [] as LibraryEntry[]),
      ),
    )
      .then((respostas) => {
        if (!vivo) return;
        // Cada título fica numa estante só — a primeira que o reivindicar.
        const visto = new Set<string>();
        const montadas = ESTANTES.map((e, i) => {
          const caixas: Caixa[] = [];
          for (const item of respostas[i]) {
            if (visto.has(item.id)) continue;
            const c = deLibrary(item);
            if (!c) continue;
            visto.add(item.id);
            caixas.push(c);
          }
          return { nome: e.nome, caixas };
        }).filter((e) => e.caixas.length > 0);

        setPrateleiras(montadas);

        // Lançamentos sai do que já está na loja: nenhuma requisição a mais.
        const tudo = montadas.flatMap((e) => e.caixas);
        setLancamentos(
          [...tudo].sort((a, b) => (b.ano ?? 0) - (a.ano ?? 0)).slice(0, 10),
        );
        setCarregando(false);
      })
      .catch((e) => vivo && (setErro(String(e)), setCarregando(false)));

    api
      .continueWatching()
      .then((ws) => {
        if (vivo) setDevolucoes(ws.map(deLista).filter((c): c is Caixa => !!c).slice(0, 8));
      })
      .catch(() => {});

    return () => {
      vivo = false;
    };
  }, []);

  const total = useMemo(
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
            : `${total} caixas nas estantes — ${total ? "boa escolha" : "nada com capa por aqui"}`}
        </p>
      </header>

      {erro && <p className="error">{erro}</p>}

      {(devolucoes.length > 0 || lancamentos.length > 0) && (
        <div className="balcao">
          {devolucoes.length > 0 && (
            <Estante
              nome="Devoluções"
              legenda="você parou no meio"
              caixas={devolucoes}
              onPegar={(c, r) => setNaMao({ caixa: c, origem: r })}
            />
          )}
          {lancamentos.length > 0 && (
            <Estante
              nome="Lançamentos"
              legenda="o que há de mais novo"
              caixas={lancamentos}
              onPegar={(c, r) => setNaMao({ caixa: c, origem: r })}
            />
          )}
        </div>
      )}

      {prateleiras.map((p) => (
        <Estante
          key={p.nome}
          nome={p.nome}
          legenda={`${p.caixas.length} ${p.caixas.length === 1 ? "título" : "títulos"}`}
          caixas={p.caixas}
          onPegar={(c, r) => setNaMao({ caixa: c, origem: r })}
        />
      ))}

      {naMao && (
        <NaMao
          caixa={naMao.caixa}
          origem={naMao.origem}
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

function Estante({
  nome,
  legenda,
  caixas,
  onPegar,
}: {
  nome: string;
  legenda: string;
  caixas: Caixa[];
  onPegar: (c: Caixa, origem: DOMRect) => void;
}) {
  return (
    <section className="estante">
      <div className="placa">
        <span>{nome}</span>
        <i>{legenda}</i>
      </div>
      <div className="prateleira">
        <div className="fileira">
          {caixas.map((c) => (
            <CaixaNaEstante key={c.id} caixa={c} onPegar={(r) => onPegar(c, r)} />
          ))}
        </div>
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
  onPegar,
}: {
  caixa: Caixa;
  onPegar: (origem: DOMRect) => void;
}) {
  const classe = [
    "caixa",
    caixa.vhs ? "vhs" : "dvd",
    caixa.serie ? "colecao" : "",
    caixa.posicao > 30 ? "comecada" : "",
  ]
    .filter(Boolean)
    .join(" ");

  return (
    <button
      className={classe}
      style={{ ["--cor" as string]: caixa.cor }}
      // O retângulo de onde a caixa saiu: é dele que o voo até o centro parte.
      // Sem isto ela apareceria pronta no meio da tela, que é o que se queria
      // justamente evitar.
      onClick={(e) => onPegar(e.currentTarget.getBoundingClientRect())}
      title={`${caixa.titulo}${caixa.ano ? ` (${caixa.ano})` : ""}`}
    >
      <span className="lombada">
        <span className="lomb-titulo">{caixa.titulo}</span>
        <span className="lomb-selo">{caixa.vhs ? "VHS" : "DVD"}</span>
      </span>
      <span className="topo" />
      <span className="frente">
        <img src={api.artworkUrl(caixa.poster)} alt="" loading="lazy" />
        <span className="brilho" />
        <span className={caixa.vhs ? "tampa" : "dobradica"} />
        {caixa.serie && caixa.temporadas > 0 && (
          <span className="temporadas">
            {caixa.temporadas} temporada{caixa.temporadas > 1 ? "s" : ""}
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
  onFechar,
  onPlay,
  onAbrirColecao,
}: {
  caixa: Caixa;
  origem: DOMRect;
  onFechar: () => void;
  onPlay: (w: WorkListItem) => void;
  onAbrirColecao: (id: string, titulo: string) => void;
}) {
  const [verso, setVerso] = useState<Verso | null>(null);
  const [fase, setFase] = useState<
    "voando" | "na-mao" | "abrindo" | "midia" | "guardando" | "tocando"
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

  const podeAbrir = caixa.temArquivo;

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
    setFase("tocando");

    const limite = Date.now() + 8000;
    const concluir = () => {
      if (caixa.serie) return onAbrirColecao(caixa.id, caixa.titulo);
      const pronto = versoAgora.current?.paraTocar;
      if (pronto) return onPlay(pronto);
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
            caixa.vhs ? "vhs" : "dvd",
            caixa.serie ? "colecao" : "",
            fase === "abrindo" || fase === "midia" || fase === "tocando" ? "aberta" : "",
            fase === "midia" || fase === "tocando" ? "recuada" : "",
            fase === "na-mao" ? "girável" : "",
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
            <span className="lomb-selo">{caixa.vhs ? "VHS" : "DVD"}</span>
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
            <span className="interior-marca">{caixa.vhs ? "VHS" : "DVD"}</span>
          </span>

          <span className="frente">
            {/* `draggable={false}`: sem isto o navegador inicia o próprio
                arrasto de imagem no primeiro pixel, o `pointermove` nunca
                chega, e girar a caixa vira arrastar uma miniatura da capa. */}
            <img src={api.artworkUrl(caixa.poster)} alt="" draggable={false} />
            <span className="brilho" />
            <span className={caixa.vhs ? "tampa" : "dobradica"} />
          </span>

        <div className="contracapa">
          <header className="verso-topo">
            <h3>{caixa.titulo}</h3>
            <p>
              {[caixa.ano, caixa.serie ? "série" : "filme", caixa.vhs ? "VHS" : "DVD"]
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
                <button className="cartaz-play" onClick={abrir}>
                  ▸ ver a série
                </button>
              ) : (
                <button className="cartaz-play" disabled={!podeAbrir} onClick={abrir}>
                  {caixa.posicao > 30 ? "▸ continuar" : "▸ assistir"}
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
              className={`midia3d ${caixa.vhs ? "fita" : "disco"}${
                fase === "midia" ? " assentada" : ""
              }`}
              style={{
                opacity: fora ? 1 : 0,
                transform: fora
                  ? `translateZ(${SAIDA.z}px) scale(1) rotateX(${giroMidia.x.toFixed(1)}deg) rotateY(${giroMidia.y.toFixed(1)}deg)`
                  : DENTRO,
              }}
            >
              {caixa.vhs ? <Fita caixa={caixa} /> : <Disco caixa={caixa} />}
            </div>
          </div>
        )}
      </div>

      {(fase === "na-mao" || fase === "midia") && (
        <div className="mao-rodape">
          <span className="mao-dica">
            {fase === "midia"
              ? `arraste pra girar · centro ${caixa.serie ? "abre a série" : "toca"} · fora, guarda`
              : "arraste pra girar · clique na abertura pra abrir a caixa"}
          </span>
          <button className="mao-devolver" onClick={onFechar}>
            devolver à estante
          </button>
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
