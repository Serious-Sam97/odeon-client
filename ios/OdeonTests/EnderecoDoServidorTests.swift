import Testing
@testable import Odeon

/// Os testes do endereço do servidor.
///
/// ## Por que ele merece teste, sendo «só» manipulação de texto
///
/// Porque **ele é a primeira coisa entre a pessoa e o app**, e quando erra o
/// sintoma é «não conecta» — que é indistinguível de servidor fora do ar, de
/// senha errada e de rede caída. Um defeito aqui não parece defeito daqui.
///
/// ⚠️ E porque esta é a **quarta** cópia da mesma regra (KMP, web, Android,
/// iOS). Cópia sem teste é cópia que diverge em silêncio.
struct EnderecoDoServidorTests {

    // MARK: - Normalizar

    @Test("aceita o que a pessoa realmente digita")
    func hostSolto() {
        #expect(EnderecoDoServidor.normalizar("rog") == "rog")
        #expect(EnderecoDoServidor.normalizar("  rog  ") == "rog")
        #expect(EnderecoDoServidor.normalizar("100.77.253.18") == "100.77.253.18")
    }

    /// ⚠️ O esquema sai **antes** de qualquer limpeza de barra. Escrito na ordem
    /// inversa, o `//` dele é confundido com barra sobrando e `https://` vira
    /// `https:` — que não resolve pra nada e dá «não conecta».
    @Test("preserva o esquema quando veio um")
    func esquemaPreservado() {
        #expect(EnderecoDoServidor.normalizar("https://rog") == "https://rog")
        #expect(EnderecoDoServidor.normalizar("http://rog:8085") == "http://rog:8085")
    }

    @Test("caminho digitado por engano é descartado")
    func caminhoSomeMasHostFica() {
        #expect(EnderecoDoServidor.normalizar("rog/api/") == "rog")
        #expect(EnderecoDoServidor.normalizar("https://rog:8443/qualquer/coisa") == "https://rog:8443")
    }

    @Test("o que não tem host nenhum devolve nulo")
    func semHost() {
        #expect(EnderecoDoServidor.normalizar("") == nil)
        #expect(EnderecoDoServidor.normalizar("   ") == nil)
        #expect(EnderecoDoServidor.normalizar("///") == nil)
        /// Um host precisa ter alguma coisa além de pontuação.
        #expect(EnderecoDoServidor.normalizar("...") == nil)
        #expect(EnderecoDoServidor.normalizar("https://") == nil)
    }

    // MARK: - Candidatos

    /// A razão de o login não perguntar «http ou https?»: quem sabe qual está
    /// ligado é o servidor.
    @Test("sem esquema e sem porta, tenta os dois com as portas do Odeon")
    func doisCandidatos() {
        #expect(EnderecoDoServidor.candidatos("rog") == [
            "https://rog:8443",
            "http://rog:8080",
        ])
    }

    /// ⚠️ **Esquema explícito é respeitado, e o outro não é tentado.** Se a
    /// pessoa escreveu `http://`, tentar https por baixo seria surpresa — e numa
    /// rede de casa, surpresa é o que faz alguém desconfiar do app.
    @Test("com esquema explícito, tenta só aquele")
    func esquemaExplicitoNaoVazaProOutro() {
        #expect(EnderecoDoServidor.candidatos("http://rog") == ["http://rog"])
        #expect(EnderecoDoServidor.candidatos("https://rog:9999") == ["https://rog:9999"])
    }

    @Test("porta explícita sem esquema pode ser qualquer um dos dois")
    func portaSemEsquema() {
        #expect(EnderecoDoServidor.candidatos("100.77.253.18:8085") == [
            "https://100.77.253.18:8085",
            "http://100.77.253.18:8085",
        ])
    }

    @Test("nada digitado não gera candidato")
    func semCandidato() {
        #expect(EnderecoDoServidor.candidatos("").isEmpty)
    }

    @Test("o que é seguro")
    func seguro() {
        #expect(EnderecoDoServidor.eSeguro("https://rog"))
        #expect(!EnderecoDoServidor.eSeguro("http://rog"))
    }
}
