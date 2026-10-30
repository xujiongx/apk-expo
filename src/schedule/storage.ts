import AsyncStorage from '@react-native-async-storage/async-storage';
import type { ScheduleTask } from './types';

const KEY = 'toolbox.schedule.tasks.v1';

export async function loadTasks(): Promise<ScheduleTask[]> {
  try {
    const raw = await AsyncStorage.getItem(KEY);
    if (!raw) return [];
    const parsed = JSON.parse(raw) as ScheduleTask[];
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

export async function saveTasks(tasks: ScheduleTask[]): Promise<void> {
  await AsyncStorage.setItem(KEY, JSON.stringify(tasks));
}

export async function upsertTask(task: ScheduleTask): Promise<ScheduleTask[]> {
  const list = await loadTasks();
  const next = [task, ...list.filter((t) => t.id !== task.id)];
  await saveTasks(next);
  return next;
}

export async function removeTask(id: string): Promise<ScheduleTask[]> {
  const list = await loadTasks();
  const next = list.filter((t) => t.id !== id);
  await saveTasks(next);
  return next;
}
