export type ScheduleKind = 'after_minutes' | 'at_time' | 'daily';

export type ScheduleTask = {
  id: string;
  title: string;
  body: string;
  kind: ScheduleKind;
  /** after_minutes */
  minutes?: number;
  /** at_time / daily，0–23 / 0–59 */
  hour?: number;
  minute?: number;
  /** expo-notifications 返回的系统调度 ID */
  notificationId: string;
  createdAt: number;
  /** 列表展示用 */
  whenLabel: string;
};

export type CreateScheduleInput = {
  title: string;
  body: string;
  kind: ScheduleKind;
  minutes?: number;
  hour?: number;
  minute?: number;
};
