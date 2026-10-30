import { useCallback, useEffect, useState } from 'react';
import { AppState, Platform, ScrollView, StyleSheet, Text, View } from 'react-native';
import {
  canDrawOverlays,
  hideClickerOverlay,
  isAccessibilityEnabled,
  isClickerOverlayShowing,
  isNativeAutoAssistAvailable,
  openAccessibilitySettings,
  openOverlaySettings,
  showClickerOverlay,
} from 'auto-assist';
import { colors, space, type } from '../theme';
import { useToast } from '../toast/ToastContext';
import { Button, Chip, Panel, Screen } from '../ui';

export function ClickerScreen({ onBack }: { onBack: () => void }) {
  const toast = useToast();
  const nativeReady = isNativeAutoAssistAvailable();
  const [a11y, setA11y] = useState(false);
  const [overlay, setOverlay] = useState(false);
  const [showing, setShowing] = useState(false);

  const refresh = useCallback(async () => {
    if (!nativeReady) {
      setA11y(false);
      setOverlay(false);
      setShowing(false);
      return;
    }
    try {
      const [a, o, s] = await Promise.all([
        isAccessibilityEnabled(),
        canDrawOverlays(),
        isClickerOverlayShowing(),
      ]);
      setA11y(a);
      setOverlay(o);
      setShowing(s);
    } catch {
      setA11y(false);
      setOverlay(false);
    }
  }, [nativeReady]);

  useEffect(() => {
    refresh();
    const sub = AppState.addEventListener('change', (state) => {
      if (state === 'active') refresh();
    });
    return () => sub.remove();
  }, [refresh]);

  const openFloat = async () => {
    if (Platform.OS !== 'android') {
      toast.warning('仅安卓可用');
      return;
    }
    if (!nativeReady) {
      toast.error('请安装 EAS 构建的 APK', '需要自定义包');
      return;
    }
    try {
      if (!overlay) {
        toast.warning('请先开启「显示在其他应用上层」');
        await openOverlaySettings();
        return;
      }
      if (!a11y) {
        toast.warning('请先开启无障碍服务');
        await openAccessibilitySettings();
        return;
      }
      await showClickerOverlay();
      setShowing(true);
      toast.success('悬浮窗已打开，可切到其他 App 选点');
    } catch (e) {
      toast.error(e instanceof Error ? e.message : '打开失败');
      refresh();
    }
  };

  return (
    <ScrollView
      style={styles.scroll}
      contentContainerStyle={styles.content}
      showsVerticalScrollIndicator={false}
    >
      <Screen
        onBack={onBack}
        title="自动点击器"
        subtitle="打开悬浮窗后，在任意界面选多个点位，然后开启循环点击。"
      >
        <Panel label="权限状态">
          <View style={styles.chips}>
            <Chip
              label={nativeReady ? '原生模块就绪' : '需自定义 APK'}
              tone={nativeReady ? 'success' : 'warning'}
            />
            <Chip
              label={a11y ? '无障碍已开' : '无障碍未开'}
              tone={a11y ? 'success' : 'warning'}
            />
            <Chip
              label={overlay ? '悬浮窗已授权' : '悬浮窗未授权'}
              tone={overlay ? 'success' : 'warning'}
            />
            <Chip
              label={showing ? '浮窗显示中' : '浮窗未打开'}
              tone={showing ? 'info' : 'neutral'}
            />
          </View>
          <Text style={styles.body}>
            点击由无障碍手势完成；悬浮窗只负责选点与启停。与「快手签到」互斥，同时只跑一个。
          </Text>
          <Button
            label="打开无障碍设置"
            variant="tonal"
            onPress={async () => {
              try {
                await openAccessibilitySettings();
              } catch (e) {
                toast.error(e instanceof Error ? e.message : '无法打开');
              }
            }}
          />
          <Button
            label="打开悬浮窗权限"
            variant="tonal"
            onPress={async () => {
              try {
                await openOverlaySettings();
              } catch (e) {
                toast.error(e instanceof Error ? e.message : '无法打开');
              }
            }}
          />
        </Panel>

        <Panel label="使用步骤">
          <Text style={styles.body}>
            1. 开启无障碍与悬浮窗权限{'\n'}
            2. 点下方按钮打开浮窗，可拖动控制条{'\n'}
            3. 浮窗内点「选点」，在目标 App 上依次点几个位置{'\n'}
            4. 「完成选点」后调间隔，再点「开启」{'\n'}
            5. 可用「暂停 / 继续 / 结束」；✕ 关闭浮窗
          </Text>
        </Panel>

        <Panel label="操作" last>
          <Button
            label={showing ? '浮窗已打开（再点可聚焦）' : '打开悬浮窗'}
            variant="filled"
            onPress={openFloat}
          />
          {showing ? (
            <Button
              label="关闭悬浮窗"
              variant="outlined"
              onPress={async () => {
                await hideClickerOverlay();
                setShowing(false);
                toast.info('悬浮窗已关闭');
              }}
            />
          ) : null}
        </Panel>
      </Screen>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  scroll: { flex: 1, backgroundColor: colors.surface },
  content: { paddingBottom: space.xxl },
  chips: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: space.sm,
  },
  body: {
    ...type.body,
    color: colors.onSurfaceVariant,
    marginTop: space.md,
  },
});
