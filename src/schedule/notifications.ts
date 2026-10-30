import { Platform } from 'react-native';
import * as Notifications from 'expo-notifications';
import type { CreateScheduleInput, ScheduleKind, ScheduleTask } from './types';
import { removeTask, upsertTask } from './storage';

export const SCHEDULE_CHANNEL_ID = 'toolbox-reminders';

Notifications.setNotificationHandler({
  handleNotification: async () => ({
    shouldPlaySound: true,
    shouldSetBadge: false,
    shouldShowBanner: true,
    shouldShowList: true,
  }),
});

function pad2(n: number) {
  return n.toString().padStart(2, '0');
}

export function formatWhenLabel(
  kind: ScheduleKind,
  opts: { minutes?: number; hour?: number; minute?: number },
): string {
  if (kind === 'after_minutes') {
    return `${opts.minutes ?? 0} 分钟后`;
  }
  const hm = `${pad2(opts.hour ?? 0)}:${pad2(opts.minute ?? 0)}`;
  if (kind === 'daily') return `每天 ${hm}`;
  return `下次 ${hm}`;
}

/** 今天的 hour:minute；若已过则推到明天 */
export function nextOccurrenceDate(hour: number, minute: number): Date {
  const date = new Date();
  date.setSeconds(0, 0);
  date.setHours(hour, minute, 0, 0);
  if (date.getTime() <= Date.now() + 5_000) {
    date.setDate(date.getDate() + 1);
  }
  return date;
}

export async function ensureNotificationReady(): Promise<void> {
  if (Platform.OS === 'web') {
    throw new Error('Web 端系统通知能力有限，请在安卓真机上使用');
  }

  if (Platform.OS === 'android') {
    await Notifications.setNotificationChannelAsync(SCHEDULE_CHANNEL_ID, {
      name: '定时提醒',
      importance: Notifications.AndroidImportance.HIGH,
      vibrationPattern: [0, 250, 250, 250],
      lightColor: '#0B57D0',
    });
  }

  const current = await Notifications.getPermissionsAsync();
  let status = current.status;
  if (status !== 'granted') {
    const asked = await Notifications.requestPermissionsAsync();
    status = asked.status;
  }
  if (status !== 'granted') {
    throw new Error('未获得通知权限，请在系统设置中允许通知');
  }
}

function buildTrigger(
  input: CreateScheduleInput,
): Notifications.NotificationTriggerInput {
  const channel =
    Platform.OS === 'android' ? { channelId: SCHEDULE_CHANNEL_ID } : {};

  if (input.kind === 'after_minutes') {
    const minutes = Math.max(1, Math.floor(input.minutes ?? 1));
    return {
      type: Notifications.SchedulableTriggerInputTypes.TIME_INTERVAL,
      seconds: minutes * 60,
      ...channel,
    };
  }

  const hour = clampInt(input.hour, 0, 23);
  const minute = clampInt(input.minute, 0, 59);

  if (input.kind === 'daily') {
    return {
      type: Notifications.SchedulableTriggerInputTypes.DAILY,
      hour,
      minute,
      ...channel,
    };
  }

  const date = nextOccurrenceDate(hour, minute);
  return {
    type: Notifications.SchedulableTriggerInputTypes.DATE,
    date,
    ...channel,
  };
}

function clampInt(value: number | undefined, min: number, max: number) {
  const n = Math.floor(Number(value));
  if (!Number.isFinite(n)) return min;
  return Math.min(max, Math.max(min, n));
}

function validateInput(input: CreateScheduleInput) {
  const title = input.title.trim() || '定时提醒';
  const body = input.body.trim() || '你设置的提醒时间到了';
  if (input.kind === 'after_minutes') {
    const minutes = Math.floor(Number(input.minutes));
    if (!Number.isFinite(minutes) || minutes < 1) {
      throw new Error('请输入至少 1 分钟');
    }
    if (minutes > 60 * 24 * 30) {
      throw new Error('间隔过长，请改用「指定时刻」或「每天」');
    }
    return { ...input, title, body, minutes };
  }
  const hour = clampInt(input.hour, 0, 23);
  const minute = clampInt(input.minute, 0, 59);
  if (
    input.hour === undefined ||
    input.minute === undefined ||
    Number.isNaN(Number(input.hour)) ||
    Number.isNaN(Number(input.minute))
  ) {
    throw new Error('请填写有效的小时（0–23）和分钟（0–59）');
  }
  return { ...input, title, body, hour, minute };
}

export async function createScheduledTask(
  raw: CreateScheduleInput,
): Promise<ScheduleTask> {
  await ensureNotificationReady();
  const input = validateInput(raw);
  const trigger = buildTrigger(input);

  const notificationId = await Notifications.scheduleNotificationAsync({
    content: {
      title: input.title,
      body: input.body,
      sound: true,
      data: { kind: input.kind, source: 'toolbox-schedule' },
    },
    trigger,
  });

  const task: ScheduleTask = {
    id: `task_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`,
    title: input.title,
    body: input.body,
    kind: input.kind,
    minutes: input.minutes,
    hour: input.hour,
    minute: input.minute,
    notificationId,
    createdAt: Date.now(),
    whenLabel: formatWhenLabel(input.kind, input),
  };

  await upsertTask(task);
  return task;
}

export async function cancelScheduledTask(task: ScheduleTask): Promise<void> {
  try {
    await Notifications.cancelScheduledNotificationAsync(task.notificationId);
  } catch {
    // 系统侧可能已触发并清除
  }
  await removeTask(task.id);
}

export function kindLabel(kind: ScheduleKind): string {
  switch (kind) {
    case 'after_minutes':
      return '延时';
    case 'at_time':
      return '定点';
    case 'daily':
      return '每天';
  }
}
