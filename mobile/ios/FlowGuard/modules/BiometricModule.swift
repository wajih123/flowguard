import Foundation
import LocalAuthentication

@objc(FlowGuardBiometric)
class BiometricModule: NSObject {

  @objc
  func isAvailable(_ resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
    let context = LAContext()
    var error: NSError?
    let available = context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: &error)
    resolve(available)
  }

  @objc
  func authenticate(_ reason: String, resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
    let context = LAContext()
    context.localizedCancelTitle = "Annuler"

    context.evaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, localizedReason: reason) { success, error in
      if success {
        resolve(["success": true])
      } else {
        resolve([
          "success": false,
          "error": error?.localizedDescription ?? "Authentification échouée"
        ])
      }
    }
  }

  @objc
  func getBiometryType(_ resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
    let context = LAContext()
    var error: NSError?
    _ = context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: &error)

    switch context.biometryType {
    case .faceID:
      resolve("FaceID")
    case .touchID:
      resolve("TouchID")
    default:
      resolve("None")
    }
  }

  @objc
  static func requiresMainQueueSetup() -> Bool {
    return false
  }
}
