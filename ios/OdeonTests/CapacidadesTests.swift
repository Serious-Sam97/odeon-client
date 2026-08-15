import Testing
@testable import Odeon

/// O que o aparelho respondeu sobre o que sabe tocar.
///
/// ## ⚠️ Este arquivo é **medida**, e não só verificação
///
/// A afirmação «o AVPlayer não lê Matroska» entrou no `PLANO.md` herdada de um
/// comentário do Android **sobre a web** — ou seja, era afirmação de terceiro
/// sobre outra plataforma. Este teste existe pra transformá-la em número medido
/// nesta, que é o método que o §26 do `REDESENHO-TV.md` custou caro pra ensinar.
///
/// ⚠️ **Rodando no simulador, isto é indício e não achado.** O simulador usa o
/// silício do Mac. A conferência que fecha é no iPhone do dono.
struct CapacidadesTests {

    /// Imprime o que este aparelho respondeu. Não afirma nada — serve pra o
    /// número existir no log de quem rodar, e pra ele poder ser comparado com o
    /// do aparelho de verdade.
    @Test("o que este aparelho diz saber tocar")
    func registrar() {
        print("── capacidades medidas ──\n\(CapacidadesDoAparelho.medido())")
    }

    /// ⚠️ **A pergunta que decide o custo do iOS no servidor da casa.**
    ///
    /// Se este teste passar, o iOS está do lado da web: onde o Android toca
    /// direto, o iPhone pede remux, e a máquina da casa acende ffmpeg pra mais um
    /// cliente. Se um dia ele **falhar**, é notícia boa — a Apple passou a abrir
    /// Matroska e metade da §4.1 do plano deixa de valer.
    @Test("matroska não está entre os contêineres declarados")
    func semMatroska() {
        #expect(!CapacidadesDoAparelho.conteineres.contains("mkv"))
    }

    /// O mínimo pra este acervo tocar: mp4 e h264 são o que o Star Wars III e o
    /// 007 em inglês são.
    @Test("o básico do acervo é declarado")
    func oBasico() {
        #expect(CapacidadesDoAparelho.conteineres.contains("mp4"))
        #expect(CapacidadesDoAparelho.codecsDeVideo.contains("h264"))
        #expect(CapacidadesDoAparelho.codecsDeAudio.contains("aac"))
    }

    /// ⚠️ **`mp3` NÃO é declarado, e este teste inverteu de propósito.**
    ///
    /// A primeira versão dele afirmava o contrário: o servidor tinha contado 576
    /// arquivos transcodificando à toa, e declarar mp3 parecia ganho puro. A prova
    /// (`ProvaDeMp3`) mostrou que o iOS decodifica mp3 em `.mp3` e em `.mov` e
    /// **não** em `.mp4` — onde a faixa nem é enumerada.
    ///
    /// O sintoma de errar aqui não é «não abre», é **tocar mudo** — e o contrato
    /// não tem como dizer «mp3 sim em mov, não em mp4». Enquanto não tiver, o
    /// seguro é ficar de fora.
    ///
    /// Se este teste começar a falhar, alguém redeclarou mp3: confira antes se a
    /// pergunta do §4.1b do plano (contêiner dos segmentos HLS) foi respondida.
    @Test("mp3 fica de fora — tocaria mudo em mp4")
    func mp3NaoDeclarado() {
        #expect(!CapacidadesDoAparelho.codecsDeAudio.contains("mp3"))
    }

    /// ⚠️ Os dois falsos positivos que a sonda pegou: as respostas vinham do
    /// **contêiner**, não do codec. Declará-los pediria ao servidor um arquivo que
    /// o app não abre — o segundo sentido em que a lista fixa mente.
    @Test("vorbis e msmpeg4v3 não são declarados")
    func semFalsosPositivos() {
        #expect(!CapacidadesDoAparelho.codecsDeAudio.contains("vorbis"))
        #expect(!CapacidadesDoAparelho.codecsDeVideo.contains("msmpeg4v3"))
    }

    /// O que o servidor mediu como ausente também tem que sair ausente daqui.
    @Test("dts e mpeg2video ficam de fora")
    func oQueNaoToca() {
        #expect(!CapacidadesDoAparelho.codecsDeAudio.contains("dts"))
        #expect(!CapacidadesDoAparelho.codecsDeVideo.contains("mpeg2video"))
    }

    /// ⚠️ Nenhuma lista pode sair vazia.
    ///
    /// Lista vazia vira query vazia, e query vazia faz o servidor decidir sem
    /// saber nada do cliente — que é o pior dos dois erros que a regra «perguntar,
    /// não listar» existe pra evitar. Se a API da Apple mudar de forma e as
    /// perguntas pararem de casar, é aqui que aparece.
    @Test("nenhuma resposta sai vazia")
    func nadaVazio() {
        #expect(!CapacidadesDoAparelho.conteineres.isEmpty)
        #expect(!CapacidadesDoAparelho.codecsDeVideo.isEmpty)
        #expect(!CapacidadesDoAparelho.codecsDeAudio.isEmpty)
    }
}
