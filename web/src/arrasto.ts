import { useCallback } from "react";

/// R48 — arrastar uma fileira horizontal com o mouse.
///
/// ## Por que um gancho, e não seis implementações
///
/// É a mesma decisão que fez o `ouvirEventos` da R46 existir depois de quatro
/// telas escreverem as mesmas seis linhas. O gesto é idêntico em toda fileira —
/// a estante da locadora, os canais, a wiki, o "para você", o elenco e as abas
/// em janela estreita —, e o que muda entre elas é só o conteúdo.
///
/// ## Ele devolve um `ref` de função, e isso é de propósito
///
/// Duas das fileiras nascem **dentro de um `map`**: a estante da locadora tem
/// uma por seção, e a wiki uma por eixo. Um `useRef` por instância não existe
/// nesse desenho. Um `ref` de função, sim — o React chama o mesmo callback pra
/// cada elemento, e o React 19 chama de volta a limpeza que ele devolve.
///
/// ## O que ele NÃO faz, e por quê
///
/// **Não mexe em toque.** O dedo já rola de nascença, com inércia que o sistema
/// dá de graça; sequestrar isso seria trocar um gesto bom por uma imitação dele.
/// O pedido foi *"grab and move com mouse"*, e é o que está aqui: `pointerType
/// !== "mouse"` sai na primeira linha.
///
/// **Não inventa inércia.** A fileira anda o que a mão andou, 1 pra 1. Uma
/// rolagem que continua depois que a mão parou é boa num celular, onde o gesto
/// termina com o dedo em movimento; com um mouse ela é a lista escapando.
export function useArrastoDeFileira() {
  return useCallback((el: HTMLElement | null) => (el ? ligar(el) : undefined), []);
}

/// O limiar que separa "arrastei a fileira" de "cliquei num cartão".
///
/// **6px, e o número não é novo:** é o mesmo do `arrastou()` da caixa na mão
/// (§35), que separa "girei pra ver o outro lado" de "toquei na lombada". Duas
/// contas diferentes pro mesmo julgamento fariam o mesmo gesto decidir coisas
/// diferentes em duas telas do mesmo produto.
const LIMIAR = 6;

function ligar(el: HTMLElement) {
  let inicio: { x: number; y: number; scroll: number; id: number } | null = null;
  let arrastando = false;
  /// O gesto acabou de virar rolagem, e o clique que vem atrás dele é resíduo.
  let engolirOClique = false;

  const rola = () => el.scrollWidth > el.clientWidth + 1;

  /// O cursor de mão só aparece quando **há** pra onde rolar.
  ///
  /// Um `grab` numa fileira que cabe inteira na tela é o produto oferecendo um
  /// gesto que ele sabe que não faz nada — o §8b visto do outro lado. E isso
  /// muda sozinho: a estante da locadora é abastecida caixa por caixa (§57), e
  /// a janela encolhe.
  const medir = () => el.classList.toggle("arrastavel", rola());
  medir();
  const ro = new ResizeObserver(medir);
  ro.observe(el);
  /// O `ResizeObserver` vê a fileira mudar de tamanho, não o conteúdo dela
  /// chegar: quando as caixas caem uma a uma, a caixa da fileira não muda e o
  /// `scrollWidth` muda.
  const mo = new MutationObserver(medir);
  mo.observe(el, { childList: true });

  const descer = (e: PointerEvent) => {
    /// Antes de qualquer recusa: um clique novo apaga o resíduo do gesto
    /// anterior. Sem isto, um arrasto que terminou sem clique nenhum deixaria a
    /// tocaia armada, e quem pagaria seria o próximo clique de verdade.
    engolirOClique = false;
    if (e.pointerType !== "mouse" || e.button !== 0 || !rola()) return;
    inicio = { x: e.clientX, y: e.clientY, scroll: el.scrollLeft, id: e.pointerId };
    arrastando = false;
  };

  const mover = (e: PointerEvent) => {
    if (!inicio) return;
    const dx = e.clientX - inicio.x;
    if (!arrastando) {
      if (Math.abs(dx) < LIMIAR) return;
      /// Um gesto mais vertical que horizontal não é desta fileira. Elas moram
      /// dentro de páginas que rolam pra baixo, e roubar o movimento de quem
      /// estava indo pra outro lugar é pior do que não ter gesto nenhum.
      if (Math.abs(e.clientY - inicio.y) > Math.abs(dx)) {
        inicio = null;
        return;
      }
      arrastando = true;
      el.classList.add("arrastando");
      /// Capturar pra que sair da fileira no meio do gesto não o interrompa —
      /// é a mesma razão da caixa na mão (§35), e a mesma armadilha: com o
      /// ponteiro capturado o navegador **entrega o `click` a quem capturou**.
      /// Aqui isso ajuda, porque quem capturou não tem `onClick` nenhum.
      try {
        el.setPointerCapture(inicio.id);
      } catch {
        /* segue sem captura */
      }
    }
    el.scrollLeft = inicio.scroll - dx;
  };

  const subir = () => {
    if (!inicio) return;
    if (arrastando) {
      try {
        el.releasePointerCapture(inicio.id);
      } catch {
        /* já solto */
      }
    }
    engolirOClique = arrastando;
    inicio = null;
    arrastando = false;
    el.classList.remove("arrastando");
  };

  /// O clique que fecha um arrasto tem que morrer aqui.
  ///
  /// Sem isto, arrastar a estante **pegaria uma caixa** no fim do gesto, que é
  /// exatamente o defeito que o `IDEIAS-3.md` §5 mandou não cometer. Na fase de
  /// captura, antes de o cartão ouvir.
  const clique = (e: MouseEvent) => {
    if (!engolirOClique) return;
    engolirOClique = false;
    /// `detail === 0` é clique de teclado — Enter num cartão com foco. Esse
    /// nunca é resíduo de arrasto, e engoli-lo tiraria a fileira de quem não
    /// usa mouse.
    if (e.detail === 0) return;
    e.stopPropagation();
    e.preventDefault();
  };

  /// O arrasto nativo de imagem do navegador começa antes do nosso limiar, e
  /// leva o gesto embora — as fileiras são quase todas de arte. Recusado só
  /// enquanto o botão está apertado sobre esta fileira.
  const semArrastoNativo = (e: DragEvent) => inicio && e.preventDefault();

  el.addEventListener("pointerdown", descer);
  el.addEventListener("pointermove", mover);
  el.addEventListener("pointerup", subir);
  el.addEventListener("pointercancel", subir);
  el.addEventListener("click", clique, true);
  el.addEventListener("dragstart", semArrastoNativo);

  return () => {
    ro.disconnect();
    mo.disconnect();
    el.removeEventListener("pointerdown", descer);
    el.removeEventListener("pointermove", mover);
    el.removeEventListener("pointerup", subir);
    el.removeEventListener("pointercancel", subir);
    el.removeEventListener("click", clique, true);
    el.removeEventListener("dragstart", semArrastoNativo);
    el.classList.remove("arrastavel", "arrastando");
  };
}
