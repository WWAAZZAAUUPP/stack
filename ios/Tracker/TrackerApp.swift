import SwiftUI
import UIKit

final class Delegate: NSObject, UIApplicationDelegate {
    func application(_ app: UIApplication,
                     didFinishLaunchingWithOptions o: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        LocationCore.shared.boot()
        return true
    }
}

@main
struct TrackerApp: App {
    @UIApplicationDelegateAdaptor(Delegate.self) var delegate
    var body: some Scene { WindowGroup { Decoy() } }
}