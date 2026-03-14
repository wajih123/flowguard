import NativeSecureStorage from './NativeSecureStorage'
import NativeBiometric from './NativeBiometric'

const ACCESS_TOKEN_KEY = 'flowguard_access_token'
const REFRESH_TOKEN_KEY = 'flowguard_refresh_token'

export const SecureStorage = {
  async saveAccessToken(token: string): Promise<boolean> {
    try {
      return await NativeSecureStorage.saveToken(ACCESS_TOKEN_KEY, token)
    } catch {
      return false
    }
  },

  async getAccessToken(): Promise<string | null> {
    try {
      return await NativeSecureStorage.getToken(ACCESS_TOKEN_KEY)
    } catch {
      return null
    }
  },

  async saveRefreshToken(token: string): Promise<boolean> {
    try {
      return await NativeSecureStorage.saveToken(REFRESH_TOKEN_KEY, token)
    } catch {
      return false
    }
  },

  async getRefreshToken(): Promise<string | null> {
    try {
      return await NativeSecureStorage.getToken(REFRESH_TOKEN_KEY)
    } catch {
      return null
    }
  },

  async clearAll(): Promise<boolean> {
    try {
      return await NativeSecureStorage.clearAll()
    } catch {
      return false
    }
  },
}

export const Biometric = {
  async isAvailable(): Promise<boolean> {
    try {
      return await NativeBiometric.isAvailable()
    } catch {
      return false
    }
  },

  async authenticate(): Promise<boolean> {
    try {
      const result = await NativeBiometric.authenticate(
        'Confirmez votre identité pour continuer',
      )
      return result.success
    } catch {
      return false
    }
  },

  async getType(): Promise<string> {
    try {
      return await NativeBiometric.getBiometryType()
    } catch {
      return 'None'
    }
  },
}

export { default as NativeSecureStorage } from './NativeSecureStorage'
export { default as NativeBiometric } from './NativeBiometric'
export { default as NativeCertPinning } from './NativeCertPinning'
