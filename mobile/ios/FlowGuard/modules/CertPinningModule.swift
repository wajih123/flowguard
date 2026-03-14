import Foundation
import CommonCrypto

@objc(FlowGuardCertPinning)
class CertPinningModule: NSObject {

  private let pinnedHashes: Set<String> = [
    "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
    "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB="
  ]

  @objc
  func secureFetch(_ url: String, options: NSDictionary, resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
    guard let requestUrl = URL(string: url) else {
      reject("FETCH_ERROR", "URL invalide", nil)
      return
    }

    var request = URLRequest(url: requestUrl)
    request.timeoutInterval = 30

    let method = options["method"] as? String ?? "GET"
    request.httpMethod = method

    if let headers = options["headers"] as? [String: String] {
      for (key, value) in headers {
        request.setValue(value, forHTTPHeaderField: key)
      }
    }

    if let body = options["body"] as? String {
      request.httpBody = body.data(using: .utf8)
    }

    let delegate = CertPinningDelegate(pinnedHashes: pinnedHashes)
    let session = URLSession(configuration: .default, delegate: delegate, delegateQueue: nil)

    let task = session.dataTask(with: request) { data, response, error in
      if let pinError = delegate.pinningError {
        reject("CERTIFICATE_PINNING_FAILED", pinError, nil)
        return
      }

      if let error = error {
        reject("NETWORK_ERROR", "Erreur réseau: \(error.localizedDescription)", nil)
        return
      }

      guard let httpResponse = response as? HTTPURLResponse else {
        reject("NETWORK_ERROR", "Réponse invalide", nil)
        return
      }

      let result: [String: Any] = [
        "status": httpResponse.statusCode,
        "body": data.flatMap { String(data: $0, encoding: .utf8) } ?? "",
        "headers": httpResponse.allHeaderFields
      ]

      resolve(result)
    }

    task.resume()
  }

  @objc
  static func requiresMainQueueSetup() -> Bool {
    return false
  }
}

private class CertPinningDelegate: NSObject, URLSessionDelegate {

  let pinnedHashes: Set<String>
  var pinningError: String?

  init(pinnedHashes: Set<String>) {
    self.pinnedHashes = pinnedHashes
    super.init()
  }

  func urlSession(_ session: URLSession, didReceive challenge: URLAuthenticationChallenge, completionHandler: @escaping (URLSession.AuthChallengeDisposition, URLCredential?) -> Void) {
    guard challenge.protectionSpace.authenticationMethod == NSURLAuthenticationMethodServerTrust,
          let serverTrust = challenge.protectionSpace.serverTrust else {
      completionHandler(.cancelAuthenticationChallenge, nil)
      return
    }

    let certificateCount = SecTrustGetCertificateCount(serverTrust)
    var matched = false

    for index in 0..<certificateCount {
      guard let certificate = SecTrustCopyCertificateChain(serverTrust) else { continue }
      let certificates = certificate as! [SecCertificate]
      guard index < certificates.count else { continue }
      let cert = certificates[index]
      let certData = SecCertificateCopyData(cert) as Data

      var hash = [UInt8](repeating: 0, count: Int(CC_SHA256_DIGEST_LENGTH))
      certData.withUnsafeBytes { buffer in
        _ = CC_SHA256(buffer.baseAddress, CC_LONG(certData.count), &hash)
      }
      let hashBase64 = Data(hash).base64EncodedString()

      if pinnedHashes.contains(hashBase64) {
        matched = true
        break
      }
    }

    if matched {
      completionHandler(.useCredential, URLCredential(trust: serverTrust))
    } else {
      pinningError = "Échec de vérification du certificat"
      completionHandler(.cancelAuthenticationChallenge, nil)
    }
  }
}
