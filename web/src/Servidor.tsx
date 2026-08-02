import { useEffect, useState } from "react";
import { api, type MatchStatus, type ScanStatus, type ScrubStatus } from "./api";

type Saude = Awaited<ReturnType<typeof api.diagnostico>>;

/// As operações de servidor, fora da barra de navegação.
///
/// Elas moravam na topbar, em pílulas iguais às das abas — e a única em amarelo
/// sólido era `identificar`. Quem abria o Odeon pra assistir encontrava um
/// painel de manutenção, e o botão mais gritante da tela era o que menos tem a
/// ver com assistir. Aqui elas ficam juntas, explicadas, e longe do caminho.
///
/// O PROGRESSO continua no fluxo principal (`.scanbar` no App): esconder numa
/// gaveta o aviso de que 17 mil arquivos estão sendo varridos seria perder
/// exatamente a informação que a implantação deste servidor ensinou a mostrar.
export default function Servidor({
  onClose,
  scan,
  match,
  scrub,
  onScan,
  onMatch,
  onScrubChanged,
}: {
  onClose: () => void;
  scan: ScanStatus | null;
  match: MatchStatus | null;
  scrub: ScrubStatus | null;
  onScan: () => Promise<void>;
  onMatch: () => Promise<void>;
  onScrubChanged: (s: ScrubStatus) => void;
}) {
  const [aviso, setAviso] = useState<string | null>(null);
  const [saude, setSaude] = useState<Saude | null>(null);

  useEffect(() => {
    api.diagnostico().then(setSaude).catch(() => {});
  }, []);

  const proteger = (fn: () => Promise<unknown>) => async () => {
    setAviso(null);
    try {
      await fn();
    } catch (e) {
      setAviso(e instanceof Error ? e.message : String(e));
    }
  };

  return (
    <div className="drawer-backdrop" onClick={(e) => e.target === e.currentTarget && onClose()}>
      <aside className="drawer">
        <header className="drawer-head">
          <div>
            <h2>Servidor</h2>
            <p className="muted small">
              Operações longas. Rodam em segundo plano — dá pra fechar isto.
            </p>
          </div>
          <button className="ghost" onClick={onClose}>
            fechar
          </button>
        </header>

        {aviso && <p className="error">{aviso}</p>}

        {saude && <Saudinha s={saude} />}

        <Operacao
          titulo="Varrer"
          descricao="Percorre as pastas e roda ffprobe no que for novo ou tiver mudado."
          estado={scan?.running ? `${scan.files_seen} vistos · ${scan.files_added} novos` : null}
          rotulo={scan?.running ? "varrendo…" : "varrer"}
          ocupado={!!scan?.running}
          onClick={proteger(onScan)}
        />

        <Operacao
          titulo="Identificar"
          descricao="Casa cada obra com TMDB ou AniList. Abaixo de 85% de confiança não escreve nada — manda pra revisão."
          estado={
            match?.running
              ? `${match.works_seen} obras · ${match.matched_auto} casadas · ${match.needs_review} pra revisar`
              : null
          }
          rotulo={match?.running ? "identificando…" : "identificar"}
          ocupado={!!match?.running}
          onClick={proteger(onMatch)}
        />

        <Operacao
          titulo="Sprites"
          descricao="Folha de miniaturas pro preview de seek. Decodifica o arquivo inteiro, então é a mais demorada — uma vez por arquivo, e fica em cache pra sempre."
          estado={scrub?.running ? `${scrub.done} de ${scrub.total}` : null}
          rotulo={scrub?.running ? "gerando…" : "gerar sprites"}
          ocupado={!!scrub?.running}
          onClick={proteger(async () => {
            await api.scrub(false);
            onScrubChanged(await api.scrubStatus());
          })}
        />

        <Operacao
          titulo="Embeddings"
          descricao="Reconstrói o corpus e os vetores de conteúdo, que é o que alimenta “parece com o que você gosta”."
          estado={null}
          rotulo="reconstruir"
          ocupado={false}
          onClick={proteger(() => api.rebuildEmbeddings())}
        />
      </aside>
    </div>
  );
}

function Operacao({
  titulo,
  descricao,
  estado,
  rotulo,
  ocupado,
  onClick,
}: {
  titulo: string;
  descricao: string;
  estado: string | null;
  rotulo: string;
  ocupado: boolean;
  onClick: () => void;
}) {
  return (
    <section className="drawer-section op">
      <h3>{titulo}</h3>
      <p className="muted small">{descricao}</p>
      {estado && <p className="op-estado">{estado}</p>}
      <button className="ghost" onClick={onClick} disabled={ocupado}>
        {rotulo}
      </button>
    </section>
  );
}

/// O painel de saúde.
///
/// Regra do bloco: **só mostra o que está torto**. Um painel que repete "0
/// erros" em cinco linhas ensina a não ser lido, e aí o dia em que houver um
/// erro ele também não será lido. Linha limpa some; linha suja fica, com o
/// número e o motivo.
function Saudinha({ s }: { s: Saude }) {
  const linhas: { rotulo: string; valor: string; grave?: boolean }[] = [];

  if (s.arquivos.com_erro > 0) {
    linhas.push({
      rotulo: "arquivos que o ffprobe recusa",
      valor: String(s.arquivos.com_erro),
      grave: true,
    });
  }
  if (s.arquivos.sumidos > 0) {
    linhas.push({ rotulo: "arquivos sumidos do disco", valor: String(s.arquivos.sumidos), grave: true });
  }

  const horas = s.ao_vivo.horas_de_grade;
  if (horas != null) {
    linhas.push({
      rotulo: "grade de TV à frente",
      valor: `${horas}h`,
      // Abaixo de 24h o vigia reimporta sozinho; ver isto vermelho por muito
      // tempo significa que ele não está conseguindo.
      grave: horas < 12,
    });
  }
  for (const f of s.ao_vivo.fontes) {
    if (f.erro) linhas.push({ rotulo: `fonte ${f.nome}`, valor: f.erro.slice(0, 60), grave: true });
  }

  if (s.identificacao.revisar > 0) {
    linhas.push({ rotulo: "esperando revisão", valor: String(s.identificacao.revisar) });
  }
  if (s.identificacao.sem_identificacao > 0) {
    linhas.push({
      rotulo: "sem identificação",
      valor: String(s.identificacao.sem_identificacao),
    });
  }
  if (s.sprites.prontos < s.sprites.de) {
    linhas.push({
      rotulo: "preview de seek",
      valor: `${s.sprites.prontos} de ${s.sprites.de}`,
    });
  }

  if (linhas.length === 0) {
    return (
      <section className="drawer-section saude">
        <h3>Saúde</h3>
        <p className="muted small">nada torto por aqui.</p>
      </section>
    );
  }

  return (
    <section className="drawer-section saude">
      <h3>Saúde</h3>
      <ul className="saude-lista">
        {linhas.map((l) => (
          <li key={l.rotulo} className={l.grave ? "grave" : undefined}>
            <span>{l.rotulo}</span>
            <b>{l.valor}</b>
          </li>
        ))}
      </ul>
      {s.arquivos.amostra.length > 0 && (
        <details className="saude-detalhe">
          <summary>quais arquivos</summary>
          <ul>
            {s.arquivos.amostra.map((a) => (
              <li key={a.arquivo}>
                <code>{a.arquivo}</code>
              </li>
            ))}
          </ul>
        </details>
      )}
    </section>
  );
}
