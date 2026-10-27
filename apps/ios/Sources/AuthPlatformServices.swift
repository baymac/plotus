import UIKit
import AuthenticationServices
import CryptoKit
import Security
import GoogleSignIn
import PlotusApp

/// OS adapters only. Shared Kotlin owns accounts, sessions and every app screen.
final class AuthPlatformServices: NSObject, NativeAuthHost, ASAuthorizationControllerDelegate, ASAuthorizationControllerPresentationContextProviding, ASWebAuthenticationPresentationContextProviding {
    private let session = URLSession(configuration: .ephemeral)
    private var passkeyCompletion: ((String?, String?) -> Void)?
    private var passkeyOperation: String?
    private var authorizationController: ASAuthorizationController?
    private var webAuthenticationSession: ASWebAuthenticationSession?
    private let keychain: [String: Any] = [
        kSecClass as String: kSecClassGenericPassword,
        kSecAttrService as String: "com.plotus.app.auth",
        kSecAttrAccount as String: "refresh-session"
    ]

    private func setting(_ key: String) -> String? {
        guard let value = Bundle.main.object(forInfoDictionaryKey: key) as? String,
              !value.isEmpty, !value.contains("$(") else { return nil }
        return value
    }
    private func complete(_ completion: @escaping (String?, String?) -> Void, _ value: String?, _ error: String?) {
        DispatchQueue.main.async { _ = completion(value, error) }
    }

    func request(method: String, path: String, body: String?, bearer: String?, csrf: String?, completion: @escaping (String?, String?) -> Void) {
        guard (path.hasPrefix("/api/auth/") || path.hasPrefix("/v1/")), let origin = setting("PlotusAuthURL"), let base = URL(string: origin),
              base.scheme == "https" || ["localhost", "127.0.0.1", "::1"].contains(base.host ?? ""),
              let url = URL(string: path, relativeTo: base)?.absoluteURL else {
            complete(completion, nil, "Sign-in isn’t configured on this device."); return
        }
        var request = URLRequest(url: url, timeoutInterval: 25)
        request.httpMethod = method
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        if let body { request.httpBody = Data(body.utf8) }
        if let bearer, !bearer.isEmpty { request.setValue("Bearer \(bearer)", forHTTPHeaderField: "Authorization") }
        if let csrf { request.setValue(csrf, forHTTPHeaderField: "X-CSRF-Token") }
        session.dataTask(with: request) { data, response, error in
            guard error == nil, let response = response as? HTTPURLResponse else {
                self.complete(completion, nil, "Couldn’t connect to Plotus. Please try again."); return
            }
            let wire: [String: Any] = ["status": response.statusCode, "body": String(data: data ?? Data(), encoding: .utf8) ?? ""]
            guard let json = try? JSONSerialization.data(withJSONObject: wire), let value = String(data: json, encoding: .utf8) else {
                self.complete(completion, nil, "Couldn’t read the sign-in response."); return
            }
            self.complete(completion, value, nil)
        }.resume()
    }

    func createProof() -> String {
        var bytes = [UInt8](repeating: 0, count: 32)
        guard SecRandomCopyBytes(kSecRandomDefault, bytes.count, &bytes) == errSecSuccess else { return "{}" }
        let verifier = Data(bytes).base64URL
        let challenge = Data(SHA256.hash(data: Data(verifier.utf8))).base64URL
        return "{\"verifier\":\"\(verifier)\",\"challenge\":\"\(challenge)\"}"
    }

    func googleIdentity(nonce: String, completion: @escaping (String?, String?) -> Void) {
        guard let client = setting("GIDClientID"), let server = setting("GIDServerClientID"), let presenter = presenter() else {
            complete(completion, nil, "Google sign-in isn’t configured on this device."); return
        }
        GIDSignIn.sharedInstance.configuration = GIDConfiguration(clientID: client, serverClientID: server)
        GIDSignIn.sharedInstance.signIn(withPresenting: presenter, hint: nil, additionalScopes: nil, nonce: nonce) { result, error in
            if let error = error as NSError? {
                self.complete(completion, nil, error.code == -5 ? "cancelled" : "Couldn’t complete Google sign-in. Please try again.")
                return
            }
            guard let token = result?.user.idToken?.tokenString else {
                self.complete(completion, nil, "Google didn’t return a sign-in credential. Please try again."); return
            }
            self.complete(completion, token, nil)
        }
    }

    func readSession() -> String? {
        var query = keychain
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne
        var item: CFTypeRef?
        guard SecItemCopyMatching(query as CFDictionary, &item) == errSecSuccess, let data = item as? Data else { return nil }
        return String(data: data, encoding: .utf8)
    }

    func facebookBrowser(url: String, completion: @escaping (String?, String?) -> Void) {
        guard let target = URL(string: url), target.scheme == "https", target.host != nil,
              target.user == nil, target.password == nil, target.path == "/api/auth/login/facebook/authorize" else {
            complete(completion, nil, "Facebook sign-in isn’t configured on this device."); return
        }
        DispatchQueue.main.async {
            let browser = ASWebAuthenticationSession(url: target, callbackURLScheme: "plotus") { returned, error in
                self.webAuthenticationSession = nil
                if let error {
                    self.complete(completion, nil, (error as NSError).code == ASWebAuthenticationSessionError.canceledLogin.rawValue ? "cancelled" : "Couldn’t complete Facebook sign-in. Please try again.")
                    return
                }
                guard let returned, returned.scheme == "plotus", returned.host == "oauth", returned.path == "/callback",
                      let components = URLComponents(url: returned, resolvingAgainstBaseURL: false) else {
                    self.complete(completion, nil, "Facebook sign-in could not be verified. Please try again."); return
                }
                var payload: [String: String] = [:]
                for key in ["state", "code", "error"] {
                    let values = (components.queryItems ?? []).filter { $0.name == key }
                    guard values.count <= 1 else {
                        self.complete(completion, nil, "Facebook sign-in could not be verified. Please try again."); return
                    }
                    if let value = values.first?.value { payload[key] = value }
                }
                guard let data = try? JSONSerialization.data(withJSONObject: payload), let json = String(data: data, encoding: .utf8) else {
                    self.complete(completion, nil, "Couldn’t read the sign-in response."); return
                }
                self.complete(completion, json, nil)
            }
            browser.presentationContextProvider = self
            self.webAuthenticationSession = browser
            if !browser.start() {
                self.webAuthenticationSession = nil
                self.complete(completion, nil, "Couldn’t open Facebook sign-in. Please try again.")
            }
        }
    }

    func presentationAnchor(for session: ASWebAuthenticationSession) -> ASPresentationAnchor {
        (UIApplication.shared.delegate as? AppDelegate)?.window ?? ASPresentationAnchor()
    }
    func writeSession(value: String?) -> Bool {
        guard let value else {
            let status = SecItemDelete(keychain as CFDictionary)
            return status == errSecSuccess || status == errSecItemNotFound
        }
        let attributes: [String: Any] = [kSecValueData as String: Data(value.utf8), kSecAttrAccessible as String: kSecAttrAccessibleWhenUnlockedThisDeviceOnly]
        let status = SecItemUpdate(keychain as CFDictionary, attributes as CFDictionary)
        if status == errSecItemNotFound {
            return SecItemAdd(keychain.merging(attributes) { _, new in new } as CFDictionary, nil) == errSecSuccess
        }
        return status == errSecSuccess
    }
    func signOutProvider() { GIDSignIn.sharedInstance.signOut() }

    func passkey(operation: String, options: String, completion: @escaping (String?, String?) -> Void) {
        guard let data = options.data(using: .utf8), let envelope = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let key = envelope["publicKey"] as? [String: Any], let encoded = key["challenge"] as? String, let challenge = Data(base64URL: encoded) else {
            complete(completion, nil, "Couldn’t start passkey sign-in."); return
        }
        let rp = operation == "register" ? (key["rp"] as? [String: Any])?["id"] as? String : key["rpId"] as? String
        guard let rp, !rp.isEmpty, rp != "localhost", rp != "127.0.0.1" else {
            complete(completion, nil, "Passkeys need a secure Plotus domain. Please use plotus.app."); return
        }
        let provider = ASAuthorizationPlatformPublicKeyCredentialProvider(relyingPartyIdentifier: rp)
        let request: ASAuthorizationRequest
        if operation == "register" {
            guard let user = key["user"] as? [String: Any], let id = user["id"] as? String, let userID = Data(base64URL: id), let name = user["name"] as? String else {
                complete(completion, nil, "Couldn’t start passkey enrollment."); return
            }
            let registration = provider.createCredentialRegistrationRequest(challenge: challenge, name: name, userID: userID)
            registration.userVerificationPreference = .required
            request = registration
        } else {
            let assertion = provider.createCredentialAssertionRequest(challenge: challenge)
            assertion.userVerificationPreference = .required
            request = assertion
        }
        passkeyCompletion = completion
        passkeyOperation = operation
        let controller = ASAuthorizationController(authorizationRequests: [request])
        controller.delegate = self
        controller.presentationContextProvider = self
        authorizationController = controller
        controller.performRequests()
    }
    func presentationAnchor(for controller: ASAuthorizationController) -> ASPresentationAnchor {
        (UIApplication.shared.delegate as? AppDelegate)?.window ?? ASPresentationAnchor()
    }
    func authorizationController(controller: ASAuthorizationController, didCompleteWithAuthorization authorization: ASAuthorization) {
        var id: Data
        var response: [String: Any]
        if let credential = authorization.credential as? ASAuthorizationPlatformPublicKeyCredentialAssertion {
            id = credential.credentialID
            response = ["clientDataJSON": credential.rawClientDataJSON.base64URL,
                        "authenticatorData": credential.rawAuthenticatorData.base64URL,
                        "signature": credential.signature.base64URL,
                        "userHandle": credential.userID.base64URL]
        } else if let credential = authorization.credential as? ASAuthorizationPlatformPublicKeyCredentialRegistration,
                  let attestation = credential.rawAttestationObject {
            id = credential.credentialID
            response = ["clientDataJSON": credential.rawClientDataJSON.base64URL, "attestationObject": attestation.base64URL]
        } else { finishPasskey(nil, "Couldn’t read the passkey response."); return }
        let payload: [String: Any] = ["id": id.base64URL, "rawId": id.base64URL, "type": "public-key",
                                      "response": response, "clientExtensionResults": [:], "authenticatorAttachment": "platform"]
        guard let data = try? JSONSerialization.data(withJSONObject: payload), let json = String(data: data, encoding: .utf8) else {
            finishPasskey(nil, "Couldn’t read the passkey response."); return
        }
        finishPasskey(json, nil)
    }
    func authorizationController(controller: ASAuthorizationController, didCompleteWithError error: Error) {
        let message = passkeyOperation == "register" ? "Couldn’t add this passkey. Please try again." : "Couldn’t sign in with this passkey. Please try again."
        finishPasskey(nil, (error as NSError).code == ASAuthorizationError.canceled.rawValue ? "cancelled" : message)
    }
    private func finishPasskey(_ value: String?, _ error: String?) {
        if let callback = passkeyCompletion { complete(callback, value, error) }
        passkeyCompletion = nil; passkeyOperation = nil; authorizationController = nil
    }
    private func presenter() -> UIViewController? {
        var controller = (UIApplication.shared.delegate as? AppDelegate)?.window?.rootViewController
        while let presented = controller?.presentedViewController { controller = presented }
        return controller
    }
}

private extension Data {
    var base64URL: String { base64EncodedString().replacingOccurrences(of: "+", with: "-").replacingOccurrences(of: "/", with: "_").replacingOccurrences(of: "=", with: "") }
    init?(base64URL value: String) {
        let standard = value.replacingOccurrences(of: "-", with: "+").replacingOccurrences(of: "_", with: "/")
        self.init(base64Encoded: standard + String(repeating: "=", count: (4 - standard.count % 4) % 4))
    }
}
