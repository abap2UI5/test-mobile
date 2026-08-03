import UIKit
import VisionKit

/// VisionKit-based barcode scanner for the bridge's scanBarcode() (Phase 3).
/// Presents a DataScannerViewController modally; the first recognized or
/// tapped barcode resolves the scan.
enum BarcodeScanner {

    static var isSupported: Bool {
        DataScannerViewController.isSupported && DataScannerViewController.isAvailable
    }

    /// completion(value, error) — exactly one of the two is non-nil.
    static func present(completion: @escaping (String?, String?) -> Void) {
        guard isSupported else {
            completion(nil, "unsupported")
            return
        }
        guard let presenter = topViewController() else {
            completion(nil, "no presenter")
            return
        }
        let controller = ScannerController(completion: completion)
        presenter.present(controller, animated: true) {
            do {
                try controller.scanner.startScanning()
            } catch {
                controller.finish(value: nil, error: "unsupported")
            }
        }
    }

    private static func topViewController() -> UIViewController? {
        let root = UIApplication.shared.connectedScenes
            .compactMap { ($0 as? UIWindowScene)?.keyWindow }
            .first?.rootViewController
        var top = root
        while let presented = top?.presentedViewController { top = presented }
        return top
    }

    private final class ScannerController: UIViewController, DataScannerViewControllerDelegate {
        let scanner = DataScannerViewController(
            recognizedDataTypes: [.barcode()],
            qualityLevel: .balanced,
            isHighlightingEnabled: true)
        private var completion: ((String?, String?) -> Void)?

        init(completion: @escaping (String?, String?) -> Void) {
            self.completion = completion
            super.init(nibName: nil, bundle: nil)
            modalPresentationStyle = .fullScreen
        }

        @available(*, unavailable)
        required init?(coder: NSCoder) { fatalError() }

        override func viewDidLoad() {
            super.viewDidLoad()
            scanner.delegate = self
            addChild(scanner)
            view.addSubview(scanner.view)
            scanner.view.frame = view.bounds
            scanner.view.autoresizingMask = [.flexibleWidth, .flexibleHeight]
            scanner.didMove(toParent: self)

            let cancel = UIButton(type: .system, primaryAction: UIAction(title: "Cancel") { [weak self] _ in
                self?.finish(value: nil, error: "cancelled")
            })
            cancel.translatesAutoresizingMaskIntoConstraints = false
            view.addSubview(cancel)
            NSLayoutConstraint.activate([
                cancel.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 12),
                cancel.trailingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.trailingAnchor, constant: -16),
            ])
        }

        func dataScanner(_ dataScanner: DataScannerViewController,
                         didAdd addedItems: [RecognizedItem],
                         allItems: [RecognizedItem]) {
            handle(items: addedItems)
        }

        func dataScanner(_ dataScanner: DataScannerViewController,
                         didTapOn item: RecognizedItem) {
            handle(items: [item])
        }

        private func handle(items: [RecognizedItem]) {
            for item in items {
                if case let .barcode(barcode) = item, let value = barcode.payloadStringValue {
                    finish(value: value, error: nil)
                    return
                }
            }
        }

        func finish(value: String?, error: String?) {
            guard let completion else { return }
            self.completion = nil
            scanner.stopScanning()
            dismiss(animated: true) { completion(value, error) }
        }
    }
}
