import { useEffect, useRef, useState } from "react";
import Avatar from "./Avatar";
import { api, type Sala } from "./api";

/// R46 — a sala, ao lado do filme.
///
/// **Conversa ao lado durante a sessão** foi metade do pedido (§4.6), e ela
/// mora aqui: quem está, quem está segurando, e o que foi dito. A outra metade
/// — a sincronia — mora no `Player`, porque é lá que o vídeo está.
///
/// A conversa **fica guardada**: ela é uma tabela, não um canal. Quem entra no
/// meio lê o que já foi dito, que é o que separa uma sala de um chat volátil.
export default function Junto({
  sala,
  aoMudar,
  aoSair,
}: {
  sala: Sala;
  aoMudar: (s: Sala) => void;
  aoSair: () => void;
}) {
  const [texto, setTexto] = useState("");
  const [enviando, setEnviando] = useState(false);
  const fim = useRef<HTMLDivElement>(null);

  useEffect(() => {
    fim.current?.scrollIntoView({ block: "end" });
  }, [sala.conversa.length]);

  const mandar = async () => {
    const t = texto.trim();
    if (!t || enviando) return;
    setEnviando(true);
    try {
      aoMudar(await api.recadoJunto(sala.id, t));
      setTexto("");
    } catch {
      /* o recado que não subiu não derruba a sessão */
    } finally {
      setEnviando(false);
    }
  };

  return (
    <aside className="junto">
      <header className="junto-topo">
        <div>
          <p className="kind-label">assistindo junto</p>
          <h3>{sala.titulo}</h3>
        </div>
        <button className="ghost small-btn" onClick={aoSair}>
          {/* O host não "sai": ele fecha. Dizer "sair" pra quem encerra a sala
              de todo mundo seria esconder o efeito do botão. */}
          {sala.sou_host ? "encerrar" : "sair"}
        </button>
      </header>

      {/* QUEM ESTÁ SEGURANDO. É a consequência que o §4.6 avisou que viria:
          "a conexão mais lenta manda no ritmo de todo mundo". Dizer o nome é o
          que transforma uma espera inexplicável numa espera por alguém — e é o
          que permite ao host decidir, que é uma decisão social. */}
      {sala.esperando.length > 0 && (
        <p className="junto-esperando">
          esperando {sala.esperando.join(" e ")} carregar…
        </p>
      )}

      <ul className="junto-gente">
        {sala.gente.map((g) => (
          <li key={g.user_id} className={g.pronto ? "pronto" : ""}>
            <Avatar nome={g.display_name} tamanho={26} />
            <b>{g.display_name}</b>
            {g.host && <i className="junto-host">host</i>}
            {/* Três estados, três palavras. "ausente" não é "travado": quem
                sumiu já não segura a sala, e a diferença muda o que o host faz. */}
            {g.ausente ? (
              <span className="junto-estado sumiu">sumiu</span>
            ) : (
              !g.pronto && <span className="junto-estado">carregando</span>
            )}
            {sala.sou_host && !g.host && (
              <button
                className="junto-expulsar"
                title={`tirar ${g.display_name} da sala`}
                onClick={() => void api.expulsarJunto(sala.id, g.user_id).catch(() => {})}
              >
                ✕
              </button>
            )}
          </li>
        ))}
      </ul>

      <div className="junto-conversa">
        {sala.conversa.length === 0 && (
          <p className="muted small">Ninguém disse nada ainda.</p>
        )}
        {sala.conversa.map((r) => (
          <p key={r.id}>
            <b>{r.display_name}</b> {r.texto}
          </p>
        ))}
        <div ref={fim} />
      </div>

      <input
        value={texto}
        maxLength={500}
        placeholder="dizer alguma coisa"
        onChange={(e) => setTexto(e.target.value)}
        onKeyDown={(e) => e.key === "Enter" && void mandar()}
      />

      <p className="junto-rodape">
        {sala.sou_host
          ? "você manda no play, na pausa e no ponto"
          : `quem manda é ${sala.host_nome}`}
        {sala.modo === "compartilhado" && " · stream compartilhado"}
      </p>
    </aside>
  );
}
