import Foundation

/// MDM managed configuration (Phase 4) — the iOS mirror of Android's
/// ManagedConfig. An MDM pushes an app-config dictionary that iOS exposes
/// under the standard `com.apple.configuration.managed` user default; the
/// keys are identical on both platforms so one EMM documentation covers both
/// (see docs/DISTRIBUTION.md).
///
/// Every value is optional on purpose: `nil` means "not managed", which is
/// what leaves the setting under user control on unmanaged devices. A
/// managed `false` is an explicit administrator decision and something else
/// entirely.
enum ManagedConfig {

    static let dictionaryKey = "com.apple.configuration.managed"
    static let endpointURLKey = "endpoint_url"
    static let appLockEnabledKey = "app_lock_enabled"
    static let screenshotProtectionKey = "screenshot_protection"

    struct Values {
        var endpointURL: String?
        var appLockEnabled: Bool?
        var screenshotProtection: Bool?
    }

    static func read(_ defaults: UserDefaults = .standard) -> Values {
        guard let managed = defaults.dictionary(forKey: dictionaryKey) else { return Values() }
        return Values(
            endpointURL: (managed[endpointURLKey] as? String)
                .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
                .flatMap { $0.isEmpty ? nil : $0 },
            appLockEnabled: managed[appLockEnabledKey] as? Bool,
            screenshotProtection: managed[screenshotProtectionKey] as? Bool)
    }
}
