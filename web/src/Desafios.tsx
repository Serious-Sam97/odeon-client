import { useCallback, useEffect, useState } from "react";
import { api, type Cadencia, type MeusDesafios } from "./api";

/// Os desafios da janela.
///
/// **Três, e eles fazem trabalhos diferentes**: um fácil, um de tema e um que
/// empurra pra fora do seu gosto — que é o único dos três que faz o desafio
/// servir ao terceiro pilar (§1).
///
/// **Falhar não custa nada.** A janela fecha, o desafio some, outro é sorteado.
/// Sem perda de XP, sem sequência quebrada, sem aviso — este projeto tem uma
/// punição só e ela é social (a fita mal devolvida), porque funciona entre
/// pessoas. Punir alguém por não ter assistido um filme é o placar do §40
/// voltando com outra roupa.
///
/// ## Por que ele mora num arquivo só (R41)
///
/// Ele nasceu dentro do `Perfil.tsx` na R35 e passou a aparecer também no "para
/// você", que é onde se **cai** — o perfil é onde se vai de propósito. Copiar a
/// lista pra segunda tela criaria dois lugares pra consertar o mesmo desafio, e
/// eles divergiriam no primeiro conserto.
///
/// **A cadência não vem junto.** Ela é ajuste, e ajuste não se repete em duas
/// telas: continua só no perfil, onde se vai pra mexer nas suas coisas.
export default function Desafios({ compacto = false }: { compacto?: boolean }) {
  const [d, setD] = useState<MeusDesafios | null>(null);

  const carregar = useCallback(() => {
    api.desafios().then(setD).catch(() => {});
  }, []);

  useEffect(carregar, [carregar]);

  const trocar = (c: Cadencia) =>
    void api.salvarCadencia(c).then(carregar).catch(() => {});

  if (!d || d.desafios.length === 0) return null;

  const feitos = d.desafios.filter((x) => x.cumprido_em).length;

  return (
    <section className={compacto ? "desafios compacto" : "desafios"}>
      <header>
        <h3>Seus desafios</h3>
        <span className="desafios-prazo">até {vence(d.desafios[0].vence_em)}</span>
        {/* A cadência é escolhida pela pessoa, entre opções definidas. Três, e
            não cinco: a diferença entre "a cada 4 dias" e "a cada 5" não é uma
            escolha, é um número. */}
        {!compacto && (
          <div className="desafios-cadencia">
            {(
              [
                ["diaria", "todo dia"],
                ["tres_dias", "3 em 3 dias"],
                ["semanal", "toda semana"],
              ] as const
            ).map(([v, r]) => (
              <button key={v} className={d.cadencia === v ? "on" : ""} onClick={() => trocar(v)}>
                {r}
              </button>
            ))}
          </div>
        )}
      </header>

      <ul className="desafio-lista">
        {d.desafios.map((x) => (
          <li key={x.id} className={x.cumprido_em ? "feito" : ""}>
            <span className="desafio-marca">{x.cumprido_em ? "✓" : "□"}</span>
            <b>{x.rotulo}</b>
            <i>+{x.xp} XP</i>
          </li>
        ))}
      </ul>

      {feitos === d.desafios.length && (
        <p className="desafios-fim">
          Você limpou a janela. A próxima vem {vence(d.desafios[0].vence_em)}.
        </p>
      )}
    </section>
  );
}

/// "domingo", "amanhã". Um prazo em data absoluta faz contar nos dedos.
function vence(iso: string): string {
  const ms = new Date(iso).getTime() - Date.now();
  const dias = Math.ceil(ms / 86_400_000);
  if (dias <= 1) return "amanhã";
  if (dias <= 7)
    return new Date(iso).toLocaleDateString("pt-BR", { weekday: "long" });
  return new Date(iso).toLocaleDateString("pt-BR", { day: "numeric", month: "long" });
}
