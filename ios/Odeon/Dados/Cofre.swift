import Foundation
import Security

/// Onde ficam o endereço do servidor, os dois tokens e o id deste aparelho.
///
/// ## ⚠️ Os dois tokens são diferentes, e confundi-los quebra o player
///
/// | | vale | vai em | quem usa |
/// |---|---|---|---|
/// | **sessão** | 90 dias | `Authorization: Bearer` | toda rota de API |
/// | **mídia** | 8 horas | `?token=` na URL | pôster, stream, legenda |
///
/// O de mídia existe porque `<img src>` e o player não mandam cabeçalho, então
/// ele precisa viajar na query — e URL vaza pra log de acesso, daí a vida curta.
///
/// ⚠️ **Emitir um token de mídia novo aposenta o anterior.** O anterior é o que
/// está dentro do player que está tocando. Por isso ele é **lido**, e só se pede
/// um novo quando não há nenhum — nunca «por garantia».
///
/// ## Aqui é Keychain, e no Android não era — a diferença é honesta
///
/// O `Cofre.kt` do Android guarda em DataStore e diz por extenso: «**nada é
/// cifrado**… é o mesmo nível da web, que guarda no `localStorage`. Dizer isto
/// por extenso é melhor do que uma camada de criptografia que parece proteger
/// mais do que protege.»
///
/// No iOS o Keychain **já é** esse lugar: cifrado pelo sistema, fora do backup
/// quando se pede, e sem máquina extra pra manter. Ou seja, não é o iOS sendo
/// mais zeloso — é a plataforma oferecendo de graça o que lá custaria uma camada.
/// A regra herdada continua valendo: **não construir criptografia própria.**
///
/// ⚠️ `kSecAttrAccessibleAfterFirstUnlock` e não `WhenUnlocked`: o app precisa
/// poder retomar um download ou marcar progresso com a tela bloqueada.
struct Cofre: Sendable {
    private static let servico = "dev.odeon.ios"

    private enum Chave: String {
        case sessao = "token-de-sessao"
        case midia = "token-de-midia"
    }

    private static let chaveDoServidor = "odeon.servidor"
    private static let chaveDoAparelho = "odeon.aparelho"

    // MARK: - O endereço do servidor
    //
    // Ele não é segredo — é configuração, e vai em `UserDefaults`. Guardá-lo no
    // Keychain só tornaria mais difícil de inspecionar quando alguém disser «não
    // conecta», que é o defeito nº 1 de um app de TV/celular de casa.

    var servidor: String? {
        get { UserDefaults.standard.string(forKey: Self.chaveDoServidor) }
        nonmutating set { UserDefaults.standard.set(newValue, forKey: Self.chaveDoServidor) }
    }

    /// O identificador **deste** aparelho, pro servidor separar «onde eu parei».
    ///
    /// ⚠️ Nasce uma vez e vive enquanto o app estiver instalado. **Não** é o
    /// `identifierForVendor` nem nada que identifique o aparelho fora daqui: é um
    /// número aleatório que só faz sentido dentro deste Odeon, e some com o app.
    ///
    /// A tese do projeto depende dele — «você parou na TV e continua no ônibus»
    /// só existe se o servidor souber distinguir de onde veio cada marca.
    var aparelho: String {
        if let ja = UserDefaults.standard.string(forKey: Self.chaveDoAparelho) { return ja }
        let novo = UUID().uuidString
        UserDefaults.standard.set(novo, forKey: Self.chaveDoAparelho)
        return novo
    }

    // MARK: - Os tokens

    var sessao: String? {
        get { Self.ler(.sessao) }
        nonmutating set { Self.gravar(.sessao, newValue) }
    }

    var tokenDeMidia: String? {
        get { Self.ler(.midia) }
        nonmutating set { Self.gravar(.midia, newValue) }
    }

    /// Sair: apaga os dois tokens e **mantém o endereço**.
    ///
    /// ⚠️ Manter o endereço é decisão de produto: quem sai da conta quase nunca
    /// está trocando de servidor, e fazer a pessoa redigitar o IP da casa a cada
    /// login é o atrito que o `EnderecoDoServidor` inteiro existe pra evitar.
    func sair() {
        sessao = nil
        tokenDeMidia = nil
    }

    // MARK: - Keychain, cru

    private static func ler(_ chave: Chave) -> String? {
        let busca: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: servico,
            kSecAttrAccount as String: chave.rawValue,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne,
        ]
        var item: CFTypeRef?
        guard SecItemCopyMatching(busca as CFDictionary, &item) == errSecSuccess,
              let dados = item as? Data
        else { return nil }
        return String(data: dados, encoding: .utf8)
    }

    private static func gravar(_ chave: Chave, _ valor: String?) {
        let identidade: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: servico,
            kSecAttrAccount as String: chave.rawValue,
        ]
        // Apagar antes de gravar: `SecItemUpdate` exigiria saber se já existe, e
        // o par apagar+inserir é o mesmo resultado com metade dos caminhos.
        SecItemDelete(identidade as CFDictionary)

        guard let valor, let dados = valor.data(using: .utf8) else { return }
        var novo = identidade
        novo[kSecValueData as String] = dados
        novo[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlock
        SecItemAdd(novo as CFDictionary, nil)
    }
}
