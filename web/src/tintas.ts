import { useEffect, useState } from "react";

/// As duas tintas da lombada, tiradas da própria capa.
///
/// ## Por que duas, e por que da capa
///
/// A lombada escolhida é a de gráfica de estúdio: um bloco de cor em cima,
/// outro mais escuro embaixo, divididos pelo fio dourado da casa. O servidor
/// manda **uma** cor (`dominant_color`) — serve pro papelão, não pra uma
/// impressão de duas tintas. As duas saem do bitmap da capa, que o navegador já
/// baixou pra desenhar o pôster.
///
/// ## Por que não a média dos pixels
///
/// Porque média devolve marrom em quase todo pôster. Foi medido no Android, no
/// varal de mockups: a média do Ichabod — um pôster de noite azul e roxa — deu
/// **cinza**. O que se quer não é «a cor média», é «a cor que o pôster é», e
/// isso é o pico de um histograma, não o centro dele.
///
/// O Android usa a `Palette` do AndroidX. Aqui não existe equivalente de
/// prateleira, então o que ela faz está escrito abaixo: histograma em HSV,
/// pesado por saturação, com dois alvos — um vivo e um vivo escuro.
export interface TintasDaCapa {
  cima: string;
  baixo: string;
}

/// O que já foi extraído. Uma estante tem quarenta caixas e a rolagem remonta
/// as fileiras; sem isto, cada volta do olho refaria quarenta histogramas.
const memoria = new Map<string, TintasDaCapa>();

/// Extrai as tintas da capa, com o fallback da casa enquanto (ou se) não dá.
///
/// ⚠️ **Sem arte, sem rede, ou ainda carregando: as tintas saem da cor
/// dominante** — o mesmo par clareado/escurecido que a lombada usava antes. Ela
/// nunca fica esperando: nasce no fallback e **troca** quando a paleta chega,
/// que é o mesmo contrato do pôster (nasce cor, vira imagem).
///
/// ## ⚠️ O `crossOrigin`, e o que acontece se o servidor não colaborar
///
/// `getImageData` num canvas contaminado por imagem de outra origem **lança** —
/// e a arte vem da API, que é outro host. Por isso a imagem é pedida com
/// `crossOrigin="anonymous"`, o que exige `Access-Control-Allow-Origin` na
/// `/artwork`. Se o cabeçalho não vier, o `catch` mantém o fallback e a
/// lombada continua com a cor dominante: perde-se o refinamento, não a tela.
///
/// A imagem visível **não** foi tocada de propósito. Pôr `crossOrigin` nela
/// também evitaria um segundo pedido, mas trocaria «lombada menos bonita» por
/// «pôster que não carrega» no dia em que o CORS falhasse — e essa não é uma
/// troca que uma cor justifica.
export function useTintasDaCapa(arte: string | null, corDominante: string | null): TintasDaCapa {
  const base = corDominante ?? "#131318";
  const fallback: TintasDaCapa = {
    cima: mistura("#000000", base, 0.55),
    baixo: mistura("#000000", base, 0.22),
  };

  const [tintas, setTintas] = useState<TintasDaCapa | null>(() =>
    arte ? (memoria.get(arte) ?? null) : null,
  );

  useEffect(() => {
    if (!arte) return;
    const guardado = memoria.get(arte);
    if (guardado) return setTintas(guardado);

    let vivo = true;
    const img = new Image();
    img.crossOrigin = "anonymous";
    img.onload = () => {
      if (!vivo) return;
      const extraidas = extrair(img);
      if (!extraidas) return;
      memoria.set(arte, extraidas);
      setTintas(extraidas);
    };
    img.src = arte;
    return () => {
      vivo = false;
    };
  }, [arte]);

  return tintas ?? fallback;
}

/// 96px bastam: paleta é estatística de cor, não leitura de detalhe — e é o que
/// faz a extração custar milissegundos mesmo com quarenta caixas na tela.
const LADO = 96;

function extrair(img: HTMLImageElement): TintasDaCapa | null {
  const tela = document.createElement("canvas");
  tela.width = LADO;
  tela.height = LADO;
  const ctx = tela.getContext("2d", { willReadFrequently: true });
  if (!ctx) return null;
  ctx.drawImage(img, 0, 0, LADO, LADO);

  let dados: Uint8ClampedArray;
  try {
    dados = ctx.getImageData(0, 0, LADO, LADO).data;
  } catch {
    // Canvas contaminado: o servidor não mandou CORS. O fallback fica.
    return null;
  }

  /// O histograma: 24 faixas de matiz, cada uma somando saturação, valor e
  /// peso. A faixa é de 15°, que é o passo em que dois azuis continuam sendo
  /// «azul» e um azul e um verde não.
  const FAIXAS = 24;
  const peso = new Float64Array(FAIXAS);
  const somaS = new Float64Array(FAIXAS);
  const somaV = new Float64Array(FAIXAS);
  const somaH = new Float64Array(FAIXAS);

  for (let i = 0; i < dados.length; i += 4) {
    if (dados[i + 3] < 128) continue;
    const [h, s, v] = paraHsv(dados[i], dados[i + 1], dados[i + 2]);
    // Cinza não tem matiz, e preto e branco puros não são tinta de gráfica.
    if (s < 0.18 || v < 0.08 || v > 0.96) continue;
    const faixa = Math.min(FAIXAS - 1, Math.floor((h / 360) * FAIXAS));
    // O peso é a saturação: um pixel lavado conta menos que um pixel chapado,
    // que é o que separa «a cor que o pôster tem» de «a cor que o pôster é».
    const p = s * s;
    peso[faixa] += p;
    somaS[faixa] += s * p;
    somaV[faixa] += v * p;
    somaH[faixa] += h * p;
  }

  let melhor = -1;
  let melhorPeso = 0;
  for (let f = 0; f < FAIXAS; f++) {
    if (peso[f] > melhorPeso) {
      melhorPeso = peso[f];
      melhor = f;
    }
  }
  // Capa preto-e-branco: não há matiz dominante nenhum. Inventar vivacidade num
  // pôster sóbrio seria pintar o que a obra não é — o fallback é o certo.
  if (melhor < 0) return null;

  const h = somaH[melhor] / peso[melhor];
  const s = somaS[melhor] / peso[melhor];
  const v = somaV[melhor] / peso[melhor];

  return {
    cima: temperada(h, s, v),
    baixo: temperada(h, s, v, 0.18, 0.4),
  };
}

/// Ajusta uma cor pra faixa em que texto branco lê sobre ela.
///
/// O pico do histograma é a cor **como impressa no pôster** — que pode ser um
/// amarelo de 95% de luz onde branco some, ou um azul de 8% que vira preto. A
/// têmpera prende o valor entre um chão e um teto e dá um empurrão de
/// saturação, que é o que a gráfica faria: tinta chapada, não a foto.
function temperada(h: number, s: number, v: number, chao = 0.34, teto = 0.62): string {
  return deHsv(h, Math.min(1, s * 1.25), Math.min(teto, Math.max(chao, v)));
}

function paraHsv(r: number, g: number, b: number): [number, number, number] {
  const rr = r / 255;
  const gg = g / 255;
  const bb = b / 255;
  const max = Math.max(rr, gg, bb);
  const min = Math.min(rr, gg, bb);
  const d = max - min;
  let h = 0;
  if (d !== 0) {
    if (max === rr) h = ((gg - bb) / d) % 6;
    else if (max === gg) h = (bb - rr) / d + 2;
    else h = (rr - gg) / d + 4;
    h *= 60;
    if (h < 0) h += 360;
  }
  return [h, max === 0 ? 0 : d / max, max];
}

function deHsv(h: number, s: number, v: number): string {
  const c = v * s;
  const x = c * (1 - Math.abs(((h / 60) % 2) - 1));
  const m = v - c;
  const [r, g, b] =
    h < 60
      ? [c, x, 0]
      : h < 120
        ? [x, c, 0]
        : h < 180
          ? [0, c, x]
          : h < 240
            ? [0, x, c]
            : h < 300
              ? [x, 0, c]
              : [c, 0, x];
  return hex((r + m) * 255, (g + m) * 255, (b + m) * 255);
}

/// O lerp do fallback, em sRGB mesmo: ele imita o papelão tingido que a lombada
/// já usava, e aquele também era uma mistura crua.
function mistura(de: string, para: string, quanto: number): string {
  const a = paraRgb(de);
  const b = paraRgb(para);
  if (!a || !b) return para;
  return hex(
    a[0] + (b[0] - a[0]) * quanto,
    a[1] + (b[1] - a[1]) * quanto,
    a[2] + (b[2] - a[2]) * quanto,
  );
}

function paraRgb(cor: string): [number, number, number] | null {
  const m = /^#?([0-9a-f]{6})$/i.exec(cor.trim());
  if (!m) return null;
  const n = parseInt(m[1], 16);
  return [(n >> 16) & 255, (n >> 8) & 255, n & 255];
}

function hex(r: number, g: number, b: number): string {
  const p = (x: number) =>
    Math.round(Math.min(255, Math.max(0, x)))
      .toString(16)
      .padStart(2, "0");
  return `#${p(r)}${p(g)}${p(b)}`;
}
