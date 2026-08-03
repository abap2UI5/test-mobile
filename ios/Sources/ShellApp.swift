import SwiftUI

/// Generic abap2UI5 shell: WKWebView on one abap2UI5 endpoint plus the
/// native bridge (see /bridge/README.md, contract v0).
///
/// Phase 0: endpoint entered manually, stored in UserDefaults. Phase 1
/// replaces this with the SAP BTP SDK for iOS onboarding flow (see PLAN.md).
@main
struct ShellApp: App {
    var body: some Scene {
        WindowGroup {
            RootView()
        }
    }
}

struct RootView: View {
    @AppStorage("endpoint_url") private var endpointURL: String = ""
    @State private var showSettings = false
    @State private var draftURL: String = ""

    var body: some View {
        NavigationStack {
            Group {
                if let url = URL(string: endpointURL), !endpointURL.isEmpty {
                    ShellWebView(url: url)
                        .ignoresSafeArea(edges: .bottom)
                } else {
                    Text("No endpoint configured")
                        .foregroundStyle(.secondary)
                }
            }
            .navigationTitle("abap2UI5")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                Button("Endpoint") {
                    draftURL = endpointURL
                    showSettings = true
                }
            }
            .onAppear { if endpointURL.isEmpty { showSettings = true } }
            .alert("abap2UI5 endpoint", isPresented: $showSettings) {
                TextField("https://host/sap/bc/z2ui5?sap-client=100", text: $draftURL)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                Button("OK") { endpointURL = draftURL.trimmingCharacters(in: .whitespaces) }
                Button("Cancel", role: .cancel) {}
            }
        }
    }
}
