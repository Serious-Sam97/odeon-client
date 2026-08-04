import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  api,
  type CanalAberto,
  type CanalNoAr,
  type Guia,
  type Lembrete,
  type ProgramaDoGuia,
} from "./api";
import { ligarHls } from "./hls";
import { useArrastoDeFileira } from "./arrasto";

const RECARREGA_MS = 60_000;
/// Quanto o mouse pode ficar parado antes do cromo sumir.
const OCIOSO_MS = 3000;

function hhmm(iso: string): string {
  return new Date(iso).toLocaleTimeString("pt-BR", { hour: "2-digit", minute: "2-digit" });
}

/// A aba "ao vivo".
///
/// O Odeon **sintoniza**, não programa: uma fonte IPTV publica os canais e a
/// grade, e daqui pra frente isto é leitura. Ver DESIGN.md (repositório do servidor) §17.
/// A ilha de transmissão.
///
/// A aba deixou de ser uma lista de canais e virou a mesa de quem opera a
/// emissora: **o que está no ar**, **para onde mudar**, e **o que vem**.
///
/// E o Odeon deixou de só sintonizar. Além das fontes IPTV, ele agora programa
/// canais próprios do seu acervo (`live::emissora`) — que aparecem como pistas
/// iguais às outras, marcadas em amarelo. Ver DESIGN.md (repositório do servidor) §25.
export default function AoVivo({
  isAdmin,
  onDetails,
}: {
  isAdmin: boolean;
  /// Abre o cartaz de uma obra do acervo. R39: é o que o bloco de um canal da
  /// casa passou a fazer — ele aponta pra uma obra, que é mais do que um
  /// programa de IPTV tem.
  onDetails: (workId: string) => void;
}) {
  const arrastar = useArrastoDeFileira();
  const [canais, setCanais] = useState<CanalNoAr[]>([]);
  const [guia, setGuia] = useState<Guia | null>(null);
  const [casa, setCasa] = useState<GradeOdeon | null>(null);
  const [loading, setLoading] = useState(true);
  const [erro, setErro] = useState<string | null>(null);
  const [assistindo, setAssistindo] = useState<CanalAberto | null>(null);
  const [abrindo, setAbrindo] = useState<string | null>(null);
  const [detalhe, setDetalhe] = useState<ProgramaDoGuia | null>(null);
  /// R44 — o que você agendou. A rota existe desde a R17 e **nunca teve
  /// cliente**: dava pra agendar e não dava pra ver o que estava agendado.
  const [agendados, setAgendados] = useState<Lembrete[]>([]);
  const [configurando, setConfigurando] = useState(false);
  const [foco, setFoco] = useState(0);
  /// Sobe a cada virada de programa. Não guarda o relógio — quem guarda o
  /// relógio é o CSS (ver `useAgulha`); isto só força o React a repintar o
  /// herói e os cartões quando o conteúdo realmente muda.
  const [virada, setVirada] = useState(0);

  const carregar = useCallback(async () => {
    try {
      const [c, g, o, l] = await Promise.all([
        api.liveChannels(),
        api.liveGuide(JANELA_H),
        api.liveOdeon(JANELA_H).catch(() => null),
        // A lista de agendamentos não derruba a aba: sem ela a grade continua.
        api.reminders().catch(() => []),
      ]);
      setCanais(c);
      setGuia(g);
      setCasa(o);
      setAgendados(l);
      setErro(null);
    } catch (e) {
      setErro(e instanceof Error ? e.message : String(e));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    carregar();
    const t = setInterval(carregar, RECARREGA_MS);
    return () => clearInterval(t);
  }, [carregar]);

  const pistas = useMemo(() => montarPistas(canais, guia, casa), [canais, guia, casa]);

  // Teclado de controle remoto: setas zapeiam, número digita o canal.
  useEffect(() => {
    if (assistindo || detalhe || pistas.length === 0) return;
    let digitado = "";
    let limpar: number | undefined;
    const onKey = (e: KeyboardEvent) => {
      if (e.target instanceof HTMLInputElement) return;
      if (e.key === "ArrowDown") {
        setFoco((f) => (f + 1) % pistas.length);
      } else if (e.key === "ArrowUp") {
        setFoco((f) => (f - 1 + pistas.length) % pistas.length);
      } else if (/^[0-9]$/.test(e.key)) {
        // Dois dígitos, como numa TV: 1 pode virar 10.
        digitado += e.key;
        window.clearTimeout(limpar);
        const tenta = () => {
          const i = pistas.findIndex((p) => p.numero === digitado);
          if (i >= 0) setFoco(i);
          digitado = "";
        };
        if (digitado.length >= 2) tenta();
        else limpar = window.setTimeout(tenta, 700);
      }
    };
    window.addEventListener("keydown", onKey);
    return () => {
      window.removeEventListener("keydown", onKey);
      window.clearTimeout(limpar);
    };
  }, [assistindo, detalhe, pistas.length, pistas]);

  const pista = pistas[Math.min(foco, Math.max(0, pistas.length - 1))];
  const noAr = pista ? emCartaz(pista, Date.now()) : null;

  /// Sintonizar — e os dois tipos de canal chegam no MESMO player.
  ///
  /// Um canal IPTV é um stream remoto; um canal da casa é um arquivo seu
  /// começando no offset que o relógio manda. Parece que pedem players
  /// diferentes, e pediriam — mas os dois viram uma playlist HLS, e é isso que
  /// o player consome. Sem essa unificação o zapeamento só funcionaria em
  /// metade dos canais, o que é pior do que não ter zapeamento.
  const sintonizar = async (p: Pista, doInicio = false) => {
    setErro(null);
    const b = emCartaz(p, Date.now());

    if (p.odeon || doInicio) {
      // Sem nada no ar (o vão de 4 min entre programas) não há o que
      // sintonizar. Dizer isso é melhor que um botão que não faz nada.
      if (!b) {
        const prox = p.blocos.find((x) => x.ini > Date.now());
        setErro(
          prox
            ? `intervalo — ${prox.titulo} começa às ${hhmm(new Date(prox.ini).toISOString())}`
            : "este canal está sem programação agora",
        );
        return;
      }
      if (!b.mediaFileId) return;
      const offset = doInicio ? 0 : Math.max(0, Math.floor((Date.now() - b.ini) / 1000));
      setAbrindo(p.chave);
      try {
        const s = await api.startSession(b.mediaFileId, offset);
        // A sessão anterior morre aqui. Sem isto, cada virada de programa
        // deixaria um ffmpeg vivo até o ceifador dos 90s notar — e num canal
        // que roda a noite toda isso é uma sessão por filme.
        setAssistindo((antigo) => {
          if (antigo?.session_id && antigo.session_id !== s.id) {
            api.stopSession(antigo.session_id).catch(() => {});
          }
          return {
            channel: { id: p.chave, name: p.nome },
            session_id: s.id,
            playlist_url: s.playlist_url,
            mode: s.mode,
            reasons: s.reasons,
          };
        });
      } catch (e) {
        setErro(e instanceof Error ? e.message : String(e));
      } finally {
        setAbrindo(null);
      }
      return;
    }

    setAbrindo(p.chave);
    try {
      setAssistindo(await api.watchChannel(p.chave));
    } catch (e) {
      setErro(e instanceof Error ? e.message : String(e));
    } finally {
      setAbrindo(null);
    }
  };

  if (loading) return <p className="muted">acendendo a ilha…</p>;

  if (pistas.length === 0) {
    return (
      <div className="curadoria">
        <div className="strip primeira">
          <h2>Ao vivo</h2>
          <span className="rule" />
          {isAdmin && (
            <button className="chip" onClick={() => setConfigurando(!configurando)}>
              {configurando ? "cancelar" : "+ fonte"}
            </button>
          )}
        </div>
        {configurando ? (
          <Fontes onMudou={carregar} />
        ) : (
          <div className="empty">
            <p>Nenhum canal ainda.</p>
            <p className="muted">
              {isAdmin
                ? "Cadastre uma lista M3U em + fonte. Se o provedor roda nesta mesma máquina, use o IP do host — dentro do container, localhost é o próprio container."
                : "Peça pro administrador cadastrar uma fonte IPTV."}
            </p>
          </div>
        )}
        {erro && <p className="error">{erro}</p>}
      </div>
    );
  }

  return (
    <div className="ilha">
      {erro && <p className="error">{erro}</p>}

      {pista && (
        <NoAr
          key={`${pista.chave}-${noAr?.id ?? "vazio"}-${virada}`}
          pista={pista}
          bloco={noAr}
          aSeguir={pista.blocos.find((b) => b.ini > Date.now()) ?? null}
          abrindo={abrindo === pista.chave}
          onSintonizar={() => sintonizar(pista)}
          onDoComeco={() => sintonizar(pista, true)}
        />
      )}

      <section className="dial">
        <header className="dial-topo">
          <h2>Sintonia</h2>
          <span className="dica">↑↓ zapeia · 0–9 digita o canal</span>
          {isAdmin && (
            <button className="chip" onClick={() => setConfigurando(!configurando)}>
              fontes
            </button>
          )}
        </header>
        {configurando && <Fontes onMudou={carregar} />}
        <div className="fileira-canais" ref={arrastar}>
          {pistas.map((p, i) => (
            <CartaoCanal
              key={p.chave}
              pista={p}
              ligado={i === foco}
              abrindo={abrindo === p.chave}
              virada={virada}
              onFoco={() => setFoco(i)}
              onAssistir={() => sintonizar(p)}
            />
          ))}
        </div>
      </section>

      <Agendados
        lista={agendados}
        onAbrir={(id) => {
          const p = guia?.programas.find((x) => x.id === id);
          if (p) setDetalhe(p);
        }}
      />

      <LinhaDoTempo
        pistas={pistas}
        foco={foco}
        onFoco={setFoco}
        // R39 — o clique que não fazia nada.
        //
        // O modal procurava o bloco na grade do IPTV. Os canais da casa são
        // programados pela emissora (§25) **sem tabela**, e os blocos deles não
        // existem nessa lista: `p` vinha `undefined` e o `if` engolia. Um
        // clique que não faz nada não é recurso ausente, é recurso quebrado
        // (§8b).
        //
        // Agora o bloco da casa abre o **cartaz da obra** — ele aponta pra uma
        // obra do acervo, que é mais do que um programa de IPTV tem.
        onAbrir={(b) => {
          const p = guia?.programas.find((x) => String(x.id) === b.id);
          if (p) {
            setDetalhe(p);
            return;
          }
          if (b.workId) {
            onDetails(b.workId);
            return;
          }
          // Nem programa nem obra: dizer, em vez de engolir de novo.
          setErro("este bloco não tem ficha — recarregue a grade");
        }}
        onVirada={() => setVirada((v) => v + 1)}
      />

      {detalhe && (
        <ModalPrograma
          programa={detalhe}
          canal={canais.find((c) => c.id === detalhe.channel_id) ?? null}
          onFechar={() => setDetalhe(null)}
          onMudou={carregar}
        />
      )}

      {assistindo && (
        <PlayerAoVivo
          aberto={assistindo}
          canal={canais.find((c) => c.id === assistindo.channel.id) ?? null}
          pistas={pistas}
          onTrocar={(p) => sintonizar(p)}
          onFechar={() => setAssistindo(null)}
        />
      )}
    </div>
  );
}

// ------------------------------------------------------- o modelo comum

/// Uma pista da linha do tempo. Canal IPTV e canal da casa viram a mesma
/// coisa aqui — a tela não deveria precisar saber de onde a programação veio.
interface Pista {
  chave: string;
  nome: string;
  numero: string | null;
  odeon: boolean;
  logo: string | null;
  blocos: Bloco[];
}

interface Bloco {
  id: string;
  titulo: string;
  ini: number;
  fim: number;
  arte: string | null;
  ano: number | null;
  categoria: string | null;
  workId: string | null;
  mediaFileId: string | null;
}

type GradeOdeon = Awaited<ReturnType<typeof api.liveOdeon>>;

function montarPistas(
  canais: CanalNoAr[],
  guia: Guia | null,
  casa: GradeOdeon | null,
): Pista[] {
  const out: Pista[] = [];

  // Os canais da casa vêm primeiro: são o que o Odeon tem de próprio.
  for (const c of casa?.canais ?? []) {
    const blocos = (casa?.programas ?? [])
      .filter((p) => p.canal === c.slug)
      .map((p) => ({
        id: p.id,
        titulo: p.title,
        ini: new Date(p.starts_at).getTime(),
        fim: new Date(p.ends_at).getTime(),
        arte: p.arte,
        ano: p.year,
        categoria: p.categoria,
        workId: p.work_id,
        mediaFileId: p.media_file_id,
      }));
    if (blocos.length > 0) {
      out.push({ chave: c.slug, nome: c.nome, numero: c.numero, odeon: true, logo: null, blocos });
    }
  }

  const porCanal = new Map<string, ProgramaDoGuia[]>();
  for (const p of guia?.programas ?? []) {
    const l = porCanal.get(p.channel_id) ?? [];
    l.push(p);
    porCanal.set(p.channel_id, l);
  }

  for (const c of canais) {
    out.push({
      chave: c.id,
      nome: c.name,
      numero: c.number,
      odeon: false,
      logo: c.logo_url,
      blocos: (porCanal.get(c.id) ?? [])
        .sort((a, b) => a.starts_at.localeCompare(b.starts_at))
        .map((p) => ({
          id: String(p.id),
          titulo: p.title,
          ini: new Date(p.starts_at).getTime(),
          fim: new Date(p.ends_at).getTime(),
          arte: p.arte,
          ano: p.year,
          categoria: p.categoria,
          workId: p.work_id,
          mediaFileId: p.media_file_id,
        })),
    });
  }
  return out;
}

function emCartaz(p: Pista, t: number): Bloco | null {
  return p.blocos.find((b) => b.ini <= t && b.fim > t) ?? null;
}




// -------------------------------------------------------------- o relógio

/// Quanto tempo a linha do tempo mostra à frente.
const JANELA_H = 5;
/// De quanto em quanto o relógio da tela anda.
///
/// 250ms, não `requestAnimationFrame`. A agulha percorre a janela de 5h em
/// alguns pixels por minuto — a 60fps, 59 de cada 60 quadros escreveriam o
/// mesmo valor e a única diferença seria a bateria.
const TIQUE_MS = 250;

/// A agulha, sem passar pelo React.
///
/// O "agora" é escrito como propriedade CSS num elemento só. Guardá-lo em
/// estado re-renderizaria a grade inteira — nove pistas, centenas de blocos —
/// quatro vezes por segundo, pra mover uma linha de dois pixels.
///
/// O React só é acordado quando o conteúdo muda de verdade: ao cruzar a
/// fronteira de um programa. É `onVirada` que troca o herói e os cartões.
function useAgulha(
  alvo: React.RefObject<HTMLElement | null>,
  ini: number,
  fim: number,
  fronteiras: number[],
  onVirada: () => void,
) {
  const proxima = useRef(Infinity);
  const virar = useRef(onVirada);
  virar.current = onVirada;

  useEffect(() => {
    const agora = Date.now();
    proxima.current = fronteiras.filter((f) => f > agora).sort((a, b) => a - b)[0] ?? Infinity;
  }, [fronteiras]);

  useEffect(() => {
    const passo = () => {
      const t = Date.now();
      const el = alvo.current;
      if (el) {
        el.style.setProperty("--agora", ((t - ini) / Math.max(1, fim - ini)).toFixed(5));
      }
      if (t >= proxima.current) {
        proxima.current = Infinity;
        virar.current();
      }
    };
    passo();
    const h = window.setInterval(passo, TIQUE_MS);
    return () => window.clearInterval(h);
  }, [alvo, ini, fim]);
}

/// O relógio da emissora, isolado pra que o segundo não repinte a tela toda.
function Relogio() {
  const [t, setT] = useState(() => new Date());
  useEffect(() => {
    const h = window.setInterval(() => setT(new Date()), 1000);
    return () => window.clearInterval(h);
  }, []);
  return (
    <div className="relogio">
      {t.toLocaleTimeString("pt-BR", { hour: "2-digit", minute: "2-digit" })}
      <small>{String(t.getSeconds()).padStart(2, "0")}</small>
    </div>
  );
}

// ----------------------------------------------------------------- no ar

function NoAr({
  pista,
  bloco,
  aSeguir,
  abrindo,
  onSintonizar,
  onDoComeco,
}: {
  pista: Pista;
  bloco: Bloco | null;
  aSeguir: Bloco | null;
  abrindo: boolean;
  onSintonizar: () => void;
  onDoComeco: () => void;
}) {
  const barra = useRef<HTMLDivElement>(null);
  const dur = bloco ? bloco.fim - bloco.ini : 0;

  // A barra do herói anda pelo mesmo relógio da agulha, e pelo mesmo motivo.
  useEffect(() => {
    if (!bloco) return;
    const passo = () => {
      const el = barra.current;
      if (el) {
        const f = Math.min(1, Math.max(0, (Date.now() - bloco.ini) / Math.max(1, dur)));
        el.style.setProperty("--feito", f.toFixed(5));
        // Escrito direto no nó: é texto que muda a cada 250ms e o React não
        // precisa saber dele. O elemento é renderizado vazio de propósito.
        const resta = Math.max(0, Math.round((bloco.fim - Date.now()) / 60000));
        const ate = el.querySelector(".ate");
        if (ate) ate.textContent = `faltam ${resta} min`;
      }
    };
    passo();
    const h = window.setInterval(passo, TIQUE_MS);
    return () => window.clearInterval(h);
  }, [bloco, dur]);

  return (
    <div
      className={`noar${bloco?.arte ? "" : " sem-arte"}`}
      style={bloco?.arte ? { ["--arte" as string]: `url(${api.artworkUrl(bloco.arte)})` } : undefined}
    >
      <div className="noar-arte" />
      {/* Sem foto em lugar nenhum — nem na biblioteca, nem no XMLTV — o fundo
          passa a ser a marquise do próprio Odeon, com as lâmpadas correndo.
          É o que a casa tem a dizer quando não tem o que mostrar. */}
      {!bloco?.arte && <div className="bulbs" />}
      <div className="noar-veu" />
      <div className="noar-txt">
        <p className="fita-noar">
          <i className="ponto" />
          {bloco ? "NO AR" : "INTERVALO"}
          <span>
            {pista.nome}
            {bloco?.categoria ? ` · ${bloco.categoria}` : ""}
          </span>
        </p>
        <h1>{bloco?.titulo ?? aSeguir?.titulo ?? pista.nome}</h1>
        <p className="sub">
          {bloco
            ? [bloco.ano, dur ? `${Math.round(dur / 60000)} min` : null,
               pista.odeon ? "programado pelo Odeon" : "ao vivo"]
                .filter(Boolean)
                .join(" · ")
            : aSeguir
              ? `a seguir, às ${hhmm(new Date(aSeguir.ini).toISOString())}`
              : "sem grade"}
        </p>

        {bloco && (
          <div className="linha-tempo" ref={barra}>
            <span className="de">começou {hhmm(new Date(bloco.ini).toISOString())}</span>
            <div className="trilho">
              <i />
              <b />
            </div>
            <span className="ate" />
          </div>
        )}

        <div className="acoes">
          <button className="b-sint" onClick={onSintonizar} disabled={abrindo}>
            {abrindo ? "sintonizando…" : "▸ SINTONIZAR"}
          </button>
          {/* Só existe quando há arquivo — nos canais sem casamento o botão
              não aparece, em vez de aparecer e falhar. */}
          {bloco?.mediaFileId && !pista.odeon && (
            <button className="b-inicio" onClick={onDoComeco} disabled={abrindo}>
              ↺ VER DESDE O INÍCIO
            </button>
          )}
        </div>
      </div>
      <Relogio />
    </div>
  );
}

// -------------------------------------------------------------- sintonia

function CartaoCanal({
  pista,
  ligado,
  abrindo,
  virada,
  onFoco,
  onAssistir,
}: {
  pista: Pista;
  ligado: boolean;
  abrindo: boolean;
  virada: number;
  onFoco: () => void;
  onAssistir: () => void;
}) {
  const b = useMemo(() => emCartaz(pista, Date.now()), [pista, virada]);
  const frac = b ? Math.min(1, (Date.now() - b.ini) / Math.max(1, b.fim - b.ini)) : 0;

  return (
    <button
      className={`canal${pista.odeon ? " odeon" : ""}${ligado ? " ligado" : ""}${abrindo ? " abrindo" : ""}`}
      onClick={() => (ligado ? onAssistir() : onFoco())}
      onDoubleClick={onAssistir}
      title={ligado ? "sintonizar" : "focar este canal"}
    >
      <span
        className={`canal-arte${b?.arte ? "" : " sem-arte"}`}
        style={b?.arte ? { backgroundImage: `url(${api.artworkUrl(b.arte)})` } : undefined}
      />
      <span className="canal-veu" />
      <span className="canal-topo">
        {pista.odeon ? <i className="selo">ODEON</i> : <i className="num">{pista.numero ?? ""}</i>}
        <em>{pista.nome}</em>
      </span>
      <span className="canal-txt">
        <b>{b?.titulo ?? "sem grade"}</b>
        {b && (
          <i>
            {hhmm(new Date(b.ini).toISOString())} – {hhmm(new Date(b.fim).toISOString())}
          </i>
        )}
      </span>
      <span className="canal-barra">
        <u style={{ width: `${frac * 100}%` }} />
      </span>
      {abrindo && <span className="canal-abrindo">sintonizando…</span>}
    </button>
  );
}

// --------------------------------------------------------- linha do tempo

function LinhaDoTempo({
  pistas,
  foco,
  onFoco,
  onAbrir,
  onVirada,
}: {
  pistas: Pista[];
  foco: number;
  onFoco: (i: number) => void;
  onAbrir: (b: Bloco) => void;
  onVirada: () => void;
}) {
  const raiz = useRef<HTMLDivElement>(null);
  // A janela começa 45 min atrás: ver o que acabou de passar é metade da
  // utilidade de uma grade.
  const ini = useMemo(() => Date.now() - 45 * 60_000, [pistas]);
  const fim = ini + JANELA_H * 3600_000;

  // Fim E começo: entre um programa e o outro há 4 minutos de intervalo, e a
  // tela tem que acordar nas duas pontas — senão ela entra no intervalo e não
  // sai mais dele até a próxima recarga.
  const fronteiras = useMemo(
    () => pistas.flatMap((p) => p.blocos.flatMap((b) => [b.fim, b.ini])),
    [pistas],
  );
  useAgulha(raiz, ini, fim, fronteiras, onVirada);

  const marcas: number[] = [];
  const primeira = Math.ceil(ini / 3600_000) * 3600_000;
  for (let t = primeira; t < fim; t += 3600_000) marcas.push(t);

  const pc = (t: number) => ((t - ini) / (fim - ini)) * 100;

  return (
    <section className="grade" ref={raiz}>
      <header className="grade-topo">
        <h2>A linha do tempo</h2>
        <span className="reg">
          {marcas.map((t) => (
            <span key={t} style={{ left: `${pc(t)}%` }}>
              {hhmm(new Date(t).toISOString())}
            </span>
          ))}
        </span>
      </header>
      <div className="pistas">
        <div className="agulha">
          <span>agora</span>
        </div>
        {pistas.map((p, i) => (
          <div
            key={p.chave}
            className={`pista${p.odeon ? " odeon" : ""}${i === foco ? " foco" : ""}`}
            onClick={() => onFoco(i)}
          >
            <div className="rot">
              {p.odeon ? <i className="selo">ODEON</i> : <i className="num">{p.numero ?? ""}</i>}
              <span>{p.nome}</span>
            </div>
            <div className="faixa">
              {p.blocos
                .filter((b) => b.fim > ini && b.ini < fim)
                .map((b) => {
                  const e = Math.max(0, pc(b.ini));
                  const l = Math.max(0.6, pc(b.fim) - e);
                  const noAr = b.ini <= Date.now() && b.fim > Date.now();
                  return (
                    <div
                      key={b.id}
                      className={`bl${p.odeon ? " odeon" : ""}${noAr ? (i === foco ? " vivo" : " agora") : ""}`}
                      style={{ left: `${e}%`, width: `${l}%` }}
                      onClick={(ev) => {
                        ev.stopPropagation();
                        onAbrir(b);
                      }}
                    >
                      <b>{b.titulo}</b>
                      <i>{hhmm(new Date(b.ini).toISOString())}</i>
                    </div>
                  );
                })}
            </div>
          </div>
        ))}
      </div>
    </section>
  );
}

function PlayerAoVivo({
  aberto,
  canal,
  pistas,
  onTrocar,
  onFechar,
}: {
  aberto: CanalAberto;
  canal: CanalNoAr | null;
  pistas: Pista[];
  onTrocar: (p: Pista) => void;
  onFechar: () => void;
}) {
  const pista = pistas.find((p) => p.chave === aberto.channel.id) ?? null;
  /// Sobe quando a programação vira, pra recalcular o que está no ar.
  const [tique, setTique] = useState(0);
  /// Qual bloco teve o arquivo acabando antes do horário.
  ///
  /// Guardar o ID em vez de um booleano é o que faz o estado se limpar
  /// sozinho: quando a programação vira, `noAr` passa a ser outro bloco e a
  /// comparação deixa de bater. Com booleano, o cartão de intervalo ficava
  /// aberto por cima do programa seguinte — apareceu no teste da virada.
  const [acabouCedo, setAcabouCedo] = useState<string | null>(null);
  const noAr = useMemo(
    () => (pista ? emCartaz(pista, Date.now()) : null),
    [pista, tique],
  );
  /// O próximo programa — o que o cartão de intervalo anuncia.
  const aSeguir = useMemo(
    () => pista?.blocos.find((b) => b.ini > Date.now()) ?? null,
    [pista, tique],
  );

  /// A virada de programa **dentro** do player.
  ///
  /// Num canal IPTV o stream continua e só o rótulo muda. Num canal da casa
  /// não há stream nenhum: o filme acaba e nada acontece depois — era o buraco
  /// que sobrou da R13.
  ///
  /// Quem manda é o RELÓGIO, não o `ended` do vídeo. A emissora troca de
  /// programa no horário, e o arquivo pode acabar antes (arquivo mais curto
  /// que o `runtime` do provider) ou depois (créditos, luvas de dublagem). O
  /// `ended` entra só como atalho pra não deixar tela preta quando acaba cedo.
  useEffect(() => {
    if (!pista) return;
    const agora = Date.now();

    // NO AR: o próximo evento é o fim deste programa.
    if (noAr) {
      const h = window.setTimeout(
        () => setTique((t) => t + 1),
        Math.max(0, noAr.fim - agora) + 250,
      );
      return () => window.clearTimeout(h);
    }

    // ENTRE PROGRAMAS: a grade tem 4 minutos de respiro entre um filme e o
    // seguinte, e nesse vão não há nada no ar. O próximo evento é a estreia.
    //
    // Foi aqui que a primeira versão morreu: ela só olhava o fim do programa,
    // e no instante seguinte `emCartaz` já devolvia `null` — o `sintonizar`
    // saía calado e o canal ficava parado no filme que tinha acabado.
    const prox = pista.blocos.find((b) => b.ini > agora);
    if (!prox) return;
    const h = window.setTimeout(
      () => {
        setAcabouCedo(null);
        setTique((t) => t + 1);
        if (pista.odeon) onTrocar(pista);
      },
      prox.ini - agora + 250,
    );
    return () => window.clearTimeout(h);
  }, [pista, noAr, tique, onTrocar]);

  /// Zapear.
  ///
  /// O chuvisco não é enfeite gratuito: trocar de canal leva segundos (o
  /// ffmpeg precisa produzir o primeiro segmento), e sem nada acontecendo
  /// nesse vão a impressão é de travamento. O ruído ocupa a espera com a
  /// linguagem certa — e é a única coisa desta tela que o
  /// `prefers-reduced-motion` desliga por completo.
  const [zapeando, setZapeando] = useState(false);
  const [banner, setBanner] = useState<Pista | null>(null);
  const bannerTimer = useRef<number | undefined>(undefined);

  const zapear = useCallback(
    (passo: number) => {
      if (pistas.length === 0) return;
      const atual = pistas.findIndex((p) => p.chave === aberto.channel.id);
      const alvo = pistas[(atual + passo + pistas.length) % pistas.length];
      if (!alvo) return;
      setZapeando(true);
      setBanner(alvo);
      window.clearTimeout(bannerTimer.current);
      bannerTimer.current = window.setTimeout(() => setBanner(null), 3400);
      window.setTimeout(() => setZapeando(false), 340);
      onTrocar(alvo);
    },
    [pistas, aberto.channel.id, onTrocar],
  );

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "ArrowUp") zapear(-1);
      else if (e.key === "ArrowDown") zapear(1);
    };
    window.addEventListener("keydown", onKey);
    return () => {
      window.removeEventListener("keydown", onKey);
      window.clearTimeout(bannerTimer.current);
    };
  }, [zapear]);

  const videoRef = useRef<HTMLVideoElement>(null);
  const [erro, setErro] = useState<string | null>(null);
  const [volume, setVolume] = useState(1);
  const [tocando, setTocando] = useState(false);
  const [relogio, setRelogio] = useState(() => new Date());
  const [cromo, setCromo] = useState(true);
  const ocioso = useRef<number | undefined>(undefined);

  /// Mexeu o mouse: mostra o cromo e reinicia a contagem.
  ///
  /// Esconde **mesmo pausado**. Ao vivo, pausar não é ler a tela — a
  /// transmissão continua e o que interessa é a imagem. Voltar é só mexer.
  const acordar = useCallback(() => {
    setCromo(true);
    window.clearTimeout(ocioso.current);
    ocioso.current = window.setTimeout(() => setCromo(false), OCIOSO_MS);
  }, []);

  useEffect(() => {
    acordar();
    return () => window.clearTimeout(ocioso.current);
  }, [acordar]);

  useEffect(() => {
    const video = videoRef.current;
    if (!video) return;
    const r = ligarHls(video, aberto.playlist_url, (d) => setErro(`HLS: ${d}`));
    if (typeof r === "string") {
      setErro(r);
      return;
    }
    return r;
  }, [aberto.playlist_url]);

  useEffect(() => {
    const t = setInterval(() => setRelogio(new Date()), 30_000);
    return () => clearInterval(t);
  }, []);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") onFechar();
      if (e.key === " ") {
        e.preventDefault();
        const v = videoRef.current;
        if (v) (v.paused ? v.play() : v.pause());
      }
      acordar();
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [onFechar, acordar]);

  let pct = 0;
  let desde: number | null = null;
  if (canal?.comeca && canal.termina) {
    const a = new Date(canal.comeca).getTime();
    const b = new Date(canal.termina).getTime();
    const agora = Date.now();
    pct = b > a ? Math.max(0, Math.min(100, ((agora - a) / (b - a)) * 100)) : 0;
    desde = Math.max(0, Math.round((agora - a) / 60000));
  }

  return (
    <div
      className={cromo ? "player" : "player idle"}
      style={{ "--accent-work": "#e0b062" } as React.CSSProperties}
      onMouseMove={acordar}
    >
      <div className="player-halo" />

      <div className="player-stage">
        <video ref={videoRef} className="video" autoPlay crossOrigin="anonymous"
          onPlay={() => setTocando(true)} onPause={() => setTocando(false)}
          // Acabou antes da hora marcada: INTERVALO, não emenda.
          //
          // Emendar no próximo adiantaria o canal em relação à própria grade,
          // e a grade é o que todo mundo está vendo — quem sintonizasse
          // depois cairia noutro ponto do filme. Uma emissora com tempo
          // sobrando entra em intervalo e espera o horário; é o que este faz.
          onEnded={() => pista?.odeon && noAr && setAcabouCedo(noAr.id)}
          onVolumeChange={(e) => setVolume(e.currentTarget.muted ? 0 : e.currentTarget.volume)} />
        {zapeando && (
          <>
            {/* Ruído por gradientes repetidos — nenhum asset externo, como o
                resto do projeto. */}
            <div className="chuvisco" />
            <div className="rolagem" />
          </>
        )}
      </div>

      {banner && (
        <div className="zap-banner">
          <span className="n">{banner.numero ?? "—"}</span>
          <span className="c">{banner.nome}</span>
          <h3>{emCartaz(banner, Date.now())?.titulo ?? "sem grade"}</h3>
        </div>
      )}

      <header className="player-top">
        <div>
          <p className="player-series">
            {(pista?.numero ?? canal?.number) && (
              <span className="num">{pista?.numero ?? canal?.number}</span>
            )}
            {pista?.nome ?? canal?.name ?? aberto.channel.name}
            {noAr && (
              <span className="ate-quando">
                {" "}
                até {hhmm(new Date(noAr.fim).toISOString())}
              </span>
            )}
          </p>
          {/* O título é o do PROGRAMA; o canal fica na sobrelinha. Antes um
              canal da casa mostrava "Odeon 1" no lugar do nome do filme. */}
          <h2 className="player-title">{noAr?.titulo ?? canal?.titulo ?? aberto.channel.name}</h2>
          {canal?.comeca && canal.termina && (
            <p className="player-tech">
              {hhmm(canal.comeca)} – {hhmm(canal.termina)}
              {canal.sub_titulo && ` · ${canal.sub_titulo}`}
            </p>
          )}
        </div>
        <button className="player-close" onClick={onFechar} title="fechar (Esc)">
          ✕
        </button>
      </header>

      {(!noAr || acabouCedo === noAr.id) && aSeguir && (
        <div className="intervalo">
          <p className="intervalo-selo">intervalo</p>
          <h3>{aSeguir.titulo}</h3>
          <p className="intervalo-hora">às {hhmm(new Date(aSeguir.ini).toISOString())}</p>
        </div>
      )}

      {erro && <div className="player-card erro">{erro}</div>}

      <div className="player-scrim">
        <div className="bulbs" />

        {/* Sem knob e sem preview: não há pra onde buscar. */}
        <div className="timeline vivo">
          <div className="track" />
          <div className="track played" style={{ width: `${pct}%` }} />
          <span className="ponto-agora" style={{ left: `${pct}%` }} />
        </div>

        <p className="linha-programa">
          <span className="selo-vivo">● AO VIVO</span>
          {desde !== null && `no ar há ${desde} min`}
          {canal?.a_seguir && (
            <>
              {" · a seguir "}
              <b>{canal.a_seguir}</b>
            </>
          )}
        </p>

        <div className="control-row">
          <button
            className="icon big"
            onClick={() => {
              const v = videoRef.current;
              if (v) (v.paused ? v.play() : v.pause());
            }}
            title="pausar (a transmissão continua)"
          >
            {tocando ? "❚❚" : "▶"}
          </button>
          <button className="player-btn" onClick={onFechar}>
            canais
          </button>
          <span className="timecode">
            {relogio.toLocaleTimeString("pt-BR", { hour: "2-digit", minute: "2-digit" })}
          </span>

          <div className="spacer" />

          <span className={`mode-badge ${aberto.mode}`} title={aberto.reasons.join(" · ")}>
            {aberto.mode === "direct_stream" ? "Remux" : aberto.mode}
          </span>

          <div className="volume-wrap">
            <button
              className="icon"
              onClick={() => {
                const v = videoRef.current;
                if (v) v.muted = !v.muted;
              }}
            >
              {volume === 0 ? "◀" : "◀)"}
            </button>
            <input
              className="volume"
              type="range"
              min={0}
              max={1}
              step={0.05}
              value={volume}
              onChange={(e) => {
                const v = videoRef.current;
                if (!v) return;
                v.volume = Number(e.target.value);
                v.muted = Number(e.target.value) === 0;
              }}
            />
          </div>
        </div>
      </div>
    </div>
  );
}

/// Cadastro das fontes. Só admin chega aqui.
function Fontes({ onMudou }: { onMudou: () => void }) {
  const [fontes, setFontes] = useState<FonteLista[]>([]);
  const [nome, setNome] = useState("");
  const [m3u, setM3u] = useState("");
  const [xmltv, setXmltv] = useState("");
  const [erro, setErro] = useState<string | null>(null);
  const [importando, setImportando] = useState(false);

  const carregar = useCallback(() => {
    api.liveSources().then(setFontes).catch(() => {});
  }, []);
  useEffect(carregar, [carregar]);

  const criar = async () => {
    if (!m3u.trim()) return;
    try {
      await api.createLiveSource(nome.trim() || "IPTV", m3u.trim(), xmltv.trim());
      setNome("");
      setM3u("");
      setXmltv("");
      carregar();
    } catch (e) {
      setErro(e instanceof Error ? e.message : String(e));
    }
  };

  return (
    <div className="fontes">
      {fontes.map((f) => (
        <div key={f.id} className="fonte-linha">
          <div>
            <strong>{f.name}</strong>{" "}
            <span className="muted small">
              {f.canais} canais
              {f.last_import_at && ` · importada ${new Date(f.last_import_at).toLocaleString("pt-BR")}`}
            </span>
            <p className="path">{f.m3u_url}</p>
            {f.last_error && <p className="error">{f.last_error}</p>}
          </div>
          <button
            className="ghost small-btn"
            onClick={async () => {
              await api.deleteLiveSource(f.id);
              carregar();
              onMudou();
            }}
          >
            ✕
          </button>
        </div>
      ))}

      <div className="criar-colecao">
        <input className="campo" placeholder="nome" value={nome} onChange={(e) => setNome(e.target.value)} />
        <input className="campo" placeholder="URL da lista (.m3u)" value={m3u} onChange={(e) => setM3u(e.target.value)} />
        <input className="campo" placeholder="URL da grade (.xml) — opcional" value={xmltv} onChange={(e) => setXmltv(e.target.value)} />
        <button className="play pequeno" onClick={criar} disabled={!m3u.trim()}>
          adicionar
        </button>
      </div>

      <div className="mais">
        <button
          className="chip"
          disabled={importando || fontes.length === 0}
          onClick={async () => {
            setImportando(true);
            try {
              await api.liveImport();
              // O import roda em segundo plano; dar um tempo antes de reler
              // evita mostrar a lista antiga e parecer que não fez nada.
              setTimeout(() => {
                carregar();
                onMudou();
                setImportando(false);
              }, 4000);
            } catch (e) {
              setErro(e instanceof Error ? e.message : String(e));
              setImportando(false);
            }
          }}
        >
          {importando ? "importando…" : "importar agora"}
        </button>
      </div>

      {erro && <p className="error">{erro}</p>}
    </div>
  );
}

type FonteLista = Awaited<ReturnType<typeof api.liveSources>>[number];

/// O detalhe de um programa da grade, com o agendamento.
///
/// O "está na sua biblioteca" só aparece quando o programa foi **ligado com
/// segurança** a uma obra (ver `live::ligar_obras`): sem isso, seria um palpite
/// vestido de fato.
function ModalPrograma({
  programa,
  canal,
  onFechar,
  onMudou,
}: {
  programa: ProgramaDoGuia;
  canal: CanalNoAr | null;
  onFechar: () => void;
  onMudou: () => void;
}) {
  const [agendado, setAgendado] = useState(programa.lembrete);
  const [erro, setErro] = useState<string | null>(null);
  const [ocupado, setOcupado] = useState(false);
  /// Por que a notificação do sistema não vai sair, quando não vai.
  const [semSistema, setSemSistema] = useState<string | null>(null);

  const comeca = new Date(programa.starts_at);
  const jaComecou = comeca.getTime() <= Date.now();
  const duracao = Math.round(
    (new Date(programa.ends_at).getTime() - comeca.getTime()) / 60000,
  );

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && onFechar();
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [onFechar]);

  const agendar = async () => {
    setOcupado(true);
    setErro(null);
    try {
      if (agendado) {
        await api.deleteReminder(programa.id);
        setAgendado(false);
        setSemSistema(null);
      } else {
        // A permissão é pedida no clique, não na carga da página: navegador
        // esconde o pedido feito sem gesto do usuário, e ninguém entende por
        // que o aviso nunca chega.
        if ("Notification" in window && Notification.permission === "default") {
          await Notification.requestPermission();
        }
        await api.createReminder(programa.id);
        setAgendado(true);
        // R44 — **e o que o navegador respondeu é dito.**
        //
        // Sem isto o agendamento ficava verde e a notificação do sistema nunca
        // chegava, porque a permissão estava negada de uma vez anterior — e
        // quem agendou não tinha como saber. É o §8b com outra roupa: o produto
        // mostrando sucesso pra um recurso que ele sabe que não vai entregar.
        //
        // Não é erro, e por isso não é `setErro`: o agendamento **funcionou**, e
        // o aviso dentro do Odeon continua chegando. O que mudou é o alcance.
        setSemSistema(motivoSemSistema());
      }
      onMudou();
    } catch (e) {
      setErro(e instanceof Error ? e.message : String(e));
    } finally {
      setOcupado(false);
    }
  };

  return (
    <div
      className="drawer-backdrop"
      onClick={(e) => e.target === e.currentTarget && onFechar()}
    >
      <aside className={programa.arte ? "modal-programa com-arte" : "modal-programa"}>
        {programa.arte && (
          <div
            className="modal-arte"
            style={{ backgroundImage: `url(${api.artworkUrl(programa.arte)})` }}
          />
        )}
        <div className="modal-corpo">
          <header className="drawer-head">
            <div>
              <p className="kind-label">
                {canal?.number && <span className="canal-num">{canal.number}</span>}
                {canal?.name ?? "canal"}
              </p>
              <h2>{programa.title}</h2>
              {programa.sub_title && <p className="muted">{programa.sub_title}</p>}
            </div>
            <button className="ghost" onClick={onFechar}>
              fechar
            </button>
          </header>

          <p className="modal-horario">
            {hhmm(programa.starts_at)} – {hhmm(programa.ends_at)}
            <span className="muted">
              {" · "}
              {duracao} min
              {programa.year && ` · ${programa.year}`}
              {programa.categoria && ` · ${programa.categoria}`}
            </span>
          </p>

          {programa.description && <p className="overview">{programa.description}</p>}

          {programa.work_id && (
            <p className="muted small">
              Esta obra está na sua biblioteca — dá pra assistir do começo em vez de
              pegar no meio.
            </p>
          )}

          <div className="modal-acoes">
            {jaComecou ? (
              <p className="muted small">Já começou — só dá pra sintonizar o canal.</p>
            ) : (
              <button
                className={agendado ? "player-btn on" : "play pequeno"}
                onClick={agendar}
                disabled={ocupado}
              >
                {ocupado ? "…" : agendado ? "◔ agendado — cancelar" : "avisar quando começar"}
              </button>
            )}
          </div>

          {semSistema && <p className="muted small">{semSistema}</p>}
          {erro && <p className="error">{erro}</p>}
        </div>
      </aside>
    </div>
  );
}

/// O que você agendou, e quando começa.
///
/// **É o §27 de novo**: `GET /api/live/reminders` existe desde a R17 e nunca
/// teve cliente nenhum — dava pra agendar um programa e não dava pra ver o que
/// estava agendado. O pedido *"adicionar uma notificação para receber os
/// agendamentos"* é sobre o aviso chegar; mas um agendamento que a pessoa não
/// consegue reler é um compromisso que só o servidor conhece.
///
/// Some quando não há nada — uma seção vazia dizendo "nenhum agendamento" é a
/// linha que o §24 manda sumir.
function Agendados({
  lista,
  onAbrir,
}: {
  lista: Lembrete[];
  onAbrir: (programmeId: number) => void;
}) {
  if (lista.length === 0) return null;

  return (
    <section className="agendados">
      <header className="dial-topo">
        <h2>Você agendou</h2>
        <span className="dica">o aviso chega quando começar</span>
      </header>
      <ul className="agendados-lista">
        {lista.map((l) => (
          <li key={l.programme_id}>
            <button onClick={() => onAbrir(l.programme_id)}>
              <b>{l.title}</b>
              <span className="muted">{l.channel_name}</span>
              <i>{quando(l.starts_at)}</i>
            </button>
          </li>
        ))}
      </ul>
    </section>
  );
}

/// "hoje 21:30", "amanhã 14:00", "sábado 20:00".
///
/// O dia da semana em vez da data: um agendamento é sempre nos próximos dias —
/// a grade do IPTV vai a 38 horas —, e ninguém conta nos dedos até "05/08".
function quando(iso: string): string {
  const d = new Date(iso);
  const hoje = new Date();
  const dia = (x: Date) => new Date(x.getFullYear(), x.getMonth(), x.getDate()).getTime();
  const dias = Math.round((dia(d) - dia(hoje)) / 86_400_000);
  const hora = hhmm(iso);
  if (dias <= 0) return `hoje ${hora}`;
  if (dias === 1) return `amanhã ${hora}`;
  if (dias < 7) return `${d.toLocaleDateString("pt-BR", { weekday: "long" })} ${hora}`;
  return `${d.toLocaleDateString("pt-BR", { day: "numeric", month: "short" })} ${hora}`;
}

/// O que dizer quando a notificação do sistema não vai sair.
///
/// Três estados diferentes, e três frases diferentes — "não vai funcionar" sem
/// dizer por quê é a mesma coisa que não dizer nada. `null` é o caso bom.
function motivoSemSistema(): string | null {
  if (!("Notification" in window)) {
    return "Este navegador não tem notificação de sistema — o aviso vai aparecer aqui dentro, com o Odeon aberto.";
  }
  if (Notification.permission === "denied") {
    return "As notificações estão bloqueadas para este site, então o aviso só vai aparecer aqui dentro. Dá pra liberar no cadeado da barra de endereço.";
  }
  if (Notification.permission === "default") {
    return "Sem permissão de notificação, o aviso só aparece com o Odeon aberto.";
  }
  return null;
}

/// Ouve o barramento e avisa quando um programa agendado começa.
///
/// Vive no App e não aqui dentro porque o aviso tem que chegar **mesmo com a
/// aba "ao vivo" fechada** — é esse o ponto de agendar.
export function AvisoDePrograma({ userId }: { userId: string }) {
  const [aviso, setAviso] = useState<{
    id: number;
    title: string;
    canal: string;
    atrasado?: boolean;
  } | null>(null);
  const jaVistos = useRef<Set<number>>(new Set(lidos()));

  /// R44 — **o aviso que chegou enquanto ninguém ouvia.**
  ///
  /// Medido, e é o defeito que fazia o agendamento parecer quebrado: o vigia do
  /// servidor marca `notified_at` e publica no barramento **uma vez**. Se
  /// naquele instante não havia aba aberta, o evento morre no ar e o lembrete
  /// nunca mais dispara — quem agendou não recebe nada, e não tem como saber
  /// por quê. É o §8b: falhar em silêncio.
  ///
  /// O conserto não precisa de service worker. Na abertura, o Odeon pergunta o
  /// que está agendado — uma rota que já existe — e recupera o que começou há
  /// pouco e ainda está no ar. Quem abre o Odeon cinco minutos depois vê que
  /// começou; quem abre no dia seguinte não vê nada, que é o certo.
  useEffect(() => {
    api
      .reminders()
      .then((lista) => {
        const agora = Date.now();
        for (const l of lista) {
          const desde = agora - new Date(l.starts_at).getTime();
          if (desde < 0 || desde > ATRASO_MAX_MS) continue;
          if (jaVistos.current.has(l.programme_id)) continue;
          mostrar({ id: l.programme_id, title: l.title, canal: l.channel_name, atrasado: true });
          break; // um por vez: dois avisos empilhados viram um só ilegível
        }
      })
      .catch(() => {});
  }, [userId]);

  /// Põe o aviso na tela — e **só marca como lido quando ele sai dela**.
  ///
  /// Marcar na hora de enfileirar parece igual e não é: se a tela fechar antes
  /// do aviso aparecer, ele fica marcado como visto sem nunca ter sido visto, e
  /// some pra sempre. É a mesma falha do `notified_at` do servidor, um andar
  /// acima — e foi assim que este defeito apareceu, num remount do modo estrito
  /// que engoliu o primeiro aviso.
  const mostrar = useCallback(
    (a: { id: number; title: string; canal: string; atrasado?: boolean }) => {
      setAviso(a);
      // A notificação do sistema é do momento em que ele começa. Recuperar um
      // aviso atrasado não dispara caixinha: quem abriu o Odeon já está aqui.
      if (!a.atrasado && "Notification" in window && Notification.permission === "granted") {
        new Notification("Começando agora no Odeon", {
          body: `${a.title} · ${a.canal}`,
          tag: `odeon-programa-${a.id}`,
        });
      }
      window.setTimeout(() => {
        marcarLido(jaVistos.current, a.id);
        setAviso((atual) => (atual?.id === a.id ? null : atual));
      }, 20_000);
    },
    [],
  );

  useEffect(() => {
    return api.ouvirEventos((ev) => {
      if (ev.type !== "programme_starting") return;
      // O evento vai pra todo mundo; cada aparelho descarta o que não é seu.
      if (ev.user_id !== userId) return;
      if (jaVistos.current.has(ev.programme_id as number)) return;
      mostrar({
        id: ev.programme_id as number,
        title: ev.title as string,
        canal: ev.channel_name as string,
      });
    });
  }, [userId, mostrar]);

  if (!aviso) return null;

  return (
    <div
      className="aviso-programa"
      onClick={() => {
        // Fechar na mão também é ter visto.
        marcarLido(jaVistos.current, aviso.id);
        setAviso(null);
      }}
    >
      {/* "começando" e "já começou" são fatos diferentes, e dizer o segundo
          como se fosse o primeiro mandaria a pessoa correr pra pegar o início
          de um filme que já está na metade. */}
      <span className="selo-vivo">{aviso.atrasado ? "● JÁ COMEÇOU" : "● COMEÇANDO"}</span>
      <strong>{aviso.title}</strong>
      <span className="muted">{aviso.canal}</span>
    </div>
  );
}

/// Até quando vale recuperar um aviso perdido.
///
/// Quinze minutos: tempo de pegar o filme quase do começo, e curto o bastante
/// pra não avisar de algo que já vai na metade. Passou disso, o silêncio é a
/// resposta honesta — o §18 outra vez.
const ATRASO_MAX_MS = 15 * 60_000;

/// Os avisos que este navegador já mostrou.
///
/// No `localStorage` e não em memória: sem isso, recarregar a página traria de
/// volta o mesmo aviso, e o recado viraria ruído — que é como um aviso deixa de
/// ser lido (a mesma razão do recado da locadora sumir sozinho, §49).
const LIDOS = "odeon.avisos-lidos";

function lidos(): number[] {
  try {
    const v = JSON.parse(localStorage.getItem(LIDOS) ?? "[]");
    return Array.isArray(v) ? v.slice(-50) : [];
  } catch {
    return [];
  }
}

function marcarLido(memoria: Set<number>, id: number) {
  memoria.add(id);
  try {
    // Os últimos 50 bastam: a grade não guarda mais que 38 horas, e uma lista
    // que só cresce acabaria maior que o dado que ela protege.
    localStorage.setItem(LIDOS, JSON.stringify([...memoria].slice(-50)));
  } catch {
    /* sem localStorage o aviso repete, e repetir é melhor que sumir */
  }
}
