import { StatusBar } from 'expo-status-bar';
import { useEffect, useState } from 'react';
import { BackHandler, StyleSheet, View } from 'react-native';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { DeviceLabScreen } from './src/screens/DeviceLabScreen';
import { HomeScreen, type ToolId } from './src/screens/HomeScreen';
import { KuaishouScreen } from './src/screens/KuaishouScreen';
import { ScheduleScreen } from './src/screens/ScheduleScreen';
import { colors } from './src/theme';
import { ToastProvider } from './src/toast/ToastContext';

// 注册前台通知展示策略（模块加载时执行一次）
import './src/schedule/notifications';

type Route = 'home' | ToolId;

export default function App() {
  const [route, setRoute] = useState<Route>('home');

  useEffect(() => {
    const onBackPress = () => {
      if (route !== 'home') {
        setRoute('home');
        return true;
      }
      return false;
    };

    const sub = BackHandler.addEventListener('hardwareBackPress', onBackPress);
    return () => sub.remove();
  }, [route]);

  return (
    <SafeAreaProvider>
      <View style={styles.root}>
        <ToastProvider>
          <StatusBar style="dark" />
          {route === 'home' ? (
            <HomeScreen onOpen={setRoute} />
          ) : route === 'schedule' ? (
            <ScheduleScreen onBack={() => setRoute('home')} />
          ) : route === 'device' ? (
            <DeviceLabScreen onBack={() => setRoute('home')} />
          ) : (
            <KuaishouScreen onBack={() => setRoute('home')} />
          )}
        </ToastProvider>
      </View>
    </SafeAreaProvider>
  );
}

const styles = StyleSheet.create({
  root: {
    flex: 1,
    backgroundColor: colors.surface,
  },
});
