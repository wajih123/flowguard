import React, { useState, useCallback, useRef } from 'react';
import {
  View,
  Text,
  StyleSheet,
  Dimensions,
  FlatList,
  type ViewToken,
  type ListRenderItem,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import Animated, {
  useSharedValue,
  useAnimatedStyle,
  withSpring,
  interpolate,
  Extrapolation,
} from 'react-native-reanimated';
import type { StackScreenProps } from '@react-navigation/stack';
import { FlowGuardButton } from '../../components/FlowGuardButton';
import { Routes } from '../../navigation/routes';
import { colors, typography, spacing } from '../../theme';

const { width: SCREEN_WIDTH } = Dimensions.get('window');

interface OnboardingPage {
  id: string
  icon: string
  title: string
  subtitle: string
}

const PAGES: OnboardingPage[] = [
  {
    id: '1',
    icon: '📊',
    title: 'Prédisez votre trésorerie',
    subtitle:
      'Notre IA analyse vos flux et prédit votre solde à 30, 60 et 90 jours avec une précision remarquable.',
  },
  {
    id: '2',
    icon: '🔔',
    title: 'Alertes proactives',
    subtitle:
      'Recevez des alertes J-7 avant chaque risque de découvert. Ne soyez plus jamais pris au dépourvu.',
  },
  {
    id: '3',
    icon: '⚡',
    title: 'Crédit Flash instantané',
    subtitle:
      "Besoin de trésorerie urgente ? Obtenez un micro-crédit en 2 minutes, directement depuis l'app.",
  },
];

type Props = StackScreenProps<Record<string, undefined>, string>

export const OnboardingScreen: React.FC<Props> = ({ navigation }) => {
  const [currentIndex, setCurrentIndex] = useState(0);
  const flatListRef = useRef<FlatList>(null);
  const progress = useSharedValue(0);

  const onViewableItemsChanged = useCallback(
    ({ viewableItems }: { viewableItems: ViewToken[] }) => {
      if (viewableItems.length > 0 && viewableItems[0].index != null) {
        setCurrentIndex(viewableItems[0].index);
        progress.value = withSpring(viewableItems[0].index);
      }
    },
    [progress],
  );

  const viewabilityConfig = useRef({ viewAreaCoveragePercentThreshold: 50 }).current;

  const handleNext = useCallback(() => {
    if (currentIndex < PAGES.length - 1) {
      flatListRef.current?.scrollToIndex({ index: currentIndex + 1, animated: true });
    } else {
      navigation.navigate(Routes.Login as never);
    }
  }, [currentIndex, navigation]);

  const handleSkip = useCallback(() => {
    navigation.navigate(Routes.Login as never);
  }, [navigation]);

  const renderPage: ListRenderItem<OnboardingPage> = useCallback(
    ({ item }) => (
      <View style={styles.page}>
        <Text style={styles.pageIcon}>{item.icon}</Text>
        <Text style={styles.pageTitle}>{item.title}</Text>
        <Text style={styles.pageSubtitle}>{item.subtitle}</Text>
      </View>
    ),
    [],
  );

  return (
    <SafeAreaView style={styles.container} edges={['top', 'bottom']}>
      <View style={styles.skipRow}>
        <Text onPress={handleSkip} style={styles.skipText}>
          Passer
        </Text>
      </View>

      <FlatList
        ref={flatListRef}
        data={PAGES}
        keyExtractor={(item) => item.id}
        renderItem={renderPage}
        horizontal
        pagingEnabled
        showsHorizontalScrollIndicator={false}
        onViewableItemsChanged={onViewableItemsChanged}
        viewabilityConfig={viewabilityConfig}
        bounces={false}
      />

      {/* Dot indicators */}
      <View style={styles.dotsRow}>
        {PAGES.map((_, index) => {
          const DotComponent: React.FC = () => {
            const dotStyle = useAnimatedStyle(() => {
              const scale = interpolate(
                progress.value,
                [index - 1, index, index + 1],
                [1, 1.4, 1],
                Extrapolation.CLAMP,
              );
              const opacity = interpolate(
                progress.value,
                [index - 1, index, index + 1],
                [0.3, 1, 0.3],
                Extrapolation.CLAMP,
              );
              return { transform: [{ scale }], opacity };
            });

            return <Animated.View style={[styles.dot, dotStyle]} />;
          };

          return <DotComponent key={index} />;
        })}
      </View>

      <View style={styles.bottomActions}>
        <FlowGuardButton
          title={currentIndex === PAGES.length - 1 ? 'Commencer' : 'Suivant'}
          onPress={handleNext}
          variant="primary"
        />
      </View>
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: colors.background,
  },
  skipRow: {
    alignItems: 'flex-end',
    paddingHorizontal: spacing.md,
    paddingTop: spacing.sm,
  },
  skipText: {
    color: colors.textSecondary,
    fontSize: typography.body.fontSize,
    fontWeight: '600',
    paddingVertical: spacing.sm,
    paddingHorizontal: spacing.sm,
  },
  page: {
    width: SCREEN_WIDTH,
    justifyContent: 'center',
    alignItems: 'center',
    paddingHorizontal: spacing.xl,
  },
  pageIcon: {
    fontSize: 80,
    marginBottom: spacing.xl,
  },
  pageTitle: {
    ...typography.h1,
    color: colors.textPrimary,
    textAlign: 'center',
    marginBottom: spacing.md,
  },
  pageSubtitle: {
    color: colors.textSecondary,
    fontSize: typography.body.fontSize,
    textAlign: 'center',
    lineHeight: 24,
  },
  dotsRow: {
    flexDirection: 'row',
    justifyContent: 'center',
    gap: spacing.sm,
    paddingVertical: spacing.lg,
  },
  dot: {
    width: 8,
    height: 8,
    borderRadius: 4,
    backgroundColor: colors.primary,
  },
  bottomActions: {
    paddingHorizontal: spacing.md,
    paddingBottom: spacing.lg,
  },
});
