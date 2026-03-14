import Foundation
import Security

@objc(FlowGuardSecureStorage)
class SecureStorageModule: NSObject {

  private let service = "com.flowguard.tokens"

  @objc
  func saveToken(_ key: String, value: String, resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
    guard let data = value.data(using: .utf8) else {
      reject("SECURE_STORAGE_ERROR", "Impossible d'encoder la valeur", nil)
      return
    }

    let deleteQuery: [String: Any] = [
      kSecClass as String: kSecClassGenericPassword,
      kSecAttrService as String: service,
      kSecAttrAccount as String: key,
    ]
    SecItemDelete(deleteQuery as CFDictionary)

    let addQuery: [String: Any] = [
      kSecClass as String: kSecClassGenericPassword,
      kSecAttrService as String: service,
      kSecAttrAccount as String: key,
      kSecValueData as String: data,
      kSecAttrAccessible as String: kSecAttrAccessibleWhenUnlockedThisDeviceOnly,
    ]

    let status = SecItemAdd(addQuery as CFDictionary, nil)
    if status == errSecSuccess {
      resolve(true)
    } else {
      reject("SECURE_STORAGE_ERROR", "Échec de sauvegarde (OSStatus: \(status))", nil)
    }
  }

  @objc
  func getToken(_ key: String, resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
    let query: [String: Any] = [
      kSecClass as String: kSecClassGenericPassword,
      kSecAttrService as String: service,
      kSecAttrAccount as String: key,
      kSecReturnData as String: true,
      kSecMatchLimit as String: kSecMatchLimitOne,
    ]

    var result: AnyObject?
    let status = SecItemCopyMatching(query as CFDictionary, &result)

    if status == errSecSuccess, let data = result as? Data, let value = String(data: data, encoding: .utf8) {
      resolve(value)
    } else if status == errSecItemNotFound {
      resolve(nil)
    } else {
      reject("SECURE_STORAGE_ERROR", "Échec de lecture (OSStatus: \(status))", nil)
    }
  }

  @objc
  func deleteToken(_ key: String, resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
    let query: [String: Any] = [
      kSecClass as String: kSecClassGenericPassword,
      kSecAttrService as String: service,
      kSecAttrAccount as String: key,
    ]

    let status = SecItemDelete(query as CFDictionary)
    if status == errSecSuccess || status == errSecItemNotFound {
      resolve(true)
    } else {
      reject("SECURE_STORAGE_ERROR", "Échec de suppression (OSStatus: \(status))", nil)
    }
  }

  @objc
  func clearAll(_ resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
    let query: [String: Any] = [
      kSecClass as String: kSecClassGenericPassword,
      kSecAttrService as String: service,
    ]

    let status = SecItemDelete(query as CFDictionary)
    if status == errSecSuccess || status == errSecItemNotFound {
      resolve(true)
    } else {
      reject("SECURE_STORAGE_ERROR", "Échec de nettoyage (OSStatus: \(status))", nil)
    }
  }

  @objc
  static func requiresMainQueueSetup() -> Bool {
    return false
  }
}
