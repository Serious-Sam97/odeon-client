# Pedidos ao servidor

O que o app **não consegue decidir sozinho** sem afirmar algo que não sabe, ou o
que ele resolve com um paliativo que se sabe temporário. Pronto pro dono levar ao
`serious-server`.

> A regra que este arquivo obedece: *«Você diz ao dono o que precisa. Ele leva o
> pedido pro `serious-server`, onde a mudança é feita.»* O `odeon-server` não é
> aberto daqui, e nenhum destes pedidos supõe uma solução — eles descrevem o que
> falta.

⚠️ **Pedido atendido sai daqui.** O que ficou está na tabela do fim, com a data —
uma linha, pra ninguém repedir o que já existe. A discussão de cada um vive no
histórico do git e nos comentários do código que os consome.

---

## 1. O token de arte já veio — e nós não sabemos como pedir

> Acrescentado em **19/08/2026**. Não é pedido de coisa nova: é o **formato** do
> que já foi entregue no dia 17 (linha 3 da tabela do fim). Sem isso, a entrega
> está no ar e nenhum cliente a usa.

```
o que preciso: como se pede o token de arte de vida longa — rota, corpo da
               resposta e validade. O único token que este cliente conhece é o
               de mídia (`POST /api/auth/media-token`, 8h), e ele roda

por quê:       a capa da notificação de mídia é baixada por **outro processo** —
               o system UI do Android, e o launcher da Google TV dias depois —
               com a URL que a gente entregou. Quando o token de mídia roda,
               essas URLs passam a devolver 401

o que quebra:  medido no emulador em 19/08/2026, com o log do próprio sistema:
               `NotificationProvider: Failed to load bitmap:
                InvalidResponseCodeException` — a notificação sobe sem capa. Na
               TV o efeito é a fileira da home com retângulos vazios

já tentei:     procurar no cliente e nos docs. `grep -r artwork-token` dá zero em
               todo o repositório, e a tabela do fim registra a entrega sem o
               formato — o corpo do pedido saiu daqui quando ele foi atendido
```

⚠️ **A lição de processo, e ela é nossa**: pedido atendido sai deste arquivo, e
com ele saiu a única descrição do que foi combinado. O que sobrou na tabela — «3 ·
token de arte de vida longa · 17/08/2026» — não basta pra escrever uma linha de
código. Da próxima vez, o formato entregue fica na tabela.

---

## Nenhum outro pedido aberto

Os dois últimos — `hevc10` e o casamento do EPG — foram entregues em 18/08/2026,
no mesmo dia em que foram escritos. O que já veio está na tabela do fim.

⚠️ **Isto é o estado normal deste arquivo, e não um vazio a preencher.** Ele só
tem seção numerada quando há algo que o app não consegue decidir sozinho.

---

## Parado por decisão conjunta

### A playlist VOD (o segmentador sob demanda)

O sintoma que doía — `«4:44 AM»` no lugar da duração — **foi resolvido** pelo
`duration_seconds`, que os quatro clientes já consomem, e a playlist ganha
`#EXT-X-ENDLIST` quando o ffmpeg termina. O que continua aberto é a **janela até
lá**: a barra segue sem fim conhecido enquanto a sessão está viva.

O time mediu e decidiu não entregar meia solução — declarar a playlist inteira
exige saber o tamanho de cada segmento antes de produzi-lo, e no caminho
`video=copy` isso são os keyframes da fonte (1m33s por arquivo pra levantar,
contra 25 s de espera da playlist). Depois do `-forced-idr`, só o caminho de
**encode** ficou previsível — e entregar VOD só nele deixaria a barra certa em
alguns filmes e errada em outros **sem o usuário ter como saber qual**.

⚠️ **Concordamos, e por isso isto não é um pedido.** Fica registrado como projeto
próprio: um segmentador sob demanda por nome. Está aqui pra ninguém repedir como
se fosse item de lista.

---

## O que **não** é pedido de servidor

Fica escrito porque a §1b diz que «boa parte do que parece exigir servidor não
exige», e vale registrar o que foi conferido e resolvido aqui:

| | resolvido onde |
|---|---|
| `height` e `size_bytes` da grade | **já vinham** no `/api/library`; o modelo Android é que os descartava |
| as etiquetas da ficha | **já vinham** no `/api/works/{id}`; idem |
| a capa no controle de mídia | a URL já existia no app — faltava declarar `MediaMetadata` |
| a mensagem do 403 | é texto do cliente |

---

---

## Já entregue

Uma linha por pedido atendido. O corpo de cada um saiu daqui quando chegou — o
que ele mudou está no código que o consome.

| | pedido | entregue |
|---|---|---|
| 1 | `POST /api/locadora/pegar` devolvia 403 sem o app poder prever | 17/08/2026 |
| 2 | duas entradas para o mesmo filme, com resoluções diferentes | 17/08/2026 |
| 3 | token de arte de vida longa, pra fileira na home da Google TV | 17/08/2026 |
| 4 | entrar na TV pelo celular, com código curto | 17/08/2026 |
| 5 | o guia contava rips; a biblioteca conta grupos | 17/08/2026 |
| 6 | `country` sem rótulo em português, vazando cru na tela | 17/08/2026 |
| 7 | gêneros duplicados em dois idiomas | 17/08/2026 |
| 8 | «você costuma terminar Canadá (55%)» | 17/08/2026 |
| 9 | `work_count` mudava conforme quem perguntava | 18/08/2026 |
| 10 | temporadas com pôster próprio do TMDB, e `overview` por episódio | 18/08/2026 |
| 11 | 88% do acervo sem formato — separar formato de identificação | 18/08/2026 |
| 12 | `?tags_not=` — «tudo menos série», que o `?tags=` não sabia dizer | 18/08/2026 |
| 13 | HEVC em MPEG-TS não é HLS válido — sessão de fonte HEVC passou a sair em fMP4 | 18/08/2026 |
| 14 | `hevc8`/`hevc10` — a profundidade entrou no vocabulário de capacidades | 18/08/2026 |
| 15 | o EPG do ao vivo não casava série nem clipe — 294 → 631 programas ligados | 18/08/2026 |

⚠️ O **14** corrigiu **a nossa proposta**, e o motivo é medido: pedimos que `hevc`
passasse a significar «8 bits», e `hevc` já está no ar em três clientes cujos
aparelhos decodificam Main 10. Estreitar o sentido de uma palavra que já circula
teria virado 5.319 arquivos de cópia em recodificação. A precisão entrou por
palavra nova, nos dois lados — e **este cliente não manda mais `hevc` puro**: diz
`hevc10` ou `hevc8`.

⚠️ O **15** desfez uma hipótese pela metade: nós vimos «série e clipe não casam» e
o servidor achou **três** causas — homônimo que era o mesmo tmdb, série que só
existe como coleção, e a capa exigida como porta em vez de desempate. O
`007 Contra Goldfinger`, que era o caso que não fechava com a nossa leitura, era
justamente o primeiro.

⚠️ O **13** corrigiu uma tese nossa que estava errada: dissemos que o gatilho era
o **E-AC3**, e é o **HEVC** — eles coincidem no Arcane. A medição de vocês (5.319
episódios HEVC contra 6.880 h264, e 2.546 destes com eac3/ac3/dts) é o que separa
as duas coisas, e nós não tínhamos como fazê-la daqui.

⚠️ E o **13** vale pro iOS antes mesmo de alguém reclamar: os 5.374 arquivos HEVC
nunca teriam tocado no iPhone por aquela via, porque o AVPlayer é estrito.

⚠️ O **12** mudou uma medida do app: a aba dos filmes passou de 981 (só os
identificados) para **3.187** — os filmes, os clipes, os animes e as 2.182 que o
scanner não classifica. E o `total` voltou a falar do mesmo conjunto que a grade,
então o cabeçalho e a paginação fecham de novo.

⚠️ O **9** vale ser lembrado: o `finished_count` agregado sobre o filtrado enchia
a barra de progresso com qualquer filtro que deixasse passar só o que já se viu.
Essa barra é desenhada em quatro clientes, e quem pegou foi o servidor.
