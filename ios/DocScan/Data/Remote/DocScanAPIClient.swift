import Foundation

struct QuotaStatusDTO: Decodable {
    let utcDay: String
    let used: Int
    let limit: Int?
    let remaining: Int?
    let isPro: Bool

    enum CodingKeys: String, CodingKey {
        case utcDay = "utc_day"
        case used
        case limit
        case remaining
        case isPro = "is_pro"
    }
}

struct EntitlementDTO: Decodable {
    let isPro: Bool
    let productId: String?
    let expiresAt: String?

    enum CodingKeys: String, CodingKey {
        case isPro = "is_pro"
        case productId = "product_id"
        case expiresAt = "expires_at"
    }
}

private struct DeviceRegisterResponseDTO: Decodable {
    let deviceId: String
    let platform: String
    let token: String

    enum CodingKeys: String, CodingKey {
        case deviceId = "device_id"
        case platform
        case token
    }
}

enum QuotaConsumeResult {
    case allowed(QuotaStatusDTO)
    case quotaExceeded
    case networkError(Error)
}

enum DocScanAPIError: Error {
    case httpError(Int, String)
    case invalidURL
}

final class DocScanAPIClient {
    static let shared = DocScanAPIClient()

    private let baseV1URL: URL
    private let session: URLSession

    init(baseURL: URL = AppConfig.apiBaseURL, session: URLSession = .shared) {
        let raw = baseURL.absoluteString.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        let normalized = raw.hasSuffix("/v1") ? raw : "\(raw)/v1"
        self.baseV1URL = URL(string: normalized) ?? baseURL
        self.session = session
    }

    func ensureRegistered() async throws {
        let deviceId = KeychainStore.getOrCreateDeviceId()
        let appVersion = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0.0"
        let body: [String: Any] = [
            "device_id": deviceId,
            "platform": "ios",
            "app_version": appVersion,
        ]
        var req = URLRequest(url: baseV1URL.appendingPathComponent("devices/register"))
        req.httpMethod = "POST"
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.httpBody = try JSONSerialization.data(withJSONObject: body)

        let (data, resp) = try await session.data(for: req)
        let code = (resp as? HTTPURLResponse)?.statusCode ?? 0
        guard (200..<300).contains(code) else {
            throw DocScanAPIError.httpError(code, String(data: data, encoding: .utf8) ?? "")
        }
        let decoded = try JSONDecoder().decode(DeviceRegisterResponseDTO.self, from: data)
        KeychainStore.saveToken(decoded.token)
    }

    func getQuotaToday() async throws -> QuotaStatusDTO {
        try await ensureTokenIfMissing()
        let (data, _) = try await authedRequest(path: "quota/today", method: "GET")
        return try JSONDecoder().decode(QuotaStatusDTO.self, from: data)
    }

    func consumeQuota(n: Int) async -> QuotaConsumeResult {
        do {
            try await ensureTokenIfMissing()
            let body = try JSONSerialization.data(withJSONObject: ["n": n])
            let (data, code) = try await authedRequestAllowing402(path: "quota/consume", method: "POST", body: body)
            if code == 402 {
                return .quotaExceeded
            }
            let status = try JSONDecoder().decode(QuotaStatusDTO.self, from: data)
            return .allowed(status)
        } catch {
            return .networkError(error)
        }
    }

    func verifyBilling(productId: String, jwsRepresentation: String) async throws -> EntitlementDTO {
        try await ensureTokenIfMissing()
        let payload: [String: Any] = [
            "store": "apple",
            "product_id": productId,
            "receipt_or_token": jwsRepresentation,
        ]
        let body = try JSONSerialization.data(withJSONObject: payload)
        let (data, _) = try await authedRequest(path: "billing/verify", method: "POST", body: body)
        return try JSONDecoder().decode(EntitlementDTO.self, from: data)
    }

    func getEntitlement() async throws -> EntitlementDTO {
        try await ensureTokenIfMissing()
        let (data, _) = try await authedRequest(path: "billing/entitlement", method: "GET")
        return try JSONDecoder().decode(EntitlementDTO.self, from: data)
    }

    func deleteDeviceMe() async throws {
        try await ensureTokenIfMissing()
        _ = try await authedRequest(path: "devices/me", method: "DELETE", allow404: true)
        KeychainStore.clearAll()
    }

    private func ensureTokenIfMissing() async throws {
        if KeychainStore.getToken()?.isEmpty != false {
            try await ensureRegistered()
        }
    }

    private func authedRequest(
        path: String,
        method: String,
        body: Data? = nil,
        allow404: Bool = false
    ) async throws -> (Data, Int) {
        let (data, code) = try await rawAuthedCall(path: path, method: method, body: body)
        if code == 401 {
            try await ensureRegistered()
            let (d2, c2) = try await rawAuthedCall(path: path, method: method, body: body)
            if (200..<300).contains(c2) || (allow404 && c2 == 404) { return (d2, c2) }
            throw DocScanAPIError.httpError(c2, String(data: d2, encoding: .utf8) ?? "")
        }
        if (200..<300).contains(code) || (allow404 && code == 404) { return (data, code) }
        throw DocScanAPIError.httpError(code, String(data: data, encoding: .utf8) ?? "")
    }

    private func authedRequestAllowing402(path: String, method: String, body: Data?) async throws -> (Data, Int) {
        let (data, code) = try await rawAuthedCall(path: path, method: method, body: body)
        if code == 401 {
            try await ensureRegistered()
            let (d2, c2) = try await rawAuthedCall(path: path, method: method, body: body)
            if (200..<300).contains(c2) || c2 == 402 { return (d2, c2) }
            throw DocScanAPIError.httpError(c2, String(data: d2, encoding: .utf8) ?? "")
        }
        if (200..<300).contains(code) || code == 402 { return (data, code) }
        throw DocScanAPIError.httpError(code, String(data: data, encoding: .utf8) ?? "")
    }

    private func rawAuthedCall(path: String, method: String, body: Data?) async throws -> (Data, Int) {
        var req = URLRequest(url: baseV1URL.appendingPathComponent(path))
        req.httpMethod = method
        req.setValue(KeychainStore.getOrCreateDeviceId(), forHTTPHeaderField: "X-Device-Id")
        if let token = KeychainStore.getToken() {
            req.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        if let body {
            req.setValue("application/json", forHTTPHeaderField: "Content-Type")
            req.httpBody = body
        }
        let (data, resp) = try await session.data(for: req)
        let code = (resp as? HTTPURLResponse)?.statusCode ?? 0
        return (data, code)
    }
}
