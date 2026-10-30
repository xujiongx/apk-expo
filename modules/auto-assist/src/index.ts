import { Platform, requireNativeModule } from 'expo-modules-core';

export type AutoProgressEvent = {
  step: string;
  message: string;
  ok: boolean;
};

type Subscription = { remove: () => void };

type AutoAssistNative = {
  isEnabled(): Promise<boolean>;
  openSettings(): Promise<void>;
  startKuaishouCheckIn(packageName: string): Promise<void>;
  stop(): Promise<void>;
  addListener(
    eventName: 'onProgress',
    listener: (event: AutoProgressEvent) => void,
  ): Subscription;
};

let native: AutoAssistNative | null | undefined;

function getNative(): AutoAssistNative | null {
  if (native !== undefined) return native;
  if (Platform.OS !== 'android') {
    native = null;
    return native;
  }
  try {
    native = requireNativeModule<AutoAssistNative>('AutoAssist');
  } catch {
    native = null;
  }
  return native;
}

export function isNativeAutoAssistAvailable(): boolean {
  return getNative() != null;
}

export async function isAccessibilityEnabled(): Promise<boolean> {
  const mod = getNative();
  if (!mod) return false;
  return mod.isEnabled();
}

export async function openAccessibilitySettings(): Promise<void> {
  const mod = getNative();
  if (!mod) {
    throw new Error('当前包不支持无障碍自动化，请使用 EAS 构建的 APK');
  }
  return mod.openSettings();
}

export async function startKuaishouCheckIn(
  packageName = 'com.kuaishou.nebula',
): Promise<void> {
  const mod = getNative();
  if (!mod) {
    throw new Error('当前包不支持无障碍自动化，请使用 EAS 构建的 APK');
  }
  return mod.startKuaishouCheckIn(packageName);
}

export async function stopAutoAssist(): Promise<void> {
  const mod = getNative();
  if (!mod) return;
  return mod.stop();
}

export function addProgressListener(
  listener: (event: AutoProgressEvent) => void,
): Subscription {
  const mod = getNative();
  if (!mod) {
    return { remove() {} };
  }
  return mod.addListener('onProgress', listener);
}
