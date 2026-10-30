import type { ReactNode } from 'react';
import {
  Platform,
  Pressable,
  StyleSheet,
  Text,
  View,
  type ViewStyle,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { colors, radius, space, type } from './theme';

function useTopInset() {
  const insets = useSafeAreaInsets();
  // Web 模拟器里 inset 常被算得过大，造成顶部大片空白
  if (Platform.OS === 'web') return space.lg;
  return Math.max(insets.top, space.sm) + space.sm;
}

export function Screen({
  children,
  title,
  subtitle,
  onBack,
}: {
  children: ReactNode;
  title: string;
  subtitle?: string;
  onBack?: () => void;
}) {
  const topInset = useTopInset();

  if (onBack) {
    return (
      <View style={[styles.screen, { paddingTop: topInset }]}>
        <View style={styles.topBar}>
          <Pressable
            onPress={onBack}
            hitSlop={12}
            accessibilityRole="button"
            accessibilityLabel="返回"
            style={({ pressed }) => [styles.iconBtn, pressed && styles.pressed]}
          >
            <Text style={styles.iconBtnText}>‹</Text>
          </Pressable>
          <Text style={styles.topBarTitle} numberOfLines={1}>
            {title}
          </Text>
          <View style={styles.iconBtnPlaceholder} />
        </View>
        {subtitle ? <Text style={styles.pageSubtitle}>{subtitle}</Text> : null}
        {children}
      </View>
    );
  }

  return (
    <View style={[styles.screen, { paddingTop: topInset }]}>
      <View style={styles.largeHeader}>
        <Text style={styles.headline}>{title}</Text>
        {subtitle ? <Text style={styles.subtitle}>{subtitle}</Text> : null}
      </View>
      {children}
    </View>
  );
}

export function Section({
  title,
  children,
  style,
}: {
  title?: string;
  children: ReactNode;
  style?: ViewStyle;
}) {
  return (
    <View style={[styles.section, style]}>
      {title ? <Text style={styles.sectionTitle}>{title}</Text> : null}
      <View style={styles.sectionBody}>{children}</View>
    </View>
  );
}

export function Panel({
  label,
  children,
  last,
}: {
  label?: string;
  children: ReactNode;
  last?: boolean;
}) {
  return (
    <Section title={label} style={last ? { marginBottom: space.sm } : undefined}>
      <View style={styles.panelPad}>{children}</View>
    </Section>
  );
}

export function ListRow({
  title,
  subtitle,
  leading,
  trailing,
  onPress,
  last,
}: {
  title: string;
  subtitle?: string;
  leading?: ReactNode;
  trailing?: ReactNode;
  onPress?: () => void;
  last?: boolean;
}) {
  const content = (
    <View style={[styles.listRow, !last && styles.listRowBorder]}>
      {leading ? <View style={styles.leading}>{leading}</View> : null}
      <View style={styles.listText}>
        <Text style={styles.listTitle}>{title}</Text>
        {subtitle ? <Text style={styles.listSubtitle}>{subtitle}</Text> : null}
      </View>
      {trailing ?? (onPress ? <Text style={styles.chevron}>›</Text> : null)}
    </View>
  );

  if (!onPress) return content;
  return (
    <Pressable
      onPress={onPress}
      android_ripple={{ color: colors.surfaceHigh }}
      style={({ pressed }) => [
        pressed && Platform.OS !== 'android' && styles.pressed,
      ]}
    >
      {content}
    </Pressable>
  );
}

export function Avatar({
  label,
  tone = 'primary',
}: {
  label: string;
  tone?: 'primary' | 'tertiary' | 'neutral';
}) {
  const bg =
    tone === 'tertiary'
      ? '#CCFBF1'
      : tone === 'neutral'
        ? colors.surfaceHigh
        : colors.primaryContainer;
  const fg =
    tone === 'tertiary'
      ? colors.tertiary
      : tone === 'neutral'
        ? colors.onSurfaceVariant
        : colors.onPrimaryContainer;
  return (
    <View style={[styles.avatar, { backgroundColor: bg }]}>
      <Text style={[styles.avatarText, { color: fg }]}>{label}</Text>
    </View>
  );
}

export function Button({
  label,
  onPress,
  variant = 'filled',
  disabled,
}: {
  label: string;
  onPress: () => void;
  variant?: 'filled' | 'tonal' | 'outlined' | 'text';
  disabled?: boolean;
}) {
  return (
    <Pressable
      disabled={disabled}
      onPress={onPress}
      accessibilityRole="button"
      android_ripple={{
        color:
          variant === 'filled' ? 'rgba(255,255,255,0.2)' : colors.primaryContainer,
      }}
      style={({ pressed }) => [
        styles.btnBase,
        variant === 'filled' && styles.btnFilled,
        variant === 'tonal' && styles.btnTonal,
        variant === 'outlined' && styles.btnOutlined,
        variant === 'text' && styles.btnTextOnly,
        pressed && !disabled && styles.pressed,
        disabled && styles.btnDisabled,
      ]}
    >
      <Text
        style={[
          styles.btnLabel,
          variant === 'filled' && { color: colors.onPrimary },
          variant === 'tonal' && { color: colors.onPrimaryContainer },
          (variant === 'outlined' || variant === 'text') && {
            color: colors.primary,
          },
          disabled && { color: colors.onSurfaceVariant },
        ]}
      >
        {label}
      </Text>
    </Pressable>
  );
}

export function PrimaryButton({
  label,
  onPress,
  tone = 'primary',
}: {
  label: string;
  onPress: () => void;
  tone?: 'primary' | 'accent' | 'ghost';
}) {
  const variant = tone === 'ghost' ? 'tonal' : 'filled';
  return <Button label={label} onPress={onPress} variant={variant} />;
}

export function Chip({
  label,
  tone = 'neutral',
}: {
  label: string;
  tone?: 'neutral' | 'success' | 'warning' | 'error' | 'info';
}) {
  const map = {
    neutral: { bg: colors.surfaceHigh, fg: colors.onSurfaceVariant },
    success: { bg: colors.successContainer, fg: colors.success },
    warning: { bg: colors.warningContainer, fg: colors.warning },
    error: { bg: colors.errorContainer, fg: colors.error },
    info: { bg: colors.primaryContainer, fg: colors.onPrimaryContainer },
  }[tone];
  return (
    <View style={[styles.chip, { backgroundColor: map.bg }]}>
      <Text style={[styles.chipText, { color: map.fg }]}>{label}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: {
    paddingHorizontal: space.lg,
    paddingBottom: space.xxl,
  },
  largeHeader: {
    marginBottom: space.lg,
  },
  headline: {
    ...type.headline,
    color: colors.onSurface,
  },
  subtitle: {
    ...type.body,
    color: colors.onSurfaceVariant,
    marginTop: space.sm,
  },
  pageSubtitle: {
    ...type.body,
    color: colors.onSurfaceVariant,
    marginBottom: space.lg,
    marginTop: -space.xs,
  },
  topBar: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: space.md,
    minHeight: 48,
  },
  topBarTitle: {
    ...type.titleSm,
    color: colors.onSurface,
    flex: 1,
    textAlign: 'center',
  },
  iconBtn: {
    width: 40,
    height: 40,
    borderRadius: radius.full,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: colors.surfaceHigh,
  },
  iconBtnPlaceholder: {
    width: 40,
    height: 40,
  },
  iconBtnText: {
    fontSize: 28,
    lineHeight: 32,
    color: colors.onSurface,
    marginTop: -2,
  },
  section: {
    marginBottom: space.lg,
  },
  sectionTitle: {
    ...type.label,
    color: colors.onSurfaceVariant,
    marginBottom: space.sm,
    marginLeft: space.sm,
  },
  sectionBody: {
    backgroundColor: colors.surfaceLow,
    borderRadius: radius.lg,
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: colors.outlineVariant,
    overflow: 'hidden',
  },
  panelPad: {
    padding: space.lg,
  },
  listRow: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: space.md,
    paddingHorizontal: space.lg,
    minHeight: 64,
    gap: space.md,
  },
  listRowBorder: {
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: colors.outlineVariant,
  },
  leading: {},
  listText: {
    flex: 1,
    gap: 2,
  },
  listTitle: {
    ...type.titleSm,
    color: colors.onSurface,
  },
  listSubtitle: {
    ...type.body,
    color: colors.onSurfaceVariant,
  },
  chevron: {
    fontSize: 22,
    color: colors.onSurfaceVariant,
    fontWeight: '300',
  },
  avatar: {
    width: 40,
    height: 40,
    borderRadius: radius.md,
    alignItems: 'center',
    justifyContent: 'center',
  },
  avatarText: {
    fontSize: 15,
    fontWeight: '700',
  },
  btnBase: {
    height: 48,
    borderRadius: radius.full,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: space.xl,
    marginTop: space.sm,
  },
  btnFilled: {
    backgroundColor: colors.primary,
  },
  btnTonal: {
    backgroundColor: colors.primaryContainer,
  },
  btnOutlined: {
    backgroundColor: 'transparent',
    borderWidth: 1,
    borderColor: colors.outline,
  },
  btnTextOnly: {
    backgroundColor: 'transparent',
  },
  btnLabel: {
    ...type.labelLg,
  },
  btnDisabled: {
    opacity: 0.48,
  },
  pressed: {
    opacity: 0.88,
  },
  chip: {
    paddingHorizontal: space.sm,
    paddingVertical: space.xs,
    borderRadius: radius.sm,
    alignSelf: 'flex-start',
  },
  chipText: {
    ...type.label,
    fontWeight: '600',
  },
});
