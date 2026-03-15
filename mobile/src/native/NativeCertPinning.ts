import type { TurboModule } from 'react-native'
import { TurboModuleRegistry } from 'react-native'

export interface Spec extends TurboModule {
  secureFetch(url: string, options: Record<string, unknown>): Promise<Record<string, unknown>>
}

export default TurboModuleRegistry.getEnforcing<Spec>('FlowGuardCertPinning')
