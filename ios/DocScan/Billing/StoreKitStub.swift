import Foundation
import StoreKit

/// StoreKit 2 product IDs — wire Product.products(for:) in the paywall slice.
enum StoreKitStub {
    static let productIds: Set<String> = [
        "pro_monthly",
        "pro_quarterly",
        "pro_yearly",
    ]

    static func loadProducts() async throws -> [Product] {
        try await Product.products(for: productIds)
    }
}
