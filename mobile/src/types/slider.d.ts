declare module '@react-native-community/slider' {
  import { Component } from 'react';
  import { StyleProp, ViewStyle } from 'react-native';

  export interface SliderProps {
    value?: number
    minimumValue?: number
    maximumValue?: number
    step?: number
    onValueChange?: (value: number) => void
    style?: StyleProp<ViewStyle>
    minimumTrackTintColor?: string
    maximumTrackTintColor?: string
    thumbTintColor?: string
    disabled?: boolean
    testID?: string
  }

  export default class Slider extends Component<SliderProps> {}
}
