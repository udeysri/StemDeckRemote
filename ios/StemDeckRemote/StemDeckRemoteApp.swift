import SwiftUI

@main
struct StemDeckRemoteApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
                .tint(ConsoleTheme.accent)
                .preferredColorScheme(.dark)
        }
    }
}
