import AuthenticationServices
import CryptoKit
import Foundation
import UIKit

enum CloudBackupError: Error {
    case iCloudUnavailable
    case notAuthenticated
    case missingClientId
    case uploadFailed(Int)
}

enum ICloudDriveBackupService {
    static var isAvailable: Bool {
        FileManager.default.ubiquityIdentityToken != nil
    }

    static func uploadFile(localURL: URL, fileName: String) async throws {
        guard let containerURL = FileManager.default.url(forUbiquityContainerIdentifier: nil) else {
            throw CloudBackupError.iCloudUnavailable
        }
        let docsDir = containerURL
            .appendingPathComponent("Documents", isDirectory: true)
            .appendingPathComponent("DocScan", isDirectory: true)
        try FileManager.default.createDirectory(at: docsDir, withIntermediateDirectories: true)
        let destURL = docsDir.appendingPathComponent(fileName)
        if FileManager.default.fileExists(atPath: destURL.path) {
            try FileManager.default.removeItem(at: destURL)
        }
        try FileManager.default.copyItem(at: localURL, to: destURL)
    }
}

@MainActor
final class GoogleOAuthBackupService: NSObject, ASWebAuthenticationPresentationContextProviding {
    static let shared = GoogleOAuthBackupService()

    private let tokenKey = "google_oauth_access_token"
    private var activeSession: ASWebAuthenticationSession?

    var isSignedIn: Bool {
        guard let t = KeychainStore.get(tokenKey) else { return false }
        return !t.isEmpty
    }

    func revoke() {
        KeychainStore.delete(tokenKey)
    }

    func signIn(includePhotos: Bool) async throws -> String {
        guard let clientId = AppConfig.googleOAuthClientID, !clientId.isEmpty else {
            throw CloudBackupError.missingClientId
        }
        let verifier = randomVerifier()
        let challenge = sha256Base64URL(verifier)
        let reversedClientId = clientId.split(separator: ".").reversed().joined(separator: ".")
        let redirectUri = "\(reversedClientId):/oauthredirect"
        let scopes = includePhotos
            ? "https://www.googleapis.com/auth/drive.file https://www.googleapis.com/auth/photoslibrary.appendonly"
            : "https://www.googleapis.com/auth/drive.file"

        var comps = URLComponents(string: "https://accounts.google.com/o/oauth2/v2/auth")!
        comps.queryItems = [
            URLQueryItem(name: "client_id", value: clientId),
            URLQueryItem(name: "redirect_uri", value: redirectUri),
            URLQueryItem(name: "response_type", value: "code"),
            URLQueryItem(name: "scope", value: scopes),
            URLQueryItem(name: "code_challenge", value: challenge),
            URLQueryItem(name: "code_challenge_method", value: "S256"),
        ]
        guard let authURL = comps.url else { throw CloudBackupError.notAuthenticated }

        let callbackURL: URL = try await withCheckedThrowingContinuation { cont in
            let session = ASWebAuthenticationSession(url: authURL, callbackURLScheme: reversedClientId) { url, error in
                if let url {
                    cont.resume(returning: url)
                } else {
                    cont.resume(throwing: error ?? CloudBackupError.notAuthenticated)
                }
            }
            session.presentationContextProvider = self
            session.prefersEphemeralWebBrowserSession = false
            self.activeSession = session
            session.start()
        }
        self.activeSession = nil

        guard let code = URLComponents(url: callbackURL, resolvingAgainstBaseURL: false)?
            .queryItems?.first(where: { $0.name == "code" })?.value else {
            throw CloudBackupError.notAuthenticated
        }
        let token = try await exchangeCode(code: code, clientId: clientId, redirectUri: redirectUri, verifier: verifier)
        KeychainStore.set(token, forKey: tokenKey)
        return token
    }

    func uploadImageToDrive(localURL: URL, fileName: String) async throws {
        guard let token = KeychainStore.get(tokenKey), !token.isEmpty else {
            throw CloudBackupError.notAuthenticated
        }
        let imageData = try Data(contentsOf: localURL)
        let boundary = "docscan-\(UUID().uuidString)"
        var body = Data()
        let meta = "{\"name\":\"\(fileName)\",\"mimeType\":\"image/jpeg\"}"
        body.append(Data("--\(boundary)\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n\(meta)\r\n".utf8))
        body.append(Data("--\(boundary)\r\nContent-Type: image/jpeg\r\n\r\n".utf8))
        body.append(imageData)
        body.append(Data("\r\n--\(boundary)--\r\n".utf8))

        var req = URLRequest(url: URL(string: "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart")!)
        req.httpMethod = "POST"
        req.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        req.setValue("multipart/related; boundary=\(boundary)", forHTTPHeaderField: "Content-Type")
        req.httpBody = body

        let (_, resp) = try await URLSession.shared.data(for: req)
        let code = (resp as? HTTPURLResponse)?.statusCode ?? 0
        guard (200...299).contains(code) else { throw CloudBackupError.uploadFailed(code) }
    }

    func uploadImageToPhotos(localURL: URL, fileName: String) async throws {
        guard let token = KeychainStore.get(tokenKey), !token.isEmpty else {
            throw CloudBackupError.notAuthenticated
        }
        let imageData = try Data(contentsOf: localURL)
        var uploadReq = URLRequest(url: URL(string: "https://photoslibrary.googleapis.com/v1/uploads")!)
        uploadReq.httpMethod = "POST"
        uploadReq.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        uploadReq.setValue("application/octet-stream", forHTTPHeaderField: "Content-Type")
        uploadReq.setValue("image/jpeg", forHTTPHeaderField: "X-Goog-Upload-Content-Type")
        uploadReq.setValue("raw", forHTTPHeaderField: "X-Goog-Upload-Protocol")
        uploadReq.httpBody = imageData

        let (tokenData, uploadResp) = try await URLSession.shared.data(for: uploadReq)
        let uploadCode = (uploadResp as? HTTPURLResponse)?.statusCode ?? 0
        guard (200...299).contains(uploadCode),
              let uploadToken = String(data: tokenData, encoding: .utf8), !uploadToken.isEmpty else {
            throw CloudBackupError.uploadFailed(uploadCode)
        }

        let payload: [String: Any] = [
            "newMediaItems": [
                [
                    "description": fileName,
                    "simpleMediaItem": ["fileName": fileName, "uploadToken": uploadToken],
                ]
            ]
        ]
        var createReq = URLRequest(url: URL(string: "https://photoslibrary.googleapis.com/v1/mediaItems:batchCreate")!)
        createReq.httpMethod = "POST"
        createReq.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        createReq.setValue("application/json", forHTTPHeaderField: "Content-Type")
        createReq.httpBody = try JSONSerialization.data(withJSONObject: payload)

        let (_, createResp) = try await URLSession.shared.data(for: createReq)
        let createCode = (createResp as? HTTPURLResponse)?.statusCode ?? 0
        guard (200...299).contains(createCode) else { throw CloudBackupError.uploadFailed(createCode) }
    }

    func presentationAnchor(for session: ASWebAuthenticationSession) -> ASPresentationAnchor {
        UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap(\.windows)
            .first(where: \.isKeyWindow) ?? ASPresentationAnchor()
    }

    private func exchangeCode(code: String, clientId: String, redirectUri: String, verifier: String) async throws -> String {
        var req = URLRequest(url: URL(string: "https://oauth2.googleapis.com/token")!)
        req.httpMethod = "POST"
        req.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")
        let params = [
            "code=\(code.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? code)",
            "client_id=\(clientId)",
            "redirect_uri=\(redirectUri.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? redirectUri)",
            "grant_type=authorization_code",
            "code_verifier=\(verifier)",
        ].joined(separator: "&")
        req.httpBody = Data(params.utf8)

        let (data, resp) = try await URLSession.shared.data(for: req)
        let status = (resp as? HTTPURLResponse)?.statusCode ?? 0
        guard (200...299).contains(status),
              let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let accessToken = json["access_token"] as? String else {
            throw CloudBackupError.notAuthenticated
        }
        return accessToken
    }

    private func randomVerifier() -> String {
        var bytes = [UInt8](repeating: 0, count: 32)
        _ = SecRandomCopyBytes(kSecRandomDefault, bytes.count, &bytes)
        return Data(bytes).base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
    }

    private func sha256Base64URL(_ input: String) -> String {
        let digest = SHA256.hash(data: Data(input.utf8))
        return Data(digest).base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
    }
}
