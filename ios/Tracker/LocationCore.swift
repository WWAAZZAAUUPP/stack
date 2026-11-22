import CoreLocation
import UIKit

final class LocationCore: NSObject, CLLocationManagerDelegate {
    static let shared = LocationCore()
    private let m = CLLocationManager()
    private var last: CLLocation?
    private var lastAt = Date.distantPast
    private var fenced = false

    func boot() {
        m.delegate = self
        m.desiredAccuracy = kCLLocationAccuracyBest
        m.distanceFilter = kCLDistanceFilterNone
        m.pausesLocationUpdatesAutomatically = false
        m.allowsBackgroundLocationUpdates = true
        m.showsBackgroundLocationIndicator = true
        m.activityType = .otherNavigation
        UIDevice.current.isBatteryMonitoringEnabled = true
        m.requestAlwaysAuthorization()
        m.startUpdatingLocation()
        m.startMonitoringSignificantLocationChanges()
        m.startMonitoringVisits()
    }

    func manager(_ m: CLLocationManager, didUpdateLocations locs: [CLLocation]) {
        for l in locs {
            if l.horizontalAccuracy < 0 || l.horizontalAccuracy > Config.maxAccuracy { continue }
            let now = Date()
            if now.timeIntervalSince(lastAt) < 0.9 { continue }
            if let p = last, l.distance(from: p) < 4,
               l.horizontalAccuracy >= p.horizontalAccuracy - 2 { continue }
            last = l; lastAt = now
            Sync.shared.add(l, source: l.speed < 0.3 ? "ios-still" : "ios-move")
            if l.speed < 0.3 { fence(l) }
        }
    }

    func manager(_ m: CLLocationManager, didVisit v: CLVisit) {
        Sync.shared.add(CLLocation(latitude: v.coordinate.latitude,
                                   longitude: v.coordinate.longitude), source: "visit")
    }

    func manager(_ m: CLLocationManager, didChangeAuthorization s: CLAuthorizationStatus) {
        if s == .authorizedAlways || s == .authorizedWhenInUse {
            m.startUpdatingLocation()
            m.startMonitoringSignificantLocationChanges()
        }
    }

    func manager(_ m: CLLocationManager, didFailWithError e: Error) {}

    private func fence(_ l: CLLocation) {
        guard !fenced else { return }
        fenced = true
        let r = CLCircularRegion(center: l.coordinate, radius: 150,
                                 identifier: "f\(Int(Date().timeIntervalSince1970))")
        r.notifyOnExit = true
        m.startMonitoring(for: r)
    }
}