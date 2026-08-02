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
  clear: () => localStorage.removeItem(TOKEN_KEY),
};

export interface AuthUser {
  id: string;
  username: string;
  display_name: string;
  role: string;
  is_active: boolean;
  last_login_at: string | null;
}

/** Sessão morreu (expirou ou foi revogada): a UI volta pro login. */
export class Unauthorized extends Error {
  constructor() {
    super("sessão expirada");
  }
}

/**
 * `<video src>`, `<img src>` e `<track src>` não mandam header. O servidor
 * aceita `?token=` só nas rotas de mídia, justamente por isso.
 */
function withToken(url: string): string {
  const token = auth.token();
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
  /** Repetido em toda linha — é o total de entradas do filtro atual. */
  total: number;
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

export interface Library {
  id: string;
  name: string;
  root_path: string;
  default_kind: string;
  provider_hint: string;
  created_at: string;
}

export interface BrowseEntry {
  name: string;
  path: string;
  video_count: number;
  has_subdirs: boolean;
}

export interface BrowseListing {
  path: string;
  /** null quando já se está numa raiz — a UI esconde o "subir". */
  parent: string | null;
  roots: string[];
  entries: BrowseEntry[];
  video_count: number;
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
  mudarUsuario: (id: string, body: { role?: "admin" | "user"; is_active?: boolean }) =>
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
};

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
