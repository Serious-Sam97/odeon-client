import { useSyncExternalStore } from "react";
import { useNavigate } from "react-router-dom";
import { api } from "./api";

/// R50 — quem sabe, no cliente, o que dá pra assistir.
///
/// ## Por que uma loja fora do React
///
/// **Nove telas** oferecem play, e todas precisam da mesma resposta. Passar isso
/// por props atravessaria seis componentes que não têm nada a ver com locadora;
/// um contexto exigiria um provider em volta de tudo pra um dado que muda umas
/// duas vezes por dia. Uma loja de módulo com `useSyncExternalStore` é o que o
/// dado é: **um só, do aplicativo inteiro**, como o `ouvirEventos` da R46.
///
/// ## Ela se atualiza sozinha, pelo barramento
///
/// A lista só muda quando alguém pega ou devolve uma fita — e isso é
/// `AppEvent::Locadora`, que existe desde a R19. Nada de repetir pergunta: a
/// resposta chega quando ela muda.
///
/// E a assinatura entra na **mesma conexão** do resto do aplicativo. Foi o
/// defeito que a R46 pagou caro (§62): quatro `EventSource` comendo o orçamento
/// de seis conexões por host.

interface Estado {
  exige: boolean;
  works: Set<string>;
}

/// `null` = ainda não perguntei. **Não é o mesmo que "nada liberado"**, e a
/// diferença importa: enquanto não sei, não tranco nada — uma tela que nasce
/// dizendo "pegar na locadora" pra tudo, e conserta meio segundo depois, mente
/// duas vezes em vez de uma.
let estado: Estado | null = null;
let voando: Promise<void> | null = null;
const ouvintes = new Set<() => void>();
let desligarBarramento: (() => void) | null = null;

function avisar() {
  for (const o of ouvintes) o();
}

/// Pergunta ao servidor. Chamadas concorrentes compartilham a mesma ida — na
/// montagem várias telas assinam ao mesmo tempo.
export function recarregarLiberadas(): Promise<void> {
  if (voando) return voando;
  voando = api
    .liberadas()
    .then((r) => {
      estado = { exige: r.exige, works: new Set(r.works) };
      avisar();
    })
    .catch(() => {
      /* sem resposta, segue sem trancar nada */
    })
    .finally(() => {
      voando = null;
    });
  return voando;
}

function assinar(aoMudar: () => void): () => void {
  ouvintes.add(aoMudar);
  if (ouvintes.size === 1) {
    desligarBarramento = api.ouvirEventos((e) => {
      if (e.type === "locadora") void recarregarLiberadas();
    });
  }
  if (!estado) void recarregarLiberadas();
  return () => {
    ouvintes.delete(aoMudar);
    if (ouvintes.size === 0) {
      desligarBarramento?.();
      desligarBarramento = null;
    }
  };
}

const ler = () => estado;

/// Esta obra está liberada **com o que eu sei agora**.
function liberadaAgora(workId: string | null | undefined): boolean {
  if (!estado || !estado.exige) return true;
  return !!workId && estado.works.has(workId);
}

/// O gancho das telas: `exige` pra saber se a regra vale, `pode` pra cada obra.
export function useLiberadas() {
  const atual = useSyncExternalStore(assinar, ler, ler);
  return {
    exige: atual?.exige ?? false,
    pode: (workId: string | null | undefined) =>
      !atual || !atual.exige || (!!workId && atual.works.has(workId)),
  };
}

/// A conferência do funil, antes de abrir o player.
///
/// **Ela repergunta antes de dizer não**, e isso não é paranoia: o caminho mais
/// comum de todos é *pegar a fita e dar play em seguida*, e nesse instante a
/// loja local ainda pode ser de um segundo atrás. Barrar quem acabou de pegar a
/// caixa seria o pior "não" possível — o produto negando o que ele mesmo acabou
/// de entregar.
///
/// O custo só existe quando a resposta seria "não": quem está liberado sai na
/// primeira linha.
export async function conferirLiberada(workId: string | null | undefined): Promise<boolean> {
  if (liberadaAgora(workId)) return true;
  await recarregarLiberadas();
  return liberadaAgora(workId);
}

/// A frase, num lugar só.
///
/// Ela aparece em nove telas, e nove redações do mesmo "não" seriam nove
/// oportunidades de uma delas dizer outra coisa. É o mesmo motivo de
/// `acesso::negado()` existir do lado do servidor — e as duas dizem a mesma
/// coisa de propósito: **onde se resolve**, e não só que não pode.
export const PEGAR_NA_LOCADORA = "pegar na locadora";
export const POR_QUE_PEGAR =
  "a locadora está no modo escassez: uma cópia por caixa, e assistir exige pegar a fita";

/// Tocar, ou levar onde a fita se pega.
///
/// **É o gesto inteiro num lugar só.** Cinco dos nove pontos de play são
/// cartões do "para você", e escrever a mesma condicional cinco vezes seria
/// cinco chances de a quinta esquecer. Mesma decisão do gancho de arrasto da
/// R48 e do `ouvirEventos` da R46.
export function useTocarOuPegar<T extends ObraTocavel>(onPlay: (w: T) => void) {
  const { exige, pode } = useLiberadas();
  const navegar = useNavigate();
  return {
    exige,
    pode: (w: T) => pode(w.id),
    /// O `media_file_id` continua mandando quando a obra ESTÁ liberada: uma
    /// obra sem arquivo não toca nem com a fita na mão, e isso é de antes.
    aoClicar: (w: T) => {
      if (!pode(w.id)) return navegar("/locadora");
      if (w.media_file_id) onPlay(w);
    },
  };
}

/// O mínimo que este módulo precisa saber de uma obra. Os cinco cartões do
/// "para você" usam `Recommendation`, os outros usam `WorkListItem` — e os dois
/// têm estes dois campos.
export interface ObraTocavel {
  id: string;
  media_file_id?: string | null;
}
