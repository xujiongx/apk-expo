import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from 'react';
import {
  Animated,
  Easing,
  Pressable,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { colors, radius, space, type } from '../theme';

export type ToastTone = 'info' | 'success' | 'warning' | 'error';

type ToastInput = {
  title?: string;
  message: string;
  tone?: ToastTone;
  duration?: number;
};

type ToastState = Required<Pick<ToastInput, 'message' | 'tone' | 'duration'>> & {
  title?: string;
  id: number;
};

type ToastContextValue = {
  show: (input: string | ToastInput) => void;
  success: (message: string, title?: string) => void;
  error: (message: string, title?: string) => void;
  warning: (message: string, title?: string) => void;
  info: (message: string, title?: string) => void;
  hide: () => void;
};

const ToastContext = createContext<ToastContextValue | null>(null);

const TONE: Record<
  ToastTone,
  { icon: string; iconBg: string; iconFg: string; ring: string }
> = {
  info: {
    icon: 'i',
    iconBg: colors.primaryContainer,
    iconFg: colors.onPrimaryContainer,
    ring: colors.primary,
  },
  success: {
    icon: '✓',
    iconBg: colors.successContainer,
    iconFg: colors.success,
    ring: colors.success,
  },
  warning: {
    icon: '!',
    iconBg: colors.warningContainer,
    iconFg: colors.warning,
    ring: colors.warning,
  },
  error: {
    icon: '!',
    iconBg: colors.errorContainer,
    iconFg: colors.error,
    ring: colors.error,
  },
};

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toast, setToast] = useState<ToastState | null>(null);
  const opacity = useRef(new Animated.Value(0)).current;
  const translateY = useRef(new Animated.Value(20)).current;
  const scale = useRef(new Animated.Value(0.96)).current;
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const seq = useRef(0);

  const hide = useCallback(() => {
    if (timerRef.current) {
      clearTimeout(timerRef.current);
      timerRef.current = null;
    }
    Animated.parallel([
      Animated.timing(opacity, {
        toValue: 0,
        duration: 160,
        easing: Easing.in(Easing.cubic),
        useNativeDriver: true,
      }),
      Animated.timing(translateY, {
        toValue: 12,
        duration: 160,
        easing: Easing.in(Easing.cubic),
        useNativeDriver: true,
      }),
      Animated.timing(scale, {
        toValue: 0.98,
        duration: 160,
        useNativeDriver: true,
      }),
    ]).start(({ finished }) => {
      if (finished) setToast(null);
    });
  }, [opacity, scale, translateY]);

  const show = useCallback(
    (input: string | ToastInput) => {
      const next: ToastState = {
        id: ++seq.current,
        message: typeof input === 'string' ? input : input.message,
        title: typeof input === 'string' ? undefined : input.title,
        tone: typeof input === 'string' ? 'info' : (input.tone ?? 'info'),
        duration: typeof input === 'string' ? 2600 : (input.duration ?? 2600),
      };

      if (timerRef.current) {
        clearTimeout(timerRef.current);
        timerRef.current = null;
      }

      setToast(next);
      opacity.setValue(0);
      translateY.setValue(20);
      scale.setValue(0.96);

      Animated.parallel([
        Animated.timing(opacity, {
          toValue: 1,
          duration: 220,
          easing: Easing.out(Easing.cubic),
          useNativeDriver: true,
        }),
        Animated.timing(translateY, {
          toValue: 0,
          duration: 220,
          easing: Easing.out(Easing.cubic),
          useNativeDriver: true,
        }),
        Animated.timing(scale, {
          toValue: 1,
          duration: 220,
          easing: Easing.out(Easing.cubic),
          useNativeDriver: true,
        }),
      ]).start();

      timerRef.current = setTimeout(() => hide(), next.duration);
    },
    [hide, opacity, scale, translateY],
  );

  useEffect(
    () => () => {
      if (timerRef.current) clearTimeout(timerRef.current);
    },
    [],
  );

  const value = useMemo<ToastContextValue>(
    () => ({
      show,
      hide,
      success: (message, title) => show({ message, title, tone: 'success' }),
      error: (message, title) => show({ message, title, tone: 'error' }),
      warning: (message, title) => show({ message, title, tone: 'warning' }),
      info: (message, title) => show({ message, title, tone: 'info' }),
    }),
    [hide, show],
  );

  const tone = toast ? TONE[toast.tone] : TONE.info;
  const insets = useSafeAreaInsets();

  return (
    <ToastContext.Provider value={value}>
      {children}
      {toast ? (
        <View
          pointerEvents="box-none"
          style={[styles.host, { paddingBottom: Math.max(insets.bottom, 12) + 16 }]}
        >
          <Animated.View
            style={[
              styles.toast,
              {
                opacity,
                transform: [{ translateY }, { scale }],
                borderColor: tone.ring + '33',
              },
            ]}
          >
            <View style={[styles.iconWrap, { backgroundColor: tone.iconBg }]}>
              <Text style={[styles.iconText, { color: tone.iconFg }]}>{tone.icon}</Text>
            </View>

            <View style={styles.textCol}>
              {toast.title ? <Text style={styles.title}>{toast.title}</Text> : null}
              <Text style={styles.message} numberOfLines={3}>
                {toast.message}
              </Text>
            </View>

            <Pressable
              onPress={hide}
              hitSlop={10}
              accessibilityRole="button"
              accessibilityLabel="关闭提示"
              style={({ pressed }) => [styles.closeBtn, pressed && { opacity: 0.6 }]}
            >
              <Text style={styles.closeText}>✕</Text>
            </Pressable>
          </Animated.View>
        </View>
      ) : null}
    </ToastContext.Provider>
  );
}

export function useToast() {
  const ctx = useContext(ToastContext);
  if (!ctx) {
    throw new Error('useToast 必须在 ToastProvider 内使用');
  }
  return ctx;
}

const styles = StyleSheet.create({
  host: {
    ...StyleSheet.absoluteFill,
    justifyContent: 'flex-end',
    alignItems: 'center',
    paddingHorizontal: space.lg,
    zIndex: 1000,
  },
  toast: {
    width: '100%',
    maxWidth: 420,
    minHeight: 64,
    flexDirection: 'row',
    alignItems: 'center',
    gap: space.md,
    paddingVertical: 14,
    paddingLeft: 14,
    paddingRight: 10,
    backgroundColor: colors.surfaceLow,
    borderRadius: radius.lg,
    borderWidth: 1,
    borderColor: colors.outlineVariant,
    shadowColor: '#0F172A',
    shadowOpacity: 0.12,
    shadowRadius: 20,
    shadowOffset: { width: 0, height: 10 },
    elevation: 8,
  },
  iconWrap: {
    width: 36,
    height: 36,
    borderRadius: radius.full,
    alignItems: 'center',
    justifyContent: 'center',
  },
  iconText: {
    fontSize: 16,
    fontWeight: '700',
    lineHeight: 20,
  },
  textCol: {
    flex: 1,
    gap: 2,
    paddingRight: 4,
  },
  title: {
    ...type.bodyStrong,
    color: colors.onSurface,
  },
  message: {
    ...type.body,
    color: colors.onSurfaceVariant,
  },
  closeBtn: {
    width: 32,
    height: 32,
    borderRadius: radius.full,
    alignItems: 'center',
    justifyContent: 'center',
  },
  closeText: {
    fontSize: 13,
    color: colors.onSurfaceVariant,
    fontWeight: '600',
  },
});
