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
    @AppStorage("app_lock_enabled") private var appLockEnabled: Bool = false
    @State private var unlocked = false
    @State private var showSettings = false
    @State private var draftURL: String = ""
    @State private var currentURL: String = ""
    @State private var reloadToken = 0
    @State private var backgroundedAt: Date?
    @Environment(\.scenePhase) private var scenePhase

    private static let backgroundReloadSeconds: TimeInterval = 30 * 60

    var body: some View {
        NavigationStack {
            Group {
                if appLockEnabled && !unlocked {
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
            .toolbar {
                Menu {
                    Button("Set endpoint") {
                        draftURL = endpointURL
                        showSettings = true
                    }
                    Button(appLockEnabled ? "Disable app lock" : "Enable app lock") {
                        appLockEnabled.toggle()
                        unlocked = true
                    }
                } label: {
                    Image(systemName: "gearshape")
                }
            }
            .onAppear {
                applyManagedConfig()
                if endpointURL.isEmpty { showSettings = true }
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

    /// MDM managed configuration (Phase 4): standard managed-app defaults
    /// dictionary, key "endpoint_url" — mirrors Android's ManagedConfig.
    private func applyManagedConfig() {
        if let managed = UserDefaults.standard.dictionary(forKey: "com.apple.configuration.managed"),
           let url = managed["endpoint_url"] as? String, !url.isEmpty {
            endpointURL = url
        }
    }

    private func authenticate() {
        let context = LAContext()
        var error: NSError?
        guard context.canEvaluatePolicy(.deviceOwnerAuthentication, error: &error) else {
            unlocked = true // no authenticator enrolled — fail open in the PoC
            return
        }
        context.evaluatePolicy(.deviceOwnerAuthentication,
                               localizedReason: "Unlock abap2UI5 Shell") { success, _ in
            DispatchQueue.main.async { unlocked = success }
        }
    }
}
