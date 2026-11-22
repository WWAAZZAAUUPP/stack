import Foundation
import CoreLocation

enum Config {
    static var url: String { UserDefaults.standard.string(forKey: "cu") ?? "__SUPABASE_URL__" }
    static var key: String { UserDefaults.standard.string(forKey: "ck") ?? "__SUPABASE_KEY__" }
    static let table = "fixes"
    static let maxAccuracy: CLLocationAccuracy = 45
}