import { useCallback, useEffect, useState } from 'react';
import {
  Keyboard,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import {
  cancelScheduledTask,
  createScheduledTask,
  ensureNotificationReady,
  kindLabel,
} from '../schedule/notifications';
import { loadTasks } from '../schedule/storage';
import type { ScheduleKind, ScheduleTask } from '../schedule/types';
import { colors, radius, space, type } from '../theme';
import { useToast } from '../toast/ToastContext';
import { Button, Chip, ListRow, Panel, Screen, Section } from '../ui';

const MODES: { id: ScheduleKind; label: string; hint: string }[] = [
  { id: 'after_minutes', label: '多少分钟后', hint: '例如 10 分钟后提醒一次' },
  { id: 'at_time', label: '指定时刻', hint: '今天该时刻已过则改到明天' },
  { id: 'daily', label: '每天定点', hint: '每天同一时间重复提醒' },
];

export function ScheduleScreen({ onBack }: { onBack: () => void }) {
  const toast = useToast();
  const [kind, setKind] = useState<ScheduleKind>('after_minutes');
  const [title, setTitle] = useState('提醒');
  const [body, setBody] = useState('');
  const [minutes, setMinutes] = useState('10');
  const [hour, setHour] = useState('9');
  const [minute, setMinute] = useState('0');
  const [tasks, setTasks] = useState<ScheduleTask[]>([]);
  const [busy, setBusy] = useState(false);
  const [permOk, setPermOk] = useState<boolean | null>(null);

  const refresh = useCallback(async () => {
    setTasks(await loadTasks());
  }, []);

  useEffect(() => {
    refresh();
    (async () => {
      try {
        await ensureNotificationReady();
        setPermOk(true);
      } catch {
        setPermOk(false);
      }
    })();
  }, [refresh]);

  const onCreate = async () => {
    if (Platform.OS === 'web') {
      toast.warning('请在安卓真机上使用系统通知', 'Web 受限');
      return;
    }
    setBusy(true);
    try {
      const task = await createScheduledTask({
        title,
        body,
        kind,
        minutes: Number(minutes),
        hour: Number(hour),
        minute: Number(minute),
      });
      setPermOk(true);
      await refresh();
      toast.success(`已创建：${task.whenLabel}`, '定时任务');
    } catch (e) {
      setPermOk(false);
      toast.error(e instanceof Error ? e.message : '创建失败');
    } finally {
      setBusy(false);
    }
  };

  const onDelete = async (task: ScheduleTask) => {
    try {
      await cancelScheduledTask(task);
      await refresh();
      toast.info('已取消该提醒');
    } catch (e) {
      toast.error(e instanceof Error ? e.message : '取消失败');
    }
  };

  const mode = MODES.find((m) => m.id === kind)!;

  return (
    <ScrollView
      style={styles.scroll}
      contentContainerStyle={styles.content}
      keyboardShouldPersistTaps="handled"
      keyboardDismissMode="on-drag"
      showsVerticalScrollIndicator={false}
    >
      <Screen
        onBack={onBack}
        title="定时提醒"
        subtitle="支持：多少分钟后 / 指定时刻 / 每天定点，发送系统通知。"
      >
        <Panel label="新建任务">
          <Text style={styles.fieldLabel}>提醒方式</Text>
          <View style={styles.modeRow}>
            {MODES.map((m) => {
              const active = m.id === kind;
              return (
                <Pressable
                  key={m.id}
                  onPress={() => {
                    Keyboard.dismiss();
                    setKind(m.id);
                  }}
                  style={[styles.modeChip, active && styles.modeChipActive]}
                >
                  <Text
                    style={[styles.modeText, active && styles.modeTextActive]}
                  >
                    {m.label}
                  </Text>
                </Pressable>
              );
            })}
          </View>
          <Text style={styles.hint}>{mode.hint}</Text>

          {/* 时间字段放上面：resize 时靠近顶部，不被键盘挡住 */}
          {kind === 'after_minutes' ? (
            <>
              <Text style={styles.fieldLabel}>分钟后</Text>
              <TextInput
                value={minutes}
                onChangeText={setMinutes}
                keyboardType="number-pad"
                placeholder="10"
                placeholderTextColor={colors.onSurfaceVariant}
                style={styles.input}
              />
            </>
          ) : (
            <View style={styles.timeRow}>
              <View style={styles.timeField}>
                <Text style={styles.fieldLabel}>时（0–23）</Text>
                <TextInput
                  value={hour}
                  onChangeText={setHour}
                  keyboardType="number-pad"
                  placeholder="9"
                  placeholderTextColor={colors.onSurfaceVariant}
                  style={styles.input}
                />
              </View>
              <View style={styles.timeField}>
                <Text style={styles.fieldLabel}>分（0–59）</Text>
                <TextInput
                  value={minute}
                  onChangeText={setMinute}
                  keyboardType="number-pad"
                  placeholder="0"
                  placeholderTextColor={colors.onSurfaceVariant}
                  style={styles.input}
                />
              </View>
            </View>
          )}

          <Text style={styles.fieldLabel}>标题</Text>
          <TextInput
            value={title}
            onChangeText={setTitle}
            placeholder="提醒标题"
            placeholderTextColor={colors.onSurfaceVariant}
            style={styles.input}
            returnKeyType="next"
          />

          <Text style={styles.fieldLabel}>内容</Text>
          <TextInput
            value={body}
            onChangeText={setBody}
            placeholder="可选，通知正文"
            placeholderTextColor={colors.onSurfaceVariant}
            style={[styles.input, styles.inputMultiline]}
            multiline
          />

          <Button
            label={busy ? '创建中…' : '创建定时通知'}
            variant="filled"
            disabled={busy}
            onPress={() => {
              Keyboard.dismiss();
              onCreate();
            }}
          />
          <Button
            label="收起键盘"
            variant="text"
            onPress={() => Keyboard.dismiss()}
          />
        </Panel>

        <Panel label="通知权限">
          <View style={styles.metaRow}>
            <Chip
              label={
                Platform.OS === 'web'
                  ? 'Web 不完整支持'
                  : permOk === true
                    ? '通知已授权'
                    : permOk === false
                      ? '通知未授权'
                      : '检测中…'
              }
              tone={
                Platform.OS === 'web'
                  ? 'warning'
                  : permOk === true
                    ? 'success'
                    : permOk === false
                      ? 'warning'
                      : 'neutral'
              }
            />
          </View>
          <Text style={styles.hint}>
            安卓 13+ 需允许通知。部分机型还要关闭对本应用的电池限制，定时才更准时。
          </Text>
          <Button
            label="重新申请通知权限"
            variant="tonal"
            onPress={async () => {
              try {
                await ensureNotificationReady();
                setPermOk(true);
                toast.success('通知权限已就绪');
              } catch (e) {
                setPermOk(false);
                toast.error(e instanceof Error ? e.message : '授权失败');
              }
            }}
          />
        </Panel>

        <Section title={`已创建（${tasks.length}）`}>
          {tasks.length === 0 ? (
            <View style={styles.emptyBox}>
              <Text style={styles.hint}>
                还没有任务。创建后会出现在这里，可随时取消。
              </Text>
            </View>
          ) : (
            tasks.map((task, index) => (
              <ListRow
                key={task.id}
                title={task.title}
                subtitle={`${kindLabel(task.kind)} · ${task.whenLabel}${
                  task.body ? ` · ${task.body}` : ''
                }`}
                last={index === tasks.length - 1}
                trailing={
                  <Pressable
                    onPress={() => onDelete(task)}
                    hitSlop={8}
                    style={styles.deleteBtn}
                  >
                    <Text style={styles.deleteText}>取消</Text>
                  </Pressable>
                }
              />
            ))
          )}
        </Section>
      </Screen>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  scroll: { flex: 1, backgroundColor: colors.surface },
  content: { paddingBottom: space.xxl },
  metaRow: { flexDirection: 'row', flexWrap: 'wrap', gap: space.sm },
  hint: {
    ...type.body,
    color: colors.onSurfaceVariant,
    marginTop: space.sm,
  },
  fieldLabel: {
    ...type.label,
    color: colors.onSurfaceVariant,
    marginTop: space.md,
    marginBottom: space.xs,
  },
  input: {
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: colors.outline,
    backgroundColor: colors.surfaceHigh,
    borderRadius: radius.md,
    paddingHorizontal: space.md,
    paddingVertical: Platform.OS === 'ios' ? space.md : space.sm,
    ...type.body,
    color: colors.onSurface,
  },
  inputMultiline: {
    minHeight: 72,
    textAlignVertical: 'top',
  },
  modeRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: space.sm,
  },
  modeChip: {
    paddingHorizontal: space.md,
    paddingVertical: space.sm,
    borderRadius: radius.full,
    backgroundColor: colors.surfaceHigh,
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: colors.outlineVariant,
  },
  modeChipActive: {
    backgroundColor: colors.primaryContainer,
    borderColor: colors.primary,
  },
  modeText: {
    ...type.labelLg,
    color: colors.onSurfaceVariant,
  },
  modeTextActive: {
    color: colors.onPrimaryContainer,
  },
  timeRow: {
    flexDirection: 'row',
    gap: space.md,
  },
  timeField: {
    flex: 1,
  },
  emptyBox: {
    padding: space.lg,
  },
  deleteBtn: {
    paddingHorizontal: space.sm,
    paddingVertical: space.xs,
  },
  deleteText: {
    ...type.labelLg,
    color: colors.error,
  },
});
