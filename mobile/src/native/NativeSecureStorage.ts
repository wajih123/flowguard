import type { TurboModule } from 'react-native';
import { TurboModuleRegistry } from 'react-native';

export interface Spec extends TurboModule {
  saveToken(key: string, value: string): Promise<boolean>
  getToken(key: string): Promise<string | null>
  deleteToken(key: string): Promise<boolean>
  clearAll(): Promise<boolean>
}

export default TurboModuleRegistry.getEnforcing<Spec>('FlowGuardSecureStorage');
