import type { TurboModule } from 'react-native'
import { TurboModuleRegistry } from 'react-native'

export interface Spec extends TurboModule {
  isAvailable(): Promise<boolean>
  authenticate(reason: string): Promise<{ success: boolean; error?: string }>
  getBiometryType(): Promise<'FaceID' | 'TouchID' | 'Fingerprint' | 'None'>
}

export default TurboModuleRegistry.getEnforcing<Spec>('FlowGuardBiometric')
