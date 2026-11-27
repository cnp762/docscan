import Foundation
import StoreKit

@MainActor
final class StoreKitManager: ObservableObject {
    @Published private(set) var products: [Product] = []
    @Published private(set) var isPro: Bool = UserDefaults.standard.bool(forKey: "docscan_is_pro")

    private var updatesTask: Task<Void, Never>?
    private let apiClient: DocScanAPIClient

    init(apiClient: DocScanAPIClient = .shared) {
        self.apiClient = apiClient
        self.updatesTask = listenForTransactions()
    }

    deinit {
        updatesTask?.cancel()
    }

    func loadProducts() async {
        do {
            let loaded = try await Product.products(for: AppConfig.productIds)
            self.products = loaded.sorted { $0.price < $1.price }
        } catch {
            // Keep cached/fallback display pricing if StoreKit products fail to load
        }
    }

    func purchase(productId: String) async throws -> Bool {
        if products.isEmpty {
            await loadProducts()
        }
        guard let product = products.first(where: { $0.id == productId }) else {
            return false
        }
        let result = try await product.purchase()
        switch result {
        case .success(let verification):
            return try await handleVerified(verification)
        case .userCancelled, .pending:
            return false
        @unknown default:
            return false
        }
    }

    func restorePurchases() async -> Bool {
        try? await AppStore.sync()
        var foundActive = false
        for await result in Transaction.currentEntitlements {
            if (try? await handleVerified(result)) == true {
                foundActive = true
            }
        }
        if !foundActive, let ent = try? await apiClient.getEntitlement(), ent.isPro {
            setCachedPro(true)
            return true
        }
        return foundActive
    }

    func clearLocalEntitlement() {
        setCachedPro(false)
    }

    private func listenForTransactions() -> Task<Void, Never> {
        Task.detached { [weak self] in
            for await result in Transaction.updates {
                guard let self else { continue }
                _ = try? await self.handleVerified(result)
            }
        }
    }

    private func handleVerified(_ verification: VerificationResult<Transaction>) async throws -> Bool {
        guard case .verified(let transaction) = verification else {
            return false
        }
        let jws = verification.jwsRepresentation
        let ent = try? await apiClient.verifyBilling(productId: transaction.productID, jwsRepresentation: jws)
        let active = ent?.isPro ?? (transaction.revocationDate == nil)
        await transaction.finish()
        setCachedPro(active)
        return active
    }

    private func setCachedPro(_ value: Bool) {
        isPro = value
        UserDefaults.standard.set(value, forKey: "docscan_is_pro")
    }
}
