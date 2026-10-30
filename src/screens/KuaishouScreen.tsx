import * as IntentLauncher from 'expo-intent-launcher';
import { useEffect, useMemo, useState } from 'react';
import {
  AppState,
  Linking,
  Platform,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import {
  addProgressListener,
  isAccessibilityEnabled,
  isNativeAutoAssistAvailable,
  openAccessibilitySettings,
  startKuaishouCheckIn,
  stopAutoAssist,
} from 'auto-assist';
import { colors, radius, space, type } from '../theme';
import { useToast } from '../toast/ToastContext';
import { Button, Chip, Panel, Screen } from '../ui';

const KUAISHOU_PACKAGE = 'com.kuaishou.nebula';

type StepStatus = 'idle' | 'running' | 'done' | 'skip' | 'fail';

type Step = {
  id: string;
  title: string;
  detail: string;
  status: StepStatus;
};

const INITIAL_STEPS: Omit<Step, 'status'>[] = [
  { id: 'service', title: '开启无障碍服务', detail: '系统设置中打开「工具箱自动签到」' },
  { id: 'open', title: '打开快手极速版', detail: `包名 ${KUAISHOU_PACKAGE}` },
  { id: 'ads', title: '跳过首屏广告', detail: '自动点击跳过 / 关闭' },
  { id: 'earn', title: '进入「去赚钱」', detail: '定位底部赚钱入口' },
  { id: 'sign', title: '点击签到', detail: '查找并点击签到按钮' },
  { id: 'done', title: '完成', detail: '流程结束' },
];

export function KuaishouScreen({ onBack }: { onBack: () => void }) {
  const toast = useToast();
  const nativeReady = isNativeAutoAssistAvailable();
  const [enabled, setEnabled] = useState(false);
  const [steps, setSteps] = useState<Step[]>(
    INITIAL_STEPS.map((s) => ({ ...s, status: 'idle' })),
  );
  const [running, setRunning] = useState(false);
  const [log, setLog] = useState<string[]>([]);

  const progressText = useMemo(() => {
    const done = steps.filter((s) => s.status === 'done' || s.status === 'skip').length;
    return `${done}/${steps.length}`;
  }, [steps]);

  const refreshEnabled = async () => {
    if (!nativeReady) {
      setEnabled(false);
      return;
    }
    try {
      setEnabled(await isAccessibilityEnabled());
    } catch {
      setEnabled(false);
    }
  };

  useEffect(() => {
    refreshEnabled();
    const sub = AppState.addEventListener('change', (state) => {
      if (state === 'active') refreshEnabled();
    });
    return () => sub.remove();
  }, [nativeReady]);

  useEffect(() => {
    const sub = addProgressListener((event) => {
      pushLog(`${event.ok ? '✓' : '✗'} ${event.message}`);
      const map: Record<string, string> = {
        service: 'service',
        open: 'open',
        ads: 'ads',
        earn: 'earn',
        sign: 'sign',
        done: 'done',
      };
      const id = map[event.step];
      if (id) {
        setStatus(id, event.ok ? 'done' : event.step === 'done' ? 'done' : 'fail');
        if (event.ok && id !== 'done') {
          // mark previous implied
        }
      }
      if (event.step === 'done') {
        setRunning(false);
        if (event.ok) toast.success(event.message, '签到完成');
        else toast.warning(event.message, '流程结束');
      }
      if (event.step === 'open' && !event.ok) {
        setRunning(false);
        toast.error(event.message, '启动失败');
      }
    });
    return () => sub.remove();
  }, [toast]);

  const pushLog = (line: string) => {
    setLog((prev) => [`${new Date().toLocaleTimeString()}  ${line}`, ...prev].slice(0, 30));
  };

  const setStatus = (id: string, status: StepStatus) => {
    setSteps((prev) => prev.map((s) => (s.id === id ? { ...s, status } : s)));
  };

  const runAutoCheckIn = async () => {
    if (Platform.OS !== 'android') {
      toast.warning('请在安卓真机上使用', '仅支持安卓');
      return;
    }
    if (!nativeReady) {
      toast.error(
        'Expo Go / Web 无法注入点击。请执行 pnpm build:apk 安装自定义包后再用。',
        '需要自定义 APK',
      );
      return;
    }

    const on = await isAccessibilityEnabled();
    setEnabled(on);
    if (!on) {
      setStatus('service', 'fail');
      toast.warning('请开启「工具箱自动签到」无障碍服务后返回重试', '需要权限');
      try {
        await openAccessibilitySettings();
      } catch {
        // ignore
      }
      return;
    }

    setStatus('service', 'done');
    setRunning(true);
    setSteps(
      INITIAL_STEPS.map((s) => ({
        ...s,
        status: s.id === 'service' ? 'done' : 'idle',
      })),
    );
    setLog([]);
    pushLog('无障碍已开启，开始自动签到');
    setStatus('open', 'running');

    try {
      await startKuaishouCheckIn(KUAISHOU_PACKAGE);
      toast.info('已启动自动化，请勿手动乱点', '执行中');
    } catch (e) {
      setRunning(false);
      toast.error(e instanceof Error ? e.message : '启动失败');
    }
  };

  const openKuaishouOnly = () => {
    try {
      if (Platform.OS !== 'android') {
        toast.warning('仅安卓可用');
        return;
      }
      IntentLauncher.openApplication(KUAISHOU_PACKAGE);
      toast.success('已打开快手极速版');
    } catch (e) {
      toast.error(e instanceof Error ? e.message : '无法打开');
      Linking.openURL(`market://details?id=${KUAISHOU_PACKAGE}`).catch(() => {});
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
        title="快手极速版签到"
        subtitle="通过系统无障碍服务自动：开 App → 跳广告 → 去赚钱 → 签到。"
      >
        <Panel label="运行环境">
          <Text style={styles.body}>
            {nativeReady
              ? enabled
                ? '无障碍服务已开启，可以自动点击。'
                : '自定义 APK 已就绪，但仍需在系统设置中开启无障碍。'
              : '当前是 Expo Go / Web：只能打开应用，不能自动点击。请用 EAS 构建 APK。'}
          </Text>
          <View style={styles.metaRow}>
            <Chip
              label={nativeReady ? '原生模块就绪' : '原生模块不可用'}
              tone={nativeReady ? 'success' : 'warning'}
            />
            <Chip
              label={enabled ? '无障碍已开' : '无障碍未开'}
              tone={enabled ? 'success' : 'neutral'}
            />
          </View>
        </Panel>

        <View style={styles.actions}>
          <Button
            label={running ? '执行中…' : '自动签到'}
            variant="filled"
            disabled={running}
            onPress={runAutoCheckIn}
          />
          <Button
            label="打开无障碍设置"
            variant="tonal"
            onPress={async () => {
              try {
                await openAccessibilitySettings();
              } catch (e) {
                toast.error(e instanceof Error ? e.message : '无法打开设置');
              }
            }}
          />
          <Button label="仅打开快手极速版" variant="outlined" onPress={openKuaishouOnly} />
          {running ? (
            <Button
              label="停止任务"
              variant="text"
              onPress={async () => {
                await stopAutoAssist();
                setRunning(false);
                toast.info('已停止');
              }}
            />
          ) : null}
        </View>

        <Panel label={`流程进度 ${progressText}`}>
          {steps.map((step, index) => (
            <View
              key={step.id}
              style={[
                styles.stepRow,
                index === steps.length - 1 && { borderBottomWidth: 0 },
              ]}
            >
              <View style={[styles.dot, statusStyle(step.status)]} />
              <View style={{ flex: 1 }}>
                <Text style={styles.stepTitle}>{step.title}</Text>
                <Text style={styles.stepDetail}>{step.detail}</Text>
              </View>
              <Chip label={statusLabel(step.status)} tone={chipTone(step.status)} />
            </View>
          ))}
        </Panel>

        <Panel label="运行日志" last>
          {log.length === 0 ? (
            <Text style={styles.stepDetail}>还没有日志。点「自动签到」开始。</Text>
          ) : (
            log.map((line) => (
              <Text key={line} style={styles.logLine}>
                {line}
              </Text>
            ))
          )}
        </Panel>
      </Screen>
    </ScrollView>
  );
}

function statusLabel(status: StepStatus) {
  switch (status) {
    case 'running':
      return '进行中';
    case 'done':
      return '完成';
    case 'skip':
      return '跳过';
    case 'fail':
      return '失败';
    default:
      return '等待';
  }
}

function statusStyle(status: StepStatus) {
  switch (status) {
    case 'running':
      return { backgroundColor: colors.primary };
    case 'done':
      return { backgroundColor: colors.success };
    case 'skip':
      return { backgroundColor: colors.warning };
    case 'fail':
      return { backgroundColor: colors.error };
    default:
      return { backgroundColor: colors.outline };
  }
}

function chipTone(
  status: StepStatus,
): 'neutral' | 'success' | 'warning' | 'error' | 'info' {
  switch (status) {
    case 'running':
      return 'info';
    case 'done':
      return 'success';
    case 'skip':
      return 'warning';
    case 'fail':
      return 'error';
    default:
      return 'neutral';
  }
}

const styles = StyleSheet.create({
  actions: {
    marginBottom: space.lg,
  },
  body: {
    ...type.body,
    color: colors.onSurfaceVariant,
    marginBottom: space.md,
  },
  metaRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: space.sm,
  },
  stepRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: space.md,
    paddingVertical: space.md,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: colors.outlineVariant,
  },
  dot: {
    width: 8,
    height: 8,
    borderRadius: radius.full,
  },
  stepTitle: {
    ...type.bodyStrong,
    color: colors.onSurface,
  },
  stepDetail: {
    ...type.label,
    color: colors.onSurfaceVariant,
    marginTop: 2,
  },
  logLine: {
    ...type.label,
    color: colors.onSurface,
    marginBottom: space.sm,
    fontFamily: Platform.select({
      ios: 'Menlo',
      android: 'monospace',
      default: 'monospace',
    }),
  },
});
