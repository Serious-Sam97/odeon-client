import { useCallback, useEffect, useState } from "react";
import Desafios from "./Desafios";
import { useArrastoDeFileira } from "./arrasto";
import {
  api,
  comPrazo,
  formatDuration,
  hueFromTitle,
  type ForYou as ForYouData,
  type Recommendation,
  type WorkListItem,
} from "./api";

const TIME_OPTIONS: [string, number | undefined][] = [
  ["qualquer tempo", undefined],
  ["15 min", 15],
  ["30 min", 30],
  ["45 min", 45],
  ["1h", 60],
  ["2h", 120],
];

/// Quantas obras entram na faixa do meio. O resto vira fila.
const DESTAQUES = 6;

/// A tela que responde "o que eu assisto agora", não "o que existe na
/// biblioteca". Todo item diz POR QUE foi sugerido — mesma regra do M1.
///
/// O ranking vem em três pesos (herói, destaque, fila) em vez de uma lista
/// plana: a curadoria do M5 produz uma ordem, e desenhar todo item igual era
/// jogar essa ordem fora. Ver DESIGN.md (repositório do servidor) §12.
export default function ForYou({ onPlay }: { onPlay: (w: WorkListItem) => void }) {
  const [data, setData] = useState<ForYouData | null>(null);
  const [minutes, setMinutes] = useState<number | undefined>(undefined);
  const [mood, setMood] = useState<string | undefined>(undefined);
  const [moods, setMoods] = useState<string[]>([]);
  const [showProfile, setShowProfile] = useState(false);
  const [loading, setLoading] = useState(true);
  const [erro, setErro] = useState<string | null>(null);
  const [parou, setParou] = useState<WorkListItem[]>([]);
  const [calib, setCalib] = useState<WorkListItem[]>([]);
  const [votadas, setVotadas] = useState<Record<string, "love" | "block">>({});

  /// ## ⚠️ Sem `catch`, a falha virava «afrouxe o tempo disponível» — 19/08/2026
  ///
  /// Esta tela tinha o defeito da família toda, e na versão mais desagradável
  /// dele: quando `/api/curation/for-you` falhava, `data` ficava nulo, a lista
  /// saía vazia, e a tela dizia **«Nada cabe nesse tempo · afrouxe o tempo
  /// disponível ou o humor»** — culpando a escolha de quem está olhando por um
  /// erro que não é dela. E, se o pedido pendurasse, o «pensando…» era pra
  /// sempre, porque `fetch` sem prazo não falha: só não volta.
  ///
  /// Não saber é diferente de não ter (§18), e as duas frases agora são duas.
  const load = useCallback(async () => {
    setLoading(true);
    setErro(null);
    try {
      setData(await comPrazo(api.forYou(minutes, mood), "para você"));
    } catch (e) {
      setErro(e instanceof Error ? e.message : String(e));
    } finally {
      setLoading(false);
    }
  }, [minutes, mood]);

  useEffect(() => {
    load();
  }, [load]);

  /// O que alimenta o estado frio: o que você largou, e capas pra votar.
  const carregarFrio = useCallback(() => {
    api.continueWatching().then(setParou).catch(() => {});
    api.calibrar().then(setCalib).catch(() => {});
  }, []);

  useEffect(carregarFrio, [carregarFrio]);

  useEffect(() => {
    api
      .tags()
      .then((tags) =>
        setMoods(tags.filter((t) => t.namespace === "mood" && t.work_count > 0).map((t) => t.value)),
      )
      .catch(() => {});
  }, []);

  /// Quanto o Odeon te conhece, de 0 a 1.
  ///
  /// Terminar vale mais que votar, e votar vale muito mais que abrir — é a
  /// mesma hierarquia do M5, só que exposta. Seis sinais bastam pra ele
  /// arriscar: abaixo disso a tela admite em vez de ranquear.
  const p = data?.profile;
  const sinais =
    (p?.finished ?? 0) * 2 +
    ((p as { curtidas?: number })?.curtidas ?? 0) +
    ((p as { bloqueadas?: number })?.bloqueadas ?? 0) +
    Object.keys(votadas).length;
  const conhecimento = Math.min(1, sinais / 6);

  const items = data?.items ?? [];
  const heroi = items[0];
  const destaques = items.slice(1, 1 + DESTAQUES);
  const fila = items.slice(1 + DESTAQUES);

  return (
    <div className="foryou">
      <div className="context-bar">
        <span className="filter-label">Tenho</span>
        <div className="chips">
          {TIME_OPTIONS.map(([label, value]) => (
            <button
              key={label}
              className={minutes === value ? "chip on" : "chip"}
              onClick={() => setMinutes(value)}
            >
              {label}
            </button>
          ))}
        </div>
      </div>

      {moods.length > 0 && (
        <div className="context-bar">
          <span className="filter-label">Pra</span>
          <div className="chips">
            {moods.map((m) => (
              <button
                key={m}
                className={mood === m ? "chip on" : "chip"}
                onClick={() => setMood(mood === m ? undefined : m)}
              >
                {m}
              </button>
            ))}
          </div>
        </div>
      )}

      {conhecimento < 1 && data && (
        <Apresentacao
          perfil={data.profile}
          votos={Object.keys(votadas).length}
          conhecimento={conhecimento}
        />
      )}

      {conhecimento < 1 && parou.length > 0 && (
        <Faixa
          titulo="Continue de onde parou"
          dica="o sinal mais forte que ele tem hoje"
          larga
        >
          {parou.slice(0, 5).map((w) => (
            <CartaoSimples key={w.id} work={w} onPlay={onPlay} progresso />
          ))}
        </Faixa>
      )}

      {/* R41 — os desafios também aqui.

          Eles moram no perfil desde a R35, e o perfil é **onde se vai de
          propósito**; o "para você" é onde se cai. Um desafio que só existe na
          tela que se visita de propósito é um desafio que se esquece.

          Abaixo do "continue de onde parou" e acima do que a curadoria sugere:
          o que você já começou vem antes do que alguém propôs, e o que você se
          comprometeu a fazer vem antes do que a máquina achou. E fora do
          `conhecimento < 1` que envolve a faixa acima — o desafio não é
          conteúdo de estado frio, ele vale igual depois de mil filmes. */}
      <Desafios compacto />

      {conhecimento < 1 && calib.length > 0 && (
        <Calibragem
          capas={calib}
          votadas={votadas}
          onVoto={(id, v) => {
            setVotadas((m) => ({ ...m, [id]: v }));
            api.feedback(id, v).then(load).catch(() => {});
          }}
        />
      )}

      {data && !data.cold_start && (
        <button className="chip toggle profile-toggle" onClick={() => setShowProfile(!showProfile)}>
          o que o Odeon acha que você gosta {showProfile ? "▴" : "▾"}
        </button>
      )}

      {showProfile && data && <TasteInspector data={data} />}

      {loading && !data ? (
        <p className="muted">pensando…</p>
      ) : erro && !data ? (
        <div className="empty">
          <p>a sugestão não veio</p>
          <p className="muted">{erro}</p>
          <button className="chip" onClick={load}>
            tentar de novo
          </button>
        </div>
      ) : items.length === 0 ? (
        <div className="empty">
          <p>Nada cabe nesse tempo.</p>
          <p className="muted">Afrouxe o tempo disponível ou o humor.</p>
        </div>
      ) : (
        <>
          {heroi && (
            <>
              <div className="strip primeira">
                <h2>Esta noite</h2>
                <span className="rule" />
              </div>
              <HeroCard item={heroi} onPlay={onPlay} onChanged={load} />
            </>
          )}

          {destaques.length > 0 && (
            <>
              <div className="strip">
                <h2>Também hoje</h2>
                <span className="rule" />
              </div>
              <section className="picks">
                {destaques.map((item, i) => (
                  <PickCard key={item.id} item={item} rank={i + 2} onPlay={onPlay} />
                ))}
              </section>
            </>
          )}

          {fila.length > 0 && (
            <>
              <div className="strip">
                <h2>A fila</h2>
                <span className="rule" />
              </div>
              <section className="queue">
                {fila.map((item, i) => (
                  <QueueRow key={item.id} item={item} rank={i + 2 + DESTAQUES} onPlay={onPlay} />
                ))}
              </section>
            </>
          )}
        </>
      )}
    </div>
  );
}

/// O perfil aberto. Recomendação que não se deixa inspecionar é adivinhação.
function TasteInspector({ data }: { data: ForYouData }) {
  const p = data.profile;
  const positives = p.tag_affinity.filter(([, v]) => v > 0.1).slice(0, 8);
  const negatives = p.tag_affinity.filter(([, v]) => v < -0.1).slice(-8);
  const peakHour = p.hour_histogram.indexOf(Math.max(...p.hour_histogram));

  return (
    <div className="taste-panel">
      <div className="taste-stats">
        <span>
          <strong>{p.works_touched}</strong> obras tocadas
        </span>
        <span>
          <strong>{p.finished}</strong> terminadas
        </span>
        <span>
          <strong>{p.abandoned}</strong> largadas
        </span>
        {p.preferred_minutes && (
          <span>
            termina entre <strong>{p.preferred_minutes[0]}</strong> e{" "}
            <strong>{p.preferred_minutes[1]}</strong> min
          </span>
        )}
        {p.works_touched > 0 && (
          <span>
            costuma assistir por volta das <strong>{peakHour}h</strong>
          </span>
        )}
      </div>

      {positives.length > 0 && (
        <div className="filter-group">
          <span className="filter-label">Termina</span>
          <div className="chips">
            {positives.map(([tag, value]) => (
              <span key={tag} className="chip on">
                {tag.split(":")[1] ?? tag} <span className="muted">+{Math.round(value * 100)}%</span>
              </span>
            ))}
          </div>
        </div>
      )}

      {negatives.length > 0 && (
        <div className="filter-group">
          <span className="filter-label">Larga</span>
          <div className="chips">
            {negatives.map(([tag, value]) => (
              <span key={tag} className="chip negative">
                {tag.split(":")[1] ?? tag} <span className="muted">{Math.round(value * 100)}%</span>
              </span>
            ))}
          </div>
        </div>
      )}

      {!p.has_taste_vector && (
        <p className="muted small">
          Sem vetor de gosto ainda — rode <em>embeddings</em> e termine alguma coisa.
        </p>
      )}
    </div>
  );
}

// ------------------------------------------------------------- utilitários

/// `--hue` alimenta o gradiente de quem não tem arte; `--accent-work` é a cor
/// dominante da obra, e só toca ARTE (halo, borda do pôster) — nunca texto nem
/// número. Ver DESIGN.md (repositório do servidor) §12.
function tintOf(item: Recommendation): React.CSSProperties {
  const style: Record<string, string | number> = { "--hue": hueFromTitle(item.title) };
  if (item.dominant_color) style["--accent-work"] = item.dominant_color;
  return style as React.CSSProperties;
}

/// A arte larga do herói. O backdrop é 16:9 de verdade; o still é o quadro do
/// episódio. Sem nenhum dos dois o pôster serve — recortado, mas é arte real.
function wideArt(item: Recommendation): string | null {
  const path = item.backdrop ?? item.still ?? item.poster;
  return path ? api.artworkUrl(path) : null;
}

function metaLine(item: Recommendation): string {
  const episodio =
    item.season_number != null && item.episode_number != null
      ? `T${item.season_number} E${item.episode_number}`
      : null;
  return [item.year, episodio, formatDuration(item.duration_seconds)]
    .filter(Boolean)
    .join(" · ");
}

function Feedback({ id, onChanged }: { id: string; onChanged: () => void }) {
  return (
    <>
      <button
        className="icon-btn"
        title="quero mais coisas assim"
        onClick={async () => {
          await api.feedback(id, "love");
          onChanged();
        }}
      >
        ♥
      </button>
      <button
        className="icon-btn"
        title="nunca mais me ofereça isto"
        onClick={async () => {
          await api.feedback(id, "block");
          onChanged();
        }}
      >
        ✕
      </button>
    </>
  );
}

// ------------------------------------------------------------------ herói

function HeroCard({
  item,
  onPlay,
  onChanged,
}: {
  item: Recommendation;
  onPlay: (w: WorkListItem) => void;
  onChanged: () => void;
}) {

  const art = wideArt(item);
  const [principal, ...resto] = item.reasons;

  return (
    <section className="hero" style={tintOf(item)}>
      {art && <div className="hero-art" style={{ backgroundImage: `url(${art})` }} />}
      <div className="hero-wash" />
      <div className="bulbs" />

      <div className="hero-inner">
        <button
          className="hero-poster"
          onClick={() => item.media_file_id && onPlay(item)}
          title={item.title}
        >
          {item.poster && <img src={api.artworkUrl(item.poster)} alt="" />}
        </button>

        <div>
          {item.series_title && <p className="hero-series">{item.series_title}</p>}
          <h1 className="hero-title">{item.title}</h1>
          <p className="hero-meta">{metaLine(item)}</p>

          {/* Os motivos, em texto. É isto que separa curadoria de sorteio. */}
          {principal && (
            <p className="hero-why">
              <b>{principal}</b>
              {resto.length > 0 && ` — ${resto.join(" · ")}`}
            </p>
          )}

          <div className="hero-actions">
            {/* R56 — o botão voltou a ter um destino só. O "pegar na locadora"
                que ele virava com a escassez ligada saiu com a regra (§71).

                O único "não" que sobrou é o de obra sem arquivo, e esse diz por
                quê: um botão apagado sem explicação é o §8b. */}
            <button
              className="play"
              onClick={() => item.media_file_id && onPlay(item)}
              disabled={!item.media_file_id}
              title={item.media_file_id ? undefined : "nenhum arquivo tocável nesta obra"}
            >
              ▸ Assistir
            </button>
            <Feedback id={item.id} onChanged={onChanged} />
          </div>
        </div>

        <div className="hero-score">
          <span className="n">{Math.round(item.score * 100)}</span>
          <span className="lbl">afinidade</span>
        </div>
      </div>
    </section>
  );
}

// -------------------------------------------------------------- destaques

function PickCard({
  item,
  rank,
  onPlay,
}: {
  item: Recommendation;
  rank: number;
  onPlay: (w: WorkListItem) => void;
}) {

  return (
    <article className="pick" style={tintOf(item)}>
      <button
        className="pick-art"
        onClick={() => item.media_file_id && onPlay(item)}
      >
        <span className="pick-rank">{rank}</span>
        {item.poster ? (
          <img src={api.artworkUrl(item.poster)} alt="" loading="lazy" />
        ) : (
          <div className="pick-noart" />
        )}
      </button>
      <div>
        {item.series_title && <p className="pick-series">{item.series_title}</p>}
        <h3 className="pick-title">{item.title}</h3>
        <p className="meta">{metaLine(item)}</p>
        {item.reasons[0] && <p className="why">{item.reasons[0]}</p>}
      </div>
    </article>
  );
}

// ------------------------------------------------------------------- fila

function QueueRow({
  item,
  rank,
  onPlay,
}: {
  item: Recommendation;
  rank: number;
  onPlay: (w: WorkListItem) => void;
}) {

  return (
    <div className="qrow" style={tintOf(item)}>
      <span className="qrank">{String(rank).padStart(2, "0")}</span>
      <button
        className="qposter"
        onClick={() => item.media_file_id && onPlay(item)}
      >
        {item.poster ? (
          <img src={api.artworkUrl(item.poster)} alt="" loading="lazy" />
        ) : (
          <div className="qnoart" />
        )}
      </button>
      <div>
        <div className="qtitle">{item.title}</div>
        <div className="qsub">
          {item.series_title && <span className="s">{item.series_title}</span>}
          {item.series_title && " · "}
          {[metaLine(item), item.reasons[0]].filter(Boolean).join(" · ")}
        </div>
      </div>
      <div className="qscore">
        <span className="bar">
          <i style={{ width: `${Math.min(100, Math.round(item.score * 100))}%` }} />
        </span>
        {Math.round(item.score * 100)}
      </div>
    </div>
  );
}

// ------------------------------------------------- o Odeon se apresentando

/// O cabeçalho que admite não te conhecer.
///
/// Ele existe porque a tela estava fazendo o contrário: apresentava seis itens
/// separados por 0,8% de score como um ranking de 2 a 7, com a mesma frase
/// embaixo de todos, construído sobre 0 obras terminadas e 0 votos.
///
/// Some sozinho quando `conhecimento` chega a 1 — não é uma tela de boas-vindas
/// que se fecha, é um estado que se resolve.
function Apresentacao({
  perfil,
  votos,
  conhecimento,
}: {
  perfil: ForYouData["profile"];
  votos: number;
  conhecimento: number;
}) {
  const p = perfil as unknown as { finished?: number; curtidas?: number };
  const terminadas = p.finished ?? 0;
  const curtidas = (p.curtidas ?? 0) + votos;

  return (
    <header className="pv-topo">
      <div>
        <p className="pv-selo">
          {conhecimento === 0 ? "o Odeon ainda não te conhece" : "ele está começando a te conhecer"}
        </p>
        <h1>{conhecimento === 0 ? "Vamos mudar isso." : "Continue."}</h1>
        <p className="pv-sub">
          Ele aprende de assistir, não de perguntar — mas com{" "}
          <b>
            {terminadas} {terminadas === 1 ? "obra terminada" : "obras terminadas"}
          </b>{" "}
          e{" "}
          <b>
            {curtidas} {curtidas === 1 ? "curtida" : "curtidas"}
          </b>{" "}
          ainda falta sinal pra ele arriscar um palpite.
        </p>
      </div>

      <div className="termometro">
        <p className="term-titulo">o quanto ele te conhece</p>
        <span className="term-trilho">
          <i style={{ width: `${Math.max(4, conhecimento * 100)}%` }} />
          <b style={{ left: `${Math.max(4, conhecimento * 100)}%` }} />
        </span>
        <div className="term-marcas">
          <span className={conhecimento < 0.34 ? "ativa" : undefined}>nada</span>
          <span className={conhecimento >= 0.34 && conhecimento < 1 ? "ativa" : undefined}>
            começando
          </span>
          <span className={conhecimento >= 1 ? "ativa" : undefined}>te conheço</span>
        </div>
      </div>
    </header>
  );
}

/// Uma faixa com título, régua e dica — o formato que o motivo passou a ter.
///
/// A justificativa virou **cabeçalho de seção** em vez de etiqueta repetida
/// embaixo de cada cartão. Ela só significa alguma coisa quando distingue um
/// grupo do outro; embaixo de seis cartões iguais, era ruído.
function Faixa({
  titulo,
  dica,
  larga,
  children,
}: {
  titulo: string;
  dica?: string;
  larga?: boolean;
  children: React.ReactNode;
}) {
  return (
    <section className="pv-secao">
      <header>
        <h3>{titulo}</h3>
        <span className="rule" />
        {dica && <i>{dica}</i>}
      </header>
      <div className={larga ? "pv-fila grande" : "pv-fila"}>{children}</div>
    </section>
  );
}

function CartaoSimples({
  work,
  onPlay,
  progresso,
}: {
  work: WorkListItem;
  onPlay: (w: WorkListItem) => void;
  progresso?: boolean;
}) {

  const arte = progresso ? (work.still ?? work.backdrop ?? work.poster) : work.poster;
  const feito =
    work.position_seconds && work.duration_seconds
      ? Math.min(100, (work.position_seconds / work.duration_seconds) * 100)
      : 0;

  return (
    <button
      className="pv-card"
      onClick={() => work.media_file_id && onPlay(work)}
    >
      <span className="pv-arte">
        {arte && <img src={api.artworkUrl(arte)} alt="" loading="lazy" />}
        {progresso && feito > 0 && (
          <span className="pv-progresso">
            <i style={{ width: `${feito}%` }} />
          </span>
        )}
      </span>
      {work.series_title && <span className="pv-serie">{work.series_title}</span>}
      <h4>{work.title}</h4>
      {progresso && work.position_seconds != null && (
        <span className="pv-meta">
          parou aos {Math.round(work.position_seconds / 60)} min
          {work.duration_seconds ? ` de ${Math.round(work.duration_seconds / 60)}` : ""}
        </span>
      )}
    </button>
  );
}

/// A calibragem: seis capas, ♥ ou ✕.
///
/// O contador não é "2 de 6" — são seis lâmpadas de marquise, e votar acende
/// uma. O tema da casa e a função viram a mesma coisa: o quanto ele te conhece
/// é literalmente o quanto a marquise está acesa.
function Calibragem({
  capas,
  votadas,
  onVoto,
}: {
  capas: WorkListItem[];
  votadas: Record<string, "love" | "block">;
  onVoto: (id: string, v: "love" | "block") => void;
}) {
  const acesas = capas.filter((c) => votadas[c.id]).length;
  const arrastar = useArrastoDeFileira();

  return (
    <section className="pv-secao calib">
      <header>
        <h3>Me diga o que você gosta</h3>
        <span className="rule" />
        {/* Fileira de lâmpadas: quantas obras da calibração você já votou.
            Chamava-se `.placar` e mudou de nome na R24 (§40) — não por
            estética, mas porque o placar de verdade nasceu ali ao lado e o
            §6.2 exige que dê pra desligá-lo sozinho. Um `grep placar` que
            encontra um indicador de calibração no fluxo principal faz quem
            for desligar concluir a coisa errada. */}
        <span className="calib-luzes">
          {capas.map((c, i) => (
            <i key={c.id} className={votadas[c.id] ? "acesa" : undefined} style={{ ["--n" as string]: i }} />
          ))}
        </span>
      </header>
      <p className="pv-explica">
        Cada ♥ ou ✕ vale mais que uma hora de tela: é o único sinal que ele consegue ler
        antes de você terminar alguma coisa.
      </p>
      <div className="pv-fila" ref={arrastar}>
        {capas.map((c) => (
          <div key={c.id} className={`pv-calib${votadas[c.id] === "block" ? " descartada" : ""}`}>
            <p className="pv-calib-t">{c.title}</p>
            <span className="pv-arte">
              {c.poster && <img src={api.artworkUrl(c.poster)} alt="" loading="lazy" />}
            </span>
            <div className="pv-voto">
              <button
                className={`v-sim${votadas[c.id] === "love" ? " on" : ""}`}
                onClick={() => onVoto(c.id, "love")}
                title="gosto disso"
              >
                ♥
              </button>
              <button
                className="v-nao"
                onClick={() => onVoto(c.id, "block")}
                title="não me mostre isso"
              >
                ✕
              </button>
            </div>
          </div>
        ))}
      </div>
      <p className="pv-contador">
        <b>{acesas}</b> de {capas.length} ·{" "}
        {acesas >= capas.length
          ? "pronto — ele já tem por onde começar"
          : `faltam ${capas.length - acesas} pra ele arriscar um palpite`}
      </p>
    </section>
  );
}
