import LocalAuthentication
import SwiftUI
import UserNotifications

/// Generic abap2UI5 shell: WKWebView on one abap2UI5 endpoint plus the
/// native bridge (see /bridge/README.md, contract v1).
///
/// Endpoint resolution order: managed configuration (MDM) > stored
/// preference > first-run dialog. Authentication runs inside the WebView
/// (OAuth/SAML redirects of the backend or its Mobile Services destination);
/// after a long background stay the SPA reloads so expired sessions
/// re-authenticate cleanly.
@main
struct ShellApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate

    var body: some Scene {
        WindowGroup {
            RootView()
        }
    }
}

/// APNs registration (Phase 2) and notification deep links. The device
/// token is stored for the bridge's getPushToken(); registration with SAP
/// Mobile Services follows the same pattern as Android's MobileServicesPush
/// (POST /mobileservices/push/v1/runtime/.../os/ios/devices/{id}) once a
/// Mobile Services session exists (Phase 1 / BTP SDK onboarding).
final class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {

    static let deepLinkNotification = Notification.Name("a2u5DeepLink")

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .badge, .sound]) { granted, _ in
            if granted {
                DispatchQueue.main.async { application.registerForRemoteNotifications() }
            }
        }
        return true
    }

    func application(_ application: UIApplication,
                     didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        let token = deviceToken.map { String(format: "%02x", $0) }.joined()
        UserDefaults.standard.set(token, forKey: "push_token")
    }

    // Notification tap: convention data key "url" carries the abap2UI5 deep
    // link (same as Android, see PushService).
    func userNotificationCenter(_ center: UNUserNotificationCenter,
                                didReceive response: UNNotificationResponse,
                                withCompletionHandler completionHandler: @escaping () -> Void) {
        if let url = response.notification.request.content.userInfo["url"] as? String {
            NotificationCenter.default.post(
                name: Self.deepLinkNotification, object: nil, userInfo: ["url": url])
        }
        completionHandler()
    }
}

struct RootView: View {
    @AppStorage("endpoint_url") private var endpointURL: String = ""
    @AppStorage("app_lock_enabled") private var appLockPreference: Bool = false
    @State private var managed = ManagedConfig.Values()
    @State private var unlocked = false
    @State private var lockedOut = false
    @State private var showSettings = false
    @State private var draftURL: String = ""
    @State private var currentURL: String = ""
    @State private var reloadToken = 0
    @State private var backgroundedAt: Date?
    @Environment(\.scenePhase) private var scenePhase

    private static let backgroundReloadSeconds: TimeInterval = 30 * 60

    /// A managed value wins over the user preference and cannot be toggled.
    private var appLockEnabled: Bool { managed.appLockEnabled ?? appLockPreference }
    private var appLockManaged: Bool { managed.appLockEnabled != nil }
    private var appLockEnforced: Bool { managed.appLockEnabled == true }

    var body: some View {
        NavigationStack {
            Group {
                if lockedOut {
                    // Enforced lock without an enrolled credential: fail
                    // closed, unlike the user-chosen lock below.
                    VStack(spacing: 16) {
                        Image(systemName: "lock.trianglebadge.exclamationmark.fill")
                            .font(.largeTitle)
                        Text("Your administrator requires an app lock. Set up Face ID, Touch ID or a passcode on this device, then start the app again.")
                            .multilineTextAlignment(.center)
                            .foregroundStyle(.secondary)
                            .padding(.horizontal, 32)
                    }
                } else if appLockEnabled && !unlocked {
                    VStack(spacing: 16) {
                        Image(systemName: "lock.fill").font(.largeTitle)
                        Button("Unlock") { authenticate() }
                    }
                } else if let url = URL(string: activeURL), !activeURL.isEmpty {
                    ShellWebView(url: url, reloadToken: reloadToken)
                        .ignoresSafeArea(edges: .bottom)
                } else {
                    Text("No endpoint configured")
                        .foregroundStyle(.secondary)
                }
            }
            .navigationTitle("abap2UI5")
            .navigationBarTitleDisplayMode(.inline)
            // Managed settings are not offered in the UI: an EMM-pushed
            // endpoint would be restored on the next start anyway, and an
            // enforced app lock must not be toggled away.
            .toolbar {
                if managed.endpointURL == nil || !appLockManaged {
                    Menu {
                        if managed.endpointURL == nil {
                            Button("Set endpoint") {
                                draftURL = endpointURL
                                showSettings = true
                            }
                        }
                        if !appLockManaged {
                            Button(appLockEnabled ? "Disable app lock" : "Enable app lock") {
                                appLockPreference.toggle()
                                unlocked = true
                            }
                        }
                    } label: {
                        Image(systemName: "gearshape")
                    }
                }
            }
            // Keep business data out of the app-switcher snapshot when the
            // EMM asks for it. iOS cannot block screenshots or recordings
            // outright — see docs/DISTRIBUTION.md.
            .overlay {
                if managed.screenshotProtection == true && scenePhase != .active {
                    privacyCover
                }
            }
            .onAppear {
                applyManagedConfig()
                if endpointURL.isEmpty && managed.endpointURL == nil { showSettings = true }
                if appLockEnabled { authenticate() }
            }
            .onChange(of: scenePhase) { phase in
                switch phase {
                case .background:
                    backgroundedAt = Date()
                case .active:
                    // Reload after a long background stay — the abap2UI5
                    // draft and auth session are likely expired (PLAN.md
                    // risk 3); also re-gate the app lock.
                    if let at = backgroundedAt,
                       Date().timeIntervalSince(at) > Self.backgroundReloadSeconds {
                        reloadToken += 1
                        if appLockEnabled { unlocked = false; authenticate() }
                    }
                    backgroundedAt = nil
                default:
                    break
                }
            }
            .onReceive(NotificationCenter.default.publisher(for: AppDelegate.deepLinkNotification)) { note in
                if let url = note.userInfo?["url"] as? String {
                    currentURL = url
                }
            }
            .alert("abap2UI5 endpoint", isPresented: $showSettings) {
                TextField("https://host/sap/bc/z2ui5?sap-client=100", text: $draftURL)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                Button("OK") {
                    endpointURL = draftURL.trimmingCharacters(in: .whitespaces)
                    currentURL = ""
                }
                Button("Cancel", role: .cancel) {}
            }
        }
    }

    /// Deep link (if any) wins once, otherwise the configured endpoint.
    private var activeURL: String { currentURL.isEmpty ? endpointURL : currentURL }

    private var privacyCover: some View {
        ZStack {
            Rectangle().fill(.background)
            Image(systemName: "lock.shield.fill")
                .font(.system(size: 48))
                .foregroundStyle(.secondary)
        }
        .ignoresSafeArea()
    }

    private func applyManagedConfig() {
        managed = ManagedConfig.read()
        if let url = managed.endpointURL { endpointURL = url }
    }

    private func authenticate() {
        let context = LAContext()
        var error: NSError?
        guard context.canEvaluatePolicy(.deviceOwnerAuthentication, error: &error) else {
            // No authenticator enrolled: a user-chosen lock fails open (not
            // locking someone out of their own shell), an enforced one fails
            // closed — same rule as Android's AppLock.
            if appLockEnforced { lockedOut = true } else { unlocked = true }
            return
        }
        context.evaluatePolicy(.deviceOwnerAuthentication,
                               localizedReason: "Unlock abap2UI5 Shell") { success, _ in
            DispatchQueue.main.async { unlocked = success }
        }
    }
}
