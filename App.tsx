import { StatusBar } from 'expo-status-bar';
import { useEffect, useState } from 'react';
import { BackHandler, StyleSheet, View } from 'react-native';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { DeviceLabScreen } from './src/screens/DeviceLabScreen';
import { HomeScreen, type ToolId } from './src/screens/HomeScreen';
import { KuaishouScreen } from './src/screens/KuaishouScreen';
import { colors } from './src/theme';
import { ToastProvider } from './src/toast/ToastContext';

type Route = 'home' | ToolId;

export default function App() {
  const [route, setRoute] = useState<Route>('home');

  useEffect(() => {
    const onBackPress = () => {
      if (route !== 'home') {
        setRoute('home');
        return true; // 已处理，不退出应用
      }
      return false; // 在首页再按返回，交给系统退出
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
