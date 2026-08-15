import AVFoundation
import Foundation
import Testing
@testable import Odeon

/// Só serve pra achar o bundle dos testes.
private final class Ancora {}

/// A prova do mp3 — sem depender do acervo.
///
/// ## ⚠️ Por que um arquivo fabricado, e não um do servidor
///
/// A pergunta é **da plataforma**, não do acervo: *o AVFoundation decodifica
/// MP3 dentro de mp4?* Um arquivo do acervo torna a pergunta concreta, mas a
/// sonda procurou `mov/mp4 + h264 + mp3` em seis pontos e não achou — são 63 em
/// ~17.900, e amostrar até cair um custaria centenas de requisições ao servidor
/// da casa.
///
/// Então o sujeito é fabricado aqui, com o mesmo par que o acervo tem: **h264
/// (que já provou decodificar) + mp3**. O vídeo servir isola a variável — se sair
/// som, o mp3 é o que decodifica.
///
/// ## ⚠️ E por que decodificar áudio, e não gerar quadro
///
/// `AVAssetImageGenerator` prova o **vídeo**. Um arquivo com vídeo bom e áudio
/// impossível geraria quadro do mesmo jeito, e eu concluiria errado. Quem obriga
/// o decodificador de áudio a trabalhar é o `AVAssetReader` pedindo PCM: se o mp3
/// não for suportado, não sai byte.
///
/// É a mesma régua do resto — «saiu quadro» pro vídeo, «saiu amostra» pro som.
struct ProvaDeMp3 {

    /// Três sujeitos, pra separar **onde** o mp3 falha:
    /// o avulso (`.mp3`), dentro de mp4, e dentro de mov.
    @Test(arguments: [("prova_avulso", "mp3"), ("prova_mp3", "mp4"), ("prova_mp3", "mov")])
    func mp3Decodifica(_ alvo: (nome: String, ext: String)) async throws {
        let bundle = Bundle(for: Ancora.self)
        guard let url = bundle.url(forResource: alvo.nome, withExtension: alvo.ext) else {
            Issue.record("\(alvo.nome).\(alvo.ext) não entrou no bundle dos testes")
            return
        }
        print("\n════ \(alvo.nome).\(alvo.ext) ════")

        let asset = AVURLAsset(url: url)
        let audio = try await asset.loadTracks(withMediaType: .audio)
        let video = try await asset.loadTracks(withMediaType: .video)
        print("── faixas de áudio=\(audio.count) vídeo=\(video.count)")

        guard let faixa = audio.first else {
            print("   ❌ NENHUMA FAIXA DE ÁUDIO — o AVFoundation não enxerga o mp3 aqui")
            return
        }

        let formatos = try await faixa.load(.formatDescriptions)
        for f in formatos {
            let tipo = CMFormatDescriptionGetMediaSubType(f)
            let nome = withUnsafeBytes(of: tipo.bigEndian) { String(bytes: $0, encoding: .ascii) ?? "?" }
            print("   subtipo da faixa: \(nome)")
        }

        /// ⚠️ **A prova.** Pedir PCM força o decodificador de mp3 a rodar; se ele
        /// não existir, o reader falha ou não entrega byte nenhum.
        let leitor = try AVAssetReader(asset: asset)
        let saida = AVAssetReaderTrackOutput(
            track: faixa,
            outputSettings: [
                AVFormatIDKey: kAudioFormatLinearPCM,
                AVLinearPCMBitDepthKey: 16,
                AVLinearPCMIsFloatKey: false,
                AVLinearPCMIsBigEndianKey: false,
            ],
        )
        leitor.add(saida)
        leitor.startReading()

        var bytes = 0
        var blocos = 0
        while let amostra = saida.copyNextSampleBuffer() {
            blocos += 1
            bytes += CMSampleBufferGetTotalSampleSize(amostra)
            if blocos > 200 { break }
        }

        print("   estado do leitor: \(leitor.status.rawValue)  blocos=\(blocos)  bytes de PCM=\(bytes)")
        if let erro = leitor.error { print("   erro: \(erro.localizedDescription)") }

        if bytes > 0 {
            print("   ✅ MP3 DECODIFICOU: \(bytes) bytes de PCM em \(blocos) blocos")
        } else {
            print("   ❌ MP3 NÃO DECODIFICOU — tirar `mp3` das capacidades declaradas")
        }


    }
}
