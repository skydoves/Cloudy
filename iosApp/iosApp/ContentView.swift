import UIKit
import SwiftUI
import CloudyApp

struct ComposeView: UIViewControllerRepresentable {
    /// Creates the view controller used by this UIViewControllerRepresentable.
    /// Creates the view controller managed by this representable.
    /// - Returns: An instance of `MainViewControllerKt.MainViewController`.
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView()
                .ignoresSafeArea()
    }
}