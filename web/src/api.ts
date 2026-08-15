const API_KEY_STORAGE = "odeon.server";

/**
 * De onde falar com a API.
 *
 * Ordem: o que o usuário salvou → `VITE_API_URL` do build → deduzido da página.
 *
 * A dedução importa mais do que parece: **uma página HTTPS não pode chamar uma
 * API HTTP** (o navegador bloqueia como mixed content, e isso inclui
 * `<video src>`). Então o esquema da API acompanha o da página, e a porta muda
 * junto: 8443 sob HTTPS, 8080 sob HTTP.
 */
// Porta da API em HTTP. O padrão do projeto é 8080, mas nesta máquina ela já
// está ocupada por outro serviço (ver API_PORT no .env), então o Odeon subiu na
// 8085. Deduzir 8080 daqui mandaria a web falar com o container errado.
//
// Continua sendo dedução e não URL fixa: o hostname sai da própria página, então
// funciona por localhost, por IP da LAN ou por qualquer nome de VPN, sem
// reconfigurar nada.
const HTTP_PORT = 8085;
const HTTPS_PORT = 8443;

function deriveFromPage(): string {
  const { protocol, hostname, port } = window.location;
  // Servido pela própria API (mesma origem): usa a origem inteira.
  if (port === String(HTTP_PORT) || port === String(HTTPS_PORT)) {
    return window.location.origin;
  }
  const secure = protocol === "https:";
  return `${secure ? "https" : "http"}://${hostname}:${secure ? HTTPS_PORT : HTTP_PORT}`;
}

function resolveApi(): string {
  const saved = localStorage.getItem(API_KEY_STORAGE);
  if (saved) return saved;
  const fromEnv = import.meta.env.VITE_API_URL;
  if (fromEnv) return fromEnv;
  return deriveFromPage();
}

export let API = resolveApi();

/** Troca o servidor em uso e recarrega — o token é por servidor. */
export function setServer(url: string) {
  const trimmed = url.trim().replace(/\/+$/, "");
  localStorage.setItem(API_KEY_STORAGE, trimmed);
  localStorage.removeItem("odeon.token");
  API = trimmed;
}

export function clearServer() {
  localStorage.removeItem(API_KEY_STORAGE);
}

/**
 * O erro que, sem explicação, parece "servidor fora do ar".
 * `null` quando não há problema.
 */
export function mixedContentProblem(
  serverUrl = API,
  // Parâmetro em vez de ler `window` direto: senão o caso que importa (página
  // segura + servidor inseguro) só seria observável servindo a UI em HTTPS.
  pageIsSecure = window.location.protocol === "https:",
): string | null {
  if (pageIsSecure && serverUrl.startsWith("http://")) {
    return (
      "esta página está em HTTPS e o servidor em HTTP — o navegador bloqueia a mistura " +
      "(inclusive o vídeo). Use https:// no endereço do servidor, ou abra esta interface por HTTP."
    );
  }
  return null;
}

const TOKEN_KEY = "odeon.token";
const MEDIA_KEY = "odeon.media";

/**
 * O token vive no localStorage e vai em `Authorization: Bearer`.
 *
 * Cookie seria mais seguro (httpOnly), mas web e API estão em origens
 * diferentes e cookie cross-origin exige `SameSite=None; Secure` — ou seja,
 * HTTPS, que não existe numa tailnet HTTP. O servidor manda o cookie mesmo
 * assim, pra quando web e API forem servidas da mesma origem.
 */
export const auth = {
  token: (): string | null => localStorage.getItem(TOKEN_KEY),
  setToken: (value: string) => localStorage.setItem(TOKEN_KEY, value),
  clear: () => {
    localStorage.removeItem(TOKEN_KEY);
    // O de mídia sai junto: uma sessão encerrada que deixasse bytes acessíveis
    // por oito horas seria um "sair" que não sai. O servidor faz o mesmo do
    // lado dele, no `revoke`.
    localStorage.removeItem(MEDIA_KEY);
  },
};

export interface AuthUser {
  id: string;
  username: string;
  display_name: string;
  role: string;
  is_active: boolean;
  last_login_at: string | null;
}

/// O mínimo que o servidor aceita (`MIN_PASSWORD_LEN`, no `auth/mod.rs`).
///
/// A tela usa isto pra não gastar uma ida ao servidor com uma senha que ela já
/// sabe que vai voltar recusada. O servidor continua sendo quem decide — este
/// número é uma cópia da regra dele, não a regra.
export const SENHA_MINIMA = 8;

/// O máximo que o nome aceita (o `CHECK` da migração 0038).
///
/// Mesma natureza da `SENHA_MINIMA`: cópia da regra do servidor, pra tela poder
/// cortar no `maxLength` em vez de deixar digitar 200 caracteres e devolver um
/// 400 depois do clique.
export const NOME_MAXIMO = 40;

/// O que volta de `POST /api/auth/password`.
///
/// A `note` é do servidor, e ela diz que "as outras sessões foram encerradas" —
/// mas o `DELETE` de lá não poupa a de quem pediu. Quem faz a frase virar
/// verdade é o cliente, entrando de novo com a senha nova logo depois.
export interface TrocaDeSenha {
  ok: boolean;
  note?: string;
}

/** Sessão morreu (expirou ou foi revogada): a UI volta pro login. */
export class Unauthorized extends Error {
  constructor() {
    super("sessão expirada");
  }
}

/**
 * O token de mídia: curto, e **só abre bytes**.
 *
 * `<video src>`, `<img src>` e `<track src>` não mandam header, então o token
 * precisa ir na URL — e URL vaza pra log de acesso e histórico de navegador.
 * Até a R27 o que ia ali era o token de sessão: 90 dias, acesso total à API.
 *
 * Agora é um token separado, que vence em horas e não serve pra mais nada. Ele
 * fica em memória e no `localStorage` pela mesma razão que o de sessão: um
 * recarregamento não pode obrigar a buscar tudo de novo antes do primeiro
 * pôster aparecer.
 */
let emCurso: Promise<void> | null = null;

// ------------------------------------------------- o barramento, um só (R46)

const ouvintes = new Set<(evento: Record<string, unknown>) => void>();
let barramento: EventSource | null = null;
let tentativasDoBarramento = 0;
let religar: number | undefined;

async function abrirBarramento() {
  if (barramento) return;
  await midia.garantir();
  // O último ouvinte pode ter ido embora enquanto o token era buscado.
  if (ouvintes.size === 0 || barramento) return;

  const fonte = new EventSource(api.eventsUrl());
  barramento = fonte;

  fonte.onopen = () => {
    // Uma conexão que vingou zera a conta: a próxima queda recomeça rápido.
    tentativasDoBarramento = 0;
  };

  fonte.onmessage = (m) => {
    let evento: Record<string, unknown>;
    try {
      evento = JSON.parse(m.data);
    } catch {
      return; /* evento malformado não derruba a tela */
    }
    // Cópia da lista: um ouvinte que se desinscreve ao receber não pode
    // quebrar a entrega pros outros.
    for (const ouvinte of [...ouvintes]) {
      try {
        ouvinte(evento);
      } catch {
        /* um ouvinte com defeito não cala os outros */
      }
    }
  };

  fonte.onerror = () => {
    fonte.close();
    if (barramento === fonte) barramento = null;
    if (ouvintes.size === 0 || tentativasDoBarramento >= 5) return;
    const espera = 1000 * 2 ** tentativasDoBarramento;
    tentativasDoBarramento += 1;
    // **Reconecta com o token que estiver valendo — sem forçar um novo.**
    // Emitir um token aposenta o anterior (§43), e o anterior é o que está
    // dentro do `<video src=…?token=>` e de cada `<img>` da página. Medido: o
    // segundo participante de uma sala recebia "o navegador recusou o arquivo
    // mesmo com plano de Direct Play" — era o barramento derrubando o filme
    // dele. Quando o token foi trocado por outra aba, o novo já está no
    // `localStorage`; quando expirou de vez, `garantir()` emite um.
    religar = window.setTimeout(() => void abrirBarramento(), espera);
  };
}

function fecharBarramento() {
  window.clearTimeout(religar);
  barramento?.close();
  barramento = null;
  tentativasDoBarramento = 0;
}

export const midia = {
  token: (): string | null => localStorage.getItem(MEDIA_KEY),
  set: (value: string) => localStorage.setItem(MEDIA_KEY, value),
  clear: () => localStorage.removeItem(MEDIA_KEY),

  /// Garante que existe token de mídia **antes** de quem depende dele começar.
  ///
  /// R44 — o defeito que isto conserta: o `renovar()` do boot não é esperado
  /// por ninguém ("a arte carrega quando ele chegar"), e pra uma `<img>` isso
  /// é verdade — ela recarrega. Pra um `EventSource` não é: ele monta a URL
  /// **uma vez**, e sem token toma 401 e reconecta pra sempre, em silêncio.
  ///
  /// Quem abria o Odeon direto numa tela com barramento nunca recebia evento
  /// nenhum. Quem passeava por outras abas antes recebia — o token tinha
  /// chegado no meio do caminho. Daí o defeito parecer intermitente.
  garantir: async (): Promise<void> => {
    if (midia.token()) return;
    // Uma renovação só, mesmo com quatro componentes pedindo ao mesmo tempo.
    emCurso ??= midia.renovar().finally(() => {
      emCurso = null;
    });
    await emCurso;
  },

  /// Pede um token novo. Chamado no boot e depois que a sessão nasce — e o
  /// servidor aposenta os anteriores do mesmo usuário ao emitir.
  renovar: async (): Promise<void> => {
    try {
      const r = await json<{ token: string }>("/api/auth/media-token", { method: "POST" });
      midia.set(r.token);
    } catch {
      /* sem token de mídia a arte não carrega, mas a API continua de pé —
         e a próxima tentativa acontece no próximo boot. */
    }
  },
};

/**
 * `<video src>`, `<img src>` e `<track src>` não mandam header. O servidor
 * aceita `?token=` só nas rotas de mídia — e desde a R27 **só aceita o token
 * de mídia ali**, nunca o de sessão.
 */
function withToken(url: string): string {
  const token = midia.token();
  if (!token) return url;
  return url + (url.includes("?") ? "&" : "?") + `token=${encodeURIComponent(token)}`;
}

/// Identidade do aparelho, estável entre recarregamentos. Serve pro SSE: cada
/// device ignora o próprio eco em vez de brigar com a própria atualização.
export const DEVICE_ID = (() => {
  const key = "odeon.device";
  let id = localStorage.getItem(key);
  if (!id) {
    id = `${navigator.platform || "web"}-${Math.random().toString(36).slice(2, 10)}`;
    localStorage.setItem(key, id);
  }
  return id;
})();

export interface SpriteInfo {
  media_file_id: string;
  path: string;
  interval_seconds: number;
  columns: number;
  rows: number;
  thumb_width: number;
  thumb_height: number;
  frame_count: number;
}

export interface ScrubStatus {
  running: boolean;
  current: string | null;
  total: number;
  done: number;
  failed: number;
  errors: string[];
}

export type AppEvent =
  | {
      type: "progress";
      work_id: string;
      position_seconds: number;
      duration_seconds: number | null;
      finished: boolean;
      device_id: string;
    }
  | { type: "scan_finished"; added: number; updated: number }
  | { type: "match_finished"; auto: number; needs_review: number }
  | { type: "scrub_finished"; done: number; failed: number }
  | {
      type: "programme_starting";
      programme_id: number;
      channel_id: string;
      channel_name: string;
      title: string;
      starts_at: string;
      user_id: string;
    }
  /// R33: chegou mensagem. **Só o aviso** — o barramento é aberto a todos os
  /// aparelhos autenticados, então o texto não vem por aqui. Cada cliente
  /// descarta o que não é seu pelo `para`.
  | {
      type: "mensagem";
      de: string;
      de_nome: string;
      para: string;
    }
  /// R19: alguém mexeu na locadora.
  ///
  /// Um evento pras quatro ações porque quem ouve faz a mesma coisa com todas —
  /// recarrega a prateleira. O que muda é a frase.
  ///
  /// Carregava um `circulo_id` que ninguém filtrava. Com uma loja só (R28), o
  /// que acontece nela acontece pra todo mundo que está nela.
  | {
      type: "locadora";
      acao: "pegou" | "devolveu" | "pediu" | "venceu";
      caixa_id: string | null;
      titulo: string | null;
      quem_nome: string | null;
    };

export interface WorkListItem {
  id: string;
  kind: string;
  title: string;
  year: number | null;
  season_number: number | null;
  episode_number: number | null;
  match_state: string;
  match_confidence: number | null;
  dominant_color: string | null;
  poster: string | null;
  /** Arte larga da obra; o herói do painel prefere ela ao pôster. */
  backdrop: string | null;
  /** Quadro do episódio, quando existe — mais específico que o backdrop. */
  still: string | null;
  series_title: string | null;
  media_file_id: string | null;
  duration_seconds: number | null;
  width: number | null;
  height: number | null;
  video_codec: string | null;
  audio_codec: string | null;
  container: string | null;
  size_bytes: number | null;
  position_seconds: number | null;
  finished: boolean | null;
  tags: string[] | null;
}

/**
 * Uma entrada da biblioteca: ou uma **série inteira**, ou uma obra avulsa.
 *
 * As séries já existiam no grafo desde o M1 — a tela é que mostrava os 14.657
 * episódios como cartões iguais, que é listagem de arquivo e não biblioteca.
 */
export interface LibraryEntry {
  id: string;
  is_series: boolean;
  title: string;
  year: number | null;
  poster: string | null;
  dominant_color: string | null;
  work_count: number;
  season_count: number;
  finished_count: number;
  media_file_id: string | null;
  duration_seconds: number | null;
  height: number | null;
  size_bytes: number | null;
  kind: string | null;
  match_state: string | null;
  position_seconds: number | null;

  /**
   * As versões deste filme — os rips que o servidor agrupou numa entrada só.
   *
   * ⚠️ **A chave é omitida quando há uma versão só**, que é o caso de 8.230 das
   * 8.273 entradas. São 43 grupos no acervo inteiro (medido em 14/08/2026:
   * 8.316 entradas viraram 8.273). A regra da tela fica a mais simples que
   * existe: *há versões, há escolha*.
   *
   * ⚠️ O agrupamento é por `external_ids->>'tmdb'` — a identificação, nunca
   * título+ano. Só `kind='movie'` e só o que já foi identificado. Série não
   * ganha versões: o tmdb é compartilhado pelos episódios.
   *
   * ⚠️ E `?versions=flat` devolve as 8.316 sem agrupar, pra a revisão do acervo
   * continuar enxergando rip por rip.
   */
  versions?: Versao[];

  /**
   * Repetido em toda linha — é o total de entradas do filtro atual.
   *
   * ⚠️ **Conta grupos desde 14/08/2026**, e não rips: a grade escreve
   * `mostrando / total` e o «carregar mais» calcula `total - mostrando`. Se o
   * total contasse 8.316 enquanto a grade desenha 8.273 cartões, o botão
   * prometeria uma página que não existe.
   */
  total: number;
}

/**
 * Uma versão de um filme — um dos rips que o servidor agrupou.
 *
 * O dono baixou alguns filmes duas vezes, um em pt-BR e outro em inglês, porque
 * não achou dual audio. A escolha entre eles é de **idioma**, e é por isso que o
 * `audio_langs` está aqui. Ver `android/docs/PEDIDOS-AO-SERVIDOR.md` §2.1.
 *
 * ⚠️ **Ela não é um `MediaFileSummary`.** Aquele é um arquivo dentro de uma
 * obra; esta é uma **obra inteira** — id próprio, progresso próprio, ficha
 * própria. Confundi-las é o caminho pra fundir o que só devia ser agrupado, e
 * fundir apagaria o `position_seconds` de uma delas.
 */
export interface Versao {
  /** O id da **obra**, e é por ele que a ficha abre. */
  id: string;
  media_file_id: string | null;
  height: number | null;
  size_bytes: number | null;
  duration_seconds: number | null;
  /**
   * Os idiomas do áudio, em ISO 639 — `["por"]`.
   *
   * ⚠️ **Vem vazio quando o arquivo não declara**, e é o caso do 007 em inglês
   * deste acervo. O servidor recusa mandar `und` (que em ISO 639 quer dizer
   * *undetermined*), e a recusa é a certa: escrever «und» como idioma faria a
   * tela oferecer «und» como escolha.
   *
   * ⚠️ A consequência é de produto — a escolha consegue dizer «Português» e
   * **não** consegue dizer «Inglês». Quem conserta é o acervo, declarando o
   * idioma da faixa no arquivo.
   */
  audio_langs: string[];
  /** Onde **este** usuário parou nesta versão. É o que distingue as duas quando
   * o idioma não foi declarado. */
  position_seconds: number | null;
  finished: boolean;
}

/** Um canal com o que está no ar nele agora. */
export interface CanalNoAr {
  id: string;
  name: string;
  number: string | null;
  logo_url: string | null;
  grupo: string | null;
  titulo: string | null;
  sub_titulo: string | null;
  comeca: string | null;
  termina: string | null;
  a_seguir: string | null;
  programme_id: number | null;
  /** Arte da obra ligada ao programa — só quando o casamento foi seguro. */
  arte: string | null;
  /** A obra e o arquivo dela: é o que "ver desde o início" toca. */
  work_id: string | null;
  media_file_id: string | null;
}

export interface ProgramaDoGuia {
  id: number;
  channel_id: string;
  starts_at: string;
  ends_at: string;
  title: string;
  sub_title: string | null;
  description: string | null;
  year: number | null;
  categoria: string | null;
  arte: string | null;
  /** A obra na sua biblioteca, quando o casamento foi seguro. */
  work_id: string | null;
  /** O arquivo dela — o que "ver desde o início" toca. */
  media_file_id: string | null;
  lembrete: boolean;
}

export interface Lembrete {
  programme_id: number;
  title: string;
  starts_at: string;
  channel_id: string;
  channel_name: string;
}

/** A grade traz o relógio DO SERVIDOR: a agulha do "agora" tem que ser
 *  desenhada contra o mesmo relógio que produziu a grade. */
export interface Guia {
  agora: string;
  ate: string;
  programas: ProgramaDoGuia[];
}

export interface FonteAoVivo {
  id: string;
  name: string;
  m3u_url: string;
  xmltv_url: string | null;
  enabled: boolean;
  last_import_at: string | null;
  last_error: string | null;
  canais: number;
}

export interface CanalAberto {
  channel: { id: string; name: string };
  session_id: string;
  playlist_url: string;
  mode: PlaybackMode;
  reasons: string[];
}

export interface Tag {
  id: string;
  namespace: string;
  value: string;
  color: string | null;
  work_count: number;
}

export interface WorkTag {
  id: string;
  namespace: string;
  value: string;
  color: string | null;
  source: string;
}

export interface TagNamespace {
  namespace: string;
  label: string;
  color: string | null;
  position: number;
}

export interface Collection {
  id: string;
  kind: string;
  parent_id: string | null;
  title: string;
  year: number | null;
  overview: string | null;
  description: string | null;
  position: number | null;
  origin: string;
  provider_key: string | null;
  /** Obras na subárvore inteira — não só filhos diretos. */
  item_count: number;
  /** Até quatro pôsteres da subárvore, pra capa empilhada do cartão. */
  posters: string[] | null;
}

export interface CollectionNode extends Collection {
  children: CollectionNode[];
}

export interface Relation {
  kind: string;
  label: string | null;
  position: number | null;
  direction: "in" | "out";
  other_id: string;
  other_title: string;
  other_year: number | null;
  other_poster: string | null;
}

/// Vem de `/api/works/{id}`, que achata a tabela `work` — projeção diferente da
/// listagem, então não estende WorkListItem (lá `tags` é `string[]`, aqui é o
/// objeto completo com origem e cor).
/// O arquivo por trás da obra, com o que o probe do M0 extraiu. Vem no
/// `GET /api/works/{id}` desde sempre; a UI só passou a olhar na R7.
export interface MediaFileSummary {
  id: string;
  path: string;
  filename: string;
  size_bytes: number;
  container: string | null;
  duration_seconds: number | null;
  bitrate: number | null;
  video_codec: string | null;
  width: number | null;
  height: number | null;
  frame_rate: number | null;
  audio_codec: string | null;
  audio_channels: number | null;
  subtitle_langs: string[];
  status: string;
}

export interface WorkDetail {
  id: string;
  kind: string;
  title: string;
  original_title: string | null;
  year: number | null;
  overview: string | null;
  runtime_seconds: number | null;
  season_number: number | null;
  episode_number: number | null;
  match_state: string;
  match_confidence: number | null;
  dominant_color: string | null;
  /// `{poster, backdrop, still}` — caminhos relativos servidos em `/artwork/`.
  artwork: Record<string, string>;
  external_ids: Record<string, string>;
  files: MediaFileSummary[];
  /// Onde este usuário parou, em segundos. `0` se nunca começou.
  position_seconds: number;
  finished: boolean;
  tags: WorkTag[];
  collections: Collection[];
  relations: Relation[];
  credits: Credit[];
}

export interface ContaUsuario {
  id: string;
  username: string;
  display_name: string;
  role: string;
  is_active: boolean;
  created_at: string;
  last_login_at: string | null;
}

export interface Aparelho {
  id: string;
  device_label: string | null;
  user_agent: string | null;
  created_at: string;
  last_seen_at: string;
  expires_at: string;
}

export interface Trabalho {
  id: string;
  kind: string;
  state: string;
  started_at: string | null;
  finished_at: string | null;
  done: number | null;
  total: number | null;
  current: string | null;
  error: string | null;
  progress: Record<string, unknown> | null;
  cancel_requested: boolean;
}

/// R46 — uma sala de assistir junto.
export interface Sala {
  id: string;
  work_id: string;
  titulo: string;
  poster: string | null;
  media_file_id: string | null;
  host_id: string;
  host_nome: string;
  /// Se eu sou o host. A tela não decide isso comparando ids.
  sou_host: boolean;
  modo: "por_pessoa" | "compartilhado";
  transcode_id: string | null;
  /// A intenção do host.
  tocando: boolean;
  /// O que toca de verdade: a intenção **e** todo mundo pronto.
  rodando: boolean;
  /// Quem está segurando a sala agora, pelo nome.
  esperando: string[];
  posicao_segundos: number;
  atualizado_em: string;
  gente: {
    user_id: string;
    username: string;
    display_name: string;
    pronto: boolean;
    host: boolean;
    ausente: boolean;
  }[];
  conversa: { id: number; user_id: string; display_name: string; texto: string; em: string }[];
}

export interface SalaAberta {
  id: string;
  host_id: string;
  host_nome: string;
  titulo: string;
  poster: string | null;
  gente: number;
}

export interface TasteProfile {
  works_touched: number;
  finished: number;
  abandoned: number;
  tag_affinity: [string, number][];
  preferred_minutes: [number, number] | null;
  hour_histogram: number[];
  has_taste_vector: boolean;
}

export interface Recommendation extends WorkListItem {
  score: number;
  reasons: string[];
}

export interface ForYou {
  profile: TasteProfile;
  items: Recommendation[];
  cold_start: boolean;
}

export interface EmbedStatus {
  running: boolean;
  total: number;
  done: number;
  skipped: number;
  corpus_terms: number;
  errors: string[];
}

export type PlaybackMode = "direct_play" | "direct_stream" | "transcode";

export interface PlaybackPlan {
  mode: PlaybackMode;
  video: "copy" | "encode";
  audio: "copy" | "encode";
  target_height: number | null;
  burn_subtitle: number | null;
  reasons: string[];
  direct_url: string | null;
  subtitles: SubtitleTrack[];
}

export interface SubtitleTrack {
  /** Negativo = legenda em arquivo ao lado do vídeo. */
  index: number;
  /** `embutida` ou `arquivo`. */
  origem: string;
  codec: string;
  language: string | null;
  title: string | null;
  forced: boolean;
  default: boolean;
  text_based: boolean;
  /** ASS/SSA: tem estilo que o WebVTT não representa. */
  styled: boolean;
  /** Rótulo pronto, vindo do servidor — não reimplemente aqui. */
  label: string;
}

export interface TranscodeSession {
  id: string;
  media_file_id: string;
  start_seconds: number;
  encoder: string;
  mode: PlaybackMode;
  reasons: string[];
  playlist_url: string;
}

/**
 * O que ESTE navegador realmente toca, perguntado a ele.
 *
 * Lista fixa mentiria nos dois sentidos: o Safari toca HEVC e receberia
 * transcode à toa; um navegador velho receberia um arquivo que não abre.
 */
export function detectCapabilities(): Record<string, string> {
  const probe = document.createElement("video");
  const can = (type: string) => probe.canPlayType(type) !== "";

  const video: string[] = [];
  if (can('video/mp4; codecs="avc1.42E01E"')) video.push("h264");
  if (can('video/mp4; codecs="hvc1.1.6.L93.B0"') || can('video/mp4; codecs="hev1.1.6.L93.B0"'))
    video.push("hevc");
  if (can('video/webm; codecs="vp8"')) video.push("vp8");
  if (can('video/webm; codecs="vp9"')) video.push("vp9");
  if (can('video/mp4; codecs="av01.0.05M.08"')) video.push("av1");

  const audio: string[] = [];
  if (can('audio/mp4; codecs="mp4a.40.2"')) audio.push("aac");
  if (can("audio/mpeg")) audio.push("mp3");
  if (can('audio/ogg; codecs="opus"') || can('audio/webm; codecs="opus"')) audio.push("opus");
  if (can('audio/mp4; codecs="ac-3"')) audio.push("ac3");
  if (can('audio/mp4; codecs="ec-3"')) audio.push("eac3");
  if (can("audio/flac")) audio.push("flac");

  const containers = ["mp4", "mov"];
  if (can("video/webm")) containers.push("webm");

  return {
    containers: containers.join(","),
    video_codecs: video.join(","),
    audio_codecs: audio.join(","),
    supports_hls: "true",
  };
}

export interface Credit {
  person_id: string;
  name: string;
  role: string;
  role_label: string;
  character_name: string | null;
  position: number | null;
  image_path: string | null;
  featured: boolean;
  role_position: number;
}

export interface Person {
  id: string;
  name: string;
  known_for: string | null;
  image_path: string | null;
  work_count: number;
}

export interface PersonDetail {
  person: Person;
  roles: { role: string; label: string; count: number }[];
  works: WorkListItem[];
}

/// R18 — o guia de cinema.
///
/// A diferença pro `Person` acima é o que vem junto: `terminadas` e `comecadas`
/// são **suas**, não da pessoa. Sem elas isto seria uma lista de créditos, que
/// qualquer site tem.
///
/// `obras` conta **títulos**, não obras: uma série inteira é um título só. Ver
/// o cabeçalho de `src/routes/guia.rs` **no repositório do servidor** pro que
/// aconteceu antes desse rollup existir — desde a R51 ele mora em outro lugar.
export interface PessoaDoGuia {
  id: string;
  name: string;
  image_path: string | null;
  known_for: string | null;
  obras: number;
  terminadas: number;
  comecadas: number;
  posters: string[] | null;
  total: number;
}

/// Um eixo que não é pessoa: gênero ou década.
export interface FaixaDoGuia {
  rotulo: string;
  /// O que vai pro filtro da biblioteca: `genre:Terror`, ou o ano da década.
  chave: string;
  obras: number;
  posters: string[] | null;
}

/// Uma curiosidade sobre a obra, montada no servidor.
///
/// O texto vem pronto de propósito: escrever a frase no cliente seria manter
/// uma segunda gramática, e o backend já monta os `reasons` do score assim
/// desde o M1. `tipo` serve pro ícone, não pra reescrever nada.
export interface Curiosidade {
  tipo: string;
  texto: string;
  /// "Wikidata" ou "Wikipédia" no que vem de fora; ausente no que sai do
  /// próprio acervo. Crédito não é enfeite: a Wikipédia é CC BY-SA.
  fonte?: string;
  fonte_url?: string;
}

/// R34 — a revista da semana.
///
/// A capa do guia: um tema sorteado do acervo, os filmes que se encaixam, o
/// ensaio (quando há chave do LLM) e o evento em cartaz. **Igual pra todo
/// mundo**, e virando na mesma segunda-feira que a vitrine da locadora — é o
/// que dá assunto em comum (`IDEIAS.md` §2.4).
export interface FilmeDaCapa {
  id: string;
  titulo: string;
  ano: number | null;
  poster: string | null;
  diretor: string | null;
  /// A única coisa da capa que é sua.
  visto: boolean;
}

export interface EventoDaSemana {
  tipo: "obra" | "saga";
  id: string;
  titulo: string;
  poster: string | null;
  obras: number;
  suas: number;
  participou: boolean;
  participantes: string[];
}

export interface Revista {
  semana_de: string;
  vira_em: string;
  eixo: "genero" | "decada" | "pais" | "diretor" | "saga";
  tema: string;
  filmes: FilmeDaCapa[];
  /// `null` quando não há chave do LLM ou o texto ainda não foi gerado. A tela
  /// **omite a seção** — não mostra "carregando" nem inventa prosa (§18, §24).
  ensaio: string | null;
  /// O selo. Quem lê tem direito de saber que aquele parágrafo não foi escrito
  /// por gente — a mesma regra do crédito `WIKIPÉDIA` das curiosidades (§32).
  ensaio_por: string | null;
  evento: EventoDaSemana | null;
}

export interface GuiaEixos {
  direcao: PessoaDoGuia[];
  elenco: PessoaDoGuia[];
  trilha: PessoaDoGuia[];
  generos: FaixaDoGuia[];
  decadas: FaixaDoGuia[];
  /// R22: de onde os filmes vêm. Só países com 2 obras ou mais — dos 33 do
  /// acervo, 10 têm um filme só, e um país com uma obra não é prateleira.
  paises: FaixaDoGuia[];
  /// Quantos filmes NÃO são dos Estados Unidos.
  ///
  /// Vem junto porque sem ele o eixo diz "Estados Unidos 491" e o resto vira
  /// rodapé. Este é o número que faz a região valer uma seção: é a pergunta
  /// que ninguém conseguia fazer antes da R22.
  fora_de_hollywood: number;
}

export interface Library {
  id: string;
  name: string;
  root_path: string;
  default_kind: string;
  provider_hint: string;
  created_at: string;
}

/// R49 — o que uma pasta **parece** ser, lido dos nomes dos arquivos dentro.
///
/// `null` quando os nomes não dizem: a tela omite em vez de chutar (§18).
export type Palpite = "filme" | "serie" | "mistura";

/// R49 — a pasta já está numa biblioteca, e o servidor recusa as duas direções.
export interface Cobertura {
  biblioteca: string;
  /** `true`: esta pasta está DENTRO da biblioteca. `false`: ela CONTÉM uma. */
  dentro: boolean;
}

export interface BrowseEntry {
  name: string;
  path: string;
  video_count: number;
  /** R49 — e quantos nas subpastas dela, que é onde episódio mora. */
  videos_abaixo: number;
  has_subdirs: boolean;
  coberta_por: Cobertura | null;
  palpite: Palpite | null;
  /** A contagem parou no teto: a tela mostra "+" em vez de mentir. */
  truncado: boolean;
}

export interface BrowseListing {
  path: string;
  /** null quando já se está numa raiz — a UI esconde o "subir". */
  parent: string | null;
  roots: string[];
  entries: BrowseEntry[];
  video_count: number;
  videos_abaixo: number;
  coberta_por: Cobertura | null;
  palpite: Palpite | null;
  truncado: boolean;
}

export interface Filters {
  q?: string;
  kind?: string;
  tags?: string[];
  tagMode?: "all" | "any";
  yearFrom?: number;
  yearTo?: number;
  minMinutes?: number;
  maxMinutes?: number;
  collection?: string;
  /** Só pra tela: o nome da coleção em que se entrou, pro caminho de volta. */
  collectionName?: string;
  state?: string;
  person?: string;
  personName?: string;
  sort?: string;
}

/** Os tipos de aresta que o CHECK do banco aceita, com rótulo em português. */
export const RELATION_KINDS: Record<string, string> = {
  sequel_of: "é sequência de",
  prequel_of: "é prequela de",
  remake_of: "é remake de",
  alternate_cut_of: "é corte alternativo de",
  watch_order: "ordem de exibição",
  related: "relacionado a",
};

/** Os `work.kind` do CHECK do 0001, em português. */
export const WORK_KINDS: Record<string, string> = {
  movie: "filme",
  episode: "episódio",
  short: "curta",
  standup: "stand-up",
  concert: "show",
  documentary: "documentário",
  music_video: "clipe",
  other: "avulso",
};

export const COLLECTION_KINDS: Record<string, string> = {
  series: "série",
  season: "temporada",
  franchise: "franquia",
  playlist: "playlist",
  watch_order: "ordem de exibição",
  custom: "coleção",
};

export interface ScanStatus {
  running: boolean;
  library: string | null;
  current_file: string | null;
  files_seen: number;
  files_added: number;
  files_updated: number;
  files_missing: number;
  errors: string[];
  started_at: string | null;
  finished_at: string | null;
}

export interface MatchStatus {
  /// R55 — quantas obras ainda não entraram nos contadores da biblioteca.
  /// Elas foram varridas e não identificadas, e é a tag `format:` (que só a
  /// identificação escreve) que os contadores filtram. Fora da conta: o que
  /// nunca vai ser identificado (`provider_hint: none`) e o que já foi
  /// decidido (`ignored`) — pendência é o que ainda pode entrar.
  nao_identificadas: number;
  running: boolean;
  tmdb_enabled: boolean;
  current: string | null;
  works_seen: number;
  matched_auto: number;
  needs_review: number;
  still_unmatched: number;
  errors: string[];
  started_at: string | null;
  finished_at: string | null;
}

export interface MatchCandidate {
  id: string;
  work_id: string;
  provider: string;
  provider_id: string;
  provider_kind: string;
  title: string;
  original_title: string | null;
  year: number | null;
  overview: string | null;
  poster_url: string | null;
  backdrop_url: string | null;
  score: number;
  reasons: string[];
}

export interface GuessView {
  title: string;
  year: number | null;
  season: number | null;
  episode: number | null;
  absolute_episode: number | null;
  release_group: string | null;
  looks_like_anime: boolean;
}

export interface ScopeRecord {
  id: string;
  provider: string;
  provider_id: string;
  provider_kind: string;
  season_number: number | null;
  numbering: string;
  absolute_offset: number;
  note: string | null;
  decided_at: string;
}

export interface SiblingMatch {
  provider: string;
  provider_id: string;
  titulo: string;
  obras: number;
}

export interface ScopeRow {
  dir_path: string;
  library_id: string;
  library_name: string;
  pendentes: number;
  unmatched: number;
  needs_review: number;
  ja_identificados: number;
  exemplos: string[];
  titulo_sugerido: string;
  sibling_match: SiblingMatch | null;
  escopo: ScopeRecord | null;
}

export interface ScopePage {
  total: number;
  limit: number;
  offset: number;
  items: ScopeRow[];
}

export interface ScopeIdentifyBody {
  library_id: string;
  dir_path: string;
  recursive?: boolean;
  provider: string;
  provider_id: string;
  provider_kind: string;
  season_number?: number | null;
  numbering?: string;
  absolute_offset?: number;
  note?: string | null;
  dry_run?: boolean;
}

export interface ScopePreviewRow {
  work_id: string;
  arquivo: string;
  temporada: number | null;
  episodio: number | null;
  titulo_resolvido: string | null;
  estado: string;
  motivos: string[];
}

export interface ScopePreview {
  dry_run: boolean;
  pasta: string;
  afetados?: number;
  confirmariam?: number;
  ficariam_em_revisao?: number;
  chamadas_de_temporada: number;
  preview?: ScopePreviewRow[];
  aplicados?: number;
  falhas?: { arquivo: string; erro: string }[];
}

export interface ReviewPage {
  total: number;
  limit: number;
  offset: number;
  /// Contagem por `match_state`, vinda do BANCO — não do status em memória,
  /// que zerava a cada restart do processo.
  counts: Record<string, number>;
  items: ReviewItem[];
}

export interface ReviewItem {
  work: {
    id: string;
    title: string;
    year: number | null;
    kind: string;
    season_number: number | null;
    episode_number: number | null;
    match_state: string;
    match_confidence: number | null;
    /// Por que a obra está na fila, quando o motivo não veio de um candidato:
    /// propagação de escopo por pasta, ou identificação desfeita por
    /// contradizer o provider. Mesma regra de auditabilidade dos `reasons` do
    /// score, estendida às decisões que não passam por candidato.
    match_reasons: string[];
    filename: string;
  };
  guess: GuessView;
  candidates: MatchCandidate[];
}

async function json<T>(path: string, init?: RequestInit): Promise<T> {
  const token = auth.token();
  const res = await fetch(`${API}${path}`, {
    ...init,
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(init?.headers ?? {}),
    },
  });
  if (res.status === 401) {
    auth.clear();
    throw new Unauthorized();
  }
  if (!res.ok) {
    const body = await res.text();
    throw new Error(`${res.status} ${path}: ${body}`);
  }
  return res.json() as Promise<T>;
}

/** Quantas entradas por página. O backend limita em 500. */
export const PAGE_SIZE = 120;

/// R47 — o perfil mudou **nesta aba**.
///
/// Não é o barramento: o barramento (`ouvirEventos`) carrega o que aconteceu no
/// servidor, e isto é o contrário — quem salvou foi você, aqui, e o cabeçalho
/// precisa saber sem perguntar de novo. Um `window` event e não um contexto do
/// React porque quem emite (`Perfil`) é filho de quem ouve (`BarraDeCima`), e um
/// estado subindo essa distância atravessaria seis telas que não têm nada a ver
/// com isso.
///
/// Sem ele o cabeçalho fica com o rosto velho até um F5, que é o §8b dito de
/// outro jeito: a tela mostrando o contrário do que acabou de acontecer.
export const PERFIL_MUDOU = "odeon:perfil";

/// R50 — o que eu posso assistir agora.
export interface Liberadas {
  /// A escassez está ligada, e o empréstimo virou condição pra dar play.
  exige: boolean;
  /// As obras cobertas pelos meus empréstimos em aberto. Vazia quando `exige`
  /// é falso — aí tudo é liberado, e mandar 17.498 ids pra dizer "sim" seria
  /// meio megabyte de nada.
  works: string[];
}

function queryString(filters: Filters, limit = PAGE_SIZE, offset = 0): string {
  const p = new URLSearchParams({ limit: String(limit) });
  if (offset > 0) p.set("offset", String(offset));
  if (filters.q?.trim()) p.set("q", filters.q.trim());
  if (filters.kind) p.set("kind", filters.kind);
  if (filters.tags?.length) {
    p.set("tags", filters.tags.join(","));
    p.set("tag_mode", filters.tagMode ?? "all");
  }
  if (filters.yearFrom) p.set("year_from", String(filters.yearFrom));
  if (filters.yearTo) p.set("year_to", String(filters.yearTo));
  if (filters.minMinutes) p.set("min_minutes", String(filters.minMinutes));
  if (filters.maxMinutes) p.set("max_minutes", String(filters.maxMinutes));
  if (filters.collection) p.set("collection", filters.collection);
  if (filters.person) p.set("person", filters.person);
  if (filters.state) p.set("state", filters.state);
  if (filters.sort) p.set("sort", filters.sort);
  return p.toString();
}

export const api = {
  works: (filters: Filters = {}, offset = 0) =>
    json<WorkListItem[]>(`/api/works?${queryString(filters, PAGE_SIZE, offset)}`),

  /**
   * A biblioteca agrupada: uma entrada por série, uma por obra avulsa.
   *
   * Cada linha carrega o `total` (via `count(*) OVER ()` no backend), então
   * "300 de 17.498" não custa uma segunda requisição.
   */
  library: (filters: Filters = {}, offset = 0, limit = PAGE_SIZE) =>
    json<LibraryEntry[]>(`/api/library?${queryString(filters, limit, offset)}`),

  continueWatching: () => json<WorkListItem[]>("/api/continue"),

  // --- R6: canais ao vivo ---
  liveChannels: () => json<CanalNoAr[]>("/api/live/channels"),
  liveGuide: (hours = 3) => json<Guia>(`/api/live/guide?hours=${hours}`),

  /// A grade dos canais que o próprio Odeon programa. Calculada, não guardada:
  /// duas chamadas no mesmo dia devolvem a mesma programação.
  liveOdeon: (hours = 5) =>
    json<{
      agora: string;
      ate: string;
      canais: { slug: string; nome: string; numero: string }[];
      programas: {
        id: string;
        canal: string;
        canal_nome: string;
        numero: string;
        work_id: string;
        media_file_id: string | null;
        title: string;
        year: number | null;
        arte: string | null;
        categoria: string | null;
        starts_at: string;
        ends_at: string;
      }[];
    }>(`/api/live/odeon?hours=${hours}`),
  liveSources: () => json<FonteAoVivo[]>("/api/live/sources"),
  createLiveSource: (name: string, m3u_url: string, xmltv_url?: string) =>
    json<{ id: string }>("/api/live/sources", {
      method: "POST",
      body: JSON.stringify({ name, m3u_url, xmltv_url: xmltv_url || null }),
    }),
  deleteLiveSource: (id: string) =>
    json<{ ok: boolean }>(`/api/live/sources/${id}`, { method: "DELETE" }),
  liveImport: () =>
    json<{ started: boolean; reason?: string; fontes?: number }>("/api/live/import", {
      method: "POST",
    }),
  watchChannel: (id: string) =>
    json<CanalAberto>(`/api/live/${id}/watch`, { method: "POST" }),
  reminders: () => json<Lembrete[]>("/api/live/reminders"),
  createReminder: (programmeId: number) =>
    json<{ ok: boolean; starts_at: string }>(`/api/live/reminders/${programmeId}`, {
      method: "POST",
    }),
  deleteReminder: (programmeId: number) =>
    json<{ ok: boolean }>(`/api/live/reminders/${programmeId}`, { method: "DELETE" }),

  scan: () => json<{ started: boolean; reason?: string }>("/api/scan", { method: "POST" }),

  scanStatus: () => json<ScanStatus>("/api/scan/status"),

  progress: (
    workId: string,
    payload: {
      position_seconds: number;
      duration_seconds?: number;
      media_file_id?: string;
      event_type?: string;
      client?: string;
    },
  ) =>
    json<{ ok: boolean; finished: boolean }>(`/api/works/${workId}/progress`, {
      method: "POST",
      body: JSON.stringify({ client: "web", device_id: DEVICE_ID, ...payload }),
    }),

  streamUrl: (mediaFileId: string) => withToken(`${API}/api/stream/${mediaFileId}`),

  artworkUrl: (path: string) => withToken(`${API}/artwork/${path}`),

  // --- M1: identidade ---

  match: (force = false) =>
    json<{ started: boolean; reason?: string }>(`/api/match?force=${force}`, { method: "POST" }),

  matchStatus: () => json<MatchStatus>("/api/match/status"),

  review: (
    params: {
      state?: string;
      library?: string;
      dir?: string;
      /// `true` = o matcher achou opções e não sabe qual;
      /// `false` = não achou nada, e o problema é o nome do arquivo.
      hasCandidates?: boolean;
      q?: string;
      sort?: string;
      limit?: number;
      offset?: number;
    } = {},
  ) => {
    const p = new URLSearchParams();
    if (params.state) p.set("state", params.state);
    if (params.library) p.set("library", params.library);
    if (params.dir) p.set("dir", params.dir);
    if (params.hasCandidates !== undefined)
      p.set("has_candidates", String(params.hasCandidates));
    if (params.q?.trim()) p.set("q", params.q.trim());
    if (params.sort) p.set("sort", params.sort);
    p.set("limit", String(params.limit ?? 50));
    p.set("offset", String(params.offset ?? 0));
    return json<ReviewPage>(`/api/review?${p}`);
  },

  searchCandidates: (workId: string, query: string, year?: number) =>
    json<MatchCandidate[]>(`/api/works/${workId}/search`, {
      method: "POST",
      body: JSON.stringify({ query, year: year ?? null, provider: "auto" }),
    }),

  confirmMatch: (workId: string, candidateId: string) =>
    json<{ ok: boolean; title: string }>(`/api/works/${workId}/match`, {
      method: "POST",
      body: JSON.stringify({ candidate_id: candidateId }),
    }),

  /// Desfaz a identificação. Simétrico: tira TUDO que veio do provider e
  /// preserva o que é humano — tag manual, playlist, e o override de parse.
  resetMatch: (workId: string) =>
    json<{ ok: boolean; guess: GuessView }>(`/api/works/${workId}/reset`, {
      method: "POST",
    }),

  /// Corrige o que o parser entendeu, e GUARDA. Só os campos enviados mudam.
  /// Sobrevive a confirm, re-scan e re-match — é decisão humana, não resultado.
  setParse: (
    workId: string,
    parse: {
      title?: string;
      year?: number | null;
      season?: number | null;
      episode?: number | null;
      absolute_episode?: number | null;
    },
  ) =>
    json<{ ok: boolean; guess: GuessView }>(`/api/works/${workId}/parse`, {
      method: "POST",
      body: JSON.stringify(parse),
    }),

  /// A saúde do servidor: o que está torto e ninguém tinha como ver.
  ///
  /// `diagnostico` e não `health`: `/api/health` é outra coisa — o liveness,
  /// que responde sem autenticação e só diz se o processo está de pé.
  diagnostico: () =>
    json<{
      arquivos: {
        total: number;
        com_erro: number;
        sumidos: number;
        amostra: { arquivo: string; estado: string }[];
      };
      identificacao: { revisar: number; sem_identificacao: number; ignoradas: number };
      sprites: { prontos: number; de: number };
      ao_vivo: {
        horas_de_grade: number | null;
        fontes: { nome: string; erro: string | null; ultimo_import: string | null }[];
      };
    }>("/api/diagnostico"),

  /// O que este servidor consegue fazer com os arquivos. `pode_apagar` sai de
  /// uma escrita de teste de verdade, não da configuração.
  storage: () =>
    json<{
      pode_apagar: boolean;
      motivo: string | null;
      raizes: { path: string; existe: boolean; gravavel: boolean }[];
    }>("/api/storage"),

  /// Apaga a obra. Com `apagarArquivos`, apaga os arquivos antes — e se algum
  /// se recusar, nada sai do catálogo.
  deleteWork: (workId: string, apagarArquivos: boolean) =>
    json<{
      ok: boolean;
      titulo: string;
      arquivos_apagados: number;
      bytes_liberados: number;
      aviso: string | null;
    }>(`/api/works/${workId}?apagar_arquivos=${apagarArquivos}`, { method: "DELETE" }),

  /// Some da biblioteca sem apagar nada.
  ignoreWork: (workId: string, reason?: string) =>
    json<{ ok: boolean }>(`/api/works/${workId}/ignore`, {
      method: "POST",
      body: JSON.stringify({ reason: reason ?? null }),
    }),

  clearParse: (workId: string) =>
    json<{ ok: boolean; guess: GuessView }>(`/api/works/${workId}/parse`, {
      method: "DELETE",
    }),

  // --- identificação por PASTA ---
  //
  // A unidade de decisão não é o arquivo. Medido no acervo real: 7.568 arquivos
  // por identificar em apenas 578 pastas, e a pasta acerta a série em 97% dos
  // casos. Uma escolha resolve centenas de arquivos.

  reviewScopes: (params: {
    q?: string;
    library?: string;
    sort?: string;
    limit?: number;
    offset?: number;
  } = {}) => {
    const p = new URLSearchParams();
    if (params.q?.trim()) p.set("q", params.q.trim());
    if (params.library) p.set("library", params.library);
    if (params.sort) p.set("sort", params.sort);
    p.set("limit", String(params.limit ?? 50));
    p.set("offset", String(params.offset ?? 0));
    return json<ScopePage>(`/api/review/scopes?${p}`);
  },

  scopeSearch: (dirPath: string, query?: string, provider?: string) =>
    json<{ consultado: string; candidatos: MatchCandidate[] }>("/api/scopes/search", {
      method: "POST",
      body: JSON.stringify({ dir_path: dirPath, query, provider }),
    }),

  /// `dryRun` é o padrão do servidor e a UI nunca oferece aplicar sem antes
  /// mostrar o preview — escrever 500 obras sem ver o que vai acontecer é o
  /// oposto do que o projeto defende.
  scopeIdentify: (body: ScopeIdentifyBody) =>
    json<ScopePreview>("/api/scopes/identify", {
      method: "POST",
      body: JSON.stringify(body),
    }),

  // --- M2: o grafo ---

  tags: () => json<Tag[]>("/api/tags"),

  tagNamespaces: () => json<TagNamespace[]>("/api/tag-namespaces"),

  detail: (workId: string) => json<WorkDetail>(`/api/works/${workId}`),

  attachTag: (workId: string, namespace: string, value: string) =>
    json<WorkTag[]>(`/api/works/${workId}/tags`, {
      method: "POST",
      body: JSON.stringify({ namespace, value, color: null }),
    }),

  detachTag: (workId: string, tagId: string) =>
    json<WorkTag[]>(`/api/works/${workId}/tags/${tagId}`, { method: "DELETE" }),

  collectionTree: () => json<CollectionNode[]>("/api/collections/tree"),

  collection: (id: string) =>
    json<{ collection: Collection; children: Collection[]; items: WorkListItem[] }>(
      `/api/collections/${id}`,
    ),

  createCollection: (kind: string, title: string, description?: string) =>
    json<Collection>("/api/collections", {
      method: "POST",
      body: JSON.stringify({ kind, title, description: description ?? null }),
    }),

  deleteCollection: (id: string) =>
    json<{ ok: boolean }>(`/api/collections/${id}`, { method: "DELETE" }),

  addToCollection: (collectionId: string, workId: string) =>
    json<{ ok: boolean; position: number }>(`/api/collections/${collectionId}/items`, {
      method: "POST",
      body: JSON.stringify({ work_id: workId, position: null }),
    }),

  removeFromCollection: (collectionId: string, workId: string) =>
    json<{ ok: boolean }>(`/api/collections/${collectionId}/items/${workId}`, {
      method: "DELETE",
    }),

  reorderCollection: (collectionId: string, items: { work_id: string; position: number }[]) =>
    json<{ ok: boolean }>(`/api/collections/${collectionId}/order`, {
      method: "PUT",
      body: JSON.stringify({ items }),
    }),

  relations: (workId: string) => json<Relation[]>(`/api/works/${workId}/relations`),

  createRelation: (workId: string, toWork: string, kind: string, label?: string) =>
    json<Relation[]>(`/api/works/${workId}/relations`, {
      method: "POST",
      body: JSON.stringify({ to_work: toWork, kind, label: label ?? null, position: null }),
    }),

  deleteRelation: (workId: string, other: string, kind: string) =>
    json<Relation[]>(`/api/works/${workId}/relations/${other}/${kind}`, { method: "DELETE" }),

  // --- M3: a alma ---

  scrub: (force = false) =>
    json<{ started: boolean; reason?: string }>(`/api/scrub?force=${force}`, { method: "POST" }),

  scrubStatus: () => json<ScrubStatus>("/api/scrub/status"),

  /// 404 quando o sprite ainda não foi gerado — o player degrada sem preview.
  ///
  /// Esta rota EXIGE credencial, e não está entre as que aceitam `?token=` na
  /// query (ver `accepts_query_token` no backend: só mídia buscada por elemento
  /// HTML entra lá; esta é JSON buscada por JS, que tem header disponível).
  /// Sem o header ela devolvia 401, `res.ok` era falso, e o player concluía
  /// "não há sprite" — silenciosamente, para TODO arquivo. Os sprites que já
  /// existiam no banco nunca chegaram a aparecer.
  ///
  /// Só o 404 vira `null`. O 401 continua sendo erro de verdade: mascará-lo foi
  /// exatamente o que escondeu este bug.
  spriteInfo: async (mediaFileId: string): Promise<SpriteInfo | null> => {
    const token = auth.token();
    const res = await fetch(`${API}/api/media/${mediaFileId}/scrub`, {
      headers: token ? { Authorization: `Bearer ${token}` } : {},
    });
    if (res.status === 404) return null;
    if (res.status === 401) {
      auth.clear();
      throw new Unauthorized();
    }
    if (!res.ok) throw new Error(`${res.status} scrub: ${await res.text()}`);
    return (await res.json()) as SpriteInfo;
  },

  spriteUrl: (path: string) => withToken(`${API}/scrub/${path}`),

  // EventSource também não manda header.
  eventsUrl: () => withToken(`${API}/api/events`),

  /// Ouve o barramento. **Uma conexão pro aplicativo inteiro.**
  ///
  /// ## Os quatro defeitos que isto conserta (R44 e R46)
  ///
  /// 1. **`EventSource` não manda header**, então a URL leva `?token=`. E
  ///    `/api/events` não estava na lista de rotas que aceitam token na query:
  ///    401 em toda conexão, com reconexão infinita e silenciosa.
  ///
  /// 2. **A URL é congelada na criação.** O `renovar()` do boot não é esperado
  ///    por ninguém — pra uma `<img>` tudo bem, ela recarrega; pra um
  ///    `EventSource` não, ele nasce sem token e morre assim pra sempre. Por
  ///    isso `garantir()` vem antes.
  ///
  /// 3. **Emitir um token de mídia aposenta o anterior** (§43): duas abas, um
  ///    modo estrito que monta duas vezes, ou oito horas de sessão derrubam a
  ///    conexão aberta com o velho. Daí a reconexão — que **não força um token
  ///    novo**, porque forçar mataria o `<video src=…?token=>` da página.
  ///
  /// 4. **Uma conexão, e não uma por componente.** O navegador dá **seis**
  ///    conexões por host em HTTP/1.1, e um `EventSource` segura a dele pra
  ///    sempre. Com quatro telas abrindo a sua, sobrava quase nada — e o
  ///    sintoma foi o pior possível: no segundo participante de uma sala, o
  ///    `fetch` do plano de reprodução **ficava trinta segundos pendurado**, o
  ///    vídeo nunca carregava, e a sala inteira esperava por ele. O comentário
  ///    do `events.rs` já dizia o desenho certo desde o M3: *"o navegador já
  ///    mantém UM `EventSource` aberto"*. Agora mantém mesmo.
  ///
  /// Assinar é barato: a conexão nasce no primeiro ouvinte e morre com o
  /// último.
  ouvirEventos: (aoReceber: (evento: Record<string, unknown>) => void): (() => void) => {
    ouvintes.add(aoReceber);
    if (ouvintes.size === 1) abrirBarramento();
    return () => {
      ouvintes.delete(aoReceber);
      if (ouvintes.size === 0) fecharBarramento();
    };
  },

  // --- R46: assistir junto ---

  /// A sala em que eu estou, se estiver em alguma.
  junto: () => json<Sala | null>("/api/junto"),
  /// As salas abertas dos meus amigos — o convite, sem tabela de convite.
  juntoAbertas: () => json<SalaAberta[]>("/api/junto/abertas"),
  criarJunto: (p: { work_id: string; media_file_id?: string | null; modo?: string }) =>
    json<Sala>("/api/junto", { method: "POST", body: JSON.stringify(p) }),
  entrarJunto: (id: string) => json<Sala>(`/api/junto/${id}/entrar`, { method: "POST" }),
  sairJunto: (id: string) =>
    json<{ encerrada: boolean }>(`/api/junto/${id}/sair`, { method: "POST" }),
  /// Só o host. O play, a pausa e o pulo da sala inteira.
  estadoJunto: (
    id: string,
    p: { tocando: boolean; posicao_segundos: number; transcode_id?: string | null },
  ) => json<Sala>(`/api/junto/${id}/estado`, { method: "PUT", body: JSON.stringify(p) }),
  /// "Estou carregando" / "voltei" — e batimento de vida.
  prontoJunto: (id: string, pronto: boolean) =>
    json<Sala>(`/api/junto/${id}/pronto`, { method: "PUT", body: JSON.stringify({ pronto }) }),
  recadoJunto: (id: string, texto: string) =>
    json<Sala>(`/api/junto/${id}/recado`, { method: "POST", body: JSON.stringify({ texto }) }),
  expulsarJunto: (id: string, quem: string) =>
    json<{ ok: boolean }>(`/api/junto/${id}/membro/${quem}`, { method: "DELETE" }),

  // --- M5: curadoria ---

  forYou: (minutes?: number, mood?: string) => {
    const p = new URLSearchParams({ limit: "24" });
    if (minutes) p.set("minutes", String(minutes));
    if (mood) p.set("mood", mood);
    return json<ForYou>(`/api/curation/for-you?${p}`);
  },

  /// O perfil + a contagem de votos. `curtidas`/`bloqueadas` são o que
  /// permite ao "para você" saber que a calibração já rendeu.
  taste: () => json<TasteProfile & { curtidas: number; bloqueadas: number }>("/api/curation/taste"),

  /// Seis capas pra calibrar o gosto — uma por gênero, nunca votadas.
  calibrar: () => json<WorkListItem[]>("/api/curation/calibrar"),

  // --- R16: administração ---
  //
  // Sete rotas que existiam no backend sem nenhum cliente. Quatro delas só
  // eram alcançáveis por `curl` — e duas foram entregues assim por mim.

  usuarios: () => json<ContaUsuario[]>("/api/auth/users"),
  criarUsuario: (body: {
    username: string;
    display_name?: string;
    password: string;
    role: "admin" | "user";
  }) => json<ContaUsuario>("/api/auth/users", { method: "POST", body: JSON.stringify(body) }),
  mudarUsuario: (
    id: string,
    body: { role?: "admin" | "user"; is_active?: boolean; display_name?: string },
  ) =>
    json<{ ok: boolean }>(`/api/auth/users/${id}`, {
      method: "PATCH",
      body: JSON.stringify(body),
    }),
  apagarUsuario: (id: string) =>
    json<{ ok: boolean }>(`/api/auth/users/${id}`, { method: "DELETE" }),

  sessoes: () => json<Aparelho[]>("/api/auth/sessions"),
  encerrarSessao: (id: string) =>
    json<{ ok: boolean }>(`/api/auth/sessions/${id}`, { method: "DELETE" }),

  trabalhos: (limit = 25) => json<Trabalho[]>(`/api/jobs?limit=${limit}`),
  cancelarTrabalho: (id: string) =>
    json<{ ok: boolean }>(`/api/jobs/${id}/cancel`, { method: "POST" }),

  /// Os três aquecimentos. R40 — as rotas existiam desde a R14, a R25 e a R32,
  /// e nenhuma tinha cliente: todas as três só eram alcançáveis por `curl`.
  ///
  /// Não há `dry_run` aqui, ao contrário das manutenções: um aquecimento não
  /// reescreve o acervo, ele preenche o que está vazio — e o que ele fez fica
  /// visível em "Trabalhos" enquanto acontece.
  aquecer: (qual: "trivia" | "producao" | "sagas") =>
    json<{ started: boolean; job_id?: string; reason?: string }>(
      `/api/maintenance/aquecer-${qual}`,
      { method: "POST" },
    ),

  /// As quatro manutenções. `dryRun` é o padrão porque é o padrão delas — e
  /// porque contar é inofensivo e reescrever milhares de linhas não é.
  manutencao: (
    qual: "repair-series" | "repair-episode-titles" | "reparse" | "artwork-orfao",
    dryRun = true,
  ) =>
    json<Record<string, unknown>>(
      `/api/maintenance/${qual}?dry_run=${dryRun}`,
      { method: "POST" },
    ),

  similar: (workId: string) => json<Recommendation[]>(`/api/works/${workId}/similar`),

  rebuildEmbeddings: () =>
    json<{ started: boolean }>("/api/curation/rebuild", { method: "POST" }),

  embedStatus: () => json<EmbedStatus>("/api/curation/rebuild/status"),

  feedback: (workId: string, verdict: "love" | "block" | "later") =>
    json<{ ok: boolean }>(`/api/works/${workId}/feedback`, {
      method: "POST",
      body: JSON.stringify({ verdict }),
    }),

  clearFeedback: (workId: string) =>
    json<{ ok: boolean }>(`/api/works/${workId}/feedback`, { method: "DELETE" }),

  // --- M6: playback pesado ---

  playbackPlan: (mediaFileId: string, burnSubtitle?: number) => {
    const p = new URLSearchParams(detectCapabilities());
    if (burnSubtitle != null) p.set("burn_subtitle", String(burnSubtitle));
    return json<PlaybackPlan>(`/api/playback/${mediaFileId}/plan?${p}`);
  },

  startSession: (mediaFileId: string, start = 0, burnSubtitle?: number) => {
    const p = new URLSearchParams(detectCapabilities());
    if (start > 0) p.set("start", String(start));
    if (burnSubtitle != null) p.set("burn_subtitle", String(burnSubtitle));
    return json<TranscodeSession>(`/api/playback/${mediaFileId}/session?${p}`, { method: "POST" });
  },

  stopSession: (sessionId: string) =>
    json<{ ok: boolean }>(`/api/hls/${sessionId}`, { method: "DELETE" }),

  hlsUrl: (path: string) => withToken(`${API}${path}`),

  subtitleUrl: (mediaFileId: string, index: number) =>
    withToken(`${API}/api/media/${mediaFileId}/subtitles/${index}`),

  transcodeCapabilities: () => json<Record<string, unknown>>("/api/transcode/capabilities"),

  // --- bibliotecas ---

  libraries: () => json<Library[]>("/api/libraries"),

  browse: (path?: string) =>
    json<BrowseListing>(`/api/browse${path ? `?path=${encodeURIComponent(path)}` : ""}`),

  createLibrary: (body: {
    name: string;
    root_path: string;
    default_kind: string;
    provider_hint: string;
  }) => json<Library>("/api/libraries", { method: "POST", body: JSON.stringify(body) }),

  updateLibrary: (id: string, body: { default_kind?: string; provider_hint?: string }) =>
    json<Library>(`/api/libraries/${id}`, { method: "PATCH", body: JSON.stringify(body) }),

  deleteLibrary: (id: string) =>
    json<{ ok: boolean; works_removed: number }>(`/api/libraries/${id}`, { method: "DELETE" }),

  // --- elenco e equipe ---

  people: (q?: string, role?: string) => {
    const p = new URLSearchParams({ limit: "60" });
    if (q) p.set("q", q);
    if (role) p.set("role", role);
    return json<Person[]>(`/api/people?${p}`);
  },

  person: (id: string) => json<PersonDetail>(`/api/people/${id}`),

  workCredits: (workId: string) => json<Credit[]>(`/api/works/${workId}/credits`),

  // --- R18: o guia de cinema ---

  /// A capa do guia inteira numa requisição. Seis rotas separadas repetiriam o
  /// custo que a locadora já paga (§20) na aba ao lado.
  guia: () => json<GuiaEixos>("/api/guia"),

  /// R34: a capa. O índice acima continua onde estava — ele virou a parte de
  /// consulta, atrás da revista.
  revista: () => json<Revista>("/api/guia/revista"),

  /// Buscada **depois** que o cartaz já está na tela: são sete consultas, e
  /// nenhuma delas vale atrasar a leitura da sinopse.
  curiosidades: (workId: string) =>
    json<Curiosidade[]>(`/api/works/${workId}/curiosidades`),

  guiaPessoas: (role: string, q?: string, offset = 0, limit = 40) => {
    const p = new URLSearchParams({ role, limit: String(limit), offset: String(offset) });
    if (q?.trim()) p.set("q", q.trim());
    return json<PessoaDoGuia[]>(`/api/guia/pessoas?${p}`);
  },

  // --- autenticação ---

  authStatus: () => json<{ needs_setup: boolean }>("/api/auth/status"),

  me: () => json<AuthUser>("/api/auth/me"),

  login: (username: string, password: string) =>
    json<{ token: string; user: AuthUser }>("/api/auth/login", {
      method: "POST",
      body: JSON.stringify({ username, password, device_label: navigator.platform || "web" }),
    }),

  setup: (username: string, password: string) =>
    json<{ token: string; user: AuthUser }>("/api/auth/setup", {
      method: "POST",
      body: JSON.stringify({ username, password, device_label: navigator.platform || "web" }),
    }),

  logout: () => json<{ ok: boolean }>("/api/auth/logout", { method: "POST" }),

  /// Trocar a senha. **Não passa pelo `json`** — e a exceção é o ponto.
  ///
  /// Esta é a única rota em que um 401 não quer dizer "a sessão morreu": quer
  /// dizer "a senha atual que você digitou está errada". Pelo caminho comum,
  /// errar a senha antiga por um dedo limparia o token e jogaria a pessoa na
  /// tela de entrada — o produto punindo com logout quem tentou se proteger.
  ///
  /// O 400 vem com a mensagem do servidor (o mínimo de caracteres é dele, não
  /// nosso) e é repassado inteiro: reescrevê-lo aqui daria duas verdades sobre
  /// a mesma regra.
  trocarSenha: async (atual: string, nova: string): Promise<TrocaDeSenha> => {
    const token = auth.token();
    const res = await fetch(`${API}/api/auth/password`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: JSON.stringify({ current_password: atual, new_password: nova }),
    });
    if (res.status === 401) throw new Error("senha atual incorreta");
    if (!res.ok) throw new Error((await res.text()) || `erro ${res.status}`);
    return res.json() as Promise<TrocaDeSenha>;
  },

  // --- R19: a locadora, e R28: o estoque é do servidor ---

  /// O que está FORA da prateleira, e o que voltou.
  ///
  /// Não devolve o estado das 746 caixas — devolve as poucas que estão em mãos.
  /// Quem cruza com a estante é a tela, que já tem as caixas carregadas.
  prateleira: () => json<Prateleira>("/api/locadora/prateleira"),

  /// R20: a loja desta semana — as estantes já reivindicadas, rotacionadas e
  /// cortadas. Uma requisição no lugar das doze que a tela fazia.
  estantes: () => json<Loja>("/api/locadora/estantes"),

  // --- R26: o convidado ---

  convites: () => json<ConviteNaLista[]>("/api/convites"),

  /// O código volta **uma vez só** — ele não fica guardado, só o SHA-256 dele.
  convidar: (para?: string) =>
    json<{ codigo: string; expira_em_dias: number; aviso: string }>("/api/convites", {
      method: "POST",
      body: JSON.stringify({ para: para ?? null }),
    }),

  revogarConvite: (para: string) =>
    json<{ revogado: boolean }>(`/api/convites/${encodeURIComponent(para)}`, {
      method: "DELETE",
    }),

  /// Público, como o login: quem resgata ainda não tem sessão.
  resgatar: (codigo: string, username: string, password: string, display_name?: string) =>
    json<{ ok: boolean; username: string }>("/api/convites/resgatar", {
      method: "POST",
      body: JSON.stringify({ codigo, username, password, display_name: display_name ?? null }),
    }),

  // --- R25: o mural, com o escopo que a R28 deu a ele ---
  //
  // Nenhuma tabela nova por trás: é um UNION sobre `play_event`, `emprestimo` e
  // `avaliacao`, escopado por **você e seus amigos**.
  feed: (limit?: number) =>
    json<Mural>(`/api/feed${limit ? `?limit=${limit}` : ""}`),

  // --- R30: a fita ---
  //
  // Chamada **no play**, não na montagem da estante.
  fita: (workId: string) => json<Fita>(`/api/locadora/fita/${workId}`),

  /// R50 — a lista do que eu posso assistir. Ver `liberadas.ts`.
  liberadas: () => json<Liberadas>("/api/locadora/liberadas"),

  // --- R29: as opções da loja ---
  //
  // Ler é de qualquer morador; gravar, só de administrador.
  opcoesDaLocadora: () => json<OpcoesDaLocadora>("/api/locadora/opcoes"),

  salvarOpcoesDaLocadora: (o: OpcoesDaLocadora) =>
    json<OpcoesDaLocadora>("/api/locadora/opcoes", {
      method: "PUT",
      body: JSON.stringify(o),
    }),

  // --- R33: a rede social ---

  presenca: () => json<Presente[]>("/api/presenca"),

  pessoas: (q?: string) =>
    json<Achado[]>(`/api/pessoas${q ? `?q=${encodeURIComponent(q)}` : ""}`),

  postar: (texto: string, workId?: string) =>
    json<{ id: string }>("/api/posts", {
      method: "POST",
      body: JSON.stringify({ texto, work_id: workId ?? null }),
    }),

  apagarPost: (id: string) =>
    json<{ apagado: boolean }>(`/api/posts/${id}`, { method: "DELETE" }),

  /// Uma chamada pros dois alvos, espelhando a tabela.
  comentar: (alvo: { post_id: string } | { review_user: string; review_work: string }, texto: string) =>
    json<{ id: string }>("/api/comentarios", {
      method: "POST",
      body: JSON.stringify({ ...alvo, texto }),
    }),

  apagarComentario: (id: string) =>
    json<{ apagado: boolean }>(`/api/comentarios/${id}`, { method: "DELETE" }),

  comentariosDaReview: (quem: string, obra: string) =>
    json<Comentario[]>(`/api/avaliacao/${quem}/${obra}/comentarios`),

  conversas: () => json<Conversa[]>("/api/mensagens"),

  conversa: (com: string) => json<Mensagem[]>(`/api/mensagens/${com}`),

  mandar: (para: string, texto: string) =>
    json<{ id: number }>(`/api/mensagens/${para}`, {
      method: "POST",
      body: JSON.stringify({ texto }),
    }),

  // --- R28: amigos ---

  amigos: () => json<MinhasAmizades>("/api/amigos"),

  /// Pede **ou** aceita — é o mesmo gesto nas duas pontas, e quem sabe em qual
  /// estado a relação está é o banco, não esta tela.
  pedirAmizade: (id: string) =>
    json<{ estado: "amigo" | "enviado"; recado: string }>(`/api/amigos/${id}`, {
      method: "POST",
    }),

  /// Recusa, cancela ou desfaz: as três apagam a mesma linha.
  desfazerAmizade: (id: string) =>
    json<{ desfeita: boolean; era_amizade: boolean }>(`/api/amigos/${id}`, {
      method: "DELETE",
    }),

  // --- R24: a retrospectiva ---
  //
  // O placar que morava aqui saiu na R32: era quatro números com um aviso
  // mandando ignorá-los. A retrospectiva ficou — descrever quem você é continua
  // sendo outra coisa que dar ponto.

  retrospectiva: () => json<RetrospectivaDoUsuario>("/api/retrospectiva"),

  // --- R32: o perfil ---

  perfil: (userId?: string) =>
    json<PerfilCompleto>(userId ? `/api/perfil/${userId}` : "/api/perfil"),

  // --- R35: os desafios ---

  desafios: () => json<MeusDesafios>("/api/desafios"),

  salvarCadencia: (cadencia: Cadencia) =>
    json<{ cadencia: Cadencia }>("/api/desafios/cadencia", {
      method: "PUT",
      body: JSON.stringify({ cadencia }),
    }),

  salvarPerfil: (p: {
    titulo: string | null;
    tags: string[];
    bio: string | null;
    vitrine: string[];
    avatar?: string | null;
    capa?: string | null;
    moldura?: string | null;
    /// O nome que aparece em toda tela. Omitir é "não mexe" — o servidor só
    /// escreve o que veio, e o campo mora no `app_user`, fora da tabela do
    /// perfil.
    display_name?: string;
  }) => json<{ ok: boolean }>("/api/perfil", { method: "PUT", body: JSON.stringify(p) }),

  // --- R23: a nota e a resenha ---

  avaliacoes: (workId: string) =>
    json<AvaliacoesDaObra>(`/api/works/${workId}/avaliacao`),

  /// `PUT` porque avaliar de novo é trocar de ideia, não criar uma segunda.
  avaliar: (workId: string, nota: number, texto?: string) =>
    json<{ ok: boolean }>(`/api/works/${workId}/avaliacao`, {
      method: "PUT",
      body: JSON.stringify({ nota, texto: texto ?? null }),
    }),

  desavaliar: (workId: string) =>
    json<{ apagada: boolean }>(`/api/works/${workId}/avaliacao`, { method: "DELETE" }),

  // --- R21: o menu de DVD ---

  /// Tudo que o menu precisa, numa requisição.
  menuDoDisco: (workId: string) => json<MenuDoDisco>(`/api/works/${workId}/menu`),

  /// A grade de cenas. **Custa ~4s na primeira vez** — doze extrações de
  /// quadro — e é instantânea depois. Por isso só é chamada quando alguém
  /// entra na tela de cenas, e nunca ao abrir o menu.
  cenasDoDisco: (workId: string) => json<Cena[]>(`/api/works/${workId}/cenas`),

  alugar: (alvo: AlvoDaCaixa) =>
    json<{ id: number; titulo: string; vence_em_dias: number }>("/api/locadora/alugar", {
      method: "POST",
      body: JSON.stringify(alvo),
    }),

  devolverEmprestimo: (id: number) =>
    json<{ devolvido_como: CondicaoDaFita; atrasada: boolean }>(
      `/api/locadora/devolver/${id}`,
      { method: "POST" },
    ),

  pedirDeVolta: (id: number) =>
    json<{ pedido_a: string }>(`/api/locadora/pedir/${id}`, { method: "POST" }),

  /// Destrutivo: apaga o "continuar de onde parou". Quem chama confirma antes.
  rebobinar: (alvo: AlvoDaCaixa) =>
    json<{ rebobinadas: number }>("/api/locadora/rebobinar", {
      method: "POST",
      body: JSON.stringify(alvo),
    }),
};

/// A caixa é uma obra avulsa **ou** a coleção de uma série — nunca as duas.
/// É o mesmo par de colunas que o CHECK da migração 0021 impõe do outro lado.
export type AlvoDaCaixa = { work_id: string } | { collection_id: string };

export type CondicaoDaFita = "rebobinada" | "no-meio" | "terminada";

/// As opções da loja. Moravam no círculo até a R28; hoje são do servidor, e a
/// fase 2 (`IDEIAS.md` §3.2) põe uma tela em cima delas.
export interface OpcoesDaLocadora {
  /// Quantas caixas ficam expostas na **loja inteira** por semana — não por
  /// estante. É a escala do `IDEIAS.md` §3.2.
  estoque: number;
  prazo_dias: number;
  limite_por_pessoa: number;
  /// Ligada, uma cópia por caixa. Desligada, sai **só o bloqueio**: a loja
  /// continua curta e ninguém barra ninguém.
  escassez: boolean;
}

/// Alguém que frequenta a loja, e quanto tem na mão.
///
/// São as pessoas do **servidor**, não as de um grupo: com estoque único quem
/// te barra pode ser qualquer uma delas.
export interface PessoaNaLoja {
  id: string;
  display_name: string;
  na_mao: number;
  /// Quantas fitas dela **alguém teve que rebobinar**. A reputação, e cada
  /// unidade é uma vez em que outra pessoa gastou os segundos por causa dela.
  zoadas: number;
  /// E quantas ela rebobinou dos outros. O outro lado precisa existir: um
  /// placar que só conta o defeito faz de todo mundo réu.
  rebobinou: number;
  /// Fitas que ela deixou no meio **agora**. Estado, não histórico — some no
  /// instante em que alguém rebobina, e é a única das três que dá pra
  /// consertar sozinha.
  no_meio: number;
}

/// R30 — onde está esta fita.
///
/// **Só chega quando alguém põe pra tocar.** A estante não sabe, de propósito:
/// *"você descobre quando põe pra tocar — não na estante, não antes"*.
export interface Fita {
  posicao_segundos: number;
  duracao_segundos: number | null;
  /// Quem deixou assim. `null` quando ninguém tocou, ou quando a conta sumiu —
  /// a fita sobrevive ao dono.
  deixada_por: string | null;
  deixada_em: string | null;
  /// Se fui eu que deixei assim. É o que decide se rebobinar é obrigatório:
  /// pausar o próprio filme e voltar é continuar; encontrar a fita de outra
  /// pessoa no minuto 47 é outra coisa.
  minha: boolean;
  /// DVD não é fita — ele lembra onde parou (§35).
  vhs: boolean;
}

export interface Emprestada {
  id: number;
  /// O mesmo id que `/api/library` devolve — é por ele que a estante casa.
  caixa_id: string;
  serie: boolean;
  titulo: string;
  quem: string;
  quem_nome: string;
  meu: boolean;
  pego_em: string;
  vence_em: string;
  pedido_em: string | null;
  pedido_por_nome: string | null;
  /// Se este empréstimo disputa a única cópia — é o que decide se a caixa some
  /// da prateleira. Com a escassez desligada ele é `false`, e a caixa continua
  /// exposta pra quem ainda pode pegá-la.
  exclusivo: boolean;
  /// A arte, pra caixa poder ser desenhada fora da estante. Existe porque a
  /// rotação da R20 pode não expor esta semana a caixa que alguém levou — e
  /// uma caixa invisível não tem como ser pedida de volta.
  poster: string | null;
  dominant_color: string | null;
  ano: number | null;
}

export interface Devolvida {
  caixa_id: string;
  titulo: string;
  quem_nome: string;
  devolvido_em: string;
  devolvido_como: CondicaoDaFita;
  /// `membro` — devolveu; `prazo` — venceu e voltou sozinha.
  devolvido_por: "membro" | "prazo";
  atrasada: boolean;
}

export interface ConviteNaLista {
  para: string | null;
  criado_em: string;
  expira_em: string;
  usado_em: string | null;
  usado_por_nome: string | null;
  /// Vencido e não usado. A lista mostra em vez de sumir: quem convidou
  /// precisa saber que o convite morreu sem ser usado.
  vencido: boolean;
}

export interface Acontecimento {
  /// `terminou` | `pegou` | `devolveu` | `pediu` | `avaliou`. Lista fechada:
  /// um tipo que a tela não sabe dizer não vira linha muda, some.
  tipo: string;
  quando: string;
  quem: string;
  quem_id: string;
  meu: boolean;
  titulo: string;
  obra_id: string | null;
  poster: string | null;
  detalhe: string | null;
  /// O id do post, quando o acontecimento **é** um post — é por ele que o
  /// comentário se pendura. `null` no resto: comentar um "fulano terminou X"
  /// seria comentar um fato, não uma fala.
  post_id: string | null;
  comentarios: Comentario[];
}

/// R33 — um comentário, de post ou de review. A mesma forma nos dois lugares,
/// porque é a mesma tabela e a mesma tela.
export interface Comentario {
  id: string;
  quem: string;
  quem_id: string;
  meu: boolean;
  texto: string;
  criado_em: string;
}

/// Quem está aqui agora.
export interface Presente {
  id: string;
  display_name: string;
  /// O que está assistindo **agora**. `null` é "online e não está vendo nada" —
  /// e a tela não inventa frase pra isso.
  assistindo: string | null;
  work_id: string | null;
  poster: string | null;
  /// É o que separa as duas listas pedidas (no servidor · entre amigos) sem
  /// pedir duas consultas.
  amigo: boolean;
  eu: boolean;
  /// R43 — o rosto escolhido.
  rosto: string | null;
}

export interface Achado {
  id: string;
  username: string;
  display_name: string;
  relacao: "amigo" | "enviado" | "recebido" | "nenhuma";
}

export interface Conversa {
  com: string;
  display_name: string;
  ultima: string | null;
  quando: string | null;
  nao_lidas: number;
}

export interface Mensagem {
  id: number;
  minha: boolean;
  texto: string;
  criado_em: string;
}

export interface Mural {
  acontecimentos: Acontecimento[];
  /// Quantas pessoas apareceram. Um mural com um nome só não é uma conversa —
  /// e a tela diz isso em vez de parecer completa.
  vozes: number;
  /// Quantas poderiam aparecer: você mais os seus amigos.
  pessoas: number;
}

/// Alguém do servidor, visto de onde você está.
/// Um enfeite do catálogo (R43): rosto, capa ou cor.
export interface EnfeiteNaTela {
  chave: string;
  /// O nome da pessoa, o título do filme, ou o hex da cor.
  rotulo: string;
  /// Caminho servível em `/artwork/…`. `null` na cor, que não tem arte.
  arte: string | null;
  exige: string | null;
  exige_nome: string | null;
  /// O hex, quando é cor de moldura. A tela pinta com ele e mostra o `rotulo`.
  cor: string | null;
  aberto: boolean;
}

export interface Alguem {
  id: string;
  username: string;
  display_name: string;
  /// R43 — o rosto escolhido, como caminho de arte. `null` cai na marca do nome.
  rosto: string | null;
  desde: string;
}

/// R28 — amizade é entre duas contas que já existem, com pedido e aceite.
///
/// As quatro listas vêm juntas porque são a mesma pergunta ("quem são as outras
/// pessoas daqui?") separada por estado — quatro requisições poderiam voltar de
/// estados diferentes se alguém aceitasse um pedido no meio.
export interface MinhasAmizades {
  amigos: Alguem[];
  /// Pedidos que chegaram pra mim.
  recebidos: Alguem[];
  /// Pedidos que eu mandei e ninguém respondeu.
  enviados: Alguem[];
  /// Quem mais está no servidor, sem relação nenhuma comigo.
  no_servidor: Alguem[];
}

export interface ItemDaRetrospectiva {
  rotulo: string;
  nota: string | null;
  imagem: string | null;
}

export interface BlocoDaRetrospectiva {
  chave: string;
  titulo: string;
  /// Montada no servidor, como os `reasons` do score (§8b) e as curiosidades
  /// (§32) — montá-la aqui seria uma segunda gramática pra manter.
  frase: string;
  detalhe?: ItemDaRetrospectiva[];
}

export interface RetrospectivaDoUsuario {
  blocos: BlocoDaRetrospectiva[];
  /// Quantos blocos ficaram calados por falta de material. A tela diz isso em
  /// vez de deixar a pessoa concluir que o Odeon não sabe nada dela.
  calados: number;
  desde: string | null;
}

/// R32 — o perfil, e o placar que ele substitui.
///
/// O §40 entregou quatro números numa aba escondida com um aviso mandando
/// ignorá-los. Aqui há nível, XP, uma lista longa de conquistas, títulos e tags
/// desbloqueáveis, campo livre, vitrine — e a comparação com os amigos, que foi
/// pedida e nunca existiu.
export type CamadaDaConquista =
  | "facil"
  | "media"
  | "dificil"
  | "impossivel"
  | "nivel"
  | "saga";

export interface ConquistaNaTela {
  chave: string;
  nome: string;
  descricao: string;
  camada: CamadaDaConquista;
  pontos: number;
  /// Se ela também serve de título no perfil.
  titulo: boolean;
  /// E se libera uma tag.
  tag: string | null;
  /// `null` enquanto trancada.
  em: string | null;
}

export interface ProgressoDoUsuario {
  xp: number;
  nivel: number;
  /// Onde o nível atual começou e onde o próximo começa. Servidos, e não
  /// recalculados aqui: a curva é regra, e regra mora num lugar só.
  xp_do_nivel: number;
  xp_do_proximo: number;
  desbloqueadas: number;
  total: number;
}

/// R35 — um desafio: tarefa com prazo, sorteada **por pessoa**.
///
/// O oposto do guia (§2.4): a revista é igual pra todo mundo, o desafio é seu.
export interface DesafioNaTela {
  id: string;
  chave: string;
  alvo: string | null;
  xp: number;
  vence_em: string;
  cumprido_em: string | null;
  /// A frase pronta. Montada no servidor porque o rótulo e o alvo moram lá —
  /// mandar os dois separados faria a tela remontar a gramática.
  rotulo: string;
}

export type Cadencia = "diaria" | "tres_dias" | "semanal";

export interface MeusDesafios {
  cadencia: Cadencia;
  desafios: DesafioNaTela[];
}

export interface NaVitrine {
  id: string;
  titulo: string;
  ano: number | null;
  poster: string | null;
}

export interface AmigoNoPlacar {
  id: string;
  username: string;
  display_name: string;
  nivel: number;
  xp: number;
  desbloqueadas: number;
  titulo: string | null;
  eu: boolean;
}

export interface PerfilCompleto {
  user_id: string;
  username: string;
  display_name: string;
  meu: boolean;
  progresso: ProgressoDoUsuario;
  /// A chave do título; `titulo_nome` é o que se mostra.
  titulo: string | null;
  titulo_nome: string | null;
  tags: string[];
  bio: string | null;
  vitrine: NaVitrine[];
  conquistas: ConquistaNaTela[];
  amigos: AmigoNoPlacar[];
  /// R43 — o rosto e a capa escolhidos, já resolvidos pelo servidor.
  avatar: EnfeiteNaTela | null;
  capa: EnfeiteNaTela | null;
  /// A cor da moldura, em hex. O servidor traduz a chave — a lista de cores é
  /// dele, e traduzir aqui seria a mesma lista escrita duas vezes.
  moldura: string | null;
  /// Os três catálogos, **inteiros**: o trancado vem junto, com a conquista que
  /// o abre, porque um rosto que ninguém sabe que existe não é perseguido.
  /// Só no seu próprio perfil.
  rostos: EnfeiteNaTela[];
  capas: EnfeiteNaTela[];
  molduras: EnfeiteNaTela[];
  /// Só vem no seu próprio perfil: o que dá pra escolher.
  titulos_disponiveis: [string, string][];
  tags_disponiveis: string[];
}

export interface AvaliacaoDeAlguem {
  user_id: string;
  quem: string;
  nota: number;
  texto: string | null;
  atualizado_em: string;
  meu: boolean;
}

export interface AvaliacoesDaObra {
  minha: AvaliacaoDeAlguem | null;
  /// As dos seus amigos — **não** uma média global.
  ///
  /// A média de estranhos é o IMDb com passos extras. A nota de gente que você
  /// conhece diz alguma coisa.
  de_amigos: AvaliacaoDeAlguem[];
  media: number | null;
  quantas: number;
}

/// Um capítulo, como o container o declara.
export interface Capitulo {
  inicio: number;
  fim: number;
  /// `null` quando o "título" é vazio, `Chapter 01` ou o próprio timecode —
  /// que é o caso de 98,4% dos filmes deste acervo. Exibir um timecode como
  /// nome de capítulo seria mentir com cara de metadado (§18).
  titulo: string | null;
}

export interface Cena {
  segundos: number;
  imagem: string;
  /// `capitulo` quando o disco disse onde a cena começa, `regular` quando foi
  /// o relógio que dividiu. A tela usa isto pra escrever a legenda certa, não
  /// pra mudar o desenho.
  origem: "capitulo" | "regular";
}

export interface MenuDoDisco {
  work_id: string;
  media_file_id: string;
  titulo: string;
  ano: number | null;
  cor: string | null;
  backdrop: string | null;
  duracao: number | null;
  /// `null` quando não há de onde continuar — e aí o item nem existe no menu.
  posicao: number | null;
  terminado: boolean;
  capitulos: Capitulo[];
  /// Idiomas distintos, na ordem das faixas do disco.
  legendas: string[];
  /// Onde a cena de fundo começa. **Sorteada a cada abertura**, no miolo do
  /// filme — abrir o mesmo disco duas vezes dá dois planos diferentes.
  cena_de_fundo: number;
  /// O clima: o índice da estante que reivindicaria este filme na locadora.
  ///
  /// Era um gênero cru vindo de um `SELECT … LIMIT 1` sem ordenação, e o
  /// sintetizador reduzia isso a três variantes — daí *a música é igual em
  /// todos os filmes*. Agora é a mesma ordem de reivindicação da prateleira,
  /// então o filme que mora na estante de terror abre um menu de terror.
  clima: number;
  clima_nome: string;
}

/// Uma caixa exposta nesta semana.
///
/// Os nomes são os do servidor e não os de `LibraryEntry` — esta é uma
/// resposta da locadora, não da biblioteca, e traduzir no meio do caminho só
/// criaria um terceiro vocabulário.
export interface CaixaExposta {
  id: string;
  serie: boolean;
  titulo: string;
  ano: number | null;
  poster: string;
  dominant_color: string | null;
  temporadas: number;
  media_file_id: string | null;
  position_seconds: number | null;
  estante: number;
  total: number;
}

export interface EstanteExposta {
  nome: string;
  /// Quantas caixas esta estante tem **no acervo**, não quantas estão à vista.
  /// A placa diz "16 de 113" com isso.
  total: number;
  caixas: CaixaExposta[];
}

export interface Loja {
  estantes: EstanteExposta[];
  /// Quantas caixas o acervo tem nas estantes, **inteiro**.
  ///
  /// Servido, e não somado aqui: uma estante que o sorteio não contemplou não
  /// vem na resposta, e somar os totais das que vieram esconde o acervo dela.
  no_acervo: number;
  /// A segunda-feira desta rotação.
  semana_de: string;
  /// Quando a vitrine vira. É o que torna a rotação promessa, não sorteio.
  vira_em: string;
  ultimo_ano_vhs: number;
}

export interface Prateleira {
  opcoes: OpcoesDaLocadora;
  pessoas: PessoaNaLoja[];
  emprestadas: Emprestada[];
  devolvidas: Devolvida[];
  posso_pegar: number;
  /// O corte entre fita e disco, servido pelo servidor.
  ///
  /// **Não é constante daqui de propósito.** O backend usa o mesmo número pra
  /// decidir se uma caixa rebobina; se os dois divergissem, uma caixa desenhada
  /// como VHS recusaria o rebobinar — a mesma família do botão que dizia "ver
  /// as 644" e abria 1.424 (§30).
  ultimo_ano_vhs: number;
}

export function formatDuration(seconds: number | null): string {
  if (!seconds || !isFinite(seconds)) return "—";
  const h = Math.floor(seconds / 3600);
  const m = Math.round((seconds % 3600) / 60);
  return h > 0 ? `${h}h${String(m).padStart(2, "0")}` : `${m}min`;
}

export function formatSize(bytes: number | null): string {
  if (!bytes) return "—";
  const gb = bytes / 1024 ** 3;
  return gb >= 1 ? `${gb.toFixed(1)} GB` : `${Math.round(bytes / 1024 ** 2)} MB`;
}

/** Sem artwork ainda (isso é o M1). Até lá, uma cor estável derivada do título. */
export function hueFromTitle(title: string): number {
  let hash = 0;
  for (let i = 0; i < title.length; i++) {
    hash = (hash << 5) - hash + title.charCodeAt(i);
    hash |= 0;
  }
  return Math.abs(hash) % 360;
}
