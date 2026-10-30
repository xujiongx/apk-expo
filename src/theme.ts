/**
 * Material Design 3 风格 token（工具类 App）
 * 8pt 网格 · 语义色 · 统一圆角/字号
 */
export const colors = {
  // Surface
  surface: '#F8FAFC',
  surfaceLow: '#FFFFFF',
  surfaceHigh: '#EEF2F6',
  // Content
  onSurface: '#0F172A',
  onSurfaceVariant: '#64748B',
  // Brand
  primary: '#0B57D0',
  onPrimary: '#FFFFFF',
  primaryContainer: '#D3E3FD',
  onPrimaryContainer: '#041E49',
  // Secondary / status
  secondaryContainer: '#E8DEF8',
  tertiary: '#006A6A',
  tertiaryContainer: '#6FF7F7',
  // Semantic
  success: '#146C2E',
  successContainer: '#C4EED0',
  warning: '#B54708',
  warningContainer: '#FFECB3',
  error: '#B3261E',
  errorContainer: '#F9DEDC',
  // Chrome
  outline: '#CBD5E1',
  outlineVariant: '#E2E8F0',
  scrim: 'rgba(15, 23, 42, 0.4)',
  // legacy aliases used by screens
  bg: '#F8FAFC',
  ink: '#0F172A',
  muted: '#64748B',
  panel: '#FFFFFF',
  line: '#E2E8F0',
  primarySoft: '#D3E3FD',
  accent: '#0B57D0',
  accentSoft: '#D3E3FD',
  ok: '#146C2E',
  okSoft: '#C4EED0',
  danger: '#B3261E',
};

export const space = {
  xs: 4,
  sm: 8,
  md: 12,
  lg: 16,
  xl: 24,
  xxl: 32,
} as const;

export const radius = {
  sm: 8,
  md: 12,
  lg: 16,
  xl: 28,
  full: 999,
} as const;

export const type = {
  headline: { fontSize: 28, lineHeight: 34, fontWeight: '700' as const },
  title: { fontSize: 22, lineHeight: 28, fontWeight: '600' as const },
  titleSm: { fontSize: 16, lineHeight: 24, fontWeight: '600' as const },
  body: { fontSize: 14, lineHeight: 20, fontWeight: '400' as const },
  bodyStrong: { fontSize: 14, lineHeight: 20, fontWeight: '600' as const },
  label: { fontSize: 12, lineHeight: 16, fontWeight: '500' as const },
  labelLg: { fontSize: 14, lineHeight: 20, fontWeight: '600' as const },
};
