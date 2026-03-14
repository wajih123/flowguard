import { useState, useEffect, useCallback } from 'react'
import NativeBiometric from '../native/NativeBiometric'

export const useBiometric = () => {
  const [isAvailable, setIsAvailable] = useState(false)
  const [biometryType, setBiometryType] = useState<string>('None')

  useEffect(() => {
    const check = async () => {
      try {
        const available = await NativeBiometric.isAvailable()
        setIsAvailable(available)
        if (available) {
          const type = await NativeBiometric.getBiometryType()
          setBiometryType(type)
        }
      } catch {
        setIsAvailable(false)
        setBiometryType('None')
      }
    }
    check()
  }, [])

  const authenticate = useCallback(async (): Promise<boolean> => {
    try {
      const result = await NativeBiometric.authenticate(
        'Confirmez votre identité pour continuer',
      )
      return result.success
    } catch {
      return false
    }
  }, [])

  return { isAvailable, biometryType, authenticate }
}
