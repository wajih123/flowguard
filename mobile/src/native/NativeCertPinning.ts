import type { TurboModule } from 'react-native'
import { TurboModuleRegistry } from 'react-native'

export interface Spec extends TurboModule {
  secureFetch(url: string, options: Object): Promise<Object>
}

export default TurboModuleRegistry.getEnforcing<Spec>('FlowGuardCertPinning')
