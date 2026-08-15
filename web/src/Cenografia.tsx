import type { Prateleira } from "./api";

/// A cenografia da locadora — «A loja da esquina, 21h».
///
/// ## De onde este arquivo vem
///
/// Ele é a volta de um trabalho que nasceu no Android. O dono pediu «o maior
/// feel possível de locadora, aquela nostalgia», olhou vinte conceitos e
/// escolheu um: **as prateleiras ficam**, e o resto da tela vira matéria —
/// madeira, papel, luz. O app desenhou as quatro peças; aqui elas atravessam
/// pra web, que é onde a locadora nasceu.
///
/// | | |
/// |---|---|
/// | a **arandela** | a luz quente que acende o título no topo |
/// | as **etiquetas penduradas** | as duas contagens da porta, em papel por barbante |
/// | a **plaquinha** | o gênero escrito à mão e preso com fita na madeira |
/// | a **etiqueta de prazo** | o `7 DIAS` colorido, na tábua |
/// | a **nota do caixa** | o saldo impresso no fim da rolagem, com serrilha e carimbo |
///
/// ## O que NÃO veio junto, e por quê
///
/// O app põe o resumo inteiro na nota. Aqui não dá: o balcão da web tem duas
/// coisas que o do app não tem — o **recado ao vivo de 6s** e as devoluções — e
/// o recado é a única saída de um bloqueio (*"um bloqueio só vira porta se quem
/// está com a fita souber que bateram nela"*). Um aviso de seis segundos no pé
/// de oito estantes é um aviso que ninguém lê. Ele ficou em cima; o saldo desceu.
///
/// ## A régua
///
/// Papel é `--papel` e tinta é tinta de papel, as mesmas do bilhete e do rótulo
/// da fita. Nada aqui inventa material que o resto do app não tenha.

/// A arandela: a meia-cúpula de latão e o facho que ela joga na parede.
///
/// É a luz da casa chegando ao topo da loja. O título embaixo dela não tem luz
/// própria — quem brilha é a lâmpada, e o texto está **na** luz.
///
/// ⚠️ O facho é um **círculo**, e não um retângulo com gradiente. O app tentou
/// retângulo e a foto mostrou uma tarja: o gradiente morre fora da caixa e o
/// recorte vira duas arestas retas atravessando o topo. Aqui o mesmo cuidado
/// vira `radial-gradient` com `closest-side` num elemento redondo — o que não é
/// luz não é pintado.
export function Arandela() {
  return (
    <div className="arandela" aria-hidden="true">
      <span className="arandela-facho" />
      <span className="arandela-cupula" />
      <span className="arandela-filamento" />
    </div>
  );
}

/// Uma etiqueta de papel pendurada por barbante — as contagens da porta.
///
/// ⚠️ O torto é **fixo por etiqueta** e vem de fora. Ângulo sorteado mudaria a
/// cada render e a etiqueta tremeria pendurada — a mesma regra do varal.
export function EtiquetaPendurada({
  numero,
  rotulo,
  angulo,
}: {
  numero: number | string;
  rotulo: string;
  angulo: number;
}) {
  return (
    <span className="etiqueta-pendurada">
      <span className="etiqueta-barbante" />
      <span className="etiqueta-papel" style={{ ["--angulo" as string]: `${angulo}deg` }}>
        <b>{numero}</b>
        <i>{rotulo}</i>
      </span>
    </span>
  );
}

/// A plaquinha de papel da estante — o gênero escrito à mão, preso com fita.
///
/// A cor cicla por estante numa paleta fixa de papelaria. Sorteá-la mudaria a
/// cor a cada render; amarrá-la ao gênero criaria um código de cores que
/// ninguém combinou — cor decorativa não pode parecer dado.
export function PlaquinhaDaEstante({ nome, indice }: { nome: string; indice: number }) {
  return (
    <span className="plaquinha" style={{ ["--papelaria" as string]: PAPEIS[indice % PAPEIS.length] }}>
      {nome}
    </span>
  );
}

const PAPEIS = ["#f2dd7c", "#a9c8e8", "#e8b4c0", "#bcdf96", "#e8c89a"];

/// A etiqueta de preço na tábua — o `7 DIAS` colorido das locadoras.
///
/// O número é o **prazo real da casa**; a cor cicla por estante, tinta e não
/// dado. Sem prazo vindo do servidor, sem etiqueta.
export function EtiquetaDePrazo({ dias, indice }: { dias: number; indice: number }) {
  if (dias <= 0) return null;
  return (
    <span className="etiqueta-prazo" style={{ ["--tinta-preco" as string]: TINTAS[indice % TINTAS.length] }}>
      {dias} DIAS
    </span>
  );
}

const TINTAS = ["#eec84a", "#5aa6d8", "#e0798f", "#8fc47a", "#e8a05a"];

/// A nota do caixa — o saldo da loja, impresso, no fim da rolagem.
///
/// ## Por que ela mora no fim
///
/// O resumo de quem-está-com-o-quê ocupava o topo, **antes** de a pessoa ver a
/// loja. No desenho aprovado ele vira o fechamento: você anda pelas estantes e,
/// na saída, o caixa te entrega a notinha — acervo, as pessoas, seu limite, o
/// prazo da casa. É o mesmo dado de antes com a ordem de uma visita de verdade.
///
/// ## O que ela imprime, e tudo é dado do servidor
///
/// | linha | de onde vem |
/// |---|---|
/// | `NO ACERVO` | some se o servidor não mandou |
/// | `PRAZO DA CASA` | `opcoes.prazo_dias` |
/// | as pessoas | `pessoas`, com as três contagens dos chips que ela substitui: fora, `✕` zoadas, `⟲` rebobinadas — *"um placar que só conta o defeito faz de todo mundo réu"* continua valendo no papel |
/// | as devoluções | `devolvidas` — fato consumado, e por isso desceu junto; o que **não** desceu foi o recado ao vivo |
/// | `VOCÊ PODE PEGAR` | `posso_pegar`, e no limite a frase vem com a saída junto |
///
/// O carimbo `VOLTE SEMPRE` é o único enfeite, e é enfeite honesto: não se
/// parece com dado nenhum, como o código de barras do verso.
export function NotaDoCaixa({
  loja,
  noAcervo,
  comoVoltou,
}: {
  loja: Prateleira;
  noAcervo: number;
  /// A tradução de `devolvido_como` pra português de balcão mora na `Locadora`,
  /// junto das outras frases da tela. Ela desce como função pra esta nota não
  /// virar o segundo lugar onde o vocabulário da loja é escrito.
  comoVoltou: (chave: string) => string;
}) {
  /// Quem aparece: quem está com fita **ou** quem tem fama. A fama sobrevive à
  /// devolução — senão ninguém carrega nada.
  const gente = loja.pessoas.filter(
    (p) => p.na_mao > 0 || p.no_meio > 0 || p.zoadas > 0 || p.rebobinou > 0,
  );
  const prazo = loja.opcoes?.prazo_dias ?? 0;

  return (
    <div className="nota-do-caixa">
      <div className="nota-papel">
        <b className="nota-marca">LOCADORA ODEON</b>
        <i className="nota-sub">— acervo da casa —</i>

        <span className="nota-tracejado" />

        {noAcervo > 0 && <LinhaDaNota esquerda="NO ACERVO" direita={String(noAcervo)} />}
        {prazo > 0 && <LinhaDaNota esquerda="PRAZO DA CASA" direita={`${prazo} DIAS`} />}

        {gente.length > 0 && (
          <>
            <span className="nota-tracejado" />
            {gente.map((p) => (
              <LinhaDaNota
                key={p.id}
                esquerda={p.display_name}
                direita={[
                  p.na_mao > 0 && `${p.na_mao} fora`,
                  p.no_meio > 0 && `${p.no_meio} no meio`,
                  p.zoadas > 0 && `✕${p.zoadas}`,
                  p.rebobinou > 0 && `⟲${p.rebobinou}`,
                ]
                  .filter(Boolean)
                  .join(" · ")}
              />
            ))}
          </>
        )}

        {loja.devolvidas.length > 0 && (
          <>
            <span className="nota-tracejado" />
            {loja.devolvidas.map((d) => (
              <LinhaDaNota
                key={`${d.caixa_id}-${d.devolvido_em}`}
                esquerda={d.titulo}
                direita={
                  d.devolvido_por === "prazo"
                    ? `venceu · ${d.quem_nome}`
                    : `${comoVoltou(d.devolvido_como)}${d.atrasada ? " · atrasada" : ""}`
                }
              />
            ))}
          </>
        )}

        <span className="nota-tracejado" />

        {loja.posso_pegar > 0 ? (
          <LinhaDaNota esquerda="VOCÊ PODE PEGAR" direita={`+${loja.posso_pegar}`} />
        ) : (
          <>
            <LinhaDaNota esquerda="VOCÊ ESTÁ NO LIMITE" direita="" />
            <i className="nota-saida">devolva uma pra pegar outra</i>
          </>
        )}

        <span className="nota-carimbo">VOLTE SEMPRE</span>
      </div>
    </div>
  );
}

/// Uma linha da nota: rótulo à esquerda, valor à direita, como recibo imprime.
function LinhaDaNota({ esquerda, direita }: { esquerda: string; direita: string }) {
  return (
    <span className="nota-linha">
      <span>{esquerda}</span>
      <span>{direita}</span>
    </span>
  );
}
