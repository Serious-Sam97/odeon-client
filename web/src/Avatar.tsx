import { api, hueFromTitle } from "./api";

/// A marca de uma pessoa.
///
/// ## Por que ela é desenhada, e não uma imagem
///
/// É a régua do §12 — *"zero bytes"* —, a mesma que recusou CDN de fonte, que
/// rendeu a trilha sintetizada do menu (§47) e o ícone de controles da barra
/// (§52). Uma marca geométrica na paleta da casa custa nada pra servir, escala
/// em qualquer tamanho e não tem licença pra ninguém checar.
///
/// ## Ela é derivada, e é de propósito
///
/// **Não há avatar escolhido no banco** — isso é o §4.1 do `IDEIAS-2.md`, e é a
/// fase seguinte. Esta marca sai do nome da pessoa por hash: mesma conta, mesma
/// cor, mesma figura, em toda tela e em toda sessão. Não é um espaço reservado
/// esperando arte; é **o padrão de quem ainda não escolheu**, que vai continuar
/// existindo depois que o escolher chegar — ninguém nasce com avatar escolhido.
///
/// A figura vem do mesmo hash da cor, entre quatro. Quatro e não doze: a marca
/// serve pra reconhecer uma pessoa numa lista de três a dez, e para isso a cor
/// já faz quase todo o trabalho — a figura é o que separa duas contas que
/// caíram em cores parecidas.
export default function Avatar({
  nome,
  arte,
  tamanho = 32,
  vendo = false,
}: {
  nome: string;
  /// O rosto escolhido (R43), como caminho de `/artwork/…`. Sem ele, a marca
  /// derivada do nome — que é o padrão de quem não escolheu, e não um buraco.
  arte?: string | null;
  tamanho?: number;
  /// Está assistindo alguma coisa agora. O anel é a mesma informação que a luz
  /// verde da presença dá — aqui ele evita uma segunda marca ao lado da
  /// primeira.
  vendo?: boolean;
}) {
  if (arte) {
    return (
      <img
        className={vendo ? "avatar rosto vendo" : "avatar rosto"}
        src={api.artworkUrl(arte)}
        alt=""
        width={tamanho}
        height={tamanho}
        loading="lazy"
      />
    );
  }

  const hue = hueFromTitle(nome);
  const figura = hueFromTitle(nome + nome) % 4;
  const letra = (nome.trim()[0] ?? "?").toUpperCase();

  return (
    <svg
      className={vendo ? "avatar vendo" : "avatar"}
      width={tamanho}
      height={tamanho}
      viewBox="0 0 40 40"
      aria-hidden="true"
      style={{ ["--hue" as string]: hue }}
    >
      <circle className="avatar-fundo" cx="20" cy="20" r="19" />
      {/* A figura fica atrás da letra e bem apagada: ela é textura, não
          desenho. Uma figura que compete com a inicial deixa a lista mais
          difícil de ler, que é o oposto do que um avatar faz numa lista. */}
      <g className="avatar-marca">
        {figura === 0 && <circle cx="20" cy="20" r="11" />}
        {figura === 1 && <rect x="9" y="9" width="22" height="22" rx="3" />}
        {figura === 2 && <path d="M20 7 L33 30 L7 30 Z" />}
        {figura === 3 && <path d="M20 6 L34 20 L20 34 L6 20 Z" />}
      </g>
      <text className="avatar-letra" x="20" y="20" dominantBaseline="central" textAnchor="middle">
        {letra}
      </text>
      {vendo && <circle className="avatar-anel" cx="20" cy="20" r="18.5" />}
    </svg>
  );
}
