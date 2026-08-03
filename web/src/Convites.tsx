import { useCallback, useEffect, useState } from "react";
import { api, type ConviteNaLista } from "./api";

/// R26 — os convites.
///
/// Convidar alguém pro seu servidor é decisão de dono, então esta tela é do
/// administrador. E ela tem uma responsabilidade que a maioria das telas de
/// convite erra: **avisar que o código aparece uma vez só**, antes de a pessoa
/// fechar a janela.
///
/// O código não fica guardado — o banco tem o SHA-256 dele, como as sessões
/// desde o §9b. Não há "ver de novo"; há "emitir outro".
export default function Convites() {
  const [lista, setLista] = useState<ConviteNaLista[] | null>(null);
  const [para, setPara] = useState("");
  const [novo, setNovo] = useState<{ codigo: string; dias: number } | null>(null);
  const [erro, setErro] = useState<string | null>(null);
  const [ocupado, setOcupado] = useState(false);
  const [copiado, setCopiado] = useState(false);

  const carregar = useCallback(() => {
    api.convites().then(setLista).catch(() => setLista([]));
  }, []);

  useEffect(carregar, [carregar]);

  const convidar = async () => {
    setOcupado(true);
    setErro(null);
    try {
      const r = await api.convidar(para.trim() || undefined);
      setNovo({ codigo: r.codigo, dias: r.expira_em_dias });
      setPara("");
      carregar();
    } catch (e) {
      setErro(e instanceof Error ? e.message : String(e));
    } finally {
      setOcupado(false);
    }
  };

  return (
    <section className="convites">
      <h3>Convidar alguém</h3>
      <p className="muted small">
        Quem entra por convite é <b>convidado</b>: navega o acervo inteiro e só assiste o que
        pegar emprestado na locadora. Diferente de <b>criar conta</b> ali em cima, que faz um
        morador — e morador assiste tudo, sem pegar nada emprestado.
      </p>

      <div className="convite-form">
        <input
          value={para}
          placeholder="pra quem é (opcional)"
          onChange={(e) => setPara(e.target.value)}
          onKeyDown={(e) => e.key === "Enter" && void convidar()}
        />
        <button className="cartaz-play" disabled={ocupado} onClick={() => void convidar()}>
          emitir convite
        </button>
      </div>

      {erro && <p className="error small">{erro}</p>}

      {/* O código, uma vez só. O aviso vem junto e não depois — depois é
          quando a pessoa já fechou a janela. */}
      {novo && (
        <div className="convite-novo">
          <p className="convite-aviso">
            Este código aparece <b>uma vez só</b>. Ele vence em {novo.dias} dias.
          </p>
          <code>{novo.codigo}</code>
          <button
            className="chip"
            onClick={() => {
              void navigator.clipboard?.writeText(novo.codigo);
              setCopiado(true);
              window.setTimeout(() => setCopiado(false), 2000);
            }}
          >
            {copiado ? "copiado" : "copiar"}
          </button>
          <button className="chip" onClick={() => setNovo(null)}>
            guardei
          </button>
        </div>
      )}

      {lista && lista.length > 0 && (
        <ul className="convite-lista">
          {lista.map((c, i) => (
            <li key={`${c.criado_em}-${i}`} className={c.vencido ? "vencido" : ""}>
              <span className="convite-para">{c.para ?? "sem nome"}</span>
              <span className="convite-estado">
                {c.usado_em
                  ? `usado por ${c.usado_por_nome ?? "alguém"}`
                  : c.vencido
                    ? "venceu sem ser usado"
                    : `aberto até ${data(c.expira_em)}`}
              </span>
              {/* Revogar só faz sentido no que ainda está aberto. */}
              {!c.usado_em && !c.vencido && (
                <button
                  className="convite-revogar"
                  onClick={() =>
                    void api.revogarConvite(c.para ?? "").then(carregar).catch(() => {})
                  }
                >
                  revogar
                </button>
              )}
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

function data(iso: string): string {
  return new Date(iso).toLocaleDateString("pt-BR", { day: "numeric", month: "short" });
}
