import { useFeatureFlagStore } from '../store/featureFlagStore';

export const useFeatureFlag = (key: string): boolean => {
  return useFeatureFlagStore((s) => s.isEnabled(key));
};
