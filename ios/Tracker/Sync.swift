import Foundation
import CoreLocation

final class Sync {
    static let shared = Sync()
    private let q = DispatchQueue(label: "sync")
    private var rows: [[String: Any]] = []
    private let k = "uc_rows"

    private init() {
        if let d = UserDefaults.standard.data(forKey: k),
           let a = try? JSONSerialization.jsonObject(with: d) as? [[String: Any]] { rows = a }
    }

    func add(_ l: CLLocation, source: String) {
        q.async {
            self.rows.append([
                "lat": l.coordinate.latitude, "lon": l.coordinate.longitude,
                "accuracy": l.horizontalAccuracy, "altitude": l.altitude,
                "vertical_accuracy": l.verticalAccuracy, "speed": l.speed,
                "bearing": l.course, "provider": source, "is_mock": false,
                "activity": "UNKNOWN", "battery_pct": -1, "charging": false,
                "source": source,
                "captured_at": Int(Date().timeIntervalSince1970 * 1000),
                "elapsed_nanos": 0, "device": UIDevice.current.model
            ])
            self.persist(); self.flush()
        }
    }

    private func persist() {
        if let d = try? JSONSerialization.data(withJSONObject: rows) {
            UserDefaults.standard.set(d, forKey: k)
        }
    }

    func flush() {
        q.async {
            guard !Config.url.hasPrefix("__"),
                  let body = try? JSONSerialization.data(
                    withJSONObject: Array(self.rows.prefix(120))) else { return }
            var r = URLRequest(url: URL(string: Config.url + "/rest/v1/" + Config.table)!)
            r.httpMethod = "POST"
            r.setValue(Config.key, forHTTPHeaderField: "apikey")
            r.setValue("Bearer " + Config.key, forHTTPHeaderField: "Authorization")
            r.setValue("application/json", forHTTPHeaderField: "Content-Type")
            r.setValue("return=minimal", forHTTPHeaderField: "Prefer")
            r.httpBody = body; r.timeoutInterval = 30
            URLSession.shared.dataTask(with: r) { _, resp, _ in
                guard let h = resp as? HTTPURLResponse, (200...299).contains(h.statusCode) else { return }
                self.q.async {
                    self.rows.removeFirst(min(120, self.rows.count))
                    self.persist()
                    if !self.rows.isEmpty { self.flush() }
                }
            }.resume()
        }
    }
}