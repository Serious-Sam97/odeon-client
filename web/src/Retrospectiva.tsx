import { useEffect, useState } from "react";
import { api, type BlocoDaRetrospectiva, type RetrospectivaDoUsuario } from "./api";

/// R24 — a retrospectiva.
///
/// **Não é um painel de números.** O §6.2 do `IDEIAS.md` separou retrospectiva
/// de placar justamente por isto: uma descreve quem você é, a outra dá ponto.
/// *"Você terminou 7 filmes do Villeneuve"* é conquista honesta porque sai do
/// grafo e não pune ninguém por ter viajado.
///
/// Três regras que esta tela obedece e que não são de estilo:
///
/// * **ela nunca cita o placar** — não há link, não há número dele aqui, e o
///   arquivo não importa nada de lá;
/// * **bloco sem material não aparece.** Medido no acervo: 4 blocos rendem, 2
///   ficam calados por não haver empréstimo nem nota. Uma tela que anuncia
///   "0 empréstimos · 0 notas" é a tela "confiante sobre o que não sabia" da
///   R15 (§26) de novo;
/// * **o rodapé diz quantos calaram**, em vez de deixar a pessoa concluir que
///   o Odeon não sabe nada dela. A tela é curta e explica por quê.
export default function Retrospectiva() {
  const [dados, setDados] = useState<RetrospectivaDoUsuario | null>(null);
  const [erro, setErro] = useState<string | null>(null);

  useEffect(() => {
    let vivo = true;
    api
      .retrospectiva()
      .then((d) => vivo && setDados(d))
      .catch((e) => vivo && setErro(String(e)));
    return () => {
      vivo = false;
    };
  }, []);

  if (erro) return <p className="error">{erro}</p>;
  if (!dados) return <p className="muted small">montando o retrato…</p>;

  return (
    <div className="retro">
      <header className="retro-porta">
        <h2>O seu retrato</h2>
        <p className="muted small">
          {dados.desde
            ? `tudo isto saiu do que você assistiu desde ${data(dados.desde)} — nada foi declarado`
            : "sai do que você assistiu, não do que você declara"}
        </p>
      </header>

      {dados.blocos.length === 0 ? (
        /* Ainda não há retrato. Dizer isso é melhor que desenhar um vazio com
           cara de painel — e é exatamente o que a R15 (§26) não fez. */
        <p className="retro-vazio">
          Ainda não há o que descrever. Abra alguma coisa e volte aqui.
        </p>
      ) : (
        <div className="retro-blocos">
          {dados.blocos.map((b) => (
            <BlocoRetro key={b.chave} bloco={b} />
          ))}
        </div>
      )}

      {dados.calados > 0 && (
        <p className="retro-calados">
          {dados.calados === 1
            ? "Um capítulo ficou de fora por não ter o que contar ainda."
            : `${dados.calados} capítulos ficaram de fora por não terem o que contar ainda.`}
        </p>
      )}
    </div>
  );
}

function BlocoRetro({ bloco }: { bloco: BlocoDaRetrospectiva }) {
  return (
    <section className={`retro-bloco retro-${bloco.chave}`}>
      <h3>{bloco.titulo}</h3>
      <p className="retro-frase">{bloco.frase}</p>

      {bloco.detalhe && bloco.detalhe.length > 0 && (
        <ul className={`retro-itens${bloco.detalhe.some((i) => i.imagem) ? " com-arte" : ""}`}>
          {bloco.detalhe.map((i) => (
            <li key={i.rotulo}>
              {i.imagem && <img src={api.artworkUrl(i.imagem)} alt="" loading="lazy" />}
              <span className="retro-rotulo">{i.rotulo}</span>
              {i.nota && <i>{i.nota}</i>}
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

function data(iso: string): string {
  return new Date(iso).toLocaleDateString("pt-BR", { day: "numeric", month: "long" });
}
