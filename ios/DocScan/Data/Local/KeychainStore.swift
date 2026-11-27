import Foundation
import Security

enum KeychainStore {
    private static let service = "cam.docscan.identity"
    private static let deviceIdAccount = "device_id"
    private static let tokenAccount = "jwt_token"

    static func getOrCreateDeviceId() -> String {
        if let existing = read(account: deviceIdAccount), !existing.isEmpty {
            return existing
        }
        let created = UUID().uuidString.lowercased()
        save(created, account: deviceIdAccount)
        return created
    }

    static func getToken() -> String? {
        read(account: tokenAccount)
    }

    static func saveToken(_ token: String) {
        save(token, account: tokenAccount)
    }

    static func clearAll() {
        delete(account: deviceIdAccount)
        delete(account: tokenAccount)
    }

    private static func read(account: String) -> String? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne,
        ]
        var result: AnyObject?
        let status = SecItemCopyMatching(query as CFDictionary, &result)
        guard status == errSecSuccess, let data = result as? Data else { return nil }
        return String(data: data, encoding: .utf8)
    }

    private static func save(_ value: String, account: String) {
        guard let data = value.data(using: .utf8) else { return }
        delete(account: account)
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecValueData as String: data,
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
        ]
        SecItemAdd(query as CFDictionary, nil)
    }

    private static func delete(account: String) {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
        ]
        SecItemDelete(query as CFDictionary)
    }
}
