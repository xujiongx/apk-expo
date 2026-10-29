import * as Application from 'expo-application';
import * as Battery from 'expo-battery';
import * as Brightness from 'expo-brightness';
import * as Clipboard from 'expo-clipboard';
import * as Device from 'expo-device';
import * as Haptics from 'expo-haptics';
import * as Network from 'expo-network';
import { Accelerometer } from 'expo-sensors';
import * as Sharing from 'expo-sharing';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useState } from 'react';
import {
  Alert,
  Linking,
  Platform,
  Pressable,
  SafeAreaView,
  ScrollView,
  Share,
  StyleSheet,
  Text,
  View,
} from 'react-native';

type Motion = { x: number; y: number; z: number };

function round(n: number, digits = 2) {
  const p = 10 ** digits;
  return Math.round(n * p) / p;
}

export default function App() {
  const [battery, setBattery] = useState<number | null>(null);
  const [charging, setCharging] = useState(false);
  const [network, setNetwork] = useState('检测中…');
  const [brightness, setBrightness] = useState<number | null>(null);
  const [motion, setMotion] = useState<Motion>({ x: 0, y: 0, z: 0 });
  const [sensing, setSensing] = useState(false);
  const [clipboardHint, setClipboardHint] = useState('点按钮复制设备摘要');
  const [toast, setToast] = useState('');

  const flash = (msg: string) => {
    setToast(msg);
    setTimeout(() => setToast(''), 1800);
  };

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

      // Web 上的 expo-battery 没有 EventEmitter，直接 addListener 会白屏
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
        // 当前平台不支持电量监听
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
          setToast('当前环境不支持加速度计');
          setSensing(false);
          return;
        }
        Accelerometer.setUpdateInterval(200);
        sub = Accelerometer.addListener((data) => setMotion(data));
      } catch {
        if (!active) return;
        setToast('加速度计启动失败');
        setSensing(false);
      }
    })();

    return () => {
      active = false;
      sub?.remove();
    };
  }, [sensing]);

  const deviceSummary = [
    `平台: ${Platform.OS} ${Platform.Version}`,
    `品牌: ${Device.brand ?? '-'}`,
    `型号: ${Device.modelName ?? '-'}`,
    `系统: ${Device.osName ?? '-'} ${Device.osVersion ?? '-'}`,
    `应用: ${Application.applicationName ?? 'APK Demo'}`,
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
      flash('已触发震动反馈');
    } catch {
      flash('当前环境不支持震动');
    }
  };

  const copySummary = async () => {
    await Clipboard.setStringAsync(deviceSummary);
    setClipboardHint('已复制到剪贴板');
    flash('设备信息已复制');
    await vibrate('light');
  };

  const readClipboard = async () => {
    const text = await Clipboard.getStringAsync();
    Alert.alert('剪贴板内容', text || '(空)');
  };

  const shareSummary = async () => {
    try {
      await Share.share({
        message: deviceSummary,
        title: 'APK Demo 设备摘要',
      });
    } catch {
      flash('分享取消或失败');
    }
  };

  const shareViaExpo = async () => {
    const available = await Sharing.isAvailableAsync();
    if (!available) {
      flash('当前平台不支持系统分享');
      return;
    }
    // Sharing 更适合文件；文本场景用 RN Share 更稳妥
    await shareSummary();
  };

  const changeBrightness = async (delta: number) => {
    try {
      const current = brightness ?? (await Brightness.getBrightnessAsync());
      const next = Math.min(1, Math.max(0.05, round(current + delta)));
      await Brightness.setBrightnessAsync(next);
      setBrightness(next);
      flash(`亮度 ${(next * 100).toFixed(0)}%`);
    } catch {
      flash('亮度调节需要真机权限');
    }
  };

  const openWifiSettings = async () => {
    try {
      if (Platform.OS === 'android' && 'sendIntent' in Linking) {
        await Linking.sendIntent('android.settings.WIFI_SETTINGS');
        return;
      }
      await Linking.openSettings();
    } catch {
      flash('无法打开系统设置');
    }
  };

  const openAppSettings = () => Linking.openSettings();

  return (
    <SafeAreaView style={styles.safe}>
      <StatusBar style="dark" />
      <ScrollView contentContainerStyle={styles.screen}>
        <Text style={styles.brand}>APK Demo</Text>
        <Text style={styles.headline}>安卓能力演示</Text>
        <Text style={styles.subtitle}>
          震动、电量、网络、传感器、亮度、剪贴板、系统设置 —— 请用手机 Expo Go
          体验（Web 上部分能力会降级）
        </Text>

        {toast ? (
          <View style={styles.toast}>
            <Text style={styles.toastText}>{toast}</Text>
          </View>
        ) : null}

        <View style={styles.panel}>
          <Text style={styles.panelLabel}>设备信息</Text>
          <InfoRow label="平台" value={`${Platform.OS} ${String(Platform.Version)}`} />
          <InfoRow label="品牌 / 型号" value={`${Device.brand ?? '-'} · ${Device.modelName ?? '-'}`} />
          <InfoRow
            label="系统"
            value={`${Device.osName ?? '-'} ${Device.osVersion ?? '-'}`}
          />
          <InfoRow
            label="设备类型"
            value={Device.deviceType != null ? Device.DeviceType[Device.deviceType] : '-'}
          />
          <InfoRow label="是否真机" value={Device.isDevice ? '是' : '模拟器 / 浏览器'} />
          <InfoRow label="应用包名" value={Application.applicationId ?? '-'} />
        </View>

        <View style={styles.panel}>
          <Text style={styles.panelLabel}>电量 & 网络</Text>
          <InfoRow
            label="电量"
            value={battery == null ? '不可用' : `${battery}%${charging ? ' · 充电中' : ''}`}
          />
          <InfoRow label="网络" value={String(network)} />
        </View>

        <View style={styles.panel}>
          <Text style={styles.panelLabel}>震动反馈（Haptics）</Text>
          <View style={styles.row}>
            <ActionButton label="轻" onPress={() => vibrate('light')} />
            <ActionButton label="中" onPress={() => vibrate('medium')} />
            <ActionButton label="重" onPress={() => vibrate('heavy')} />
          </View>
          <Pressable
            style={({ pressed }) => [
              styles.button,
              styles.buttonPrimary,
              styles.fullButton,
              pressed && styles.buttonPressed,
            ]}
            onPress={() => vibrate('success')}
          >
            <Text style={styles.buttonPrimaryText}>成功反馈</Text>
          </Pressable>
        </View>

        <View style={styles.panel}>
          <Text style={styles.panelLabel}>加速度传感器</Text>
          <Text style={styles.mono}>
            x {round(motion.x, 3)} · y {round(motion.y, 3)} · z {round(motion.z, 3)}
          </Text>
          <Pressable
            style={({ pressed }) => [
              styles.button,
              sensing ? styles.buttonGhost : styles.buttonPrimary,
              styles.fullButton,
              pressed && styles.buttonPressed,
            ]}
            onPress={() => setSensing((v) => !v)}
          >
            <Text style={sensing ? styles.buttonGhostText : styles.buttonPrimaryText}>
              {sensing ? '停止监听' : '开始监听倾角 / 晃动'}
            </Text>
          </Pressable>
        </View>

        <View style={styles.panel}>
          <Text style={styles.panelLabel}>屏幕亮度</Text>
          <InfoRow
            label="当前"
            value={brightness == null ? '不可用' : `${(brightness * 100).toFixed(0)}%`}
          />
          <View style={styles.row}>
            <ActionButton label="变暗" onPress={() => changeBrightness(-0.1)} ghost />
            <ActionButton label="变亮" onPress={() => changeBrightness(0.1)} />
          </View>
        </View>

        <View style={styles.panel}>
          <Text style={styles.panelLabel}>剪贴板 & 分享</Text>
          <Text style={styles.hint}>{clipboardHint}</Text>
          <View style={styles.row}>
            <ActionButton label="复制摘要" onPress={copySummary} />
            <ActionButton label="读取剪贴板" onPress={readClipboard} ghost />
          </View>
          <Pressable
            style={({ pressed }) => [
              styles.button,
              styles.buttonPrimary,
              styles.fullButton,
              pressed && styles.buttonPressed,
            ]}
            onPress={shareViaExpo}
          >
            <Text style={styles.buttonPrimaryText}>系统分享面板</Text>
          </Pressable>
        </View>

        <View style={[styles.panel, styles.lastPanel]}>
          <Text style={styles.panelLabel}>系统能力跳转</Text>
          <View style={styles.row}>
            <ActionButton label="Wi‑Fi 设置" onPress={openWifiSettings} />
            <ActionButton label="应用设置" onPress={openAppSettings} ghost />
          </View>
          <Text style={styles.hint}>
            安卓上会通过 Intent 打开系统设置；真机效果最完整。
          </Text>
        </View>
      </ScrollView>
    </SafeAreaView>
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

function ActionButton({
  label,
  onPress,
  ghost = false,
}: {
  label: string;
  onPress: () => void;
  ghost?: boolean;
}) {
  return (
    <Pressable
      style={({ pressed }) => [
        styles.button,
        ghost ? styles.buttonGhost : styles.buttonPrimary,
        styles.flexButton,
        pressed && styles.buttonPressed,
      ]}
      onPress={onPress}
    >
      <Text style={ghost ? styles.buttonGhostText : styles.buttonPrimaryText}>
        {label}
      </Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  safe: {
    flex: 1,
    backgroundColor: '#E8F1F0',
  },
  screen: {
    paddingHorizontal: 24,
    paddingTop: 28,
    paddingBottom: 40,
  },
  brand: {
    fontSize: 13,
    letterSpacing: 2,
    textTransform: 'uppercase',
    color: '#4F6F6C',
    fontWeight: '700',
    marginBottom: 8,
  },
  headline: {
    fontSize: 32,
    lineHeight: 38,
    fontWeight: '800',
    color: '#122A28',
  },
  subtitle: {
    marginTop: 8,
    marginBottom: 20,
    fontSize: 15,
    lineHeight: 22,
    color: '#4F6F6C',
  },
  toast: {
    backgroundColor: '#0F766E',
    borderRadius: 12,
    paddingVertical: 10,
    paddingHorizontal: 14,
    marginBottom: 14,
  },
  toastText: {
    color: '#FFFFFF',
    fontWeight: '700',
    textAlign: 'center',
  },
  panel: {
    backgroundColor: '#FFFFFF',
    borderRadius: 20,
    padding: 20,
    marginBottom: 16,
    borderWidth: 1,
    borderColor: '#D5E4E2',
  },
  lastPanel: {
    marginBottom: 8,
  },
  panelLabel: {
    fontSize: 13,
    fontWeight: '700',
    color: '#6A8582',
    marginBottom: 12,
  },
  infoRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    gap: 12,
    paddingVertical: 8,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: '#D5E4E2',
  },
  infoLabel: {
    color: '#6A8582',
    fontSize: 14,
  },
  infoValue: {
    flex: 1,
    textAlign: 'right',
    color: '#122A28',
    fontSize: 14,
    fontWeight: '600',
  },
  mono: {
    fontFamily: Platform.select({ ios: 'Menlo', android: 'monospace', default: 'monospace' }),
    fontSize: 14,
    color: '#122A28',
    marginBottom: 14,
  },
  hint: {
    fontSize: 13,
    color: '#6A8582',
    marginBottom: 12,
    lineHeight: 18,
  },
  row: {
    flexDirection: 'row',
    gap: 10,
    marginBottom: 10,
  },
  button: {
    height: 48,
    borderRadius: 14,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 12,
  },
  flexButton: {
    flex: 1,
  },
  fullButton: {
    width: '100%',
    marginTop: 2,
  },
  buttonPrimary: {
    backgroundColor: '#0F766E',
  },
  buttonGhost: {
    backgroundColor: '#DCEAE8',
  },
  buttonPressed: {
    opacity: 0.85,
    transform: [{ scale: 0.98 }],
  },
  buttonPrimaryText: {
    color: '#FFFFFF',
    fontSize: 15,
    fontWeight: '700',
  },
  buttonGhostText: {
    color: '#122A28',
    fontSize: 15,
    fontWeight: '700',
  },
});
