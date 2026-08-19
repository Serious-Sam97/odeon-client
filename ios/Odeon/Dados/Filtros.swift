import Foundation

/// O que a biblioteca está mostrando além de «tudo».
///
/// ## ⚠️ É uma **fatia** do filtro do Android, e a fatia é a decisão
///
/// O `Filtros.kt` de lá tem doze campos — busca, tipo, etiquetas, modo, ano,
/// minutos, coleção, estado, pessoa, ordem. Ele é grande porque **a tela de
/// filtros do Android existe**, com painel, chips e faixas de duração.
///
/// Aqui não há painel. O que existe é o guia, e o guia liga exatamente três
/// coisas: uma etiqueta (`genre:Terror`), uma década (`year_from`/`year_to`) e o
/// `kind=movie` que vem junto das duas. Declarar os outros nove seria escrever a
/// terceira cópia de um contrato que nenhuma tela deste app monta — e contrato
/// que ninguém confere envelhece calado. Eles entram quando o painel entrar.
///
/// ## ⚠️ E `kind=movie` **não** é detalhe
///
/// É da web, e o Android o herdou: gênero e década no guia são «só filmes». Sem
/// ele, tocar em «Comédia» traria 3.220 entradas em que a maioria é episódio de
/// série — e o número que a pílula do guia prometia era o dos filmes. A pílula
/// diria 400 e a grade mostraria 3.220: o filtro mentindo sobre si mesmo.
struct Filtros: Equatable, Sendable {
    /// `namespace:valor` — `genre:Terror`. A mesma string que vai pro servidor,
    /// sem tradução no meio.
    /// ## A prateleira: `format:série`, `format:filme`, ou nenhuma
    ///
    /// ⚠️ **Separada da [etiqueta] de propósito**, mesmo indo pro mesmo `?tags=`.
    /// Misturá-las faria «limpar» apagar a prateleira junto — e prateleira não é
    /// filtro: é em qual metade do acervo você está. Quem está nas séries e
    /// limpa os filtros continua nas séries.
    var prateleira: String?
    /// ## As etiquetas que **tiram** — `?tags_not=` · 18/08/2026
    ///
    /// A aba dos filmes é «tudo que não é série». `?tags=` só soma, e fixar
    /// `format:filme` deixaria de fora as 2.182 entradas que o scanner não
    /// classifica.
    ///
    /// ⚠️ Semântica **`any`**, sem opção de trocar: sai quem tiver qualquer uma.
    /// ⚠️ Lista vazia **não vira parâmetro** — ver o repositório.
    var excluindo: [String] = []
    var etiqueta: String?
    var anoDe: Int?
    var anoAte: Int?
    var tipo: String?
    /// `featured` · `title` · `year` · `added` · `duration` · `random`.
    var ordem: String?

    /// O que fica escrito na tela quando o filtro está ligado.
    ///
    /// ⚠️ Ele vem **de quem ligou o filtro**, e não é remontado a partir dos
    /// campos: quem tocou em «Terror» sabe que escreveu «Terror», enquanto daqui
    /// só se vê `genre:Terror`, e traduzir de volta seria inventar a metade da
    /// string que o servidor nunca prometeu ser legível.
    var rotulo: String = ""

    /// ⚠️ A prateleira **não conta** como filtro ligado — ver o comentário
    /// dela. É o que faz o «limpar» aparecer só quando há o que limpar.
    var ligado: Bool { etiqueta != nil || anoDe != nil || tipo != nil }

    /// O filtro que um eixo do guia vira.
    ///
    /// ## ⚠️ As duas formas do `chave`, e por que só ele decide
    ///
    /// O servidor manda `genre:Terror` nos gêneros **e nos países** — o mesmo
    /// formato da barra de filtros — e **o ano** (`1980`) nas décadas. Distinguir
    /// pelo conteúdo, e não pelo nome da fileira de onde o toque veio, é o que faz
    /// esta função não precisar saber de onde foi tocada.
    static func doEixo(_ faixa: FaixaDoGuia) -> Filtros {
        if let decada = Int(faixa.chave) {
            /// ⚠️ Dentro de uma década, a ordem é por **ano**. «Em destaque»
            /// embaralharia dez anos de cinema, e a década deixaria de contar a
            /// história que é o motivo de ela ser um eixo.
            Filtros(anoDe: decada, anoAte: decada + 9, tipo: "movie", ordem: "year",
                    rotulo: faixa.rotulo)
        } else {
            Filtros(etiqueta: faixa.chave, tipo: "movie", rotulo: faixa.rotulo)
        }
    }
}
