# Amanhã de manhã — o que está esperando no servidor

Separado de `PEDIDOS-AO-SERVIDOR.md`, que é o arquivo de **argumento** (por que
cada coisa é do servidor e não da tela). Isto aqui é a **ordem de trabalho**: o
que abrir, o que mudar, e como conferir que mudou.

⚠️ **O `PEDIDOS-AO-SERVIDOR.md` tem duas numerações colididas** — há dois «3» e
dois «4», de duas levas diferentes. Enquanto não forem renumerados, aqui embaixo
cada item aponta pela **linha** do arquivo, não pelo número.

Ordenado por **quanto conserta ÷ quanto custa**, não por gravidade. Os três
primeiros somam menos de uma hora e apagam defeito visível em três telas.

---

## ⚠️ Pendente de aparelho: a TV, quando a TCL ligar — 19/08/2026

Três coisas foram feitas no `:tv` **sem ninguém ver rodando**. Elas compilam, e é
tudo que se pode dizer delas. Quando a TCL voltar, é isto que precisa ser olhado,
nesta ordem:

| | o que testar | como saber que falhou |
|---|---|---|
| 1 | entrar num **canal de fora** (Tela Quente, Videoteca, Sessão Seriado) | **som sem imagem**. A superfície de vídeo deixou de nascer dentro de `if (player != null)`; no celular essa mesma forma dava 17 de 17 canais pretos |
| 2 | derrubar a rede no meio de um canal e usar os dois recados | «não deu pra sintonizar» agora tem `tentar de novo`, e o «a transmissão parou» **re-sintoniza** em vez de `prepare()` — que falhava na hora quando a sessão do servidor tinha morrido |
| 3 | decidir se a sala quer o que o celular e o web ganharam no dia 19 | a ponte do `collection_id` (bloco de série → ficha da série) e o cartão de **próximo episódio** não existem na TV |

As medições que justificam cada um estão em `REDESENHO.md` §23, §24 e §25. A regra
da casa vale aqui como em tudo: **ver na tela antes de escrever que funciona.**

---

## Antes de começar: o que dá pra conferir sem sair do lugar

Três dos itens se conferem **na mesma tela** — o painel de filtros da biblioteca,
no celular. Vale abrir ele antes e depois:

```
biblioteca → filtros ▾
```

Hoje ele mostra `FORMATO` · `GÊNERO` · **`COUNTRY`**, e a lista de gênero tem
`Comédia 3228` e `Comedy 43` como coisas diferentes.

---

## 1 · Rótulo do `country` — 10 minutos

**O defeito:** o painel de filtros escreve **`COUNTRY`** em caixa alta no meio de
`FORMATO` e `GÊNERO`. Medido em 16/08/2026.

**Onde:** a tabela de `EspacoDeEtiqueta` (o que preenche o `label` de cada
namespace na resposta de etiquetas). `format` e `genre` já têm; `country` não.

**O que fazer:** dar rótulo a **todo** namespace que a API expõe — não só ao
`country`. Se um namespace novo aparecer sem rótulo amanhã, o defeito volta.

**Como conferir:** abrir `filtros ▾`. Tem de ler `PAÍS`.

> ⚠️ A ficha **já foi consertada do lado do cliente** (`Etiqueta.rotulo` traduz o
> que conhece e omite o resto). Isto aqui é só o painel de filtros, que mostra o
> que o servidor mandou — de propósito, pra um rótulo novo do servidor aparecer
> sozinho.

---

## 2 · Rótulos de áudio chegam como `por` e `eng` — 10 minutos

**O defeito:** o seletor de faixas mostra o código ISO em vez do nome. Anotado no
iOS, mesma origem para os quatro clientes.

**O que fazer:** mandar o nome pronto, como já é feito com o rótulo composto das
faixas (`FaixaDeAudio.label`).

**Como conferir:** no player, abrir faixas de áudio. Tem de ler «Português», não
`por`.

> É irmão do item 1: as duas são a mesma pergunta — «quem traduz código em nome?».
> A resposta escrita neste repositório é *o cliente, quando é desenho; o servidor,
> quando é rótulo composto*. Rótulo de faixa é composto.

---

## 3 · «você costuma terminar Canadá (55%)» — 30 minutos

**O defeito:** a aba «para você» mostrou três cartões seguidos assim, medido em
16/08/2026:

- «você costuma terminar **Canadá** (55%)»
- «você costuma terminar **Crime** (22%)»
- «você costuma terminar **Alemanha** (33%)»

**Onde:** o `reasons` do `GET /api/curation/for-you`.

**O que fazer:** compor a frase **com o tipo da etiqueta**, que o servidor já tem
e está descartando na hora de montar o texto:

| etiqueta | hoje | deveria |
|---|---|---|
| `country/Canadá` | «terminar Canadá» | «terminar filmes **do** Canadá» |
| `genre/Crime` | «terminar Crime» | «terminar filmes **de** Crime» |

⚠️ A preposição depende do namespace, e é por isso que ela não pode ser montada na
tela: o cliente recebe a frase pronta e a decisão de que ela vem pronta está
escrita (`PlanoDeReproducao.reasons`). Montá-la aqui seria a quarta redação.

**Como conferir:** aba «para você». Nenhum cartão pode terminar um país.

> É a tela que carrega a tese do produto — «uma biblioteca que te conhece» — e
> hoje ela fala errado em **todos** os cartões. Por isso está em terceiro e não em
> sétimo.

---

## 4 · Gêneros duplicados em dois idiomas — 1 a 2 horas

**O defeito:** o mesmo gênero contado duas vezes, em português e em inglês:

```
Comédia 3228   ·   Comedy 43
Ação 288       ·   Action 43
Aventura 230   ·   Adventure 43
Ficção científica 160  ·  Sci-Fi 43
```

Quem filtra por «Comédia» perde 43 filmes; quem filtra por «Comedy» perde 3.228.

**O que fazer:** casar na origem (uma tabela de sinônimos na ingestão, ou
normalizar o que vem da segunda fonte).

⚠️ **Cuidado com `Action & Adventure` (959)**: ele existe como grupo próprio e
**não** é a soma de `Ação` com `Aventura`. Casar por palavra solta o quebraria.

**Como conferir:** `filtros ▾` → a lista de gênero não pode ter duas entradas para
o mesmo gênero.

---

## 5 · A playlist de HLS é `EVENT` — meia manhã

**O defeito:** o player vira transmissão em vez de filme — a barra não sabe a
duração total e o «faltam» mente enquanto o transcode não alcança.

**Onde:** `PEDIDOS-AO-SERVIDOR.md`, a seção «A playlist de HLS é `EVENT`»
(linha 377).

**O que fazer:** playlist `VOD` com `#EXT-X-ENDLIST`, ou a duração total
declarada desde o primeiro segmento.

**Como conferir:** abrir um filme que **não** toca direto (que precise de
transcode) e olhar a barra: ela tem de nascer com a duração certa.

> Conserta os **quatro** clientes de uma vez. É o item de maior alcance da lista, e
> está em quinto só porque custa mais que os quatro de cima somados.

---

## 6 · O guia conta rips, a biblioteca conta grupos — meia manhã

**O defeito:** os dois números discordam para o mesmo acervo. Medido: 216 + 12 =
228 de um lado, outro número do outro.

**Onde:** `PEDIDOS-AO-SERVIDOR.md`, seção «O guia conta rips» (linha 336).

**Como conferir:** o total do guia e o da biblioteca têm de fechar.

⚠️ **Medida nova, de 17/08/2026, e ela é mais fácil de conferir que a original:**
o eixo «décadas» do guia diz **2000 · 256**; tocar nele leva à biblioteca filtrada
por aquela década, que diz **273**. Mesmo filtro, mesma década, dois números, uma
tela de distância — e um toque separa os dois, o que faz desta a forma mais rápida
de reproduzir o problema.

---

## 7 · Um token de mídia novo aposenta o anterior — precisa de decisão, não só de código

**O defeito:** cada cliente que renova o token de mídia **derruba os outros**. Com
celular e TV abertos ao mesmo tempo, um mata a arte do outro.

**O que já foi feito do lado de cá:** os clientes renovam **uma** vez, com trava,
justamente para não entrar em corrida — o que reduz o sintoma e não resolve a
causa.

**A pergunta que precisa de resposta antes do código:** o token de mídia é *por
conta* ou *por aparelho*? Se for por aparelho, o problema some sozinho.

---

## 8 · `POST /api/locadora/pegar` devolve 403 sem o app poder prever — investigar

**O defeito:** o botão «pegar a fita» leva a 403 de forma imprevisível, então ele
**não é oferecido** em lugar nenhum (§53: não oferecer o que a validação nega).

**Onde:** `PEDIDOS-AO-SERVIDOR.md`, seção 1 (linha 18), com o que já foi medido e
uma hipótese que é do app e **pode estar errada**.

**Por que está por último:** é o único item da lista que começa por investigação e
não por conserto — e é o único cujo defeito hoje está **escondido** (o botão não
existe), em vez de visível na tela.

---

## Fora da lista de defeitos: duas funcionalidades da TV

Não são conserto, são coisa nova. Ficam aqui só pra não sumirem:

- **Token de arte de vida longa**, pra a fileira do Odeon na home da Google TV
  continuar com capa dias depois, com o app fechado (`PEDIDOS`, linha 260).
- **Entrar na TV pelo celular**, um código curto trocado por sessão
  (`PEDIDOS`, linha 296).

---

## Uma arrumação de casa, quando der

Renumerar o `PEDIDOS-AO-SERVIDOR.md`. Hoje ele tem **dois «3» e dois «4»**, de
duas levas escritas em momentos diferentes, e por isso este arquivo aponta por
linha. Enquanto estiver assim, «o Pedido 3» é uma frase ambígua nos dois
repositórios.

---

# ✅ Resposta do servidor — 17/08/2026

Sete dos oito entregues. **Conferido na tela deste lado**, e o que sobrou virou
trabalho de cliente.

| item | na tela |
|---|---|
| 1 · rótulo do `country` | painel de filtros lê **FORMATO · GÊNERO · PAÍS · IDIOMA** |
| 3 · «terminar Canadá» | «você costuma terminar **filmes do** Canadá (54%)» |
| 4 · gêneros duplicados | `Comédia 3271`; `Comedy`, `Action`, `Adventure`, `Sci-Fi` sumiram da lista |
| 6 · guia × biblioteca | o eixo «2000» diz **273**, e tocar nele leva a **273** — era 256 ≠ 273 |

## O item 5 virou trabalho nosso, e a recusa foi bem medida

A playlist `VOD` **não vem**, e o argumento é melhor que o pedido: declarar `VOD`
exige o tamanho de cada segmento antes de produzi-lo, e no caminho `video=copy`
isso são os keyframes da fonte — **1m33s por arquivo** contra 25s de espera da
playlist. Metade dos filmes ficaria certa e metade errada, sem ninguém saber qual.

No lugar veio `duration_seconds` no `/api/playback/{id}/plan` **e** na resposta de
sessão. É o que faltava: quem desenha a barra é o cliente, e o que faltava era o
denominador.

**Feito nos dois clientes nativos**, e visto na tela:

- «007 Contra Octopussy» (transcodificando): `3:04` · **faltam 2:07:44** — soma
  2h10, a duração da ficha.
- Canal «Odeon 1» com «Lego Batman»: `11:11` · **faltam 1:33:16**. Este é o caso
  que estava quebrado: o canal abre o player **sem duração nenhuma**, e o
  denominador virava o trecho já gerado.

## A trava de renovação: metade do motivo acabou

O servidor autoriza tirá-la, e tirar **inteira** seria trocar um defeito por
outro. Ela tinha duas justificativas:

| motivo | hoje |
|---|---|
| um 401 que não é de token vira laço | **continua valendo** |
| cada renovação aposenta o token dos outros aparelhos | ❌ acabou (token por aparelho) |

Então ela mudou de **alcance** em vez de sumir: era «uma vez por abertura, para
sempre», virou «uma vez por tentativa» — quem volta a tocar zera a trava. Um filme
de três horas cujo token vença duas vezes agora renova duas vezes, que era o caso
que a trava velha condenava a um «o filme não abriu» no meio da sessão.

## O que ainda não fiz, e é o próximo passo

O item 8 trouxe `caixa_chave` e `caixa_ids` na prateleira, e a descoberta de que
**não existe** `/api/locadora/pegar` — é `alugar`, e ela aceita qualquer obra. Com
os `caixa_ids` o app finalmente **consegue prever** o 403 («esta já está com
você»), que é a condição que o §53 exigia pra o botão «pegar a fita» voltar a ser
oferecido.

Isso é reabertura de funcionalidade, não conserto de linha — merece um passo
próprio, com a ficha decidindo o que oferecer a partir dos ids.
