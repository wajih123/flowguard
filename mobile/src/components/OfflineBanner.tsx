import React, { useEffect, useRef, useState } from 'react'
import { Animated, StyleSheet, Text } from 'react-native'
import { colors, spacing } from '../theme'

/**
 * Polls connectivity by attempting a lightweight fetch to Cloudflare's DNS.
 * Avoids adding @react-native-community/netinfo as an extra dependency.
 */
const CHECK_URL = 'https://1.1.1.1'
const POLL_INTERVAL_MS = 5_000
const RECONNECT_SHOW_MS = 3_000

async function isReachable(): Promise<boolean> {
  try {
    const ctrl = new AbortController()
    const tid = setTimeout(() => ctrl.abort(), 3_000)
    await fetch(CHECK_URL, { method: 'HEAD', signal: ctrl.signal })
    clearTimeout(tid)
    return true
  } catch {
    return false
  }
}

export const OfflineBanner: React.FC = () => {
  const [offline, setOffline] = useState(false)
  const [reconnectTime, setReconnectTime] = useState('')
  const [showReconnect, setShowReconnect] = useState(false)
  const translateY = useRef(new Animated.Value(-60)).current
  const prevOffline = useRef(false)

  useEffect(() => {
    let timer: ReturnType<typeof setTimeout>

    const poll = async () => {
      const reachable = await isReachable()
      const nowOffline = !reachable

      if (!nowOffline && prevOffline.current) {
        const t = new Date().toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' })
        setReconnectTime(t)
        setShowReconnect(true)
        setTimeout(() => setShowReconnect(false), RECONNECT_SHOW_MS)
      }

      prevOffline.current = nowOffline
      setOffline(nowOffline)
      timer = setTimeout(poll, POLL_INTERVAL_MS)
    }

    poll()
    return () => clearTimeout(timer)
  }, [])

  const visible = offline || showReconnect

  useEffect(() => {
    Animated.spring(translateY, {
      toValue: visible ? 0 : -60,
      useNativeDriver: true,
      damping: 18,
      stiffness: 180,
    }).start()
  }, [visible, translateY])

  if (!visible) {
    return null
  }

  return (
    <Animated.View
      style={[
        styles.banner,
        offline ? styles.offline : styles.online,
        { transform: [{ translateY }] },
      ]}
      pointerEvents="none"
    >
      <Text style={styles.text}>
        {offline
          ? '📡  Hors connexion · Données mises en cache'
          : `✅  Reconnecté · ${reconnectTime}`}
      </Text>
    </Animated.View>
  )
}

const styles = StyleSheet.create({
  banner: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    zIndex: 9999,
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: 8,
    paddingHorizontal: spacing.md,
  },
  offline: { backgroundColor: colors.danger },
  online: { backgroundColor: colors.success },
  text: { color: '#fff', fontSize: 13, fontWeight: '600' },
})
