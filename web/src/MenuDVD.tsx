import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { api, type Cena, type MenuDoDisco, type WorkListItem } from "./api";
import { ligarHls } from "./hls";

/// R21 — o menu de DVD.
///
/// A tese: **o menu é onde a caixa aberta já leva.** A R10 (§22) moveu o
/// "tocar" pra uma decisão consciente, mas ali havia sinopse pra ler. Um menu
/// que só *atrasa* o play sem informar nada é a intro que todo mundo pula.
/// Então ele não se mete no caminho de ninguém: o `▸ assistir` da biblioteca,
/// da busca e da ficha continua indo direto pro filme. O menu é o que acontece
/// quando você abre a caixa na locadora — e aí ele não é atraso, é o objeto.
///
/// **E só em DVD.** A fita não tem menu; ela tem rebobinar. A R19 (§35) já
/// tinha transformado a diferença de formato em comportamento, e esta é a
/// outra metade da mesma moeda.
///
/// ## O que a medição fez com a tela de cenas
///
/// Medido nos 548 filmes: **13,5% têm capítulos, 1,6% têm nomes de capítulo
/// úteis** e **zero têm folha de sprites** (as 725 que existem são de episódio
/// e YouTube). Um menu de capítulos feito de nomes funcionaria em nove filmes.
///
/// Então a grade de cenas é o principal e o capítulo é só uma âncora melhor
/// quando existe — que é o que "scene selection" sempre foi num DVD: uma grade
/// de miniaturas com timecode. A diferença entre os dois casos é invisível na
/// tela, e isso é correto.

/// Onde o menu está.
///
/// **A vinheta é uma fase**, e não um enfeite do menu: enquanto ela roda o menu
/// ainda não existe. Foi assim que os discos de 2004 faziam, e é o que separa
/// "abriu uma tela" de "pôs um disco".
type Fase = "vinheta" | "menu" | "capitulos" | "saindo";

/// O clima do disco: um por estante da locadora.
///
/// ## O bug que isto conserta
///
/// O sintetizador tinha **três variantes** — duas escalas e duas raízes, com
/// regex sobrepostos —, e o gênero chegava por um `SELECT … LIMIT 1` sem
/// ordenação. O resultado é o defeito relatado: *a música é igual em todos os
/// filmes*. Ela era mesmo: metade do acervo caía no mesmo par.
///
/// ## Por que doze, e por que estes doze
///
/// Porque a locadora já tinha resolvido a pergunta "qual é o gênero deste
/// filme". `ESTANTES` é uma lista ordenada em que o distintivo reivindica
/// primeiro, e é por isso que *Alien* mora em ficção científica e não em drama.
/// O servidor manda o índice dessa estante; aqui ele vira som e cor.
///
/// A coerência é de graça e vale mais que a variedade: **o filme que mora na
/// estante de terror abre um menu de terror**, e quem passou pela prateleira
/// reconhece o clima antes de ler o título.
///
/// ## Sobre a paleta
///
/// O §12 fechou a paleta do aplicativo, e isto é uma exceção deliberada — não
/// um esquecimento. A decisão do `IDEIAS.md` §3.7 é explícita: *"o estilo sai
/// da temática do filme — comédia e terror não ganham o mesmo menu"*. Um menu
/// de disco não é cromo do produto; é a arte da edição especial, e ela nunca
/// combinou com o resto da estante.
interface Clima {
  /// Graus da escala, em semitons.
  escala: number[];
  /// A fundamental, em Hz.
  raiz: number;
  /// Segundos por nota. É o andamento, e é o que mais muda o caráter.
  passo: number;
  /// O timbre da melodia e o do colchão.
  voz: OscillatorType;
  pad: OscillatorType;
  /// Corte do filtro passa-baixa, em Hz. Grave e abafado assusta; aberto anima.
  corte: number;
  /// A tinta do menu. Entra por variável CSS e não substitui a cor do filme —
  /// convive com ela.
  tinta: string;
  /// O desenho da vinheta. Quatro formas, e cada uma serve a três ou quatro
  /// climas: doze animações distintas seriam doze coisas pra manter.
  vinheta: "risco" | "iris" | "onda" | "brilho";
}

/// Um por estante, na ordem de `ESTANTES` (locadora.rs). O índice **é** o
/// contrato: mexer na ordem de lá sem mexer aqui troca o clima de todo mundo.
const CLIMAS: Clima[] = [
  // 0 · Terror — frígio, grave, lento, filtro fechado. O modo mais escuro que
  // uma escala de sete notas oferece sem virar dissonância gratuita.
  { escala: [0, 1, 3, 5, 7, 8, 10], raiz: 82.41, passo: 0.72, voz: "triangle", pad: "sawtooth", corte: 430, tinta: "#8c1c1c", vinheta: "risco" },
  // 1 · Faroeste — mixolídio, seco, notas espaçadas.
  { escala: [0, 2, 4, 5, 7, 9, 10], raiz: 110.0, passo: 0.66, voz: "triangle", pad: "triangle", corte: 900, tinta: "#c08a3e", vinheta: "onda" },
  // 2 · Guerra — menor natural, andamento de marcha, timbre duro.
  { escala: [0, 2, 3, 5, 7, 8, 10], raiz: 98.0, passo: 0.50, voz: "square", pad: "sawtooth", corte: 620, tinta: "#6f7a52", vinheta: "risco" },
  // 3 · Documentário — dórico, calmo, senoide. Não comenta o que mostra.
  { escala: [0, 2, 3, 5, 7, 9, 10], raiz: 130.81, passo: 0.80, voz: "sine", pad: "sine", corte: 1100, tinta: "#5b7f95", vinheta: "brilho" },
  // 4 · Animação — pentatônica maior, aguda, saltitante.
  { escala: [0, 2, 4, 7, 9, 12, 14], raiz: 174.61, passo: 0.34, voz: "triangle", pad: "triangle", corte: 1800, tinta: "#d97ab0", vinheta: "iris" },
  // 5 · Infantil — maior, aguda, redonda.
  { escala: [0, 2, 4, 5, 7, 9, 11], raiz: 196.0, passo: 0.38, voz: "sine", pad: "triangle", corte: 1600, tinta: "#e0b04a", vinheta: "iris" },
  // 6 · Ficção científica — tons inteiros: nenhuma nota "resolve", e é por isso
  // que ele soa suspenso. O truque mais barato de sci-fi que existe.
  { escala: [0, 2, 4, 6, 8, 10, 12], raiz: 146.83, passo: 0.58, voz: "sine", pad: "sawtooth", corte: 1400, tinta: "#4fb3c8", vinheta: "brilho" },
  // 7 · Ação e aventura — maior, rápido, serra aberta.
  { escala: [0, 2, 4, 5, 7, 9, 11], raiz: 130.81, passo: 0.30, voz: "sawtooth", pad: "sawtooth", corte: 1500, tinta: "#d9762b", vinheta: "risco" },
  // 8 · Crime e suspense — menor harmônica: a sétima maior dentro do menor é a
  // tensão sem susto, que é exatamente o gênero.
  { escala: [0, 2, 3, 5, 7, 8, 11], raiz: 110.0, passo: 0.62, voz: "triangle", pad: "sawtooth", corte: 700, tinta: "#4a5f9e", vinheta: "onda" },
  // 9 · Comédia — maior, curto e staccato.
  { escala: [0, 2, 4, 5, 7, 9, 11], raiz: 164.81, passo: 0.36, voz: "square", pad: "triangle", corte: 1700, tinta: "#e08a5a", vinheta: "iris" },
  // 10 · Romance — maior com sexta, lento, macio.
  { escala: [0, 2, 4, 7, 9, 11, 12], raiz: 155.56, passo: 0.86, voz: "sine", pad: "sine", corte: 1000, tinta: "#c4708c", vinheta: "brilho" },
  // 11 · Drama — menor natural, lento, sóbrio. É também o sumidouro: filme sem
  // etiqueta nenhuma cai aqui, e este é o clima que menos afirma coisa alguma.
  { escala: [0, 2, 3, 5, 7, 8, 10], raiz: 123.47, passo: 0.78, voz: "sine", pad: "sine", corte: 800, tinta: "#a08258", vinheta: "onda" },
];

const clima = (i: number | undefined): Clima => CLIMAS[i ?? 11] ?? CLIMAS[11];

/// A música do menu, sintetizada.
///
/// O §12 recusou CDN de fonte e ficou com a serifa do sistema, *"zero bytes"* —
/// e a mesma régua vale pro som. Um loop `.ogg` por gênero custaria ~200 KB
/// cada, mais escolher e licenciar. Web Audio custa **zero bytes**, e é
/// historicamente correto: aqueles menus **eram** sequenciados, não gravados.
class Sintetizador {
  private ctx: AudioContext | null = null;
  private mestre: GainNode | null = null;
  private relogio = 0;
  private timer: number | undefined;
  private compasso = 0;

  /// Quantas notas tem a frase. Oito é o que o ouvido guarda sem cansar, e
  /// **é o que faz o loop ser costurado**: a frase recomeça exatamente onde
  /// terminou, no mesmo grau, em vez de ser um bloco novo a cada agendamento.
  private static readonly FRASE = 8;

  tocar(c: Clima) {
    if (this.ctx) return;
    // `AudioContext` só nasce depois de um gesto do usuário — e aqui sempre
    // há um: o menu foi aberto por um clique.
    const Ctx = window.AudioContext ?? (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext;
    if (!Ctx) return;
    this.ctx = new Ctx();
    this.mestre = this.ctx.createGain();
    this.mestre.gain.value = 0;
    this.mestre.connect(this.ctx.destination);
    // Entra em fade: um menu que começa a tocar de estalo assusta.
    this.mestre.gain.linearRampToValueAtTime(0.075, this.ctx.currentTime + 2.5);

    this.relogio = this.ctx.currentTime + 0.2;
    this.compasso = 0;

    // O pad: duas ondas levemente desafinadas atrás de um filtro baixo. É o
    // colchão que todo menu de disco tinha, e ele **nunca para** — é o que
    // costura o loop: a melodia recomeça, o colchão não tem emenda.
    const pad = this.ctx.createGain();
    pad.gain.value = 0.5;
    const filtro = this.ctx.createBiquadFilter();
    filtro.type = "lowpass";
    filtro.frequency.value = c.corte;
    pad.connect(filtro).connect(this.mestre);
    for (const desafino of [-5, 5]) {
      const o = this.ctx.createOscillator();
      o.type = c.pad;
      o.frequency.value = c.raiz;
      o.detune.value = desafino;
      o.connect(pad);
      o.start();
    }
    // Uma quinta acima, bem baixa: dá corpo sem escolher modo — a quinta é a
    // única nota que soa igual em maior e menor.
    const quinta = this.ctx.createOscillator();
    const gq = this.ctx.createGain();
    gq.gain.value = 0.18;
    quinta.type = c.pad;
    quinta.frequency.value = c.raiz * 1.5;
    quinta.connect(gq).connect(filtro);
    quinta.start();

    // O arpejo, agendado em frases. Agendar tudo de uma vez encheria a fila;
    // agendar a cada nota dependeria do `setTimeout` chegar na hora — e ele não
    // chega. Uma frase por vez compra folga sem acumular, e o contador de
    // compasso é o que faz a frase seguinte **continuar** a anterior em vez de
    // recomeçar do primeiro grau.
    const agendar = () => {
      if (!this.ctx || !this.mestre) return;
      for (let i = 0; i < Sintetizador.FRASE; i++) {
        const n = this.compasso * Sintetizador.FRASE + i;
        const grau = c.escala[(n * 2) % c.escala.length];
        // A cada quatro notas uma sobe uma oitava, e a cada duas frases o
        // padrão desloca — é o mínimo pra oito notas não virarem um bipe.
        const oitava = n % 4 === 3 ? 2 : 1;
        const solto = this.compasso % 2 === 1 && i === Sintetizador.FRASE - 1;
        this.nota(
          c.raiz * oitava * Math.pow(2, grau / 12),
          this.relogio,
          c.passo * (solto ? 1.8 : 0.9),
          c.voz,
        );
        this.relogio += c.passo;
      }
      this.compasso++;
      this.timer = window.setTimeout(agendar, c.passo * Sintetizador.FRASE * 1000 * 0.8);
    };
    agendar();
  }

  private nota(freq: number, quando: number, dur: number, voz: OscillatorType) {
    if (!this.ctx || !this.mestre) return;
    const o = this.ctx.createOscillator();
    const g = this.ctx.createGain();
    o.type = voz;
    o.frequency.value = freq;
    g.gain.setValueAtTime(0, quando);
    g.gain.linearRampToValueAtTime(voz === "square" ? 0.12 : 0.22, quando + 0.02);
    g.gain.exponentialRampToValueAtTime(0.0001, quando + dur);
    o.connect(g).connect(this.mestre);
    o.start(quando);
    o.stop(quando + dur + 0.05);
  }

  parar() {
    window.clearTimeout(this.timer);
    const ctx = this.ctx;
    const mestre = this.mestre;
    this.ctx = null;
    this.mestre = null;
    if (!ctx || !mestre) return;
    // Sai em fade e só então fecha: cortar o contexto no meio estala.
    mestre.gain.linearRampToValueAtTime(0, ctx.currentTime + 0.4);
    window.setTimeout(() => ctx.close().catch(() => {}), 600);
  }
}

const SOM_KEY = "odeon.menu.som";

export function relogio(segundos: number): string {
  const s = Math.max(0, Math.round(segundos));
  const h = Math.floor(s / 3600);
  const m = Math.floor((s % 3600) / 60);
  const r = String(s % 60).padStart(2, "0");
  return h > 0 ? `${h}:${String(m).padStart(2, "0")}:${r}` : `${m}:${r}`;
}

export default function MenuDVD({
  workId,
  aoTocar,
  aoFechar,
}: {
  workId: string;
  /// Tocar a partir de um ponto. `0` é do começo.
  aoTocar: (obra: WorkListItem, de: number) => void;
  aoFechar: () => void;
}) {
  const [disco, setDisco] = useState<MenuDoDisco | null>(null);
  const [erro, setErro] = useState<string | null>(null);
  const [fase, setFase] = useState<Fase>("vinheta");
  const [cenas, setCenas] = useState<Cena[] | null>(null);
  const [carregandoCenas, setCarregandoCenas] = useState(false);
  const [foco, setFoco] = useState(0);
  const [som, setSom] = useState(() => localStorage.getItem(SOM_KEY) !== "off");

  const video = useRef<HTMLVideoElement>(null);
  const sinte = useRef<Sintetizador | null>(null);
  const sessao = useRef<string | null>(null);
  /// As janelinhas de vídeo dos itens. Uma por item, preenchidas do mesmo
  /// `<video>` — ver `useEffect` da pintura.
  const janelas = useRef<(HTMLCanvasElement | null)[]>([]);

  const c = clima(disco?.clima);

  // -------------------------------------------------------------- a vinheta
  //
  // **Toda vez, e dá pra pular.** É o que os discos bons faziam; os ruins eram
  // os que travavam o controle. Dois segundos e meio é o tempo de uma vinheta
  // de estúdio — longo o bastante pra ser vista, curto o bastante pra não virar
  // pedágio quando você já sabe o que quer.
  useEffect(() => {
    if (fase !== "vinheta") return;
    const t = window.setTimeout(() => setFase("menu"), 2500);
    const pular = () => setFase("menu");
    window.addEventListener("keydown", pular);
    window.addEventListener("pointerdown", pular);
    return () => {
      window.clearTimeout(t);
      window.removeEventListener("keydown", pular);
      window.removeEventListener("pointerdown", pular);
    };
  }, [fase]);

  useEffect(() => {
    let vivo = true;
    api
      .menuDoDisco(workId)
      .then((d) => vivo && setDisco(d))
      .catch((e) => vivo && setErro(String(e)));
    return () => {
      vivo = false;
    };
  }, [workId]);

  // ---------------------------------------------------------------- a música
  useEffect(() => {
    if (!disco || !som) return;
    const s = new Sintetizador();
    sinte.current = s;
    s.tocar(c);
    return () => {
      s.parar();
      sinte.current = null;
    };
  }, [disco, som, c]);

  // ------------------------------------------------------- a cena de fundo
  //
  // Uma sessão HLS com offset e sem áudio — o que a emissora (§25) faz desde
  // a R13, e o §8g já garantia que `-ss` antes do `-i` torna o offset
  // instantâneo. **Só começa depois de 900ms**: abrir e fechar o menu num
  // gesto não deve deixar um ffmpeg pra trás.
  useEffect(() => {
    if (!disco) return;
    let vivo = true;
    let desligar: (() => void) | undefined;

    const t = window.setTimeout(() => {
      api
        .startSession(disco.media_file_id, Math.round(disco.cena_de_fundo))
        .then((s) => {
          if (!vivo) {
            api.stopSession(s.id).catch(() => {});
            return;
          }
          sessao.current = s.id;
          const v = video.current;
          if (!v) return;
          const r = ligarHls(v, s.playlist_url, () => {
            /* fundo é enfeite: se o HLS falhar, o menu segue com o backdrop */
          });
          if (typeof r !== "string") desligar = r;
        })
        .catch(() => {
          /* sem fundo em movimento, e sem barulho na tela por causa disso */
        });
    }, 900);

    return () => {
      vivo = false;
      window.clearTimeout(t);
      desligar?.();
      // Sem isto o ffmpeg do fundo fica vivo até o reaper passar.
      if (sessao.current) {
        api.stopSession(sessao.current).catch(() => {});
        sessao.current = null;
      }
    };
  }, [disco]);

  // ------------------------------------------------- o vídeo dentro dos itens
  //
  // *"Vídeo rodando dentro dos itens do menu"* — o efeito que separa um menu de
  // 2004 de uma lista de botões. Cada item é uma **janela** para o filme, e
  // cada uma mostra um pedaço diferente do mesmo quadro.
  //
  // ## Por que canvas e não um `<video>` por item
  //
  // Quatro elementos de vídeo são quatro decodificações do mesmo fluxo — e o
  // fluxo aqui é HLS transcodificado ao vivo, ou seja, quatro sessões de ffmpeg
  // pra mostrar o mesmo plano. Um `<video>` só, pintado em quatro canvas
  // pequenos, custa uma decodificação e quatro `drawImage` de ~200×60px.
  //
  // O recorte é o que faz cada janela ser diferente: o item `i` mostra uma
  // faixa horizontal deslocada do quadro, então a mesma cena rende quatro
  // aberturas distintas em vez de quatro cópias.
  useEffect(() => {
    if (fase !== "menu") return;
    let vivo = true;
    let quadro = 0;

    const pintar = () => {
      if (!vivo) return;
      quadro = requestAnimationFrame(pintar);
      const v = video.current;
      if (!v || v.readyState < 2 || !v.videoWidth) return;

      janelas.current.forEach((cv, i) => {
        if (!cv) return;
        const ctx = cv.getContext("2d");
        if (!ctx) return;
        // A largura do recorte acompanha a proporção da janela, pra imagem não
        // esticar; a altura é uma faixa fina, que é o formato do item.
        const alturaFonte = v.videoHeight / 5;
        const larguraFonte = alturaFonte * (cv.width / cv.height);
        const x = (v.videoWidth - larguraFonte) / 2;
        // Cada item pega uma faixa diferente, de cima pra baixo.
        const y = ((i + 1) / (janelas.current.length + 1)) * (v.videoHeight - alturaFonte);
        ctx.drawImage(v, x, y, larguraFonte, alturaFonte, 0, 0, cv.width, cv.height);
      });
    };

    quadro = requestAnimationFrame(pintar);
    return () => {
      vivo = false;
      cancelAnimationFrame(quadro);
    };
  }, [fase]);

  const abrirCapitulos = useCallback(() => {
    setFase("capitulos");
    setFoco(0);
    if (cenas || carregandoCenas) return;
    setCarregandoCenas(true);
    api
      .cenasDoDisco(workId)
      .then(setCenas)
      .catch(() => setCenas([]))
      .finally(() => setCarregandoCenas(false));
  }, [workId, cenas, carregandoCenas]);

  const tocarDe = useCallback(
    (de: number) => {
      if (!disco) return;
      setFase("saindo");
      // O que o player espera. O menu não inventa uma segunda forma de obra —
      // manda o mínimo que `Player` usa e deixa o resto com quem já sabe.
      aoTocar(
        {
          id: disco.work_id,
          title: disco.titulo,
          year: disco.ano,
          media_file_id: disco.media_file_id,
          poster: null,
          dominant_color: disco.cor,
        } as WorkListItem,
        de,
      );
    },
    [disco, aoTocar],
  );

  /// Os itens do menu, na ordem em que um disco os tinha.
  ///
  /// "Continuar" só existe quando há de onde continuar. Um item morto no menu
  /// principal é a linha que o §24 manda sumir — e aqui ela seria pior, porque
  /// um menu de DVD tem quatro itens e um deles cinza é 25% de tela desperdiçada.
  const itens = useMemo(() => {
    if (!disco) return [];
    const l: { rotulo: string; nota?: string; acao: () => void }[] = [];
    if (disco.posicao) {
      l.push({
        rotulo: "Continuar",
        nota: relogio(disco.posicao),
        acao: () => tocarDe(disco.posicao!),
      });
    }
    l.push({ rotulo: disco.posicao ? "Do começo" : "Tocar", acao: () => tocarDe(0) });
    l.push({
      rotulo: "Capítulos",
      // O número só aparece quando o disco o declarou. Sem ele a grade ainda
      // existe — são doze, e é o que ela mostra —, mas anunciar "12 capítulos"
      // pra uma divisão feita pelo relógio seria dizer que o filme tem doze.
      nota: disco.capitulos.length > 1 ? `${disco.capitulos.length} no disco` : undefined,
      acao: abrirCapitulos,
    });
    if (disco.legendas.length) {
      // Não é item de menu: é ficha. Escolher legenda continua no player, onde
      // já funciona — dois seletores seriam dois lugares pra manter iguais.
      l.push({ rotulo: "Legendas", nota: disco.legendas.join(" · "), acao: () => {} });
    }
    return l;
  }, [disco, tocarDe, abrirCapitulos]);

  // ------------------------------------------------------------ o controle
  //
  // Setas e Enter, porque um menu de DVD se navega com controle remoto — e é
  // essa a alma da coisa. O mouse continua funcionando; ele só não é o único.
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") {
        if (fase === "capitulos") return (setFase("menu"), setFoco(0));
        return aoFechar();
      }
      const alvos = fase === "capitulos" ? (cenas?.length ?? 0) : itens.length;
      if (!alvos) return;
      const colunas = fase === "capitulos" ? 4 : 1;
      const passo =
        e.key === "ArrowDown" ? colunas
        : e.key === "ArrowUp" ? -colunas
        : e.key === "ArrowRight" && fase === "capitulos" ? 1
        : e.key === "ArrowLeft" && fase === "capitulos" ? -1
        : 0;
      if (passo) {
        e.preventDefault();
        setFoco((f) => Math.max(0, Math.min(alvos - 1, f + passo)));
        return;
      }
      if (e.key === "Enter") {
        e.preventDefault();
        if (fase === "capitulos") {
          const cap = cenas?.[foco];
          if (cap) tocarDe(cap.segundos);
        } else itens[foco]?.acao();
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [fase, foco, itens, cenas, aoFechar, tocarDe]);

  const alternarSom = () => {
    setSom((s) => {
      localStorage.setItem(SOM_KEY, s ? "off" : "on");
      return !s;
    });
  };

  if (erro) {
    return (
      <div className="menu-dvd" onClick={aoFechar}>
        <p className="error">{erro}</p>
      </div>
    );
  }

  return (
    <div
      className={[
        "menu-dvd",
        `clima-${c.vinheta}`,
        fase === "saindo" ? "saindo" : "",
        fase === "capitulos" ? "nas-cenas" : "",
        fase === "vinheta" ? "na-vinheta" : "",
      ]
        .filter(Boolean)
        .join(" ")}
      style={{
        ["--cor" as string]: disco?.cor ?? "#3a3a44",
        // A tinta do clima convive com a cor do filme; ela não a substitui.
        // Um menu de terror de um filme azul continua sendo daquele filme.
        ["--tinta" as string]: c.tinta,
      }}
    >
      {/* O fundo, em três camadas: o backdrop que já está em disco desde o M1
          (aparece na hora), a cena em movimento por cima (chega depois), e o
          véu que devolve contraste ao texto. */}
      {disco?.backdrop && (
        <img className="menu-parado" src={api.artworkUrl(disco.backdrop)} alt="" />
      )}
      <video ref={video} className="menu-cena" muted playsInline autoPlay />
      <div className="menu-veu" />

      {/* A VINHETA. Ela não é uma camada por cima do menu: enquanto roda, o
          menu ainda não existe — foi assim que os discos faziam, e é o que
          separa "abriu uma tela" de "pôs um disco". Quatro desenhos servem os
          doze climas; doze animações seriam doze coisas pra manter. */}
      {fase === "vinheta" && (
        <div className="vinheta">
          <div className="vinheta-marca">
            <span className="vinheta-forma" />
            <span className="vinheta-titulo">{disco?.titulo ?? ""}</span>
            {disco && <span className="vinheta-clima">{disco.clima_nome}</span>}
          </div>
          <span className="vinheta-pular">qualquer tecla pula</span>
        </div>
      )}

      {/* O PALCO. Menu e capítulos são duas telas **no mesmo espaço**, e a
          transição move a câmera de uma pra outra em vez de trocar o conteúdo.
          É a "viagem até a tela de capítulos" que os discos de edição especial
          faziam — e ela só é possível porque as duas existem ao mesmo tempo.

          **Ele não existe durante a vinheta**, e isso não é otimização: um
          palco montado atrás dela aparecia por baixo, com o título do filme
          legível enquanto a vinheta ainda rodava — e aí a vinheta vira um
          borrão sobre um menu já aberto, que é o oposto de pôr um disco. */}
      {fase !== "vinheta" && (
      <div className="menu-palco">
        <div className="menu-conteudo">
          <header className="menu-cabeca">
            <h2>{disco?.titulo ?? "…"}</h2>
            <p>
              {[disco?.ano, disco?.duracao ? relogio(disco.duracao) : null]
                .filter(Boolean)
                .join(" · ")}
            </p>
          </header>

          <nav className="menu-itens">
            {itens.map((it, i) => (
              <button
                key={it.rotulo}
                className={`menu-item${i === foco ? " focado" : ""}`}
                onMouseEnter={() => setFoco(i)}
                onClick={it.acao}
              >
                {/* A janela: o filme rodando dentro do item. Um canvas e não um
                    vídeo — quatro vídeos seriam quatro sessões de ffmpeg do
                    mesmo plano. */}
                <canvas
                  className="menu-janela"
                  width={360}
                  height={72}
                  ref={(el) => {
                    janelas.current[i] = el;
                  }}
                />
                <span className="menu-seta">▸</span>
                <span className="menu-rotulo">{it.rotulo}</span>
                {it.nota && <i>{it.nota}</i>}
              </button>
            ))}
          </nav>
        </div>

        <div className="menu-cenas">
          <div className="cenas-topo">
            <h3>Capítulos</h3>
            <span>
              {/* A origem dita a legenda, e a legenda diz a verdade: com
                  capítulo, os pontos são do disco; sem, foi o relógio que
                  dividiu. A palavra é a mesma nos dois casos — o Odeon não
                  está dizendo que o arquivo declarou, está dividindo o filme —,
                  mas de onde veio o corte continua sendo dito. */}
              {carregandoCenas
                ? "procurando os capítulos…"
                : cenas?.length
                  ? cenas[0].origem === "capitulo"
                    ? "nos cortes do disco"
                    : "divididos pelo relógio"
                  : "este disco não rendeu capítulos"}
            </span>
            <button className="cenas-voltar" onClick={() => (setFase("menu"), setFoco(0))}>
              voltar ao menu
            </button>
          </div>

          <div className="cenas-grade">
            {carregandoCenas &&
              /* Doze molduras vazias, e não um "carregando": a grade já ocupa o
                 lugar dela, então quando os capítulos chegam nada salta. */
              Array.from({ length: 12 }, (_, i) => <div key={i} className="cena vazia" />)}
            {cenas?.map((cap, i) => (
              <button
                key={cap.imagem}
                className={`cena${i === foco ? " focada" : ""}`}
                onMouseEnter={() => setFoco(i)}
                onClick={() => tocarDe(cap.segundos)}
              >
                <img src={api.artworkUrl(cap.imagem)} alt="" loading="lazy" />
                {/* O número é o que faz a grade ser de capítulos e não de
                    quadros soltos — é assim que um disco os apresentava. */}
                <b className="cena-n">{i + 1}</b>
                <span>{relogio(cap.segundos)}</span>
              </button>
            ))}
          </div>
        </div>
      </div>
      )}

      <div className="menu-rodape">
        <span className="menu-dica">setas navegam · enter escolhe · esc fecha</span>
        <button className="menu-som" onClick={alternarSom} title="música do menu">
          {som ? "♪ som" : "♪̸ mudo"}
        </button>
        <button className="menu-sair" onClick={aoFechar}>
          fechar
        </button>
      </div>
    </div>
  );
}
