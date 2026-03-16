import messaging from '@react-native-firebase/messaging';
import notifee, { AndroidImportance } from '@notifee/react-native';
import * as flowguardApi from '../api/flowguardApi';
import { useAccountStore } from '../store/accountStore';

const CHANNEL_ID = 'flowguard-alerts';

async function ensureChannel(): Promise<void> {
  await notifee.createChannel({
    id: CHANNEL_ID,
    name: 'Alertes FlowGuard',
    importance: AndroidImportance.HIGH,
    vibration: true,
  });
}

export async function requestPermission(): Promise<boolean> {
  const authStatus = await messaging().requestPermission();
  return (
    authStatus === messaging.AuthorizationStatus.AUTHORIZED ||
    authStatus === messaging.AuthorizationStatus.PROVISIONAL
  );
}

export async function getFcmToken(): Promise<string | null> {
  try {
    return await messaging().getToken();
  } catch {
    return null;
  }
}

export async function registerFcmToken(): Promise<void> {
  const token = await getFcmToken();
  const accountId = useAccountStore.getState().account?.id;
  if (token && accountId) {
    await flowguardApi.saveFcmToken(accountId, token);
  }
}

export function setupForegroundHandler(): () => void {
  const unsubscribeNotifee = notifee.onForegroundEvent(() => {
    // Handle foreground notification presses — navigation handled in App
  });

  const unsubscribeMessaging = messaging().onMessage(async (remoteMessage) => {
    await ensureChannel();
    await notifee.displayNotification({
      title: remoteMessage.notification?.title ?? 'FlowGuard',
      body: remoteMessage.notification?.body ?? '',
      android: {
        channelId: CHANNEL_ID,
        pressAction: { id: 'default' },
        importance: AndroidImportance.HIGH,
      },
      data: remoteMessage.data,
    });
  });

  return () => {
    unsubscribeNotifee();
    unsubscribeMessaging();
  };
}

export function setupBackgroundHandler(): void {
  messaging().setBackgroundMessageHandler(async (remoteMessage) => {
    await ensureChannel();
    await notifee.displayNotification({
      title: remoteMessage.notification?.title ?? 'FlowGuard',
      body: remoteMessage.notification?.body ?? '',
      android: {
        channelId: CHANNEL_ID,
        pressAction: { id: 'default' },
        importance: AndroidImportance.HIGH,
      },
      data: remoteMessage.data,
    });
  });
}
