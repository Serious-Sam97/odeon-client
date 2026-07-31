import SwiftUI
import UIKit
import ComposeApp

/// A casca. Todo o app é o mesmo Compose que roda no celular Android — o que
/// existe de Swift aqui é a ponte pro UIViewController que o Kotlin devolve.
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea(.keyboard)
    }
}
