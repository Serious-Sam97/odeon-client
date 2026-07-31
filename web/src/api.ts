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
function deriveFromPage(): string {
  const { protocol, hostname, port } = window.location;
  // Servido pela própria API (mesma origem): usa a origem inteira.
  if (port === "8080" || port === "8443") return window.location.origin;
  const secure = protocol === "https:";
  return `${secure ? "https" : "http"}://${hostname}:${secure ? 8443 : 8080}`;
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
  | { type: "scrub_finished"; done: number; failed: number };

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
  item_count: number;
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
export interface WorkDetail {
  id: string;
  kind: string;
  title: string;
  original_title: string | null;
  year: number | null;
  overview: string | null;
  season_number: number | null;
  episode_number: number | null;
  match_state: string;
  match_confidence: number | null;
  dominant_color: string | null;
  tags: WorkTag[];
  collections: Collection[];
  relations: Relation[];
  credits: Credit[];
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
  index: number;
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

function queryString(filters: Filters): string {
  const p = new URLSearchParams({ limit: "300" });
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
  works: (filters: Filters = {}) => json<WorkListItem[]>(`/api/works?${queryString(filters)}`),

  continueWatching: () => json<WorkListItem[]>("/api/continue"),

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

  review: () => json<ReviewItem[]>("/api/review"),

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
  spriteInfo: async (mediaFileId: string): Promise<SpriteInfo | null> => {
    const res = await fetch(`${API}/api/media/${mediaFileId}/scrub`);
    return res.ok ? ((await res.json()) as SpriteInfo) : null;
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

  taste: () => json<TasteProfile>("/api/curation/taste"),

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
