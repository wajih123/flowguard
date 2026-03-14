import React, { useEffect } from 'react'
import { type ViewStyle } from 'react-native'
import Animated, {
  useSharedValue,
  useAnimatedStyle,
  withRepeat,
  withTiming,
  Easing,
} from 'react-native-reanimated'
import { colors } from '../theme'

interface SkeletonCardProps {
  height: number
  width?: number | string
  borderRadius?: number
}

export const SkeletonCard: React.FC<SkeletonCardProps> = ({
  height,
  width = '100%',
  borderRadius = 16,
}) => {
  const opacity = useSharedValue(0.3)

  useEffect(() => {
    opacity.value = withRepeat(
      withTiming(0.7, { duration: 1000, easing: Easing.inOut(Easing.ease) }),
      -1,
      true,
    )
  }, [opacity])

  const animatedStyle = useAnimatedStyle((): ViewStyle => ({
    opacity: opacity.value,
  }))

  return (
    <Animated.View
      style={[
        {
          height,
          width: width as number | undefined,
          borderRadius,
          backgroundColor: colors.card,
        },
        animatedStyle,
      ]}
    />
  )
}
