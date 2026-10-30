import * as Application from 'expo-application';
import * as Battery from 'expo-battery';
import * as Brightness from 'expo-brightness';
import * as Clipboard from 'expo-clipboard';
import * as Device from 'expo-device';
import * as Haptics from 'expo-haptics';
import * as Network from 'expo-network';
import { Accelerometer } from 'expo-sensors';
import * as Sharing from 'expo-sharing';
import { useEffect, useState } from 'react';
import {
  Linking,
  Platform,
  Pressable,
  ScrollView,
  Share,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import { colors, radius, space, type } from '../theme';
import { useToast } from '../toast/ToastContext';
import { Button, Panel, Screen } from '../ui';

type Motion = { x: number; y: number; z: number };

function round(n: number, digits = 2) {
  const p = 10 ** digits;
  return Math.round(n * p) / p;
}

export function DeviceLabScreen({ onBack }: { onBack: () => void }) {
  const toast = useToast();
  const [battery, setBattery] = useState<number | null>(null);
  const [charging, setCharging] = useState(false);
  const [network, setNetwork] = useState('检测中…');
  const [brightness, setBrightness] = useState<number | null>(null);
  const [motion, setMotion] = useState<Motion>({ x: 0, y: 0, z: 0 });
  const [sensing, setSensing] = useState(false);
  const [clipboardHint, setClipboardHint] = useState('点按钮复制设备摘要');

  useEffect(() => {
    let mounted = true;
    const subs: { remove: () => void }[] = [];

    (async () => {
      try {
        const available = await Battery.isAvailableAsync();
        if (available) {
          const level = await Battery.getBatteryLevelAsync();
          const state = await Battery.getBatteryStateAsync();
          if (!mounted) return;
          setBattery(level < 0 ? null : Math.round(level * 100));
          setCharging(state === Battery.BatteryState.CHARGING);
        }
      } catch {
        if (mounted) setBattery(null);
      }

      try {
        const state = await Network.getNetworkStateAsync();
        if (!mounted) return;
        if (!state.isConnected) setNetwork('离线');
        else setNetwork(state.type ?? '已连接');
      } catch {
        if (mounted) setNetwork('未知');
      }

      try {
        const value = await Brightness.getBrightnessAsync();
        if (mounted) setBrightness(round(value));
      } catch {
        if (mounted) setBrightness(null);
      }

      if (!mounted || Platform.OS === 'web') return;

      try {
        subs.push(
          Battery.addBatteryLevelListener(({ batteryLevel }) => {
            setBattery(Math.round(batteryLevel * 100));
          }),
        );
        subs.push(
          Battery.addBatteryStateListener(({ batteryState }) => {
            setCharging(batteryState === Battery.BatteryState.CHARGING);
          }),
        );
      } catch {
        // ignore
      }
    })();

    return () => {
      mounted = false;
      subs.forEach((sub) => sub.remove());
    };
  }, []);

  useEffect(() => {
    if (!sensing) return;
    let active = true;
    let sub: { remove: () => void } | undefined;

    (async () => {
      try {
        const available = await Accelerometer.isAvailableAsync();
        if (!active) return;
        if (!available) {
          toast.warning('当前环境不支持加速度计');
          setSensing(false);
          return;
        }
        Accelerometer.setUpdateInterval(200);
        sub = Accelerometer.addListener((data) => setMotion(data));
      } catch {
        if (!active) return;
        toast.error('加速度计启动失败');
        setSensing(false);
      }
    })();

    return () => {
      active = false;
      sub?.remove();
    };
  }, [sensing, toast]);

  const deviceSummary = [
    `平台: ${Platform.OS} ${Platform.Version}`,
    `品牌: ${Device.brand ?? '-'}`,
    `型号: ${Device.modelName ?? '-'}`,
    `系统: ${Device.osName ?? '-'} ${Device.osVersion ?? '-'}`,
    `应用: ${Application.applicationName ?? '工具箱'}`,
    `包名: ${Application.applicationId ?? 'com.example.apkdemo'}`,
  ].join('\n');

  const vibrate = async (style: 'light' | 'medium' | 'heavy' | 'success') => {
    try {
      if (style === 'success') {
        await Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success);
      } else {
        const map = {
          light: Haptics.ImpactFeedbackStyle.Light,
          medium: Haptics.ImpactFeedbackStyle.Medium,
          heavy: Haptics.ImpactFeedbackStyle.Heavy,
        } as const;
        await Haptics.impactAsync(map[style]);
      }
      toast.success('已触发震动反馈');
    } catch {
      toast.warning('当前环境不支持震动');
    }
  };

  const copySummary = async () => {
    await Clipboard.setStringAsync(deviceSummary);
    setClipboardHint('已复制到剪贴板');
    toast.success('设备信息已复制');
    try {
      await Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
    } catch {
      // ignore
    }
  };

  const changeBrightness = async (delta: number) => {
    try {
      const current = brightness ?? (await Brightness.getBrightnessAsync());
      const next = Math.min(1, Math.max(0.05, round(current + delta)));
      await Brightness.setBrightnessAsync(next);
      setBrightness(next);
      toast.info(`亮度已设为 ${(next * 100).toFixed(0)}%`);
    } catch {
      toast.warning('亮度调节需要真机权限');
    }
  };

  return (
    <ScrollView
      style={{ flex: 1, backgroundColor: colors.surface }}
      contentContainerStyle={{ paddingBottom: space.xxl }}
      showsVerticalScrollIndicator={false}
    >
      <Screen
        onBack={onBack}
        title="系统能力实验室"
        subtitle="震动、传感器、亮度等系统能力演示。"
      >
        <Panel label="设备信息">
          <InfoRow label="平台" value={`${Platform.OS} ${String(Platform.Version)}`} />
          <InfoRow
            label="品牌 / 型号"
            value={`${Device.brand ?? '-'} · ${Device.modelName ?? '-'}`}
          />
          <InfoRow
            label="系统"
            value={`${Device.osName ?? '-'} ${Device.osVersion ?? '-'}`}
          />
          <InfoRow
            label="是否真机"
            value={Device.isDevice ? '是' : '模拟器 / 浏览器'}
          />
        </Panel>

        <Panel label="电量 & 网络">
          <InfoRow
            label="电量"
            value={
              battery == null ? '不可用' : `${battery}%${charging ? ' · 充电中' : ''}`
            }
          />
          <InfoRow label="网络" value={String(network)} />
        </Panel>

        <Panel label="震动反馈">
          <View style={styles.row}>
            <MiniButton label="轻" onPress={() => vibrate('light')} />
            <MiniButton label="中" onPress={() => vibrate('medium')} />
            <MiniButton label="重" onPress={() => vibrate('heavy')} />
          </View>
          <Button label="成功反馈" onPress={() => vibrate('success')} variant="tonal" />
        </Panel>

        <Panel label="加速度传感器">
          <Text style={styles.mono}>
            x {round(motion.x, 3)} · y {round(motion.y, 3)} · z {round(motion.z, 3)}
          </Text>
          <Button
            label={sensing ? '停止监听' : '开始监听'}
            onPress={() => setSensing((v) => !v)}
            variant={sensing ? 'outlined' : 'filled'}
          />
        </Panel>

        <Panel label="屏幕亮度">
          <InfoRow
            label="当前"
            value={
              brightness == null ? '不可用' : `${(brightness * 100).toFixed(0)}%`
            }
          />
          <View style={styles.row}>
            <MiniButton label="变暗" onPress={() => changeBrightness(-0.1)} ghost />
            <MiniButton label="变亮" onPress={() => changeBrightness(0.1)} />
          </View>
        </Panel>

        <Panel label="剪贴板与分享" last>
          <Text style={styles.hint}>{clipboardHint}</Text>
          <View style={styles.row}>
            <MiniButton label="复制摘要" onPress={copySummary} />
            <MiniButton
              label="读取"
              onPress={async () => {
                const text = await Clipboard.getStringAsync();
                toast.info(text.trim() ? text.slice(0, 120) : '剪贴板为空', '剪贴板');
              }}
              ghost
            />
          </View>
          <Button
            label="系统分享"
            onPress={async () => {
              if (!(await Sharing.isAvailableAsync())) {
                toast.warning('当前环境不支持分享');
                return;
              }
              try {
                await Share.share({ message: deviceSummary });
                toast.success('已打开系统分享');
              } catch {
                toast.info('已取消分享');
              }
            }}
            variant="tonal"
          />
          <View style={[styles.row, { marginTop: space.sm }]}>
            <MiniButton
              label="Wi‑Fi 设置"
              onPress={async () => {
                try {
                  if (Platform.OS === 'android' && 'sendIntent' in Linking) {
                    await Linking.sendIntent('android.settings.WIFI_SETTINGS');
                  } else {
                    await Linking.openSettings();
                  }
                  toast.info('已打开系统设置');
                } catch {
                  toast.error('无法打开设置');
                }
              }}
            />
            <MiniButton
              label="应用设置"
              onPress={() => {
                Linking.openSettings();
                toast.info('已打开应用设置');
              }}
              ghost
            />
          </View>
        </Panel>
      </Screen>
    </ScrollView>
  );
}

function InfoRow({ label, value }: { label: string; value: string }) {
  return (
    <View style={styles.infoRow}>
      <Text style={styles.infoLabel}>{label}</Text>
      <Text style={styles.infoValue}>{value}</Text>
    </View>
  );
}

function MiniButton({
  label,
  onPress,
  ghost,
  full,
}: {
  label: string;
  onPress: () => void;
  ghost?: boolean;
  full?: boolean;
}) {
  return (
    <Pressable
      onPress={onPress}
      style={({ pressed }) => [
        styles.miniBtn,
        ghost ? styles.miniGhost : styles.miniPrimary,
        full && { flex: undefined, width: '100%', marginTop: 8 },
        !full && { flex: 1 },
        pressed && { opacity: 0.85 },
      ]}
    >
      <Text style={ghost ? styles.miniGhostText : styles.miniPrimaryText}>{label}</Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  infoRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    gap: space.md,
    paddingVertical: space.sm,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: colors.outlineVariant,
  },
  infoLabel: { color: colors.onSurfaceVariant, ...type.body },
  infoValue: {
    flex: 1,
    textAlign: 'right',
    color: colors.onSurface,
    ...type.bodyStrong,
  },
  mono: {
    fontFamily: Platform.select({
      ios: 'Menlo',
      android: 'monospace',
      default: 'monospace',
    }),
    fontSize: 13,
    color: colors.onSurface,
    marginBottom: space.sm,
  },
  hint: {
    ...type.body,
    color: colors.onSurfaceVariant,
    marginBottom: space.md,
  },
  row: { flexDirection: 'row', gap: space.sm, marginBottom: space.xs },
  miniBtn: {
    height: 40,
    borderRadius: radius.full,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: space.md,
  },
  miniPrimary: { backgroundColor: colors.primary },
  miniGhost: { backgroundColor: colors.primaryContainer },
  miniPrimaryText: { color: colors.onPrimary, ...type.labelLg },
  miniGhostText: { color: colors.onPrimaryContainer, ...type.labelLg },
});
