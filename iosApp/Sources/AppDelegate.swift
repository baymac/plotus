import UIKit
import PlotusApp

// iOS lifecycle host only. Every app screen and all state live in shared Kotlin.
@main
final class AppDelegate: UIResponder, UIApplicationDelegate {
    var window: UIWindow?

    func application(_ application: UIApplication, didFinishLaunchingWithOptions options: [UIApplication.LaunchOptionsKey: Any]?) -> Bool {
        let window = UIWindow(frame: UIScreen.main.bounds)
        window.rootViewController = MainViewControllerKt.MainViewController()
        window.makeKeyAndVisible()
        self.window = window
        return true
    }
}
